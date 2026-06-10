package guru.interlis.transformer.interlis;

import guru.interlis.transformer.geometry.ItfGeometryWriter;
import guru.interlis.transformer.diag.DiagnosticCollector;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.itf.ItfReader2;
import ch.interlis.iom_j.itf.ItfWriter;
import ch.interlis.iom_j.xtf.Xtf24Reader;
import ch.interlis.iom_j.xtf.XtfWriter;
import ch.interlis.iox.EndTransferEvent;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxFactoryCollection;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;
import ch.interlis.iox.IoxException;
import ch.interlis.iox_j.IoxIliReader;
import java.nio.file.Path;

public final class InterlisIoFactory {
    public IoxReader createReader(Path path, TransferDescription transferDescription) throws Exception {
        String lowerName = path.getFileName().toString().toLowerCase();
        IoxReader reader;
        if (lowerName.endsWith(".itf")) {
            // ItfReader2 merges AREA/SURFACE helper tables into canonical geometry objects.
            reader = new ItfReader2(path.toFile(), false);
        } else if (lowerName.endsWith(".xtf") || lowerName.endsWith(".xml")) {
            reader = Xtf24Reader.createReader(path.toFile());
        } else {
            throw new IllegalArgumentException("Unsupported input file type: " + path);
        }
        if (reader instanceof IoxIliReader iliReader) {
            iliReader.setModel(transferDescription);
        }
        return new EndTransferAwareReader(reader);
    }

    public IoxWriter createWriter(Path path, TransferDescription transferDescription) throws Exception {
        return createWriter(path, transferDescription, null);
    }

    public IoxWriter createWriter(Path path, TransferDescription transferDescription,
                                  DiagnosticCollector diagnostics) throws Exception {
        String lowerName = path.getFileName().toString().toLowerCase();
        if (lowerName.endsWith(".itf")) {
            return new ItfGeometryWriter(
                    new ItfWriter(path.toFile(), transferDescription), transferDescription, diagnostics);
        }
        if (lowerName.endsWith(".xtf") || lowerName.endsWith(".xml")) {
            return new XtfWriter(path.toFile(), transferDescription);
        }
        throw new IllegalArgumentException("Unsupported output file type: " + path);
    }

    private static final class EndTransferAwareReader implements IoxReader {
        private final IoxReader delegate;
        private boolean endTransferSeen;

        private EndTransferAwareReader(IoxReader delegate) {
            this.delegate = delegate;
        }

        @Override
        public IoxEvent read() throws IoxException {
            if (endTransferSeen) {
                return null;
            }
            IoxEvent event = delegate.read();
            if (event instanceof EndTransferEvent) {
                endTransferSeen = true;
            }
            return event;
        }

        @Override
        public void close() throws IoxException {
            delegate.close();
        }

        @Override
        public void setFactory(IoxFactoryCollection factory) throws IoxException {
            delegate.setFactory(factory);
        }

        @Override
        public IoxFactoryCollection getFactory() throws IoxException {
            return delegate.getFactory();
        }

        @Override
        public IomObject createIomObject(String type, String oid) throws IoxException {
            return delegate.createIomObject(type, oid);
        }
    }
}
