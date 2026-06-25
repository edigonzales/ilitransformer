package guru.interlis.transformer.io.jdbc;

public final class DefaultJdbcDialect implements JdbcDialect {

    @Override
    public String id() {
        return "default";
    }
}
