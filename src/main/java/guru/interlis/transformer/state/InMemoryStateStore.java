package guru.interlis.transformer.state;

import ch.interlis.iom.IomObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryStateStore implements StateStore {
    private final List<DeferredReference> deferredReferences = new ArrayList<>();
    private final List<SourceRecord> sourceRecords = new ArrayList<>();
    private final Map<String, List<SourceRecord>> sourceRecordsByInputAndClass = new HashMap<>();
    private final Map<TargetObjectKey, IomObject> targetIndex = new HashMap<>();

    @Override
    public void addDeferredReference(DeferredReference ref) {
        deferredReferences.add(ref);
    }

    @Override
    public List<DeferredReference> deferredReferences() {
        return List.copyOf(deferredReferences);
    }

    @Override
    public void addSourceRecord(SourceRecord sourceRecord) {
        sourceRecords.add(sourceRecord);
        sourceRecordsByInputAndClass
                .computeIfAbsent(
                        inputClassKey(sourceRecord.sourceFileId(), sourceRecord.sourceClass()),
                        ignored -> new ArrayList<>())
                .add(sourceRecord);
    }

    @Override
    public List<SourceRecord> sourceRecords() {
        return List.copyOf(sourceRecords);
    }

    @Override
    public List<SourceRecord> sourceRecords(String inputId, String sourceClass) {
        return List.copyOf(sourceRecordsByInputAndClass.getOrDefault(inputClassKey(inputId, sourceClass), List.of()));
    }

    @Override
    public void registerTarget(TargetObjectKey key, IomObject object) {
        if (targetExists(key)) {
            throw new DuplicateTargetOidException("Duplicate target OID: targetClass=" + key.targetClass()
                    + ", targetOid=" + key.targetOid()
                    + (key.outputId() != null ? ", outputId=" + key.outputId() : ""));
        }
        targetIndex.put(key, object);
    }

    @Override
    public boolean targetExists(TargetObjectKey key) {
        return targetIndex.containsKey(key);
    }

    @Override
    public Optional<IomObject> findTarget(TargetObjectKey key) {
        return Optional.ofNullable(targetIndex.get(key));
    }

    private static String inputClassKey(String inputId, String sourceClass) {
        return inputId + "|" + sourceClass;
    }
}
