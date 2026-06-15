package guru.interlis.transformer.mapping.plan;

public final class FailPolicyParser {
    private FailPolicyParser() {}

    public static FailPolicy parseOrDefault(String value, FailPolicy defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        if (value.equalsIgnoreCase("strict")) return FailPolicy.STRICT;
        if (value.equalsIgnoreCase("lenient")) return FailPolicy.LENIENT;
        if (value.equalsIgnoreCase("report_only") || value.equalsIgnoreCase("reportOnly")) {
            return FailPolicy.REPORT_ONLY;
        }
        throw new IllegalArgumentException("Unknown failPolicy: " + value
                + " (valid values: strict, lenient, report_only/reportOnly)");
    }
}
