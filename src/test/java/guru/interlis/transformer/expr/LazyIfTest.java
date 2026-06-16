package guru.interlis.transformer.expr;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.expr.builtins.BasicFunctions;
import guru.interlis.transformer.mapping.plan.TypeInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class LazyIfTest {

    @Test
    void ifTrueEvaluatesThenBranchOnly() {
        FunctionRegistry registry = new FunctionRegistry();
        BasicFunctions.registerAll(registry);

        AtomicBoolean elseCalled = new AtomicBoolean(false);

        registry.registerNonDeterministic("error", TypeInfo.UNKNOWN, List.of(), (args, ctx) -> {
            elseCalled.set(true);
            throw new RuntimeException("should not be called");
        });

        ExpressionEngine engine = new ExpressionEngine(registry);
        EvalContext ctx = new EvalContext(Map.of(), null, "r1");

        Value result = engine.evaluate("if(true, 'yes', error())", ctx);
        assertThat(result).isInstanceOf(TextValue.class);
        assertThat(((TextValue) result).value()).isEqualTo("yes");
        assertThat(elseCalled.get()).isFalse();
    }

    @Test
    void ifFalseEvaluatesElseBranch() {
        ExpressionEngine engine = new ExpressionEngine();
        EvalContext ctx = new EvalContext(Map.of(), null, "r1");

        Value result = engine.evaluate("if(false, 'yes', 'no')", ctx);
        assertThat(result).isInstanceOf(TextValue.class);
        assertThat(((TextValue) result).value()).isEqualTo("no");
    }

    @Test
    void ifWithNullConditionIsFalsy() {
        ExpressionEngine engine = new ExpressionEngine();
        EvalContext ctx = new EvalContext(Map.of(), null, "r1");

        Value result = engine.evaluate("if(null, 'yes', 'no')", ctx);
        assertThat(result).isInstanceOf(TextValue.class);
        assertThat(((TextValue) result).value()).isEqualTo("no");
    }
}
