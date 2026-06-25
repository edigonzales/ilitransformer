package guru.interlis.transformer.io.shp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import guru.interlis.transformer.io.shp.ShapefileMappingException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShxReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void readsEmptyShx() throws Exception {
        Path shx = tempDir.resolve("empty.shx");
        writeShx(shx, ShapeType.POINT, List.of());

        try (ShxReader reader = ShxReader.open(shx)) {
            assertThat(reader.header().shapeType()).isEqualTo(ShapeType.POINT);
            assertThat(reader.recordCount()).isEqualTo(0);
            assertThat(reader.entries()).isEmpty();
        }
    }

    @Test
    void readsSingleEntry() throws Exception {
        Path shx = tempDir.resolve("one.shx");
        writeShx(shx, ShapeType.POINT, List.of(new ShxEntry(50, 10)));

        try (ShxReader reader = ShxReader.open(shx)) {
            assertThat(reader.recordCount()).isEqualTo(1);
            ShxReader.ShxIndexEntry entry = reader.entries().get(0);
            assertThat(entry.offsetWords()).isEqualTo(50);
            assertThat(entry.contentLengthWords()).isEqualTo(10);
        }
    }

    @Test
    void readsMultipleEntries() throws Exception {
        Path shx = tempDir.resolve("multi.shx");
        writeShx(shx, ShapeType.POLYLINE, List.of(new ShxEntry(50, 20), new ShxEntry(70, 30), new ShxEntry(100, 15)));

        try (ShxReader reader = ShxReader.open(shx)) {
            assertThat(reader.recordCount()).isEqualTo(3);
            assertThat(reader.entries().get(0).offsetWords()).isEqualTo(50);
            assertThat(reader.entries().get(1).offsetWords()).isEqualTo(70);
            assertThat(reader.entries().get(2).offsetWords()).isEqualTo(100);
        }
    }

    @Test
    void validatesAgainstShpHeaderMatches() throws Exception {
        Path shx = tempDir.resolve("match.shx");
        writeShx(shx, ShapeType.POINT, List.of(new ShxEntry(50, 10)));

        try (ShxReader reader = ShxReader.open(shx)) {
            ShapefileHeader shpHeader =
                    new ShapefileHeader(9994, 50 + 20 * 2, 1000, ShapeType.POINT, 0, 0, 0, 0, 0, 0, 0, 0);
            reader.validateAgainstShpHeader(shpHeader);
        }
    }

    @Test
    void validateAgainstShpHeaderMismatchThrows() throws Exception {
        Path shx = tempDir.resolve("mismatch.shx");
        writeShx(shx, ShapeType.POINT, List.of(new ShxEntry(50, 10)));

        try (ShxReader reader = ShxReader.open(shx)) {
            ShapefileHeader shpHeader =
                    new ShapefileHeader(9994, 100, 1000, ShapeType.POLYLINE, 0, 0, 0, 0, 0, 0, 0, 0);
            assertThatThrownBy(() -> reader.validateAgainstShpHeader(shpHeader))
                    .isInstanceOf(ShapefileMappingException.class)
                    .hasMessageContaining("does not match");
        }
    }

    @Test
    void validateRecordCountAgainstDbfMatches() throws Exception {
        Path shx = tempDir.resolve("count.shx");
        writeShx(shx, ShapeType.POINT, List.of(new ShxEntry(50, 10), new ShxEntry(60, 10)));

        try (ShxReader reader = ShxReader.open(shx)) {
            reader.validateRecordCountAgainstDbf(2, "test");
        }
    }

    @Test
    void validateRecordCountAgainstDbfMismatchThrows() throws Exception {
        Path shx = tempDir.resolve("wrongcount.shx");
        writeShx(shx, ShapeType.POINT, List.of(new ShxEntry(50, 10)));

        try (ShxReader reader = ShxReader.open(shx)) {
            assertThatThrownBy(() -> reader.validateRecordCountAgainstDbf(5, "test"))
                    .isInstanceOf(ShapefileMappingException.class)
                    .hasMessageContaining("SHX has 1 index entries but DBF contains 5 records");
        }
    }

    @Test
    void rejectsNonMonotonicOffsets() throws Exception {
        Path shx = tempDir.resolve("nonmono.shx");
        byte[] data = new byte[100 + 16];
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putInt(9994);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt((100 + 16) / 2);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(1000);
        buf.putInt(ShapeType.POINT.code());
        for (int i = 0; i < 8; i++) buf.putDouble(0.0);

        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putInt(60);
        buf.putInt(10);
        buf.putInt(50);
        buf.putInt(10);
        Files.write(shx, data);

        assertThatThrownBy(() -> ShxReader.open(shx))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("not monotonically");
    }

    @Test
    void rejectsOffsetTooSmall() throws Exception {
        Path shx = tempDir.resolve("smalloffset.shx");
        writeShx(shx, ShapeType.POINT, List.of(new ShxEntry(10, 10)));

        assertThatThrownBy(() -> ShxReader.open(shx))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("too small");
    }

    @Test
    void rejectsNegativeContentLength() throws Exception {
        Path shx = tempDir.resolve("negcontent.shx");
        writeShx(shx, ShapeType.POINT, List.of(new ShxEntry(50, -1)));

        assertThatThrownBy(() -> ShxReader.open(shx))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("negative content length");
    }

    @Test
    void closeAllowsReopen() throws Exception {
        Path shx = tempDir.resolve("reopen.shx");
        writeShx(shx, ShapeType.POINT, List.of(new ShxEntry(50, 10)));

        try (ShxReader reader = ShxReader.open(shx)) {
            assertThat(reader.recordCount()).isEqualTo(1);
        }
        try (ShxReader reader = ShxReader.open(shx)) {
            assertThat(reader.recordCount()).isEqualTo(1);
        }
    }

    @Test
    void usesSameShapeTypeAsShp() throws Exception {
        for (ShapeType type :
                new ShapeType[] {ShapeType.NULL, ShapeType.POINT, ShapeType.POLYLINE, ShapeType.POLYGON}) {
            Path shx = tempDir.resolve(type.name().toLowerCase() + ".shx");
            writeShx(shx, type, List.of(new ShxEntry(50, 10)));

            try (ShxReader reader = ShxReader.open(shx)) {
                assertThat(reader.header().shapeType()).isEqualTo(type);
            }
        }
    }

    private record ShxEntry(int offsetWords, int contentLengthWords) {}

    private static void writeShx(Path path, ShapeType shapeType, List<ShxEntry> entries) throws Exception {
        int fileLength = 100 + entries.size() * 8;

        ByteBuffer buf = ByteBuffer.allocate(fileLength);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putInt(9994);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(fileLength / 2);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(1000);
        buf.putInt(shapeType.code());
        for (int i = 0; i < 8; i++) buf.putDouble(0.0);

        for (ShxEntry entry : entries) {
            buf.order(ByteOrder.BIG_ENDIAN);
            buf.putInt(entry.offsetWords);
            buf.putInt(entry.contentLengthWords);
        }

        buf.flip();
        byte[] data = new byte[buf.remaining()];
        buf.get(data);
        Files.write(path, data);
    }
}
