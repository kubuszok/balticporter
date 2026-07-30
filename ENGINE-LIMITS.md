# Engine limits — the measured dead ends, for the NEXT library

**Read this when the first wall of typer errors appears, not at the end.**

You are probably an agent porting a Java library in a repository that has never seen libGDX. Every
entry below was paid for once, on libGDX, in this repository — and most of them cost a whole session
of reasoning from first principles before the measurement settled it. They are facts about **Java,
Scala 3, Spoon under `noClasspath`, dotty, or Baltic Porter's own architecture**, so they will be
true of your library too. Re-deriving one is waste, and the record exists so you do not have to.

## How to read an entry

- **A number is evidence, not decoration.** `13 → 28` means someone built the change, measured it,
  and it was worse. `+277` means catastrophically worse. `inert` means the code was built, ran, and
  changed nothing — which is usually the *most* informative outcome, because it proves the gate you
  suspected is not the one responsible.
- **"Do NOT retry" means do not retry as stated.** Where an attempt is worth re-opening from a
  different direction, the entry says so and says what has to be answered first.
- **Every entry names the kind of fix it would need**, per `CLAUDE.md` §1:
  - **(a) engine bug / engine gap** — the engine is wrong or incomplete for every library. Fix it in
    `core` / `frontend-spoon` / `scala-emit`, unparameterised.
  - **(b) configure an existing phase** — the mechanism exists; supply your library's values.
  - **(c) write a library-specific rule** — plug a rule into your own migration program. It does not
    go in the engine.
  Classifying an error is the expensive part of a new library's first wall (`CLAUDE.md` §4.45); an
  entry that already tells you the kind has done most of the work.
- **Worked examples name libGDX constructs.** That is deliberate and permitted (`CLAUDE.md` §1): the
  example documents *why* a general rule exists. It drives nothing. Substitute your own library's
  shape freely.
- **The measurements live in `LIBGDX-PORT-STATUS.md`.** This file holds the rule; that file holds
  the per-site diagnosis, the trajectory and the counts in context.

---

## 0. The root cause behind most of these

**The engine's recorded type is not a reliable witness of what the emitted Scala will have or
infer.**

This one sentence explains more failed attempts below than any other. It has three faces:

- A node's `tpe` in the TIR can disagree with the Scala the emitter prints for it. Every later rule
  that consults `t.tpe` then reasons about a type the output does not have. (`fieldAccess` cast a
  wildcard receiver to its erased view and kept Spoon's un-erased type on the `Select`, so the TIR
  said `data.type : Class[T]` while the emitted Scala had `Class[Object]`; `erasedFieldReceiver` now
  returns the receiver view and the field's type through it **together** — producing one without the
  other *is* the inconsistency.)
- **Two renderings of one Java type in one class** is this engine's most persistent defect shape. A
  declaration renders in one scope; a use of it re-renders Spoon's type in another scope and gets
  something else. Whenever a fix "moves the failing column" rather than closing it, suspect this.
- A **frontend-recorded** `Symbol.info` is a *pre-transform* rendering. `CollectionsTransform.run`
  retypes symbol signatures after the frontend records them (`StandardTraversal.mapSymbols`), so
  consulting `Minter.infoOf` late is not "the honest source" — it is a **third** rendering.
  Measured: coercing an assignment to the field symbol's recorded `info` went **1 → 3**.

The type the emitter will finally print is only knowable **after all transforms have run**. A check
that needs it belongs in a late pass over the TIR — where `RewriteTrace.check` already verifies that
call sites agree with declarations.

*Fix kind: (a).*

---

## 1. Generics, raw types and wildcards

This is where a new library's first wall mostly is.

### G1. Erase USES (casts), never DECLARATIONS — **+277 errors**

Rendering a raw declaration as `Object`-parameterised instead of wildcard measured **+277 errors**.
`Array[?]` accepts an `Array[String]`; `Array[Object]` does not. A declaration widened to make one
use type-check breaks every other use.

*Fix kind: (a). This is a hard invariant, not a tuning knob.*

### G2. A raw generic renders `[?]`, everywhere — and `?` DOES round-trip across an override

The design space was measured in full. Two knobs: whether a name-keyed inherited fill runs, and what
an un-nameable raw type argument falls back to.

| inherited fill | fallback | errors |
|---|---|---|
| off | `?` | 162 |
| off | `Object` | 97 |
| on | `Object` | 87 |
| on | `?` | 1 |

`Object` is a NAMED type and `?` is a fresh existential per occurrence, so "overrides fail because
two `?`s do not conform" is a reasonable hypothesis — and it is **WRONG in general**: `Object` is
uniformly worse in both columns. **Wildcards round-trip across overrides in the overwhelming
majority of cases.**

The apparent counter-evidence (110 × E164 when the inherited fill is disabled) is an artefact of
disabling only the CHILD half: the parent stayed at `[T]` from the ordinary name-directed fill.
**Both halves must move together.**

Confirmed against the reference hand-port: in sge, parent, every override and the field are all
`AssetDescriptor[?]` (`AssetLoader.getDependencies`, four loader overrides,
`AssetLoadingTask.dependencies`). Filling the element with the loader's own `T` is not merely
different, it is **semantically wrong** — a `BitmapFont`'s dependencies are a `TextureAtlas` and a
`Texture`. Java wrote the element raw *because it is heterogeneous*.

Adopting the correct rendering cost **1 → 11** deliberately, then settled at a small residue of
individual sites — not a wall.

*Fix kind: (a).*

### G3. A class must see its INHERITED INSTANTIATION — 162 → 7, and the guard that cannot work

An override re-renders the parent's raw type with no type variable in scope and disagrees with the
parent. `instantiationOfParents` maps each ancestor's formal NAMES to what the class instantiated
them as. Each refinement was measured and each is load-bearing:

| refinement | measured |
|---|---|
| the fill itself | 162 → 45 |
| skip a parent arg naming an out-of-scope var | 45 → 36 |
| suppress it at a RAW `new` — there the ARGUMENTS decide the parameter | 36 → 7 |
| require the candidate to satisfy the formal's BOUND (name match ≠ evidence) | 7 → 6 |
| suppress the fill for whole method BODIES instead | 36 → **59** — bodies genuinely need it |
| suppress it for LOCAL declarations only | 6 → **17** |

**The failure mode is name collision**: two unrelated generics that both call their parameter `T`.
(`AssetLoadingTask implements AsyncTask<Void>` puts `T → Void` in the map; the raw field
`Array<AssetDescriptor>` then renders `Array[AssetDescriptor[Void]]`.)

**Do NOT retry filtering the MAP.** Four guards measured:

| guard | measured |
|---|---|
| ancestor must MENTION the type being filled | 4 → **161** |
| …transitively through its own supertypes | 4 → **161** |
| SUPERCLASS chain only, no interfaces | 4 → **142** |
| reject `java.lang.Void` as an uninhabited candidate | 4 → **141** |

Instrumented, so do not re-derive: the transitive `mentionedIn` scan **works** — it finds
`getDependencies → Array<AssetDescriptor>` exactly as intended. The test was never buggy; the
**theory** was. Requiring positive evidence rejects the many good name matches the 162 → 4 win
depends on, because the fill succeeds broadly *because* it is name-keyed and only two sites collide.
And the bad entry is genuinely needed elsewhere — `AssetLoadingTask.call()` really does return
`Void`.

**The answer is not a filter on the map: the fill is an obligation of OVERRIDING MEMBERS, not of the
class** (measured 4 → 3, gated by `inOverridingMember` off the same `overrides` flag `execDef`
computes). It exists to make an inherited member agree with the one it overrides; a member the class
declares for itself carries no such obligation. **The obligation is a property of the SITE.**

*Fix kind: (a).*

### G4. A name-keyed fill's success is a property of the CORPUS's naming — re-test it

The unrestricted name-directed fill beat the principled nested-only fill on libGDX:

| configuration | errors |
|---|---|
| nested-only fill, inherited fill ON | 14 |
| nested-only fill, inherited fill OFF (the coherent pairing) | 19 |
| unrestricted fill + inherited fill | 1 |

libGDX names its asset type `T` consistently enough that the "wrong" fill agrees on both sides of
nearly every override. **A codebase with less uniform naming would invert this result.** If your
library's counts do not look like these, that is the expected outcome, not a regression to chase.

*Fix kind: (a) if it inverts — the rule moves toward the principled fill as the corpus grows
(`CLAUDE.md` §2).*

### G5. An override's return type must NOT be rendered from the parent's declaration — 162 → **438**

The diagnosis is right (110 × E164 are `Array[AssetDescriptor[?]]` vs `Array[AssetDescriptor[?]]` —
two INDEPENDENT captures produced by rendering each side separately) and the repair is not. The
parent's type reference names the PARENT's type variables, which do not exist in the subclass's
scope, so rendering it there is worse than the mismatch it fixes.

What is wanted is the parent's **already-rendered** result with the parent's formals substituted by
the subclass's actual arguments — the transitive parent substitution `CtorFunnel.parentTypeSubst`
already computes for constructor replays, applied to member signatures.

*Fix kind: (a).*

### G6. A de-wildcarded raw PARENT and its overrides must agree

Scala forbids `extends Configurable[?]`, so the emitter must pick a concrete argument for a raw
parent (it picks `Object`) — while the implementing members were rendered `[?]` by the raw fill. Two
renderings of one raw type in one class again; 8 classes, `needs to be abstract`.

**The type argument the EMITTER chose for a raw parent must be the fill for that variable in every
member of the class that overrides one from that parent.** Keyed off the emitted parent it cannot
disagree with itself, which is what a name-directed `inheritedInst` rule could not guarantee.

Note that the reference hand-port solved this by a **shape change** — `trait Configurable` with no
type parameter at all — which is per-library surgery and not available to the engine.

*Fix kind: (a) for the engine; a shape change would be (c).*

### G7. Wildcards in an `extends` clause take the parameter's DECLARED bound, not `AnyRef`

Resolved left to right, so a later bound can name an earlier parameter (`T <: ParticleBatch[D]`).
Super-constructor arguments get the same elimination as a cast: the parent head and the parameter
beside it are one raw type read in two positions.

*Fix kind: (a).*

### G8. A partially-nameable F-BOUNDED class has no consistent fill — a genuine expressiveness limit

Four ways of closing the last core gap, all measured, all worse:

| attempt | measured |
|---|---|
| prefer the DECLARATION's type for every variable access | 2 → 3 |
| …only when the reference is RAW and the declaration is not | 2 → 3 |
| let `subst` reach inside applied formals unconditionally | 1 → 11 |
| …only on the name-filled path | 1 → 11 |
| …plus wildcarding un-nameable formals on that path | 1 → 10 |

On the name-filled path an F-bound refers to its SIBLING formals, so a formal that cannot be named
here poisons the others — filling `A` with `Actor` while `N`'s bound still reads `Node[N, V, A]`
makes `N` fail its own bound. **Either every formal comes from the enclosing scope or none can.**

This is an expressiveness limit, not a missing case. The honest output is "this construct has no
Scala image, here is what a hand-porter would write" — the strongest existing candidate for
`UNPORTABLE-DESIGN.md`'s marker rather than another gate.

*Fix kind: (a), and the (a) is "report it as unportable", not "translate it".*

### G9. Scala CHECKS an F-bound where javac does not

`erasureOfFormal` erasing an F-bounded variable to `Object` is what javac does — and it produced 43
× E057 (`Node[Object, Object, Actor]` where `Object` cannot satisfy `N <: Node[N,V,A]`). The erased
view needs an **F-bound-aware erasure**: erase `N` to its own bound with the recursion cut, not to
`Object`.

These 43 were latent for the project's entire history and only appeared once the typer went green —
see M1.

*Fix kind: (a).*

### G10. A RAW anonymous class has no faithful Scala image — REFUSED, not approximated

Java's `new ReadOnlySerializer() { … }` (raw, with a body). Without a body Scala infers the type
argument from the expected type; **with** one, the anonymous class's type is fixed and a raw use
gives `ReadOnlySerializer[Nothing]`. Naming the argument does not help either: the body declares
`Object read(…)`, written against the erasure, so it only overrides under the `Object`
instantiation — and `Serializer[Object]` still does not conform to `Serializer[TintedDrawable]`.

Java accepts it as an unchecked conversion. Expressing that needs an argument-position cast whose
target mentions the **callee's** type variable, which is itself a measured dead end (G12).

Left **refused and reported** rather than approximated. Expect the same in your library wherever a
raw anonymous class has a body.

*Fix kind: (a) — and the correct (a) today is a refusal with a report, not a translation.*

### G11. Erasing a RECEIVER to its erased view LOSES members — 7 → **41**

Broadening `erasedReceiverView` to fire on a RENDERED wildcard, and to consider the callee's RESULT
type: 7 → 41, of which 21 × E008 `not a member`. `Array[Object]` has no library-specific members the
code then calls. The erased view is **only** safe where the capture is genuinely unusable, and
Spoon's own actuals are the right signal for that.

The context-dependent-fill problem this was aimed at needs the FIELD's declared rendering at the
READ, not a wider receiver cast.

*Fix kind: (a).*

### G12. A callee's own type variables do not resolve at the call site

Argument-position raw→parameterized via `coerce` is wrong for this reason: an executable reference's
formal can name the CALLEE's type variables, which have no meaning at the call site — the emitted
cast would render a `?T` stub. This is why `uncheckedGeneric` declines those arguments.

The working shape is `appliedCtorArgs`: at a `new C<targs>(args)`, coerce each argument to the
formal **with C's own parameters replaced by the explicit type ARGUMENTS**. `rawCtorArgs` is the raw
counterpart.

*Fix kind: (a).*

### G13. `rawCtorArgs` erased-formal fallback — THREE gates, all worse; and what each taught

| gate | measured |
|---|---|
| none — cast every formal mentioning a class type variable | 2 → **23** |
| + skip when the argument already shares the target's head constructor | 1 → **5** |
| + require a SIBLING argument to pin the instantiation to its erasure | 1 → **43** (E057) |

Keep these even though the problem was later solved by **inverting the direction**
(`rawCtorSpecialisation` casts the ERASED argument UP to the binding a precise sibling implies,
instead of casting the precise argument DOWN to the erasure) — because the wrong direction is the
intuitive one and will be tried again.

What each gate taught, so the next attempt starts further along:

- **The head-constructor gate is correct and necessary.** Casting `Array[Foo] → Array[Object]` or
  `Class[Foo] → Class[Object]` widens nothing; it erases the argument's OWN type argument and loses
  the members the code then calls. Same failure as G1 and G11 in a new place.
- **"Pinned by a sibling" is the right IDEA but cannot be decided from recorded types.** A class
  literal must not count as evidence — **Spoon types `Texture.class` as raw `Class`**, so its
  recorded type collapses to the erasure and every loader looks pinned. Excluding class literals BY
  NAME then admits ordinary field reads and the count goes to 43.
- **The engine's recorded type is not a reliable witness of what the emitted Scala will infer** (§0).
  This site is a good first customer for the unportable marker: there is a defensible argument that
  no faithful Scala exists, since the Java is exploiting raw-type unsoundness.

*Fix kind: (a).*

### G14. Under `noClasspath`, a REFERENCE erases and a DECLARATION does not

Three separate measured failures, one cause:

| attempt | measured |
|---|---|
| `typeParamToObject` consulting the REFERENCE formal when the declaration is unavailable | 13 → **28** |
| disabling array covariance for a generic array formal (`Arrays.copyOf(T[], int)`) | 13 → **28** |
| …in the "result shares the argument's type variable" form | 10 → **26** |
| unconditional bound-erasing of a callee formal | **+47** |

A reference erases a generic `T` to `Object`, so consulting it casts your own `foo(x: T)` arguments
to `Object`. Conversely a **JDK shadow** reports `T[]` while the real Scala-visible signature is
`Object[]` — so the erasure cast is right there, and what was wrong was the RESULT type recorded
(see `erasedResult`). And Spoon types `X.class` as raw `Class` while the engine emits a precise
`classOf[X]`.

**Drive a synthesized cast from the DECLARATION's formal, never the reference's.** Check type-variable
identity against the id its declaring type minted (`<owner>$$T`), never by name, so a callee's `<T>`
can never bind to an unrelated in-scope one.

*Fix kind: (a).*

### G15. Gate synthesized casts on the BARRIER-AWARE frame, not on name resolution — +2

Gating the name-directed fill on `resolveTypeParam` instead of the accessible-frame check measured
+2 `Not found: type T`. `resolveTypeParam` sees every enclosing scope by name; a **`static` nested
class cannot actually name the outer class's type parameters.** Gate on `accessibleTp`
(`SpoonTir.tpAccessibleHere`).

*Fix kind: (a).*

### G16. Casting a type-parameter argument to `Object` when the resolved formal is PRIMITIVE — inert

The reasoning is sound (a type variable can never denote a primitive, so Spoon mis-resolved the
overload) but the rule **fires nowhere**. `CharArray:718` has a different cause. Recorded so the
sound-looking argument is not rebuilt.

*Fix kind: (a) — but not this one.*

### G17. `selfRawFormalArgs` and `rawToParameterized` — already covered, do not re-add

- **`selfRawFormalArgs`** (raw arg into a self-typed formal, `Cell.set(Cell)`): **0 change**.
  `uncheckedGeneric` already emits that cast; the rule only appends a second, identical
  `asInstanceOf`.
- **`rawToParameterized` in `coerce`**, including the "keyed on the Java raw type" refinement:
  already present and **strictly more general** as `SpoonTir.uncheckedGeneric` (`rawTarget` /
  `ownScope` flags).

*Fix kind: (a), already done.*

### G18. An inner class of an ANCESTOR is in scope by simple name

Not merely verbose to project — **illegal**. A type nested in an ancestor is an inherited member
type (`ObjectMap.Entries` used from `OrderedMap[K,V]`; `TextArea` exporting
`TextField.TextFieldClickListener`). A nested-type path also picks its separator **per level**.

*Fix kind: (a).*

### G19. An override's TYPE-PARAMETER BOUNDS must follow the PARENT — four measured dead ends

Java's `<T>` **means** `<T extends Object>`, so the engine renders `[T <: Object]` — correctly. But
`classOf[Int]` is `Class[Int]` and `Int` is not `<: Object`, so any call passing a primitive class
literal needs the bound absent. One site wants the bound; sixteen want it gone.

| attempt | measured |
|---|---|
| give the substitute the bound `[T <: Object]` | 1 → **16** (all `Class[Int]` vs `Class[Object]`) |
| …and write java's own static type, `classOf[Int].asInstanceOf[Class[Integer]]` | 1 → **21** |
| drop java's implicit `Object` bound on METHOD type params | 1 → **7** |
| pin `T` + cast the literal + give the substitute the bound | 1 → **52** |

Each taught something:

- The cast in attempt 2 is **correct** — same runtime object, java's own static type — but with
  inference still free the ASSIGNMENT's expected type leaks in and scala demands
  `Class[Object & Int]`. The fix has to pin `T` at the same time.
- Attempt 3 is a **clean refutation**: the bound is load-bearing wherever a method's `T` flows into a
  CLASS's `T` (`Array.with[T](…): Array[T]` calling `new Array[T](…)` needs `T <: Object`). "An
  override may simply drop the bound" is wrong; only "an override COPIES the parent's bounds" works.
- Attempt 4 found, by tracing rather than editing (`CLAUDE.md` §4.6), that **Spoon reports
  `actuals = 1` for a call carrying a primitive class literal** — it hands back the INFERRED type
  argument even though the Java source writes none. So the existing code is not failing to see the
  case; it deliberately declines it. Removing the exclusion compiles the 16 and produces **52**
  `equals(Object)` vs `equals(Any)` name clashes across unrelated files, which is **not understood**
  and is where the next attempt must start. Do not re-run any of the four without that answer.

The right general rule: **take an override's type-parameter bounds from the overridden member**,
which needs the engine to see an INJECTED parent's signature — the same channel
`TirEmitter(program, externalConcrete)` opened for diamond disambiguation. Extend that; do not
invent a second one.

*Fix kind: (a) for the rule. Bounding only the overridden overload of an injected substitute is (b)
policy in that library's manifest.*

---

## 2. Constructors

### C1. Never promote a paramful constructor to the primary without a WHOLE-PROGRAM check — +14

It removes the class's nilary construction path, and every subclass whose `extends` clause passes no
arguments then fails (`FillViewport extends ScalingViewport`, `FloatAttribute extends Attribute`).
The promotion must be withheld at a fixpoint (`CtorFunnel.Plans`).

*Fix kind: (a).*

### C2. A promoted constructor's parameters AND top-level locals become MEMBERS

Inlining a promoted body without renaming what it declares collides with fields
(`GLVersion.vendorString`, `PolygonRegion.textureCoords`). The **inherited**-name case matters as
much as the own-name one, because it captures unqualified reads instead of failing to compile.

Covered as a governing rule by `CLAUDE.md` §4.55 — read it before writing any renaming pass.

*Fix kind: (a).*

### C3. `super(args)` in a secondary constructor — and why PADDING is not a fix

`CLAUDE.md` §4.4 has the rule (promote the widest super call to the primary and delegate). The
measured boundary is here: **padding a shorter super call to reach a wider parent constructor
measured 0 → 55 errors.** It is exact only for the JDK throwable family, whose constructor set is
fixed; elsewhere it is a guess. 49 sites were **left counted by `OmissionCheck` rather than
guessed** — `DistanceFieldFont extends BitmapFont` has seven roots reaching seven different
overloads.

Note also that `super(msg)` and `super(msg, null)` differ in `Throwable`'s cause semantics (unset vs
null), which no delegation reproduces.

*Fix kind: (a) — and the (a) is "count the omission", not "approximate it".*

### C4. Several roots, none nilary, plus an explicit nilary constructor = a clash with no plan

`plan0`'s search for a nilary ROOT finds none when the nilary constructor *delegates* (`this(1)`), so
it returns no plan and the synthesised Scala primary collides with the emitted `def this()`.

**Two earlier write-ups of this were wrong**, and the correction is the durable part:

- ~~"No faithful single-primary encoding is known"~~ — wrong; defaults give one.
- ~~"The whole-program fixpoint withholds the nomination"~~ — **also wrong**. `plan0` never nominates
  anything. Verified by building promote-with-defaults end to end (defaults from the nilary
  constructor's own `this(1)`, the fixpoint preferring defaults to withholding, the emitter
  rendering `(regionsCount: Int = 1)` and `extends C()`): the emitted class came out **unchanged**,
  so the defaults path was never consulted. Reverted rather than left in as dead code.

The available fix: when several roots exist, none is nilary, but the class HAS an explicit nilary
constructor, promote **that** one and inline its `this(args)` delegation via `effects`. Sound
wherever `supersedes` holds.

*Fix kind: (a). Recorded chiefly as a warning about diagnosing before building — see M4.*

### C5. Constructor REPLAY repeats work Java did once — a declared cost, not a defect

Where a prologue is dead but not empty, replaying a parent's statements after `this()` re-runs
allocation (`new DelayedRemovalArray(1000)` allocates and discards the nilary path's 16-element
backing array). Final state is identical. This is a cost, and it replaces the previous behaviour of
silently returning the wrong array.

Replay is refused, and the omission counted, where the parent's nilary path does real work the
constructor does not re-do (classpath I/O, texture allocation), or where `super` targets a VARARGS
parent constructor and the argument is a single element rather than a `Repeated`.

*Fix kind: (a).*

### C6. Do NOT tighten `supersedes` to inspect assignment RIGHT-HAND SIDES — it removes no effect and costs the argument

`CtorFunnel.Plans.supersedes` compares only the assignment TARGETS of the prologue and the replay.
Its docstring used to claim the prologue is "invisible" when it is pure field assignment, and
nothing in it looks at an RHS — so `this.n = Registry.register()` in a prologue passes, and that
call really did happen where Java never ran it. Read as written, the contract was overstated, and
the obvious repair is to require the RHS to be effect-free.

**Do not.** The prologue is the emitted class's OWN construction path — whatever `class C extends P`
plus the promoted primary's body runs — and a secondary constructor's first statement must be
`this(...)`, so `this()` executes the prologue *before* `supersedes` is consulted and regardless of
its answer. Measured with a kill switch forcing `supersedes` to `false` on a probe with an escaping
prologue RHS:

| | escaping call runs | argument delivered |
|---|---|---|
| replay accepted (today) | yes | yes — `this(); this.n = k` |
| replay refused (tightened) | **still yes** | **no** — `this()`, and an omission finding |

Refusing removes nothing and loses the constructor's argument. Corpus reach of the gap: **330
accepted replays on libGDX have a non-re-readable prologue RHS**, and every distinct shape there is
an allocation (`new C(…)`), a pure JDK static (`Long.numberOfTrailingZeros`), or a cast — all
harmless. Ashley adds exactly one shape that is NOT (`Family.Builder.get()`, which mutates a static
family cache), and it changes nothing: refusing that replay would still run the `get()`, because it
is in the prologue, and would additionally drop the family the constructor was passing up. So the
tightened rule refuses 330+ replays to fix zero defects.

The escaping-effect problem is real and lives one level UP, in the promotion: making a nilary
constructor's body the class body runs it on every construction path, which Java did not
(`Base() { this.n = Audit.bump(); }` plus `Base(int)` — `new Base(5)` bumps in the port and does not
in Java). That is a `plan0` question about which constructor may be promoted, not a `supersedes`
question, and it is still open.

*Fix kind: (a), at the promotion — NOT at `supersedes`.*

---

## 3. `this`, inner classes and anonymous classes

### T1. A `CtNewClass` is a SUBTYPE of `CtConstructorCall` — 156 silently dropped bodies

Translating a `CtConstructorCall` without asking whether the node is the `CtNewClass` subtype emits
every Java anonymous class as a bare constructor call **with its body discarded**. 156 sites; every
button silently did nothing when clicked, and the gate stayed green for the project's entire
history. Only four `java.util.Comparator` sites failed to compile, which is the only reason it was
noticed.

If your frontend touches constructor calls, check this first.

The model that works: `Tree.New` carries `anon: Option[Tree.AnonClass]`; `None` means "not an
anonymous class"; **`Some(Nil)` means `new Base(){}`, which is a DIFFERENT type from `new Base()`**
and still renders its braces. `AnonClass` carries `dropped` so `OmissionCheck` can report members
the frontend could not carry. Members are owned by a SYNTHETIC symbol (two listeners in one class
both declaring `clicked` would otherwise intern to one symbol).

*Fix kind: (a).*

### T2. Inside an anonymous body, only a `this` in VALUE position may be rebound — **+33** twice

| attempt | measured |
|---|---|
| bind an anonymous body's `this` to the anonymous instance in MEMBER-ACCESS position | 33 → **66** |
| treat an UNTYPED `this` inside an anonymous body as the anonymous instance | same **+33** |

Spoon reports the anonymous class as the target type of an implicit `this` **whatever the member's
real owner is** — a key listener calling `setSelectedIndex`, declared on the enclosing `List` — so
rebinding in access position loses every enclosing-member access at once (all `E008 value X is not a
member of InputListener{…}`).

And **Spoon leaves `CtThisAccess.getType` null for many implicit accesses**, so `forall` (vacuously
true on `None`) must not be used to decide this. Require an explicit match on the anonymous class's
qualified name.

As an access target, the existing resolution — whose fallback is the bare name Scala resolves
lexically exactly as Java did — is correct.

*Fix kind: (a).*

### T3. An anonymous class has no name, so it cannot be a `this` QUALIFIER

Spoon suggests a qualifier like `Pixmap$1`, which names nothing in the emitted code. Emit the
reference bare; Scala resolves it lexically to that enclosing anonymous class's member, which is what
Java resolved.

*Fix kind: (a).*

### T4. Qualified `Outer.this` needs BOTH a static guard and a supertype guard — +22

Java libraries nest subclasses inside their own base (`DynamicsModifier.FaceDirection extends
DynamicsModifier`), and Spoon reports a plain `this` reaching an INHERITED member under the member's
DECLARING type. Two guards are required together:

- the frontend walks out only through **non-static** inner classes (`capturesEnclosing`);
- the emitter refuses to qualify a symbol the innermost class **inherits** (`inheritsFrom`) —
  constructor replay moves the base's `this` statements into the subclass body, where the bare `this`
  is right.

*Fix kind: (a).*

### T5. "The frontend cannot see it" is NOT "we cannot see it"

Under `noClasspath` Spoon does **not** resolve an implicit access to an inherited instance field to a
`CtFieldWrite` at all, so neither the `null`-target nor the implicit-`CtThisAccess` branch of a
field-access translator sees it. Qualifying it as `this.f` measured **0 change — the code never
runs.**

But the site was fixed anyway, from downstream: the TIR already knew the symbol was an instance
member of an ancestor, and the emitter already knew which static names each companion carries.
**Check what the TIR and the emitter already know before recording a blocker**, and before writing
another frontend branch that will not be reached.

*Fix kind: (a).*

### T6. A Java `@interface` is an ANNOTATION TYPE — 161 errors if it is not

Spoon reports an `@interface` as a `CtInterface`. Emitted as an ordinary interface it becomes a
`trait`, and then **nothing can be annotated with it**: 161 errors the instant annotations started
being emitted (7 → 179). It must become `class X extends scala.annotation.StaticAnnotation`, which
needs an explicit `Flags.isAnnotation`.

Two more annotation facts:

- **Annotation arguments are real terms.** Dropping an ARGUMENT is the same silent-omission defect
  one level down; `@A` where Java wrote `@A(x)` is a different annotation. Where no expression
  translator is in scope, carry only MARKER annotations and **report** the rest.
- **Java's single-value shorthand for an ARRAY element.** `@SuppressWarnings("unchecked")` means
  `value = {"unchecked"}`; Scala wants `Array("unchecked")`. Decide from the element's DECLARED type
  and leave it alone when that cannot be read — a wrong wrap is worse than the compile error it
  replaces (11 errors).

The worst shape a silent omission can take is here: **a suite with no `@Test` runs zero tests and
reports SUCCESS.** It manufactures the evidence that behaviour is fine and conceals itself by
disabling the very gate meant to catch such things. `@Test in Java: 221 / @Test in emitted Scala: 0`
was found on the day the tests were first ported, after two sessions of compile-count work never
came close to it.

*Fix kind: (a).*

### T7. Concrete-member DIAMOND — solved at the EMITTER; qualified `super[X]` still has no TIR node

`class Entries extends MapIterator with JavaIterator`: `remove()` is concrete in both. Java has no
such rule — `MapIterator.remove()` simply implements `Iterator.remove()`. Scala's linearisation
demands an explicit disambiguation. **Any** Java class inheriting a concrete method from its
superclass while implementing an interface that has a default for it produces this. 11 sites in one
module.

**This SHIPS**: `TirEmitter.diamondOverrides` synthesises
`override def remove(): Unit = super[MapIterator].remove()` for every member that arrives concrete
from both the superclass chain and a mixin, choosing the SUPERCLASS — the parent Java would have
run. It is a rendering repair, not a tree rewrite: no new symbol, no call-site change, the
qualified-super TEXT emitted directly. Do not re-derive or re-attempt it.

**What remains a limit:** `Tree.Super(cls, …)` carries the class but the emitter prints a bare
`super` — qualified `super[X]` is still not expressible in the TIR, so a TRANSFORM that needs a
qualified super call in a TREE (anything beyond this fixed forwarder shape) is still blocked on
adding that node.

Relatedly, `export` diamonds must dedupe by **DECLARING TYPE**, so a diamond drops the duplicate
while a genuine redeclaration keeps the most specific.

*Fix kind: (a) — shipped for the diamond forwarder; the TIR node is an (a) prerequisite for
anything more.*

### T8. Enum constants with class bodies — a known thin path

An enum-constant anonymous body is read for `CtMethod` members only; a field or instance-initializer
block in a constant body would be dropped **silently, with no omission finding**. Zero sites in
libGDX core (no enum constant there has a class body at all), which is why it was left. If your
library uses that Java form, this is a known hole, not a surprise.

*Fix kind: (a), unbuilt.*

---

## 4. Collections, shims and the JDK boundary

### K1. Never model a Java interface on a Scala COLLECTION trait — the governing rule is `CLAUDE.md` §4.5

The measured evidence behind it, kept here:

- Modelled on `scala.collection.{Iterable, Iterator}`, the shape 14 classes actually have is not
  awkward but **ILLEGAL**: 24 "cannot override final member", 19 `size` vs `IterableOnceOps`, 15
  `isEmpty`, 15 "inherits conflicting members".
- Rewriting both shims as standalone traits carrying Java's ARITY went **145 → 47 → 69** and closed
  the cluster.
- `foreach` on **both** shims made every `for` over a class that is both iterable and iterator
  **ambiguous — 23 errors**. It belongs on the iterable only, which is what Java's own for-each
  requires anyway.
- A parenless-accessor rewrite (stripping `()` from `iterator`/`hasNext`/`next`) exists for Scala
  collections' parameterless accessors and **must decline on a shim — 24 errors**.

*Fix kind: (a).*

### K2. The JDK/Scala collection BOUNDARY is universal, and neither obvious fix works

Retyping your library's own collection signatures while the bodies still call the JDK creates two
collection worlds that cannot meet — a problem Java does not have, where `List` IS an `Iterable`.
**Two independent witnesses** in one module: a `java.util.Map[String, java.util.List[String]]`
returned by the JDK flowing into a retyped declaration, and `appendAll(JavaIterable[?])` handed a
`mutable.ArrayBuffer` that came from a `java.util.List` mapping.

Two measured dead ends:

| attempt | measured |
|---|---|
| `given Conversion[scala.collection.Iterable[A], JavaIterable[A]]` in the shim's companion | **5 → 5** |
| map `java.lang.Iterable` to `scala.collection.Iterable` in PARAMETER position only | 1 → **5** |

The first reason is decisive and general: the conversion was verified in isolation first — it
compiles, applies with **no import** (the target's companion is in implicit scope, so it respects
the FQN-no-imports rule) and only raises a feature warning. Against the corpus it changed nothing,
because **the call is OVERLOADED, and Scala does not attempt implicit conversions when no overload
alternative matches. No bridge placed anywhere can rescue an overloaded call.**

The second fails because widening the parameter breaks bodies that **iterate-and-remove** through
it. A provenance rule that works where two candidates differ only in *mutability* does not transfer
where they differ in a **capability the body may already use**.

`.asScala` is not a free fix either: on a nested collection it **COPIES**, turning a live view into a
detached snapshot, and under `noClasspath` a JDK shadow may carry no return type at all to convert
from.

**The approach that WORKS — wrap at the CALL SITE — is BUILT.** Both dead ends attacked the TYPE;
`CollectionsTransform.coerce` attacks the SLOT: where an expression of a retyped scala kind meets a
shim-typed expectation, it inserts the explicit wrap *before* overload resolution runs, so the
argument's type is already exactly the formal — nothing has to be inferred — and the parameter
keeps the shim, so iterate-and-remove bodies are untouched. Measured: it is one of the seams that
took simple-graphs to 0 and un-broke the libGDX test port after the argument-only version failed
(`0 → 3` on declared slots; see the port status files for the per-step numbers).

**Coverage, stated as a table because a prose claim of totality was wrong twice.** Four slot kinds —
shim-typed ARGUMENT, DECLARATION, ASSIGNMENT and RETURN — crossed with the source kinds:

| source \ target | `JavaIterable` | `JavaCollection` |
|---|---|---|
| `Kind.Seq` (`Buffer`, `ArrayBuffer`, `Queue`, `ArrayDeque`) | `JavaIterable.from` | `JavaCollection.from` |
| `Kind.Set` (`mutable.Set` & co) | `JavaIterable.from` | `JavaCollection.fromSet` |
| `Kind.Map` (`mutable.Map` & co) | `JavaIterable.from` | **REFUSED** |
| `Kind.Entry` (`Tuple2`) | n/a — not a collection | n/a |

`JavaIterable.from` takes a `scala.collection.Iterable`, so every kind reaches it with nothing
added, and a scala `Map[K, V]` IS an `Iterable[(K, V)]` — precisely java's `entrySet()` view.

Three cells are REFUSALS rather than gaps, and each refusal is a compile error at the slot (M6):

- **`Kind.Map` into `JavaCollection`.** Java's `Map` is neither a `Collection` nor an `Iterable`, so
  no valid java sends one to such a slot; the only path is the phase's own `entrySet()` rewrite. A
  `Collection` view of a map's entries would have to reproduce `entrySet().remove(e)` removing a
  mapping only when the KEY AND THE VALUE both match — guessing that is §4.4 exactly.
- **A `map.keySet()` SOURCE, whatever the target.** Its node claims the retyped `mutable.Set` while
  the scala it emits is `m.keySet`, a `scala.collection.Set` — §0's root cause met in a new place,
  and the same disagreement `transformValDef`'s keySet arm already encodes for a declaration.
  Wrapping on a type the phase knows the value does not have emits a call that names the WRAPPER;
  refusing leaves the error naming the BOUNDARY (`Found: scala.collection.Set[String] / Required:
  JavaCollection[String]`), which is the one a reader can act on. `Map.values()` has no such
  problem — its rewrite already wraps at the call and restores the invariant.
- **The RETURN walk is deliberately BOUNDED.** A `return` inside a lambda, an anonymous class's
  method or a local class returns from THAT, so the walk follows only the nine statement-carrying
  node kinds and its default arm does not descend. Under-reach is a missed coercion — a compile
  error. Over-reach would be a wrong wrap that can type-check. The rule generalises: **when a walk
  must be scope-bounded rather than complete, make the DEFAULT stop**, so a node kind added later
  fails loudly instead of silently reaching into a scope it does not own.

*Fix kind: (a). Whether to retype collections **at all** is (b) — a JVM-only port may keep
`java.util` and skip the phase entirely, which makes this boundary vanish.*

### K3. Injected sources are for SEMANTICS the target lacks — never for adapting SHAPES

Two rules, and a port violates them in different ways:

- **Semantics the target genuinely lacks** (a removal-capable iterator) → a runtime the port
  **depends on**, published and version-locked to the engine. **Not** source copied into each port:
  when two ports emit their own copy at the same FQN, the Scala.js and Native linkers see duplicate
  definitions — a hard error on exactly the platforms a cross-platform port exists for — and on the
  JVM, two ports pinned to different engine versions carry silently divergent bodies at one name.
- **Shapes the engine could emit correctly** → the engine emits them, and nothing ships.

A base class whose every member is `assertEquals(a, b) => assert(a == b)` or
`testCase(n, b) => test(n)(b)` contributes nothing over what it wraps. Shipping it means every new
porting effort copy-pastes shared glue, which is a failure of a re-compiler.

Both offenders are gone: `PortedSuite` (K4) and the `Asserts` assertion façade (X2), the second of
which looked like the first rule's case — "MUnit's type constraint is a semantic gap" — and was not.
The test that separates them: could the engine EMIT the difference from what it already knows? A
removal-capable iterator, no. An argument order and a numeric widening the transform holds both
types for, yes.

*Fix kind: (a) for what to inject; see `LIBRARY-READINESS.md` §1.3 for the distribution work.*

### K4. RETRACTED — the TIR expresses a CURRIED APPLICATION perfectly well

This entry said `test(name)(body)` could not be emitted, and that an un-curried forwarder in an
injected base class was therefore unavoidable. **It was wrong.** `Tree.Apply.fun` is itself a `Term`,
so two argument lists are a NESTED `Apply` — exactly how `quotes.reflect` models currying. The
scaffold was built over a gap that did not exist, and both it and the `Asserts` façade that rode
along on the same file have been deleted.

Kept, rather than removed, as the standing warning: before concluding that an IR cannot express a
target idiom, build the tree and emit it. This one cost a base class shipped into every port.

*Fix kind: (a). No work outstanding.*

---

## 5. Portability and platform

### P1. A `--js` compile proves NOTHING as a portability gate

Scala.js type-checks against JDK signatures and compiles `java.lang.reflect` happily. **Only the
linker rejects it, and only for code reachable from an entry point — which a library lacks.** This is
why portability must be checked over the TIR (`PortabilityCheck`), not by compiling for the target.

*Fix kind: (a).*

### P2. A check that is not WIRED is not a check, and a rule that does not exist reports clean

Both gates that should have caught "the ported test suite is JUnit, and neither Scala.js nor Scala
Native has JUnit" missed it, in the two independent ways a gate can miss:

- the test migration ran two checks and **never called `PortabilityCheck` at all**;
- `PortabilityCheck.jsAndNative` **had no `org.junit` rule**, so it would have reported clean anyway.

Fix the wiring and the rule *before* the translation: that turns a silent assumption into a number,
which is worth more than the conversion itself. Check invocation being copy-paste rather than
orchestration is the root cause and is tracked in `LIBRARY-READINESS.md` §1.2.

*Fix kind: (a) for the rule and the orchestration; the per-library API list is (b).*

### P3. A JVM-only API in the LIBRARY is not an engine gap

`java.lang.reflect`, `Thread`, networking, `java.util.zip`, `java.util.concurrent` appearing in
emitted code means the library uses them. Porting them to Scala.js/Native needs **per-library
substitution** — replacing the type with an injected implementation — which is exactly what a
hand-port does. Do not look for an engine fix.

*Fix kind: (b) `Substitutions`, with (c) injected sources.*

### P4. An EXTERNAL MEMBER is only identifiable through its owner — and it had no owner

**libGDX core 139 → 147, test port 148 → 156.** Nine of `PortabilityCheck`'s rules name a member
(`java.lang.Class#forName`, `#newInstance`, the six reflective `getDeclared*`/`get*` readers,
`java.lang.System#getProperty`) — the exact APIs the check names as its reason for existing. **Not
one of them had ever fired**, in the whole history of the project, while the check reported a number
that read as coverage.

The cause is structural, and it will be the same in any frontend that interns lazily. An external
symbol has no declaration to name it, so `Minter.external` set `fullName` to the *interning key*
(`@8#forName(java.lang.String)`) and `owner = SymId.None`. The check then computed
`owner.fullName + "#" + name`, got `None` from every external member, and `None.contains(rule)` is
false forever. The prefix branch could not match either, because the `fullName` is `@8#…`. The
comment two lines above the bug states the intent; nothing enforced it.

Three things to take from it:

- **An external TYPE is correctly rooted at `SymId.None`** — `PackageRenameTransform.ownedSymbols`,
  `Cache.topOwner` and `PortabilityCheck.owningType` all decide "is this ours?" by climbing to it.
  An external **member** must NOT be: its owner is the external type, whose own owner is `None`, so
  every ownership predicate still terminates one level later and answers identically. Verified
  against the hostile `Map("java" -> "jvm")` rename spec, which must leave `java.lang.String` alone.
- **Leave the `fullName` alone.** It is the interning key and the emitter, the nested-path builder
  and the rename all read it. Only the owner moves. (Rendering it as `owner#name` instead would also
  make the *prefix* rules match members, double-counting every site that already counts through its
  receiver type.)
- **Two other engine mechanisms key on the same string** and were equally blind: `ClassTableTransform`
  and `StaticForwarderTransform` both select by `owner.fullName#name`. They worked only because
  every key they were given happened to name an IN-PROGRAM wrapper. A redirect written against a
  JDK member (`"java.lang.Class#forName" -> …`) silently matched nothing before this and matches now.

Cost: a whole audit pass to notice, twice independently. What made it findable was asking why a
*known* unportable API was not in the output — the same move that found the missing `org.junit` rule
(P2). **When a check reports zero, name an API you know is present and confirm the check sees it.**

*Fix kind: (a) engine — one field in the frontend's interning.*

---

## 6. Porting a test suite

### X1. Converting JUnit to MUnit is a STRUCTURAL transform, not an annotation rename

Per `CLAUDE.md` §1 this is **(a) universal** — every Java library ported to cross-platform Scala
needs it — so it belongs in the engine with the *target framework* parameterised, not in a library's
policy.

| JUnit | MUnit |
|---|---|
| `@Test def m()` in a plain class | `class X extends munit.FunSuite` + `test("m") { … }` |
| `@Test(expected = classOf[E])` | `intercept[E] { … }` |
| `Assert.assertEquals(expected, actual)` | `assertEquals(obtained, expected)` — **order reversed** |
| `assertArrayEquals` | no equivalent; `assertEquals(a.toSeq, b.toSeq)` |
| `assertEquals(d1, d2, delta)` | `assertEqualsDouble` |
| `@Before` | called at the head of each test body (`CLAUDE.md` §4.4) |
| `@RunWith(Parameterized)` | no equivalent; generate N tests or loop — **report by name**, never silently mistranslate |

The reversed argument order does not change pass/fail; it flips the expected/obtained labels in
failure messages.

*Fix kind: (a), target framework parameterised (b).*

### X2. CLOSED — MUnit's `assertEquals` is TYPE-CONSTRAINED, and all 33 errors were the transform's job

Java's `assertEquals(Object, Object)` compares anything; MUnit's `assertEquals[A, B]` needs a
`Compare[A, B]`. Mapping directly measured **1 -> 33**, and an early reading of that number
concluded a shipped helper was justified. **That was wrong twice over** — first because the errors
were shape adaptation (K3), and then because closing them measured **33 -> 0**:

| cause | count | closed by |
|---|---|---|
| `Can't compare these two types: Long / Int` | 26 | re-applying JAVA'S BINARY NUMERIC PROMOTION in the transform — widen the NARROWER operand from the two static types the TIR already carries, and promote a `Char`/`Short` pair to `Int` since neither widens to the other |
| `Not found: assertEquals` / `fail` | 6 | X3 |
| unrelated pre-existing error | 1 | — |

Measured 2026-07-29: `balticporter.runtime.Asserts` deleted, **0 compile errors, 217/221 tests
passing** — the same four `Json.fromJson` substitution failures as before. Nothing ships with the
port. Do not re-derive whether the helper is needed; it is not.

The whole mapping is argument PERMUTATION, which is what a re-compiler is for. Every shape below was
probed directly against MUnit 1.0.2 and compiles:

| junit | MUnit |
|---|---|
| `assertEquals(expected, actual)` | `assertEquals(actual, expected)` |
| `assertEquals(message, expected, actual)` | `assertEquals(actual, expected, message)` — java's LEADING message is MUnit's TRAILING clue |
| `assertEquals(e, a, delta)` | `assertEqualsFloat` / `assertEqualsDouble`, chosen by the WIDTH of the widest operand |
| `assertTrue(b)` / `assertFalse(b)` | `assert(b)` / `assertEquals(b, false)` |
| `assertNull(o)` / `assertNotNull(o)` | `assertEquals(o, null)` / `assertNotEquals(o, null)` |
| `assertSame` / `assertNotSame` | `assert(a eq e)` / `assert(a ne e)` — **never `assertEquals`**, which is java's `equals` (`CLAUDE.md` §4.4) |
| `assertArrayEquals(e, a)` | `assertEquals(a.toSeq, e.toSeq)` |
| `fail()` | `fail("failed")` — MUnit has no no-argument form |

**One junit assertion has no MUnit counterpart at all**: `assertArrayEquals(e, a, delta)`,
elementwise-with-tolerance. Emit it as the loop it means — bind BOTH arrays to locals first, since
the operands are arbitrary expressions and naming each once is the difference between java's one
evaluation and one per element — and check the lengths before the elements, as junit does. Dropping
the delta and comparing `.toSeq` is STRICTER than java and fails tests that pass upstream.

Two traps in the numeric widening, both real:

- **Read which overload was resolved from the ARGUMENTS' static types, not from the callee's
  signature.** Both were available (the Spoon frontend encodes the erased signature into an external
  member's `fullName`), but only the argument types are an IR contract. Java's optional leading
  `String message` is separable structurally: a leading `String` is the message exactly when the
  call has more arguments than the member's minimal arity — which distinguishes every junit overload
  that exists without naming one.
- **A widening conversion needs its receiver PARENTHESIZED.** `a * b` is a bare `Apply` in the TIR
  but renders infix, so `.toLong` on it attaches to `b`: `x >> 2.toLong` is not `(x >> 2).toLong`.

*Fix kind: (a). Closed in `TestFrameworkTransform`.*

### X3. CLOSED — a Java `static` test helper emits into the COMPANION OBJECT; use the framework's assertion OBJECT

~6 errors (`Not found: assertEquals` / `fail`). If your target framework puts its assertions on an
instance base class, anything Java made `static` cannot see them: it lands in the companion object,
which does not extend the suite.

**The fix is NOT to move the helper onto the suite.** That was the obvious answer and it is the
worse one — it changes which scope every static member of a test class lives in, and a helper named
`test` (libGDX has one) then overloads the framework's own registration method. MUnit declares every
assertion twice, on the `Assertions` TRAIT that `FunSuite` mixes in and on the `munit.Assertions`
OBJECT, and an object member resolves identically from a suite body, a companion object, a nested
class and a lambda. Emit every assertion fully qualified through the object and the scope question
disappears instead of being answered — which also satisfies `CLAUDE.md` §6.

Generalise the shape, not the name: **when a target framework offers its assertions as both
inherited members and object members, emit the object members.** Inheritance is the only one of the
two that a translated scope can fail to reach.

*Fix kind: (a). Closed in `TestFrameworkTransform`.*

### X4. Calling `@Before` at the head of each test does not reproduce JUnit's FRESH INSTANCE

`CLAUDE.md` §4.4 gives the translation. Its declared limit: it is exact wherever setup **assigns**
the fields it needs; a field carrying state through its own **INITIALISER** would still leak between
tests. No libGDX test depends on it, and all 217 passing tests are unaffected — but it is a known
approximation, not an equivalence.

*Fix kind: (a), unbuilt.*

### X5. JUnit lifecycle and enablement — CLOSED, except what has no translation

`@After` used to leave a `tearDown` as an ordinary never-called method — the **same silent shape** as
the `@Before` defect, but on the release side: tests passed and leaked state. `@Ignore` **enabled** a
disabled test, turning an upstream "we know this is broken" into a green result that meant nothing.

Both now translate, and the shape matters more than the mapping:

- **`@After` is `try { … } finally { tearDown() }`, not a trailing call.** JUnit's statement chain is
  `afters(befores(expectException(invoke)))`, so teardown runs *whether or not the body threw* —
  which is exactly the case teardown exists for. Appending the call to the end of the body is the
  form that compiles and is wrong. `@Before` calls go INSIDE the `try`, so a setup that throws still
  tears down. Deviation kept honest: JUnit runs each `@After` in its own try/catch collecting
  errors, and orders subclass before superclass; the port runs them in declaration order in one
  `finally`.
- **`@Ignore` is `test(munit.TestOptions("n").ignore) { … }`**, on a method or a whole class. Not
  `"n".ignore` — that needs MUnit's implicit `String` conversion, and this emitter writes
  fully-qualified names with no imports (§6). The body is kept: it still has to compile.
- `@BeforeClass` / `@AfterClass` → `beforeAll()` / `afterAll()`.

**What has no translation is now REPORTED, with its §1 class, instead of vanishing:** `@Rule`,
`@ClassRule`, `@RunWith`, JUnit 5, TestNG, JUnit 3's `TestCase` subclass, and Hamcrest `assertThat`.
All classify **(a)** — which is itself the finding: none is fixable by configuring a phase or writing
a library rule.

`@RunWith` is the one worth understanding before you try: a custom runner changes how tests are
**enumerated**, so a converted suite runs a different *set* of tests from Java's while looking
complete. Hamcrest is a second assertion vocabulary; inventing a mapping is precisely the silent
miss this project exists to prevent.

**Two claims here were false and are corrected.** JUnit 5 reaches the `org.junit.` prefix rule only
through the *term* reference from an assertion call — annotation types are not in the xref at all,
since `Xref` walks trees and not `Symbol.annotations`, so a JUnit-5 suite whose assertions came from
elsewhere is invisible. And **TestNG matches no rule whatsoever**; `PortabilityCheck.check` returns
`Nil` for it. A test now pins that `Nil` so it fails the day a rule is added.

*Fix kind: (a) for the residue, and it is reported rather than silent. Lifecycle: BUILT.*

---

## 7. Measurement discipline — the ones that will mislead you

### M1. The error count is a TYPER-ONLY measurement — the governing rule is `CLAUDE.md` §3

The evidence, kept here because the size of the effect is the point:

- Minimal reproduction: a file with one E007 and a second file with a missing `override` reports
  **1 error, not 2**. dotty's `Phase.isRunnable` is `!ctx.reporter.hasErrors`.
- The moment the typer reached zero, the count went **1 → 145**: 93 override errors and 8
  unimplemented-member errors became visible for the first time. They had been there all along.
- On another pass, **300 → 6** with **four** compiler phases running that never had — parser/naming,
  typer, PostTyper bound checks (11 latent E057), RefChecks (**907 latent E164**). *A number measured
  before those phases ran was measuring less than it appeared to.*
- Verified directly rather than assumed: one file was **byte-identical** with and without the commit
  that closed the typer error, yet errors only with it.

**Expect the count to RISE when it first reaches 0. That is the gate beginning to tell the truth.**

*Fix kind: (a) — and the (a) is understanding the gate, not changing it.*

### M2. A green compile says nothing about behaviour — the governing rule is `CLAUDE.md` §4.4

Thirteen defects were found by running ported tests, **none of which moved any compile-error count**.
The pass trajectory, which is the argument for running tests over fixing counts:
**48 → 52 → 88 → 113 → 115 → 183 → 187 → 188 → 201 → 217**.

Two readings worth carrying:

- The **115 → 183** jump is not 68 tests fixed one at a time. With control flow wrong the suite did
  not get past one package inside the timeout; fixing `break`/`continue` let it **RUN**. Expect
  step-changes, not gradients.
- Two of the thirteen were **silent behavioural changes rather than crashes** and would have
  shipped: a subclass field shadowing a superclass field emitted as one field (writes through the
  subclass reached the superclass's draw path), and an `override` of a method an injected substitute
  did not declare — it **compiled to nothing**.

### M3. A two-stage measurement can be honest about its own stage and still lie

A test-measurement script that re-emits only the TESTS is **blind to a change in a core transform**
until the core measure runs. The first reading of one experiment was taken against a stale core and
looked like a harmless `5 → 5`. **Run the core measure first whenever the change is to the engine.**
Per-script stale-emit aborts do not cover this: each script is individually honest about its OWN
stage.

*Fix kind: (a) in the measurement scripts.*

### M4. A kill switch beats another condition — the governing rule is `CLAUDE.md` §4.6

The evidence: three consecutive edits widening conditions inside one gate each measured **11 → 11,
INERT**. What settled it in two runs was (1) a kill switch returning the input at the top of the
function, printing on entry — **72120 calls suppressed, cast unchanged**, so the gate was provably
not responsible — and (2) a tracer on all **16** construction sites of that node kind, which showed
the cast was never built by the frontend at all. It came from the emitter.

Two further inert-by-instrumentation results in the same family, recorded so the plausible diagnosis
is not committed again:

- "Give the gate the RENDERED receiver type" — built it, printed the gate's inputs at the failing
  site, and the Spoon type was **already** one actual, not raw, so the gate was never blocked there.
  Reverted; **6 → 6, same six sites.** *The wrong diagnosis was committed first and looked plausible.*
- Extending a scope gate to clear itself at a declaration site — **3 → 3**: the cast path does not
  route through it.

`sbt -client` talks to a long-running server, so a shell environment variable never reaches the
forked migration. **Gate the switch on a marker FILE.**

### M5. Walk the tree with `StandardTraversal` — the governing rule is `CLAUDE.md` §3

Both silent-omission defects in this project were hand-rolled traversals that stopped one node short:
the anonymous-class omission itself, and a mutable-params transform that walked class bodies by hand
and so never saw a method of an anonymous class. When anonymous bodies started being emitted, fifteen
`E052 Reassignment to val` appeared from that second gap alone.

**A check reporting zero is only as good as its coverage.** Add the check in the same commit as the
translation path, and negative-test it — a check that has never failed is not known to work.

### M5.5 After editing `runtime/`, RESTART the sbt server before believing a vendoring spec

`RuntimeArtifact` reads the runtime sources from a resource that `core`'s build copies out of the
`runtime` module. Two layers can serve a stale copy, and only one of them matters:

- `classes/` lags `resource_managed/` until `copyResources` runs, which plain `compile` does not
  trigger. Harmless in practice — every migration runs FORKED (`corpus-tests` sets
  `Compile / run / fork := true`) and reads the current file from disk, which is why all four ports
  measured correctly throughout.
- `sbt -client`'s CLASSLOADER LAYER caches the resource for the life of the server, so a NON-forked
  test keeps seeing the previous text. `RuntimeArtifactSpec` ("the vendored text is byte-identical to
  the published source") and `RuntimeMembersDerivationSpec` both failed on a runtime edit that was
  correct, and both passed unchanged after `pkill -9 -f sbt-launch`.

Cost of not knowing this: a plausible-looking build change (`IO.copyFile` → `IO.write`, to stop the
copy inheriting the source's mtime) attributed to the wrong layer entirely. It was reverted, because a
change that cannot be shown to fix the thing it claims to fix is a comment that will mislead the next
reader. Same family as the `-D`-does-not-reach-the-forked-migration trap in §4.6 — the state that
lies is between the edit and the process that reads it.

### M5.6 Killing a hung `sbt -client` WEDGES the server permanently — kill the SERVER, not the client

sbt 2's network channel dies badly under a client kill: `NetworkChannel.shutdown →
VirtualTerminal.cancelRequests` blocks forever on a full `ArrayBlockingQueue`, and every later
command — `sbt -batch` included, it also goes through sbtn — queues behind the corpse. From the
outside this looks like the machine having a slow day, indefinitely; two concurrent worktrees hit
it in one afternoon, independently.

Recovery, in order: find the wedged checkout's OWN server (each checkout has one — the socket is
per project directory) with `ps aux | grep "[s]bt-launch"` cross-checked against
`lsof -U | grep <socket-hash>`, `kill -9` THAT pid, then `rm -f <checkout>/project/target/active.json`.
Do not `pkill` by name across the machine: sibling checkouts' servers are healthy and mid-measure,
and reaping them is the same cross-checkout kill the measure scripts had to have removed from them
(see the `pkill scala-cli` note in `scripts/_report.sh`).

Prevention is cheaper than either: never kill a client that is merely slow — `sbt -client` compiling
a cold worktree takes minutes, and the wedge only exists because a kill looked faster.

### M5.7 An unchanged-tree `testFull` is a cache REPLAY — it proves nothing about flakiness

sbt 2 caches test results, and a replay is a perfect forgery of a run: per-project totals, suite
stdout, even per-test timings are printed again. Measured: eight consecutive "testFull" invocations
on an unchanged tree completed in ~8s each and executed NOTHING — established not by reading the
output (indistinguishable) but by a spec's known file side effect keeping its old mtime through all
eight. Under forked tests the replay is the norm, because a forked test task is hermetic enough to
cache.

Two consequences:

- **"N consecutive green runs" of an unchanged tree is ONE run.** Source-edit cache-busting does
  not work either — measured twice: a comment-only edit recompiles and replays (bytecode
  unchanged), and even a NEW classfile carrying a per-run string constant replayed every
  downstream test task. **What works: `testOnly *` at the root** — it aggregates like `test`, so
  the cross-project parallel task shape (the one races need) is preserved, and it bypasses the
  result cache, re-executing everything on every invocation. Verify each iteration anyway by a
  KNOWN SIDE EFFECT (delete it before the run, confirm the run recreated it) — never by the
  output.
- The forgery cuts the other way too: when a test IS flaky, the cached green can mask it until an
  unrelated edit re-executes the suite — the failure then diffs against the edit that exposed it,
  not the one that caused it. Read a surprising test failure with this in mind before blaming the
  commit under test.

### M6. Refuse and COUNT rather than approximate

Three places where the port deliberately carries a number instead of a guess, and each is the right
call:

- 49 dropped `super(args)` on non-throwable parents — padding measured **0 → 55** (C3).
- A raw anonymous class with a body — refused and reported (G10).
- A single-primary encoding with no faithful form — **left as a compile error deliberately**,
  because the compiler is a louder tracker than a silent omission.

A residue comment count (`/* break */ ()`) is itself a measure — do not delete it to make output
tidy.

---

## 8. A DEPENDENT reading its base's published port map

### D1. A TIR symbol's `fullName` is the SAME for every overload — arity is not enough. **263 → 8**

`Symbol.fullName` for a member is `owner#name` with **no parameter list**; every overload is a
distinct `SymId` carrying the same string. Every map, manifest key and `dropMethods` entry, by
contrast, is written `owner#name(P1,P2)`. So a phase that looks a call site up in a published map
has to reconstruct the parameter list, and the obvious discriminator — the call's ARGUMENT COUNT —
is not one:

- `Array#toArray(ArraySupplier)` is `Ported` and `Array#toArray(Class)` is `Dropped`, at the **same
  arity**. Every real library has such a pair, because the portable replacement for a reflective
  overload takes exactly one argument too.
- Measured on Ashley against libGDX core's published map: arity-only selection produced **263**
  findings, of which **118 were `Ambiguous`** and *every* `toArray` and `Array#<init>` site was one.
  The acceptance case itself — `ImmutableArray.toArray(Class)` — came back undecidable.

**What works: the callee symbol's own `info`.** The frontend interns a member symbol with its
`MethodType` (through `PolyType` for a generic method) even when the member was dropped and has no
declaration left, so the erased parameter SIMPLE names are available at the call site and give the
exact manifest-shaped key. `PortMapTransform.preciseKey` does this; arity survives only as the
fallback for a symbol whose `info` a lenient frontend never resolved.

Two smaller rules from the same measurement:

- **No arity match means NO record, not the nearest one.** The first version fell back to the whole
  candidate list, which attributed a 1-argument call to the map's 0-argument entry and reported the
  base's decision about a member nobody had called.
- **The xref records `a.m(x)` twice** — `Call` on the `Apply`, `TermRef` on the `Select` inside it.
  Reporting per usage doubles every finding, and the `Select` half has no argument list, so it
  cannot pick an overload and lands on `Ambiguous` for a call the `Apply` half resolves exactly.
  Collapse to (file, line, enclosing definition) and keep the `Apply`.

*Fix kind: (a) engine — this is a fact about the TIR, not about any library.*

### D2. A dependent's program CONTAINS its base — filter every per-site report by ownership

`resolutionRoots` means the base's Java is parsed, so a dependent's `Program` holds libGDX's 596
units and every call libGDX makes into its own dropped members. Of the 263 findings above, **255
were in libGDX's own files** — sites the dependent's author neither owns nor can fix, and which the
base's own run already reported.

Decide ownership STRUCTURALLY, as §4.56 does for renames: climb the `owner` chain and ask whether it
reaches a type the base's map NAMES. Do not filter on the origin path — that is the same lexical
path comparison §5.4 already documents as broken across a symlinked worktree, and here the phase
does not even know the source root. A symbol that exhausts the climb counts as the dependent's, so
an unresolvable case errs toward reporting.

*Fix kind: (a) engine.*

### D3. A `<synthetic>` origin is not a file — exclude it from any source fingerprint

`PortMap`'s staleness check digests the Java files the map attributes members to. libGDX core has
exactly **one** member whose origin is `<synthetic>`; including it put an unresolvable path in the
file set, and the first dependent run reported the base's map as `Unverified` — a check whose first
real firing was a false positive. `SrcMap.relativise` already leaves `<…>` alone for the same
reason; anything consuming its paths must too.

*Fix kind: (a) engine.*

---

## 9. Asserted, not measured

Clearly separated because nothing below has a number behind it yet.

- **Duplicate injected-runtime definitions will break the Scala.js and Native linkers** when a second
  module is ported. Confirmed by design reasoning; not observed, because only one module exists so
  far. (`LIBRARY-READINESS.md` §1.3.)
- **An enum constant with a field or initializer block in its class body** would be dropped silently
  (T8). Zero sites in the corpus, so the hole is reasoned, not observed.
- **A `StaticForwarderTransform` wrapper whose overloads are not all receiver-first** would be
  rewritten wrongly: members are matched by **name only**. Safe under current policy; worth a guard
  when a second library configures it. (`LIBRARY-READINESS.md`.)
- ~~A typo'd policy key silently no-ops~~ **CLOSED**: `PolicyReport` collects a classified
  `never matched` finding from every parameterised phase, `SubstitutionCheck.dangling` covers the
  drop side, and the migration prints and baselines both. The check has now also FIRED in anger —
  it caught `getName` in the `ClassReflection` forwarder, a key dead since the first draft
  (`policy 1->0` when removed).

### K5. A java class that EXTENDS a JDK collection — CLOSED, and what the shim must get exactly right

`java.util.Collection`, `List`, `Map`, `Set` and `Iterator` are in `CollectionsTransform.typeMap`, so
every *use* of them retypes. The ABSTRACT BASES are not — `java.util.AbstractCollection`,
`AbstractList`, `AbstractMap`, `AbstractSet`. A library that merely *uses* JDK collections is
therefore fine, and one that **inherits** from them comes out half-translated: the class keeps the
JDK parent while every use of it is retyped to a Scala collection, and the two no longer meet.

Measured on simple-graphs (29 files), where three classes do this — `Array`, `NodeCollection`,
`VertexCollection`, all `extends AbstractCollection<T>`. It is **27 of that library's 30 compile
errors**, in three shapes that all trace to the one cause:

| shape | count | why |
|---|---|---|
| `value foreach is not a member of sge.graphs.Array[…]` | 7 | a java enhanced-for over the class emits scala `for (x <- xs)`, which needs `foreach`; the JDK parent supplies `forEach` (capital E) and nothing else |
| `Required: java.util.Collection[?]` / `Required: Buffer[…]` given the other | ~5 | a parameter typed by the JDK base meeting an argument the transform retyped, or the reverse |
| `value stream is not a member of Buffer[…]` | 3 | separate cause — see K6 |

**Do NOT reach for the obvious fix.** Mapping `AbstractCollection` onto a Scala collection base
(`mutable.AbstractBuffer` and friends) is exactly what **§4.5** forbids and for exactly the reasons
recorded there: the Scala collection traits are large and interlocking, they import hundreds of
members that clash with the ported class's own `size`, `isEmpty`, `remove`, and a class that
implements several small Java interfaces cannot satisfy them at once.

The shape that *is* known to work is the one `JavaIterator`/`JavaIterable` already take: a
**standalone abstract class in `balticporter-runtime` with Java's own member arity**, with Scala
interop restored by extension methods rather than by inheritance. `AbstractCollection` is the next
member of that family, not a mapping onto the stdlib.

**CLOSED.** simple-graphs compiles at 0 and its three `extends AbstractCollection<T>` classes
translate. `java.util.Collection` and `java.util.AbstractCollection` both map to the
`balticporter.runtime.JavaCollection` shim — they MUST map to the same family, since java's abstract
base implements the interface — and a scala collection reaching a shim-typed slot is bridged at the
slot by `CollectionsTransform.coerce` (arguments, declarations and assignments alike; arguments alone
left libGDX's `Collection<Object[]> parameters = new ArrayList<>()` broken).

Four things the shim had to get EXACTLY right, every one of them invisible until the last typer error
was gone and `RefChecks` finally ran (§3 — the count rose 1 → 8 at that moment, which is the gate
starting to tell the truth):

| got wrong | what RefChecks said | the rule |
|---|---|---|
| `contains`/`isEmpty`/`remove`/`clear` declared ABSTRACT | `class Array needs to be abstract, since: it has 2 unimplemented members` × 4 classes | mirror `AbstractCollection`'s OWN split: only `iterator()` and `size()` are abstract there |
| `contains(o: Any)`, `remove(o: Any)` | same, for a class that DOES declare `contains(Object)` | scala's `Any` is not java's `Object`; take java's parameter type |
| no `toArray(T[])` at all | `method toArray overrides nothing` × 3 | carry every member java's base has, not the ones that look needed |
| `toArray[T]` (bound `Any`) | `method toArray has a different signature than the overridden declaration` × 3 | java's IMPLICIT type-parameter bound is `Object`; render it |

`add` is CONCRETE and throws `UnsupportedOperationException`, because that is what
`AbstractCollection.add` does — a subclass that does not override it really does reject `add`, and
making it abstract would demand code the source never contained.

The general lesson, and the one that transfers to `AbstractMap`/`AbstractList`/`AbstractSet`: a shim
standing in for a JDK abstract base is not a list of the members the corpus happens to call. It is
that base's OWN abstract/concrete split, member for member, with java's parameter types and java's
type-parameter bounds — and no compile error names any of the four mistakes above until every other
error is gone.

*Fix kind: (a). The types are JDK, the inheritance is ordinary java, and every library that defines
its own collection type hits it — flexmark and liqp both do.*

### K5.5 Several constructors reaching the SAME parent constructor — a SYNTHESISED primary

Scala lets only the PRIMARY constructor reach `super`, so a class whose java constructors each call
`super(...)` with different arguments has no obvious encoding. The engine used to nominate one and
DROP the others' arguments — counted by `OmissionCheck`, but counted is not fixed:

```java
AlgorithmPath()          { super(0, false); }
AlgorithmPath(Node<V> v) { super(v.getIndex() + 1, true); setByBacktracking(v); }
```
emitted `extends Path[V](0, false)` for BOTH, and simple-graphs' shortest path came back size 0
instead of 39. It compiled, and only the test suite noticed.

When every root reaches the SAME parent constructor, the faithful encoding is the one a scala author
writes by hand — a primary taking the super call's own parameters:

```scala
class AlgorithmPath[V](sup$0: Int, sup$1: Boolean) extends Path[V](sup$0, sup$1):
  def this()           = this(0, false)
  def this(v: Node[V]) = { this(v.getIndex() + 1, true); setByBacktracking(v) }
```

Four things measured while getting there, each of which moved a libGDX number the wrong way:

| got wrong | measured |
|---|---|
| promoting a pass-through root when some root takes NO parameters | libGDX **0 → 5** — the no-arg root has nothing to delegate with and emits `this()` against a primary that no longer accepts it |
| picking the FIRST pass-through root rather than the widest | omissions **46 → 50** |
| letting the synthesis run for a JDK-THROWABLE parent | that branch already nominates the widest and is measured (0 → 55 when it guessed) — leave it alone |
| letting either shape reach the other's classes | every ordering tried moved a number; they are disjoint by construction now — a no-arg root means SYNTHESIS, all-paramful-with-a-collision means PROMOTION, anything else keeps the old behaviour |

And one that moved a number the RIGHT way while looking wrong: `OmissionCheck` counts a dropped
`super(args)` by "this root is not the primary", which is false under both new shapes — every root's
call survives. Marking the plan `superExpressed` and skipping those took libGDX from a reported
46 → 50 to **46 → 43**, i.e. three super calls that had been silently dropped are now emitted. A check
derived from a decision must be told when the decision changes, or an improvement reads as a
regression.

*Fix kind: (a). Java's constructor rules against scala's, no library involved.*

### K5.6 A cast that only BECOMES impossible after a retyping

`(Collection<V>) anArrayList` is valid java and the frontend emits it faithfully. `CollectionsTransform`
then maps `Collection` to the runtime shim while leaving the `java.util.List` alone — it came from an
`IntStream` chain, which K6's own rule correctly declines to collapse — and the surviving
`asInstanceOf[JavaCollection[V]]` on an `ArrayList` can never succeed. It COMPILES and throws
`ClassCastException`.

No count moves: it is not an omission, not a portability site, not a signature mismatch. Only the test
suite sees it, which makes it CLAUDE.md §4.4's defect class arriving without a java statement form.

**A phase that retypes must ask what it has done to the CASTS around the types it moved.** Dropping
the cast turns a runtime failure into a compile error on the same line (ENGINE-LIMITS M6), and here it
also let `coerce` see the argument and bridge it properly — the cast had been standing between them.

*Fix kind: (a).*

### K6. `java.util.stream` — the CHAIN collapses; and the two rules that make that safe

**PARTLY CLOSED.** `xs.stream().filter(p).collect(Collectors.toList())` now translates, and the shape
of the fix is the transferable part: a stream chain is not rewritten call-for-call. Scala's
collections carry the operations directly, so `stream()` becomes the receiver AS a scala collection,
`collect(Collectors.toList())` becomes NOTHING, and only the middle operation survives. Map any one
of the three on its own and the result does not type-check.

Two rules were each measured the hard way, and a new backend or a new operation will need both:

- **A rewritten node must be TYPED as what it now emits.** The collapse first kept the java
  `Stream<E>` type, and one call further out `coerce` therefore declined to bridge: `Found:
  Buffer[V] / Required: JavaCollection[V]` — the chain translated and then failed to meet the method
  it fed. Same invariant as `Map.values()`, which claims a `Collection` in the TIR while emitting a
  `scala.collection.Iterable`, and is wrapped for exactly that reason.
- **A stream OPERATION is rewritten only when its receiver is a collection the phase already
  collapsed — never on the method name alone.** `"…".lines()` is a `java.util.stream.Stream` with no
  collection behind it; rewriting its `filter` gave `Found: java.util.stream.Stream[String]`.
  Measured 0 → 1 on libGDX's test port. A chain from a non-collection source is untranslated and
  must fail as such.

Still open, and each needs a different target type rather than more of the same: `Collectors.toSet`
and `Collectors.toMap`. Guessing one would be a silent wrong answer, so they are deliberately
unmapped and fail to compile. Two that WERE on this list now ship in `JavaCollections`:
`Stream.sorted(Comparator)` as `sortedWith` (a copy, with the doc explaining why the name matters)
and `Collectors.toCollection(f)` as `into` (bounded by `Growable`).
`java.util.Collections`' statics are the same story — `unmodifiableCollection` is mapped (its
`Collection<? extends T> -> Collection<T>` widening is load-bearing, not erasable to the identity),
while `unmodifiableList` has no read-only `Buffer` view to map onto and mapping it to the identity
would drop the immutability with a green compile.

`java.util.function` is now exercised: `Predicate` reaches the runtime shim's `removeIf`, and the
shim declares JAVA's signature (`Predicate<? super A>`) rather than `A => Boolean` — because a
ported class OVERRIDES it and scala requires an override's parameter type to match EXACTLY.
Mapping `Predicate` to `Function1` and adapting at each call moves that disagreement rather than
removing it, since it changes the override's parameter type too.

**A `java.util.stream.Stream`-typed SLOT is the one shape the collapse cannot reach, and the refusal
is correct.** Audited as "the collapse keys on the receiver's WRITTEN type rather than its retyped
kind" and **DISPROVED** — recorded because the disproof is what stops it being re-opened:

| what would have to be true for the guard to diverge | measured |
|---|---|
| `recv.tpe` still naming a JAVA collection at the guard | impossible — `StandardTraversal.mapTerm` routes a node's `tpe` and its children through `transformType` BEFORE `transformApply`, and every node the phase mints is typed from an already-mapped one |
| `recv.tpe` naming a scala collection the phase did not introduce | impossible — `kindOf` is keyed only on symbols the phase minted |
| the SOURCE arm keying on the receiver's declared type | it keys on the RESOLVED method's DECLARING type: **13 of 13** receiver spellings (`ArrayDeque`, `TreeSet`, `Queue`, a program class extending `AbstractCollection`, …) resolve to `java.util.Collection#stream` |
| the receiver being a collapsed buffer whose recorded type says otherwise | REACHABLE, through a `Stream`-typed slot — and there `false` is right |

```java
Stream<String> st = f.stream();    // st : Stream, value : Buffer
st.filter(p).collect(toList());    // not collapsed
```

The DECLARATION is what has no translation, not the operation. Making the guard answer `true` here
would rewrite `filter` and leave the `Stream`-typed slot in place — moving the error rather than
closing it. Measured: that emission is **2 compile errors**, so the refusal is loud (M6), never
silent. The general rule is `CLAUDE.md` §4.56's: a phase may only conclude something about a type
from what it did to that type, and this phase did nothing to `java.util.stream.Stream`.

*Fix kind: (b) for the call shapes, on `CollectionsTransform`'s existing rewrite table; the chain
collapse and both rules above are (a). The `Stream`-typed slot is (a) and unbuilt — it needs the
stream family retyped, not a wider guard.*

### K7. A java enhanced-for BINDING may be declared at a supertype, and the port dropped it

`for (Object e : collection)` over a `Collection<?>` is a DECLARATION: java resolves every use of `e`
in the body against `Object`. Scala's `for (e <- xs)` binds at the ITERABLE's element type, which for
a wildcard is an unusable capture — so any use fails with `Found: ?1.CAP`. No retyping at the
collection can fix it; the loss is at the binding.

Faithful translation re-binds: `for (e$e <- xs) { val e: T = e$e.asInstanceOf[T]; … }`. The cast is
sound wherever it fires, because java permits only a WIDENING here.

Two things measured while doing it:

- **The gate must be a PROVABLE difference.** Treating an unreadable element type as "differs" would
  put a cast on every for-each in the corpus to fix the handful that need one.
- **Derive the fresh name from the RAW name and escape THAT.** Appending to the escaped form gives
  `` `object`$e ``, which is not an identifier: 0 → 3 on libGDX main, as an E040 syntax error in a
  file that had compiled for weeks. A suffixed keyword needs no escape — but only because `esc` is
  applied to the whole name.

27 members of libGDX main changed emitted text; 217/221 tests still pass, so the alias is behaviour-
preserving where it fired.

*Fix kind: (a) — java's for-each, scala's for-comprehension, no library involved.*

### K8. `Type::method` is TWO java forms sharing one syntax

A method reference on a TYPE is a qualified name (`Type.method`) only when the method is STATIC. For
an INSTANCE method it is an UNBOUND reference and the receiver becomes the function's first
parameter: `Edge<V>::getWeight` means `(self: Edge[V]) => self.getWeight()`. Emitted as a name it is
`sge.graphs.Edge[V].getWeight`, which is not even a member access — measured as `value Edge is not a
member of sge.graphs`, i.e. an error that points at the PACKAGE and says nothing about method
references.

Distinguish on `Flags.isStatic`, and take the synthesised lambda's arity from the method's own
`MethodType` so a multi-parameter reference (`String::compareTo` as a `Comparator`) works too.

*Fix kind: (a).*
