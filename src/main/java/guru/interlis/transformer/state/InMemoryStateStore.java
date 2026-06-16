package guru.interlis.transformer.state;

import ch.interlis.iom.IomObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryStateStore implements StateStore {
    private final Map<SourceRefKey, List<TargetRefValue>> idMap = new HashMap<>();
    private final Map<String, List<IomObject>> sourceIndex = new HashMap<>();
    private final List<DeferredRef> deferredRefs = new ArrayList<>();
    private final List<DeferredReference> deferredReferences = new ArrayList<>();
    private final List<SourceRecord> sourceRecords = new ArrayList<>();
    private final Map<String, List<SourceRecord>> sourceRecordsByInputAndClass = new HashMap<>();
    private final Map<String, IomObject> targetIndex = new HashMap<>();
    private final AtomicLong oidSequence = new AtomicLong();

    @Override
    public void putIdMapping(SourceRefKey key, TargetRefValue value) {
        idMap.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
    }

    @Override
    public List<TargetRefValue> findIdMappings(
            String sourceClass, String sourceOid, String sourceFileId, String sourceBasketId) {
        List<TargetRefValue> exact =
                idMap.getOrDefault(new SourceRefKey(sourceClass, sourceOid, sourceFileId, sourceBasketId), List.of());
        if (!exact.isEmpty()) {
            return exact;
        }
        List<TargetRefValue> basketWide =
                idMap.getOrDefault(new SourceRefKey(sourceClass, sourceOid, sourceFileId, null), List.of());
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
    public void indexSourceObject(
            String sourceClass, String sourceFileId, String sourceBasketId, IomObject sourceObject) {
        sourceIndex
                .computeIfAbsent(key(sourceClass, sourceFileId, sourceBasketId), ignored -> new ArrayList<>())
                .add(sourceObject);
    }

    @Override
    public List<IomObject> listSourceObjects(String sourceClass, String sourceFileId, String sourceBasketId) {
        return sourceIndex.getOrDefault(key(sourceClass, sourceFileId, sourceBasketId), List.of());
    }

    @Override
    public Optional<IomObject> findTargetObject(String targetClass, String targetOid) {
        Optional<IomObject> exactLegacy = findTarget(new TargetObjectKey(null, targetClass, targetOid));
        if (exactLegacy.isPresent()) return exactLegacy;
        String suffix = "::" + targetClass + "::" + targetOid;
        return targetIndex.entrySet().stream()
                .filter(e -> e.getKey().endsWith(suffix))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    @Override
    public void indexTargetObject(String targetClass, String targetOid, IomObject targetObject) {
        registerTarget(new TargetObjectKey(null, targetClass, targetOid), targetObject);
    }

    @Override
    public void registerTarget(TargetObjectKey key, IomObject object) {
        if (targetExists(key)) {
            throw new DuplicateTargetOidException("Duplicate target OID: targetClass=" + key.targetClass()
                    + ", targetOid=" + key.targetOid()
                    + (key.outputId() != null ? ", outputId=" + key.outputId() : ""));
        }
        targetIndex.put(targetKey(key), object);
    }

    @Override
    public boolean targetExists(TargetObjectKey key) {
        return targetIndex.containsKey(targetKey(key));
    }

    @Override
    public Optional<IomObject> findTarget(TargetObjectKey key) {
        return Optional.ofNullable(targetIndex.get(targetKey(key)));
    }

    private static String targetKey(TargetObjectKey key) {
        return (key.outputId() == null ? "" : key.outputId()) + "::" + key.targetClass() + "::" + key.targetOid();
    }

    @Override
    public long nextOid() {
        return oidSequence.incrementAndGet();
    }

    @Override
    public String nextOid(
            OidStrategy strategy,
            String namespace,
            String ruleId,
            String sourceOid,
            Map<String, String> identityKeyValues) {
        return switch (strategy) {
            case INTEGER -> Long.toString(oidSequence.incrementAndGet());
            case PRESERVE -> sourceOid != null ? sourceOid : Long.toString(oidSequence.incrementAndGet());
            case UUID -> java.util.UUID.randomUUID().toString();
            case DETERMINISTIC_UUID -> generateDeterministicUuid(namespace, ruleId, identityKeyValues);
            case EXTERNAL -> null;
        };
    }

    private static String generateDeterministicUuid(
            String namespace, String ruleId, Map<String, String> identityKeyValues) {
        String ns = namespace != null ? namespace : "default";
        String keyPart = identityKeyValues != null && !identityKeyValues.isEmpty()
                ? String.join("|", identityKeyValues.values())
                : "";
        String name = ns + ":" + ruleId + ":" + keyPart;
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String key(String sourceClass, String sourceFileId, String sourceBasketId) {
        return sourceClass + "|" + (sourceFileId == null ? "" : sourceFileId) + "|"
                + (sourceBasketId == null ? "" : sourceBasketId);
    }

    private static String inputClassKey(String inputId, String sourceClass) {
        return inputId + "|" + sourceClass;
    }
}
