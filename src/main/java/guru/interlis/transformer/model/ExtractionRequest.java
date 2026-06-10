package guru.interlis.transformer.model;

import java.nio.file.Path;
import java.util.List;

public record ExtractionRequest(
        List<String> targetClasses,
        List<String> modelDirs,
        int maxDepth,
        int maxObjects,
        boolean includeBidirectional,
        Path targetDir
) {
    public static ExtractionRequest lfp3Default(Path targetDir) {
        return new ExtractionRequest(
                List.of("LFP3Nachfuehrung", "LFP3", "LFP3Pos", "LFP3Symbol"),
                List.of(),
                2,
                200,
                true,
                targetDir
        );
    }
}
