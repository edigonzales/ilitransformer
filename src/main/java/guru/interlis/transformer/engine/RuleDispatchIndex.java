package guru.interlis.transformer.engine;

import guru.interlis.transformer.mapping.plan.BagPlan;
import guru.interlis.transformer.mapping.plan.RulePlan;
import guru.interlis.transformer.mapping.plan.SourcePlan;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.TypeSystemFacade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RuleDispatchIndex {

    private final Map<String, List<RulePlan>> rulesByInputAndClass;
    private final Map<String, List<BagPlan>> embedBagsByInputAndClass;
    private final List<BagExpansionEntry> expandBags;

    private RuleDispatchIndex(
            Map<String, List<RulePlan>> rulesByInputAndClass,
            Map<String, List<BagPlan>> embedBagsByInputAndClass,
            List<BagExpansionEntry> expandBags) {
        this.rulesByInputAndClass = Collections.unmodifiableMap(rulesByInputAndClass);
        this.embedBagsByInputAndClass = Collections.unmodifiableMap(embedBagsByInputAndClass);
        this.expandBags = Collections.unmodifiableList(expandBags);
    }

    public static RuleDispatchIndex build(TransformPlan plan) {
        Map<String, List<RulePlan>> rulesMap = new LinkedHashMap<>();
        Map<String, List<BagPlan>> embedMap = new LinkedHashMap<>();
        List<BagExpansionEntry> expandList = new ArrayList<>();

        for (RulePlan rule : plan.rules()) {
            for (SourcePlan sp : rule.sources()) {
                if (sp.sourceClass() == null) continue;
                for (String inputId : sp.inputIds()) {
                    String className = TypeSystemFacade.getScopedName(sp.sourceClass());
                    String key = key(inputId, className);
                    rulesMap.computeIfAbsent(key, k -> new ArrayList<>()).add(rule);
                }
            }

            for (BagPlan bag : rule.bags()) {
                if (bag.fromSource().sourceClass() == null) continue;
                for (String inputId : bag.fromSource().inputIds()) {
                    SourcePlan parentSource = null;
                    for (SourcePlan rsp : rule.sources()) {
                        if (rsp.inputIds().contains(inputId)) {
                            parentSource = rsp;
                            break;
                        }
                    }
                    collectBagsRecursive(bag, inputId, embedMap, expandList, parentSource, rule);
                }
            }
        }

        return new RuleDispatchIndex(rulesMap, embedMap, expandList);
    }

    public List<RulePlan> rulesFor(String inputId, String sourceClass) {
        return rulesByInputAndClass.getOrDefault(key(inputId, sourceClass), List.of());
    }

    public List<BagPlan> embedBagsFor(String inputId, String sourceClass) {
        return embedBagsByInputAndClass.getOrDefault(key(inputId, sourceClass), List.of());
    }

    public List<BagExpansionEntry> expandBagsFor(String inputId, String sourceClass) {
        List<BagExpansionEntry> result = new ArrayList<>();
        for (BagExpansionEntry entry : expandBags) {
            if (entry.parentSource.inputIds().contains(inputId)
                    && TypeSystemFacade.getScopedName(entry.parentSource.sourceClass()).equals(sourceClass)) {
                result.add(entry);
            }
        }
        return result;
    }

    public List<BagExpansionEntry> allExpandBags() {
        return expandBags;
    }

    private static String key(String inputId, String sourceClass) {
        return (inputId != null ? inputId : "*") + "::" + sourceClass;
    }

    private static void collectBagsRecursive(BagPlan bag, String inputId,
                                              Map<String, List<BagPlan>> embedMap,
                                              List<BagExpansionEntry> expandList,
                                              SourcePlan parentSource, RulePlan rule) {
        if (bag.fromSource().sourceClass() == null) return;
        String className = TypeSystemFacade.getScopedName(bag.fromSource().sourceClass());
        String key = key(inputId, className);
        if (bag.isEmbed()) {
            embedMap.computeIfAbsent(key, k -> new ArrayList<>()).add(bag);
        } else if (bag.isExpand()) {
            if (parentSource != null) {
                expandList.add(new BagExpansionEntry(bag, parentSource, rule));
            }
        }
        for (BagPlan nested : bag.nestedBags()) {
            collectBagsRecursive(nested, inputId, embedMap, expandList, parentSource, rule);
        }
    }

    public record BagExpansionEntry(BagPlan bag, SourcePlan parentSource, RulePlan parentRule) {}
}
