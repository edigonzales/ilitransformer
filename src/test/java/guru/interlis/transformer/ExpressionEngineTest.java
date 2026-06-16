package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.expr.TextValue;
import guru.interlis.transformer.expr.Value;

import ch.interlis.iom_j.Iom_jObject;

import java.util.Map;

import org.junit.jupiter.api.Test;

class ExpressionEngineTest {
    @Test
    void evaluatesSourcePathAndIf() {
        ExpressionEngine engine = new ExpressionEngine();
        Iom_jObject src = new Iom_jObject("A.B", "1");
        src.setattrvalue("Name", "Alice");

        Value result = engine.evaluate("${s.Name}", Map.of("s", src));
        assertThat(result).isInstanceOf(TextValue.class);
        assertThat(((TextValue) result).value()).isEqualTo("Alice");

        Value ifResult = engine.evaluate("if(${s.Name} != null, 'ok', 'no')", Map.of("s", src));
        assertThat(ifResult).isInstanceOf(TextValue.class);
        assertThat(((TextValue) ifResult).value()).isEqualTo("ok");
    }

    @Test
    void evaluatesIfFalseBranch() {
        ExpressionEngine engine = new ExpressionEngine();
        Iom_jObject src = new Iom_jObject("A.B", "1");

        Value result = engine.evaluate("if(${s.Name} != null, 'ok', 'no')", Map.of("s", src));
        assertThat(result).isInstanceOf(TextValue.class);
        assertThat(((TextValue) result).value()).isEqualTo("no");
    }
}
