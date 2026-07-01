package guru.interlis.transformer.mapping.ilimap.lsp;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.mapping.ilimap.ide.IlimapTraceParams;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapTraceSummary;

import java.lang.reflect.Method;
import java.util.List;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.junit.jupiter.api.Test;

class IlimapTextDocumentServiceTraceTest {

    private static final String URI = "file:///mapping.ilimap";

    @Test
    void exposesTraceCustomRequest() throws NoSuchMethodException {
        Method method = IlimapLanguageServer.class.getMethod("trace", IlimapTraceParams.class);
        JsonRequest request = method.getAnnotation(JsonRequest.class);

        assertThat(request).isNotNull();
        assertThat(request.value()).isEqualTo("ilimap/trace");
        assertThat(request.useSegment()).isFalse();
    }

    @Test
    void requestWithMissingUriReturnsUnavailable() {
        IlimapLanguageServer server =
                new IlimapLanguageServer(new IlimapTextDocumentService(), new IlimapWorkspaceService());

        IlimapTraceSummary trace = server.trace(new IlimapTraceParams(
                "file:///nonexistent.ilimap", "targetAttribute", "r1", "X", null, null, null))
                .join();

        assertThat(trace.available()).isFalse();
        assertThat(trace.message()).contains("No open ILIMAP document");
    }

    @Test
    void requestForOpenDocReturnsTrace() {
        IlimapTextDocumentService textDocumentService = new IlimapTextDocumentService();
        IlimapLanguageServer server =
                new IlimapLanguageServer(textDocumentService, new IlimapWorkspaceService());
        textDocumentService.didOpen(
                new DidOpenTextDocumentParams(new TextDocumentItem(URI, "ilimap", 1, copyAssignmentMapping())));

        IlimapTraceSummary trace = server.trace(new IlimapTraceParams(
                URI, "targetAttribute", "r1", "X", null, null, null))
                .join();

        assertThat(trace.available()).isTrue();
        assertThat(trace.ruleId()).isEqualTo("r1");
        assertThat(trace.target()).isNotNull();
        assertThat(trace.target().targetAttribute()).isEqualTo("X");
        assertThat(trace.expression()).isNotNull();
        assertThat(trace.expression().text()).isEqualTo("s.X");
    }

    @Test
    void unsavedDocumentChangesAreReflected() {
        IlimapTextDocumentService textDocumentService = new IlimapTextDocumentService();
        IlimapLanguageServer server =
                new IlimapLanguageServer(textDocumentService, new IlimapWorkspaceService());
        textDocumentService.didOpen(
                new DidOpenTextDocumentParams(new TextDocumentItem(URI, "ilimap", 1, copyAssignmentMapping())));

        textDocumentService.didChange(
                new org.eclipse.lsp4j.DidChangeTextDocumentParams(
                        new org.eclipse.lsp4j.VersionedTextDocumentIdentifier(URI, 2),
                        List.of(new org.eclipse.lsp4j.TextDocumentContentChangeEvent(
                                computedMapping()))));

        IlimapTraceSummary trace = server.trace(new IlimapTraceParams(
                URI, "targetAttribute", "r1", "Z", null, null, null))
                .join();

        assertThat(trace.available()).isTrue();
        assertThat(trace.expression()).isNotNull();
        assertThat(trace.expression().kind()).isEqualTo("computed");
    }

    private static String copyAssignmentMapping() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      X = s.X;
                    }
                  }
                }
                """;
    }

    private static String computedMapping() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                    assign {
                      Z = s.First + s.Last;
                    }
                  }
                }
                """;
    }
}
