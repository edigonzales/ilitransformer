package guru.interlis.transformer.expr;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public record DateValue(LocalDate value) implements Value {
    private static final DateTimeFormatter ILI1_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    @Override
    public String asText() {
        return value.toString();
    }

    @Override
    public Object toNative() {
        return value.format(ILI1_DATE);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
