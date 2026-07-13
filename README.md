# BeautyAndTheLua

A universal Lua/Luau code beautifier. Supports Lua 5.1, 5.2, 5.3, 5.4, Luau (Roblox), and all dialects in between.

## Features

- **Multi-dialect** — works with Lua 5.1 through 5.4, Luau, and Roblox Lua
- **Idempotent** — running the beautifier twice produces the same output
- **Comment-preserving** — shebangs, single-line and multi-line comments are kept intact
- **Configurable** — indent width, tabs vs spaces, spacing around operators, and more
- **Check mode** — verify formatting without modifying files (`--check`)
- **Recursive** — process entire directory trees (`--recursive`)
- **No dependencies** — pure Java, zero external libraries
- **CLI + library** — use from the command line or embed in your own tools

## Usage

```
BeautyAndTheLua [options] <file|directory>
```

| Option | Description |
|--------|-------------|
| `-i, --indent <n>` | Indent width (default: 4) |
| `-t, --tabs` | Use tabs for indentation |
| `-w, --width <n>` | Max line length (default: 120) |
| `-c, --check` | Check formatting without modifying files |
| `-o, --output <file>` | Write to file instead of in-place |
| `--stdin` | Read source from stdin |
| `-r, --recursive` | Process directories recursively |
| `-v, --version` | Show version |
| `-h, --help` | Show help |

### Examples

```bash
# Format a single file in-place
java -jar BeautyAndTheLua.jar script.lua

# Check formatting without modifying
java -jar BeautyAndTheLua.jar --check script.lua

# Process all .lua files in a directory tree
java -jar BeautyAndTheLua.jar --recursive src/

# Use 2-space indentation
java -jar BeautyAndTheLua.jar --indent 2 script.lua

# Read from stdin, write to stdout
cat script.lua | java -jar BeautyAndTheLua.jar --stdin > formatted.lua
```

## Building

```bash
mvn package
```

The JAR will be in `target/BeautyAndTheLua-1.0.0.jar`.

## API

```java
import com.beautyandthelua.BeautyAndTheLua;
import com.beautyandthelua.Config;

BeautyAndTheLua beautifier = new BeautyAndTheLua();
String formatted = beautifier.beautify(source);

// With custom config
Config config = new Config()
    .indentWidth(2)
    .useTabs(false)
    .spacesAroundOperators(true);
beautifier = new BeautyAndTheLua(config);
formatted = beautifier.beautify(source, "script.lua");

// Check mode
boolean isFormatted = beautifier.check(source, "script.lua");
```

## Input / Output

| Before | After |
|--------|-------|
| `local x=1` | `local x = 1` |
| `if x>0 then` | `if x > 0 then` |
| `for i,v in pairs(t) do` | `for i, v in pairs(t) do` |
| `local t={1,2,3}` | `local t = {1, 2, 3}` |
| `print(i,v)` | `print(i, v)` |
| `return{1,2}` | `return {1, 2}` |
| `x=-1` | `x = -1` |
| `x=n-1` | `x = n - 1` |

## Supported Syntax

- All Lua 5.1–5.4 keywords and operators (`::`, `//`, `<<`, `>>`, `&`, `|`, `~`)
- Luau extensions (`continue`, `type`, `export type`, `typeof`)
- Long strings and comments (`[[...]]`, `[=[...]=]`)
- Shebang lines (`#!/usr/bin/env lua`)
- Goto labels (`::label::`)
- Numeric and generic `for` loops
- `repeat...until`, `while...do`, `if...then...elseif...else...end`
- Nested block structures

## License

MIT
