package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapAssignmentBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapBagBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapCreateBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapDefaultsBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapEnumBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapInputBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapLiteral;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapOutputBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRefBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleElement;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapSourceStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapTargetStmt;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSymbolKind;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class IlimapHoverService {

    private final IlimapSymbolReferenceResolver symbolResolver;

    public IlimapHoverService() {
        this(new IlimapSymbolReferenceResolver());
    }

    IlimapHoverService(IlimapSymbolReferenceResolver symbolResolver) {
        this.symbolResolver = Objects.requireNonNull(symbolResolver, "symbolResolver");
    }

    public Optional<IlimapHover> hoverAt(IlimapAnalysis analysis, IlimapIdePosition position) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(position, "position");

        return symbolResolver.resolve(analysis, position).flatMap(resolved -> markdown(resolved).map(markdown -> new IlimapHover(resolved.range(), markdown)));
    }

    private Optional<String> markdown(IlimapResolvedSymbol resolved) {
        if (resolved.symbol().kind() == IlimapSymbolKind.INPUT && resolved.symbol().node() instanceof IlimapInputBlock input) {
            return Optional.of(inputMarkdown(input));
        }
        if (resolved.symbol().kind() == IlimapSymbolKind.OUTPUT && resolved.symbol().node() instanceof IlimapOutputBlock output) {
            return Optional.of(outputMarkdown(output));
        }
        if (resolved.symbol().kind() == IlimapSymbolKind.RULE && resolved.symbol().node() instanceof IlimapRuleBlock rule) {
            return Optional.of(ruleMarkdown(rule));
        }
        if (resolved.symbol().kind() == IlimapSymbolKind.ENUM_MAP && resolved.symbol().node() instanceof IlimapEnumBlock enumBlock) {
            return Optional.of(enumMarkdown(enumBlock));
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
        enumBlock.entries().stream()
                .limit(5)
                .forEach(entry -> markdown.append("\n- `")
                        .append(literal(entry.source()))
                        .append("` => `")
                        .append(literal(entry.target()))
                        .append("`"));
        if (enumBlock.entries().size() > 5) {
            markdown.append("\n- ...");
        }
        return markdown.toString().stripTrailing();
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
        return (int) rule.elements().stream().filter(IlimapRefBlock.class::isInstance).count();
    }

    private static void appendOptional(StringBuilder markdown, String label, String value) {
        if (value != null && !value.isBlank()) {
            markdown.append(label).append(": `").append(value).append("`\n");
        }
    }

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
