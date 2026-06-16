package guru.interlis.transformer.expr;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.plan.ExpressionCompileContext;
import guru.interlis.transformer.mapping.plan.ResolvedPath;
import guru.interlis.transformer.mapping.plan.SourcePlan;
import guru.interlis.transformer.mapping.plan.TypeInfo;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ExpressionTypeChecker {

    public record Result(TypeInfo resultType, Set<ResolvedPath> resolvedPaths) {}

    private final ExpressionCompileContext context;
    private final DiagnosticCollector diagnostics;

    public ExpressionTypeChecker(ExpressionCompileContext context, DiagnosticCollector diagnostics) {
        this.context = context;
        this.diagnostics = diagnostics;
    }

    public Result check(Expression expr) {
        Set<ResolvedPath> paths = new HashSet<>();
        TypeInfo type = check(expr, paths);
        return new Result(type, Set.copyOf(paths));
    }

    public TypeInfo check(Expression expr, Set<ResolvedPath> paths) {
        return switch (expr) {
            case LiteralExpr lit -> typeOfLiteral(lit.value());
            case PathExpr path -> checkPath(path, paths);
            case FunctionCallExpr call -> checkFunction(call, paths);
            case ConditionalExpr cond -> checkConditional(cond, paths);
        };
    }

    private TypeInfo typeOfLiteral(Value value) {
        if (value instanceof TextValue) return TypeInfo.TEXT;
        if (value instanceof NumberValue) return TypeInfo.NUMERIC;
        if (value instanceof BooleanValue) return TypeInfo.BOOLEAN;
        if (value instanceof EnumValue) return TypeInfo.ENUM;
        return TypeInfo.UNKNOWN;
    }

    private TypeInfo checkPath(PathExpr path, Set<ResolvedPath> paths) {
        String alias = path.alias();
        String attrName = path.attributeName();
        SourcePlan source = context.sourcesByAlias().get(alias);

        if (source == null) {
            if (diagnostics != null) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.MAP_UNKNOWN_SOURCE_ATTRIBUTE,
                        Severity.ERROR,
                        "Unknown source alias in expression: " + alias,
                        context.ruleId(),
                        "Check the alias"));
            }
            return TypeInfo.UNKNOWN;
        }

        if (attrName != null && attrName.contains(".")) {
            if (diagnostics != null) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.EXPR_UNSUPPORTED,
                        Severity.ERROR,
                        "Nested expression paths are not supported: " + alias + "." + attrName,
                        context.ruleId(),
                        "Use bags/nestedBags for structures or direct alias.attribute paths"));
            }
            return TypeInfo.UNKNOWN;
        }

        if (attrName == null || attrName.isBlank()) {
            paths.add(new ResolvedPath(
                    alias,
                    null,
                    source.sourceClass() != null ? source.sourceClass().getName() : null,
                    TypeInfo.REFERENCE));
            return TypeInfo.REFERENCE;
        }

        if (source.sourceClass() != null) {
            ch.interlis.ili2c.metamodel.AttributeDef attrDef = findAttribute(source.sourceClass(), attrName);
            if (attrDef != null) {
                TypeInfo type = ExpressionCompiler.classifyIliAttr(attrDef);
                paths.add(new ResolvedPath(alias, attrName, source.sourceClass().getName(), type));
                return type;
            }
            ch.interlis.ili2c.metamodel.RoleDef roleDef = findRole(source.sourceClass(), attrName);
            if (roleDef != null) {
                paths.add(new ResolvedPath(alias, attrName, source.sourceClass().getName(), TypeInfo.REFERENCE));
                return TypeInfo.REFERENCE;
            }
        }

        paths.add(new ResolvedPath(
                alias,
                attrName,
                source.sourceClass() != null ? source.sourceClass().getName() : null,
                TypeInfo.UNKNOWN));

        if (diagnostics != null) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.MAP_UNKNOWN_SOURCE_ATTRIBUTE,
                    Severity.ERROR,
                    "Source attribute not found: " + alias + "." + attrName,
                    context.ruleId(),
                    "Check the attribute name in source class"));
        }
        return TypeInfo.UNKNOWN;
    }

    private TypeInfo checkFunction(FunctionCallExpr call, Set<ResolvedPath> paths) {
        Optional<FunctionDef> defOpt = context.functionRegistry().resolve(call.functionName());
        if (defOpt.isEmpty()) {
            if (diagnostics != null) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.EXPR_UNKNOWN_FUNC,
                        Severity.ERROR,
                        "Unknown function: " + call.functionName(),
                        context.ruleId(),
                        "Check the function name"));
            }
            for (Expression arg : call.arguments()) {
                check(arg, paths);
            }
            return TypeInfo.UNKNOWN;
        }

        FunctionDef def = defOpt.get();
        List<TypeInfo> argTypes =
                call.arguments().stream().map(arg -> check(arg, paths)).toList();

        if (!def.variadic() && call.arguments().size() != def.parameters().size()) {
            if (diagnostics != null) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.EXPR_WRONG_ARG_COUNT,
                        Severity.ERROR,
                        "Function '" + def.name() + "' expects "
                                + def.parameters().size() + " arguments but got "
                                + call.arguments().size(),
                        context.ruleId(),
                        "Check the function arguments"));
            }
        }

        checkEnumMapFunction(def, call, argTypes);
        checkArgTypeCompatibility(def, argTypes);

        if (!def.deterministic() && diagnostics != null) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.EXPR_NON_DETERMINISTIC,
                    Severity.WARNING,
                    "Non-deterministic function used: " + def.name(),
                    context.ruleId(),
                    "Results may vary between runs"));
        }

        TypeInfo enumMapType = inferEnumMapReturnType(def, call);
        if (enumMapType != null) {
            return enumMapType;
        }

        return def.returnTypeResolver() != null
                ? def.returnTypeResolver().resolveReturnType(argTypes)
                : TypeInfo.UNKNOWN;
    }

    private void checkEnumMapFunction(FunctionDef def, FunctionCallExpr call, List<TypeInfo> argTypes) {
        String name = def.name().toLowerCase();
        if (!name.startsWith("enummap")) return;
        if (call.arguments().size() < 2) return;

        Expression mapNameExpr = call.arguments().get(1);
        if (!(mapNameExpr instanceof LiteralExpr lit && lit.value() instanceof TextValue tv)) return;

        String mapName = tv.value();
        Map<String, Map<String, String>> enumMaps = context.enumMaps();

        if (enumMaps == null || !enumMaps.containsKey(mapName)) {
            if (diagnostics != null) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.EXPR_ENUM_MAP_MISSING,
                        Severity.ERROR,
                        "Enum mapping table '" + mapName + "' not found in config",
                        context.ruleId(),
                        "Define the mapping under mapping.enums"));
            }
        }
    }

    private TypeInfo inferEnumMapReturnType(FunctionDef def, FunctionCallExpr call) {
        String name = def.name().toLowerCase();
        if (!name.startsWith("enummap")) return null;
        if (call.arguments().size() < 2) return TypeInfo.ENUM;

        Expression mapNameExpr = call.arguments().get(1);
        if (!(mapNameExpr instanceof LiteralExpr lit && lit.value() instanceof TextValue tv)) {
            return TypeInfo.ENUM;
        }

        Map<String, Map<String, String>> enumMaps = context.enumMaps();
        if (enumMaps == null) return TypeInfo.ENUM;
        Map<String, String> mapping = enumMaps.get(tv.value());
        if (mapping == null || mapping.isEmpty()) return TypeInfo.ENUM;

        boolean allBoolean = true;
        boolean allNumeric = true;
        for (String value : mapping.values()) {
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                allBoolean = false;
            }
            try {
                new BigDecimal(value);
            } catch (NumberFormatException e) {
                allNumeric = false;
            }
        }
        if (allBoolean) return TypeInfo.BOOLEAN;
        if (allNumeric) return TypeInfo.NUMERIC;
        return TypeInfo.ENUM;
    }

    private void checkArgTypeCompatibility(FunctionDef def, List<TypeInfo> argTypes) {
        int paramSize = def.parameters().size();
        for (int i = 0; i < Math.min(paramSize, argTypes.size()); i++) {
            TypeInfo paramType = def.parameters().get(i).type();
            TypeInfo argType = argTypes.get(i);
            if (paramType != TypeInfo.UNKNOWN && argType != TypeInfo.UNKNOWN && !isTypeCompatible(argType, paramType)) {
                if (diagnostics != null) {
                    diagnostics.add(new Diagnostic(
                            DiagnosticCode.EXPR_WRONG_ARG_TYPE,
                            Severity.WARNING,
                            "Function '" + def.name() + "' parameter " + (i + 1) + " expects " + paramType + " but got "
                                    + argType,
                            context.ruleId(),
                            "Check the argument type"));
                }
            }
        }
    }

    private TypeInfo checkConditional(ConditionalExpr cond, Set<ResolvedPath> paths) {
        check(cond.condition(), paths);
        TypeInfo thenType = check(cond.thenExpr(), paths);
        TypeInfo elseType = check(cond.elseExpr(), paths);

        if (thenType == elseType) return thenType;
        if (thenType == TypeInfo.UNKNOWN) return elseType;
        if (elseType == TypeInfo.UNKNOWN) return thenType;
        if (isCompatible(thenType, elseType)) return thenType;
        return TypeInfo.UNKNOWN;
    }

    private boolean isCompatible(TypeInfo a, TypeInfo b) {
        if (a == b) return true;
        if (a == TypeInfo.TEXT || b == TypeInfo.TEXT) return true;
        return false;
    }

    private boolean isTypeCompatible(TypeInfo argType, TypeInfo paramType) {
        if (argType == paramType) return true;
        if (paramType == TypeInfo.UNKNOWN) return true;
        if (paramType == TypeInfo.TEXT) return true;
        if (paramType == TypeInfo.NUMERIC && argType == TypeInfo.NUMERIC) return true;
        if (paramType == TypeInfo.BOOLEAN && argType == TypeInfo.BOOLEAN) return true;
        if (paramType == TypeInfo.ENUM && argType == TypeInfo.ENUM) return true;
        if (paramType == TypeInfo.COORD && argType == TypeInfo.COORD) return true;
        if (paramType == TypeInfo.POLYLINE && argType == TypeInfo.POLYLINE) return true;
        if (paramType == TypeInfo.SURFACE && argType == TypeInfo.SURFACE) return true;
        if (paramType == TypeInfo.AREA && argType == TypeInfo.AREA) return true;
        return false;
    }

    private static ch.interlis.ili2c.metamodel.AttributeDef findAttribute(
            ch.interlis.ili2c.metamodel.Table table, String attrName) {
        ch.interlis.ili2c.metamodel.AttributeDef found = table.findAttribute(attrName);
        if (found != null) return found;
        var it = table.getAttributesAndRoles2();
        while (it.hasNext()) {
            ch.interlis.ili2c.metamodel.ViewableTransferElement element = it.next();
            if (element.obj instanceof ch.interlis.ili2c.metamodel.AttributeDef attr
                    && attrName.equals(attr.getName())) {
                return attr;
            }
        }
        return null;
    }

    private static ch.interlis.ili2c.metamodel.RoleDef findRole(
            ch.interlis.ili2c.metamodel.Table table, String roleName) {
        var it = table.getAttributesAndRoles2();
        while (it.hasNext()) {
            ch.interlis.ili2c.metamodel.ViewableTransferElement element = it.next();
            if (element.obj instanceof ch.interlis.ili2c.metamodel.RoleDef role && roleName.equals(role.getName())) {
                return role;
            }
        }
        @SuppressWarnings("unchecked")
        java.util.Iterator<ch.interlis.ili2c.metamodel.RoleDef> targetRoles = table.getTargetForRoles();
        while (targetRoles != null && targetRoles.hasNext()) {
            ch.interlis.ili2c.metamodel.RoleDef role = targetRoles.next();
            if (roleName.equals(role.getName())) {
                return role;
            }
        }
        return null;
    }
}
