package guru.interlis.transformer.dmav.fullrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import guru.interlis.transformer.dmav.Dm01DmavPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Dm01DmavFullRunManifestLoaderTest {

    private static final Path REPOSITORY_ROOT = Path.of("").toAbsolutePath().normalize();

    private final Dm01DmavFullRunManifestLoader loader = new Dm01DmavFullRunManifestLoader();

    @TempDir
    Path tempDir;

    @Test
    void loadsCheckedInSo2549Manifest() throws Exception {
        Path manifestPath = Dm01DmavPaths.fullRunBundleDir("so-2549").resolve("manifest.yaml");

        Dm01DmavFullRunManifest manifest = loader.load(manifestPath, REPOSITORY_ROOT);

        assertThat(manifest.datasetSlug).isEqualTo("so-2549");
        assertThat(manifest.source.pathHint).isEqualTo("./source/2549.ch.so.agi.av.dm01_ch.itf");
        assertThat(manifest.report.expectedSummary).isEqualTo("./expected-summary.yaml");
        assertThat(manifest.topics.include).hasSize(11);
        assertThat(manifest.topics.include)
                .filteredOn(topic -> "lfp3".equals(topic.id))
                .singleElement()
                .satisfies(topic -> {
                    assertThat(topic.preferredIlimap).isEqualTo("profiles/dm01-to-dmav/1.1/lfp3.ilimap");
                    assertThat(topic.fallbackYaml).isNull();
                });
        assertThat(manifest.topics.include)
                .filteredOn(topic -> "gs".equals(topic.id))
                .singleElement()
                .satisfies(topic -> {
                    assertThat(topic.preferredIlimap).isEqualTo("profiles/dm01-to-dmav/1.1/gs.ilimap");
                    assertThat(topic.fallbackYaml).isNull();
                });
    }

    @Test
    void resolvesManifestRelativePathsEvenWhenTargetDoesNotExist() {
        Path manifestPath = tempDir.resolve("manifest.yaml");

        Path resolvedSource = loader.resolveManifestPath(manifestPath, REPOSITORY_ROOT, "./source/missing.itf");
        Path resolvedRepoFile =
                loader.resolveManifestPath(manifestPath, REPOSITORY_ROOT, "profiles/dm01-to-dmav/1.1/eo.yaml");

        assertThat(resolvedSource)
                .isEqualTo(
                        tempDir.resolve("source/missing.itf").toAbsolutePath().normalize());
        assertThat(resolvedRepoFile)
                .isEqualTo(REPOSITORY_ROOT
                        .resolve("profiles/dm01-to-dmav/1.1/eo.yaml")
                        .normalize());
    }

    @Test
    void rejectsDuplicateTopicIdsAcrossIncludeAndExclude() throws Exception {
        Path expectedSummary = tempDir.resolve("expected-summary.yaml");
        Files.writeString(expectedSummary, "datasetSlug: temp\n", StandardCharsets.UTF_8);

        Path manifestPath = tempDir.resolve("manifest.yaml");
        Files.writeString(manifestPath, """
                datasetSlug: temp
                description: temp manifest
                direction: dm01-to-dmav
                failPolicy: strict
                source:
                  pathHint: ./source/input.itf
                  sha256: deadbeef
                  model: DM01AVCH24LV95D
                  format: itf
                output:
                  outputId: dmav
                  model: DMAVTYM_Alles_V1_1
                  format: xtf
                  fileName: out.xtf
                mapping:
                  oidStrategy: deterministicUuid
                  oidNamespace: temp
                  basketStrategy: byTopic
                  compileMode: compatible
                report:
                  expectedSummary: ./expected-summary.yaml
                modeldirs:
                  - src/test/data/av/models
                topics:
                  include:
                    - id: eo
                      fallbackYaml: profiles/dm01-to-dmav/1.1/eo.yaml
                  exclude:
                    - id: eo
                      reason: duplicate on purpose
                """, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> loader.load(manifestPath, REPOSITORY_ROOT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Topic id appears in include and exclude: eo");
    }
}
