package guru.interlis.transformer.mapping.ilimap.ide;

public record IlimapTraceUsage(
        String ruleId,
        String targetOutput,
        String targetClass,
        String targetAttribute,
        String context,
        String expression,
        IlimapOverviewLocation location) {}
