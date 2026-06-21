package guru.interlis.transformer.mapping.ilimap.ide;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.diag.DiagnosticCode;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class IlimapAnalysisServiceTest {

    private static final IlimapAnalysisOptions OPTIONS = IlimapAnalysisOptions.defaults(Path.of("."));

    private final IlimapAnalysisService service = new IlimapAnalysisService();

    @Test
    void analyzesValidMinimalIlimapWithoutErrors() {
        var analysis = service.analyze("file:///test.ilimap", validMapping(), OPTIONS);

        assertThat(analysis.hasDocument()).isTrue();
        assertThat(analysis.hasErrors()).isFalse();
        assertThat(analysis.diagnostics()).isEmpty();
    }

    @Test
    void returnsSyntaxDiagnosticForMissingSemicolon() {
        var analysis = service.analyze("file:///test.ilimap", missingSemicolonMapping(), OPTIONS);

        assertThat(analysis.hasErrors()).isTrue();
        assertThat(analysis.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.ILIMAP_SYNTAX_ERROR);
            assertThat(diagnostic.message()).contains("SEMICOLON");
            assertThat(diagnostic.range().start().line()).isGreaterThanOrEqualTo(0);
        });
    }

    @Test
    void returnsSemanticDiagnosticForUnknownInput() {
        var analysis = service.analyze("file:///test.ilimap", unknownInputMapping(), OPTIONS);

        assertThat(analysis.hasDocument()).isTrue();
        assertThat(analysis.hasErrors()).isTrue();
        assertThat(analysis.diagnostics()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.ILIMAP_UNKNOWN_INPUT);
            assertThat(diagnostic.range().start().line()).isGreaterThanOrEqualTo(0);
        });
    }

    @Test
    void returnsNullDocumentOnParserError() {
        var analysis = service.analyze("file:///test.ilimap", missingSemicolonMapping(), OPTIONS);

        assertThat(analysis.hasDocument()).isFalse();
        assertThat(analysis.document()).isNull();
    }

    @Test
    void returnsDocumentAndSymbolsOnValidFile() {
        var analysis = service.analyze("file:///test.ilimap", validMapping(), OPTIONS);

        assertThat(analysis.document()).isNotNull();
        assertThat(analysis.symbols().resolveInput("src")).isPresent();
        assertThat(analysis.symbols().resolveOutput("out")).isPresent();
        assertThat(analysis.symbols().resolveRule("r1")).isPresent();
    }

    private static String validMapping() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                }
                """;
    }

    private static String missingSemicolonMapping() {
        return """
                mapping v2 {
                  input src {
                    path "in.xtf"
                    model "M";
                  }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                }
                """;
    }

    private static String unknownInputMapping() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from missing class "M.A";
                  }
                }
                """;
    }
}
