package guru.interlis.transformer.mapping.ilimap.lsp;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.mapping.ilimap.ide.IlimapAnalysisOptions;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapFormattingService;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapIdePosition;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapLineMap;

import java.nio.file.Path;
import java.util.List;

import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

class IlimapDefinitionLspTest {

    private static final String URI = "file:///mapping.ilimap";

    private final IlimapTextDocumentService service = new IlimapTextDocumentService(
            new IlimapDocumentStore(),
            new IlimapLspDiagnosticMapper(),
            new IlimapFormattingService(),
            new IlimapLspRangeMapper(),
            IlimapAnalysisOptions.defaults(Path.of(".")));

    @Test
    void serverAdvertisesDefinitionCapability() {
        var result =
                new IlimapLanguageServer().initialize(new InitializeParams()).join();

        assertThat(result.getCapabilities().getDefinitionProvider().getLeft()).isTrue();
    }

    @Test
    void lspMapsDefinitionLocation() {
        String source = validMapping();
        open(source);

        Either<List<? extends Location>, List<? extends LocationLink>> result = service.definition(
                        definitionParams(source, "enumMap(s.X, Quality)", "enumMap(s.X, Qual".length()))
                .join();

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).singleElement().satisfies(location -> {
            assertThat(location.getUri()).isEqualTo(URI);
            assertThat(textAt(source, location)).isEqualTo("Quality");
        });
    }

    @Test
    void lspReturnsEmptyDefinitionListForUnknownSymbol() {
        String source = validMapping().replace("enumMap(s.X, Quality)", "coalesce(s.X, Quality)");
        open(source);

        Either<List<? extends Location>, List<? extends LocationLink>> result = service.definition(
                        definitionParams(source, "coalesce(s.X, Quality)", "coalesce(s.X, Qual".length()))
                .join();

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isEmpty();
    }

    private void open(String source) {
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI, "ilimap", 1, source)));
    }

    private DefinitionParams definitionParams(String source, String needle, int cursorDelta) {
        int offset = source.indexOf(needle);
        assertThat(offset).as("needle offset for %s", needle).isGreaterThanOrEqualTo(0);
        IlimapIdePosition position = new IlimapLineMap(source).toIdePosition(offset + cursorDelta);
        return new DefinitionParams(
                new TextDocumentIdentifier(URI), new Position(position.line(), position.character()));
    }

    private static String textAt(String source, Location location) {
        IlimapLineMap lineMap = new IlimapLineMap(source);
        int start = lineMap.positionToOffset(
                location.getRange().getStart().getLine(),
                location.getRange().getStart().getCharacter());
        int end = lineMap.positionToOffset(
                location.getRange().getEnd().getLine(),
                location.getRange().getEnd().getCharacter());
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
