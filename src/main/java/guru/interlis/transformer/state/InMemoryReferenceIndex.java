package guru.interlis.transformer.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class InMemoryReferenceIndex implements ReferenceIndex {

    private final Map<String, Map<String, List<Map.Entry<SourceObjectKey, TargetReference>>>> index = new HashMap<>();

    @Override
    public void add(SourceObjectKey source, TargetReference target) {
        index.computeIfAbsent(source.inputId(), ignored -> new HashMap<>())
                .computeIfAbsent(source.sourceOid(), ignored -> new ArrayList<>())
                .add(Map.entry(source, target));
    }

    @Override
    public List<TargetReference> find(SourceReferenceSelector selector) {
        if (selector.inputId() == null || selector.referencedSourceOid() == null) return List.of();
        return index
                .getOrDefault(selector.inputId(), Map.of())
                .getOrDefault(selector.referencedSourceOid(), List.of())
                .stream()
                .filter(entry -> selector.transferScoped()
                        || Objects.equals(selector.basketId(), entry.getKey().basketId()))
                .filter(entry -> selector.expectedSourceClass() == null
                        || selector.expectedSourceClass().equals(entry.getKey().sourceClass()))
                .map(Map.Entry::getValue)
                .toList();
    }

    @Override
    public List<TargetReference> findByRuleId(String ruleId) {
        if (ruleId == null || ruleId.isBlank()) return List.of();
        return index.values().stream()
                .flatMap(byOid -> byOid.values().stream())
                .flatMap(List::stream)
                .map(Map.Entry::getValue)
                .filter(ref -> ruleId.equals(ref.producingRuleId()))
                .toList();
    }
}
