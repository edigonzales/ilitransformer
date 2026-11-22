package guru.interlis.transformer.mapping.compiler;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.expr.ExpressionParseException;
import guru.interlis.transformer.expr.ExpressionParser;
import guru.interlis.transformer.expr.ExpressionTypeChecker;
import guru.interlis.transformer.expr.PathExpr;
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

    List<LossPlan> compileLosses(
            JobConfig.RuleSpec rule, Map<String, SourcePlan> sourcesByAlias, String ruleId, CompilerContext ctx) {
        if (rule.losses == null || rule.losses.isEmpty()) {
            return List.of();
        }
        List<LossPlan> result = new ArrayList<>();
        for (JobConfig.LossSpec loss : rule.losses) {
            if (rule.target != null) {
                validateSourcePath(loss.sourcePath, sourcesByAlias, ruleId, ctx);
            }
            CompiledExpression when = null;
            if (loss.when != null && !loss.when.isBlank()) {
                ExpressionCompileContext exprCtx = new ExpressionCompileContext(
                        ruleId, sourcesByAlias, TypeInfo.BOOLEAN, ctx.functionRegistry(), ctx.enumMaps());
                when = ctx.expressionCompiler().compile(loss.when, exprCtx, ctx.diagnostics());
            }
            result.add(new LossPlan(loss.sourcePath, loss.reasonCode, loss.description, when));
        }
        return result;
    }

    private void validateSourcePath(
            String sourcePath, Map<String, SourcePlan> sourcesByAlias, String ruleId, CompilerContext ctx) {
        if (sourcePath == null || sourcePath.isBlank()) return;
        try {
            if (!(ExpressionParser.parse(sourcePath.strip()) instanceof PathExpr path)) return;
            String attribute = path.attributeName();
            if (attribute == null && sourcesByAlias.containsKey(path.alias())) {
                ctx.diagnostics()
                        .add(new Diagnostic(
                                DiagnosticCode.MAP_UNSUPPORTED_FEATURE,
                                Severity.ERROR,
                                "A loss of an entire source class requires a rule without a target",
                                ruleId,
                                null));
            } else if (attribute == null || !attribute.contains(".")) {
                var exprCtx = new ExpressionCompileContext(
                        ruleId, sourcesByAlias, TypeInfo.UNKNOWN, ctx.functionRegistry(), ctx.enumMaps());
                new ExpressionTypeChecker(exprCtx, ctx.diagnostics()).check(path);
            }
        } catch (ExpressionParseException ignored) {
            // sourcePath is report metadata. Only simple source paths need model validation.
        }
    }
}
