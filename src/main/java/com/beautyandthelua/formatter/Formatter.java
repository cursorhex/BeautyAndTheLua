package com.beautyandthelua.formatter;

import com.beautyandthelua.Config;
import com.beautyandthelua.lexer.Token;
import com.beautyandthelua.lexer.TokenType;
import com.beautyandthelua.parser.Node;

import java.util.List;

public class Formatter implements Node.Visitor {
    private final Config config;
    private final StringBuilder output;
    private int indentLevel;
    private boolean needsIndent;
    private ConstantPropagator propagator;

    public Formatter(Config config) {
        this.config = config;
        this.output = new StringBuilder();
        this.indentLevel = 0;
        this.needsIndent = true;
    }

    public String format(Node root) {
        if (root instanceof Node.Block block) {
            if (config.renameLocals) {
                LocalRenamer.run(block);
            }
            if (config.solveExpressions) {
                propagator = ConstantPropagator.analyze(block);
                if (config.eliminateDeadCode) {
                    new DeadCodeEliminator(propagator).run(block);
                }
                if (config.removeUnusedLocals) {
                    UnusedLocalEliminator.run(block);
                }
            }
        }
        root.accept(this);
        if (config.insertFinalNewline && output.length() > 0 && output.charAt(output.length() - 1) != '\n') {
            output.append('\n');
        }
        if (config.removeTrailingWhitespace) {
            String result = output.toString();
            result = result.replaceAll("[ \\t]+\n", "\n");
            result = result.replaceAll("[ \\t]+\r\n", "\r\n");
            return result;
        }
        return output.toString();
    }

    private void indent() {
        if (config.minify) return;
        if (config.useTabs) {
            output.append("\t".repeat(indentLevel));
        } else {
            output.append(" ".repeat(indentLevel * config.indentWidth));
        }
    }

    private void nl() {
        output.append('\n');
        needsIndent = true;
    }

    private void wr(String s) {
        if (needsIndent) {
            indent();
            needsIndent = false;
        }
        output.append(s);
    }

    private boolean isValue(Token t) {
        return switch (t.type) {
            case IDENTIFIER, NUMBER, STRING, NIL, TRUE, FALSE, VARARG,
                 RPAREN, RBRACK, RCURLY -> true;
            default -> false;
        };
    }

    private Token prevMeaningful(List<Token> tokens, int i) {
        for (int j = i - 1; j >= 0; j--) {
            TokenType tt = tokens.get(j).type;
            if (tt != TokenType.NEWLINE && tt != TokenType.COMMENT) return tokens.get(j);
        }
        return null;
    }

    private int findTableClose(List<Token> tokens, int open) {
        int depth = 0;
        for (int j = open; j < tokens.size(); j++) {
            TokenType t = tokens.get(j).type;
            if (t == TokenType.LCURLY) depth++;
            else if (t == TokenType.RCURLY) {
                depth--;
                if (depth == 0) return j;
            }
        }
        return -1;
    }

    private int currentCol() {
        int n = output.lastIndexOf("\n");
        return n < 0 ? output.length() : output.length() - n - 1;
    }

    private boolean trySingleLineTable(List<Token> tokens, int open, int close) {
        int est = 2;
        for (int j = open + 1; j < close; j++) {
            Token t = tokens.get(j);
            if (t.type == TokenType.COMMENT) {
                if (!config.minify) return false;
                continue;
            }
            if (t.type == TokenType.NEWLINE) continue;
            est += t.raw.length() + 1;
        }
        int base = currentCol();
        if (needsIndent) base += config.useTabs ? indentLevel : indentLevel * config.indentWidth;
        if (!config.minify && base + est > config.maxLineLength) return false;
        int end = close - 1;
        while (end > open && (tokens.get(end).type == TokenType.NEWLINE
            || tokens.get(end).type == TokenType.COMMA
            || tokens.get(end).type == TokenType.SEMI)) end--;
        boolean av = false;
        Token pv = tokens.get(open);
        for (int j = open + 1; j <= end; j++) {
            Token t = tokens.get(j);
            if (t.type == TokenType.NEWLINE) continue;
            String raw = (t.type == TokenType.SEMI) ? "," : t.raw;
            Token cur = (t.type == TokenType.SEMI)
                ? new Token(TokenType.COMMA, ",", t.line, t.col) : t;
            if (needSpace(pv, cur, av, tokens, j)) output.append(' ');
            output.append(raw);
            needsIndent = false;
            av = isValue(cur);
            pv = cur;
        }
        output.append('}');
        return true;
    }

    private void ensureTrailingComma() {
        int k = output.length();
        while (k > 0) {
            char c = output.charAt(k - 1);
            if (c == '\n' || c == ' ' || c == '\t' || c == '\r') k--;
            else break;
        }
        if (k <= 0) return;
        char lc = output.charAt(k - 1);
        if (lc != ',' && lc != ';' && lc != '{') {
            output.setLength(k);
            output.append(',');
        }
    }

    @Override
    public void visit(Node.Block block) {
        boolean first = true;
        for (int i = 0; i < block.children.size(); i++) {
            Node child = block.children.get(i);
            if (child instanceof Node.Stmt s && s.tokens.isEmpty()) {
                continue;
            }
            if (!first) {
                nl();
                if (!config.minify && config.preserveBlankLines && child.blankBefore > 0) {
                    int blanks = Math.min(child.blankBefore, config.maxBlankLines);
                    for (int b = 0; b < blanks; b++) {
                        nl();
                    }
                }
            }
            child.accept(this);
            first = false;
        }
    }

    @Override
    public void visit(Node.Stmt stmt) {
        List<Token> raw = config.solveExpressions && propagator != null
            ? propagator.substitute(stmt.tokens) : stmt.tokens;
        List<Token> tokens = config.solveExpressions
            ? ExpressionSolver.solve(raw) : raw;
        if (config.solveExpressions && config.compoundAssign) {
            tokens = CompoundAssign.convert(tokens);
        }
        boolean afterValue = false;
        int tableDepth = 0;
        boolean suppressNextSpace = false;
        boolean suppressCloseBrace = false;
        boolean sawBrace = false;

        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);

            if (t.type == TokenType.NEWLINE) {
                nl();
                afterValue = false;
                continue;
            }
            if (t.type == TokenType.COMMENT) {
                if (config.minify) { afterValue = false; continue; }
                Token before = prevMeaningful(tokens, i);
                boolean inline = config.keepInlineComments && before != null
                    && !needsIndent && t.line == before.line;
                if (inline) {
                    output.append(' ');
                    output.append(t.raw);
                } else {
                    if (!needsIndent) nl();
                    wr(t.raw);
                }
                afterValue = false;
                continue;
            }

            Token prev = i > 0 ? tokens.get(i - 1) : null;
            while (prev != null && prev.type == TokenType.NEWLINE) {
                prev = i > 1 ? tokens.get(i - 2) : null;
                break;
            }

            if (t.type == TokenType.LCURLY) {
                tableDepth++;
            } else if (t.type == TokenType.RCURLY) {
                if (tableDepth > 0) tableDepth--;
            }

            if (t.type == TokenType.LCURLY && tableDepth == 1) {
                boolean emptyTable = false;
                for (int j = i + 1; j < tokens.size(); j++) {
                    Token nxt = tokens.get(j);
                    if (nxt.type != TokenType.NEWLINE && nxt.type != TokenType.COMMENT) {
                        emptyTable = (nxt.type == TokenType.RCURLY);
                        break;
                    }
                }
                boolean needSp = !suppressNextSpace && needSpace(prev, t, afterValue, tokens, i);
                suppressNextSpace = false;
                if (needsIndent) {
                    indent();
                    needsIndent = false;
                }
                if (needSp) {
                    output.append(' ');
                }
                output.append(t.raw);
                if (emptyTable) {
                    output.append('}');
                    afterValue = true;
                    suppressCloseBrace = true;
                    continue;
                }
                int close = findTableClose(tokens, i);
                if (close > 0 && trySingleLineTable(tokens, i, close)) {
                    i = close;
                    afterValue = true;
                    continue;
                }
                sawBrace = true;
                indentLevel++;
                nl();
                afterValue = false;
                continue;
            }

            if (t.type == TokenType.RCURLY && tableDepth == 0) {
                if (suppressCloseBrace) {
                    suppressCloseBrace = false;
                } else {
                    if (sawBrace) ensureTrailingComma();
                    sawBrace = false;
                    if (indentLevel > 0) indentLevel--;
                    nl();
                    wr(t.raw);
                    afterValue = isValue(t);
                }
                continue;
            }

            if ((t.type == TokenType.COMMA || t.type == TokenType.SEMI) && tableDepth == 1) {
                wr(t.raw);
                nl();
                afterValue = false;
                suppressNextSpace = true;
                continue;
            }

            if (tableDepth == 0 && isStmtKeyword(t)) {
                boolean prevEndsStmt = prev != null && (
                    prev.type == TokenType.IDENTIFIER || prev.type == TokenType.NUMBER ||
                    prev.type == TokenType.STRING || prev.type == TokenType.NIL ||
                    prev.type == TokenType.TRUE || prev.type == TokenType.FALSE ||
                    prev.type == TokenType.VARARG || prev.type == TokenType.RPAREN ||
                    prev.type == TokenType.RBRACK || prev.type == TokenType.RCURLY ||
                    prev.type == TokenType.END || prev.type == TokenType.DO ||
                    prev.type == TokenType.THEN || prev.type == TokenType.REPEAT);
                if (prevEndsStmt && !(prev.type == TokenType.LOCAL && t.type == TokenType.FUNCTION)) {
                    nl();
                    wr(t.raw);
                    afterValue = false;
                    continue;
                }
            }

            boolean needSp = !suppressNextSpace && needSpace(prev, t, afterValue, tokens, i);
            suppressNextSpace = false;
            if (needsIndent) {
                indent();
                needsIndent = false;
            }
            if (needSp) {
                output.append(' ');
            }
            output.append(t.raw);
            if (t.type == TokenType.MINUS || t.type == TokenType.TILDE) {
            } else if (t.type == TokenType.NOT || t.type == TokenType.HASH) {
                afterValue = false;
            } else {
                afterValue = isValue(t);
            }
        }
    }

    private boolean isStmtKeyword(Token t) {
        return switch (t.type) {
            case LOCAL, FUNCTION, IF, WHILE, FOR, REPEAT, DO,
                 RETURN, BREAK, GOTO, ELSEIF, ELSE, END, UNTIL,
                 TYPE, EXPORT, IMPORT, TYPEOF -> true;
            default -> false;
        };
    }

    private boolean needSpace(Token prev, Token curr, boolean afterValue, List<Token> tokens, int i) {
        if (prev == null) return false;

        if (curr.type == TokenType.QMARK) return false;
        if (prev.type == TokenType.QMARK) return true;
        if (prev.type == TokenType.ATRATE || curr.type == TokenType.ATRATE) return false;
        if (curr.type == TokenType.LT && prev.type == TokenType.IDENTIFIER && isGenericOpen(tokens, i)) return false;
        if (curr.type == TokenType.GT && isGenericClose(tokens, i)) return false;
        if (curr.type == TokenType.SHR && isGenericClose(tokens, i)) return false;
        if (prev.type == TokenType.GT && curr.type == TokenType.LPAREN && isGenericClose(tokens, i - 1)) return false;
        if (prev.type == TokenType.LT && isGenericOpen(tokens, prevIndex(tokens, i))) return false;

        if (prev.type == TokenType.DOT || prev.type == TokenType.COLON) return false;
        if (curr.type == TokenType.DOT || curr.type == TokenType.COLON) return false;

        if (curr.type == TokenType.COMMA || curr.type == TokenType.SEMI) return false;
        if (prev.type == TokenType.COMMA) return config.spaceAfterComma;
        if (prev.type == TokenType.SEMI) return config.spaceAfterSemicolon;

        if (prev.type == TokenType.LPAREN || prev.type == TokenType.LBRACK) {
            return (prev.type == TokenType.LPAREN && config.spacesInsideParens) ||
                   (prev.type == TokenType.LBRACK && config.spacesInsideBrackets);
        }
        if (curr.type == TokenType.RPAREN || curr.type == TokenType.RBRACK) {
            return (curr.type == TokenType.RPAREN && config.spacesInsideParens) ||
                   (curr.type == TokenType.RBRACK && config.spacesInsideBrackets);
        }
        if (prev.type == TokenType.LCURLY) return config.spacesInsideBraces;
        if (curr.type == TokenType.RCURLY) return config.spacesInsideBraces;

        if (curr.type == TokenType.LPAREN) {
            if (prev.type == TokenType.IDENTIFIER && !config.spaceBeforeFunctionParen) return false;
            if (isBinaryOp(prev.type)) {
                if (prev.type == TokenType.MINUS || prev.type == TokenType.TILDE) {
                    return afterValue && config.spacesAroundOperators;
                }
                return config.spacesAroundOperators;
            }
            return isKeyword(prev);
        }
        if (curr.type == TokenType.LBRACK) return false;

        if (isKeyword(prev)) return true;

        if (isBinaryOp(curr.type)) return config.spacesAroundOperators;
        if (isBinaryOp(prev.type)) {
            if (prev.type == TokenType.MINUS || prev.type == TokenType.TILDE) {
                return afterValue && config.spacesAroundOperators;
            }
            return config.spacesAroundOperators;
        }

        if (curr.type == TokenType.MINUS || curr.type == TokenType.TILDE) {
            return afterValue && config.spacesAroundOperators;
        }

        if (prev.type == TokenType.RPAREN || prev.type == TokenType.RBRACK ||
            prev.type == TokenType.RCURLY) {
            return true;
        }

        if (isLiteral(curr) || isKeyword(curr)) {
            if (isLiteral(prev) || isKeyword(prev)) return true;
            return true;
        }

        return false;
    }

    private boolean isUnaryOp(TokenType t) {
        return t == TokenType.NOT || t == TokenType.HASH;
    }

    private boolean isGenericOpen(List<Token> tokens, int i) {
        int depth = 0;
        for (int j = i + 1; j < tokens.size() && j < i + 18; j++) {
            TokenType t = tokens.get(j).type;
            if (t == TokenType.LT) { depth++; continue; }
            if (t == TokenType.GT || t == TokenType.SHR) {
                if (depth == 0) return followsGeneric(tokens, j);
                depth--;
                if (t == TokenType.SHR) {
                    if (depth == 0) return followsGeneric(tokens, j);
                    depth--;
                }
                continue;
            }
            if (!isGenericPart(t)) return false;
        }
        return false;
    }

    private boolean isGenericClose(List<Token> tokens, int i) {
        for (int j = i - 1; j >= 0 && j > i - 18; j--) {
            if (tokens.get(j).type == TokenType.LT && isGenericOpen(tokens, j)) {
                int depth = 0;
                for (int k = j + 1; k <= i; k++) {
                    TokenType t = tokens.get(k).type;
                    if (t == TokenType.LT) depth++;
                    else if (t == TokenType.GT || t == TokenType.SHR) {
                        if (depth == 0) return k == i;
                        depth--;
                    }
                }
                return false;
            }
        }
        return false;
    }

    private boolean followsGeneric(List<Token> tokens, int j) {
        for (int k = j + 1; k < tokens.size(); k++) {
            TokenType t = tokens.get(k).type;
            if (t == TokenType.NEWLINE || t == TokenType.COMMENT) continue;
            return switch (t) {
                case LPAREN, COLON, ASSIGN, COMMA, RPAREN, RBRACK, RCURLY,
                     ARROW, EOF, END, ELSE, ELSEIF, THEN, DO, IN -> true;
                default -> false;
            };
        }
        return true;
    }

    private boolean isGenericPart(TokenType t) {
        return switch (t) {
            case IDENTIFIER, NUMBER, STRING, COLON, QMARK, PIPE, AMPERSAND,
                 COMMA, DOT, LCURLY, RCURLY, LPAREN, RPAREN, LBRACK, RBRACK,
                 ARROW, CONCAT, VARARG, TRUE, FALSE, NIL -> true;
            default -> false;
        };
    }

    private int prevIndex(List<Token> tokens, int i) {
        for (int j = i - 1; j >= 0; j--) {
            TokenType t = tokens.get(j).type;
            if (t != TokenType.NEWLINE && t != TokenType.COMMENT) return j;
        }
        return -1;
    }

    private boolean isLiteral(Token t) {
        return t.type == TokenType.IDENTIFIER || t.type == TokenType.NUMBER ||
               t.type == TokenType.STRING || t.type == TokenType.VARARG;
    }

    private boolean isKeyword(Token t) {
        return switch (t.type) {
            case AND, BREAK, CONTINUE, DO, ELSE, ELSEIF, END,
                 FALSE, FOR, FUNCTION, GOTO, IF, IN, LOCAL,
                 NIL, NOT, OR, REPEAT, RETURN, THEN, TRUE,
                 UNTIL, WHILE, TYPE, EXPORT, IMPORT, TYPEOF -> true;
            default -> false;
        };
    }

    private boolean isBinaryOp(TokenType t) {
        return switch (t) {
            case PLUS, MINUS, STAR, SLASH, PERCENT, CARET, IDIV,
                 AMPERSAND, PIPE, TILDE, SHL, SHR,
                 EQ, NE, LE, GE, LT, GT, CONCAT,
                 AND, OR, ASSIGN, PLUS_EQ, MINUS_EQ, STAR_EQ, SLASH_EQ,
                 IDIV_EQ, PERCENT_EQ, CARET_EQ, CONCAT_EQ, ARROW -> true;
            default -> false;
        };
    }

    @Override
    public void visit(Node.Comment comment) {
        Token t = comment.token;
        if (t.type == TokenType.SHEBANG) {
            wr(t.raw);
            return;
        }
        if (config.minify) return;
        if (!needsIndent) nl();
        wr(t.raw);
    }

    @Override
    public void visit(Node.IfStmt i) {
        for (int ci = 0; ci < i.clauses.size(); ci++) {
            Node.IfClause c = i.clauses.get(ci);
            if (ci > 0) { nl(); }
            printTokens(c.header);
            if (!c.body.children.isEmpty()) {
                indentLevel++; nl();
                c.body.accept(this);
                indentLevel--;
            }
        }
        if (i.elseBody != null) {
            nl(); wr("else");
            if (!i.elseBody.children.isEmpty()) {
                indentLevel++; nl();
                i.elseBody.accept(this);
                indentLevel--;
            }
        }
        if (!i.endToken.isEmpty()) {
            nl();
            printTokens(i.endToken);
        }
    }

    @Override
    public void visit(Node.RepeatStmt s) {
        printTokens(s.header);
        if (!s.body.children.isEmpty()) {
            indentLevel++; nl();
            s.body.accept(this);
            indentLevel--;
        }
        if (!s.untilTokens.isEmpty()) {
            nl();
            printTokens(s.untilTokens);
        }
    }

    @Override
    public void visit(Node.FuncStmt s) {
        printTokens(s.header);
        boolean hasEnd = !s.header.isEmpty() && s.header.get(s.header.size() - 1).value.equals("end");
        if (hasEnd) return;
        if (!s.body.children.isEmpty()) {
            indentLevel++; nl();
            s.body.accept(this);
            indentLevel--;
        }
        if (!s.endTokens.isEmpty()) {
            nl();
            printTokens(s.endTokens);
        }
    }

    @Override
    public void visit(Node.DoStmt s) {
        printTokens(s.header);
        if (!s.body.children.isEmpty()) {
            indentLevel++; nl();
            s.body.accept(this);
            indentLevel--;
        }
        if (!s.endTokens.isEmpty()) {
            nl();
            printTokens(s.endTokens);
        }
    }

    @Override
    public void visit(Node.ForStmt s) {
        printTokens(s.header);
        if (!s.body.children.isEmpty()) {
            indentLevel++; nl();
            s.body.accept(this);
            indentLevel--;
        }
        if (!s.endTokens.isEmpty()) {
            nl();
            printTokens(s.endTokens);
        }
    }

    @Override
    public void visit(Node.WhileStmt s) {
        printTokens(s.header);
        if (!s.body.children.isEmpty()) {
            indentLevel++; nl();
            s.body.accept(this);
            indentLevel--;
        }
        if (!s.endTokens.isEmpty()) {
            nl();
            printTokens(s.endTokens);
        }
    }

    private void printTokens(java.util.List<Token> tokenList) {
        java.util.List<Token> propagated = config.solveExpressions && propagator != null
            ? propagator.substitute(tokenList) : tokenList;
        java.util.List<Token> tokens = config.solveExpressions
            ? ExpressionSolver.solve(propagated) : propagated;
        boolean afterValue = false;
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.type == TokenType.NEWLINE) { nl(); afterValue = false; continue; }
            if (t.type == TokenType.COMMENT) {
                if (config.minify) { afterValue = false; continue; }
                if (!needsIndent) output.append(' ');
                wr(t.raw);
                afterValue = false;
                continue;
            }
            Token prev = i > 0 ? tokens.get(i - 1) : null;
            while (prev != null && prev.type == TokenType.NEWLINE) {
                prev = null;
                break;
            }
            if (prev != null && needSpace(prev, t, afterValue, tokens, i)) {
                output.append(' ');
                needsIndent = false;
            }
            wr(t.raw);
            afterValue = isValue(t);
        }
    }
}
