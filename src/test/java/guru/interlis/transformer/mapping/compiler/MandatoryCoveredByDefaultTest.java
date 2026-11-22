package guru.interlis.transformer.mapping.compiler;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.ModelRegistry;

import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

class MandatoryCoveredByDefaultTest {

    private static final String MODELDIR = "src/test/data/models/";

    @Test
    void mandatoryAttributeCoveredByRuleDefault() {
        JobConfig config = minimalConfig();
        var rule = config.mapping.rules.get(0);
        rule.assign = new LinkedHashMap<>();
        rule.assign.put("Beschreibung", "${s.Beschreibung}");
        // Name (mandatory) not explicitly assigned

        rule.defaults = new LinkedHashMap<>();
        rule.defaults.put("Name", "\"default-name\"");

        var registry = ModelRegistry.builder().config(config).modelDir(MODELDIR).build();
        TransformPlan plan = new MappingCompiler().compileTyped(config, registry);

        // Name should be in assignments via default
        var nameAssign = plan.rules().get(0).assignments().stream()
                .filter(a -> a.targetAttrName().equals("Name"))
                .findFirst();
        assertThat(nameAssign).isPresent();
    }

    @Test
    void mandatoryAttributeCoveredByMappingDefault() {
        JobConfig config = minimalConfig();
        var rule = config.mapping.rules.get(0);
        rule.assign = new LinkedHashMap<>();
        rule.assign.put("Beschreibung", "${s.Beschreibung}");
        // Name (mandatory) not explicitly assigned, no rule default

        config.mapping.defaults = new LinkedHashMap<>();
        config.mapping.defaults.put("Name", "\"global-default\"");

        var registry = ModelRegistry.builder().config(config).modelDir(MODELDIR).build();
        TransformPlan plan = new MappingCompiler().compileTyped(config, registry);

        // Name should be in assignments via mapping default
        var nameAssign = plan.rules().get(0).assignments().stream()
                .filter(a -> a.targetAttrName().equals("Name"))
                .findFirst();
        assertThat(nameAssign).isPresent();
        assertThat(nameAssign.get().expression().sourceText()).isEqualTo("\"global-default\"");
    }

    @Test
    void mandatoryAttributeMissingWithoutDefaults() {
        JobConfig config = minimalConfig();
        var rule = config.mapping.rules.get(0);
        rule.assign = new LinkedHashMap<>();
        rule.assign.put("Beschreibung", "${s.Beschreibung}");
        // Name (mandatory) not assigned, no defaults

        var registry = ModelRegistry.builder().config(config).modelDir(MODELDIR).build();
        TransformPlan plan = new MappingCompiler().compileTyped(config, registry);

        // Should still report mandatory missing (upgraded to ERROR in STRICT mode)
        assertThat(plan.diagnostics().all())
                .anyMatch(d -> d.code().equals(DiagnosticCode.MAP_MANDATORY_MISSING)
                        && d.message().contains("Name"));
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
