package guru.interlis.transformer.io.shp.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

class EndianByteBufferTest {

    @Test
    void allocateCreatesBufferWithGivenCapacity() {
        EndianByteBuffer buf = EndianByteBuffer.allocate(16);
        assertThat(buf.buffer().capacity()).isEqualTo(16);
        assertThat(buf.buffer().order()).isEqualTo(ByteOrder.BIG_ENDIAN);
    }

    @Test
    void wrapArrayCreatesBufferOverArray() {
        byte[] data = new byte[8];
        EndianByteBuffer buf = EndianByteBuffer.wrap(data);
        assertThat(buf.buffer().capacity()).isEqualTo(8);
    }

    @Test
    void wrapArrayWithOffsetAndLength() {
        byte[] data = new byte[16];
        EndianByteBuffer buf = EndianByteBuffer.wrap(data, 4, 8);
        assertThat(buf.buffer().capacity()).isEqualTo(8);
        assertThat(buf.position()).isEqualTo(0);
    }

    @Test
    void bigEndianIntRoundtrip() {
        EndianByteBuffer buf = EndianByteBuffer.allocate(4);
        buf.putBigInt(0x01020304);
        buf.flip();
        assertThat(buf.getBigInt()).isEqualTo(0x01020304);
        assertThat(buf.position()).isEqualTo(4);
    }

    @Test
    void littleEndianIntRoundtrip() {
        EndianByteBuffer buf = EndianByteBuffer.allocate(4);
        buf.putLittleInt(0x01020304);
        buf.flip();
        assertThat(buf.getLittleInt()).isEqualTo(0x01020304);
        assertThat(buf.position()).isEqualTo(4);
    }

    @Test
    void bigEndianAndLittleEndianIntProduceDifferentBytes() {
        EndianByteBuffer beBuf = EndianByteBuffer.allocate(4);
        beBuf.putBigInt(0x01020304);
        beBuf.flip();

        EndianByteBuffer leBuf = EndianByteBuffer.allocate(4);
        leBuf.putLittleInt(0x01020304);
        leBuf.flip();

        byte[] beBytes = new byte[4];
        byte[] leBytes = new byte[4];
        beBuf.get(beBytes);
        leBuf.get(leBytes);
        assertThat(beBytes).isNotEqualTo(leBytes);
    }

    @Test
    void mixedEndianIntsInSameBuffer() {
        EndianByteBuffer buf = EndianByteBuffer.allocate(8);

        buf.putBigInt(9994);
        buf.putLittleInt(1000);
        buf.flip();

        assertThat(buf.getBigInt()).isEqualTo(9994);
        assertThat(buf.getLittleInt()).isEqualTo(1000);
    }

    @Test
    void littleEndianDoubleRoundtrip() {
        EndianByteBuffer buf = EndianByteBuffer.allocate(8);
        buf.putLittleDouble(3.141592653589793);
        buf.flip();
        assertThat(buf.getLittleDouble()).isEqualTo(3.141592653589793);
    }

    @Test
    void littleEndianDoubleExtremes() {
        EndianByteBuffer buf = EndianByteBuffer.allocate(8);
        buf.putLittleDouble(Double.NaN);
        buf.flip();
        assertThat(Double.isNaN(buf.getLittleDouble())).isTrue();

        buf = EndianByteBuffer.allocate(8);
        buf.putLittleDouble(Double.POSITIVE_INFINITY);
        buf.flip();
        assertThat(buf.getLittleDouble()).isInfinite();

        buf = EndianByteBuffer.allocate(8);
        buf.putLittleDouble(Double.NEGATIVE_INFINITY);
        buf.flip();
        assertThat(buf.getLittleDouble()).isInfinite();

        buf = EndianByteBuffer.allocate(8);
        buf.putLittleDouble(0.0);
        buf.flip();
        assertThat(buf.getLittleDouble()).isEqualTo(0.0);

        buf = EndianByteBuffer.allocate(8);
        buf.putLittleDouble(-0.0);
        buf.flip();
        assertThat(buf.getLittleDouble()).isEqualTo(-0.0);
    }

    @Test
    void littleEndianShortRoundtrip() {
        EndianByteBuffer buf = EndianByteBuffer.allocate(2);
        buf.putLittleShort((short) 258);
        buf.flip();
        assertThat(buf.getLittleShort()).isEqualTo((short) 258);
    }

    @Test
    void intBoundaryValues() {
        int[] values = {0, 1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE, 9994, 1000};
        for (int val : values) {
            EndianByteBuffer buf = EndianByteBuffer.allocate(4);
            buf.putBigInt(val);
            buf.flip();
            assertThat(buf.getBigInt()).as("big-endian " + val).isEqualTo(val);

            buf = EndianByteBuffer.allocate(4);
            buf.putLittleInt(val);
            buf.flip();
            assertThat(buf.getLittleInt()).as("little-endian " + val).isEqualTo(val);
        }
    }

    @Test
    void positionTrackingIsCorrect() {
        EndianByteBuffer buf = EndianByteBuffer.allocate(16);
        assertThat(buf.position()).isEqualTo(0);

        buf.putBigInt(1);
        assertThat(buf.position()).isEqualTo(4);

        buf.putLittleInt(2);
        assertThat(buf.position()).isEqualTo(8);

        buf.position(0);
        assertThat(buf.position()).isEqualTo(0);

        buf.position(12);
        assertThat(buf.position()).isEqualTo(12);
    }

    @Test
    void flipLimitsToCurrentPosition() {
        EndianByteBuffer buf = EndianByteBuffer.allocate(16);
        buf.putBigInt(1);
        buf.putLittleInt(2);
        buf.flip();

        assertThat(buf.position()).isEqualTo(0);
        assertThat(buf.remaining()).isEqualTo(8);
        assertThat(buf.hasRemaining()).isTrue();
    }

    @Test
    void getAndPutSingleByte() {
        EndianByteBuffer buf = EndianByteBuffer.allocate(4);
        buf.put((byte) 0x2A);
        buf.put((byte) 0x20);
        buf.put((byte) 0x0D);
        buf.flip();

        assertThat(buf.get()).isEqualTo((byte) 0x2A);
        assertThat(buf.get()).isEqualTo((byte) 0x20);
        assertThat(buf.get()).isEqualTo((byte) 0x0D);
    }

    @Test
    void getAndPutByteArrays() {
        byte[] src = {0x01, 0x02, 0x03, 0x04, 0x05};
        EndianByteBuffer buf = EndianByteBuffer.allocate(10);
        buf.put(src, 0, 5);
        buf.flip();

        byte[] dst = new byte[5];
        buf.get(dst, 0, 5);
        assertThat(dst).isEqualTo(src);
    }

    @Test
    void underyingBufferExposesSameBytes() {
        EndianByteBuffer buf = EndianByteBuffer.allocate(8);
        buf.putBigInt(0xDEADBEEF);
        buf.flip();

        ByteBuffer raw = buf.buffer();
        raw.order(ByteOrder.BIG_ENDIAN);
        assertThat(raw.getInt(0)).isEqualTo(0xDEADBEEF);
    }

    @Test
    void bigEndianIntMatchesExplicitlyOrderedBuffer() {
        int value = 0x12345678;

        EndianByteBuffer buf = EndianByteBuffer.allocate(4);
        buf.putBigInt(value);
        buf.flip();

        byte b0 = buf.get();
        byte b1 = buf.get();
        byte b2 = buf.get();
        byte b3 = buf.get();

        assertThat(Byte.toUnsignedInt(b0)).isEqualTo(0x12);
        assertThat(Byte.toUnsignedInt(b1)).isEqualTo(0x34);
        assertThat(Byte.toUnsignedInt(b2)).isEqualTo(0x56);
        assertThat(Byte.toUnsignedInt(b3)).isEqualTo(0x78);
    }

    @Test
    void littleEndianIntBytesAreReversed() {
        int value = 0x12345678;

        EndianByteBuffer buf = EndianByteBuffer.allocate(4);
        buf.putLittleInt(value);
        buf.flip();

        byte b0 = buf.get();
        byte b1 = buf.get();
        byte b2 = buf.get();
        byte b3 = buf.get();

        assertThat(Byte.toUnsignedInt(b0)).isEqualTo(0x78);
        assertThat(Byte.toUnsignedInt(b1)).isEqualTo(0x56);
        assertThat(Byte.toUnsignedInt(b2)).isEqualTo(0x34);
        assertThat(Byte.toUnsignedInt(b3)).isEqualTo(0x12);
    }

    @Test
    void fileCode9994BigEndianBytes() {
        EndianByteBuffer buf = EndianByteBuffer.allocate(4);
        buf.putBigInt(9994);
        buf.flip();

        byte[] bytes = new byte[4];
        buf.get(bytes);

        assertThat(Byte.toUnsignedInt(bytes[0])).isEqualTo(0x00);
        assertThat(Byte.toUnsignedInt(bytes[1])).isEqualTo(0x00);
        assertThat(Byte.toUnsignedInt(bytes[2])).isEqualTo(0x27);
        assertThat(Byte.toUnsignedInt(bytes[3])).isEqualTo(0x0A);
    }

    @Test
    void version1000LittleEndianBytes() {
        EndianByteBuffer buf = EndianByteBuffer.allocate(4);
        buf.putLittleInt(1000);
        buf.flip();

        byte[] bytes = new byte[4];
        buf.get(bytes);

        assertThat(Byte.toUnsignedInt(bytes[0])).isEqualTo(0xE8);
        assertThat(Byte.toUnsignedInt(bytes[1])).isEqualTo(0x03);
        assertThat(Byte.toUnsignedInt(bytes[2])).isEqualTo(0x00);
        assertThat(Byte.toUnsignedInt(bytes[3])).isEqualTo(0x00);
    }
}
