package guru.interlis.transformer.io.jdbc;

public final class PostgisJdbcDialect implements JdbcDialect {

    @Override
    public String id() {
        return "postgis";
    }

    @Override
    public boolean supportsEncoding(String encoding) {
        if (encoding == null) return false;
        return switch (encoding.toLowerCase().trim()) {
            case "wkt", "wkb", "ewkb" -> true;
            default -> false;
        };
    }
}
