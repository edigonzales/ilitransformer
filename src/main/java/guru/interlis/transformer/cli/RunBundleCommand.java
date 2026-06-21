package guru.interlis.transformer.cli;

import guru.interlis.transformer.bundle.BundleManifest;
import guru.interlis.transformer.bundle.BundleManifestLoader;
import guru.interlis.transformer.bundle.BundleRunner;
import guru.interlis.transformer.bundle.BundleSummary;
import guru.interlis.transformer.bundle.BundleSummaryNormalizer;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "run-bundle",
        description = "Assemble several mapping modules from a bundle manifest and run them as one transformation",
        mixinStandardHelpOptions = true)
public final class RunBundleCommand implements Callable<Integer> {

    @Option(
            names = {"--manifest"},
            required = true,
            description = "Bundle manifest file (YAML)")
    private Path manifest;

    @Option(
            names = {"--source"},
            description = "Override the source transfer file (default: manifest source.pathHint)")
    private Path source;

    @Option(
            names = {"--report-dir"},
            description = "Output directory for reports and the generated combined mapping")
    private Path reportDir;

    @Option(
            names = {"--output"},
            description = "Override the output transfer file path")
    private Path output;

    @Option(
            names = {"--repo-root"},
            description = "Repository root for resolving non-relative manifest paths (default: working directory)")
    private Path repoRoot;

    @Option(
            names = {"--no-validate"},
            negatable = true,
            description = "Run ilivalidator on the output after transformation (default: from manifest)")
    private Boolean validate;

    @Override
    public Integer call() throws Exception {
        Path repositoryRoot = repoRoot != null
                ? repoRoot.toAbsolutePath().normalize()
                : Path.of("").toAbsolutePath().normalize();
        Path manifestPath = manifest.toAbsolutePath().normalize();

        BundleManifestLoader manifestLoader = new BundleManifestLoader();
        BundleManifest bundle = manifestLoader.load(manifestPath, repositoryRoot);

        Path sourcePath = resolveSourcePath(bundle, manifestPath, repositoryRoot, manifestLoader);

        Path resolvedReportDir = reportDir != null
                ? reportDir.toAbsolutePath().normalize()
                : repositoryRoot
                        .resolve("build/reports/bundle/" + slug(bundle.name))
                        .normalize();

        Path outputPath = output != null
                ? output.toAbsolutePath().normalize()
                : resolvedReportDir
                        .resolve(bundle.output.fileName)
                        .toAbsolutePath()
                        .normalize();

        boolean validateOutput = validate != null ? validate : bundle.validate;

        BundleRunner runner = new BundleRunner();
        BundleRunner.BundleRunResult result = runner.run(
                bundle, manifestPath, repositoryRoot, sourcePath, resolvedReportDir, outputPath, validateOutput);

        BundleSummaryNormalizer normalizer = new BundleSummaryNormalizer();
        BundleSummary actualSummary = normalizer.normalize(result.reportJson(), bundle);
        Path normalizedSummary = resolvedReportDir.resolve("normalized-summary.yaml");
        normalizer.writeSummary(normalizedSummary, actualSummary);

        System.out.println("Manifest: " + manifestPath);
        System.out.println("Source: " + sourcePath);
        System.out.println("Output: " + outputPath);
        System.out.println("Generated mapping: " + result.combinedMappingPath());
        System.out.println("Report directory: " + resolvedReportDir);
        System.out.println("Normalized summary: " + normalizedSummary);

        if (bundle.expectedSummary != null && !bundle.expectedSummary.isBlank()) {
            Path expectedSummaryPath =
                    manifestLoader.resolveManifestPath(manifestPath, repositoryRoot, bundle.expectedSummary);
            BundleSummary expectedSummary = normalizer.readSummary(expectedSummaryPath);
            if (!normalizer.summariesMatch(expectedSummary, actualSummary)) {
                System.err.println("Normalized summary mismatch. Expected " + expectedSummaryPath
                        + " but wrote actual summary to " + normalizedSummary);
                return 1;
            }
            System.out.println("Summary matches expected: " + expectedSummaryPath);
        }

        return 0;
    }

    private Path resolveSourcePath(
            BundleManifest bundle, Path manifestPath, Path repositoryRoot, BundleManifestLoader manifestLoader) {
        if (source != null) {
            return source.toAbsolutePath().normalize();
        }
        return manifestLoader
                .resolveManifestPath(manifestPath, repositoryRoot, bundle.source.pathHint)
                .toAbsolutePath()
                .normalize();
    }

    private static String slug(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
    }
}
