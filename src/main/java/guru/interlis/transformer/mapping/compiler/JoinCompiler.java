package guru.interlis.transformer.mapping.compiler;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.expr.FunctionCallExpr;
import guru.interlis.transformer.expr.PathExpr;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.CompiledExpression;
import guru.interlis.transformer.mapping.plan.ExpressionCompileContext;
import guru.interlis.transformer.mapping.plan.JoinCardinality;
import guru.interlis.transformer.mapping.plan.JoinPlan;
import guru.interlis.transformer.mapping.plan.JoinType;
import guru.interlis.transformer.mapping.plan.SourcePlan;
import guru.interlis.transformer.mapping.plan.TypeInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class JoinCompiler {

    List<JoinPlan> compileJoins(
            JobConfig.RuleSpec rule,
            List<SourcePlan> sourcePlans,
            Map<String, SourcePlan> sourcesByAlias,
            String ruleId,
            CompilerContext ctx) {
        if (rule.joins == null || rule.joins.isEmpty()) {
            return List.of();
        }
        if (rule.joins.size() > 1) {
            ctx.diagnostics()
                    .add(new Diagnostic(
                            DiagnosticCode.MAP_UNSUPPORTED_FEATURE,
                            Severity.ERROR,
                            "Only one join per rule is currently supported. Found " + rule.joins.size()
                                    + " joins. Rule: " + ruleId,
                            ruleId,
                            "Split the mapping into multiple rules or wait for multi-join support"));
            return List.of();
        }
        List<JoinPlan> result = new ArrayList<>();
        for (int i = 0; i < rule.joins.size(); i++) {
            JobConfig.JoinSpec js = rule.joins.get(i);
            String joinId = ruleId + "-join-" + i;

            SourcePlan leftSp = sourcesByAlias.get(js.left);
            SourcePlan rightSp = sourcesByAlias.get(js.right);
            if (leftSp == null || rightSp == null) {
                continue;
            }

            Map<String, SourcePlan> joinAliases = new HashMap<>();
            joinAliases.put(js.left, leftSp);
            joinAliases.put(js.right, rightSp);

            ExpressionCompileContext exprCtx = new ExpressionCompileContext(
                    joinId, joinAliases, TypeInfo.BOOLEAN, ctx.functionRegistry(), ctx.enumMaps());
            CompiledExpression condition = ctx.expressionCompiler().compile(js.on, exprCtx, ctx.diagnostics());

            if (condition == null || !isEquiJoin(condition)) {
                ctx.diagnostics()
                        .add(new Diagnostic(
                                DiagnosticCode.MAP_JOIN_NON_EQUI,
                                Severity.ERROR,
                                "Join condition is not an indexable equi-join. Only 'left.attr = right.attr' is supported. Rule: "
                                        + ruleId,
                                joinId,
                                "Use a simple equality between attributes from different sources"));
                continue;
            }

            JoinType type = JoinType.INNER;
            if (js.type != null && js.type.equalsIgnoreCase("left")) {
                type = JoinType.LEFT;
            }

            JoinCardinality cardinality = JoinCardinality.MANY_TO_MANY;

            result.add(new JoinPlan(joinId, type, leftSp, rightSp, condition, cardinality));
        }
        return result;
    }

    static boolean isEquiJoin(CompiledExpression condition) {
        if (!(condition.ast() instanceof FunctionCallExpr call)) {
            return false;
        }
        if (!call.functionName().equals("eq")) {
            return false;
        }
        if (call.arguments().size() != 2) {
            return false;
        }
        if (!(call.arguments().get(0) instanceof PathExpr leftPath)) {
            return false;
        }
        if (!(call.arguments().get(1) instanceof PathExpr rightPath)) {
            return false;
        }
        return !leftPath.alias().equals(rightPath.alias());
    }
}
