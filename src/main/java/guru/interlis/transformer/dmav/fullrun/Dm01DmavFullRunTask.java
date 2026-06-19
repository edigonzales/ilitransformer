package guru.interlis.transformer.dmav.fullrun;

import guru.interlis.transformer.app.JobRunner;
import guru.interlis.transformer.app.RunOptions;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.dmav.Dm01DmavPaths;
import guru.interlis.transformer.mapping.model.JobConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
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
        verifySourceFingerprint(sourcePath, manifest.source.sha256);

        Path reportDir = options.containsKey("--report-dir")
                ? Path.of(options.get("--report-dir")).toAbsolutePath().normalize()
                : repositoryRoot
                        .resolve("build/reports/dm01-dmav/full-runs/" + manifest.datasetSlug)
                        .normalize();
        Files.createDirectories(reportDir);

        Path outputPath = options.containsKey("--output")
                ? Path.of(options.get("--output")).toAbsolutePath().normalize()
                : reportDir.resolve(manifest.output.fileName).toAbsolutePath().normalize();

        Dm01DmavFullRunAssembler assembler = new Dm01DmavFullRunAssembler();
        Dm01DmavFullRunAssembler.AssembledFullRun assembled =
                assembler.assemble(manifest, manifestPath, repositoryRoot, sourcePath, outputPath);

        Path generatedMapping = reportDir.resolve("combined.generated.yaml");
        writeCombinedMapping(generatedMapping, assembled.combinedConfig());

        JobRunner jobRunner = new JobRunner();
        DiagnosticCollector diagnostics = jobRunner.run(
                generatedMapping, new RunOptions(assembled.combinedConfig().job.modeldir, true, reportDir, false));
        if (diagnostics.hasErrors()) {
            throw new IllegalStateException(
                    "Full run produced errors. See " + reportDir.resolve("transformation-report.json"));
        }

        Dm01DmavFullRunSummaryNormalizer normalizer = new Dm01DmavFullRunSummaryNormalizer();
        Path reportJson = reportDir.resolve("transformation-report.json");
        if (!Files.isRegularFile(reportJson)) {
            throw new IllegalStateException("Transformation report not found: " + reportJson);
        }

        Dm01DmavFullRunSummary actualSummary = normalizer.normalize(reportJson, manifest);
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
        System.out.println("Generated mapping: " + generatedMapping);
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

    private static void verifySourceFingerprint(Path sourcePath, String expectedSha256) throws Exception {
        if (!Files.isRegularFile(sourcePath)) {
            throw new IllegalArgumentException("Source file not found: " + sourcePath);
        }
        String actualSha256 = sha256(sourcePath);
        if (!expectedSha256.equalsIgnoreCase(actualSha256)) {
            throw new IllegalStateException("Source fingerprint mismatch for " + sourcePath + ": expected "
                    + expectedSha256 + " but was " + actualSha256);
        }
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        try (InputStream inputStream = Files.newInputStream(path)) {
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void writeCombinedMapping(Path path, JobConfig config) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        YAML_MAPPER.writeValue(path.toFile(), config);
    }

    private static boolean summariesMatch(Dm01DmavFullRunSummary expected, Dm01DmavFullRunSummary actual) {
        return YAML_MAPPER.valueToTree(expected).equals(YAML_MAPPER.valueToTree(actual));
    }
}
