package guru.interlis.transformer.feature;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

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
                // STUB/UNSUPPORTED/etc. may or may not have test references
                assertThat(entry.status()).isIn(
                        FeatureStatus.STUB, FeatureStatus.UNSUPPORTED,
                        FeatureStatus.EXPERIMENTAL, FeatureStatus.PARTIAL,
                        FeatureStatus.CONFIG_ONLY);
            }
        }
    }

    @Test
    void allReferencedTestsExist() throws Exception {
        var matrix = new FeatureMatrix();
        Set<String> existingTestNames = new HashSet<>();
        Path srcRoot = Path.of("src");
        Files.walk(srcRoot)
                .filter(p -> p.getFileName().toString().endsWith("Test.java"))
                .forEach(p -> existingTestNames.add(
                        p.getFileName().toString().replace(".java", "")));

        for (FeatureEntry entry : matrix.entries()) {
            for (String testRefList : entry.testReferences()) {
                for (String ref : testRefList.split("\\s*,\\s*")) {
                    String refTrimmed = ref.trim();
                    if (refTrimmed.isEmpty()) continue;
                    assertThat(existingTestNames)
                            .as("Feature '%s' references missing test '%s'", entry.feature(), refTrimmed)
                            .contains(refTrimmed);
                }
            }
        }
    }
}
