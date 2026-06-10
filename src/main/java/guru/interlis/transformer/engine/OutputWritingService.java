package guru.interlis.transformer.engine;

import ch.interlis.iom.IomObject;
import ch.interlis.iox.IoxWriter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class OutputWritingService {

    public long writeOutputs(
            Map<String, IoxWriter> writersByOutputId,
            Map<String, Map<String, List<IomObject>>> objectsByOutputAndBasket) throws Exception {
        long written = 0;
        for (var entry : writersByOutputId.entrySet()) {
            String outputId = entry.getKey();
            IoxWriter writer = entry.getValue();
            writer.write(new ch.interlis.iox_j.StartTransferEvent("ili-transformer", null, null));
            Map<String, List<IomObject>> byBasket = objectsByOutputAndBasket.getOrDefault(outputId, Map.of());
            for (var basketEntry : byBasket.entrySet()) {
                String[] parts = basketEntry.getKey().split("::", 2);
                String topic = parts[0];
                String basketId = parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null;
                writer.write(new ch.interlis.iox_j.StartBasketEvent(topic, basketId));
                List<IomObject> sorted = new ArrayList<>(basketEntry.getValue());
                sorted.sort(targetObjectComparator());
                for (IomObject target : sorted) {
                    writer.write(new ch.interlis.iox_j.ObjectEvent(target));
                    written++;
                }
                writer.write(new ch.interlis.iox_j.EndBasketEvent());
            }
            writer.write(new ch.interlis.iox_j.EndTransferEvent());
            writer.flush();
            writer.close();
        }
        return written;
    }

    public static Comparator<IomObject> targetObjectComparator() {
        return Comparator.comparingInt(OutputWritingService::referenceWeight)
                .thenComparing(IomObject::getobjecttag)
                .thenComparing(IomObject::getobjectoid);
    }

    private static int referenceWeight(IomObject object) {
        return hasReference(object) ? 1 : 0;
    }

    private static boolean hasReference(IomObject object) {
        if (object == null) return false;
        if (object.getobjectrefoid() != null) return true;
        for (int i = 0; i < object.getattrcount(); i++) {
            String attrName = object.getattrname(i);
            int valueCount = object.getattrvaluecount(attrName);
            for (int valueIdx = 0; valueIdx < valueCount; valueIdx++) {
                if (hasReference(object.getattrobj(attrName, valueIdx))) {
                    return true;
                }
            }
        }
        return false;
    }
}
