package guru.interlis.transformer.app;

import guru.interlis.transformer.validation.InProcessIlivalidatorService;
import guru.interlis.transformer.validation.TransferValidationService;
import guru.interlis.transformer.validation.ValidationResult;

import java.nio.file.Path;
import java.util.List;

public final class IlivalidatorRunner {

    private IlivalidatorRunner() {}

    private static final TransferValidationService service = new InProcessIlivalidatorService();

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

        ValidationResult result = service.validate(file, List.of(modelDirs.split(";")), List.of(model), log);
        System.out.println(result.valid() ? "VALIDATION OK" : "VALIDATION FAILED");
        if (!result.logText().isBlank()) {
            System.out.println(result.logText());
        }
        return result.valid() ? 0 : 1;
    }
}
