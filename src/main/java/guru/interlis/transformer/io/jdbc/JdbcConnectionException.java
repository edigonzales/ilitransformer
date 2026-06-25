package guru.interlis.transformer.io.jdbc;

/**
 * Signals that a JDBC connection or driver could not be established. The runner turns this into an
 * {@code IO_JDBC_CONNECTION_FAILED} diagnostic. Messages must never contain credentials; only the
 * (user-authored) connection url is echoed, never the resolved password.
 */
public final class JdbcConnectionException extends RuntimeException {

    public JdbcConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
