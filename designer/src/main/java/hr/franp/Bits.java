package hr.franp;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class Bits {

    private boolean[] bits;

    public Bits(int size) {
        this.bits = new boolean[size];
    }

    public int size() {
        return bits.length;
    }

    public void setBit(int position, boolean value) {
        bits[position] = value;
    }

    public void clear() {
        bits = new boolean[bits.length];
    }

    public void or(Bits other) {
        int maxSize = max(other.bits.length, bits.length);

        if (bits.length < maxSize) {
            // expand
            bits = Arrays.copyOf(bits, maxSize);
        }

        for (int idx = 0; idx < maxSize; idx++) {
            bits[idx] |= other.bits[idx];
        }
    }

    public void writeTo(OutputStream outputStream) throws IOException {
        writeTo(outputStream, ByteOrder.LITTLE_ENDIAN);
    }

    public void writeTo(OutputStream outputStream, ByteOrder byteOrder) throws IOException {
        int byteCnt = (bits.length + 7) / 8;
        ByteBuffer buffer = ByteBuffer.allocate(byteCnt)
            .order(byteOrder);

        for (int bi = 0; bi < byteCnt; bi++) {
            byte b = 0;
            for (int idx = bi * 8; idx < (bi + 1) * 8; idx++) {
                if (bits[idx]) {
                    b |= (1 << (idx % 8));
                }
            }
            buffer.put(b);
        }

        outputStream.write(buffer.array());
    }

    public int nextSetBit(int position) {
        int start = min(
            max(0, position),
            bits.length - 1
        );
        for (int idx = start; idx < bits.length - 1; idx++) {
            if (bits[idx]) {
                return idx;
            }
        }
        return -1;
    }
}
