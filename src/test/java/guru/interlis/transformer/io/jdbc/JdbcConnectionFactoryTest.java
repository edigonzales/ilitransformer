package guru.interlis.transformer.io.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import guru.interlis.transformer.mapping.model.JobConfig;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcConnectionFactoryTest {

    @Test
    void resolvesUserAndPasswordFromEnv() {
        JdbcConnectionFactory factory = new JdbcConnectionFactory(Map.of("PGUSER", "alice", "PGPW", "s3cret")::get);

        JobConfig.JdbcConnectionSpec spec = new JobConfig.JdbcConnectionSpec();
        spec.userEnv = "PGUSER";
        spec.passwordEnv = "PGPW";

        Properties properties = factory.buildProperties(spec);

        assertThat(properties.getProperty("user")).isEqualTo("alice");
        assertThat(properties.getProperty("password")).isEqualTo("s3cret");
    }

    @Test
    void inlineCredentialsWinOverEnv() {
        JdbcConnectionFactory factory = new JdbcConnectionFactory(Map.of("PGUSER", "alice")::get);

        JobConfig.JdbcConnectionSpec spec = new JobConfig.JdbcConnectionSpec();
        spec.user = "bob";
        spec.userEnv = "PGUSER";

        Properties properties = factory.buildProperties(spec);

        assertThat(properties.getProperty("user")).isEqualTo("bob");
    }

    @Test
    void copiesExtraProperties() {
        JdbcConnectionFactory factory = new JdbcConnectionFactory(name -> null);

        JobConfig.JdbcConnectionSpec spec = new JobConfig.JdbcConnectionSpec();
        spec.properties.put("ApplicationName", "ilitransformer");

        Properties properties = factory.buildProperties(spec);

        assertThat(properties.getProperty("ApplicationName")).isEqualTo("ilitransformer");
    }

    @Test
    void opensSqliteFileConnection(@TempDir Path tempDir) throws Exception {
        JobConfig.JdbcConnectionSpec spec = new JobConfig.JdbcConnectionSpec();
        spec.url = "jdbc:sqlite:" + tempDir.resolve("test.sqlite");

        JdbcConnectionFactory factory = new JdbcConnectionFactory();
        try (Connection connection = factory.open(spec, null)) {
            assertThat(connection.isClosed()).isFalse();
        }
    }

    @Test
    void throwsMappingExceptionForMissingUrl() {
        JobConfig.JdbcConnectionSpec spec = new JobConfig.JdbcConnectionSpec();

        assertThatThrownBy(() -> new JdbcConnectionFactory().open(spec, null))
                .isInstanceOf(JdbcMappingException.class)
                .hasMessageContaining("connection url");
    }

    @Test
    void doesNotLeakPasswordInConnectionException() {
        JobConfig.JdbcConnectionSpec spec = new JobConfig.JdbcConnectionSpec();
        spec.driver = "no.such.Driver";
        spec.url = "jdbc:nosuch://localhost/db";
        spec.password = "topsecret";

        assertThatThrownBy(() -> new JdbcConnectionFactory().open(spec, null))
                .isInstanceOf(JdbcConnectionException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("topsecret"));
    }
}
