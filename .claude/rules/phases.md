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
