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
    `api` / `engine` / `frontend-spoon`, unparameterised.
  - **(b) configure an existing phase** — the mechanism exists; supply your library's values.
  - **(c) write a library-specific rule** — plug a rule into your own migration program. It does not
    go in the engine.
  Classifying an error is the expensive part of a new library's first wall (`CLAUDE.md` §4.45); an
  entry that already tells you the kind has done most of the work.
- **Worked examples name libGDX constructs.** That is deliberate and permitted (`CLAUDE.md` §1): the
  example documents *why* a general rule exists. It drives nothing. Substitute your own library's
  shape freely.
- **The measurements live in `PROGRESS.md`**, in the section for the library that produced them. This
  file holds the rule; that one holds the per-site diagnosis, the trajectory and the counts in context.

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
`DESIGN.md` §6's marker rather than another gate.

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

### G20. A STATIC member sees NONE of its class's type parameters — carry it in the FRAME, not a flag

CLOSED. G15's rule ("gate on the BARRIER-AWARE frame, not on name resolution") was right and its
implementation was one level too shallow: the gate was `inStatic`, a flag set per EXECUTABLE. It is
reset the moment an anonymous class inside a static initialiser declares an INSTANCE method — which
is what an anonymous class is made of — so the enclosing class's type parameters became reachable
again, the raw fill reconstructed them, and the emitter put the whole thing in the COMPANION
OBJECT, where they are not in scope.

The idiom that finds it is a per-class object pool, and the java is written RAW for exactly the
reason this rule states:

```java
private static class Wrapper<T> implements Pool.Poolable {
  private static final Pool<Wrapper> pool = new Pool<Wrapper>() {
    protected Wrapper newObject() { return new Wrapper(); }   // raw — `T` is out of scope
  };
}
```

**3 × `Not found: type T`** on gdx-vfx's `PrioritizedArray`, in one field. The scope now lives in the
type-parameter frame: a static `execDef` starts from an EMPTY accessible map plus its OWN formals (a
static generic method keeps its `E`), and a static `fieldDef` pushes an empty one around its
initialiser. Everything lexically inside inherits it with no flag to reset — including the anonymous
class, whose translation (`anonClass`) never touches the frame at all.

Two things confirmed on the way out, both G2's: `?` round-trips across the override (the emitted
`override def newObject(): Wrapper[?]` satisfies `Pool[Wrapper[?]]`), and `ctorTpe` already drops a
wildcard argument list so `new Wrapper()` lets scala infer rather than emitting an illegal
`new Wrapper[?]()`.

**0 members moved** on libGDX core, libGDX test, Ashley, anim8, simple-graphs, noise4j and jbump —
no other corpus library writes a generic static in a generic class. Spec:
`StaticTypeParamScopeSpec`, four directions including "a static METHOD keeps its own parameters".

*Fix kind: (a) engine — frontend. Built.*

### G21. A RAW result read through an ERASED RECEIVER must be TYPED as what it emits

CLOSED, and it is §0's root cause with a name. G11's erased-receiver view is correct and stays: a
wildcard receiver whose callee depends on its type variables is called through java's own erased
view. But the CALL's recorded type was still Spoon's, and Spoon reports the raw declared result —
which the CALLER's name-directed fill then renders in the caller's variables:

| | |
|---|---|
| emitted | `pool.obtain().asInstanceOf[W[Object]].init(item)` — a `W[Object]` |
| node `tpe` | `W[T]` — the raw `W` rendered through the caller's fill |

Nothing about the expression is wrong; the type recorded ON it is. The rule that pays for it is
`knownReceiverArgs`, which compares the callee's substituted formal with the argument's type to
decide java's unchecked conversion: it found `W[T]` against `W[T]`, concluded there was nothing to
convert, and emitted no cast for a conversion java really performed.
`Found: Wrapper[Object] / Required: Wrapper[T]` — gdx-vfx's last compile error.

Two halves, both narrow, and both needed:

- `erasedRecvResult` RE-TYPES the node (emitting no text) to the erased instantiation when the
  DECLARED result is a raw generic use and the receiver was erased. This is the case `substFormal`
  answers `None` for BY DESIGN — a raw use has nothing to substitute — so the existing path fell
  through to "leave it alone" rather than to "say what it is".
- `knownReceiverArgs` gains the ERASED direction of `uncheckedFrom`: the argument is an
  `Object`-parameterised view of exactly the slot's type. `uncheckedFrom` demands the same type
  CONSTRUCTOR, the same arity and every differing argument to be `Object` or a wildcard, which is
  the shape of an erased or raw use and of nothing else.

Measured alone, the guard extension was **INERT** — the retype is what makes it reachable, and that
is the informative half (M4): the plausible fix was the guard, and the guard was not the problem.
**0 members moved** on every other corpus port. Spec: `ErasedReceiverResultSpec`, negative-tested by
gating the retype off.

*Fix kind: (a) engine. Built.*

---

## 2. Constructors

### C1. Never promote a paramful constructor to the primary without a WHOLE-PROGRAM check — +14

It removes the class's nilary construction path, and every subclass whose `extends` clause passes no
arguments then fails (`FillViewport extends ScalingViewport`, `FloatAttribute extends Attribute`).
The promotion must be withheld at a fixpoint (`CtorFunnel.Plans`).

**DELETING the fixpoint for SYNTHESISED plans was measured and is a dead end: 0 -> 4 compile errors,
omissions 180 -> 196.** `DESIGN.md` §8.2 argued that a synthesis removes nothing — every java
constructor survives as a `def this`, so `extends C` reaches the nilary one — and therefore needs no
withholding. The premise is true and the conclusion does not follow. The fixpoint's TRIGGER is not
"java wrote an argument-free `extends`": it is `needNilary`, computed from the SUBCLASS's plan, and a
subclass whose own plan carries no super arguments emits `extends P` **bare** even where java wrote
`super(args)`. A synthesised class with no nilary java constructor is then reached argument-free by
a subclass java never reached that way — measured as four `E134 None of the overloaded alternatives
of constructor BatchTiledMapRenderer` on libGDX core. The guard stays, gated on
`reachableArgumentFree` (does a NILARY SECONDARY survive), which a synthesis satisfies whenever java
declared one and a promotion never can.

What WAS wrong is the **fallback**. Withholding dropped straight to `nilaryPlan`, which for a class
the synthesis had claimed threw away the promotion it would otherwise have had, every root's
`super(args)` with it: dropped supers **30 -> 79**. A withheld synthesis now falls back to
`plan0(…, synthesis = false)` — the plan this class would have had without the synthesis — and the
count returns to 30.

**…and that first attempt is INERT as the predicates now stand — re-measured, 0 hits.** Its filter
wants a plan that is nominated and NOT paramful, which for several roots means `several.find(nilary)`
found a NILARY ROOT — and a class with a nilary constructor is one `reachableArgumentFree` never
withholds, so the two conditions exclude each other. Probed over libGDX core: of **10 withheld
classes the filter passes for none** (`ScalingViewport`, `Pool`, `ObjectSet`, `ObjectMap`,
`TextField`, `Table`, `BatchTiledMapRenderer`, `TextureMapObject`, `GLFrameBuffer$GLFrameBufferBuilder`,
`BitmapFont`), and deleting the attempt outright leaves the lane **byte-for-byte identical** —
0 members changed, omissions 65, errors 0. The 30 -> 79 above was real when it was taken; what
holds the count at 30 today is `nilaryPlan` plus the guard above it, not this line. Keep the
attempt — it is the correct order and costs nothing — and do NOT cite it as the thing that fixed
anything. `inert` is a result (see "How to read an entry").

**Pinned by `SyntheticPrimaryWithholdingSpec`**, which is the `BatchTiledMapRenderer` shape reduced
to three classes: a synthesis with no nilary java constructor, a wall subclass whose bare `extends`
is the fact `needNilary` reads, and — the other direction, without which the guard could be a
blanket refusal — the same synthesis with a subclass that passes arguments up, which must survive.

*Fix kind: (a).*

### C1.5. `primary.isEmpty` is NOT "nothing was nominated" — 109 escaping paths came back

A SYNTHESISED plan has no `primary` either, because no java constructor backs it. `Plans` ran
`nilaryPlan` over every class whose plan had an empty `primary`, so it OVERWROTE every synthesis in
its own domain — a class with no `super(args)` anywhere is exactly what both fire on — and the
promotion came back with its escaping body. libGDX core: promoted-body escapes **95** with the
confusion, **31** with `!p.isSynthesised` added to the guard; `CharArray` alone was 9 escaping paths
that the synthesis had already removed. Nothing else moved and the port compiled at 0 either way.

The general form is `Plan.isSynthesised`, and every predicate that asks "is this a synthesised
primary" must go through it rather than through `synthetic.nonEmpty`: a class disambiguated by the
MARKER ALONE has an empty slot list, and reading `synthetic.nonEmpty` there emitted a primary whose
parameter list was empty while every secondary wrote `this((null: C.Funnel))` against scala's
implicit nilary primary.

**Both halves are pinned by `SyntheticPrimaryWithholdingSpec`**, each verified to fail against its
own guard reverted: a class with no `super(args)` anywhere whose synthesis `nilaryPlan` must not
claim, and a marker-only class (`Two()` beside `Two(int)`, parent `Object`, nothing hoistable) whose
primary must come out `protected (ctor$: Two.Funnel)` and not `protected ()`.

*Fix kind: (a).*

### C1.6. A `val` derived from a WHOLE-PROGRAM write count does not survive a DEPENDENT — 7 -> 23

A1's condition — a field written exactly once, in the primary, from a parameter — is `val`-eligibility,
and the write count is necessarily over THIS RUN's program. A dependent module compiled against the
emitted base is not in it. Built and measured: with `val` decided by the write count alone, libGDX
core emitted 20 `val` field slots at 0 errors and **gdx-gltf went 7 -> 23**, every new error `E052
Reassignment to val` on a libGDX core field gltf writes (`ShaderProgram.vertexShader`,
`ShaderProgram.fragmentShader`, `PBRFloatAttribute.value`).

No bigger scan fixes it — the base run cannot see a module that does not exist yet — so the condition
is narrowed by a JAVA fact instead: a field java declared `final` cannot be written after
construction at all, and one java declared `private` cannot be written from outside the compilation
this run holds. Neither can drift. libGDX core: 5 `val` of 53 hoisted slots under the narrow rule
against 20 under the wide one, and the 15 difference is exactly the class of field a dependent may
legitimately assign. Widening it needs §8.3's published base surface, not a better count.

**And the count must include THE EMISSION'S OWN writes, not only java's — 0 -> 4.** A REPLAY
(`Plans.replayFor`) lifts a parent constructor's statements into a subclass, so a parent field the
funnel hoisted into a slot and saw written exactly once in the java is written again, once per
replaying subclass, in code no source scan can see. Ashley's `EntitySystemMock.updates` is
java-`private` and assigned by one constructor — `val`-eligible by every test over the java — and its
two mocks replay `super(updates)` as `this.updates = updates`: **`E052 Reassignment to val` × 4**, in
the TEST source set, on a run whose every check count was unchanged. The `val`/`var` decision
therefore runs LAST, after `replays` is built, and adds the replayed writes to the program's.

**Both halves are pinned by `SyntheticPrimarySlotsSpec`, and one of them was not.** The REPLAY half
has been pinned since it was measured (`MockA`, whose two replays force `updates` to a `var`); the
`final || private` NARROWING was pinned by no fixture at all — reverting the guard to `true` left the
whole suite green, and the only thing that would have said so is the gltf lane, three lanes
downstream of the change. `Loose` is the negative it needed: a field java declared package-private
and non-final, written exactly once in the leading run, which is `val`-eligible by the write count
and by nothing else, and which must still bind `var loose: scala.Int = f$loose`. A guard nothing
fails against is a guard the next agent deletes.

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
question. It has since been measured and is **C7**: refusing the promotion costs 0 -> 41 compile
errors, so the emission stands and the divergence is COUNTED
(`OmissionCheck.promotedBodyOnEveryPath`). Nothing about it moved `supersedes`.

*Fix kind: (a), at the promotion — NOT at `supersedes`.*

### C7. A PROMOTED constructor's body runs on EVERY construction path — refusing it costs 0 -> 41

`CtorFunnel` nominates one Java constructor as Scala's primary and `TirEmitter.lowerCtors`
substitutes its body for it in the CLASS BODY. A Scala class body runs on every construction path,
because every secondary constructor's first statement is a `this(...)` that reaches the primary.
Java had no such rule: two constructors that do not delegate to each other run disjoint bodies. So
promoting one of several roots runs its statements where Java ran nothing. C6's probe, run both
ways:

| | `new Base(5)` | then `new Base()` |
|---|---|---|
| javac | `n=5 bumps=0` | `n=-1 bumps=1` |
| the port | `n=5 bumps=1` | `n=-1 bumps=2` |

**LARGELY RETIRED — the fix was not to refuse the promotion but to stop promoting.** A2's synthesised
`protected` primary promotes no java constructor at all, so there is no body to escape; the shape now
fires wherever every root reaches ONE parent constructor, the implicit nilary `super()` included,
which is the whole domain the escapes lived in. Measured on libGDX core: promoted-body escapes
**140 -> 31**, dropped `super(args)` unchanged at 30, omissions **177 -> 67**, compile 0 -> 0.
`Material` — this entry's worked example, whose promoted nilary body bumped a static id counter on
every construction — is repaired outright, and `CtorFunnelPromotedBodySpec`'s own C6 probe had to be
given a WALL parent to keep reproducing the divergence at all. What remains is the residue below:
wall classes, JDK-throwable parents, and the UNIQUE-ROOT class whose promotion the C1 fixpoint
withholds (`ObjectMap`, 3 paths — one root, so the synthesis's two-root condition excludes it; that
is the largest single item left).

**AND A FOURTH SOURCE, NOW CLOSED: the COLLAPSE re-created this entry's own defect. omissions
noise4j 3 -> 0, libGDX core 67 -> 65.** C8's disambiguation tries COLLAPSE before the marker,
because a collapse promotes a real pass-through root and leaves the class byte-for-byte as it was.
But a promotion is a promotion: its body becomes the class body, and where another root does not
delegate to it, the body runs on a path java never ran it on — exactly what the synthesis was built
to stop. Two classes in the corpus: noise4j's `Object2dArray`
(`this.array = getArray(width * height)` on all three paths — that port's ENTIRE omissions residue)
and libGDX's `Dialog` (`initialize()` on two paths, which is `Button`'s observable defect in
miniature and was NOT predicted). The fix is one condition on the collapse — take it only where the
promotion has no escaping path, and otherwise fall through to the marker, which promotes nothing.

Two things about HOW it is asked, both of which are the rule rather than the detail. It goes through
`CtorFunnel.escapesOf`, the same function `OmissionCheck.promotedBodyOnEveryPath` counts with,
prefix strip included — a second predicate written at the nomination is exactly the
count-disagrees-with-emission shape this entry warns about at `droppedSuperArgs`. And it is asked of
a CANDIDATE plan, before the nomination commits, which is why `escapesOf`/`escapingRootsOf`/
`residualBodyOf` are parameterised on `(primary, primaryBody)` rather than on a decided `Plan`.
Cost: 2 classes gain a companion marker, 5 member digests on libGDX core and 8 on noise4j, every
other port and every other count unmoved.

**Do not refuse the promotion** where one still happens. Dropping every escaping plan to `Plan.none`
measured **0 -> 41 compile errors** on libGDX core, every one an `E120 Conflicting definitions`: the
refused class emits a `def this()` beside Scala's implicit nilary primary, which is the exact clash
shapes 2 and 6 exist to prevent. Promoting a *different* constructor only moves the escape.

**CORRECTED, 2026-07-31.** This entry used to add *"and a synthesised no-op primary cannot help
either — a subclass's `extends C` invokes C's PRIMARY, so a body Java ran from the implicit
`super()` has to be there"*. **That sentence is false**, and it was the same false claim
`TirEmitter` carried beside the synthesis it was keeping public. Compiled and run against scalac
3.8.4: a `private` primary's SECONDARIES are reachable from an `extends` clause **in another
package** (`class D extends p.C("hello")`, `class E extends p.C()`), and a `protected` primary is
reachable **directly** from a subclass's `extends` clause in another package and from an anonymous
`new G(3, false) {}`. So a class MAY have a non-public primary that no `extends` clause names, with
every Java constructor surviving as a `def this` the `extends` clauses reach — which is precisely
the encoding under which no Java body becomes the class body and there is nothing to escape.
`DESIGN.md` §8.2 is that design. The 0 -> 41 and 0 -> 35 measurements below stand exactly as
written: both are the cost of refusing a *promotion*, and they say nothing about an encoding that
promotes nothing. Note the boundary that makes `protected` rather than `private` the answer:
`private` is CLASS-private in Scala, so even a SAME-package subclass sees only the secondaries
(`too many arguments for constructor A in class A: (): g.A`).

Corpus reach, by the structural test (a constructor escapes iff no chain of leading `this(...)`
delegations — at any arity — reaches the promoted one): **61 classes, 160 constructor paths** in the
units the ports EMIT — libGDX core 59/156, simple-graphs 2/4, Ashley 0/0, libGDX's own suite 0/0. Of
libGDX core's 771 promotions, 323 have a non-empty body and 59 of those escape.

Ashley's zero is worth one line, because it looks like a miss and is not. `EntitySystem` escapes in
the plan the ASHLEY-TEST run computes — its test subclasses reach it with an argument-free `extends`,
so the fixpoint withholds the paramful promotion and `nilaryPlan` takes over — and does not in the
plan the ASHLEY run computes, which is the run that EMITS the class. Two ports legitimately plan the
same class differently; only the owning one emits, and B7's unit filter is what keeps the finding
with the code.

Most are cost, not divergence: the promoted body writes fields the other constructor overwrites, or
allocates a backing array it discards (`Array`, `IntArray`, `ObjectMap` — C5's declared replay cost
in its promotion form). Three in the corpus are observable, and they are why this is counted rather
than tolerated:

| class | what runs twice, or where Java ran nothing |
|---|---|
| `Material` | `Material()` is `this("mtl" + (++counter))` — the port bumps the STATIC counter on every construction, so every later generated id is wrong |
| `Button` | `initialize()` runs again on 8 of 10 paths, adding a SECOND `ClickListener`; each click then calls `setChecked` twice and the button never changes state |
| `Table` | `obtainCell()` takes a `Cell` from the static `cellPool` on every path — one is leaked per construction, and `Button extends Table` |

A "restrict the promotion to empty-ish bodies" rule is the 41 errors.

**PREFIX-STRIPPING SHIPPED** (was "not worth an emission change"; the reach was re-measured and the
earlier estimate was one class low). Where an escaping ROOT's own body literally BEGINS with the
promoted body, the class body runs the prefix, `this(…)` returns, and the residual runs — the same
statements, in the same order, once each. Nothing is approximated. `CtorFunnel.Plans.residualBody`
is the one function: `TirEmitter.ctorBody` emits what it returns and `promotionEscapes` SUBTRACTS
exactly what it returns, so the emission and the count cannot disagree about which paths still
duplicate. The comparison is over `TirPrinter.Style.canonical`, never tree equality — two
occurrences of `initialize()` are two source positions and `==` is false for every pair this is
meant to find.

Measured on libGDX core: **omissions 193 -> 177**, 16 construction paths repaired across 10 classes,
compile still 0. The classes and their repaired paths:

| class | paths |
|---|---|
| `Button` | 4 of 10 — `this.initialize()` now appears ONCE in the file, in the class body |
| `FloatFrameBuffer`, `ModelInfluencer$Random`, `ParticleControllerInfluencer$Random` | 2 each |
| `ParticleEmitter`, `ResourceData$SaveData`, `DynamicsModifier$Angular`, `DynamicsModifier$Strength`, `PrimitiveSpawnShapeValue`, `WeightMeshSpawnShapeValue` | 1 each |

`Button` is REACHED, contrary to this entry's earlier reading of it — 4 of its 10 paths lose the
duplicate `initialize()` and the second `ClickListener` with it. The other 6 never called
`initialize()` in java at all, so there is nothing to strip and the divergence there is still
counted, correctly.

What it does NOT reach is `Material` and `Table`, both shape 6 (`promoted-nilary`), and both
observable (`Material` bumps a static id counter, `Table` leaks a pooled `Cell`).

**A TARGETED refusal for exactly that shape was measured and is a dead end too: 0 -> 35.** Demoting
to `Plan.none` only the shape-6 plans whose promoted body is non-empty — not the blanket 41 — costs
**35 `E120 Conflicting definitions`** on libGDX core and still leaves 65 escaping paths (omissions
177 -> 65). So the targeted version buys 112 of 177 divergences for 35 compile errors, which is the
same trade as the blanket one at 85% of the price: shape 6 exists precisely because a class with
several roots and no `super(args)` needs the nilary promotion to stop `def this()` clashing with
scala's implicit primary, and refusing it re-creates the clash it was invented to remove. Do not
re-derive this; the experiment was a `DebugFlags`-gated demotion in `Plans.plans`, one run, and it
is not in the tree.

For those classes the honest outcome is still M6's: emission unchanged, divergence reported.
`OmissionCheck.promotedBodyOnEveryPath` derives it from `CtorFunnel.Plans.promotionEscapes`, which
reads the same `Plan.primaryBody` the emitter inlines — libGDX core omissions **37 -> 193** when the
check arrived (`members.tsv` byte-identical, 0 members moved), **193 -> 177** when the strip shipped.

Note also what this does NOT cover, and is a second question: a SUBCLASS reaching a promoted
paramful root. `extends C(args)` can only invoke C's primary, so a Java `super(args)` that targeted a
different root of C constructs the parent through the promoted one. That is C3's padding domain and
is counted by `droppedSuperArgs` where the delegation declines.

*Fix kind: (a) — and the (a) is "count it", as in C3.*

### C8. A SYNTHESISED primary is SHADOWED by a narrower real constructor — the test is APPLICABILITY, not signature equality — **0 -> 2**

A synthesised primary takes the PARENT constructor's formals; every Java constructor becomes a
`def this` whose body starts `this(<its own super arguments>)`. The obvious question — "does a real
constructor already have this signature?" — is the wrong one, and asking only it measured **0 -> 2
compile errors** on libGDX core the first time the synthesis was widened past a nilary root.

Scalac does not compare the delegation against the primary's signature. It resolves an OVERLOAD:
every constructor of the class is a candidate, applicability comes first, most-specific decides. So a
real constructor whose parameters are *narrower* than the parent's formals wins the call even though
its signature equals nothing:

```scala
class DistanceFieldFontCache protected (sup$0: BitmapFont, sup$1: Boolean)
    extends BitmapFontCache(sup$0, sup$1):
  def this(font: DistanceFieldFont)                   = this(font, font.usesIntegerPositions())
  def this(font: DistanceFieldFont, integer: Boolean) = this(font, integer)   // ITSELF
```

`DistanceFieldFont <: BitmapFont`, so the second constructor is applicable to `(font, integer)` and
strictly more specific than the primary: it delegates to **itself**. The first then resolves to the
second, which is declared below it — `secondary constructor must call a preceding constructor`.
Note what each half costs: the second error is a compiler diagnostic, and the first would have been
an infinite recursion at construction time had the declaration order gone the other way.

**The predicate is therefore per-ROOT and about the ARGUMENTS the emitter will write**, not about
the slot list: refuse (or disambiguate) when any real constructor of the class is applicable to some
root's delegation argument list — same arity, and every argument type assignable to the
corresponding parameter. Signature equality and erasure-equality are both special cases of it. An
unknown type on either side counts as assignable, because refusing the synthesis is the safe answer
and pretending to know is not.

Erasure equality is still the right test for the OTHER direction — two DECLARATIONS that cannot
coexist (`E120 … have the same type after erasure`, and `private` does not separate them) — so a
widening needs both. `DESIGN.md` §8.2 now states both.

**FIXED — the marker parameter shipped, and this entry is now the reason it exists rather than a
warning.** A final parameter of a per-class companion-`protected` marker type changes the primary's
ARITY, which removes it from every delegation's candidate set at once and answers the erasure half
in the same stroke. Attempt order is COLLAPSE first (a pass-through root whose parameters ARE the
slots is promoted and nothing is synthesised, so those classes stay byte-for-byte as they were)
**— but only where that promotion has NO ESCAPING PATH, which is a correction, see C7 —** then the
marker. Measured on libGDX core: omissions **177 -> 176**, the one removed being
`DistanceFieldFontCache`'s two discarded arguments — this entry's own worked example — with compile
still 0 and 6 classes gaining a marker (the five tiled map loaders plus `DistanceFieldFontCache`).

**There is no second applicability test after the marker, and that absence is a CONSEQUENCE rather
than a residue.** The design asked for one — refuse where even the disambiguated primary is shadowed
— and it has no cases, because the marker's argument is ASCRIBED: the disambiguated delegation
writes `(null: C.Funnel)`, at a type the engine minted for this class alone (`TirEmitter.markerArg`).
No real constructor declares a parameter of it, and one declaring `Object` in that position is
applicable but strictly LESS specific, so it can never win the resolution. A bare `null` would have
been applicable to everything — which is how the predicate was first written, and it refused the
synthesis for every class that also declared a one-argument constructor. `CtorFunnel.syntheticPrimary`
therefore asks the applicability question ONCE, about the BARE delegation, and the ascription answers
the disambiguated one by construction (`DESIGN.md` §8.2, as-built item 3).

*Fix kind: (a).*

### C9. A companion-`private` marker type CANNOT appear in a `protected` primary's signature

`DESIGN.md` §8.2's disambiguator is "a final parameter of a companion-private marker type", validated
against a `private` primary. It does not compose with the same section's `protected` primary:

```
non-private constructor C in class C refers to private class Funnel
in its type signature (n: Int, ctor: e2.e4.C.Funnel): e2.e4.C
```

Scala requires every type in a member's signature to be at least as visible as the member. Measured
against scalac 3.8.4, the marker must be **`protected` in the companion** — which compiles, runs, and
is reachable from a subclass's `extends` clause **in another package** (`class D extends n1.C(7,
null)` where `object C { protected final class Funnel }`). A public marker with a private constructor
also works and is strictly worse: it publishes a name into the API for no reader's benefit.

Recorded because the two halves of §8.2 were validated in separate probes and only their combination
fails, which is exactly the shape that survives a design review.

**Re-measured on the engine's OWN emitted text when the marker shipped**, which is the only version
of this measurement that proves anything about the port: the emitted `class Marked protected (sup$0:
scala.Int, sup$1: scala.Boolean, ctor$: Marked.Funnel)` compiles, runs, and is reached from another
package both at the primary (`extends demo.DCache(new demo.DFont, true, null)`) and at a secondary
(`extends demo.DCache(new demo.DFont)`); the same text with `protected final class Funnel` replaced
by `private final class Funnel` is **one error per disambiguated class**, verbatim as above.
`SyntheticPrimaryDisambiguationSpec` pins the emitter's half.

*Fix kind: (a).*

### C10. `uninitialized` REPLACES THE CAST and nothing else — keyed on the fallback, 2,466 vs 1,184

**Title, for renumbering: "the field placeholder replaces the cast, not every uninitialised
field".** CLOSED. (a) engine.

A field the constructor funnel could not hoist into a slot keeps a `var f: T = <blank>`, and
`scala.compiletime.uninitialized` is scala's own word for the JVM default — exactly what java put
there, and strictly better than the `null.asInstanceOf[T]` it replaces, which is a CAST in a
position where nothing is being cast, on a value that is not of the type it claims.

**Applied to every uninitialised field it is WORSE, and the damage is invisible.** `defaultFor`
answers honestly for every type that STATES a default — `0`/`false` for a primitive, and `null` for
the nullability phase's `T | scala.Null`, which is the union that phase exists to introduce.
Written unconditionally, the substitution silently took that default back off
(`var parent: demo.Actor | scala.Null = null` became `= uninitialized`), re-imposing a placeholder on
the one shape the port had just retired it from. Nothing reports it: the output compiles, no check
count moves, and only `NullabilitySpec` asserting BOTH halves of its rule caught it. So the
substitution is keyed on `defaultFor`'s FALLBACK — the `.asInstanceOf[` rendering — and not on "this
field has no initialiser". libGDX core: **1,184 placeholders keyed, against 2,466 unkeyed**
(`a85d8872`).

Two gates ride with it, both measured: it is emitted ONLY for a field of a CLASS, because scalac's
rule is "`uninitialized` can only be used as the right hand side of a mutable FIELD definition" and
the same function renders local `var`s — **0 -> 3 compile errors** on libGDX core, every one that
message — and the test is STRUCTURAL (the symbol's owner is a class, not a method), never the shape
of the type.

*Fix kind: (a).*

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

### T8. Enum constants with class bodies — FIELDS closed, initializer blocks still open

The shape works: a Java enum whose constants carry class bodies emits as a `sealed abstract class`
plus one `case object` per constant, with the per-constant overrides in the object's body. noise4j
is the first corpus library to have any (three independent ones — `GenerationMode`,
`DefaultRoomType`, `Direction`), and confirmed it.

**The FIELD half was the predicted hole, and it fired.** This entry used to say a field in a constant
body "would be dropped silently, with no omission finding … zero sites in libGDX core". noise4j has
two: `DefaultRoomType.CASTLE { public static final int MIN_SIZE = 7, MIN_TOWER = 3; … }` and
`CROSS { public static final int MIN_SIZE = 3; … }`, read UNQUALIFIED by the same constant's own
methods. JLS 8.1.3 permits statics in an anonymous class body when they are constant variables, and
that is exactly the form a library uses to keep a magic number beside the constant that needs it.
Cost: **4 of the port's 6 errors** — and the prediction about invisibility held, because the
omissions check counts what the TIR CARRIES and these never reached it.

The fix is a frontend harvest, not an emitter change: a Scala `case object`'s body IS the constant's
scope, so `SpoonTir.enumCase` now collects `CtField` alongside `CtMethod` and the field emits as an
ordinary member (`inline val MIN_SIZE = 7`, through the same `static final` constant path as
anywhere else). **0 members changed** in libGDX, libGDX-test, Ashley, Ashley-test, simple-graphs and
its suite — a body with no field harvests exactly what it did before. Spec: `EnumConstantBodySpec`
in `testkit`, two positive and two negative.

**Still open, and still unobserved:** an *instance-initializer block* in a constant body, and a
*nested type* in one, are both dropped with no finding. Zero sites across four corpus libraries. If
you hit one, the shape of the fix is the same one line — `enumCase` mirrors `anonClass`, which
already handles the block case — and `anonClass`'s `dropped` list is the model for reporting what is
left.

*Fix kind: (a). Field half BUILT; initializer-block and nested-type halves unbuilt.*

### T9. A method-LOCAL named class is refused by the frontend outright

`class Holder { … }` written as a STATEMENT inside a method body — Java's local class, the named
sibling of the anonymous class — reaches `SpoonTir.stmtKind` as a `CtClassImpl` and is refused:
`unsupported construct: statement CtClassImpl`. Not dropped silently; the whole unit fails to
translate, which is the right direction but is the whole story.

**Zero sites in the corpus**, across libGDX core (604), libGDX test (29), Ashley (39), simple-graphs
(36) and jbump (19) — which is why it has never cost anything and why it is recorded rather than
built. It surfaced only when a spec for the captured-local rename (`CLAUDE.md` §4.55's fourth face)
reached for a local class as the second shape that shadows a capture; the pass itself is indifferent
between the two, because it reads `Tree.ClassDef` and `Tree.AnonClass` through one traversal, so it
will cover local classes the day the frontend produces them.

Java code that uses the form at all tends to use it a lot (parser and visitor code especially), so a
library that hits this hits it as a wall rather than as a residue. Budget it as frontend work, not as
a translation rule: the TIR already has the node.

*Fix kind: (a), unbuilt — frontend only.*

### T10. A java ENUM CONSTRUCTOR has a BODY, and it runs. **6 libGDX sides silently broken, 0 errors**

CLOSED. Recorded because the shape of the failure is the one this file exists for: it moved **no
number at all**. The enum lowering kept the constructor's PARAMETERS — a `case object` has to pass
its arguments somewhere — and dropped the constructor itself, so every field the body assigned
stayed at its declared default. The port compiled with zero errors, every check count was unchanged,
and no test covered the members.

libGDX `Cubemap.CubemapSide` is the worked example: its constructor builds `up` and `direction` from
six float parameters, so all six sides shipped with `up == null` and `getUp(out)` threw. It was found
by porting anim8-gdx three libraries later, whose `Dithered.DitherAlgorithm` assigns `legibleName`
the same way — `toString()` returned null for all 22 constants — and only because the SAME
constructor tripped T11 below, which is a compile error.

Two limits deliberately kept, so nobody re-opens them as bugs:

- a PURE self-assignment (`this.glEnum = glEnum`) is dropped, because the parameter promotion
  already performs it. That is four of libGDX's five enums, and re-emitting it would be churn; only
  that exact shape, so anything that computes (`new Vector3(upX, …)`) survives.
- an OVERLOADED enum constructor is left alone. A `case object` can reach only one primary, so that
  java shape is inexpressible here whatever is done with the body, and attributing one overload's
  body to every constant would be worse than leaving it out. **Zero sites across four libraries.**

`CtorFunnel` is deliberately not consulted for an enum, and this is the measured part: it plans
nothing there, `Plan.primaryParams` comes back EMPTY, and routing the lowering through it deleted
the whole parameter list.

*Fix kind: (a) engine. Built; `EnumCtorBodySpec`.*

### T11. A PROMOTED enum constructor parameter IS a member — `name` collides with `Enum.name()`

CLOSED. The synthesised `Enum.name()` was already skipped when the enum declared a `name` member,
and the guard read only the BODY. `CLAUDE.md` §4.55's rule applies here exactly as it does to an
ordinary class — *count what the constructor funnel PROMOTES* — and the enum lowering renders every
parameter as a `var`, so a `String name` parameter produced both `var name` and `def name()`:

```
E120 Conflicting definitions:
  var name: String in class DitherAlgorithm and
  def name(): String in class DitherAlgorithm
```

Java never has to choose: `Enum.name()` is FINAL there, so no enum can declare the method, and a
constructor parameter is not a member at all. Note the SEMANTIC caveat that comes with the skip and
was accepted rather than overlooked — the port's `name` is then the constructor argument, where
java's `Enum.name()` is the constant's identifier. The two differ whenever the argument is a display
string (anim8: `"Wren"` against `WREN`), and `valueOf` still keys on the identifier. Renaming the
parameter instead would need a §4.55 pass that can see an EMITTER-synthesised member, which no phase
can today.

**One error on anim8, the last one that port had.**

*Fix kind: (a) engine. Built; `EnumCtorBodySpec`.*

### T12. Java `protected` is DROPPED, and accessibility is an input to OVERLOAD RESOLUTION — 1 error

CLOSED by `DESIGN.md` §8.7. The entry stays because the two facts it MEASURED are the constraints
that design is built on, and because its history is the cheapest available lesson in what an error
burn-down costs.

**What it was.** Commit `c6645c0` ("libgdx-core burn-down: 95 → 45 errors") changed the emitter's
`if f.isProtected then "protected "` to `""`, to escape the SAME-PACKAGE-CALLER delta: Java's
`protected` is package access PLUS subclass access, Scala's bare `protected` is subclass access only
and additionally forbids reaching the member through a reference that is not `this`, so emitting it
turned a widening into a set of NEW access errors. libGDX core's Java has **867** `protected`
declarations; every one of them then shipped PUBLIC.

That is usually invisible — a widened member breaks no compile and no test. It stops being invisible
where accessibility CHOSE AN OVERLOAD. Java resolves a call against the applicable methods that are
also ACCESSIBLE from the call site, so a `protected` overload simply is not a candidate for an
unrelated caller in another package; emitted public, it becomes one, and a call javac resolved
uniquely becomes ambiguous. Measured on gdx-gltf: `AnimationController` declares six public
`setAnimation(String, …)` overloads and one `protected AnimationDesc setAnimation(AnimationDesc)`.
`AnimationsPlayer.clearAnimations()` writes `scene.animationController.setAnimation(null)`, which
javac resolved to `setAnimation(String)`. Emitted, both were public and arity-1, `null` conformed to
both, and dotty reported `E051 Ambiguous overload` — the one error of that port's residue a reader
would have blamed on the library rather than on the port. **The overload interaction was the COST of
the drop, discovered a port and three weeks later, not its cause.** That is the lesson: a burn-down
edit that removes information removes it everywhere, and the bill arrives in a module nobody was
looking at.

**What closed it.** `protected[<emitted package tail>]` restores the package half and lifts the
`this` restriction, and dotty PRUNES an inaccessible alternative before overload resolution — so the
mapping does not merely avoid the ambiguity, it restores javac's own resolution INPUT. The
hand-written body substitution that compensated for it is deleted; the port lost the error.

**The two measured facts that survive as constraints, and must not be re-derived:**

- **Bare `protected` is unusable.** The same-package non-subclass caller set is **20 sites** in
  libGDX core alone — `Button→ButtonGroup.canCheck`, `Stage→Actor.setStage`, and the decals package
  reading `Decal`/`DecalMaterial` fields as a package-internal struct. Those are exactly what bare
  `protected` breaks and what the qualified form preserves. Do not "simplify" the mapping to bare
  `protected`.
- **The residual PUBLIC widenings are `protected static` and the two guards**, all recorded. A
  cross-package protected override does NOT widen to public: it takes the nearest common enclosing
  package as its qualifier.

*Fix kind: (a) engine. Built (`DESIGN.md` §8.7, `emit/Visibility.scala`, `VisibilitySpec`); the
blast was one designed corpus-wide re-baseline.*
### T13. `Enum.ordinal()` is part of every java enum's SURFACE, mentioned or not

CLOSED. The enum lowering synthesised `name()`, `values()` and `valueOf(String)` and not the fourth
member every java enum has. `java.lang.Enum.ordinal()` is FINAL there, so no enum declares it and no
enum can opt out of it — which is precisely why a library reaches for it wherever the constants
stand for consecutive integers somewhere ELSE. gdx-vfx's `CrtEffect` passes `lineStyle.ordinal()`
straight into a shader `#define`, beside a comment saying the ordinals match the shader's own
constants: `value ordinal is not a member of …`, and unlike `name()` there is no substitute a reader
would reach for.

Emitted as an ABSTRACT member on the sealed class with one `override def ordinal(): Int = <index>`
per constant — java's own O(1) field read. `values().indexOf(this)` would be one line instead of
n + 1 and would allocate an array on every call, which is the wrong trade for a member a render loop
calls.

**Suppressed WHOLE — base and constants together — when the enum declares its own `ordinal`**, for
T11's reason one member along: java's two namespaces let a FIELD or a promoted constructor PARAMETER
carry the name beside the final method, and scala's one namespace cannot. Half a suppression is
worse than none — every constant would `override` a member the base no longer declares.

Blast radius, since this is emitted text for every ported enum and therefore the one entry here with
a real one: **libGDX core 69 members, libGDX test 71, Ashley 75, anim8 71, noise4j 6, simple-graphs
0, jbump 0** — every changed unit an enum or the type that declares one, verified against the
members diff. No error count and no check count moved anywhere. Spec: `EnumCtorBodySpec`, with the
negative.

*Fix kind: (a) engine. Built.*

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

*Fix kind: (a) for what to inject; see `PROGRESS.md` §Publishability item 1.3 for the distribution
work.*

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

### K6.5 A java `T...` becomes an `Array[T]`, so a REWRITE onto a scala vararg must undo the pack

The engine renders a java varargs parameter as `Array[T]` and MATERIALISES the pack at the call
site (`SpoonTir.varargPack` builds a `Tree.NewArray`). That is right for every in-program vararg
method — the emitted `def pack[T](xs: Array[T])` is fed `pack(Array[String](a, b))` and both halves
agree. It is wrong for the one place a rewrite retargets a java call at a runtime helper declared
with a SCALA vararg, `JavaCollections.asList[A](xs: A*)`:

| java | before | after |
|---|---|---|
| `Arrays.asList(1, 2, 3)` | `asList(1, 2, 3)` — right BY ACCIDENT | unchanged |
| `Arrays.asList(s)` | `asList(Array[String](s))` — E007 | `asList(s)` |
| `Arrays.asList(xs, xs)` | `asList(Array[Array[String]](xs, xs))` — E007 | `asList(xs, xs)` |
| `Arrays.asList(xs)` | `JavaCollections.asList(xs.asInstanceOf[Array[Object]])` — E007 | `java.util.Arrays.asList(…)` — REFUSED, E007 under the JDK name |

The accident matters more than the failure: the frontend declines to pack PRIMITIVES, so the one
shape everybody writes arrived as bare elements and the convention clash never showed. **A rewrite
onto a differently-shaped runtime signature must normalise the pack, not assume either form** — the
frontend produces both, conditionally.

Two rules the fix rests on:

- **Open the pack into separate arguments**, which is `CLAUDE.md` §6's spread with no spread node
  needed and makes both frontend outcomes emit one shape. A LITERAL array in the slot
  (`asList(new String[]{a, b})`) opens too, soundly: the array is allocated at the call, so no
  caller holds the alias.
- **A single ARRAY-typed argument is the ALIASING form and is refused.** Java returns a live view of
  the caller's array; a spread would silently copy what java aliases (§4.4). The rewrite is skipped
  entirely so the emitted text keeps the JDK name and the error reads as an untranslated call
  (`Found: java.util.List[Array[Object]] / Required: Buffer[String]`) rather than a broken helper.
  A faithful live view — a fixed-size `Buffer` over the array with `add`/`remove` throwing — is
  expressible but not reachable from the rewrite: the frontend has already coerced the argument to
  the ERASED formal (`Array[Object]`), so the element type the view needs is gone by then.
  Recovering it is a frontend change with far wider blast radius.

*Fix kind: (a). The residue (the aliasing form) is (a) and unbuilt, and is a refusal by choice.*

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

### K9. A java enhanced-for over a JDK `Iterable` has no `foreach` — 2 errors, and why the obvious fix is worse

`Tree.ForEach` emits `for (x <- xs)`, which needs Scala's `foreach`. That is correct for an ARRAY,
for anything the port owns that ends up under `balticporter.runtime.JavaIterable` (whose companion
carries a `foreach` extension, so no import is needed), and for every JDK collection a port retyped
with `CollectionsTransform`. It is wrong for a JDK collection a port **kept**:

```
for (currentRoom <- this.rooms)   // rooms: java.util.List[Room]
    value foreach is not a member of java.util.List[Room] — did you mean rooms.forEach?
```

Two errors on noise4j, which is the first corpus port to run no `collections` phase — and it runs
none on purpose, because the JDK forms it uses (`Iterator.remove()`, `List.set` for its RETURN value)
have no Scala-collection counterpart and retyping turns one of them into a runtime `throw`
(`PROGRESS.md` §noise4j 5.4). So this is not an artefact of an unusual configuration; it is what
every port that keeps `java.util` will hit, which is every port whose reference hand-port kept it.

**Do NOT switch the emitter's `ForEach` to the iterator protocol universally.** Not attempted, and
deliberately: `{ val it = xs.iterator(); while (it.hasNext()) { val x = it.next(); … } }` is the JAVA
arity. A Scala `Buffer`'s `iterator` is parameterless and its `hasNext` is a parameterless accessor,
so the same text breaks every port that DOES run `collections` — and it would move the emitted digest
of every foreach loop in libGDX for a change that helps one port.

**And do not decide it from the type's NAME.** "`java.util.*` gets the iterator protocol" is exactly
the string test `CLAUDE.md` §4.56 exists to forbid, and it fails in both directions here:
`balticporter.runtime.JavaIterable` is external and not `scala.*` yet already has `foreach`, while a
retyped `java.util.List` is `scala.collection.mutable.Buffer` and no longer names `java` at all.

What a real fix has to answer first: **which iterables support Scala's `foreach`, decided from what a
PHASE did rather than from what a type is called.** The shape that costs no existing port anything is
a §1(b) phase with an empty default — a set of iterable types the port declares it kept, rewritten to
the iterator protocol before emission — because an empty parameter makes it a no-op and every current
lane stays byte-identical.

**The demand is now DERIVED and reported before any compile.** `JdkSurfaceCheck` (DESIGN.md §8.9)
walks every `ForEach` in the units a run emits and reports a `kept-iterable` finding for each
receiver the pipeline **left in the JDK namespace**, carrying the §1(b) classification above.
Measured: noise4j's two errors are exactly two findings, at the loops themselves —

```
kept-iterable  java.util.List  DungeonGenerator.java:164     (error anchored at :163, the method)
kept-iterable  java.util.Set   DungeonGenerator.java:281     (error anchored at :258, the method)
```

— and every other port reports **zero**, which is the honest number: they all run the collections
phase, so no receiver survives in `java.*`. The finding names the loop; the compile error names the
enclosing member, so the check is also the more precise of the two.

**Read the NODE, not the phase's table.** The first spelling of this — "the receiver's java type is
absent from `CollectionsTransform.typeMap`" — is the wrong side of the mapping and is wrong in both
directions: a declaration the port SCOPES OUT keeps a real `java.util.List` that the table calls
mapped, and a port with no phase at all keeps the same type that the table also calls mapped. The
post-pipeline type standing in the receiver slot is what the emitted `for (x <- xs)` is applied to,
whatever any phase intended.

*Fix kind: (a) in effect, best delivered as (b). Unbuilt — but no longer invisible: it is 2
findings on the one port that has it, on every run, before a compiler is started.*

### K10. A TYPE-VARIABLE map key arrives carrying java's `Object` WIDENING

CLOSED, and it is K5.6's rule met one slot along: *a phase that retypes owns the coercions around
what it moved.* Java declares `Map.get`, `Map.remove` and `Map.containsKey` over `Object`, so a key
whose static type is a TYPE VARIABLE reaches `CollectionsTransform` already wrapped —
`key.asInstanceOf[java.lang.Object]`, which the frontend synthesises off the DECLARED formal (G14)
and which is right for a call to a java `Map`. Scala's `Map[K, V]` declares the same three over `K`,
so once the receiver is retyped that widening is the only thing between the argument and the
parameter: `Found: Object / Required: K`, three times in gdx-vfx's `ValueArrayMap`.

The strip is STRUCTURAL and names no type (`CLAUDE.md` §4.56): the cast goes exactly when what it
WRAPS already has the type the rewritten member wants. A key that is genuinely some other `Object` —
java permits any — is left alone, and the boundary then still fails to compile naming both types,
which is the error a reader can act on (M6).

**0 members moved** on any other corpus port: no library before this one passed a type-variable key
to a retyped map, which is why the seam went four libraries without being seen. Spec:
`CollectionsTransformSpec`, both directions.

*Fix kind: (a) engine.*

### K11. A CAPACITY hint at a HASHED collection has no one-argument scala constructor

CLOSED. `copyConstructor`'s own note says a java capacity hint "maps correctly by accident", and for
the SEQUENCE targets it does — `new ArrayBuffer(10)` means what `new ArrayList<>(10)` means. It is
false for the HASHED ones and silently so: `scala.collection.mutable.HashMap` declares `()` and
`(initialCapacity: Int, loadFactor: Double)` and nothing between, so java's one-argument form lands
on no overload at all (E134, gdx-vfx's `ValueArrayMap`).

Java's own one-argument constructor IS `(initialCapacity, DEFAULT_LOAD_FACTOR)` and scala's
companion publishes the same 0.75 as `defaultLoadFactor`, so supplying it is java's definition
rather than a guess — the difference between a translation and an approximation.

The two `new` arms are disjoint by construction: `copyConstructor` takes a single COLLECTION
argument, `capacityConstructor` a single `scala.Int`, and java's `HashMap`/`HashSet` have no other
one-argument constructor. The two-argument `(int, float)` form needs nothing — scala widens the
`Float` to the `Double`.

**0 members moved** elsewhere, and that is arithmetic rather than luck: a one-argument hashed
constructor was a compile error before this, so no port that compiles could have had one.

*Fix kind: (a) engine; the SET of hashed targets is closed over the phase's own `typeMap`, so it is
the phase's record and not a name test.*

### K12. A component under an UNPARSED PARENT is frozen — was **12 of 144**, now **0**; and the surface that fixed it may not be demand-derived

`OverrideGraph.closureOf` may only change a member's signature when it can see every declaration of
the override component. A parent type the frontend never parsed has no `ClassDef` and therefore no
members to look at, so the closure is ANCHORED and every consumer refuses, counted. That is
deliberate and stated in DESIGN.md §8.5 — *an over-refusal is a counted skip an agent can see; an
under-refusal is a silent contract break* — and this entry is what it COSTS, so nobody has to
re-derive it.

Measured on libGDX core, binding the 144-entry harvested property policy in a dry run: **127
applied, 17 refused, and 12 of the 17 are this.** Every one of the twelve comes from a JDK interface
in an `implements` clause:

| type | unparsed parent | properties lost |
|---|---|---|
| `Selection<T>` | `java.lang.Iterable` | 6 |
| `VertexAttributes` | `java.lang.Iterable`, `java.lang.Comparable` | 2 |
| `TiledMapTileSet` | `java.lang.Iterable` | 2 |
| `OrientedBoundingBox` | `java.io.Serializable` | 2 |

None of the four interfaces declares a member remotely like `getToggle` or `getMaskWithSizePacked`.
The refusals are pure over-approximation, and they are the whole gap: **the engine has no way to ask
what a JDK type declares.** `RuntimePlan.concreteMembers` is not it — that is the engine's own
INJECTED shims (three types), threaded for the emitter's diamond check, not the JDK.

**CLOSED for all twelve, and NOT the way this entry predicted.** `ExternalSurface(known)` was the
right seam — no change to `OverrideGraph`, no change to any phase — but the value that fills it is
not the demand-derived surface. `ExternalSurface.jdkPlatform` is the eight PLATFORM types whose
member sets are CLOSED by the JDK: `java.io.Serializable` and `java.lang.Cloneable` declare nothing,
`java.lang.Comparable` declares `compareTo`, `java.lang.Iterable` declares three methods, and no
library can add to any of them. It is folded into `default`, arity-only so it over-matches in the
refusing direction, and it is §1(a) for exactly the reason `java.lang.Object`'s member set is.

**Measured** by rebuilding the graph over libGDX core with each surface and reading
`closureOf(...).externalAnchors` for the eighteen accessors of the twelve properties:

| surface | accessors anchored | properties refused |
|---|---:|---:|
| `java.lang.Object` only (the old default) | 17 of 18 | **12** |
| `Object` + `jdkPlatform` (the new default) | **0** of 18 | **0** |

**12 of 12.** `Selection`'s six, `VertexAttributes`' two, `TiledMapTileSet`'s two and
`OrientedBoundingBox`' two are all free. **0 members changed on all 13 ports** and every check count
identical — `bean-properties` is default-off, so the widening is measured and not yet spent.

**A DEMAND-DERIVED surface may NOT fill this map, and the correction matters more than the number.**
`ExternalUsage`'s rows say which members a program CALLS; `known`'s contract is that a type present
in it is answered EXACTLY, so an absence is proof. The two do not compose: a member the JDK type
declares and this program never calls is absent from the rows, and the anchor lifts on evidence that
was never there. On this corpus it would have lifted the same twelve — `java.lang.Iterable`'s rows
are `iterator`, and none of the twelve is called `iterator` — which is precisely what makes it
dangerous: an unsound rule that is right on the code you have. That converts §8.5's counted
over-refusal into an unnoticed under-refusal, which is the trade §8.5 exists to refuse. The rule is
general: **a surface may be believed only where it is COMPLETE, and completeness is a property of
the source, not of the shape of the data.** A type whose surface is large or version-dependent
(`java.util.Comparator`, whose default methods grew across releases) is therefore absent from
`jdkPlatform` too, and still anchors.

**One entry in the default surface is NOT optional and is easy to lose**: `java.lang.Object`.
`SpoonTir.superTypes` filters it out of every parent list on purpose, so a graph that reads parents
alone reports a rename of `toString`, `equals`, `hashCode` or `clone` as UNANCHORED — and renaming
those breaks every caller in the JVM with a green compile and no count moving. `ExternalSurface`
carries `Object`'s member set as universal knowledge and every closure consults it whatever the tree
shows.

*Fix kind: (a) engine. CLOSED for the twelve. The refusal remains correct — and remains the default —
for every unparsed parent whose surface is not closed by the platform, which is all of them but
eight.*

---

## 5. Portability and platform

### K13. `T | Null` is NOT transparent at an ABSTRACT type parameter — the union floor is free only at CONCRETE types

`Null` is a subtype of every concrete reference type, so `String | Null` simplifies at every use and
the annotation-driven union floor (`DESIGN.md` §8.6) costs nothing there. **It is not a subtype of an
abstract `T <: Object`** — which is not a corner case but the very fact that makes
`def m[T <: X](): T = null` a type error and forces the frontend's `null.asInstanceOf[T]`. So
`T | Null` does not conform to `T`, and every USE of an annotated `T`-typed declaration in a plain
`T` slot fails.

**Measured** by binding libGDX's own `@Null` policy on the reference port, emitting, and compiling —
**0 → 35 errors**, from **632** retyped declarations:

| shape | n |
|---|---:|
| `Found: T \| Null / Required: T` | 34 |
| a defaulted overload losing its one-argument form (`ObjectMap#get`) | 1 |

Every one is inside a generic type — the containers (`IntMap`, `LongMap`, `ObjectMap`, `OrderedMap`,
`Array`, `Queue`, `AtomicQueue`) and the generic widgets (`Tree`, `List`, `SelectBox`, `Selection`).
The second shape matters on its own: §8.6 claimed the floor cannot move overload resolution, and it
did.

**Do NOT "fix" this by refusing to retype at an abstract type.** The refusal would delete exactly the
thing the floor buys — the generic RETURN is where the placeholder cast lives — and the declaration
itself is perfectly well-typed. The cost is entirely at the USES, which is why it is COUNTED
(`NullabilityBoundaryCheck.Issue.AbstractTypeParameter`: 155 occurrences flagged, a deliberate
over-approximation of the 35 that fail) and left as a policy decision with three exits: scope the
generic types out of the phase, accept the errors, or stage to `-Yexplicit-nulls
-language:unsafeNulls` (§8.6's N2), under which the whole class disappears.

**And do not trust a probe that used `String`.** The claim this corrects was compiled, not reasoned —
against concrete types only. A probe for a rule about type conformance has to include an abstract
type or it has not tested the rule.

**THE FIRST EXIT WAS TAKEN, AND IT CLOSES OVER THE SUBCLASSES OR IT IS NOT AN EXIT.** Measured on
libGDX at P3. The eleven types the 35 errors landed in, scoped out by FQN, took **35 → 6**; the six
survivors were all in `SnapshotArray` and `DelayedRemovalArray`, which extend the scoped-out `Array`
and OVERRIDE two of its annotated members with the annotation re-stated on their own `T`. So the
parent's members were held back while the children's moved — half an override pair, which is the one
shape a union floor may not emit — and it is the mirror of the constraint wrapper mode already
refuses on (`DESIGN.md` §8.6). Adding the two subclasses took it to **0**. **A scope exit on a
generic type therefore names the type AND every owned subtype that RE-STATES the annotation.**

**THE RULE STANDS AND THE RUN NOW ENFORCES IT, so the numbers above are history rather than the
procedure.** This entry used to end "nothing computes this closure — a `RuleScope` is a set of FQNs
and the compile is the only thing that finds a missing entry", which was true of the SCOPE and false
of the PHASE: at plan time it already holds both halves, the annotation hits it has just computed
and `Definition.parents`. `NullabilityBoundaryCheck.Issue.ScopedOutParent` now reports every
(retyped declaration, scoped-out annotating ancestor) pair with the §1(b) classification, so the
35 → 6 → 0 hunt costs the next port ONE RUN. The subject is the CHILD — the end a port can move —
and the detail names the entry holding the parent back, which is the string an agent edits. It
invents no notion of overriding beyond the NAME, deliberately: over-approximating names a pair a
port can dismiss, while a signature test would need the override closure this phase does not have.

**And it stops exactly there, which is the other half of the rule.** A subtype that merely INHERITS
an annotated member needs no entry, and adding one is DEAD POLICY that nothing can report:
`OrderedMap` extends the scoped-out `ObjectMap` and `OrderedMapValues` overrides an annotated
`ObjectMap$Values#toArray`, so the unscoped run put an error in it and the first draft listed it —
but it declares zero `@Null` of its own, so scoping the parent out settles both ends and the entry
holds back nothing. Measured: with and without it, `members.tsv` is byte-identical.
`PolicyBinder.bindScope` asks only "did anything in this program fall inside this region", the type
exists, and `policy` stays 0 — so an inert SCOPE entry was the one §1(b) no-op the never-fired
machinery could not see, and only a byte-identity check found it.

**ENFORCED TOO, and by the phase for the same reason.** `RuleScope.neverFired` is the complement of
what a phase OBSERVED, and only the phase knows what it observed: `NullabilityTransform` records the
entry that decided each ANNOTATED declaration — in both directions, since an entry excludes under
`Everywhere` and includes under `Only` — and reports the complement as a `policy` `NeverMatched`
finding whose text says what "fired" means for a scope. Only entries whose BINDING succeeded are
reported, or one mistake with one fix would be reported twice. **A byte-identity experiment is not a
report**, and a rule whose only evidence is an experiment nobody will re-run is a rule that decays.

Its price, stated as a number rather than as "a few generic types": **12 entries hold back 92 of 632
declarations** to clear 35 errors, of which about 34 are declarations that actually fail. A
`RuleScope` says types, the failing set is a predicate over declarations, and a hand-written list of
that predicate would be a second copy of the one the engine already computes and reports. The
over-approximation is the honest price of the exit; the whole list is DELETED, not edited, when N2
lands.

**…and the price is only that high when the failing set is a container's WHOLE API. Spell the exit
at the MEMBER when it is not.** A `RuleScope` entry may name `owner#member` as readily as a type,
and a bare member name is every overload of it. Measured on the second port to take this exit
(screens, P3): `ScreenManager<S extends ManagedScreen, T extends ScreenTransition>` annotates its
own type parameters and the unscoped run put **3 errors** in it — an overload-resolution failure at
`pushScreen` and two `T | Null` mismatches inside `render`. **Two member keys took it to 0**:
`ScreenManager#transition` and `ScreenManager#pushScreen`. Two further declarations annotated at an
abstract `S` (`#getCurrentScreen`, `#getLastScreen`) are NOT exempt and keep the floor, because
nothing in reach uses them in a position `S | Null` does not satisfy; they remain counted, which is
where a declaration that *could* have failed belongs. **The exit is what the compiler measured, not
every declaration the check flagged** — and the two entries had to travel together for the reason
the subclass closure exists one level up: the field is assigned from the parameter, so scoping out
one and retyping the other is `T | Null` into `T`.

One thing that shape does NOT record, and it is a provenance gap rather than a limit: a scope entry
that holds back only a PARAMETER produces no `decisions.tsv` row and no porter note, because
`NullabilityTransform.scopedOut` skips a param and does not attribute to its method (PROGRESS §12.1).

*Fix kind: (b) per-library policy — the engine's part is the number.*

### K14. A RETARGET's subtyping licence is ONE-DIRECTIONAL — the producer side is COUNTED, never coerced

A `CollectionsTransform(retarget)` entry moves a type and bridges nothing, on a precondition the
policy author owes: *the scala target is usable wherever the java source was*. `java.util.Comparator
-> scala.math.Ordering` holds it, because `Ordering[T] <: Comparator[T]`.

**That licence covers exactly one direction.** It says a retyped value reaches a slot that still
declares `Comparator`. It says nothing about a `Comparator` the JDK HANDS BACK —
`Collections.reverseOrder()`, `TreeMap.comparator()`, `Comparator.comparing(…)`,
`String.CASE_INSENSITIVE_ORDER` — arriving at a slot the phase retyped. That value is not an
`Ordering`, and three things conspire to make it silent:

- a retarget contributes to neither `mappedTypes` nor `retypedTargets`, so `CollectionBoundaryCheck`
  is blind to it BY CONSTRUCTION (the precondition says there is no seam);
- `transformType` is position-blind, so the producing node's own type moved too — both sides of the
  slot read `Ordering` and a check comparing node types reports ZERO;
- the phase has no `transformIdent`, so a STATIC receiver (`Comparator.naturalOrder()`) keeps its
  java spelling under a moved node type.

Through a rewritten cast the failure is a `ClassCastException` at run time with a green compile,
which is `CLAUDE.md` §3's whole subject.

**What is BUILT is the counter, not the coercion.** `RetargetBoundaryCheck` (`collection-retarget`)
reports all three shapes with the §1 classification, recorded whenever the phase is in the pipeline.
**The corpus measures 0** — every `Comparator` in it is produced by code the port emits, which moves
with its declaration — so the counter was proven against a SYNTHETIC producer
(`ComparatorOrderingPortSpec`); a residue nobody can produce on demand is one nobody can prove is
counted.

**What is NOT built, and its cost.** No wrapper is synthesised. One is expressible in principle for
this pair (an `Ordering` delegating to a `Comparator`'s `compare`) and it is NOT a general answer:
a retarget is a §1(b) table whose target may be any type, so a coercion would have to arrive as
policy beside the entry — a factory FQN the port supplies, exactly as `typeMap` carries one — and
that is a table shape, not a rule the engine can derive. Until a port has a real producer to measure
against, the honest position is the counted refusal: **do not add a wrapper on a synthetic case.**
The alternative already exists and is documented — move the type out of `retarget` into `typeMap`
with a kind and a factory, where the seam becomes a counted `coerce` boundary (`DESIGN.md` §8.12).

*Fix kind: (a) engine for the counter — DONE; (b) per-library for the choice of table when a real
producer appears.*

---

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
orchestration was the root cause; `PortRun.RequiredChecks` closed it, and `PROGRESS.md`
§Publishability item 1.2 records what that took.

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

The CLAUDE.md §4.4 table's defects were found by running ported tests, **none of which moved any compile-error count** — count them there, not here; the table has grown every time a suite ran.
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

`RuntimeArtifact` reads the runtime sources from a resource that `engine`'s build copies out of the
`runtime` module. Two layers can serve a stale copy, and only one of them matters:

- `classes/` lags `resource_managed/` until `copyResources` runs, which plain `compile` does not
  trigger. Harmless in practice — every migration runs FORKED (`corpus` sets
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
and reaping them is the same cross-checkout kill the measure lanes had to have removed from them
(see the `pkill scala-cli` note at the head of `scripts/_lib.sh`, which the `Justfile`'s lanes source).

Prevention is cheaper than either: never kill a client that is merely slow — `sbt -client` compiling
a cold worktree takes minutes, and the wedge only exists because a kill looked faster.

**Which is which takes two commands and no guessing**, and the rule above is unusable without them —
"minutes" and "for ever" look identical from the outside, especially with three sibling worktrees
measuring at once and everything slow. A wedged server is IDLE, and idleness is observable:

```
find . -newermt "-3 minutes" -type f -not -path "./.git/*"   # a build that is working WRITES
ps -o time= -p <server-pid>                                  # …twice: a working server BURNS CPU
```

Both static — no file touched in three minutes and CPU unmoved between two samples — is the wedge;
either one moving is a slow compile, and killing it is the mistake this entry is about. Then find
the server for THIS checkout through its own `project/target/active.json` socket
(`lsof -U | grep <hash>`), never off `ps` alone: measured during M5r, `ps aux | grep sbt-launch`
listed ten servers of which exactly one was this worktree's — the other nine were sibling
checkouts', mid-measure, and three of them had started during this lane's own run, which is
precisely the coincidence that makes a name-based kill look justified.

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

### M5.8 A symbol's ANNOTATIONS are types too — and `mapSymbols` was not showing them

`StandardTraversal.mapSymbols` routed each `Symbol.info` through `transformType` and nothing else.
`Symbol.annotations` is a `List[Annot]`, each with a `tpe`, and the emitter renders `@…` from
exactly that `tpe` — so **every retyping phase in the engine had the same blind spot**: it moved a
type in every signature, every `new`, every cast and every type argument, and left the annotation
naming the type it had just removed.

Measured on a port whose §1(b) type redirect moved a third-party marker annotation: **3 sites**, and
the only symptom was three `value <pkg> is not a member of` errors in the emitted file. No check
moved, no test broke, and the phase's own policy report said the entry had fired — because it had,
everywhere except here.

Two things this generalises to, before the next traversal is narrowed:

- **An annotation is a declaration's CONTRACT** (the `Annot` doc says so at length: a `@Test` that
  does not survive runs zero tests and reports success). A traversal that treats it as decoration is
  wrong for the same reason dropping it is.
- **The annotation's ARGUMENTS are terms**, and terms are reached by the tree walk that visits the
  declaration, not from the symbol table. `mapSymbols` deliberately does not touch them; a phase
  that needs to rewrite an annotation argument needs a tree hook, not this one.

*Fix kind: (a) engine — done; pinned by `TypeRedirectTransformSpec`'s annotation case, which is the
one that fails if the traversal is narrowed again.*


### M5.9 A baseline ACCEPTED IN A WORKTREE may not reproduce in the primary checkout — realpath the provenance root

**Title, for renumbering: "worktree-accepted baselines and the provenance root symlink".** CLOSED —
the third CLAUDE.md §5.4 instance, and the first to reach an EMITTED BYTE. (a) universal.

`TirEmitter.sourcePathOf` compared the parser-recorded origin path against `Provenance.sourceRoot`
with a lexical `startsWith`. A git worktree reaches the sibling source checkout through
`.claude/worktrees/<x>/../sge` — a symlink — so the configured root and the recorded path spelled
the same directory two ways, the root-relative case silently failed ONLY in worktrees, and the
marker cut (first-occurrence, one directory too early for a repo that nests a module dir of its own
name, as gdx-vfx does) rendered `gdx-vfx/gdx-vfx/core/…` there against `gdx-vfx/core/…` in the
primary checkout. Same commit, two headers.

Measured: **44 vfx + 6 noise4j whole-file digests** in worktree-accepted baselines that the primary
checkout could not reproduce — zero member digests moved, zero counts moved, only the class rows
(whole-file digests) — found the first time `just measure-all` ran in the primary after a wave of
worktree-side integrations. Fix: realpath both operands, normalize as the not-exists fallback
(§5.4's rule verbatim); pinned by `ProvenanceHeaderSpec`'s symlink case, which was negative-proofed
against the lexical code (the naive temp-dir layout does NOT discriminate — the root's own parent
must contain the marker, `…/mylib/mylib/`).
### M6. Refuse and COUNT rather than approximate

Three places where the port deliberately carries a number instead of a guess, and each is the right
call:

- 49 dropped `super(args)` on non-throwable parents — padding measured **0 → 55** (C3).
- 156 construction paths that run the PROMOTED constructor's body where Java ran nothing — refusing
  the promotion measured **0 → 41** (C7). Note this one is an ADDITION rather than a drop, and the
  count is on the same footing for it: what the port loses is Java's separation of construction
  paths.
- A raw anonymous class with a body — refused and reported (G10).
- A single-primary encoding with no faithful form — **left as a compile error deliberately**,
  because the compiler is a louder tracker than a silent omission.

A residue comment count (`/* break */ ()`) is itself a measure — do not delete it to make output
tidy.

**…and `PortRun(preview = true)` is the other half, for a library nobody has ported yet.** Refusing
and counting is right for a port that ships and wrong for the first week of a NEW one, where the
operator is an agent in another repository that has to FIND the residue before it can act — and
`/* break … */ ()` compiles perfectly. Under `preview` each counted refusal becomes

```
scala.compiletime.error("balticporter: <construct>: <why>; <what an agent must do>; origin <javaPath>:<line>")
```

so the port deliberately does not compile and every error carries the four things the residue
comment does not. It is a DIAGNOSTIC mode, orthogonal to `RuntimeMode`, off by default, and the
shipping emission with it off is byte-identical (proved by `members.tsv`, not asserted). The errors
have their own lane — `Correlate.Lane.Declared`, classified by the message the engine itself wrote,
ahead of the source-map lookup — so a preview run's declared refusals can never be read as engine
gaps, nor as "outside the map, not our problem".

### M7. A check over EMITTED TEXT must join on a RECORDED id, never on the rendering — 594 → 0

`NoteCoverageCheck` asks whether every decision that must carry a porter note (CLAUDE.md §4.575) got
one. Its first version answered the second direction — "a note with no decision" — by parsing the
note's own `k=v` pairs back out of the emitted text and matching the value against the decision's
`detail`. On libGDX core that reported **594 unbacked notes on a corpus where every one of them was
derived**: the pair list is whitespace-separated, `Reason.Configured("package-rename", "com
.badlogic.gdx -> sge")` renders `key=com.badlogic.gdx -> sge`, and both sides were reading a value
truncated at the first space — differently.

Quoting the value fixed the truncation and is now the grammar. The rule the 594 taught is larger and
is why this is here rather than in a commit message:

- **A rendering is not an identity.** The authoritative record of "which decision produced this
  note" is the EMITTER's, taken as it printed — `PorterNote.Printed(kind, SymId, unit)`, the same
  discipline as `SrcMap.Recording`. The check joins on that. It reads only the SLUG out of the text,
  which is enough to catch the thing the text can uniquely reveal: a note NOTHING recorded printing.
- **Do not join on a NAME either.** Three of the emitter's own passes rename the symbol before it is
  rendered (`style` → `style$shadow`), so a name-keyed join is empty on exactly the decisions the
  check exists for.
- **A check that greps emitted text for an FQN must strip porter notes first.** A note names the
  UPSTREAM FQN deliberately; `SubstitutionCheck.dangling`'s substring search then reported
  `com.badlogic.gdx.utils.Json` as dropped-but-still-referenced from seven `from=…JsonValue`
  notes — **substitution(dangling) 0 → 3**, on a port whose replacement was on disk.
  `SubstitutionCheck.withoutPorterNotes` is the fix, and it removes only the port's own commentary:
  an upstream Javadoc that genuinely discusses the type is text this check has always counted.

*Fix kind: (a).*

### M8. A note is emitted only where the emitter ASKS for one — a member on a special path has none

CLOSED, and it is M7's family from the other side. `NoteCoverageCheck` runs in both directions, so
"a decision with no note" is a fatal finding — and it fires the moment a policy decides about a
member the emitter renders through a path that never calls `declNotes`. A java `static { }` block is
one: it is carried as a synthetic member and emitted as `locally { … }`, not as a `def`, so
`MethodBodyTransform` replacing a `<clinit>`'s body recorded its decision and shipped no note beside
the code.

Worth keeping because of what it is invisible to: the output compiles either way, no other count
moves, and no test breaks — the same profile as the trivia regression §4.58 describes. The general
form is *every emission path that renders a DECLARATION owes it its notes*, and the ones to suspect
are exactly the members with no `def`/`val` keyword of their own.

`porter-notes 1 -> 0` on gdx-vfx; **0 members moved** elsewhere, since no other port decides about a
`<clinit>`. Spec: `MethodBodyTransformSpec`'s `<clinit>` case asserts the body AND the note.

*Fix kind: (a) engine.*

---

## 8. A DEPENDENT reading its base's published port map

### D1. A TIR symbol's `fullName` is the SAME for every overload. **263 → 8, then CLOSED for policy**

`Symbol.fullName` for a member is `owner#name` with **no parameter list**; every overload is a
distinct `SymId` carrying the same string. Every map, manifest key and `dropMethods` entry, by
contrast, is written `owner#name(P1,P2)`. The obvious discriminator — the call's ARGUMENT COUNT — is
not one:

- `Array#toArray(ArraySupplier)` is `Ported` and `Array#toArray(Class)` is `Dropped`, at the **same
  arity**. Every real library has such a pair, because the portable replacement for a reflective
  overload takes exactly one argument too.
- Measured on Ashley against libGDX core's published map: arity-only selection produced **263**
  findings, of which **118 were `Ambiguous`** and *every* `toArray` and `Array#<init>` site was one.
  The acceptance case itself — `ImmutableArray.toArray(Class)` — came back undecidable.

**The root is CLOSED for POLICY, by `Symbol.descriptor` (DESIGN.md §8.1).** A symbol now carries its
source-level parameter spelling as a separate FIELD — never a fourth separator in `fullName`, which
would move every `findings.tsv` id in every lane and hand the package rename a place to cut inside a
parameter list — and every policy key is resolved once, before the pipeline, by `PolicyBinder`. A
phase receives bound `SymId`s. Two cross-grammar divergences that were latent and invisible to every
count went with it: an ARRAY parameter (`int[]` in a manifest, `Array` in the engine's own key) and
`equals(Object)` (`Object` before the frontend's retype, `Any` after). Both are negative specs now.

**The arity fallback SURVIVES in exactly one place, and it is not the same question.**
`PortMapTransform.preciseKey`/`bareKey`/`arityOf`/`select` and `PortMap.erase` are the two ends of
ONE JOIN in the EMITTED namespace: a base publishes `upstream` as `erase(TirEmitter.memberKey(…))`,
which is emitted type names (`Array` for an array, `Any` for `equals`, `Int` for `int`), and a
dependent reconstructs the same string from the callee's `info`. Swapping the reading end to the
descriptor alone makes the join MISS for exactly the members the descriptor spells differently. Both
ends can move together, and that is a commit that re-publishes every `port-map.tsv` — an artifact
dependents read — so it is measured on its own and not folded into the identity work. Until then the
**8-finding residue stands**: a call whose callee `info` a lenient frontend never resolved.

Two smaller rules from the same measurement, both unchanged:

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

Now the FIFTH measured instance of the same shape: `OmissionCheck` (Ashley reporting libGDX's 47
omissions), `PortabilityCheck`, the port-map findings above, the collection closure check — which
unfiltered reported `AsyncExecutor`'s two findings to four different ports, three of which cannot
act on them — and now `decisions.tsv`. Every new per-site check starts from the run's OWN units
(`checkedUnits` / `OmissionCheck.check`'s unit parameter is the pattern); a check that scans
`program.units` bare is wrong on every dependent port, and the wrongness arrives exactly when the
second module does.

**And it is not only CHECKS.** A provenance artifact is subject to the identical rule, which is how
the fifth instance was found: every PHASE also runs over the base's units and decides about them
identically to the base's own run, so `libgdx-test` published **1240 decisions of which 961 were
libGDX core's** — the same rows, byte for byte, that `libgdx-core`'s own artifact already carries,
in a file whose reader is looking for the 279 that are the test module's. Ashley: 499 -> 131,
ashley-test 657 -> 196, simple-graphs-test 93 -> 23.

Two things that decision cost, and both are worth repeating for the next artifact:

- **Withhold, do not section.** A clearly-marked second section in the same file is still read
  past, still diffed by anything comparing the artifact, and still makes "how many decisions did
  this port make" a question with two answers. The rows are not lost — the module that OWNS the
  declaration emits them, and it is the only module that can change one. Print the WITHHELD COUNT
  on every run instead, so it can never be mistaken for "none were made".
- **Scope the PHASE log, not the run's own rows.** A drop the dependent INHERITED is anchored on
  the base's unit and would be filtered out by the same rule — and it is exactly the row that
  explains why a type the dependent references is absent. `PortRun` filters the drained phase log
  and records its own policy rows afterwards; `detail("own")` already separates declared-here from
  inherited.

**The substrate was wrong, and that is why the family kept recurring.** `Program.owned` roots its
climb on `program.units` — ALL of them, the base's included — so it is a *program-vs-JDK* filter, not
a *mine-vs-base* filter, and in a dependent it answers `true` for every base symbol. It is exactly
right for what it is asked there (a rename must not rewrite the JDK; a `RuleScope` entry naming an
external type did not fire), and it is not the predicate any of the instances above needed. The five
real mine-vs-base filters were **six independent copies** of a fuel-bounded climb, all on the
reporting side, none on the rewriting side, with different failure directions.

`balticporter.tir.Surface.owns` (`api`) is now the one climb, and the failure direction is named
once: **exhausting the fuel counts as NOT owned**, so the run asks the base's published contract and
gets an honest `Unknown` rather than deciding on a guess. `PortRun` builds it from the same realpathed
`partitionUnits` split every other owner question uses (§5.4), and the reporting-side filters keep
their current semantics. The reason it had to be a VIEW and not a check is D4's own record: nothing
in a dependent's run disagrees with itself, so a check comparing recomputed answers has nothing to
compare against.

**THE SIXTH INSTANCE IS ON THE REWRITING SIDE, and it is a REWRITE a POLICY KEY authorised.** Every
instance above is a report; this one changes emitted code. `NullabilityTransform`'s plan loop walks
`Program.owned` — the program-vs-JDK filter named above — so in a dependent it plans over the BASE's
declarations too, and a dependent that declares its own annotation FQN retypes them. `SurfaceFold`'s
`governs` screen cannot see it, and that is structural rather than an oversight: the screen reads
policy KEYS, and an annotation FQN (`org.jspecify.annotations.Nullable`, a third-party jar's name) is
the ONE key kind that names none of the declarations it moves — inside no base's claim, so admitted,
correctly, because the key itself edits nothing.

**Invisible by construction, in BOTH artifacts, which is why no number would ever have moved.** The
`decisions.tsv` rows are about the base's declarations and this entry's own module scope withholds
them; the findings are raised at base units and `NullabilityTransform.boundary`'s emitted-unit filter
drops them. A dependent could have emitted `Actor | Null` where its base emitted `Actor`, on every
run, with every count identical.

Closed by `balticporter.tir.RunScope` (`api`) — *which units does this run EMIT*, and *which of a
merged phase's keys did THIS manifest contribute* — carried on the `PolicyBinder`, which is already
the one object the run hands every `PolicyBound` phase before the pipeline starts. `RunScope.whole`
is the default and is the truth for a base port, so the screen is a no-op there by arithmetic. The
refusal is COUNTED as a `policy` finding, one per KEY, and NON-FATAL: the refusal has already made
the emission correct, so what is wrong is the manifest's claim and not the port's output
(`DESIGN.md` §8.13's last subsection argues it). Only the annotation half needs the run-time screen —
a SCOPE entry names an FQN, so one that reaches a base declaration is inside that base's `governs`
claim by construction and is already fatal at manifest time. **Do not add a run-time screen for the
other key kinds**; it would duplicate the fold and report the same thing twice.

*Fix kind: (a) engine. The reporting filters landed per instance; the predicate under them is now one
value (`Surface`); the rewriting side is `RunScope` for a policy-driven rewrite and `CtorFunnel`'s
fixpoint under D4 for a whole-program index.*

### D3. A `<synthetic>` origin is not a file — exclude it from any source fingerprint

`PortMap`'s staleness check digests the Java files the map attributes members to. libGDX core has
exactly **one** member whose origin is `<synthetic>`; including it put an unresolvable path in the
file set, and the first dependent run reported the base's map as `Unverified` — a check whose first
real firing was a false positive. `SrcMap.relativise` already leaves `<…>` alone for the same
reason; anything consuming its paths must too.

*Fix kind: (a) engine.*

### D4. `CtorFunnel`'s fixpoint is WHOLE-PROGRAM, and a dependent's program is a different one — 3 errors

**Title, for renumbering: "CtorFunnel's fixpoint is whole-program, and a dependent's program is a
different one".**

`CtorFunnel.Plans` decides which Java constructor becomes each class's Scala primary, and it decides
it at a FIXPOINT over the whole program (C1: a paramful promotion is withheld wherever a subclass
needs a nilary `extends`). That answer is part of the emitted SURFACE — it is the class's parameter
list — and it is computed from a set of classes the run does not choose.

A dependent's `Program` CONTAINS its base (D2), so the fixpoint sees the base's classes plus the
dependent's, and can therefore reach a DIFFERENT answer for a base class than the base's own run
did. The base has already emitted; the dependent then emits code shaped to the answer it computed.

Measured on gdx-gltf against libGDX core. libGDX emits `abstract class Attribute(type$p: Long)` —
paramful, and every one of its own 10+ subclasses passes the argument up. gdx-gltf adds three
subclasses with several roots and no shared parameter list (`ClippingPlaneAttribute`,
`PBRCubemapAttribute`, `PBRTextureAttribute`), so ITS fixpoint withheld `Attribute`'s promotion;
`Plans.prologueOf` then reported a nilary prologue for the parent, `replayFor` accepted, and each of
the three emitted `class C extends Attribute { def this(…) = { this(); this.type = …; … } }`.

Against libGDX's ACTUAL emitted `Attribute` that is `E171 not enough arguments for constructor
Attribute` / `E134 None of the overloaded alternatives … match arguments ()` — three errors, one per
class. Note what makes it expensive to find: the replayed bodies are correct, the dependent's own
file looks right, and NOTHING in the dependent's run disagrees with itself. `ManifestAgreement`
reports 0, because a funnel plan is not a manifest key; the port map records `Attribute` as `Ported`,
which it is. The disagreement is only visible when the two modules are compiled together.

**It is DRIFT, not a plain funnel gap, and a probe settles which.** Put the same shape in a SINGLE
program — a paramful parent, a subclass with two roots both calling `super(K)`:

```java
abstract class Parent { public Parent(long type) { this.type = type; this.typeBit = (int) type; } }
class Heir extends Parent {
  public static final long Type = 7L;
  public Heir(String plane)      { super(Type); … }
  public Heir(int a, float b)    { super(Type); … }
}
```

It emits `abstract class Parent { def this(type: Long) = … }` and `class Heir extends Parent` with
the parent's promotion correctly WITHHELD, and it compiles. Two modules, one program each, two
different correct answers — which is the definition of the §1.5 drift and not a defect in either
answer. Do not go looking for a bug in `plan0`.

**Do not "fix" it by refusing the withholding** — that is C1 exactly (+14 on libGDX), and it would
break the dependent's own subclasses instead.

**CLOSED for the fixpoint, and the residue is a different limit.** Port-map schema 3 carries
`primary=` / `primaryKind=` / `primaryVis=` for every emitted type, `Surface` (`api`) answers
mine-vs-base structurally, and `CtorFunnel.Plans` now runs its withholding fixpoint over the run's
OWN classes only — both the demand set and the demotion target. A non-owned class is reconciled
against the published row, in ONE implementation split by a provable property rather than a
heuristic: the fixpoint's predicate is `paramfulPrimary && needNilary && !reachableArgumentFree` and
adding units can only GROW `needNilary`, so a class failing the first or third conjunct is INVARIANT
under extra subclasses (its local derivation *is* the base's answer, and the row is a cross-check
whose disagreement is FATAL as an engine bug), while a class satisfying both is a WALL whose answer
is a function of its subclasses (there is nothing to derive; the row is load-bearing and is read).

`BaseSurfaceSpec` pins both directions, including the pre-D1 behaviour as a falsifier: with the whole
program as the surface the fixpoint demotes the base's `Base(int n)` to nilary; with the view it does
not, and an owned class is still demoted.

**What is NOT closed, measured on the same port.** gdx-gltf's `PBRCubemapAttribute` and
`PBRTextureAttribute` are still 2 errors, and the seeded row does not and cannot remove them: the
base publishes `CubemapAttribute` as `primary=(long) primaryKind=unique-root` with
`(long,TextureDescriptor)` and `(long,Cubemap)` among its `secondaries`, and both of the dependent's
roots call exactly those two. A Scala `extends` clause reaches only the PRIMARY, and §8.2's synthesis
is inadmissible because the roots reach *different* parent constructors. **The row confirms the wall
rather than removing it.** That residue is C3's shape, not D4's — the honest outcome is a counted
refusal, not a plan — and it must not be re-attempted as a seeding problem.

*Fix kind: (a) engine. Landed for the fixpoint; the wall residue is C3.*

### D5. A REPLAY may not widen a `private` member the run does not EMIT — 4 errors

**Title, for renumbering: "A replay may not widen a private member the run does not emit".**

Scala secondary constructors cannot call `super(args)`, so `CtorFunnel.Plans.replayFor` expresses one
as the parent constructor's own statements replayed after `this()`. Those statements execute one
level DOWN, in the subclass, where a `private` parent member no longer reaches — so the planner
collects them in `widenedMembers` and `TirEmitter.widen` drops `private` from each. Within one
module that is exact and cannot change behaviour.

Across a module boundary it is not a widening at all. `widen` edits the SYMBOL TABLE of the run's own
`Program`; the DECLARATION lives in the base's emitted file, which this run does not write and which
still says `private`. The dependent emits a call to it and the compile fails.

Measured on gdx-gltf: `ModelInstanceHack(Model)` / `(Model, String…)` each `super(model, …)`, the
replay lands `ModelInstance`'s constructor body in the subclass, and that body calls
`ModelInstance.copyNodes` — `private` in libGDX's Java and `private def copyNodes` in libGDX's
emitted Scala, because libGDX itself never replays it and so never widened it. **4 errors, all
`value copyNodes is not a member of …ModelInstanceHack`.**

M6's answer applies unchanged: where the widening cannot be performed, REFUSE the replay and count
the omission. `super(args)` is then dropped, which `OmissionCheck` already reports (C3), and the
port compiles with a known, named divergence instead of failing.

**Both missing inputs now EXIST and nothing reads them yet — 4 errors, unchanged.** `Plans` knows
which classes this run emits (`Surface.owns`, D4), and port-map schema 3 publishes `vis=` on every
member row, so *"may a replay reach this base member?"* is a lookup rather than a re-derivation over
the dependent's own symbol table. `replayFor` still consults `widenedMembers` and
`TirEmitter.widen` still drops `private` from the flags of a class this run does not emit. The
remaining work is one commit and it must not be measured together with D4's (`.balticporter/CHUNK3.md`
sequences them).

Note the asymmetry that makes this invisible from the base: the base cannot widen speculatively (it
has no way to know a future dependent will replay), and the dependent cannot widen at all. Only the
dependent can SEE the problem, and only the base could fix it — which is why the honest engine
answer is to refuse rather than to shift the decision.

*Fix kind: (a) engine.*

### D6. An all-static class collapses to an `object`, and a CONSUMER is the one that names it as a TYPE

**Title, for renumbering: "an all-static class collapses to an object, and a consumer names it as a
type".** CLOSED — fixed, and recorded because the shape recurs. **L3 is the other half of the same
guard** — the class-literal face, found independently on gdx-vfx; both are answered by one
`TirEmitter.typeNamedElsewhere` (its doc records the merge).

A Java class whose every member is `static` still has an implicit public constructor and is still a
TYPE. The emitter collapses it to a bare `object`, guarded on nobody EXTENDING it and nobody
INSTANTIATING it — two guards each added after a library broke on the missing one (the second cost
Ashley 26 errors from a single empty `private static class Dummy { }`).

The third face is a type POSITION with no `new` and no `extends`: `KHRMaterialsUnlit.class` as a
`Class<T>` argument, and `T get(Class<T>, String)` returning at `T = KHRMaterialsUnlit`. libGDX core
has 31 all-static classes and names none of them as a type, which is why five ports did not see it;
a library that CONSUMES another's constant-holders does, and gdx-gltf cost 3 errors from one
eight-line file.

The lesson for the next guard of this kind: the question is not "can this be an object" but "does
anything the program does with this name require a TYPE", and the ways to require one are `extends`,
`new`, a DECLARATION's type and a CLASS LITERAL — four, not three.

**And the obvious over-approximation is the wrong answer, measured.** `Phase.transformType` sees
every type occurrence, which sounds like the safe reading and is not: a term's own `tpe` is an
occurrence, so `Gdx.app` — an ordinary static ACCESS, the one thing a collapsed object is perfect
for — makes `Gdx` look named-as-a-type. Reading it bare de-collapsed **29 of libGDX core's 31
constant holders and moved 36 members** (`Align`, `Gdx`, `Base64Coder`, `TimeUtils`, …) for a
question none of them asks. It still compiled and every check count held, which is what makes a bad
approximation here expensive rather than loud.

Narrowed to the two positions that genuinely require a type — every `Symbol.info` (complete for
declarations by construction, walked with `StandardTraversal.mapType`) and every
`Constant.ClassOfC` (the half `info` cannot see: `ext(X.class, …)` infers the callee's `T` and
declares nothing) — libGDX core moves **0 members** and gdx-gltf's 5 errors go. Exclude the
candidate's own owner chain either way, or every class names itself and the collapse is disabled
outright.

**The CROSS-MODULE face is a fifth way, and it has no local repair.** The four positions above are
what THIS run does with a name, and `typeNamedElsewhere` is right about them. The other direction is
a base that collapsed a class and a DEPENDENT that names it as a type: the base's own run cannot see
it (it has 31 and names none of them), and the dependent's recomputation cannot either, because the
dependent does not emit the base and never takes the collapse branch. Port-map schema 3's `form=`
answers it — `members.tsv` records `sge.utils.Align`'s kind as `class` while it emits as
`object Align` — and `TirEmitter.surfaceGaps` reports it, ATTRIBUTED to the base and **not fatal**:
the base is emitted and gone, nothing this module does makes the type a type again, and the fix is
in the base's repository or in this module's use of the name. That smaller claim is the whole
content of §8.3's honest-scope statement, and §4.45 measures a check by exactly that difference —
a bare "type Align is not a member of sge.utils" becomes a finding naming the module that must
change. **Measured 0 across the corpus today**, which is D6's own observation restated: libGDX has
31 bare objects and no dependent names one as a type. `BaseSurfaceSpec` pins both directions.

*Fix kind: (a) engine — fixed in `TirEmitter.typeNamedElsewhere`; `AllStaticClassAsTypeSpec` pins
both directions INCLUDING the static-access negative. The cross-module face is `TirEmitter.surfaceGaps`
over `Surface.typeShape`, and is attribution, not repair.*

### D6.5. A drop and its INJECTION are in different namespaces, so nothing paired them — 10 false findings

**Title, for renumbering: "a drop and its injection are in different namespaces, so nothing paired
them".** CLOSED — fixed; recorded because it is §4.56's rule failing at a THIRD artifact.

`PortMap.of` already distinguished a `Dropped` type (nothing stands at the name; every call must be
gone) from a `Substituted` one (something stands at it; the call gets a different implementation) —
"the whole content of the entry for a dependent", as its own comment says. It decided which by
`injectedFqns(fqn)`.

`dropTypes` is a MANIFEST KEY, so it is upstream. `injectedFqns` is the set of files the run
actually WROTE, so it is emitted. Compared directly the test is false for every RENAMING port, and
`Substituted` had therefore never once been produced by one. libGDX's published map carried
`Dropped com.badlogic.gdx.utils.Json` beside `Added sge.utils.Json`, two rows apart in the same
file, with nothing joining them.

Nothing failed until a dependent read it. gdx-gltf is the first port in the corpus to REFERENCE an
injected replacement — its `GLTFExtensions`, `GLTFExtras`, `GLTFMorphTarget`, `GLTFExporter` and
both data-file resolvers all use `Json` — and `PortMapTransform` told it, ten times, that the base
"emits nothing at that name and nothing replaces it" about a type the base ships and it compiles
against. **port-map 10 -> 0**, no emitted text moved anywhere, every other check count identical.

This is the same failure §4.56 records for `dropped-types.tsv`, at a third artifact, and the same
fix: the run is the last place that holds the manifest name and the rename map together, so it
writes BOTH, translating with `PackageRenameTransform.renamed` — never a hand-written `startsWith`,
because a prefix must cut only at a separator (`up.stream` must not cover `up.streaming`, pinned).

The generalisation worth carrying: **wherever a port artifact has a POLICY column and an OBSERVED
column, check whether some predicate compares one to the other.** Both of the ones found so far
were silent, produced no finding of their own, and were discovered only when a consumer acted on
the wrong answer.

*Fix kind: (a) engine — fixed in `PortMap.of`; `PortMapSpec` pins the renaming case and the
separator rule.*

### D7. An inherited drop leaves a CALL SITE the engine had no seam for — CLOSED; what remains is POLICY

**Title, for renumbering: "an inherited drop leaves a call site the engine has no seam for".**

A base drops a member; the dependent still calls it. The engine has two seams for code it must not
translate mechanically and both are WHOLE-DECLARATION: `Substitutions.dropMethods` removes the
member, and `MethodBodyTransform` replaces the body of the method that CONTAINS the call. Neither
can say *keep this method, rewrite this one call in it*.

Ashley met this and got lucky: its offending site was a one-line forwarder
(`ImmutableArray.toArray(Class)`), so `dropMethods` on the forwarder was both available and right.
gdx-gltf's is not. libGDX core drops `Array#toArray(Class)` — the `java.lang.reflect.Array` overload
upstream itself deprecated in favour of a portable `ArraySupplier` twin — and
`MeshLoader.loadMeshes` calls it once, at line 252 of a 464-line method that is otherwise entirely
mechanical. `MethodBodyTransform` on that method would fork 200 lines from upstream permanently to
change one argument; `dropMethods` on it would delete the port's mesh loader. **1 error, and no
seam.**

Note the finding IS reported, twice and correctly classified — `RewriteTrace`'s signature check
("a call to a member with no declaration: `@6#toArray(java.lang.Class)`") and the port map's
`Dropped` record both name it with the Java file and line. What is missing is not the diagnosis, it
is the repair.

**BUILT — `CallSiteSubstitutionTransform` (DESIGN.md §8.12).** Keyed like `dropMethods`
(`owner#name(P1,P2)`, overload-exact through `PolicyBinder`), the value an expression template with
`{recv}` / `{arg0}..{argN}`, spliced as TREES so the package rename and every retyping phase still
reach the arguments, and `SurfacePolicy`-fingerprinted because two modules rewriting a shared call
differently is drift. Default-off; an empty map is a no-op.

Two things it took to actually reach the case above, both of which look like details and are the
entry's whole point:

- **a DROPPED member has no declaration symbol**, so the declaration-side binding answers "bound,
  nothing to point at" — correct, and useless to a phase that rewrites calls. `bindCallee` takes the
  symbol the frontend interned from the REFERENCE, which exists for every caller of a dropped
  member, and suppresses the `SyntheticTarget` refusal on that path only (its structural test cannot
  tell a reference-side interning from an engine-minted member). Spec'd both ways in
  `PolicyBinderSpec`, and end to end in `CallSiteSubstitutionSpec`.
- **ordering is a silent no-op waiting to happen.** A call-site entry placed AFTER a phase that
  re-points the same callee (`CollectionsTransform`'s statics table maps `Collections.sort`) matches
  nothing, with every count unchanged. A key that bound and rewrote ZERO sites is therefore its own
  finding.

**What remains is POLICY, and it is one library's.** The mechanism reaches gdx-gltf's three dead
`Json` sites — dry-run measured **3/3 bound, 3 sites, 0 policy findings**, at
`SeparatedDataFileResolver.java:30`, `BinaryDataFileResolver.java:97` and `GLTFExporter.java:241`.
The entry is NOT enabled, because the other side of it does not exist: the reference hand port
replaced the reflective path with 2,268 lines of Jsoniter codecs, a decision that port has not made,
and naming a codec that does not exist would trade three inert calls for three that do not compile.
`MeshLoader.java:252` (`Array#toArray(Class)`, the original 1-error case) is now reachable by the
same mechanism and wants the same thing: a replacement expression the port must decide on.

*Fix kind: was (b) with no phase; the phase now exists, and each remaining site is (b) POLICY in
that library's manifest.*

### D8. A TYPE REDIRECT that only rewrites `TypeRepr` is a PARTIAL redirect — and its own contract said that was impossible

`TypeRedirectTransform` is the (b) mechanism a dependent uses for a type it cannot ship and cannot
inject at the other module's FQN. Its doc promised "every reference moves together, so a partial
redirect is impossible". It rewrote `transformType` and nothing else, and a type occurrence is only
one of the THREE ways a reference to a type reaches the emitted file:

| the occurrence | where the emitter reads the name from | reached by `transformType`? |
|---|---|---|
| a field/parameter/result type, a `new`, a cast, a type argument | the node's `TypeRepr` | yes |
| a static access `T.m(…)` / `T.F` | `Tree.Ident`'s **SymId**, or — when `T` was PARSED — the member symbol's **OWNER**, via `TirEmitter.staticThroughInstance` | no |
| `@T` on a declaration | `Symbol.annotations`, see M5.8 | no |

**And the second row has two answers, which is the part that costs a cycle.** A redirected type the
frontend never parsed (a jar on the frontend classpath) reaches its statics through an explicit
`Select(Ident(type), member)`, so remapping the `Ident`'s symbol is enough. A redirected type the
frontend DID parse — a resolution root, which is the ORDINARY case for a dependent — is re-qualified
by the emitter from the member symbol's owner, which deliberately ignores the qualifier in the tree
(that behaviour is right: it is what turns Java's legal `instance.staticMethod()` into Scala's
required `Type.staticMethod()`). Remap the `Ident` alone and the emitter silently undoes it one
layer later.

**The second answer is a TWIN, not a re-pointing of the original owner — that was measured worse.**
Moving the members' `owner` onto the target is the same fix in fewer lines and is what the first
attempt did. But a redirected type is normally one this module RESOLVES AGAINST, so its members are
the BASE's declarations, and moving their owner detaches them from the unit they belong to: the
ownership climb (`CLAUDE.md` §4.56) stops reaching a `program.units` symbol, and every per-site check
that filters "the base's declarations, not mine" (D2) stops recognising them. Measured on a dependent
that redirects one parsed type: **`port-map` 0 → 6**, all six inside the redirected type's own Java
file, in a run whose emitted text was **byte-identical** — no member digest moved, so only the check
diff said anything at all. Mint a new symbol with the same name and signature owned by the target
instead, re-point `Ident.sym` / `Select.sym` / `Apply.method` at it, and leave the base's own symbols
exactly as they were: 6 → 0, 0 members moved.

Twin only the STATIC members. An instance member is reached through a receiver whose TYPE the phase
has already moved, so it needs nothing, and twinning it is surface with no reference behind it.

Measured on the first library to redirect types WITH a static surface: **26 references** across 10
redirected types — **23 static calls and 3 annotations** — every one of them naming a package the
port does not declare, in a port whose every check reported clean and whose emitted files were
otherwise complete. The first library to use the phase redirected one type with no statics and no
annotation use, which is why the promise went untested for as long as it did.

Be exact about which half each fix answers, because the corpus only measures one of them. That
library resolves its redirected types from a JAR, so all 26 sites were the FIRST shape and the
`Ident` remap alone closed them (`0` members changed when the owner half landed on top). The
PARSED shape — the ordinary one for a dependent, and the one the emitter re-qualifies — is proven
by `TypeRedirectTransformSpec`'s static-call case and by no corpus number.

Two rules that fall out:

- **A phase whose doc claims totality owes a spec PER OCCURRENCE KIND**, with the negative half
  (`assertNotEmits`) — the positive one passes on a partial redirect.
- **A port that redirects a type it OWNS must also DROP it.** Moving the members' owner is exact only
  because the contract is that this module does not ship the type. A module that ships it emits the
  declaration under one name and every reference under another — a `trait Disposable` nothing
  refers to, beside classes that all extend the target. The redirect re-points REFERENCES and never
  deletes a declaration, which is not an oversight (deleting is `Substitutions.dropTypes`, and the
  two are separate decisions for §1.5's reason: the DROP binds every module that sees the type).
  Nothing enforces the pairing and nothing can: it is a coherence property of the configuration.
  Measured, not assumed — `TypeRedirectMemberRenameSpec`'s owned-and-parsed case runs a redirect of
  a type the fixture declares and asserts exactly this shape, statics twinned and members renamed,
  with the orphan declaration still there. **And now EXERCISED IN PRODUCTION**: Stage P's P1
  redirects libGDX's own `Disposable`, and there the paired `dropTypes` entry is the whole of what
  keeps `sge/utils/Disposable.scala` from shipping beside the 47 types that gain the target as a
  parent — 598 files (11 dropped) → 597 (12), the emitted file count being the only artifact that
  says so (`PROGRESS.md` §11.15, delivered). Nothing else would have: with the drop omitted that
  port still compiles at 0 errors and every check reports the same number. Confirming the pairing
  means reading a port's `dropTypes` against its `redirects` by hand.

  **And a drop whose target already exists has nowhere to put its porter note — correctly.** A
  dropped TYPE's note is `PorterNote.NotInTree` (`CLAUDE.md` §4.575), carried by the INJECTED file
  that supplies its FQN. A redirect-and-drop pair pointed at a type the platform already ships has
  no injection, so it emits no note, and `NoteCoverageCheck` stays 0/0. The `DroppedType` row in
  `decisions.tsv` is the whole record, which is the right place for it: the reader's question is
  asked at the REFERENCES, and every one of those carries the redirect's own `renamed-member` note.

*Fix kind: (a) engine — the mechanism was incomplete, not the policy. Done; pinned by
`TypeRedirectTransformSpec`.*

### D9. A (b) phase configured in a BASE manifest is one no DEPENDENT may ever configure — and adding one to a base that has dependents cannot land. **P1 blocked, 2 ports fatal** — **CLOSED by M5m**

`PortManifest.extendedBy` composes every row §1.5 calls shared, and it composes them two different
ways. `dropTypes`, `dropMethods` and the rename maps are unioned KEY BY KEY. `surface` is a
`List[Phase]`, concatenated and deduplicated BY IDENTITY — and a phase's policy is a constructor
argument, so two instances holding two halves of one table never merge. `ManifestAgreement` then
fails the run: one phase NAME carrying two fingerprints in one effective pipeline is
`SurfaceDivergence`, which is fatal.

That check is right and must not be weakened where it stands: it cannot tell two DISJOINT tables
from two DISAGREEING ones, and the second is exactly the drift §1 exists to stop.

**Both halves are measured, and the second is what makes this an engine gap rather than a manifest
mistake.** Stage P's P1 adds one `TypeRedirectTransform` (`Disposable -> AutoCloseable`, with
`dispose -> close`) to the libGDX BASE manifest. Two dependents have declared a
`TypeRedirectTransform` of their own since the phase was written, neither of which has ever
overlapped with the base — because the base had none at all:

| what was tried | what the run said |
|---|---|
| base gains the phase; ashley keeps `ReflectionPool -> ComponentPool`, screens keeps its ten guacamole entries | **`ashley` 1 fatal `SurfaceDivergence`; `screens` 1 fatal `SurfaceDivergence`.** Emitted code untouched, every other count identical — the manifest check is the only thing that sees it |
| the escape: the base takes the dependent's entries as a parameter and builds ONE phase (`LibgdxPolicy.core(root, redirects = …)`) | **`ashley`: `BaseMapStale` (`policy fea528bdefdd4235 vs 2f1e063ead131c7d`) → the published map REFUSED → `BASE SURFACE … 926, 309 of them FATAL`.** The dependent is now declaring a base that never ran |

The second row is not a detail of that attempt, it is a PROOF: D1's contract is that the base
manifest a dependent declares is the base *as the base ran it*, and the base runs ONCE and publishes
ONE map. N dependents each wanting their own entry cannot all agree with one map. So the base
manifest is structurally not a home for a dependent's policy, and the dependent's own surface is
closed by `SurfaceDivergence`. There was no third place — until M5m made the two instances ONE,
which is the closure at the end of this entry; both statements above still hold of the manifests, and
what changed is that the pipeline no longer has two of the phase to disagree about.

Note the `.conf` path has the identical hole — `base = "…"` is `base.extendedBy(own)`, and a base
conf and a dependent conf that both declare `redirects { }` build two instances exactly as the
Scala path does. This is not a corpus accident.

**Do NOT retry** either row above. And do not reach for the near-misses: renaming the dependent's
phase (the name is `def name` on a `final class`, and a per-instance name would defeat the drift
check for real drift); putting the dependents' entries in the base's own table permanently (the base
would name `de.damios.guacamole.*` and `com.badlogic.ashley.core.ComponentPool`, which is §1
inverted, and the keys would never fire on the base's own run); `PortManifestConfig.fromPortMap`
mirroring (a manifest with no `bases` is itself a fatal finding for a run with foreign resolution
roots).

**What this does NOT block, measured by Stage P's P2.** The limit is a phase INSTANCE count, not a
policy count. A base that already carries a (b) phase may gain any amount of new policy ON THAT
INSTANCE, because `extendedBy` never has two of it to merge — the dependents inherit the one value
and their effective surface is identical by construction. libGDX's base `CollectionsTransform` gained
a `retarget` table this way with **`manifest` 0 on all thirteen ports**, and the fingerprint change
propagated to nine published port maps with nothing else moving. The question to ask before writing
a base policy is therefore not "is this a (b) phase?" but *does any dependent CONSTRUCT this phase?*
— one grep over the ports, and if the answer is no, D9 has nothing to say.

**CLOSED, and now EXERCISED IN PRODUCTION.** M5m landed the contract; P1 then re-issued unchanged
and is delivered (`PROGRESS.md` §11.15). The fold is visible in `screens`, where ONE `type-redirect`
instance carries both tables — the base's `Disposable → AutoCloseable` (2 `RetypedSignature` + 7
`RenamedMember` rows against screens' own declarations) beside screens' ten guacamole entries (33
rows) — and in `ashley`, whose merged instance holds the base's entry (0 rows: ashley emits no
`Disposable` reference) beside `ReflectionPool → ComponentPool` (6 rows), admitted by the `governs`
screen because the base DROPS that type. **`manifest` 0 and `port-map` 0 on both**, against `1 fatal
SurfaceDivergence` each for the identical configuration before M5m. Every `policy=` digest in the
family moved and every dependent stayed fresh against the base's published map, so the D1 half held
too. The two rows in the table above remain what NOT to retry; they are now history rather than
the state.

*Fix kind: (a) engine, and it is a CONTRACT change rather than a condition: `Phase` needs a way to
say "my policy is a table, merge it with the base's", `PortManifest.effectiveSurface` needs to fold
same-name phases through it, and `SurfaceDivergence` then fires only where a merge is refused — same
key, different value. Every parameterised phase has to declare its own answer, because the merge is
not always a union (an ordered list, a first-match table and a set compose differently). Done —
M5m; pinned by `DESIGN.md` §8.13 and by P1 as its first consumer.*

**CLOSED by M5m** — `MergeablePolicy` (`api`), `PortManifest.surfaceFold`, and
`TypeRedirectTransform`'s own merge declaration; `DESIGN.md` §8.13 is the as-built. The two rows
above are what the mechanism had to satisfy and both do: the FIRST now composes into ONE phase
holding both tables (`ManifestSpec`'s two-module fixture runs it end to end), and the SECOND is
preserved by WHERE the fold runs — `surfaceFold` folds `policyChain`, so a base's own
`effectiveSurface`, and therefore the `policy=` digest its map publishes, is byte-identical before
and after. Only the dependent's EFFECTIVE pipeline holds the merged phase.

Two things this did NOT relax, both negative-tested (`SurfaceFoldSpec`): a phase that declares no
merge is the same fatal `SurfaceDivergence` it always was — the engine does not guess how an ordered
list or a first-match table composes — and a merged-in key naming a subject inside a base's
`governs` that the base EMITS is a new fatal `SurfaceIntrusion`, because a dependent re-shaping the
shared surface is the drift this whole page exists to stop. A subject the base DROPS is allowed and
is the ordinary case; that is the criterion, not the prefix, and a bare prefix test would have
refused `ashley`'s `ReflectionPool` redirect, which is the one port the mechanism exists for.

Stage P's P1 is unblocked and re-issues unchanged; `PROGRESS.md` §11.15 keeps its numbers.

---

## 9. Asserted, not measured

Clearly separated because these are reasoned, not observed — no corpus number stands behind an
OPEN entry here (a bullet marked CLOSED graduated by acquiring one).

- **Duplicate injected-runtime definitions will break the Scala.js and Native linkers** when a second
  module is ported. Confirmed by design reasoning; not observed, because only one module exists so
  far. (`PROGRESS.md` §Publishability item 1.3.)
- ~~An enum constant with a FIELD in its class body is dropped silently~~ **CLOSED by observation**:
  noise4j had two, they cost 4 errors, and `SpoonTir.enumCase` now harvests `CtField` (T8). The
  INITIALIZER-BLOCK and NESTED-TYPE halves of that entry remain reasoned rather than observed — still
  zero sites across four libraries.
- **An assignment used as a VALUE re-evaluates its left-hand side.** `return a[f(x)] = v` lowers to
  `Tree.Block(List(Assign(lhs, rhs)), lhs)` with the SAME `lhs` tree in both slots, so it emits
  `{ a(f(x)) = v; a(f(x)) }` — and the compound form `a[f(x)] += v` evaluates `f(x)` three times.
  Java evaluates the target subexpressions exactly once and yields the assigned value. 7 sites in
  noise4j's `Grid` (`return grid[toIndex(x,y)] = value` and its five arithmetic siblings), every one
  with a PURE index, so no port has yet been wrong because of it — which is why this is here and not
  in a numbered entry. `a[i++] = v` used as a value would double-increment, with a green compile and
  no count moving. The simple form has an exact fix that is also cheaper (`{ val $v = rhs; lhs = $v;
  $v }` — Java yields the assigned value, not a re-read); the compound form needs the LHS decomposed
  into a bound receiver and a bound index, which is the part nobody has built. *(a), unbuilt.*
- **A `StaticForwarderTransform` wrapper whose overloads are not all receiver-first** would be
  rewritten wrongly: members are matched by **name only**. Safe under current policy; worth a guard
  when a second library configures it. (`PROGRESS.md` §Publishability.)
- ~~A typo'd policy key silently no-ops~~ **CLOSED**: `PolicyReport` collects a classified
  `never matched` finding from every parameterised phase, `SubstitutionCheck.dangling` covers the
  drop side, and the migration prints and baselines both. The check has now also FIRED in anger —
  it caught `getName` in the `ClassReflection` forwarder, a key dead since the first draft
  (`policy 1->0` when removed).

## 9.5 Control flow — what a `break` really leaves, and the boundary that steals it

### F1. A java LABEL sits on ANY statement, not only a loop. **55 → 10 residues**

`LabeledStatement: Identifier : Statement` (JLS 14.7) — a label goes on an `if`, a bare block or a
`switch` as readily as on a loop, and `break L` leaves exactly THAT statement. A loop-only encoding
(a `label` field on the loop nodes) therefore cannot express most of them: on libGDX core, 45 of the
55 untranslated jumps were labelled breaks to a NON-loop — `JsonReader` `outer:` on an `if` (30
after the switch fallthrough lowering duplicates the arm six times), `TextField` `keys:`/`selection:`
on bare blocks (11), `GlyphLayout` `runEnded:` on a block (3), `Table` `outer:` on an `if` (1).

Dropped, they are SILENT: the port compiles, every count holds, and the code simply runs on.
Measured on the real `JsonReader` with a differential event probe against javac's own build of the
same file — before the fix, `{a:true,b:null,c:1.5}` produced `bool(a,true)` **and** `string(a,true)`,
a spurious second event for every unquoted boolean, null and number; after, the event sequences are
identical.

What shipped: a `Tree.Labeled(name, stmt)` WRAPPER, minted by the frontend for a labelled non-loop
statement only. A loop keeps the label in its own node, because that same label is `continue L`'s
target and the two boundaries go in different places (around the loop / around its body); splitting
that decision across two encodings is how a label ends up claimed twice. Emission is a NAMED
`scala.util.boundary` around the statement, and none at all when nothing breaks to the label.

*Fix kind: (a). If your library's port shows a residue for a labelled jump, the node is there —
check the frontend is minting it, not that the label needs a new mechanism.*

### F2. A `boundary` the emitter INTERPOSES steals the enclosing loop's un-annotated jumps

The hazard F1 creates, and it is not visible in any count. `scala.util.boundary.break(())` with no
`using` resolves the INNERMOST given `Label`, so the moment the emitter puts a new `boundary`
between a loop and an unlabelled `break`/`continue` under it, that jump silently retargets — it
leaves the labelled statement instead of the loop, and the loop runs on.

Naming the inner boundary does NOT shield anything: `boundary { (l: Label[Unit]) ?=> … }` makes `l`
a context-function parameter, which is exactly what "innermost given" means.

So every construct that opens a boundary of its own has to force the ENCLOSING one to be named:
`TirEmitter.interposes` answers "does anything in this body render with a boundary" and
`loopWithJumps` names `brk$`/`cnt$` when it does. It is an OVER-approximation on purpose — it does
not check that an unlabelled jump is really underneath. An unused name costs one identifier; a
missed one is a control-flow change with a green compile.

*Fix kind: (a). The rule generalises: any lowering that introduces a scoped, implicitly-resolved
capability must re-examine every use that was resolving to an outer one.*

### F3. An unlabelled `break` in the MIDDLE of a case ends the CASE. **10 → 0 residues**

The remaining 10. The frontend already deletes the break that TERMINATES a case (scala's `match`
ends the arm anyway) and lowers real fallthrough by TAIL DUPLICATION — the next case's statements
are copied into this arm. So a `break` still standing in a case body means "stop HERE", and what ran
on past the dropped one was code java had put in a **different case**.

`GlyphLayout` is the worked example: `case '[': … if (length >= 0) { …; break; } …` falls through
into `default: continue outer`, so a successfully parsed colour tag fell into the `continue` and
re-scanned the run. Green compile, no count moved.

Scala's `match` cannot leave an arm early, so the arm gets its own NAMED `boundary` (named for F2's
reason). Note this is the same defect as the dropped `break`, one construct along — if your library
has a switch-heavy scanner, this is where it hides.

*Fix kind: (a).*

---

## 10. Comments (trivia) — what still does not survive, with its number

The governing rule is `CLAUDE.md` §4.58. This section is only the residue: what is measured to be
lost after the TIR path carries comments, so nobody re-derives it.

**What carrying them costs, measured by re-emitting every port with the harvest off** (a one-line
kill switch, `CLAUDE.md` §4.6) — emitted bytes before → after:

| port | before | after | delta |
|---|---|---|---|
| libGDX core (604 files) | 5 301 863 | 7 095 303 | **+33.8 %** |
| libGDX tests (29) | 237 786 | 262 896 | +10.6 % |
| Ashley core (23) | 69 345 | 104 755 | **+51.1 %** |
| Ashley tests (18) | 154 867 | 164 307 | +6.1 % |
| simple-graphs core (33) | 125 579 | 174 383 | **+38.9 %** |
| simple-graphs tests (7) | 35 348 | 39 889 | +12.8 % |

A THIRD of the emitted text of a well-documented library is its documentation, and a test suite's
share is a fraction of that. Nothing else moved: compile errors 0 on all four lanes, every other
check count unchanged, determinism green, `srcmap` unit and member COUNTS identical (594 / 19 257 on
libGDX) — only 7 159 of those 19 257 member digests, which is exactly the members that gained a
comment.

### V1. A comment the FRONTEND claimed and dropped, and one the EMISSION consumes. **222 → 100 → 0 lost**

CLOSED as a loss; what is left is a COUNTED residue (`trivia(recovered)`), which is a different
thing and is below.

`TriviaCheck` compares the Java text to the emitted text on every run. On libGDX core it first
reported **222** dropped comments; recovering the promoted constructor's Javadoc onto the CLASS took
it to **100**, and this entry then recorded the remaining 100 as three emission-side categories. **That
framing was wrong about where the loss happened, and the correction is the lesson**: the categories
named the CONTEXTS (a merged `switch` arm, a lowered `break`) and every traced site died on one line
of the FRONTEND — the statement fold accumulated comment-statements into a pending buffer, folded
them onto the NEXT statement, and DISCARDED them when there was none. They had been claimed by then,
so no coarser harvest could recover them either: **claim-then-drop**. Reading a residue's category
names as a diagnosis cost this entry two years of being believed.

What the three mechanisms of `DESIGN.md` §8.8 did to it, measured over thirteen ports:

| mechanism | libGDX core | corpus total |
|---|---|---|
| — | 100 | 233 |
| position-based file-leading harvest (V3) | 65 | 198 |
| `Tree.Block.trailing` — the frontend KEEPS the leftover | 18 | 77 |
| the recovery backstop + the `deliberate` lane | **0 lost** | **0 lost** |

Two things this entry still says, both unchanged by the fix:

- **Do NOT hoist to the nearest surviving node silently.** A comment that describes a statement,
  printed above a method, is worse than absent, because it now says something false. What makes the
  backstop admissible is the marker: every relocated comment carries `/* trivia: recovered from
  <path>:<line> */`, which turns it into a QUOTATION with the coordinates a reader can check.
- **`recovered` is a residue, not a success.** It counts the comments the attachment channel could
  not place, at member granularity rather than at the statement they were written on, and its
  per-file breakdown is the work list for giving each remaining category an honest home.

*Fix kind: (a) engine — the frontend's statement fold, the emitter's block rendering and the
emitter's backstop. No library policy anywhere in it.*

### V2. `TirPrinter.canonical` must NOT carry trivia, and `TirPrinter.digest` MUST

Two consumers with opposite requirements, and satisfying one with the other is a silent defect
either way:

- A phase-boundary dump exists to show what a phase did to the TREE. libGDX's `AssetManager` carries
  ~400 lines of Javadoc that would bury the nodes a phase actually moved, and no phase reads a
  comment. So `Style.canonical` elides it — and a `Commented` wrapper prints as its statement alone,
  so a dump is identical whether the Java had a comment there or not.
- `TirCacheKey` keys the ACTION CACHE on `TirPrinter.digest`, and the cache stores EMITTED TEXT. A
  digest over the canonical form would make a source edit that changed only a comment a cache HIT
  that re-serves the previous file with the previous comment — silent, surviving a `clean`, and
  moving no count. `digest` therefore renders `Style.identity` (canonical + trivia): everything that
  reaches the emitted file, and nothing that does not.

*Fix kind: (a) engine.*

### V3. Spoon attaches only ONE of several consecutive FILE-LEADING comment blocks — 9+ sites

CLOSED by a POSITIONAL harvest. Two corrections to what this entry used to say, and the second one
is why it should not have been left open:

- **WHERE the second block goes is now known**: the PACKAGE DECLARATION. Where a java file opens
  with two consecutive block comments, `CtCompilationUnit.getComments` carries the first and
  `CtPackageDeclaration.getComments` carries the second — the one attachment site `SpoonTir` never
  read. Probed and pinned in `testkit`'s `FileLeadingTriviaSpec`, so a Spoon release that changes it
  fails a spec instead of silently making a mechanism redundant.
- **The mitigation recorded here was FALSE beyond the two files it was measured on.** "The licence
  text itself survives, because it is the FIRST block" holds for gdx-vfx's `LensFlareEffect` and
  `LevelsEffect`. It does not hold for libGDX, which had **seven more files of the same shape inside
  its own residue** — `GL30/31/32`, and the Ragel-generated `JsonReader`, `JsonSkimmer`,
  `PatternParser`, `XmlReader`, where three `//` generator lines are the first block and **the
  APACHE NOTICE ITSELF is the one that was dropped**. That makes this a §4.57/§4.58 obligation and
  not a tidiness item, and it is the reason a mitigation measured on one library must not be written
  down as a property of the engine.

Reading one more of the parser's slots is NOT the fix, and that is the whole shape of the lesson: the
next file lands in a slot nobody enumerated, and no set of slots can say which of two blocks came
FIRST — the order of a licence and the banner above it is text's answer alone. The harvest is
therefore positional (`CommentScanner.firstCodeOffset`: a comment is the file's iff no code precedes
it), the parser-attached comments are merged in by OFFSET so a block both sides see is emitted once,
and the header CLAIMS its spans so a leading block the parser also attached to the type cannot be
emitted twice.

Measured: **libGDX core trivia 100 → 65, gdx-vfx 11 → 9**, every other check count identical on all
thirteen ports, 26 + 4 whole-file digests moved.

*Fix kind: (a) engine — frontend.*

---

## 11. Literals and the emitted file's LEXICAL correctness

The emitter's output is TEXT, and two facts about Scala's lexer decide whether that text is a file
at all. Neither is visible to any check: the run reports its usual numbers and the compiler then
fails at a position that has nothing to do with the construct that caused it. Both were found by
porting anim8-gdx, the first corpus library whose difficulty is per-LINE rather than per-file — 16
files, 19,594 lines, of which `ConstantData` is 108 lines holding four ISO-8859-1 string literals of
47,935 and three x 6,390 characters.

### L1. A literal's VALUE must be RE-ESCAPED — **1,334 errors from ONE file**

CLOSED. `Constant.StringC` holds DECODED text, so every character has to be put back in a form Scala
accepts inside `"…"`. The emitter escaped five (backslash, quote, `\n`, `\r`, `\t`) and passed
everything else through raw. That is a file that does not parse the moment a literal holds anything
else:

- a raw control character is an "illegal character" outright;
- a raw NEWLINE **ends the literal**, and every byte after it is read as source — which is where
  1,334 of anim8's 1,383 first-run errors came from, all attributed to the two lines the lexer
  happened to be on;
- a lone SURROGATE cannot be encoded in the UTF-8 the file is written as, so it would be replaced on
  the way out and the VALUE would silently change — no error at all, the §3 shape.

`\uXXXX` is the general escape and, verified against 3.8.4, it is a Scala 3 escape SEQUENCE inside a
literal, not the Scala 2 source pre-processing that was removed — so an emitted `\\u` cannot leak.
Ordinary non-ASCII text is left VERBATIM: the file is UTF-8 and Scala reads it as UTF-8, and
escaping it would churn every port's diff for characters that already round-trip.

Note what survived three libraries. libGDX has exactly four affected files (`JsonReader`,
`PropertiesUtils`, `CharArray`, `JsonSkimmer`, 11 members) and they held only `\b`, `\f` and NUL in
CHAR literals — which dotty happens to tolerate, so the port compiled and nobody looked. **A corpus
that has not met a construct is not evidence that the construct is handled.**

*Fix kind: (a) engine. Built; `EmitterLiteralSpec`, whose strongest assertion is that NO raw control
character appears anywhere in the emitted source.*

### L2. A prefix operator and its operand are TWO tokens — **48 errors in one method**

CLOSED. Scala's lexer takes a maximal run of operator characters as ONE identifier, so a prefix `-`
written directly against an operand that already renders with a leading `-` produces `--`: a
different token and a syntax error, not a double negation. The emitter rendered operator and operand
adjacent with no separator.

The java form is routine in hash-mixing code — `x * -0xC13FA9A902A6328FL`, where that hex literal's
`long` VALUE is `-4521708957497675121`. anim8's `AnimatedGif.analyzeOverboard` does it fourteen
times and produced 48 E040 "',' or ')' expected, but long literal found".

Parenthesising the OPERAND is the only fix that cannot mis-lex. A separating SPACE was rejected on
inspection rather than measured: `- -4L` reads as an infix application waiting for a left operand.
The test is on the two CHARACTERS that would meet, never on the operator's name, so an operator the
emitter gains later is covered without being listed.

*Fix kind: (a) engine. Built; `EmitterLiteralSpec` carries the negative half too — `-y` and `!b` are
untouched.*

### L3. A CLASS LITERAL needs a CLASS — an all-static class named by one must not collapse

CLOSED. An all-static java utility class emits as a Scala `object`, which is a real improvement: its
statics and its nested types then live together and see each other by simple name. **D6 is the other
half of the same guard** — the consumer-names-it-as-a-type face, found independently on gdx-gltf;
both are answered by one `TirEmitter.typeNamedElsewhere`. The collapse
already withholds for the two constructs an object cannot serve — something EXTENDS it, something
`new`s it — and `classOf` is the third, because an object's only type is `X.type`.

The idiom that finds it is java's log tag, inside the class it names:

```java
class VfxGLUtils {
  private static final String TAG = VfxGLUtils.class.getSimpleName();
  // …every other member static too, so the collapse fired on the very class the literal names
}
```

`Expected a type, but found a term: VfxGLUtils`. **`classOf[VfxGLUtils.type]` is the trap, not the
answer**: it compiles, and `getSimpleName` on it is `"VfxGLUtils$"` — so the port would carry a
different string than java, with a green compile and no count moved (`CLAUDE.md` §3). Withholding
the collapse costs nothing: the statics move to the companion object, which is where the collapse
put them anyway, so no call site changes.

The general shape, and the reason this is here rather than in a commit message: **the collapse is
withheld by a SET of symbols the program uses in a way an object cannot serve**, one lazy scan per
construct, each walked with `StandardTraversal`. A fourth construct is a fourth set, not a fourth
condition inside the guard.

**0 members moved** on any other corpus port — arithmetic again, since `classOf` on an object is a
compile error and every other port compiles. Spec: `StaticCollapseSpec`, all three guards plus two
negatives (the collapse still fires without them; the guard is per-symbol).

*Fix kind: (a) engine.*

---

## 12. Threading a CONTEXT through a program

Turning a static holder into a `using` parameter is a whole-program reachability, and three of its
edges are not call-graph facts. What follows is what was measured wrong on the way to getting it
right (`DESIGN.md` §8.4).

Prefixed `CT`, and that is not decoration: these entries were minted `X1`–`X4` beside §6's `X1`–`X5`,
so every citation in the repository read as either section for as long as both existed. An id in this
file is unique across it, and an entry whose id may ever move carries its own **"Title, for
renumbering"** line so a citation can be resolved by title when the number cannot.

### CT1. An anonymous body's LEXICAL HOME is not in the owner chain — the capture lands on the CLASS

**Title, for renumbering: "an anonymous body's lexical home is not in the owner chain".** CLOSED.
(a) engine.

CLOSED. The frontend interns an anonymous class with its **enclosing class** as owner, because that
is where its emitted name comes from (`Outer$1`). A pass that finds "the declaration this body was
written inside" by climbing `Symbol.owner` therefore reaches the CLASS and loses the method, and
that is wrong in two directions at once — measured on the mechanism's own fixture:

- the capture landed on `Listeners` rather than on `Listeners#install`, which under `attach =
  method` is a boundary, so the read stayed a global read and was counted as a seam that did not
  exist;
- and the anonymous `Runnable#run` was then offered to the closure as an ordinary method, where its
  external `java.lang.Runnable` anchor froze it — a `frozen-component` refusal for a body that
  never needed threading at all.

The xref holds the answer and nothing has to re-walk the tree for it: every `new T(){ … }` is an
`Instantiate` usage of `T` whose SITE is the `New` node carrying the body and whose `enclosing` is
the declaration it was written in. Do NOT reach for a private traversal that tracks "where am I" —
that is the shape `CLAUDE.md` §3 forbids, and the index already answers the question.

Same rule one level down: a MEMBER of such a body is reached from the member, so a climb that only
tests the symbol it was handed sees an ordinary method. Look UP one level before deciding.

*Fix kind: (a) engine.*

### CT2. A `lazy val` cannot receive a context — the deferred static is a CACHE PAIR, not a `lazy val`

**Title, for renumbering: "a lazy val cannot receive a context — the deferred static is a cache
pair".** ASSERTED. (b) configure.

ASSERTED, and it is a language fact rather than a measurement. A class initialiser that reads the
holder cannot be threaded (it has no signature) and cannot be made a `lazy val` either: **a `lazy
val`'s initialiser has no parameter list**, which is precisely the problem being solved. A
null-sentinel cache is not the answer either — a primitive-typed static legitimately holds its zero,
so the sentinel fires forever.

What works: `private var f$set: Boolean` / `private var f$value: T` and a `def f(using T): T` that
reuses the FIELD'S OWN SYMBOL, so every read in the program keeps naming it and no call site changes.
It does **not** reproduce the JVM's class-initialisation lock, which is why it is per-site opt-in
with a `DeferredInit` decision and a porter note that says so.

*Fix kind: (b) configure — `sites { "…#<clinit>" = "lazy-init" }`, per site, never a mode.*

### CT3. An anonymous `(using T)` clause is an EMITTER capability, not a phase one

**Title, for renumbering: "an anonymous using clause is an emitter capability, not a phase one".**
CLOSED. (a) engine.

CLOSED. Every parameter the emitter rendered was `name: Type`, so the shape the design specifies —
`(using T)` with no name — was unemittable and a phase would have had to MINT a name. That is the
thing the design rejects on measured evidence: a context parameter named after an emitted root
package shadows it and breaks every fully-qualified reference in scope, and this backend emits
nothing but fully-qualified references.

A `using` parameter whose symbol has an EMPTY NAME now renders anonymously. An empty name is
otherwise impossible — the frontend gives every parameter Java's own — so the rule cannot capture a
real one.

*Fix kind: (a) engine.*

### CT4. A CONSTRUCTOR could not carry a `using` clause — **CLOSED; 5 errors → 0, and the fix is one distinction**

**Title, for renumbering: "a constructor could not carry a using clause — paramss.flatten".**
CLOSED. (a) engine.

**CLOSED.** Kept because the *reason* it was open is a rule, and because the shape of the wrong fix
is worth naming twice.

Adding a `(using T)` clause to a class's constructors is the reference hand port's shape — **82 % of
its 493 attachment sites are constructors** — and the TIR edit for it was always correct: the clause
lands on every `<init>`, the closure propagates down the hierarchy and across every `new`. The
EMISSION failed, at 5 scalac errors on the mechanism's own fixture, in three places that looked
unrelated and were one thing:

- a constructor that had GAINED a parameter was no longer nilary, so `CtorFunnel` declined to
  promote it (C1) and emitted a **synthetic nilary primary beside it** — the class body then had no
  given in scope at all and every `summon` in it failed;
- where it DID promote, the primary's parameter list was rebuilt from the funnel's own plan as a
  FLAT list, so the `given` grouping was **dropped** and the clause rendered as an ordinary
  `class Scene($p: demo.Ctx)`;
- a subclass of the first shape saw TWO applicable constructors — `()` and `()(using T)` — and
  reported an **ambiguous overload**.

**All three are `paramss.flatten`.** A java constructor's parameter list is ONE list; a Scala
constructor's is a list of GROUPS, and every question the funnel asks — *is this constructor nilary,
does it pass its parameters straight through, does its signature equal the slots* — is a question
about what JAVA declared. Flattening answers a different one the moment a phase can append a clause.
So the plan models the split (`CtorFunnel.Plan.givens` beside `primaryParams`), every such question
goes through `CtorFunnel.valueParams`, and the emitter renders the clause as its own group through
`paramClause`. The third cause then has no cases: there is only one constructor again.

Validated by RUNNING, not by asserting (the M2 lesson): the phase's fixture is emitted one file per
unit and put through `scala-cli 3.8.4` in both attachment modes at **0 errors**, and the
synthesised-primary shape — `class Panel protected (sup$0: Int, sup$1: Boolean)(using demo.Ctx)`
reached by two secondaries and by a subclass's argument-free `extends` — compiles and runs. Over one
corpus library (605 types) class attachment emits **578 `(using T)` clauses, 0 of them flattened
into a value parameter and 0 synthesised empty primaries**.

**SCOPE, corrected by CT5: "0 synthesised empty primaries" covers only the primaries the funnel
BUILDS.** Both figures above are read off classes the funnel PROMOTED or SYNTHESISED, which is where
this entry's three causes lived — and they say nothing at all about the third outcome, `Plan.none`,
where there is no primary to count a clause on. 22 of that same run's classes were in it and every
one of them emitted a class with no clause at all, which is CT5 beside this entry. Read the two
numbers as "of the primaries this entry is about", never as "of the port".

Two things NOT to re-derive. **Do not work around this in the threading phase**: a clause the funnel
will not carry is not a clause, and every workaround is a second constructor plan — which is why the
knob RECORDED a `PolicyIssue.Unverifiable` finding for as long as it could not emit, rather than
shipping something. And the refusal cost nothing to keep honest: the dry run that sized it
reproduces unchanged after the fix — **275 threaded declarations in 177 files and 17 seams under
class attachment against 2,497 in 324 and 162 under method**, `frozen-component` **32 → 0** because
class mode changes no method signature at all.

*Fix kind: (a) engine — the constructor region, not the threading phase.*

### CT5. A class the funnel neither PROMOTES nor SYNTHESISES has nowhere to put the context clause — **CLOSED; 57 errors → 3, and the primary hosts the clause and nothing else**

**Title, for renumbering: "an implicit nilary primary carries no using clause".** CLOSED. (a) engine.

CT4 closed the clause for the two primaries the funnel BUILDS — a promoted java constructor
(`plan0` reads `givenClauses` off it) and a synthesised one (`rootGivens.head`). It says nothing
about the third outcome, which is the most common one in real code: **`Plan.none` — no promotion,
no synthesis — where the emitted class relies on Scala's own implicit nilary primary and every java
constructor becomes a `def this`.** There is no primary in the plan, so there is no parameter list
to append a group to, and the emitter has no branch that writes `class X(using T)` for a class whose
primary it did not construct.

The threading phase does its half correctly: the clause lands on every `<init>`, so every secondary
reads `def this(…)(using T)`. What is emitted is a class whose SECONDARIES take the context and
whose own body cannot see it:

```scala
class IndexBufferObject extends sge.graphics.glutils.IndexData {          // <- no clause
  def this(isStatic: Boolean, maxIndices: Int)(using sge.Sge) = { … }     // <- clause here
  def bind(): Unit = summon[sge.Sge].graphics.gl20.glBindBuffer(…)        // <- no given in scope
}
```

Measured on the enablement of `globals-to-implicits` over one corpus library, `attach = "class"`,
**57 scalac errors from 188 threaded classes** — 19 top-level classes plus at least 3 nested ones
lost the clause. Three shapes, one cause:

- **55 × `E172` "No given … is in scope"** — every `summon` in such a class's body, every field
  initialiser that constructs a threaded type, and every private helper the class calls. `Mesh` 14,
  `IndexBufferObjectSubData` 11, `IndexBufferObject` 9, `TextField` 7, `VertexBufferObject` 5.
- **2 × `E051` "Ambiguous overload"** — a java NILARY constructor became `def this()(using T)`
  beside Scala's implicit `()` primary, so a subclass's argument-free `extends` sees two applicable
  alternatives (`BitmapFont` / `DistanceFieldFont`). This is CT4's third cause reappearing from the
  other side: CT4 removed it for a promoted primary by making the promotion possible again, and it
  is still live wherever no promotion happens.
- **the knock-on nobody counts**: a threaded class that has no body `summon` and no threaded
  construction in its initialisers loses its threading SILENTLY — it compiles, and the decision row
  and the porter note both claim a clause the emitted file does not carry.

**Do not work around it in the threading phase** — CT4's standing rule, and this is the same
module. **The caution that was written here when it was open is the part worth keeping**, because
answering it is what makes the fix small: giving such a class `class X(using T)` looks like it needs
each secondary to delegate `this()` first, and the roots of a `Plan.none` class are exactly the
constructors whose `super(args)` is already a counted `OmissionCheck` finding — so making the primary
REAL for this shape would be the synthesis widened to classes where its parent-constructor agreement
(`targets.sizeIs == 1`, `arities.sizeIs == 1`, `formals.sizeIs == arities.head`) does not hold.

**CLOSED, and not that way. The primary HOSTS the clause and delegates nothing.** `Plan.none` gains
exactly one thing — `Plan.givens`, read back off the class's own constructors
(`CtorFunnel.classGivens`, applied by `Plans.hosting` as a post-pass over the decided plans, so all
five roads to "no primary" are covered: the nomination's trait/module/enum guard, its two `case None`
arms, `syntheticPrimary`'s nothing-to-synthesise refusal and the withholding fixpoint's fallback).
`superArgs` stays empty, so the `extends` clause is the bare parent it already was; every secondary
writes the delegation it already wrote; every `super(args)` that was `SuperCall.Dropped` still is and
`OmissionCheck` still counts it. Nothing is lifted into an `extends` clause, so the synthesis's
preconditions are not consulted — this is not the synthesis widened, it is the implicit primary made
SPELLABLE. Measured: the omission census is identical with the clause and without it, on both
fixtures and on the port.

**And it is CLAUSE-CONDITIONAL, which is what pays for it.** With no clause anywhere `classGivens`
is `Nil`, the plan is `Plan.none` unchanged, and the emitted text is byte-identical: **all 13 ports,
0 members changed, every check count identical.** No re-baseline was needed and none was taken.

Three things measured while closing it, none of which a compile-error count would have shown:

- **`this()` reaches a `(using T)`-only primary**, and the shape must NOT gain an empty value group.
  scalac 3.8.4, compiled and RUN: `class X(using Ctx)` with `def this(k: Int)(using Ctx) = { this();
  … }`, `new X()`, `new X(3)`, `class Sub(using Ctx) extends X`, a body `summon` and a field
  initialiser that summons. `()(using T)` is a different signature and would move every call site.
- **The `E051` half is `paramss.flatten` again, one level down in the EMITTER.**
  `TirEmitter.orderBody.degenerate` asked "does this constructor take nothing" of `paramss.flatten`,
  so a nilary java constructor that had gained the clause stopped being degenerate and was emitted
  as `def this()(using T)` beside a primary of the same erased signature — reproduced on a probe as
  `E120` at the declaration plus one `E051` at every argument-free `extends` and every `new C()`,
  which is exactly the `BitmapFont`/`DistanceFieldFont` pair. It reads `CtorFunnel.valueParams` now,
  which restores the answer that class gets with no clause: the degenerate secondary dropped.
  **Promoting the java nilary constructor instead was rejected** — `nilaryPlan` refuses any class
  where a constructor carries `super(args)`, so it would mean widening a promotion past its own
  preconditions to remove a clash the existing rule already removes, for identical emitted text.
- **The silent variant is now COUNTED**, and it had to be, because it is the one shape a green
  compile ratifies: `context-seam`'s fifth kind, `lost-clause`, recorded from the EMITTER's reading
  of the header it wrote (never from the plan — a check reading the plan would have passed on the day
  CT4 flattened a clause into a value parameter). It fires for a `class` that lost one, and for the
  three forms that cannot hold one at all: an all-static class collapsed to an `object`, a `trait`
  (scala's trait parameters are a different feature; the port's `promoteToClass` is the answer), and
  an `enum`, whose primary IS its java constructor — its clause is dropped from the parameter list
  rather than emitted as the unparseable `var : T`. Negative-tested on all four through the real
  emitter.

**What it unblocked.** The globals→context enablement, replayed with the P5 policy in a worktree:
**57 → 3 errors**, and the 3 are the port's own boundary exactly as `PROGRESS.md` §11.12 said they
were (two static field initialisers that construct a now-threaded type, and a hand-written injected
shim registering factories for constructors that now take a context). Everything else in that run
reproduced: 275 threaded declarations, 177 files, 17 seams, 0 refusals, 0 `frozen-component`.

*Fix kind: (a) engine — `CtorFunnel`, the `Plan.none` outcome. Not reachable from any manifest key.*

### CT6. The INSTANTIATE edge does not exist for a GENERIC class, and the `sites` exit the seam NAMES does not exist either — **CLOSED; 3 errors → 1, and both faces are read off the NODE**

**Title, for renumbering: "a generic `new` is not an instantiate, and `sites` cannot reach an
unsuppliable use".** CLOSED. (a) engine, both faces — `ContextNeed`. Measured on the P5 replay after
CT5 closed: the enablement landed at **3 scalac errors**, and neither of the two that are the port's
own boundary had the policy exit the engine's own diagnostic tells its reader to use.

Read this beside CT5. CT5 was the emitter losing a clause the phase attached; these two are the
CLOSURE not seeing an edge, and the ESCAPE HATCH not reaching the site it is named for. Both are
invisible to every count in the run — which is the part that makes them worth an entry rather than a
line in a status file.

#### Face A — `new G<…>()` records a `Tycon` usage, not an `Instantiate` one

`Xref.walkType`'s `AppliedType` arm re-labels the kind it was called with:

```scala
case TypeRepr.AppliedType(tycon, args) =>
  walkType(tycon, UsageKind.Tycon, site); args.foreach(walkType(_, UsageKind.TypeArg, site))
```

so `walkType(tpt.tpe, UsageKind.Instantiate, n)` at a `Tree.New` reaches the constructed class as
`Tycon` whenever that class takes type parameters — for a parameterised `new Cell<String>()` and for
a RAW `new Cell()` alike, because the frontend applies the raw type too. Dumped from the index on a
five-class fixture, which is where the two lines that settle it are:

```
demo.Svc:  Instantiate site=New enc=demo.Named#S        <- non-generic: the edge exists
demo.Cell: Tycon       site=New enc=demo.Tbl#cellPool   <- generic: the same `new`, relabelled
```

Three consequences, in increasing order of how badly they hide:

- **`ContextNeed.expandClass` matches `case Usage(UsageKind.Instantiate, …)`, so the INSTANTIATE
  edge of `DESIGN.md` §8.4's five-edge closure is simply absent for every generic class.** A
  declaration that constructs a threaded generic is not threaded by that construction. Under
  `attach = "class"` it is usually threaded anyway — the class-level over-approximation covers it —
  which is exactly why one library surfaced ONE site.
- **`ContextNeed.anonHome` is built from `Instantiate` usages carrying an anon body, so an anonymous
  subclass of a GENERIC parent has no lexical home** and a capture inside it climbs to the enclosing
  CLASS instead. That is CT1 reappearing, for generics only, from the other side of the same table.
- **And the seam is not counted at all.** The `impose` boundary arm is what records
  `residual-global-read`, and it is never reached, so the site produces a scalac error that no
  number in the run predicts. Measured on libGDX: `Table#cellPool`
  (`static final Pool<Cell> cellPool = new Pool<Cell>(){ … new Cell() … }`) is **1 error and 0
  seams**, while the non-generic `TextField#DEFAULT_ONSCREEN_KEYBOARD` beside it is 1 error and 1
  counted seam. A boundary the engine cannot see is worse than one it refuses (CLAUDE.md §1).

**Where the fix goes, and where it must NOT.** Not in `Xref`: `UsageKind` is a shared index read by
the portability check, the rewrite trace and the external-surface walk, and re-labelling one arm is
its own change with its own measure cycle across thirteen ports. In the PHASE: a usage whose SITE is
a `Tree.New` **is** an instantiation whatever the walk labelled it, which is a structural fact about
the node the phase is holding rather than a conclusion from a name (§4.56). Both readers —
`expandClass`'s usage match and `anonHome`'s — take the same two-line widening.

#### Face B — a `sites` `lazy-init` entry cannot reach an UNSUPPLIABLE USE

The seam's own §1 classification and its per-site detail both say the same thing:

> give the site a `sites` policy, or move the use into a declaration the closure can reach

There is no such policy. `ContextNeed.deferrals` is derived from `reads` — reads of a MAPPED STATIC
— and `planDeferral` then filters each candidate assignment on `readsHolder(rhs)`. A static field
initialiser that CONSTRUCTS a now-threaded type reads no holder at all, so it is in neither set and
no `sites` key can name it. The exit exists for exactly the shape that does not need naming.

**Measured, and the measurement is the point**: both keys added to the libGDX holder
(`TextField#DEFAULT_ONSCREEN_KEYBOARD`, `Table#cellPool` → `ContextSite.LazyInit`), and the emitted
output is **byte-identical** — 1,799 members changed with the entries and without them, `context-seam`
17 both times with `deferred-init` 0, 3 scalac errors both times. **And `policy` stayed at its 2-row
floor**, because the keys BIND: they name real members, `PolicyBinder.bindMembers` resolves them, and
the never-fired machinery has nothing to report. This is a policy entry that is accepted, does
nothing, and is invisible to every check in the run — the third face of "never fired" and the one
nothing currently counts.

#### CLOSED — both readers ask the NODE, and the trigger is the POLICY

**Face A is two readers and one predicate.** `ContextNeed.instantiates` answers *is this usage of
`c` a construction of `c`* from the site: at a `Tree.New` it compares `c` against what the `New`
CONSTRUCTS (`constructedBy` = the head of `tpt.tpe` with any application stripped), and off a `New`
it falls back to the recorded kind. `expandClass`'s instantiate arm goes through it, and `anonHome`
stops filtering on `UsageKind.Instantiate` entirely — the lexical home is *which body this `New`
carries*, which the node says and the label does not.

**Reading the node is what keeps it EXACT, and that is the part a kind-blind widening gets wrong.**
"Any usage at a `New` site is an instantiation" would make `new Pool<Cell>()` an instantiation of
`Cell` — `Cell` is named at that very node as a `TypeArg`, and constructing a `Pool` constructs no
`Cell`. Negative-tested: relaxed to `case _: Tree.New => true`, the fixture's `Sized#mine` gains a
seam it must not have.

**Face B: the trigger is the POLICY, and a dead entry now reports.** `ContextNeed.deferrals` takes
its candidates from the `sites` entries the binder RESOLVED (`boundSites`, the phase's own record),
with the read-derived set kept beside them — that set is a subset by construction, but it is the
only thing that reaches `<clinit>`, which `PolicyBinder` refuses as a `SyntheticTarget` and policy
may still legitimately name. `planDeferral` then handles a static FIELD carrying its own initialiser
as well as a class initialiser's assignments (`Deferral.clinit` is `SymId.None` for the first — there
is nothing to strip, the `ValDef` is replaced whole), and `readsHolder(rhs)` widens to
`needsContext(rhs)`: reads a mapped static, **or constructs a type this program declares**.

That second disjunct is an over-approximation and cannot be anything else *here*: the deferral plan
is read BEFORE the growth because it creates seeds, so `threadedClasses` does not exist yet, and a
second growth to refine it would draw every boundary once and then un-draw it. What makes it safe is
that the port had to NAME the site — `lazy-init` is per-site opt-in with a decision row, a porter
note and a counted seam.

**One more thing had to move or the exit reports against itself**: `climb` now resolves a DEFERRED
field to `Site.Method`, because after the rewrite it IS a `def` over a cache carrying the clause. Its
own scan therefore asks `preSiteOf`, a deferral-unaware and UNCACHED climb — cached, the pre-deferral
answer would have been handed to the growth as well.

**A BOUND `sites` ENTRY THAT SELECTS ZERO SITES NOW REPORTS** — the third face of "never fired", the
same shape K13's dead-scope-entry report takes. `bindMembers` asks whether the MEMBER exists, which a
real field answers whether or not the phase ever reaches it. A `lazy-init` entry counts as fired iff
it produced a `Deferral`; the other two count when `policyFor` resolved a boundary through them, and
their finding says the thing that is easy to get wrong — an UNSUPPLIABLE USE has no read to re-spell,
so `residual-global` on one is dead and `lazy-init` is its exit. Only entries whose BINDING succeeded
are reported, or one mistake with one fix is reported twice.

**THE SHARED INDEX STILL UNDER-LABELS, and that is deliberate — do not diagnose it fresh.**
`Xref.walkType`'s `AppliedType` arm still REPLACES the kind it was called with, so a symbol reached
through an application is `Tycon`/`TypeArg` whatever position the caller was describing. CT6 is the
worked example. It stays because `UsageKind` is read by the portability check, the rewrite trace and
the external-surface walk, and re-labelling it is its own thirteen-port measure cycle; a consumer
that needs the position asks the NODE (`Usage.site`), which is a structural fact. The arm carries
this note in code.

**What it measured. The P5 enablement, applied in a worktree and reverted: 3 → 1 error.** The one is
the port's own injected `sge/utils/Pools.scala`, `Unmapped` and explicitly *not an engine gap*. The
two CT6 errors are gone and both are now COUNTED:

| | delivery replay (CT6 open) | with CT6 closed |
|---|---:|---:|
| scalac errors | **3** | **1** — the injected `Pools.scala` shim |
| `context-seam` | 17 | **19** |
|  — `captured-context` | 13 | **14** — `+1`, and it is `Table$114#newObject`, the anonymous subclass of `Pool<Cell>` that had no lexical home |
|  — `residual-global-read` | 4 | **3** — `TextField#DEFAULT_ONSCREEN_KEYBOARD` left this lane for `deferred-init` |
|  — `deferred-init` | **0** | **2** — both `sites` keys FIRE: `TextField#DEFAULT_ONSCREEN_KEYBOARD`, `Table#cellPool` |
|  — `frozen-component` / `lost-clause` | 0 / 0 | **0 / 0** |
| threaded declarations | 275 (188 classes + 87 methods) | **275** — 188 + 87 |
| distinct java files threaded | 177 | **177** |
| `policy` | 2 (the `bean-properties` floor) | **2**, the same two — no dead-binding row, because both entries fired |
| `omissions` | 65 | **65** |
| every other check | baseline | **identical** |
| blast (`just members-unchanged`) | 1,799 | **1,807** — `+8`, the two cache pairs and their `def`s |

`Table#cellPool` is the entry's own fixture reproduced at scale: `static final Pool<Cell> cellPool =
new Pool<Cell>(){ … new Cell() … }` was **1 error and 0 seams**; it is now 1 counted `deferred-init`
and a `def cellPool(using sge.Sge)` over a cache, with the porter note beside it. Read the note's
`from=` — it says *the field's own initialiser*, because a note that named a `<clinit>` that never
existed would say something false about the code its reader is holding.

*Fix kind: (a) engine, both faces — `ContextNeed` (`instantiates`, `anonHome`, the deferral's
trigger, and `climb`'s view of a deferred field), plus the dead-binding report in
`GlobalsToImplicitsTransform`. None of it is reachable from any manifest key; the mechanism stays
DEFAULT-OFF, so all 13 ports are 0 members changed with every check count identical.*

## 13. Retyping a PRIMITIVE to an opaque domain type

All three entries below come from the SAME delivery — Stage P6's attempt to enable an opaque family
on libGDX core (`PROGRESS.md` §11.25) — and all three are in `PrimitiveToOpaqueTransform` itself,
not in the policy that configured it. Read O1 and O2 together: they are two halves of "a retyping
phase owes more than the declaration it was pointed at", and neither was reachable from any manifest
key, so a port that hit either had no exit but the engine. O3 is a third shape — a family the spec
cannot ask for at all, and it is the one still open.

The delivery O1 and O2 blocked was otherwise complete and correct. The family emits exactly what the
reference hand port emits (`GLTexture.glHandle: TextureHandle.T`, the mint wrapped at
`TextureHandle(gl.glGenTexture())`, every GL-interface crossing unwrapped), every one of the 21
check counts is unchanged, and the whole cost was **6 scalac errors, 3 from each of O1 and O2**.

**O1 and O2 are CLOSED**, and the number that says so is the same one that opened them: the P6
`OpaqueSpec` re-applied verbatim reads **6 → 0 errors**, with the reached set intact — 1 seed, 2
`RetypedSignature` decisions, and every one of the 21 check counts identical to the port's baseline.
The two deltas are both O1's and both accounted: coercions **27 → 30** (one per O1 error site) and
members **34 → 37** (`TextureDescriptor`, `#hashCode`, `#compareTo` — the three rows that pre-fix did
not move BECAUSE no coercion was inserted in them). Both runs are in the same session against the
same baseline, so the attribution is a diff and not a reconstruction.

### O1. A coercion reads the boundary TERM's own type, so a seed reaching it through an `if` is INVISIBLE — was 3 errors

CLOSED, and it is the general rule §1.5 already states for `CollectionsTransform` — *read the
boundary through the DECLARATION* — met by a second retyping phase. (a) engine, in the phase's
coercion. Measured on libGDX core: `TextureDescriptor#hashCode` (1) and `#compareTo` (2).

The phase retypes seed REFERENCES so boundary detection reads a consistent `tpe` —
`transformIdent`, `transformSelect` and `transformApply` each rewrite the node's type to the opaque
one. Every coercion site then tests that type:

```scala
private def isOpaque(t: Term): Boolean = headSym(t.tpe).contains(opaqueSym)
private def unwrapIfOpaque(e: Term): Term = if isOpaque(e) then unwrapCall(e) else e
```

which is exact for a BARE reference and blind to every term that CARRIES one. A conditional is the
shape the corpus actually has:

```java
result = 811 * result + (texture == null ? 0 : texture.getTextureObjectHandle());   // E134
int h1 =                (texture == null ? 0 : texture.getTextureObjectHandle());   // E007
```

`getTextureObjectHandle` is a seed method, so the `Apply` node is correctly typed
`TextureHandle.T` — but the enclosing `Tree.If` is not, because nothing retypes a composite node
from its branches. So `unwrapIfOpaque` sees an `If` whose `tpe` is still `Int`, inserts nothing, and
the `+` has no overload for `Long + TextureHandle.T`. The `val h1: scala.Int = <If>` face is the
same defect in the other direction: `h1` is correctly NOT a seed (an `If` is not a pure move, so
`FlowPropagation` builds no edge to it and the declaration rightly keeps `Int`) — and that is
precisely a boundary, which is exactly where a coercion was owed and none was inserted.

The failure direction is the SAFE one `FlowPropagation`'s own doc argues for — a missed edge is a
compile error at the site, never a silent retype — so this was a gap to close, not a design to
revisit.

**The fix reads the boundary through the DECLARATION and pushes the coercion INTO EACH BRANCH.**
`carriesOpaque` asks the SEED TABLE about the declaration a value flows from, and descends the
compound expressions that carry a value without being one — `if`, a block's tail, a `match` arm, a
`Commented` wrapper; `coerce` then rewrites the leaves. Both halves were choices and both are
recorded:

- **which of the two candidates.** The entry left "push into each branch" and "type the carrier from
  its branches and coerce the whole" as unmeasured alternatives. The first is right, and the
  REFERENCE PORT settles it rather than taste (§3.5): sge writes
  `texture.map(_.textureObjectHandle.toInt).getOrElse(0)` — the coercion at the leaf, the
  declaration that kept the primitive reading as java wrote it. The second is also WRONG for a MIXED
  carrier, which the first draft did not see: an `if` with one branch of each type has no type a
  single coercion could take, since an opaque type's bound outside its own object is `Any`. A
  UNIFORM plain carrier is still coerced whole, so the pre-fix answer survives where it was right
  and no emitted byte moves for it.
- **which node kinds are carriers is ENUMERATED, and an unenumerated one is a MISSED coercion** —
  the same failure direction, deliberately: a missing coercion is a compile error at the site, while
  a spurious one silently unwraps a value nothing asked about. A `Try`, a `Lambda` and an
  anonymous-class body carry no coercion today and each is a missed edge rather than a wrong one.

Emitted, at the site that opened this:

```scala
result = (811 * result) + (if (this.texture == null) 0 else sge.graphics.TextureHandle.unwrap(this.texture.getTextureObjectHandle()))
val h1: scala.Int = if (this.texture == null) 0 else sge.graphics.TextureHandle.unwrap(this.texture.getTextureObjectHandle())
```

*Fix kind: (a) engine — `PrimitiveToOpaqueTransform`'s coercion. No `RuleScope` could have reached
it: the errors are at CALLERS of a retyped member, and scoping the caller out cannot un-retype the
callee.*

### O2. A retyped PARAMETER leaves its METHOD's signature stale — and the ctor funnel reads the signature — was 3 errors

CLOSED. (a) engine, in the phase's retype loop. Measured on libGDX core: `Texture`'s synthesised
primary (1) plus the two overload resolutions that then fail (2).

The retype loop rewrites two things and no third:

```scala
case r if isPrim(r) => s.copy(info = opaqueRef)                       // a VALUE symbol
case TypeRepr.MethodType(ps, ret, im) if isPrim(ret) =>
  s.copy(info = TypeRepr.MethodType(ps, opaqueRef, im))               // a method's RETURN
```

`ps` is never touched. So when a seed is a PARAMETER, the parameter's own symbol carries the opaque
type while its enclosing method's `MethodType` still lists the primitive — one declaration with two
types, and which one a consumer sees depends on whether it reads the `ValDef` or the signature.

The emitter reads the `ValDef`, so the declaration renders correctly:

```scala
abstract class GLTexture(glTarget$p: scala.Int, glHandle$p: sge.graphics.TextureHandle.T)
```

The constructor funnel reads the SIGNATURE — and its comment says why that is the right thing to
do, which is what makes this the phase's defect and not the funnel's:

```scala
// parameter TYPES from the parent constructor's own signature, never from one call's
// arguments: an argument is an expression whose type may be narrower than the formal.
val formals = program.symbolOf(targets.head).map(_.info).collect {
  case TypeRepr.MethodType(ps, _, _) => ps.map(_._2) … }
```

so a subclass whose primary is SYNTHESISED (`DESIGN.md` §8.2) types its `sup$k` slots from the
stale list and emits a parent call that cannot type-check:

```scala
class Texture protected (sup$0: scala.Int, sup$1: scala.Int) extends sge.graphics.GLTexture(sup$0, sup$1)
//                                         ^^^^^^^^^^^^^^^^ the parent's formal is TextureHandle.T
```

The two E134s follow from the same slot: every `def this(...) = this(...)` delegation is resolved
against a primary whose second slot has the wrong type.

**The funnel is the consumer that MEASURED it, not the only one.** Anything else deriving from a
method's signature rather than its `ValDef`s reads the same stale list — a descriptor, and the
published surface a dependent compiles against (`DESIGN.md` §8.3).

**The fix rewrites the enclosing `MethodType`'s parameter slots in the same motion as the `ValDef`,
BY POSITION** — the correction `NullabilityTransform` already records for its own retype loop, and
for the same reason: a `MethodType`'s parameter list and its `DefDef`'s are parallel by
construction while the NAMES are not, since an earlier phase may rewrite a parameter slot without
touching the method's `info`. `PolyType` is unwrapped and re-wrapped, so a generic method with a
seeded parameter moves too. Proof, in the run's own artifact rather than in prose —
`decisions.tsv`, the `FunnelledCtor` row for the class that failed:

```
primary=(sup$0: scala.Int, sup$1: <notype>::sge.graphics.TextureHandle.T)
```

**And the three other consumers were checked, not assumed:**

- **`MemberKey`/`Descriptor` are UNAFFECTED, and that is the invariant holding rather than luck.**
  `Symbol.descriptor` is set by the FRONTEND from the Java signature and no phase rewrites it; the
  binder, `OverrideGraph` and the emitter's contract rows read that field first, and
  `Descriptor.ofInfo` — the only derivation that reads `MethodType` — is the documented FALLBACK for
  a symbol the frontend never declared (an external member, or one the engine minted after it ran).
  So a retyped parameter moves the signature and leaves the descriptor Java-derived, which is what
  P4's audit pinned. Measured, not argued: `port-map` **0**, `signature` **0**, and the emitted member
  KEY for the affected constructor is the same string before and after the fix.
- **the CONSTRUCTOR FUNNEL** — the consumer that measured it — now reads `TextureHandle.T` and the
  three errors go.
- **the PUBLISHED SURFACE** (`DESIGN.md` §8.3) is no longer unmeasured: the fixed run reaches a
  compile, `port-map` and `manifest` are both 0 on the base, and the seven libGDX dependents are
  0 members changed with identical counts.

*Fix kind: (a) engine. The general rule it is an instance of: a phase that retypes a DECLARATION
owes every derived signature that mentions it, because the TIR stores a parameter's type twice and
only one of the two is what a given consumer reads.*

### O3. An opaque family that lands on an ARRAY ELEMENT is INEXPRESSIBLE — not refused, unreachable

OPEN. (a) engine, in the phase's eligibility test. Found while harvesting P6's policy; not counted
in the 6 errors above, because the family it blocks was never configurable in the first place.

An `OpaqueSpec` names a primitive and the phase seeds symbols whose OWN info is that primitive:

```scala
private def taggablePrim(info: TypeRepr): Boolean = info match
  case r if isPrim(r)                 => true
  case TypeRepr.MethodType(_, ret, _) => isPrim(ret)
  case _                              => false
```

`int[]` is not `scala.Int`, so a declaration whose element is the domain value is invisible to
seeding — and to propagation as well, because `FlowPropagation`'s edges run between SYMBOLS and an
array's element has none. The measured case is libGDX's `void bind(ShaderProgram, int[] locations,
int[] instanceLocations)`, which the reference hand port types `Array[AttributeLocation]`: a real
retype of a real ported declaration that no `OpaqueSpec` can currently ask for.

The failure is quiet in the way that matters — a hint naming such a declaration does not throw and
does not refuse, it simply matches nothing and is reported as never-fired, which reads identically
to a typo. Any fix has to decide how far the element type travels (an `Array[T]` element, a
collection's type argument, both) and what a coercion at an array boundary even is — a per-element
map is not a wrap — so this is a design question, not an oversight to patch.

**STILL OPEN — but no longer QUIET, which was the half that made it expensive.** The refusal stands
and the report is new: the phase now reports every hint that named a real declaration of this
program, inside the fence, whose VALUE TYPE mentions the spec's primitive without being it
(`reportUnreachable`, a `PolicyIssue.Malformed` row in the `policy` check). Three things about the
shape, each a decision:

- **`Malformed`, not `NeverMatched`.** The key named something, so "your key matches nothing" is the
  wrong sentence, and `PolicyReport`'s three answers already hold the right one — *it could never
  have named anything the phase can act on*. The enum gains no case, which its own doc asks for.
- **the detail says (a) ENGINE, explicitly**, because `PolicyFinding.render` appends §1(b)
  unconditionally and here that is false: no respelling of the key reaches a container's element,
  and the exits are to drop the hint or to widen the mechanism. §4.45's rule — a finding whose
  reader cannot classify it costs a full investigation — is what makes the override worth the words.
- **the report's DOMAIN is exactly the seeding rule's domain** — a method's RESULT, anything else's
  own `info`. A hint naming a METHOD whose PARAMETER is the primitive is a different mistake with a
  real policy exit (name the parameter), and reporting it here would send its author to the engine.

*Fix kind: (a) engine — `taggablePrim` plus whatever `FlowPropagation` would need to carry an edge
into a container's element. No policy exit: the spec has no vocabulary for "the element of".*

### O4. An `OpaqueSpec`'s `hints` is a PREDICATE, so the surface fingerprint cannot see it

OPEN, and it is the residue left by closing a bigger hole rather than a new one. The phase RETYPES
declarations under a `RuleScope` and did not implement `SurfacePolicy`, so `PortManifest.fingerprint`
compared two instances by NAME — and the name is `primitive->opaque:<fqn>`, which means a base and a
dependent seeding the SAME opaque type from different declarations compared EQUAL, in
`ManifestAgreement` and in every published port map. That is closed: the fingerprint now renders the
spec's `fqn`, its primitive, its sorted `extraHints` and its `RuleScope.fingerprint`.

**`hints` is a `Symbol => Boolean` and has no stable rendering, so two specs differing only in their
predicate still compare equal.** This is the same blind spot `PortManifest.fingerprint`'s own doc
names one level up, one level down: opt-in, and the alternative — reflecting over a lambda — would
compare things that are not policy. What is left is strictly smaller than what it replaces, and the
part a port actually EDITS after a failed compile (`extraHints`) is inside the fingerprint.

**Nothing depends on it today, and that is a MEASUREMENT rather than a hope** (§1.5's instance-count
question): no dependent in the corpus constructs a `primitive->opaque` phase, so there is one
instance, inherited through `extendedBy`, and every dependent's effective surface agrees by
construction. The exit when one does, and the reason it is not built yet, are in `DESIGN.md` §8.13.

*Fix kind: (a) engine, if it is worth building — an `OpaqueSpec` field naming the predicate, which
is policy the port would have to keep honest by hand, or a fingerprint over what the phase SEEDED,
which is not available at fold time and would not be pure. Neither is obviously better than the
named residue.*
