package guru.interlis.transformer.mapping.ilimap.semantic;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class IlimapScope {
    private final IlimapScope parent;
    private final Map<String, IlimapSymbol> symbols = new LinkedHashMap<>();

    public IlimapScope(IlimapScope parent) {
        this.parent = parent;
    }

    public Optional<IlimapSymbol> resolve(String name) {
        Optional<IlimapSymbol> local = resolveLocal(name);
        if (local.isPresent()) {
            return local;
        }
        if (parent != null) {
            return parent.resolve(name);
        }
        return Optional.empty();
    }

    public Optional<IlimapSymbol> resolveLocal(String name) {
        return Optional.ofNullable(symbols.get(name));
    }

    public boolean define(IlimapSymbol symbol, DiagnosticCollector diagnostics) {
        IlimapSymbol existing = symbols.get(symbol.name());
        if (existing != null) {
            String rangeInfo = null;
            if (symbol.node() != null) {
                var range = symbol.node().range();
                rangeInfo = "line " + range.start().line() + ", column " + range.start().column();
            }
            diagnostics.add(new Diagnostic(
                    DiagnosticCode.ILIMAP_DUPLICATE_ID,
                    Severity.ERROR,
                    "duplicate " + symbol.kind().name().toLowerCase() + " ID '" + symbol.name() + "'",
                    rangeInfo,
                    "Use a unique name"));
            return false;
        }
        symbols.put(symbol.name(), symbol);
        return true;
    }

    public Map<String, IlimapSymbol> allLocal() {
        return Map.copyOf(symbols);
    }
}
