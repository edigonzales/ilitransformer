package guru.interlis.transformer.mapping.compiler;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.TypeSystemFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class UnsupportedCreateRejectedTest {

    private static final String MODELDIR = "src/test/data/models/";
    private static TypeSystemFacade testModelTs;

    @BeforeAll
    static void compileModels() {
        IliModelService service = new IliModelService();
        var result = service.compileModel("src/test/data/models/minimal.ili", MODELDIR);
        assertThat(result.hasErrors()).isFalse();
        testModelTs = new TypeSystemFacade(result.transferDescription());
    }

    @Test
    void createIsRejected() {
        JobConfig config = minimalConfig();
        JobConfig.CreateSpec create = new JobConfig.CreateSpec();
        create.clazz = "TestModel.TestTopic.TestClass";
        create.assign = new LinkedHashMap<>();
        create.assign.put("Name", "${s.Name}");
        config.mapping.rules.get(0).create = List.of(create);

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.diagnostics().all()).anyMatch(d ->
                d.code().equals(DiagnosticCode.MAP_UNSUPPORTED_FEATURE)
                        && d.severity() == Severity.ERROR
                        && d.message().contains("Create"));
    }

    @Test
    void emptyCreateListNotRejected() {
        JobConfig config = minimalConfig();
        config.mapping.rules.get(0).create = List.of();

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.diagnostics().all()).noneMatch(d ->
                d.code().equals(DiagnosticCode.MAP_UNSUPPORTED_FEATURE));
    }

    @Test
    void nullCreateNotRejected() {
        JobConfig config = minimalConfig();

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.diagnostics().all()).noneMatch(d ->
                d.code().equals(DiagnosticCode.MAP_UNSUPPORTED_FEATURE));
    }

    private static JobConfig minimalConfig() {
        JobConfig config = new JobConfig();
        config.version = 1;

        JobConfig.InputSpec in = new JobConfig.InputSpec();
        in.id = "in1";
        in.model = "TestModel";
        in.path = "in.xtf";
        config.job.inputs.add(in);

        JobConfig.OutputSpec out = new JobConfig.OutputSpec();
        out.id = "out1";
        out.model = "TestModel";
        out.path = "out.xtf";
        config.job.outputs.add(out);

        JobConfig.RuleSpec rule = new JobConfig.RuleSpec();
        rule.id = "rule1";
        rule.target = new JobConfig.TargetSpec();
        rule.target.clazz = "TestModel.TestTopic.TestClass";
        rule.target.output = "out1";

        JobConfig.SourceSpec src = new JobConfig.SourceSpec();
        src.alias = "s";
        src.clazz = "TestModel.TestTopic.TestClass";
        src.inputs = List.of("in1");
        rule.sources.add(src);

        rule.assign = new java.util.LinkedHashMap<>();
        rule.assign.put("Name", "${s.Name}");
        rule.assign.put("Beschreibung", "${s.Beschreibung}");

        config.mapping.rules.add(rule);
        return config;
    }
}
