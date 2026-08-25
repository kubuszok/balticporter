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

**Sources for the numbers above** (verified 2026-07-17, in the original research pass): the
Luau→Rust figures are pjankiewicz/luaur (TRANSLATION.md and its blog write-up); Kotlinator is
engineering.fb.com, 2024-12; the incremental-soundness incident in §3.12 is the rustc
incremental-compilation guide's own history (rustc 1.52.1 disabling incremental globally);
prior-art shape from c2rust docs, the j2objc FAQ + jre_emul README, j2cl design docs, IntelliJ
nj2k sources; the LLM-translation survival data family: AlphaTrans arXiv:2410.24117, Syzygy
arXiv:2412.14234, VERT arXiv:2404.18852, FLOURINE arXiv:2405.11514, C2SaferRust arXiv:2501.14257.
Internal: sge `docs/contributing/*.md`, ssg `docs/contributing/conversion-rules-java.md` and
`docs/plans/remediation-2026-06.md`.

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
emission through Scalameta + a pinned scalafmt. What shipped is `engine`'s `TirEmitter`, a direct
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
| `GlobalsToImplicitsTransform` | globals → CONTEXT: thread an anonymous `(using T)` through every declaration that reaches a static holder, and rewrite each read to a summon along a mapped PATH. A whole-program rewrite — the `ResearchPlugin` case — over five edge kinds, not a call graph (§8.4) |
| `PrimitiveToOpaqueTransform` | primitive → opaque type + companion: retype a semantically-tagged value everywhere it flows, wrap its construction sites. Seed detection is flow propagation — a union-find over the whole-program reference graph from a small HINT set. Configured entirely by an `OpaqueSpec` (§2.1.1) |
| `CollectionsTransform` | Java collections → Scala, leaner where possible: retype + API-map every usage site of a collection symbol. Takes a `RuleScope` (§2.1.1) |
| `PanamaFfiTransform` | Panama FFI generation: `native` methods → `java.lang.foreign` downcall bindings for JVM and Scala Native linkers |

Each is verified emitting scalac-compiling Scala by its own spec. That the tool must **own** this
layer (rather than delegate to scalafix) and that the tree must carry **more** than a Scala-2.13
semantic AST does are the two requirements everything below follows from.

### 2.1.1 Every RETYPING rule takes a SCOPE, and a scoped rewrite PROPAGATES

Two of the four transforms above move a set of declarations onto a different type and carry every
reference with them. That shared shape has two halves, and both are now values rather than
assumptions.

**`RuleScope` (`api`) — WHERE a rule applies.** `Everywhere(except)` or `Only(include)`, matched by
fully-qualified name and cut only at a `Symbol.fullName` separator (§4.56 — `com.foo` must not cover
`com.foobar`); `PortManifest.covers` forwards to it, so the rule this project has been bitten by
twice has one body. `Everywhere(Set.empty)` is the default and every phase branches on it to take
its pre-scope code path, which is the strongest available form of §1(b)'s "an empty parameter needs
no code path" — the measure lanes assert it as **0 members changed** over 600 files.

Three decisions inside it worth stating, because each looks like a detail and is not:

- **A symbol is placed through its OWNER CHAIN, never by its own name alone.** The frontend gives a
  method-local a `fullName` that is its simple name and a PARAMETER one that is `?#p` (it qualifies
  the parameter before the method's own record is set). A name-only test would scope every field and
  method correctly and silently leave every parameter and local behind — half a retyped signature.
- **The scope is a set of ENTRIES, not a predicate.** A `Symbol => Boolean` would be strictly more
  expressive and impossible to report on; declared entries are what lets an entry that named nothing
  become a §1(b) `PolicyFinding` instead of a silent no-op.
- **A phase that takes one implements `SurfacePolicy`.** Two modules scoping the same phase
  differently emit signatures that each compile alone and cannot compile together, which is exactly
  what §1.5's shared-surface comparison exists for. `CollectionsTransform` did not implement it
  before, because before the scope there was nothing to compare.

**`FlowPropagation` (`engine`) — the second half.** A rewritten declaration carries its call sites,
by union-find over pure-move flows read off the Spoon-resolved TIR (assignment, `val x = ref`,
`return ref`, argument-to-parameter). Arithmetic is deliberately not an edge: it breaks the chain,
which is what makes an opaque type an opaque type and gives the coercion somewhere to go. It was
`IntToOpaqueTransform`'s private code; `CollectionsTransform` grows a `RuleScope.Only` with the same
function. Its walk is hand-written and deliberately bounded, and the argument for that is the
failure direction: an unknown node kind is a MISSED edge — a declaration left out of the rewrite,
i.e. a compile error or a boundary finding at the site — never a spurious one.

**The seam a scope creates cannot be closed, so it is COUNTED.** There is no wrap from a
`mutable.Buffer` into a real `java.util.List` slot and there cannot be one; the runtime shims bridge
the other direction only. So `CollectionBoundaryCheck` gains `Issue.ScopedOut` with the §1(b) fix,
and — the part that is easy to get wrong — it reads a reference to a scoped-out declaration through
the DECLARATION rather than through the node. `transformType` is position-blind, so the node's `tpe`
was remapped anyway, and a check reading it compares `Buffer` against `Buffer` and reports ZERO on
the one seam a scope is guaranteed to create. `Decision.Kind.ScopedOut` carries the same fact to
`decisions.tsv` and to a porter note beside the code, because a declaration that KEPT its type shows
nothing in a diff.

### 2.1.2 `OpaqueSpec` — the opaque-type rule's policy, as one value

`IntToOpaqueTransform(typeName, hint, extraHints)` was `Int`-only, with an implicit definition site
and no fence. Each of those three was a limit nobody had decided on purpose, so the phase is now
`PrimitiveToOpaqueTransform(spec: OpaqueSpec)` and the spec (in `api`, because a porting program in
another repository constructs it) declares all of them:

- **the FQN, which IS the definition site.** The object is `spec.fqn` and the type is `<fqn>.T`; the
  package comes from the FQN's prefix and the file from the package. A second "where does it live"
  knob could only ever hold a value that agrees with this one or a value that is wrong — Scala does
  not let an `object com.foo.X` live in package `c.d` — so there is no second knob. An FQN with no
  `.` is the default package, which is what the bare `typeName` did.
- **the underlying primitive**, as a CLOSED enum of the eight Scala value types a Java primitive maps
  to, so "this primitive cannot work" is unrepresentable rather than a runtime check somebody has to
  remember. `fromScalaName` is the loud door for a caller holding a string. All eight work: the
  mechanism is `opaque type T = P` plus `apply`/`unwrap` and is indifferent to `P`.
- **the seeds** — `hints` (the port's own predicate, §1(c) in its purest form) and `extraHints` (the
  agent-in-the-loop escape hatch after a failed compile) — **and a `RuleScope` fencing both.** The
  fence matters because a pure-move chain crosses type boundaries freely: one hint on a field
  propagates through a call into another class's field, which the spec measures. A fence a named
  entry could step over is not a fence, so an `extraHints` entry outside the scope does not fire.

**Two instances in one pipeline must compose, and an overlap FAILS THE RUN.** One symbol cannot be
two opaque types, and the silent failure is order-dependent: whichever instance runs second finds
those symbols already retyped away from the primitive, declines them as ineligible, and emits a port
with half a domain type missing — green compile, no count moved, and the only evidence a row that is
not there. So the propagation is allowed to walk INTO a sibling's opaque type precisely so the
overlap is visible, and the run throws, naming the symbol and both specs. A throw rather than a
finding, for the reason `Pipeline.order` throws on a phase cycle: there is no honest program to emit.

**A retyping phase owes FOUR things beyond the declaration it was pointed at**, and this one shipped
owing all four. Each is now built, each was measured by the family that exposed it
(`ENGINE-LIMITS.md` §13, `PROGRESS.md` §11.25), and each generalises past this phase:

- **the coercion reads the boundary through the DECLARATION.** A node's own `tpe` is exact for a
  bare reference and blind to every term that CARRIES one, because nothing retypes a composite node
  from its branches — so `carriesOpaque` asks the seed table and descends `if` / a block's tail / a
  `match` arm / a `Commented` wrapper, and `coerce` rewrites the LEAVES. That is §1.5's rule for
  `CollectionsTransform` restated one phase along, and the leaf placement is the reference port's
  own answer rather than a preference. Which node kinds carry is enumerated; an unenumerated one is
  a MISSED coercion, which is a compile error at the site, never a silent unwrap.
- **the enclosing `MethodType`'s parameter slots move with the parameter symbol, by POSITION.** The
  TIR stores a parameter's type TWICE and consumers read different halves — the emitter reads the
  `ValDef`, the constructor funnel reads the signature (deliberately: an argument's type may be
  narrower than the formal), a published contract row reads the signature. One declaration with two
  types is a disagreement no count can see. Descriptors are NOT part of this: `Symbol.descriptor` is
  frontend-derived from the Java signature and no phase rewrites it, so a retyped parameter moves
  the signature and leaves member identity alone — which is the invariant, verified rather than
  assumed.
- **a hint the MECHANISM cannot reach is REPORTED, and says (a) engine.** An `OpaqueSpec` seeds a
  symbol whose own type is the primitive, so a family landing on a container's ELEMENT is
  unreachable — and it used to be unreachable SILENTLY, which reads exactly like a typo. It is now a
  `policy` finding whose detail names `ENGINE-LIMITS.md` §13 O3 and says no respelling helps.
- **the SYNTHESISED unit belongs to ONE module, and it is the one that owns the declarations it was
  minted FOR.** The fourth is not about translation at all, which is why it survived a delivery whose
  base read 0 errors with all 21 check counts flat. This phase adds a top-level unit, `PortRun`
  classifies a unit by its `Origin`, and a unit with no usable origin is CONVERTED — right for a
  parsed unit, blind for a minted one, and a dependent's `Program` contains its base's units, so
  every module in the chain wrote its own copy of one FQN (`ENGINE-LIMITS.md` §13 O5: nine files
  where one was owed, 24 errors over six lanes, six suites stopped). The mint is now fenced on
  `RunScope.emits`, read off the spec's own HINTS rather than off the grown seed set — a pure-move
  flow reaches a dependent's own declarations the moment it assigns a tagged getter to a local, so a
  grown-set fence hands the mint back to a module that merely uses the family. A module that does not
  mint still retypes and coerces: the minted symbols are `external` to it exactly as a JDK symbol is,
  and the emitted fully-qualified reference resolves against the owning module's output.

  This is CLAUDE.md §1.5's rule for `inject` generalised — exactly one module ships each definition
  of an FQN — and the general form is the one to reach for next: **a phase that SYNTHESISES a
  declaration owes the same one-module answer.** `PortRun` carries the belt beside the phase's
  suspenders: a synthesised unit at an FQN a base's published port map already claims fails the run,
  whichever phase minted it, because the fence has to hold for the next phase too and that phase's
  author will not have read O5.

**And the spec is SHARED SURFACE, so the phase implements `SurfacePolicy`** — CLAUDE.md §1's standing
obligation for anything that retypes declarations under a `RuleScope`, unmet here until now. The
fingerprint renders the FQN, the primitive, the sorted `extraHints` and the scope. `hints` is a
predicate and cannot be rendered; that residue is `ENGINE-LIMITS.md` §13 O4. **`MergeablePolicy` is
deliberately NOT implemented**, and the argument is §1.5's instance-count question answered rather
than assumed: no corpus dependent constructs this phase, so there is one instance inherited through
`extendedBy` and nothing to fold. Two same-NAME instances would mean the same opaque type configured
twice — the phase's name is `primitive->opaque:<fqn>` — which is two answers to "which values are
this type", and OR-ing two predicates would silently widen the shared surface. `SurfaceDivergence`
is the right answer for a composition nobody has designed; a merge is what to build when a dependent
first needs one, not before.

### 2.1.3 Java primitives → Scala primitives is (a) UNIVERSAL, with nothing to scope

Investigated when `RuleScope` landed, and recorded here so it is not re-derived.

`int` → `Int` happens in the **frontend** (`SpoonTir.primName`; `ScalaPrinter.primMap` on the frozen
BIR path), unconditionally, at the point a type reference is interned. It is §1(a) in the strictest
sense — a Java `int` IS a Scala `Int`, for every library there will ever be — and there is no
variant of it a port could want. The thing a port might actually want, "this `int` is really a
domain value", is not a variant of the mapping at all: it is §2.1.2's phase, and that one has a
scope. Adding a knob here would be a knob nothing needs.

The plausible second candidate — BOXED types, `java.lang.Integer` → `Int` — **is not a rewrite the
engine performs, and must not become one.** A boxed value is NULLABLE and a Scala value type is not,
and the port's own translations depend on that: `CollectionsTransform` renders java's `Map.get` as
`getOrElse(k, null)` with the default ascribed to `V`, which requires `V` to be a reference type.
48 emitted libGDX files name a boxed type; every one of them is a slot where Java's own autoboxing
put the wrapper (`SpoonTir.wrapperOf`), and unboxing them wholesale would turn `null` into `0` with a
green compile — §4.4's defect class exactly. There is therefore no scoped mechanism to wire: there
is no rewrite.

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

A rule is a `balticporter.tir.Phase` implementation **passed to the run** — that is the whole
contract, and `balticporter/corpus/.../libgdx/GdxSharedIteratorRule.scala` is the worked example of a §1(c) rule
living outside the engine. There is no plugin descriptor and nothing is loaded by class name.

This section used to add "no registry, service loader": that is now half wrong and the correction
matters. §5.7 adds a `ServiceLoader` **so that a CONFIG FILE can NAME a phase**, and its contract is
deliberately narrow — a stable string resolving to a class the consumer already compiled and put on
its own classpath. Nothing is instantiated from a name written in a config file, no phase is
discovered implicitly (a factory found on the classpath does nothing until a conf's `surface` names
it), and the Scala path is unchanged: a `PortRun(phases = …)` still takes instances and needs no
registration at all.

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

### 2.8 The difference catalog — the (a) layer, made explicit

Every Java-vs-Scala semantic difference the engine knows about is a VALUE in
`balticporter.catalog` (`api`), not a table in a document. The decision, and the four arguments for
it, in descending weight:

1. a lowering arm, a transform and a check must be able to CITE a difference, and a coverage lane
   must be able to report that one is never reached. A markdown table can do neither;
2. `CLAUDE.md` §3.6 admits six documents and forbids a seventh, and none of the six fits — 126 rows
   would swamp this file (decisions, not inventories), `ENGINE-LIMITS.md` is measured dead ends and
   most rows are neither measured nor dead ends, `PROGRESS.md` is per-port state;
3. `CLAUDE.md` §4.45 — the consumer is an agent in ANOTHER repository. A catalog in the engine jar
   is one that agent has;
4. the precedent is §6.2's own, on `UnportableKind`: *a closed engine enum — a new kind is an engine
   change that arrives with its mint sites and its report text, which is the correct friction.*

**Ids.** `JS-<AREA><NN>` — `E` expressions, `S` statements, `C` classes, `G` generics, `L` library
surface, `P` platform capability. **Never reused, never renumbered**, which is why `Differences.retired`
exists: an id absorbed into another row keeps its number out of circulation instead of freeing it,
and a retirement with no record is an id the next agent assigns to a different fact. Always written
with the `JS-` prefix, because `ENGINE-LIMITS.md` has its own `G22` and it is not the same thing.

**The no-parameter rule, and it is the guard rail the whole taxonomy rests on.** A `Difference`
takes no parameter — no `RuleScope`, no `Set[String]`, no predicate, no `SurfacePolicy`. A difference
is a fact about Java and Scala; if a row needs to know something about a LIBRARY it is not a
difference, it is a (b) phase's policy, and it belongs in that phase's constructor where §1.5's merge
contract already governs it. `DifferenceTakesNoParameterSpec` asserts it by REFLECTION over the
constructed rows — every field, recursively, a literal or an enum case — because a list of allowed
field names is a list the next field is not on.

The `JS-{L,P}` rows are the apparent exception and are not one. An `ApiRow` legitimately carries
per-platform MAPS, because Scala.js and Scala Native genuinely disagree and one shared verdict is
exactly what makes a rule wrong for one of them. So the guard is NARROWER rather than absent —
`ApiRowCarriesNoPolicySpec` — and the line inside the row is: `by` (availability) is the (a) FACT,
true of every port and contradicted by no manifest; `verdict` is a recommended DEFAULT; and a TARGET
SET is neither, so no row may hold one. Which platforms a port cares about is the port's, in its
manifest.

**And that line is what `PortabilityCheck` is built along.** The check is the `JS-{L,P}` half's first
consumer and CLAUDE.md §1(b) in its purest form: one mechanism (match a rule against
`ExternalUsage`), one parameter (`PortManifest.targets`), and a `Rule` that carries the set of
platforms it is a refusal FOR. Seven of its rules were measurably too broad for Scala Native 0.5.x
and an eighth (`System#getProperty`) was not merely broad but factually wrong for Scala.js, which
implements it against a link-time table; all eight now claim Scala.js alone.

Two things about the direction of the coupling, because both are easy to get backwards:

- **the registry does not GENERATE the rule list.** A generated list would silently drop every rule
  the javalib survey has no row for — `org.junit.`, `org.hamcrest.`, `java.lang.ClassLoader`, the
  exact `Class#…` readers — which is a lane reset rather than a re-scoping, and fifteen baselines
  "improving" to zero is a promotion nobody can read. Each rule CITES its row instead, and
  `PortabilityTargetsSpec` holds the pair together in the direction that matters: **a rule may not
  claim a platform on which its own cited row says `Keep`**, so a row corrected in the registry
  cannot leave a rule firing against it;
- **a `Depend` verdict is not an unportability**, it is a BUILD-GRAPH fact — the API exists, in an
  artifact the port's build does not name — and conflating the two makes the finding unanswerable:
  the reader is told to remove a call that a one-line `libraryDependencies` entry makes correct. So
  `Verdict.dependency` splits the rule list in two, and the `Depend`-shaped half feeds
  `dependency-coverage` rather than `portability(*)`.

**`dependency-coverage`, and the shape of its three conjuncts.** A finding is *a usage FIRED* ∧ *no
declared dependency covers it* ∧ *the port declared no alternative*, and only the middle one is a
FILTER. The first is the walk itself (the same `ExternalUsage` enumeration `PortabilityCheck`
performs, over the complementary rules). The third is read THROUGH `PortManifest.verdictOverrides`,
inside `ApiRow.verdictOn`, so a port that declared it ships its own shim has changed the verdict and
the row never becomes a requirement at all. Written as a second filter that conjunct could disagree
with the first — one of them consulting the override and the other not — which is the shape of a
check reporting a row it has already excused. Each conjunct is still separately OBSERVABLE, and the
finding's detail says so: how many dependencies the port declares, and which row and platform went
unanswered. Coverage is matched on organisation and NAME and never on the revision: the catalog's
`rev` is the version the survey checked, not a floor, and a lane that policed versions is one nobody
asked for.

**…and the fourth question, which is the same pair read backwards.** Coverage SUBTRACTS, so a
`dependencies` entry naming an artifact no requirement wants leaves both numbers on this lane exactly
where they were — 0 before and 0 after — while the port resolves and ships a jar on every backend for
a call it does not make. `DependencyCheck.unneeded` is that half, and it is reported on the `policy`
lane and not here, because it is a declared key that fired on nothing rather than a fact about this
port's portability. `PolicyIssue.NeverApplied` rather than either neighbour: the entry is well formed
and names a real artifact, and what did not happen is the REQUIREMENT — `NeverMatched` would send its
author looking for a typo in a coordinate that is correct. It is asked of the EMITTED requirements
for `ENGINE-LIMITS.md` D2's reason in the other direction: the unfiltered walk would credit a
dependent's declaration for a call only its base makes.

**Where a declared artifact LANDS in the generated build is the run's own `SourceSet`.** A manifest's
`dependencies` are the artifacts that module's declarations need, and a `sourceSet = Test` run's
declarations are its suite's — written into the main `libraryDependencies` they would publish, on the
shipped library, a coordinate only its tests call. `PortRun` therefore folds them into `deps` or
`testDeps` by the set it is emitting, which is the split `SbtGen.managedResources` already makes for
a descriptor: a resource, a source and a dependency each belong to ONE configuration, and the run is
the only place that knows which. This is also the half that makes the check honest — a lane that
stops reporting a requirement because a coordinate was declared is a liar unless that coordinate
reaches a build file.

**The CROSS KIND of a `Depend` coordinate is checked against the repository, never inferred.** An
artifact a `Depend` names is one a NON-JVM backend has to resolve, so `%%` is almost never the right
spelling: `scala-java-time`, `scala-java-time-tzdb`, `scala-java-locales` and `scala-native-crypto`
are all published per PLATFORM (`_sjs1_3`, `_native0.5_3`), and the first three ALSO publish the JVM
jar `%%` would resolve — which then fails at link time rather than at resolution. One surveyed
coordinate cannot be made good on at all (`com.dedipresta:scala-crypto` is Scala 2 only), and that is
recorded in the rows that name it rather than repaired with an invented replacement.

**…and WHERE it resolves from is the same kind of fact, one layer out.** The cross kind decides which
artifact NAME a build asks for; `ArtifactDep.resolver` decides which repository it asks. Both are
properties of the artifact rather than of the port, and both fail identically when they are wrong: a
coordinate published outside the default resolver set resolves NOTHING, in somebody's build, with no
finding here and nothing in the emitted Scala to see. So the URL travels WITH the coordinate — in the
catalog row that recommends it and in the `dependencies` entry that declares it (`resolver = "…"`) —
and `SbtGen` renders a deduplicated `resolvers` block from the dependencies it is already writing.
`None` is the default and emits nothing, so every port that predates the field generates the build it
always did. The entry's NAME is DERIVED from the URL and never supplied: a label is a value a caller
could type, therefore one that can disagree with the URL beside it, and nothing could ever check it —
derived, it is a rendering of a fact rather than a second fact, and the generated build stays
byte-deterministic. The first coordinate to need it is `com.kubuszok:multiarch-serviceloader`, which
§8.19 maps `java.util.ServiceLoader` to and which is published to the Central Portal SNAPSHOT
repository and to no release repository at all.

Two things it deliberately does not do. It does not write a `Decision`: an override changes no
emitted code, and `decisions.tsv` answers "why is the emitted code not a mechanical translation" —
the override's record is the finding lane itself, which a baseline diffs. And `ArtifactDep` carries
no PLATFORM SET: which platforms need the artifact is the row's own `verdict` map (`MessageDigest`
wants `scala-crypto` on Scala.js and `scala-native-crypto` on Native — two `Depend` verdicts, not
one dep with a set), and a set in a row is the one shape `ApiRowCarriesNoPolicySpec` forbids.

**Two status-enforcement rules, both mechanical, because a status transcribed by hand is already
wrong.** Four headline `Open` rows were fixed inside one week while the document describing them was
being written, and nothing could see that they had.

- **(i) a row whose twin names a CLOSED `ENGINE-LIMITS.md` entry may not claim `Open`, and a row
  whose twin reads OPEN may not claim `Handled`.** That file is
  maintained in the commit that measures, so the twin column is more current than the status column
  by construction; `ClosedTwinStatusSpec` parses the stable ids and their marker, and
  `scripts/catalog-status.sh` is the same rule for an agent holding a checkout. Its honest limit: it
  sees a row only through its twin, which is why a row claiming `Open` may not have `Twin.NoTwin`.
  **Both directions, because they are different failures.** The first is a status that went stale
  downwards — a row still saying `Open` after somebody closed the entry — and it is the one four
  headline rows fell into. The second is the one that costs something: a row claiming `Handled`
  against a record that says the engine does not handle it is the registry asserting coverage its
  own measurement contradicts, which reads as a guarantee to §4.45's agent. `Partial(why)` is
  exempt, and that is the whole of the exemption — a partial row STATES which half is missing, which
  is exactly what an open twin is evidence of. A twin that is CLOSED on one face and OPEN on another
  is `AMBIGUOUS`: reported by both readers, failed on by neither, because a half-closed entry is a
  human's call by construction (`ENGINE-LIMITS.md` K15 is the one, and its heading had to be made
  honest before the state had any instance at all).
- **(ii) a consult that cites an `Open` or `Absent` row is a finding.** A lowering arm consulting a
  difference the registry says nobody handles is a registry that has stopped describing the code.
  The practical effect is what makes the pair land together: a wiring commit flips the status IN THE
  SAME CHANGE, or it does not go green.

**A row does NOT imply a decision.** `Decision` records why the emitted code is not a MECHANICAL
translation; a JLS-mandated deterministic lowering *is* the mechanical translation, and a note there
would be narration that `NoteCoverageCheck` polices in both directions. A row earns a decision only
where the port could have gone another way, or where something is missing from the output.

**`attaches`, and the exact strength of what it buys.** A row declares WHERE the engine owes it a
decision, and the engine records whether that decision was taken (`balticporter.catalog.Attaches`).
Three discharge surfaces were designed, all three are built, and a **fourth** was found by the
residue the third could not retire (see *THE FOURTH SURFACE IS A TYPE* below). The three:

- **frontend lowering** — `Lowering.of(kind, dispatch, at)` at `SpoonTir`'s statement and expression
  dispatches, `Obligations.consult(id, at)(predicate)` inside. **The wrapper goes at the DISPATCH,
  never in an arm**: written inside each `case`, an arm could decline to wrap, and an arm that opts
  out is the same shape as the defect the mechanism exists to catch. Entered at the dispatch, an arm
  never had the choice;
- **a whole-program pass** — `CatalogLog.cite(id, decl)`, one row per DECLARATION it decided about,
  which is the granularity `Decision` already uses (`CLAUDE.md` §5.1). Deliberately weaker and
  reported apart: nothing can assert that a pass *should have* considered a difference at a
  declaration it never visited. Reached from `Phase.cite` for a pipeline phase, and directly by the
  emitter's own construction-time passes — `resolveFieldShadowing` (JS-C04) and
  `resolveMemberClashes` (JS-C46) are not phases and are exactly this shape: they walk the program,
  not a node kind, so a dispatch wrapper is the wrong instrument for them;
- **emitter rendering** — `Rendering.of(kind, at, subject)` at `TirEmitter`'s `stat` and `term`
  dispatches, keyed on the `Tree` kind (`productPrefix`) rather than on the Java one. Built with
  area S, which is the first area most of whose rows are decided HERE: a `switch` with no `default`,
  a `break` in the middle of a case, a `boundary` the emitter interposes, a `try`'s resources — the
  frontend has already discharged its obligations correctly by the time any of them arise. It shares
  `Lowering`'s machinery exactly (one `scoped`, one delegation seam, one fast path), because the
  emitter has the SAME seam the frontend does: `stat` hands every `Term` to `term`, so one node is
  rendered inside two scopes and the consults happen in the inner one.

  **It carries no `Dispatch`, and that absence is the design.** The frontend's key needs one because
  java gives a node kind two meanings by POSITION; by the time a `Tree` exists that question is
  answered — the position is in the tree — so a second axis here would be a distinction with no fact
  behind it.

  **Exactly one emitter per run holds the run's log.** The determinism twin, the preview emitter and
  the best-effort emitter all re-render every unit, and a shared log would count every consult twice
  — the same reason those instances do not share a source map (`CLAUDE.md` §5.1). They take
  `CatalogLog.discarding`, which is not a flag but the honest answer for a second rendering whose
  bytes are thrown away.

**A row may attach at more than one place — `Attaches.Both`.** Two facts need it and both arrived
with area S: MORE THAN ONE KIND at one surface (a loop is four `Tree` kinds, all reaching
`loopWithJumps`, so `JS-S01` attached to one of them would leave three able to render without
considering it) and TWO SURFACES (`JS-S18`'s `do`-`while` is decided in the frontend, which maps
`CtDo` to a node the language has no keyword for, and again in the emitter, which chooses the image).
It is a product of enum cases and not a `List` because `DifferenceTakesNoParameterSpec` rejects a
collection in any row field — a collection is the exact shape a per-library policy takes — and a
product is admitted by the recursion that spec already performs. `Differences.leaves` is the one
place a `Both` is flattened, so the three indexes built from it can never disagree; a `Both` counts
as mechanised only when EVERY leaf is.

**AREA C NEEDED NO FOURTH SURFACE, and the prediction that it would is worth recording.** The
implementation plan said *most `JS-C` rows discharge in PHASES*; chunk 0's re-derivation had already
disproved it, putting a `SpoonTir` or `TirEmitter` symbol against almost every row. What made the
three surfaces sufficient is a fact about the IR rather than about area C: a `Tree.ClassDef`, a
`Tree.DefDef` and a `Tree.ValDef` are `Statement`s, so the emitter's rendering dispatch has reached
every declaration since it was built. Two consequences, both general:

- **a dispatch is only a dispatch if EVERY node goes through it.** `TirEmitter.emitUnit` called
  `classDef` directly, so a TOP-LEVEL type never entered the rendering scope while every nested one
  did — every `Rendered("ClassDef")` row would have been owed, and discharged, only by the nested
  ones, on every port, forever. That is `ENGINE-LIMITS.md` F8's shape at an ENTRY rather than in an
  arm, and it is the second thing to check when an area attaches to a kind nothing attached to
  before;
- **the consult goes above the FORK, not in the arm that decides.** `classDef` forks into `enumDef`
  and `classDef1`; consulted below the fork, an enum's rendering would owe `JS-C34` and never ask
  it. The predicates are therefore read off the tree and the symbol table rather than by re-running
  `orderBody` or `diamondOverrides`, and that trade is stated where it bounds what a `fired` count
  means: `consult` asks *does this difference APPLY at this declaration*, which is a question about
  the shape, and whether the repair emitted text is what the edge-case suite asserts.

**And a construct the frontend REFUSES gets no obligation at all.** `JS-S09` (switch expressions) and
`JS-S10` (pattern switch) are `Absent`, and attaching them to their Spoon kinds would be a claim that
reads as coverage and can never fail: the dispatch enters, the refusal throws, the lowering never
returns, so the row would sit on `mechanised` reading `unreached` on every port forever. They carry
`Unmechanised` naming the instrument that DOES measure them — `SpoonKinds` records each refused kind
against the row's own `DiffId` and the `markers` lane counts every mint. `CatalogCoverageSpec` holds
that line for the whole registry: no row may claim a lowering attachment at a kind no arm lowers.

`Attaches.NoObligation(why)` is the fourth answer and is kept apart from `Unmechanised` on purpose:
one says the surface is missing, the other says no surface is owed (a checked non-difference, or a
difference the translation satisfies by construction with no site-level decision to take), and
collapsing them would hide the first inside the second.

**And HAVING NOTHING TO TRANSLATE IS NOT HAVING NOTHING TO DECIDE, which is the distinction that
moved `JS-G41` off `Unmechanised`.** That row's own text said an attachment would be "a false
obligation" because `@SafeVarargs` is advice javac gives ITSELF — true about the ANNOTATION and
false about the row. Java's heap pollution has no repair to owe (the port reproduces the unsoundness
exactly; a phase that "fixed" it would emit a different program), and the decision an emitted
declaration takes about it is nevertheless a real one: *carry it, and say where*. So the row is a
consult at `Rendered("DefDef")` plus a COUNTER lane (`HeapPollutionCheck`), which is the shape the
proposal's chunk 18 named — and the counter's population is derived, never keyed on the annotation.
A census of java's ACKNOWLEDGEMENTS reports six declarations in one corpus library and calls that
the risk, while the declarations javac warned about and nobody annotated — carrying the same
unsoundness with no marker of any kind — are the ones a reader cannot otherwise find. That is
`CLAUDE.md` §4.56's rule about an instrument whose silence is a wrong answer, met at the population
rather than at a filter. Read it as the general shape: **`Unmechanised` is a claim about the
DISCHARGE SURFACE, and a row that needs a counter rather than a rewrite still has one.**

**And the second half of that lesson is that an `Unmechanised` sentence can be false about a surface
THAT ALREADY EXISTS.** `JS-E06` read *"the frontend decides it while lowering an expression's casts,
which is not an obligation dispatch, and the EMITTER half has none either"* — and the second clause
was simply wrong: `Tree.Typed` enters the rendering dispatch, and `JS-G34` had been consulting there
since chunk 10. Nothing could report it. An `Unmechanised` text is prose whose only reader is an
agent, so it goes stale exactly like an evidence string, and the moment it is re-derived is the
moment somebody tries to wire the row. **Re-read the sentence against the CODE before believing
it** — which is the same instruction the fourth surface's wave arrived at from the other side, where
three rows' sentences named a surface they did not want.

**And an OBLIGATION is per NODE, so the consult goes in the ARM even where the RULE's convergence
point is not one.** F8's rule — a rule stated once per arm is a rule the next arm will not have —
says to put the rule where the arms converge, and area G is where that runs into the mechanism's own
shape. Java's assignment conversion (JLS 5.2) is ONE conversion written out by ONE function
(`SpoonTir.coerce`), reached from six node kinds: a local's initialiser, an assignment, a `return`, a
call argument, a `new`'s argument, an array initialiser's element. The tempting move is to consult
inside `coerce`. It is wrong, and silently: `coerce` is not reached at all for a local with no
initialiser, a bare `return` or a zero-argument call, so the consult would report a hole at exactly
the nodes where the difference does not apply — a phantom on the work list, which is the one thing a
work list may not have. The answer is both halves at once: the rule is stated once
(`SpoonTir.slotConsults`, one function) and CALLED from each of the six arms, where the node always
is. F8 is satisfied by the single statement; the obligation is satisfied by the six call sites.

**…and a node a PARENT consumes POSITIONALLY never enters the dispatch, so attaching a row to its
kind is coverage that cannot fail.** The third face of "a dispatch is only a dispatch if every node
goes through it", and the one with no symptom at all: `TirEmitter.argTerms` FLATTENS a
`Tree.Repeated` in an argument position before `term` is called on it — a fact about the POSITION,
since a node rendering `""` would leave `f(a, )` — so `JS-G39` attached at `Rendered("Repeated")`
would have been neither consulted nor reported as a hole, because a node that does not enter the
dispatch owes nothing. Every other shape of this defect leaves SOMETHING behind (a lower consult
count, a hole on the work list); this one leaves a row sitting on `mechanised` with a surface that
can never be reached. It is `SpoonKinds.Claim.Positional` met at the other end of the pipeline, and
the answer is the same one the frontend's registry already gives: the decision belongs to the
CONSUMING node, so the row attaches at the enclosing `Apply` where the flattening is decided.

**And what hides it is that a `Both` row's consult count is ONE NUMBER.** `catalog(unreached)` and
`just catalog-coverage` both ask "did this ROW get reached", so a live leaf answers for a dead one —
`JS-G39` read *consulted 30,560* on the strength of its lowering half while its rendering half could
not run. Neither instrument is wrong; they answer a question about the row, and this is a question
about a leaf. Until something asks it per leaf, the guard is the review step named above: when a row
attaches to a `Tree` kind nothing attached to before, check that the kind reaches its dispatch at
all — the entry (`emitUnit`, chunk 11) and the parent (`argTerms`, this one) are the two ways it
does not.

**THE FOURTH SURFACE IS A TYPE, and it is one surface with two ends.** The first three are all about
a NODE — a java statement or expression, a `Tree`, a declaration a whole-program pass decided about.
A whole family of differences is about none of them: the wildcard grammar, the raw-type fill, the
F-bound no instantiation can eliminate, the diamond's unnameable inference variable, a nested type
that is path-dependent in one language and not the other. Every one is decided while a TYPE is
lowered or rendered, and a type is not a node at either end — a `CtTypeReference` is not a
`CtStatement` or a `CtExpression`, and a `TypeRepr` is not a `Tree` at all: it is the algebra a
`TypeTree` carries, and the `TypeTree` is rendered through its parent. Ten rows said exactly that in
their `Unmechanised` text. `Typing.ofReference` at `SpoonTir.tpe` and `Typing.ofRepr` at
`TirEmitter.tpe` are the wrappers, both at the DISPATCH, both delegating to the one `Lowering.scoped`
the other surfaces share.

- **TWO `Attaches` cases and not one**, because the KEYS are two vocabularies. `LoweredType(kind)`
  takes Spoon's reference-INTERFACE name from a new registry for `spoon.reflect.reference` — a THIRD
  jar scan, kept apart from `SpoonKinds.registry` so that a Spoon upgrade produces two readable diffs
  rather than one number answering for two taxonomies, and needed for the same reason the node
  registry is: `SpoonTir.tpe`'s match is ORDERED and its final arm is the supertype's, so a reference
  kind Spoon adds tomorrow is absorbed there and renders as an ordinary class reference. That is
  `CtTextBlock`'s shape where the wrong answer is a TYPE. `RenderedType(kind)` takes the `TypeRepr`
  case's own `productPrefix`, derived from the class files exactly as the `Tree` kinds are. Neither
  carries a `Dispatch`: java gives a NODE two meanings by position, and a type reference has only
  ever had one;
- **the emitter half has NO ORIGIN of its own, and that is a fact rather than an omission.** A
  `TypeRepr` is a value the IR shares between every position naming the same type, so there is no one
  place it was written. What exists is the origin of the node it is being rendered FOR, which
  `CatalogLog.currentOrigin` now holds — maintained by `scoped` beside `currentSubject`, restored by
  the same caller, and inherited rather than overwritten by a scope whose own origin is synthetic.
  Reporting it is exact: a finding's job is to name a file and line somebody can open, and the line
  where the type was NAMED is the one they want, where `Origin.synthetic` would put `-`/0 on every
  type-surface finding in the catalog;
- **three of the ten rows turned out NOT to need it**, and finding that out is what wiring them was
  for. `JS-G05` (a wildcard in an `extends` clause takes the declared bound) and `JS-G11` (an
  F-bounded slot cannot be eliminated at all) are decided ABOVE `TirEmitter.tpe`:
  `deWildcardedArgs` REPLACES the wildcard before any type is rendered, so the `TypeBounds` arm never
  sees the slot the rows are about. That is `JS-G39`'s rule read at the other end — the decision
  belongs to the CONSUMING node — and the consuming node is the declaration whose `extends` clause it
  is, so both attach at `Rendered("ClassDef")`. `JS-G06` (a de-wildcarded raw parent and its
  overrides must agree) is `rawParentAlignment`, a whole-program pass at emitter construction, which
  is the CITATION surface exactly as `resolveFieldShadowing` is for `JS-C04`. **The prediction that
  the residue was one fact was right about nine rows out of eleven and wrong about which surface
  three of them wanted** — the same shape as area C's prediction one wave earlier, and the same
  lesson: the surface a row needs is read off the CODE THAT DECIDES IT, never off the row's own
  sentence;
- **and re-reading that code corrected four EVIDENCE strings, two of which named branches that are
  switched off.** `inheritedTp` opens `if true || noInheritFill || !inOverridingMember then None`, so
  the name-directed parent-instantiation fill has answered `None` unconditionally since the sge-design
  revert — and it is the only reader of `inOverridingMember`. `JS-G06` cited that fill and `JS-G08`
  cited the `uncheckedGeneric` save-and-restore that feeds it, so both rows explained the port with
  machinery the port does not run. What is live is `rawParentAlignment` for the first and the
  frame the raw fill reads (`inStatic`, `nestedInScope`) for the second. Nothing could have reported
  this: an evidence string is prose, and the only thing that reads it is an agent — which is why
  wiring a row is also the moment its evidence is re-derived.

`Differences.mechanised` is written as the COMPLEMENT of the two honest negatives for exactly this
reason (`CLAUDE.md` §4.56): a list of surfaces is a list the fourth was not on, and
`just catalog-coverage`'s own filter had already been caught that way.

**The DISPATCH is part of the key, and that is not an implementation detail leaking upward.** Java
gives one node kind two meanings by position: `i += f` as a statement discards the compound
assignment's value (JLS 14.8) and the same node as an expression yields it (JLS 15.26.2). The
catalog has two rows for exactly that reason, and a kind-only attachment could not tell `JS-E03`
from `JS-E04` — which is the pair the whole mechanism was designed around.

**What the wrapper GUARANTEES, at the strength it holds.** It detects an ABSENT consult. It cannot
detect a WRONG one: an arm that consults a row and hands it a predicate which never returns `Some`
discharges the obligation and emits the same wrong code. So the claim is *a difference cannot be
silently UNCONSIDERED at a site the catalog attaches it to*, and the other half — that the
consideration is CORRECT — is carried by the per-area edge-case suites, which is why those suites
are a definition-of-done and not an afterthought. An over-claimed guarantee is how a mechanism stops
being audited.

**Enforcement is staged.** In the testkit and `just debug-emit` an undischarged obligation is FATAL,
because every difference gets an edge-case suite and that suite is what the guarantee rests on. In a
PORT RUN it is a counted finding: a run that died because a rule is incomplete produces no
diagnostics at all, which is the wrong trade (`ENGINE-LIMITS.md` M6 is about refusing to
*approximate*, not about refusing to *report*). A row the registry itself calls `Open` or `Absent` is
never fatal in either mode — it is the work list, and a mode that died on the work list would make
the work list unrunnable.

**Coverage: four lanes, one registry lane, and one artifact.** `catalog.tsv` holds one row per catalog entry, reached or
not — every row, because the question it exists to answer is "which branches does this port never
touch" and a file listing only what fired answers the other one. It is written **through the
artifact-layer gate**, without exception (`CLAUDE.md` §5.1): it comes from the frontend's own log, so
it is reachable from more test paths than `PortMap` is, and one unconditional `PortMap.write` was
enough to publish run directories into the checkout from a JVM with no port identity at all. The
lanes are `catalog(consulted|unreached|unmechanised|undischarged)`, all four in
`PortRun.RequiredChecks`, following the `trivia` family's precedent: `unreached = 0` is a bar a run
could hold by declaring every row unmeasured. `catalog(refused)` is deliberately NOT another lane —
it is the `markers` lane, which already records a `Tree.Unportable` mint with its catalog id, and two
lanes counting one thing is how two numbers start disagreeing.

**A fifth lane that is not about coverage: `catalog(uncited)`.** It counts registry rows whose
Scala-side citation is the literal `UNCITED — ` prefix, and it is derived from the REGISTRY rather
than the run, exactly as `unmechanised` is. It is a lane and not a `println` because `counts.tsv` and
the committed baseline are the only things in this project that DIFF a number: the gap lived in one
spec beside `assert(uncited <= all)`, a comparison no possible registry can fail, so 121 rows sat
there with nothing able to report the 122nd. It is required of every run and asserted on NOWHERE — a
spec failing on this gap is a spec somebody silences by inventing a citation, which is worse than the
gap it closes. `just catalog-coverage` aggregates the
artifact across the corpus, which is the only place the useful question can be asked: a row unreached
on one small library is normal, a row unreached on all of them is dead code or an untested rule.

**`tests` is still not a field**, and for the reason `attaches` was not one until its surface
existed: the edge-case suites are per-area waves, and a row pointing at a suite nobody wrote is a
claim the engine cannot honour.

**Rendering.** `just catalog` writes `.balticporter/catalog.md` — gitignored, a build product,
§5.5's rule for emitted code applied one medium over. Committed, that markdown would be a seventh
document nobody loads, and it would start disagreeing with the code it came from.

**No row carries a number** — a measurement lives where §3.6 says and the row points at it with its
twin. That includes the catalog's own size: `Differences.all.size` is derived and written down
nowhere, which is `PortabilityCheck`'s phantom "34 rules" lesson applied to the thing that recorded
it.

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
  runtime/         // balticporter-runtime: the shims a PORT depends on (JavaIterator, …),
                   // cross-built JVM + Scala.js + Native (`runtimeJVM`/`runtimeJS`/`runtimeNative`)
  api/             // the MODEL and the CONTRACTS a rule author compiles against
  frontend-spoon/  // the ONLY module that sees Spoon types
  engine/          // the MACHINERY: transforms, checks, emitter, vocab, sbt-gen, verify, PortRun
  testkit/         // golden-test harness for rule authors (used by ports too)
  corpus/          // the framework's own acceptance ports against ../ssg, ../sge
ported/            // one directory per ported library — the OTHER half of the checkout
  sge/ sge-ecs/ sge-gltf/ sge-anim8/ sge-vfx/ sge-screens/ sge-jbump/ sge-noise/
  sge-graphs/ ssg-liquid/
port-report/       // one directory per MIGRATOR CLASS — the measurement identity, keyed
                   // on `LibgdxCoreMigrate` and not on `sge`, so a module rename does not
                   // orphan a baseline
```

**`ported/` names its directories for the REFERENCE PORT's module ids, never for the upstream
libraries** (`CLAUDE.md` §2.1) — `sge` is libGDX core, `ssg-liquid` is liqp — and each port's
`label` and `PortManifest.name` carry the same string, because `label` is what the run publishes as
`module=` in `port-map.tsv` and `PortManifest.name` is what a dependent's base chain matches
against it. Note the two directory trees answer different questions and must not be aligned by
symmetry: `ported/<module>` is WHERE THE CODE GOES and `port-report/<Migrator>` is WHOSE
MEASUREMENT IT IS.

Dependency directions: `api` depends on nothing; `frontend-spoon` on `api`; `engine` on both;
`testkit` and `corpus` on `engine`. Nothing depends on `corpus`. **`frontend-spoon` is the only
module that sees Spoon types** — the insulation rule that let j2objc and j2cl swap frontends whole,
and it survives `engine → frontend-spoon` because the arrow only ever points that way.

`CLAUDE.md` §1's enforcement grep covers `api`, `engine`, `frontend-spoon` and `runtime`: no file
in them may name a ported library in code, test sources included.

**Why the line is drawn between `api` and `engine`, and not somewhere else.** The consumer is an
agent in another repository (§4.45) writing a §1(c) rule for its own library, and the cost of that
must be ONE dependency that drags in no emitter, no orchestrator and no Spoon. So the criterion is
operational rather than aesthetic: *a §1(c) rule and its spec must compile against `api` alone*.
What that admits is the TIR (`Tree`, `Symbol`, `SymId`, `TypeRepr`, `Origin`, `Trivia`, `Program`,
`Xref` — a `Program` cannot be built without the indexer), `Phase`/`Plugin`/`StandardTraversal`/
`Pipeline`, the decision model (`Decision`, `Reason`, `DecisionLog`), the recording surface
(`CheckReport`, `PolicyReport`), the §4.6 debug surface (`DebugFlags`, `TirTrace`, `TirPrinter` —
whose `sha256` is what a finding's stable id is hashed from), the frontend contract (`Frontend`,
`FrontendConfig`, `Unsupported`, `CommentScanner`), `PortManifest`/`Substitutions`, and the two
values a SCOPED retyping rule cannot be written without — `RuleScope` and `FlowPropagation`
(`balticporter.transform`, the one package `api` shares with `engine`). Everything else — every
transform, every check IMPLEMENTATION, the emitter, `PortMap`, `Cache`, `PortRun` — is machinery and
is in `engine`.

Five judgement calls in that cut, recorded because each looks wrong until the reason is stated:

  - **`Pipeline` is in `api`, not `engine`.** It reads as the runner and is not: running a phase
    list over a `Program` is exactly what a rule's SPEC does, and `frontend-spoon`'s own
    `SpoonTirSpec` already did it. The runner that is not in `api` is `PortRun`.
  - **The frozen BIR (`Bir.scala`) is in `api`.** `SpoonFrontend` still populates it, and
    `frontend-spoon` must depend on `api` ALONE — the alternative is `frontend-spoon → engine`
    beside `engine → frontend-spoon`, which is a cycle. The BIR's PASSES (`Pass`, `Transform`) and
    its printer stay in `engine`; only the model crosses. When the BIR path is retired, this row
    goes with it.
  - **`PortManifestConfig` was split out of `PortManifest.scala` into `engine`.** It reads and
    writes a `PortMap`, which is an artifact concern; the manifest itself is policy a consumer
    declares. This is the only file the consolidation split.
  - **`engine` depends on `frontend-spoon`**, because `PortRun` models a source set with
    `SpoonTir`. That is the direction the insulation rule wants; a second frontend is added beside
    Spoon and `engine` gains a dependency, never the reverse.
  - **`FlowPropagation` is in `api`, in package `balticporter.transform`.** It reads as a transform
    helper and is not a transform: CLAUDE.md §1 requires every retyping rule to take a `RuleScope`
    and carry its call sites with it, so a §1(c) rule in a consumer's repository needs exactly this
    to grow its seeds — and the criterion above then decides it, since needing it from `engine`
    would drag in the emitter, `PortRun` and Spoon. It imports `balticporter.tir` alone. The package
    is deliberately unchanged: it names what the value is FOR, and a rename would break every
    consumer's import to say nothing new.

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
report.

**The set's SIZE is not restated here, and that is deliberate.** This paragraph said "twelve are
required unconditionally" beside a twelve-row table for long enough that both went stale — the same
failure `PortabilityCheck`'s own docstring had with its phantom "34 rules", one document over.
`PortRun.RequiredChecks` is the list; `CLAUDE.md` §5 names every member in the sentence an agent
actually reads before a measurement, and that sentence is maintained in the commit that changes the
set. A count in a third place is a count that disagrees with both.

Some checks record on every run that reaches them but are deliberately outside the set, because the set
is asserted against what actually recorded and a port without the relevant phase records nothing:
`porter-notes` (§7.2), and `collection-closure`, `collection-boundary`, `collection-retarget`
(recorded only when the collections transform is in the pipeline). They are made unskippable by their wiring living in the
orchestrator, not by the set. A port's own §1(c) rule may register a check of its own beside these.

`PortManifest` is the **shared-surface policy as an ordinary Scala value** — `name`, `governs`,
`dropTypes`, `dropMethods`, `packageRenames`, `surface`, `inject`, `bases`, and the two
platform fields below — composed with `base.extendedBy(dependent)`.

**`targets` and `verdictOverrides` — the platform half, and both are NOT inherited.** `targets` is
the set of backends this module is ported for, and it is the §1(b) parameter `PortabilityCheck`
reads (§2.8). It moves no emitted signature — only which findings are reported — so it sits in
§1.5's right-hand column beside `runtimeMode`, and a base and a dependent may legitimately hold
different sets. The default is ALL THREE platforms, which is exactly what the check asked before it
had a parameter: no port's `portability(*)` baseline moves because the engine gained a target set,
and a port that wants the narrower question declares it and takes the drop as its own decision.
There is one constraint, in ONE direction only — `ManifestAgreement.Kind.TargetWidening` — and it is
fatal: a dependent may target FEWER platforms than its base and may not target MORE, because a
dependent built for Scala.js while its base is JVM-only depends on emitted Scala nobody checked
against that platform, and `ENGINE-LIMITS.md` D2's ownership filter is precisely what stops the
dependent seeing the base's findings about it. `verdictOverrides` is the same field's other half: an
`ApiRow`'s `verdict` is a recommended DEFAULT and this is where a port says it ships its own shim,
vendors a subset or accepts the refusal. What an override may NOT touch is `by`, the availability
FACT — a manifest that could contradict a platform's coverage is a manifest in which a port silently
declares a gap closed. **`dependencies` is the third**, and it is a build fact on `inject`'s line:
exactly one module's build file names each coordinate. `SbtGen` writes them into the generated
`libraryDependencies` — at the run's own `SourceSet`, so a suite's artifacts do not reach the shipped
library — and `dependency-coverage` reports every requirement none of them covers, while a key that
covers nothing is a `policy` finding. Empty is still the default and the no-op; it is no longer the
whole corpus, three ports having declared the artifacts their `Depend` sites need. A manifest DSL would move the policy out of reach of the consumer's
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
- **A TEST-CLASS HIERARCHY is answered at TWO places, and neither is the class the `@Test` is in.**
  Java lets a suite inherit — an abstract base declaring the `@Test`, concrete subclasses supplying
  the data — and both halves of the structural transform above break on it. The suite PARENT is a
  class and so is java's superclass, and scala has one: it therefore anchors at the ROOT of the chain
  of program-declared classes the declarer belongs to, which is the declarer itself for every suite
  that extends nothing (i.e. every suite in the corpus before flexmark). And a `test("m"){…}`
  STATEMENT overrides nothing, so a `@Test` that takes part in java's own override relation stays a
  `def` and the registration is emitted ONCE, at the top declarer, as a CALL to it — virtual dispatch
  then runs one test per concrete class, which is what JUnit does. Emitted as two registrations it
  would be an MUnit duplicate-name failure at RUN time, after the compile and after every count.
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

`balticporter.vocab` ships the Java→Scala stdlib tables and the **platform coverage + lint data** (RE2 regex limits
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

**…and the dependency is cross-built, because the argument for publishing it is a LINKER argument.**
The paragraph above rests on the Scala.js and Native linkers rejecting two bodies at one FQN — which
says nothing at all unless the artifact those linkers resolve actually exists. `runtime` is therefore
a `projectMatrix` over the three platforms sge and ssg publish for (`CLAUDE.md` §1.5), at sge's own
toolchain versions, publishing `balticporter-runtime_3`, `balticporter-runtime_sjs1_3` and
`balticporter-runtime_native0.5_3` from ONE `src/main/scala`: a port resolving `%%%` finds the same
version of the same types on whichever platform it links for.

Two consequences worth stating, because each is a fact the JVM row cannot produce. **The JS and
Native rows are the only instrument that checks the module's "nothing JVM-only" rule** — nothing else
in this build can fail when a `java.*` only the JVM implements arrives here, and the error would
otherwise land in the consumer's repository (`CLAUDE.md` §4.45). And **what they check is
REACHABILITY, not compilation**: both backends compile against the real JDK and resolve `java.*`
against their own javalib when they LINK, so a member naming an unimplemented JDK API builds
everywhere and refuses only where something calls it. That makes the residue a per-MEMBER fact rather
than a per-file one, and it is why `RuntimeMode.Vendored`'s flat one-file-per-type drop is the thing
standing between the engine and an off-JVM `UnsupportedOperationException`: the varying half would
have to live in a platform source directory the vendored copy does not carry. The measured residue —
three members, two backends — is in `PROGRESS.md` §13.1.

**And the corollary that decides where a NEW support type may go: only a BASE port can ship one.**
`RuntimePlan.of` derives a run's required types from the PHASES that ran (`RequiresRuntime`), and a
dependent INHERITS its base's phases through `PortManifest.surface` — so a dependent's `required` set
CONTAINS its base's, and `RuntimeMode.Vendored` on a dependent writes a second copy of every shim the
base already wrote. That is why every dependent port in this corpus is `RuntimeMode.Dependency` while
its base vendors, and why a dependent compiled beside its base sees the support types through the
BASE's `src_managed` rather than through anything of its own. The consequence for a proposed runtime
type is the part that is easy to miss: if the ports that want it are dependents, the only module that
can deliver it is the base — which then vendors a type nothing in it references, one module carrying
another's build artefact, the asymmetry `CLAUDE.md` §1.5 draws `inject`'s line at. Measured as the
thing that refused the reflective-instantiation registry (`ENGINE-LIMITS.md` P10); stated here because
until now it lived only as the same paragraph copied into two migrators' comments.

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

**Generating that skeleton is OPTIONAL, and off by default.** The consumer this engine actually has
(§4.45) calls `PortRun` from inside a build it already owns; its `build.sbt`, its `.gitignore` and
its dependency declarations are decisions that repository has already made, and an engine that
overwrites any of them is unusable there. So the whole of the list above is gated on ONE seam —
`PortRun.project: Option[SbtGen.ProjectSpec]`, defaulting to `None`, consumed at the single
`project.foreach(SbtGen.emitPort(…))` at the end of `execute()`. With it `None` a run writes exactly
two things: the SOURCES (emitted, injected, `supportSources`, and the vendored runtime when
`RuntimeMode.Vendored`) under `outDir`, and its report directory when the artifact layer is on
(CLAUDE.md §5.1). Nothing else touches `portRoot`, and `PortRunProjectSpec` asserts the exact file
set in both directions — a gate never observed OPEN cannot be told from a feature that was deleted.

What a caller controls without opting in is `portRoot` + `sourceSet`: an arbitrary directory, which
need not be an sbt project and need not exist, plus the `src_managed/<set>/scala` suffix it opted
into by naming a `SourceSet`. That suffix is the only layout assumption left, and a `Test` run
materialises no `main` tree — the engine asserts no build shape on a repository that did not ask for
one. No separate output-path knob exists, deliberately: a second way to say where the files go is a
second thing that can disagree with `PortRun.outDir`, which every artifact the run writes is
relative to.

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

The map is built (§5.2). §5.7 is what was then built on top of it, and it revises the "cannot be"
above: the surface half is still CODE, but a phase can be NAMED from config, which is a different
claim.

### 5.7 The CONFIG front door — a `.conf` plus an SPI, and why it is not a second truth

**What was measured.** Every migration program in `balticporter/corpus/` turned out to be hard-coded
configuration plus one `PortRun(...)` call. `SimpleGraphsMigrate` was 90 lines of which the only
non-declarative statement was a directory walk the engine can do itself. If that is what a port IS,
then requiring a consumer to write a Scala program, a build that depends on the engine, and a `main`
before it can port anything is a cost with nothing on the other side of it.

So there are now **three front doors to the same `PortRun`**, and the third one is a file:

| door | for |
|---|---|
| a Scala `main` calling `PortRun(...)` | full strength; the recommended door; what a port with §1(c) rules or a non-trivial build uses |
| an embedder calling `PortConfig.load(...)` | a repository that owns its own entry point but keeps its policy in a file |
| `PortConfigMain <port.conf>` | a consumer with nothing to write at all |

**The pieces.** `balticporter.tir.TransformFactory` (in `api`) is `name: String` plus
`fromConfig(ConfigView): Phase`, discovered with `java.util.ServiceLoader`. `balticporter.runner`
holds the HOCON adapter (`HoconView`), the registry (`TransformRegistry`), the engine's own factory
registrations (`BuiltinFactories`, one `META-INF/services` line each) and the loader (`PortConfig`).
The corpus registers `GdxSharedIteratorFactory` beside its rule, which is the worked example of a
§1(c) rule reaching the config door from the porting repository rather than from the engine.

**The §1.5 tension, resolved.** §1.5 warns that "a manifest DSL would move the policy out of reach
of the consumer's compiler". The warning is about a SECOND SOURCE OF TRUTH, and it stands. This is
not one, for three reasons that are the design rather than a defence of it:

  - the conf path **constructs the same values** — `PortManifest`, `FrontendConfig`, `Provenance`,
    `PortRun` — through the same constructors. There is no parallel model and no second code path
    inside `PortRun`, so `ManifestAgreement`, `PortManifest.fingerprint` and the resolution-root/base
    rule cannot tell a conf-built manifest from a hand-built one;
  - **inheritance is `extendedBy`**, the operator §1.5 names. `base = "main.conf"` reads that file's
    manifest and extends it. It is deliberately NOT an include: the base's `input`, `output`,
    `provenance`, `runtimeMode` and `inject` are §1.5's must-DIFFER column and are ignored;
  - **anything config cannot express arrives as CODE.** No classname-in-a-string, no expression
    language, no reflective construction of a lambda. A factory NAME resolving through
    `ServiceLoader` to a class the consumer compiled is the one sanctioned indirection, and it is
    exactly as typed as the Scala path. `PrimitiveToOpaqueTransform` from config therefore takes
    `extraHints` (a set of names) and REFUSES `hints` (a predicate), naming the escape hatch in the
    error: write a factory.

What is genuinely given up is compiler checking of the declarative half — §5.6's own assessment, and
a `dropTypes` key is a `String` either way. The Scala path answers that with `PolicyReport`; the
config path answers it with **a refusal on any key nobody read**. HOCON accepts any document it can
parse, so `dropType` for `dropTypes` is a policy entry that silently does nothing — the §1(b) no-op
this engine refuses everywhere else. `HoconView` records every accessor call and `PortConfig` fails
the run on the leftovers, by tree walk rather than by path-string comparison (a key may itself
contain dots: `packageRenames { "com.foo" = "sge" }`).

**Two things the format forced, both worth knowing before writing a conf.** `include` is a HOCON
KEYWORD — `include = [...]` is a parse error — so file selection is `includeGlobs`/`excludeGlobs`.
And a key read only on SOME code paths is reported as junk on the others: reading `determinism` only
when no CLI flag was passed made `--determinism=full` refuse its own valid configuration, which is
the unread-key check working exactly as intended, on the loader.

**`package-rename` is refused by name, not merely absent.** It is manifest DATA because it must run
after every other phase and `runsAfter` cannot say "after everything" (§4.56). A port told "unknown
transform" would reasonably conclude the feature is missing; it is not missing, it is spelled
`manifest.packageRenames`, and the error says so.

**Where the confs live, and what is converted.** A port's configuration lives at
`balticporter/corpus/ports/<library>/{main,test}.conf`, beside the other port inputs (`balticporter/corpus/*-overrides/`) and
not under `src/`. Paths inside a conf resolve against THE CONF FILE, lexically, so a port directory
is relocatable and needs no system property; `${balticporter.root}` substitution against `-D`
properties remains for values an operator genuinely supplies.

simple-graphs is converted and is the acceptance proof: `just sg-measure` measures the conf-driven
port, and the requirement was every check count unchanged with **0 members changed** — met, on both
source sets.

libGDX and Ashley are deliberately NOT converted, and the reason is measurement rather than
capability. Every phase either port uses is now nameable from a conf — `collections`,
`mutable-params`, `panama-ffi`, `static-forwarder`, `class-table`, `type-redirect`, `method-body`,
`port-map-migration`, `test-framework`, and libGDX's own §1(c) `gdx-shared-iterator` through the
factory `corpus` ships — so what their conversion still needs is exactly three things, none of them
mechanism:

  - **libGDX's `--raw` flag.** `LibgdxCoreMigrate` takes an argument that swaps the manifest for
    `withoutSurface`. A conf has no arguments; the equivalent is the §4.6 flag
    `balticporter.skipPhases=*`, which is a better tool for the job anyway (it needs no second
    manifest and it names what it skipped).
  - **Ashley's `base` pointing at libGDX's conf.** `AshleyPolicy.core` calls
    `LibgdxPolicy.core(repoRoot).extendedBy(...)`; the conf spelling is `base = "../libgdx/main.conf"`,
    which is the same `extendedBy` — but it only works once libGDX itself is a conf, so the two
    convert together or not at all.
  - **A re-measurement of four lanes.** Each conversion has to land with `gdx-measure`,
    `gdx-test-measure` and `ashley-measure` showing every count unchanged and 0 members changed, and
    CLAUDE.md §5 says change one thing then measure. Converting three ports in the commit that built
    the mechanism would have measured nothing about either.

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

### 6.2 The marker, and why it lives in the tree — BUILT (term level)

- **Term level**: a wrapper node — `Tree.Unportable`, a term translated only APPROXIMATELY,
  syntactically complete and semantically wrong, whose `inner` is the approximation and whose state
  `Open` means it MUST NOT ship. A smart constructor (`Tree.Unportable.open`) rejects a synthetic
  origin: a marker must point at real Java, and that is also the precondition `markerKey` — the
  identity the conservation check compares two programs on — depends on, since `<synthetic>:0:0`
  would collapse every marker in a program onto one key and the check would then report nothing,
  confidently. It carries `Option[DiffId]` beside its `UnportableKind`, so a refusal report says
  *which known difference* this is rather than only *which kind of problem*; `None` is an honest
  state and is better than inventing a catalog row to point at.
- **…and the node the marker STANDS at is not always the node the refusal is ABOUT.** The
  constructor's rejection of a synthetic origin is right and is what `markerKey` needs — but a
  parser hands back nodes that carry no position at all, and for those the fallback is the
  unit-fatal throw. So a kind the registry files as `MarkedUnportable` can be `RefusedLoudly` in
  every actual run, with nothing able to report it: the claim is prose, the census reads off the
  claim, and the emitted output of a unit that failed to translate is a file that is simply not
  there. Measured on `CtCasePattern`, which Spoon builds as an unpositioned wrapper around the real
  pattern — every pattern case label took the whole compilation unit with it while the registry said
  it cost one construct. `SpoonTir.unlowered` therefore takes the SUBJECT apart from the SITE: the
  kind, and with it the catalog row, comes from the node the frontend has no arm for; the origin
  comes from the nearest enclosing node that is real java at a real line (the `CtCase`). Neither
  half is guessed and the fallback is untouched, so a refusal with no position anywhere near it is
  still the honest throw.
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
frontend blind spot, unmodelled node kind, annotation residue), and each carries default ranked
remediations classified (a)/(b)/(c) — with (c) ranking last unless the kind is inherently semantic
(`PlatformHostileApi` is the deliberate exception: which APIs a port may not use is a fact about that
port). Every string in a remediation is a TEMPLATE naming only the engine's own mechanisms, so the
mechanism stays library-free and `CLAUDE.md` §1's grep gate continues to hold.

`UnmodelledNodeKind(spoonKind)` is the one member that carries data, and the parser's own interface
name is what it carries — `CtSwitchExpression`, not `CtSwitchExpressionImpl`, resolved structurally
by `SpoonKinds.nameOf`. That is what joins a run's findings to §2.8's kind registry, which is the
list that says what the frontend does with every kind a Java source can produce. Without the name a
run could report *n constructs were refused* and nothing could say which — the report that costs its
reader the whole investigation §4.45 is about.

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

**The test lane is the only one that sees `CLAUDE.md` §4.4.** That table's Java forms translate to valid Scala
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

### 6.4 Best-effort emission — BUILT

Not a second code path. One emitter, one flag (`TirEmitter.bestEffort`, supplied by
`PortRun.bestEffort`), three effects: `Open` markers render as the inner term inside deterministic
comment fences (comments cannot change program shape); each affected file gets a banner naming the
regions; output goes to a separate directory (`PortRun.bestEffortDir`) with a
`BALTICPORTER-BEST-EFFORT` sentinel file, and the migration exits nonzero. In deliverable mode the
gate runs first — any `Open` marker and the deliverable tree is not written.

Two details that are decisions rather than mechanics. **The nonzero exit is at the END of the run,
not at the gate**, because the value of the mode is the diagnostics — the report, the marker
inventory, the per-file banners — and a run that died at the gate would produce none of them
(`ENGINE-LIMITS.md` M6 is about refusing to *approximate*, not about refusing to *report*). And
**the emitter's rendering of an open marker in the shipping default is `compiletime.error`**, not the
fence and not a comment: the orchestrator's gate means a real deliverable run never reaches that
branch, so what reaches it is an emitter with no orchestrator around it — every testkit fixture — and
the default there has to be the loudest available answer rather than the quietest.

At zero open markers, best-effort output minus fences and banner is byte-identical to deliverable
output *by construction* (same emitter, same tree) — no marker means no fence and no banner, so there
is nothing for the mode to add. `UnportableMarkerSpec` asserts exactly that, in both the zero-marker
and the all-discharged case, so the mode cannot rot into a divergent path.

**And this is what replaced the engine's only previous emission-side refusal.**
`TirEmitter.unrenderable` has four call sites, all of them a `Tree.Break`/`Tree.Continue` whose
target label is not in scope, and under `preview = false` — the default, and what every measure lane
runs — it emits its `residue` string, which for all four is a comment and `()`. So before the marker,
"emission refuses loudly" was true of four sites in a diagnostic mode and of nothing at all in a
normal run. The two mechanisms are kept apart on purpose and the difference is which way each
degrades: `unrenderable` stands at an expression the engine *can* still spell and chooses a weaker
spelling, the marker stands where the engine has nothing to say.

The measure lanes have been running in unlabelled best-effort mode all along; after this the
"deliverable" claim becomes a *positive statement the gate makes* (zero open markers, every check
clean) rather than the absence of complaints. That is the mode a new library lives in for weeks.

### 6.5 Status and staging

Everything in §6.3 is built. **The marker (§6.2) and best-effort emission (§6.4) are now built too**,
and they landed together as the adoption order below required: `Tree.Unportable` + `UnportableKind` +
`MarkerState`, the traversal case, `MarkerCheck` (conservation), the emission gate, the fences and
`markers.tsv` in one change. The measured claim that made it emission-neutral is stated as a
condition and not as a hope: **zero markers mint on all fifteen lanes**, so no emitted byte moved.

What is still owed, and by whom:

- **the DEFINITION-level half — a `SymTag` for a finding whose subject is a declaration's *shape***
  (a constructor topology with no single-primary encoding, a signature that cannot be expressed).
  Deferred to the stage that MINTS one, which is this section's own third stage: a tag with no mint
  site is indistinguishable from one that is not there, which is the `TirTrace` failure the marker
  exists to avoid repeating. `UnportableKind.ConstructorTopology` is already in the taxonomy so the
  stage has a kind to mint, and the constraint below still stands — it stays derived from
  `CtorFunnel.Plans`;
- **four of `SpoonTir.unsupported`'s six sites**, which are the ones whose SHAPE a term-level marker
  cannot take: a `Constant` (the literal arm), a `ValDef` (a try-with-resources resource that is not
  a local declaration), the type operand of an `instanceof`, and the lambda-without-body guard. Each
  is a real mint site wanting a marker of its own kind; putting a term where the tree needs a
  declaration would be worse than the throw. `SpoonKinds.Absence.RefusedLoudly` is now exactly the
  kinds that still reach one of these, and `MarkedUnportable` the kinds that mint;
- **the correlation join.** `markers.tsv` is written (`unit`, `member`, `state`, `kind`, `catalog`,
  `javaPath`, `line`, `what` — keyed the way the source map keys members, and gated on the artifact
  layer per `CLAUDE.md` §5.1). §6.3's marked-region lane and the false-positive lane — one
  set-difference over the same two inputs — read it next.

The adoption order, kept so its builder does not re-derive it: the marker, traversal case,
conservation check, emission gate and fences land **together as one change** (CLAUDE.md §3 — check
and translation arrive together); then mint sites one at a time, each measured against §6.3's
precision check, starting with the frontend's refusal points (`SpoonTir.unsupported`), then transform
refusal points, then gate-derived tags. Two standing constraints: the minting rule wraps the
**smallest wrong term**, never a `this(...)`/`super(...)` delegation head (shape-matching consumers
read constructor prologues); and constructor-topology markers stay **derived from
`CtorFunnel.Plans`** — never frontend-minted — because the plan, the check and the emitter must keep
answering from one function (§7's rule, already enforced for the funnel's other decisions).

**One thing the conservation check answers with no exemption list**, because the risk table below
asks for it and a hand-maintained list is what `CLAUDE.md` §5.1 says rots: a marker that is gone
because its whole DECLARATION is gone is not an erasure. The owner settles it — if the declaration
went, everything in it went with it — and the survivor set is read from the TREES, never from the
symbol table, because a phase that drops a member removes the `DefDef` and leaves the `Symbol`
behind (nothing prunes the table, and it would be wrong to: every reference to the dropped member
still has to resolve). Asked of the table, the check reports every legitimate deletion as an engine
defect, which is the false positive that would have made the lane un-baselineable.

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

**Eleven of the thirteen kinds carry a note.** The line is drawn at *would a reader of this line be
unable to explain it from the line itself*. A rename, a drop, a substitution and an injection all
leave the emitted code saying something the upstream Java does not, with no local evidence of why.
The two excluded are not oversights:

| excluded | why |
|---|---|
| `RetypedSignature` | the new type is written in the declaration; a note per retyped member is 335 comments on libGDX core restating what the signature already says, and the noise would bury the ones that carry information nothing else does |
| `RedirectedCall` | recorded per declaration, and the rewritten call is right there in the body |

**`FunnelledCtor` was a third exclusion and is not one, and the correction is a lesson about how such
an argument dates.** The reason recorded was *"the emitted class has one primary and N secondaries —
that IS the funnel, in the code"*, which is true of a PROMOTION: the primary is a java constructor,
spelled as java spelled it, and the reader's diff shows a reordering. §8.2 then made SYNTHESIS the
normal case for a multi-constructor class, and the same sentence became false — the reader is looking
at a `protected` constructor **no java declared**, whose parameters are `sup$0` and `f$name`, possibly
followed by a parameter of a companion type called `Funnel` that has no runtime purpose at all. There
is no upstream line, so the source map cannot answer it either, while `slots` / `notSlot` /
`disambiguator` / `shape` — which the decision already carried — answer it exactly. An exclusion is an
argument about a SHAPE, so it has to be re-read whenever that shape changes; nothing in the pipeline
can notice that it went stale, because a missing note moves no count. Corpus cost: **380 notes over
thirteen ports** (libgdx-core 292), one per funnelled class.

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

---

## 8. Closing the adoption gap — ten designs

`PROGRESS.md` §11.9's three-way audit compared what the engine emits against what the hand ports
wrote, and the gaps it found were mostly not bugs. They were **design absences**: a family of
symptoms with one cause, patched one library at a time. Eight research briefs measured each family's
root cause before anything was implemented; this section is what they decided.

Two properties are shared by all ten and are the bar each was held to. **Fix by design, never by
accreting conditions** — every one of these families grew by one patch per library, and an entry
table with six faces of one defect is the evidence that the substrate was wrong rather than that the
world is complicated. And **a mechanism is (b), the policy is the port's** (§1): every new phase here
is default-off with an empty parameter meaning no code path, and the gate for landing it is every
lane reporting *0 members changed*.

The `ENGINE-LIMITS.md` entries each design retires, narrows or keeps are named in its subsection;
what is BUILT is `PROGRESS.md`'s business, not this document's.

### 8.1 Member identity — `Symbol.descriptor`, and one `PolicyBinder`

**Decision.** `Symbol` gains a `descriptor: Option[Descriptor]` field carrying the member's
parameter spelling. **`Symbol.fullName` is untouched.** One grammar, rendered and parsed in one
place, read from the frontend before any retyping. Policy keys are resolved once, by a two-stage
`PolicyBinder` in `api`, and **phases receive bound `SymId`s, never raw strings**.

**Zero baseline movement is the decisive property, and it is what chooses a field over a name.** The
obvious alternative — a fourth separator in `fullName`, `com.foo.Bar#baz(int,String)` — moves every
`findings.tsv` id in nine lanes (an id hashes the owner's `fullName`), every `decisions.tsv` row,
every `TirPrinter.canonical` dump and therefore every cache key. Worse, a descriptor contains `.`
and `$`, which gives `PackageRenameTransform.longestMatch` and `RuleScope.covers` a place to cut
*inside* the parameter list — §4.56's trap re-opened in the one function this project has been bitten
by twice. P4 already ruled the same way for a different reason: *leave the `fullName` alone*. With a
separate field, `members.tsv`, `srcmap.tsv`, `findings.tsv`, `decisions.tsv`, the porter notes, the
port map, the cache key and `Xref` are all unchanged **byte for byte**, and the migration cost is one
compiler-forced thread-through rather than a re-baseline.

**The descriptor is SOURCE-LEVEL spelling, not a JVM erasure, and that is sound rather than
convenient.** Java forbids two methods in one class with the same erasure, so source-level parameter
spelling is **already injective within an overload set**: `void m(T)` beside `void m(String)` is
legal and the two spell differently, while `<X> void m(X)` beside `void m(Object)` is illegal. The
descriptor therefore does not need to be truly erased — it needs to be *consistently derived*. That
is what makes the design cheap: a manifest key stays exactly the string a policy author writes today.

**Five key grammars exist in the engine and two of them disagree.** Both divergences are latent, both
are invisible to every count, and both become negative specs rather than reconciliation code:

| divergence | frontend/manifest grammar | engine grammar | why |
|---|---|---|---|
| an ARRAY parameter | `Owner#copy(int[])` | `Owner#copy(Array)` | Spoon's `getSimpleName` for an array reference is `component + "[]"`; the TIR renders it `AppliedType(Array, [Int])` and the engine key takes the *tycon's* name. A Java `T...` vararg is an array reference too, so every vararg member has the same split |
| `equals(Object)` | `Object` | `Any` | the frontend deliberately retypes a 1-arg `equals`'s parameter to `scala.Any` before building the `MethodType`; the manifest grammar is read before that, the engine grammar after |

Neither is exercised by the corpus — all 16 member-shaped policy keys in every port take `Class`,
`int`, `boolean` and `<init>` only — so this is a **trap set for the next library**, and an unmatched
key reports `NeverMatched`, which reads as a typo. The fix is one negative spec each: the array key
binds from *both* sides with the exact spelling `int[]` and `Array` does *not* bind; the `equals` key
binds as `Object`, never `Any`, with the descriptor asserted to be read before the retyping.

**A THIRD divergence was not latent: the QUALIFIED spelling every report shows.** The two above are
traps for the next library; this one was met by two ports, documented in two comments and removed by
neither. This grammar is SIMPLE names, and an EXTERNAL member's `Symbol.fullName` is its interning
key, parameters and all — so a `collection-boundary` row prints
`…#identityHashCode(java.lang.Object)`, and a key copied out of it matched NOTHING. Both ports wrote
the same workaround (`# BARE on purpose`, with a paragraph explaining why the precise form cannot be
written), which is exact for a one-overload member and wrong the day there are two — and it is advice
that reaches a reader only if they find the comment.

The binder holds both strings at the moment it fails, so the fix is a comparison and not a note:
`Descriptor.matches` compares `Param.simple` on both sides, cutting at the LAST `.` or `$` and only
there (§4.56 — `java.util.Map$Entry` is `Entry`, and the normalisation is the identity on every key
any manifest holds today). Two things follow. Two DISTINCT simple names stay distinct, so the
leniency is about the PACKAGE and nothing else. And what it newly admits is one shape java permits —
two overloads whose parameter simple names collide across packages (`m(java.util.List)` beside
`m(com.foo.List)`) — which is not a silent pick but the binder's own `Ambiguous` refusal, rendering
each candidate with its qualified signature *only* where the rendered keys cannot tell them apart, so
every message that could already be written is byte-identical.

**A dropped member is never interned, and that forces the binder to be two-stage.** The frontend
filters an executable out *before* the method symbol is minted, so a `dropMethods` key names a member
with no `SymId`, no `Symbol` and no row in the symbol table — it cannot be resolved against a
`Program` at all. The general rule, worth stating because it also explains why a dropped TYPE has no
`srcmap` entry (§4.56): **policy that REMOVES something can only be bound where the thing still
exists, which is the frontend.** So stage 1 is a `MemberIndex` the frontend publishes, carrying every
executable it saw *including the ones it is about to drop*; stage 2 is `PortRun` binding every key
once, before the pipeline runs. The index also subsumes `Substitutions`' mutable `matchedKeys` tally,
whose own scaladoc apologises for being a mutable field on a `case class` that `copy()` empties.

Three boundaries bound the design and are stated so an implementer does not chase them:

- **A FIELD has no descriptor, and that is the complete answer rather than a gap.** A binder that
  reported it as unresolved would produce a finding for every field in the program.
- **An external member whose `info` never resolved has none.** That is D1's surviving eight-finding
  residue and the design cannot remove it. What it can do is make the failure LOUD at bind time
  instead of silently degrading to arity at match time.
- **The convenience source-parse path takes the unresolved branch systematically**, where a
  reference's formals are erased. So a spec pinning descriptor identity must be written against a
  real source-tree model, or it pins the erased answer and passes for the wrong reason.

**A SYNTHETIC member gets an ENGINE-derived descriptor in the same grammar, and is never a
`MemberIndex` entry.** The engine mints members the frontend never saw — §8.2's synthetic primary and
its tuple-spreading auxiliary above all — and §8.3's contract publishes the primary's *signature*, so
those members do need a descriptor. It is derived by the engine, in the **same source-spelling
grammar** a manifest author writes (`(int,String)`; `int[]` for an array or a vararg, per the two
negative specs above), so a published contract row and a policy key can never be in two grammars.
Three consequences, each a spec:

- the contract's `primary=` row carries the primary's slots **in descriptor grammar**, and where the
  collision disambiguator is §8.2's companion-private marker the row says `disambiguator=marker` —
  **never an invented FQN for the marker type.** The marker is per-class and companion-private, so an
  FQN in a published artifact would name something no consumer can resolve and none may depend on;
  `disambiguator=marker` is the whole fact a dependent needs (there is one extra final parameter it
  cannot supply and must not try to).
- a synthetic member is **never** a `MemberIndex` entry. The index's declared question is *what the
  frontend saw* — that is exactly why it is stage 1, and why policy that REMOVES something can only
  bind there. A member the engine minted after the frontend ran is in neither of the two places a
  policy key can legitimately name.
- therefore `PolicyBinder` refuses a key naming one with a dedicated **`SyntheticTarget`** reason,
  never `NeverMatched`. The two read identically in a findings file and mean opposite things:
  `NeverMatched` says *your key is a typo*, `SyntheticTarget` says *your key names a member the engine
  created and policy has no standing to address* — which is (a) work if it should be addressable at
  all, and §4.45's rule is that a finding an agent cannot classify costs it a full investigation.

**The evidence that this is one design defect and not a collection of local ones**: two independent
phases have already routed *around* member identity, each with the reason written down.
`TestFrameworkTransform` refuses to read a callee's parameter list and reads the arguments' static
types instead, because *"a callee's parameter list is only as good as the frontend's key encoding for
an EXTERNAL symbol"*; `CollectionsTransform` reads a call's RESULT type to decide which `remove`
overload Java resolved (§4.4). Two routes around the same wall is the signal.

**`Program.members` is a REQUIRED constructor parameter, not a defaulted field.** There are 28
`new Program(...)` sites outside tests and the pipeline rebuilds a fresh `Program` after every phase;
a defaulted field would be silently dropped at 27 of them and at every phase boundary — a regression
no count could see, which is precisely the class of defect this repository has shipped before. The
required parameter makes it a mechanical, compiler-forced migration, and `Program.rebuilt` becomes
*the* way a phase returns a program.

**Never-fired reporting unifies as a side effect.** The binder accumulates every binding with the
`(phase, setting)` that asked for it and owns the report, so five phases lose their private
`var report` and hand-written `audit`. The case that pays immediately is `ExternalOnly`: `RuleScope`'s
own doc already describes it as a live silent no-op — *the entry counts as having FIRED and
`neverFired` therefore reports nothing* — and in the binder it is one branch.

**One asymmetry decided on purpose rather than inherited.** A BARE `owner#name` key drops every
overload under `dropMethods` (which is how a reflective family is dropped wholesale) and is reported
`Unverifiable` by another phase — two seams, one key convention, opposite policies. The decision:
bare stays legal for `dropMethods`, exactness is required for call-site substitution, and
`Unverifiable` is the answer everywhere else.

**The honest limit, stated rather than implied.** `Symbol.fullName` is public and `String`-typed, so
nothing in the type system stops a new phase writing `s.fullName.startsWith("java.")` — the exact
defect §4.56 records. This is **a convention with a lint, not a type-level guarantee**: a source-level
check in the engine's own suite forbidding `fullName ==`, `fullName.startsWith` and `+ "#" +` inside
the transform package with a named allow-list, plus the auditor's hunt line.

**Two things the design listed that do NOT go, corrected by implementing them.**

- **`PortMapTransform.preciseKey` and `PortMap.erase` stay.** §8.1 listed them beside
  `MethodBodyTransform.keysOf` as key machinery the binder replaces. They are not: they are the two
  ends of ONE JOIN in the EMITTED namespace. A base publishes a member's `upstream` column as
  `erase(TirEmitter.memberKey(…))` — emitted type names, so `Array` for an array and `Any` for
  `equals` — and a dependent reconstructs the same string from the callee's `info`. Moving the
  reading end to the descriptor alone makes the join MISS on exactly the members the two grammars
  spell differently. Moving both ends together re-publishes every `port-map.tsv`, an artifact
  dependents read, so it is a measured commit of its own and not part of an identity change whose
  gate is byte-identity elsewhere. D1's arity residue survives, scoped to that join.
- **Four `policyReport` vars go, not five.** `PortMapTransform`'s report is about a whole MAP naming
  nothing, which is not a key binding and has no binder answer.

**What the `policy-binding` measurement corrected in this design.** Both were false statements in a
findings file, neither moved a member digest, and nothing else in the pipeline could have seen
either — which is the whole argument for taking the measurement while both answers still exist:

- **The `SyntheticTarget` refusal is STRUCTURAL, so the `MemberIndex` must hold every executable the
  frontend WALKED — initialiser blocks included.** A `static { }` block is a member a port really
  does key policy on (gdx-vfx replaces the body of one whose Java branches on a reflective class the
  base drops). Left out of the index, the binder found its symbol in the program, found its owner
  among the walked types, and concluded that the ENGINE had minted it: measured, `policy 0 -> 1`,
  refusing a hand-written Java block as engine-created. The index therefore also holds a LIST per
  key — a class with two `static { }` blocks has two members at one identity, and a map keyed by it
  keeps one of them in silence.
- **`Ownership` is not decoration, and `TypeRedirectTransform` is why it exists.** Every other keyed
  seam wants `Owned`, because an entry naming a type the program merely REFERENCES is the §1(b)
  silent no-op. That phase is the exception BY CONSTRUCTION: its subject is a type this module does
  not declare and cannot declare — the base dropped it, and exactly one module may ship a
  replacement at a given FQN — and what it rewrites is every REFERENCE to one, not a declaration.
  Bound as `Owned` it reported ten correct redirects as never-matched: measured on
  libgdx-screenmanager, `policy 0 -> 10`.

**Rejected.** A fourth separator in `fullName` (above). A structured `SymId` — it is an
`opaque type SymId = Int` whose interning order is relied on for deterministic minting, and
structuring it would put symbol identity into every `Xref` map key for nothing the field does not
give. A true JVM erased descriptor — correct and wrong for this engine: no manifest author writes it,
all 16 existing keys would change, source spelling is already injective, and it cannot be produced
for a type variable whose bound the frontend could not resolve. Deriving the descriptor at the ENGINE
from `Symbol.info` alone — it permanently re-creates the `equals` divergence (`info` has already been
retyped) and cannot answer for a dropped member, which has no `info` because it has no symbol. A
local arity or arg-shape test per vulnerable site — measured: arity produced **118 `Ambiguous` out of
263 findings** and the acceptance case itself came back undecidable. A `Symbol => Boolean` policy —
strictly more expressive and impossible to report on, which `neverFired` is the whole point of.

**Retires and unblocks.** D1's root defect narrows to its external residue; the two divergences become
recorded limits with negative specs instead of latent traps; `Substitutions.matchedKeys` is deleted.
It is a hard prerequisite for call-site substitution (a bare `Json#toJson` names three overloads with
three different arities, and an expression template with positional holes can only be right for one) —
BUILT, see §8.12, which also records what the comparator call-site table turned out to need — and for §8.3's member rows — a per-member contract row that is not
overload-keyed reproduces D1's own 118 ambiguities.

### 8.2 The constructor funnel — a synthetic PROTECTED primary

**Decision.** Every class with two or more constructors gets a **synthetic `protected` primary**
whose parameters are *(the formals of the single parent constructor its roots reach) ++ (the values
of the fields its constructors assign, where those values are expressible before construction)*. The
primary's `extends` clause passes the super slots and its body assigns the field slots; **every Java
constructor becomes a `def this(...)`** delegating with expressions and running its own remaining
statements afterwards.

**The fact that changes the design is an emitter comment that is false.** `TirEmitter` asserts that
*"scala's `extends C(args)` can only ever invoke C's PRIMARY, so hiding it would make the class
unextendable"*. A probe — a `private` primary, three secondaries, three subclasses **in another
package** — compiles and runs. That false claim is the only thing keeping today's synthetic primary
public; correcting it is part of the change.

**`protected`, not `private`, and the reason is not taste.** `private` works for every subclass the
port itself emits, and the reference ports use it freely — but only on classes nobody extends (zero of
their 22 private-primary classes is extended anywhere), switching to `protected` the moment one is
(16 such classes, whose subclasses call the primary from their `extends` clause directly). Deciding
*"is this class extended?"* is a **whole-program question, and a whole-program question in the plan
table is exactly what D4 measures as drift**. `protected` needs no such question and cannot be reached
by ordinary client code. The negative boundary is pinned by a spec because it is what makes the choice
real: `private` is class-private, not package-private, so even a *same-package* subclass cannot reach
a private primary's slot.

**Slot derivation.** Super slots come from the parent constructor's own **formals**, never from one
call's argument types — an argument may be narrower than the formal. Field slots are the **leading
run** of `this.f = e` assignments in each root, gated on three conditions: every root either assigns
`f` in its own leading run or does not assign it at all; the value expression does not read `this`
(Java may there, a Scala delegation argument list may not); and no delegating constructor or method
assigns it again. A root that does not assign `f` supplies `f`'s own Java initialiser or the Java
default at that slot. Everything *not* in a root's leading run stays a post-delegation statement of
that root's secondary, in source order — which is what makes interleaved statements a **degradation
rather than a wall**.

**The third condition above is TWO conditions, and conflating them demotes half the corpus.** "No
delegating constructor or method assigns it again" is the *`val`* condition (A1): it decides whether
the slot binds as `val f = slot` or `var f = slot` in the primary body, and a field with an ordinary
setter fails it while remaining a perfectly good SLOT. Slot eligibility is conditions 1–2 plus
order-safety below; `val`-vs-`var` is decided afterwards, per field, from the whole-program
assignment count the plan data already carries. Reading the three as one gate would have demoted
most settered fields to the no-slot path for no semantic reason.

**Order-safety: a slot value evaluates BEFORE super; Java evaluated it AFTER.** Java runs a
constructor body's field assignments after `super(...)` and after the instance initialisers; a value
hoisted into the delegation argument list runs before both. Condition 2 (no `this`) already removes
instance-state reads — Scala enforces it in a delegation argument list — but a static read, a
companion call or a `new` can still observe the reordering. The rule: a slot value must be
ORDER-BLIND — composed of the secondary's own parameters, literals, and operator applications on
those; an expression containing any method call, `new`, array read or static/field read stays a
post-delegation assignment of its secondary, `reason=order` recorded per field. Step 3 measures the
survivor count first; a purity allow-list is only worth designing if that number says so. (This is
§4.4's discipline applied to the funnel's own synthesis: ask what the form means when its evaluation
ORDER is observed, not only what it looks like.)

**Build order inside step 3: the disambiguator comes FIRST, the slots second.** Field slots make a
synthetic signature look exactly like a value class's all-fields constructor — which is the moment
C8's applicability test starts refusing widenings. With the marker (C9) already in place the
applicability problem cannot arise (the marker changes the primary's arity), so landing marker-then-
slots turns a correctness cliff into a non-event. The reverse order was the plan's original text and
is retired.

**AS BUILT — six things this section said that the implementation refuted, each with its number.**
They are here rather than in a footnote because every one of them was measured after the design was
signed off, and a reader who takes the paragraphs below at face value will re-derive them.

1. **Step 4 — "the withholding fixpoint is DELETED for the 348" is REFUTED. 0 → 4 compile errors,
   omissions 180 → 196.** The premise (a synthesis removes no construction path) is true; the
   conclusion does not follow, because the fixpoint's TRIGGER is not "java wrote an argument-free
   `extends`" — it is `needNilary`, computed from the SUBCLASS's plan, and a subclass whose own plan
   carries no super arguments emits `extends P` **bare** even where java wrote `super(args)`. The
   guard stays, gated on "does a NILARY SECONDARY survive". What was actually wrong is the
   **fallback**: withholding dropped to `nilaryPlan` and threw away the promotion the class would
   otherwise have had, dropped `super(args)` 30 → 79. It now falls back to
   `plan0(…, synthesis = false)`, and the count returns to 30. `ENGINE-LIMITS.md` C1.
2. **"Slot-eligibility IS `val`-eligibility" needs a JAVA fact on top.** The write count is over THIS
   RUN's program and a dependent module is not in it: decided by the count alone, libGDX core emitted
   20 `val` slots at 0 errors and **gdx-gltf went 7 → 23**, every one `E052 Reassignment to val`.
   The condition is `final || private` AND the count. `ENGINE-LIMITS.md` C1.6.
3. **The marker's argument is ASCRIBED, `(null: C.Funnel)`.** A bare `null` inhabits every reference
   type, so it is applicable to every one-argument constructor of the class — the ambiguity the
   marker exists to remove. With the ascription no real constructor can shadow the disambiguated
   primary at all, so the "refuse where even the marker is shadowed" arm this section asked for has
   no cases and is not built.
4. **"MARKER FIRST, THEN field slots" is a LANDING order, not a computation order.** Both predicates
   ask about the signature the emitter will write and the arguments it will pass, and field slots
   change both — a class colliding with the parent's formals does not collide with
   `formals ++ fieldTypes`. Asked against the super slots alone, the erasure and applicability tests
   minted a marker for classes that needed none. Slots are derived first; the predicates then see the
   whole list. The collapse is tried only where there are NO field slots, since with them nothing
   collides and collapsing would trade the class's entire `val` binding for a problem it has not got.
5. **THE COLLAPSE IS DELEGATED TO POSITIONALLY, and the type-matched fill was dropping exactly what
   this section exists to express.** A collapse promotes a root whose parameters ARE the parent
   constructor's formals, and the class was a candidate only because EVERY root reaches that one
   parent constructor — so every sibling's `super(args)` fills the same formals in the same order.
   Sent through the fill instead, `Mixed(String s) { super(s, 7); }` beside a promoted
   `Mixed(Object a, int b)` found no `Object`-typed argument, declined, and lost BOTH arguments to a
   bare `this()`; it compiled, and `CtorFunnelSuperArgsSpec` pinned it as correct. The alternative —
   DECLINE the collapse whenever a root's delegation would be `Dropped`, in the same attempt-order
   style as the escapes decline — was weighed and rejected twice over: it prices a class that has a
   perfectly good promoted primary into the marker path (a synthetic signature and a companion
   member, and a moved diff for every such class), and the question it would have to ask is the
   delegation's, which is `Plans.superCall`'s and reads the DECIDED plan — asking it again at the
   nomination is the second locally-written copy of one rule that `ENGINE-LIMITS.md` C7 records as
   the way the count and the emission came to disagree. The fill stays FIRST, because it is what
   supplies a JDK throwable's padded slot; positional is the fallback, keyed on `Plan.collapse`,
   which the collapse itself records (§4.56: a phase concludes only from what the phase did).
6. **"escapes 188 → 0 for the 348" is not reached; the measured figure is libGDX core 140 → 31.**
   The residue is wall classes, JDK-throwable parents, and — the largest single item — the
   UNIQUE-ROOT class whose promotion the C1 fixpoint withholds (`ObjectMap`, 3 paths): one root, so
   the synthesis's two-root condition excludes it. Widening the synthesis to a withheld unique root
   is the next item, and it is not this one.

Two implementation invariants that are not restatements of the above, both of which cost a
measurement: **the synthesis fires for the IMPLICIT `super()` too** — a root with no explicit super
call reaches the parent's nilary constructor, so a class all of whose roots do that reaches ONE
parent constructor, and that is the entire domain the promoted-body escapes lived in; and **"is this
a synthesised primary" is `Plan.isSynthesised`, never `synthetic.nonEmpty` and never
`primary.isEmpty`** — the first misses a marker-only synthesis (empty slot list), the second is also
true of a synthesis and let `nilaryPlan` overwrite every one of them in its own domain, escapes 95
→ 31. `ENGINE-LIMITS.md` C1.5.

**Step 4's acceptance, rescoped by measurement.** The original "gltf's D4 trio → 0" double-counted:
one of the three was the local shape and fell in step 2 (8 → 7); the other two reach TWO DIFFERENT
parent constructors — genuine walls, out of local derivation's reach by definition, waiting on
§8.3's seeded row or remaining as recorded walls. The fixpoint deletion for non-wall classes
therefore expects NO error movement anywhere; its gate is the escapes count and the deleted code,
not an error delta.

**A consumed assignment's TRIVIA rides the slot** (§4.58 — the comment is the licence-bearing part of
the port, and nothing else in the pipeline can see it go). A `this.f = e` folded into a slot does not
disappear from the emitted file: its comment rides the **primary body's** assignment of that field,
and where N roots each contributed the same slot the comment attaches to **each secondary's
delegation** — the one place the funnel duplicates rather than moves. This is §8.8's claim-then-drop
shape one level up (the statement is *claimed* by the funnel and then has no statement left to sit
on), so it is pinned by `SyntheticPrimarySlotsSpec` rather than left to whichever harvest runs last.

**This shape is in-repo prior art, not a proposal.** The frozen BIR path's `CtorPlan.maximalPrimaryPlan`
already mints exactly it — a non-public primary whose parameters are super slots ++ assigned-field
slots, field assignment in the primary body, each root a secondary delegating with its own super args
and field values, and an unassigned field filled from its own initialiser or the Java default. It was
written once for the flexmark/liqp corpus and not carried across to the TIR rewrite.

**The double-evaluation trap is UNREACHABLE BY CONSTRUCTION, and the tuple/auxiliary encoding this
section used to specify for it is retired.** The trap as stated was `this(h(n), h(n) * 2)` — a hoist
evaluating `h` twice where Java evaluated it once — and the answer was a companion helper returning a
tuple plus a `private def this(p: (A, B))` auxiliary spreading it, *entered into* the emitter's
delegation-topological ordering rather than appended. None of it is buildable, because none of it has
a domain. ORDER-SAFETY above admits a slot value only when it is **order-blind** — composed of the
secondary's own parameters, literals and operator applications on those — so any expression
containing a method call, a `new`, an array read or a static/field read is REFUSED a slot
(`reason=order`; 146 of libGDX core's 166 refusals) and stays a post-delegation assignment. `h(n)` is
a method call, so it never becomes a slot value at all, and what does survive into a delegation
argument list is by definition RE-READABLE: evaluating it twice reads the same immutable parameter
bindings and applies the same pure operators. Order-blindness and evaluate-once are two different
properties and neither implies the other in general; this is the one place where a single condition
happens to buy both, which is exactly why it is written down rather than left to be re-derived from
the reference port's habit of recomputing a subexpression three times in one delegation.

**Collision disambiguation has TWO predicates, and the design originally stated only one.** The first
is about DECLARATIONS that cannot coexist and is by **ERASURE**, because that is the test scalac
applies — the measured error is `E120 … have the same type after erasure`, and `private` does not
separate the two declarations. The second is about the DELEGATION each secondary writes, and it is
**overload APPLICABILITY**: scalac resolves `this(<a root's own super arguments>)` against every
constructor of the class, applicability first and most-specific second, so a real constructor whose
parameters are *narrower* than the parent's formals wins the call while its signature equals nothing.
Measured 0 -> 2 on libGDX (`DistanceFieldFontCache`, one of them an infinite self-delegation);
`ENGINE-LIMITS.md` C8 has the shape. A widening needs both tests, and the applicability one is asked
per ROOT against the arguments the emitter will actually write.

Two answers in order: **collapse** — if the colliding constructor is a pure pass-through whose
parameters *are* the slots, promote it and emit no synthetic member at all, which covers most of the
100 measured collisions (they are overwhelmingly value classes whose all-fields constructor IS the
synthetic primary) and leaves output unchanged — **but only where that promotion has no ESCAPING
PATH**, since a collapse promotes a real constructor and C7 therefore applies to it again: its body
becomes the class body and runs where java ran nothing. "Byte-for-byte unchanged" is worth having
only where nothing is wrong with the bytes, and the escaping case is rare enough to price at the
marker (two classes corpus-wide: noise4j's `Object2dArray`, which was that port's entire omissions
residue, and libGDX's `Dialog`). The question is asked through `CtorFunnel.escapesOf` — the same
function `OmissionCheck.promotedBodyOnEveryPath` counts with — so the nomination and the count
cannot disagree about what a promotion costs. Otherwise **a final parameter of a marker type**,
minted **per disambiguated class** rather than once in `runtime/`: emitted code then carries no
dependency on the engine's runtime artifact for a purely local encoding, at the price of ~100
one-line companion members corpus-wide. The marker also answers the applicability problem outright,
which is the larger reason to have it: it changes the primary's ARITY, so no delegation can reach a
real constructor by accident.

**The marker is `protected` in the companion, NOT `private`** — a correction, because the two halves
of this design were validated in separate probes and only their combination fails. Scala requires
every type in a member's signature to be at least as visible as the member, so a companion-`private`
marker in a `protected` primary is `non-private constructor C in class C refers to private class
Funnel`. `protected` compiles, runs, and is reachable from a subclass's `extends` clause in another
package, which is exactly the reach the `protected` primary needs (`ENGINE-LIMITS.md` C9). §8.1
states how it reaches a dependent — the contract row says `disambiguator=marker` and never spells the
marker as an FQN, because a companion-protected type is not a name any ordinary consumer may resolve.
Rejected `(using DummyImplicit)`: the declarations coexist but every call is an ambiguous overload
unless the `using` clause is passed explicitly, and it puts a second parameter clause on the class.

**Initialisation order is reproduced exactly, not approximately.** javac and scalac traces of the same
program are byte-identical: super arguments evaluate at the delegation site, super runs, instance
initialisers and init blocks run in source order, the constructor body runs — and a
`this(...)`-delegating constructor does **not** re-run the initialisers, on either side. Scala's class
body occupies precisely the slot Java's instance initialisers occupied, which is why the encoding is
order-*exact*.

**The one assumption everything rests on**, validated three ways: a Java super-argument is a pure
expression that cannot read `this`. Every corpus upstream declares Java 1.6–1.8; the frontend pins its
compliance level below the release that introduced flexible constructors, so the *input language*
excludes them by construction; and empirically, across 1,106 corpus files plus flexmark separately,
**1,454 super/this invocations with 0 violations**, with the probe's own `super-arg-reads-this` counter
**0 in every port**. Raising that compliance level re-opens this and must fail loudly.

**The measurements that decide the design** (raw, pre-pipeline, over 430 multi-constructor classes in
eleven corpus source sets). **They are a census, not a lane**, and the difference has since been
measured rather than assumed: the raw pass sees the Java before any substitution, drop or package
rename, so its residue counts are an upper bound on what a lane reports. Where a lane number exists,
it governs — libGDX core's EMITTED omissions are 140 promoted-body escapes and 31 dropped
`super(args)` against the census's 144 and 31, and the dropped-super residue there is dominated by
genuine WALL classes (`DistanceFieldFont`'s seven roots to seven overloads alone is 7 of the 31), so
generalising the super-slot rule alone moves libGDX by **zero**. `PROGRESS.md` §7 carries the
per-step numbers.

| | |
|---|---|
| expressible with **no wall** | **348 of 430 (81 %)** — of which 100 need a disambiguator, priced at one parameter |
| the **wall** — roots reach DIFFERENT parent constructors | **82 classes**, of which 11 are reducible through a pure `this(...)` chain |
| of the wall, **already fully expressed today by replay** | **73 of 82** |
| promoted-body escapes today | **188 → 0** for the 348 |
| non-hoistable classes (a field stays a `var`) | 129, with the reason recorded per field |

The 73 is the single most important number here: **replay must be KEPT**, or the design trades 33
dropped `super(args)` for roughly 200. The wall is genuinely irreducible — one `extends` clause, N
parent constructors — and the attempt order per class is: one parent target → synthesise; reducible
chain → reduce, then synthesise; a JDK-throwable parent → the measured widest-pass-through with null
padding, and **synthesis must not be consulted first** (recorded: consulting it first cost 4
omissions); otherwise promote a nilary root, replay, and count what is still dropped.

**A1 folds in and is not a second pass.** A field that is a SLOT is assigned exactly once, in the
primary body, from a parameter — which *is* the condition for emitting it as a `val`. Slot-eligibility
**is** `val`-eligibility: one derivation, one answer, no way for the two to disagree.
`scala.compiletime.uninitialized` covers the residue.

**The withholding fixpoint is DELETED for the 348.** It exists solely because promoting a paramful
constructor *removes* a class's nilary construction path; a synthetic primary removes nothing — every
Java constructor survives as a secondary, `extends Parent()` reaches the nilary one and
`extends Parent(args)` the paramful one. Measured addition: `class D extends C` with **no parentheses
at all** — which is what the emitter writes for a subclass whose plan carries no super arguments —
also resolves to the nilary SECONDARY. That is the exact condition under which a paramful primary is
safe, so the guard is not "is this class extended" but "does a nilary constructor survive": a
synthesis satisfies it whenever the Java class declares one, and a promotion never can, because it
consumes the constructor it promotes. And the synthetic signature is a **local function of the
Java** — parent formals plus this class's own field declarations, consulting no subclass — so a
dependent computing it from the same Java gets the same answer the base did: **D4's measured drift has
no cause left** for a non-wall class. The fixpoint narrows to the 82, where it is still whole-program
and still D4-exposed, which is what §8.3's contract row is for.

**Provenance: extend the existing kind, do not add one.** `FunnelledCtor` already records shape,
primary, constructors, super args and escapes. `shape` gains `synthetic-primary` /
`synthetic-primary-collapsed` / `wall-replay` / `wall-counted`, derived in the one place shape is
derived; `slots` names each slot and its origin (`sup$k` = parent formal *k*, `f$<name>` = field) so a
reader can join the emitted signature back to the Java **without the run directory**;
`disambiguator=marker|none`; and `notSlot="cache=reads-this"` is the sentence an agent asking *"why is
this field a `var`"* needs, which A1 has no other channel for.

**Rejected.** *Keep promotion, add a seventh and eighth shape* — the path the file has been on for four
additions, and weaker for a structural reason: **every shape that promotes a real constructor inherits
C7 and C1 by construction**, because promotion makes one Java body the class body and removes one
construction path. Both available escapes were measured (blanket refusal 0→41, targeted 0→35). The
synthetic primary is the only encoding under which *no* Java body becomes the class body. *Keep the
primary public* — justified only by the false emitter claim; it widens the API with a constructor Java
never exposed and makes the signature part of the published surface rather than an implementation
detail. *A discriminator slot for the wall* — the `extends` clause is evaluated once with one argument
list, so a discriminator can select different **arguments to the same parent constructor**, never a
different one; that is the null-padding whose guess measured 0→55 outside the JDK family. *Default
parameters* — built end to end once and the emitted class came out unchanged, the path never
consulted; independently, the reference ports prefer overloaded `def this` 14:1 and 46:1 and never put
a default on a funnel class. *A funnel-only `skipInit` flag* — a human wrote one, and it adds a
parameter that changes the class's construction API for a reason no Java reader can see, where
post-delegation statements need no flag. *Computing slot values in the primary body* — the primary body
runs on every path, so a per-root value cannot live there; it is the promotion problem one level down.
*Seeding the dependent's plans from the base's map* — not rejected but **superseded** for the 348,
since a local derivation needs no seed.

**Retires.** C1, C4 and C7 for the 348 (**188 escapes → 0**) and D4 for non-wall classes; C2 narrows
sharply (engine-named slots cannot collide with an inherited member, and only the collapse case
promotes real parameters). C3 and D5 narrow to the wall. C5/C6 are unchanged — replay is retained and
both govern it exactly as written. The three C7 sites that were *observable* are the worked examples:
a `Material` whose nilary body bumped a static counter on every construction, a `Table` leaking one
pooled cell per construction, and a `Button` running `initialize()` on 8 of 10 paths. Under the design
each constructor's statements run on its own path only, and there is nothing to strip.

**AS BUILT, SEVENTH — THE CLAUSE-BEARING EMPTY PRIMARY: what a `Plan.none` class does with a context
clause (`ENGINE-LIMITS.md` CT5).** The funnel has THREE outcomes and this section had only ever
specified two of them. A class is PROMOTED (a java constructor becomes the primary), SYNTHESISED (the
funnel builds one), or neither — `Plan.none`, where the emitted class relies on Scala's own implicit
nilary primary and every java constructor becomes a `def this`. That third outcome is the most common
one in real code, and it is the one with no parameter list for a phase's `(using T)` group to land
on: the clause reaches every secondary and the class body has no given in scope. Measured on one
corpus library at `attach = "class"`: **57 scalac errors, 55 of them this**, over 19 top-level plus
at least 3 nested classes of 188 threaded.

**The decision is CLAUSE-CONDITIONAL, and that is a statement about the emitted text rather than a
convenience.** `Plan.none` gains exactly one thing — the context clause its OWN constructors already
carry (`CtorFunnel.classGivens`, applied once as a post-pass over the decided plans so every road to
"no primary" is covered: the nomination's four `Plan.none` returns, the withholding fixpoint's
fallback, and the module/trait/enum guard). With no clause anywhere the post-pass computes `Nil`, the
plan is `Plan.none` unchanged, and the emitted text is byte-identical — **13 ports, 0 members
changed**, which is the only acceptable price for a shape change with every phase default-off.

**What the clause-bearing primary DOES about `super` is NOTHING, and that is the whole of why it is
sound.** It hosts the clause and delegates nothing: `superArgs` stays empty, so the `extends` clause
is the bare parent it was (java's implicit `super()`, which Scala's `extends P` already runs); every
secondary still writes the delegation it wrote before, and a `super(args)` the funnel could not
express is still `SuperCall.Dropped`, still rendered `this()`, still counted by `OmissionCheck`. The
synthesis's parent-agreement preconditions (`targets.sizeIs == 1`, `arities.sizeIs == 1`,
`formals.sizeIs == arities.head`) are not consulted because nothing is being lifted into an `extends`
clause — there are no slots. So CT5's caution — *a `Plan.none` class's roots are exactly the ones
whose `super(args)` is already a counted omission, so making the primary real for this shape IS the
synthesis widened past its preconditions* — is answered by not touching them at all: this is not the
synthesis widened, it is the implicit primary made SPELLABLE. The omission census is arithmetically
unchanged (libGDX core 65 before and after), which is the check that says so.

**The shape is `(using T)` and never `()(using T)`, and `this()` reaches it — validated by running,
not asserted.** scalac 3.8.4: with `class X(using Ctx)`, a secondary `def this(k: Int)(using Ctx) = {
this(); … }` compiles and RUNS (the compiler supplies the primary's argument from the secondary's own
anonymous clause), as do `new X()`, `new X(3)`, a body `summon`, a field initialiser that summons,
and `class Sub(using Ctx) extends X` with no parentheses. An empty value group in front would be a
different signature at every call site, which is the reason the emitter's promoted-nilary branch
already refuses to add one. Note what this makes the emitter's job: `prim` for a plan with no primary
and no synthesis is ALREADY `givenClause` (the branch CT4 landed for a promoted nilary constructor),
so the emission needs no new branch — the funnel had simply never given that plan a clause to render.

**E051 is the same `paramss.flatten` mistake CT4 closed, one level down, and the answer is NOT to
promote.** A java NILARY constructor that delegates (`BitmapFont()` calling `this(…)`) is DEGENERATE
against a nilary primary — Scala's implicit one already is no-arg — and `TirEmitter.orderBody` has
dropped it since before any of this. It asked `d.paramss.flatten.isEmpty`, which is *what does this
constructor take* where the question is *what did JAVA declare*: with the clause the constructor
stopped being degenerate and was emitted as `def this()(using T)` beside a primary of the same erased
signature. Measured on the probe: `E120` at the declaration plus one `E051` at every argument-free
`extends` and every `new C()` — which is exactly the pair the census reported for
`BitmapFont`/`DistanceFieldFont`. Reading `CtorFunnel.valueParams` restores today's answer for the
same class with no clause. Promoting the java nilary constructor instead was weighed and rejected:
`nilaryPlan` refuses any class where a constructor carries `super(args)` — BitmapFont's do — so
promoting here means widening a promotion past its own preconditions to remove a clash the existing
rule already removes, with a bigger blast radius for identical emitted text.

**The SILENT half needs a check, because none of the above is what a green compile measures.** A
threaded class with no body `summon` and no threaded construction in its initialisers loses its
clause and COMPILES, while its decision row and its porter note both claim it. So the emitter records,
per type it renders, whether the class's constructors carried a context clause and whether the
rendered header carries one, and the run reports each disagreement as `context-seam`'s fifth kind,
`lost-clause` (§1(a), fatal to nobody but counted like every other seam). It is recorded from the
RENDERED header text at the one place that writes it, and after emission, because a check that reads
the plan would have passed on the day CT4 flattened the clause into a value parameter. Three shapes it
covers by construction and no other check can see: a class the funnel gives no clause, a `trait` that
Scala's trait parameters are not the answer for (the port's `promoteToClass` is), and a java ENUM,
whose primary IS its java constructor and whose CONSTANTS would each have to pass an argument —
its clause is dropped from the parameter list rather than emitted as `var : T`, and counted here.
(Both enum shapes, and for one reason: a `case object` and a scala 3 `enum` case reach the primary
the same way and neither can supply a clause — `ENGINE-LIMITS.md` T21.)

**Not changed, deliberately: the CONTRACT row.** `Descriptor` records the primary's VALUE slots, and a
context clause is not one — two modules that disagree about the clause are caught by the phase's
`surfaceFingerprint` through `ManifestAgreement` (§1.5), which is where a disagreement about POLICY
belongs. Putting it in the descriptor would make the same fact fatal in two places with two spellings.

### 8.3 The published base surface — a `Surface` VIEW, and prevention rather than a check

**Decision**, three parts: the port map goes to **schema 3 with ONE new column, `shape`**, carrying a
`k=v` payload in the porter-note grammar; phases and the emitter receive a **`Surface` view** instead
of a bare `Program`, answering `Own` / `Published` / `Unknown`; and **an `Unknown` that shaped emitted
text fails the run.**

**The family keeps recurring because the substrate is wrong.** `Program.owned` roots its ownership
climb on `program.units` — *all* of them, the base's included — so it is a **program-vs-JDK filter,
not a mine-vs-base filter**, and in a dependent it answers `true` for every base symbol. Every
`RuleScope` rule, the package rename and the port-map repointing ask it. The five real mine-vs-base
filters are **six independent copies** of the same fuel-bounded climb, all on the reporting side, none
on the rewriting side, with different failure directions. One ownership predicate is the prerequisite
for everything else here.

**Inventory: 24 sites where a run answers a whole-program question covering non-owned types; nine have
drift that MATTERS** — the dependent emits text whose correctness depends on the answer agreeing with
the base's. Only two of the nine are the already-diagnosed D4/D5. **Three are new and unpatched:**

- **descendant clash renames.** A field is renamed `x → x$field` iff this class *or any descendant*
  declares a method `x`; a dependent subclass declaring `def x()` therefore renames the **base's** field
  in the dependent's run, while the base emitted `x`. Zero corpus sites today — and the ownership
  filter *hides* it: the dependent records the rename decision and then withholds it, so no count
  moves, no note prints, and only a joint compile fails. That is D4's signature failure mode at a
  second site.
- **the `export` exclusion lists.** 311 emitted `export` lines corpus-wide, whose excluded static names
  are computed from the base's **Java as this run reads it**, not from what the base **emitted** — so a
  base static that was renamed, dropped or moved is named wrongly, and a base with no companion makes
  `export X.*` an error outright.
- **D6's cross-module face.** A base that collapsed an all-static class to a bare `object` (31 of them
  in one base), named by a dependent in a *type* position. Measured 0 today, which is exactly D6's own
  observation that the base has 31 and names none of them, *which is why five ports did not see it*.

Four more are **latent behind default-off phases Stage P is scheduled to arm** — the globals→context
closure, the opaque-type seeds, the collections scope and the flow propagation. That is the ordering
constraint: **the `Surface` must land before those phases are armed**, because arming them over a
substrate that cannot tell mine from base is precisely how D4 shipped.

**The port map cannot answer a single one of the nine today**, measured on the committed baseline: one
base's map has 19,219 rows and carries **no class-vs-object row** (a type that emits as an `object` is
recorded with kind `class`), **no primary-constructor row** (828 constructor rows, one per *Java*
constructor, with nothing saying which became the primary), **no visibility**, and **zero of that
port's 827 member renames** — the renames exist only in `decisions.tsv`, which nothing discovers and
nothing consumes.

**Extending the map, rather than adding an artifact,** because the map's declared question is *"the
correspondence between the upstream Java surface and what this port actually emitted"* — and emitted
form, emitted primary signature and emitted member name are answers to **that** question. A second
artifact would duplicate discovery keyed on the header's `module=`, freshness over sources digest plus
engine fingerprint, own-map exclusion, the both-namespaces split and schema-major refusal: five places
for two files to disagree about one base, which is D6.5's failure shape at a fourth artifact, plus a
fifth committed baseline per port. Row count is unchanged, the payload is sparse, and a schema-major
bump makes an old map degrade **per question** rather than wholesale.

**Both namespaces (§4.56), and the split is not symmetric.** The `upstream` column stays the
manifest-shaped upstream name because it is the join key; **every name inside `shape` is an EMITTED
name**, because every consumer of it compares against emitted text — a reference, a `super[X]`, an
`export` selector, a stack frame. `PortMap.of` is the one point where both are in scope, exactly as
D6.5 requires.

| row | keys |
|---|---|
| type | `form` (class/object/trait/annotation/enum-class), `companion`, `statics` (emitted names), `primary`, `primaryKind`, `primaryVis`, `disambiguator`, `secondaries`, `tparams`, `parents`, `flags`, `vis` |
| member | `name` (emitted simple name, when it differs), `vis`, `placement` (class vs companion), `promotedParam` |

`primary=` carries the primary's slots **in §8.1's descriptor grammar** — the same source-level
spelling a manifest key uses, so a contract row and a policy key are never in two grammars — and
`disambiguator=marker|none` says whether §8.2 added a final companion-private marker parameter,
**without naming the marker type**, which is companion-private and therefore not a name a consumer may
resolve. A synthetic member is engine-minted and is not a `MemberIndex` entry, which is why §8.1 gives
its descriptor an engine-side derivation and gives `PolicyBinder` a `SyntheticTarget` refusal.

Deliberately **not** carried: **policy** (drops, renames and scopes reach a dependent through manifest
inheritance and `ManifestAgreement`; duplicating them here is the second source of truth §5.7
refuses), **bodies or trees** (a dependent never needs to re-derive the base's implementation, and if
it thinks it does the honest outcome is M6), and **per-site data** (`srcmap.tsv` stays the one file
that answers that).

**Freshness must cover POLICY, or the contract is `Fresh` and WRONG.** `PortMap.freshness` (§5.4)
compares two things — the engine fingerprint and a digest over the base's Java files — and neither of
them moves when the base's **manifest** changes. Schema 3's `shape` payload is full of policy
outcomes: an emitted member `name` is §8.5's property pairs read from the base manifest, a `form` is a
drop or a collapse, a `vis` is one rename entry away from a different qualifier. So editing one entry
in the base manifest and re-running the dependent alone yields a map whose every source digest still
matches and whose payload is stale — **D4's signature failure, a run that reports clean while the
emitted text is wrong, re-entering through the artifact built to prevent it.** The header therefore
carries a **third** fingerprint, the base's **sorted `SurfacePolicy` fingerprint** — the same value
`ManifestAgreement` already compares, not a new derivation — and freshness gains a third comparison
against the fingerprint the dependent computes from its **INHERITED** manifest, which §1.5 guarantees
it holds as a value without loading the base's build. A mismatch is `BaseMapStale`, and by the rule
below that is **fatal wherever the stale answer shaped emitted text**. The field lands in the SAME
schema bump as the `shape` column: a schema that changes twice regenerates every committed baseline
twice, for one design that was known at the first bump.

**Enforcement is by PREVENTION, and a drift check is rejected on evidence.** D4's own write-up records
that **nothing in the dependent's run disagrees with itself** — `ManifestAgreement` reports 0, the port
map records the type as `Ported`, which it is, and the disagreement is visible only when the two
modules are compiled together. A check would have to hold both the recomputed answer *and* the
published one, at which point it is already reading the contract and the only remaining question is
whether it reads *before* or *after* emitting the wrong text. And detection is per-fact, so it
**accrues**: D2 → D4 → D5 → D6 → D6.5 → D8 is six patches to six faces of one defect, with the three
new findings above waiting to become a seventh, eighth and ninth.

`Surface` lives in `api`, because a §1(c) rule must be able to ask it (§3.2's criterion). `owns` is the
**one** structural climb — owner chain to a program unit *and* membership of the run's own units — and
its failure direction is **named once and specified**: exhausting the fuel counts as *not owned*, so
the run asks the contract and gets an honest `Unknown` rather than silently computing. The
whole-program indexes move behind it: the constructor plan table and the emitter's parent, static,
extended-type and named-elsewhere indexes are built over `surface.ownedUnits`, and every question about
a symbol outside them goes through the view. **Honest about the guarantee**: a future phase can still
write `program.units.foreach` — Scala has no capability type here — but the *answers it would need
about a base type* are obtainable only from `Surface`, which is the same lever `RuleScope` and
`FlowPropagation` already use, and as close to structural refusal as the language gives.

**`BaseMapStale` and `BaseMapMissing` become fatal where they shaped emitted text.** Today they are
loud-but-non-fatal findings that fall back to re-derivation, and falling back is exactly how D4
produced three errors while every check reported clean. The **empty base manifest stays the escape
hatch** for a resolution root that is genuinely not a ported module — that is a *statement* a port
makes, not staleness, and it remains exempt while the run still says so loudly. An `Unknown` no
emission consumed is a finding; an `Unknown` whose answer shaped emitted text fails the run, naming
the base module, the type, and which of §1's three kinds the fix is.

**The honest scope statement, stated up front rather than discovered later.** *"A dependent answers
every non-owned question from the contract"* is **not achievable for all nine**, and the design says so
rather than pretending. Three of them — a base type collapsed to an `object` and named as a type, a
base primary a dependent's subclass cannot satisfy, a base `private` member a replay needs — have **no
local repair**, because the base is already emitted and gone. For those the contract buys
**attribution and refuse-and-count**: a bare typer error becomes a finding naming the module that must
change and the §1 kind of the fix. That is a smaller claim than "answer from it", and §4.45 measures a
check by exactly that difference.

**Rejected.** A new `surface.tsv`; extending `EnginePin` (one line per build asserting engine identity
— wrong granularity, wrong lifecycle); **parsing the base's emitted Scala** (a second frontend for a
language the engine only writes, when §1.5's rule is that a dependent resolves against the base's
*Java* and the map exists precisely so it need not read the output); a drift check; **making the base
emit defensively** — never collapse, never promote, widen every private — which was measured worse three
times (refusing the promotion 0→41, the targeted refusal 0→35, the de-collapse moving 36 members
across 29 of 31 constant holders, the un-withheld promotion +14); passing the base's `Program` to the
dependent (the base's run is over, and the dependent's phases would re-decide over a program that is
not the one the base emitted from — the same drift with more machinery); sectioning the contract into
the dependent's own artifacts (D2's rule: **withhold, do not section** — a clearly-marked second
section is still read past and still makes "what did this port do" a question with two answers); and a
`contract = <path>` conf key, which is a second way to name a base that `ManifestAgreement` cannot
check against the declared base chain.

**One publication gap, named because §4.45's consumer hits it first.** The report root keys on this
repository's own run tree, and an agent in another repository pointing the published engine at a
library has no such tree. The addition is an explicit `baseReports` search path; the map-consuming
phase, which reads maps at **construction** time, must take the same path or two loads of one file
disagree within a run.

**Replaces and unifies.** D4 and D5 both become lookups (the plan table's fixpoint runs over owned
units only; a non-owned **wall** class is seeded from `primary=`, while a non-owned **non-wall** class
is derived locally *and* cross-checked against the row where one exists — §8.11 pins that as one
implementation, not two; a replay consults `vis` and refuses + counts). D2's six ownership climbs collapse to one with a specified failure direction. D6, D6.5, D8 and
D1 are kept unchanged, with the contract *adding* D6's cross-module face as a finding. §5.5's recorded
hole — *member signatures are not compared* — closes for everything the `sig` row reaches.

#### 8.3 AS BUILT — what landed, and the four places it differs from the design above

Schema 3 ships: `port-map.tsv` carries a ninth column, `shape`, and a sixth header field, `policy=`.
`Surface` and `Answer` live in `api`; `TrivialSurface` (every unit is mine) is the default every
non-port caller gets and `PublishedSurface` (`engine/core`) is what a run builds. Two reads are
migrated — the constructor plan and the class-vs-object question — and the fatal enforcement is live.
A sample row, verbatim from libGDX core's map:

```
type	com.badlogic.gdx.assets.loaders.ShaderProgramLoader	sge.assets.loaders.ShaderProgramLoader	Renamed	-		0		companion=yes disambiguator=marker form=class parents=sge.assets.loaders.AsynchronousAssetLoader primary=(FileHandleResolver,String,String,?) primaryKind=synthesised-primary primaryVis=protected secondaries=(FileHandleResolver);(FileHandleResolver,String,String) statics=ShaderProgramParameter
```

**`Answer.Own` carries NO value**, where §8.3 above sketched `Own(a: A)`. An owned type's shape is
what this run emits, and most of it — the class-vs-object collapse above all — does not exist until
the emitter takes the branch that decides it. A view that carried the value would have to pre-compute
every answer from the same indexes the emitter reads, before emission: a second derivation of exactly
the thing the view exists to stop being derived twice, and one free to disagree with what was
written. `Own` says *"you emit this declaration — your own derivation is the answer"*, which is both
the truth and the only shape that cannot drift.

**`promotedParam` is NOT carried, and the reason is structural.** A member row exists for every
emitted DECLARATION, and a promoted constructor parameter has none: it *is* the class's parameter
list, so the source map records no row for it and there is nothing for the key to hang on. `primary=`
answers the constructor question; the §4.55 clash question stays open until the map carries rows for
engine-minted members. `Surface.NotCarried` names it in code, because a key silently absent from a
schema reads as an oversight.

**A §4.55 member rename needed no `upstream`-column repair**, which was built and then deleted as
inert (§3.10: a gate never observed open cannot be told from a deleted feature). The renaming passes
rewrite `Symbol.name` and not `Symbol.fullName`, which is a stored field — so the member key the
source map records already spells Java's name (`…FileHandle#file`, never `#file$field`) and the join
key was right by construction. The EMITTED name was the half no artifact carried, and it is now
`shape`'s `name=` on 237 of libGDX core's rows. **That invariant is now a GATE**
(`MemberClashPlacementSpec`), because it is not an accident nobody could disturb: `MemberRenamer` —
the policy-driven rename one layer up — rewrites `fullName` as well, so the shape is already in the
codebase, and a §4.55 pass doing the same would move the join key under the source map, the port
map, the contract's member rows and `dropMethods` at once, with no count moving and nothing failing
to compile.

**…and the MEMBER lookup could not find a method row at all — corrected.** `PublishedSurface`
looked a member up by `Symbol.fullName`, `owner#name`, while a published member row is keyed the way
the SOURCE MAP keys one, `owner#name(params)` — `Symbol.descriptor` is a separate field and
deliberately never folded into the name (§8.1). So it matched every FIELD row and no method row, and
answered `Unknown` for exactly the questions `ENGINE-LIMITS.md` D5 needs it for, from the day it was
written; nothing read `memberShape` yet, so nothing failed. The repair does NOT re-spell the
emitter's parameter grammar in a second place — that is the drift this view exists to stop. It groups
the rows by `owner#name`, the OVERLOAD SET, which is what a symbol honestly identifies here, and
answers `Unknown` where the overloads publish different shapes rather than picking one.

**Type rows now cover NESTED types** (libGDX core: 605 units → 983 type rows). Not a tidy-up: a
dependent extends a base's nested class as readily as its top-level one, and a contract covering only
units answers `Unknown` for precisely the constructor questions this section exists for.

**An ENUM is excluded from the constructor cross-check**, structurally. `TirEmitter.enumDef` lowers a
Java enum directly — its primary IS the Java constructor, because every CONSTANT passes its
arguments to it, in either shape (`ENGINE-LIMITS.md` T21) — and consults the funnel for nothing, so
the funnel's plan for an enum is
`Plan.none` while the contract row records the constructor's real slots. Comparing them compares two
derivations. Measured before the exclusion: **5 FATAL cross-check failures on one dependent, every
one an enum and every one a false alarm.**

**What the wall costs, measured.** gdx-gltf's two remaining wall-class constructor errors
(`PBRCubemapAttribute`, `PBRTextureAttribute`) **do not resolve with the row seeded, and cannot**:
the base's row says `CubemapAttribute` has `primary=(long) primaryKind=unique-root` and lists
`(long,TextureDescriptor)` / `(long,Cubemap)` among its `secondaries`, while both of the dependent
subclass's roots call exactly those two. A Scala `extends` clause can reach only the PRIMARY, and
§8.2's synthesis is inadmissible because the roots reach *different* parent constructors. The row
therefore **confirms the wall rather than removing it** — which is the honest-scope statement above
arriving at a real site: for this class the contract buys attribution, and the repair it would need
is a counted refusal (M6/C3), not a seeded plan. Errors 7 → 7; the nine member digests that moved are
those two classes and their constructors, now consistent with what the base published rather than
with a demoted re-derivation.

**Two more reads are migrated: §4.55's field-rename passes and the `export` exclusion lists.**
Both are whole-program indexes and both were wrong only across a module boundary. A field is renamed
`x$field` iff this class OR ANY DESCENDANT declares a method of that name — and a dependent's program
has EXTRA descendants the base never saw, so a dependent subclass declaring `def x()` renamed the
BASE's field and every reference the dependent emitted spelled a name the base never wrote. The
base's `name=` is now FOLLOWED instead: a base that renamed the field hands the dependent that name,
one that did not hands it nothing.

**…and "hands it nothing" is not "there is nothing to do" — that branch was SILENT.** A published row
with no `name=` settles ONE HALF of the pair: the field keeps java's name and this run may not move
it. The clash the dependent saw is still there, and it is one only this run has — the instance clash
is decided against this class *and every descendant*, so a descendant the base ALSO had would have
made the base's own run see it and publish a `name=`. Withheld, the dependent emitted `def x()` under
an inherited `var x`: the same erased signature, which cannot compile, with zero findings and nothing
in the run disagreeing with itself. So the other half moves — **the DEPENDENT'S OWN METHOD**, renamed
`x$method`, which is this module's declaration to move and nobody else's. The rename runs through
`OverrideGraph.closureOf`, so all of a component moves or none of it does, and a component that
reaches an unparsed parent or a resolution root's declaration is REFUSED and recorded as a
`Surface.Gap` naming the base — a method implementing a base interface genuinely has no local repair,
and refuse-and-count is what the contract buys there. It rewrites `Symbol.name` and **not**
`fullName`, like every other §4.55 pass, so the member key four artifacts join on does not move
(`MemberClashPlacementSpec` is the gate); and the method renames are kept in a table of their own,
because the field map also drives the `private`/`protected` strip and a method renamed for the
field's sake must not be widened for it. **0 corpus sites**, which is exactly why it is a
construction-time restriction and not a check — a check would have reported zero for as long as anybody looked, and
`BaseSurfaceSpec` builds the shape that has none. The `export` lists read `statics=` and `companion=`
for the same reason from the other end: what `export P.{… => _, *}` may exclude is the set of names
the parent's companion ACTUALLY DELIVERS, and a static the base renamed or dropped exists only in the
base's emitted output. `export P.*` against a type whose companion the base did not emit is an error
outright.

**A member the ENGINE REFUSED is a `Dropped` MEMBER ROW — `secondaries` was write-only for it.**
The §8.3 table above gives the type row a `secondaries` key and nothing that says why one is
*missing*. `ENGINE-LIMITS.md` C11's nilary constructor is exactly that: `TirEmitter.secondariesOf`
SUBTRACTS it, so a dependent reads `primary=() primaryKind=not-funnelled` with no `()` among the
secondaries — which is character-for-character what a benign class with one constructor publishes. An
absence is not a disposition, and `new C()` in a dependent therefore compiled into the wrong answer
(§4.4's shape: valid Scala meaning something else) with nothing counting it. The refusal is now a
`member` row with `Disposition.Dropped` and a `shape` carrying one key, `refusal=` — the ENGINE RULE
in the `Reason` grammar the decision log uses — so it lands in the lane `PortMapTransform` already
has for a dropped member's call sites, with no new consumer and no second artifact. `refusal` is what
keeps it apart from a POLICY drop, which is the reader's first question and a different §1 kind: a
policy drop can be asked back, an engine refusal cannot (the base ships it by hand, §1.5's `inject`).

Two consequences that are not optional. The row carries BOTH namespaces where every other `Dropped`
row carries only the upstream one, because here the TYPE IS EMITTED and only the member is missing —
there is a name to grep the base's output for (§4.56). And `PublishedSurface.memberRows` must now
EXCLUDE `Dropped` rows explicitly: it filtered on `emitted.nonEmpty`, which happened to exclude every
policy drop and does not exclude this one, and a shapeless row read into an overload set makes the
set disagree with itself — turning a `Published` answer into `Unknown` for every sibling overload of
that name. A row published to remove a blind spot would have created one, at the members beside it.

**The base's map is discovered over a SEARCH PATH, not one root.** `PortMap.reportRoot` is the
parent of THIS run's report directory, which is exactly right inside this repository and useless to
§4.45's consumer: an agent in another repository has no tree of that shape, and its base's map comes
from wherever that base was run. **`PortManifest.baseReports` is where a port states it**, beside the
`bases` it is about — not `balticporter.baseReports`, which survives as the fallback for a tool with
no port configuration (`DebugEmit`) and is IGNORED wherever a port has stated one. The reason is
§4.6's `reportPathRoot` lesson at an input that shapes the OUTPUT: a base's map decides the
constructor plan, the class-vs-object question, §4.55's field names and the `export` lists, so which
maps a run discovers is part of that run's identity, and left to a flag a leftover `debug.properties`
entry makes two checkouts at the same commit emit differently with every count identical.
`PortMap.searchPath` CHOOSES between the two rather than merging them — an extra root can only ADD a
base, so merging would leave that failure in place for every port that had stated its own. The
`.conf` spelling is a top-level `baseReports = […]` beside `base`, and `PortConfig` anchors it before
building the `surface` entries, because a `{ transform = "port-map-migration" }` entry loads its maps
at CONSTRUCTION time and a `TransformFactory` takes nothing but its own `ConfigView` — the same
publish-through-the-one-accessor move `PortRun.anchorReportPaths` makes, and the alternative (a
`reports = […]` key on the factory) would be a second home for one value. BOTH readers
take the same function (`PortMap.discoverIn`) — `PortRun` builds the `Surface` from it and
`PortMapTransform` resolves its own base through `published` — because two loads of one artifact
answering differently is D6.5's failure shape and this view exists to remove it. An extra root can
only ADD a base: first wins per module, so the run's own tree cannot be shadowed by a stale copy.

**A NON-FATAL contract gap is now a CHECK, `base-surface`.** The fatal half fails the run — §8.3's
enforcement, and deliberately not a check ("a drift check is rejected on evidence"). The other half is
specified as a FINDING and was a line of stdout: an `Unknown` no emission consumed. A number nobody
persists is a number nobody diffs, and a base-surface question is exactly the kind that starts
appearing with no other count moving. It is `RequiredChecks` on EVERY port, a base with no declared
base included, because a run that asked nothing and a run whose recording was skipped are
indistinguishable without the row; and it is recorded BEFORE the fatal refusal, so the run that dies
still leaves the artifact naming what killed it. `kind` splits the two halves (`unanswered` /
`shaped emitted text`) and the §1 classification rides in `detail`, for an agent holding only
`findings.tsv` (§4.45).

**The determinism twin is an emitter over the same program and must be handed the SAME `Surface`.**
The view is an INPUT to emission — it scopes the funnel's fixpoint — so a twin built without it
re-derives every base class's primary the pre-§8.3 way. That is not a hypothetical: it fired on the
first dependent run, on exactly the two units this item fixes, and the determinism gate is what
caught it.

### 8.4 Globals → context — replace the core, keep the shell

**Decision.** The existing globals transform's **closure and boundary handling are replaced**; its
forwarding insight, provenance shape, factory contract and traversal-based rewrite pattern are kept.
The policy is a list of **holders**, each with a **path-valued member map** onto an injected or minted
context type, an attachment mode, a read shape, and per-site boundary policies.

**What survives is worth naming, because it is why the mechanism scales.** A call into a threaded
method changes **nothing at the call site** — the argument arrives from the `using` in scope. That is
what makes "no decision row per call site" a derivation rather than a shortcut, across 562 measured
read sites.

**Two live SILENT mistranslations are the hazard being closed**, and they are the reason this is a
replacement rather than an extension:

- A `static { }` block is a synthetic class-initialiser `DefDef` with a `MethodType`, so it passes the
  is-a-method test, **seeds**, and receives a `using` parameter — and the emitter inlines only its
  *body* into the companion, dropping the parameter and leaving the context identifier unresolved.
- A **field initializer's** read is enclosed by the *field* symbol, which fails the is-a-method seed
  test, and the rewrite visits only `DefDef` arms — so the initializer still names a member that is no
  longer static. **Broken emitted code, zero decisions, zero findings.**

The rest of the disqualifying list is structural: one holder, first match wins; the holder *is* the
context, so a separate context type with hand-written ergonomics and a member map is inexpressible;
and the closure is the direct call graph and nothing else, which is unsound in **both directions at
once** because Java resolved every virtual call to the *declared* member — threading an implementation
breaks `override` against the unthreaded declaration, and callers through the interface never join the
closure at all.

**The closure is a directed reachability over five edge kinds, not a call graph.** Seed (a body reads a
mapped static — read from the phase's **own record** of what it mapped, per §4.56's *a phase may only
conclude from what it did*); call; **override component, both directions**; instantiate (class
attachment only); and **capture** — a lambda or anonymous/local class body imposes the need on the
**enclosing** declaration rather than threading its own signature-frozen method, which is what makes
external-interface SAMs a non-problem in the common case. Rejected reusing `FlowPropagation`: it is a
union-find over **symmetric** pure-move edges (*these two must share a type*), and this is **directed
need** (*this one must be able to supply that one*); bending one into the other either over-unions or
requires the dedicated walk anyway. What *is* reused is its shape — `edges(program)` exposed separately
from the growth, so a spec can pin the edge set itself.

**Over-approximation across an override component is benign and priced.** Threading a component member
that never reads the holder costs one trailing anonymous clause at the declaration, **nothing at call
sites**, one extra reference argument at run time, and an API-surface cost that the closure itself
counts. The unsound alternative — thread only the overrides that read — breaks `override` matching:
100+ compile errors, unattributed. So: over-approximate, note each with `via=override-component`
naming the member that seeded it, and count the total.

**The read shape is an anonymous `(using T)` plus a summon, and this is forced by measured evidence.**
The reference port repaired two files *away* from named context parameters, with the reason recorded:
a parameter named after the renamed root package **shadows** it and breaks every qualified reference in
scope — and this engine emits **only** fully-qualified names (§2.5), so every reference in scope is
qualified. Nothing reads the name (`using` resolution and `summon` never do), so anonymity costs
nothing; **98.2 % of the reference port's 557 context reads** are the inline companion-summon idiom.

**The member map is PATH-valued, not a member rename**, because the reference port's dominant rewrite
is two-hop: 6 of 11 holder fields became bundle members and the 5 remaining statics were **re-homed
onto a service**, which is **305 of 557 reads (56 %)**. The same shape answers the write problem: the
bundle stays an **immutable** case class and the mutability lives on the service, so a global rebinding
— 10 writes, all in one profiling class, symmetric enable/disable — write-throughs along the mapped
path when it ends on a `var` or a setter. A path ending on a `val` makes the write site a per-site
policy decision; **the mechanism never mints mutability the mapped type does not declare.**

**The ambient default `given` is DELETED, and that is the load-bearing reversal.** With
`given T = new T` in scope, every unthreaded→threaded seam compiles silently and the global is
reintroduced with extra steps. Without it, an unthreaded owned caller of a threaded callee is
**impossible by construction** — the closure would have threaded it — except across a refused boundary,
and those sites are exactly the seams. **`ContextSeamCheck`**, modelled on the collection boundary
check (a number with an origin and a §1 classification, gated on the artifact layer, filtered to the
run's own units), counts four seam kinds: `residual-global-read`, `deferred-init`, `captured-context`,
`frozen-component`. A port that enables the phase **sees its boundary, sized, before any compile.**

**Eager→lazy is a REPORTED semantic change, never a default.** Java runs a class initialiser at first
active use; a `lazy val` runs at first *read of that field*. Per-site opt-in, decision row, note, seam
count. The corpus demand is **exactly one true class-initialiser site in nine libraries, and it is in a
dependent** — which is itself the argument for per-site policy over a mode.

**Attachment has two modes and only one of them has a dependency.** `method` — a trailing anonymous
clause per threaded method — is correct for statics and for the whole demand when class attachment is
off. `class` — the clause on the **primary constructor**, so instance methods summon it with no
signature change and external-anchored overrides become a non-problem — is the reference port's shape:
**82 % of its 493 attachment sites are constructors, and 49 % are SECONDARY constructors**, so the
clause must land on every funnelled secondary too. That rides on the constructor plan, which is why
class mode landed after §8.2 — and the dependency turned out to be exactly one distinction inside the
plan: java's parameter list is ONE list and Scala's is a list of GROUPS, so the plan carries the
context clause apart from the value parameters (`CtorFunnel.Plan.givens`) and every "is this
constructor nilary" question in the funnel reads the value parameters alone. Both modes emit and
compile; the enablement (a port declaring a holder) is still sequenced last.

**Measured facts that bound the risk.** 562 code reads in 97 of 605 base files; **62 of the reference
port's 159 attachment files carry the clause purely to PROPAGATE**, so expect the closure to touch
roughly 1.6× the direct-reader file set; **zero** interface-default-method reads anywhere; and **the
test lane cannot break** — upstream test sources contain zero holder references and the emitted test
set contains zero, so the behavioural gate survives unless a threaded signature reaches a tested
member, which `members.tsv` reports **before any compile**. One dependent has zero holder references at
all and must show **0 members changed**, which is itself a gate that the phase respects D2.

**What building it settled.** Nine things the design left open or got slightly wrong, each fixed in
the mechanism commit rather than left for the enablement:

- **The anonymous clause needed the EMITTER, not the phase.** Every parameter the emitter renders is
  `name: Type`, so the shape this section specifies — `(using T)` with no name — was unemittable. A
  `using` parameter whose symbol has an EMPTY NAME now renders anonymously, and an empty name is
  otherwise impossible (the frontend gives every parameter Java's own), so the rule cannot capture a
  real one. Without this the phase would have had to mint a name, which is the thing the section
  rejects.
- **The CAPTURE edge is not in the owner chain, and reading it from there is wrong in two ways.**
  The frontend interns an anonymous class with its enclosing **class** as owner — that is where its
  emitted name comes from (`Outer$1`) — so a climb reaches the class and loses the method. Measured
  on the fixture: the capture landed on `Listeners`, which under `attach = method` is a boundary, so
  the read stayed global AND the anonymous method was offered to the closure as an ordinary method
  (where its external `Runnable` anchor then froze it). The lexical home is in the xref: every
  `new T(){ … }` is an `Instantiate` usage of `T` whose SITE is the `New` node carrying the body and
  whose `enclosing` is the declaration it was written in. Read from there; nothing re-walks the tree
  with its own notion of *where am I* (§3).
- **A member of an anonymous body is reached from the MEMBER, so the climb looks up one level before
  it decides.** The same defect from the other side: `isType(s)` is false for the anon's `run`, so a
  climb that only tests the symbol it was handed treats it as an ordinary method.
- **`mint` is a bag of mutable `var`s, and a two-hop path with a mint is REFUSED at bind time.** The
  minted type is the holder's own shape moved onto an instance, so a consumer's bootstrap sets its
  members where it used to set the statics. It cannot express the reference port's immutable case
  class with `@implicitNotFound` and accessor sugar, and it does not try: that is what `inject` is
  for. It also has no intermediate type to hang a second hop off, which makes `gl = "graphics.gl20"`
  a malformed entry rather than a silent miss.
- **`lazy-init` is a CACHE PAIR, not a `lazy val`.** A `lazy val` initialiser has no parameter list,
  which is precisely the problem being solved, and a null-sentinel cache would re-run forever for a
  primitive-typed static. So: `private var f$set` / `private var f$value` and a `def f(using T)` that
  reuses the FIELD'S OWN SYMBOL — every read in the program keeps naming it and no call site changes.
  What it does not reproduce is the JVM's class-initialisation LOCK, and the decision row says so.
- **A porter note renders the §1 classification's pairs AND the decision's detail**, so a decision
  that repeats the policy key in its `detail` prints `key=` twice in one comment. Only a kind in
  `PorterNote.Rendered` can show it, which is why it survived unnoticed in the predecessor.
- **A `DroppedMember` decision's SUBJECT is the owning TYPE**, not the member: `PorterNote.InBody`
  puts the note at the head of the type's body, and the emitter looks that up by the type's symbol.
  A note keyed on the member's own symbol never appears, and `NoteCoverageCheck` cannot see it either
  (the member is not emitted, so it is out of scope by construction).
- **The INSTANTIATE edge is read off the NODE, not off `UsageKind` — and the `lazy-init` trigger is
  the POLICY, not a read.** Both are `ENGINE-LIMITS.md` CT6, and both were the closure believing a
  label. `Xref.walkType`'s `AppliedType` arm REPLACES the kind it was called with, so
  `walkType(tpt.tpe, Instantiate, n)` at a `Tree.New` labels a GENERIC class `Tycon` — parameterised
  and raw alike — and this edge was therefore absent for every generic class: no threading, no
  `impose`, and so no SEAM, which is a boundary the engine cannot see rather than one it refuses. It
  also left an anonymous subclass of a generic parent with no lexical home, which is the capture
  defect above reappearing for generics. `ContextNeed.instantiates` asks *what does this `New`
  construct* (the head of `tpt.tpe`, application stripped) and `anonHome` asks *which body does it
  carry*; both are structural facts about the node the phase is holding (§4.56). Asking the node is
  also EXACT where a kind-blind widening is not — `new Pool<Cell>()` names `Cell` at that node as a
  TYPE ARGUMENT, and constructing a `Pool` constructs no `Cell`. The fix is deliberately NOT in
  `Xref`: `UsageKind` is a shared index read by three other consumers.
  The second face is the same mistake in the escape hatch. Every seam this phase draws tells its
  reader to *give the site a `sites` policy*, and the shape that most needs one — a static
  initialiser that CONSTRUCTS a now-threaded type — reads no mapped static, so a trigger derived
  from reads could not name it. The candidates are the bound entries themselves; a static field
  carrying its own initialiser is deferrable (there is no `<clinit>` to strip); `climb` sees a
  deferred field as the `def` the rewrite made it, or the seam is reported against the exit just
  taken; and `readsHolder` widens to *reads a mapped static OR constructs a type this program
  declares*, which is an over-approximation the per-site opt-in pays for and which cannot be
  sharpened here, since the plan is read before the growth that would answer it.
  **And a bound entry that selects zero sites REPORTS** — `bindMembers` asks whether the member
  exists, which a real field answers whether or not the phase reaches it, so the binder's never-fired
  machinery is blind to exactly this. Two such keys were measured on a real port: both bound, both
  dead, output byte-identical, `policy` at its floor.

- **ATTACHMENT HAS A THIRD ANSWER, and it had to, because a class a FRAMEWORK instantiates has no
  caller to change.** `ENGINE-LIMITS.md` CT7, and the first thing in this section that a compile
  could not see at all. The closure reasons from the program: it may add a parameter because it can
  see, and fix, every `new`. A test suite, a `ServiceLoader` implementation and a bean are
  constructed reflectively from OUTSIDE, so the closure sees no instantiation, concludes correctly
  and uselessly that nothing has to be fixed, and the clause lands — emitting a class that compiles
  perfectly and cannot be built at run time. Measured on the first port to thread a constructor: 0
  scalac errors, `context-seam` 0, `policy` 0, and a whole suite gone.
  So `ContextHolder.selfSupplied` names such a type and the expression that yields its context, and
  the type takes the value WITHOUT taking a parameter: `private given <ctx> = <expression>` at the
  head of its body — the reference hand port's own shape (`private given Sge =
  SgeTestFixture.testSge()` on a no-arg suite class), reached from policy. It is a RESOLUTION and
  not a refusal, so the reads inside it stay threaded reads (the plan asks `supplies`, not
  `classes`) and no global comes back; and it propagates nothing, because its constructors are
  java's and there is nothing for a `new` to supply. The expression is `Tree.Opaque`, in the EMITTED
  namespace, uncheckable here and checked by the target compiler at one attributable line — the same
  contract `MethodBodyTransform`'s bodies have.
  **WHICH declarations those are is not derivable and the SHAPE is**, which is why the warning ships
  beside the policy rather than the policy alone: `context-seam`'s `unconstructed-thread` is a
  threaded class nothing in this program constructs (nor any owned descendant of it) whose ancestry
  leaves the program, `java.lang.Object` excluded because it is every class's parent. It WARNS
  rather than refuses on evidence rather than caution: from inside the program, a class a framework
  constructs and a class this library's USERS construct are indistinguishable, and refusing would
  make the second unportable while silence made the first invisible.

- **A `given` MEMBER is an emitter capability too, and it is CT3's empty-name rule one node over.**
  A `ValDef` whose symbol is `isGiven` and whose NAME IS EMPTY renders as an anonymous
  `given <T> = <rhs>`, `private` when the flag says so. A name minted into a class body can shadow
  an emitted root package exactly as a named context parameter can, and nothing reads a given's
  name; an empty name is otherwise impossible, since the frontend gives every declaration java's own.

**`attach = "class"` NOW EMITS, and the refusal it carried is a worked example of where such a
refusal belongs.** For one release the TIR edit was complete and the emission was not — the
constructor funnel undid it three ways, 5 scalac errors (`ENGINE-LIMITS.md` CT4), all three inside
the region §8.2 owns — so the knob RECORDED a `PolicyIssue.Unverifiable` finding naming all three
rather than emitting code that does not compile. That was the right place for the fix and the right
place for the finding: a clause the funnel will not carry is not a clause, so every workaround in
THIS phase would have been a second constructor plan. The funnel now models parameter GROUPS
(`CtorFunnel.Plan.givens`), reads java's VALUE parameters for every "is this constructor nilary"
question (`CtorFunnel.valueParams`), and renders the clause through the emitter's `paramClause`;
this phase gained no code at all, which is the evidence that the refusal was pointing at the right
module. The dry run that sized the deferral reproduces unchanged: class attachment threads **275
declarations in 177 files with 17 seams** against method attachment's **2,497 in 324 with 162**, and
`frozen-component` refusals go **32 → 0** because class mode changes no method signature at all. 177
against 97 direct-reader files is **1.8×**, which is the reference hand port's measured 1.6×
reproduced; method mode's 3.3× is not.

**One deliberate simplification worth stating.** An UNSUPPLIABLE USE — a declaration that cannot take
a clause calling one that now requires it — is counted as `residual-global-read` rather than as a
fifth seam kind. It is the same fact from the reader's side (this site still needs the global) and the
detail names it precisely; a fifth kind would split one count in two without changing what anyone does
about it.

**Rejected.** Extending the existing transform condition by condition — *the core relation it computes
is the wrong relation*, and every fix would be a patch on a foundation that cannot carry an override or
a constructor. The ambient given. A named context parameter (measured twice in the reference port). A
hand-maintained list of threaded methods as config (§5.1's *derived, not listed*). Context function
types as a general emission shape — no Java analogue and it changes every functional interface's
emitted shape; it is right at the **consumer's** entry point, which is hand-written bootstrap and not
emitted code. De-statifying the holder in place as the only mode — subsumed: that is `mint` pointed at
the holder's own FQN with an identity member map.

### 8.5 `OverrideGraph`, `MemberRenamer`, and the property transform

**Decision.** Two layers, both in `api`. **`OverrideGraph`** answers member-level correspondence across
a hierarchy — `parentsOf`, `childrenOf`, `overridden`, and `closureOf` returning owned members plus
`externalAnchors` plus `baseAnchors`. **`MemberRenamer`** expands rename requests through those
closures, refuses anchored ones, checks the new name against **effective names parents-first**, records
one decision per renamed declaration and applies **one** symbol-table rewrite. Four consumers: the
property transform, the type-redirect member renames, §8.4's using-threading, and `member-rename`.

**`member-rename` is the FOURTH, and it exists because the other three never let a PORT choose a
name.** Every gap the list below names — override edges, external-anchor refusal, the
`Reason.Configured` path — was closed *for* a policy rename, and until it shipped the only policy
caller was the type-redirect one, whose new name is not chosen at all: it is **dictated** by the
redirect target. That leaves one ordinary configuration with no spelling. A base redirects
`Disposable -> java.lang.AutoCloseable` renaming `dispose -> close`; a dependent declares a class
implementing `Disposable` that already has a `close()` of its own; one emitted class cannot declare
`close()` twice, so `OnCollision.Refuse` refuses the component whole — correctly, because *which of two
members keeps a name* is not the engine's to invent. `AutoCloseable#close` is not negotiable, so the
member that moves is the library's own, and a `MemberRenameTransform(Map("owner#member" -> "newName"))`
is where the port says so. Three rulings distinguish it from the other three consumers:

- **`OnCollision.Refuse`, which is the OPPOSITE of the property transform's `DeferToEmitter`.** A bean
  property's name is forced by java's `getX`; this one is the port's free choice, so a collision has a
  one-edit answer the port already holds, and moving a third member the entry never named would be the
  engine doing exactly what the refusal it is built on declines to do.
- **BASE-ANCHORED, which the type-redirect rename deliberately is not.** That one may move a base's
  declaration because the base itself declared the rename and already performed it; a free-form rename
  has no such agreement, so its graph carries the units this run does not emit as `baseUnits`. Note
  `SurfaceIntrusion` cannot cover this: it screens the KEY's owner, and the key here names the
  dependent's own class whose *component* climbs into the base.
- **its PIPELINE POSITION comes from the merge, not from an edge.** See §8.13's own note.

**The shared core, named precisely: *the set of declarations that must change together, or none of
them*, plus anchored refusal with a counted decision.** Renaming half a component is a silent contract
break; threading half is a broken `override`. §8.4 consumes only the closure/anchor layer and does not
use the renamer at all — it changes signatures, not names — which is exactly why the two layers are
separate types rather than one.

**Reference propagation is free, and the reason is §4.55's exactness argument.** A rename is a
symbol-table rewrite; the emitter renders every reference through the symbol's name, every reference
node carries its `SymId`, and Java resolved all of this **statically**, so each reference already points
at the symbol Java chose.

**Why no such graph exists today.** Four whole-program rename passes run in the emitter's constructor
and each rebuilds its own local structures — parent-edge computation is duplicated at least six times in
one file, and the emitter's own parent index is private and built *after* those passes ran. Where member
correspondence across a hierarchy is needed the emitter matches on **name + arity**, which D1 measured
insufficient (263 findings, 118 ambiguous). So **edges are descriptor-keyed**: D1's erased-descriptor
rule now, §8.1's identity when it lands, with the fallback's misses **reported, never guessed**.

Five gaps in the existing passes that the utility closes, each of which explains why the feature was
never needed before: they **never rename a method** (all four rename fields, locals or parameters —
things that do not override, and the field-vs-method pass renames the *field* precisely to avoid
touching the method's override obligations); no override edges; no external-anchor refusal (they note
that members inherited from unparsed types are invisible and accept it); no arity change; and no
`Reason.Configured` path, because they are Universal by construction and a policy rename is not.

**Anchor policy is deliberately conservative and stated as such**: an unknown external parent with no
surface data **anchors** — refuse, count. *An over-refusal is a counted skip an agent can see; an
under-refusal is a silent contract break.* The external surface comes from the channel the emitter
already threads, not a second one.

**Collision handling delegates rather than re-implements.** The suffix-until-free idiom is §4.55's, and
the emitter's four passes still run afterwards, so a rename that lands a method on a private field's
name is resolved by the *field* moving. The utility refuses only when the collision is with something
those passes will **not** move.

**The DEFAULT property target is `def x` / `def x_=(v: R): Unit`, bodies kept verbatim.** The `var x`
collapse — which is what the reference port actually wrote for most harvested pairs, and which deletes
the `$field` noise — is **opt-in per entry** (`target = "var" | "val"`), because it carries three
silent-correctness obligations: a `var` cannot be overridden (legal only when the closure has no
members *below* the declaring class, though an ABSTRACT one *above* is fine); deleting the accessors
requires that every call provably route through the pair; and any direct field access elsewhere in the
class must be verified equivalent post-collapse. §5's *change one thing* is what makes the two shapes
one phase rather than two: the def-pair blast was measurable alone, and **the collapse degenerates to
the def-pair whenever a guard fails**, so every refusal is a port that keeps working.

**Never invent a member.** An entry naming an accessor that does not exist is a `NeverMatched` finding,
not a synthesis. The audit found the hand port **authored** getters to complete pairs; that is
authoring, permanently outside mechanism scope.

#### The `var`/`val` collapse — BUILT, and what building it settled

**Five things the design below left open or got slightly wrong**, each fixed in the mechanism rather
than left for an enablement:

- **the population is 90, and the census said 93 while this section said 91** — three numbers for one
  question, which is what a census beside a transformer buys. 91 was prose transcribed before two
  pairs stopped being `ComputedBody`; 93 was the census, which OVER-counted by three because it asked
  neither question the transformer asks: **2** of its rows are pairs the def-pair path itself refuses
  (`Guard.PairRefused` — there is no property there to collapse) and **1** is a concrete relative.
  Read `idiom(refused)`'s guard breakdown on the run in front of you; never a sentence, and never a
  second phase's opinion.
- **`overriddenBelow` is only half the override question.** A scala `var` IMPLEMENTS an abstract
  `def x`/`def x_=` and cannot OVERRIDE a concrete one — so a CONCRETE accessor anywhere in the
  component refuses, whichever direction it lies in. `BeanCollapse.Guard.ConcreteRelative`; without
  it a class whose parent has a real `getX()` collapses into a `var` that scalac rejects, and only
  at 0 typer errors (§3).
- **`val` does not turn on java's `final`.** A java field is routinely non-`final` and never
  reassigned (`private MapObjects objects = new MapObjects();`), and refusing on the modifier would
  decline most of the get-only population for a fact about java's syntax. What decides whether a
  `val` COMPILES is that the value is at the declaration and that NOTHING in the program writes it,
  which is a whole-program scan and is what `Guard.MutableStorage` asks. A field the constructor
  fills is refused whatever its modifier says: its keyword is the constructor funnel's answer and
  this phase cannot see the funnel, so guessing it would be §4.6's fabricated fact.
- **`isMutable` on the surviving symbol is the TARGET, never the field's.** The guards are what make
  that sound. Read off the field, a get-only property over an ordinary non-`final` java field emits
  a public `var` and publishes a writer java never had — no error, no moved count.
- **THE EMITTER'S SHADOWING PASS UNDOES THE COLLAPSE, silently, and this is the finding worth
  keeping.** `resolveFieldShadowing` renames a field whose name is an inherited member's, which is
  exact for java (fields shadow, and a java field beside an inherited method is illegal in scala's
  one namespace) and WRONG the moment a field is a property: the emitted `var w` under an interface's
  abstract `def w` is that member's IMPLEMENTATION, and the pass emitted `var w$shadow`, leaving the
  class abstract. Nothing reports it until the port is already at 0 typer errors, because `RefChecks`
  does not run before then (§3) — so it arrives on the day a port goes green, in a member nobody is
  looking at. The fix is a fact about SCALA and universal: a `val`/`var` and a PARAMETERLESS `def` of
  the same name across a subtype edge are an implementation pair, and java cannot produce that shape
  because a java method always has a parameter clause. `ConcreteRelative` above is what makes the
  admission safe, so the two guards compose and neither re-asks the other's question.

#### The design, as written before it was built

The count the deferral was gated on has been taken and the design is here rather than in a scratch
file, because the gate is met: `ported/sge/src_managed/main` carries **280 `$field` DECLARATIONS and
2,998 references** to a name no human wrote, and `ported/ssg-liquid` 13 references. The population
the collapse can actually reach is smaller and is PUBLISHED on every run rather than predicted: the
`BeanCollapse` lane of `idiom(refused)` reports the intersection of *configured* ∩ *trivial body* ∩
*unanchored closure* ∩ *no override below* ∩ *no concrete relative* on the libGDX base, one row per
configured pair, grouped by the guard that declined it. `BeanPropertyTransform` files those rows
itself; the `BeanCollapseCensus` that published them through wave 0 is retired, because a
transformer that files one row per site CONSIDERED already IS the denominator and a census beside it
is a second answer to its own question (§4.6).

**Kind: the SAME §1(b), no new phase, and NO `RuleScope`.** The pairs map already IS the include list
and the "Rejected" list above forbids a second knob beside it. §1(b)'s adds-vs-retypes rule chooses a
DEFAULT for a scope; it does not say every phase has one, and a phase whose policy is a per-key
include list has its no-op in the empty map. A scope beside it would let a pair be listed and then
silently scoped out, which is the exact failure §1(b) exists to prevent.

**How a port asks for it — the pair's VALUE gains an object form,** which is
`TypeRedirectTransform`'s landed precedent read once more (`redirects { "a.B" = "c.D" }` beside
`"a.B" = { to = "c.D", memberRenames { … } }`, read apart by `ConfigView.isObject`, a PROBE that does
not count as a read so the unread-key refusal still catches a misspelling inside an entry):

```hocon
pairs {
  "com.foo.MapLayer#opacity" = "getOpacity/setOpacity"                    # def-pair, unchanged
  "com.foo.MapLayer#name"    = { accessors = "getName/setName", target = "var" }
  "com.foo.Map#properties"   = { accessors = "getProperties",   target = "val" }
}
```

`target` defaults to `def-pair`, so every existing port and every existing config is byte-identical
by construction — §1(b)'s no-op met at the ENTRY level, which is the right granularity for a per-key
policy. The Scala constructor mirrors the two halves the way `TypeRedirectTransform` does (a second
map keyed by the same key), never a second config home.

**`SurfacePolicy`: the fingerprint MUST include the target.** `surfaceFingerprint` renders
`pairs.toList.sorted` today; two configurations that differ only in `target` would compare EQUAL,
which is `ENGINE-LIMITS.md` CT9's recorded failure exactly — `SurfaceMissing` cannot see the
difference and a same-name pair can be neither compared nor composed. One line, easy to miss,
load-bearing. `MergeablePolicy` is NOT owed while no dependent constructs a `bean-properties` phase,
which is a fact about today's ports and is re-asked with one grep before the wave writes policy
(§1.5's instance-count question).

**…AND THE FINGERPRINT IS NOT ENOUGH, BECAUSE THE VERDICT IS DERIVED AND NOT TABULATED.** The two
paragraphs above are the whole of the §1.5 machinery — one instance, one fingerprint, one fold — and
every one of them compares POLICY. That is exact for a phase whose emitted shape is a function of its
constructor arguments. Here the arguments only NOMINATE a pair; whether it becomes a `var` is decided
by `overriddenBelow` over the run's descendants, `concreteRelative` over its override closure,
`writtenSymbols` over its assignments and `closureOf(_).isAnchored` over its parents — all four
whole-program, and a dependent's model CONTAINS its base's units. One subclass overriding the
accessor, or one write of the field, and the dependent re-derives `Refuse` for a declaration the base
COLLAPSED: it emits `def getW()` where the base emitted `var w`, the entry is identical on both sides
so the fingerprints are EQUAL, the phase agrees with itself so `idiom(refused)` reports an honest
guard, every count is flat, and both ports compile alone.

So the shape is PUBLISHED and COMPARED: `Surface.MemberShape.form` carries `var`/`val` on the base's
member row, and `PortRun.collapseDivergence` holds the dependent's derivation against it as a FATAL
`base-surface` gap naming both answers. Three details are load-bearing:

- **the base's answer is STATED, never inferred.** A collapsed pair emits a member row keyed on the
  PROPERTY and no rows for the accessors — and so does a member java simply declared under that name,
  and so does a `dropMethods` entry. Absence is not evidence, and reading it as one would be §4.6's
  fabricated fact;
- **the question is asked only where the base's map has a row for the OWNER TYPE.** §1.5's own rule
  read one level down: ask what the base EMITS, never what its `governs` claim says, or every pair a
  dependent writes about its own types becomes a finding (`ENGINE-LIMITS.md` D10's shape);
- **the run's own answer comes from the phase's IDIOM LOG.** Re-deriving the verdict inside the check
  would be a second answer to the phase's own question, free to disagree with it — `ENGINE-LIMITS.md`
  K2.5 is the measured shape of that, and a FATAL finding is the last place to introduce one.

**THE COLLAPSE RETYPES THE SURVIVING GETTER SYMBOL — it never drops the pair and mints a `var`.** The
two produce the same emitted text and only one of them is visible to anything. `Pipeline.runTraced`
derives what a phase moved by comparing each owned symbol's `info` ACROSS the phase, so a retype is a
`Patch.retyped` row and a drop-plus-mint is not: the dropped symbol has no post-phase info, the
minted one has no pre-phase info, and the pair cancels to nothing (§8.14 says exactly this about the
shape). Mint the `var` and the collapse owes no lane either — `rewrite-callsites` reports 0,
`idiom(residue)` reports 0, and the usages the phase failed to rewrite are invisible to the one
instrument built to find them. It is also what keeps the rest of the run coherent: the surviving
symbol carries the `usagesOf` edges every call-site rewrite reads, the `srcmap` row that attributes
an error to a java line, and the `port-map.tsv` member row a dependent's `base-surface` compares
against. So: **retype the GETTER** (a `MethodType(Nil, R)` becomes `R`), **drop the SETTER** (java's
`setX` has no survivor; the `var`'s `x_=` is scala's own generated member, not a declaration this IR
holds), and drop the backing field only where the `$field` rename gave it a name nothing else
references. Pin the direction with a fixture asserting on `Patch.retyped`, not only on emitted text.

Because it moves an `info`, the phase `extends Rewrite` with `accountedBy = Set(IdiomCheck.Residue)`
and **must not name `policy`**: a declared key that never fired is a different residue from a slot
whose two sides disagree (§8.14).

**The three obligations, each a structural guard with a fixture, none of them approximable** — and
they are the whole safety argument:

1. **a `var` cannot be overridden.** Legal only where the `OverrideGraph.Closure` has no member BELOW
   the declaring class. An interface ABOVE is fine (a scala `var` implements an abstract `def x` and
   `def x_=`); a subclass overriding the accessor is not, and an ANCHORED closure refuses the whole
   component. Whole-or-none, as for every other edit a consumer applies;
2. **deleting the accessors requires that every call provably route through the pair.**
   `XrefIndex.usagesOf` over both accessor symbols, every one of them a call this phase rewrites. A
   `Tree.MethodRef` or a value-position `Select` is already a refusal in the def-pair path and stays
   one;
3. **direct field access elsewhere in the class must be equivalent post-collapse.** The class's own
   body reads `this.x$field`; after the collapse those become `this.x`, which routes through the
   accessor. Equivalent iff the getter's body is exactly `return this.f` and the setter's exactly
   `this.f = v` — the TRIVIAL-BODY test. A computed getter or a side-effecting setter is precisely
   why the def-pair keeps bodies verbatim, and is a refusal here rather than a widening.

**Edge cases the suite must pin**, each an `assertEmits`/`assertDecides` fixture plus a refusal
assertion where the answer is no: a computed getter (REFUSED, `ScopedOut` + `detail`); a validating
or listener-firing setter (REFUSED); getter and setter touching DIFFERENT fields (REFUSED — there is
no single field to become the `var`); a subclass overriding the getter (whole component REFUSED); an
INTERFACE above with no subclass below (COLLAPSES, and the emitted `var x` must satisfy the trait's
abstract `def x` — pin both halves); a `private` field with `public` accessors (the `var`'s
visibility is the ACCESSORS', because the field is the implementation and the pair is the surface —
taking the field's would silently narrow the port's API with a green compile); a `$field` rename the
collapse makes unnecessary (the field's name returns to `x` and the `renamed-member` /
`widened-visibility` notes for it must DISAPPEAR — two notes per collapsed pair, a large share of the
wave's digest blast, and it must be predicted rather than discovered); a java-`final` single-write
field (emit whatever the existing field emission says, and RECORD it as a candidate for
`PROGRESS.md`'s open `val` item — fixing that inside this wave is two changes in one measurement);
and TRIVIA, where the getter's and the setter's Javadoc both lose their declaration, so both go to
the surviving node's `leading` in DECLARATION ORDER and the second is RECOVERED with its coordinates
where they disagree (`trivia(recovered)` will move; `trivia(lost)` may not, and that is a gate).

**Perf is zero and the argument is bytecode-exact**: a scala `var x: T` emits a private field plus
`x()`/`x_$eq()`, which is what the `def x` / `def x_=` pair already emits, which is what java's
`getX()`/`setX()` already emitted — three spellings, one class file. The one runtime-visible delta is
the JVM METHOD NAMES, which matters only to a reflective reader; that is K21 face 2's seam, so **a
port declaring both `PublicFieldAccessorTransform` and a collapse on one type is asking for two
opposite things and must be refused and counted**, with the finding naming both policy keys.

**Wave shape**: (1) the `Target` config/constructor + fingerprint, NO entry using it — gate is 0
digests everywhere and a MOVED `policy=` fingerprint on the ports carrying the phase, accounted;
(2) the mechanism + the three obligations + refusals, spec'd, still no entry; (3) enable on five to
ten hand-read libGDX pairs, measured with `just measure-all` because the base's six dependents ARE
the measurement; (4) widen to the 91 the census publishes. §1.5's machinery is exercised throughout:
`SurfacePolicy` fingerprint, published `port-map.tsv` member rows (the accessors vanish, the property
gains a name), `base-surface` on six dependents.

Refusals, each counted, each with a spec: an accessor overriding an external member — **the whole pair
is skipped**, because a renamed getter with an anchored setter is half a property; a **fluent** setter
returning the declaring type (`o.x = v` is `Unit` and a chain has no assignment rendering); a
**set-only** entry; a value-position accessor reference (an eta-expanded `x_=` is not the SAM Java
saw); a static accessor. The setter rewrite has one detail that is not obvious and is also the
structural reason set-only is refused: the assignment's **left-hand side names the GETTER symbol**, so
the emitter renders `recv.x = v` and scalac desugars to `x_=` — with no getter there is nothing to put
on an LHS.

**The policy is an INCLUDE LIST because blanket application is measured wrong.** The upstream emits
3,234 bean-shaped methods today and the hand port **kept 1,375 of them** (684 distinct names) against
~223 converted accessors — a **~14 % conversion rate**, concentrated in three package families, with the
same pair converted differently per type (one type's `opacity` is a computed `def`, another's is a
`var`). A blanket rule would rewrite ~3,000 members the reference port deliberately left alone. The
include list harvested from the hand port's own documented rename headers — **145 properties across 38
upstream types, ~223 accessor methods, every entry a conversion the hand port actually performed** — is
the enablement's input, not this design's content.

**This phase changes emitted signatures, so it is SHARED SURFACE**: it implements `SurfacePolicy` with a
sorted fingerprint and its pairs live in the **base** manifest, because dependents resolve against the
base's Java and must see the same conversion or the two ports cannot compile together (§1.5).

**What building it settled.** Six things the design left open or got slightly wrong, each fixed in the
mechanism commit rather than left for the enablement:

- **The SHAPE change ranges over the COMPONENT, not over the accessor the entry named.** The rename
  already did — that is the whole point of the closure — but the arity edit and the call-site rewrite
  were written against the named symbol, and a call through an *implementor's* symbol is a different
  `SymId` from a call through the interface's. Measured on the phase's own end-to-end fixture: the
  rename reached three declarations and the arity edit reached one, emitting an interface `def x`
  against an implementor `override def x()`, which do not override each other. The invariant is
  therefore "all of a component or none of it" for EVERY edit a consumer applies, not only for the name
  — and a consumer holding a `Closure` should read that as an instruction.
- **The getter's `info` stays `MethodType(Nil, R)`** (§8.5's open question about a parameterless method
  type). A Scala parameterless `def` IS a method, its descriptor is still the empty one, and every
  arity reader in the engine reads `paramss`. Retyping `info` would make `PolicyBinder.isExecutable` and
  `OverrideGraph.signatureOf` stop recognising the member the phase had just renamed.
- **The implicit root has to be MODELLED.** `SpoonTir.superTypes` filters `java.lang.Object` out of
  every parent list on purpose, so a graph reading parents alone reports a rename of `toString`,
  `equals` or `clone` as unanchored. `ExternalSurface` carries `java.lang.Object`'s member set as
  §1(a) universal knowledge and every closure consults it whatever the tree shows.
- **The external surface is a VALUE, and it may hold only what is COMPLETE.** `ExternalSurface(known)`
  answers exactly for a type it has, and anything else anchors. Its default is `java.lang.Object`
  plus `jdkPlatform` — the eight platform types whose member sets the JDK itself CLOSES
  (`Serializable` and `Cloneable` declare nothing, `Comparable` declares `compareTo`, `Iterable`
  declares three) — which is §1(a) for the same reason `Object`'s set is, and which frees all 12 of
  `ENGINE-LIMITS.md` K12's frozen properties. **§8.9's demand-derived surface is NOT a second source
  for this map**, though K12 originally said it was: `ExternalUsage`'s rows say what a program CALLS,
  and an absence from them is not an absence from the type. Believing them would turn this
  mechanism's counted over-refusal into an unnoticed under-refusal, which is the one trade it exists
  to refuse. `java.util.Comparator` is absent from the table for the same reason — its default
  methods grew across releases, so its surface is not closed and an incomplete entry is worse than
  no entry.
- **A refused pair records `Decision.Kind.ScopedOut` with `detail("refused")`,** not a new enum case.
  A `ScopedOut` row already means "a policy entry named this declaration and it kept its upstream form
  while the code around it moved", which is exactly a refusal; the `detail` carries the cause and the
  `why` names the fix. The enum is closed on purpose and every parallel track reads it.
- **Requests refuse in GROUPS, one level above the component.** A property is two accessors with two
  independent closures, so `MemberRenamer.Request.group` (defaulting to the policy key) is what makes
  "a renamed getter with an anchored setter" unrepresentable rather than merely unlikely.

**What the SECOND consumer settled — `TypeRedirectTransform.memberRenames`, as built.** The type
redirect's own member renames (`Disposable → AutoCloseable`, `dispose → close`) are the renamer's
first METHOD-rename consumer, and building them fixed four things this section had left open:

- **The rename is part of the REDIRECT PHASE, not a phase beside it.** Post-redirect every
  implementor's parent edge points at the external target, so `OverrideGraph` has no owned ancestor
  joining them and the N-declaration component splits into singletons — measured on the mechanism's
  own fixture, a 4-declaration component becomes `{Buffer, Sub}` plus two singletons. Whole-or-none
  then guarantees nothing: with one implementor anchored by an unparsed parent, the PRE-redirect
  order refuses all four and the POST-redirect order renames three and reports success. How that
  half program fails is not the engine's to choose — for `AutoCloseable` it is two scalac errors
  (the member is abstract); where the target's member is concrete, or the split leaves an interface
  nothing implements, it compiles and no count moves. So the ordering is a CORRECTNESS constraint,
  not scheduling, and `runsAfter`/`runsBefore` cannot express it: only one phase that builds the
  graph, renames, and then redirects can.
- **The new name must exist on the TARGET, where the target is known at all.** A rename is
  checked against `ExternalSurface` — `jdkPlatform` already closes `java.lang.AutoCloseable#close`
  — and refused with what the target DOES declare. An UNKNOWN target (the ordinary case: a
  shape-compatible type the port ships itself) cannot refuse anything, and must not: the target
  compiler is the gate there, exactly as it already is for the redirect's shape. This is the one
  place the mechanism's conservative direction inverts, and it inverts because refusing an unknown
  target would make the feature unusable for every redirect that is not to a JDK platform type.
- **`OnCollision.Refuse`, never `DeferToEmitter`.** The requested name is the TARGET's name for the
  member, so a name that lands one `$` along is not a name the target declares — which is the whole
  thing the surface check exists to prevent.
- **The redirect may name a type this module DECLARES AND PARSED**, which no port had exercised.
  The twin machinery covers it as documented, and one thing follows that an enablement must not be
  surprised by: a redirect re-points REFERENCES and never deletes a declaration, so the upstream
  type is still emitted and the port must `dropTypes` it separately. Pinned by
  `TypeRedirectMemberRenameSpec`'s owned-and-parsed case.

The config shape extends compatibly rather than replacing: `redirects { "a.B" = "c.D" }` and
`"a.B" = { to = "c.D", memberRenames { … } }` live in one map, read apart by `ConfigView.isObject`
— a PROBE that does not count as a read, so the unread-key refusal still catches a misspelling
inside an entry. Deciding the shape by CATCHING the error the other reader throws would have turned
a genuine shape mistake into a silent fallback.

**Rejected.** Blanket bean-pair auto-detection (the negative space above). `var x` as the primary target
(deferred, above). Renaming the setter into an overload of `x` — loses assignment syntax and collides in
one namespace. **Emitter-level beautification** — rendering `getX()` calls as `x` without renaming
symbols desynchronises the surface from the source map, and the emitted *surface* is what an adopter
consumes. A second scope knob beside the pairs map (two homes for one policy) — restated here because
it keeps being asked for: the pairs map IS the include list, `PolicyBinder` already reports an entry
that named nothing or named only an external, and a `RuleScope` beside it would give one decision two
homes and let a pair be listed and then silently scoped out. Per-call-site decisions
(the diff shows the site; the policy lives at the declaration). Building the graph inside the emitter —
its copy is post-§4.55, private, unavailable to transforms, and §8.3 wants it too.

### 8.6 Nullability — three stages, union floor first

**Decision.** One phase, §1(b), default-off, with **two configured targets** — `union` (`T | Null`) and
`wrapper` (a configured FQN satisfying a four-member contract) — and a three-stage design target of
which only the first is built:

| stage | what | prerequisite |
|---|---|---|
| **N1** | annotation-driven `T \| Null` floor, **no compiler flag** | none |
| **N2** | compile emitted ports with `-Yexplicit-nulls -language:unsafeNulls` | §8.2's `val`/`uninitialized` work removing the placeholder shapes |
| **N3** | strict mode per port | the out-of-scope null-flow research line |

> **AMENDED, and the amendment is the important part. "N1 costs nothing at use sites" is TRUE at a
> CONCRETE reference type and FALSE at an ABSTRACT one.** `Null` is a subtype of `String`; it is not a
> subtype of a `T <: Object`, which is the very reason `def m[T <: X](): T = null` needs a cast — so
> `T | Null` does not conform to `T`, and every use of an annotated `T`-typed declaration in a plain
> `T` slot is a compile error. Measured by binding the real policy on the reference port and
> compiling: **0 → 35 errors**, 34 of them `Found: T | Null / Required: T` and one an ARITY change at
> a defaulted overload — an overload-resolution movement this section said could not happen. Every
> one is inside a generic container or a generic widget. The probes the claim rests on used `String`;
> none used a type parameter. The engine COUNTS rather than refuses (the declaration is fine and the
> cost is entirely at the uses — `NullabilityBoundaryCheck.Issue.AbstractTypeParameter`, 155 sites
> flagged for the 35 that fail), and enabling the floor on a generic-heavy library is a POLICY
> decision with three exits: scope the generic types out, accept the errors, or land N2, under which
> the whole class disappears. Read the rest of this section with that correction applied.

**N1 costs nothing at use sites, and that is compiled rather than reasoned.** Without the flag
`Null <: String`, so a union return simplifies at every use, an override may narrow *or* widen, and no
`.nn` is required anywhere. What it **buys**: the contract moves out of an annotation the Scala
compiler ignores and **into the type**, visible to every IDE and every downstream compiler; it is
byte-forward into explicit-nulls with no second migration; and it **deletes the
`null.asInstanceOf[T]` placeholder at annotated GENERIC returns** — `def m[T <: X](): T = null` is a
type error even without the flag, because `Null <: T` does not hold at an abstract `T`, which is
exactly why the emitter resorts to the cast today, while the union form compiles. Its **honest
limitation**: without the flag it enforces nothing. It is *typed documentation* until N2.

**Two measured facts decide N2's shape.** The placeholder casts are **not** the blocker —
`null.asInstanceOf[T]` compiles under explicit-nulls (it is an unchecked cast; it lies, but it does not
error); what breaks is the literal `= null` field initialiser and every body selection on a genuinely
nullable value. And `scala.language.unsafeNulls`, **as a compiler option**, makes every probe compile —
widening overrides and literal inits included — while the *signatures* keep their honest `| Null`. As
an option it needs no per-file import, so it does not violate §6's no-imports rule. Strict mode rejects
a **widening** override, which is why annotation propagation across the override graph (§8.5's utility)
is done in N1: it is not needed for compilation now, and doing it late would churn digests twice.

**Wrapper mode attacks the SLOT, not the type** — K2's lesson transferred whole. `given Conversion` is
a hard constraint, measured never to fire through overloaded calls, and the survey adds the decisive
footnote: the reference wrapper's own two conversions are **dead in practice** (zero bare-null-into-
wrapper sites in either hand port, and at overload-heavy surfaces the hand port dodges the wrapper
entirely). So the phase retypes annotated declarations and inserts **explicit** wrap/unwrap at the same
four slot kinds the collections coercion uses — argument-vs-formal, declaration-vs-init,
assignment-vs-RHS, return-vs-result — *before overload resolution ever runs*: the argument's type is
already exactly the formal, nothing is inferred and no implicit is consulted. One rewrite is not
optional: **`x == null` on an opaque wrapper is a compile error**, so every Java null-test on a wrapped
value becomes `.isEmpty`. The contract is exactly four members — `apply` (null-normalising), `empty`,
extension `get` (unchecked, NPE on empty, which **is** Java's semantics at a dereference), extension
`isEmpty` — which the published reference wrapper satisfies verbatim today. Nothing in the contract
touches that wrapper's `orNull`, which is fake-`@deprecated` as a lint tripwire in repositories
compiling with `-Werror`; generated code must never emit it. Emission is FQN-only and extensions
resolve from the companion's implicit scope with no import (§2.5).

**The census bounds the work.** **Seven of eleven upstreams have zero nullability annotations**, so the
empty-config no-op is the *normal* case — §1(b)'s shape exactly. Where annotations exist the grammar is
declaration-position rather than TYPE_USE, with two edge shapes: an annotated **array** is fine to
retype, and an annotated **vararg** is a refusal, because a Scala vararg has no nullable form. One
prerequisite is a small §1(a) frontend gap: parameter annotations are never captured, which the output
confirms exactly — **389 upstream parameter annotation sites → 0 emitted**. Returns and fields are
consumable today, and the type model needs no extension because `TypeRepr.OrType` already exists.

**The consumed annotation is STRIPPED**: the type now states the fact, keeping both double-states it and
re-imposes the annotation-jar dependency on every port. One port's third-party annotation dependency
becomes a measurable deletion.

**The boundary is written down rather than approximated.** The hand ports cover roughly **2×** the
annotated set, and two of them hand-marked nullability over upstreams with *zero* annotations: that gap
is null-flow knowledge, not a missing rule. Of the candidate signals only the annotation is both sound
and actionable. `return null` in a body is **sound but not actionable as a retype** — it changes surface
the author did not contract, must propagate through the override graph, and misses every method that is
nullable *via a callee*; it ships as a **harvest**, a candidate list for human review, which is the same
hand-off shape §8.5's harvest uses. Javadoc *"may be null"* is a text heuristic that also matches *"must
not be null"*. "Field with no initialiser" is answered by `compiletime.uninitialized`, not by widening
the type. **Closing the gap needs interprocedural null-flow analysis over TIR bodies; no phase has flow
analysis today, and this is named as a research line, not scheduled.**

**Ordering:** after the collections family (their retypes must land first, so `@Null Array<T>` becomes
`Buffer[T] | Null` and not the reverse) and before the package rename, because the configured
annotation FQNs are upstream-namespace (§4.56).

**BUILT, and five things the build settled that the design above did not.** N1 shipped as
`NullabilityTransform` + `NullabilityBoundaryCheck`, default-off, with **wrapper mode built rather
than deferred** — its seam rules are written down either way, and a written seam nobody exercised is
a design, not a mechanism.

- **The check counts REFUSALS in BOTH modes, not only the wrapper's seams.** The union floor has
  refusals of its own — an annotated vararg, an annotated primitive, an annotation carrying
  arguments, an annotation on a type or a local — and each leaves the declaration byte-identical to
  the one the phase never saw. A refusal nobody can count is the §1(b) silent no-op the whole design
  exists to avoid, so `nullability-boundary` records whenever the phase is in the pipeline, exactly
  as the two collection checks do, and a refused site KEEPS its annotation so the contract stays
  readable at the line.
- **A retype gets a DECISION and deliberately no PORTER NOTE.** `Decision.Kind.RetypedSignature` is
  outside `PorterNote.Rendered` by a standing rule — the new type is written in the declaration and
  the diff against the Java shows it, and one note per retyped member is several hundred comments
  restating what the signature says. Nullability is not the exception that overturns it: the type
  reads `T | Null` and the annotation is gone, which is the whole of what a note would say. Its
  COMPLEMENT is rendered, and that asymmetry is the same rule rather than a break in it — a
  `ScopedOut` declaration kept its upstream type, so the diff shows nothing and the reader has no
  local evidence at all.
- **`null.asInstanceOf` is retired at TWO shapes, not one.** The generic return was the known one.
  The other is an uninitialised annotated FIELD: a Java field with no initialiser has no Scala
  default, so the emitter writes `null.asInstanceOf[T]`, and a union with `Null` states its own —
  `var parent: Actor | Null = null`. Both placeholders exist for the same reason and both go.
- **Wrapper mode refuses an override-crossing member, conservatively and COUNTED.** The wrapper
  changes the signature, so both ends of an override pair must move together; until §8.5's shared
  override closure exists the test is `isOverride`, plus any owned overriding member matching by
  name and descriptor so the PARENT end of the same pair is refused too. It over-approximates across
  unrelated hierarchies, which refuses a safe retype and counts it — never the reverse. Swapping the
  predicate for the real closure is one line and nothing else. Union mode has no such constraint,
  measured: a union return may be narrowed OR widened across an override without the flag.
- **The one interaction P3 must measure before it is believed.** The emitter's raw-parent parameter
  alignment (`rawParentAlignment`) tests `hasWildcardArg`, which does not look inside a union — so an
  annotated parameter whose type carries a wildcard AND overrides a parent method silently stops
  being aligned. Nothing today produces that shape, because the phase is off; it is named here so the
  P3 enablement measures it rather than discovering it as an unexplained diff. **Measured at P3 and it
  does not fire**: the enabled port has five wildcard-inside-union sites and not one of them is an
  override, so the shape does not occur in that library (`PROGRESS.md` §11.17). The gap in the
  predicate is real and stays open for the next one.

**Rejected.** `given Conversion` ergonomics. A **boxing** wrapper — it changes erasure, bringing bridges,
overload-erasure collisions and an allocation per annotated call, where the opaque-over-union wrapper
has none of the three (measured). **Blanket `T | Null` on every reference type** — it destroys the
annotation's information, making the annotated set indistinguishable from the unannotated majority, and
makes eventual strict mode worthless. Retyping from `return null` sites. Emitting `.nn` at consumption
sites now — meaningless without the flag, body churn with no buyer.

### 8.7 Visibility — Java's four levels, mapped

**Decision.** The frontend records **JLS-effective** visibility, and the emitter renders all four
levels:

| Java | Scala | fidelity |
|---|---|---|
| `public` | *nothing* | exact |
| `private` member of a top-level class | `private` | exact — and never qualify this one |
| `private` member or nested TYPE of a nested class | `private[TopLevel]` | **exact**, not a widening: Java `private` is accessible throughout the top-level enclosure (JLS 6.6.1) |
| package-private member / ctor / nested type | `private[<emitted pkg tail>]` | near-exact |
| package-private TOP-LEVEL type | top-level `private` | near-exact — Scala's top-level `private` already means package-plus-subpackages |
| `protected` member / ctor / nested type | `protected[<emitted pkg tail>]` | near-exact — the package half restored, the subclass half kept |
| `protected static` | public + **recorded** widening | residual |

**Root cause: `PUBLIC` is never read and package-private is never REPRESENTED.** A Java declaration with
no modifier produces flags byte-identical to a `public` one, so **the TIR cannot even state that a type
is package-private** and nothing downstream can render, record or check it. `protected` was dropped
wholesale during an error burn-down to escape the same-package-caller delta; the overload interaction
was found later and is the **cost of** that drop, not its cause.

**Feasibility is probe-verified on the two idioms that had to work**: a public member may expose a
`private[pkg]` type in its signature, and a public class may extend a `private[pkg]` base whose
inherited members stay callable cross-package. That is what retires the emitter's blanket erasure of
type-level `private` — only *unqualified* private types are barred from non-private signatures, and
that remains true and load-bearing for the top-level case. The qualifier is a **simple identifier**
naming an enclosing scope (no dotted form exists in the language), and it is derived from the emitter's
**current emitted package** — never from a symbol's upstream FQN. Because the rename runs last
(§4.56), at emission time the unit's package *is* the emitted one, so **no new two-namespace join is
created**.

**T12 retires, and not merely by avoiding its error.** With `protected[pkg]` kept, dotty **prunes
inaccessible alternatives before overload resolution**, reproducing javac's choice exactly — so the
mapping restores Java's *resolution input*, which is the principled fix the entry asks for. The
hand-written body substitution that compensated for it is deleted in the same change (its own comment
states the retirement condition verbatim), and that dependent port loses **−1 error** and one
`SubstitutedBody` row.

**Cross-package protected overrides are a wall with a door.** A child can keep neither bare `protected`
nor its own package's qualifier — both are *"has weaker access privileges"* — but it **can** name any
**enclosing** package, and a qualifier at the nearest **common** enclosing package satisfies the check.
Under the corpus renames every emitted package nests under one root, so a common ancestor always exists
and **no protected override needs to widen to public**. The added access is nominal rather than
behavioural: any same-subtree caller could already reach the member in Java through a parent-typed
receiver, since dynamic dispatch lands in the child regardless.

**Two structural facts about a qualified boundary after a rename.** An upstream package **cannot split**
across emitted packages **under the package-rename map alone** — renames map whole packages, cut at
separators — so under that map the only delta is **merges**, where three corpus ports fold two upstream
packages into one emitted one and former siblings gain access Java never granted; that is a consequence
of *configured policy*, not of the mapping, and is recorded as such.

**A per-TYPE rename falsifies the no-split premise, so the split gets the same treatment as the
merge.** M6 adds `typeRenames` beside `packageRenames` on the one renaming phase, and a type rename
moves **one type at a time**: two types that shared an upstream package can land in different emitted
ones, and a package-private or `protected` member declared by one and read by the other then crosses a
boundary Java never had — the dependent-safety argument above cannot rest on a premise this port's own
policy can break. M6's bind-time checks therefore gain a **`package-split`** rule beside its
target-freedom check: a type rename that changes a type's **emitted package**, where that type declares
or is referenced by package-private/protected members across the old boundary, is **refused** — or,
where the port declares the split deliberately, **recorded as a `package-split` `Configured` widening**,
exactly parallel to `package-merge` and for the same reason (it varies per port and per rename entry, so
it is what `Configured` is for). **Which rule wins in the qualifier derivation is stated once: the
recorded widening does.**

**BUILT (M6), and four things the implementation settled that this paragraph could not.** The
per-type policy is four maps on the one renaming phase — `typeRenames` (a whole upstream FQN, or a
bare simple name renaming in place), `subPackages`, `flattenNestedTypes` and `allowPackageSplit` —
all four inherited by dependents (§1.5) and compared by `ManifestAgreement.TypeRenameDivergence`. The
last three DERIVE into per-type entries of the same longest-prefix table the package renames use, so
there is one rewrite, one LAST position and one check; every target is written UPSTREAM and the
package rename is applied to it once. What the code had to decide:

- **A boundary is decided by SCALA's rule, not Java's, and the two differ in exactly one direction.**
  Java's package boundary is exact; Scala's `private[p]` covers `p` *and its subpackages*, which is
  what this section already says about subpackage nesting. So nesting a type under `p.internal`
  keeps everything `p` restricts reachable FROM it and removes only the other half. Compared by
  string equality the rule refuses every `subPackages` entry for a crossing that does not exist —
  measured on simple-graphs, where the equality form refused `BinaryHeap` and `NodeMap` and the
  `reaches` form refuses neither, matching the hand port.
- **`flattenNestedTypes` gets the SAME rule at the enclosure**, recorded as `enclosure-split`
  beside `package-split`: Java's `private` reaches throughout the top-level enclosure (the
  `private[TopLevel]` row above), and a promoted type is no longer inside it. It is a second cause
  and not a second mechanism.
- **The rule sees the `protected` half of the package boundary and NOT the default-access half**,
  because package-private is not represented in the TIR at all (the root cause above). That is the
  measured limit of M6's refusal, stated where the flag lands: one predicate, one line to widen when
  this section's `Flags` work makes package-private representable.
- **Two structural refusals the boundary rule does not cover, both `Malformed`:** a destination that
  is already some other type's emitted name is a COLLISION rather than a hit (two files silently
  overwriting each other, and no count moves for it), and a Java INNER class cannot be flattened at
  all — it carries an implicit reference to its enclosing instance and a top-level type has nowhere
  to keep it.

Measured on simple-graphs against the hand port it reproduces: `Connection$DirectedConnection` and
`Connection$UndirectedConnection` flatten with **0** findings, `BinaryHeap` and `NodeMap` sub-package
with **0**, and `Array -> internal.InternalArray` is refused with **1** — `Array#strictResize` is
`protected` and inherited by `algorithms.AlgorithmPath`, which the move takes out of the declaring
package's subtree, so its qualifier must widen to the common ancestor. Declared, that is exactly
**1** `WidenedVisibility` row with `cause=package-split` for D3 to read. The qualifier is derived from the emitter's *current emitted package* and
never from an upstream FQN (that is what keeps this out of §4.56's two-namespace join), so a split
type's qualifier names its NEW package — narrower or wider than Java's, but always the truth about the
emitted file — and the `package-split` row is the record that the boundary moved. And subpackage nesting only **widens, never blocks**; across ports every dependent's
`governs` set is disjoint from its base's, so none of their Java ever legally touched a base's
package-private member — javac would have rejected it. **The one deliberate package-sharer is a
library's own test suite**, which declares its types inside the library's packages (the standard Java
same-package test idiom) and routinely reads package-private members — and because the dependent
**inherits** the base's rename map rather than copying it, both land in the same emitted packages and
the idiom keeps working. **The mapping preserves it precisely because §1.5's inheritance rule holds**, and
a dependent that landed in a base's emitted package by *accident* is what a manifest check should flag.

`Symbol.privateWithin` — a stub mirroring `reflect`, populated nowhere and read nowhere — becomes real,
**as effective visibility on `Flags`, not as a `SymId`**: a `SymId` cannot name a package in this TIR,
because packages are `fullName` segments and not symbols.

**What records and what does not.** The mapping itself is §1(a) universal and **the diff IS the change**,
so a faithful rendering records nothing — recording it would be thousands of identical rows burying
every real decision, which is the altitude rule §4.575 already states. The two systematic
over-approximations (subpackage nesting, dependent-namespace nesting) are properties of *every* qualified
boundary rather than of any declaration, and are documented **here, once**. What *does* record is the
residual widenings, and they **reuse `Decision.Kind.WidenedVisibility` for members AND types** rather
than minting a type-level kind: the kind is the fact that this declaration ships wider than Java wrote
it, the subject column already distinguishes a type from a member, and the §4.575 grammar puts the cause
in the pairs — a new kind would need its own note placement and its own coverage wiring for no
additional information. The causes: `x-pkg-protected-override`, `protected-static`,
`qualifier-shadowed` (the guard for an enclosing type named like the package tail, which otherwise binds
the qualifier to the *class* and silently narrows the boundary), `x-pkg-pkg-private-override` (Java's
non-override across packages has no Scala form, and adding `override` **changes dispatch** — stated in
`why`), the retargeted `ctor-replay-widening`, `member-rename` (below), and `package-merge` plus
`package-split`, which are the two **`Configured`** causes because they vary per port and per rename
entry.

**`member-rename` is the largest of them and was the last to be recorded, which is the lesson.** Both
of the emitter's §4.55 field-clash passes strip `private` and `protected` from every field they
rename, unconditionally, and they must: a renamed field has to stay reachable from wherever Java read
it, and Scala's own access rules do not grant that at the new name. The RENAME was recorded and the
WIDENING was not, so ~280 members on the largest port shipped `public` carrying a `RenamedMember` row
that says nothing about visibility and no row that does. **Nothing in the pipeline could catch it** —
the emitted visibility is what it always was, the compile is unchanged, and `NoteCoverageCheck`
compares decisions to NOTES rather than decisions to reality, so a widening with no decision is
invisible to it in the one direction that matters. It also explains a number that had been filed
against the wrong decider: `WidenedVisibility` 142 → 135 when a policy rename moved seven
`BaseDrawable` fields out from under the ctor-replay widener's `isPrivate` test — the honest reading
is not that that decider lost seven rows, but that THIS one never had them.

**And the note is RENDERED, which is a decision rather than an omission.** `WidenedVisibility` is
already in `PorterNote.Rendered` for its other five causes, and `Rendered` is per KIND: excluding
half of one kind is not expressible without splitting the kind, and splitting a kind to hide half of
it is exactly what §4.575 says a note must not do. The P2 precedent that excluded `RetypedSignature`
does not transfer, and the reason is the same one that admitted `ScopedOut` beside it: a retyped
signature IS the declaration and the diff shows it, while an ABSENT `private` is only meaningful
against Java the reader does not have. The blast is paid once and stated: ~280 declarations gain a
second note beside the rename note they already carry.

**Two hazards the mapping itself introduces, with their answers.** A widened member re-enters overload
resolution for outside callers — the T12 shape, now caused by the port's own widening; the residual set
is small and every member of it carries a decision and a note, so the failure is at least attributable.
And **a companion re-export creates a public forwarder for a `private[pkg]` member**, silently undoing
the mapping for statics; the re-export must filter members that did not render public, which is also the
*faithful* rendering, since Java's own access to a parent's package-private static is package-scoped.

One rule-scoping correction that is not cosmetic: the emitter drops `override` for a private member.
That is right for **bare** private and **wrong** once qualified private exists — a `private[pkg]` member
does override and needs the keyword. The existing negative stays pinned: qualifying a top-level class's
own bare `private` regressed a port by 1 error, because a member that overrode nothing suddenly did.

**BUILT (D3), and seven things the implementation settled that the paragraphs above could not.** The
level is decided once, whole-program, in `emit/Visibility.scala`; the QUALIFIER is supplied by the
emitter from the package it is currently writing into, which is why two of the five `Vis` cases carry
no string at all. `Flags` gained `isPackagePrivate` (additive) and `Symbol.privateWithin` is deleted.
What the code had to decide:

- **The cross-package `protected` override lives in ANONYMOUS classes, and a walk over class bodies
  finds none of them.** `new Pool<T>() { protected T newObject() { … } }` is how a Java library
  writes a factory, and every one is an override across the package that declares `Pool`. An
  anonymous class is a `Tree.New`, not a `ClassDef` (§3's rule, one more time), so the override
  graph missed all of them: **14 `E164` errors** on the largest port, each *"has weaker access
  privileges"* — and note the failure mode is an ERROR rather than the silent widening the rest of
  this section is about. Counted, the residue is **28** cross-package protected overrides where the
  census, which could only scan declared classes, predicted 10.
- **`protected static` covers nested TYPES too**, for the same reason it covers members: a `protected
  static class` moves to the companion `object`, and nothing subclasses an object. Its own
  CONSTRUCTOR is not static and keeps `protected[pkg]`, which still admits a subclass in any package.
  With the types counted the residue is **99**, not the census's 71.
- **A NILARY constructor has nowhere for the modifier to sit**, so it gets the empty clause written
  out — `class C private[p] ()`. NOT when the class already carries a context clause: `()(using Ctx)`
  is a different signature from `(using Ctx)` and every call site would have to move.
- **The companion re-export filter must skip `<clinit>`.** A static initialiser block is not a name;
  excluding it emits `export P.{<clinit> => _, *}`, which the parser reads as an XML start tag —
  **29 `E040` syntax errors**, and the second time that exact trap has been sprung in this file.
- **`ctor-replay-widening` is NOT retargeted to `private[pkg]`.** The replayed statements execute in
  a SUBCLASS, which the port may emit into another package, and a package boundary does not reach
  there; public remains the only form that always does. Its row now carries the same `cause=` pair as
  every other residue, so "what widened, and why" is one grep.
- **An INJECTED file has to agree with the surface of the type it replaces, BY HAND.** Nothing
  derives an injection's signatures from the Java it stands in for, so a hand-written shim that made
  an upstream `protected` member public is weaker-access at every override the port emits — one
  measured site, one error, fixed in the shim and not in the engine.
- **`override` is dropped for java `private` and KEPT for package-private**, which is the scoping
  correction below stated as the level rather than as the qualifier: `private[TopLevel]` IS java's
  `private` (JLS 6.6.1) and overrides nothing, while `private[pkg]` does override within its package
  and needs the keyword.

**Blast: one designed corpus-wide re-baseline.** The census *is* the predicted movement — for the largest
port ≈ 867 protected + 1,043 package-private + 36 types + 23 ctors ≈ **1,970 member digests**.
`members-unchanged` is the wrong gate for this change **by design**; the gate is that error counts do
not rise unexplained, test outcomes do not move, and every new error maps to a recorded cause or a
missed guard. Two empirical anchors keep it honest: the exhaustive set of same-package non-subclass
`protected` callers is **20 sites** — exactly what bare `protected` breaks and `protected[pkg]`
preserves — and Java's non-override shadow (a package-private method re-declared in a different-package
subclass) is **0 sites over 9,346 method declarations**, so its guard is cheap insurance rather than a
live cost.

**Rejected.** Bare `protected` — it denies Java's package half, which is the access-error class that
caused the original drop. Keeping the drop and recording every one — thousands of rows saying the same
sentence, and T12's error class stays alive. A new type-level `Decision.Kind`. Dotted qualifiers, which
do not exist. Populating `privateWithin` as a `SymId`. Deriving the qualifier from the upstream FQN plus
the rename map — it re-creates the two-namespace join §4.56 exists to kill. A **mixed per-usage
strategy** (qualify only where a same-package caller exists) — visibility then becomes a function of the
caller census and is unstable under any upstream edit.

### 8.8 Trivia — a hybrid, and a loss that is not where it was thought to be

**Decision**, three mechanisms, ordered by what each retires:

1. a **`trailing: List[Trivia]` slot on `Tree.Block`**, with the frontend **keeping** instead of
   dropping the leftover comment-statements;
2. **position-based file-leading harvest**;
3. **span-interleave as a completeness BACKSTOP only.**

**The finding that reorders the work: the dominant residue category is not an emitter rewrite at all.**
The frontend's statement fold accumulates comment-statements into a pending buffer and folds them onto
the **next** statement — or **discards them when there is none**. Because they were already *claimed*,
no coarser harvest can ever recover them: **claim-then-drop**, one line. Three traced sites are all the
same mechanism — a comment that *is* an empty override body; commented-out code as the last line of a
method; a comment between a `case`'s `return` and `default:`. The contrast is what made the residue look
arbitrary from outside: a sibling comment one line further up folds onto the following `return` and
survives. And the case-terminator `break` filter **manufactures** the shape — a comment written above a
stripped `break` becomes trailing the moment the break is deleted. Fixing it **in the model** gives
**exact** placement, because end-of-block is precisely where Java had these comments; no fallback
heuristic, no marker.

**The V3 culprit is Spoon's attachment model, with one unread harvest point as accessory.** The walk
reads the compilation unit's comments, every element's own, and a filter sweep over every declaration
subtree; the only attachment site never read is the **package declaration's**. Since a type-attached or
statement-attached block would have been emitted — misplaced but *present* — the loss is either that
unread slot or Spoon attaching nowhere, and the recorded measurement is that the unit carries only the
**first** of consecutive leading blocks. The fix is position-based either way: every scanned comment
whose end precedes the `package` keyword's offset is file-leading, the Spoon-attached subset claimed by
identity as today, the **rest** taken from the scanner stream verbatim, in source order. **And V3 is
undercounted and worse than recorded**: at least seven more files of the same shape sit inside one
port's existing residue, and in one generated-parser family the **dropped block is the Apache notice
itself** — so V3's own mitigation, *the licence is the first block so it survives*, does not hold there.
That makes this a licence obligation (§4.57, §4.58), not a tidiness item.

**Pure interleave is rejected as the primary channel, and the reason is a number.** The source map's
granularity is the **member**, so a pure interleave can place a comment no finer than *somewhere in this
member* and every body comment would clump at member boundaries — while the attachment channel is the
only carrier of statement-level position through rewrites (`Commented` survives the traversal;
`recomment` restores it) and it currently places the overwhelming majority correctly: **7,159 members
carry trivia** in one port's emission. **Replacing a channel that is right at fine grain with one that
is complete at coarse grain optimises the check number at the cost of the thing the check exists to
protect.**

So the backstop runs **inside the emitter, after body text is built and before the source map is
computed** — a post-pass over finished text would desync `srcmap.tsv` and `members.tsv`, and M7's rule
(join on a recorded id, never on the rendering) applies to line ranges too. It reads the emitter's own
slots, tests presence through the **shared** normalisation function rather than a fork, **strips porter
notes before searching** (a note names the upstream FQN on purpose and otherwise produces phantom
matches — §4.575's own recorded trap), anchors on the last member whose origin line precedes the
comment, and appends **after** that member's rendered text — *between* slots, so no member digest moves
and only the whole-file digest does. Each recovered comment carries one marker line naming its Java path
and line: **a comment relocated WITH its source coordinates is a quotation, not a false statement about
the code below it**, which is V1's own objection to hoisting, answered.

**That marker is deliberately NOT porter-note grammar.** A recovered comment records **no `Decision`** —
a row per comment is finer than §5.1's one-row-per-declaration rule — so a `/* porter: … */` line would
be a note with no decision behind it, which `NoteCoverageCheck` fails the run for, in that exact
direction (§4.575). The marker therefore has its own shape,

```
/* trivia: recovered from <path>:<line> */
```

with three properties that are specified rather than incidental: it is **inventoried by `TriviaCheck`'s
`recovered` lane**, which is where the count belongs; it is **exempt from note coverage BY SHAPE**, not
by an exemption list a future kind can fall off; and every note-stripping-adjacent check **strips it
exactly as it strips a note**, per M7's precedent — a check that searches emitted text for a string must
first remove what the engine wrote *about* the code, or it matches the engine's own words (the trap that
produced three phantom dangling drops the first time notes shipped).

`CommentScanner` gains start offsets. That is an `api` change and it also makes the check's line
recovery exact for free — today it recovers a line by `indexOf`, which is wrong for duplicated comment
text.

**`TriviaCheck` grows LANES rather than shrinking scope**: `lost` (the publishable bar, target **0**),
`recovered` (backstop placements — a counted residue in M6's sense, **not** a success), and
`deliberate`, **derived** from the run's own drops exactly as the expected-failure ledger is (§3.6). That
last lane fixes a live miscount: the check's deliberate-drop exemption is **type-level only**, so a
policy-dropped **member's** Javadoc is currently counted as engine loss, and several such rows sit in the
baseline today.

**Order:** (2) first, whose blast is only the V3-shaped files; then (1), the largest, where every member
with a trailing comment changes digest; then (3), which moves whole-file digests only. One mechanism per
commit with `before->after` trivia counts in the subject.

**Updates the recorded limits**: V1's category table is *wrong about where the loss happens* — the
dominant category is a frontend claim-then-drop, not an emitter rewrite — and V3's site count rises while
its licence mitigation is withdrawn. Both in the same commit as the fix.

#### 8.8 AS BUILT — the design held; four things it did not say

All three mechanisms landed as specified, in the specified order, and the bar is met: **`lost = 0` on
every one of the thirteen ports**, from a corpus total of 233. Per port, `lost / recovered /
deliberate`, with `lost` shown against its committed baseline:

| port | before | after (all three) |
|---|---|---|
| libGDX core | 100 | **0** / 4 / 12 |
| libGDX tests | 69 | **0** / 0 / 0 |
| anim8 | 34 | **0** / 0 / 0 |
| gdx-gltf | 10 | **0** / 4 / 0 |
| gdx-vfx | 11 | **0** / 2 / 0 |
| screens | 7 | **0** / 1 / 0 |
| simple-graphs (+ suite) | 1 + 1 | **0** / 0 / 0 each |
| ashley, ashley-test, gltf-test, jbump, noise4j | 0 | **0** / 0 / 0 |

**`recovered` is small because the lane was READ, not because the backstop is good.** Its first run
on libGDX's suite was **51**, and all 51 were one category: a `@Test` method's javadoc, lost because
`TestFrameworkTransform` turns the method into a `test("…") { … }` STATEMENT and the `leading` field
went with the `def`. That has an exact home — the TIR's carrier for a statement's comments is
`Tree.Commented` — and taking it dropped the lane to 0. This is the loop the lane exists for: a
`recovered` count that reads high names a category that still wants a home, and shipping the
backstop over it would have hidden 51 comments at member granularity behind a green `lost = 0`.

What the design did not anticipate, in the order it was learned:

- **V3's culprit is the PACKAGE DECLARATION, not "nowhere".** The brief's accessory lead was the
  answer: `CtPackageDeclaration.getComments` carries the second of two leading blocks. It changes
  nothing about the fix — reading one more of a parser's slots leaves the next shape unhandled, and
  no set of slots can order two blocks — but the probe is pinned in a spec so a Spoon release that
  moves it is visible.
- **The file-leading harvest had to become a CLAIM, and had to run FIRST.** A positional harvest can
  take a comment the parser also attached to the type, which would emit it twice. So the header owns
  its SPANS (`headerSpans`), the finer harvests skip them, and the whole thing runs before any type
  translates — a positional claim binds only the harvests that come after it. The header is cached
  per compilation unit, which is what keeps "each of a file's top-level types carries the whole
  notice" a fact of the code rather than of two harvests agreeing.
- **The recovery backstop made the source-map slots load-bearing for EMITTED TEXT.** They were
  recorded only when the artifact layer was on. The backstop anchors on them, so a run with
  reporting off would have emitted a different file from the same program — recording is now
  unconditional. The same reasoning added the upstream file's digest to `TirCacheKey`: the backstop
  reads comments the frontend never harvested, so a source edit that moved only one of those changes
  the file while every tree digest stays identical.
- **"Between slots, so no member digest moves" is true only while slots do not NEST.** They do: a
  nested class's recorded text CONTAINS its members', so a comment placed after a nested member
  falls inside the enclosing class's string and `srcMapOf` — which finds a member by searching for
  exactly that string — then cannot find it. 2 UNLOCATABLE members on libGDX core the first time
  this shipped, which is a silent hole in the map that misattributes every later error in those
  types. The splice therefore applies the insertion to every ENCLOSING slot as well; that member's
  digest does move, and honestly, because it did gain a line.
- **Two silent defects the mechanisms exposed rather than caused**, both invisible to every count:
  `TriviaCheck.normalize` stripped `//` LAST, so a nesting comment rendered as `//` lines (§4.58)
  normalised differently from its java and was reported lost while sitting in the file; and a
  finding carries the check name it is filed under, so the `deliberate` lane's rows were filed
  against `trivia` — `lost` read 12 and `deliberate` 0 on a run whose own stdout said the reverse.
  Both are pinned by specs.

And one §5.4 repeat, which is why that rule is a rule: `CommentAnchor`'s map is keyed by java path
and its two consumers hold the path in different spellings — what the parser recorded, and what the
orchestrator resolved. Compared raw, the check's lookup missed EVERY file in a worktree, and the
`deliberate` lane read zero on a port with twelve dropped-member javadocs. Both sides go through
`RealPath` now.

### 8.9 The JDK surface — derived from the walk that already runs

**Decision.** One new artifact and one new check, **both second consumers of an enumeration the engine
already computes**.

`PortabilityCheck.checkAll` already walks every referenced external member, resolves its `owner#name`
(correct only since P4 gave an external member an owner), and holds each one's usage kinds and site
counts — **and throws all of it away except the hits of its 34 rules**. No artifact of external
references exists anywhere. So `external-surface.tsv` is **one filter away**: lift the enumeration out of
the rule filter and write one row per external member this port's **emitted** units reference —
`owner#name(descriptor)` (arity is not enough, per D1), usage kinds, site count, first origin — with
**zero new traversal**.

The `jdk-surface` check classifies every call-kind row against three sources, and **anything
unclassified is a finding**:

| class | source of truth | what it tells the agent |
|---|---|---|
| shimmed | the runtime artifact's concrete-member map, already pinned to the published sources by a derivation spec | engine, done |
| mapped | the transform's **handled set** | configured — enable or extend this phase |
| refused | a `Refusals` table, each entry carrying `why` **and** its `ENGINE-LIMITS.md` pointer | (a) with a citation, or (c) per manifest |
| — | | **the port's JDK wall, named** |

The *mapped* row is the one that requires work, and it is work worth doing on its own: the collections
transform's static and instance tables are **`match` arms, not data**, so adding a mapping is three
coordinated hand edits and nothing can ask *"what does this phase handle?"*. **Lift the arms into one
declarative table that the arms AND the check both read**, pinned by a bijection spec in both directions
— the same discipline the runtime-members spec already applies, and the file's own "one list" comment
finally made true.

**Refusals are CHECK DATA, not decisions**, and the line is principled: `decisions.tsv` records what
changed an emitted **declaration** (§5.1's altitude rule), and a kept JDK call changes nothing — it is a
fact about the *surface*, which is what a check table is for. Today refusals have no home at all: several
live in doc comments and `case _ => None` arms, no decision kind fits an external member, and a refusal
surfaces only as a compile error, *after* a compile. **An uncited refusal is itself a finding.**

**K9 becomes a derived demand rather than an open entry.** A row whose type appears as a `ForEach`
receiver and is neither retyped by a phase — decided from the phase's **own table membership**, per
§4.56, never from the type's name — nor covered by the shipped iterable shim is a finding of its own
kind, pointing at the §1(b) phase that entry already specifies.

**The day-one story is the point.** A new library's first `preview` run prints, beside the fifteen
checks, its entire JDK wall as classified rows — *N* shimmed, *M* mapped-if-you-enable, *K*
refused-with-reasons, *J* unclassified — and *J* is the work list, each row carrying the §4.45
classification an agent needs. Two worked cases: a `Collections.swap` demand would have been an
*unclassified, 1 site* row on that library's **first** run instead of a compile error three lanes later,
and K9's two errors in another port would have been a named finding **before any compile**.

**Scale**, measured from emitted text (fully qualified by §6, so grep is a near-census): **144 distinct
`java.*` types and 108 distinct statically-qualified members** across nine ports. The 108 is a **floor** —
instance calls on kept receivers are invisible to text, while the TIR inventory sees them all, which is
itself the argument for deriving the artifact from the walk rather than from a grep.

**As built, four things the design did not say, each of which changes how the report reads.**

- **A fifth and sixth class, and they are what make "anything unclassified is a finding" mean
  something.** `Kept` is a member nothing in the port claims — emitted verbatim against the JDK,
  portable, compiling. `java.lang.Math#max` is such a member at hundreds of sites, and reporting
  those would put hundreds of rows in front of the one that says `Collections#rotate` has no
  translation; a report whose false positives must be routinely ignored trains its readers to
  ignore it (§4.45). A member is a FINDING when the port's own machinery has already **moved the
  ground under it** — the owner was retyped and the member was not, or the loop that iterates it
  will not compile. `Kept` is counted in the summary, never hidden. `Mappable` is the second: with
  the retyping phase ABSENT the same unmapped member is untouched JDK code the port chose to keep,
  so the row is an OFFER and not a hole. `ran` is therefore a parameter of the check's `Mapping`,
  not a property of the phase — the same tables answer two different questions.
- **The rows are the surface AFTER the pipeline.** A member whose call the phase actually rewrote is
  no longer referenced and does not appear at all, which is the strongest outcome available and the
  reason the `mapped` count is smaller than a reader expects. `Mapped` survives only for rewrites
  that keep the java member SYMBOL and change its shape (`xs.size()` → `xs.size`, `get(i)` → `xs(i)`).
  A check reading the PRE-pipeline program would show a comfortable `mapped` row for a dependency
  the port does not have.
- **K9 is derived from the NODE, not from a table.** "the receiver's java type is absent from the
  phase's `typeMap`" is the wrong side of the mapping and fails in both directions: a scoped-out
  declaration keeps a real `java.util.List` that the table calls mapped, and a port with no phase
  keeps the same type that the table also calls mapped. The post-pipeline type standing in the
  receiver slot is what the emitted `for (x <- xs)` will be applied to, whatever any phase intended
  — §4.56 at its strongest, since the conclusion comes from what the phase did to *this expression*.
- **The tables are DECLARED beside the arms and pinned by a source scan, not lifted into one
  structure.** The design asked for the `match` arms to become a declarative table both the arms and
  the check read; what landed is `CollectionsTransform.handledStatics`/`handledInstance` as data,
  with `CollectionsHandledDerivationSpec` asserting the bijection against the arms' own SOURCE TEXT
  in both directions. Restructuring 25 static arms and 25 instance arms whose guards read receivers,
  collectors and result types is a rewrite of the phase, and it would have been measured in the same
  commit as a new check — §5's "change one thing". The scan is SLICED to each function's region and
  asserts the region does not contain the table, because a whole-file scan would find its own answer
  and pass vacuously in both directions.

### 8.10 One `RealPath` — and a watch note on class-initialisation timing

**Decision.** One utility, `balticporter.core.RealPath`, replacing the **four divergent private copies**
of §5.4's helper; the two remaining raw comparisons fixed; enforcement by a grep-spec **plus** an auditor
hunt line, because the two catch different things.

**The four copies are a bigger liability than either raw site**, because each reimplements the rule
differently and three of the four have distinct exception policies — one of them a bug:

| copy | policy |
|---|---|
| the run's unit partitioner | catches `Exception`, falls back to `toAbsolutePath.normalize` |
| the check report's relativiser | falls back to **bare** `normalize`, so a relative path stays relative and the subsequent `relativize` can throw — whose outer catch then returns the raw absolute path, the one thing that function's own doc promises never to emit |
| the emitter's source-path resolver | catches only `IOException`, so a `SecurityException` or `InvalidPathException` escapes and kills emission |
| the vendored-commit reader | guard-based (`exists` then `toRealPath`) — TOCTOU, and it throws on exists-but-unreadable |

`RealPath.of` is §5.4's rule verbatim — realpath, with `toAbsolutePath.normalize` **only** as the
not-exists fallback so the fallback can never produce the relative-vs-absolute throw. Beside it go the
**comparison** forms (`startsWith`, `relativize`, `str`) so a call site states intent rather than
composing one, and a strict `ofExisting` that throws with a diagnostic, for the frontend sites where an
absent declared input must be **fatal** rather than silently normalised (§5.1's missing-input rule).

**Two raw sites remain.** The config loader's base-chain **cycle detection** compares `normalize`-only
paths, and here the §5.4 failure is a **crash rather than a wrong number**: a base cycle spelled through
a worktree symlink is undetected and becomes a `StackOverflowError` instead of the intended
configuration error. The loader's own doc argues that lexical resolution *"resolves to the same place
either way"*, which is right for **resolution** and wrong for **comparison** — so resolution stays
lexical and the seen-set gets `RealPath.of`, and the distinction is then enforced by *using different
functions*. The second is a freshness-roots site that spells its roots lexically while three neighbours
spell the same roots through the realpath helper; it is consumed only by existence probes today, so it is
safe and would regress silently under any future prefix test. **Two independent briefs flagged it**,
which is the argument for fixing a spelling inconsistency that is not yet a bug.

**Enforcement needs both halves.** A source-scan spec asserts that `.toRealPath(` appears in production
code **only inside the utility** — helper duplication is exactly what a grep *can* see, and it is the
failure that actually happened four times. A grep for raw `startsWith` on paths is deliberately **not**
built: path-ish receivers are not syntactically distinguishable from FQN prefix tests, and a spec whose
false positives must be routinely ignored trains people to ignore it. The semantic half is an auditor
hunt line — *any comparison, prefix test or relativize between a CONFIG-written path and a
PARSER-recorded path that does not go through `RealPath`; check both operands, a lexical `normalize`
beside a `startsWith` is the signature, and the worktree is the environment where it fails.*

> **Watch: class-initialisation timing is not preserved, and nothing measures it.** Java runs a class's
> static initialisers lazily, on first *active use* (JLS 12.4) — and a read of a constant variable
> (`static final` with a constant initialiser) is not an active use at all, because javac inlines it.
> Scala companions are also lazy, but at a different granularity: **any** member access initialises the
> **whole** companion, in declaration order. The port already carries two consequences: constant
> variables emit as `inline val` (§4.4) precisely so that reading one triggers nothing — the dodge that
> broke the `Vector3`/`Matrix4` initialisation cycle — and Java `static { }` blocks emit as members that
> run at companion init rather than at Java's class-init point. What remains **unmeasured**: a
> non-constant `static final` read still forces its entire companion where Java forced one field's
> class; cross-companion cycles that Java resolved by partial initialisation (a static read during init
> observing a default value) may deadlock, NPE, or simply produce different values in Scala; and a
> side-effecting static initialiser runs at a different moment than upstream. The failure profile is
> T10's — no compile error, no count moves, only behaviour — so if a port ever exhibits init-order
> symptoms, **this is the paragraph to reread before instrumenting.** Not a project; a named suspect.
>
> **And it bites the ENGINE'S OWN objects, which is not hypothetical.** Moving
> `CollectionsTransform.typeMap` into the companion so a check could read it (§8.9) put it ABOVE the
> four `*Fqn` vals four of its entries name — an `object`'s vals initialise in declaration order, so
> the table was built with four `null` targets, compiled cleanly, and threw a `NullPointerException`
> deep inside the phase's `run`: **49 corpus tests, one edit.** A table that names other vals of its
> own object goes below them, and `CollectionsHandledDerivationSpec` now asserts no target is null,
> because a `null` in a `Map[String, (String, Kind)]` is invisible to the type system.

### 8.11 Ordering, and the interactions the briefs left open

Four interactions cross design boundaries and are resolved here rather than in either subsection.

**§8.2's `protected` primary × §8.7's `protected` mapping — they compose, and the composition is what
makes both safe.** §8.2 chose `protected` over `private` to avoid a whole-program *is this class
extended?* question; §8.7 renders a Java `protected` declaration as `protected[<emitted pkg tail>]`. The
synthetic primary is **not a Java declaration**, so §8.7's mapping does not reach it: it is emitted as
**bare `protected`**, which is the wider form in the subclass direction and the narrower one in the
package direction — and that is exactly the right pair of answers. Its only legitimate callers are the
class's own secondaries, which are inside the class, and a subclass's `extends` clause **in any
package**, which bare `protected` permits and a package qualifier would deny across a dependent
boundary. A package-qualified synthetic primary would break precisely the cross-module subclassing §8.2
chose `protected` to protect. §8.7's rendering continues to govern every Java-declared constructor,
which after §8.2 is every **secondary**: one rule per kind of declaration, no overlap.

**§8.2's D4 dissolution × §8.3's contract scope — the constructor row becomes attribution-only for wall
classes.** §8.2 removes D4's cause for 348 of 430 classes by making the synthetic signature a *local*
function of the Java, while §8.3 keeps a `primary=` / `primaryKind=` / `primaryVis=` row for every type.
Those are not redundant, and there is **ONE implementation**, pinned here because the two subsections
read as two: a dependent **derives the synthetic signature locally for every non-wall class AND compares
it against the `primary=` row wherever a row exists** — never one or the other by circumstance. For a
non-wall class the row is a **cross-check**, and the cross-check is real and **FATAL**: a disagreement is
an engine bug, reported as such and failing the run, because the local derivation is only "the same
answer the base got" while both modules run the *same engine version*, and an engine upgrade between the
base's run and the dependent's is precisely the drift a purely local derivation cannot see. (The engine
fingerprint in the map header says the versions differ; only this comparison says whether the difference
changed a signature.) For the **82 wall classes** the fixpoint survives and is still whole-program, so
there is nothing to derive: the row is **load-bearing** and the dependent reads it. §8.3's honest-scope statement then
applies to the wall row specifically: where the contract says the base emitted a primary a dependent's
subclass cannot reach, there is no local repair — the outcome is **refuse the replay and count**, never
demote the base's plan. And §8.3's open question about a `private` synthetic primary is answered by
§8.2: it is `protected`, and reachable.

**§8.4 and §8.5 share ONE `OverrideGraph` — §8.5 owns it, §8.4 consumes it.** The merged requirements:
edges keyed by name **and descriptor** (D1's rule now, §8.1's identity when it lands, misses reported and
never guessed); the walk uses `StandardTraversal` so anonymous-class bodies are nodes (§3);
`closureOf` returns owned members plus `externalAnchors` plus `baseAnchors`; and the shared invariant is
that a signature change applies to **all of a component or none of it**. The consumers differ only in
what they do with a frozen component: §8.5 refuses the rename with a finding, §8.4 falls back per site —
capture where lexical, residual-global otherwise — and counts a `frozen-component` seam. §8.4 does not
use `MemberRenamer`, because it changes signatures rather than names; that is why the two layers are
separate types.

**The two phase-ordering constraints do not conflict.** §8.5's property transform runs **before** every
retyping phase, so the descriptors it matches are Java's own; §8.6's nullability runs **after** the
collections family, so it wraps the *result* of their retype. They sit at opposite ends of the surface
list, and the rule that governs both is §4.56's: every policy key is written in the **upstream**
namespace, and the package rename runs **last**.

The ordering that follows, with the reason each edge is real rather than scheduling:

| track | order | why |
|---|---|---|
| core-model spine | **§8.1 first, alone** | it touches symbol minting, the `Program` constructor and every keyed phase; nothing that reads symbol identity can run beside it |
| emitter track | **§8.2 → §8.7 → §8.8** | all three rewrite emitter regions (constructor rendering, modifiers, trivia), so serialising avoids three-way merges — and each moves member digests, which must stay separately attributable (§5) |
| base surface | **§8.3 after §8.2, after §8.1's ownership work** | the contract's constructor rows *are* §8.2's signatures, and `Surface.owns` is the same climb §8.1 touches |
| the armed phases | **§8.3 before §8.4's and the opaque-type enablements** | four of the nine drift sites are latent behind exactly those phases; arming them first is how the family grows a tenth face |
| transform track | **§8.4, §8.5, §8.6 in parallel, default-off** | none reads a policy key beyond a type binding; each is its own phase, factory and spec, and the gate is every lane 0 members changed |
| blocked on §8.1 | ~~call-site substitution, the comparator table~~ (**BUILT — §8.12**), §8.3's member rows | each needs an overload-exact key |
| checks track | **§8.9, §8.10 any time** | no shared files with the above |

### 8.12 Call-site substitution, and the first RETARGET entry — as built

**Decision.** `CallSiteSubstitutionTransform(calls: Map[String, String])`: a member key naming the
**resolved callee**, a value that is an **expression template** with `{recv}` and
`{arg0}…{argN}`, and the call replaced by the template with the call's own receiver and arguments
spliced in. `CollectionsTransform` gains a second, orthogonal table — `retarget: Map[String, String]`
— for a type that **moves and is API-mapped nowhere**. Both default-empty; every lane byte-identical
without them.

**Why the seam had to exist.** The engine had three places to put code it must not translate
mechanically and all three are whole-declaration: `dropTypes` + `inject`, `dropMethods`, and
`MethodBodyTransform`. None can say *keep this method, rewrite this one call in it*, which is
`ENGINE-LIMITS.md` D7 — a base drops a member, a dependent still calls it, and the call sits at one
line of a method that is otherwise entirely mechanical.

**The holes are TREES, and that is the load-bearing choice.** `Tree.Opaque` gains
`holes: List[Term]` with a NUL marker no Scala source can contain; `holes = Nil` is the old node
byte for byte. Three consequences, each of which a string splice would lose: `StandardTraversal`
maps into the holes, so the **package rename — which runs last (§4.56)** — and every retyping phase
still reach a spliced argument; `Xref` records the usages inside them, so a symbol used only in a
hole is not dead; and nothing outside the emitter ever has to render a term, which a phase cannot do
at all. Rendering parenthesises a hole slightly more eagerly than `operand` does, in a helper of its
own — a hole's neighbours are whatever a policy author wrote, not the emitter's own output, and a
redundant pair of parentheses cannot change a meaning.

**Everything knowable at BIND time is known there.** The template parses once, into literal parts
and numbered holes, so `{arg3}` on a one-argument callee is a `Malformed` finding *before the
pipeline runs* rather than a compile error in generated Scala nobody attributes to a manifest entry
— DESIGN §8.1's rule for keys, applied to their values. Exactness is required and is §8.1's stated
asymmetry: a bare key naming two overloads is `Ambiguous` with the candidates listed, never one of
them picked, and `remove(Object)` does not rewrite `remove(int)`.

**`PolicyBinder.bindCallee` — the one binder addition, and why a declaration-side answer is not
enough.** A member `dropMethods` removed has no declaration symbol, so `bindMembers` correctly
answers "bound, nothing to point at" — which is the complete truth for a declaration and useless to
a phase that rewrites CALLS, i.e. for exactly the case D7 is about. The frontend interns the callee
from the REFERENCE anyway (`SpoonTir.methodSym`, with the declaration's own descriptor), so this
falls through to the symbol table when the index answers with dropped members only, keeps
`dropped = true` visible, and suppresses the `SyntheticTarget` refusal **on that path only** — its
structural test ("the frontend walked this owner and did not record this executable") cannot tell a
reference-side interning from an engine-minted member.

**Refusals are per-SITE and counted, and ONE predicate decides them.** A vararg spread among the
arguments (a positional hole names a term, not a group); an argument count this site does not have;
`{recv}` on a call with no receiver term; the callee used as a method VALUE (`Foo::bar` has no
argument list). `siteFault` is applied both by the traversal that rewrites and by the pass that
records the decisions, so a `SubstitutedCall` row — and the porter note derived from it — can never
claim a substitution that was refused.

**A key that BOUND and rewrote ZERO sites is its own finding.** Not an unbound key, and its
instruction is different: the callee is real, so either nothing calls it or an **earlier phase
already re-pointed those calls**. That is not hypothetical — `CollectionsTransform`'s universal
statics table maps `Collections.sort`, so an entry for it placed after that phase matches nothing,
with every count unchanged and the emitted code exactly what the port asked to change.

**`Decision.Kind.SubstitutedCall` is rendered as a porter note; its sibling `RedirectedCall` is
not.** A redirect swaps the callee and leaves the call's shape alone, so the emitted line still
reads as the Java's call with a different name on it and the diff carries the fact. A substitution
replaces the whole expression, and the source map then points at a Java line that says something
else — §4.575's case, the same one that makes `SubstitutedBody` rendered one level up.

#### The RETARGET table, and why it is not four more `typeMap` rows

`typeMap` says two things at once: *this type becomes that one*, and *its calls are rewritten
kind-aware and its slots bridged by `coerce`*. The second half is what a collection needs and
exactly what a retarget must not get, so a retarget entry joins `remap` and joins neither `kindOf`
nor any factory — which makes every kind-driven arm a no-op on it **by arithmetic** rather than by a
new guard in each one. A key that also appears in `typeMap` is refused rather than merged: two
answers for one type is a rewrite whose outcome depends on which table was read.

**The precondition the engine cannot check, and the policy author owes: the Scala target must be
usable wherever the Java source was.** `java.util.Comparator` → `scala.math.Ordering` is the worked
example and every property follows from Scala declaring `trait Ordering[T] extends Comparator[T]` —
the parent moves, `compare` stays structurally identical (it is `Ordering`'s one abstract member, so
a Java lambda stays SAM-convertible), and every slot that still says `Comparator` accepts the
retyped value with no bridge. Where that relation does not hold the seam is a `coerce` boundary and
the type belongs in `typeMap` with a kind and a factory. This is also why the retarget stays out of
`mappedTypes` / `retypedTargets`: those feed the closure and boundary checks, and a retarget has no
boundary by construction. It IS in `surfaceFingerprint` — a base whose `Comparator`s became
`Ordering`s and a dependent whose did not emit signatures that cannot meet (§1.5).

#### The comparator call-site table: RIDE, EXTEND — or neither

The question left open at the design stage was whether `Collections.sort(xs, c)` / `Arrays.sort(a, c)` become M4
config entries or new arms in `CollectionsTransform.staticRewrite`. **Measured, the answer is
neither, and §8.9's statics table gained no arm:**

- the template language **can** express the shape — `{arg0}.sortInPlace()(using {arg1})`; a `using`
  clause is ordinary text around a hole — so no extension of either mechanism was needed;
- the **shape is refuted**. `scala-cli compile --scala 3.8.4`: *value sortInPlace is not a member of
  scala.collection.mutable.Buffer[String]*. `sortInPlace` is a `mutable.IndexedSeqOps` member and
  `java.util.List` maps to `Buffer`. The substitution is performed exactly as asked, no count moves,
  and the POLICY is simply wrong — which is the shape of finding this seam will keep producing;
- after the retarget the existing `JavaCollections.sort(xs, cmp)` arm is **already correct**, because
  its parameter is a real `java.util.Comparator` and an `Ordering` is one. Verified by compiling the
  emitted probe.

`Arrays.sort` ships no entry either, and the reason is §4.4's family rather than expressibility: the
idiomatic `scala.util.Sorting.stableSort` takes a `ClassTag` as well as an `Ordering` and a
positional template cannot name a **summoned** argument, while `quickSort` has the one using
parameter and is **not stable** where Java's `Arrays.sort` is guaranteed to be. Trading a documented
guarantee for legibility is not a trade a seam may make silently.

**Rejected.** Rendering the template to text at the phase — a phase cannot print a term, and the
result would be the one region of the program no later phase can see. A `Tree.Spliced` node of its
own — every broad `Term` match would need an arm, and the ones written with a catch-all would miss
it silently, where a fourth field on `Opaque` is compiler-forced at the two positional patterns that
exist. A printable hole delimiter — it needs an escape grammar, which is a second parser over text
the engine deliberately does not parse. Deciding the overload from the call's argument shape — §8.1
measured that alternative at 118 `Ambiguous` out of 263.

### 8.13 The merge contract — how a parameterised phase's POLICY composes across manifests — as built

**Decision.** A phase may declare `MergeablePolicy` (`api`, beside `SurfacePolicy`, which it
refines): one method, `mergedWith(later: Phase): Either[String, Merged]`, answering *how MY table
composes with a nearer manifest's instance of me*. `PortManifest.surfaceFold` folds the policy chain
through it — same `Phase.name`, base's pipeline POSITION preserved, the merged instance replacing
the base's in place. A phase that declares nothing keeps exactly today's behaviour: both instances
stay in the effective pipeline and `ManifestAgreement` reports the pair as a fatal
`SurfaceDivergence`. `TypeRedirectTransform` declared the first merge, `NullabilityTransform` the
second and `GlobalsToImplicitsTransform` the third; every other parameterised phase keeps the
no-merge default, deliberately (below).

**What this closes.** `ENGINE-LIMITS.md` D9: a (b) phase configured in a BASE manifest was one no
dependent could ever configure, so the libGDX base could not gain its first `TypeRedirectTransform`
while `ashley` and `screens` each had one (1 fatal `SurfaceDivergence` each), and the escape of
handing the dependents' entries to the base was closed from the other side by D1's published-map
contract (`BaseMapStale` → 309 fatal base-surface gaps). Stage P's P1 was blocked on exactly this.

**Every parameterised phase declares its OWN answer, and that is the whole reason this is a
contract rather than a union.** A `Map` of independent keys unions; an ORDERED list of forwarders
does not (the order is policy); a first-match table does not (a later entry is shadowed, not added);
a `RuleScope` is a set that composes one way for `Only` and the opposite way for `Everywhere`. An
engine-side "merge the maps" would be right for one phase and silently wrong for the next, which is
CLAUDE.md §1's failure mode with the policy in the engine's hands instead of the library's.

**The merge is REFUSED, never approximated.** `Left(why)` means same key, different value — and the
refused pair stays in the effective pipeline, so the divergence detection that already exists fires
on it unchanged, now carrying the phase's own sentence for why. Three obligations fall on an
implementor, all stated on the trait because none is checkable from outside:

- the result must preserve BOTH inputs' behaviour on their OWN keys, or refuse — `SurfaceMissing`
  stops firing for the base's absorbed instance on the strength of that promise;
- the merge must be PURE and DETERMINISTIC — the base's own `effectiveSurface` is folded by the base
  and re-folded by every dependent, and D1's freshness comparison is between those two computations;
- `surfaceFingerprint` must move whenever the merged table differs from either input, or a merge that
  changed the surface publishes a digest saying it did not.

#### …and a refused pair STOPS THE RUN before any phase runs — the refusal has to be load-bearing

"The refused pair stays in the effective pipeline, so the divergence detection that already exists
fires on it unchanged" was true of the FINDING and false of the RUN, and the gap between those two
was invisible for as long as merging has existed. `Pipeline.order` sorted a `name -> phase` map and
ended in `out.toList.map(byName)`, so of two same-name instances the LATER one ran and the earlier
one was silently dropped. Measured on the first refused merge to reach production: a base's entire
`globals->implicits` holder never ran for one module — 0 decisions, no error, no check count, no
finding — while the fatal finding reported beside it was about a DIFFERENT thing and would have read
identically had the pipeline been correct (`ENGINE-LIMITS.md` CT9 Face B).

Two changes, and they are two because either alone is wrong:

- **`Pipeline.order` orders INSTANCES.** An ordering edge NAMES a phase and a name may stand for two
  instances, so an edge to a name is an edge to EACH of them — "after X" means after every X, the
  only reading that cannot silently under-constrain. Ties stay stable in declaration order and
  successors are still visited in name order, so a pipeline with distinct names orders exactly as it
  always did (every port in the corpus: 0 members changed).
- **A refused pair is FATAL BEFORE THE PIPELINE.** Ordering instances alone would replace a silent
  DROP with a silent DOUBLE APPLICATION: two configurations of one phase, both rewriting one program,
  in whatever order the fold left them. The refusal is the engine saying it does not know how to
  compose them, and running them anyway is the approximation this contract exists to refuse. So
  `ManifestAgreement.surfaceGate` is `PortRun.execute`'s first act after anchoring the report paths —
  nothing parsed, nothing emitted, and the message carries BOTH instances' policy fingerprints, which
  is the pair a reader has to reconcile and precisely what the silent drop made unreadable.

The gate shares `statik`'s body (`surfacePairs`), so "what is a refusal" has one derivation and the
gate and the report cannot drift. It is the only manifest finding that runs early, and the criterion
for that is stated rather than assumed: every other one describes EMITTED TEXT an operator reads
beside it, and this one describes the pipeline that is about to run.

#### …and "stable in declaration order" is the tie-break GLOBALLY, which a FIFO could not give

Every port places a phase whose predecessor no `runsBefore` can spell by writing it earlier in
`surface` — `BeanPropertyTransform`'s own doc says exactly this about the opaque-type phase, whose
name embeds an FQN. That guarantee was not delivered. Kahn's algorithm over a QUEUE appends a node
when its last predecessor is processed, so a CONSTRAINED phase that only just became ready lands
AFTER every unconstrained phase declared later — and the declaration order between them is silently
inverted. On the libGDX base, `CollectionsTransform` is written SECOND of eleven, behind one
`runsBefore` edge from `bean-properties`, and ran ELEVENTH.

The failure only becomes visible when somebody ADDS a phase, and then it is the worst shape a defect
can have here: two phases whose `run` returns its argument, prepended with a `runsBefore` edge onto
an existing phase, moved a port's `collection-boundary` count 22 → 20 and two member digests. An
inert phase that is inert on the TREE and not on the PIPELINE, with the evidence landing on a check
that has nothing to do with it.

So `ready` is a **min-heap on declaration index**, and the result is the unique topological order
that is lexicographically smallest by that index: every phase runs as early as its constraints
allow, in the order the port wrote them. Measured across the corpus — **0 member digests on all
fifteen ports**, every error baseline and every suite outcome unchanged, and ONE moved count: the
libGDX TEST lane gains one `collection-boundary` `OpaqueEgress` REVIEW row, because the collections
phase now takes its egress census over the tree the port asked it to see rather than over the one
nine later phases had already moved.

#### …and the EQUAL pair is the third answer, because ordering instances changed what "equal" costs

Face B's two changes made the refused pair loud and left the pair beside it silent. `SurfaceFold.of`
appended a same-name instance whenever the fold declined to merge it — including when the two
fingerprints were EQUAL, where it deliberately recorded no refusal, because equal policy is not
drift and reported nothing before merging existed. That append was free for as long as
`Pipeline.order` keyed phases by NAME: one of the two ran. Ordering INSTANCES turned it into *the
phase runs twice, over one program, with one policy* — harmless only if that phase's rewrite happens
to be idempotent, which is a property of the phase and not of any contract. It survived review
because the one production shape it had, `ClassTableTransform`, IS idempotent, and `PortRunSpec`'s
green negative was pinned on that accident rather than on the pipeline.

So the fold answers a same-name pair three ways, not two, and the third is a DEDUP: two instances a
phase's `MergeablePolicy` composes are one merged instance; two the engine can prove EQUAL are one
instance, the base's, at the base's position — the pre-CT9 semantics, now stated rather than emergent;
anything else is two instances and a fatal `SurfaceDivergence`.

**And "equal" is only sayable of a `SurfacePolicy`.** `PortManifest.fingerprint` is NAME-ONLY for a
phase that implements neither contract — the blind spot its own scaladoc documents — so two
differently-configured instances of such a phase render identically, and the dedup above would then
pick one policy of two and drop the other in silence, which is Face B restored under a new name. The
engine cannot see inside a phase it was not told about, so it says so: `Cause.Unverifiable`, fatal at
the gate, whose fix is one line in the phase. The asymmetry runs the safe way — an equal pair the
engine can VERIFY is admitted silently, one it cannot compare is refused loudly, never the reverse.

**The report had the same hole, from the other side.** `surfacePairs` derived "is this a pair" from
`fingerprint(ps).distinct.size > 1`, so a pair whose fingerprints render identically produced NO
finding — exactly the unreadable pair, which is the one the engine understands least. The criterion
is now TWO INSTANCES IN THE EFFECTIVE PIPELINE, full stop: the fold has already collapsed every pair
it can prove equal, so a name surviving it twice is a pair by construction, and the report reads the
pipeline instead of re-deriving the fold's judgement from strings. Where the two fingerprints do
match, the message says so out loud — `x vs x — EQUAL AS RENDERED, which is not evidence of
agreement` — because a reader handed `x vs x` would otherwise conclude the check had misfired.

Zero movement across all thirteen ports, for the reason Face B measured: no port in the corpus
declares one phase name twice.

#### The D1 contract: the base is the base AS THE BASE RAN IT

This is the half that had to be got right or refused, and the shape that gets it right is not a
guard — it is WHERE the fold runs. `surfaceFold` folds `policyChain`, which is *this* manifest's
chain, so a base manifest `b` folds `b.policyChain` and a dependent folds `[…b, this]`. The
dependent's added phase is not in `b.policyChain` and therefore cannot reach `b.effectiveSurface`.
`PortRun.basePolicyFingerprint(b)` — the value `PortMap.freshness` compares the base's published
`policy=` against — is computed from `b.effectiveSurface` and is byte-identical before and after
this commit. **Only the dependent's EFFECTIVE pipeline holds the merged phase**, which is exactly
the sentence D9 said there was no place for.

It composes down a chain for the same reason: for `a → b → c`, `b` publishes `merge(a,b)` and `c`
folds `merge(merge(a,b),c)`, so the absorbed input at `c`'s last step IS the fingerprint `b`
published. The fold records every absorbed fingerprint for that reason, and `SurfaceMissing`'s
"present in the base, absent here" test reads `mySurface ++ absorbed` rather than `mySurface`.

**A module that inherits NOTHING has no fold to read, so the same question is asked of the phase.**
`mirroring` states the shared policy in full and is checked against the base rather than inheriting
it (`PortManifest.inherit`), so a mirroring module that writes ONE instance holding both tables has
no `absorbed` entry and would be `SurfaceMissing` for a phase it demonstrably runs. The containment
test is `bases.mergedWith(mine)` leaving `mine`'s fingerprint unchanged — mine already holds
everything the base's does — asked through the phase's own `mergedWith` precisely so there is no
second notion of containment to keep in step with the first. A module that restates the base's table
WRONGLY still fails, on the key it got wrong, because the merged fingerprint then differs.

`surfaceFold` is a **`lazy val`**, and that is not an optimisation. A merged phase is a NEW instance
holding a run's mutable binding state; recomputed per call, the instance the pipeline ran would not
be the instance whose `policyReport` the run reads — the same failure `PortManifest.substitutions`
is a `lazy val` to avoid, one layer up.

#### The `governs` intrusion — refused, and the criterion is not PREFIXED (first correction of two)

The dangerous shape the merge newly permits is a dependent adding a key that edits the SHARED
surface: the base emitted a type mechanically, and a dependent quietly re-points every reference to
it, so the two ports each compile alone and cannot compile together — §1.5's failure with a merge as
the back door. So the fold screens each subject the later instance ADDS against the bases' `governs`
claims and refuses with a fatal `SurfaceIntrusion`, counted, naming the base and the subject.

**A bare prefix test is the wrong criterion and would have refused the one port the mechanism
exists for.** `ashley` redirects `com.badlogic.gdx.utils.ReflectionPool`, which is inside libGDX's
`governs = com.badlogic.gdx` — and is CORRECT, because the base drops that type and supplies
NOTHING at its name, so there is no shared surface at it to edit, and re-pointing references at a
replacement the dependent ships is precisely what `TypeRedirectTransform` was built for. The honest
criterion is therefore *inside a base's claimed namespace AND not accounted for by that base's own
policy* — the base's own instance of the same phase already saying the same thing (which contributes
no added key at all), or **a drop with NOTHING STANDING AT THE NAME**. §4.56's rule applies to the
claim as it applies to every prefix: `covers` cuts only at a separator.

**"Dropped" is not that criterion; it approximates it, and the approximation is wrong in exactly one
place.** A drop and its replacement are two decisions (§1.5), and a drop WITH an `inject` puts a
FILE at that FQN — shared surface as much as an emitted class is, and the thing every dependent
compiles against. A dependent re-pointing its references away from the base's shim to a type of its
own produces two ports that each compile alone and cannot compile together, which is the failure
this screen exists for, silently admitted. So the admission asks the base whether it SHIPS anything
at the name (`PortManifest.shipsInjectionAt`), through `renamed` — the drop key is UPSTREAM and the
shim's FQN is EMITTED, and a direct comparison would never fire on a renaming port (§4.56, the same
trap `PortMap`'s `Substituted` fell into). A root that does not exist supplies nothing, which is not
leniency: it is the answer the RUN gives, since its copy loop skips such a root and no file lands.
Measured over the corpus: **the one production case is unaffected** — libGDX's overrides hold no
`sge/utils/ReflectionPool.scala`, so ashley is admitted for the honest reason rather than by the
approximation, and screens' ten guacamole entries were never inside the claim at all.

A subject is read off a policy key as its leading FQN cut at `#` — the convention `ManifestAgreement`
already uses for `dropMethods` keys, one body (`MergeablePolicy.subjectOf`), so a phase does not
answer the question twice.

**The screen runs on EVERY dependent-declared instance, merged or not** — and it did not, for one
checkpoint. Scoped to the keys a merge ADDS, it lived inside the `Right(Merged(…))` arm, so the one
shape with the most freedom never reached it: a dependent declaring a phase **no base has** is
appended to the pipeline whole, which is one instance (no divergence to report) and no merge (no
`added` to read), leaving every type the base emits mechanically available to re-point. So
`MergeablePolicy` declares `subjects` — each key's leading FQN, through the same `subjectOf` — the
fold screens the no-counterpart arm with it, and `ManifestAgreement.statik` derives a
`SurfaceIntrusion` from any refusal the divergence arm did not already report (a refused merge has
two fingerprints and is reported there; an unmerged intrusion has one and would otherwise be
silent). The classification text always stated the rule unconditionally; the run now enforces it
unconditionally. Measured: the corpus is unmoved — ashley's redirect merges with the base's and is
admitted by the drop, screens' ten entries are outside `com.badlogic.gdx` — which is exactly why
nothing had noticed.

#### …and "not accounted for by the base's own POLICY" is still not the criterion — the base's OUTPUT is

The section above corrected the criterion once, from `dropped` to *nothing stands at that name*, and
the correction was made **inside the manifest**: a drop is a manifest fact, an `inject` root is a
manifest fact, and both were reachable from a pure function of manifests. That is the whole of what
this screen could see, and it is not the whole question. **A drop is a statement about a type the
base HAS. It says nothing about a name the base has never heard of.**

Which is the shape a library's own test module is in. libGDX's suites are declared *inside*
`com.badlogic.gdx` — `AnimationControllerTest` shares a package with `BaseAnimationController` — so
no prefix separates the two modules, the base's `governs` claim covers the dependent's own
declarations, and the base does not parse `gdx/test` at all. The one `selfSupplied` entry CT7 exists
to make writable was refused as a fatal `SurfaceIntrusion` naming a type the base has never seen,
with a sentence — *"which `libgdx-core` emits mechanically"* — that is simply false about the program
(`ENGINE-LIMITS.md` CT9 Face A). A dependent with a namespace of its own is unaffected, which is why
`gdx-vfx` merged on its first production run and libgdx-test did not: the refused case is exactly *a
module whose own declarations live in the base's namespace* — a test module, a split package, a
`com.foo.internal.impl` sibling shipped as a second artifact.

**So the criterion becomes: inside a base's claim, AND the base EMITS a declaration at that FQN, AND
not accounted for by the base's own policy.** The engine had already solved this once, one artifact
over: `ManifestAgreement`'s substitution half works from UNIT ORIGINS precisely because a prefix
cannot separate these two modules, and the base's PUBLISHED PORT MAP is where emits-facts live (the
D1 contract, §8.3). The map answers in three ways and each is the honest one:

| the base's map, at that FQN | means | screen |
|---|---|---|
| an entry that is not `Dropped` | a class, a rename of one, or an injected replacement stands there | REFUSE — all three are shared surface |
| an entry that IS `Dropped` | the map's own words for "nothing stands at that name" | ADMIT — §8.13's admission, now read off OUTPUT rather than approximated from a drop |
| **no entry at all** | the base declares nothing there | ADMIT — this is CT9 Face A |

`Kind.BaseSurfaceAbsent` is the same statement read in the other direction, and it is why the third
row is not leniency: a fresh map is a claim about the WHOLE of a module's output, and a shared type
missing from one is already fatal.

**Staleness is answered by D1's existing machinery and nothing new.** `BasePort.map` is `None`
exactly when no map was published and when one was proven STALE — the two share a path deliberately
(§8.3) — and the screen then falls back to RE-DERIVATION, which is the criterion that shipped before
it could read a map: *the base emits it unless its manifest drops it and ships nothing at the name*.
That fallback is strictly more REFUSING than the map's answer, which is the safe direction for a
screen, and it is already reported as weaker by `BaseMapMissing` / `BaseMapStale` with "run the base
port once" as the fix. **A dependent that runs before its base ever has therefore behaves exactly as
it does today** — it refuses, loudly, and is told what to run. No new freshness notion, no new
finding kind, and no silent admission bought with an absent artifact.

**And the screen therefore MOVES: the fold stops refusing, and reports CANDIDATES.** A port map is a
run artifact; `SurfaceFold` is a pure function of manifests and must stay one (that is the whole
argument for folding on the manifest rather than in the run, two sections up). So the fold does what
a manifest can do — name every subject a nearer manifest ADDS inside a base's claim that the base's
own manifest does not account for — and `ManifestAgreement`, which already holds the `BasePort`s,
applies the emits-fact and derives the finding. Three consequences worth stating:

- **an intrusion no longer un-merges.** A candidate the map clears merges normally, which is the
  point; a candidate the map confirms is fatal at the gate, before any phase runs, so what the
  pipeline holds for a doomed run does not matter. `SurfaceFold.Cause` is back to the two causes that
  really do leave two instances — `NoContract` and `Conflict`.
- **the fold's `refusals` and its `intrusions` are different lists because the reader's next action
  differs**: reconcile two values, or stop editing a base's surface.
- **one finding per phase**, naming the first subject and counting the rest, exactly as before — a
  dependent's manifest mistake is one mistake however many keys it touches.

#### The policy report follows the KEYS, not the instance

`PortRun` holds a module to its OWN keys — a §1(b) finding says "fix this key in the library's
manifest", and an inherited key lives in the base's. For drops and renames that filter is
key-level; for PHASES it was instance-level, which was a sound proxy only while no instance was
shared. After a merge the dependent's own declared instance never runs and never binds, so reading
its `policyReport` reports nothing at all — a typo'd key silently no-oping, which is exactly the
thing `PolicyReport` was built to close (`ENGINE-LIMITS.md` §9). So `ownPhases` resolves each
own-declared phase to the effective instance that ABSORBED it, and the merged instance's findings
are filtered to the subjects the fold recorded this manifest as having contributed. A port with no
merge resolves every phase to itself and the filter is absent — byte-identical.

**And a finding a filter reads by SUBJECT has to carry one.** The filter takes `subjectOf(f.key)` —
the leading FQN, cut at `#` — so every finding a mergeable phase can produce must be keyed on a
member key or a type FQN, never on a fragment. `TypeRedirectTransform` keyed its member-level
`Malformed` findings by the bare SEGMENT (`dispose()`), whose subject is itself; it matched no
contributed set and the filter DROPPED it, so a dependent's own typo'd `memberRenames` entry was
silently unreported on every merged phase — the one seam this whole section exists to keep honest,
failing in the direction nothing measures. They are keyed `owner#member` now (`MemberKey.spell`, the
same splice `parseIn` performs, in the file that owns the grammar); the parse's own MESSAGE still
names the segment the author wrote, because that is the string they have to edit.

#### The `.conf` path composes through the SAME fold

Nothing was added to it, and that is the result rather than an omission: `base = "…"` already ends
in `base.extendedBy(own)` (`PortConfig.readManifest`), and `extendedBy` builds the chain the fold
reads. A base conf and a dependent conf that each declare `redirects { }` now merge exactly as two
Scala manifests do. Had the fold been placed in `PortRun` instead of on the manifest, this would
have been a second truth on the second path — D9 notes the hole was identical on both, so the fix
had to be.

#### …and the merge is the only way a DEPENDENT can place a phase EARLY — measured

The fold puts a merged phase at the BASE's position, which reads as a detail about tables and is the
whole of how a dependent gets a phase to run before one it did not write. An UNMERGED dependent phase
lands at the END of the effective surface, and `Pipeline.order` is a min-heap on declaration index —
so a `runsBefore` edge from there does not merely constrain the phase it names, it **postpones that
phase past every unconstrained phase in between**. That is `Pipeline.order`'s own recorded failure
shape one era later: the FIFO half was fixed by the heap, and this half the heap cannot fix, because
the constraint is real and the position is wrong.

`member-rename` (§8.5) measured it. Declared last in `sge-visui`'s surface with a
`runsBefore("java-collections->scala")` copied from `bean-properties` — which needs that edge and this
phase does not — it pushed `type-redirect` past `globals->implicits`; the threading analysis then read
a PRE-redirect ancestry, one `unconstructed-thread` warning stopped firing, and **`context-seam` moved
42 -> 41 with ZERO emitted bytes changed and the phase SKIPPED for the measurement**. The fix is the
one this section is about: libGDX's base declares an EMPTY `MemberRenameTransform` at the position a
rename belongs (ahead of its own `disposableRedirect`), the dependent's table merges into it, and the
`type-redirect` edge is satisfied by declaration order and costs nothing. Every other libGDX port pays
one fingerprint field — measured as `policy=` alone moving in the base's published `port-map.tsv`, at
0 rows, 0 errors and every count flat.

The general rule: **an edge added by a late-declared phase is a REORDERING of the phases in between,
and an inert phase can be inert on the tree and not on the pipeline.** State the phase's position, then
add the edge.

#### Which phases declare a merge, and which keep the default

`TypeRedirectTransform` declares one: `redirects` and `memberRenames` are key-independent maps, a
key present in both sides with the same value is agreement and with a different value is refusal —
**with the member half compared through `MemberKey.parseIn`, never as a map key.** `dispose` and
`dispose()` are two strings and ONE member (a bare key is every overload, the nilary key is one of
them), so a raw-key intersection merged `dispose -> close` with `dispose() -> shutdown` cleanly and
the drift arrived one layer down as `MemberRenamer`'s NON-FATAL two-claimants refusal — a
`PolicyIssue` where `mergedWith`'s stated obligation ("preserve both inputs' behaviour on their own
keys, or refuse") owes a fatal `SurfaceDivergence`. A segment that will not parse falls back to its
own text, so a refusal never depends on a parse succeeding; over-refusal is the safe direction for
`OverrideGraph`'s reason, and two spellings agreeing on the target are still agreement,
and `ExternalSurface` is unioned because it is ENGINE knowledge about the JDK rather than policy (it
is not in the fingerprint, and cannot be — two ports that know different amounts about a platform
type still emit the same signatures).

**`NullabilityTransform` declares the second one, and it is the first policy that is not a MAP** —
which is what turns "the phase answers, not the engine" from an argument into a measurement. Its
three tables compose three different ways *inside one phase*:

| table | composes | why |
|---|---|---|
| `annotations` | UNION | each FQN independently selects the declarations it marks; nothing about one entry changes what another does, so both inputs keep their behaviour on their own keys by arithmetic |
| `target` | must AGREE, else REFUSE | it is not a key set, it is the SHAPE every retyped declaration takes. `T \| Null` and `Nullable[T]` are two emitted signatures for one member, so a "merge" of them is a choice |
| `scope` | UNION of ENTRIES, in BOTH directions — REFUSE across directions | below |

**The scope is the part worth writing down.** An entry means *hold this back* under
`Everywhere(except)` and *move this* under `Only(include)`, so honouring every entry either module
wrote is the union of the sets **either way** — and the effect on the covered REGION therefore runs
in opposite directions: `Everywhere` shrinks as excepts accumulate, `Only` grows. A merge rule
phrased as "compose the region" would have had to pick one of those and would have been silently
wrong for the other; phrased as "honour every entry" it is right for both, and it is exactly
`SurfaceFold`'s first obligation ("preserve both inputs' behaviour on their OWN keys") read
literally. A base `Everywhere` and a dependent `Only` **refuse**: `Only` says as much by what it
OMITS as by what it lists, so no entry set preserves both — and that includes the DEFAULT
`Everywhere(Set.empty)`, because "the whole program" is a direction and not the absence of one.

**PER-MODULE scope DIRECTIONS are inexpressible in one merged instance, and the refusal is the honest
rendering rather than a limitation to be worked around** — one instance carries one `RuleScope`, a
`RuleScope` carries one direction, and a "merge" that kept both would have to be two instances, which
is precisely the `SurfaceDivergence` the fold exists to remove. **P5/P6 must not re-litigate this**:
a module that needs the other direction spells its base's scope the way its base spells it.

**Its `subjects` are the annotation FQNs AND the scope entries**, on the trait's own instruction to
over-approximate. The scope half is the one that carries the failure: a dependent that scopes out a
type its BASE emits leaves its own overrides of that type's annotated members holding the upstream
type beside a parent the base emitted as `T | Null` — half an override pair, which is the shape
§11.17 measured when a scoped-out parent sat beside a retyped child, and precisely the "two modules
that each compile alone and cannot compile together" the intrusion screen exists for.

**`GlobalsToImplicitsTransform` declares the third, and it is the first whose merge needed a NEW
VALUE rather than a rule** (`ENGINE-LIMITS.md` CT8). The rule alone is easy to state — holders union
by holder FQN, `sites` and `selfSupplied` union refusing same-key-different-value, everything else
must agree — and stating it is not sufficient, which is the finding. A `sites` entry belongs to a
HOLDER, so a dependent that must name one would have to restate the holder; and with the context
type, the member map, the attachment mode, the read shape and the boundary default all
agree-or-refuse, restating the holder means restating the base's whole member map in the dependent's
manifest. That is §1.5's prohibition arriving through the door the merge opened.

So the split is a value: `ContextHolderExtension` carries `holder` plus the per-declaration half and
has **no field in which the shared half could be restated**, and `ContextHolder.sharedSurface` is the
other side of the same line, on the policy rather than spelled twice in the phase. The config front
door says it the same way — a `holders` entry **with no `context` block IS an extension**, chosen
because `context` is the one key with no default, and any shared-surface key written inside such a
block is an unread key the loader already refuses. `effectiveHolders` folds each extension into the
holder of its FQN and is what binds and runs; a DANGLING extension — one naming a holder nothing in
the chain declares — is a counted `Malformed` derived from the policy, because its own keys would
bind perfectly and what is missing is not a program fact at all.

Two details that are not arbitrary. `promoteToClass` and `scope` are in the SHARED half rather than
unioned: both name types, but both change what the threading does to declarations the base emits,
and the phase has no per-module direction to offer (the `RuleScope` argument above, one phase over).
And `subjects` includes the HOLDER FQN of an extension as well as of a holder, which looks like it
would refuse every dependent and does the opposite: the base holds that subject too, so
`o.subjects -- subjects` reports it as nothing ADDED — while a dependent declaring the phase with a
holder in the base's namespace and NO base counterpart is screened whole and refused, which is the
shape that has the most freedom.

Every other parameterised phase keeps the no-merge default, and the reason is uniform:
none of them has a second consumer yet, and a merge rule written without one is a guess that will be
discovered wrong by the port that first needs it — the same argument §8.5 makes about `memberRenames`
having waited for its second consumer. What each will need when that day comes is not the same
answer, which is the point: `StaticForwarderTransform` holds an ORDERED `List[Forwarder]`;
`CollectionsTransform` holds a `RuleScope` too, and would want the rule above — but it has no second
consumer, and a rule copied ahead of one is the guess this paragraph is about;
`MethodBodyTransform` and `CallSiteSubstitutionTransform` hold whole replacement bodies, where two
manifests naming one key is a conflict no union can resolve. Until then, two instances of any of
them remain the fatal `SurfaceDivergence` they are today, which is the correct answer for a
composition nobody has designed.

**Rejected.** A union in the engine, keyed on `Map` — right for two of eleven phases and silently
wrong for the rest (above). A per-instance `Phase.name` so two instances never collide — D9's
recorded near-miss: it defeats the drift check for REAL drift, which is the only thing the check is
for. Merging at `PortRun` rather than on the manifest — a second truth on the `.conf` path, and it
puts the merged pipeline where `ManifestAgreement` (a pure function of manifests) cannot see it.
Letting the intrusion screen pass and reporting it as advisory — a dependent that edits the shared
surface produces two ports that cannot compile together, which is the definition of fatal here.

#### The screen the FOLD cannot run: a key that names NOTHING

The `governs` screen above reads policy KEYS, and it is complete for every key that names what it
edits — a redirect names a type, a drop names a type, a scope entry names an FQN. **An ANNOTATION
FQN names none of the declarations it moves.** `org.jspecify.annotations.Nullable` is a third-party
jar's name, inside no base's claim, so the fold admits it — correctly, because the key itself edits
nothing. What it SELECTS is another matter: `NullabilityTransform`'s plan loop walks `Program.owned`,
which in a dependent roots on every unit in the program including the base's (`ENGINE-LIMITS.md` D2's
substrate note), so a dependent whose base's Java carries the same third-party marker retypes
declarations the base's own run emitted untouched. Two ports that each compile alone and cannot
compile together — §1.5's failure, through the one door a key-reading screen cannot watch.

**So the screen runs a second time, at PLAN time, against the DECLARATIONS.** A phase refuses a
rewrite when both halves hold: the declaration's unit is not one this run EMITS, and the key that
selected it is one THIS manifest contributed. Both are facts about the run and neither is derivable
from the `Program`, so they arrive as one value — `balticporter.tir.RunScope`, on the `PolicyBinder`,
which is already the object the run hands every `PolicyBound` phase before the pipeline starts.
`emits` is `PortRun.partitionUnits`, the same realpathed origin split every other owner question
uses (§5.4); `contributed` is `PortManifest.contributedSubjects`, which is `SurfaceFold.ownKeys`
where the fold merged this module's instance into a base's and the instance's whole `subjects` where
no base declares the phase at all — the shape with no constraint on it, exactly as the fold's
no-counterpart arm treats it. A phase this module does not declare is ABSENT from that map, which
reads as "no filter": every key it holds is a base's, and the base's own run applied it identically.
`RunScope.whole` — everything is emitted, nothing is scoped — is the default and is the truth for a
base port, so the screen is a no-op there by arithmetic rather than by a branch.

**Only the annotation half needs it, and that is a proof rather than a scoping decision.** A scope
entry names an FQN, so an entry that reaches a base declaration is *by construction* inside that
base's `governs` claim and is already a fatal `SurfaceIntrusion` at manifest time. The annotation is
the only key whose reach is not its spelling.

**The severity is NON-FATAL, argued.** The refusal has already made the emission correct — the base's
declaration keeps exactly the type the base's own run gave it — so nothing this port WRITES is wrong.
What is wrong is what its manifest SAYS: a nullability contract stated for a namespace it does not
own. A fatal finding would stop a run whose output is right; a silent one would leave the author
believing the annotation applies library-wide. One `policy` finding per KEY (never per declaration —
that reports one manifest mistake once per member of the base), carrying the count and the first
three subjects, is the honest middle. It reaches `policy` rather than `nullability-boundary` for a
structural reason, not a taste one: `NullabilityTransform.boundary` filters to the units the run
EMITS (D2), so a finding raised at a base declaration is dropped by the very filter that makes the
check correct — the refusal has no emitted site to hang on, and the manifest key is the only thing it
is really about.

**And the corollary the screen depends on: an empty `governs` switches the FIRST screen off.**
`PortManifest.claims` is `false` for every FQN when the set is empty ("no claim", never
"everything"), so a base that states policy and claims no namespace admits every subject every
dependent adds — silently, because a screen with nothing to screen against cannot be told from one
that passed. `ManifestAgreement.Kind.BaseNamespaceUnclaimed` reports it, non-fatally, from the
dependent's side (the run that holds both manifests) with the fix named in the base's. The empty
manifest that declares "this resolution root is not a ported module" states no policy and reports
nothing — `PortManifest.declaresPolicy` is the one line that separates the two.

### 8.14 The RETYPING contract — a phase declares its LANE, the pipeline observes its REACH — as built

`CLAUDE.md` §1 states what a retyping phase owes in prose, and four phases discharge it: every seam
the retyping opened and could not close is counted, with the §1 classification a bare typer error
cannot carry. Each of those four answers was written after a port hit the wall, and
`ENGINE-LIMITS.md` K5.6 recorded the residue of that process in one sentence — *a NEW retyping phase
can reintroduce this shape until it too asks the question*. Nothing could see a phase that had not
asked.

**Why it is invisible without a mechanism.** A retyping is position-blind, so both sides of nearly
every slot move together and the port compiles; there is no check, so no count moves; the emitted
text is exactly what the phase meant, so no member digest is wrong. The evidence arrives in another
repository, as a wall of `Found: … / Required: …` with nothing to classify it (§4.45).

**The two halves come from different places, and that is the design.**

- a phase DECLARES `Rewrite.accountedBy`: the check lanes that count its residue, as `Check.Name`
  symbols rather than string literals, so a renamed lane is a compile error and not a silently
  unwired claim;
- the PIPELINE OBSERVES `Patch.retyped`: the owned declarations whose `info` that phase changed,
  compared across the phase inside `Pipeline.runTraced`, which is the only place both symbol tables
  exist at once. A phase is never asked what it retyped — that is the one number it could be wrong
  about, or silently stop maintaining, and asking for it would put the mechanism's own population
  under the control of the thing it polices.

`rewrite-callsites` then reports two things: a phase that moved declarations and names no lane
(`Unaccounted`), and a phase naming a lane that did not RECORD in this run (`UnwiredAccounting`).
The second is `PortRun.RequiredChecks`'s guarantee for the lanes that exist only when their phase
does — which that set cannot express, because a lane required of every run and a lane required of
every run *that carries a phase* are different claims. It is the LAST check to run, and that
position is part of it: it asks whether a lane recorded, and a lane not yet called has not.

**It counts PHASES, never usages, and that is measured rather than assumed.** The generic form
`usagesOf(s) \ callSites` — every usage of a retyped declaration the phase did not also rewrite — is
not the four boundary counts and cannot become them: what makes a usage a seam is the type
comparison the four checks perform with their own `typeMap`, which §4.56 forbids reasoning without.
On libGDX core it is 3,045 usages against 152 counted seams (`ENGINE-LIMITS.md` K5.10). The scale
line the check prints recomputes both on every run, so the refusal cannot become prose that goes
stale.

**The wiring question takes an `Option`, and that is not defensiveness.** `CheckReport.record` is a
no-op when the artifact layer is off (§5.1: the gate is on the LAYER, and a run with no derivable
port identity has it off whatever the flags say), so `snapshot()` is then empty for EVERY lane. Read
as an answer, that reports every accounted phase in the engine as unwired — a finding manufactured by
a diagnostic switch rather than by the program, at exactly the fixtures (`testkit`, a §1(c) rule's own
harness) where a phase author would first meet it. `None` is "the question cannot be asked here"; the
`Unaccounted` lane is unaffected, because it reads the pipeline's own observation and no artifact
switch touches that.

**What the first run found.** Two retyping phases had never answered — `PrimitiveToOpaqueTransform`
and `TypeRedirectTransform`. Both hold a `PolicyReport`, which is why neither looked silent, and
naming `policy` in `accountedBy` would have been the suppression the lane exists to prevent: a
declared key that never fired is a different residue from a slot two sides of which disagree. They
stay counted, and the count is the work list — derived, not guessed.

### 8.15 The IDIOM layer — what licenses it, and the three lanes that hold it to that licence

**Every other layer in this engine derives its mandate from a DIFFERENCE**: java does X, scala does
Y, and the port must not silently do Y where java did X. `Differences` is that mandate as code, and
`catalog(unmechanised)` is the residue of it.

The idiom layer has no such mandate, by construction. An anonymous class implementing a
single-abstract-method interface and a lambda at that interface are the same value with two
spellings; a `getX()`/`setX(v)` pair and a `var x` are one property with two spellings. The faithful
translation already exists, already compiles and already behaves identically — so *why change
anything at all* has to be answered before the first transformer, or every future row is argued from
taste. The answer is:

> **An idiom transformer's mandate is the REFERENCE PORTS, read as this layer's Scala EVIDENCE SET —
> and read under §3.5, which means the SHAPE they emitted is evidence and the COUNT beside it is
> not an opportunity.** A transformer ships when a hand port of this same library wrote the scala
> form, when the delta between the two forms is ENUMERABLE, and when every member of that
> enumeration is either structurally impossible or counted. Nothing else licenses moving code that
> already means the right thing.

The narrowing is what makes the layer's small size reproducible rather than a verdict a reader has to
accept. The raw java populations look enormous — 1,545 bean-shaped getters, 1,650 null checks, 1,588
index loops — and the hand port converted about **14 %** of the first and **none** of the second. That
difference is not laziness: a hand port fixes every caller by hand in the same commit, where a
mechanical port moves whole families at once, and reading the first as an option for the second is
`ENGINE-LIMITS.md` K16's measured mistake (27 -> 47 errors). Read through the narrowing, ten candidate
families leave **two transformers with a mandate, one worth pricing and six refused with an
argument**.

**And ONE row ships against negative reference-port evidence, which the charter permits and must not
permit silently.** Both hand ports wrote the java anonymous class where scala's SAM conversion was
available — deliberately, which is the strongest negative evidence a reference port can give. That
row (`SamLambdaTransform`) ships on the other half of the charter alone: *the shape is strictly
smaller and the refusals are decidable*. When a row ships on that half, **its refusal population is
published before it converts anything** — which is what the census phases are for.

#### An idiom transformer is a PHASE, never a beautification backend

`CLAUDE.md` §6 admits one optional beautification backend — human-readable imports — and that is a
RENDERING choice: it moves no declaration, no symbol, no source-map row and no published surface.
Every idiom transformer changes a DECLARATION or a TERM in the TIR. §8.5's own rejected list settles
the closest case in one sentence: *rendering `getX()` calls as `x` without renaming symbols
desynchronises the surface from the source map, and the emitted surface is what an adopter consumes.*
So the beautification-backend question is answered NO for the whole layer, once, here.

#### The safety argument is a REFUSAL ENUMERATION, not a suite result

Suite outcomes are the strongest oracle this project has and §3 says to prefer them over any number
of further compile fixes. They are **not sufficient here**, and this repository has the receipts:
the dropped `static { }` blocks, the dropped `super(args)`, the 156 anonymous-class bodies, and K21
face 2 — where a framework read ZERO properties and the library's null-coercion then answered three
assertions out of four CORRECTLY from data that was not there. Every one compiled green and moved no
check count. And the oracle strength across the corpus spans two orders of magnitude, from a
637-test ported suite down to one port with no suite at all.

So for each transformer the set of behavioural differences between the java shape and the scala shape
is ENUMERATED, and each member is either (i) made impossible by a structural GUARD, (ii) made
impossible by the SHAPE the transformer emits, or (iii) COUNTED. **A transformer that cannot
enumerate its deltas does not ship** — the Hoare bar applied to a transformer rather than to a
lowering.

Arm (ii) is the cheapest and is worth naming, because it removes a guard rather than adding one. A
java anonymous class is a value whose type javac WROTE DOWN; a bare scala function literal is a POLY
EXPRESSION whose type the CALLEE decides. So `SamLambdaTransform` emits the lambda ASCRIBED to the
interface the anonymous class named, and overload resolution leaves the enumeration entirely: the
argument's type at every callee is what it was before the phase ran, so the candidate set and the
most-specific answer cannot move. Without the ascription the transformer would be standing in
`JS-C22`/`JS-C23`'s risk — the one place this engine knows it cannot follow javac — for no gain.

#### `idiom(converted|refused|residue)` — three lanes, and why not one number

The trivia family's argument, one artifact over. `idiom(refused) = 0` is a bar a run could hold by
CONVERTING NOTHING, and `idiom(converted) = N` says nothing about the population `N` was drawn from.
So the positive, the refusal population (one row per declined site, naming the GUARD, in §1's
grammar) and the unrewritten-usage residue are reported apart, from ONE log, with each row carrying
`kind=` so a lane stays readable per transformer. The DENOMINATOR is recomputed and printed beside
the numerator on every run, so it cannot go stale as prose can — chunk 18's shape.

Three things about the wiring that are not incidental:

- **the data comes from the PHASES, never from a second walk.** A check that re-derived *would this
  have converted* would be a second answer to the phase's own question, free to disagree with it —
  §4.6's one-mechanism-one-seam, and `ENGINE-LIMITS.md` K2.5's measured shape (five findings that had
  been misreading themselves for the life of a port). The refusal lane is only trustworthy because
  the phase holds both halves at the moment it files: a refusal whose guard the phase itself could
  pass is an ENGINE BUG, not a residue, and nothing else can tell the two apart;
- **all three are in `PortRun.RequiredChecks`**, required of every port including one with no idiom
  phase, carrying `JdkSurface`'s own argument: three rows of zero and a check that never ran are one
  row otherwise;
- **a phase DECLARES the kinds it files** (`IdiomPhase.idiomKinds`), so a kind whose phase ran and
  found nothing prints `0 considered` while a kind with no phase prints no row. Averaged into one
  line those two are indistinguishable, and the first is the number a reader needs: a census whose
  population fell to zero is exactly what a conversion regression looks like from here.

#### The layer is §1(a) and the RUN weaves it — there is no switch

Which types are SAM is a structural fact about a class file; whether a body is a single method is a
structural fact about a tree. There is no library policy in any of it, so there is no constructor
parameter, no `RuleScope`, no `SurfacePolicy` and no fingerprint — **and no on/off switch, because a
knob on an (a) is the shape §1 forbids**. `PortRun.idiomPhases` weaves them, exactly as it appends
the package rename, so they are outside every manifest `surface`: no fingerprint moves, no published
port map changes, and §1.5 owes nothing, which is right because there is nothing for two modules to
configure differently.

The one exception is the bean collapse, and it is not an exception to that rule: it is a refinement
of `bean-properties`' EXISTING per-key include list (§8.5), so its opt-in is the entry, not a boolean
beside a policy map.

#### …and a census is RETIRED the moment its transformer runs, by the same rule

A census is the transformer with the rewrite removed, so once the transformer is in the pipeline the
census is a SECOND ANSWER to a question the transformer already answers: an idiom phase files one row
per site CONSIDERED — `Converted`, or `Refused` naming the guard — and that IS the denominator. Two
phases at one position filing about one site double every row in the lane, which is §4.6's
one-mechanism-one-seam read in the report rather than in the tree. `SamLambdaCensus` was deleted the
commit its transformer was wired; `SamLambda.decide`, which both of them always called, is what
survives. A census is therefore a WAVE-0 ARTIFACT with a defined end, not a permanent second lane —
and the refusal rows it filed keep the same SUBJECT (the enclosing declaration) under the
transformer, so the wiring moves rows between lanes and moves nothing else.

#### A CENSUS IS A PHASE, AT THE POSITION ITS TRANSFORMER WILL OCCUPY

`CLAUDE.md` §5's dry-run rule read in the other direction, and it is the same failure either way:
**a phase measures what it is HANDED**. A dry run hands a phase untransformed input and under-counts
(measured: a warning lane priced at 1 by a dry run reads 25 in the live pipeline, `ENGINE-LIMITS.md`
CT7). A POST-PIPELINE census hands it a tree every surface phase has already moved, and over- or
under-counts depending on which phase moved what — and there is a live instance of the second
direction in this layer: a `CollectionsTransform` retarget moving `java.util.Comparator` to
`scala.math.Ordering` changes what a SAM conversion would ascribe to. A census asked at the end of
the pipeline would publish a denominator that is not the population the phase will meet, and the
transformer wave's blast would then be DISCOVERED rather than confirmed — which is the one thing a
census exists to prevent.

So the SAM and `return this` censuses take FIRST position, which is their transformers'; the bean
census is spliced immediately before `bean-properties`, whose own policy it reads. Two consequences
worth stating because they are what makes this affordable: the censuses are PHASES, so
`RequiredChecks` gains three CHECK names and not six; and the wave gate — **0 member digests on all
fifteen ports** — is what proves each is really inert at the position it took, which a post-pipeline
check could neither have been wrong about nor have proved.

#### What the SAM oracle is, and why it lives in the FRONTEND

"Is this interface a functional interface" is a fact about a CLASS FILE: JLS 9.8 counts the abstract
methods a type declares AND inherits, excludes `static` and `default` ones, and excludes anything
override-equivalent to a public method of `java.lang.Object` — which is exactly why
`java.util.Comparator`, with its `equals` redeclaration, is one. **Nothing in the TIR can answer it**:
the frontend interns an external symbol lazily and its MEMBERS only where the program references
them, so an interface whose single method the program never calls has no members in the symbol table
at all. A phase deriving the answer from what is interned would be reading what this run happened to
PARSE rather than what the class DECLARES, which is §4.56's rule and whose recorded failure mode is a
wrongful seal.

So `SpoonTir.samAnswerOf` asks it once, where the shadow model is, and the answer travels on the node
(`Tree.AnonClass.sam`). `Sam.Answer.Unreadable` is a FIRST-CLASS answer and never `false`:
`getTypeDeclaration` returns null for a type not on the classpath, which is normal and common, and
both answers lead to the same ACTION (leave the anonymous class) — which is precisely why the refusal
must be COUNTED, or a port whose classpath is incomplete reads as a port with no SAM sites.

One consequence is worth stating because it is easy to read the other way: the answer being computed
in the frontend makes it java's own BY CONSTRUCTION, and it does not relax the ordering constraint.
The transformer still runs before every retyping phase, because what that constraint is about is the
type the conversion ASCRIBES TO.

#### …and the §4.4-shaped face guard 4 could not see, which the fixtures found and the corpus has none of

Guard 4 is the `this`-binding difference: inside a java anonymous class `this` is the ANON instance,
inside a scala lambda it is the ENCLOSING class. Written as *does the body contain
`Tree.This(anonSym)`* it is right for a `this` used as a VALUE and blind to the whole of the other
half — because where `this` is the TARGET of a member access the frontend deliberately builds no such
node. Spoon reports the anonymous class as the receiver's type whatever the member's real owner is,
so trusting it would qualify half the corpus's references with a type that does not exist in the
emitted code; the fallback is a BARE reference that scala resolves LEXICALLY, exactly as java did
(`SpoonTir.thisOf`).

`this.toString()` in an anonymous `Runnable` therefore arrives as
`Tree.Ident(java.lang.Object#toString)` — no `This`, no `Select`, nothing a guard reading `This` can
see — and converting it prints the ENCLOSING object with a green compile and no moved count. Guard 3
has already established the body is exactly ONE method, so the members a bare reference can reach
that a lambda would re-resolve are enumerable and small: `java.lang.Object`'s public members, and the
anon's own SAM method reached recursively. Both are a bare `Tree.Ident` whose symbol's OWNER says
which it is, which is the structural test §4.56 asks for. Zero corpus sites; the fixture is the whole
evidence, which is `JS-E06`'s third commit again.

### 8.16 Per-location remediation SELECTION — a MENU the phase publishes, one word the port writes

`CLAUDE.md` §1 splits every rule three ways and the split is about MECHANISMS. It leaves a gap the
corpus keeps falling into: a difference with **no single right answer**, where the engine could
faithfully do either of two things and only the port knows which one it wants at THIS declaration.
Today a port has exactly two reachable answers — accept the residue finding, or write a §1(c) rule —
and the second is a compiled artifact, a `TransformFactory`, a `META-INF/services` line and a test
suite for a decision that is one word long. So ports accept, the residue lanes stay high, and the
engine's own `Remediator` prints the manifest line a human then pastes by hand.

The mechanism closes that loop from the other end. **The phase or check that MINTS a residue finding
declares the alternatives it could carry out; a port SELECTS one per location; the engine applies it
and both halves are counted.** Nothing about the mechanism moves into the manifest — the manifest
names a choice from a list the engine published, which is the shape a `RuleScope` and a
`verdictOverrides` entry already have.

The PLUMBING shipped with **no menu** — no phase and no check declared a remedy — so every port's
`remediation(resolved)` was zero, the surface fingerprint was byte-identical on a manifest that
selects nothing, and the mechanism's arrival was provably flat on all fifteen lanes. That was
deliberate, and §1(b)'s rule that an empty parameter must be a no-op still holds for a port that
selects nothing: the menus below add ids to a vocabulary, and a vocabulary nobody names moves nothing.
The first menus are the three BOUNDARY checks (`collection-boundary`, `context-seam`,
`nullability-boundary`), registered in `PortRun.CheckRemedies`, and they arrived flat on every lane.

**The KEY is a `MemberKey`, and the granularity is PER MEMBER.** `owner#name` or
`owner#name(P1,P2)`, in the upstream namespace — the one grammar `dropMethods`, `ClassTableTransform`
and `PolicyBinder` already speak, and the one that survives an upstream reformat by construction.
Per-member is not a new compromise: `Decision.declarationsUsing` records one row per (declaration,
kind) and never one per expression, `IdiomCandidate.subject` is `owner#member` and never the site's
own expression, and `Remediator.Suggestion` has been declaration-scoped since it was written. Two
consequences follow and both are decisions:

- a selection **BROADCASTS** across two sites of the targeted finding kind inside one member. The
  alternative is an occurrence index, which reintroduces line-number-shaped fragility one level down
  — the exact fragility `CheckReport.Finding` already refuses by excluding the line from its id. A
  member that genuinely wants two remedies at two sites is a feature nobody has needed, and it will
  be an EXPLICIT one when somebody does;
- an **overload set is not broadcast**. `PolicyBinder.bindMember` is what binds a selection, so a
  bare key naming two overloads is `Ambiguous` with both candidates rendered — they are two different
  members and picking one by position is what this grammar exists to stop. A key naming nothing is
  `NeverMatched`, a key outside the grammar is `Malformed`, and all three arrive through the same
  vocabulary every other policy key uses.

**A REMEDY is DECLARED by the mechanism that can carry it out** (`balticporter.tir.Remedy`,
`RemedySource`), with four fields and none of them optional. The `id` is a globally-unique kebab slug,
which is what lets the selection key stay flat — `"owner#member" = "remedy-id"` and never a compound
`(lane, kind, id)`; the `lane` and `kind` name the residue row it drains, without which a resolution
is a verdict with no denominator; `emissionAffecting` is what puts a selection in §1.5's MUST-agree
column, declared rather than assumed so that the day a remedy only re-files a finding, THAT one can
move to the not-inherited column beside `verdictOverrides`; and `fix` is §1's classification of the
remedy's own CODE, which is `balticporter.catalog.FixKind` reused rather than a second enum with the
same three cases.

**Two vocabularies, and the difference between them is the whole staleness story.** The ACTIVE set is
assembled per run from what the run HOLDS — its pipeline's phases plus `PortRun.CheckRemedies`, which
is `Rewrite.accountedBy`'s shape one level up: a claim each source makes about itself, gathered by the
run rather than kept in a table somebody has to remember to edit. The KNOWN set is that plus whatever
the classpath DECLARES without being constructed, which is why `TransformFactory.remedies` exists
beside the phase's own: a factory speaks for the phase it would build, so the config loader can tell
a TYPO from a port that picked a real remedy and forgot to enable the phase. Refused at load, the
second reads as the first and sends its reader hunting for a spelling mistake in a correct id. A
duplicate id is refused and never resolved, exactly as `TransformRegistry` refuses two factories
claiming one name; the same remedy declared twice (factory and phase) is ONE entry, which is the
shape that union has by construction.

**The HOME is one `PortManifest.resolutions` field**, and the precedent is `verdictOverrides`: a table
dispatched by finding kind, consulted from more than one place in the pipeline by code that shares no
phase instance — which is `RuleScope`'s disqualifying property, since a scope is owned by exactly one
phase. It is consulted through the `PolicyBinder` (`PolicyBinder.resolutions`), which is documented as
the only place a phase may learn what a key names, and a selection is precisely that: a key, bound,
with the port's answer attached. That costs no pipeline wiring anybody can forget — a phase that
consults one already implements `PolicyBound` to bind its own keys.

**INHERITANCE is §1.5's strictest case.** A remedy decides emitted text at a declaration a dependent
compiles against, so `effectiveResolutions` unions the chain bases-first like every other inherited
map — and, unlike `packageRenames`, nearest-wins is emphatically not the reported answer. A package
rename disagreement is a fact about the dependent's OWN namespace; a selection disagreement is two
answers to one question about a SHARED one. So:

- the same key with two values anywhere in a chain is a fatal
  `ManifestAgreement.Kind.ResolutionDivergence`, a sibling of `SurfaceDivergence` and not an instance
  of it (that one is two phase INSTANCES that could not be composed; this is one key that composes
  perfectly and composes wrong). The union still stands, because the effective policy has to be well
  defined; the disagreement is a finding beside it, which is the shape `MergeablePolicy` refusals
  already take;
- a dependent's key naming a declaration inside a base's `governs` claim that the base's own
  `resolutions` does not answer is a fatal `ResolutionIntrusion` — `SurfaceIntrusion`'s screen read
  at a member key, through the SAME `standsAt`, which asks the base's PUBLISHED MAP what it EMITS and
  never its claim. `ENGINE-LIMITS.md` D10 is why: a dependent's own declarations routinely sit inside
  the base's namespace and a test source set always does, so a claim-based screen is a rule with no
  way to comply with it. A key the base itself answers is not an intrusion — it is inheritance, or it
  is already a divergence;
- both are asked in `surfacePairs`, so both are **load-bearing**: `surfaceGate` stops the run before
  any phase runs rather than reporting the disagreement beside output an operator would then have to
  read to work out which of the two answers produced it;
- selections join the published surface fingerprint (`PortManifest.surfaceDigestInputs`, folded into
  `PortMap.policyDigest`), so a base whose choices moved cannot look fresh to a dependent. A manifest
  with no selections contributes no lines and digests exactly as the surface list alone did — which is
  what makes the field's arrival flat rather than something to preserve by hand.

**STALENESS gains a THIRD state, and it is a state no binding can express.** `PolicyBinder` answers
one question — did this key name a real declaration? — and its five refusals are complete for it. A
selection can bind perfectly, name a live remedy, and still do nothing, because the FINDING it
resolves never fired this run. Reported as `NeverMatched` it would send its author looking for a
member that is right there; reported as `Unverifiable` it would claim the engine could not check
something it checked. So `PolicyIssue` gains a fourth case, `NeverApplied`, and
`ResolutionPlan.troubles` reports declared beside applied — `CLAUDE.md` §5.5's
`expected#derived`/`expected#declared` split one artifact over, and the reason the plan is a value one
TRANSLATION owns rather than a process-global table (`Determinism.Full` translates twice). The other
two troubles are its neighbours and are kept apart for `PolicyBinder`'s own reason: an unknown id is
`Malformed` (it could never have named a mechanism), and a known id whose declarer is not in this
pipeline is `NeverMatched` with the phase named in the detail (the entry did nothing, and the fix is
one `surface` line).

**…and a FOURTH, which is the flat map's one hole: TWO KEYS AT ONE DECLARATION.** `resolutions` is a
`Map[String, String]`, so two entries can never share a key — a duplicate in a `.conf` merges
last-wins before the loader is reached, and there is nothing to report. What a map does not prevent is
two SPELLINGS of one member: `Foo#bar` and `Foo#bar(int)` are both legal and name one declaration
wherever `bar` has one overload, which is routine across a chain where a base wrote one and a
dependent the other. `ResolutionPlan.selected` takes the first match, so the second is silently inert
and reported `NeverApplied`, whose sentence — *no finding occurred at it this run* — is false: one
did, and the other key answered it.

Only the BINDING can see this, which is why it is here and not in `ManifestAgreement`: two declared
keys can only ever be said to POSSIBLY name one member, while two bound entries name the same `SymId`.
It is narrowed by (declaration, ROW) and not by declaration, because **one member holding rows on two
lanes may legitimately hold two selections** — a `collection-boundary` accept beside an `overload-risk`
one is two answers to two questions, and there is no key that could express them jointly.

**And the LANE is only half of that argument, which cost one wrong finding shape.** `selected`
dispatches by `(lane, kind)`, so two selections at one declaration on ONE lane whose KINDS are
disjoint are also both live and both do their job — `heap-pollution`'s `Acknowledged` beside its
`Unacknowledged` is two statements about two different rows, and `collection-boundary` at one member
is the same shape. Grouped by lane alone the pair is reported and the port is told to delete one of
two entries it needs, which is a finding with no way to comply with it. So the test is
`Remedy.overlaps` — *could these two answer the SAME ROW* — which is `answers`' own question read
between two remedies, derived beside it so the two cannot drift (§4.56's fast-path guard, one type
over): equal kinds, an `alsoKinds` intersection, or either side declaring `Remedy.AnyKind`, which
overlaps everything on its lane by construction. That is the finding, in the `Ambiguous` family
(`PolicyIssue.Unverifiable`), and the effective policy still stands beside it — `MergeablePolicy`'s
own shape: the union has to be well defined and the disagreement is a finding, never an
approximation.

**And the same identity question is the one `ManifestAgreement` was answering with `==`.** Every
comparison of two DECLARED selection keys — the chain's `resolutionConflicts`, the mirroring module's
`MissingResolution`, the `ResolutionIntrusion` screen — grouped or looked up by the key STRING, which
is `CLAUDE.md` §4.56 at the newest layer this engine has: a key is not a string, it is a `MemberKey`,
and two spellings of one member are equal as members and unequal as text. Both directions were live: a
base's `Foo#bar = accept-risk` beside a dependent's `Foo#bar(int) = ascribe-javac-choice` was two
contradictory answers about one declaration with NO divergence reported, and a mirroring module
restating its base's selection under the more precise spelling took a fatal `MissingResolution` for
agreeing. `MemberKey.mayNameSame` is the test, `MemberKey.overloadSetOf` is what a comparison groups
by (overlap is not transitive — two distinct descriptors meet only through a bare key), and two
DISTINCT descriptors stay two members, because a source-level descriptor is injective within an
overload set and collapsing them would be the over-approximation in the other direction.

**ACCOUNTING reuses the `remediation` check with a new kind, `resolved`.** A new top-level lane would
have had to be added to `PortRun.RequiredChecks` by somebody remembering to; `remediation` is already
a member, so a run that stopped recording resolutions fails exactly the way a run that stopped
recording `Remediator`'s suggestions does. The two are the same loop seen from its two ends — that
check already carries the manifest line an operator would paste, and an applied resolution is the
engine having pasted it. The `finding` and the `Decision` come from ONE value (`AppliedResolution`),
so the count and the row cannot disagree about what a resolution did, and the decision's reason is
`Reason.Configured("resolutions", key)` — never the remedy's own `FixKind`, because the reader's first
question is which repository to edit and for a SELECTION the answer is always this port's manifest.
`Decision.Kind.SelectedRemedy` is in `PorterNote.Rendered`, so the reader at the emitted line is told
there was a menu and which entry this port picked; whose code carried it out travels in the detail.

The obligation the lane's arrival creates is stated in `CLAUDE.md` §5 and not restated here: a
resolution must DRAIN the refusal lane it names, by exactly the count that moved.

**CONFIG.** `resolutions { "owner#member" = "remedy-id" }` through `ConfigView.stringMap`, with the
LOADER validating each id against the vocabulary and `ConfigView` staying dumb —
`TransformFactory.scopeOf`'s door, one key over. The KEY's shape is deliberately NOT checked there:
the binder parses every one and its `Malformed` refusal already says what to write, so a second parser
would give the two front doors two sentences for one mistake. What cannot survive the door at all is a
predicate-selected remedy, refused for `OpaqueSpec.hints`' reason (§5.7): a port that needs one writes
a §1(c) rule in Scala, and that rule publishes its menu through `TransformFactory.remedies` like any
other.

**The one hole this leaves, stated rather than left to be found.** `resolutionConflicts` reads
`policyChain`, which for an inheriting dependent IS `baseChain :+ this` and is therefore complete —
and for a `mirroring` module (`inherit = false`) is `List(this)`. So a module that states its policy
IN FULL rather than inheriting it can restate a base's selection differently, or omit one the base
made, and neither is reported: the `ResolutionDivergence` above sees one manifest, and there is no
`MissingDrop`-shaped counterpart asking whether every base selection is answered here. That is
exactly the asymmetry `mirroring` exists to expose for the drops (`MissingDrop` and `SurfaceMissing`
can only ever fire on that path), so the counterpart is owed. It is not built with the plumbing
because no port in this corpus uses `mirroring`, and building it there would have gone out with
`measure-all` numbers nothing in the run could exercise — the shape §5's dry-run rule refuses. The
fix is two comparisons per base, over `baseChain`, and it belongs to the wave that ships the first
menu.

**CLOSED with the first menu**, as that sentence said it would be, and its evidence is a SPEC rather
than a lane — which is the honest form for a path no corpus port takes. Two comparisons per base in
`surfacePairs` (so both are gate-visible, for the reason the other two are): a base selection this
module restates DIFFERENTLY is `ResolutionDivergence` again, and one it does not answer at all is a
new `MissingResolution`, fatal, `MissingDrop` read at a member key. The loop is SCOPED to `!inherit`,
and that is structural rather than an optimisation — for an inheriting module the omission half is
vacuous by construction (its `effectiveResolutions` already contains its base's) while the divergence
half is NOT, since an override is a real disagreement `chainConflicts` already reports from the
chain, naming both claimants. Unscoped, the loop told that one mistake a second time in a shorter
sentence, which leaves a reader two things to reconcile where there is one.

**THE MENUS THAT EXIST, and where each half of one lives.** A lane's menu is DECLARED by the check
or phase that mints its rows (`RemedySource`), and where the minting object is a CHECK the menu needs
a second half: a check runs after the units are written, and a resolution must be recorded before
emission, because the emitter renders a `Decision.Kind.SelectedRemedy` as a porter note while it
writes the member. So the declarer and the carrier are two things — `PortRun.CheckRemedies` names the
first (which is also what lets a `.conf` validate an id at load) and `PortRun.remedyPhases` weaves the
second. Those phases are woven rather than declared for `wovenIdiomPhases`' reason arrived at from the
other side: they have no constructor policy at all, their whole configuration is
`PortManifest.resolutions`, and with no selections each is a no-op on its first line.

| lane | remedy | emission-affecting | what it does |
|---|---|---|---|
| `heap-pollution` | `acknowledge` | no | the operator states this vararg use is safe |
| `overload-risk` | `ascribe-javac-choice` | **yes** | name javac's alternative, as a method-value ascription |
| `overload-risk` | `accept-risk` | no | the operator read the candidate set and accepts the divergence |
| `collection-boundary` | `accept-external-callee` | no | the port read the seam; the value crossing it is one this callee handles |
| `collection-boundary` | `accept-opaque-egress` | no | the port read this external method; it does not read the representation |
| `context-seam` | `accept-unconstructed-thread` | no | the port's USERS construct this class, so the clause is part of the ported API |
| `context-seam` | `accept-residual-global` | no | this read stays global on purpose — a per-SITE statement `boundary` cannot make |
| `nullability-boundary` | `accept-scoped-out` | no | this declaration is meant to keep its upstream type and marker |
| `nullability-boundary` | `accept-abstract-type-parameter` | no | the port accepts what this retype costs at the USES |

**The boundary trio is a DIFFERENT SHAPE from the two above it, and the difference is not an accident
of which lanes came first.** Every one of its six entries is `accept`-shaped and none is
emission-affecting, so none of them appears in `remedyPhases`: there is no act to carry out and no
deadline to beat — the check that mints the residue is the only thing that ever holds the row, so it
partitions and records in one traversal (`ResolutionPlan.drain`, whose doc states the split against
`appliedAt` in full). That uniformity is the ONE-SPELLING rule's own shadow, and it is stated below:
at these three seams every act that CHANGES the emission already has a manifest key, so the only
entries left to add are the ones that move a row.

`acknowledge` is java's own conversation given a home: javac warns at a non-reifiable vararg and
`@SafeVarargs` is the author answering, and NEITHER half exists in scala. Nothing is translated —
the port reproduces java's unsoundness exactly, and a phase that "fixed" it would be emitting a
different program — so what the remedy carries out is the ANSWER, recorded as a decision that reaches
`decisions.tsv` and the emitted line. That is strictly more than the java said, because scalac neither
checks `@SafeVarargs`'s placement nor derives anything from it. It answers `Unacknowledged` alone:
`Acknowledged` is a different statement — java's own author already made that call — and draining it
with the port's would erase the distinction the two kinds were split for. Ruled out beside it, in the
code where the menu is declared: a scala-side marker (inert, since scala has no warning to suppress),
proving the body safe (an analysis nothing here performs — the operator ASSERTS, and the assertion is
recorded with their name on it), and narrowing the emitted signature (the port would stop being the
library).

`ascribe-javac-choice` is the first EMISSION-AFFECTING remedy, and the whole of its safety argument is
one distinction against `ENGINE-LIMITS.md` T17. T17 refuses a RESOLVER: an engine act would have to
know that scala and javac disagree HERE, which is scala's own resolution modelled well enough to
contradict javac about a program both compilers accept. This remedy predicts nothing — **which member
javac bound is not inferred, it is READ**, because the frontend resolved the call and `Tree.Apply.method`
IS javac's answer. What it carries out is writing that answer down, as the method-value ascription
`(recv.m: (A, B) => R)(x, y)` — the shape `TirEmitter.numericOverloadAscription` has emitted
unconditionally for one closed face since before the menu existed, with its trigger moved from a
hard-coded predicate to a port's selection. Scala picks an overload at an ascribed method value by the
EXPECTED TYPE, which is exactly what pins it; an ARGUMENT ascription would pin nothing, since both
alternatives stay applicable under scala's single phase.

Three things about how it is built, each of which is a rule read at a new place:

- the phase mints a `Tree.Typed` whose target is a `MethodType` and prints NOTHING. A phase that
  produced the ascription as text would be writing the upstream namespace into a file the rename has
  not reached (§4.56); `TirEmitter` already renders a `MethodType` as `(A, B) => R`, so one new arm —
  guarded on the target being a `MethodType`, which java can never produce, since java has no method
  types — does every bit of the printing;
- the `Tree.Apply` SURVIVES the rewrite. Wrapping the whole call in an `Opaque` would drain the lane
  structurally (the check would stop seeing a call there), and that is what makes it wrong: the drain
  would be a SIDE EFFECT rather than a recorded move, and a call whose ascription refused would be
  indistinguishable from one nobody asked about;
- it REFUSES wherever javac's alternative cannot be WRITTEN — a generic callee (a polymorphic method
  value has no plain function type), a vararg one (`JS-G37` emits the pack as `Array[T]`, so java's
  parameter list names a signature the port does not have), a constructor, an operator, a `super`
  receiver, a static (java lets a static be called through an instance and `JS-C06` has to move the
  receiver), a spread argument, a bare wildcard in the signature. The finding stays in the lane, and
  the decline is COUNTED beside it — one row per declined SITE naming its GUARD, which is the
  `remediation(refused)` population §5 asks for.

  **It used to record nothing, and "a selection that refused everywhere is `NeverApplied`" was the
  whole of the answer.** That is true of a selection that refused EVERYWHERE and says nothing about
  the ordinary case, because a selection BROADCASTS: a member with two risky calls can have one
  ascribed and one declined, which left an applied row, a lane residue, no refusal row and no
  `NeverApplied` — the key had fired, once. Nothing anywhere said the engine had refused something the
  port asked for, which is exactly what `Resolution.RefusedKind` exists to make sayable. The guard is
  produced by `OverloadRiskCheck.ascription` itself (an `Either`), never by a second predicate beside
  it, for the reason the emission and the refusal already shared one: two derivations of *can this be
  written* would eventually refuse a call the emitter would have printed. One row per declined SITE
  and not per KIND — a call files up to three of JLS 15.12.2's boundaries and this is one act
  declining once, where the APPLIED side counts per kind because there the lane falls by exactly as
  many rows as `resolved` gains.

`accept-risk` beside it is a STATEMENT rather than an act, and it is what can empty an
over-approximated lane honestly: the row moves with the port's name on it and a porter note beside the
emitted call, so an unexamined risk becomes an examined one and a port that merely wants the number
smaller has no way to write it without saying, per member, that somebody looked. Also ruled out where
the menu is declared: the general resolver (T17), the argument ascription (pins nothing), a per-callee
table (the phase java resolved in is a fact about the ARGUMENTS at one site, not about the member), and
emitting both alternatives (there is nothing to emit — both typecheck, which is the lane's premise).

**A KIND SOMETIMES PARTITIONS A LANE AND SOMETIMES SPLITS ONE SITE, and the first menu is where the
difference bites.** A remedy names the `lane(kind)` it drains, and the plumbing shipped reading that
as one pair, on the model every lane in the corpus looked like: `heap-pollution`'s `Acknowledged` and
`Unacknowledged` are two statements about two DIFFERENT declarations, so a remedy for one has nothing
to say about the other. `overload-risk` is the other shape — its three kinds are JLS 15.12.2's three
phase boundaries, all three asked of the SAME candidate set at the SAME call, so a single call files
up to three rows and ONE act (pin javac's alternative, or accept the divergence) answers all of them.

Read through the one-pair rule that lane needs one id per kind, and the selection key is per MEMBER —
so a member holding two kinds could answer only one of them and its remaining rows would be residue
**no key can ever drain**. That is a bar the MECHANISM cannot meet, not a port's omission, and it is
also the compound key's own failure re-arriving: the port writes one decision three times, in three
places it can be wrong. So `Remedy.alsoKinds` names the other kinds of the SAME lane one remedy
answers, `Remedy.answers` is the single predicate `ResolutionPlan.selected` matches through, and the
default is `Nil` — every declarer written before it keeps the pair it had, byte for byte. It does not
widen across LANES, and that restriction is what keeps §5's accounting readable: a remedy drains rows
in exactly one check, so "the drained lane fell by what `resolved` gained" is still one subtraction.

**The DRAIN is read off the LEDGER, not off the manifest** (`ResolutionPlan.appliedAt`). A remedy
that is not emission-affecting changes no tree, so the residue it answered is still standing where
the check walks: without a drain the check reports the row BESIDE the `remediation(resolved)` row
that says it was answered, and the lane cannot fall by what `resolved` gained. Asking the ledger
rather than the declaration is what makes a REFUSAL honest — a selection a phase examined and
declined (its guard says the remedy does not apply at this site) records nothing, so its finding
stays in the lane exactly as it should, and the two ends of the loop read one value.

**ONE POLICY, ONE SPELLING — the rule that decides what becomes a remedy at all.** The menu is a new
front door onto the engine, and the first thing every lane's candidate list produces is a set of acts
the port can ALREADY ask for: `selfSupplied`, `sites`, `promoteToClass`, a `RuleScope` entry, a
`typeMap` row, a `reflectiveSinks` name. Offering any of those as a remedy would give one act two
spellings, which is precisely the drift §1.5 exists to kill — two modules would state the same policy
two ways, `surfaceFingerprint` would compare them as different, and a reader meeting one would have
to know the other existed to be sure they had seen the whole answer.

So each candidate is decided ONE way:

- the act has NO existing spelling → it becomes a remedy;
- the act HAS a spelling → **it is not added**, and the check's own menu documentation carries the
  POINTER. The menu is then complete as documentation even where selection is not the mechanism,
  which is the reader's real question (*what can I do about this row?*) rather than the narrower one
  the field can answer;
- the act is REFUSED → absent, with its `ENGINE-LIMITS.md` id **at the declaration site, in code**,
  so the option is visibly refused rather than silently missing.

Applied to the boundary trio, that leaves six entries out of roughly twenty candidates, and every one
of them is the same act: **the port has read this site and states that the residue is right here.**
That act had no spelling anywhere, on any lane, and its absence is why these counts could only ever
go up — a review that concluded "this one is fine" had nowhere to be recorded.

| lane | remedy | pointer instead (existing spelling) | absent, and why |
|---|---|---|---|
| `collection-boundary` | `accept-external-callee`, `accept-opaque-egress` | `UnmappedSubtype` → `typeMap`; the egress WRAP → `CollectionsTransform(reflectiveSinks)`; `ScopedOut` → `CollectionsTransform(scope)` | `wrap-at-seam` — a row reaches this lane only where the phase found NO factory (K2.5), and a forced bridge over a refused value names the wrapper instead of the boundary (K6.5); `copy-detach` — a copy detaches both directions (§4.4, K15); scope-as-residue-reduction (K16, `27 -> 47`/`27 -> 51`); a second shim-typed parent (K5.7); a coercion at a CONCRETE reified target (K18); a retarget-table wrapper (K14) |
| `context-seam` | `accept-unconstructed-thread`, `accept-residual-global` | a framework really builds it → `selfSupplied`; lazy static → a `sites` entry; a trait body → `promoteToClass`; how residual reads are SPELLED → `boundary` | `lost-clause` — an engine bug in the constructor region reachable from no manifest key (§8.2, CT5), and a remedy for it would let a port silence a defect the engine caused; `captured-context`, `self-supplied`, `deferred-init` — OUTCOME rows, so accepting one would drain a report of something that already worked; `frozen-component` — its acts are spelled, and its own residue is counted as `residual-global-read`, where the accept is |
| `nullability-boundary` | `accept-scoped-out`, `accept-abstract-type-parameter` | widen/narrow → `NullabilityTransform(scope)`; union vs wrapper → `target`; is this really a marker → `annotations`; `-Yexplicit-nulls -language:unsafeNulls` → a BUILD act no manifest key should hold | `VarargParameter`, `AnnotationArguments` — refused STRUCTURALLY, so there is no site at which accepting would mean anything; `PrimitiveType` — an upstream ANNOTATION ERROR, and accepting it would record agreement with a mistake; `OverrideCrossing`, `UncoercibleSeam` — `target`/`scope`, and the same K15 external-callee limit; `ScopedOutParent` — half an override pair, whose only answer is the missing scope entry (K13, `35 -> 6 -> 0`); `NotAValuePosition` — an OUTCOME row, its own classification saying the emitted signature is already correct |

Two consequences of that table worth stating, because both were surprises. **Every entry it produced
is `emissionAffecting = false`** — which is not a coincidence but the rule's own shadow: an act that
changes emitted text at one of these seams is an act somebody already needed, and therefore already
has a key. A future menu on a lane with no such key would produce the other kind. And **the pointer
column is most of the value**: the `context-seam` lane's largest cluster gets one entry and four
pointers, and a reader of that check now finds every answer in one place instead of inferring the set
from three phases' constructor parameters.

**The SUBJECT KIND is the remedy's to declare, which the first menus taught this section twice.** The
paragraph above says the key is a `MemberKey` naming a declaration this run owns, and that was exact
for as long as there was no menu to disagree with it. Two of the boundary trio's residues disagree,
and neither is exotic:

- a residue can be a fact about a TYPE, with no member to name. A class threaded with a `using`
  clause that nothing in the program constructs (`ENGINE-LIMITS.md` CT7) has as many constructors as
  java gave it and the clause is on all of them, so a member key would have to pick one arbitrarily
  and a bare one is `Ambiguous` — a grammar with no way to comply with it;
- a residue can sit at a member this program does NOT declare. An egress row asks *does this external
  method read the representation I hand it?* and is deduplicated BY CALLEE for exactly that reason,
  so the thing a port has an opinion about is the callee — the same string it would write into the
  phase's own sink list. Keyed at the enclosing declaration instead, one row would answer for a site
  chosen by a `min(path, line)` that moves whenever the upstream does.

So `Remedy.subject` (`OwnedMember` | `OwnedType` | `ExternalMember`) says which seam binds the key and
with what `Ownership`, and `ResolutionPlan.of` asks the remedy rather than assuming. Doing it the
other way round — binding every key with `Ownership.Either` and letting the subject fall out — would
let a typo'd owned key bind to an interned external and then report `NeverApplied`, whose sentence is
"the key names a declaration this run OWNS": a refusal that states something false. The two refusals
stay each other's opposite and each stays right for its own remedy — a bare FQN is `Malformed` for an
`OwnedMember` remedy and binds for an `OwnedType` one; `java.lang.StringBuilder#append` is
`ExternalOnly` for the first and binds for `ExternalMember`. `Resolution.key` is therefore
`Option[MemberKey]`, absent for a type subject, because a `MemberKey(fqn, "")` would be §4.6's
fabricated fact; `declaredKey` is what every consumer renders. There is deliberately no
`ExternalType`: no residue in this engine sits at one, and a case nothing produces is a case no spec
can hold to its meaning.

**The DRAIN is ONE function, not one per check.** §5's obligation — `<lane> N->M` beside
`remediation(resolved) 0->(N-M)` — is a property of the pair, and written per check it would have
been three chances to record one half and not the other, which is exactly the shape "a lane that fell
with nothing to attribute the fall to" describes. `ResolutionPlan.drain(remedies, findings)(residue)`
partitions and records in one traversal, returns the rows that were NOT drained, and takes one
`ResolutionPlan.Residue` per row (`kind`, `at`, `subject`, `origin`, `what`) rather than five curried
functions, because a positional lambda list is where two of them get swapped silently. Its empty-plan
fast path is derived from the plan's own entries and nothing else (§4.56).

**…and it is keyed on the CALLER'S OWN MENU, which the first lane with two declarers forced.** It
took a LANE NAME and matched through `(lane, kind)` — the colliding form `ResolutionPlan.selected`'s
own doc warns about — and that is exact only while one source declares a lane's remedies. The
portability lane has two: `PortabilityCheck.AcceptJvmOnly` beside `RemediationTransform`'s three, and
all four declare `Remedy.AnyKind`, so `(lane, kind)` cannot tell them apart at all. A port that
selected `class-table` and got the phase's honest `no-table` REFUSAL would have had its finding
drained anyway, by the check, under a `remediation(resolved)` row saying *accepted as JVM-only* — a
decision that port never made, recorded on the very row whose remedy declined. Nothing was measurable:
the drain is what makes the lane fall, so the two halves agree with each other and disagree with the
manifest. So a caller passes the remedies IT declares (a `RemedySource` hands its own `remedies`), an
id is globally unique, and the lane is DERIVED from those remedies rather than named beside them —
which removes the last seam where a lane could be spelled twice.

**Rejected: a compound key.** `resolutions { "owner#member" { lane = "…", remedy = "…" } }` was the
obvious shape and it is redundant by construction — a remedy id is globally unique, so the id already
determines the lane and the kind, and writing them again gives a port a second place to be wrong. The
flat key is what globally-unique ids BUY.

**Rejected: a `RuleScope`-shaped phase parameter.** A selection spans several phases' findings and is
consulted by checks that are not phases at all, which is exactly `RuleScope`'s disqualifying property.
Threaded through N constructors it would also be N places for two modules to disagree, where the
manifest field is one value §1.5 already knows how to compare.

**Rejected: a new `FixKind` enum in `balticporter.tir`.** A remedy's §1 classification is the same
three answers `balticporter.catalog.FixKind` already carries for a difference row, so the catalog's
enum is reused and gained a `section` accessor rendering them the way `Reason.section` does. Two enums
with identical cases would be two vocabularies for one question — the failure `RemedyHint` (the PROSE
remediation attached to an `Unportable` marker, renamed from `Remedy` in this wave) avoids by sharing
it. `RemedyHint` and `Remedy` stay different TYPES: one is advice a human carries out, the other is a
named alternative the engine performs, and a single type would have to pretend every sentence in the
first is something the engine could be asked to do.

### 8.17 `serviceProviders` — the one deliverable of a port that is not `.scala`

The engine emitted `.scala` AND NOTHING ELSE, and a `ServiceLoader.load(X.class)` reads
`META-INF/services/<X's FQN>` — whose lines are provider class names. Nothing in the pipeline could
carry that file, and the failure is the quietest one this project has (`ENGINE-LIMITS.md` P5): the
providers are constructed reflectively from OUTSIDE the program, so the closure sees no
instantiation and concludes, correctly and uselessly, that nothing has to be fixed. With the
resource absent the loader finds ZERO providers, every registration the library performs at
class-initialisation silently no-ops, and there is **no compile error, no check count and no
finding**. A library then turns "not registered" into a plausible wrong answer rather than an error.

**`PortManifest.serviceProviders` is the §1(b) key** (`serviceProviders = […]` in a `.conf`), and
the split is the ordinary one: the MECHANISM — copy, rewrite, count the residue — is the engine's
(`balticporter.tir.ServiceProviders`), WHICH upstream files a module ships is the port's, and the
empty default is the no-op, so fourteen of the fifteen corpus ports declare nothing and write
nothing.

**It is a REWRITE and not a copy, which is the whole of the difficulty.** §4.56: the descriptor's
NAME is an interface FQN and its LINES are implementation FQNs, so a renaming port moves all of them.
Copied verbatim the file names a service the port does not declare and lists providers that do not
exist — and no instrument can see it, because a resource is on no check's traversal. So every name
goes through the RUN'S OWN `emittedName`, which is the rename PHASE's rule and therefore covers
`typeRenames` and `subPackages` as well as the prefix map; `packageRenames` alone would silently miss
a provider moved by one of the other two.

Five decisions that are not incidental:

- **NOT inherited — `inject`'s line, read at a resource.** Exactly one module's build ships each
  descriptor, and a dependent that copied its base's list would write a second
  `META-INF/services/<X>` at the same path and win or lose by classpath order. The DROPS that decide
  whether a provider still exists ARE inherited, which is what makes the dropped-provider finding
  meaningful in a dependent;
- **per SOURCE SET, not beside the notices.** `Provenance.notices` belongs to the MODULE and both
  source sets write the same bytes; a resource is on ONE configuration's classpath, and a test-set
  descriptor on the main classpath registers a provider the shipped library does not have. So the
  destination is `SbtGen.managedResources(root, config)` and `SbtGen.managedSources` adds both
  `unmanagedResourceDirectories` — a `sourceGenerator` cannot carry a non-source file, and a
  descriptor that is not on the classpath is exactly the silence the key removes;
- **the drop is asked of the UPSTREAM name.** A `Substitutions.dropTypes` key is written in the
  upstream namespace, so asking it of the emitted one is the same key read in the wrong namespace —
  the error `dropped-types.tsv` carried for the life of every renaming port;
- **a declared file that is not there is FATAL**, `Provenance.notices`' rule exactly, and one step
  worse: a descriptor the port meant to ship and silently did not looks identical to one it shipped,
  and the library answers wrongly rather than failing;
- **the write is NOT gated on the artifact layer**, for the notices' reason — a deliverable that
  shipped only when a diagnostic switch was on would be met by accident. What keeps it scoped is the
  empty default and the destination, `src_managed/` (§5.5).

**The lane is `service-providers`, and it carries its own denominator.** One POSITIVE row per shipped
provider, beside `dropped-provider` (the descriptor would advertise a class the port removed —
`ServiceConfigurationError` at the first load and nothing before it), `dropped-service` (the same
fact one level up, kept apart because the next action is to drop the descriptor rather than fix a
line), `unrenamed` (a name this port's rules did not move: legitimate for a provider genuinely
outside the namespace, and identical to a stale descriptor, so the engine states it and the port
decides) and `empty`. A lane that only ever reported trouble could hold its bar at zero by shipping
nothing, which is the trivia family's argument one artifact over.

**Required CONDITIONALLY, and derived rather than listed.** `PortRun.RequiredChecks` is what every
run owes whatever it is configured as, and a lane a port with no key never records cannot go in it.
Left out entirely, a run that stopped writing descriptors would report success with the row gone. So
`PortRun.requiredChecks` adds the lane exactly when the manifest's key is non-empty — the
requirement derived from the same declaration the work is. (The other conditional shape,
`collection-*`, is required by a PHASE being present and is made unskippable by the wiring living
beside the block that records it.)

**Rejected: scanning a resource ROOT.** A resource root is not a source root, and which of a
library's resources are service descriptors is a fact about that library rather than about java. A
scan would ship a provider list the port did not write the moment an upstream tree carried a shaded
dependency's descriptor — the over-approximation §1 calls noise, in the one artifact where being
wrong is silent.

**Rejected: rewriting a line the format does not admit.** Two tokens on one line is not a binary
class name; a best-effort rewrite of one would be §4.6's fabricated fact, so such a line is carried
verbatim and is not counted as a provider.

### 8.18 The FIRST menu — the portability four, as built

§8.16 shipped the plumbing with no menu, so nothing exercised selection → application → accounting on
a real lane. This is the first one, and it is the lane `Remediator` was already writing manifest lines
for: a human read the snippet, opened the manifest and pasted it. The engine now pastes it.

**Four remedies, three of them a PHASE and one a CHECK.** `RemediationTransform` declares
`substitutions-drop`, `static-forwarder-inline` and `class-table` — the ids are `Remediator`'s own
`mechanism` strings, deliberately, so the loop reads as one thing from both ends. `PortabilityCheck`
declares `accept-jvm-only` and is the first entry in `PortRun.CheckRemedies`, because that lane's
producer is a plain object the orchestrator calls and there is no pipeline phase to collect it out of.

**The precondition is `Remediator`'s, verified by `Remediator`'s own code.** The phase calls
`Remediator.suggest` on the program it is handed and acts on the answer; a second implementation of
"is this a chokepoint" would be free to disagree with the one the report prints, which is §4.6's two
answers to one question. What the templates gained is `Suggestion.payload` — the machine-readable half
of the same expression that builds the snippet, because a phase that parsed the snippet back would be
deciding from a string (§4.56).

Four decisions the first menu forced, none of them foreseen by §8.16:

- **a remedy DECLARES the shape of key it is selected at** (`Remedy.Subject`). Two of these templates
  name a TYPE — `Substitutions(dropTypes = …)`, `Forwarder(wrapper = …)` — and have no member to be
  selected at; forcing one would make a port write an arbitrary member of the type it means.
  `ResolutionPlan.of` binds through `bindType` or `bindMember` accordingly, read off the REMEDY and
  never off the key's punctuation, so a `#`-less key at a member remedy still reaches the binder's own
  `Malformed` sentence;
- **`Remedy.AnyKind`**, because `portability(emitted)`'s kind column is the offending API's FQN. A
  per-location remedy there is about the LOCATION, and declaring `kind = "java.net.URL"` would be a
  remedy that answers one row of one port. It is `Remedy.alsoKinds` at the other cardinality and not
  a competitor to it: `alsoKinds` ENUMERATES the kinds one remedy answers, which is exact for a lane
  whose kinds are a closed enum (`overload-risk`'s three phase spans) and unwritable for one whose
  kinds are an open set of hundreds, where the enumeration would have to be rewritten every time a
  rule is added. The precise question a phase asks is therefore
  `ResolutionPlan.selected(target, remedy)` — keyed on the globally-unique id, which cannot collide
  with a sibling on the same lane and needs no kind at all;
- **`AppliedResolution.drained`**, because one selection at a TYPE takes many rows out of the lane.
  §5's drain rule is `sum(drained)` and never `count(rows)`, or a diff reads "one applied, fifteen
  gone" and fourteen have nothing to attribute them to. An inline claims **0** and says why: it
  RELOCATES a call rather than removing one, and over-claiming would break the one arithmetic the rule
  rests on. **So does a REDIRECT, and that took a second reading to see.** `class-table` claimed the
  number of CALL SITES of the wrapper — a real number about a real thing, and not this lane's: the row
  `portability(emitted)` holds is the `Class.forName` inside the WRAPPER'S BODY, which the rewrite
  does not touch, so `resolved` gained N while the lane fell by 0. Relocation is the shape, not the
  mechanism: any remedy that moves a call rather than removing one claims **0**, whatever it can count
  about the move;
- **`Resolution.RefusedKind`** — the refusal POPULATION, one row per declined site naming its GUARD.
  A remedy that verified its own precondition and declined is not `NeverApplied` (the selection was
  consulted and answered) and it is emphatically not silence, which reads exactly like success. Four
  guards, four different next actions: `needs-injection`, `not-a-chokepoint`, `no-table`,
  `targets-contradiction`.

**And this menu's phase is DECLARED in `surface`, where Wave B's two are WOVEN — the difference is
constructor policy.** `HeapPollutionCheck.Apply` and `OverloadRiskCheck.Apply` have none: their whole
configuration is `PortManifest.resolutions`, which is already a manifest field and already in the
surface fingerprint, so there is nothing for two modules to configure differently and nothing a
`surface` line would add. `RemediationTransform` has ONE parameter a port really can disagree about —
the `class-table` destination — so it is an ordinary §1(b) phase with a `TransformFactory`, a
`SurfacePolicy` fingerprint and a `surface` line, and its no-op is the empty table. Weaving it would
have put a policy-bearing phase in every port to serve the ones that configured it.

**It had a SECOND parameter, and the second parameter was a manifest field spelled twice.** WHICH
BACKENDS the questions are asked for was `RemediationTransform(targets)`, defaulted to all three —
which agrees with `PortManifest.targets` by CONVENTION and by nothing else, in a phase whose whole
job is to reason about the lane that field decides. A port with `targets = ["jvm"]` reports an EMPTY
`portability(emitted)` lane (no rule asks about the JVM, `ENGINE-LIMITS.md` P6) while the phase at its
default computed violations against all three and could claim to drain rows from a lane reading zero.
Nothing compared the two, and nothing could: a `SurfacePolicy` fingerprint compares a phase against
ANOTHER MODULE'S instance of the same phase, never against the manifest beside it.

So it is not a parameter. It arrives on the binder — `RunScope.platform`, a `PlatformPolicy` carrying
`targets` AND `verdictOverrides`, because `PortabilityCheck.rulesFor` takes both and a phase reading
one of them would ask a third question nobody reports on. That is `RunScope`'s own charter read once
more (*what the RUN knows about ITSELF and a phase cannot derive from the `Program` it is handed*),
and the default is the pre-parameterised answer exactly, so `RunScope.whole` — every spec, `DebugEmit`
and every run with no manifest — behaves as it did. The general rule is one line longer than the case:
**a phase parameter that RESTATES a manifest field is a divergence with no instrument**, and the fix
is the channel, never a comparison.

**A site the key cannot name is asked for UP THE OWNER CHAIN.** `PortabilityCheck.Violation.enclosing`
is the nearest enclosing DEFINITION, which for a site inside a method body is routinely a LOCAL — the
corpus's one `ServiceLoader` row is owned by a val called `loader`. Asked only at `enclosing`, a
selection would answer `None` for exactly the sites a port most wants to speak about; the fix is not a
finer key but to ask the question at each declaration the key CAN name (`PortRun.ownerChain`).

**A PRINTED KEY AND A BINDABLE KEY ARE THE SAME KEY, or the loop this menu closes runs backwards.**
`Remediator` prints the line a human pastes and the menu is the engine pasting it, so a template that
prints a key no front door can accept has made the round trip WORSE than silence — the reader pastes
it, gets a policy finding, and spends the cycle this file exists to save. Both doors onto
`class-table` bind at `Ownership.Owned` (`ClassTableTransform.bindPolicy` through `bindMembers`, and
the remedy through `Remedy.Subject.OwnedMember`), so `java.lang.Class#forName` — which the direct-site
arm used to print — is `ExternalOnly` at both. The arm proposes NOTHING there instead, and says which
key shape the mechanism needs. The general form is one line longer than the case: **whatever prints a
manifest key states the OWNERSHIP the reader's door will demand of it**, and a template that cannot
meet it says so rather than printing the nearest thing.

**What the menu does NOT offer, stated at the declaration.** A MEDIUM-grade drop with an
engine-written replacement (the replacement is library semantics, and a stand-in that compiles and
silently does nothing is what §1 refuses); a `dropMethods`-scale drop of only the touching members
(`Remediator` observes the ratio rather than thresholding it, because "nothing calls them" is the
port's judgement about its own consumers); and a table `class-table` could invent (a redirect with no
destination leaves the lookup reflective and the port JVM-only with nothing said).

### 8.19 Platform-divergent JDK FAMILIES map to `multiarch-scala` — the decision, ahead of its build

The maintainer's ruling (2026-08-14), recorded before any code exists so the scoping does not
re-open it: where a JDK family exists on all three platforms but with DIFFERENT mechanics —
`java.util.ServiceLoader` is the first instance; JNI-adjacent loading is the standing second — the
port's mapping target is a cross-platform wrapper in **multiarch-scala** (`../multiarch-scala`,
`com.kubuszok`), not a per-port shim and not a JVM-only acceptance. The split follows §1 exactly:

- the WRAPPER is multiarch-scala's deliverable — built-in delegation on JVM and Scala Native
  (Native's `ServiceLoader` support rides linker config, which its sbt plugins already own), a
  registration shim on Scala.js fed from the same `META-INF/services` descriptors §8.17 already
  ships. Its `panama-api`/`panama-jdk` modules are likewise the JVM half of the recorded
  JNI-is-never-carried posture (`CLAUDE.md` §1.5);
- the TRANSFORMER is this engine's — a §1(b) redirect from the JDK family to the wrapper, in the
  same class as `scala-java-time` in the catalog's `Depend` rows: an infrastructure artifact the
  engine may name, parameterised only by what cannot be derived. The catalog rows for the family
  then carry the honest off-JVM verdict (`Depend` on multiarch's artifact + redirect) instead of
  JVM-only;
- liqp is the demonstration port: `SPIHelper#findProviders` currently holds the
  `accept-jvm-only` targets-contradiction refusal (`ENGINE-LIMITS.md` P6), which this mapping is
  what discharges.

**AS BUILT (2026-08-14), and the one thing the ruling got wrong about Scala Native.** The wrapper is
`com.kubuszok %%% multiarch-serviceloader`, and the ruling above expected "built-in delegation on JVM
and Scala Native". Native's delegation is IMPOSSIBLE, measured against 0.5.12: its
`java.util.ServiceLoader.load` is a toolchain INTRINSIC resolved at link time, and the compiler
rejects any call whose argument is not a literal class — *first argument of method load needs to be
literal constant of class type* — which a `Class`-taking wrapper cannot write and `classOf[T]` at an
abstract `T` cannot satisfy either. So `nativeConfig.withServiceProviders` enlistment serves only
direct `load(classOf[Concrete])` sites, which a ported library's generic lookup is never one of, and
Native resolves providers by REGISTRATION exactly as Scala.js does, off the same `META-INF/services`
descriptors §8.17 already ships. **That is the reason the wrapper exists at all** rather than a thin
JVM-and-Native passthrough with a JS shim, and it is what the catalog row's Native verdict now says.

**The TRANSFORMER is TWO EXISTING KEYED SEAMS and no new phase**, which is the part worth stating
because the obvious reading is that a redirect wants one. `java.util.ServiceLoader` is both a FACTORY
and a HANDLE — java hangs `load` off the type it returns — and scala splits them, so the wrapper's
`PlatformServiceLoader.load` returns a `ServiceProviders[T]` and one redirect table cannot name two
targets. `TypeRedirectTransform` moves the TYPE occurrences and `CallSiteSubstitutionTransform` moves
the STATIC CALL, in that order in the port's `surface` and for a reason the port states: the redirect
mints a static TWIN owned by its target, after which the substitution's callee symbol occurs nowhere
and it matches nothing silently. A `staticsTo` parameter on the redirect was the alternative and is
refused — it would be a second mechanism for a seam the engine already has.

**The GUARD is the SHAPE of the target, not a check, and the wrapper's `iterator()` is the one seam
it does create.** `ServiceProviders` declares `iterator()` and `toList` and nothing else — no
`stream()`, no `reload()`, no `Provider`, and it is not an `Iterable` — so every member java's
`ServiceLoader` has and the wrapper does not simply fails to resolve. The one member both spell
carries java's ARITY and not java's ELEMENT TYPE: it answers a `scala.collection.Iterator` where java
answered a `java.util.Iterator`, which `CollectionsTransform` retypes to
`balticporter.runtime.JavaIterator` (§4.5 is why, and it is why "drop-in for `ServiceLoader#iterator()`"
is true of the arity and not of the type). Left alone the collections phase coerces at that external
callee and emits `JavaCollections.fromJava(loader.iterator())` — measured, 0 -> 1 errors, `E134`,
no overload taking a scala iterator — so the port substitutes that call too, ahead of the collections
phase, and bridges through `JavaIterator.from`. Every way this redirect can be wrong is therefore a
SLOT WHOSE TWO SIDES DISAGREE and the compiler is the instrument: there is no residue that compiles
and means something else, which is the case CLAUDE.md §3 says a count has to stand in for.

**…AND THAT CLAIM IS ABOUT THE TYPES, NOT ABOUT THE REGISTRATION — one delta the compiler cannot
see.** The paragraph above is exact for every MEMBER of the wrapper and says nothing about what its
`load` FINDS, which is a different question with a different instrument. Java's trigger for a
descriptor is the CLASSPATH SCAN `load` performs: no code runs first, so a port that ships the
resource (§8.17) has done everything the JVM needs. Off the JVM there is no scan — both backends
resolve by REGISTRATION — and the wrapper's registration lives in a generated object body that
nothing in a ported LIBRARY ever forces: the port emits no entry point, and neither the emitted
Scala nor the generated build names that object. So on a future JS or Native build `load` answers an
EMPTY ITERATOR, silently, which is `ENGINE-LIMITS.md` P5's own defect one platform over and
`CLAUDE.md` §4.4's class-initialiser row read at a generated declaration. Nothing here is wired to
fix it and nothing in the corpus builds off-JVM yet, so the delta is COUNTED rather than closed:
`service-providers` files an `off-jvm-unwired` row per descriptor on every port whose `targets`
reach past the JVM, and `ENGINE-LIMITS.md` P9 names the trigger emission as the future mechanism and
says what it needs that no manifest key carries.

**What it cost, on the demonstration port**: `remediation` 20 -> 17 (the two `accept-jvm-only`
refusal rows and the `substitutions-drop` candidate derived from the same findings),
`portability(all|emitted)` 56 -> 54, `policy` 0 -> 1 (`ENGINE-LIMITS.md` P8 — the declared coordinate
is invisible to a walk that enumerates JDK usages, because the redirect removed the usage),
`rewrite-callsites` 0 -> 1 (`type-redirect` names no accounting lane, the row libGDX core and screens
have carried since they gained the phase), two member digests, 0 errors and 636 passing / 1 expected
failure unchanged.

### 8.20 What an ARTIFACT provides is READ FROM THE ARTIFACT — the provides-set, and the 2×2 it feeds

`PortManifest.dependencies` is checked in both directions: a requirement no coordinate covers
(`dependency-coverage`), and a coordinate no requirement wants (`policy`). The second was asked of ONE
program — the one the run emits — which is exact for a coordinate copied from another module and
wrong for the shape §8.19 introduced: a `Verdict.Depend` is answered by declaring the artifact AND
REDIRECTING INTO IT, and the redirect is what removes the JDK usage the coordinate answers. The port's
most load-bearing coordinate therefore read as the one that fired on nothing, and the finding said
*remove it* (`ENGINE-LIMITS.md` P8, where the numbers and the three rejected candidates live).

**The decision (maintainer, 2026-08-14) is that the entry's state is a PAIR, not a walk.** Does the
PRE-pipeline code use the artifact, × does the EMITTED code use it — four cells, and the asymmetry
between the axes is the part worth stating: the EMITTED column alone decides keep-or-remove, and the
ORIGINAL column decides which SENTENCE the reader gets (`Stale` and `Unused` want different
investigations; `Introduced` has to say that a phase of this port put the artifact there, or it reads
as `Covered`). A better single walk cannot produce that, which is why running the old test
pre-pipeline was rejected rather than adopted.

**And the piece that had no mechanism is the PROVIDES-SET.** To answer the emitted column you must
know which symbols an artifact provides, and there is no structural link between `org:name` and a
package name — deriving one is §4.56's hazard at a build coordinate, which is exactly why P8 stood
open. `ArtifactIndex` reads it **from the artifact**: resolve the coordinate, enumerate the jar's
class entries, and match a program's external references against that set. The jar is the one
authority on what the jar provides, so nothing is guessed and no manifest key is invented — which
matters because a second key linking coordinate to package would be a second spelling of a decision
the coordinate and the redirect already state twice (§1.5).

**And it is matched against TWO KINDS OF REFERENCE, because one of the two seams interns no symbol.**
An `ExternalUsage` row needs a symbol the frontend resolved; a `Tree.Opaque` is ready-made Scala the
engine deliberately does not parse, so a `call-site-substitution` template names an artifact on every
line it wrote and the symbol table holds nothing for it. A `Verdict.Depend` answered by a
substitution ALONE — the static-utility shape, where the port rewrites the CALL and declares no
type — therefore read `No` on both halves, landed in `Stale`, and was told to remove the coordinate
its own emitted code cannot compile without: P8's defect re-entering through the other seam, masked
on the demonstration port only because its `type-redirect` interns a symbol for the same artifact.
So the emitted column also reads the DOTTED NAMES of every `Tree.Opaque` in the program
(`DependencyCheck.splicedNames`), cut at a separator against the same listing. DERIVED from the tree
and never asked of the phases, for `Rewrite.accountedBy`'s reason (§1) — a phase is the one thing
that could be wrong about what it introduced — and fed to the EMITTED column only, since the
pre-pipeline tree holds no spliced text at all.

Five properties that are decisions rather than implementation:

- **`--intransitive`**, or the set is `scala-library` and every port references every coordinate;
- **the coordinate is built EXPLICITLY** — `org:name_3:rev`, never `cs`'s `::`, whose suffix is an
  ambient fact about the machine. Two checkouts reading two different jars is a divergence with every
  count agreeing, which §5 exists to make impossible;
- **the JVM jar, and the limit stated out loud.** A platform-crossed artifact publishes three, and
  their shared API surface is what the JVM listing approximates — `scala-java-time_sjs1_3` exists so
  `java.time.Instant` resolves off the JVM and declares the same types. A class published on ONE
  backend only is one this does not see; nothing in the corpus has that shape, and this is the
  sentence it would contradict;
- **THREE-valued.** A jar that cannot be fetched is `Unverifiable` — neither "provides it" nor "does
  not". Collapsed either way it is §4.6's fabricated fact (an offline run inventing a remove
  instruction, or a silence over a real stale coordinate), so the cell keeps and gives no advice. Note
  this is the OPPOSITE arm from `ClasspathCache`'s, where a fetch failure is FATAL, and the difference
  is what the value decides: a frontend with no classpath resolves WRONGLY rather than failing, while
  this decides one column of one check. It reaches the CELLS as two values and not one: the emitted
  column can answer `Yes` from the catalog half without a jar, leaving the ORIGINAL column unknown,
  and `Introduced`'s sentence asserts *no original usage names this artifact* — so that pair is
  `IntroducedOriginalUnknown`, which keeps exactly as `Introduced` does and says unknown instead of
  asserting the one thing the run does not know;
- **a LINE OF `cs` OUTPUT IS A PATH, spaces included**, and a progress line is told apart by the path
  not EXISTING rather than by its shape. The coursier cache's platform default on macOS is under
  `~/Library/Application Support/`, so a shape test on whitespace drops every jar the resolution
  found and reports a permanent `Unverifiable` on a machine that is online and holds the artifact —
  the `--intransitive` flag order's own failure, one line down;
- **the catalog half first.** The jar is consulted only where the old test would already have
  reported, so fourteen of fifteen ports touch no network and the emitted column is a strict SUPERSET
  of what `unneeded` used to read — the change can turn a finding off and never on. That is a spec,
  not a claim.

The listing is cached beside its own invocation under `.balticporter/` on `ClasspathCache`'s
fingerprint discipline and for its reason: a cache keyed on existence alone answers `yes` after a
coordinate bump, and the port is then checked against the classes it used to declare. It is not the
same object because the engine cannot depend on `corpus`, which is where that one lives.

**…and the same rows are PUBLISHED as an artifact a BUILD reads.** `run-latest/dependencies.tsv` is
one row per declared coordinate — org, name, rev, cross, resolver, the EXPLICIT jvm coordinate, and
whether a jvm compile of the emitted code needs it — and it exists because §1.5's rule holds at the
build layer too. A port's coordinate was stated three times (the `.conf`, the generated build, and a
hand-maintained `--dependency … --repository …` in the measure lane) and nothing compared the third
with the first, so a revision bumped in the manifest and not in the lane compiles the port against a
DIFFERENT JAR with every check count, every member digest and every test outcome flat. The run
publishes and `scripts/_lib.sh`'s `declared_dep_flags` derives, so the coordinate can only be wrong
in one place. The `onClasspath` column is decided from the EVIDENCE and never from the coordinate's
shape: an artifact answering a JDK API off the JVM is one this jvm compile already has, an artifact
the emitted code NAMES is one it cannot resolve without, and an unverifiable one takes the including
arm — the opposite direction from the cell's own advice, and for the opposite reason (a jar nothing
needs costs a resolution; a missing one is a wall of errors that are not the port's).

The file is deliberately NOT a baseline — it is an INPUT to the lane, and the thing a reader wants
held constant is already held: `dependency-coverage(declared)`'s own finding names the coordinate
with its revision, and `findings.tsv` is diffed in both directions, so a rev bump is a row that
moves and a lane that stops.

**`dependency-coverage(declared)` is the positive beside the residue**, required of every run: one row
per declared coordinate naming its cell and the evidence for each half. The trivia family's argument
one lane over — `policy = 0` on this seam is a bar a port meets by declaring nothing, and an artifact a
phase redirected into had no row on either usage lane at all, which is precisely how P8 stayed
invisible for the life of the port that had it.

### 8.21 The LAST two menus — `omissions` and `jdk-surface`, and the line an ACCEPT is cut on

§8.16 shipped the plumbing, §8.18 the first menu and the boundary trio the three that followed.
These are the two lanes the programme priced and never assigned, and between them they carry the
largest per-site residues in the corpus that had no front door at all: `omissions` at 97 rows and
`jdk-surface` at 57.

**They also force the question the earlier menus never had to answer, because every lane they
touched was homogeneous.** `heap-pollution` has two kinds and both are the same question asked of
two populations; `collection-boundary` has ten and the six it refuses are refused one at a time,
each with its own citation. `omissions` has SEVEN kinds that are seven different constructs, and the
first attempt at a menu here is one `accept` per kind — which would have been wrong for five of
them, and wrong in a way no count could have shown, because an accept that should not exist looks
exactly like one that should: the row leaves the lane, `remediation(resolved)` gains it, and the
arithmetic balances perfectly over a defect nobody will now look at.

**The line is whether the ENGINE HAS DECLINED TO DECIDE, and it is written in the engine's own
code.** An accept is licensed where the mechanism can see the site, can see both answers, and has
said — in a doc comment beside the refusal — that WHICH of them is right is not something it can
compute. Two of `omissions`' kinds are like that and both say so in as many words:

- `CtorFunnel.Plans.promotionEscapes` is "deliberately NOT a purity question about the body", because
  whether re-running the promoted statements is observable "depends on what the other constructor
  overwrites, on what the callee touches, and on the caller". 59 of libGDX's 771 promotions escape,
  "most only waste an allocation", and two do not. The engine computes the structural fact and stops;
  the port reads the body. That is §8.16's premise stated by the mechanism itself;
- an annotation the frontend could not carry is `FrontendConfig.preservedAnnotations`' subject, and
  that parameter exists because "WHICH annotations are behaviour-bearing is a fact about a library
  and its dependencies, never about java" (`ENGINE-LIMITS.md` T16). The corpus population is 19
  `@SuppressWarnings` of 25, which suppress a JAVAC warning scala does not have; the same lane holds
  a `@RunWith` on a suite a phase converts to MUnit, which would be actively WRONG to carry.

The other five are LOSSES: the port runs LESS than java, and there is no reading of the site that
yields *this is fine*. `super(args) dropped` is C3 — every one of the corpus's 41 rows is a copy
constructor that copies nothing or a `super(type, texture)` that builds an untextured attribute, and
PADDING was measured and refused. `nilary constructor dropped` is C11, where all three ways of
keeping the delegation were measured worse, and its accept would say *nothing constructs `new C()`* —
a claim about the port's CONSUMERS rather than about a site the port can read, which is §8.18's own
reason for refusing the `dropMethods`-scale drop. `Throwable(cause) message dropped` is the same
shape one member in. `anonymous-class member dropped` is an ENGINE GAP (T1's residue).
And `lambda return with an unnameable result type` is a WORK ITEM rather than a refusal (M6/I9 — a
builder that holds the SAM method fills `Tree.Lambda.resultTpt`, which is how `SamLambdaTransform`
closed part of it), so accepting it would RETIRE it silently, which is the one thing a residue lane
must not let a port do.

Stated as a rule rather than as five cases: **an accept answers a question, never a defect, and the
evidence that it is a question is that the mechanism said so where it declined.** A residue with a
citation that reads *measured worse* is not a question — it is an answer the port is being told.

**`jdk-surface` cuts the same way with three kinds and one entry.** `unhandled` is coverage by
COINCIDENCE, which the check's own doc already names: `java.util.Arrays#fill(float[],float)` compiles
and behaves because a `scala.Array[Float]` IS a `float[]`, and `java.util.Map#clear()` survives
because scala spells the member the same way. Whether THIS port relies on the coincidence is a
reading of the call and not of a table, and it is emphatically not a `Refusals` entry: a `Refusal` is
the ENGINE saying *no translation exists, here is the citation*, and it silences the row for all
fifteen ports at once. `kept-iterable` takes nothing because the row stands for a compile error that
is really there — noise4j's two errors ARE those two rows (K9) — and `stale-refusal` takes nothing
because it reports two ENGINE tables contradicting each other, which is a fact about no port.

**The SUBJECT axis produced the one shape §8.16 had not met: TWO IDS FOR ONE ACT.** `Remedy.subject`
is declared per remedy, and `omissions`' annotation rows sit at BOTH kinds of subject — a
`@SuppressWarnings` on a class is a row over a TYPE symbol and one on a field is a row over a MEMBER
symbol, from one line of the check. A single id would have to pick one seam and the other half of the
population would be residue no key could ever drain, which is the bar `Remedy.alsoKinds` exists to
stop a MECHANISM failing, met here on a different axis. So `accept-dropped-annotation` and
`accept-dropped-type-annotation` are one sentence spelled twice, and the pair is safe by
construction rather than by care: a bare FQN binds through `bindType` and `owner#member` through
`bindMember`, so no key can name both and no declaration can hold both — `Remedy.overlaps` is never
even asked about a pair that could answer one row.

**The POINTERS, which are again most of the value** (`CLAUDE.md` §5's one-policy-one-spelling):

| lane | remedy | pointer instead (existing spelling) | absent, and why |
|---|---|---|---|
| `omissions` | `accept-promoted-body`, `accept-dropped-annotation`, `accept-dropped-type-annotation` | carry the annotation → `FrontendConfig.preservedAnnotations`; supply a constructor the funnel could not express → `Substitutions.inject` beside its `dropMethods`/`dropTypes` key | `super(args)` (C3), `nilary constructor` (C11), `Throwable(cause) message`, `anonymous-class member` (T1) — LOSSES, and an accept would drain a defect; `lambda return` — a WORK ITEM (M6/I9); keeping java's construction paths apart — no key AND no engine act, since tightening `supersedes` "removes no effect and costs the constructor's argument" (C6) |
| `jdk-surface` | `accept-jdk-member` | retarget the owner → `CollectionsTransform(retarget)`; hold the calling declaration back → `CollectionsTransform(scope)`, ruled out as a general answer at `27 -> 47`/`27 -> 51` (K16) | `kept-iterable` — the row IS a compile error (K9); `stale-refusal` — two engine tables disagreeing, and accepting it preserves the reason not to look; the mapping and the `Refusals` entry beside it — ENGINE edits with no manifest key, so neither competes |

**And `jdk-surface`'s key is the finding's own SUBJECT COLUMN, verbatim.** That lane files one row per
MEMBER (however many call sites) rather than per calling declaration, deliberately, so the thing a
port has an opinion about is the external callee — `Remedy.Subject.ExternalMember`, the same seam
`accept-opaque-egress` uses, and the same round trip §8.18 states as a rule: whatever prints a
manifest key states the OWNERSHIP the reader's door will demand of it. An `Ownership.Owned` binding
would refuse every key a reader could copy out of that report.

**Mechanically both are `RemedySource`s the CHECK declares and nothing carries out**, so neither
appears in `PortRun.remedyPhases` — the boundary trio's shape and for its reason: every entry is
`accept`-shaped, there is no act and no deadline, and the check that mints the residue drains it in
the traversal that records it. What the two lanes needed that the trio did not is a `Finding.at`: an
`omissions` row is per CONSTRUCTOR or per annotated symbol and a `jdk-surface` row is per external
member, and neither type carried the `SymId` a selection keys on. `SymId.None` is what a row with no
nameable declaration takes, and it makes that row UNSELECTABLE rather than selectable by whatever
else happens to hold that id — a fallback to the enclosing unit would let one key drain every row in
a file.

### 8.22 `resources` — the descriptor's COMPLEMENT, and the second deliverable that is not `.scala`

§8.17 shipped `serviceProviders` and called it *the one deliverable of a port that is not `.scala`*.
It was one of two, and the second was raised in the same audit item (`PROGRESS.md` §11 item 7) about
a different library: a port whose emitted code reads its own classpath resource ships nothing at
that path, and the failure is the quietest this project has — no compile error, no check count, no
member digest, just a static initialiser throwing in the CONSUMER's build, or a toolkit that refuses
to start.

**Two libraries in two families is what makes it (b) rather than a per-port workaround.** One reads
a single properties table in a `static` initialiser to build an entity map; the next loads its whole
skin, six i18n bundles and six shaders through hardcoded paths, and its first call is a `load()` that
cannot work without them. Both were carried by a LANE — a `--resource-dir` flag pointing at the
upstream tree — which puts bytes on a test JVM's classpath and puts nothing in the port.

**It is a COPY where a descriptor is a REWRITE, and that is the whole design.** §4.56 decides both:

| | what the file CONTAINS | so the act is |
|---|---|---|
| `META-INF/services/<X>` | FQNs all the way down — the NAME is an interface's, the LINES are implementations' | REWRITE both namespaces through the port's own rename rules; copied verbatim it advertises a service the port does not declare |
| every other resource | bytes the program merely LOCATES, found through a STRING LITERAL no rename may touch | COPY, path included; rewritten, the one lookup it exists for stops resolving |

So they are siblings and not variants, a port picks between them by asking what the file contains,
and each doc says when to reach for the other. Everything else about the two is deliberately
identical: not inherited (`inject`'s line — a resource lands at ONE classpath path, and a dependent
that copied its base's list would write a second file there and win or lose by classpath order),
empty is the no-op, the destination is `SbtGen.managedResources(root, config)` (per SOURCE SET, since
a resource is on one configuration's classpath), the write is NOT gated on the artifact layer (a
deliverable that shipped only under a diagnostic switch would be met by accident), and a declared
file that is not there is FATAL.

**DECLARED, never scanned — §8.17's argument with a sharper measurement.** Which of a library's
resources are part of the DERIVED WORK is a fact about that library. Measured on the first port to
take the key: of the **24** files under its upstream resource root, **22** are the library's own and
**2** belong to the upstream BUILD — a cross-compiler module definition and a native-toolchain
configuration — and the module definition NAMES the upstream package this port renames, so shipping
it would advertise sources that do not exist. The reference hand port of that library ships exactly
those 22, which is the control. A scan ships 24 and says nothing.

**The lane is `resources`, and its residues are what make the declaration honest.** One POSITIVE row
per file shipped (a lane that only reported trouble could hold its bar at zero by shipping nothing),
beside:

- **`named-unshipped`** — the emitted code holds a literal naming a path, the file EXISTS under a
  root this port already declared, and the port did not declare the file. That is the defect itself,
  seen at PORT time instead of in somebody else's build. It is the one row that required the engine
  to walk a resource root, and the walk PROPOSES where the declaration DISPOSES: the candidate-list
  shape §1 already asks of a reflective-sink list, one artifact over, and a scan that only proposes
  cannot be wrong about the output;
- **`unnamed`** — shipped, and no emitted literal names it. Legitimate, because a resource routinely
  names another (an atlas names its image, a skin names its fonts) and no phase can walk a resource's
  own content — and also exactly what a stale declaration looks like. Stated, never repaired, which
  is `service-providers`' `unrenamed` row read one key over;
- **`empty`** — a declared tree that ships no file, which is indistinguishable from the resource
  being absent.

**What the emitted program NAMES is read off the LITERALS, not off the emitted text.** The question
is *does this program reference that path*, which is a fact about the program: a text search answers
it YES for a reproduced upstream COMMENT and for a porter note, both of which name upstream strings
on purpose (§4.575). Both spellings are asked, because both are ordinary java — a
`getResourceAsStream` literal carries a leading `/` and a classpath file handle does not — and the
population is `checkedUnits`, so a dependent does not answer for its base's lookups
(`ENGINE-LIMITS.md` D2).

**And the RESOURCE TREE is now WIPED with the emitted sources**, which nothing did before: a port
that stops declaring a file, or moves the path it ships one at, would otherwise leave the previous
run's copy on the consumer's classpath — a deliverable no declaration accounts for, and the one state
`src_managed/` exists to make impossible (§5.5). Unconditional, so *this module ships nothing here*
is a state the tree can reach.

### 8.23 `api-parity` — the hand-port surface comparison, and the families that classify it

A §1(b) check: the MECHANISM (parse both Scala surfaces with scalameta, compare, classify each
divergence into a family) is in the engine; the POLICY (which hand-port source tree, which package
mapping) is per-library, declared in `PortManifest.parity` as a `ParityRef`. Empty/absent = the
check is a no-op AND records nothing — §1(b)'s rule.

**Why a check rather than a script.** The parity campaign (PROGRESS.md §13) needs every hand-port API
choice turned into a counted, classified fact. A script run by hand is a number nobody baselines and
nobody diffs; a check in the engine is in `findings.tsv`, in `counts.tsv`, in every measure lane's
baseline, and in `RequiredChecks` (required-when-declared, like `service-providers` and `resources`).
A port that declares `parity` and then stops recording the lanes fails the run.

**Both sides are parsed by the SAME parser** — scalameta's Scala 3 dialect — which is §4.56's rule
(two spellings make the edge incomparable). The surface model extracts public/protected declarations:
kind, name, arity, structural path. Private members are excluded on both sides.

**The families, and their §4.45 classification:**

| family | what it catches | §1 classification |
|---|---|---|
| `accessor` | getter/setter collapse (`getX`/`setX` vs `x`/`x_=`) or paren-vs-parenless | §1(a) engine — an idiom phase produces this shape |
| `static-placement` | class vs companion member placement | §1(a) engine — informational |
| `mutability` | val vs var vs def drift | §1(a) engine — usually benign (the hand port narrowed mutability) |
| `rename` | same shape, different name (known renames are non-divergent) | §1(a) engine or §1(b) configured |
| `visibility` | different access level | §1(a) engine or §1(c) library-specific |
| `hand-port-extra` | declared only in the hand port (factory methods, helpers, redesigned APIs) | §1(c) library-specific or informational |
| `port-extra` | declared only in the emitted port (java the hand port skipped) | §1(a) engine or §1(b) configured |
| `unclassified` | everything else — the work list | unknown — each row is a missing classifier or a real divergence |

Each family is a separate lane (`api-parity(<family>)`), so `unclassified = 0` is the gate and
the recognised families are the work items a reader can act on one family at a time.

**NOT inherited.** A hand port is a fact about THIS module's destination, not the shared surface.
A dependent does not inherit its base's parity reference. The `.conf` key is
`parity { roots = ["…"], packageMapping = { … } }`.
### 8.24 The drop-in gate — the emitted port INSIDE the reference repo's own build

The parity campaign's done bar (`PROGRESS.md` §13) is that the emitted tree can replace the module's
hand-ported files inside sge's (or ssg's) own sbt-projectMatrix build, and the full suite passes on
JVM + JS + Native. `just <module>-dropin` is that gate.

**Why a CLONE of the reference repo and not the live checkout.** The lane deletes files (the
`Ported from` hand-ported sources) and wires in directories the reference build has never seen. A
live checkout would leave `../sge` with files deleted or a broken build after the lane, which is not
the lane's to do. A disposable `git clone --shared` at the commit `../sge` currently has checked out
is reproducible, idempotent (re-running refreshes it to the same commit), and disposable (it lives
under `.balticporter/dropin/`, which is gitignored). A `--shared` clone creates a proper `.git/`
directory (not a worktree pointer) while sharing the object store, so `sbt-git`'s jgit can resolve
HEAD. A `git worktree add` creates only a `.git` file that jgit cannot follow, which is how the
first attempt failed: `MissingObjectException` on the worktree's own HEAD commit.

**Why `unmanagedSourceDirectories` and not copying files.** The emitted tree is a build product
(`CLAUDE.md` §5.5), regenerated by every engine change. Copying into the worktree would mix emitted
code with hand-written code and make `git status` unable to distinguish the two. An
`unmanagedSourceDirectories` addition follows the sbt-projectMatrix idiom `../sge/build.sbt` already
uses and keeps the emitted tree where it was emitted. The setting is delivered through a
`project/dropin.scala` AutoPlugin in the disposable worktree, which is the only reliable way to
reach all three platform projects of a projectMatrix module.

**What is replaced.** Every file whose header matches the module's `Ported from` pattern (sge
spelling: `Ported from <lib> - <url>` + `Original source: <path>`; ssg spelling: `Ported from:
<path>`). Everything else stays: SGE-original platform files, hand-added tests with no port header,
all non-Scala files.

**What is NOT replaced.** The hand-added test files (those with no `Ported from` header) are the
module's own and are kept in place. They compile against the emitted surface exactly as they compile
against the hand-ported surface, which is the gate: a public-surface divergence shows up as a
compile error in these tests.

**The lane is NOT in `measure-all`.** The drop-in is expected red until the emitted API is at
parity. `just dropin-all` is the separate aggregator.
