package guru.interlis.transformer.formats;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.app.CliMain;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import picocli.CommandLine;

/**
 * Phase 6 end-to-end guard: the bundled {@code examples/jdbc-to-xtf} demo must transform a flat
 * SQLite table into a valid INTERLIS 2.4 XTF transfer through both the YAML and the {@code .ilimap}
 * pipeline, including {@code --validate}. SQLite keeps the test self-contained and deterministic; the
 * opt-in {@code postgisTest} task exercises the same provider against a real PostgreSQL/PostGIS
 * database.
 */
class JdbcToXtfIntegrationTest {

    private static final Path EXAMPLE = Path.of("examples/jdbc-to-xtf");
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
    void transformsSqliteTableToValidXtfUsingYamlMapping(@TempDir Path tempDir) throws Exception {
        runAndAssertValid(tempDir, "mapping.yaml");
    }

    @Test
    void transformsSqliteTableToValidXtfUsingIlimapMapping(@TempDir Path tempDir) throws Exception {
        runAndAssertValid(tempDir, "mapping.ilimap");
    }

    @Test
    void failsWithHelpfulDiagnosticWhenJdbcInputHasNoQueries(@TempDir Path tempDir) throws Exception {
        Path work = prepareExample(tempDir);
        Path models = work.resolve("models").toAbsolutePath();
        Path output = work.resolve("build/out/no-queries.xtf").toAbsolutePath();
        Path mapping = work.resolve("no-queries.yaml");
        Files.writeString(mapping, """
                version: 1
                job:
                  name: jdbc-no-queries
                  inputs:
                    - id: db
                      format: jdbc
                      model: DemoJdbcSource
                      connection:
                        url: "jdbc:sqlite:%s"
                  outputs:
                    - id: out
                      path: "%s"
                      model: DemoTarget
                      format: xtf
                mapping:
                  oidStrategy:
                    default: deterministicUuid
                    namespace: jdbc-no-queries
                  rules:
                    - id: r
                      target:
                        output: out
                        class: "DemoTarget.Catalog.Municipality"
                      sources:
                        - alias: src
                          input: db
                          class: "DemoJdbcSource.Data.Municipality"
                """.formatted(work.resolve("db/demo.sqlite").toAbsolutePath(), output));

        int exitCode = new CommandLine(new CliMain())
                .execute("transform", "--mapping", mapping.toString(), "--modeldir", models.toString());

        assertThat(exitCode).isNotZero();
        assertThat(outContent.toString()).contains("no queries");
        assertThat(Files.exists(output)).isFalse();
    }

    @Test
    void doesNotLeakPasswordInDiagnostics(@TempDir Path tempDir) throws Exception {
        Path work = prepareExample(tempDir);
        Path models = work.resolve("models").toAbsolutePath();
        Path output = work.resolve("build/out/leak.xtf").toAbsolutePath();
        Path report = work.resolve("build/leak-report");
        Path mapping = work.resolve("leak.yaml");
        Files.writeString(mapping, """
                version: 1
                job:
                  name: jdbc-leak
                  inputs:
                    - id: db
                      format: jdbc
                      model: DemoJdbcSource
                      connection:
                        url: "jdbc:nosuchdb://localhost/db"
                        password: "topsecret"
                      queries:
                        - id: municipalities
                          class: "DemoJdbcSource.Data.Municipality"
                          sql: "select 1"
                  outputs:
                    - id: out
                      path: "%s"
                      model: DemoTarget
                      format: xtf
                mapping:
                  oidStrategy:
                    default: deterministicUuid
                    namespace: jdbc-leak
                  rules:
                    - id: r
                      target:
                        output: out
                        class: "DemoTarget.Catalog.Municipality"
                      sources:
                        - alias: src
                          input: db
                          class: "DemoJdbcSource.Data.Municipality"
                """.formatted(output));

        int exitCode = new CommandLine(new CliMain())
                .execute(
                        "transform",
                        "--mapping",
                        mapping.toString(),
                        "--modeldir",
                        models.toString(),
                        "--report",
                        report.toString());

        assertThat(exitCode).isNotZero();
        assertThat(outContent.toString()).doesNotContain("topsecret");
        assertThat(errContent.toString()).doesNotContain("topsecret");
        if (Files.exists(report)) {
            try (Stream<Path> files = Files.walk(report)) {
                for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                    assertThat(Files.readString(file))
                            .as("report file %s must not contain the password", file)
                            .doesNotContain("topsecret");
                }
            }
        }
    }

    private void runAndAssertValid(Path tempDir, String mappingName) throws Exception {
        Path work = prepareExample(tempDir);
        Path mapping = work.resolve(mappingName);
        Path output = work.resolve("build/out/municipalities.xtf");
        Path report = work.resolve("build/report");

        int exitCode = new CommandLine(new CliMain())
                .execute(
                        "transform",
                        "--mapping",
                        mapping.toString(),
                        "--modeldir",
                        work.resolve("models").toString(),
                        "--validate",
                        "--report",
                        report.toString());

        assertThat(exitCode)
                .as("stdout:%n%s%nstderr:%n%s", outContent, errContent)
                .isZero();
        assertThat(Files.exists(output)).as("output XTF created").isTrue();
        assertThat(Files.exists(report.resolve("transformation-report.json")))
                .as("report written")
                .isTrue();
        assertThat(countObjects(output, "Municipality")).isEqualTo(2);
    }

    private Path prepareExample(Path tempDir) throws Exception {
        Path work = tempDir.resolve("jdbc-to-xtf");
        copyRecursively(EXAMPLE, work);
        createDatabase(work.resolve("db/demo.sqlite"));

        Path database = work.resolve("db/demo.sqlite").toAbsolutePath();
        Path output = work.resolve("build/out/municipalities.xtf").toAbsolutePath();
        Path models = work.resolve("models").toAbsolutePath();

        for (String name : List.of("mapping.yaml", "mapping.ilimap")) {
            Path m = work.resolve(name);
            String content = Files.readString(m)
                    .replace("\"jdbc:sqlite:build/demo.sqlite\"", quoted(name, "jdbc:sqlite:" + database))
                    .replace("\"build/out/municipalities.xtf\"", quoted(name, output.toString()))
                    .replace("\"models\"", quoted(name, models.toString()));
            Files.writeString(m, content);
        }
        return work;
    }

    private void createDatabase(Path databaseFile) throws Exception {
        Files.createDirectories(databaseFile.getParent());
        String url = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE municipalities (id INTEGER PRIMARY KEY, bfsnr INTEGER, name TEXT, population INTEGER)");
            statement.executeUpdate("INSERT INTO municipalities VALUES (1, 2601, 'Solothurn', 17000)");
            statement.executeUpdate("INSERT INTO municipalities VALUES (2, 2610, 'Olten', 19000)");
        }
    }

    private static String quoted(String mappingName, String value) {
        if (mappingName.endsWith(".ilimap")) {
            value = value.replace("\\", "\\\\");
        }
        return "\"" + value + "\"";
    }

    private void copyRecursively(Path source, Path target) throws Exception {
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                Path dest = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(path, dest);
                }
            }
        }
    }

    private int countObjects(Path xtf, String localName) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Document document = factory.newDocumentBuilder().parse(xtf.toFile());
        return document.getElementsByTagNameNS(DEMO_NS, localName).getLength();
    }
}
