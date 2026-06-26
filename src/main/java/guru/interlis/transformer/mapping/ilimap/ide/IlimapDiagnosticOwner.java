package guru.interlis.transformer.mapping.ilimap.ide;

public record IlimapDiagnosticOwner(
        String ownerNodeId,
        String ruleId,
        String inputId,
        String outputId,
        String enumMapId,
        String targetClass,
        String targetAttribute) {

    public static IlimapDiagnosticOwner none() {
        return new IlimapDiagnosticOwner(null, null, null, null, null, null, null);
    }
}
