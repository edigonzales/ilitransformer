package guru.interlis.transformer.mapping.ilimap.ide;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class IlimapRuleDetailServiceTest {

    private static final IlimapAnalysisOptions OPTIONS = IlimapAnalysisOptions.defaults(Path.of("."));

    private final IlimapAnalysisService analysisService = new IlimapAnalysisService();
    private final IlimapRuleDetailService ruleDetailService = new IlimapRuleDetailService();

    @Test
    void detailForRuleWithTargetSourceAndAssignments() {
        IlimapRuleDetailSummary detail = ruleDetailService.detail(analyze(validMapping()), "r1");

        assertThat(detail.available()).isTrue();
        assertThat(detail.ruleId()).isEqualTo("r1");
        assertThat(detail.nodeId()).isEqualTo("rule:r1");
        assertThat(detail.location()).isNotNull();
        assertThat(detail.location().line()).isGreaterThanOrEqualTo(0);

        assertThat(detail.target()).isNotNull();
        assertThat(detail.target().outputId()).isEqualTo("out");
        assertThat(detail.target().className()).isEqualTo("M.A");

        assertThat(detail.sources()).singleElement().satisfies(source -> {
            assertThat(source.alias()).isEqualTo("s");
            assertThat(source.inputIds()).containsExactly("src");
            assertThat(source.className()).isEqualTo("M.A");
        });

        assertThat(detail.assignments()).hasSize(1);
        assertThat(detail.assignments().get(0).targetAttribute()).isEqualTo("X");
        assertThat(detail.assignments().get(0).expression()).isEqualTo("s.X");
        assertThat(detail.assignments().get(0).kind()).isEqualTo("copy");
        assertThat(detail.assignments().get(0).location()).isNotNull();

        assertThat(detail.diagnostics()).isEmpty();
    }

    @Test
    void detailForRuleWithJoinsIdentityAndWhere() {
        IlimapRuleDetailSummary detail = ruleDetailService.detail(
                analyze(mappingWithJoinsIdentityWhere()), "r2");

        assertThat(detail.available()).isTrue();

        assertThat(detail.joins()).singleElement().satisfies(join -> {
            assertThat(join.type()).isEqualTo("left");
            assertThat(join.leftAlias()).isEqualTo("a");
            assertThat(join.rightAlias()).isEqualTo("b");
            assertThat(join.condition()).isEqualTo("eq(a.Key, b.Key)");
        });

        assertThat(detail.identity()).hasSize(2);
        assertThat(detail.identity().get(0).expression()).isEqualTo("a.Id");
        assertThat(detail.identity().get(1).expression()).isEqualTo("b.Id");

        assertThat(detail.sources()).hasSize(2);
        assertThat(detail.sources().get(0).where()).isEqualTo("a.Active");
    }

    @Test
    void detailForRuleWithEnumMapAssignment() {
        IlimapRuleDetailSummary detail = ruleDetailService.detail(
                analyze(mappingWithEnumMap()), "r3");

        assertThat(detail.available()).isTrue();
        assertThat(detail.assignments()).hasSize(1);
        assertThat(detail.assignments().get(0).targetAttribute()).isEqualTo("Quality");
        assertThat(detail.assignments().get(0).expression()).isEqualTo("enumMap(Quality)");
        assertThat(detail.assignments().get(0).kind()).isEqualTo("enumMap");
        assertThat(detail.assignments().get(0).dependencies())
                .anySatisfy(dep -> assertThat(dep.enumMapId()).isEqualTo("Quality"));
    }

    @Test
    void detailForRuleWithBags() {
        IlimapRuleDetailSummary detail = ruleDetailService.detail(
                analyze(mappingWithBags()), "r4");

        assertThat(detail.available()).isTrue();
        assertThat(detail.bags()).hasSize(1);
        IlimapBagSummary outer = detail.bags().get(0);
        assertThat(outer.name()).isEqualTo("Outer");
        assertThat(outer.source()).isNotNull();
        assertThat(outer.source().alias()).isEqualTo("o");
        assertThat(outer.assignments()).hasSize(1);
        assertThat(outer.assignments().get(0).targetAttribute()).isEqualTo("O");
        assertThat(outer.nestedBags()).hasSize(1);

        IlimapBagSummary inner = outer.nestedBags().get(0);
        assertThat(inner.name()).isEqualTo("Inner");
        assertThat(inner.assignments()).hasSize(1);
        assertThat(inner.assignments().get(0).targetAttribute()).isEqualTo("I");
    }

    @Test
    void detailForRuleWithRefs() {
        IlimapRuleDetailSummary detail = ruleDetailService.detail(
                analyze(mappingWithRef()), "r5");

        assertThat(detail.available()).isTrue();
        assertThat(detail.refs()).singleElement().satisfies(ref -> {
            assertThat(ref.name()).isEqualTo("Parent");
            assertThat(ref.required()).isTrue();
            assertThat(ref.association()).isEqualTo("M.ParentAssoc");
            assertThat(ref.role()).isEqualTo("ParentRole");
            assertThat(ref.targetRuleId()).isEqualTo("r1");
            assertThat(ref.sourceRef()).isEqualTo("s.ParentId");
        });
    }

    @Test
    void detailForRuleWithLosses() {
        IlimapRuleDetailSummary detail = ruleDetailService.detail(
                analyze(mappingWithLoss()), "r6");

        assertThat(detail.available()).isTrue();
        assertThat(detail.losses()).singleElement().satisfies(loss -> {
            assertThat(loss.sourcePath()).isEqualTo("s.Obsolete");
            assertThat(loss.reasonCode()).isEqualTo("NOT_MAPPED");
            assertThat(loss.description()).isEqualTo("Field is not available in target");
            assertThat(loss.when()).isEqualTo("defined(s.Obsolete)");
        });
    }

    @Test
    void detailForRuleWithMetadata() {
        IlimapRuleDetailSummary detail = ruleDetailService.detail(
                analyze(mappingWithMetadata()), "r7");

        assertThat(detail.available()).isTrue();
        assertThat(detail.metadata()).isNotNull();
        assertThat(detail.metadata().direction()).isEqualTo("dm01_to_dmav");
        assertThat(detail.metadata().roundtrip()).isEqualTo("none");
        assertThat(detail.metadata().lossiness()).isEqualTo("partial");
    }

    @Test
    void detailForRuleWithDefaults() {
        IlimapRuleDetailSummary detail = ruleDetailService.detail(
                analyze(mappingWithDefaults()), "r8");

        assertThat(detail.available()).isTrue();
        assertThat(detail.defaults()).hasSize(1);
        assertThat(detail.defaults().get(0).targetAttribute()).isEqualTo("Status");
        assertThat(detail.defaults().get(0).expression()).isEqualTo("\"active\"");
        assertThat(detail.defaults().get(0).kind()).isEqualTo("default");
    }

    @Test
    void detailUnknownRuleReturnsUnavailable() {
        IlimapRuleDetailSummary detail = ruleDetailService.detail(analyze(validMapping()), "nonexistent");

        assertThat(detail.available()).isFalse();
        assertThat(detail.message()).contains("not found");
        assertThat(detail.ruleId()).isEqualTo("nonexistent");
    }

    @Test
    void detailUnparseableMappingReturnsUnavailable() {
        IlimapAnalysis analysis = analyze(missingSemicolonMapping());

        IlimapRuleDetailSummary detail = ruleDetailService.detail(analysis, "r1");

        assertThat(detail.available()).isFalse();
        assertThat(detail.message()).contains("could not be parsed");
    }

    @Test
    void assignmentKindCopy() {
        assertThat(IlimapRuleDetailService.classifyAssignmentKind("p.Name")).isEqualTo("copy");
        assertThat(IlimapRuleDetailService.classifyAssignmentKind("s.Description")).isEqualTo("copy");
    }

    @Test
    void assignmentKindConstant() {
        assertThat(IlimapRuleDetailService.classifyAssignmentKind("\"literal\"")).isEqualTo("constant");
        assertThat(IlimapRuleDetailService.classifyAssignmentKind("42")).isEqualTo("constant");
        assertThat(IlimapRuleDetailService.classifyAssignmentKind("3.14")).isEqualTo("constant");
        assertThat(IlimapRuleDetailService.classifyAssignmentKind("true")).isEqualTo("constant");
        assertThat(IlimapRuleDetailService.classifyAssignmentKind("false")).isEqualTo("constant");
    }

    @Test
    void assignmentKindNull() {
        assertThat(IlimapRuleDetailService.classifyAssignmentKind("null")).isEqualTo("null");
    }

    @Test
    void assignmentKindEnumMap() {
        assertThat(IlimapRuleDetailService.classifyAssignmentKind("enumMap(Quality)")).isEqualTo("enumMap");
    }

    @Test
    void assignmentKindComputed() {
        assertThat(IlimapRuleDetailService.classifyAssignmentKind("s.X + 1")).isEqualTo("computed");
        assertThat(IlimapRuleDetailService.classifyAssignmentKind("s.First + \" \" + s.Last")).isEqualTo("computed");
    }

    @Test
    void assignmentKindUnknown() {
        assertThat(IlimapRuleDetailService.classifyAssignmentKind("")).isEqualTo("unknown");
        assertThat(IlimapRuleDetailService.classifyAssignmentKind(null)).isEqualTo("unknown");
        assertThat(IlimapRuleDetailService.classifyAssignmentKind("   ")).isEqualTo("unknown");
    }

    @Test
    void extractDependenciesForSourceAttributes() {
        List<IlimapExpressionDependencySummary> deps = IlimapRuleDetailService.extractDependencies("p.Name + s.Description");

        assertThat(deps).hasSize(2);
        assertThat(deps).anySatisfy(dep -> {
            assertThat(dep.kind()).isEqualTo("sourceAttribute");
            assertThat(dep.alias()).isEqualTo("p");
            assertThat(dep.member()).isEqualTo("Name");
        });
    }

    @Test
    void extractDependenciesForEnumMap() {
        List<IlimapExpressionDependencySummary> deps = IlimapRuleDetailService.extractDependencies("enumMap(Quality)");

        assertThat(deps).singleElement().satisfies(dep -> {
            assertThat(dep.kind()).isEqualTo("enumMap");
            assertThat(dep.enumMapId()).isEqualTo("Quality");
        });
    }

    @Test
    void locationsArePopulated() {
        IlimapRuleDetailSummary detail = ruleDetailService.detail(analyze(validMapping()), "r1");

        assertThat(detail.location()).isNotNull();
        assertThat(detail.target().location()).isNotNull();
        assertThat(detail.sources().get(0).location()).isNotNull();
        assertThat(detail.assignments().get(0).location()).isNotNull();
    }

    @Test
    void detailIncludesRuleDiagnostics() {
        IlimapAnalysis analysis = analyze(unknownInputMapping());

        IlimapRuleDetailSummary detail = ruleDetailService.detail(analysis, "r1");

        assertThat(detail.available()).isTrue();
        assertThat(detail.diagnostics()).isNotEmpty();
        assertThat(detail.diagnostics()).anySatisfy(diagnostic ->
                assertThat(diagnostic.severity()).isEqualTo("error"));
    }

    private IlimapAnalysis analyze(String source) {
        return analysisService.analyze("file:///test.ilimap", source, OPTIONS);
    }

    private static String validMapping() {
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

    private static String mappingWithJoinsIdentityWhere() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r2 {
                    target out class "M.A";
                    source a from src class "M.A" where a.Active;
                    source b from src class "M.B";
                    join left a to b on eq(a.Key, b.Key);
                    identity a.Id, b.Id;
                    assign {
                      Name = a.Name;
                    }
                  }
                }
                """;
    }

    private static String mappingWithEnumMap() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  enum Quality {
                    "old" -> "new";
                  }
                  rule r3 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      Quality = enumMap(Quality);
                    }
                  }
                }
                """;
    }

    private static String mappingWithBags() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r4 {
                    target out class "M.A";
                    source s from src class "M.A";
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
                  }
                }
                """;
    }

    private static String mappingWithRef() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r5 {
                    target out class "M.A";
                    source s from src class "M.A";
                    ref Parent {
                      association "M.ParentAssoc";
                      role "ParentRole";
                      required;
                      target rule r1 sourceRef s.ParentId;
                    }
                  }
                }
                """;
    }

    private static String mappingWithLoss() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r6 {
                    target out class "M.A";
                    source s from src class "M.A";
                    loss {
                      sourcePath s.Obsolete;
                      reasonCode "NOT_MAPPED";
                      description "Field is not available in target";
                      when defined(s.Obsolete);
                    }
                  }
                }
                """;
    }

    private static String mappingWithMetadata() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r7 {
                    target out class "M.A";
                    source s from src class "M.A";
                    metadata {
                      direction dm01_to_dmav;
                      roundtrip none;
                      lossiness partial;
                    }
                  }
                }
                """;
    }

    private static String mappingWithDefaults() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r8 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      Name = s.Name;
                    }
                    defaults {
                      Status = "active";
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
