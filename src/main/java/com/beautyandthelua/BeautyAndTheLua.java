package com.beautyandthelua;

import com.beautyandthelua.lexer.Lexer;
import com.beautyandthelua.lexer.Token;
import com.beautyandthelua.parser.Node;
import com.beautyandthelua.parser.Parser;
import com.beautyandthelua.formatter.Formatter;

import java.io.*;
import java.nio.file.*;
import java.util.List;

public class BeautyAndTheLua {
    private final Config config;

    public BeautyAndTheLua() {
        this(new Config());
    }

    public BeautyAndTheLua(Config config) {
        this.config = config;
    }

    public String beautify(String source) {
        return beautify(source, "<input>");
    }

    public String beautify(String source, String filename) {
        String prev = source;
        for (int i = 0; i < 4; i++) {
            String next = formatOnce(prev, filename);
            if (next.equals(prev)) return next;
            prev = next;
        }
        return prev;
    }

    private String formatOnce(String source, String filename) {
        Lexer lexer = new Lexer(source, filename);
        List<Token> tokens = lexer.tokenize();
        if (config.normalizeQuotes) {
            tokens = com.beautyandthelua.formatter.StringTransformer.normalize(tokens);
        }
        if (config.decodeStringEscapes) {
            tokens = com.beautyandthelua.formatter.StringTransformer.decode(tokens);
        }
        if (config.encodeStringEscapes) {
            tokens = com.beautyandthelua.formatter.StringTransformer.encode(tokens);
        }
        Parser parser = new Parser(tokens);
        Node.Block ast = parser.parse();
        Formatter formatter = new Formatter(config);
        String result = formatter.format(ast);
        if (config.removeTrailingWhitespace) {
            result = result.replaceAll("[ \\t]+\\n", "\n");
        }
        return result;
    }

    public boolean check(String source, String filename) {
        String formatted = beautify(source, filename);
        return source.equals(formatted);
    }

    private static void printUsage() {
        System.out.println("BeautyAndTheLua - a universal Lua/Luau beautifier");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  BeautyAndTheLua [options] <file>");
        System.out.println("  BeautyAndTheLua [options] <directory>");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -i, --indent <n>        Indent width (default: 4)");
        System.out.println("  -t, --tabs              Use tabs for indentation");
        System.out.println("  -c, --check             Check formatting without modifying");
        System.out.println("  -o, --output <file>     Output file (default: stdout)");
        System.out.println("      --stdin             Read from stdin");
        System.out.println("  -r, --recursive         Process directories recursively");
        System.out.println("  -e, --solve-expressions  Simplify constant arithmetic expressions (on by default)");
        System.out.println("      --no-solve-expressions  Disable constant folding and propagation");
        System.out.println("  -d, --decode-strings    Decode string escapes to readable characters");
        System.out.println("  -E, --encode-strings    Encode strings as \\ddd decimal escapes");
        System.out.println("      --no-dead-code      Disable dead code elimination");
        System.out.println("      --no-unused-locals  Keep unused pure locals");
        System.out.println("      --compound-assign   Rewrite x = x + 1 as x += 1");
        System.out.println("      --minify            Compact output, no indent or comments");
        System.out.println("      --rename-locals     Rename obfuscated locals to v1, v2, ...");
        System.out.println("  -v, --version           Show version");
        System.out.println("  -h, --help              Show this help");
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        Config config = new Config();
        boolean checkMode = false;
        boolean recursive = false;
        boolean readStdin = false;
        String outputFile = null;
        List<String> inputs = new java.util.ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-i": case "--indent":
                    config.indentWidth = Integer.parseInt(args[++i]);
                    break;
                case "-t": case "--tabs":
                    config.useTabs = true;
                    break;
                case "-c": case "--check":
                    checkMode = true;
                    break;
                case "-e": case "--solve-expressions":
                    config.solveExpressions = true;
                    break;
                case "--no-solve-expressions":
                    config.solveExpressions = false;
                    break;
                case "-d": case "--decode-strings":
                    config.decodeStringEscapes = true;
                    break;
                case "-E": case "--encode-strings":
                    config.encodeStringEscapes = true;
                    break;
                case "--no-dead-code":
                    config.eliminateDeadCode = false;
                    break;
                case "--no-unused-locals":
                    config.removeUnusedLocals = false;
                    break;
                case "--compound-assign":
                    config.compoundAssign = true;
                    break;
                case "--minify":
                    config.minify = true;
                    break;
                case "--rename-locals":
                    config.renameLocals = true;
                    break;
                case "-o": case "--output":
                    outputFile = args[++i];
                    break;
                case "--stdin":
                    readStdin = true;
                    break;
                case "-r": case "--recursive":
                    recursive = true;
                    break;
                case "-v": case "--version":
                    System.out.println("BeautyAndTheLua v1.0.0");
                    System.exit(0);
                    break;
                case "-h": case "--help":
                    printUsage();
                    System.exit(0);
                    break;
                default:
                    if (args[i].startsWith("-")) {
                        System.err.println("Unknown option: " + args[i]);
                        System.exit(1);
                    }
                    inputs.add(args[i]);
                    break;
            }
        }

        BeautyAndTheLua beautifier = new BeautyAndTheLua(config);

        if (readStdin) {
            try {
                String source = new String(System.in.readAllBytes());
                String result = beautifier.beautify(source, "<stdin>");
                if (outputFile != null) {
                    Files.writeString(Paths.get(outputFile), result);
                } else {
                    System.out.print(result);
                }
            } catch (IOException e) {
                System.err.println("Error reading stdin: " + e.getMessage());
                System.exit(1);
            }
            return;
        }

        if (inputs.isEmpty()) {
            printUsage();
            System.exit(1);
        }

        boolean allOk = true;
        boolean finalCheck = checkMode;
        String finalOutput = outputFile;
        for (String input : inputs) {
            Path path = Paths.get(input);
            if (!Files.exists(path)) {
                System.err.println("File not found: " + input);
                allOk = false;
                continue;
            }
            if (Files.isDirectory(path)) {
                if (!recursive) {
                    System.err.println("Skipping directory (use -r to process): " + input);
                    continue;
                }
                if (finalOutput != null) {
                    System.err.println("Cannot use --output with a directory: " + input);
                    allOk = false;
                    continue;
                }
                final BeautyAndTheLua fb = beautifier;
                final boolean[] dirOk = {true};
                try {
                    Files.walk(path)
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".lua"))
                        .forEach(p -> { if (!processFile(fb, p, finalCheck, null)) dirOk[0] = false; });
                    allOk &= dirOk[0];
                } catch (IOException e) {
                    System.err.println("Error walking directory: " + e.getMessage());
                    allOk = false;
                }
            } else {
                allOk &= processFile(beautifier, path, finalCheck, finalOutput);
            }
        }

        if (checkMode && !allOk) {
            System.exit(1);
        }
    }

    private static boolean processFile(BeautyAndTheLua beautifier, Path path, boolean checkMode, String outputFile) {
        try {
            String source = Files.readString(path);
            if (checkMode) {
                boolean ok = beautifier.check(source, path.toString());
                if (!ok) {
                    System.out.println(path + ": would reformat");
                }
                return ok;
            }
            String result = beautifier.beautify(source, path.toString());
            if (outputFile != null) {
                Files.writeString(Paths.get(outputFile), result);
            } else {
                Files.writeString(path, result);
            }
            return true;
        } catch (IOException e) {
            System.err.println("Error processing " + path + ": " + e.getMessage());
            return false;
        }
    }
}
