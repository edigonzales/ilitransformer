package guru.interlis.transformer.io;

import ch.interlis.iom.IomObject;
import ch.interlis.iox.EndTransferEvent;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxException;
import ch.interlis.iox.IoxFactoryCollection;
import ch.interlis.iox.IoxReader;

/**
 * Wraps an {@link IoxReader} so that {@link #read()} returns {@code null} on every call after the
 * first {@link EndTransferEvent}. All other operations delegate to the wrapped reader.
 *
 * <p>Extracted unchanged from the former {@code InterlisIoFactory.EndTransferAwareReader} so the
 * observable contract stays identical: a reader yields exactly one {@code EndTransferEvent} and then
 * returns {@code null} on every subsequent read.
 */
public final class EndTransferAwareReader implements IoxReader {
    private final IoxReader delegate;
    private boolean endTransferSeen;

    public EndTransferAwareReader(IoxReader delegate) {
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
