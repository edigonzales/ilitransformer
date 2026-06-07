package guru.interlis.transformer.mapping.plan;

public enum ExpressionKind {
    LITERAL_TEXT,
    LITERAL_NUMBER,
    LITERAL_BOOLEAN,
    LITERAL_ENUM,
    SOURCE_PATH,
    FUNCTION_CALL,
    UNKNOWN
}
