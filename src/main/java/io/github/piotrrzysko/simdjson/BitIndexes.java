package io.github.piotrrzysko.simdjson;

public class BitIndexes {

    private final int[] indexes;

    private int writeIdx;
    private int readIdx;

    public BitIndexes(int capacity) {
        indexes = new int[capacity];
    }

    public void write(int blockIndex, long bits) {
        if (bits == 0) {
            return;
        }

        int idx = blockIndex - 64;
        int cnt = Long.bitCount(bits);
        for (int i = 0; i < 8; i++) {
            indexes[i + writeIdx] = idx + Long.numberOfTrailingZeros(bits);
            bits = clearLowestBit(bits);
        }

        if (cnt > 8) {
            for (int i = 8; i < 16; i++) {
                indexes[i + writeIdx] = idx + Long.numberOfTrailingZeros(bits);
                bits = clearLowestBit(bits);
            }
            if (cnt > 16) {
                int i = 16;
                do {
                    indexes[i + writeIdx] = idx + Long.numberOfTrailingZeros(bits);
                    bits = clearLowestBit(bits);
                    i++;
                } while (i < cnt);
            }
        }
        writeIdx += cnt;
    }

    private long clearLowestBit(long bits) {
        return bits & (bits - 1);
    }

    public void advance() {
        readIdx++;
    }

    public int getAndAdvance() {
        assert readIdx <= writeIdx;
        return indexes[readIdx++];
    }

    public int getLast() {
        return indexes[writeIdx - 1];
    }

    public boolean isEnd() {
        return writeIdx == readIdx;
    }

    public void finish() {
        indexes[writeIdx] = 0;
    }

    public void reset() {
        writeIdx = 0;
        readIdx = 0;
    }
}
