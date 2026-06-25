package guru.interlis.transformer.mapping.ilimap.lsp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import guru.interlis.transformer.mapping.ilimap.ide.IlimapAnalysisOptions;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapFormattingService;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapIdePosition;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapLineMap;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapValidateMappingParams;

import java.nio.file.Path;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InsertTextFormat;
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
        assertThat(result.getCapabilities().getCompletionProvider().getTriggerCharacters())
                .containsExactly(".", "\"");
    }

    @Test
    void lspMapsCompletionItems() {
        String source = validMapping();
        open(source);

        Either<List<CompletionItem>, CompletionList> result = service.completion(
                        completionParams(source, "enumMap(s.X, Quality)", "enumMap(s.X, Qual".length()))
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

    @Test
    void lspMapsSnippetCompletionItems() {
        String source = "mapping v2 {\n  input\n";
        open(source);

        CompletionItem item =
                service.completion(completionParams(source, "input", "input".length())).join().getLeft().stream()
                        .filter(candidate -> candidate.getLabel().equals("input block"))
                        .findFirst()
                        .orElseThrow();

        assertThat(item.getKind()).isEqualTo(CompletionItemKind.Snippet);
        assertThat(item.getInsertTextFormat()).isEqualTo(InsertTextFormat.Snippet);
        assertThat(item.getTextEdit().getLeft().getNewText()).contains("input ${1:src}");
    }

    @Test
    void lspMapsModelAwareCompletionTextEditUsingWorkspaceRoot() {
        IlimapTextDocumentService modelAwareService = new IlimapTextDocumentService(
                new IlimapDocumentStore(),
                new IlimapLspDiagnosticMapper(),
                new IlimapFormattingService(),
                new IlimapLspRangeMapper(),
                IlimapAnalysisOptions.defaults(Path.of("/tmp")));
        InitializeParams params = new InitializeParams();
        params.setRootUri(Path.of(".").toAbsolutePath().normalize().toUri().toString());
        new IlimapLanguageServer(modelAwareService, new IlimapWorkspaceService())
                .initialize(params)
                .join();

        String source = modelAwareMapping();
        modelAwareService.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI, "ilimap", 1, source)));
        modelAwareService
                .validateMapping(new IlimapValidateMappingParams(URI, source, 1))
                .join();

        CompletionItem item = modelAwareService
                .completion(completionParams(source, "TestModel.Test", "TestModel.Test".length()))
                .join()
                .getLeft()
                .stream()
                .filter(candidate -> candidate.getLabel().equals("TestModel.TestTopic.TestClass"))
                .findFirst()
                .orElseThrow();

        assertThat(item.getKind()).isEqualTo(CompletionItemKind.Class);
        assertThat(item.getTextEdit().getLeft().getNewText()).isEqualTo("TestModel.TestTopic.TestClass");
        assertThat(item.getTextEdit().getLeft().getRange())
                .isEqualTo(rangeFor(source, "TestModel.Test", 0, "TestModel.Test".length()));
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

    private static org.eclipse.lsp4j.Range rangeFor(String source, String needle, int startDelta, int endDelta) {
        int offset = source.indexOf(needle);
        assertThat(offset).as("needle offset for %s", needle).isGreaterThanOrEqualTo(0);
        IlimapLineMap lineMap = new IlimapLineMap(source);
        IlimapIdePosition start = lineMap.toIdePosition(offset + startDelta);
        IlimapIdePosition end = lineMap.toIdePosition(offset + endDelta);
        return new org.eclipse.lsp4j.Range(
                new Position(start.line(), start.character()), new Position(end.line(), end.character()));
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

    private static String modelAwareMapping() {
        return """
                mapping v2 {
                  job {
                    modeldir "src/test/data/models/";
                  }
                  input src { path "in.xtf"; model "TestModel"; }
                  output out { path "out.xtf"; model "TestModel"; }
                  rule r1 {
                    target out class "TestModel.Test";
                    source s from src class "TestModel.TestTopic.TestClass";
                    assign {
                      Name = s.Name;
                    }
                  }
                }
                """;
    }
}
