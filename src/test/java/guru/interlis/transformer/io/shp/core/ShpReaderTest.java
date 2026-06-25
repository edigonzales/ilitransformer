package guru.interlis.transformer.io.shp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import guru.interlis.transformer.io.shp.ShapefileMappingException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShpReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void readsPointShapefileWithOneRecord() throws Exception {
        Path shp = tempDir.resolve("point.shp");
        writePointShp(shp, new PointRecord(1, 7.5, 47.2));

        try (ShpReader reader = ShpReader.open(shp)) {
            assertThat(reader.header().shapeType()).isEqualTo(ShapeType.POINT);
            assertThat(reader.currentRecordNumber()).isEqualTo(0);

            Optional<ShapeRecord> record = reader.readNext();
            assertThat(record).isPresent();
            ShapeRecord r = record.get();
            assertThat(r.recordNumber()).isEqualTo(1);
            assertThat(r.shapeType()).isEqualTo(ShapeType.POINT);
            assertThat(r.bounds().xmin()).isEqualTo(7.5);
            assertThat(r.bounds().ymin()).isEqualTo(47.2);
            assertThat(r.bounds().xmax()).isEqualTo(7.5);
            assertThat(r.bounds().ymax()).isEqualTo(47.2);
            assertThat(reader.currentRecordNumber()).isEqualTo(1);

            assertThat(reader.readNext()).isEmpty();
        }
    }

    @Test
    void readsMultipleRecordsSequentially() throws Exception {
        Path shp = tempDir.resolve("multi.shp");
        writePointShp(shp, new PointRecord(1, 1.0, 1.0), new PointRecord(2, 2.0, 2.0), new PointRecord(3, 3.0, 3.0));

        try (ShpReader reader = ShpReader.open(shp)) {
            Optional<ShapeRecord> r1 = reader.readNext();
            assertThat(r1).isPresent();
            assertThat(r1.get().recordNumber()).isEqualTo(1);
            assertThat(reader.currentRecordNumber()).isEqualTo(1);

            Optional<ShapeRecord> r2 = reader.readNext();
            assertThat(r2).isPresent();
            assertThat(r2.get().recordNumber()).isEqualTo(2);
            assertThat(reader.currentRecordNumber()).isEqualTo(2);

            Optional<ShapeRecord> r3 = reader.readNext();
            assertThat(r3).isPresent();
            assertThat(r3.get().recordNumber()).isEqualTo(3);
            assertThat(reader.currentRecordNumber()).isEqualTo(3);

            assertThat(reader.readNext()).isEmpty();
        }
    }

    @Test
    void readsNullShapeRecord() throws Exception {
        Path shp = tempDir.resolve("null.shp");
        writeShpFile(shp, ShapeType.NULL, new int[] {1}, new int[] {2}, buf -> {
            buf.putInt(ShapeType.NULL.code());
        });

        try (ShpReader reader = ShpReader.open(shp)) {
            Optional<ShapeRecord> record = reader.readNext();
            assertThat(record).isPresent();
            ShapeRecord r = record.get();
            assertThat(r.shapeType()).isEqualTo(ShapeType.NULL);
            assertThat(r.bounds().xmin()).isEqualTo(0.0);
            assertThat(r.bounds().ymin()).isEqualTo(0.0);
        }
    }

    @Test
    void emptyShapefileYieldsNoRecords() throws Exception {
        Path shp = tempDir.resolve("empty.shp");
        writeShpFile(shp, ShapeType.POINT, new int[0], new int[0]);

        try (ShpReader reader = ShpReader.open(shp)) {
            assertThat(reader.header().shapeType()).isEqualTo(ShapeType.POINT);
            assertThat(reader.readNext()).isEmpty();
        }
    }

    @Test
    void rejectsTruncatedRecordContent() throws Exception {
        Path shp = tempDir.resolve("truncated.shp");
        writeTruncatedRecordShp(shp);

        try (ShpReader reader = ShpReader.open(shp)) {
            assertThatThrownBy(() -> reader.readNext())
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Unexpected end of file");
        }
    }

    @Test
    void rejectsRecordShapeTypeMismatchWithHeader() throws Exception {
        Path shp = tempDir.resolve("mismatch.shp");
        writeShpFile(shp, ShapeType.POINT, new int[] {1}, new int[] {2}, buf -> {
            buf.putInt(ShapeType.POLYLINE.code());
        });

        try (ShpReader reader = ShpReader.open(shp)) {
            assertThatThrownBy(() -> reader.readNext())
                    .isInstanceOf(ShapefileMappingException.class)
                    .hasMessageContaining("does not match header shape type");
        }
    }

    @Test
    void allowsNullShapeRecordRegardlessOfHeaderType() throws Exception {
        Path shp = tempDir.resolve("nullAllowed.shp");
        writeShpFile(shp, ShapeType.POLYLINE, new int[] {1}, new int[] {2}, buf -> {
            buf.putInt(ShapeType.NULL.code());
        });

        try (ShpReader reader = ShpReader.open(shp)) {
            Optional<ShapeRecord> record = reader.readNext();
            assertThat(record).isPresent();
            assertThat(record.get().shapeType()).isEqualTo(ShapeType.NULL);
        }
    }

    @Test
    void rejectsUnsupportedShapeTypeInHeader() throws Exception {
        Path shp = tempDir.resolve("unsupported.shp");
        writeShpFile(shp, ShapeType.POINT_Z, new int[0], new int[0]);

        assertThatThrownBy(() -> ShpReader.open(shp))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("not supported")
                .hasMessageContaining("POINT_Z");
    }

    @Test
    void contentBufferContainsCorrectPointData() throws Exception {
        Path shp = tempDir.resolve("content.shp");
        writePointShp(shp, new PointRecord(1, 100.5, 200.75));

        try (ShpReader reader = ShpReader.open(shp)) {
            ShapeRecord r = reader.readNext().get();
            ByteBuffer content = r.content();
            EndianByteBuffer buf = EndianByteBuffer.wrap(content);

            int shapeType = buf.getLittleInt();
            assertThat(shapeType).isEqualTo(ShapeType.POINT.code());

            double x = buf.getLittleDouble();
            double y = buf.getLittleDouble();
            assertThat(x).isEqualTo(100.5);
            assertThat(y).isEqualTo(200.75);
        }
    }

    @Test
    void nullShapeHasNoAdditionalContent() throws Exception {
        Path shp = tempDir.resolve("nullContent.shp");
        writeShpFile(shp, ShapeType.NULL, new int[] {42}, new int[] {2}, buf -> {
            buf.putInt(ShapeType.NULL.code());
        });

        try (ShpReader reader = ShpReader.open(shp)) {
            ShapeRecord r = reader.readNext().get();
            assertThat(r.content().remaining()).isEqualTo(4);
        }
    }

    private void writePointShp(Path path, PointRecord... records) throws IOException {
        int[] recordNumbers = new int[records.length];
        int[] contentLengthWords = new int[records.length];
        for (int i = 0; i < records.length; i++) {
            recordNumbers[i] = records[i].recordNumber;
            contentLengthWords[i] = 10;
        }

        writeShpFile(
                path,
                ShapeType.POINT,
                recordNumbers,
                contentLengthWords,
                buf -> {
                    buf.putInt(ShapeType.POINT.code());
                    buf.putDouble(records[0].x);
                    buf.putDouble(records[0].y);
                },
                records);
    }

    @FunctionalInterface
    interface RecordContentWriter {
        void write(ByteBuffer buf);
    }

    private void writeShpFile(Path path, ShapeType shapeType, int[] recordNumbers, int[] contentLengthWords)
            throws IOException {
        writeShpFile(path, shapeType, recordNumbers, contentLengthWords, buf -> {});
    }

    private void writeShpFile(
            Path path,
            ShapeType shapeType,
            int[] recordNumbers,
            int[] contentLengthWords,
            RecordContentWriter contentWriter,
            PointRecord... extraRecords)
            throws IOException {

        int records = recordNumbers.length;
        int totalContentBytes = 100;
        for (int w : contentLengthWords) {
            totalContentBytes += 8 + w * 2;
        }

        ByteBuffer buf = ByteBuffer.allocate(totalContentBytes);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putInt(9994);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(totalContentBytes / 2);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(1000);
        buf.putInt(shapeType.code());
        for (int i = 0; i < 8; i++) {
            buf.putDouble(0.0);
        }

        for (int i = 0; i < records; i++) {
            buf.order(ByteOrder.BIG_ENDIAN);
            buf.putInt(recordNumbers[i]);
            buf.putInt(contentLengthWords[i]);
            buf.order(ByteOrder.LITTLE_ENDIAN);

            int contentPos = buf.position();
            if (extraRecords.length > 0 && i < extraRecords.length) {
                PointRecord rec = extraRecords[i];
                buf.order(ByteOrder.LITTLE_ENDIAN);
                buf.putInt(ShapeType.POINT.code());
                buf.putDouble(rec.x);
                buf.putDouble(rec.y);
            } else {
                contentWriter.write(buf);
            }

            int bytesWritten = buf.position() - contentPos;
            int expected = contentLengthWords[i] * 2;
            while (bytesWritten < expected) {
                buf.put((byte) 0);
                bytesWritten++;
            }
        }

        buf.flip();
        byte[] data = new byte[buf.remaining()];
        buf.get(data);
        Files.write(path, data);
    }

    private void writeTruncatedRecordShp(Path path) throws IOException {
        int totalBytes = 100 + 8 + 20;
        int fileLengthWords = totalBytes / 2;

        ByteBuffer buf = ByteBuffer.allocate(totalBytes);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putInt(9994);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(fileLengthWords);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(1000);
        buf.putInt(ShapeType.POINT.code());
        for (int i = 0; i < 8; i++) {
            buf.putDouble(0.0);
        }

        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putInt(1);
        buf.putInt(10);

        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(ShapeType.POINT.code());
        buf.putDouble(7.5);

        buf.flip();
        byte[] data = new byte[buf.remaining()];
        buf.get(data);
        Files.write(path, data);
    }

    private record PointRecord(int recordNumber, double x, double y) {}
}
