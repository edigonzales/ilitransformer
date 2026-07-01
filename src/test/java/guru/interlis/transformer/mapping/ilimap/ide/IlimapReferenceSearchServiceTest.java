package guru.interlis.transformer.mapping.ilimap.ide;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleBlock;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class IlimapReferenceSearchServiceTest {

    private static final IlimapAnalysisOptions OPTIONS = IlimapAnalysisOptions.defaults(Path.of("."));
    private static final String URI = "file:///test.ilimap";

    private final IlimapAnalysisService analysisService = new IlimapAnalysisService();
    private final IlimapReferenceSearchService searchService = new IlimapReferenceSearchService();

    @Test
    void inputReferences_findsDeclarationAndSourceReferences() {
        IlimapAnalysis analysis = analyze(validMapping());

        List<IlimapIdeRange> refs = searchService.inputReferences(analysis, "src");

        assertThat(refs).hasSize(2);
        assertThat(textAt(analysis, refs.get(0))).isEqualTo("src");
        assertThat(textAt(analysis, refs.get(1))).isEqualTo("src");
    }

    @Test
    void inputReferences_findsDeclarationAndSourceReferencesForSecondInput() {
        IlimapAnalysis analysis = analyze(validMapping());

        List<IlimapIdeRange> refs = searchService.inputReferences(analysis, "other");

        assertThat(refs).hasSize(2);
        assertThat(textAt(analysis, refs.get(0))).isEqualTo("other");
        assertThat(textAt(analysis, refs.get(1))).isEqualTo("other");
    }

    @Test
    void outputReferences_findsDeclarationAndTargetReferences() {
        IlimapAnalysis analysis = analyze(validMapping());

        List<IlimapIdeRange> refs = searchService.outputReferences(analysis, "out");

        assertThat(refs).hasSize(3);
        assertThat(textAt(analysis, refs.get(0))).isEqualTo("out");
        assertThat(textAt(analysis, refs.get(1))).isEqualTo("out");
        assertThat(textAt(analysis, refs.get(2))).isEqualTo("out");
    }

    @Test
    void ruleReferences_findsDeclarationAndTargetRuleRefs() {
        IlimapAnalysis analysis = analyze(validMapping());

        List<IlimapIdeRange> refs = searchService.ruleReferences(analysis, "r1");

        assertThat(refs).hasSize(2);
        assertThat(textAt(analysis, refs.get(0))).isEqualTo("r1");
        assertThat(textAt(analysis, refs.get(1))).isEqualTo("r1");
    }

    @Test
    void enumMapReferences_findsDeclarationAndExpressionRefs() {
        IlimapAnalysis analysis = analyze(validMapping());

        List<IlimapIdeRange> refs = searchService.enumMapReferences(analysis, "Quality");

        assertThat(refs).hasSize(2);
        assertThat(textAt(analysis, refs.get(0))).isEqualTo("Quality");
        assertThat(textAt(analysis, refs.get(1))).isEqualTo("Quality");
    }

    @Test
    void sourceAliasReferences_findsDeclarationAndExpressionRefsInRule() {
        IlimapAnalysis analysis = analyze(validMapping());
        IlimapRuleBlock rule = analysis.document().rules().get(0);

        List<IlimapIdeRange> refs = searchService.sourceAliasReferences(analysis, rule, "s");

        assertThat(refs).hasSize(3);
        assertThat(textAt(analysis, refs.get(0))).isEqualTo("s");
        assertThat(textAt(analysis, refs.get(1))).isEqualTo("s");
        assertThat(textAt(analysis, refs.get(2))).isEqualTo("s");
    }

    @Test
    void sourceAliasReferences_isolatedByRule() {
        IlimapAnalysis analysis = analyze(validMapping());
        IlimapRuleBlock rule = analysis.document().rules().get(1);

        List<IlimapIdeRange> refs = searchService.sourceAliasReferences(analysis, rule, "s");

        assertThat(refs).hasSize(2);
    }

    @Test
    void unknownInputReturnsEmpty() {
        IlimapAnalysis analysis = analyze(validMapping());

        List<IlimapIdeRange> refs = searchService.inputReferences(analysis, "nonexistent");

        assertThat(refs).isEmpty();
    }

    @Test
    void unknownOutputReturnsEmpty() {
        IlimapAnalysis analysis = analyze(validMapping());

        List<IlimapIdeRange> refs = searchService.outputReferences(analysis, "nonexistent");

        assertThat(refs).isEmpty();
    }

    @Test
    void references_resolvesInputSymbol() {
        IlimapAnalysis analysis = analyze(validMapping());
        IlimapIdePosition pos = positionAt(analysis, "input src", "input s".length());
        IlimapResolvedSymbol resolved = new IlimapSymbolReferenceResolver().resolve(analysis, pos).orElseThrow();

        List<IlimapIdeRange> refs = searchService.references(analysis, resolved);

        assertThat(refs).hasSize(2);
    }

    @Test
    void references_resolvesSourceAliasSymbol() {
        IlimapAnalysis analysis = analyze(validMapping());
        IlimapIdePosition pos = positionAt(analysis, "sourceRef s.Parent", "sourceRef s".length());
        IlimapResolvedSymbol resolved = new IlimapSymbolReferenceResolver().resolve(analysis, pos).orElseThrow();

        List<IlimapIdeRange> refs = searchService.references(analysis, resolved);

        assertThat(refs).hasSize(3);
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
        int start = analysis.lineMap().positionToOffset(range.start().line(), range.start().character());
        int end = analysis.lineMap().positionToOffset(range.end().line(), range.end().character());
        return analysis.text().substring(start, end);
    }

    private static String validMapping() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; format xtf; }
                  input other { path "in2.xtf"; model "M"; format xtf; }
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
                  rule r2 {
                    target out class "M.B";
                    source s from other class "M.B";
                    assign {
                      Y = s.Y;
                    }
                  }
                }
                """;
    }
}
