package guru.interlis.transformer.testutil;

import java.nio.file.Path;
import java.util.List;

public record TransferDatasetDescriptor(
        String id,
        Path transferFile,
        TransferFormat format,
        List<String> declaredModels,
        List<String> modelDirectories,
        long sizeBytes) {}
