package guru.interlis.transformer;

import guru.interlis.transformer.app.JobRunner;
import guru.interlis.transformer.app.RunOptions;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One-shot test that produces dm01-bb.itf from DM01-AV-CH.itf
 * via forward+reverse transform and saves to build/out/.
 */
class ProduceDm01BbItfTest {

    private static final String MODEL_DIR = "src/test/data/av/models/";
    private static final Path DM01_TO_DMAV_PROFILE = Path.of("profiles/dm01-to-dmav/1.1/bb.yaml");
    private static final Path DMAV_TO_DM01_PROFILE = Path.of("profiles/dmav-to-dm01/1.1/bb.yaml");
    private static final Path DM01_INPUT = Path.of("src/test/data/DMAV_Version_1_1/DM01-AV-CH.itf");
    private static final Path DM01_OUTPUT = Path.of("build/out/dm01-bb.itf");

    @Test
    void produce() throws Exception {
        Path dmavIntermediate = Path.of("build/out/dmav-bb-tmp.xtf");

        // Ensure output dir
        Files.createDirectories(DM01_OUTPUT.getParent());

        // Forward: DM01 → DMAV
        run(materialize(DM01_TO_DMAV_PROFILE, DM01_INPUT, dmavIntermediate),
                Path.of("build/out/reports-dm01-forward"));

        // Reverse: DMAV → DM01
        run(materialize(DMAV_TO_DM01_PROFILE, dmavIntermediate, DM01_OUTPUT),
                Path.of("build/out/reports-dmav-reverse"));

        assertThat(DM01_OUTPUT).exists();
        assertThat(Files.size(DM01_OUTPUT)).isGreaterThan(0);
        System.out.println("Produced: " + DM01_OUTPUT.toAbsolutePath());
        System.out.println("Size: " + Files.size(DM01_OUTPUT) + " bytes");
    }

    private void run(Path mappingPath, Path reportDir) throws Exception {
        List<String> modelDirs = new ArrayList<>(List.of(MODEL_DIR));
        modelDirs.add("https://models.interlis.ch");
        DiagnosticCollector diagnostics = new JobRunner().run(mappingPath,
                new RunOptions(modelDirs, false, reportDir, false));
        List<Diagnostic> errors = diagnostics.all().stream()
                .filter(d -> d.severity() == Severity.ERROR)
                .toList();
        assertThat(errors).as("Diagnostics: %s", diagnostics.all()).isEmpty();
    }

    private Path materialize(Path profilePath, Path inputPath, Path outputPath) throws Exception {
        Path mappingPath = outputPath.resolveSibling(outputPath.getFileName() + "-mapping.yaml");
        String yaml = Files.readString(profilePath, StandardCharsets.UTF_8)
                .replace("path: \"input/dm01.itf\"",
                        "path: \"" + inputPath.toAbsolutePath() + "\"")
                .replace("path: \"input/dmav.xtf\"",
                        "path: \"" + inputPath.toAbsolutePath() + "\"")
                .replace("path: \"build/out/dmav-bb.xtf\"",
                        "path: \"" + outputPath.toAbsolutePath() + "\"")
                .replace("path: \"build/out/dm01-bb.itf\"",
                        "path: \"" + outputPath.toAbsolutePath() + "\"");
        Files.writeString(mappingPath, yaml, StandardCharsets.UTF_8);
        return mappingPath;
    }
}
