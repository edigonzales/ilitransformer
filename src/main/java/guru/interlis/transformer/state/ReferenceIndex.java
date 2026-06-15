package guru.interlis.transformer.state;

import java.util.List;

public interface ReferenceIndex {
    void add(SourceObjectKey source, TargetReference target);

    List<TargetReference> find(SourceReferenceSelector selector);

    List<TargetReference> find(SourceReferenceSelector selector, boolean crossBasketFallback, boolean globalFallback);

    List<TargetReference> findByRuleId(String ruleId);
}
