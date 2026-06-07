package guru.interlis.transformer.state;

import ch.interlis.iom.IomObject;
import java.util.List;
import java.util.Optional;

public interface StateStore {
    void putIdMapping(SourceRefKey key, TargetRefValue value);

    List<TargetRefValue> findIdMappings(String sourceClass, String sourceOid, String sourceFileId, String sourceBasketId);

    void addDeferredRef(DeferredRef deferredRef);

    List<DeferredRef> deferredRefs();

    void addSourceRecord(SourceRecord sourceRecord);

    List<SourceRecord> sourceRecords();

    void indexSourceObject(String sourceClass, String sourceFileId, String sourceBasketId, IomObject sourceObject);

    List<IomObject> listSourceObjects(String sourceClass, String sourceFileId, String sourceBasketId);

    Optional<IomObject> findTargetObject(String targetClass, String targetOid);

    void indexTargetObject(String targetClass, String targetOid, IomObject targetObject);

    long nextOid();
}
