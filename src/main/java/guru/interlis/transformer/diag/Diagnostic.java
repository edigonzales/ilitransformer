package guru.interlis.transformer.diag;

public record Diagnostic(String code, Severity severity, String message, String sourcePath, String suggestion) {}
