package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.model.JobConfig;

import org.junit.jupiter.api.Test;

class MappingCompilerTest {

    private static JobConfig minimalConfig() {
        JobConfig config = new JobConfig();
        config.version = 1;

        JobConfig.InputSpec in = new JobConfig.InputSpec();
        in.id = "in1";
        in.model = "A";
        in.path = "in.xtf";
        config.job.inputs.add(in);

        JobConfig.OutputSpec out = new JobConfig.OutputSpec();
        out.id = "out1";
        out.model = "B";
        out.path = "out.xtf";
        config.job.outputs.add(out);

        JobConfig.RuleSpec rule = new JobConfig.RuleSpec();
        rule.id = "rule1";
        rule.target = new JobConfig.TargetSpec();
        rule.target.clazz = "M.T.C";
        rule.target.output = "out1";

        JobConfig.SourceSpec src = new JobConfig.SourceSpec();
        src.alias = "s";
        src.inputs = java.util.List.of("in1");
        src.clazz = "M.S.C";
        rule.sources.add(src);
        config.mapping.rules.add(rule);

        return config;
    }

    @Test
    void validatesMinimalConfig() {
        assertThatCode(() -> new MappingCompiler().validate(minimalConfig())).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingVersion() {
        JobConfig config = minimalConfig();
        config.version = 0;
        assertThatThrownBy(() -> new MappingCompiler().validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
    }

    @Test
    void rejectsUnknownOutput() {
        JobConfig config = minimalConfig();
        config.mapping.rules.get(0).target.output = "missing";
        assertThatThrownBy(() -> new MappingCompiler().validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown output");
    }

    @Test
    void rejectsMissingRuleId() {
        JobConfig config = minimalConfig();
        config.mapping.rules.get(0).id = null;
        assertThatThrownBy(() -> new MappingCompiler().validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void rejectsDuplicateRuleIds() {
        JobConfig config = minimalConfig();
        JobConfig.RuleSpec rule2 = new JobConfig.RuleSpec();
        rule2.id = "rule1"; // same as existing
        rule2.target = new JobConfig.TargetSpec();
        rule2.target.clazz = "M.T.Other";
        rule2.target.output = "out1";
        config.mapping.rules.add(rule2);
        assertThatThrownBy(() -> new MappingCompiler().validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void rejectsMissingSourceClass() {
        JobConfig config = minimalConfig();
        config.mapping.rules.get(0).sources.get(0).clazz = null;
        assertThatThrownBy(() -> new MappingCompiler().validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("class");
    }

    @Test
    void rejectsDuplicateAliases() {
        JobConfig config = minimalConfig();
        JobConfig.SourceSpec src2 = new JobConfig.SourceSpec();
        src2.alias = "s"; // same as existing
        src2.clazz = "M.S.Other";
        src2.inputs = java.util.List.of("in1");
        config.mapping.rules.get(0).sources.add(src2);
        assertThatThrownBy(() -> new MappingCompiler().validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void compileReturnsDiagnosticsInsteadOfThrowing() {
        JobConfig config = minimalConfig();
        config.version = 0;
        var result = new MappingCompiler().compile(config);
        assertThat(result.diagnostics().hasErrors()).isTrue();
        assertThat(result.diagnostics().all()).isNotEmpty();
    }

    @Test
    void backwardCompatFlatTargetClassStillWorks() {
        JobConfig config = new JobConfig();
        config.version = 1;
        JobConfig.InputSpec in = new JobConfig.InputSpec();
        in.id = "in1";
        in.model = "A";
        in.path = "in.xtf";
        config.job.inputs.add(in);
        JobConfig.OutputSpec out = new JobConfig.OutputSpec();
        out.id = "out1";
        out.model = "B";
        out.path = "out.xtf";
        config.job.outputs.add(out);

        JobConfig.RuleSpec rule = new JobConfig.RuleSpec();
        rule.id = "r1";
        rule.targetClass = "M.T.C"; // flat format
        rule.output = "out1"; // flat format
        JobConfig.SourceSpec src = new JobConfig.SourceSpec();
        src.alias = "s";
        src.input = "in1"; // single string input
        src.clazz = "M.S.C";
        rule.sources.add(src);
        config.mapping.rules.add(rule);

        assertThatCode(() -> new MappingCompiler().validate(config)).doesNotThrowAnyException();

        // Effective accessors also work on flat format after normalization
        var normalized = new MappingCompiler().compile(config).config();
        JobConfig.RuleSpec loadedRule = normalized.mapping.rules.get(0);
        assertThat(loadedRule.getEffectiveTargetClass()).isEqualTo("M.T.C");
        assertThat(loadedRule.getEffectiveTargetOutput()).isEqualTo("out1");
        assertThat(loadedRule.sources.get(0).getInputIds()).containsExactly("in1");
    }
}
