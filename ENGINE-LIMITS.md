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
slots is promoted and nothing is synthesised, so those classes stay byte-for-byte as they were),
then the marker. Measured on libGDX core: omissions **177 -> 176**, the one removed being
`DistanceFieldFontCache`'s two discarded arguments — this entry's own worked example — with compile
still 0 and 6 classes gaining a marker (the five tiled map loaders plus `DistanceFieldFontCache`).
`CtorFunnel.syntheticPrimary` asks `shadowedAt(1)` after choosing the marker, so a class that some
real constructor of the DISAMBIGUATED arity would still shadow is refused rather than emitted; that
refusal is the residue this entry now covers.

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

**Title, for renumbering: "java protected is dropped, and accessibility is an input to overload
resolution".** This entry is about MEMBER VISIBILITY, whose only current home is this section.

The emitter renders no visibility modifier for a Java `protected` member: libGDX core's Java has
**867** `protected` declarations and its emitted Scala has **3**, all of them inside injected or
commented text. Every one of those members ships PUBLIC.

That is usually invisible — a widened member breaks no compile and no test. It stops being invisible
where accessibility CHOSE AN OVERLOAD. Java resolves a call against the applicable methods that are
also ACCESSIBLE from the call site, so a `protected` overload simply is not a candidate for an
unrelated caller in another package; emitted public, it becomes one, and a call javac resolved
uniquely becomes ambiguous.

Measured on gdx-gltf: `AnimationController` declares six public `setAnimation(String, …)` overloads
and one `protected AnimationDesc setAnimation(AnimationDesc)`. `AnimationsPlayer.clearAnimations()`
writes `scene.animationController.setAnimation(null)`, which javac resolved to `setAnimation(String)`
because the protected twin is out of reach. Emitted, both are public and arity-1, `null` conforms to
both, and dotty reports `E051 Ambiguous overload` — 1 error, and the only one of gdx-gltf's residue
that a reader would blame on the library rather than on the port.

**Why it is not simply "emit `protected`".** Java's `protected` is package-access PLUS subclass
access; Scala's is subclass access only, and Scala additionally forbids reaching a `protected`
member through a reference that is not `this`. Emitting Scala's `protected` would turn a widening
into a set of NEW access errors across every same-package caller the corpus has. The near-exact
rendering is `protected[<package>]`, which restores package access and lifts the `this` restriction
— untried, and its blast radius is every one of those 867 declarations plus whatever their callers
then do, so it wants its own measured cycle and its own baseline promotion. Do not attempt it as
part of another change.

*Fix kind: (a) engine — OPEN, and priced at 867 declarations of emitted-text movement.*
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

*Fix kind: (a) in effect, best delivered as (b). Unbuilt.*

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

### K12. The engine has NO JDK MEMBER SURFACE, so a component under an UNPARSED PARENT is frozen — **12 of 144**

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

**The seam is already there, so do not re-architect for it.** `ExternalSurface(known)` is a
`Map[FQN, Set[Member]]` a caller supplies; a type PRESENT in it is answered exactly, so an absence
from its member set is proof and the anchor lifts. A demand-derived JDK surface (DESIGN.md §8.9)
fills exactly that map, with no change to `OverrideGraph` and no change to any phase. `Selection`'s
six properties come back the day it lands.

**One entry in the default surface is NOT optional and is easy to lose**: `java.lang.Object`.
`SpoonTir.superTypes` filters it out of every parent list on purpose, so a graph that reads parents
alone reports a rename of `toString`, `equals`, `hashCode` or `clone` as UNANCHORED — and renaming
those breaks every caller in the JVM with a green compile and no count moving. `ExternalSurface`
carries `Object`'s member set as universal knowledge and every closure consults it whatever the tree
shows.

*Fix kind: (a) engine — the surface is a value the engine must derive; until it does, the refusal is
correct and the cost is the number above.*

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

*Fix kind: (b) per-library policy — the engine's part is the number.*

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

*Fix kind: (a) engine.*

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

The shape of a real fix: a class the run does not EMIT must have its plan READ, not recomputed —
from the base's published port map, which is already the channel for "what did the base actually
do" and already carries per-member records. That requires the map to carry each type's primary
parameter list, and `Plans` to seed itself from it for every non-owned class. Neither exists.
`Plans` does not currently know which classes the run owns at all, which is the same missing input
D5 needs.

*Fix kind: (a) engine — and it is the largest one a dependent port has surfaced.*

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
port compiles with a known, named divergence instead of failing. The missing input is the same one
D4 needs — `Plans` has no notion of which classes this run EMITS, only of which are in the
`Program`.

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

*Fix kind: (a) engine — fixed in `TirEmitter.typeNamedElsewhere`; `AllStaticClassAsTypeSpec` pins
both directions INCLUDING the static-access negative.*

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

### D7. An inherited drop leaves a CALL SITE the engine has no seam for — 1 error

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

The shape of the missing (b): a CALL-SITE substitution keyed like `dropMethods`
(`owner#name(P1,P2)`) whose replacement text can name the receiver and the arguments — the
call-level twin of `MethodBodyTransform`'s declaration-level one, and subject to the same rules
(spliced verbatim, written in the port's FINAL namespace, `SurfacePolicy`-fingerprinted because two
modules rewriting a shared call differently is drift). Until it exists, a dependent that calls an
inherited drop from inside a large method has exactly one honest outcome, which is to count it.

*Fix kind: (b) — an engine phase that does not exist yet, not a library rule.*
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
- **Do not redirect a type the port also EMITS.** Moving the members' owner is exact only because the
  contract is that this module does not ship the type; a module that shipped it would emit the
  declaration under one name and every reference under another. Nothing enforces this, and nothing
  can: it is a coherence property of the configuration.

*Fix kind: (a) engine — the mechanism was incomplete, not the policy. Done; pinned by
`TypeRedirectTransformSpec`.*

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

### V1. A comment on a construct the EMISSION consumes has nowhere to go. **222 → 100** on libGDX core

`TriviaCheck` compares the Java text to the emitted text on every run. On libGDX core it first
reported **222** dropped comments; recovering the one large, principled category took it to **100**.

The category that was recoverable: the constructor `CtorFunnel` promotes to Scala's PRIMARY loses
its `def`, so its Javadoc had no node left to sit on. Scala documents a primary constructor on the
CLASS, so the promoted constructor's `leading` is appended to the class's — **Javadoc losses 138 →
17**, no other count moved, output still deterministic.

What remains, by kind (libGDX core `100`; libGDX tests `69`; simple-graphs `1`+`1`; Ashley `0`):

- **81 Line** — the overwhelming majority are comments inside a body that the emitter REWRITES
  rather than renders statement-for-statement: a `switch` arm the lowering merges, a `break`
  replaced by a `boundary`, a `for` header (comments there are stripped on purpose — the clause is
  emitted on one line and a `//` would swallow the rest of it).
- **17 Javadoc** — members the emission consumes for a different reason: a constructor dropped as
  degenerate, an all-static class collapsed to an `object` (its `<init>` is filtered out), an enum's
  constructor folded into the sealed class's parameters.
- **2 Block** — commented-out code inside an expression position, where a comment cannot be rendered
  safely at all (a `//` would comment out the rest of the term).

Do NOT "fix" this by hoisting everything to the nearest surviving node: a comment that describes a
statement, printed above a method, is worse than absent, because it now says something false. The
honest fix per category is a harvest point at the construct that survives, one category at a time,
measured against this number.

*Fix kind: (a) engine — every one of these is an emission path in the emitter, not a library
policy.*

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

### V3. Spoon attaches only ONE of several consecutive FILE-LEADING comment blocks — 2 sites

OPEN, and measured rather than reasoned. `SpoonTir.fileHeader` takes every comment the compilation
unit reports, which is the right shape; the limit is upstream of it. Where a java file opens with
TWO consecutive block comments before the `package` declaration, Spoon's
`CtCompilationUnit.getComments` carries the first and the second is attached to nothing this walk
reaches — so it is dropped, and only `TriviaCheck`'s independent re-lex can see it.

gdx-vfx has exactly 2 such files (`LensFlareEffect`, `LevelsEffect`), both of which open with a
copyrighted Apache notice followed by an anonymous copy of the same notice. **The licence text
itself survives** — it is the FIRST block — so nothing legally material is lost here, which is why
this is recorded and not fixed under time pressure. It would not be true of a file whose second
block carried a different notice.

Zero sites across the other six corpus libraries. The fix is a source-text harvest between the last
claimed comment and the `package` keyword rather than another walk over Spoon's attachment model;
budget it as frontend work, and note that it moves emitted text only for files that have the shape.

*Fix kind: (a) engine — frontend. Unbuilt.*

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
