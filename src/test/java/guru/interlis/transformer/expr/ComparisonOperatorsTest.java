package guru.interlis.transformer.expr;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ComparisonOperatorsTest {

    private final ExpressionEngine engine = new ExpressionEngine();
    private final EvalContext ctx = new EvalContext(Map.of(), null, "r1");

    @Test
    void eqNumbers() {
        assertThat(engine.evaluate("42 == 42", ctx)).isEqualTo(BooleanValue.TRUE);
        assertThat(engine.evaluate("42 == 7", ctx)).isEqualTo(BooleanValue.FALSE);
    }

    @Test
    void neqNumbers() {
        assertThat(engine.evaluate("42 != 7", ctx)).isEqualTo(BooleanValue.TRUE);
        assertThat(engine.evaluate("42 != 42", ctx)).isEqualTo(BooleanValue.FALSE);
    }

    @Test
    void ltNumbers() {
        assertThat(engine.evaluate("5 < 10", ctx)).isEqualTo(BooleanValue.TRUE);
        assertThat(engine.evaluate("10 < 5", ctx)).isEqualTo(BooleanValue.FALSE);
        assertThat(engine.evaluate("5 < 5", ctx)).isEqualTo(BooleanValue.FALSE);
    }

    @Test
    void lteNumbers() {
        assertThat(engine.evaluate("5 <= 10", ctx)).isEqualTo(BooleanValue.TRUE);
        assertThat(engine.evaluate("5 <= 5", ctx)).isEqualTo(BooleanValue.TRUE);
        assertThat(engine.evaluate("10 <= 5", ctx)).isEqualTo(BooleanValue.FALSE);
    }

    @Test
    void gtNumbers() {
        assertThat(engine.evaluate("10 > 5", ctx)).isEqualTo(BooleanValue.TRUE);
        assertThat(engine.evaluate("5 > 10", ctx)).isEqualTo(BooleanValue.FALSE);
        assertThat(engine.evaluate("5 > 5", ctx)).isEqualTo(BooleanValue.FALSE);
    }

    @Test
    void gteNumbers() {
        assertThat(engine.evaluate("10 >= 5", ctx)).isEqualTo(BooleanValue.TRUE);
        assertThat(engine.evaluate("5 >= 5", ctx)).isEqualTo(BooleanValue.TRUE);
        assertThat(engine.evaluate("5 >= 10", ctx)).isEqualTo(BooleanValue.FALSE);
    }

    @Test
    void eqStrings() {
        assertThat(engine.evaluate("\"abc\" == \"abc\"", ctx)).isEqualTo(BooleanValue.TRUE);
        assertThat(engine.evaluate("\"abc\" == \"xyz\"", ctx)).isEqualTo(BooleanValue.FALSE);
    }

    @Test
    void ltStrings() {
        assertThat(engine.evaluate("\"a\" < \"b\"", ctx)).isEqualTo(BooleanValue.TRUE);
        assertThat(engine.evaluate("\"b\" < \"a\"", ctx)).isEqualTo(BooleanValue.FALSE);
    }

    @Test
    void nullEqualsNullIsDefined() {
        Value result = engine.evaluate("null == null", ctx);
        assertThat(result).isInstanceOf(BooleanValue.class);
    }

    @Test
    void decimalComparison() {
        assertThat(engine.evaluate("3.14 > 3.0", ctx)).isEqualTo(BooleanValue.TRUE);
        assertThat(engine.evaluate("0.1 + 0.2 == 0.3", ctx)).isNotEqualTo(BooleanValue.TRUE);
    }
}
