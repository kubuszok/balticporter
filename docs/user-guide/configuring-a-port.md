# Configuring a port

A port is described by a HOCON `.conf` file. This page documents the format in full, verified
against `balticporter.runner.PortConfig` (the loader) and `balticporter.core.PortManifest` (the
value it builds) — those two are the authority if this page and the engine ever disagree.

Every path in a conf resolves against **the conf file itself**, not the working directory, so a
port directory can be moved freely. The two worked examples below are trimmed from the corpus:
`simplegraphs/main.conf`, `simplegraphs/test.conf` and `noise4j/main.conf`.

## The three ways to run one

| way | for |
|---|---|
| a Scala `main` calling `PortConfig.load(conf, args).execute()` | **recommended** for anything you will run more than once |
| `balticporter.runner.PortConfigMain <port.conf>` | nothing to write at all — fine for a one-off |
| a hand-written `PortRun(...)` | full strength: a predicate, a computed file list, several source sets driven from one program |

Prefer a small per-port `main` over `PortConfigMain` for anything you measure twice: the report
directory a run writes into is named after the *main class*, so giving every port its own keeps
their reports from overwriting each other (see [Reading the report](reading-the-report.md)).

```scala
object MyLibMigrate:
  def main(args: Array[String]): Unit =
    PortConfig.load(repoRoot.resolve("ports/mylib/main.conf"), args.toSeq).execute()
```

## `label`, `input`, `output` — the required top level

```hocon
label = "mylib"

input {
  sourceRoot    = "upstream/src/main/java"
  classpathFile = "target/mylib-classpath.txt"
}

output {
  portRoot  = "target/port"
  sourceSet = "main"
}
```

`label`, `input` and `output` are required at the top level; `manifest` (below) is required too.
Everything else is optional.

### `input` — what the frontend parses, and what it merely resolves against

```hocon
input {
  sourceRoot         = "upstream/src/main/java"     # required
  includeGlobs       = ["**.java"]                  # default
  excludeGlobs       = ["**/package-info.java", "package-info.java",
                         "**/module-info.java", "module-info.java"]   # default
  # files            = ["a/B.java"]                 # or state the list outright — never both
  classpathFile      = "target/mylib-test-classpath.txt"
  # classpath        = ["/path/to/some.jar"]
  resolutionRoots    = ["upstream/src/main/java"]
  resolutionExcludes = ["com/example/emu"]          # paths under a root not to parse
}
```

- **`sourceRoot`** is required. Every file under it is a candidate; `includeGlobs`/`excludeGlobs`
  narrow the walk, or `files` names the exact list. `include` on its own is not a usable key — it is
  a keyword in HOCON itself, so the engine spells these `includeGlobs`/`excludeGlobs`.
- **`files` and the globs are mutually exclusive.** Declaring both is refused, because there is no
  honest reading of which one the port meant. Either way the resulting list is sorted: unit order is
  emission order, and an unsorted directory walk would make a port depend on the filesystem.
- **`classpathFile`** points at one path-separator-joined line — the frontend's own classpath, i.e.
  the upstream library's dependencies, not yours. Produce it with a dependency resolver, resolving
  what the library's build actually *declares* rather than a current version:

  ```
  cs fetch --classpath junit:junit:4.12 > target/mylib-test-classpath.txt
  ```

  A `classpathFile` that does not exist is **fatal** — never a silent empty classpath. An
  unresolvable reference does not fail parsing; it resolves *wrongly*, as an unqualified call on the
  enclosing class, and the port then emits nonsense and reports success. `classpath` lists jars
  directly when you already have the paths and no file to read them from.
- **`resolutionRoots`** are parsed and resolved against, but never emitted. This is how a dependent
  module sees its base's types — against the base's own Java, never against the Scala the base port
  emitted (see [Multi-module ports](multi-module-ports.md)). A run whose resolution roots lie outside
  its own source root is structurally a dependent.
- **`resolutionExcludes`** are paths *under a resolution root* to skip, relative to that root — for a
  subtree that redeclares the same fully-qualified names the main tree does (a GWT `super-source`
  directory is the shape that hits this first). Do not narrow the root itself instead: a base's
  published surface is joined to a dependent through the whole resolution root, and narrowing it
  costs findings a dependent has no way to explain.

### `output` — and where the Scala lands

```hocon
output { portRoot = "target/port", sourceSet = "main" }   # main | test
```

Emitted Scala always lands at `<portRoot>/src_managed/{main,test}/scala` — gitignored, deleted by
`sbt clean`, never under `src/`. There is no way to redirect this; the layout is the contract (see
[Getting started](getting-started.md)). `src/` is where the hand-written part of a port lives — the
files a library needs and the engine cannot derive, listed under `manifest.inject` below.

### `roots` — a configuration that can be run from somewhere else

Every path in a `.conf` is relative to the file. That stops working the moment the file is not
where you wrote it — for instance when a build takes it out of a published jar. Name the
directories the port depends on, give each a default, and write paths under them as `@name/…`:

```hocon
roots {
  upstream = "../my-lib"      # the Java checkout
  work     = "target/port"    # output and scratch
}
input  { sourceRoot = "@upstream/src/main/java" }
output { portRoot   = "@work", sourceSet = "main" }
```

Whoever runs the configuration says where the roots are on their disk:

```scala
PortConfig.load(conf, roots = Map("upstream" -> upstreamDir, "work" -> targetDir)).execute()
```

An override for a root the file does not declare, and a path under a root nobody declared, are both
refused by name — a misspelt root would otherwise silently do nothing. A `base = "…"` configuration
is resolved with its own declarations and the same overrides.

Name the port for its **destination**, never for the upstream library: the directory, the top-level
`label` and `manifest.name` should all agree on the identifier the emitted Scala is going to become
in your build (`mylib-core`, not `my-lib-1.2`).

## `manifest` — the port's policy

This is the only other required block, and the one that carries almost everything specific to the
library.

```hocon
manifest {
  name           = "mylib"                # required
  governs        = ["com.example.mylib"]
  dropTypes      = []
  dropMethods    = []
  packageRenames { "com.example.mylib" = "mylib" }
  typeRenames    { "com.example.mylib.Utils$Helper" = "Helper" }
  inject         = ["overrides/mylib"]
  platformDirs   = { jvm = ["overrides/mylib-jvm"] }
  surface = [
    { transform = "collections" },
    { transform = "mutable-params" },
  ]
}
```

- **`name`** is for reports and is what a dependent module matches its `base` chain against.
- **`governs`** is a set of fully-qualified-name prefixes this module *claims* — used only where a
  check genuinely needs one (comparing package renames). Leave it empty when your packages interleave
  with another module's (a library's own test suite usually declares its tests in the very packages
  it tests). An empty set means "no claim", not "everything".
- **`dropTypes`** / **`dropMethods`** state that a type or method is *not translated mechanically* —
  something else supplies its fully-qualified name. `dropMethods` keys are `owner#name` or
  `owner#name(P1,P2)` for an overloaded member. Every emitted reference to a dropped type must be
  rewritten away or replaced; see [Reading the report](reading-the-report.md) for what happens when
  one is not.
- **`packageRenames`** is upstream-prefix → port-prefix, and **`typeRenames`** is upstream *type*
  fully-qualified name → its new name in the port, either a bare simple name (rename in place) or a
  whole new fully-qualified name. Both are data, not a phase: the rename always runs *after* every
  other phase, because every other phase's policy is written in the upstream namespace. Writing
  `{ transform = "package-rename" }` in `surface` is refused by name, with a message pointing back
  here.
- **`subPackages`** nests a type under a sub-package in place (upstream type FQN → dot-separated
  segments); **`flattenNestedTypes`** promotes a nested type (`p.Outer$Inner`) to top level. A type
  that needs both — promoted *and* placed under a sub-package — is one destination: name it in both
  keys (`Files$FileType` → `files.FileType`).
- **`allowPackageSplit`** declares, for a type whose per-type move crosses an access boundary Java
  gave it, that the move is deliberate.
- **`inject`** is a list of directories or files of ready-made Scala this module ships — typically the
  replacement for something in `dropTypes`. It is a **build artefact**, not shared policy: exactly one
  module in a dependent chain should ship each replacement file (see
  [Multi-module ports](multi-module-ports.md)).
- **`platformDirs`** is the per-platform-row form of `inject` — `jvm` / `js` / `native` (the row names
  `sbt-projectmatrix` uses) → directories copied to `src_managed/<row>/scala`, compiled only by that
  row. Use it for a hand port's platform-specific layer (a JVM-only desktop backend, a Native-only
  buffer implementation), and for the case where one operation's **answer** differs per platform:

  ```hocon
  platformDirs {
    jvm    = ["overrides/mylib-jvm"]        # the platform can answer, so it answers
    js     = ["overrides/mylib-no-reflect"] # two rows, one refusal, one directory
    native = ["overrides/mylib-no-reflect"]
  }
  ```

  Keep **one call site** in the shared `inject` row and let it call a small object each row ships
  under the same fully-qualified name — the shared code then reads the same on every platform and
  only the answer moves. A row that cannot answer raises by name rather than returning something
  empty — an empty answer is a program that silently does nothing.

  The keys are **row names, not target names**: a row is a directory some build compiles, so `jvm`,
  `js` and `native` are the three accepted, and anything else (`scalajvm`, `scala-js`) is refused at
  load with the list of the ones that exist. Two rows may name the same directory. Absent, or `{}`,
  is the no-op. Like `inject`, it is a **build artefact and is not inherited** — a dependent that
  inherited it would emit the same fully-qualified name twice.
- **`resources`** copies classpath resources verbatim, at the upstream paths the emitted code already
  names:

  ```hocon
  resources = [ { root = "upstream/src/main/resources", files = ["mylib/data.json"] } ]
  ```

  They land in `<port>/src_managed/main/resources`, and a build that takes the emitted Scala without
  that directory compiles green and throws at the first lookup — often from a static initialiser, so
  the message is `Could not initialize class <Owner>$` and names nothing about a resource. In a
  consuming build, wire it as a task that generates first and then lists the files
  (`SbtGen.resourceFiles(portRoot, "main")`), plus `Compile / managedResourceDirectories +=` that
  directory so each file keeps its package path. Never a directory test inside a setting: settings
  are evaluated while the build loads, before the port has ever run.

- **`dependencies`** declares artifacts the emitted code needs on its classpath:

  ```hocon
  dependencies = [ { org = "org.scala-lang.modules", name = "scala-java-time", rev = "2.6.0", cross = "scala" } ]
  ```

  `cross` is `scala` (default), `java`, or `platform`.

### `surface` — the phases that shape emitted signatures

Each entry is `{ transform = "<stable-kebab-name>", …that transform's own keys… }`. The name is
resolved through `java.util.ServiceLoader`; giving an unknown one prints every name actually on your
classpath. Every key a phase does not read fails the run — a typo is loud, never a silent no-op. The
full list of phases and their keys is in
[Customizing the translation](customizing-translation.md).

Every retyping phase accepts the same `scope` grammar:

```hocon
{ transform = "collections", scope { except = ["com.foo.Bridge"] } }   # everywhere except these
{ transform = "collections", scope { only   = ["com.foo.gl"] } }       # only these
# scope absent                                                        # everywhere — the default
```

Naming both `except` and `only` at once is refused — there is no value that is both. An empty
`only = []` is honoured as written: "only these, and there are none."

## `provenance`, `runtimeMode`, and the rest

```hocon
provenance {
  upstreamName     = "mylib"       # required within the block
  originalLicense  = "MIT"         # required within the block
  sourcePathPrefix = "src/main/java"  # required within the block
  # upstreamCommit  — omit: derived from the vendored tree's own git state
  # sourceRoot      — defaults to input.sourceRoot
}

runtimeMode = "vendored"    # dependency (default) | vendored
determinism = "emission"    # emission (default) | full | off
lenient     = true          # default
preview     = false         # default
```

- **`provenance`** is a licence obligation, not decoration: every port is a derived work, and every
  emitted file carries a header naming the upstream project, commit and licence. Omitting the block
  means the port ships *no* attribution — nothing else in the pipeline will tell you that. State the
  licence the upstream file actually carries; where a hand port disagrees, upstream is the authority.
- **`runtimeMode`**: `vendored` copies the small support types a phase like `collections` retypes onto
  *beside* the emitted code, so a single source set compiles standalone. `dependency` (the default)
  takes them from `balticporter-runtime` instead. Vendor a `main` port and take `test` as
  `dependency`, never vendor both — the JVM tolerates two identical copies of a support type, but the
  Scala.js and Scala Native linkers reject it.
- **`determinism`**: `emission` (default) emits twice from the same in-memory model and byte-compares;
  `full` reparses and retranslates from scratch and compares that; `off` skips the check. A
  `--determinism=full`/`--determinism=off` argument on `PortConfig.load`'s `args` overrides the file.
- **`lenient`** (default `true`) and **`preview`** (default `false`, makes the emitter *declare* —
  with a `scala.compiletime.error` — what it has no faithful Scala for, instead of silently omitting
  it) are per-run tuning; leave them at their defaults unless a specific failure tells you otherwise.
- **`supportSources`** and **`cache`** are advanced knobs (a hand-supplied support type by fully
  qualified name, and a cache directory); most ports do not need either.

## The dependent case — `base = "…"`

```hocon
label = "mylib-test"
base  = "main.conf"     # relative to this conf file
```

`base` loads that file's `manifest` and computes `thatManifest.extendedBy(thisManifest)` — it is
**not an include**. Everything under `input`, `output`, `provenance` and `runtimeMode` in the base
file is a fact about *that* module's own build and is ignored here. See
[Multi-module ports](multi-module-ports.md) for what is inherited and what is not, and for what the
engine refuses when the two disagree.

The corpus's own worked example, trimmed from `simplegraphs/test.conf` — the library's JUnit suite,
ported as a dependent of `simplegraphs/main.conf`:

```hocon
label = "sge-graphs-test"
base  = "main.conf"

input {
  sourceRoot      = "…/simple-graphs/src/test/java"
  classpathFile   = "…/simplegraphs-test-classpath.txt"
  resolutionRoots = ["…/simple-graphs/src/main/java"]
}

output { portRoot = "…/ported/sge-graphs", sourceSet = "test" }

manifest {
  name    = "sge-graphs-test"
  surface = [ { transform = "test-framework" } ]
}

# the main source set already carries the vendored shims
runtimeMode = "dependency"
```

## Any key nobody reads fails the run

HOCON accepts any document it can parse, so a misspelt key would otherwise be a policy entry that
silently does nothing. This engine refuses to start instead, naming the full path:

```
port config: /…/typo.conf: 1 key(s) nobody read: manifest.dropType. HOCON accepts any key it is
given, so a misspelt one is a policy entry that silently does nothing. Fix the spelling, or delete
the key.
```

That check is separate from what happens once the run starts: a bad **key** is caught here, at load
time; a bad **value** — a `dropTypes` entry naming a type that turns out not to exist — reaches the
run and is reported by a check instead (see [Reading the report](reading-the-report.md)).

One thing to know about a conf loaded only as somebody's `base`: only its `manifest` is read in that
role, so a typo among its own `input`/`output`/`provenance` keys is *not* caught by the dependent's
run. It is caught the next time that file is loaded as a port in its own right — load every conf you
write at least once on its own, including one you wrote only to be extended.

## Before you measure anything

Load the conf once and see that it does not throw. Configuration errors surface before any port
exists, so nothing is written and nothing reported yet — you get one line naming the key to fix.
Once it loads cleanly, move on to running it and reading what it produced: see
[Getting started](getting-started.md) and [Reading the report](reading-the-report.md).
