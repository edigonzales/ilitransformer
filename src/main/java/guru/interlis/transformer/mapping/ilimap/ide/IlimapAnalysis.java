package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapDocument;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSymbolTable;

import java.util.List;
import java.util.Objects;

public record IlimapAnalysis(
        String uri,
        String text,
        IlimapDocument document,
        IlimapSymbolTable symbols,
        List<IlimapIdeDiagnostic> diagnostics,
        IlimapLineMap lineMap) {

    public IlimapAnalysis {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(symbols, "symbols");
        Objects.requireNonNull(diagnostics, "diagnostics");
        Objects.requireNonNull(lineMap, "lineMap");
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean hasDocument() {
        return document != null;
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == IlimapIdeSeverity.ERROR);
    }
}
