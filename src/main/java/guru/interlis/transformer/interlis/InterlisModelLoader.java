package guru.interlis.transformer.interlis;

import ch.interlis.ili2c.Ili2cFailure;
import ch.interlis.ili2c.Ili2cSettings;
import ch.interlis.ili2c.config.Configuration;
import ch.interlis.ili2c.config.FileEntry;
import ch.interlis.ili2c.config.FileEntryKind;
import ch.interlis.ili2c.metamodel.TransferDescription;

public final class InterlisModelLoader {
    public TransferDescription compileModel(String modelName, String modelDirectories) throws Ili2cFailure {
        Ili2cSettings settings = new Ili2cSettings();
        ch.interlis.ili2c.Main.setDefaultIli2cPathMap(settings);
        if (modelDirectories != null && !modelDirectories.isBlank()) {
            settings.setIlidirs(modelDirectories);
        } else {
            settings.setIlidirs(Ili2cSettings.DEFAULT_ILIDIRS);
        }

        Configuration config = new Configuration();
        config.addFileEntry(new FileEntry(modelName, FileEntryKind.ILIMODELFILE));
        return ch.interlis.ili2c.Main.runCompiler(config, settings);
    }
}
