package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

public record IlimapCreateBlock(String targetClass, IlimapAssignmentBlock assign, IlimapSourceRange range)
        implements IlimapRuleElement {}
