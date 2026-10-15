---
paths:
  - "balticporter/engine/**/emit/**"
  - "balticporter/engine/**/tir/**"
  - "balticporter/frontend-spoon/**"
  - "balticporter/frontend-ts/**"
  - "balticporter/frontend-dart/**"
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
  the guard reads the enclosing SCOPE — Scala reports an ambiguous reference when a captured local
  clashes with an inherited method as well as an inherited field, so the capture-rename guard must
  read both off the enclosing scope. Three cells only a probe gives: it fires for an inherited
  METHOD too; a member the body DECLARES ITSELF is subtracted; an outer FIELD is QUALIFIED at the
  reference, not moved.
- **A name clash and an implementation pair look identical.** `resolveFieldShadowing` renamed a
  collapsed `var w` to `w$shadow` under the interface's abstract `def w`, leaving the class abstract
  until 0 typer errors. A scala `val`/`var` and a PARAMETERLESS `def` of one name across a subtype
  edge are an implementation pair; java cannot produce that shape.
- An abstract inherited accessor makes a same-named field its IMPLEMENTATION only while no ancestor
  already implements it concretely: under `Styleable#style` and `TextButton#style` both, a dependent's
  own field `style` was read as the implementation and kept its name, shadowing the concrete method
  (visui `VisTextButton`, `FileChooser`, 2 errors). Kind (a).
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
- **`java.lang.Object`'s anchor is a name match FILTERED, never widened**: kept by name for
  `equals`'s retyped parameter, it froze a STATIC `toString(int)` rename. A static member hides and
  never overrides an instance method (JLS 8.4.8), and another arity is another method, so
  `ExternalSurface.javaLangObjectOverridden` filters the name set by both. Kind (a).
- **A promotion may drop something**: a promoted enum constructor parameter becomes a member and can
  collide with `Enum.name()` or a declared accessor; supersede a same-named body field only when it
  is the SAME member, not just the same name — self-assignment `this.f = f` only; `HtmlMatch(String
  open)` beside `final Pattern open` is two members. Decide from the emitted TYPE; a WIDENING is two
  members; the drop, the rename and the self-assignment elision read ONE derivation.
- Count what the constructor funnel PROMOTES — parameters and top-level locals — as members.
- **A promotion moves a NAME, never a POSITION.** A local variable of a promoted constructor must
  keep its original position in the class body: hoisting it with the fields reorders Java's
  initialisation sequence and causes null reads at run time. A FIELD initialiser is JLS 12.5 step 4, hoisted
  above the promoted body; a constructor LOCAL is a step-5 statement emitted where java wrote it.
  Ownership is a symbol lookup (field under the CLASS, local under the EXECUTABLE). 409 of 414 test
  failures on one constructor at 0 errors came from getting this wrong. Step 4 is fields AND instance initialiser BLOCKS as
  ONE textual sequence (`{ b = 2; } int b = 5;` leaves 5); a body assembled as
  `fields ++ … ++ initBlocks` has already lost it — 16 digests over five ports.
- **It moves MUTABILITY**: a java constructor parameter may be reassigned (a record's COMPACT
  constructor exists for it, JLS 8.10.4); emit `private var` for the parameters really ASSIGNED,
  decided by SYMBOL over the lowered body (every write is a `Tree.Assign`).

## §4.56 — ownership and structural decisions

- Owned iff climbing owners reaches a `program.units` symbol — stronger than "has a definition",
  which anonymous-class symbols lack. `StandardTraversal.mapSymbols` skips unowned symbols: an
  unowned symbol's signature is a class-file fact no phase may move.
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
  included — 16 errors came from a guard that only checked the head type.
- **A `match` arm below a supertype arm is dead.** In Spoon a wildcard reference is a subtype of a
  type-parameter reference (`CtWildcardReference` EXTENDS
  `CtTypeParameterReference`), so thirteen `SpoonTir` matches never reached their wildcard arm (ten
  answer-changing; census by grep, and a `case r if !r.isInstanceOf[Sub]` greps as neither). Their old
  answers are kept on purpose: reordering cost `5 -> 8` because "nameable" is TWO questions (writable
  INSIDE an argument, not as a
  cast target). The repair is `SpoonTir.TypeShape` / `TypeShape.of` — one derivation, each site
  stating the answer it gave the shadowed kind, flat by construction.
- **A guard whose confirming artifact can be MISSING states the REFUTATION** (`!x.exists(isObject)`);
  its neighbour needs the opposite polarity because there the signature IS the evidence — a guard on
  evidence that can be missing must state what refutes it, since java's `?` is bounded by `Object`
  and Scala's by `Any`, so reading an element through a wildcard-typed collection shim needs a cast
  to `Object`.
- **A node-kind test owes every syntax java has for the fact.** Instantiation must be detected on the
  node itself for a generic `new G<...>()`, a field initialiser and a `Type::new` reference, or the
  context parameter never reaches those sites: `case n: Tree.New` missed `C::new`
  (232 sites); the shared index records that reference at the qualifier's `TypeTree`, so ask the
  CONSTRUCTOR's usage at the `MethodRef`.
- **An instrument's filter states the COMPLEMENT** (`catalog-coverage` matched `^(lowering|phase):`
  and missed the third surface's twenty rows). A documented blind spot is re-derived per corpus
  member (the `test("` counter missed 37 calls across 18 files on the next library).
- **A NAME substitution in a differential suite is applied per RECEIVER**: `.isEmpty` → `== null`
  inverted eight assertions on a `FloatArray`; scalac's own message names the receiver's class. A
  witness list is a measurement — the 727-vs-725 reconciliation was inheritance, not the counter.
- **The survivors are not the DECLARATION.** `sealOf` reconstructed `permits` from visible edges, so
  an excluded or refused subtype made the seal read exact and shipped `sealed`; nothing records a
  decision NOT taken. Carry the interned `permits` set; the unaccountable case takes the conservative arm.
- **The diamond forwarder ASKS which external parent is concrete, never guesses**: a class member
  that implements an external interface's default method is legal Java but a conflicting inheritance
  in Scala, so the disambiguating `override` forwarder must also see class-file default methods — an
  injected support type (`RuntimePlan.concreteMembers`) or an interface whose JLS 9.4.3 `default`
  methods the frontend read off the class file (`Program.internedDefaults`); an ABSTRACT interface
  method mints nothing, and minting at every class-plus-interface pair is forbidden.
- **A synthesised member carries the parent's scope.** Diamond forwarders, synthesised primaries and
  replayed constructor bodies copy a signature in the PARENT's scope, so the parent's type parameters
  are substituted through one shared substitution or they are not found in the subclass; the
  `extends` clause makes the substitution EXACT (a different case from a partially-nameable
  F-bounded class, which has no consistent way to fill its raw type arguments because filling one
  sibling formal breaks another — a genuine Scala expressiveness limit that is refused rather than
  substituted). A forwarded member's OWN type parameters come too; one
  `ParentSubst` complete over `TypeRepr`. 41 of 42 `Not found: type`, 243 -> 201.
- **The override QUESTION needs the same substitution**: whether a member overrides a parent member
  is asked with the parent's type parameters substituted along each `extends` edge, composing the
  frame one `extends` edge at a
  time, a RAW supertype contributes an empty one; a name-and-arity lookup returns all candidates so
  the caller decides. 3 errors and 48 moved digests came from getting this wrong. The third
  site is `OverrideGraph` (see `.claude/rules/collections.md`).
- **A refusal predicate reads a SHAPE** (`CtorFunnel.supersedes`): read the lane as
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

## §4.57 — the resource half of the deliverable

- A port ships TWO outputs: the emitted Scala and `src_managed/<set>/resources`. A consuming build
  that collects only the sources compiles green and throws at the first lookup — flexmark's
  `Html5Entities` reads its entity table in a static initialiser, so nine ssg suites failed with
  `Could not initialize class …Html5Entities$` while the port shipped the file correctly. The
  directory is wired through a resource GENERATOR that runs after the port
  (`SbtGen.resourceFiles`), never a directory test evaluated while the build LOADS: on a fresh
  checkout the port has not run, the test reads false, and the fallback is silently empty.
- A classpath lookup is a STRING LITERAL, so a renaming port ships the resource at the UPSTREAM
  path while a hand port moved it into its own namespace; both trees can sit on one classpath with
  the file reachable at neither name the code uses.

## §4.58 — trivia

- `SpoonTir.triviaOf` slices by source position; a `VirtualFile` has no buffer, so the convenience
  parse path is handed the text. `SpoonTir.columnOf` guards `getColumn`'s NPE on a bufferless unit
  (`isValidPosition` answers TRUE there); a missing column costs decoration.
- Claimed identity set; coarse harvest after children; the FILE header is decided positionally (no
  code precedes it) and claims the OFFSET first — because the parser attaches only one of several
  consecutive file-leading comment blocks, so a comment belongs to the file header only when no code
  precedes it: of two leading blocks Spoon gives the unit the first
  and the PACKAGE the second, and the Apache notice fell down that gap. The file header is the
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
absent and its accessor calling ITSELF. The parser synthesises an anonymous class's constructor, so
call-site rules such as vararg packing must resolve it against the superclass constructor Java
actually wrote: an anonymous class's constructor is materialised with one
untyped parameter and `isVarArgs = false`; JLS 15.9.5.1 says it takes the SUPERCLASS constructor's
parameters, the erased signature selects it, and the fix is at the SHARED LOOKUP (auto-tupled
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

## More rules (one line each)

- Scala 3 forbids a subclass field, even a `private` one, of the same name as ANY non-private
  parent field, `private[pkg]` from another package included ("cannot override variable"); java
  hides it. Probed on 3.8.2. So narrowing a parent's access never frees the name; only a rename
  on the subclass side does. Kind (a).
- A java identifier ending in `_` is backticked by `TirEmitter.esc` (scala lexes `other_:` as ONE
  operator identifier: `val other_: Pair = …` is a parse error); a PATTERN binder takes a space
  instead (`escBinder`), since a backticked name in a pattern is a stable identifier, not a binding.
  Kind (a).

- A java `T...` the port DECLARES is emitted `T*` (hand-port parity: the reference spells every
  vararg repeated, and a consumer hand-added `addRoomTypes(RoomType*)` beside the `Array` one). Every
  call passes a REPEATED tail or SPREADS java's array (`SpoonTir.varargPack`, one path for owned and
  external callees); a method body binds `x$arr` once from `ArraySeq.unsafeArray` (the caller's
  array, so a write through it is seen, as in java) and a constructor reads it in place. A parameter
  that is a MEMBER (a superseded enum field) keeps `Array[T]`, and so does a member beside an
  overload differing ONLY in the vararg's element (`C(String...)`/`C(FileHandle...)`: both `T*`
  erase to one `Seq`, E120) and a member of a DROPPED type (its hand-written replacement states the
  slot, where the call still binds at that type's own name) — their calls, a `this(...)` and an
  anonymous class's superclass call pass java's array (`arrayVarargArgs`). A constructor REPLAY
  substitutes an argument as the VALUE the parameter holds (`CtorFunnel.argValue`: a spread is its
  array, a repeated tail the array java built), or `super(ms)` replays `new X(ms*)`; an anonymous class implementing a
  `T...` method stays a class (`sam-anon->lambda` guard `RepeatedParam`: a function literal cannot
  declare `T*`); an owned override of an external NON-vararg `T[]` member, and a java LAMBDA at an
  owned `T...` SAM, are loud untested edges. Kind (a).
- The element-only overload test reads the overloads the class INHERITS as well as its own, and an
  OVERRIDE takes the form of the member it overrides: an enum declaring `m(Actor...)` beside its
  interface's `m(CellWidget...)` emitted `Actor*` there and `CellWidget*` in each constant body,
  which then overrode nothing (visui `TableLayout`, 3 errors). Kind (a).
- The `x$arr` binding's fallback COPIES a caller's `Seq` that is not an `ArraySeq` — every literal
  vararg on Scala.js arrives as a JS-array-backed `Seq`. At an element whose `Object` bound the
  witness phase dropped, `toArray[Any]` builds boxes, and `System.arraycopy` into the `int[]`
  backing store throws (lls `DynamicArray` "addAll from plain array" on JS; on the JVM only a
  non-array `Seq` spread reaches it). The phase that knows the representation tags the parameter
  (`VarargArrayFactory`, the witness's `create`) and the copy goes through it; no witness in scope is
  `witness` lane `VarargCopy`. Kind (a).
- **An `ArraySeq`'s backing array is taken only at the RUNTIME class the element needs** (the
  component against the element's `ClassTag`, or the witness's own array's class), a copy otherwise:
  an empty `ArraySeq`'s backing array is `Object[]`/`Nothing$[]`, and the unchecked cast threw on JS
  (ashley `Family.all(...)` over `Class[? <: Component]*`, 17 sge-ecs failures). A GENERIC element
  nothing builds an array of is built by the java CALLER at its static type and spread
  (`new Anim[String](0.1f, Array[String]("a", "b")*)`, as javac passes `String[]`); a hand-written
  scala caller passing plain elements still gets an erased `Object[]` — `heap-pollution` lane
  `UnreifiedCopy`, one row per declaration. A java `f(null)` at such a slot is already a null
  ARRAY (`null*`, `varargHoldsArray`). Kind (a).

- A method reference's lowered call reads the member's DECLARATION for its arity (`parenlessDecl`:
  a `def` with no clause, or a collapsed field), never the symbol's java `MethodType`; a BOUND
  reference to a parenless member binds the receiver once and wraps it in a lambda. So a method
  reference is no reason for the bean phase to refuse a pair: simple-graphs' `Edge::getWeight`
  rendered `self$.getWeight()` on a parenless getter and froze the `weight` rename. Kind (a).

- An enum constructor parameter is a MEMBER only where it stands for a field it supersedes or the
  body reassigns it (`EnumShape.memberParams`); any other is emitted plain, as java's is no member.
  Emitted `var`, a parameter named `name` pushed anim8's `DitherAlgorithm` into the sealed shape
  (no `java.lang.Enum`, `name()` answering the parameter) and `values` kept its parens; `values` is
  now parenless on BOTH shapes, one surface whichever shape an enum took. Kind (a).

- A check over EMITTED code is scoped to the units the run WRITES, not to the whole model: a dropped
  type's body stays in the program (the drop removes the declaration from emission, not the tree),
  so the signature check reported 5 orphaned calls inside a dropped visitor — calls whose text is
  never written. `RewriteTrace.check` takes the written-unit set; an unknown enclosing position
  answers "written", because not knowing where a use is is no evidence that nothing is emitted.
- A raw Java generic type is rendered with wildcard arguments (`[?]`) everywhere it appears, and the
  wildcard form must stay consistent between a member and its overrides, not just at the declaration.
- A callee's own type variables mean nothing at the call site: coerce an argument using the call's
  explicit type arguments or its inherited instantiation, never the callee's own declared variables.
- Converting an anonymous class to a lambda needs the lambda's result type to be nameable, so a
  `return` inside it can be emitted as a nested method; without a nameable result type the emitter
  must refuse visibly rather than silently drop the `return`. A raw-typed single-method target
  compiles cleanly as a lambda at a wildcard type, so that case needs no extra guard.
- A `Tree.Block`'s `expr` is a STATEMENT position: every body rebuild keeps it in `stats`, or the
  last statement vanishes at 0 errors. When super-call arguments are stripped from a constructor
  body the block's final expression must be preserved the same way, otherwise a field assignment
  vanishes and the field reads null at run time (`this.effect = effect` under class-to-trait).
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
  private field is state a reflective reader (a bean serialiser, a third-party framework) reads back,
  so it is suppressed, never deleted.

## Jumps

- A NAMED loop boundary (`break L`, or an unlabelled `break` beside a `continue`/an interposed
  boundary) is a `ControlThrowable` SENTINEL thrown and caught at the loop, never a named
  `boundary.break`: Scala.js lowers the latter across nested `while`s to a JS `break` of the
  INNERMOST loop (TimSort's merge read index -1 on every merging input). `continue L` keeps the
  BODY boundary. The broad-catch guard (§4.4) rethrows BOTH shapes — `boundary.Break[?]` and, at a
  `Throwable` catch, `ControlThrowable` — since `NonFatal` spares the sentinel but `case t:
  Throwable` does not. A spec that asserts the spelling of a jump asserts THIS one.

- **A colon switch case falls through exactly when its statements CAN COMPLETE NORMALLY (JLS
  14.22)**, decided by `SpoonCompletion` for both frontends: a trailing `continue`, labelled
  `break`, an if/else whose branches both leave, an infinite loop with no break out, a `try` whose
  `finally` cannot complete. Reading only the last statement's node kind duplicated the following
  cases into arms java never runs them from (RegExodus `Matcher#search` duplicate declarations).
  Where a rule is not modelled the answer is "can": a wrong "cannot" deletes code java runs, a wrong
  "can" only duplicates unreachable code. Kind (a).

- A member reached through a CAST `this` (`((Sub) this).f`, `((Sub) this).m()`) is an ordinary
  receiver: the bare and `this.`-qualified shortcuts for an own `this` dropped the cast, and the
  subclass member was looked up on the superclass (RegExodus `Term#toString`, 1 E008). Kind (a).

- An INNER class's companion is emitted inside the enclosing class's body, so its class-initialisation
  trigger names it by simple name from the innermost inner level down, never by the package path
  (`val _ = p.Outer.Inner` is not found; regexodus' 18 upstream tests did not compile). A SUBCLASS
  trigger whose companion is outside that body has no path at all: declined and counted
  (`class-init-trigger` `SubclassInitPathless`). Kind (a).

## Construction and generic arrays

- `outer.new Inner()` names the enclosing instance; scala names it in the PATH, `new outer.Inner()`.
  The frontend carries a local/parameter qualifier as the inner type's term prefix and `ctorTpe`
  renders it; dropped, gdx-tools' `mask.new MaskIterator()` read `new MaskIterator()` outside `Mask`
  (1 error). Any other qualifier (a field, emitted a `var`; a call) is no stable path: it is bound
  once, first, to a local `outer$q` the path names (visui `chooser.new FileItem(…)` rendered the
  projection `new FileChooser#FileItem`, 1 E083). Kind (a).
- An external callee's formal that is an array of the METHOD's own type variable
  (`<T> T[] Arrays.copyOf(T[], int)`) takes the argument's own array type: its erasure reads
  `Object[]`, and the covariance cast to `Array[Object]` bound `T` to `Object` and broke the result's
  `String[]` slot (gdx-tools, 2 errors). A real `Object[]` formal still takes the cast. Kind (a).
- A body a port SUBSTITUTES is text the write set cannot see into: a private field of its class the
  text assigns stays a `var` (`BeanCollapse.writtenSymbols`), or the emitter's never-assigned `val`
  idiom read java's own writes, re-spelled, as absent (screenmanager `currentProcessors`, 2 E052).
  Conservative by construction: a local of the same name also counts. Kind (a).

## Non-Java body translator

- (a) The non-Java body translator (frontend-ts) must refuse a value it cannot TYPE rather than render
  it: an untyped `[]`/`{}`, a Double or an unknown value in string concatenation (JS prints `2`,
  Scala `2.0`), `str.replace` with a non-literal pattern (JS replaces the FIRST occurrence of a
  string), and a JS built-in static with no Scala namesake each compiled before they were refused,
  and with them KaTeX went 3 -> 27 translated bodies with no test failing.
- (a) A type a callee's signature names may be spelled through the CALLEE file's imports: the body
  translator writes out only primitives and `String` as type arguments and ascriptions, and leaves
  every other type to inference from the slot (`HtmlDomNode` not found in KaTeX's BuildTree).
- (b) One library's JS naming conventions (class prefix, type-tag field, node factories, methods
  moved into helpers) are a `JsConventions` value the consumer passes; the empty value reads plain JS.
- (a) The body translator checks a member read against its receiver's declared type for EVERY
  receiver whose type it knows, a member read included: checking identifiers only let
  `context.parser.settings` (the reference holds `parser: AnyRef`) reach scalac once the identifier
  became typed. The checker's type answers only where it is unambiguous (a boolean; a LOCAL of a
  class the reference declares) — at a member read it was wider in the reference, a compile error.
- (a) JS `splice` clamps its bounds, removes to the end on one argument, inserts the rest and
  returns the removed array: no `Buffer.remove` call is faithful, so it is refused (one KaTeX body
  was emitted as `remove(i, 1, xs*)`).
- (b) A consumer's deliberate divergences from upstream are `NonJavaBodies.Library.recordedDivergences`,
  matched by file, member and overload: they keep the reference body ahead of every other reason,
  and an unmatched record is reported; a faithful translation would otherwise silently undo them.
- (a) JS semantics the translator spelled wrong are spelled faithfully or refused, each found
  reading a translated body against its source: a `finally` runs (it was dropped); a JS `catch`
  never sees a jump, so `case b: boundary.Break[?] => throw b` precedes it; a last `if` with no
  `else` returns the result type's absent value; `===` on two reference types is `eq`, parenthesised
  since a letter operator binds looser than `&&`; a local nothing writes is a `val` whatever its JS
  keyword; `new Set(xs)` and `new Map(xs)` take the elements (`Set.from`), never the iterable; JS
  reads past an array's end as `undefined` where Scala throws, so a read of reference elements is
  `xs.lift(i).orNull` (a compressor reading its parent off a one-deep stack threw 1444 times). Refused:
  in-place `sort`/`reverse` (Scala returns a copy, the statement was a no-op), a negative or
  non-literal `slice` bound, `substring`/`charAt`/`charCodeAt` (JS clamps and answers "" or NaN
  where Java throws, and `charAt` is a String), `match`/`exec`, `parseInt`/`parseFloat`, a coercing
  global (`isNaN`, `Number`) on a non-number, `join` of non-strings (and `join()` joins with ","),
  `Object.create`, a `RegExp` constructor.
- (a) The reference index reads ANCESTRY: a member read is checked against the receiver's class and
  every class and trait it extends, nearest first, and a class declaring nothing of its own is still
  a known type. Keyed by the declaring class only, `thing.body` on a block statement whose trait
  declares `body` was refused as `wrong-member-access`.
- (a) A call on a receiver of unknown type reads as `Boolean` only when every class declaring the
  method returns `Boolean` (any other type then fails to compile rather than reading differently);
  a `.length` condition is `!= 0`. No other agreed result is read this way: an `Int` read as a
  truthiness `!= 0` would compile against an `Option` and always hold.
- (a) A name a JS function's ENCLOSING function binds shadows every module-level name: it resolves
  only to a member of the class the reference made of that function (`enclosingClass`, which the
  consumer names), and is refused (`enclosing-function-binding`) where nothing holds it. Resolved
  through the callee index, a nested parser's `is(...)` was refused as `wrong-function-ref`, and a
  shadowed module-level namesake would have been called instead.
- (b) A JS fixed-length array the reference reshaped into a record is `JsConventions.tupleRecords`
  (class -> field per position, its constructor taking exactly those fields): `p[0]` reads the
  field, `[a, b]` at its slot builds it, `const [a, b] = p` binds its fields. Every other array
  destructuring is refused, since JS reads past an array's end as `undefined` (an unread pattern
  was emitted `val _ = center`); a destructuring source is evaluated once, and the exporter flags a
  binding's `rest`, `renamed` and `default` forms, which its children alone cannot show.
- (a) A JS type tag is compared as the string it is (`x.nodeType == "Call"`), never as a type test:
  a subclass carries its own tag, so `isInstanceOf[Call]` held for a `New` where JS said no. The
  test narrows `x` only to the ONE class the reference declares with that string constant, and a
  `{ <tag>: "Y", ... }` literal builds that class where the slot admits it.
- (b) One object literal passed where the reference spells the options bag as named parameters
  (`extend({ color })` against `extend(color = …, size = …)`) becomes named arguments, each at its
  parameter's type; a key naming no parameter, or an omitted parameter with no default, keeps the
  `js-map-construction` refusal.
- (a) No translated body may throw where JS would not. JS reads past an array's end, an absent map
  key and `pop`/`shift` of an empty array as `undefined`: a read of references answers null
  (`lift(i).orNull`, `getOrElse(k, null)`, an emptiness test before `remove`), a `pop` nothing reads
  removes only when there is something to remove, and any other read is refused (`read-past-end`)
  unless a length or `has` test on the SAME receiver encloses it (an `if`/loop/`?:` condition or the
  left of an `&&`). KaTeX's `scriptFromCodepoint` read `block[0]` of a numeric pair: 46 -> 45.
- (a) A JS array local is a mutable buffer only where its annotation names an element type the
  TARGET reference file writes itself (`ReferenceDeclarations.typeNamesIn`, passed as
  `nameableTypes`), since `[]` must name it. Handing the buffer to a slot the reference declares
  immutable copies it, which is unobservable only at the local's LAST use: declared once from an
  array literal, every earlier read a member read, an element access or a `for … of`, no loop or
  function around the hand-over that the declaration is outside of, and any closure holding an
  earlier read run in place by an array method. graphs-commons 36 -> 41.
- (a) JS grows an array stored to at or past its end, where Scala throws: an element store is
  refused unless a length test on the same receiver guards it (points-on-curve's `b[0] = …` on an
  empty array compiled and would have thrown on every curve of four points or more). `push` of two
  or more arguments evaluates them all before appending any, and answers a length no append does.
- (a) A lambda's parameters are locals that shadow every module-level name (rotateLines' `line`
  was emitted as a module function of that name), typed by the array a method's callback walks;
  a function that can run off its end answers `undefined`, refused unless the caller discards the
  result (`forEach`).
- (a) A JS `switch` runs on from a clause control does not leave into the next one and tries
  `default` after every label wherever it stands: each arm is its clause's statements plus those
  it falls into, `default` is the last arm, a non-literal label is a guard (`eq` for a reference),
  and a `break` before a clause's end is refused (`switch-break-in-middle`), since a `match` arm
  cannot be left early — an empty `case "hachure":` above `default:` had been emitted as an arm
  doing nothing. A string label at a selector of unknown type is refused (`switch-label-type`).
- (a) An object literal's values are evaluated in the LITERAL's order, so the named arguments are
  written in that order (rough.js reads `random()` inside them); a field the reference names with a
  keyword is matched by its key and written backticked. An array JS changes in place (`push`,
  `pop`, an element store) where the reference holds an immutable collection is refused
  (`mutated-immutable-collection`). graphs-commons 41 -> 44, KaTeX 46 -> 48.
- (a) A present `""`, `0`, `NaN` or `false` held in an optional is as falsy as an absent one: the
  truthiness of `Nullable[String]` is `isDefined && get.nonEmpty`, never `isDefined` alone. Under
  `if (x)` on an optional local or parameter the body never reassigns, `x` reads as its value. A
  present value at an optional slot is wrapped, so it is checked against the slot's INNER type
  (KaTeX's `Options -> Nullable[Options]` had refused 20 sites); an optional stored where a value is
  required is refused, an assignment included. KaTeX 48 -> 51.
- (b) A JS module object indexed as a map (`symbols[mode]`) that the reference made an object
  declaring `apply` is read through that `apply`, whose answer for an absent key is the
  reference's; the consumer names the object through `apiLookup`. A map key is checked against
  the map's key type.
