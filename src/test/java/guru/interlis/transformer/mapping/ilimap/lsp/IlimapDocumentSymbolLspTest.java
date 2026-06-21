package guru.interlis.transformer.mapping.ilimap.lsp;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.mapping.ilimap.ide.IlimapAnalysisOptions;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapFormattingService;

import java.nio.file.Path;
import java.util.List;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

class IlimapDocumentSymbolLspTest {

    private static final String URI = "file:///mapping.ilimap";

    private final IlimapTextDocumentService service = new IlimapTextDocumentService(
            new IlimapDocumentStore(),
            new IlimapLspDiagnosticMapper(),
            new IlimapFormattingService(),
            new IlimapLspRangeMapper(),
            IlimapAnalysisOptions.defaults(Path.of(".")));

    @Test
    void serverAdvertisesDocumentSymbolCapability() {
        var result =
                new IlimapLanguageServer().initialize(new InitializeParams()).join();

        assertThat(result.getCapabilities().getDocumentSymbolProvider().getLeft())
                .isTrue();
    }

    @Test
    void lspMapsSymbolsToDocumentSymbol() {
        open(validMapping());

        List<Either<SymbolInformation, DocumentSymbol>> symbols = service.documentSymbol(
                        new DocumentSymbolParams(new TextDocumentIdentifier(URI)))
                .join();

        assertThat(symbols).singleElement().satisfies(either -> {
            assertThat(either.isRight()).isTrue();
            DocumentSymbol mapping = either.getRight();
            assertThat(mapping.getName()).isEqualTo("mapping Profile");
            assertThat(mapping.getKind()).isEqualTo(SymbolKind.Module);
            assertThat(mapping.getChildren())
                    .extracting(DocumentSymbol::getName)
                    .contains("rule r1");
            DocumentSymbol rule = childNamed(mapping, "rule r1");
            assertThat(rule.getKind()).isEqualTo(SymbolKind.Method);
            DocumentSymbol bag = childNamed(rule, "bag Outer");
            assertThat(childNamed(bag, "bag Inner").getKind()).isEqualTo(SymbolKind.Object);
        });
    }

    private void open(String source) {
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI, "ilimap", 1, source)));
    }

    private static DocumentSymbol childNamed(DocumentSymbol parent, String name) {
        return parent.getChildren().stream()
                .filter(child -> child.getName().equals(name))
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
}
