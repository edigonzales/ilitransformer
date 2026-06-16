package guru.interlis.transformer.mapping.compiler;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.FailPolicy;
import guru.interlis.transformer.mapping.plan.InputBinding;
import guru.interlis.transformer.mapping.plan.OutputBinding;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.ModelRegistry;
import guru.interlis.transformer.model.TypeSystemFacade;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TransformPlanBindingTest {

    private static final String MODELDIR = "src/test/data/models/";
    private static TypeSystemFacade testModelTs;
    private static ModelRegistry modelRegistry;
    private static JobConfig testConfig;

    @BeforeAll
    static void compileModels() {
        IliModelService service = new IliModelService();
        var result = service.compileModel("src/test/data/models/minimal.ili", MODELDIR);
        assertThat(result.hasErrors()).isFalse();
        testModelTs = new TypeSystemFacade(result.transferDescription());

        testConfig = minimalConfig();
        modelRegistry = ModelRegistry.builder()
                .config(testConfig)
                .buildWithSuppliedTypeSystems(Map.of("TestModel", testModelTs), Map.of("TestModel", testModelTs));
    }

    private static JobConfig minimalConfig() {
        JobConfig config = new JobConfig();
        config.version = 1;
        config.job.name = "binding-test";
        config.job.direction = "forward";
        config.job.failPolicy = "lenient";

        JobConfig.InputSpec in = new JobConfig.InputSpec();
        in.id = "in1";
        in.model = "TestModel";
        in.path = "in.xtf";
        config.job.inputs.add(in);

        JobConfig.OutputSpec out = new JobConfig.OutputSpec();
        out.id = "out1";
        out.model = "TestModel";
        out.path = "out.xtf";
        config.job.outputs.add(out);

        JobConfig.RuleSpec rule = new JobConfig.RuleSpec();
        rule.id = "r1";
        rule.target = new JobConfig.TargetSpec();
        rule.target.clazz = "TestModel.TestTopic.TestClass";
        rule.target.output = "out1";

        JobConfig.SourceSpec src = new JobConfig.SourceSpec();
        src.alias = "s";
        src.clazz = "TestModel.TestTopic.TestClass";
        src.inputs = List.of("in1");
        rule.sources.add(src);

        rule.assign = new java.util.LinkedHashMap<>();
        rule.assign.put("Name", "${s.Name}");
        rule.assign.put("Beschreibung", "${s.Beschreibung}");

        config.mapping.rules.add(rule);
        return config;
    }

    @Test
    void transformPlanContainsInputBindings() {
        TransformPlan plan = new MappingCompiler().compileTyped(testConfig, modelRegistry);

        Map<String, InputBinding> inputs = plan.inputsById();
        assertThat(inputs).containsKey("in1");
        InputBinding binding = inputs.get("in1");
        assertThat(binding.inputId()).isEqualTo("in1");
        assertThat(binding.declaredModelName()).isEqualTo("TestModel");
        assertThat(binding.typeSystem()).isNotNull();
    }

    @Test
    void transformPlanContainsOutputBindings() {
        TransformPlan plan = new MappingCompiler().compileTyped(testConfig, modelRegistry);

        Map<String, OutputBinding> outputs = plan.outputsById();
        assertThat(outputs).containsKey("out1");
        OutputBinding binding = outputs.get("out1");
        assertThat(binding.outputId()).isEqualTo("out1");
        assertThat(binding.declaredModelName()).isEqualTo("TestModel");
        assertThat(binding.typeSystem()).isNotNull();
    }

    @Test
    void failPolicyIsEnum() {
        TransformPlan plan = new MappingCompiler().compileTyped(testConfig, modelRegistry);
        assertThat(plan.failPolicy()).isEqualTo(FailPolicy.LENIENT);
    }

    @Test
    void oidPlanIsPresent() {
        TransformPlan plan = new MappingCompiler().compileTyped(testConfig, modelRegistry);
        assertThat(plan.oidPlan()).isNotNull();
        assertThat(plan.oidPlan().defaultStrategy().name()).isEqualTo("INTEGER");
    }

    @Test
    void basketPlanIsPresent() {
        TransformPlan plan = new MappingCompiler().compileTyped(testConfig, modelRegistry);
        assertThat(plan.basketPlan()).isNotNull();
        assertThat(plan.basketPlan().defaultStrategy().name()).isEqualTo("PRESERVE");
    }

    @Test
    void noSourceTypeSystemsOrTargetTypeSystems() {
        TransformPlan plan = new MappingCompiler().compileTyped(testConfig, modelRegistry);
        assertThat(plan.inputsById()).isNotEmpty();
        assertThat(plan.outputsById()).isNotEmpty();
    }

    @Test
    void roleResolverCanAccessTypeSystemFromOutputBinding() {
        TransformPlan plan = new MappingCompiler().compileTyped(testConfig, modelRegistry);
        OutputBinding outputBinding = plan.outputsById().get("out1");
        assertThat(outputBinding).isNotNull();
        assertThat(outputBinding.typeSystem()).isNotNull();
    }
}
