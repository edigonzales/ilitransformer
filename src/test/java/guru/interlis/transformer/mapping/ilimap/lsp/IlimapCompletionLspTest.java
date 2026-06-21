package guru.interlis.transformer.mapping.ilimap.lsp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import guru.interlis.transformer.mapping.ilimap.ide.IlimapAnalysisOptions;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapFormattingService;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapIdePosition;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapLineMap;

import java.nio.file.Path;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

class IlimapCompletionLspTest {

    private static final String URI = "file:///mapping.ilimap";

    private final IlimapTextDocumentService service = new IlimapTextDocumentService(
            new IlimapDocumentStore(),
            new IlimapLspDiagnosticMapper(),
            new IlimapFormattingService(),
            new IlimapLspRangeMapper(),
            IlimapAnalysisOptions.defaults(Path.of(".")));

    @Test
    void serverAdvertisesCompletionCapability() {
        var result =
                new IlimapLanguageServer().initialize(new InitializeParams()).join();

        assertThat(result.getCapabilities().getCompletionProvider()).isNotNull();
        assertThat(result.getCapabilities().getCompletionProvider().getResolveProvider())
                .isFalse();
    }

    @Test
    void lspMapsCompletionItems() {
        String source = validMapping();
        open(source);

        Either<List<CompletionItem>, CompletionList> result =
                service.completion(completionParams(source, "enumMap(s.X, Quality)", "enumMap(s.X, Qual".length()))
                        .join();

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft())
                .extracting(
                        CompletionItem::getLabel,
                        CompletionItem::getKind,
                        CompletionItem::getDetail,
                        CompletionItem::getInsertText)
                .containsExactly(tuple("Quality", CompletionItemKind.Enum, "enum map", "Quality"));
    }

    private void open(String source) {
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI, "ilimap", 1, source)));
    }

    private CompletionParams completionParams(String source, String needle, int cursorDelta) {
        int offset = source.indexOf(needle);
        assertThat(offset).as("needle offset for %s", needle).isGreaterThanOrEqualTo(0);
        IlimapIdePosition position = new IlimapLineMap(source).toIdePosition(offset + cursorDelta);
        return new CompletionParams(
                new TextDocumentIdentifier(URI), new Position(position.line(), position.character()));
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
                    }
                  }
                }
                """;
    }
}
