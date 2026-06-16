package guru.interlis.transformer.app;

import guru.interlis.transformer.validation.InProcessIlivalidatorService;
import guru.interlis.transformer.validation.TransferValidationService;

import java.nio.file.Path;
import java.util.List;

public final class IlivalidatorRunner {

    private IlivalidatorRunner() {}

    private static final TransferValidationService service = new InProcessIlivalidatorService();

    /**
     * @deprecated Use {@link guru.interlis.transformer.validation.ValidationResult} instead.
     */
    @Deprecated(since = "25", forRemoval = false)
    public record ValidationResult(boolean success, String log) {}

    /**
     * Validates an ITF/XTF file against an INTERLIS model.
     *
     * @deprecated Use {@link TransferValidationService#validate(Path, List, List, Path)} instead.
     */
    @Deprecated(since = "25", forRemoval = false)
    public static ValidationResult validate(Path dataFile, List<String> modelDirs, String modelName, Path logFile)
            throws Exception {

        guru.interlis.transformer.validation.ValidationResult result =
                service.validate(dataFile, modelDirs, modelName != null ? List.of(modelName) : List.of(), logFile);

        return new ValidationResult(result.valid(), result.logText());
    }

    // -- CLI entry point for Gradle task ----------------------------------

    public static void main(String[] args) {
        int exitCode = run(args);
        System.exit(exitCode);
    }

    private static int run(String[] args) {
        Path file = null;
        String modelDirs = null;
        String model = null;
        Path log = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--file" -> file = Path.of(args[++i]);
                case "--modeldir" -> modelDirs = args[++i];
                case "--model" -> model = args[++i];
                case "--log" -> log = Path.of(args[++i]);
            }
        }

        if (file == null || modelDirs == null || model == null) {
            System.err.println(
                    "Usage: IlivalidatorRunner --file <path> --modeldir <dirs> --model <name> [--log <path>]");
            return 1;
        }

        guru.interlis.transformer.validation.ValidationResult result =
                service.validate(file, List.of(modelDirs.split(";")), List.of(model), log);
        System.out.println(result.valid() ? "VALIDATION OK" : "VALIDATION FAILED");
        if (!result.logText().isBlank()) {
            System.out.println(result.logText());
        }
        return result.valid() ? 0 : 1;
    }
}
