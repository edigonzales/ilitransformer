package guru.interlis.transformer.interlis;

import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom_j.itf.ItfReader;
import ch.interlis.iom_j.itf.ItfWriter;
import ch.interlis.iom_j.xtf.XtfReader;
import ch.interlis.iom_j.xtf.XtfWriter;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;
import ch.interlis.iox_j.IoxIliReader;
import java.nio.file.Path;

public final class InterlisIoFactory {
    public IoxReader createReader(Path path, TransferDescription transferDescription) throws Exception {
        String lowerName = path.getFileName().toString().toLowerCase();
        IoxReader reader;
        if (lowerName.endsWith(".itf")) {
            reader = new ItfReader(path.toFile());
        } else if (lowerName.endsWith(".xtf") || lowerName.endsWith(".xml")) {
            reader = new XtfReader(path.toFile());
        } else {
            throw new IllegalArgumentException("Unsupported input file type: " + path);
        }
        if (reader instanceof IoxIliReader iliReader) {
            iliReader.setModel(transferDescription);
        }
        return reader;
    }

    public IoxWriter createWriter(Path path, TransferDescription transferDescription) throws Exception {
        String lowerName = path.getFileName().toString().toLowerCase();
        if (lowerName.endsWith(".itf")) {
            return new ItfWriter(path.toFile(), transferDescription);
        }
        if (lowerName.endsWith(".xtf") || lowerName.endsWith(".xml")) {
            return new XtfWriter(path.toFile(), transferDescription);
        }
        throw new IllegalArgumentException("Unsupported output file type: " + path);
    }
}
