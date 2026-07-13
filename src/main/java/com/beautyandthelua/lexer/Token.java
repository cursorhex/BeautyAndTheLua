package com.beautyandthelua.lexer;

public class Token {
    public final TokenType type;
    public final String value;
    public final String raw;
    public final int line;
    public final int col;

    public Token(TokenType type, String value, String raw, int line, int col) {
        this.type = type;
        this.value = value;
        this.raw = raw;
        this.line = line;
        this.col = col;
    }

    public Token(TokenType type, String value, int line, int col) {
        this(type, value, value, line, col);
    }

    @Override
    public String toString() {
        return String.format("Token{%s '%s' %d:%d}", type, raw, line, col);
    }
}
