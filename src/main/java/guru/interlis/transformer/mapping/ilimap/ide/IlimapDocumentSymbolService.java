package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapAssignmentBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapAstNode;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapBagBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapDocument;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRefBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleElement;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapSourceStmt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class IlimapDocumentSymbolService {

    public List<IlimapDocumentSymbol> symbols(IlimapAnalysis analysis) {
        Objects.requireNonNull(analysis, "analysis");
        if (!analysis.hasDocument()) {
            return List.of();
        }

        IlimapDocument document = analysis.document();
        List<IlimapDocumentSymbol> children = new ArrayList<>();
        if (document.job() != null) {
            children.add(symbol("job", IlimapSymbolDisplayKind.OBJECT, document.job(), analysis, List.of()));
        }
        document.inputs()
                .forEach(input -> children.add(
                        symbol("input " + input.id(), IlimapSymbolDisplayKind.FILE, input, analysis, List.of())));
        document.outputs()
                .forEach(output -> children.add(
                        symbol("output " + output.id(), IlimapSymbolDisplayKind.FILE, output, analysis, List.of())));
        document.enums()
                .forEach(enumBlock -> children.add(symbol(
                        "enum " + enumBlock.id(), IlimapSymbolDisplayKind.ENUM, enumBlock, analysis, List.of())));
        document.rules().forEach(rule -> children.add(ruleSymbol(rule, analysis)));

        return List.of(symbol(mappingName(document), IlimapSymbolDisplayKind.MODULE, document, analysis, children));
    }

    private IlimapDocumentSymbol ruleSymbol(IlimapRuleBlock rule, IlimapAnalysis analysis) {
        List<IlimapDocumentSymbol> children = new ArrayList<>();
        for (IlimapRuleElement element : rule.elements()) {
            if (element instanceof IlimapSourceStmt source) {
                children.add(
                        symbol("source " + source.alias(), IlimapSymbolDisplayKind.FIELD, source, analysis, List.of()));
            } else if (element instanceof IlimapAssignmentBlock assign) {
                children.add(symbol("assign", IlimapSymbolDisplayKind.PROPERTY, assign, analysis, List.of()));
            } else if (element instanceof IlimapBagBlock bag) {
                children.add(bagSymbol(bag, analysis));
            } else if (element instanceof IlimapRefBlock ref) {
                children.add(symbol("ref " + ref.id(), IlimapSymbolDisplayKind.OBJECT, ref, analysis, List.of()));
            }
        }
        return symbol("rule " + rule.id(), IlimapSymbolDisplayKind.METHOD, rule, analysis, children);
    }

    private IlimapDocumentSymbol bagSymbol(IlimapBagBlock bag, IlimapAnalysis analysis) {
        List<IlimapDocumentSymbol> children = new ArrayList<>();
        if (bag.assign() != null) {
            children.add(symbol("assign", IlimapSymbolDisplayKind.PROPERTY, bag.assign(), analysis, List.of()));
        }
        bag.nestedBags().forEach(nestedBag -> children.add(bagSymbol(nestedBag, analysis)));
        return symbol("bag " + bag.id(), IlimapSymbolDisplayKind.OBJECT, bag, analysis, children);
    }

    private IlimapDocumentSymbol symbol(
            String name,
            IlimapSymbolDisplayKind kind,
            IlimapAstNode node,
            IlimapAnalysis analysis,
            List<IlimapDocumentSymbol> children) {
        IlimapIdeRange range = analysis.lineMap().toIdeRange(node.range());
        return new IlimapDocumentSymbol(name, kind, range, range, children);
    }

    private static String mappingName(IlimapDocument document) {
        if (document.name() == null || document.name().isBlank()) {
            return "mapping";
        }
        return "mapping " + document.name();
    }
}
