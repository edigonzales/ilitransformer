package guru.interlis.transformer.mapping.ilimap.ide;

public record IlimapTraceStep(
        String nodeId,
        String kind,
        String label,
        String detail,
        String status,
        IlimapOverviewLocation location) {}
