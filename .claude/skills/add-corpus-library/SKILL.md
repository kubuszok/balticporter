---
name: add-corpus-library
description: Add a new Java library to the Baltic Porter corpus — port it, make it compile, run its migrated tests, and classify every specialisation it needed as universal / parameterised / library-specific. Use when asked to bring a new library into the corpus or to widen the port to another module.
---

# Adding a library to the corpus

Until Baltic Porter is published and each library gets its own porter repository, new libraries are
added to the **corpus** (`balticporter/corpus/`, one package per library). Read `CLAUDE.md` §1 before starting — the classification
it defines is the point of the exercise, not a formality.

Each library added should move engine rules **from (c) library-specific → (b) parameterised → (a)
universal**. A library that lands with a pile of new (c) rules has been ported but has not improved
the framework.

**This skill is the corpus-specific spine.** The steps it wraps are each their own skill, written for
an agent doing the same work in another repository, and they carry the detail this file only points
at:

| step | skill |
|---|---|
| write the `.conf` — inputs, classpath, manifest, `base = …` | **`configure-port`** |
| run it and read the numbers honestly | **`port-first-attempt`** |
| read `errors.tsv`, `findings.tsv`, `decisions.tsv`, never-fired policy | **`read-port-issues`** |
| configure a (b) phase, or write and test a (c) rule | **`customize-port`** |
| flags, kill switches, `debug-emit`, `correlate`, blast radius | **`debug-port`** |

## 1. Scope it before porting it

Survey the module tree and write down, per module, the file count and whether it is in scope.
Platform backends, authoring tools and demo apps are usually **not** — sge targets Scala Native and
Scala.js, so a GWT or Android backend is dead weight. Say so explicitly rather than silently
skipping it.

Find the **tests** early and count `@Test` methods and assertions. That number is the only
behavioural evidence the port will ever have, and it decides whether the library is worth adding.

## 2. Write the migration program — a `PortRun` CONFIGURATION, never a copied file

### 2.0 CONF-FIRST: start here unless the library needs a §1(c) rule on day one

Most of what a migration program contains is declarative, so **write a `.conf` and a three-line
`main`** and only reach for a hand-written `PortRun(...)` when something genuinely resists it.
simple-graphs is the worked example — read `balticporter/corpus/ports/simplegraphs/{main,test}.conf` beside
`SimpleGraphsMigrate.scala` before writing either.

```
balticporter/corpus/ports/<lib>/main.conf     # the port
balticporter/corpus/ports/<lib>/test.conf     # its suite: `base = "main.conf"` — that IS `extendedBy` (§1.5)
balticporter/corpus/src/main/scala/balticporter/corpus/<lib>/<Lib>Migrate.scala
```

```scala
object <Lib>Migrate:
  def main(args: Array[String]): Unit =
    PortConfig.load(<Lib>Port.conf("main.conf"), args.toSeq).execute()
```

The `main` still exists, and not as ceremony: `CheckReport.dir` is derived from the MAIN CLASS's
simple name, so a per-port `main` is what keeps `port-report/<Lib>Migrate` a stable measurement
baseline. (`balticporter.runner.PortConfigMain <conf>` runs any conf without one — fine for a
one-off, wrong for a port you will measure twice.)

The schema is documented in full on `balticporter.runner.PortConfig`; it is `PortRun`'s parameters
in the same words. Four things that bite:

- **paths resolve against the CONF FILE**, lexically. Not the working directory, not
  `balticporter.root`.
- **`include` is a HOCON keyword** — file selection is `includeGlobs` / `excludeGlobs`. The default
  is every `.java` minus `package-info`/`module-info`, sorted, which is what every hand-written
  migrator did by hand.
- **any key nobody reads FAILS the run**, naming its full path. HOCON accepts `dropType` for
  `dropTypes` silently; this engine does not.
- **`package-rename` is not a transform.** Write `manifest.packageRenames`; §4.56.

**When to write Scala instead.** A conf holds names and sets; it cannot hold a predicate or a rule.
If the port needs `OpaqueSpec.hints`, a custom `Symbol => Boolean`, or a §1(c) rule, you have two
options and both are fine: write the `PortRun(...)` by hand, or ship a `TransformFactory` (§2.1) and
keep the conf. Do NOT invent a way to say it in strings — see DESIGN.md §5.7.

### 2.01 …or the Scala program, when the port needs one

One `object <Lib>Migrate` in `balticporter/corpus/src/main/scala/balticporter/corpus/<lib>/`, in package
`balticporter.corpus.<lib>`. It is a **single
`PortRun(...)` value plus this library's policy, and nothing else**. Do NOT copy the body of
another migration program: everything mechanical — emission, the dropped-type skip, the injection
copy, the support-source write-out, every check, the substitution checks, determinism, provenance,
the `src_managed` paths, the runtime dependency — is `balticporter.runner.PortRun`'s and cannot be
opted out of. That is deliberate: check invocation used to be copy-paste, and `LibgdxTestMigrate`
went its whole life without ever calling `PortabilityCheck` as a result.

```scala
PortRun(
  label      = "<lib>",
  portRoot   = repoRoot.resolve("<lib>"),        // src_managed/{main,test}/scala hangs off this
  sourceSet  = SourceSet.Main,                   // Test for the suite; same mechanics either way
  frontend   = FrontendConfig(base, files, Nil), // resolutionRoots = roots resolved but NOT emitted
  phases     = Nil,                              // supplied by the manifest; the two are exclusive
  manifest   = Some(PortManifest(               // this module's SHARED-SURFACE policy, as a value
    name        = "<lib>",
    governs     = Set("com.upstream"),           // the namespace this module claims
    dropTypes   = …, dropMethods = …,            // what is NOT translated mechanically
    inject      = List(overridesDir),            // the replacements THIS module ships
    packageRenames = Map("com.upstream" -> "org.you"),  // NOT a phase: PortRun runs it LAST (§4.56)
    surface     = List(/* universal, then (b) configured, then your (c) rules */),
  )),
  provenance = Some(Provenance(name, commit, license, prefix, sourceRoot = base.toString)),
  runtimeMode    = RuntimeMode.Dependency,            // Vendored only for a standalone single set
  supportSources = Map.empty,                         // sources a phase needs but cannot declare
  determinism    = Determinism.fromArgs(args.toSeq),
  project        = None,                              // see below — generation is OPT-IN
).execute()
```

**`project` is OPTIONAL and defaults to `None`, which is what a corpus port uses.** `Some(spec)`
makes the run generate a whole sbt skeleton for the port — `build.sbt`, `project/build.properties`,
`.gitignore` and the engine pin — and is right only when the port's build does not exist yet. Every
port in this repository lives in a module `build.sbt` already declares, and so does the port an
agent in another repository is adding to its own build (§4.45): pass `None` and the run writes the
SOURCES and nothing else. `PortRunProjectSpec` asserts the exact file set in both directions.

A single-module port may still pass `phases` / `subs` / `packageRenames` directly instead of a
manifest, and `PortRun` refuses both at once. Prefer the manifest from the start: the moment a
SECOND module appears it is the only thing that keeps the two agreeing, and retrofitting it means
re-deciding every policy question you already answered.

Things that are now errors rather than omissions:

- passing a `PackageRenameTransform` in `phases` — it has an ordering obligation `runsAfter` cannot
  state, so `PortRun` appends it and verifies it;
- forgetting `externalConcrete` — it is derived from the phases via `RuntimePlan`, and a caller
  cannot supply it;
- a check going unrun — `PortRun.RequiredChecks` is asserted against what actually recorded, so a
  number that reaches stdout and not `findings.tsv` fails the run.

**What a new port gets for free, and should not rebuild.** Every `PortRun` writes, into
`port-report/<MainClass>/run-latest/`: `findings.tsv` + `counts.tsv` + `report.md` + `diff.txt`
(fifteen checks, sorted, path-relative, diffed against a promotable `baseline/`); `srcmap.tsv`
(member → emitted lines → Java origin) and `members.tsv` (one digest per member — the blast radius,
available before any compile); `decisions.tsv`, one row per declaration whose emitted form a
non-mechanical decision changed, each carrying its `CLAUDE.md` §1 classification and, for a
configured one, the manifest key **verbatim**; `port-map.tsv`, which is what a dependent of this
module will read; and `dropped-types.tsv`, from which deliberate test failures are DERIVED rather
than listed. The matching `/* porter: … */` notes are emitted beside the code itself —
`grep -rn '/\* porter:' src_managed` is the complete inventory of what this port did
non-mechanically. `subject.txt` is the `before->after` fragment for your commit subject.

None of that needs configuring. If your library's numbers are not appearing, the run did not reach
the artifact layer — do not add a writer.

### 2.05 A SECOND module — a dependent port

A library is rarely one module, and the second one is where ports drift. An extension (a plugin, an
add-on, the library's own test suite) references the base module's types, but its frontend can only
parse **Java**: it resolves against the base's *upstream* sources through
`FrontendConfig.resolutionRoots`, never against the Scala the base port emitted. Everything the
base's transforms did to those signatures — collections retyped, members dropped, a namespace moved,
a type substituted — has to be redone identically here, or the two ports each compile alone and
cannot compile together.

**Do not restate the base's policy. Import it and extend it.**

```scala
object <Lib>Policy:
  def core(repoRoot: Path): PortManifest = PortManifest(name = "<lib>-core", …)

  def extension(repoRoot: Path): PortManifest =
    core(repoRoot).extendedBy(PortManifest(
      name    = "<lib>-ext",
      governs = Set("com.upstream.ext"),          // omit when the packages interleave
      surface = List(new MyExtensionOnlyPhase),   // added AFTER the base's, which come first
      inject  = List(extOverridesDir),            // this module's OWN replacements
    ))
```

What is INHERITED (must agree) and what is not (may differ):

| inherited — a fact about the SHARED SURFACE | not inherited — a fact about THIS module's build |
|---|---|
| `dropTypes`, `dropMethods` | `inject` — exactly one module ships each replacement file |
| `packageRenames` | `sourceSet`, `frontend`, `portRoot`, `provenance` |
| `surface` (the signature-affecting phases) | `runtimeMode`, `supportSources`, `project`, `determinism`, `cache` |

The asymmetry in the first row is the one to get right: a **drop** is an observation about the
shared API and binds every module that sees the type; an **injection** is a build artefact, and a
dependent that copied it would emit a second definition of the same FQN.

`PortRun` checks the agreement on every run (`ManifestAgreement`, recorded as the `manifest` check,
fatal on a real disagreement), so writing the dependent's policy out longhand is allowed and
verified rather than merely trusted — use `PortManifest(...).mirroring(base)` when you want that.
Things it will refuse:

- **a dependent port that names no base at all.** Resolution roots outside your own source root make
  this a dependent port; a run that declares no `bases` fails with `NoBaseDeclared`. If the
  resolution root is *not* a ported module (a vendored third-party tree you resolve against and
  never emit), say so with an empty `PortManifest(name = "…")` as the base — that is a statement,
  not a loophole;
- a drop, a rename, or a signature-affecting phase present in one module and not the other;
- one phase name appearing twice in the effective pipeline with different policy.

What it cannot see, so that you do not trust it further than it goes: a parameterised phase's
CONFIGURATION unless that phase implements `SurfacePolicy` (otherwise two differently-configured
instances compare equal by name); nested-type drops, which are covered only by the never-fired
tally; and anything about the base's *emitted output*, which is `EnginePin`'s job.

Two source sets of one module are the smallest case of exactly this: `LibgdxTestMigrate` is a
dependent of `LibgdxCoreMigrate` and inherits `LibgdxPolicy.core`'s manifest. Never instantiate the
same parameterised phase twice with different arguments; that is the drift `CLAUDE.md` §1 warns
about, and it is now a fatal finding rather than a convention.

The migration program owns **all** per-library policy and nothing else:

- `Substitutions(dropTypes, dropMethods, inject)` — what not to emit and the Scala to inject instead
- the parameterised transforms, constructed with this library's values
- the injected replacement sources, under `balticporter/corpus/<lib>-overrides/`
- any §1(c) rule this library plugs in

Nothing library-specific goes into `api` / `engine` / `frontend-spoon`. When you need a new
rule, decide its kind FIRST (`CLAUDE.md` §1):

- universal → engine, unparameterised
- same mechanics, different values → engine, **constructor parameters**; empty parameter = no-op
- only ever this library → a separate plugged-in rule **in your own repository**

### 2.1 Writing a §1(c) rule — the worked example

`balticporter/corpus/src/main/scala/balticporter/corpus/libgdx/GdxSharedIteratorRule.scala` is the model, with
`balticporter/corpus/src/test/scala/balticporter/corpus/libgdx/GdxSharedIteratorRuleSpec.scala` as the model for
testing it. It is deliberately *not* in the engine, and it shows the three things the engine's own
phases cannot:

1. **Where the file goes** — beside your migration program, in your repository. It names the
   library freely; the §1 enforcement grep covers only `api`, `engine`, `frontend-spoon` and
   `runtime`, and a (c) rule being outside them is the point.
2. **How it enters the pipeline** — as an ordinary element of `PortRun(phases = …)`. Implement
   `balticporter.tir.Phase`; nothing has to be registered. If the port is driven from a `.conf`
   (§2.0) it needs a name instead, and that is one more five-line class beside the rule:
   `balticporter/corpus/src/main/scala/balticporter/corpus/libgdx/GdxSharedIteratorFactory.scala` plus a
   `META-INF/services/balticporter.tir.TransformFactory` line in your own resources. Still your
   repository, still compiled by your build — the conf holds a NAME, never behaviour.
3. **How it is tested** — `balticporter.testkit.PortSuite`, on a Java snippet, in your own test
   source set. Include a **negative** test: a check that has never reported is not known to work.

Before writing one, satisfy yourself the MECHANISM cannot be shared. Most things that look (c) are
a (b) with the policy inlined. A rule whose only library-specific part is a list of names is a (b);
a rule that encodes an invariant of that library's design is a (c).

## 3. Make it compile

Add a measurement lane to the root `Justfile` — copy the recipe nearest in shape to your library
(`sg-measure` for a self-contained port, `ashley-measure` for a dependent one), and put its sbt
project, upstream tree and dependency coordinates in the variable block at the top of the file
rather than inline. Every lane is a `#!/usr/bin/env bash` recipe that sources `scripts/_lib.sh`,
which is where the shared mechanism lives; **do not add `set -e`** (`grep -c` exits 1 when it counts
zero, and zero errors is the success case, so the lane would abort exactly when the port is green).
The shape, in order — every element of it exists because leaving it out hid something:

1. `write_run_props` with `balticporter.reportPathRoot` — a finding's stable id is hashed from paths
   relative to it, so without it every finding diffs as removed-and-re-added against a baseline whose
   counts are identical (`CLAUDE.md` §4.6).
2. run the migration and **abort if it did not run**: `grep -qE "wrote [0-9]+ Scala( test)? files"`.
   Piping straight into a count discards the exit status, so an engine that failed to compile
   measures the PREVIOUS emit and reports a stale number as a result.
3. `show_check_report` for each report directory — the whole block, never a `grep` for named lines,
   which is exactly how the checks got discarded by the one command everyone runs.
4. `java_test_count` against the Java suite, compared with what is discoverable in the emitted Scala.
   A suite with no discoverable tests runs ZERO and reports SUCCESS.
5. `break_residue <emitted-dir>` — untranslated `break`/`continue` jumps in emitted code.
6. compile with `scala-cli compile --scala 3.8.4 --server=false`, **strip ANSI** (`sed
   's/\x1b\[[0-9;]*m//g'` — dropped once, and every line then began with an escape, reporting 0
   errors for a port that had 20), count `^-- (\[E[0-9]+\] )?.*Error` — coded AND bare, since the
   coded-only count silently undercounts — and pass both through `compile_guard`, which catches a
   compiler that died without emitting errors.
7. if it compiles, `scala-cli test`, then `reconcile_outcomes <run-file> <emitted-count>`: outcomes
   are reconciled against the EMITTED count, not a sum of markers, so a test with no recognised line
   is reported whatever the reason. MUnit prints THREE terminal markers and the third — `==> s …
   skipped` — is what a suite abandoned after a fatal `Error` produces.
8. `correlate` over the compiler output (or the test output) with every relevant `srcmap.tsv`,
   including the BASE port's if this is a dependent — a stack that reaches the base is exactly what a
   dependent's failure looks like.
9. `headline` last.

A dependent port compiles the base's emitted Scala and its own on ONE `scala-cli` invocation.
Compiling the dependent alone measures nothing.

**The moment the first wall of errors appears, read `ENGINE-LIMITS.md` — before designing any fix.**
It is the measured record of what has already been tried: raw types and wildcards, constructors,
`this` and anonymous classes, the JDK/Scala collection boundary, portability, test porting, and the
ways the measurement itself misleads. Every entry carries its number and its direction (`13 → 28`,
`+277`, `inert`) and says which of `CLAUDE.md` §1's three kinds a fix would be. Reading it at the end
is reading it too late — most of these cost a session each to re-derive. Read `CLAUDE.md` §3.5 in the
same breath and check the reference port for the construct.

Then work the count down. Discipline (`CLAUDE.md` §5): **change one thing, then measure**; record
regressions and their cause; state `before->after` in the commit subject; read the emitted output to
confirm a fix rather than trusting the number.

Expect the count to **rise** the first time it reaches 0 — that is `RefChecks` running for the first
time (`CLAUDE.md` §3), not a regression.

## 4. Test-compile, then RUN the tests

Compiling is not the gate. Port the library's test sources through the same pipeline, compile them,
and run them. Report pass/fail honestly with the output; a test that does not run is not a passing
test.

Test frameworks differ — JUnit 4 (`org.junit.Test`, `Assert.*`), JUnit 5, TestNG. Decide the target
framework once and record it in the migration program.

## 5. Add the checks the library needed

Every translation path added for this library gets a check at the same time (`CLAUDE.md` §3). Walk
the tree with `StandardTraversal`, never a private recursion. Then **negative-test the check**:
break something deliberately and confirm it reports. A check that has never failed is not known to
work.

**Run the engine's own suite with `sbt -batch "testOnly *"` — never `sbt test`, never `testFull`.**
Bare `sbt test` maps to `testQuick` in this build and silently reports "No tests to run", so a green
`sbt test` has never been a gate here. And `testFull` over an unchanged tree is a cache REPLAY, not a
run: it keys on BYTECODE, so it proves nothing about flakiness and nothing about your change
(`ENGINE-LIMITS.md` M5.7).

## 6. Write it down — in `PROGRESS.md`, as a new section

**Do not create a per-library status file.** Add a section for the library to `PROGRESS.md`, beside
the ones already there, and add its row to §Corpus inventory:

- measured state (errors, omissions, portability, trivia, break residue, decisions, tests) in the
  same table shape the other sections use, with the command to reproduce it
- remaining work, highest value first, each item with the *shape* of its fix
- **Do NOT retry** — every measured failure, with its cost and cause
- which specialisations this library needed, classified (a)/(b)/(c), and which engine rules it
  generalised

Then split it: any dead end that is a fact about **Java, Scala 3, Spoon, dotty or the engine** goes
into `ENGINE-LIMITS.md` — with its number, its worked example and its (a)/(b)/(c) kind — because the
next library will be ported in a repository that never sees your measurements (`CLAUDE.md` §3.6,
§4.45). Leave a one-line pointer where you lifted it; the measurement stays. If your library
confirmed, contradicted or generalised an existing entry, **say so in that entry** — a limit that
survives a second library is stronger evidence than the one that first recorded it.

Research and scratch files stay under gitignored `.balticporter/` and are **never committed**
(`CLAUDE.md` §3.7). Incorporate what they found into `DESIGN.md` (a decision) or `PROGRESS.md`
(state) before calling the work done, then delete them. And maintain the remaining-work list by
DELETION — a done item is removed, not moved to a "done" section.

## 7. Hand off to the Auditor

When the work is delivered, tell the user it is ready for the **porting-auditor** agent
(`.claude/agents/porting-auditor.md`, Fable 5). Do **not** launch it yourself — it is expensive and
the user runs it deliberately, once per delivered body of work.
