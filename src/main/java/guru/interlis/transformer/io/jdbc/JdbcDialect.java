package guru.interlis.transformer.io.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

public interface JdbcDialect {

    String id();

    default void configureConnection(Connection connection) throws SQLException {}

    default boolean supportsEncoding(String encoding) {
        if (encoding == null) return false;
        return switch (encoding.toLowerCase().trim()) {
            case "wkt", "wkb" -> true;
            default -> false;
        };
    }
}
