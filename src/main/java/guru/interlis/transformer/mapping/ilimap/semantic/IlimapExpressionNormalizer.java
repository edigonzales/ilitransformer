package guru.interlis.transformer.mapping.ilimap.semantic;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapExpressionText;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IlimapExpressionNormalizer {

    private static final Pattern ENUM_MAP_PATTERN = Pattern.compile("enumMap\\s*\\(");

    public String normalizeForJobConfig(IlimapExpressionText expression, IlimapSymbolTable symbols) {
        return normalizeForJobConfig(expression.text(), symbols);
    }

    public String normalizeForJobConfig(String text, IlimapSymbolTable symbols) {
        Matcher matcher = ENUM_MAP_PATTERN.matcher(text);
        StringBuilder result = null;
        int lastCopied = 0;

        while (matcher.find()) {
            int parenStart = matcher.end() - 1;
            SecondArgBounds bounds = findSecondArgBounds(text, parenStart);
            if (bounds == null) {
                continue;
            }
            String arg = text.substring(bounds.start, bounds.end).strip();
            if (arg.isEmpty() || arg.startsWith("\"") || arg.startsWith("'")) {
                continue;
            }
            if (symbols.resolveEnumMap(arg).isEmpty()) {
                continue;
            }
            if (result == null) {
                result = new StringBuilder();
            }
            result.append(text, lastCopied, bounds.start);
            result.append("\"").append(arg).append("\"");
            lastCopied = bounds.end;
        }

        if (result == null) {
            return text;
        }
        result.append(text, lastCopied, text.length());
        return result.toString();
    }

    private static SecondArgBounds findSecondArgBounds(String text, int openParenIndex) {
        int depth = 0;
        int commaIndex = -1;
        for (int i = openParenIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    if (commaIndex >= 0) {
                        int start = commaIndex + 1;
                        int end = i;
                        while (start < end && Character.isWhitespace(text.charAt(start))) {
                            start++;
                        }
                        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
                            end--;
                        }
                        return new SecondArgBounds(start, end);
                    }
                    return null;
                }
            } else if (c == ',' && depth == 1) {
                if (commaIndex < 0) {
                    commaIndex = i;
                }
            } else if (c == '"') {
                i = skipString(text, i, '"');
            } else if (c == '\'') {
                i = skipString(text, i, '\'');
            }
        }
        return null;
    }

    private static int skipString(String text, int quoteIndex, char quoteChar) {
        for (int i = quoteIndex + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quoteChar) {
                return i;
            }
        }
        return text.length() - 1;
    }

    private record SecondArgBounds(int start, int end) {}
}
