package guru.interlis.transformer.mapping.compiler;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.ModelRegistry;

import java.util.List;

import org.junit.jupiter.api.Test;

class UnsupportedOidStrategyRejectedTest {

    private static final String MODELDIR = "src/test/data/models/";

    @Test
    void externalOidStrategyIsRejected() {
        JobConfig config = minimalConfig();
        config.mapping.oidStrategy = new JobConfig.OidStrategySpec();
        config.mapping.oidStrategy.defaultStrategy = "external";

        var registry = ModelRegistry.builder().config(config).modelDir(MODELDIR).build();
        TransformPlan plan = new MappingCompiler().compileTyped(config, registry);

        assertThat(plan.diagnostics().all())
                .anyMatch(d -> d.code().equals(DiagnosticCode.MAP_EXTERNAL_STRATEGY_UNSUPPORTED)
                        && d.severity() == Severity.ERROR);
    }

    @Test
    void preserveOidStrategyAccepted() {
        JobConfig config = minimalConfig();
        config.mapping.oidStrategy = new JobConfig.OidStrategySpec();
        config.mapping.oidStrategy.defaultStrategy = "preserve";

        var registry = ModelRegistry.builder().config(config).modelDir(MODELDIR).build();
        TransformPlan plan = new MappingCompiler().compileTyped(config, registry);

        assertThat(plan.diagnostics().all())
                .noneMatch(d -> d.code().equals(DiagnosticCode.MAP_EXTERNAL_STRATEGY_UNSUPPORTED));
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

        rule.assign = new java.util.LinkedHashMap<>();
        rule.assign.put("Name", "${s.Name}");
        rule.assign.put("Beschreibung", "${s.Beschreibung}");

        config.mapping.rules.add(rule);
        return config;
    }
}
