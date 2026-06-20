package guru.interlis.transformer.mapping.ilimap.lsp;

import guru.interlis.transformer.mapping.ilimap.ide.IlimapIdeDiagnostic;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapIdeSeverity;

import java.util.List;
import java.util.Objects;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

public final class IlimapLspDiagnosticMapper {

    private static final String SOURCE = "ilimap";

    private final IlimapLspRangeMapper rangeMapper;

    public IlimapLspDiagnosticMapper() {
        this(new IlimapLspRangeMapper());
    }

    IlimapLspDiagnosticMapper(IlimapLspRangeMapper rangeMapper) {
        this.rangeMapper = Objects.requireNonNull(rangeMapper, "rangeMapper");
    }

    public List<Diagnostic> map(List<IlimapIdeDiagnostic> diagnostics) {
        Objects.requireNonNull(diagnostics, "diagnostics");
        return diagnostics.stream().map(this::map).toList();
    }

    public Diagnostic map(IlimapIdeDiagnostic diagnostic) {
        Objects.requireNonNull(diagnostic, "diagnostic");
        Diagnostic lspDiagnostic = new Diagnostic();
        lspDiagnostic.setRange(rangeMapper.toLspRange(diagnostic.range()));
        lspDiagnostic.setSeverity(mapSeverity(diagnostic.severity()));
        lspDiagnostic.setCode(diagnostic.code());
        lspDiagnostic.setSource(SOURCE);
        lspDiagnostic.setMessage(diagnostic.message());
        return lspDiagnostic;
    }

    private DiagnosticSeverity mapSeverity(IlimapIdeSeverity severity) {
        return switch (severity) {
            case ERROR -> DiagnosticSeverity.Error;
            case WARNING -> DiagnosticSeverity.Warning;
            case INFORMATION -> DiagnosticSeverity.Information;
            case HINT -> DiagnosticSeverity.Hint;
        };
    }
}
