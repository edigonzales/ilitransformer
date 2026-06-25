package guru.interlis.transformer.io.jdbc;

/**
 * Signals that a configured JDBC query could not be prepared or executed (syntax error, unknown
 * table/column, ...). The runner turns this into an {@code IO_JDBC_QUERY_FAILED} diagnostic. Messages
 * must never contain credentials.
 */
public final class JdbcQueryException extends RuntimeException {

    public JdbcQueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
