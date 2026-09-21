package com.beautyandthelua;

public class Config {
    public int indentWidth = 4;
    public boolean useTabs = false;
    public int maxLineLength = 100;
    public boolean normalizeQuotes = true;
    public boolean spacesAroundOperators = true;
    public boolean spacesInsideBrackets = false;
    public boolean spacesInsideParens = false;
    public boolean spacesInsideBraces = false;
    public boolean spaceAfterComma = true;
    public boolean spaceAfterSemicolon = true;
    public boolean spaceBeforeFunctionParen = false;
    public boolean preserveBlankLines = true;
    public int maxBlankLines = 2;
    public boolean keepInlineComments = true;
    public boolean decodeStringEscapes = false;
    public boolean encodeStringEscapes = false;
    public boolean removeTrailingWhitespace = true;
    public boolean insertFinalNewline = true;
    public boolean solveExpressions = true;
    public boolean eliminateDeadCode = true;
    public boolean removeUnusedLocals = true;
    public boolean compoundAssign = false;
    public boolean minify = false;
    public boolean renameLocals = false;

    public Config() {}

    public Config indentWidth(int w) { this.indentWidth = w; return this; }
    public Config useTabs(boolean t) { this.useTabs = t; return this; }
    public Config maxLineLength(int m) { this.maxLineLength = m; return this; }
    public Config normalizeQuotes(boolean n) { this.normalizeQuotes = n; return this; }
    public Config spacesAroundOperators(boolean s) { this.spacesAroundOperators = s; return this; }
    public Config solveExpressions(boolean s) { this.solveExpressions = s; return this; }
    public Config preserveBlankLines(boolean p) { this.preserveBlankLines = p; return this; }
    public Config maxBlankLines(int m) { this.maxBlankLines = m; return this; }
    public Config keepInlineComments(boolean k) { this.keepInlineComments = k; return this; }
    public Config decodeStringEscapes(boolean d) { this.decodeStringEscapes = d; return this; }
    public Config encodeStringEscapes(boolean e) { this.encodeStringEscapes = e; return this; }
    public Config eliminateDeadCode(boolean e) { this.eliminateDeadCode = e; return this; }
    public Config removeUnusedLocals(boolean r) { this.removeUnusedLocals = r; return this; }
    public Config compoundAssign(boolean c) { this.compoundAssign = c; return this; }
    public Config minify(boolean m) { this.minify = m; return this; }
    public Config renameLocals(boolean r) { this.renameLocals = r; return this; }
}
