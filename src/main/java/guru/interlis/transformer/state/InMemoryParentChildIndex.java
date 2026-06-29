package guru.interlis.transformer.state;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InMemoryParentChildIndex implements ParentChildIndex {

    private final Map<String, Map<String, Map<String, List<SourceRecord>>>> index = new LinkedHashMap<>();

    @Override
    public void index(String sourceClass, String referenceAttribute, String parentOid, SourceRecord child) {
        if (sourceClass == null || referenceAttribute == null || parentOid == null || child == null) return;
        List<SourceRecord> children = index.computeIfAbsent(sourceClass, k -> new LinkedHashMap<>())
                .computeIfAbsent(referenceAttribute, k -> new LinkedHashMap<>())
                .computeIfAbsent(parentOid, k -> new ArrayList<>());
        if (children.stream().noneMatch(existing -> sameSourceRecord(existing, child))) {
            children.add(child);
        }
    }

    @Override
    public List<SourceRecord> children(String sourceClass, String referenceAttribute, String parentOid) {
        if (sourceClass == null || referenceAttribute == null || parentOid == null) return List.of();
        Map<String, Map<String, List<SourceRecord>>> classIndex = index.get(sourceClass);
        if (classIndex == null) return List.of();
        Map<String, List<SourceRecord>> refIndex = classIndex.get(referenceAttribute);
        if (refIndex == null) return List.of();
        List<SourceRecord> children = refIndex.get(parentOid);
        return children != null ? List.copyOf(children) : List.of();
    }

    private static boolean sameSourceRecord(SourceRecord left, SourceRecord right) {
        String leftOid = left.sourceObject().getobjectoid();
        String rightOid = right.sourceObject().getobjectoid();
        boolean sameObject = leftOid != null || rightOid != null
                ? java.util.Objects.equals(leftOid, rightOid)
                : left.sourceObject() == right.sourceObject();
        return java.util.Objects.equals(left.sourceFileId(), right.sourceFileId())
                && java.util.Objects.equals(left.sourceBasketId(), right.sourceBasketId())
                && java.util.Objects.equals(left.sourceClass(), right.sourceClass())
                && sameObject;
    }
}
