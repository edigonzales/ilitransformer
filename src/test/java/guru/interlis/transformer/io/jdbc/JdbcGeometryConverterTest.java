package guru.interlis.transformer.io.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import guru.interlis.transformer.mapping.model.JobConfig;

import ch.interlis.iom.IomObject;
import ch.interlis.iox.IoxException;

import org.junit.jupiter.api.Test;

class JdbcGeometryConverterTest {

    private final JdbcGeometryConverter converter = new JdbcGeometryConverter();

    @Test
    void convertsWktPointToCoord() throws IoxException {
        JobConfig.JdbcGeometrySpec spec = spec("geom", "geom_wkt", "wkt", "coord");

        IomObject result = converter.convertToGeometry("POINT (2607600 1228500)", spec);

        assertThat(result).isNotNull();
        assertThat(result.getobjecttag()).isEqualTo("COORD");
        assertThat(result.getattrvalue("C1")).isEqualTo("2607600.0");
        assertThat(result.getattrvalue("C2")).isEqualTo("1228500.0");
    }

    @Test
    void convertsWktPointToCoordWithoutExplicitType() throws IoxException {
        JobConfig.JdbcGeometrySpec spec = spec("geom", "geom_wkt", "wkt", null);

        IomObject result = converter.convertToGeometry("POINT (2635000.5 1242000.25)", spec);

        assertThat(result).isNotNull();
        assertThat(result.getobjecttag()).isEqualTo("COORD");
        assertThat(result.getattrvalue("C1")).isEqualTo("2635000.5");
        assertThat(result.getattrvalue("C2")).isEqualTo("1242000.25");
    }

    @Test
    void convertsWktPointWithScientificNotation() throws IoxException {
        JobConfig.JdbcGeometrySpec spec = spec("geom", "geom_wkt", "wkt", null);

        IomObject result = converter.convertToGeometry("POINT (2.6076E6 1.2285E6)", spec);

        assertThat(result).isNotNull();
        assertThat(result.getobjecttag()).isEqualTo("COORD");
        assertThat(result.getattrvalue("C1")).isEqualTo("2607600.0");
        assertThat(result.getattrvalue("C2")).isEqualTo("1228500.0");
    }

    @Test
    void returnsNullForNullRawValue() throws IoxException {
        JobConfig.JdbcGeometrySpec spec = spec("geom", "geom_wkt", "wkt", "coord");

        IomObject result = converter.convertToGeometry(null, spec);

        assertThat(result).isNull();
    }

    @Test
    void rejectsNonPointGeometryForCoordType() {
        JobConfig.JdbcGeometrySpec spec = spec("geom", "geom_wkt", "wkt", "coord");

        assertThatThrownBy(() -> converter.convertToGeometry("LINESTRING (0 0, 1 1)", spec))
                .isInstanceOf(JdbcMappingException.class)
                .hasMessageContaining("Expected POINT")
                .hasMessageContaining("coord");
    }

    @Test
    void rejectsInvalidWkt() {
        JobConfig.JdbcGeometrySpec spec = spec("geom", "geom_wkt", "wkt", "coord");

        assertThatThrownBy(() -> converter.convertToGeometry("NOT-A-WKT", spec))
                .isInstanceOf(JdbcMappingException.class)
                .hasMessageContaining("Cannot parse WKT");
    }

    @Test
    void rejectsUnsupportedEncoding() {
        JobConfig.JdbcGeometrySpec spec = spec("geom", "geom_col", "geojson", "coord");

        assertThatThrownBy(() -> converter.convertToGeometry("{}", spec))
                .isInstanceOf(JdbcMappingException.class)
                .hasMessageContaining("geojson");
    }

    @Test
    void wkbRequiresByteArray() {
        JobConfig.JdbcGeometrySpec spec = spec("geom", "geom_wkb", "wkb", "coord");

        assertThatThrownBy(() -> converter.convertToGeometry("not bytes", spec))
                .isInstanceOf(JdbcMappingException.class)
                .hasMessageContaining("byte[]");
    }

    @Test
    void encodingDefaultsToWkt() throws IoxException {
        JobConfig.JdbcGeometrySpec spec = new JobConfig.JdbcGeometrySpec();
        spec.attribute = "geom";
        spec.column = "geom_wkt";
        spec.type = "coord";

        IomObject result = converter.convertToGeometry("POINT (1 2)", spec);
        assertThat(result.getattrvalue("C1")).isEqualTo("1.0");
        assertThat(result.getattrvalue("C2")).isEqualTo("2.0");
    }

    @Test
    void convertsWktLineStringToPolyline() throws IoxException {
        JobConfig.JdbcGeometrySpec spec = spec("geom", "geom_wkt", "wkt", "polyline");

        IomObject result = converter.convertToGeometry("LINESTRING (2607600 1228500, 2635000 1242000)", spec);

        assertThat(result).isNotNull();
        assertThat(result.getobjecttag()).isEqualTo("POLYLINE");
        assertThat(result.getattrcount()).isEqualTo(1);
        IomObject sequence = result.getattrobj("sequence", 0);
        assertThat(sequence).isNotNull();
        assertThat(sequence.getobjecttag()).isEqualTo("SEGMENTS");
    }

    @Test
    void convertsWktLineStringToPolylineWithAutoInference() throws IoxException {
        JobConfig.JdbcGeometrySpec spec = spec("geom", "geom_wkt", "wkt", null);

        IomObject result = converter.convertToGeometry("LINESTRING (0 0, 1 1, 2 2)", spec);

        assertThat(result).isNotNull();
        assertThat(result.getobjecttag()).isEqualTo("POLYLINE");
    }

    @Test
    void convertsWktMultiLineStringToMultiPolyline() throws IoxException {
        JobConfig.JdbcGeometrySpec spec = spec("geom", "geom_wkt", "wkt", "polyline");

        IomObject result = converter.convertToGeometry("MULTILINESTRING ((0 0, 1 1), (2 2, 3 3))", spec);

        assertThat(result).isNotNull();
        assertThat(result.getobjecttag()).isEqualTo("MULTIPOLYLINE");
    }

    @Test
    void convertsWktPolygonToSurface() throws IoxException {
        JobConfig.JdbcGeometrySpec spec = spec("geom", "geom_wkt", "wkt", "surface");

        IomObject result = converter.convertToGeometry("POLYGON ((0 0, 0 10, 10 10, 10 0, 0 0))", spec);

        assertThat(result).isNotNull();
        assertThat(result.getobjecttag()).isEqualTo("MULTISURFACE");
    }

    @Test
    void convertsWktPolygonToSurfaceWithAutoInference() throws IoxException {
        JobConfig.JdbcGeometrySpec spec = spec("geom", "geom_wkt", "wkt", null);

        IomObject result = converter.convertToGeometry("POLYGON ((0 0, 0 5, 5 5, 5 0, 0 0))", spec);

        assertThat(result).isNotNull();
        assertThat(result.getobjecttag()).isEqualTo("MULTISURFACE");
    }

    @Test
    void convertsWktMultiPolygonToMultiSurface() throws IoxException {
        JobConfig.JdbcGeometrySpec spec = spec("geom", "geom_wkt", "wkt", "surface");

        IomObject result = converter.convertToGeometry(
                "MULTIPOLYGON (((0 0, 0 5, 5 5, 5 0, 0 0)), ((10 10, 10 15, 15 15, 15 10, 10 10)))", spec);

        assertThat(result).isNotNull();
        assertThat(result.getobjecttag()).isEqualTo("MULTISURFACE");
    }

    @Test
    void returnsNullForEmptyPolylineGeometry() throws IoxException {
        JobConfig.JdbcGeometrySpec spec = spec("geom", "geom_wkt", "wkt", "polyline");

        IomObject result = converter.convertToGeometry("LINESTRING EMPTY", spec);

        assertThat(result).isNull();
    }

    @Test
    void returnsNullForEmptySurfaceGeometry() throws IoxException {
        JobConfig.JdbcGeometrySpec spec = spec("geom", "geom_wkt", "wkt", "surface");

        IomObject result = converter.convertToGeometry("POLYGON EMPTY", spec);

        assertThat(result).isNull();
    }

    @Test
    void rejectsNonLineStringGeometryForPolylineType() {
        JobConfig.JdbcGeometrySpec spec = spec("geom", "geom_wkt", "wkt", "polyline");

        assertThatThrownBy(() -> converter.convertToGeometry("POINT (1 2)", spec))
                .isInstanceOf(JdbcMappingException.class)
                .hasMessageContaining("LINESTRING or MULTILINESTRING")
                .hasMessageContaining("polyline");
    }

    @Test
    void rejectsNonPolygonGeometryForSurfaceType() {
        JobConfig.JdbcGeometrySpec spec = spec("geom", "geom_wkt", "wkt", "surface");

        assertThatThrownBy(() -> converter.convertToGeometry("POINT (1 2)", spec))
                .isInstanceOf(JdbcMappingException.class)
                .hasMessageContaining("POLYGON or MULTIPOLYGON")
                .hasMessageContaining("surface");
    }

    @Test
    void rejectsUnexpectedExplicitType() {
        JobConfig.JdbcGeometrySpec spec = spec("geom", "geom_wkt", "wkt", "tin");

        assertThatThrownBy(() -> converter.convertToGeometry("POINT (1 2)", spec))
                .isInstanceOf(JdbcMappingException.class)
                .hasMessageContaining("coord, polyline, surface");
    }

    private static JobConfig.JdbcGeometrySpec spec(String attribute, String column, String encoding, String type) {
        JobConfig.JdbcGeometrySpec spec = new JobConfig.JdbcGeometrySpec();
        spec.attribute = attribute;
        spec.column = column;
        spec.encoding = encoding;
        spec.type = type;
        return spec;
    }
}
