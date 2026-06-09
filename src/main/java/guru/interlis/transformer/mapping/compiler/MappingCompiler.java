package guru.interlis.transformer.mapping.compiler;

import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Cardinality;
import ch.interlis.ili2c.metamodel.CompositionType;
import ch.interlis.ili2c.metamodel.Container;
import ch.interlis.ili2c.metamodel.Extendable;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.Topic;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.expr.ExpressionCompiler;
import guru.interlis.transformer.expr.FunctionRegistry;
import guru.interlis.transformer.expr.builtins.BasicFunctions;
import guru.interlis.transformer.expr.builtins.DateFunctions;
import guru.interlis.transformer.expr.builtins.EnumFunctions;
import guru.interlis.transformer.expr.builtins.MathFunctions;
import guru.interlis.transformer.expr.builtins.RefFunctions;
import guru.interlis.transformer.expr.builtins.StringFunctions;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.AssignmentPlan;
import guru.interlis.transformer.mapping.plan.BagPlan;
import guru.interlis.transformer.mapping.plan.BasketPlan;
import guru.interlis.transformer.mapping.plan.CompileMode;
import guru.interlis.transformer.mapping.plan.CompiledExpression;
import guru.interlis.transformer.mapping.plan.ExpressionCompileContext;
import guru.interlis.transformer.mapping.plan.ExpressionKind;
import guru.interlis.transformer.mapping.plan.FailPolicy;
import guru.interlis.transformer.mapping.plan.InputBinding;
import guru.interlis.transformer.mapping.plan.OidPlan;
import guru.interlis.transformer.mapping.plan.OutputBinding;
import guru.interlis.transformer.mapping.plan.RefPlan;
import guru.interlis.transformer.mapping.plan.RulePlan;
import guru.interlis.transformer.mapping.plan.SourcePlan;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.model.ModelRegistry;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.BasketStrategy;
import guru.interlis.transformer.state.OidStrategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MappingCompiler {

    private final FunctionRegistry functionRegistry;
    private final ExpressionCompiler expressionCompiler;

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
        return registry;
    }

    public CompileResult compile(JobConfig config) {
        DiagnosticCollector diagnostics = new DiagnosticCollector();
        validateVersion(config, diagnostics);
        validateOutputs(config, diagnostics);
        validateRules(config, diagnostics);
        return new CompileResult(config, diagnostics);
    }

    // -- New typed compilation with ModelRegistry ---------------------------

    public TransformPlan compileTyped(JobConfig config, ModelRegistry modelRegistry) {
        DiagnosticCollector diagnostics = new DiagnosticCollector();

        validateVersion(config, diagnostics);
        validateOutputs(config, diagnostics);
        validateRulesStructurally(config, diagnostics);

        // Parse compile mode
        CompileMode compileMode = parseCompileMode(config.mapping.compileMode, diagnostics);

        // Phase 21: Validate unsupported DSL features
        new DslCapabilityValidator().validateSupportedFeatures(config, diagnostics);

        // Compile rules
        Map<String, Map<String, String>> enumMaps = extractEnumMaps(config);
        List<RulePlan> rulePlans = new ArrayList<>();
        for (JobConfig.RuleSpec rule : config.mapping.rules) {
            RulePlan rp = compileRule(rule, diagnostics, modelRegistry, enumMaps, config.mapping.defaults);
            if (rp != null) {
                rulePlans.add(rp);
            }
        }

        checkRuleDependencies(rulePlans, diagnostics);

        // Compile OID strategy
        OidStrategy oidStrategy = OidStrategy.INTEGER;
        String oidNamespace = null;
        if (config.mapping.oidStrategy != null) {
            try {
                oidStrategy = OidStrategy.fromString(config.mapping.oidStrategy.defaultStrategy);
            } catch (IllegalArgumentException e) {
                diagnostics.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_OID_STRATEGY, Severity.ERROR,
                        "Unknown OID strategy: " + config.mapping.oidStrategy.defaultStrategy,
                        null, "Valid values: preserve, integer, uuid, deterministicUuid, external"));
            }
            oidNamespace = config.mapping.oidStrategy.namespace;
        }

        if (oidStrategy == OidStrategy.EXTERNAL) {
            diagnostics.add(new Diagnostic(DiagnosticCode.MAP_EXTERNAL_STRATEGY_UNSUPPORTED, Severity.ERROR,
                    "EXTERNAL OID strategy is not yet implemented",
                    null, "Use one of: preserve, integer, uuid, deterministicUuid"));
            oidStrategy = OidStrategy.INTEGER;
        }

        validateOidTypeCompatibility(oidStrategy, rulePlans, modelRegistry, diagnostics);

        // Compile basket strategy
        BasketStrategy basketStrategy = BasketStrategy.PRESERVE;
        if (config.mapping.basketStrategy != null) {
            try {
                basketStrategy = BasketStrategy.fromString(config.mapping.basketStrategy.defaultStrategy);
            } catch (IllegalArgumentException e) {
                diagnostics.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_BASKET_STRATEGY, Severity.ERROR,
                        "Unknown basket strategy: " + config.mapping.basketStrategy.defaultStrategy,
                        null, "Valid values: preserve, generateUuid, preserveOrGenerateUuid, byTopic"));
            }
        }

        // Parse fail policy
        FailPolicy failPolicy = parseFailPolicy(config.job.failPolicy);

        // Phase 21: In STRICT mode, upgrade WARNING diagnostics to ERROR
        if (compileMode == CompileMode.STRICT) {
            List<Diagnostic> existing = new ArrayList<>(diagnostics.all());
            for (Diagnostic d : existing) {
                if (d.severity() == Severity.WARNING) {
                    diagnostics.add(new Diagnostic(d.code(), Severity.ERROR, d.message(),
                            d.sourcePath(), d.suggestion()));
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
                enumMaps
        );
    }

    // -- Deprecated typed compilation (backward compatible) ------------------

    /**
     * @deprecated use {@link #compileTyped(JobConfig, ModelRegistry)} instead.
     * Internally constructs a temporary {@link ModelRegistry} from the provided maps.
     */
    @Deprecated
    public TransformPlan compileTyped(JobConfig config,
                                       Map<String, TypeSystemFacade> sourceTypeSystems,
                                       Map<String, TypeSystemFacade> targetTypeSystems) {
        ModelRegistry registry = ModelRegistry.builder()
                .config(config)
                .buildWithSuppliedTypeSystems(sourceTypeSystems, targetTypeSystems);
        return compileTyped(config, registry);
    }

    // -- Backward-compatible convenience method ----------------------------

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

    // -- Typed rule compilation --------------------------------------------

    private RulePlan compileRule(JobConfig.RuleSpec rule, DiagnosticCollector diag,
                                  ModelRegistry modelRegistry,
                                  Map<String, Map<String, String>> enumMaps,
                                  Map<String, String> mappingDefaults) {
        String ruleId = rule.id;
        String targetOutput = rule.getEffectiveTargetOutput();
        String targetClassName = rule.getEffectiveTargetClass();

        // Resolve target class via ModelRegistry
        Table targetClass = resolveTargetClass(targetOutput, targetClassName, modelRegistry);
        if (targetClass == null) {
            diag.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_TARGET_CLASS, Severity.ERROR,
                    "Target class not found in model: " + targetClassName,
                    ruleId, "Check the target class name and model registration"));
            return null;
        }
        if (targetClass.isAbstract()) {
            diag.add(new Diagnostic(DiagnosticCode.MAP_ABSTRACT_TARGET_CLASS, Severity.ERROR,
                    "Target class is abstract: " + targetClassName,
                    ruleId, "Choose a concrete target class or instantiate via a specific subclass"));
        }
        if (!targetClass.isIdentifiable()) {
            diag.add(new Diagnostic(DiagnosticCode.MAP_NON_TRANSFERABLE_TARGET, Severity.WARNING,
                    "Target class is not identifiable (possibly a view): " + targetClassName,
                    ruleId, "Views cannot be written as transfer objects"));
        }

        TypeSystemFacade targetTs = targetOutput != null && !targetOutput.isEmpty()
                ? modelRegistry.requireTargetTypeSystem(targetOutput) : null;

        // Compile sources (phase 1: class resolution)
        List<SourcePlan> sourcePlans = new ArrayList<>();
        for (JobConfig.SourceSpec src : rule.sources) {
            SourcePlan sp = compileSource(src, ruleId, modelRegistry, diag);
            if (sp != null) {
                sourcePlans.add(sp);
            }
        }
        if (sourcePlans.isEmpty()) {
            return null;
        }

        // Build sourcesByAlias map for expression context
        Map<String, SourcePlan> sourcesByAlias = new HashMap<>();
        for (SourcePlan sp : sourcePlans) {
            sourcesByAlias.put(sp.alias(), sp);
        }

        // Compile where filters (phase 2: expression compilation)
        sourcePlans = compileWhereFilters(sourcePlans, sourcesByAlias, ruleId, enumMaps, diag,
                rule.sources);
        sourcesByAlias = new HashMap<>();
        for (SourcePlan sp : sourcePlans) {
            sourcesByAlias.put(sp.alias(), sp);
        }

        // Compile rule-level predicate (Phase 21)
        CompiledExpression predicate = null;
        if (rule.where != null && !rule.where.isBlank()) {
            ExpressionCompileContext predCtx = new ExpressionCompileContext(
                    ruleId, sourcesByAlias, TypeInfo.BOOLEAN, functionRegistry, enumMaps);
            predicate = expressionCompiler.compile(rule.where, predCtx, diag);
        }

        // Compile assignments
        List<AssignmentPlan> assignmentPlans = new ArrayList<>();
        Set<String> assignedTargets = new HashSet<>();
        for (JobConfig.AttributeMapping attr : rule.getAllAttributes()) {
            if (attr.target == null || attr.target.isBlank()) continue;
            if (!assignedTargets.add(attr.target)) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_DUPLICATE_TARGET_ASSIGN, Severity.ERROR,
                        "Target attribute assigned multiple times: " + attr.target,
                        ruleId, "Remove duplicate assignment or use an explicit merge policy"));
            }

            AssignmentPlan ap = compileAssignment(attr, targetClass, targetTs, sourcesByAlias,
                    ruleId, enumMaps, diag);
            if (ap != null) {
                assignmentPlans.add(ap);
            }
        }

        // Phase 21: Compile rule-level defaults
        if (rule.defaults != null && !rule.defaults.isEmpty()) {
            for (var entry : rule.defaults.entrySet()) {
                if (!assignedTargets.contains(entry.getKey())) {
                    AssignmentPlan dp = compileDefaultAssignment(entry.getKey(), entry.getValue(),
                            targetClass, targetTs, sourcesByAlias, ruleId, enumMaps, diag);
                    if (dp != null) {
                        assignedTargets.add(entry.getKey());
                        assignmentPlans.add(dp);
                    }
                }
            }
        }

        // Phase 21: Compile mapping-level defaults (fallback after rule defaults)
        if (mappingDefaults != null && !mappingDefaults.isEmpty()) {
            for (var entry : mappingDefaults.entrySet()) {
                if (!assignedTargets.contains(entry.getKey())) {
                    AssignmentPlan dp = compileDefaultAssignment(entry.getKey(), entry.getValue(),
                            targetClass, targetTs, sourcesByAlias, ruleId, enumMaps, diag);
                    if (dp != null) {
                        assignedTargets.add(entry.getKey());
                        assignmentPlans.add(dp);
                    }
                }
            }
        }

        // Check mandatory coverage (after defaults, Phase 21)
        if (targetTs != null) {
            checkMandatoryCoverage(targetClass, targetTs, assignmentPlans, ruleId, diag);
        }

        // Compile refs
        List<RefPlan> refPlans = new ArrayList<>();
        for (JobConfig.RefMapping ref : rule.getEffectiveRefs()) {
            RefPlan rp = compileRef(ref, targetClass, targetTs, sourcePlans, ruleId, diag);
            if (rp != null) {
                refPlans.add(rp);
            }
        }

        // Compile bags
        List<BagPlan> bagPlans = compileBags(rule, sourcePlans, sourcesByAlias, targetClass, targetTs,
                ruleId, modelRegistry, enumMaps, diag);

        // Compile identity source keys
        List<String> identitySourceKeys = compileIdentityKeys(rule, sourcePlans, diag);

        return new RulePlan(
                ruleId,
                targetOutput != null ? targetOutput : "",
                targetClass,
                sourcePlans,
                assignmentPlans,
                refPlans,
                bagPlans,
                identitySourceKeys,
                predicate
        );
    }

    private SourcePlan compileSource(JobConfig.SourceSpec src, String ruleId,
                                      ModelRegistry modelRegistry,
                                      DiagnosticCollector diag) {
        if (src.alias == null || src.alias.isBlank()) return null;
        if (src.clazz == null || src.clazz.isBlank()) return null;

        // Find the type system for this source's inputs
        TypeSystemFacade sourceTs = null;
        for (String inputId : src.getInputIds()) {
            try {
                sourceTs = modelRegistry.requireSourceTypeSystem(inputId);
                break;
            } catch (IllegalArgumentException e) {
                // try next input
            }
        }

        Table sourceClass = null;
        if (sourceTs != null) {
            sourceClass = sourceTs.resolveClass(src.clazz);
        }
        if (sourceClass == null) {
            diag.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_SOURCE_CLASS, Severity.ERROR,
                    "Source class not found in any registered model: " + src.clazz,
                    ruleId, "Check the source class name and ensure its model is listed in inputs"));
        }

        return new SourcePlan(src.alias, sourceClass, src.getInputIds(), null);
    }

    private List<SourcePlan> compileWhereFilters(List<SourcePlan> sourcePlans,
                                                   Map<String, SourcePlan> sourcesByAlias,
                                                   String ruleId,
                                                   Map<String, Map<String, String>> enumMaps,
                                                   DiagnosticCollector diag,
                                                   List<JobConfig.SourceSpec> sourceSpecs) {
        Map<String, String> whereByAlias = new HashMap<>();
        for (JobConfig.SourceSpec src : sourceSpecs) {
            if (src.where != null && !src.where.isBlank() && src.alias != null) {
                whereByAlias.put(src.alias, src.where);
            }
        }
        if (whereByAlias.isEmpty()) {
            return sourcePlans;
        }

        List<SourcePlan> result = new ArrayList<>();
        for (SourcePlan sp : sourcePlans) {
            String rawWhere = whereByAlias.get(sp.alias());
            CompiledExpression compiledWhere = null;
            if (rawWhere != null) {
                ExpressionCompileContext ctx = new ExpressionCompileContext(
                        ruleId, sourcesByAlias, TypeInfo.BOOLEAN, functionRegistry, enumMaps);
                compiledWhere = expressionCompiler.compile(rawWhere, ctx, diag);
            }
            result.add(new SourcePlan(sp.alias(), sp.sourceClass(), sp.inputIds(), compiledWhere));
        }
        return result;
    }

    private AssignmentPlan compileAssignment(JobConfig.AttributeMapping attr,
                                              Table targetClass, TypeSystemFacade targetTs,
                                              Map<String, SourcePlan> sourcesByAlias,
                                              String ruleId,
                                              Map<String, Map<String, String>> enumMaps,
                                              DiagnosticCollector diag) {
        String targetName = attr.target;
        String expr = attr.expr;

        AttributeDef targetAttr = null;
        if (targetTs != null) {
            targetAttr = targetTs.findAttribute(targetClass, targetName);
            if (targetAttr == null) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_TARGET_ATTRIBUTE, Severity.ERROR,
                        "Target attribute not found: " + targetName + " in class " + targetClass.getName(),
                        ruleId, "Check the target attribute name"));
            }
        }

        TypeInfo expectedTargetType = targetAttr != null
                ? ExpressionCompiler.classifyIliAttr(targetAttr) : TypeInfo.UNKNOWN;

        ExpressionCompileContext compileCtx = new ExpressionCompileContext(ruleId, sourcesByAlias,
                expectedTargetType, functionRegistry, enumMaps);

        CompiledExpression compiled = expressionCompiler.compile(expr, compileCtx, diag);

        if (targetAttr != null && compiled.resultType() != TypeInfo.UNKNOWN) {
            TypeInfo targetType = ExpressionCompiler.classifyIliAttr(targetAttr);
            if (!isTypeCompatible(compiled.resultType(), targetType)) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_TYPE_MISMATCH, Severity.WARNING,
                        "Type mismatch: expression '" + expr + "' produces " + compiled.resultType()
                                + " but target '" + targetName + "' expects " + targetType,
                        ruleId, "Add a type conversion or check the expression"));
            }
        }

        return new AssignmentPlan(targetName, targetAttr, compiled);
    }

    private AssignmentPlan compileDefaultAssignment(String targetName, String expr,
                                                     Table targetClass, TypeSystemFacade targetTs,
                                                     Map<String, SourcePlan> sourcesByAlias,
                                                     String ruleId,
                                                     Map<String, Map<String, String>> enumMaps,
                                                     DiagnosticCollector diag) {
        AttributeDef targetAttr = null;
        if (targetTs != null) {
            targetAttr = targetTs.findAttribute(targetClass, targetName);
        }

        TypeInfo expectedTargetType = targetAttr != null
                ? ExpressionCompiler.classifyIliAttr(targetAttr) : TypeInfo.UNKNOWN;

        ExpressionCompileContext compileCtx = new ExpressionCompileContext(ruleId, sourcesByAlias,
                expectedTargetType, functionRegistry, enumMaps);

        CompiledExpression compiled = expressionCompiler.compile(expr, compileCtx, diag);

        if (targetAttr != null && compiled.resultType() != TypeInfo.UNKNOWN) {
            TypeInfo targetType = ExpressionCompiler.classifyIliAttr(targetAttr);
            if (!isTypeCompatible(compiled.resultType(), targetType)) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_TYPE_MISMATCH, Severity.WARNING,
                        "Default expression '" + expr + "' for '" + targetName
                                + "' produces " + compiled.resultType()
                                + " but target expects " + targetType,
                        ruleId, "Check the default expression type"));
            }
        }

        return new AssignmentPlan(targetName, targetAttr, compiled);
    }

    private RefPlan compileRef(JobConfig.RefMapping ref, Table targetClass,
                                 TypeSystemFacade targetTs, List<SourcePlan> sourcePlans,
                                 String ruleId,
                                 DiagnosticCollector diag) {
        // Determine role name from ref structure
        String roleName = ref.role;
        if (roleName == null && ref.target != null) {
            roleName = ref.target; // backward compat
        }

        // Check role exists (look in target class's target-for-roles)
        if (roleName != null && targetTs != null) {
            boolean roleFound = false;
            ch.interlis.ili2c.metamodel.RoleDef foundRole = null;
            @SuppressWarnings("rawtypes")
            var it = targetClass.getTargetForRoles();
            if (it != null) {
                while (it.hasNext()) {
                    Object obj = it.next();
                    if (obj instanceof ch.interlis.ili2c.metamodel.RoleDef roleDef) {
                        if (roleName.equals(roleDef.getName())) {
                            roleFound = true;
                            foundRole = roleDef;
                            break;
                        }
                    }
                }
            }
            if (!roleFound) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_ROLE, Severity.WARNING,
                        "Role not found on target class '" + targetClass.getName() + "': " + roleName,
                        ruleId, "Check the association/role name in the target model"));
            }

            // Phase 19: Check association exists and role belongs to it
            if (roleFound && ref.association != null) {
                ch.interlis.ili2c.metamodel.Container container = foundRole.getContainer();
                if (container instanceof ch.interlis.ili2c.metamodel.AssociationDef assoc) {
                    if (!ref.association.equals(assoc.getName())) {
                        diag.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_ROLE, Severity.ERROR,
                                "Role '" + roleName + "' belongs to association '"
                                        + assoc.getName() + "', not '" + ref.association + "'",
                                ruleId, "Correct the association name or remove it"));
                    }
                } else if (ref.association != null) {
                    diag.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_ROLE, Severity.WARNING,
                            "Role '" + roleName + "' is not part of an association"
                                    + " but association '" + ref.association + "' was specified",
                            ruleId, "Remove the association name or use the correct role"));
                }
            }

            // Phase 19: Check mandatory status consistency
            if (roleFound && foundRole != null) {
                ch.interlis.ili2c.metamodel.Cardinality card = foundRole.getCardinality();
                long modelMin = card != null ? card.getMinimum() : 0;
                if (modelMin > 0 && !ref.required) {
                    diag.add(new Diagnostic(DiagnosticCode.MAP_TYPE_MISMATCH, Severity.WARNING,
                            "Role '" + roleName + "' is mandatory in model but ref is marked optional",
                            ruleId, "Set required: true or adjust the model"));
                }
                if (modelMin == 0 && ref.required) {
                    diag.add(new Diagnostic(DiagnosticCode.MAP_TYPE_MISMATCH, Severity.WARNING,
                            "Role '" + roleName + "' is optional in model but ref is marked required",
                            ruleId, "Set required: false or adjust the model"));
                }
            }
        }

        // Phase 19: Check source reference path exists
        if (ref.sourceRef != null) {
            String sourceRefPath = ref.sourceRef;
            int dotIdx = sourceRefPath.indexOf('.');
            if (dotIdx > 0) {
                String alias = sourceRefPath.substring(0, dotIdx);
                String attrName = sourceRefPath.substring(dotIdx + 1);
                boolean aliasFound = false;
                for (SourcePlan sp : sourcePlans) {
                    if (alias.equals(sp.alias())) {
                        aliasFound = true;
                        break;
                    }
                }
                if (!aliasFound) {
                    diag.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_SOURCE_ATTRIBUTE, Severity.WARNING,
                            "Source alias not found for ref '" + sourceRefPath + "': " + alias,
                            ruleId, "Check that a source with alias '" + alias + "' is defined"));
                }
            }
        }

        String association = ref.association;
        String sourceRef = ref.sourceRef;
        String targetRuleId = ref.targetRule;
        if (targetRuleId == null && ref.targetObject != null) {
            targetRuleId = ref.targetObject.rule;
            if (sourceRef == null) {
                sourceRef = ref.targetObject.sourceRef;
            }
        }

        return new RefPlan(roleName, association, sourceRef, targetRuleId, ref.required);
    }

    private List<String> compileIdentityKeys(JobConfig.RuleSpec rule, List<SourcePlan> sourcePlans,
                                              DiagnosticCollector diag) {
        if (rule.identity == null || rule.identity.sourceKey == null || rule.identity.sourceKey.isEmpty()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        for (String key : rule.identity.sourceKey) {
            if (key == null || key.isBlank()) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_IDENTITY_KEY_MISSING, Severity.ERROR,
                        "Identity key is null or empty in rule " + rule.id,
                        rule.id, "Remove empty key or provide a valid attribute reference"));
                continue;
            }
            String trimmed = key.trim();

            if (!seenKeys.add(trimmed)) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_IDENTITY_KEY_DUPLICATE, Severity.ERROR,
                        "Duplicate identity key: " + trimmed + " in rule " + rule.id,
                        rule.id, "Remove duplicate identity key"));
                continue;
            }

            if (!trimmed.contains(".")) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_IDENTITY_KEY_MISSING, Severity.ERROR,
                        "Identity key must be qualified with alias: " + trimmed,
                        rule.id, "Use format: <alias>.<attributeName>"));
                continue;
            }

            String[] parts = trimmed.split("\\.", 2);
            if (parts.length != 2) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_IDENTITY_KEY_MISSING, Severity.ERROR,
                        "Invalid identity key format: " + trimmed,
                        rule.id, "Use format: <alias>.<attributeName>"));
                continue;
            }

            String alias = parts[0];
            String attrName = parts[1];

            SourcePlan matchingSource = sourcePlans.stream()
                    .filter(sp -> sp.alias() != null && sp.alias().equals(alias))
                    .findFirst().orElse(null);

            if (matchingSource == null) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_IDENTITY_KEY_MISSING, Severity.ERROR,
                        "Identity key alias not found: " + alias + " in rule " + rule.id,
                        rule.id, "Verify the alias matches a source definition"));
                continue;
            }

            if (matchingSource.sourceClass() == null) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_IDENTITY_KEY_MISSING, Severity.ERROR,
                        "Source class not resolved for alias: " + alias,
                        rule.id, "Verify the source class is valid"));
                continue;
            }

            AttributeDef attr = null;
            var attrIt = matchingSource.sourceClass().getAttributes();
            while (attrIt.hasNext()) {
                ch.interlis.ili2c.metamodel.Extendable ext = attrIt.next();
                if (ext instanceof AttributeDef ad) {
                    if (ad.getName() != null && ad.getName().equals(attrName)) {
                        attr = ad;
                        break;
                    }
                }
            }

            if (attr == null) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_IDENTITY_KEY_MISSING, Severity.ERROR,
                        "Identity key attribute not found: " + attrName
                                + " on source " + alias + " in rule " + rule.id,
                        rule.id, "Verify the attribute name exists on the source class"));
                continue;
            }

            if (!isValidIdentityKeyType(attr)) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_IDENTITY_KEY_INVALID_TYPE, Severity.ERROR,
                        "Identity key attribute is not a usable scalar type: "
                                + alias + "." + attrName + " in rule " + rule.id,
                        rule.id, "Identity keys must be scalar text, numeric, enum, boolean, or date attributes. "
                                + "Geometry, BAG/STRUCTURE, and reference types are not allowed."));
                continue;
            }

            keys.add(trimmed);
        }
        return keys;
    }

    private static boolean isValidIdentityKeyType(AttributeDef attr) {
        ch.interlis.ili2c.metamodel.Type type = attr.getDomainResolvingAliases();
        if (type == null) type = attr.getDomain();
        if (type == null) return false;

        if (type instanceof ch.interlis.ili2c.metamodel.CoordType
                || type instanceof ch.interlis.ili2c.metamodel.PolylineType
                || type instanceof ch.interlis.ili2c.metamodel.AreaType
                || type instanceof ch.interlis.ili2c.metamodel.SurfaceOrAreaType
                || type instanceof ch.interlis.ili2c.metamodel.SurfaceType
                || type instanceof ch.interlis.ili2c.metamodel.ReferenceType) {
            return false;
        }

        if (type instanceof CompositionType ct) {
            Table component = ct.getComponentType();
            if (component == null) return false;
            var innerIt = component.getAttributes();
            while (innerIt.hasNext()) {
                ch.interlis.ili2c.metamodel.Extendable ext = innerIt.next();
                if (ext instanceof AttributeDef) return false;
            }
            return false;
        }

        return true;
    }

    private void validateIdentityKeysStructurally(JobConfig.RuleSpec rule, DiagnosticCollector diag) {
        if (rule.identity == null || rule.identity.sourceKey == null || rule.identity.sourceKey.isEmpty()) {
            return;
        }
        Set<String> seenKeys = new HashSet<>();
        Set<String> aliases = new HashSet<>();
        for (JobConfig.SourceSpec src : rule.sources) {
            if (src.alias != null && !src.alias.isBlank()) {
                aliases.add(src.alias);
            }
        }
        for (String key : rule.identity.sourceKey) {
            if (key == null || key.isBlank()) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_IDENTITY_KEY_MISSING, Severity.ERROR,
                        "Identity key is null or empty in rule " + rule.id,
                        rule.id, "Remove empty key or provide a valid attribute reference"));
                continue;
            }
            String trimmed = key.trim();

            if (!seenKeys.add(trimmed)) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_IDENTITY_KEY_DUPLICATE, Severity.ERROR,
                        "Duplicate identity key: " + trimmed + " in rule " + rule.id,
                        rule.id, "Remove duplicate identity key"));
            }

            if (!trimmed.contains(".")) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_IDENTITY_KEY_MISSING, Severity.ERROR,
                        "Identity key must be qualified with alias: " + trimmed,
                        rule.id, "Use format: <alias>.<attributeName>"));
                continue;
            }

            String[] parts = trimmed.split("\\.", 2);
            String alias = parts[0];
            if (!aliases.isEmpty() && !aliases.contains(alias)) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_IDENTITY_KEY_MISSING, Severity.ERROR,
                        "Identity key alias not found: " + alias + " in rule " + rule.id,
                        rule.id, "Available aliases: " + aliases));
            }
        }
    }

    private static void validateOidTypeCompatibility(OidStrategy oidStrategy, List<RulePlan> rulePlans,
                                                      ModelRegistry modelRegistry,
                                                      DiagnosticCollector diagnostics) {
        for (RulePlan rp : rulePlans) {
            String targetPath = getScopedName(rp.targetClass());
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
                        diagnostics.add(new Diagnostic(DiagnosticCode.MAP_OID_STRATEGY_INCOMPATIBLE, Severity.ERROR,
                                "OID strategy 'integer' is incompatible with target OID type '"
                                        + oidType + "' for class " + targetPath,
                                rp.ruleId(), "Use 'uuid' or 'deterministicUuid' for UUIDOID targets"));
                    }
                    if (oidTypeLower.contains("text")) {
                        diagnostics.add(new Diagnostic(DiagnosticCode.MAP_OID_STRATEGY_INCOMPATIBLE, Severity.ERROR,
                                "OID strategy 'integer' is incompatible with target OID type '"
                                        + oidType + "' for class " + targetPath,
                                rp.ruleId(), "Use 'preserve' for TextOID targets"));
                    }
                }
                case UUID, DETERMINISTIC_UUID -> {
                    if (oidTypeLower.contains("numeric")) {
                        diagnostics.add(new Diagnostic(DiagnosticCode.MAP_OID_STRATEGY_INCOMPATIBLE, Severity.ERROR,
                                "OID strategy '" + oidStrategy.name().toLowerCase()
                                        + "' is incompatible with target OID type '" + oidType + "' for class "
                                        + targetPath,
                                rp.ruleId(), "Use 'integer' for NumericOID targets"));
                    }
                }
                case PRESERVE -> {
                    if (oidTypeLower.contains("uuid") || oidTypeLower.contains("anyoid")) {
                        diagnostics.add(new Diagnostic(DiagnosticCode.MAP_OID_STRATEGY_INCOMPATIBLE, Severity.WARNING,
                                "OID strategy 'preserve' copies source OID, which may not be a UUID for target OID type '"
                                        + oidType + "' for class " + targetPath,
                                rp.ruleId(), "Use 'uuid' or 'deterministicUuid' for UUIDOID targets"));
                    }
                }
                default -> {}
            }
        }
    }

    // -- Bag compilation (Phase 12) ----------------------------------------

    private List<BagPlan> compileBags(JobConfig.RuleSpec rule, List<SourcePlan> sourcePlans,
                                       Map<String, SourcePlan> sourcesByAlias,
                                       Table targetClass, TypeSystemFacade targetTs,
                                       String ruleId,
                                       ModelRegistry modelRegistry,
                                       Map<String, Map<String, String>> enumMaps,
                                       DiagnosticCollector diag) {
        if (rule.bags == null || rule.bags.isEmpty()) {
            return List.of();
        }

        List<BagPlan> bagPlans = new ArrayList<>();
        for (var entry : rule.bags.entrySet()) {
            String bagAttrName = entry.getKey();
            JobConfig.BagSpec bagSpec = entry.getValue();

            // Validate bag attribute exists on target class
            AttributeDef bagAttr = targetTs != null ? targetTs.findAttribute(targetClass, bagAttrName) : null;
            if (bagAttr == null) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_TARGET_ATTRIBUTE, Severity.ERROR,
                        "Bag target attribute not found: " + bagAttrName + " in class " + targetClass.getName(),
                        ruleId, "Check the bag attribute name in the target class"));
                continue;
            }

            // Check if it's a BAG OF structure type
            ch.interlis.ili2c.metamodel.Type domain = bagAttr.getDomain();
            if (!(domain instanceof CompositionType compositionType)) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_TYPE_MISMATCH, Severity.ERROR,
                        "Bag attribute " + bagAttrName + " is not a structure type",
                        ruleId, "Bag attributes must be BAG OF structures"));
                continue;
            }

            Table componentTable = compositionType.getComponentType();
            if (componentTable == null) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_TYPE_MISMATCH, Severity.ERROR,
                        "Bag attribute " + bagAttrName + " has no component type",
                        ruleId, "Structure type must have a component"));
                continue;
            }

            // Validate the structure name from YAML against the component type
            String structureName = bagSpec.structure;
            if (structureName != null && !structureName.isBlank()) {
                String componentName = getScopedName(componentTable);
                if (!componentTable.getName().equals(structureName)
                        && !componentName.equals(structureName)
                        && !componentName.endsWith("." + structureName)) {
                    diag.add(new Diagnostic(DiagnosticCode.MAP_TYPE_MISMATCH, Severity.WARNING,
                            "Structure name mismatch: YAML specifies '" + structureName
                                    + "' but target attribute uses '" + componentName + "'",
                            ruleId, "The structure is determined by the target model"));
                }
            }

            // Use the component type name for the structure
            String effectiveStructureName = getScopedName(componentTable);

            // Compile the from source
            JobConfig.BagFrom from = bagSpec.from;
            if (from == null || from.clazz == null || from.clazz.isBlank()) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_MISSING_SOURCE_CLASS, Severity.ERROR,
                        "Bag '" + bagAttrName + "' is missing from.class",
                        ruleId, "Specify the source class in bags.<name>.from.class"));
                continue;
            }
            if (from.alias == null || from.alias.isBlank()) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_MISSING_ALIAS, Severity.ERROR,
                        "Bag '" + bagAttrName + "' is missing from.alias",
                        ruleId, "Specify an alias in bags.<name>.from.alias"));
                continue;
            }
            if (from.input == null || from.input.isBlank()) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_MISSING_INPUT, Severity.ERROR,
                        "Bag '" + bagAttrName + "' is missing from.input",
                        ruleId, "Specify the input in bags.<name>.from.input"));
                continue;
            }

            // Resolve the bag source class via ModelRegistry
            TypeSystemFacade sourceTs = modelRegistry.requireSourceTypeSystem(from.input);
            Table bagSourceClass = null;
            if (sourceTs != null) {
                bagSourceClass = sourceTs.resolveClass(from.clazz);
            }
            if (bagSourceClass == null) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_SOURCE_CLASS, Severity.ERROR,
                        "Bag source class not found: " + from.clazz,
                        ruleId, "Check the class name in bags.<name>.from.class"));
                continue;
            }

            // Compile the bag where filter
            CompiledExpression bagWhere = null;
            if (from.where != null && !from.where.isBlank()) {
                Map<String, SourcePlan> bagSourcesByAlias = new HashMap<>(sourcesByAlias);
                ExpressionCompileContext whereCtx = new ExpressionCompileContext(ruleId,
                        bagSourcesByAlias, TypeInfo.BOOLEAN, functionRegistry, enumMaps);
                bagWhere = expressionCompiler.compile(from.where, whereCtx, diag);
            }

            SourcePlan bagSourcePlan = new SourcePlan(from.alias, bagSourceClass,
                    List.of(from.input), bagWhere);

            // Compile bag assignments against structure attributes
            List<AssignmentPlan> bagAssignments = new ArrayList<>();
            Set<String> assignedBagAttrs = new HashSet<>();
            if (bagSpec.assign != null) {
                for (var assignEntry : bagSpec.assign.entrySet()) {
                    String structAttrName = assignEntry.getKey();
                    String expr = assignEntry.getValue();

                    if (!assignedBagAttrs.add(structAttrName)) {
                        diag.add(new Diagnostic(DiagnosticCode.MAP_DUPLICATE_TARGET_ASSIGN, Severity.ERROR,
                                "Structure attribute assigned multiple times: " + structAttrName
                                        + " in bag " + bagAttrName,
                                ruleId, "Remove duplicate assignment"));
                    }

                    AttributeDef structAttr = targetTs != null
                            ? targetTs.findAttribute(componentTable, structAttrName) : null;
                    if (structAttr == null) {
                        diag.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_TARGET_ATTRIBUTE, Severity.ERROR,
                                "Structure attribute not found: " + structAttrName
                                        + " in structure " + effectiveStructureName,
                                ruleId, "Check the attribute name in the structure type"));
                    }

                    TypeInfo expectedType = structAttr != null
                            ? ExpressionCompiler.classifyIliAttr(structAttr) : TypeInfo.UNKNOWN;

                    Map<String, SourcePlan> bagCtxSources = new HashMap<>();
                    bagCtxSources.put(from.alias, bagSourcePlan);
                    bagCtxSources.putAll(sourcesByAlias);

                    ExpressionCompileContext compileCtx = new ExpressionCompileContext(ruleId,
                            bagCtxSources, expectedType, functionRegistry, enumMaps);
                    CompiledExpression compiledExpr = expressionCompiler.compile(expr, compileCtx, diag);

                    AssignmentPlan ap = new AssignmentPlan(structAttrName,
                            structAttr, compiledExpr);
                    bagAssignments.add(ap);
                }
            }

            // Check mandatory coverage for structure attributes
            checkStructureMandatoryCoverage(componentTable, bagAssignments, bagAttrName, ruleId, diag);

            BagPlan.BagMode mode = "expand".equalsIgnoreCase(bagSpec.mode)
                    ? BagPlan.BagMode.EXPAND : BagPlan.BagMode.EMBED;

            BagPlan bp = new BagPlan(bagAttrName, bagSourcePlan, effectiveStructureName,
                    bagAssignments, bagWhere, mode);
            bagPlans.add(bp);
        }
        return bagPlans;
    }

    private void checkStructureMandatoryCoverage(Table componentTable,
                                                  List<AssignmentPlan> assignments,
                                                  String bagAttrName, String ruleId,
                                                  DiagnosticCollector diag) {
        Set<String> assigned = new HashSet<>();
        for (AssignmentPlan ap : assignments) {
            assigned.add(ap.targetAttrName());
        }

        Iterator<Extendable> it = componentTable.getAttributes();
        while (it.hasNext()) {
            Extendable ext = it.next();
            if (ext instanceof AttributeDef attr) {
                var card = attr.getCardinality();
                if (card != null && card.getMinimum() > 0) {
                    if (!assigned.contains(attr.getName())) {
                        diag.add(new Diagnostic(DiagnosticCode.MAP_MANDATORY_MISSING, Severity.WARNING,
                                "Mandatory structure attribute not assigned: " + attr.getName()
                                        + " in structure " + componentTable.getName()
                                        + " (bag " + bagAttrName + ")",
                                ruleId, "Add an assignment or specify a default value"));
                    }
                }
            }
        }
    }

    // -- Function type inference (Phase 4) ---------------------------------

    private TypeInfo inferFunctionType(String expr, DiagnosticCollector diag, String ruleId) {
        String funcName = extractFunctionName(expr);
        if (funcName == null) return TypeInfo.UNKNOWN;
        var def = functionRegistry.resolve(funcName);
        if (def.isPresent()) {
            if (!def.get().deterministic()) {
                diag.add(new Diagnostic(DiagnosticCode.EXPR_NON_DETERMINISTIC, Severity.WARNING,
                        "Non-deterministic function used: " + funcName,
                        ruleId, "Results may vary between runs"));
            }
            return def.get().returnType();
        }
        return TypeInfo.UNKNOWN;
    }

    static String extractFunctionName(String expr) {
        if (expr == null || expr.isBlank()) return null;
        String trimmed = expr.trim();
        int paren = trimmed.indexOf('(');
        if (paren < 0) return null;
        return trimmed.substring(0, paren).trim();
    }

    // -- Type classification helpers ---------------------------------------

    static ExpressionKind classifyExpression(String expr) {
        if (expr == null || expr.isBlank()) return ExpressionKind.UNKNOWN;
        String trimmed = expr.trim();

        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            return ExpressionKind.SOURCE_PATH;
        }
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")
                || trimmed.startsWith("'") && trimmed.endsWith("'")) {
            return ExpressionKind.LITERAL_TEXT;
        }
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return ExpressionKind.LITERAL_BOOLEAN;
        }
        if (trimmed.startsWith("#")) {
            return ExpressionKind.LITERAL_ENUM;
        }
        if (trimmed.contains("(") && trimmed.endsWith(")")) {
            return ExpressionKind.FUNCTION_CALL;
        }
        try {
            Double.parseDouble(trimmed);
            return ExpressionKind.LITERAL_NUMBER;
        } catch (NumberFormatException e) {
            // fall through
        }
        if (trimmed.contains(".")) {
            return ExpressionKind.SOURCE_PATH; // unbraced path
        }
        return ExpressionKind.UNKNOWN;
    }

    static TypeInfo inferExpressionType(String expr, ExpressionKind kind,
                                          List<SourcePlan> sourcePlans,
                                          DiagnosticCollector diag, String ruleId) {
        return switch (kind) {
            case LITERAL_TEXT -> TypeInfo.TEXT;
            case LITERAL_NUMBER -> TypeInfo.NUMERIC;
            case LITERAL_BOOLEAN -> TypeInfo.BOOLEAN;
            case LITERAL_ENUM -> TypeInfo.ENUM;
            case SOURCE_PATH -> inferSourcePathType(expr, sourcePlans);
            case FUNCTION_CALL -> TypeInfo.UNKNOWN; // overridden in instance method
            default -> TypeInfo.UNKNOWN;
        };
    }

    private static TypeInfo inferSourcePathType(String expr, List<SourcePlan> sourcePlans) {
        String trimmed = expr.trim();
        String pathContent;
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            pathContent = trimmed.substring(2, trimmed.length() - 1);
        } else {
            pathContent = trimmed;
        }
        String[] parts = pathContent.split("\\.", 2);
        if (parts.length < 2) return TypeInfo.UNKNOWN;

        String alias = parts[0];
        String attrName = parts[1];

        for (SourcePlan sp : sourcePlans) {
            if (sp.alias().equals(alias) && sp.sourceClass() != null) {
                var it = sp.sourceClass().getAttributes();
                while (it.hasNext()) {
                    ch.interlis.ili2c.metamodel.Extendable ext = it.next();
                    if (ext instanceof AttributeDef attrDef) {
                        if (attrName.equals(attrDef.getName())) {
                            return classifyIliType(attrDef);
                        }
                    }
                }
            }
        }
        return TypeInfo.UNKNOWN;
    }

    static TypeInfo classifyIliType(AttributeDef attr) {
        if (attr == null) return TypeInfo.UNKNOWN;
        ch.interlis.ili2c.metamodel.Type type = attr.getDomain();
        TypeInfo result = classifyIliTypeObj(type != null ? type : null);
        if (result == TypeInfo.UNKNOWN && type != null) {
            ch.interlis.ili2c.metamodel.Type resolved = attr.getDomainResolvingAliases();
            if (resolved != null && resolved != type) {
                result = classifyIliTypeObj(resolved);
            }
        }
        return result;
    }

    static TypeInfo classifyIliTypeObj(ch.interlis.ili2c.metamodel.Type type) {
        if (type == null) return TypeInfo.UNKNOWN;

        if (type instanceof ch.interlis.ili2c.metamodel.TextType) return TypeInfo.TEXT;
        if (type instanceof ch.interlis.ili2c.metamodel.NumericType) return TypeInfo.NUMERIC;
        if (type instanceof ch.interlis.ili2c.metamodel.NumericalType) return TypeInfo.NUMERIC;
        if (type.isBoolean()) return TypeInfo.BOOLEAN;
        if (type instanceof ch.interlis.ili2c.metamodel.EnumerationType) return TypeInfo.ENUM;
        if (type instanceof ch.interlis.ili2c.metamodel.CoordType) return TypeInfo.COORD;
        if (type instanceof ch.interlis.ili2c.metamodel.PolylineType) return TypeInfo.POLYLINE;
        if (type instanceof ch.interlis.ili2c.metamodel.AreaType) return TypeInfo.AREA;
        if (type instanceof ch.interlis.ili2c.metamodel.SurfaceOrAreaType) return TypeInfo.SURFACE;
        if (type instanceof ch.interlis.ili2c.metamodel.SurfaceType) return TypeInfo.SURFACE;
        if (type instanceof ch.interlis.ili2c.metamodel.CompositionType) return TypeInfo.STRUCTURE;
        if (type instanceof ch.interlis.ili2c.metamodel.ReferenceType) return TypeInfo.REFERENCE;

        String typeName = type.getClass().getSimpleName();
        if (typeName.contains("Date") || typeName.contains("Xml")) return TypeInfo.XML_DATE_TIME;
        return TypeInfo.UNKNOWN;
    }

    static boolean isTypeCompatible(TypeInfo sourceType, TypeInfo targetType) {
        if (sourceType == TypeInfo.UNKNOWN || targetType == TypeInfo.UNKNOWN) return true;
        if (sourceType == targetType) return true;
        return switch (sourceType) {
            case TEXT, ENUM -> targetType == TypeInfo.TEXT || targetType == TypeInfo.ENUM;
            case NUMERIC -> targetType == TypeInfo.NUMERIC || targetType == TypeInfo.TEXT;
            case BOOLEAN -> targetType == TypeInfo.BOOLEAN || targetType == TypeInfo.TEXT;
            case COORD, POLYLINE, SURFACE, AREA -> targetType == sourceType || targetType == TypeInfo.TEXT;
            case XML_DATE_TIME -> targetType == TypeInfo.XML_DATE_TIME || targetType == TypeInfo.DATE
                    || targetType == TypeInfo.TEXT;
            case DATE -> targetType == TypeInfo.DATE || targetType == TypeInfo.XML_DATE_TIME
                    || targetType == TypeInfo.TEXT;
            default -> true;
        };
    }

    // -- Source path validation --------------------------------------------

    private void checkSourcePaths(String expr, List<SourcePlan> sourcePlans,
                                   String ruleId, DiagnosticCollector diag) {
        if (expr == null || expr.isBlank()) return;
        String trimmed = expr.trim();

        // Extract all ${alias.attr} patterns
        int idx = 0;
        while ((idx = trimmed.indexOf("${", idx)) >= 0) {
            int end = trimmed.indexOf('}', idx);
            if (end < 0) break;
            String path = trimmed.substring(idx + 2, end);
            idx = end + 1;

            String[] parts = path.split("\\.", 2);
            if (parts.length < 2) continue;
            String alias = parts[0];
            String attrName = parts[1];

            SourcePlan sp = sourcePlans.stream()
                    .filter(s -> s.alias().equals(alias)).findFirst().orElse(null);
            if (sp == null) continue;
            if (sp.sourceClass() == null) continue;

            boolean found = false;
            var it = sp.sourceClass().getAttributes();
            while (it.hasNext()) {
                ch.interlis.ili2c.metamodel.Extendable ext = it.next();
                if (ext instanceof AttributeDef attrDef) {
                    if (attrName.equals(attrDef.getName())) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_SOURCE_ATTRIBUTE, Severity.ERROR,
                        "Source attribute not found: " + alias + "." + attrName,
                        ruleId, "Check the attribute name in source class"));
            }
        }
    }

    // -- Mandatory coverage check ------------------------------------------

    private void checkMandatoryCoverage(Table targetClass, TypeSystemFacade ts,
                                         List<AssignmentPlan> assignments,
                                         String ruleId, DiagnosticCollector diag) {
        var attrIt = targetClass.getAttributes();
        Set<String> assigned = new HashSet<>();
        for (AssignmentPlan ap : assignments) {
            assigned.add(ap.targetAttrName());
        }
        while (attrIt.hasNext()) {
            ch.interlis.ili2c.metamodel.Extendable ext = attrIt.next();
            if (ext instanceof AttributeDef attr) {
                var card = attr.getCardinality();
                if (card != null && card.getMinimum() > 0) {
                    if (!assigned.contains(attr.getName())) {
                        diag.add(new Diagnostic(DiagnosticCode.MAP_MANDATORY_MISSING, Severity.WARNING,
                                "Mandatory attribute not assigned: " + attr.getName()
                                        + " in " + targetClass.getName(),
                                ruleId, "Add an assignment or specifier a default value"));
                    }
                }
            }
        }
    }

    // -- Rule dependency cycle detection -----------------------------------

    private void checkRuleDependencies(List<RulePlan> plans, DiagnosticCollector diag) {
        Map<String, RulePlan> byId = new HashMap<>();
        for (RulePlan rp : plans) {
            byId.put(rp.ruleId(), rp);
        }
        for (RulePlan rp : plans) {
            for (RefPlan ref : rp.refs()) {
                if (ref.targetRuleId() != null && byId.containsKey(ref.targetRuleId())) {
                    RulePlan target = byId.get(ref.targetRuleId());
                    // Phase 19: Check target class compatibility
                    String referencingClass = getScopedName(rp.targetClass());
                    String referencedClass = getScopedName(target.targetClass());
                    if (!referencingClass.equals(referencedClass)) {
                        // Different target classes are acceptable for cross-class refs
                    }
                    for (RefPlan backRef : target.refs()) {
                        if (rp.ruleId().equals(backRef.targetRuleId())) {
                            diag.add(new Diagnostic(DiagnosticCode.MAP_CYCLIC_DEPENDENCY, Severity.ERROR,
                                    "Cyclic dependency between rules '" + rp.ruleId()
                                            + "' and '" + ref.targetRuleId() + "'",
                                    rp.ruleId(), "Break the cycle by reordering or removing one reference"));
                        }
                    }
                } else if (ref.targetRuleId() != null && !byId.containsKey(ref.targetRuleId())) {
                    diag.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_OUTPUT, Severity.ERROR,
                            "targetRuleId not found: '" + ref.targetRuleId()
                                    + "' referenced from rule '" + rp.ruleId() + "'",
                            rp.ruleId(), "Define a rule with id '" + ref.targetRuleId()
                                    + "' or correct the targetRule reference"));
                }
            }
        }
    }

    // -- Resolvers (ModelRegistry-based) -----------------------------------

    private Table resolveTargetClass(String outputId, String className,
                                      ModelRegistry modelRegistry) {
        if (className == null || className.isBlank()) return null;
        if (outputId != null && !outputId.isEmpty()) {
            TypeSystemFacade ts = modelRegistry.requireTargetTypeSystem(outputId);
            Table resolved = ts.resolveClass(className);
            if (resolved != null) return resolved;
        }
        // Fallback: try all output type systems
        for (OutputBinding binding : modelRegistry.outputsById().values()) {
            Table resolved = binding.typeSystem().resolveClass(className);
            if (resolved != null) return resolved;
        }
        return null;
    }

    // -- Helpers -----------------------------------------------------------

    private static FailPolicy parseFailPolicy(String failPolicy) {
        if (failPolicy == null) return FailPolicy.STRICT;
        if (failPolicy.equalsIgnoreCase("lenient")) return FailPolicy.LENIENT;
        if (failPolicy.equalsIgnoreCase("report_only") || failPolicy.equalsIgnoreCase("reportOnly")) return FailPolicy.REPORT_ONLY;
        return FailPolicy.STRICT;
    }

    private static CompileMode parseCompileMode(String compileMode, DiagnosticCollector diag) {
        if (compileMode == null) return CompileMode.STRICT;
        if (compileMode.equalsIgnoreCase("strict")) return CompileMode.STRICT;
        if (compileMode.equalsIgnoreCase("compatible")) return CompileMode.COMPATIBLE;
        if (compileMode.equalsIgnoreCase("report")) return CompileMode.REPORT;
        diag.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_COMPILE_MODE, Severity.WARNING,
                "Unknown compileMode: " + compileMode,
                null, "Valid values: strict, compatible, report"));
        return CompileMode.STRICT;
    }

    // -- Structural validation (unchanged from Phase 1) --------------------

    private void validateVersion(JobConfig config, DiagnosticCollector diag) {
        if (config.version < 1) {
            diag.add(new Diagnostic(
                    DiagnosticCode.MAP_VERSION,
                    Severity.ERROR,
                    "Mapping file must declare 'version: 1' or higher",
                    null,
                    "Add 'version: 1' at the top of the mapping YAML"
            ));
        }
    }

    private void validateOutputs(JobConfig config, DiagnosticCollector diag) {
        for (JobConfig.OutputSpec output : config.job.outputs) {
            if (output.id == null || output.id.isBlank()) {
                diag.add(new Diagnostic(
                        DiagnosticCode.MAP_MISSING_ID,
                        Severity.ERROR,
                        "Output id is required",
                        null,
                        "Add an 'id' to each output in job.outputs"
                ));
            }
        }
    }

    private void validateRules(JobConfig config, DiagnosticCollector diag) {
        validateRulesStructurally(config, diag);
    }

    private void validateRulesStructurally(JobConfig config, DiagnosticCollector diag) {
        Set<String> ruleIds = new HashSet<>();
        Set<String> outputIds = new HashSet<>();
        for (JobConfig.OutputSpec o : config.job.outputs) {
            if (o.id != null && !o.id.isBlank()) outputIds.add(o.id);
        }
        Set<String> inputIds = new HashSet<>();
        for (JobConfig.InputSpec i : config.job.inputs) {
            if (i.id != null && !i.id.isBlank()) inputIds.add(i.id);
        }

        for (JobConfig.RuleSpec rule : config.mapping.rules) {
            validateRuleId(rule, ruleIds, diag);
            validateRuleTarget(rule, outputIds, diag);
            validateRuleSources(rule, inputIds, outputIds, diag);
            validateIdentityKeysStructurally(rule, diag);
        }
    }

    private void validateRuleId(JobConfig.RuleSpec rule, Set<String> seenIds, DiagnosticCollector diag) {
        if (rule.id == null || rule.id.isBlank()) {
            diag.add(new Diagnostic(DiagnosticCode.MAP_MISSING_ID, Severity.ERROR,
                    "Rule is missing required 'id' field",
                    rule.getEffectiveTargetClass(),
                    "Add an 'id' to each rule"));
        } else if (!seenIds.add(rule.id)) {
            diag.add(new Diagnostic(DiagnosticCode.MAP_DUPLICATE_ID, Severity.ERROR,
                    "Duplicate rule id: " + rule.id,
                    rule.getEffectiveTargetClass(),
                    "Rule ids must be unique within a mapping file"));
        }
    }

    private void validateRuleTarget(JobConfig.RuleSpec rule, Set<String> outputIds, DiagnosticCollector diag) {
        String targetClass = rule.getEffectiveTargetClass();
        if (targetClass == null || targetClass.isBlank()) {
            diag.add(new Diagnostic(DiagnosticCode.MAP_MISSING_TARGET_CLASS, Severity.ERROR,
                    "Rule is missing target class",
                    rule.id,
                    "Add 'target.class' or 'targetClass' to the rule"));
        }
        String targetOutput = rule.getEffectiveTargetOutput();
        if (targetOutput != null && !targetOutput.isBlank() && !outputIds.isEmpty()
                && !outputIds.contains(targetOutput)) {
            diag.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_OUTPUT, Severity.ERROR,
                    "Rule references unknown output: " + targetOutput,
                    rule.id,
                    "Ensure the output id exists in job.outputs"));
        }
    }

    private void validateRuleSources(JobConfig.RuleSpec rule, Set<String> inputIds,
                                      Set<String> outputIds, DiagnosticCollector diag) {
        Set<String> aliases = new HashSet<>();
        for (JobConfig.SourceSpec source : rule.sources) {
            if (source.alias == null || source.alias.isBlank()) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_MISSING_ALIAS, Severity.ERROR,
                        "Source is missing required 'alias' field in rule " + rule.id,
                        rule.id, "Add an 'alias' to each source"));
                continue;
            }
            if (!aliases.add(source.alias)) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_DUPLICATE_ALIAS, Severity.ERROR,
                        "Duplicate source alias '" + source.alias + "' in rule " + rule.id,
                        rule.id, "Source aliases must be unique within a rule"));
            }
            if (source.clazz == null || source.clazz.isBlank()) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_MISSING_SOURCE_CLASS, Severity.ERROR,
                        "Source '" + source.alias + "' is missing 'class' field in rule " + rule.id,
                        rule.id, "Add 'class' to each source definition"));
            }
            List<String> sourceInputs = source.getInputIds();
            if (sourceInputs.isEmpty() || sourceInputs.stream().allMatch(String::isBlank)) {
                diag.add(new Diagnostic(DiagnosticCode.MAP_MISSING_INPUT, Severity.ERROR,
                        "Source '" + source.alias + "' is missing 'input' field in rule " + rule.id,
                        rule.id, "Add 'input' or 'inputs' to each source definition"));
            }
            for (String inputId : sourceInputs) {
                if (inputId != null && !inputId.isBlank() && !inputIds.isEmpty()
                        && !inputIds.contains(inputId)) {
                    diag.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_INPUT, Severity.ERROR,
                            "Source '" + source.alias + "' references unknown input: " + inputId,
                            rule.id, "Available inputs: " + inputIds));
                }
            }
        }
    }

    static String getScopedName(Table table) {
        if (table == null) return null;
        Container container = table.getContainer();
        if (container instanceof Topic topic) {
            Container modelContainer = topic.getContainer();
            if (modelContainer instanceof Model model) {
                return model.getName() + "." + topic.getName() + "." + table.getName();
            }
        }
        return table.getName();
    }

    // -- CompileResult -----------------------------------------------------

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
