package guru.interlis.transformer.mapping.ilimap.ide;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class IlimapCodeLensServiceTest {

    private static final IlimapAnalysisOptions OPTIONS = IlimapAnalysisOptions.defaults(Path.of("."));

    private final IlimapAnalysisService analysisService = new IlimapAnalysisService();
    private final IlimapCodeLensService codeLensService = new IlimapCodeLensService();

    @Test
    void producesShowInOverviewAndCoverageLensForEachRule() {
        List<IlimapCodeLensSummary> lenses = codeLensService.codeLenses(analyze(validMapping()));

        assertThat(lenses).hasSize(2);
        assertThat(lenses.get(0)).satisfies(lens -> {
            assertThat(lens.title()).isEqualTo("Show in Overview");
            assertThat(lens.command()).isEqualTo(IlimapCodeLensService.SHOW_IN_OVERVIEW_COMMAND);
            assertThat(lens.ruleId()).isEqualTo("r1");
            assertThat(lens.location()).isNotNull();
            assertThat(lens.location().line()).isGreaterThanOrEqualTo(0);
        });
        assertThat(lenses.get(1)).satisfies(lens -> {
            assertThat(lens.command()).isEqualTo(IlimapCodeLensService.SHOW_COVERAGE_COMMAND);
            assertThat(lens.ruleId()).isEqualTo("r1");
            assertThat(lens.title()).isEqualTo("1 ref · 2 bags");
        });
    }

    @Test
    void includesWarningSegmentForRuleDiagnostics() {
        IlimapAnalysis analysis = analyze(validMapping());
        IlimapIdeRange warningRange = rangeAt(analysis, "s.X;");
        IlimapAnalysis withWarning = withDiagnostics(
                analysis,
                List.of(new IlimapIdeDiagnostic(
                        "TEST_WARNING", IlimapIdeSeverity.WARNING, "warning", warningRange, null)));

        List<IlimapCodeLensSummary> lenses = codeLensService.codeLenses(withWarning);

        assertThat(lenses).hasSize(2);
        assertThat(lenses.get(1).command()).isEqualTo(IlimapCodeLensService.SHOW_COVERAGE_COMMAND);
        assertThat(lenses.get(1).title()).isEqualTo("1 ref · 2 bags · 1 warning");
    }

    @Test
    void returnsNoLensesWhenMappingHasNoRules() {
        List<IlimapCodeLensSummary> lenses = codeLensService.codeLenses(analyze(noRulesMapping()));

        assertThat(lenses).isEmpty();
    }

    private IlimapAnalysis analyze(String source) {
        return analysisService.analyze("file:///test.ilimap", source, OPTIONS);
    }

    private static IlimapAnalysis withDiagnostics(IlimapAnalysis analysis, List<IlimapIdeDiagnostic> diagnostics) {
        return new IlimapAnalysis(
                analysis.uri(),
                analysis.text(),
                analysis.document(),
                analysis.symbols(),
                diagnostics,
                analysis.lineMap(),
                analysis.modelIndex());
    }

    private static IlimapIdeRange rangeAt(IlimapAnalysis analysis, String needle) {
        int offset = analysis.text().indexOf(needle);
        assertThat(offset).isGreaterThanOrEqualTo(0);
        IlimapIdePosition position = analysis.lineMap().toIdePosition(offset);
        return new IlimapIdeRange(position, position);
    }

    private static String validMapping() {
        return """
                mapping v2 "Profile" {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      X = s.X;
                    }
                    bag Outer {
                      from o in src class "M.Outer";
                      assign {
                        O = o.O;
                      }
                      bag Inner {
                        from i in src class "M.Inner";
                        assign {
                          I = i.I;
                        }
                      }
                    }
                    ref Parent {
                      target rule r1 sourceRef s.Parent;
                    }
                  }
                }
                """;
    }

    private static String noRulesMapping() {
        return """
                mapping v2 "Profile" {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                }
                """;
    }
}
