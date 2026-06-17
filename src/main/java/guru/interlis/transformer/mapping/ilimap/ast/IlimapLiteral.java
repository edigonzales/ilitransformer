package guru.interlis.transformer.mapping.ilimap.ast;

import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

public sealed interface IlimapLiteral extends IlimapAstNode
        permits IlimapLiteral.StringLit,
                IlimapLiteral.BooleanLit,
                IlimapLiteral.NumberLit,
                IlimapLiteral.NullLit,
                IlimapLiteral.HashLit {

    record StringLit(String value, IlimapSourceRange range) implements IlimapLiteral {}

    record BooleanLit(boolean value, IlimapSourceRange range) implements IlimapLiteral {}

    record NumberLit(String text, IlimapSourceRange range) implements IlimapLiteral {}

    record NullLit(IlimapSourceRange range) implements IlimapLiteral {}

    record HashLit(String value, IlimapSourceRange range) implements IlimapLiteral {}
}
