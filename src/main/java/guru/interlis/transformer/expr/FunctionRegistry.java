package guru.interlis.transformer.expr;

import guru.interlis.transformer.expr.builtins.BasicFunctions;
import guru.interlis.transformer.expr.builtins.DateFunctions;
import guru.interlis.transformer.expr.builtins.EnumFunctions;
import guru.interlis.transformer.expr.builtins.GeometryFunctions;
import guru.interlis.transformer.expr.builtins.LookupFunctions;
import guru.interlis.transformer.expr.builtins.MathFunctions;
import guru.interlis.transformer.expr.builtins.RefFunctions;
import guru.interlis.transformer.expr.builtins.StringFunctions;
import guru.interlis.transformer.mapping.plan.TypeInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class FunctionRegistry {

    private final Map<String, FunctionDef> functions = new LinkedHashMap<>();

    public static FunctionRegistry defaultRegistry() {
        FunctionRegistry registry = new FunctionRegistry();
        BasicFunctions.registerAll(registry);
        StringFunctions.registerAll(registry);
        DateFunctions.registerAll(registry);
        EnumFunctions.registerAll(registry);
        RefFunctions.registerAll(registry);
        MathFunctions.registerAll(registry);
        LookupFunctions.registerAll(registry);
        GeometryFunctions.registerAll(registry);
        return registry;
    }

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
