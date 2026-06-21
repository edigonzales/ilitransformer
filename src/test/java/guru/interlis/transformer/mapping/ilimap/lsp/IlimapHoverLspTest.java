package guru.interlis.transformer.mapping.ilimap.lsp;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.mapping.ilimap.ide.IlimapAnalysisOptions;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapFormattingService;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapIdePosition;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapLineMap;

import java.nio.file.Path;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.Test;

class IlimapHoverLspTest {

    private static final String URI = "file:///mapping.ilimap";

    private final IlimapTextDocumentService service = new IlimapTextDocumentService(
            new IlimapDocumentStore(),
            new IlimapLspDiagnosticMapper(),
            new IlimapFormattingService(),
            new IlimapLspRangeMapper(),
            IlimapAnalysisOptions.defaults(Path.of(".")));

    @Test
    void serverAdvertisesHoverCapability() {
        var result =
                new IlimapLanguageServer().initialize(new InitializeParams()).join();

        assertThat(result.getCapabilities().getHoverProvider().getLeft()).isTrue();
    }

    @Test
    void lspMapsHoverMarkdown() {
        String source = validMapping();
        open(source);

        Hover hover = service.hover(hoverParams(source, "target out class", "target ou".length()))
                .join();

        assertThat(hover).isNotNull();
        assertThat(hover.getContents().isRight()).isTrue();
        assertThat(hover.getContents().getRight().getKind()).isEqualTo(MarkupKind.MARKDOWN);
        assertThat(hover.getContents().getRight().getValue()).contains("**output `out`**");
        assertThat(textAt(source, hover)).isEqualTo("out");
    }

    @Test
    void lspReturnsNullHoverForUnknownSymbol() {
        String source = validMapping().replace("enumMap(s.X, Quality)", "coalesce(s.X, Quality)");
        open(source);

        Hover hover = service.hover(hoverParams(source, "coalesce(s.X, Quality)", "coalesce(s.X, Qual".length()))
                .join();

        assertThat(hover).isNull();
    }

    private void open(String source) {
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI, "ilimap", 1, source)));
    }

    private HoverParams hoverParams(String source, String needle, int cursorDelta) {
        int offset = source.indexOf(needle);
        assertThat(offset).as("needle offset for %s", needle).isGreaterThanOrEqualTo(0);
        IlimapIdePosition position = new IlimapLineMap(source).toIdePosition(offset + cursorDelta);
        return new HoverParams(new TextDocumentIdentifier(URI), new Position(position.line(), position.character()));
    }

    private static String textAt(String source, Hover hover) {
        IlimapLineMap lineMap = new IlimapLineMap(source);
        int start = lineMap.positionToOffset(
                hover.getRange().getStart().getLine(),
                hover.getRange().getStart().getCharacter());
        int end = lineMap.positionToOffset(
                hover.getRange().getEnd().getLine(), hover.getRange().getEnd().getCharacter());
        return source.substring(start, end);
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
