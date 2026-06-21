package guru.interlis.transformer.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundleManifestLoaderTest {

    private static final Path REPOSITORY_ROOT = Path.of("").toAbsolutePath().normalize();

    private final BundleManifestLoader loader = new BundleManifestLoader();

    @TempDir
    Path tempDir;

    private void writeModule(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, "version: 1\n", StandardCharsets.UTF_8);
    }

    private String manifestYaml(String topicsBlock, String expectedSummaryLine) {
        return """
                name: demo-bundle
                description: demo
                direction: dm01-to-dmav
                failPolicy: strict
                source:
                  pathHint: ./source/input.itf
                  model: DM01AVCH24LV95D
                  format: itf
                output:
                  model: DMAVTYM_Alles_V1_1
                  format: xtf
                  fileName: out.xtf
                mapping:
                  oidStrategy: deterministicUuid
                  oidNamespace: demo-bundle
                  basketStrategy: byTopic
                  compileMode: compatible
                %smodeldirs:
                  - src/test/data/models
                modules:
                %s
                """.formatted(expectedSummaryLine, topicsBlock);
    }

    @Test
    void loadsValidManifestWithoutExpectedSummary() throws Exception {
        writeModule(tempDir.resolve("a.yaml"));
        Path manifestPath = tempDir.resolve("manifest.yaml");
        Files.writeString(manifestPath, manifestYaml("  - id: a\n    mapping: ./a.yaml\n", ""), StandardCharsets.UTF_8);

        BundleManifest manifest = loader.load(manifestPath, REPOSITORY_ROOT);

        assertThat(manifest.name).isEqualTo("demo-bundle");
        assertThat(manifest.modules).singleElement().satisfies(module -> {
            assertThat(module.id).isEqualTo("a");
            assertThat(module.mapping).isEqualTo("./a.yaml");
        });
        assertThat(manifest.validate).isTrue();
    }

    @Test
    void rejectsModuleWithoutMapping() throws Exception {
        Path manifestPath = tempDir.resolve("manifest.yaml");
        Files.writeString(manifestPath, manifestYaml("  - id: a\n", ""), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> loader.load(manifestPath, REPOSITORY_ROOT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modules[].mapping");
    }

    @Test
    void rejectsDuplicateModuleIds() throws Exception {
        writeModule(tempDir.resolve("a.yaml"));
        Path manifestPath = tempDir.resolve("manifest.yaml");
        Files.writeString(
                manifestPath,
                manifestYaml("  - id: a\n    mapping: ./a.yaml\n  - id: a\n    mapping: ./a.yaml\n", ""),
                StandardCharsets.UTF_8);

        assertThatThrownBy(() -> loader.load(manifestPath, REPOSITORY_ROOT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate module id: a");
    }

    @Test
    void rejectsMissingExpectedSummaryFile() throws Exception {
        writeModule(tempDir.resolve("a.yaml"));
        Path manifestPath = tempDir.resolve("manifest.yaml");
        Files.writeString(
                manifestPath,
                manifestYaml("  - id: a\n    mapping: ./a.yaml\n", "expectedSummary: ./missing-summary.yaml\n"),
                StandardCharsets.UTF_8);

        assertThatThrownBy(() -> loader.load(manifestPath, REPOSITORY_ROOT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected summary file not found");
    }
}
