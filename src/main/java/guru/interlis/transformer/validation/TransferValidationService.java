package guru.interlis.transformer.validation;

import java.nio.file.Path;
import java.util.List;

public interface TransferValidationService {
    ValidationResult validate(
            Path transferFile,
            List<String> modelDirectories,
            List<String> modelNames,
            Path logFile);
}
