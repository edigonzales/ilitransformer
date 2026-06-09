package guru.interlis.transformer;

import guru.interlis.transformer.app.IlivalidatorRunner;
import guru.interlis.transformer.app.IlivalidatorRunner.ValidationResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class CheckedInTransfersValidateTest {

    private static final String AV_MODELDIR = "src/test/data/av/models/;https://models.interlis.ch";

    private static final Set<String> ALLOWLIST_INVALID = Set.of(
            "so_2549.itf",
            "DM01-AV-CH.itf",
            "DMAVTYM_Alles_V1_1.xtf"
    );

    private static final Map<String, String> FILE_TO_MODEL = Map.ofEntries(
            Map.entry("DM01_Grundstuecke_449.itf", "DM01AVCH24LV95D"),
            Map.entry("so_2549.itf", "DM01AVCH24LV95D"),
            Map.entry("DMAV_Grundstuecke_V1_0.449.xtf", "DMAV_Grundstuecke_V1_0"),
            Map.entry("DM01-AV-CH.itf", "DM01AVCH24LV95D"),
            Map.entry("DMAVTYM_Alles_V1_1.xtf", "DMAVTYM_Alles_V1_1")
    );

    @Test
    void validateCheckedInTransferFiles() throws Exception {
        List<Path> transferFiles = new java.util.ArrayList<>(findTransferFiles(Path.of("src/test/data/av/")));
        transferFiles.addAll(findTransferFiles(Path.of("src/test/data/DMAV_Version_1_1/")));

        assertThat(transferFiles).as("Expected transfer files under src/test/data/").isNotEmpty();

        var compiled = new java.util.HashMap<String, ch.interlis.ili2c.metamodel.TransferDescription>();
        List<String> failures = new java.util.ArrayList<>();
        List<String> successes = new java.util.ArrayList<>();
        List<String> skipped = new java.util.ArrayList<>();

        for (Path file : transferFiles) {
            String fileName = file.getFileName().toString();
            String modelName = FILE_TO_MODEL.get(fileName);

            assertThat(modelName)
                    .as("No model mapping for file: " + fileName)
                    .isNotNull();

            if (isNegativeFixture(file) || ALLOWLIST_INVALID.contains(fileName)) {
                skipped.add(fileName + " (allowlist/negative)");
                continue;
            }

            if (!compiled.containsKey(modelName)) {
                var svc = new guru.interlis.transformer.model.IliModelService();
                var r = svc.compileModel(modelName, AV_MODELDIR);
                if (r.hasErrors()) {
                    failures.add(fileName + ": model '" + modelName + "' compile error: "
                            + r.diagnostics().all().get(0).message());
                    continue;
                }
                compiled.put(modelName, r.transferDescription());
            }

            ValidationResult result = IlivalidatorRunner.validate(
                    file, List.of(AV_MODELDIR), modelName, null);
            if (result.success()) {
                successes.add(fileName + " -> " + modelName);
            } else {
                failures.add(fileName + ": validation failed against " + modelName
                        + (result.log() != null && !result.log().isBlank() ? " - " + result.log().replace('\n', ' ').substring(0, Math.min(200, result.log().length())) : ""));
            }
        }

        if (!skipped.isEmpty()) {
            System.out.println("Skipped (allowlist/negative): " + String.join(", ", skipped));
        }
        if (!successes.isEmpty()) {
            System.out.println("Validated successfully: " + String.join(", ", successes));
        }

        assertThat(failures)
                .as("All non-allowlisted transfer files must validate")
                .isEmpty();
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

    private static List<Path> findTransferFiles(Path dir) throws Exception {
        if (!Files.exists(dir)) return List.of();
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(f -> {
                String name = f.getFileName().toString().toLowerCase();
                return name.endsWith(".itf") || name.endsWith(".xtf");
            }).toList();
        }
    }
}
