package guru.interlis.transformer.mapping.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

class JobConfigNormalizerTest {

    @Test
    void normalizesFlatTargetClassIntoNestedTarget() {
        JobConfig config = new JobConfig();
        config.version = 1;
        config.job = new JobConfig.JobSection();
        config.job.outputs = List.of();
        config.mapping = new JobConfig.MappingSection();

        JobConfig.RuleSpec rule = new JobConfig.RuleSpec();
        rule.id = "r1";
        rule.targetClass = "Model.Topic.Class";
        rule.output = "out1";
        rule.sources = new ArrayList<>();
        config.mapping.rules.add(rule);

        JobConfigNormalizer.normalize(config);

        assertThat(rule.target).isNotNull();
        assertThat(rule.target.clazz).isEqualTo("Model.Topic.Class");
        assertThat(rule.target.output).isEqualTo("out1");
    }

    @Test
    void doesNotOverwriteNestedTargetWhenPresent() {
        JobConfig config = new JobConfig();
        config.version = 1;
        config.job = new JobConfig.JobSection();
        config.job.outputs = List.of();
        config.mapping = new JobConfig.MappingSection();

        JobConfig.RuleSpec rule = new JobConfig.RuleSpec();
        rule.id = "r1";
        rule.target = new JobConfig.TargetSpec();
        rule.target.clazz = "Model.Topic.NewClass";
        rule.target.output = "out2";
        rule.targetClass = "Model.Topic.OldClass";
        rule.output = "out1";
        rule.sources = new ArrayList<>();
        config.mapping.rules.add(rule);

        JobConfigNormalizer.normalize(config);

        assertThat(rule.target.clazz).isEqualTo("Model.Topic.NewClass");
        assertThat(rule.target.output).isEqualTo("out2");
    }

    @Test
    void normalizesSingleInputToInputsList() {
        JobConfig config = new JobConfig();
        config.version = 1;
        config.job = new JobConfig.JobSection();
        config.job.outputs = List.of();
        config.mapping = new JobConfig.MappingSection();

        JobConfig.RuleSpec rule = new JobConfig.RuleSpec();
        rule.id = "r1";
        rule.target = new JobConfig.TargetSpec();
        rule.target.clazz = "M.C";
        rule.sources = new ArrayList<>();
        JobConfig.SourceSpec src = new JobConfig.SourceSpec();
        src.alias = "s";
        src.clazz = "M.S";
        src.input = "myInput";
        rule.sources.add(src);
        config.mapping.rules.add(rule);

        JobConfigNormalizer.normalize(config);

        assertThat(src.inputs).isNotNull().containsExactly("myInput");
    }

    @Test
    void doesNotMutateSourcesListWhenNull() {
        JobConfig config = new JobConfig();
        config.version = 1;
        config.job = new JobConfig.JobSection();
        config.job.outputs = List.of();
        config.mapping = new JobConfig.MappingSection();

        JobConfig.RuleSpec rule = new JobConfig.RuleSpec();
        rule.id = "r1";
        rule.target = new JobConfig.TargetSpec();
        rule.target.clazz = "M.C";
        rule.sources = null;
        config.mapping.rules.add(rule);

        JobConfigNormalizer.normalize(config);

        assertThat(rule.sources).isNotNull();
    }

    @Test
    void fillsDefaultSectionsWhenNull() {
        JobConfig config = new JobConfig();
        config.version = 1;
        config.job = null;
        config.mapping = null;

        JobConfigNormalizer.normalize(config);

        assertThat(config.job).isNotNull();
        assertThat(config.mapping).isNotNull();
        assertThat(config.mapping.oidStrategy).isNotNull();
        assertThat(config.mapping.oidStrategy.defaultStrategy).isEqualTo("integer");
        assertThat(config.mapping.basketStrategy).isNotNull();
        assertThat(config.mapping.basketStrategy.defaultStrategy).isEqualTo("preserve");
    }

    @Test
    void preservesExistingSourcesInNewFormat() {
        JobConfig config = new JobConfig();
        config.version = 1;
        config.job = new JobConfig.JobSection();
        config.job.outputs = List.of();
        config.mapping = new JobConfig.MappingSection();

        JobConfig.RuleSpec rule = new JobConfig.RuleSpec();
        rule.id = "r1";
        rule.target = new JobConfig.TargetSpec();
        rule.target.clazz = "M.C";
        rule.sources = new ArrayList<>();
        JobConfig.SourceSpec src = new JobConfig.SourceSpec();
        src.alias = "s";
        src.clazz = "M.S";
        src.inputs = List.of("in1", "in2");
        rule.sources.add(src);
        config.mapping.rules.add(rule);

        JobConfigNormalizer.normalize(config);

        assertThat(src.inputs).containsExactly("in1", "in2");
        assertThat(src.input).isNull();
    }

    @Test
    void normalizeOptionsConvertsValuesToStrings() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("firstLineIsHeader", Boolean.TRUE);
        raw.put("fetchSize", 10000);
        raw.put("encoding", "UTF-8");
        raw.put("skip", null);

        Map<String, String> normalized = JobConfigNormalizer.normalizeOptions(raw);

        assertThat(normalized)
                .containsEntry("firstLineIsHeader", "true")
                .containsEntry("fetchSize", "10000")
                .containsEntry("encoding", "UTF-8")
                .doesNotContainKey("skip");
    }

    @Test
    void normalizeOptionsHandlesNullAndEmpty() {
        assertThat(JobConfigNormalizer.normalizeOptions(null)).isEmpty();
        assertThat(JobConfigNormalizer.normalizeOptions(new LinkedHashMap<>())).isEmpty();
    }

    @Test
    void normalizeEnsuresInputOutputOptionsNonNull() {
        JobConfig config = new JobConfig();
        config.version = 1;

        JobConfig.InputSpec input = new JobConfig.InputSpec();
        input.id = "src";
        input.options = null;
        config.job.inputs.add(input);

        JobConfig.OutputSpec output = new JobConfig.OutputSpec();
        output.id = "tgt";
        output.options = null;
        config.job.outputs.add(output);

        JobConfigNormalizer.normalize(config);

        assertThat(config.job.inputs.get(0).options).isNotNull().isEmpty();
        assertThat(config.job.outputs.get(0).options).isNotNull().isEmpty();
    }

    @Test
    void yamlOptionsDeserializeAsObjectsAndNormalizeToStrings() throws Exception {
        String yaml = """
                version: 1
                job:
                  inputs:
                    - id: source
                      path: input/municipalities.csv
                      model: DemoCsvSource
                      format: csv
                      options:
                        firstLineIsHeader: true
                        separator: ";"
                        fetchSize: 10000
                        encoding: UTF-8
                  outputs:
                    - id: target
                      path: build/out.xtf
                      model: DemoTarget
                """;

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JobConfig config = mapper.readValue(yaml, JobConfig.class);
        JobConfigNormalizer.normalize(config);

        Map<String, Object> raw = config.job.inputs.get(0).options;
        assertThat(raw).containsKeys("firstLineIsHeader", "separator", "fetchSize", "encoding");
        assertThat(raw.get("firstLineIsHeader")).isEqualTo(Boolean.TRUE);
        assertThat(raw.get("fetchSize")).isEqualTo(10000);

        Map<String, String> normalized = JobConfigNormalizer.normalizeOptions(raw);
        assertThat(normalized)
                .containsEntry("firstLineIsHeader", "true")
                .containsEntry("separator", ";")
                .containsEntry("fetchSize", "10000")
                .containsEntry("encoding", "UTF-8");

        // Output without options remains usable.
        assertThat(config.job.outputs.get(0).options).isNotNull().isEmpty();
    }
}
