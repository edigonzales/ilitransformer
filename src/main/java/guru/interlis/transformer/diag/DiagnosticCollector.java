package guru.interlis.transformer.diag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DiagnosticCollector {
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    public void add(Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }

    public List<Diagnostic> all() {
        return Collections.unmodifiableList(diagnostics);
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == Severity.ERROR);
    }

    public long errors() {
        return diagnostics.stream().filter(d -> d.severity() == Severity.ERROR).count();
    }

    public long warnings() {
        return diagnostics.stream()
                .filter(d -> d.severity() == Severity.WARNING)
                .count();
    }
}
