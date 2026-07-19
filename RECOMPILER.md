# Baltic Porter as a re-compiler (not a transpiler + pretty-printer)

## What this is

Baltic Porter's product is **not** "faithful Scala-like-Java, pretty-printed
deterministically." It is a **re-compiler**: Java is recompiled into a **typed,
symbol-resolved, whole-program Scala tree** on which **project-owned transformers**
run — scalafix-style but richer and owned — to emit a **refactored, migrated**
project. Emission (source, later TASTy) is a decoupled backend, not the driver.

The transformers are customized per target and are the point of the tool. Real
cases from sge/ssg:

1. **globals → implicits** — thread a `using` parameter through every method that
   transitively reaches a global. A call-graph rewrite.
2. **Int → opaque type (+companion)** — GL layer ids, key/button codes: retype a
   semantically-tagged value everywhere it flows, wrap its construction sites.
3. **java collections → scala**, leaner where possible — retype + API-map every
   usage site of a collection symbol.
4. **Panama FFI generation** — generate `java.lang.foreign` bindings for JVM and
   Scala Native linkers from native signatures; find and replace existing JNI.

None are textual. All are whole-program, type- and symbol-driven. They must run
**before** emission (some rewrites are impossible to recover post-hoc), the tool
must **own** the layer (not delegate to scalafix), and the tree must carry **more**
than a Scala-2.13 semantic AST does.

## Why the current BIR is the wrong substrate

The BIR is a lossy, per-unit, **string-oriented** projection built to feed a
deterministic printer: qualified-name *strings*, `owner` *strings*, types attached
only where a print rule needs them. There is **no symbol table, no cross-unit
reference index, no whole-program view**, and it discards types it had (the raw
`new HashSet<>()` → `HashSet[Object]` bug). A string IR can never support
symbol-driven, whole-program refactoring. Pretty-printing/determinism is a backend
concern that was mistaken for the architecture.

The information already exists one stage upstream: **Spoon's `CtModel` is a fully
typed, symbol-resolved, position-carrying, cross-referenceable Java tree** (usage
search is its native job). The pipeline throws that away at the BIR boundary. The
fix is to **stop collapsing to strings** and carry a real typed+symbol model.

## The owned typed IR (TIR)

A Scala-shaped, whole-program, typed tree with a real symbol model. Populated by
**leveraging Spoon's resolution** (we do not re-implement type resolution) and
translating Java types/symbols to Scala ones.

### Symbols
Every declaration (class, trait, object, method, ctor, field, param, local, type
param, package) gets a `Symbol` with **stable identity** (interned id, not a
string), its `TType`, its `owner`, `flags` (visibility, abstract, …), an `Origin`,
and an open slot for **domain tags** (e.g. "this Int is a GL layer"). Every
reference node points to a `Symbol`. This is what makes `usagesOf(sym)` possible
and what makes patches **bump-resilient**: they key on symbol identity, not text,
so an upstream rename/reflow can't break them.

### Types
A **structured** Scala type algebra (`TType`) — not Java's, and never a flat string
that collapses type structure. It must faithfully represent, as addressable nodes:
- **applied type constructors** — `Applied(tycon, args)`, args recursively typed;
- **intersection / mixins** — `And(members)` and the `TypeDef.parents` linearization
  (`A with B with C`);
- **self-types** — `TypeDef.selfType` + `This(cls)` (`trait T { self: S => }`);
- **type-parameter bounds, incl. F-bounds** — `TypeParam(sym, variance, bounds, …)`
  where `T <: IRichSequence[T]` is a param whose bound references its own symbol;
- **variance**, **higher-kinded** params (`hkParams`, `HKLambda`), **wildcards**
  (`Bounds`), **path-dependent / singleton** types (`Named(prefix)`, `Singleton`),
  and **method/poly signatures** (`Method(MethodSig)`).

Every tree node carries its `TType`, resolved from Spoon (including F-bound
instantiations and generic-method returns), so transforms and emission **never
re-infer**. Type references point to a type-*symbol*, so "find all usages of
`java.util.List`" includes type positions, not just calls. This is the "more than a
2.13 AST" information: full structured resolved types, carried, not recovered.

### Trees
A typed Scala tree (the `tpd.Tree` analog): `ValDef`, `DefDef`, `Apply`, `Select`,
`Ident`, `New`, `Lambda`, … Each node has `tpe`, an optional `symbol`, and an
`Origin`. This **replaces** the BIR.

### Program (whole-program index)
`Program` holds all units + the `SymbolTable` + an `XrefIndex` (symbol → definition,
symbol → all usages, method → callers). Built once over all Spoon units. Query API:
`usagesOf(sym)`, `definitionOf(sym)`, `callersOf(method)`, `symbolAt(tree)`,
`typeOf(tree)`. This is the substrate the transformers run on.

### Provenance
Each node carries its **Java origin** (path + position) and, after emission, its
Scala position. Satisfies "access the original source that produced each node" and
underpins bump-resilience and diagnostics.

### Transform API — shaped by Scala 3's compiler-plugin model
`Plugin` (a named bundle) ~ `dotc.plugins.Plugin`; `Phase` ~ `PluginPhase`/
`MiniPhase` with `runsAfter`/`runsBefore` ordering and `transformX` hooks you
override only for the nodes you touch (the framework does the bottom-up traversal
and fuses them); a full-control `run` ~ `ResearchPlugin` for whole-program analyses
(globals→implicits needs the call graph before rewriting). Every hook runs with the
whole-program `Program` in scope (`using`), so a transform can ask `usagesOf` /
`callersOf` / `symbolOf` *while* rewriting — the thing Quotes and scalafix-over-
SemanticDB cannot give you across a program, before emission. `Pipeline.order`
topologically sorts phases; the xref index is rebuilt between phases. These passes
are the project-owned, customized transformers.

## Design anchors

- **Tree / type / symbol model → `scala.quoted.Quotes#reflect`.** Same shapes
  (`TypeRepr`, `Tree`/`Statement`/`Definition`/`Term`/`TypeTree`, `Symbol`) so macro-
  literate authors are immediately at home. We do **not** use Quotes directly — its
  contracts are hard to satisfy outside a macro and its `Symbol` hides internals — we
  own a close analog that exposes everything and adds `Origin`, `SymTag`, and the
  whole-program `XrefIndex`.
- **Transform pipeline → Scala 3 compiler-plugin model** (`Plugin`/`PluginPhase`/
  `MiniPhase`, phase ordering, `transformX` hooks, `ResearchPlugin` escape hatch).

### Emission (backend)
A backend walks the transformed typed tree → Scala source (and later TASTy).
Because the tree carries types and symbols, emission inserts the correct form by
construction (the diamond/inference bugs cannot occur). The determinism / comment-
invariant / API-parity checks become backend verifications, not the driver.

## Design goal: semantic diff between two portings (planned, not yet built)

The re-compiler must be able to produce a **semantic diff between any two portings of the
same project** — not a textual diff. Two axes:

- **Upstream drift** — when the original Java changes, diff the two TIRs to get the semantic
  delta in Java terms (symbols added/removed/retyped, signature changes, call-graph edges
  gained/lost) AND its projection onto the emitted Scala (which output definitions actually
  change). Symbol-keyed identity (interned `SymId`, not text) is what makes this a
  structural diff resilient to renames/reflow, rather than a line diff.
- **Transform drift** — when we develop or change a plugin/phase, diff the pre- and
  post-transform TIRs (and their emissions) to see exactly what a phase did across the whole
  program — the reviewable unit of a migration.

Enablers already in place: stable `SymId` identity, `Origin` provenance, the kinded
`XrefIndex`, and the immutable-rebuild pipeline (each phase yields a fresh `Program`, so
before/after snapshots are naturally available). The diff itself (a `Program × Program →
SemanticDelta`, plus an emission-level projection) is future work — slot it after the
emission backend (step 3), since projecting the delta onto Scala output needs emission.

## What is reused vs replaced

- **Reuse:** the Spoon frontend + whole-classpath resolution (the typed/symbol
  source of truth), the vendoring/closure machinery, sbt-gen, the cache.
- **Replace:** the BIR + printer + `BirPass` **as the transform substrate**. The
  printer becomes one emission backend behind the TIR.

## Build order

1. **TIR core model** — `Symbol`, `SymId`, `SymbolTable`, `TType`, `Tree`,
   `Origin`, `Program`, `XrefIndex` (signatures + minimal bodies). *(this commit)*
2. **Populate from Spoon — DONE** (declarations + signatures + types).
   - **2a. Kinded xref + rewrite-responsive traversal** (`Xref.build`, `UsageKind`,
     `Usage`; `StandardTraversal` routes every type occurrence and every symbol `info`
     through `transformType`; `Pipeline` rebuilds the index between phases).
     `usagesOf(sym)` traces a type across every position — external type, type argument,
     member type, mixin, extends, self-type, bound — and after a phase rewrites the tree
     the old symbol drops to zero usages while the new one inherits the exact positions.
     Proven by `core` `XrefSpec` (3/3). Class/method type parameters are now first-class
     `TypeDef` nodes on `ClassDef`/`DefDef`, so class F-bounds ARE walked (gap closed).
   - **2b. `SpoonTir` populator** (`frontend-spoon`) — one pass over Spoon's resolved
     `CtModel` that mints a stable-identity `Symbol` per declaration, lazily interns
     externals (JDK/library) so `usagesOf(java.util.List)` works with no local
     definition, and translates every `CtTypeReference` to a structured `TypeRepr`
     (applied types, wildcards→`TypeBounds`, intersections→`AndType`, type params via a
     scope stack incl. F-bounds), building `ClassDef` units fed to `Xref.build`. Proven
     by `SpoonTirSpec` (4/4) on real parsed Java, including a class F-bound traced
     across type-arg/member-type/bound positions and a live List→scala rewrite.
   - **2c. Method-body translation** — `SpoonTir.BodyTranslator` translates method / ctor
     / field-initializer bodies into TIR terms, resolving every reference to a `SymId`:
     locals, assignments, `if`/`while`/`return`/`throw`, blocks, method & constructor
     calls, field/variable access, `this`, casts, ternary, operators (as `x.op(y)`, the
     quotes.reflect shape), literals. New imperative Term nodes `Return`/`While`/`Throw`
     were added to the model; each `Usage` now records its `enclosing` definition, making
     `callersOf` a real call-graph edge. Constructs not yet modeled (for-loops, switch,
     try, lambdas, arrays, method refs) fail loudly via `Unsupported` (same anti-omission
     stance as the BIR frontend; the body node set grows the same way). Proven by
     `SpoonTirSpec` (6/6): method calls / field refs become traced usages, and
     `callersOf(pick) == [run]` over real translated bodies.
   - **2d. Full corpus body coverage** — the body node set was grown until EVERY Java
     library that sge and ssg port translates clean. Added faithful Term nodes: `InstanceOf`,
     `ArrayAccess`, `ArrayLength`, `NewArray`, `ForEach`, `For`, `Try`(+`CatchCase`,
     +resources), `Match`(+`CaseDef`), `MethodRef`, `Break`, `Continue`, `Assert`, `IncDec`,
     `DoWhile`, `Synchronized`; assignment-as-value reuses `Assign`; Java switch → `Match`
     with **tail-duplication** for genuine fallthrough (RESEARCH §4.2). `SpoonTirCoverage` is
     a multi-corpus burn-down harness (`runMain … [liqp|flexmark|sge|all] [N]`) over `../ssg`
     and `../sge`; sge is a lenient per-library sweep (each libGDX-ecosystem library modeled
     on its own, pinned to its canonical module root for the multi-backend ones):
       - **ssg liqp: 135/135**, flexmark: **789/789**.
       - **sge: 1484/1484** across all 14 libGDX libraries (libgdx core 605, gdx-gltf 160,
         vis-ui 180, gdx-ai 166, …).
       - **all: 2408/2408 top-level types, 0 `Unsupported`.**
     `SpoonTirBodySpec` locks every construct in corpus-independently (5/5).
3. **Emission backend** — TIR → Scala source, types-aware (subsumes the compile
   fixes). Gate: the M6 closure compiles from TIR emission.
   - **3a. First cut — `TirEmitter` (scala-emit)** — walks the typed tree → Scala 3 source,
     resolving every name from the `Program` symbol table (types: simple for our own decls,
     qualified for externals). Covers the whole node set: classes/traits/objects/enums with
     type params + F-bounds, defs/vals/fields, and all terms (calls, control flow, arrays,
     try, match, for/foreach, lambda, instanceof, …). Operators render infix/prefix
     (precedence-safe parens). Emits readable Scala on real corpus files (e.g. liqp
     `Compact`, `Upcase`). `TirEmitterSpec` pins output from a hand-built program;
     `SpoonTirEmit` (runMain) emits any Java file for eyeballing.
   - **3b. scalac gate + burn-down** — `SpoonTirEmitProject` (runMain) emits a whole small
     library through the TIR to `out/tir-emit/<lib>/` and runs scalac (`M0Pipeline.compileGate`)
     over it; the loop is: run, read errors, fix the emitter, repeat. Driving noise4j
     (12 files, dependency-free) burned through the first structural classes of Java→Scala:
     keyword-escaping (`type`/`object`/… as identifiers), `abstract def` (methods are abstract
     by lacking a body, not a keyword), uninitialized fields → defaulted `var` (no `final var`),
     a `Super` term node so super-vs-this dispatch and constructor delegation are distinct,
     secondary-constructor ordering (delegate must precede — sorted by descending arity),
     fully-qualified type references (no import machinery), operators infix, and Java statics →
     a **companion `object`**. Remaining classes on the worklist (each a distinct
     emitter/populator refinement, not yet done): nested-type placement (static-nested →
     companion, inner → path-dependent), accessor-paren semantics, **enum lowering with cases**
     (populator must carry `CtEnumValue`s), java-collection API surface, and `break`/`continue`
     → `boundary` / do-while / inc-dec-in-value. Full green on a real library is a multi-wave
     grind (the M6-scale effort, now on the emitter, where it belongs).
4. **First transform** — pick one real case (java→scala collection, or a field
   usage rewrite) end-to-end to validate the substrate against an actual migration.
5. **Transform API + the sge/ssg cases** — globals→implicits (call graph),
   Int→opaque, collections, Panama.

The old string-printer compile grind (M6 at ~61 errors) is **subsumed** by step 3:
a types-carrying backend emits compiling code by construction. We stop patching the
printer.

## North star

1. **Cover every ported library** — the TIR populates from every Java library sge and ssg
   port. *(done: 2408/2408 types, step 2d.)*
2. **Source pretty-printing** — the emission backend (step 3): TIR → Scala source. This is
   also what projects the semantic diff onto Scala output.
3. **Agents take over library maintenance** — once the re-compiler round-trips (populate →
   transform → emit) and can semantic-diff two portings, per-library agents manage the ports:
   pull upstream Java changes, re-run the transforms, review the semantic delta (Java + its
   Scala projection), and land the migration. The re-compiler is the tool; the agents are the
   operators. The pieces this needs — whole-program symbol/xref substrate, owned transform
   pipeline, semantic diff — are what steps 2–5 build.
