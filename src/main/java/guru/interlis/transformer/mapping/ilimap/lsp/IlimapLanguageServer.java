package guru.interlis.transformer.mapping.ilimap.lsp;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
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
        ServerCapabilities capabilities = new ServerCapabilities();
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
        capabilities.setHoverProvider(false);
        capabilities.setDefinitionProvider(false);
        capabilities.setDocumentFormattingProvider(true);
        capabilities.setDocumentSymbolProvider(true);
        capabilities.setFoldingRangeProvider(true);
        return CompletableFuture.completedFuture(new InitializeResult(capabilities));
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

    @Override
    public void connect(LanguageClient client) {
        textDocumentService.connect(client);
    }
}
