package guru.interlis.transformer.mapping.ilimap;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapDocument;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSymbolTable;
import guru.interlis.transformer.mapping.model.JobConfig;

import java.util.List;

public record IlimapLoadResult(
        IlimapDocument document,
        IlimapSymbolTable symbols,
        JobConfig jobConfig,
        List<Diagnostic> diagnostics) {

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == Severity.ERROR);
    }
}
