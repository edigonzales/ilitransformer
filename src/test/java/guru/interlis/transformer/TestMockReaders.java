package guru.interlis.transformer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxReader;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

final class TestMockReaders {

    private TestMockReaders() {}

    static IoxReader mockReader(Iom_jObject... objects) {
        IoxReader reader = mock(IoxReader.class);
        List<IoxEvent> events = new ArrayList<>();
        events.add(new ch.interlis.iox_j.StartTransferEvent("t", null, null));
        events.add(new ch.interlis.iox_j.StartBasketEvent("TestModel.TestTopic", "b1"));
        for (var obj : objects) {
            events.add(new ch.interlis.iox_j.ObjectEvent(obj));
        }
        events.add(new ch.interlis.iox_j.EndBasketEvent());
        events.add(new ch.interlis.iox_j.EndTransferEvent());
        Iterator<IoxEvent> iter = events.iterator();
        try {
            when(reader.read()).thenAnswer(inv -> iter.hasNext() ? iter.next() : null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return reader;
    }
}
