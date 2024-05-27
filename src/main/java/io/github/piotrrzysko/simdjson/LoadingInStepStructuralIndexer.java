package io.github.piotrrzysko.simdjson;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorShuffle;

import java.util.Arrays;

import static jdk.incubator.vector.ByteVector.SPECIES_256;
import static jdk.incubator.vector.ByteVector.SPECIES_512;
import static jdk.incubator.vector.VectorOperators.UNSIGNED_LE;

/*
    This is a modified version of io.github.piotrrzysko.simdjson.OriginalStructuralIndexer.
    The only difference is that in this implementation, loading vectors is done in the step method
    instead of passing them as arguments.

    The allocation rate is significantly lower in comparison to OriginalStructuralIndexer:

    StructuralIndexerBenchmark.loadingInStep:·gc.alloc.rate       thrpt    5       ≈ 10⁻⁴               MB/sec
    StructuralIndexerBenchmark.loadingInStep:·gc.alloc.rate.norm  thrpt    5        0.010 ±   0.001       B/op
    StructuralIndexerBenchmark.loadingInStep:·gc.count            thrpt    5          ≈ 0               counts

    StructuralIndexerBenchmark.original:·gc.alloc.rate            thrpt    5     2975.118 ± 696.611     MB/sec
    StructuralIndexerBenchmark.original:·gc.alloc.rate.norm       thrpt    5  1263104.018 ±   0.010       B/op
    StructuralIndexerBenchmark.original:·gc.count                 thrpt    5      448.000               counts
    StructuralIndexerBenchmark.original:·gc.time                  thrpt    5      530.000                   ms


    The performance of the LoadingInStepStructuralIndexer is also much better:

    StructuralIndexerBenchmark.loadingInStep                      thrpt    5     4105.010 ±  18.583      ops/s
    StructuralIndexerBenchmark.original                           thrpt    5     2906.714 ±  36.484      ops/s
 */
public class LoadingInStepStructuralIndexer {

    private static final int STEP_SIZE = 64;
    private static final byte BACKSLASH = (byte) '\\';
    private static final byte QUOTE = (byte) '"';
    private static final byte SPACE = 0x20;
    private static final byte LAST_CONTROL_CHARACTER = (byte) 0x1F;
    private static final long EVEN_BITS_MASK = 0x5555555555555555L;
    private static final long ODD_BITS_MASK = ~EVEN_BITS_MASK;
    private static final byte LOW_NIBBLE_MASK = 0x0f;
    private static final ByteVector WHITESPACE_TABLE = repeat(
            new byte[]{' ', 100, 100, 100, 17, 100, 113, 2, 100, '\t', '\n', 112, 100, '\r', 100, 100}
    );
    private static final ByteVector OP_TABLE = repeat(
            new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, ':', '{', ',', '}', 0, 0}
    );
    private static final byte[] LAST_BLOCK_SPACES = new byte[STEP_SIZE];

    static {
        Arrays.fill(LAST_BLOCK_SPACES, SPACE);
    }

    private final BitIndexes bitIndexes;
    private final byte[] lastBlock = new byte[STEP_SIZE];

    private long prevInString;
    private long prevEscaped;
    private long prevStructurals;
    private long unescapedCharsError;
    private long prevScalar;

    public LoadingInStepStructuralIndexer(BitIndexes bitIndexes) {
        this.bitIndexes = bitIndexes;
    }

    public void index(byte[] buffer, int length) {
        reset();

        // Using SPECIES_512 here is not a mistake. Each iteration of the below loop processes two 256-bit chunks,
        // so effectively it processes 512 bits at once.
        int loopBound = SPECIES_512.loopBound(length);
        int offset = 0;
        int blockIndex = 0;
        for (; offset < loopBound; offset += STEP_SIZE) {
            step(buffer, offset, blockIndex);
            blockIndex += STEP_SIZE;
        }

        byte[] remainder = remainder(buffer, length, blockIndex);
        step(remainder, 0, blockIndex);
        blockIndex += STEP_SIZE;

        finish(blockIndex);
    }

    private void step(byte[] buffer, int offset, int blockIndex) {
        ByteVector chunk0 = ByteVector.fromArray(SPECIES_256, buffer, offset);
        ByteVector chunk1 = ByteVector.fromArray(SPECIES_256, buffer, offset + 32);

        // string scanning
        long backslash = eq(chunk0, chunk1, BACKSLASH);

        long escaped;
        if (backslash == 0) {
            escaped = prevEscaped;
            prevEscaped = 0;
        } else {
            backslash &= ~prevEscaped;
            long followsEscape = backslash << 1 | prevEscaped;
            long oddSequenceStarts = backslash & ODD_BITS_MASK & ~followsEscape;

            long sequencesStartingOnEvenBits = oddSequenceStarts + backslash;
            // Here, we check if the unsigned addition above caused an overflow. If that's the case, we store 1 in prevEscaped.
            // The formula used to detect overflow was taken from 'Hacker's Delight, Second Edition' by Henry S. Warren, Jr.,
            // Chapter 2-13.
            prevEscaped = ((oddSequenceStarts >>> 1) + (backslash >>> 1) + ((oddSequenceStarts & backslash) & 1)) >>> 63;

            long invertMask = sequencesStartingOnEvenBits << 1;
            escaped = (EVEN_BITS_MASK ^ invertMask) & followsEscape;
        }

        long unescaped = le(chunk0, chunk1, LAST_CONTROL_CHARACTER);
        long quote = eq(chunk0, chunk1, QUOTE) & ~escaped;
        long inString = prefixXor(quote) ^ prevInString;
        prevInString = inString >> 63;

        // characters classification
        VectorShuffle<Byte> chunk0Low = chunk0.and(LOW_NIBBLE_MASK).toShuffle();
        VectorShuffle<Byte> chunk1Low = chunk1.and(LOW_NIBBLE_MASK).toShuffle();

        long whitespace = eq(chunk0, WHITESPACE_TABLE.rearrange(chunk0Low), chunk1, WHITESPACE_TABLE.rearrange(chunk1Low));

        ByteVector curlified0 = chunk0.or((byte) 0x20);
        ByteVector curlified1 = chunk1.or((byte) 0x20);
        long op = eq(curlified0, OP_TABLE.rearrange(chunk0Low), curlified1, OP_TABLE.rearrange(chunk1Low));

        // finish
        long scalar = ~(op | whitespace);
        long nonQuoteScalar = scalar & ~quote;
        long followsNonQuoteScalar = nonQuoteScalar << 1 | prevScalar;
        prevScalar = nonQuoteScalar >>> 63;
        long potentialScalarStart = scalar & ~followsNonQuoteScalar;
        long potentialStructuralStart = op | potentialScalarStart;
        bitIndexes.write(blockIndex, prevStructurals);
        prevStructurals = potentialStructuralStart & ~(inString ^ quote);
        unescapedCharsError |= unescaped & inString;
    }

    private static long prefixXor(long bitmask) {
        bitmask ^= bitmask << 1;
        bitmask ^= bitmask << 2;
        bitmask ^= bitmask << 4;
        bitmask ^= bitmask << 8;
        bitmask ^= bitmask << 16;
        bitmask ^= bitmask << 32;
        return bitmask;
    }

    private void reset() {
        bitIndexes.reset();
        prevInString = 0;
        prevEscaped = 0;
        prevStructurals = 0;
        unescapedCharsError = 0;
        prevScalar = 0;
    }

    private void finish(int blockIndex) {
        bitIndexes.write(blockIndex, prevStructurals);
        bitIndexes.finish();
        if (prevInString != 0) {
            throw new IllegalArgumentException("Unclosed string. A string is opened, but never closed.");
        }
        if (unescapedCharsError != 0) {
            throw new IllegalArgumentException("Unescaped characters. Within strings, there are characters that should be escaped.");
        }
    }

    private byte[] remainder(byte[] buffer, int length, int idx) {
        System.arraycopy(LAST_BLOCK_SPACES, 0, lastBlock, 0, lastBlock.length);
        System.arraycopy(buffer, idx, lastBlock, 0, length - idx);
        return lastBlock;
    }

    private static ByteVector repeat(byte[] array) {
        int n = SPECIES_256.vectorByteSize() / 4;
        byte[] result = new byte[n * array.length];
        for (int dst = 0; dst < result.length; dst += array.length) {
            System.arraycopy(array, 0, result, dst, array.length);
        }
        return ByteVector.fromArray(SPECIES_256, result, 0);
    }

    private static long eq(ByteVector chunk0, ByteVector mask0, ByteVector chunk1, ByteVector mask1) {
        long r0 = chunk0.eq(mask0).toLong();
        long r1 = chunk1.eq(mask1).toLong();
        return r0 | (r1 << 32);
    }

    private static long eq(ByteVector chunk0, ByteVector chunk1, byte e) {
        long r0 = chunk0.eq(e).toLong();
        long r1 = chunk1.eq(e).toLong();
        return r0 | (r1 << 32);
    }

    private static long le(ByteVector chunk0, ByteVector chunk1, byte e) {
        long r0 = chunk0.compare(UNSIGNED_LE, e).toLong();
        long r1 = chunk1.compare(UNSIGNED_LE, e).toLong();
        return r0 | (r1 << 32);
    }
}
