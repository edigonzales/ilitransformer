package guru.interlis.transformer.io;

import static org.assertj.core.api.Assertions.*;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.EndTransferEvent;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxException;
import ch.interlis.iox.IoxFactoryCollection;
import ch.interlis.iox.IoxReader;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.Test;

class EndTransferAwareReaderTest {

    @Test
    void endTransferAwareReaderReturnsNullAfterEndTransfer() throws Exception {
        IoxEvent start = new ch.interlis.iox_j.StartTransferEvent("t", null, null);
        IoxEvent end = new ch.interlis.iox_j.EndTransferEvent();
        StubReader stub = new StubReader(start, end);
        EndTransferAwareReader reader = new EndTransferAwareReader(stub);

        assertThat(reader.read()).isSameAs(start);
        assertThat(reader.read()).isSameAs(end);
        // After the EndTransferEvent the wrapper must keep returning null and must not delegate again.
        assertThat(reader.read()).isNull();
        assertThat(reader.read()).isNull();
        assertThat(stub.delegatedAfterEnd).isFalse();
    }

    @Test
    void delegatesCloseAndCreateIomObject() throws Exception {
        StubReader stub = new StubReader();
        EndTransferAwareReader reader = new EndTransferAwareReader(stub);

        IomObject created = reader.createIomObject("Model.Topic.Class", "o1");
        assertThat(created.getobjecttag()).isEqualTo("Model.Topic.Class");

        reader.close();
        assertThat(stub.closed).isTrue();
    }

    private static final class StubReader implements IoxReader {
        private final Deque<IoxEvent> events;
        private boolean endReturned;
        private boolean closed;
        private boolean delegatedAfterEnd;
        private IoxFactoryCollection factory;

        private StubReader(IoxEvent... evs) {
            this.events = new ArrayDeque<>(List.of(evs));
        }

        @Override
        public IoxEvent read() throws IoxException {
            if (endReturned) {
                delegatedAfterEnd = true;
            }
            IoxEvent event = events.poll();
            if (event instanceof EndTransferEvent) {
                endReturned = true;
            }
            return event;
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public void setFactory(IoxFactoryCollection factory) {
            this.factory = factory;
        }

        @Override
        public IoxFactoryCollection getFactory() {
            return factory;
        }

        @Override
        public IomObject createIomObject(String type, String oid) {
            return new Iom_jObject(type, oid);
        }
    }
}
