package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.engine.RuleMetricsSnapshot;
import guru.interlis.transformer.engine.TransformResult;
import guru.interlis.transformer.engine.TransformationEngine;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.mapping.compiler.MappingCompiler;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.InMemoryStateStore;

import ch.interlis.iom_j.Iom_jObject;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TransformationProfilingTest {

    private static final String MODELDIR = "src/test/data/models/";
    private static TypeSystemFacade modelTs;

    @BeforeAll
    static void compileModels() {
        IliModelService service = new IliModelService();
        var result = service.compileModel("src/test/data/models/minimal.ili", MODELDIR);
        assertThat(result.hasErrors())
                .as("Model compilation errors: %s", result.diagnostics().all())
                .isFalse();
        modelTs = new TypeSystemFacade(result.transferDescription());
    }

    @Test
    void ruleMetricsAreCapturedForCopyAndJoinRules(@TempDir Path tempDir) throws Exception {
        TransformPlan plan = new MappingCompiler()
                .compileTyped(
                        config(tempDir.resolve("out.xtf").toString()),
                        Map.of("TestModel", modelTs),
                        Map.of("TestModel", modelTs));
        assertThat(plan.diagnostics().hasErrors()).isFalse();

        Iom_jObject first = source("1", "A", "B");
        Iom_jObject second = source("2", "B", "none");

        DiagnosticCollector diagnostics = new DiagnosticCollector();
        var writer = new InterlisIoFactory()
                .createWriter(tempDir.resolve("out.xtf"), modelTs.getTransferDescription(), diagnostics);
        TransformationEngine engine =
                new TransformationEngine(new ExpressionEngine(), new InMemoryStateStore(), diagnostics);

        TransformResult result =
                engine.runTyped(plan, inputId -> TestMockReaders.mockReader(first, second), Map.of("out1", writer));

        assertThat(diagnostics.hasErrors()).isFalse();
        assertThat(result.targetsCreated()).isEqualTo(3);
        assertThat(result.targetsWritten()).isEqualTo(3);

        Map<String, RuleMetricsSnapshot> rules = engine.getMetricsSnapshot().rules().stream()
                .collect(Collectors.toMap(RuleMetricsSnapshot::ruleId, Function.identity()));
        assertThat(rules).containsKeys("copy-rule", "join-rule");
        assertThat(rules.get("copy-rule").sourceRecordsVisited()).isEqualTo(2);
        assertThat(rules.get("copy-rule").matches()).isEqualTo(2);
        assertThat(rules.get("copy-rule").filtered()).isZero();
        assertThat(rules.get("copy-rule").targetsCreated()).isEqualTo(2);
        assertThat(rules.get("copy-rule").elapsedMillis()).isGreaterThanOrEqualTo(0);
        assertThat(rules.get("join-rule").sourceRecordsVisited()).isEqualTo(2);
        assertThat(rules.get("join-rule").matches()).isEqualTo(1);
        assertThat(rules.get("join-rule").filtered()).isEqualTo(1);
        assertThat(rules.get("join-rule").targetsCreated()).isEqualTo(1);
        assertThat(rules.get("join-rule").elapsedMillis()).isGreaterThanOrEqualTo(0);
    }

    private static Iom_jObject source(String oid, String name, String description) {
        Iom_jObject object = new Iom_jObject("TestModel.TestTopic.TestClass", oid);
        object.setattrvalue("Name", name);
        object.setattrvalue("Beschreibung", description);
        return object;
    }

    private static JobConfig config(String outputPath) {
        JobConfig config = new JobConfig();
        config.version = 1;

        JobConfig.InputSpec in = new JobConfig.InputSpec();
        in.id = "in1";
        in.path = "in.xtf";
        in.model = "TestModel";
        config.job.inputs.add(in);

        JobConfig.OutputSpec out = new JobConfig.OutputSpec();
        out.id = "out1";
        out.path = outputPath;
        out.model = "TestModel";
        config.job.outputs.add(out);

        config.mapping.rules.add(rule("copy-rule", List.of(sourceSpec("s")), Map.of("Name", "${s.Name}"), null));

        JobConfig.JoinSpec join = new JobConfig.JoinSpec();
        join.left = "s";
        join.right = "s2";
        join.on = "${s.Beschreibung} == ${s2.Name}";
        join.type = "inner";
        config.mapping.rules.add(
                rule("join-rule", List.of(sourceSpec("s"), sourceSpec("s2")), Map.of("Name", "${s.Name}"), join));

        return config;
    }

    private static JobConfig.RuleSpec rule(
            String id, List<JobConfig.SourceSpec> sources, Map<String, String> assign, JobConfig.JoinSpec join) {
        JobConfig.RuleSpec rule = new JobConfig.RuleSpec();
        rule.id = id;
        rule.target = new JobConfig.TargetSpec();
        rule.target.output = "out1";
        rule.target.clazz = "TestModel.TestTopic.TestClass";
        rule.sources.addAll(sources);
        rule.assign = assign;
        if (join != null) {
            rule.joins = List.of(join);
        }
        return rule;
    }

    private static JobConfig.SourceSpec sourceSpec(String alias) {
        JobConfig.SourceSpec source = new JobConfig.SourceSpec();
        source.alias = alias;
        source.inputs = List.of("in1");
        source.clazz = "TestModel.TestTopic.TestClass";
        return source;
    }
}
