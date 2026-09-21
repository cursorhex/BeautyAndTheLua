# BeautyAndTheLua

A universal Lua/Luau code beautifier. Supports Lua 5.1, 5.2, 5.3, 5.4, Luau (Roblox), and all dialects in between.

## Features

- Multi-dialect: works with Lua 5.1 through 5.4, Luau, and Roblox Lua
- Idempotent: running the beautifier twice produces the same output
- Comment-preserving: shebangs, single-line and multi-line comments are kept intact, including inline trailing comments
- Blank-line aware: keeps intentional blank lines between statements, capped at a configurable maximum
- Configurable: indent width, tabs vs spaces, spacing around operators, and more
- Constant folding: simplifies constant arithmetic and propagates constant locals (on by default)
- Dead code elimination: removes branches whose conditions fold to a constant (on by default)
- Unused locals: drops `local x = <literal>` never read (on by default)
- Compound assign: rewrites `x = x + 1` as `x += 1` (opt-in)
- Minify: compact output without indent or comments (opt-in)
- Rename locals: renames `_0x...` locals to `v1, v2, ...` (opt-in)
- String escapes: decode `\ddd` escapes to readable text or re-encode them
- Check mode: verify formatting without modifying files (`--check`)
- Recursive: process entire directory trees (`--recursive`)
- No dependencies: zero external libraries
- CLI and library: use from the command line or embed in your own tools

## Usage

```
BeautyAndTheLua [options] <file|directory>
```

| Option | Description |
|--------|-------------|
| `-i, --indent <n>` | Indent width (default: 4) |
| `-t, --tabs` | Use tabs for indentation |
| `-c, --check` | Check formatting without modifying files |
| `-e, --solve-expressions` | Fold constant arithmetic and propagate constant locals (on by default) |
| `--no-solve-expressions` | Disable constant folding and propagation |
| `-d, --decode-strings` | Decode string escapes to readable characters |
| `-E, --encode-strings` | Encode strings as `\ddd` decimal escapes |
| `--no-dead-code` | Disable dead code elimination |
| `--no-unused-locals` | Keep unused pure locals |
| `--compound-assign` | Rewrite `x = x + 1` as `x += 1` |
| `--minify` | Compact output, no indent or comments |
| `--rename-locals` | Rename obfuscated locals to `v1, v2, ...` |
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

# Fold constant expressions while formatting
java -jar BeautyAndTheLua.jar -e script.lua

# Format without folding constant expressions
java -jar BeautyAndTheLua.jar --no-solve-expressions script.lua

# Decode escaped strings into readable text
java -jar BeautyAndTheLua.jar -d script.lua

# Encode strings as decimal escapes
java -jar BeautyAndTheLua.jar -E script.lua

# Rewrite x = x + 1 as x += 1
java -jar BeautyAndTheLua.jar --compound-assign script.lua

# Minify
java -jar BeautyAndTheLua.jar --minify script.lua

# Rename obfuscated local names
java -jar BeautyAndTheLua.jar --rename-locals script.lua

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

With `-e` enabled:

| Before | After |
|--------|-------|
| `local y = 2 + 3 * 4` | `local y = 14` |
| `local z = 2 ^ 3 ^ 2` | `local z = 512` |
| `local x = 5; local y = x + 3` | `local y = 8` |
| `local s = "a" .. "b"` | `local s = "ab"` |
| `local b = not nil` | `local b = true` |
| `local x: number = 5; local y = x + 1` | `local y = 6` |

## Constant folding and propagation

Constant folding and propagation run by default. Pass `--no-solve-expressions`
to turn them off, or `-e` to state the intent explicitly. Two passes run before
formatting:

- **ExpressionSolver** folds constant sub-expressions. It parses each expression
  respecting Lua operator precedence and associativity, so `2 + 3 * 4` becomes `14`
  and `2 ^ 3 ^ 2` becomes `512`. It folds exact integers, hex floats (`0x1p4`),
  string concat (`"a" .. "b"`), boolean `and`/`or`/`not`, and `==`/`~=` on
  numbers, strings and booleans. A result is only substituted when it is exact,
  so folding never changes program behavior. Anything non-constant is left untouched.
- **ConstantPropagator** replaces references to constant locals with their values.
  A local is propagated only when it is effectively final: declared once as
  `local n = <literal>` (Luau type annotations like `local n: number = 5` count)
  and never reassigned in the region where it is visible.
  Scopes are tracked properly, so a `local` inside a nested block or function
  never leaks into the enclosing scope.

Both passes are conservative by design: when in doubt, they leave the code as-is.

## Whitespace and comments

Layout-level cleanup is always on and is controlled by the `Config` object:

- **Blank lines**: runs of blank lines between statements are preserved but capped
  at `maxBlankLines` (default 2). Leading and trailing blank lines inside a block
  are removed. Set `preserveBlankLines` to `false` to collapse all blank lines.
- **Inline comments**: a comment that sits on the same source line as the code
  before it stays on that line (`local x = 1 -- note`). This also applies to block
  headers, such as `function f() -- ...`, `if cond then -- ...`, and
  `for i = 1, n do -- ...`. Set `keepInlineComments` to `false` to push every
  comment onto its own line.

## Dead code elimination

After constant folding, branch conditions that reduce to a constant are resolved
and unreachable code is dropped. Runs by default; pass `--no-dead-code` to keep
every branch.

- `if false then ... end` is removed entirely.
- `if true then X end` inlines `X`, or becomes `do X end` when `X` declares locals.
- Dead `elseif` clauses are dropped, and the first clause that is always taken
  turns the rest of the chain into a plain block.
- `while false do ... end` is removed.
- Empty `do end` is removed, and `do X end` without locals inlines `X`.

Conditions are only resolved when they fold to a single literal, so anything
depending on runtime values is left untouched.

## String escapes

Two optional, opposite transforms rewrite short-string literals before parsing:

- **Decode** (`-d` / `decodeStringEscapes`): turns escape sequences back into
  readable characters where it is safe. `"\056\051\052"` becomes `"834"`.
  Control characters, quotes and backslashes stay escaped; bytes outside
  printable ASCII are kept as `\ddd`.
- **Encode** (`-E` / `encodeStringEscapes`): renders every byte as a `\ddd`
  decimal escape, the form many obfuscators emit. `"Hi"` becomes `"\072\105"`.

Both decode the literal to its raw bytes first, so each direction is idempotent
and the two can be chained. Long strings (`[[...]]`) and literals containing an
unsupported escape (unicode or `\z`) are left untouched.

## Supported Syntax

- All Lua 5.1 to 5.4 keywords and operators (`::`, `//`, `<<`, `>>`, `&`, `|`, `~`)
- Luau extensions (`continue`, `type`, `export type`, `typeof`, compound assign
  `+= -= *= /= //= %= ^= ..=`, `?`, `->`, `@attributes`, generics `f<T>`,
  union/intersection types, interpolated strings)
- Long strings and comments (`[[...]]`, `[=[...]=]`)
- Shebang lines (`#!/usr/bin/env lua`)
- Goto labels (`::label::`)
- Numeric and generic `for` loops
- `repeat...until`, `while...do`, `if...then...elseif...else...end`
- Nested block structures
- Single-line tables when they fit `maxLineLength` (default 100), trailing
  comma in multi-line tables, normalized quotes

## License

MIT
