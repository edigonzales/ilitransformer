package guru.interlis.transformer;

import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.model.JobConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MappingCompilerTest {
    @Test
    void validatesMinimalConfig() {
        JobConfig config = new JobConfig();

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
        rule.targetClass = "M.T.C";
        rule.output = "out1";

        JobConfig.SourceSpec src = new JobConfig.SourceSpec();
        src.alias = "s";
        src.input = "in1";
        src.clazz = "M.S.C";
        rule.sources.add(src);
        config.mapping.rules.add(rule);

        assertThatCode(() -> new MappingCompiler().validate(config)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownOutput() {
        JobConfig config = new JobConfig();
        JobConfig.RuleSpec rule = new JobConfig.RuleSpec();
        rule.targetClass = "M.T.C";
        rule.output = "missing";

        JobConfig.SourceSpec src = new JobConfig.SourceSpec();
        src.alias = "s";
        src.input = "in1";
        src.clazz = "M.S.C";
        rule.sources.add(src);
        config.mapping.rules.add(rule);

        assertThatThrownBy(() -> new MappingCompiler().validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown output");
    }
}
