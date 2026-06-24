package guru.interlis.transformer.interlis;

import ch.interlis.ili2c.Ili2cException;
import ch.interlis.ili2c.Ili2cFailure;
import ch.interlis.ili2c.Ili2cSettings;
import ch.interlis.ili2c.config.Configuration;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ilirepository.IliManager;

import java.util.ArrayList;
import java.util.List;

public final class InterlisModelLoader {

    public TransferDescription compileModel(String modelName, String modelDirectories) throws Ili2cFailure {
        List<String> repos = normalizeModelDirectories(modelDirectories);

        IliManager manager = new IliManager();
        manager.setRepositories(repos.toArray(new String[0]));

        ArrayList<String> entries = new ArrayList<>();
        entries.add(modelName.trim());

        Configuration cfg;
        try {
            cfg = manager.getConfigWithFiles(entries, null, 0.0);
        } catch (Ili2cException e) {
            throw new Ili2cFailure(e);
        }

        if (cfg == null) {
            throw new Ili2cFailure("Failed to create configuration for model: " + modelName);
        }

        Ili2cSettings settings = new Ili2cSettings();
        ch.interlis.ili2c.Main.setDefaultIli2cPathMap(settings);
        String normalizedModelDirectories = normalizeModelDirectoryString(modelDirectories);
        if (normalizedModelDirectories != null) {
            settings.setIlidirs(normalizedModelDirectories);
        }

        return ch.interlis.ili2c.Main.runCompiler(cfg, settings);
    }

    public static String normalizeModelDirectoryString(String modelDirectories) {
        List<String> normalized = normalizeModelDirectories(modelDirectories);
        return normalized.isEmpty() ? null : String.join(";", normalized);
    }

    public static List<String> normalizeModelDirectories(String modelDirectories) {
        ArrayList<String> normalized = new ArrayList<>();
        if (modelDirectories != null && !modelDirectories.isBlank()) {
            for (String part : modelDirectories.split(";")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    normalized.add(normalizeModelDirectory(trimmed));
                }
            }
        }
        return List.copyOf(normalized);
    }

    private static String normalizeModelDirectory(String modelDirectory) {
        if (!isRemoteModelDirectory(modelDirectory)) {
            return modelDirectory;
        }
        int minimumLength = modelDirectory.startsWith("https://") ? "https://".length() : "http://".length();
        String normalized = modelDirectory;
        while (normalized.length() > minimumLength && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static boolean isRemoteModelDirectory(String modelDirectory) {
        return modelDirectory.startsWith("http://") || modelDirectory.startsWith("https://");
    }
}
