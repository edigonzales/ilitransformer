package guru.interlis.transformer.io.shp.core;

import guru.interlis.transformer.io.shp.ShapefileMappingException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

public final class ShpReader implements AutoCloseable {

    private static final int RECORD_HEADER_BYTES = 8;

    private final FileChannel channel;
    private final ShapefileHeader header;
    private long currentRecordNumber;
    private long currentByteOffset;

    private ShpReader(FileChannel channel, ShapefileHeader header) {
        this.channel = channel;
        this.header = header;
        this.currentRecordNumber = 0;
        this.currentByteOffset = ShapefileHeader.HEADER_SIZE;
    }

    public static ShpReader open(Path shpPath) throws IOException, ShapefileMappingException {
        FileChannel channel = FileChannel.open(shpPath, StandardOpenOption.READ);
        channel.position(0);
        ShapefileHeader header = ShapefileHeader.read(channel);
        header.validateMainFileHeader();
        if (!header.shapeType().isSupported2dMvp()) {
            throw new ShapefileMappingException("SHP shape type " + header.shapeType() + " ("
                    + header.shapeType().code()
                    + ") is not supported. Supported: Point, PolyLine, Polygon, NullShape.");
        }
        return new ShpReader(channel, header);
    }

    public ShapefileHeader header() {
        return header;
    }

    public Optional<ShapeRecord> readNext() throws IOException, ShapefileMappingException {
        if (currentByteOffset >= (long) header.fileLengthWords() * 2) {
            return Optional.empty();
        }

        long recordStart = currentByteOffset;

        ByteBuffer recordHeaderBuf = ByteBuffer.allocate(RECORD_HEADER_BYTES);
        while (recordHeaderBuf.hasRemaining()) {
            int n = channel.read(recordHeaderBuf);
            if (n < 0) {
                throw new IOException("Unexpected end of file reading record header at byte offset " + recordStart);
            }
        }
        recordHeaderBuf.flip();

        EndianByteBuffer headerReader = EndianByteBuffer.wrap(recordHeaderBuf);
        int recordNumber = headerReader.getBigInt();
        int contentLengthWords = headerReader.getBigInt();

        if (contentLengthWords < 0) {
            throw new ShapefileMappingException("Record " + recordNumber + " at byte offset " + recordStart
                    + ": negative content length " + contentLengthWords + " words");
        }

        int contentLengthBytes = contentLengthWords * 2;

        if (contentLengthBytes < 4) {
            throw new ShapefileMappingException("Record " + recordNumber + " at byte offset " + recordStart
                    + ": content length " + contentLengthBytes + " bytes is too small (minimum 4 for shape type)");
        }

        ByteBuffer contentBuf = ByteBuffer.allocate(contentLengthBytes);
        while (contentBuf.hasRemaining()) {
            int n = channel.read(contentBuf);
            if (n < 0) {
                throw new IOException("Unexpected end of file reading record " + recordNumber + " content ("
                        + contentLengthBytes + " bytes) at byte offset " + recordStart);
            }
        }
        contentBuf.flip();

        EndianByteBuffer contentReader = EndianByteBuffer.wrap(contentBuf);
        int recordShapeTypeCode = contentReader.getLittleInt();
        ShapeType recordShapeType = ShapeType.fromCode(recordShapeTypeCode);

        if (recordShapeType != ShapeType.NULL && recordShapeType != header.shapeType()) {
            throw new ShapefileMappingException(
                    "Record " + recordNumber + " shape type " + recordShapeType + " (" + recordShapeTypeCode
                            + ") does not match header shape type " + header.shapeType() + " ("
                            + header.shapeType().code() + ")");
        }

        Bounds bounds = extractBounds(contentReader, recordShapeType);

        currentRecordNumber++;
        currentByteOffset = recordStart + RECORD_HEADER_BYTES + contentLengthBytes;

        contentBuf.position(0);
        return Optional.of(new ShapeRecord(recordNumber, recordShapeType, contentBuf.asReadOnlyBuffer(), bounds));
    }

    private Bounds extractBounds(EndianByteBuffer buf, ShapeType shapeType) {
        return switch (shapeType) {
            case NULL -> new Bounds(0.0, 0.0, 0.0, 0.0);
            case POINT -> {
                double x = buf.getLittleDouble();
                double y = buf.getLittleDouble();
                yield new Bounds(x, y, x, y);
            }
            case POLYLINE, POLYGON -> Bounds.read(buf);
            default -> new Bounds(0.0, 0.0, 0.0, 0.0);
        };
    }

    public long currentRecordNumber() {
        return currentRecordNumber;
    }

    public long currentByteOffset() {
        return currentByteOffset;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
