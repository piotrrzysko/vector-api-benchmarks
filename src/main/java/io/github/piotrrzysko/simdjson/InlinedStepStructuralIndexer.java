package io.github.piotrrzysko.simdjson;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorShuffle;

import java.util.Arrays;

import static jdk.incubator.vector.ByteVector.SPECIES_256;
import static jdk.incubator.vector.ByteVector.SPECIES_512;
import static jdk.incubator.vector.VectorOperators.UNSIGNED_LE;

/*
    This is a modified version of io.github.piotrrzysko.simdjson.LoadingInStepStructuralIndexer.
    The only difference is that in this implementation, all vector operations are inlined in the step method.

    The allocation rate is on a par with the LoadingInStepStructuralIndexer:

    StructuralIndexerBenchmark.inlinedStep:·gc.alloc.rate         thrpt    5       ≈ 10⁻⁴               MB/sec
    StructuralIndexerBenchmark.inlinedStep:·gc.alloc.rate.norm    thrpt    5        0.010 ±   0.001       B/op
    StructuralIndexerBenchmark.inlinedStep:·gc.count              thrpt    5          ≈ 0               counts

    StructuralIndexerBenchmark.loadingInStep:·gc.alloc.rate       thrpt    5       ≈ 10⁻⁴               MB/sec
    StructuralIndexerBenchmark.loadingInStep:·gc.alloc.rate.norm  thrpt    5        0.010 ±   0.001       B/op
    StructuralIndexerBenchmark.loadingInStep:·gc.count            thrpt    5          ≈ 0               counts


    The performance of the InlinedStepStructuralIndexer is slightly better:

    StructuralIndexerBenchmark.inlinedStep                        thrpt    5     4204.302 ±  7.170       ops/s
    StructuralIndexerBenchmark.loadingInStep                      thrpt    5     4105.010 ±  18.583      ops/s
 */
public class InlinedStepStructuralIndexer {

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

    public InlinedStepStructuralIndexer(BitIndexes bitIndexes) {
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
        long backslash0 = chunk0.eq(BACKSLASH).toLong();
        long backslash1 = chunk1.eq(BACKSLASH).toLong();
        long backslash = backslash0 | (backslash1 << 32);

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

        long unescaped0 = chunk0.compare(UNSIGNED_LE, LAST_CONTROL_CHARACTER).toLong();
        long unescaped1 = chunk1.compare(UNSIGNED_LE, LAST_CONTROL_CHARACTER).toLong();
        long unescaped = unescaped0 | (unescaped1 << 32);

        long quote0 = chunk0.eq(QUOTE).toLong();
        long quote1 = chunk1.eq(QUOTE).toLong();
        long quote = (quote0 | (quote1 << 32)) & ~escaped;

        long inString = prefixXor(quote) ^ prevInString;
        prevInString = inString >> 63;

        // characters classification
        VectorShuffle<Byte> chunk0Low = chunk0.and(LOW_NIBBLE_MASK).toShuffle();
        VectorShuffle<Byte> chunk1Low = chunk1.and(LOW_NIBBLE_MASK).toShuffle();

        long whitespace0 = chunk0.eq(WHITESPACE_TABLE.rearrange(chunk0Low)).toLong();
        long whitespace1 = chunk1.eq(WHITESPACE_TABLE.rearrange(chunk1Low)).toLong();
        long whitespace = whitespace0 | (whitespace1 << 32);

        ByteVector curlified0 = chunk0.or((byte) 0x20);
        ByteVector curlified1 = chunk1.or((byte) 0x20);
        long op0 = curlified0.eq(OP_TABLE.rearrange(chunk0Low)).toLong();
        long op1 = curlified1.eq(OP_TABLE.rearrange(chunk1Low)).toLong();
        long op = op0 | (op1 << 32);

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
}
