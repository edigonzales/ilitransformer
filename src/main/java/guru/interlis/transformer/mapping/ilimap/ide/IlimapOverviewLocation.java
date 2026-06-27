package guru.interlis.transformer.mapping.ilimap.ide;

public record IlimapOverviewLocation(int line, int character, Integer endLine, Integer endCharacter) {

    public static IlimapOverviewLocation point(int line, int character) {
        return new IlimapOverviewLocation(line, character, null, null);
    }
}
