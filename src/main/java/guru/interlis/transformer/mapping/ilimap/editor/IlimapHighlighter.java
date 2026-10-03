package guru.interlis.transformer.mapping.ilimap.editor;

import guru.interlis.transformer.mapping.ilimap.semantic.IlimapReservedWords;

import java.util.ArrayList;
import java.util.List;

/** Tolerant lexical highlighting. UTF-16 offsets match SWT and the document source ranges. */
public final class IlimapHighlighter {
    public enum Kind {
        KEYWORD,
        FUNCTION,
        STRING,
        NUMBER,
        ENUM,
        COMMENT,
        IDENTIFIER,
        OPERATOR
    }

    public record Span(int start, int end, Kind kind) {}

    public List<Span> highlight(String text) {
        List<Span> spans = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            int start = i++;
            Kind kind;
            if (c == '/' && i < text.length() && text.charAt(i) == '/') {
                while (i < text.length() && text.charAt(i) != '\n') i++;
                kind = Kind.COMMENT;
            } else if (c == '/' && i < text.length() && text.charAt(i) == '*') {
                int end = text.indexOf("*/", i + 1);
                i = end < 0 ? text.length() : end + 2;
                kind = Kind.COMMENT;
            } else if (c == '"' || c == '\'') {
                while (i < text.length()) {
                    char next = text.charAt(i++);
                    if (next == '\\' && i < text.length()) i++;
                    else if (next == c) break;
                }
                kind = Kind.STRING;
            } else if (c == '#') {
                while (i < text.length() && (Character.isJavaIdentifierPart(text.charAt(i)) || text.charAt(i) == '.'))
                    i++;
                kind = Kind.ENUM;
            } else if (Character.isDigit(c)) {
                while (i < text.length() && (Character.isDigit(text.charAt(i)) || ".eE".indexOf(text.charAt(i)) >= 0))
                    i++;
                kind = Kind.NUMBER;
            } else if (Character.isJavaIdentifierStart(c)) {
                while (i < text.length() && (Character.isJavaIdentifierPart(text.charAt(i)) || text.charAt(i) == '-'))
                    i++;
                String word = text.substring(start, i);
                int next = i;
                while (next < text.length() && Character.isWhitespace(text.charAt(next))) next++;
                kind = IlimapReservedWords.isReserved(word)
                                || List.of("and", "or", "not").contains(word)
                        ? Kind.KEYWORD
                        : next < text.length() && text.charAt(next) == '(' ? Kind.FUNCTION : Kind.IDENTIFIER;
            } else {
                kind = Kind.OPERATOR;
            }
            spans.add(new Span(start, i, kind));
        }
        return List.copyOf(spans);
    }
}
