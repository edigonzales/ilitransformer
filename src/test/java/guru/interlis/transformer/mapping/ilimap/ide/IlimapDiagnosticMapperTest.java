package guru.interlis.transformer.mapping.ilimap.ide;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.Severity;

import org.junit.jupiter.api.Test;

class IlimapDiagnosticMapperTest {

    private final IlimapDiagnosticMapper mapper = new IlimapDiagnosticMapper();

    @Test
    void mapsLineColumnSourcePathToOneCharacterRange() {
        var lineMap = new IlimapLineMap("ab\ncd");
        var fallbackRange = new IlimapIdeRange(new IlimapIdePosition(0, 0), new IlimapIdePosition(0, 0));
        var diagnostic = new Diagnostic("CODE", Severity.ERROR, "message", "mapping.ilimap:2:2", null);

        var mapped = mapper.map(diagnostic, lineMap, fallbackRange);

        assertThat(mapped.severity()).isEqualTo(IlimapIdeSeverity.ERROR);
        assertThat(mapped.range()).isEqualTo(new IlimapIdeRange(new IlimapIdePosition(1, 1), new IlimapIdePosition(1, 2)));
    }

    @Test
    void mapsAtLineColumnSourcePathToOneCharacterRange() {
        var lineMap = new IlimapLineMap("ab\ncd");
        var fallbackRange = new IlimapIdeRange(new IlimapIdePosition(0, 0), new IlimapIdePosition(0, 0));
        var diagnostic = new Diagnostic("CODE", Severity.WARNING, "message", "at line 2, column 1: issue", null);

        var mapped = mapper.map(diagnostic, lineMap, fallbackRange);

        assertThat(mapped.severity()).isEqualTo(IlimapIdeSeverity.WARNING);
        assertThat(mapped.range()).isEqualTo(new IlimapIdeRange(new IlimapIdePosition(1, 0), new IlimapIdePosition(1, 1)));
    }

    @Test
    void usesFallbackRangeWhenSourcePathHasNoPosition() {
        var lineMap = new IlimapLineMap("ab\ncd");
        var fallbackRange = new IlimapIdeRange(new IlimapIdePosition(0, 0), new IlimapIdePosition(0, 0));
        var diagnostic = new Diagnostic("CODE", Severity.INFO, "message", "mapping.ilimap", null);

        var mapped = mapper.map(diagnostic, lineMap, fallbackRange);

        assertThat(mapped.severity()).isEqualTo(IlimapIdeSeverity.INFORMATION);
        assertThat(mapped.range()).isEqualTo(fallbackRange);
    }
}
