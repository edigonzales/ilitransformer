package guru.interlis.transformer.mapping.ilimap.ide;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class IlimapTraceServiceTest {

    private static final IlimapAnalysisOptions OPTIONS = IlimapAnalysisOptions.defaults(Path.of("."));

    private final IlimapAnalysisService analysisService = new IlimapAnalysisService();
    private final IlimapTraceService traceService = new IlimapTraceService();

    @Test
    void traceDirectCopyAssignment() {
        IlimapAnalysis analysis = analyze(copyAssignmentMapping());
        IlimapTraceParams params =
                new IlimapTraceParams("file:///test.ilimap", "targetAttribute", "r1", "X", null, null, null);

        IlimapTraceSummary trace = traceService.trace(analysis, params);

        assertThat(trace.available()).isTrue();
        assertThat(trace.mode()).isEqualTo("targetAttribute");
        assertThat(trace.ruleId()).isEqualTo("r1");
        assertThat(trace.target()).isNotNull();
        assertThat(trace.target().targetAttribute()).isEqualTo("X");
        assertThat(trace.target().assignmentKind()).isEqualTo("assign");
        assertThat(trace.expression()).isNotNull();
        assertThat(trace.expression().text()).isEqualTo("s.X");
        assertThat(trace.expression().kind()).isEqualTo("copy");
        assertThat(trace.dependencies()).singleElement().satisfies(dep -> {
            assertThat(dep.kind()).isEqualTo("sourceAttribute");
            assertThat(dep.alias()).isEqualTo("s");
            assertThat(dep.member()).isEqualTo("X");
        });
        assertThat(trace.steps()).isNotEmpty();
    }

    @Test
    void traceComputedExpression() {
        IlimapAnalysis analysis = analyze(computedMapping());
        IlimapTraceParams params =
                new IlimapTraceParams("file:///test.ilimap", "targetAttribute", "r1", "Z", null, null, null);

        IlimapTraceSummary trace = traceService.trace(analysis, params);

        assertThat(trace.available()).isTrue();
        assertThat(trace.expression()).isNotNull();
        assertThat(trace.expression().kind()).isEqualTo("computed");
        assertThat(trace.dependencies()).hasSize(2);
    }

    @Test
    void traceEnumMapExpression() {
        IlimapAnalysis analysis = analyze(enumMapMapping());
        IlimapTraceParams params =
                new IlimapTraceParams("file:///test.ilimap", "targetAttribute", "r1", "Quality", null, null, null);

        IlimapTraceSummary trace = traceService.trace(analysis, params);

        assertThat(trace.available()).isTrue();
        assertThat(trace.expression()).isNotNull();
        assertThat(trace.expression().kind()).isEqualTo("enumMap");
        assertThat(trace.dependencies()).singleElement().satisfies(dep -> {
            assertThat(dep.kind()).isEqualTo("enumMap");
            assertThat(dep.enumMapId()).isEqualTo("Quality");
        });
    }

    @Test
    void traceMissingMandatoryTargetAttribute() {
        IlimapAnalysis analysis = analyze(copyAssignmentMapping());

        IlimapTraceParams params =
                new IlimapTraceParams("file:///test.ilimap", "targetAttribute", "r1", "Missing", null, null, null);

        IlimapTraceSummary trace = traceService.trace(analysis, params);

        assertThat(trace.available()).isTrue();
        assertThat(trace.target()).isNotNull();
        assertThat(trace.target().targetAttribute()).isEqualTo("Missing");
        assertThat(trace.expression()).isNull();
        assertThat(trace.dependencies()).isEmpty();
    }

    @Test
    void traceSourceMemberReverseUsages() {
        IlimapAnalysis analysis = analyze(twoRulesSameSourceMapping());

        IlimapTraceParams params =
                new IlimapTraceParams("file:///test.ilimap", "sourceMember", null, null, "s", "X", null);

        IlimapTraceSummary trace = traceService.trace(analysis, params);

        assertThat(trace.available()).isTrue();
        assertThat(trace.mode()).isEqualTo("sourceMember");
        assertThat(trace.usages()).isNotEmpty();
    }

    @Test
    void traceSourceMemberInSpecificRule() {
        IlimapAnalysis analysis = analyze(twoRulesSameSourceMapping());

        IlimapTraceParams params =
                new IlimapTraceParams("file:///test.ilimap", "sourceMember", "r1", null, "s", "X", null);

        IlimapTraceSummary trace = traceService.trace(analysis, params);

        assertThat(trace.available()).isTrue();
        assertThat(trace.usages()).singleElement();
        assertThat(trace.usages().get(0).ruleId()).isEqualTo("r1");
    }

    @Test
    void traceBagAssignment() {
        IlimapAnalysis analysis = analyze(bagMapping());

        IlimapTraceParams params =
                new IlimapTraceParams("file:///test.ilimap", "targetAttribute", "r1", "O", null, null, null);

        IlimapTraceSummary trace = traceService.trace(analysis, params);

        assertThat(trace.available()).isTrue();
        assertThat(trace.target()).isNotNull();
        assertThat(trace.target().targetAttribute()).isEqualTo("O");
        assertThat(trace.target().assignmentKind()).isEqualTo("bag");
    }

    @Test
    void traceRefSourceRef() {
        IlimapAnalysis analysis = analyze(refMapping());

        IlimapTraceParams params = new IlimapTraceParams(
                "file:///test.ilimap", "targetAttribute", "r1", "sourceRef.Parent", null, null, null);

        IlimapTraceSummary trace = traceService.trace(analysis, params);

        assertThat(trace.available()).isTrue();
        assertThat(trace.target()).isNotNull();
        assertThat(trace.target().assignmentKind()).isEqualTo("ref");
        assertThat(trace.expression()).isNotNull();
        assertThat(trace.expression().text()).isEqualTo("s.ParentId");
    }

    @Test
    void traceRuleReturnsSteps() {
        IlimapAnalysis analysis = analyze(copyAssignmentMapping());
        IlimapTraceParams params = new IlimapTraceParams("file:///test.ilimap", "rule", "r1", null, null, null, null);

        IlimapTraceSummary trace = traceService.trace(analysis, params);

        assertThat(trace.available()).isTrue();
        assertThat(trace.mode()).isEqualTo("rule");
        assertThat(trace.steps()).isNotEmpty();
        assertThat(trace.steps()).anySatisfy(step -> assertThat(step.kind()).isEqualTo("expression"));
    }

    @Test
    void traceUnknownRuleReturnsUnavailable() {
        IlimapAnalysis analysis = analyze(copyAssignmentMapping());
        IlimapTraceParams params =
                new IlimapTraceParams("file:///test.ilimap", "targetAttribute", "nonexistent", "X", null, null, null);

        IlimapTraceSummary trace = traceService.trace(analysis, params);

        assertThat(trace.available()).isFalse();
        assertThat(trace.message()).contains("not found");
    }

    @Test
    void traceUnparseableMappingReturnsUnavailable() {
        IlimapAnalysis analysis = analyze("invalid {{");
        IlimapTraceParams params = new IlimapTraceParams("file:///test.ilimap", "rule", "r1", null, null, null, null);

        IlimapTraceSummary trace = traceService.trace(analysis, params);

        assertThat(trace.available()).isFalse();
        assertThat(trace.message()).contains("could not be parsed");
    }

    @Test
    void usageOfSourceMemberAcrossRules() {
        IlimapAnalysis analysis = analyze(twoRulesSameSourceMapping());

        List<IlimapTraceUsage> usages = traceService.usagesOfSourceMember(analysis, "s", "X", null);

        assertThat(usages).hasSize(2);
        assertThat(usages).extracting(IlimapTraceUsage::ruleId).containsExactlyInAnyOrder("r1", "r2");
    }

    @Test
    void usageOfSourceMemberInSpecificRuleOnly() {
        IlimapAnalysis analysis = analyze(twoRulesSameSourceMapping());

        List<IlimapTraceUsage> usages = traceService.usagesOfSourceMember(analysis, "s", "X", "r1");

        assertThat(usages).singleElement();
        assertThat(usages.get(0).ruleId()).isEqualTo("r1");
    }

    private IlimapAnalysis analyze(String source) {
        return analysisService.analyze("file:///test.ilimap", source, OPTIONS);
    }

    private static String copyAssignmentMapping() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      X = s.X;
                    }
                  }
                }
                """;
    }

    private static String computedMapping() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      Z = s.First + s.Last;
                    }
                  }
                }
                """;
    }

    private static String enumMapMapping() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      Quality = enumMap(Quality);
                    }
                  }
                }
                """;
    }

    private static String bagMapping() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    bag Outer {
                      from b in src class "M.B";
                      assign {
                        O = b.X;
                      }
                    }
                  }
                }
                """;
    }

    private static String refMapping() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    ref Parent {
                      association "M.ParentAssoc";
                      required;
                      target rule r2 sourceRef s.ParentId;
                    }
                  }
                  rule r2 {
                    target out class "M.B";
                    source s from src class "M.B";
                  }
                }
                """;
    }

    private static String twoRulesSameSourceMapping() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      X = s.X;
                    }
                  }
                  rule r2 {
                    target out class "M.B";
                    source s from src class "M.B";
                    assign {
                      Y = s.X;
                    }
                  }
                }
                """;
    }
}
