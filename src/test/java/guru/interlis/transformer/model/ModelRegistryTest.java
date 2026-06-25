package guru.interlis.transformer.model;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.InputBinding;
import guru.interlis.transformer.mapping.plan.OutputBinding;

import org.junit.jupiter.api.Test;

class ModelRegistryTest {

    private static JobConfig twoInputsConfig() {
        JobConfig config = new JobConfig();
        config.version = 1;
        config.job.name = "test-job";
        config.job.direction = "forward";

        JobConfig.InputSpec in1 = new JobConfig.InputSpec();
        in1.id = "dm01";
        in1.model = "DM01AVCH24LV95";
        in1.path = "input.xtf";
        config.job.inputs.add(in1);

        JobConfig.InputSpec in2 = new JobConfig.InputSpec();
        in2.id = "gwr";
        in2.model = "DM01AVCH24LV95";
        in2.path = "gwr.xtf";
        config.job.inputs.add(in2);

        JobConfig.OutputSpec out = new JobConfig.OutputSpec();
        out.id = "dmav";
        out.model = "DMAV_FixpunkteAVKategorie3_V1_1";
        out.path = "output.xtf";
        config.job.outputs.add(out);

        return config;
    }

    @Test
    void registryRequiresInputById() {
        // Build registry with config but without compiling models (requires real ILI files)
        // Instead use buildWithSuppliedTypeSystems with mock TypeSystemFacades
        JobConfig config = twoInputsConfig();
        TypeSystemFacade mockTs = new TypeSystemFacade(null);

        ModelRegistry registry = ModelRegistry.builder()
                .config(config)
                .buildWithSuppliedTypeSystems(
                        java.util.Map.of("DM01AVCH24LV95", mockTs),
                        java.util.Map.of("DMAV_FixpunkteAVKategorie3_V1_1", mockTs));

        InputBinding dm01 = registry.requireInput("dm01");
        assertThat(dm01.inputId()).isEqualTo("dm01");
        assertThat(dm01.declaredModelName()).isEqualTo("DM01AVCH24LV95");

        InputBinding gwr = registry.requireInput("gwr");
        assertThat(gwr.inputId()).isEqualTo("gwr");
        assertThat(gwr.declaredModelName()).isEqualTo("DM01AVCH24LV95");

        OutputBinding dmav = registry.requireOutput("dmav");
        assertThat(dmav.outputId()).isEqualTo("dmav");
        assertThat(dmav.declaredModelName()).isEqualTo("DMAV_FixpunkteAVKategorie3_V1_1");
    }

    @Test
    void registryThrowsForUnknownInputId() {
        JobConfig config = twoInputsConfig();
        ModelRegistry registry = ModelRegistry.builder()
                .config(config)
                .buildWithSuppliedTypeSystems(java.util.Map.of(), java.util.Map.of());

        assertThatThrownBy(() -> registry.requireInput("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown input ID");
    }

    @Test
    void registryThrowsForUnknownOutputId() {
        JobConfig config = twoInputsConfig();
        ModelRegistry registry = ModelRegistry.builder()
                .config(config)
                .buildWithSuppliedTypeSystems(java.util.Map.of(), java.util.Map.of());

        assertThatThrownBy(() -> registry.requireOutput("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown output ID");
    }

    @Test
    void outputIdDifferentFromModelName() {
        // Output ID "dmav" != model name "DMAV_FixpunkteAVKategorie3_V1_1"
        JobConfig config = twoInputsConfig();
        TypeSystemFacade mockTs = new TypeSystemFacade(null);

        ModelRegistry registry = ModelRegistry.builder()
                .config(config)
                .buildWithSuppliedTypeSystems(
                        java.util.Map.of(), java.util.Map.of("DMAV_FixpunkteAVKategorie3_V1_1", mockTs));

        OutputBinding dmav = registry.requireOutput("dmav");
        assertThat(dmav.outputId()).isEqualTo("dmav");
        assertThat(dmav.declaredModelName()).isEqualTo("DMAV_FixpunkteAVKategorie3_V1_1");
        assertThat(dmav.outputId()).isNotEqualTo(dmav.declaredModelName());
    }

    @Test
    void inputIdDifferentFromModelName() {
        JobConfig config = new JobConfig();
        config.version = 1;
        JobConfig.InputSpec in = new JobConfig.InputSpec();
        in.id = "src1";
        in.model = "SomeVeryLongModelName_V2_3";
        config.job.inputs.add(in);

        TypeSystemFacade mockTs = new TypeSystemFacade(null);
        ModelRegistry registry = ModelRegistry.builder()
                .config(config)
                .buildWithSuppliedTypeSystems(
                        java.util.Map.of("SomeVeryLongModelName_V2_3", mockTs), java.util.Map.of());

        InputBinding binding = registry.requireInput("src1");
        assertThat(binding.inputId()).isEqualTo("src1");
        assertThat(binding.declaredModelName()).isEqualTo("SomeVeryLongModelName_V2_3");
        assertThat(binding.inputId()).isNotEqualTo(binding.declaredModelName());
    }

    @Test
    void multipleInputsSameModel() {
        JobConfig config = twoInputsConfig();
        TypeSystemFacade mockTs = new TypeSystemFacade(null);

        ModelRegistry registry = ModelRegistry.builder()
                .config(config)
                .buildWithSuppliedTypeSystems(java.util.Map.of("DM01AVCH24LV95", mockTs), java.util.Map.of());

        InputBinding dm01 = registry.requireInput("dm01");
        InputBinding gwr = registry.requireInput("gwr");
        assertThat(dm01.declaredModelName()).isEqualTo(gwr.declaredModelName());
        assertThat(dm01.inputId()).isNotEqualTo(gwr.inputId());
    }

    @Test
    void multipleOutputsDifferentModels() {
        JobConfig config = new JobConfig();
        config.version = 1;
        JobConfig.OutputSpec out1 = new JobConfig.OutputSpec();
        out1.id = "targetA";
        out1.model = "ModelAlpha";
        config.job.outputs.add(out1);

        JobConfig.OutputSpec out2 = new JobConfig.OutputSpec();
        out2.id = "targetB";
        out2.model = "ModelBeta";
        config.job.outputs.add(out2);

        TypeSystemFacade mockTs = new TypeSystemFacade(null);
        ModelRegistry registry = ModelRegistry.builder()
                .config(config)
                .buildWithSuppliedTypeSystems(
                        java.util.Map.of(), java.util.Map.of("ModelAlpha", mockTs, "ModelBeta", mockTs));

        OutputBinding a = registry.requireOutput("targetA");
        OutputBinding b = registry.requireOutput("targetB");
        assertThat(a.declaredModelName()).isEqualTo("ModelAlpha");
        assertThat(b.declaredModelName()).isEqualTo("ModelBeta");
        assertThat(a.declaredModelName()).isNotEqualTo(b.declaredModelName());
    }

    @Test
    void requireSourceTypeSystemReturnsTypeSystemFacade() {
        JobConfig config = twoInputsConfig();
        TypeSystemFacade mockTs = new TypeSystemFacade(null);
        ModelRegistry registry = ModelRegistry.builder()
                .config(config)
                .buildWithSuppliedTypeSystems(java.util.Map.of("DM01AVCH24LV95", mockTs), java.util.Map.of());

        TypeSystemFacade ts = registry.requireSourceTypeSystem("dm01");
        assertThat(ts).isSameAs(mockTs);
    }

    @Test
    void requireTargetTypeSystemReturnsTypeSystemFacade() {
        JobConfig config = twoInputsConfig();
        TypeSystemFacade mockTs = new TypeSystemFacade(null);
        ModelRegistry registry = ModelRegistry.builder()
                .config(config)
                .buildWithSuppliedTypeSystems(
                        java.util.Map.of(), java.util.Map.of("DMAV_FixpunkteAVKategorie3_V1_1", mockTs));

        TypeSystemFacade ts = registry.requireTargetTypeSystem("dmav");
        assertThat(ts).isSameAs(mockTs);
    }

    @Test
    void findByModelNameFindsCachedModel() {
        JobConfig config = twoInputsConfig();
        ModelRegistry registry = ModelRegistry.builder()
                .config(config)
                .buildWithSuppliedTypeSystems(java.util.Map.of(), java.util.Map.of());

        // Without pre-supplied ts, findByModelName should be empty
        assertThat(registry.findByModelName("DM01AVCH24LV95")).isEmpty();
    }

    @Test
    void optionsFlowIntoBindingsAsStrings() {
        JobConfig config = new JobConfig();
        config.version = 1;

        JobConfig.InputSpec in = new JobConfig.InputSpec();
        in.id = "src";
        in.model = "M";
        in.path = "in.csv";
        in.options = new java.util.LinkedHashMap<>();
        in.options.put("firstLineIsHeader", Boolean.TRUE);
        in.options.put("fetchSize", 10000);
        in.options.put("encoding", "UTF-8");
        config.job.inputs.add(in);

        JobConfig.OutputSpec out = new JobConfig.OutputSpec();
        out.id = "tgt";
        out.model = "M";
        out.path = "out.xtf";
        config.job.outputs.add(out);

        TypeSystemFacade mockTs = new TypeSystemFacade(null);
        ModelRegistry registry = ModelRegistry.builder()
                .config(config)
                .buildWithSuppliedTypeSystems(java.util.Map.of("M", mockTs), java.util.Map.of("M", mockTs));

        InputBinding binding = registry.requireInput("src");
        assertThat(binding.options())
                .containsEntry("firstLineIsHeader", "true")
                .containsEntry("fetchSize", "10000")
                .containsEntry("encoding", "UTF-8");

        // Outputs without options expose an empty (non-null) map.
        assertThat(registry.requireOutput("tgt").options()).isEmpty();
    }

    @Test
    void bindingOptionsAreNeverNull() {
        JobConfig config = twoInputsConfig();
        TypeSystemFacade mockTs = new TypeSystemFacade(null);
        ModelRegistry registry = ModelRegistry.builder()
                .config(config)
                .buildWithSuppliedTypeSystems(
                        java.util.Map.of("DM01AVCH24LV95", mockTs),
                        java.util.Map.of("DMAV_FixpunkteAVKategorie3_V1_1", mockTs));

        assertThat(registry.requireInput("dm01").options()).isNotNull().isEmpty();
        assertThat(registry.requireOutput("dmav").options()).isNotNull().isEmpty();
    }
}
