package guru.interlis.transformer.io.shp.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class EndianByteBuffer {

    private final ByteBuffer buffer;

    private EndianByteBuffer(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    public static EndianByteBuffer allocate(int capacity) {
        return new EndianByteBuffer(ByteBuffer.allocate(capacity));
    }

    public static EndianByteBuffer wrap(byte[] array) {
        return new EndianByteBuffer(ByteBuffer.wrap(array));
    }

    public static EndianByteBuffer wrap(byte[] array, int offset, int length) {
        return new EndianByteBuffer(ByteBuffer.wrap(array, offset, length).slice());
    }

    public static EndianByteBuffer wrap(ByteBuffer buffer) {
        return new EndianByteBuffer(buffer);
    }

    public ByteBuffer buffer() {
        return buffer;
    }

    public void position(int newPosition) {
        buffer.position(newPosition);
    }

    public int position() {
        return buffer.position();
    }

    public void flip() {
        buffer.flip();
    }

    public int remaining() {
        return buffer.remaining();
    }

    public boolean hasRemaining() {
        return buffer.hasRemaining();
    }

    public byte get() {
        return buffer.get();
    }

    public void put(byte value) {
        buffer.put(value);
    }

    public void get(byte[] dst) {
        buffer.get(dst);
    }

    public void get(byte[] dst, int offset, int length) {
        buffer.get(dst, offset, length);
    }

    public void put(byte[] src) {
        buffer.put(src);
    }

    public void put(byte[] src, int offset, int length) {
        buffer.put(src, offset, length);
    }

    public int getBigInt() {
        buffer.order(ByteOrder.BIG_ENDIAN);
        return buffer.getInt();
    }

    public void putBigInt(int value) {
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(value);
    }

    public int getLittleInt() {
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        return buffer.getInt();
    }

    public void putLittleInt(int value) {
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(value);
    }

    public double getLittleDouble() {
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        return buffer.getDouble();
    }

    public void putLittleDouble(double value) {
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putDouble(value);
    }

    public short getLittleShort() {
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        return buffer.getShort();
    }

    public void putLittleShort(short value) {
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(value);
    }
}
