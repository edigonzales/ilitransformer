package guru.interlis.transformer.mapping.compiler;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.TypeSystemFacade;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JoinCompilationTest {

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
    void validInnerJoinIsCompiled() {
        JobConfig config = minimalConfigWithTwoSources();
        JobConfig.JoinSpec join = new JobConfig.JoinSpec();
        join.left = "s";
        join.right = "s2";
        join.on = "${s.Name} == ${s2.Name}";
        join.type = "inner";
        config.mapping.rules.get(0).joins = List.of(join);

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.diagnostics().all()).noneMatch(d -> d.code().equals(DiagnosticCode.MAP_UNSUPPORTED_FEATURE));
        assertThat(plan.rules().get(0).joins()).isNotEmpty();
        assertThat(plan.rules().get(0).joins().get(0).type().name()).isEqualTo("INNER");
    }

    @Test
    void validLeftJoinIsCompiled() {
        JobConfig config = minimalConfigWithTwoSources();
        JobConfig.JoinSpec join = new JobConfig.JoinSpec();
        join.left = "s";
        join.right = "s2";
        join.on = "${s.Name} == ${s2.Name}";
        join.type = "left";
        config.mapping.rules.get(0).joins = List.of(join);

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.rules().get(0).joins().get(0).type().name()).isEqualTo("LEFT");
    }

    @Test
    void nonEquiJoinIsDiagnosed() {
        JobConfig config = minimalConfigWithTwoSources();
        JobConfig.JoinSpec join = new JobConfig.JoinSpec();
        join.left = "s";
        join.right = "s2";
        join.on = "${s.Name}"; // not an equality
        join.type = "inner";
        config.mapping.rules.get(0).joins = List.of(join);

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.diagnostics().all())
                .anyMatch(d -> d.code().equals(DiagnosticCode.MAP_JOIN_NON_EQUI) && d.severity() == Severity.ERROR);
    }

    @Test
    void unknownAliasIsDiagnosed() {
        JobConfig config = minimalConfig();
        JobConfig.JoinSpec join = new JobConfig.JoinSpec();
        join.left = "s";
        join.right = "unknown";
        join.on = "${s.Name} == ${unknown.Name}";
        join.type = "inner";
        config.mapping.rules.get(0).joins = List.of(join);

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.diagnostics().all())
                .anyMatch(
                        d -> d.code().equals(DiagnosticCode.MAP_JOIN_UNKNOWN_ALIAS) && d.severity() == Severity.ERROR);
    }

    @Test
    void selfJoinIsDiagnosed() {
        JobConfig config = minimalConfig();
        JobConfig.JoinSpec join = new JobConfig.JoinSpec();
        join.left = "s";
        join.right = "s"; // same alias
        join.on = "${s.Name} == ${s.Name}";
        join.type = "inner";
        config.mapping.rules.get(0).joins = List.of(join);

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.diagnostics().all())
                .anyMatch(d -> d.code().equals(DiagnosticCode.MAP_JOIN_SELF_REF) && d.severity() == Severity.ERROR);
    }

    @Test
    void invalidJoinTypeIsDiagnosed() {
        JobConfig config = minimalConfigWithTwoSources();
        JobConfig.JoinSpec join = new JobConfig.JoinSpec();
        join.left = "s";
        join.right = "s2";
        join.on = "${s.Name} == ${s2.Name}";
        join.type = "outer";
        config.mapping.rules.get(0).joins = List.of(join);

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.diagnostics().all())
                .anyMatch(d -> d.code().equals(DiagnosticCode.MAP_JOIN_INVALID)
                        && d.severity() == Severity.ERROR
                        && d.message().contains("outer"));
    }

    @Test
    void emptyJoinsListOk() {
        JobConfig config = minimalConfig();
        config.mapping.rules.get(0).joins = List.of();

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.diagnostics().all()).noneMatch(d -> d.code().equals(DiagnosticCode.MAP_UNSUPPORTED_FEATURE));
    }

    @Test
    void nullJoinsOk() {
        JobConfig config = minimalConfig();

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.diagnostics().all()).noneMatch(d -> d.code().equals(DiagnosticCode.MAP_UNSUPPORTED_FEATURE));
    }

    @Test
    void compilesMultipleJoinsInOrder() {
        JobConfig config = minimalConfigWithTwoSources();

        JobConfig.JoinSpec join1 = new JobConfig.JoinSpec();
        join1.left = "s";
        join1.right = "s2";
        join1.on = "${s.Name} == ${s2.Name}";
        join1.type = "inner";

        JobConfig.JoinSpec join2 = new JobConfig.JoinSpec();
        join2.left = "s";
        join2.right = "s2";
        join2.on = "${s.Beschreibung} == ${s2.Beschreibung}";
        join2.type = "inner";

        config.mapping.rules.get(0).joins = List.of(join1, join2);

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.diagnostics().all()).noneMatch(d -> d.code().equals(DiagnosticCode.MAP_UNSUPPORTED_FEATURE));
        assertThat(plan.rules().get(0).joins()).hasSize(2);
        assertThat(plan.rules().get(0).joins().get(0).left().alias()).isEqualTo("s");
        assertThat(plan.rules().get(0).joins().get(1).right().alias()).isEqualTo("s2");
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

    private static JobConfig minimalConfigWithTwoSources() {
        JobConfig config = minimalConfig();

        JobConfig.SourceSpec src2 = new JobConfig.SourceSpec();
        src2.alias = "s2";
        src2.clazz = "TestModel.TestTopic.TestClass";
        src2.inputs = List.of("in1");
        config.mapping.rules.get(0).sources.add(src2);

        return config;
    }
}
