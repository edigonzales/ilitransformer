package guru.interlis.transformer.mapping.ilimap.ide;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.diag.DiagnosticCode;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class IlimapModelIndexServiceTest {

    private static final IlimapAnalysisOptions MODEL_AWARE_OPTIONS = IlimapAnalysisOptions.modelAware(Path.of("."));

    private final IlimapAnalysisService analysisService = new IlimapAnalysisService();

    @Test
    void loadsClassesFromLocalModeldir() {
        IlimapAnalysis analysis = analysisService.analyze("file:///test.ilimap", validMapping(), MODEL_AWARE_OPTIONS);

        assertThat(analysis.diagnostics()).isEmpty();
        assertThat(analysis.modelIndex().models()).singleElement().satisfies(model -> {
            assertThat(model.name()).isEqualTo("TestModel");
            assertThat(model.classes())
                    .extracting(IlimapClassInfo::qualifiedName)
                    .contains("TestModel.TestTopic.TestClass");
        });
        assertThat(analysis.modelIndex().findClass("TestModel.TestTopic.TestClass"))
                .get()
                .satisfies(classInfo -> assertThat(classInfo.attributes())
                        .extracting(IlimapAttributeInfo::name)
                        .contains("Name", "Beschreibung", "Anzahl", "Aktiv"));
    }

    @Test
    void mapsRolesFromModelInventory() {
        IlimapAnalysis analysis =
                analysisService.analyze("file:///test.ilimap", associationMapping(), MODEL_AWARE_OPTIONS);

        assertThat(analysis.diagnostics()).isEmpty();
        assertThat(analysis.modelIndex().findClass("AssocModel.AssocTopic.Parent"))
                .get()
                .satisfies(classInfo -> assertThat(classInfo.roles())
                        .extracting(IlimapRoleInfo::name)
                        .contains("ParentRole"));
    }

    @Test
    void missingModeldirProducesDiagnosticWithoutCrash() {
        IlimapAnalysis analysis =
                analysisService.analyze("file:///test.ilimap", missingModeldirMapping(), MODEL_AWARE_OPTIONS);

        assertThat(analysis.diagnostics()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.MODEL_COMPILE_FAILED);
            assertThat(diagnostic.message()).contains("Model directory does not exist");
        });
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

    private static String missingModeldirMapping() {
        return validMapping().replace("src/test/data/models/", "src/test/data/missing-models/");
    }

    private static String associationMapping() {
        return """
                mapping v2 {
                  job {
                    modeldir "src/test/data/models/";
                  }
                  input src { path "in.xtf"; model "AssocModel"; }
                  output out { path "out.xtf"; model "AssocModel"; }
                  rule r1 {
                    target out class "AssocModel.AssocTopic.Parent";
                    source p from src class "AssocModel.AssocTopic.Parent";
                    assign {
                      Name = p.Name;
                    }
                  }
                }
                """;
    }
}
