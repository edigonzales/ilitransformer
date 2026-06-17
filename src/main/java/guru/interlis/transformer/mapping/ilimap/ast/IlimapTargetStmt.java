package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

public record IlimapTargetStmt(String outputId, String targetClass, IlimapSourceRange range)
        implements IlimapRuleElement {}
