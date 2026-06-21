package guru.interlis.transformer.bundle;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.mapping.model.JobConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundleAssemblerTest {

    private static final Path REPOSITORY_ROOT = Path.of("").toAbsolutePath().normalize();

    private final BundleManifestLoader manifestLoader = new BundleManifestLoader();
    private final BundleAssembler assembler = new BundleAssembler();

    @TempDir
    Path tempDir;

    @Test
    void mergesModulesAndRenamesConflictingEnumsAndRuleIds() throws Exception {
        Path manifestDir = tempDir.resolve("conflict-bundle");
        Path yamlMappingPath = manifestDir.resolve("yaml-module.yaml");
        Path ilimapMappingPath = manifestDir.resolve("ilimap-module.ilimap");
        Path manifestPath = manifestDir.resolve("manifest.yaml");

        Files.createDirectories(manifestDir);
        Files.writeString(yamlMappingPath, """
                version: 1
                job:
                  name: yaml-module
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
                    namespace: yaml-module
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
                mapping v2 "ilimap-module" {
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
                    namespace "ilimap-module";
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
                name: conflict-bundle
                description: enum conflict bundle
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
                  oidNamespace: conflict-bundle
                  basketStrategy: byTopic
                  compileMode: compatible
                modeldirs:
                  - src/test/data/models
                modules:
                  - id: yamlmodule
                    mapping: ./yaml-module.yaml
                  - id: ilimapmodule
                    mapping: ./ilimap-module.ilimap
                """, StandardCharsets.UTF_8);

        BundleManifest manifest = manifestLoader.load(manifestPath, REPOSITORY_ROOT);
        BundleAssembler.AssembledBundle assembled = assembler.assemble(
                manifest, manifestPath, REPOSITORY_ROOT, tempDir.resolve("source.itf"), tempDir.resolve("output.xtf"));

        JobConfig combined = assembled.combinedConfig();
        Map<String, JobConfig.RuleSpec> rulesById =
                combined.mapping.rules.stream().collect(Collectors.toMap(rule -> rule.id, Function.identity()));

        assertThat(combined.job.name).isEqualTo("conflict-bundle");
        assertThat(assembled.modules())
                .extracting(BundleAssembler.AssembledModule::moduleId)
                .containsExactly("yamlmodule", "ilimapmodule");
        assertThat(assembled.modules())
                .filteredOn(module -> "yamlmodule".equals(module.moduleId()))
                .singleElement()
                .satisfies(module -> assertThat(module.format()).isEqualTo("yaml"));
        assertThat(assembled.modules())
                .filteredOn(module -> "ilimapmodule".equals(module.moduleId()))
                .singleElement()
                .satisfies(module -> assertThat(module.format()).isEqualTo("ilimap"));

        assertThat(assembled.conflictingEnumNames()).containsExactly("SharedEnum");
        assertThat(combined.mapping.enums).containsKeys("yamlmodule_SharedEnum", "ilimapmodule_SharedEnum");
        assertThat(rulesById.get("yamlmodule-yaml-rule").assign.get("Flag"))
                .isEqualTo("enumMap(s.Flag, 'yamlmodule_SharedEnum')");
        assertThat(rulesById.get("ilimapmodule-ilimap-rule").assign.get("Flag")).contains("ilimapmodule_SharedEnum");
    }
}
