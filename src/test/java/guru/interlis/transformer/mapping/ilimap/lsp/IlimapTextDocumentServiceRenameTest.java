package guru.interlis.transformer.mapping.ilimap.lsp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import guru.interlis.transformer.mapping.ilimap.ide.IlimapAnalysisOptions;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapIdeRange;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapRenamePrepareResult;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapRenameResult;

import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.junit.jupiter.api.Test;

class IlimapTextDocumentServiceRenameTest {

    private static final String URI = "file:///mapping.ilimap";

    @Test
    void prepareRenameReturnsRangeAndPlaceholder() {
        IlimapTextDocumentService textDocumentService = new IlimapTextDocumentService();
        textDocumentService.didOpen(
                new DidOpenTextDocumentParams(new TextDocumentItem(URI, "ilimap", 1, validMapping())));

        var result = textDocumentService.prepareRename(
                new PrepareRenameParams(new TextDocumentIdentifier(URI), new Position(3, 11))).join();

        assertThat(result).isNotNull();
        assertThat(result.getSecond()).isNotNull();
        assertThat(result.getSecond().getPlaceholder()).isEqualTo("output id");
        assertThat(result.getSecond().getRange()).isNotNull();
    }

    @Test
    void prepareRenameReturnsNullForClosedDocument() {
        IlimapTextDocumentService textDocumentService = new IlimapTextDocumentService();

        var result = textDocumentService.prepareRename(
                new PrepareRenameParams(new TextDocumentIdentifier(URI), new Position(0, 0))).join();

        assertThat(result).isNull();
    }

    @Test
    void renameReturnsWorkspaceEditForOneDocument() {
        IlimapTextDocumentService textDocumentService = new IlimapTextDocumentService();
        textDocumentService.didOpen(
                new DidOpenTextDocumentParams(new TextDocumentItem(URI, "ilimap", 1, validMapping())));

        var edit = textDocumentService.rename(
                new RenameParams(new TextDocumentIdentifier(URI), new Position(3, 11), "myoutput")).join();

        assertThat(edit).isNotNull();
        assertThat(edit.getChanges()).containsKey(URI);
        assertThat(edit.getChanges().get(URI)).hasSize(2);
        for (var textEdit : edit.getChanges().get(URI)) {
            assertThat(textEdit.getNewText()).isEqualTo("myoutput");
        }
    }

    @Test
    void renameReturnsEmptyWorkspaceEditForClosedDocument() {
        IlimapTextDocumentService textDocumentService = new IlimapTextDocumentService();

        var edit = textDocumentService.rename(
                new RenameParams(new TextDocumentIdentifier(URI), new Position(0, 0), "anything")).join();

        assertThat(edit).isNotNull();
        assertThat(edit.getChanges()).isNullOrEmpty();
    }

    @Test
    void unsavedDocumentChangesAreReflected() {
        IlimapTextDocumentService textDocumentService = new IlimapTextDocumentService();
        textDocumentService.didOpen(
                new DidOpenTextDocumentParams(new TextDocumentItem(URI, "ilimap", 1, validMapping())));

        textDocumentService.didChange(
                new org.eclipse.lsp4j.DidChangeTextDocumentParams(
                        new org.eclipse.lsp4j.VersionedTextDocumentIdentifier(URI, 2),
                        List.of(new org.eclipse.lsp4j.TextDocumentContentChangeEvent(
                                mappingWithAdditionalInput()))));

        var edit = textDocumentService.rename(
                new RenameParams(new TextDocumentIdentifier(URI), new Position(3, 11), "myoutput")).join();

        assertThat(edit).isNotNull();
        assertThat(edit.getChanges()).containsKey(URI);
        assertThat(edit.getChanges().get(URI)).hasSize(2);
    }

    @Test
    void renameServerExposesRenameCapability() {
        IlimapTextDocumentService textDocumentService = new IlimapTextDocumentService();
        IlimapLanguageServer server =
                new IlimapLanguageServer(textDocumentService, new IlimapWorkspaceService());

        var initResult = server.initialize(new org.eclipse.lsp4j.InitializeParams()).join();

        assertThat(initResult.getCapabilities().getRenameProvider()).isNotNull();
        assertThat(initResult.getCapabilities().getRenameProvider().isRight()).isTrue();
        assertThat(initResult.getCapabilities().getRenameProvider().getRight().getPrepareProvider()).isTrue();
    }

    private static String validMapping() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; format xtf; }
                  input other { path "in2.xtf"; model "M"; format xtf; }
                  output out { path "out.xtf"; model "M"; format xtf; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      X = s.Name;
                    }
                  }
                }
                """;
    }

    private static String mappingWithAdditionalInput() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; format xtf; }
                  input extra { path "in3.xtf"; model "M"; format xtf; }
                  output out { path "out.xtf"; model "M"; format xtf; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      X = s.Name;
                    }
                  }
                }
                """;
    }
}
