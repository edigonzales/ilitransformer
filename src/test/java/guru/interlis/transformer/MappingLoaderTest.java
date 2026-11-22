package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.model.MappingLoader;

import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
        assertThat(rule.target.clazz).isEqualTo("TestModel.TestTopic.TestClass");
        assertThat(rule.target.output).isEqualTo("out1");
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
        assertThat(rule.sources.get(0).inputs).containsExactly("in1");
    }

    @Test
    void loadsAssignMap() throws Exception {
        Path path = Path.of("src/test/resources/mappings/multi-input.yaml");
        JobConfig config = loader.load(path);

        JobConfig.RuleSpec rule = config.mapping.rules.get(0);
        assertThat(rule.assign).isNotNull();
        assertThat(rule.assign).containsExactlyEntriesOf(java.util.Map.of("attr1", "${s.field}"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            id: rule1 | targetClass: TestModel.TestTopic.TestClass
            id: rule1 | output: out1
            id: rule1 | attributes: []
            inputs: [in1] | input: in1
            class: "TestModel.TestTopic.TestClass" | clazz: TestModel.TestTopic.TestClass
            class: "TestModel.TestTopic.SourceClass" | clazz: TestModel.TestTopic.SourceClass
            role: "Entstehung" | target: Entstehung
            role: "Entstehung" | expr: p.Entstehung
            role: "Entstehung" | targetObject: {}
            """)
    void rejectsLegacyFields(String canonical, String legacy, @TempDir Path tempDir) throws Exception {
        Path source = canonical.startsWith("role:")
                ? Path.of("profiles/dm01-to-dmav/1.1/lfp3.yaml")
                : Path.of("src/test/resources/mappings/minimal-valid.yaml");
        String yaml = Files.readString(source);
        assertThat(yaml).contains(canonical);
        Path mapping = Files.writeString(tempDir.resolve("legacy.yaml"), yaml.replace(canonical, legacy));
        assertThatThrownBy(() -> loader.load(mapping))
                .isInstanceOfSatisfying(
                        UnrecognizedPropertyException.class,
                        error -> assertThat(error.getPropertyName()).isEqualTo(legacy.split(":", 2)[0]));
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

        // Refs
        assertThat(lfp3.refs).hasSize(1);
        assertThat(lfp3.refs.get(0).association).isEqualTo("Entstehung_LFP3");
        assertThat(lfp3.refs.get(0).targetRule).isEqualTo("lfp3-nachfuehrung");

        // Identity
        assertThat(lfp3.identity).isNotNull();
        assertThat(lfp3.identity.sourceKey).containsExactly("p.NBIdent", "p.Nummer");
    }
}
