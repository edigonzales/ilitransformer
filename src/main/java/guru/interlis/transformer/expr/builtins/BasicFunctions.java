package guru.interlis.transformer.expr.builtins;

import guru.interlis.transformer.expr.BooleanValue;
import guru.interlis.transformer.expr.EvalContext;
import guru.interlis.transformer.expr.Expression;
import guru.interlis.transformer.expr.FunctionDef;
import guru.interlis.transformer.expr.FunctionRegistry;
import guru.interlis.transformer.expr.NumberValue;
import guru.interlis.transformer.expr.NullValue;
import guru.interlis.transformer.expr.TextValue;
import guru.interlis.transformer.expr.Value;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

public final class BasicFunctions {

    private BasicFunctions() {}

    public static void registerAll(FunctionRegistry registry) {
        registry.register(FunctionDef.lazy("coalesce",
                argTypes -> argTypes.isEmpty() ? TypeInfo.UNKNOWN : argTypes.get(0),
                List.of(new FunctionDef.FunctionParam("values", TypeInfo.UNKNOWN)),
                true, BasicFunctions::coalesceLazy));

        registry.register("defined", TypeInfo.BOOLEAN,
                List.of(new FunctionDef.FunctionParam("value", TypeInfo.UNKNOWN)),
                BasicFunctions::defined);

        registry.register("notDefined", TypeInfo.BOOLEAN,
                List.of(new FunctionDef.FunctionParam("value", TypeInfo.UNKNOWN)),
                BasicFunctions::notDefined);

        registry.register("isNull", TypeInfo.BOOLEAN,
                List.of(new FunctionDef.FunctionParam("value", TypeInfo.UNKNOWN)),
                BasicFunctions::isNull);

        registry.register("default", TypeInfo.TEXT,
                List.of(new FunctionDef.FunctionParam("value", TypeInfo.UNKNOWN),
                        new FunctionDef.FunctionParam("fallback", TypeInfo.UNKNOWN)),
                BasicFunctions::withDefault);

        registry.register("null", TypeInfo.UNKNOWN,
                List.of(), BasicFunctions::nullFn);

        registry.register("eq", TypeInfo.BOOLEAN,
                List.of(new FunctionDef.FunctionParam("a", TypeInfo.UNKNOWN),
                        new FunctionDef.FunctionParam("b", TypeInfo.UNKNOWN)),
                BasicFunctions::eq);

        registry.register("neq", TypeInfo.BOOLEAN,
                List.of(new FunctionDef.FunctionParam("a", TypeInfo.UNKNOWN),
                        new FunctionDef.FunctionParam("b", TypeInfo.UNKNOWN)),
                BasicFunctions::neq);

        registry.register("lt", TypeInfo.BOOLEAN,
                List.of(new FunctionDef.FunctionParam("a", TypeInfo.UNKNOWN),
                        new FunctionDef.FunctionParam("b", TypeInfo.UNKNOWN)),
                BasicFunctions::lt);

        registry.register("lte", TypeInfo.BOOLEAN,
                List.of(new FunctionDef.FunctionParam("a", TypeInfo.UNKNOWN),
                        new FunctionDef.FunctionParam("b", TypeInfo.UNKNOWN)),
                BasicFunctions::lte);

        registry.register("gt", TypeInfo.BOOLEAN,
                List.of(new FunctionDef.FunctionParam("a", TypeInfo.UNKNOWN),
                        new FunctionDef.FunctionParam("b", TypeInfo.UNKNOWN)),
                BasicFunctions::gt);

        registry.register("gte", TypeInfo.BOOLEAN,
                List.of(new FunctionDef.FunctionParam("a", TypeInfo.UNKNOWN),
                        new FunctionDef.FunctionParam("b", TypeInfo.UNKNOWN)),
                BasicFunctions::gte);

        registry.register("not", TypeInfo.BOOLEAN,
                List.of(new FunctionDef.FunctionParam("value", TypeInfo.UNKNOWN)),
                BasicFunctions::notFn);
    }

    static Value coalesceLazy(List<Expression> args, Function<Expression, Value> evaluator,
                               EvalContext ctx) {
        for (Expression arg : args) {
            Value val = evaluator.apply(arg);
            if (val.isDefined()) {
                return val;
            }
        }
        return NullValue.INSTANCE;
    }

    static Value defined(List<Value> args, EvalContext ctx) {
        if (args.isEmpty()) return BooleanValue.FALSE;
        return BooleanValue.of(args.get(0).isDefined());
    }

    static Value notDefined(List<Value> args, EvalContext ctx) {
        if (args.isEmpty()) return BooleanValue.TRUE;
        return BooleanValue.of(!args.get(0).isDefined());
    }

    static Value isNull(List<Value> args, EvalContext ctx) {
        if (args.isEmpty()) return BooleanValue.TRUE;
        return BooleanValue.of(args.get(0).isNull());
    }

    static Value withDefault(List<Value> args, EvalContext ctx) {
        if (args.size() < 2) return NullValue.INSTANCE;
        Value val = args.get(0);
        return val.isDefined() ? val : args.get(1);
    }

    static Value nullFn(List<Value> args, EvalContext ctx) {
        return NullValue.INSTANCE;
    }

    static Value eq(List<Value> args, EvalContext ctx) {
        if (args.size() < 2) return BooleanValue.FALSE;
        return BooleanValue.of(valuesEqual(args.get(0), args.get(1)));
    }

    static Value neq(List<Value> args, EvalContext ctx) {
        if (args.size() < 2) return BooleanValue.FALSE;
        return BooleanValue.of(!valuesEqual(args.get(0), args.get(1)));
    }

    static Value lt(List<Value> args, EvalContext ctx) {
        if (args.size() < 2) return BooleanValue.FALSE;
        int cmp = compareValues(args.get(0), args.get(1));
        return cmp < 0 ? BooleanValue.TRUE : BooleanValue.FALSE;
    }

    static Value lte(List<Value> args, EvalContext ctx) {
        if (args.size() < 2) return BooleanValue.FALSE;
        int cmp = compareValues(args.get(0), args.get(1));
        return cmp <= 0 ? BooleanValue.TRUE : BooleanValue.FALSE;
    }

    static Value gt(List<Value> args, EvalContext ctx) {
        if (args.size() < 2) return BooleanValue.FALSE;
        int cmp = compareValues(args.get(0), args.get(1));
        return cmp > 0 ? BooleanValue.TRUE : BooleanValue.FALSE;
    }

    static Value gte(List<Value> args, EvalContext ctx) {
        if (args.size() < 2) return BooleanValue.FALSE;
        int cmp = compareValues(args.get(0), args.get(1));
        return cmp >= 0 ? BooleanValue.TRUE : BooleanValue.FALSE;
    }

    static Value notFn(List<Value> args, EvalContext ctx) {
        if (args.isEmpty() || !args.get(0).isDefined()) return BooleanValue.TRUE;
        Value val = args.get(0);
        if (val instanceof BooleanValue bv) return BooleanValue.of(!bv.value());
        return BooleanValue.FALSE;
    }

    private static boolean valuesEqual(Value a, Value b) {
        if (!a.isDefined() && !b.isDefined()) return true;
        if (!a.isDefined() || !b.isDefined()) return false;
        if (a instanceof NumberValue na && b instanceof NumberValue nb) {
            return na.value().compareTo(nb.value()) == 0;
        }
        if (a instanceof BooleanValue ba && b instanceof BooleanValue bb) {
            return ba.value() == bb.value();
        }
        return a.asText().equals(b.asText());
    }

    private static int compareValues(Value a, Value b) {
        if (!a.isDefined() || !b.isDefined()) return 0;
        if (a instanceof NumberValue na && b instanceof NumberValue nb) {
            return na.value().compareTo(nb.value());
        }
        return a.asText().compareTo(b.asText());
    }
}
