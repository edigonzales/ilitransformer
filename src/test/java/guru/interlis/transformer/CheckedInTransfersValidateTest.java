package guru.interlis.transformer;

import guru.interlis.transformer.app.IlivalidatorRunner;
import guru.interlis.transformer.app.IlivalidatorRunner.ValidationResult;
import guru.interlis.transformer.dmav.Dm01DmavFixtures;
import guru.interlis.transformer.dmav.Dm01DmavPaths;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CheckedInTransfersValidateTest {

    private static final String AV_MODELDIR = Dm01DmavPaths.LOCAL_AND_REMOTE_MODEL_DIRS;

    private static final Set<String> ALLOWLIST_INVALID = Set.of(
            "so_2549.itf",
            "DM01-AV-CH.itf",
            "DMAVTYM_Alles_V1_1.xtf"
    );

    private static final Map<String, String> DATA_FILE_TO_MODEL = Map.ofEntries(
            Map.entry("DM01_Grundstuecke_449.itf", Dm01DmavPaths.DM01_MODEL),
            Map.entry("so_2549.itf", Dm01DmavPaths.DM01_MODEL),
            Map.entry("DMAV_Grundstuecke_V1_0.449.xtf", "DMAV_Grundstuecke_V1_0"),
            Map.entry("DM01-AV-CH.itf", Dm01DmavPaths.DM01_MODEL),
            Map.entry("DMAVTYM_Alles_V1_1.xtf", Dm01DmavPaths.DMAV_UMBRELLA_MODEL)
    );

    private static final Map<String, String> FIXTURE_FILE_TO_MODEL = Map.ofEntries(
            Map.entry(relativeFixturePath(Dm01DmavFixtures.LFP3.dm01MinimalFixture()), Dm01DmavFixtures.LFP3.dm01Model()),
            Map.entry(relativeFixturePath(Dm01DmavFixtures.LFP3.dmavMinimalFixture()), Dm01DmavFixtures.LFP3.dmavMinimalModel()),
            Map.entry(relativeFixturePath(Dm01DmavFixtures.LFP3.dm01RealExtractFixture()), Dm01DmavFixtures.LFP3.dm01Model()),
            Map.entry(relativeFixturePath(Dm01DmavFixtures.LFP3.dmavRealExtractFixture()), Dm01DmavFixtures.LFP3.dmavRealExtractModel()),
            Map.entry(relativeFixturePath(Dm01DmavFixtures.HFP3.dm01MinimalFixture()), Dm01DmavFixtures.HFP3.dm01Model()),
            Map.entry(relativeFixturePath(Dm01DmavFixtures.HFP3.dmavMinimalFixture()), Dm01DmavFixtures.HFP3.dmavMinimalModel()),
            Map.entry(relativeFixturePath(Dm01DmavFixtures.HFP3.dm01RealExtractFixture()), Dm01DmavFixtures.HFP3.dm01Model()),
            Map.entry(relativeFixturePath(Dm01DmavFixtures.HFP3.dmavRealExtractFixture()), Dm01DmavFixtures.HFP3.dmavRealExtractModel()),
            Map.entry(relativeFixturePath(Dm01DmavFixtures.BB.dm01MinimalFixture()), Dm01DmavFixtures.BB.dm01Model()),
            Map.entry(relativeFixturePath(Dm01DmavFixtures.BB.dmavMinimalFixture()), Dm01DmavFixtures.BB.dmavMinimalModel()),
            Map.entry(relativeFixturePath(Dm01DmavFixtures.BB.dm01RealExtractFixture()), Dm01DmavFixtures.BB.dm01Model()),
            Map.entry(relativeFixturePath(Dm01DmavFixtures.BB.dmavRealExtractFixture()), Dm01DmavFixtures.BB.dmavRealExtractModel()),
            Map.entry(relativeFixturePath(Dm01DmavFixtures.EO.dm01MinimalFixture()), Dm01DmavFixtures.EO.dm01Model()),
            Map.entry(relativeFixturePath(Dm01DmavFixtures.EO.dmavMinimalFixture()), Dm01DmavFixtures.EO.dmavMinimalModel()),
            Map.entry(relativeFixturePath(Dm01DmavFixtures.GS.dm01MinimalFixture()), Dm01DmavFixtures.GS.dm01Model()),
            Map.entry(relativeFixturePath(Dm01DmavFixtures.GS.dmavMinimalFixture()), Dm01DmavFixtures.GS.dmavMinimalModel()),
            Map.entry(relativeFixturePath(Dm01DmavFixtures.NOMENKLATUR.dm01MinimalFixture()), Dm01DmavFixtures.NOMENKLATUR.dm01Model()),
            Map.entry(relativeFixturePath(Dm01DmavFixtures.NOMENKLATUR.dmavMinimalFixture()), Dm01DmavFixtures.NOMENKLATUR.dmavMinimalModel())
    );

    @Test
    void validateCheckedInTransferFiles() throws Exception {
        List<Path> transferFiles = new java.util.ArrayList<>(findTransferFiles(Path.of("src/test/data/av/")));
        transferFiles.addAll(findTransferFiles(Path.of("src/test/data/DMAV_Version_1_1/")));

        assertThat(transferFiles).as("Expected transfer files under src/test/data/").isNotEmpty();

        validateTransferSet(
                transferFiles,
                file -> DATA_FILE_TO_MODEL.get(file.getFileName().toString()),
                true,
                "All non-allowlisted transfer files under src/test/data/ must validate"
        );
    }

    @Test
    void validateCheckedInDm01DmavFixtures() throws Exception {
        List<Path> transferFiles = findTransferFiles(Dm01DmavPaths.FIXTURE_ROOT);

        assertThat(transferFiles)
                .as("Expected DM01/DMAV fixture files under %s", Dm01DmavPaths.FIXTURE_ROOT)
                .isNotEmpty();

        validateTransferSet(
                transferFiles,
                file -> FIXTURE_FILE_TO_MODEL.get(relativeFixturePath(file)),
                false,
                "All checked-in DM01/DMAV fixtures must validate"
        );
    }

    @Test
    void testResourceTransferFilesExist() throws Exception {
        List<Path> transferFiles = findTransferFiles(Path.of("src/test/resources/transfers/"));

        assertThat(transferFiles).as("Test resource transfer files must exist").isNotEmpty();

        for (Path file : transferFiles) {
            assertThat(file).exists().isRegularFile();
            assertThat(Files.size(file)).as("Transfer file '" + file + "' must be non-empty").isGreaterThan(0);
        }
    }

    private static boolean isNegativeFixture(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.contains(".invalid.") || name.startsWith("bad-")
                || file.getParent().getFileName().toString().equals("negative");
    }

    private static void validateTransferSet(
            List<Path> transferFiles,
            Function<Path, String> modelLookup,
            boolean honorAllowlist,
            String assertionMessage
    ) throws Exception {
        var compiled = new java.util.HashMap<String, ch.interlis.ili2c.metamodel.TransferDescription>();
        List<String> failures = new java.util.ArrayList<>();
        List<String> successes = new java.util.ArrayList<>();
        List<String> skipped = new java.util.ArrayList<>();

        for (Path file : transferFiles) {
            String fileName = file.getFileName().toString();
            String modelName = modelLookup.apply(file);

            assertThat(modelName)
                    .as("No model mapping for file: " + file)
                    .isNotNull();

            if (isNegativeFixture(file) || (honorAllowlist && ALLOWLIST_INVALID.contains(fileName))) {
                skipped.add(fileName + " (allowlist/negative)");
                continue;
            }

            if (!compiled.containsKey(modelName)) {
                var svc = new guru.interlis.transformer.model.IliModelService();
                var r = svc.compileModel(modelName, AV_MODELDIR);
                if (r.hasErrors()) {
                    failures.add(file + ": model '" + modelName + "' compile error: "
                            + r.diagnostics().all().get(0).message());
                    continue;
                }
                compiled.put(modelName, r.transferDescription());
            }

            ValidationResult result = IlivalidatorRunner.validate(file, List.of(AV_MODELDIR), modelName, null);
            if (result.success()) {
                successes.add(file + " -> " + modelName);
            } else {
                failures.add(file + ": validation failed against " + modelName
                        + compactLog(result));
            }
        }

        if (!skipped.isEmpty()) {
            System.out.println("Skipped (allowlist/negative): " + String.join(", ", skipped));
        }
        if (!successes.isEmpty()) {
            System.out.println("Validated successfully: " + String.join(", ", successes));
        }

        assertThat(failures)
                .as(assertionMessage)
                .isEmpty();
    }

    private static List<Path> findTransferFiles(Path dir) throws Exception {
        if (!Files.exists(dir)) return List.of();
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(f -> {
                String name = f.getFileName().toString().toLowerCase();
                return name.endsWith(".itf") || name.endsWith(".xtf");
            }).toList();
        }
    }

    private static String compactLog(ValidationResult result) {
        if (result.log() == null || result.log().isBlank()) {
            return "";
        }
        String compact = result.log().replace('\n', ' ');
        return " - " + compact.substring(0, Math.min(200, compact.length()));
    }

    private static String relativeFixturePath(Path file) {
        return Dm01DmavPaths.FIXTURE_ROOT.relativize(file).toString().replace('\\', '/');
    }
}
