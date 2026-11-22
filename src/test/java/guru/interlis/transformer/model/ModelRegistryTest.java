package guru.interlis.transformer.model;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.InputBinding;
import guru.interlis.transformer.mapping.plan.OutputBinding;

import org.junit.jupiter.api.Test;

class ModelRegistryTest {

    private static final String MODELDIR = "src/test/data/models/";

    private static JobConfig twoInputsConfig() {
        JobConfig config = new JobConfig();
        config.version = 1;
        config.job.name = "test-job";
        config.job.direction = "forward";

        JobConfig.InputSpec in1 = new JobConfig.InputSpec();
        in1.id = "dm01";
        in1.model = "TestModel";
        in1.path = "input.xtf";
        config.job.inputs.add(in1);

        JobConfig.InputSpec in2 = new JobConfig.InputSpec();
        in2.id = "gwr";
        in2.model = "TestModel";
        in2.path = "gwr.xtf";
        config.job.inputs.add(in2);

        JobConfig.OutputSpec out = new JobConfig.OutputSpec();
        out.id = "dmav";
        out.model = "P5Model";
        out.path = "output.xtf";
        config.job.outputs.add(out);

        return config;
    }

    @Test
    void registryRequiresInputById() {
        JobConfig config = twoInputsConfig();

        ModelRegistry registry =
                ModelRegistry.builder().config(config).modelDir(MODELDIR).build();

        InputBinding dm01 = registry.requireInput("dm01");
        assertThat(dm01.inputId()).isEqualTo("dm01");
        assertThat(dm01.declaredModelName()).isEqualTo("TestModel");

        InputBinding gwr = registry.requireInput("gwr");
        assertThat(gwr.inputId()).isEqualTo("gwr");
        assertThat(gwr.declaredModelName()).isEqualTo("TestModel");

        OutputBinding dmav = registry.requireOutput("dmav");
        assertThat(dmav.outputId()).isEqualTo("dmav");
        assertThat(dmav.declaredModelName()).isEqualTo("P5Model");
    }

    @Test
    void registryThrowsForUnknownInputId() {
        JobConfig config = twoInputsConfig();
        ModelRegistry registry =
                ModelRegistry.builder().config(config).modelDir(MODELDIR).build();

        assertThatThrownBy(() -> registry.requireInput("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown input ID");
    }

    @Test
    void registryThrowsForUnknownOutputId() {
        JobConfig config = twoInputsConfig();
        ModelRegistry registry =
                ModelRegistry.builder().config(config).modelDir(MODELDIR).build();

        assertThatThrownBy(() -> registry.requireOutput("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown output ID");
    }

    @Test
    void outputIdDifferentFromModelName() {
        // Output ID "dmav" != model name "P5Model"
        JobConfig config = twoInputsConfig();

        ModelRegistry registry =
                ModelRegistry.builder().config(config).modelDir(MODELDIR).build();

        OutputBinding dmav = registry.requireOutput("dmav");
        assertThat(dmav.outputId()).isEqualTo("dmav");
        assertThat(dmav.declaredModelName()).isEqualTo("P5Model");
        assertThat(dmav.outputId()).isNotEqualTo(dmav.declaredModelName());
    }

    @Test
    void inputIdDifferentFromModelName() {
        JobConfig config = new JobConfig();
        config.version = 1;
        JobConfig.InputSpec in = new JobConfig.InputSpec();
        in.id = "src1";
        in.model = "TestModel";
        config.job.inputs.add(in);

        ModelRegistry registry =
                ModelRegistry.builder().config(config).modelDir(MODELDIR).build();

        InputBinding binding = registry.requireInput("src1");
        assertThat(binding.inputId()).isEqualTo("src1");
        assertThat(binding.declaredModelName()).isEqualTo("TestModel");
        assertThat(binding.inputId()).isNotEqualTo(binding.declaredModelName());
    }

    @Test
    void multipleInputsSameModel() {
        JobConfig config = twoInputsConfig();

        ModelRegistry registry =
                ModelRegistry.builder().config(config).modelDir(MODELDIR).build();

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
        out1.model = "TestModel";
        config.job.outputs.add(out1);

        JobConfig.OutputSpec out2 = new JobConfig.OutputSpec();
        out2.id = "targetB";
        out2.model = "P5Model";
        config.job.outputs.add(out2);

        ModelRegistry registry =
                ModelRegistry.builder().config(config).modelDir(MODELDIR).build();

        OutputBinding a = registry.requireOutput("targetA");
        OutputBinding b = registry.requireOutput("targetB");
        assertThat(a.declaredModelName()).isEqualTo("TestModel");
        assertThat(b.declaredModelName()).isEqualTo("P5Model");
        assertThat(a.declaredModelName()).isNotEqualTo(b.declaredModelName());
    }

    @Test
    void requireSourceTypeSystemReturnsTypeSystemFacade() {
        JobConfig config = twoInputsConfig();
        ModelRegistry registry =
                ModelRegistry.builder().config(config).modelDir(MODELDIR).build();

        TypeSystemFacade ts = registry.requireSourceTypeSystem("dm01");
        assertThat(ts.getTransferDescription())
                .isSameAs(registry.findByModelName("TestModel").orElseThrow());
    }

    @Test
    void requireTargetTypeSystemReturnsTypeSystemFacade() {
        JobConfig config = twoInputsConfig();
        ModelRegistry registry =
                ModelRegistry.builder().config(config).modelDir(MODELDIR).build();

        TypeSystemFacade ts = registry.requireTargetTypeSystem("dmav");
        assertThat(ts.getTransferDescription())
                .isSameAs(registry.findByModelName("P5Model").orElseThrow());
    }

    @Test
    void findByModelNameFindsCachedModel() {
        JobConfig config = twoInputsConfig();
        ModelRegistry registry =
                ModelRegistry.builder().config(config).modelDir(MODELDIR).build();

        assertThat(registry.findByModelName("TestModel"))
                .containsSame(registry.requireInput("dm01").transferDescription());
        assertThat(registry.findByModelName("unknown")).isEmpty();
    }

    @Test
    void optionsFlowIntoBindingsAsStrings() {
        JobConfig config = new JobConfig();
        config.version = 1;

        JobConfig.InputSpec in = new JobConfig.InputSpec();
        in.id = "src";
        in.model = "TestModel";
        in.path = "in.csv";
        in.options = new java.util.LinkedHashMap<>();
        in.options.put("firstLineIsHeader", Boolean.TRUE);
        in.options.put("fetchSize", 10000);
        in.options.put("encoding", "UTF-8");
        config.job.inputs.add(in);

        JobConfig.OutputSpec out = new JobConfig.OutputSpec();
        out.id = "tgt";
        out.model = "TestModel";
        out.path = "out.xtf";
        config.job.outputs.add(out);

        ModelRegistry registry =
                ModelRegistry.builder().config(config).modelDir(MODELDIR).build();

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
        ModelRegistry registry =
                ModelRegistry.builder().config(config).modelDir(MODELDIR).build();

        assertThat(registry.requireInput("dm01").options()).isNotNull().isEmpty();
        assertThat(registry.requireOutput("dmav").options()).isNotNull().isEmpty();
    }
}
