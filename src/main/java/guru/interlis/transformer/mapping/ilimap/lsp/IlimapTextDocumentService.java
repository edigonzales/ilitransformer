package guru.interlis.transformer.mapping.ilimap.lsp;

import guru.interlis.transformer.mapping.ilimap.format.IlimapFormatOptions;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapAnalysis;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapAnalysisOptions;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapCompletionService;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapDocumentSymbol;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapDocumentSymbolService;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapFoldingRange;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapFoldingService;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapFormattingService;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapIdePosition;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapSymbolDisplayKind;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapTextEdit;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

public final class IlimapTextDocumentService implements TextDocumentService {

    private final IlimapDocumentStore documentStore;
    private final IlimapLspDiagnosticMapper diagnosticMapper;
    private final IlimapFormattingService formattingService;
    private final IlimapDocumentSymbolService documentSymbolService;
    private final IlimapFoldingService foldingService;
    private final IlimapCompletionService completionService;
    private final IlimapLspCompletionMapper completionMapper;
    private final IlimapLspRangeMapper rangeMapper;
    private final IlimapAnalysisOptions analysisOptions;
    private LanguageClient client;

    public IlimapTextDocumentService() {
        this(
                new IlimapDocumentStore(),
                new IlimapLspDiagnosticMapper(),
                new IlimapFormattingService(),
                new IlimapDocumentSymbolService(),
                new IlimapFoldingService(),
                new IlimapCompletionService(),
                new IlimapLspCompletionMapper(),
                new IlimapLspRangeMapper(),
                IlimapAnalysisOptions.defaults(Path.of(".")));
    }

    IlimapTextDocumentService(
            IlimapDocumentStore documentStore,
            IlimapLspDiagnosticMapper diagnosticMapper,
            IlimapFormattingService formattingService,
            IlimapLspRangeMapper rangeMapper,
            IlimapAnalysisOptions analysisOptions) {
        this(
                documentStore,
                diagnosticMapper,
                formattingService,
                new IlimapDocumentSymbolService(),
                new IlimapFoldingService(),
                new IlimapCompletionService(),
                new IlimapLspCompletionMapper(),
                rangeMapper,
                analysisOptions);
    }

    IlimapTextDocumentService(
            IlimapDocumentStore documentStore,
            IlimapLspDiagnosticMapper diagnosticMapper,
            IlimapFormattingService formattingService,
            IlimapDocumentSymbolService documentSymbolService,
            IlimapFoldingService foldingService,
            IlimapLspRangeMapper rangeMapper,
            IlimapAnalysisOptions analysisOptions) {
        this(
                documentStore,
                diagnosticMapper,
                formattingService,
                documentSymbolService,
                foldingService,
                new IlimapCompletionService(),
                new IlimapLspCompletionMapper(),
                rangeMapper,
                analysisOptions);
    }

    IlimapTextDocumentService(
            IlimapDocumentStore documentStore,
            IlimapLspDiagnosticMapper diagnosticMapper,
            IlimapFormattingService formattingService,
            IlimapDocumentSymbolService documentSymbolService,
            IlimapFoldingService foldingService,
            IlimapCompletionService completionService,
            IlimapLspCompletionMapper completionMapper,
            IlimapLspRangeMapper rangeMapper,
            IlimapAnalysisOptions analysisOptions) {
        this.documentStore = Objects.requireNonNull(documentStore, "documentStore");
        this.diagnosticMapper = Objects.requireNonNull(diagnosticMapper, "diagnosticMapper");
        this.formattingService = Objects.requireNonNull(formattingService, "formattingService");
        this.documentSymbolService = Objects.requireNonNull(documentSymbolService, "documentSymbolService");
        this.foldingService = Objects.requireNonNull(foldingService, "foldingService");
        this.completionService = Objects.requireNonNull(completionService, "completionService");
        this.completionMapper = Objects.requireNonNull(completionMapper, "completionMapper");
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

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
            DocumentSymbolParams params) {
        String uri = params.getTextDocument().getUri();
        return CompletableFuture.completedFuture(documentStore
                .get(uri)
                .map(snapshot -> documentStore.analyze(uri, analysisOptions))
                .map(documentSymbolService::symbols)
                .map(symbols ->
                        symbols.stream().map(this::toLspDocumentSymbolEither).toList())
                .orElseGet(List::of));
    }

    @Override
    public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
        String uri = params.getTextDocument().getUri();
        return CompletableFuture.completedFuture(documentStore
                .get(uri)
                .map(snapshot -> documentStore.analyze(uri, analysisOptions))
                .map(foldingService::foldingRanges)
                .map(ranges -> ranges.stream().map(this::toLspFoldingRange).toList())
                .orElseGet(List::of));
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
        String uri = params.getTextDocument().getUri();
        if (documentStore.get(uri).isEmpty()) {
            return CompletableFuture.completedFuture(Either.forLeft(List.of()));
        }

        IlimapAnalysis analysis = documentStore.analyze(uri, analysisOptions);
        IlimapIdePosition position =
                new IlimapIdePosition(params.getPosition().getLine(), params.getPosition().getCharacter());
        List<CompletionItem> items = completionService.complete(analysis, position).stream()
                .map(completionMapper::map)
                .toList();
        return CompletableFuture.completedFuture(Either.forLeft(items));
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

    private Either<SymbolInformation, DocumentSymbol> toLspDocumentSymbolEither(IlimapDocumentSymbol symbol) {
        return Either.forRight(toLspDocumentSymbol(symbol));
    }

    private DocumentSymbol toLspDocumentSymbol(IlimapDocumentSymbol symbol) {
        List<DocumentSymbol> children =
                symbol.children().stream().map(this::toLspDocumentSymbol).toList();
        return new DocumentSymbol(
                symbol.name(),
                toLspSymbolKind(symbol.kind()),
                rangeMapper.toLspRange(symbol.range()),
                rangeMapper.toLspRange(symbol.selectionRange()),
                null,
                children);
    }

    private FoldingRange toLspFoldingRange(IlimapFoldingRange range) {
        FoldingRange foldingRange = new FoldingRange(range.startLine(), range.endLine());
        foldingRange.setKind(range.kind());
        return foldingRange;
    }

    private static SymbolKind toLspSymbolKind(IlimapSymbolDisplayKind kind) {
        return switch (kind) {
            case FILE -> SymbolKind.File;
            case MODULE -> SymbolKind.Module;
            case CLASS -> SymbolKind.Class;
            case METHOD -> SymbolKind.Method;
            case PROPERTY -> SymbolKind.Property;
            case ENUM -> SymbolKind.Enum;
            case FIELD -> SymbolKind.Field;
            case OBJECT -> SymbolKind.Object;
        };
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
