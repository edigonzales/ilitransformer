package guru.interlis.transformer.mapping.ilimap.lsp;

import guru.interlis.transformer.mapping.ilimap.format.IlimapFormatOptions;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapAnalysis;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapAnalysisOptions;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapFormattingService;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapTextEdit;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

public final class IlimapTextDocumentService implements TextDocumentService {

    private final IlimapDocumentStore documentStore;
    private final IlimapLspDiagnosticMapper diagnosticMapper;
    private final IlimapFormattingService formattingService;
    private final IlimapLspRangeMapper rangeMapper;
    private final IlimapAnalysisOptions analysisOptions;
    private LanguageClient client;

    public IlimapTextDocumentService() {
        this(
                new IlimapDocumentStore(),
                new IlimapLspDiagnosticMapper(),
                new IlimapFormattingService(),
                new IlimapLspRangeMapper(),
                IlimapAnalysisOptions.defaults(Path.of(".")));
    }

    IlimapTextDocumentService(
            IlimapDocumentStore documentStore,
            IlimapLspDiagnosticMapper diagnosticMapper,
            IlimapFormattingService formattingService,
            IlimapLspRangeMapper rangeMapper,
            IlimapAnalysisOptions analysisOptions) {
        this.documentStore = Objects.requireNonNull(documentStore, "documentStore");
        this.diagnosticMapper = Objects.requireNonNull(diagnosticMapper, "diagnosticMapper");
        this.formattingService = Objects.requireNonNull(formattingService, "formattingService");
        this.rangeMapper = Objects.requireNonNull(rangeMapper, "rangeMapper");
        this.analysisOptions = Objects.requireNonNull(analysisOptions, "analysisOptions");
    }

    public void connect(LanguageClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        var textDocument = params.getTextDocument();
        documentStore.open(textDocument.getUri(), textDocument.getText(), textDocument.getVersion());
        analyzeAndPublish(textDocument.getUri());
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String text = latestText(params.getContentChanges());
        documentStore.updateFull(uri, text, params.getTextDocument().getVersion());
        analyzeAndPublish(uri);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        documentStore.close(uri);
        publish(uri, null, List.of());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        // No-op in the diagnostics-only MVP.
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        String uri = params.getTextDocument().getUri();
        return CompletableFuture.completedFuture(documentStore
                .get(uri)
                .flatMap(snapshot -> formattingService.format(uri, snapshot.text(), IlimapFormatOptions.defaults()))
                .map(this::toLspTextEdit)
                .map(List::of)
                .orElseGet(List::of));
    }

    private void analyzeAndPublish(String uri) {
        IlimapAnalysis analysis = documentStore.analyze(uri, analysisOptions);
        Integer version =
                documentStore.get(uri).map(IlimapDocumentSnapshot::version).orElse(null);
        publish(uri, version, diagnosticMapper.map(analysis.diagnostics()));
    }

    private TextEdit toLspTextEdit(IlimapTextEdit edit) {
        return new TextEdit(rangeMapper.toLspRange(edit.range()), edit.newText());
    }

    private void publish(String uri, Integer version, List<org.eclipse.lsp4j.Diagnostic> diagnostics) {
        if (client != null) {
            client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics, version));
        }
    }

    private String latestText(List<TextDocumentContentChangeEvent> contentChanges) {
        if (contentChanges == null || contentChanges.isEmpty()) {
            return "";
        }
        return contentChanges.get(contentChanges.size() - 1).getText();
    }
}
