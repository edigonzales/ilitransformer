package guru.interlis.transformer.formats;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.app.CliMain;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Phase 3 end-to-end guard: the bundled {@code examples/csv-to-xtf} demo must transform a flat CSV
 * file into a valid INTERLIS 2.4 XTF transfer through both the YAML and the {@code .ilimap} pipeline,
 * including {@code --validate} (which runs ilivalidator on the committed output).
 */
class CsvToXtfIntegrationTest {

    private static final Path EXAMPLE = Path.of("examples/csv-to-xtf");
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
    void transformsCsvToValidXtfUsingYamlMapping(@TempDir Path tempDir) throws Exception {
        runAndAssertValid(tempDir, "mapping.yaml");
    }

    @Test
    void transformsCsvToValidXtfUsingIlimapMapping(@TempDir Path tempDir) throws Exception {
        runAndAssertValid(tempDir, "mapping.ilimap");
    }

    @Test
    void failsWithHelpfulDiagnosticForInvalidSeparatorOption(@TempDir Path tempDir) throws Exception {
        Path work = prepareExample(tempDir);
        Path mapping = work.resolve("mapping.yaml");
        Files.writeString(mapping, Files.readString(mapping).replace("separator: \";\"", "separator: \"::\""));
        Path output = work.resolve("build/out/municipalities.xtf");

        int exitCode = new CommandLine(new CliMain())
                .execute(
                        "transform",
                        "--mapping",
                        mapping.toString(),
                        "--modeldir",
                        work.resolve("models").toString());

        assertThat(exitCode).isNotZero();
        assertThat(outContent.toString()).contains("separator");
        assertThat(Files.exists(output)).isFalse();
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
        Path work = tempDir.resolve("csv-to-xtf");
        copyRecursively(EXAMPLE, work);

        Path input = work.resolve("input/municipalities.csv").toAbsolutePath();
        Path output = work.resolve("build/out/municipalities.xtf").toAbsolutePath();
        Path models = work.resolve("models").toAbsolutePath();

        for (String name : List.of("mapping.yaml", "mapping.ilimap")) {
            Path m = work.resolve(name);
            String content = Files.readString(m)
                    .replace("\"input/municipalities.csv\"", quoted(name, input))
                    .replace("\"build/out/municipalities.xtf\"", quoted(name, output))
                    .replace("\"models\"", quoted(name, models));
            Files.writeString(m, content);
        }
        return work;
    }

    private static String quoted(String mappingName, Path path) {
        String text = path.toString();
        if (mappingName.endsWith(".ilimap")) {
            text = text.replace("\\", "\\\\");
        }
        return "\"" + text + "\"";
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
