package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

public record IlimapBagFromStmt(
        String alias,
        String inputId,
        String sourceClass,
        IlimapExpressionText where,
        IlimapSourceRange range) implements IlimapAstNode {}
