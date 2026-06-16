package guru.interlis.transformer.model;

import guru.interlis.transformer.diag.DiagnosticCollector;

public record IliModelCompileResult(
        ch.interlis.ili2c.metamodel.TransferDescription transferDescription, DiagnosticCollector diagnostics) {
    public boolean hasErrors() {
        return diagnostics.hasErrors();
    }
}
