package guru.interlis.transformer.io.shp.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import guru.interlis.transformer.io.shp.ShapefileMappingException;
import guru.interlis.transformer.io.shp.ShapefileOptions;
import guru.interlis.transformer.io.shp.core.DbfField;
import guru.interlis.transformer.io.shp.core.DbfFieldType;
import guru.interlis.transformer.io.shp.core.DbfRecord;

import ch.interlis.iom_j.Iom_jObject;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class DbfToIomMapperTest {

    private static final List<DbfField> STATION_FIELDS = List.of(
            new DbfField("BFS_NR", DbfFieldType.NUMERIC, 10, 0), new DbfField("NAME", DbfFieldType.CHARACTER, 80, 0));
    private static final List<DbfField> TYPED_FIELDS = List.of(
            new DbfField("DT", DbfFieldType.DATE, 8, 0),
            new DbfField("COUNT", DbfFieldType.NUMERIC, 6, 0),
            new DbfField("RATIO", DbfFieldType.FLOAT, 8, 2),
            new DbfField("FLAG_T", DbfFieldType.LOGICAL, 1, 0),
            new DbfField("FLAG_Y", DbfFieldType.LOGICAL, 1, 0),
            new DbfField("FLAG_1", DbfFieldType.LOGICAL, 1, 0),
            new DbfField("FLAG_F", DbfFieldType.LOGICAL, 1, 0),
            new DbfField("FLAG_N", DbfFieldType.LOGICAL, 1, 0),
            new DbfField("FLAG_0", DbfFieldType.LOGICAL, 1, 0),
            new DbfField("FLAG_U", DbfFieldType.LOGICAL, 1, 0));

    @Test
    void mapsDbfFieldsToAttributes() throws Exception {
        var mapper = new DbfToIomMapper(
                "Demo.Topic.Station",
                Optional.empty(),
                Map.of(),
                STATION_FIELDS,
                ShapefileOptions.DeletedRecordPolicy.ERROR);

        DbfRecord record = new DbfRecord(false, List.of("  2601  ", "  Solothurn  "));
        Iom_jObject obj = mapper.map(1, record);

        assertThat(obj).isNotNull();
        assertThat(obj.getobjecttag()).isEqualTo("Demo.Topic.Station");
        assertThat(obj.getattrvalue("BFS_NR")).isEqualTo("2601");
        assertThat(obj.getattrvalue("NAME")).isEqualTo("Solothurn");
    }

    @Test
    void generatesSyntheticOidWhenNoOidField() throws Exception {
        var mapper = new DbfToIomMapper(
                "Demo.Topic.Station",
                Optional.empty(),
                Map.of(),
                STATION_FIELDS,
                ShapefileOptions.DeletedRecordPolicy.ERROR);

        DbfRecord record = new DbfRecord(false, List.of("1", "Test"));
        Iom_jObject obj = mapper.map(3, record);

        assertThat(obj.getobjectoid()).isEqualTo("shp.3");
    }

    @Test
    void usesOidFromConfiguredField() throws Exception {
        var mapper = new DbfToIomMapper(
                "Demo.Topic.Station",
                Optional.of("BFS_NR"),
                Map.of(),
                STATION_FIELDS,
                ShapefileOptions.DeletedRecordPolicy.ERROR);

        DbfRecord record = new DbfRecord(false, List.of("2601", "Solothurn"));
        Iom_jObject obj = mapper.map(1, record);

        assertThat(obj.getobjectoid()).isEqualTo("2601");
    }

    @Test
    void fallsBackToSyntheticOidWhenOidFieldValueEmpty() throws Exception {
        var mapper = new DbfToIomMapper(
                "Demo.Topic.Station",
                Optional.of("BFS_NR"),
                Map.of(),
                STATION_FIELDS,
                ShapefileOptions.DeletedRecordPolicy.ERROR);

        DbfRecord record = new DbfRecord(false, List.of("   ", "  Solothurn  "));
        Iom_jObject obj = mapper.map(5, record);

        assertThat(obj.getobjectoid()).isEqualTo("shp.5");
    }

    @Test
    void appliesColumnMappings() throws Exception {
        var mapper = new DbfToIomMapper(
                "Demo.Topic.Station",
                Optional.empty(),
                Map.of("BFS_NR", "bfsnr", "NAME", "name"),
                STATION_FIELDS,
                ShapefileOptions.DeletedRecordPolicy.ERROR);

        DbfRecord record = new DbfRecord(false, List.of("2601", "Solothurn"));
        Iom_jObject obj = mapper.map(1, record);

        assertThat(obj.getattrvalue("bfsnr")).isEqualTo("2601");
        assertThat(obj.getattrvalue("name")).isEqualTo("Solothurn");
        assertThat(obj.getattrvalue("BFS_NR")).isNull();
    }

    @Test
    void nullDbfValuesAreSkipped() throws Exception {
        var mapper = new DbfToIomMapper(
                "Demo.Topic.Station",
                Optional.empty(),
                Map.of(),
                STATION_FIELDS,
                ShapefileOptions.DeletedRecordPolicy.ERROR);

        DbfRecord record = new DbfRecord(false, Arrays.asList(null, "Name"));
        Iom_jObject obj = mapper.map(1, record);

        assertThat(obj.getattrvalue("BFS_NR")).isNull();
        assertThat(obj.getattrvalue("NAME")).isEqualTo("Name");
    }

    @Test
    void emptyStringsAreSkipped() throws Exception {
        var mapper = new DbfToIomMapper(
                "Demo.Topic.Station",
                Optional.empty(),
                Map.of(),
                STATION_FIELDS,
                ShapefileOptions.DeletedRecordPolicy.ERROR);

        DbfRecord record = new DbfRecord(false, List.of("   ", "Name"));
        Iom_jObject obj = mapper.map(1, record);

        assertThat(obj.getattrvalue("BFS_NR")).isNull();
        assertThat(obj.getattrvalue("NAME")).isEqualTo("Name");
    }

    @Test
    void deletedRecordErrorPolicyThrows() throws Exception {
        var mapper = new DbfToIomMapper(
                "Demo.Topic.Station",
                Optional.empty(),
                Map.of(),
                STATION_FIELDS,
                ShapefileOptions.DeletedRecordPolicy.ERROR);

        DbfRecord record = new DbfRecord(true, List.of("1", "Test"));

        assertThatThrownBy(() -> mapper.map(1, record))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("deleted");
    }

    @Test
    void deletedRecordSkipPolicyReturnsNull() throws Exception {
        var mapper = new DbfToIomMapper(
                "Demo.Topic.Station",
                Optional.empty(),
                Map.of(),
                STATION_FIELDS,
                ShapefileOptions.DeletedRecordPolicy.SKIP);

        DbfRecord record = new DbfRecord(true, List.of("1", "Test"));
        Iom_jObject obj = mapper.map(1, record);

        assertThat(obj).isNull();
    }

    @Test
    void oidFieldAlsoSetAsAttribute() throws Exception {
        var mapper = new DbfToIomMapper(
                "Demo.Topic.Station",
                Optional.of("BFS_NR"),
                Map.of(),
                STATION_FIELDS,
                ShapefileOptions.DeletedRecordPolicy.ERROR);

        DbfRecord record = new DbfRecord(false, List.of("2601", "Solothurn"));
        Iom_jObject obj = mapper.map(1, record);

        assertThat(obj.getobjectoid()).isEqualTo("2601");
        assertThat(obj.getattrvalue("BFS_NR")).isEqualTo("2601");
        assertThat(obj.getattrvalue("NAME")).isEqualTo("Solothurn");
    }

    @Test
    void normalizesDateAndLogicalDbfValues() throws Exception {
        var mapper = new DbfToIomMapper(
                "Demo.Topic.Typed",
                Optional.empty(),
                Map.of(),
                TYPED_FIELDS,
                ShapefileOptions.DeletedRecordPolicy.ERROR);

        DbfRecord record = new DbfRecord(false, List.of("20131021", "42", "3.50", "T", "Y", "1", "F", "N", "0", "?"));
        Iom_jObject obj = mapper.map(1, record);

        assertThat(obj.getattrvalue("DT")).isEqualTo("2013-10-21");
        assertThat(obj.getattrvalue("COUNT")).isEqualTo("42");
        assertThat(obj.getattrvalue("RATIO")).isEqualTo("3.50");
        assertThat(obj.getattrvalue("FLAG_T")).isEqualTo("true");
        assertThat(obj.getattrvalue("FLAG_Y")).isEqualTo("true");
        assertThat(obj.getattrvalue("FLAG_1")).isEqualTo("true");
        assertThat(obj.getattrvalue("FLAG_F")).isEqualTo("false");
        assertThat(obj.getattrvalue("FLAG_N")).isEqualTo("false");
        assertThat(obj.getattrvalue("FLAG_0")).isEqualTo("false");
        assertThat(obj.getattrvalue("FLAG_U")).isEqualTo("?");
    }

    @Test
    void skipsDbfNullSentinels() throws Exception {
        var mapper = new DbfToIomMapper(
                "Demo.Topic.Typed",
                Optional.empty(),
                Map.of(),
                TYPED_FIELDS,
                ShapefileOptions.DeletedRecordPolicy.ERROR);

        DbfRecord record =
                new DbfRecord(false, List.of("00000000", "******", "********", "T", "Y", "1", "F", "N", "0", "?"));
        Iom_jObject obj = mapper.map(1, record);

        assertThat(obj.getattrvalue("DT")).isNull();
        assertThat(obj.getattrvalue("COUNT")).isNull();
        assertThat(obj.getattrvalue("RATIO")).isNull();
        assertThat(obj.getattrvalue("FLAG_T")).isEqualTo("true");
    }
}
