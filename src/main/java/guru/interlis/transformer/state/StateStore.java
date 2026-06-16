package guru.interlis.transformer.state;

import ch.interlis.iom.IomObject;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface StateStore {
    void putIdMapping(SourceRefKey key, TargetRefValue value);

    List<TargetRefValue> findIdMappings(String sourceClass, String sourceOid, String sourceFileId, String sourceBasketId);

    void addDeferredRef(DeferredRef deferredRef);

    List<DeferredRef> deferredRefs();

    void addDeferredReference(DeferredReference ref);

    List<DeferredReference> deferredReferences();

    void addSourceRecord(SourceRecord sourceRecord);

    List<SourceRecord> sourceRecords();

    List<SourceRecord> sourceRecords(String inputId, String sourceClass);

    void indexSourceObject(String sourceClass, String sourceFileId, String sourceBasketId, IomObject sourceObject);

    List<IomObject> listSourceObjects(String sourceClass, String sourceFileId, String sourceBasketId);

    Optional<IomObject> findTargetObject(String targetClass, String targetOid);

    void registerTarget(TargetObjectKey key, IomObject object);

    boolean targetExists(TargetObjectKey key);

    Optional<IomObject> findTarget(TargetObjectKey key);

    void indexTargetObject(String targetClass, String targetOid, IomObject targetObject);

    /** @deprecated Use {@link #nextOid(OidStrategy, String, String, String, Map)} */
    @Deprecated
    long nextOid();

    String nextOid(OidStrategy strategy, String namespace, String ruleId,
                   String sourceOid, Map<String, String> identityKeyValues);
}
