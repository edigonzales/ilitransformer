package guru.interlis.transformer.mapping.ilimap.lexer;

public record IlimapToken(IlimapTokenType type, String text, IlimapSourceRange range) {
    public boolean isKeyword(String keyword) {
        return type == IlimapTokenType.KEYWORD && text.equals(keyword);
    }
}
