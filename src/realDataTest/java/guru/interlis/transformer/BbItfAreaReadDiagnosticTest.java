package guru.interlis.transformer;

import static org.assertj.core.api.Assertions.fail;

import guru.interlis.transformer.app.JobRunner;
import guru.interlis.transformer.app.RunOptions;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.dmav.Dm01DmavFixtures;
import guru.interlis.transformer.dmav.Dm01DmavPaths;
import guru.interlis.transformer.interlis.InterlisIoFactory;
import guru.interlis.transformer.model.IliModelCompileResult;
import guru.interlis.transformer.model.IliModelService;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.ObjectEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("real-data")
class BbItfAreaReadDiagnosticTest {

    private static final String MODEL_DIR = Dm01DmavPaths.LOCAL_MODEL_DIR;
    private static final String DM01_MODEL = Dm01DmavPaths.DM01_MODEL;
    private static final Path DM01_TO_DMAV_PROFILE = Dm01DmavFixtures.BB.dm01ToDmavProfile();
    private static final Path DMAV_TO_DM01_PROFILE = Dm01DmavFixtures.BB.dmavToDm01Profile();
    private static final Path DM01_INPUT = Dm01DmavPaths.FULL_DM01_DATASET;

    private static TransferDescription dm01Td;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void compileModels() {
        IliModelService modelService = new IliModelService();
        IliModelCompileResult dm01Result = modelService.compileModel(DM01_MODEL, MODEL_DIR);
        if (dm01Result.hasErrors()) {
            fail("DM01 model compilation errors:\n  " + diagnostics(dm01Result));
        }
        dm01Td = dm01Result.transferDescription();
    }

    @Test
    void diagnoseItfAreaReadError() throws Exception {
        Path dmavIntermediate = tempDir.resolve("dm01-to-dmav-bb.xtf");
        Path dm01Roundtrip = tempDir.resolve("dm01-roundtrip-bb.itf");

        // Step 1: Forward DM01 -> DMAV
        run(materializeDm01ToDmav(DM01_INPUT, dmavIntermediate), tempDir.resolve("reports-dm01-forward"));

        // Step 2: Reverse DMAV -> DM01
        run(materializeDmavToDm01(dmavIntermediate, dm01Roundtrip), tempDir.resolve("reports-dmav-reverse"));

        System.out.println("Reverse ITF output: " + dm01Roundtrip);
        System.out.println("Reverse ITF size: " + Files.size(dm01Roundtrip));

        // Step 3: Try reading the reverse ITF and capture errors
        try {
            readObjects(dm01Roundtrip, dm01Td);
            System.out.println("ITF read succeeded - no polygon error");
        } catch (Exception e) {
            System.out.println("ITF read FAILED with: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("Stack trace (first 10):");
            for (StackTraceElement ste :
                    java.util.Arrays.stream(e.getStackTrace()).limit(10).toList()) {
                System.out.println("  at " + ste);
            }
            Throwable cause = e.getCause();
            while (cause != null) {
                System.out.println("  Caused by: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
                for (StackTraceElement ste :
                        java.util.Arrays.stream(cause.getStackTrace()).limit(5).toList()) {
                    System.out.println("    at " + ste);
                }
                cause = cause.getCause();
            }
            // Save error details
            Path errorLog = tempDir.resolve("itf-area-read-error.txt");
            Files.writeString(
                    errorLog,
                    e.toString() + "\n\nStack trace:\n"
                            + String.join(
                                    "\n",
                                    java.util.Arrays.stream(e.getStackTrace())
                                            .map(StackTraceElement::toString)
                                            .limit(20)
                                            .toList()));
            System.out.println("Error details saved to: " + errorLog);
            // Don't fail the test - this is diagnostic
        }
    }

    private void run(Path mappingPath, Path reportDir) throws Exception {
        List<String> modelDirs = new ArrayList<>(List.of(MODEL_DIR));
        modelDirs.add("https://models.interlis.ch");
        DiagnosticCollector diagnostics =
                new JobRunner().run(mappingPath, new RunOptions(modelDirs, false, reportDir, false));
        List<Diagnostic> errors = diagnostics.all().stream()
                .filter(d -> d.severity() == Severity.ERROR)
                .toList();
        if (!errors.isEmpty()) {
            throw new RuntimeException("Transformation errors: " + errors);
        }
    }

    private Path materializeDm01ToDmav(Path inputPath, Path outputPath) throws Exception {
        Path mappingPath = tempDir.resolve("dm01-to-dmav-bb-test.yaml");
        String yaml = Files.readString(DM01_TO_DMAV_PROFILE, StandardCharsets.UTF_8)
                .replace("path: \"input/dm01.itf\"", "path: \"" + inputPath.toAbsolutePath() + "\"")
                .replace("path: \"build/out/dmav-bb.xtf\"", "path: \"" + outputPath.toAbsolutePath() + "\"");
        Files.writeString(mappingPath, yaml, StandardCharsets.UTF_8);
        return mappingPath;
    }

    private Path materializeDmavToDm01(Path inputPath, Path outputPath) throws Exception {
        Path mappingPath = tempDir.resolve("dmav-to-dm01-bb-test.yaml");
        String yaml = Files.readString(DMAV_TO_DM01_PROFILE, StandardCharsets.UTF_8)
                .replace("path: \"input/dmav.xtf\"", "path: \"" + inputPath.toAbsolutePath() + "\"")
                .replace("path: \"build/out/dm01-bb.itf\"", "path: \"" + outputPath.toAbsolutePath() + "\"");
        Files.writeString(mappingPath, yaml, StandardCharsets.UTF_8);
        return mappingPath;
    }

    private List<ch.interlis.iom.IomObject> readObjects(Path path, TransferDescription td) throws Exception {
        InterlisIoFactory ioFactory = new InterlisIoFactory();
        IoxReader reader = ioFactory.createReader(path, td);
        List<ch.interlis.iom.IomObject> objects = new ArrayList<>();
        try {
            IoxEvent event;
            while ((event = reader.read()) != null) {
                if (event instanceof ObjectEvent objectEvent) {
                    objects.add(objectEvent.getIomObject());
                }
            }
        } finally {
            reader.close();
        }
        return objects;
    }

    private static String diagnostics(IliModelCompileResult result) {
        return result.diagnostics().all().stream()
                .map(d -> d.severity() + " " + d.code() + ": " + d.message())
                .collect(java.util.stream.Collectors.joining("\n  "));
    }
}
