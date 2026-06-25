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

    @Test
    void createsInputWithJdbcConnectionQueryAndGeometryChildren() {
        IlimapDocumentSymbol mapping =
                symbolService.symbols(analyze(jdbcInputMapping())).get(0);
        IlimapDocumentSymbol input = childNamed(mapping, "input db");

        assertThat(input.children())
                .extracting(IlimapDocumentSymbol::name)
                .containsExactly("connection", "query stations");

        IlimapDocumentSymbol connection = childNamed(input, "connection");
        assertThat(connection.kind()).isEqualTo(IlimapSymbolDisplayKind.OBJECT);
        assertThat(connection.children()).isEmpty();

        IlimapDocumentSymbol query = childNamed(input, "query stations");
        assertThat(query.kind()).isEqualTo(IlimapSymbolDisplayKind.CLASS);
        assertThat(query.children()).extracting(IlimapDocumentSymbol::name).containsExactly("geometry geom");

        IlimapDocumentSymbol geometry = childNamed(query, "geometry geom");
        assertThat(geometry.kind()).isEqualTo(IlimapSymbolDisplayKind.FIELD);
        assertThat(geometry.children()).isEmpty();
    }

    @Test
    void createsInputWithoutJdbcHasNoExtraChildren() {
        IlimapDocumentSymbol mapping =
                symbolService.symbols(analyze(validMapping())).get(0);
        IlimapDocumentSymbol input = childNamed(mapping, "input src");

        assertThat(input.children()).isEmpty();
    }

    private static String jdbcInputMapping() {
        return """
                mapping v2 "jdbc-demo" {
                  input db {
                    model "S";
                    format jdbc;
                    connection {
                      url "jdbc:sqlite:demo.sqlite";
                    }
                    query stations {
                      class "S.Data.Station";
                      sql "select id, name, geom_wkt from stations";
                      geometry {
                        attribute "geom";
                        column "geom_wkt";
                        encoding wkt;
                        type coord;
                      }
                    }
                  }
                  output o { path "out.xtf"; model "S"; }
                  rule r1 {
                    target o class "S.Data.Station";
                    source s from db class "S.Data.Station";
                  }
                }
                """;
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
