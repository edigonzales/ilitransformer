package guru.interlis.transformer.mapping.ilimap.ide;

public record IlimapDiagnosticSummary(
        String code,
        String severity,
        String message,
        int line,
        int character,
        String nodeId,
        IlimapOverviewLocation location,
        String ownerNodeId,
        String ruleId,
        String inputId,
        String outputId,
        String enumMapId,
        String targetClass,
        String targetAttribute) {}
