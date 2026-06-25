package guru.interlis.transformer.io.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.interlis.iom_j.Iom_jObject;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;

class JdbcValueMapperTest {

    private final JdbcValueMapper mapper = new JdbcValueMapper();

    @Test
    void keepsStringValue() {
        assertThat(mapper.toIoxScalar("Solothurn")).isEqualTo("Solothurn");
    }

    @Test
    void mapsNullToNull() {
        assertThat(mapper.toIoxScalar(null)).isNull();
    }

    @Test
    void mapsIntegralNumbersToPlainDecimal() {
        assertThat(mapper.toIoxScalar(17000)).isEqualTo("17000");
        assertThat(mapper.toIoxScalar(17000L)).isEqualTo("17000");
        assertThat(mapper.toIoxScalar(new BigInteger("123456789012345"))).isEqualTo("123456789012345");
    }

    @Test
    void mapsBigDecimalWithoutScientificNotation() {
        assertThat(mapper.toIoxScalar(new BigDecimal("1.50"))).isEqualTo("1.50");
        assertThat(mapper.toIoxScalar(new BigDecimal("1E+3"))).isEqualTo("1000");
    }

    @Test
    void mapsBooleanToLowercaseLiteral() {
        assertThat(mapper.toIoxScalar(Boolean.TRUE)).isEqualTo("true");
        assertThat(mapper.toIoxScalar(Boolean.FALSE)).isEqualTo("false");
    }

    @Test
    void mapsSqlDateToIsoDate() {
        assertThat(mapper.toIoxScalar(java.sql.Date.valueOf("2024-01-02"))).isEqualTo("2024-01-02");
    }

    @Test
    void rejectsBinaryByDefault() {
        assertThatThrownBy(() -> mapper.toIoxScalar(new byte[] {1, 2, 3}))
                .isInstanceOf(JdbcMappingException.class)
                .hasMessageContaining("blobEncoding");
    }

    @Test
    void encodesBinaryAsBase64WhenEnabled() {
        JdbcValueMapper base64Mapper = new JdbcValueMapper(true);
        assertThat(base64Mapper.toIoxScalar(new byte[] {0, 1, 2, 3})).isEqualTo("AAECAw==");
    }

    @Test
    void applyScalarValueSkipsNull() {
        Iom_jObject object = new Iom_jObject("Demo.Topic.Class", "oid1");
        mapper.applyScalarValue(object, "name", null);
        assertThat(object.getattrvalue("name")).isNull();
    }

    @Test
    void applyScalarValueSetsAttribute() {
        Iom_jObject object = new Iom_jObject("Demo.Topic.Class", "oid1");
        mapper.applyScalarValue(object, "population", 17000);
        assertThat(object.getattrvalue("population")).isEqualTo("17000");
    }
}
