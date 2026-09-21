package com.beautyandthelua.formatter;

import com.beautyandthelua.lexer.Token;
import com.beautyandthelua.lexer.TokenType;
import com.beautyandthelua.parser.Node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class ConstantPropagator {

    private final IdentityHashMap<List<Token>, Map<String, Token>> envByList = new IdentityHashMap<>();

    public static ConstantPropagator analyze(Node.Block root) {
        ConstantPropagator cp = new ConstantPropagator();
        Scope rootScope = cp.buildScope(root);
        cp.walk(root, rootScope, new HashMap<>(), Set.of());
        return cp;
    }

    public List<Token> substitute(List<Token> tokens) {
        Map<String, Token> env = envByList.get(tokens);
        if (env == null || env.isEmpty()) return tokens;

        Set<String> excluded = lhsNames(tokens);
        List<Token> out = new ArrayList<>(tokens.size());
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.type == TokenType.IDENTIFIER && !excluded.contains(t.value) && env.containsKey(t.value)) {
                Token prev = prevMeaningful(tokens, i);
                Token next = nextMeaningful(tokens, i);
                boolean isField = prev != null && (prev.type == TokenType.DOT || prev.type == TokenType.COLON);
                boolean isTarget = next != null && next.type == TokenType.ASSIGN;
                if (!isField && !isTarget) {
                    Token lit = env.get(t.value);
                    out.add(new Token(lit.type, lit.value, lit.raw, t.line, t.col));
                    continue;
                }
            }
            out.add(t);
        }
        return out;
    }


    private static final class Scope {
        final Map<String, Integer> localCounts = new HashMap<>();
        final Set<String> declaredLocals = new HashSet<>();
        final Set<String> assignedNames = new HashSet<>();
        final List<Scope> children = new ArrayList<>();

        void declare(String name) {
            declaredLocals.add(name);
            localCounts.merge(name, 1, Integer::sum);
        }
    }

    private Scope buildScope(Node.Block block) {
        Scope scope = new Scope();
        for (Node child : block.children) {
            collectDecls(child, scope);
        }
        return scope;
    }

    private void collectDecls(Node node, Scope scope) {
        if (node instanceof Node.Stmt s) {
            for (String n : localDeclNames(s.tokens)) scope.declare(n);
            for (String n : reassignedNames(s.tokens)) scope.assignedNames.add(n);
        } else if (node instanceof Node.IfStmt ifs) {
            for (Node.IfClause c : ifs.clauses) {
                scope.children.add(buildScope(c.body));
            }
            if (ifs.elseBody != null) scope.children.add(buildScope(ifs.elseBody));
        } else if (node instanceof Node.WhileStmt w) {
            scope.children.add(buildScope(w.body));
        } else if (node instanceof Node.ForStmt f) {
            Scope body = buildScope(f.body);
            for (String n : loopVarNames(f.header)) body.declare(n);
            scope.children.add(body);
        } else if (node instanceof Node.DoStmt d) {
            scope.children.add(buildScope(d.body));
        } else if (node instanceof Node.RepeatStmt r) {
            scope.children.add(buildScope(r.body));
        } else if (node instanceof Node.FuncStmt fn) {
            for (String n : funcAssignTargets(fn.header)) scope.assignedNames.add(n);
            Scope body = buildScope(fn.body);
            if (fn.isLocal) {
                String name = localFuncName(fn.header);
                if (name != null) scope.declare(name);
            }
            for (String n : funcParamNames(fn.header)) body.declare(n);
            scope.children.add(body);
        }
    }


    private void walk(Node.Block block, Scope scope, Map<String, Token> parentEnv, Set<String> headerLocals) {
        Map<String, Token> env = new HashMap<>(parentEnv);
        for (String n : headerLocals) env.remove(n);

        int childIdx = 0;
        for (Node child : block.children) {
            childIdx = walkChild(child, scope, env, childIdx);
        }
    }

    private int walkChild(Node child, Scope scope, Map<String, Token> env, int childIdx) {
        if (child instanceof Node.Stmt s) {
            record(s.tokens, env);
            applyStmtEffect(s.tokens, scope, env);
        } else if (child instanceof Node.Comment) {
        } else if (child instanceof Node.IfStmt ifs) {
            for (Node.IfClause c : ifs.clauses) {
                record(c.header, env);
                walk(c.body, scope.children.get(childIdx++), env, Set.of());
            }
            if (ifs.elseBody != null) {
                walk(ifs.elseBody, scope.children.get(childIdx++), env, Set.of());
            }
        } else if (child instanceof Node.WhileStmt w) {
            record(w.header, env);
            walk(w.body, scope.children.get(childIdx++), env, Set.of());
        } else if (child instanceof Node.ForStmt f) {
            Set<String> loopVars = new HashSet<>(loopVarNames(f.header));
            recordExcluding(f.header, env, loopVars);
            walk(f.body, scope.children.get(childIdx++), env, loopVars);
        } else if (child instanceof Node.DoStmt d) {
            walk(d.body, scope.children.get(childIdx++), env, Set.of());
        } else if (child instanceof Node.RepeatStmt r) {
            Scope bodyScope = scope.children.get(childIdx++);
            Map<String, Token> bodyEnv = new HashMap<>(env);
            int bi = 0;
            for (Node bc : r.body.children) {
                bi = walkChild(bc, bodyScope, bodyEnv, bi);
            }
            record(r.untilTokens, bodyEnv);
        } else if (child instanceof Node.FuncStmt fn) {
            Set<String> params = new HashSet<>(funcParamNames(fn.header));
            Set<String> headerExcl = new HashSet<>(params);
            headerExcl.addAll(funcAssignTargets(fn.header));
            if (fn.isLocal) {
                String name = localFuncName(fn.header);
                if (name != null) headerExcl.add(name);
            }
            recordExcluding(fn.header, env, headerExcl);
            walk(fn.body, scope.children.get(childIdx++), env, params);
            for (String n : funcAssignTargets(fn.header)) env.remove(n);
        }
        return childIdx;
    }

    private void applyStmtEffect(List<Token> tokens, Scope scope, Map<String, Token> env) {
        List<String> declared = localDeclNames(tokens);
        if (!declared.isEmpty()) {
            Token lit = singleLiteralDecl(tokens);
            if (lit != null && declared.size() == 1 && isFinalConst(scope, declared.get(0))) {
                env.put(declared.get(0), lit);
            } else {
                for (String n : declared) env.remove(n);
            }
            return;
        }
        for (String n : reassignedNames(tokens)) env.remove(n);
    }

    private boolean isFinalConst(Scope scope, String name) {
        Integer c = scope.localCounts.get(name);
        return c != null && c == 1 && !isAssignedInRegion(scope, name);
    }

    private boolean isAssignedInRegion(Scope scope, String name) {
        if (scope.assignedNames.contains(name)) return true;
        for (Scope child : scope.children) {
            if (child.declaredLocals.contains(name)) continue;
            if (isAssignedInRegion(child, name)) return true;
        }
        return false;
    }

    private void record(List<Token> tokens, Map<String, Token> env) {
        if (!env.isEmpty()) envByList.put(tokens, new HashMap<>(env));
    }

    private void recordExcluding(List<Token> tokens, Map<String, Token> env, Set<String> exclude) {
        Map<String, Token> snap = new HashMap<>(env);
        for (String n : exclude) snap.remove(n);
        if (!snap.isEmpty()) envByList.put(tokens, snap);
    }

    private static int topLevelAssign(List<Token> tokens) {
        int depth = 0;
        for (int i = 0; i < tokens.size(); i++) {
            TokenType t = tokens.get(i).type;
            switch (t) {
                case LPAREN, LBRACK, LCURLY -> depth++;
                case RPAREN, RBRACK, RCURLY -> depth--;
                case ASSIGN -> { if (depth == 0) return i; }
                default -> { }
            }
        }
        return -1;
    }

    private static List<String> localDeclNames(List<Token> tokens) {
        List<String> names = new ArrayList<>();
        if (tokens.isEmpty() || tokens.get(0).type != TokenType.LOCAL) return names;
        int eq = topLevelAssign(tokens);
        int end = eq == -1 ? tokens.size() : eq;
        for (int i = 1; i < end; i++) {
            Token t = tokens.get(i);
            if (t.type == TokenType.IDENTIFIER) {
                Token prev = i > 0 ? tokens.get(i - 1) : null;
                if (prev != null && (prev.type == TokenType.DOT || prev.type == TokenType.COLON)) continue;
                names.add(t.value);
            } else if (t.type == TokenType.COLON) {
                i++;
            }
        }
        return names;
    }

    private static List<String> reassignedNames(List<Token> tokens) {
        List<String> names = new ArrayList<>();
        if (tokens.isEmpty() || tokens.get(0).type == TokenType.LOCAL) return names;
        int eq = topLevelAssign(tokens);
        if (eq <= 0) return names;
        for (int i = 0; i < eq; i++) {
            Token t = tokens.get(i);
            if (t.type == TokenType.IDENTIFIER) {
                Token prev = i > 0 ? tokens.get(i - 1) : null;
                Token next = i + 1 < eq ? tokens.get(i + 1) : (i + 1 < tokens.size() ? tokens.get(i + 1) : null);
                boolean field = prev != null && (prev.type == TokenType.DOT || prev.type == TokenType.COLON);
                boolean indexedOrCalled = next != null && (next.type == TokenType.DOT
                    || next.type == TokenType.COLON || next.type == TokenType.LBRACK
                    || next.type == TokenType.LPAREN);
                if (!field && !indexedOrCalled) names.add(t.value);
            } else if (t.type != TokenType.COMMA) {
                if (t.type != TokenType.DOT && t.type != TokenType.COLON
                    && t.type != TokenType.LBRACK && t.type != TokenType.RBRACK) {
                    return new ArrayList<>();
                }
            }
        }
        return names;
    }

    private static Set<String> lhsNames(List<Token> tokens) {
        Set<String> names = new HashSet<>();
        if (tokens.isEmpty()) return names;
        boolean isLocal = tokens.get(0).type == TokenType.LOCAL;
        int eq = topLevelAssign(tokens);
        int end;
        int start = isLocal ? 1 : 0;
        if (eq == -1) {
            if (!isLocal) return names;
            end = tokens.size();
        } else {
            end = eq;
        }
        for (int i = start; i < end; i++) {
            Token t = tokens.get(i);
            if (t.type == TokenType.IDENTIFIER) {
                Token prev = i > 0 ? tokens.get(i - 1) : null;
                if (prev != null && (prev.type == TokenType.DOT || prev.type == TokenType.COLON)) continue;
                names.add(t.value);
            } else if (isLocal && t.type == TokenType.COLON) {
                i++;
            }
        }
        return names;
    }

    private static Token singleLiteralDecl(List<Token> tokens) {
        if (tokens.isEmpty() || tokens.get(0).type != TokenType.LOCAL) return null;
        int eq = topLevelAssign(tokens);
        if (eq == -1 || eq + 1 >= tokens.size()) return null;
        Token val = tokens.get(eq + 1);
        if (!isLiteral(val)) return null;
        for (int i = eq + 2; i < tokens.size(); i++) {
            TokenType t = tokens.get(i).type;
            if (t != TokenType.NEWLINE && t != TokenType.COMMENT) return null;
        }
        return val;
    }

    private static boolean isLiteral(Token t) {
        return t.type == TokenType.NUMBER || t.type == TokenType.STRING
            || t.type == TokenType.TRUE || t.type == TokenType.FALSE
            || t.type == TokenType.NIL;
    }

    private static List<String> loopVarNames(List<Token> header) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < header.size(); i++) {
            Token t = header.get(i);
            if (t.type == TokenType.FOR) continue;
            if (t.type == TokenType.ASSIGN || t.type == TokenType.IN || t.value.equals("do")) break;
            if (t.type == TokenType.IDENTIFIER) names.add(t.value);
        }
        return names;
    }

    private static List<String> funcAssignTargets(List<Token> header) {
        int eq = topLevelAssign(header);
        if (eq > 0) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < eq; i++) {
                Token t = header.get(i);
                if (t.type == TokenType.IDENTIFIER) {
                    Token prev = i > 0 ? header.get(i - 1) : null;
                    Token next = i + 1 < eq ? header.get(i + 1) : null;
                    boolean field = prev != null && (prev.type == TokenType.DOT || prev.type == TokenType.COLON);
                    boolean indexed = next != null && (next.type == TokenType.DOT
                        || next.type == TokenType.COLON || next.type == TokenType.LBRACK);
                    if (!field && !indexed) names.add(t.value);
                }
            }
            return names;
        }
        if (header.size() >= 2 && header.get(0).type == TokenType.FUNCTION
            && header.get(1).type == TokenType.IDENTIFIER) {
            Token after = header.size() >= 3 ? header.get(2) : null;
            if (after == null || after.type == TokenType.LPAREN) {
                return new ArrayList<>(List.of(header.get(1).value));
            }
        }
        return new ArrayList<>();
    }

    private static String localFuncName(List<Token> header) {
        for (int i = 0; i + 1 < header.size(); i++) {
            if (header.get(i).type == TokenType.FUNCTION
                && header.get(i + 1).type == TokenType.IDENTIFIER) {
                return header.get(i + 1).value;
            }
        }
        return null;
    }

    private static List<String> funcParamNames(List<Token> header) {
        List<String> names = new ArrayList<>();
        int lp = -1, depth = 0;
        for (int i = 0; i < header.size(); i++) {
            Token t = header.get(i);
            if (t.type == TokenType.LPAREN) { lp = i; break; }
        }
        if (lp == -1) return names;
        for (int i = lp; i < header.size(); i++) {
            Token t = header.get(i);
            if (t.type == TokenType.LPAREN) { depth++; continue; }
            if (t.type == TokenType.RPAREN) { depth--; if (depth == 0) break; continue; }
            if (depth == 1 && t.type == TokenType.IDENTIFIER) {
                Token prev = header.get(i - 1);
                if (prev.type == TokenType.LPAREN || prev.type == TokenType.COMMA) {
                    names.add(t.value);
                }
            }
        }
        return names;
    }

    private static Token prevMeaningful(List<Token> tokens, int i) {
        for (int j = i - 1; j >= 0; j--) {
            TokenType t = tokens.get(j).type;
            if (t != TokenType.NEWLINE && t != TokenType.COMMENT) return tokens.get(j);
        }
        return null;
    }

    private static Token nextMeaningful(List<Token> tokens, int i) {
        for (int j = i + 1; j < tokens.size(); j++) {
            TokenType t = tokens.get(j).type;
            if (t != TokenType.NEWLINE && t != TokenType.COMMENT) return tokens.get(j);
        }
        return null;
    }
}
