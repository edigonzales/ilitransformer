package guru.interlis.transformer.mapping.ilimap.lsp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import guru.interlis.transformer.mapping.ilimap.ide.IlimapAnalysisOptions;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapFormattingService;

import java.nio.file.Path;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.Test;

class IlimapFoldingLspTest {

    private static final String URI = "file:///mapping.ilimap";

    private final IlimapTextDocumentService service = new IlimapTextDocumentService(
            new IlimapDocumentStore(),
            new IlimapLspDiagnosticMapper(),
            new IlimapFormattingService(),
            new IlimapLspRangeMapper(),
            IlimapAnalysisOptions.defaults(Path.of(".")));

    @Test
    void serverAdvertisesFoldingRangeCapability() {
        var result =
                new IlimapLanguageServer().initialize(new InitializeParams()).join();

        assertThat(result.getCapabilities().getFoldingRangeProvider().getLeft()).isTrue();
    }

    @Test
    void lspMapsFoldingRanges() {
        open(validMapping());

        var ranges = service.foldingRange(new FoldingRangeRequestParams(new TextDocumentIdentifier(URI)))
                .join();

        assertThat(ranges)
                .extracting(FoldingRange::getStartLine, FoldingRange::getEndLine, FoldingRange::getKind)
                .contains(tuple(0, 31, "region"), tuple(9, 30, "region"), tuple(12, 14, "region"));
    }

    private void open(String source) {
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI, "ilimap", 1, source)));
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
