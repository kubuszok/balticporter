# Multi-module ports

A library is rarely one source tree. Its test suite is a second module resolved against the same
Java; a Gradle multi-module build may spread one package root across several jars; an extension
library builds on top of a core one. This page is about how Baltic Porter keeps those modules
consistent with each other.

## A dependent inherits, it never restates

A second module that resolves against the first's Java (never against the Scala the first module
emitted) is a **dependent**. In a `.conf`, this is `base = "…"`:

```hocon
label = "mylib-test"
base  = "main.conf"
```

`base` is **not an include**. It loads only the base file's `manifest` block and computes
`baseManifest.extendedBy(thisManifest)` — everything under the base's `input`, `output`,
`provenance` and `runtimeMode` is a fact about *that* module's own build and plays no role here. See
[Configuring a port](configuring-a-port.md) for the full conf format.

| inherited from the base | not inherited — this module's own build |
|---|---|
| `dropTypes`, `dropMethods`, `packageRenames`, `typeRenames`, `subPackages`, `flattenNestedTypes`, `allowPackageSplit`, `surface`, per-location remedy selections | `sourceSet`, `input`, `provenance`, `runtimeMode`, `supportSources`, `project` |
| | `inject`, `platformDirs`, `targets`, `dependencies`, `externalParenless`, `parity` |

The two columns follow one rule: what is inherited is a fact about **the shared surface** — the
Java types and members both modules see, and what the first module already did to them. What is not
inherited is a fact about **this module's own build** — where its files live, what Scala version it
compiles with, which artefacts it ships.

### Why `inject` is the one surprising exception

A type dropped in the base manifest binds *every* module that resolves against it — a dependent must
still model it as substituted, or its references will not resolve. But the *replacement Scala* for
that type is a build artefact: exactly one module in the chain should ship the file that supplies the
dropped type's fully-qualified name. If a dependent also inherited `inject`, it would define the same
type a second time. This asymmetry between a drop (shared) and its injection (owned by one module) is
the single thing about a dependent conf most worth getting right.

### `targets` only narrows

A dependent may declare fewer target platforms than its base, never more — a base built for the JVM
only cannot be the foundation of a dependent that also wants Scala.js. Widening is refused; if a
dependent genuinely needs a platform its base does not support, the base is where that has to change.

## What a base publishes, and what a dependent reads

Each run writes `port-map.tsv` into its report directory — a line per type and member the run
produced, whether it was ported at the same name, renamed, replaced by an injected file, or dropped
outright. Its header names the module and the schema:

```
# balticporter port map	schema=4	module=mylib-core	engine=…
```

Every row carries the upstream name and the emitted name, and one of five dispositions:

| disposition | what it means |
|---|---|
| `Ported` | translated mechanically, at the same fully-qualified name |
| `Renamed` | translated, but emitted at a different name (a package or type rename) |
| `Substituted` | not translated; an injected file replaces it at the same name — a caller sees the same name and a different implementation underneath |
| `Dropped` | not emitted and not replaced. Every reference to it must have been rewritten away by something else in the pipeline, which is exactly what a dependent naming this base gets checked against |
| `Added` | present in the port but absent upstream — an injected type, a runtime support type, or a member a library-specific rule introduced |

A dependent does not need to open this file by hand: the `port-map-migration` phase reads it and
resolves the dependent's own references against it, and the `port-map` check (see
[Reading the report](reading-the-report.md)) tells you when a reference does not resolve against
anything the base's map says it produced. Reading the map directly is useful mainly when a `port-map`
finding is confusing and you want to see exactly what the base module believes it shipped.

A dependent's conf enables the `port-map-migration` phase explicitly, in its own `surface` list, and
names the base modules whose maps it should read:

```hocon
{ transform = "port-map-migration", bases = ["mylib-core"] }
```

The dependent joins its own references against that map to decide what a base-declared reference
resolves to — a rename the base performed, a member the base's injected file actually supplies. A
dependent whose resolution roots point at files the base's map does not cover produces a `port-map`
finding rather than a silent wrong answer: see [Reading the report](reading-the-report.md).

## Where a mismatch is reported

Every run checks its own manifest against its declared base chain. The check that watches this is
`manifest`, and it is fatal in several shapes:

- a type or member the base does not translate mechanically, but this module does not declare
  dropped either — a **missing drop**;
- this module drops something the base's own output actually emits — an **extra drop**;
- the two modules rename a shared namespace, or a shared type, to two different destinations;
- the same phase name appears in both modules' effective pipelines with different policy;
- a dependent that resolves against files outside its own source root but names no `base` at all.

Each of these means the two modules, taken together, do not describe one consistent piece of Scala —
one compiles against a name the other never produces. The check exists so that discovering it is a
build-time finding rather than a runtime `NoSuchMethodError` in whichever module compiles second.

!!! note "An empty manifest is a legitimate answer"

    A resolution root that is not itself a ported module — a vendored third-party tree you resolve
    against but never emit anything for — is declared as a `base` with an empty manifest. That states
    "this root is not a port" rather than leaving the question unanswered.

## Two source sets of one destination

The most common multi-module shape is a library's own test suite: `main.conf` ports the library,
`test.conf` is a dependent at the same `portRoot` with `sourceSet = "test"` instead of `"main"`. Both
resolve against the same upstream tree; the second is a dependent purely because its
`resolutionRoots` reach back into the first's source root. Trimmed from the corpus:

```hocon
# main.conf
label = "mylib"
input  { sourceRoot = "upstream/src/main/java" }
output { portRoot = "target/port", sourceSet = "main" }
manifest {
  name = "mylib"
  packageRenames { "com.example.mylib" = "mylib" }
  surface = [ { transform = "collections" } ]
}
runtimeMode = "vendored"
```

```hocon
# test.conf — a dependent of main.conf
label = "mylib-test"
base  = "main.conf"

input {
  sourceRoot      = "upstream/src/test/java"
  classpathFile   = "target/mylib-test-classpath.txt"
  resolutionRoots = ["upstream/src/main/java"]
}

output { portRoot = "target/port", sourceSet = "test" }

manifest {
  name    = "mylib-test"
  surface = [ { transform = "test-framework" } ]
}

runtimeMode = "dependency"   # the main source set already carries the vendored shims
```

`test.conf` inherits `packageRenames` and the `collections` phase from `main.conf` through `base` —
it never restates either — and adds only what belongs to it alone: its own source root, its own
frontend classpath (JUnit has to be resolvable for the test sources to parse at all), and the
`test-framework` phase that turns its `@Test` methods into MUnit tests. `runtimeMode` is
`"dependency"` here rather than `"vendored"`, precisely because vendoring the same support types a
second time would define them twice in one build.

A destination's identity is the pair *(port root, source set)* — a run wipes its whole output
directory unconditionally before writing, so two confs that share both values will each overwrite the
other's work. An upstream library spread across several build modules that all belong under one
destination package is one conf with a wider glob, not one conf per upstream module; a module that
genuinely needs a third, disjoint output tree earns its own port root instead.

## What a dependent still has to decide for itself

Inheriting the shared surface is not inheriting everything. A dependent still states, on its own:

- its own `input` (source root, classpath, resolution roots and excludes) — a dependent's
  `resolutionRoots` typically point back at the base's source tree, which is what makes it a
  dependent rather than an unrelated port in the first place;
- its own `provenance` (the licence header a *test* source set carries can differ from the header its
  library's main sources carry) and `runtimeMode` (a dependent usually takes `dependency` rather than
  `vendored`, since the base source set already carries the vendored support types, and vendoring
  twice defines every support type a second time);
- its own `inject`/`platformDirs`, `dependencies`, and `targets` (narrowed, never widened).

See [Configuring a port](configuring-a-port.md) for the syntax of each, and
[Reading the report](reading-the-report.md) for what the `manifest` and `port-map` checks look like
in a run's output when a dependent and its base disagree.
