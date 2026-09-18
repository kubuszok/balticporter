# Overview

Baltic Porter is a deterministic re-compiler: it turns a Java library into a typed, whole-program
Scala 3 tree, runs a fixed pipeline of rewrites over that tree, and emits Scala source from what
comes out. It is not a text transpiler with a pretty-printer bolted on, and it is not an LLM
translating file by file. The output has to be exactly reproducible from the same Java sources and
the same configuration — running the engine twice on unchanged input produces byte-identical Scala.

## Why a re-compiler

A text-level or per-unit translator cannot see across files, so it cannot safely rewrite a global
variable into a threaded parameter, retype a primitive into an opaque type, or change how a Java
collection is represented — all of these need to know, for the whole program, who reads and writes
the thing being changed. Baltic Porter instead behaves like a compiler: a frontend resolves the
whole program's types and symbols, a series of typed, symbol-driven transforms rewrite the resulting
tree, and only then does a backend print Scala text. Because every node in the tree already carries
its resolved type and symbol, emission never has to re-infer anything — it inserts the right form by
construction.

The alternative — ad hoc, per-file, best-guess translation — produces code that compiles but behaves
differently from the original at a rate large enough to be worse than useless for a library other
code depends on. Baltic Porter refuses that trade: every construct it cannot translate faithfully is
refused and counted rather than silently approximated (see
[unportable-constructs-and-refusals.md](unportable-constructs-and-refusals.md)).

## What the engine refuses to be

- **Not a program for porting one library.** libGDX is the library that drives most of the engine's
  design, because it surfaces more Java-vs-Scala friction per file than anything else in the corpus,
  but a rule is judged by whether it helps porting *any* Java library, not just this one. A change
  that makes the engine better at libGDX and worse at the next library is the wrong change.
- **Not a plugin-loading engine.** A rule is a `Phase` value passed to a run; nothing is instantiated
  from a class name written in a configuration file. A configuration file may *name* a phase that is
  already compiled onto the classpath, through `TransformFactory` and Java's `ServiceLoader`, but it
  cannot describe new behaviour on its own.
  - **Not an in-place refactoring tool.** It does not rewrite hand-written Scala, and it does not
    watch a project incrementally; a run is a batch job, backed by a cache keyed on content, not on
    file timestamps.
- **Not a silent best-effort translator.** Where no faithful Scala exists, the engine says so, in the
  tree and in its reports, rather than emitting something that merely compiles.

## The pipeline

1. **Frontend.** A frontend parses and type-resolves the source language and populates the engine's
   own intermediate representation (the TIR — see
   [intermediate-representation.md](intermediate-representation.md)). The production frontend is
   `frontend-spoon`, built on the Spoon Java library with full-classpath (never no-classpath)
   resolution, because later phases need real overload resolution, boxing information, and resolved
   static types — a home-grown resolver or a syntax-only parser cannot supply these.
2. **Typed intermediate representation.** The TIR is a Scala-shaped, whole-program, typed tree:
   interned symbols with stable identity, a structured type algebra, typed tree nodes, and a
   whole-program index for querying uses, definitions and callers. Transforms and the emitter read
   types and symbols off this tree; nothing re-infers them.
3. **Ordered transform phases.** A fixed, declaration-ordered pipeline of `Phase` values rewrites the
   tree — see [phases-and-policy.md](phases-and-policy.md) for how a phase declares what it changes
   and how its policy is configured per library.
4. **Checks.** Every translation path that can leave a residue (an unhandled construct, a boundary a
   retyping rule could not cross, a member two modules disagree about) is checked at the same time it
   is introduced, and the result is recorded — never inferred by re-reading emitted files by hand.
5. **Emitter.** A backend walks the transformed, typed tree and prints Scala 3 source text. Because
   every node already carries a resolved type and symbol, the emitter's job is mechanical rendering,
   not decision-making. Reference emission is fully qualified with no imports, which removes the
   whole class of import-decision bugs; a human-readable-imports backend is a separate, optional
   beautification step, never a correctness requirement.
6. **Run reports.** A run publishes a set of machine-readable artifacts describing what it did: which
   declarations were dropped, renamed or substituted, which constructs could not be translated, which
   comments were or were not preserved, and a decision log recording every non-mechanical choice (see
   [provenance-and-licensing.md](provenance-and-licensing.md)).

## Modules and what may depend on what

```
balticporter/
  api/             the model and contracts a rule author compiles against
  frontend-spoon/  the only module that sees Spoon's own types
  frontend-ts/     experimental frontend for a second source language
  frontend-dart/   experimental frontend for a third source language
  engine/          transform implementations, checks, the emitter, vocabulary tables, PortRun
  runtime/         shims a port depends on at runtime, cross-built for JVM, Scala.js and Native
  testkit/         a golden-test harness for rule authors
  corpus/          the framework's own acceptance ports
```

Dependencies run one way: `api` depends on nothing. `frontend-spoon`, `frontend-ts` and
`frontend-dart` each depend on `api` alone (`frontend-dart` also depends on `frontend-ts`), so a
frontend module never leaks its own parser's types into the rest of the engine — that insulation is
what lets a second or third frontend be added beside Spoon without touching anything downstream.
`engine` depends on `api` and `frontend-spoon`; a port program built with `PortRun` currently needs
the Spoon frontend to model a source set. `testkit` and `corpus` depend on `engine`; nothing depends
on `corpus`.

`api` holds everything a library-specific rule needs and nothing more: the TIR itself (trees,
symbols, types, provenance), the `Phase`/pipeline machinery, the decision model, the reporting
surfaces, and the scope and flow-propagation helpers a retyping rule builds on. A rule and its test
suite should compile against `api` alone — no emitter, no orchestrator, no Spoon dependency. Every
transform *implementation*, the emitter, the port-map machinery, the run cache and `PortRun` itself
live in `engine`.

## Shared modules never name a library

No file under `api`, `engine`, `frontend-spoon` or `runtime` — including test sources — may mention a
ported library or one of its dependencies by name in code. Doc comments may mention one for context,
but nothing in those comments may drive behaviour. A fact that is true of every Java-to-Scala port
belongs in these modules, unparameterised. A fact that depends on which library is being ported
belongs in that library's own configuration — either as a parameter to an existing, reusable
mechanism, or, only once a mechanism genuinely cannot be shared, as a separate rule the porting
program plugs in. See [phases-and-policy.md](phases-and-policy.md) for how that choice is made.

## The port as a value

A port is not a script; it is configuration. A `PortManifest` value states what a module does —
which types and members it drops, which packages it renames, which transform phases run with what
policy, which platforms it targets, which dependency coordinates it declares — and everything
mechanical (emission, running the declared phases in order, writing `src_managed/`, running every
required check) is supplied by `PortRun` and cannot be opted out of. This split is what lets a
dependent module reuse a base module's policy instead of restating it; see
[port-map-and-dependent-modules.md](port-map-and-dependent-modules.md).
