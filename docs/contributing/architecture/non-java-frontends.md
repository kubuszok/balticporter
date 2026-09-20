# Non-Java frontends

Baltic Porter's stable frontend boundary is a `Program` (the typed intermediate representation),
not a parsed source tree — see [the intermediate representation](intermediate-representation.md).
That boundary is what lets a second and third source language exist beside the Java/Spoon frontend
without touching anything downstream of it: `frontend-ts` reads TypeScript and, through the same
mechanism, untyped JavaScript; `frontend-dart` reads Dart. Both are experimental. Neither is used
by a corpus port today — no `ported/` module and no `balticporter.corpus.*` package migrates a
TypeScript or Dart library yet. Everything described here is exercised by `frontend-ts`'s and
`frontend-dart`'s own test suites against fixture files checked into `src/test/resources`, as a
proving ground for the mechanism before any library goes through it as a real port.

## The frontend seam

`api`'s `core.Frontend` declares `trait TirFrontend`, `def build(cfg, subs, catalog, lenient):
Program`, plus `name`, `language` and `defaultInclude`. A frontend registers itself under
`META-INF/services/balticporter.core.TirFrontend` and a port selects one by name with
`input.frontend` in its configuration; `frontend-ts` ships `TsFrontend` (name `"typescript"`) and
`JsFrontend` (name `"javascript"`), `frontend-dart` ships `DartFrontend` (name `"dart"`).

`core.Language` gives every source language a catalog prefix: `Java` ("JS"), `TypeScript` ("TS"),
`Dart` ("DT"), `JavaScript` ("JX"). The catalog's `DiffId` carries a `Language` alongside its area
and number, so a TypeScript- or Dart-specific difference is a new area of the same registry the
Java rows live in, never a parallel one.

`frontend-ts` depends on `api` alone, exactly like `frontend-spoon`. `frontend-dart` depends on
`api` and `frontend-ts` — it reuses the RAST JSON codecs and reader plumbing rather than
duplicating them. Both modules are `ProjectType.JarOnly` sbt projects and both are in the root
aggregate (`root` aggregates `api`, `frontend-spoon`, `engine`, `testkit`, `corpus`, `frontend-ts`
and `frontend-dart`), so `sbt compile`/`sbt test` at the top of the build reaches them.

## The interchange format: RAST

Both exporters produce the same JSON shape, schema-versioned as RAST ("resolved AST") version 1:
one document per exported program, with a `producer` header (compiler/analyzer version, exporter
version, effective options, a hash per input file), a `files` table, a `symbols` table (name,
qualified name, kind, owner, declaration site, resolved type), a `types` table (every type
resolved anywhere in the program, interned once), a `sigs` table for resolved call signatures, and
a `units` array of per-file node trees. A node carries its parser-kind name, position, resolved
symbol and type, and its children keyed by the source language's own property names. The exporter
is deliberately dumb: it serialises what the compiler or analyzer already resolved and decides
nothing about how a construct becomes Scala — every such decision is taken once, in Scala, by the
reader that turns RAST into TIR.

On the Scala side, `frontend-ts`'s `Rast.scala` and `frontend-dart`'s `DartRast.scala` hold the
jsoniter-scala codecs for this shape (`frontend-ts` depends on `jsoniter-scala-core`; the macro
module is `Provided`, since only the exporter side needs to change what is emitted). Determinism
matters because a fixture must diff cleanly: the exporter sorts maps and assigns ids in a fixed
walk order.

## The exporters

- **TypeScript**: `frontend-ts/exporter` is an npm package (`export.ts`) pinned to one exact
  `typescript` version (currently 5.8.3). It runs `ts.createProgram` over a `tsconfig.json`,
  walks every source file under the port's source root, and writes RAST JSON. `TsFrontend` either
  points at a pre-exported directory of `.rast.json` files or (as a follow-up) invokes the
  exporter as a subprocess.
- **JavaScript**: `JsFrontend` is the same exporter and the same `TsMinter` reader, run with
  `allowJs` turned on in the generated `tsconfig`. The TypeScript checker can still resolve module
  structure and many call sites for plain JavaScript; whatever it cannot resolve comes back typed
  `any`, and the minter records a site built on an unresolved type as `Unportable` rather than
  guessing. This is how `frontend-ts` reaches an untyped source such as Terser without a separate
  JavaScript-specific exporter.
- **Dart**: `frontend-dart/exporter/bin/export.dart` uses `package:analyzer` —
  `AnalysisContextCollection` plus `getResolvedUnit` — to get a fully resolved compilation unit per
  file, with a static type on every expression and a resolved `Element` on every reference. It
  needs a Dart SDK on `PATH` at export time; nothing on the Scala side depends on Dart.

## The readers (minters)

`TsMinter` (`frontend-ts/src/main/scala/balticporter/frontend/ts/TsMinter.scala`) turns a list of
parsed RAST files into a TIR `Program`: it mints an interned `Symbol` for every declaration it
recognises, wraps top-level functions and variables in a synthetic module class, and builds the
declared members, parameter lists and bodies from the RAST node tree. `DartMinter`
(`frontend-dart/.../DartMinter.scala`) is the Dart-side counterpart, over `DartRast`'s node shape.
`TsFrontend`, `JsFrontend` and `DartFrontend` are thin `TirFrontend` adapters that read RAST off
disk and hand it to their minter.

Beside the minter, `TsToScalaEmitter` is a second, independent path: it reads RAST directly and
prints Scala text without going through the TIR at all. It exists as a fast-turnaround harness for
working out a lowering rule (RAST in, Scala text out, checked against `scalac` in seconds) before
that rule is written against the minter and the TIR — which is the only path that reaches the
engine's phases, checks and reports. A rule is not considered to have reached the engine until its
construction half lives in the minter; the text-emitter form is a scratch step on the way there.

## Library-specific emitters

The `dedicated` package under `frontend-ts` (and `DartSassEmitter`, which sits alongside it) is
where per-library knowledge is allowed to live for this second language path. The rule that no
shared module may name a ported library in code binds `api`, `engine`, `frontend-spoon` and
`runtime` (see [Overview](overview.md)); `frontend-ts` and `frontend-dart` are outside that rule on
purpose, because a mechanism for a new source language is worked out against one real library
before (or instead of) it is generalised into a parameterised phase the rest of the engine can
share.

Each file targets one library or one shared shape drawn from a library: `RoughCoreEmitter`,
`RoughEngineEmitter`, `RoughGeometryEmitter`, `RoughMathEmitter`, `RoughFillersEmitter`,
`PointsOnCurveEmitter`, `PointsOnPathEmitter` and `HachureFillEmitter` cover the rough.js family;
`KaTeXEmitter` covers KaTeX; `MermaidEmitter`, `MermaidDbEmitter`, `MermaidDetectorEmitter`,
`MermaidDiagramEmitter`, `MermaidTypeEmitter`, `MermaidB9Emitter` and `VitestToMunitEmitter` cover
Mermaid's module shapes and its test suite; `TerserEmitter`, `TerserCompressEmitter`,
`TerserB10B11C3Emitter`, `DefmethodBodyTranslator`, `JisonActionExtractor`, `AstDtsGenerator`,
`GenAstDts`, `RastTypes` and `ReferenceTypeOracle` cover Terser's runtime-defined class hierarchy
and its jison-generated parsers; `DartSassEmitter` covers dart-sass. Each emitter derives what it
can from RAST for its one library and refuses (or falls back, see below) at the rest.

## Combining a hand-written reference port with translated bodies

`ParityDerive` (`frontend-ts/src/main/scala/balticporter/frontend/ts/ParityDerive.scala`) is the
shared mechanism behind every dedicated emitter above: it takes a hand-written reference Scala
file — one of sge's or ssg's already-ported modules — as the structural skeleton, and for each
method replaces the reference body with the RAST-derived translation wherever a matching method
exists and the translated body does not hit one of the library's declared `uncompilablePatterns`.
Where no RAST-derived body qualifies, the reference body is kept. Every method's outcome is
recorded as a `BodyEntry` naming its source (`"translated"` or `"reference"`) and, for a kept
reference body, why it was kept. Each dedicated emitter supplies only its own translated bodies
(`ParityDerive.Bodies`: one list per member name, one entry per OCCURRENCE, each carrying the
translator's refusal reasons) and its `ParityDerive.Policy`; `ParityDerive` owns the interleaving
algorithm and the provenance recording, and `ReferenceSkeleton` the reading of the reference file
into replaceable methods, so that logic exists once rather than once per library.

`NonJavaBodies.forLibrary(library, referenceDir, rastDir)` is the one entry point a consumer build
calls. A library is a registered VALUE (`NonJavaBodies.Library`: name, policy, RAST reader, and the
table of which RAST files feed which reference file), supplied by its dedicated emitter; the result
derives a whole reference tree and writes `bodies.tsv` (`BodiesReport`), described for users in
[Reading the report](../../user-guide/reading-the-report.md). Every registered policy sets
`keepReferenceOnRefusal`: a body the translator left a hole in (`???`) compiles and then throws, so
the reference body is kept and the row says `translator-refusal:<reason>`.

Two small mechanisms read `ParityDerive`'s output:

- `CoverageLane` turns a module's list of `BodyEntry` values into a report — total methods,
  how many came from RAST versus the reference, and a breakdown of the reference-derived ones by
  reason.
- `BodyVerdicts` classifies every reference-derived body as `justified` (a named uncompilable
  pattern or a translator refusal explains it), `structural` (no RAST symbol lines up with it at
  all) or `unjustified` (kept for no recorded reason) — so a run can be read for how much of it is
  still a copy of hand-written code rather than a translation, instead of only reporting a pass
  count.

`ReferenceTypeOracle` answers a narrower version of the same need for Terser: JavaScript carries no
static types, so the RAST types every `this.x` access `any`; the oracle reads parameter and field
types back off the hand-ported reference by `(objectName, methodName)` so the compress emitter can
still produce typed Scala for a construct the checker itself cannot type.

## Where fixtures live

- **RAST fixtures**: `frontend-ts/src/test/resources/rast/<library>/…`, one JSON file per exported
  source file, for path-data-parser, points-on-curve, points-on-path, hachure-fill, roughjs, katex,
  mermaid, terser and dart-sass (the last under `frontend-ts` because the Dart minter's tests reuse
  the same jsoniter infrastructure).
- **Reference sources for `ParityDerive`**: `frontend-ts/src/test/resources/reference/<library>/…`
  — hand-ported Scala files for katex, mermaid, terser and dart-sass, plus hand-extracted jison
  grammar fixtures under `reference/jison/<diagram>/`.

No RAST export of a whole upstream library is checked in as a build input outside these test
fixtures; a real port run always re-exports from the pinned upstream checkout rather than reading
a stale snapshot.
