package guru.interlis.transformer.expr;

import static org.assertj.core.api.Assertions.*;

import guru.interlis.transformer.mapping.plan.TypeInfo;

import java.util.List;

import org.junit.jupiter.api.Test;

class FunctionRegistryTest {

    @Test
    void registersAndResolvesFunction() {
        FunctionRegistry registry = new FunctionRegistry();
        registry.register("myFunc", TypeInfo.TEXT, List.of(), (args, ctx) -> new TextValue("hello"));

        assertThat(registry.resolve("myFunc")).isPresent();
        assertThat(registry.resolve("myFunc").get().returnType()).isEqualTo(TypeInfo.TEXT);
        assertThat(registry.resolve("myFunc").get().deterministic()).isTrue();
    }

    @Test
    void resolvesCaseInsensitively() {
        FunctionRegistry registry = new FunctionRegistry();
        registry.register(
                "Truncate",
                TypeInfo.TEXT,
                List.of(new FunctionDef.FunctionParam("v", TypeInfo.TEXT)),
                (args, ctx) -> new TextValue(""));

        assertThat(registry.resolve("truncate")).isPresent();
        assertThat(registry.resolve("TRUNCATE")).isPresent();
        assertThat(registry.resolve("Truncate")).isPresent();
    }

    @Test
    void rejectsDuplicateRegistration() {
        FunctionRegistry registry = new FunctionRegistry();
        registry.register("f", TypeInfo.TEXT, List.of(), (a, c) -> new TextValue(""));
        assertThatThrownBy(() -> registry.register(
                        "f", TypeInfo.NUMERIC, List.of(), (a, c) -> new NumberValue(java.math.BigDecimal.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void returnsEmptyForUnknownFunction() {
        FunctionRegistry registry = new FunctionRegistry();
        assertThat(registry.resolve("nonexistent")).isEmpty();
    }

    @Test
    void nonDeterministicRegistration() {
        FunctionRegistry registry = new FunctionRegistry();
        registry.registerNonDeterministic(
                "rand", TypeInfo.NUMERIC, List.of(), (args, ctx) -> NumberValue.of(Math.random()));

        var def = registry.resolve("rand");
        assertThat(def).isPresent();
        assertThat(def.get().deterministic()).isFalse();
    }

    @Test
    void defaultRegistryContainsAllBuiltinFunctionGroups() {
        FunctionRegistry registry = FunctionRegistry.defaultRegistry();

        assertThat(registry.resolve("coalesce")).isPresent();
        assertThat(registry.resolve("truncate")).isPresent();
        assertThat(registry.resolve("toXmlDateTime")).isPresent();
        assertThat(registry.resolve("enumMap")).isPresent();
        assertThat(registry.resolve("refEquals")).isPresent();
        assertThat(registry.resolve("div")).isPresent();
        assertThat(registry.resolve("lookup")).isPresent();
        assertThat(registry.resolve("existsIn")).isPresent();
        assertThat(registry.resolve("coordEquals")).isPresent();
        assertThat(registry.resolve("pointOnSurface")).isPresent();
    }
}
