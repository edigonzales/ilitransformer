package guru.interlis.transformer.io.shp.core;

import guru.interlis.transformer.io.shp.ShapefileMappingException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Low-level writer for the {@code .shp} main file.
 *
 * <p>This writer is byte-level only: it writes the 100-byte header and individual record blocks
 * (8-byte big-endian record header followed by the little-endian content of an {@link
 * EncodedShape}). Geometry-to-bytes encoding lives in {@code ShpGeometryEncoder} so that the {@code
 * core} package stays free of JTS encoding logic.
 *
 * <p>The {@link FileChannel} is owned by {@link ShapefileDatasetWriter}; {@link #close()} therefore
 * does not close the channel.
 */
public final class ShpWriter implements AutoCloseable {

    private static final int RECORD_HEADER_BYTES = 8;

    private final FileChannel channel;
    private final ShapeType shapeType;

    public ShpWriter(FileChannel channel, ShapeType shapeType) {
        this.channel = channel;
        this.shapeType = shapeType;
    }

    public ShapeType shapeType() {
        return shapeType;
    }

    /** Writes (or patches) the 100-byte file header at the start of the file. */
    public void writeHeader(ShapefileHeader header) throws IOException, ShapefileMappingException {
        channel.position(0);
        header.write(channel);
    }

    /**
     * Appends a single record at the current channel position.
     *
     * <p>The record header (record number and content length in 16-bit words) is big-endian; the
     * content is the little-endian {@link EncodedShape#content()} buffer produced by the encoder.
     */
    public void writeRecord(int recordNumber, EncodedShape encoded) throws IOException, ShapefileMappingException {
        int contentLengthBytes = encoded.contentLengthBytes();
        if (contentLengthBytes < 4 || contentLengthBytes % 2 != 0) {
            throw new ShapefileMappingException("Encoded shape content length " + contentLengthBytes
                    + " bytes is invalid (must be even and at least 4)");
        }

        EndianByteBuffer headerBuf = EndianByteBuffer.allocate(RECORD_HEADER_BYTES);
        headerBuf.putBigInt(recordNumber);
        headerBuf.putBigInt(contentLengthBytes / 2);
        ByteBuffer rawHeader = headerBuf.buffer();
        rawHeader.flip();
        while (rawHeader.hasRemaining()) {
            channel.write(rawHeader);
        }

        ByteBuffer content = encoded.content().duplicate();
        content.position(0);
        content.limit(contentLengthBytes);
        while (content.hasRemaining()) {
            channel.write(content);
        }
    }

    @Override
    public void close() {
        // The FileChannel is owned and closed by ShapefileDatasetWriter.
    }
}
