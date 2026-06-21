package guru.interlis.transformer.bundle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public final class BundleManifestLoader {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public BundleManifest load(Path manifestPath, Path repositoryRoot) throws IOException {
        BundleManifest manifest = yamlMapper.readValue(manifestPath.toFile(), BundleManifest.class);
        validate(manifest, manifestPath, repositoryRoot);
        return manifest;
    }

    public Path resolveManifestPath(Path manifestPath, Path repositoryRoot, String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        Path candidate = Path.of(rawPath);
        if (candidate.isAbsolute()) {
            return candidate.normalize();
        }
        if (isManifestRelative(rawPath)) {
            return manifestPath.toAbsolutePath().getParent().resolve(candidate).normalize();
        }

        return repositoryRoot.toAbsolutePath().resolve(candidate).normalize();
    }

    private void validate(BundleManifest manifest, Path manifestPath, Path repositoryRoot) {
        requireText(manifest.name, "name");
        requireText(manifest.direction, "direction");
        requireText(manifest.failPolicy, "failPolicy");

        requireText(manifest.source.pathHint, "source.pathHint");
        requireText(manifest.source.model, "source.model");
        requireText(manifest.source.format, "source.format");
        requireText(manifest.output.model, "output.model");
        requireText(manifest.output.format, "output.format");
        requireText(manifest.output.fileName, "output.fileName");
        requireText(manifest.mapping.oidStrategy, "mapping.oidStrategy");
        requireText(manifest.mapping.basketStrategy, "mapping.basketStrategy");
        requireText(manifest.mapping.compileMode, "mapping.compileMode");

        if (manifest.modeldirs == null || manifest.modeldirs.isEmpty()) {
            throw new IllegalArgumentException("manifest.modeldirs must not be empty");
        }
        if (manifest.modules == null || manifest.modules.isEmpty()) {
            throw new IllegalArgumentException("manifest.modules must not be empty");
        }

        if (manifest.expectedSummary != null && !manifest.expectedSummary.isBlank()) {
            Path expectedSummaryPath = resolveManifestPath(manifestPath, repositoryRoot, manifest.expectedSummary);
            if (!Files.isRegularFile(expectedSummaryPath)) {
                throw new IllegalArgumentException("Expected summary file not found: " + expectedSummaryPath);
            }
        }

        Set<String> seenModuleIds = new LinkedHashSet<>();
        for (BundleManifest.MappingModule module : manifest.modules) {
            requireText(module.id, "modules[].id");
            if (!seenModuleIds.add(module.id)) {
                throw new IllegalArgumentException("Duplicate module id: " + module.id);
            }
            requireText(module.mapping, "modules[].mapping");
            Path mappingPath = resolveManifestPath(manifestPath, repositoryRoot, module.mapping);
            if (!Files.isRegularFile(mappingPath)) {
                throw new IllegalArgumentException("mapping not found for module " + module.id + ": " + mappingPath);
            }
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Manifest field must not be blank: " + fieldName);
        }
    }

    private static boolean isManifestRelative(String rawPath) {
        return rawPath.startsWith("./") || rawPath.startsWith("../");
    }
}
