package guru.interlis.transformer.mapping.ilimap.lsp;

import guru.interlis.transformer.mapping.ilimap.format.IlimapFormatOptions;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapAnalysis;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapAnalysisOptions;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapCodeActionService;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapCompletionService;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapDefinition;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapDefinitionService;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapDocumentSymbol;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapDocumentSymbolService;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapFoldingRange;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapFoldingService;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapFormattingService;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapHover;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapHoverService;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapIdePosition;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapIdeRange;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapMappingSummary;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapMappingSummaryParams;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapMappingSummaryService;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapSymbolDisplayKind;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapTextEdit;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapValidateMappingParams;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapValidateMappingResult;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
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
    private final IlimapDefinitionService definitionService;
    private final IlimapHoverService hoverService;
    private final IlimapMappingSummaryService mappingSummaryService;
    private final IlimapLspRangeMapper rangeMapper;
    private final IlimapCodeActionService codeActionService;
    private final Map<String, CompletableFuture<IlimapAnalysis>> runningModelAnalyses = new ConcurrentHashMap<>();
    private IlimapAnalysisOptions analysisOptions;
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
                new IlimapDefinitionService(),
                new IlimapHoverService(),
                new IlimapMappingSummaryService(),
                new IlimapLspRangeMapper(),
                IlimapAnalysisOptions.modelAware(Path.of(".")));
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
                new IlimapDefinitionService(),
                new IlimapHoverService(),
                new IlimapMappingSummaryService(),
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
                new IlimapDefinitionService(),
                new IlimapHoverService(),
                new IlimapMappingSummaryService(),
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
            IlimapDefinitionService definitionService,
            IlimapHoverService hoverService,
            IlimapMappingSummaryService mappingSummaryService,
            IlimapLspRangeMapper rangeMapper,
            IlimapAnalysisOptions analysisOptions) {
        this.documentStore = Objects.requireNonNull(documentStore, "documentStore");
        this.diagnosticMapper = Objects.requireNonNull(diagnosticMapper, "diagnosticMapper");
        this.formattingService = Objects.requireNonNull(formattingService, "formattingService");
        this.documentSymbolService = Objects.requireNonNull(documentSymbolService, "documentSymbolService");
        this.foldingService = Objects.requireNonNull(foldingService, "foldingService");
        this.completionService = Objects.requireNonNull(completionService, "completionService");
        this.completionMapper = Objects.requireNonNull(completionMapper, "completionMapper");
        this.definitionService = Objects.requireNonNull(definitionService, "definitionService");
        this.hoverService = Objects.requireNonNull(hoverService, "hoverService");
        this.mappingSummaryService = Objects.requireNonNull(mappingSummaryService, "mappingSummaryService");
        this.rangeMapper = Objects.requireNonNull(rangeMapper, "rangeMapper");
        this.codeActionService = new IlimapCodeActionService(this.formattingService);
        this.analysisOptions = Objects.requireNonNull(analysisOptions, "analysisOptions");
    }

    public void connect(LanguageClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    public void setAnalysisOptions(IlimapAnalysisOptions analysisOptions) {
        this.analysisOptions = Objects.requireNonNull(analysisOptions, "analysisOptions");
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        var textDocument = params.getTextDocument();
        documentStore.open(textDocument.getUri(), textDocument.getText(), textDocument.getVersion());
        analyzeAndPublishFast(textDocument.getUri());
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String text = latestText(params.getContentChanges());
        documentStore.updateFull(uri, text, params.getTextDocument().getVersion());
        analyzeAndPublishFast(uri);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        runningModelAnalyses.remove(uri);
        documentStore.close(uri);
        publish(uri, null, List.of());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        if (params.getText() != null) {
            int version =
                    documentStore.get(uri).map(IlimapDocumentSnapshot::version).orElse(0);
            documentStore.updateFull(uri, params.getText(), version);
        }
        analyzeAndPublishFast(uri);
        analyzeModelAwareAndPublish(uri);
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
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        String uri = params.getTextDocument().getUri();
        if (documentStore.get(uri).isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        IlimapAnalysis analysis = analysisForCompletion(uri);
        CodeActionContext context = params.getContext();
        IlimapIdeRange requestedRange = toIdeRange(params.getRange());
        List<Either<Command, CodeAction>> actions = codeActionService.codeActions(analysis, requestedRange).stream()
                .filter(action -> matchesOnly(action.kind(), context))
                .map(action -> Either.<Command, CodeAction>forRight(toLspCodeAction(uri, action, context)))
                .toList();
        return CompletableFuture.completedFuture(actions);
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
            DocumentSymbolParams params) {
        String uri = params.getTextDocument().getUri();
        return CompletableFuture.completedFuture(documentStore
                .get(uri)
                .map(snapshot -> documentStore.analyze(snapshot, fastOptions()))
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
                .map(snapshot -> documentStore.analyze(snapshot, fastOptions()))
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

        IlimapAnalysis analysis = analysisForCompletion(uri);
        IlimapIdePosition position = new IlimapIdePosition(
                params.getPosition().getLine(), params.getPosition().getCharacter());
        List<CompletionItem> items = completionService.complete(analysis, position).stream()
                .map(completionMapper::map)
                .toList();
        return CompletableFuture.completedFuture(Either.forLeft(items));
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
            DefinitionParams params) {
        String uri = params.getTextDocument().getUri();
        if (documentStore.get(uri).isEmpty()) {
            return CompletableFuture.completedFuture(Either.forLeft(List.of()));
        }

        IlimapAnalysis analysis = analysisForCompletion(uri);
        IlimapIdePosition position = new IlimapIdePosition(
                params.getPosition().getLine(), params.getPosition().getCharacter());
        List<Location> locations = definitionService.definitionAt(analysis, position).stream()
                .map(this::toLspLocation)
                .toList();
        return CompletableFuture.completedFuture(Either.forLeft(locations));
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        String uri = params.getTextDocument().getUri();
        if (documentStore.get(uri).isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        IlimapAnalysis analysis = documentStore.analyze(uri, fastOptions());
        IlimapIdePosition position = new IlimapIdePosition(
                params.getPosition().getLine(), params.getPosition().getCharacter());
        return CompletableFuture.completedFuture(
                hoverService.hoverAt(analysis, position).map(this::toLspHover).orElse(null));
    }

    public CompletableFuture<IlimapMappingSummary> mappingSummary(IlimapMappingSummaryParams params) {
        if (params == null || params.uri() == null || params.uri().isBlank()) {
            return CompletableFuture.completedFuture(
                    IlimapMappingSummary.unavailable("No ILIMAP document URI provided."));
        }
        String uri = params.uri();
        if (documentStore.get(uri).isEmpty()) {
            return CompletableFuture.completedFuture(
                    IlimapMappingSummary.unavailable("No open ILIMAP document for URI: " + uri));
        }

        IlimapAnalysis analysis = analysisForCompletion(uri);
        return CompletableFuture.completedFuture(mappingSummaryService.summarize(analysis));
    }

    public CompletableFuture<IlimapValidateMappingResult> validateMapping(IlimapValidateMappingParams params) {
        if (params == null || params.uri() == null || params.uri().isBlank()) {
            return CompletableFuture.completedFuture(
                    IlimapValidateMappingResult.unavailable("No ILIMAP document URI provided."));
        }

        String uri = params.uri();
        if (params.text() != null) {
            documentStore.updateFull(uri, params.text(), params.version() != null ? params.version() : 0);
        } else if (documentStore.get(uri).isEmpty()) {
            return CompletableFuture.completedFuture(
                    IlimapValidateMappingResult.unavailable("No open ILIMAP document for URI: " + uri));
        }

        return analyzeModelAwareAndPublish(uri)
                .thenApply(analysis -> IlimapValidateMappingResult.available(
                        analysis.diagnostics().size()))
                .exceptionally(error -> IlimapValidateMappingResult.unavailable(
                        "Failed to validate ILIMAP document: " + errorMessage(error)));
    }

    public void invalidateModelCache() {
        runningModelAnalyses.clear();
        documentStore.invalidateModelCache();
    }

    private IlimapAnalysis analyzeAndPublishFast(String uri) {
        IlimapAnalysis analysis = documentStore.analyze(uri, fastOptions());
        Integer version =
                documentStore.get(uri).map(IlimapDocumentSnapshot::version).orElse(null);
        publish(uri, version, diagnosticMapper.map(analysis.diagnostics()));
        return analysis;
    }

    private CompletableFuture<IlimapAnalysis> analyzeModelAwareAndPublish(String uri) {
        return documentStore
                .get(uri)
                .map(snapshot -> {
                    CompletableFuture<IlimapAnalysis> previous = runningModelAnalyses.remove(uri);
                    if (previous != null) {
                        previous.cancel(true);
                    }
                    CompletableFuture<IlimapAnalysis> future =
                            CompletableFuture.supplyAsync(() -> documentStore.analyze(snapshot, modelAwareOptions()));
                    runningModelAnalyses.put(uri, future);
                    future.whenComplete((analysis, error) -> {
                        runningModelAnalyses.remove(uri, future);
                        if (error == null
                                && documentStore
                                        .get(uri)
                                        .filter(snapshot::equals)
                                        .isPresent()) {
                            publish(uri, snapshot.version(), diagnosticMapper.map(analysis.diagnostics()));
                        }
                    });
                    return future;
                })
                .orElseGet(() -> CompletableFuture.failedFuture(
                        new IllegalArgumentException("No open ILIMAP document for URI: " + uri)));
    }

    private IlimapAnalysis analysisForCompletion(String uri) {
        return documentStore
                .cachedAnalysis(uri, modelAwareOptions())
                .orElseGet(() -> documentStore.analyze(uri, fastOptions()));
    }

    private IlimapAnalysisOptions fastOptions() {
        return IlimapAnalysisOptions.defaults(analysisOptions.baseDirectory());
    }

    private IlimapAnalysisOptions modelAwareOptions() {
        return IlimapAnalysisOptions.modelAware(analysisOptions.baseDirectory());
    }

    private TextEdit toLspTextEdit(IlimapTextEdit edit) {
        return new TextEdit(rangeMapper.toLspRange(edit.range()), edit.newText());
    }

    private CodeAction toLspCodeAction(
            String uri,
            guru.interlis.transformer.mapping.ilimap.ide.IlimapCodeAction action,
            CodeActionContext context) {
        CodeAction codeAction = new CodeAction(action.title());
        codeAction.setKind(action.kind());
        List<TextEdit> edits = action.edits().stream().map(this::toLspTextEdit).toList();
        codeAction.setEdit(new WorkspaceEdit(Map.of(uri, edits)));
        List<Diagnostic> diagnostics = matchingDiagnostics(action.diagnosticCode(), context);
        if (!diagnostics.isEmpty()) {
            codeAction.setDiagnostics(diagnostics);
        }
        return codeAction;
    }

    private IlimapIdeRange toIdeRange(Range range) {
        if (range == null) {
            IlimapIdePosition start = new IlimapIdePosition(0, 0);
            return new IlimapIdeRange(start, start);
        }
        return new IlimapIdeRange(
                new IlimapIdePosition(
                        range.getStart().getLine(), range.getStart().getCharacter()),
                new IlimapIdePosition(range.getEnd().getLine(), range.getEnd().getCharacter()));
    }

    private boolean matchesOnly(String actionKind, CodeActionContext context) {
        if (context == null || context.getOnly() == null || context.getOnly().isEmpty()) {
            return true;
        }
        return context.getOnly().stream()
                .anyMatch(onlyKind -> actionKind.equals(onlyKind) || actionKind.startsWith(onlyKind + "."));
    }

    private List<Diagnostic> matchingDiagnostics(String diagnosticCode, CodeActionContext context) {
        if (diagnosticCode == null || context == null || context.getDiagnostics() == null) {
            return List.of();
        }
        return context.getDiagnostics().stream()
                .filter(diagnostic -> diagnosticCode.equals(diagnosticCode(diagnostic)))
                .toList();
    }

    private String diagnosticCode(Diagnostic diagnostic) {
        if (diagnostic.getCode() == null) {
            return null;
        }
        if (diagnostic.getCode().isLeft()) {
            return diagnostic.getCode().getLeft();
        }
        return String.valueOf(diagnostic.getCode().getRight());
    }

    private Location toLspLocation(IlimapDefinition definition) {
        return new Location(definition.uri(), rangeMapper.toLspRange(definition.range()));
    }

    private Hover toLspHover(IlimapHover hover) {
        return new Hover(
                new MarkupContent(MarkupKind.MARKDOWN, hover.markdown()), rangeMapper.toLspRange(hover.range()));
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

    private static String errorMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() != null
                ? current.getMessage()
                : current.getClass().getSimpleName();
    }
}
