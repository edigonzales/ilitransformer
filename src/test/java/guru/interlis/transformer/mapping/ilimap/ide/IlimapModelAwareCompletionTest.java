package guru.interlis.transformer.mapping.ilimap.ide;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class IlimapModelAwareCompletionTest {

    private static final IlimapAnalysisOptions MODEL_AWARE_OPTIONS = IlimapAnalysisOptions.modelAware(Path.of("."));

    private final IlimapAnalysisService analysisService = new IlimapAnalysisService();
    private final IlimapCompletionService completionService = new IlimapCompletionService();

    @Test
    void completesTargetClasses() {
        List<IlimapCompletionItem> items = complete(mappingWithTargetClass("TestModel.Test"), "TestModel.Test");

        assertThat(items).anySatisfy(item -> {
            assertThat(item.label()).isEqualTo("TestModel.TestTopic.TestClass");
            assertThat(item.kind()).isEqualTo(IlimapCompletionKind.CLASS);
            assertThat(item.replacementRange()).isNotNull();
        });
    }

    @Test
    void completesSourceClasses() {
        List<IlimapCompletionItem> items = complete(mappingWithSourceClass("TestModel.Test"), "TestModel.Test");

        assertThat(items).extracting(IlimapCompletionItem::label).contains("TestModel.TestTopic.TestClass");
    }

    @Test
    void completesTargetAttributesInAssign() {
        List<IlimapCompletionItem> items = complete(validMapping(), "Name = s.Name", "Na".length());

        assertThat(items).anySatisfy(item -> {
            assertThat(item.label()).isEqualTo("Name");
            assertThat(item.kind()).isEqualTo(IlimapCompletionKind.ATTRIBUTE);
            assertThat(item.detail()).contains("TEXT");
        });
    }

    @Test
    void completesSourceAliasAttributes() {
        List<IlimapCompletionItem> items = complete(mappingWithExpression("Beschreibung = s.;"), "s.", "s.".length());

        assertThat(items).extracting(IlimapCompletionItem::label).contains("Name", "Beschreibung", "Anzahl", "Aktiv");
        assertThat(items).allSatisfy(item -> assertThat(item.replacementRange()).isNotNull());
    }

    private List<IlimapCompletionItem> complete(String source, String needle) {
        return complete(source, needle, needle.length());
    }

    private List<IlimapCompletionItem> complete(String source, String needle, int cursorDelta) {
        IlimapAnalysis analysis = analysisService.analyze("file:///test.ilimap", source, MODEL_AWARE_OPTIONS);
        int offset = source.indexOf(needle);
        assertThat(offset).as("needle offset for %s", needle).isGreaterThanOrEqualTo(0);
        return completionService.complete(analysis, analysis.lineMap().toIdePosition(offset + cursorDelta));
    }

    private static String validMapping() {
        return mappingWithExpression("Name = s.Name;");
    }

    private static String mappingWithExpression(String expressionLine) {
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
                      %s
                    }
                  }
                }
                """.formatted(expressionLine);
    }

    private static String mappingWithTargetClass(String targetClass) {
        return validMapping()
                .replace(
                        "target out class \"TestModel.TestTopic.TestClass\"",
                        "target out class \"" + targetClass + "\"");
    }

    private static String mappingWithSourceClass(String sourceClass) {
        return validMapping()
                .replace(
                        "source s from src class \"TestModel.TestTopic.TestClass\"",
                        "source s from src class \"" + sourceClass + "\"");
    }
}
