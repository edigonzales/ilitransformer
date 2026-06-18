package guru.interlis.transformer.mapping.model;

import java.nio.file.Path;
import java.util.Locale;

public final class MappingFormatDetector {

    public MappingFormat detect(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".ilimap")) {
            return MappingFormat.ILIMAP;
        }
        if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
            return MappingFormat.YAML;
        }
        throw new IllegalArgumentException("Unsupported mapping file extension: " + path);
    }
}
