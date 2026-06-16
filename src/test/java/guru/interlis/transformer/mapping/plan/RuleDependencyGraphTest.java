package guru.interlis.transformer.mapping.plan;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class RuleDependencyGraphTest {

    @Test
    void topologicalOrderNoDependencies() {
        RulePlan a = rule("a");
        RulePlan b = rule("b");
        RulePlan c = rule("c");

        RuleDependencyGraph graph = new RuleDependencyGraph(List.of(a, b, c));
        List<String> order = graph.topologicalOrder();

        assertThat(order).containsExactly("a", "b", "c");
        assertThat(graph.cycles()).isEmpty();
    }

    @Test
    void topologicalOrderWithDependencies() {
        RulePlan a = rule("a"); // no deps
        RulePlan b = rule("b", ref("a")); // b depends on a
        RulePlan c = rule("c", ref("b")); // c depends on b

        RuleDependencyGraph graph = new RuleDependencyGraph(List.of(c, b, a));
        List<String> order = graph.topologicalOrder();

        assertThat(order).containsExactly("a", "b", "c");
        assertThat(graph.cycles()).isEmpty();
    }

    @Test
    void cycleDetection() {
        RulePlan a = rule("a", ref("b")); // a -> b
        RulePlan b = rule("b", ref("a")); // b -> a

        RuleDependencyGraph graph = new RuleDependencyGraph(List.of(a, b));
        List<List<String>> cycles = graph.cycles();

        assertThat(cycles).isNotEmpty();
    }

    @Test
    void noCycleWhenNoDependencies() {
        RulePlan a = rule("a");
        RulePlan b = rule("b");

        RuleDependencyGraph graph = new RuleDependencyGraph(List.of(a, b));
        assertThat(graph.cycles()).isEmpty();
    }

    private static RulePlan rule(String id, RefPlan... refs) {
        return new RulePlan(
                id,
                "",
                null,
                List.of(),
                List.of(),
                List.of(refs),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of());
    }

    private static RefPlan ref(String targetRuleId) {
        return new RefPlan(null, null, null, targetRuleId, false);
    }
}
