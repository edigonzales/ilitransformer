package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import java.util.List;

public record IlimapRuleBlock(String id, List<IlimapRuleElement> elements, IlimapSourceRange range)
        implements IlimapAstNode {}
