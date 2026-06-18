package guru.interlis.transformer.mapping.ilimap.format;

public record IlimapFormatOptions(
        int indentSize,
        boolean finalNewline,
        boolean alignAssignments) {

    public static IlimapFormatOptions defaults() {
        return new IlimapFormatOptions(2, true, false);
    }
}
