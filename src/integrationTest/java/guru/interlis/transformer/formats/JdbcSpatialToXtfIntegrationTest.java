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
 * Phase 7 end-to-end guard: a JDBC query result with a WKT text column must be
 * transformed into a valid INTERLIS 2.4 XTF transfer with COORD geometry through
 * both the YAML and the {@code .ilimap} pipeline, including {@code --validate}.
 *
 * <p>The SQLite database is created programmatically before each test with WKT
 * point literals. No binary fixture is committed.
 */
class JdbcSpatialToXtfIntegrationTest {

    private static final Path EXAMPLE = Path.of("examples/jdbc-spatial-to-xtf");
    private static final String DEMO_NS = "http://www.interlis.ch/xtf/2.4/DemoJdbcSpatialTarget";

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
    void transformsJdbcWktPointToValidXtfUsingYamlMapping(@TempDir Path tempDir) throws Exception {
        runAndAssertValid(tempDir, "mapping.yaml");
    }

    @Test
    void transformsJdbcWktPointToValidXtfUsingIlimapMapping(@TempDir Path tempDir) throws Exception {
        runAndAssertValid(tempDir, "mapping.ilimap");
    }

    private void runAndAssertValid(Path tempDir, String mappingName) throws Exception {
        Path work = prepareExample(tempDir);
        Path mapping = work.resolve(mappingName);
        Path output = work.resolve("build/out/stations.xtf");
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
        assertThat(countObjects(output, "Station")).isEqualTo(2);
    }

    private Path prepareExample(Path tempDir) throws Exception {
        Path work = tempDir.resolve("jdbc-spatial-to-xtf");
        copyRecursively(EXAMPLE, work);
        createDatabase(work.resolve("build/demo.sqlite"));

        Path database = work.resolve("build/demo.sqlite").toAbsolutePath();
        Path output = work.resolve("build/out/stations.xtf").toAbsolutePath();
        Path models = work.resolve("models").toAbsolutePath();

        for (String name : List.of("mapping.yaml", "mapping.ilimap")) {
            Path m = work.resolve(name);
            String content = Files.readString(m)
                    .replace("\"jdbc:sqlite:build/demo.sqlite\"", quoted(name, "jdbc:sqlite:" + database))
                    .replace("\"build/out/stations.xtf\"", quoted(name, output.toString()))
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
                    "CREATE TABLE stations (" + " id TEXT PRIMARY KEY, identifier TEXT, name TEXT, geom_wkt TEXT)");
            statement.executeUpdate(
                    "INSERT INTO stations VALUES ('s1', 'SOLOTHURN', 'Solothurn', 'POINT (2607600 1228500)')");
            statement.executeUpdate("INSERT INTO stations VALUES ('s2', 'OLTEN', 'Olten', 'POINT (2635000 1242000)')");
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
