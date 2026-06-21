package guru.interlis.transformer.mapping.ilimap.ide;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class IlimapDocumentSymbolServiceTest {

    private static final IlimapAnalysisOptions OPTIONS = IlimapAnalysisOptions.defaults(Path.of("."));

    private final IlimapAnalysisService analysisService = new IlimapAnalysisService();
    private final IlimapDocumentSymbolService symbolService = new IlimapDocumentSymbolService();

    @Test
    void createsTopLevelSymbols() {
        var symbols = symbolService.symbols(analyze(validMapping()));

        assertThat(symbols).singleElement().satisfies(mapping -> {
            assertThat(mapping.name()).isEqualTo("mapping Profile");
            assertThat(mapping.kind()).isEqualTo(IlimapSymbolDisplayKind.MODULE);
            assertThat(mapping.children())
                    .extracting(IlimapDocumentSymbol::name)
                    .containsExactly("job", "input src", "output out", "enum Quality", "rule r1");
        });
    }

    @Test
    void createsRuleChildSymbols() {
        IlimapDocumentSymbol mapping =
                symbolService.symbols(analyze(validMapping())).get(0);
        IlimapDocumentSymbol rule = childNamed(mapping, "rule r1");

        assertThat(rule.kind()).isEqualTo(IlimapSymbolDisplayKind.METHOD);
        assertThat(rule.children())
                .extracting(IlimapDocumentSymbol::name)
                .containsExactly("source s", "assign", "bag Outer", "ref Parent");
    }

    @Test
    void createsBagAndRefSymbols() {
        IlimapDocumentSymbol mapping =
                symbolService.symbols(analyze(validMapping())).get(0);
        IlimapDocumentSymbol rule = childNamed(mapping, "rule r1");
        IlimapDocumentSymbol outerBag = childNamed(rule, "bag Outer");
        IlimapDocumentSymbol innerBag = childNamed(outerBag, "bag Inner");
        IlimapDocumentSymbol ref = childNamed(rule, "ref Parent");

        assertThat(outerBag.kind()).isEqualTo(IlimapSymbolDisplayKind.OBJECT);
        assertThat(outerBag.children()).extracting(IlimapDocumentSymbol::name).containsExactly("assign", "bag Inner");
        assertThat(innerBag.children()).extracting(IlimapDocumentSymbol::name).containsExactly("assign");
        assertThat(ref.kind()).isEqualTo(IlimapSymbolDisplayKind.OBJECT);
    }

    @Test
    void invalidDocumentReturnsEmptySymbols() {
        assertThat(symbolService.symbols(analyze(missingSemicolonMapping()))).isEmpty();
    }

    private IlimapAnalysis analyze(String source) {
        return analysisService.analyze("file:///test.ilimap", source, OPTIONS);
    }

    private static IlimapDocumentSymbol childNamed(IlimapDocumentSymbol parent, String name) {
        return parent.children().stream()
                .filter(child -> child.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static String validMapping() {
        return """
                mapping v2 "Profile" {
                  job {
                    name "demo";
                  }
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  enum Quality {
                    "old" -> "new";
                  }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      X = s.X;
                    }
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
                    ref Parent {
                      target rule r1 sourceRef s.Parent;
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
}
