package guru.interlis.transformer.expr.builtins;

import guru.interlis.transformer.expr.EvalContext;
import guru.interlis.transformer.expr.FunctionDef;
import guru.interlis.transformer.expr.FunctionRegistry;
import guru.interlis.transformer.expr.NullValue;
import guru.interlis.transformer.expr.TextValue;
import guru.interlis.transformer.expr.Value;
import guru.interlis.transformer.mapping.plan.TypeInfo;

import java.util.List;
import java.util.stream.Collectors;

public final class StringFunctions {

    private StringFunctions() {}

    public static void registerAll(FunctionRegistry registry) {
        registry.register("concat", TypeInfo.TEXT, List.of(), StringFunctions::concat);
        registry.register(
                "substring",
                TypeInfo.TEXT,
                List.of(
                        new FunctionDef.FunctionParam("value", TypeInfo.TEXT),
                        new FunctionDef.FunctionParam("start", TypeInfo.NUMERIC),
                        new FunctionDef.FunctionParam("length", TypeInfo.NUMERIC)),
                StringFunctions::substring);
        registry.register(
                "trim",
                TypeInfo.TEXT,
                List.of(new FunctionDef.FunctionParam("value", TypeInfo.TEXT)),
                StringFunctions::trim);
        registry.register(
                "upper",
                TypeInfo.TEXT,
                List.of(new FunctionDef.FunctionParam("value", TypeInfo.TEXT)),
                StringFunctions::upper);
        registry.register(
                "lower",
                TypeInfo.TEXT,
                List.of(new FunctionDef.FunctionParam("value", TypeInfo.TEXT)),
                StringFunctions::lower);
        registry.register(
                "replace",
                TypeInfo.TEXT,
                List.of(
                        new FunctionDef.FunctionParam("value", TypeInfo.TEXT),
                        new FunctionDef.FunctionParam("pattern", TypeInfo.TEXT),
                        new FunctionDef.FunctionParam("replacement", TypeInfo.TEXT)),
                StringFunctions::replace);
        registry.register(
                "truncate",
                TypeInfo.TEXT,
                List.of(
                        new FunctionDef.FunctionParam("value", TypeInfo.TEXT),
                        new FunctionDef.FunctionParam("maxLength", TypeInfo.NUMERIC)),
                StringFunctions::truncate);
    }

    static Value concat(List<Value> args, EvalContext ctx) {
        String result =
                args.stream().filter(Value::isDefined).map(v -> v.asText()).collect(Collectors.joining());
        return new TextValue(result);
    }

    static Value substring(List<Value> args, EvalContext ctx) {
        if (args.size() < 3 || !args.get(0).isDefined()) return NullValue.INSTANCE;
        String s = args.get(0).asText();
        int start = (int) args.get(1).asNumber();
        int length = (int) args.get(2).asNumber();
        if (start < 0 || start >= s.length()) return new TextValue("");
        int end = Math.min(start + length, s.length());
        return new TextValue(s.substring(start, end));
    }

    static Value trim(List<Value> args, EvalContext ctx) {
        if (args.isEmpty() || !args.get(0).isDefined()) return NullValue.INSTANCE;
        return new TextValue(args.get(0).asText().trim());
    }

    static Value upper(List<Value> args, EvalContext ctx) {
        if (args.isEmpty() || !args.get(0).isDefined()) return NullValue.INSTANCE;
        return new TextValue(args.get(0).asText().toUpperCase());
    }

    static Value lower(List<Value> args, EvalContext ctx) {
        if (args.isEmpty() || !args.get(0).isDefined()) return NullValue.INSTANCE;
        return new TextValue(args.get(0).asText().toLowerCase());
    }

    static Value replace(List<Value> args, EvalContext ctx) {
        if (args.size() < 3 || !args.get(0).isDefined()) return NullValue.INSTANCE;
        return new TextValue(
                args.get(0).asText().replace(args.get(1).asText(), args.get(2).asText()));
    }

    static Value truncate(List<Value> args, EvalContext ctx) {
        if (args.size() < 2 || !args.get(0).isDefined()) return NullValue.INSTANCE;
        String s = args.get(0).asText();
        int maxLen = (int) args.get(1).asNumber();
        if (maxLen <= 0) return new TextValue("");
        return new TextValue(s.length() <= maxLen ? s : s.substring(0, maxLen));
    }
}
