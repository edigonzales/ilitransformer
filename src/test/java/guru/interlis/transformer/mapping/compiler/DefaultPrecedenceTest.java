package guru.interlis.transformer.mapping.compiler;

import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.AssignmentPlan;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.TypeSystemFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class DefaultPrecedenceTest {

    private static final String MODELDIR = "src/test/data/models/";
    private static TypeSystemFacade testModelTs;

    @BeforeAll
    static void compileModels() {
        IliModelService service = new IliModelService();
        var result = service.compileModel("src/test/data/models/minimal.ili", MODELDIR);
        assertThat(result.hasErrors()).isFalse();
        testModelTs = new TypeSystemFacade(result.transferDescription());
    }

    @Test
    void explicitAssignOverridesRuleDefault() {
        JobConfig config = minimalConfig();
        var rule = config.mapping.rules.get(0);
        rule.assign = new LinkedHashMap<>();
        rule.assign.put("Name", "${s.Name}"); // explicit

        rule.defaults = new LinkedHashMap<>();
        rule.defaults.put("Name", "${s.Beschreibung}"); // rule default - should not be used

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        var nameAssign = plan.rules().get(0).assignments().stream()
                .filter(a -> a.targetAttrName().equals("Name")).findFirst();
        assertThat(nameAssign).isPresent();
        assertThat(nameAssign.get().expression().sourceText()).isEqualTo("${s.Name}");
    }

    @Test
    void ruleDefaultOverridesMappingDefault() {
        JobConfig config = minimalConfig();
        var rule = config.mapping.rules.get(0);
        rule.assign = new LinkedHashMap<>();
        // No explicit assign for Beschreibung

        rule.defaults = new LinkedHashMap<>();
        rule.defaults.put("Beschreibung", "${s.Name}"); // rule default

        config.mapping.defaults = new LinkedHashMap<>();
        config.mapping.defaults.put("Beschreibung", "\"mapping-default\""); // mapping default - should not be used

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        var descAssign = plan.rules().get(0).assignments().stream()
                .filter(a -> a.targetAttrName().equals("Beschreibung")).findFirst();
        assertThat(descAssign).isPresent();
        assertThat(descAssign.get().expression().sourceText()).isEqualTo("${s.Name}");
    }

    @Test
    void mappingDefaultUsedWhenNoRuleDefault() {
        JobConfig config = minimalConfig();
        var rule = config.mapping.rules.get(0);
        rule.assign = new LinkedHashMap<>();
        // No explicit assign for Beschreibung, no rule default

        config.mapping.defaults = new LinkedHashMap<>();
        config.mapping.defaults.put("Beschreibung", "\"mapping-default\"");

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        var descAssign = plan.rules().get(0).assignments().stream()
                .filter(a -> a.targetAttrName().equals("Beschreibung")).findFirst();
        assertThat(descAssign).isPresent();
        assertThat(descAssign.get().expression().sourceText()).isEqualTo("\"mapping-default\"");
    }

    @Test
    void explicitAssignOverridesAllDefaults() {
        JobConfig config = minimalConfig();
        var rule = config.mapping.rules.get(0);
        rule.assign = new LinkedHashMap<>();
        rule.assign.put("Name", "${s.Name}"); // explicit

        rule.defaults = new LinkedHashMap<>();
        rule.defaults.put("Name", "${s.Beschreibung}"); // rule default

        config.mapping.defaults = new LinkedHashMap<>();
        config.mapping.defaults.put("Name", "\"global\""); // mapping default

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        var nameAssign = plan.rules().get(0).assignments().stream()
                .filter(a -> a.targetAttrName().equals("Name")).findFirst();
        assertThat(nameAssign).isPresent();
        assertThat(nameAssign.get().expression().sourceText()).isEqualTo("${s.Name}");
    }

    private static JobConfig minimalConfig() {
        JobConfig config = new JobConfig();
        config.version = 1;

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
        rule.id = "rule1";
        rule.target = new JobConfig.TargetSpec();
        rule.target.clazz = "TestModel.TestTopic.TestClass";
        rule.target.output = "out1";

        JobConfig.SourceSpec src = new JobConfig.SourceSpec();
        src.alias = "s";
        src.clazz = "TestModel.TestTopic.TestClass";
        src.inputs = List.of("in1");
        rule.sources.add(src);

        config.mapping.rules.add(rule);
        return config;
    }
}
