package guru.interlis.transformer.io;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Read-only, typed accessor over the generic format options declared on an input/output binding.
 *
 * <p>Values are always stored as strings (the canonical representation after YAML/{@code .ilimap}
 * normalization). Typed getters parse on demand and throw {@link IllegalArgumentException} with a
 * precise message when a value cannot be interpreted; callers (providers) turn those into
 * diagnostics.
 */
public final class FormatOptions {

    private static final Set<String> TRUE_VALUES = Set.of("true", "yes", "1");
    private static final Set<String> FALSE_VALUES = Set.of("false", "no", "0");

    private final Map<String, String> values;

    public FormatOptions(Map<String, String> values) {
        this.values = values == null ? Map.of() : Map.copyOf(values);
    }

    public static FormatOptions of(Map<String, String> values) {
        return new FormatOptions(values);
    }

    public String get(String key) {
        return values.get(key);
    }

    public String getOrDefault(String key, String defaultValue) {
        String value = values.get(key);
        return value != null ? value : defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = values.get(key);
        if (value == null) {
            return defaultValue;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (TRUE_VALUES.contains(normalized)) {
            return true;
        }
        if (FALSE_VALUES.contains(normalized)) {
            return false;
        }
        throw new IllegalArgumentException(
                "Invalid boolean option '" + key + "': expected one of true/false/yes/no/1/0, got '" + value + "'");
    }

    public int getInt(String key, int defaultValue) {
        String value = values.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid integer option '" + key + "': expected a whole number, got '" + value + "'");
        }
    }

    public char getChar(String key, char defaultValue) {
        String value = values.get(key);
        if (value == null) {
            return defaultValue;
        }
        switch (value.toLowerCase(Locale.ROOT)) {
            case "tab", "\\t":
                return '\t';
            case "semicolon":
                return ';';
            case "comma":
                return ',';
            default:
                break;
        }
        if (value.length() == 1) {
            return value.charAt(0);
        }
        throw new IllegalArgumentException("Invalid option '" + key
                + "': expected a single character (or one of tab, \\t, semicolon, comma), got '" + value + "'");
    }

    public String require(String key) {
        String value = values.get(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Missing required option '" + key + "'");
        }
        return value;
    }

    public Map<String, String> asMap() {
        return new LinkedHashMap<>(values);
    }
}
