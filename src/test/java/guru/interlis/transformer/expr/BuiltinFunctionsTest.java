package guru.interlis.transformer.expr;

import ch.interlis.iom_j.Iom_jObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class BuiltinFunctionsTest {

    private ExpressionEngine engine;
    private Iom_jObject src;

    @BeforeEach
    void setUp() {
        engine = new ExpressionEngine();
        src = new Iom_jObject("A.B", "1");
        src.setattrvalue("Name", "Alice");
        src.setattrvalue("Empty", "");
        src.setattrvalue("Number", "42");
    }

    // -- Basic functions -------------------------------------------

    @Test
    void coalesceReturnsFirstDefined() {
        Value result = engine.evaluate("coalesce(${s.Missing}, ${s.Name}, 'fallback')", Map.of("s", src));
        assertThat(result).isInstanceOf(TextValue.class);
        assertThat(((TextValue) result).value()).isEqualTo("Alice");
    }

    @Test
    void coalesceReturnsNullWhenAllNull() {
        Value result = engine.evaluate("coalesce(${s.Missing}, ${s.Other})", Map.of("s", src));
        assertThat(result.isNull()).isTrue();
    }

    @Test
    void coalesceSkipsNulls() {
        Value result = engine.evaluate("coalesce(${s.Missing}, ${s.Missing}, 'third')", Map.of("s", src));
        assertThat(result).isInstanceOf(TextValue.class);
        assertThat(((TextValue) result).value()).isEqualTo("third");
    }

    @Test
    void definedReturnsTrue() {
        Value result = engine.evaluate("defined(${s.Name})", Map.of("s", src));
        assertThat(result).isInstanceOf(BooleanValue.class);
        assertThat(((BooleanValue) result).value()).isTrue();
    }

    @Test
    void definedReturnsFalse() {
        Value result = engine.evaluate("defined(${s.Missing})", Map.of("s", src));
        assertThat(result).isInstanceOf(BooleanValue.class);
        assertThat(((BooleanValue) result).value()).isFalse();
    }

    @Test
    void notDefinedReturnsTrue() {
        Value result = engine.evaluate("notDefined(${s.Missing})", Map.of("s", src));
        assertThat(result).isInstanceOf(BooleanValue.class);
        assertThat(((BooleanValue) result).value()).isTrue();
    }

    @Test
    void notDefinedReturnsFalse() {
        Value result = engine.evaluate("notDefined(${s.Name})", Map.of("s", src));
        assertThat(result).isInstanceOf(BooleanValue.class);
        assertThat(((BooleanValue) result).value()).isFalse();
    }

    @Test
    void defaultReturnsValueIfDefined() {
        Value result = engine.evaluate("default(${s.Name}, 'fallback')", Map.of("s", src));
        assertThat(((TextValue) result).value()).isEqualTo("Alice");
    }

    @Test
    void defaultReturnsFallbackIfNotDefined() {
        Value result = engine.evaluate("default(${s.Missing}, 'fallback')", Map.of("s", src));
        assertThat(((TextValue) result).value()).isEqualTo("fallback");
    }

    @Test
    void nullFnReturnsNull() {
        Value result = engine.evaluate("null()", Map.of("s", src));
        assertThat(result.isNull()).isTrue();
    }

    // -- String functions ------------------------------------------

    @Test
    void concatJoinsStrings() {
        Value result = engine.evaluate("concat('Hello', ' ', ${s.Name})", Map.of("s", src));
        assertThat(((TextValue) result).value()).isEqualTo("Hello Alice");
    }

    @Test
    void substringWorks() {
        Value result = engine.evaluate("substring(${s.Name}, 1, 3)", Map.of("s", src));
        assertThat(((TextValue) result).value()).isEqualTo("lic");
    }

    @Test
    void substringReturnsEmptyForOutOfBounds() {
        Value result = engine.evaluate("substring(${s.Name}, 100, 3)", Map.of("s", src));
        assertThat(((TextValue) result).value()).isEqualTo("");
    }

    @Test
    void trimRemovesWhitespace() {
        Value result = engine.evaluate("trim('  padded  ')", Map.of("s", src));
        assertThat(((TextValue) result).value()).isEqualTo("padded");
    }

    @Test
    void upperUppercases() {
        Value result = engine.evaluate("upper(${s.Name})", Map.of("s", src));
        assertThat(((TextValue) result).value()).isEqualTo("ALICE");
    }

    @Test
    void lowerLowercases() {
        Value result = engine.evaluate("lower(${s.Name})", Map.of("s", src));
        assertThat(((TextValue) result).value()).isEqualTo("alice");
    }

    @Test
    void replaceReplacesSubstring() {
        Value result = engine.evaluate("replace('Hello World', 'World', 'Alice')", Map.of("s", src));
        assertThat(((TextValue) result).value()).isEqualTo("Hello Alice");
    }

    @Test
    void truncateShortens() {
        Value result = engine.evaluate("truncate('Hello World', 5)", Map.of("s", src));
        assertThat(((TextValue) result).value()).isEqualTo("Hello");
    }

    @Test
    void truncateReturnsSameIfShorter() {
        Value result = engine.evaluate("truncate(${s.Name}, 20)", Map.of("s", src));
        assertThat(((TextValue) result).value()).isEqualTo("Alice");
    }

    @Test
    void truncateWithZeroReturnsEmpty() {
        Value result = engine.evaluate("truncate('Hello', 0)", Map.of("s", src));
        assertThat(((TextValue) result).value()).isEqualTo("");
    }

    // -- Date functions --------------------------------------------

    @Test
    void toXmlDateTimeConvertsDateString() {
        Value result = engine.evaluate("toXmlDateTime('2024-01-15T00:00:00+00:00')", Map.of("s", src));
        assertThat(result).isInstanceOf(XmlDateTimeValue.class);
    }

    @Test
    void toXmlDateTimeConvertsPlainDate() {
        Value result = engine.evaluate("toXmlDateTime('2024-01-15')", Map.of("s", src));
        assertThat(result).isInstanceOf(XmlDateTimeValue.class);
    }

    @Test
    void toXmlDateTimeConvertsIli1CompactDate() {
        Value result = engine.evaluate("toXmlDateTime('20240115')", Map.of("s", src));
        assertThat(result).isInstanceOf(XmlDateTimeValue.class);
        assertThat(result.asText()).isEqualTo("2024-01-15T12:00:00");
    }

    @Test
    void toDateConvertsIli1CompactDate() {
        Value result = engine.evaluate("toDate('20240115')", Map.of("s", src));
        assertThat(result).isInstanceOf(DateValue.class);
    }

    @Test
    void toDateConvertsLocalXmlDateTime() {
        Value result = engine.evaluate("toDate('2024-01-15T12:00:00')", Map.of("s", src));
        assertThat(result).isInstanceOf(DateValue.class);
        assertThat(result.asText()).isEqualTo("2024-01-15");
        assertThat(result.toNative()).isEqualTo("20240115");
    }

    @Test
    void toXmlDateTimeReturnsNullForInvalid() {
        Value result = engine.evaluate("toXmlDateTime('not-a-date')", Map.of("s", src));
        assertThat(result.isNull()).isTrue();
    }

    @Test
    void nowReturnsCurrentDateTime() {
        Value result = engine.evaluate("now()", Map.of("s", src));
        assertThat(result).isInstanceOf(XmlDateTimeValue.class);
    }

    // -- Enum functions --------------------------------------------

    @Test
    void enumNameReturnsName() {
        Value result = engine.evaluate("enumName(#LFP3)", Map.of("s", src));
        assertThat(result).isInstanceOf(TextValue.class);
        assertThat(((TextValue) result).value()).isEqualTo("LFP3");
    }

    @Test
    void enumDefaultUsesValueIfDefined() {
        Value result = engine.evaluate("enumDefault(#LFP3, 'fallback')", Map.of("s", src));
        assertThat(result).isInstanceOf(EnumValue.class);
        assertThat(((EnumValue) result).name()).isEqualTo("LFP3");
    }

    @Test
    void enumDefaultUsesFallbackIfNotDefined() {
        Value result = engine.evaluate("enumDefault(${s.Missing}, 'fallback')", Map.of("s", src));
        assertThat(result).isInstanceOf(EnumValue.class);
        assertThat(((EnumValue) result).name()).isEqualTo("fallback");
    }

    // -- Conditional (if) ------------------------------------------

    @Test
    void ifWithTrueCondition() {
        Value result = engine.evaluate("if(true, 'yes', 'no')", Map.of("s", src));
        assertThat(((TextValue) result).value()).isEqualTo("yes");
    }

    @Test
    void ifWithFalseCondition() {
        Value result = engine.evaluate("if(false, 'yes', 'no')", Map.of("s", src));
        assertThat(((TextValue) result).value()).isEqualTo("no");
    }

    @Test
    void ifWithDefinedSource() {
        Value result = engine.evaluate("if(${s.Name} != null, 'present', 'absent')", Map.of("s", src));
        assertThat(((TextValue) result).value()).isEqualTo("present");
    }

    @Test
    void ifWithUndefinedSource() {
        Value result = engine.evaluate("if(${s.Missing} != null, 'present', 'absent')", Map.of("s", src));
        assertThat(((TextValue) result).value()).isEqualTo("absent");
    }

    @Test
    void ifWithIsNullCondition() {
        Value result = engine.evaluate("if(${s.Missing} == null, 'absent', 'present')", Map.of("s", src));
        assertThat(((TextValue) result).value()).isEqualTo("absent");
    }

    // -- Nested expressions ----------------------------------------

    @Test
    void nestedFunctionCalls() {
        Value result = engine.evaluate("truncate(trim('  long text needs trimming  '), 4)", Map.of("s", src));
        assertThat(((TextValue) result).value()).isEqualTo("long");
    }

    @Test
    void coalesceWithFunctionArg() {
        Value result = engine.evaluate("coalesce(upper(${s.Missing}), trim('  hello  '))", Map.of("s", src));
        assertThat(((TextValue) result).value()).isEqualTo("hello");
    }

    // -- Error handling --------------------------------------------

    @Test
    void unknownFunctionReturnsNull() {
        Value result = engine.evaluate("unknownFunc(1, 2)", Map.of("s", src));
        assertThat(result.isNull()).isTrue();
    }
}
