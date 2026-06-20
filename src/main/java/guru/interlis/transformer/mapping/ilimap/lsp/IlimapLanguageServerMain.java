package guru.interlis.transformer.mapping.ilimap.lsp;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

public final class IlimapLanguageServerMain {

    private IlimapLanguageServerMain() {}

    public static void main(String[] args) throws Exception {
        IlimapLanguageServer server = new IlimapLanguageServer();
        Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, System.in, System.out);
        server.connect(launcher.getRemoteProxy());
        launcher.startListening().get();
    }
}
