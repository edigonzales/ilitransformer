package guru.interlis.transformer.io.shp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import guru.interlis.transformer.io.FormatOptions;
import guru.interlis.transformer.io.shp.geom.GeometryKind;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ShapefileOptionsTest {

    @Test
    void readsClassName() {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of("class", "Model.Topic.Class")));

        assertThat(opts.className()).hasValue("Model.Topic.Class");
    }

    @Test
    void classNameEmptyWhenMissing() {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of()));

        assertThat(opts.className()).isEmpty();
    }

    @Test
    void classNameEmptyWhenBlank() {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of("class", "  ")));

        assertThat(opts.className()).isEmpty();
    }

    @Test
    void readsTopicName() {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of("topic", "Model.Data")));

        assertThat(opts.topicName()).hasValue("Model.Data");
    }

    @Test
    void topicNameDefaultEmpty() {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of()));

        assertThat(opts.topicName()).isEmpty();
    }

    @Test
    void basketIdDefaultsToB1() {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of()));

        assertThat(opts.basketId()).isEqualTo("b1");
    }

    @Test
    void basketIdConfigured() {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of("basketId", "myBasket")));

        assertThat(opts.basketId()).isEqualTo("myBasket");
    }

    @Test
    void readsOidField() {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of("oidField", "TID")));

        assertThat(opts.oidField()).hasValue("TID");
    }

    @Test
    void oidFieldEmptyWhenMissing() {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of()));

        assertThat(opts.oidField()).isEmpty();
    }

    @Test
    void readsGeometryAttribute() {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of("geometryAttribute", "geom")));

        assertThat(opts.geometryAttribute()).hasValue("geom");
    }

    @Test
    void readsGeometryType() {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of("geometryType", "coord")));

        assertThat(opts.geometryType()).hasValue(GeometryKind.COORD);
    }

    @Test
    void geometryTypeCaseInsensitive() {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of("geometryType", "COORD")));

        assertThat(opts.geometryType()).hasValue(GeometryKind.COORD);
    }

    @Test
    void geometryTypeEmptyWhenMissing() {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of()));

        assertThat(opts.geometryType()).isEmpty();
    }

    @Test
    void dbfCharsetDefaultsToIso88591() throws ShapefileMappingException {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of()));

        assertThat(opts.dbfCharset(Optional.empty())).isEqualTo(StandardCharsets.ISO_8859_1);
    }

    @Test
    void dbfCharsetConfigured() throws ShapefileMappingException {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of("dbfEncoding", "UTF-8")));

        assertThat(opts.dbfCharset(Optional.empty())).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void dbfCharsetConfiguredOverridesCpg() throws ShapefileMappingException {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of("dbfEncoding", "ISO-8859-1")));

        assertThat(opts.dbfCharset(Optional.of(StandardCharsets.UTF_8))).isEqualTo(StandardCharsets.ISO_8859_1);
    }

    @Test
    void dbfCharsetUsesCpgWhenNoOptionSet() throws ShapefileMappingException {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of()));

        assertThat(opts.dbfCharset(Optional.of(StandardCharsets.UTF_8))).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void dbfCharsetUsesCpgWhenDbfEncodingBlank() throws ShapefileMappingException {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of("dbfEncoding", "  ")));

        assertThat(opts.dbfCharset(Optional.of(StandardCharsets.US_ASCII))).isEqualTo(StandardCharsets.US_ASCII);
    }

    @Test
    void dbfCharsetInvalidThrows() {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of("dbfEncoding", "NOT_A_CHARSET")));

        assertThatThrownBy(() -> opts.dbfCharset(Optional.empty()))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("dbfEncoding");
    }

    @Test
    void zipMemberReturnsConfiguredValue() {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of("member", "parcels.shp")));

        assertThat(opts.zipMember()).hasValue("parcels.shp");
    }

    @Test
    void zipMemberEmptyWhenMissing() {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of()));

        assertThat(opts.zipMember()).isEmpty();
    }

    @Test
    void zipMemberEmptyWhenBlank() {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of("member", "  ")));

        assertThat(opts.zipMember()).isEmpty();
    }

    @Test
    void emptyColumnMappings() throws ShapefileMappingException {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of()));

        assertThat(opts.columnMappings()).isEmpty();
    }

    @Test
    void readsColumnMappings() throws ShapefileMappingException {
        ShapefileOptions opts =
                ShapefileOptions.input(FormatOptions.of(Map.of("column.BFS_NR", "bfsnr", "column.NAME", "name")));

        Map<String, String> mappings = opts.columnMappings();
        assertThat(mappings).containsEntry("BFS_NR", "bfsnr").containsEntry("NAME", "name");
    }

    @Test
    void columnMappingsIgnoreNonColumnKeys() throws ShapefileMappingException {
        ShapefileOptions opts =
                ShapefileOptions.input(FormatOptions.of(Map.of("column.BFS", "bfs", "class", "Model.T.Cls")));

        Map<String, String> mappings = opts.columnMappings();
        assertThat(mappings).containsOnlyKeys("BFS");
    }

    @Test
    void duplicateColumnMappingCaseInsensitiveThrows() {
        var map = new java.util.LinkedHashMap<String, String>();
        map.put("column.FIELD", "first");
        map.put("column.Field", "second");
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(map));

        assertThatThrownBy(() -> opts.columnMappings())
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("column.Field")
                .hasMessageContaining("conflicts");
    }

    @Test
    void deletedRecordPolicyDefaultsToError() throws ShapefileMappingException {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of()));

        assertThat(opts.deletedRecordPolicy()).isEqualTo(ShapefileOptions.DeletedRecordPolicy.ERROR);
    }

    @Test
    void deletedRecordPolicySkip() throws ShapefileMappingException {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of("deletedRecordPolicy", "skip")));

        assertThat(opts.deletedRecordPolicy()).isEqualTo(ShapefileOptions.DeletedRecordPolicy.SKIP);
    }

    @Test
    void deletedRecordPolicyInvalidThrows() {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of("deletedRecordPolicy", "ignore")));

        assertThatThrownBy(() -> opts.deletedRecordPolicy())
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("deletedRecordPolicy");
    }

    @Test
    void requireShxDefaultsFalse() {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of()));

        assertThat(opts.requireShx()).isFalse();
    }

    @Test
    void requireShxTrue() {
        ShapefileOptions opts = ShapefileOptions.input(FormatOptions.of(Map.of("requireShx", "true")));

        assertThat(opts.requireShx()).isTrue();
    }

    // --- output options (phase 8) ---

    @Test
    void outputDbfCharsetDefaultsToUtf8() throws ShapefileMappingException {
        ShapefileOptions opts = ShapefileOptions.output(FormatOptions.of(Map.of()));

        assertThat(opts.dbfCharset(Optional.empty())).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void shapeTypeOverrideParsesValues() throws ShapefileMappingException {
        assertThat(ShapefileOptions.output(FormatOptions.of(Map.of("shapeType", "point")))
                        .shapeTypeOverride())
                .hasValue(guru.interlis.transformer.io.shp.core.ShapeType.POINT);
        assertThat(ShapefileOptions.output(FormatOptions.of(Map.of("shapeType", "POLYLINE")))
                        .shapeTypeOverride())
                .hasValue(guru.interlis.transformer.io.shp.core.ShapeType.POLYLINE);
        assertThat(ShapefileOptions.output(FormatOptions.of(Map.of("shapeType", "polygon")))
                        .shapeTypeOverride())
                .hasValue(guru.interlis.transformer.io.shp.core.ShapeType.POLYGON);
    }

    @Test
    void shapeTypeOverrideEmptyWhenMissing() throws ShapefileMappingException {
        assertThat(ShapefileOptions.output(FormatOptions.of(Map.of())).shapeTypeOverride())
                .isEmpty();
    }

    @Test
    void shapeTypeOverrideInvalidThrows() {
        ShapefileOptions opts = ShapefileOptions.output(FormatOptions.of(Map.of("shapeType", "nonsense")));

        assertThatThrownBy(opts::shapeTypeOverride)
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("shapeType");
    }

    @Test
    void fieldNameStrategyDefaultsToStrict() throws ShapefileMappingException {
        assertThat(ShapefileOptions.output(FormatOptions.of(Map.of())).fieldNameStrategy())
                .isEqualTo(ShapefileOptions.FieldNameStrategy.STRICT);
    }

    @Test
    void fieldNameStrategyParsed() throws ShapefileMappingException {
        assertThat(ShapefileOptions.output(FormatOptions.of(Map.of("fieldNameStrategy", "stable")))
                        .fieldNameStrategy())
                .isEqualTo(ShapefileOptions.FieldNameStrategy.STABLE);
        assertThat(ShapefileOptions.output(FormatOptions.of(Map.of("fieldNameStrategy", "TRUNCATE")))
                        .fieldNameStrategy())
                .isEqualTo(ShapefileOptions.FieldNameStrategy.TRUNCATE);
    }

    @Test
    void fieldNameStrategyInvalidThrows() {
        ShapefileOptions opts = ShapefileOptions.output(FormatOptions.of(Map.of("fieldNameStrategy", "weird")));

        assertThatThrownBy(opts::fieldNameStrategy)
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("fieldNameStrategy");
    }

    @Test
    void failOnMultipleBasketsDefaultsTrue() {
        assertThat(ShapefileOptions.output(FormatOptions.of(Map.of())).failOnMultipleBaskets())
                .isTrue();
    }

    @Test
    void failOnMultipleBasketsConfigured() {
        assertThat(ShapefileOptions.output(FormatOptions.of(Map.of("failOnMultipleBaskets", "false")))
                        .failOnMultipleBaskets())
                .isFalse();
    }

    @Test
    void writeSidecarMappingDefaultsTrue() {
        assertThat(ShapefileOptions.output(FormatOptions.of(Map.of())).writeSidecarMapping())
                .isTrue();
    }

    @Test
    void prjReadsConfiguredValue() {
        ShapefileOptions opts = ShapefileOptions.output(FormatOptions.of(Map.of("prj", "PROJCS[...]")));

        assertThat(opts.prj()).hasValue("PROJCS[...]");
    }
}
