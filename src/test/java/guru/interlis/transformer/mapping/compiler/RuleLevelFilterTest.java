package guru.interlis.transformer.mapping.compiler;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.TypeSystemFacade;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RuleLevelFilterTest {

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
    void ruleLevelFilterIsCompiled() {
        JobConfig config = minimalConfig();
        config.mapping.rules.get(0).where = "${s.Aktiv} == true";

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.rules()).hasSize(1);
        assertThat(plan.rules().get(0).predicate()).isNotNull();
        assertThat(plan.rules().get(0).predicate().resultType()).isEqualTo(TypeInfo.BOOLEAN);
    }

    @Test
    void ruleLevelFilterSyntaxErrorProducesDiagnostic() {
        JobConfig config = minimalConfig();
        config.mapping.rules.get(0).where = "${s.Aktiv} =="; // syntax error

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.diagnostics().all()).anyMatch(d -> d.code().equals(DiagnosticCode.EXPR_SYNTAX));
    }

    @Test
    void noWhereClauseMeansNullPredicate() {
        JobConfig config = minimalConfig();

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.rules()).hasSize(1);
        assertThat(plan.rules().get(0).predicate()).isNull();
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
