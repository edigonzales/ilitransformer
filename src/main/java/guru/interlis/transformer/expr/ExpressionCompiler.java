package guru.interlis.transformer.expr;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.plan.CompiledExpression;
import guru.interlis.transformer.mapping.plan.ExpressionCompileContext;
import guru.interlis.transformer.mapping.plan.ResolvedPath;
import guru.interlis.transformer.mapping.plan.SourcePlan;
import guru.interlis.transformer.mapping.plan.TypeInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ExpressionCompiler {

    public CompiledExpression compile(
            String expression,
            ExpressionCompileContext context,
            DiagnosticCollector diagnostics
    ) {
        if (expression == null || expression.isBlank()) {
            return new CompiledExpression(expression, new LiteralExpr(NullValue.INSTANCE),
                    TypeInfo.UNKNOWN, true, Set.of());
        }

        String trimmed = expression.trim();

        Expression ast;
        try {
            ast = ExpressionParser.parse(trimmed);
        } catch (ExpressionParseException e) {
            if (diagnostics != null) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.EXPR_SYNTAX, Severity.ERROR,
                        "Expression parse error: " + e.getMessage(),
                        context.ruleId(), expression));
            }
            return new CompiledExpression(trimmed, new LiteralExpr(NullValue.INSTANCE),
                    TypeInfo.UNKNOWN, false, Set.of());
        }

        Set<ResolvedPath> paths = new HashSet<>();
        TypeInfo resultType = resolveType(ast, context, diagnostics, paths);
        boolean deterministic = isDeterministic(ast, context.functionRegistry());

        return new CompiledExpression(trimmed, ast, resultType, deterministic, paths);
    }

    private TypeInfo resolveType(Expression expr, ExpressionCompileContext context,
                                  DiagnosticCollector diagnostics, Set<ResolvedPath> paths) {
        return switch (expr) {
            case LiteralExpr lit -> typeOfLiteral(lit.value());
            case PathExpr path -> resolvePathType(path, context, diagnostics, paths);
            case FunctionCallExpr call -> resolveFunctionType(call, context, diagnostics, paths);
            case ConditionalExpr cond -> resolveConditionalType(cond, context, diagnostics, paths);
        };
    }

    private TypeInfo typeOfLiteral(Value value) {
        if (value instanceof TextValue) return TypeInfo.TEXT;
        if (value instanceof NumberValue) return TypeInfo.NUMERIC;
        if (value instanceof BooleanValue) return TypeInfo.BOOLEAN;
        if (value instanceof EnumValue) return TypeInfo.ENUM;
        return TypeInfo.UNKNOWN;
    }

    private TypeInfo resolvePathType(PathExpr path, ExpressionCompileContext context,
                                      DiagnosticCollector diagnostics, Set<ResolvedPath> paths) {
        String alias = path.alias();
        String attrName = path.attributeName();

        SourcePlan source = context.sourcesByAlias().get(alias);
        if (source == null) {
            if (diagnostics != null) {
                diagnostics.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_SOURCE_ATTRIBUTE,
                        Severity.ERROR,
                        "Unknown source alias in expression: " + alias,
                        context.ruleId(), "Check the alias in the expression"));
            }
            return TypeInfo.UNKNOWN;
        }

        if (attrName == null || attrName.isBlank()) {
            paths.add(new ResolvedPath(alias, null,
                    source.sourceClass() != null ? source.sourceClass().getName() : null,
                    TypeInfo.REFERENCE));
            return TypeInfo.REFERENCE;
        }

        if (source.sourceClass() != null) {
            var it = source.sourceClass().getAttributes();
            while (it.hasNext()) {
                ch.interlis.ili2c.metamodel.Extendable ext = it.next();
                if (ext instanceof ch.interlis.ili2c.metamodel.AttributeDef attrDef) {
                    if (attrName.equals(attrDef.getName())) {
                        TypeInfo attrType = classifyIliAttr(attrDef);
                        paths.add(new ResolvedPath(alias, attrName,
                                source.sourceClass().getName(), attrType));
                        return attrType;
                    }
                }
            }
        }

        paths.add(new ResolvedPath(alias, attrName,
                source.sourceClass() != null ? source.sourceClass().getName() : null,
                TypeInfo.UNKNOWN));

        if (diagnostics != null) {
            diagnostics.add(new Diagnostic(DiagnosticCode.MAP_UNKNOWN_SOURCE_ATTRIBUTE,
                    Severity.ERROR,
                    "Source attribute not found: " + alias + "." + attrName,
                    context.ruleId(), "Check the attribute name in source class"));
        }
        return TypeInfo.UNKNOWN;
    }

    private TypeInfo resolveFunctionType(FunctionCallExpr call, ExpressionCompileContext context,
                                          DiagnosticCollector diagnostics, Set<ResolvedPath> paths) {
        Optional<FunctionDef> defOpt = context.functionRegistry().resolve(call.functionName());
        if (defOpt.isEmpty()) {
            if (diagnostics != null) {
                diagnostics.add(new Diagnostic(DiagnosticCode.EXPR_UNKNOWN_FUNC, Severity.ERROR,
                        "Unknown function: " + call.functionName(),
                        context.ruleId(), "Check function name or ensure it is registered"));
            }
            for (Expression arg : call.arguments()) {
                resolveType(arg, context, diagnostics, paths);
            }
            return TypeInfo.UNKNOWN;
        }

        FunctionDef def = defOpt.get();

        List<TypeInfo> argTypes = call.arguments().stream()
                .map(arg -> resolveType(arg, context, diagnostics, paths))
                .toList();

        checkEnumMapValidation(def, call, argTypes, context, diagnostics);
        checkArgTypeCompatibility(def, argTypes, context, diagnostics);

        int paramCount = def.parameters().size();
        int argCount = call.arguments().size();
        if (!def.variadic() && argCount != paramCount) {
            if (diagnostics != null) {
                diagnostics.add(new Diagnostic(DiagnosticCode.EXPR_WRONG_ARG_COUNT, Severity.ERROR,
                        "Function '" + def.name() + "' expects " + paramCount
                                + " arguments but got " + argCount,
                        context.ruleId(), "Check the function arguments"));
            }
        }

        if (!def.deterministic() && diagnostics != null) {
            diagnostics.add(new Diagnostic(DiagnosticCode.EXPR_NON_DETERMINISTIC, Severity.WARNING,
                    "Non-deterministic function used: " + def.name(),
                    context.ruleId(), "Results may vary between runs"));
        }

        return def.returnTypeResolver() != null
                ? def.returnTypeResolver().resolveReturnType(argTypes)
                : TypeInfo.UNKNOWN;
    }

    private TypeInfo resolveConditionalType(ConditionalExpr cond, ExpressionCompileContext context,
                                             DiagnosticCollector diagnostics, Set<ResolvedPath> paths) {
        resolveType(cond.condition(), context, diagnostics, paths);
        TypeInfo thenType = resolveType(cond.thenExpr(), context, diagnostics, paths);
        TypeInfo elseType = resolveType(cond.elseExpr(), context, diagnostics, paths);

        if (thenType == elseType) return thenType;
        if (thenType == TypeInfo.UNKNOWN) return elseType;
        if (elseType == TypeInfo.UNKNOWN) return thenType;
        if (isCompatible(thenType, elseType)) return thenType;
        return TypeInfo.UNKNOWN;
    }

    private boolean isCompatible(TypeInfo a, TypeInfo b) {
        if (a == b) return true;
        if (a == TypeInfo.TEXT || b == TypeInfo.TEXT) return true;
        if (a == TypeInfo.NUMERIC && b == TypeInfo.NUMERIC) return true;
        return false;
    }

    private void checkEnumMapValidation(FunctionDef def, FunctionCallExpr call,
                                          List<TypeInfo> argTypes,
                                          ExpressionCompileContext context,
                                          DiagnosticCollector diagnostics) {
        if (!"enumMap".equalsIgnoreCase(def.name())) return;
        if (call.arguments().size() < 2) return;
        if (diagnostics == null) return;

        Expression mapNameExpr = call.arguments().get(1);
        if (!(mapNameExpr instanceof LiteralExpr lit && lit.value() instanceof TextValue tv)) return;

        String mapName = tv.value();
        Map<String, Map<String, String>> enumMaps = context.enumMaps();

        if (enumMaps == null || !enumMaps.containsKey(mapName)) {
            diagnostics.add(new Diagnostic(DiagnosticCode.EXPR_ENUM_MAP_MISSING, Severity.ERROR,
                    "Enum mapping table '" + mapName + "' not found in config",
                    context.ruleId(), "Define the mapping under mapping.enums"));
        }
    }

    private void checkArgTypeCompatibility(FunctionDef def, List<TypeInfo> argTypes,
                                            ExpressionCompileContext context,
                                            DiagnosticCollector diagnostics) {
        if (diagnostics == null) return;
        int paramSize = def.parameters().size();
        for (int i = 0; i < Math.min(paramSize, argTypes.size()); i++) {
            TypeInfo paramType = def.parameters().get(i).type();
            TypeInfo argType = argTypes.get(i);
            if (paramType != TypeInfo.UNKNOWN && argType != TypeInfo.UNKNOWN
                    && !isArgTypeCompatible(argType, paramType)) {
                diagnostics.add(new Diagnostic(DiagnosticCode.EXPR_WRONG_ARG_TYPE, Severity.WARNING,
                        "Function '" + def.name() + "' parameter " + (i + 1)
                                + " expects " + paramType + " but got " + argType,
                        context.ruleId(), "Check the argument type"));
            }
        }
    }

    private boolean isArgTypeCompatible(TypeInfo argType, TypeInfo paramType) {
        if (argType == paramType) return true;
        if (paramType == TypeInfo.UNKNOWN) return true;
        if (paramType == TypeInfo.TEXT) return true;
        if (paramType == TypeInfo.NUMERIC && argType == TypeInfo.NUMERIC) return true;
        if (paramType == TypeInfo.BOOLEAN && argType == TypeInfo.BOOLEAN) return true;
        if (paramType == TypeInfo.ENUM && argType == TypeInfo.ENUM) return true;
        return false;
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
        return classifyIliType(type != null ? type : null);
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
