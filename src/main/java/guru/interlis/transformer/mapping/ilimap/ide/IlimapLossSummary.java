package guru.interlis.transformer.mapping.ilimap.ide;

public record IlimapLossSummary(
        String sourcePath, String reasonCode, String description, String when, IlimapOverviewLocation location) {}
