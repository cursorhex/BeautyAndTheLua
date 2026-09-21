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
        int pendingBlank = 0;
        boolean seenContent = false;
        boolean justAddedNode = false;

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
                if (!stmt.tokens.isEmpty()) { stmt.blankBefore = pendingBlank; block.children.add(stmt); }
                advance();
                return;
            }

            if (t.type == TokenType.NEWLINE) {
                advance();
                if (depth == 0) {
                    if (!stmt.tokens.isEmpty()) {
                        stmt.blankBefore = pendingBlank;
                        pendingBlank = 0;
                        block.children.add(stmt);
                        stmt = new Node.Stmt();
                        seenContent = true;
                        justAddedNode = false;
                    } else if (justAddedNode) {
                        justAddedNode = false;
                    } else if (seenContent) {
                        pendingBlank++;
                    }
                }
                continue;
            }

            if (depth == 0 && t.type == TokenType.SEMI) {
                advance();
                if (!stmt.tokens.isEmpty()) {
                    stmt.blankBefore = pendingBlank;
                    pendingBlank = 0;
                    block.children.add(stmt);
                    stmt = new Node.Stmt();
                    seenContent = true;
                    justAddedNode = true;
                }
                continue;
            }

            if (depth == 0 && isBlockCloser(t)) {
                if (!stmt.tokens.isEmpty()) { stmt.blankBefore = pendingBlank; block.children.add(stmt); }
                return;
            }

            if (t.type == TokenType.COMMENT) {
                advance();
                if (!stmt.tokens.isEmpty()) {
                    stmt.tokens.add(t);
                } else {
                    Node.Comment cm = new Node.Comment(t);
                    cm.blankBefore = pendingBlank;
                    pendingBlank = 0;
                    block.children.add(cm);
                    seenContent = true;
                    justAddedNode = true;
                }
                continue;
            }

            if (t.type == TokenType.SHEBANG) {
                advance();
                block.children.add(new Node.Comment(t));
                seenContent = true;
                justAddedNode = true;
                continue;
            }

            if (t.value.equals("if")) {
                if (!stmt.tokens.isEmpty()) { stmt.blankBefore = pendingBlank; pendingBlank = 0; block.children.add(stmt); stmt = new Node.Stmt(); }
                int b = pendingBlank; pendingBlank = 0;
                Node.IfStmt n = parseIfStmt();
                n.blankBefore = b;
                block.children.add(n);
                seenContent = true;
                justAddedNode = true;
                continue;
            }

            if (t.value.equals("repeat")) {
                if (!stmt.tokens.isEmpty()) { stmt.blankBefore = pendingBlank; pendingBlank = 0; block.children.add(stmt); stmt = new Node.Stmt(); }
                int b = pendingBlank; pendingBlank = 0;
                Node.RepeatStmt n = parseRepeatStmt();
                n.blankBefore = b;
                block.children.add(n);
                seenContent = true;
                justAddedNode = true;
                continue;
            }

            if (t.value.equals("function")) {
                if (!stmt.tokens.isEmpty()) {
                    Token prev = stmt.tokens.get(stmt.tokens.size() - 1);
                    if (prev.type == TokenType.ASSIGN) {
                        int b = pendingBlank; pendingBlank = 0;
                        Node.FuncStmt fs = parseFuncStmt(false, stmt.tokens);
                        fs.blankBefore = b;
                        block.children.add(fs);
                        stmt = new Node.Stmt();
                        seenContent = true;
                        justAddedNode = true;
                        continue;
                    }
                }
                if (stmt.tokens.isEmpty()) {
                    int b = pendingBlank; pendingBlank = 0;
                    Node.FuncStmt fs = parseFuncStmt(false);
                    fs.blankBefore = b;
                    block.children.add(fs);
                    seenContent = true;
                    justAddedNode = true;
                    continue;
                }
            }

            if (t.value.equals("local")) {
                Token next = peekAhead(1);
                if (next != null && next.value.equals("function")) {
                    if (!stmt.tokens.isEmpty()) { stmt.blankBefore = pendingBlank; pendingBlank = 0; block.children.add(stmt); stmt = new Node.Stmt(); }
                    int b = pendingBlank; pendingBlank = 0;
                    Node.FuncStmt fs = parseFuncStmt(true);
                    fs.blankBefore = b;
                    block.children.add(fs);
                    seenContent = true;
                    justAddedNode = true;
                    continue;
                }
                if (!stmt.tokens.isEmpty()) {
                    Token prev = stmt.tokens.get(stmt.tokens.size() - 1);
                    if (prev.type == TokenType.IDENTIFIER || prev.type == TokenType.NUMBER ||
                        prev.type == TokenType.STRING || prev.type == TokenType.NIL ||
                        prev.type == TokenType.TRUE || prev.type == TokenType.FALSE ||
                        prev.type == TokenType.VARARG || prev.type == TokenType.RPAREN ||
                        prev.type == TokenType.RBRACK || prev.type == TokenType.RCURLY) {
                        stmt.blankBefore = pendingBlank;
                        pendingBlank = 0;
                        block.children.add(stmt);
                        stmt = new Node.Stmt();
                        seenContent = true;
                    }
                }
            }

            if (t.value.equals("export")) {
                if (!stmt.tokens.isEmpty()) { stmt.blankBefore = pendingBlank; pendingBlank = 0; block.children.add(stmt); stmt = new Node.Stmt(); }
            }

            if (t.value.equals("import")) {
                if (!stmt.tokens.isEmpty()) { stmt.blankBefore = pendingBlank; pendingBlank = 0; block.children.add(stmt); stmt = new Node.Stmt(); }
            }

            if (t.value.equals("typeof")) {
                if (!stmt.tokens.isEmpty()) { stmt.blankBefore = pendingBlank; pendingBlank = 0; block.children.add(stmt); stmt = new Node.Stmt(); }
            }

            if (t.value.equals("do")) {
                if (!stmt.tokens.isEmpty()) { stmt.blankBefore = pendingBlank; pendingBlank = 0; block.children.add(stmt); stmt = new Node.Stmt(); }
                int b = pendingBlank; pendingBlank = 0;
                Node.DoStmt n = parseDoStmt();
                n.blankBefore = b;
                block.children.add(n);
                seenContent = true;
                justAddedNode = true;
                continue;
            }

            if (t.value.equals("while")) {
                if (!stmt.tokens.isEmpty()) { stmt.blankBefore = pendingBlank; pendingBlank = 0; block.children.add(stmt); stmt = new Node.Stmt(); }
                int b = pendingBlank; pendingBlank = 0;
                Node.WhileStmt n = parseWhileStmt();
                n.blankBefore = b;
                block.children.add(n);
                seenContent = true;
                justAddedNode = true;
                continue;
            }

            if (t.value.equals("for")) {
                if (!stmt.tokens.isEmpty()) { stmt.blankBefore = pendingBlank; pendingBlank = 0; block.children.add(stmt); stmt = new Node.Stmt(); }
                int b = pendingBlank; pendingBlank = 0;
                Node.ForStmt n = parseForStmt();
                n.blankBefore = b;
                block.children.add(n);
                seenContent = true;
                justAddedNode = true;
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
                    stmt.blankBefore = pendingBlank;
                    pendingBlank = 0;
                    block.children.add(stmt);
                    stmt = new Node.Stmt();
                    seenContent = true;
                }
            }

            stmt.tokens.add(advance());
            seenContent = true;
            justAddedNode = false;
        }

        if (!stmt.tokens.isEmpty()) { stmt.blankBefore = pendingBlank; block.children.add(stmt); }
    }

    private Token peekAhead(int n) {
        int idx = pos + n;
        return idx < tokens.size() ? tokens.get(idx) : null;
    }

    private void attachTrailingComment(List<Token> header) {
        if (header.isEmpty()) return;
        Token last = header.get(header.size() - 1);
        if (pos < tokens.size() && peek().type == TokenType.COMMENT
            && peek().line == last.line) {
            header.add(advance());
        }
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

        attachTrailingComment(clause.header);
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
                attachTrailingComment(ec.header);
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
        if (seenParen && parenDepth == 0 && pos < tokens.size()
            && peek().type == TokenType.COLON) {
            while (pos < tokens.size()) {
                Token nt = peek();
                if (nt.type == TokenType.NEWLINE || nt.type == TokenType.EOF
                    || nt.type == TokenType.COMMENT) break;
                node.header.add(advance());
            }
        }
        attachTrailingComment(node.header);
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
        attachTrailingComment(node.header);
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
        attachTrailingComment(node.header);
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
        attachTrailingComment(node.header);
        parseBlock(node.body);
        if (pos < tokens.size() && peek().value.equals("end")) {
            advance();
            node.endTokens.add(tokens.get(pos - 1));
        }
        return node;
    }
}
