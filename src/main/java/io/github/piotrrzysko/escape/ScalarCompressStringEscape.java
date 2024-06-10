package io.github.piotrrzysko.escape;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorMask;

public class ScalarCompressStringEscape {

    public static int escape(byte[] src, byte[] dest) {
        int destIdx = 0;
        for (int srcIdx = 0; srcIdx < ByteVector.SPECIES_256.loopBound(src.length); srcIdx += ByteVector.SPECIES_256.vectorByteSize()) {
            ByteVector srcVec = ByteVector.fromArray(ByteVector.SPECIES_256, src, srcIdx);

            long backslashMask = srcVec.eq((byte) '\\').toLong();

            VectorMask<Byte> escapeMask = VectorMask.fromLong(ByteVector.SPECIES_256, backslashMask << 1);
            VectorMask<Byte> bMask = srcVec.eq((byte) 'b').and(escapeMask);
            VectorMask<Byte> fMask = srcVec.eq((byte) 'f').and(escapeMask);
            VectorMask<Byte> nMask = srcVec.eq((byte) 'n').and(escapeMask);
            VectorMask<Byte> rMask = srcVec.eq((byte) 'r').and(escapeMask);
            VectorMask<Byte> tMask = srcVec.eq((byte) 't').and(escapeMask);

            srcVec = srcVec.blend((byte) 0b0000_1000, bMask);
            srcVec = srcVec.blend((byte) 0b0000_1100, fMask);
            srcVec = srcVec.blend((byte) 0b0000_1010, nMask);
            srcVec = srcVec.blend((byte) 0b0000_1101, rMask);
            srcVec = srcVec.blend((byte) 0b0000_1001, tMask);

            srcVec.intoArray(dest, destIdx);

            int left = destIdx;
            int right = left + 1;
            int bound = destIdx + ByteVector.SPECIES_256.vectorByteSize() + 1;

            while (right < bound) {
                if (dest[left] == '\\') {
                    dest[left] = dest[right++];
                } else {
                    dest[++left] = dest[right++];
                }
            }

            while (backslashMask != 0) {
                int from = destIdx + Long.numberOfTrailingZeros(backslashMask);
                System.arraycopy(dest, , src, );
                backslashMask = (backslashMask - 1) & backslashMask;
            }

//            int from = destIdx + Long.numberOfTrailingZeros(backslashMask);
//            int to = from + 1;
//            while (backslashMask != 0) {
//                for (int i = from; i < to; i++) {
//                    dest[i] = dest[to++];
//                }
//                backslashMask = (backslashMask - 1) & backslashMask;
//                to += Long.numberOfTrailingZeros(backslashMask) + 1;
//            }

            destIdx = left;
        }

        return destIdx;
    }
}
