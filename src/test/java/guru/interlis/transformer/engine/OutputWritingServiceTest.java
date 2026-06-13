package guru.interlis.transformer.engine;

import static org.assertj.core.api.Assertions.assertThat;

import ch.interlis.iom.IomObject;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxFactoryCollection;
import ch.interlis.iox.IoxWriter;
import ch.interlis.iox.ObjectEvent;
import ch.interlis.iox.StartTransferEvent;
import ch.interlis.iom_j.Iom_jObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OutputWritingServiceTest {

    @Test
    void writesIlitransformerAsTransferSender() throws Exception {
        CapturingWriter writer = new CapturingWriter();
        IomObject target = new Iom_jObject("Model.Topic.Target", "o1");

        Map<String, Map<String, List<IomObject>>> objectsByOutputAndBasket = new LinkedHashMap<>();
        objectsByOutputAndBasket.put("out1", Map.of("Model.Topic::basket-1", List.of(target)));

        long written = new OutputWritingService().writeOutputs(
                Map.of("out1", writer), objectsByOutputAndBasket);

        assertThat(written).isEqualTo(1);
        assertThat(writer.startTransferEvent).isNotNull();
        assertThat(writer.startTransferEvent.getSender()).isEqualTo("ilitransformer");
        assertThat(writer.objectEvents)
                .extracting(objectEvent -> objectEvent.getIomObject().getobjectoid())
                .containsExactly("o1");
    }

    private static final class CapturingWriter implements IoxWriter {
        private StartTransferEvent startTransferEvent;
        private final List<ObjectEvent> objectEvents = new ArrayList<>();

        @Override
        public void write(IoxEvent event) {
            if (event instanceof StartTransferEvent startTransfer) {
                this.startTransferEvent = startTransfer;
            }
            if (event instanceof ObjectEvent objectEvent) {
                objectEvents.add(objectEvent);
            }
        }

        @Override
        public void close() {
        }

        @Override
        public void flush() {
        }

        @Override
        public void setFactory(IoxFactoryCollection factory) {
        }

        @Override
        public IoxFactoryCollection getFactory() {
            return null;
        }

        @Override
        public IomObject createIomObject(String type, String oid) {
            return new Iom_jObject(type, oid);
        }
    }
}
