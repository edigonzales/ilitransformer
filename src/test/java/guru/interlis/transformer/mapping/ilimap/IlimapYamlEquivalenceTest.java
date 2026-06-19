package guru.interlis.transformer.mapping.ilimap;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.model.MappingLoader;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class IlimapYamlEquivalenceTest {

    @Test
    void yamlAndIlimapProduceEquivalentJobConfig() throws Exception {
        MappingLoader yamlLoader = new MappingLoader();
        JobConfig yamlConfig = yamlLoader.load(Path.of("src/test/resources/mapping/equivalence/minimal.yaml"));

        IlimapLoader ilimapLoader = new IlimapLoader();
        JobConfig ilimapConfig = ilimapLoader.load(Path.of("src/test/resources/mapping/equivalence/minimal.ilimap"));

        assertThat(ilimapConfig.version).isEqualTo(yamlConfig.version);

        assertThat(ilimapConfig.job.name).isEqualTo(yamlConfig.job.name);
        assertThat(ilimapConfig.job.description).isEqualTo(yamlConfig.job.description);
        assertThat(ilimapConfig.job.direction).isEqualTo(yamlConfig.job.direction);
        assertThat(ilimapConfig.job.failPolicy).isEqualTo(yamlConfig.job.failPolicy);

        assertThat(ilimapConfig.mapping.compileMode).isEqualTo(yamlConfig.mapping.compileMode);

        assertThat(ilimapConfig.mapping.oidStrategy.defaultStrategy)
                .isEqualTo(yamlConfig.mapping.oidStrategy.defaultStrategy);
        assertThat(ilimapConfig.mapping.basketStrategy.defaultStrategy)
                .isEqualTo(yamlConfig.mapping.basketStrategy.defaultStrategy);

        assertThat(ilimapConfig.job.inputs).hasSize(yamlConfig.job.inputs.size());
        assertThat(ilimapConfig.job.inputs.get(0).id).isEqualTo(yamlConfig.job.inputs.get(0).id);
        assertThat(ilimapConfig.job.inputs.get(0).path).isEqualTo(yamlConfig.job.inputs.get(0).path);
        assertThat(ilimapConfig.job.inputs.get(0).model).isEqualTo(yamlConfig.job.inputs.get(0).model);

        assertThat(ilimapConfig.job.outputs).hasSize(yamlConfig.job.outputs.size());
        assertThat(ilimapConfig.job.outputs.get(0).id).isEqualTo(yamlConfig.job.outputs.get(0).id);

        assertThat(ilimapConfig.mapping.enums).isEqualTo(yamlConfig.mapping.enums);

        assertThat(ilimapConfig.mapping.rules).hasSize(yamlConfig.mapping.rules.size());
        JobConfig.RuleSpec ilimapRule = ilimapConfig.mapping.rules.get(0);
        JobConfig.RuleSpec yamlRule = yamlConfig.mapping.rules.get(0);
        assertThat(ilimapRule.id).isEqualTo(yamlRule.id);
        assertThat(ilimapRule.target.output).isEqualTo(yamlRule.target.output);
        assertThat(ilimapRule.target.clazz).isEqualTo(yamlRule.target.clazz);
        assertThat(ilimapRule.sources).hasSize(yamlRule.sources.size());
        assertThat(ilimapRule.sources.get(0).alias).isEqualTo(yamlRule.sources.get(0).alias);
        assertThat(ilimapRule.sources.get(0).clazz).isEqualTo(yamlRule.sources.get(0).clazz);
        assertThat(ilimapRule.where).isEqualTo(yamlRule.where);
        assertThat(ilimapRule.identity.sourceKey).isEqualTo(yamlRule.identity.sourceKey);
        assertThat(ilimapRule.assign).isEqualTo(yamlRule.assign);
    }

    @Test
    void yamlAndIlimapBothCompileWithMappingCompiler() throws Exception {
        MappingLoader yamlLoader = new MappingLoader();
        JobConfig yamlConfig = yamlLoader.load(Path.of("src/test/resources/mapping/equivalence/minimal.yaml"));

        IlimapLoader ilimapLoader = new IlimapLoader();
        JobConfig ilimapConfig = ilimapLoader.load(Path.of("src/test/resources/mapping/equivalence/minimal.ilimap"));

        MappingCompiler compiler = new MappingCompiler();
        var yamlResult = compiler.compile(yamlConfig);
        var ilimapResult = compiler.compile(ilimapConfig);

        assertThat(yamlResult.diagnostics().hasErrors())
                .as("YAML config should compile without errors")
                .isFalse();
        assertThat(ilimapResult.diagnostics().hasErrors())
                .as("ILIMAP config should compile without errors")
                .isFalse();
    }
}
