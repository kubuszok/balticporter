---
paths:
  - "balticporter/engine/**/emit/**"
  - "balticporter/engine/**/tir/**"
  - "balticporter/frontend-spoon/**"
---

# Emitter, TIR and frontend — renames, ownership, provenance, notes, trivia, debugging

Detail for `CLAUDE.md` §4.55, §4.56, §4.57, §4.575, §4.58, §4.59 and §4.6.

## §4.55 — renaming

- Read effective names, not originals: reading originals renamed `CheckBox.style` and
  `TextButton.style` to the same `style$shadow`. Scan parents before children.
- **The capture face has TWO rules.** (1) Unnameable capture: a parameter `filter` beside a nested
  class's `filter(a, b)`; Scala resolves innermost-first and cannot name a shadowed local. Rename the
  capture only where referenced inside the nested body AND the body declares or inherits the name — a
  PARAMETER rename moves surface, so no over-approximation; the nested class is usually anonymous, so
  it lives in a TERM. (2) Scala 3's AMBIGUITY: a name defined in an enclosing scope AND inherited is
  `E049`; java has no such rule (probed: an inherited field shadows an enclosing local through an
  anonymous body, a local class and a grandparent). The reference already points at the MEMBER, so
  the guard reads the enclosing SCOPE. Three cells only a probe gives: it fires for an inherited
  METHOD too; a member the body DECLARES ITSELF is subtracted; an outer FIELD is QUALIFIED at the
  reference, not moved (C16).
- **A name clash and an implementation pair look identical.** `resolveFieldShadowing` renamed a
  collapsed `var w` to `w$shadow` under the interface's abstract `def w`, leaving the class abstract
  until 0 typer errors. A scala `val`/`var` and a PARAMETERLESS `def` of one name across a subtype
  edge are an implementation pair; java cannot produce that shape.
- **A map from an over-approximate key to a single value is a choice nobody made.** `Map[(name,
  arity), Sym]` kept whichever the builder saw LAST: a public `visit(Node)` beside a `protected
  visit(AnchorRefTarget)` emitted `has weaker access privileges`. Index to a `List`, the CALLER
  decides, and where it cannot take the reading whose error direction is known (the COMMON package,
  since an override may only widen). A `find` up the parent chain is the same map by SOURCE ORDER:
  both `convertToActor` overloads emitted at the first one's formal with an `asInstanceOf` the source
  never wrote, while the same pair in the INTERFACE emitted correctly. A head-constructor guard is
  not a key (both heads `scala.Array`); look for the SIBLING walks.
- **The repair is a deletion**: `OverrideGraph.overridden` answers on descriptor with the parent's
  type variables substituted; the local walk keeps only its SCOPE (filter the published edge) and its
  guard for the arity fallback where a DESCRIPTOR is missing. Nineteen of twenty lanes byte-identical,
  one declaration moved, citation total `27 -> 26` in `findings.tsv` alone.
- **A promotion may drop something**: an enum's constructor parameter supersedes a same-named field
  only for the self-assignment `this.f = f`; `HtmlMatch(String open)` beside `final Pattern open` is
  two members. Decide from the emitted TYPE; a WIDENING is two members; the drop, the rename and the
  self-assignment elision read ONE derivation (T11).
- Count what the constructor funnel PROMOTES — parameters and top-level locals — as members.
- **A promotion moves a NAME, never a POSITION.** A FIELD initialiser is JLS 12.5 step 4, hoisted
  above the promoted body; a constructor LOCAL is a step-5 statement emitted where java wrote it.
  Ownership is a symbol lookup (field under the CLASS, local under the EXECUTABLE). 409 of 414 test
  failures on one constructor at 0 errors (C12). Step 4 is fields AND instance initialiser BLOCKS as
  ONE textual sequence (`{ b = 2; } int b = 5;` leaves 5); a body assembled as
  `fields ++ … ++ initBlocks` has already lost it — 16 digests over five ports (C12's correction).
- **It moves MUTABILITY**: a java constructor parameter may be reassigned (a record's COMPACT
  constructor exists for it, JLS 8.10.4); emit `private var` for the parameters really ASSIGNED,
  decided by SYMBOL over the lowered body (every write is a `Tree.Assign`).

## §4.56 — ownership and structural decisions

- Owned iff climbing owners reaches a `program.units` symbol — stronger than "has a definition",
  which anonymous-class symbols lack. `StandardTraversal.mapSymbols` skips unowned symbols (K15).
- Cut only at `.`, `$`, `#`; `com.foo` must not cover `com.foobar`; carry the rest verbatim or nested
  paths break at EMISSION. A namespace rename runs LAST: every other policy is written upstream.
- `dropped-types.tsv` holds `upstream` TAB `emitted` (`Correlate.Dropped`): with only the manifest
  FQN the derived expected-failure rule had never fired on a renaming port. Translate with
  `PackageRenameTransform.renamed`. A frame arrives as `p.Json`, `p.Json$`, `p.Json$Ref`,
  `p.Json$$anonfun$3`; `p.JsonTest` is none; a dropped type has no `srcmap` entry.
- `PortRun.converted` asked *under `sourceRoot`, or under no resolution root*; with nested roots it
  emitted 546 files against 90 in scope. `FrontendConfig.files` is the list.
- **A phase may only conclude something from what it did.** `CollectionsTransform` deleted a cast
  because the source type had a `java.` prefix — `java.lang.Object` has one.
- **Fast-path guards** derive from ALL targets and ask the ANCESTRY, a type parameter's BOUND
  included (K2.6, 16 errors).
- **A `match` arm below a supertype arm is dead.** `CtWildcardReference` EXTENDS
  `CtTypeParameterReference`, so thirteen `SpoonTir` matches never reached their wildcard arm (ten
  answer-changing; census by grep, and a `case r if !r.isInstanceOf[Sub]` greps as neither).
  Reordering cost `5 -> 8` because "nameable" is TWO questions (writable INSIDE an argument, not as a
  cast target). The repair is `SpoonTir.TypeShape` / `TypeShape.of` — one derivation, each site
  stating the answer it gave the shadowed kind, flat by construction (G21).
- **A guard whose confirming artifact can be MISSING states the REFUTATION** (`!x.exists(isObject)`);
  its neighbour needs the opposite polarity because there the signature IS the evidence (G33).
- **A node-kind test owes every syntax java has for the fact.** `case n: Tree.New` missed `C::new`
  (232 sites); the shared index records that reference at the qualifier's `TypeTree`, so ask the
  CONSTRUCTOR's usage at the `MethodRef` (CT6 face C).
- **An instrument's filter states the COMPLEMENT** (`catalog-coverage` matched `^(lowering|phase):`
  and missed the third surface's twenty rows). A documented blind spot is re-derived per corpus
  member (the `test("` counter missed 37 calls across 18 files on the next library).
- **A NAME substitution in a differential suite is applied per RECEIVER**: `.isEmpty` → `== null`
  inverted eight assertions on a `FloatArray`; scalac's own message names the receiver's class. A
  witness list is a measurement — the 727-vs-725 reconciliation was inheritance, not the counter.
- **The survivors are not the DECLARATION.** `sealOf` reconstructed `permits` from visible edges, so
  an excluded or refused subtype made the seal read exact and shipped `sealed`; nothing records a
  decision NOT taken. Carry the interned `permits` set; the unaccountable case takes the conservative arm.
- **A synthesised member carries the parent's scope.** Diamond forwarders, synthesised primaries and
  replayed constructor bodies copy a signature in the PARENT's scope; the `extends` clause makes the
  substitution EXACT (not G8's F-bound). A forwarded member's OWN type parameters come too; one
  `ParentSubst` complete over `TypeRepr`. 41 of 42 `Not found: type`, 243 -> 201 (G25).
- **The override QUESTION needs the same substitution**: compose the frame one `extends` edge at a
  time, a RAW supertype contributes an empty one. 3 errors and 48 moved digests (K28.2). The third
  site is `OverrideGraph` (see `.claude/rules/collections.md`).
- **A refusal predicate reads a SHAPE** (`CtorFunnel.supersedes`, C3's correction): read the lane as
  a population; MAY-assign on the prologue side, MUST-assign on the replay side.
- **An UNOWNED symbol under the port's own prefix is renamed only where the FRONTEND RESOLVED no
  declaration for its TYPE, or the PORT SUPPLIES the name.** The phase moved every symbol under a
  prefix covering an owned symbol, for the replacements a port ships (8 errors without it) — and
  with them a RESOLVABLE third-party class in the same namespace: libGDX moved
  `SharedLibraryLoader` into `gdx-jnigen-loader` and the port renamed a class it neither declares
  nor replaces (6 errors). The rule is three structural facts, no name test: `Flags.isResolved`,
  set by `SpoonTirBuilder.typeSym` where Spoon FOUND a declaration; `Substitutions.dropTypes`
  (upstream) and `PortManifest.injectedFqns` (emitted), either of which says the port supplies the
  name. **Polarity is AFFIRMATIVE and the count is the proof of it**: a refuting `isUnresolved`
  read a PHASE-MINTED name (an `OpaqueSpec` type the base mints, seen from a dependent) as
  resolved and cost textra 2 errors, and a `dropTypes`-only supply test cost the full port 7 — a
  type upstream no longer declares is INJECTED with nothing to drop. Resolution is the TYPE's
  fact: a member climbs to its owner. The classpath is the other half — a port whose upstream moved
  a class to another artifact DECLARES the artifact and puts the jar on `FrontendConfig.classpath`.
- **A recursion that reads RIGHT**: twenty-eight `cd.body.foreach { case c: Tree.ClassDef => … }`
  walks were exact only while the frontend refused method-local classes; `Tree.ClassDef` is a
  `Statement`. State the walk once (`StandardTraversal.allClassDefs`, `TirEmitter.allDeclaredClasses`).

## §4.57 — provenance

Every generated file ships an attribution header; the TIR path regressed a feature the BIR path had.
Source path from the unit's `Origin`; where it cannot be relativised say so. Apache-2.0 met the
obligation by construction; an MIT library carries zero per-file headers, so the port declares
`Provenance.notices` (`notices = […]`): per-library POLICY with an empty default; destination
`src_managed/`; NOT gated on the artifact layer; a declared file that is absent is FATAL.

## §4.575 — porter notes

```
/* porter: <kind-slug> k=v … — <free text> */
/* porter: renamed-member reason=universal rule=member-rename(§4.55) clash=field-vs-method from=align to=align$field */
```

Classification first (`reason=`, then `rule=` or `phase=`+`key=`), detail sorted, `why` after an em
dash; whitespace values QUOTED. `grep -rn '/\* porter:' src_managed` is the inventory. Notes are
DERIVED — `TirEmitter` renders only decisions whose subject it emits; `NoteCoverageCheck` fails a
decision with no note and a note with no decision. Placement: `AtDeclaration`, `InBody` (a dropped
member's note heads the owner's body), `NotInTree` (a dropped TYPE's note is prepended to the
injected file). A note moves member digests; `SubstitutionCheck.dangling` reported 3 phantoms until
it stripped notes.

## §4.58 — trivia

- `SpoonTir.triviaOf` slices by source position; a `VirtualFile` has no buffer, so the convenience
  parse path is handed the text. `SpoonTir.columnOf` guards `getColumn`'s NPE on a bufferless unit
  (`isValidPosition` answers TRUE there); a missing column costs decoration.
- Claimed identity set; coarse harvest after children; the FILE header is decided positionally (no
  code precedes it) and claims the OFFSET first — of two leading blocks Spoon gives the unit the first
  and the PACKAGE the second, and the Apache notice fell down that gap (V3). The file header is the
  one comment deliberately emitted twice (two top-level types, two derived works).
- Homes: `leading` on the surviving node, `Tree.Block.trailing`, else
  `/* trivia: recovered from <path>:<line> */` counted apart from placed ones.
- Nesting `/*` → `//` lines; `TirEmitter.firstCode` skips comments in the `{` separator test (0 -> 2
  errors the first time trivia was emitted). Indent re-derived to the node; text never re-wrapped.
- `TriviaCheck` re-lexes with `CommentScanner` and looks for each normalised body in the emitted
  text, by Java file. Normalisation strips what the emitter may WRAP first (the `//` before a javadoc
  opener), or every such comment is reported lost while present.

## §4.59 — parser synthesis

Records out of one parser: canonical constructor parameters in FIELD order (transposed `new`),
compact-constructor assignments absent (accessors answered defaults), a nested record's constructor
absent and its accessor calling ITSELF. An anonymous class's constructor is materialised with one
untyped parameter and `isVarArgs = false`; JLS 15.9.5.1 says it takes the SUPERCLASS constructor's
parameters, the erased signature selects it, and the fix is at the SHARED LOOKUP (G29; auto-tupled
`Tuple2` where java passed an array). `SpoonKinds.Absence.AbsorbedSilently` is a suspicion about a
KIND; this is one about a MEMBER — a `Lowered` kind can translate three fabricated facts.

## §4.6 — kill switches and flags

- Three edits to `uncheckedGeneric` measured nothing before a kill switch showed the cast came from
  the emitter. Return the input unchanged at the top, print on entry, re-emit; then tag every
  construction site and grep the trace (`balticporter.traceNode`).
- `SpoonTir.formalArity` computed arity inside `catch { case _: Throwable => 0 }` at five sites; ZERO
  is *this type takes no type arguments*, and a generic was emitted un-applied at a green compile.
  Ask what the default MEANS to the caller and how many callers share the catch.
- `DebugFlags.get`: `System.getProperty` first, then `<root>/.balticporter/debug.properties`, then
  `run.properties`. A `-D` on the caller's command line does not reach the forked migration (sbt
  forks with `javaOptions`). A key without the `balticporter.` prefix and a misspelt one (`skipPhase`)
  are read by nothing; `just debug-flags` marks both.
- `baseReports` belongs to the PORT (`PortManifest.baseReports`); `PortMap.searchPath` CHOOSES rather
  than merges, extends the run's own report root, first wins per module; both readers use
  `PortMap.discoverIn`.
- `just debug-emit ROOT FQN [PHASES] [FLAGS…]` prints the pipeline's view of one type (`TirPrinter`
  `canonical` style leaks no `SymId`), never a port's emitted file — no substitution, injection,
  rename or header; giving it a `.conf` would be a second assembly path.
- `balticporter.reportPathRoot` set by the lanes falls back silently when run directly and every
  finding diffs as removed-and-re-added at identical counts; derive it from the port's configuration.

## Lessons of 2026-09-05 (one line each; the numbers are in `PROGRESS.md` §13.26)

- A `Tree.Block`'s `expr` is a STATEMENT position: every body rebuild keeps it in `stats`, or the
  last statement vanishes at 0 errors (CT13: `this.effect = effect` under class-to-trait).
- An emitter decision is recorded at CONSTRUCTION, never while rendering: `PortRun` records
  `ownDecisions` before emission so porter notes can be derived (the constructor `@nowarn` set).
- One scan owns "deprecated use": `DeprecatedUseScan` (class-file `@Deprecated` interned by the
  frontend, lls `orNull`, anon bodies belong to their own members); the phase annotates members,
  the emitter annotates what it renders (secondaries, promoted bodies, super args).
- A rewrite that keeps a list's SIZE is still a change: compare lists, never sizes
  (`UnusedSymbolTransform`'s discard was a no-op for 15 locals while its decision was recorded).
- scalac's `-Wunused` texts: a write-only `var` (local or private) is "not read", an unreferenced
  private is "unused"; a compound assignment READS; `@nowarn` on the definition suppresses both.
- A recorded decision the rewrite never applied hides a misclassification: the moment the
  unused-symbol rewrite reached anonymous bodies, 16 liqp tests failed — an anonymous class's
  private field is state its consumer reads reflectively (K21), so it is suppressed, never deleted.
