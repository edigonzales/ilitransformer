package guru.interlis.transformer.dmav.fullrun;

import guru.interlis.transformer.bundle.BundleManifest;
import guru.interlis.transformer.bundle.BundleRunner;
import guru.interlis.transformer.dmav.Dm01DmavPaths;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public final class Dm01DmavFullRunTask {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private Dm01DmavFullRunTask() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseArgs(args);
        if (!options.containsKey("--manifest")) {
            throw new IllegalArgumentException(
                    "Usage: --manifest <path> [--source <path>] [--report-dir <path>] [--output <path>] [--repo-root <path>]");
        }

        Path repositoryRoot = options.containsKey("--repo-root")
                ? Path.of(options.get("--repo-root")).toAbsolutePath().normalize()
                : Path.of("").toAbsolutePath().normalize();
        Path manifestPath = Path.of(options.get("--manifest")).toAbsolutePath().normalize();

        Dm01DmavFullRunManifestLoader manifestLoader = new Dm01DmavFullRunManifestLoader();
        Dm01DmavFullRunManifest manifest = manifestLoader.load(manifestPath, repositoryRoot);

        Path sourcePath =
                resolveSourcePath(options.get("--source"), manifest, manifestPath, repositoryRoot, manifestLoader);

        Path reportDir = options.containsKey("--report-dir")
                ? Path.of(options.get("--report-dir")).toAbsolutePath().normalize()
                : repositoryRoot
                        .resolve("build/reports/dm01-dmav/full-runs/" + manifest.datasetSlug)
                        .normalize();

        Path outputPath = options.containsKey("--output")
                ? Path.of(options.get("--output")).toAbsolutePath().normalize()
                : reportDir.resolve(manifest.output.fileName).toAbsolutePath().normalize();

        BundleManifest bundle = Dm01DmavFullRunAssembler.toBundleManifest(manifest);
        BundleRunner.BundleRunResult result =
                new BundleRunner().run(bundle, manifestPath, repositoryRoot, sourcePath, reportDir, outputPath, true);

        Dm01DmavFullRunSummaryNormalizer normalizer = new Dm01DmavFullRunSummaryNormalizer();
        Dm01DmavFullRunSummary actualSummary = normalizer.normalize(result.reportJson(), manifest);
        Path normalizedSummary = reportDir.resolve("normalized-summary.yaml");
        normalizer.writeSummary(normalizedSummary, actualSummary);

        Path expectedSummaryPath =
                manifestLoader.resolveManifestPath(manifestPath, repositoryRoot, manifest.report.expectedSummary);
        Dm01DmavFullRunSummary expectedSummary = normalizer.readSummary(expectedSummaryPath);
        if (!summariesMatch(expectedSummary, actualSummary)) {
            throw new IllegalStateException("Normalized summary mismatch. Expected "
                    + expectedSummaryPath
                    + " but wrote actual summary to "
                    + normalizedSummary);
        }

        System.out.println("Manifest: " + manifestPath);
        System.out.println("Source: " + sourcePath);
        System.out.println("Output: " + outputPath);
        System.out.println("Generated mapping: " + result.combinedMappingPath());
        System.out.println("Report directory: " + reportDir);
        System.out.println("Normalized summary: " + normalizedSummary);
        System.out.println(
                "Bundle root: " + repositoryRoot.resolve(Dm01DmavPaths.fullRunBundleDir(manifest.datasetSlug)));
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if (!key.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + key);
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for " + key);
            }
            options.put(key, args[++i]);
        }
        return options;
    }

    private static Path resolveSourcePath(
            String explicitSource,
            Dm01DmavFullRunManifest manifest,
            Path manifestPath,
            Path repositoryRoot,
            Dm01DmavFullRunManifestLoader manifestLoader) {
        String rawPath =
                explicitSource != null && !explicitSource.isBlank() ? explicitSource : manifest.source.pathHint;
        return manifestLoader
                .resolveManifestPath(manifestPath, repositoryRoot, rawPath)
                .toAbsolutePath()
                .normalize();
    }

    private static boolean summariesMatch(Dm01DmavFullRunSummary expected, Dm01DmavFullRunSummary actual) {
        return YAML_MAPPER.valueToTree(expected).equals(YAML_MAPPER.valueToTree(actual));
    }
}
