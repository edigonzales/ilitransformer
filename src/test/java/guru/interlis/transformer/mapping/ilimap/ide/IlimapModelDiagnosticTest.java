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
    void reportsInvalidModelWithoutCrash() {
        IlimapAnalysis analysis = analyze(validMapping().replace("model \"TestModel\"", "model \"MissingModel\""));

        assertThat(analysis.diagnostics())
                .anySatisfy(diagnostic -> assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.MODEL_COMPILE_FAILED));
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
}
