package guru.interlis.transformer.mapping.ilimap.ide;

public record IlimapTraceParams(
        String uri,
        String mode,
        String ruleId,
        String targetAttribute,
        String sourceAlias,
        String sourceMember,
        IlimapIdePosition position) {}
