package guru.interlis.transformer.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public record ExtractedTransfer(
        Path transferFile,
        String format,
        Set<String> includedClasses,
        int totalObjects,
        List<String> includedBasketIds,
        String provenance) {}
