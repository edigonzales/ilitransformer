package guru.interlis.transformer.io.shp.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.io.shp.ShapefileMappingException;
import guru.interlis.transformer.io.shp.ShapefileOptions.FieldNameStrategy;
import guru.interlis.transformer.io.shp.core.DbfField;
import guru.interlis.transformer.io.shp.core.DbfFieldType;
import guru.interlis.transformer.io.shp.core.ShapeType;
import guru.interlis.transformer.io.shp.geom.GeometryKind;
import guru.interlis.transformer.io.shp.mapping.ShapefileSchemaBuilder.WriteSchema;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.TypeSystemFacade;

import ch.interlis.iom_j.Iom_jObject;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ShapefileSchemaBuilderTest {

    private static final String MODEL = "src/test/data/models/shp-writer-test.ili";
    private static final String MODELDIR = "src/test/data/models/";

    private static TypeSystemFacade ts;

    @BeforeAll
    static void compile() {
        IliModelCompileResult result = new IliModelService().compileModel(MODEL, MODELDIR);
        assertThat(result.transferDescription())
                .as("model must compile via ili2c")
                .isNotNull();
        ts = new TypeSystemFacade(result.transferDescription());
    }

    private ShapefileSchemaBuilder builder(DiagnosticCollector diag) {
        return new ShapefileSchemaBuilder(FieldNameStrategy.STRICT, diag, "out");
    }

    @Test
    void buildsPointSchemaFromModelAndSkipsStructure() throws Exception {
        DiagnosticCollector diag = new DiagnosticCollector();
        WriteSchema schema = builder(diag)
                .fromModel(ts, "ShpWriterTest.Data.Station", Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(schema.shapeType()).isEqualTo(ShapeType.POINT);
        assertThat(schema.geometryAttribute()).isEqualTo("Geometrie");
        assertThat(schema.iomAttributes()).containsExactly("Name", "BfsNr", "Aktiv");

        List<DbfField> fields = schema.dbfFields();
        assertThat(fields).extracting(DbfField::name).containsExactly("Name", "BfsNr", "Aktiv");
        assertThat(fields.get(0).type()).isEqualTo(DbfFieldType.CHARACTER);
        assertThat(fields.get(0).length()).isEqualTo(60);
        assertThat(fields.get(1).type()).isEqualTo(DbfFieldType.NUMERIC);
        assertThat(fields.get(2).type()).isEqualTo(DbfFieldType.LOGICAL);

        assertThat(diag.all())
                .anyMatch(d -> d.code().equals(DiagnosticCode.IO_SHP_ATTRIBUTE_SKIPPED)
                        && d.message().contains("Zusatz"));
    }

    @Test
    void mapsScalarDomainsToDbfFieldTypesAndWidths() throws Exception {
        WriteSchema schema = builder(new DiagnosticCollector())
                .fromModel(ts, "ShpWriterTest.Data.Typen", Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(schema.shapeType()).isEqualTo(ShapeType.POINT);

        List<DbfField> fields = schema.dbfFields();
        assertThat(fields).extracting(DbfField::name).containsExactly("Name", "Zahl", "Flag", "Status", "Datum");

        DbfField name = fields.get(0);
        assertThat(name.type()).isEqualTo(DbfFieldType.CHARACTER);
        assertThat(name.length()).isEqualTo(60);

        DbfField zahl = fields.get(1);
        assertThat(zahl.type()).isEqualTo(DbfFieldType.NUMERIC);
        assertThat(zahl.decimalCount()).isEqualTo(2);
        assertThat(zahl.length()).isEqualTo(6); // 3 integer digits + '.' + 2 decimals

        // INTERLIS BOOLEAN maps to DBF Logical, not a 254-char text field.
        DbfField flag = fields.get(2);
        assertThat(flag.type()).isEqualTo(DbfFieldType.LOGICAL);
        assertThat(flag.length()).isEqualTo(1);

        // Enumeration width is derived from the longest value ("stillgelegt" = 11), not a fixed default.
        DbfField status = fields.get(3);
        assertThat(status.type()).isEqualTo(DbfFieldType.CHARACTER);
        assertThat(status.length()).isEqualTo(11);

        // INTERLIS.XMLDate maps to a DBF Date field.
        DbfField datum = fields.get(4);
        assertThat(datum.type()).isEqualTo(DbfFieldType.DATE);
        assertThat(datum.length()).isEqualTo(8);
    }

    @Test
    void inferPolylineShapeType() throws Exception {
        WriteSchema schema = builder(new DiagnosticCollector())
                .fromModel(ts, "ShpWriterTest.Data.Strecke", Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(schema.shapeType()).isEqualTo(ShapeType.POLYLINE);
        assertThat(schema.geometryAttribute()).isEqualTo("Linie");
        assertThat(schema.iomAttributes()).containsExactly("Name");
    }

    @Test
    void inferPolygonShapeType() throws Exception {
        WriteSchema schema = builder(new DiagnosticCollector())
                .fromModel(ts, "ShpWriterTest.Data.Flaeche", Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(schema.shapeType()).isEqualTo(ShapeType.POLYGON);
        assertThat(schema.geometryAttribute()).isEqualTo("Perimeter");
    }

    @Test
    void inferMultiPointShapeType() throws Exception {
        WriteSchema schema = builder(new DiagnosticCollector())
                .fromModel(ts, "ShpWriterTest.Data.MehrPunkt", Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(schema.shapeType()).isEqualTo(ShapeType.MULTIPOINT);
        assertThat(schema.geometryAttribute()).isEqualTo("Lage");
        assertThat(schema.iomAttributes()).containsExactly("Name");
    }

    @Test
    void buildsNullShapeSchemaForModelClassWithoutGeometryWhenExplicitlyConfigured() throws Exception {
        WriteSchema schema = builder(new DiagnosticCollector())
                .fromModel(
                        ts,
                        "ShpWriterTest.Data.Tabelle",
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(ShapeType.NULL));

        assertThat(schema.shapeType()).isEqualTo(ShapeType.NULL);
        assertThat(schema.geometryAttribute()).isNull();
        assertThat(schema.iomAttributes()).containsExactly("Name", "Zahl", "Flag");
        assertThat(schema.dbfFields())
                .extracting(DbfField::type)
                .containsExactly(DbfFieldType.CHARACTER, DbfFieldType.NUMERIC, DbfFieldType.LOGICAL);
    }

    @Test
    void rejectsNullShapeForModelClassWithGeometry() {
        assertThatThrownBy(() -> builder(new DiagnosticCollector())
                        .fromModel(
                                ts,
                                "ShpWriterTest.Data.Station",
                                Optional.empty(),
                                Optional.empty(),
                                Optional.of(ShapeType.NULL)))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("shapeType=null")
                .hasMessageContaining("Geometrie");
    }

    @Test
    void rejectsGeometryAttributeWithNullShape() {
        assertThatThrownBy(() -> builder(new DiagnosticCollector())
                        .fromModel(
                                ts,
                                "ShpWriterTest.Data.Tabelle",
                                Optional.of("Geometrie"),
                                Optional.empty(),
                                Optional.of(ShapeType.NULL)))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("geometryAttribute")
                .hasMessageContaining("shapeType=null");
    }

    @Test
    void ambiguousGeometryAttributesRejected() {
        assertThatThrownBy(() -> builder(new DiagnosticCollector())
                        .fromModel(
                                ts, "ShpWriterTest.Data.TwoGeom", Optional.empty(), Optional.empty(), Optional.empty()))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("geometry attributes");
    }

    @Test
    void configuredGeometryAttributeResolvesAmbiguity() throws Exception {
        DiagnosticCollector diag = new DiagnosticCollector();
        WriteSchema schema = builder(diag)
                .fromModel(ts, "ShpWriterTest.Data.TwoGeom", Optional.of("P1"), Optional.empty(), Optional.empty());

        assertThat(schema.shapeType()).isEqualTo(ShapeType.POINT);
        assertThat(schema.geometryAttribute()).isEqualTo("P1");
        // P2 is an additional geometry attribute and is skipped (not written to the DBF).
        assertThat(schema.iomAttributes()).isEmpty();
        assertThat(diag.all())
                .anyMatch(d -> d.code().equals(DiagnosticCode.IO_SHP_ATTRIBUTE_SKIPPED)
                        && d.message().contains("P2"));
    }

    @Test
    void fallbackFromFirstObjectRequiresShapeType() {
        Iom_jObject obj = new Iom_jObject("Some.Unknown.Cls", "o1");
        assertThatThrownBy(() -> builder(new DiagnosticCollector())
                        .fromFirstObject(obj, Optional.of("geom"), Optional.empty(), Optional.empty()))
                .isInstanceOf(ShapefileMappingException.class)
                .hasMessageContaining("shapeType");
    }

    @Test
    void fallbackFromFirstObjectBuildsCharacterFields() throws Exception {
        Iom_jObject obj = new Iom_jObject("Some.Unknown.Cls", "o1");
        obj.setattrvalue("code", "42");
        obj.setattrvalue("label", "Hello");
        obj.addattrobj("geom", new Iom_jObject("COORD", null));

        WriteSchema schema = builder(new DiagnosticCollector())
                .fromFirstObject(obj, Optional.of("geom"), Optional.of(GeometryKind.COORD), Optional.empty());

        assertThat(schema.shapeType()).isEqualTo(ShapeType.POINT);
        assertThat(schema.iomAttributes()).containsExactlyInAnyOrder("code", "label");
        assertThat(schema.dbfFields()).allMatch(f -> f.type() == DbfFieldType.CHARACTER);
    }

    @Test
    void fallbackFromFirstObjectBuildsNullShapeSchemaWhenExplicitlyConfigured() throws Exception {
        Iom_jObject obj = new Iom_jObject("Some.Unknown.Cls", "o1");
        obj.setattrvalue("code", "42");
        obj.setattrvalue("label", "Hello");

        WriteSchema schema = builder(new DiagnosticCollector())
                .fromFirstObject(obj, Optional.empty(), Optional.empty(), Optional.of(ShapeType.NULL));

        assertThat(schema.shapeType()).isEqualTo(ShapeType.NULL);
        assertThat(schema.geometryAttribute()).isNull();
        assertThat(schema.iomAttributes()).containsExactlyInAnyOrder("code", "label");
        assertThat(schema.dbfFields()).allMatch(f -> f.type() == DbfFieldType.CHARACTER);
    }
}
