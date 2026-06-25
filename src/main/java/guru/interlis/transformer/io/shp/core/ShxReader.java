package guru.interlis.transformer.io.shp.core;

import guru.interlis.transformer.io.shp.ShapefileMappingException;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ShxReader implements AutoCloseable {

    private final FileChannel channel;
    private final ShapefileHeader header;
    private final List<ShxIndexEntry> entries;

    private ShxReader(FileChannel channel, ShapefileHeader header, List<ShxIndexEntry> entries) {
        this.channel = channel;
        this.header = header;
        this.entries = entries;
    }

    public static ShxReader open(Path shxPath) throws IOException, ShapefileMappingException {
        FileChannel channel = FileChannel.open(shxPath, StandardOpenOption.READ);
        try {
            channel.position(0);
            ShapefileHeader header = ShapefileHeader.read(channel);
            header.validateIndexFileHeader();

            int fileLengthBytes = header.fileLengthWords() * 2;
            int indexDataBytes = fileLengthBytes - ShapefileHeader.HEADER_SIZE;
            int numEntries = indexDataBytes / 8;

            if (indexDataBytes % 8 != 0) {
                throw new ShapefileMappingException(
                        "SHX file has invalid index data size: " + indexDataBytes + " bytes (not a multiple of 8)");
            }

            List<ShxIndexEntry> entries = new ArrayList<>(numEntries);
            long previousOffset = -1;
            for (int i = 0; i < numEntries; i++) {
                EndianByteBuffer buf = EndianByteBuffer.allocate(8);
                while (buf.buffer().hasRemaining()) {
                    int n = channel.read(buf.buffer());
                    if (n < 0) {
                        throw new IOException("Unexpected end of SHX file reading entry " + i);
                    }
                }
                buf.flip();
                int offsetWords = buf.getBigInt();
                int contentLengthWords = buf.getBigInt();

                if (offsetWords < 50) {
                    throw new ShapefileMappingException("SHX entry " + i + ": offset " + offsetWords
                            + " words is too small (minimum 50 for the file header)");
                }
                if (contentLengthWords < 0) {
                    throw new ShapefileMappingException(
                            "SHX entry " + i + ": negative content length " + contentLengthWords + " words");
                }
                if (previousOffset >= 0 && offsetWords <= previousOffset) {
                    throw new ShapefileMappingException(
                            "SHX entry " + i + ": offset " + offsetWords + " words is not monotonically "
                                    + "increasing relative to previous entry offset " + previousOffset + " words");
                }
                previousOffset = offsetWords;

                entries.add(new ShxIndexEntry(offsetWords, contentLengthWords));
            }

            return new ShxReader(channel, header, entries);
        } catch (Exception e) {
            channel.close();
            throw e;
        }
    }

    public ShapefileHeader header() {
        return header;
    }

    public List<ShxIndexEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public int recordCount() {
        return entries.size();
    }

    public void validateAgainstShpHeader(ShapefileHeader shpHeader) throws ShapefileMappingException {
        if (shpHeader.shapeType() != header.shapeType()) {
            throw new ShapefileMappingException("SHX header shape type " + header.shapeType() + " ("
                    + header.shapeType().code()
                    + ") does not match SHP header shape type " + shpHeader.shapeType() + " ("
                    + shpHeader.shapeType().code() + ")");
        }
    }

    public void validateRecordCountAgainstDbf(int dbfRecordCount, String inputId) throws ShapefileMappingException {
        if (recordCount() != dbfRecordCount) {
            throw new ShapefileMappingException("SHP input '" + inputId + "': SHX has " + recordCount()
                    + " index entries but DBF contains " + dbfRecordCount + " records");
        }
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    public record ShxIndexEntry(int offsetWords, int contentLengthWords) {}
}
