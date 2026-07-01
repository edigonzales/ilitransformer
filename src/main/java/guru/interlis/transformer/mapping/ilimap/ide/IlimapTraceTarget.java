package guru.interlis.transformer.mapping.ilimap.ide;

public record IlimapTraceTarget(
        String outputId,
        String targetClass,
        String targetAttribute,
        String type,
        String cardinality,
        boolean mandatory,
        String assignmentKind,
        IlimapOverviewLocation location) {}
