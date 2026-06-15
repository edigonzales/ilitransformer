package guru.interlis.transformer.model;

import ch.interlis.iom.IomObject;

@FunctionalInterface
public interface TransferInventoryClassifier {

    void classify(IomObject object, ClassificationSink sink);

    interface ClassificationSink {
        void addTag(String category, String value);
    }

    static TransferInventoryClassifier none() {
        return (object, sink) -> {
        };
    }
}
