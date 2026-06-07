package guru.interlis.transformer;

import ch.interlis.iom_j.Iom_jObject;
import guru.interlis.transformer.expr.ExpressionEngine;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExpressionEngineTest {
    @Test
    void evaluatesSourcePathAndIf() {
        ExpressionEngine engine = new ExpressionEngine();
        Iom_jObject src = new Iom_jObject("A.B", "1");
        src.setattrvalue("Name", "Alice");

        assertThat(engine.evaluate("${s.Name}", Map.of("s", src))).isEqualTo("Alice");
        assertThat(engine.evaluate("if(${s.Name} != null, 'ok', 'no')", Map.of("s", src))).isEqualTo("ok");
    }
}
