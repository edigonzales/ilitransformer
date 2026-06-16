package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import guru.interlis.transformer.app.JobRunner;
import guru.interlis.transformer.app.RunOptions;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.dmav.Dm01DmavFixtures;
import guru.interlis.transformer.dmav.Dm01DmavPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("real-data")
class HoheitsgrenzenMinimalFixtureRoundtripTest {

    private static final Path DM01_TO_DMAV_PROFILE = Dm01DmavFixtures.HOHEITSGRENZEN.dm01ToDmavProfile();
    private static final Path DMAV_TO_DM01_PROFILE = Dm01DmavFixtures.HOHEITSGRENZEN.dmavToDm01Profile();
    private static final Path DM01_INPUT = Dm01DmavFixtures.HOHEITSGRENZEN.dm01MinimalFixture();
    private static final Path DMAV_INPUT = Dm01DmavFixtures.HOHEITSGRENZEN.dmavMinimalFixture();

    @TempDir
    Path tempDir;

    @Test
    void dm01ToDmavToDm01KeepsCoreHoheitsgrenzenContent() throws Exception {
        Path dmavIntermediate = tempDir.resolve("hoheitsgrenzen-minimal-forward.xtf");
        Path dm01Roundtrip = tempDir.resolve("hoheitsgrenzen-minimal-roundtrip.itf");

        run(materializeDm01ToDmav(DM01_INPUT, dmavIntermediate), tempDir.resolve("reports-dm01-forward"));
        run(materializeDmavToDm01(dmavIntermediate, dm01Roundtrip), tempDir.resolve("reports-dmav-reverse"));

        assertThat(dm01Roundtrip).exists();
        String content = Files.readString(dm01Roundtrip, StandardCharsets.ISO_8859_1);
        assertThat(content).contains("TOPI Gemeindegrenzen");
        assertThat(content).contains("TABL GEMNachfuehrung");
        assertThat(content).contains("TABL Hoheitsgrenzpunkt");
        assertThat(content).contains("TABL HoheitsgrenzpunktSymbol");
        assertThat(content).contains("TABL Gemeinde");
        assertThat(content).contains("TABL ProjGemeindegrenze");
        assertThat(content).contains("TABL Gemeindegrenze");
        assertThat(content).contains("TABL Gemeindegrenze_Geometrie");
        assertThat(content).contains("TOPI Bezirksgrenzen");
        assertThat(content).contains("TABL Bezirksgrenzabschnitt");
        assertThat(content).contains("TOPI Kantonsgrenzen");
        assertThat(content).contains("TABL Kantonsgrenzabschnitt");
        assertThat(content).contains("HG_NB");
        assertThat(content).contains("HG_ID001");
        assertThat(content).contains("HoheitsgrenzenMinimalTest");
        assertThat(content).contains("Musterhausen");
        assertThat(content).contains("Beispielikon");
    }

    @Test
    void dmavToDm01ToDmavKeepsCoreHoheitsgrenzenContent() throws Exception {
        Path dm01Intermediate = tempDir.resolve("hoheitsgrenzen-minimal-reverse.itf");
        Path dmavRoundtrip = tempDir.resolve("hoheitsgrenzen-minimal-roundtrip.xtf");

        run(materializeDmavToDm01(DMAV_INPUT, dm01Intermediate), tempDir.resolve("reports-dmav-reverse"));
        run(materializeDm01ToDmav(dm01Intermediate, dmavRoundtrip), tempDir.resolve("reports-dmav-forward"));

        assertThat(dmavRoundtrip).exists();
        String content = Files.readString(dmavRoundtrip, StandardCharsets.UTF_8);
        assertThat(content).contains("HHGNachfuehrung");
        assertThat(content).contains("Gemeinde");
        assertThat(content).contains("ProjGemeindegrenzabschnitt");
        assertThat(content).contains("Gemeindegrenze");
        assertThat(content).contains("Bezirksgrenzabschnitt");
        assertThat(content).contains("Kantonsgrenzabschnitt");
        assertThat(content).contains("HoheitsgrenzenMinimalTest");
        assertThat(content).contains("Musterhausen");
        assertThat(content).contains("rechtskraeftig");
    }

    private void run(Path mappingPath, Path reportDir) throws Exception {
        List<String> modelDirs = new ArrayList<>(Dm01DmavPaths.localModelDirs());
        modelDirs.add(Dm01DmavPaths.REMOTE_MODEL_DIR);
        DiagnosticCollector diagnostics =
                new JobRunner().run(mappingPath, new RunOptions(modelDirs, true, reportDir, false));
        List<Diagnostic> errors = diagnostics.all().stream()
                .filter(d -> d.severity() == Severity.ERROR)
                .toList();
        assertThat(errors).as("Diagnostics: %s", diagnostics.all()).isEmpty();
    }

    private void runWithoutValidation(Path mappingPath, Path reportDir) throws Exception {
        List<String> modelDirs = new ArrayList<>(Dm01DmavPaths.localModelDirs());
        modelDirs.add(Dm01DmavPaths.REMOTE_MODEL_DIR);
        DiagnosticCollector diagnostics =
                new JobRunner().run(mappingPath, new RunOptions(modelDirs, false, reportDir, false));
        List<Diagnostic> errors = diagnostics.all().stream()
                .filter(d -> d.severity() == Severity.ERROR)
                .toList();
        assertThat(errors).as("Diagnostics: %s", diagnostics.all()).isEmpty();
    }

    private Path materializeDm01ToDmav(Path inputPath, Path outputPath) throws Exception {
        Path mappingPath = tempDir.resolve("dm01-to-dmav-hoheitsgrenzen-minimal.yaml");
        String yaml = Files.readString(DM01_TO_DMAV_PROFILE, StandardCharsets.UTF_8)
                .replace("path: \"input/dm01.itf\"", "path: \"" + inputPath.toAbsolutePath() + "\"")
                .replace(
                        "path: \"build/out/dmav-hoheitsgrenzen.xtf\"", "path: \"" + outputPath.toAbsolutePath() + "\"");
        Files.writeString(mappingPath, yaml, StandardCharsets.UTF_8);
        return mappingPath;
    }

    private Path materializeDmavToDm01(Path inputPath, Path outputPath) throws Exception {
        Path mappingPath = tempDir.resolve("dmav-to-dm01-hoheitsgrenzen-minimal.yaml");
        String yaml = Files.readString(DMAV_TO_DM01_PROFILE, StandardCharsets.UTF_8)
                .replace("path: \"input/dmav.xtf\"", "path: \"" + inputPath.toAbsolutePath() + "\"")
                .replace(
                        "path: \"build/out/dm01-hoheitsgrenzen.itf\"", "path: \"" + outputPath.toAbsolutePath() + "\"");
        Files.writeString(mappingPath, yaml, StandardCharsets.UTF_8);
        return mappingPath;
    }
}
