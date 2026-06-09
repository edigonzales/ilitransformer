package guru.interlis.transformer.expr;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record NumberValue(BigDecimal value) implements Value {

    public NumberValue {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }

    public static NumberValue of(long value) {
        return new NumberValue(BigDecimal.valueOf(value));
    }

    public static NumberValue of(double value) {
        return new NumberValue(BigDecimal.valueOf(value));
    }

    public static NumberValue of(String value) {
        return new NumberValue(new BigDecimal(value));
    }

    @Override
    public double asNumber() {
        return value.doubleValue();
    }

    @Override
    public Object toNative() {
        if (value.scale() <= 0 || value.stripTrailingZeros().scale() <= 0) {
            return value.longValue();
        }
        return value.doubleValue();
    }
}
