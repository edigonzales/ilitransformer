package guru.interlis.transformer.mapping.compiler;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.io.FormatIdResolver;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.*;
import guru.interlis.transformer.model.TypeSystemFacade;

import ch.interlis.ili2c.metamodel.AbstractClassDef;
import ch.interlis.ili2c.metamodel.AssociationDef;
import ch.interlis.ili2c.metamodel.Table;

import java.util.*;

final class RuleCompiler {

    private final SourceCompiler sourceCompiler = new SourceCompiler();
    private final AssignmentCompiler assignmentCompiler = new AssignmentCompiler();
    private final RefCompiler refCompiler = new RefCompiler();
    private final BagCompiler bagCompiler = new BagCompiler();
    private final JoinCompiler joinCompiler = new JoinCompiler();
    private final CreateCompiler createCompiler = new CreateCompiler();
    private final IdentityCompiler identityCompiler = new IdentityCompiler();
    private final LossCompiler lossCompiler = new LossCompiler();
    private final MandatoryCoverageValidator mandatoryCoverageValidator = new MandatoryCoverageValidator();

    RulePlan compileRule(JobConfig.RuleSpec rule, CompilerContext ctx) {
        if (rule.target == null) {
            return compileClassLoss(rule, ctx);
        }
        String ruleId = rule.id;
        String targetOutput = rule.target.output;
        String targetClassName = rule.target.clazz;

        AbstractClassDef<?> targetClass =
                CompileUtils.resolveTargetClass(targetOutput, targetClassName, ctx.modelRegistry());
        if (targetClass == null) {
            ctx.diagnostics()
                    .add(new Diagnostic(
                            DiagnosticCode.MAP_UNKNOWN_TARGET_CLASS,
                            Severity.ERROR,
                            "Target class not found in model: " + targetClassName,
                            ruleId,
                            "Check the target class name and model registration"));
            return null;
        }
        if (targetClass.isAbstract()) {
            ctx.diagnostics()
                    .add(new Diagnostic(
                            DiagnosticCode.MAP_ABSTRACT_TARGET_CLASS,
                            Severity.ERROR,
                            "Target class is abstract: " + targetClassName,
                            ruleId,
                            "Choose a concrete target class or instantiate via a specific subclass"));
        }
        if (targetClass instanceof AssociationDef association) {
            String problem = TypeSystemFacade.unsupportedAssociation(association);
            var output = ctx.modelRegistry().outputsById().get(targetOutput);
            if (problem == null && output != null && !FormatIdResolver.isXtf(output.format())) {
                problem = "Standalone association targets require XTF output: " + targetClassName;
            }
            if (problem != null) {
                ctx.diagnostics()
                        .add(new Diagnostic(
                                DiagnosticCode.MAP_UNSUPPORTED_FEATURE, Severity.ERROR, problem, ruleId, null));
                return null;
            }
        } else if (targetClass instanceof Table table && !table.isIdentifiable()) {
            ctx.diagnostics()
                    .add(new Diagnostic(
                            DiagnosticCode.MAP_NON_TRANSFERABLE_TARGET,
                            Severity.WARNING,
                            "Target class is not identifiable (possibly a view): " + targetClassName,
                            ruleId,
                            "Views cannot be written as transfer objects"));
        }

        TypeSystemFacade targetTs = targetOutput != null && !targetOutput.isEmpty()
                ? ctx.modelRegistry().requireTargetTypeSystem(targetOutput)
                : null;

        List<SourcePlan> sourcePlans = new ArrayList<>();
        for (JobConfig.SourceSpec src : rule.sources) {
            SourcePlan sp = sourceCompiler.compileSource(src, ruleId, ctx);
            if (sp != null) {
                sourcePlans.add(sp);
            }
        }
        if (sourcePlans.isEmpty()) {
            return null;
        }

        Map<String, SourcePlan> sourcesByAlias = new HashMap<>();
        for (SourcePlan sp : sourcePlans) {
            sourcesByAlias.put(sp.alias(), sp);
        }

        sourcePlans = sourceCompiler.compileWhereFilters(sourcePlans, sourcesByAlias, ruleId, ctx, rule.sources);
        sourcesByAlias = new HashMap<>();
        for (SourcePlan sp : sourcePlans) {
            sourcesByAlias.put(sp.alias(), sp);
        }

        CompiledExpression predicate = null;
        if (rule.where != null && !rule.where.isBlank()) {
            ExpressionCompileContext predCtx = new ExpressionCompileContext(
                    ruleId, sourcesByAlias, TypeInfo.BOOLEAN, ctx.functionRegistry(), ctx.enumMaps());
            predicate = ctx.expressionCompiler().compile(rule.where, predCtx, ctx.diagnostics());
        }

        List<AssignmentPlan> assignmentPlans = new ArrayList<>();
        Set<String> assignedTargets = new HashSet<>();
        if (rule.assign != null) {
            for (var entry : rule.assign.entrySet()) {
                String name = entry.getKey();
                if (name == null || name.isBlank()) continue;
                assignedTargets.add(name);
                AssignmentPlan assignment = assignmentCompiler.compileAssignment(
                        name, entry.getValue(), targetClass, targetTs, sourcesByAlias, ruleId, ctx);
                if (assignment != null) {
                    assignmentPlans.add(assignment);
                }
            }
        }

        if (rule.defaults != null && !rule.defaults.isEmpty()) {
            for (var entry : rule.defaults.entrySet()) {
                if (!assignedTargets.contains(entry.getKey())) {
                    AssignmentPlan dp = assignmentCompiler.compileDefaultAssignment(
                            entry.getKey(), entry.getValue(), targetClass, targetTs, sourcesByAlias, ruleId, ctx);
                    if (dp != null) {
                        assignedTargets.add(entry.getKey());
                        assignmentPlans.add(dp);
                    }
                }
            }
        }

        if (ctx.globalDefaults() != null && !ctx.globalDefaults().isEmpty()) {
            for (var entry : ctx.globalDefaults().entrySet()) {
                if (!assignedTargets.contains(entry.getKey())) {
                    AssignmentPlan dp = assignmentCompiler.compileDefaultAssignment(
                            entry.getKey(), entry.getValue(), targetClass, targetTs, sourcesByAlias, ruleId, ctx);
                    if (dp != null) {
                        assignedTargets.add(entry.getKey());
                        assignmentPlans.add(dp);
                    }
                }
            }
        }

        List<RefPlan> refPlans = new ArrayList<>();
        if (rule.refs != null) {
            for (JobConfig.RefMapping ref : rule.refs) {
                refPlans.add(refCompiler.compileRef(ref, targetClass, targetTs, sourcePlans, ruleId, ctx));
            }
        }

        List<BagPlan> bagPlans =
                bagCompiler.compileBags(rule, sourcePlans, sourcesByAlias, targetClass, targetTs, ruleId, ctx, null);

        if (targetTs != null) {
            mandatoryCoverageValidator.checkMandatoryCoverage(
                    targetClass, assignmentPlans, refPlans, bagPlans, ruleId, ctx.diagnostics());
        }

        List<LossPlan> lossPlans = lossCompiler.compileLosses(rule, sourcesByAlias, ruleId, ctx);

        List<String> identitySourceKeys = identityCompiler.compileIdentityKeys(rule, sourcePlans, ctx);

        List<JoinPlan> joinPlans = joinCompiler.compileJoins(rule, sourcePlans, sourcesByAlias, ruleId, ctx);

        List<CreatePlan> createPlans =
                createCompiler.compileCreates(rule, targetOutput, sourcePlans, sourcesByAlias, ruleId, ctx);

        return new RulePlan(
                ruleId,
                targetOutput != null ? targetOutput : "",
                targetClass,
                sourcePlans,
                assignmentPlans,
                refPlans,
                bagPlans,
                lossPlans,
                identitySourceKeys,
                predicate,
                joinPlans,
                createPlans);
    }

    private RulePlan compileClassLoss(JobConfig.RuleSpec rule, CompilerContext ctx) {
        if (StructuralValidator.classLossProblem(rule) != null) return null;
        SourcePlan source = sourceCompiler.compileSource(rule.sources.getFirst(), rule.id, ctx);
        if (source == null || source.sourceClass() == null) return null;

        String className = TypeSystemFacade.getScopedName(source.sourceClass());
        var inputs = ctx.modelRegistry().inputsById();
        var inputIds = source.inputIds().stream().distinct().toList();
        for (String inputId : inputIds) {
            var input = inputs.get(inputId);
            var cls = input == null || input.typeSystem() == null
                    ? null
                    : input.typeSystem().resolveClass(className);
            boolean transferable =
                    switch (cls) {
                        case Table table ->
                            table.isIdentifiable() && !table.isImplicit() && !table.isIli1LineAttrStruct();
                        case AssociationDef association -> TypeSystemFacade.unsupportedAssociation(association) == null;
                        case null, default -> false;
                    };
            if (!transferable || cls.isAbstract()) {
                ctx.diagnostics()
                        .add(new Diagnostic(
                                DiagnosticCode.MAP_UNSUPPORTED_FEATURE,
                                Severity.ERROR,
                                "Class loss requires a concrete transfer class: " + className + " in input " + inputId,
                                rule.id,
                                null));
                return null;
            }
        }

        return new RulePlan(
                rule.id,
                "",
                null,
                List.of(new SourcePlan(source.alias(), source.sourceClass(), inputIds, null)),
                List.of(),
                List.of(),
                List.of(),
                lossCompiler.compileLosses(rule, Map.of(source.alias(), source), rule.id, ctx),
                List.of(),
                null,
                List.of(),
                List.of());
    }
}
