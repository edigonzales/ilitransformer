package guru.interlis.transformer.mapping.ilimap.ide;

public record IlimapDiagnosticSummary(String code, String severity, String message, int line, int character) {}
