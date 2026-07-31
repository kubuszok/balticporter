---
name: configure-port
description: Turn a directory of Java sources into a working Baltic Porter `.conf` — input roots and globs, the frontend classpath, the manifest (drops, renames, injections, surface phases), provenance, runtime mode, and the `base = …` dependent-module case. Use when starting a port of a library that has no configuration yet, or adding a second source set or module to one that does.
---

# Configuring a port

You have a checkout of a Java library — possibly shallow, possibly vendored — and you want Scala 3
out of it. This skill takes you from that to a `.conf` the engine accepts.

**Read first:** `CLAUDE.md` §1 (the three kinds of rule), §1.5 (a dependent inherits, never
restates), §5.5 (emitted code is a build product). The schema is documented in full on
`balticporter.runner.PortConfig` — it is `PortRun`'s parameters in the same words, and it is the
authority when this file and it disagree.

**Worked example:** `corpus/ports/simplegraphs/main.conf` (a base port) and `test.conf` (its
dependent). Read both before writing either; everything below is visible in them.

**Not covered here:** running the port and reading the numbers (`port-first-attempt`), reading the
issues it reports (`read-port-issues`), configuring a `(b)` phase's own keys or writing a `(c)` rule
(`customize-port`), instrumenting a run (`debug-port`). Adding a library to *this* repository's
corpus wraps all five (`add-corpus-library`).

## 0. Three doors — pick the file one unless you need Scala

| door | for |
|---|---|
| a Scala `main` calling `PortRun(...)` | full strength; needed for a predicate, a hand-built `OpaqueSpec`, or a non-trivial build |
| `PortConfig.load(conf, args)` from your own `main` | a `.conf` plus three lines — **the recommended shape** |
| `balticporter.runner.PortConfigMain <port.conf>` | nothing to write at all; fine for a one-off |

Prefer the middle one for anything you will measure twice. `CheckReport.dir` is derived from the
MAIN CLASS's simple name, so a per-port `main` is what keeps `port-report/<YourMain>` a stable
measurement identity; every port run through `PortConfigMain` reports into `port-report/PortConfigMain`,
and two ports sharing one report directory is two baselines overwriting each other.

```scala
object MyLibMigrate:
  def main(args: Array[String]): Unit =
    PortConfig.load(repoRoot.resolve("ports/mylib/main.conf"), args.toSeq).execute()
```

## 1. Where the file lives, and how its paths resolve

In this repository: `corpus/ports/<library>/{main,test}.conf`, beside the port's other inputs
(`corpus/<lib>-overrides/`) and **not** under `src/`. In your own repository, anywhere you like.

**Every path in a conf resolves against THE CONF FILE, lexically** — not the working directory, not
`balticporter.root`. That is what makes a port directory relocatable. `${balticporter.root}`
substitution against `-D` system properties remains for values an operator genuinely supplies.

## 2. `input` — what the frontend parses, and what it merely resolves against

```hocon
input {
  sourceRoot      = "../../../../sge/original-src/simple-graphs/src/main/java"   # required
  includeGlobs    = ["**.java"]                       # default
  excludeGlobs    = ["**/package-info.java", "package-info.java",
                     "**/module-info.java", "module-info.java"]   # default
  # files         = ["a/B.java"]                      # or state the list outright — never both
  classpathFile   = "../../../out/mylib-test-classpath.txt"
  # classpath     = ["…jar"]
  resolutionRoots = ["../../../../sge/original-src/simple-graphs/src/main/java"]
}
```

- **`include` is a HOCON KEYWORD.** `include = [...]` is a parse error from the config library, not
  from this engine, so the obvious spelling is unusable — hence `includeGlobs`/`excludeGlobs`.
- **`files` and the globs are exclusive.** Declaring both is refused: "there is no honest reading of
  which one the port meant". Either way the list is SORTED, because unit order is emission order and
  an unsorted directory walk is a different port on a different filesystem.
- **`resolutionRoots` are parsed and NOT emitted.** This is how a second module sees its base's
  types: it resolves against the base's *Java*, never against the Scala the base port emitted. A run
  whose resolution roots lie outside its own source root is structurally a dependent — see §6.

### The frontend classpath, and why a missing one is fatal

The realistic source is a dependency resolver. Write the one path-separator-joined line it produces
to a file and point `classpathFile` at it:

```
cs fetch --classpath junit:junit:4.12 > out/mylib-test-classpath.txt
```

**Resolve what the library DECLARES** (`build.gradle`, `pom.xml`), not a current version. Ashley's
suite needs Mockito 1.10.19 because `ComponentClassFactory` uses `org.mockito.asm`, removed in 2.0;
guessing a modern version cost a full measurement cycle.

A `classpathFile` that does not exist is **fatal**, never a degrade-to-empty:

```
port config: input.classpathFile: /…/out/does-not-exist-classpath.txt does not exist. A classpath
that silently resolves to nothing does not fail the frontend — it makes it resolve every
unresolvable reference WRONGLY, and the port then emits nonsense and reports success.
```

That is the whole reason. `import static org.junit.Assert.assertEquals` that the frontend cannot
resolve does not error — it resolves to an unqualified call on the enclosing test class and emits as
`this.assertEquals(...)`. Recorded three times now (libGDX's `org.junit.Assert.*`, Ashley's
`org.mockito.Mockito.*`, and simple-graphs would have been the third). A config file naming a
COMMAND to run is deliberately not supported: that would be a string that is secretly code.

## 3. `output` — and where the Scala lands

```hocon
output { portRoot = "../../../simplegraphs-core", sourceSet = "main" }   # main | test
```

Emitted Scala goes to `<portRoot>/src_managed/{main,test}/scala` — gitignored, deleted by
`sbt clean`, never `src/` (`CLAUDE.md` §5.5). `src/` holds only the hand-written part of a port.
Do not try to redirect this; the layout is the contract.

## 4. `manifest` — the port's POLICY, and the only required block

```hocon
manifest {
  name           = "simple-graphs"                              # required
  governs        = ["space.earlygrey.simplegraphs"]
  dropTypes      = []
  dropMethods    = []
  packageRenames { "space.earlygrey.simplegraphs" = "sge.graphs" }
  inject         = ["../../mylib-overrides"]
  surface = [
    { transform = "collections" },
    { transform = "mutable-params" },
  ]
}
```

- **`name`** is for reports. **`governs`** is a set of FQN prefixes this module CLAIMS, used only
  where a check genuinely needs prefixes (the package-rename comparison). Leave it empty when a
  module's packages interleave with another's — a library's test suite usually declares its suites
  in the very packages it tests, and no prefix separates those. Empty means "no claim", not
  "everything".
- **`dropTypes` / `dropMethods` are OBSERVATIONS ABOUT THE SHARED SURFACE.** "This type is not
  translated mechanically; something else supplies its FQN." They bind every module that sees the
  type and they ARE inherited by a dependent. `dropMethods` keys are `owner#name` or
  `owner#name(P1,P2)`.
- **`inject` is a BUILD ARTEFACT and is NOT inherited.** Exactly one module ships each replacement
  file; a dependent that copied the list would emit a second definition of the same FQN. This
  asymmetry between a drop and its replacement is the single thing about the manifest most worth
  getting right (`CLAUDE.md` §1.5).
- **`packageRenames` is DATA, not a transform.** It must run after every other phase — all of their
  policy is written in the UPSTREAM namespace — and `runsAfter` cannot say "after everything", so
  `PortRun` appends it last and verifies it. Writing it as a surface entry is refused BY NAME rather
  than falling through to "unknown transform", so you are never told a feature is missing when it is
  merely spelled differently:

  ```
  port config: manifest.surface[0].transform: `package-rename` is not a surface transform. …
    Write `manifest.packageRenames { "upstream.prefix" = "port.prefix" }` instead.
  ```

### `surface` — the phases that shape emitted SIGNATURES

Each entry is an object whose `transform` key is a **stable, kebab-case factory name** resolved
through `java.util.ServiceLoader`; the rest of the object is that transform's own configuration. An
unknown name lists every name your classpath actually offers:

```
port config: manifest.surface[0].transform: unknown transform 'collectionz'; discovered on this
classpath: class-table, collections, gdx-shared-iterator, globals-to-implicits, method-body,
mutable-params, panama-ffi, port-map-migration, primitive-to-opaque, static-forwarder,
test-framework, type-redirect.
```

Eleven of those are the engine's own §1(a)/§1(b) transforms; `gdx-shared-iterator` is the corpus's
own §1(c) rule, discovered because the corpus's build put it on the classpath. Per-transform keys
are enumerated in **`customize-port`**.

**Every retyping rule takes the same `scope { }` grammar** (`TransformFactory.scopeOf`), so a
third-party factory gets it for free:

```hocon
{ transform = "collections", scope { except = ["com.foo.Bridge"] } }   # Everywhere(except)
{ transform = "collections", scope { only   = ["com.foo.gl"] } }       # Only(include)
# scope absent                                                        # Everywhere() — the default
```

Both halves at once is REFUSED rather than resolved: there is no value that is both. An empty
`only = []` is honoured as written — "only these, and there are none" is a statement a port can make
on purpose.

The surface list IS inherited by a dependent, and the base's phases are placed BEFORE the
dependent's own.

## 5. The rest of the file

```hocon
provenance {                          # omitted ⇒ None, and then the port ships no attribution
  upstreamName     = "simple-graphs"  # required within the block
  originalLicense  = "MIT"            # required within the block
  sourcePathPrefix = "src/main/java"  # required within the block
  # upstreamCommit — omit: derived from the vendored tree's own git state, the value that cannot go stale
  # sourceRoot     — defaults to input.sourceRoot, absolute
}

runtimeMode = "vendored"      # dependency (default) | vendored
determinism = "emission"      # emission (default) | full | off   — a --determinism= flag beats this
supportSources { "com.foo.Shim" = "…" }
cache    = ".balticporter/cache"
lenient  = true               # default
preview  = false              # default; true makes the emitter DECLARE what it cannot render
nextStep = "just sg-measure"
```

- **`provenance` is a licence obligation** (`CLAUDE.md` §4.57), not decoration: every port is a
  derived work and every emitted file ships an attribution header. Nothing in the pipeline reports a
  missing one — the output compiles perfectly without it. State the licence the UPSTREAM file
  carries; where a reference hand-port disagrees, upstream is the authority (simple-graphs is the
  worked case: upstream `LICENSE` says MIT, the hand-port's headers say ISC, the conf states MIT and
  says why).
- **`runtimeMode`**: `vendored` ships the support types the phases retype onto BESIDE the emitted
  code, for a single source set compiled standalone. `dependency` (the default) takes them from
  `balticporter-runtime`. simple-graphs' main port is `vendored` and its TEST port is `dependency` —
  vendoring twice would define every support type twice, which the JVM tolerates only while the
  copies agree and the Scala.js/Native linkers reject.
- **`project { … }` is OPT-IN and defaults to omitted.** Present, it makes the run generate an sbt
  skeleton — `build.sbt`, `project/build.properties`, `.gitignore`, the engine pin. **Omit it.**
  Your build already exists, and it is yours: the run then writes the SOURCES and nothing else.
  Present it takes `moduleName`, `organization`, `scalaVersion`, `sbtVersion`, `deps`/`testDeps`
  (`org:artifact:version`, or `org::artifact:version` for Scala-cross), `testFramework`.

## 6. The second module — `base = "…"`, which IS `extendedBy`

```hocon
label = "simple-graphs-test"
base  = "main.conf"           # relative to THIS conf file
```

`base` reads that file's `manifest` and returns `thatManifest.extendedBy(mine)`. It is **not an
include**: everything under `input`, `output`, `provenance` and `runtimeMode` in the base is a fact
about the BASE's build and is ignored here.

| inherited — the SHARED SURFACE | not inherited — THIS module's build |
|---|---|
| `dropTypes`, `dropMethods`, `packageRenames`, `surface` | `sourceSet`, `frontend`/`input`, `provenance`, `runtimeMode`, `supportSources`, `project`, **`inject`** |

`PortRun` runs `ManifestAgreement` on every port and refuses:

- **a dependent that names no base at all** (`NoBaseDeclared`). Resolution roots outside your own
  source root make you a dependent. If a resolution root is genuinely NOT a ported module — a
  vendored third-party tree you resolve against and never emit — say so with an empty manifest for
  it. That is a statement, not a loophole;
- a drop, a rename or a signature-affecting phase present in one module and not the other;
- one phase name appearing twice in the effective pipeline with different policy.

What it cannot see, so do not trust it further: a parameterised phase's CONFIGURATION unless that
phase implements `SurfacePolicy`; nested-type drops; anything about the base's emitted output.

## 7. Any key nobody reads FAILS the run

HOCON accepts any document it can parse, so a misspelt key is a policy entry that silently does
nothing. This engine refuses it, naming the full path:

```
port config: /…/typo.conf: 1 key(s) nobody read: manifest.dropType. HOCON accepts any key it is
given, so a misspelt one is a policy entry that silently does nothing — the §1(b) no-op this engine
refuses everywhere else. Fix the spelling, or delete the key.
```

That refusal is the config path's answer to what the Scala path answers with `PolicyReport`. Note
the split it draws, which matters when you are hunting a rule that did not fire (`read-port-issues`):
a bad **key** is caught here, at load; a bad **value** — a `dropTypes` entry naming a type that does
not exist — reaches the run and is caught by the `policy` check.

**One hole to know about, because it is invisible from the dependent's side.** When a conf is loaded
as somebody's `base = "…"`, §1.5 says the dependent inherits the shared SURFACE and nothing else —
so the base file's own top-level keys (`input`, `output`, `provenance`, `runtimeMode`) are MARKED
READ without being read, and a typo among them is therefore not caught by that run. It is caught by
the run that loads the same file as its OWN configuration, which is the run it belongs to. So a
module that only ever runs its dependent's lane never checks its base conf's top level: **load every
conf as a port at least once** (§9), including one you wrote only to be extended.

## 8. What a conf deliberately cannot hold

No classname-in-a-string, no expression language, no reflective construction of a lambda. Three
engine phases take a `Symbol => Boolean`, and each is handled without inventing one:

- a universal default needs no policy (`panama-ffi`'s `isNative`);
- where the port must name things, config names them AS DATA and the factory closes over it
  (`globals-to-implicits` takes `holders`, whose whole policy — the holder, the context type, the
  field→path map, the attachment, the per-site boundaries — is names);
- where an arbitrary predicate is genuinely needed, config **refuses and names the escape hatch** —
  `primitive-to-opaque`'s `hints`, whose error tells you to list seeds in `extraHints` or to register
  a `TransformFactory` of your own.

That escape hatch, and writing a §1(c) rule behind it, is **`customize-port`**.

## 9. Before you measure anything, make the conf load

Run it once. Configuration errors are thrown before any port exists, so nothing is written and
nothing is reported — you get one line naming the key to edit.

```
sbt -client "corpus/runMain balticporter.runner.PortConfigMain <path/to/your.conf>"
```

Then go to **`port-first-attempt`**.
