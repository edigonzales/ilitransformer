package guru.interlis.transformer.io.shp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import guru.interlis.transformer.io.shp.ShapefileMappingException;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShapefileHeaderTest {

    @TempDir
    Path tempDir;

    @Test
    void readsValidPointShapefileHeader() throws Exception {
        byte[] headerBytes = createHeaderBytes(ShapeType.POINT, 50, 1.0, 2.0, 3.0, 4.0);
        Path shp = tempDir.resolve("test.shp");
        Files.write(shp, headerBytes);

        try (FileChannel channel = FileChannel.open(shp, StandardOpenOption.READ)) {
            ShapefileHeader header = ShapefileHeader.read(channel);

            assertThat(header.fileCode()).isEqualTo(9994);
            assertThat(header.fileLengthWords()).isEqualTo(50);
            assertThat(header.version()).isEqualTo(1000);
            assertThat(header.shapeType()).isEqualTo(ShapeType.POINT);
            assertThat(header.xmin()).isEqualTo(1.0);
            assertThat(header.ymin()).isEqualTo(2.0);
            assertThat(header.xmax()).isEqualTo(3.0);
            assertThat(header.ymax()).isEqualTo(4.0);
            assertThat(header.zmin()).isEqualTo(5.0);
            assertThat(header.zmax()).isEqualTo(6.0);
            assertThat(header.mmin()).isEqualTo(7.0);
            assertThat(header.mmax()).isEqualTo(8.0);
        }
    }

    @Test
    void readsPolylineShapefileHeader() throws Exception {
        byte[] headerBytes = createHeaderBytes(ShapeType.POLYLINE, 100, 0.0, 0.0, 100.0, 200.0);
        Path shp = tempDir.resolve("lines.shp");
        Files.write(shp, headerBytes);

        try (FileChannel channel = FileChannel.open(shp, StandardOpenOption.READ)) {
            ShapefileHeader header = ShapefileHeader.read(channel);
            assertThat(header.shapeType()).isEqualTo(ShapeType.POLYLINE);
        }
    }

    @Test
    void readsPolygonShapefileHeader() throws Exception {
        byte[] headerBytes = createHeaderBytes(ShapeType.POLYGON, 200, -10.0, -20.0, 30.0, 40.0);
        Path shp = tempDir.resolve("polys.shp");
        Files.write(shp, headerBytes);

        try (FileChannel channel = FileChannel.open(shp, StandardOpenOption.READ)) {
            ShapefileHeader header = ShapefileHeader.read(channel);
            assertThat(header.shapeType()).isEqualTo(ShapeType.POLYGON);
            assertThat(header.xmin()).isEqualTo(-10.0);
            assertThat(header.ymax()).isEqualTo(40.0);
        }
    }

    @Test
    void readsNullShapefileHeader() throws Exception {
        byte[] headerBytes = createHeaderBytes(ShapeType.NULL, 50, 0, 0, 0, 0);
        Path shp = tempDir.resolve("null.shp");
        Files.write(shp, headerBytes);

        try (FileChannel channel = FileChannel.open(shp, StandardOpenOption.READ)) {
            ShapefileHeader header = ShapefileHeader.read(channel);
            assertThat(header.shapeType()).isEqualTo(ShapeType.NULL);
        }
    }

    @Test
    void headerIsExactly100Bytes() {
        assertThat(ShapefileHeader.HEADER_SIZE).isEqualTo(100);
    }

    @Test
    void writeThenReadRoundtrip() throws Exception {
        ShapefileHeader original =
                new ShapefileHeader(9994, 50, 1000, ShapeType.POLYLINE, 1.0, 2.0, 3.0, 4.0, -1.0, -2.0, -3.0, -4.0);

        Path shp = tempDir.resolve("roundtrip.shp");
        try (FileChannel channel = FileChannel.open(shp, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            original.write(channel);
        }

        try (FileChannel channel = FileChannel.open(shp, StandardOpenOption.READ)) {
            ShapefileHeader reread = ShapefileHeader.read(channel);

            assertThat(reread.fileCode()).isEqualTo(original.fileCode());
            assertThat(reread.fileLengthWords()).isEqualTo(original.fileLengthWords());
            assertThat(reread.version()).isEqualTo(original.version());
            assertThat(reread.shapeType()).isEqualTo(original.shapeType());
            assertThat(reread.xmin()).isEqualTo(original.xmin());
            assertThat(reread.ymin()).isEqualTo(original.ymin());
            assertThat(reread.xmax()).isEqualTo(original.xmax());
            assertThat(reread.ymax()).isEqualTo(original.ymax());
            assertThat(reread.zmin()).isEqualTo(original.zmin());
            assertThat(reread.zmax()).isEqualTo(original.zmax());
            assertThat(reread.mmin()).isEqualTo(original.mmin());
            assertThat(reread.mmax()).isEqualTo(original.mmax());
        }
    }

    @Test
    void validateMainFileHeaderAcceptsValidHeader() {
        ShapefileHeader header = new ShapefileHeader(9994, 50, 1000, ShapeType.POINT, 1, 2, 3, 4, 0, 0, 0, 0);
        assertThatCode(header::validateMainFileHeader).doesNotThrowAnyException();
    }

    @Test
    void validateMainFileHeaderRejectsInvalidFileCode() {
        ShapefileHeader header = new ShapefileHeader(1234, 50, 1000, ShapeType.POINT, 1, 2, 3, 4, 0, 0, 0, 0);
        assertThatThrownBy(header::validateMainFileHeader)
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("1234")
                .hasMessageContaining("9994");
    }

    @Test
    void validateMainFileHeaderRejectsInvalidVersion() {
        ShapefileHeader header = new ShapefileHeader(9994, 50, 2000, ShapeType.POINT, 1, 2, 3, 4, 0, 0, 0, 0);
        assertThatThrownBy(header::validateMainFileHeader)
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("2000")
                .hasMessageContaining("1000");
    }

    @Test
    void validateIndexFileHeaderDelegatesToMainValidation() {
        ShapefileHeader valid = new ShapefileHeader(9994, 50, 1000, ShapeType.POLYLINE, 1, 2, 3, 4, 0, 0, 0, 0);
        assertThatCode(valid::validateIndexFileHeader).doesNotThrowAnyException();

        ShapefileHeader invalid = new ShapefileHeader(0, 50, 1000, ShapeType.POINT, 1, 2, 3, 4, 0, 0, 0, 0);
        assertThatThrownBy(invalid::validateIndexFileHeader).isInstanceOf(ShapefileMappingException.class);
    }

    @Test
    void rejectsUnsupportedShapeType() throws Exception {
        byte[] headerBytes = createHeaderBytes(9994, 50, 1000, 42, 0, 0, 0, 0);
        Path shp = tempDir.resolve("unsupported.shp");
        Files.write(shp, headerBytes);

        try (FileChannel channel = FileChannel.open(shp, StandardOpenOption.READ)) {
            assertThatThrownBy(() -> ShapefileHeader.read(channel))
                    .isInstanceOf(ShapefileMappingException.class)
                    .hasMessageContaining("42")
                    .hasMessageContaining("Unknown");
        }
    }

    @Test
    void rejectsTruncatedFile() throws Exception {
        byte[] truncated = new byte[50];
        Path shp = tempDir.resolve("truncated.shp");
        Files.write(shp, truncated);

        try (FileChannel channel = FileChannel.open(shp, StandardOpenOption.READ)) {
            assertThatThrownBy(() -> ShapefileHeader.read(channel))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Unexpected end of file");
        }
    }

    @Test
    void rejectsEmptyFile() throws Exception {
        Path shp = tempDir.resolve("empty.shp");
        Files.createFile(shp);

        try (FileChannel channel = FileChannel.open(shp, StandardOpenOption.READ)) {
            assertThatThrownBy(() -> ShapefileHeader.read(channel))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Unexpected end of file");
        }
    }

    @Test
    void fileLengthWordsIsIn16BitWords() throws Exception {
        byte[] headerBytes = createHeaderBytes(ShapeType.POINT, 75, 1, 2, 3, 4);
        Path shp = tempDir.resolve("words.shp");
        Files.write(shp, headerBytes);

        try (FileChannel channel = FileChannel.open(shp, StandardOpenOption.READ)) {
            ShapefileHeader header = ShapefileHeader.read(channel);
            assertThat(header.fileLengthWords()).isEqualTo(75);
        }
    }

    private byte[] createHeaderBytes(
            ShapeType shapeType, int fileLengthWords, double xmin, double ymin, double xmax, double ymax) {
        return createHeaderBytes(9994, fileLengthWords, 1000, shapeType.code(), xmin, ymin, xmax, ymax);
    }

    private byte[] createHeaderBytes(
            int fileCode,
            int fileLengthWords,
            int version,
            int shapeTypeCode,
            double xmin,
            double ymin,
            double xmax,
            double ymax) {
        EndianByteBuffer buf = EndianByteBuffer.allocate(100);
        buf.putBigInt(fileCode);
        buf.position(24);
        buf.putBigInt(fileLengthWords);
        buf.putLittleInt(version);
        buf.putLittleInt(shapeTypeCode);
        buf.putLittleDouble(xmin);
        buf.putLittleDouble(ymin);
        buf.putLittleDouble(xmax);
        buf.putLittleDouble(ymax);
        buf.putLittleDouble(5.0);
        buf.putLittleDouble(6.0);
        buf.putLittleDouble(7.0);
        buf.putLittleDouble(8.0);

        byte[] bytes = new byte[100];
        buf.flip();
        buf.get(bytes);
        return bytes;
    }
}
