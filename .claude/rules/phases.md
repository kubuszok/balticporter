---
paths:
  - "balticporter/engine/src/main/scala/balticporter/transform/**"
  - "balticporter/api/**"
---

# Parameterised phases — the obligations of a §1(b) rule

Detail for `CLAUDE.md` §1(b), §3 and §4.5. The core states the rule; this file states what the
rule costs and where it was measured.

## Choosing the default

A (b) whose empty parameter is a no-op still needs its DEFAULT chosen against what the phase did
BEFORE it had one. The two questions look like one and are not: `Everywhere(Set.empty)` is both the
no-op and the pre-scope behaviour for a retyping phase. For `PortabilityCheck` they come apart — the
empty target set is the no-op and emphatically not the default, because `Set.empty` (or `Set(Jvm)`)
would empty the rule list on every port at once and collapse three counted lanes to a floor in one
commit: a baseline promotion nobody can read, indistinguishable from fifteen ports getting better.
The default is *every question the phase asked before it was parameterised*; the narrowing is a
port's own declaration; the parameter's arrival is provably flat.

**Every rule that RETYPES declarations takes a `RuleScope`** (`api`) — `Everywhere(except)` or
`Only(include)`, matched by FQN and cut only at a `Symbol.fullName` separator (§4.56), with
`Everywhere(Set.empty)` the default and the pre-scope code path.

**A rule that ADDS declarations takes one too, with the OPPOSITE default.** A scope on a retyping
phase is an opt-OUT. A phase that MINTS members has no pre-scope behaviour to preserve, and its
unrestricted form would put new NAMES on every matching declaration in every port to serve one. Its
no-op is `Only(Set.empty)`; what does not carry across is the SPELLING, so `TransformFactory.scopeOf`
takes the phase's own default rather than assuming one. (`NullaryArityTransform`,
`AddMembersTransform`, the `bpFreshState`-style holder members.)

## SurfacePolicy

The scope is a fact about the emitted SURFACE, so the phase implements `SurfacePolicy` — two modules
scoping it differently emit signatures that compile alone and cannot compile together (§1.5). A
scope is not the only way a policy reaches the surface: a parameter that decides a PARENT, a member
name, a type's kind or its package is the same fact, so `SurfacePolicy` is owed by every (b) whose
parameters a reader could point at in the emitted signatures. `TestFrameworkTransform` owed it for
two (`suite`, `testMember`). Nothing reports the omission: `PortManifest.fingerprint` falls back to
the phase NAME, under which two configurations compare EQUAL, so `SurfaceMissing` cannot see the
difference and a same-name pair can be neither compared nor composed (`ENGINE-LIMITS.md` CT9).

## The fingerprint no-op rule

§1(b)'s no-op rule is owed at the FINGERPRINT as well as at the emission. A key that contributes an
empty SEGMENT unconditionally moves the `policy=` field on every port the phase runs in, the day the
key is added: twenty baselines to acknowledge for a key not one port uses. Omit the segment where the
key is empty — an unstated key and an empty one render the same string, a non-empty one always
contributes — so the arrival is flat BY CONSTRUCTION and the corpus run confirms rather than absorbs.

## Every seam is counted

- Where a coercion exists, insert it; where none can (a `mutable.Buffer` is not a `java.util.List`),
  refuse and report with the §1 classification.
- Read the boundary through the DECLARATION: a position-blind `transformType` has already remapped
  the reference node's type, so a check reading node types reports ZERO on exactly the seam the scope
  made.
- The same seam exists at every EXTERNAL CALLEE, scope or no scope — a class file's signature no
  phase can move — and it is not JDK-only: a third party's parser returning a `java.util.List` is the
  same shape. Measured at 15 errors against 0 findings on one third-party package (K15). Where the
  phase can wrap at the seam it must; where the FORMAL is unknowable it counts, and says which.
- The REWRITE reads the receiver the same way, through the same function. Keyed on the node type it
  fires against the JDK type the declaration kept (`b.raw ++= mine` on a `java.util.List`).
- A formal that stays JAVA is read LITERALLY, never through the phase's own remap: read through
  `remap` the slot says it wants the port's shim, the wrap fires, and a standalone runtime trait is
  handed to a class file asking for `java.lang.Iterable`. Where the class file cannot be read there
  is no formal, and that is what the count stands for.
- A COERCION may not precede a REWRITE of the same call: `items.addAll(other.items)` became
  `items ++= JavaCollection.from(other.items)` and `++=` takes an `IterableOnce` — 4 errors on a port
  that had 0, every count flat.

## accountedBy — the pipeline asks for the count

A phase that retypes and counts NOTHING is invisible to every instrument: both sides of most slots
move together, the port compiles, no count moves. So a retyping phase declares `Rewrite.accountedBy`
— the check LANES that count its residue, as symbols never strings — and `Pipeline.runTraced`
DERIVES what it moved by comparing each owned symbol's `info` across the phase. A phase is not asked
what it retyped, because that is the one number it could be wrong about. `rewrite-callsites` reports
a phase that moved declarations and names no lane, and a phase naming a lane that did not RECORD this
run. It counts PHASES, not usages — the generic `usagesOf(s) \ callSites` form is not the boundary
counts (K5.10; its first run named two retyping phases that had never answered).

## Obligations the engine's own translation created

- **A drop key is a statement about the LIBRARY's surface.** Where the member is one an emitted
  PARENT declares, dropping it breaks an obligation — the class needs to be abstract, and nothing
  reports that before 0 typer errors (§3). When the residue exists BECAUSE a phase emitted a parent,
  chose a name or retyped a field, the answer is the phase's; where no translation exists, emit the
  JAVA CONTRACT'S OWN refusal (`UnsupportedOperationException` at an optional operation), louder than
  java and never quieter. One `Not Found` traded for one `needs to be abstract` (K5.7).
- **A MODIFIER is part of what a re-parenting moved.** `override` was justified by JAVA's resolved
  hierarchy; after a re-parenting scalac reads `overrides nothing` at a perfect translation — 73 of
  one port's 131 `RefChecks` rows (K28). The phase that moved the parent owes the modifier; state
  the target's overridable surface as a TABLE, admissible because both errors are loud.
- **The guard for *does the port emit the far side itself* is the LOOSER key** — name and arity,
  not the parameter spelling (java permutes type-parameter names across an override): 77 strips
  against 71 errors closed, read against each other.
- ***Did the phase MOVE this* is not *can the phase ANSWER for what it became*.** Ask the POSITIVE
  question — is the RESULT one this code holds a surface for — so a second table widens the answer.
- **A repair for a disagreement asks whether the two languages disagree HERE.** A diamond forwarder
  is licensed by scala's linearisation conflict and NOT at a `final` superclass member; minted there
  it overrides a `final` member — 18 rows, invisible until 0 typer errors. Carry the difference's own
  PRECONDITION into the guard; where the repair cannot apply, leave scalac's own message.
- **A refusal SUBSTITUTED FOR A BODY is licensed by the defect the phase caused, never by the member
  it sits on.** Match by SIGNATURE (a class implementing `Map.Entry` may declare `setValue(int, int)`
  beside it), and check the TRANSLATED body still references something the mapping removed; record
  what the substitution BROKE on the decision (K5.7's correction).
- **A SYNTHESIS asks the same question.** A record's derived `equals` is skipped when the class
  "already has it" by (name, arity) — exact for arity-0, wrong for `equals(String)`; the fallback is
  REFERENCE equality at a green compile. Ask what the derived member would COLLIDE with in the SCOPE
  it is emitted into: a record's `unapply` goes in the COMPANION, so an instance `unapply` cannot clash.

## A class a FRAMEWORK instantiates has no caller to change

A test suite, a `ServiceLoader` implementation, a bean: the closure sees no `new`, concludes nothing
must be fixed, and a parameter added to one compiles perfectly and cannot be constructed at run time
— 0 scalac errors, a whole suite silently gone (CT7). A constructor-changing phase owes a third
answer beside attach and refuse: *this declaration takes the value without taking a parameter*, and
where that value comes from is a port's to say (a hand-written file may carry a `given`).

- **The port's HAND-WRITTEN half is outside the closure.** Which answer a shim takes is READ OFF THE
  GENERATED CALLER: reached only from threaded declarations it may take the clause; reached from an
  unthreaded generated declaration its answer is the residual global the phase counts. A body
  substitution may change what a member DOES and never what it TAKES.
- **An escape hatch that takes an EXPRESSION owes the program a way to write one.** Ask *is there no
  value, or no NAME?* Where the value exists in a constructor parameter, the fix is one member — let
  the threaded type KEEP what it was given under a port-chosen name — scoped `Only(Set.empty)`
  (PROGRESS §10.8.11; the hand port had written that member by hand).
- **Asked a SECOND TIME the answer differs, because WHERE THE CLAUSE ATTACHED decides what a name
  means.** An all-`static` lifecycle class takes its clause on METHODS; the answer is a HOLDER the
  method assigns and an accessor that THROWS when nothing has captured one yet. Two keys, not one
  deriving two shapes: the surfaces differ (instance `val` vs companion `def` over a `var`) and the
  expressions differ. **A mechanism's ANSWER is indexed by where it ATTACHED.**

## Refusal enumeration (§3)

- An idiom transformer has no DIFFERENCE mandate (`DESIGN.md` §8.15): the faithful translation
  exists, so a green suite is what it produces either way. Enumerate the behavioural deltas; each is
  (i) guarded, (ii) impossible by the emitted SHAPE, or (iii) COUNTED — one lane row per declined
  site NAMING THE GUARD. `refused = 0` is a bar met by converting nothing.
- The wave's `members.tsv` blast is CLASSIFIED: every moved digest attributed to a recorded
  `Decision`, a rewritten call site or a changed note; the residue is EMPTY. The SAM wiring came
  back with two members explained by nothing — an emitted NAME keyed on a program-global counter (M10).
- **"Refuse loudly" is a claim about the emitted text.** A bare `return` under a function literal is
  scala's NON-LOCAL RETURN — three in libGDX core at 0 errors (M6). Count the refusal when written.
- **A repair at the USE cannot discharge the DECLARATION's obligation.** An F-bounded result pinned
  at the call left the OVERRIDE EDGE (JLS 8.4.2 erasure override) unmeasured for six waves; stating
  the type at the declaration closed 8 of 42 `RefChecks` rows and `overload-risk` fell 6 (G8.7 → G8.10).
- **A new arm for an existing node kind inherits that node's obligations.** `catalog(undischarged)`
  `5 -> 7` the first time an emitter arm was added for a phase-minted `Tree.Typed`; discharge
  not-fired — `None` is a FACT there, not a default.
- **A refusal predicate reads a SHAPE, and every shape it does not recognise is counted as a WALL.**
  `CtorFunnel.supersedes` recognised `this.f = <e>` and nothing else, refused a parent constructor
  whose body is one `Tree.If`, and every renderer was built with no options — 42 CommonMark examples
  wrong at 0 errors (C3's correction). Read a refusal lane as a POPULATION: sample the sites. A
  predicate asked in TWO DIRECTIONS needs TWO functions — MAY-assign (branch UNION) on the prologue
  side, MUST-assign (INTERSECTION) on the replay side.

## §4.5 — never model a Java interface on a Scala collection trait

Java interfaces are small and orthogonal, so a class implements several: 14 classes in libGDX core
implement both `Iterable<E>` and `Iterator<E>`. Scala's collection traits are large and interlocking
and that shape is ILLEGAL under them — `Iterator.iterator` is `final`, `seq` arrives from both
parents — and inheriting imports hundreds of members that clash with the class's own `size`,
`isEmpty`, `remove`. An extension adds a VIEW and cannot conflict; a parent adds MEMBERS and does.
`foreach` on both an iterable-and-iterator pair made every `for` ambiguous.

## §4.45 — a lane's kinds are the classification read one level down

A kind that mixes a residue which COMPILES with one that cannot gets one instruction written for one
of them. `ResidualGlobalRead` carried a kept global read and an unsuppliable constructing use (`No
given` every time). Where the mechanism's prose has a word for a population its lane does not, the
lane is one kind short. A split is flat by construction (rows and count unchanged, `findings.tsv`
sees the kind); the screen is *would a reader act differently*.

## Rule lines moved out of the archive (2026-09-16; ids kept)

- **K52** a `static final` scratch instance is one object per class: `ThreadConfinedStaticsTransform(fields)`
  emits `new ThreadLocal[T] { override def initialValue(): T = e }` as trees — never text, never
  `withInitial` (absent from the Scala.js javalib). Four counted guards: not static, not final, assigned
  after init, initialiser not a fresh allocation.
- **K53** a derived seed is exact, so a java STATIC whose reference twin is `extension (a: T) def m`
  derives its slot from `SurfaceDecl.receiver` as parameter 0 (`slotTypes`); never for an instance method.
- **K54** a member rename on an EXTERNAL redirected type: hits are the owned overrides whose closure is
  anchored on `(source, member)`, within the redirect's scope, each request `detachedParents = Set(source)`.
  A second unknown parent's surface is PORT policy (`TypeRedirectTransform(external = default ++ …)`),
  never a new closed platform row. Walk such a policy on a spec before regenerating a consumer.
- **K55** under a named/Option null target a boxed element is the primitive (`Nullable[Int]`); union keeps
  the box. A wrapper over the box and over the primitive are one slot (`sameSlot`) — no ascription
  between them. A field's derived rows are keyed `fullName:field`; a qualified-private reference member
  is a derivation input, not a compared surface; a package-private java FIELD the reference ships public
  derives a `Public` row.
- The bean fold's `setterOnly` guard reads an INHERITED getter as in scope (`graph.ancestorsOf`).
- **K56** a mechanism that DEFERS a class initialiser (`DeferredInit`'s `$set`/`$value` holder) stands in
  for JLS 12.4.2's class-init LOCK as well as its trigger: the value is assigned under the companion's
  monitor, double-checked, the flag written LAST. Only a parallel suite sees the race (sge textra 1 NPE
  -> 0 at 0 compile errors); a green serial run is no evidence.

## The §1(b) phase table (moved from CLAUDE.md 2026-09-16 — loads with the transform files)

| phase(params) — mechanism | policy |
|---|---|
| `ClassTableTransform(redirects, scope)` — re-point a reflective name lookup at an explicit table | which method → which table, `RuleScope`; disjoint scopes compose, overlapping refuse |
| `StaticForwarderTransform(List[Forwarder])` — a wrapper's statics are members of argument 1 | which wrapper, receiver, members |
| `Substitutions` — do not emit these types/methods; inject this Scala instead | which ones, replacement sources |
| `CollectionsTransform(scope, families, familyScopes, retarget, retargetRewrites, retargetRewritesByDesc, retargetTypeArgs, retargetCoercions, reifiedCarriers, reflectiveSinks, retargetIndexedFields)` — retype collections, API-map call sites; JDK table is a §1(a) constant | which declarations, which extra families (per-entry `RuleScope`, D12), which library types retarget (java FQN → scala FQN that extends the source), per-member rewrites (`Rename`, `BoolDispatch`, `Construct`, `ForEach`, `Collect`, `Chain`, `FieldWrite`, `DropWrite`, `IndexedField`, `Template`), type-arg maps, coercion templates, reified carriers (K20), reflective sinks (K21), indexed field rewrites keyed by (source, field) to avoid key collision with method rewrites. `MergeablePolicy`: independent keys union, same source/different target refuses |
| `PrimitiveToOpaqueTransform(OpaqueSpec)` — seed, propagate along pure-move flows, retype (scalar, `Array[Prim]` and `Carrier[Prim]`, one container deep), coerce at the boundary | which primitive, name, mint site, seed FQNs as an exact `Set[String]` (O4), scope, `carriers` (one-type-parameter wrappers such as the null model's, coerced through their `map`; a spec naming one runs AFTER `nullability`, which is what puts the carrier in the program; empty = none and no edge; a carrier inside a carrier is refused and counted, O3). A formal on a callee this run does not emit is read off the BASE'S PUBLISHED PORT MAP (`RunScope.baseMemberUpstream`), never re-derived (O8); a symbol in a unit this run does not emit is never a SEED (K51) `OpaqueSpec.derive`: also seed where the REFERENCE port (`PortManifest.parity`) spells the slot at the target type — spelling read off signatures, never behaviour (§13.31 step 1) |
| `PortabilityCheck(targets)` — match a rule against every external symbol, report each site | WHICH BACKENDS (`PortManifest.targets`); the rule list is derived from them |
| `ApiParityCheck(ParityRef)` — parse both sides with scalameta, classify divergences by family | WHICH hand-port tree(s) (`PortManifest.parity`); `upstreamMarkers` decides which files are parties; empty = no-op `ParityRef.compare = false` keeps the tree as the DERIVATION source only (`RunScope.derived`, `derived-policy.tsv`, `derived(*)` lanes) |
| `MemberRenameTransform(renames, derive)` — rename over the whole override component or refuse | which members, what name (symbolic emits `@targetName`); `derive` reads the reference's `TargetName`, underscore `FieldName` and parametrised-getter `Rename` rows |
| `NullabilityTransform(annotations, target, scope, nullableMembers)` — move nullability into the type, strip annotation, coerce at seams, rewrite `== null` | which annotations, target shape (`Union`/`Named`/`OptionTarget`), `RuleScope`, `nullableMembers` exact FQNs (K13.6); `MergeablePolicy` union `deriveMembers`: also the members the reference returns wrapped |
| `BeanPropertyTransform(pairs, targets, scope)` — accessor pair → scala property over the override component, derive java-convention pairs in scope | explicit pairs, derivation scope; `Only(Set.empty)` = no-op; configured key wins; `derive`: a pair the reference keeps under its java accessor name is left alone (`KeepName` rows), one it spells as a `var`/`val` folds as if configured, fluent setter included (`Property` rows) |
| `NullaryArityTransform(scope)` — drop `()` from getter-like nullary methods, whole-or-none per component | `RuleScope`; `Only(Set.empty)` default (it MINTS an arity) `force` exact FQNs, the whole override component following; `derive`: parenless where the reference is (its component follows), `def x()` kept where the reference keeps the parens (`KeepParens`) |
| `ClassToTraitTransform(specs)` — abstract class → trait, ctor params → abstract vals, direct subclasses gain `override val` | `Map[fqn, List[ParamMapping]]`; `SurfacePolicy`; differing mappings refuse |
| `ClassTagParamsTransform(members, derive)` — a method's `Class<T>` parameter becomes a `ClassTag[T]` context clause: the body reads the class off the tag, an owned call passing `X.class` names `[X]`; whole override component or none | which methods (`C#m`, `C#m(desc)`); `derive`: the reference's `[T: ClassTag]` members (`ClassTagParam` rows); a call passing a `Class` VALUE, a method reference or an undetermined type parameter refuses, counted |
| `VisibilityTransform(widen, derive)` — ship a listed member PUBLIC where java declared it narrower (a signature fact on the symbol) | which members; `derive`: the reference's `Public` rows (a protected java constructor the hand port made public) |
| `ThreadConfinedStaticsTransform(fields)` — a java `static final` SCRATCH field (one instance every thread shares) becomes a companion `def` over a `ThreadLocal` holder initialised from the java initialiser; every `Owner.f` keeps its spelling | which fields (`owner#name`); `Set.empty` = no-op; refused and COUNTED by guard: not static, not final, assigned after initialisation anywhere, initialiser not a fresh allocation. `MergeablePolicy`: independent keys union |
| `AddMembersTransform(members, fromReference)` — splice hand-written members, or members read VERBATIM from the reference port by NAME (its imports they mention ahead), at the end of a class body, or of its COMPANION (`MemberSpec.static` — a spliced member has no symbol, so its home rides on the node) | which owners, which members, which home; `Only(Set.empty)` default; same owner+name+home refuses |
| `RegistryTransform(entries, facadeMembers)` — reflective instantiation becomes a `Class`-keyed registry: rewrite the call, MINT the table/`register`/`create` at the placement, elide the handler the rewrite made dead | which callee, `RuleScope`, `Placement.Member`/`Object` (the three names, `T`'s bound), `seeds`, `handles`, `miss` (`Null`/`Throw`/`JvmReflect(onFailure)`, the non-JVM cost COUNTED); `Only(Set.empty)` default (it MINTS); independent callees union, one placement slot twice refuses (P10) |
| `ElementWitnessTransform(witness, members, subjectTypes, dropBound, boxedWitness, scope)` — an array whose element is a TYPE PARAMETER is allocated/copied/cleared through a type-class WITNESS, java's implicit `Object` bound dropped and CLOSED under application, raw constructions completed, `eq`/`ne` operands ascribed | which type class, its member names, which declarations at which type-parameter indexes, whose bound goes, the boxed witness a declaration that cannot be threaded takes instead (CT7); `Only(Set.empty)` default (it MINTS a clause); independent subjects union, one subject at two index lists refuses (K41) |
