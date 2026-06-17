package guru.interlis.transformer.mapping.ilimap.lexer;

public enum IlimapTokenType {
    IDENTIFIER,
    STRING,
    NUMBER,
    BOOLEAN,
    NULL,
    HASH_LITERAL,

    LBRACE,
    RBRACE,
    LPAREN,
    RPAREN,
    COMMA,
    SEMICOLON,
    EQUALS,
    ARROW,

    KEYWORD,
    EOF
}
