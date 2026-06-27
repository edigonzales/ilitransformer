package guru.interlis.transformer.mapping.ilimap.ide;

public record IlimapJoinSummary(
        String type, String leftAlias, String rightAlias, String condition, IlimapOverviewLocation location) {}
