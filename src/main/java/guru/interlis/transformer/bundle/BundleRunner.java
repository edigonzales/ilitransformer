package guru.interlis.transformer.bundle;

import guru.interlis.transformer.app.JobRunner;
import guru.interlis.transformer.app.RunOptions;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.mapping.model.JobConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public final class BundleRunner {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private final BundleAssembler assembler = new BundleAssembler();

    public BundleRunResult run(
            BundleManifest manifest,
            Path manifestPath,
            Path repositoryRoot,
            Path sourcePath,
            Path reportDir,
            Path outputPath,
            boolean validate)
            throws Exception {
        verifySourceFingerprint(sourcePath, manifest.source.sha256);

        Files.createDirectories(reportDir);

        BundleAssembler.AssembledBundle assembled =
                assembler.assemble(manifest, manifestPath, repositoryRoot, sourcePath, outputPath);

        Path generatedMapping = reportDir.resolve("combined.generated.yaml");
        writeCombinedMapping(generatedMapping, assembled.combinedConfig());

        JobRunner jobRunner = new JobRunner();
        DiagnosticCollector diagnostics = jobRunner.run(
                generatedMapping, new RunOptions(assembled.combinedConfig().job.modeldir, validate, reportDir, false));
        if (diagnostics.hasErrors()) {
            throw new IllegalStateException(
                    "Bundle run produced errors. See " + reportDir.resolve("transformation-report.json"));
        }

        Path reportJson = reportDir.resolve("transformation-report.json");
        if (!Files.isRegularFile(reportJson)) {
            throw new IllegalStateException("Transformation report not found: " + reportJson);
        }

        return new BundleRunResult(generatedMapping, reportDir, reportJson, outputPath, assembled);
    }

    private static void verifySourceFingerprint(Path sourcePath, String expectedSha256) throws Exception {
        if (!Files.isRegularFile(sourcePath)) {
            throw new IllegalArgumentException("Source file not found: " + sourcePath);
        }
        if (expectedSha256 == null || expectedSha256.isBlank()) {
            return;
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

    public record BundleRunResult(
            Path combinedMappingPath,
            Path reportDir,
            Path reportJson,
            Path outputPath,
            BundleAssembler.AssembledBundle assembled) {}
}
