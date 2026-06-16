package guru.interlis.transformer.mapping.compiler;

import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.CompiledExpression;
import guru.interlis.transformer.mapping.plan.ExpressionCompileContext;
import guru.interlis.transformer.mapping.plan.LossPlan;
import guru.interlis.transformer.mapping.plan.SourcePlan;
import guru.interlis.transformer.mapping.plan.TypeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class LossCompiler {

    List<LossPlan> compileLosses(JobConfig.RuleSpec rule,
                                  Map<String, SourcePlan> sourcesByAlias,
                                  String ruleId, CompilerContext ctx) {
        if (rule.losses == null || rule.losses.isEmpty()) {
            return List.of();
        }
        List<LossPlan> result = new ArrayList<>();
        for (JobConfig.LossSpec loss : rule.losses) {
            CompiledExpression when = null;
            if (loss.when != null && !loss.when.isBlank()) {
                ExpressionCompileContext exprCtx = new ExpressionCompileContext(ruleId,
                        sourcesByAlias, TypeInfo.BOOLEAN, ctx.functionRegistry(), ctx.enumMaps());
                when = ctx.expressionCompiler().compile(loss.when, exprCtx, ctx.diagnostics());
            }
            result.add(new LossPlan(loss.sourcePath, loss.reasonCode, loss.description, when));
        }
        return result;
    }
}
