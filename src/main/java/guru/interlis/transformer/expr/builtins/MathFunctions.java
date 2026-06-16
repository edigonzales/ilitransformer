package guru.interlis.transformer.expr.builtins;

import guru.interlis.transformer.expr.EvalContext;
import guru.interlis.transformer.expr.FunctionDef;
import guru.interlis.transformer.expr.FunctionRegistry;
import guru.interlis.transformer.expr.NullValue;
import guru.interlis.transformer.expr.NumberValue;
import guru.interlis.transformer.expr.TextValue;
import guru.interlis.transformer.expr.Value;
import guru.interlis.transformer.mapping.plan.TypeInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class MathFunctions {

    private MathFunctions() {}

    public static void registerAll(FunctionRegistry registry) {
        registry.register(
                "div",
                TypeInfo.NUMERIC,
                List.of(
                        new FunctionDef.FunctionParam("value", TypeInfo.UNKNOWN),
                        new FunctionDef.FunctionParam("divisor", TypeInfo.NUMERIC)),
                MathFunctions::div);

        registry.register(
                "mul",
                TypeInfo.NUMERIC,
                List.of(
                        new FunctionDef.FunctionParam("value", TypeInfo.UNKNOWN),
                        new FunctionDef.FunctionParam("factor", TypeInfo.NUMERIC)),
                MathFunctions::mul);

        registry.register(
                "add",
                TypeInfo.NUMERIC,
                List.of(
                        new FunctionDef.FunctionParam("value", TypeInfo.UNKNOWN),
                        new FunctionDef.FunctionParam("addend", TypeInfo.NUMERIC)),
                MathFunctions::add);

        registry.register(
                "sub",
                TypeInfo.NUMERIC,
                List.of(
                        new FunctionDef.FunctionParam("value", TypeInfo.UNKNOWN),
                        new FunctionDef.FunctionParam("subtrahend", TypeInfo.NUMERIC)),
                MathFunctions::sub);

        registry.register(
                "round",
                TypeInfo.NUMERIC,
                List.of(
                        new FunctionDef.FunctionParam("value", TypeInfo.UNKNOWN),
                        new FunctionDef.FunctionParam("scale", TypeInfo.NUMERIC)),
                MathFunctions::round);

        registry.register(
                "abs",
                TypeInfo.NUMERIC,
                List.of(new FunctionDef.FunctionParam("value", TypeInfo.UNKNOWN)),
                MathFunctions::abs);

        registry.register(
                "min",
                TypeInfo.NUMERIC,
                List.of(
                        new FunctionDef.FunctionParam("a", TypeInfo.UNKNOWN),
                        new FunctionDef.FunctionParam("b", TypeInfo.NUMERIC)),
                MathFunctions::min);

        registry.register(
                "max",
                TypeInfo.NUMERIC,
                List.of(
                        new FunctionDef.FunctionParam("a", TypeInfo.UNKNOWN),
                        new FunctionDef.FunctionParam("b", TypeInfo.NUMERIC)),
                MathFunctions::max);

        registry.register(
                "toNumber",
                TypeInfo.NUMERIC,
                List.of(new FunctionDef.FunctionParam("value", TypeInfo.TEXT)),
                MathFunctions::toNumber);
    }

    static Value mul(List<Value> args, EvalContext ctx) {
        if (args.size() < 2 || !args.get(0).isDefined()) return NullValue.INSTANCE;
        BigDecimal value = toBigDecimal(args.get(0));
        BigDecimal factor = toBigDecimal(args.get(1));
        if (value == null || factor == null) return NullValue.INSTANCE;
        return new NumberValue(value.multiply(factor));
    }

    static Value div(List<Value> args, EvalContext ctx) {
        if (args.size() < 2 || !args.get(0).isDefined()) return NullValue.INSTANCE;
        BigDecimal value = toBigDecimal(args.get(0));
        BigDecimal divisor = toBigDecimal(args.get(1));
        if (value == null || divisor == null) return NullValue.INSTANCE;
        if (divisor.compareTo(BigDecimal.ZERO) == 0) return NullValue.INSTANCE;
        return new NumberValue(value.divide(divisor, 10, RoundingMode.HALF_UP));
    }

    static Value add(List<Value> args, EvalContext ctx) {
        if (args.size() < 2 || !args.get(0).isDefined()) return NullValue.INSTANCE;
        BigDecimal value = toBigDecimal(args.get(0));
        BigDecimal addend = toBigDecimal(args.get(1));
        if (value == null || addend == null) return NullValue.INSTANCE;
        return new NumberValue(value.add(addend));
    }

    static Value sub(List<Value> args, EvalContext ctx) {
        if (args.size() < 2 || !args.get(0).isDefined()) return NullValue.INSTANCE;
        BigDecimal value = toBigDecimal(args.get(0));
        BigDecimal subtrahend = toBigDecimal(args.get(1));
        if (value == null || subtrahend == null) return NullValue.INSTANCE;
        return new NumberValue(value.subtract(subtrahend));
    }

    static Value round(List<Value> args, EvalContext ctx) {
        if (args.size() < 2 || !args.get(0).isDefined() || !args.get(1).isDefined()) return NullValue.INSTANCE;
        BigDecimal value = toBigDecimal(args.get(0));
        BigDecimal scaleValue = toBigDecimal(args.get(1));
        if (value == null || scaleValue == null) return NullValue.INSTANCE;
        int scale = scaleValue.intValue();
        return new NumberValue(value.setScale(scale, RoundingMode.HALF_UP));
    }

    static Value abs(List<Value> args, EvalContext ctx) {
        if (args.isEmpty() || !args.get(0).isDefined()) return NullValue.INSTANCE;
        BigDecimal value = toBigDecimal(args.get(0));
        if (value == null) return NullValue.INSTANCE;
        return new NumberValue(value.abs());
    }

    static Value min(List<Value> args, EvalContext ctx) {
        if (args.size() < 2 || !args.get(0).isDefined()) return NullValue.INSTANCE;
        BigDecimal a = toBigDecimal(args.get(0));
        BigDecimal b = toBigDecimal(args.get(1));
        if (a == null || b == null) return NullValue.INSTANCE;
        return new NumberValue(a.min(b));
    }

    static Value max(List<Value> args, EvalContext ctx) {
        if (args.size() < 2 || !args.get(0).isDefined()) return NullValue.INSTANCE;
        BigDecimal a = toBigDecimal(args.get(0));
        BigDecimal b = toBigDecimal(args.get(1));
        if (a == null || b == null) return NullValue.INSTANCE;
        return new NumberValue(a.max(b));
    }

    static Value toNumber(List<Value> args, EvalContext ctx) {
        if (args.isEmpty() || !args.get(0).isDefined()) return NullValue.INSTANCE;
        BigDecimal value = toBigDecimal(args.get(0));
        if (value == null) return NullValue.INSTANCE;
        return new NumberValue(value);
    }

    private static BigDecimal toBigDecimal(Value v) {
        if (v instanceof NumberValue nv) return nv.value();
        if (v instanceof TextValue tv) {
            try {
                return new BigDecimal(tv.value());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
