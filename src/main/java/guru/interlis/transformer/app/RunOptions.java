package guru.interlis.transformer.app;

import java.nio.file.Path;
import java.util.List;

public record RunOptions(
        List<String> modelDirectories,
        boolean validateOutput,
        Path reportDirectory,
        boolean keepTemporaryFiles
) {
    public RunOptions {
        modelDirectories = modelDirectories != null
                ? List.copyOf(modelDirectories) : List.of();
    }

    public RunOptions() {
        this(List.of(), false, null, false);
    }

    public RunOptions(List<String> modelDirectories) {
        this(modelDirectories, false, null, false);
    }
}
