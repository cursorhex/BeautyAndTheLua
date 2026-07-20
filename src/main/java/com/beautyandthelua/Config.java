package com.beautyandthelua;

public class Config {
    public int indentWidth = 4;
    public boolean useTabs = false;
    public boolean indentWithTabs = false;
    public boolean addNewlineAtEnd = true;
    public int maxLineLength = 120;
    public boolean spacesAroundOperators = true;
    public boolean spacesInsideBrackets = false;
    public boolean spacesInsideParens = false;
    public boolean spacesInsideBraces = false;
    public boolean spaceAfterComma = true;
    public boolean spaceAfterSemicolon = true;
    public boolean spaceBeforeFunctionParen = false;
    public boolean alignConsecutiveAssignments = false;
    public boolean alignConsecutiveTableFields = false;
    public boolean preserveBlankLines = true;
    public int maxBlankLines = 2;
    public boolean keepInlineComments = true;
    public boolean removeTrailingWhitespace = true;
    public boolean tableNewlineAfterLBrace = true;
    public boolean tableNewlineBeforeRBrace = true;
    public boolean singleLineTableIfSimple = true;
    public int singleLineTableMaxFields = 4;
    public boolean insertFinalNewline = true;
    public boolean solveExpressions = true;

    public Config() {}

    public Config indentWidth(int w) { this.indentWidth = w; return this; }
    public Config useTabs(boolean t) { this.useTabs = t; return this; }
    public Config maxLineLength(int m) { this.maxLineLength = m; return this; }
    public Config spacesAroundOperators(boolean s) { this.spacesAroundOperators = s; return this; }
    public Config solveExpressions(boolean s) { this.solveExpressions = s; return this; }
    public Config preserveBlankLines(boolean p) { this.preserveBlankLines = p; return this; }
    public Config maxBlankLines(int m) { this.maxBlankLines = m; return this; }
    public Config keepInlineComments(boolean k) { this.keepInlineComments = k; return this; }
}
