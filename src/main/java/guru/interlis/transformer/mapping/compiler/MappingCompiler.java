package guru.interlis.transformer.mapping.compiler;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.expr.ExpressionCompiler;
import guru.interlis.transformer.expr.FunctionRegistry;
import guru.interlis.transformer.expr.builtins.BasicFunctions;
import guru.interlis.transformer.expr.builtins.DateFunctions;
import guru.interlis.transformer.expr.builtins.EnumFunctions;
import guru.interlis.transformer.expr.builtins.GeometryFunctions;
import guru.interlis.transformer.expr.builtins.LookupFunctions;
import guru.interlis.transformer.expr.builtins.MathFunctions;
import guru.interlis.transformer.expr.builtins.RefFunctions;
import guru.interlis.transformer.expr.builtins.StringFunctions;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.BasketPlan;
import guru.interlis.transformer.mapping.plan.CompileMode;
import guru.interlis.transformer.mapping.plan.FailPolicy;
import guru.interlis.transformer.mapping.plan.FailPolicyParser;
import guru.interlis.transformer.mapping.plan.OidPlan;
import guru.interlis.transformer.mapping.plan.RefPlan;
import guru.interlis.transformer.mapping.plan.RulePlan;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.ModelRegistry;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.BasketStrategy;
import guru.interlis.transformer.state.OidStrategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MappingCompiler {

    private final FunctionRegistry functionRegistry;
    private final ExpressionCompiler expressionCompiler;
    private final RuleCompiler ruleCompiler = new RuleCompiler();
    private final StructuralValidator structuralValidator = new StructuralValidator();

    public MappingCompiler() {
        this.functionRegistry = defaultRegistry();
        this.expressionCompiler = new ExpressionCompiler();
    }

    public MappingCompiler(FunctionRegistry functionRegistry) {
        this.functionRegistry = functionRegistry;
        this.expressionCompiler = new ExpressionCompiler();
    }

    private static FunctionRegistry defaultRegistry() {
        FunctionRegistry registry = new FunctionRegistry();
        BasicFunctions.registerAll(registry);
        StringFunctions.registerAll(registry);
        DateFunctions.registerAll(registry);
        EnumFunctions.registerAll(registry);
        RefFunctions.registerAll(registry);
        MathFunctions.registerAll(registry);
        LookupFunctions.registerAll(registry);
        GeometryFunctions.registerAll(registry);
        return registry;
    }

    public CompileResult compile(JobConfig config) {
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        structuralValidator.validateVersion(config, diagnostics);
        structuralValidator.validateOutputs(config, diagnostics);
        structuralValidator.validateRules(
                config,
                new CompilerContext(
                        config,
                        null,
                        functionRegistry,
                        expressionCompiler,
                        diagnostics,
                        Map.of(),
                        OidStrategy.INTEGER,
                        null,
                        config.mapping.defaults));
        return new CompileResult(config, diagnostics);
    }

    public TransformPlan compileTyped(JobConfig config, ModelRegistry modelRegistry) {
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        structuralValidator.validateVersion(config, diagnostics);
        structuralValidator.validateOutputs(config, diagnostics);

        CompileMode compileMode = parseCompileMode(config.mapping.compileMode, diagnostics);

        new DslCapabilityValidator().validateSupportedFeatures(config, diagnostics);

        OidStrategy oidStrategy = OidStrategy.INTEGER;
        String oidNamespace = null;
        if (config.mapping.oidStrategy != null) {
            try {
                oidStrategy = OidStrategy.fromString(config.mapping.oidStrategy.defaultStrategy);
            } catch (IllegalArgumentException e) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.MAP_UNKNOWN_OID_STRATEGY,
                        Severity.ERROR,
                        "Unknown OID strategy: " + config.mapping.oidStrategy.defaultStrategy,
                        null,
                        "Valid values: preserve, integer, uuid, deterministicUuid, external"));
            }
            oidNamespace = config.mapping.oidStrategy.namespace;
        }

        if (oidStrategy == OidStrategy.EXTERNAL) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.MAP_EXTERNAL_STRATEGY_UNSUPPORTED,
                    Severity.ERROR,
                    "EXTERNAL OID strategy is not yet implemented",
                    null,
                    "Use one of: preserve, integer, uuid, deterministicUuid"));
            oidStrategy = OidStrategy.INTEGER;
        }

        Map<String, Map<String, String>> enumMaps = extractEnumMaps(config);

        CompilerContext ctx = new CompilerContext(
                config,
                modelRegistry,
                functionRegistry,
                expressionCompiler,
                diagnostics,
                enumMaps,
                oidStrategy,
                oidNamespace,
                config.mapping.defaults);

        structuralValidator.validateRulesStructurally(config, ctx);

        List<RulePlan> rulePlans = new ArrayList<>();
        for (JobConfig.RuleSpec rule : config.mapping.rules) {
            RulePlan rp = ruleCompiler.compileRule(rule, ctx);
            if (rp != null) {
                rulePlans.add(rp);
            }
        }

        checkRuleDependencies(rulePlans, diagnostics);

        validateOidTypeCompatibility(oidStrategy, rulePlans, modelRegistry, diagnostics);

        BasketStrategy basketStrategy = BasketStrategy.PRESERVE;
        if (config.mapping.basketStrategy != null) {
            try {
                basketStrategy = BasketStrategy.fromString(config.mapping.basketStrategy.defaultStrategy);
            } catch (IllegalArgumentException e) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.MAP_UNKNOWN_BASKET_STRATEGY,
                        Severity.ERROR,
                        "Unknown basket strategy: " + config.mapping.basketStrategy.defaultStrategy,
                        null,
                        "Valid values: preserve, generateUuid, preserveOrGenerateUuid, byTopic"));
            }
        }

        FailPolicy failPolicy = parseFailPolicy(config.job.failPolicy);

        if (compileMode == CompileMode.STRICT) {
            List<Diagnostic> existing = new ArrayList<>(diagnostics.all());
            for (Diagnostic d : existing) {
                if (d.severity() == Severity.WARNING) {
                    diagnostics.add(
                            new Diagnostic(d.code(), Severity.ERROR, d.message(), d.sourcePath(), d.suggestion()));
                }
            }
        }

        return new TransformPlan(
                config.job.name,
                config.job.direction,
                failPolicy,
                compileMode,
                rulePlans,
                modelRegistry.inputsById(),
                modelRegistry.outputsById(),
                diagnostics,
                new OidPlan(oidStrategy, oidNamespace),
                new BasketPlan(basketStrategy),
                enumMaps);
    }

    @Deprecated
    public TransformPlan compileTyped(
            JobConfig config,
            Map<String, TypeSystemFacade> sourceTypeSystems,
            Map<String, TypeSystemFacade> targetTypeSystems) {
        ModelRegistry registry = ModelRegistry.builder()
                .config(config)
                .buildWithSuppliedTypeSystems(sourceTypeSystems, targetTypeSystems);
        return compileTyped(config, registry);
    }

    public void validate(JobConfig config) {
        CompileResult result = compile(config);
        if (result.diagnostics().hasErrors()) {
            StringBuilder sb = new StringBuilder("Mapping validation failed:");
            for (Diagnostic d : result.diagnostics().all()) {
                if (d.severity() == Severity.ERROR) {
                    sb.append("\n  [").append(d.code()).append("] ").append(d.message());
                }
            }
            throw new IllegalArgumentException(sb.toString());
        }
    }

    private void checkRuleDependencies(List<RulePlan> plans, DiagnosticCollector diag) {
        Map<String, RulePlan> byId = new HashMap<>();
        for (RulePlan rp : plans) {
            byId.put(rp.ruleId(), rp);
        }
        for (RulePlan rp : plans) {
            for (RefPlan ref : rp.refs()) {
                if (ref.targetRuleId() != null && byId.containsKey(ref.targetRuleId())) {
                    RulePlan target = byId.get(ref.targetRuleId());
                    for (RefPlan backRef : target.refs()) {
                        if (rp.ruleId().equals(backRef.targetRuleId())) {
                            diag.add(new Diagnostic(
                                    DiagnosticCode.MAP_CYCLIC_DEPENDENCY,
                                    Severity.ERROR,
                                    "Cyclic dependency between rules '" + rp.ruleId() + "' and '" + ref.targetRuleId()
                                            + "'",
                                    rp.ruleId(),
                                    "Break the cycle by reordering or removing one reference"));
                        }
                    }
                } else if (ref.targetRuleId() != null && !byId.containsKey(ref.targetRuleId())) {
                    diag.add(new Diagnostic(
                            DiagnosticCode.MAP_UNKNOWN_OUTPUT,
                            Severity.ERROR,
                            "targetRuleId not found: '" + ref.targetRuleId() + "' referenced from rule '" + rp.ruleId()
                                    + "'",
                            rp.ruleId(),
                            "Define a rule with id '" + ref.targetRuleId() + "' or correct the targetRule reference"));
                }
            }
        }
    }

    private static void validateOidTypeCompatibility(
            OidStrategy oidStrategy,
            List<RulePlan> rulePlans,
            ModelRegistry modelRegistry,
            DiagnosticCollector diagnostics) {
        for (RulePlan rp : rulePlans) {
            String targetPath = CompileUtils.getScopedName(rp.targetClass());
            String outputId = rp.outputId();
            if (outputId == null || outputId.isEmpty()) continue;

            TypeSystemFacade ts;
            try {
                ts = modelRegistry.requireTargetTypeSystem(outputId);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (ts == null) continue;

            String oidType = ts.getOidType(targetPath);
            if (oidType == null) continue;

            String oidTypeLower = oidType.toLowerCase();

            switch (oidStrategy) {
                case INTEGER -> {
                    if (oidTypeLower.contains("uuid") || oidTypeLower.contains("anyoid")) {
                        diagnostics.add(new Diagnostic(
                                DiagnosticCode.MAP_OID_STRATEGY_INCOMPATIBLE,
                                Severity.ERROR,
                                "OID strategy 'integer' is incompatible with target OID type '" + oidType
                                        + "' for class " + targetPath,
                                rp.ruleId(),
                                "Use 'uuid' or 'deterministicUuid' for UUIDOID targets"));
                    }
                    if (oidTypeLower.contains("text")) {
                        diagnostics.add(new Diagnostic(
                                DiagnosticCode.MAP_OID_STRATEGY_INCOMPATIBLE,
                                Severity.ERROR,
                                "OID strategy 'integer' is incompatible with target OID type '" + oidType
                                        + "' for class " + targetPath,
                                rp.ruleId(),
                                "Use 'preserve' for TextOID targets"));
                    }
                }
                case UUID, DETERMINISTIC_UUID -> {
                    if (oidTypeLower.contains("numeric")) {
                        diagnostics.add(new Diagnostic(
                                DiagnosticCode.MAP_OID_STRATEGY_INCOMPATIBLE,
                                Severity.ERROR,
                                "OID strategy '" + oidStrategy.name().toLowerCase()
                                        + "' is incompatible with target OID type '" + oidType + "' for class "
                                        + targetPath,
                                rp.ruleId(),
                                "Use 'integer' for NumericOID targets"));
                    }
                }
                case PRESERVE -> {
                    if (oidTypeLower.contains("uuid") || oidTypeLower.contains("anyoid")) {
                        diagnostics.add(new Diagnostic(
                                DiagnosticCode.MAP_OID_STRATEGY_INCOMPATIBLE,
                                Severity.WARNING,
                                "OID strategy 'preserve' copies source OID, which may not be a UUID for target OID type '"
                                        + oidType + "' for class " + targetPath,
                                rp.ruleId(),
                                "Use 'uuid' or 'deterministicUuid' for UUIDOID targets"));
                    }
                }
                default -> {}
            }
        }
    }

    private static FailPolicy parseFailPolicy(String failPolicy) {
        return FailPolicyParser.parseOrDefault(failPolicy, FailPolicy.STRICT);
    }

    private static CompileMode parseCompileMode(String compileMode, DiagnosticCollector diag) {
        if (compileMode == null) return CompileMode.STRICT;
        if (compileMode.equalsIgnoreCase("strict")) return CompileMode.STRICT;
        if (compileMode.equalsIgnoreCase("compatible")) return CompileMode.COMPATIBLE;
        if (compileMode.equalsIgnoreCase("report")) return CompileMode.REPORT;
        diag.add(new Diagnostic(
                DiagnosticCode.MAP_UNKNOWN_COMPILE_MODE,
                Severity.WARNING,
                "Unknown compileMode: " + compileMode,
                null,
                "Valid values: strict, compatible, report"));
        return CompileMode.STRICT;
    }

    private static Map<String, Map<String, String>> extractEnumMaps(JobConfig config) {
        if (config.mapping.enums == null || config.mapping.enums.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, String>> result = new HashMap<>();
        config.mapping.enums.forEach((name, mapping) -> {
            result.put(name, Map.copyOf(mapping));
        });
        return Map.copyOf(result);
    }

    public record CompileResult(JobConfig config, DiagnosticCollector diagnostics) {}
}
