package guru.interlis.transformer.mapping.ilimap.lsp;

import guru.interlis.transformer.mapping.ilimap.ide.IlimapCompletionItem;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapCompletionKind;

import java.util.Objects;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

public final class IlimapLspCompletionMapper {

    public CompletionItem map(IlimapCompletionItem item) {
        Objects.requireNonNull(item, "item");

        CompletionItem completionItem = new CompletionItem(item.label());
        completionItem.setKind(toLspKind(item.kind()));
        completionItem.setDetail(item.detail());
        completionItem.setDocumentation(item.documentation());
        completionItem.setInsertText(item.insertText());
        return completionItem;
    }

    private static CompletionItemKind toLspKind(IlimapCompletionKind kind) {
        return switch (kind) {
            case KEYWORD -> CompletionItemKind.Keyword;
            case INPUT, OUTPUT -> CompletionItemKind.File;
            case RULE -> CompletionItemKind.Method;
            case ENUM_MAP -> CompletionItemKind.Enum;
            case SOURCE_ALIAS -> CompletionItemKind.Variable;
            case FUNCTION -> CompletionItemKind.Function;
            case ATTRIBUTE -> CompletionItemKind.Field;
            case VALUE -> CompletionItemKind.Value;
        };
    }
}
