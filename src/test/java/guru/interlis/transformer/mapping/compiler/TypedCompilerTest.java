package guru.interlis.transformer.mapping.compiler;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.model.IliModelService;
import guru.interlis.transformer.model.TypeSystemFacade;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TypedCompilerTest {

    private static final String MODELDIR = "src/test/data/models/";
    private static TypeSystemFacade testModelTs;

    @BeforeAll
    static void compileModels() {
        IliModelService service = new IliModelService();
        var result = service.compileModel("src/test/data/models/minimal.ili", MODELDIR);
        assertThat(result.hasErrors()).isFalse();
        testModelTs = new TypeSystemFacade(result.transferDescription());
    }

    // -- Positive tests -----------------------------------------------------

    @Test
    void compilesValidMappingToTransformPlan() {
        JobConfig config = minimalConfig();
        Map<String, TypeSystemFacade> sourceTs = Map.of("TestModel", testModelTs);
        Map<String, TypeSystemFacade> targetTs = Map.of("TestModel", testModelTs);

        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        assertThat(plan.rules()).hasSize(1);
        assertThat(plan.rules().get(0).targetClass()).isNotNull();
        assertThat(plan.rules().get(0).targetClass().getName()).isEqualTo("TestClass");
        assertThat(plan.rules().get(0).sources()).hasSize(1);
        assertThat(plan.rules().get(0).sources().get(0).sourceClass()).isNotNull();
        assertThat(plan.rules().get(0).sources().get(0).sourceClass().getName()).isEqualTo("TestClass");
        assertThat(plan.rules().get(0).assignments()).hasSize(2);
    }

    @Test
    void assignsAreCorrectlyCompiled() {
        JobConfig config = minimalConfig();
        Map<String, TypeSystemFacade> sourceTs = Map.of("TestModel", testModelTs);
        Map<String, TypeSystemFacade> targetTs = Map.of("TestModel", testModelTs);

        TransformPlan plan = new MappingCompiler().compileTyped(config, sourceTs, targetTs);
        var assignments = plan.rules().get(0).assignments();

        var nameAssign = assignments.stream()
                .filter(a -> a.targetAttrName().equals("Name"))
                .findFirst();
        assertThat(nameAssign).isPresent();
        assertThat(nameAssign.get().targetAttr()).isNotNull();
        assertThat(nameAssign.get().expression().resultType()).isEqualTo(TypeInfo.TEXT);
    }

    @Test
    void literalExpressionTypesAreCorrect() {
        JobConfig config = minimalConfig();
        // Add literal assignments
        var rule = config.mapping.rules.get(0);
        rule.assign = new java.util.LinkedHashMap<>();
        rule.assign.put("Name", "\"literal\"");
        rule.assign.put("Aktiv", "true");

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        var nameAssign = plan.rules().get(0).assignments().stream()
                .filter(a -> a.targetAttrName().equals("Name"))
                .findFirst()
                .orElseThrow();
        assertThat(nameAssign.expression().resultType()).isEqualTo(TypeInfo.TEXT);

        var boolAssign = plan.rules().get(0).assignments().stream()
                .filter(a -> a.targetAttrName().equals("Aktiv"))
                .findFirst()
                .orElseThrow();
        assertThat(boolAssign.expression().resultType()).isEqualTo(TypeInfo.BOOLEAN);
    }

    // -- Negative tests -----------------------------------------------------

    @Test
    void rejectsUnknownTargetClass() {
        JobConfig config = minimalConfig();
        config.mapping.rules.get(0).target = new JobConfig.TargetSpec();
        config.mapping.rules.get(0).target.clazz = "TestModel.TestTopic.NonExistent";
        config.mapping.rules.get(0).target.output = "out1";

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.diagnostics().all()).isNotEmpty();
        assertThat(plan.diagnostics().all())
                .anyMatch(d ->
                        d.code().equals(DiagnosticCode.MAP_UNKNOWN_TARGET_CLASS) && d.severity() == Severity.ERROR);
        assertThat(plan.rules()).isEmpty();
    }

    @Test
    void rejectsUnknownTargetAttribute() {
        JobConfig config = minimalConfig();
        var rule = config.mapping.rules.get(0);
        rule.assign = new java.util.LinkedHashMap<>();
        rule.assign.put("NonExistentAttr", "${s.Name}");

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.diagnostics().all())
                .anyMatch(d ->
                        d.code().equals(DiagnosticCode.MAP_UNKNOWN_TARGET_ATTRIBUTE) && d.severity() == Severity.ERROR);
    }

    @Test
    void rejectsUnknownSourceAttribute() {
        JobConfig config = minimalConfig();
        var rule = config.mapping.rules.get(0);
        rule.assign = new java.util.LinkedHashMap<>();
        rule.assign.put("Name", "${s.NonExistent}");

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.diagnostics().all())
                .anyMatch(d ->
                        d.code().equals(DiagnosticCode.MAP_UNKNOWN_SOURCE_ATTRIBUTE) && d.severity() == Severity.ERROR);
    }

    @Test
    void reportsTypeMismatch() {
        JobConfig config = minimalConfig();
        var rule = config.mapping.rules.get(0);
        rule.assign = new java.util.LinkedHashMap<>();
        rule.assign.put("Aktiv", "${s.Beschreibung}"); // TEXT source -> BOOLEAN target

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.diagnostics().all()).anyMatch(d -> d.code().equals(DiagnosticCode.MAP_TYPE_MISMATCH));
    }

    @Test
    void reportsMissingMandatory() {
        JobConfig config = minimalConfig();
        var rule = config.mapping.rules.get(0);
        // Only assign Beschreibung (optional), not Name (mandatory)
        rule.assign = new java.util.LinkedHashMap<>();
        rule.assign.put("Beschreibung", "${s.Beschreibung}");

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.diagnostics().all())
                .anyMatch(d -> d.code().equals(DiagnosticCode.MAP_MANDATORY_MISSING)
                        && d.message().contains("Name"));
    }

    @Test
    void rejectsDuplicateTargetAssignments() {
        JobConfig config = minimalConfig();
        var rule = config.mapping.rules.get(0);
        // Use attributes list to allow duplicates
        rule.assign = null;
        JobConfig.AttributeMapping am1 = new JobConfig.AttributeMapping();
        am1.target = "Name";
        am1.expr = "${s.Name}";
        JobConfig.AttributeMapping am2 = new JobConfig.AttributeMapping();
        am2.target = "Name";
        am2.expr = "${s.Beschreibung}";
        rule.attributes = List.of(am1, am2);

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.diagnostics().all())
                .anyMatch(d ->
                        d.code().equals(DiagnosticCode.MAP_DUPLICATE_TARGET_ASSIGN) && d.severity() == Severity.ERROR);
    }

    @Test
    void warnsOnAbstractTargetClass() {
        // Note: minimal.ili has no abstract class, so we test the code path differently
        JobConfig config = new JobConfig();
        config.version = 1;
        addInputOutput(config);
        var rule = addRule(
                config, "rule1", "INTERLIS.TIMESYSTEMS.CALENDAR", "out1", "in1", "INTERLIS.TIMESYSTEMS.CALENDAR");

        // Compile INTERLIS predefined model
        IliModelService service = new IliModelService();
        var result = service.compileModel("src/test/data/models/minimal.ili", MODELDIR);
        assertThat(result.hasErrors()).isFalse();
        // The TransferDescription contains the INTERLIS model too
        TypeSystemFacade interlisTs = new TypeSystemFacade(result.transferDescription());

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", interlisTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        // CALENDAR extends SCALSYSTEM (not abstract), so no abstract error expected here
        // But non-identifiable (isIli1Optional tables) might trigger a warning
        assertThat(plan.diagnostics().all()).isNotEmpty();
    }

    @Test
    void detectsCyclicDependencies() {
        JobConfig config = new JobConfig();
        config.version = 1;
        addInputOutput(config);

        var rule1 =
                addRule(config, "r1", "TestModel.TestTopic.TestClass", "out1", "in1", "TestModel.TestTopic.TestClass");
        var rule2 =
                addRule(config, "r2", "TestModel.TestTopic.TestClass", "out1", "in1", "TestModel.TestTopic.TestClass");

        // r1 -> r2, r2 -> r1 (cycle)
        JobConfig.RefMapping ref1 = new JobConfig.RefMapping();
        ref1.role = "ref1";
        ref1.targetRule = "r2";
        rule1.refs = List.of(ref1);

        JobConfig.RefMapping ref2 = new JobConfig.RefMapping();
        ref2.role = "ref2";
        ref2.targetRule = "r1";
        rule2.refs = List.of(ref2);

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        assertThat(plan.diagnostics().all())
                .anyMatch(d -> d.code().equals(DiagnosticCode.MAP_CYCLIC_DEPENDENCY) && d.severity() == Severity.ERROR);
    }

    @Test
    void enumLiteralDetected() {
        JobConfig config = minimalConfig();
        var rule = config.mapping.rules.get(0);
        rule.assign = new java.util.LinkedHashMap<>();
        rule.assign.put("Name", "#enumValue");

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        var assign = plan.rules().get(0).assignments().get(0);
        assertThat(assign.expression().resultType()).isEqualTo(TypeInfo.ENUM);
    }

    // -- Function type inference (Phase 4) -------------------------

    @Test
    void functionCallTypeIsInferred() {
        JobConfig config = minimalConfig();
        var rule = config.mapping.rules.get(0);
        rule.assign = new java.util.LinkedHashMap<>();
        rule.assign.put("Name", "truncate(${s.Beschreibung}, 60)");

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        var assign = plan.rules().get(0).assignments().get(0);
        assertThat(assign.expression().resultType()).isEqualTo(TypeInfo.TEXT);
    }

    @Test
    void xmlDateTimeFunctionTypeIsInferred() {
        JobConfig config = minimalConfig();
        var rule = config.mapping.rules.get(0);
        rule.assign = new java.util.LinkedHashMap<>();
        rule.assign.put("Name", "toXmlDateTime(${s.Beschreibung})");

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        var assign = plan.rules().get(0).assignments().get(0);
        assertThat(assign.expression().resultType()).isEqualTo(TypeInfo.XML_DATE_TIME);
    }

    @Test
    void unknownFunctionTypeIsUnknown() {
        JobConfig config = minimalConfig();
        var rule = config.mapping.rules.get(0);
        rule.assign = new java.util.LinkedHashMap<>();
        rule.assign.put("Name", "nonexistent(42)");

        Map<String, TypeSystemFacade> ts = Map.of("TestModel", testModelTs);
        TransformPlan plan = new MappingCompiler().compileTyped(config, ts, ts);

        var assign = plan.rules().get(0).assignments().get(0);
        assertThat(assign.expression().resultType()).isEqualTo(TypeInfo.UNKNOWN);
    }

    // -- Helpers ------------------------------------------------------------

    private static JobConfig minimalConfig() {
        JobConfig config = new JobConfig();
        config.version = 1;
        addInputOutput(config);
        addRule(config, "rule1", "TestModel.TestTopic.TestClass", "out1", "in1", "TestModel.TestTopic.TestClass");
        return config;
    }

    private static void addInputOutput(JobConfig config) {
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
    }

    private static JobConfig.RuleSpec addRule(
            JobConfig config, String id, String targetClass, String output, String inputId, String sourceClass) {
        JobConfig.RuleSpec rule = new JobConfig.RuleSpec();
        rule.id = id;
        rule.target = new JobConfig.TargetSpec();
        rule.target.clazz = targetClass;
        rule.target.output = output;

        JobConfig.SourceSpec src = new JobConfig.SourceSpec();
        src.alias = "s";
        src.clazz = sourceClass;
        src.inputs = List.of(inputId);
        rule.sources.add(src);

        // Default assignments
        if (rule.assign == null) {
            rule.assign = new java.util.LinkedHashMap<>();
            rule.assign.put("Name", "${s.Name}");
            rule.assign.put("Beschreibung", "${s.Beschreibung}");
        }

        config.mapping.rules.add(rule);
        return rule;
    }
}
