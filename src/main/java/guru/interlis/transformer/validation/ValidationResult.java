package guru.interlis.transformer.validation;

import java.nio.file.Path;

public record ValidationResult(boolean valid, int errorCount, int warningCount, Path logFile, String logText) {}
