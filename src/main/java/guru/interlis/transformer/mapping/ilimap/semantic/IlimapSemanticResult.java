package guru.interlis.transformer.mapping.ilimap.semantic;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapDocument;

import java.util.List;

public record IlimapSemanticResult(IlimapDocument document, IlimapSymbolTable symbols, List<Diagnostic> diagnostics) {

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == Severity.ERROR);
    }
}
