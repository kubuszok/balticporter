# Baltic Porter — working rules

Baltic Porter is a **framework for porting Java libraries to Scala 3**, not a program for porting
one library. libGDX is spearheading the effort because it surfaces the most issues per file — but
the target is every libGDX module already ported in sge, every Java library that became part of ssg,
and, once published as open source, whatever libraries other people point it at.

Everything below follows from that. If a decision would make the engine better at libGDX and worse
at the next library, it is the wrong decision.

---

## 1. Universal vs library-specific — the rule that governs every phase and plugin

When designing a phase, transform or plugin, decide first **which of three kinds it is**. Get this
wrong and the framework silently becomes a libGDX porter.

### (a) Universal — belongs in the engine, unparameterised

A fact about **Java and Scala**, true of every codebase. Java arrays are covariant and Scala's are
not. Java interface constants are `static` and inherited; Scala companions do not inherit. Java
allows unchecked conversion at a raw type; Scala does not.

These live in `core` / `frontend-spoon` / `scala-emit` with no configuration.

### (b) Reusable mechanism, per-library policy — belongs in the engine, PARAMETERISED

The **mechanics are the same** for every library; what differs is *which* attributes, types,
variables or references get modified, and whether the rule runs at all. These belong in the engine
**taking their differences as constructor parameters** — sets, maps, lambdas, whatever fits — so the
porting program instantiates them with the values for its library.

An empty/default parameter must make the phase a **no-op**, so "turned off" needs no code path.

Current examples:

| phase | mechanism (engine) | policy (library) |
|---|---|---|
| `ClassTableTransform(Map)` | re-point a reflective name lookup at an explicit table | which method → which table |
| `StaticForwarderTransform(List[Forwarder])` | a wrapper's statics are plain members of argument 1 | which wrapper, receiver, members |
| `Substitutions` | do not emit these types/methods; inject this Scala instead | which ones, and the replacement sources |

### (c) Genuinely library-specific — a SEPARATE, PLUGGED-IN RULE

If a customisation needs knowledge so specific that it could only ever apply to **one** library, it
is a separate rule that the porting program plugs in. It does not go in the engine at any level of
generality. In future it will be maintained by the repository that manages that library's porting
effort, not by this repository.

Turning a primitive into an opaque type is the canonical example: *which* `Int`s are really a GL
handle is knowledge about libGDX and nothing else.

### The balance

**Design every rule to be as reusable as possible.** Reach for (c) only after establishing that the
mechanism genuinely cannot be shared. Most things that look library-specific are a (b) with the
policy inlined — that is exactly the mistake `ReflectionToPortableTransform` made, hard-coding
`com.badlogic.gdx.utils.reflect.ClassReflection` and its member list inside `core`.

### Enforcing it

No file under `core/`, `frontend-spoon/` or `scala-emit/` may name a ported library **in code**:

```
grep -rn --include='*.scala' -E "badlogic|libgdx" core frontend-spoon scala-emit | grep -vE ":\s*(\*|//)"
```

Library names in **doc comments** are fine and wanted — the worked example that justifies a general
rule (`GL30Interceptor` witnessing the export diamond, `Array<? extends T>` witnessing array
covariance). They document; they must drive nothing.

---

## 1.5 A dependent module INHERITS the shared surface; it never restates it

A library is rarely one module, and the second one is where a port drifts. An extension resolves
against the base module's **Java** (`resolutionRoots`), never against the Scala the base port
emitted — so everything the base's transforms did to those signatures has to be redone identically,
or the two ports each compile alone and cannot compile together. Copying the configuration is not a
mechanism; it is a habit, and it fails one module at a time.

So the shared surface is a VALUE — `PortManifest` — that a dependent imports and extends
(`base.extendedBy(...)`), never a block of policy it repeats. Ordinary Scala, type-checked by the
consumer's compiler; a manifest DSL would move the policy out of reach of both.

The line between what must agree and what must not:

| inherited — a fact about the SHARED SURFACE | not inherited — a fact about THIS module's build |
|---|---|
| `dropTypes`, `dropMethods`, `packageRenames`, `surface` | `sourceSet`, `frontend`, `provenance`, `runtimeMode`, `supportSources`, `project` |
| | **`inject`** |

`inject` is the one that looks wrong and is not. A drop and its replacement read as one decision and
are two: the DROP is an observation about the shared API and binds every module that sees the type;
the INJECTION is a build artefact, and exactly one module must ship each replacement file — a
dependent that copied it would emit a second definition of the same FQN. Every check that asks "is
this replaced?" follows the same line and holds a module to its OWN drops only.

`PortRun` runs `ManifestAgreement` on every port, and a run whose resolution roots lie outside its
own source root — the structural signature of a dependent — with no base declared is itself a fatal
finding. If a resolution root is genuinely NOT a ported module, declare an empty manifest for it and
say so; that is a statement, not a loophole.

---

## 2. Adding a library to the corpus

Until the framework is published and each library gets its own porter repository, new libraries are
added to the **corpus** (`corpus-tests/`). The procedure for each:

1. **Make it compile.** Every effort — this is where the engine's gaps surface.
2. **Test-compile it**, then port and run its tests. Compiling is not passing; see §3.
3. **Run the Auditor** (§4) over both the new specialisations and the shared code.

Each library added is expected to move engine rules from (c) toward (b) toward (a). A rule that
survives three libraries unchanged is probably universal; one that needs a new parameter per library
is correctly a (b); one that cannot be shared at all is a (c) and should be named as such.

---

## 3. Compiling is not the gate

The compile-error count is a **typer-only** measurement: dotty's `Phase.isRunnable` is
`!ctx.reporter.hasErrors`, so a single typer error skips `RefChecks` for the whole program. Missing
`override`, unimplemented members and variance violations are unmeasured until the count reaches 0 —
and then the number will RISE. That is the gate beginning to tell the truth, not a regression.

Worse, a green compile says nothing about behaviour. Four silent correctness defects were found in
libGDX core that all compiled cleanly — dropped `static { }` blocks, dropped `super(args)`, dropped
anonymous-class bodies (156 sites, every button silently doing nothing), and the typer blind spot
itself. Each would have shipped.

So:

- **Every translation path gets a check at the same time it gets a translation.** A check reporting
  zero is only as good as its coverage.
- **Walk the tree with `StandardTraversal`**, never a private recursion — two of those four defects
  were hand-rolled traversals that stopped one node short.
- **Prefer running ported tests over any number of further compile fixes.** Assertions are the only
  evidence of behaviour this project can have.
- **Read the emitted output**, not just the count, when confirming a fix.

---

## 3.5 Consult the REFERENCE PORT before inventing a rule

sge is a hand-written port of the same library, and ssg contains hand-ported Java libraries. Where
they solved a problem, the solution is visible and already validated against real code. **Look there
before designing a rule, and before concluding that no faithful translation exists.**

```
grep -rn "<the construct>" ../sge/sge/src/main/scala/    # and ../ssg for its libraries
```

Two things to record when you find one:

- **What they emitted.** Example: every raw generic is rendered `[?]` — parent, overrides and fields
  alike (`AssetLoader.getDependencies` returns `DynamicArray[AssetDescriptor[?]]` and every override
  matches). That settles both that `?` round-trips across an override, and that filling the element
  with the loader's own `T` is semantically wrong, since a `BitmapFont`'s dependencies are a
  `TextureAtlas`.
- **Whether they SOLVED it or SKIPPED it.** sge also renamed `AsyncTask` to `() => Unit` and simply
  did not port several classes. Skips are not models — this project exists precisely to port what
  sge left out — so a construct that merely vanished from sge tells you nothing except that it is
  still open.

Reasoning from first principles when a worked answer exists nearby wastes whole sessions. It has.

## 3.6 Where a discovery goes

A lesson that would change how the NEXT library is ported does not belong only in that port's
progress section — nothing loads it, and it gets re-derived. **There is one document per KIND**, and
a discovery goes into whichever fits, in the same commit that learned it:

| home | for |
|---|---|
| this file | a governing rule or constraint for all porting work |
| `ENGINE-LIMITS.md` | a MEASURED dead end or engine limit — what not to retry, and what it cost |
| `DESIGN.md` | a DECISION about what the engine is or how it is built |
| `PROGRESS.md` | the STATE of a port or of publishability — measurements, residues, remaining work |
| a skill (`.claude/skills/**`) | a procedure, e.g. adding a library to the corpus |
| an agent definition (`.claude/agents/**`) | what a reviewer should hunt for |

**Do not add a seventh document.** A new file for one investigation is a file nothing loads; see §3.7.

That library's `PROGRESS.md` section keeps the MEASUREMENTS and the dead ends with their numbers. The
rule extracted from them goes above. A rule that names a specific library is per-library policy and
belongs in that library's manifest instead (§1c).

`ENGINE-LIMITS.md` is the split between the first two rows: this file says what you must do,
`ENGINE-LIMITS.md` says what has already been tried and measured worse, grouped by what an agent is
doing when it hits the wall and classified (a)/(b)/(c). Add to it in the same commit that measures
the failure, and keep the number — a dead end without its number is an opinion.

## 3.7 A RESEARCH FILE IS NOT A DELIVERABLE

Working through a question often wants a scratch document — a survey, a table of candidate shapes, a
transcript of what four experiments measured. **Write it. Do not commit it.**

- Scratch and research files live under **`.balticporter/`**, which is gitignored and which the
  measure scripts already use for their own captures. Nothing else in the repository is a valid home
  for one.
- Before the work is called done, what the file FOUND is incorporated: a decision into `DESIGN.md`, a
  measurement or a residue into `PROGRESS.md`, a measured dead end into `ENGINE-LIMITS.md`, a
  governing rule here. Then the scratch file is deleted, not committed.
- A committed research file is worse than no file. It is not in the §3.6 table, so nothing loads it;
  it accretes a status section, so it starts disagreeing with `PROGRESS.md`; and its conclusions get
  re-derived anyway because the next agent never opens it. Every document deleted in the
  consolidation that produced `DESIGN.md` and `PROGRESS.md` began as exactly this.

**A TODO list shrinks by DELETION.** A completed item is REMOVED — never moved to a "done" section,
never struck through, never annotated `[x]`. A list that only grows stops being a list of what is
left and becomes a changelog nobody reads, and the remaining work is then invisible inside it. If a
finished item taught something worth keeping, that lesson goes to one of the §3.6 homes; the list
entry still goes. Git history is the record of what was done.

## 4. The Auditor

An **adversarial reviewer** (`.claude/agents/porting-auditor.md`) that reads the engine and the
per-library specialisations looking for over-specificity, missed cases and shortcuts — rules that
happen to work on the corpus rather than being right.

It runs on the **Fable 5** model and is expensive, so it is **not** run on every change. It is run
**by the user, once a whole piece of work is delivered.** Do not launch it speculatively.

---

## 5. Measurement discipline

- **Reproduce every number with the measure scripts, serially.** There are four lanes and each
  re-emits into `src_managed/`, so a dependent lane compiles against what the base lane just wrote:

  | script | lane |
  |---|---|
  | `scripts/gdx_measure.sh` | libGDX core — emit, checks, break residue, compile, correlate |
  | `scripts/gdx_test_measure.sh` | libGDX's own suite — the same, then RUN it |
  | `scripts/ashley_measure.sh` | Ashley + its suite, compiled WITH libGDX core (a dependent port) |
  | `scripts/sg_measure.sh` | simple-graphs + its suite |
  | `scripts/decision_counts.sh` | `decisions.tsv` row counts by kind, every port |

  Each prints, untruncated and diffed against the committed baseline, **fifteen engine checks —
  not four — plus any check the port's own §1(c) rules register** (libGDX adds
  `gdx-shared-iterator`, so its lanes show sixteen). Twelve are required of every run
  (`signature`, `omissions`, `portability(all|emitted|injected)`,
  `substitution(emitted|dangling)`, `remediation`, `policy`, `manifest`, `port-map`, `trivia`);
  `porter-notes` records on every run, and `collection-closure`/`collection-boundary` record when
  `CollectionsTransform` is in the pipeline. `PortRun.RequiredChecks` is asserted against what
  actually recorded, so a number that reaches stdout and not `findings.tsv` fails the run.

  Four more measurements are NOT check counts and are printed beside them, because each catches a
  class nothing else can see:

  - **`break_residue`** — untranslated `break`/`continue` jumps left in emitted code. It was quoted
    in prose as "45, all switch-case" while nothing computed it; the real number was 55 (§4.4).
  - **the TEST lane** — `reconcile_outcomes` reconciles outcomes against the **emitted** test count,
    not against a sum of markers, so a test with no recognised line is reported whatever the reason
    (§5.1). A skipped test is not a passing test.
  - **`members.tsv`** — which members' emitted text moved, available BEFORE any compile (§5.1).
  - **`decisions.tsv` + the porter notes** — how many non-mechanical decisions the port made, by
    kind, and whether every one of them reached the code (§4.575). `porter-notes` is the check;
    `scripts/decision_counts.sh` is the size, which nothing else prints.
- **Change one thing, then measure.** Two changes measured together cost a full cycle to untangle
  and tell you nothing about either.
- **Record what regressed and why**, in that library's `PROGRESS.md` section under "Do NOT retry". A
  measured failure is a result; re-deriving it later is waste.
- State counts as `before->after` in the commit subject.

### 5.1 A diagnostic over emitted code is ATTRIBUTABLE — never read it by hand

`PortRun` writes `srcmap.tsv` (member → emitted line range → Java `Origin`) and `members.tsv`
(one digest per emitted member) into the run's report directory on every migration, from the
emitter's own recording — `TirEmitter.srcMap` is a value one emitter owns, never a process-global
table. `balticporter.tir.CorrelateRun` joins compiler output and TEST-RUNNER output back through
them, in-process (`PortRun.correlate`) or as a command (`CorrelateMain`). The measure scripts run
the command; run it yourself when you run a compiler by hand. Three consequences that change what
you should do:

- **Never open an emitted file to work out which member an error is in.** `errors.tsv` already
  says, with the Java file and line, and splits errors into "at a region the engine marked
  approximate", "engine gap" and "outside the source map" (an injected shim is none of the other
  two) — plus, on a `preview = true` run only, "declared unrenderable" (`Correlate.Lane.Declared`),
  so the engine's own `compiletime.error` markers never inflate the real error count.
- **The member-digest baseline is the blast radius, and it is available BEFORE a compile.** After a
  change, `run-latest/members.tsv` against `baseline/members.tsv` says exactly which members'
  emitted text moved. Identical files mean the output is byte-for-byte unchanged — which is a
  stronger revert check than any count, because *no check count moves for most transform
  regressions* (with the whole pipeline skipped, every check count is unchanged).
- **The test lane is the only one that sees §4.4.** The §4.4 table's Java forms translate to valid Scala meaning
  something else and move no error count. `tests.tsv` is the pass/fail baseline and the diff names
  newly-failing tests, anchors each on the first stack frame in ported code, and says how good that
  anchor is (`main-frame` = the library member that threw; `test-frame` = only where the failure
  was observed). A test that stopped running is reported as such, never as a pass.
- **Parse every TERMINAL MARKER the runner emits, and gate on each.** MUnit prints three — `  + `
  (pass), `==> X ` (fail) and `==> s <suite>.<name> skipped 0.0s`. The third was dropped by the
  parser, so a suite abandoned after a fatal error lost its remaining tests from `tests.tsv`
  entirely and the run reported success (ashley: 112 emitted, 110 recorded). A skip moves no pass
  count and no fail count, which is exactly why it needs its own gate — `TestDiff.newlySkipped`,
  and `skipped` is kept apart from `ignored` because an ignored test is a DECISION and a skipped
  one is PREVENTION. Same rule for a missing INPUT: a `--tests` path that does not exist is fatal,
  never a header-only artifact and a headline of "0 passing, 0 failing".
- **`decisions.tsv` says WHY the emitted code is not a mechanical translation.** The source map
  answers "which Java produced this line"; it cannot answer "why is this type simply absent, this
  package not the upstream one, this member from a hand-written file". Each of those is a
  `balticporter.tir.Decision`, and its `Reason` is a CONSTRUCTOR PARAMETER — `Universal(rule)` /
  `Configured(phase, key)` / `LibraryRule(rule)` — because §4.45's rule applies to provenance
  exactly as it does to findings: a note that does not say which of §1's three kinds the fix is
  costs its reader a full investigation. For a `Configured` one the KEY is the manifest entry
  verbatim, which is the string an agent edits to change the outcome. A phase records with
  `Phase.record`; `Pipeline.runTraced` hands back the log; the run's non-phase deciders record into
  the same one. The log is a value ONE RUN owns and each phase's buffer is drained into it, for the
  reason stated above for the source map. Every decider records at the DECLARATION level — one row
  per declaration whose emitted form the decision changes, never one per expression: a site-level
  rewrite is already visible in the diff the reader is holding, and what the diff cannot say is
  which policy entry produced it. And the artifact is scoped to THIS MODULE's declarations, because
  `ENGINE-LIMITS.md` D2 governs a provenance artifact exactly as it governs a check — a dependent's
  phases decide about its base's units too, and republishing those puts a module's own rows in a
  minority in its own file (libgdx-test: 961 of 1240).
- **An artifact write is GATED ON THE ARTIFACT LAYER, without exception.** One unconditional
  `PortMap.write` was enough for the engine's own forked test suites to publish port maps into the
  checkout (`runner/port-report/…`, and once a COMMITTED `port-report/jar/`), because with reporting
  off the report directory falls back to `<cwd>/port-report/…` and a forked test's cwd is the
  subproject. A `git status` that cannot distinguish a decision from an artefact defeats §5.5, and
  the gate belongs at the write, not in each caller — a wrapper every spec must remember is a
  wrapper one spec will not.

Deliberate failures are **DERIVED, not listed**. A test whose failure stack reaches a type in the
port's `Substitutions.dropTypes` fails because the port deliberately does not have that type, so
`PortRun` writes those FQNs to `run-latest/dropped-types.tsv` on every run — `upstream` TAB
`emitted`, both namespaces, because the stack frame it will be matched against is renamed and the
manifest key is not (§4.56) — and the correlator classifies from them: the set follows the manifest
with nobody editing anything, and `core` still names no library (§1). `port-report/<Port>/baseline/expected-failures.tsv` survives ONLY as the
explicit escape hatch for a failure no drop explains, and is normally empty; the artifact records
`expected#derived` against `expected#declared` so the two can never be confused. A hand-maintained
list of expected failures is exactly the thing that rots into "we always ignore those four" and
then hides a fifth. Promote every baseline with `bash scripts/port_baseline.sh accept <port>`.

---

## 4.45 The consumer is an AGENT IN ANOTHER REPOSITORY

The engine's users are not this repository. sge and ssg will maintain their ports by pointing a
published Baltic Porter at their Java sources, with agents doing that work in *their* repos,
without this session's context. Two standing consequences:

- **A lesson that is an ENGINE limit must live somewhere the engine ships.** CLAUDE.md,
  `ENGINE-LIMITS.md`, a skill, or an agent definition — not only in a per-library status section. §3.6
  already says this. The engine-scoped dead ends measured on libGDX — the raw-anon refusal, `given
  Conversion` never firing, wildcards round-tripping across an override, "erase uses, never
  declarations" — are now lifted into `ENGINE-LIMITS.md`, with the counts and the per-site diagnosis
  left in `PROGRESS.md`'s section for that library, under a pointer. **When you measure a new one, put
  the rule there in the same commit.** The remaining exception is per-library POLICY, which is where it
  belongs.
- **A check must say which of §1's three kinds the fix is.** An error an agent cannot classify as
  (a) engine bug, (b) configure an existing phase, or (c) write a library-specific rule costs it a
  full investigation. `PortabilityCheck` and `RewriteTrace` do this well; bare typer errors do not,
  and they are the bulk of a new library's first wall. Every `ENGINE-LIMITS.md` entry carries this
  classification for the same reason.

`PROGRESS.md` §Publishability holds the full audit of what is missing before that consumption is
possible.

## 4.5 Never model a Java interface on a Scala COLLECTION trait

When a Java interface needs a Scala counterpart the stdlib does not have, write a standalone trait
with Java's own shape — Java's method *arity* included (`iterator()`, `hasNext()`, `next()`, not
Scala's parameterless forms). Restore Scala interop with **extension methods**, never by extending
`scala.collection.*`.

The reason is not taste. Java interfaces are small and orthogonal, so a class routinely implements
several: 14 classes in libGDX core implement both `Iterable<E>` and `Iterator<E>`. Scala's
collection traits are large and interlocking, and that same shape is *illegal* under them —
`Iterator.iterator` is `final`, `seq` arrives from both parents. No `override` recovers it, because
the conflict is in the parents. Inheriting also imports hundreds of members that then clash with
the ported class's own `size`, `isEmpty`, `remove`.

An extension adds a VIEW and cannot conflict; a parent adds MEMBERS and does. Put such an extension
on **one** of a pair of related shims, never both — with `foreach` on both an iterable-and-iterator
class made every `for` ambiguous.

Note this whole class of defect is invisible while any typer error remains (§3).

## 4.4 Java statement semantics Scala does not share — the ones that COMPILE

Each of these translates to syntactically valid Scala that means something else. None moves a
compile-error count; every one was found by RUNNING the ported tests, never by a compile:

| Java | naive Scala | why it is wrong | faithful |
|---|---|---|---|
| `a == b` (references) | `a == b` | Scala's `==` calls `equals` — and inside an `equals` body that is infinite recursion | `a eq b` |
| `x++` as a value | `{ x += 1; x }` | post-increment yields the value BEFORE the update; every circular buffer was off by one | `{ val p = x; x += 1; p }` |
| `break` / `continue` | *nothing* | the loop simply ran on / the rest of the body still executed | `boundary` around the LOOP for break, around the BODY for continue; name the outer one when both |
| `break L` / `continue L` | *nothing* | a labelled jump crosses nested loops and switches by definition | a NAMED boundary on the labelled statement, `break(())(using brk)` |
| `L:` on a statement that is NOT a loop | *nothing* — a label on an `if`, a block or a `switch` had nowhere to live | `break L` leaves THAT statement; dropped, `JsonReader` fell through after `bool(name,true)` and emitted a second, string event for every unquoted bool/null/number | `Tree.Labeled` + a named `boundary` around the STATEMENT. A LOOP's label stays in the loop node — it is also `continue L`'s target, whose boundary goes elsewhere |
| any boundary the emitter INTERPOSES | an un-annotated `break(())` under it | `boundary.break` with no `using` resolves the INNERMOST `Label`, so a new boundary silently steals the enclosing loop's jumps | name the enclosing boundary whenever anything inside the body renders with a boundary of its own; over-approximate, an unused name costs one identifier |
| `switch` with no `default` | `match` with no `case _` | java FALLS OUT when nothing matches; scala throws `MatchError` — and falling out is often the normal path | always emit the fall-out arm |
| a case's trailing `break L` | stripped as a case terminator | only an UNLABELLED break ends a case; a labelled one leaves the LOOP | strip unlabelled only |
| a `break` in the MIDDLE of a case | *nothing* | it ends the CASE — and fallthrough is lowered by duplicating the next case's tail INTO this arm, so what ran on is code java put in a different case | a named `boundary` around the ARM; `match` cannot leave an arm early |
| `static final int X = 0` | `final val X: Int = 0` | java INLINES a constant variable, so reading it never triggers the class initialiser; the typed `val` does, and libgdx's `Vector3`/`Matrix4` initialisers are a cycle | `inline val X = 0`, literal rendered AT the declared type |
| `super(args)` in a 2nd ctor | *nothing* | scala secondary constructors cannot call super; every exception lost its message | promote the widest super call to the PRIMARY and delegate (JDK throwables only — elsewhere padding is a guess) |
| `@Before` | *nothing* | JUnit runs it before EVERY test, on a fresh instance; MUnit has neither | call it at the head of each test body |
| `@Test(expected = E.class)` | body run bare | it would PASS while checking nothing | `intercept[E] { … }` |
| `list.remove(anInteger)` | `buffer.remove(x)` | java resolved `remove(Object)` — by VALUE, returning `boolean`; scala's only `Buffer.remove` is BY INDEX, and `Integer2int` applies silently, so `[10,11,12].remove(Integer.valueOf(1))` removes nothing in java and `11` in the port | a by-value helper. Read WHICH overload java resolved off the call's RESULT type: `remove(Object)` returns a primitive `boolean` and `remove(int)` returns the element, which java generics can never make primitive |

Before adding a translation for a Java *statement* form, ask what it means when its value or its
control flow is used, not only what it looks like. And read §3 again: a green compile said nothing
about any of these.

## 4.55 A renaming pass reads EFFECTIVE names, PARENTS-FIRST

Java lets a name be reused where Scala cannot: a field shadows an inherited field, a field coexists
with a same-named method, a constructor local becomes a member. Each is fixed by renaming, and
renaming the symbol propagates to every reference — which is exact, because Java resolved all of
these STATICALLY, so each reference already points at the symbol Java chose.

Two things every such pass must do, learned by getting both wrong:

- **Read effective names, not original ones.** If an ancestor has already been renamed, the new name
  is what a descendant must avoid. Reading originals renames `CheckBox.style` and `TextButton.style`
  to the same `style$shadow` and just moves the collision up a level. Then keep appending until the
  name is free.
- **Scan parents before children**, or the previous point cannot hold.

And count what the constructor funnel PROMOTES — the chosen constructor's parameters *and* its
top-level locals. Neither is in the class body, both become members, and a Java constructor local
becoming a Scala member is exactly what a subclass then collides with.

## 4.56 A rename decides OWNERSHIP structurally, never by name

Any pass that rewrites a name by prefix — package rename above all — must first answer *does this
program declare this symbol?*, and must answer it from STRUCTURE, not from the string. A prefix
match alone rewrites the standard library: `Map("java" -> "j")` turns `java.lang.String` into
`j.lang.String` and every honest map that happens to share a prefix does the same, silently, with a
green compile.

The TIR answers it: the frontend interns an external symbol lazily with `owner = SymId.None` and no
`Definition`, while everything the program declares hangs off a top-level unit through the `owner`
chain. So **a symbol is owned iff climbing its owners reaches a `program.units` symbol** — stronger
than "has a definition", which anonymous-class symbols do not.

Two corollaries for any prefix rule:

- **Cut only at a separator.** `Symbol.fullName` uses three: `.` between packages and the top-level
  type, `$` before a nested type, `#` before a member. `com.foo` must not cover `com.foobar`, and
  everything after the cut is carried across verbatim or nested-type paths break — at EMISSION,
  not at the rename.
- **A namespace rename runs LAST.** Every other phase's policy (`ClassTableTransform` redirects,
  `StaticForwarderTransform` wrappers, `Substitutions` drops, opaque-type hints) is written in the
  UPSTREAM namespace. Rename first and all of them match nothing — a phase that does nothing, with
  no error. `runsAfter` cannot say "after everything"; `Pipeline.order` is stable in declaration
  order, so the porting program places it last and `PackageRenameTransform.check` verifies it.

- **An artifact that joins POLICY to OBSERVED code carries BOTH names.** The corollary of the point
  above, and it fails silently in the other direction: policy is upstream, but a stack frame, a
  compiler path and a class name are all EMITTED, so any file joining the two is comparing two
  namespaces. `dropped-types.tsv` held only the manifest FQN, and the derived expected-failure rule
  it feeds had therefore **never once fired on a renaming port** — libGDX's four deliberate
  `JsonTest` failures were reported as unexpected on every run since the rule was written, with the
  claim that they were classified living only in prose. The run is the last place that holds the
  manifest name and the rename map together, so the run writes both (`Correlate.Dropped`) and
  nothing downstream re-derives a namespace it cannot see. Translate with
  `PackageRenameTransform.renamed` — the phase's own rule — never a hand-written `startsWith`.

- **Match a runtime class name at a SEPARATOR too.** The same cut applies on the reading side: a
  frame in `p.Json` may arrive as `p.Json`, `p.Json$`, `p.Json$Ref` or `p.Json$$anonfun$3`, and
  `p.JsonTest` is none of them. And do not match through the source map: a DROPPED type is the one
  type the port does not emit, so it has no `srcmap` entry at all — its replacement is injected
  Scala the emitter never saw. The class name in the frame is the only place it appears.

**And it is not only renames.** A second phase has now been bitten by the same string test, which is
what makes this a rule about DECIDING FROM A NAME rather than a rule about renaming.
`CollectionsTransform` decided "this cast can never succeed" from the cast's SOURCE type having a
`java.` prefix — and `java.lang.Object` has one. `(Collection<V>) anObject` is an ordinary downcast
that the phase does not touch on the source side, so at run time the value IS a shim and the cast
succeeds; deleting it turned a correct program into a wrong one, at three sites in libGDX's `Json`
alone. A prefix is not a structural fact about anything.

The general form: **a phase may only conclude something about a type from what the PHASE ITSELF did
to that type.** Every retyping phase already has that record — `CollectionsTransform` has `typeMap`
and the `remap`/`kindOf` tables built from it — so the question "did I move this type out of the
family?" is a lookup, and "is this type one of mine?" is a membership test. A type the phase does
NOT retype is by definition still whatever it was, and the phase has no standing to reason about it.
Note this failure is invisible to every count: the port still compiled and every check reported the
same number, because the members it broke were inside a type the library drops.

## 4.57 Every emission backend carries PROVENANCE — it is a licence obligation

Each library in reach of this engine is licensed (Apache-2.0 so far) and every port is a derived
work, so every generated file ships an attribution header. Nothing in the pipeline reports a
missing one — the output compiles perfectly without it — so a new backend loses the header silently,
which is exactly how the TIR path regressed a feature the BIR path had.

Take the source path from the unit's `Origin`, never from its FQN: a renamed or nested type does not
live where its FQN suggests, and after a package rename the FQN is not the upstream one at all.
Where the origin cannot be relativised, say so in the header — a wrong-but-plausible path defeats
the only purpose the line has.

## 4.575 A PORTER NOTE puts the decision where the question is asked

Every non-mechanical thing the port did is recorded in `decisions.tsv` with its §1 classification.
That artifact answers an agent that holds the run directory. The agent this engine actually has is
reading ONE emitted file in another repository (§4.45), and its question is asked at a line of
Scala: *why is this field called `style$shadow`, why is this method simply absent, why does this
file live in a package the upstream never had.* A record in a sibling TSV cannot be found from
there.

So the same fact is also emitted BESIDE the code, in one grammar:

```
/* porter: <kind-slug> k=v … — <free text> */
/* porter: renamed-member reason=universal rule=member-rename(§4.55) clash=field-vs-method from=align to=align$field */
```

`<kind-slug>` is `Decision.Kind` in kebab case — the enum, never a string a decider chose. The pairs
carry the §1 classification first (`reason=universal|configured|library-rule`, then `rule=` or
`phase=`+`key=`), because which repository the fix lives in is the reader's first question; then the
decision's own detail, sorted; then `why` as free text after an em dash. A value containing
whitespace is QUOTED, or the whitespace-separated pair list silently truncates it. `grep -rn '/\*
porter:' src_managed` is the complete inventory of non-mechanical translation in a port.

Three rules that are not style:

- **Notes are DERIVED, never authored.** `TirEmitter` renders only decisions whose subject it is
  emitting; nothing constructs a note from a local condition. A note invented at the emitter is
  policy that reads as authoritative, and `NoteCoverageCheck` fails the run for it — in both
  directions: a decision about an emitted subject with no note, and a note with no decision behind
  it. Neither is visible to a compile, to any other count, or to a test.
- **Original trivia FIRST, note LAST, member next.** The upstream comment is what a licence obliges
  the port to reproduce (§4.58); a note above it reads as part of it and displaces it.
- **A note may never open or close a comment.** Scala block comments nest (§4.58), so every
  rendered value goes through `PorterNote.safe`.

Where a kind's note goes is machinery, not taste: `PorterNote.AtDeclaration` (above the `def`/`val`/
nested `class`), `PorterNote.InBody` (a dropped member has no declaration to sit above, so its note
heads the owning type's body), `PorterNote.NotInTree` (a dropped TYPE's note is carried by the
INJECTED file that supplies its FQN, prepended at copy time). A kind in the wrong set is a note that
never appears.

Two consequences to keep in mind when adding one: a note is emitted text, so it moves member
digests (§5.1) — expect the blast and account for it; and a check that searches emitted text for a
string must strip notes first, because a note names the UPSTREAM FQN on purpose
(`SubstitutionCheck.dangling` reported 3 phantom dangling drops the first time notes shipped).

## 4.58 COMMENTS are part of the port — and only a TEXT-to-TEXT check can see them

The upstream licence lives in a comment, and §4.57's generated banner does not replace it: the
banner says what the file is, the notice is the thing the licence obliges a derived work to
reproduce. Everything below follows from taking that seriously.

**Slice VERBATIM from the source buffer; never re-print.** A parser's `toString` for a comment
reflows the body, normalises the ` * ` gutter and loses the alignment of a `<pre>` block — fine for
prose, not for a legal notice. `SpoonTir.triviaOf` cuts the comment out of the original text by its
source positions. Note an in-memory compilation unit may have NO buffer (Spoon's
`getOriginalSourceCode` returns null for a `VirtualFile`), so the convenience parse path has to be
handed the text it was given, or it is silently the one path that does not preserve anything.

**One comment, one home: keep a CLAIMED identity set.** Harvesting is layered — a declaration takes
its own, and a statement then scoops whatever expression-level comments its subtree still has (the
TIR has no node for those). A coarse harvest must therefore run AFTER its children have translated
and must skip what they took, by IDENTITY, or every nested comment is emitted twice. The one
deliberate exception is the FILE header: a Java file with two top-level types becomes two Scala
files and each is a derived work, so each carries the notice.

**Two Java-vs-Scala comment facts, both of which break the emitted file, neither of which any type
check sees:**

| Java | naive Scala | what happens |
|---|---|---|
| `/* see the /* marker */` | same text | Scala block comments NEST; Java's do not. The emitted comment never closes and swallows the rest of the file. Emit such a comment line-by-line as `//` — every character survives and nothing can open. |
| a statement separator decided by `nextLine.startsWith("{")` | comment first, `{` second | a comment is WHITESPACE to the parser, so `new Array[String](n)` / `// note` / `{ … }` still parses the block as an anonymous-class body. Any such test must skip comments the way a scanner does (`TirEmitter.firstCode`). Measured: 0 → 2 errors on libGDX the first time trivia was emitted. |

**Indent is re-derived; text is not.** A comment is re-indented to the node it belongs to (its
internal relative alignment preserved) rather than reproduced at the column upstream used — a port
is regenerated on every engine change and a diff that moves because a comment re-wrapped is a diff
nobody reads. Emitted at its original column, a nested member's Javadoc reads as a comment on the
enclosing class.

**The check compares SOURCE TEXT to EMITTED TEXT — never the tree.** This is the part that is easy
to get wrong and expensive to leave wrong. Counting `Trivia` nodes proves the frontend harvested and
proves nothing about the emitter; and the frontend's own notion of "every comment" is the parser's
attachment model, which is exactly what may be incomplete. `TriviaCheck` re-lexes the Java
independently (`CommentScanner`) and looks for each comment's normalised body in what the run
actually WROTE, grouped by Java file. Nothing else in the pipeline can fail when this feature
regresses: the output compiles perfectly with every comment gone, no count moves, and no test
breaks. It regressed to exactly that during development — one null reaching a broad `catch` turned
the whole harvest into `Nil` — and the emitted Scala was valid.

**A `catch` around a harvest is how that happens.** Wrap only the lookups where an absent value is
NORMAL (a missing source buffer); let a harvest that throws be seen.

## 4.6 A kill switch beats another condition

When a synthesized construct is wrong, first establish **which code produces it** — do not add a
condition to the gate you suspect. Return the input unchanged at the top of that function, print on
entry, and re-emit: one run tells you whether the gate is even consulted. If it is not, tag every
construction site of that node kind and grep the trace for the source line. Three consecutive edits
to `uncheckedGeneric` measured no change at all before a kill switch showed, in one run, that the
cast came from the emitter.

`sbt -client` talks to a long-running server, so a shell environment variable never reaches the
forked migration. Gate the switch on a marker FILE.

**The kill switch is now a FLAG — do not edit source to get one.** `Pipeline.run` reads these, so
the question "is this phase even responsible" costs one run and no diff:

| flag | does |
|---|---|
| `balticporter.skipPhases=<name>,<name>` (or `*`) | omit those phases; the answer in one run |
| `balticporter.dumpTirBefore=<phase>` / `dumpTirAfter=<phase>` | print the TIR around a phase |
| `balticporter.dumpOnly=<fqn>` | narrow either dump to one type |
| `balticporter.tracePhases` | announce each phase as it runs |
| `balticporter.traceNode=<Kind>` | `TirTrace.mint` prints constructing frames for a node kind — no node gains a field |

Resolution order is **system property → `<root>/.balticporter/run.properties` (script-written) →
`<root>/.balticporter/debug.properties` (hand-written, wins)**. Note the marker file is not merely a convenience:
a `-D` on the *caller's* command line does not reach the forked migration either, because `sbt`
forks it with `javaOptions` from `build.sbt`. Only the file crosses that boundary.

`TirPrinter` renders the TIR readably (`canonical` style leaks no `SymId` and no origin, so two
runs are comparable); `DebugEmit` models once and emits one FQN, optionally around a phase.

**A flag that carries measurement identity must come from the PORT, not the operator.**
`balticporter.reportPathRoot` anchors the paths a finding's stable id is hashed from. Set only by
the measure scripts, it silently falls back when the migration is run directly — and every finding
then diffs as removed-and-re-added against a baseline whose *counts are identical*. A baseline that
reproduces only through one shell script is not a baseline. Derive such a value from the port's own
configuration.

## 5.4 Compare paths through `toRealPath`, on BOTH sides — always

Two independent parts of the engine have now been bitten by the same thing, which is what makes it
a rule rather than a note. Every path this project compares has two forms: the one an operator or a
config *wrote*, and the one the parser *recorded*. They differ whenever a symlink is in play, and a
symlink is in play in the normal case — **a git worktree reaches its sibling checkouts through
one**, and `.claude/worktrees/<x>/../sge` is a link to the real `sge`.

A lexical `normalize` keeps the link; `Files.walk` follows it. So `startsWith` between the two
matches nothing, silently, and the code around it looks correct:

- `PortRun.converted` decides which units a run OWNS by "under `sourceRoot`, not under a
  `resolutionRoot`". Compared lexically it classified every unit as owned and wrote **635 files
  instead of 30** — the whole resolution root re-emitted into the test source set.
- `CheckReport.relativise` hit it as a diff that was deterministic but *different* from the
  baseline computed in the primary checkout — a stack of `..` segments that depends on where the
  link lives.

So: realpath both operands before comparing, and fall back to `normalize` only when the path does
not exist (a synthetic origin, a directory not yet created). Note the failure is invisible to a
compile and to every count except the one it inflates — the port still compiled, and the tests
still passed, on 635 files.

## 5.5 Emitted code is a BUILD PRODUCT — `src_managed/`, never `src/`

Every port writes its generated Scala to `<port>/src_managed/{main,test}/scala`, which is
gitignored and deleted by `sbt clean`. `src/` holds only the hand-written part of a port — the
shims and overrides a library needs and the engine cannot derive.

This is the layout a target project is meant to use, so the engine must produce it, not
approximate it. `SbtGen.managedMain` / `managedTest` give the paths; `SbtGen.emit` writes the
`.gitignore` and the `sourceGenerators` + `cleanFiles` settings that make sbt see the directory
and `clean` remove it. Never hardcode an output path in a corpus migrator.

The reason is diagnostic, not tidiness: emitted code is reproducible from the Java sources plus
the manifest and is invalidated by every engine change. Committed alongside `src/`, a `git status`
can no longer distinguish a DECISION from an ARTEFACT — which is the single thing this project's
measurement discipline (§5) depends on being able to see.

## 6. Scala 3 output constraints

- **Never cast to `scala.Nothing`.**
- Vararg spread is `args*`, never `: _*`.
- Emit **fully-qualified names, no imports**, for the structural phase — this deletes the whole
  import-decision bug class. Human-readable imports are a separate, optional beautification backend.
