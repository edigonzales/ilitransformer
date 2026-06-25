package guru.interlis.transformer.io.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JdbcDialectTest {

    @Test
    void defaultDialectSupportsWkt() {
        JdbcDialect dialect = new DefaultJdbcDialect();
        assertThat(dialect.supportsEncoding("wkt")).isTrue();
        assertThat(dialect.supportsEncoding("WKT")).isTrue();
    }

    @Test
    void defaultDialectSupportsWkb() {
        JdbcDialect dialect = new DefaultJdbcDialect();
        assertThat(dialect.supportsEncoding("wkb")).isTrue();
        assertThat(dialect.supportsEncoding("WKB")).isTrue();
    }

    @Test
    void defaultDialectRejectsUnsupportedEncoding() {
        JdbcDialect dialect = new DefaultJdbcDialect();
        assertThat(dialect.supportsEncoding("geojson")).isFalse();
        assertThat(dialect.supportsEncoding("ewkb")).isFalse();
    }

    @Test
    void defaultDialectRejectsNull() {
        JdbcDialect dialect = new DefaultJdbcDialect();
        assertThat(dialect.supportsEncoding(null)).isFalse();
    }

    @Test
    void postgisDialectSupportsEwkb() {
        JdbcDialect dialect = new PostgisJdbcDialect();
        assertThat(dialect.supportsEncoding("wkt")).isTrue();
        assertThat(dialect.supportsEncoding("wkb")).isTrue();
        assertThat(dialect.supportsEncoding("ewkb")).isTrue();
    }

    @Test
    void postgisDialectRejectsUnsupported() {
        JdbcDialect dialect = new PostgisJdbcDialect();
        assertThat(dialect.supportsEncoding("geojson")).isFalse();
    }

    @Test
    void duckdbDialectHasDefaultEncodingSupport() {
        JdbcDialect dialect = new DuckDbJdbcDialect();
        assertThat(dialect.supportsEncoding("wkt")).isTrue();
        assertThat(dialect.supportsEncoding("wkb")).isTrue();
        assertThat(dialect.supportsEncoding("ewkb")).isFalse();
    }

    @Test
    void eachDialectReturnsItsId() {
        assertThat(new DefaultJdbcDialect().id()).isEqualTo("default");
        assertThat(new PostgisJdbcDialect().id()).isEqualTo("postgis");
        assertThat(new DuckDbJdbcDialect().id()).isEqualTo("duckdb");
    }
}
