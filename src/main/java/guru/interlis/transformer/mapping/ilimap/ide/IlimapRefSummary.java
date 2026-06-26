package guru.interlis.transformer.mapping.ilimap.ide;

public record IlimapRefSummary(
        String name,
        String association,
        String role,
        boolean required,
        String targetRuleId,
        String sourceRef,
        IlimapOverviewLocation location) {}
