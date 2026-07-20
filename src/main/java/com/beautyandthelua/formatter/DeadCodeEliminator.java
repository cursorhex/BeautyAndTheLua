package com.beautyandthelua.formatter;

import com.beautyandthelua.lexer.Token;
import com.beautyandthelua.lexer.TokenType;
import com.beautyandthelua.parser.Node;

import java.util.ArrayList;
import java.util.List;

public class DeadCodeEliminator {

    private enum Truth { TRUE, FALSE, UNKNOWN }

    private final ConstantPropagator propagator;

    public DeadCodeEliminator(ConstantPropagator propagator) {
        this.propagator = propagator;
    }

    public void run(Node.Block root) {
        rewriteBlock(root);
    }

    private void rewriteBlock(Node.Block block) {
        List<Node> out = new ArrayList<>(block.children.size());
        for (Node child : block.children) {
            Node r = rewriteNode(child);
            if (r != null) out.add(r);
        }
        block.children.clear();
        block.children.addAll(out);
    }

    private Node rewriteNode(Node n) {
        if (n instanceof Node.IfStmt ifs) {
            for (Node.IfClause c : ifs.clauses) rewriteBlock(c.body);
            if (ifs.elseBody != null) rewriteBlock(ifs.elseBody);
            return transformIf(ifs);
        }
        if (n instanceof Node.WhileStmt w) {
            rewriteBlock(w.body);
            if (evalCond(w.header, "do") == Truth.FALSE) return null;
            return w;
        }
        if (n instanceof Node.ForStmt f) {
            rewriteBlock(f.body);
            return f;
        }
        if (n instanceof Node.DoStmt d) {
            rewriteBlock(d.body);
            return d;
        }
        if (n instanceof Node.RepeatStmt r) {
            rewriteBlock(r.body);
            return r;
        }
        if (n instanceof Node.FuncStmt fn) {
            rewriteBlock(fn.body);
            return fn;
        }
        if (n instanceof Node.TableNode tn) {
            rewriteBlock(tn.fields);
            return tn;
        }
        return n;
    }

    private Node transformIf(Node.IfStmt ifs) {
        List<Node.IfClause> survivors = new ArrayList<>();
        Node.Block takenBody = null;
        boolean taken = false;
        for (Node.IfClause c : ifs.clauses) {
            Truth t = evalCond(c.header, "then");
            if (t == Truth.FALSE) continue;
            if (t == Truth.TRUE) {
                takenBody = c.body;
                taken = true;
                break;
            }
            survivors.add(c);
        }

        if (taken) {
            if (survivors.isEmpty()) {
                return makeDo(takenBody, ifs.blankBefore);
            }
            fixFirstKeyword(survivors);
            ifs.clauses.clear();
            ifs.clauses.addAll(survivors);
            ifs.elseBody = takenBody;
            return ifs;
        }

        if (survivors.isEmpty()) {
            if (ifs.elseBody != null) return makeDo(ifs.elseBody, ifs.blankBefore);
            return null;
        }
        fixFirstKeyword(survivors);
        ifs.clauses.clear();
        ifs.clauses.addAll(survivors);
        return ifs;
    }

    private Node makeDo(Node.Block body, int blankBefore) {
        if (body.children.isEmpty()) return null;
        Node.DoStmt d = new Node.DoStmt();
        d.header.add(new Token(TokenType.DO, "do", 0, 0));
        d.body.children.addAll(body.children);
        d.endTokens.add(new Token(TokenType.END, "end", 0, 0));
        d.blankBefore = blankBefore;
        return d;
    }

    private void fixFirstKeyword(List<Node.IfClause> survivors) {
        List<Token> header = survivors.get(0).header;
        for (int i = 0; i < header.size(); i++) {
            Token t = header.get(i);
            if (t.type == TokenType.NEWLINE || t.type == TokenType.COMMENT) continue;
            if (t.type == TokenType.ELSEIF) {
                header.set(i, new Token(TokenType.IF, "if", t.line, t.col));
            }
            return;
        }
    }

    private Truth evalCond(List<Token> header, String endWord) {
        List<Token> h = propagator != null ? propagator.substitute(header) : header;
        h = ExpressionSolver.solve(h);
        List<Token> cond = new ArrayList<>();
        boolean started = false;
        for (Token t : h) {
            if (t.type == TokenType.NEWLINE || t.type == TokenType.COMMENT) continue;
            if (!started) { started = true; continue; }
            if (t.value.equals(endWord)) break;
            cond.add(t);
        }
        if (cond.size() != 1) return Truth.UNKNOWN;
        return switch (cond.get(0).type) {
            case TRUE, NUMBER, STRING -> Truth.TRUE;
            case FALSE, NIL -> Truth.FALSE;
            default -> Truth.UNKNOWN;
        };
    }
}
