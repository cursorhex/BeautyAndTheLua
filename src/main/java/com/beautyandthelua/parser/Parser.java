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

        while (pos < tokens.size()) {
            Token t = peek();
            if (t.type == TokenType.EOF) {
                if (!stmt.tokens.isEmpty()) block.children.add(stmt);
                advance();
                return;
            }

            if (t.type == TokenType.NEWLINE) {
                advance();
                if (!stmt.tokens.isEmpty()) {
                    block.children.add(stmt);
                    stmt = new Node.Stmt();
                }
                continue;
            }

            if (t.type == TokenType.SEMI) {
                advance();
                if (!stmt.tokens.isEmpty()) {
                    block.children.add(stmt);
                    stmt = new Node.Stmt();
                }
                continue;
            }

            if (isBlockCloser(t)) {
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
                if (!stmt.tokens.isEmpty()) { block.children.add(stmt); stmt = new Node.Stmt(); }
                block.children.add(parseFuncStmt(false));
                continue;
            }

            if (t.value.equals("local") && peekAhead(1) != null && peekAhead(1).value.equals("function")) {
                if (!stmt.tokens.isEmpty()) { block.children.add(stmt); stmt = new Node.Stmt(); }
                block.children.add(parseFuncStmt(true));
                continue;
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
        Node.FuncStmt node = new Node.FuncStmt();
        node.isLocal = isLocal;
        if (isLocal) {
            node.header.add(advance());
        }
        while (pos < tokens.size()) {
            Token t = advance();
            node.header.add(t);
            if (t.value.equals("end")) return node;
            if (t.type == TokenType.NEWLINE) break;
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
