package com.beautyandthelua.lexer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Lexer {
    private static final Set<String> KEYWORDS = new HashSet<>();
    static { // ik this sucks but im lazy
        String[] kws = {"and", "break", "continue", "do", "else", "elseif", "end",
            "false", "for", "function", "goto", "if", "in", "local", "nil",
            "not", "or", "repeat", "return", "then", "true", "until", "while",
            "type", "export", "import", "typeof"};
        for (String kw : kws) KEYWORDS.add(kw);
    }

    private final String source;
    private final String filename;
    private int pos;
    private int line;
    private int col;
    private final List<Token> tokens;

    public Lexer(String source) {
        this(source, "<unknown>");
    }

    public Lexer(String source, String filename) {
        this.source = source;
        this.filename = filename;
        this.pos = 0;
        this.line = 1;
        this.col = 1;
        this.tokens = new ArrayList<>();
    }

    public List<Token> tokenize() {
        if (source.isEmpty()) {
            emit(TokenType.EOF, "");
            return tokens;
        }
        while (pos < source.length()) {
            skipWhitespace();
            if (pos >= source.length()) break;

            char c = peek();

            if (line == 1 && col == 1 && c == '#') {
                readShebang();
                continue;
            }

            if (c == '-' && peek(1) == '-') {
                readComment();
                continue;
            }

            if (c == '[' && (peek(1) == '[' || peek(1) == '=')) {
                readLongString();
                continue;
            }

            switch (c) {
                case '(': advance(); emit(TokenType.LPAREN, "("); break;
                case ')': advance(); emit(TokenType.RPAREN, ")"); break;
                case '{': advance(); emit(TokenType.LCURLY, "{"); break;
                case '}': advance(); emit(TokenType.RCURLY, "}"); break;
                case '[': advance(); emit(TokenType.LBRACK, "["); break;
                case ']': advance(); emit(TokenType.RBRACK, "]"); break;
                case ',': advance(); emit(TokenType.COMMA, ","); break;
                case ';': advance(); emit(TokenType.SEMI, ";"); break;
                case '+':
                    if (peek(1) == '=') { advance(); advance(); emit(TokenType.PLUS_EQ, "+="); }
                    else { advance(); emit(TokenType.PLUS, "+"); }
                    break;
                case '-':
                    if (peek(1) == '=') { advance(); advance(); emit(TokenType.MINUS_EQ, "-="); }
                    else if (peek(1) == '>') { advance(); advance(); emit(TokenType.ARROW, "->"); }
                    else { advance(); emit(TokenType.MINUS, "-"); }
                    break;
                case '*':
                    if (peek(1) == '=') { advance(); advance(); emit(TokenType.STAR_EQ, "*="); }
                    else { advance(); emit(TokenType.STAR, "*"); }
                    break;
                case '^':
                    if (peek(1) == '=') { advance(); advance(); emit(TokenType.CARET_EQ, "^="); }
                    else { advance(); emit(TokenType.CARET, "^"); }
                    break;
                case '%':
                    if (peek(1) == '=') { advance(); advance(); emit(TokenType.PERCENT_EQ, "%="); }
                    else { advance(); emit(TokenType.PERCENT, "%"); }
                    break;
                case '#': advance(); emit(TokenType.HASH, "#"); break;
                case '&': advance(); emit(TokenType.AMPERSAND, "&"); break;
                case '|': advance(); emit(TokenType.PIPE, "|"); break;
                case '?': advance(); emit(TokenType.QMARK, "?"); break;
                case '@': advance(); emit(TokenType.ATRATE, "@"); break;
                case '`': readInterpolatedString(); break;

                case '.':
                    if (peek(1) == '.') {
                        if (peek(2) == '.') {
                            advance(); advance(); advance();
                            emit(TokenType.VARARG, "...");
                        } else if (peek(2) == '=') {
                            advance(); advance(); advance();
                            emit(TokenType.CONCAT_EQ, "..=");
                        } else {
                            advance(); advance();
                            emit(TokenType.CONCAT, "..");
                        }
                    } else if (peek(1) != '\0' && Character.isDigit(peek(1))) {
                        readNumber();
                    } else {
                        advance();
                        emit(TokenType.DOT, ".");
                    }
                    break;

                case ':':
                    if (peek(1) == ':') {
                        advance(); advance();
                        emit(TokenType.LABEL, "::");
                    } else {
                        advance();
                        emit(TokenType.COLON, ":");
                    }
                    break;

                case '=':
                    if (peek(1) == '=') {
                        advance(); advance();
                        emit(TokenType.EQ, "==");
                    } else {
                        advance();
                        emit(TokenType.ASSIGN, "=");
                    }
                    break;

                case '<':
                    if (peek(1) == '=') {
                        advance(); advance();
                        emit(TokenType.LE, "<=");
                    } else if (peek(1) == '<') {
                        advance(); advance();
                        emit(TokenType.SHL, "<<");
                    } else {
                        advance();
                        emit(TokenType.LT, "<");
                    }
                    break;

                case '>':
                    if (peek(1) == '=') {
                        advance(); advance();
                        emit(TokenType.GE, ">=");
                    } else if (peek(1) == '>') {
                        advance(); advance();
                        emit(TokenType.SHR, ">>");
                    } else {
                        advance();
                        emit(TokenType.GT, ">");
                    }
                    break;

                case '~':
                    if (peek(1) == '=') {
                        advance(); advance();
                        emit(TokenType.NE, "~=");
                    } else {
                        advance();
                        emit(TokenType.TILDE, "~");
                    }
                    break;

                case '/':
                    if (peek(1) == '/') {
                        if (peek(2) == '=') {
                            advance(); advance(); advance();
                            emit(TokenType.IDIV_EQ, "//=");
                        } else {
                            advance(); advance();
                            emit(TokenType.IDIV, "//");
                        }
                    } else if (peek(1) == '=') {
                        advance(); advance();
                        emit(TokenType.SLASH_EQ, "/=");
                    } else {
                        advance();
                        emit(TokenType.SLASH, "/");
                    }
                    break;

                case '\'':
                case '"':
                    readShortString(c);
                    break;

                default:
                    if (Character.isDigit(c)) {
                        readNumber();
                    } else if (isIdentStart(c)) {
                        readIdentifier();
                    } else {
                        error("unexpected symbol '" + c + "'");
                    }
                    break;
            }
        }
        emit(TokenType.EOF, "");
        return tokens;
    }

    private char peek() {
        return pos < source.length() ? source.charAt(pos) : '\0';
    }

    private char peek(int offset) {
        int idx = pos + offset;
        return idx < source.length() ? source.charAt(idx) : '\0';
    }

    private void advance() {
        char c = source.charAt(pos);
        if (c == '\n') {
            line++;
            col = 1;
        } else if (c == '\r') {
            line++;
            col = 1;
            if (pos + 1 < source.length() && source.charAt(pos + 1) == '\n') {
                pos++;
            }
        } else {
            col++;
        }
        pos++;
    }

    private void emit(TokenType type, String value) {
        emit(type, value, value);
    }

    private void emit(TokenType type, String value, String raw) {
        tokens.add(new Token(type, value, raw, line, col));
    }

    private void error(String msg) {
        throw new RuntimeException(filename + ":" + line + ":" + col + ": " + msg);
    }

    private void skipWhitespace() {
        while (pos < source.length()) {
            char c = peek();
            if (c == ' ' || c == '\t' || c == '\f') {
                advance();
            } else if (c == '\n' || c == '\r') {
                advance();
                emit(TokenType.NEWLINE, "\n");
            } else if (c == '-' && peek(1) == '-') {
                break;
            } else if (c == '#' && line == 1 && col == 1) {
                break;
            } else {
                break;
            }
        }
    }

    private void readShebang() {
        int startLine = line;
        int startCol = col;
        StringBuilder raw = new StringBuilder("#");
        advance();
        while (pos < source.length()) {
            char c = peek();
            if (c == '\n' || c == '\r') break;
            raw.append(advanceChar());
        }
        emit(TokenType.SHEBANG, raw.substring(1), raw.toString());
    }

    private char advanceChar() {
        char c = source.charAt(pos);
        advance();
        return c;
    }

    private void readComment() {
        advance();
        advance();
        if (peek() == '[') {
            int savedPos = pos;
            int eq = 0;
            while (peek(1 + eq) == '=') eq++;
            if (peek(1 + eq) == '[') {
                advance();
                for (int i = 0; i < eq; i++) advance();
                advance();
                StringBuilder value = new StringBuilder();
                String closing = eq > 0 ? "]" + "=".repeat(eq) + "]" : "]]";
                String rawStart = "--[" + "=".repeat(eq) + "[";
                while (true) {
                    if (pos >= source.length()) {
                        error("unfinished long comment");
                    }
                    if (peek() == ']') {
                        if (source.substring(pos).startsWith(closing)) {
                            for (int i = 0; i < closing.length(); i++) advance();
                            emit(TokenType.COMMENT, value.toString(), rawStart + value + closing);
                            return;
                        }
                    }
                    value.append(advanceChar());
                }
            }
            pos = savedPos;
        }
        StringBuilder value = new StringBuilder();
        StringBuilder raw = new StringBuilder("--");
        while (pos < source.length()) {
            char c = peek();
            if (c == '\n' || c == '\r') break;
            raw.append(c);
            value.append(c);
            advance();
        }
        emit(TokenType.COMMENT, value.toString(), raw.toString());
    }

    private void readLongString() {
        advance();
        int eq = 0;
        while (peek() == '=') {
            eq++;
            advance();
        }
        if (peek() == '[') {
            advance();
            StringBuilder value = new StringBuilder();
            String closing = eq > 0 ? "]" + "=".repeat(eq) + "]" : "]]";
            String rawStart = "[" + "=".repeat(eq) + "[";
            while (true) {
                if (pos >= source.length()) {
                    error("unfinished long string");
                }
                if (peek() == ']') {
                    if (source.substring(pos).startsWith(closing)) {
                        for (int i = 0; i < closing.length(); i++) advance();
                        emit(TokenType.STRING, value.toString(), rawStart + value + closing);
                        return;
                    }
                }
                value.append(advanceChar());
            }
        }
        error("invalid long bracket");
    }

    private void readShortString(char quote) {
        StringBuilder raw = new StringBuilder();
        raw.append(advanceChar());
        StringBuilder value = new StringBuilder();
        while (true) {
            if (pos >= source.length()) {
                error("unfinished string");
            }
            char c = peek();
            if (c == '\\') {
                raw.append(advanceChar());
                if (pos >= source.length()) error("unfinished string");
                char esc = advanceChar();
                raw.append(esc);
                if (esc == '\n') {
                } else if (esc == '\r') {
                    if (peek() == '\n') {
                        raw.append(advanceChar());
                    }
                } else {
                    value.append('\\').append(esc);
                }
            } else if (c == quote) {
                raw.append(advanceChar());
                emit(TokenType.STRING, value.toString(), raw.toString());
                return;
            } else if (c == '\n' || c == '\r') {
                error("unfinished string");
            } else {
                char ch = advanceChar();
                raw.append(ch);
                value.append(ch);
            }
        }
    }

    private void readInterpolatedString() {
        StringBuilder raw = new StringBuilder();
        raw.append(advanceChar());
        StringBuilder value = new StringBuilder();
        while (true) {
            if (pos >= source.length()) {
                error("unfinished string");
            }
            char c = peek();
            if (c == '\\') {
                raw.append(advanceChar());
                if (pos >= source.length()) error("unfinished string");
                char esc = advanceChar();
                raw.append(esc);
                value.append('\\').append(esc);
            } else if (c == '`') {
                raw.append(advanceChar());
                emit(TokenType.STRING, value.toString(), raw.toString());
                return;
            } else if (c == '\n' || c == '\r') {
                error("unfinished string");
            } else {
                char ch = advanceChar();
                raw.append(ch);
                value.append(ch);
            }
        }
    }

    private void readNumber() {
        StringBuilder raw = new StringBuilder();
        char c = peek();
        if (c == '0') {
            raw.append(advanceChar());
            c = peek();
            if (c == 'x' || c == 'X') {
                raw.append(advanceChar());
                while (isXDigit(peek())) raw.append(advanceChar());
                if (peek() == '.' && isXDigit(peek(1))) {
                    raw.append(advanceChar());
                    while (isXDigit(peek())) raw.append(advanceChar());
                }
                if (peek() == 'p' || peek() == 'P') {
                    raw.append(advanceChar());
                    if (peek() == '+' || peek() == '-') raw.append(advanceChar());
                    while (Character.isDigit(peek())) raw.append(advanceChar());
                }
                emit(TokenType.NUMBER, raw.toString());
                return;
            }
        }
        while (Character.isDigit(peek())) raw.append(advanceChar());
        c = peek();
        if (c == '.') {
            if (Character.isDigit(peek(1))) {
                raw.append(advanceChar());
                while (Character.isDigit(peek())) raw.append(advanceChar());
            }
        }
        c = peek();
        if (c == 'e' || c == 'E') {
            raw.append(advanceChar());
            c = peek();
            if (c == '+' || c == '-') raw.append(advanceChar());
            while (Character.isDigit(peek())) raw.append(advanceChar());
        }
        emit(TokenType.NUMBER, raw.toString());
    }

    private boolean isXDigit(char c) {
        return Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private boolean isIdentPart(char c) {
        return isIdentStart(c) || Character.isDigit(c);
    }

    private void readIdentifier() {
        StringBuilder raw = new StringBuilder();
        while (isIdentPart(peek())) raw.append(advanceChar());
        String word = raw.toString();
        if (KEYWORDS.contains(word)) {
            TokenType tt;
            switch (word) { // ._.
                case "and": tt = TokenType.AND; break;
                case "break": tt = TokenType.BREAK; break;
                case "continue": tt = TokenType.CONTINUE; break;
                case "do": tt = TokenType.DO; break;
                case "else": tt = TokenType.ELSE; break;
                case "elseif": tt = TokenType.ELSEIF; break;
                case "end": tt = TokenType.END; break;
                case "false": tt = TokenType.FALSE; break;
                case "for": tt = TokenType.FOR; break;
                case "function": tt = TokenType.FUNCTION; break;
                case "goto": tt = TokenType.GOTO; break;
                case "if": tt = TokenType.IF; break;
                case "in": tt = TokenType.IN; break;
                case "local": tt = TokenType.LOCAL; break;
                case "nil": tt = TokenType.NIL; break;
                case "not": tt = TokenType.NOT; break;
                case "or": tt = TokenType.OR; break;
                case "repeat": tt = TokenType.REPEAT; break;
                case "return": tt = TokenType.RETURN; break;
                case "then": tt = TokenType.THEN; break;
                case "true": tt = TokenType.TRUE; break;
                case "until": tt = TokenType.UNTIL; break;
                case "while": tt = TokenType.WHILE; break;
                case "type": tt = TokenType.TYPE; break;
                case "export": tt = TokenType.EXPORT; break;
                case "import": tt = TokenType.IMPORT; break;
                case "typeof": tt = TokenType.TYPEOF; break;
                default: tt = TokenType.IDENTIFIER; break;
            }
            emit(tt, word);
        } else {
            emit(TokenType.IDENTIFIER, word);
        }
    }
}
