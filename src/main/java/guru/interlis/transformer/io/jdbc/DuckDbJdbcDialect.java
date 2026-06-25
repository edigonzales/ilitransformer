package guru.interlis.transformer.io.jdbc;

public final class DuckDbJdbcDialect implements JdbcDialect {

    @Override
    public String id() {
        return "duckdb";
    }
}
