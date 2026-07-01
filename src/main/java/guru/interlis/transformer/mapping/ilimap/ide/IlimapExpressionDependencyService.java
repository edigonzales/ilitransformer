package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapExpressionText;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapSourceStmt;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IlimapExpressionDependencyService {

    private static final Pattern ENUM_MAP_PATTERN =
            Pattern.compile("enumMap\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_-]*)\\s*\\)");
    private static final Pattern SOURCE_MEMBER_PATTERN =
            Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_-]*)\\.([A-Za-z_][A-Za-z0-9_-]*)\\b");

    public List<IlimapExpressionDependencySummary> dependencies(
            IlimapAnalysis analysis, IlimapExpressionText expression, IlimapRuleBlock rule) {
        return IlimapRuleDetailService.extractDependencies(expression.text());
    }

    public List<IlimapExpressionDependencySummary> dependencies(String expressionText) {
        return IlimapRuleDetailService.extractDependencies(expressionText);
    }

    public List<IlimapTraceDependency> dependenciesWithLocations(
            IlimapAnalysis analysis, IlimapExpressionText expression, IlimapRuleBlock rule) {
        if (expression == null || expression.text() == null || expression.text().isBlank()) {
            return List.of();
        }
        List<IlimapTraceDependency> result = new ArrayList<>();
        String text = expression.text();
        IlimapSourceRange expressionRange = expression.range();
        int baseOffset = expressionRange != null ? expressionRange.start().offset() : 0;

        Matcher enumMapMatcher = ENUM_MAP_PATTERN.matcher(text);
        while (enumMapMatcher.find()) {
            String enumMapId = enumMapMatcher.group(1);
            int matchStart = enumMapMatcher.start(1);
            int matchEnd = enumMapMatcher.end(1);
            result.add(new IlimapTraceDependency(
                    "enumMap",
                    null,
                    null,
                    null,
                    enumMapId,
                    null,
                    null,
                    rangeFromOffsets(analysis, baseOffset + matchStart, baseOffset + matchEnd),
                    null));
        }

        Matcher sourceMatcher = SOURCE_MEMBER_PATTERN.matcher(text);
        while (sourceMatcher.find()) {
            String alias = sourceMatcher.group(1);
            String member = sourceMatcher.group(2);
            if ("enumMap".equals(alias)) {
                continue;
            }
            int memberStart = sourceMatcher.start(2);
            int memberEnd = sourceMatcher.end(2);
            String sourceClass = sourceForAlias(rule, alias)
                    .map(IlimapSourceStmt::sourceClass)
                    .orElse(null);
            IlimapOverviewLocation memberLocation =
                    rangeFromOffsets(analysis, baseOffset + memberStart, baseOffset + memberEnd);
            IlimapOverviewLocation definitionLocation = sourceForAlias(rule, alias)
                    .map(source -> toLocation(analysis, source.range()))
                    .orElse(null);
            result.add(new IlimapTraceDependency(
                    "sourceAttribute",
                    alias,
                    member,
                    sourceClass,
                    null,
                    null,
                    null,
                    memberLocation,
                    definitionLocation));
        }
        return result;
    }

    public Optional<IlimapSourceStmt> sourceForAlias(IlimapRuleBlock rule, String alias) {
        if (rule == null || alias == null) {
            return Optional.empty();
        }
        return rule.elements().stream()
                .filter(IlimapSourceStmt.class::isInstance)
                .map(IlimapSourceStmt.class::cast)
                .filter(source -> alias.equals(source.alias()))
                .findFirst();
    }

    public Optional<IlimapClassInfo> sourceClass(IlimapAnalysis analysis, IlimapSourceStmt source) {
        if (source == null || analysis == null) {
            return Optional.empty();
        }
        for (String inputId : source.inputIds()) {
            Optional<IlimapClassInfo> classInfo = analysis.modelIndex()
                    .modelNameForInput(inputId)
                    .flatMap(modelName -> analysis.modelIndex().classesForModel(modelName).stream()
                            .filter(candidate -> candidate.qualifiedName().equals(source.sourceClass()))
                            .findFirst());
            if (classInfo.isPresent()) {
                return classInfo;
            }
        }
        return Optional.empty();
    }

    private static IlimapOverviewLocation rangeFromOffsets(IlimapAnalysis analysis, int startOffset, int endOffset) {
        if (analysis == null || startOffset < 0 || endOffset < startOffset) {
            return null;
        }
        IlimapIdePosition start = analysis.lineMap().toIdePosition(startOffset);
        IlimapIdePosition end = analysis.lineMap().toIdePosition(endOffset);
        return new IlimapOverviewLocation(start.line(), start.character(), end.line(), end.character());
    }

    private static IlimapOverviewLocation toLocation(IlimapAnalysis analysis, IlimapSourceRange range) {
        if (range == null) {
            return null;
        }
        IlimapIdeRange ideRange = analysis.lineMap().toIdeRange(range);
        return new IlimapOverviewLocation(
                ideRange.start().line(),
                ideRange.start().character(),
                ideRange.end().line(),
                ideRange.end().character());
    }
}
