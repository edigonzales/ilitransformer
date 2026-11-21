package guru.interlis.transformer.state;

import ch.interlis.iom.IomObject;

import java.util.List;
import java.util.Optional;

public interface StateStore {
    void addDeferredReference(DeferredReference ref);

    List<DeferredReference> deferredReferences();

    void addSourceRecord(SourceRecord sourceRecord);

    List<SourceRecord> sourceRecords();

    List<SourceRecord> sourceRecords(String inputId, String sourceClass);

    void registerTarget(TargetObjectKey key, IomObject object);

    boolean targetExists(TargetObjectKey key);

    Optional<IomObject> findTarget(TargetObjectKey key);
}
