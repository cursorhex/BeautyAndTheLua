package com.beautyandthelua.parser;

import com.beautyandthelua.lexer.Token;

import java.util.ArrayList;
import java.util.List;

public abstract class Node {
    /** Number of blank lines that appeared before this node in the source. */
    public int blankBefore;

    public abstract void accept(Visitor visitor);

    public interface Visitor {
        void visit(Block block);
        void visit(Stmt stmt);
        void visit(Comment comment);
        void visit(IfStmt ifStmt);
        void visit(RepeatStmt repeatStmt);
        void visit(FuncStmt funcStmt);
        void visit(DoStmt doStmt);
        void visit(ForStmt forStmt);
        void visit(WhileStmt whileStmt);
        void visit(TableNode tableNode);
    }

    public static class Block extends Node {
        public final List<Node> children = new ArrayList<>();
        public boolean isTable;
        public boolean isInline;

        public Block() {}

        public Block(boolean isTable) {
            this.isTable = isTable;
        }

        @Override
        public void accept(Visitor visitor) { visitor.visit(this); }
    }

    public static class Stmt extends Node {
        public final List<Token> tokens = new ArrayList<>();

        public Stmt() {}

        public Stmt(List<Token> tokens) {
            this.tokens.addAll(tokens);
        }

        @Override
        public void accept(Visitor visitor) { visitor.visit(this); }
    }

    public static class Comment extends Node {
        public final Token token;

        public Comment(Token token) {
            this.token = token;
        }

        @Override
        public void accept(Visitor visitor) { visitor.visit(this); }
    }

    public static class IfClause {
        public final List<Token> header = new ArrayList<>();
        public final Block body = new Block();
    }

    public static class IfStmt extends Node {
        public final List<IfClause> clauses = new ArrayList<>();
        public Block elseBody;
        public final List<Token> endToken = new ArrayList<>();

        @Override
        public void accept(Visitor visitor) { visitor.visit(this); }
    }

    public static class RepeatStmt extends Node {
        public final List<Token> header = new ArrayList<>();
        public final Block body = new Block();
        public final List<Token> untilTokens = new ArrayList<>();

        @Override
        public void accept(Visitor visitor) { visitor.visit(this); }
    }

    public static class FuncStmt extends Node {
        public final List<Token> header = new ArrayList<>();
        public final Block body = new Block();
        public final List<Token> endTokens = new ArrayList<>();
        public boolean isLocal;

        @Override
        public void accept(Visitor visitor) { visitor.visit(this); }
    }

    public static class DoStmt extends Node {
        public final List<Token> header = new ArrayList<>();
        public final Block body = new Block();
        public final List<Token> endTokens = new ArrayList<>();

        @Override
        public void accept(Visitor visitor) { visitor.visit(this); }
    }

    public static class ForStmt extends Node {
        public final List<Token> header = new ArrayList<>();
        public final Block body = new Block();
        public final List<Token> endTokens = new ArrayList<>();

        @Override
        public void accept(Visitor visitor) { visitor.visit(this); }
    }

    public static class WhileStmt extends Node {
        public final List<Token> header = new ArrayList<>();
        public final Block body = new Block();
        public final List<Token> endTokens = new ArrayList<>();

        @Override
        public void accept(Visitor visitor) { visitor.visit(this); }
    }

    public static class TableNode extends Node {
        public final Token openBrace;
        public final Block fields = new Block();
        public final List<Token> trailingTokens = new ArrayList<>();
        public boolean singleLine;

        public TableNode(Token openBrace) {
            this.openBrace = openBrace;
        }

        @Override
        public void accept(Visitor visitor) { visitor.visit(this); }
    }
}
