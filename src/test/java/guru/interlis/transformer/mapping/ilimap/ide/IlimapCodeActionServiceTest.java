package guru.interlis.transformer.mapping.ilimap.ide;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.diag.DiagnosticCode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

class IlimapCodeActionServiceTest {

    private static final String URI = "file:///test.ilimap";
    private static final IlimapAnalysisOptions OPTIONS = IlimapAnalysisOptions.defaults(Path.of("."));

    private final IlimapAnalysisService analysisService = new IlimapAnalysisService();
    private final IlimapCodeActionService codeActionService = new IlimapCodeActionService();

    @Test
    void offersEnumMapStringToSymbolQuickFix() {
        IlimapAnalysis analysis = analyze(mappingWithExpression("enumMap(s.X, \"Quality\")", true));

        IlimapCodeAction action = actionByTitle(actionsAt(analysis, "\"Quality\"", 1, "Quality".length() + 1), "Use symbolic enum map reference");

        assertThat(action.kind()).isEqualTo(IlimapCodeActionService.QUICK_FIX);
        assertThat(action.diagnosticCode()).isEqualTo(DiagnosticCode.ILIMAP_ENUM_MAP_STRING_REF);
        assertThat(action.edits()).hasSize(2);
        assertThat(action.edits()).allSatisfy(edit -> {
            assertThat(textAt(analysis, edit.range())).isEqualTo("\"");
            assertThat(edit.newText()).isEmpty();
        });
        assertThat(applyEdits(analysis, action.edits())).contains("enumMap(s.X, Quality)");
    }

    @Test
    void doesNotOfferQuickFixForUnknownStringEnumMap() {
        IlimapAnalysis analysis = analyze(mappingWithExpression("enumMap(s.X, \"Ghost\")", true));

        assertThat(actionsAt(analysis, "\"Ghost\"", 1, "Ghost".length() + 1))
                .extracting(IlimapCodeAction::title)
                .doesNotContain("Use symbolic enum map reference");
    }

    @Test
    void createsMissingEnumMapBlockAfterLastEnumBlock() {
        IlimapAnalysis analysis = analyze(mappingWithExpression("enumMap(s.X, MissingMap)", true));

        IlimapCodeAction action = actionByTitle(
                actionsAt(analysis, "MissingMap", 0, "MissingMap".length()), "Create enum map 'MissingMap'");

        assertThat(action.kind()).isEqualTo(IlimapCodeActionService.QUICK_FIX);
        assertThat(action.diagnosticCode()).isEqualTo(DiagnosticCode.ILIMAP_UNKNOWN_ENUM_MAP);
        assertThat(action.edits()).singleElement().satisfies(edit -> {
            assertThat(textAt(analysis, edit.range())).isEmpty();
            assertThat(edit.newText()).isEqualTo("  enum MissingMap {\n  }\n");
        });
        String edited = applyEdits(analysis, action.edits());
        assertThat(edited.indexOf("  enum Quality")).isLessThan(edited.indexOf("  enum MissingMap"));
        assertThat(edited.indexOf("  enum MissingMap")).isLessThan(edited.indexOf("  rule r1"));
    }

    @Test
    void createsMissingEnumMapBlockBeforeFirstRuleWhenNoEnumExists() {
        IlimapAnalysis analysis = analyze(mappingWithExpression("enumMap(s.X, MissingMap)", false));

        IlimapCodeAction action = actionByTitle(
                actionsAt(analysis, "MissingMap", 0, "MissingMap".length()), "Create enum map 'MissingMap'");

        assertThat(action.edits()).singleElement().satisfies(edit -> assertThat(edit.newText())
                .isEqualTo("  enum MissingMap {\n  }\n\n"));
        String edited = applyEdits(analysis, action.edits());
        assertThat(edited.indexOf("  enum MissingMap")).isLessThan(edited.indexOf("  rule r1"));
    }

    @Test
    void doesNotCreateInvalidEnumMapId() {
        IlimapAnalysis analysis = analyze(mappingWithExpression("enumMap(s.X, s.MissingMap)", false));

        assertThat(actionsAt(analysis, "s.MissingMap", 0, "s.MissingMap".length()))
                .extracting(IlimapCodeAction::title)
                .doesNotContain("Create enum map 's.MissingMap'");
    }

    @Test
    void formatActionReturnsFormatterEditForValidDocument() {
        String source = compactMapping();
        IlimapAnalysis analysis = analyze(source);

        IlimapCodeAction action = actionByTitle(
                codeActionService.codeActions(analysis, range(analysis, 0, 0)), "Format ILIMAP document");

        assertThat(action.kind()).isEqualTo(IlimapCodeActionService.SOURCE);
        assertThat(action.diagnosticCode()).isNull();
        assertThat(action.edits()).singleElement().satisfies(edit -> {
            assertThat(edit.range())
                    .isEqualTo(new IlimapIdeRange(new IlimapIdePosition(0, 0), analysis.lineMap().toIdePosition(source.length())));
            assertThat(edit.newText()).contains("input src {");
            assertThat(edit.newText()).endsWith("\n");
        });
    }

    private IlimapAnalysis analyze(String source) {
        return analysisService.analyze(URI, source, OPTIONS);
    }

    private List<IlimapCodeAction> actionsAt(IlimapAnalysis analysis, String needle, int startDelta, int endDelta) {
        int offset = analysis.text().indexOf(needle);
        assertThat(offset).as("needle offset for %s", needle).isGreaterThanOrEqualTo(0);
        return codeActionService.codeActions(analysis, range(analysis, offset + startDelta, offset + endDelta));
    }

    private IlimapIdeRange range(IlimapAnalysis analysis, int startOffset, int endOffset) {
        return new IlimapIdeRange(analysis.lineMap().toIdePosition(startOffset), analysis.lineMap().toIdePosition(endOffset));
    }

    private IlimapCodeAction actionByTitle(List<IlimapCodeAction> actions, String title) {
        return actions.stream()
                .filter(action -> action.title().equals(title))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing action: " + title));
    }

    private static String textAt(IlimapAnalysis analysis, IlimapIdeRange range) {
        int start = analysis.lineMap().positionToOffset(range.start().line(), range.start().character());
        int end = analysis.lineMap().positionToOffset(range.end().line(), range.end().character());
        return analysis.text().substring(start, end);
    }

    private static String applyEdits(IlimapAnalysis analysis, List<IlimapTextEdit> edits) {
        StringBuilder result = new StringBuilder(analysis.text());
        List<IlimapTextEdit> sorted = new ArrayList<>(edits);
        sorted.sort(Comparator.comparingInt((IlimapTextEdit edit) -> startOffset(analysis, edit)).reversed());
        for (IlimapTextEdit edit : sorted) {
            result.replace(startOffset(analysis, edit), endOffset(analysis, edit), edit.newText());
        }
        return result.toString();
    }

    private static int startOffset(IlimapAnalysis analysis, IlimapTextEdit edit) {
        return analysis.lineMap().positionToOffset(edit.range().start().line(), edit.range().start().character());
    }

    private static int endOffset(IlimapAnalysis analysis, IlimapTextEdit edit) {
        return analysis.lineMap().positionToOffset(edit.range().end().line(), edit.range().end().character());
    }

    private static String mappingWithExpression(String expression, boolean includeEnum) {
        String enumBlock = includeEnum
                ? """
                  enum Quality {
                    "old" => "new";
                  }

                """
                : "";
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                """
                + enumBlock
                + """
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      X = %s;
                    }
                  }
                }
                """
                        .formatted(expression);
    }

    private static String compactMapping() {
        return "mapping v2 { input src { path \"in.xtf\"; model \"M\"; } output out { path \"out.xtf\"; model \"M\"; } rule r1 { target out class \"M.A\"; source s from src class \"M.A\"; } }";
    }
}
