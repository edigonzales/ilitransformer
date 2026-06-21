package guru.interlis.transformer.mapping.ilimap.lsp;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.mapping.ilimap.ide.IlimapMappingSummaryParams;

import java.lang.reflect.Method;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.junit.jupiter.api.Test;

class IlimapMappingSummaryLspTest {

    private static final String URI = "file:///mapping.ilimap";

    @Test
    void exposesMappingSummaryCustomRequest() throws NoSuchMethodException {
        Method method = IlimapLanguageServer.class.getMethod("mappingSummary", IlimapMappingSummaryParams.class);
        JsonRequest request = method.getAnnotation(JsonRequest.class);

        assertThat(request).isNotNull();
        assertThat(request.value()).isEqualTo("ilimap/mappingSummary");
        assertThat(request.useSegment()).isFalse();
    }

    @Test
    void returnsSummaryForOpenDocument() {
        IlimapTextDocumentService textDocumentService = new IlimapTextDocumentService();
        IlimapLanguageServer server = new IlimapLanguageServer(textDocumentService, new IlimapWorkspaceService());
        textDocumentService.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI, "ilimap", 1, validMapping())));

        var summary = server.mappingSummary(new IlimapMappingSummaryParams(URI)).join();

        assertThat(summary.available()).isTrue();
        assertThat(summary.mappingName()).isEqualTo("Profile");
        assertThat(summary.inputCount()).isEqualTo(1);
        assertThat(summary.outputCount()).isEqualTo(1);
        assertThat(summary.ruleCount()).isEqualTo(1);
        assertThat(summary.rules()).singleElement().satisfies(rule -> {
            assertThat(rule.id()).isEqualTo("r1");
            assertThat(rule.status()).isEqualTo("ok");
        });
    }

    @Test
    void returnsUnavailableSummaryForUnopenedDocument() {
        IlimapLanguageServer server = new IlimapLanguageServer(new IlimapTextDocumentService(), new IlimapWorkspaceService());

        var summary = server.mappingSummary(new IlimapMappingSummaryParams(URI)).join();

        assertThat(summary.available()).isFalse();
        assertThat(summary.message()).contains("No open ILIMAP document");
        assertThat(summary.inputCount()).isZero();
        assertThat(summary.rules()).isEmpty();
    }

    private static String validMapping() {
        return """
                mapping v2 "Profile" {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                }
                """;
    }
}
