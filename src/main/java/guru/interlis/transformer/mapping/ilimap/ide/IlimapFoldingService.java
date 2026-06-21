package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapAssignmentBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapBagBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapCreateBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapDocument;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRefBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleElement;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class IlimapFoldingService {

    private static final String REGION = "region";

    public List<IlimapFoldingRange> foldingRanges(IlimapAnalysis analysis) {
        Objects.requireNonNull(analysis, "analysis");
        if (!analysis.hasDocument()) {
            return List.of();
        }

        IlimapDocument document = analysis.document();
        List<IlimapFoldingRange> ranges = new ArrayList<>();
        addRange(ranges, document.range(), analysis.lineMap());
        document.enums().forEach(enumBlock -> addRange(ranges, enumBlock.range(), analysis.lineMap()));
        document.rules().forEach(rule -> addRuleRanges(ranges, rule, analysis.lineMap()));
        return List.copyOf(ranges);
    }

    private void addRuleRanges(List<IlimapFoldingRange> ranges, IlimapRuleBlock rule, IlimapLineMap lineMap) {
        addRange(ranges, rule.range(), lineMap);
        for (IlimapRuleElement element : rule.elements()) {
            if (element instanceof IlimapAssignmentBlock assign) {
                addRange(ranges, assign.range(), lineMap);
            } else if (element instanceof IlimapBagBlock bag) {
                addBagRanges(ranges, bag, lineMap);
            } else if (element instanceof IlimapRefBlock ref) {
                addRange(ranges, ref.range(), lineMap);
            } else if (element instanceof IlimapCreateBlock create && create.assign() != null) {
                addRange(ranges, create.assign().range(), lineMap);
            }
        }
    }

    private void addBagRanges(List<IlimapFoldingRange> ranges, IlimapBagBlock bag, IlimapLineMap lineMap) {
        addRange(ranges, bag.range(), lineMap);
        if (bag.assign() != null) {
            addRange(ranges, bag.assign().range(), lineMap);
        }
        bag.nestedBags().forEach(nestedBag -> addBagRanges(ranges, nestedBag, lineMap));
    }

    private void addRange(List<IlimapFoldingRange> ranges, IlimapSourceRange sourceRange, IlimapLineMap lineMap) {
        IlimapIdeRange range = lineMap.toIdeRange(sourceRange);
        int startLine = range.start().line();
        int endLine = foldingEndLine(range.end());
        if (endLine > startLine) {
            ranges.add(new IlimapFoldingRange(startLine, endLine, REGION));
        }
    }

    private static int foldingEndLine(IlimapIdePosition end) {
        if (end.character() == 0 && end.line() > 0) {
            return end.line() - 1;
        }
        return end.line();
    }
}
