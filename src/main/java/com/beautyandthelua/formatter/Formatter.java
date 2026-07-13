package com.beautyandthelua.formatter;

import com.beautyandthelua.Config;
import com.beautyandthelua.lexer.Token;
import com.beautyandthelua.lexer.TokenType;
import com.beautyandthelua.parser.Node;

public class Formatter implements Node.Visitor {
    private final Config config;
    private final StringBuilder output;
    private int indentLevel;
    private boolean needsIndent;

    public Formatter(Config config) {
        this.config = config;
        this.output = new StringBuilder();
        this.indentLevel = 0;
        this.needsIndent = true;
    }

    public String format(Node root) {
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

    @Override
    public void visit(Node.Block block) {
        for (int i = 0; i < block.children.size(); i++) {
            Node child = block.children.get(i);
            if (i > 0 && !(child instanceof Node.Comment)) {
                nl();
            }
            child.accept(this);
        }
    }

    @Override
    public void visit(Node.Stmt stmt) {
        boolean afterValue = false;
        int tableDepth = 0;
        boolean suppressNextSpace = false;
        boolean suppressCloseBrace = false;

        for (int i = 0; i < stmt.tokens.size(); i++) {
            Token t = stmt.tokens.get(i);

            if (t.type == TokenType.NEWLINE) {
                nl();
                afterValue = false;
                continue;
            }
            if (t.type == TokenType.COMMENT) {
                if (!needsIndent) nl();
                wr(t.raw);
                afterValue = false;
                continue;
            }

            Token prev = i > 0 ? stmt.tokens.get(i - 1) : null;
            while (prev != null && prev.type == TokenType.NEWLINE) {
                prev = i > 1 ? stmt.tokens.get(i - 2) : null;
                break;
            }

            if (t.type == TokenType.LCURLY) {
                tableDepth++;
            } else if (t.type == TokenType.RCURLY) {
                if (tableDepth > 0) tableDepth--;
            }

            if (t.type == TokenType.LCURLY && tableDepth == 1) {
                boolean emptyTable = false;
                for (int j = i + 1; j < stmt.tokens.size(); j++) {
                    Token nxt = stmt.tokens.get(j);
                    if (nxt.type != TokenType.NEWLINE && nxt.type != TokenType.COMMENT) {
                        emptyTable = (nxt.type == TokenType.RCURLY);
                        break;
                    }
                }
                boolean needSp = !suppressNextSpace && needSpace(prev, t, afterValue);
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
                indentLevel++;
                nl();
                afterValue = false;
                continue;
            }

            if (t.type == TokenType.RCURLY && tableDepth == 0) {
                if (suppressCloseBrace) {
                    suppressCloseBrace = false;
                } else {
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

            boolean needSp = !suppressNextSpace && needSpace(prev, t, afterValue);
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

    private boolean needSpace(Token prev, Token curr, boolean afterValue) {
        if (prev == null) return false;

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
                 AND, OR, ASSIGN -> true;
            default -> false;
        };
    }

    @Override
    public void visit(Node.Comment comment) {
        Token t = comment.token;
        if (t.type == TokenType.SHEBANG) {
            wr(t.raw); nl();
            return;
        }
        if (!needsIndent) nl();
        wr(t.raw);
        nl();
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
        indentLevel++; nl();
        s.body.accept(this);
        indentLevel--;
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
        indentLevel++; nl();
        s.body.accept(this);
        indentLevel--;
        if (!s.endTokens.isEmpty()) {
            nl();
            printTokens(s.endTokens);
        }
    }

    @Override
    public void visit(Node.ForStmt s) {
        printTokens(s.header);
        indentLevel++; nl();
        s.body.accept(this);
        indentLevel--;
        if (!s.endTokens.isEmpty()) {
            nl();
            printTokens(s.endTokens);
        }
    }

    @Override
    public void visit(Node.WhileStmt s) {
        printTokens(s.header);
        indentLevel++; nl();
        s.body.accept(this);
        indentLevel--;
        if (!s.endTokens.isEmpty()) {
            nl();
            printTokens(s.endTokens);
        }
    }

    @Override
    public void visit(Node.TableNode t) {
        wr("{");
        boolean single = true;
        int tc = 0;
        for (Node c : t.fields.children) {
            if (c instanceof Node.Stmt s) tc += s.tokens.size();
            else tc++;
        }
        single = tc <= config.singleLineTableMaxFields && config.singleLineTableIfSimple;

        if (single) {
            indentLevel++;
            for (Node c : t.fields.children) {
                if (c instanceof Node.Stmt s) printTokens(s.tokens);
                else if (c instanceof Node.Comment cn) { wr(" "); wr(cn.token.raw); }
            }
            indentLevel--;
        } else {
            indentLevel++; nl();
            t.fields.accept(this);
            indentLevel--; nl();
        }
        for (Token tk : t.trailingTokens) wr(tk.raw);
    }

    private void printTokens(java.util.List<Token> tokens) {
        boolean afterValue = false;
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.type == TokenType.NEWLINE) { nl(); afterValue = false; continue; }
            Token prev = i > 0 ? tokens.get(i - 1) : null;
            while (prev != null && prev.type == TokenType.NEWLINE) {
                prev = null;
                break;
            }
            if (prev != null && needSpace(prev, t, afterValue)) {
                output.append(' ');
                needsIndent = false;
            }
            wr(t.raw);
            afterValue = isValue(t);
        }
    }
}
