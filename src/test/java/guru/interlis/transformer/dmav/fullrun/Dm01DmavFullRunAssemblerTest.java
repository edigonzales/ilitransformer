package guru.interlis.transformer.dmav.fullrun;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.dmav.Dm01DmavPaths;
import guru.interlis.transformer.mapping.model.JobConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Dm01DmavFullRunAssemblerTest {

    private static final Path REPOSITORY_ROOT = Path.of("").toAbsolutePath().normalize();

    private final Dm01DmavFullRunManifestLoader manifestLoader = new Dm01DmavFullRunManifestLoader();
    private final Dm01DmavFullRunAssembler assembler = new Dm01DmavFullRunAssembler();

    @TempDir
    Path tempDir;

    @Test
    void assemblesMixedYamlAndIlimapBundleWithScopedRuleAndEnumIds() throws Exception {
        Path manifestPath = Dm01DmavPaths.fullRunBundleDir("so-2549").resolve("manifest.yaml");
        Dm01DmavFullRunManifest manifest = manifestLoader.load(manifestPath, REPOSITORY_ROOT);

        Dm01DmavFullRunAssembler.AssembledFullRun assembled = assembler.assemble(
                manifest, manifestPath, REPOSITORY_ROOT, tempDir.resolve("source.itf"), tempDir.resolve("output.xtf"));

        JobConfig combined = assembled.combinedConfig();
        Map<String, JobConfig.RuleSpec> rulesById =
                combined.mapping.rules.stream().collect(Collectors.toMap(rule -> rule.id, Function.identity()));

        assertThat(assembled.loadedTopics())
                .extracting(Dm01DmavFullRunAssembler.LoadedTopic::topicId)
                .containsExactly(
                        "bb",
                        "dbv",
                        "eo",
                        "fpds2",
                        "gebaeudeadressen",
                        "gs",
                        "hfp3",
                        "lfp3",
                        "nomenklatur",
                        "rohrleitungen",
                        "toleranzstufen");
        assertThat(assembled.loadedTopics())
                .filteredOn(topic -> "gs".equals(topic.topicId()))
                .singleElement()
                .satisfies(topic -> assertThat(topic.format()).isEqualTo("ilimap"));
        assertThat(assembled.loadedTopics())
                .filteredOn(topic -> "lfp3".equals(topic.topicId()))
                .singleElement()
                .satisfies(topic -> assertThat(topic.format()).isEqualTo("ilimap"));
        assertThat(assembled.loadedTopics())
                .filteredOn(topic -> "eo".equals(topic.topicId()))
                .singleElement()
                .satisfies(topic -> assertThat(topic.format()).isEqualTo("yaml"));

        assertThat(combined.job.name).isEqualTo("dm01-to-dmav-so-2549-all");
        assertThat(combined.job.inputs).singleElement().satisfies(input -> {
            assertThat(input.id).isEqualTo("dm01");
            assertThat(input.path)
                    .isEqualTo(tempDir.resolve("source.itf")
                            .toAbsolutePath()
                            .normalize()
                            .toString());
        });
        assertThat(combined.job.outputs).singleElement().satisfies(output -> {
            assertThat(output.id).isEqualTo("dmav");
            assertThat(output.path)
                    .isEqualTo(tempDir.resolve("output.xtf")
                            .toAbsolutePath()
                            .normalize()
                            .toString());
        });
        assertThat(combined.job.modeldir)
                .contains(REPOSITORY_ROOT.resolve("src/test/data/av/models").toString())
                .contains("https://models.interlis.ch", "https://models.geo.admin.ch", "https://models.kgk-cgc.ch");

        assertThat(combined.mapping.enums).containsKey("Zuverlaessigkeit_DM01_DMAV");
        assertThat(rulesById)
                .containsKeys(
                        "lfp3-lfp3",
                        "lfp3-lfp3-nachfuehrung",
                        "eo-einzelobjekt-gueltig",
                        "gs-grenzpunkt-gueltig",
                        "gs-grenzpunkt-projektiert");
        assertThat(rulesById.get("lfp3-lfp3").getEffectiveRefs())
                .singleElement()
                .satisfies(ref -> assertThat(ref.targetObject.rule).isEqualTo("lfp3-lfp3-nachfuehrung"));
        assertThat(rulesById.get("gs-grenzpunkt-gueltig").getEffectiveRefs())
                .singleElement()
                .satisfies(ref -> assertThat(ref.targetObject.rule).isEqualTo("gs-gs-nachfuehrung-gueltig"));
        assertThat(rulesById.get("gs-grenzpunkt-projektiert").getEffectiveRefs())
                .singleElement()
                .satisfies(ref -> assertThat(ref.targetObject.rule).isEqualTo("gs-gs-nachfuehrung-projektiert"));
    }

    @Test
    void renamesConflictingEnumMapsAcrossYamlAndIlimapTopics() throws Exception {
        Path manifestDir = tempDir.resolve("conflict-bundle");
        Path expectedSummaryPath = manifestDir.resolve("expected-summary.yaml");
        Path yamlMappingPath = manifestDir.resolve("yaml-topic.yaml");
        Path ilimapMappingPath = manifestDir.resolve("ilimap-topic.ilimap");
        Path manifestPath = manifestDir.resolve("manifest.yaml");

        Files.createDirectories(manifestDir);
        Files.writeString(expectedSummaryPath, "datasetSlug: conflict\n", StandardCharsets.UTF_8);
        Files.writeString(yamlMappingPath, """
                version: 1
                job:
                  name: yaml-topic
                  direction: dm01-to-dmav
                  failPolicy: strict
                  modeldir:
                    - src/test/data/models
                  inputs:
                    - id: dm01
                      path: input.itf
                      model: Dm01TestModel
                      format: itf
                  outputs:
                    - id: dmav
                      path: output.xtf
                      model: DmavTestModel
                      format: xtf
                mapping:
                  oidStrategy:
                    default: deterministicUuid
                    namespace: yaml-topic
                  basketStrategy:
                    default: byTopic
                  compileMode: compatible
                  enums:
                    SharedEnum:
                      ja: Y
                      nein: N
                  rules:
                    - id: yaml-rule
                      target:
                        output: dmav
                        class: DmavTestModel.Topic.Target
                      sources:
                        - alias: s
                          input: dm01
                          class: Dm01TestModel.Topic.Source
                      assign:
                        Flag: "enumMap(s.Flag, 'SharedEnum')"
                """, StandardCharsets.UTF_8);
        Files.writeString(ilimapMappingPath, """
                mapping v2 "ilimap-topic" {
                  job {
                    description "conflict test";
                    direction dm01-to-dmav;
                    failPolicy strict;
                    compileMode compatible;
                  }

                  input dm01 {
                    path "input.itf";
                    model "Dm01TestModel";
                    format itf;
                  }

                  output dmav {
                    path "output.xtf";
                    model "DmavTestModel";
                    format xtf;
                  }

                  oid {
                    strategy deterministicUuid;
                    namespace "ilimap-topic";
                  }

                  basket byTopic;

                  enum SharedEnum {
                    "ja" => true;
                    "nein" => false;
                  }

                  rule ilimap-rule {
                    target dmav class "DmavTestModel.Topic.Target";
                    source s from dm01 class "Dm01TestModel.Topic.Source";

                    assign {
                      Flag = enumMap(s.Flag, SharedEnum);
                    }
                  }
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(manifestPath, """
                datasetSlug: conflict
                description: enum conflict bundle
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
                  oidNamespace: conflict-bundle
                  basketStrategy: byTopic
                  compileMode: compatible
                report:
                  expectedSummary: ./expected-summary.yaml
                modeldirs:
                  - src/test/data/models
                topics:
                  include:
                    - id: yamltopic
                      mapping: ./yaml-topic.yaml
                    - id: ilimaptopic
                      mapping: ./ilimap-topic.ilimap
                """, StandardCharsets.UTF_8);

        Dm01DmavFullRunManifest manifest = manifestLoader.load(manifestPath, REPOSITORY_ROOT);
        Dm01DmavFullRunAssembler.AssembledFullRun assembled = assembler.assemble(
                manifest, manifestPath, REPOSITORY_ROOT, tempDir.resolve("source.itf"), tempDir.resolve("output.xtf"));

        Map<String, JobConfig.RuleSpec> rulesById = assembled.combinedConfig().mapping.rules.stream()
                .collect(Collectors.toMap(rule -> rule.id, Function.identity()));

        assertThat(assembled.conflictingEnumNames()).containsExactly("SharedEnum");
        assertThat(assembled.combinedConfig().mapping.enums)
                .containsKeys("yamltopic_SharedEnum", "ilimaptopic_SharedEnum");
        assertThat(rulesById.get("yamltopic-yaml-rule").assign.get("Flag"))
                .isEqualTo("enumMap(s.Flag, 'yamltopic_SharedEnum')");
        assertThat(rulesById.get("ilimaptopic-ilimap-rule").assign.get("Flag")).contains("ilimaptopic_SharedEnum");
    }
}
