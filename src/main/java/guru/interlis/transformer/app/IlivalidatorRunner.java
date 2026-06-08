package guru.interlis.transformer.app;

import ch.ehi.basics.settings.Settings;
import org.interlis2.validator.Validator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class IlivalidatorRunner {

    private IlivalidatorRunner() {}

    public record ValidationResult(boolean success, String log) {}

    /**
     * Validates an ITF/XTF file against an INTERLIS model.
     *
     * @param dataFile  the ITF/XTF file to validate
     * @param modelDirs model directories (semicolon-separated paths)
     * @param modelName the INTERLIS model name (e.g. "DM01AVCH24LV95D")
     * @param logFile   where to write the validation log (may be null)
     * @return validation result with success flag and log content
     */
    public static ValidationResult validate(
            Path dataFile, List<String> modelDirs, String modelName, Path logFile) throws Exception {

        Settings settings = new Settings();
        settings.setValue("org.interlis2.validator.ilidirs", String.join(";", modelDirs));
        settings.setValue("org.interlis2.validator.modelNames", modelName);

        if (logFile != null) {
            Files.createDirectories(logFile.getParent());
            settings.setValue("org.interlis2.validator.log", logFile.toString());
        }

        boolean success = Validator.runValidation(
                new String[]{dataFile.toString()}, settings);

        String log = logFile != null && Files.exists(logFile)
                ? Files.readString(logFile) : "";

        return new ValidationResult(success, log);
    }

    // -- CLI entry point for Gradle task ----------------------------------

    public static void main(String[] args) throws Exception {
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
            System.err.println("Usage: IlivalidatorRunner --file <path> --modeldir <dirs> --model <name> [--log <path>]");
            System.exit(1);
        }

        ValidationResult result = validate(file,
                List.of(modelDirs.split(";")), model, log);
        System.out.println(result.success() ? "VALIDATION OK" : "VALIDATION FAILED");
        if (!result.log().isBlank()) {
            System.out.println(result.log());
        }
        System.exit(result.success() ? 0 : 1);
    }
}
