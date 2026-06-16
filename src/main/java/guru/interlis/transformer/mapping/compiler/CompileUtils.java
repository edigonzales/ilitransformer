package guru.interlis.transformer.mapping.compiler;

import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Container;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.Topic;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.expr.FunctionRegistry;
import guru.interlis.transformer.mapping.plan.ExpressionKind;
import guru.interlis.transformer.mapping.plan.OutputBinding;
import guru.interlis.transformer.mapping.plan.SourcePlan;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.model.ModelRegistry;
import guru.interlis.transformer.model.TypeSystemFacade;

import java.util.List;

final class CompileUtils {

    private CompileUtils() {}

    static String getScopedName(Table table) {
        if (table == null) return null;
        Container container = table.getContainer();
        if (container instanceof Topic topic) {
            Container modelContainer = topic.getContainer();
            if (modelContainer instanceof Model model) {
                return model.getName() + "." + topic.getName() + "." + table.getName();
            }
        }
        if (container instanceof Model model) {
            return model.getName() + "." + table.getName();
        }
        return table.getName();
    }

    static Table resolveTargetClass(String outputId, String className, ModelRegistry modelRegistry) {
        if (className == null || className.isBlank()) return null;
        if (outputId != null && !outputId.isEmpty()) {
            TypeSystemFacade ts = modelRegistry.requireTargetTypeSystem(outputId);
            Table resolved = ts.resolveClass(className);
            if (resolved != null) return resolved;
        }
        for (OutputBinding binding : modelRegistry.outputsById().values()) {
            Table resolved = binding.typeSystem().resolveClass(className);
            if (resolved != null) return resolved;
        }
        return null;
    }

    static ExpressionKind classifyExpression(String expr) {
        if (expr == null || expr.isBlank()) return ExpressionKind.UNKNOWN;
        String trimmed = expr.trim();

        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            return ExpressionKind.SOURCE_PATH;
        }
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")
                || trimmed.startsWith("'") && trimmed.endsWith("'")) {
            return ExpressionKind.LITERAL_TEXT;
        }
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return ExpressionKind.LITERAL_BOOLEAN;
        }
        if (trimmed.startsWith("#")) {
            return ExpressionKind.LITERAL_ENUM;
        }
        if (trimmed.contains("(") && trimmed.endsWith(")")) {
            return ExpressionKind.FUNCTION_CALL;
        }
        try {
            Double.parseDouble(trimmed);
            return ExpressionKind.LITERAL_NUMBER;
        } catch (NumberFormatException e) {
            // fall through
        }
        if (trimmed.contains(".")) {
            return ExpressionKind.SOURCE_PATH; // unbraced path
        }
        return ExpressionKind.UNKNOWN;
    }

    static TypeInfo classifyIliType(AttributeDef attr) {
        if (attr == null) return TypeInfo.UNKNOWN;
        ch.interlis.ili2c.metamodel.Type type = attr.getDomain();
        TypeInfo result = classifyIliTypeObj(type);
        if (result == TypeInfo.UNKNOWN && type != null) {
            ch.interlis.ili2c.metamodel.Type resolved = attr.getDomainResolvingAliases();
            if (resolved != null && resolved != type) {
                result = classifyIliTypeObj(resolved);
            }
        }
        return result;
    }

    static TypeInfo classifyIliTypeObj(ch.interlis.ili2c.metamodel.Type type) {
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

    static boolean isTypeCompatible(TypeInfo sourceType, TypeInfo targetType) {
        if (sourceType == TypeInfo.UNKNOWN || targetType == TypeInfo.UNKNOWN) return true;
        if (sourceType == targetType) return true;
        return switch (sourceType) {
            case TEXT, ENUM -> targetType == TypeInfo.TEXT || targetType == TypeInfo.ENUM;
            case NUMERIC -> targetType == TypeInfo.NUMERIC || targetType == TypeInfo.TEXT;
            case BOOLEAN -> targetType == TypeInfo.BOOLEAN || targetType == TypeInfo.TEXT;
            case COORD, POLYLINE, SURFACE, AREA -> targetType == sourceType || targetType == TypeInfo.TEXT
                    || (sourceType == TypeInfo.AREA && targetType == TypeInfo.SURFACE)
                    || (sourceType == TypeInfo.SURFACE && targetType == TypeInfo.AREA);
            case XML_DATE_TIME -> targetType == TypeInfo.XML_DATE_TIME || targetType == TypeInfo.DATE
                    || targetType == TypeInfo.TEXT;
            case DATE -> targetType == TypeInfo.DATE || targetType == TypeInfo.XML_DATE_TIME
                    || targetType == TypeInfo.TEXT;
            default -> true;
        };
    }

    static String extractFunctionName(String expr) {
        if (expr == null || expr.isBlank()) return null;
        String trimmed = expr.trim();
        int paren = trimmed.indexOf('(');
        if (paren < 0) return null;
        return trimmed.substring(0, paren).trim();
    }

    static TypeInfo inferFunctionType(String expr, FunctionRegistry functionRegistry,
                                       DiagnosticCollector diag, String ruleId) {
        String funcName = extractFunctionName(expr);
        if (funcName == null) return TypeInfo.UNKNOWN;
        var def = functionRegistry.resolve(funcName);
        if (def.isPresent()) {
            if (!def.get().deterministic()) {
                diag.add(new Diagnostic(DiagnosticCode.EXPR_NON_DETERMINISTIC, Severity.WARNING,
                        "Non-deterministic function used: " + funcName,
                        ruleId, "Results may vary between runs"));
            }
            return def.get().returnType();
        }
        return TypeInfo.UNKNOWN;
    }

    static TypeInfo inferExpressionType(String expr, ExpressionKind kind,
                                          List<SourcePlan> sourcePlans,
                                          DiagnosticCollector diag, String ruleId) {
        return switch (kind) {
            case LITERAL_TEXT -> TypeInfo.TEXT;
            case LITERAL_NUMBER -> TypeInfo.NUMERIC;
            case LITERAL_BOOLEAN -> TypeInfo.BOOLEAN;
            case LITERAL_ENUM -> TypeInfo.ENUM;
            case SOURCE_PATH -> inferSourcePathType(expr, sourcePlans);
            case FUNCTION_CALL -> TypeInfo.UNKNOWN;
            default -> TypeInfo.UNKNOWN;
        };
    }

    static TypeInfo inferSourcePathType(String expr, List<SourcePlan> sourcePlans) {
        String trimmed = expr.trim();
        String pathContent;
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            pathContent = trimmed.substring(2, trimmed.length() - 1);
        } else {
            pathContent = trimmed;
        }
        String[] parts = pathContent.split("\\.", 2);
        if (parts.length < 2) return TypeInfo.UNKNOWN;

        String alias = parts[0];
        String attrName = parts[1];

        for (SourcePlan sp : sourcePlans) {
            if (sp.alias().equals(alias) && sp.sourceClass() != null) {
                var it = sp.sourceClass().getAttributes();
                while (it.hasNext()) {
                    ch.interlis.ili2c.metamodel.Extendable ext = it.next();
                    if (ext instanceof AttributeDef attrDef) {
                        if (attrName.equals(attrDef.getName())) {
                            return classifyIliType(attrDef);
                        }
                    }
                }
            }
        }
        return TypeInfo.UNKNOWN;
    }
}
