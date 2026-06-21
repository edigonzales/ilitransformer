package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapAssignment;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapAssignmentBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapBagBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapCreateBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapDefaultsBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapExpressionText;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapIdentityStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapJoinStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapLossBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRefBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleElement;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapSourceStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapTargetStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapWhereStmt;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class IlimapModelDiagnosticService {

    public List<IlimapIdeDiagnostic> diagnostics(IlimapAnalysis analysis) {
        Objects.requireNonNull(analysis, "analysis");
        if (!analysis.hasDocument()) {
            return List.of();
        }

        List<IlimapIdeDiagnostic> diagnostics = new ArrayList<>();
        for (IlimapRuleBlock rule : analysis.document().rules()) {
            Optional<IlimapTargetStmt> target = target(rule);
            Optional<IlimapClassInfo> targetClass =
                    target.flatMap(targetStmt -> checkTargetClass(analysis, targetStmt, diagnostics));
            checkSourceClassesAndAttributes(analysis, rule, diagnostics);
            targetClass.ifPresent(classInfo -> checkTargetAttributes(analysis, rule, classInfo, diagnostics));
        }
        return diagnostics;
    }

    private Optional<IlimapClassInfo> checkTargetClass(
            IlimapAnalysis analysis, IlimapTargetStmt target, List<IlimapIdeDiagnostic> diagnostics) {
        Optional<String> modelName = analysis.modelIndex().modelNameForOutput(target.outputId());
        if (modelName.isEmpty() || !analysis.modelIndex().isModelLoaded(modelName.get())) {
            return Optional.empty();
        }

        Optional<IlimapClassInfo> classInfo =
                findClassInModel(analysis.modelIndex(), modelName.get(), target.targetClass());
        if (classInfo.isEmpty()) {
            diagnostics.add(new IlimapIdeDiagnostic(
                    DiagnosticCode.MAP_UNKNOWN_TARGET_CLASS,
                    IlimapIdeSeverity.ERROR,
                    "Target class not found in output model '" + modelName.get() + "': " + target.targetClass(),
                    rangeOfValue(analysis, target.range(), target.targetClass()),
                    "Check the target class name"));
        }
        return classInfo;
    }

    private void checkSourceClassesAndAttributes(
            IlimapAnalysis analysis, IlimapRuleBlock rule, List<IlimapIdeDiagnostic> diagnostics) {
        Map<String, IlimapClassInfo> sourceClassesByAlias = new LinkedHashMap<>();
        for (IlimapSourceStmt source : sources(rule)) {
            Optional<IlimapClassInfo> sourceClass = resolveSourceClass(analysis, source);
            if (sourceClass.isEmpty()) {
                boolean hasLoadedInputModel = source.inputIds().stream()
                        .map(inputId -> analysis.modelIndex().modelNameForInput(inputId))
                        .flatMap(Optional::stream)
                        .anyMatch(modelName -> analysis.modelIndex().isModelLoaded(modelName));
                if (hasLoadedInputModel) {
                    diagnostics.add(new IlimapIdeDiagnostic(
                            DiagnosticCode.MAP_UNKNOWN_SOURCE_CLASS,
                            IlimapIdeSeverity.ERROR,
                            "Source class not found in input model: " + source.sourceClass(),
                            rangeOfValue(analysis, source.range(), source.sourceClass()),
                            "Check the source class name"));
                }
                continue;
            }
            sourceClassesByAlias.put(source.alias(), sourceClass.get());
        }

        checkSourceAttributeOccurrences(analysis, rule, sourceClassesByAlias, diagnostics);
    }

    private Optional<IlimapClassInfo> resolveSourceClass(IlimapAnalysis analysis, IlimapSourceStmt source) {
        for (String inputId : source.inputIds()) {
            Optional<String> modelName = analysis.modelIndex().modelNameForInput(inputId);
            if (modelName.isEmpty() || !analysis.modelIndex().isModelLoaded(modelName.get())) {
                continue;
            }
            Optional<IlimapClassInfo> classInfo =
                    findClassInModel(analysis.modelIndex(), modelName.get(), source.sourceClass());
            if (classInfo.isPresent()) {
                return classInfo;
            }
        }
        return Optional.empty();
    }

    private void checkTargetAttributes(
            IlimapAnalysis analysis,
            IlimapRuleBlock rule,
            IlimapClassInfo targetClass,
            List<IlimapIdeDiagnostic> diagnostics) {
        for (IlimapRuleElement element : rule.elements()) {
            if (element instanceof IlimapAssignmentBlock assignments) {
                for (IlimapAssignment assignment : assignments.assignments()) {
                    if (targetClass.findAttribute(assignment.targetAttribute()).isEmpty()) {
                        diagnostics.add(new IlimapIdeDiagnostic(
                                DiagnosticCode.MAP_UNKNOWN_TARGET_ATTRIBUTE,
                                IlimapIdeSeverity.ERROR,
                                "Target attribute not found: " + assignment.targetAttribute() + " in class "
                                        + targetClass.qualifiedName(),
                                rangeOfValue(analysis, assignment.range(), assignment.targetAttribute()),
                                "Check the target attribute name"));
                    }
                }
            }
        }
    }

    private void checkSourceAttributeOccurrences(
            IlimapAnalysis analysis,
            IlimapRuleBlock rule,
            Map<String, IlimapClassInfo> sourceClassesByAlias,
            List<IlimapIdeDiagnostic> diagnostics) {
        if (sourceClassesByAlias.isEmpty()) {
            return;
        }

        for (IlimapExpressionText expression : expressions(rule)) {
            for (PathOccurrence occurrence : simpleAliasAttributeOccurrences(expression)) {
                IlimapClassInfo sourceClass = sourceClassesByAlias.get(occurrence.alias());
                if (sourceClass == null) {
                    continue;
                }
                if (sourceClass.findAttribute(occurrence.attribute()).isEmpty()) {
                    diagnostics.add(new IlimapIdeDiagnostic(
                            DiagnosticCode.MAP_UNKNOWN_SOURCE_ATTRIBUTE,
                            IlimapIdeSeverity.ERROR,
                            "Source attribute not found: " + occurrence.alias() + "." + occurrence.attribute(),
                            new IlimapIdeRange(
                                    analysis.lineMap().toIdePosition(occurrence.startOffset()),
                                    analysis.lineMap().toIdePosition(occurrence.endOffset())),
                            "Check the attribute name in source class"));
                }
            }
        }
    }

    private static Optional<IlimapClassInfo> findClassInModel(
            IlimapModelIndex index, String modelName, String qualifiedName) {
        return index.classesForModel(modelName).stream()
                .filter(classInfo -> classInfo.qualifiedName().equals(qualifiedName))
                .findFirst();
    }

    private static Optional<IlimapTargetStmt> target(IlimapRuleBlock rule) {
        return rule.elements().stream()
                .filter(IlimapTargetStmt.class::isInstance)
                .map(IlimapTargetStmt.class::cast)
                .findFirst();
    }

    private static List<IlimapSourceStmt> sources(IlimapRuleBlock rule) {
        return rule.elements().stream()
                .filter(IlimapSourceStmt.class::isInstance)
                .map(IlimapSourceStmt.class::cast)
                .toList();
    }

    private static List<IlimapExpressionText> expressions(IlimapRuleBlock rule) {
        List<IlimapExpressionText> result = new ArrayList<>();
        for (IlimapRuleElement element : rule.elements()) {
            collectExpressions(element, result);
        }
        return result;
    }

    private static void collectExpressions(IlimapRuleElement element, List<IlimapExpressionText> result) {
        switch (element) {
            case IlimapAssignmentBlock block ->
                block.assignments().forEach(assignment -> result.add(assignment.expression()));
            case IlimapDefaultsBlock block ->
                block.assignments().forEach(assignment -> result.add(assignment.expression()));
            case IlimapWhereStmt where -> result.add(where.expression());
            case IlimapIdentityStmt identity -> result.addAll(identity.expressions());
            case IlimapSourceStmt source -> {
                if (source.where() != null) {
                    result.add(source.where());
                }
            }
            case IlimapJoinStmt join -> result.add(join.on());
            case IlimapBagBlock bag -> collectBagExpressions(bag, result);
            case IlimapRefBlock ref -> {
                if (ref.sourceRef() != null) {
                    result.add(ref.sourceRef());
                }
            }
            case IlimapCreateBlock create -> {
                if (create.assign() != null) {
                    create.assign().assignments().forEach(assignment -> result.add(assignment.expression()));
                }
            }
            case IlimapLossBlock loss -> {
                if (loss.sourcePath() != null) {
                    result.add(loss.sourcePath());
                }
                if (loss.when() != null) {
                    result.add(loss.when());
                }
            }
            default -> {}
        }
    }

    private static void collectBagExpressions(IlimapBagBlock bag, List<IlimapExpressionText> result) {
        if (bag.from() != null && bag.from().where() != null) {
            result.add(bag.from().where());
        }
        if (bag.assign() != null) {
            bag.assign().assignments().forEach(assignment -> result.add(assignment.expression()));
        }
        for (IlimapBagBlock nested : bag.nestedBags()) {
            collectBagExpressions(nested, result);
        }
    }

    private static List<PathOccurrence> simpleAliasAttributeOccurrences(IlimapExpressionText expression) {
        List<PathOccurrence> result = new ArrayList<>();
        String text = expression.text();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipQuoted(text, i) + 1;
                continue;
            }
            if (!isIdentifierStart(c)) {
                i++;
                continue;
            }

            int aliasStart = i;
            int aliasEnd = readIdentifier(text, aliasStart);
            if (aliasStart > 0 && text.charAt(aliasStart - 1) == '.') {
                i = aliasEnd;
                continue;
            }
            if (aliasEnd >= text.length() || text.charAt(aliasEnd) != '.') {
                i = aliasEnd;
                continue;
            }
            int attrStart = aliasEnd + 1;
            if (attrStart >= text.length() || !isIdentifierStart(text.charAt(attrStart))) {
                i = attrStart;
                continue;
            }
            int attrEnd = readIdentifier(text, attrStart);
            if (attrEnd < text.length() && text.charAt(attrEnd) == '.') {
                i = attrEnd;
                continue;
            }

            result.add(new PathOccurrence(
                    text.substring(aliasStart, aliasEnd),
                    text.substring(attrStart, attrEnd),
                    expression.range().start().offset() + aliasStart,
                    expression.range().start().offset() + attrEnd));
            i = attrEnd;
        }
        return result;
    }

    private static IlimapIdeRange rangeOfValue(IlimapAnalysis analysis, IlimapSourceRange sourceRange, String value) {
        int start = sourceRange.start().offset();
        int end = sourceRange.end().offset();
        String segment =
                analysis.text().substring(start, Math.min(end, analysis.text().length()));
        int relative = value != null ? segment.indexOf(value) : -1;
        if (relative < 0) {
            return analysis.lineMap().toIdeRange(sourceRange);
        }
        int valueStart = start + relative;
        return new IlimapIdeRange(
                analysis.lineMap().toIdePosition(valueStart),
                analysis.lineMap().toIdePosition(valueStart + value.length()));
    }

    private static int skipQuoted(String text, int quoteOffset) {
        char quote = text.charAt(quoteOffset);
        for (int i = quoteOffset + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quote) {
                return i;
            }
        }
        return text.length() - 1;
    }

    private static int readIdentifier(String text, int offset) {
        int current = offset;
        while (current < text.length() && isIdentifierPart(text.charAt(current))) {
            current++;
        }
        return current;
    }

    private static boolean isIdentifierStart(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_';
    }

    private static boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || (c >= '0' && c <= '9') || c == '-';
    }

    private record PathOccurrence(String alias, String attribute, int startOffset, int endOffset) {}
}
