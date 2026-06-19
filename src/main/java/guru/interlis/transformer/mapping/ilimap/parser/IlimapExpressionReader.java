package guru.interlis.transformer.mapping.ilimap.parser;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapExpressionText;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourcePosition;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

public final class IlimapExpressionReader {

    public IlimapExpressionText readUntilStatementSemicolon(String source, int startOffset) {
        int length = source.length();
        if (startOffset >= length) {
            return empty(source, startOffset);
        }

        int pos = startOffset;
        int startLine = 1;
        int startColumn = 1;
        for (int i = 0; i < startOffset; i++) {
            if (source.charAt(i) == '\n') {
                startLine++;
                startColumn = 1;
            } else {
                startColumn++;
            }
        }

        int line = startLine;
        int column = startColumn;

        StringBuilder text = new StringBuilder();
        int parenDepth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;

        while (pos < length) {
            char c = source.charAt(pos);

            // String literal
            if (c == '"') {
                text.append(c);
                pos++;
                column++;
                while (pos < length) {
                    char sc = source.charAt(pos);
                    if (sc == '\\') {
                        text.append(sc);
                        pos++;
                        column++;
                        if (pos >= length) {
                            throw readerError("unterminated string literal", line, column);
                        }
                        text.append(source.charAt(pos));
                        pos++;
                        column++;
                        continue;
                    }
                    if (sc == '"') {
                        text.append(sc);
                        pos++;
                        column++;
                        break;
                    }
                    if (sc == '\n' || sc == '\r') {
                        throw readerError("unterminated string literal", line, column);
                    }
                    text.append(sc);
                    pos++;
                    column++;
                }
                if (pos >= length || source.charAt(pos - 1) != '"') {
                    throw readerError("unterminated string literal", line, column);
                }
                continue;
            }

            // Line comment
            if (c == '/' && pos + 1 < length && source.charAt(pos + 1) == '/') {
                while (pos < length && source.charAt(pos) != '\n') {
                    pos++;
                    column++;
                }
                continue;
            }

            // Block comment
            if (c == '/' && pos + 1 < length && source.charAt(pos + 1) == '*') {
                pos += 2;
                column += 2;
                while (pos < length) {
                    if (source.charAt(pos) == '*' && pos + 1 < length && source.charAt(pos + 1) == '/') {
                        pos += 2;
                        column += 2;
                        break;
                    }
                    if (source.charAt(pos) == '\n') {
                        line++;
                        column = 1;
                    } else {
                        column++;
                    }
                    pos++;
                }
                if (pos >= length || (pos >= 2 && source.charAt(pos - 2) != '*' && source.charAt(pos - 1) != '/')) {
                    throw readerError("unterminated block comment", line, column);
                }
                continue;
            }

            // Track parentheses/brackets/braces for nesting
            if (c == '(') {
                parenDepth++;
            } else if (c == ')') {
                parenDepth--;
            } else if (c == '[') {
                bracketDepth++;
            } else if (c == ']') {
                bracketDepth--;
            } else if (c == '{') {
                braceDepth++;
            } else if (c == '}') {
                braceDepth--;
            }

            // Semicolon at top level ends the statement
            if (c == ';' && parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
                if (parenDepth < 0 || bracketDepth < 0 || braceDepth < 0) {
                    throw readerError("unbalanced parentheses/brackets", line, column);
                }
                var startPos = new IlimapSourcePosition(startOffset, startLine, startColumn);
                var endPos = new IlimapSourcePosition(pos, line, column);
                return new IlimapExpressionText(
                        text.toString().stripTrailing(), new IlimapSourceRange(startPos, endPos));
            }

            text.append(c);
            pos++;
            column++;
            if (c == '\n') {
                line++;
                column = 1;
            }
        }

        // Check for unbalanced nesting at end of input
        if (parenDepth > 0 || bracketDepth > 0 || braceDepth > 0) {
            throw readerError("unbalanced parentheses/brackets/braces at end of input", line, column);
        }

        var startPos = new IlimapSourcePosition(startOffset, startLine, startColumn);
        var endPos = new IlimapSourcePosition(pos, line, column);
        return new IlimapExpressionText(text.toString().stripTrailing(), new IlimapSourceRange(startPos, endPos));
    }

    private IlimapExpressionText empty(String source, int offset) {
        var pos = new IlimapSourcePosition(offset, 1, 1);
        return new IlimapExpressionText("", new IlimapSourceRange(pos, pos));
    }

    private ReaderException readerError(String message, int line, int column) {
        return new ReaderException("at line " + line + ", column " + column + ": " + message);
    }

    public static final class ReaderException extends RuntimeException {
        public ReaderException(String message) {
            super(message);
        }
    }
}
