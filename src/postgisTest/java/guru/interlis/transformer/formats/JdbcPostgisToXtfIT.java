package guru.interlis.transformer.formats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import guru.interlis.transformer.app.CliMain;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import picocli.CommandLine;

/**
 * Opt-in real-database guard for the JDBC format provider. It transforms a flat PostgreSQL table into
 * a valid INTERLIS 2.4 XTF transfer against the vendored PostGIS stack ({@code dev/stack/compose.yml}).
 *
 * <p>Not part of {@code check}: run it with {@code ./gradlew postgisTest} after starting the database
 * via {@code docker compose -f dev/stack/compose.yml -p ilitransformer-pg up -d}. When the database is
 * unreachable, the tests skip themselves so they never break the build.
 */
class JdbcPostgisToXtfIT {

    private static final String URL = "jdbc:postgresql://127.0.0.1:54321/ilitransformer";
    private static final String USER = "postgres";
    private static final String PASSWORD = "secret";
    private static final String SCHEMA = "ilitransformer_phase6";
    private static final String MODELS = Path.of("examples/jdbc-to-xtf/models").toString();
    private static final String DEMO_NS = "http://www.interlis.ch/xtf/2.4/DemoTarget";

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void setUpStreams() {
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void transformsPostgresTableToValidXtf(@TempDir Path tempDir) throws Exception {
        assumeTrue(
                databaseReachable(), "PostgreSQL/PostGIS not reachable at " + URL + " (start dev/stack/compose.yml)");

        createFixture();
        try {
            Path output = tempDir.resolve("municipalities.xtf");
            Path report = tempDir.resolve("report");
            Path mapping = tempDir.resolve("mapping.yaml");
            Files.writeString(mapping, mappingYaml(output));

            int exitCode = new CommandLine(new CliMain())
                    .execute(
                            "transform",
                            "--mapping",
                            mapping.toString(),
                            "--modeldir",
                            Path.of(MODELS).toAbsolutePath().toString(),
                            "--validate",
                            "--report",
                            report.toString());

            assertThat(exitCode)
                    .as("stdout:%n%s%nstderr:%n%s", outContent, errContent)
                    .isZero();
            assertThat(Files.exists(output)).as("output XTF created").isTrue();
            assertThat(countObjects(output)).isEqualTo(2);
            assertThat(outContent.toString()).doesNotContain(PASSWORD);
        } finally {
            dropFixture();
        }
    }

    private String mappingYaml(Path output) {
        return """
                version: 1
                job:
                  name: jdbc-postgis-demo
                  inputs:
                    - id: db
                      format: jdbc
                      model: DemoJdbcSource
                      connection:
                        driver: org.postgresql.Driver
                        url: "%s"
                        user: %s
                        password: %s
                      queries:
                        - id: municipalities
                          topic: "DemoJdbcSource.Data"
                          class: "DemoJdbcSource.Data.Municipality"
                          basketId: b1
                          oidColumn: id
                          sql: "select id, bfsnr, name, population from %s.municipalities order by id"
                  outputs:
                    - id: out
                      path: "%s"
                      model: DemoTarget
                      format: xtf
                mapping:
                  oidStrategy:
                    default: deterministicUuid
                    namespace: jdbc-postgis-demo
                  basketStrategy:
                    default: byTopic
                  rules:
                    - id: municipality
                      target:
                        output: out
                        class: "DemoTarget.Catalog.Municipality"
                      sources:
                        - alias: src
                          input: db
                          class: "DemoJdbcSource.Data.Municipality"
                      identity:
                        sourceKey: ["src.bfsnr"]
                      assign:
                        BfsNr: "src.bfsnr"
                        Name: "src.name"
                        Population: "src.population"
                """.formatted(URL, USER, PASSWORD, SCHEMA, output.toAbsolutePath());
    }

    private boolean databaseReachable() {
        try (Connection ignored = DriverManager.getConnection(URL, USER, PASSWORD)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void createFixture() throws Exception {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            statement.executeUpdate("CREATE SCHEMA " + SCHEMA);
            statement.executeUpdate("CREATE TABLE " + SCHEMA
                    + ".municipalities (id integer primary key, bfsnr integer, name text, population integer)");
            statement.executeUpdate("INSERT INTO " + SCHEMA
                    + ".municipalities VALUES (1, 2601, 'Solothurn', 17000), (2, 2610, 'Olten', 19000)");
        }
    }

    private void dropFixture() throws Exception {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    private int countObjects(Path xtf) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Document document = factory.newDocumentBuilder().parse(xtf.toFile());
        return document.getElementsByTagNameNS(DEMO_NS, "Municipality").getLength();
    }
}
