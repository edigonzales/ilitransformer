package guru.interlis.transformer.io.shp.core;

import guru.interlis.transformer.io.shp.ShapefileMappingException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Low-level writer for the {@code .shx} index file.
 *
 * <p>The index shares the same 100-byte header layout as the {@code .shp} file. Each index entry is
 * 8 bytes: the offset and content length, both big-endian and measured in 16-bit words. The offset
 * points at the start of the corresponding {@code .shp} record, including its 8-byte record header.
 *
 * <p>The {@link FileChannel} is owned by {@link ShapefileDatasetWriter}; {@link #close()} therefore
 * does not close the channel.
 */
public final class ShxWriter implements AutoCloseable {

    private static final int INDEX_ENTRY_BYTES = 8;

    private final FileChannel channel;

    public ShxWriter(FileChannel channel) {
        this.channel = channel;
    }

    /** Writes (or patches) the 100-byte index header at the start of the file. */
    public void writeHeader(ShapefileHeader header) throws IOException, ShapefileMappingException {
        channel.position(0);
        header.write(channel);
    }

    /** Appends a single 8-byte index entry at the current channel position. */
    public void writeIndexEntry(long shpOffsetWords, int contentLengthWords)
            throws IOException, ShapefileMappingException {
        if (shpOffsetWords < ShapefileHeader.HEADER_SIZE / 2) {
            throw new ShapefileMappingException("SHX offset " + shpOffsetWords + " words is too small (minimum "
                    + (ShapefileHeader.HEADER_SIZE / 2) + " for the file header)");
        }
        if (shpOffsetWords > Integer.MAX_VALUE) {
            throw new ShapefileMappingException(
                    "SHX offset " + shpOffsetWords + " words exceeds the 32-bit Shapefile word limit");
        }
        if (contentLengthWords < 0) {
            throw new ShapefileMappingException("SHX content length " + contentLengthWords + " words is negative");
        }

        EndianByteBuffer buf = EndianByteBuffer.allocate(INDEX_ENTRY_BYTES);
        buf.putBigInt((int) shpOffsetWords);
        buf.putBigInt(contentLengthWords);
        ByteBuffer raw = buf.buffer();
        raw.flip();
        while (raw.hasRemaining()) {
            channel.write(raw);
        }
    }

    @Override
    public void close() {
        // The FileChannel is owned and closed by ShapefileDatasetWriter.
    }
}
