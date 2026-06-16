package guru.interlis.transformer.expr.builtins;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.expr.BooleanValue;
import guru.interlis.transformer.expr.EnumValue;
import guru.interlis.transformer.expr.EvalContext;
import guru.interlis.transformer.expr.FunctionDef;
import guru.interlis.transformer.expr.FunctionRegistry;
import guru.interlis.transformer.expr.NullValue;
import guru.interlis.transformer.expr.NumberValue;
import guru.interlis.transformer.expr.Value;
import guru.interlis.transformer.mapping.plan.TypeInfo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class EnumFunctions {

    private EnumFunctions() {}

    public static void registerAll(FunctionRegistry registry) {
        registry.register(
                "enumMap",
                TypeInfo.ENUM,
                List.of(
                        new FunctionDef.FunctionParam("value", TypeInfo.UNKNOWN),
                        new FunctionDef.FunctionParam("mapName", TypeInfo.TEXT)),
                EnumFunctions::enumMap);

        registry.register(
                "enumMapDefault",
                TypeInfo.ENUM,
                List.of(
                        new FunctionDef.FunctionParam("value", TypeInfo.UNKNOWN),
                        new FunctionDef.FunctionParam("mapName", TypeInfo.TEXT),
                        new FunctionDef.FunctionParam("fallback", TypeInfo.UNKNOWN)),
                EnumFunctions::enumMapDefault);

        registry.register(
                "enumMapStrict",
                TypeInfo.ENUM,
                List.of(
                        new FunctionDef.FunctionParam("value", TypeInfo.UNKNOWN),
                        new FunctionDef.FunctionParam("mapName", TypeInfo.TEXT)),
                EnumFunctions::enumMapStrict);

        registry.register(
                "enumDefault",
                TypeInfo.ENUM,
                List.of(
                        new FunctionDef.FunctionParam("value", TypeInfo.UNKNOWN),
                        new FunctionDef.FunctionParam("fallback", TypeInfo.TEXT)),
                EnumFunctions::enumDefault);

        registry.register(
                "enumName",
                TypeInfo.TEXT,
                List.of(new FunctionDef.FunctionParam("value", TypeInfo.ENUM)),
                EnumFunctions::enumName);
    }

    static Value enumMap(List<Value> args, EvalContext ctx) {
        if (args.size() < 2 || !args.get(0).isDefined()) return NullValue.INSTANCE;
        Value val = args.get(0);
        String mapName = args.get(1).asText();
        String sourceKey = val.asText();

        Map<String, Map<String, String>> enumMaps = ctx.enumMaps();
        if (enumMaps == null || !enumMaps.containsKey(mapName)) {
            if (ctx.diagnostics() != null) {
                ctx.diagnostics()
                        .add(new Diagnostic(
                                DiagnosticCode.EXPR_UNSUPPORTED,
                                Severity.WARNING,
                                "enumMap(): enum mapping table '" + mapName
                                        + "' not found in config – pass-through used",
                                ctx.ruleId(),
                                "Define the enum mapping under mapping.enums."));
            }
            return val;
        }

        Map<String, String> mapping = enumMaps.get(mapName);
        String targetValue = mapping.get(sourceKey);
        if (targetValue == null) {
            if (ctx.diagnostics() != null) {
                ctx.diagnostics()
                        .add(new Diagnostic(
                                DiagnosticCode.EXPR_TYPE,
                                Severity.WARNING,
                                "enumMap(): no mapping for source value '" + sourceKey + "' in map '" + mapName + "'",
                                ctx.ruleId(),
                                "Add the missing mapping or use enumMapDefault() for a fallback"));
            }
            return NullValue.INSTANCE;
        }

        return resolveEnumTargetValue(targetValue);
    }

    static Value enumMapDefault(List<Value> args, EvalContext ctx) {
        if (args.size() < 3 || !args.get(0).isDefined()) {
            if (args.size() >= 3) return args.get(2);
            return NullValue.INSTANCE;
        }
        Value val = args.get(0);
        String mapName = args.get(1).asText();
        Value fallback = args.get(2);
        String sourceKey = val.asText();

        Map<String, Map<String, String>> enumMaps = ctx.enumMaps();
        if (enumMaps == null || !enumMaps.containsKey(mapName)) {
            if (ctx.diagnostics() != null) {
                ctx.diagnostics()
                        .add(new Diagnostic(
                                DiagnosticCode.EXPR_UNSUPPORTED,
                                Severity.WARNING,
                                "enumMapDefault(): enum mapping table '" + mapName + "' not found in config",
                                ctx.ruleId(),
                                "Define the enum mapping under mapping.enums."));
            }
            return fallback;
        }

        Map<String, String> mapping = enumMaps.get(mapName);
        String targetValue = mapping.get(sourceKey);
        if (targetValue == null) {
            return fallback;
        }

        return resolveEnumTargetValue(targetValue);
    }

    static Value enumMapStrict(List<Value> args, EvalContext ctx) {
        if (args.size() < 2 || !args.get(0).isDefined()) return NullValue.INSTANCE;
        Value val = args.get(0);
        String mapName = args.get(1).asText();
        String sourceKey = val.asText();

        Map<String, Map<String, String>> enumMaps = ctx.enumMaps();
        if (enumMaps == null || !enumMaps.containsKey(mapName)) {
            if (ctx.diagnostics() != null) {
                ctx.diagnostics()
                        .add(new Diagnostic(
                                DiagnosticCode.EXPR_UNSUPPORTED,
                                Severity.ERROR,
                                "enumMapStrict(): enum mapping table '" + mapName + "' not found in config",
                                ctx.ruleId(),
                                "Define the enum mapping under mapping.enums."));
            }
            return NullValue.INSTANCE;
        }

        Map<String, String> mapping = enumMaps.get(mapName);
        String targetValue = mapping.get(sourceKey);
        if (targetValue == null) {
            if (ctx.diagnostics() != null) {
                ctx.diagnostics()
                        .add(new Diagnostic(
                                DiagnosticCode.EXPR_TYPE,
                                Severity.ERROR,
                                "enumMapStrict(): no mapping for source value '" + sourceKey + "' in map '" + mapName
                                        + "'",
                                ctx.ruleId(),
                                "Add the missing mapping or use enumMapDefault() for a fallback"));
            }
            return NullValue.INSTANCE;
        }

        return resolveEnumTargetValue(targetValue);
    }

    private static Value resolveEnumTargetValue(String targetValue) {
        if ("true".equalsIgnoreCase(targetValue)) return BooleanValue.TRUE;
        if ("false".equalsIgnoreCase(targetValue)) return BooleanValue.FALSE;

        try {
            return new NumberValue(new BigDecimal(targetValue));
        } catch (NumberFormatException ignored) {
        }

        return new EnumValue(targetValue, null);
    }

    static Value enumDefault(List<Value> args, EvalContext ctx) {
        if (args.isEmpty()) return NullValue.INSTANCE;
        Value val = args.get(0);
        if (val.isDefined()) return val;
        if (args.size() >= 2) {
            return new EnumValue(args.get(1).asText(), null);
        }
        return NullValue.INSTANCE;
    }

    static Value enumName(List<Value> args, EvalContext ctx) {
        if (args.isEmpty() || !args.get(0).isDefined()) return NullValue.INSTANCE;
        Value val = args.get(0);
        if (val instanceof EnumValue ev) {
            return new guru.interlis.transformer.expr.TextValue(ev.name());
        }
        return new guru.interlis.transformer.expr.TextValue(val.asText());
    }
}
