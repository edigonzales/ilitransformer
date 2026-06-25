package guru.interlis.transformer.io.shp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import guru.interlis.transformer.io.shp.ShapefileMappingException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DbfReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void readsEmptyDbf() throws Exception {
        byte[] dbfBytes = createDbfBytes(List.of(), 0);
        Path dbf = tempDir.resolve("empty.dbf");
        Files.write(dbf, dbfBytes);

        try (DbfReader reader = DbfReader.open(dbf, StandardCharsets.ISO_8859_1)) {
            DbfHeader header = reader.header();
            assertThat(header.recordCount()).isEqualTo(0);
            assertThat(reader.readNext()).isEmpty();
            assertThat(reader.currentRecordNumber()).isEqualTo(0);
        }
    }

    @Test
    void readsHeaderFields() throws Exception {
        List<DbfField> fields = List.of(
                new DbfField("NAME", DbfFieldType.CHARACTER, 20, 0),
                new DbfField("VALUE", DbfFieldType.NUMERIC, 10, 2),
                new DbfField("SCORE", DbfFieldType.FLOAT, 12, 4),
                new DbfField("ACTIVE", DbfFieldType.LOGICAL, 1, 0),
                new DbfField("UPDATED", DbfFieldType.DATE, 8, 0));

        byte[] dbfBytes = createDbfBytes(fields, 0);
        Path dbf = tempDir.resolve("header.dbf");
        Files.write(dbf, dbfBytes);

        try (DbfReader reader = DbfReader.open(dbf, StandardCharsets.ISO_8859_1)) {
            DbfHeader header = reader.header();
            List<DbfField> readFields = reader.fields();

            assertThat(readFields).hasSize(5);
            assertThat(readFields.get(0).name()).isEqualTo("NAME");
            assertThat(readFields.get(0).type()).isEqualTo(DbfFieldType.CHARACTER);
            assertThat(readFields.get(0).length()).isEqualTo(20);
            assertThat(readFields.get(0).decimalCount()).isEqualTo(0);

            assertThat(readFields.get(1).name()).isEqualTo("VALUE");
            assertThat(readFields.get(1).type()).isEqualTo(DbfFieldType.NUMERIC);
            assertThat(readFields.get(1).length()).isEqualTo(10);
            assertThat(readFields.get(1).decimalCount()).isEqualTo(2);

            assertThat(readFields.get(2).name()).isEqualTo("SCORE");
            assertThat(readFields.get(2).type()).isEqualTo(DbfFieldType.FLOAT);
            assertThat(readFields.get(2).length()).isEqualTo(12);
            assertThat(readFields.get(2).decimalCount()).isEqualTo(4);

            assertThat(readFields.get(3).name()).isEqualTo("ACTIVE");
            assertThat(readFields.get(3).type()).isEqualTo(DbfFieldType.LOGICAL);

            assertThat(readFields.get(4).name()).isEqualTo("UPDATED");
            assertThat(readFields.get(4).type()).isEqualTo(DbfFieldType.DATE);
        }
    }

    @Test
    void readsRecordsWithVariousFieldTypes() throws Exception {
        List<DbfField> fields = List.of(
                new DbfField("NAME", DbfFieldType.CHARACTER, 10, 0), new DbfField("NUM", DbfFieldType.NUMERIC, 8, 2));

        byte[] dbfBytes = createDbfBytes(
                fields, List.of(new String[] {"Hello     ", "  123.45"}, new String[] {"World     ", "  -99.00"}));

        Path dbf = tempDir.resolve("records.dbf");
        Files.write(dbf, dbfBytes);

        try (DbfReader reader = DbfReader.open(dbf, StandardCharsets.ISO_8859_1)) {
            Optional<DbfRecord> r1 = reader.readNext();
            assertThat(r1).isPresent();
            assertThat(r1.get().deleted()).isFalse();
            assertThat(r1.get().values()).containsExactly("Hello     ", "  123.45");

            Optional<DbfRecord> r2 = reader.readNext();
            assertThat(r2).isPresent();
            assertThat(r2.get().deleted()).isFalse();
            assertThat(r2.get().values()).containsExactly("World     ", "  -99.00");

            assertThat(reader.readNext()).isEmpty();
            assertThat(reader.currentRecordNumber()).isEqualTo(2);
        }
    }

    @Test
    void detectsDeletedRecords() throws Exception {
        List<DbfField> fields = List.of(new DbfField("X", DbfFieldType.CHARACTER, 5, 0));

        byte[] record0 = buildRecordBytes(true, new String[] {"x1   "});
        byte[] record1 = buildRecordBytes(false, new String[] {"x2   "});

        byte[] dbfBytes = createDbfBytesWithRawRecords(fields, List.of(record0, record1));
        Path dbf = tempDir.resolve("deleted.dbf");
        Files.write(dbf, dbfBytes);

        try (DbfReader reader = DbfReader.open(dbf, StandardCharsets.ISO_8859_1)) {
            Optional<DbfRecord> r1 = reader.readNext();
            assertThat(r1).isPresent();
            assertThat(r1.get().deleted()).isTrue();

            Optional<DbfRecord> r2 = reader.readNext();
            assertThat(r2).isPresent();
            assertThat(r2.get().deleted()).isFalse();
        }
    }

    @Test
    void readsRecordsWithUtf8Encoding() throws Exception {
        List<DbfField> fields = List.of(new DbfField("TEXT", DbfFieldType.CHARACTER, 20, 0));

        byte[] dbfBytes = createDbfBytes(fields, List.<String[]>of(new String[] {"Hello UTF8 World    "}));

        Path dbf = tempDir.resolve("utf8.dbf");
        Files.write(dbf, dbfBytes);

        try (DbfReader reader = DbfReader.open(dbf, StandardCharsets.UTF_8)) {
            Optional<DbfRecord> r = reader.readNext();
            assertThat(r).isPresent();
            assertThat(r.get().values().get(0)).startsWith("Hello UTF8");
        }
    }

    @Test
    void readsRecordsWithLatin1Encoding() throws Exception {
        List<DbfField> fields = List.of(new DbfField("TEXT", DbfFieldType.CHARACTER, 10, 0));

        byte[] record = buildRecordBytes(false, new String[] {"Hello     "});
        byte[] dbfBytes = createDbfBytesWithRawRecords(fields, List.of(record));
        Path dbf = tempDir.resolve("latin1.dbf");
        Files.write(dbf, dbfBytes);

        try (DbfReader reader = DbfReader.open(dbf, StandardCharsets.ISO_8859_1)) {
            Optional<DbfRecord> r = reader.readNext();
            assertThat(r).isPresent();
            assertThat(r.get().values().get(0)).startsWith("Hello");
        }
    }

    @Test
    void readsHeaderMetadata() throws Exception {
        List<DbfField> fields = List.of(new DbfField("F1", DbfFieldType.CHARACTER, 5, 0));

        byte[] dbfBytes = createDbfBytes(fields, 0);
        Path dbf = tempDir.resolve("meta.dbf");
        Files.write(dbf, dbfBytes);

        try (DbfReader reader = DbfReader.open(dbf, StandardCharsets.ISO_8859_1)) {
            DbfHeader header = reader.header();
            assertThat(header.version()).isEqualTo((byte) 0x03);
            assertThat(header.lastUpdateYear()).isEqualTo(125);
            assertThat(header.lastUpdateMonth()).isEqualTo(6);
            assertThat(header.lastUpdateDay()).isEqualTo(25);
            assertThat(header.recordCount()).isEqualTo(0);
            assertThat(header.headerLength()).isGreaterThan(32);
            assertThat(header.recordLength()).isGreaterThan(0);
        }
    }

    @Test
    void readsMultipleRecordsSequentially() throws Exception {
        List<DbfField> fields = List.of(new DbfField("ID", DbfFieldType.NUMERIC, 6, 0));

        byte[] dbfBytes = createDbfBytes(
                fields,
                List.of(
                        new String[] {"     1"},
                        new String[] {"     2"},
                        new String[] {"     3"},
                        new String[] {"     4"},
                        new String[] {"     5"}));

        Path dbf = tempDir.resolve("many.dbf");
        Files.write(dbf, dbfBytes);

        try (DbfReader reader = DbfReader.open(dbf, StandardCharsets.ISO_8859_1)) {
            for (int i = 1; i <= 5; i++) {
                Optional<DbfRecord> r = reader.readNext();
                assertThat(r).isPresent();
                assertThat(r.get().values().get(0).trim()).isEqualTo(String.valueOf(i));
            }
            assertThat(reader.readNext()).isEmpty();
            assertThat(reader.currentRecordNumber()).isEqualTo(5);
        }
    }

    @Test
    void rejectsUnsupportedFieldType() throws Exception {
        List<DbfField> validField = List.of(new DbfField("F1", DbfFieldType.CHARACTER, 10, 0));
        byte[] dbfBytes = createDbfBytesWithMemoField(validField);
        Path dbf = tempDir.resolve("memo.dbf");
        Files.write(dbf, dbfBytes);

        assertThatThrownBy(() -> DbfReader.open(dbf, StandardCharsets.ISO_8859_1))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("M")
                .hasMessageContaining("Unsupported");
    }

    @Test
    void returnsHeaderWithCorrectRecordCount() throws Exception {
        List<DbfField> fields = List.of(new DbfField("VAL", DbfFieldType.CHARACTER, 3, 0));

        byte[] dbfBytes =
                createDbfBytes(fields, List.of(new String[] {"ABC"}, new String[] {"DEF"}, new String[] {"GHI"}));

        Path dbf = tempDir.resolve("count.dbf");
        Files.write(dbf, dbfBytes);

        try (DbfReader reader = DbfReader.open(dbf, StandardCharsets.ISO_8859_1)) {
            assertThat(reader.header().recordCount()).isEqualTo(3);
            int count = 0;
            while (reader.readNext().isPresent()) count++;
            assertThat(count).isEqualTo(3);
        }
    }

    @Test
    void headerRecordLengthMatchesRecordSize() throws Exception {
        List<DbfField> fields = List.of(
                new DbfField("A", DbfFieldType.CHARACTER, 5, 0), new DbfField("B", DbfFieldType.NUMERIC, 10, 2));

        byte[] dbfBytes = createDbfBytes(fields, 0);
        Path dbf = tempDir.resolve("reclen.dbf");
        Files.write(dbf, dbfBytes);

        try (DbfReader reader = DbfReader.open(dbf, StandardCharsets.ISO_8859_1)) {
            int expectedRecordLength = 1 + 5 + 10;
            assertThat(reader.header().recordLength()).isEqualTo(expectedRecordLength);
        }
    }

    @Test
    void closeAllowsReopenOnSameFile() throws Exception {
        List<DbfField> fields = List.of(new DbfField("X", DbfFieldType.CHARACTER, 5, 0));
        byte[] dbfBytes = createDbfBytes(fields, List.<String[]>of(new String[] {"hello"}));
        Path dbf = tempDir.resolve("reopen.dbf");
        Files.write(dbf, dbfBytes);

        try (DbfReader reader = DbfReader.open(dbf, StandardCharsets.ISO_8859_1)) {
            assertThat(reader.readNext()).isPresent();
        }

        try (DbfReader reader = DbfReader.open(dbf, StandardCharsets.ISO_8859_1)) {
            assertThat(reader.header().recordCount()).isEqualTo(1);
            assertThat(reader.readNext()).isPresent();
            assertThat(reader.readNext()).isEmpty();
        }
    }

    @Test
    void trimsFieldNameNullBytes() throws Exception {
        List<DbfField> fields = List.of(new DbfField("SHORT", DbfFieldType.CHARACTER, 3, 0));
        byte[] dbfBytes = createDbfBytes(fields, 0);
        Path dbf = tempDir.resolve("trim.dbf");
        Files.write(dbf, dbfBytes);

        try (DbfReader reader = DbfReader.open(dbf, StandardCharsets.ISO_8859_1)) {
            assertThat(reader.fields().get(0).name()).isEqualTo("SHORT");
        }
    }

    private static byte[] createDbfBytes(List<DbfField> fields, int recordCount) {
        return createDbfBytes(fields, recordCount, List.of());
    }

    private static byte[] createDbfBytes(List<DbfField> fields, List<String[]> rawRecords) {
        return createDbfBytes(fields, rawRecords.size(), rawRecords);
    }

    private static byte[] createDbfBytes(List<DbfField> fields, int recordCount, List<String[]> rawRecords) {
        int headerLength = 32 + 1 + fields.size() * 32;
        int recordLength = 1;
        for (DbfField f : fields) {
            recordLength += f.length();
        }

        int totalSize = headerLength + recordCount * recordLength;
        ByteBuffer buf = ByteBuffer.allocate(totalSize);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        buf.put((byte) 0x03);
        buf.put((byte) 125);
        buf.put((byte) 6);
        buf.put((byte) 25);
        buf.putInt(recordCount);
        buf.putShort((short) headerLength);
        buf.putShort((short) recordLength);

        for (int i = 0; i < 20; i++) buf.put((byte) 0);

        for (DbfField field : fields) {
            byte[] nameBytes = field.name().getBytes(StandardCharsets.US_ASCII);
            byte[] fullName = new byte[11];
            System.arraycopy(nameBytes, 0, fullName, 0, Math.min(nameBytes.length, 11));
            buf.put(fullName);
            buf.put((byte) field.type().code());
            for (int i = 0; i < 4; i++) buf.put((byte) 0);
            buf.put((byte) field.length());
            buf.put((byte) field.decimalCount());
            for (int i = 0; i < 14; i++) buf.put((byte) 0);
        }

        buf.put((byte) 0x0D);

        for (String[] record : rawRecords) {
            buf.put((byte) 0x20);
            for (int col = 0; col < fields.size(); col++) {
                String rawVal = record[col];
                byte[] fieldBytes;
                try {
                    fieldBytes = rawVal.getBytes(StandardCharsets.ISO_8859_1);
                } catch (Exception e) {
                    fieldBytes = rawVal.getBytes(StandardCharsets.UTF_8);
                }
                byte[] padded = new byte[fields.get(col).length()];
                System.arraycopy(fieldBytes, 0, padded, 0, Math.min(fieldBytes.length, padded.length));
                for (int i = fieldBytes.length; i < padded.length; i++) padded[i] = (byte) ' ';
                buf.put(padded);
            }
        }

        return buf.array();
    }

    private static byte[] createDbfBytesWithRawRecords(List<DbfField> fields, List<byte[]> records) {
        int headerLength = 32 + 1 + fields.size() * 32;
        int recordLength = 1;
        for (DbfField f : fields) {
            recordLength += f.length();
        }

        int totalSize = headerLength + records.size() * recordLength;
        ByteBuffer buf = ByteBuffer.allocate(totalSize);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        buf.put((byte) 0x03);
        buf.put((byte) 125);
        buf.put((byte) 6);
        buf.put((byte) 25);
        buf.putInt(records.size());
        buf.putShort((short) headerLength);
        buf.putShort((short) recordLength);

        for (int i = 0; i < 20; i++) buf.put((byte) 0);

        for (DbfField field : fields) {
            byte[] nameBytes = field.name().getBytes(StandardCharsets.US_ASCII);
            byte[] fullName = new byte[11];
            System.arraycopy(nameBytes, 0, fullName, 0, Math.min(nameBytes.length, 11));
            buf.put(fullName);
            buf.put((byte) field.type().code());
            for (int i = 0; i < 4; i++) buf.put((byte) 0);
            buf.put((byte) field.length());
            buf.put((byte) field.decimalCount());
            for (int i = 0; i < 14; i++) buf.put((byte) 0);
        }

        buf.put((byte) 0x0D);

        for (byte[] record : records) {
            buf.put(record);
        }

        return buf.array();
    }

    private static byte[] buildRecordBytes(boolean deleted, String[] values) {
        int recordLength = 1;
        for (String v : values) {
            recordLength += v.length();
        }
        ByteBuffer buf = ByteBuffer.allocate(recordLength);
        buf.put(deleted ? (byte) 0x2A : (byte) 0x20);
        for (String v : values) {
            buf.put(v.getBytes(StandardCharsets.ISO_8859_1));
        }
        return buf.array();
    }

    private static byte[] createDbfBytesWithMemoField(List<DbfField> fields) {
        int headerLength = 32 + 1 + (fields.size() + 1) * 32;

        List<DbfField> allFields = new java.util.ArrayList<>(fields);
        allFields.add(new DbfField("MEMO", DbfFieldType.CHARACTER, 10, 0));

        int recordLength = 1;
        for (DbfField f : allFields) {
            recordLength += f.length();
        }

        int totalSize = headerLength + 5 * recordLength;
        ByteBuffer buf = ByteBuffer.allocate(totalSize);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        buf.put((byte) 0x03);
        buf.put((byte) 125);
        buf.put((byte) 6);
        buf.put((byte) 25);
        buf.putInt(5);
        buf.putShort((short) headerLength);
        buf.putShort((short) recordLength);

        for (int i = 0; i < 20; i++) buf.put((byte) 0);

        for (DbfField field : fields) {
            byte[] nameBytes = field.name().getBytes(StandardCharsets.US_ASCII);
            byte[] fullName = new byte[11];
            System.arraycopy(nameBytes, 0, fullName, 0, Math.min(nameBytes.length, 11));
            buf.put(fullName);
            buf.put((byte) field.type().code());
            for (int i = 0; i < 4; i++) buf.put((byte) 0);
            buf.put((byte) field.length());
            buf.put((byte) field.decimalCount());
            for (int i = 0; i < 14; i++) buf.put((byte) 0);
        }

        byte[] memoName = "MEMO".getBytes(StandardCharsets.US_ASCII);
        byte[] fullMemoName = new byte[11];
        System.arraycopy(memoName, 0, fullMemoName, 0, memoName.length);
        buf.put(fullMemoName);
        buf.put((byte) 'M');
        for (int i = 0; i < 4; i++) buf.put((byte) 0);
        buf.put((byte) 10);
        buf.put((byte) 0);
        for (int i = 0; i < 14; i++) buf.put((byte) 0);

        buf.put((byte) 0x0D);

        return buf.array();
    }
}
