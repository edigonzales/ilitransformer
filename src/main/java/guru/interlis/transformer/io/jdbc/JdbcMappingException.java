package guru.interlis.transformer.io.jdbc;

/**
 * Signals that a JDBC input is configured incorrectly (no queries, missing query class/sql, missing
 * connection url, unsupported column type). The runner turns this into an {@code IO_JDBC_MAPPING_INVALID}
 * diagnostic. Messages must never contain credentials.
 */
public final class JdbcMappingException extends RuntimeException {

    public JdbcMappingException(String message) {
        super(message);
    }
}
