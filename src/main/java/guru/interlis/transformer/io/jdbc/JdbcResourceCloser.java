package guru.interlis.transformer.io.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/** Quietly closes JDBC resources in the correct order, swallowing close-time failures. */
final class JdbcResourceCloser {

    private JdbcResourceCloser() {}

    static void closeQuietly(ResultSet resultSet) {
        if (resultSet != null) {
            try {
                resultSet.close();
            } catch (Exception ignored) {
                // best effort
            }
        }
    }

    static void closeQuietly(Statement statement) {
        if (statement != null) {
            try {
                statement.close();
            } catch (Exception ignored) {
                // best effort
            }
        }
    }

    static void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {
                // best effort
            }
        }
    }
}
