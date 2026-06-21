package guru.interlis.transformer.mapping.ilimap.ide;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class IlimapCompletionContextResolverTest {

    private static final IlimapAnalysisOptions OPTIONS = IlimapAnalysisOptions.defaults(Path.of("."));

    private final IlimapAnalysisService analysisService = new IlimapAnalysisService();
    private final IlimapCompletionContextResolver resolver = new IlimapCompletionContextResolver();

    @Test
    void resolvesTopLevelContext() {
        IlimapCompletionContext context = resolve(validMapping(), "input src", "in".length());

        assertThat(context.kind()).isEqualTo(IlimapCompletionContextKind.TOP_LEVEL);
        assertThat(context.prefix()).isEqualTo("in");
        assertThat(context.currentRule()).isNull();
    }

    @Test
    void resolvesRuleBlockContext() {
        IlimapCompletionContext context = resolve(validMapping(), "source s from", "sou".length());

        assertThat(context.kind()).isEqualTo(IlimapCompletionContextKind.RULE_BLOCK);
        assertThat(context.prefix()).isEqualTo("sou");
        assertThat(context.currentRule().id()).isEqualTo("r1");
    }

    @Test
    void resolvesTargetOutputContext() {
        IlimapCompletionContext context = resolve(validMapping(), "target out class", "target ou".length());

        assertThat(context.kind()).isEqualTo(IlimapCompletionContextKind.TARGET_OUTPUT);
        assertThat(context.prefix()).isEqualTo("ou");
        assertThat(context.currentRule().id()).isEqualTo("r1");
    }

    @Test
    void resolvesSourceInputContext() {
        IlimapCompletionContext context =
                resolve(validMapping(), "source s from src class", "source s from sr".length());

        assertThat(context.kind()).isEqualTo(IlimapCompletionContextKind.SOURCE_INPUT);
        assertThat(context.prefix()).isEqualTo("sr");
        assertThat(context.currentRule().id()).isEqualTo("r1");
    }

    @Test
    void resolvesRefTargetRuleContext() {
        IlimapCompletionContext context = resolve(validMapping(), "target rule r1 sourceRef", "target rule r".length());

        assertThat(context.kind()).isEqualTo(IlimapCompletionContextKind.REF_TARGET_RULE);
        assertThat(context.prefix()).isEqualTo("r");
        assertThat(context.currentRule().id()).isEqualTo("r1");
    }

    @Test
    void resolvesEnumMapSecondArgumentContext() {
        IlimapCompletionContext context =
                resolve(validMapping(), "enumMap(s.X, Quality)", "enumMap(s.X, Qual".length());

        assertThat(context.kind()).isEqualTo(IlimapCompletionContextKind.ENUM_MAP_ARGUMENT);
        assertThat(context.prefix()).isEqualTo("Qual");
        assertThat(context.currentRule().id()).isEqualTo("r1");
    }

    @Test
    void resolvesNonEnumMapExpressionContext() {
        IlimapCompletionContext context =
                resolve(validMapping(), "coalesce(s.Y, Quality)", "coalesce(s.Y, Qual".length());

        assertThat(context.kind()).isEqualTo(IlimapCompletionContextKind.EXPRESSION);
        assertThat(context.prefix()).isEqualTo("Qual");
    }

    private IlimapCompletionContext resolve(String source, String needle, int cursorDelta) {
        IlimapAnalysis analysis = analysisService.analyze("file:///test.ilimap", source, OPTIONS);
        int offset = source.indexOf(needle);
        assertThat(offset).as("needle offset for %s", needle).isGreaterThanOrEqualTo(0);
        return resolver.resolve(analysis, analysis.lineMap().toIdePosition(offset + cursorDelta));
    }

    private static String validMapping() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  enum Quality {
                    "old" -> "new";
                  }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      X = enumMap(s.X, Quality);
                      Y = coalesce(s.Y, Quality);
                    }
                    ref Parent {
                      target rule r1 sourceRef s.Parent;
                    }
                  }
                }
                """;
    }
}
