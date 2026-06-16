package guru.interlis.transformer.mapping.compiler;

import ch.interlis.ili2c.metamodel.Table;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.expr.ExpressionCompiler;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.AssignmentPlan;
import guru.interlis.transformer.mapping.plan.CompiledExpression;
import guru.interlis.transformer.mapping.plan.CreatePlan;
import guru.interlis.transformer.mapping.plan.ExpressionCompileContext;
import guru.interlis.transformer.mapping.plan.IdentityPlan;
import guru.interlis.transformer.mapping.plan.SourcePlan;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.OidStrategy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class CreateCompiler {

    List<CreatePlan> compileCreates(JobConfig.RuleSpec rule, String targetOutput,
                                     List<SourcePlan> sourcePlans,
                                     Map<String, SourcePlan> sourcesByAlias,
                                     String ruleId, CompilerContext ctx) {
        if (rule.create == null || rule.create.isEmpty()) {
            return List.of();
        }
        List<CreatePlan> result = new ArrayList<>();
        for (int i = 0; i < rule.create.size(); i++) {
            JobConfig.CreateSpec cs = rule.create.get(i);
            String createId = ruleId + "-create-" + i;

            String targetClassName = cs.clazz;
            Table targetClass = CompileUtils.resolveTargetClass(targetOutput, targetClassName, ctx.modelRegistry());
            if (targetClass == null) {
                ctx.diagnostics().add(new Diagnostic(DiagnosticCode.MAP_CREATE_UNKNOWN_CLASS, Severity.ERROR,
                        "Create target class not found in model: " + targetClassName,
                        createId, "Check the class name and model registration"));
                continue;
            }

            TypeSystemFacade targetTs = targetOutput != null && !targetOutput.isEmpty()
                    ? ctx.modelRegistry().requireTargetTypeSystem(targetOutput) : null;

            List<AssignmentPlan> assignments = new ArrayList<>();
            Set<String> assignedTargets = new HashSet<>();
            if (cs.assign != null) {
                for (var entry : cs.assign.entrySet()) {
                    if (entry.getKey() == null || entry.getKey().isBlank()) continue;
                    if (!assignedTargets.add(entry.getKey())) {
                        ctx.diagnostics().add(new Diagnostic(DiagnosticCode.MAP_DUPLICATE_TARGET_ASSIGN, Severity.ERROR,
                                "Target attribute assigned multiple times in create: " + entry.getKey(),
                                createId, "Remove duplicate assignment"));
                    }
                    ExpressionCompileContext exprCtx = new ExpressionCompileContext(
                            createId, sourcesByAlias, null, ctx.functionRegistry(), ctx.enumMaps());
                    CompiledExpression expr = ctx.expressionCompiler().compile(entry.getValue(), exprCtx, ctx.diagnostics());
                    if (expr != null) {
                        AssignmentPlan ap = new AssignmentPlan(entry.getKey(), null, expr);
                        assignments.add(ap);
                    }
                }
            }

            OidStrategy oidStrategy = ctx.oidStrategy() != null ? ctx.oidStrategy() : OidStrategy.INTEGER;
            IdentityPlan identity = new IdentityPlan(oidStrategy, ctx.oidNamespace(), List.of());

            result.add(new CreatePlan(createId, targetOutput != null ? targetOutput : "",
                    targetClass, Optional.empty(), assignments, List.of(),
                    List.of(), identity));
        }
        return result;
    }
}
