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
    private final Map<AnalysisCacheKey, IlimapAnalysis> analysisCache = new ConcurrentHashMap<>();
    private final IlimapAnalysisService analysisService;

    public IlimapDocumentStore() {
        this(new IlimapAnalysisService());
    }

    IlimapDocumentStore(IlimapAnalysisService analysisService) {
        this.analysisService = Objects.requireNonNull(analysisService, "analysisService");
    }

    public void open(String uri, String text, int version) {
        String requiredUri = requireUri(uri);
        invalidate(requiredUri);
        documents.put(requiredUri, new IlimapDocumentSnapshot(requiredUri, requireText(text), version));
    }

    public void updateFull(String uri, String text, int version) {
        String requiredUri = requireUri(uri);
        invalidate(requiredUri);
        documents.put(requiredUri, new IlimapDocumentSnapshot(requiredUri, requireText(text), version));
    }

    public void close(String uri) {
        String requiredUri = requireUri(uri);
        documents.remove(requiredUri);
        invalidate(requiredUri);
    }

    public Optional<IlimapDocumentSnapshot> get(String uri) {
        return Optional.ofNullable(documents.get(requireUri(uri)));
    }

    public IlimapAnalysis analyze(String uri, IlimapAnalysisOptions options) {
        IlimapDocumentSnapshot snapshot =
                get(uri).orElseThrow(() -> new IllegalArgumentException("No open ILIMAP document for URI: " + uri));
        return analyze(snapshot, options);
    }

    public IlimapAnalysis analyze(IlimapDocumentSnapshot snapshot, IlimapAnalysisOptions options) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(options, "options");
        AnalysisCacheKey key = AnalysisCacheKey.from(snapshot, options);
        return analysisCache.computeIfAbsent(
                key, ignored -> analysisService.analyze(snapshot.uri(), snapshot.text(), options));
    }

    public Optional<IlimapAnalysis> cachedAnalysis(String uri, IlimapAnalysisOptions options) {
        return get(uri).map(snapshot -> analysisCache.get(AnalysisCacheKey.from(snapshot, options)));
    }

    public void invalidateModelCache() {
        analysisService.invalidateModelCache();
        analysisCache.keySet().removeIf(AnalysisCacheKey::includeModelDiagnostics);
    }

    private void invalidate(String uri) {
        analysisCache.keySet().removeIf(key -> key.uri().equals(uri));
    }

    private static String requireUri(String uri) {
        return Objects.requireNonNull(uri, "uri");
    }

    private static String requireText(String text) {
        return Objects.requireNonNull(text, "text");
    }

    private record AnalysisCacheKey(
            String uri,
            int version,
            java.nio.file.Path baseDirectory,
            boolean includeSemanticDiagnostics,
            boolean includeExpressionDiagnostics,
            boolean includeModelDiagnostics) {

        static AnalysisCacheKey from(IlimapDocumentSnapshot snapshot, IlimapAnalysisOptions options) {
            return new AnalysisCacheKey(
                    snapshot.uri(),
                    snapshot.version(),
                    options.baseDirectory().toAbsolutePath().normalize(),
                    options.includeSemanticDiagnostics(),
                    options.includeExpressionDiagnostics(),
                    options.includeModelDiagnostics());
        }
    }
}
