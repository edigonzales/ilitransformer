package guru.interlis.transformer.mapping.ilimap.convert;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.mapping.ilimap.IlimapLoader;
import guru.interlis.transformer.mapping.ilimap.format.IlimapFormatter;
import guru.interlis.transformer.mapping.ilimap.parser.IlimapParser;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.model.MappingLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

class YamlToIlimapConverterTest {

    private final YamlToIlimapConverter converter = new YamlToIlimapConverter();

    @Test
    void convertsMinimalYamlToParseableIlimap() throws Exception {
        String ilimapText = converter.convert(Path.of("src/test/resources/mapping/equivalence/minimal.yaml"));

        assertThat(ilimapText).isNotBlank();
        assertThatCode(() -> new IlimapParser(ilimapText).parseDocument()).doesNotThrowAnyException();
    }

    @Test
    void convertedIlimapLoadsToEquivalentJobConfig() throws Exception {
        Path yamlPath = Path.of("src/test/resources/mapping/equivalence/minimal.yaml");
        String ilimapText = converter.convert(yamlPath);

        MappingLoader yamlLoader = new MappingLoader();
        JobConfig yamlConfig = yamlLoader.load(yamlPath);

        IlimapLoader ilimapLoader = new IlimapLoader();
        JobConfig ilimapConfig =
                ilimapLoader.load(ilimapText, yamlPath.toAbsolutePath().getParent());

        assertThat(ilimapConfig.version).isEqualTo(yamlConfig.version);
        assertThat(ilimapConfig.job.name).isEqualTo(yamlConfig.job.name);
        assertThat(ilimapConfig.job.direction).isEqualTo(yamlConfig.job.direction);
        assertThat(ilimapConfig.job.failPolicy).isEqualTo(yamlConfig.job.failPolicy);
        assertThat(ilimapConfig.mapping.compileMode).isEqualTo(yamlConfig.mapping.compileMode);

        assertThat(ilimapConfig.job.inputs).hasSize(yamlConfig.job.inputs.size());
        assertThat(ilimapConfig.job.inputs.get(0).id).isEqualTo(yamlConfig.job.inputs.get(0).id);
        assertThat(ilimapConfig.job.inputs.get(0).path).isEqualTo(yamlConfig.job.inputs.get(0).path);

        assertThat(ilimapConfig.job.outputs).hasSize(yamlConfig.job.outputs.size());
        assertThat(ilimapConfig.job.outputs.get(0).id).isEqualTo(yamlConfig.job.outputs.get(0).id);

        assertThat(ilimapConfig.mapping.enums).isEqualTo(yamlConfig.mapping.enums);

        assertThat(ilimapConfig.mapping.rules).hasSize(yamlConfig.mapping.rules.size());
        JobConfig.RuleSpec ilimapRule = ilimapConfig.mapping.rules.get(0);
        JobConfig.RuleSpec yamlRule = yamlConfig.mapping.rules.get(0);
        assertThat(ilimapRule.id).isEqualTo(yamlRule.id);
        assertThat(ilimapRule.target.output).isEqualTo(yamlRule.target.output);
        assertThat(ilimapRule.target.clazz).isEqualTo(yamlRule.target.clazz);
        assertThat(ilimapRule.where).isEqualTo(yamlRule.where);
        assertThat(ilimapRule.identity.sourceKey).isEqualTo(yamlRule.identity.sourceKey);
        assertThat(ilimapRule.assign).isEqualTo(yamlRule.assign);
    }

    @Test
    void convertedIlimapFormatsStably() throws Exception {
        String ilimapText = converter.convert(Path.of("src/test/resources/mapping/equivalence/minimal.yaml"));

        var doc1 = new IlimapParser(ilimapText).parseDocument();
        String formatted1 = new IlimapFormatter().format(doc1);
        var doc2 = new IlimapParser(formatted1).parseDocument();
        String formatted2 = new IlimapFormatter().format(doc2);

        assertThat(formatted2).isEqualTo(formatted1);
    }

    @Test
    void convertsComplexYamlWithBagsAndRefs() throws Exception {
        String ilimapText = converter.convert(Path.of("src/test/resources/mappings/dm01-to-dmav-lfp3-test.yaml"));

        assertThat(ilimapText).isNotBlank();
        assertThat(ilimapText).contains("bag Textposition");
        assertThat(ilimapText).contains("ref Entstehung");
        assertThat(ilimapText).contains("enum Zuverlaessigkeit_DM01_DMAV");

        assertThatCode(() -> new IlimapParser(ilimapText).parseDocument()).doesNotThrowAnyException();
    }

    @Test
    void convertsYamlWithBagEmbed() throws Exception {
        String ilimapText = converter.convert(Path.of("src/test/resources/mappings/bag-embed-test.yaml"));

        assertThat(ilimapText).isNotBlank();
        assertThat(ilimapText).contains("bag Positions");
        assertThat(ilimapText).contains("parentRef attribute");

        assertThatCode(() -> new IlimapParser(ilimapText).parseDocument()).doesNotThrowAnyException();
    }

    @Test
    void denormalizesEnumMapInConvertedOutput() throws Exception {
        JobConfig config = new JobConfig();
        config.version = 1;
        config.job = new JobConfig.JobSection();
        config.job.name = "enum-test";
        config.job.inputs = new ArrayList<>();
        config.job.outputs = new ArrayList<>();
        config.mapping = new JobConfig.MappingSection();
        config.mapping.rules = new ArrayList<>();

        JobConfig.InputSpec input = new JobConfig.InputSpec();
        input.id = "src";
        input.path = "in.xtf";
        input.model = "M";
        config.job.inputs.add(input);

        JobConfig.OutputSpec output = new JobConfig.OutputSpec();
        output.id = "tgt";
        output.path = "out.xtf";
        output.model = "M";
        config.job.outputs.add(output);

        config.mapping.enums = new LinkedHashMap<>();
        config.mapping.enums.put("StatusMap", new LinkedHashMap<>());
        config.mapping.enums.get("StatusMap").put("a", "b");

        JobConfig.RuleSpec rule = new JobConfig.RuleSpec();
        rule.id = "r1";
        rule.target = new JobConfig.TargetSpec();
        rule.target.output = "tgt";
        rule.target.clazz = "M.T.C";
        rule.sources = new ArrayList<>();
        JobConfig.SourceSpec source = new JobConfig.SourceSpec();
        source.alias = "s";
        source.inputs = java.util.List.of("src");
        source.clazz = "M.T.C";
        rule.sources.add(source);
        rule.assign = new LinkedHashMap<>();
        rule.assign.put("Status", "enumMap(s.Type, \"StatusMap\")");
        config.mapping.rules.add(rule);

        String ilimapText = converter.convert(config);

        assertThat(ilimapText).contains("enumMap(s.Type, StatusMap)");
        assertThat(ilimapText).doesNotContain("enumMap(s.Type, \"StatusMap\")");
    }

    @Test
    void converterProducesValidIlimapFromJobConfig() {
        JobConfig config = new JobConfig();
        config.version = 1;
        config.job = new JobConfig.JobSection();
        config.job.name = "simple";
        config.job.inputs = new ArrayList<>();
        config.job.outputs = new ArrayList<>();
        config.mapping = new JobConfig.MappingSection();
        config.mapping.rules = new ArrayList<>();

        JobConfig.InputSpec input = new JobConfig.InputSpec();
        input.id = "src";
        input.path = "in.xtf";
        input.model = "M";
        config.job.inputs.add(input);

        JobConfig.OutputSpec output = new JobConfig.OutputSpec();
        output.id = "tgt";
        output.path = "out.xtf";
        output.model = "M";
        config.job.outputs.add(output);

        JobConfig.RuleSpec rule = new JobConfig.RuleSpec();
        rule.id = "r1";
        rule.target = new JobConfig.TargetSpec();
        rule.target.output = "tgt";
        rule.target.clazz = "M.T.C";
        rule.sources = new ArrayList<>();
        JobConfig.SourceSpec source = new JobConfig.SourceSpec();
        source.alias = "s";
        source.inputs = java.util.List.of("src");
        source.clazz = "M.T.C";
        rule.sources.add(source);
        rule.assign = new LinkedHashMap<>();
        rule.assign.put("X", "s.X");
        config.mapping.rules.add(rule);

        String result = converter.convert(config);

        assertThat(result).startsWith("mapping v2");
        assertThat(result).contains("input src");
        assertThat(result).contains("output tgt");
        assertThat(result).contains("rule r1");
    }
}
