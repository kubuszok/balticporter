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

## What is reused vs replaced

- **Reuse:** the Spoon frontend + whole-classpath resolution (the typed/symbol
  source of truth), the vendoring/closure machinery, sbt-gen, the cache.
- **Replace:** the BIR + printer + `BirPass` **as the transform substrate**. The
  printer becomes one emission backend behind the TIR.

## Build order

1. **TIR core model** — `Symbol`, `SymId`, `SymbolTable`, `TType`, `Tree`,
   `Origin`, `Program`, `XrefIndex` (signatures + minimal bodies). *(this commit)*
2. **Populate from Spoon** — one pass over the existing closure that mints symbols
   for every declaration, resolves every reference to a symbol via Spoon, and
   builds the xref index. Validate: `usagesOf` a known field returns every site.
3. **Emission backend** — TIR → Scala source, types-aware (subsumes the compile
   fixes). Gate: the M6 closure compiles from TIR emission.
4. **First transform** — pick one real case (java→scala collection, or a field
   usage rewrite) end-to-end to validate the substrate against an actual migration.
5. **Transform API + the sge/ssg cases** — globals→implicits (call graph),
   Int→opaque, collections, Panama.

The old string-printer compile grind (M6 at ~61 errors) is **subsumed** by step 3:
a types-carrying backend emits compiling code by construction. We stop patching the
printer.
