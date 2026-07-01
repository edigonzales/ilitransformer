package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleBlock;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapIdentifierRules;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapScope;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSymbol;
import guru.interlis.transformer.mapping.ilimap.semantic.IlimapSymbolKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class IlimapRenameService {

    private final IlimapSymbolReferenceResolver symbolResolver;
    private final IlimapReferenceSearchService referenceSearch;

    public IlimapRenameService() {
        this(new IlimapSymbolReferenceResolver(), new IlimapReferenceSearchService());
    }

    IlimapRenameService(IlimapSymbolReferenceResolver symbolResolver, IlimapReferenceSearchService referenceSearch) {
        this.symbolResolver = Objects.requireNonNull(symbolResolver, "symbolResolver");
        this.referenceSearch = Objects.requireNonNull(referenceSearch, "referenceSearch");
    }

    public Optional<IlimapRenamePrepareResult> prepareRename(IlimapAnalysis analysis, IlimapIdePosition position) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(position, "position");

        Optional<IlimapResolvedSymbol> resolved = symbolResolver.resolve(analysis, position);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        IlimapSymbol symbol = resolved.get().symbol();
        if (!isRenamable(symbol)) {
            return Optional.empty();
        }

        IlimapIdeRange range = symbolResolver.definitionRange(analysis, symbol);
        String placeholder = placeholder(symbol.kind());
        return Optional.of(new IlimapRenamePrepareResult(range, placeholder));
    }

    public IlimapRenameResult rename(IlimapAnalysis analysis, IlimapIdePosition position, String newName) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(newName, "newName");

        Optional<IlimapResolvedSymbol> resolved = symbolResolver.resolve(analysis, position);
        if (resolved.isEmpty()) {
            return IlimapRenameResult.unavailable("No renamable symbol at the cursor position.");
        }
        IlimapSymbol symbol = resolved.get().symbol();
        if (!isRenamable(symbol)) {
            return IlimapRenameResult.unavailable(
                    "Symbols of kind '" + symbol.kind() + "' cannot be renamed from the ILIMAP document.");
        }

        String oldName = symbol.name();
        if (oldName.equals(newName)) {
            return IlimapRenameResult.unavailable("New name is the same as the current name.");
        }

        String validationMessage = validateNewName(symbol, newName);
        if (validationMessage != null) {
            return IlimapRenameResult.unavailable(validationMessage);
        }

        String collisionMessage = checkCollision(analysis, symbol, newName);
        if (collisionMessage != null) {
            return IlimapRenameResult.unavailable(collisionMessage);
        }

        List<IlimapIdeRange> refRanges = referenceSearch.references(analysis, resolved.get());
        List<IlimapTextEdit> edits = new ArrayList<>();
        for (IlimapIdeRange refRange : refRanges) {
            edits.add(new IlimapTextEdit(refRange, newName));
        }

        return new IlimapRenameResult(true, null, edits);
    }

    private boolean isRenamable(IlimapSymbol symbol) {
        return switch (symbol.kind()) {
            case INPUT, OUTPUT, RULE, ENUM_MAP, SOURCE_ALIAS, JOIN_ALIAS, BAG, REF -> true;
        };
    }

    private String validateNewName(IlimapSymbol symbol, String newName) {
        if (symbol.kind() == IlimapSymbolKind.SOURCE_ALIAS || symbol.kind() == IlimapSymbolKind.JOIN_ALIAS) {
            if (!IlimapIdentifierRules.isValidAliasId(newName)) {
                return "'" + newName + "' is not a valid alias ID (must start with a letter, "
                        + "contain only letters, digits, underscores; no hyphens).";
            }
        } else {
            if (!IlimapIdentifierRules.isValidSymbolId(newName)) {
                return "'" + newName + "' is not a valid symbol ID (must start with a letter, "
                        + "contain only letters, digits, underscores or hyphens).";
            }
        }
        return null;
    }

    private String checkCollision(IlimapAnalysis analysis, IlimapSymbol symbol, String newName) {
        if (symbol.kind() == IlimapSymbolKind.SOURCE_ALIAS || symbol.kind() == IlimapSymbolKind.JOIN_ALIAS) {
            return checkAliasScopeCollision(analysis, symbol, newName);
        }
        return checkTopLevelCollision(analysis, symbol, newName);
    }

    private String checkAliasScopeCollision(IlimapAnalysis analysis, IlimapSymbol symbol, String newName) {
        if (!analysis.hasDocument()) {
            return null;
        }
        IlimapScope scope = findAliasScope(analysis, symbol).orElse(null);
        if (scope == null) {
            return null;
        }
        Optional<IlimapSymbol> existing = scope.resolveLocal(newName);
        if (existing.isPresent()) {
            return "'" + newName + "' already exists in this scope as '"
                    + existing.get().kind().name().toLowerCase().replace('_', ' ') + "'.";
        }
        return null;
    }

    private Optional<IlimapScope> findAliasScope(IlimapAnalysis analysis, IlimapSymbol symbol) {
        if (!analysis.hasDocument() || symbol.node() == null) {
            return Optional.empty();
        }
        int symbolOffset = symbol.node().range().start().offset();
        for (IlimapRuleBlock rule : analysis.document().rules()) {
            if (rule.range().start().offset() <= symbolOffset
                    && symbolOffset <= rule.range().end().offset()) {
                return Optional.of(analysis.symbols().scopeFor(rule));
            }
        }
        return Optional.empty();
    }

    private String checkTopLevelCollision(IlimapAnalysis analysis, IlimapSymbol symbol, String newName) {
        Optional<IlimapSymbol> existing = analysis.symbols().topLevelScope().resolve(newName);
        if (existing.isPresent() && !existing.get().equals(symbol)) {
            return "'" + newName + "' is already used by another "
                    + existing.get().kind().name().toLowerCase().replace('_', ' ') + ".";
        }
        return null;
    }

    private static String placeholder(IlimapSymbolKind kind) {
        return switch (kind) {
            case INPUT -> "input id";
            case OUTPUT -> "output id";
            case RULE -> "rule id";
            case ENUM_MAP -> "enum id";
            case SOURCE_ALIAS -> "alias";
            case JOIN_ALIAS -> "join alias";
            case BAG -> "bag id";
            case REF -> "ref id";
        };
    }
}
