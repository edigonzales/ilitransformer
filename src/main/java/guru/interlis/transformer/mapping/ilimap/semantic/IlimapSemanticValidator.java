package guru.interlis.transformer.mapping.ilimap.semantic;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.ilimap.ast.*;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IlimapSemanticValidator {

    private static final Set<String> VALID_FAIL_POLICIES =
            Set.of("strict", "lenient", "reportOnly", "report_only");
    private static final Set<String> VALID_COMPILE_MODES =
            Set.of("strict", "compatible", "report");
    private static final Set<String> VALID_OID_STRATEGIES =
            Set.of("preserve", "integer", "uuid", "deterministicUuid");
    private static final Set<String> RESERVED_OID_STRATEGIES =
            Set.of("external");
    private static final Set<String> VALID_BASKET_STRATEGIES =
            Set.of("preserve", "generateUuid", "preserveOrGenerateUuid", "byTopic");
    private static final Set<String> RESERVED_BASKET_STRATEGIES =
            Set.of("expression");

    private static final Pattern ENUM_MAP_PATTERN =
            Pattern.compile("enumMap\\s*\\(");

    public IlimapSemanticResult validate(IlimapDocument document) {
        var diagnostics = new DiagnosticCollector();
        var symbols = new IlimapSymbolTable();

        validateTopLevelStructure(document, diagnostics);
        registerTopLevelSymbols(document, symbols, diagnostics);
        validateStrategies(document, diagnostics);
        validateRules(document, symbols, diagnostics);
        validateEnumMapReferences(document, symbols, diagnostics);

        return new IlimapSemanticResult(document, symbols, diagnostics.all());
    }

    private void validateTopLevelStructure(IlimapDocument document, DiagnosticCollector diagnostics) {
        if (document.inputs().isEmpty()) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.ILIMAP_MISSING_INPUT,
                    Severity.ERROR,
                    "mapping must declare at least one input",
                    formatRange(document.range()),
                    null));
        }
        if (document.outputs().isEmpty()) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.ILIMAP_MISSING_OUTPUT,
                    Severity.ERROR,
                    "mapping must declare at least one output",
                    formatRange(document.range()),
                    null));
        }
        if (document.rules().isEmpty()) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.ILIMAP_MISSING_RULE,
                    Severity.ERROR,
                    "mapping must declare at least one rule",
                    formatRange(document.range()),
                    null));
        }
    }

    private void registerTopLevelSymbols(
            IlimapDocument document, IlimapSymbolTable symbols, DiagnosticCollector diagnostics) {
        IlimapScope scope = symbols.topLevelScope();
        for (var input : document.inputs()) {
            IlimapIdentifierRules.requireSymbolId(input.id(), diagnostics, input.range());
            scope.define(new IlimapSymbol(IlimapSymbolKind.INPUT, input.id(), input), diagnostics);
        }
        for (var output : document.outputs()) {
            IlimapIdentifierRules.requireSymbolId(output.id(), diagnostics, output.range());
            scope.define(new IlimapSymbol(IlimapSymbolKind.OUTPUT, output.id(), output), diagnostics);
        }
        for (var rule : document.rules()) {
            IlimapIdentifierRules.requireSymbolId(rule.id(), diagnostics, rule.range());
            scope.define(new IlimapSymbol(IlimapSymbolKind.RULE, rule.id(), rule), diagnostics);
        }
        for (var enumBlock : document.enums()) {
            IlimapIdentifierRules.requireSymbolId(enumBlock.id(), diagnostics, enumBlock.range());
            scope.define(
                    new IlimapSymbol(IlimapSymbolKind.ENUM_MAP, enumBlock.id(), enumBlock),
                    diagnostics);
        }
    }

    private void validateStrategies(IlimapDocument document, DiagnosticCollector diagnostics) {
        if (document.job() != null) {
            validateOptionalStrategy(document.job().failPolicy(), VALID_FAIL_POLICIES,
                    Collections.emptySet(), "failPolicy", document.job().range(), diagnostics);
            validateOptionalStrategy(document.job().compileMode(), VALID_COMPILE_MODES,
                    Collections.emptySet(), "compileMode", document.job().range(), diagnostics);
        }
        if (document.oid() != null && document.oid().strategy() != null) {
            validateOptionalStrategy(document.oid().strategy(), VALID_OID_STRATEGIES,
                    RESERVED_OID_STRATEGIES, "oid strategy", document.oid().range(), diagnostics);
        }
        if (document.basket() != null && document.basket().strategy() != null) {
            validateOptionalStrategy(document.basket().strategy(), VALID_BASKET_STRATEGIES,
                    RESERVED_BASKET_STRATEGIES, "basket strategy",
                    document.basket().range(), diagnostics);
        }
    }

    private void validateOptionalStrategy(
            String value, Set<String> valid, Set<String> reserved,
            String label, IlimapSourceRange range, DiagnosticCollector diagnostics) {
        if (value == null) {
            return;
        }
        if (reserved.contains(value)) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.ILIMAP_RESERVED_STRATEGY,
                    Severity.ERROR,
                    label + " '" + value + "' is reserved but not implemented",
                    formatRange(range),
                    "Use one of: " + String.join(", ", valid)));
            return;
        }
        if (!valid.contains(value)) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.ILIMAP_INVALID_STRATEGY,
                    Severity.ERROR,
                    "unknown " + label + " '" + value + "'",
                    formatRange(range),
                    "Use one of: " + String.join(", ", valid)));
        }
    }

    private void validateRules(
            IlimapDocument document, IlimapSymbolTable symbols, DiagnosticCollector diagnostics) {
        for (var rule : document.rules()) {
            validateRule(rule, symbols, diagnostics);
        }
    }

    private void validateRule(
            IlimapRuleBlock rule, IlimapSymbolTable symbols, DiagnosticCollector diagnostics) {
        IlimapScope ruleScope = symbols.scopeFor(rule);

        List<IlimapTargetStmt> targets = new ArrayList<>();
        List<IlimapSourceStmt> sources = new ArrayList<>();
        int identityCount = 0;
        int assignCount = 0;
        int defaultsCount = 0;

        for (var element : rule.elements()) {
            switch (element) {
                case IlimapTargetStmt t -> targets.add(t);
                case IlimapSourceStmt s -> sources.add(s);
                case IlimapIdentityStmt ignored -> identityCount++;
                case IlimapAssignmentBlock ignored -> assignCount++;
                case IlimapDefaultsBlock ignored -> defaultsCount++;
                case IlimapWhereStmt ignored -> {}
            }
        }

        if (targets.isEmpty()) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.ILIMAP_MISSING_TARGET,
                    Severity.ERROR,
                    "rule '" + rule.id() + "' must have exactly one target",
                    formatRange(rule.range()),
                    null));
        } else if (targets.size() > 1) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.ILIMAP_DUPLICATE_ELEMENT,
                    Severity.ERROR,
                    "rule '" + rule.id() + "' has " + targets.size() + " targets, expected exactly one",
                    formatRange(targets.get(1).range()),
                    null));
        }

        if (sources.isEmpty()) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.ILIMAP_MISSING_SOURCE,
                    Severity.ERROR,
                    "rule '" + rule.id() + "' must have at least one source",
                    formatRange(rule.range()),
                    null));
        }

        if (identityCount > 1) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.ILIMAP_DUPLICATE_ELEMENT,
                    Severity.ERROR,
                    "rule '" + rule.id() + "' has multiple identity statements, at most one is allowed",
                    formatRange(rule.range()),
                    null));
        }
        if (assignCount > 1) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.ILIMAP_DUPLICATE_ELEMENT,
                    Severity.ERROR,
                    "rule '" + rule.id() + "' has multiple assign blocks, at most one is allowed",
                    formatRange(rule.range()),
                    null));
        }
        if (defaultsCount > 1) {
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.ILIMAP_DUPLICATE_ELEMENT,
                    Severity.ERROR,
                    "rule '" + rule.id() + "' has multiple defaults blocks, at most one is allowed",
                    formatRange(rule.range()),
                    null));
        }

        if (!targets.isEmpty()) {
            IlimapTargetStmt target = targets.get(0);
            if (symbols.resolveOutput(target.outputId()).isEmpty()) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.ILIMAP_UNKNOWN_OUTPUT,
                        Severity.ERROR,
                        "target references unknown output '" + target.outputId() + "'",
                        formatRange(target.range()),
                        "Declare an output with this ID"));
            }
        }

        Set<String> seenAliases = new HashSet<>();
        for (var source : sources) {
            String alias = source.alias();
            IlimapIdentifierRules.requireAliasId(alias, diagnostics, source.range());

            if (!seenAliases.add(alias)) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.ILIMAP_DUPLICATE_ALIAS,
                        Severity.ERROR,
                        "duplicate source alias '" + alias + "' in rule '" + rule.id() + "'",
                        formatRange(source.range()),
                        "Use a unique alias"));
            } else {
                ruleScope.define(
                        new IlimapSymbol(IlimapSymbolKind.SOURCE_ALIAS, alias, source),
                        diagnostics);
            }

            for (String inputId : source.inputIds()) {
                if (symbols.resolveInput(inputId).isEmpty()) {
                    diagnostics.add(new Diagnostic(
                            DiagnosticCode.ILIMAP_UNKNOWN_INPUT,
                            Severity.ERROR,
                            "source references unknown input '" + inputId + "'",
                            formatRange(source.range()),
                            "Declare an input with this ID"));
                }
            }
        }

        for (var element : rule.elements()) {
            if (element instanceof IlimapAssignmentBlock assignBlock) {
                validateDuplicateAssignments(assignBlock.assignments(), "assign", rule.id(), diagnostics);
            }
            if (element instanceof IlimapDefaultsBlock defaultsBlock) {
                validateDuplicateAssignments(defaultsBlock.assignments(), "defaults", rule.id(), diagnostics);
            }
        }
    }

    private void validateDuplicateAssignments(
            List<IlimapAssignment> assignments, String blockType,
            String ruleId, DiagnosticCollector diagnostics) {
        Set<String> seen = new HashSet<>();
        for (var assignment : assignments) {
            if (!seen.add(assignment.targetAttribute())) {
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.ILIMAP_DUPLICATE_ASSIGNMENT,
                        Severity.ERROR,
                        "duplicate assignment to '" + assignment.targetAttribute()
                                + "' in " + blockType + " block of rule '" + ruleId + "'",
                        formatRange(assignment.range()),
                        "Remove the duplicate assignment"));
            }
        }
    }

    void validateEnumMapReferences(
            IlimapDocument document, IlimapSymbolTable symbols, DiagnosticCollector diagnostics) {
        for (var rule : document.rules()) {
            for (var element : rule.elements()) {
                collectExpressions(element).forEach(expr ->
                        checkEnumMapRefsInExpression(expr, symbols, diagnostics));
            }
        }
    }

    private List<IlimapExpressionText> collectExpressions(IlimapRuleElement element) {
        List<IlimapExpressionText> result = new ArrayList<>();
        switch (element) {
            case IlimapAssignmentBlock block ->
                    block.assignments().forEach(a -> result.add(a.expression()));
            case IlimapDefaultsBlock block ->
                    block.assignments().forEach(a -> result.add(a.expression()));
            case IlimapWhereStmt where -> result.add(where.expression());
            case IlimapIdentityStmt identity -> result.addAll(identity.expressions());
            case IlimapSourceStmt source -> {
                if (source.where() != null) {
                    result.add(source.where());
                }
            }
            case IlimapTargetStmt ignored -> {}
        }
        return result;
    }

    private void checkEnumMapRefsInExpression(
            IlimapExpressionText expr, IlimapSymbolTable symbols, DiagnosticCollector diagnostics) {
        String text = expr.text();
        Matcher matcher = ENUM_MAP_PATTERN.matcher(text);
        while (matcher.find()) {
            int parenStart = matcher.end() - 1;
            String secondArg = extractSecondArgument(text, parenStart);
            if (secondArg == null) {
                continue;
            }
            secondArg = secondArg.strip();
            if (secondArg.isEmpty()) {
                continue;
            }

            if (secondArg.startsWith("\"") && secondArg.endsWith("\"")) {
                String unquoted = secondArg.substring(1, secondArg.length() - 1);
                diagnostics.add(new Diagnostic(
                        DiagnosticCode.ILIMAP_ENUM_MAP_STRING_REF,
                        Severity.WARNING,
                        "enumMap uses string literal \"" + unquoted
                                + "\"; prefer symbolic enum map reference",
                        formatRange(expr.range()),
                        "Use " + unquoted + " instead of \"" + unquoted + "\""));
                if (symbols.resolveEnumMap(unquoted).isEmpty()) {
                    diagnostics.add(new Diagnostic(
                            DiagnosticCode.ILIMAP_UNKNOWN_ENUM_MAP,
                            Severity.ERROR,
                            "enumMap references unknown enum map '" + unquoted + "'",
                            formatRange(expr.range()),
                            "Declare an enum block with this ID"));
                }
            } else {
                if (symbols.resolveEnumMap(secondArg).isEmpty()) {
                    diagnostics.add(new Diagnostic(
                            DiagnosticCode.ILIMAP_UNKNOWN_ENUM_MAP,
                            Severity.ERROR,
                            "enumMap references unknown enum map '" + secondArg + "'",
                            formatRange(expr.range()),
                            "Declare an enum block with this ID"));
                }
            }
        }
    }

    static String extractSecondArgument(String text, int openParenIndex) {
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
                        return text.substring(commaIndex + 1, i).strip();
                    }
                    return null;
                }
            } else if (c == ',' && depth == 1) {
                if (commaIndex < 0) {
                    commaIndex = i;
                }
            } else if (c == '"') {
                i = skipString(text, i);
            }
        }
        return null;
    }

    private static int skipString(String text, int quoteIndex) {
        for (int i = quoteIndex + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '"') {
                return i;
            }
        }
        return text.length() - 1;
    }

    private static String formatRange(IlimapSourceRange range) {
        if (range == null) {
            return null;
        }
        return "line " + range.start().line() + ", column " + range.start().column();
    }
}
