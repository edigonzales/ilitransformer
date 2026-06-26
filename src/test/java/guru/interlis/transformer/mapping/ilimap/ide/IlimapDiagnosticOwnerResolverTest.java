package guru.interlis.transformer.mapping.ilimap.ide;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class IlimapDiagnosticOwnerResolverTest {

    private static final IlimapAnalysisOptions OPTIONS = IlimapAnalysisOptions.defaults(Path.of("."));

    private final IlimapAnalysisService analysisService = new IlimapAnalysisService();
    private final IlimapDiagnosticOwnerResolver resolver = new IlimapDiagnosticOwnerResolver();

    @Test
    void resolvesDiagnosticInsideAssignmentToRuleAndTargetAttribute() {
        IlimapAnalysis analysis = analyze(validMapping());

        IlimapDiagnosticOwner owner = resolver.resolve(analysis, diagnosticAt(analysis, "s.X"));

        assertThat(owner.ruleId()).isEqualTo("r1");
        assertThat(owner.targetClass()).isEqualTo("M.A");
        assertThat(owner.targetAttribute()).isEqualTo("X");
        assertThat(owner.ownerNodeId()).isEqualTo("rule:r1:assign:X");
        assertThat(owner.inputId()).isNull();
        assertThat(owner.outputId()).isNull();
        assertThat(owner.enumMapId()).isNull();
    }

    @Test
    void resolvesDiagnosticInsideRuleButOutsideAssignmentToRuleOnly() {
        IlimapAnalysis analysis = analyze(validMapping());

        IlimapDiagnosticOwner owner = resolver.resolve(analysis, diagnosticAt(analysis, "target out"));

        assertThat(owner.ruleId()).isEqualTo("r1");
        assertThat(owner.targetClass()).isEqualTo("M.A");
        assertThat(owner.targetAttribute()).isNull();
        assertThat(owner.ownerNodeId()).isEqualTo("rule:r1");
    }

    @Test
    void resolvesDiagnosticInsideInputToInput() {
        IlimapAnalysis analysis = analyze(validMapping());

        IlimapDiagnosticOwner owner = resolver.resolve(analysis, diagnosticAt(analysis, "in.xtf"));

        assertThat(owner.inputId()).isEqualTo("src");
        assertThat(owner.ownerNodeId()).isEqualTo("input:src");
        assertThat(owner.ruleId()).isNull();
    }

    @Test
    void resolvesDiagnosticInsideOutputToOutput() {
        IlimapAnalysis analysis = analyze(validMapping());

        IlimapDiagnosticOwner owner = resolver.resolve(analysis, diagnosticAt(analysis, "out.xtf"));

        assertThat(owner.outputId()).isEqualTo("out");
        assertThat(owner.ownerNodeId()).isEqualTo("output:out");
    }

    @Test
    void resolvesDiagnosticInsideEnumToEnumMap() {
        IlimapAnalysis analysis = analyze(validMapping());

        IlimapDiagnosticOwner owner = resolver.resolve(analysis, diagnosticAt(analysis, "\"old\""));

        assertThat(owner.enumMapId()).isEqualTo("Quality");
        assertThat(owner.ownerNodeId()).isEqualTo("enum:Quality");
    }

    @Test
    void leavesDiagnosticOutsideAnyBlockUnowned() {
        IlimapAnalysis analysis = analyze(validMapping());
        IlimapIdeRange start = new IlimapIdeRange(new IlimapIdePosition(0, 0), new IlimapIdePosition(0, 0));
        IlimapIdeDiagnostic diagnostic =
                new IlimapIdeDiagnostic("TOP", IlimapIdeSeverity.WARNING, "top", start, null);

        IlimapDiagnosticOwner owner = resolver.resolve(analysis, diagnostic);

        assertThat(owner.ownerNodeId()).isNull();
        assertThat(owner.ruleId()).isNull();
        assertThat(owner.inputId()).isNull();
        assertThat(owner.outputId()).isNull();
        assertThat(owner.enumMapId()).isNull();
        assertThat(owner.targetClass()).isNull();
        assertThat(owner.targetAttribute()).isNull();
    }

    @Test
    void leavesDiagnosticUnownedWhenDocumentUnavailable() {
        IlimapAnalysis analysis = analyze(unparsableMapping());
        assertThat(analysis.hasDocument()).isFalse();
        IlimapIdeRange start = new IlimapIdeRange(new IlimapIdePosition(0, 0), new IlimapIdePosition(0, 0));
        IlimapIdeDiagnostic diagnostic =
                new IlimapIdeDiagnostic("PARSE", IlimapIdeSeverity.ERROR, "parse", start, null);

        IlimapDiagnosticOwner owner = resolver.resolve(analysis, diagnostic);

        assertThat(owner).isEqualTo(IlimapDiagnosticOwner.none());
    }

    private IlimapAnalysis analyze(String source) {
        return analysisService.analyze("file:///test.ilimap", source, OPTIONS);
    }

    private static IlimapIdeDiagnostic diagnosticAt(IlimapAnalysis analysis, String needle) {
        int offset = analysis.text().indexOf(needle);
        assertThat(offset).isGreaterThanOrEqualTo(0);
        IlimapIdePosition position = analysis.lineMap().toIdePosition(offset);
        return new IlimapIdeDiagnostic(
                "TEST", IlimapIdeSeverity.WARNING, "test", new IlimapIdeRange(position, position), null);
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
                    }
                    ref Parent {
                      target rule r1 sourceRef s.Parent;
                    }
                  }
                }
                """;
    }

    private static String unparsableMapping() {
        return """
                mapping v2 {
                  input src {
                    path "in.xtf"
                    model "M";
                  }
                }
                """;
    }
}
