package guru.interlis.transformer.mapping.ilimap.ide;

public record IlimapMappingOutputSummary(
        String id, String path, String model, String format, String nodeId, IlimapOverviewLocation location) {}
