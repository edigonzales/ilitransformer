package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.model.JobConfig;

import org.junit.jupiter.api.Test;

class IdentityKeyCompilerTest {

    private static JobConfig configWithIdentityKey(String key) {
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
        rule.target = new JobConfig.TargetSpec();
        rule.target.clazz = "M.T.C";
        rule.target.output = "out1";

        JobConfig.SourceSpec src = new JobConfig.SourceSpec();
        src.alias = "s";
        src.inputs = java.util.List.of("in1");
        src.clazz = "M.S.C";
        rule.sources.add(src);

        if (key != null) {
            rule.identity = new JobConfig.IdentitySpec();
            rule.identity.sourceKey = java.util.List.of(key);
        }

        config.mapping.rules.add(rule);
        return config;
    }

    @Test
    void rejectsEmptyIdentityKey() {
        JobConfig config = configWithIdentityKey("  ");
        var result = new MappingCompiler().compile(config);
        assertThat(result.diagnostics().hasErrors()).isTrue();
        assertThat(result.diagnostics().all()).anyMatch(d -> d.code().equals("ILITRF-MAP-IDENTITY-KEY-MISSING"));
    }

    @Test
    void rejectsIdentityKeyWithoutAliasPrefix() {
        JobConfig config = configWithIdentityKey("Name");
        var result = new MappingCompiler().compile(config);
        assertThat(result.diagnostics().hasErrors()).isTrue();
        assertThat(result.diagnostics().all())
                .anyMatch(d -> d.code().equals("ILITRF-MAP-IDENTITY-KEY-MISSING")
                        && d.message().contains("qualified with alias"));
    }

    @Test
    void rejectsDuplicateIdentityKeys() {
        JobConfig config = configWithIdentityKey("s.Name");
        config.mapping.rules.get(0).identity.sourceKey = java.util.List.of("s.Name", "s.Name");
        var result = new MappingCompiler().compile(config);
        assertThat(result.diagnostics().all()).anyMatch(d -> d.code().equals("ILITRF-MAP-IDENTITY-KEY-DUPLICATE"));
    }

    @Test
    void rejectsUnknownAlias() {
        JobConfig config = configWithIdentityKey("unknownAlias.Name");
        var result = new MappingCompiler().compile(config);
        assertThat(result.diagnostics().all())
                .anyMatch(
                        d -> d.message().contains("identity key") || d.message().contains("alias"));
    }
}
