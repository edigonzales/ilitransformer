package guru.interlis.transformer.mapping.ilimap.ide;

public record IlimapRuleSummary(
        String id,
        String targetOutput,
        String targetClass,
        int sourceCount,
        int assignmentCount,
        int bagCount,
        int refCount,
        String status,
        String nodeId,
        IlimapOverviewLocation location) {}
