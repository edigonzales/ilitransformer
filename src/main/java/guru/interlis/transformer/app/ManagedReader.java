package guru.interlis.transformer.app;

import ch.interlis.iom.IomObject;
import ch.interlis.iox.*;
import ch.interlis.iox_j.EndTransferEvent;

/** Readers may be closed by the engine and by its owner on early failures. */
final class ManagedReader implements IoxReader {
    private final IoxReader delegate;
    private final boolean requireCompleteTransfer;
    private boolean closed;
    private boolean ended;

    ManagedReader(IoxReader delegate, boolean requireCompleteTransfer) {
        this.delegate = delegate;
        this.requireCompleteTransfer = requireCompleteTransfer;
    }

    @Override
    public IoxEvent read() throws IoxException {
        var event = delegate.read();
        if (event instanceof EndTransferEvent) ended = true;
        if (event == null && requireCompleteTransfer && !ended)
            throw new IoxException("Incomplete original transfer: END_TRANSFER required");
        return event;
    }

    @Override
    public void close() throws IoxException {
        if (!closed) {
            closed = true;
            delegate.close();
        }
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
    public IomObject createIomObject(String tag, String oid) throws IoxException {
        return delegate.createIomObject(tag, oid);
    }
}
