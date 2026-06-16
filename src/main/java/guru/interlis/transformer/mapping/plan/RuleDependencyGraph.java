package guru.interlis.transformer.mapping.plan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RuleDependencyGraph {

    private final List<RulePlan> rules;
    private final Map<String, Integer> ruleIndex;
    private final List<List<Integer>> adjacency;

    public RuleDependencyGraph(List<RulePlan> rules) {
        this.rules = List.copyOf(rules);
        this.ruleIndex = new HashMap<>();
        for (int i = 0; i < rules.size(); i++) {
            ruleIndex.put(rules.get(i).ruleId(), i);
        }
        this.adjacency = new ArrayList<>(rules.size());
        for (int i = 0; i < rules.size(); i++) {
            adjacency.add(new ArrayList<>());
        }
        buildEdges();
    }

    private void buildEdges() {
        for (int i = 0; i < rules.size(); i++) {
            RulePlan rule = rules.get(i);

            // Dependencies from RefPlan.targetRuleId:
            // If rule i references rule depIdx, then depIdx must execute first.
            // Edge direction: depIdx -> i (depIdx comes before i)
            for (RefPlan ref : rule.refs()) {
                if (ref.targetRuleId() != null && !ref.targetRuleId().isBlank()) {
                    Integer depIdx = ruleIndex.get(ref.targetRuleId());
                    if (depIdx != null && depIdx != i) {
                        adjacency.get(depIdx).add(i);
                    }
                }
            }

            // Dependencies from CreatePlan refs
            for (CreatePlan create : rule.creates()) {
                for (RefPlan ref : create.references()) {
                    if (ref.targetRuleId() != null && !ref.targetRuleId().isBlank()) {
                        Integer depIdx = ruleIndex.get(ref.targetRuleId());
                        if (depIdx != null && depIdx != i) {
                            adjacency.get(depIdx).add(i);
                        }
                    }
                }
            }
        }
    }

    public List<String> topologicalOrder() {
        List<List<String>> cycles = cycles();
        if (!cycles.isEmpty()) {
            // Return order ignoring cycles (for partial generation)
            List<String> result = new ArrayList<>();
            for (RulePlan rule : rules) {
                result.add(rule.ruleId());
            }
            return result;
        }

        int[] inDegree = new int[rules.size()];
        for (int u = 0; u < rules.size(); u++) {
            for (int v : adjacency.get(u)) {
                inDegree[v]++;
            }
        }

        List<Integer> queue = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            if (inDegree[i] == 0) {
                queue.add(i);
            }
        }

        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            int u = queue.removeFirst();
            order.add(rules.get(u).ruleId());
            for (int v : adjacency.get(u)) {
                inDegree[v]--;
                if (inDegree[v] == 0) {
                    queue.add(v);
                }
            }
        }
        return order;
    }

    public List<List<String>> cycles() {
        List<List<String>> result = new ArrayList<>();
        boolean[] visited = new boolean[rules.size()];
        boolean[] onStack = new boolean[rules.size()];
        List<Integer> stack = new ArrayList<>();

        for (int i = 0; i < rules.size(); i++) {
            if (!visited[i]) {
                findCycles(i, visited, onStack, stack, result);
            }
        }
        return result;
    }

    private void findCycles(
            int u, boolean[] visited, boolean[] onStack, List<Integer> stack, List<List<String>> result) {
        visited[u] = true;
        onStack[u] = true;
        stack.add(u);

        for (int v : adjacency.get(u)) {
            if (!visited[v]) {
                findCycles(v, visited, onStack, stack, result);
            } else if (onStack[v]) {
                List<String> cycle = new ArrayList<>();
                for (int k = stack.indexOf(v); k < stack.size(); k++) {
                    cycle.add(rules.get(stack.get(k)).ruleId());
                }
                cycle.add(rules.get(v).ruleId());
                result.add(cycle);
            }
        }

        stack.removeLast();
        onStack[u] = false;
    }
}
