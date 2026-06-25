package guru.interlis.transformer.formats;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.app.CliMain;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;

class ExamplesContractTest {

    private static final Path EXAMPLES_ROOT = Path.of("examples");

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
    void allDocumentedExampleDirectoriesExist() {
        for (String dir : exampleDirectories()) {
            Path path = EXAMPLES_ROOT.resolve(dir);
            assertThat(Files.isDirectory(path))
                    .as("example directory '%s' must exist", dir)
                    .isTrue();
        }
    }

    @Test
    void allExamplesHaveReadme() {
        for (String dir : exampleDirectories()) {
            Path readme = EXAMPLES_ROOT.resolve(dir).resolve("README.md");
            assertThat(Files.exists(readme))
                    .as("example '%s' must have README.md", dir)
                    .isTrue();
        }
    }

    @Test
    void allExamplesHaveModelsDirectory() {
        for (String dir : exampleDirectories()) {
            Path models = EXAMPLES_ROOT.resolve(dir).resolve("models");
            assertThat(Files.isDirectory(models))
                    .as("example '%s' must have models/ directory", dir)
                    .isTrue();
        }
    }

    @ParameterizedTest
    @MethodSource("exampleMappings")
    void yamlMappingValidatesSuccessfully(String exampleDir, String mappingName) {
        Path mapping = EXAMPLES_ROOT.resolve(exampleDir).resolve(mappingName);
        assertThat(Files.exists(mapping))
                .as("mapping file '%s/%s' must exist", exampleDir, mappingName)
                .isTrue();

        Path models = EXAMPLES_ROOT.resolve(exampleDir).resolve("models");
        int exitCode = new CommandLine(new CliMain())
                .execute(
                        "validate-mapping",
                        "--mapping",
                        mapping.toAbsolutePath().toString(),
                        "--modeldir",
                        models.toAbsolutePath().toString());

        String output = outContent.toString();
        assertThat(exitCode)
                .as(
                        "validate-mapping '%s/%s' failed with exit code %d. stdout:%n%s%nstderr:%n%s",
                        exampleDir, mappingName, exitCode, output, errContent)
                .isZero();
        assertThat(output).contains("valid");
    }

    @Test
    void docsFormatsMdExists() {
        assertThat(Files.exists(Path.of("docs/formats.md")))
                .as("docs/formats.md must exist")
                .isTrue();
    }

    @Test
    void docsFormatsMdContainsFormatMatrix() {
        Path path = Path.of("docs/formats.md");
        assertThat(Files.exists(path)).isTrue();

        String content = readFile(path);
        assertThat(content).contains("Format-Matrix");
        assertThat(content).contains("XTF | ja | ja | ja | ja | ja");
    }

    @Test
    void docsFormatsMdDocumentsShapefileAsNotYetImplemented() {
        Path path = Path.of("docs/formats.md");
        String content = readFile(path);
        assertThat(content).contains("nicht implementiert");
    }

    @Test
    void readmeDocumentsAllFormatExamples() {
        Path path = Path.of("README.md");
        String content = readFile(path);
        assertThat(content).contains("examples/csv-to-xtf");
        assertThat(content).contains("examples/gpkg-to-xtf");
        assertThat(content).contains("examples/gpkg-spatial-to-xtf");
        assertThat(content).contains("examples/jdbc-to-xtf");
        assertThat(content).contains("examples/jdbc-spatial-to-xtf");
    }

    @Test
    void readmeLinksToFormatsDoc() {
        Path path = Path.of("README.md");
        String content = readFile(path);
        assertThat(content).contains("docs/formats.md");
    }

    @Test
    void readmeNotesShapefileAsNotYetImplemented() {
        Path path = Path.of("README.md");
        String content = readFile(path);
        assertThat(content).contains("noch nicht implementiert");
    }

    @Test
    void cliDocReferencesSpatialExamples() {
        Path path = Path.of("docs/cli.md");
        String content = readFile(path);
        assertThat(content).contains("gpkg-spatial-to-xtf");
        assertThat(content).contains("jdbc-spatial-to-xtf");
    }

    @Test
    void cliDocReferencesFormatsMd() {
        Path path = Path.of("docs/cli.md");
        String content = readFile(path);
        assertThat(content).contains("formats.md");
    }

    @Test
    void mappingDslDocContainsFormatMatrixWithJdbcGeometry() {
        Path path = Path.of("docs/mapping-dsl.md");
        String content = readFile(path);
        assertThat(content).contains("WKT/WKB POINT");
        assertThat(content).contains("Noch nicht implementiert");
    }

    private static List<String> exampleDirectories() {
        return List.of(
                "minimal",
                "ilimap/minimal",
                "csv-to-xtf",
                "gpkg-to-xtf",
                "gpkg-spatial-to-xtf",
                "jdbc-to-xtf",
                "jdbc-spatial-to-xtf");
    }

    private static List<Arguments> exampleMappings() {
        return List.of(
                Arguments.of("minimal", "mapping.yaml"),
                Arguments.of("ilimap/minimal", "profile.ilimap"),
                Arguments.of("csv-to-xtf", "mapping.yaml"),
                Arguments.of("csv-to-xtf", "mapping.ilimap"),
                Arguments.of("gpkg-to-xtf", "mapping.yaml"),
                Arguments.of("gpkg-to-xtf", "mapping.ilimap"),
                Arguments.of("gpkg-spatial-to-xtf", "mapping.yaml"),
                Arguments.of("gpkg-spatial-to-xtf", "mapping.ilimap"),
                Arguments.of("jdbc-to-xtf", "mapping.yaml"),
                Arguments.of("jdbc-to-xtf", "mapping.ilimap"),
                Arguments.of("jdbc-spatial-to-xtf", "mapping.yaml"),
                Arguments.of("jdbc-spatial-to-xtf", "mapping.ilimap"));
    }

    private static String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read " + path, e);
        }
    }
}
