package guru.interlis.transformer.expr;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public record XmlDateTimeValue(ZonedDateTime value) implements Value {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public String asText() {
        return format();
    }

    @Override
    public Object toNative() {
        return format();
    }

    @Override
    public String toString() {
        return format();
    }

    private String format() {
        return value.toLocalDateTime().format(FORMATTER);
    }
}
