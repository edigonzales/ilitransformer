package guru.interlis.transformer.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class InMemoryReferenceIndex implements ReferenceIndex {

    private final Map<SourceObjectKey, List<TargetReference>> index = new HashMap<>();

    @Override
    public void add(SourceObjectKey source, TargetReference target) {
        index.computeIfAbsent(source, ignored -> new ArrayList<>()).add(target);
    }

    @Override
    public List<TargetReference> find(SourceReferenceSelector selector) {
        return find(selector, true, false);
    }

    @Override
    public List<TargetReference> find(SourceReferenceSelector selector, boolean crossBasketFallback, boolean globalFallback) {
        // 1. Exact match: inputId, basketId, class, OID
        List<TargetReference> exact = index.get(new SourceObjectKey(
                selector.inputId(),
                selector.basketId(),
                selector.expectedSourceClass(),
                selector.referencedSourceOid()));
        if (exact != null && !exact.isEmpty()) {
            return exact;
        }

        // 2. Cross-basket: inputId, class, OID (ignore basketId)
        if (crossBasketFallback) {
            List<TargetReference> matches = findMatchingEntries(
                    selector.inputId(), selector.expectedSourceClass(), selector.referencedSourceOid(), false);
            if (!matches.isEmpty()) {
                return matches;
            }
        }

        // 3. Global OID-only fallback (only if explicitly enabled)
        if (globalFallback) {
            List<TargetReference> matches = findMatchingEntries(null, null, selector.referencedSourceOid(), true);
            if (!matches.isEmpty()) {
                return matches;
            }
        }

        return List.of();
    }

    private List<TargetReference> findMatchingEntries(String inputId, String sourceClass, String sourceOid, boolean oidOnly) {
        List<TargetReference> result = new ArrayList<>();
        for (var entry : index.entrySet()) {
            SourceObjectKey key = entry.getKey();
            if (sourceOid != null && !sourceOid.equals(key.sourceOid())) continue;
            if (!oidOnly) {
                if (inputId != null && !inputId.equals(key.inputId())) continue;
                if (sourceClass != null && !sourceClass.equals(key.sourceClass())) continue;
            }
            result.addAll(entry.getValue());
        }
        return result;
    }
}
