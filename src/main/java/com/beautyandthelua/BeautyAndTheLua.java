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
        Lexer lexer = new Lexer(source, filename);
        List<Token> tokens = lexer.tokenize();
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
        System.out.println("  -w, --width <n>         Max line length (default: 120)");
        System.out.println("  -c, --check             Check formatting without modifying");
        System.out.println("  -o, --output <file>     Output file (default: stdout)");
        System.out.println("      --stdin             Read from stdin");
        System.out.println("  -r, --recursive         Process directories recursively");
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
                case "-w": case "--width":
                    config.maxLineLength = Integer.parseInt(args[++i]);
                    break;
                case "-c": case "--check":
                    checkMode = true;
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
                try {
                    Files.walk(path)
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".lua"))
                        .forEach(p -> processFile(beautifier, p, finalCheck, finalOutput));
                } catch (IOException e) {
                    System.err.println("Error walking directory: " + e.getMessage());
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
