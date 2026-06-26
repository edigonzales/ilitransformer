package guru.interlis.transformer.mapping.ilimap.ide;

public record IlimapCoverageAttributeSummary(
        String name,
        String type,
        String cardinality,
        boolean mandatory,
        boolean assigned,
        int line,
        int character,
        String nodeId,
        IlimapOverviewLocation location,
        String status,
        String expression,
        String sourceSummary) {}
