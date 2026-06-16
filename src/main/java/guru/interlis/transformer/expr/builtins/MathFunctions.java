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
        return new NumberValue(value.divide(divisor, 10, java.math.RoundingMode.HALF_UP));
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
