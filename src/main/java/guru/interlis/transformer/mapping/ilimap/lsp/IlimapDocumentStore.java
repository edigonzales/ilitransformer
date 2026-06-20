package guru.interlis.transformer.mapping.ilimap.lsp;

import guru.interlis.transformer.mapping.ilimap.ide.IlimapAnalysis;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapAnalysisOptions;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapAnalysisService;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class IlimapDocumentStore {

    private final Map<String, IlimapDocumentSnapshot> documents = new ConcurrentHashMap<>();
    private final IlimapAnalysisService analysisService;

    public IlimapDocumentStore() {
        this(new IlimapAnalysisService());
    }

    IlimapDocumentStore(IlimapAnalysisService analysisService) {
        this.analysisService = Objects.requireNonNull(analysisService, "analysisService");
    }

    public void open(String uri, String text, int version) {
        documents.put(requireUri(uri), new IlimapDocumentSnapshot(uri, requireText(text), version));
    }

    public void updateFull(String uri, String text, int version) {
        documents.put(requireUri(uri), new IlimapDocumentSnapshot(uri, requireText(text), version));
    }

    public void close(String uri) {
        documents.remove(requireUri(uri));
    }

    public Optional<IlimapDocumentSnapshot> get(String uri) {
        return Optional.ofNullable(documents.get(requireUri(uri)));
    }

    public IlimapAnalysis analyze(String uri, IlimapAnalysisOptions options) {
        IlimapDocumentSnapshot snapshot =
                get(uri).orElseThrow(() -> new IllegalArgumentException("No open ILIMAP document for URI: " + uri));
        return analysisService.analyze(snapshot.uri(), snapshot.text(), options);
    }

    private static String requireUri(String uri) {
        return Objects.requireNonNull(uri, "uri");
    }

    private static String requireText(String text) {
        return Objects.requireNonNull(text, "text");
    }
}
