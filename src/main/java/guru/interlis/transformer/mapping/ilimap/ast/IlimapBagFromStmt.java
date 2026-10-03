package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

/** Source class, or an embedded attribute of the enclosing source alias. */
public record IlimapBagFromStmt(
        String alias,
        String inputId,
        String sourceClass,
        IlimapExpressionText where,
        IlimapSourceRange range,
        String attributePath)
        implements IlimapAstNode {
    public IlimapBagFromStmt(
            String alias, String inputId, String sourceClass, IlimapExpressionText where, IlimapSourceRange range) {
        this(alias, inputId, sourceClass, where, range, null);
    }
}
