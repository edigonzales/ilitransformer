package guru.interlis.transformer.io.shp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import guru.interlis.transformer.io.shp.ShapefileMappingException;

import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DbfWriterTest {

    @TempDir
    Path tempDir;

    private final List<DbfField> fields = List.of(
            new DbfField("NAME", DbfFieldType.CHARACTER, 10, 0),
            new DbfField("VAL", DbfFieldType.NUMERIC, 8, 2),
            new DbfField("FLAG", DbfFieldType.LOGICAL, 1, 0),
            new DbfField("DT", DbfFieldType.DATE, 8, 0));

    @Test
    void writesHeaderAndRecordsReadableByDbfReader() throws Exception {
        Path dbf = tempDir.resolve("data.dbf");
        try (FileChannel channel = openWrite(dbf)) {
            DbfWriter writer = new DbfWriter(channel, fields, StandardCharsets.UTF_8);
            assertThat(writer.headerLength()).isEqualTo(32 + 4 * 32 + 1);
            assertThat(writer.recordLength()).isEqualTo(1 + 10 + 8 + 1 + 8);

            writer.writeHeader(0);
            writer.writeRecord(new Object[] {"Bern", 12.5, Boolean.TRUE, LocalDate.of(2026, 6, 25)});
            writer.writeRecord(new Object[] {"Zürich", -3, Boolean.FALSE, "2020-01-02"});
            writer.writeEndOfFile();
            writer.patchRecordCount(2);
        }

        try (DbfReader reader = DbfReader.open(dbf, StandardCharsets.UTF_8)) {
            assertThat(reader.header().recordCount()).isEqualTo(2);
            assertThat(reader.fields()).hasSize(4);

            Optional<DbfRecord> r1 = reader.readNext();
            assertThat(r1).isPresent();
            assertThat(r1.get().deleted()).isFalse();
            assertThat(r1.get().values().get(0).trim()).isEqualTo("Bern");
            assertThat(r1.get().values().get(1).trim()).isEqualTo("12.50");
            assertThat(r1.get().values().get(2).trim()).isEqualTo("T");
            assertThat(r1.get().values().get(3).trim()).isEqualTo("20260625");

            Optional<DbfRecord> r2 = reader.readNext();
            assertThat(r2).isPresent();
            assertThat(r2.get().values().get(0).trim()).isEqualTo("Zürich");
            assertThat(r2.get().values().get(1).trim()).isEqualTo("-3.00");
            assertThat(r2.get().values().get(2).trim()).isEqualTo("F");
            assertThat(r2.get().values().get(3).trim()).isEqualTo("20200102");

            assertThat(reader.readNext()).isEmpty();
        }
    }

    @Test
    void writesEmptyValuesForNulls() throws Exception {
        Path dbf = tempDir.resolve("nulls.dbf");
        try (FileChannel channel = openWrite(dbf)) {
            DbfWriter writer = new DbfWriter(channel, fields, StandardCharsets.UTF_8);
            writer.writeHeader(0);
            writer.writeRecord(new Object[] {null, null, null, null});
            writer.writeEndOfFile();
            writer.patchRecordCount(1);
        }

        try (DbfReader reader = DbfReader.open(dbf, StandardCharsets.UTF_8)) {
            DbfRecord r = reader.readNext().orElseThrow();
            assertThat(r.values().get(0).trim()).isEmpty();
            assertThat(r.values().get(1).trim()).isEmpty();
            assertThat(r.values().get(2).trim()).isEmpty();
            assertThat(r.values().get(3).trim()).isEmpty();
        }
    }

    @Test
    void rejectsValueThatDoesNotFitField() throws Exception {
        Path dbf = tempDir.resolve("overflow.dbf");
        try (FileChannel channel = openWrite(dbf)) {
            DbfWriter writer = new DbfWriter(channel, fields, StandardCharsets.UTF_8);
            writer.writeHeader(0);
            assertThatThrownBy(() ->
                            writer.writeRecord(new Object[] {"ThisNameIsWayTooLong", 1, Boolean.TRUE, LocalDate.now()}))
                    .isInstanceOf(ShapefileMappingException.class)
                    .hasMessageContaining("field width");
        }
    }

    @Test
    void truncatesCharacterValuesByteSafelyWhenConfigured() throws Exception {
        Path dbf = tempDir.resolve("truncate.dbf");
        List<DbfField> truncateFields = List.of(new DbfField("NAME", DbfFieldType.CHARACTER, 5, 0));
        try (FileChannel channel = openWrite(dbf)) {
            DbfWriter writer = new DbfWriter(
                    channel, truncateFields, StandardCharsets.UTF_8, ShapefileWriteOptions.OverflowPolicy.TRUNCATE);
            writer.writeHeader(0);
            writer.writeRecord(new Object[] {"äbcdef"});
            writer.writeEndOfFile();
            writer.patchRecordCount(1);
        }

        try (DbfReader reader = DbfReader.open(dbf, StandardCharsets.UTF_8)) {
            assertThat(reader.readNext().orElseThrow().values().get(0).trim()).isEqualTo("äbcd");
        }
    }

    @Test
    void truncatePolicyKeepsNonCharacterFieldsStrict() throws Exception {
        assertThatThrownBy(() ->
                        writeSingleTruncateField(new DbfField("NUM", DbfFieldType.NUMERIC, 2, 0), "123", "num.dbf"))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("field width");
        assertThatThrownBy(() -> writeSingleTruncateField(
                        new DbfField("DATE", DbfFieldType.DATE, 4, 0), LocalDate.of(2026, 7, 1), "date.dbf"))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("field width");
        assertThatThrownBy(() -> writeSingleTruncateField(
                        new DbfField("FLAG", DbfFieldType.LOGICAL, 0, 0), Boolean.TRUE, "logical.dbf"))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("field width");
    }

    @Test
    void rejectsWrongValueCount() throws Exception {
        Path dbf = tempDir.resolve("count.dbf");
        try (FileChannel channel = openWrite(dbf)) {
            DbfWriter writer = new DbfWriter(channel, fields, StandardCharsets.UTF_8);
            writer.writeHeader(0);
            assertThatThrownBy(() -> writer.writeRecord(new Object[] {"a"}))
                    .isInstanceOf(ShapefileMappingException.class)
                    .hasMessageContaining("4 fields");
        }
    }

    private static FileChannel openWrite(Path path) throws Exception {
        return FileChannel.open(
                path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void writeSingleTruncateField(DbfField field, Object value, String fileName) throws Exception {
        Path dbf = tempDir.resolve(fileName);
        try (FileChannel channel = openWrite(dbf)) {
            DbfWriter writer = new DbfWriter(
                    channel, List.of(field), StandardCharsets.UTF_8, ShapefileWriteOptions.OverflowPolicy.TRUNCATE);
            writer.writeHeader(0);
            writer.writeRecord(new Object[] {value});
        }
    }
}
