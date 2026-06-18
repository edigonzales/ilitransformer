package guru.interlis.transformer.mapping.ilimap.semantic;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import java.util.regex.Pattern;

public final class IlimapIdentifierRules {
    private IlimapIdentifierRules() {}

    private static final Pattern SYMBOL_ID_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");
    private static final Pattern ALIAS_ID_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    public static boolean isValidSymbolId(String value) {
        return value != null
                && SYMBOL_ID_PATTERN.matcher(value).matches()
                && !IlimapReservedWords.isReserved(value);
    }

    public static boolean isValidAliasId(String value) {
        return value != null
                && ALIAS_ID_PATTERN.matcher(value).matches()
                && !IlimapReservedWords.isReserved(value);
    }

    public static void requireSymbolId(String value, DiagnosticCollector diagnostics, IlimapSourceRange range) {
        if (value == null || value.isEmpty()) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.ILIMAP_INVALID_SYMBOL_ID,
                    Severity.ERROR,
                    "symbol ID must not be empty",
                    formatRange(range),
                    null));
            return;
        }
        if (IlimapReservedWords.isReserved(value)) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.ILIMAP_RESERVED_WORD,
                    Severity.ERROR,
                    "'" + value + "' is a reserved word and cannot be used as an identifier",
                    formatRange(range),
                    "Choose a different name"));
            return;
        }
        if (!SYMBOL_ID_PATTERN.matcher(value).matches()) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.ILIMAP_INVALID_SYMBOL_ID,
                    Severity.ERROR,
                    "'" + value + "' is not a valid symbol ID (must match [A-Za-z][A-Za-z0-9_-]*)",
                    formatRange(range),
                    null));
        }
    }

    public static void requireAliasId(String value, DiagnosticCollector diagnostics, IlimapSourceRange range) {
        if (value == null || value.isEmpty()) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.ILIMAP_INVALID_ALIAS_ID,
                    Severity.ERROR,
                    "alias ID must not be empty",
                    formatRange(range),
                    null));
            return;
        }
        if (IlimapReservedWords.isReserved(value)) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.ILIMAP_RESERVED_WORD,
                    Severity.ERROR,
                    "'" + value + "' is a reserved word and cannot be used as an alias",
                    formatRange(range),
                    "Choose a different name"));
            return;
        }
        if (!ALIAS_ID_PATTERN.matcher(value).matches()) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.ILIMAP_INVALID_ALIAS_ID,
                    Severity.ERROR,
                    "'" + value + "' is not a valid alias ID (must match [A-Za-z][A-Za-z0-9_]*, no hyphens)",
                    formatRange(range),
                    "Remove hyphens from the alias name"));
        }
    }

    private static String formatRange(IlimapSourceRange range) {
        if (range == null) {
            return null;
        }
        return "line " + range.start().line() + ", column " + range.start().column();
    }
}
