package guru.interlis.transformer.docs;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.feature.FeatureEntry;
import guru.interlis.transformer.feature.FeatureMatrix;
import guru.interlis.transformer.mapping.ilimap.IlimapLoader;
import guru.interlis.transformer.mapping.ilimap.parser.IlimapParser;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSemanticValidator;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class DocumentationExampleTest {

    private static final Path EXAMPLES_DIR = Path.of("examples/ilimap");

    @Test
    void allIlimapExamplesParse() throws Exception {
        List<Path> ilimapFiles = findIlimapFiles();
        assertThat(ilimapFiles).as("at least one .ilimap example must exist").isNotEmpty();

        for (Path file : ilimapFiles) {
            String source = Files.readString(file);
            assertThatCode(() -> new IlimapParser(source).parseDocument())
                    .as("parsing %s", file)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void allIlimapExamplesValidateSemantically() throws Exception {
        List<Path> ilimapFiles = findIlimapFiles();
        assertThat(ilimapFiles).as("at least one .ilimap example must exist").isNotEmpty();

        var validator = new IlimapSemanticValidator();
        for (Path file : ilimapFiles) {
            String source = Files.readString(file);
            var doc = new IlimapParser(source).parseDocument();
            var result = validator.validate(doc);
            assertThat(result.hasErrors())
                    .as("semantic validation of %s must have no errors, but got: %s", file, result.diagnostics())
                    .isFalse();
        }
    }

    @Test
    void minimalExampleLoadsToJobConfig() {
        Path profile = EXAMPLES_DIR.resolve("minimal/profile.ilimap");
        assertThat(profile).exists();

        var loader = new IlimapLoader();
        var config = loader.load(profile);

        assertThat(config).isNotNull();
        assertThat(config.job.name).isEqualTo("minimal-example");
        assertThat(config.job.inputs).hasSize(1);
        assertThat(config.job.inputs.get(0).id).isEqualTo("src-input");
        assertThat(config.job.outputs).hasSize(1);
        assertThat(config.job.outputs.get(0).id).isEqualTo("tgt-output");
        assertThat(config.mapping.rules).hasSize(1);
        assertThat(config.mapping.rules.get(0).id).isEqualTo("copy-rule");
        assertThat(config.mapping.rules.get(0).assign).containsKeys("Label", "Size", "Enabled");
    }

    @Test
    void featureMatrixReferencesExistingTests() throws Exception {
        var matrix = new FeatureMatrix();
        Set<String> existingTestNames = collectTestSimpleNames();
        assertThat(existingTestNames).as("must find at least some test classes").isNotEmpty();

        for (FeatureEntry entry : matrix.entries()) {
            for (String ref : entry.testReferences()) {
                String trimmed = ref.trim();
                if (trimmed.isEmpty()) continue;
                assertThat(existingTestNames)
                        .as("Feature '%s' references test '%s' which does not exist", entry.feature(), trimmed)
                        .contains(trimmed);
            }
        }
    }

    private static List<Path> findIlimapFiles() throws Exception {
        assertThat(EXAMPLES_DIR).as("examples/ilimap directory must exist").isDirectory();
        try (Stream<Path> walk = Files.walk(EXAMPLES_DIR)) {
            return walk.filter(p -> p.toString().endsWith(".ilimap")).sorted().toList();
        }
    }

    private static Set<String> collectTestSimpleNames() throws Exception {
        Set<String> names = new HashSet<>();
        Path projectRoot = detectProjectRoot();
        if (projectRoot == null) {
            projectRoot = Path.of(".");
        }
        for (String dir : List.of("src/test/java", "src/integrationTest/java", "src/realDataTest/java")) {
            Path testDir = projectRoot.resolve(dir);
            if (!Files.isDirectory(testDir)) continue;
            try (Stream<Path> walk = Files.walk(testDir)) {
                walk.filter(p -> p.getFileName().toString().endsWith("Test.java"))
                        .filter(p -> !p.getFileName().toString().contains("$"))
                        .forEach(p -> names.add(p.getFileName().toString().replace(".java", "")));
            }
        }
        String classpath = System.getProperty("java.class.path");
        if (classpath != null) {
            for (String entry : classpath.split(File.pathSeparator)) {
                Path path = Path.of(entry);
                if (Files.isDirectory(path)) {
                    try (Stream<Path> walk = Files.walk(path)) {
                        walk.filter(p -> p.getFileName().toString().endsWith("Test.class"))
                                .filter(p -> !p.getFileName().toString().contains("$"))
                                .forEach(p ->
                                        names.add(p.getFileName().toString().replace(".class", "")));
                    }
                }
            }
        }
        return names;
    }

    private static Path detectProjectRoot() {
        try {
            var codeSource =
                    DocumentationExampleTest.class.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                Path current = Path.of(codeSource.getLocation().toURI());
                while (current != null) {
                    if (Files.exists(current.resolve("build.gradle"))
                            || Files.exists(current.resolve("settings.gradle"))) {
                        return current;
                    }
                    current = current.getParent();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
