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
difference and a same-name pair can be neither compared nor composed. A phase's policy must be
part of its fingerprint, and a refused policy merge must stop the run, or two differently
configured instances look equal by name and only one silently runs.

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
  phase can move, so every value crossing to or from it is bridged toward what the class file
  declares, and each crossing is counted — and it is not JDK-only: a third party's parser returning a
  `java.util.List` is the same shape. Measured at 15 errors against 0 findings on one third-party
  package. Where the phase can wrap at the seam it must; where the FORMAL is unknowable it counts,
  and says which.
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
counts; its first run named two retyping phases that had never answered.

## Obligations the engine's own translation created

- **A drop key is a statement about the LIBRARY's surface.** Where the member is one an emitted
  PARENT declares, dropping it breaks an obligation — the class needs to be abstract, and nothing
  reports that before 0 typer errors (§3). An obligation the engine's own translation creates belongs
  to the phase, never to a port's drop key: when the residue exists BECAUSE a phase emitted a parent,
  chose a name or retyped a field, the answer is the phase's; where no translation exists, emit the
  JAVA CONTRACT'S OWN refusal (`UnsupportedOperationException` at an optional operation), louder than
  java and never quieter. One `Not Found` traded for one `needs to be abstract`.
- **A MODIFIER is part of what a re-parenting moved.** `override` was justified by JAVA's resolved
  hierarchy; after a re-parenting scalac reads `overrides nothing` at a perfect translation — 73 of
  one port's 131 `RefChecks` rows. When a class is re-parented onto a Scala collection, the new
  parent's members sit beside Java's own and fail override checks that only run after typer errors
  reach zero; the phase that moved the parent owes the modifier and resolves these clashes itself,
  never through port policy; state the target's overridable surface as a TABLE, admissible because
  both errors are loud.
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
  what the substitution BROKE on the decision.
- **A SYNTHESIS asks the same question.** A record's derived `equals` is skipped when the class
  "already has it" by (name, arity) — exact for arity-0, wrong for `equals(String)`; the fallback is
  REFERENCE equality at a green compile. Ask what the derived member would COLLIDE with in the SCOPE
  it is emitted into: a record's `unapply` goes in the COMPANION, so an instance `unapply` cannot clash.

## A class a FRAMEWORK instantiates has no caller to change

A test suite, a `ServiceLoader` implementation, a bean: the closure sees no `new`, concludes nothing
must be fixed, and a parameter added to one compiles perfectly and cannot be constructed at run time
— 0 scalac errors, a whole suite silently gone. A constructor-changing phase owes a third
answer beside attach and refuse: *this declaration takes the value without taking a parameter*, and
where that value comes from is a port's to say (a hand-written file may carry a `given`).

- **The port's HAND-WRITTEN half is outside the closure.** Which answer a shim takes is READ OFF THE
  GENERATED CALLER: reached only from threaded declarations it may take the clause; reached from an
  unthreaded generated declaration its answer is the residual global the phase counts. A body
  substitution may change what a member DOES and never what it TAKES.
- **An escape hatch that takes an EXPRESSION owes the program a way to write one.** Ask *is there no
  value, or no NAME?* Where the value exists in a constructor parameter, the fix is one member — let
  the threaded type KEEP what it was given under a port-chosen name — scoped `Only(Set.empty)`
  (the hand port had written that member by hand).
- **Asked a SECOND TIME the answer differs, because WHERE THE CLAUSE ATTACHED decides what a name
  means.** An all-`static` lifecycle class takes its clause on METHODS; the answer is a HOLDER the
  method assigns and an accessor that THROWS when nothing has captured one yet. Two keys, not one
  deriving two shapes: the surfaces differ (instance `val` vs companion `def` over a `var`) and the
  expressions differ. **A mechanism's ANSWER is indexed by where it ATTACHED.**

## Refusal enumeration (§3)

- An idiom transformer is not asked to explain a behavioural difference from java: the faithful
  translation exists, so a green suite is what it produces either way. Enumerate the behavioural deltas; each is
  (i) guarded, (ii) impossible by the emitted SHAPE, or (iii) COUNTED — one lane row per declined
  site NAMING THE GUARD. `refused = 0` is a bar met by converting nothing.
- The wave's `members.tsv` blast is CLASSIFIED: every moved digest attributed to a recorded
  `Decision`, a rewritten call site or a changed note; the residue is EMPTY. The SAM wiring came
  back with two members explained by nothing — an emitted NAME keyed on a program-global counter;
  emitted identifiers must not depend on the symbol mint counter, or one unrelated change renames
  many members, so a disambiguator is keyed on what Java itself overloads on and the per-member
  digest comparison stays meaningful.
- **"Refuse loudly" is a claim about the emitted text.** Where the untranslated form is also valid
  Scala the compiler will not flag it, so the refusal must be counted when written, not just left
  approximated: a bare `return` under a function literal is
  scala's NON-LOCAL RETURN — three in libGDX core at 0 errors. Count the refusal when written.
- **A repair at the USE cannot discharge the DECLARATION's obligation.** An F-bounded result pinned
  at the call left the OVERRIDE EDGE (JLS 8.4.2 erasure override) unmeasured for six waves; stating
  the type at the declaration closed 8 of 42 `RefChecks` rows and `overload-risk` fell 6. Where a
  call's result is one of the method's own unconstrained F-bounded type variables, ascribe the
  receiver its static type rather than a type argument (an ascription need not satisfy the bound);
  and because Java lets an implementor drop an F-bounded type parameter that appears only in the
  result while Scala has no such rule, erase that parameter at the declaration.
- **A new arm for an existing node kind inherits that node's obligations.** `catalog(undischarged)`
  `5 -> 7` the first time an emitter arm was added for a phase-minted `Tree.Typed`; discharge
  not-fired — `None` is a FACT there, not a default.
- **A refusal predicate reads a SHAPE, and every shape it does not recognise is counted as a WALL.**
  `CtorFunnel.supersedes` recognised `this.f = <e>` and nothing else, refused a parent constructor
  whose body is one `Tree.If`, and every renderer was built with no options — 42 CommonMark examples
  wrong at 0 errors. Read a refusal lane as a POPULATION: sample the sites. A
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

## More measured rules

- **A CLASS LITERAL carries its type in the CONSTANT, and only a phase that moves the DECLARATION
  may rewrite it.** `classOf[T]` asks about a RUNTIME CLASS, so it is a reified occurrence like
  `instanceof` and a downcast: `Phase.mapType` deliberately leaves it alone, and a RETYPING phase
  that rewrote it would claim the object changed class — routing it through `mapType` turned
  `convertValue(v, classOf[java.util.List])` into `classOf[mutable.Buffer]` and the wrap around the
  result stopped type-checking (2 errors on a port that had 0). A REDIRECT may move it, because the
  declaration really is the target type now: `TypeRedirectTransform.transformTerm`. Without that,
  a redirect leaves `classOf[Old]` in the ARGUMENT of a call whose signature it already moved, and
  the old name survives on the compile classpath with every count flat.
- **A policy key naming a TYPE names it as the PHASE SEES it.** For a phase that runs before a
  `type-redirect`, that is the name the FRONTEND resolved — the original. Re-pointing liqp's
  `reifiedCarriers`/`reflectiveSinks` at the replacement mapper matched nothing and the run said so
  (`never matched`, one `policy` finding), which is the binder working; the keys keep jackson's names
  and the reason they state moves one step instead.
- **A constructor's body is not a body `method-body` can reach**, and the seam that replaces what it
  builds is a REDIRECT of the type it builds. Scala promotes a java constructor into the class body,
  where the locals become members, so `MethodBodyTransform` refuses `<init>` outright (the funnel owns
  it). liqp's `Template` constructs the generated ANTLR lexer there; re-pointing
  `liquid.parser.v4.LiquidLexer` at a hand-written class whose constructor takes java's own arguments
  in java's own order replaced it with no key at all, and the one method that wired the lexer up
  (`Template.parse`) took an ordinary body. A redirect target is minted when nothing in the program
  declares it, which is exactly the injected-replacement case. `method-body`'s `group` is what lets
  one manifest carry two body sets at two pipeline positions; two unnamed entries are two instances.
- A `T | Null` union is not accepted where an abstract type parameter `T` is expected, so a nullable
  value at a type-parameter slot must use a named wrapper type instead of the union target.
- An opaque type that replaces a Java class is retyped against an already injected opaque type
  instead of minting a new one; nested classes and classes with constructors or fields are not
  supported by this mechanism.
- An opaque coercion must look through composite terms such as `if` branches to find the retyped
  reference underneath; the node kinds treated as carriers are enumerated explicitly, and a missed
  one is a compile error, not a silent skip.
- A dependent module may add context-threading policy for its own types (`globals-to-implicits`),
  while the shared part of that policy is inherited from the base and may not be restated.
- A class for which no primary constructor is promoted or synthesised still needs a primary that
  hosts only the `using` context clause, otherwise its body has no context in scope.
- A constructor can carry a `using` clause: the constructor plan must keep given parameters apart
  from value parameters, so the clause is neither flattened into them nor mistaken for one.
- A rule needed at two dispatch sites must be one shared function: a narrowing coercion (Java's
  narrowing cast on a compound assignment, `b += 3` on a `byte`) applied to the statement form only
  and left the expression form with the same defect silently unfixed.
- A `static final` scratch instance is one object per class: `ThreadConfinedStaticsTransform(fields)`
  emits `new ThreadLocal[T] { override def initialValue(): T = e }` as trees — never text, never
  `withInitial` (absent from the Scala.js javalib). Four counted guards: not static, not final, assigned
  after init, initialiser not a fresh allocation.
- A derived seed is exact, so a java STATIC whose reference twin is `extension (a: T) def m`
  derives its slot from `SurfaceDecl.receiver` as parameter 0 (`slotTypes`); never for an instance method.
- A member rename on an EXTERNAL redirected type: hits are the owned overrides whose closure is
  anchored on `(source, member)`, within the redirect's scope, each request `detachedParents = Set(source)`.
  A second unknown parent's surface is PORT policy (`TypeRedirectTransform(external = default ++ …)`),
  never a new closed platform row. Walk such a policy on a spec before regenerating a consumer.
- Under a named/Option null target a boxed element is the primitive (`Nullable[Int]`); union keeps
  the box. A wrapper over the box and over the primitive are one slot (`sameSlot`) — no ascription
  between them. A field's derived rows are keyed `fullName:field`; a qualified-private reference member
  is a derivation input, not a compared surface; a package-private java FIELD the reference ships public
  derives a `Public` row.
- The bean fold's `setterOnly` guard reads an INHERITED getter as in scope (`graph.ancestorsOf`).
- A mechanism that DEFERS a class initialiser (`DeferredInit`'s `$set`/`$value` holder) stands in
  for JLS 12.4.2's class-init LOCK as well as its trigger: the value is assigned under the companion's
  monitor, double-checked, the flag written LAST. Only a parallel suite sees the race (sge textra 1 NPE
  -> 0 at 0 compile errors); a green serial run is no evidence.
- A holder member mapped to a PATH through another service (`gl30 -> graphics.getGL30()`) is an
  ALIAS: java's write refreshing it from that same path (`Holder.gl30 = graphics.getGL30()`) is a
  self-assignment under the mapping and is ELIDED (a `SubstitutedCall` decision on the enclosing
  declaration), never the setter's call — a hand-written setter read the absent value as a command and the
  browser backend lost its GL20 handle (2 demos NPE at 0 compile errors). A write of ANOTHER value stays
  the setter's call. The path's `seg()` hop is a minted symbol: compare it by resolving it on the
  receiver's type, and find the elided line through the holder static's usages.

- A platform limit is answered at the smallest site the REFERENCE answered it, measured first.
  Do NOT retry: shadowing a whole class on one row with upstream's per-platform emulation (libGDX's GWT
  `VertexArray`/`IndexArray` over buffer objects) — the emulation needs the context the java class does
  not take, and shared suites pin the java semantics on every row (sge JS test-compile 0 -> 27 errors).
  The reference diverged at ONE use site (`DecalBatch.initialize`'s no-GL30 fallback), carried as a body
  rule. A consumer wires three things or the port fails at RUN time with a green compile: the port's
  platform-row directories, its classpath RESOURCES (on Scala.js an embedded-resources object of their
  own), and a masking `finally` hides the first exception — read the FIRST error, not the last.

## The §1(b) phase table (loads with the transform files)

| phase(params) — mechanism | policy |
|---|---|
| `ClassTableTransform(redirects, scope)` — re-point a reflective name lookup at an explicit table | which method → which table, `RuleScope`; disjoint scopes compose, overlapping refuse |
| `StaticForwarderTransform(List[Forwarder])` — a wrapper's statics are members of argument 1 | which wrapper, receiver, members |
| `Substitutions` — do not emit these types/methods; inject this Scala instead | which ones, replacement sources |
| `CollectionsTransform(scope, families, familyScopes, retarget, retargetRewrites, retargetRewritesByDesc, retargetTypeArgs, retargetCoercions, reifiedCarriers, reflectiveSinks, retargetIndexedFields)` — retype collections, API-map call sites; JDK table is a §1(a) constant | which declarations, which extra families (per-entry `RuleScope`, scoped on the entry because retyping inside a base's own declarations changes what a dependent's run derives), which library types retarget (java FQN → scala FQN that extends the source), per-member rewrites (`Rename`, `BoolDispatch`, `Construct`, `ForEach`, `Collect`, `Chain`, `FieldWrite`, `DropWrite`, `IndexedField`, `Template`), type-arg maps, coercion templates, reified carriers (a type argument a third party reads back at run time, e.g. `Class<T>`, `TypeReference<…>`, `TypeToken<…>` — not retyped, bridged at the use), reflective sinks (a retyped collection or a java-public field a third party reads back reflectively), indexed field rewrites keyed by (source, field) to avoid key collision with method rewrites. `MergeablePolicy`: independent keys union, same source/different target refuses |
| `PrimitiveToOpaqueTransform(OpaqueSpec)` — seed, propagate along pure-move flows, retype (scalar, `Array[Prim]` and `Carrier[Prim]`, one container deep), coerce at the boundary | which primitive, name, mint site, seed FQNs as an exact `Set[String]` (data, not a predicate function, so two specs seeding the same type from different declarations produce different policy fingerprints), scope, `carriers` (one-type-parameter wrappers such as the null model's, coerced through their `map`; a spec naming one runs AFTER `nullability`, which is what puts the carrier in the program; empty = none and no edge; a carrier inside a carrier is refused and counted — only one container depth is supported). A formal on a callee this run does not emit is read off the BASE'S PUBLISHED PORT MAP (`RunScope.baseMemberUpstream`), never re-derived — opaque propagation must follow array element reads, and a dependent must read the base's published map to know which base members were retyped before unwrapping opaque arguments; a symbol in a unit this run does not emit is never a SEED, must not treat a `using` clause as a formal, and must cast padded slots and unwrap funnelled constructor arguments on a dependent module. `OpaqueSpec.derive`: also seed where the REFERENCE port (`PortManifest.parity`) spells the slot at the target type — spelling read off signatures, never behaviour |
| `PortabilityCheck(targets)` — match a rule against every external symbol, report each site | WHICH BACKENDS (`PortManifest.targets`); the rule list is derived from them |
| `ApiParityCheck(ParityRef)` — parse both sides with scalameta, classify divergences by family | WHICH hand-port tree(s) (`PortManifest.parity`); `upstreamMarkers` decides which files are parties; empty = no-op `ParityRef.compare = false` keeps the tree as the DERIVATION source only (`RunScope.derived`, `derived-policy.tsv`, `derived(*)` lanes) |
| `MemberRenameTransform(renames, derive)` — rename over the whole override component or refuse | which members, what name (symbolic emits `@targetName`); `derive` reads the reference's `TargetName`, underscore `FieldName` and parametrised-getter `Rename` rows |
| `NullabilityTransform(annotations, target, scope, nullableMembers)` — move nullability into the type, strip annotation, coerce at seams, rewrite `== null` | which annotations, target shape (`Union`/`Named`/`OptionTarget`), `RuleScope`, `nullableMembers` exact FQNs (members that return null with no Java nullability annotation, listed by exact name and then treated as if annotated; empty = no-op); `MergeablePolicy` union `deriveMembers`: also the members the reference returns wrapped |
| `BeanPropertyTransform(pairs, targets, scope)` — accessor pair → scala property over the override component, derive java-convention pairs in scope | explicit pairs, derivation scope; `Only(Set.empty)` = no-op; configured key wins; `derive`: a pair the reference keeps under its java accessor name is left alone (`KeepName` rows), one it spells as a `var`/`val` folds as if configured, fluent setter included (`Property` rows) |
| `NullaryArityTransform(scope)` — drop `()` from getter-like nullary methods, whole-or-none per component | `RuleScope`; `Only(Set.empty)` default (it MINTS an arity) `force` exact FQNs, the whole override component following; `derive`: parenless where the reference is (its component follows), `def x()` kept where the reference keeps the parens (`KeepParens`) |
| `ClassToTraitTransform(specs)` — abstract class → trait, ctor params → abstract vals, direct subclasses gain `override val` | `Map[fqn, List[ParamMapping]]`; `SurfacePolicy`; differing mappings refuse |
| `ClassTagParamsTransform(members, derive)` — a method's `Class<T>` parameter becomes a `ClassTag[T]` context clause: the body reads the class off the tag, an owned call passing `X.class` names `[X]`; whole override component or none | which methods (`C#m`, `C#m(desc)`); `derive`: the reference's `[T: ClassTag]` members (`ClassTagParam` rows); a call passing a `Class` VALUE, a method reference or an undetermined type parameter refuses, counted |
| `VisibilityTransform(widen, derive)` — ship a listed member PUBLIC where java declared it narrower (a signature fact on the symbol) | which members; `derive`: the reference's `Public` rows (a protected java constructor the hand port made public) |
| `ThreadConfinedStaticsTransform(fields)` — a java `static final` SCRATCH field (one instance every thread shares) becomes a companion `def` over a `ThreadLocal` holder initialised from the java initialiser; every `Owner.f` keeps its spelling | which fields (`owner#name`); `Set.empty` = no-op; refused and COUNTED by guard: not static, not final, assigned after initialisation anywhere, initialiser not a fresh allocation. `MergeablePolicy`: independent keys union |
| `AddMembersTransform(members, fromReference)` — splice hand-written members, or members read VERBATIM from the reference port by NAME (its imports they mention ahead), at the end of a class body, or of its COMPANION (`MemberSpec.static` — a spliced member has no symbol, so its home rides on the node) | which owners, which members, which home; `Only(Set.empty)` default; same owner+name+home refuses |
| `RegistryTransform(entries, facadeMembers)` — reflective instantiation becomes a `Class`-keyed registry: rewrite the call, MINT the table/`register`/`create` at the placement, elide the handler the rewrite made dead | which callee, `RuleScope`, `Placement.Member`/`Object` (the three names, `T`'s bound), `seeds`, `handles`, `miss` (`Null`/`Throw`/`JvmReflect(onFailure)`, the non-JVM cost COUNTED); `Only(Set.empty)` default (it MINTS); independent callees union, one placement slot twice refuses. This recurring shape ships as a phase that mints a registry into each port, never as a shared runtime support type |
| `ElementWitnessTransform(witness, members, subjectTypes, dropBound, boxedWitness, scope)` — an array whose element is a TYPE PARAMETER is allocated/copied/cleared through a type-class WITNESS, java's implicit `Object` bound dropped and CLOSED under application, raw constructions completed, `eq`/`ne` operands ascribed | which type class, its member names, which declarations at which type-parameter indexes, whose bound goes, the boxed witness a declaration that cannot take a `using` constructor clause takes instead (a class a framework instantiates reflectively — a test suite, a service provider — keeps a no-argument constructor and holds the context as a private given member); `Only(Set.empty)` default (it MINTS a clause); independent subjects union, one subject at two index lists refuses — an open-addressed hash table reads `null` at an element slot to mean an empty slot, so its element type must keep the `Object` bound, and dropping it for primitive elements compiles but breaks probing |
