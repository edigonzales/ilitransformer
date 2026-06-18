package guru.interlis.transformer.mapping.ilimap.semantic;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleBlock;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class IlimapSymbolTable {
    private final IlimapScope topLevel = new IlimapScope(null);
    private final Map<String, IlimapScope> ruleScopes = new LinkedHashMap<>();

    public IlimapScope topLevelScope() {
        return topLevel;
    }

    public IlimapScope scopeFor(IlimapRuleBlock rule) {
        return ruleScopes.computeIfAbsent(rule.id(), id -> new IlimapScope(topLevel));
    }

    public Optional<IlimapSymbol> resolveRule(String id) {
        return topLevel.resolveLocal(id).filter(s -> s.kind() == IlimapSymbolKind.RULE);
    }

    public Optional<IlimapSymbol> resolveInput(String id) {
        return topLevel.resolveLocal(id).filter(s -> s.kind() == IlimapSymbolKind.INPUT);
    }

    public Optional<IlimapSymbol> resolveOutput(String id) {
        return topLevel.resolveLocal(id).filter(s -> s.kind() == IlimapSymbolKind.OUTPUT);
    }

    public Optional<IlimapSymbol> resolveEnumMap(String id) {
        return topLevel.resolveLocal(id).filter(s -> s.kind() == IlimapSymbolKind.ENUM_MAP);
    }
}
