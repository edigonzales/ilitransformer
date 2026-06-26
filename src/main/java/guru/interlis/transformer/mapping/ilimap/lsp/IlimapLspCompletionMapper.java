package guru.interlis.transformer.mapping.ilimap.lsp;

import guru.interlis.transformer.mapping.ilimap.ide.IlimapCompletionItem;
import guru.interlis.transformer.mapping.ilimap.ide.IlimapCompletionKind;

import java.util.Objects;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public final class IlimapLspCompletionMapper {

    public CompletionItem map(IlimapCompletionItem item) {
        Objects.requireNonNull(item, "item");

        CompletionItem completionItem = new CompletionItem(item.label());
        completionItem.setKind(toLspKind(item.kind()));
        completionItem.setDetail(item.detail());
        completionItem.setDocumentation(item.documentation());
        completionItem.setInsertText(item.insertText());
        if (item.filterText() != null) {
            completionItem.setFilterText(item.filterText());
        }
        if (item.snippet()) {
            completionItem.setInsertTextFormat(InsertTextFormat.Snippet);
        }
        if (item.replacementRange() != null) {
            completionItem.setTextEdit(Either.forLeft(
                    new TextEdit(new IlimapLspRangeMapper().toLspRange(item.replacementRange()), item.insertText())));
        }
        return completionItem;
    }

    private static CompletionItemKind toLspKind(IlimapCompletionKind kind) {
        return switch (kind) {
            case KEYWORD -> CompletionItemKind.Keyword;
            case SNIPPET -> CompletionItemKind.Snippet;
            case INPUT, OUTPUT -> CompletionItemKind.File;
            case RULE -> CompletionItemKind.Method;
            case ENUM_MAP -> CompletionItemKind.Enum;
            case SOURCE_ALIAS -> CompletionItemKind.Variable;
            case CLASS -> CompletionItemKind.Class;
            case FUNCTION -> CompletionItemKind.Function;
            case ATTRIBUTE -> CompletionItemKind.Field;
            case ROLE -> CompletionItemKind.Reference;
            case VALUE -> CompletionItemKind.Value;
        };
    }
}
