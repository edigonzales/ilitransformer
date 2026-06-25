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

    private static JobConfig.JdbcGeometrySpec spec(String attribute, String column, String encoding, String type) {
        JobConfig.JdbcGeometrySpec spec = new JobConfig.JdbcGeometrySpec();
        spec.attribute = attribute;
        spec.column = column;
        spec.encoding = encoding;
        spec.type = type;
        return spec;
    }
}
