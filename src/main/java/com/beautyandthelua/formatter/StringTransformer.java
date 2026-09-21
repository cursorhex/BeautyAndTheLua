package com.beautyandthelua.formatter;

import com.beautyandthelua.lexer.Token;
import com.beautyandthelua.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;

public class StringTransformer {

    public static List<Token> decode(List<Token> tokens) {
        return apply(tokens, false);
    }

    public static List<Token> encode(List<Token> tokens) {
        return apply(tokens, true);
    }

    private static List<Token> apply(List<Token> tokens, boolean encode) {
        List<Token> out = new ArrayList<>(tokens.size());
        for (Token t : tokens) {
            if (t.type == TokenType.STRING && isShortString(t.raw)) {
                out.add(rewrite(t, encode));
            } else {
                out.add(t);
            }
        }
        return out;
    }

    private static boolean isShortString(String raw) {
        return raw.length() >= 2
            && (raw.charAt(0) == '"' || raw.charAt(0) == '\'')
            && raw.charAt(raw.length() - 1) == raw.charAt(0);
    }

    private static Token rewrite(Token t, boolean encode) {
        char quote = t.raw.charAt(0);
        String body = t.raw.substring(1, t.raw.length() - 1);
        int[] bytes = decodeBody(body);
        if (bytes == null) {
            return t;
        }
        String newBody = encode ? renderEncoded(bytes) : renderReadable(bytes, quote);
        String newRaw = quote + newBody + quote;
        StringBuilder value = new StringBuilder();
        for (int b : bytes) value.append((char) b);
        return new Token(TokenType.STRING, value.toString(), newRaw, t.line, t.col);
    }

    private static int[] decodeBody(String body) {
        List<Integer> chars = new ArrayList<>();
        int i = 0;
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c != '\\') {
                chars.add((int) c);
                i++;
                continue;
            }
            if (i + 1 >= body.length()) return null;
            char e = body.charAt(i + 1);
            switch (e) {
                case 'a': chars.add(7); i += 2; break;
                case 'b': chars.add(8); i += 2; break;
                case 'f': chars.add(12); i += 2; break;
                case 'n': chars.add(10); i += 2; break;
                case 'r': chars.add(13); i += 2; break;
                case 't': chars.add(9); i += 2; break;
                case 'v': chars.add(11); i += 2; break;
                case '\\': chars.add((int) '\\'); i += 2; break;
                case '"': chars.add((int) '"'); i += 2; break;
                case '\'': chars.add((int) '\''); i += 2; break;
                case '\n': chars.add(10); i += 2; break;
                case 'x': {
                    int h = 0, digits = 0;
                    int j = i + 2;
                    while (j < body.length() && digits < 2 && isHex(body.charAt(j))) {
                        h = h * 16 + hexVal(body.charAt(j));
                        j++; digits++;
                    }
                    if (digits == 0) return null;
                    chars.add(h);
                    i = j;
                    break;
                }
                default:
                    if (e >= '0' && e <= '9') {
                        int n = 0, digits = 0;
                        int j = i + 1;
                        while (j < body.length() && digits < 3 && body.charAt(j) >= '0' && body.charAt(j) <= '9') {
                            n = n * 10 + (body.charAt(j) - '0');
                            j++; digits++;
                        }
                        if (n > 255) return null;
                        chars.add(n);
                        i = j;
                    } else {
                        return null;
                    }
            }
        }
        int[] result = new int[chars.size()];
        for (int k = 0; k < chars.size(); k++) result[k] = chars.get(k);
        return result;
    }

    private static String renderReadable(int[] bytes, char quote) {
        StringBuilder sb = new StringBuilder();
        for (int b : bytes) {
            switch (b) {
                case '\\': sb.append("\\\\"); break;
                case 7: sb.append("\\a"); break;
                case 8: sb.append("\\b"); break;
                case 12: sb.append("\\f"); break;
                case 10: sb.append("\\n"); break;
                case 13: sb.append("\\r"); break;
                case 9: sb.append("\\t"); break;
                case 11: sb.append("\\v"); break;
                default:
                    if (b == quote) {
                        sb.append('\\').append((char) b);
                    } else if (b >= 0x20 && b <= 0x7E) {
                        sb.append((char) b);
                    } else {
                        sb.append(String.format("\\%03d", b));
                    }
            }
        }
        return sb.toString();
    }

    private static String renderEncoded(int[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int b : bytes) {
            sb.append(String.format("\\%03d", b));
        }
        return sb.toString();
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static int hexVal(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        return c - 'A' + 10;
    }
}
