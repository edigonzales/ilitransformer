package guru.interlis.transformer.state;

import ch.interlis.iom.IomObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryStateStore implements StateStore {
    private final Map<SourceRefKey, List<TargetRefValue>> idMap = new HashMap<>();
    private final Map<String, List<IomObject>> sourceIndex = new HashMap<>();
    private final List<DeferredRef> deferredRefs = new ArrayList<>();
    private final List<SourceRecord> sourceRecords = new ArrayList<>();
    private final Map<String, IomObject> targetIndex = new HashMap<>();
    private final AtomicLong oidSequence = new AtomicLong();

    @Override
    public void putIdMapping(SourceRefKey key, TargetRefValue value) {
        idMap.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
    }

    @Override
    public List<TargetRefValue> findIdMappings(String sourceClass, String sourceOid, String sourceFileId, String sourceBasketId) {
        List<TargetRefValue> exact = idMap.getOrDefault(new SourceRefKey(sourceClass, sourceOid, sourceFileId, sourceBasketId), List.of());
        if (!exact.isEmpty()) {
            return exact;
        }
        List<TargetRefValue> basketWide = idMap.getOrDefault(new SourceRefKey(sourceClass, sourceOid, sourceFileId, null), List.of());
        if (!basketWide.isEmpty()) {
            return basketWide;
        }
        return idMap.getOrDefault(new SourceRefKey(sourceClass, sourceOid, null, null), List.of());
    }

    @Override
    public void addDeferredRef(DeferredRef deferredRef) {
        deferredRefs.add(deferredRef);
    }

    @Override
    public List<DeferredRef> deferredRefs() {
        return List.copyOf(deferredRefs);
    }

    @Override
    public void addSourceRecord(SourceRecord sourceRecord) {
        sourceRecords.add(sourceRecord);
    }

    @Override
    public List<SourceRecord> sourceRecords() {
        return List.copyOf(sourceRecords);
    }

    @Override
    public void indexSourceObject(String sourceClass, String sourceFileId, String sourceBasketId, IomObject sourceObject) {
        sourceIndex.computeIfAbsent(key(sourceClass, sourceFileId, sourceBasketId), ignored -> new ArrayList<>()).add(sourceObject);
    }

    @Override
    public List<IomObject> listSourceObjects(String sourceClass, String sourceFileId, String sourceBasketId) {
        return sourceIndex.getOrDefault(key(sourceClass, sourceFileId, sourceBasketId), List.of());
    }

    @Override
    public Optional<IomObject> findTargetObject(String targetClass, String targetOid) {
        return Optional.ofNullable(targetIndex.get(targetClass + "::" + targetOid));
    }

    @Override
    public void indexTargetObject(String targetClass, String targetOid, IomObject targetObject) {
        targetIndex.put(targetClass + "::" + targetOid, targetObject);
    }

    @Override
    public long nextOid() {
        return oidSequence.incrementAndGet();
    }

    private String key(String sourceClass, String sourceFileId, String sourceBasketId) {
        return sourceClass + "|" + (sourceFileId == null ? "" : sourceFileId) + "|" + (sourceBasketId == null ? "" : sourceBasketId);
    }
}
