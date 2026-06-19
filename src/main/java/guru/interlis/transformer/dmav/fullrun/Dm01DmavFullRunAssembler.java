package guru.interlis.transformer.dmav.fullrun;

import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.mapping.model.JobConfigNormalizer;
import guru.interlis.transformer.mapping.model.MappingLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class Dm01DmavFullRunAssembler {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final MappingLoader mappingLoader = new MappingLoader();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AssembledFullRun assemble(
            Dm01DmavFullRunManifest manifest, Path manifestPath, Path repositoryRoot, Path sourcePath, Path outputPath)
            throws Exception {
        Dm01DmavFullRunManifestLoader manifestLoader = new Dm01DmavFullRunManifestLoader();

        Map<String, JobConfig> loadedConfigs = new LinkedHashMap<>();
        List<LoadedTopic> loadedTopics = new ArrayList<>();

        for (Dm01DmavFullRunManifest.TopicMappingSpec topic : manifest.topics.include) {
            Path mappingPath =
                    manifestLoader.resolveManifestPath(manifestPath, repositoryRoot, topic.effectiveMappingReference());
            JobConfig config = mappingLoader.load(mappingPath);
            loadedConfigs.put(topic.id, config);
            loadedTopics.add(new LoadedTopic(topic.id, mappingPath, topic.effectiveFormat(), Map.of(), 0));
        }

        Map<String, Map<String, Map<String, String>>> enumVariants = collectEnumVariants(loadedConfigs);
        Set<String> conflictingEnumNames = detectConflictingEnumNames(enumVariants);
        Map<String, Map<String, String>> enumRenamesByTopic =
                buildEnumRenames(manifest.topics.include, conflictingEnumNames, loadedConfigs);

        Map<String, Map<String, String>> mergedEnums = new LinkedHashMap<>();
        Map<String, String> mergedDefaults = new LinkedHashMap<>();
        List<JobConfig.RuleSpec> mergedRules = new ArrayList<>();
        List<LoadedTopic> finalizedTopics = new ArrayList<>();

        for (Dm01DmavFullRunManifest.TopicMappingSpec topic : manifest.topics.include) {
            JobConfig originalConfig = loadedConfigs.get(topic.id);
            Map<String, String> enumRenames = enumRenamesByTopic.getOrDefault(topic.id, Map.of());
            JobConfig transformedConfig = renameTopicConfig(topic.id, originalConfig, enumRenames);

            mergeEnums(mergedEnums, transformedConfig.mapping.enums);
            mergeDefaults(mergedDefaults, transformedConfig.mapping.defaults, topic.id);
            mergedRules.addAll(transformedConfig.mapping.rules);

            Path mappingPath =
                    manifestLoader.resolveManifestPath(manifestPath, repositoryRoot, topic.effectiveMappingReference());
            finalizedTopics.add(new LoadedTopic(
                    topic.id,
                    mappingPath,
                    topic.effectiveFormat(),
                    enumRenames,
                    transformedConfig.mapping.rules.size()));
        }

        JobConfig combined = new JobConfig();
        combined.version = 1;
        combined.job.name = "dm01-to-dmav-" + manifest.datasetSlug + "-all";
        combined.job.description = manifest.description;
        combined.job.direction = manifest.direction;
        combined.job.failPolicy = manifest.failPolicy;
        combined.job.modeldir = absolutizeModeldirs(manifest.modeldirs, manifestPath, repositoryRoot);
        combined.job.inputs = List.of(buildInput(manifest, manifestPath, repositoryRoot, sourcePath));
        combined.job.outputs = List.of(buildOutput(manifest, outputPath));

        combined.mapping.compileMode = manifest.mapping.compileMode;
        combined.mapping.oidStrategy.defaultStrategy = manifest.mapping.oidStrategy;
        combined.mapping.oidStrategy.namespace = manifest.mapping.oidNamespace;
        combined.mapping.basketStrategy.defaultStrategy = manifest.mapping.basketStrategy;
        combined.mapping.enums = mergedEnums.isEmpty() ? null : mergedEnums;
        combined.mapping.defaults = mergedDefaults.isEmpty() ? null : mergedDefaults;
        combined.mapping.rules = mergedRules;
        JobConfigNormalizer.normalize(combined);

        return new AssembledFullRun(combined, finalizedTopics, new ArrayList<>(conflictingEnumNames));
    }

    private Map<String, Map<String, Map<String, String>>> collectEnumVariants(Map<String, JobConfig> loadedConfigs) {
        Map<String, Map<String, Map<String, String>>> enumVariants = new LinkedHashMap<>();
        for (Map.Entry<String, JobConfig> entry : loadedConfigs.entrySet()) {
            String topicId = entry.getKey();
            Map<String, Map<String, String>> enums = entry.getValue().mapping.enums;
            if (enums == null) {
                continue;
            }
            for (Map.Entry<String, Map<String, String>> enumEntry : enums.entrySet()) {
                enumVariants
                        .computeIfAbsent(enumEntry.getKey(), ignored -> new LinkedHashMap<>())
                        .put(topicId, enumEntry.getValue());
            }
        }
        return enumVariants;
    }

    private Set<String> detectConflictingEnumNames(Map<String, Map<String, Map<String, String>>> enumVariants) {
        Set<String> conflicts = new LinkedHashSet<>();
        for (Map.Entry<String, Map<String, Map<String, String>>> entry : enumVariants.entrySet()) {
            Map<String, String> firstMap = null;
            for (Map<String, String> candidate : entry.getValue().values()) {
                if (firstMap == null) {
                    firstMap = candidate;
                    continue;
                }
                if (!Objects.equals(firstMap, candidate)) {
                    conflicts.add(entry.getKey());
                    break;
                }
            }
        }
        return conflicts;
    }

    private Map<String, Map<String, String>> buildEnumRenames(
            List<Dm01DmavFullRunManifest.TopicMappingSpec> topics,
            Set<String> conflictingEnumNames,
            Map<String, JobConfig> loadedConfigs) {
        Map<String, Map<String, String>> renamesByTopic = new LinkedHashMap<>();
        for (Dm01DmavFullRunManifest.TopicMappingSpec topic : topics) {
            Map<String, String> renames = new LinkedHashMap<>();
            JobConfig config = loadedConfigs.get(topic.id);
            if (config.mapping.enums != null) {
                for (String enumName : config.mapping.enums.keySet()) {
                    if (conflictingEnumNames.contains(enumName)) {
                        renames.put(enumName, topic.id + "_" + enumName);
                    }
                }
            }
            renamesByTopic.put(topic.id, renames);
        }
        return renamesByTopic;
    }

    private JobConfig renameTopicConfig(String topicId, JobConfig config, Map<String, String> enumRenames) {
        @SuppressWarnings("unchecked")
        LinkedHashMap<String, Object> configMap = objectMapper.convertValue(config, MAP_TYPE);

        @SuppressWarnings("unchecked")
        Map<String, Object> mapping = (Map<String, Object>) configMap.get("mapping");

        @SuppressWarnings("unchecked")
        Map<String, Object> enumMap =
                mapping.get("enums") instanceof Map<?, ?> rawEnums ? (Map<String, Object>) rawEnums : null;
        if (enumMap != null && !enumMap.isEmpty()) {
            Map<String, Object> renamedEnums = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : enumMap.entrySet()) {
                renamedEnums.put(enumRenames.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue());
            }
            mapping.put("enums", renamedEnums);
        }

        @SuppressWarnings("unchecked")
        List<Object> rawRules = mapping.get("rules") instanceof List<?> list ? (List<Object>) list : List.of();
        Map<String, String> ruleRenames = new LinkedHashMap<>();
        for (Object rawRule : rawRules) {
            @SuppressWarnings("unchecked")
            Map<String, Object> ruleMap = (Map<String, Object>) rawRule;
            String originalId = String.valueOf(ruleMap.get("id"));
            ruleRenames.put(originalId, topicId + "-" + originalId);
        }

        List<Object> rewrittenRules = new ArrayList<>();
        for (Object rawRule : rawRules) {
            @SuppressWarnings("unchecked")
            Map<String, Object> ruleMap = (Map<String, Object>) rawRule;
            Map<String, Object> copy = new LinkedHashMap<>(ruleMap);
            String originalId = String.valueOf(copy.get("id"));
            copy.put("id", ruleRenames.get(originalId));
            @SuppressWarnings("unchecked")
            Map<String, Object> rewritten = (Map<String, Object>) rewriteValue(copy, ruleRenames, enumRenames);
            rewrittenRules.add(rewritten);
        }
        mapping.put("rules", rewrittenRules);

        JobConfig renamed = objectMapper.convertValue(configMap, JobConfig.class);
        JobConfigNormalizer.normalize(renamed);
        return renamed;
    }

    private Object rewriteValue(Object value, Map<String, String> ruleRenames, Map<String, String> enumRenames) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> rewritten = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                rewritten.put(String.valueOf(entry.getKey()), rewriteValue(entry.getValue(), ruleRenames, enumRenames));
            }
            rewriteRuleReference(rewritten, "rule", ruleRenames);
            rewriteRuleReference(rewritten, "targetRule", ruleRenames);
            return rewritten;
        }
        if (value instanceof List<?> rawList) {
            List<Object> rewritten = new ArrayList<>(rawList.size());
            for (Object child : rawList) {
                rewritten.add(rewriteValue(child, ruleRenames, enumRenames));
            }
            return rewritten;
        }
        if (value instanceof String text && text.contains("enumMap(") && !enumRenames.isEmpty()) {
            String rewritten = text;
            for (Map.Entry<String, String> entry : enumRenames.entrySet()) {
                rewritten = rewritten.replace("'" + entry.getKey() + "'", "'" + entry.getValue() + "'");
                rewritten = rewritten.replace("\"" + entry.getKey() + "\"", "\"" + entry.getValue() + "\"");
            }
            return rewritten;
        }
        return value;
    }

    private void rewriteRuleReference(Map<String, Object> rewritten, String key, Map<String, String> ruleRenames) {
        Object value = rewritten.get(key);
        if (value instanceof String text && ruleRenames.containsKey(text)) {
            rewritten.put(key, ruleRenames.get(text));
        }
    }

    private void mergeEnums(
            Map<String, Map<String, String>> mergedEnums, Map<String, Map<String, String>> incomingEnums) {
        if (incomingEnums == null) {
            return;
        }
        for (Map.Entry<String, Map<String, String>> entry : incomingEnums.entrySet()) {
            Map<String, String> existing = mergedEnums.get(entry.getKey());
            if (existing != null && !existing.equals(entry.getValue())) {
                throw new IllegalStateException("Conflicting enum map after renaming: " + entry.getKey());
            }
            mergedEnums.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    private void mergeDefaults(
            Map<String, String> mergedDefaults, Map<String, String> incomingDefaults, String topicId) {
        if (incomingDefaults == null) {
            return;
        }
        for (Map.Entry<String, String> entry : incomingDefaults.entrySet()) {
            String existing = mergedDefaults.get(entry.getKey());
            if (existing != null && !existing.equals(entry.getValue())) {
                throw new IllegalStateException(
                        "Conflicting mapping.defaults entry for " + entry.getKey() + " in topic " + topicId);
            }
            mergedDefaults.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    private List<String> absolutizeModeldirs(List<String> modeldirs, Path manifestPath, Path repositoryRoot) {
        Dm01DmavFullRunManifestLoader manifestLoader = new Dm01DmavFullRunManifestLoader();
        List<String> resolved = new ArrayList<>(modeldirs.size());
        for (String modeldir : modeldirs) {
            if (modeldir.contains("://")) {
                resolved.add(modeldir);
            } else {
                resolved.add(manifestLoader
                        .resolveManifestPath(manifestPath, repositoryRoot, modeldir)
                        .toString());
            }
        }
        return resolved;
    }

    private JobConfig.InputSpec buildInput(
            Dm01DmavFullRunManifest manifest, Path manifestPath, Path repositoryRoot, Path sourcePath) {
        JobConfig.InputSpec input = new JobConfig.InputSpec();
        input.id = manifest.source.inputId;
        input.path = sourcePath.toAbsolutePath().normalize().toString();
        input.model = manifest.source.model;
        input.format = manifest.source.format;
        return input;
    }

    private JobConfig.OutputSpec buildOutput(Dm01DmavFullRunManifest manifest, Path outputPath) {
        JobConfig.OutputSpec output = new JobConfig.OutputSpec();
        output.id = manifest.output.outputId;
        output.path = outputPath.toAbsolutePath().normalize().toString();
        output.model = manifest.output.model;
        output.format = manifest.output.format;
        return output;
    }

    public record LoadedTopic(
            String topicId, Path mappingPath, String format, Map<String, String> enumRenames, int ruleCount) {}

    public record AssembledFullRun(
            JobConfig combinedConfig, List<LoadedTopic> loadedTopics, List<String> conflictingEnumNames) {}
}
