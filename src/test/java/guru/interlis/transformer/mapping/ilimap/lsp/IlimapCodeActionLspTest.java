package guru.interlis.transformer.mapping.ilimap.lsp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapAnalysisOptions;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapFormattingService;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapIdePosition;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapLineMap;

import java.nio.file.Path;
import java.util.List;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionOptions;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

class IlimapCodeActionLspTest {

    private static final String URI = "file:///mapping.ilimap";

    private final IlimapTextDocumentService service = new IlimapTextDocumentService(
            new IlimapDocumentStore(),
            new IlimapLspDiagnosticMapper(),
            new IlimapFormattingService(),
            new IlimapLspRangeMapper(),
            IlimapAnalysisOptions.defaults(Path.of(".")));

    @Test
    void serverAdvertisesCodeActionCapability() {
        var result =
                new IlimapLanguageServer().initialize(new InitializeParams()).join();

        assertThat(result.getCapabilities().getCodeActionProvider().isRight()).isTrue();
        CodeActionOptions options =
                result.getCapabilities().getCodeActionProvider().getRight();
        assertThat(options.getCodeActionKinds()).containsExactly(CodeActionKind.QuickFix, CodeActionKind.Source);
    }

    @Test
    void mapsCodeActionToLsp() {
        String source = validMapping();
        Range range = rangeFor(source, "\"Quality\"", 1, "Quality".length() + 1);
        Diagnostic diagnostic = new Diagnostic(
                range,
                "enumMap uses string literal",
                DiagnosticSeverity.Warning,
                "ilimap",
                DiagnosticCode.ILIMAP_ENUM_MAP_STRING_REF);
        open(source);

        CodeAction action =
                codeAction(source, "\"Quality\"", 1, "Quality".length() + 1, List.of(), List.of(diagnostic)).stream()
                        .filter(item -> item.getRight().getTitle().equals("Use symbolic enum map reference"))
                        .findFirst()
                        .orElseThrow()
                        .getRight();

        assertThat(action.getKind()).isEqualTo(CodeActionKind.QuickFix);
        assertThat(action.getDiagnostics()).containsExactly(diagnostic);
        assertThat(action.getEdit().getChanges()).containsOnlyKeys(URI);
        assertThat(action.getEdit().getChanges().get(URI))
                .extracting(TextEdit::getNewText, edit -> textAt(source, edit.getRange()))
                .containsExactly(tuple("", "\""), tuple("", "\""));
    }

    @Test
    void filtersCodeActionsByRequestedKind() {
        String source = validMapping();
        open(source);

        List<CodeAction> sourceActions =
                codeAction(source, "\"Quality\"", 1, "Quality".length() + 1, List.of(CodeActionKind.Source), List.of())
                        .stream()
                        .map(Either::getRight)
                        .toList();
        assertThat(sourceActions).extracting(CodeAction::getTitle).containsExactly("Format ILIMAP document");

        List<CodeAction> quickFixActions =
                codeAction(
                                source,
                                "\"Quality\"",
                                1,
                                "Quality".length() + 1,
                                List.of(CodeActionKind.QuickFix),
                                List.of())
                        .stream()
                        .map(Either::getRight)
                        .toList();
        assertThat(quickFixActions).extracting(CodeAction::getTitle).containsExactly("Use symbolic enum map reference");
    }

    private void open(String source) {
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI, "ilimap", 1, source)));
    }

    private List<Either<Command, CodeAction>> codeAction(
            String source,
            String needle,
            int startDelta,
            int endDelta,
            List<String> only,
            List<Diagnostic> diagnostics) {
        Range range = rangeFor(source, needle, startDelta, endDelta);
        return service.codeAction(new CodeActionParams(
                        new TextDocumentIdentifier(URI), range, new CodeActionContext(diagnostics, only)))
                .join();
    }

    private static Range rangeFor(String source, String needle, int startDelta, int endDelta) {
        int offset = source.indexOf(needle);
        assertThat(offset).as("needle offset for %s", needle).isGreaterThanOrEqualTo(0);
        IlimapLineMap lineMap = new IlimapLineMap(source);
        IlimapIdePosition start = lineMap.toIdePosition(offset + startDelta);
        IlimapIdePosition end = lineMap.toIdePosition(offset + endDelta);
        return new Range(new Position(start.line(), start.character()), new Position(end.line(), end.character()));
    }

    private static String textAt(String source, Range range) {
        IlimapLineMap lineMap = new IlimapLineMap(source);
        int start = lineMap.positionToOffset(
                range.getStart().getLine(), range.getStart().getCharacter());
        int end = lineMap.positionToOffset(
                range.getEnd().getLine(), range.getEnd().getCharacter());
        return source.substring(start, end);
    }

    private static String validMapping() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  enum Quality {
                    "old" => "new";
                  }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      X = enumMap(s.X, "Quality");
                    }
                  }
                }
                """;
    }
}
