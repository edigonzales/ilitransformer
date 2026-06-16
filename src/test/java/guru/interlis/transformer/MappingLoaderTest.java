package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.model.MappingLoader;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class MappingLoaderTest {

    private final MappingLoader loader = new MappingLoader();

    @Test
    void loadsMinimalValidMapping() throws Exception {
        Path path = Path.of("src/test/resources/mappings/minimal-valid.yaml");
        JobConfig config = loader.load(path);

        assertThat(config.version).isEqualTo(1);
        assertThat(config.job.inputs).hasSize(1);
        assertThat(config.job.inputs.get(0).id).isEqualTo("in1");
        assertThat(config.job.outputs).hasSize(1);
        assertThat(config.job.outputs.get(0).id).isEqualTo("out1");
        assertThat(config.mapping.rules).hasSize(1);

        JobConfig.RuleSpec rule = config.mapping.rules.get(0);
        assertThat(rule.id).isEqualTo("rule1");
        assertThat(rule.getEffectiveTargetClass()).isEqualTo("TestModel.TestTopic.TestClass");
        assertThat(rule.getEffectiveTargetOutput()).isEqualTo("out1");
        assertThat(rule.sources).hasSize(1);
        assertThat(rule.sources.get(0).alias).isEqualTo("s");
    }

    @Test
    void loadsClassFieldAsClazz() throws Exception {
        Path path = Path.of("src/test/resources/mappings/minimal-valid.yaml");
        JobConfig config = loader.load(path);

        // YAML uses "class:", which maps to SourceSpec.clazz via @JsonProperty("class")
        assertThat(config.mapping.rules.get(0).sources.get(0).clazz).isEqualTo("TestModel.TestTopic.SourceClass");
    }

    @Test
    void loadsMultiInputAsList() throws Exception {
        Path path = Path.of("src/test/resources/mappings/multi-input.yaml");
        JobConfig config = loader.load(path);

        JobConfig.RuleSpec rule = config.mapping.rules.get(0);
        assertThat(rule.sources.get(0).getInputIds()).containsExactly("in1");
    }

    @Test
    void loadsAssignMap() throws Exception {
        Path path = Path.of("src/test/resources/mappings/multi-input.yaml");
        JobConfig config = loader.load(path);

        JobConfig.RuleSpec rule = config.mapping.rules.get(0);
        assertThat(rule.assign).isNotNull();
        assertThat(rule.getAllAttributes()).hasSize(1);
        assertThat(rule.getAllAttributes().get(0).target).isEqualTo("attr1");
        assertThat(rule.getAllAttributes().get(0).expr).isEqualTo("${s.field}");
    }

    @Test
    void normalizesBackwardCompatInputToInputs() throws Exception {
        // Programmatic config with old-style input field
        JobConfig config = new JobConfig();
        config.version = 1;
        JobConfig.InputSpec in = new JobConfig.InputSpec();
        in.id = "inX";
        config.job.inputs.add(in);
        JobConfig.OutputSpec out = new JobConfig.OutputSpec();
        out.id = "outX";
        config.job.outputs.add(out);

        JobConfig.RuleSpec rule = new JobConfig.RuleSpec();
        rule.id = "r1";
        rule.targetClass = "M.T.C";
        rule.output = "outX";
        JobConfig.SourceSpec src = new JobConfig.SourceSpec();
        src.alias = "s";
        src.input = "inX";
        src.clazz = "M.S.C";
        rule.sources.add(src);
        config.mapping.rules.add(rule);

        // Simulate what load() does
        new guru.interlis.transformer.mapping.model.MappingLoader()
                .load(java.nio.file.Path.of(
                        "src/test/resources/mappings/minimal-valid.yaml")); // just to verify loader works

        // Direct test: getInputIds() works even with old input field
        assertThat(src.getInputIds()).containsExactly("inX");
    }

    @Test
    void loadsFullDslExample() throws Exception {
        Path path = Path.of("profiles/dm01-to-dmav/1.1/lfp3.yaml");
        JobConfig config = loader.load(path);

        assertThat(config.version).isEqualTo(1);
        assertThat(config.job.name).isEqualTo("dm01-to-dmav-lfp3");
        assertThat(config.job.direction).isEqualTo("dm01-to-dmav");
        assertThat(config.job.failPolicy).isEqualTo("strict");
        assertThat(config.job.modeldir).contains("https://models.geo.admin.ch/");

        assertThat(config.mapping.oidStrategy.defaultStrategy).isEqualTo("deterministicUuid");
        assertThat(config.mapping.oidStrategy.namespace).isEqualTo("dm01-to-dmav-lfp3");
        assertThat(config.mapping.basketStrategy.defaultStrategy).isEqualTo("byTopic");

        assertThat(config.mapping.rules).hasSize(2);
        assertThat(config.mapping.rules.get(0).id).isEqualTo("lfp3-nachfuehrung");
        assertThat(config.mapping.rules.get(1).id).isEqualTo("lfp3");

        // Assign map
        JobConfig.RuleSpec lfp3 = config.mapping.rules.get(1);
        assertThat(lfp3.assign).containsKeys("NBIdent", "Nummer", "LFPArt", "Geometrie");
        assertThat(lfp3.getAllAttributes()).isNotEmpty();

        // Refs
        assertThat(lfp3.refs).hasSize(1);
        assertThat(lfp3.refs.get(0).association).isEqualTo("Entstehung_LFP3");
        assertThat(lfp3.refs.get(0).targetObject).isNotNull();
        assertThat(lfp3.refs.get(0).targetObject.rule).isEqualTo("lfp3-nachfuehrung");

        // Identity
        assertThat(lfp3.identity).isNotNull();
        assertThat(lfp3.identity.sourceKey).containsExactly("p.NBIdent", "p.Nummer");
    }
}
