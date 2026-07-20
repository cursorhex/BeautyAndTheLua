package com.beautyandthelua.formatter;

import com.beautyandthelua.lexer.Token;
import com.beautyandthelua.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;


public class ExpressionSolver {

    private static final int UNARY_PREC = 12;
    // Largest magnitude that is exactly representable as a double integer.
    private static final double MAX_EXACT_INT = 9007199254740992.0; // 2^53

    public static List<Token> solve(List<Token> tokens) {
        return new ExpressionSolver(tokens).foldSpanUntil(null);
    }

    private final List<Token> tokens;
    private int pos;

    private ExpressionSolver(List<Token> tokens) {
        this.tokens = tokens;
        this.pos = 0;
    }

    private static abstract class Node {
        int line;
        int col;
    }

    private static final class NumNode extends Node {
        final double value;
        final Token original;

        NumNode(double value, Token original, int line, int col) {
            this.value = value;
            this.original = original;
            this.line = line;
            this.col = col;
        }
    }

    private static final class BoolNode extends Node {
        final boolean value;

        BoolNode(boolean value, int line, int col) {
            this.value = value;
            this.line = line;
            this.col = col;
        }
    }

    private static final class OpaqueNode extends Node {
        final List<Token> tokens;

        OpaqueNode(List<Token> tokens, int line, int col) {
            this.tokens = tokens;
            this.line = line;
            this.col = col;
        }
    }

    private List<Token> foldSpanUntil(TokenType close) {
        List<Token> out = new ArrayList<>();
        while (pos < tokens.size()) {
            Token t = tokens.get(pos);
            if (close != null && t.type == close) {
                break;
            }
            if (canStartExpr(t)) {
                Node node = parseExpr(0);
                if (node == null) {
                    out.add(tokens.get(pos++));
                } else {
                    out.addAll(emit(node));
                }
            } else {
                out.add(tokens.get(pos++));
            }
        }
        return out;
    }

    private boolean canStartExpr(Token t) {
        return switch (t.type) {
            case NUMBER, STRING, IDENTIFIER, NIL, TRUE, FALSE, VARARG,
                 LPAREN, LCURLY, MINUS, NOT, HASH, TILDE -> true;
            default -> false;
        };
    }



    private Node parseExpr(int minPrec) {
        Node left = parseUnaryOrPrimary();
        if (left == null) return null;
        while (pos < tokens.size()) {
            Token op = tokens.get(pos);
            int prec = binPrec(op.type);
            if (prec < minPrec) break;
            boolean rightAssoc = op.type == TokenType.CARET || op.type == TokenType.CONCAT;
            int nextMin = rightAssoc ? prec : prec + 1;
            pos++; // consume operator
            Node right = parseExpr(nextMin);
            if (right == null) {

                List<Token> t = new ArrayList<>(emit(left));
                t.add(op);
                left = new OpaqueNode(t, left.line, left.col);
                break;
            }
            left = makeBinary(op, left, right);
        }
        return left;
    }

    private Node parseUnaryOrPrimary() {
        if (pos >= tokens.size()) return null;
        Token t = tokens.get(pos);
        if (t.type == TokenType.MINUS || t.type == TokenType.NOT
            || t.type == TokenType.HASH || t.type == TokenType.TILDE) {
            pos++;
            Node operand = parseExpr(UNARY_PREC);
            if (operand == null) {
                return new OpaqueNode(new ArrayList<>(List.of(t)), t.line, t.col);
            }
            return makeUnary(t, operand);
        }
        Node base = parsePrimaryCore();
        if (base == null) return null;
        return parseSuffixed(base);
    }

    private Node parsePrimaryCore() {
        if (pos >= tokens.size()) return null;
        Token t = tokens.get(pos);
        switch (t.type) {
            case NUMBER: {
                pos++;
                Double v = parseLuaNumber(t.value);
                if (v == null) {
                    return new OpaqueNode(new ArrayList<>(List.of(t)), t.line, t.col);
                }
                return new NumNode(v, t, t.line, t.col);
            }
            case TRUE:
                pos++;
                return new BoolNode(true, t.line, t.col);
            case FALSE:
                pos++;
                return new BoolNode(false, t.line, t.col);
            case STRING:
            case NIL:
            case VARARG:
            case IDENTIFIER:
                pos++;
                return new OpaqueNode(new ArrayList<>(List.of(t)), t.line, t.col);
            case LCURLY:
                return parseTable();
            case LPAREN:
                return parseGrouping();
            default:
                return null;
        }
    }

    private Node parseGrouping() {
        Token lp = tokens.get(pos);
        int save = pos;
        pos++; // consume '('
        Node inner = parseExpr(0);
        if (inner != null && pos < tokens.size() && tokens.get(pos).type == TokenType.RPAREN) {
            pos++; // consume ')'
            if (inner instanceof NumNode || inner instanceof BoolNode) {
                return inner;
            }
            List<Token> t = new ArrayList<>();
            t.add(lp);
            t.addAll(emit(inner));
            t.add(new Token(TokenType.RPAREN, ")", lp.line, lp.col));
            return new OpaqueNode(t, lp.line, lp.col);
        }
        // Not a clean "( expr )": treat '(' as a lone structural token.
        pos = save + 1;
        return new OpaqueNode(new ArrayList<>(List.of(lp)), lp.line, lp.col);
    }

    private Node parseTable() {
        Token lc = tokens.get(pos);
        pos++; // consume '{'
        List<Token> inner = foldSpanUntil(TokenType.RCURLY);
        List<Token> t = new ArrayList<>();
        t.add(lc);
        t.addAll(inner);
        if (pos < tokens.size() && tokens.get(pos).type == TokenType.RCURLY) {
            t.add(tokens.get(pos++));
        }
        return new OpaqueNode(t, lc.line, lc.col);
    }

    private Node parseSuffixed(Node base) {
        if (pos >= tokens.size() || !isSuffix(tokens.get(pos).type)) {
            return base;
        }
        List<Token> t = new ArrayList<>(emit(base));
        while (pos < tokens.size()) {
            Token t0 = tokens.get(pos);
            switch (t0.type) {
                case DOT:
                case COLON:
                    t.add(tokens.get(pos++));
                    if (pos < tokens.size() && tokens.get(pos).type == TokenType.IDENTIFIER) {
                        t.add(tokens.get(pos++));
                    }
                    break;
                case LBRACK:
                    t.add(tokens.get(pos++));
                    t.addAll(foldSpanUntil(TokenType.RBRACK));
                    if (pos < tokens.size() && tokens.get(pos).type == TokenType.RBRACK) {
                        t.add(tokens.get(pos++));
                    }
                    break;
                case LPAREN:
                    t.add(tokens.get(pos++));
                    t.addAll(foldSpanUntil(TokenType.RPAREN));
                    if (pos < tokens.size() && tokens.get(pos).type == TokenType.RPAREN) {
                        t.add(tokens.get(pos++));
                    }
                    break;
                case LCURLY: {
                    Node table = parseTable();
                    t.addAll(((OpaqueNode) table).tokens);
                    break;
                }
                case STRING:
                    t.add(tokens.get(pos++));
                    break;
                default:
                    return new OpaqueNode(t, base.line, base.col);
            }
        }
        return new OpaqueNode(t, base.line, base.col);
    }

    private boolean isSuffix(TokenType t) {
        return t == TokenType.DOT || t == TokenType.COLON || t == TokenType.LBRACK
            || t == TokenType.LPAREN || t == TokenType.LCURLY || t == TokenType.STRING;
    }


    private Node makeBinary(Token op, Node left, Node right) {
        if (left instanceof NumNode a && right instanceof NumNode b) {
            if (isArithOp(op.type)) {
                double v = compute(op.type, a.value, b.value);
                if (isFoldableInt(v)) {
                    return new NumNode(v, null, left.line, left.col);
                }
            } else if (isCompareOp(op.type)) {
                return new BoolNode(computeCompare(op.type, a.value, b.value), left.line, left.col);
            }
        }
        List<Token> t = new ArrayList<>(emit(left));
        t.add(op);
        t.addAll(emit(right));
        return new OpaqueNode(t, left.line, left.col);
    }

    private Node makeUnary(Token op, Node operand) {
        if (op.type == TokenType.MINUS && operand instanceof NumNode n) {
            double v = -n.value;
            if (isFoldableInt(v)) {
                return new NumNode(v, null, op.line, op.col);
            }
        }
        if (op.type == TokenType.NOT && operand instanceof BoolNode b) {
            return new BoolNode(!b.value, op.line, op.col);
        }
        List<Token> t = new ArrayList<>();
        t.add(op);
        t.addAll(emit(operand));
        return new OpaqueNode(t, op.line, op.col);
    }

    private boolean isFoldableInt(double v) {
        return Double.isFinite(v) && v == Math.floor(v) && Math.abs(v) < MAX_EXACT_INT;
    }

    private List<Token> emit(Node node) {
        if (node instanceof NumNode n) {
            if (n.original != null) {
                return new ArrayList<>(List.of(n.original));
            }
            long v = (long) n.value;
            List<Token> t = new ArrayList<>();
            if (v < 0) {
                t.add(new Token(TokenType.MINUS, "-", n.line, n.col));
                String s = String.valueOf(-v);
                t.add(new Token(TokenType.NUMBER, s, s, n.line, n.col));
            } else {
                String s = String.valueOf(v);
                t.add(new Token(TokenType.NUMBER, s, s, n.line, n.col));
            }
            return t;
        }
        if (node instanceof BoolNode b) {
            return new ArrayList<>(List.of(new Token(
                b.value ? TokenType.TRUE : TokenType.FALSE,
                b.value ? "true" : "false", node.line, node.col)));
        }
        return ((OpaqueNode) node).tokens;
    }



    private static double compute(TokenType op, double a, double b) {
        return switch (op) {
            case PLUS -> a + b;
            case MINUS -> a - b;
            case STAR -> a * b;
            case SLASH -> a / b;
            case PERCENT -> a - Math.floor(a / b) * b; // Lua modulo semantics
            case IDIV -> Math.floor(a / b);
            case CARET -> Math.pow(a, b);
            default -> Double.NaN;
        };
    }

    private static boolean computeCompare(TokenType op, double a, double b) {
        return switch (op) {
            case EQ -> a == b;
            case NE -> a != b;
            case LT -> a < b;
            case GT -> a > b;
            case LE -> a <= b;
            case GE -> a >= b;
            default -> false;
        };
    }

    private static boolean isArithOp(TokenType t) {
        return t == TokenType.PLUS || t == TokenType.MINUS || t == TokenType.STAR
            || t == TokenType.SLASH || t == TokenType.PERCENT
            || t == TokenType.IDIV || t == TokenType.CARET;
    }

    private static boolean isCompareOp(TokenType t) {
        return t == TokenType.EQ || t == TokenType.NE || t == TokenType.LT
            || t == TokenType.GT || t == TokenType.LE || t == TokenType.GE;
    }

    private static int binPrec(TokenType t) {
        return switch (t) {
            case OR -> 1;
            case AND -> 2;
            case LT, GT, LE, GE, NE, EQ -> 3;
            case PIPE -> 4;
            case TILDE -> 5;
            case AMPERSAND -> 6;
            case SHL, SHR -> 7;
            case CONCAT -> 9;
            case PLUS, MINUS -> 10;
            case STAR, SLASH, IDIV, PERCENT -> 11;
            case CARET -> 14;
            default -> -1;
        };
    }

    private static Double parseLuaNumber(String s) {
        try {
            String t = s.trim();
            if (t.length() > 2 && (t.charAt(0) == '0')
                && (t.charAt(1) == 'x' || t.charAt(1) == 'X')) {
                String hex = t.substring(2);
                if (hex.indexOf('.') >= 0 || hex.indexOf('p') >= 0 || hex.indexOf('P') >= 0) {
                    return null;
                }
                return (double) Long.parseLong(hex, 16);
            }
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
