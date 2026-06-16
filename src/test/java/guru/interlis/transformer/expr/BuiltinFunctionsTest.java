package guru.interlis.transformer.expr;

import static org.assertj.core.api.Assertions.*;

import ch.interlis.iom_j.Iom_jObject;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @Test
    void enumMapReturnsBooleanForBooleanMappingValue() {
        Map<String, Map<String, String>> enumMaps = Map.of("Map", Map.of("ja", "true", "nein", "false"));
        EvalContext ctx = new EvalContext(Map.of(), null, "r1").withEnumMaps(enumMaps);

        Value result = engine.evaluate("enumMap('ja', 'Map')", ctx);
        assertThat(result).isInstanceOf(BooleanValue.class);
        assertThat(((BooleanValue) result).value()).isTrue();
    }

    @Test
    void enumMapReturnsNumberForNumericMappingValue() {
        Map<String, Map<String, String>> enumMaps = Map.of("Map", Map.of("A", "42", "B", "99"));
        EvalContext ctx = new EvalContext(Map.of(), null, "r1").withEnumMaps(enumMaps);

        Value result = engine.evaluate("enumMap('A', 'Map')", ctx);
        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(result.asNumber()).isEqualTo(42.0);
    }

    @Test
    void enumMapReturnsEnumForEnumMappingValue() {
        Map<String, Map<String, String>> enumMaps = Map.of("Map", Map.of("A", "LFP3", "B", "LFP4"));
        EvalContext ctx = new EvalContext(Map.of(), null, "r1").withEnumMaps(enumMaps);

        Value result = engine.evaluate("enumMap('A', 'Map')", ctx);
        assertThat(result).isInstanceOf(EnumValue.class);
        assertThat(((EnumValue) result).name()).isEqualTo("LFP3");
    }

    @Test
    void enumMapReturnsNullAndWarningForMissingSourceValue() {
        Map<String, Map<String, String>> enumMaps = Map.of("Map", Map.of("A", "X"));
        guru.interlis.transformer.diag.DiagnosticCollector diagnostics =
                new guru.interlis.transformer.diag.DiagnosticCollector();
        EvalContext ctx = new EvalContext(Map.of(), diagnostics, "r1").withEnumMaps(enumMaps);

        Value result = engine.evaluate("enumMap('Unknown', 'Map')", ctx);
        assertThat(result.isNull()).isTrue();
        assertThat(diagnostics.warnings()).isGreaterThan(0);
        assertThat(diagnostics.all()).anyMatch(d -> d.code().equals("ILITRF-EXPR-TYPE"));
    }

    @Test
    void enumMapStrictReportsErrorForMissingSourceValue() {
        Map<String, Map<String, String>> enumMaps = Map.of("Map", Map.of("A", "X"));
        guru.interlis.transformer.diag.DiagnosticCollector diagnostics =
                new guru.interlis.transformer.diag.DiagnosticCollector();
        EvalContext ctx = new EvalContext(Map.of(), diagnostics, "r1").withEnumMaps(enumMaps);

        Value result = engine.evaluate("enumMapStrict('Unknown', 'Map')", ctx);
        assertThat(result.isNull()).isTrue();
        assertThat(diagnostics.errors()).isGreaterThan(0);
    }

    @Test
    void enumMapDefaultReturnsFallbackForMissingSourceValue() {
        Map<String, Map<String, String>> enumMaps = Map.of("Map", Map.of("A", "X"));
        guru.interlis.transformer.diag.DiagnosticCollector diagnostics =
                new guru.interlis.transformer.diag.DiagnosticCollector();
        EvalContext ctx = new EvalContext(Map.of(), diagnostics, "r1").withEnumMaps(enumMaps);

        Value result = engine.evaluate("enumMapDefault('Unknown', 'Map', 'Default')", ctx);
        assertThat(result).isInstanceOf(TextValue.class);
        assertThat(((TextValue) result).value()).isEqualTo("Default");
        assertThat(diagnostics.warnings()).isZero();
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

    // -- Math functions ---------------------------------------------

    @Test
    void divDividesNumbers() {
        Value result = engine.evaluate("div(10, 2)", Map.of("s", src));
        assertThat(result.asNumber()).isEqualTo(5.0);
    }

    @Test
    void divReturnsNullForZeroDivisor() {
        Value result = engine.evaluate("div(10, 0)", Map.of("s", src));
        assertThat(result.isNull()).isTrue();
    }

    @Test
    void mulMultipliesNumbers() {
        Value result = engine.evaluate("mul(3, 4)", Map.of("s", src));
        assertThat(result.asNumber()).isEqualTo(12.0);
    }

    @Test
    void addAddsNumbers() {
        Value result = engine.evaluate("add(3, 4)", Map.of("s", src));
        assertThat(result.asNumber()).isEqualTo(7.0);
    }

    @Test
    void subSubtractsNumbers() {
        Value result = engine.evaluate("sub(10, 3)", Map.of("s", src));
        assertThat(result.asNumber()).isEqualTo(7.0);
    }

    @Test
    void roundRoundsHalfUpToScale() {
        Value result = engine.evaluate("round(3.14159, 2)", Map.of("s", src));
        assertThat(result.asNumber()).isEqualTo(3.14);
    }

    @Test
    void absReturnsAbsoluteValue() {
        Value positive = engine.evaluate("abs(5)", Map.of("s", src));
        assertThat(positive.asNumber()).isEqualTo(5.0);

        Value negative = engine.evaluate("abs(-5)", Map.of("s", src));
        assertThat(negative.asNumber()).isEqualTo(5.0);
    }

    @Test
    void minReturnsSmallerNumber() {
        Value result = engine.evaluate("min(3, 7)", Map.of("s", src));
        assertThat(result.asNumber()).isEqualTo(3.0);
    }

    @Test
    void maxReturnsLargerNumber() {
        Value result = engine.evaluate("max(3, 7)", Map.of("s", src));
        assertThat(result.asNumber()).isEqualTo(7.0);
    }

    @Test
    void toNumberConvertsTextNumber() {
        Value result = engine.evaluate("toNumber('42.5')", Map.of("s", src));
        assertThat(result).isInstanceOf(NumberValue.class);
        assertThat(result.asNumber()).isEqualTo(42.5);
    }

    @Test
    void toNumberReturnsNullForInvalidText() {
        Value result = engine.evaluate("toNumber('not-a-number')", Map.of("s", src));
        assertThat(result.isNull()).isTrue();
    }
}
