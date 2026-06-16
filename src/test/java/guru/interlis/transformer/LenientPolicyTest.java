package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.engine.TransformResult;
import guru.interlis.transformer.engine.TransformationEngine;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.FailPolicy;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.InMemoryStateStore;

import ch.interlis.iom_j.Iom_jObject;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LenientPolicyTest {

    private static final String MODELDIR = "src/test/data/models/";
    private static TypeSystemFacade modelTs;

    @BeforeAll
    static void compileModels() {
        IliModelService service = new IliModelService();
        IliModelCompileResult result = service.compileModel("src/test/data/models/minimal.ili", MODELDIR);
        assertThat(result.hasErrors())
                .as("Model compilation errors: %s", result.diagnostics())
                .isFalse();
        modelTs = new TypeSystemFacade(result.transferDescription());
    }

    @Test
    void lenientPolicyIsEnabledInPlan(@TempDir Path tempDir) throws Exception {
        JobConfig config = new JobConfig();
        config.version = 1;
        config.job.failPolicy = "lenient";

        JobConfig.InputSpec in = new JobConfig.InputSpec();
        in.id = "in1";
        in.path = "input.xtf";
        in.model = "TestModel";
        config.job.inputs.add(in);

        JobConfig.OutputSpec out = new JobConfig.OutputSpec();
        out.id = "out1";
        out.path = tempDir.resolve("output.xtf").toString();
        out.model = "TestModel";
        config.job.outputs.add(out);

        JobConfig.RuleSpec rule = new JobConfig.RuleSpec();
        rule.id = "rule1";
        JobConfig.TargetSpec tgt = new JobConfig.TargetSpec();
        tgt.output = "out1";
        tgt.clazz = "TestModel.TestTopic.TestClass";
        rule.target = tgt;
        JobConfig.SourceSpec src = new JobConfig.SourceSpec();
        src.alias = "s";
        src.input = "in1";
        src.clazz = "TestModel.TestTopic.TestClass";
        rule.sources.add(src);
        rule.assign = Map.of("Name", "${s.Name}");
        config.mapping.rules.add(rule);

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", modelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.diagnostics().hasErrors()).isFalse();
        assertThat(plan.failPolicy()).isEqualTo(FailPolicy.LENIENT);
    }

    @Test
    void lenientProducesOutputOnSuccess(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("output.xtf");
        JobConfig config = buildConfig(output.toString());
        Map<String, TypeSystemFacade> ts = Map.of("TestModel", modelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject src1 = new Iom_jObject("TestModel.TestTopic.TestClass", "1");
        src1.setattrvalue("Name", "LenientSuccess");

        DiagnosticCollector engineDiag = new DiagnosticCollector();
        InterlisIoFactory ioFactory = new InterlisIoFactory();
        var writer = ioFactory.createWriter(output, modelTs.getTransferDescription(), engineDiag);

        TransformationEngine engine =
                new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), engineDiag);
        TransformResult result = engine.runTyped(plan, id -> TestMockReaders.mockReader(src1), Map.of("out1", writer));

        assertThat(engineDiag.hasErrors()).isFalse();
        assertThat(result.targetsWritten()).isEqualTo(1);
        assertThat(output).exists();
    }

    private static JobConfig buildConfig(String outputPath) {
        JobConfig config = new JobConfig();
        config.version = 1;
        config.job.failPolicy = "lenient";

        JobConfig.InputSpec in = new JobConfig.InputSpec();
        in.id = "in1";
        in.path = "input.xtf";
        in.model = "TestModel";
        config.job.inputs.add(in);

        JobConfig.OutputSpec out = new JobConfig.OutputSpec();
        out.id = "out1";
        out.path = outputPath;
        out.model = "TestModel";
        config.job.outputs.add(out);

        JobConfig.RuleSpec rule = new JobConfig.RuleSpec();
        rule.id = "rule1";
        JobConfig.TargetSpec tgt = new JobConfig.TargetSpec();
        tgt.output = "out1";
        tgt.clazz = "TestModel.TestTopic.TestClass";
        rule.target = tgt;
        JobConfig.SourceSpec src = new JobConfig.SourceSpec();
        src.alias = "s";
        src.input = "in1";
        src.clazz = "TestModel.TestTopic.TestClass";
        rule.sources.add(src);
        rule.assign = Map.of("Name", "${s.Name}");
        config.mapping.rules.add(rule);

        return config;
    }
}
