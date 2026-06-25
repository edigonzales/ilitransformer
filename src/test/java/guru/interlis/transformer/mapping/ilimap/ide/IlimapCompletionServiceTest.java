package guru.interlis.transformer.mapping.ilimap.ide;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class IlimapCompletionServiceTest {

    private static final IlimapAnalysisOptions OPTIONS = IlimapAnalysisOptions.defaults(Path.of("."));

    private final IlimapAnalysisService analysisService = new IlimapAnalysisService();
    private final IlimapCompletionService completionService = new IlimapCompletionService();

    @Test
    void completesTopLevelKeywords() {
        List<IlimapCompletionItem> items = complete(validMapping(), "\n  input src", 1);

        assertThat(items)
                .extracting(IlimapCompletionItem::label)
                .contains("job", "input", "output", "oid", "basket", "enum", "defaults", "rule", "input block");
        assertThat(items)
                .filteredOn(item -> item.label().equals("input block"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.kind()).isEqualTo(IlimapCompletionKind.SNIPPET);
                    assertThat(item.snippet()).isTrue();
                    assertThat(item.insertText()).contains("input ${1:src}");
                });
    }

    @Test
    void completesRuleKeywords() {
        List<IlimapCompletionItem> items = complete(validMapping(), "\n    target out", 1);

        assertThat(items)
                .extracting(IlimapCompletionItem::label)
                .contains(
                        "target",
                        "source",
                        "where",
                        "join",
                        "identity",
                        "assign",
                        "defaults",
                        "bag",
                        "ref",
                        "create",
                        "loss",
                        "metadata",
                        "target statement",
                        "source statement",
                        "assign block");
    }

    @Test
    void completesJobKeywords() {
        List<IlimapCompletionItem> items = complete(mappingWithEmptyJob(), "job {\n  }", "job {\n  ".length());

        assertThat(items)
                .extracting(IlimapCompletionItem::label)
                .contains("name", "description", "direction", "failPolicy", "compileMode", "modeldir");
        assertThat(items)
                .filteredOn(item -> item.label().equals("modeldir"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.snippet()).isTrue();
                    assertThat(item.documentation()).contains("model");
                });
    }

    @Test
    void completesInputFields() {
        List<IlimapCompletionItem> items =
                complete(mappingWithEmptyInput(), "input src {\n  }", "input src {\n  ".length());

        assertThat(items)
                .extracting(IlimapCompletionItem::label)
                .contains("path", "model", "format", "option", "connection", "query");
        assertThat(items).allSatisfy(item -> assertThat(item.snippet()).isTrue());
    }

    @Test
    void completesFiniteFieldValues() {
        List<IlimapCompletionItem> formatItems =
                complete(mappingWithInputFormatPrefix(), "format x", "format x".length());
        List<IlimapCompletionItem> failPolicyItems =
                complete(mappingWithJobFailPolicyPrefix(), "failPolicy l", "failPolicy l".length());

        assertThat(formatItems).extracting(IlimapCompletionItem::label).containsExactly("xtf");
        assertThat(failPolicyItems).extracting(IlimapCompletionItem::label).containsExactly("lenient");
    }

    @Test
    void completesTopLevelSnippetInPartiallyInvalidDocument() {
        String source = """
                mapping v2 {
                  input
                """;

        IlimapAnalysis analysis = analysisService.analyze("file:///test.ilimap", source, OPTIONS);
        List<IlimapCompletionItem> items = completionService.complete(
                analysis, analysis.lineMap().toIdePosition(source.indexOf("input") + "input".length()));

        assertThat(items)
                .filteredOn(item -> item.label().equals("input block"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.snippet()).isTrue();
                    assertThat(item.replacementRange()).isNotNull();
                });
    }

    @Test
    void completesOutputIdsAfterTarget() {
        List<IlimapCompletionItem> items = complete(validMapping(), "target out class", "target ou".length());

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.label()).isEqualTo("out");
            assertThat(item.kind()).isEqualTo(IlimapCompletionKind.OUTPUT);
            assertThat(item.detail()).isEqualTo("output");
            assertThat(item.insertText()).isEqualTo("out");
        });
    }

    @Test
    void completesInputIdsAfterSourceFrom() {
        List<IlimapCompletionItem> items =
                complete(validMapping(), "source s from src class", "source s from sr".length());

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.label()).isEqualTo("src");
            assertThat(item.kind()).isEqualTo(IlimapCompletionKind.INPUT);
            assertThat(item.detail()).isEqualTo("input");
        });
    }

    @Test
    void completesRuleIdsInRefTargetRule() {
        List<IlimapCompletionItem> items =
                complete(validMapping(), "target rule r1 sourceRef", "target rule r".length());

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.label()).isEqualTo("r1");
            assertThat(item.kind()).isEqualTo(IlimapCompletionKind.RULE);
            assertThat(item.detail()).isEqualTo("rule");
        });
    }

    @Test
    void completesEnumMapsInEnumMapSecondArgument() {
        List<IlimapCompletionItem> items =
                complete(validMapping(), "enumMap(s.X, Quality)", "enumMap(s.X, Qual".length());

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.label()).isEqualTo("Quality");
            assertThat(item.kind()).isEqualTo(IlimapCompletionKind.ENUM_MAP);
            assertThat(item.detail()).isEqualTo("enum map");
        });
    }

    @Test
    void doesNotSuggestEnumMapsOutsideEnumMapContext() {
        List<IlimapCompletionItem> items =
                complete(validMapping(), "coalesce(s.Y, Quality)", "coalesce(s.Y, Qual".length());

        assertThat(items).isEmpty();
    }

    @Test
    void completesSourceAliasesInExpressionContext() {
        List<IlimapCompletionItem> items = complete(mappingWithExpression("X = s;"), "X = s", "X = s".length());

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.label()).isEqualTo("s");
            assertThat(item.kind()).isEqualTo(IlimapCompletionKind.SOURCE_ALIAS);
            assertThat(item.detail()).isEqualTo("source alias");
        });
    }

    @Test
    void showsModelUnavailableHintForUnknownAlias() {
        List<IlimapCompletionItem> items = complete(mappingWithExpression("X = q.;"), "q.", "q.".length());

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.label()).isEqualTo("Validate or save to load models");
            assertThat(item.kind()).isEqualTo(IlimapCompletionKind.VALUE);
            assertThat(item.documentation()).contains("INTERLIS models");
        });
    }

    private List<IlimapCompletionItem> complete(String source, String needle, int cursorDelta) {
        IlimapAnalysis analysis = analysisService.analyze("file:///test.ilimap", source, OPTIONS);
        int offset = source.indexOf(needle);
        assertThat(offset).as("needle offset for %s", needle).isGreaterThanOrEqualTo(0);
        return completionService.complete(analysis, analysis.lineMap().toIdePosition(offset + cursorDelta));
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

    private static String mappingWithEmptyJob() {
        return """
                mapping v2 {
                  job {
                  }
                  input src { path "in.xtf"; model "M"; }
                }
                """;
    }

    private static String mappingWithEmptyInput() {
        return """
                mapping v2 {
                  input src {
                  }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                }
                """;
    }

    private static String mappingWithInputFormatPrefix() {
        return """
                mapping v2 {
                  input src {
                    path "in.xtf";
                    model "M";
                    format x;
                  }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                }
                """;
    }

    private static String mappingWithJobFailPolicyPrefix() {
        return """
                mapping v2 {
                  job {
                    failPolicy l;
                  }
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                }
                """;
    }

    private static String mappingWithExpression(String expressionLine) {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      %s
                    }
                  }
                }
                """.formatted(expressionLine);
    }
}
