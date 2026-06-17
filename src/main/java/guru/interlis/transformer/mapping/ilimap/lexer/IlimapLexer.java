package guru.interlis.transformer.mapping.ilimap.lexer;

import java.util.ArrayList;
import java.util.List;

import guru.interlis.transformer.mapping.ilimap.semantic.IlimapReservedWords;

public final class IlimapLexer {

    private final String source;
    private final int length;
    private int offset;
    private int line;
    private int column;
    private IlimapToken pending;

    public IlimapLexer(String source) {
        this.source = source;
        this.length = source.length();
        this.offset = 0;
        this.line = 1;
        this.column = 1;
    }

    public IlimapToken peek() {
        if (pending == null) {
            pending = readNext();
        }
        return pending;
    }

    public IlimapToken next() {
        if (pending != null) {
            var token = pending;
            pending = null;
            return token;
        }
        return readNext();
    }

    public boolean hasNext() {
        if (pending != null) {
            return pending.type() != IlimapTokenType.EOF;
        }
        pending = readNext();
        return pending.type() != IlimapTokenType.EOF;
    }

    public List<IlimapToken> tokenize() {
        List<IlimapToken> tokens = new ArrayList<>();
        while (hasNext()) {
            tokens.add(next());
        }
        return tokens;
    }

    private IlimapToken readNext() {
        skipWhitespaceAndComments();

        if (offset >= length) {
            return eofToken();
        }

        IlimapSourcePosition start = currentPosition();
        char c = source.charAt(offset);

        if (c == '"') {
            return readString();
        }
        if (c == '#') {
            return readHashLiteral();
        }
        if (isDigit(c)) {
            return readNumber();
        }
        if (isIdentifierStart(c)) {
            return readIdentifierOrKeyword();
        }
        if (c == '=' && peekChar(1) == '>') {
            return twoCharToken(IlimapTokenType.ARROW, start);
        }
        if (c == '-' && peekChar(1) == '>') {
            return twoCharToken(IlimapTokenType.ARROW, start);
        }
        return singleCharToken(switch (c) {
            case '{' -> IlimapTokenType.LBRACE;
            case '}' -> IlimapTokenType.RBRACE;
            case '(' -> IlimapTokenType.LPAREN;
            case ')' -> IlimapTokenType.RPAREN;
            case ',' -> IlimapTokenType.COMMA;
            case ';' -> IlimapTokenType.SEMICOLON;
            case '=' -> IlimapTokenType.EQUALS;
            default -> throw lexerError("unexpected character '" + c + "'");
        }, start);
    }

    private void skipWhitespaceAndComments() {
        while (offset < length) {
            char c = source.charAt(offset);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                advance();
            } else if (c == '/' && peekChar(1) == '/') {
                skipLineComment();
            } else if (c == '/' && peekChar(1) == '*') {
                skipBlockComment();
            } else {
                break;
            }
        }
    }

    private void skipLineComment() {
        advance(); // /
        advance(); // /
        while (offset < length && source.charAt(offset) != '\n') {
            advance();
        }
    }

    private void skipBlockComment() {
        IlimapSourcePosition start = currentPosition();
        advance(); // /
        advance(); // *
        while (offset < length) {
            if (source.charAt(offset) == '*' && peekChar(1) == '/') {
                advance(); // *
                advance(); // /
                return;
            }
            advance();
        }
        throw lexerError("unterminated block comment", start);
    }

    private IlimapToken readString() {
        IlimapSourcePosition start = currentPosition();
        StringBuilder sb = new StringBuilder();
        advance(); // opening "
        while (offset < length) {
            char c = source.charAt(offset);
            if (c == '"') {
                advance(); // closing "
                var end = currentPosition();
                return new IlimapToken(IlimapTokenType.STRING, sb.toString(), new IlimapSourceRange(start, end));
            }
            if (c == '\\') {
                advance();
                if (offset >= length) {
                    throw lexerError("unterminated string literal", start);
                }
                char escaped = source.charAt(offset);
                switch (escaped) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    default -> {
                        sb.append('\\');
                        sb.append(escaped);
                    }
                }
                advance();
            } else if (c == '\n' || c == '\r') {
                throw lexerError("unterminated string literal", start);
            } else {
                advance();
                sb.append(c);
            }
        }
        throw lexerError("unterminated string literal", start);
    }

    private IlimapToken readHashLiteral() {
        IlimapSourcePosition start = currentPosition();
        StringBuilder sb = new StringBuilder();
        advance(); // #
        while (offset < length && isIdentifierPart(source.charAt(offset))) {
            advance();
            sb.append(source.charAt(offset - 1));
        }
        var end = currentPosition();
        return new IlimapToken(IlimapTokenType.HASH_LITERAL, "#" + sb, new IlimapSourceRange(start, end));
    }

    private IlimapToken readNumber() {
        IlimapSourcePosition start = currentPosition();
        StringBuilder sb = new StringBuilder();
        boolean hasDecimal = false;
        while (offset < length) {
            char c = source.charAt(offset);
            if (isDigit(c)) {
                advance();
                sb.append(c);
            } else if (c == '.' && !hasDecimal && peekChar(1) != '.') {
                hasDecimal = true;
                advance();
                sb.append(c);
            } else {
                break;
            }
        }
        var end = currentPosition();
        return new IlimapToken(IlimapTokenType.NUMBER, sb.toString(), new IlimapSourceRange(start, end));
    }

    private IlimapToken readIdentifierOrKeyword() {
        IlimapSourcePosition start = currentPosition();
        StringBuilder sb = new StringBuilder();
        while (offset < length && isIdentifierPart(source.charAt(offset))) {
            advance();
            sb.append(source.charAt(offset - 1));
        }
        String text = sb.toString();
        var end = currentPosition();

        // Boolean and null literals
        if (text.equals("true") || text.equals("false")) {
            return new IlimapToken(IlimapTokenType.BOOLEAN, text, new IlimapSourceRange(start, end));
        }
        if (text.equals("null")) {
            return new IlimapToken(IlimapTokenType.NULL, text, new IlimapSourceRange(start, end));
        }
        if (IlimapReservedWords.isReserved(text)) {
            return new IlimapToken(IlimapTokenType.KEYWORD, text, new IlimapSourceRange(start, end));
        }
        return new IlimapToken(IlimapTokenType.IDENTIFIER, text, new IlimapSourceRange(start, end));
    }

    private IlimapToken singleCharToken(IlimapTokenType type, IlimapSourcePosition start) {
        advance();
        var end = currentPosition();
        return new IlimapToken(type, Character.toString(source.charAt(offset - 1)), new IlimapSourceRange(start, end));
    }

    private IlimapToken twoCharToken(IlimapTokenType type, IlimapSourcePosition start) {
        char c1 = source.charAt(offset);
        char c2 = source.charAt(offset + 1);
        advance(); advance();
        var end = currentPosition();
        return new IlimapToken(type, "" + c1 + c2, new IlimapSourceRange(start, end));
    }

    private IlimapToken eofToken() {
        var pos = currentPosition();
        return new IlimapToken(IlimapTokenType.EOF, "", new IlimapSourceRange(pos, pos));
    }

    private char peekChar(int lookahead) {
        int idx = offset + lookahead;
        return idx < length ? source.charAt(idx) : '\0';
    }

    private void advance() {
        if (offset < length) {
            char c = source.charAt(offset);
            offset++;
            if (c == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isIdentifierStart(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_';
    }

    private boolean isIdentifierPart(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }

    private IlimapSourcePosition currentPosition() {
        return new IlimapSourcePosition(offset, line, column);
    }

    private LexerException lexerError(String message) {
        return new LexerException(message, new IlimapSourcePosition(offset, line, column));
    }

    private LexerException lexerError(String message, IlimapSourcePosition start) {
        return new LexerException("at line " + start.line() + ", column " + start.column() + ": " + message, start);
    }

    public static final class LexerException extends RuntimeException {
        public final IlimapSourcePosition position;

        LexerException(String message, IlimapSourcePosition position) {
            super(message);
            this.position = position;
        }
    }
}
