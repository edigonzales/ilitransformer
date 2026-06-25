package guru.interlis.transformer.io.shp.core;

import guru.interlis.transformer.io.shp.ShapefileMappingException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public record ShapefileHeader(
        int fileCode,
        int fileLengthWords,
        int version,
        ShapeType shapeType,
        double xmin,
        double ymin,
        double xmax,
        double ymax,
        double zmin,
        double zmax,
        double mmin,
        double mmax) {

    public static final int HEADER_SIZE = 100;
    static final int FILE_CODE_EXPECTED = 9994;
    static final int VERSION_EXPECTED = 1000;

    public static ShapefileHeader read(FileChannel channel) throws IOException, ShapefileMappingException {
        EndianByteBuffer buf = EndianByteBuffer.allocate(HEADER_SIZE);
        ByteBuffer raw = buf.buffer();
        while (raw.hasRemaining()) {
            int n = channel.read(raw);
            if (n < 0) {
                throw new IOException("Unexpected end of file reading SHP header: expected " + HEADER_SIZE + " bytes");
            }
        }
        buf.flip();

        int fileCode = buf.getBigInt();
        buf.position(24);
        int fileLengthWords = buf.getBigInt();
        int version = buf.getLittleInt();
        ShapeType shapeType = ShapeType.fromCode(buf.getLittleInt());
        double xmin = buf.getLittleDouble();
        double ymin = buf.getLittleDouble();
        double xmax = buf.getLittleDouble();
        double ymax = buf.getLittleDouble();
        double zmin = buf.getLittleDouble();
        double zmax = buf.getLittleDouble();
        double mmin = buf.getLittleDouble();
        double mmax = buf.getLittleDouble();

        return new ShapefileHeader(
                fileCode, fileLengthWords, version, shapeType, xmin, ymin, xmax, ymax, zmin, zmax, mmin, mmax);
    }

    public void write(FileChannel channel) throws IOException, ShapefileMappingException {
        EndianByteBuffer buf = EndianByteBuffer.allocate(HEADER_SIZE);
        buf.putBigInt(fileCode);
        buf.position(24);
        buf.putBigInt(fileLengthWords);
        buf.putLittleInt(version);
        buf.putLittleInt(shapeType.code());
        buf.putLittleDouble(xmin);
        buf.putLittleDouble(ymin);
        buf.putLittleDouble(xmax);
        buf.putLittleDouble(ymax);
        buf.putLittleDouble(zmin);
        buf.putLittleDouble(zmax);
        buf.putLittleDouble(mmin);
        buf.putLittleDouble(mmax);

        ByteBuffer raw = buf.buffer();
        raw.flip();
        while (raw.hasRemaining()) {
            channel.write(raw);
        }
    }

    public void validateMainFileHeader() throws ShapefileMappingException {
        if (fileCode != FILE_CODE_EXPECTED) {
            throw new ShapefileMappingException("SHP file code is " + fileCode + ", expected " + FILE_CODE_EXPECTED);
        }
        if (version != VERSION_EXPECTED) {
            throw new ShapefileMappingException("SHP version is " + version + ", expected " + VERSION_EXPECTED);
        }
    }

    public void validateIndexFileHeader() throws ShapefileMappingException {
        validateMainFileHeader();
    }
}
