package guru.interlis.transformer.io.jdbc;

import guru.interlis.transformer.mapping.model.JobConfig;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Function;

/**
 * Opens JDBC {@link Connection}s from a {@link JobConfig.JdbcConnectionSpec}.
 *
 * <p>Credentials may be inline ({@code user}/{@code password}) or read from the environment
 * ({@code userEnv}/{@code passwordEnv}). The resolved password is only ever passed to the driver via
 * connection {@link Properties}; it is never placed into exception messages, logs or diagnostics.
 */
public final class JdbcConnectionFactory {

    private static final String SQLITE_PREFIX = "jdbc:sqlite:";

    private final Function<String, String> env;

    public JdbcConnectionFactory() {
        this(System::getenv);
    }

    /** Test seam: inject the environment lookup used to resolve {@code userEnv}/{@code passwordEnv}. */
    public JdbcConnectionFactory(Function<String, String> env) {
        this.env = env;
    }

    public Connection open(JobConfig.JdbcConnectionSpec spec, Path baseDirectory) {
        if (spec == null || spec.url == null || spec.url.isBlank()) {
            throw new JdbcMappingException(
                    "JDBC input has no connection url. Add connection.url, e.g. 'jdbc:sqlite:data.sqlite'.");
        }
        String url = resolveRelativeSqliteUrl(spec.url, baseDirectory);

        if (spec.driver != null && !spec.driver.isBlank()) {
            try {
                Class.forName(spec.driver);
            } catch (ClassNotFoundException e) {
                throw new JdbcConnectionException(
                        "JDBC driver class not found: '" + spec.driver
                                + "'. Add the driver to the classpath or remove 'connection.driver'.",
                        e);
            }
        }

        Properties properties = buildProperties(spec);
        try {
            return DriverManager.getConnection(url, properties);
        } catch (SQLException e) {
            throw new JdbcConnectionException("Could not open JDBC connection to '" + url + "': " + e.getMessage(), e);
        }
    }

    /** Package-visible test seam: resolves credentials and extra properties (without a password log). */
    Properties buildProperties(JobConfig.JdbcConnectionSpec spec) {
        Properties properties = new Properties();
        if (spec.properties != null) {
            spec.properties.forEach((k, v) -> {
                if (k != null && v != null) {
                    properties.setProperty(k, v);
                }
            });
        }
        String user = resolveSecret(spec.user, spec.userEnv);
        if (user != null) {
            properties.setProperty("user", user);
        }
        String password = resolveSecret(spec.password, spec.passwordEnv);
        if (password != null) {
            properties.setProperty("password", password);
        }
        return properties;
    }

    private String resolveSecret(String direct, String envName) {
        if (direct != null && !direct.isEmpty()) {
            return direct;
        }
        if (envName != null && !envName.isBlank()) {
            String value = env.apply(envName);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    /**
     * For {@code jdbc:sqlite:<file>} urls with a relative file part, resolve the file against the
     * mapping base directory so examples are self-contained regardless of the process working
     * directory. In-memory urls ({@code jdbc:sqlite::memory:}) and absolute paths are left untouched.
     */
    private static String resolveRelativeSqliteUrl(String url, Path baseDirectory) {
        if (baseDirectory == null) {
            return url;
        }
        if (!url.regionMatches(true, 0, SQLITE_PREFIX, 0, SQLITE_PREFIX.length())) {
            return url;
        }
        String filePart = url.substring(SQLITE_PREFIX.length());
        if (filePart.isEmpty()
                || filePart.startsWith(":")
                || filePart.toLowerCase(Locale.ROOT).contains(":memory:")) {
            return url;
        }
        Path file = Path.of(filePart);
        if (file.isAbsolute()) {
            return url;
        }
        return SQLITE_PREFIX + baseDirectory.resolve(file).normalize();
    }
}
