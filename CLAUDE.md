# Baltic Porter — working rules

Baltic Porter is a **framework for porting Java libraries to Scala 3**, not a program for porting
one library. libGDX is spearheading the effort because it surfaces the most issues per file — but
the target is every libGDX module already ported in sge, every Java library that became part of ssg,
and, once published as open source, whatever libraries other people point it at.

Everything below follows from that. If a decision would make the engine better at libGDX and worse
at the next library, it is the wrong decision.

---

## 1. Universal vs library-specific — the rule that governs every phase and plugin

When designing a phase, transform or plugin, decide first **which of three kinds it is**. Get this
wrong and the framework silently becomes a libGDX porter.

### (a) Universal — belongs in the engine, unparameterised

A fact about **Java and Scala**, true of every codebase. Java arrays are covariant and Scala's are
not. Java interface constants are `static` and inherited; Scala companions do not inherit. Java
allows unchecked conversion at a raw type; Scala does not.

These live in `api` / `engine` / `frontend-spoon` with no configuration.

### (b) Reusable mechanism, per-library policy — belongs in the engine, PARAMETERISED

The **mechanics are the same** for every library; what differs is *which* attributes, types,
variables or references get modified, and whether the rule runs at all. These belong in the engine
**taking their differences as constructor parameters** — sets, maps, lambdas, whatever fits — so the
porting program instantiates them with the values for its library.

An empty/default parameter must make the phase a **no-op**, so "turned off" needs no code path.

Current examples:

| phase | mechanism (engine) | policy (library) |
|---|---|---|
| `ClassTableTransform(Map)` | re-point a reflective name lookup at an explicit table | which method → which table |
| `StaticForwarderTransform(List[Forwarder])` | a wrapper's statics are plain members of argument 1 | which wrapper, receiver, members |
| `Substitutions` | do not emit these types/methods; inject this Scala instead | which ones, and the replacement sources |
| `CollectionsTransform(RuleScope)` | retype JDK collections and API-map their call sites | WHICH declarations — a bridge class that must keep the JDK shape opts out |
| `PrimitiveToOpaqueTransform(OpaqueSpec)` | seed, propagate along pure-move flows, retype, coerce at the boundary | which primitive, what the type is called, where it is minted, which declarations seed it, how far propagation may reach |
| `PortabilityCheck(targets)` | match a rule against every external symbol the program references, and report each site | WHICH BACKENDS the module is ported for (`PortManifest.targets`) — the rule LIST is derived from them, because JS and Native disagree on nine families and one shared verdict is wrong for one of them either way |

**A (b) whose empty parameter is a no-op still needs its DEFAULT chosen against what the phase did
BEFORE it had one.** The two questions look like one and are not: `Everywhere(Set.empty)` is both
the no-op and the pre-scope behaviour for a retyping phase, so nobody had to notice. For
`PortabilityCheck` they come apart — the empty target set really is the no-op, and it is emphatically
not the default, because `Set.empty` (or `Set(Jvm)`) would empty the rule list on all fifteen ports
at once and collapse three counted lanes to a floor in a single commit. Fifteen baselines
"improving" to zero is a baseline promotion nobody can read, and it is indistinguishable from
fifteen ports that got better. So the default is *every question the phase asked before it was
parameterised*, the narrowing is a port's own declaration, and the parameter's arrival is provably
flat.

**Every rule that RETYPES declarations takes a `RuleScope`** (`api`) — `Everywhere(except)` or
`Only(include)`, matched by FQN and cut only at a `Symbol.fullName` separator (§4.56), with
`Everywhere(Set.empty)` the default and the pre-scope code path. Two things such a phase then owes,
neither optional:

**…and a rule that ADDS declarations takes one too, with the OPPOSITE default.** A scope on a
retyping phase is an opt-OUT: the mapping is right everywhere and a declaration asks to be left
alone, so `Everywhere(Set.empty)` is both the no-op and the behaviour the phase had before it was
scoped. A phase that MINTS members has no pre-scope behaviour to preserve, and its unrestricted form
is not a safe default at all — it would put new NAMES on every matching declaration in every port to
serve the one port that hands objects to a framework. Its no-op is therefore `Only(Set.empty)`, and
§1(b)'s rule is met exactly as written: an empty parameter is a no-op. What does NOT carry across is
the SPELLING, so `TransformFactory.scopeOf` takes the phase's own default rather than assuming one.

- **the scope is a fact about the emitted SURFACE**, so the phase implements `SurfacePolicy` — two
  modules scoping it differently emit signatures that each compile alone and cannot compile together
  (§1.5). **And a scope is not the only way a policy reaches the surface**: a parameter that decides
  a PARENT, a member name, a type's kind or its package is the same fact, so `SurfacePolicy` is owed
  by every (b) whose parameters a reader could point at in the emitted signatures — not only by the
  retyping ones. `TestFrameworkTransform` owed it for two: its `suite` becomes a converted suite's
  parent and its `testMember` the call every `@Test` becomes. Nothing reports the omission and
  nothing can: `PortManifest.fingerprint` falls back to the phase NAME, under which two different
  configurations compare EQUAL — so `SurfaceMissing` cannot see the difference and a same-name pair
  can be neither compared nor composed (`ENGINE-LIMITS.md` CT9);
- **every seam the scope creates is COUNTED.** A scope that silently produces an uncompilable or
  wrongly-typed boundary is worse than no scope. Where a coercion exists, insert it; where none can
  (a `mutable.Buffer` is not a `java.util.List`), refuse and report with the §1 classification, and
  read the boundary through the DECLARATION — a position-blind `transformType` has already remapped
  the reference node's type, so a check reading node types reports ZERO on exactly the seam the
  scope made. **And the same seam exists at every EXTERNAL CALLEE, scope or no scope** — a method
  the program does not declare has its signature in a COMPILED CLASS FILE, which no phase can move,
  while the position-blind retyping moved the call NODE's type on both sides of it. So a check
  comparing node types reports ZERO there too, and it is not a JDK-only fact: a third party's
  parser returning a `java.util.List` is the same shape as the JDK's, and the JDK-shaped check does
  not even look. **Every retyping phase owes a boundary count at EXTERNAL callees, not only at JDK
  ones** — measured at 15 errors against 0 findings on one third-party package
  (`ENGINE-LIMITS.md` K15). Where the phase can wrap at the seam it must; where the FORMAL is
  unknowable it counts, and says which of the two it is. **The REWRITE reads it the same way, through the same function.** A call rewrite keyed
  on the receiver's node type fires against the JDK type the declaration kept — `b.raw ++= mine` on
  a `java.util.List` — so the scope emits, for the very declarations it was asked to protect, code
  that cannot compile and that no check counts. **And a formal that stays JAVA is read LITERALLY —
  never through the phase's own remap.** The frontend interns an external member with its
  `MethodType` where a class file can be read for one scope-free, so the formal is usually there;
  read through `remap` it says the slot wants the port's own shim, the wrap fires, and a standalone
  runtime trait is handed to a class file asking for `java.lang.Iterable`. The seam then moves one
  type to the left and stops being findable, because the emitted call names the shim rather than the
  boundary. Where the class file cannot be read at all there is no formal, and that — not a
  guess — is what the count stands for. **And a COERCION may not precede a REWRITE of the same
  call**: the phase is about to change what that call IS, so an argument coerced to the old callee's
  formal is an argument the new one does not want — `items.addAll(other.items)` became
  `items ++= JavaCollection.from(other.items)`, and `++=` takes an `IterableOnce`. Measured at 4
  errors on a port that had 0, with every check count flat and only the member digests moving.

**And the obligation to COUNT is now asked of the pipeline, so a phase cannot simply not have it.**
Everything above was per-phase discipline, arrived at four times the same way — a port shipped, a
wall of `Found: … / Required: …` arrived, and somebody wrote the count afterwards. A phase that
retypes and counts NOTHING is invisible to every instrument here: the retyping is position-blind so
both sides of most slots move together, the port compiles, no check count moves because there is no
check, and the seams reach whoever compiles the port next as bare typer errors with no §1
classification. So a retyping phase declares `Rewrite.accountedBy` — the check LANES that count its
residue, as symbols, never as strings — and `Pipeline.runTraced` DERIVES what it actually moved by
comparing each owned symbol's `info` across the phase. The two halves come from different places on
purpose: **a phase is not asked what it retyped**, because that is the one number it could be wrong
about or silently stop maintaining, and the pipeline can simply see it. `rewrite-callsites` then
reports two things nothing could see before — a phase that moved declarations and names no lane, and
a phase naming a lane that did not RECORD in this run (`RequiredChecks`'s guarantee for the lanes
that are required only when their phase is present, which that set cannot express). It counts PHASES
and not usages, and that is measured rather than assumed: the generic `usagesOf(s) \ callSites` form
is not the boundary counts and cannot become them — `ENGINE-LIMITS.md` K5.10 carries both numbers,
and its first run named two retyping phases that had never answered.

**And a retyping owes an answer at the REIFIED positions, which are not slots at all.** Everything
above is about a SLOT — two sides disagree, and a compiler or a boundary count says so. An
`instanceof` and a downcast are neither: they ask about a RUNTIME OBJECT, java answered over java's
own classes, and a retyping moves the static type without moving one object or one class. (Those two
are the reified positions the SOURCE writes, not the whole list — a type ARGUMENT a third party
reads back out of the class file is a third, and the port writes it nowhere; see the end of this
paragraph.) The
emitted `isInstanceOf`/`asInstanceOf` is then valid Scala asking a DIFFERENT QUESTION, with no
compile error, no finding and no member digest to see it — §4.4's defect class arriving through a
retype. And the two representations are not an edge case: a ported library holds its own values
(the mapping's targets) and its producers' (java's own classes — a deserialiser, a parser, its own
caller) at the same `Object` slot, and java's test accepted both. So the translation answers over
BOTH, refuses where the target is one no view can be, and counts the refusal; and it must not fire
where the representation is already known — an operand the phase ITSELF retyped, or one whose type
the PROGRAM DECLARES, since every instance of a program-declared type is one the port made. That
second exclusion is not tidiness: without it the coercion lands on `Queue.iterator()`, a runtime
dispatch added to every `for` loop in a game engine for no behavioural difference at all. Measured
at **160 of 183 remaining test failures on one library, at 0 compile errors before and after**
(`ENGINE-LIMITS.md` K18).

**…and the THIRD reified position is one no phase can walk to, because the port never writes it.** A
generic type ARGUMENT survives into the class file's signature, and a framework reads it back:
jackson's `TypeReference<Map<String,Object>>`, gson's `TypeToken`, guice's `Key`, every `Class<T>`
literal. Retyped, the argument is a fact the framework then ACTS on —
`Cannot construct instance of scala.collection.mutable.Map`. There is no slot whose sides disagree
and no node to translate, so every instrument reads clean; measured at **10 of one library's 23
remaining failures with every check count flat** (`ENGINE-LIMITS.md` K20). The mechanism is
universal — do not retype a type argument a third party will reify, bridge at the USE — and WHICH
external generics are reified carriers is a fact about that library's dependencies, so it is a (b)
parameter and not a list in the engine. The MECHANISM belongs to the TRAVERSAL, which is the only
place that knows it is about to descend into an argument, and the BRIDGE is the external-callee seam
that already exists — a fix that writes its own is two mechanisms for one seam.

**…and the same third party reads the OTHER end of that call, which is the port's own value and its
own class file.** Everything above is about a type the port WRITES. A framework also reads what the
port EMITTED: a retyped `mutable.Map` handed to a serialiser is not a `java.util.Map` to it, so it
bean-serialises the internals (`{"scala$collection$mutable$HashMap$$table":[…]}`, whose first value
is the `table` array — a `java.util.ArrayList`, which is then a cast failure three hops away); and a
java `public` FIELD emitted as a scala `var` is PRIVATE on the JVM, so a framework auto-detecting
public fields sees ZERO properties. Neither has a slot: the formal is `Object` and the port's value
conforms perfectly. **The second one does not even throw** — every absent property reads `null`, and
a library that coerces `null` to a default then answers three assertions out of four CORRECTLY from
data that is not there (`ENGINE-LIMITS.md` K21, 13 failures at 0 errors and every count flat). So a
retyping phase owes an answer where its value leaves the program, not only where one arrives.

**And the answer is a RUN-TIME one, at TWO seams, because a framework calls back IN.** There is no
static evidence to bridge on: the formal is `Object` and so is the argument's own type — a library
whose data model is `Map<String,Object>` gives the phase nothing at the call, at the declaration or
anywhere else — so the question is asked of the OBJECT, deep and by view (a serialiser walks the
whole tree; a one-level `asJava` is the refusal such a phase already records, and a copy detaches
both directions). And bridging the ARGUMENT is only half: the framework then calls the ACCESSOR it
discovered, so the accessor is the same seam read from the other side — which is why a generated
bean property (`@BeanProperty`) is measurably worse than an emitted one that can interpose. Both
halves are per-library policy for the same reason and one step past K20's: java guarantees no
reflective sink and no reflectively-read class, so the engine ships both lists empty and publishes
the CANDIDATES instead — one row per external callee with an opaque formal, one row per emitted
type with java-public fields. A missing entry is otherwise invisible; the first run of that list
named a second sink the port had not declared. **A candidate list is a residue of the POLICY,
though, so it is published where the PHASE runs and nowhere else** — and the two halves therefore
appear on different conditions, which is a difference to state rather than to average over. The
egress list rides on a phase every port with collections already carries, so a missing sink shows up
on every run of those; the bean list rides on a phase whose no-op is `Only(Set.empty)` and which a
module only puts in its surface once it has said that something is read reflectively. Unscoped that
list is every public field in the library, which is the over-approximate review list §1 calls noise
— so a module that does not carry the phase publishes nothing here and is not claiming that nothing
is exposed.

**And "N failures are gated behind this one" is a HYPOTHESIS, never a count.** A defect that THROWS
hides every defect after it on the same path, so the tests attributed to it are the tests it is
first on — not the tests it is the cause of. Both of K21's faces sat behind K20's exception and were
invisible while it fired; closing K20 flipped **2** of the **10** predicted. Re-census after the fix,
and quote the family that went to zero (`10 → 0`) rather than the suite delta, which is the only
honest reading of either number.

**And a class a FRAMEWORK instantiates has no caller to change.** Every such phase reasons from the
program: it may add a parameter because it can see, and fix, each `new`. A test suite, a
`ServiceLoader` implementation, a bean — these are constructed reflectively from OUTSIDE, so the
closure sees no instantiation at all and concludes, correctly and uselessly, that nothing has to be
fixed. Adding a parameter to one emits code that compiles perfectly and **cannot be constructed at
run time**: no compile error, no check count, no finding, and the only evidence is the suite that
stopped running. Measured on the first port to thread a constructor
(`ENGINE-LIMITS.md` CT7 — 0 scalac errors, a whole suite silently gone). So a phase that changes a
CONSTRUCTOR's signature owes a third answer beside "attach" and "refuse": *this declaration takes the
value without taking a parameter*, and where that value comes from is a port's to say — a hand-written
file may carry a `given`, and generated code cannot be edited to.

**And the port's HAND-WRITTEN half is outside the closure, so the phase leaves it to a human — who
does not get to choose.** A shim, an injected replacement, a hand-written suite: the frontend never
saw them, so nothing threads them and every one is a compile error the moment the shared surface
moves. Which answer each takes is READ OFF THE GENERATED CALLER, not picked: a shim whose callers are
all inside threaded declarations may take the clause; a shim reached from a generated declaration the
closure did NOT thread may not, because no manifest key can add a clause to that caller, and its
honest answer is the residual global the phase already counts. Both shapes shipped in one port, two
files apart, and the difference between them is entirely the difference between their callers.
Corollary for a body substitution: it may change what a member DOES and never what it TAKES, so a
replacement body needing the new value has to be placed at a member the closure already reached —
one member further out than the one you were looking at.

**And an obligation THE ENGINE'S OWN TRANSLATION created is not a port's to discharge.** A drop key
is a statement about the LIBRARY's surface — "this port ships without that member". Where the member
is one an emitted PARENT declares, dropping it does not remove an obligation, it breaks one: the
class needs to be abstract, and **nothing reports that until the port is already at 0 typer errors**,
because `RefChecks` does not run before then (§3). So the failure arrives on the day the port goes
green, in a member nobody was looking at, having passed every count on the way. Read the shape rather
than the case: when the residue exists BECAUSE a phase emitted a parent, chose a member name, or
retyped a field, the answer is owed by the phase — and where no translation exists, the honest
emission is often the JAVA CONTRACT'S OWN refusal (an `UnsupportedOperationException` at an operation
the interface itself declares optional), which is louder than java and never quieter, rather than a
stand-in that compiles and silently does nothing. Measured on the first port to try the drop:
one `Not Found` traded for one `needs to be abstract`, at 0 net errors moved
(`ENGINE-LIMITS.md` K5.7).

**…and a refusal SUBSTITUTED FOR A BODY is licensed by the DEFECT THE PHASE CAUSED, never by the
member it sits on.** The contract makes the throw conforming for a receiver that cannot perform the
operation; it says nothing about one that can, and both are spelled the same. So a body substitution
answers two questions before it fires — *is this really the interface's member*, by SIGNATURE (a
class implementing `Map.Entry` may declare `setValue(int, int)` beside it, and java resolves the two
separately), and *does the TRANSLATED body still reference something the mapping removed*. Matched
on the bare name alone, a self-contained `setValue` that java runs was replaced by a throw, and so
was the unrelated overload: green compile, every count flat, and no test in the corpus to see it
because the one library that has the shape happens to delegate (`ENGINE-LIMITS.md` K5.7's
correction). Record what the substitution BROKE on the decision, too — a reader of the emitted throw
cannot otherwise recover which call it replaced.

**…and the same test governs a SYNTHESIS, where "already declared" decides whether to write a member
at all.** The second occurrence is what makes this a rule about MATCHING A MEMBER rather than a rule
about substitutions. Java derives a `record`'s `equals`/`hashCode`/`toString` unless the record
declares them (JLS 8.10.3), so the emitter skips each where the class "already has it" — read as
(name, ARITY), which is exact for the two arity-0 names (java cannot overload on a return type) and
wrong for `equals`, whose one-argument form java resolves separately from `equals(String)`. And the
failure is QUIETER than K5.7's: suppressing a derived `equals` does not leave the class abstract,
because `AnyRef.equals` is concrete — the record simply falls back to REFERENCE equality, with a
green compile, no moved count and no finding anywhere. **Ask what the derived member would COLLIDE
with, in the SCOPE it is emitted into**, which is a third question again where the two differ: a
record's extractor goes in the COMPANION, so an INSTANCE method called `unapply` cannot clash with
it and declining on the bare name left every record pattern over that type naming nothing.

### (c) Genuinely library-specific — a SEPARATE, PLUGGED-IN RULE

If a customisation needs knowledge so specific that it could only ever apply to **one** library, it
is a separate rule that the porting program plugs in. It does not go in the engine at any level of
generality. In future it will be maintained by the repository that manages that library's porting
effort, not by this repository.

Turning a primitive into an opaque type is the canonical example: *which* `Int`s are really a GL
handle is knowledge about libGDX and nothing else. Note where the line falls, though — the MECHANISM
(seed, propagate, retype, coerce) is a (b) in the engine and the knowledge is a (c) `OpaqueSpec` the
port hands it. A (c) is a value or a plugged-in rule; it is almost never a whole mechanism.

### The balance

**Design every rule to be as reusable as possible.** Reach for (c) only after establishing that the
mechanism genuinely cannot be shared. Most things that look library-specific are a (b) with the
policy inlined — that is exactly the mistake `ReflectionToPortableTransform` made, hard-coding
`com.badlogic.gdx.utils.reflect.ClassReflection` and its member list inside the engine.

### Enforcing it

No file under `balticporter/{api,engine,frontend-spoon,runtime}/` may name a ported library **in
code** — test sources included, because a fixture that hard-codes one library's names is the same
mistake one layer down:

```
grep -rn --include='*.scala' -E "badlogic|libgdx|liqp|liquid\.parser|earlygrey|simplegraphs|dongbat|jbump|czyzby|noise4j|tommyettinger|anim8|crashinvaders|eskalon|mgsx|fasterxml|antlr|strftime" balticporter/api balticporter/engine balticporter/frontend-spoon balticporter/runtime | grep -vE ":\s*(\*|//|/\*)"
```

**Every corpus library's identifying string is in that pattern, and so are its DEPENDENCIES'.** One
library's name in the engine is the mistake the rule is about; one library's *dependency* named
there is the same mistake with an extra hop, and both have now been found — `PlatformLint` carried
`liquid.parser.`, `ua.co.k.` and jackson, and `SpoonFrontend` decided which annotations survive from
a hard-coded `com.fasterxml.jackson.`. Add the new library's strings when you add the library (§2),
and note the third `grep -v` alternative: a doc comment opening with `/*` is documentation and is
wanted (the rule is about code), which the two-alternative filter used to let through as a hit.

Library names in **doc comments** are fine and wanted — the worked example that justifies a general
rule (`GL30Interceptor` witnessing the export diamond, `Array<? extends T>` witnessing array
covariance). They document; they must drive nothing.

---

## 1.5 A dependent module INHERITS the shared surface; it never restates it

A library is rarely one module, and the second one is where a port drifts. An extension resolves
against the base module's **Java** (`resolutionRoots`), never against the Scala the base port
emitted — so everything the base's transforms did to those signatures has to be redone identically,
or the two ports each compile alone and cannot compile together. Copying the configuration is not a
mechanism; it is a habit, and it fails one module at a time.

So the shared surface is a VALUE — `PortManifest` — that a dependent imports and extends
(`base.extendedBy(...)`), never a block of policy it repeats. Ordinary Scala, type-checked by the
consumer's compiler; a manifest DSL would move the policy out of reach of both.

A port may also be written as a `.conf` (DESIGN.md §5.7), and that is not a second truth: the config
path CONSTRUCTS these same values through these same constructors, `base = "…"` IS `extendedBy`, and
anything config cannot hold — a predicate, a rule — arrives only as `ServiceLoader`-discovered code,
never as a string that is secretly code.

The line between what must agree and what must not:

| inherited — a fact about the SHARED SURFACE | not inherited — a fact about THIS module's build |
|---|---|
| `dropTypes`, `dropMethods`, `packageRenames`, `surface` | `sourceSet`, `frontend`, `provenance`, `runtimeMode`, `supportSources`, `project` |
| the PER-TYPE half of the rename policy — `typeRenames`, `subPackages`, `flattenNestedTypes`, and `allowPackageSplit` beside them | **`inject`**, `targets`, `verdictOverrides`, `dependencies` |

**`targets` is not-inherited with a ONE-DIRECTIONAL constraint, which is a third shape the table
cannot state.** Which backends a module is ported for moves no emitted signature — it decides which
findings the module is TOLD about — so two modules may hold different sets and neither produces a
port that compiles alone and cannot compile with the other. That is the whole argument for the
right-hand column, and it runs out halfway: a dependent targeting FEWER platforms than its base
merely asks fewer questions of its own declarations, while a dependent targeting MORE is a port that
**cannot be built** — it depends on emitted Scala that was never checked against, and may not be
portable to, the platform it claims. `ENGINE-LIMITS.md` D2's ownership filter is exactly what hides
this, since a dependent is forbidden to report about its base's declarations, so the unbuildable
half is the half nothing looks at. `ManifestAgreement.Kind.TargetWidening` is that rule, fatal, and
its honest escape hatch is §1.5's own: if the base genuinely IS portable and only never said so,
widen the BASE — a statement, not a loophole. Note the DEFAULT is what makes this reachable at all:
a dependent that declares nothing gets all three platforms, so a base that narrowed and a dependent
that did not is a widening BY OMISSION, and it is the shape the rule will meet first.

`inject` is the one that looks wrong and is not. A drop and its replacement read as one decision and
are two: the DROP is an observation about the shared API and binds every module that sees the type;
the INJECTION is a build artefact, and exactly one module must ship each replacement file — a
dependent that copied it would emit a second definition of the same FQN. Every check that asks "is
this replaced?" follows the same line and holds a module to its OWN drops only.

**And a phase that SYNTHESISES a declaration owes the same answer, which is the half the table
cannot state.** An inherited phase is one instance and it RUNS in every module — those are different
questions, and asking only the first ("does any dependent CONSTRUCT this phase?", the instance count
above) settles the merge while saying nothing about the emission. A dependent's model CONTAINS its
base's units, so a phase keyed on a base declaration fires there too; if what it does is MINT a
top-level unit, every module in the chain writes its own copy of the same FQN, and the run cannot
tell them apart because a minted unit has no `Origin` for `PortRun.converted` to classify. So a
synthesised unit belongs to the module that owns the declarations it was synthesised FOR — `RunScope`
is that set — and nothing else may write it. Measured at 24 errors over six dependent lanes with six
suites stopped, while the BASE read 0 errors and all of its check counts flat
(`ENGINE-LIMITS.md` §13 O5). Note the shape of that evidence: **a base port's green numbers are not
evidence about its dependents**, so a step that changes shared surface is measured with
`just measure-all` or it is not measured.

**`surface` composes only where the PHASE says how.** `extendedBy` unions the drops and the renames
key by key; it cannot do that to a `surface`, because a phase's policy is a constructor argument and
two instances holding two halves of one table are not a map. So a parameterised phase declares
`MergeablePolicy` — *how MY table composes with a nearer manifest's instance of me* — and
`PortManifest.surfaceFold` folds same-name phases through it, at the base's pipeline position
(`DESIGN.md` §8.13). Four things that follow, none of them optional:

- **the merge is the PHASE's answer, never the engine's.** A `Map` of independent keys unions; an
  ordered list does not; a first-match table does not; a `RuleScope` composes one way for `Only` and
  the opposite way for `Everywhere`. A phase that declares nothing keeps the pre-merge behaviour —
  two instances stay in the pipeline and the pair is a fatal `SurfaceDivergence`, which is the right
  answer for a composition nobody has designed;
- **a refusal is a finding, never an approximation.** Same key, different value stays two instances
  and is reported with the phase's own sentence for why;
- **the merge lives in the DEPENDENT's pipeline only.** The base manifest a dependent declares must
  stay the base *as the base ran it* or its published map is `BaseMapStale` and every base-surface
  question turns fatal — which is why the fold runs on `policyChain` (a dependent's chain contains
  its base; a base's does not contain its dependent) and not in the run;
- **no key a DEPENDENT declares may edit what a base EMITS — merged or not.** A subject inside a
  base's `governs` claim that the base neither drops nor already declares is a fatal
  `SurfaceIntrusion`: a dependent re-shaping the shared surface produces two ports that each compile
  alone and cannot compile together. The allowed case is a subject the base drops and leaves EMPTY —
  **"nothing stands at that name", never "the base drops it"**: a drop WITH an injection ships a
  file at that FQN, and the shim is shared surface exactly as an emitted class is. **Screen the
  whole of a mergeable phase's
  policy, never only what a merge added**: scoped to the merge, the screen misses the one shape with
  no constraint on it at all — a phase the dependent declares and no base has, which is one instance
  (nothing diverges) and no merge (nothing is "added"). That is why `MergeablePolicy` has to expose
  its `subjects` and not just its merge.
  **And ask that question of what the base EMITS, never of its `governs` CLAIM.** A claim names a
  NAMESPACE; it does not say that every FQN under it is the base's, and a dependent's own
  declarations routinely live inside it — a TEST SOURCE SET always does, `src/test/java/<pkg>` being
  the same package as `src/main/java/<pkg>`. Screened by the claim, every key such a module writes
  about its OWN members is an intrusion, which is a rule with no way to comply with it: 3 fatal
  findings for one three-key `dropMethods` entry naming the dependent's own test class
  (`ENGINE-LIMITS.md` D10). The base's PUBLISHED PORT MAP is the list of what it emitted and is what
  the screen must read; the namespace is the fallback for a base that published none, which is
  already a finding of its own.

Measured on the libGDX base's first `TypeRedirectTransform`, which is what closed `ENGINE-LIMITS.md`
D9.

**And a policy whose effect is DERIVED rather than TABULATED escapes every one of those rules, at an
EQUAL fingerprint.** Everything above compares POLICY: two instances, two tables, one fold, one
`surfaceFingerprint`. That machinery is exact for a phase whose emitted shape is a function of its
constructor arguments, and it is blind to one whose arguments only NOMINATE a subject whose shape the
phase then derives from the program. `BeanCollapse` is the worked example: the manifest says *this
pair*, and whether the pair becomes a `var` is decided by `overriddenBelow` over the run's
descendants, `concreteRelative` over its override closure, `writtenSymbols` over its assignments and
`closureOf(_).isAnchored` over its parents. A dependent's model CONTAINS its base's units, so ONE
subclass overriding the accessor — or one write of the field — makes the dependent re-derive `Refuse`
for a declaration the base COLLAPSED, and emit `def getW()` where the base emitted `var w`.

Nothing in the merge contract can see that. The entry is identical on both sides, so the fingerprints
are EQUAL and `SurfaceDivergence` has nothing to compare; the phase agrees with itself, so the
refusal is reported honestly under a real guard; every count is flat; both ports compile alone. So a
phase whose surface is derived owes a THIRD answer beside "merge" and "refuse": **publish the derived
shape in the port map, and compare the dependent's derivation against the base's PUBLISHED one, as a
fatal `base-surface` finding naming both.** Two things that are not incidental — the base's answer
must be STATED (`Surface.MemberShape.form`) rather than inferred from an absent accessor row, since
a `dropMethods` entry produces the same absence and inferring would be §4.6's fabricated fact; and
the question is asked only where the base's map has a row for the OWNER TYPE, which is this section's
own rule read one level down (ask what the base EMITS, never what its `governs` claim says).

**And what a merge is even needed FOR is an INSTANCE count, not a policy count** — the distinction
that decides how a base policy lands. New policy on a phase the base ALREADY carries reaches no fold
at all: there is only ever one instance, the dependents inherit that one value, and their effective
surfaces agree by construction. So the first question before writing a base policy is still not "is
this a (b) phase?" but *does any dependent CONSTRUCT this phase?* — one grep over the ports; the
merge contract above is what answers the second case, and only that one. Measured the other way round on
libGDX's base `CollectionsTransform` gaining a `retarget` table: `manifest` 0 on all thirteen ports,
with the fingerprint change reaching nine published port maps and nothing else moving.

`PortRun` runs `ManifestAgreement` on every port, and a run whose resolution roots lie outside its
own source root — the structural signature of a dependent — with no base declared is itself a fatal
finding. If a resolution root is genuinely NOT a ported module, declare an empty manifest for it and
say so; that is a statement, not a loophole.

---

## 2. Adding a library to the corpus

Until the framework is published and each library gets its own porter repository, new libraries are
added to the **corpus** (`balticporter/corpus/`, one package per library:
`balticporter.corpus.<lib>`). The
procedure for each:

1. **Make it compile.** Every effort — this is where the engine's gaps surface.
2. **Test-compile it**, then port and run its tests. Compiling is not passing; see §3.
3. **Run the Auditor** (§4) over both the new specialisations and the shared code.

Each library added is expected to move engine rules from (c) toward (b) toward (a). A rule that
survives three libraries unchanged is probably universal; one that needs a new parameter per library
is correctly a (b); one that cannot be shared at all is a (c) and should be named as such.

### 2.1 A port is named for its DESTINATION, never for its upstream

The repository has two halves and the split is structural: **`balticporter/`** holds the framework
(`api`, `engine`, `frontend-spoon`, `runtime`, `testkit`, `corpus`) and **`ported/`** holds one
directory per ported library. What goes in a `ported/` name is the **reference port's own module
id** — `../sge/build.sbt`'s `sge`, `sge-ecs`, `sge-gltf`, …, `../ssg/build.sbt`'s `ssg-liquid` — and
not the upstream library's name. libGDX core is `ported/sge`; Ashley is `ported/sge-ecs`; liqp is
`ported/ssg-liquid`.

The reason is that the upstream name says nothing about where the output belongs, and a port has
exactly one consumer that must be able to drop the emitted tree in place. A directory called
`ashley-core` emitting `package sge.ecs` states two different answers to one question, and the
second module is where that starts costing something. The **port's `label` and its
`PortManifest.name` take the same value** — they are not decoration: `label` is what
`PortRun` writes as `module=` into the published `port-map.tsv`, `PortManifest.name` is what a
dependent's `baseChain` matches against it, and `PortMapTransform.forBases(…)` names it a third
time. All three must agree or a dependent finds no base map and says so quietly.

**Three things this rule deliberately does NOT reach**, because each is a different axis:

- the **`port-report/<X>/` directory**, keyed on the migrator's `main` CLASS simple name. That is
  the MEASUREMENT identity, and a baseline whose directory moves with a rename is a baseline that
  cannot be diffed across one. Rename the module; leave `LibgdxCoreMigrate` alone.
- **`packageRenames`**, which is the namespace axis and was already correct on every port — the
  reference module's directory and the package it declares are not the same string
  (`sge-screens` declares `sge.screen`, singular).
- the **upstream source tree**, which keeps whatever name the library gave it.

---

## 3. Compiling is not the gate

The compile-error count is a **typer-only** measurement: dotty's `Phase.isRunnable` is
`!ctx.reporter.hasErrors`, so a single typer error skips `RefChecks` for the whole program. Missing
`override`, unimplemented members and variance violations are unmeasured until the count reaches 0 —
and then the number will RISE. That is the gate beginning to tell the truth, not a regression.

Worse, a green compile says nothing about behaviour. Four silent correctness defects were found in
libGDX core that all compiled cleanly — dropped `static { }` blocks, dropped `super(args)`, dropped
anonymous-class bodies (156 sites, every button silently doing nothing), and the typer blind spot
itself. Each would have shipped.

So:

- **Every translation path gets a check at the same time it gets a translation.** A check reporting
  zero is only as good as its coverage.
- **Walk the tree with `StandardTraversal`**, never a private recursion — two of those four defects
  were hand-rolled traversals that stopped one node short.

  **…and the recursion that reads RIGHT is the one to look at hardest, because a construct the
  frontend REFUSES makes a short walk correct by accident.** Twenty-eight recursion lines across
  nine files walked nested types as `cd.body.foreach { case c: Tree.ClassDef => scan(c) }` — the
  class's MEMBERS — and every one of them was exact for as long as the only `Tree.ClassDef` in the
  program was a type member. A method-LOCAL class is a `BlockStatement` (JLS 14.3), so the day the
  frontend grows that node all twenty-eight answer *there is no nested type here* about a type the
  program declares: the emitter names it through a projection that names nothing, the visibility
  plan calls it a member, the constructor funnel plans nothing for it, and the package rename leaves
  it in the upstream namespace. None of that is reachable while the construct is refused, so nothing
  can measure it and no reviewer sees a bug. The rule is therefore not "walk with `StandardTraversal`
  where a node kind can appear" but **where the IR says it can** — `Tree.ClassDef` has been a
  `Statement` since the IR was written. State the walk ONCE
  (`StandardTraversal.allClassDefs`, `TirEmitter.allDeclaredClasses`) so growing a node is one edit
  and not twenty-two. Measured at **0 blast on all fifteen ports**, which is exactly what a latent
  defect costs to fix and exactly why nobody had.
- **Prefer running ported tests over any number of further compile fixes.** Assertions are the only
  evidence of behaviour this project can have.
- **Read the emitted output**, not just the count, when confirming a fix.
- **AN IDIOM TRANSFORM'S SAFETY ARGUMENT IS A REFUSAL ENUMERATION, NEVER A SUITE RESULT.** Every
  other layer here derives its mandate from a DIFFERENCE — java does X, scala does Y — and its
  evidence is that the port stopped doing Y. An idiom transformer has no such mandate: the faithful
  translation already exists, compiles and behaves identically (`DESIGN.md` §8.15 says what licenses
  it instead), so it moves code that already means the right thing and a green suite is exactly what
  it would produce either way. Suites are necessary and not sufficient, and this repository has the
  receipts one paragraph up. So for each transformer the set of behavioural differences between the
  java shape and the scala shape is ENUMERATED, and every member is (i) made impossible by a
  structural GUARD, (ii) made impossible by the SHAPE the transformer emits, or (iii) COUNTED. **A
  transformer that cannot enumerate its deltas does not ship.** And the enumeration is what a run
  reports: the refusal population is a lane, one row per declined site NAMING THE GUARD, because a
  count of conversions says nothing about what was declined and `refused = 0` is a bar met by
  converting nothing.
- **…and such a wave's `members.tsv` blast is CLASSIFIED, not minimised.** These waves move emitted
  text by design, so 0 digests is the wrong gate. The gate is that every changed digest is
  attributable — to a declaration this phase recorded a `Decision` for, to a declaration holding a
  call site it rewrote, or to a declaration whose porter NOTES changed — and the residue is EMPTY. A
  digest that moved with nothing to attribute it to is an unexplained rewrite and the commit does not
  land. Measured: the SAM wiring came back with two members no decision explained, and they were an
  emitted NAME keyed on a program-global counter renumbering itself (`ENGINE-LIMITS.md` M10's shape);
  the gate is what found it, and a "small enough" reading of the diff would not have.
- **"REFUSE LOUDLY" IS A CLAIM ABOUT THE EMITTED TEXT, and you do not control whether it holds.**
  Refusing a translation and leaving the construct alone is right (`ENGINE-LIMITS.md` M6) — the part
  that is not a decision is whether scalac then rejects what was left. Where the untranslated form
  is ALSO VALID SCALA, the refusal is a silent divergence and the compiler tracks nothing: the
  emitter's `Tree.Lambda` arm declined to interpose the nested `def` that restores java's
  *`return` leaves the LAMBDA*, and a bare `return` under a function literal is scala's NON-LOCAL
  RETURN FROM THE ENCLOSING METHOD — three of them in libGDX core at **0 compile errors**, one of
  them a validator that unwinds out of the method that installed it. So a refusal is COUNTED at the
  moment it is written, and the count is not the weaker half of "refuse and count": where the
  residue compiles, it is the only instrument there is.
- **A NEW ARM FOR AN EXISTING NODE KIND INHERITS THAT NODE'S OBLIGATIONS — discharge them
  NOT-FIRED.** The catalog's discharge surface is owed per NODE, not per arm, so adding a `case` to
  a dispatch that already answers a difference row silently stops answering it for whatever the new
  arm now catches. The emitted text is fine, the port compiles, and the only instrument that can see
  it is `catalog(undischarged)`, whose rows say `ENGINE GAP` — which reads as an engine defect in
  the ROW rather than as a `match` somebody split. Measured at `5 -> 7` the first time an emitter
  arm was added for a phase-minted `Tree.Typed`, at 0 errors with every other count flat. Both
  answers are usually `None`, and `None` is a FACT here rather than a default: the new arm's node
  really is not the construct those rows are about, which is exactly why it needed its own arm.

---

## 3.5 Consult the REFERENCE PORT before inventing a rule

sge is a hand-written port of the same library, and ssg contains hand-ported Java libraries. Where
they solved a problem, the solution is visible and already validated against real code. **Look there
before designing a rule, and before concluding that no faithful translation exists.**

```
grep -rn "<the construct>" ../sge/sge/src/main/scala/    # and ../ssg for its libraries
```

Two things to record when you find one:

- **What they emitted.** Example: every raw generic is rendered `[?]` — parent, overrides and fields
  alike (`AssetLoader.getDependencies` returns `DynamicArray[AssetDescriptor[?]]` and every override
  matches). That settles both that `?` round-trips across an override, and that filling the element
  with the loader's own `T` is semantically wrong, since a `BitmapFont`'s dependencies are a
  `TextureAtlas`.
- **Whether they SOLVED it or SKIPPED it.** sge also renamed `AsyncTask` to `() => Unit` and simply
  did not port several classes. Skips are not models — this project exists precisely to port what
  sge left out — so a construct that merely vanished from sge tells you nothing except that it is
  still open.

Reasoning from first principles when a worked answer exists nearby wastes whole sessions. It has.

**But what the reference port EMITTED is evidence; what it implies for a MECHANICAL port is a
hypothesis, and it is measured before it is policy.** The two are not doing the same thing. A hand
port adjusts every caller by hand, so it can keep a JDK type in one file and stay consistent
everywhere; a mechanical port moves whole families of declarations at once, and a policy that
exempts some of them SPLITS a call graph the hand port never had to keep whole. Read the second way,
"ssg kept `java.util` in 32 of its 130 files" became "scope the collections phase out of the
declarations at the seams", which is the same shape as their answer and cost **27 → 47 errors**,
then **27 → 51** turned off entirely (`ENGINE-LIMITS.md` K16). Nothing was wrong with the
observation. What was wrong was reading a hand port's freedom as a mechanical port's option — so
quote the reference port for the SHAPE it emitted, and get a number before that shape becomes a
manifest entry.

## 3.6 Where a discovery goes

A lesson that would change how the NEXT library is ported does not belong only in that port's
progress section — nothing loads it, and it gets re-derived. **There is one document per KIND**, and
a discovery goes into whichever fits, in the same commit that learned it:

| home | for |
|---|---|
| this file | a governing rule or constraint for all porting work |
| `ENGINE-LIMITS.md` | a MEASURED dead end or engine limit — what not to retry, and what it cost |
| `DESIGN.md` | a DECISION about what the engine is or how it is built |
| `PROGRESS.md` | the STATE of a port or of publishability — measurements, residues, remaining work |
| a skill (`.claude/skills/**`) | a procedure, e.g. adding a library to the corpus |
| an agent definition (`.claude/agents/**`) | what a reviewer should hunt for |

**Do not add a seventh document.** A new file for one investigation is a file nothing loads; see §3.7.

That library's `PROGRESS.md` section keeps the MEASUREMENTS and the dead ends with their numbers. The
rule extracted from them goes above. A rule that names a specific library is per-library policy and
belongs in that library's manifest instead (§1c).

`ENGINE-LIMITS.md` is the split between the first two rows: this file says what you must do,
`ENGINE-LIMITS.md` says what has already been tried and measured worse, grouped by what an agent is
doing when it hits the wall and classified (a)/(b)/(c). Add to it in the same commit that measures
the failure, and keep the number — a dead end without its number is an opinion.

## 3.7 A RESEARCH FILE IS NOT A DELIVERABLE

Working through a question often wants a scratch document — a survey, a table of candidate shapes, a
transcript of what four experiments measured. **Write it. Do not commit it.**

- Scratch and research files live under **`.balticporter/`**, which is gitignored and which the
  measure lanes already use for their own captures. Nothing else in the repository is a valid home
  for one.
- **…and no committed file may CITE one.** A pointer is the same failure one level of indirection
  out: `ENGINE-LIMITS.md` D5 named a scratch plan as the authority for the order two commits had to
  be measured in, and `PROGRESS.md` cited five briefs and a question number for facts those briefs
  no longer held. Every one of them reads as a document the next agent can open, and none of them
  exists in a fresh checkout. State the FACT where the §3.6 table says it lives, and cite that.
- Before the work is called done, what the file FOUND is incorporated: a decision into `DESIGN.md`, a
  measurement or a residue into `PROGRESS.md`, a measured dead end into `ENGINE-LIMITS.md`, a
  governing rule here. Then the scratch file is deleted, not committed.
- A committed research file is worse than no file. It is not in the §3.6 table, so nothing loads it;
  it accretes a status section, so it starts disagreeing with `PROGRESS.md`; and its conclusions get
  re-derived anyway because the next agent never opens it. Every document deleted in the
  consolidation that produced `DESIGN.md` and `PROGRESS.md` began as exactly this.

**A TODO list shrinks by DELETION.** A completed item is REMOVED — never moved to a "done" section,
never struck through, never annotated `[x]`. A list that only grows stops being a list of what is
left and becomes a changelog nobody reads, and the remaining work is then invisible inside it. If a
finished item taught something worth keeping, that lesson goes to one of the §3.6 homes; the list
entry still goes. Git history is the record of what was done.

## 4. The Auditor

An **adversarial reviewer** (`.claude/agents/porting-auditor.md`) that reads the engine and the
per-library specialisations looking for over-specificity, missed cases and shortcuts — rules that
happen to work on the corpus rather than being right.

It runs on the **Fable 5** model and is expensive, so it is **not** run on every change. It is run
**by the user, once a whole piece of work is delivered.** Do not launch it speculatively.

---

## 5. Measurement discipline

- **Reproduce every number with the measure lanes, serially.** They live in the root `Justfile` —
  one file, so a fix to one lane's guard reaches the others — and each re-emits into `src_managed/`,
  so a dependent lane compiles against what the base lane just wrote:

  | recipe | lane |
  |---|---|
  | `just gdx-measure` | libGDX core — emit, checks, break residue, compile, correlate |
  | `just gdx-test-measure` | libGDX's own suite — the same, then RUN it |
  | `just ashley-measure` | Ashley + its suite, compiled WITH libGDX core (a dependent port) |
  | `just gltf-measure` | gdx-gltf + both its suites, compiled WITH libGDX core (a dependent port) |
  | `just sg-measure` | simple-graphs + its suite |
  | `just jbump-measure` | jbump — a library that ships NO suite, so the lane re-derives that zero |
  | `just measure-all` | every lane above, SERIALLY, stopping at the first failure |
  | `just decision-counts` | `decisions.tsv` row counts by kind, every port |
  | `just members-unchanged` | `members.tsv` against its baseline — the blast radius, before a compile |
  | `just baseline-{list,show,diff,accept}` | the baseline half of the check report |

  `just` with no recipe lists them. The mechanism the lanes share (`java_test_count`,
  `reconcile_outcomes`, `break_residue`, `compile_guard`, `show_check_report`, `correlate`,
  `headline`) is `scripts/_lib.sh`, which every lane sources; the POLICY — which sbt project, which
  upstream tree, which dependencies — is a variable at the top of the `Justfile`. **Never add
  `set -e` to a lane**: `grep -c` exits 1 when it counts zero, and counting zero errors is the
  success case, so a lane under `set -e` aborts exactly when the port is green.

  Each prints, untruncated and diffed against the committed baseline, **every engine check the
  run's own pipeline registers, plus any check the port's own §1(c) rules register**. The total is
  not a constant to memorise — quoting one is what went stale twice; it
  is the TWENTY-SEVEN required of every run (`signature`, `omissions`,
  `portability(all|emitted|injected)`, `dependency-coverage(all|)`, `substitution(emitted|dangling)`,
  `remediation`, `policy`,
  `manifest`, `port-map`, `trivia(|recovered|deliberate)`, `jdk-surface`, `base-surface`,
  `rewrite-callsites`, `idiom(converted|refused|residue)`,
  `catalog(consulted|unreached|unmechanised|undischarged|uncited)`) plus
  whatever the RUN'S OWN PIPELINE registers. `base-surface` is required of a BASE port too, which has
  no contract to ask: a run that asked nothing and a run whose recording was skipped are
  indistinguishable without the row. The trivia family is three lanes and all three
  are required, because `lost = 0` is the bar and a run could hold it by RECOVERING everything —
  `recovered` is a counted residue and `deliberate` is derived from the port's own drops, so
  reporting the bar without them says nothing about how it was met. **The catalog family is four,
  for exactly that reason one artifact over**: `unreached = 0` is a bar a run could hold by
  declaring every difference row unmeasured, so the positive (`consulted`), the two residues
  (`unreached` — narrowed to rows whose discharge surface EXISTS — and `unmechanised`, the rows
  nothing is instrumented to answer for) and the work list (`undischarged`) are reported apart.
  `just catalog-coverage` is the corpus-wide half, and it is the recipe an agent runs before
  claiming a rule is live: a row unreached on one small library is normal, a row unreached on all
  fifteen is dead code or an untested rule.
  **The idiom family is three, for the trivia family's reason one artifact over again**:
  `idiom(refused) = 0` is a bar a run could hold by CONVERTING NOTHING, and `idiom(converted) = N`
  says nothing about the population `N` was drawn from — so the positive, the refusal population
  (one row per declined site, naming the guard) and the unrewritten-usage residue are reported
  apart, with the DENOMINATOR recomputed beside them on every run. All three are required of every
  port including one with no idiom phase, for `jdk-surface`'s own reason. **`catalog(uncited)` rides beside those four and is not
  one of them**: it counts registry rows with no Scala-side normative citation, which says nothing
  about the port and everything about the registry — it is here because `counts.tsv` is the artifact
  a baseline diffs and that number was a `println` in one spec beside `assert(uncited <= all)`, which
  no registry can fail. It is never asserted on: a spec that failed on it is a spec somebody silences
  by INVENTING a citation, which is worse than the gap.
  **`dependency-coverage` is the twenty-second and twenty-third and is the OTHER half of
  `portability(*)`, not a
  subset of it**: half of the platform matrix's answers are that the API EXISTS off the JVM, in an
  artifact the build does not name, and a build-graph fact reported as a symbol-reference one is a
  finding the reader cannot act on — they are told to remove a call that one `libraryDependencies`
  line makes correct. A finding needs three conjuncts (the usage fired, no declared dependency
  covers it, the port declared no alternative) and only the middle one is a filter: the third is
  read THROUGH `PortManifest.verdictOverrides`, so a port that says it ships its own shim never
  produces the requirement at all. Written as a second filter that conjunct could disagree with the
  first, which is the shape of a check reporting a row it has already excused. **And it is a PAIR
  for `portability(all|emitted)`'s reason**: the residue passes two filters — this module's own
  emitted code (D2) and coverage by a declared dependency — so a dependent port, whose requirements
  legitimately belong to its base, reports an honest `0` that is indistinguishable from a walk that
  found nothing, a rule list that matched nothing, or a target set that emptied it.
  `dependency-coverage(all)` is the enumeration behind it and the difference is one subtraction. It
  is deliberately not spelled `(emitted)`: naming one of the two filters would hide the other;
  `porter-notes`, `break-catch`, `try-resource`, `switch-null`, `heap-pollution`,
  `cast-conversion`, `overload-risk` and `markers` record on every run,
  `collection-closure`/`collection-boundary`/`collection-retarget` record when
  `CollectionsTransform` is in the pipeline, and `nullability-boundary` when
  `NullabilityTransform` is. **A retype has TWO directions and a subtyping argument
  licenses only one of them**: `collection-retarget` counts the other — every value the JDK
  PRODUCES at a type the port retargets, which the boundary check cannot see, because the
  position-blind retyping moved the node type on both sides of that slot. `PortRun.RequiredChecks` is asserted against what
  actually recorded, so a number that reaches stdout and not `findings.tsv` fails the run.

  **And a RESOLUTION DRAINS A LANE VISIBLY — both halves, or the improvement is unreadable.** Where
  the engine has no single right answer, a phase or check publishes a MENU of named remedies and a
  port SELECTS one per location (`DESIGN.md` §8.16). Applying one is not a fix, it is a MOVE: a row
  leaves the refusal lane that counted it and arrives in `remediation(resolved)`. So a baseline diff
  must read `<lane> N->M, remediation(resolved) 0->(N-M)`, and a lane that fell with nothing to
  attribute the fall to is exactly the shape §1's residue rules already refuse — indistinguishable
  from a check that stopped asking, from a rule list that emptied, and from a port that got better.
  That is the trivia family's own argument (`lost = 0` is a bar met by RECOVERING everything) read one
  artifact over, and it is why a remedy DECLARES the lane and kind it drains rather than merely
  claiming to have helped. **And a selection that did nothing is a finding, not silence**: a key can
  bind to a real declaration, name a live remedy, and be inert because the finding never fired —
  which no binding can say, so it is reported apart (`PolicyIssue.NeverApplied`), declared beside
  applied, on §5.5's `expected#derived`/`#declared` model.

  Four more measurements are NOT check counts and are printed beside them, because each catches a
  class nothing else can see:

  - **`break_residue`** — untranslated `break`/`continue` jumps left in emitted code. It was quoted
    in prose as "45, all switch-case" while nothing computed it; the real number was 55 (§4.4).
  - **the TEST lane** — `reconcile_outcomes` reconciles outcomes against the **emitted** test count,
    not against a sum of markers, so a test with no recognised line is reported whatever the reason
    (§5.1). A skipped test is not a passing test. Nor is one that was never EMITTED — the
    discovery figure beside it is baselined too, and both directions of it are fatal.
  - **`members.tsv`** — which members' emitted text moved, available BEFORE any compile (§5.1).
  - **`decisions.tsv` + the porter notes** — how many non-mechanical decisions the port made, by
    kind, and whether every one of them reached the code (§4.575). `porter-notes` is the check;
    `just decision-counts` is the size, which nothing else prints.
- **THE ERROR COUNT IS BASELINED TOO, and in BOTH directions.** It is the number every commit
  subject quotes and it was the one measurement nothing compared — every check, every member digest
  and every test outcome was diffed against a committed file, while the headline was printed and
  thrown away. So a lane could go 0 -> 3 and `measure-all` would run straight through it, because a
  non-zero count is a legitimate state for a port that has not reached zero and no lane could tell
  "3, as always" from "3, as of this commit". Measured exactly that way on the screens lane.
  `baseline/expected-errors` is a one-line file per lane, written by the run itself
  (`run-latest/errors-count`) and promoted by `just baseline-accept`, so nobody ever types the
  number — a hand-edited floor is the one baseline that can disagree with the run that produced it.
  **Fewer errors fails the lane as loudly as more**: a change is acknowledged by re-accepting, and a
  lane that absorbed improvement would let a fix and a regression cancel inside one run.
- **…AND SO IS THE NUMBER OF TESTS THE PORT EMITS, for the same reason and with the same file.** The
  error count was not the only measurement nothing compared. Every test lane already counted what
  each framework would DISCOVER in the emitted Scala against the `@Test` count in the upstream java
  — because a suite with no discoverable tests runs ZERO and reports SUCCESS — and then printed the
  difference in a line that exited 0. On a port with a declared loss that line is a CONSTANT
  (liqp: `!! TESTS LOST — 64 of 639`, from four `excludeGlobs` files and three `dropMethods` keys),
  so an operator reads past it and a 65th test lost to a conversion regression changes one digit
  inside it. Nothing else can see that loss: the test is not failing, it is not skipped, it has no
  row in `tests.tsv` for any baseline to hold an opinion about, and no compile-error count moves.
  So the expected loss is `baseline/expected-lost`, written by the run and promoted by
  `just baseline-accept`, and `test_discovery_guard` fails the lane in EITHER direction — a
  RECOVERED test is a change to acknowledge, not an improvement to absorb. A port that loses nothing
  is held to 0, which is the normal case and deliberately not an exemption.
  **`TestDiff.disappeared` is the same failure seen from the artifact side** and was rendered and
  ungated on the grounds that deleting a test is somebody's decision — true of a deletion, false of
  a conversion regression, which removes the rows from both sides at once. It gates now; a real
  deletion is acknowledged by re-accepting, which is what makes the decision a recorded fact.
- **…AND SO IS THE CONTENT OF `findings.tsv`, which was PROMOTED and never read.** The third
  baseline nothing compared, and the one whose absence is hardest to see: every check COUNT can be
  identical while a finding's OWNER, the `UsageKind` it was seen at, or a running total printed
  inside its own text has moved — none of those is a count, so `counts.tsv` holds and the lane is
  green over a changed answer. The file was left ungated for a good reason, and it is a reason about
  ONE COLUMN: `Finding.id` is a hash with a `/2`, `/3` sequence assigned in LINE order, so an
  upstream whitespace edit renumbers rows that did not change. `findings_baseline_guard` drops
  exactly that column (`cut -f2-`) and diffs the six that carry meaning, in the writer's own sort, in
  either direction. What it found first was its own justification: **eight dependent baselines had
  been stale since waves 0/1**, carrying a declaration count the base's own commit had moved
  `280 -> 220` — invisible to every other artifact, and mis-attributed in prose to a
  worktree-versus-primary difference until somebody read the row rather than the diff summary
  (`PROGRESS.md` §12.2.5). A count is not path-derived; a §5.4 explanation could never have produced
  it.
- **Change one thing, then measure.** Two changes measured together cost a full cycle to untangle
  and tell you nothing about either.
- **A DRY RUN of one phase is not a measurement of the pipeline.** Running a single phase over a
  library — the cheap way to price a policy before enabling it — measures that phase against
  UNTRANSFORMED input, and every other surface phase in the port is a phase that has already moved
  what it reads. Quote such a number as what it is. Measured: a warning lane priced at **1** by a
  dry run reads **25** in the live pipeline, because a `TypeRedirectTransform` earlier in the list
  gives 24 more classes an ancestor outside the program and the warning's criterion is exactly
  "ancestry leaves the program" (`ENGINE-LIMITS.md` CT7's correction). Nothing was wrong with the
  criterion or the dry run; what was wrong was reading one as the other.
- **Record what regressed and why**, in that library's `PROGRESS.md` section under "Do NOT retry". A
  measured failure is a result; re-deriving it later is waste.
- State counts as `before->after` in the commit subject.

### 5.1 A diagnostic over emitted code is ATTRIBUTABLE — never read it by hand

`PortRun` writes `srcmap.tsv` (member → emitted line range → Java `Origin`) and `members.tsv`
(one digest per emitted member) into the run's report directory on every migration, from the
emitter's own recording — `TirEmitter.srcMap` is a value one emitter owns, never a process-global
table. `balticporter.tir.CorrelateRun` joins compiler output and TEST-RUNNER output back through
them, in-process (`PortRun.correlate`) or as a command (`CorrelateMain`). The measure lanes run
the command; run it yourself when you run a compiler by hand. Three consequences that change what
you should do:

- **Never open an emitted file to work out which member an error is in.** `errors.tsv` already
  says, with the Java file and line, and splits errors into "at a region the engine marked
  approximate", "engine gap" and "outside the source map" (an injected shim is none of the other
  two) — plus, on a `preview = true` run only, "declared unrenderable" (`Correlate.Lane.Declared`),
  so the engine's own `compiletime.error` markers never inflate the real error count.
- **The member-digest baseline is the blast radius, and it is available BEFORE a compile.** After a
  change, `run-latest/members.tsv` against `baseline/members.tsv` says exactly which members'
  emitted text moved. Identical files mean the output is byte-for-byte unchanged — which is a
  stronger revert check than any count, because *no check count moves for most transform
  regressions* (with the whole pipeline skipped, every check count is unchanged).
- **…and a baseline is only ever a claim about the run that PRODUCED it, so accept it from a run
  that is CURRENT.** A committed baseline row that neither of two checkouts can reproduce at the
  commit that ships it reads exactly like an environment defect (§5.4 is full of real ones), and
  the first suspects — a symlinked path, a resolver difference, an iteration order — are expensive
  to chase. Measured: one `members.tsv` row diverged between a worktree and the primary checkout at
  identical commits, and what settled it was running the lane in the SECOND checkout at the FIRST
  one's commit: the two agreed exactly, and the committed value turned out to be the digest that
  statement had *before the same wave's own earlier commit* moved it. A baseline accepted from a
  `run-latest` that predates a later edit in the same wave is stale, and nothing reports it: the
  numbers all agree, only one digest is from another run. So re-run the lane before
  `just baseline-accept`, and when a digest cannot be reproduced, compare **checkout against
  checkout at one commit** before reaching for an environment explanation.
- **The test lane is the only one that sees §4.4.** The §4.4 table's Java forms translate to valid Scala meaning
  something else and move no error count. `tests.tsv` is the pass/fail baseline and the diff names
  newly-failing tests, anchors each on the first stack frame in ported code, and says how good that
  anchor is (`main-frame` = the library member that threw; `test-frame` = only where the failure
  was observed). A test that stopped running is reported as such, never as a pass.
- **Parse every TERMINAL MARKER the runner emits, and gate on each.** MUnit prints three — `  + `
  (pass), `==> X ` (fail) and `==> s <suite>.<name> skipped 0.0s`. The third was dropped by the
  parser, so a suite abandoned after a fatal error lost its remaining tests from `tests.tsv`
  entirely and the run reported success (ashley: 112 emitted, 110 recorded). A skip moves no pass
  count and no fail count, which is exactly why it needs its own gate — `TestDiff.newlySkipped`,
  and `skipped` is kept apart from `ignored` because an ignored test is a DECISION and a skipped
  one is PREVENTION. Same rule for a missing INPUT: a `--tests` path that does not exist is fatal,
  never a header-only artifact and a headline of "0 passing, 0 failing". And a test with no row at
  all — `TestDiff.disappeared` — gates beside them: a skip is a test the runner REACHED and did not
  assert, a disappearance is a test the run never had, which is what a conversion regression that
  stops EMITTING a suite looks like from here. Both sides fall together, so the run reports success
  on a smaller suite; a deliberate deletion is acknowledged by re-accepting the baseline.
- **…and the same rule reaches the ENGINE'S OWN SPECS, where nothing is gating it.** The
  ported-test lanes gate on `TestDiff.newlySkipped`; `sbt <project>/testOnly *` gates on nothing —
  it prints `Skipped 1` and exits 0. So an `assume`-guarded spec whose precondition is ANOTHER
  RUN'S ARTIFACT (a published `port-map.tsv`, an emitted source set, a vendored upstream tree) does
  not run in a fresh checkout, and a hard-coded expectation inside it can go stale for as long as
  nobody happens to run the port first. Measured: `PortMapAcceptanceSpec` asserted a
  `DroppedType` count of **8** while the answer had been **7** since the base gained an injection —
  it reads `port-report/<base>/run-latest/port-map.tsv`, which a fresh worktree does not have, so
  every `corpus/test` since had reported success without executing it. **Two things follow**: a
  spec that `assume`s on an artifact is one nobody is running unless a lane produces that artifact
  first, so run the suites AFTER `just measure-all` and not before; and `sbt test` here is
  `testQuick`, which re-runs only what the last change touched — the full suite is
  `testOnly *`, and a wave that never types it is a wave that has not seen its own spec failures.
- **`decisions.tsv` says WHY the emitted code is not a mechanical translation.** The source map
  answers "which Java produced this line"; it cannot answer "why is this type simply absent, this
  package not the upstream one, this member from a hand-written file". Each of those is a
  `balticporter.tir.Decision`, and its `Reason` is a CONSTRUCTOR PARAMETER — `Universal(rule)` /
  `Configured(phase, key)` / `LibraryRule(rule)` — because §4.45's rule applies to provenance
  exactly as it does to findings: a note that does not say which of §1's three kinds the fix is
  costs its reader a full investigation. For a `Configured` one the KEY is the manifest entry
  verbatim, which is the string an agent edits to change the outcome. A phase records with
  `Phase.record`; `Pipeline.runTraced` hands back the log; the run's non-phase deciders record into
  the same one. The log is a value ONE RUN owns and each phase's buffer is drained into it, for the
  reason stated above for the source map. Every decider records at the DECLARATION level — one row
  per declaration whose emitted form the decision changes, never one per expression: a site-level
  rewrite is already visible in the diff the reader is holding, and what the diff cannot say is
  which policy entry produced it. And the artifact is scoped to THIS MODULE's declarations, because
  `ENGINE-LIMITS.md` D2 governs a provenance artifact exactly as it governs a check — a dependent's
  phases decide about its base's units too, and republishing those puts a module's own rows in a
  minority in its own file (libgdx-test: 961 of 1240).
- **An artifact write is GATED ON THE ARTIFACT LAYER, without exception.** One unconditional
  `PortMap.write` was enough for the engine's own forked test suites to publish port maps into the
  checkout (`<module>/port-report/…`, and once a COMMITTED `port-report/jar/`), because with reporting
  off the report directory falls back to `<cwd>/port-report/…` and a forked test's cwd is the
  subproject. A `git status` that cannot distinguish a decision from an artefact defeats §5.5, and
  the gate belongs at the write, not in each caller — a wrapper every spec must remember is a
  wrapper one spec will not.
  **…and the gate is on the LAYER, never on a flag, because the DIRECTORY the layer would write to
  is itself derived from ambient state.** `CheckReport.dir` falls back to
  `port-report/<this JVM's main class>`, which is exactly right for a migration `main` — that is the
  measurement identity §2.1 keeps stable across a module rename — and answers `WorkerMain` under
  sbt's forked test JVM, whose command is the build's own worker. So reporting turned on WITHOUT an
  explicit `reportDir` published a run directory into the checkout from a JVM that had no port
  identity at all, and the flag read like the gate. Where no identity can be derived the layer is
  OFF whatever the flags say; an explicit `reportDir` is an identity the caller supplied, and is
  the one thing that still enables it.

Deliberate failures are **DERIVED, not listed**. A test whose failure stack reaches a type in the
port's `Substitutions.dropTypes` fails because the port deliberately does not have that type, so
`PortRun` writes those FQNs to `run-latest/dropped-types.tsv` on every run — `upstream` TAB
`emitted`, both namespaces, because the stack frame it will be matched against is renamed and the
manifest key is not (§4.56) — and the correlator classifies from them: the set follows the manifest
with nobody editing anything, and the engine still names no library (§1). `port-report/<Port>/baseline/expected-failures.tsv` survives ONLY as the
explicit escape hatch for a failure no drop explains, and is normally empty; the artifact records
`expected#derived` against `expected#declared` so the two can never be confused. A hand-maintained
list of expected failures is exactly the thing that rots into "we always ignore those four" and
then hides a fifth. Promote every baseline with `just baseline-accept <port>`.

---

## 4.45 The consumer is an AGENT IN ANOTHER REPOSITORY

The engine's users are not this repository. sge and ssg will maintain their ports by pointing a
published Baltic Porter at their Java sources, with agents doing that work in *their* repos,
without this session's context. Two standing consequences:

- **A lesson that is an ENGINE limit must live somewhere the engine ships.** CLAUDE.md,
  `ENGINE-LIMITS.md`, a skill, or an agent definition — not only in a per-library status section. §3.6
  already says this. The engine-scoped dead ends measured on libGDX — the raw-anon refusal, `given
  Conversion` never firing, wildcards round-tripping across an override, "erase uses, never
  declarations" — are now lifted into `ENGINE-LIMITS.md`, with the counts and the per-site diagnosis
  left in `PROGRESS.md`'s section for that library, under a pointer. **When you measure a new one, put
  the rule there in the same commit.** The remaining exception is per-library POLICY, which is where it
  belongs.
- **A check must say which of §1's three kinds the fix is.** An error an agent cannot classify as
  (a) engine bug, (b) configure an existing phase, or (c) write a library-specific rule costs it a
  full investigation. `PortabilityCheck` and `RewriteTrace` do this well; bare typer errors do not,
  and they are the bulk of a new library's first wall. Every `ENGINE-LIMITS.md` entry carries this
  classification for the same reason.

`PROGRESS.md` §Publishability holds the full audit of what is missing before that consumption is
possible.

## 4.5 Never model a Java interface on a Scala COLLECTION trait

When a Java interface needs a Scala counterpart the stdlib does not have, write a standalone trait
with Java's own shape — Java's method *arity* included (`iterator()`, `hasNext()`, `next()`, not
Scala's parameterless forms). Restore Scala interop with **extension methods**, never by extending
`scala.collection.*`.

The reason is not taste. Java interfaces are small and orthogonal, so a class routinely implements
several: 14 classes in libGDX core implement both `Iterable<E>` and `Iterator<E>`. Scala's
collection traits are large and interlocking, and that same shape is *illegal* under them —
`Iterator.iterator` is `final`, `seq` arrives from both parents. No `override` recovers it, because
the conflict is in the parents. Inheriting also imports hundreds of members that then clash with
the ported class's own `size`, `isEmpty`, `remove`.

An extension adds a VIEW and cannot conflict; a parent adds MEMBERS and does. Put such an extension
on **one** of a pair of related shims, never both — with `foreach` on both an iterable-and-iterator
class made every `for` ambiguous.

Note this whole class of defect is invisible while any typer error remains (§3).

## 4.4 Java statement semantics Scala does not share — the ones that COMPILE

Each of these translates to syntactically valid Scala that means something else. None moves a
compile-error count; every one was found by RUNNING the ported tests, never by a compile:

| Java | naive Scala | why it is wrong | faithful |
|---|---|---|---|
| `a == b` (references) | `a == b` | Scala's `==` calls `equals` — and inside an `equals` body that is infinite recursion | `a eq b` |
| `x++` as a value | `{ x += 1; x }` | post-increment yields the value BEFORE the update; every circular buffer was off by one | `{ val p = x; x += 1; p }` |
| `break` / `continue` | *nothing* | the loop simply ran on / the rest of the body still executed | `boundary` around the LOOP for break, around the BODY for continue; name the outer one when both |
| `break L` / `continue L` | *nothing* | a labelled jump crosses nested loops and switches by definition | a NAMED boundary on the labelled statement, `break(())(using brk)` |
| `L:` on a statement that is NOT a loop | *nothing* — a label on an `if`, a block or a `switch` had nowhere to live | `break L` leaves THAT statement; dropped, `JsonReader` fell through after `bool(name,true)` and emitted a second, string event for every unquoted bool/null/number | `Tree.Labeled` + a named `boundary` around the STATEMENT. A LOOP's label stays in the loop node — it is also `continue L`'s target, whose boundary goes elsewhere |
| any boundary the emitter INTERPOSES | an un-annotated `break(())` under it | `boundary.break` with no `using` resolves the INNERMOST `Label`, so a new boundary silently steals the enclosing loop's jumps | name the enclosing boundary whenever anything inside the body renders with a boundary of its own; over-approximate, an unused name costs one identifier |
| `switch` with no `default` | `match` with no `case _` | java FALLS OUT when nothing matches; scala throws `MatchError` — and falling out is often the normal path | always emit the fall-out arm |
| `switch` on a `String`/boxed/enum selector that is `null` | the same `match` | java throws `NullPointerException` the instant the selector is null (JLS 14.11), IMPLICITLY — a classic switch has no `case null` to opt out with. `null` matches no literal pattern, so it reaches the fall-out arm above and the EXCEPTIONAL path became a silent no-op. The same mechanism as the row above, read at the other selector value | `case null => throw new NullPointerException(…)` ahead of the java arms, for a selector whose type is not a scala value class — and never where the java itself writes `case null` (SE21's opt-out) |
| a case's trailing `break L` | stripped as a case terminator | only an UNLABELLED break ends a case; a labelled one leaves the LOOP | strip unlabelled only |
| a `break` in the MIDDLE of a case | *nothing* | it ends the CASE — and fallthrough is lowered by duplicating the next case's tail INTO this arm, so what ran on is code java put in a different case | a named `boundary` around the ARM; `match` cannot leave an arm early |
| an ARROW arm — `case A -> e;` (SE14) | the colon form's lowering: fallthrough duplication, and the fall-out arm above | java's arrow form has NO fallthrough at all (JLS 14.11.2) — that is what it was added FOR — so the terminator a colon arm ends on is simply not written. A rule that decides "did this case run on?" by looking for a trailing `break` finds none, and duplicates the NEXT arm's tail into every arrow arm in the switch: valid Scala, no moved count, and each arm runs the one below it | read the CASE KIND, never the body. A parser may also NORMALISE the arm — Spoon wraps `case 1 -> doIt();` in a `yield` node java has no word for — so undo the parser's shape before translating it, or the arm holds a jump the source never wrote |
| a `switch` in EXPRESSION position (SE14) | the same `match`, plus the fall-out arm above | a switch EXPRESSION cannot fall out: JLS 15.28.1 requires it to be EXHAUSTIVE, so `case _ => ()` answers `()` where java answers nothing and widens the expression's type on the way. The same rule reaches the STATEMENT form through the back door — an ENHANCED switch statement must be exhaustive too, so it has no fall-out to model either | no fall-out arm for an expression, and for a statement only where it is not enhanced. **Ask BOTH of JLS 14.11.2's disjuncts.** The labels alone are not enough, and the reason is a SE21 form that no label betrays: a QUALIFIED ENUM CONSTANT (`case Coin.HEADS`) is a constant label at a selector typed as the enum's SUPERTYPE, so the switch is enhanced with not one pattern and not one `null` in it — javac calls the statement after it `unreachable` and compiles a `MatchException` throw, while a `case _ => ()` throws nothing. Read it off the SELECTOR'S TYPE and never off the label's shape, which is measured the other way too: the same qualified label at the enum's OWN selector is classic and really does fall out. And `provably` outside the classic set — resolution failure keeps the fall-out arm, since dropping it because `noClasspath` could not answer is the same defect in the other direction, on every classic switch in a corpus. Where java's own exhaustiveness fails at run time it throws `MatchException` and scala throws `MatchError`: both throw, and the class is the only difference |
| `yield v` that is NOT the arm's last statement (SE14) | the value, in place | java's `yield` completes the WHOLE switch expression abruptly, from arbitrary depth (JLS 14.21). Dropped into place it is an expression whose value is discarded, and every statement after it still runs | peel a TAIL `yield` into the arm's value — which is what a scala arm already means — and give any other one a value-carrying `scala.util.boundary` around the ARM, jumped to as `break(v)(using n)`. The same interposition a mid-case `break` gets, at a `Label[T]` rather than a `Label[Unit]`; the two never coexist, because JLS 15.28 forbids a `break`, `continue` or `return` whose target lies outside a switch expression. **And `arbitrary depth` includes a nested switch STATEMENT** — 14.21 re-binds a `yield` at an EXPRESSION and nowhere else, so a statement switch in between is an intervening construct like any other (javac runs `case 1 -> { switch (b) { case 2: yield 10; … } yield 20; }` and answers 10). One IR node renders both java switches, so a walk that stops at "a switch" tells the outer arm it holds no yield and lets the inner statement switch mint the boundary at its own `Unit` — a `break(10)` with nothing of the right type to jump to. Carry the java construct on the node; a `Unit` result type is a coincidence of java's rules, not a statement the IR makes |
| `static final int X = 0` | `final val X: Int = 0` | java INLINES a constant variable, so reading it never triggers the class initialiser; the typed `val` does, and libgdx's `Vector3`/`Matrix4` initialisers are a cycle | `inline val X = 0`, literal rendered AT the declared type |
| a java CLASS INITIALISER — `static { … }` **or a non-constant static FIELD initialiser** | `locally { … }` / a `val` in the companion `object` | it is EMITTED and never RUNS. Java initialises the CLASS on the first `new`, the first static access or a subclass's init (JLS 12.4.1); Scala initialises the OBJECT when something touches the OBJECT, and `new Template(…)` does not. Where it REGISTERS something — an SPI provider, a factory, a codec — every later lookup silently answers "not registered", which a library then turns into a plausible wrong answer rather than an error. 5 test failures on liqp, 0 compile errors, every count flat. **And the two spellings are ONE construct**: JLS 12.4.2 step 9 runs the static field initialisers and the blocks as one sequence, so a census keyed on the BLOCK reports 0 on trees that have the defect | reproduce JAVA'S OWN TRIGGER LIST, item by item, and nothing else. Two of its items are broken and each gets the same statement in a different place: `val _ = <Type>` at the head of the CLASS body (instantiation — ahead of every field initialiser, where java ran `<clinit>` relative to them, and it also carries a subclass's `new S` through the super constructor), and `val _ = <Ancestor>` at the head of the COMPANION body (a subclass's own initialisation, JLS 12.4.1 item 7 — forcing only the NEAREST bearing ancestor, and never crossing to a superinterface that declares no default method, which item 7 does not reach). A static ACCESS needs nothing (`T.member` already touches the object) and a CONSTANT needs nothing (the row above inlines it, exactly as java does). **NOT from every use**, and not for a form nothing can `new` — an interface bearing a non-constant static field is step-9 content with no instantiation trigger to lose. TWO refusals, both counted: reflection (a reflective load of the emitted class does not touch its module, and the load lives in the port's CONSUMER), and a companion whose initialisation is a MUTUAL CYCLE — the JVM lets a thread re-enter a class it is already initialising and a Scala module in a cycle has no `MODULE$` yet, so forcing libGDX's `Vector3`/`Matrix4` pair traded 26 passing tests for an `ExceptionInInitializerError` at first use. liqp `567 -> 572` passing, 8 -> 3 failing (`ENGINE-LIMITS.md` K22, catalog `JS-C07`) |
| `super(args)` in a 2nd ctor | *nothing* | scala secondary constructors cannot call super; every exception lost its message | promote the widest super call to the PRIMARY and delegate (JDK throwables only — elsewhere padding is a guess) |
| `@Before` | *nothing* | JUnit runs it before EVERY test, on a fresh instance; MUnit has neither | call it at the head of each test body |
| `@Test(expected = E.class)` | body run bare | it would PASS while checking nothing | `intercept[E] { … }` |
| an array forwarded through a `T...` slot at an EXTERNAL callee — `String.format(fmt, args)` | the same call, array unspread | scalac reads a class file's `T...` as a REPEATED parameter, so the bare array conforms as ONE element wherever that element is `Object` — `format` prints the array for the first `%s` and throws for the second. Where the element is not `Object` it is an uncounted compile error instead. A callee the port DECLARES is unaffected: its parameter is emitted `Array[T]` | the spread, `args*` — measured faithful, `Arrays.asList(arr*)` still ALIASES `arr` as java's does (`ENGINE-LIMITS.md` K6.5) |
| a PRIMITIVE array at a reference `T...` slot — `Arrays.asList(intArr)`, `String.format("%s", intArr)` | a forward (the row above), or the bare array | java's rule for the slot is ASSIGNABILITY, and `int[]` is assignable to nothing but `int[]` — so java does not forward it at all, it builds `new Object[]{ intArr }`. `asList` is a `List<int[]>` of SIZE 1 and `format` gets ONE argument printing `[I@…`. Spread instead, both compile and change the call's arity | pack as ONE element. "The argument is an array" is not the question — compare the COMPONENT to the vararg's, and a primitive passes through only at its own primitive's vararg |
| `arr[f()] += x`, `arr[f()]++` — a compound assignment or increment at a SIDE-EFFECTING lvalue | `arr(f()) = arr(f()) + x` | java evaluates the array reference and the index ONCE and stores back through that same reference (JLS 15.26.2, 15.14.2); every arm translates the lvalue and uses the translation on BOTH sides, so `f()` runs twice — three times in expression position, whose value is also a re-read rather than what was stored | bind each lvalue subexpression to a temporary once (`{ val $r = arr; val $i = f(); $r($i) = $r($i) + x }`). **OPEN** — measured at 161 duplicated sites in the corpus of which 0 misbehave today, so it is recorded and counted rather than fixed or refused: `ENGINE-LIMITS.md` F7, catalog `JS-E17` |
| `list.remove(anInteger)` | `buffer.remove(x)` | java resolved `remove(Object)` — by VALUE, returning `boolean`; scala's only `Buffer.remove` is BY INDEX, and `Integer2int` applies silently, so `[10,11,12].remove(Integer.valueOf(1))` removes nothing in java and `11` in the port | a by-value helper. Read WHICH overload java resolved off the call's RESULT type: `remove(Object)` returns a primitive `boolean` and `remove(int)` returns the element, which java generics can never make primitive |
| a call whose CANDIDATE SET spans one of java's three RESOLUTION PHASES — `f(int)` beside `f(Object)`, a fixed-arity candidate beside a vararg one, a generic one beside a non-generic one | the same call, rendered as java wrote it | java resolves in THREE PHASES (JLS 15.12.2) — strict, then loose (boxing), then varargs — and a candidate admitted in an earlier phase WINS OUTRIGHT; scala resolves in ONE, and its most-specific rule PREFERS a non-generic alternative where java's does not. So javac and scalac can bind the SAME call to DIFFERENT members, and both programs typecheck: no error, no moved digest, nothing but a test that does something else. The `list.remove` row above is one JDK instance of this | there is no faithful translation short of a resolver, and that is REFUSED — the RISK is counted instead, at every call whose candidate set spans a phase, with the check's own denominator beside it (63,037 calls / 5,049 overloaded / 364 spanning, on libGDX core). `ENGINE-LIMITS.md` T17, catalog `JS-C22`/`JS-C23` |
| a java `record` (SE16) | a scala `case class` | six cells differ, MEASURED against `javac`, and two of them cannot be repaired at all. An EXPLICIT accessor — `public int y() { return y * 2; }`, which java permits — is `E120 Conflicting definitions` beside the case class's `val y`; and the generated `unapply` reads the constructor PARAMETERS where java's record pattern reads the ACCESSOR (JLS 14.30.1), so java binds `6` on that record and a case class binds `3`, silently. The other four: `toString` (`Pt[x=1, y=2]` against `Pt(1,2)` — bracket, field names, space), `hashCode` (javac's 31-fold from zero, `33`, against `MurmurHash3.productHash`, `2081183297` — the JLS leaves the ALGORITHM unspecified, so this binds nothing on its own, but two values are two hash-bucket orders), `equals` on `double`/`float` (java uses `Double.compare`, so `NaN` equals `NaN` and `0.0` does NOT equal `-0.0`; scala's `==` is the opposite on both), and the added surface (`copy`, `apply`, `productArity`, `canEqual` — the last for a problem records cannot have, being final by construction) | a plain `final class` with javac's four members WRITTEN OUT — `equals`/`hashCode`/`toString` over the FIELDS and an `unapply` over the ACCESSORS, which is java's own split and is exactly why the case class cannot be the image. What no image carries is THREE things, all recorded on the decision: the REFLECTIVE record (scalac emits no JVM record, so `Class.isRecord` is false and `getRecordComponents` is null), and the two cells where java's record pattern is a matching PROCESS and a tuple-returning `unapply` is a FUNCTION — every accessor runs where java stops at the first failing component, and an accessor's exception arrives raw where java wraps it in a `MatchException` (`ENGINE-LIMITS.md` T20, catalog `JS-C43`) |
| `x instanceof Map`, `(Map<K,V>) x` — a REIFIED occurrence of a type a phase RETYPED | `x.isInstanceOf[mutable.Map[?, ?]]`, `x.asInstanceOf[mutable.Map[K,V]]` | a retyping moves STATIC types; these two ask about a RUNTIME OBJECT, and neither the objects nor their classes moved. A ported library holds BOTH representations at every `Object` slot — the ones its own code made, and the ones jackson, a parser or its own CALLER made — and java's test accepted all of them. Measured at **160 of liqp's 183 remaining test failures, 0 compile errors, every check count flat** | answer java's question over both representations (`JavaCollections.Reified`), and where the target is a CONCRETE one no live view can be (`mutable.HashMap`, `ArrayBuffer`, `Tuple2`) REFUSE and count. An UPCAST — an operand the phase itself retyped — is not a reified question and must be left alone (`ENGINE-LIMITS.md` K18) |
| `TypeReference<Map<K,V>>`, `TypeToken<…>`, `Class<T>` — a type ARGUMENT a THIRD PARTY reifies | the argument retyped along with every slot | the argument survives into the class file's generic signature, and jackson/gson/guice READ IT BACK and act on it — `Cannot construct instance of scala.collection.mutable.Map`. The port writes this position nowhere, so no phase can walk to it and every check reads clean; measured at **10 of one library's 23 remaining failures** | do not retype a type argument a reified CARRIER holds; bridge at the USE, where a value exists. The carrier list is per-library (b) — `java.lang.Class` is the only one java guarantees (`ENGINE-LIMITS.md` K20) |
| `try (R r = …) { … }` | a bare `try { … }` | java closes every resource on ANY completion — normal, exceptional or a jump — in REVERSE declaration order and BEFORE this try's own `catch`/`finally` (JLS 14.20.3). Dropped, a resource NAMED in its body is a loud compile error and a resource opened for its side effect alone (`try (var lock = acquire()) { … }`) compiles perfectly with nothing acquired and nothing released | JLS 14.20.3.1's own lowering, INLINE as statements — one nesting per resource, `primary`/`addSuppressed` for a `close()` that throws under an already-abrupt body, and a `case b: boundary.Break[?] => throw b` arm AHEAD of that recorder, because a java jump carries NO exception object for a failing `close()` to be suppressed into (recorded as `primary`, the close exception hit `Break`'s disabled suppression and vanished). Never `Using(r) { r => … }`: the body holds `return`s and `boundary.break`s bound OUTSIDE the try, and neither survives a lambda |
| a jump inside a `try` whose `catch` is broad (`Exception`/`RuntimeException`/`Throwable`) | the `boundary.break` emitted under that translated `catch` | java's `break`/`continue` is a JUMP — no handler can intercept one; scala's is `boundary.Break`, which **extends `RuntimeException`**, so the handler eats it, the loop runs on, and the catch body runs for a condition java never had. Not incidental: dotty's `DropBreaks.prepareForTry` shadows enclosing labels, so a break under a `try` is ALWAYS the exception form | a re-throw arm AHEAD of the java arms — `case b: scala.util.boundary.Break[?] => throw b` — wherever a jump really crosses the catch. Exact rather than a compromise: java's semantics say that handler never sees that jump. `finally` is untouched (both languages run it and let the jump through), and a NARROW catch is left alone |

Before adding a translation for a Java *statement* form, ask what it means when its value or its
control flow is used, not only what it looks like. And read §3 again: a green compile said nothing
about any of these.

**This table is the rows an agent must know BY HEART; it is not the complete set.**
`balticporter.catalog.Differences` is — every Java-vs-Scala difference the engine knows about, as
code, with its status re-derived against the engine rather than transcribed (`DESIGN.md` §2.8, and
`just catalog` renders it). Each row above is a catalog row whose `twin` says so.

## 4.55 A renaming pass reads EFFECTIVE names, PARENTS-FIRST

Java lets a name be reused where Scala cannot: a field shadows an inherited field, a field coexists
with a same-named method, a constructor local becomes a member. Each is fixed by renaming, and
renaming the symbol propagates to every reference — which is exact, because Java resolved all of
these STATICALLY, so each reference already points at the symbol Java chose.

Two things every such pass must do, learned by getting both wrong:

- **Read effective names, not original ones.** If an ancestor has already been renamed, the new name
  is what a descendant must avoid. Reading originals renames `CheckBox.style` and `TextButton.style`
  to the same `style$shadow` and just moves the collision up a level. Then keep appending until the
  name is free.
- **Scan parents before children**, or the previous point cannot hold.
- **A CAPTURE can be the thing that has to move.** The three faces above rename a MEMBER; the fourth
  renames a method's local or parameter, because the shadowing runs the other way. Java's two
  namespaces let a parameter `filter` and a nested class's `Response filter(a, b)` coexist, and a
  bare `filter` inside that class is unambiguously the parameter; Scala has one namespace and
  resolves innermost-first, so the member wins and the capture becomes UNNAMEABLE — Scala can
  qualify an outer member (`Outer.this.x`) and cannot name a shadowed local at all. Rename the
  capture, and rename it only where it is really shadowed (referenced inside the nested body AND
  the body declares or inherits the name): a local rename is invisible, but a PARAMETER rename
  moves emitted surface, so this is the one member-rename pass that must not over-approximate.
  Note the nested class is usually ANONYMOUS, so it lives inside a TERM — a walk over class bodies
  finds none of them (§3).

**…and a NAME CLASH and an IMPLEMENTATION PAIR look identical, so a renaming pass must ask which it
is.** Every pass above renames because two declarations cannot share a name; none of them asks
whether the two are the SAME MEMBER. That question does not arise while the emitted forms are java's
own — a java field never implements a java method — and it arises the moment any phase turns a
member into a PROPERTY. `resolveFieldShadowing` renamed a collapsed `var w` to `w$shadow` under the
interface's abstract `def w` it was the implementation of, leaving the class abstract; and **nothing
reports that until the port is already at 0 typer errors**, because `RefChecks` does not run before
then (§3), so it arrives on the day a port goes green in a member nobody is looking at. Decide it
from the EMITTED SHAPE, which is exact rather than heuristic: a scala `val`/`var` and a scala
PARAMETERLESS `def` of the same name across a subtype edge are an implementation pair, and java
cannot produce that shape at all because a java method always has a parameter clause. The general
form is §4.56's, read at a rename: *a pass may only conclude "these two are different members" from
a structural fact, and two names being equal is not one.*

And count what the constructor funnel PROMOTES — the chosen constructor's parameters *and* its
top-level locals. Neither is in the class body, both become members, and a Java constructor local
becoming a Scala member is exactly what a subclass then collides with.

**…and a promotion moves a NAME, never a POSITION.** The rule above is the whole of the renaming
question and none of the ordering one, and the two look alike because the promoted local ends up
spelled as a class member. A Scala class body IS its constructor, so a `val` initialiser and a
statement are the same kind of thing there and their relative order is the entire semantics. JLS
12.5 is the cut: a FIELD initialiser runs in step 4, before any constructor body statement — so
hoisting a field above the promoted body puts it where java runs it — while a constructor's own
LOCAL DECLARATION is a step-5 body statement whose position among the other statements carries every
dependency between them. Emit it where java wrote it. Both are `ValDef`s and the difference is
OWNERSHIP (§4.56): the frontend interns a field under
the CLASS and a local under the enclosing EXECUTABLE, so "is this a member of the class whose body I
am ordering?" is a symbol lookup, and a name, an origin line or a per-caller "was this in the plan's
body?" test are all the string-shaped answers §4.56 is about. Measured at **409 of 414 test failures
on one constructor, 0 scalac errors, every check count flat** (`ENGINE-LIMITS.md` C12) — and note
what that number is evidence for: the compile, the twenty-two checks and the member digests were all
green throughout, so only §3's gate could see it.

**…and STEP 4 IS NOT ONLY FIELDS, which is the half "in textual order" quietly did the work of.**
JLS 12.5 step 4 runs field initialisers and INSTANCE INITIALISER BLOCKS as ONE sequence, in textual
order (12.4.2 step 9 says the same of the static pair). A block is not a `ValDef` — it is a
synthetic member under the JVM's own name — so any pass that groups "the fields" and "the blocks"
into two lists has already lost the only thing that decides what the class computes:
`{ b = 2; } int b = 5;` leaves `b == 5` in java and left `b == 2` in the port, because the
assignment java ran FIRST ran LAST. **Ask which STEP a member belongs to, never which node kind it
is** — and check the FRONTEND as well as the emitter, because a body assembled as
`fields ++ … ++ initBlocks` has thrown the order away before any emitter can honour it
(`ENGINE-LIMITS.md` C12's correction: 16 member digests over five ports, every headline, check count
and suite outcome identical — the same §3-only evidence as C12 itself).

**…and it moves MUTABILITY, which is the third axis and the only loud one.** A java constructor
parameter is an ordinary LOCAL (JLS 8.8.1) and may be reassigned; a scala class parameter is a
`val`. So `C(int x) { x = x * 2; this.f = x; }` promotes to `Reassignment to val x$p` — and a
record's COMPACT constructor is the shape that makes this ordinary rather than exotic, because JLS
8.10.4 exists PRECISELY so that a record can normalise its components by assigning the parameters
before the appended field assignments read them. The emission is `private var`: it keeps the value
per-construction, keeps the header's arity, types and descriptor, and puts no name on the emitted
SURFACE, which a bare `var` would — java's parameter is not a member and the promotion must not make
one a consumer can see. Narrow it to the parameters really ASSIGNED, decided by SYMBOL over the
LOWERED body (every write in this IR is a `Tree.Assign`, so the scan is complete); rendered
unconditionally it would put a private field on every promoted parameter in every port, which is a
JVM shape change for a defect that fires only at the assignment.

## 4.56 A rename decides OWNERSHIP structurally, never by name

Any pass that rewrites a name by prefix — package rename above all — must first answer *does this
program declare this symbol?*, and must answer it from STRUCTURE, not from the string. A prefix
match alone rewrites the standard library: `Map("java" -> "j")` turns `java.lang.String` into
`j.lang.String` and every honest map that happens to share a prefix does the same, silently, with a
green compile.

The TIR answers it: the frontend interns an external symbol lazily with `owner = SymId.None` and no
`Definition`, while everything the program declares hangs off a top-level unit through the `owner`
chain. So **a symbol is owned iff climbing its owners reaches a `program.units` symbol** — stronger
than "has a definition", which anonymous-class symbols do not.

**And an unowned symbol's SIGNATURE is a fact about a class file, so no phase may move it.**
`StandardTraversal.mapSymbols` skips what the program does not own, for the same reason a rename
must: whatever the port does to itself, that method still takes and returns what it was compiled to.
Mapped, the table would say the port's own type on BOTH sides of every external seam, and the check
written to find that seam would report zero (`ENGINE-LIMITS.md` K15).

Two corollaries for any prefix rule:

- **Cut only at a separator.** `Symbol.fullName` uses three: `.` between packages and the top-level
  type, `$` before a nested type, `#` before a member. `com.foo` must not cover `com.foobar`, and
  everything after the cut is carried across verbatim or nested-type paths break — at EMISSION,
  not at the rename.
- **A namespace rename runs LAST.** Every other phase's policy (`ClassTableTransform` redirects,
  `StaticForwarderTransform` wrappers, `Substitutions` drops, opaque-type hints) is written in the
  UPSTREAM namespace. Rename first and all of them match nothing — a phase that does nothing, with
  no error. `runsAfter` cannot say "after everything"; `Pipeline.order` is stable in declaration
  order, so the porting program places it last and `PackageRenameTransform.check` verifies it.

- **An artifact that joins POLICY to OBSERVED code carries BOTH names.** The corollary of the point
  above, and it fails silently in the other direction: policy is upstream, but a stack frame, a
  compiler path and a class name are all EMITTED, so any file joining the two is comparing two
  namespaces. `dropped-types.tsv` held only the manifest FQN, and the derived expected-failure rule
  it feeds had therefore **never once fired on a renaming port** — libGDX's four deliberate
  `JsonTest` failures were reported as unexpected on every run since the rule was written, with the
  claim that they were classified living only in prose. The run is the last place that holds the
  manifest name and the rename map together, so the run writes both (`Correlate.Dropped`) and
  nothing downstream re-derives a namespace it cannot see. Translate with
  `PackageRenameTransform.renamed` — the phase's own rule — never a hand-written `startsWith`.

- **Match a runtime class name at a SEPARATOR too.** The same cut applies on the reading side: a
  frame in `p.Json` may arrive as `p.Json`, `p.Json$`, `p.Json$Ref` or `p.Json$$anonfun$3`, and
  `p.JsonTest` is none of them. And do not match through the source map: a DROPPED type is the one
  type the port does not emit, so it has no `srcmap` entry at all — its replacement is injected
  Scala the emitter never saw. The class name in the frame is the only place it appears.

**And it is not only renames.** A second phase has now been bitten by the same string test, which is
what makes this a rule about DECIDING FROM A NAME rather than a rule about renaming.
`CollectionsTransform` decided "this cast can never succeed" from the cast's SOURCE type having a
`java.` prefix — and `java.lang.Object` has one. `(Collection<V>) anObject` is an ordinary downcast
that the phase does not touch on the source side, so at run time the value IS a shim and the cast
succeeds; deleting it turned a correct program into a wrong one, at three sites in libGDX's `Json`
alone. A prefix is not a structural fact about anything.

The general form: **a phase may only conclude something about a type from what the PHASE ITSELF did
to that type.** Every retyping phase already has that record — `CollectionsTransform` has `typeMap`
and the `remap`/`kindOf` tables built from it — so the question "did I move this type out of the
family?" is a lookup, and "is this type one of mine?" is a membership test. A type the phase does
NOT retype is by definition still whatever it was, and the phase has no standing to reason about it.
Note this failure is invisible to every count: the port still compiled and every check reported the
same number, because the members it broke were inside a type the library drops.

**And the same rule governs a phase's own FAST-PATH GUARD, where it is even harder to see.** A
"nothing to do here" test written when a pass had ONE target keeps passing for that target and
starts answering for every target added since. `CollectionsTransform`'s argument bridge opened with
`if javaIterableSym == SymId.None then t` — a fact about `java.lang.Iterable` — while the bridge
it guards also serves `java.util.Collection`; so a library that uses `Collection` throughout and
never names `Iterable` had the **whole pass inert**. So a guard is derived from ALL of the pass's
own targets or it is not written — the work each target would do already declines cheaply on its
own.

**And the rule reaches an INSTRUMENT'S OWN FILTER, in whatever language it is written.** The second
occurrence was not a phase at all: `just catalog-coverage`'s aggregation matched
`^(lowering|phase):` — the two obligation discharge surfaces that existed the day it was written —
so when a THIRD was built its twenty rows were neither counted as mechanised nor eligible to be
REPORTED as never reached, and the recipe went on printing a confident total. That is the recipe an
agent runs *before claiming a rule is live*, which makes a stale filter there worse than one in a
phase: a phase's silence is a missing fix, an instrument's silence is a wrong answer to the question
"is this branch dead". State such a test as the COMPLEMENT — everything that is not one of the
honest negatives is a surface — so a kind added tomorrow is included by construction rather than by
somebody remembering a list.

**And the SURVIVORS are not the DECLARATION, which is the same mistake read off the parse instead of
off a string.** A rule may conclude something from what the program DECLARES; it may not conclude it
from what this run happened to PARSE, and the two look identical whenever the port converts a whole
library. `TirEmitter.sealOf` reconstructed java's `permits` set from the extends-edges it could see,
so a permitted subtype in an `excludeGlobs` file — or in a unit whose translation was refused — was
simply not in the set, and a hierarchy whose remaining subclasses all land in one file read as *the
seal is exact* and shipped `sealed`. Whatever supplies the missing FQN then cannot extend a type java
said it could: an injected shim, or §4.45's consumer. **A WRONGFUL SEAL IS INVISIBLE TO EVERY
INSTRUMENT** — only the widening is recorded, so a decision NOT taken has no row anywhere, no member
digest moves, and the corpus has no sealed type to notice with. The general form: where java WROTE
the set down, carry it (interned, never as names — §4.56's own rule, and the permits clause and the
emitted FQN are two namespaces on a renaming port), and let anything the parse cannot account for
take the conservative arm. A count of the survivors answers a different question than the one the
construct asked.

**And what makes it hard to see is a RESIDUE COUNT that cannot tell a refusal from a switched-off
fix.** The boundary check reported all five seams, precisely, on every run since that port began —
and a "no wrap was inserted" finding reads as *no wrap exists for this pair*, which is what the
honest refusals in that same count are. Here the phase's own factory table answered the pair on its
first line. **A residue count is only as good as the assumption that everything able to close it
RAN**, and the phase is the one place that can check it: a reported boundary whose (source kind,
target) pair HAS a factory is an engine bug, not a residue, and the phase holds both halves at the
moment it files the finding. `members.tsv` cannot help either — the output has been that way since
the port began. (`ENGINE-LIMITS.md` K2.5: 3 errors, and 5 findings that had been misreading
themselves for the life of the port.)

## 4.57 Every emission backend carries PROVENANCE — it is a licence obligation

Each library in reach of this engine is licensed (Apache-2.0 so far) and every port is a derived
work, so every generated file ships an attribution header. Nothing in the pipeline reports a
missing one — the output compiles perfectly without it — so a new backend loses the header silently,
which is exactly how the TIR path regressed a feature the BIR path had.

Take the source path from the unit's `Origin`, never from its FQN: a renamed or nested type does not
live where its FQN suggests, and after a package rename the FQN is not the upstream one at all.
Where the origin cannot be relativised, say so in the header — a wrong-but-plausible path defeats
the only purpose the line has.

**And a BANNER IS NOT ALWAYS THE NOTICE.** Every port so far was Apache-2.0, whose sources carry the
notice in every file — so reproducing each file's comment (§4.58) and stamping the banner met the
obligation *by construction*, and nothing in the pipeline had to know that. That is an accident of
how Apache-2.0 projects write their sources, and the first non-Apache library in the corpus broke it:
an MIT library commonly carries **zero** per-file headers, so the only home of the copyright and
permission notice is the upstream `LICENSE`, and MIT's one condition is that this notice be INCLUDED
in copies. A banner reading `Original license: MIT (see <lib> upstream)` NAMES a licence; naming is
not including, and no check can tell the difference — the port compiles, every count is flat, and the
port ships nothing.

So a port DECLARES the upstream files that carry its notice (`Provenance.notices`, `notices = […]`
in a `.conf`) and the run copies them beside the emitted code. Four things that are not incidental:

- **it is per-library POLICY, and a §1(b) parameter** — which file carries the notice cannot be
  derived (`LICENSE`, `NOTICE`, both, neither) and the upstream ROOT is not the source root the port
  parses. Empty is the default and the no-op, so an Apache-2.0 port states nothing and nothing is
  written;
- **the destination is `src_managed/`, the BUILD PRODUCT** — not the port root, where an untracked
  file blurs the decision-vs-artefact distinction §5.5 depends on. The notice belongs to the derived
  work, and the derived work is regenerated by every run;
- **it is NOT gated on the artifact layer.** A licence obligation that held only when a diagnostic
  switch was on would be met by accident; what keeps the write scoped is the empty default and the
  destination, not a flag;
- **a declared file that is not there is FATAL** — same rule as a missing `classpathFile`. A notice
  the port meant to ship and silently did not looks exactly like one it shipped.

## 4.575 A PORTER NOTE puts the decision where the question is asked

Every non-mechanical thing the port did is recorded in `decisions.tsv` with its §1 classification.
That artifact answers an agent that holds the run directory. The agent this engine actually has is
reading ONE emitted file in another repository (§4.45), and its question is asked at a line of
Scala: *why is this field called `style$shadow`, why is this method simply absent, why does this
file live in a package the upstream never had.* A record in a sibling TSV cannot be found from
there.

So the same fact is also emitted BESIDE the code, in one grammar:

```
/* porter: <kind-slug> k=v … — <free text> */
/* porter: renamed-member reason=universal rule=member-rename(§4.55) clash=field-vs-method from=align to=align$field */
```

`<kind-slug>` is `Decision.Kind` in kebab case — the enum, never a string a decider chose. The pairs
carry the §1 classification first (`reason=universal|configured|library-rule`, then `rule=` or
`phase=`+`key=`), because which repository the fix lives in is the reader's first question; then the
decision's own detail, sorted; then `why` as free text after an em dash. A value containing
whitespace is QUOTED, or the whitespace-separated pair list silently truncates it. `grep -rn '/\*
porter:' src_managed` is the complete inventory of non-mechanical translation in a port.

Three rules that are not style:

- **Notes are DERIVED, never authored.** `TirEmitter` renders only decisions whose subject it is
  emitting; nothing constructs a note from a local condition. A note invented at the emitter is
  policy that reads as authoritative, and `NoteCoverageCheck` fails the run for it — in both
  directions: a decision about an emitted subject with no note, and a note with no decision behind
  it. Neither is visible to a compile, to any other count, or to a test.
- **Original trivia FIRST, note LAST, member next.** The upstream comment is what a licence obliges
  the port to reproduce (§4.58); a note above it reads as part of it and displaces it.
- **A note may never open or close a comment.** Scala block comments nest (§4.58), so every
  rendered value goes through `PorterNote.safe`.

Where a kind's note goes is machinery, not taste: `PorterNote.AtDeclaration` (above the `def`/`val`/
nested `class`), `PorterNote.InBody` (a dropped member has no declaration to sit above, so its note
heads the owning type's body), `PorterNote.NotInTree` (a dropped TYPE's note is carried by the
INJECTED file that supplies its FQN, prepended at copy time). A kind in the wrong set is a note that
never appears.

Two consequences to keep in mind when adding one: a note is emitted text, so it moves member
digests (§5.1) — expect the blast and account for it; and a check that searches emitted text for a
string must strip notes first, because a note names the UPSTREAM FQN on purpose
(`SubstitutionCheck.dangling` reported 3 phantom dangling drops the first time notes shipped).

## 4.58 COMMENTS are part of the port — and only a TEXT-to-TEXT check can see them

The upstream licence lives in a comment, and §4.57's generated banner does not replace it: the
banner says what the file is, the notice is the thing the licence obliges a derived work to
reproduce. Everything below follows from taking that seriously.

**Slice VERBATIM from the source buffer; never re-print.** A parser's `toString` for a comment
reflows the body, normalises the ` * ` gutter and loses the alignment of a `<pre>` block — fine for
prose, not for a legal notice. `SpoonTir.triviaOf` cuts the comment out of the original text by its
source positions. Note an in-memory compilation unit may have NO buffer (Spoon's
`getOriginalSourceCode` returns null for a `VirtualFile`), so the convenience parse path has to be
handed the text it was given, or it is silently the one path that does not preserve anything.

**…and THE ABSENT BUFFER REACHES FURTHER THAN THE HARVEST, which is what makes it a rule.** A
POSITION is derived from the same buffer: Spoon computes a COLUMN by scanning the compilation unit's
original source, so `getColumn` throws a `NullPointerException` on a unit that has none — and
`isValidPosition` answers TRUE there, because the position itself is perfectly good. The whole
translation then dies with no origin, no construct name and nothing to classify it by, which is the
one failure shape this frontend must not have (§4.45). Guard the buffer, not the position, and
report the origin without the part that needs it: every reader of an `Origin` keys on the FILE and
the LINE — `srcmap.tsv`, `errors.tsv`, a finding's location, the correlator — so a missing column
costs decoration and inventing one would be §4.6's fabricated fact. `SpoonTir.columnOf` is that
guard; it changes no port's output, because every corpus source is a real file with a buffer, and it
is a crash only an in-memory parse can reach — which is exactly what §4.45's agent and every
testkit fixture are.

**One comment, one home: keep a CLAIMED identity set.** Harvesting is layered — a declaration takes
its own, and a statement then scoops whatever expression-level comments its subtree still has (the
TIR has no node for those). A coarse harvest must therefore run AFTER its children have translated
and must skip what they took, by IDENTITY, or every nested comment is emitted twice. The one
deliberate exception is the FILE header: a Java file with two top-level types becomes two Scala
files and each is a derived work, so each carries the notice.

**…and a harvest that reads TEXT claims a SPAN, before the ones that read the parser.** The file
header is decided positionally — a comment is the file's iff no code precedes it — precisely because
a parser's attachment model is what cannot be trusted there (`ENGINE-LIMITS.md` V3: of two leading
blocks the unit gets the first and the PACKAGE DECLARATION the second, and in one generated-parser
family the block that fell down that gap was the Apache notice itself). A positional harvest has no
parser object to claim, so it claims the OFFSET, and every finer harvest skips what it took — which
only works if it runs FIRST. Reading one more of the parser's slots is not the fix: the next shape
lands in a slot nobody enumerated, and no set of slots can say which of two blocks came first.

**A comment the emission CONSUMES needs a home, and the last resort is a QUOTATION.** Where a
construct disappears — a promoted constructor's braces, a `@Test` method that becomes a
`test("…"){…}` statement, a `for` header rendered on one line — its comments go with it unless
something carries them. Give each category its honest home first: the TIR node that survives has a
`leading` (or, for the end of a body, `Tree.Block.trailing`), and that placement is EXACT. What
cannot be placed is relocated to the member it was written in with its java coordinates beside it
(`/* trivia: recovered from <path>:<line> */`), which is what makes the relocation admissible — a
comment moved WITH its position is a quotation, while the same comment moved silently says something
false about the code below it. Count those separately from the ones that were placed: a recovery
lane that reads high is a category that still wants a home, and a `lost` count that hides it is a
number that stopped meaning anything.

**Two Java-vs-Scala comment facts, both of which break the emitted file, neither of which any type
check sees:**

| Java | naive Scala | what happens |
|---|---|---|
| `/* see the /* marker */` | same text | Scala block comments NEST; Java's do not. The emitted comment never closes and swallows the rest of the file. Emit such a comment line-by-line as `//` — every character survives and nothing can open. |
| a statement separator decided by `nextLine.startsWith("{")` | comment first, `{` second | a comment is WHITESPACE to the parser, so `new Array[String](n)` / `// note` / `{ … }` still parses the block as an anonymous-class body. Any such test must skip comments the way a scanner does (`TirEmitter.firstCode`). Measured: 0 → 2 errors on libGDX the first time trivia was emitted. |

**Indent is re-derived; text is not.** A comment is re-indented to the node it belongs to (its
internal relative alignment preserved) rather than reproduced at the column upstream used — a port
is regenerated on every engine change and a diff that moves because a comment re-wrapped is a diff
nobody reads. Emitted at its original column, a nested member's Javadoc reads as a comment on the
enclosing class.

**The check compares SOURCE TEXT to EMITTED TEXT — never the tree.** This is the part that is easy
to get wrong and expensive to leave wrong. Counting `Trivia` nodes proves the frontend harvested and
proves nothing about the emitter; and the frontend's own notion of "every comment" is the parser's
attachment model, which is exactly what may be incomplete. `TriviaCheck` re-lexes the Java
independently (`CommentScanner`) and looks for each comment's normalised body in what the run
actually WROTE, grouped by Java file. Nothing else in the pipeline can fail when this feature
regresses: the output compiles perfectly with every comment gone, no count moves, and no test
breaks. It regressed to exactly that during development — one null reaching a broad `catch` turned
the whole harvest into `Nil` — and the emitted Scala was valid.

**A `catch` around a harvest is how that happens.** Wrap only the lookups where an absent value is
NORMAL (a missing source buffer); let a harvest that throws be seen.

**And the NORMALISATION the check compares through is itself a source of false losses.** It strips
what the emitter is allowed to change — delimiters, the ` * ` gutter, indentation — and the ORDER it
strips them in decides whether the two sides meet. A block comment Scala would nest on is emitted as
`//` lines, so its javadoc opener arrives with a `//` in front of it; with the `//` taken off LAST
the opener was still there, the two sides normalised differently, and every such comment was
reported lost while sitting in the emitted file. Whatever the emitter may WRAP the text in comes off
first.

## 4.59 A construct the PARSER SYNTHESISES is not a construct the parser MODELS

Java DERIVES declarations nobody wrote — a record's backing field, its bare-name accessor, its
canonical constructor and its `equals`/`hashCode`/`toString`; an enum's `values()`; a class's default
constructor. A parser hands some of them over, and where it does, the arm reading them looks exactly
like the arm reading real java. It is not the same evidence, and the difference is invisible to every
instrument here: a synthesised member has no source position, no comment and no `srcmap` row, so no
diff moves, no count moves, and there is no upstream line to compare the emitted one against.

Measured on ONE construct, with THREE different wrong answers out of ONE parser:

| what java derives (JLS 8.10) | what the parser handed over |
|---|---|
| the canonical constructor's parameters, in the HEADER's order | the same parameters in the parser's own FIELD order — so every translated `new` transposes them, which is a compile error where the types differ and a silent swap where they do not |
| a COMPACT constructor's trailing field assignments | nothing. The class assigned no component at all, and every accessor answered the type's default, with a green compile |
| a NESTED record's constructor, and its accessors' bodies | no constructor at all, and an accessor whose field read does not resolve — which in scala's ONE namespace is the accessor calling ITSELF |

None of the three moves a compile-error count and two are silent at run time as well. So **derive
what java DERIVES, from the declaration java WROTE**, and read the parser's synthesis only where a
fixture has shown the two agree. Note what this is NOT: it is not "distrust the parser" — the same
parser's model of written java is what this whole frontend rests on. It is that an IMPLICIT member is
a second-class fact, and a fixture is the only thing that can promote it.

The corollary for the kind census: `SpoonKinds.Absence.AbsorbedSilently` is a suspicion about a
KIND, and this is a suspicion about a MEMBER. A kind can read as `Lowered` — an arm exists, it runs,
the emitted text has the right shape — while that arm faithfully translates three fabricated facts.

## 4.6 A kill switch beats another condition

When a synthesized construct is wrong, first establish **which code produces it** — do not add a
condition to the gate you suspect. Return the input unchanged at the top of that function, print on
entry, and re-emit: one run tells you whether the gate is even consulted. If it is not, tag every
construction site of that node kind and grep the trace for the source line. Three consecutive edits
to `uncheckedGeneric` measured no change at all before a kill switch showed, in one run, that the
cast came from the emitter.

**And a bare `catch` is a KILL SWITCH somebody left on.** The rule above is about finding which code
produces a construct; this is its twin, about finding which code produces an ANSWER. A
`try … catch { case _: Throwable => <default> }` around a parser lookup reads as defensive and is
not: the default is a value the rest of the computation cannot distinguish from a real result, so a
resolution failure becomes a statement about the program. `SpoonTir.formalArity` is the worked
example — it computed a type's declared arity inside `catch { case _: Throwable => 0 }` at five
sites, and arity ZERO is not "unknown", it is *this type takes no type arguments*, which is what the
emitter then wrote. A generic emitted un-applied, silently, with a green compile and no moved count.

So, before writing one: **name the ONE lookup where an absent value is normal, wrap only that, and
let everything else be seen.** Two questions settle it —

- *what does the default MEAN to the caller?* If it is indistinguishable from a real answer, it is
  not a fallback, it is a fabricated fact. Where no honest default exists, there is no honest catch;
- *how many callers share this one?* A `catch` copied to five sites is five different questions
  answered by one `getOrElse`. Make it one function, so narrowing it is one edit and so the next
  reader can see what it is FOR.

`CLAUDE.md` §4.58 already says this about a trivia harvest ("wrap only the lookups where an absent
value is NORMAL; let a harvest that throws be seen") — this is the same rule, and it is here because
the second occurrence is what makes it a rule rather than a note about comments.

`sbt -client` talks to a long-running server, so a shell environment variable never reaches the
forked migration. Gate the switch on a marker FILE.

**The kill switch is now a FLAG — do not edit source to get one.** `Pipeline.run` reads these, so
the question "is this phase even responsible" costs one run and no diff:

| flag | does |
|---|---|
| `balticporter.skipPhases=<name>,<name>` (or `*`) | omit those phases; the answer in one run |
| `balticporter.dumpTirBefore=<phase>` / `dumpTirAfter=<phase>` | print the TIR around a phase |
| `balticporter.dumpOnly=<fqn>` | narrow either dump to one type |
| `balticporter.tracePhases` | announce each phase as it runs |
| `balticporter.traceNode=<Kind>` | `TirTrace.mint` prints constructing frames for a node kind — no node gains a field |
| `balticporter.baseReports=<p1:p2>` | FALLBACK ONLY — extra directories to look for a base module's published `port-map.tsv` in |

`baseReports` is the one entry on that list that is not a debugger's: §4.45's agent has no
`port-report/` tree of this repository's shape, so its base's map arrives from wherever that base was
run. **It belongs to the PORT** (`PortManifest.baseReports`, beside the `bases` it is about; `baseReports = […]`
in a `.conf`), and where a port states one this flag is not consulted at all — a base's map decides
EMITTED TEXT, so which maps a run discovers is part of that run's identity and a leftover
`debug.properties` entry would make two checkouts at the same commit emit differently with every
count identical. `PortMap.searchPath` is the one place the two meet and it CHOOSES rather than
merges, because merging leaves that failure in place for every port that stated its own; `just
debug-flags` marks the flag as the fallback it is. Whatever the source, it EXTENDS the run's own
report root and never shadows it — first wins per module — and both readers take the one search path
(`PortMap.discoverIn`), because two discoveries of one artifact answering differently is the failure
the base-surface view exists to remove.

Resolution order, in INCREASING precedence, is **`<root>/.balticporter/run.properties`
(script-written) → `<root>/.balticporter/debug.properties` (hand-written, beats run.properties) → a
system property, which beats both** — `DebugFlags.get` reads `System.getProperty` first. Note the
marker file is not merely a convenience:
a `-D` on the *caller's* command line does not reach the forked migration either, because `sbt`
forks it with `javaOptions` from `build.sbt`. Only the file crosses that boundary.

**Reach all of it through `just`, not through a main class or a hand-written file.** Every recipe
below is proven by a spec or by `just debug-selfcheck`; the mains behind them are an implementation
detail, and an agent that has to know which one exists is an agent that re-invents the tool:

| recipe | |
|---|---|
| `just debug-flags [PORT]` | WHICH layer defines each flag right now, what it shadowed, what a run RECORDED (`report.md`), and which entries no accessor will ever read |
| `just debug-set KEY VALUE` / `just debug-clear [KEY]` | edit `debug.properties`, the winning layer — idempotent, and `debug-clear` with no key removes the file |
| `just debug-emit ROOT FQN [PHASES] [FLAGS…]` | model a Java tree once and print ONE type as TIR and as Scala, bracketing each named phase |
| `just correlate OUT …` | `CorrelateMain` on a compiler or test log you produced by hand (§5.1) |
| `just members-unchanged [PORT]` | the blast radius, before any compile — and FATAL on an input it cannot compare |

Two facts the resolution surface exists to make visible, because nothing else can: a key without the
`balticporter.` prefix is read by no accessor, and a misspelt one (`skipPhase`) is a flag that does
nothing — the run it was written for looks entirely normal. `just debug-flags` marks both.
**Clear a flag when you are done with it** (`just debug-clear`): a leftover one moves no count, fails
no check, and quietly changes what every later run in that checkout emits.

`TirPrinter` renders the TIR readably (`canonical` style leaks no `SymId` and no origin, so two
runs are comparable); `DebugEmit` (`balticporter.runner`, in the ENGINE so a consumer's agent has it
too) models once and emits one FQN, optionally around a phase. What it prints is the pipeline's view
of one type, never a reproduction of a port's emitted file — there is no substitution, injection,
package rename or provenance header in it; that is `PortRun`'s job, and giving this tool a port
`.conf` would make it a second assembly path free to drift from the first.

**A flag that carries measurement identity must come from the PORT, not the operator.**
`balticporter.reportPathRoot` anchors the paths a finding's stable id is hashed from. Set only by
the measure lanes, it silently falls back when the migration is run directly — and every finding
then diffs as removed-and-re-added against a baseline whose *counts are identical*. A baseline that
reproduces only through one `just` recipe is not a baseline. Derive such a value from the port's own
configuration.

## 5.4 Compare paths through `toRealPath`, on BOTH sides — always

Three independent parts of the engine have now been bitten by the same thing, which is what makes
it a rule rather than a note. Every path this project compares has two forms: the one an operator or a
config *wrote*, and the one the parser *recorded*. They differ whenever a symlink is in play, and a
symlink is in play in the normal case — **a git worktree reaches its sibling checkouts through
one**, and `.claude/worktrees/<x>/../sge` is a link to the real `sge`.

A lexical `normalize` keeps the link; `Files.walk` follows it. So `startsWith` between the two
matches nothing, silently, and the code around it looks correct:

- `PortRun.converted` decides which units a run OWNS by "under `sourceRoot`, not under a
  `resolutionRoot`". Compared lexically it classified every unit as owned and wrote **635 files
  instead of 30** — the whole resolution root re-emitted into the test source set.
- `CheckReport.relativise` hit it as a diff that was deterministic but *different* from the
  baseline computed in the primary checkout — a stack of `..` segments that depends on where the
  link lives.
- `TirEmitter.sourcePathOf` compared the parser-recorded origin against `Provenance.sourceRoot`
  lexically, so in a worktree the root-relative case silently failed and the marker cut took over —
  emitting `gdx-vfx/gdx-vfx/core/…` there against `gdx-vfx/core/…` in the primary, at the same
  commit. Every whole-file digest a worktree-accepted baseline carried was one the primary could
  not reproduce: 44 vfx members plus 6 noise4j members "changed" with zero code changed.

So: realpath both operands before comparing, and fall back to `normalize` only when the path does
not exist (a synthetic origin, a directory not yet created). Note the failure is invisible to a
compile and to every count except the one it inflates — the port still compiled, and the tests
still passed, on 635 files.

## 5.5 Emitted code is a BUILD PRODUCT — `src_managed/`, never `src/`

Every port writes its generated Scala to `<port>/src_managed/{main,test}/scala`, which is
gitignored and deleted by `sbt clean`. `src/` holds only the hand-written part of a port — the
shims and overrides a library needs and the engine cannot derive.

This is the layout a target project is meant to use, so the engine must produce it, not
approximate it. `SbtGen.managedMain` / `managedTest` give the paths; `SbtGen.emit` writes the
`.gitignore` and the `sourceGenerators` + `cleanFiles` settings that make sbt see the directory
and `clean` remove it. Never hardcode an output path in a corpus migrator.

The reason is diagnostic, not tidiness: emitted code is reproducible from the Java sources plus
the manifest and is invalidated by every engine change. Committed alongside `src/`, a `git status`
can no longer distinguish a DECISION from an ARTEFACT — which is the single thing this project's
measurement discipline (§5) depends on being able to see.

## 6. Scala 3 output constraints

- **Never cast to `scala.Nothing`.**
- Vararg spread is `args*`, never `: _*`.
- Emit **fully-qualified names, no imports**, for the structural phase — this deletes the whole
  import-decision bug class. Human-readable imports are a separate, optional beautification backend.
