package guru.interlis.transformer.mapping.ilimap.lsp;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.mapping.ilimap.ide.IlimapIdeDiagnostic;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapIdePosition;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapIdeRange;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapIdeSeverity;

import java.util.List;

import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;

class IlimapLspDiagnosticMapperTest {

    private final IlimapLspDiagnosticMapper mapper = new IlimapLspDiagnosticMapper();

    @Test
    void mapsErrorSeverity() {
        var diagnostic = mapper.map(ideDiagnostic(IlimapIdeSeverity.ERROR));

        assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Error);
    }

    @Test
    void mapsWarningSeverity() {
        var diagnostic = mapper.map(ideDiagnostic(IlimapIdeSeverity.WARNING));

        assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Warning);
    }

    @Test
    void mapsInformationAndHintSeverity() {
        assertThat(mapper.map(ideDiagnostic(IlimapIdeSeverity.INFORMATION)).getSeverity())
                .isEqualTo(DiagnosticSeverity.Information);
        assertThat(mapper.map(ideDiagnostic(IlimapIdeSeverity.HINT)).getSeverity())
                .isEqualTo(DiagnosticSeverity.Hint);
    }

    @Test
    void mapsCodeMessageAndSource() {
        var diagnostic = mapper.map(new IlimapIdeDiagnostic(
                "ILIMAP_TEST",
                IlimapIdeSeverity.ERROR,
                "Something is wrong",
                new IlimapIdeRange(new IlimapIdePosition(0, 0), new IlimapIdePosition(0, 1)),
                "Fix it"));

        assertThat(diagnostic.getCode().getLeft()).isEqualTo("ILIMAP_TEST");
        assertThat(diagnostic.getMessage().getLeft()).isEqualTo("Something is wrong");
        assertThat(diagnostic.getSource()).isEqualTo("ilimap");
    }

    @Test
    void mapsRangeToZeroBasedLspRange() {
        var diagnostic = mapper.map(new IlimapIdeDiagnostic(
                "ILIMAP_TEST",
                IlimapIdeSeverity.ERROR,
                "message",
                new IlimapIdeRange(new IlimapIdePosition(1, 2), new IlimapIdePosition(3, 4)),
                null));

        assertThat(diagnostic.getRange()).isEqualTo(new Range(new Position(1, 2), new Position(3, 4)));
    }

    @Test
    void mapsList() {
        var diagnostics = mapper.map(List.of(ideDiagnostic(IlimapIdeSeverity.ERROR)));

        assertThat(diagnostics).hasSize(1);
    }

    private static IlimapIdeDiagnostic ideDiagnostic(IlimapIdeSeverity severity) {
        return new IlimapIdeDiagnostic(
                "ILIMAP_TEST",
                severity,
                "message",
                new IlimapIdeRange(new IlimapIdePosition(0, 0), new IlimapIdePosition(0, 1)),
                null);
    }
}
