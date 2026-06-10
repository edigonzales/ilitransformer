package guru.interlis.transformer.expr.builtins;

import guru.interlis.transformer.expr.DateValue;
import guru.interlis.transformer.expr.EvalContext;
import guru.interlis.transformer.expr.FunctionDef;
import guru.interlis.transformer.expr.FunctionRegistry;
import guru.interlis.transformer.expr.NullValue;
import guru.interlis.transformer.expr.Value;
import guru.interlis.transformer.expr.XmlDateTimeValue;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public final class DateFunctions {

    private static final DateTimeFormatter ILI1_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private DateFunctions() {}

    public static void registerAll(FunctionRegistry registry) {
        registry.register("toXmlDateTime", TypeInfo.XML_DATE_TIME,
                List.of(new FunctionDef.FunctionParam("value", TypeInfo.UNKNOWN)),
                DateFunctions::toXmlDateTime);

        registry.register("toInterlis1Date", TypeInfo.TEXT,
                List.of(new FunctionDef.FunctionParam("value", TypeInfo.UNKNOWN)),
                DateFunctions::toInterlis1Date);

        registry.register("toDate", TypeInfo.DATE,
                List.of(new FunctionDef.FunctionParam("value", TypeInfo.UNKNOWN)),
                DateFunctions::toDate);

        registry.registerNonDeterministic("now", TypeInfo.XML_DATE_TIME,
                List.of(), DateFunctions::now);
    }

    static Value toInterlis1Date(List<Value> args, EvalContext ctx) {
        if (args.isEmpty() || !args.get(0).isDefined()) return NullValue.INSTANCE;
        Value val = args.get(0);
        String text = val.asText();
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            return new guru.interlis.transformer.expr.TextValue(zdt.format(DateTimeFormatter.BASIC_ISO_DATE));
        } catch (DateTimeParseException e1) {
            try {
                LocalDateTime dateTime = LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                return new guru.interlis.transformer.expr.TextValue(dateTime.format(DateTimeFormatter.BASIC_ISO_DATE));
            } catch (DateTimeParseException e2) {
                try {
                    LocalDate date = LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
                    return new guru.interlis.transformer.expr.TextValue(date.format(DateTimeFormatter.BASIC_ISO_DATE));
                } catch (DateTimeParseException e3) {
                    try {
                        LocalDate date = LocalDate.parse(text, ILI1_DATE);
                        return new guru.interlis.transformer.expr.TextValue(date.format(DateTimeFormatter.BASIC_ISO_DATE));
                    } catch (DateTimeParseException e4) {
                        return NullValue.INSTANCE;
                    }
                }
            }
        }
    }

    static Value toDate(List<Value> args, EvalContext ctx) {
        if (args.isEmpty() || !args.get(0).isDefined()) return NullValue.INSTANCE;
        Value val = args.get(0);

        if (val instanceof XmlDateTimeValue xdt) {
            return new DateValue(xdt.value().toLocalDate());
        }

        String text = val.asText();
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            return new DateValue(zdt.toLocalDate());
        } catch (DateTimeParseException e1) {
            try {
                LocalDateTime dateTime = LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                return new DateValue(dateTime.toLocalDate());
            } catch (DateTimeParseException e2) {
                try {
                    LocalDate date = LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
                    return new DateValue(date);
                } catch (DateTimeParseException e3) {
                    try {
                        LocalDate date = LocalDate.parse(text, ILI1_DATE);
                        return new DateValue(date);
                    } catch (DateTimeParseException e4) {
                        return NullValue.INSTANCE;
                    }
                }
            }
        }
    }

    static Value toXmlDateTime(List<Value> args, EvalContext ctx) {
        if (args.isEmpty() || !args.get(0).isDefined()) return NullValue.INSTANCE;
        Value val = args.get(0);
        String text = val.asText();

        try {
            return new XmlDateTimeValue(ZonedDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        } catch (DateTimeParseException e1) {
            try {
                LocalDateTime dateTime = LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                return new XmlDateTimeValue(dateTime.atZone(ZoneOffset.UTC));
            } catch (DateTimeParseException e2) {
                try {
                    LocalDate date = LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
                    return new XmlDateTimeValue(date.atTime(LocalTime.NOON).atZone(ZoneOffset.UTC));
                } catch (DateTimeParseException e3) {
                    try {
                        LocalDate date = LocalDate.parse(text, ILI1_DATE);
                        return new XmlDateTimeValue(date.atTime(LocalTime.NOON).atZone(ZoneOffset.UTC));
                    } catch (DateTimeParseException e4) {
                        return NullValue.INSTANCE;
                    }
                }
            }
        }
    }

    static Value now(List<Value> args, EvalContext ctx) {
        return new XmlDateTimeValue(ZonedDateTime.now(ZoneOffset.UTC));
    }
}
