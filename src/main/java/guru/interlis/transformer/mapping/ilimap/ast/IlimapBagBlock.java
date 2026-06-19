package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import java.util.List;

public record IlimapBagBlock(
        String id,
        IlimapBagFromStmt from,
        String structure,
        String mode,
        Integer maxItems,
        IlimapParentRefStmt parentRef,
        IlimapAssignmentBlock assign,
        List<IlimapBagBlock> nestedBags,
        IlimapSourceRange range)
        implements IlimapRuleElement, IlimapBagElement {}
