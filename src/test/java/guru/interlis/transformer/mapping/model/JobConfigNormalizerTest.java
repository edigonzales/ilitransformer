package guru.interlis.transformer.mapping.model;

import guru.interlis.transformer.mapping.model.JobConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}
