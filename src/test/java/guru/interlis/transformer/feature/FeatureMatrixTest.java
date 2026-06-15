package guru.interlis.transformer.feature;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

class FeatureMatrixTest {

    @Test
    void everySupportedEntryHasAtLeastOneTestReference() {
        var matrix = new FeatureMatrix();
        for (FeatureEntry entry : matrix.entries()) {
            if (entry.status() == FeatureStatus.SUPPORTED) {
                assertThat(entry.testReferences())
                        .as("SUPPORTED feature '" + entry.feature() + "' must have test references")
                        .isNotEmpty();
            }
        }
    }

    @Test
    void noDuplicateFeatureNames() {
        var matrix = new FeatureMatrix();
        var names = new HashSet<String>();
        for (FeatureEntry entry : matrix.entries()) {
            assertThat(names.add(entry.feature()))
                    .as("Duplicate feature name: " + entry.feature())
                    .isTrue();
        }
    }

    @Test
    void allStatusValuesAreUsed() {
        var matrix = new FeatureMatrix();
        var statuses = new HashSet<FeatureStatus>();
        for (FeatureEntry entry : matrix.entries()) {
            statuses.add(entry.status());
        }
        assertThat(statuses).contains(FeatureStatus.SUPPORTED);
    }

    @Test
    void writeMarkdownProducesValidOutput(@TempDir Path tempDir) throws Exception {
        var matrix = new FeatureMatrix();
        Path out = tempDir.resolve("features.md");
        matrix.writeMarkdown(out);

        String content = Files.readString(out);
        assertThat(content).isNotEmpty();
        assertThat(content).contains("# Feature Matrix");
        assertThat(content).contains("| Feature | Phase | Status | Description | Tests |");
        for (FeatureEntry entry : matrix.entries()) {
            assertThat(content).contains(entry.feature());
        }
    }

    @Test
    void writeJsonProducesValidOutput(@TempDir Path tempDir) throws Exception {
        var matrix = new FeatureMatrix();
        Path out = tempDir.resolve("features.json");
        matrix.writeJson(out);

        String content = Files.readString(out);
        assertThat(content).isNotEmpty();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var tree = mapper.readTree(out.toFile());
        assertThat(tree).isNotNull();
        assertThat(tree.isArray()).isTrue();
        assertThat(tree.size()).isEqualTo(matrix.entries().size());
    }

    @Test
    void entriesReturnUnmodifiableList() {
        var matrix = new FeatureMatrix();
        var entries = matrix.entries();
        assertThatThrownBy(() -> entries.add(
                FeatureEntry.of("Test", "0", FeatureStatus.SUPPORTED, "desc", "TestClass")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void stubsAndUnsupportedMayHaveEmptyTestReferences() {
        var matrix = new FeatureMatrix();
        for (FeatureEntry entry : matrix.entries()) {
            if (entry.status() != FeatureStatus.SUPPORTED) {
                assertThat(entry.status()).isIn(
                        FeatureStatus.STUB, FeatureStatus.UNSUPPORTED,
                        FeatureStatus.EXPERIMENTAL, FeatureStatus.PARTIAL,
                        FeatureStatus.CONFIG_ONLY);
            }
        }
    }

    @Test
    void featureMatrixYamlIsLoadableAndMatchesMatrix() throws Exception {
        var matrix = new FeatureMatrix();
        List<FeatureEntry> matrixEntries = matrix.entries();

        InputStream in = getClass().getResourceAsStream("/feature-matrix.yaml");
        assertThat(in).as("feature-matrix.yaml must be on classpath").isNotNull();

        var mapper = new com.fasterxml.jackson.databind.ObjectMapper(
                new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
        FeatureMatrixYaml yaml = mapper.readValue(in, FeatureMatrixYaml.class);

        assertThat(yaml.entries)
                .as("YAML entry count must match matrix entry count")
                .hasSize(matrixEntries.size());

        Set<String> matrixNames = matrixEntries.stream()
                .map(FeatureEntry::feature)
                .collect(Collectors.toSet());
        Set<String> yamlNames = yaml.entries.stream()
                .map(e -> e.feature)
                .collect(Collectors.toSet());

        assertThat(yamlNames)
                .as("YAML features must match matrix features")
                .isEqualTo(matrixNames);

        for (FeatureEntryYaml ye : yaml.entries) {
            FeatureEntry me = matrixEntries.stream()
                    .filter(e -> e.feature().equals(ye.feature))
                    .findFirst().orElseThrow();
            assertThat(ye.status).isEqualTo(me.status());
            assertThat(ye.phase).isEqualTo(me.phase());
            assertThat(ye.description).isEqualTo(me.description());
            assertThat(ye.toFeatureEntry().testReferences())
                    .containsExactlyInAnyOrderElementsOf(me.testReferences());
        }
    }

    private record TestClassInfo(Set<String> fullQualifiedNames, boolean foundOnClasspath) {}

    @Test
    void allReferencedTestsExist() throws Exception {
        var matrix = new FeatureMatrix();
        Map<String, TestClassInfo> existingBySimpleName = collectExistingTestClasses();

        for (FeatureEntry entry : matrix.entries()) {
            for (String testRef : entry.testReferences()) {
                for (String ref : testRef.split("\\s*,\\s*")) {
                    String refTrimmed = ref.trim();
                    if (refTrimmed.isEmpty()) continue;
                    assertThat(existingBySimpleName)
                            .as("Feature '%s' references missing test '%s'", entry.feature(), refTrimmed)
                            .containsKey(refTrimmed);
                }
            }
        }
    }

    @Test
    void allReferencedTestClassesAreLoadable() throws Exception {
        var matrix = new FeatureMatrix();
        Map<String, TestClassInfo> existingBySimpleName = collectExistingTestClasses();

        for (FeatureEntry entry : matrix.entries()) {
            for (String testRef : entry.testReferences()) {
                for (String ref : testRef.split("\\s*,\\s*")) {
                    String refTrimmed = ref.trim();
                    if (refTrimmed.isEmpty()) continue;
                    TestClassInfo info = existingBySimpleName.get(refTrimmed);
                    if (info == null) continue;
                    if (!info.foundOnClasspath) continue;

                    boolean atLeastOneLoaded = false;
                    for (String fqn : info.fullQualifiedNames) {
                        try {
                            Class.forName(fqn);
                            atLeastOneLoaded = true;
                            break;
                        } catch (ClassNotFoundException ignored) {
                        }
                    }
                    assertThat(atLeastOneLoaded)
                            .as("Feature '%s' references test '%s' but no matching class is loadable",
                                    entry.feature(), refTrimmed)
                            .isTrue();
                }
            }
        }
    }

    private static Map<String, TestClassInfo> collectExistingTestClasses() throws Exception {
        Map<String, TestClassInfo> bySimpleName = new HashMap<>();

        String classpath = System.getProperty("java.class.path");
        if (classpath != null) {
            for (String entry : classpath.split(File.pathSeparator)) {
                Path path = Path.of(entry);
                if (Files.isDirectory(path)) {
                    Files.walk(path)
                            .filter(p -> p.getFileName().toString().endsWith("Test.class"))
                            .filter(p -> !p.getFileName().toString().contains("$"))
                            .forEach(p -> {
                                String simpleName = p.getFileName().toString()
                                        .replace(".class", "");
                                String fqn = classNameFromPath(path, p);
                                bySimpleName.computeIfAbsent(simpleName,
                                        k -> new TestClassInfo(new HashSet<>(), true))
                                        .fullQualifiedNames.add(fqn);
                            });
                }
            }
        }

        Path projectRoot = detectProjectRoot();
        if (projectRoot != null) {
            for (String testSourceDir : List.of(
                    "src/test/java", "src/integrationTest/java", "src/realDataTest/java")) {
                Path dir = projectRoot.resolve(testSourceDir);
                if (Files.isDirectory(dir)) {
                    Files.walk(dir)
                            .filter(p -> p.getFileName().toString().endsWith("Test.java"))
                            .forEach(p -> {
                                String simpleName = p.getFileName().toString()
                                        .replace(".java", "");
                                String fqn = classNameFromSourcePath(dir, p);
                                bySimpleName.computeIfAbsent(simpleName,
                                        k -> new TestClassInfo(new HashSet<>(), false))
                                        .fullQualifiedNames.add(fqn);
                            });
                }
            }
        }

        return bySimpleName;
    }

    private static String classNameFromPath(Path root, Path classFile) {
        String relative = root.relativize(classFile).toString();
        String noExtension = relative.replace(".class", "");
        return noExtension.replace(File.separatorChar, '.');
    }

    private static String classNameFromSourcePath(Path sourceRoot, Path javaFile) {
        String relative = sourceRoot.relativize(javaFile).toString();
        String noExtension = relative.replace(".java", "");
        return noExtension.replace(File.separatorChar, '.');
    }

    private static Path detectProjectRoot() {
        try {
            var codeSource = FeatureMatrixTest.class.getProtectionDomain().getCodeSource();
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
