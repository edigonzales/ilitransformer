package guru.interlis.transformer.mapping.compiler;

import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.CompileMode;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.TypeSystemFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class CompileModeTest {

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
    void strictModeUpgradesWarningsToErrors() {
        JobConfig config = minimalConfig();
        config.mapping.compileMode = "strict";
        // Cause a type mismatch (WARNING by default)
        var rule = config.mapping.rules.get(0);
        rule.assign = new java.util.LinkedHashMap<>();
        rule.assign.put("Aktiv", "${s.Beschreibung}"); // TEXT->BOOLEAN

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.compileMode()).isEqualTo(CompileMode.STRICT);
        // In STRICT mode, the type mismatch WARNING should be upgraded to ERROR
        // Since we add a duplicate with ERROR while keeping the original WARNING,
        // we should find at least one ERROR diagnostic
        assertThat(plan.diagnostics().all()).anyMatch(d ->
                d.severity() == Severity.ERROR
                        && d.message().contains("Beschreibung"));
    }

    @Test
    void compatibleModeKeepsWarningsAsWarnings() {
        JobConfig config = minimalConfig();
        config.mapping.compileMode = "compatible";
        var rule = config.mapping.rules.get(0);
        rule.assign = new java.util.LinkedHashMap<>();
        rule.assign.put("Aktiv", "${s.Beschreibung}"); // TEXT->BOOLEAN

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.compileMode()).isEqualTo(CompileMode.COMPATIBLE);
        assertThat(plan.diagnostics().hasErrors()).isFalse();
    }

    @Test
    void reportModeMarksPlanAsReportOnly() {
        JobConfig config = minimalConfig();
        config.mapping.compileMode = "report";

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.compileMode()).isEqualTo(CompileMode.REPORT);
        assertThat(plan.isReportOnly()).isTrue();
    }

    @Test
    void strictModeIsDefault() {
        JobConfig config = minimalConfig();
        // Default compileMode is "strict"

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.compileMode()).isEqualTo(CompileMode.STRICT);
    }

    @Test
    void unknownCompileModeFallsBackToStrictWithWarning() {
        JobConfig config = minimalConfig();
        config.mapping.compileMode = "unknown";

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.compileMode()).isEqualTo(CompileMode.STRICT);
        assertThat(plan.diagnostics().warnings()).isGreaterThan(0);
    }

    @Test
    void allowTodosFallsBackToStrictWithWarning() {
        JobConfig config = minimalConfig();
        config.mapping.compileMode = "allowTodos";

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.compileMode()).isEqualTo(CompileMode.STRICT);
        assertThat(plan.diagnostics().all()).anyMatch(d ->
                d.severity() == Severity.WARNING
                        && d.message().contains("Unknown compileMode")
                        && d.message().contains("allowTodos"));
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
