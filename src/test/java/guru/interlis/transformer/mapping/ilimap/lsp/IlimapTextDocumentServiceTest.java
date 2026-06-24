package guru.interlis.transformer.mapping.ilimap.lsp;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapAnalysisOptions;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapFormattingService;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.Test;

class IlimapTextDocumentServiceTest {

    private final CapturingLanguageClient client = new CapturingLanguageClient();
    private final IlimapTextDocumentService service = new IlimapTextDocumentService(
            new IlimapDocumentStore(),
            new IlimapLspDiagnosticMapper(),
            new IlimapFormattingService(),
            new IlimapLspRangeMapper(),
            IlimapAnalysisOptions.defaults(Path.of(".")));

    @Test
    void didOpenPublishesDiagnostics() {
        service.connect(client);

        service.didOpen(new DidOpenTextDocumentParams(
                new TextDocumentItem("file:///mapping.ilimap", "ilimap", 1, missingSemicolonMapping())));

        assertThat(client.publishedDiagnostics()).singleElement().satisfies(params -> {
            assertThat(params.getUri()).isEqualTo("file:///mapping.ilimap");
            assertThat(params.getVersion()).isEqualTo(1);
            assertThat(params.getDiagnostics()).hasSize(1);
            assertThat(params.getDiagnostics().get(0).getCode().getLeft())
                    .isEqualTo(DiagnosticCode.ILIMAP_SYNTAX_ERROR);
        });
    }

    @Test
    void didChangePublishesUpdatedDiagnostics() {
        service.connect(client);
        service.didOpen(new DidOpenTextDocumentParams(
                new TextDocumentItem("file:///mapping.ilimap", "ilimap", 1, missingSemicolonMapping())));

        service.didChange(new DidChangeTextDocumentParams(
                new VersionedTextDocumentIdentifier("file:///mapping.ilimap", 2),
                List.of(new TextDocumentContentChangeEvent(validMapping()))));

        assertThat(client.publishedDiagnostics()).hasSize(2);
        PublishDiagnosticsParams params = client.publishedDiagnostics().get(1);
        assertThat(params.getUri()).isEqualTo("file:///mapping.ilimap");
        assertThat(params.getVersion()).isEqualTo(2);
        assertThat(params.getDiagnostics()).isEmpty();
    }

    @Test
    void didCloseClearsDiagnostics() {
        service.connect(client);
        service.didOpen(new DidOpenTextDocumentParams(
                new TextDocumentItem("file:///mapping.ilimap", "ilimap", 1, missingSemicolonMapping())));

        service.didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier("file:///mapping.ilimap")));

        assertThat(client.publishedDiagnostics()).hasSize(2);
        PublishDiagnosticsParams params = client.publishedDiagnostics().get(1);
        assertThat(params.getUri()).isEqualTo("file:///mapping.ilimap");
        assertThat(params.getVersion()).isNull();
        assertThat(params.getDiagnostics()).isEmpty();
    }

    @Test
    void validateMappingPublishesFreshDiagnosticsForUpdatedText() {
        IlimapTextDocumentService modelAwareService = new IlimapTextDocumentService(
                new IlimapDocumentStore(),
                new IlimapLspDiagnosticMapper(),
                new IlimapFormattingService(),
                new IlimapLspRangeMapper(),
                IlimapAnalysisOptions.modelAware(Path.of(".")));
        modelAwareService.connect(client);
        modelAwareService.didOpen(new DidOpenTextDocumentParams(
                new TextDocumentItem("file:///mapping.ilimap", "ilimap", 1, missingModeldirMapping())));

        var result = modelAwareService
                .validateMapping(new guru.interlis.transformer.mapping.ilimap.ide.IlimapValidateMappingParams(
                        "file:///mapping.ilimap", validModelAwareMapping(), 2))
                .join();

        assertThat(result.available()).isTrue();
        assertThat(result.diagnosticCount()).isZero();
        waitForPublishedDiagnosticCount(2);
        assertThat(client.publishedDiagnostics().get(0).getDiagnostics()).isEmpty();
        PublishDiagnosticsParams params = client.publishedDiagnostics().get(1);
        assertThat(params.getUri()).isEqualTo("file:///mapping.ilimap");
        assertThat(params.getVersion()).isEqualTo(2);
        assertThat(params.getDiagnostics()).isEmpty();
    }

    @Test
    void didOpenUsesFastDiagnosticsWithoutModelCompile() {
        IlimapTextDocumentService modelAwareService = new IlimapTextDocumentService(
                new IlimapDocumentStore(),
                new IlimapLspDiagnosticMapper(),
                new IlimapFormattingService(),
                new IlimapLspRangeMapper(),
                IlimapAnalysisOptions.modelAware(Path.of(".")));
        modelAwareService.connect(client);

        modelAwareService.didOpen(new DidOpenTextDocumentParams(
                new TextDocumentItem("file:///mapping.ilimap", "ilimap", 1, missingModeldirMapping())));

        assertThat(client.publishedDiagnostics()).singleElement().satisfies(params -> {
            assertThat(params.getUri()).isEqualTo("file:///mapping.ilimap");
            assertThat(params.getDiagnostics()).isEmpty();
        });
    }

    @Test
    void didSavePublishesFreshDiagnosticsForSavedText() {
        IlimapTextDocumentService modelAwareService = new IlimapTextDocumentService(
                new IlimapDocumentStore(),
                new IlimapLspDiagnosticMapper(),
                new IlimapFormattingService(),
                new IlimapLspRangeMapper(),
                IlimapAnalysisOptions.modelAware(Path.of(".")));
        modelAwareService.connect(client);
        modelAwareService.didOpen(new DidOpenTextDocumentParams(
                new TextDocumentItem("file:///mapping.ilimap", "ilimap", 1, missingModeldirOnlyMapping())));

        modelAwareService.didSave(new DidSaveTextDocumentParams(
                new TextDocumentIdentifier("file:///mapping.ilimap"), validModelAwareMapping()));

        waitForPublishedDiagnosticCount(3);
        PublishDiagnosticsParams params = client.publishedDiagnostics().get(2);
        assertThat(params.getUri()).isEqualTo("file:///mapping.ilimap");
        assertThat(params.getVersion()).isEqualTo(1);
        assertThat(params.getDiagnostics()).isEmpty();
    }

    private static String validMapping() {
        return """
                mapping v2 {
                  input src { path "in.xtf"; model "M"; }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                }
                """;
    }

    private static String validModelAwareMapping() {
        return """
                mapping v2 {
                  job {
                    modeldir "src/test/data/models/";
                  }
                  input src { path "in.xtf"; model "TestModel"; }
                  output out { path "out.xtf"; model "TestModel"; }
                  rule r1 {
                    target out class "TestModel.TestTopic.TestClass";
                    source s from src class "TestModel.TestTopic.TestClass";
                  }
                }
                """;
    }

    private static String missingModeldirOnlyMapping() {
        return """
                mapping v2 {
                  job {
                    modeldir "models/";
                  }
                }
                """;
    }

    private static String missingModeldirMapping() {
        return """
                mapping v2 {
                  job {
                    modeldir "models/";
                  }
                  input src { path "in.xtf"; model "TestModel"; }
                  output out { path "out.xtf"; model "TestModel"; }
                  rule r1 {
                    target out class "TestModel.TestTopic.TestClass";
                    source s from src class "TestModel.TestTopic.TestClass";
                  }
                }
                """;
    }

    private static String missingSemicolonMapping() {
        return """
                mapping v2 {
                  input src {
                    path "in.xtf"
                    model "M";
                  }
                  output out { path "out.xtf"; model "M"; }
                  rule r1 {
                    target out class "M.A";
                    source s from src class "M.A";
                  }
                }
                """;
    }

    private static final class CapturingLanguageClient implements LanguageClient {

        private final List<PublishDiagnosticsParams> publishedDiagnostics = new CopyOnWriteArrayList<>();

        List<PublishDiagnosticsParams> publishedDiagnostics() {
            return publishedDiagnostics;
        }

        @Override
        public void telemetryEvent(Object object) {}

        @Override
        public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
            publishedDiagnostics.add(diagnostics);
        }

        @Override
        public void showMessage(MessageParams messageParams) {}

        @Override
        public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void logMessage(MessageParams message) {}
    }

    private void waitForPublishedDiagnosticCount(int count) {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (client.publishedDiagnostics().size() < count && System.nanoTime() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(client.publishedDiagnostics()).hasSizeGreaterThanOrEqualTo(count);
    }
}
