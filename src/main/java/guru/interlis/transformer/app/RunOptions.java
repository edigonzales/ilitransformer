package guru.interlis.transformer.app;

import guru.interlis.transformer.mapping.plan.FailPolicy;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record RunOptions(
        List<String> modelDirectories,
        boolean validateOutput,
        Path reportDirectory,
        boolean keepTemporaryFiles,
        FailPolicy failPolicyOverride) {
    public RunOptions {
        modelDirectories = modelDirectories != null ? List.copyOf(modelDirectories) : List.of();
    }

    public RunOptions() {
        this(List.of(), false, null, false, null);
    }

    public RunOptions(List<String> modelDirectories) {
        this(modelDirectories, false, null, false, null);
    }

    public RunOptions(
            List<String> modelDirectories, boolean validateOutput, Path reportDirectory, boolean keepTemporaryFiles) {
        this(modelDirectories, validateOutput, reportDirectory, keepTemporaryFiles, null);
    }

    public Optional<FailPolicy> failPolicyOverrideOptional() {
        return Optional.ofNullable(failPolicyOverride);
    }
}
