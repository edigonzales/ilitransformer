package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapAssignmentBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapBagBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapCreateBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapDefaultsBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapEnumBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapExpressionText;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapInputBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapLiteral;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapOutputBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRefBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleElement;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapSourceStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapTargetStmt;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSymbolKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class IlimapHoverService {

    private final IlimapSymbolReferenceResolver symbolResolver;
    private final IlimapPositionResolver positionResolver = new IlimapPositionResolver();

    public IlimapHoverService() {
        this(new IlimapSymbolReferenceResolver());
    }

    IlimapHoverService(IlimapSymbolReferenceResolver symbolResolver) {
        this.symbolResolver = Objects.requireNonNull(symbolResolver, "symbolResolver");
    }

    public Optional<IlimapHover> hoverAt(IlimapAnalysis analysis, IlimapIdePosition position) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(position, "position");

        Optional<IlimapHover> symbolHover = symbolResolver
                .resolve(analysis, position)
                .flatMap(resolved ->
                        markdown(analysis, resolved).map(markdown -> new IlimapHover(resolved.range(), markdown)));
        if (symbolHover.isPresent()) {
            return symbolHover;
        }
        return sourceMemberHover(analysis, position);
    }

    private Optional<String> markdown(IlimapAnalysis analysis, IlimapResolvedSymbol resolved) {
        if (resolved.symbol().kind() == IlimapSymbolKind.INPUT
                && resolved.symbol().node() instanceof IlimapInputBlock input) {
            return Optional.of(inputMarkdown(input));
        }
        if (resolved.symbol().kind() == IlimapSymbolKind.OUTPUT
                && resolved.symbol().node() instanceof IlimapOutputBlock output) {
            return Optional.of(outputMarkdown(output));
        }
        if (resolved.symbol().kind() == IlimapSymbolKind.RULE
                && resolved.symbol().node() instanceof IlimapRuleBlock rule) {
            return Optional.of(ruleMarkdown(rule));
        }
        if (resolved.symbol().kind() == IlimapSymbolKind.ENUM_MAP
                && resolved.symbol().node() instanceof IlimapEnumBlock enumBlock) {
            return Optional.of(enumMarkdown(enumBlock));
        }
        if (resolved.symbol().kind() == IlimapSymbolKind.SOURCE_ALIAS
                && resolved.symbol().node() instanceof IlimapSourceStmt source) {
            return Optional.of(sourceAliasMarkdown(
                    analysis, new SourceBinding(source.alias(), source.inputIds(), source.sourceClass())));
        }
        if (resolved.symbol().kind() == IlimapSymbolKind.SOURCE_ALIAS
                && resolved.symbol().node() instanceof IlimapBagBlock bag
                && bag.from() != null) {
            return Optional.of(sourceAliasMarkdown(
                    analysis,
                    new SourceBinding(
                            bag.from().alias(),
                            List.of(bag.from().inputId()),
                            bag.from().sourceClass())));
        }
        return Optional.empty();
    }

    private String inputMarkdown(IlimapInputBlock input) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("**input `").append(input.id()).append("`**\n");
        appendOptional(markdown, "Path", input.path());
        appendOptional(markdown, "Model", input.model());
        appendOptional(markdown, "Format", input.format());
        return markdown.toString().stripTrailing();
    }

    private String outputMarkdown(IlimapOutputBlock output) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("**output `").append(output.id()).append("`**\n");
        appendOptional(markdown, "Path", output.path());
        appendOptional(markdown, "Model", output.model());
        appendOptional(markdown, "Format", output.format());
        return markdown.toString().stripTrailing();
    }

    private String ruleMarkdown(IlimapRuleBlock rule) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("**rule `").append(rule.id()).append("`**\n");

        rule.elements().stream()
                .filter(IlimapTargetStmt.class::isInstance)
                .map(IlimapTargetStmt.class::cast)
                .findFirst()
                .ifPresent(target -> {
                    appendOptional(markdown, "Target", target.outputId());
                    appendOptional(markdown, "Class", target.targetClass());
                });

        String sources = rule.elements().stream()
                .filter(IlimapSourceStmt.class::isInstance)
                .map(IlimapSourceStmt.class::cast)
                .map(IlimapSourceStmt::alias)
                .collect(Collectors.joining("`, `", "`", "`"));
        if (!sources.equals("``")) {
            markdown.append("Sources: ").append(sources).append("\n");
        }

        markdown.append("Assignments: ").append(assignmentCount(rule)).append("\n");
        markdown.append("Bags: ").append(bagCount(rule)).append("\n");
        markdown.append("Refs: ").append(refCount(rule)).append("\n");
        return markdown.toString().stripTrailing();
    }

    private String enumMarkdown(IlimapEnumBlock enumBlock) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("**enum `").append(enumBlock.id()).append("`**\n\n");
        markdown.append("Entries: ").append(enumBlock.entries().size()).append("\n");
        enumBlock.entries().stream().limit(5).forEach(entry -> markdown.append("\n- `")
                .append(literal(entry.source()))
                .append("` => `")
                .append(literal(entry.target()))
                .append("`"));
        if (enumBlock.entries().size() > 5) {
            markdown.append("\n- ...");
        }
        return markdown.toString().stripTrailing();
    }

    private String sourceAliasMarkdown(IlimapAnalysis analysis, SourceBinding source) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("**source `").append(source.alias()).append("`**\n");
        appendOptional(markdown, "Input", String.join(", ", source.inputIds()));
        appendOptional(markdown, "Class", source.sourceClass());
        sourceClass(analysis, source).ifPresent(classInfo -> appendMemberPreview(markdown, classInfo));
        return markdown.toString().stripTrailing();
    }

    private Optional<IlimapHover> sourceMemberHover(IlimapAnalysis analysis, IlimapIdePosition position) {
        Optional<IlimapTokenAtPosition> token = positionResolver.tokenAt(analysis, position);
        if (token.isEmpty()) {
            return Optional.empty();
        }
        Optional<IlimapExpressionText> expression =
                positionResolver.expressionAt(analysis, token.get().range());
        if (expression.isEmpty()) {
            return Optional.empty();
        }

        int expressionStart = expression.get().range().start().offset();
        int tokenStart = positionResolver.rangeStartOffset(analysis, token.get().range()) - expressionStart;
        String expressionText = expression.get().text();
        if (tokenStart < 2 || expressionText.charAt(tokenStart - 1) != '.') {
            return Optional.empty();
        }
        int aliasEnd = tokenStart - 1;
        int aliasStart = aliasEnd;
        while (aliasStart > 0 && isIdentifierPart(expressionText.charAt(aliasStart - 1))) {
            aliasStart--;
        }
        if (aliasStart == aliasEnd) {
            return Optional.empty();
        }
        String alias = expressionText.substring(aliasStart, aliasEnd);
        String memberName = token.get().text();
        return currentRuleAt(analysis, token.get())
                .flatMap(rule -> sourceForAlias(rule, alias))
                .flatMap(source -> sourceClass(analysis, source))
                .flatMap(classInfo -> memberMarkdown(alias, memberName, classInfo))
                .map(markdown -> new IlimapHover(token.get().range(), markdown));
    }

    private Optional<String> memberMarkdown(String alias, String memberName, IlimapClassInfo classInfo) {
        Optional<IlimapAttributeInfo> attribute = classInfo.findAttribute(memberName);
        if (attribute.isPresent()) {
            IlimapAttributeInfo attr = attribute.get();
            return Optional.of("**attribute `" + alias + "." + attr.name() + "`**\n"
                    + "Type: `" + attr.type() + "`\n"
                    + "Cardinality: `" + attr.cardinality() + "`");
        }
        Optional<IlimapRoleInfo> role = classInfo.findRole(memberName);
        if (role.isPresent()) {
            IlimapRoleInfo roleInfo = role.get();
            StringBuilder markdown = new StringBuilder();
            markdown.append("**role `")
                    .append(alias)
                    .append(".")
                    .append(roleInfo.name())
                    .append("`**\n");
            appendOptional(markdown, "Association", roleInfo.association());
            appendOptional(markdown, "Target", roleInfo.targetClass());
            appendOptional(markdown, "Cardinality", roleInfo.cardinality());
            return Optional.of(markdown.toString().stripTrailing());
        }
        return Optional.empty();
    }

    private Optional<IlimapRuleBlock> currentRuleAt(IlimapAnalysis analysis, IlimapTokenAtPosition token) {
        int tokenStart = positionResolver.rangeStartOffset(analysis, token.range());
        return analysis.document().rules().stream()
                .filter(rule -> rule.range().start().offset() <= tokenStart
                        && tokenStart <= rule.range().end().offset())
                .findFirst();
    }

    private Optional<SourceBinding> sourceForAlias(IlimapRuleBlock rule, String alias) {
        return sources(rule).stream()
                .filter(source -> source.alias().equals(alias))
                .findFirst();
    }

    private Optional<IlimapClassInfo> sourceClass(IlimapAnalysis analysis, SourceBinding source) {
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

    private void appendMemberPreview(StringBuilder markdown, IlimapClassInfo classInfo) {
        if (!classInfo.attributes().isEmpty()) {
            markdown.append("Attributes: `")
                    .append(classInfo.attributes().stream()
                            .limit(8)
                            .map(IlimapAttributeInfo::name)
                            .collect(Collectors.joining("`, `")))
                    .append("`");
            if (classInfo.attributes().size() > 8) {
                markdown.append(", ...");
            }
            markdown.append("\n");
        }
        if (!classInfo.roles().isEmpty()) {
            markdown.append("Roles: `")
                    .append(classInfo.roles().stream()
                            .limit(8)
                            .map(IlimapRoleInfo::name)
                            .collect(Collectors.joining("`, `")))
                    .append("`");
            if (classInfo.roles().size() > 8) {
                markdown.append(", ...");
            }
            markdown.append("\n");
        }
    }

    private int assignmentCount(IlimapRuleBlock rule) {
        int count = 0;
        for (IlimapRuleElement element : rule.elements()) {
            count += assignmentCount(element);
        }
        return count;
    }

    private int assignmentCount(IlimapRuleElement element) {
        if (element instanceof IlimapAssignmentBlock assign) {
            return assign.assignments().size();
        }
        if (element instanceof IlimapDefaultsBlock defaults) {
            return defaults.assignments().size();
        }
        if (element instanceof IlimapBagBlock bag) {
            int count = bag.assign() == null ? 0 : bag.assign().assignments().size();
            for (IlimapBagBlock nestedBag : bag.nestedBags()) {
                count += assignmentCount(nestedBag);
            }
            return count;
        }
        if (element instanceof IlimapCreateBlock create && create.assign() != null) {
            return create.assign().assignments().size();
        }
        return 0;
    }

    private int assignmentCount(IlimapBagBlock bag) {
        int count = bag.assign() == null ? 0 : bag.assign().assignments().size();
        for (IlimapBagBlock nestedBag : bag.nestedBags()) {
            count += assignmentCount(nestedBag);
        }
        return count;
    }

    private int bagCount(IlimapRuleBlock rule) {
        int count = 0;
        for (IlimapRuleElement element : rule.elements()) {
            if (element instanceof IlimapBagBlock bag) {
                count += 1 + nestedBagCount(bag);
            }
        }
        return count;
    }

    private int nestedBagCount(IlimapBagBlock bag) {
        int count = 0;
        for (IlimapBagBlock nestedBag : bag.nestedBags()) {
            count += 1 + nestedBagCount(nestedBag);
        }
        return count;
    }

    private int refCount(IlimapRuleBlock rule) {
        return (int) rule.elements().stream()
                .filter(IlimapRefBlock.class::isInstance)
                .count();
    }

    private static void appendOptional(StringBuilder markdown, String label, String value) {
        if (value != null && !value.isBlank()) {
            markdown.append(label).append(": `").append(value).append("`\n");
        }
    }

    private static List<SourceBinding> sources(IlimapRuleBlock rule) {
        List<SourceBinding> result = new ArrayList<>();
        for (IlimapRuleElement element : rule.elements()) {
            collectSources(element, result);
        }
        return result;
    }

    private static void collectSources(IlimapRuleElement element, List<SourceBinding> result) {
        if (element instanceof IlimapSourceStmt source) {
            result.add(new SourceBinding(source.alias(), source.inputIds(), source.sourceClass()));
        } else if (element instanceof IlimapBagBlock bag) {
            collectSources(bag, result);
        }
    }

    private static void collectSources(IlimapBagBlock bag, List<SourceBinding> result) {
        if (bag.from() != null) {
            result.add(new SourceBinding(
                    bag.from().alias(),
                    List.of(bag.from().inputId()),
                    bag.from().sourceClass()));
        }
        for (IlimapBagBlock nested : bag.nestedBags()) {
            collectSources(nested, result);
        }
    }

    private static boolean isIdentifierPart(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }

    private record SourceBinding(String alias, List<String> inputIds, String sourceClass) {}

    private static String literal(IlimapLiteral literal) {
        return switch (literal) {
            case IlimapLiteral.StringLit stringLit -> "\"" + stringLit.value() + "\"";
            case IlimapLiteral.BooleanLit booleanLit -> Boolean.toString(booleanLit.value());
            case IlimapLiteral.NumberLit numberLit -> numberLit.text();
            case IlimapLiteral.NullLit ignored -> "null";
            case IlimapLiteral.HashLit hashLit -> hashLit.value();
        };
    }
}
