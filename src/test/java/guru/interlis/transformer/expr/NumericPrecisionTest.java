package guru.interlis.transformer.expr;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class NumericPrecisionTest {

    @Test
    void numberValueStoresBigDecimalPrecisely() {
        NumberValue v = new NumberValue(new BigDecimal("0.1"));
        assertThat(v.value()).isEqualTo(new BigDecimal("0.1"));
        assertThat(v.asNumber()).isEqualTo(0.1);
    }

    @Test
    void integerStoredAsExactBigDecimal() {
        NumberValue v = new NumberValue(new BigDecimal("42"));
        assertThat(v.value()).isEqualTo(new BigDecimal("42"));
        assertThat(v.toNative()).isEqualTo(42L);
    }

    @Test
    void decimalStoredExactly() {
        NumberValue v = new NumberValue(new BigDecimal("3.14159"));
        assertThat(v.value().toString()).isEqualTo("3.14159");
    }

    @Test
    void factoryOfLongCreatesExact() {
        NumberValue v = NumberValue.of(42L);
        assertThat(v.value()).isEqualTo(new BigDecimal("42"));
    }

    @Test
    void factoryOfDoubleCreatesViaBigDecimal() {
        NumberValue v = NumberValue.of(0.1);
        assertThat(v.value().toString()).isEqualTo("0.1");
    }

    @Test
    void stringConstructorCreatesExact() {
        NumberValue v = NumberValue.of("0.123456789");
        assertThat(v.value().toString()).isEqualTo("0.123456789");
    }
}
