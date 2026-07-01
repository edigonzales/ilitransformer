package guru.interlis.transformer.mapping.ilimap.ide;

public record IlimapTraceDependency(
        String kind,
        String alias,
        String member,
        String sourceClass,
        String enumMapId,
        String functionName,
        String literal,
        IlimapOverviewLocation location,
        IlimapOverviewLocation definitionLocation) {}
