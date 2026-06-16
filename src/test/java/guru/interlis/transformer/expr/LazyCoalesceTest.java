package guru.interlis.transformer.expr;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.expr.builtins.BasicFunctions;
import guru.interlis.transformer.mapping.plan.TypeInfo;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class LazyCoalesceTest {

    @Test
    void coalesceDoesNotEvaluateLaterArgsWhenEarlierDefined() {
        FunctionRegistry registry = new FunctionRegistry();
        BasicFunctions.registerAll(registry);

        AtomicBoolean nonDetCalled = new AtomicBoolean(false);

        registry.registerNonDeterministic("sideEffect", TypeInfo.XML_DATE_TIME, List.of(), (args, ctx) -> {
            nonDetCalled.set(true);
            return new XmlDateTimeValue(ZonedDateTime.now());
        });

        ExpressionEngine engine = new ExpressionEngine(registry);
        EvalContext ctx = new EvalContext(Map.of(), null, "r1");

        Value result = engine.evaluate("coalesce('defined', sideEffect())", ctx);
        assertThat(result).isInstanceOf(TextValue.class);
        assertThat(((TextValue) result).value()).isEqualTo("defined");
        assertThat(nonDetCalled.get()).isFalse();
    }

    @Test
    void coalesceFallsThroughToLaterArgsWhenUndefined() {
        FunctionRegistry registry = new FunctionRegistry();
        BasicFunctions.registerAll(registry);

        ExpressionEngine engine = new ExpressionEngine(registry);
        EvalContext ctx = new EvalContext(Map.of(), null, "r1");

        Value result = engine.evaluate("coalesce(null, 42)", ctx);
        assertThat(result).isInstanceOf(NumberValue.class);
    }

    @Test
    void coalesceAllUndefinedReturnsNull() {
        FunctionRegistry registry = new FunctionRegistry();
        BasicFunctions.registerAll(registry);

        ExpressionEngine engine = new ExpressionEngine(registry);
        EvalContext ctx = new EvalContext(Map.of(), null, "r1");

        Value result = engine.evaluate("coalesce(null, null)", ctx);
        assertThat(result.isDefined()).isFalse();
    }
}
