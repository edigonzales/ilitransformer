package guru.interlis.transformer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.TypeSystemFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class GeometryTypeMismatchTest {

    private static final String MODELDIR = "src/test/data/models/";
    private static TypeSystemFacade dm01Ts;
    private static TypeSystemFacade dmavGeomTs;

    @BeforeAll
    static void compileModels() {
        IliModelService service = new IliModelService();

        IliModelCompileResult dm01Result = service.compileModel(
                "src/test/data/models/dm01-geom-test.ili", MODELDIR);
        if (dm01Result.hasErrors()) {
            fail("DM01 model compilation errors:\n  " + formatDiagnostics(dm01Result));
        }
        dm01Ts = new TypeSystemFacade(dm01Result.transferDescription());

        IliModelCompileResult dmavResult = service.compileModel(
                "src/test/data/models/dmav-geom-test.ili", MODELDIR);
        if (dmavResult.hasErrors()) {
            fail("DMAV model compilation errors:\n  " + formatDiagnostics(dmavResult));
        }
        dmavGeomTs = new TypeSystemFacade(dmavResult.transferDescription());
    }

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
                          input: dm01
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

        TransformPlan plan = new MappingCompiler().compileTyped(
                config,
                Map.of("Dm01GeomTestModel", dm01Ts),
                Map.of("DmavGeomTestModel", dmavTs()));

        assertThat(plan.diagnostics().hasErrors())
                .as("Should detect geometry type mismatch, but got: %s", plan.diagnostics().all())
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
                          input: dm01
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

        TransformPlan plan = new MappingCompiler().compileTyped(
                config,
                Map.of("Dm01GeomTestModel", dm01Ts),
                Map.of("DmavGeomTestModel", dmavTs()));

        assertThat(plan.diagnostics().hasErrors())
                .as("Same geometry types should be compatible: %s", plan.diagnostics().all())
                .isFalse();
    }

    private TypeSystemFacade dmavTs() {
        return dmavGeomTs;
    }

    private static String formatDiagnostics(IliModelCompileResult result) {
        return result.diagnostics().all().stream()
                .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                .reduce((left, right) -> left + "\n  " + right)
                .orElse("<none>");
    }
}
