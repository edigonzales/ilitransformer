package guru.interlis.transformer.expr;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.expr.builtins.BasicFunctions;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class BooleanOperatorsTest {

    private final ExpressionEngine engine = new ExpressionEngine();
    private final EvalContext ctx = new EvalContext(Map.of(), null, "r1");

    @Test
    void andTrueTrueIsTrue() {
        assertThat(engine.evaluate("true and true", ctx)).isEqualTo(BooleanValue.TRUE);
    }

    @Test
    void andTrueFalseIsFalse() {
        assertThat(engine.evaluate("true and false", ctx)).isEqualTo(BooleanValue.FALSE);
    }

    @Test
    void andShortCircuitsOnFalse() {
        AtomicBoolean secondEvaluated = new AtomicBoolean(false);
        FunctionRegistry registry = new FunctionRegistry();
        BasicFunctions.registerAll(registry);
        registry.registerNonDeterministic(
                "track", guru.interlis.transformer.mapping.plan.TypeInfo.BOOLEAN, java.util.List.of(), (args, c) -> {
                    secondEvaluated.set(true);
                    return BooleanValue.TRUE;
                });
        ExpressionEngine eng = new ExpressionEngine(registry);

        eng.evaluate("false and track()", ctx);
        assertThat(secondEvaluated.get()).isFalse();
    }

    @Test
    void orFalseFalseIsFalse() {
        assertThat(engine.evaluate("false or false", ctx)).isEqualTo(BooleanValue.FALSE);
    }

    @Test
    void orTrueFalseIsTrue() {
        assertThat(engine.evaluate("true or false", ctx)).isEqualTo(BooleanValue.TRUE);
    }

    @Test
    void orShortCircuitsOnTrue() {
        AtomicBoolean secondEvaluated = new AtomicBoolean(false);
        FunctionRegistry registry = new FunctionRegistry();
        BasicFunctions.registerAll(registry);
        registry.registerNonDeterministic(
                "track", guru.interlis.transformer.mapping.plan.TypeInfo.BOOLEAN, java.util.List.of(), (args, c) -> {
                    secondEvaluated.set(true);
                    return BooleanValue.TRUE;
                });
        ExpressionEngine eng = new ExpressionEngine(registry);

        eng.evaluate("true or track()", ctx);
        assertThat(secondEvaluated.get()).isFalse();
    }

    @Test
    void notTrueIsFalse() {
        assertThat(engine.evaluate("not true", ctx)).isEqualTo(BooleanValue.FALSE);
    }

    @Test
    void notFalseIsTrue() {
        assertThat(engine.evaluate("not false", ctx)).isEqualTo(BooleanValue.TRUE);
    }

    @Test
    void notNestedParens() {
        assertThat(engine.evaluate("not (false and true)", ctx)).isEqualTo(BooleanValue.TRUE);
    }
}
