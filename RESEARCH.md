# Baltic Porter — Deterministic Java→Scala 3 Porting Engine

Research report, 2026-07-17. Sources: exploration of the sibling repos
(`../sge`, `../ssg`), primary-source web research on production transpilers,
LLM-era translation projects, and incremental-computation systems. Facts below
were verified against repos/docs/papers by dedicated research passes; the
handful of unverified items are flagged inline.

---

## 1. Verdict up front

The idea is sound, the architecture is not novel — it is the *convergent*
architecture that every production transpiler independently arrived at — and
this project starts with two assets almost nobody else has:

1. **A written rule catalog.** sge and ssg already document the transformation
   set as prose: `docs/contributing/conversion-rules.md` (sge, 19-rule
   pipeline), `docs/contributing/conversion-rules-java.md` (ssg, 19 steps),
   plus `type-mappings.md`, `control-flow-guide.md`, `nullable-guide.md`,
   `code-style.md` in both. The engine's rule set is largely a formalization
   of documents that already exist.
2. **A validation corpus of ~2,600 audited file pairs.** Both repos keep
   pinned upstream sources in `original-src/` (git submodules) and a per-file
   manifest (`.rescale/data/migration.tsv`) mapping each Java file to its
   Scala port *with the exact upstream commit it was synced against*
   (`source_sync_commit`). sge: 1,306 rows, 1,307 audit passes. ssg: similar
   scale for flexmark (1,100 Java files → 773 Scala) and Liqp (135 → 130).
   Every accepted, human/agent-audited port is a golden test for the engine:
   run the engine on the original, diff against the accepted port, and every
   discrepancy is either a missing rule or a rule bug. This turns "is the
   transpiler correct?" from a trust problem into a measurable convergence
   metric.

The core motivation also survives contact with the evidence. The documented
failure catalog in `ssg/docs/plans/remediation-2026-06.md` (anti-cheat items
C1–C16: migration rows marked `ported` with no file, `full-port` covenants on
23%-coverage files, 1,507 tests pinned expected-fail so CI reads green, fake
"API not implemented" comments, audits later retracted as wrong) is
independently mirrored in every LLM translation project that published data:
Bun's Rust port had a stub incident caught by its own gates; the Luau→Rust
project measured its best volume model at **76.9% "it compiles" acceptance but
only 24.6% survival** against real test oracles. Determinism is not an
aesthetic preference here — it eliminates precisely the silent-divergence
taxonomy both your repos and the field have documented.

---

## 2. Prior art — what the field converged on

### 2.1 Production rule-based transpilers

Every serious one has the same four-stage shape:

| Project | Frontend | Own IR | Passes | Backend |
|---|---|---|---|---|
| c2rust | Clang-as-library (typed AST → CBOR) | typed C AST + CFG | relooper, translator | `syn` AST → printer |
| j2objc (Google) | javac (migrated from JDT) | own JDT-shaped AST | `translate/` passes | Obj-C emitter |
| j2cl (Google) | javac (migrated from JDT) | own AST | dozens of named passes | Closure JS / Wasm / Kotlin |
| nj2k (JetBrains) | IntelliJ Java PSI + resolve | **JK tree** | ~54 single-concern conversions | Kotlin printer + resolve-driven postprocessing |

Load-bearing lessons:

- **Real compiler frontend with full type attribution is non-negotiable.**
  Syntax-only conversion is what killed Scalagen (the most serious prior
  Java→Scala attempt, dead since 2015, pinned to a pre-Java-8 parser).
  Overload resolution, boxing, `==` vs `equals`, SAM targets all depend on
  resolved static types.
- **Insulate via your own intermediate tree.** j2objc and j2cl both survived a
  complete frontend swap (JDT→javac) because passes never touched the
  frontend's AST directly.
- **Trivia (comments/whitespace) must be first-class IR from day one** if the
  output is adopted as source. nj2k carries formatting on JK nodes and
  re-emits it; c2rust *lost* comment preservation when it moved to `syn`
  (which has no trivia model) and never got it back.
- **Per-file translation units + the target compiler as the link-time gate.**
  j2cl compiles files individually and defers all whole-program checking to
  Closure. For us, `scalac` on three backends is that gate for free.
- **Escape hatches are a small standard kit:** annotations the source
  compiler tolerates but the engine interprets (j2objc's `@Weak`,
  `@ObjectiveCName`, `@GwtIncompatible`), per-declaration hand-written
  companion files (j2cl's `.native.js`), and an exclude/strip mechanism.
- **Meta's Kotlinator** (40k+ files Java→Kotlin): deep build → ~50 Java→Java
  preprocessing codemods (mostly nullability propagation) → headless
  deterministic J2K core → ~150 Kotlin→Kotlin postprocessing steps →
  build-error-driven repair loop. *Pre-normalize in the source language,
  post-normalize in the target language, keep the core deterministic* is a
  proven decomposition.

### 2.2 The LLM-era graph approach (the Jankiewicz post)

Verified against his public repo (`pjankiewicz/luaur`, `docs/TRANSLATION.md`):

- He built a **Typed Semantic Translation Graph**: ~15k nodes
  (record/method/function/macro/enum) with typed edges (declares, calls,
  type-uses, includes, inherits). "The graph — not the file tree — is the
  unit of work."
- **Tarjan SCC condensation + bottom-up topological translation**; each unit's
  prompt/context includes the already-translated signatures of its
  dependencies. Cyclic clusters (SCCs) were withheld and landed as
  hand-designed units "whose stub shape is a contract."
- Gates per unit: compile in-tree + a **drift check** (no dropped
  declarations, no module-exclusion fake-greens, no stubbed logic). Failures
  reverted and re-queued.
- Oracles ranked: byte-exact differential vs the reference implementation
  (his bytecode differential caught 6 bugs that 5,347 ported tests missed) >
  ported upstream test suite > compile.
- Numbers: 205k LOC C++ → 420k LOC Rust in ~15 days + 1 week fuzzing, ~$400
  of API credits plus subscriptions; cheap batch models landed 77% of units.
  He also had agents build deterministic side-tools and then ran the tools
  mechanically — the "agents build the tool" pattern you referenced.
- The academic line (AlphaTrans FSE'25, Syzygy, PtrTrans, RustMap,
  C2SaferRust) independently rediscovered the same skeleton: dependency graph
  → SCC condensation → topo bottom-up with dependency signatures in context →
  per-unit validation. C2SaferRust is the cleanest published version of the
  division of labor we want: **deterministic transpiler does the bulk, LLM
  polishes idioms afterward, tests stay green throughout.**

The delta for us: he still had an LLM *translate* each node (hence the
survival-rate problem and the fuzzing week). We can go one step further
because Java→Scala is a far smaller semantic distance than C++→Rust: the
translator itself is deterministic, and LLMs are only used to *write and
extend the rule set*, validated against the golden corpus.

### 2.3 Keeping ports fresh against upstream

- Textual patch queues over generated/fast-moving text are the documented
  pain case (Debian quilt fuzz-rejections; Brave hand-resolving failed
  patches every Chromium bump and eventually migrating to structured
  overrides).
- The winning precedent is **regenerate-always + a replayable override
  layer**: Google's OwlBot/synthtool regenerates client libraries per
  upstream commit and re-applies handwritten deltas as a *program*
  (templated replacements, protected handwritten dirs), never as diffs
  against generated text. j2cl regenerates every build. "Do not edit
  generated code by hand" is the contract that makes upstream bumps cheap.
- Deterministic output + a pinned canonical formatter is the enabler
  (gofmt/gofix lineage): regeneration diffs become reviewable and the
  override layer stays stable to author against.
- **OpenRewrite** is the architecture to study for the tree layer (Lossless
  Semantic Trees: every node carries its leading trivia, full type
  attribution, byte-identical round-trip; recipes are composable, versioned,
  deterministic; Moderne serializes LSTs as cacheable artifacts). Note: it
  has never been used for cross-language translation — that part is
  genuinely our own territory — but it validates every design choice in the
  Java-side half.

---

## 3. Toolchain choices (researched, with versions)

### 3.1 Java frontend: **Spoon** (first choice), JDT Core (fallback)

| Candidate | Resolution | Comments | Verdict |
|---|---|---|---|
| **Spoon 11.5.0** (2026-07, INRIA/KTH, MIT-dual-licensed, Java 25) | ECJ underneath = compiler-grade; **shadow classes** expose classpath/JAR types through the same `CtModel` metamodel | `CtComment` attached to elements (enable `setCommentEnabled(true)`) | **Recommended.** Transformation-designed model + real resolution + comments already attached. Caveat: model is semantics-first (`isImplicit()` nodes); exact trivia recoverable via `SourcePosition` slicing. Use full-classpath mode, never no-classpath. |
| Eclipse JDT Core 3.46.0 | Compiler-grade bindings standalone (`ASTParser` + `setEnvironment`) | Javadoc only in-tree; statement-level attachment DIY via extended ranges | Solid fallback; Spoon is roughly "JDT resolution + comment attachment + nicer model, already written." |
| JavaParser 3.28.2 + SymbolSolver | Home-grown JLS reimplementation, documented `UnsolvedSymbolException` fragility | Best-in-class comment model | Resolver is the weak link — disqualifying for a type-driven transpiler. |
| javac Tree API | Perfect (is the compiler) | **Discards `//` and `/* */` at the lexer** | Use as a *validation oracle* for resolved types, not the frontend. |
| tree-sitter-java | None | Lossless CST | Disqualified alone (no types; grammar frozen at ~Java 21). |

### 3.2 Scala side: **Scalameta trees** + pinned scalafmt

- Scalameta **4.17.2** (2026-07): dialects through `Scala39`; and critically,
  the historical "synthesized trees can't carry comments" objection **died in
  v4.14.3 (2025-12) / v4.15.0 (2026-02)** — trees now carry printable
  leading/trailing comments (`createWithComments`, `tree.begComment`).
- Printing is deterministic *per pinned version* but changes across minors —
  pin exactly, golden-test, treat upgrades as rule-set version bumps.
- Normalize with **scalafmt 3.11.x via scalafmt-dynamic** (version pinned in
  `.scalafmt.conf` → byte-reproducible).
- Why Scalameta over the ecosystem-majority "custom IR + line printer"
  (ScalaPB/smithy4s pattern): we explicitly want a **user-programmable
  Scala-AST transformation layer** (your per-project adjustment passes), and a
  standard public tree type is the right extension surface. Bonus: Scalameta's
  parser golden-tests our own output (`parse(print(t)) == t`), and the
  Scala-side post-processing layer can literally be **scalafix custom rules**
  (Scalameta + SemanticDB, deterministic, composable) rather than a bespoke
  framework.
- Keep the printer behind an interface anyway (the j2objc/j2cl insulation
  lesson); if Scalameta printing gaps bite, smithy4s's typed-`LineSegment`
  printer design is the proven alternative.

### 3.3 Engine host language

Scala 3 on the JVM. Spoon and JDT are Java libraries, Scalameta/scalafix are
Scala libraries; both sides load in one process. The engine itself never needs
to cross-compile.

---

## 4. Proposed architecture

```
                       ┌──────────────────────────────────────────────┐
 original-src/         │                BALTIC PORTER                 │
 (git submodules,      │                                              │
  pinned SHAs)         │  A. Frontend: Spoon full-classpath model     │
   │                   │       + comment attachment                   │
   ▼                   │       + javac cross-check (sampled)          │
 per-module            │  B. Graph: class/member dependency graph     │
 source sets ───────►  │       Tarjan SCC → condensation DAG →        │
   │                   │       deterministic topo order (Kahn +       │
 project plan          │       lexicographic FQCN tie-break)          │
 (manifest +           │  C. Bridge IR ("BIR"): normalized Java-      │
  rules-as-code)       │       semantics tree, trivia first-class,    │
   │                   │       resolved types/overloads baked in      │
   ▼                   │  D. Pass pipeline over BIR → Scala trees:    │
 vocabulary maps       │       Tier 1 core semantic passes            │
 (type/method/         │       Tier 2 platform & stdlib mapping       │
  library mapping)     │       Tier 3 project idiom passes            │
                       │  E. Emit: Scalameta trees (+ generated       │
                       │       provenance header) → pinned printer    │
                       │       → pinned scalafmt                      │
                       │  F. Post: scalafix-style project rules       │
                       │  G. Gates: API-parity check, scalac ×3,      │
                       │       ported tests, differential harness     │
                       └──────────────────────────────────────────────┘
                                │
                                ▼
              generated Scala sources (never hand-edited)
              + override layer (replayable, separate)
              + persistent CAS/action cache
```

### 4.1 Translation units and the graph

- Extract a **typed dependency graph** from the Spoon model: nodes =
  top-level types (optionally members for merge/split decisions), edges =
  extends/implements, field/param/return type-use, call, static-access,
  annotation-use. This is the Jankiewicz TSTG, except we get it exactly from
  a resolved compiler model instead of heuristics.
- **Tarjan SCC** (emits components in reverse topological order in one DFS
  pass) → condensation DAG → **Kahn's algorithm with a min-priority queue on
  FQCN** for a *unique, deterministic* topological order. Unlike
  C++/Rust/Haskell, Java and Scala both tolerate mutual reference within a
  compilation batch, so an SCC is simply translated as one batch with a
  shared symbol environment — no `.hs-boot`-style forward stubs needed.
- Module-level ordering (your multi-module requirement) is the same
  algorithm one level up, driven by the project plan's module graph; sge's
  14 submodules and ssg's per-library modules are the test case.
- File mapping is manifest-driven and supports **N-to-1 merges** (sge merges
  `Vector.java` + `Vector2/3/4.java` → `Vectors.scala`) and **1-to-N
  platform splits** (shared trait in `scala/` + impls in
  `scalajvm|scalajs|scalanative/`, the layout both repos already use). The
  manifest format should be an evolution of the existing
  `.rescale/data/migration.tsv` so current data migrates in.

### 4.2 The three rule tiers (mined from sge/ssg)

**Tier 1 — core semantic passes (universal, ship with the engine).** These
encode the Java→Scala 3 gap catalog; the dangerous ones — exactly where the
ssg audits found agents silently broke semantics — are marked ⚠:

- Declarations: `interface`→`trait`, statics→companion object ⚠ (companion
  init is lazier than Java class-init; statics are *not* inherited via
  subclass name — always qualify with declaring class; interface statics not
  directly expressible — flag), nested types, records→final case classes,
  `enum` → Scala 3 `enum extends java.lang.Enum[E]`.
- Constructors ⚠: Java auxiliaries may each call `super(...)`; Scala
  auxiliaries must chain to the primary. Funnel through a synthesized
  primary; this is Scalagen's documented #73 failure and ssg's
  super-constructor-inlining trap.
- Control flow: `return`/`break`/`continue`/labels →
  `scala.util.boundary`/`break` (stable since 3.3.0) ⚠ — `boundary.Break`
  extends `RuntimeException`, so translated `catch (RuntimeException)` /
  broad catches inside a boundary must filter it. `switch`→`match` with
  fallthrough-closure duplication, explicit `case _ =>` where Java fell
  through silently, explicit null-check for enum switches. `i++` in value
  position, assignment-as-expression, `for(;;)`→`while` (never HOFs — Scala 3
  dropped non-local returns).
- Expressions ⚠: **resolve overloads/boxing in the Java frontend and emit
  unambiguous calls** (Scala 3 dropped weak conformance; `remove(int)` vs
  `remove(Object)`; emit explicit `.toLong`, `Integer.valueOf`). Reference
  `==`/`!=` → `eq`/`ne`; boxed comparisons Java resolved as `equals` → emit
  `.equals`. String `+` with non-String LHS → `String.valueOf`. Ternary
  numeric promotion/unboxing made explicit.
- Types: wildcards `? extends/super T` → `[? <: T]`/`[? >: T]` (existentials
  are gone in Scala 3; emit `?` not `_`); raw types → `[?]` + inserted casts;
  array covariance via `asInstanceOf` (JVM still enforces store checks; do
  *not* introduce `ClassTag`); F-bounded generics preserved verbatim.
- Exceptions: drop checked, emit `@throws[E]`; multi-catch → union pattern;
  try-with-resources → explicit `try/finally` + `addSuppressed` mirroring
  javac's desugaring (not `Using.resource` — suppression policy differs).
- Misc: varargs `T...` → `T*` + `@varargs`; `volatile`/`transient`/
  `serialVersionUID` → annotations; `synchronized` statics →
  `classOf[X].synchronized`; keyword collisions → backticks; uninitialized
  fields → `scala.compiletime.uninitialized`; anon classes stay anon classes
  (never "modernized" to lambdas when identity/`this`/default-method calls
  are involved) ⚠; Javadoc + all comments carried on IR nodes.

**Tier 2 — platform & vocabulary mapping (table-driven, per target).**
A declarative mapping vocabulary: fully-qualified Java symbol → target Scala
symbol + call-shape adaptation. Instances already exist as prose in
`type-mappings.md` of both repos:

- Stdlib policy per project: ssg maps `ArrayList`→`ArrayBuffer` etc.; sge
  maps LibGDX collections to the external `lls` library (`DynamicArray`,
  `ObjectMap`, `Nullable[A]`). Same mechanism, different tables.
- Nullability policy per project: `@Nullable`/null-flow → `Nullable[A]`
  opaque type (both repos ban raw `null`), applied where the frontend's
  null-analysis says a reference is nullable. (Meta's Kotlinator spent most
  of its preprocessing budget here — this pass earns real investment,
  including optionally consuming an external nullness-inference run.)
- Getter/setter collapse (needs whole-model call-site knowledge — the
  frontend has it), `Comparator`→`Ordering`, `Disposable`→`AutoCloseable`,
  etc.
- **Cross-platform lint table** (emit warnings, not silent output): regex
  features RE2 lacks on Native (lookaround, possessive quantifiers);
  `java.text`/`java.time`/`Locale` absent from both JS and Native core
  javalibs (→ scala-java-time 2.7.0 / scala-java-locales 1.5.4);
  executors/threads absent on JS (Native's `java.util.concurrent` is real
  since 0.5.x); string identity/`toString`-of-doubles divergence on JS;
  fullLinkJS defaults to Unchecked exception semantics; `@safePublish`
  needed on Native for final-field publication.

**Tier 3 — project idiom passes (rules-as-code, the per-project layer you
asked for).** Registered programmatically against the engine's API, operating
on BIR or on emitted Scalameta trees (scalafix-style):

- sge's flagship: global `Gdx.*` → `(using Sge)` context parameter threaded
  through constructors and inheritance chains (161 files today — a
  whole-graph rewrite, exactly what a deterministic pass does better than an
  agent).
- Renames (`Gdx`→`Sge`, exception family mapping), abstract-class→SAM-trait
  decisions, file merges, package re-layout, header/license templating.

### 4.3 Provenance, licenses, comments

Generated automatically per file (both repos do this by hand today):
`Ported from:` original path(s), `Original authors/license:` (parsed from
upstream headers + AUTHORS), `upstream-commit:` the submodule SHA, engine +
rule-set version, and the machine-readable audit block. Comments ride the IR
(never dropped — already a hard rule in both repos, enforceable structurally
by the engine: every source comment must appear in output or in an explicit
drop-list). New/modified comments are Tier 3 rules or override-layer entries.

### 4.4 External dependencies of the original code

The frontend resolves *everything* on the classpath, so the engine can emit a
precise report per module: which external types are referenced, by how many
units, with which members. Each external library then gets one of three
dispositions in the project plan:

1. **Port it too** — add it as another translation-unit set in the same run
   (the repo-local preference you described); the graph handles ordering.
2. **Map it** — a Tier 2 vocabulary table mapping its API onto an existing
   Scala/cross-platform library (the ssg pattern: Jackson→`LiquidSupport`
   trait, strftime4j→`DateTimeFormatter`+scala-java-time; each mapping may
   carry a documented behavioral-divergence note, as ssg's ANTLR replacement
   does).
3. **Shim/escape-hatch it** — per-declaration handwritten companion files
   (the j2objc/j2cl `.native` pattern) for the residue.

Unresolved externals with no disposition fail the run loudly — that is the
"analyze whether we need yet another library" feature, and it is the
anti-omission gate at the same time.

### 4.5 Keeping the port fresh

- Bump the submodule pin → `git diff old..new --name-status` scopes candidate
  files; per-unit content hashes (already conceptually in
  `migration.tsv.source_sync_commit`) decide what actually changed.
- **GumTree** (v4, active, first-class Java support) gives declaration-level
  change classification when you want to report *what* changed upstream, not
  just *that* it changed. (Cross-language Java-tree-vs-Scala-tree matching
  does not exist off the shelf; if ever needed, match over BIR.)
- Retranslate changed units; the **interface-hash early-cutoff** (below)
  stops the ripple when only bodies changed.
- Human deltas never live as edits to generated files: they are Tier 3
  rules, vocabulary entries, or override files — all replayed on every
  regeneration (the OwlBot model). Existing hand-ported files can be
  grandfathered per-file in the manifest (`handwritten` status) and migrated
  opportunistically.

### 4.6 Persistent cache

Standard, proven design (Bazel/Gradle/ccache/mypy convergence — do not
innovate here):

- **Action cache + CAS split.** CAS: content-addressed blobs in a fan-out
  directory (`cache/cas/ab/cdef...`), written to temp + atomic rename.
  Action index: SQLite (WAL) mapping action-key → output digests + metadata.
- **Action key** = digest of: (a) source file(s) content, (b) the *interface
  digests* of every dependency unit (Java-side resolved signatures it uses),
  (c) the rule-pipeline fingerprint — engine version + ordered rule IDs +
  each rule's declared version + full project config, (d) pinned
  Scalameta/scalafmt versions + dialect, (e) an explicit salt. Never mtimes;
  never unlisted env.
- **Early cutoff via interface hashing** (mypy's trick, Salsa's backdating):
  each translated unit stores two digests — full output, and exported-API
  surface. Downstream units re-key only on the interface digest, so a
  body-only upstream change retranslates one file.
- Granularity: coarse per-unit actions. Fine-grained query-level
  incrementality (Salsa/rustc red-green) is the only tier with a documented
  soundness-failure history (Rust 1.52.1 globally disabled incremental over
  it) — not worth it for a batch tool. The expensive non-cacheable step is
  building the Spoon model per module; mitigate by scoping model builds to
  modules whose file-set digest changed.
- Determinism prerequisites: no timestamps in output, stable iteration order
  everywhere (sorted maps in every pass), canonical serialization. These
  also make the golden-corpus diffs byte-stable.

### 4.7 Verification stack (ordered by oracle strength)

1. **Golden corpus convergence** (unique to us): engine output vs the ~2,600
   accepted ports, diffed modulo the manifest's recorded idiom decisions.
   The convergence percentage is the engine's honest progress metric.
2. **Structural API-parity check**: public-surface comparison original-vs-
   translated computed from both ASTs — this *is* the covenant system
   (`Covenant-baseline-methods`, `re-scale enforce compare`) but computed,
   not stamped by an agent. Nothing can be omitted silently; C1/C2-class
   cheats become impossible rather than detectable.
3. **Compile gate ×3**: scalac JVM/JS/Native per module, per topological
   wave. "It compiles" is weak as a *correctness* oracle but perfect as a
   *link* check (the j2cl pattern).
4. **Ported upstream tests**: test sources go through the same engine
   (AlphaTrans/Syzygy/luaur all translate tests as first-class targets); ssg
   already has the spec-file pattern (data-driven fixtures run by generic
   runners — those port for free) and a mechanical test generator precedent
   (`scripts/gen-compress-tests.js`).
5. **Differential harness**: original Java running on the JVM is a cheap,
   perfect oracle (much cheaper than C++→Rust ever had it). Property-based
   differential tests (ScalaCheck, cross-published JVM/JS/Native) on
   value-level APIs; this is established Scala practice — Scala.js/Native
   validate their javalibs by running shared test suites with the JVM as
   ground truth.

### 4.8 Where LLMs remain (with deterministic gates)

- Writing and extending rules/vocabulary tables — validated by the golden
  corpus, so a wrong rule is caught by diff, not by another agent's opinion.
- Proposing dispositions for external libraries and designs for SCC clusters
  and platform splits (the genuinely judgment-shaped work; luaur's
  "stub shape is a contract" pattern).
- Idiom polish *after* the deterministic bulk, tests green throughout
  (the C2SaferRust division of labor) — as Tier 3 rules where possible, as
  override-layer entries otherwise.
- Never: translating a unit ad hoc, stamping audit status, or editing
  generated output in place.

---

## 5. What to build, concretely

| # | Component | Notes |
|---|---|---|
| 1 | Project plan model | module graph, source sets, manifest (evolves `migration.tsv`), vocabulary tables, rule registry, override layer conventions |
| 2 | Frontend adapter | Spoon full-classpath model + comment attachment; javac sampled cross-check; kept behind an interface (frontend-swap lesson) |
| 3 | Dependency-graph extractor | typed edges from the resolved model; Tarjan SCC + condensation; deterministic topo (Kahn + FQCN tie-break) |
| 4 | Bridge IR + trivia model | Java-semantics tree, resolved types/overload picks baked in, comments first-class |
| 5 | Pass framework + Tier 1 passes | small, named, single-concern, individually versioned (nj2k's ~54-conversion granularity is the right size) |
| 6 | Vocabulary engine (Tier 2) | declarative symbol/call-shape mapping + cross-platform lint table |
| 7 | Project rule API (Tier 3) | BIR passes + scalafix-style Scalameta passes; rules-as-code, versioned |
| 8 | Emitter | Scalameta trees + provenance headers → pinned printer → pinned scalafmt; printer behind an interface |
| 9 | Verification stack | corpus-diff runner, structural API-parity, 3× compile orchestration, test translation, differential harness |
| 10 | Cache | CAS + SQLite action index, interface-hash early cutoff |
| 11 | Freshness tooling | submodule bump → scoped retranslation → reviewable regen diff; GumTree-based upstream change report |

Bootstrap order that de-risks fastest:

1. Components 2+3+4 skeleton, then Tier 1 passes driven **entirely by
   corpus convergence on Liqp** (135 files, 9.5k LOC, already fully ported
   and audited in ssg — small, real, and the golden diff tells you every
   missing rule by name). This is also where "agents build the tool" applies:
   each corpus diff is a self-contained rule-writing task with a
   deterministic acceptance test.
2. Scale the same loop to flexmark (1,100 files — exercises merges, skips,
   visitor patterns, F-bounded generics) and an sge extension with the
   `(using Sge)` idiom (exercises Tier 3 and platform splits).
3. Only then the first *new* port — a library neither repo has, translated
   cold, validated by ported tests + differential harness. That run, not the
   corpus, is the real acceptance test of the engine.

Effort anchors from the evidence (not hedging, just the data): Scalagen was
~17 transformers largely by one person on 2010s tooling; nj2k's core is ~54
conversions; the Tier 1 catalog above is ~60–80 rules of which perhaps 15 are
genuinely hard (constructors, overloads, boundary/catch interaction, statics
init order, null-flow); luaur did 205k LOC of a *much* wider language gap in
~3.5 weeks by making the graph the unit of work and the oracles deterministic.
The two repos' corpus removes the largest unknown any transpiler project
faces — knowing when it's right.

---

## 6. Semantic traps quick-reference

The short list every reviewer of engine output should know (details in §4.2):

1. Companion-object init is lazy vs Java `<clinit>` eager-on-first-active-use
   — registries/`loadLibrary` in static blocks can silently not run.
2. Auxiliary constructors can't call `super` — constructor graphs need
   restructuring.
3. `boundary.Break` is a `RuntimeException` — broad catches must filter it.
4. Overload resolution differs (weak conformance dropped) — pre-resolve in
   the frontend, emit unambiguous calls.
5. Reference `==` → `eq`; boxed equality differs (`Integer.equals(Long)`).
6. `match` has no fallthrough and throws `MatchError` where `switch` fell
   through silently; null enum switch behavior differs.
7. Statics aren't inherited through subclass names; interface statics aren't
   expressible.
8. Array covariance and raw types need inserted casts, not redesign.
9. try-with-resources suppression order: mirror javac's desugaring exactly.
10. Cross-platform: RE2 regex on Native, string identity + number formatting
    on JS, fullLinkJS unchecked semantics, no executors on JS, `@safePublish`
    on Native, `java.text`/`java.time`/`Locale` via scala-java-time/-locales.

---

## 7. Primary sources

Repos: `../sge` (`docs/contributing/*.md`, `.rescale/data/*.tsv`, covenant
system), `../ssg` (`docs/contributing/conversion-rules-java.md`,
`docs/plans/remediation-2026-06.md` C1–C16, `docs/architecture/*-port.md`).

External (verified 2026-07-17): c2rust docs/source-walkthrough; j2objc FAQ +
jre_emul README; j2cl design docs + semantics.md; IntelliJ nj2k sources;
Meta Kotlinator (engineering.fb.com 2024-12); pjankiewicz/luaur
TRANSLATION.md + blog; bun.com/blog/bun-in-rust + PR #30412 and its critiques;
AlphaTrans arXiv:2410.24117; Syzygy arXiv:2412.14234; VERT arXiv:2404.18852;
FLOURINE arXiv:2405.11514; C2SaferRust arXiv:2501.14257; PtrTrans
arXiv:2510.10956; OpenRewrite LST docs + Moderne LST-artifact docs; OwlBot
README; Spoon 11.5.0 / JDT 3.46.0 / JavaParser 3.28.2 docs and issue
trackers; Scalameta 4.17.2 release notes (4.14.3/4.15.0 comment support);
scalafmt 3.11.x dynamic API; Scalagen repo + issues; Scala.js 1.22.0
semantics + JAVALIB.md; Scala Native 0.5.12 docs; Bazel REAPI proto; Gradle
build-cache docs; ccache manual; mypy incremental internals; Salsa book;
rustc incremental-compilation guide; GumTree v4; Tarjan/Kahn/Pearce–Kelly
references.
