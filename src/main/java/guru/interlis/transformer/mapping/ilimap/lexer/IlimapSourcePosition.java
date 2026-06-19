package guru.interlis.transformer.mapping.ilimap.lexer;

public record IlimapSourcePosition(int offset, int line, int column) {
    public static IlimapSourcePosition start() {
        return new IlimapSourcePosition(0, 1, 1);
    }
}
