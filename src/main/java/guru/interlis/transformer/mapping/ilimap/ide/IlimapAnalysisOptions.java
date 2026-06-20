package guru.interlis.transformer.mapping.ilimap.ide;

import java.nio.file.Path;
import java.util.Objects;

public record IlimapAnalysisOptions(
        Path baseDirectory,
        boolean includeSemanticDiagnostics,
        boolean includeExpressionDiagnostics,
        boolean includeModelDiagnostics) {

    public IlimapAnalysisOptions {
        Objects.requireNonNull(baseDirectory, "baseDirectory");
    }

    public static IlimapAnalysisOptions defaults(Path baseDirectory) {
        return new IlimapAnalysisOptions(baseDirectory, true, false, false);
    }
}
