package guru.interlis.transformer.dmav.fullrun;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public final class Dm01DmavFullRunManifestLoader {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public Dm01DmavFullRunManifest load(Path manifestPath, Path repositoryRoot) throws IOException {
        Dm01DmavFullRunManifest manifest = yamlMapper.readValue(manifestPath.toFile(), Dm01DmavFullRunManifest.class);
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

    private void validate(Dm01DmavFullRunManifest manifest, Path manifestPath, Path repositoryRoot) {
        requireText(manifest.datasetSlug, "datasetSlug");
        requireText(manifest.description, "description");
        requireText(manifest.direction, "direction");
        requireText(manifest.failPolicy, "failPolicy");

        requireText(manifest.source.pathHint, "source.pathHint");
        requireText(manifest.source.sha256, "source.sha256");
        requireText(manifest.source.model, "source.model");
        requireText(manifest.source.format, "source.format");
        requireText(manifest.output.model, "output.model");
        requireText(manifest.output.format, "output.format");
        requireText(manifest.output.fileName, "output.fileName");
        requireText(manifest.mapping.oidNamespace, "mapping.oidNamespace");
        requireText(manifest.mapping.oidStrategy, "mapping.oidStrategy");
        requireText(manifest.mapping.basketStrategy, "mapping.basketStrategy");
        requireText(manifest.mapping.compileMode, "mapping.compileMode");
        requireText(manifest.report.expectedSummary, "report.expectedSummary");

        if (manifest.modeldirs == null || manifest.modeldirs.isEmpty()) {
            throw new IllegalArgumentException("manifest.modeldirs must not be empty");
        }
        if (manifest.topics == null || manifest.topics.include == null || manifest.topics.include.isEmpty()) {
            throw new IllegalArgumentException("manifest.topics.include must not be empty");
        }

        Path expectedSummaryPath = resolveManifestPath(manifestPath, repositoryRoot, manifest.report.expectedSummary);
        if (!Files.isRegularFile(expectedSummaryPath)) {
            throw new IllegalArgumentException("Expected summary file not found: " + expectedSummaryPath);
        }

        Set<String> seenTopicIds = new LinkedHashSet<>();
        for (Dm01DmavFullRunManifest.TopicMappingSpec topic : manifest.topics.include) {
            requireText(topic.id, "topics.include[].id");
            if (!seenTopicIds.add(topic.id)) {
                throw new IllegalArgumentException("Duplicate included topic id: " + topic.id);
            }
            if ((topic.preferredIlimap == null || topic.preferredIlimap.isBlank())
                    && (topic.fallbackYaml == null || topic.fallbackYaml.isBlank())) {
                throw new IllegalArgumentException(
                        "Included topic " + topic.id + " must define preferredIlimap or fallbackYaml");
            }
            if (topic.preferredIlimap != null && !topic.preferredIlimap.isBlank()) {
                Path ilimapPath = resolveManifestPath(manifestPath, repositoryRoot, topic.preferredIlimap);
                if (!Files.isRegularFile(ilimapPath)) {
                    throw new IllegalArgumentException("preferredIlimap not found for " + topic.id + ": " + ilimapPath);
                }
            }
            if (topic.fallbackYaml != null && !topic.fallbackYaml.isBlank()) {
                Path yamlPath = resolveManifestPath(manifestPath, repositoryRoot, topic.fallbackYaml);
                if (!Files.isRegularFile(yamlPath)) {
                    throw new IllegalArgumentException("fallbackYaml not found for " + topic.id + ": " + yamlPath);
                }
            }
        }

        if (manifest.topics.exclude != null) {
            for (Dm01DmavFullRunManifest.ExcludedTopicSpec topic : manifest.topics.exclude) {
                requireText(topic.id, "topics.exclude[].id");
                requireText(topic.reason, "topics.exclude[].reason");
                if (!seenTopicIds.add(topic.id)) {
                    throw new IllegalArgumentException("Topic id appears in include and exclude: " + topic.id);
                }
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
