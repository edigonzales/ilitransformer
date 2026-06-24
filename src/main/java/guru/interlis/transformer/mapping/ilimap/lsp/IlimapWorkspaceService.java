package guru.interlis.transformer.mapping.ilimap.lsp;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.services.WorkspaceService;

public final class IlimapWorkspaceService implements WorkspaceService {

    private final Runnable modelCacheInvalidator;

    public IlimapWorkspaceService() {
        this(() -> {});
    }

    IlimapWorkspaceService(Runnable modelCacheInvalidator) {
        this.modelCacheInvalidator = modelCacheInvalidator;
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        // No workspace configuration in the diagnostics-only MVP.
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        if (params != null
                && params.getChanges() != null
                && params.getChanges().stream()
                        .anyMatch(change ->
                                change.getUri() != null && change.getUri().endsWith(".ili"))) {
            modelCacheInvalidator.run();
        }
    }
}
