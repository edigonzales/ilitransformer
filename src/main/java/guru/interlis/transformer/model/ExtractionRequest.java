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
) {}
