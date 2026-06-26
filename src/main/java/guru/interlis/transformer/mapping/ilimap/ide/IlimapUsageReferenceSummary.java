package guru.interlis.transformer.mapping.ilimap.ide;

public record IlimapUsageReferenceSummary(
        String ruleId, String context, String targetAttribute, IlimapOverviewLocation location) {}
