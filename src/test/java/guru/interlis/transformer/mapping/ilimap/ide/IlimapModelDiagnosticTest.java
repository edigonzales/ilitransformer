package guru.interlis.transformer.mapping.ilimap.ide;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.diag.DiagnosticCode;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class IlimapModelDiagnosticTest {

    private static final IlimapAnalysisOptions MODEL_AWARE_OPTIONS = IlimapAnalysisOptions.modelAware(Path.of("."));

    private final IlimapAnalysisService analysisService = new IlimapAnalysisService();

    @Test
    void reportsUnknownTargetClass() {
        IlimapAnalysis analysis = analyze(validMapping()
                .replace(
                        "target out class \"TestModel.TestTopic.TestClass\"",
                        "target out class \"TestModel.TestTopic.MissingClass\""));

        assertThat(analysis.diagnostics()).anySatisfy(diagnostic -> assertThat(diagnostic.code())
                .isEqualTo(DiagnosticCode.MAP_UNKNOWN_TARGET_CLASS));
    }

    @Test
    void reportsUnknownSourceClass() {
        IlimapAnalysis analysis = analyze(validMapping()
                .replace(
                        "source s from src class \"TestModel.TestTopic.TestClass\"",
                        "source s from src class \"TestModel.TestTopic.MissingClass\""));

        assertThat(analysis.diagnostics()).anySatisfy(diagnostic -> assertThat(diagnostic.code())
                .isEqualTo(DiagnosticCode.MAP_UNKNOWN_SOURCE_CLASS));
    }

    @Test
    void reportsUnknownTargetAttribute() {
        IlimapAnalysis analysis = analyze(validMapping().replace("Name = s.Name;", "MissingAttr = s.Name;"));

        assertThat(analysis.diagnostics()).anySatisfy(diagnostic -> assertThat(diagnostic.code())
                .isEqualTo(DiagnosticCode.MAP_UNKNOWN_TARGET_ATTRIBUTE));
    }

    @Test
    void reportsUnknownSourceAttribute() {
        IlimapAnalysis analysis = analyze(validMapping().replace("Name = s.Name;", "Name = s.MissingAttr;"));

        assertThat(analysis.diagnostics()).anySatisfy(diagnostic -> assertThat(diagnostic.code())
                .isEqualTo(DiagnosticCode.MAP_UNKNOWN_SOURCE_ATTRIBUTE));
    }

    @Test
    void acceptsSourceRolesInSourceRefJoinAndExpressions() {
        IlimapAnalysis analysis = analyze(associationRoleMapping());

        assertThat(analysis.diagnostics()).noneSatisfy(diagnostic -> assertThat(diagnostic.code())
                .isEqualTo(DiagnosticCode.MAP_UNKNOWN_SOURCE_ATTRIBUTE));
    }

    @Test
    void acceptsIli1ReferenceRolesAsSourceMembers() {
        IlimapAnalysis analysis = analyze(ili1ReferenceRoleMapping());

        assertThat(analysis.diagnostics()).noneSatisfy(diagnostic -> assertThat(diagnostic.code())
                .isEqualTo(DiagnosticCode.MAP_UNKNOWN_SOURCE_ATTRIBUTE));
    }

    @Test
    void reportsInvalidModelWithoutCrash() {
        IlimapAnalysis analysis = analyze(validMapping().replace("model \"TestModel\"", "model \"MissingModel\""));

        assertThat(analysis.diagnostics())
                .anySatisfy(diagnostic -> assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.MODEL_COMPILE_FAILED));
    }

    @Test
    void reportsMissingMandatoryTargetAttribute() {
        IlimapAnalysis analysis = analyze(missingMandatoryMapping());

        assertThat(analysis.diagnostics()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.MAP_MANDATORY_MISSING);
            assertThat(diagnostic.message()).contains("Name");
        });
    }

    @Test
    void noMandatoryDiagnosticWhenAttributeSetInDefaults() {
        IlimapAnalysis analysis = analyze(defaultsMapping());

        assertThat(analysis.diagnostics())
                .filteredOn(d -> d.code().equals(DiagnosticCode.MAP_MANDATORY_MISSING))
                .isEmpty();
    }

    private IlimapAnalysis analyze(String source) {
        return analysisService.analyze("file:///test.ilimap", source, MODEL_AWARE_OPTIONS);
    }

    private static String validMapping() {
        return """
                mapping v2 {
                  job {
                    modeldir "src/test/data/models/";
                  }
                  input src { path "in.xtf"; model "TestModel"; }
                  output out { path "out.xtf"; model "TestModel"; }
                  rule r1 {
                    target out class "TestModel.TestTopic.TestClass";
                    source s from src class "TestModel.TestTopic.TestClass";
                    assign {
                      Name = s.Name;
                    }
                  }
                }
                """;
    }

    private static String associationRoleMapping() {
        return """
                mapping v2 {
                  job {
                    modeldir "src/test/data/models/";
                  }
                  input src { path "in.xtf"; model "AssocModel"; }
                  output out { path "out.xtf"; model "AssocModel"; }
                  rule rParent {
                    target out class "AssocModel.AssocTopic.Parent";
                    source p from src class "AssocModel.AssocTopic.Parent";
                    assign {
                      Name = p.Name;
                    }
                  }
                  rule rChild {
                    target out class "AssocModel.AssocTopic.Child";
                    source c from src class "AssocModel.AssocTopic.Child" where defined(c.ChildRole);
                    source p from src class "AssocModel.AssocTopic.Parent";
                    join inner c to p on eq(c.ChildRole, p);
                    assign {
                      Name = c.ChildRole;
                    }
                    ref ParentRole {
                      target rule rParent sourceRef c.ChildRole;
                    }
                  }
                }
                """;
    }

    private static String ili1ReferenceRoleMapping() {
        return """
                mapping v2 {
                  job {
                    modeldir "src/test/data/av/models/";
                  }
                  input src { path "in.itf"; model "DM01AVCH24LV95D"; }
                  output out { path "out.itf"; model "DM01AVCH24LV95D"; }
                  rule rNf {
                    target out class "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3Nachfuehrung";
                    source nf from src class "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3Nachfuehrung";
                    assign {}
                  }
                  rule rLfp3 {
                    target out class "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3";
                    source p from src class "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3";
                    assign {}
                    ref Entstehung {
                      target rule rNf sourceRef p.Entstehung;
                    }
                  }
                }
                """;
    }

    private static String missingMandatoryMapping() {
        return """
                mapping v2 {
                  job {
                    modeldir "src/test/data/models/";
                  }
                  input src { path "in.xtf"; model "TestModel"; }
                  output out { path "out.xtf"; model "TestModel"; }
                  rule r1 {
                    target out class "TestModel.TestTopic.TestClass";
                    source s from src class "TestModel.TestTopic.TestClass";
                  }
                }
                """;
    }

    private static String defaultsMapping() {
        return """
                mapping v2 {
                  job {
                    modeldir "src/test/data/models/";
                  }
                  input src { path "in.xtf"; model "TestModel"; }
                  output out { path "out.xtf"; model "TestModel"; }
                  rule r1 {
                    target out class "TestModel.TestTopic.TestClass";
                    source s from src class "TestModel.TestTopic.TestClass";
                    assign {
                      Beschreibung = s.Beschreibung;
                    }
                    defaults {
                      Name = s.Name;
                    }
                  }
                }
                """;
    }
}
