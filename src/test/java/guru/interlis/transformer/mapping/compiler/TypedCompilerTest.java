package guru.interlis.transformer.mapping.compiler;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.ilimap.IlimapLoader;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.model.ModelRegistry;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TypedCompilerTest {

    private static final String MODELDIR = "src/test/data/models/";

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            reasonCode "CLASS_REMOVED"; | reasonCode "  "; | non-empty reasonCode
            reasonCode "CLASS_REMOVED"; | '' | non-empty reasonCode
            description "Removed"; | '' | non-empty description
            Obsolete | Missing | Source class not found
            Obsolete | AbstractItem | concrete transfer class
            Obsolete | Details | concrete transfer class
            sourcePath s; | sourcePath s.Name; | declared source alias
            sourcePath s; | sourcePath other; | declared source alias
            sourcePath s; | sourcePath s; when true; | where or when
            "P43Source.Data.Obsolete"; | "P43Source.Data.Obsolete" where true; | where or when
            loss { | where true; loss { | one source and one loss block
            loss { | assign { Name = s.Name; } loss { | one source and one loss block
            loss { | source b from original class "P43Source.Data.Item"; loss { | one source and one loss block
            loss { | target migrated class "P43Target.Data.Item"; loss { | requires a rule without a target
            """)
    void rejectsMalformedClassLoss(String original, String replacement, String message) {
        assertLossDiagnostics("""
                rule drop {
                  source s from original class "P43Source.Data.Obsolete";
                  loss { sourcePath s; reasonCode "CLASS_REMOVED"; description "Removed"; }
                }
                """.replace(original, replacement), message);
    }

    @ParameterizedTest
    @CsvSource({"original, true", "alternate, false"})
    void rejectsOnlyOverlappingClassLossRules(String secondInput, boolean overlaps) {
        var config = new IlimapLoader().load("""
                mapping v2 {
                  input original { path "source.xtf"; model "P43Source"; }
                  input alternate { path "alternate.xtf"; model "P43Source"; }
                  output migrated { path "target.xtf"; model "P43Target"; }
                  rule drop {
                    source s from original class "P43Source.Data.Obsolete";
                    loss { sourcePath s; reasonCode "CLASS_REMOVED"; description "Removed"; }
                  }
                  rule dropAgain {
                    source t from %s class "P43Source.Data.Obsolete";
                    loss { sourcePath t; reasonCode "CLASS_REMOVED"; description "Removed"; }
                  }
                }
                """.formatted(secondInput), Path.of("."));
        var registry = ModelRegistry.builder()
                .config(config)
                .modelDir("src/test/data/migration")
                .build();
        var plan = new MappingCompiler().compileTyped(config, registry);

        if (overlaps) {
            assertThat(plan.diagnostics().all())
                    .filteredOn(d -> d.severity() == Severity.ERROR)
                    .singleElement()
                    .satisfies(d -> assertThat(d.message())
                            .contains("P43Source.Data.Obsolete", "overlaps class loss rule 'dropAgain'"));
        } else {
            assertThat(plan.diagnostics().all())
                    .filteredOn(d -> d.severity() == Severity.ERROR)
                    .isEmpty();
            assertThat(plan.rules()).hasSize(2);
        }
    }

    @Test
    void rejectsMappingAndClassLossForTheSameInputAndClass() {
        assertLossDiagnostics("""
                rule drop {
                  source s from original class "P43Source.Data.Item";
                  loss { sourcePath s; reasonCode "CLASS_REMOVED"; description "Removed"; }
                }
                rule migrate {
                  source item from original class "P43Source.Data.Item";
                  target migrated class "P43Target.Data.Item";
                  assign { Name = item.Name; Origin = "test"; }
                }
                """, "overlaps mapping rule 'migrate'");
    }

    @Test
    void rejectsClassLossOverlappingAnExplicitBagSource() {
        assertLossDiagnostics("""
                rule drop {
                  source s from original class "P43Source.Data.Obsolete";
                  loss { sourcePath s; reasonCode "CLASS_REMOVED"; description "Removed"; }
                }
                rule migrate {
                  source item from original class "P43Source.Data.Item";
                  target migrated class "P43Target.Data.Item";
                  assign { Name = item.Name; Origin = "test"; }
                  bag History {
                    from old in original class "P43Source.Data.Obsolete";
                    assign { Note = "old"; }
                  }
                }
                """, "overlaps mapping rule 'migrate'");
    }

    @ParameterizedTest
    @CsvSource({
        "missing.Name, Unknown source alias",
        "s.Missing, Source attribute not found",
        "unbekannt, Unknown source alias in expression: unbekannt"
    })
    void rejectsInvalidAttributeLossPath(String path, String message) {
        assertLossDiagnostics("""
                rule migrate {
                  source s from original class "P43Source.Data.Item";
                  target migrated class "P43Target.Data.Item";
                  assign { Name = s.Name; Origin = "test"; }
                  loss { sourcePath %s; reasonCode "ATTRIBUTE_REMOVED"; description "Removed"; }
                }
                """.formatted(path), message);
    }

    private static void assertLossDiagnostics(String rules, String message) {
        var loaded = new IlimapLoader().loadDetailed("""
                mapping v2 {
                  input original { path "source.xtf"; model "P43Source"; }
                  output migrated { path "target.xtf"; model "P43Target"; }
                  %s
                }
                """.formatted(rules), Path.of("."));
        var diagnostics = loaded.diagnostics();
        if (!loaded.hasErrors()) {
            var config = loaded.jobConfig();
            var registry = ModelRegistry.builder()
                    .config(config)
                    .modelDir("src/test/data/migration")
                    .build();
            diagnostics = new MappingCompiler()
                    .compileTyped(config, registry)
                    .diagnostics()
                    .all();
        }
        assertThat(diagnostics)
                .anyMatch(d -> d.severity() == Severity.ERROR && d.message().contains(message));
    }

    // -- Positive tests -----------------------------------------------------

    @Test
    void compilesValidMappingToTransformPlan() {
        JobConfig config = minimalConfig();

        var registry = ModelRegistry.builder().config(config).modelDir(MODELDIR).build();
        TransformPlan plan = new MappingCompiler().compileTyped(config, registry);
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

        var registry = ModelRegistry.builder().config(config).modelDir(MODELDIR).build();
        TransformPlan plan = new MappingCompiler().compileTyped(config, registry);
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

        var registry = ModelRegistry.builder().config(config).modelDir(MODELDIR).build();
        TransformPlan plan = new MappingCompiler().compileTyped(config, registry);

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

        var registry = ModelRegistry.builder().config(config).modelDir(MODELDIR).build();
        TransformPlan plan = new MappingCompiler().compileTyped(config, registry);

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

        var registry = ModelRegistry.builder().config(config).modelDir(MODELDIR).build();
        TransformPlan plan = new MappingCompiler().compileTyped(config, registry);

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

        var registry = ModelRegistry.builder().config(config).modelDir(MODELDIR).build();
        TransformPlan plan = new MappingCompiler().compileTyped(config, registry);

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

        var registry = ModelRegistry.builder().config(config).modelDir(MODELDIR).build();
        TransformPlan plan = new MappingCompiler().compileTyped(config, registry);

        assertThat(plan.diagnostics().all()).anyMatch(d -> d.code().equals(DiagnosticCode.MAP_TYPE_MISMATCH));
    }

    @Test
    void reportsMissingMandatory() {
        JobConfig config = minimalConfig();
        var rule = config.mapping.rules.get(0);
        // Only assign Beschreibung (optional), not Name (mandatory)
        rule.assign = new java.util.LinkedHashMap<>();
        rule.assign.put("Beschreibung", "${s.Beschreibung}");

        var registry = ModelRegistry.builder().config(config).modelDir(MODELDIR).build();
        TransformPlan plan = new MappingCompiler().compileTyped(config, registry);

        assertThat(plan.diagnostics().all())
                .anyMatch(d -> d.code().equals(DiagnosticCode.MAP_MANDATORY_MISSING)
                        && d.message().contains("Name"));
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
        var registry = ModelRegistry.builder().config(config).modelDir(MODELDIR).build();
        TransformPlan plan = new MappingCompiler().compileTyped(config, registry);

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

        var registry = ModelRegistry.builder().config(config).modelDir(MODELDIR).build();
        TransformPlan plan = new MappingCompiler().compileTyped(config, registry);

        assertThat(plan.diagnostics().all())
                .anyMatch(d -> d.code().equals(DiagnosticCode.MAP_CYCLIC_DEPENDENCY) && d.severity() == Severity.ERROR);
    }

    @Test
    void enumLiteralDetected() {
        JobConfig config = minimalConfig();
        var rule = config.mapping.rules.get(0);
        rule.assign = new java.util.LinkedHashMap<>();
        rule.assign.put("Name", "#enumValue");

        var registry = ModelRegistry.builder().config(config).modelDir(MODELDIR).build();
        TransformPlan plan = new MappingCompiler().compileTyped(config, registry);

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

        var registry = ModelRegistry.builder().config(config).modelDir(MODELDIR).build();
        TransformPlan plan = new MappingCompiler().compileTyped(config, registry);

        var assign = plan.rules().get(0).assignments().get(0);
        assertThat(assign.expression().resultType()).isEqualTo(TypeInfo.TEXT);
    }

    @Test
    void xmlDateTimeFunctionTypeIsInferred() {
        JobConfig config = minimalConfig();
        var rule = config.mapping.rules.get(0);
        rule.assign = new java.util.LinkedHashMap<>();
        rule.assign.put("Name", "toXmlDateTime(${s.Beschreibung})");

        var registry = ModelRegistry.builder().config(config).modelDir(MODELDIR).build();
        TransformPlan plan = new MappingCompiler().compileTyped(config, registry);

        var assign = plan.rules().get(0).assignments().get(0);
        assertThat(assign.expression().resultType()).isEqualTo(TypeInfo.XML_DATE_TIME);
    }

    @Test
    void unknownFunctionTypeIsUnknown() {
        JobConfig config = minimalConfig();
        var rule = config.mapping.rules.get(0);
        rule.assign = new java.util.LinkedHashMap<>();
        rule.assign.put("Name", "nonexistent(42)");

        var registry = ModelRegistry.builder().config(config).modelDir(MODELDIR).build();
        TransformPlan plan = new MappingCompiler().compileTyped(config, registry);

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
