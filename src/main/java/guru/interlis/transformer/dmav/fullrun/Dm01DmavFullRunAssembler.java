package guru.interlis.transformer.dmav.fullrun;

import guru.interlis.transformer.bundle.BundleAssembler;
import guru.interlis.transformer.bundle.BundleManifest;
import guru.interlis.transformer.mapping.model.JobConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Dm01DmavFullRunAssembler {

    private final BundleAssembler bundleAssembler = new BundleAssembler();

    public AssembledFullRun assemble(
            Dm01DmavFullRunManifest manifest, Path manifestPath, Path repositoryRoot, Path sourcePath, Path outputPath)
            throws Exception {
        BundleManifest bundle = toBundleManifest(manifest);

        BundleAssembler.AssembledBundle assembled =
                bundleAssembler.assemble(bundle, manifestPath, repositoryRoot, sourcePath, outputPath);

        List<LoadedTopic> loadedTopics = new ArrayList<>();
        for (BundleAssembler.AssembledModule module : assembled.modules()) {
            loadedTopics.add(new LoadedTopic(
                    module.moduleId(),
                    module.mappingPath(),
                    module.format(),
                    module.enumRenames(),
                    module.ruleCount()));
        }

        return new AssembledFullRun(assembled.combinedConfig(), loadedTopics, assembled.conflictingEnumNames());
    }

    static BundleManifest toBundleManifest(Dm01DmavFullRunManifest manifest) {
        BundleManifest bundle = new BundleManifest();
        bundle.name = manifest.direction + "-" + manifest.datasetSlug + "-all";
        bundle.description = manifest.description;
        bundle.direction = manifest.direction;
        bundle.failPolicy = manifest.failPolicy;
        bundle.modeldirs = manifest.modeldirs;

        bundle.source.id = manifest.source.inputId;
        bundle.source.pathHint = manifest.source.pathHint;
        bundle.source.sha256 = manifest.source.sha256;
        bundle.source.model = manifest.source.model;
        bundle.source.format = manifest.source.format;

        bundle.output.id = manifest.output.outputId;
        bundle.output.model = manifest.output.model;
        bundle.output.format = manifest.output.format;
        bundle.output.fileName = manifest.output.fileName;

        bundle.mapping.oidStrategy = manifest.mapping.oidStrategy;
        bundle.mapping.oidNamespace = manifest.mapping.oidNamespace;
        bundle.mapping.basketStrategy = manifest.mapping.basketStrategy;
        bundle.mapping.compileMode = manifest.mapping.compileMode;

        bundle.expectedSummary = manifest.report.expectedSummary;

        List<BundleManifest.MappingModule> modules = new ArrayList<>();
        for (Dm01DmavFullRunManifest.TopicMappingSpec topic : manifest.topics.include) {
            BundleManifest.MappingModule module = new BundleManifest.MappingModule();
            module.id = topic.id;
            module.mapping = topic.mapping;
            modules.add(module);
        }
        bundle.modules = modules;
        return bundle;
    }

    public record LoadedTopic(
            String topicId, Path mappingPath, String format, Map<String, String> enumRenames, int ruleCount) {}

    public record AssembledFullRun(
            JobConfig combinedConfig, List<LoadedTopic> loadedTopics, List<String> conflictingEnumNames) {}
}
