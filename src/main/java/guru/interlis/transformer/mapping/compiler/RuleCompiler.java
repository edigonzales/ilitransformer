package guru.interlis.transformer.mapping.compiler;

import ch.interlis.ili2c.metamodel.Table;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.expr.ExpressionCompiler;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.*;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.OidStrategy;

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
        String ruleId = rule.id;
        String targetOutput = rule.getEffectiveTargetOutput();
        String targetClassName = rule.getEffectiveTargetClass();

        Table targetClass = CompileUtils.resolveTargetClass(targetOutput, targetClassName, ctx.modelRegistry());
        if (targetClass == null) {
            ctx.diagnostics().add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_TARGET_CLASS, Severity.ERROR,
                    "Target class not found in model: " + targetClassName,
                    ruleId, "Check the target class name and model registration"));
            return null;
        }
        if (targetClass.isAbstract()) {
            ctx.diagnostics().add(new Diagnostic(DiagnosticCode.MAP_ABSTRACT_TARGET_CLASS, Severity.ERROR,
                    "Target class is abstract: " + targetClassName,
                    ruleId, "Choose a concrete target class or instantiate via a specific subclass"));
        }
        if (!targetClass.isIdentifiable()) {
            ctx.diagnostics().add(new Diagnostic(DiagnosticCode.MAP_NON_TRANSFERABLE_TARGET, Severity.WARNING,
                    "Target class is not identifiable (possibly a view): " + targetClassName,
                    ruleId, "Views cannot be written as transfer objects"));
        }

        TypeSystemFacade targetTs = targetOutput != null && !targetOutput.isEmpty()
                ? ctx.modelRegistry().requireTargetTypeSystem(targetOutput) : null;

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
        for (JobConfig.AttributeMapping attr : rule.getAllAttributes()) {
            if (attr.target == null || attr.target.isBlank()) continue;
            if (!assignedTargets.add(attr.target)) {
                ctx.diagnostics().add(new Diagnostic(DiagnosticCode.MAP_DUPLICATE_TARGET_ASSIGN, Severity.ERROR,
                        "Target attribute assigned multiple times: " + attr.target,
                        ruleId, "Remove duplicate assignment or use an explicit merge policy"));
            }

            AssignmentPlan ap = assignmentCompiler.compileAssignment(attr, targetClass, targetTs,
                    sourcesByAlias, ruleId, ctx);
            if (ap != null) {
                assignmentPlans.add(ap);
            }
        }

        if (rule.defaults != null && !rule.defaults.isEmpty()) {
            for (var entry : rule.defaults.entrySet()) {
                if (!assignedTargets.contains(entry.getKey())) {
                    AssignmentPlan dp = assignmentCompiler.compileDefaultAssignment(entry.getKey(),
                            entry.getValue(), targetClass, targetTs, sourcesByAlias, ruleId, ctx);
                    if (dp != null) {
                        assignedTargets.add(entry.getKey());
                        assignmentPlans.add(dp);
                    }
                }
            }
        }

        if (ctx.config().mapping.defaults != null && !ctx.config().mapping.defaults.isEmpty()) {
            for (var entry : ctx.config().mapping.defaults.entrySet()) {
                if (!assignedTargets.contains(entry.getKey())) {
                    AssignmentPlan dp = assignmentCompiler.compileDefaultAssignment(entry.getKey(),
                            entry.getValue(), targetClass, targetTs, sourcesByAlias, ruleId, ctx);
                    if (dp != null) {
                        assignedTargets.add(entry.getKey());
                        assignmentPlans.add(dp);
                    }
                }
            }
        }

        if (targetTs != null) {
            mandatoryCoverageValidator.checkMandatoryCoverage(targetClass, targetTs, assignmentPlans,
                    ruleId, ctx.diagnostics());
        }

        List<RefPlan> refPlans = new ArrayList<>();
        for (JobConfig.RefMapping ref : rule.getEffectiveRefs()) {
            RefPlan rp = refCompiler.compileRef(ref, targetClass, targetTs, sourcePlans, ruleId, ctx);
            if (rp != null) {
                refPlans.add(rp);
            }
        }

        List<BagPlan> bagPlans = bagCompiler.compileBags(rule, sourcePlans, sourcesByAlias,
                targetClass, targetTs, ruleId, ctx, null);

        List<LossPlan> lossPlans = lossCompiler.compileLosses(rule, sourcesByAlias, ruleId, ctx);

        List<String> identitySourceKeys = identityCompiler.compileIdentityKeys(rule, sourcePlans, ctx);

        List<JoinPlan> joinPlans = joinCompiler.compileJoins(rule, sourcePlans, sourcesByAlias, ruleId, ctx);

        List<CreatePlan> createPlans = createCompiler.compileCreates(rule, targetOutput, sourcePlans,
                sourcesByAlias, ruleId, ctx);

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
                createPlans
        );
    }
}
