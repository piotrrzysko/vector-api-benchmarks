package io.github.piotrrzysko.escape;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorMask;

public class VectorStringEscape {

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

            srcVec = srcVec.compress(VectorMask.fromLong(ByteVector.SPECIES_256, ~backslashMask));
            srcVec.intoArray(dest, destIdx);

            destIdx += ByteVector.SPECIES_256.vectorByteSize() - Long.bitCount(backslashMask);
        }

        return destIdx;
    }
}
