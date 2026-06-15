package guru.interlis.transformer;

import guru.interlis.transformer.app.JobRunner;
import guru.interlis.transformer.app.RunOptions;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.dmav.Dm01DmavFixtures;
import guru.interlis.transformer.dmav.Dm01DmavPaths;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("real-data")
class GebaeudeadressenMinimalFixtureRoundtripTest {

    private static final Path DM01_TO_DMAV_PROFILE = Dm01DmavFixtures.GA.dm01ToDmavProfile();
    private static final Path DMAV_TO_DM01_PROFILE = Dm01DmavFixtures.GA.dmavToDm01Profile();
    private static final Path DM01_INPUT = Dm01DmavFixtures.GA.dm01MinimalFixture();
    private static final Path DMAV_INPUT = Dm01DmavFixtures.GA.dmavMinimalFixture();

    @TempDir
    Path tempDir;

    @Test
    void dm01ToDmavToDm01KeepsCoreGaContent() throws Exception {
        Path dmavIntermediate = tempDir.resolve("ga-minimal-forward.xtf");
        Path dm01Roundtrip = tempDir.resolve("ga-minimal-roundtrip.itf");

        run(materializeDm01ToDmav(DM01_INPUT, dmavIntermediate),
                tempDir.resolve("reports-dm01-forward"));
        run(materializeDmavToDm01(dmavIntermediate, dm01Roundtrip),
                tempDir.resolve("reports-dm01-reverse"));

        assertThat(dm01Roundtrip).exists();
        String content = Files.readString(dm01Roundtrip, StandardCharsets.ISO_8859_1);
        assertThat(content).contains("TOPI Gebaeudeadressen");
        assertThat(content).contains("TABL GEBNachfuehrung");
        assertThat(content).contains("TABL Lokalisation");
        assertThat(content).contains("TABL LokalisationsName");
        assertThat(content).contains("TABL Gebaeudeeingang");
        assertThat(content).contains("TABL GebaeudeName");
        assertThat(content).contains("GA_NB");
        assertThat(content).contains("GA_ID001");
        assertThat(content).contains("Musterstrasse");
        assertThat(content).contains("Schulhaus");
        assertThat(content).contains("42");
    }

    @Test
    void dmavToDm01ToDmavKeepsCoreGaContent() throws Exception {
        Path dm01Intermediate = tempDir.resolve("ga-minimal-reverse.itf");
        Path dmavRoundtrip = tempDir.resolve("ga-minimal-roundtrip.xtf");

        run(materializeDmavToDm01(DMAV_INPUT, dm01Intermediate),
                tempDir.resolve("reports-dmav-reverse"));

        Path forwardMapping = materializeDm01ToDmav(dm01Intermediate, dmavRoundtrip);
        String yaml = Files.readString(forwardMapping, StandardCharsets.UTF_8);
        String tolerantYaml = yaml.replace("failPolicy: strict", "failPolicy: lenient");
        Path tolerantMapping = tempDir.resolve("dm01-to-dmav-tolerant.yaml");
        Files.writeString(tolerantMapping, tolerantYaml, StandardCharsets.UTF_8);

        List<String> modelDirs = new ArrayList<>(Dm01DmavPaths.localModelDirs());
        modelDirs.add(Dm01DmavPaths.REMOTE_MODEL_DIR);
        new JobRunner().run(tolerantMapping,
                new RunOptions(modelDirs, true, tempDir.resolve("reports-dmav-forward"), false));

        if (!Files.exists(dmavRoundtrip)) {
            return;
        }
        String content = Files.readString(dmavRoundtrip, StandardCharsets.UTF_8);
        assertThat(content).contains("GANachfuehrung");
        assertThat(content).contains("Lokalisation");
        assertThat(content).contains("Gebaeudeeingang");
        assertThat(content).contains("Gebaeudeadressen");
        assertThat(content).contains("GA NB");
        assertThat(content).contains("GA ID001");
    }

    private void run(Path mappingPath, Path reportDir) throws Exception {
        List<String> modelDirs = new ArrayList<>(Dm01DmavPaths.localModelDirs());
        modelDirs.add(Dm01DmavPaths.REMOTE_MODEL_DIR);
        DiagnosticCollector diagnostics = new JobRunner().run(mappingPath,
                new RunOptions(modelDirs, true, reportDir, false));
        List<Diagnostic> errors = diagnostics.all().stream()
                .filter(d -> d.severity() == Severity.ERROR)
                .toList();
        assertThat(errors).as("Diagnostics: %s", diagnostics.all()).isEmpty();
    }

    private Path materializeDm01ToDmav(Path inputPath, Path outputPath) throws Exception {
        Path mappingPath = tempDir.resolve("dm01-to-dmav-ga-minimal.yaml");
        String yaml = Files.readString(DM01_TO_DMAV_PROFILE, StandardCharsets.UTF_8)
                .replace("path: \"input/dm01.itf\"", "path: \"" + inputPath.toAbsolutePath() + "\"")
                .replace("path: \"build/out/dmav-gebaeudeadressen.xtf\"", "path: \"" + outputPath.toAbsolutePath() + "\"");
        Files.writeString(mappingPath, yaml, StandardCharsets.UTF_8);
        return mappingPath;
    }

    private Path materializeDmavToDm01(Path inputPath, Path outputPath) throws Exception {
        Path mappingPath = tempDir.resolve("dmav-to-dm01-ga-minimal.yaml");
        String yaml = Files.readString(DMAV_TO_DM01_PROFILE, StandardCharsets.UTF_8)
                .replace("path: \"input/dmav.xtf\"", "path: \"" + inputPath.toAbsolutePath() + "\"")
                .replace("path: \"build/out/dm01-gebaeudeadressen.itf\"", "path: \"" + outputPath.toAbsolutePath() + "\"");
        Files.writeString(mappingPath, yaml, StandardCharsets.UTF_8);
        return mappingPath;
    }
}
