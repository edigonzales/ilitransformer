package guru.interlis.transformer.io;

import guru.interlis.transformer.mapping.model.JobConfig;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolves a lower-case format id for an input/output specification.
 *
 * <p>An explicit {@code format} always wins. Otherwise the id is derived from the file extension.
 * Formats without a path (such as {@code jdbc}) must declare {@code format} explicitly.
 *
 * <p>Prepared for the upcoming format work; phase 1 keeps {@code ModelRegistry} unchanged.
 */
public final class FormatIdResolver {

    private FormatIdResolver() {}

    public static String resolveInputFormat(JobConfig.InputSpec input) {
        if (input == null) {
            return null;
        }
        if (input.format != null && !input.format.isBlank()) {
            return input.format.trim().toLowerCase(Locale.ROOT);
        }
        return input.path != null ? fromPath(Path.of(input.path)).orElse(null) : null;
    }

    public static String resolveOutputFormat(JobConfig.OutputSpec output) {
        if (output == null) {
            return null;
        }
        if (output.format != null && !output.format.isBlank()) {
            return output.format.trim().toLowerCase(Locale.ROOT);
        }
        return output.path != null ? fromPath(Path.of(output.path)).orElse(null) : null;
    }

    public static Optional<String> fromPath(Path path) {
        if (path == null) {
            return Optional.empty();
        }
        String lowerName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".xtf")) {
            return Optional.of("xtf");
        }
        if (lowerName.endsWith(".xml")) {
            return Optional.of("xml");
        }
        if (lowerName.endsWith(".itf")) {
            return Optional.of("itf");
        }
        if (lowerName.endsWith(".csv")) {
            return Optional.of("csv");
        }
        if (lowerName.endsWith(".gpkg")) {
            return Optional.of("gpkg");
        }
        if (lowerName.endsWith(".shp")) {
            return Optional.of("shp");
        }
        return Optional.empty();
    }
}
