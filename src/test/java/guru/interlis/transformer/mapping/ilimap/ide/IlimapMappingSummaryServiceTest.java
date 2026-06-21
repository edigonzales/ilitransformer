package guru.interlis.transformer.mapping.ilimap.ide;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class IlimapMappingSummaryServiceTest {

    private static final IlimapAnalysisOptions OPTIONS = IlimapAnalysisOptions.defaults(Path.of("."));

    private final IlimapAnalysisService analysisService = new IlimapAnalysisService();
    private final IlimapMappingSummaryService summaryService = new IlimapMappingSummaryService();

    @Test
    void summarizesValidMappingFromAst() {
        IlimapMappingSummary summary = summaryService.summarize(analyze(validMapping()));

        assertThat(summary.available()).isTrue();
        assertThat(summary.mappingName()).isEqualTo("Profile");
        assertThat(summary.inputCount()).isEqualTo(1);
        assertThat(summary.outputCount()).isEqualTo(1);
        assertThat(summary.ruleCount()).isEqualTo(1);
        assertThat(summary.enumMapCount()).isEqualTo(1);
        assertThat(summary.bagCount()).isEqualTo(2);
        assertThat(summary.refCount()).isEqualTo(1);
        assertThat(summary.errorCount()).isZero();
        assertThat(summary.warningCount()).isZero();
        assertThat(summary.inputs()).extracting(IlimapMappingInputSummary::id).containsExactly("src");
        assertThat(summary.outputs()).extracting(IlimapMappingOutputSummary::id).containsExactly("out");
        assertThat(summary.enumMaps())
                .singleElement()
                .satisfies(enumMap -> {
                    assertThat(enumMap.id()).isEqualTo("Quality");
                    assertThat(enumMap.entryCount()).isEqualTo(1);
                });
        assertThat(summary.rules())
                .singleElement()
                .satisfies(rule -> {
                    assertThat(rule.id()).isEqualTo("r1");
                    assertThat(rule.targetOutput()).isEqualTo("out");
                    assertThat(rule.targetClass()).isEqualTo("M.A");
                    assertThat(rule.sourceCount()).isEqualTo(1);
                    assertThat(rule.assignmentCount()).isEqualTo(3);
                    assertThat(rule.bagCount()).isEqualTo(2);
                    assertThat(rule.refCount()).isEqualTo(1);
                    assertThat(rule.status()).isEqualTo("ok");
                });
    }

    @Test
    void summarizesSyntaxErrorWithoutStructuralCounts() {
        IlimapMappingSummary summary = summaryService.summarize(analyze(missingSemicolonMapping()));

        assertThat(summary.available()).isTrue();
        assertThat(summary.inputCount()).isZero();
        assertThat(summary.outputCount()).isZero();
        assertThat(summary.ruleCount()).isZero();
        assertThat(summary.enumMapCount()).isZero();
        assertThat(summary.bagCount()).isZero();
        assertThat(summary.refCount()).isZero();
        assertThat(summary.errorCount()).isEqualTo(1);
        assertThat(summary.diagnostics())
                .singleElement()
                .satisfies(diagnostic -> {
                    assertThat(diagnostic.severity()).isEqualTo("error");
                    assertThat(diagnostic.message()).contains("SEMICOLON");
                });
    }

    @Test
    void derivesRuleStatusFromDiagnosticsInsideRuleRange() {
        IlimapAnalysis analysis = analyze(validMapping());
        IlimapIdeRange warningRange = rangeAt(analysis, "X = s.X;");
        IlimapAnalysis warningAnalysis = withDiagnostics(
                analysis,
                List.of(new IlimapIdeDiagnostic("TEST_WARNING", IlimapIdeSeverity.WARNING, "warning", warningRange, null)));

        IlimapMappingSummary warningSummary = summaryService.summarize(warningAnalysis);

        assertThat(warningSummary.rules()).singleElement().extracting(IlimapRuleSummary::status).isEqualTo("warning");

        IlimapMappingSummary errorSummary = summaryService.summarize(analyze(unknownInputMapping()));

        assertThat(errorSummary.errorCount()).isGreaterThan(0);
        assertThat(errorSummary.rules()).singleElement().extracting(IlimapRuleSummary::status).isEqualTo("error");
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
                  enum Quality {
                    "old" -> "new";
                  }
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

    private static String missingSemicolonMapping() {
        return """
                mapping v2 {
                  input src {
                    path "in.xtf"
                    model "M";
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
