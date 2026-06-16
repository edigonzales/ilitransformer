package guru.interlis.transformer.expr;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.plan.CompiledExpression;
import guru.interlis.transformer.mapping.plan.ExpressionCompileContext;
import guru.interlis.transformer.mapping.plan.TypeInfo;

import java.util.Set;

public final class ExpressionCompiler {

    public CompiledExpression compile(
            String expression, ExpressionCompileContext context, DiagnosticCollector diagnostics) {
        if (expression == null || expression.isBlank()) {
            return new CompiledExpression(
                    expression, new LiteralExpr(NullValue.INSTANCE), TypeInfo.UNKNOWN, true, Set.of());
        }

        String trimmed = expression.trim();

        Expression ast;
        try {
            ast = ExpressionParser.parse(trimmed);
        } catch (ExpressionParseException e) {
            if (diagnostics != null) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.EXPR_SYNTAX,
                        Severity.ERROR,
                        "Expression parse error: " + e.getMessage(),
                        context.ruleId(),
                        expression));
            }
            return new CompiledExpression(
                    trimmed, new LiteralExpr(NullValue.INSTANCE), TypeInfo.UNKNOWN, false, Set.of());
        }

        ExpressionTypeChecker checker = new ExpressionTypeChecker(context, diagnostics);
        ExpressionTypeChecker.Result result = checker.check(ast);
        boolean deterministic = isDeterministic(ast, context.functionRegistry());

        return new CompiledExpression(trimmed, ast, result.resultType(), deterministic, result.resolvedPaths());
    }

    private boolean isDeterministic(Expression expr, FunctionRegistry registry) {
        return switch (expr) {
            case LiteralExpr lit -> true;
            case PathExpr path -> true;
            case FunctionCallExpr call -> {
                var def = registry.resolve(call.functionName());
                if (def.isPresent() && !def.get().deterministic()) yield false;
                yield call.arguments().stream().allMatch(a -> isDeterministic(a, registry));
            }
            case ConditionalExpr cond ->
                isDeterministic(cond.condition(), registry)
                        && isDeterministic(cond.thenExpr(), registry)
                        && isDeterministic(cond.elseExpr(), registry);
        };
    }

    public static TypeInfo classifyIliAttr(ch.interlis.ili2c.metamodel.AttributeDef attr) {
        if (attr == null) return TypeInfo.UNKNOWN;
        ch.interlis.ili2c.metamodel.Type type = attr.getDomain();
        TypeInfo result = classifyIliType(type);
        if (result == TypeInfo.UNKNOWN && type != null) {
            ch.interlis.ili2c.metamodel.Type resolved = attr.getDomainResolvingAliases();
            if (resolved != null && resolved != type) {
                result = classifyIliType(resolved);
            }
        }
        return result;
    }

    static TypeInfo classifyIliType(ch.interlis.ili2c.metamodel.Type type) {
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
}
