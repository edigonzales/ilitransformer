package guru.interlis.transformer.validation;

import ch.ehi.basics.settings.Settings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.interlis2.validator.Validator;

public final class InProcessIlivalidatorService implements TransferValidationService {

    private static final Pattern ERROR_PATTERN = Pattern.compile("Error:", Pattern.LITERAL);
    private static final Pattern WARNING_PATTERN = Pattern.compile("Warning:", Pattern.LITERAL);

    @Override
    public ValidationResult validate(
            Path transferFile, List<String> modelDirectories, List<String> modelNames, Path logFile) {

        Settings settings = new Settings();
        settings.setValue("org.interlis2.validator.ilidirs", String.join(";", modelDirectories));
        settings.setValue("org.interlis2.validator.modelNames", String.join(";", modelNames));

        Path effectiveLog = logFile;
        if (effectiveLog != null) {
            try {
                Files.createDirectories(effectiveLog.getParent());
            } catch (Exception ignore) {
            }
            settings.setValue("org.interlis2.validator.log", effectiveLog.toString());
        }

        boolean valid;
        try {
            valid = Validator.runValidation(new String[] {transferFile.toString()}, settings);
        } catch (Exception e) {
            return new ValidationResult(false, -1, -1, effectiveLog, e.getMessage());
        }

        String logText = "";
        int errorCount = -1;
        int warningCount = -1;

        if (effectiveLog != null && Files.exists(effectiveLog)) {
            try {
                logText = Files.readString(effectiveLog);
                errorCount = countOccurrences(logText, ERROR_PATTERN);
                warningCount = countOccurrences(logText, WARNING_PATTERN);
            } catch (Exception ignore) {
            }
        }

        return new ValidationResult(valid, errorCount, warningCount, effectiveLog, logText);
    }

    private static int countOccurrences(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
