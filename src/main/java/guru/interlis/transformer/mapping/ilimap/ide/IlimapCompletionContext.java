package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapAstNode;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleBlock;

import java.util.Objects;

public record IlimapCompletionContext(
        IlimapCompletionContextKind kind,
        String prefix,
        IlimapRuleBlock currentRule,
        IlimapAstNode currentNode,
        String qualifier,
        IlimapIdeRange replacementRange) {

    public IlimapCompletionContext(
            IlimapCompletionContextKind kind, String prefix, IlimapRuleBlock currentRule, IlimapAstNode currentNode) {
        this(kind, prefix, currentRule, currentNode, null, null);
    }

    public IlimapCompletionContext {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(prefix, "prefix");
    }
}
