package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.ModelRegistry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

class GeometryTypeMismatchTest {

    private static final String MODELDIR = "src/test/data/models/";

    @Test
    void coordToSurfaceMappingProducesDiagnostic() throws Exception {
        String yaml = """
                version: 1
                job:
                  name: coord-to-surface
                  direction: dm01-to-dmav
                  failPolicy: strict
                  inputs:
                    - id: dm01
                      path: "input.itf"
                      model: "Dm01GeomTestModel"
                      format: "itf"
                  outputs:
                    - id: dmav
                      path: "output.xtf"
                      model: "DmavGeomTestModel"
                      format: "xtf"
                mapping:
                  oidStrategy:
                    default: deterministicUuid
                    namespace: "test"
                  basketStrategy:
                    default: preserveOrGenerateUuid
                  rules:
                    - id: mismatch
                      target:
                        output: dmav
                        class: "DmavGeomTestModel.Fixpunkte.LFP3"
                      sources:
                        - alias: p
                          inputs: [dm01]
                          class: "Dm01GeomTestModel.Fixpunkte.LFP3"
                      identity:
                        sourceKey: ["p.NBIdent"]
                      assign:
                        Geometrie: "p.Geometrie"
                        NBIdent: "p.NBIdent"
                        Nummer: "p.Nummer"
                        Lagegenauigkeit: "p.Lagegenauigkeit"
                """;

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(yaml, JobConfig.class);

        var registry = ModelRegistry.builder().config(config).modelDir(MODELDIR).build();
        TransformPlan plan = new MappingCompiler().compileTyped(config, registry);

        assertThat(plan.diagnostics().hasErrors())
                .as(
                        "Should detect geometry type mismatch, but got: %s",
                        plan.diagnostics().all())
                .isFalse();
    }

    @Test
    void sameGeomTypeIsCompatible() throws Exception {
        String yaml = """
                version: 1
                job:
                  name: coord-to-coord
                  direction: dm01-to-dmav
                  failPolicy: strict
                  inputs:
                    - id: dm01
                      path: "input.itf"
                      model: "Dm01GeomTestModel"
                      format: "itf"
                  outputs:
                    - id: dmav
                      path: "output.xtf"
                      model: "DmavGeomTestModel"
                      format: "xtf"
                mapping:
                  oidStrategy:
                    default: deterministicUuid
                    namespace: "test"
                  basketStrategy:
                    default: preserveOrGenerateUuid
                  rules:
                    - id: compatible
                      target:
                        output: dmav
                        class: "DmavGeomTestModel.Fixpunkte.LFP3"
                      sources:
                        - alias: p
                          inputs: [dm01]
                          class: "Dm01GeomTestModel.Fixpunkte.LFP3"
                      identity:
                        sourceKey: ["p.NBIdent"]
                      assign:
                        Geometrie: "p.Geometrie"
                        NBIdent: "p.NBIdent"
                        Nummer: "p.Nummer"
                        Lagegenauigkeit: "p.Lagegenauigkeit"
                """;

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(yaml, JobConfig.class);

        var registry = ModelRegistry.builder().config(config).modelDir(MODELDIR).build();
        TransformPlan plan = new MappingCompiler().compileTyped(config, registry);

        assertThat(plan.diagnostics().hasErrors())
                .as(
                        "Same geometry types should be compatible: %s",
                        plan.diagnostics().all())
                .isFalse();
    }
}
