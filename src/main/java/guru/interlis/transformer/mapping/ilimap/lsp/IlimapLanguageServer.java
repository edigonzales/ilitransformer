package guru.interlis.transformer.mapping.ilimap.lsp;

import guru.interlis.transformer.mapping.ilimap.ide.IlimapAnalysisOptions;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapMappingSummary;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapMappingSummaryParams;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionOptions;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

public final class IlimapLanguageServer implements LanguageServer, LanguageClientAware {

    private final IlimapTextDocumentService textDocumentService;
    private final IlimapWorkspaceService workspaceService;
    private int shutdown;

    public IlimapLanguageServer() {
        this(new IlimapTextDocumentService(), new IlimapWorkspaceService());
    }

    IlimapLanguageServer(IlimapTextDocumentService textDocumentService, IlimapWorkspaceService workspaceService) {
        this.textDocumentService = textDocumentService;
        this.workspaceService = workspaceService;
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        textDocumentService.setAnalysisOptions(IlimapAnalysisOptions.modelAware(workspaceRoot(params)));
        ServerCapabilities capabilities = new ServerCapabilities();
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
        capabilities.setHoverProvider(true);
        capabilities.setDefinitionProvider(true);
        capabilities.setDocumentFormattingProvider(true);
        capabilities.setDocumentSymbolProvider(true);
        capabilities.setFoldingRangeProvider(true);
        capabilities.setCodeActionProvider(new CodeActionOptions(List.of(CodeActionKind.QuickFix, CodeActionKind.Source)));
        CompletionOptions completionOptions = new CompletionOptions();
        completionOptions.setResolveProvider(false);
        capabilities.setCompletionProvider(completionOptions);
        return CompletableFuture.completedFuture(new InitializeResult(capabilities));
    }

    private static Path workspaceRoot(InitializeParams params) {
        return workspaceRootUri(params)
                .map(IlimapLanguageServer::pathFromFileUri)
                .flatMap(path -> path)
                .orElseGet(() -> Path.of(".").toAbsolutePath().normalize());
    }

    private static Optional<String> workspaceRootUri(InitializeParams params) {
        if (params != null && params.getWorkspaceFolders() != null && !params.getWorkspaceFolders().isEmpty()) {
            return Optional.ofNullable(params.getWorkspaceFolders().get(0).getUri());
        }
        if (params != null && params.getRootUri() != null && !params.getRootUri().isBlank()) {
            return Optional.of(params.getRootUri());
        }
        return Optional.empty();
    }

    private static Optional<Path> pathFromFileUri(String uri) {
        try {
            URI parsed = URI.create(uri);
            if (!"file".equals(parsed.getScheme())) {
                return Optional.empty();
            }
            return Optional.of(Path.of(parsed).toAbsolutePath().normalize());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        shutdown = 1;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        if (shutdown == 0) {
            System.exit(1);
        }
        System.exit(0);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    @JsonRequest(value = "ilimap/mappingSummary", useSegment = false)
    public CompletableFuture<IlimapMappingSummary> mappingSummary(IlimapMappingSummaryParams params) {
        return textDocumentService.mappingSummary(params);
    }

    @Override
    public void connect(LanguageClient client) {
        textDocumentService.connect(client);
    }
}
