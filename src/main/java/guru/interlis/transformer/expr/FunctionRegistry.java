package guru.interlis.transformer.expr;

import guru.interlis.transformer.mapping.plan.TypeInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class FunctionRegistry {

    private final Map<String, FunctionDef> functions = new LinkedHashMap<>();

    public void register(FunctionDef def) {
        String key = def.name().toLowerCase();
        if (functions.containsKey(key)) {
            throw new IllegalArgumentException("Duplicate function registration: " + def.name());
        }
        functions.put(key, def);
    }

    public void register(
            String name, TypeInfo returnType, List<FunctionDef.FunctionParam> params, FunctionImplementation impl) {
        register(FunctionDef.eager(name, returnType, params, impl));
    }

    public void registerNonDeterministic(
            String name, TypeInfo returnType, List<FunctionDef.FunctionParam> params, FunctionImplementation impl) {
        register(FunctionDef.eagerNonDeterministic(name, returnType, params, impl));
    }

    public Optional<FunctionDef> resolve(String name) {
        return Optional.ofNullable(functions.get(name.toLowerCase()));
    }

    public Map<String, FunctionDef> all() {
        return Map.copyOf(functions);
    }
}
