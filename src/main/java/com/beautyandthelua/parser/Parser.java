package com.beautyandthelua.parser;

import com.beautyandthelua.lexer.Token;
import com.beautyandthelua.lexer.TokenType;

import java.util.List;

public class Parser {
    private final List<Token> tokens;
    private int pos;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.pos = 0;
    }

    public Node.Block parse() {
        Node.Block root = new Node.Block();
        parseBlock(root);
        return root;
    }

    private Token peek() {
        return pos < tokens.size() ? tokens.get(pos) : null;
    }

    private Token advance() {
        return tokens.get(pos++);
    }

    private Token expect(TokenType type) {
        Token t = advance();
        if (t.type != type) {
            throw new RuntimeException(String.format("expected %s but got %s at %d:%d",
                type, t.type, t.line, t.col));
        }
        return t;
    }

    private boolean match(String... values) {
        Token t = peek();
        if (t == null) return false;
        for (String v : values) {
            if (t.value.equals(v)) return true;
        }
        return false;
    }

    private boolean isBlockCloser(Token t) {
        return t.type == TokenType.END || t.type == TokenType.UNTIL ||
               t.type == TokenType.ELSE || t.type == TokenType.ELSEIF ||
               t.type == TokenType.EOF;
    }

    private boolean isKw(String word) {
        Token t = peek();
        return t != null && t.value.equals(word);
    }

    private void parseBlock(Node.Block block) {
        Node.Stmt stmt = new Node.Stmt();
        int depth = 0;

        while (pos < tokens.size()) {
            Token t = peek();

            if (t.type == TokenType.LPAREN || t.type == TokenType.LBRACK || t.type == TokenType.LCURLY) {
                depth++;
            }
            if (t.type == TokenType.RPAREN || t.type == TokenType.RBRACK || t.type == TokenType.RCURLY) {
                depth--;
                if (depth < 0) depth = 0;
            }

            if (t.type == TokenType.EOF) {
                if (!stmt.tokens.isEmpty()) block.children.add(stmt);
                advance();
                return;
            }

            if (t.type == TokenType.NEWLINE) {
                advance();
                if (depth == 0 && !stmt.tokens.isEmpty()) {
                    block.children.add(stmt);
                    stmt = new Node.Stmt();
                }
                continue;
            }

            if (depth == 0 && t.type == TokenType.SEMI) {
                advance();
                if (!stmt.tokens.isEmpty()) {
                    block.children.add(stmt);
                    stmt = new Node.Stmt();
                }
                continue;
            }

            if (depth == 0 && isBlockCloser(t)) {
                if (!stmt.tokens.isEmpty()) block.children.add(stmt);
                return;
            }

            if (t.type == TokenType.COMMENT) {
                advance();
                if (!stmt.tokens.isEmpty()) {
                    stmt.tokens.add(t);
                } else {
                    block.children.add(new Node.Comment(t));
                }
                continue;
            }

            if (t.type == TokenType.SHEBANG) {
                advance();
                block.children.add(new Node.Comment(t));
                continue;
            }

            if (t.value.equals("if")) {
                if (!stmt.tokens.isEmpty()) { block.children.add(stmt); stmt = new Node.Stmt(); }
                block.children.add(parseIfStmt());
                continue;
            }

            if (t.value.equals("repeat")) {
                if (!stmt.tokens.isEmpty()) { block.children.add(stmt); stmt = new Node.Stmt(); }
                block.children.add(parseRepeatStmt());
                continue;
            }

            if (t.value.equals("function")) {
                if (!stmt.tokens.isEmpty()) {
                    Token prev = stmt.tokens.get(stmt.tokens.size() - 1);
                    if (prev.type == TokenType.ASSIGN) {
                        Node.FuncStmt fs = parseFuncStmt(false, stmt.tokens);
                        block.children.add(fs);
                        stmt = new Node.Stmt();
                        continue;
                    }
                }
                if (stmt.tokens.isEmpty()) {
                    block.children.add(parseFuncStmt(false));
                    continue;
                }
            }

            if (t.value.equals("local")) {
                Token next = peekAhead(1);
                if (next != null && next.value.equals("function")) {
                    if (!stmt.tokens.isEmpty()) { block.children.add(stmt); stmt = new Node.Stmt(); }
                    block.children.add(parseFuncStmt(true));
                    continue;
                }
                if (!stmt.tokens.isEmpty()) {
                    Token prev = stmt.tokens.get(stmt.tokens.size() - 1);
                    if (prev.type == TokenType.IDENTIFIER || prev.type == TokenType.NUMBER ||
                        prev.type == TokenType.STRING || prev.type == TokenType.NIL ||
                        prev.type == TokenType.TRUE || prev.type == TokenType.FALSE ||
                        prev.type == TokenType.VARARG || prev.type == TokenType.RPAREN ||
                        prev.type == TokenType.RBRACK || prev.type == TokenType.RCURLY) {
                        block.children.add(stmt);
                        stmt = new Node.Stmt();
                    }
                }
            }

            if (t.value.equals("type")) {
                if (stmt.tokens.isEmpty()) {
                    block.children.add(stmt); stmt = new Node.Stmt();
                }
            }

            if (t.value.equals("export")) {
                if (!stmt.tokens.isEmpty()) { block.children.add(stmt); stmt = new Node.Stmt(); }
            }

            if (t.value.equals("import")) {
                if (!stmt.tokens.isEmpty()) { block.children.add(stmt); stmt = new Node.Stmt(); }
            }

            if (t.value.equals("typeof")) {
                if (!stmt.tokens.isEmpty()) { block.children.add(stmt); stmt = new Node.Stmt(); }
            }

            if (t.value.equals("do")) {
                if (!stmt.tokens.isEmpty()) { block.children.add(stmt); stmt = new Node.Stmt(); }
                block.children.add(parseDoStmt());
                continue;
            }

            if (t.value.equals("while")) {
                if (!stmt.tokens.isEmpty()) { block.children.add(stmt); stmt = new Node.Stmt(); }
                block.children.add(parseWhileStmt());
                continue;
            }

            if (t.value.equals("for")) {
                if (!stmt.tokens.isEmpty()) { block.children.add(stmt); stmt = new Node.Stmt(); }
                block.children.add(parseForStmt());
                continue;
            }

            if (t.type == TokenType.IDENTIFIER && !stmt.tokens.isEmpty()) {
                Token prev = stmt.tokens.get(stmt.tokens.size() - 1);
                boolean prevIsValue = prev.type == TokenType.IDENTIFIER ||
                    prev.type == TokenType.NUMBER || prev.type == TokenType.STRING ||
                    prev.type == TokenType.NIL || prev.type == TokenType.TRUE ||
                    prev.type == TokenType.FALSE || prev.type == TokenType.VARARG ||
                    prev.type == TokenType.RPAREN || prev.type == TokenType.RBRACK ||
                    prev.type == TokenType.RCURLY || prev.type == TokenType.END;
                boolean prevIsKw = prev.type == TokenType.LOCAL || prev.type == TokenType.RETURN ||
                    prev.type == TokenType.BREAK || prev.type == TokenType.GOTO ||
                    prev.type == TokenType.IF || prev.type == TokenType.WHILE ||
                    prev.type == TokenType.FOR || prev.type == TokenType.REPEAT ||
                    prev.type == TokenType.DO || prev.type == TokenType.FUNCTION ||
                    prev.type == TokenType.ELSE || prev.type == TokenType.ELSEIF ||
                    prev.type == TokenType.THEN || prev.type == TokenType.AND ||
                    prev.type == TokenType.OR || prev.type == TokenType.NOT;
                if (prevIsValue && !prevIsKw) {
                    block.children.add(stmt);
                    stmt = new Node.Stmt();
                }
            }

            stmt.tokens.add(advance());
        }

        if (!stmt.tokens.isEmpty()) block.children.add(stmt);
    }

    private Token peekAhead(int n) {
        int idx = pos + n;
        return idx < tokens.size() ? tokens.get(idx) : null;
    }

    private void skipNewlines() {
        while (pos < tokens.size() && peek().type == TokenType.NEWLINE) advance();
    }

    private Node.IfStmt parseIfStmt() {
        Node.IfStmt node = new Node.IfStmt();
        Node.IfClause clause = new Node.IfClause();

        while (pos < tokens.size()) {
            Token t = advance();
            clause.header.add(t);
            if (t.value.equals("then")) break;
            if (t.type == TokenType.EOF) throw new RuntimeException("unexpected EOF in if");
        }

        parseBlock(clause.body);
        node.clauses.add(clause);

        while (pos < tokens.size()) {
            Token t = peek();
            if (t.value.equals("elseif")) {
                Node.IfClause ec = new Node.IfClause();
                while (pos < tokens.size()) {
                    Token ct = advance();
                    ec.header.add(ct);
                    if (ct.value.equals("then")) break;
                }
                parseBlock(ec.body);
                node.clauses.add(ec);
            } else if (t.value.equals("else")) {
                advance();
                node.elseBody = new Node.Block();
                parseBlock(node.elseBody);
            } else if (t.value.equals("end")) {
                advance();
                node.endToken.add(t);
                break;
            } else {
                break;
            }
        }

        return node;
    }

    private Node.RepeatStmt parseRepeatStmt() {
        Node.RepeatStmt node = new Node.RepeatStmt();
        node.header.add(advance());
        parseBlock(node.body);
        if (pos < tokens.size() && peek().value.equals("until")) {
            node.untilTokens.add(advance());
            while (pos < tokens.size()) {
                Token t = peek();
                if (t.type == TokenType.NEWLINE || t.type == TokenType.EOF ||
                    t.type == TokenType.SEMI || t.type == TokenType.COMMENT) break;
                node.untilTokens.add(advance());
            }
        }
        return node;
    }

    private Node.FuncStmt parseFuncStmt(boolean isLocal) {
        return parseFuncStmt(isLocal, null);
    }

    private Node.FuncStmt parseFuncStmt(boolean isLocal, List<Token> prefix) {
        Node.FuncStmt node = new Node.FuncStmt();
        node.isLocal = isLocal;
        if (prefix != null) {
            node.header.addAll(prefix);
        }
        if (isLocal && prefix == null) {
            node.header.add(advance());
        }
        int parenDepth = 0;
        boolean seenParen = false;
        while (pos < tokens.size()) {
            Token t = advance();
            node.header.add(t);
            if (t.type == TokenType.LPAREN) { parenDepth++; seenParen = true; }
            if (t.type == TokenType.RPAREN) parenDepth--;
            if (seenParen && parenDepth == 0) break;
            if (t.value.equals("end")) return node;
        }
        parseBlock(node.body);
        if (pos < tokens.size() && peek().value.equals("end")) {
            advance();
            node.endTokens.add(tokens.get(pos - 1));
        }
        return node;
    }

    private Node.DoStmt parseDoStmt() {
        Node.DoStmt node = new Node.DoStmt();
        node.header.add(advance());
        parseBlock(node.body);
        if (pos < tokens.size() && peek().value.equals("end")) {
            advance();
            node.endTokens.add(tokens.get(pos - 1));
        }
        return node;
    }

    private Node.WhileStmt parseWhileStmt() {
        Node.WhileStmt node = new Node.WhileStmt();
        while (pos < tokens.size()) {
            Token t = advance();
            node.header.add(t);
            if (t.value.equals("do")) break;
            if (t.type == TokenType.NEWLINE || t.type == TokenType.EOF) break;
        }
        parseBlock(node.body);
        if (pos < tokens.size() && peek().value.equals("end")) {
            advance();
            node.endTokens.add(tokens.get(pos - 1));
        }
        return node;
    }

    private Node.ForStmt parseForStmt() {
        Node.ForStmt node = new Node.ForStmt();
        while (pos < tokens.size()) {
            Token t = advance();
            node.header.add(t);
            if (t.value.equals("do")) break;
            if (t.type == TokenType.NEWLINE || t.type == TokenType.EOF) break;
        }
        parseBlock(node.body);
        if (pos < tokens.size() && peek().value.equals("end")) {
            advance();
            node.endTokens.add(tokens.get(pos - 1));
        }
        return node;
    }
}
