package guru.interlis.transformer.mapping.ilimap.ide;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class IlimapDefinitionServiceTest {

    private static final IlimapAnalysisOptions OPTIONS = IlimapAnalysisOptions.defaults(Path.of("."));
    private static final String URI = "file:///test.ilimap";

    private final IlimapAnalysisService analysisService = new IlimapAnalysisService();
    private final IlimapDefinitionService definitionService = new IlimapDefinitionService();

    @Test
    void findsDefinitionOfInputId() {
        IlimapAnalysis analysis = analyze(validMapping());

        Optional<IlimapDefinition> definition =
                definitionService.definitionAt(analysis, positionAt(analysis, "from src class", "from sr".length()));

        assertThat(definition).isPresent();
        assertThat(definition.get().uri()).isEqualTo(URI);
        assertThat(definition.get().label()).isEqualTo("input src");
        assertThat(textAt(analysis, definition.get().range())).isEqualTo("src");
    }

    @Test
    void findsDefinitionOfOutputId() {
        IlimapAnalysis analysis = analyze(validMapping());

        Optional<IlimapDefinition> definition = definitionService.definitionAt(
                analysis, positionAt(analysis, "target out class", "target ou".length()));

        assertThat(definition).isPresent();
        assertThat(definition.get().label()).isEqualTo("output out");
        assertThat(textAt(analysis, definition.get().range())).isEqualTo("out");
    }

    @Test
    void findsDefinitionOfTargetRule() {
        IlimapAnalysis analysis = analyze(validMapping());

        Optional<IlimapDefinition> definition = definitionService.definitionAt(
                analysis, positionAt(analysis, "target rule r1", "target rule r".length()));

        assertThat(definition).isPresent();
        assertThat(definition.get().label()).isEqualTo("rule r1");
        assertThat(textAt(analysis, definition.get().range())).isEqualTo("r1");
    }

    @Test
    void findsDefinitionOfEnumMapSymbol() {
        IlimapAnalysis analysis = analyze(validMapping());

        Optional<IlimapDefinition> definition = definitionService.definitionAt(
                analysis, positionAt(analysis, "enumMap(s.X, Quality)", "enumMap(s.X, Qual".length()));

        assertThat(definition).isPresent();
        assertThat(definition.get().label()).isEqualTo("enum Quality");
        assertThat(textAt(analysis, definition.get().range())).isEqualTo("Quality");
    }

    @Test
    void findsDefinitionWhenHoveringDeclarationIdentifier() {
        IlimapAnalysis analysis = analyze(validMapping());

        Optional<IlimapDefinition> definition =
                definitionService.definitionAt(analysis, positionAt(analysis, "input src", "input s".length()));

        assertThat(definition).isPresent();
        assertThat(definition.get().label()).isEqualTo("input src");
        assertThat(textAt(analysis, definition.get().range())).isEqualTo("src");
    }

    @Test
    void returnsEmptyForUnknownSymbol() {
        String source = validMapping().replace("enumMap(s.X, Quality)", "coalesce(s.X, Quality)");
        IlimapAnalysis analysis = analyze(source);

        Optional<IlimapDefinition> definition = definitionService.definitionAt(
                analysis, positionAt(analysis, "coalesce(s.X, Quality)", "coalesce(s.X, Qual".length()));

        assertThat(definition).isEmpty();
    }

    private IlimapAnalysis analyze(String source) {
        return analysisService.analyze(URI, source, OPTIONS);
    }

    private static IlimapIdePosition positionAt(IlimapAnalysis analysis, String needle, int cursorDelta) {
        int offset = analysis.text().indexOf(needle);
        assertThat(offset).as("needle offset for %s", needle).isGreaterThanOrEqualTo(0);
        return analysis.lineMap().toIdePosition(offset + cursorDelta);
    }

    private static String textAt(IlimapAnalysis analysis, IlimapIdeRange range) {
        int start = analysis.lineMap()
                .positionToOffset(range.start().line(), range.start().character());
        int end = analysis.lineMap()
                .positionToOffset(range.end().line(), range.end().character());
        return analysis.text().substring(start, end);
    }

    private static String validMapping() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; format xtf; }
                  output out { path "out.xtf"; model "M"; format xtf; }
                  enum Quality {
                    "old" -> "new";
                    "bad" -> "good";
                  }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      X = enumMap(s.X, Quality);
                    }
                    ref Parent {
                      target rule r1 sourceRef s.Parent;
                    }
                  }
                }
                """;
    }
}
