package guru.interlis.transformer.mapping.compiler;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.expr.ExpressionCompiler;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.plan.AssignmentPlan;
import guru.interlis.transformer.mapping.plan.CompiledExpression;
import guru.interlis.transformer.mapping.plan.ExpressionCompileContext;
import guru.interlis.transformer.mapping.plan.SourcePlan;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.model.TypeSystemFacade;

import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Table;

import java.util.Map;

final class AssignmentCompiler {

    AssignmentPlan compileAssignment(
            JobConfig.AttributeMapping attr,
            Table targetClass,
            TypeSystemFacade targetTs,
            Map<String, SourcePlan> sourcesByAlias,
            String ruleId,
            CompilerContext ctx) {
        String targetName = attr.target;
        String expr = attr.expr;

        AttributeDef targetAttr = null;
        if (targetTs != null) {
            targetAttr = targetTs.findAttribute(targetClass, targetName);
            if (targetAttr == null) {
                ctx.diagnostics()
                        .add(new Diagnostic(
                                DiagnosticCode.MAP_UNKNOWN_TARGET_ATTRIBUTE,
                                Severity.ERROR,
                                "Target attribute not found: " + targetName + " in class " + targetClass.getName(),
                                ruleId,
                                "Check the target attribute name"));
            }
        }

        TypeInfo expectedTargetType =
                targetAttr != null ? ExpressionCompiler.classifyIliAttr(targetAttr) : TypeInfo.UNKNOWN;

        ExpressionCompileContext compileCtx = new ExpressionCompileContext(
                ruleId, sourcesByAlias, expectedTargetType, ctx.functionRegistry(), ctx.enumMaps());

        CompiledExpression compiled = ctx.expressionCompiler().compile(expr, compileCtx, ctx.diagnostics());

        if (targetAttr != null && compiled.resultType() != TypeInfo.UNKNOWN) {
            TypeInfo targetType = ExpressionCompiler.classifyIliAttr(targetAttr);
            if (!CompileUtils.isTypeCompatible(compiled.resultType(), targetType)) {
                ctx.diagnostics()
                        .add(new Diagnostic(
                                DiagnosticCode.MAP_TYPE_MISMATCH,
                                Severity.WARNING,
                                "Type mismatch: expression '" + expr + "' produces " + compiled.resultType()
                                        + " but target '" + targetName + "' expects " + targetType,
                                ruleId,
                                "Add a type conversion or check the expression"));
            }
        }

        return new AssignmentPlan(targetName, targetAttr, compiled);
    }

    AssignmentPlan compileDefaultAssignment(
            String targetName,
            String expr,
            Table targetClass,
            TypeSystemFacade targetTs,
            Map<String, SourcePlan> sourcesByAlias,
            String ruleId,
            CompilerContext ctx) {
        AttributeDef targetAttr = null;
        if (targetTs != null) {
            targetAttr = targetTs.findAttribute(targetClass, targetName);
        }

        TypeInfo expectedTargetType =
                targetAttr != null ? ExpressionCompiler.classifyIliAttr(targetAttr) : TypeInfo.UNKNOWN;

        ExpressionCompileContext compileCtx = new ExpressionCompileContext(
                ruleId, sourcesByAlias, expectedTargetType, ctx.functionRegistry(), ctx.enumMaps());

        CompiledExpression compiled = ctx.expressionCompiler().compile(expr, compileCtx, ctx.diagnostics());

        if (targetAttr != null && compiled.resultType() != TypeInfo.UNKNOWN) {
            TypeInfo targetType = ExpressionCompiler.classifyIliAttr(targetAttr);
            if (!CompileUtils.isTypeCompatible(compiled.resultType(), targetType)) {
                ctx.diagnostics()
                        .add(new Diagnostic(
                                DiagnosticCode.MAP_TYPE_MISMATCH,
                                Severity.WARNING,
                                "Default expression '" + expr + "' for '" + targetName
                                        + "' produces " + compiled.resultType()
                                        + " but target expects " + targetType,
                                ruleId,
                                "Check the default expression type"));
            }
        }

        return new AssignmentPlan(targetName, targetAttr, compiled);
    }
}
