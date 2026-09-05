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

Deterministic re-compilation, validated against sge/ssg's ~2,600 audited Java→Scala file pairs as a
golden corpus: run the engine on the original, diff against the accepted port — every discrepancy is
either a missing rule or a rule bug. Determinism is not aesthetic: LLM-translation projects that
measured survival against real test oracles reported large "compiles but wrong" gaps (one Luau→Rust
project: 76.9% compiles, 24.6% survives). A deterministic translator eliminates that silent-divergence
class (`CLAUDE.md` §3, §4.4).

### 1.2 The four load-bearing lessons from prior art

- A real compiler frontend with full type attribution is non-negotiable (overload resolution, boxing,
  `==` vs `equals`, SAM targets all need resolved static types).
- Insulate behind an owned IR — only `frontend-spoon` sees Spoon types (§3.2).
- Trivia (comments) is first-class IR from day one, or it is lost for good and never recovered
  (`CLAUDE.md` §4.58, §7.3).
- Per-unit translation, target compiler as the whole-program link-time gate.

### 1.3 Where LLMs remain, and where they do not

- **Yes**: writing/extending rules and vocabulary tables (validated by golden-corpus diff), proposing
  per-library dispositions, idiom polish after the deterministic bulk with tests green throughout.
- **Never**: ad hoc per-unit translation, stamping audit status, editing generated output in place.

### 1.4 Toolchain choices

- Java frontend: **Spoon** (ECJ resolution; shadow classes expose classpath types). Full-classpath mode
  only, never no-classpath (`ENGINE-LIMITS.md` G14). Rejected: JavaParser+SymbolSolver (home-grown
  resolver), javac Tree API (drops comments at the lexer), tree-sitter-java (no types). Eclipse JDT
  Core is the drop-in fallback behind the same interface.
- Scala side: emit source text from the typed IR (`TirEmitter`), not Scalameta trees — emission is a
  backend, and the typed tree inserts the correct form by construction.
- Engine host: Scala 3 on the JVM; only the port's OUTPUT cross-compiles.

### 1.5 Keeping a port fresh — regenerate always, override replayably

Regenerate-always plus a replayable override layer (cf. Google OwlBot/synthtool), never textual
patches against generated text. Emitted code lives in `src_managed/` (`CLAUDE.md` §5.5) and is never
hand-edited; regeneration diffs stay reviewable and the override layer stays stable to author against.

### 1.6 Verification, ordered by oracle strength

1. **Golden-corpus convergence** — engine output vs. the ~2,600 accepted hand-ports.
2. **Structural API parity** — public-surface comparison, computed rather than stamped.
3. **Compile gate** — per platform per module; a *link* check, weak as a *correctness* oracle
   (`CLAUDE.md` §3).
4. **Ported upstream tests** — the only behavioural evidence this project can have.
5. **Differential harness** — original Java as a cheap, perfect oracle; property-based, value-level.

---

## 2. Product stance — a re-compiler, not a transpiler plus pretty-printer

Baltic Porter's product is a **re-compiler**: Java is recompiled into a typed, symbol-resolved,
whole-program Scala tree, on which project-owned transformers (scalafix-style but richer and owned)
run **before** emission — some rewrites are impossible to recover post hoc. Emission is a decoupled
backend, not the driver. The transformers are customised per target and are the point of the tool.

### 2.1 The four production transforms that define the requirement

Real cases from sge/ssg; none textual, all whole-program and type/symbol-driven, all run before
emission:

| transform | what it does |
|---|---|
| `GlobalsToImplicitsTransform` | globals → context: thread an anonymous `(using T)` through every declaration reaching a static holder, rewrite each read to a summon (§8.4) |
| `PrimitiveToOpaqueTransform` | primitive → opaque type + companion: flow-propagate from a hint set, retype, coerce at boundaries (§2.1.2) |
| `CollectionsTransform` | Java collections → Scala: retype + API-map every usage site (§2.1.1, §8.12) |
| `PanamaFfiTransform` | `native` methods → `java.lang.foreign` downcall bindings, JVM and Scala Native |

Each is verified emitting scalac-compiling Scala by its own spec.

### 2.1.1 Every RETYPING rule takes a SCOPE, and a scoped rewrite PROPAGATES

- **`RuleScope`** (`api`): `Everywhere(except)` / `Only(include)`, matched by FQN, cut only at a
  `Symbol.fullName` separator (§4.56). `Everywhere(Set.empty)` is the default and every phase's
  pre-scope code path (measured: 0 members changed over 600 files on adoption). A symbol is placed
  through its OWNER CHAIN, never by name alone (a parameter's own `fullName` is not stable). The scope
  is a set of ENTRIES, never a predicate — reportable as a `PolicyFinding` when an entry names nothing.
  A phase taking one implements `SurfacePolicy`.
- **`FlowPropagation`** (engine): union-find over pure-move edges read off the resolved TIR
  (assignment, `val x = ref`, `return ref`, arg→param) — arithmetic is deliberately NOT an edge (it
  breaks the chain, which is what makes the coercion have somewhere to go). An unknown node kind is a
  MISSED edge, never a spurious one.
- A scoped-out seam cannot be wrapped (no `mutable.Buffer` → `java.util.List`); it is COUNTED
  (`CollectionBoundaryCheck.Issue.ScopedOut`), read through the DECLARATION — a position-blind
  `transformType` has already remapped the node's own `tpe`, so a node-keyed check reports zero on
  exactly this seam. `Decision.Kind.ScopedOut` + a porter note carry the same fact where a kept type
  shows nothing in a diff.

### 2.1.2 `OpaqueSpec` — the opaque-type rule's policy, as one value

`PrimitiveToOpaqueTransform(spec: OpaqueSpec)`, `spec` in `api`:

- `fqn` **is** the definition site (object `spec.fqn`, type `<fqn>.T`) — no second "where" knob.
- underlying primitive: closed enum of the 8 Scala value types a Java primitive maps to.
- `hints` (port's seed set) / `extraHints` (agent escape hatch), both **exact FQNs** (`Set[String]`
  matched on `Symbol.fullName`) fenced by a `RuleScope` — not a predicate: unrenderable, invisible to
  `surfaceFingerprint`, refused by the `.conf` path (`ENGINE-LIMITS.md` §13 O4).
- Two instances overlapping on one symbol FAIL THE RUN (never a silent order-dependent loss).
- A retyping phase's four standing obligations, all shipped here: (i) a composite term's coercion
  reads the boundary through the DECLARATION, over enumerated node kinds — an unenumerated kind is a
  compile error, never a silent unwrap; (ii) a retyped parameter's `MethodType` slot moves in lockstep
  with its `ValDef`, by POSITION (`Symbol.descriptor` itself never moves — frontend-derived, no phase
  rewrites it); (iii) a hint the mechanism cannot reach (a container ELEMENT) is a reported `policy`
  finding naming `ENGINE-LIMITS.md` O3 — except one container-deep `Array[Prim]`, whose coercion is an
  erasure identity; (iv) a synthesised unit belongs to exactly the module owning the declarations it
  was minted FOR (`RunScope.emits`, read off `hints`), never every dependent in the chain — measured at
  24 errors / 6 stopped suites when unfenced (`ENGINE-LIMITS.md` §13 O5).
- Implements `SurfacePolicy` (fingerprints `fqn`, primitive, sorted hints/extraHints, scope); does
  **not** implement `MergeablePolicy` — no corpus dependent constructs the phase, so there is one
  instance; two same-name instances is a `SurfaceDivergence`, not a merge.
- Measured against sge's 39 hand-written `opaque type`s: 3 are within this mechanism's reach today
  (scalar field / scalar param-or-result / array element); ~17 have no java counterpart (n/a — nothing
  to seed); ~19 need mechanisms this phase does not have (an optional mint reusing an existing
  definition; a mint producing named-constant vocabulary beyond `T`/`apply`/`unwrap`).

### 2.1.3 Java primitives → Scala primitives is (a) UNIVERSAL, with nothing to scope

`int → Int` etc. happens unconditionally in the frontend (`SpoonTir.primName`) at type-reference
interning — no library could want a variant; "this int is really a domain value" is §2.1.2's phase,
not a variant of this mapping. Boxed types (`java.lang.Integer → Int`) are deliberately **not**
unboxed by the engine: a boxed value is nullable and a Scala value type is not
(`CollectionsTransform`'s `getOrElse(k, null)` on `V` depends on it) — unboxing wholesale would turn
`null` into `0` with a green compile, §4.4's defect class.

### 2.2 Why a string-oriented IR is the wrong substrate

The frozen Bridge IR (BIR) is a lossy, per-unit, string-oriented projection (qualified names as
strings, no symbol table, no cross-unit index, no whole-program view) built to feed a deterministic
printer — it cannot support symbol-driven whole-program refactoring. Superseded by the TIR (§2.3); BIR
is **frozen, not deleted** — headers on `Bir.scala`/`SpoonFrontend.scala`/`ScalaPrinter.scala` name its
remaining corpus dependents (`PROGRESS.md` §Publishability).

### 2.3 The owned typed IR (TIR)

A Scala-shaped, whole-program, typed tree populated by **leveraging Spoon's own resolution** — no
re-implemented type resolution.

- **Symbols**: every declaration gets a `Symbol` — interned `SymId` (stable identity, not a string),
  `TypeRepr`, `owner`, `flags`, `Origin`, an open `SymTag` slot for domain tags. Every reference node
  points to a `Symbol` (`usagesOf(sym)`; rewrites key on identity, not text — bump-resilient). An
  external symbol interns lazily with `owner = SymId.None`, no `Definition` — ownership is structural
  (§4.56: owned iff climbing owners reaches a `program.units` symbol). An external MEMBER must still
  carry its owner or member-keyed rules are blind (`ENGINE-LIMITS.md` P4).
- **Types**: a structured `TypeRepr` algebra — applied constructors, intersections/`parents`
  linearisation, self-types, F-bounds, variance, HK params, wildcards (`TypeBounds`), path-dependent
  and singleton types, method/poly signatures — never a flat string. Every node carries its resolved
  type; transforms and emission never re-infer. Type references point to a type-*symbol*.
- **Trees**: a typed Scala tree (`ValDef`, `DefDef`, `Apply`, `Select`, `Ident`, `New`, `Lambda`, …),
  each with `tpe`, an optional `symbol`, an `Origin`.
- **Program**: all units + `SymbolTable` + a kinded `XrefIndex` (definition / usages-by-kind /
  callers). `usagesOf`, `definitionOf`, `callersOf`, `symbolAt`, `typeOf` are the transform query API;
  the index rebuilds between phases.
- **Provenance**: every node carries its Java origin (path + position) and, post-emission, its Scala
  position (`CLAUDE.md` §5.1, §4.57).

### 2.4 Transform API — shaped by Scala 3's compiler-plugin model

`Phase` ~ `PluginPhase`/`MiniPhase` (`runsAfter`/`runsBefore`, `transformX` hooks overridden only for
touched nodes); a full-control `run` ~ `ResearchPlugin` for whole-program analyses. Every hook runs
with the whole-program `Program` in scope (`usagesOf`/`callersOf`/`symbolOf` while rewriting).
`Pipeline.order` is stable in declaration order; the xref index rebuilds between phases. The
tree/type/symbol model mirrors `scala.quoted.Quotes#reflect`'s shapes but is an owned analogue — adds
`Origin`, `SymTag`, the whole-program `XrefIndex`, the decision log (§7).

A rule is a `Phase` instance **passed to the run** — no plugin descriptor, nothing loaded by class
name in Scala code. §5.7 adds a `ServiceLoader` so a **config file** can *name* a phase already on the
consumer's classpath — narrow: nothing is instantiated from a name in a config unless a `.conf`'s
`surface` names it, and a Scala-authored `PortRun(phases = …)` still takes plain instances.

### 2.5 Emission

A backend walks the transformed typed tree → Scala source (later TASTy); because the tree carries
types and symbols, emission inserts the correct form by construction. Determinism, the comment
invariant and API parity are backend *verifications*, not the driver.

**Reference emission is fully qualified, no imports.** Every stable global reference (type, object,
static, top-level def) renders as its FQN — a context-free function of the symbol's owner chain — and
no `import`s are generated, deleting the whole import-decision bug class at its root. Only type
parameters and a type declared in the unit being rendered stay unqualified; nested types render
`Outer#Inner` / `Obj.T`. Human-readable imports are a separate, optional beautification backend, never
a correctness prerequisite.

### 2.6 Semantic diff between two portings — planned, not built

Two axes, both structural (symbol-keyed) rather than textual: **upstream drift** (diff two TIRs of the
same project across a Java change) and **transform drift** (diff pre-/post-phase TIRs and their
emissions). Enablers in place: stable `SymId`, `Origin`, the kinded `XrefIndex`, the immutable-rebuild
pipeline, `TirPrinter.canonical`/`digest`. Shipped today is the *emission-level* half only —
`members.tsv` digests + `srcmap.tsv` (§6.3). `Program × Program → SemanticDelta` at the TIR level is
future work.

### 2.7 North star

1. Cover every ported library — the TIR populates from every Java library sge/ssg port.
2. Emit source (§2.5), which also projects the semantic diff onto Scala.
3. Agents take over library maintenance from their own repositories, once round-trip and semantic diff
   exist (`CLAUDE.md` §4.45 is the standing consequence).

### 2.8 The difference catalog — the (a) layer, made explicit

Every Java-vs-Scala semantic difference the engine knows about is a **VALUE** in
`balticporter.catalog` (`api`), not a table in a document — citable by lowering arms/transforms/
checks, coverage-measurable, and shipped to `CLAUDE.md` §4.45's agent inside the engine jar. A
markdown table can do neither of the first two; none of the six §3.6 documents fits an inventory this
size; the closed-enum precedent is §6.2's `UnportableKind`.

- **Ids**: `JS-<AREA><NN>` (`E` expr, `S` stmt, `C` class, `G` generics, `L` library surface, `P`
  platform capability). Never reused or renumbered — `Differences.retired` keeps a retired id out of
  circulation.
- **No-parameter rule**: a `Difference` takes no per-library parameter — no `RuleScope`, no
  `Set[String]`, no predicate (enforced by reflection over every field, `DifferenceTakesNoParameterSpec`).
  A row needing library knowledge is a (b) phase's policy, not a catalog row. `JS-{L,P}` rows may carry
  per-**platform** maps (`by`, `verdict` — Scala.js/Native genuinely disagree) but never a target SET
  — that is the port's own manifest (`ApiRowCarriesNoPolicySpec`).
- **`PortabilityCheck(targets)`**: the `JS-{L,P}` consumer and §1(b) in its purest form — one rule
  list, one parameter (`PortManifest.targets`). Each rule CITES its row; the registry does not
  generate the rule list (would silently drop unsurveyed JDK/3rd-party rules — a lane reset, not a
  re-scoping). `PortabilityTargetsSpec`: a rule may not claim a platform its own cited row calls
  `Keep`. `Verdict.Depend` (API exists, in an undeclared artifact) is a BUILD-GRAPH fact, split out of
  `portability(*)` into `dependency-coverage`.
- **`dependency-coverage`**: a finding is *usage fired* ∧ *no declared dependency covers it* ∧ *no
  port-declared alternative* (`PortManifest.verdictOverrides`, read through `ApiRow.verdictOn`) — each
  conjunct separately observable. `DependencyCheck.unneeded` (declared, matched nothing) is the mirror,
  reported on the `policy` lane as `PolicyIssue.NeverApplied`. A `Depend` artifact's per-platform
  coordinate (`ArtifactDep`, e.g. `scala-java-time`/`_sjs1_3`/`_native0.5_3`) and resolver URL are
  checked against the real repository and recorded on the row / the `dependencies` entry — never
  inferred from the coordinate string (§4.56, `ENGINE-LIMITS.md` P8). A declared dependency lands in
  `deps` or `testDeps` by the run's own `SourceSet`.
- **Status enforcement**, both mechanical: (i) a row cannot claim `Open` while its `ENGINE-LIMITS.md`
  twin reads CLOSED, nor `Handled` while its twin reads Open (`ClosedTwinStatusSpec`); `Partial(why)`
  and `AMBIGUOUS` (twin closed on one face, open on another) are the stated escape hatches. (ii) a
  lowering/rendering consult citing an `Open`/`Absent` row is itself a finding.
- **`attaches`** — where the engine owes a decision for a row, and whether it took one — four
  discharge surfaces: frontend **lowering** (`Lowering.of`, wrapped at the DISPATCH, never inside an
  arm); a whole-program **pass** citation (`CatalogLog.cite`, per declaration, for non-phase passes);
  emitter **rendering** (`Rendering.of`, keyed on `Tree` kind); and **type** lowering/rendering
  (`Typing.ofReference`/`Typing.ofRepr` — a `TypeRepr` is not a `Tree` node, inherits the enclosing
  node's `Origin`). A row may attach at more than one surface (`Attaches.Both`).
  `Attaches.NoObligation(why)` marks a row with nothing to decide, apart from `Unmechanised` (no
  surface exists — a refused construct, measured through `SpoonKinds`/`markers` instead). What the
  wrapper guarantees: a difference cannot be silently UNCONSIDERED at an attached site — it cannot
  detect a WRONG consideration, which is the per-area edge-case suites' job.
- **Coverage**: `catalog(consulted|unreached|unmechanised|undischarged)` — all four required, on the
  trivia-family precedent (`unreached = 0` must not be achievable by declaring rows unmeasured).
  `catalog(uncited)` — registry rows with no Scala-side citation — is a fifth, derived from the
  registry, never asserted on (a spec silencing it by inventing a citation is worse than the gap).
  `just catalog-coverage` aggregates corpus-wide: a row unreached on one small library is normal,
  unreached on all is dead code or an untested rule. `just catalog` renders the (gitignored) markdown
  from the registry; committing it would be a seventh §3.6 document. No row carries a raw number —
  measurements live where §3.6 says and the row points at them with its `twin`.

---

## 3. Architecture

### 3.1 Product shape

Baltic Porter is a **framework** (a set of libraries), not an application. A port is an ordinary
Scala 3 program depending on the framework: declares its policy as a value, gets the whole pipeline
(analysis, translation, emission, verification, caching, upstream bumps) from `PortRun`.

**Contract**: given upstream Java modules + a port configuration, produce a complete, self-contained
sbt project (build definition, main/test sources, licence files) that compiles and passes tests on the
declared platforms (JVM always; JS/Native per configuration).

**Non-goals (v1)**: non-Java input; non-Scala-3 output; in-place refactoring of hand-written Scala;
IDE integration; incremental watch mode (batch + cache only).

### 3.2 Module layout, and the insulation rule

```
balticporter/
  runtime/         // shims a PORT depends on, cross-built JVM + Scala.js + Native
  api/             // the MODEL and CONTRACTS a rule author compiles against
  frontend-spoon/  // the ONLY module that sees Spoon types
  engine/          // transforms, checks, emitter, vocab, sbt-gen, verify, PortRun
  testkit/         // golden-test harness for rule authors
  corpus/          // the framework's own acceptance ports against ../ssg, ../sge
ported/            // one directory per ported library, named for the reference port's OWN
                   // module id (`CLAUDE.md` §2.1) — never the upstream library name
port-report/       // one directory per MIGRATOR CLASS — the measurement identity, stable
                   // across a `ported/` module rename
```

`ported/<module>`'s `label` and `PortManifest.name` carry the same string (`label` is what
`port-map.tsv` publishes as `module=`; `PortManifest.name` is what a dependent's base chain matches).
`ported/<module>` (where the code goes) and `port-report/<Migrator>` (whose measurement it is) answer
different questions and are not aligned by symmetry.

Dependencies: `api` depends on nothing; `frontend-spoon` on `api`; `engine` on both; `testkit`/
`corpus` on `engine`; nothing depends on `corpus`. `frontend-spoon` is the only module that sees Spoon
types (the insulation that let j2objc/j2cl swap frontends whole). `CLAUDE.md` §1's library-name
enforcement grep covers `api`, `engine`, `frontend-spoon`, `runtime`.

**The `api`/`engine` cut is operational**: a §1(c) rule and its spec, written by §4.45's consumer
agent, must compile against `api` alone — no emitter, no orchestrator, no Spoon. `api` therefore holds
the TIR (`Tree`, `Symbol`, `SymId`, `TypeRepr`, `Origin`, `Trivia`, `Program`, `Xref`), `Phase`/
`Plugin`/`StandardTraversal`/`Pipeline`, the decision model, the recording surface (`CheckReport`,
`PolicyReport`), the §4.6 debug surface, the frontend contract, `PortManifest`/`Substitutions`, and
`RuleScope`/`FlowPropagation` (package `balticporter.transform`, shared with `engine`). Everything
else — every transform IMPLEMENTATION, the emitter, `PortMap`, `Cache`, `PortRun` — is in `engine`.
Five non-obvious placements: `Pipeline` is in `api` (running phases over a `Program` is what a rule's
own spec does; the RUNNER, `PortRun`, is not); the frozen BIR model is in `api` so `frontend-spoon`
depends on `api` alone (its passes and printer stay in `engine`); `PortManifestConfig` (reads/writes a
`PortMap`, an artifact concern) is in `engine`, split out of `PortManifest.scala`; `engine` depends on
`frontend-spoon` because `PortRun` models a source set with `SpoonTir` — a second frontend is added
beside Spoon, never behind it; `FlowPropagation` is in `api` because a §1(c) rule needs it to grow its
own seeds without dragging in the emitter.

### 3.3 The port as a VALUE — `PortManifest` and `PortRun`

A port program is **configuration only**; everything mechanical (emission, drop skip, injection copy,
support-source write-out, every check, determinism, provenance, `src_managed` paths, the runtime
dependency) belongs to `PortRun` and cannot be opted out of. `PortRun.RequiredChecks` is a NAMED list
(not derived from what was invoked, which would assert nothing) and is compared against what actually
registered with `CheckReport` — a number reaching stdout and not `findings.tsv` fails the run. Its
size is not restated anywhere else (a second count is a count that goes stale); `CLAUDE.md` §5 names
every member. Some checks (`porter-notes`, and `collection-closure`/`collection-boundary`/
`collection-retarget` when `CollectionsTransform` runs) record on every run that reaches them but sit
outside the named set — unskippable by their wiring in the orchestrator, not by the set.

`PortManifest` is the shared-surface policy as an ordinary Scala value — `name`, `governs`,
`dropTypes`, `dropMethods`, `packageRenames`, `surface`, `inject`, `bases`, `targets`,
`verdictOverrides`, `dependencies` — composed with `base.extendedBy(dependent)` (full inherited/
not-inherited line: `CLAUDE.md` §1.5).

- **`targets`**: the §1(b) parameter `PortabilityCheck` reads (§2.8); NOT inherited (moves no emitted
  signature, only which findings are reported). Default is all three platforms. One fatal,
  one-directional constraint: `ManifestAgreement.Kind.TargetWidening` — a dependent may target FEWER
  platforms than its base, never MORE (a wider dependent depends on emitted Scala nobody checked
  against that platform, invisible to it under `ENGINE-LIMITS.md` D2's ownership filter).
- **`verdictOverrides`**: a port stating it ships its own shim/vendors/accepts a refusal — may never
  contradict `by`, the availability fact.
- **`dependencies`**: exactly one module's build file names each coordinate (`inject`'s line);
  `SbtGen` writes them at the run's own `SourceSet`; `dependency-coverage`/`policy` report the two
  directions of mismatch. Empty is still the default and the no-op.

`PortRun` refuses rather than tolerates: `PackageRenameTransform` passed in `phases` (it has an
ordering obligation `runsAfter` cannot state — the run appends it last); a caller-supplied
`externalConcrete` (derived from `phases` by `RuntimePlan`); a dependent declaring no base.

### 3.4 The anti-omission stance

A construct the engine cannot translate faithfully is **refused and counted** — never silently
approximated, never quietly dropped. Diagnostics are values with stable codes, aggregated per unit; a
unit with errors fails the run. §6 refines rather than reverses this: what is forbidden is *silent*
best effort — a marker that blocks the deliverable, prints on every run, and fences its region in a
labelled artifact is the opposite of silence. Practical form (`CLAUDE.md` §3): every translation path
gets a check when it gets a translation, walked with `StandardTraversal` rather than a private
recursion.

### 3.5 Graph, batching and unit mapping

Nodes = top-level types (+ synthetic merge-group nodes); typed edges (extends, implements, uses,
calls, static access, annotation). Tarjan SCC → condensation → Kahn with a lexicographic FQCN
tie-break gives a unique deterministic translation-batch order (same machinery orders modules). An SCC
is one batch with a shared symbol environment — no forward stubs, since both languages tolerate mutual
reference within a compilation batch.

File mapping supports N-to-1 merges and 1-to-N platform splits (`scala/`, `scalajvm/`, `scalajs/`,
`scalanative/`, the layout both target repos use). Renames, merges, splits, whole-file overrides are
declared, not inferred.

### 3.6 Test porting

First-class (ported upstream tests are oracle #4, §1.6). `TestFrameworkTransform`: JUnit→MUnit is a
**structural** transform, not an annotation rename (`@Test def m()` in a plain class becomes a suite
class plus a `test("m"){…}` statement). Full mapping and non-surviving semantics: `ENGINE-LIMITS.md`
X1–X5, `CLAUDE.md` §4.4. Data-driven spec fixtures are copied as test resources with a generic runner
per suite family, never translated. A test-class HIERARCHY anchors registration at the ROOT of the
program-declared chain, emitted ONCE as a `def` call (never duplicated per subclass — an MUnit
duplicate-name failure at run time). A skip/expected-fail ledger is DERIVED from `dropTypes`, never
hand-listed (`CLAUDE.md` §5.1). Classification: (a) universal mechanism, (b) with exactly one
implemented policy value (MUnit).

### 3.7 External-dependency dispositions

The frontend resolves everything on the classpath; every external symbol must have exactly one
disposition, unresolved ⇒ the run fails loudly.

| disposition | meaning |
|---|---|
| `PortInRepo` | becomes another port; the graph handles ordering |
| `MapTo` | API mapped onto an existing Scala/cross-platform library via a vocabulary table |
| `Shim` | handwritten companion implementation, never generated |
| `PlatformProvided` | `java.*` covered by the JVM + javalib, checked against JS/Native coverage tables |
| `Drop` | with the unit exclusions it implies |

`balticporter.vocab` ships the Java→Scala stdlib tables and platform coverage/lint data, each lint
keyed to the module's targeted platforms.

**Distribution rule**: semantics the target lacks become a published dependency
(`balticporter-runtime`: `JavaIterator`, `JavaIterable`, `JavaCollection`, `JavaCollections`); shapes
the engine can emit correctly are emitted and nothing ships. Copying a shim's source into each port's
`src_managed` puts two divergent bodies at one FQN — a hard error on Scala.js/Native linkers — so
source emission survives only as an explicit `RuntimeMode.Vendored` fallback for a standalone
single-module port (`ENGINE-LIMITS.md` K3). `runtime` is a `projectMatrix` cross-built over all three
platforms from one `src/main/scala`, publishing `balticporter-runtime_3`/`_sjs1_3`/`_native0.5_3` —
the JS/Native rows are the only instrument checking the module's "nothing JVM-only" rule, and what
they check is REACHABILITY at link time, not compilation (a per-MEMBER fact, not per-file).

**Only a BASE port may ship a new support type**: `RuntimePlan.of` derives required types from the
phases that ran, a dependent inherits its base's phases through `surface`, so a dependent's `required`
set already contains its base's — `RuntimeMode.Vendored` on a dependent would write a second copy of a
type the base already ships. Every dependent in this corpus is therefore `RuntimeMode.Dependency`
while its base vendors (`ENGINE-LIMITS.md` P10).

### 3.8 Override layer

Replayable, never patches on generated text, in descending preference: (1) rules/phases — whole
pattern, survives upstream drift; (2) declaration overrides — fragments spliced by symbol id, engine
verifies the symbol still exists upstream; (3) whole-file handwritten — unit still parsed for
resolution, only its output replaced, carries a checked `HANDWRITTEN OVERRIDE` header; (4) comment
additions/edits, keyed by symbol id at emission. All applications logged; an orphaned override (target
symbol gone) is an error, never a silent skip.

### 3.9 Upstream freshness

`bump`: diff old/new pin → per-unit source-digest comparison → retranslate changed units + interface
ripple (early cutoff on interface fingerprints) → declaration-level change report → orphaned-override
and disposition-gap check → regenerate → gates → one reviewable diff. The pin moves only when the
gates pass.

### 3.10 sbt output contract

```
<out>/
  build.sbt / project/build.properties / .gitignore / NOTICE / THIRD-PARTY-LICENSES
  <module>/src/main/scala/…              // HAND-WRITTEN shims and overrides only
  <module>/src_managed/{main,test}/scala/…   // everything the engine emitted
```

`SbtGen.managedMain`/`managedTest`/`emit` give the paths and write the `sourceGenerators` +
`cleanFiles` settings; a migrator never hardcodes an output path. Module graph → `dependsOn`; external
deps computed from what the vocabulary actually mapped onto.

**Generating the skeleton is OPTIONAL, off by default** — gated on one seam,
`PortRun.project: Option[SbtGen.ProjectSpec]` (default `None`). With it `None`, a run writes only the
SOURCES under `outDir` and its report directory; nothing else touches `portRoot`
(`PortRunProjectSpec` asserts the exact file set both ways). A caller controls `portRoot` +
`sourceSet` without opting in — an arbitrary directory plus the `src_managed/<set>/scala` suffix; no
second output-path knob exists.

*Acceptance*: `sbt Test/compile` and `sbt test` succeed on the declared platforms with zero manual
edits.

### 3.11 Verification gates

Ordered by oracle strength (§1.6), all deterministic:

1. **corpus-diff** — engine output vs. accepted hand-ports, three-state per file (byte-equal /
   ast-equal / diverged) with divergences classified (missing rule / rule bug / hand-port
   idiosyncrasy).
2. **parity** — structural public-API comparison, computed; every upstream public symbol present,
   mapped, overridden, or explicitly dropped-with-reason. Comment preservation is checked here too.
3. **compile** — scalac per platform per module, errors mapped back to units and originating passes
   (§6.3); typer-only until it reaches zero (`CLAUDE.md` §3).
4. **tests** — ported suites per platform, ledger derived (§3.6) and ratcheted.
5. **differential** — original Java (JVM) against ported Scala on generated inputs, value-level APIs.

Determinism is itself a gate: `Determinism.Emission` on every port by double-translation, `Full`
behind a flag.

### 3.12 Cache

Action cache + CAS split: content-addressed blobs, an action index (action-key → output digests).
**Action key** = engine fingerprint (version + ordered phase ids/versions + full configuration) + the
unit's canonical digest + interface digests of every dependency unit + toolchain pins + an explicit
salt. Never mtimes; never unlisted environment. **Early cutoff via interface hashing**: each unit
stores a full-output digest and an exported-API-surface digest; downstream units re-key only on the
interface digest. Granularity is coarse, per unit — fine-grained query-level incrementality is not
worth it for a batch tool (rustc 1.52.1's soundness-failure history with it). Model builds scope to
modules whose file-set digest changed. Determinism prerequisites: no timestamps in output, stable
iteration order everywhere, canonical serialisation; the cache is advisory — a cacheless run
reproduces byte-identical output.


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

A dependent's frontend only parses **Java** — it resolves against the base's upstream sources, never
against the base's emitted Scala — so it re-derives the base's decisions (inherits `PortManifest`,
re-runs identically configured phases, `ManifestAgreement` checks agreement). That answers "did we
both intend the same thing?", never "what did you actually produce?" (found via an orphaned-call
check on Ashley's `ImmutableArray.toArray(Class)`, which forwards to a member the base drops).

### 5.2 The artifact

Each module publishes a **port map** at end of run: upstream Java surface ↔ what this port emitted.
Per type/member, one of five dispositions:

| disposition | meaning |
|---|---|
| `ported` | translated mechanically; carries the emitted FQN + signature |
| `renamed` | ported, at a different FQN |
| `substituted` | dropped and replaced at the same FQN by injected Scala |
| `dropped` | not emitted and NOT replaced — every reference must be rewritten away |
| `added` | present in the port, absent upstream |

A member also records whether its **body** was substituted (behaviour changes without a signature
change). The map is a *projection of artifacts that already exist* (source map, substitution tags,
rename-check prefixes, injection list, engine pin, member digests), published with a declared schema.

### 5.3 What it buys

A dependent reads the base's actual surface instead of re-deriving intent; call migration becomes
mechanical (`PortMapTransform(maps)`, a §1(b) phase, `Nil` a total no-op, re-points renamed types by
the package-rename mechanism — owned symbols, longest prefix, separator cut — and reports calls into
dropped or body-substituted members with the base's own reason attached); it closes the holes
re-derivation admits (phase misconfiguration, nested-type drops, emitted output — all become
observable because the map records *output*, not *intent*); it is the corpus hand-off artifact — a
base that changes its surface breaks its dependents loudly at their next run.

### 5.4 Discovery, freshness and ordering — as built

**Discovery**: `PortMap.discover(reportRoot, exclude)` keys each map on its header's `module=` field,
never the directory name. `run-latest` wins over `baseline`. A module never reads its own map.

**Freshness (R1)**: header carries a digest over `(path, sha256(file))` for every Java path the map
attributes a member to, self-derived so a consumer recomputes the same digest.

| answer | meaning | consumer action |
|---|---|---|
| `Fresh` | engine, sources, policy, JDK all match | uses the map |
| `Stale` | engine/sources/manifest differ | **refuses**, reports, re-derives |
| `JdkMismatch` | published by a different JDK spec version | **refuses**, reports, run STOPS |
| `Unverified` | no fingerprint, or sources outside this run's resolution roots | uses it, reports |

Three loud non-fatal finding kinds keep the fallback honest: `BaseMapStale`, `BaseMapUnverified`,
`BaseMapMissing`. An empty base manifest (the documented way to declare a non-ported resolution root)
is exempt and claims no namespace.

`jdk=` is a fourth, independent fingerprint (`java.specification.version`) because the other three
provably cannot stand in for it — the frontend resolves external types from CLASS FILES, so emitted
text is a function of the JDK even when engine/sources/policy are unchanged (measured:
`sge.utils.CharArray` `E037 overrides nothing` when only the JDK moved, `ENGINE-LIMITS.md` M5.10).
`ManifestAgreement.Kind.BaseMapJdk` is FATAL, uniquely: `Stale`/`Missing` degrade to honestly
re-deriving the base's decisions; a JDK mismatch means the base's already-emitted Scala came from
class files this run does not have, and no re-derivation reproduces it. (The same disagreement WITHIN
one lane is `scripts/_lib.sh`'s `jdk_guard`, reading `run-latest/jvm.txt` — a different question.)

**Ordering**: a map-consuming phase runs LAST in a dependent's surface — a residue check, like
`PortabilityCheck`, of what remains after this module's own policy applied.

**Overload identity**: exact arity wins; no arity match means no record, never the nearest one
(`ENGINE-LIMITS.md` D1, 263 → 8).

### 5.5 What is NOT closed

- Member SIGNATURES are not compared — the map's key is the *emitted* signature with renames
  reversed, so the hole is closed for a name change and open for a retyping that changes only a
  parameter's type.
- The diamond (R4, N maps merging with the nearest base winning) is untested — no corpus library yet
  has two bases sharing a third.

### 5.6 On storing the CONFIGURATION as JSON/YAML — a qualified yes

The **declarative** half of a manifest (`dropTypes`, `dropMethods`, `packageRenames`, body maps,
forwarder lists, class tables — string-keyed, no behaviour) can be config, sharing the port map's
schema. The **`surface` (§1(c) rule) half is code and cannot be** — expressing it in config would make
this a plugin-loading engine, which §2.4 says it is not. "Scala is typed" is a thin argument for the
declarative half (a `dropTypes` key is a `String` either way — hence `PolicyReport`); config does not
*replace* the Scala manifest, it *adds* to it (two homes for policy). The port map (§5.2) mostly
dissolves the question: once a base publishes a map, a dependent *reads* drops/renames as data instead
of restating them.

### 5.7 The CONFIG front door — a `.conf` plus an SPI, and why it is not a second truth

Three front doors to the same `PortRun`:

| door | for |
|---|---|
| a Scala `main` calling `PortRun(...)` | full strength; a port with §1(c) rules or a non-trivial build |
| an embedder calling `PortConfig.load(...)` | a repository with its own entry point, policy in a file |
| `PortConfigMain <port.conf>` | a consumer with nothing to write at all |

**Pieces**: `balticporter.tir.TransformFactory` (`api`) is `name: String` + `fromConfig(ConfigView):
Phase`, discovered with `java.util.ServiceLoader`. `balticporter.runner` holds the HOCON adapter
(`HoconView`), the registry (`TransformRegistry`), the engine's own factory registrations
(`BuiltinFactories`) and the loader (`PortConfig`).

**Not a second source of truth**, for three structural reasons: the conf path constructs the SAME
values (`PortManifest`, `FrontendConfig`, `Provenance`, `PortRun`) through the same constructors — no
parallel model inside `PortRun`; inheritance is `extendedBy` itself (`base = "main.conf"`), never an
include — `input`/`output`/`provenance`/`runtimeMode`/`inject` are the must-differ column and are
ignored; anything config cannot express arrives as CODE — no classname-in-a-string, no expression
language. A factory NAME resolving through `ServiceLoader` to a class the consumer compiled is the one
sanctioned indirection. `PrimitiveToOpaqueTransform` from config therefore takes `extraHints` (a set of
names) and REFUSES `hints` (a predicate), naming the escape hatch (write a factory) in the error.

Every key read is tracked (`HoconView`) and an unread key fails the run — HOCON silently accepts
`dropType` for `dropTypes`, which is the §1(b) no-op this engine refuses everywhere else. `include` is
a HOCON keyword, so file selection is `includeGlobs`/`excludeGlobs`. `package-rename` is refused **by
name**, not merely absent: it is manifest DATA (`manifest.packageRenames`) because it must run after
every other phase and `runsAfter` cannot say "after everything" (§4.56).

A port's `.conf` lives at `balticporter/corpus/ports/<library>/{main,test}.conf`, not under `src/`.
Paths resolve lexically against the conf file (relocatable, no system property needed);
`${balticporter.root}` remains for operator-supplied `-D` values. simple-graphs is the acceptance
proof (`just sg-measure`, 0 members changed on both source sets). libGDX and Ashley are deliberately
NOT converted — every phase either uses is nameable from a conf already; what remains is measurement
discipline (one manifest-argument flag becomes `balticporter.skipPhases=*`; Ashley's `base` needs
libGDX converted first; each conversion needs its own re-measured commit, `CLAUDE.md` §5).

---

## 6. Unportable constructs — markers, the failure report, best-effort emission

Governed by §3.4: what is forbidden is **silent** best effort.

### 6.1 Prior art: dotty's best-effort compilation, and where this differs

Borrowed from dotty: first-class marker in the tree (never a side table), degraded output to a
separate directory with a distinct header, opt-in consumption. Rejected: stopping after the pickler
(here an approximation carries a real type, so later phases need no special handling, and stopping
would forfeit whole-program transforms that might fix the marked site); in-tree error poisoning
through typing (here poisoning happens at the emission GATE, per unit, to preserve report precision
and not block sibling transforms). This engine RECORDS constructs it chose to translate
approximately, detected before any Scala compiler sees the output, while holding the original Java —
buying a taxonomy of *why*, a hand-porter's likely answer plus ranked remediations, and expected-error
correlation (§6.3), none of which dotty's own recovery can have.

### 6.2 The marker, and why it lives in the tree — BUILT (term level)

- **Term level**: `Tree.Unportable` wraps a term translated only approximately; `Open` means it MUST
  NOT ship. The smart constructor rejects a synthetic origin (a marker must point at real Java —
  `markerKey`, the conservation check's identity, would otherwise collapse every marker onto one key).
  Carries `Option[DiffId]` — `None` is honest and preferred over inventing a catalog row. Where the
  frontend hands back a NODE with no position (e.g. Spoon's unpositioned `CtCasePattern` wrapper), the
  KIND comes from the node with no arm and the ORIGIN from the nearest enclosing real-java node
  (`SpoonTir.unlowered` splits subject from site) — the untouched fallback is still the honest throw.
- **Definition level**: a `SymTag`, for findings whose subject is a declaration's *shape* (a
  constructor topology with no single-primary encoding, an unexpressible signature).
- Rejected: a side table on `Program` (trees have no stable identity across phases); a `diag` field on
  every node (touches every construction site for an almost-always-empty field); symbol tags only
  (term sites are many-per-symbol; fencing/correlation/diff need the exact expression).
- The traversal gains one case so every phase's hooks reach inside an approximation; a phase that
  doesn't match the wrapper leaves it untouched — marker-preserved is the safe default. Discharge is
  explicit: `Open → Resolved(byPhase, how)`.
- `UnportableKind` is a **closed engine enum** (raw-generic conversion, context-dependent raw fill, JDK
  boundary flow, constructor topology, platform-hostile API, reflective lookup, overload divergence,
  frontend blind spot, unmodelled node kind, annotation residue), each with ranked default
  remediations classified (a)/(b)/(c) — templated strings naming only engine mechanisms, never a
  library. `UnmodelledNodeKind(spoonKind)` carries the parser's own interface name (`SpoonKinds.nameOf`,
  structural), joining a run's findings to §2.8's kind registry.

### 6.3 The report and the correlation — BUILT

One document per run: `report.md` (operator-readable — location, Java excerpt, why, hand-porter guess,
ranked remediations); `findings.tsv` (one line per finding, id = short hash of
`(kind, javaPath, ownerFullName, detailDigest)` — the line number is carried but NOT part of the id,
so upstream whitespace does not orphan a baseline row); `counts.tsv`/`subject.txt` (the
`before->after` commit-subject fragment); `members.tsv` (one digest per emitted member — byte-for-byte
unchanged is a stronger revert check than any count); `srcmap.tsv` (member → emitted line range → Java
`Origin`); plus `tests.tsv`/`decisions.tsv`/`port-map.tsv`/`dropped-types.tsv` (§3.6, §7, §5).
`baseline/` promotes only by explicit accept.

Source-map decisions: positions recovered by SEARCH (a wrapper remembers each rendered member's exact
string, located pre-order in the finished unit) rather than a threaded cursor parameter — cannot
change a byte of output, asserted; a member the emitter cannot re-find is COUNTED, never dropped. The
member key carries parameter types (`owner#name(T1,T2)` — java overloading). A class-body statement
with no symbol gets an ordinal (`owner#<stmtN>`, needed because the test transform lowers every
`@Test` to a bare statement). The Java path relativises against a root derived from the port's own
package suffix — no flag, no script.

**Correlation** joins compiler + test-runner output through the source map, three lanes: **marked**
(classified, expected, carries its remediation), **unmarked** (engine gap, auto-located — the triage
queue), **unmapped** (a diagnostic outside the source map, e.g. an injected shim). A marked region with
no error is a false-positive candidate. The test lane is the only one that sees `CLAUDE.md` §4.4:
each failure anchors on the first stack frame in ported code, with the anchor's QUALITY recorded
(`main-frame`/`test-frame`/`assert-site`/`suite`/`none`) rather than assumed; a newly-failing test whose
anchored member also changed digest is the highest-value signal the engine produces. Demonstrated by
deliberate breakage: removing `override` emission → 916 errors, 916 located, 0 unmapped; breaking
`static final → inline val` → 0 scalac errors, located by the test lane alone.

### 6.4 Best-effort emission — BUILT

One emitter, one flag (`TirEmitter.bestEffort` / `PortRun.bestEffort`), three effects: `Open` markers
render as the inner term inside deterministic comment fences; each affected file gets a banner; output
goes to a separate directory with a `BALTICPORTER-BEST-EFFORT` sentinel and the run exits nonzero. In
deliverable mode the gate runs FIRST — any `Open` marker and the tree is not written. The nonzero exit
is at the END of the run, not the gate, so the diagnostics still get produced. The shipping default's
rendering of an open marker is `compiletime.error` (not the fence) — a real deliverable run never
reaches that branch, so what reaches it (bare emitter, e.g. a testkit fixture) gets the loudest answer.
At zero open markers, best-effort output is byte-identical to deliverable output by construction
(`UnportableMarkerSpec`). This replaced the engine's only prior emission-side refusal
(`TirEmitter.unrenderable`, four `Tree.Break`/`Continue`-with-out-of-scope-label sites, previously
silent under the default `preview = false`) — kept apart because they degrade differently: it stands
where the engine CAN still spell something, weaker; the marker stands where it has nothing to say.

### 6.5 Status and staging

§6.2–6.4 are all built, landed together (`Tree.Unportable` + `UnportableKind` + `MarkerState`, the
traversal case, `MarkerCheck`, the emission gate, fences, `markers.tsv`, in one change) — measured
emission-neutral: **zero markers mint on all fifteen lanes**, no emitted byte moved.

Still owed: the DEFINITION-level `SymTag` half (deferred to the stage that mints one — a tag with no
mint site is indistinguishable from absent, the `TirTrace` failure the marker exists to avoid;
`ConstructorTopology` stays derived from `CtorFunnel.Plans`, never frontend-minted, per §7's
one-function rule); four of `SpoonTir.unsupported`'s six sites whose SHAPE a term-level marker cannot
take (a `Constant`, a non-declaration `ValDef` try-resource, an `instanceof` type operand, the
lambda-without-body guard) — each wants a mint site of its own kind; the correlation join over
`markers.tsv` (`unit`,`member`,`state`,`kind`,`catalog`,`javaPath`,`line`,`what`, gated on the artifact
layer, `CLAUDE.md` §5.1).

Adoption order: marker + traversal + conservation check + gate + fences land together (`CLAUDE.md` §3
— check and translation together); then mint sites one at a time, measured against §6.3's precision
check, frontend refusal points first. Two standing constraints: mint wraps the SMALLEST wrong term,
never a `this(...)`/`super(...)` delegation head; constructor-topology markers stay derived from
`CtorFunnel.Plans`. The conservation check has no hand-maintained exemption list: a marker gone because
its whole DECLARATION is gone is read off the TREES (not the symbol table, which a phase never prunes)
— a legitimate deletion, never an engine defect.

Risks and their falsifiers: a shape-matcher missing its shape through a wrapper → wrap every body term
with a no-op marker, assert byte-identical output; marker flood → the marked-but-clean count on one
mint site IS the false-positive rate; nondeterminism → double-run diff; conservation false positives
on legitimate deletions → inject synthetic markers into known-dropped members, each must report
*discharged* not *erased*; best-effort/deliverable divergence → standing check §6.4; remediation
templates accreting library knowledge → templates interpolate only program/manifest strings, caught by
the §1 grep gate; finding-id churn from line shifts → insert a comment line, re-run, orphaned ids must
be zero.

---

## 7. Derivation provenance — why the emitted code is not a mechanical translation

The source map (§6.3) answers "which Java produced this line", never "why is this type absent, this
package renamed, this member hand-written". Those are **decisions**, recorded twice: once in an
artifact for an agent holding the run directory, once **beside the code** for the agent this engine
actually has (`CLAUDE.md` §4.45).

### 7.1 The decision log

Every non-mechanical thing a port does is a `balticporter.tir.Decision`; its `Reason` is a
**constructor parameter**, never free text, because the first question is which §1 kind the fix is.

| `Reason` | §1 kind | payload |
|---|---|---|
| `Universal(rule)` | (a) engine | `rule` names the Java/Scala fact |
| `Configured(phase, key)` | (b) parameterised mechanism | `phase` + the manifest entry **verbatim** |
| `LibraryRule(rule)` | (c) one-library rule | `rule` names it |

`Decision.Kind` is a **closed enum** — thirteen: `RenamedType`, `RenamedPackage`, `RenamedMember`,
`DroppedType`, `DroppedMember`, `SubstitutedBody`, `InjectedMember`, `RedirectedCall`,
`RetypedSignature`, `FunnelledCtor`, `DroppedSuperCall`, `WidenedVisibility`, `Unrenderable`.
`DroppedSuperCall` stays distinct from `FunnelledCtor` — a class can funnel successfully and still
drop one root's super arguments, and merging would make that unanswerable.

Rules that are not bookkeeping: a phase records with `Phase.record`, drained per run by
`Pipeline.runTraced` (never carried across two translations of the same instance); a phase **skipped**
by a debug flag records nothing. `subjectFqn` is the name at DECISION TIME (a `SymId` dies with the
run; re-deriving at write time would relabel earlier decisions into the post-rename namespace since
package rename runs last, `CLAUDE.md` §4.56); position is read from the TREE, never the symbol
(`Symbol.origin` is unpopulated). Every decider records at the **declaration level** — one row per
declaration whose emitted form changed, via the declaration reached through the xref (never a
hand-rolled "current definition" walk); parameters/locals are filtered out structurally, not by an
`isParam` flag. The artifact is scoped to **this module's own declarations**; a dependent's phases
deciding about its base's units are WITHHELD (measured: `libgdx-test` unfiltered published 634
`RenamedPackage` rows, 605 already the base's own) — the count still prints, so withheld never reads
as none-made. Sorted on every column (diffable); written even when empty (a header-only file states
"no decisions", distinct from a run that never got that far). Artifact write gated on the artifact
layer without exception. One row per DECLARED policy key, including a key that matched nothing (a
failed decision, stated as such).

### 7.2 The porter note

The same fact is emitted beside the code (full spec `CLAUDE.md` §4.575):
```
/* porter: <kind-slug> k=v … — <free text> */
```
`<kind-slug>` is the decision kind in kebab case. `grep -rn '/\* porter:' src_managed` is the complete
inventory. Rules: notes are **DERIVED, never authored** (the emitter renders only decisions whose
subject it emits); **original trivia first, note last, member next**; a note **never opens or closes a
comment** (values neutralised `/*` → `/ *`, never rejected; whitespace values quoted).

**Eleven of thirteen kinds carry a note** — drawn at *would a reader be unable to explain this line
from the line itself*. Excluded: `RetypedSignature` (the new type is already in the declaration — a
note per member would be 335 restating comments on libGDX core alone) and `RedirectedCall` (the
rewritten call is right there in the body). `FunnelledCtor` was a third exclusion until §8.2 made
SYNTHESIS the normal case for a multi-constructor class — a reader now faces a `protected` constructor
no java declared, with `sup$0`/`f$name` parameters, unexplainable without the note (`slots`/`notSlot`/
`disambiguator`/`shape`, already carried on the decision). An exclusion is an argument about a SHAPE
and must be re-read whenever that shape changes; nothing in the pipeline can notice it going stale.

Note placement, per kind: at the declaration (subject is emitted); in the owning type's body head, for
a dropped MEMBER (no `def` to sit above); not in the tree at all for a dropped TYPE (carried by the
INJECTED file supplying its FQN, prepended at copy time). `NoteCoverageCheck` fails the run in BOTH
directions, **per unit** (never globally — a global count balances an invented note against a lost
one): a decision with no note; a note with no decision (policy smuggled into the emitter); a recorded
note absent from the emitted file. Joins on `SymId`, never a name (three emitter passes rename the
symbol before rendering).

### 7.3 Comments are part of the port

Full rules: `CLAUDE.md` §4.58. Design commitments: **slice verbatim from the source buffer**, never
re-print (a parser's `toString` loses `<pre>`-block alignment); **one comment, one home** (a
claimed-identity set — coarse harvests skip what children already took); **indent re-derived, text
not** (a port regenerates on every engine change; a re-wrap diff is unreadable); **the check compares
SOURCE TEXT to EMITTED TEXT, never the tree** — an independent lexer re-reads the Java and searches for
each comment's normalised body in what the run actually wrote (counting harvested nodes proves nothing
about the emitter). Matches on normalised body text; groups **by Java file**, not by unit (one Java
file → two Scala files is ordinary); an unreadable source reports nothing at all with a separate
coverage denominator. A finding is a §1(a) ENGINE gap, never a policy question — except a member the
port drops on purpose, whose Javadoc is meant to go with it.

### 7.4 Preview mode — a diagnostic, not an emission strategy

Where the engine has no faithful Scala it refuses and carries a residue comment (`ENGINE-LIMITS.md`
M6) — right for a shipping port, wrong for the first week of a new library, where the operator
(`CLAUDE.md` §4.45) has to FIND a residue that compiles perfectly before it can act on it. With
`preview` on, each such site becomes a `scala.compiletime.error` naming, in order: what the construct
is, why it has no faithful translation, what an agent must do, and the upstream origin — the port
deliberately does not compile, and these errors never mix with real ones (classified by the engine's
own message, ahead of the source-map lookup). Default **off** — `preview = false` emits the same bytes
it always did (proven by member digests, not this document).

---

## 8. Closing the adoption gap — ten designs

`PROGRESS.md` §11.9's three-way audit found **design absences** (one cause, patched per library) —
the fix in each case is a mechanism (b), default-off, empty parameter = no-op, gated on every lane
reporting 0 members changed. `ENGINE-LIMITS.md` entries each design retires/narrows are named in its
subsection; what is BUILT is `PROGRESS.md`'s business.

### 8.1 Member identity — `Symbol.descriptor`, and one `PolicyBinder`

**Decision.** `Symbol` gains `descriptor: Option[Descriptor]` (source-level parameter spelling, e.g.
`(int,String)`), leaving `Symbol.fullName` untouched (a 4th `fullName` separator would move every
`findings.tsv`/`decisions.tsv`/cache-key id and give `PackageRenameTransform`/`RuleScope` a place to
cut inside a parameter list — §4.56's trap). Policy keys resolve once via a two-stage `PolicyBinder`
(`api`); **phases receive bound `SymId`s, never raw strings**.

- Descriptor is SOURCE-LEVEL spelling, not JVM erasure — already injective within an overload set
  (java forbids same-erasure overloads), so a manifest key stays exactly what an author writes today.
- Two divergences between the frontend/manifest grammar and the engine grammar are latent traps (not
  yet hit by the corpus, so closed by negative specs rather than reconciliation): an array/vararg
  parameter (`int[]` vs. `Array`), and `equals(Object)` (`Object` vs. `Any` — the frontend retypes it
  before the `MethodType` is built). A third, QUALIFIED-vs-simple-name divergence WAS hit (workaround
  comments in two ports, "# BARE on purpose") — fixed by `Descriptor.matches` comparing `Param.simple`
  on both sides, cutting only at the last `.`/`$` (§4.56); two DISTINCT simple names stay distinct, so
  two same-simple-name overloads across packages are the binder's `Ambiguous` refusal, not a silent
  pick.
- **Two-stage, because a dropped member is never interned** (the frontend filters it before minting a
  symbol — policy that REMOVES something can only bind where the thing still exists). Stage 1:
  `MemberIndex`, published by the frontend, carrying every executable it WALKED, including ones about
  to be dropped and including initialiser blocks (as a LIST per key — two `static{}` blocks share one
  identity). Stage 2: `PortRun` binds every key once, before the pipeline runs. Subsumes
  `Substitutions.matchedKeys`.
- A FIELD has no descriptor (not a gap). An external member whose `info` never resolved has none (D1's
  residual 8-finding gap, now LOUD at bind time rather than silently degrading to arity).
- A **synthetic** member (§8.2's primary, §8.3's contract) gets an engine-derived descriptor in the
  SAME grammar but is **never** a `MemberIndex` entry (the index is "what the frontend saw"). A key
  naming one gets a dedicated `SyntheticTarget` refusal, never `NeverMatched` (typo vs. "engine-minted,
  policy has no standing"). `Ownership` matters here: `TypeRedirectTransform` must bind `Unowned` (its
  subject is a type this module cannot declare), or ten correct redirects report `NeverMatched`
  (measured, `policy 0 -> 10`).
- `Program.members` is a REQUIRED constructor parameter (28 `new Program` sites; defaulted, it would
  silently vanish across most of them). Never-fired reporting unifies through the binder.
- One asymmetry kept on purpose: a BARE `owner#name` key is legal for `dropMethods` (drops a whole
  overload family), `Unverifiable` everywhere else (call-site substitution needs exactness). Honest
  limit: `fullName` stays public `String`, so nothing stops a phase writing `startsWith("java.")` —
  enforced by a lint, not a type guarantee.
- `PortMapTransform.preciseKey`/`PortMap.erase` are NOT replaced by the binder — they are the two ends
  of one join in the EMITTED namespace; moving only one end misses the members whose grammars diverge.
- **Rejected**: a 4th `fullName` separator; a structured `SymId` (would put identity into every `Xref`
  key); a true JVM-erased descriptor (unproducible for an unresolved type-variable bound, and no author
  writes it); deriving the descriptor from `Symbol.info` alone (re-creates the `equals` divergence,
  fails for a dropped member with no `info`); a local arity/arg-shape test (measured: 118 `Ambiguous`
  of 263 findings); a `Symbol => Boolean` policy (unrenderable, unreportable).

**Retires/unblocks**: D1's defect narrows to its external residue; the two grammar divergences become
recorded limits with negative specs; a hard prerequisite for call-site substitution (§8.12, BUILT) and
for §8.3's per-member contract rows.

### 8.2 The constructor funnel — a synthetic PROTECTED primary

**Decision.** A class with two or more constructors gets a **synthetic `protected` primary** whose
parameters are the single reachable parent constructor's formals ++ the values of fields assigned in
every root's leading run (where expressible before construction). The primary's `extends` passes the
super slots and its body assigns the field slots; every Java constructor becomes a `def this(...)`
delegating, then running its own remaining statements. `protected` (not `private`, not `public`) —
reachable from any subclass's `extends` clause including cross-package, unreachable from ordinary
client code, and needs no whole-program "is this class extended?" question (which would be D4 drift).

- **Slot derivation**: super slots from the parent constructor's own FORMALS (never one call's
  argument types). A field is a slot iff (1) every root either assigns it in its leading run or not at
  all, (2) the value expression does not read `this`, and separately (A1) no delegating constructor
  reassigns it — condition A1 decides `val` vs `var`, NOT slot eligibility (conflating them wrongly
  demotes settered fields). **Order-safety**: a slot value must be ORDER-BLIND (only the secondary's
  own parameters, literals, operators on those) — anything with a method call/`new`/array read/static
  read stays a post-delegation statement (`reason=order`; 146/166 of libGDX core's refusals). A
  root that doesn't assign `f` uses `f`'s own initialiser or the Java default.
- **Build order**: disambiguator marker FIRST, field slots SECOND (a marker changes the primary's
  arity, closing the applicability problem before slots can create it).
- **Collision disambiguation, two predicates**: (i) declarations that cannot coexist — by ERASURE
  (`E120`); (ii) a real constructor's delegation resolving by overload APPLICABILITY to a narrower
  constructor instead of the intended one (`ENGINE-LIMITS.md` C8). Two answers: **collapse** — a
  colliding constructor that is a pure pass-through whose parameters ARE the slots is promoted with no
  synthetic member, UNLESS that promotion has an escaping path (C7 applies again — 2 corpus classes);
  else **a final marker parameter**, a type minted PER DISAMBIGUATED CLASS in its companion,
  `protected` (not `private` — `private` fails cross-package reach, `ENGINE-LIMITS.md` C9), argument
  ASCRIBED `(null: C.Funnel)` so no real constructor can shadow it. The collapse's argument fill is
  POSITIONAL (matches the parent constructor's formal order) — a type-matched fill was tried and
  dropped real arguments on a mismatch (`CtorFunnelSuperArgsSpec` pins the positional answer).
- **The wall**: roots reaching DIFFERENT parent constructors cannot fuse into one primary at all — 82
  of 430 measured classes, 11 reducible through a pure `this(...)` chain, 73 already fully expressed by
  REPLAY (kept — dropping it trades 33 lost `super(args)` for ~200). JDK-throwable parents get the
  measured widest-pass-through with null padding, tried only where synthesis is NOT applicable first
  (consulting synthesis first cost 4 omissions).
- **The withholding fixpoint is deleted for the 348 non-wall classes**: it existed only because
  PROMOTING a paramful constructor removes the nilary construction path; SYNTHESIS removes nothing
  (every Java constructor survives as a secondary). Guard narrows to "does a nilary secondary
  survive" — true whenever the Java class declares one; a bare `extends Parent` (no super args in the
  subclass's own plan) also resolves to the nilary secondary. Narrows to the 82 wall classes, still
  D4-exposed there (seeded by §8.3's contract row).
- Measured over 430 multi-constructor classes across 11 source sets: 348/430 (81%) expressible with no
  wall (100 need a disambiguator); promoted-body escapes 188 → 0 for the 348; 129 fields stay `var`.
  libGDX core: 140 escapes / 31 dropped-super remaining (wall-dominated: `DistanceFieldFont`'s 7 roots
  alone is 7 of 31) — generalising the super-slot rule alone moves libGDX by zero.
- **Provenance**: `Decision.Kind.FunnelledCtor` gains `shape` ∈ `synthetic-primary` /
  `synthetic-primary-collapsed` / `wall-replay` / `wall-counted`; `slots` names each slot's origin
  (`sup$k`, `f$<name>`); `disambiguator=marker|none` (never an FQN — the marker is companion-private,
  unresolvable outside); `notSlot="cache=reads-this"` for the `var`-not-slot reason.
- **Rejected**: more promoted-constructor shapes (every promotion inherits C1/C7 by construction); a
  public primary (widens the API with a constructor java never exposed); a discriminator slot for the
  wall (`extends` is one argument list to one parent constructor); default parameters (reference ports
  prefer overloaded `def this` 14:1/46:1); a `skipInit` flag (an API surface no Java reader can see);
  computing slot values in the primary body (runs on every path); seeding a dependent's plans from the
  base's map (superseded — a local derivation needs no seed for the 348).
- **Retires/narrows**: C1, C4, C7 (188→0 for the 348) and D4 for non-wall classes; C2 narrows sharply;
  C3/D5 narrow to the wall; C5/C6 unchanged (replay retained, governs the wall exactly as written).

**AS BUILT, seventh outcome — `Plan.none`'s context-clause carriage (`ENGINE-LIMITS.md` CT5).** A
third funnel outcome, `Plan.none` (Scala's implicit nilary primary, every java constructor a
`def this`), is the commonest shape and had no parameter list for a `(using T)` clause to land on
(measured: 57 scalac errors, 55 this shape). Fix is CLAUSE-CONDITIONAL: `Plan.none` gains exactly the
context clause its own constructors already carry (`CtorFunnel.classGivens`); with none anywhere,
`Nil`, byte-identical output (13 ports, 0 members changed). It does NOTHING about `super` (a dropped
`super(args)` is still counted as before — this is the implicit primary made SPELLABLE, not the
synthesis widened). Shape is `(using T)`, never `()(using T)`. A SILENT-loss check
(`context-seam`'s `lost-clause`) reads the RENDERED header post-emission, catching a class given no
clause, a `trait`, and a java enum (T21). The CONTRACT row is unchanged — a context clause is not a
value slot; disagreement is caught by `surfaceFingerprint`/`ManifestAgreement` (§1.5).

### 8.3 The published base surface — a `Surface` VIEW, and prevention rather than a check

**Decision**, three parts: the port map goes to **schema 3** with one new column, `shape` (a `k=v`
payload in porter-note grammar); phases/emitter receive a **`Surface` view** instead of a bare
`Program`, answering `Own` / `Published` / `Unknown`; an **`Unknown` that shaped emitted text fails the
run.**

**Why**: `Program.owned`'s ownership climb roots on `program.units` — *all* of them, base included — so
in a dependent it is a program-vs-JDK filter, not a mine-vs-base filter, and every `RuleScope` rule,
package rename and port-map repoint asks it. Six independent copies of the same climb existed, all on
the reporting side, none on the rewriting side. Of 24 sites answering a whole-program question over
non-owned types, nine have drift that MATTERS (dependent-emitted text correctness depends on agreeing
with the base): descendant clash renames (a base field renamed by a dependent subclass's method —
0 corpus sites, hidden by the old filter with no count moving), `export` exclusion lists (311 emitted
`export` lines computed from the base's Java as read, not as EMITTED), and D6's cross-module face (a
base `object`-collapsed static class named as a type by a dependent — measured 0, which is exactly why
five ports never saw it). Four more are latent behind default-off phases (globals→context, opaque
seeds, collections scope, flow propagation) — `Surface` must land before those are armed.

**Extends the map rather than adding an artifact** (a second file duplicates discovery/freshness/
namespace-split machinery — D6.5's failure shape one artifact over). Both namespaces, asymmetric: the
`upstream` column stays the manifest-shaped join key; every name inside `shape` is an EMITTED name
(§4.56). Key fields: type row — `form`, `companion`, `statics`, `primary` (in §8.1's descriptor
grammar), `primaryKind`, `primaryVis`, `disambiguator=marker|none` (never the marker's FQN —
companion-private, unresolvable), `secondaries`, `tparams`, `parents`, `flags`, `vis`; member row —
`name` (emitted, when it differs), `vis`, `placement`, `promotedParam`. Deliberately NOT carried:
policy (reaches a dependent through manifest inheritance/`ManifestAgreement` — a second source of
truth, §5.7), bodies/trees (M6 if a dependent thinks it needs one), per-site data (`srcmap.tsv`'s job).

**Freshness must cover POLICY** — the header gains a THIRD fingerprint, the base's sorted
`SurfacePolicy` fingerprint (the same value `ManifestAgreement` already compares), compared against
what the dependent computes from its INHERITED manifest; a mismatch is `BaseMapStale`, fatal wherever
the stale answer shaped emitted text. Lands in the same schema bump as `shape`.

**Enforcement is PREVENTION, not a drift check** — D4's own write-up shows nothing in the dependent's
run disagrees with itself (`ManifestAgreement` reports 0, the map records `Ported`); a check would
have to hold both the recomputed and published answer, at which point read it BEFORE emitting wrong
text. `Surface` lives in `api` (a §1(c) rule must be able to ask it). `owns` is the ONE structural
climb (owner chain to a program unit ∧ membership in the run's own units); exhausting fuel counts as
NOT owned (honest `Unknown`, never silent computation). `BaseMapStale`/`BaseMapMissing` become FATAL
where they shaped emitted text (an empty base manifest stays the exempt escape hatch). Honest scope
limit, stated: three of the nine have NO local repair (a collapsed-to-`object` base type named as a
type, a base primary a subclass cannot satisfy, a base `private` member a replay needs) — the contract
buys attribution and refuse-and-count there, not an answer.

**Rejected**: a new `surface.tsv`; extending `EnginePin`; parsing the base's emitted Scala (a second
frontend for a language the engine only writes); a drift check; making the base emit defensively
(measured worse three times); passing the base's `Program` to the dependent (re-decides over a program
that isn't the one the base emitted from); sectioning the contract into the dependent's own artifacts
(D2: withhold, don't section); a `contract = <path>` conf key (a second way to name a base
`ManifestAgreement` can't check). `PortManifest.baseReports` is the search-path addition for §4.45's
consumer (no run tree of this repo's shape) — beside `bases`, ignored when a port has stated one,
choosing rather than merging with the fallback `balticporter.baseReports` flag.

**Replaces/unifies**: D4 and D5 become lookups (owned units for the fixpoint; a non-owned wall class
seeded from `primary=`; a non-owned non-wall class derived locally AND cross-checked against the
published row, §8.11); D2's six climbs collapse to one; D6/D6.5/D8/D1 unchanged, contract adds D6's
cross-module face as a finding; §5.5's "signatures not compared" hole closes for everything `sig`
reaches.

#### 8.3 AS BUILT — what landed, and the four places it differs from the design above

Schema 3 shipped: `port-map.tsv` gains column `shape`, header field `policy=`. `Surface`/`Answer` in
`api`; `TrivialSurface` (every unit is mine) is the non-port default, `PublishedSurface`
(`engine/core`) what a run builds. Differences from the design:

- **`Answer.Own` carries NO value** (not `Own(a: A)`) — an owned type's shape doesn't exist until the
  emitter takes the branch that decides it; a pre-computed value would be a second, disagreeable
  derivation. `promotedParam` is NOT carried either (no declaration row exists for it,
  `Surface.NotCarried`) — `primary=` answers the constructor question instead.
- Member lookup keys `owner#name(params)` (§8.1 descriptor), grouped by OVERLOAD SET, answering
  `Unknown` where overloads publish different shapes rather than picking one; a §4.55 member rename
  needed no `upstream`-column repair (the join key was already right — the gap was the EMITTED name,
  now `shape`'s `name=`, gated as `MemberClashPlacementSpec`). Type rows cover NESTED types too (libGDX
  core 605 units → 983 rows). An ENUM is excluded from the constructor cross-check structurally (its
  primary IS the java constructor via constants — comparing would compare unrelated derivations;
  measured 5 false-alarm FATALs before the exclusion).
- The wall CONFIRMS rather than removes: gdx-gltf's two remaining wall-class errors don't resolve even
  with the row seeded (roots reach different parent constructors) — the row buys attribution, not a
  repair; errors 7→7.
- Two more reads migrated to the contract: §4.55's field-rename passes (follows the base's `name=`;
  where the base published none, the DEPENDENT'S OWN METHOD is renamed instead via
  `OverrideGraph.closureOf`, whole component or refused as `Surface.Gap`) and the `export` exclusion
  lists (read `statics=`/`companion=` from what the base EMITTED).
- A member the ENGINE REFUSED (e.g. C11's nilary constructor) is now a `Dropped` MEMBER ROW with
  `refusal=<engine rule>` — an absence used to read as a benign one-constructor class (§4.4's shape).
  Carries BOTH namespaces (the type IS emitted, only the member is missing); `PublishedSurface
  .memberRows` now excludes `Dropped` rows explicitly.
- The base's map is discovered over a SEARCH PATH (`PortManifest.baseReports`), not
  `PortMap.reportRoot` alone; both readers take the same `PortMap.discoverIn`, first wins per module.
  A non-fatal contract gap is now the `base-surface` CHECK (`kind` ∈ `unanswered`/`shaped emitted
  text`), required of EVERY port, recorded BEFORE any fatal refusal. The determinism twin must be
  handed the SAME `Surface` (scopes the funnel's fixpoint) — measured to fire without this.

#### 8.3.1 Following the base's MEMBER renames — the port map as the base's answer

A dependent's inherited whole-program phases (`BeanPropertyTransform`, `NullaryArityTransform`) can
have a base member's override component refused in the dependent's WIDER program even where the base's
own smaller program passed the guard. Fix, two pieces: the port map's `upstream` column now carries
JAVA's name, not the post-rename one (`PortRun` builds `memberOriginals` from `DecisionLog`'s
`RenamedMember` entries; `form=parenless` marks arity-changed members) — 918 rows moved on the libGDX
base. `PortMapTransform.followMemberRenames` reads the base's map and applies the SAME rename in the
dependent (matched via the package rename map, applied through `MemberRenamer` so both the override
component and call sites follow); a base refusal (no `name=`/`form=`) is left alone. Measured: visui
164 → 7 (floor), the whole dependent regression across six ports closed.

#### 8.3.2 Following the base's PUBLISHED CONSTRUCTOR PLAN — D15

A dependent FOLLOWS the base's published constructor plan for types it does not emit, same rule as
§8.3.1 (D14): its own retyping/opaque phases must not re-derive over base units (D12, O8), so a
descriptor mismatch between the local plan and the published one is expected, not a bug. The local
plan stands for the fixpoint; the dependent follows the base's published signature at call sites
through `coerceArgs`/`baseMemberUpstream`. The disagreement is a recorded non-fatal gap.

### 8.4 Globals → context — replace the core, keep the shell

**Decision.** The existing globals transform's closure/boundary handling is REPLACED (forwarding
insight, provenance shape, factory contract, traversal pattern kept). Policy is a list of **holders**,
each a path-valued member map onto an injected/minted context type, an attachment mode, a read shape,
and per-site boundary policies. A threaded call changes NOTHING at the call site (argument arrives from
`using` in scope) — no per-call decision row.

Replaces a call-graph closure because it was unsound in both directions and silently mistranslated a
`static {}` block (seeds, gets a `using` param the emitter then drops) and a field initializer read
(never visited — the is-a-method seed test excludes it): both broken, zero decisions, zero findings.

- **Closure = directed reachability over five edge kinds**: seed (own record of what the phase
  mapped), call, override component (BOTH directions), instantiate (class attachment only), capture
  (a lambda/anon/local body imposes need on the ENCLOSING declaration, not its own signature). Not
  `FlowPropagation` (that's symmetric pure-move union-find; this is directed need).
- Over-approximation across an override component is priced, not avoided: one trailing clause + one
  reference argument, noted `via=override-component`, counted — the sound alternative (thread only
  readers) broke `override` matching, 100+ unattributed errors.
- Read shape: anonymous `(using T)` + summon, never a named parameter (a named one shadows the
  root package under fully-qualified emission, measured twice in the reference port; 98.2% of its
  reads are inline companion-summon).
- Member map is PATH-valued (two-hop: 56% of reference-port reads re-home a static onto a service via
  a bundle); a write-through fires only when the mapped path ends on a `var`/setter — the mechanism
  never mints mutability the target doesn't declare.
- The ambient default `given` is DELETED — without it an unthreaded caller of a threaded callee is
  impossible by construction except across a refused boundary, which `ContextSeamCheck` counts (four
  kinds: `residual-global-read`, `deferred-init`, `captured-context`, `frozen-component`), gated on
  the artifact layer.
- Eager→lazy is per-site opt-in, reported, never a default (corpus demand: one true class-initialiser
  site in nine libraries).
- Two attachment modes: `method` (trailing clause per threaded method) and `class` (clause on the
  primary constructor, landing on every funnelled SECONDARY too — depends on §8.2's `CtorFunnel.Plan
  .givens`, so class mode landed after §8.2). Reference port: 82% of attachment sites are
  constructors, 49% secondaries.
- **`attach="class"` now EMITS** — for one release the funnel undid the clause three ways
  (`ENGINE-LIMITS.md` CT4), so the phase recorded a `PolicyIssue.Unverifiable` rather than emit broken
  code; fixed by modelling parameter GROUPS in the funnel (`Plan.givens`, `CtorFunnel.valueParams`).
  Measured: class mode threads 275 declarations/177 files/17 seams vs. method mode's 2,497/324/162;
  `frozen-component` 32→0 under class mode.
- **`ContextHolder.selfSupplied`** is a third attachment answer for a type a FRAMEWORK instantiates
  (test suites, `ServiceLoader` impls, beans) that a closure has no caller to fix — measured on the
  first port to thread a constructor: 0 errors, 0 seams counted, a whole suite silently gone
  (`ENGINE-LIMITS.md` CT7). Emits `private given <ctx> = <expression>` at the type's head (the
  reference hand port's own shape) — a RESOLUTION not a refusal, reads stay threaded. `Tree.Opaque`,
  checked by the target compiler like a `MethodBodyTransform` body. Paired with a WARNING
  (`context-seam`'s `unconstructed-thread`): a threaded class nothing owned constructs, `Object`
  excluded — warns rather than refuses (a program-constructed class and a framework-constructed one
  are indistinguishable from inside).
- Build-time corrections (each fixed structurally, §4.56 applied): the anonymous clause needed the
  EMITTER's empty-name-renders-anonymous rule; the CAPTURE edge reads from the xref's `enclosing`,
  never the owner chain (an anon class's owner is its enclosing CLASS, losing the method); `mint` (a
  mutable-`var` bag) is REFUSED at bind time for a two-hop path (cannot express an immutable case
  class — `inject`'s job); `lazy-init` is a cache PAIR reusing the field's own symbol, not a `lazy val`
  (no parameter list there); a `DroppedMember` decision's SUBJECT is the owning TYPE, not the member;
  the INSTANTIATE edge is read off the NODE (`ContextNeed.instantiates`), never off `UsageKind` (a
  shared `Xref.walkType` arm was silently relabelling it for every generic class, `ENGINE-LIMITS.md`
  CT6); a bound entry selecting zero sites REPORTS.
- An UNSUPPLIABLE USE (a declaration that can't take a clause calling one that now requires it) is
  folded into `residual-global-read` rather than a fifth seam kind — same reader-facing fact.

**Rejected**: extending the existing transform (wrong core relation); the ambient given; a named
context parameter; a hand-maintained threaded-method list (§5.1); context function types as a general
emission shape (no java analogue, right only at hand-written consumer bootstrap); de-statifying the
holder in place as the only mode (subsumed by `mint` at the holder's own FQN).

### 8.5 `OverrideGraph`, `MemberRenamer`, and the property transform
**Decision.** Two layers, both in `api`. **`OverrideGraph`**: member-level correspondence across a
hierarchy — `parentsOf`, `childrenOf`, `overridden`, `closureOf` (owned members + `externalAnchors` +
`baseAnchors`). **`MemberRenamer`**: expands rename requests through those closures, refuses anchored
ones, checks the new name against effective names PARENTS-FIRST (§4.55), records one decision per
renamed declaration, applies ONE symbol-table rewrite. Edges are DESCRIPTOR-keyed (D1's erased rule,
later §8.1's identity) — the emitter's own four ad hoc rename passes matched name+arity only (D1: 263
findings, 118 ambiguous).

Four consumers: the property transform, type-redirect member renames, §8.4's using-threading, and
`member-rename` (`MemberRenameTransform(Map)`) — the fourth exists because the other three never let a
PORT choose a name (a redirect's new name is dictated by the target; here the port states it,
e.g. resolving a collision from a base redirect). Distinguishing rulings: `OnCollision.Refuse` (opposite
of the property transform's `DeferToEmitter` — a free port choice has a one-edit fix, so a collision is
refused rather than silently resolved); BASE-ANCHORED (a free rename has no base agreement, so it may
not move a base declaration, unlike the type-redirect case); pipeline position from the merge (§8.13),
not an edge.

Shared core: *the set of declarations that must change together, or none of them*, plus anchored
refusal, counted. Anchor policy is conservative by design — an unknown external parent with no surface
data ANCHORS (refuse, count): an over-refusal is visible, an under-refusal is a silent contract break.
Collision handling delegates to the emitter's existing suffix-until-free passes rather than
re-implementing it; the utility refuses only what those passes will not move. Never invents a member —
an entry naming a non-existent accessor is `NeverMatched`, not a synthesis.

**Symbolic targets and `@targetName` — BUILT**: a rename VALUE may be a symbolic operator (`+`, `<=`,
`unary_-`, …); the phase emits `@scala.annotation.targetName("<javaName>")` (JVM name stays java's for
binary compatibility, keeps `-Werror` clean). Rules: overloads from the SAME key are not colliders
(java forbids same-erasure overloads, so they coexist under the new name too); the whole component
moves or none does; call sites follow through the one rewrite; `unary_-`-family names refused
`Malformed` at bind time for a non-nullary target; `MergeablePolicy` unions keys, refuses on a
different value for the same key.

**The `var`/`val` collapse — BUILT.** Default property target is `def x`/`def x_=`, bodies verbatim;
the collapse to `var x` is opt-in per entry (`target = "var"`), degrading to the def-pair whenever a
guard fails. Guards found only by BUILDING it: `ConcreteRelative` (a `var` implements an abstract
accessor and cannot override a concrete one, either direction — without it, the shadowing pass's
renaming of a field silently un-does the collapse, emitting `var w$shadow` under an abstract `def w`,
invisible until `RefChecks` at 0 typer errors); `MutableStorage` (a `val` compiles only when nothing in
the whole program writes it — java `final` is irrelevant; a constructor-filled field is refused, since
the funnel decides its keyword and this phase cannot see the funnel); `isMutable` is read off the
SURVIVING (target) symbol, never the field's. The shadowing-pass exemption for a parameterless `def`
above a field is ABSTRACT-ONLY (a CONCRETE one is a def-pair getter over its own storage — exempting
it breaks `RefChecks` too, 2 corpus rows). Population: 90 (not 91 or 93 — two independent
over/undercounts, resolved by reading `idiom(refused)`'s guard breakdown, never a transcribed number).

**The WHOLE-PROGRAM derivation — one `RuleScope` on the SAME phase — BUILT**, overturning the earlier
"no second knob" ruling: that argument holds only while the policy is a hand-typed table (a scope
beside it would double-home one decision) — but libGDX's hand-harvested pairs are 132 against 1,420
java's own convention identifies, and a 1,420-key include list is a transcription of the program, not
policy. `BeanPropertyTransform` takes `scope: RuleScope`: the map spells THIS pair at THIS target, the
scope derives the REST by java's convention; a configured key always wins over the derivation. Default
`Only(Set.empty)` is the no-op (§1(b)'s ADD rule — a derivation MINTS names); the fingerprint segment
is omitted only at this exact default, never at a non-default empty scope (`ENGINE-LIMITS.md` CT9).
Scope is asked of the OWNER TYPE, never the accessor. Measured on libGDX at `Everywhere()`: 1,420
converted / 235 refused, guards `RenameRefused`(158, anchored/unmovable), `Static`(28),
`SetterOnlyInterface`(23 — scala's `x.prop=v` sugar names the GETTER symbol on the LHS, so a
setter-only interface receiver has nothing to name; refused as a WHOLE component, confirmed by the
reference port keeping `Cullable.setCullingArea` verbatim, §3.5), `FluentSetter`(21, `setW` returns
`this`), `NonVoidSetter`(5). Three derivation-only collision guards (an accessor already claimed by a
configured pair; a derived name colliding with a configured key; two getters in one type deriving the
same property) exist only because policy is no longer a hand-typed table.

**`NullaryArityTransform` — BUILT, moved off Rejected under EXACT PARITY, by measurement not
reconsideration.** What's rejected is emitter-level rendering (desyncs source map); this is a phase
that changes the DECLARATION's arity in the TIR and rewrites call sites through the symbol table — the
symbol moves, so surface and source map move together. sge states no rule; the split is EMPIRICAL
(1,358 parenless defs beside 1,212 with `()`), so the predicate is STRUCTURAL and deliberately
narrower than sge's own hand: one empty parameter clause, non-`void` result, no `Tree.Assign`/
`Tree.IncDec`/argument-carrying call in the body, unanchored override component, every component
member satisfying the same test. Over-refuses on purpose (a call to a callee this phase hasn't proved
anything about still refuses). An ABSTRACT method is never getter-like (a SAM ascription reads the
interface's declared arity — measured 38 errors, `PoolSupplier.get()`/`Comparator.compare()` family,
before this was made a refusal). Measured on libGDX at `Everywhere()`: 176 converted / 1,693 refused
of 1,869 considered — `ComponentPartial`(837, an interface's abstract half can never qualify whole),
`SideEffectingBody`(541), `AnchoredClosure`(315). Runs AFTER `bean-properties`, BEFORE `package-rename`
(last, §4.56); implements `SurfacePolicy`/`MergeablePolicy`; `accountedBy` names `IdiomCheck.Residue`.

*(The earlier, pre-build design text for the collapse and the whole-program derivation is superseded
by the two BUILT subsections above.)*

### 8.6 Nullability — three stages, union floor first

**Decision.** One phase, §1(b), default-off, three configured targets — `union` (`T | Null`, default),
`named` (a configured FQN meeting a 5-member contract: `apply`, `empty`, extension `get`/`orNull`/
`isEmpty` — sge's `lowlevel.Nullable`; CLOSES K13's 35-error abstract-type-parameter class since
`Nullable[T]` composes at an abstract `T` where `T | Null` cannot), `option` (`scala.Option`). A
dependent at the default `union` INHERITS a base's non-default target through the merge contract
(§8.13); `target` is omitted from the fingerprint only at the default.

- **A wrapper target owes a coercion at 5 universal (§1(a)) seams** the union floor doesn't need
  (measured `661 → 0` on the base): call result (`coerceTo` reads through `Tree.Typed`/If/Match/
  Block), external callee (class-file `MethodType`, counted `UncoercibleSeam` either way), reified
  positions (`== null` → `.isEmpty`; `ArrayLength`/`ArrayAccess` unwrap), lambda body (a SAM result
  slot), and erasure (an opaque wrapper collides overloads differing only in the wrapped type — a
  counted `ScopedOut`, the wrapper's own limit).
- Three stages: **N1** annotation-driven `T|Null` floor, no compiler flag (built); **N2** compile with
  `-Yexplicit-nulls -language:unsafeNulls` (needs §8.2's `val`/`uninitialized` work first); **N3**
  strict mode (out of scope, needs null-flow research).
- **AMENDED**: "N1 costs nothing at use sites" is true at a CONCRETE type and FALSE at an ABSTRACT
  one — `Null <: String` but not `<: T` for `T <: Object`, so `T | Null` does not conform to plain `T`.
  Measured by binding the real policy: 0 → 35 errors, all inside generic containers/widgets. The
  engine COUNTS (`NullabilityBoundaryCheck.Issue.AbstractTypeParameter`, 155 flagged for the 35 that
  fail) rather than refuses — enabling on a generic-heavy library is a POLICY call (scope generics out
  / accept errors / wait for N2).
- N1 without the flag buys typed documentation and deletes the `null.asInstanceOf[T]` placeholder at
  annotated generic returns; enforces nothing until N2. N2's shape is measured: placeholder casts
  compile fine under explicit-nulls, but a literal `= null` init and a body selection on a nullable
  value break — the `unsafeNulls` COMPILER OPTION (not a per-file import, §6-compliant) fixes both
  while signatures keep their honest `| Null`. Strict mode rejects a widening override, so
  override-graph propagation is done in N1 to avoid churning digests twice.
- **Wrapper mode attacks the SLOT, not the type** (K2's lesson): `given Conversion` never fires through
  overloaded calls (measured, both the reference wrapper's own conversions are dead in practice) — so
  the phase inserts EXPLICIT wrap/unwrap at 4 slot kinds (arg-vs-formal, decl-vs-init, assign-vs-RHS,
  return-vs-result) before overload resolution runs. `x == null` on a wrapper is a compile error, so
  every null-test becomes `.isEmpty`. Slot-nullability rule: a DEREFERENCE uses `.get` (NPEs, matches
  java); a SLOT COERCION uses `.orNull` (null-preserving, matches java's default-accepts-null), except
  a PRIMITIVE slot (unboxing) which uses `.get`.
- Census: 7/11 upstreams have zero nullability annotations (empty-config no-op is normal); grammar is
  declaration-position; an annotated vararg refuses (no nullable vararg form); a small frontend gap
  (parameter annotations never captured — 389 upstream sites → 0 emitted) is prerequisite work. The
  consumed annotation is STRIPPED (the type states the fact; keeping both double-states it).
- Boundary stated, not approximated: hand ports cover ~2× the annotated set via null-flow knowledge no
  phase has. `return null` harvesting is a candidate list for human review, not a retype (unsound as a
  surface change). Ordering: after collections, before package-rename (§4.56).
- **BUILT, five corrections**: the boundary check counts REFUSALS in BOTH modes (an annotated vararg/
  primitive/argumented-annotation/type-or-local annotation all leave a byte-identical, unreported
  declaration otherwise); a retype gets a `Decision` and deliberately NO porter note (`RetypedSignature`
  is outside `PorterNote.Rendered` — the type itself is the note; complement asymmetry: a `ScopedOut`
  declaration DOES get one, since nothing else shows it kept its type); `null.asInstanceOf` retires at
  TWO shapes (generic return AND an uninitialised annotated field); wrapper mode refuses an
  override-crossing member conservatively (`isOverride` plus name/descriptor match, until §8.5's real
  closure lands — over-approximates safely); `rawParentAlignment`'s `hasWildcardArg` doesn't look
  inside a union (named as a risk, measured NOT to fire at P3 enablement — 5 wildcard-inside-union
  sites, none an override, `PROGRESS.md` §11.17 — gap stays open).
- **Rejected**: `given Conversion` ergonomics; a BOXING wrapper (erasure/bridges/allocation the opaque
  union wrapper avoids); blanket `T|Null` everywhere (destroys the annotation's information); retyping
  from `return null` sites; emitting `.nn` now (meaningless without the flag).

### 8.7 Visibility — Java's four levels, mapped

**Decision.** The frontend records JLS-effective visibility; the emitter renders all four levels.

| Java | Scala | fidelity |
|---|---|---|
| `public` | *nothing* | exact |
| `private` member of a top-level class | `private` | exact |
| `private` member/nested type of a NESTED class | `private[TopLevel]` | exact (JLS 6.6.1 — private reaches the whole top-level enclosure) |
| package-private member/ctor/nested type | `private[<emitted pkg tail>]` | near-exact |
| package-private top-level type | top-level `private` | near-exact |
| `protected` member/ctor/nested type | `protected[<emitted pkg tail>]` | near-exact |
| `protected static` | public + recorded widening | residual |

Root cause fixed: previously `PUBLIC` was never read and package-private never REPRESENTED (a
no-modifier Java declaration produced `public`-identical flags), and `protected` had been dropped
wholesale to a same-package-caller error class. The qualifier is a simple identifier naming an
enclosing scope, derived from the emitter's CURRENT EMITTED package — never an upstream FQN (no new
two-namespace join, since package rename runs last, §4.56). `protected[pkg]` restores javac's
overload-resolution PRUNING behavior (dotty prunes inaccessible alternatives before resolution too),
retiring T12 and its compensating body-substitution hack. A qualified boundary can only WIDEN, never
narrow Java's rule (Scala's `private[p]` reaches `p`'s subpackages too) — under the corpus's rename map
alone (whole-package cut at separators) the only delta is package MERGES, recorded as `Configured`
widenings. A base's own test suite sharing its package with the library keeps working because the
dependent INHERITS the base's rename map (§1.5).

**Per-type rename (M6) can also SPLIT** an upstream package across emitted ones (`typeRenames`,
`subPackages`, `flattenNestedTypes`, `allowPackageSplit` — four maps on the one renaming phase, all
inherited by dependents, all deriving into the same longest-prefix table as `packageRenames`). A split
crossing a package-private/protected boundary is REFUSED, or — where the port declares it deliberately
— recorded as a `package-split` `Configured` widening (parallel to `package-merge`); the recorded
widening wins in the qualifier derivation.

**BUILT, corrections found only by implementing**: subpackage reachability must use SCALA's `reaches`
rule, not string equality (equality wrongly refused entries a real hand port accepts);
`flattenNestedTypes` gets the same treatment at the ENCLOSURE (`enclosure-split`); the rule sees only
the `protected` half (package-private isn't representable yet); two structural `Malformed` refusals: a
rename destination colliding with another type's emitted name, and a Java INNER class (cannot
flatten). **BUILT (D3), seven more**: cross-package `protected` overrides live in ANONYMOUS classes
(`Tree.New`, not `ClassDef` — a class-body walk misses all of them, measured 14 `E164` errors, residue
28 vs. a class-scan census's 10); `protected static` covers nested TYPES too (residue 99 vs census's
71); a nilary constructor with a qualifier but no context clause gets an empty clause written out; the
companion re-export filter must skip `<clinit>` (else it parses as an XML tag — 29 `E040`s, the second
time this exact trap fired); `ctor-replay-widening` is NOT retargeted to `private[pkg]` (replayed
statements may run in another package — public is the only form that always reaches); an INJECTED
file must match the surface it replaces BY HAND; `override` drops for bare java `private`, KEPT for
package-private (`private[pkg]` does override within its package).

**Recording**: the mapping itself is §1(a), the diff IS the change — a faithful rendering records
nothing. Residual widenings reuse `Decision.Kind.WidenedVisibility` for BOTH members and types (no new
kind — subject column already distinguishes), causes: `x-pkg-protected-override`, `protected-static`,
`qualifier-shadowed`, `x-pkg-pkg-private-override`, `ctor-replay-widening`, `member-rename`,
`package-merge`/`package-split` (the two `Configured` causes). `member-rename` was the LAST cause found
and the largest: §4.55's field-clash renaming passes strip `private`/`protected` unconditionally
(necessary — the new name must stay reachable) but the WIDENING went unrecorded for ~280 members on the
largest port, invisible because `NoteCoverageCheck` compares decisions to notes, not to reality. The
note IS rendered (`WidenedVisibility` is already `Rendered` for its other 5 causes — splitting a kind
to hide half of it is exactly what §4.575 forbids).

**Blast**: designed corpus-wide re-baseline, ~1,970 member digests on the largest port —
`members-unchanged` is the wrong gate BY DESIGN; the gate is unexplained error-count rises and moved
test outcomes. Empirical anchors: the exhaustive same-package non-subclass `protected`-caller set is 20
sites (exactly what bare `protected` breaks); java's non-override shadow across packages is 0 of 9,346
method declarations.

**Rejected**: bare `protected` (denies the package half); keeping the drop and recording every instance
(thousands of identical rows, T12 stays alive); a new type-level `Decision.Kind`; dotted qualifiers
(don't exist in the language); populating `privateWithin` as a `SymId` (packages aren't symbols here);
deriving the qualifier from the upstream FQN (re-creates the two-namespace join); a mixed per-usage
strategy (unstable under any upstream edit).

### 8.8 Trivia — a hybrid, and a loss that is not where it was thought to be

**Decision**, three mechanisms, ordered by what each retires: (1) a `trailing: List[Trivia]` slot on
`Tree.Block` — the frontend KEEPS leftover comment-statements instead of dropping them; (2)
position-based FILE-LEADING harvest; (3) span-interleave as a completeness BACKSTOP only.

**The dominant residue is a frontend claim-then-drop, not an emitter gap**: the statement fold
accumulates comment-statements into a pending buffer and folds them onto the NEXT statement, or
discards them when there is none — already claimed, so no coarser harvest can recover them. Fixed in
the model (exact placement, no heuristic).

**V3's culprit is Spoon's package-declaration attachment slot**, never read — of two leading blocks the
unit gets the first and the package declaration the second (in one generated-parser family the
DROPPED block is the Apache notice itself, making this a §4.57/4.58 licence obligation, not tidiness).
Fix is positional: every scanned comment ending before the `package` keyword is file-leading; the
Spoon-attached subset is claimed by identity; the rest taken from the scanner verbatim.

**Pure interleave is rejected as the primary channel** — the source map's granularity is the MEMBER,
so interleave alone could only place a comment "somewhere in this member" (clumping at boundaries),
while the attachment channel already places 7,159/port members' trivia correctly at statement grain.
So the interleave backstop runs INSIDE the emitter, after body text is built and BEFORE the source map
is computed (a post-pass would desync `srcmap.tsv`/`members.tsv`, M7's join-on-recorded-id rule
applied to line ranges); strips porter notes before searching (else phantom matches, §4.575); anchors
on the last member preceding the comment, appends AFTER its text (between slots — only the whole-file
digest moves). Marker `/* trivia: recovered from <path>:<line> */` — deliberately NOT porter-note
grammar (a per-comment row is finer than §5.1's per-declaration rule; `NoteCoverageCheck` would fail a
note with no decision behind it); inventoried by `TriviaCheck`'s `recovered` lane; exempt from note
coverage BY SHAPE; every note-stripping check strips it too (M7's precedent — the trap that produced 3
phantom dangling drops the first time notes shipped).

`TriviaCheck` grows lanes rather than shrinking scope: `lost` (publishable bar, target 0),
`recovered` (backstop placements — a counted residue, NOT a success), `deliberate` (derived from the
run's own drops, §3.6 — fixes a live miscount: the old exemption was type-level only, so a
policy-dropped MEMBER's javadoc counted as engine loss).

#### 8.8 AS BUILT — the design held; four things it did not say

All three landed as specified. **`lost = 0` on all thirteen ports** (from a 233-comment corpus total).
`recovered` is small because the lane was READ, not because the backstop is good: its first run (51,
all one category — a `@Test` method's javadoc lost because `TestFrameworkTransform` turns the method
into a `test("…"){…}` statement, whose `leading` field went with the `def`) got an exact home
(`Tree.Commented`, the TIR's own statement-comment carrier) and dropped to 0 — the loop the lane exists
for.

Corrections found only by building: V3's culprit IS the package declaration (`CtPackageDeclaration
.getComments`), pinned in a spec so a Spoon release that moves it is visible; the file-leading harvest
had to become a CLAIM running FIRST (`headerSpans`, cached per unit, or a type-attached parser read
double-emits it); the backstop made the source-map slots load-bearing for EMITTED TEXT (recording is
now unconditional, not gated on the artifact layer; the upstream file's digest was added to
`TirCacheKey`); "between slots, no member digest moves" holds only while slots don't NEST (a nested
class's recorded text CONTAINS its members' — 2 members went unlocatable on libGDX core until the
splice applies to every ENCLOSING slot too, which honestly does move that member's digest); two silent
pre-existing defects were exposed, both spec-pinned: `TriviaCheck.normalize` stripped `//` LAST
(reported a nesting-safe `//`-rendered comment as lost while it sat in the file, §4.58), and a finding
carries the check name it's filed under (the `deliberate` lane's rows were filed against `trivia`,
inverting what stdout said). One §5.4 repeat: `CommentAnchor`'s map keyed on java path compared two
different path spellings — both sides now go through `RealPath`.

### 8.9 The JDK surface — derived from the walk that already runs

**Decision.** `external-surface.tsv` (one row per external member this port's EMITTED units
reference: `owner#name(descriptor)`, usage kinds, site count, first origin) and the `jdk-surface`
check are both second consumers of the enumeration `PortabilityCheck.checkAll` already computes and
used to throw away except its 34 rules' hits — zero new traversal. Classifies every row:

| class | source of truth |
|---|---|
| shimmed | the runtime artifact's concrete-member map |
| mapped | the transform's handled-set table |
| refused | a `Refusals` table, `why` + an `ENGINE-LIMITS.md` pointer |
| unclassified | a finding — the port's JDK wall, named |

**Refusals are CHECK DATA, not `decisions.tsv` rows** — a kept JDK call changes no emitted
declaration (§5.1's altitude rule); an uncited refusal is itself a finding. `CollectionsTransform`'s
static/instance `match` arms are lifted into declarative tables the arms AND the check both read,
pinned by a bijection spec against the arms' own SOURCE TEXT (sliced to each function's region — a
whole-file scan would pass vacuously). Scale: 144 distinct `java.*` types / 108 distinct
statically-qualified members across 9 ports (a floor — instance calls on kept receivers are invisible
to text).

**As built, four corrections**: two more classes — `Kept` (nothing claims it, portable, compiling —
counted but not flagged, else hundreds of `Math#max` rows bury the real findings) and `Mappable`
(unmapped because the retyping phase is simply ABSENT — an offer, not a hole; `ran` is a parameter of
the check's `Mapping`, not a phase property). Rows are the surface AFTER the pipeline — a fully
rewritten call no longer references the member at all; `Mapped` survives only for shape-changing
rewrites that keep the java symbol. K9 is derived from the NODE's post-pipeline receiver type, never
from table membership (§4.56 — a scoped-out declaration keeps a real `java.util.List` the table calls
mapped).

### 8.10 One `RealPath` — and a watch note on class-initialisation timing

**Decision.** One utility, `balticporter.core.RealPath`, replacing four divergent private copies of
§5.4's helper (each with a different, sometimes buggy, exception policy) plus two raw-path
comparisons. `RealPath.of` = §5.4's rule verbatim (realpath, `toAbsolutePath.normalize` ONLY as the
not-exists fallback); comparison forms (`startsWith`/`relativize`/`str`) beside it; a strict
`ofExisting` throws for frontend sites where a missing declared input must be fatal (§5.1). Two raw
sites fixed: a config-loader base-chain CYCLE DETECTOR compared lexically (undetected cycle through a
symlink → `StackOverflowError` instead of a config error — resolution stays lexical, the seen-set gets
`RealPath.of`); a freshness-roots site inconsistent with its neighbours (safe today, flagged by two
independent briefs). Enforcement: a source-scan spec confines `.toRealPath(` to the utility, plus an
auditor hunt line for the semantic half (a raw `startsWith`/`normalize` comparing a config path against
a parser-recorded one — no grep is built, since path-ish receivers aren't syntactically distinguishable
from FQN prefix tests).

> **Watch, unmeasured**: Java's lazy class-init is per-CLASS (JLS 12.4, constant reads inlined away);
> Scala's companion laziness is per-WHOLE-OBJECT, any member access initialising it in declaration
> order. Handled so far: constants emit `inline val` (§4.4), `static{}` blocks run at companion init.
> Unmeasured: a non-constant `static final` read forces the WHOLE companion; cross-companion init
> cycles may deadlock/NPE/diverge from Java's partial-init read; a side-effecting initialiser runs at a
> different moment. No compile error, no count moves — reread this paragraph before instrumenting any
> init-order symptom. **Bites the engine's own code too**: moving `CollectionsTransform.typeMap` above
> the four `*Fqn` vals it names caused a clean compile and a `NullPointerException` 49 tests deep — a
> table naming other vals of its own object must be declared BELOW them;
> `CollectionsHandledDerivationSpec` now asserts no target is `null`.

### 8.11 Ordering, and the interactions the briefs left open

Four cross-cutting interactions, resolved once here:

- **§8.2's `protected` primary × §8.7's mapping compose correctly without overlap**: the synthetic
  primary is not a Java declaration, so §8.7 does not reach it — it stays bare `protected` (wide
  enough for any-package subclassing, narrow enough to exclude ordinary callers). §8.7 governs every
  Java-declared constructor, which after §8.2 is every SECONDARY.
- **§8.2's D4 dissolution × §8.3's contract is ONE implementation, not two**: a dependent derives the
  synthetic signature LOCALLY for every non-wall class AND cross-checks it against `primary=` wherever
  a row exists — a disagreement is a FATAL engine bug (catches an engine-version drift between base and
  dependent runs the local derivation alone cannot see). For the 82 WALL classes the row is
  LOAD-BEARING (no local derivation exists) — §8.3's honest-scope limit applies: no local repair, refuse
  the replay and count, never demote the base's plan.
- **§8.4 and §8.5 share ONE `OverrideGraph`** (§8.5 owns it): descriptor-keyed edges, `StandardTraversal`
  (anonymous bodies are nodes), `closureOf` = owned + `externalAnchors` + `baseAnchors`, whole-component
  invariant. §8.5 refuses a frozen component with a finding; §8.4 falls back per site and counts
  `frozen-component`. §8.4 does not use `MemberRenamer` — it changes signatures, not names.
- **Ordering constraints don't conflict**: §8.5's property transform runs BEFORE every retyping phase
  (matches Java's own descriptors); §8.6's nullability runs AFTER collections (wraps their result); both
  obey §4.56 (upstream-namespace keys, package rename last).

| track | order | why |
|---|---|---|
| core-model spine | §8.1 first, alone | touches symbol minting and every keyed phase |
| emitter track | §8.2 → §8.7 → §8.8 | all three rewrite emitter regions; serialise to avoid 3-way merges |
| base surface | §8.3 after §8.1, §8.2 | contract's constructor rows ARE §8.2's signatures |
| armed phases | §8.3 before §8.4 / opaque-type | 4 of 9 drift sites are latent behind exactly those phases |
| transform track | §8.4, §8.5, §8.6 in parallel, default-off | none reads a policy key beyond a type binding |
| blocked on §8.1 | §8.3's member rows | needs an overload-exact key (call-site substitution — BUILT, §8.12) |
| checks track | §8.9, §8.10 any time | no shared files |

### 8.12 Call-site substitution, and the first RETARGET entry — as built
**Decision.** `CallSiteSubstitutionTransform(calls: Map[String, String])`: a member key naming the
resolved callee, a value that is an expression template with `{recv}`/`{arg0}…{argN}`, splicing the
call's own receiver/arguments in. `CollectionsTransform` gains an orthogonal `retarget: Map[String,
String]` table for a type that moves and is API-mapped NOWHERE. Both default-empty, no-op.

**Why**: none of the three existing whole-declaration seams (`dropTypes`+`inject`, `dropMethods`,
`MethodBodyTransform`) can say "keep this method, rewrite one call in it" (`ENGINE-LIMITS.md` D7).

- **Holes are TREES** (`Tree.Opaque.holes: List[Term]`, a NUL marker no Scala source contains;
  `holes=Nil` is byte-identical to today) — so `StandardTraversal` (hence the LAST-running package
  rename) and `Xref` still reach a spliced argument; nothing outside the emitter renders a term.
- **Bind-time exactness**, §8.1's asymmetry: a template hole out of range is `Malformed` before the
  pipeline runs; a bare key naming two overloads is `Ambiguous` (candidates listed), never picked.
  `PolicyBinder.bindCallee` is the one binder addition — a dropped member's declaration symbol doesn't
  exist, but the frontend interns the callee from the REFERENCE anyway, so this falls through to the
  symbol table on that path only (structural test can't tell reference-interning from engine-minting).
- Per-site refusals (vararg spread among args, wrong arg count, `{recv}` on a receiverless call, a
  method VALUE reference) go through ONE predicate (`siteFault`), shared by the rewrite and the
  decision-recording pass, so a `SubstitutedCall` row can never claim a refused site.
- A key that bound and rewrote ZERO sites is its own finding (not `NeverMatched` — an earlier phase
  may have already re-pointed the calls).
- `SubstitutedCall` is rendered as a porter note; `RedirectedCall` is not (a redirect keeps the call's
  shape, the diff shows it; a substitution replaces the whole expression, §4.575).

**RETARGET vs `typeMap`**: `typeMap` says both "retype" AND "kind-aware call rewrite + `coerce`
bridge"; a retarget entry needs only the first half, so it joins `remap` and joins NEITHER `kindOf`
nor any factory (a no-op on every kind-driven arm by arithmetic). Precondition the ENGINE cannot
check, the policy author owes: the Scala target must be usable wherever the java source was (e.g.
`Comparator → Ordering`, since `Ordering extends Comparator` and `compare` stays SAM-compatible — no
bridge needed anywhere). A key in both `typeMap` and `retarget` is refused. Not in `mappedTypes`/
`retypedTargets` (no boundary by construction); IS in `surfaceFingerprint` (§1.5).

**Comparator call-site table — measured, needs NEITHER new config nor new arms**: the template CAN
express `Collections.sort(xs,c)` but the shape is REFUTED (`sortInPlace` isn't a
`mutable.Buffer` member — compiled probe, wrong policy, no count moved); after the retarget the
EXISTING `JavaCollections.sort(xs,cmp)` arm is already correct (an `Ordering` is a real
`java.util.Comparator`). `Arrays.sort` ships no entry either — `Sorting.stableSort` needs a summoned
`ClassTag` a positional template can't name, and `quickSort` is not STABLE where java's is guaranteed
to be (§4.4's family — a seam may not silently trade a documented guarantee for legibility).

**Rejected**: rendering the template to text at the phase (the one region no later phase could see); a
dedicated `Tree.Spliced` node (every broad `Term` match needs an arm, silently missed by a catch-all);
a printable hole delimiter (a second text parser); deciding the overload from argument shape (§8.1:
118 `Ambiguous`/263).

**`retargetRewrites`** (per-member call-site images, keyed `(name,arity)` or `(name,Descriptor)` — a
descriptor key wins, needed for same-arity overloads like `Array`'s three arity-1 constructors, keys
written in the UPSTREAM namespace since package-rename runs last): nine variants — `Rename`,
`BoolDispatch`, `Construct` (`new Source(args)` → `Target.factory(args)`), `ForEach`, `Collect`,
`Chain`, `FieldWrite`, `IndexedField`, `Template`. `Template` holes (`$recv`, `$0..$N`, `$T0..$TN`,
`$Target`) are AST slots exactly like `CallSiteSubstitutionTransform`'s, with EVALUATE-ONCE binding for
side-effecting arguments (F7's rule). `Construct`'s element type for a raw constructor derives, in
order: the declared slot's type argument; the dropped supplier's `MethodRef` element type
(`Sprite[]::new` → `Sprite`); else REFUSE and count — never a silent `Object`-applied fallback.

**`RetargetBoundaryCheck`** (`collection-retarget`, unconditional when `CollectionsTransform` runs)
counts three kinds the position-blind retype hides from `collection-boundary`: `ExternalProducer` (a
JDK/external callee returning the retarget SOURCE type at a retargeted node — no coercion exists);
`CastToTarget` (an `instanceof`/cast at a concrete target no live view can be — refused and counted);
`IteratorRemove` — **CLOSED**: the engine emits `JavaIterator.removing`/`removingFromBuffer` keyed on
the target FQN (three variants for `ArrayDeque`, `DynamicArray`, and `ObjectMap` Collect results);
unsupported targets keep the read-only bridge and this finding kind.

### 8.13 The merge contract — how a parameterised phase's POLICY composes across manifests — as built

**Decision.** A phase may declare `MergeablePolicy` (`api`, refines `SurfacePolicy`): one method,
`mergedWith(later: Phase): Either[String, Merged]` — how MY table composes with a nearer manifest's
instance of me. `PortManifest.surfaceFold` folds the policy chain through it (same `Phase.name`,
base's pipeline POSITION preserved). No declaration = today's behaviour: both instances stay in the
pipeline, fatal `SurfaceDivergence`. Declared by `TypeRedirectTransform`, `NullabilityTransform`,
`GlobalsToImplicitsTransform`; every other parameterised phase keeps the no-merge default deliberately
(no second consumer yet — a merge rule written without one is a guess). Closes `ENGINE-LIMITS.md` D9
(a base-configured (b) phase no dependent could ever also configure).

**Every phase declares its OWN merge rule** because a `Map` union is right for some and silently wrong
for others (an ordered forwarder list, a first-match table, a `RuleScope` composing oppositely for
`Only` vs `Everywhere`) — an engine-side generic merge would repeat §1's mistake with policy in the
engine's hands. `Left(why)` refuses; the pair stays two instances and `SurfaceDivergence` fires
carrying the phase's own sentence. Three implementor obligations (stated on the trait, unverifiable
externally): preserve both inputs' behaviour on their OWN keys or refuse; be PURE and DETERMINISTIC
(the base's `effectiveSurface` is refolded by every dependent); move `surfaceFingerprint` whenever the
merged table differs from either input.

**Enforcement gaps found only by measuring, each closed**:
- A refused pair must STOP THE RUN before any phase runs, not just produce a finding —
  `Pipeline.order` previously sorted by NAME, so of two same-name instances the LATER one silently ran
  and the earlier one was dropped (measured: a base's whole holder never ran, 0 decisions, 0 errors, 0
  findings). Fixed by two changes together: `Pipeline.order` orders INSTANCES, not names (an ordering
  edge to a name binds EVERY instance); and a refused pair is FATAL BEFORE THE PIPELINE
  (`ManifestAgreement.surfaceGate`, `PortRun.execute`'s first act) — else instance-ordering alone would
  silently DOUBLE-APPLY both configurations instead of dropping one.
- "Stable in declaration order" needed a MIN-HEAP on declaration index, not a FIFO — a queue-based
  Kahn's algorithm postpones a constrained phase past every unconstrained one declared after it
  (measured: `collection-boundary` 22→20 with two member digests moved, from an inert-looking phase
  addition). Fixed corpus-wide: 0 member digests on all fifteen ports.
- The EQUAL pair needed a third answer: `SurfaceFold` used to silently APPEND a same-name instance
  whenever the fold declined to merge, including when fingerprints were EQUAL — harmless only while
  `Pipeline.order` keyed by name; once ordering instances, it became "the phase runs TWICE, with one
  policy" (survived review only because the one production case, `ClassTableTransform`, is
  idempotent). Fixed: the fold answers three ways — merge (`MergeablePolicy`), DEDUP (two instances
  provably EQUAL via `SurfacePolicy`, one kept), or fatal divergence. "Equal" is unsayable of a
  name-only-fingerprinted phase (no `SurfacePolicy`) — `Cause.Unverifiable`, always fatal. The report
  criterion is now TWO INSTANCES IN THE EFFECTIVE PIPELINE, not distinct fingerprints.

**The D1 contract**: the base is the base AS THE BASE RAN IT — `surfaceFold` folds `policyChain`
(THIS manifest's own chain), so a dependent's added phase never reaches `b.effectiveSurface`; only the
dependent's OWN effective pipeline holds the merged phase, and `PortMap.freshness` compares against
`b.effectiveSurface` alone, unchanged by this feature. Composes down an `a→b→c` chain because `b`
publishes `merge(a,b)` which `c` then folds again. A module with `mirroring` (states the base's policy
in full rather than inheriting) is checked via `bases.mergedWith(mine)` leaving `mine` unchanged — same
`mergedWith`, no second containment notion. `surfaceFold` is a `lazy val` (a merged phase is a NEW
instance holding run-mutable state; recomputed, the pipeline's instance and the report's instance would
diverge).

**The `governs` intrusion screen**, corrected twice: a dependent adding a key that edits shared surface
(a base emits a type, a dependent silently re-points references to it) is refused as fatal
`SurfaceIntrusion`. First correction: a bare PREFIX test is wrong — it would refuse `ashley` redirecting
`ReflectionPool`, which is legitimate because the base DROPS that type and ships nothing at its name.
Corrected to *inside a base's claim ∧ the base's own manifest doesn't account for it* (a drop-with-no-
injection admits; a drop-WITH-injection still refuses — the shim IS shared surface); read through
`shipsInjectionAt` in the EMITTED namespace (§4.56, since a drop key is upstream). Runs on EVERY
dependent-declared instance (`MergeablePolicy.subjects`), not just what a merge ADDS — a phase no base
has at all was slipping through untested. Second correction: "not accounted for by the base's POLICY"
is still not the criterion — the base's OUTPUT is. A namespace-sharing module (a library's own test
suite declared inside the library's own package, `AnimationControllerTest` beside
`BaseAnimationController`) is inside the base's claim by prefix but the base never parsed it — refusing
here is `ENGINE-LIMITS.md` CT9 Face A. Fixed by reading the base's PUBLISHED PORT MAP (§8.3): an entry
that is NOT `Dropped` refuses (shared surface); an entry that IS `Dropped` admits (nothing stands
there); NO entry at all admits (the base declares nothing there — `BaseSurfaceAbsent`). A missing/stale
map falls back to the stricter manifest-only rule (never a silent admission from an absent artifact).
The screen therefore no longer un-merges on the manifest alone — it reports run-time CANDIDATES that
`ManifestAgreement` (holding the published maps) resolves into refusal or admission.

**Policy-report filtering follows KEYS, not the instance**: after a merge the dependent's own declared
instance never runs, so reading its `policyReport` reports nothing (a typo silently no-ops) —
`ownPhases` resolves each own-declared phase to the effective (merged) instance and filters its
findings to the subjects THIS manifest contributed. Requires every mergeable-phase finding be keyed on
a member key or type FQN (`owner#member`), never a bare fragment — `TypeRedirectTransform` initially
keyed member findings by the bare segment, which the subject filter silently dropped; fixed via
`MemberKey.spell`.

**The `.conf` path composes through the SAME fold** — nothing added; `base = "…"` already builds
`base.extendedBy(own)`, which is the chain the fold reads.

**The merge is the only way a DEPENDENT places a phase EARLY** — an unmerged dependent phase lands at
the END of the effective surface, so its `runsBefore` edge doesn't just constrain the phase it names,
it POSTPONES every unconstrained phase in between (measured: `context-seam` moved 42→41 with ZERO
emitted bytes changed, the new phase SKIPPED for the measurement, because `type-redirect` slid past
`globals->implicits`). Fix: state the phase's POSITION first (a base declares an EMPTY instance where
it belongs — free, §1(b)'s no-op), THEN add the edge — never copy a `runsBefore` edge the phase doesn't
itself need.

**Worked merge rules**: `TypeRedirectTransform` — key-independent maps unioned, member keys compared
through `MemberKey.parseIn` (not raw strings, or `dispose`/`dispose()` silently intersect as different
members); `ExternalSurface` unions unconditionally (engine knowledge, not policy, not in the
fingerprint). `NullabilityTransform` — three tables, three different compositions: `annotations` UNION;
`target` inherits the other side's default or must AGREE; `scope` unions ENTRIES in BOTH directions
but REFUSES across `Only`/`Everywhere` directions (a merge cannot express two per-module scope
DIRECTIONS in one instance — the refusal IS the honest rendering). `GlobalsToImplicitsTransform` needed
a NEW VALUE, not just a rule: `ContextHolderExtension` (a dependent's per-declaration additions,
holding `sites`/`selfSupplied`, unioned) is split from `ContextHolder.sharedSurface` (agree-or-refuse)
because there is no field to restate a base's whole member map in (§1.5's prohibition, reached through
the merge door) — a `holders` entry with no `context` block IS an extension, a dangling one (naming a
holder nothing in the chain declares) is a counted `Malformed`.

**Rejected**: an engine-side generic `Map` union; a per-instance `Phase.name` to avoid collisions
(defeats the drift check for real drift); merging at `PortRun` instead of the manifest (a second truth
on the `.conf` path, invisible to `ManifestAgreement`); letting the intrusion screen be advisory.

**A second screen the fold cannot run**: a key that NAMES NOTHING it edits (an annotation FQN, e.g.
`org.jspecify.annotations.Nullable` — a third-party name, inside no base's claim) passes the `governs`
screen but its SELECTION (`Program.owned`, which roots on every unit including the base's, D2) can
still retype a base's own declaration. Second, PLAN-TIME screen: refuse a rewrite when the declaration's
unit is NOT one this run emits AND the key that selected it IS one this manifest contributed
(`balticporter.tir.RunScope`, held by `PolicyBinder`: `emits` from `PortRun.partitionUnits`
(realpathed, §5.4), `contributed` from `PortManifest.contributedSubjects`). Non-fatal (`policy`, once
per key, never per declaration — the emission is already correct, only the manifest's claim is wrong);
default `RunScope.whole` is a no-op for a base port by arithmetic. Corollary: an empty `governs` (`{}`,
"no claim") switches the FIRST screen off entirely — `ManifestAgreement.Kind.BaseNamespaceUnclaimed`
reports it non-fatally; `PortManifest.declaresPolicy` distinguishes this from a genuinely empty,
non-ported-module manifest.

### 8.14 The RETYPING contract — a phase declares its LANE, the pipeline observes its REACH — as built
`CLAUDE.md` §1 states what a retyping phase owes in prose; four phases discharge it, each answering
after a port hit the wall (`ENGINE-LIMITS.md` K5.6 — a NEW retyping phase can reintroduce this shape
until it too asks). Invisible without a mechanism: position-blind retyping moves both sides of a slot
together, the port compiles, no check exists so no count moves.

**Two halves from different places**: a phase DECLARES `Rewrite.accountedBy` (check-lane symbols, not
strings — a renamed lane is a compile error); the PIPELINE OBSERVES `Patch.retyped` (owned
declarations whose `info` changed, compared inside `Pipeline.runTraced`) — a phase is never ASKED what
it retyped, so it cannot be wrong about or stop maintaining that number. `rewrite-callsites` reports a
phase that moved declarations naming no lane (`Unaccounted`), and a phase naming a lane that did not
RECORD this run (`UnwiredAccounting`) — the last check to run. Counts PHASES, never usages (measured:
`usagesOf \ callSites` is not the boundary counts — 3,045 usages against 152 counted seams on libGDX
core, `ENGINE-LIMITS.md` K5.10). The wiring question is `Option` (empty when the artifact layer is off,
§5.1) so a fixture with reporting off doesn't manufacture a false "every phase unwired" finding.

First run found 2 silent phases (fixed to 0): `PrimitiveToOpaqueTransform` names `opaque-boundary`
(same shape as `collection-boundary`); `TypeRedirectTransform` names `base-surface` — a DIFFERENT
SHAPE lane on purpose, because a redirect swaps both sides of every slot together (no position-blind
residue AT A SLOT) — its residue is BETWEEN MODULES (a dependent's redirected signature vs. the base's
published map, D12). `accountedBy` means "the lane that counts where THIS phase's seams live", not
"a boundary lane" specifically.

### 8.15 The IDIOM layer — what licenses it, and the three lanes that hold it to that licence

Every other engine layer derives its mandate from a java-vs-scala DIFFERENCE (`Differences`); idiom
has none by construction (an anon SAM class and a lambda are the same value, two spellings) — so *why
change anything* must be answered before the first transformer:

> An idiom transformer ships when a hand port of THIS SAME library wrote the scala form (§3.5 — the
> SHAPE is evidence, the COUNT beside it is not license to generalise), the delta between forms is
> ENUMERABLE, and every member of that enumeration is structurally impossible or COUNTED.

Raw java populations look enormous (1,545 bean getters, 1,650 null checks, 1,588 index loops); hand
ports converted ~14% of the first and NONE of the second — a hand port fixes every caller by hand,
where a mechanical port moves whole families at once, and reading the first number as license for the
second is `ENGINE-LIMITS.md` K16's measured mistake (27→47 errors). Through the narrowing: 2
transformers with a mandate, 1 priced against NEGATIVE reference-port evidence (`SamLambdaTransform` —
both hand ports wrote the java anonymous class deliberately; ships anyway on the charter's other half,
shape strictly smaller and refusals decidable, with its refusal population published BEFORE it
converts anything), 6 refused with an argument.

- **Never a beautification backend**: `CLAUDE.md` §6 admits one rendering-only backend (imports); every
  idiom transformer changes a TIR declaration/term, so the layer is answered NO to that question once,
  here.
- **Safety argument is a REFUSAL ENUMERATION, never a suite result** — suites are necessary and not
  sufficient (this repo's own receipts: dropped `static{}`, dropped `super(args)`, 156 silent anon
  bodies, K21 face 2 all compiled green with flat counts). Each transformer's behavioural delta is
  enumerated: impossible by GUARD, impossible by the emitted SHAPE, or COUNTED — a transformer that
  cannot enumerate does not ship. `SamLambdaTransform` ascribes the lambda to the interface type,
  which removes overload resolution from the enumeration entirely (the argument's type at every callee
  doesn't move) — cheaper than a guard.
- **`idiom(converted|refused|residue)` — three lanes, one log**: `refused=0` is a bar met by converting
  nothing; the refusal population (one row per site, naming the GUARD) and the unrewritten-usage
  residue are reported apart, denominator recomputed every run. Data comes from the PHASES, never a
  second walk (a re-derived "would this have converted" check is a second answer free to disagree —
  K2.5's measured shape). All three in `RequiredChecks` for every port, even idiom-less ones. A phase
  DECLARES its kinds (`IdiomPhase.idiomKinds`) so "0 considered" (phase ran, found nothing) is
  distinguishable from "no row" (no phase).
- **The layer is §1(a), the RUN weaves it — no switch, no `RuleScope`, no fingerprint, no manifest
  entry**: SAM-ness and single-method-body are structural facts, not library policy. The one exception,
  the bean collapse, is a refinement of `bean-properties`' EXISTING include list (§8.5), not a boolean.
- **A census retires the moment its transformer ships** — a census IS the transformer with the rewrite
  removed, so once wired it's a second answer to a question the phase already answers (deleted the
  commit its transformer landed).
- **A census is itself a PHASE, at the position its transformer will occupy** — a dry run under-counts
  (§5's dry-run rule) and a post-pipeline census over/under-counts against a tree other surface phases
  already moved (a `Comparator→Ordering` retarget changes what SAM conversion would ascribe to); the
  wave gate (0 member digests on all 15 ports) is what PROVES a census-turned-phase is inert at its
  position.
- **The SAM oracle lives in the FRONTEND**: "is this a functional interface" (JLS 9.8) is a class-file
  fact the TIR cannot answer (an unreferenced interface method has no interned symbol) — deriving it
  from what's interned would be reading what this run happened to PARSE (§4.56's wrongful-seal
  failure). `SpoonTir.samAnswerOf` asks it once at the shadow model; `Sam.Answer.Unreadable` is a
  FIRST-CLASS answer (never `false`) and is counted, or an incomplete classpath reads as no SAM sites.
- A `this`-binding guard (java anon `this` = the anon instance; scala lambda `this` = enclosing class)
  is blind where `this` is the TARGET of a member access rather than a value — Spoon reports no `This`
  node there, so the fallback is a bare lexically-resolved reference matching java's own resolution
  exactly (`SpoonTir.thisOf`). Zero corpus sites; caught by a fixture, not the corpus.

### 8.16 Per-location remediation SELECTION — a MENU the phase publishes, one word the port writes
**Decision.** Where a difference has NO single right answer, the phase/check that MINTS a residue
finding declares `Remedy`s it could carry out; a port SELECTS one per location
(`PortManifest.resolutions: Map[MemberKey, remedy-id]`); the engine applies it and BOTH halves are
counted. Shipped with NO menu initially (0 members changed, 0 fingerprint moves — §1(b)'s no-op held).

- **Key**: `MemberKey` (`owner#name` or `owner#name(P1,P2)`), upstream namespace, PER MEMBER — a
  selection BROADCASTS across sites of the same finding kind inside one member (no line-number-shaped
  fragility); an overload set is NOT broadcast (bare key on 2+ overloads is `Ambiguous`).
- **`Remedy`** (declared by `RemedySource`): `id` (globally-unique kebab slug — flat key, no compound
  `(lane,kind,id)`), `lane`+`kind` (which residue row it drains), `emissionAffecting` (puts it in
  §1.5's MUST-agree column), `fix` (§1 classification, reuses `catalog.FixKind`). Two vocabularies:
  ACTIVE (what this run's pipeline+`PortRun.CheckRemedies` holds) vs. KNOWN (that plus what the
  classpath DECLARES via `TransformFactory.remedies` — lets the loader distinguish a typo from a
  correct-but-unenabled remedy). Duplicate ids refused; same remedy declared twice (factory+phase) is
  one entry.
- **Home**: `PortManifest.resolutions`, precedent `verdictOverrides` (dispatched by finding kind,
  consulted by more than one phase — disqualifies `RuleScope`, owned by one phase). Read through
  `PolicyBinder.resolutions`.
- **Inheritance** (§1.5's strictest case — a remedy decides emitted text a dependent compiles
  against): `effectiveResolutions` unions bases-first; SAME key, different value anywhere in the chain
  is fatal `ResolutionDivergence` (sibling of, not an instance of, `SurfaceDivergence` — one key
  composing WRONG vs. two instances that couldn't compose); a dependent key inside a base's `governs`
  claim that the base's `resolutions` doesn't answer is fatal `ResolutionIntrusion` (screened through
  the base's PUBLISHED MAP, not its claim — D10's reason). Both asked in `surfacePairs`, load-bearing
  (stop the run before any phase runs). Selections join the surface fingerprint.
- **A third staleness state, `PolicyIssue.NeverApplied`**: a selection can bind perfectly, name a live
  remedy, and still do nothing because the finding it resolves never fired this run (§5.5's
  `expected#derived`/`#declared` split one artifact over).
- **Two spellings of one member** (`Foo#bar` vs `Foo#bar(int)`) can both name one overload across a
  chain — `ResolutionPlan.selected` takes the first match, the second falsely reads `NeverApplied`; all
  cross-manifest comparisons must use `MemberKey.mayNameSame`, never string `==` (§4.56 again — both
  directions were live bugs). One member CAN hold two selections on different (lane,kind) rows —
  grouping tests `Remedy.overlaps` (could these answer the SAME row?), not "same lane" alone.
- **Accounting** reuses the `remediation` check, new kind `resolved` (not a new top-level lane).
  `finding` and `Decision` come from ONE value (`AppliedResolution`); `SelectedRemedy` is
  `PorterNote.Rendered`. A resolution must DRAIN the lane it names by exactly the count that moved
  (`CLAUDE.md` §5).
- **Config**: `resolutions { "owner#member" = "remedy-id" }`; loader validates the id, `ConfigView`
  stays dumb; a predicate-selected remedy cannot survive the door (write a §1(c) rule instead).
- **One gap, closed with the first menu**: a `mirroring` module (`inherit=false`) had no counterpart
  to `MissingDrop`/`SurfaceMissing` for "did this module answer every base selection" — closed with two
  comparisons per base in `surfacePairs`, scoped to `!inherit` (an inheriting module's omission half is
  vacuous by construction).
- **Menus so far**: the boundary trio (`collection-boundary`, `context-seam`, `nullability-boundary`),
  all `accept`-shaped, NOT emission-affecting; `overload-risk`'s `ascribe-javac-choice` (the FIRST
  emission-affecting remedy — writes a method-value ascription pinning javac's already-RESOLVED, never
  inferred, callee; refuses per site where the alternative can't be written, counted) and
  `accept-risk`; `heap-pollution`'s `acknowledge` (reproduces java's own `@SafeVarargs` conversation,
  answers `Unacknowledged` alone). `overload-risk`'s three kinds are JLS 15.12.2's three resolution
  phases, all asked of ONE call — `Remedy.alsoKinds` lets one remedy drain more than one kind of its
  OWN lane, never across lanes (§5's `<lane> N->M / resolved 0->(N-M)` stays one subtraction).
- **ONE POLICY, ONE SPELLING**: every candidate act is screened against existing spellings before
  becoming a remedy — has one → NOT added, the menu documents a POINTER instead (`scope`, `typeMap`,
  `reflectiveSinks`, etc.); is refused → absent with its `ENGINE-LIMITS.md` id in code at the
  declaration; has none → becomes a remedy. Measured: 6 entries survived of ~20 candidates on the
  boundary trio, and all 6 are the SAME act (*the port has read this site and states the residue is
  right here*), which had no existing spelling anywhere — why those counts could only ever go up. Every
  produced entry is `emissionAffecting=false` (an act that changes text already has a key).
- **`Remedy.subject`** (`OwnedMember`|`OwnedType`|`ExternalMember`) — the remedy declares which kind of
  key binds it, because two residues disagree with the member-key default: a type-level residue
  (`CT7`'s unconstructed-thread — as many constructors as java gave, no single member to name) and an
  external-callee residue (an egress row is about the CALLEE, deduplicated by callee, not by the
  enclosing owned declaration). `Resolution.key` is `Option[MemberKey]` (absent for a type subject —
  no `MemberKey(fqn,"")` fabrication).
- **`ResolutionPlan.drain`** is ONE function (not one per check), partitions+records in one traversal,
  keyed per-caller on the remedies THAT CALLER declares (not a bare lane name) — needed once a lane
  gained two declarers (`PortabilityCheck.AcceptJvmOnly` beside `RemediationTransform`, both
  `Remedy.AnyKind`) or one source's drain could silently absorb the other's refusal.
- **Rejected**: a compound `(lane,remedy)` key (redundant — the id already determines both); a
  `RuleScope`-shaped phase parameter (spans phases/checks, `RuleScope`'s disqualifying property); a new
  `FixKind` enum (reuses `catalog.FixKind` — kept as a DIFFERENT TYPE from `RemedyHint`, advice a human
  carries out vs. a named engine-performable alternative).

### 8.17 `serviceProviders` — the one deliverable of a port that is not `.scala`
The engine emitted `.scala` only, so a `ServiceLoader` descriptor (`META-INF/services/<X's FQN>`)
had no carrier — the quietest failure class (`ENGINE-LIMITS.md` P5: reflectively-constructed
providers mean the closure sees no instantiation, "not registered" reads as a plausible wrong answer,
no compile error, no count moves).

**Decision.** `PortManifest.serviceProviders` (§1(b), empty default = no-op; 14/15 corpus ports write
nothing). It is a REWRITE, not a copy: both the descriptor's FILE NAME (an interface FQN) and its
LINES (implementation FQNs) go through the run's own `emittedName` (the rename phase's full rule —
`typeRenames`/`subPackages` too, not just `packageRenames`). Five decisions: NOT inherited (`inject`'s
line — one module ships each descriptor; the DROPS that decide whether a provider still exists ARE
inherited); per SOURCE SET (`SbtGen.managedResources(root, config)`, not beside licence notices, which
are per-module); drop lookup asked of the UPSTREAM name (a `dropTypes` key read in the emitted
namespace is the wrong-namespace bug `dropped-types.tsv` carried for the life of every renaming port);
a declared-but-missing file is FATAL; write is NOT gated on the artifact layer (scoped by the empty
default and `src_managed/` instead). Lane `service-providers`: `dropped-provider`, `dropped-service`,
`unrenamed`, `empty`, positive rows — required CONDITIONALLY (only when the manifest key is
non-empty, derived from the same declaration). Rejected: scanning a resource root (ships a shaded
dependency's descriptor by accident); rewriting a two-token line (not a class name — §4.6's
fabricated fact; carried verbatim, uncounted).

### 8.18 The FIRST menu — the portability four, as built

First lane to exercise §8.16's plumbing — the one `Remediator` already wrote manifest lines for.
**Four remedies**: `RemediationTransform` declares `substitutions-drop`, `static-forwarder-inline`,
`class-table` (ids = `Remediator`'s own `mechanism` strings); `PortabilityCheck` declares
`accept-jvm-only` (first in `PortRun.CheckRemedies` — no pipeline phase collects a plain-object
check's rows). Precondition reused from `Remediator.suggest` itself (never re-derived — §4.6);
`Suggestion.payload` is the machine-readable half of the same expression that builds the snippet
(never parsed back from text, §4.56).

Corrections the first real lane forced: a remedy DECLARES its key shape (`Remedy.Subject` — two of
these name a TYPE, no member to select at); `Remedy.AnyKind` for a lane whose kind column is an open
set of hundreds (an FQN) rather than a closed enum — `alsoKinds` enumerates, `AnyKind` matches on the
id alone; `AppliedResolution.drained` is `sum(drained)` not `count(rows)` — an inline claims 0 (it
RELOCATES, doesn't remove; over-claiming breaks the arithmetic) and so does a redirect whose lane-row
is a `Class.forName` inside the wrapper's body the rewrite doesn't touch; `Resolution.RefusedKind` — a
remedy that verified its own precondition and declined is not `NeverApplied` and not silence (four
guards: `needs-injection`, `not-a-chokepoint`, `no-table`, `targets-contradiction`).

`RemediationTransform` is DECLARED in `surface` (it has a real parameter — `class-table`'s
destination); the boundary-trio checks are WOVEN (no constructor policy, config lives entirely in
`resolutions`). A second parameter, WHICH BACKENDS (originally `RemediationTransform(targets)`,
defaulted to all three), was a manifest field spelled twice — removed, now read off `RunScope.platform`
(carrying `targets` AND `verdictOverrides`) rather than duplicated on the phase — the general rule: a
phase parameter that restates a manifest field is a divergence with no instrument. A site the key
cannot name (a local inside a method body) is asked up the OWNER CHAIN (`PortRun.ownerChain`), not via
a finer key. A printed key and a bindable key must be the SAME shape (`Remediator`'s
`java.lang.Class#forName` template was rewritten to say nothing rather than print an unbindable key).
Not offered: a MEDIUM-grade engine-written drop replacement (library semantics, §1 refuses); a
`dropMethods`-scale partial drop (the port's judgement, not derivable); an invented `class-table`
destination.

### 8.19 Platform-divergent JDK FAMILIES map to `multiarch-scala` — the decision, ahead of its build

**Decision** (maintainer, 2026-08-14): where a JDK family exists on all 3 platforms with DIFFERENT
mechanics (`java.util.ServiceLoader` first, JNI-adjacent loading standing second), the mapping target
is a cross-platform wrapper in **multiarch-scala** (`com.kubuszok`), never a per-port shim or
JVM-only acceptance. Split: the WRAPPER is multiarch-scala's deliverable; the TRANSFORMER is this
engine's — a §1(b) redirect, same class as `scala-java-time` in the catalog's `Depend` rows.

**AS BUILT**: Scala Native's `ServiceLoader.load` is a link-time INTRINSIC requiring a literal class
argument — a `Class`-taking wrapper cannot satisfy it, so Native resolves by REGISTRATION off the same
`META-INF/services` descriptors §8.17 ships, exactly like Scala.js (this is WHY the wrapper exists,
not a thin passthrough). The transformer is TWO EXISTING KEYED SEAMS, no new phase: `TypeRedirectTransform`
moves the type (java's `ServiceLoader` is a factory+handle; scala splits them, `load` returns
`ServiceProviders[T]`), `CallSiteSubstitutionTransform` moves the static call — in that order (the
redirect mints a static twin first, or the substitution's callee symbol occurs nowhere). The wrapper's
`iterator()` returns java's ARITY but a `scala.collection.Iterator`, not java's — `CollectionsTransform`
retypes it to `JavaIterator` (§4.5), substituted ahead of the collections phase. Every mismatch here is
a SLOT the compiler catches (§3). One UNCLOSED delta the compiler cannot see: off-JVM, `load`'s
registration lives in a generated object body nothing in a ported LIBRARY ever forces — silently empty
iterator on a future JS/Native build. Counted, not fixed: `service-providers` files
`off-jvm-unwired` per descriptor when `targets` reach past the JVM (`ENGINE-LIMITS.md` P9).

### 8.20 What an ARTIFACT provides is READ FROM THE ARTIFACT — the provides-set, and the 2×2 it feeds

`PortManifest.dependencies` checked both directions (`dependency-coverage`, `policy`); the `policy`
(unused-coordinate) direction was asked of ONE program (the emitted one), which was WRONG for §8.19's
shape — a redirect removes the very JDK usage the coordinate answers, so the port's most load-bearing
coordinate read as unused (`ENGINE-LIMITS.md` P8).

**Decision** (maintainer, 2026-08-14): an entry's state is a 2×2 PAIR — pre-pipeline usage × emitted
usage. EMITTED alone decides keep/remove; ORIGINAL decides the SENTENCE (`Stale`/`Unused`/`Introduced`
want different reader actions). `ArtifactIndex` derives the provides-set FROM THE JAR (resolve,
enumerate class entries, match program references) — never a guessed coordinate-to-package link
(§4.56's hazard at a build coordinate). Matched against TWO reference kinds: interned `ExternalUsage`
symbols, AND the dotted names inside every `Tree.Opaque` (`DependencyCheck.splicedNames`) — a
substitution template names an artifact the symbol table holds nothing for. Five properties:
`--intransitive` (else `scala-library` pollutes every port); the coordinate built EXPLICITLY, never
`cs`'s `::` (an ambient machine fact); the JVM jar only, stated as a limit (a cross-published
artifact's shared surface is approximated by it); THREE-VALUED (`Unverifiable` when a jar can't be
fetched — never collapsed to yes/no, §4.6); a `cs` output line is a PATH (told apart by existing, not
by shape — a naive whitespace test drops every real jar on macOS's spaced cache path); catalog checked
FIRST (jar consulted only where the OLD test would already report — 14/15 ports touch no network, the
change can only turn a finding OFF).

Published as `run-latest/dependencies.tsv` (one row per declared coordinate, read by
`scripts/_lib.sh`'s `declared_dep_flags` — a coordinate can now only be wrong in one place, closing a
3-way restatement bug where a manifest revision bump silently diverged from the measure lane's jar).
NOT a baseline — an INPUT; `dependency-coverage(declared)` is the required positive lane (§5's trivia
argument one seam over — an artifact a phase redirected into had no row on either usage lane, which is
how P8 stayed invisible).

### 8.21 The LAST two menus — `omissions` and `jdk-surface`, and the line an ACCEPT is cut on
The two largest per-site residues with no menu (`omissions` 97 rows, `jdk-surface` 57), and the lane
that forced the harder question: a menu is licensed only where the ENGINE HAS DECLINED TO DECIDE —
evidenced by the mechanism's OWN doc comment saying so, never by "measured worse". `omissions` has
SEVEN kinds; two are licensed accepts because the mechanism itself refuses to compute an answer
(`CtorFunnel.Plans.promotionEscapes` — "deliberately NOT a purity question about the body";
`FrontendConfig.preservedAnnotations` — "a fact about a library, never about java", T16): remedies
`accept-promoted-body`, `accept-dropped-annotation`/`accept-dropped-type-annotation` (two ids for one
act — a `@SuppressWarnings` sits on either a TYPE or a MEMBER subject; one id would leave half the
population undrainable). The other five kinds are LOSSES (no accept — an accept would drain a defect
nobody would revisit): `super(args) dropped` (C3), `nilary constructor dropped` (C11),
`Throwable(cause) message dropped`, `anonymous-class member dropped` (T1, an engine gap), and
`lambda return with unnameable result type` (a WORK ITEM, M6/I9, not a refusal — accepting would
silently retire it). `jdk-surface`'s three kinds get ONE entry, `accept-jdk-member`: `unhandled` is
coverage by COINCIDENCE (not a `Refusals` entry — that would silence the row for all 15 ports at
once); `kept-iterable` takes nothing (the row IS a real compile error, K9); `stale-refusal` takes
nothing (two engine tables disagreeing, a fact about no port). Both are `RemedySource`s the CHECK
declares and drains in its own traversal (no phase, boundary-trio shape); rows with no nameable
declaration take `SymId.None`, making them UNSELECTABLE rather than falling back to an enclosing unit
that would over-drain.

### 8.22 `resources` — the descriptor's COMPLEMENT, and the second deliverable that is not `.scala`

§8.17's sibling: a port whose emitted code reads its own classpath resource (a properties table, a
skin, i18n bundles, shaders) ships nothing at that path — silent failure, no compile error, no count.
**Decision**: `PortManifest.resources` (§1(b), empty=no-op). §4.56 decides the act: a
`META-INF/services/<X>` descriptor is FQNs (REWRITE both namespaces, §8.17); every OTHER resource is
bytes located through a string literal no rename may touch (COPY verbatim, path included). Otherwise
identical to §8.17: not inherited, per SOURCE SET, write not gated on the artifact layer, a
declared-but-missing file is FATAL. DECLARED never scanned (measured: of 24 files under one library's
resource root, 2 belong to the upstream BUILD, not the library — a scan ships junk the reference hand
port doesn't). Lane `resources`: positive rows, `named-unshipped` (a literal names an existing,
undeclared file — the ONE row requiring the engine to walk a resource root, proposing where the
manifest disposes), `unnamed` (shipped, nothing references it — legitimate, stated not repaired,
§8.17's `unrenamed` one key over), `empty`. What the program NAMES is read off literals matched
against `checkedUnits` only (D2 — a dependent doesn't answer for its base's lookups). The resource
tree is now WIPED with the emitted sources (previously nothing did — a stopped declaration would
leave a stale file on the consumer's classpath, the one state `src_managed/` exists to prevent).

### 8.23 `api-parity` — the hand-port surface comparison, and the families that classify it

**Decision.** §1(b): MECHANISM (parse both Scala surfaces with scalameta's Scala 3 dialect — §4.56,
same parser both sides; public/protected only) in the engine; POLICY (which hand-port tree, which
package mapping, which header markers make a hand-port file a PARTY) per-library in
`PortManifest.parity: ParityRef`. Empty = no-op, records nothing. A check
rather than a script, so it lands in `findings.tsv`/baselines/`RequiredChecks`
(required-when-declared, like §8.17/§8.22). Fifteen families (`ApiParityCheck.Families`, the enum is
the count — never restated in prose, which went stale once already), each its own lane
(`api-parity(<family>)`), `unclassified=0` the gate: `accessor`, `static-placement`, `mutability`,
`rename`, `visibility`, `hand-port-extra`, `hand-original`, `port-extra`, `null-model`,
`collection-retarget`, `opaque`, `operator`, `factory`, `file-merge`, `signature`, plus
`unclassified` (the work list). NOT inherited — a hand port is a fact about THIS module's
destination, not shared surface.

**Precision of the two surfaces.** Both are read the same way, and both defects were measured on the
same run: a hand-port FILE whose header (first 40 lines) names none of `ParityRef.upstreamMarkers` is
the hand port's OWN code — it is listed once per top-level type as `hand-original` and compared
against nothing, and an emitted type of that name leaves the comparison with it rather than becoming
`port-extra` (empty markers = every file a party, §1b's no-op). And SURFACE is what is reachable from
outside: a direct member of a template body, of a top-level scope, or of an extension group. A
declaration inside a method body, a block or an inaccessible template is neither, on either side.

### 8.23b The JDK a lane compiles with — `jdk_version`, and why `-release 17` is a different number

sge's `-release 17` states the BYTECODE/API target, not a JDK to build on. What a measure lane's JDK
decides is which class files the FRONTEND resolves external members/modifiers from. `Justfile`'s
`jdk_version := "22"` is the state every committed baseline was measured on — moving it is a
measurement change, acknowledged by re-accepting every baseline, never absorbed. The 17-vs-22 gap is
real and deliberate; nothing in the port depends on it (`flags_compile` already compiles under the
reference build's own `scalacOptions`, and `-release` is not among them).

### 8.24 The drop-in gate — the emitted port INSIDE the reference repo's own build

`just <module>-dropin`: the emitted tree replaces the module's hand-ported files inside sge's/ssg's
OWN sbt-projectMatrix build; the full suite must pass JVM+JS+Native. Runs against a disposable `git
clone --shared` (never the live checkout — the lane deletes/rewires files; `--shared` gives a real
`.git/` dir so `sbt-git`'s jgit resolves HEAD, unlike a `git worktree add`'s `.git` FILE which
threw `MissingObjectException`), under `.balticporter/dropin/` (gitignored). Wires the emitted tree
via `unmanagedSourceDirectories` (never copies — §5.5, keeps `git status` meaningful) through a
`project/dropin.scala` AutoPlugin. Replaces every file whose header matches the module's `Ported
from` pattern; keeps everything else, including hand-added tests with no port header — those compile
against the emitted surface, which IS the gate (a public-surface divergence is a compile error there).
NOT in `measure-all` (expected red until parity); `just dropin-all` is the separate aggregator.
Per-platform baselines (`baseline/dropin/expected-errors.<platform>`,
`baseline/dropin/tests.<platform>.tsv`), gated both directions. Discovers and records the reference
build's `scalacOptions` (`-no-indent -Werror -Wunused:…` — a file green under scala-cli defaults can
be a syntax/warning error there); enforced by a fourth compile, `port-<module>-ref` sbt projects
(shares the port's generated sources, compiles under the reference repo's own flags; a dependent's
`-ref` project `dependsOn` the base's JVM row with `-nowarn` so warnings aren't double-counted).
`just deps-lint` verifies `build.sbt` coordinates agree with the manifest. Ports are `projectMatrix`
subprojects of THIS build (`port-` prefix, JVM/JS/Native rows), each worktree running its own sbt
background server (`SBT_GLOBAL_SERVER_DIR` keyed on cwd hash — `ENGINE-LIMITS.md` M5.11, sbt 2's
`sbtn` socket collided across worktrees sharing a `.git`).

### 8.25 The divergence census and its verdicts

`just <module>-divergence` reads `api-parity` findings from the non-surface-only families
(`hand-port-extra`, `port-extra`, `mutability`, `accessor`), enriches each with the hand-port file's
own `Migration notes:` header evidence, and censuses hand-added test files. Produces
`divergence.tsv` (module/kind/subject/java_says/hand_port_says/evidence/status/spelling/decided_by),
baselined content-wise like `findings.tsv`. The `divergence-investigator` agent resolves each row
against the reference repo's git history/docs/`.rescale/data` into one of four verdicts: `justified`
(a recorded decision — becomes engine policy: a manifest key, phase parameter, §1(c) rule, or
injection), `unjustified` (no recorded reason — a hand-port defect; java's behaviour wins, test
adapted/dropped), `version-skew`, `not-a-divergence` (a census artefact). Verdicts live in the
COMMITTED `ported/<module>/divergence-verdicts.tsv`, joined into `divergence.tsv` on every run — a
verdict for a subject the census no longer produces is reported stale. NOT in `measure-all` or
`dropin-all`.

### 8.26 Unused-symbol handling (`UnusedSymbolTransform`)

A late §1(a) phase (derived unconditionally by `PortRun.derivedPhases`, no configuration) removing
unused locals/private members — java has no `-Wunused` equivalent and sge/ssg's strict flags
(`-Werror -Wunused:locals,privates,patvars,nowarn`) need it. Runs BEFORE `SuppressionPhase` and
`package-rename`. Translation order: DELETE (side-effect-free, unreferenced); DISCARD (unreferenced
but the init MAY have effects — keep as a bare statement, drop the binding); SUPPRESS (`@nowarn` —
`serialVersionUID`, write-only vars); REFUSED (side-effecting private inits a `MethodBodyTransform`
substitution might reference invisibly to the TIR walk, `ENGINE-LIMITS.md` T26.2). Required an
emitter fix: `TirEmitter.valDef` now calls `annots(sym(v.symbol), i)` (annotations on val/var were
previously silently dropped). Lanes `unused-symbol(handled|refused)`, unconditional. Read/write
distinction from ONE `StandardTraversal` walk (`allCounts` vs `assignCounts`; `IncDec` counts as
both).

### 8.27 Drop+inject a trait-shaped base type, re-parent direct subclasses with the widest primary (`ClassToTraitTransform`)

A hand port may reshape an abstract class into a trait (sge's `Pool[A]`, verdict justified/api). The
engine cannot DERIVE the trait — trait init order differs from class init order (a trait field reads
before a subclass's `override val` is assigned); emitting a trait directly from the TIR measured
0→20 errors. So the type is DROPPED+INJECTED as hand-written Scala, and `ClassToTraitTransform`
rewrites subclasses: (1) the nominated `ClassDef` loses its constructors in the TIR, mapped
parameters become abstract `val`s, `isTrait` set — so `CtorFunnel` sees nothing to replay; (2) every
direct subclass (named or anonymous `new`) gains `override val` members bound to its `super(args)`;
(3) multi-root super-calling constructors are rewritten into `this(...)` delegations targeting the
widest, so the funnel promotes it as primary. §1(b): mechanism engine, policy `.conf`
(`specs { "com.foo.Pool" { params = [...] } }`), empty=no-op, `SurfacePolicy`+`MergeablePolicy`.
Paired with §8.28's `InjectedSurface`: gdx 0/0/0 after both. `ENGINE-LIMITS.md` CT12.

### 8.28 Overrides and calls follow an injected parent's surface (`InjectedSurface`)

An injected file may declare a surface differing from java's (a wildcard bound, a parenless method, a
renamed type); an emitted override/call must adopt the INJECTED shape or it doesn't compile.
Rejected: constraining every injection to match java exactly (ruled out sge's own
`Pool.freeAll(DynamicArray[? <: A])`; the earlier per-injection rewrite workaround didn't scale).
`InjectedSurface` parses injection roots once with scalameta (same parser as §8.23), builds a
`Surface` keyed `(ownerFqn, memberName, arity)`. Two emitter consumers: `injectedOverrideTypes`
(rebuilds an override's parameter `TypeRepr` from the injected type string, substituted through the
child's `extends` clause) and `calleeHasParens` (a call to an injected member follows the injected
arity, not java's). §1(a) universal. `ENGINE-LIMITS.md` K35 CLOSED.

### 8.29 Hand-port-added members as an ADD-scoped phase (`AddMembersTransform`)

A hand port may add members java never declared (sge-ecs's `Engine.registerComponentFactory`,
replacing reflective `ClassReflection.newInstance`). Neither existing seam expresses it: `inject` is
a whole file (freezes 100+ mechanically-translated members against engine improvements),
`MethodBodyTransform` replaces a body but can't ADD one. `AddMembersTransform` appends verbatim Scala
text at statement position at the end of the nominated owner's body — name, arity, source text,
`Reason`; `Decision.Kind.AddedMember`, `PorterNote.InBody`; target compiler is the gate (uncheckable
by the engine, `MethodBodyTransform`'s contract). §1(b): mechanism engine, policy per-library, empty
map=no-op, ADD-scoped `Only(Set.empty)` default, `SurfacePolicy`+`MergeablePolicy` (independent
owners union, same owner+name refuses). `.conf` key `add-members`. ashley drop-in 4/4/4; gdx 0/0/0.
