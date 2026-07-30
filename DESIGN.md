# Baltic Porter — design

**What the engine is, and why it is built this way.** This is the only design document. Its
companions each answer a different question and none of them repeats this one:

| document | answers |
|---|---|
| `CLAUDE.md` | what you MUST do — the governing rules for all porting work |
| `ENGINE-LIMITS.md` | what has already been TRIED and measured worse, with its number and its §1 kind |
| `PROGRESS.md` | the STATE of every port and of publishability |
| `.claude/skills/**` | a procedure, e.g. adding a library to the corpus |
| `.claude/agents/**` | what a reviewer must hunt for |

Section numbers here are cited from code comments; keep them stable.

---

## 1. Rationale — why a deterministic re-compiler

### 1.1 The verdict this project was founded on

The architecture is not novel — it is the *convergent* architecture every production transpiler
independently arrived at — and this project starts with two assets almost nobody else has:

1. **A written rule catalogue.** sge and ssg already document the transformation set as prose
   (`docs/contributing/conversion-rules.md`, `conversion-rules-java.md`, `type-mappings.md`,
   `control-flow-guide.md`, `nullable-guide.md`). The engine's rule set is largely a formalisation
   of documents that already exist.
2. **A validation corpus of ~2,600 audited file pairs.** Both repos keep pinned upstream sources in
   `original-src/` and a per-file manifest mapping each Java file to its Scala port *with the exact
   upstream commit it was synced against*. Every accepted, audited port is a golden test: run the
   engine on the original, diff against the accepted port, and every discrepancy is either a missing
   rule or a rule bug. That turns "is the transpiler correct?" from a trust problem into a
   measurable convergence metric.

**Determinism is not an aesthetic preference.** The documented failure catalogue in ssg's own
remediation plan (migration rows marked `ported` with no file, `full-port` covenants on 23 %-coverage
files, 1,507 tests pinned expected-fail so CI reads green, fake "API not implemented" comments,
audits later retracted as wrong) is mirrored in every LLM translation project that published data:
the Luau→Rust project measured its best volume model at **76.9 % "it compiles" acceptance but only
24.6 % survival** against real test oracles. A deterministic translator eliminates precisely that
silent-divergence taxonomy — the same defect class `CLAUDE.md` §3 and §4.4 are written against.

### 1.2 The four load-bearing lessons from prior art

c2rust, j2objc, j2cl and IntelliJ's nj2k all have the same four-stage shape (frontend with full type
attribution → own IR → many small passes → backend). Four of their lessons are structural
commitments here:

- **A real compiler frontend with full type attribution is non-negotiable.** Syntax-only conversion
  is what killed Scalagen, the most serious prior Java→Scala attempt. Overload resolution, boxing,
  `==` vs `equals` and SAM targets all depend on resolved static types.
- **Insulate behind your own intermediate tree.** j2objc and j2cl both survived a complete frontend
  swap (JDT→javac) because passes never touched the frontend's AST directly. Here: `frontend-spoon`
  is the only module that sees Spoon types (§3.2).
- **Trivia is first-class IR from day one** if the output is adopted as source. c2rust *lost* comment
  preservation when it moved to `syn` and never got it back. See `CLAUDE.md` §4.58 and §7.3 below.
- **Per-unit translation, with the target compiler as the link-time gate.** j2cl compiles files
  individually and defers whole-program checking to Closure; `scalac` on three platforms is that gate
  here for free.

Meta's Kotlinator (40k+ files Java→Kotlin) adds the decomposition this project follows: *pre-normalise
in the source language, post-normalise in the target language, keep the core deterministic.*

### 1.3 Where LLMs remain, and where they do not

- **Yes:** writing and extending rules, vocabulary tables and dispositions — validated by the golden
  corpus, so a wrong rule is caught by a diff and not by another agent's opinion; proposing
  dispositions for external libraries; idiom polish *after* the deterministic bulk with tests green
  throughout.
- **Never:** translating a unit ad hoc, stamping audit status, or editing generated output in place.

### 1.4 Toolchain choices

**Java frontend: Spoon** (ECJ underneath, so compiler-grade resolution; *shadow classes* expose
classpath/JAR types through the same `CtModel`; `CtComment` attached to elements). Use full-classpath
mode, never no-classpath — the fallback `lenient` mode silently shadow-resolves anything it cannot
see, and `ENGINE-LIMITS.md` G14 records what that costs. Rejected: JavaParser + SymbolSolver (a
home-grown JLS reimplementation — the resolver is the weak link and disqualifying for a type-driven
transpiler); javac's Tree API (**discards `//` and `/* */` at the lexer**); tree-sitter-java (no
types). Eclipse JDT Core remains the drop-in fallback behind the same interface.

**Scala side: emit source text from the typed IR, not Scalameta trees.** The original design routed
emission through Scalameta + a pinned scalafmt. What shipped is `scala-emit`'s `TirEmitter`, a direct
typed-tree→source backend, for the reason §2 gives: emission is a *backend*, and the tree that
carries types and symbols inserts the correct form by construction. The Scalameta option remains a
possible second backend; nothing in the pipeline depends on it.

**Engine host language: Scala 3 on the JVM.** Spoon is a Java library, so both sides load in one
process. The engine itself never cross-compiles; only its output does.

### 1.5 Keeping a port fresh — regenerate always, override replayably

Textual patch queues over generated text are the documented pain case (quilt fuzz-rejections; Brave
hand-resolving failed patches on every Chromium bump). The winning precedent is
**regenerate-always + a replayable override layer** — Google's OwlBot/synthtool regenerates client
libraries per upstream commit and re-applies handwritten deltas as a *program*, never as diffs
against generated text. "Do not edit generated code by hand" is the contract that makes upstream
bumps cheap, and it is why `CLAUDE.md` §5.5 puts emitted code in `src_managed/`. Deterministic
output is the enabler: regeneration diffs become reviewable and the override layer stays stable to
author against.

### 1.6 Verification, ordered by oracle strength

1. **Golden-corpus convergence** — engine output against the ~2,600 accepted hand-ports, diffed
   modulo the manifest's recorded idiom decisions.
2. **Structural API parity** — public-surface comparison of original against translated, *computed*
   rather than stamped by an agent. Nothing can be omitted silently.
3. **Compile gate** — scalac per platform per module. Weak as a *correctness* oracle, perfect as a
   *link* check. Read `CLAUDE.md` §3 before treating it as more than that.
4. **Ported upstream tests** — test sources go through the same engine. The only behavioural
   evidence this project can have.
5. **Differential harness** — original Java on the JVM is a cheap, perfect oracle. Property-based
   differential tests on value-level APIs; established Scala practice (Scala.js and Native validate
   their javalibs exactly this way).

---

## 2. Product stance — a re-compiler, not a transpiler plus pretty-printer

Baltic Porter's product is **not** "faithful Scala-like-Java, pretty-printed deterministically". It
is a **re-compiler**: Java is recompiled into a **typed, symbol-resolved, whole-program Scala tree**
on which **project-owned transformers** run — scalafix-style but richer and owned — to emit a
**refactored, migrated** project. Emission (source, later TASTy) is a decoupled backend, not the
driver.

The transformers are customised per target and are the point of the tool.

### 2.1 The four production transforms that define the requirement

Real cases from sge/ssg. None is textual; all are whole-program, type- and symbol-driven; all must
run **before** emission, because some rewrites are impossible to recover post hoc.

| transform | what it does |
|---|---|
| `GlobalsToImplicitsTransform` | globals → implicits: thread a `using` parameter through every method that transitively reaches a global. A call-graph rewrite; the `ResearchPlugin` case, since it needs the call graph before rewriting |
| `IntToOpaqueTransform` | `Int` → opaque type + companion: retype a semantically-tagged value everywhere it flows, wrap its construction sites. Seed detection is flow propagation — a union-find over the whole-program reference graph from a small HINT set |
| `CollectionsTransform` | Java collections → Scala, leaner where possible: retype + API-map every usage site of a collection symbol |
| `PanamaFfiTransform` | Panama FFI generation: `native` methods → `java.lang.foreign` downcall bindings for JVM and Scala Native linkers |

Each is verified emitting scalac-compiling Scala by its own spec. That the tool must **own** this
layer (rather than delegate to scalafix) and that the tree must carry **more** than a Scala-2.13
semantic AST does are the two requirements everything below follows from.

### 2.2 Why a string-oriented IR is the wrong substrate

The original Bridge IR (BIR) is a lossy, per-unit, **string-oriented** projection built to feed a
deterministic printer: qualified-name *strings*, `owner` *strings*, types attached only where a print
rule needed one. There is **no symbol table, no cross-unit reference index, no whole-program view**,
and it discarded types it had. A string IR can never support symbol-driven, whole-program
refactoring. Pretty-printing and determinism are backend concerns that were mistaken for the
architecture.

The information already exists one stage upstream: Spoon's `CtModel` is a fully typed,
symbol-resolved, position-carrying, cross-referenceable Java tree. The fix is to **stop collapsing to
strings**.

> BIR is **frozen, not deleted** — headers on `Bir.scala` / `SpoonFrontend.scala` /
> `ScalaPrinter.scala` say so and name its remaining corpus dependents. See `PROGRESS.md`
> §Publishability for what that costs.

### 2.3 The owned typed IR (TIR)

A Scala-shaped, whole-program, typed tree with a real symbol model, populated by **leveraging Spoon's
resolution** — the engine does not re-implement type resolution, it translates Java types and symbols
to Scala ones.

**Symbols.** Every declaration (class, trait, object, method, ctor, field, param, local, type param,
package) gets a `Symbol` with **stable identity** (an interned `SymId`, not a string), its `TypeRepr`,
its `owner`, `flags`, an `Origin`, and an open slot for **domain tags** (`SymTag` — e.g. "this `Int`
is a GL layer", or `Substituted`). Every reference node points to a `Symbol`. That is what makes
`usagesOf(sym)` possible and what makes rewrites **bump-resilient**: they key on symbol identity, not
text, so an upstream rename or reflow cannot break them.

Two consequences the codebase depends on:

- an **external** symbol is interned lazily with `owner = SymId.None` and no `Definition`, so
  ownership is a structural question — `CLAUDE.md` §4.56's "a symbol is owned iff climbing its owners
  reaches a `program.units` symbol";
- an external **member** must still carry its owner, or every member-keyed rule is silently blind
  (`ENGINE-LIMITS.md` P4).

**Types.** A **structured** Scala type algebra (`TypeRepr`) — not Java's, and never a flat string that
collapses type structure. It represents as addressable nodes: applied type constructors;
intersections and the `parents` linearisation; self-types; type-parameter bounds including F-bounds
(`T <: IRichSequence[T]` is a param whose bound references its own symbol); variance; higher-kinded
params; wildcards (`TypeBounds`); path-dependent and singleton types; and method/poly signatures.
Every tree node carries its type, resolved from Spoon, so transforms and emission **never re-infer**.
Type references point to a type-*symbol*, so "find all usages of `java.util.List`" includes type
positions, not just calls.

**Trees.** A typed Scala tree (the `tpd.Tree` analogue): `ValDef`, `DefDef`, `Apply`, `Select`,
`Ident`, `New`, `Lambda`, … Each node has `tpe`, an optional `symbol`, and an `Origin`.

**Program.** `Program` holds all units + the `SymbolTable` + a kinded `XrefIndex` (symbol →
definition, symbol → all usages *by kind*, method → callers), built once over all Spoon units.
`usagesOf`, `definitionOf`, `callersOf`, `symbolAt`, `typeOf` are the query API the transformers run
against. The index is rebuilt between phases, so after a phase rewrites the tree the old symbol drops
to zero usages and the new one inherits the exact positions.

**Provenance.** Each node carries its Java origin (path + position) and, after emission, its Scala
position — which is what makes every diagnostic attributable (`CLAUDE.md` §5.1) and every generated
file able to carry its licence header (§4.57).

### 2.4 Transform API — shaped by Scala 3's compiler-plugin model

`Phase` ~ `PluginPhase`/`MiniPhase`, with `runsAfter`/`runsBefore` ordering and `transformX` hooks
overridden only for the nodes a phase touches (the framework does the traversal and fuses them); a
full-control `run` ~ `ResearchPlugin` for whole-program analyses. Every hook runs with the
whole-program `Program` in scope, so a transform can ask `usagesOf` / `callersOf` / `symbolOf`
*while* rewriting — the thing Quotes and scalafix-over-SemanticDB cannot give across a program,
before emission. `Pipeline.order` is stable in declaration order; the xref index is rebuilt between
phases.

**Design anchors.** The tree/type/symbol model mirrors `scala.quoted.Quotes#reflect` (same shapes:
`TypeRepr`, `Tree`/`Statement`/`Definition`/`Term`/`TypeTree`, `Symbol`) so macro-literate authors are
immediately at home. The engine does **not** use Quotes directly — its contracts are hard to satisfy
outside a macro and its `Symbol` hides internals — it owns a close analogue that exposes everything
and adds `Origin`, `SymTag`, the whole-program `XrefIndex` and the decision log (§7).

There is **no registry, service loader or plugin descriptor**: a rule is a `balticporter.tir.Phase`
implementation passed to the run. `corpus-tests/.../GdxSharedIteratorRule.scala` is the worked example
of a §1(c) rule living outside the engine.

### 2.5 Emission

A backend walks the transformed typed tree → Scala source (and later TASTy). Because the tree carries
types and symbols, emission inserts the correct form by construction — the diamond and inference bugs
of the string printer cannot occur. Determinism, the comment invariant and API parity become backend
*verifications*, not the driver.

**Reference emission is fully qualified, no imports.** Every stable global reference (type, object,
static, top-level def) renders as its fully-qualified path — a context-free function of the symbol's
owner chain — and no `import`s are generated. This deletes the entire import-decision bug class
(import-vs-projection, class/companion shadowing, static-receiver qualification) at its root, and
de-risks transforms that mint symbols. Only two things stay unqualified: type parameters, and a type
declared in the unit being rendered. Class-nested types are `Outer#Inner`, object type members
`Obj.T` — those ARE the FQN for a nested type. Human-readable imports are a separate, optional
beautification backend, never a correctness prerequisite; a refinement still owes explicit handling
for givens and extension methods, which an FQN genuinely cannot name.

### 2.6 Semantic diff between two portings — planned, not built

The re-compiler must be able to produce a **semantic diff between any two portings of the same
project** — not a textual diff. Two axes:

- **Upstream drift** — when the original Java changes, diff the two TIRs for the semantic delta in
  Java terms (symbols added/removed/retyped, signature changes, call-graph edges gained/lost) *and*
  its projection onto the emitted Scala. Symbol-keyed identity is what makes this structural rather
  than a line diff.
- **Transform drift** — when a phase is developed or changed, diff the pre- and post-transform TIRs
  and their emissions, to see exactly what a phase did across the whole program. That is the
  reviewable unit of a migration.

Enablers already in place: stable `SymId`, `Origin`, the kinded `XrefIndex`, the immutable-rebuild
pipeline (each phase yields a fresh `Program`), and `TirPrinter.canonical`/`digest` — a rendering
with no `SymId` and no origin, so two runs are comparable. What is shipped today is the
*emission-level* half: `members.tsv` member digests and `srcmap.tsv` (§6.3). The TIR-level
`Program × Program → SemanticDelta` is future work.

### 2.7 North star

1. **Cover every ported library** — the TIR populates from every Java library sge and ssg port.
2. **Emit source** — the backend of §2.5, which is also what projects the semantic diff onto Scala.
3. **Agents take over library maintenance** — once the re-compiler round-trips (populate → transform
   → emit) and can semantic-diff two portings, per-library agents manage the ports from *their*
   repositories: pull upstream Java changes, re-run the transforms, review the semantic delta, land
   the migration. The re-compiler is the tool; the agents are the operators. `CLAUDE.md` §4.45 is the
   standing consequence of that.

---

## 3. Architecture

### 3.1 Product shape

**Baltic Porter is a framework (a set of libraries), not an application.** Each port is an ordinary
Scala 3 program depending on the framework: it declares its policy as a value and gets the whole
pipeline — analysis, translation, emission, verification, caching, upstream bumps — from the
framework's runner.

**End-to-end contract:** given a set of upstream modules and a port configuration, produce a
directory tree that is a complete, self-contained **sbt project** — build definition, main sources,
test sources, test resources, licence/NOTICE files — that compiles and passes tests on the platforms
the configuration declares (JVM always; JS/Native per configuration).

**Non-goals (v1):** input languages other than Java; emitting anything but Scala 3; in-place
refactoring of existing hand-written Scala; IDE integration; incremental *watch* mode (batch runs plus
cache only).

### 3.2 Module layout, and the insulation rule

```
balticporter/
  runtime/         // balticporter-runtime: the shims a PORT depends on (JavaIterator, …)
  core/            // TIR, phases, checks, manifest, substitutions, cache, port map
  frontend-spoon/  // the ONLY module that sees Spoon types
  scala-emit/      // TIR → Scala source (TirEmitter); the frozen BIR printer
  vocab/           // vocabulary model + Java→Scala stdlib tables
  sbt-gen/         // sbt project layout + build-definition emission
  verify/          // API parity, corpus diff, platform lint
  runner/          // PortRun — the one entry point
  testkit/         // golden-test harness for rule authors (used by ports too)
  corpus-tests/    // the framework's own acceptance ports against ../ssg, ../sge
```

Dependency directions: everything depends on `core`; `runner` depends on all; nothing depends on
`runner`. **`frontend-spoon` is the only module that sees Spoon types** — the insulation rule that
let j2objc and j2cl swap frontends whole.

`CLAUDE.md` §1's enforcement grep covers `core`, `frontend-spoon`, `scala-emit` and `runtime`: no file
in them may name a ported library in code.

### 3.3 The port as a VALUE — `PortManifest` and `PortRun`

A port program is **configuration only**. Everything mechanical — emission, the dropped-type skip,
the injection copy, the support-source write-out, every check, determinism, provenance, the
`src_managed` paths, the runtime dependency — belongs to `balticporter.runner.PortRun` and cannot be
opted out of.

That is not tidiness. Check invocation used to be copy-paste, and one migration program went its
whole life without ever calling `PortabilityCheck` as a result. `PortRun.RequiredChecks` is now
compared against what actually registered with `CheckReport`, so a number that reaches stdout and not
`findings.tsv` fails the run.

**The required set is NAMED, not derived.** The property being asserted is "the orchestrator invoked
all of them", and a list derived from what was invoked would assert nothing. Adding a check to the run
means adding it to the set, and forgetting fails the next run rather than shipping a silently narrower
report. Twelve are required unconditionally:

| | | |
|---|---|---|
| `signature` | `omissions` | `trivia` |
| `portability(all)` | `portability(emitted)` | `portability(injected)` |
| `substitution(emitted)` | `substitution(dangling)` | `remediation` |
| `policy` | `manifest` | `port-map` |

Three more record on every run that reaches them but are deliberately outside the set, because the set
is asserted against what actually recorded and a port without the relevant phase records nothing:
`porter-notes` (§7.2), `collection-closure` and `collection-boundary` (recorded only when the
collections transform is in the pipeline). They are made unskippable by their wiring living in the
orchestrator, not by the set. A port's own §1(c) rule may register a check of its own beside these.

`PortManifest` is the **shared-surface policy as an ordinary Scala value** — `name`, `governs`,
`dropTypes`, `dropMethods`, `packageRenames`, `surface`, `inject`, `bases` — composed with
`base.extendedBy(dependent)`. A manifest DSL would move the policy out of reach of the consumer's
compiler; a copied block of policy is not a mechanism but a habit, and it fails one module at a time.
`CLAUDE.md` §1.5 is the governing rule and states the inherited/not-inherited line; `ManifestAgreement`
is the check, in a static layer (declaration against declaration) and a dynamic one (the base's policy
against what this run actually modelled of the shared surface).

Things `PortRun` refuses rather than tolerates: a `PackageRenameTransform` passed in `phases` (it has
an ordering obligation `runsAfter` cannot state, so the run appends it last and verifies it); a
caller-supplied `externalConcrete` (derived from the phases by `RuntimePlan`); a dependent port that
declares no base.

**A port declares its own policy; it never edits the engine.** When a new rule is needed, decide its
`CLAUDE.md` §1 kind first — universal → engine unparameterised; same mechanics, different values →
engine with constructor parameters and an empty parameter meaning no-op; only ever this library → a
plugged-in rule in the port's own repository.

### 3.4 The anti-omission stance

**A construct the engine cannot translate faithfully is REFUSED and COUNTED — never silently
approximated, and never quietly dropped.** Diagnostics are values with stable codes, aggregated per
unit; a unit with errors fails the run. There is no "best effort" emission.

This is the stance `Frontend`, `OmissionCheck` and `SpoonTir.unsupported` are written against, and
§6 refines rather than reverses it: what is forbidden is **silent** best effort. A marker that blocks
the deliverable, prints on every run and fences its region in a separately labelled artifact is the
opposite of silence.

Its practical form is a rule about checks, stated in full at `CLAUDE.md` §3: every translation path
gets a check at the same time it gets a translation, the check walks the tree with
`StandardTraversal` rather than a private recursion, and a check that has never reported is not known
to work.

### 3.5 Graph, batching and unit mapping

From the resolved model: nodes = top-level types (plus synthetic nodes for merge groups), typed edges
(extends, implements, uses, calls, static access, annotation). Tarjan SCC → condensation → Kahn with
a lexicographic FQCN tie-break gives a *unique deterministic* order of translation batches; the same
machinery one level up orders modules. Java and Scala both tolerate mutual reference within a
compilation batch, so an SCC is simply one batch with a shared symbol environment — no forward stubs.

File mapping supports **N-to-1 merges** and **1-to-N platform splits** (`scala/`, `scalajvm/`,
`scalajs/`, `scalanative/` — the layout both target repos already use). Mapping entries beyond the
path convention — renames, merges, splits, whole-file overrides — are declared, not inferred.

### 3.6 Test porting

First-class, because the goal explicitly includes tests and because ported upstream tests are oracle
#4 (§1.6). `TestFrameworkTransform` is the phase; converting JUnit to MUnit is a **structural
transform, not an annotation rename** — `@Test def m()` in a plain class becomes a suite class plus a
`test("m") { … }` *statement*, which changes the shape of the file. The full JUnit→MUnit mapping,
including the argument-order reversal and the type-constrained `assertEquals`, is
`ENGINE-LIMITS.md` X1–X5; the semantics that do NOT survive a naive rename (`@Before`,
`@Test(expected=)`) are `CLAUDE.md` §4.4.

Two structural obligations:

- **Data-driven spec fixtures are not translated** — they are copied as test resources with a generic
  runner emitted once per suite family.
- **A skip/expected-fail ledger is DERIVED, never listed.** A test whose failure stack reaches a type
  in the port's `dropTypes` fails because the port deliberately does not have that type; the run
  writes those FQNs (in *both* namespaces) and the correlator classifies from them. A hand-maintained
  list of expected failures is exactly the thing that rots into "we always ignore those four" and then
  hides a fifth. `CLAUDE.md` §5.1 is the rule.

Per `CLAUDE.md` §1 this is **(a) universal** — every Java library ported to cross-platform Scala needs
it — so it belongs in the engine with the target framework parameterised. Honest label today:
**(b) with exactly one implemented policy value**, since `intercept` and the curried `test(name){body}`
application shape are MUnit facts baked into the phase.

### 3.7 External-dependency dispositions

The frontend resolves *everything* on the classpath, so the engine can report per module which
external types are referenced, by how many units, with which members. Every external symbol must have
exactly one disposition; unresolved ⇒ the run fails loudly. That is both the "do we need yet another
library" feature and the anti-omission gate at the same time.

| disposition | meaning |
|---|---|
| `PortInRepo` | becomes another port; the graph handles ordering |
| `MapTo` | API mapped onto an existing Scala/cross-platform library via a vocabulary table |
| `Shim` | handwritten companion implementation, never generated (the j2objc/j2cl `.native` pattern) |
| `PlatformProvided` | `java.*` covered by the JVM + javalib, checked against JS/Native coverage tables |
| `Drop` | with the unit exclusions it implies |

`vocab` ships the Java→Scala stdlib tables and the **platform coverage + lint data** (RE2 regex limits
on Native, missing `java.text`/`java.time`/`Locale`, no executors on JS, `@safePublish`, string
identity on JS), each lint keyed to the platforms the module targets.

**The distribution rule for anything the engine must supply itself:** semantics the target lacks
become a **published dependency** (`balticporter-runtime` — `JavaIterator`, `JavaIterable`,
`JavaCollection`, `JavaCollections`: a removal-capable iterator is genuinely absent from Scala);
shapes the engine can emit correctly are **emitted correctly and nothing ships**. Copying a shim's
source into each port's `src_managed` puts two divergent bodies at one FQN and is a hard error on the
Scala.js and Native linkers — which are the platforms the target repos exist for. Source emission
survives only as an explicit `RuntimeMode.Vendored` fallback for a standalone single-module port.
`ENGINE-LIMITS.md` K3 is the measured form of this rule.

### 3.8 Override layer

Replayable, never patches on generated text (§1.5), in descending order of preference:

1. **Rules / phases** — whole-pattern, survives upstream drift.
2. **Declaration overrides** — fragments spliced by symbol id; the engine verifies the overridden
   symbol still exists upstream and errors on a bump when it does not.
3. **Whole-file handwritten** — the unit is still parsed for resolution; only its OUTPUT is replaced.
   The file carries a `HANDWRITTEN OVERRIDE` header that is checked.
4. **Comment additions/edits** — keyed by symbol id, applied at emission.

All override applications are logged; orphaned overrides (target symbol gone) are errors, not silent
skips.

### 3.9 Upstream freshness

`bump`: diff the old and new pin → per-unit source-digest comparison → retranslate changed units plus
the interface ripple (early cutoff on interface fingerprints) → a declaration-level report of what
changed upstream, not merely that it changed → orphaned-override and disposition-gap check →
regenerate → gates → one reviewable diff of the generated tree. The pin moves only when the gates
pass.

### 3.10 sbt output contract

Generated tree per port:

```
<out>/
  build.sbt                       // generated Scala 3 build definition, with the engine pin
  project/build.properties        // sbt version pinned
  .gitignore                      // src_managed/ — emitted code is a BUILD PRODUCT (§5.5)
  NOTICE / THIRD-PARTY-LICENSES   // synthesised from upstream licence metadata
  <module>/src/main/scala/…              // HAND-WRITTEN shims and overrides only
  <module>/src_managed/main/scala/…      // everything the engine emitted
  <module>/src_managed/test/scala/…
```

`SbtGen.managedMain`/`managedTest` give the paths; `SbtGen.emit` writes the `.gitignore` and the
`sourceGenerators` + `cleanFiles` settings that make sbt see the directory and `clean` remove it. A
corpus migrator never hardcodes an output path. The module graph becomes `dependsOn` edges; external
Scala dependencies are computed from what the vocabulary actually mapped onto, so there are no unused
deps. Everything generated carries the do-not-edit provenance header, the build file included.

*Acceptance:* `sbt Test/compile` and `sbt test` succeed on the declared platforms with **zero manual
edits**.

### 3.11 Verification gates

Ordered by oracle strength (§1.6), all deterministic:

1. **corpus-diff** — engine output against accepted hand-ports, three-state per file (byte-equal /
   ast-equal / diverged), aggregated as a convergence percentage, with divergences individually
   classified: *missing rule* / *rule bug* / *hand-port idiosyncrasy to encode or accept*. The
   three-state split is what keeps a plateau from turning into rule contortions.
2. **parity** — structural public-API comparison of the source model against the emitted Scala tree:
   the *computed* covenant. Every upstream public symbol must be present, mapped, overridden, or
   explicitly dropped-with-reason. The comment-preservation invariant is checked here too.
3. **compile** — scalac per platform per module, in graph order, with errors mapped back to units and
   originating passes (§6.3). Read `CLAUDE.md` §3: this is a typer-only measurement until it reaches
   zero.
4. **tests** — run the ported suites per platform; the ledger is derived (§3.6) and ratcheted.
5. **differential** — original Java (JVM) against ported Scala on generated inputs for value-level
   APIs.

Determinism is itself a gate: `Determinism.Emission` runs on every port by double-translation, `Full`
behind a flag.

### 3.12 Cache

Standard, proven design — do not innovate here. **Action cache + CAS split**: content-addressed blobs
in a fan-out directory written to temp and atomically renamed; an action index mapping action-key →
output digests.

**Action key** = the engine fingerprint (version + ordered phase ids/versions + full configuration) +
the unit's canonical digest + the **interface digests** of every dependency unit + toolchain pins + an
explicit salt. Never mtimes; never unlisted environment.

**Early cutoff via interface hashing** (mypy's trick, Salsa's backdating): each translated unit stores
two digests — full output, and exported-API surface. Downstream units re-key only on the interface
digest, so a body-only upstream change retranslates one file.

Granularity is **coarse, per unit**. Fine-grained query-level incrementality is the only tier with a
documented soundness-failure history (rustc 1.52.1 globally disabled incremental over it) and is not
worth it for a batch tool. The expensive non-cacheable step is building the Spoon model per module;
scope model builds to modules whose file-set digest changed.

Determinism prerequisites, which also make golden-corpus diffs byte-stable: no timestamps in output,
stable iteration order everywhere (sorted collections in every phase), canonical serialisation. The
cache is advisory — a cacheless run must reproduce byte-identical output.

---

## 4. Semantic traps quick-reference

The short list every reviewer of engine output should know. The ones that translate to *valid* Scala
meaning something else are stated in full, with their faithful translation, at `CLAUDE.md` §4.4; the
ones that were measured and refused are in `ENGINE-LIMITS.md`.

1. Companion-object init is lazy vs Java's `<clinit>` eager-on-first-active-use — registries and
   `loadLibrary` in static blocks can silently not run. (`CLAUDE.md` §4.4, `static final` row.)
2. Auxiliary constructors cannot call `super` — constructor graphs need restructuring.
   (`ENGINE-LIMITS.md` §2.)
3. `boundary.Break` is a `RuntimeException` — broad catches inside a boundary must filter it.
4. Overload resolution differs (weak conformance dropped) — pre-resolve in the frontend and emit
   unambiguous calls.
5. Reference `==` → `eq`; boxed equality differs.
6. `match` has no fallthrough and throws `MatchError` where `switch` fell out silently; a Java label
   sits on ANY statement. (`ENGINE-LIMITS.md` §9.5.)
7. Statics are not inherited through subclass names; interface statics are not directly expressible.
8. Array covariance and raw types need inserted casts, not redesign — **erase uses, never
   declarations** (`ENGINE-LIMITS.md` G1).
9. try-with-resources suppression order: mirror javac's desugaring exactly, not `Using.resource`.
10. Cross-platform: RE2 regex on Native, string identity and number formatting on JS, `fullLinkJS`
    unchecked semantics, no executors on JS, `@safePublish` on Native, `java.text`/`java.time`/
    `Locale` via scala-java-time and scala-java-locales.

---

## 5. The port map — a module publishes what it DID, and dependents read it

### 5.1 The problem, from evidence rather than principle

A dependent module's frontend can only parse **Java**: it resolves against the base's *upstream*
sources, never against the Scala the base port emitted. So a dependent arrives at the base's decisions
by **re-deriving** them — it inherits the base's `PortManifest`, re-runs identically configured phases
over the same Java, and `ManifestAgreement` verifies that the two derivations agree.

That works and it caught real drift. But re-derivation has a ceiling, and it is only as good as the
phases being deterministic *and* identically configured. It answers "did we both intend the same
thing?" — never "what did you actually produce?"

The concrete case: Ashley's `ImmutableArray.toArray(Class)` forwards to `Array.toArray(Class)`, which
the base drops. That was found by an orphaned-call check *after* translating and emitting. With a map
of what the base produced it is a lookup answerable *before* translation begins.

### 5.2 The artifact

Each module, at the end of its run, publishes a **port map**: the correspondence between the upstream
Java surface and what this port actually emitted. Per type and per member, one of five dispositions:

| disposition | meaning |
|---|---|
| `ported` | translated mechanically; carries the emitted FQN + signature, which may differ from Java's |
| `renamed` | ported, at a different FQN |
| `substituted` | dropped and replaced at the same FQN by injected Scala |
| `dropped` | not emitted and NOT replaced — every reference must have been rewritten away |
| `added` | present in the port and absent upstream — an injected type, a support shim, a member a (c) rule introduced |

A member additionally records whether its **body** was substituted, because that changes behaviour
without changing signature, and a dependent's author needs to know the body they are calling is not
the upstream one.

**The engine already computes all of it** — the map is a *projection of artifacts that already exist*
(the source map, the substitution tags, the rename check's matched prefixes, the injection list, the
engine pin, the member digests), published in one file with a declared schema.

### 5.3 What it buys

1. **A dependent reads the base's surface instead of guessing at it.** `ManifestAgreement` stops being
   "did we configure the same thing?" and becomes "does what I am about to emit agree with what you
   actually emitted?"
2. **Call migration becomes mechanical.** `PortMapTransform(maps)` — a §1(b) phase, `Nil` a total
   no-op — re-points a renamed type by the same mechanism as the package rename (owned symbols,
   longest prefix, cut at a separator), reports a call to a dropped member or type with the base's own
   reason string attached, and reports a call into a body-substituted member.
3. **It closes three holes re-derivation admits to** — phase configuration, nested-type drops and
   emitted output all become observable, because the map records *output*, not *intent*.
4. **It is the hand-off artifact a multi-library corpus needs.** A published map per library makes
   ports composable without every agent reading every other agent's manifest — and it is checkable, so
   a base that changes its surface breaks its dependents *loudly* at their next run.

### 5.4 Discovery, freshness and ordering — as built

**Discovery.** `PortMap.discover(reportRoot, exclude)` scans the report tree and keys each map on the
`module=` field of its own header, not on the directory name — a report directory is named after the
migration PROGRAM and a manifest names the MODULE, and nothing enforces that those agree. `run-latest`
wins over `baseline`, so a dependent run in the same session as its base sees what the base just
produced; the committed baseline is the fallback for a fresh checkout. A module never reads its own
map.

**Freshness (R1).** The header carries a digest over `(path, sha256(file))` for every distinct Java
path the map attributes a member to — a file list *derived from the map itself*, so a consumer
recomputes the same digest with nothing to agree on beyond the map. Three answers, and the difference
between the last two is the point:

| answer | meaning | what the consumer does |
|---|---|---|
| `Fresh` | engine and sources match | uses the map |
| `Stale` | engine differs, or the base's Java has changed | **refuses** it, reports, re-derives |
| `Unverified` | no fingerprint, or sources outside this run's resolution roots | uses it, reports |

Three non-fatal but LOUD finding kinds keep the fallback from ever being silent: `BaseMapStale`,
`BaseMapUnverified`, `BaseMapMissing`. An **empty** base manifest — the documented way to declare a
resolution root that is not a ported module — is exempt and claims no namespace.

**Ordering.** A map-consuming phase runs **LAST** in a dependent's surface. It is a RESIDUE check,
exactly like `PortabilityCheck`: what is left once this module's own policy has been applied. Run
first on Ashley it reported 7 findings, every one of them a reference the next two phases immediately
repair; run last it reports what an agent must actually act on.

**Overload identity is the hard part.** A TIR symbol's `fullName` is `X#m` for *every* overload, so
arity is the whole discriminator; exact arity wins, and **no** arity match means no record rather than
the nearest one. `ENGINE-LIMITS.md` D1 has the number (263 → 8).

### 5.5 What is NOT closed

- **Member SIGNATURES are not compared.** A map's member key is the *emitted* signature with renames
  reversed, not the Java one, so a base that retyped a parameter publishes the retyped key. Comparing
  it against a dependent's Java-derived key would need the base's erasure re-derived — the thing a map
  exists to stop doing. So the hole is closed for anything that reaches a NAME and open for a retyping
  that changes only a parameter's type.
- **The diamond (R4) is untested.** The lookups merge N maps with the nearest base winning, but no
  corpus library yet has two bases sharing a third.

### 5.6 On storing the CONFIGURATION as JSON/YAML — a qualified yes

The **declarative half** of a manifest is data and can be config: `dropTypes`, `dropMethods`,
`packageRenames`, body maps, forwarder lists, class tables — all string-keyed values with no
behaviour, and the schema is the one the port map already needs. The **`surface` half is code and
cannot be**: a §1(c) rule's whole content is an invariant of one library's design expressed as a
traversal, and the moment a config format tries to express it, it becomes a plugin-loading
mechanism — precisely what §2.4 says this engine does not have and does not want.

Two arguments are commonly mis-weighted. *Stronger than it appears*: "Scala is typed and checkable" is
thin for exactly the parts proposed as config — a `dropTypes` key is a `String` either way, and a typo
is a runtime no-op in both forms, which is why `PolicyReport` had to be built at all. *Weaker than it
appears*: config does not **replace** the Scala manifest, it **adds** to it, since the phase list must
stay code — and two homes for policy is a cost.

**The port map largely dissolves the question.** The reason a dependent restates declarative policy is
that it has no way to learn it; once the base publishes a map, the dependent *reads* the drops and
renames as data rather than declaring them. Config is then worth having for the **base** module of a
library and for the parts an agent edits by hand, sharing the map's schema. Build the map first —
doing config first would standardise a shape the map is about to change.

---

## 6. Unportable constructs — markers, the failure report, best-effort emission

Governed by §3.4: what is forbidden is **silent** best effort.

### 6.1 Prior art: dotty's best-effort compilation, and where this differs

Dotty puts `ERRORtype` on the erroring tree — first-class in the tree and the `.betasty` format, never
a side table — keeps the erroring subtree with its position and message, writes degraded artifacts to
a separate directory with a distinct header so they never masquerade as real ones, and makes
consumption opt-in. All four are **borrowed**.

Two things are **rejected**. Dotty stops after the pickler because its error trees are ill-typed and
every later phase would need error handling; here an approximation carries a real type, so phases need
no special handling — and stopping would forfeit the whole-program transforms that might *fix* the
marked site. And dotty's error types poison parents through typing; here poisoning happens at the
**gate**, per emitted unit, because in-tree propagation destroys the precision the report needs and
would block transforms from reaching siblings.

Where this fundamentally differs: dotty is *recovering from errors in its own input*, discovered by
its typer, and can say nothing beyond "this did not typecheck". This engine is *recording constructs
it chose to translate approximately*, detected **before any Scala compiler sees the output**, while
holding the original Java. That buys three things dotty cannot have: a taxonomy of *why*; a statement
of what a hand-porter would write plus ranked remediations against the engine's own seams; and
**expected-error correlation** (§6.3).

### 6.2 The marker, and why it lives in the tree

- **Term level**: a wrapper node — a term translated only APPROXIMATELY, syntactically complete and
  semantically wrong, whose `inner` is the approximation and whose state `Open` means it MUST NOT
  ship. A smart constructor rejects a synthetic origin: a marker must point at real Java.
- **Definition level**: a `SymTag`, for findings whose subject is a declaration's *shape* rather than
  an expression — a constructor topology with no single-primary encoding, a signature that cannot be
  expressed.

Rejected alternatives, each for a reason this codebase has already paid for once:

- **A side table on `Program`.** Trees have no identity — the standard traversal rebuilds every node
  on every phase — so there is nothing stable to key on; keying on `Origin` collides on synthetic
  nodes; and a phase that deletes a subtree leaves a stale entry that either vanishes silently or
  falsely blocks. A marker whose lifetime is independent of the data it describes is exactly the
  defect class `CLAUDE.md` §3 is written against.
- **A `diag` field on every tree node.** Touches every construction site for a field that is empty
  almost everywhere; the codebase precedent is targeted carriage on the node that needs it.
- **Symbol tags only.** Term sites are many-per-symbol, and the fence, the error correlation and the
  diff all need the *exact expression* pinned.

The traversal gains one case so every phase's hooks reach *inside* an approximation. A phase that
pattern-matches for a specific shape simply fails to match a wrapped one and leaves it alone: **the
safe default is marker-preserved, code-untouched.** Erasing a marker requires deliberately matching it
and constructing a replacement — which is the point: discharge is an explicit act, `Open →
Resolved(byPhase, how)`, and a marker never leaves the tree until emission.

Taxonomy: `UnportableKind` is a **closed engine enum** — a new kind is an engine change that arrives
with its mint sites and its report text, which is the correct friction. Its members are derived from
what this codebase has actually produced (raw-generic conversion, context-dependent raw fill, JDK
boundary flow, constructor topology, platform-hostile API, reflective lookup, overload divergence,
frontend blind spot, unmodelled construct, annotation residue), and each carries default ranked
remediations classified (a)/(b)/(c) — with (c) ranking last unless the kind is inherently semantic.
Every string in a remediation is built from `Program` data at run time, so the mechanism stays
library-free and `CLAUDE.md` §1's grep gate continues to hold.

### 6.3 The report and the correlation — BUILT

Findings from every check plus the marker inventory assemble into one document per run, written to a
directory the porting program chooses:

- `report.md` — the operator document: location, Java excerpt, why it is unportable, what a hand-porter
  would write, ranked remediations.
- `findings.tsv` — one line per finding, sorted, path-relative and clock-free, so the diff is stable.
  Finding identity is a short hash of `(kind, javaPath, ownerFullName, detailDigest)`; the **line
  number is carried but is not part of the id**, so a whitespace edit upstream does not orphan a
  baseline entry.
- `counts.tsv` + `subject.txt` — the `before->after` fragment `CLAUDE.md` §5 asks every commit subject
  to carry.
- `members.tsv` — one digest per emitted member. Identical files mean the output is byte-for-byte
  unchanged, which is a stronger revert check than any count.
- `srcmap.tsv` — member → emitted line range → Java `Origin`.
- `tests.tsv`, `decisions.tsv`, `port-map.tsv`, `dropped-types.tsv` — §3.6, §7, §5.

`baseline/` is promoted only by an explicit accept, so an agent iterates against a fixed baseline and
promotes when a step is accepted.

Four decisions about the source map are worth keeping:

- **Positions are recovered by SEARCH, not by threading an offset.** One wrapper remembers the exact
  string each class-body member rendered to and the map locates those strings in the finished unit.
  Slots are reserved PRE-ORDER so a nested class precedes its own members, and the cursor advances one
  character past each match so two textually identical siblings resolve to two positions. The
  alternative — a cursor parameter on ~40 rendering methods — could not have been added without
  re-measuring the port; this one **cannot change a byte of output**, and a test asserts exactly that.
  A member the emitter renders but cannot find again is COUNTED and printed, never dropped.
- **The member key carries the parameter types** (`owner#name(T1,T2)`, the form `dropMethods` already
  uses). Java overloading puts eight `encode`s in one class and a key that merges them cannot say
  which one changed.
- **A class-body statement with no symbol gets an ordinal** (`owner#<stmtN>`). Not an edge case: the
  test transform lowers every `@Test` method to a bare statement, so without it a test file would map
  only at unit granularity.
- **The Java path is relativised against a root DERIVED FROM THE PORT.** A unit's origin ends in its
  own package path, so stripping that suffix *is* the source root — no flag, no script, the same
  answer from any checkout. (`CLAUDE.md` §4.6's last paragraph, applied where it was still open.)

**Correlation** joins compiler output and test-runner output back through the source map. Three lanes,
not two: at a **marked** region (classified, expected, carrying its remediation); at an **unmarked**
region (*engine gap*, auto-located to member and Java origin — the triage queue); and **unmapped** — a
diagnostic in a file the source map does not cover, such as an injected shim or a dependency. Folding
`Unmapped` into either of the others would be a lie in both directions. A **marked region with no
error** is a false-positive candidate, and an accidentally-compiling wrong approximation is precisely
the silent-defect class this whole document exists for.

**The test lane is the only one that sees `CLAUDE.md` §4.4.** Ten Java forms translate to valid Scala
meaning something else and move no error count; every one was found by running the ported tests. So
the same join runs over the test runner's output, anchoring each failure on the first stack frame in
ported code and **recording the quality of that anchor** rather than assuming it: `main-frame` (threw
inside the library — exact, the §4.4 case), `test-frame` (a plain assertion mismatch — where the
failure was *observed*, not where the wrong value was computed), `assert-site`, `suite`, `none`. Each
failing test is joined against the member-digest delta, so a newly-failing test whose anchored member
also changed digest is called out: that is the highest-value signal the engine produces.

*Demonstrated by deliberate breakage, twice:* removing `override` emission produced **916 errors, 916
located to a member and a Java line, 0 unmapped**; breaking the `static final` → `inline val` rule
produced **zero scalac errors** and was located to `Matrix4#<stmt1>` by the test lane alone.

### 6.4 Best-effort emission

Not a second code path. One emitter, one flag, three effects: `Open` markers render as the inner term
inside deterministic comment fences (comments cannot change program shape); each affected file gets a
banner naming the regions and their state against the baseline; output goes to a separate directory
with a sentinel file and the migration exits nonzero. In deliverable mode the gate runs first — any
`Open` marker and the deliverable tree is not written.

At zero open markers, best-effort output minus fences and banner is byte-identical to deliverable
output *by construction* (same emitter, same tree) — and a standing check asserts exactly that, so the
mode cannot rot into a divergent path.

The measure scripts have been running in unlabelled best-effort mode all along; after this the
"deliverable" claim becomes a *positive statement the gate makes* (zero open markers, every check
clean) rather than the absence of complaints. That is the mode a new library lives in for weeks.

### 6.5 Status and staging

Everything in §6.3 is **built**. The marker itself (§6.2) and best-effort emission (§6.4) are
deliberately **not**: the correlation lane already accepts a marker set and an empty one is a tested,
legal input, so the marker side only has to WRITE a `markers.tsv` of `unit<TAB>member` lines keyed the
way the source map keys members. The false-positive lane is one set-difference over the same two
inputs.

Risks and their cheapest falsifying experiments, kept because they are what the staging is for:

| risk | falsifier |
|---|---|
| a shape-matcher misses its shape through a wrapper | wrap every body term with a no-op resolved marker; assert emitted output and constructor plans byte-identical |
| marker flood — sites that actually compile fine | adopt one mint site, run §6.3; the marked-but-clean count IS the false-positive rate |
| nondeterministic artifacts poison every diff | run the migration twice, `diff -r` the two run directories |
| conservation false-positives on legitimate deletions | inject synthetic markers into members known to be dropped or replayed; each must report *discharged*, not *erased* |
| best-effort and deliverable output diverge | standing check, §6.4 |
| remediation templates accrete library knowledge into core | templates may interpolate only strings drawn from program/manifest data; the §1 grep gate catches a literal |
| finding-id churn from upstream line shifts | insert a comment line at the top of one Java file, re-run, count orphaned ids — must be zero |

---

## 7. Derivation provenance — why the emitted code is not a mechanical translation

The source map (§6.3) answers "which Java produced this line". It cannot answer "why is this type
simply absent, this package not the upstream one, this member from a hand-written file". Those are
**decisions**, and they are recorded twice: once in an artifact for an agent holding the run
directory, and once **beside the code** for the agent this engine actually has — one reading a single
emitted file in another repository (`CLAUDE.md` §4.45), whose question is asked at a line of Scala.

### 7.1 The decision log

Every non-mechanical thing a port does is a `balticporter.tir.Decision`, and its `Reason` is a
**constructor parameter**, never free text — because the first question an investigating agent has is
`CLAUDE.md` §1's: is this the engine's doing (a), a policy entry it can change (b), or a rule written
for one library (c)? A record that says what happened without saying which of the three it is costs a
full investigation to classify. Free text is still allowed; it goes in `detail("why")`, where it
cannot be mistaken for the classification.

| `Reason` | §1 kind | payload |
|---|---|---|
| `Universal(rule)` | (a) engine | `rule` names the Java/Scala fact, e.g. `java-static-inherited-constant` |
| `Configured(phase, key)` | (b) parameterised mechanism | `phase` is the mechanism, `key` is the manifest entry **verbatim** — together, exactly what an agent must edit to change the outcome |
| `LibraryRule(rule)` | (c) one-library rule | `rule` names it |

**`Decision.Kind` is a CLOSED enum** — an open string would make the artifact ungroupable and let two
deciders describe the same act two ways. Adding a case is one edit that forces the name to be agreed.
The thirteen: `RenamedType`, `RenamedPackage`, `RenamedMember`, `DroppedType`, `DroppedMember`,
`SubstitutedBody`, `InjectedMember`, `RedirectedCall`, `RetypedSignature`, `FunnelledCtor`,
`DroppedSuperCall`, `WidenedVisibility`, `Unrenderable`. Two of those pairs are deliberately *not*
merged: `DroppedSuperCall` is distinct from `FunnelledCtor` because one class may funnel successfully
and still drop one root's super arguments, and merging them would make "how many paths lost their
arguments" unanswerable.

Rules that are not bookkeeping:

- **A phase records with `Phase.record`; `Pipeline.runTraced` hands back the log.** The log is a value
  ONE RUN owns; each phase's own buffer is cleared before it runs and drained after, so a phase
  instance reused across two translations never reports the first run's decisions as the second's.
  Recording is cheap and unconditional — not gated on an artifact directory — so a phase can be tested
  on its decisions with no filesystem in sight. A phase **skipped** by a debug flag records nothing,
  which is the honest answer.
- **`subjectFqn` is the name the subject had WHEN THE DECISION WAS MADE.** A `SymId` is interning order
  and dies with the run; and re-deriving the name at write time would silently relabel every earlier
  decision into the emitted namespace, since the package rename runs last (`CLAUDE.md` §4.56). Position
  is read from the TREE, never from the symbol — `Symbol.origin` is not populated by the frontend, so a
  decision anchored on it would write `<synthetic>` in the one column that makes the row navigable.
- **Every decider records at the DECLARATION level** — one row per declaration whose emitted form the
  decision changes, never one per expression. A site-level rewrite is already visible in the diff the
  reader is holding; what the diff cannot say is which policy entry produced it, and that is one fact
  per (declaration, key). Recorded per site it would be the same sentence 240 times, burying every
  decision that is not a redirect. The enclosing declaration comes from the **xref**, because a phase
  tracking "the definition I am currently inside" with its own walk would be the hand-rolled traversal
  `CLAUDE.md` §3 forbids. Parameters, type parameters and locals are filtered out **structurally**
  (from the owner chain, not from an `isParam` flag locals do not carry): a method's `info` carries its
  parameter types, so a parameter whose type moved moved the method's signature and is one decision,
  not two.
- **The artifact is scoped to THIS MODULE's declarations, and the rest are WITHHELD.** A dependent's
  `Program` contains its base, so every phase decides about the base's units too. Unfiltered,
  `libgdx-test` published 634 `RenamedPackage` rows of which **605 were libGDX core's** — the same
  rows, byte for byte, that the base's own artifact already carries, in a file whose reader is looking
  for the 29 that are the test module's. This is `ENGINE-LIMITS.md` D2's fifth instance, and its
  conclusion is not "annotate them": a report a repository cannot act on is not its report. Withheld
  rather than sectioned, because a second section would still be read past, still be diffed, and still
  make "how many decisions did this port make" a question with two answers. **The count is printed on
  every run**, so "withheld" can never be mistaken for "none were made". Ownership is decided
  structurally (§4.56), never from the origin path — that is the lexical comparison §5.4 documents as
  broken across a symlinked worktree. A row with no subject at all is a statement about a policy KEY
  and is always the run's own.
- **The artifact sorts on every column it writes**, because it is meant to be diffed and accumulation
  order is phase order, i.e. an implementation detail. It is written **even when empty**: a header-only
  file says "this port recorded no decisions", where a missing file cannot be told from a run that
  never got that far.
- **An artifact write is gated on the artifact layer, without exception** — a gate at the write, not
  in each caller, because a wrapper every spec must remember is a wrapper one spec will not.

**One row per DECLARED policy key, not per key that fired.** A key that matched nothing is a decision
the run made and failed to carry out, and the row says so.

### 7.2 The porter note

The same fact is emitted beside the code in one grammar, whose full specification is `CLAUDE.md`
§4.575:

```
/* porter: <kind-slug> k=v … — <free text> */
```

`<kind-slug>` is the decision kind in kebab case — the enum, never a string a decider chose. The pairs
carry the §1 classification first, because which repository the fix lives in is the reader's first
question. `grep -rn '/\* porter:' src_managed` is the complete inventory of non-mechanical translation
in a port.

Three rules that are not style: notes are **DERIVED, never authored** — the emitter renders only
decisions whose subject it is emitting and invents nothing, which is what keeps the two artifacts from
being able to disagree and what makes the coverage check a real check rather than a tautology;
**original trivia first, note last, member next** (the upstream comment is what a licence obliges the
port to reproduce, and a note above it displaces it); and a note **may never open or close a
comment**, because Scala block comments nest. Every rendered value is neutralised (`/*` → `/ *`) rather
than rejected — a value that cannot be rendered safely is still information — and any value containing
whitespace is **quoted**, because the pair list is whitespace-separated and an unquoted
`key=com.badlogic.gdx -> sge` is three tokens that every reader silently truncates.

**Ten of the thirteen kinds carry a note.** The line is drawn at *would a reader of this line be unable
to explain it from the line itself*. A rename, a drop, a substitution and an injection all leave the
emitted code saying something the upstream Java does not, with no local evidence of why. The three
excluded are not oversights:

| excluded | why |
|---|---|
| `RetypedSignature` | the new type is written in the declaration; a note per retyped member is 335 comments on libGDX core restating what the signature already says, and the noise would bury the ones that carry information nothing else does |
| `RedirectedCall` | recorded per declaration, and the rewritten call is right there in the body |
| `FunnelledCtor` | the emitted class has one primary and N secondaries — that IS the funnel, in the code. Its *escaping* paths are a different matter and are a finding, not a note |

Where a kind's note goes is machinery, not taste: **at the declaration** (the subject is emitted);
**in the owning type's body**, at its head, for a dropped MEMBER (there is no `def` to sit above, so the
note goes where a reader looking for the member will find it); **not in the tree at all** for a dropped
TYPE, whose note is carried by the INJECTED file that supplies its FQN, prepended at copy time and
never written back to the overrides directory. A kind in the wrong set is a note that never appears.

`NoteCoverageCheck` fails the run in **both** directions, per unit rather than globally — a global
count balances a note invented in one file against a note lost in another, which is the shape of check
that reports zero forever:

| direction | why it is invisible otherwise |
|---|---|
| a decision with no note | the emitter renders notes at the declaration sites it knows about; a subject emitted through some other path simply produces none. The output compiles, every count is unchanged, and the TSV still has the row |
| a note with no decision | some call site printed one from a local condition rather than from the log — policy smuggled into the emitter, which would read to an agent as authoritative |
| a note recorded but absent from the emitted file | a rendered note dropped by the code that assembled the member |

**It joins on `SymId`, never on a name.** Three of the emitter's own passes rename the symbol before it
is rendered, so a name-keyed join is empty on exactly the decisions the check exists for.

### 7.3 Comments are part of the port

The upstream licence lives in a comment, and the generated banner does not replace it: the banner says
what the file is, the notice is the thing the licence obliges a derived work to reproduce. The rules
are `CLAUDE.md` §4.58 in full; the design commitments are:

- **Slice verbatim from the source buffer; never re-print.** A parser's `toString` reflows the body and
  loses the alignment of a `<pre>` block — fine for prose, not for a legal notice.
- **One comment, one home**, enforced by a claimed-identity set, because harvesting is layered and a
  coarse harvest must skip what its children took.
- **Indent is re-derived; text is not.** A port is regenerated on every engine change, and a diff that
  moves because a comment re-wrapped is a diff nobody reads.
- **The check compares SOURCE TEXT to EMITTED TEXT, never the tree.** Counting harvested nodes proves
  the frontend harvested and proves nothing about the emitter — and the frontend's own notion of
  "every comment" is the parser's attachment model, which is exactly what may be incomplete. So an
  independent lexer re-reads the Java and the check looks for each comment's normalised body in what
  the run actually WROTE. Nothing else in the pipeline can fail when this feature regresses: the output
  compiles perfectly with every comment gone, no count moves, and no test breaks.

Three details that make the comparison honest rather than merely present: it matches on **normalised
body text** (delimiters, gutter and indentation removed) because the emitter deliberately re-indents;
it groups **by Java file, not by unit**, because a Java file with two top-level types becomes two Scala
files and a comment "missing" from one is present in its sibling, so the concatenation of everything
that file emitted is the only honest right-hand side; and an unreadable source is reported as **nothing
at all** with a separate coverage denominator, because a check that silently scores an unreadable file
as clean is worse than one that admits it saw fewer files.

A finding is a §1(a) ENGINE gap — a Java position with no harvest point, or an emission path that
renders a node without its leading trivia. It is never a policy question. The one exception is a member
the port drops on purpose: its Javadoc goes with it, which is why only the units actually emitted are
passed in.

### 7.4 Preview mode — a diagnostic, not an emission strategy

Where the engine has no faithful Scala it refuses and carries a NUMBER (`ENGINE-LIMITS.md` M6), leaving
a residue comment. That is right for a port that ships. It is wrong for the first week of a NEW library,
where the operator is an agent in another repository (`CLAUDE.md` §4.45) that has to *find* the residue
before it can act on it — and a residue that compiles perfectly is exactly what it cannot find.

With `preview` on, each such site becomes a `scala.compiletime.error` naming four things, all mandatory
and in this order: **what** the construct is, **why** it has no faithful translation, **what an agent
must do** about it, and the **upstream origin**. The third is the one an error message almost never
carries, and its absence is what costs a full investigation. The port deliberately does not compile,
and those errors never mix with real ones — the correlator classifies them by the message the engine
itself wrote, ahead of the source-map lookup.

Default **off**: it changes the deliverable. `preview = false` emits the same bytes it always did,
which the member digests prove rather than this document.
