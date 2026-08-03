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

**…and the same answer covers an INFERENCE VARIABLE, which is not a raw type and reads like one.**
A java DIAMOND (`new ArrayList<>(((Collection<?>) value))`, liqp `LValue.java:154`) has a type
argument javac inferred and no scope names — so the frontend interns a MARKER symbol for it
(`Symbol.UnresolvedTypeVarPrefix`, spelled once in `api` and read by both sides, because a
convention spelled twice is one that drifts). Printed, the marker read
`JavaCollection[? <: ?E]`. `?E` is not a wrong type: it names nothing and does not LEX, so the one
occurrence cost three errors and only the third of them mentioned a type at all.

The emitter therefore never prints one, in two places:

- a `TypeBounds` whose bound IS the marker DROPS that bound. `? <: ?E` was never more informative
  than `?` — the variable the wildcard was bounded by has no binder in this scope either;
- a bare marker renders `?`. In the one position where `?` is not a type either, that is a
  CONTAINED error rather than a lexical one that takes the enclosing statement with it.

**liqp 58 → 57**, all check counts flat, and 0 members moved on any other port — no other corpus
library reaches a diamond whose argument is un-nameable. `TirEmitterSpec`, both shapes,
negative-tested.

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

### G22. A method TYPE PARAMETER constrained only by its BOUND infers `Nothing` in Scala and its BOUND in java — CLOSED

```java
<T extends Map<String, ?>> T getRegistry(String name);
…
assertTrue(context.getRegistry(REGISTRY_FOR).isEmpty());
```

`T` appears in no formal, and the result is consumed by a member selection rather than by a typed
slot — so nothing at the call site constrains it. Java then instantiates it at its BOUND and
`isEmpty()` resolves; Scala instantiates an unconstrained variable at its LOWER bound and the
selection fails with `Found: Nothing / Required: ?{ isEmpty: ? }`. Nothing about the receiver is
wrong and nothing about the retyping is: the two languages disagree about what an unconstrained
variable is.

`SpoonTir.pinTypeArgs` covers the NEIGHBOURING case — a generic call whose ARGUMENTS determine what
java resolved — and declines here, correctly, because no argument mentions `T`. The answer java gave
is therefore a fact about the DECLARATION rather than about the call, which is what makes this a
different rule and not a widening of that one: pin the java-resolved argument explicitly, and where
nothing at the call constrains it, that argument is the bound.

Measured at **1 error** on liqp (`TemplateContext.getRegistry`, called for its emptiness in a
`finally`). `CLAUDE.md` §6's "never cast to `scala.Nothing`" is the same disagreement met at a cast.

**CLOSED** by `SpoonTir.pinUnconstrainedTypeArgs`, on four conditions each of which is a way the pin
would be wrong without it:

- **no FORMAL mentions the variable.** One that does is constrained by its argument, and both
  languages infer it the same way;
- **the call has no TARGET TYPE.** `Map<String,Integer> m = ctx.getRegistry(k)` gives java AND scala
  a target, and pinning the bound there would emit `Map[String, ?]` where `Map[String, Integer]` was
  written. The shape with no target is the call standing as the RECEIVER of another selection —
  which is exactly where scala's `Nothing` is then selected from, so the condition and the symptom
  are the same fact;
- **every variable has a REAL bound.** An unbounded `T` means `T extends Object`, and pinning that
  is G24's territory for no gain: an `Object` receiver has no member worth selecting;
- **the bound mentions no NAMED type variable.** An F-bound, or one naming the enclosing class's
  parameter, is not a type this call site can write down.

**The fourth condition is where this was first written wrong, and the reason is a Spoon inheritance
nobody would guess: `CtWildcardReference` EXTENDS `CtTypeParameterReference`.** So the frontend's own
`mentionsAnyTypeVar` answers TRUE for every `?` — its `case _: CtTypeParameterReference` matches
first and the wildcard arm below it is dead code — and `Map<String, ?>`, which is the bound this pin
exists for, was rejected as F-bounded. The rule declined silently, on the one shape it was built for,
with no error and no count. `mentionsNamedTypeVar` orders the wildcard arm FIRST.

Measured on liqp: **4 -> 3**, one site, and on libGDX core **0 errors, 0 member digests, every check
count flat** — the number a frontend change owes.

*Fix kind: (a). Universal — the two languages disagree about what an unconstrained type variable is,
and java's answer is written down at the call.*

### G23. Java's `?` is bounded by `Object`; scala's is bounded by `Any` — and the gap is one operation wide

CLOSED. `java.util.List<?>` means `List<? extends Object>` — java's unbounded wildcard carries an
implicit `Object` upper bound — so every element read off one IS an `Object` and
`list.addAll(valueList)` type-checks with no cast anywhere in the source. Scala's `?` is bounded by
`Any`, which is strictly wider, so `Buffer[?]` is an `IterableOnce[Any]` and `dst ++= src` on a
`Buffer[Object]` reads `Found: Buffer[?] / Required: IterableOnce[Object]`.

**Do not fix this by changing what `?` renders as.** G2 measured that whole design space and settled
on `[?]` everywhere — parent, override and field alike — which is also what the reference port
emits, and the wildcard round-trips across an override precisely because it says nothing. Widening
it to `? <: Object` to satisfy four call sites would move every raw generic in every port.

So the difference is stated at the ONE operation it blocks: `JavaCollections.addAll(dst, src)`,
which performs java's own read (`asInstanceOf[E]` on an erased parameter is a no-op at run time, so
it throws nothing java's own unchecked `addAll` would not) and returns java's `boolean` besides,
which `++=` does not. The rewrite is keyed structurally on the SOURCE's sole type argument being a
`TypeBounds`, and deliberately nothing wider: a source with a real element type conforms through
`IterableOnce`'s covariance, so every other `addAll` in the corpus stays the idiomatic `++=`.

Measured on liqp: **26 → 22**, four sites (`Push`, `Unshift`), 4 member digests, every check count
flat and no other port's output moved.

*Fix kind: (a). Universal — a bound java writes implicitly and scala writes differently.*

### G24. Java's `<T>` bound is VACUOUS and the emitted `T <: java.lang.Object` is not — 1 error, OPEN. THE FIX WAS BUILT AND MEASURED AT 0 -> 50; DO NOT RETRY BLIND

An unbounded java type parameter means `T extends Object`, which admits every reference type there
is. The port emits that bound literally (`def cartesianProduct[T <: java.lang.Object]`), and in
Scala 3 it is **not** vacuous: `java.io.Serializable` is rooted at `Any`, not at `AnyRef`, because
value classes are serialisable. So `java.io.Serializable </: java.lang.Object`, and

```scala
def cp[A <: java.lang.Object](l: Buffer[Buffer[A]]): Buffer[Buffer[A]] = l
val b: Buffer[Buffer[java.io.Serializable]] = …
val c: Buffer[Buffer[java.io.Serializable]] = cp(b)   // Found: …[Serializable] / Required: …[A]
```

fails to compile with `A is a type variable with constraint <: Object`. **Reproduced standalone on
3.8.4**, so it is a fact about the two type lattices and not about anything the port did.

The obvious fix is to stop emitting a bound java wrote implicitly — an unbounded java type
parameter is an unbounded Scala one. **It has now been BUILT AND MEASURED THREE WAYS, and all three
are worse than the one error it closes. DO NOT RETRY without reading what each one cost.**

The measurement is the entry, because "one edit in `TirEmitter.typeParam`" is exactly what it looks
like and exactly what it is not:

| what was dropped | libGDX core | what broke |
|---|---|---|
| nothing (today) | **0** | — |
| every vacuous bound, class and method | **0 -> 50** | 49 × §4.4's reference-equality translation, 1 × a wildcard capture |
| a METHOD's only, class bounds kept | **0 -> 6** | a method's `T` passed as a CLASS's type argument |

**The 50, and the two families in them.** Forty-nine are `value eq is not a member of T`: §4.4's
`a == b` -> `a eq b` rule needs an `AnyRef`, and an unbounded `T` is not statically one. That half
IS repairable and the repair is small — `SpoonTir.referenceIdentity`'s `asRef` already ascribes a
`java.lang.Object`-typed operand, and a TYPE-VARIABLE operand is the third case it owes. **Whoever
retries G24 owes this repair with it; the two rules are coupled and the coupling is invisible until
the bound comes off.** The fiftieth is not repairable at the operation:
`map.get(this.keys.get(nextIndex))` in `OrderedMap$OrderedMapValues` type-checked for the life of
the port because `Array[?]`'s element capture conformed to `Object` — *because `Array[T]` declared
that bound*. Java keeps the two sides linked as ONE capture (`OrderedMap<?, V>`'s `?` is the same
capture as its `keys`') and the emitted form does not, so there is no argument slot at which to
state the difference. That is the G2/G23 wildcard family met from the other side.

**And the method-only split is not a smaller version of the change — it is an INCOHERENT one.** It
reads as principled: a method type parameter is instantiated afresh at every call and never
CAPTURED, so dropping its bound weakens nothing, while a class's bound is re-read at every `C[?]`.
Both halves of that are true and the conclusion still fails, because **a method's type parameter is
routinely a CLASS's type ARGUMENT**: `Array.with[T](array)` calls `new Array[T](…)`, and an
unbounded `T` does not satisfy the `T <: java.lang.Object` the class kept. Six errors on libGDX,
every one of that shape (`Array#of`, `Array#with`, `DelayedRemovalArray#with`,
`SnapshotArray#with`, `AssetManager#load`, `ResourceData$SaveData#saveAsset`). **So G24 is
all-or-nothing across the two kinds of type parameter, and "all" costs the wildcard capture.**

Three further things the retry should know:

- **the port's HAND-WRITTEN half moves with it** (CLAUDE.md §1). `corpus/libgdx-overrides/sge/utils/Json.scala`
  carries `readValue[T <: Object]` precisely because the engine renders `Skin`'s override that way;
  the moment the engine stops, that line is a compile error and the answer is read off the generated
  override, not chosen. One site found, and only the sites the change reaches are findable;
- **`members.tsv` is the honest size**: 174 members moved on libGDX for the both-halves version and
  143 for the method-only one, at 0 changed check counts either way. The blast is real and no count
  reports it;
- **the cheaper place to stand was EVALUATED, and it is not there.** This entry used to note one:
  the liqp error is not really about the bound, it is that the port WROTE DOWN java's inferred type
  argument (`Serializable`, the lub of `98, "97", true, false, null`) at an `Arrays.asList` call, so
  pinning such a lub as `java.lang.Object` would be one site's fix rather than every generic
  signature's. **Reading the java settles it: `Serializable` is written there TWICE.** Beside the
  three `asList` calls whose argument the port inferred, the enclosing declaration is
  `List<List<Serializable>> cases = cartesianProduct(…)` — java's own written type. Pin the lub to
  `Object` and the call yields `Buffer[Buffer[Object]]`, which then does not conform to the
  `Buffer[Buffer[Serializable]]` the DECLARATION asks for, because `Buffer` is invariant. The
  mismatch moves one line down and the error count does not move at all. So there is no site-local
  fix for this error: it is the bound, and the bound is priced above. **Do not re-derive this.**

Measured at **1 error** on liqp (`ComparingExpressionNodeTest`, whose fixture is a
`List<List<Serializable>>`), and it is the first corpus library to write `Serializable` as a type
argument at all — which is why five libraries and fourteen ports went past it. **One error closed,
six to fifty opened.**

*Fix kind: (a) engine, MEASURED AND REVERTED. The permissive direction is the one this project
accepts elsewhere (`asList`'s fixed-size divergence); what defeats it here is not permissiveness but
that two other universal rules — reference identity and wildcard capture — are reading the bound.*

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

**THE JDK-THROWABLE HALF §4.4 PRESCRIBES IS NOW BUILT**, and the shape of what was missing is the
part worth keeping. The padding was already there — `Slot.NullAt` fills what a narrower overload
left `null`, `Slot.CauseMessage` handles the one JDK overload that fills its own message. What it
had no way to reach was a PRIMARY to delegate to, because it read one from `plan.primaryParams` and
that is the promoted root's parameter list.

liqp supplied both sides of the line in one package:

- `VariableNotExistException extends RuntimeException` has ONE root, so it is PROMOTED and the fill
  runs — `extends RuntimeException(String.format(…))`, exact;
- `LiquidException extends RuntimeException` has THREE, with three different `super(...)` calls
  (`super(createMessage(e), e)`, `super(message)`, `super(message, cause)`) and no nilary root. Not
  one of them passes its own parameters straight through, so `plan0`'s widest-pass-through branch
  nominated NOTHING, `primaryParams` was EMPTY, the fill never ran, and all three roots delegated
  `this()`. The port compiled, moved no count, and **every exception it threw had a null message and
  no cause** — §4.4's own row, shipping.

The shape that closes it is a SYNTHESISED primary at the family's widest overload —
`class LiquidException protected (sup$0: String, sup$1: Throwable) extends RuntimeException(sup$0,
sup$1)` — which is the one place §4.4's "promote the widest super call" cannot mean "promote a root",
because no root IS the widest. `CtorFunnel.throwablePadding` expresses each root at those slots and
`syntheticPrimary` takes it from there unchanged; it is shape (7) in that file's header.

**Three things about how it had to be written, and each of them is the transferable part:**

- **The overload is read off the TARGET CONSTRUCTOR's formals, never off the arguments.** The
  existing fill matches an argument to a slot by HEAD NAME, and `super(createMessage(e), e)` passes
  a `RecognitionException` — a subtype of `Throwable`, whose head name is not `java.lang.Throwable`.
  Matched by name that argument finds no slot, the fill declines, and BOTH arguments are dropped.
  Java already resolved the overload; the target symbol is where that answer is written down, and it
  is readable exactly because K15's frontend work made an external member carry its `MethodType`.
- **K5.5's fence is NARROWED, not removed.** Its table says of the throwable branch *"leave it
  alone"*, and that stands wherever it NOMINATED something — consulting the synthesis first there
  cost libGDX omissions 46 → 50. The new arm is reached only on `chosen == None`, which is the case
  K5.5 never had to answer for.
- **A root reaching an overload NOBODY names is refused, not minted.** The JDK really declares
  `(String, Throwable)`, but if no root calls it this run holds no symbol for it, and a class whose
  roots reach only `(String)` and `(Throwable)` therefore keeps its counted omission. Same for a
  `super(cause)` whose cause cannot be read twice: the delegation would name it in both slots, so
  the WHOLE synthesis is refused rather than that one root — a synthesised primary is paramful, and
  a root without a delegation of its own would emit `this()` against it and not compile.

**Measured: liqp `omissions` 4 → 1, scalac errors 31 → 31** (this is a behavioural fix; §3 is the
whole point of it moving no error count), **8 member digests, all of them `LiquidException` and its
three constructors**, and every other port in `just measure-all` byte-for-byte unchanged — which
confirms the blast-radius prediction this entry carried: of the classes across all ports whose
dropped `super(args)` `OmissionCheck` counts, not one else is a `Throwable`. `PlainBigDecimal`,
which this entry once carried as the other half, was already CLOSED by K5.5 when the external
constructor's signature became readable.

**The residue this shape carries, stated so nobody re-derives it:** a root whose super call is
`()` pads to `(null, null)`, and java leaves the cause UNSET where that sets it to null — so a later
`initCause` throws in the port and works in java. It is the same padding the promoted throwable path
has done since it was built, and the note above about `super(msg)` vs `super(msg, null)` is the same
fact.

*Fix kind: (a). The refusal stays correct for every non-throwable parent and IS correctly counted:
`omissions` reports each site, `decisions.tsv` classifies it
`Universal("ctor-funnel/super-args-dropped(C3)")`, and a porter note sits on every affected
constructor in the emitted file — verified on liqp.*

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

### C11. A NILARY constructor in front of a NILARY primary cannot be emitted, and all three ways of keeping its delegation are WORSE — 1 site, `omissions 65 -> 66`

**Title, for renumbering: "a nilary constructor whose delegation carries arguments cannot be
emitted".** Counted, not closed. (a) engine.

`TirEmitter.orderBody` drops every nilary java constructor in front of a class whose primary is
scala's own implicit nilary one. For `C() { super(); }` that is exact — the implicit primary IS that
constructor. For `C() { this(seed(), "d"); }` it is not, and the drop was SILENT: the port compiles,
no other count moves, and the only witness is a `new C()` somebody runs.

Measured over the whole corpus by instrumenting the predicate: **exactly one site**, libGDX
`BitmapFont()`, which delegates
`this(classpath("lsans-15.fnt"), classpath("lsans-15.png"), false, true)`. `new BitmapFont()` in the
port therefore built a font with no data, no page and no glyph where java loaded the default 15pt
face. Nothing saw it — and the published contract made it worse, listing `()` among `BitmapFont`'s
`secondaries=` for a `def this()` the module does not emit.

**The three ways to keep the delegation were each priced, and each emits a WRONG answer in place of
a missing one:**

| attempt | what it costs |
|---|---|
| emit it as `def this()` | `E120 Conflicting definitions` against the implicit nilary primary, plus `E051` at every argument-free `extends` — CT4/CT5's measured shape, and C7's `0 -> 41` is the same wall from the other side |
| PROMOTE it (`Plans.nilaryPlan`) so its body becomes the class body | the body then runs on EVERY construction path (C6/C7). 9 of `BitmapFont`'s 10 paths never ran it, so `new BitmapFont(fontFile)` would also load the default face. `effects` refuses it first anyway — `BitmapFont(BitmapFontData,TextureRegion,boolean)` reads its `region` parameter twice and the argument is not re-evaluable |
| give the class a MARKER-disambiguated synthesised primary (C8) so the `def this()` is declarable | a subclass's bare `extends C` then resolves to that very `def this()` — the fact `reachableArgumentFree` is built on — so `new DistanceFieldFont(…)` would load the default face where today it loads nothing |

So the outcome is the counted one, exactly as for C7: `CtorFunnel.delegationOnlyNilary` is the ONE
predicate the emission drops with and `OmissionCheck.droppedNilaryCtors` counts from, and
`TirEmitter.secondariesOf` subtracts the same set so the published contract stops claiming a
constructor that was never written. A port that needs the behaviour writes the constructor by hand
(§1.5's `inject`); the engine's job is to say so.

**…and the DEPENDENT could not see it at all.** The drop reached the published contract only as an
absence from `TypeShape.secondaries`, and an absence is not a disposition: `primary=()
primaryKind=not-funnelled` with no `()` among the secondaries is character-for-character what a
benign class with one constructor publishes. So a dependent's `new BitmapFont()` compiled straight
into the empty-font wrong answer with nothing counting it — C11's own failure, one module out. It is
now a `Dropped` MEMBER row carrying `refusal=ctor-funnel/nilary-dropped(C11)` in both namespaces
(`DESIGN.md` §8.3), which lands in the lane `PortMapTransform` already has for a dropped member's
call sites, so a dependent's `new C()` is a counted finding whose message says the fix is §1(a) IN
THE BASE and not a manifest key anywhere. Measured: libgdx-core's map 19606 -> 19607 rows, one row,
and 0 -> 0 on every check of all 13 ports — no dependent in the corpus calls it, which is exactly why
nothing had ever noticed.

**…and saying so means SAYING IT WHERE THE QUESTION IS ASKED** (§4.575). A count in `findings.tsv`
answers an agent holding the run directory; the agent this engine has is reading `BitmapFont.scala`
in another repository, and its question — *why is there no `def this()`* — has no grep. So
`PortRun.recordDroppedNilaryCtors` records the drop as a `Decision.Kind.DroppedMember` with
`Reason.Universal("ctor-funnel/nilary-dropped(C11)")`, from the same `Plans.droppedNilaryCtor`
through the run's own `Surface`, and the note is `PorterNote.InBody` — a dropped member has no
declaration to sit above, so it heads the owning type's body, which is where somebody looking for
the constructor looks. Measured: libGDX core `decisions.tsv` 3893 -> 3894 rows (`DroppedMember`
25 -> 26), ONE member digest moved (`sge.graphics.g2d.BitmapFont`'s class row, the note being emitted
text), `porter-notes` 0 -> 0, and every other count on all 13 ports flat.

**Do NOT retry any of the three rows above.** Each is a behaviour change, not a compile fix, and two
of them are silent.

**…and a predicate whose answer is DELETE THIS DECLARATION may not have a fallback arm.** The one
above ended in `case _ => Some(Nil)` under a match on `Tree.Block`, so two bodies that are not "no
body" answered DROP IT: a body wrapped in `Tree.Commented` — which is what ONE COMMENT above the
constructor's first statement produces — and a single-statement body with no braces. Either removed
the constructor AND the record of its removal in one step, since the emission and the count read this
same function. The body is now read through the wrapper (`CtorFunnel.stmtsOf`, which every shape
match in that file goes through, so the transparency is given once) and an unrecognised shape answers
`None`, which EMITS the constructor and takes the `E120` — a compile error an agent can act on rather
than a behaviour change (CLAUDE.md §3). **0 corpus sites**: 13 ports, 0 -> 0 members changed, every
check count flat. It is pinned by `CtorFunnelBodyShapeSpec`, which builds both shapes by rewriting a
parsed constructor's `rhs`, because no Java text can produce them at today's frontend — and that is
the point, since the next lowering or frontend can.

### C12. A PROMOTED CONSTRUCTOR LOCAL keeps its NAME and loses its POSITION — **liqp 161/414 -> 357/218 passing, 0 compile errors either side. CLOSED**

C2 says a promoted constructor's parameters and top-level locals become MEMBERS, and `CLAUDE.md`
§4.55 says how to rename them so nothing collides. Both are about the NAME. Neither is about WHERE
the member's initialiser runs, and that is the half that is wrong.

A java constructor body is a sequence. Promoting it to Scala's primary emits the locals as `val`
members and the rest as statements — but the `val`s are emitted at the HEAD of the class body,
ahead of every statement, whatever order java wrote them in:

```java
Template(TemplateParser templateParser, CharStream stream, Path location) {
    this.templateParser = templateParser;                                    // 1st in java
    Set<String> blockNames = this.templateParser.insertions.getBlockNames();  // 2nd
    …
}
```
```scala
private var templateParser: ssg.liquid.TemplateParser = scala.compiletime.uninitialized
val blockNames$p = this.templateParser.insertions.getBlockNames()   // ← runs FIRST; the field is null
…
this.templateParser = templateParser$p                              // ← runs after
```

A Scala class body IS its constructor, so a `val` initialiser and a statement are the same kind of
thing and their relative order is the whole of the semantics. Emitting every promoted local before
every statement re-orders java's constructor, and the failure it produces is a `NullPointerException`
on a field that java had already assigned.

**Measured on liqp's first suite run: 409 of 414 failures, all of them this one constructor.**
`Template` is what every test parses through, so one member's ordering took 71% of the suite. And
the number nothing else could have found: **0 scalac errors, every check count flat, and the port
had been reported green on the compile for the whole wave that produced it** — CLAUDE.md §3 in one
member, at the scale it warns about.

**The line was `TirEmitter.orderBody`**, and it was three lines long:

```scala
val fields = body.collect { case v: Tree.ValDef => v }
val rest   = body.filterNot(s => isCtor(s) || s.isInstanceOf[Tree.ValDef])
fields ++ ordered.toList ++ rest          // every ValDef hoisted ahead of every statement
```

For a class's own FIELDS that partition is right — JLS 12.5 step 4 runs field initialisers, in
textual order, before any constructor body statement, so hoisting them reproduces java WHATEVER
order the java file declared them in, and a field declared BELOW the constructor needs the hoist to
compile at all. For a PROMOTED CONSTRUCTOR LOCAL it is exactly wrong: that `ValDef` is a step-5
constructor BODY statement (spliced in as `plan.primaryBody`), and hoisting it past the
constructor's other statements re-orders java.

**THE FIX: the hoist applies to `owner`'s FIELDS, and ownership is what tells the two apart.** The
two kinds are indistinguishable by NODE KIND, and every other discriminator is a `CLAUDE.md` §4.56
violation waiting to happen — a name, an origin line (a real field and a promoted local can share
one only by accident), a `plan.primaryBody` membership test at one caller. The frontend already
records the fact structurally: `SpoonTir.defineLocal` interns a local with `owner = methodId` while a
field is interned under the CLASS, so `orderBody` takes the class symbol and asks *is this `ValDef` a
member of it?* That also generalises past the funnel with no caller opting in — any route that
splices a constructor's own declarations into a class body produces symbols owned by that
constructor, the enum-parameter route T11 names included.

**The SHAPE chosen, and the one deliberately not needed.** A promoted local simply stays in `rest`,
in place; there is no `var x = scala.compiletime.uninitialized` at the top with an assignment where
the declaration stood. Scala permits a `val` anywhere in a class body, so nothing about the position
needs repairing — and the split's only motivation, a forward reference from an earlier statement to a
later local, cannot arise: java's definite-assignment rules already rejected that program. (Had it
been needed, `uninitialized` reading as null/0 before the assignment IS java's semantics for the
pre-assignment window, so the split would have been faithful too — it is simply a second construct
for nothing.)

Three things the fix keeps, which is why it is not a sort:

- **java's order, exactly** — the promoted locals interleave with the promoted statements and the
  interleaving is what carries the dependencies;
- **real fields keep the hoist**, and `CtorFunnelPromotedLocalOrderSpec` pins that negative with a
  field declared BELOW the constructor that reads it;
- **the RENAMES C2/§4.55 already do are correct and independent** — only the placement moved, which
  is why the spec asserts the `$p` suffixes beside the order.

**MEASURED, and the shape of the evidence is the point.** No error count moved anywhere: liqp
0 -> 0, and every one of the eleven lanes flat with every check count identical. What moved was
emitted TEXT and test OUTCOMES:

| | |
|---|---|
| liqp suite | **161 passing / 414 failing -> 357 / 218** — 196 newly passing, **0 newly failing**, 0 skipped |
| liqp member digests | **1** — `Template`, the one member the whole census pointed at |
| libgdx-core member digests | **29** classes, every one a paramful promoted constructor with a top-level local (`IntMap`, `ObjectMap`, `SpriteBatch`, `ShaderProgram`, `DefaultShader`, `Timer`…) |
| every suite's outcomes | **unchanged** — gdx-test 217/4, ashley 108/2+2, anim8 23, vfx 64, sg 16, screens 16 |

That last row is the one that had to be checked and could have gone either way: the hoist was
load-bearing nowhere, but nothing short of running every suite could say so. And note libGDX was
carrying the same defect silently — `IntMap(initialCapacity, loadFactor)` computed
`tableSize(initialCapacity, loadFactor)` BEFORE the `loadFactor <= 0f` validation that java runs
first — with 0 errors and a green suite, because no test passed an invalid load factor.

*Fix kind: (a) engine. CLOSED in `TirEmitter.orderBody`, pinned by
`CtorFunnelPromotedLocalOrderSpec`. Found by RUNNING a suite and by nothing else.*

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

It was **zero sites** across libGDX core (604), libGDX test (29), Ashley (39), simple-graphs (36) and
jbump (19), which is why it was recorded rather than built. It surfaced only when a spec for the
captured-local rename (`CLAUDE.md` §4.55's fourth face) reached for a local class as the second shape
that shadows a capture; the pass itself is indifferent between the two, because it reads
`Tree.ClassDef` and `Tree.AnonClass` through one traversal, so it will cover local classes the day
the frontend produces them.

**MEASURED, on the sixth library: liqp's own test suite is the first corpus hit, and it costs 62 of
639 tests plus 12 compile errors.** Five sites in four files — `liqp/ReadmeSamplesTest.java:65,76`,
`liqp/TemplateTest.java:145`, `liqp/filters/DateTest.java:166`,
`liqp/parser/LiquidSupportTest.java:198` — every one of them a `class X implements Inspectable {…}`
declared inside a `@Test` body, which is idiomatic for a suite that documents an API by using it.
The refusal aborts the WHOLE run, so `corpus/ports/liqp/test.conf` states the four files in
`excludeGlobs`, 577 of the 639 `@Test` are emitted, and `just liqp-measure` prints
`!! TESTS LOST — 62 of 639` on every run until the frontend grows the node. Read the second number
too: the four excluded files are not isolated — four OTHER suites `import liqp.TemplateTest` for its
nested `ComparableBase`, so the exclusion also produces **12 `value TemplateTest is not a member of
ssg.liquid` errors**, a fifth of that source set's wall, which no fix to those four suites can
remove. The exclusion is a stated, counted retreat and not a policy: when the construct lands, the
four lines are DELETED rather than narrowed.

That is the shape this entry predicted — Java code that uses the form at all tends to use it a lot,
so a library that hits this hits it as a wall rather than as a residue. Budget it as frontend work,
not as a translation rule: the TIR already has the node.

*Fix kind: (a), unbuilt — frontend only. Cost, measured: 62 tests unportable + 12 cascade errors on
one library.*

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

**THE OTHER HALF — a DECLARED collidee — needs no such pass, and is now closed.** The sentence above
is about a SYNTHESISED collidee and does not carry over: liqp's `Flavor` takes a constructor
parameter `isLiquidStyleInclude` and DECLARES `isLiquidStyleInclude()`, which is an ordinary member
`funnelParamRenames` already reads. What blocked it was not visibility but the ROUTE — `enumDef`
promotes the parameters itself, deliberately without consulting `CtorFunnel`, so `plans(cd)
.primaryParams` is empty for an enum and the §4.55 pass saw nothing to place. So the pass grows an
enum arm reading the same parameters `enumDef` does.

**NARROW, unlike the plan-based arm beside it, and the difference is what the parameter IS.** A
promoted funnel parameter is positional and invisible, so that arm renames every one; an enum
parameter is EMITTED SURFACE — a public `var` — so a rename that is not forced would move the API of
every enum in the corpus. Two names are therefore not collidees: the parameter's own, and a body
FIELD it SUPERSEDES (`enumDef` drops a same-named `ValDef` because the `var` parameter IS that field,
so renaming would un-supersede it, emit both, and break the self-assignment drop — libGDX's
`TextureFilter(glEnum)`). The taken set is built from the PARTS rather than by subtracting from the
visible names, because one name can be both (`isStyled` the parameter, `styled` the field,
`isStyled()` the method) and subtracting the parameter's own name would take the collidee with it.

**liqp 56 → 54**, 0 members moved on any other port. Note the residue it inherits rather than
introduces: a promoted PARAMETER's rename decision is recorded in `decisions.tsv` and has no porter
note in the emitted file, because a note is `AtDeclaration` and a constructor parameter has no
declaration line — true of the plan-based arm's parameters (`templateParser$p`) since they shipped,
and invisible to `porter-notes`, which does not resolve these subjects.

*Fix kind: (a) engine. Built; `EnumCtorBodySpec`, both directions.*

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

### T14. A java STATIC is INHERITED by every subclass; a Scala companion inherits nothing — emit the DECLARING type — **CLOSED, 20 errors**

`ZoneOffset.systemDefault()` compiles in Java. `systemDefault()` is declared `static` on
`java.time.ZoneId`, `ZoneOffset extends ZoneId`, and java lets a static be named through ANY
subclass — so a library that writes the subclass name is writing valid, ordinary java. Scala's
companion objects inherit nothing from each other, so the same text emitted verbatim is
`value systemDefault is not a member of object java.time.ZoneOffset`, every time.

The receiver in the source is a NAME, and the name is the wrong thing to carry across. What the
frontend has and the emission drops is the resolved executable's **declaring type**, which is the
only receiver that means the same thing in both languages — the same fact `CLAUDE.md` §1(a) already
states for the other half of this (*"Java interface constants are `static` and inherited; Scala
companions do not inherit"*), arriving at a METHOD instead of a constant.

**Measured on liqp's test suite: 20 errors, one call spelled four ways across
`nodes/{Gt,GtEq,Lt,LtEq}NodeTest`** — a third of that source set's whole wall (59), from a single
upstream idiom, and every site is `java.time.ZoneOffset.systemDefault()` where the JDK declares
`java.time.ZoneId.systemDefault()`. It is a JDK-boundary case here, but nothing about it is JDK-only:
any ported hierarchy whose subclass name is used to reach a base's static has the same shape, and
the same fix covers both.

Note what it is NOT: not a collections retype, not a shim, not policy. No manifest key can express
"call this static on its declaring class", and no library should have to.

**CLOSED, in the FRONTEND, and the placement is the whole of it.** The declaring type is a fact about
how JAVA resolved the call, so the layer that owns it is the one that read java. The frontend already
had the answer and was throwing it away: `methodSym` derives an external member's owner from
`getExecutableDeclaration`'s own declaration, so the interned SYMBOL's owner IS `java.time.ZoneId`
while `typeTerm` re-derived the receiver from `CtTypeAccess.getAccessedType` — the NAME the source
wrote. `SpoonTir.staticCallQualifier` reads the owner instead (§4.56: never a test on the name), and
where the parse resolved no declaration the owner is the written type and the rewrite is a no-op by
arithmetic, which is the right degrade. A transform could not have done it as well: it would have to
re-derive "this is a static call" from the tree, which is reading the frontend's answer back, and it
would have to run before the package rename with nothing able to say so.

**And the FIELD half was half-built and looked finished.** `staticFieldAccess` had done exactly this
for a field since libGDX, by walking the accessed type's SUPERCLASS chain — which reaches no
interface, and a java INTERFACE CONSTANT is `static` and inherited through `implements`. That is
`CLAUDE.md` §1(a)'s own worked example of this rule, and the walk could not see it. It is now the
inheritance CLOSURE, breadth-first with the class edge first (java's own shadowing precedence; two
interfaces offering one name does not compile in java either, so there is no tie to break).

Measured, `just measure-all`, one commit:

| | |
|---|---|
| liqp | **76 -> 56**, all 20 in the test source set (`main 27` unchanged, `test 49 -> 29`); 8 members over the four `nodes/{Gt,GtEq,Lt,LtEq}NodeTest` |
| libgdx core | **0 -> 0 errors, 10 members** — the interface half, entirely `GL30.GL_COLOR_ATTACHMENT0` / `GL_TEXTURE_2D` / `GL_DEPTH_COMPONENT` / `GL_STENCIL_ATTACHMENT` / `GL_LUMINANCE` and friends, every one declared in `GL20` and written through `GL30`. It compiled before only because `TirEmitter.classDef` re-exports a parent's companion; the emitted text now names what java meant |
| every other port | byte-for-byte unchanged, every check count flat on all fifteen |

One thing the diff shows that is worth knowing before it is chased: three `collection-boundary` findings
changed their stable ID without changing their count, because an external member's `fullName` IS its
interning key (`@33538#getHeaderFields()`) and the interface walk mints a different set of external
symbols. Same seams, renumbered.

*Fix kind: (a) engine. Both halves — method and interface constant — are the one rule, read off the
symbol's owner.*

### T15. A RECEIVER IS AN OPERAND — `.m` binds tighter than every control-flow expression — **CLOSED, 2 errors and an unknown number of silent ones**

```java
String data = (nodes.length >= 2 ? nodes[1].render(c) : nodes[0].render(c)).toString();
```

emitted

```scala
val data: String = if (nodes.length >= 2) nodes(1).render(c) else nodes(0).render(c).toString()
```

The `.toString()` is now **inside the else branch**. Scala parses it, so this is not a syntax
error — it is `CLAUDE.md` §4.4's shape reached at the EMITTER rather than at a statement form: where
the two branches have different types it is a type error attributed to the wrong expression, and
where they have the same type it COMPILES and calls the method on one branch only.

`TirEmitter.operand` has known which terms need parenthesising as an operand since it was written —
an operator application (precedence) and any control-flow expression — and `Tree.Typed` and
`Tree.Spread` already went through it. **Four receiver positions did not**: `Select`'s qualifier,
`InstanceOf`'s, `ArrayLength`'s and `ArrayAccess`'s. The rule was half-applied rather than absent,
which is exactly why nothing found it: the emitter *looks* like it parenthesises operands.

Note the second face, which has no conditional in it at all: `(a + b).length()` emitted
`a + b.length()`, a different program wherever both sides are `String`.

Found by porting a TEST SUITE and reading one error, not by compiling a library — the same route as
every §4.4 entry. **2 errors on liqp** (`InsertionTest`, the same anonymous `Block.render` written
twice), and the measurement that matters is the MEMBER diff rather than the error diff, because the
loud face is the rare one.

**The silent face was in the corpus already, in a port measured green.** `anim8`:

```java
(filename == null ? Gdx.files.local("BigPaletteMapping.dat") : filename)
    .writeString(new String(bigPaletteMapping), false, "UTF8");
```

emitted `if (c) Gdx.files.local(…) else filename.writeString(…)`. Both branches are a `FileHandle`,
so the ported `writeBigPalette` COMPILED — and wrote the file only when the caller passed a handle;
called with `null` it built the local handle, discarded it, and wrote nothing. **0 errors, every
check count flat, 23 tests passing, for as long as that port has existed.** libGDX core moved 0
members, which says only that libGDX has no such receiver — not that the defect is rare.

*Fix kind: (a) engine. Built — one call to `operand` in each of the four positions, and no new rule.*

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

### K2.5 A pass gated on ONE of its targets is SWITCHED OFF for every program that lacks that target — 3 errors, and a whole pass silently inert

K2's bridge has TWO independent targets: `JavaIterable`, which exists when the program names
`java.lang.Iterable`, and `JavaCollection`, which exists when it names `java.util.Collection`. The
pass that applies the bridge at an OWNED callee's formals opened with
`if javaIterableSym == SymId.None then t` — a cheap "nothing to do" guard, written when the pass had
one target, and left in place when it grew the second.

**So a library that uses `Collection` throughout and never once mentions `Iterable` is a library
where the whole argument bridge is a no-op.** liqp is exactly that: 135 java files, `Collection` in
three public APIs, `Iterable` nowhere, and **not one `JavaCollection.from(` in the emitted port**.
What reaches the reader is `E134 None of the overloaded alternatives of method of in object Filters`
at each call where java's own `List`-is-a-`Collection` subtyping did not survive the retyping —
`mutable.Buffer` is not a `JavaCollection`, and the bridge that exists for precisely that was never
consulted.

**CORRECTION, and it is the better half of this entry: a check DID fire, on all five, and could not
say what it had found.** `collection-boundary` reported every one as a `ShimBoundary` — `argument:
Found scala.collection.mutable.Buffer / Required balticporter.runtime.JavaCollection`, with the java
file and line — and had done so on every run since the port began. What it cannot say is that a wrap
for that exact pair EXISTS. A `ShimBoundary` means "a retyped value met a shim-typed slot and no
wrap was inserted", and a reader takes that as K2's honest residue — the `Kind.Map`-into-
`JavaCollection` cell that is refused on purpose, the `keySet()` source the phase declines. Here
`coerce`'s own table answers the pair on its first line (`Kind.Seq` + `JavaCollection` →
`JavaCollection.from`), and the finding was a BUG REPORT that read as a residue report. Five of
them, for the life of the port.

So the durable rule is not "nothing could see it" — it is **a residue count is only as good as the
assumption that everything able to close it RAN**, and that assumption is checkable from inside the
phase: a reported boundary whose (source `Kind`, target shim) pair has a factory in `coerce` is not
a residue, it is an engine bug, and the phase holds both halves at the moment it files the finding.
Unbuilt, and priced here at the five findings it would have caught the first time this port was
measured. `members.tsv` cannot help — the output has been that way since the port began, so the
digest is stable — and no policy entry goes unmatched, because the phase is unscoped here.
**A pass that never runs looks exactly like a pass with nothing to do.**

The rule is `CLAUDE.md` §4.56's, one remove out: *a phase may conclude something only from what the
phase itself did to the thing it is concluding about.* "Is there a `JavaIterable` in this program"
is not a fact about a `Collection`-typed formal. The guard also bought nothing — `coerce` already
returns the argument untouched when no factory matches — and what it LOOKED like it protected (an
expected type whose head did not resolve, so `wants` is `Some(SymId.None)` and `contains` matches an
ABSENT shim) belongs per-target at the comparison, where it costs no other target its bridge.

**Measured on liqp: 90 → 87 errors, `collection-boundary` 13 → 8, 8 member digests over 4
members.** Five bridges are emitted where none was; three compile. The other two are the same
`Arrays.asList(arr)` sites K6.5 deliberately refuses to rewrite, and they are the caution:

**A bridge inserted over a value the phase REFUSED to move names the wrapper, not the boundary.**
`Insertions.of(Arrays.asList(arr))` gets `JavaCollection.from(java.util.Arrays.asList(…))` — the
node's type says `Buffer` because `transformType` moved it, while K6.5's aliasing refusal left the
VALUE a real `java.util.List`, so `from` receives a java collection. Same error count at those two
sites either way (the call could not compile before), and the shape is the one `CLAUDE.md` §1(b)
warns about. What made it look unfixable here was an ORDERING claim: `wrapIterableArgs` runs BEFORE
the rewrites (it must — a shim-typed formal has to be in place before overload resolution sees the
argument), so at wrap time "the phase has not yet decided to refuse the `asList`".

**CLOSED, and the ordering claim was about the wrong node.** The `asList` is an ARGUMENT, and the
traversal is bottom-up: by the time the enclosing call's arguments are bridged, that inner call has
already been through `transformApply` and is in its final form. So the question is answerable at the
wrap — and it is answered from the phase's own table, not from an arm-by-arm list:
`CollectionsTransform.handledStatic` asks whether the callee still stands at one of the `owner#name`
keys `staticRewrite` covers, and every arm that FIRED left its minted helper's symbol behind
instead. A call still at `java.util.Arrays#asList` is one this phase declined, whatever the node's
retyped `tpe` says. `coerce` refuses beside `isKeySetView`, which is §4.56's rule at a second site —
*the recorded type is not a witness of what the emitter will print.*

**And the message does NOT come back naming the boundary — that expectation was wrong, for a reason
worth knowing.** Both these callees are OVERLOADED, so what scalac prints for an argument that fits
no alternative is `E134 None of the overloaded alternatives of method of`, with the three formals
listed — not a `Found`/`Required` pair. The wrapped form printed a pair (`Found: java.util.List`)
because the wrapper is a single-alternative method: a sharper-looking message about
`JavaCollection.from`'s parameter rather than about the port's own `of`. What actually improves is
the emitted TEXT — `Insertions.of(java.util.Arrays.asList(…))` is the untranslated JDK call, at the
line, greppable — and the COUNT: those two seams are now reported instead of being hidden inside a
wrapper that made them look translated.

**The residue row therefore needs its own name.** A `ShimBoundary` whose (source kind, target shim)
pair HAS a factory is, by this entry's own rule, an engine bug — which is exactly what these two
would have read as. `CollectionBoundaryCheck.Issue.RefusedSource` is that row: same slot, and a
classification that says the `Found` side is the NODE's type and not the value's, that wrapping is
the thing not to do, and that it closes when the REFUSAL closes.

**Measured: liqp 56 -> 56** (main 27, test 29 — the count was never going to move, the call could
not compile either way), `collection-boundary` 16 -> 18, 8 member digests over 4 members, every
other check count flat and every other port byte-for-byte unchanged.

*Fix kind: (a) engine — the gate, DONE; the `asList`-refused source reaching the bridge, DONE.*

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

**…AND THE COMMONEST WAY TO WRITE SUCH A CALL WRITES NO RECEIVER AT ALL.** The inherited-call
rewrite above reads the RESOLVED METHOD's declaring type, which is right — and it is dispatched on
`Tree.Select(recv, m)`, because it needs a receiver term both to ask the question and to build the
answer. Java's double-brace initialiser has none:

```java
List<?> xs = new ArrayList<Object>() {{ add(a); add(b); }};
```

Inside a NAMED class this never showed, and that is worth knowing before designing anything here:
Spoon reports an implicit `CtThisAccess` there, so `SpoonTir` already emits `this.add(…)` — or
`Outer.this.add(…)`, choosing the innermost enclosing type that PROVIDES the member, which is a
walk only the frontend can do. Inside an ANONYMOUS class the target is absent, the call is a bare
`Tree.Ident`, and the entire family went through untouched: `add(…)` against a `mutable.ArrayBuffer`.
**4 errors on liqp's suite, all in one field initialiser**, and the shape is the one every library
that seeds a collection inline uses.

The receiver is java's own rule — the innermost enclosing class that provides the member, which for
a mapped collection's member is the innermost enclosing class that IS one. The traversal is
bottom-up, so every enclosing `new … { … }` is offered the calls under it before anything further
out is; it CLAIMS them when its own type answers `kindAt` and DROPS them when it does not. The drop
is the load-bearing half: `this` inside a nested anonymous class is that class, and an anonymous
class has no name to qualify with from inside one (T2/T3), so where the provider is an enclosing
ANONYMOUS class there is no receiver to synthesise and the call stays as java wrote it (M6). Note
the pending set is keyed by `Origin` and not by node identity — `StandardTraversal.mapTerm` REBUILDS
every node it visits, so no identity survives to the enclosing hook.

**And the four errors were the SMALLER half.** `add` has no scala namesake, so an unclaimed one is a
compile error and gets counted. `put` does: java's `Map.put` returns the PREVIOUS value and scala's
returns an `Option`, so a bare `put(k, v)` inside a double-brace initialiser COMPILED — the phase's
`put` rewrite (`this.put(k, v).getOrElse(null)`) simply never fired there, at **22 sites in one
library** whose emitted text no error, no check and no test could distinguish from the right one.
That is `CLAUDE.md` §4.4's shape reached through a receiver rather than through a statement form, and
it is why the blast radius of this fix is 9 members and not 4: the errors named one member of the
family and the claim repaired all of it.

Measured, `just measure-all`: **liqp 56 -> 52** (main 27 flat, test 29 -> 25), 9 members — 1 in the
main source set (`Parser#toBeReplaced`, ten `put`s) and 8 in the test — every other port
byte-for-byte unchanged and every check count flat on all fifteen.

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
| letting the synthesis run for a JDK-THROWABLE parent | that branch already nominates the widest and is measured (0 → 55 when it guessed) — leave it alone **wherever it NOMINATES.** Where it nominates NOTHING the fence is narrowed by C3, which is a different question and a different arm: no root passes through, so there is nothing for this table's warning to protect |
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

### K5.7 A class that IMPLEMENTS `Map.Entry` — the target is FINAL, so the PARENT stays java's

K5 is a class that EXTENDS a JDK collection and it is closed for the shim families. This is the cell
it does not cover, and the difference is that the target here is not a shim at all.

`java.util.Map.Entry` maps to `scala.Tuple2`, and for every USE that is exact — an entry read out of
a map really is a `(K, V)`, which is why the `entrySet()` rewrite can hand back the map itself. As a
PARENT it is impossible three times over, and all three at once:

| `Tuple2` | so `extends scala.Tuple2[K, V]` is |
|---|---|
| `final` | `class ComparableMapEntry cannot extend final class Tuple2` |
| takes `(_1, _2)` | `missing argument for parameter _1 of constructor Tuple2` |
| has no `setValue` | the write-through member has nothing to override |

**A phase may not emit a parent its target cannot BE.** So the parent is left as JAVA's. The class
really does implement `java.util.Map.Entry` — it is on the classpath and the class already declares
`getKey`, `getValue` and `setValue` — so the class itself compiles, and the seam moves to the SLOTS
where the port hands such a class to a `Tuple2`, which is where a reader can act on it. **liqp 49 →
47**, with `collection-boundary` 12 → 13: two errors become one counted, classified refusal
(`Issue.InexpressibleParent`), which is M6's bar met by construction rather than by leaving a broken
emission behind.

**The obvious alternative is a SECOND TRUTH and is refused.** A `JavaMapEntry` shim for the
implements-case, beside the `Tuple2` the use-case keeps, is the K5 answer and it does not transfer:
`entrySet()` yields a `Tuple2` in every port, so the two targets would meet at every crossing, in
both directions, needing a coercion each way — for one class in one library. §1's balance is
explicit that a mechanism is preferred to a special case, and this special case would be paid for by
every port that never implements the interface.

**What is left is `setValue`, and it is ONE MESSAGE OVER TWO CASES — only one of which is refused.**
K2 records the refusal as *a `Tuple2` has no write-through*, which is true and is stated at the
wrong granularity. The line is ***`setValue` is unmappable where the MAP IS NOT REACHABLE FROM THE
CALL***, and there is exactly one shape where it is reachable — java's own single legal mutation
during entry-set iteration:

```java
for (Map.Entry<K, V> e : m.entrySet()) { … e.setValue(v); }
```

The map is not on the entry and it IS ON THE LOOP, so `m.put(e._1, v)` is the same write. It is the
phase's own `Map.put` rewrite, `getOrElse(null)` included, because java's `setValue` returns the
PREVIOUS value exactly as `put` does — `update` would discard it, which is the §4.4 shape the `put`
arm exists to avoid. Four conditions, each a way the rewrite would be wrong without it: the loop's
SOURCE is a `Kind.Map` (the phase's own record, never a name test); the receiver is the loop's
BINDING and not some other entry, which would write to a different map; the source is a PURE PATH,
because java evaluates the iterable ONCE and the rewrite repeats it inside the body; and the binding
is not REASSIGNED, or `e._1` is no longer the key the loop is at.

**The case with no loop and no map** is a class holding a detached entry in a FIELD
(`Sort$ComparableMapEntry`), where the receiver's java type was `Map.Entry` and its value, after the
retyping, really is a detached pair. Restoring the field's java type would make the body compile and
move the seam to the CONSTRUCTION site, where a `Tuple2` meets a `java.util.Map.Entry` formal: one
error traded for at least one, unless the runtime supplies a `SimpleEntry`, which is exactly the
write-to-a-detached-copy K2 refuses.

Measured on liqp: **9 -> 8**, one site (`LiquidSupport#visitMap`), 1 member digest, every check
count flat.

**…AND THE REFUSAL STILL OWED AN EMISSION, because the RETAINED PARENT is an obligation.** Leaving
the body to fail to compile naming the member (M6) is the right answer for a method the class merely
declares. It is not available for THIS one: keeping java's parent makes the `extends` clause legal
and leaves the class INCOMPLETE — `java.util.Map.Entry` declares `setValue`, so the emitted class
must implement it or be abstract. The engine's own parent choice created an obligation the body
cannot meet, which is why the port cannot answer it either:

| answer | measured |
|---|---|
| leave the untranslated body | `value setValue is not a member of (K, V)` — 1 error, and the port cannot reach 0 |
| **`Substitutions.dropMethods` on the member** | the `Not Found` goes and `class ComparableMapEntry needs to be abstract, since def setValue(x0: V): V in trait Entry in object Map is not defined` arrives. **One error traded for one**, and the new one is INVISIBLE until the port is at 0 typer errors, because `RefChecks` does not run before then (`CLAUDE.md` §3) |
| `dropTypes` + `inject` | the type is `private static final` and NESTED in a class the port emits, so an injected file at that FQN would define `Sort` twice. Only dropping the whole enclosing filter expresses it, which is 130 lines of mechanically portable code by hand |

**So the answer is JAVA'S OWN, and it is a contract rather than a stand-in.** `Map.Entry.setValue`
is an **optional operation**, documented to throw `UnsupportedOperationException` where the backing
map does not support the write. A ported entry whose map is not reachable from the call IS that
entry, so the phase emits the refusal the interface prescribes — the same refusal K2 has always
made, expressed in code instead of as a compile error. It is the exact opposite of the `SimpleEntry`
K2 rejects: that would compile and write to a DETACHED COPY, succeeding while changing nothing
(§4.4's defect class), where this is louder than java and never quieter, and counted at the slot
(`Issue.InexpressibleParent`, `slot = member (implements) setValue`).

Scoped to the obligation and not to the receiver: a class that merely HOLDS an entry and calls
`e.setValue(v)` in a method of its own keeps the compile error, because no interface asked it for
that member and inventing a throw there would be the engine deciding what the method means. Both
directions are spec'd. Derived from the phase's own mapping — `UnsupportedOnTarget` is keyed on a
target in `UninheritableTargets` — never from a receiver's name (§4.56).

Measured on liqp: **main source set 1 -> 0**, `collection-boundary` 14 -> 15, 3 member digests, and
the emitted member carries its porter note.

**CORRECTION (audit-2 F1): the refusal above was matched by BARE NAME, so it refused two members
java runs.** "Derived from the phase's own mapping, never from a receiver's name" was true of WHICH
TARGET the table is keyed on and false of WHICH MEMBER it then substituted: the arm read
`unimplementable(s.name)` over the class body, which is a string test on the declaration and says
nothing about what the phase did to it. Two shapes fall through it, and both are §4.4's class — the
port throws where java succeeded, with a green compile, no check count moving and no test to see it,
because neither shape occurs in liqp:

| shape | java | the port, before the fix |
|---|---|---|
| a SELF-CONTAINED entry — `class Pair implements Map.Entry { V v; V setValue(V nv){ V o=v; v=nv; return o; } }` | runs, returns the previous value | `throw new UnsupportedOperationException` — the whole body discarded |
| an unrelated OVERLOAD — `void setValue(int a, int b)` beside the interface's member | a method the interface says nothing about | the same throw, for a five-letter name collision |

The fix is TWO conditions where there was one, and neither alone is sufficient:

- **the member is the INTERFACE'S**, by signature — `UnsupportedOnTarget` now holds a
  `MemberSig(name, arity)`, so `setValue(int, int)` is not `Map.Entry#setValue(V)`. Arity is the
  whole of the signature available: the declaring interface is EXTERNAL, interned with no member
  list, and its parameter is a type variable that erases to `Object` anyway;
- **the phase can point at what it BROKE** — the TRANSLATED body still selects a member on a
  receiver this phase retyped to a target in `UnsupportedOnTarget`. That is the licence for the
  substitution and the only reading of §4.56 that holds here: the optional-operation contract makes
  a throw CONFORMING for an entry that cannot perform the write, and says nothing about one that
  can. The reference found is recorded on the decision (`broke=scala.Tuple2#setValue`) and reaches
  the porter note, because a reader of the emitted throw cannot otherwise recover which call it
  replaced.

Measured on liqp: **0 -> 0 errors, suite 357/218 -> 357/218**, every check count flat, 3 member
digests — `Sort$ComparableMapEntry#setValue(V)` and its two enclosing types, and the only text that
moved is the new `broke=` pair. liqp's own entry DELEGATES (`return entry.setValue(value)`), so it
keeps the refusal, which is what makes the regression invisible to this corpus and is exactly why
the two shapes are spec'd rather than measured. *Fix kind: (a) engine.*

**The transferable half is the middle row of that table.** A `dropMethods` key that removes a member
an emitted parent DECLARES leaves the class abstract, and nothing in the engine reports it: it is
not a check, not a finding, not a member digest, and not a typer error until the port is already
green. Any port at a non-zero error count that reaches for `dropMethods` on an `@Override` is buying
a failure it cannot yet see.

*Fix kind: (a) engine — the parent restore, the loop-reachable half of `setValue`, and the
field-held half as the interface's own documented refusal. Nothing here is (b) or (c): the
obligation is one the engine's own mapping created, so a manifest key cannot discharge it.*

### K5.8 A `super` receiver is a SYNTAX question, and it is answered of the RESULT — not of the arm

CLOSED. Scala's grammar admits `super` in exactly one position, as the QUALIFIER of a member
selection; java has no such rule, so an inherited call on a class that EXTENDS a retyped collection
can be rewritten into a shape that puts it somewhere illegal. Three did: `entrySet()` maps to the
RECEIVER ALONE (`for (e <- super)`), the `Seq` `get` maps to an application of it (`super(i)`), and
every `+=`/`-=`/`++=` rendered INFIX (`super ++= m`). All three are E040 SYNTAX errors, which are
strictly worse than the type errors they replace — a syntax error cannot be attributed to a member
and can take the rest of the file with it.

K5 answered with a BLANKET refusal, on the stated grounds that *which of these renders infix is a
fact about the EMITTER that this phase cannot read*. Both halves of that turned out to be movable:

- **the infix face is gone at its source.** `TirEmitter.applyStr0` now renders an operator on a
  `super` receiver as an ordinary selection — `super.++=(m)`, which is legal and is the only legal
  spelling of that call. The emitter is where the position rule lives, so the fix belongs there and
  not in a phase guessing at it;
- **what remains is a STRUCTURAL property of the RESULT**, which the phase can simply check:
  does every `Tree.Super` in the term I just built stand as a `Tree.Select`'s qualifier?
  `superPlaced` asks exactly that, of the rewrite AFTER it is built and never of the arm — so a
  rewrite added later is covered by construction and no arm can reintroduce the failure by
  omission. That was the one property the blanket refusal was bought for, and it is kept.

`super.putAll(m)`, `super.contains(k)` and `super.getOrElse(k, null)` now translate; `entrySet()` and
the `Seq` `get` stay untranslated under java's own names and fail to compile there (M6). Measured on
liqp: **14 -> 13**, one site, and the two refusals still reported.

**And the refusal had ONE more answer under it, which is a whole-program question rather than a
syntax one: stand on `this`.** `super.m` is java's non-virtual call of the nearest inherited `m` and
`this.m` is the virtual one, so the two name THE SAME MEMBER exactly when nothing between them can
override — neither the class itself, nor any class IN THE PROGRAM that extends it, declares `m`.
Both halves are needed and each was checked: an override on the class makes `this.m` recurse into
itself, and one on a SUBCLASS makes an instance of that subclass dispatch somewhere `super.m` never
would. `superIsThis` asks exactly that of `Program`, transitively (`A extends B extends C`).

Three things about the shape, none of them incidental:

- **it is a FALLBACK, reached only where the super-placed rewrite failed.** Applied up front it
  would move `super.putAll(m)` to `this.++=(m)` as well — the same call, and a diff for nothing. So
  the retry runs `.orElse` behind `superPlaced`, and every arm that already translated is untouched;
- **the retry goes back through the SAME function** with a `this` receiver, so no arm is spelled
  twice. Its own receiver is not a `Super`, so it returns directly and the placement filter is then
  trivially satisfied — applied anyway, because a filter omitted on the grounds that it holds is the
  omission `superPlaced` exists to prevent;
- **"in this program" is the honest scope and it is stated, not assumed.** An emitted class can be
  extended by code the port never sees, and no whole-program question answers for that. What makes
  it admissible is that the alternative is not a correct emission but NO emission — the refused
  rewrite leaves a call that does not compile — so the choice is between an exact answer for every
  subclass the program declares and no answer at all.

Measured on liqp: **10 -> 9**, one site (`Sort$SortableMap#toString`), 3 member digests, every check
count flat.

*Fix kind: (a). Universal — a scala grammar rule, no library involved.*

### K5.9 A METHOD REFERENCE is a second NODE SHAPE of a rewrite keyed on a CALL — and it has to be LOWERED

CLOSED. `CollectionsTransform`'s member table answers `getKey`, and it is keyed on `Tree.Apply`.
`Map.Entry::getKey` is a `Tree.MethodRef`, which the EMITTER expands to `self$ => self$.getKey()`
AFTER every phase has run — so the rewrite never saw the call, and the emitted lambda selects a
member the retyped receiver does not have (`value getKey is not a member of (String, Insertion)`).

Two phases already look at both node shapes (`CallSiteSubstitutionTransform`,
`BeanPropertyTransform`), so this is one more shape of an existing rewrite rather than a new
mechanism. What makes it worth its own entry is that **it cannot be a symbol swap, and it cannot be
fixed at the emitter either**:

- `getKey` becomes `_1`, which turns an `Apply` into a `Select`. There is no method left to point
  the reference at, so re-targeting `mr.method` has nothing to target;
- teaching the emitter's own expansion the phase's table fails for the same reason from the other
  side — it renders `self$.<member>(<args>)`, and `_1` is parenless. It would also put a phase's
  policy in the backend, which §4.575 forbids for exactly the reason it forbids an authored note.

So the phase LOWERS the reference into the lambda the emitter would have built, with the rewritten
term as the body: it synthesises the `Apply` the reference stands for, runs the SAME `rewrite` that
answers a written-out call, and wraps the result. Only an UNBOUND instance reference is lowered — a
static one is `Type.member` with no receiver to rewrite, and a bound one already carries its
receiver as a term and is the `Apply` case one node out.

**The parameter is emitted UNANNOTATED, and that is the part a retry will get wrong.** Java writes
this qualifier RAW (`Map.Entry::getKey`), so the retyped type renders `Tuple2[?, ?]`; annotating
with it makes the body's `_1` an unusable capture and the enclosing `collect` yields `Set[Any]`
where a `Set[String]` was wanted. Scalac takes the parameter from the expected function type, which
is java's own poly-expression rule and is what the emitter's own expansion already emits. That
needed one emitter capability — a `ValDef` whose type is `NoType` renders as a bare name — and
nothing else can produce one: every declaration the frontend builds carries java's own type, so a
lambda parameter a phase mints is the only `ValDef` without one.

Measured on liqp: **8 -> 7**, one site (`Insertions#getNames`), 3 member digests, every check count
flat.

*Fix kind: (a). Universal — a rewrite owes every node shape its member can appear in.*

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

**`Collectors.toSet` and `Collectors.toMap` are now BUILT**, and what kept them off `toList`'s arm
is the transferable part: `collect(toList())` collapses to NOTHING because the receiver already IS
the sequence, while these two change the TARGET TYPE and therefore need a helper each. Neither was
guessable, and `toMap` is why the entry said so — java's TWO-argument form **throws
`IllegalStateException` on a duplicate key**, where the obvious `.map(x => k(x) -> v(x)).toMap`
silently keeps the last one. A stream whose keys collide is a bug java reports loudly and the naive
translation hides, with no compile error and no count moved (§4.4). The three-argument form takes a
merge run as `merge(EXISTING, INCOMING)` — an order that inverts every non-commutative resolver —
and REMOVES the mapping when the merge returns null, which is `Map.merge`'s documented behaviour.
The mappers are declared with JAVA's `Function`, for `removeIf`/`Predicate`'s reason above:
`Function.identity()` reaches that slot in real code and is a `java.util.function.Function`, while a
lambda written at the call site SAM-converts to one.

**And a third thing was wrong here the whole time, invisibly**: the collapse emitted the SHIM's
accessor unconditionally — `Tree.Select(recv, asScalaBuffer)` — which is a table lookup keyed on one
kind applied to every kind. `asScalaBuffer` is an extension in `JavaCollection`'s companion, so it
is right only where the declaration was a `java.util.Collection`; a `java.util.List` retypes to a
`Buffer` and a `java.util.Set` to a `mutable.Set`, and both got the Buffer-side accessor.
**No check could see it**: the collapse FIRED, so nothing reported an untranslated chain, and the
error surfaced only as `value asScalaBuffer is not a member of scala.collection.mutable.Buffer[…]`
three files apart on liqp. The source is now chosen by what the receiver IS (`kindOf`/`shimSyms`),
falling back to the TARGET OF THE TYPE THAT DECLARES `stream()` — which is what makes
`class Own extends AbstractCollection<T>` still take `asScalaBuffer`, correctly, because `Own` really
does extend the shim after the retyping. A `Set` or `Map` source is `.toBuffer`, a copy on the same
footing the collapse already accepts.

Two that WERE on this list now ship in `JavaCollections`:
`Stream.sorted(Comparator)` as `sortedWith` (a copy, with the doc explaining why the name matters)
and `Collectors.toCollection(f)` as `into` (bounded by `Growable`).
`java.util.Collections`' statics were the same story and are now **CLOSED**, which is worth keeping
because the reason they were open is the reason they closed. `unmodifiableList`/`Set`/`Map` were
recorded here as unmappable — no read-only `Buffer`/`Set`/`Map` view to map onto, so the available
shapes were a COPY (detaches the view) and the IDENTITY (drops the immutability), both of which
compile. That was true of the STDLIB and only of the stdlib: the RUNTIME can supply the view, and
now does (`JavaCollections`' private `FrozenBuffer`/`FrozenSet`/`FrozenMap` delegate every read to
the collection they wrap and throw `UnsupportedOperationException` on every write, which is java's
own answer rather than an approximation of it). The same three classes are what let the IMMUTABLE
producers — `emptyList`, `emptyMap`, `emptySet`, `singletonList`, `singleton`, `singletonMap` —
map at all; `mutable.ArrayBuffer.empty` would have turned a loud `UnsupportedOperationException`
into a silent write to whatever shared static the factory's result was stored in (§4.4). liqp
92 errors at that step, with `jdk-surface` 19 → 12.

**The transferable rule: "scala has no such type" is a claim about the STDLIB, and this engine
ships a runtime.** Before recording a JDK utility as unmappable, ask whether the missing semantics
is expressible as a runtime type — the same question `JavaIterator`/`JavaIterable`/`JavaCollection`
already answered YES to for the collection interfaces. `unmodifiableCollection` had been mapped all
along, on the shim, for exactly this reason; the three that stayed open differed only in that
nobody had written their target. `CLAUDE.md` §4.5's ban is on a PORTED class inheriting a scala
collection trait — these are private, nothing extends them, and the ban does not reach them.

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

**And the collapse assumes a TERMINAL, which the third rule is about.** `xs.stream().filter(p)` with
no `collect` is a chain whose VALUE is still a `Stream`, and it reaches java again at a `Stream`
formal — `Stream.concat`, `Stream.of`, a third party's own. The collapse has already made it a
`Buffer` by then, and no `toJava` overload serves that slot: `Found: Buffer[LNode] / Required:
Stream[? <: LNode]`, which `CollectionBoundaryCheck` counted honestly as `UntranslatedFamily` and
which nothing could close.

`JavaCollections.toStream` closes it, at the CONSUMER slot and not by declining to collapse. Three
things that decided the shape:

- **not-collapsing is worse.** Left alone, `xs.stream()` emits a `stream()` call on a `Buffer`,
  which has no such member — so the chain would have to be bridged at its RECEIVER instead, and a
  receiver is the one position nothing in this phase bridges;
- **it is faithful for the same reason `toJava` is at the universal slot**: java's value at that
  slot really WAS a `Stream`. What is not restored is LAZINESS — the operations before the wrap have
  already run — which is the collapse's own documented divergence met at its boundary rather than a
  new one, and it is unobservable wherever the terminal consumes the whole stream, which a `Stream`
  formal's callee does;
- **`asJava.stream()` and never a hand-built iterator**: java's `Stream` carries spliterator-derived
  size and ordering characteristics that `toArray` reads. `Kind.Map` is excluded — java's `Map` has
  no `stream()`, so no valid java sends one to such a slot, the same asymmetry `coerce`'s
  `JavaCollection` row already records. EXTERNAL formals only: a `Stream` formal on a declaration
  this port emits is one the port itself decided, and the collapse would have moved it too.

Measured on liqp: **6 -> 5**, one site (`NodeVisitor#getJekyllIncludeInsertionNode`),
`collection-boundary` **14 -> 13** — the closed row is exactly the `UntranslatedFamily` this seam
was being counted as — and 3 member digests.


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
| `Arrays.asList(xs)` | `JavaCollections.asList(xs.asInstanceOf[Array[Object]])` — E007 | `JavaCollections.asListView(xs)` — java's LIVE, fixed-size view (see the closure below) |

The accident matters more than the failure: the frontend declines to pack PRIMITIVES, so the one
shape everybody writes arrived as bare elements and the convention clash never showed. **A rewrite
onto a differently-shaped runtime signature must normalise the pack, not assume either form** — the
frontend produces both, conditionally.

Two rules the fix rests on:

- **Open the pack into separate arguments**, which is `CLAUDE.md` §6's spread with no spread node
  needed and makes both frontend outcomes emit one shape. A LITERAL array in the slot
  (`asList(new String[]{a, b})`) opens too, soundly: the array is allocated at the call, so no
  caller holds the alias.
- **A single ARRAY-typed argument is the ALIASING form and goes to a DIFFERENT helper.** Java
  returns a live view of the caller's array; the vararg helper's `A*` would copy it and silently
  detach every aliased write (§4.4). It was REFUSED for two waves on the grounds that a faithful
  live view, though expressible, was not reachable from here — see the closure below for why that
  was a fact about the argument rather than about the tree. `JavaCollections.asListView(arr)` is
  what it takes now.
- …**and NOTHING DOWNSTREAM MAY PAINT OVER THAT REFUSAL.** The refused call keeps the JDK name, so
  the value really is a `java.util.List` — while its NODE says `Buffer`, because the position-blind
  retyping moved the type on both sides of it. Read from the node alone, `coerce` found a factory
  and emitted `JavaCollection.from(java.util.Arrays.asList(…))`: a wrapper over a value this phase
  had just declined to move, which is the shape §1(b) warns about and which K2.5 measured and left
  open. Closed there, by the phase's own table (`handledStatic`) rather than by an arm-by-arm list,
  and the seam is counted as `CollectionBoundaryCheck.Issue.RefusedSource` — a row that says the
  `Found` side is the node's type and not the value's, so it cannot be mistaken for the engine bug a
  bare `ShimBoundary` at a pair with a factory would be.

**CLOSED — and what was refused was the COPY, never the form.** The rows above say a single
array-typed argument is REFUSED, and every word of the reason stands: java returns a live view, a
`Buffer` built from `A*` copies, and a copy compiles while silently detaching every aliased write.
What was wrong was the conclusion that there was no third answer. `JavaCollections.asListView(arr)`
is java's own: a fixed-size `Buffer` reading AND WRITING THROUGH the array, with `add`/`remove`/
`clear` throwing `UnsupportedOperationException` at the call `java.util.Arrays$ArrayList` throws it
at. Nothing in it is an approximation, and it needs no composition — the earlier idea of
`fromJava(Arrays.asList(arr))` (a Buffer view of a List view) was two live views where one does.

**What "blocked" it was a fact about the ARGUMENT read as a fact about the TREE.** This entry said
the frontend has already coerced the argument to the erased formal (`Array[Object]`), so the element
type is gone. True of the argument node; false of the call. The coercion is a `Tree.Typed` the
frontend synthesised for the OLD callee's formal, and java's own inference is recorded on the CALL —
`Arrays.asList(arr)` over an `Insertion[]` has result type `List<Insertion>`, which this phase has
retyped to `Buffer[Insertion]`. So the element type is recoverable by looking THROUGH a cast the
rewrite is about to make irrelevant, which is CLAUDE.md §1(b)'s *a coercion may not precede a
rewrite of the same call* and `arrayArg`'s rule at a second site. The strip is structural and names
no type: take the cast off exactly when the array it wraps has the element type the call RESULTS in.

**The aliasing form has THREE node shapes, not two, and the third is this entry's own fourth case
biting the arm that reads it.** After K6.5's fourth case an external callee's array pass-through is
a `Tree.Spread` (`arr*`), so the single-argument arm sees a spread whose type is an array — right
about the shape, wrong about the node. Passed through, the rewrite emitted `asListView(arr*)` and
scalac said `Sequence argument type annotation '*' cannot be used here`: the rewrite firing at the
right site with the wrong shape, at every one of the twelve sites. **A phase that pattern-matches an
argument list owes every shape of the frontend's vararg convention** — the same sentence this entry
already carries for the composition above, met a second time by the arm one line down.

Measured on liqp: **38 → 26** (main 27 → 16, test 11 → 10), twelve sites, 22 member digests, every
check count flat. The earlier accidental measurement (liqp 76 → 67 when K15's wrap reached these
calls) was right about the count and wrong about the value — that path shipped `Buffer[Object]`
where java inferred `List<Insertion>`, and read green only because that library's filters are
`Object`-typed throughout.

**THE FIFTH CASE — the ELEMENT form needs java's INFERENCE written down, not just its arity.**
CLOSED. Opening the pack made the elements separate arguments, which is what let scalac describe a
disagreement it could not reach before: `Arrays.asList(98, "97", true, false, null)` is a
`List<Serializable & Comparable<…>>` in java, because java infers `T` across all the arguments at
once and BOXES what it must. Scala infers `A` across them too — and its `Int`/`Boolean` are VALUE
types that join to nothing java would name, so at an INFERRED `A` scalac declines the boxing
conversion outright ("implicit conversions were not tried because the result of an implicit
conversion must be more specific than T") and reports **one mismatch per element**.

Java's answer is recorded on the CALL, exactly as it is for the aliasing form above, so the rewrite
writes it down: `JavaCollections.asList[java.io.Serializable](98, "97", true)`. With `A` explicit
the conversion IS tried, `Predef.int2Integer` applies, and the emitted list is java's. Written only
where java's answer CAN be written — a `TypeBounds` is a wildcard and `?` in a term position is not
syntax (K10), an inference marker names nothing (G2) — and left to scala's own inference otherwise,
which is what those calls had before.

Measured on liqp: **19 → 14**, six per-element mismatches becoming one aggregate one, and the
aggregate is a different fact entirely (G24: java's `<T>` bound is vacuous and the emitted
`<: java.lang.Object` is not).

`refusedRewriteSource` and the `handledStatic` record it reads are UNCHANGED and still load-bearing:
they answer "did this phase decline this call", and a call the phase now rewrites carries the minted
helper's symbol, so the wrap fires for it exactly as for every other value the phase produced. The
mechanism was never about `asList`.

**THE THIRD CASE — the convention stops at the PROGRAM'S EDGE, and it is not about `asList` at
all.** CLOSED. The two cases above are one rewrite retargeting one helper. The general fact is that
the materialised pack is right only while BOTH halves are ours: the emitted `def f(xs: Array[T])`
and the emitted `f(Array[T](a, b))` agree by construction. An **EXTERNAL** callee's half is a CLASS
FILE nothing in this port can move, and scalac reads a java `T...` there as a REPEATED parameter —
so the pack is one argument too many at *every* external java vararg method, which every library
meets. `Paths.get(".")` emitted `Paths.get(".", scala.Array[java.lang.String]())` and read
`Found: Array[String] / Required: String`.

**The loud half is the smaller half.** Where the repeated element type is `Object` the pack
CONFORMS — `Array[Object] <: Object` — so `String.format(fmt, Array[Object](a, b))` compiles and
passes the whole array as ONE `%s`. That is CLAUDE.md §4.4's shape exactly: no error, no moved
count, no failing check, and a wrong string at run time. In liqp: **9 sites that failed to compile
and 9 more that compiled**, in the same library, from the same defect.

What shipped, in two halves that are each meaningless alone:

- the FRONTEND packs into `Tree.Repeated` rather than `Tree.NewArray` when the callee is external,
  decided STRUCTURALLY (§4.56) from the declaring type being a SHADOW — a reconstruction from
  bytecode, the same signal `coerceArgsFixed` already reads, and the only one that survives
  `noClasspath` (where `getExecutableDeclaration` is non-null for the JDK too). A resolution root's
  java is parsed as SOURCE and stays ours, which is what keeps a dependent port's calls into its
  base on the materialised form both modules emit;
- the EMITTER flattens a `Repeated` in an ARGUMENT position into the argument list. Invisible for
  one element or more — the node renders comma-joined and so does the arg list — and decisive for
  ZERO, where a node rendering `""` leaves `f(a, )`. Java's `get(".")` against `get(String,
  String...)` is exactly that call, so the empty spread is the normal case and not an edge.

**And no CHECK is available for the silent half, which is a fact about the descriptor rather than a
gap in effort.** A check over the trees would have to ask "is this external array formal really a
`T...`?", and `SpoonTir.descriptorOf` spells `T…` and `T[]` identically BY CONSTRUCTION (its own
doc says so, deliberately — it is what makes a vararg member's key match). The fact lives only in
the class file the frontend read, so a check could do nothing but read the frontend's answer back,
which is precisely what `BreakCatchCheck`'s contract forbids. The gate is therefore the frontend
SPEC — both directions, negative-tested (`SpoonTirBodySpec`) — plus the emitter's own
(`TirEmitterSpec`). **liqp 67 → 58**, with every check count flat.

**THE FOURTH CASE — the MIRROR: java PASSES AN ARRAY THROUGH the same slot.** CLOSED. The third
case is about a call that spells its arguments out; this one is about java's own vararg-FORWARDING
idiom, which is at least as common — `String.format(fmt, args)`, `Arrays.asList(xs)`,
`logger.debug(msg, args)`, `Insertions.of(insertions)`. The frontend recognised it (`passesArray`)
and answered "java passes it through, so do we", which is right for exactly the half the third case
was right for and wrong at the same edge, in the same two faces:

| where the callee is | java | emitted before | what it meant |
|---|---|---|---|
| OURS | `pick(parts)` | `pick(parts)` | correct — the parameter is emitted `Array[String]` |
| a CLASS FILE, element `Object` | `String.format("%s %s", args)` | `String.format("%s %s", args)` | COMPILES; the array is ONE `%s` and the second throws `MissingFormatArgumentException`. Measured on 3.8.4 |
| a CLASS FILE, element not `Object` | `Paths.get(".", parts)` | same | uncounted `Found: Array[String] / Required: String` |

So an external callee's pass-through becomes a SPREAD (`Tree.Spread`, rendered `xs*` — §6, never
`: _*`), built through `coerceArgsFixed` so the erasure cast an `Object...` formal needs is still
that function's one answer. Three things measured while doing it:

- **the spread is FAITHFUL, not a compromise.** `java.util.Arrays.asList(arr*)` on 3.8.4 yields a
  list of `arr.length` elements that still ALIASES `arr` — a write through `arr` is visible in the
  list — which is exactly what java's pass-through does. That is a different answer from the `A*`
  runtime helper one layer up, which COPIES, and it is why the `asList` refusal above stays a
  refusal while the JDK call under it becomes correct;
- **a bare `null` in the slot is java's null ARRAY**, and `f(null*)` renders it as one: it compiles,
  and it throws where java throws;
- **the count does not move, and that is the point.** liqp main 31, test 61, every check flat, 40
  members changed — and ELEVEN error messages changed from `Found: java.util.List[Array[Object]]`
  to `Found: java.util.List[Object]`: same error, because the collections retyping is what those
  slots still disagree about, but the value in them is now the N-element list java built instead of
  a one-element list holding the array. The sites that were SILENT move no message at all.

*Fix kind: (a). Universal.*

**CORRECTION (audit-2 F3): "the argument is an array" is not the question — java's is ASSIGNABILITY,
and a PRIMITIVE array is assignable to nothing but its own array type.** `passesArray` read the
argument's type, asked whether it was an array at all, and never looked at the COMPONENT. So `int[]`
at an `Object...`/`T...` slot was marked pass-through, when java does not forward it at all: it
materialises `new Object[]{ intArr }`, ONE element holding the array. That is the classic gotcha —
`Arrays.asList(intArr)` is a `List<int[]>` of size 1, not a list of ints, and
`String.format("%s", intArr)` prints `[I@…` for one `%s`. Read as a pass-through it becomes the
SPREAD above, and both faces are §4.4's:

| java | emitted before | what it meant |
|---|---|---|
| `Arrays.asList(intArr)` — a `List<int[]>` of size 1 | `Arrays.asList(intArr*)` | COMPILES, and is a list of `intArr.length` ints. No error, no moved count |
| `String.format("%s", intArr)` — one `%s`, printing `[I@…` | `String.format("%s", intArr*)` | the call's ARITY changed |
| an OWNED callee, `def f(xs: Array[Object])` | `f(intArr)` | `Array[Int]` does not conform — Scala's `Array` is invariant |

Pass-through now requires the components to AGREE: identical where either is primitive, and a
reference component is left alone because `String[] <: Object[]` is java's own array covariance and
the forward really is a forward. The CAST wins where there is one, which is the type java resolved
the slot against and the same reason `(String[]) null` is read from the cast rather than the literal.

Measured: **all eleven lanes, 0 member digests moved and every check count flat** — no library in
this corpus forwards a primitive array through a reference vararg slot, which is exactly why the
gate is `SpoonTirBodySpec`'s three cases (primitive-at-reference packs, primitive-at-same-primitive
passes, reference-at-reference passes) and not a number. *Fix kind: (a). Universal.*

**…and the third case COLLIDED with the first, which is what a composition costs.** The two halves
above landed in different steps and each was green alone. The rewrite reads its ARGUMENTS and knew
one pack shape (`Tree.NewArray`); the frontend now mints the other (`Tree.Repeated`) at exactly the
callee this rewrite is about, since `java.util.Arrays.asList` is a class file and nothing else. Read
as an ordinary argument a `Repeated` carries an ARRAY node type — so it fell into the ALIASING arm,
and the rewrite REFUSED a pack it had itself just opened: `asList(xs, xs)` and `asList(s)` emitted
`java.util.Arrays.asList(…)` under a retyped return type, while `asList(1, 2, 3)` — never packed —
was rewritten. **A phase that pattern-matches an argument list owes BOTH shapes of the frontend's
vararg convention**, and the only thing that can see the omission is a spec over the composition:
neither wave's own suite moved. Measured on liqp's test set, **59 → 61 with three sites repaired**:
three `LookupNodeTest` calls stopped emitting the JDK name, and one heterogeneous
`asList(98, "97", true, false, null)` turned one aggregate mismatch into six per-element ones, which
is the compiler describing what it could not reach before.

*Fix kind: (a). Universal — java's call syntax against a class file, no library involved.*

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

**A SECOND, independent reason to re-bind: the body WRITES to the binding.** This entry is about the
binding's declared TYPE and says nothing about its mutability. Java's `for (Object obj : array)`
binding is an ordinary local, so `obj = evaluated.toLiquid()` is legal java (liqp `Sort.java:111`);
Scala's generator binds a `val` and the same body reads `Reassignment to val obj`. That is the same
java fact `MutableParamsTransform` handles for a PARAMETER, one node kind out — and a `for`
generator cannot itself be a `var`, so the answer is the alias this entry already built, with `var`
in place of `val` and no cast (the widening is what earns the cast, and this is not it).

The two reasons COMPOSE into one alias rather than two: a binding that is both widened and written
to gets `var name: T = name$e.asInstanceOf[T]`. And the alias is inside the loop BODY, so a write
cannot leak into the next iteration — which is java's semantics exactly, since java assigns the
binding afresh from the iterator each time round.

Read with `StandardTraversal` and counting `IncDec` beside `Assign` (§3); over-approximating costs a
`var` where a `val` would do, under-approximating costs a compile error. **liqp 57 → 56**, 0 members
moved on any other port.

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

### K15. A retyping phase owes a boundary count at EXTERNAL callees — CLOSED where a class file can be READ, counted where it cannot

**The seam nothing could see, and the one that a new library meets first.** CLAUDE.md §1(b) states
it for a SCOPE seam — "the callee is then the JDK's own external symbol, which the frontend interned
without a signature". An external callee is the same fact one step out and it is worse, because the
signature is a fact about a COMPILED CLASS FILE that no phase can move:

- an ANTLR parser's `ctx.atom()` really returns a `java.util.List<AtomContext>` — but
  `transformType` is position-blind, so the CALL NODE's type was retyped to `Buffer`, and the
  for-each, `coerce` and `CollectionBoundaryCheck` all believe it;
- a generated lexer's constructor really takes a `java.util.Set<String>` — and `coerce` reads the
  formal THROUGH `remap`, so it sees `mutable.Set` on both sides and declines to bridge.

**BOTH SIDES READ THE SAME MOVED TYPE**, so a check comparing node types reports ZERO on exactly the
seam the retyping made. Measured on liqp before any of this: **15 compile errors at one third-party
package against 0 findings**.

**The PRODUCER half is CLOSED**, and the observable it had to be built on is the finding worth
keeping. The arm was first written to read the callee's declared RESULT TYPE — and at the time there
was none: **every external member the frontend interned carried `NoType`**, measured at 1157 external
callees on liqp with not one `MethodType`, `java.lang.Object#toString` included. (That is the state
the consumer half below then FIXED; the producer arm was not rewritten to use it, because the
observable it settled on is sound and re-deriving it would move emitted text for no gain.) The only
evidence that a value
crossing an external call is a collection is the NODE's type, which Spoon resolved and this phase
then moved — still §4.56's question answered from the phase's own record, since the node says
`Buffer` precisely because the phase put it there. `JavaCollections.fromJava` wraps it into a LIVE
`scala.jdk` view, and needs no evidence of WHICH java type it was: the helper is overloaded and
scalac resolves it against the real static type from the class file, which is the one thing in the
whole seam that is not in doubt. **liqp 86 → 76**, the 13 for-each errors included.

Four exclusions, each measured as a false positive before it was written:

| excluded | because |
|---|---|
| a symbol this phase MINTED | every rewrite target (`+=`, `filtered`, `fromJava` itself) is owned by nothing and named by no class file |
| a callee whose OWNER is a mapped type or one of its targets | `java.util.Map#keySet` is an external method returning `java.util.Set` whose value IS already scala's, because the RECEIVER moved |
| a callee with NO owner at all | `scala.<op>#+` is an interned operator, not a member of any class file — `"…" + aMap` was reported twice |
| a member `handledStatics` covers | `staticRewrite` returning `None` is either "no arm matched" or "an arm REFUSED", and only the first is a seam (K6.5) |
| a GENERIC PASS-THROUGH | the node's type is evidence of two different things — see below |
| a target NO CONVERTER PRODUCES | `kindOf` holds every mapping target; `fromJava` produces five shapes — see below |

**A WRAP MAY ONLY BE EMITTED TOWARD A TARGET THE HELPER CAN ACTUALLY PRODUCE, and "is this a type
the phase produced" is not that question.** The arm gated on `kindOf.contains(s)`, which holds EVERY
mapping target — `ArrayBuffer`, `ArrayDeque`, `mutable.TreeMap`, `mutable.LinkedHashMap`, `Tuple2` —
while `JavaCollections.fromJava` is five overloads returning `Buffer`, `Set`, `Map`, `JavaIterator`
and `JavaIterable`. So an external callee declared to return a CONCRETE `java.util.HashMap` got a
wrap whose result (`Map`) does not meet the node's own claim (`mutable.HashMap`), and a
`java.util.Map.Entry` return got one with no overload behind it at all. The failure shape is the
worst this seam has: `E134 None of the overloaded alternatives of method fromJava` — an error naming
the HELPER instead of the boundary, which is the same complaint this entry already records against
the pass-through case, arriving by a different route.

The predicate for it — `liveWrappable`, a TARGET test read in the direction the phase moved the type
(§4.56) — **had been written for exactly this and was NEVER CALLED**, while a hand-written
`s == javaCollectionSym` stood in its place covering one of the five refusals. That is the shape to
look for: a refusal expressed as a comparison against ONE known-bad value is a refusal that will be
right until the second bad value exists, and nothing reports the difference. Note also that it is
invisible to every JDK-only fixture: every JDK member returning a concrete collection type is owned
by a type the mapping already covers, so it is excluded one row above and the arm is never reached.
The negative test needs a COMPILED CLASS FILE of its own (`CollectionsTransformSpec.portAgainst`,
built the way `ExternalSignatureSpec` builds its partial classpath).

**Measured on liqp: 87 → 74 errors** (main 28 → 27, test 59 → 47), `collection-boundary` 8 → 10 on
the base and 0 → 12 on the suite — the two `new HashMap<>(){{ put(…) }}` double-brace initialisers
and the `mapper.readValue(s, HashMap.class)` site, each now a counted refusal instead of an E134 with
a cascade behind it. 12 member digests over 4 units.

**The node's type means two different things, and telling them apart is the whole subtlety.** Where
the callee's result is a real `java.util.List`, the node says `Buffer` because this phase MOVED it
and the value is java's. Where the callee's result is a TYPE VARIABLE, the node says `Buffer`
because the CALLER HANDED IT ONE: `Objects.requireNonNull(m)` and `ThreadLocal<Map<K,V>>.get()` give
back exactly what the port put in, already a scala collection, and wrapping converts a value that
was never java's. Measured at **7 sites on liqp** (`76 → 70`), each emitted as
`fromJava(java.util.Objects.requireNonNull(aScalaMap))` — an E134 naming the HELPER rather than the
boundary, which is the worst kind of error this seam can produce.

With no external signature there is no way to ask "is the result a type variable", so it is answered
STRUCTURALLY from the call itself: **the value passes through iff the result type already occurs on
the INPUT side** — as an argument's type, or anywhere inside the receiver's. That is what a generic
pass-through is, and it costs the honest cases nothing: `ctx.atom()`'s receiver is a parse-tree
context mentioning no collection, and `ServiceLoader<T>.iterator()`'s receiver mentions `T` but not
the `JavaIterator` its result became.

**…and that guess is CONSULTED LAST, because a guess that suppresses ALSO suppressed the count.**
"The result type occurs on the input side" is equally the shape of every non-identity `List`→`List`
third party — `reverse`, `sorted`, a cache's `getOrDefault` — where the value crossing the call
really is java's; and of every concrete-returning member of a generic holder instantiated at a
collection (`Holder<List<String>>.names()`), where the RECEIVER carries the occurrence and nothing
bridges a receiver. The suppression was an EARLY EXIT returning the tree, so those calls got no wrap
AND no finding: **the pre-K15 state, at the very calls K15 exists for**, reachable by a guess the
entry above validated against seven sites of one shape.

Two halves, and only the first is a fix:

- **ask the CLASS FILE first, wherever it can be read.** A `MethodType` is all-or-none, so a member
  whose result is a type VARIABLE is signature-less by construction — which means a READABLE result
  whose HEAD is a type the mapping covers is a real java collection, whatever the argument and
  receiver types happen to be. The phase's own table answers it (§4.56) and the guess never runs.
- **where the guess IS all there is, the suppression is a residue and gets its own lane** —
  `external result (unverified pass-through, no signature)`, deliberately not the
  `argument (external callee, no signature)` lane, which is about a DIFFERENT SLOT of the same call.
  Ordering the two together would let a reader take either for the other.

**Measured on liqp: 74 → 74 errors, `collection-boundary` 10 → 16, and ZERO member digests moved** —
which is the whole result: every one of liqp's six pass-through sites is a `requireNonNull`/
`ThreadLocal.get` whose class file cannot be read, so nothing was newly wrapped and six silent
suppressions became six counted ones. The readable-signature half is exercised by
`CollectionsTransformSpec`'s own class-file fixture and by nothing in the corpus yet; a library that
uses a third-party `List`→`List` utility is the one that will move an error count with it.

…and a call the phase itself rewrote is not looked at at all: ordering the seam arms first reported
`Collections.unmodifiableSet(mySet)` as an unverifiable external argument while the same run was
retargeting it — eight findings closed before they were written down, which is §4.45's
report-credibility failure exactly.

**The CONSUMER half was FRONTEND-BLOCKED and is now CLOSED — `SpoonTir` interns an external member
with its `MethodType`.** `asJava` was always as obvious as `asScala`; what was missing was any way to
know a formal is a `java.util.*`, because of the `NoType` fact above. An `asJava` helper was written
and then DELETED rather than shipped for exactly that reason: a capability nothing can reach reads as
one that works. **liqp 70 → 67**, `collection-boundary` 14 → 12, and the two ANTLR lexer arguments
this entry named are now `JavaCollections.toJava(…)`, a live view.

Five things the frontend half had to get right, each of which is a way to make the signature WORSE
than none:

- **it is rendered SCOPE-FREE.** `SpoonTir.tpe` resolves a type variable by NAME against the scopes
  the walk is inside, and fills a raw generic from the names accessible there. Both are right for a
  type written in the program and catastrophic for one read out of a class file: `List<E>.add(E)`
  would bind the callee's `E` to whatever `E` the CALLER declares — and because an external symbol
  is interned once and never clobbered, the FIRST call site in the run would decide the signature
  for the whole run. `externalSlot` therefore renders type variables, intersections and raw fills
  without consulting any scope.
- **a SLOT and a type ARGUMENT are different questions.** Spoon reconstructs a shadow type by
  REFLECTION, so `String.join`'s `Iterable<? extends CharSequence>` arrives as `Iterable<T>` —
  the interface's own formal, echoed. Refusing that would refuse most generic signatures in the JDK;
  recording `Iterable[?]` records what was read, and the HEAD is the whole of the question a
  boundary asks. A variable at the slot ITSELF still refuses: there is no head to record.
- **ALL of it or NONE of it**, `Descriptor.total`'s rule. A partially-resolvable class file is one
  the parse was lenient about, and the slots that DID resolve are not evidence either. Measured
  shape: a classpath holding `Partial.class` whose referenced `Gone.class` was removed — Spoon
  reconstructs NO declaration for `Partial`, so every member of it stays signature-less, including
  the one whose own parameter is a plain `String` (`ExternalSignatureSpec`, both directions in one
  fixture, because a run where everything is signature-less looks exactly like a regression).
- **`StandardTraversal.mapSymbols` must not walk a symbol the program does not OWN.** A class file's
  signature is not the phase's to edit; mapped, the table would say `mutable.Set` on both sides of
  the seam and the check would report zero — this entry's own failure shape, one level down. It was
  a no-op until the day external infos stopped being `NoType`, which is exactly when it would have
  started costing something silently.
- **knowing a formal's TYPE is not knowing its NULLABILITY.** `NullabilityTransform.coerceArgs`
  reads formals the same way, and with them visible it began emitting `.get` at
  `println(anAbsentValue)` — which java prints as `null` and the port would THROW on: a §4.4
  behaviour change with no compile error and no count moving. That phase excludes external callees
  BEFORE reading formals, and its count says which of the two facts is missing.

**THE SEAM WITH NO COMPILE ERROR BEHIND IT IS `java.lang.Object`, and it was reported by nothing.**
Every arm above is reachable from a type error: a `java.util.Set` formal, a shim formal, a scoped-out
declaration. A retyped collection at a class file's UNIVERSAL formal is none of them — `mutable.Map`
IS an `AnyRef`, so it conforms, the port compiles, and `objectMapper.convertValue(myMap)` /
`writeValueAsString` / `jsgen.writeObject` / `String.valueOf` / `println` hand reflective third-party
code a value java handed a `HashMap`. `toString`, `instanceof` and every serializer see something
else. That is §4.4's shape — valid output meaning something different — arriving through a retype
rather than through a statement form.

Three places had to change for it, and each was silent in its own way: `coerce` wrapped only where
the expected head was in `typeMap`, and `java.lang.Object` is not; `externalArgs` counted only
SIGNATURE-LESS callees, and this one has a signature; `CollectionBoundaryCheck.sideOf` put
`java.lang.Object` on no side at all, so the pair fell through the match. **A check that reads zero
because the pair it needs is not in its own vocabulary is the hardest kind to notice**, since every
other pair in that match is right — `Side.Universal` is now its own side for exactly that reason.

The bridge is `toJava` and it is FAITHFUL rather than a compromise, which is what licenses inserting
a wrap where nothing is broken: java's value at that slot really WAS a java collection, so the live
view restores what the callee is entitled to see with both directions still shared. What cannot be
bridged is counted — a nested element a one-level view would lie about, a shim source — and OWNED
callees are excluded, because their `Object` formal belongs to scala this port emits and the scala
collection is what it wants.

Two limits found while measuring it, both worth knowing before the next port:

- **naming `java.lang.Object` is not §4.56's forbidden name test**, and the distinction is the one to
  carry: that rule forbids concluding a type's PROVENANCE from its spelling, because a prefix is a
  fact about a string. This is an EQUALITY, against java's universal supertype, asked at a slot the
  phase already knows is a class file's — a fact about the java LANGUAGE, exactly as `typeMap`'s own
  keys are. Note the callee's owner must still be screened (`java.util.List#indexOf(Object)` is an
  external member with a universal formal whose receiver has already been retyped, so the call binds
  to scala's own `indexOf` and the class file's formal describes nothing that will be emitted).
- **the bridge inherits K2.5's open caution and made one message worse.** Where the SOURCE is a call
  the phase deliberately refused to move — `Arrays.asList(arr)`, K6.5's aliasing refusal — the node
  says `Buffer` while the value is a real `java.util.List`, so `toJava` receives a java collection
  and the error names the HELPER. Measured at exactly one liqp site, count flat: `Can't compare these
  two types` became `None of the overloaded alternatives of method toJava`. It is the same shape
  K2.5 recorded and left OPEN in K6.5's territory, and the fix belongs there — a refused-source test
  beside `coerce`'s existing `isKeySetView`, which is the same idea for the other refused source.

One case is deliberately left where it was: a `Kind.Entry` — a `Tuple2` where java had a `Map.Entry`
— at a universal formal is neither bridged (`toJava` has no overload for a pair, and a pair is not a
collection) nor counted, because `scala.Tuple2` is deliberately not on the scala side of this
check's line and putting it there would be noise. Its `toString` really does differ from java's
(`(k,v)` against `k=v`); that belongs to K5.7's story about `Map.Entry`, not to this one.

**Measured on liqp: 74 → 74 errors, every check count flat, 5 member digests over 3 units** — the
whole visible effect being two `toJava` wraps at jackson's own `writeObject` and one at a converted
assertion, plus the message above. That is the point of the entry: the seam this closes is one no
count could have shown, and the number that would have moved is a serialization test's.

**A SHIM formal belongs to a callee the PROGRAM OWNS, and reading an external one through `remap`
broke a green port.** `wrapIterableArgs` wraps toward `JavaIterable`/`JavaCollection`, and no class
file can name a `balticporter.runtime` type — so the moment external formals became readable, that
pass started reading `java.util.Collection` through `remap`, concluding the slot wanted the shim, and
wrapping. Two failures at once: `String.join(",", JavaIterable.from(xs))` hands a standalone runtime
trait to a class file asking for `java.lang.Iterable`, and — the one that cost a port — the wrap
lands on a call the phase is ABOUT TO REWRITE, so `this.items.addAll(other.items)` came out as
`this.items ++= JavaCollection.from(other.items)` where `++=` wants an `IterableOnce`. **jbump 0 → 4
errors, 8 member digests moved, every check count flat**: nothing but the compiler could see it.

Two things about how that was found and shut, both of which generalise:

- **the first regression spec written for it PASSED.** `wrapIterableArgs` short-circuits when the
  program names no `java.lang.Iterable` at all — the shim is minted on demand — so a fixture with
  only `List` and `Set` in it never reached the pass. A spec that does not name an `Iterable` is a
  spec for a code path that did not run, and it looked exactly like a spec that holds. The fixture
  now says so at the line that carries the mention.
- **that same short-circuit is a capability switch, and it stays only because it is now harmless.**
  Removing it — which reads like tidying an accidental gate — put the pass in front of every
  rewritten call in the corpus: **8 specs**, first try. The gate is not what makes the pass correct;
  `ownedSym` is.

**And the BRIDGE runs after the rewrites, not with `wrapIterableArgs`.** That pass runs first by
design — a shim-typed formal belongs to a declaration the port emits, and the wrap must be in place
before overload resolution. A `java.util.*` formal is the signature of a method this phase may be
about to RETARGET (`Collections.sort(buf)` → `JavaCollections.sort`, `items.addAll(more)` → `++=`),
so bridging first hands the rewritten call an argument its new target does not want: **8 specs**,
first try. `bridgeJavaFormals` therefore runs where the seam COUNT runs, on a call nothing else
rewrote — the same ordering rule this entry already records for the count.

**Three calls keep java formals, and "not owned" is not the test.** A `super.putAll(m)` inside a
class extending a mapped collection is an unowned callee with a `java.util.Map` formal — and the
phase REFUSED to rewrite it (the blanket `super` guard, every scala form being an E040). Bridging its
argument leaves the same uncompilable call with a wrapper inside it, so the error stops naming the
member and starts naming the helper: M6's refusal made unfindable, K6.5's failure under a new name.
The test is `excluded(callee) || externalCallee(callee) || the RECEIVER resolves through a held-back
declaration` — and `externalCallee` already excludes a callee whose OWNER the phase maps.

**The SCOPE seam's consumer direction closed with it, and its classification text was wrong.**
`Issue.ScopedOut` said "NO WRAP CAN CLOSE IT — a `mutable.Buffer` is not a `java.util.List`", which
is true of the TYPE and false of the VALUE. A retyped value at a held-back java formal now goes
through `toJava`; what remains ScopedOut is the PRODUCER direction, where the held-back declaration
hands BACK a `java.util.List` and nothing at a call site can change what a declaration returns.

**What is left is a count, and it is smaller and better classified.** Where no signature can be read
the seam is still `Issue.ExternalCallee` and still says "cannot verify" — that arm may never go to
zero by being deleted, which is CLAUDE.md §1(b)'s rule. Where the signature IS readable the row moved
to `CollectionBoundaryCheck`, which holds both types and can say more: liqp's
`Stream.concat(Stream, Stream)` is now `UntranslatedFamily` (K6's deliberate refusal) instead of
"cannot verify". One caution that arrived with it — **a MAPPED type at an EXTERNAL formal is not
`MappedTypeSurvived`.** That issue reads "§1(a) engine bug: no occurrence of this type should have
survived `transformType`", and a class file's `java.util.Map` never had the chance to move. It
became reachable the moment external formals became visible, and it is classified `ExternalCallee`.

*Fix kind: (a) engine for the producer — DONE. (a) FRONTEND for the consumer — DONE. What remains
is a count for class files the parse cannot resolve, which no engine change can reach. The
generalisation is CLAUDE.md's: every retyping phase owes a boundary count at EXTERNAL callees, not
only at JDK ones.*

---

**And a FIELD is the one member kind with no call node at all.** Everything above is keyed on
`Tree.Apply`; a field read is a `Tree.Select`, so an ANTLR context's `public List<ParseTree>
children` was the same seam one node kind along and invisible to every check — the class file says
`java.util.List`, the position-blind retyping moved the SELECT's node type to `Buffer`, and both
`CollectionBoundaryCheck` and `JdkSurfaceCheck` therefore read a scala collection on both sides.
`jdk-surface` reported ZERO on it while scalac read `value foreach is not a member of
java.util.List`.

Two halves, and the second is what makes the arm SOUND rather than merely present:

- **the frontend interns an external FIELD with its declared type** (`externalFieldType`), by
  exactly `externalSignature`'s rules — scope-free through `externalSlot`, so a field typed at the
  declaring class's own type variable (`Node<N>.parent`) cannot bind to whatever `N` the CALLER
  declares, and only for a SHADOW declaration, since a field the program declares gets its real type
  from `fieldDef`;
- **the arm reads the CLASS FILE and not the node, and here that is not a preference — reading the
  node is UNSOUND.** `StandardTraversal.mapTerm` visits an `Apply`'s `fun` as a term of its own, so
  this arm sees every method SELECTION too, and wrapping one would put a `fromJava(…)` where the
  callee belongs — turning every rewritten call in the program into a call on a wrap, silently. The
  class file separates the two exactly: a method's `info` is a `MethodType` (or `NoType` where the
  file could not be read), and only a FIELD carries a plain type, which is a fact no method can
  have. Where the file cannot be read there is no `info` and the arm does nothing, which is this
  entry's own answer everywhere else.

Measured on liqp: **7 -> 6**, one site (`NodeVisitor#visitSimple_tag`), 2 member digests, every
check count flat — and on libGDX core **0 errors, 0 member digests, every count flat**, which is the
number a frontend change owes: that port has no external field at a mapped collection type, so the
interning is inert there.

**…and the guess's ARGUMENT half was asking the wrong question, which cost one E134 for the life of
the port.** "The result type occurs on the input side" was implemented as `occursIn` for the
RECEIVER and as `_.tpe == want` for the ARGUMENTS — equality, so it saw `Objects.requireNonNull(m)`
and nothing one type argument in. `mapper.convertValue(value, MAP_TYPE_REF)` pins its result `T`
through the TYPE ARGUMENT of an argument, so the value's type comes from what the CALLER handed in;
wrapped, it emitted `fromJava(aScalaMap)`, an E134 naming the HELPER rather than the boundary, which
is the shape this entry already records as the worst one this seam produces. Occurrence subsumes
equality, so the case the guess was written for is unchanged.

Measured on liqp: **5 -> 4**, `collection-boundary` **13 -> 14** — the wrap became a COUNTED
suppression in the unverified-pass-through lane, which is what the two numbers moving in opposite
directions means here — and 4 member digests.


### K16. A `CollectionsTransform` SCOPE is not a way to opt out of its residue — 27 → 47 narrow, 27 → 51 off. DO NOT RETRY

**The move that looks obviously right, measured in both directions, and worse in both.** A port
whose remaining wall is entirely this phase's boundaries reaches for the phase's own §1(b) knob:
name the declarations whose java shape is load-bearing and let them keep it. The reference hand-port
recommends exactly this from the sources — *"recommend `Everywhere(Set.empty)` off, or a very narrow
`Only`; the cheapest first answer is not to open [the third-party seam]"* — and it is wrong. Three
configurations, one library, one commit apart:

| `scope` | main errors | test errors |
|---|---|---|
| none (`Everywhere(Set.empty)`) | **27** | 49 |
| `except` = 18 named types, every one of them a declaration at a real seam | **47** | 49 |
| `except = ["<the whole governed package>"]` — the phase a total no-op | **51** | 42 |

**Anchored at `b95480b5`**, which is the honest way to quote three numbers from one afternoon: they
were measured against that engine, one after another, with nothing else moving. The TEST column has
since been overtaken (T14, K5's anonymous receiver and T15 took it to 23, and the cascade injection
to 11) and the MAIN column has not moved at all. What the entry claims is the SHAPE — that the two
scoped columns are both worse than the unscoped one, by a lot — and re-deriving the exact figures on
a later engine is a day's work that would not change the conclusion. If a future change makes the
scope look plausible again, it is cheaper to re-measure the three configurations than to trust these
three integers.

So the phase being ON is worth **24 main errors** to a library that is `java.util.Map<String,Object>`
in and `Object` out throughout. Two facts explain it and both generalise:

- **A SCOPE SPLITS A CALL GRAPH, and a library is not stratified.** Every error the narrow scope
  removed at a scoped-out declaration it re-created at that declaration's CALLERS, which are
  retyped: `Insertions.getBlockNames()` stopped being a `mutable.Set` and three callers in two other
  files stopped compiling; `LiquidSupport.toLiquid()` stopped being a `mutable.Map` and five did.
  The direction reverses but the count does not fall, because the boundary did not go away — it
  MOVED, from one seam the phase can see to several it cannot. A scope pays only where the scoped
  set is a genuine ISLAND (a bridge class nothing else consumes), and "this declaration sits at a
  seam" is not evidence that it is one.
- **A scoped-out body LOSES the phase's REWRITES, not only its retyping** — which is correct
  (§4.56: a type the phase does not retype is still whatever it was) and is not what the knob's
  name suggests. The enhanced-for is the expensive one: `for (x : javaList)` in a scoped-out body
  emits `for (x <- javaList)` against a real `java.util.List`, which has no `foreach`, and that one
  shape DOMINATES the narrow scope's new errors — it is what took the scoped-out `NodeVisitor` from
  2 errors to 22, and it also appeared inside the scoped-out `LValue` and `SPIHelper` (K9's
  territory, arriving through the scope). **A port that scopes out any declaration iterating a JDK
  collection has to accept K9's wall inside it**, and nothing says so at configuration time.

**And the seams a scope opens are NOT COUNTED, which is the half CLAUDE.md §1 requires and the
engine does not yet have.** `collection-boundary` read **12 on all three runs** while the error count
moved 27 → 47 → 51, and every other check count was flat too. §1 is explicit — "a scope that silently
produces an uncompilable or wrongly-typed boundary is worse than no scope" — and the reason it reads
zero is the reason K15 already records: the retyping is position-blind, so at a scope seam BOTH sides
of the slot carry the moved type and a check comparing node types sees no disagreement. K15 closed
that for an EXTERNAL callee by reading the class file; a scope seam's far side is a declaration this
same run emitted, so the answer is available and simply not asked. **Until it is, the only instrument
that sees a scope is the compiler**, which means a scope must be measured on the whole port and never
reasoned about.

*Fix kind: (b) for the port — DO NOT RETRY on a library whose collection types are its currency;
scope only a genuine island. (a) for the engine — a scope's own seams need a count, from the
DECLARATION on each side, and `collection-boundary` reading flat through a 24-error move is what
says so.*

---

### K17. A java CONVERSION emitted as a scala CAST asserts where java CONVERTED — **34 test failures, 0 compile errors. OPEN**

Two faces, found in the same run and the same defect twice: java's expression grammar performs a
CONVERSION at a position where the emitter renders an `asInstanceOf`. The cast has the right static
type — that is why nothing complains — and at run time it asserts a fact that is false, because the
conversion never happened.

Both were invisible until C12 closed. They sit behind `Template`'s constructor, so every test that
would have reached them died one frame earlier with C12's `NullPointerException`; they are not
regressions and no count moved when they appeared (`errors 0 -> 0`, every check flat).

**FACE 1 — a LAMBDA at an external functional-interface slot. 27 failures.**

```java
Optional.ofNullable(location).orElseGet(() -> Paths.get(".").toAbsolutePath())
```
```scala
java.util.Optional.ofNullable(location)
  .orElseGet((() => java.nio.file.Paths.get(".").toAbsolutePath())
               .asInstanceOf[java.util.function.Supplier[? <: java.nio.file.Path]])
```

Scala 3 SAM-converts a function literal to a java functional interface **when the EXPECTED type is
that interface**. Written as a cast, the literal is elaborated first — to a `scala.Function0` — and
the cast then asserts that a `Function0` is a `Supplier`, which it is not:

```
java.lang.ClassCastException: class ssg.liquid.TemplateParser$$Lambda cannot be cast to
class java.util.function.Supplier
```

The fix is to make the expected type do the work — emit the literal into the slot and let the
conversion happen, or `new Supplier[…] { def get() = … }` where no expected type is available — and
never to cast a literal into an interface. Note the seam is at an EXTERNAL callee, so the formal
comes from a class file and is exactly readable (K15's rule); this is not a case where the engine
cannot know.

**FACE 2 — a conditional `? :` over MIXED BOXED NUMERICS. 7 failures.**

```java
return str.matches("\\d+") ? Long.valueOf(str) : Double.valueOf(str);   // JLS 15.25
```
```scala
return (if (str.matches("\\d+")) java.lang.Long.valueOf(str)
        else java.lang.Double.valueOf(str)).asInstanceOf[java.lang.Double]
```

JLS 15.25 gives a conditional whose two operands are `Long` and `Double` **binary numeric
promotion**: both branches are UNBOXED, promoted to `double`, and the result re-boxed — the
expression's type really is `Double` and the `Long` branch really does become one. Scala's `if` has
no such rule; its type is the LUB (`java.lang.Number`), and the branch value stays a `Long`.

The emitter read java's static type correctly and wrote it as a cast, which is the whole error:
`java.lang.Long cannot be cast to class java.lang.Double`. A cast is not a conversion. The faithful
emission is the promotion java performed — `java.lang.Double.valueOf(x.doubleValue())` on the
branch, at the promoted type — and the general rule is that **wherever the frontend records a java
expression type that differs from the scala expression's own type BY A CONVERSION, the emitter owes
the conversion and not an assertion.**

**Neither is visible to a compile, to any check, or to a member digest** — both emit valid Scala with
the right static type. The gate that saw them is the one §3 names.

*Fix kind: (a) engine, OPEN, both faces. Found by RUNNING liqp's suite after C12 closed.*

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

### P5. The engine emits `.scala` AND NOTHING ELSE — a `META-INF/services` file is a deliverable no phase carries, and a rename moves both its NAME and its CONTENTS — **OPEN; the counting is closed, the pipeline is not**

A `ServiceLoader.load(X.class)` reads `META-INF/services/<X's FQN>`, whose lines are provider class
names. Nothing in this pipeline emits, copies or renames that file, and the failure is CT7's family
one step short of CT7 itself: the providers are constructed reflectively from OUTSIDE the program,
so the closure sees no instantiation, concludes correctly and uselessly that nothing has to be
fixed, and **there is no compile error, no check count and no finding**. With the file absent the
loader finds zero providers and every registration the library performs at class-initialisation
silently no-ops. Found by the liqp census: `SPIHelper.findProviders()` is reached from a
`static { }` block in `Template`, i.e. effectively from the whole suite, and from
`LiquidSupport`'s type-referencing setup, i.e. from every render of a POJO.

Two halves, and only the first is closed:

- **the DEPENDENCE is now counted.** `PortabilityCheck` has a `java.util.ServiceLoader` rule, whose
  `why` names both reasons — reflective provider lookup is JVM-only, and the resource it reads is
  not emitted. That converts "silent" into a number an agent can act on, which is `CLAUDE.md` §3's
  bar and is all a rule can do;
- **the ARTEFACT is still hand-written.** The engine has no notion of a resource input at all: no
  config key names one, no lane copies one. The milestone-1 answer is a file in the port's
  hand-written `src/main/resources` (§5.5 — `src/` is the hand-written half) plus a `--resource-dir`
  on the lane's runner, which is what liqp ships.

**Do not underestimate the second half by looking at the first.** A carried services file is not a
copy: under a package rename the file's NAME is the renamed interface FQN and its CONTENTS are
renamed provider FQNs, so it is §4.56's "an artifact that joins POLICY to OBSERVED code carries BOTH
names" in a format `PackageRenameTransform` has never seen — and it must translate through
`PackageRenameTransform.renamed`, never a hand-written `startsWith`. That, plus a `resourceRoot`
the config would have to grow, is why this is not the one-line (a) a census can mistake it for: the
COUNTING is (a) and shipped, the resource lane is (a)-mechanism with a (b) input and is unbuilt.

*Fix kind: (a) engine for the rule — SHIPPED, and no corpus port referenced `ServiceLoader`, so
every lane is unchanged. (a) engine for the artefact lane — OPEN. Port-side workaround: a
hand-written `src/main/resources` tree, which is what exists today.*

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

**…AND JAVA HAS A SECOND WIDENING AT THE SAME MEMBER, which the numeric one hid for four ports.**
`assertEquals(Object, Object)` widened its REFERENCE operands exactly as `assertEquals(long, long)`
widened its numeric ones — one overload, two conversions — and MUnit's `Compare[A, B]` needs the two
types to relate whichever kind they are. Two invariant `java.util.List`s at different element types
do not: `Can't compare these two types: java.util.List[Object] / java.util.List[String]`, liqp's
`RenderSettingsTest`, the last error in that port's test source set. Five libraries went past it
because the numeric half is far commoner and because a suite comparing two DIFFERENTLY-PARAMETERISED
collections is unusual.

The fix is the same rule at the other overload, and it is written as the call's TYPE ARGUMENTS
(`assertEquals[java.lang.Object, java.lang.Object](a, e)`) rather than as a cast per operand: one
construct instead of two, exactly what java's signature said, and the operands' own emitted text is
left alone. **MUnit's constraint is a STRICTLY STRONGER check than java's**, so it is KEPT wherever
it is a check and java's widening is written down only where it is not — the two static types are
the same, and that type is not a root.

**The trap is the ROOT, and it reads as the opposite of what it is.** "One side is
`java.lang.Object`, so the pair already relates, so leave it alone" is wrong twice: `Compare[A,
Object]` resolves for every `A`, so MUnit's constraint at a root operand is ALREADY VACUOUS and the
widening costs no check at all — and a root is the one answer the TIR's own types cannot be trusted
on. An earlier phase's boundary bridge types its wrap as the FORMAL it was inserted for, and java's
`assertEquals(Object, Object)` formal IS `java.lang.Object`: at the liqp site BOTH operands are
`JavaCollections.toJava(…)` nodes carrying `java.lang.Object`, while the text they emit is
`java.util.List[Object]` and `java.util.List[String]`. Read as "same type, so MUnit can compare
them", that pair declines the widening and does not compile. Read as "a root, so there was never a
check here", it takes it. Note the phase concludes nothing about another phase's rewrite — it reads
its own operand types and treats a root as the absence of information it is (§4.56).

Two guards, both refusals rather than approximations: `NoType` on either side is "the frontend could
not say", and a PRIMITIVE on either side belongs to the numeric promotion above — widening a boxed
pair to `Object` would silently change the comparison, scala's `==` on two boxed numbers being
NUMERIC (`BoxesRunTime.equals`) where java's `Integer.equals(Long)` is `false`. That is a §4.4
divergence and this rewrite must not open it.

Measured: **liqp 3 -> 2** (its test source set 2 -> 1), and across the four green test lanes it
moves emitted TEXT and no outcome — the widening is behaviour-preserving by construction, because
MUnit compares with `==` at every instantiation.

*Fix kind: (a). Closed in `TestFrameworkTransform`, both halves.*

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
miss this project exists to prevent — and since X6 its residue is a COUNTED one, `PortabilityCheck`
having gained the `org.hamcrest.` rule it never had.

**Two claims here were false and are corrected.** JUnit 5 reaches the `org.junit.` prefix rule only
through the *term* reference from an assertion call — annotation types are not in the xref at all,
since `Xref` walks trees and not `Symbol.annotations`, so a JUnit-5 suite whose assertions came from
elsewhere is invisible. And **TestNG matches no rule whatsoever**; `PortabilityCheck.check` returns
`Nil` for it. A test now pins that `Nil` so it fails the day a rule is added.

*Fix kind: (a) for the residue, and it is reported rather than silent. Lifecycle: BUILT.*

### X6. The JUnit surface a phase HANDLES is a VOCABULARY and a SCOPE, and both were narrower than JUnit — **CLOSED, four faces, all (a)**

Every one was found by censusing a suite the engine had not yet been pointed at (liqp, 639 `@Test`),
and none of them is visible to a compile: each leaves valid Scala that references JUnit, so the port
still builds and runs *on the JVM with junit on the classpath* — which is the one platform the
conversion exists to leave.

- **The assertion statics are THREE FQNs, not one.** `AssertClass` was the single string
  `org.junit.Assert`, so `junit.framework.Assert` and `junit.framework.TestCase` — JUnit 3's copy of
  the same members, inherited, with the same argument order and the same minimal arities — were
  neither rewritten nor reported. 31 call sites in five ordinary JUnit-4 classes, reached through
  `import static junit.framework.TestCase.assertEquals`. Note what that is NOT: none of the five
  subclasses `TestCase`, so `survey`'s JUnit-3 PARENT scan correctly says nothing and the CALLS are
  the only trace. Which of the two a frontend reports as the receiver is not the phase's fact to
  know (Spoon reports the executable's declaring type; a frontend reporting the qualifier would say
  the other), so both are in the set. **Not a parameter**: an empty default would silently stop
  converting `org.junit.Assert` too, and a per-library list of JUnit's own class names is policy
  nobody can get right twice.
- **`assertThrows` had no mapping**, though MUnit's counterpart is the `intercept[E] { … }` the same
  phase already builds for `@Test(expected = …)` — same assertion, same returned throwable. Two
  overloads are REFUSED rather than approximated, and the refusal now carries its own reason:
  `intercept[T](body)` has no clue slot, so junit's leading `String message` has nowhere to go; and
  the runnable must be a no-argument LAMBDA, because `intercept[E] { r }` EVALUATES a
  `ThrowingRunnable` value rather than running it — the assertion would then test whether
  CONSTRUCTING it threw, passing while checking nothing.
- **The rewrite was SUITE-SCOPED, so a test HELPER was never visited.** `convert` returned a class
  unchanged when it declared no `@Test`, and the only walk reaching `transformApply` ran inside that
  `else`. A class with no `@Test` is not a non-test; it is a helper — and a helper is where a
  library centralises its assertions. The scoping had a stated reason (rewriting program-wide once
  produced `Not found: assertTrue` in helper classes, whose members came from the suite's base
  class) and that reason was REMOVED by X3: assertions have been fully-qualified `munit.Assertions`
  object calls ever since, which resolve from every scope. **A gate whose justification is closed
  elsewhere is a leftover, and nothing reports one.** The walk now splits — assertions over every
  unit, the CONVERSION (parent, registrations, lifecycle inlining) still keyed on `@Test`, because
  that is what a suite is.
- **…and running that walk per CONVERTED CLASS double-counted.** `StandardTraversal.mapClassDef`
  descends into nested classes, so an outer suite re-walked its already-converted nested one.
  Idempotent for the rewrites — a rewritten call is no longer a `Select` on `Assert` — and NOT for
  the findings: an unmapped member inside a nested suite was reported twice, and the
  "UNTRANSLATED test-framework constructs" headline over-counted by exactly that. One walk per unit.

**And the RESIDUE the phase deliberately leaves is now counted.** `PortabilityCheck` had rules for
`org.junit.` and `junit.framework.` and none for `org.hamcrest.` — the vocabulary reached *through*
them, and the one that arrives TRANSITIVELY with junit rather than as a declared dependency. So a
suite could be entirely hamcrest (liqp: 1535 references, 767 `assertThat`) and every portability lane
read zero, while the phase printed the number to stdout and nothing recorded it. A decision not to
translate is only defensible if what it leaves behind is a NUMBER (`CLAUDE.md` §3).

*Fix kind: (a) engine, every face. No corpus port uses `junit.framework`, `assertThrows`, hamcrest or
an assertion-carrying helper, so all lanes are 0 members changed with every check count identical —
which is the point: none of these was measurable until a library that used them was pointed at.*

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

### M5.6b A dead server turns `sbt -batch` into a GREEN-LOOKING NON-RESULT — exit 0, no tests run

The other end of M5.6, and the reason a wedge is worth diagnosing rather than waiting out.
`sbt -batch` is a CLIENT (M5.6 says so in passing; this is what it costs): when the server it
attaches to dies or is killed, the batch invocation prints

```
[error] sbt server disconnected
```

**and exits 0**, having executed nothing. Measured here: a `sbt -batch "testOnly *"` sat at 0% CPU
for 26 minutes queued behind this worktree's own idle server, and the moment that server was killed
the batch run "completed" — 36 bytes of output, exit 0, zero suites. A caller that reads the exit
code, or a lane that greps for a failure marker, records a full green test run that never happened.

So gate on OUTPUT, never on the status, exactly as the measure lanes already do for the migration
itself (`gdx-measure` refuses to measure unless the run printed `wrote N Scala files`, because
piping into `grep` discarded the status). For a test invocation the marker is `[info] Passed: Total
…` — one per project, and an invocation with none of them ran nothing whatever the shell says.

Same failure shape as §5.1's skipped-test lane one level up: a run that did not happen and a run
that passed are indistinguishable unless something insists on seeing the evidence.

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

### M9. A lane's ERROR COUNT was the one measurement nothing compared — 0 -> 3 exited 0

CLOSED. Every number a lane prints is diffed against a committed baseline: `findings.tsv` and
`counts.tsv` per check, `members.tsv` per emitted member, `tests.tsv` per test outcome. The
COMPILE-ERROR TOTAL — the number CLAUDE.md §3 calls the gate and every commit subject quotes — was
printed to stdout and thrown away.

That is not a cosmetic gap, because a non-zero count is a LEGITIMATE state here: gltf sits at 3 and
noise4j at 2 for reasons both ports have written down. So no lane could distinguish "3, as always"
from "3, as of this commit", and `measure-all` walked through a real regression reporting success —
measured on the screens lane, whose 0 became 3 when K6.5's third case changed the shape of an
external vararg call and a hand-written shim's five formals stopped matching. Nothing in the run
said so; the regression was found by reading the capture.

The fix is `baseline/expected-errors`, one line per lane, and three properties that are not
optional:

- **the number is WRITTEN BY THE RUN**, into `run-latest/errors-count`, and promoted by
  `just baseline-accept`. A baseline a human types is the one baseline that can disagree with the
  run that produced it;
- **fewer errors fails the lane too.** A lane that silently absorbed improvement would let a fix and
  a regression cancel inside one run and report nothing, and a floor that only ever moves up is not
  a baseline. The message names `baseline-accept`, so acknowledging is one command;
- **the verdict crosses to `headline` as a MARKER FILE, never a shell variable.** Exiting at the
  guard would take the correlation with it — the part of the run that says which member and which
  java line the new errors came from — so the decision and the exit are in different functions. The
  first cut used a global, which is set in a subshell and reaches nobody; `just lane-selfcheck`
  catches it in the one shape a lane never uses, and both directions plus the stale-marker case are
  cases there now.

*Fix kind: (a) engine — measurement machinery, no library involved.*

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

### D5. A REPLAY may not widen a `private` member the run does not EMIT — **CLOSED; gltf 7 -> 3 errors, `omissions` 3 -> 12**

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

**CLOSED by reading the published `vis=`.** `CtorFunnel.Plans.reachablePrivate` asks the SURFACE
instead of the symbol table, and the three answers are the three cases:

| the owner is | the answer |
|---|---|
| a class this run EMITS | the widening is real — the old `classOfSym(...).isDefined` test, unchanged |
| a base that published the member NOT `private` | reachable; no widening needed |
| a base that published it `private`, or published nothing | REFUSE the replay, record a non-fatal `Surface.Gap` naming the base |

The refused `super(args)` is then dropped, `OmissionCheck.droppedSuperArgs` counts it, and the port
compiles with a named divergence instead of failing (M6/C3). Measured on gdx-gltf: **errors 7 -> 3**
— the 4 `copyNodes` errors are gone and what is left is the 2 C3 wall classes plus D7's `MeshLoader`
— with **1** base-surface gap, `[base: libgdx-core]`, classified `§1(a) ENGINE, in the BASE`.

**TWO THINGS THE FIX HAD TO REPAIR BEFORE THE LOOKUP WORKED AT ALL, both silent:**

- **`Surface.memberShape` could not find a METHOD row.** It looked a member up by
  `Symbol.fullName` — `owner#name` — while a published member row is keyed the way the SOURCE MAP
  keys one, `owner#name(params)`, because `Symbol.descriptor` is deliberately never folded into the
  name (§8.1). So it matched every FIELD row and no method row, and had answered `Unknown` for
  exactly the questions D5 needs it for since the day it was written. Fixed by grouping the rows by
  `owner#name` — the OVERLOAD SET — rather than re-spelling the emitter's parameter grammar in a
  second place; where the overloads publish DIFFERENT shapes the answer is `Unknown`, never one of
  them picked.

  **…and the overload set ALONE is still wrong — 272 false reports on ONE dependent.** §4.55 exists
  because java lets `FileHandle.file` be a FIELD and `file()` a METHOD, and the field is then renamed
  `file$field` — so those two rows DISAGREE by construction, and every renamed field in every base
  answered `Unknown` about a row sitting right there. A field's key is exactly `owner#name` and a
  nilary method's still carries `()`, so the two are told apart by WHAT THE SYMBOL IS: read from the
  DEFINITION (`Tree.DefDef` vs `Tree.ValDef`), never from `Symbol.descriptor`, which is also `None`
  for an unresolved external and would conflate "a field" with "we do not know". Found by a lane, not
  by a compile: the count is a check row and nothing else moved.
- **Every check and decision recorder built its own `CtorFunnel.Plans` with NO surface.**
  `OmissionCheck` (four lanes), `PortRun.recordDroppedSuperArgs` and `PortRun.recordCtorFunnel` each
  did `CtorFunnel.Plans(program)`, which is a `TrivialSurface` — everything is mine. So the check
  answered over a DIFFERENT fixpoint from the one the emitter used: gdx-gltf's `omissions` sat at
  **3** while the emitter had lowered nine constructors to a bare `this()`, and the port map's own
  decisions claimed those `super(args)` were expressed. Threading the run's view took gltf's
  `omissions` **3 -> 12** with the emitted CODE unchanged for seven of them — only the porter notes
  appeared, which is the shadow-becomes-a-claim failure `OmissionCheck` warns about, arriving in the
  one place nothing was watching.

The GAP is scoped to classes this run EMITS (D2). A dependent's `Plans` decides about its base's
classes too, so two of gltf's three initial refusals were about `Button`/`ScaleInfluencer` — text the
base wrote and this run does not touch. The refusal still stands there and costs nothing.

Note the asymmetry that makes this invisible from the base: the base cannot widen speculatively (it
has no way to know a future dependent will replay), and the dependent cannot widen at all. Only the
dependent can SEE the problem, and only the base could fix it — which is why the honest engine
answer is to refuse rather than to shift the decision, and why the gap's `fix` names the BASE's
repository.

**Do NOT retry a blanket refusal of cross-class private widening.** libGDX core makes 22 sound
`WidenedVisibility` decisions of its own, all within one module; refusing them regresses the base to
fix the dependent (`PROGRESS.md` §8.5). The guard is a SCOPE, and `BaseSurfaceSpec` pins both
directions.

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

### D10. `governs` IS A NAMESPACE, NOT A SET OF DECLARATIONS — and a TEST SOURCE SET is always inside its base's. **3 fatal findings on a key about the module's OWN member**

`CLAUDE.md` §1.5 says *no key a DEPENDENT declares may edit what a base EMITS*, and the screen
implemented it as *is the subject inside the base's `governs` claim*. Those are not the same
question, and every dependent whose sources share the base's package is where they come apart:

```
src/main/java/liqp/nodes/…   →  base,      governs = ["liqp"]
src/test/java/liqp/nodes/…   →  dependent, same package by construction
```

So a `dropMethods` key naming `liqp.nodes.ComparingExpressionNodeTest#…` — the dependent's OWN test
class, which the base neither declares nor emits nor has ever seen — read as an `ExtraDrop` against
the base, fatal, three findings for one three-key entry. **A rule with no way to comply with it**:
that module's every declaration is inside the claimed namespace, so no drop it could ever write is
admissible. No corpus port had declared a test-set drop before, which is why it survived six
libraries.

The base's published PORT MAP answers §1.5's actual question exactly — it is the list of what the
base EMITTED — so the screen asks it, and falls back to the namespace claim only where there is no
map. That fallback is not a loophole: an unpublished base is already reported (`BaseMapMissing`), and
the namespace is then the only answer that exists.

Note which direction stayed strict. `MissingDrop` is unchanged: a base drop absent in a dependent is
a disagreement about the base's own policy and needs no map to see. And an `ExtraDrop` against a type
the base really does emit is still fatal, which is the case the mechanism was built for
(`ManifestSpec` pins all three: the base's map without the subject, with it, and no map at all).

*Fix kind: (a) engine — the screen read a claim where the rule said EMITS. Measured on liqp's test
port: `manifest` 3 -> 0, with `manifest` 0 unchanged on every other port and no member digest moved
by the change.*

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

### F4. A translated CATCH swallows a translated JUMP — `boundary.Break` is a `RuntimeException`

The third face of the same lowering, and the only one that is invisible to `break_residue` too: the
jump WAS translated, correctly, and then eaten. `scala.util.boundary.Break[T] extends
RuntimeException(null, null, false, false)` — read off `scala/util/boundary.scala` in the 3.8.x
library, and deliberately NOT a `ControlThrowable`, so `NonFatal` matches it as well. So a
`boundary.break` standing inside a `try` whose boundary is outside it is caught by any arm broad
enough to match a `RuntimeException`: the loop runs on, and the handler's body runs for a condition
java never had. Java's own `break` is a jump and no handler can see one, at any breadth.

**Do not expect dotty's optimiser to save it.** `DropBreaks` rewrites a same-method break into a
labelled jump, which would be immune — but `DropBreaks.prepareForTry` shadows every enclosing label
("Need to suppress labeled returns if there is an intervening try"), so a break under a `try` is
always the exception form. The swallow is deterministic, not a race with an optimisation.

Measured in the reference ecosystem before it was measured here: ssg `ed8ce078`, a hand-written
port whose date parser exits early inside the `catch (Exception)` that exists to ignore a FAILED
parse — the whole `date` filter silently stopped parsing, with a green compile, no moved count and
no failing check.

**And the engine's exposure is NARROWER than that hand port's, which is the part worth knowing.**
ssg's witness is a java `return`, and a `return` is not this defect for this emitter: scala's
method-level `return` is a jump, so the same source emits
`liqp-core/…/filters/date/BasicDateParser.scala:30` as a plain `return` inside the `try` and is
immune — as is a `return` in a LAMBDA, which the emitter lowers to a nested `def` (F-family, the
`Tree.Lambda` case) rather than to a boundary. What the engine can produce is the `break`/`continue`
face, and only that. So do not read a hand port's occurrence as a port's: which java constructs a
`boundary` gets interposed around is an EMITTER fact, and the same defect class has a different
footprint in each.

What shipped: a re-throw arm ahead of the java arms (`case brkThru$: scala.util.boundary.Break[?]
=> throw brkThru$`), interposed only where a jump really crosses the catch — which the emitter's own
boundary state answers exactly, since a jump renders as `boundary.break` precisely when its target
is in scope at that `try`. `finally` is untouched (a finalizer is not a handler; both languages run
it and let the jump through) and a narrow catch is left alone. The counted lane is `break-catch`,
and it finds the crossings from the TREES rather than reading the emitter's answer back, so the two
disagree exactly when the emitter's state missed a shape the walk can see.

**Where the corpus's ONE real site is, and why every count says zero.** `break-catch` reads 0 on all
eleven lanes and `members.tsv` moved by 0 on all fourteen ports — and the corpus is NOT free of the
shape. `com.badlogic.gdx.utils.Json#writeFields` is a textbook instance: a `for` over the field
names, a `try` whose body carries four `continue`s (the default-value skips), and
`catch (Exception runtimeEx)` at `Json.java:343`. Unrepaired, that port would write every field
equal to its default instead of skipping it, and wrap the jump in a `SerializationException` for a
condition java never had.

It moves nothing because **libGDX DROPS `Json`** (`Substitutions.dropTypes`, replaced by an injected
shim), so the unit is modelled and never written: no file on disk changes, `members.tsv` cannot see
it, and `break-catch` cannot either — the check runs over `checkedUnits`, which excludes dropped
types, and that scoping is correct (a finding about code the run does not emit describes nothing).
The single trace it leaves anywhere is one member digest in `port-map.tsv`, which digests the
emit ORDER rather than the written files. That is the §4.56 blind spot again, one phase along: *a
defect inside a dropped type is invisible to every count this project has*, and the only reason this
one was seen at all is that a port map row moved by one digest.

liqp — 19 broad catches over 15 files, the library this was expected to bite — really does read
zero, for the reason above: its early exits inside a guarded block are `return`s, and its
`break`/`continue`s are not inside one (`blocks/For.scala:216` is the worst-case method, and its
`boundary.break`s at :95/:125/:129/:134 all sit outside the `try`).

Two things to take from that. **A defect class is worth closing at a corpus count of zero** — the
count is a fact about the libraries measured so far and about what they happen to DROP, the shape is
a fact about the language, and this one is invisible to every gate the project has while the
reference ecosystem shipped it to production. And **the evidence for a repair like this is a SPEC,
not a port**: `BreakInCatchSpec` asserts the emitted shape for five crossing shapes and five
must-not-touch ones and EXECUTES three in the test JVM — one of which fails if the naive shape ever
stops swallowing the jump, so the day scala changes `Break`'s parent, that test says so.

*Fix kind: (a). If a port shows a `break-catch` finding, the fix is `TirEmitter.crossesCatch`, never
the port's manifest.*

### F5. TRY-WITH-RESOURCES was dropped WHOLE — the frontend modelled it and the emitter never printed it

CLOSED. The worst of this family, because nothing about it was half-done: `Tree.Try.resources` was
populated correctly by the frontend, carried through every phase, and rendered by `TirPrinter` in the
TIR's own debug view — so every diagnostic said the resources were there. `TirEmitter.tryStr`
computed their text into a local `r` and then **never interpolated it**, behind a trailing comment
(`// resources: r prepended when the backend lowers auto-close`) describing an intended step as if it
were a plan rather than a gap. The resource `val`, every `close()`, the reverse ordering and the
suppression were absent from the output entirely.

**Two failure modes, and only one of them is loud.** A resource REFERENCED by name inside its own
body is an unbound identifier and fails to compile — self-correcting. A resource opened for its side
effect alone — `try (var lock = acquire()) { … }`, an idiomatic shape — compiles perfectly with the
lock never acquired and never released. No error, no moved count, no failing check, nothing in the
emitted file to say a java statement had ever been there. That is §3's defect class with a whole
STATEMENT FORM inside it rather than a corner of one.

**What shipped is JLS 14.20.3.1's own lowering, emitted INLINE as statements** — one nesting per
resource, wrapping the BODY only, since 14.20.3.2 defines the extended form as the basic one nested
inside `try … Catches Finally` and java therefore closes before this try's own `catch` runs:

```
{ val r = init
  var primary$n: Throwable = null
  try <rest>
  catch { case brkThru$: scala.util.boundary.Break[?] => throw brkThru$
          case t$n: Throwable => { primary$n = t$n; throw t$n } }
  finally if r != null then {
    if primary$n != null then { try r.close() catch { case s$n: Throwable => primary$n.addSuppressed(s$n) } }
    else r.close() } }
```

**Statements, and NOT `Using(r) { r => … }` or a runtime `withResource` helper — that is the part to
not re-litigate.** Both combinator forms put the body inside a LAMBDA, and this emitter emits
explicit `return`, while `break`/`continue` render as `boundary.break` bound to a label opened
OUTSIDE the try. A java jump out of a try-with-resources is legal and must still close
(14.20.3.1), and neither construct survives being moved into a function body unchanged. The
statement form needs no rewriting of the body at all.

Four contract properties, reproduced rather than approximated, and all four executed in
`TryResourceBehaviourSpec`: reverse declaration order (it falls out of the nesting); every `close()`
attempted even when an earlier one threw (an inner failure propagates and becomes the outer level's
`primary`); suppression rather than replacement; and closed on ANY completion including a jump —
a `boundary.Break` is a `RuntimeException`, so the catch-all sees it and RE-THROWS it, which is F4's
rule met by construction and why this arm needs no `BreakGuard` beside it.

**CORRECTION (audit-2 F2): the jump arm had to come AHEAD of the recorder, and re-throwing alone was
not enough.** The catch-all did re-throw, so the jump itself survived — and on the way it wrote the
`Break` into `primary$n`, which routed the `finally` to the SUPPRESSING arm. `scala.util.boundary.
Break` is constructed with suppression DISABLED, so `addSuppressed` there is a documented no-op:
a `close()` that threw during a jump was **swallowed entirely** and the jump completed normally.
Java is the opposite and for a structural reason — a java `break` carries no exception object, so
14.20.3.1 has nothing to suppress INTO; the close exception replaces the jump and propagates out of
the loop. The fix is one arm, mirroring `tryStr`'s existing §4.4 guard:

```
catch { case brkThru$: scala.util.boundary.Break[?] => throw brkThru$
        case t$n: Throwable => { primary$n = t$n; throw t$n } }
```

`primary$n` then stays null on a jump, the `finally` calls `close()` bare, and the statement
completes abruptly for the close's reason exactly as java's does. **Note what could see it: only
`TryResourceBehaviourSpec`.** The emitted code compiled before and after, the corpus writes zero
try-with-resources, and the shape spec asserted every line of the lowering while asserting nothing
about their ORDER — which is the whole of the defect. Measured: liqp 0 -> 0 errors, 357/218 ->
357/218, **0 member digests moved on any lane**, because no library in the corpus has the construct.
*Fix kind: (a) engine.*

**The corpus count is ZERO and that is the whole reason this survived.** Ten ported upstream trees,
not one try-with-resources between them, so no port's number would have moved if a library HAD used
one — the same lesson F4 states, at a defect one order larger. The gate is therefore a SPEC
(`TryResourceSpec`, shape + the `try-resource` check lane; `TryResourceBehaviourSpec`, the semantics
executed) plus a counted check that reports 0 on every lane today and cannot report 0 by
construction: it finds the resource-carrying `try`s from the TREES and takes only the LOWERED set
from the emitter, so `_ => false` reproduces the un-repaired engine exactly.

**And the frontend had a second, smaller hole in the same statement.** `getResources` was read with a
`collect { case lv: CtLocalVariable[?] => … }`, which is a silent drop for every shape it does not
name — and JLS 14.20.3's SE9 form (`try (existingEffectivelyFinalLocal) { … }`) is one, modelled by
Spoon as a variable REFERENCE. It fell out of the list, so that resource closed one fewer time than
java does, with nothing in the tree to say it had been there. Refused LOUDLY now (M6): the faithful
translation is a fresh alias binding and minting a local symbol for it is a change worth making the
day a library writes one. None does.

*Fix kind: (a). Universal — a java statement form with no Scala counterpart, no library involved.*

### F6. A NULL selector must NPE — the fall-out arm's own defect, read at the other value

CLOSED. Java throws a `NullPointerException` the instant a `switch` on a REFERENCE type sees a null
selector (JLS 14.11.2 for enums, 14.11's general text for `String` and boxed primitives), and it is
IMPLICIT: a classic switch has no `case null` syntax, so there is no way to write one that tolerates
null. Scala's `match` special-cases nothing — `null` fails every literal and constructor pattern and
reaches whatever the LAST arm is.

**Which makes it the same mechanism as F-family's fall-out arm, read at a different selector value,
and the two point opposite ways.** Without the fall-out arm an ordinary value throws `MatchError`
where java falls out — a non-exceptional path became an exception. With it and without this repair a
null value falls out where java throws — an EXCEPTIONAL path became a silent no-op. Adding the first
repair is what created the second one's silence; before it, a null selector at least threw
`MatchError`.

Both are invisible to a compile: the emitted `match` is valid Scala either way and no count moves.

What shipped: `case null => throw new java.lang.NullPointerException(…)` ahead of the java arms,
under two conditions and only those.

- **the selector's type is not a scala value class.** A java primitive renders as `scala.Int` /
  `scala.Char` / … by construction, so the emitted type is the whole test — and it has to be, since
  most of the corpus's switches are `char` scanners where an unreachable `case null` would be noise
  on every one. `TirEmitter.ScalaValueClasses` is spelled once and read by the emitter and the check
  both, so the repair and the count cannot disagree about which switches java's rule applies to;
- **the java does not write a `null` label itself.** SE21's pattern switch may (`case null ->`, JLS
  14.11.1), and that is java deliberately handling null — a synthetic throw ahead of it would invert
  exactly the behaviour the label exists to state. Spoon parses the form and the frontend already
  carries it as a `Constant.NullC` label, so the test is a tree fact rather than a guess.

Counted as `switch-null`, found from the TREES (which switches are reference-typed) against the
EMITTER's guarded set — so `_ => false` is the un-repaired engine on the same trees. `Tree.Match`
gained an id for that, on `Tree.Try`'s contract exactly: an `Origin` is not unique across nodes and
`StandardTraversal` rebuilds every node it walks, so neither can key the question.

**libGDX: 55 member digests moved, errors 0 -> 0, every check count flat.** That is the measurement
worth keeping — fifty-five switches in one library whose null path was a no-op, none of which any
gate could see, and a repair whose whole cost is one arm each. `JsonValue` alone carries 19.

*Fix kind: (a). Universal.*

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

### L4. A Scala KEYWORD as a PACKAGE SEGMENT — the emitter escaped IDENTIFIERS and never PATHS

CLOSED. Java's reserved words are not Scala's, so `type`, `object`, `val`, `given`, `end`, `as` and
two dozen more are legal java package segments and unparseable scala ones.
`com.fasterxml.jackson.core.type.TypeReference` is the witness: **3 errors on liqp, none of which
names the cause** — an E119 (`package com.fasterxml.jackson.core is not a value`), an E067 about the
member that reference is inside, and a bare `end of statement expected but '.' found`.

The emitter's `esc` had the keyword set from the beginning and every name rendered BY HAND went
through it — a member, a local, a type's simple name, a `private[pkg]` qualifier. A
`Symbol.fullName` did not, because it is a PATH and reached the output verbatim through
`nestedPath`'s base case, `nestedPath`'s fallback, `typeValue`'s two qualified branches and the
`package` clause itself. So the gap was never "which keywords" — it was that four call sites emitted
a string nothing escaped.

Two things worth keeping:

- **Escape per SEGMENT, cutting only at §4.56's three separators** (`.`, `$`, `#`), carrying each
  separator across verbatim — `nestedPath` re-chooses `.` versus `#` per level afterwards, and a
  whole-string test would both miss `com.x.type.Ref` and corrupt `com.typescript.T`.
- **Nothing but scalac can count this, and that is the right counting.** The failure is a SYNTAX
  error at the first occurrence, never silent: there is no emitted-text check to add, because the
  emitted text is not a file. Contrast §4.4's table, where the whole point is that the text parses.
  What the fix owes instead is a spec on the EMISSION (`TirEmitterSpec`, negative-tested by
  neutering `escPath`), and the member half pinned beside it — `esc` already covered members, and
  the pin is what keeps the two halves from drifting apart again.

Five corpus libraries had no keyword segment anywhere, which is why this survived to the sixth. A
library with a `type`, `object`, `val` or `package` segment is not exotic — jackson ships one.

*Fix kind: (a) engine, universal.*

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

### CT7. A class a FRAMEWORK instantiates cannot host the clause, and nothing can put a `given` in generated code — **CLOSED; the numbers below are what it cost, and the fix is a THIRD ANSWER plus the warning that finds it**

CLOSED CT5 and CLOSED CT6 each took the enablement one step further and each was found BY COMPILING.
CT7 is the first that a compile cannot see at all, and it is the reason CLAUDE.md §3 says what it
says. The P5 delivery reached **0 scalac errors on libgdx-core, every number of CT6's census
reproduced exactly** — and then the base's own suite lost five tests:

```
sge.graphics.g3d.utils.AnimationControllerTest.initializationError
  java.lang.IllegalArgumentException: requirement failed:
  Class 'sge.graphics.g3d.utils.AnimationControllerTest' is missing a public empty argument constructor
```

217 passing / 4 expected-failing → **212 / 5, with 5 baseline tests reported DID NOT RUN**. libgdx-test
compiled at **0 errors** while this happened; `context-seam` on that module is 0, `policy` is 0, and
the emitted file is valid Scala. Only §5.1's `tests.tsv` diff sees it, which is the whole argument
for running the suite rather than counting errors.

**The cause is every step of the design working.** The suite constructs `new Model()`; `Model` is one
of the 188 threaded classes; the instantiate edge threads the suite; `attach = "class"` puts the
clause on its constructor:

```scala
class AnimationControllerTest(using sge.Sge) extends munit.FunSuite {
```

The decision row says `via=instantiates-threaded`. What no part of the closure knows is that
**nothing in the program ever instantiates this class** — MUnit does, reflectively, and a reflective
instantiation cannot supply a `using`. The same is true of every JUnit suite, every `ServiceLoader`
implementation and every framework-constructed type in reach of this engine.

**No manifest key reaches it, and the three that look like they might were each walked to the wall:**

| exit | what it actually does |
|---|---|
| `scope = Everywhere(except = <the suite>)` | leaves the suite un-threaded — and its `new Model()` still needs a given, so a lost suite becomes a COMPILE ERROR. Strictly worse |
| `sites` (`lazy-init` / `residual-global` / `refuse`) | speaks about a READ. The suite has no read; it has an instantiate edge. CT6 Face B, one shape further out |
| `attach = "method"` | MUnit's `test(…)` registrations are class-BODY statements, i.e. the constructor — so the clause lands in the same place. And it is the mode §11.12 measured at 3.3× with 32 frozen components |

**The reference port ports this very suite, and its shape is the fix** (`../sge`,
`sge/graphics/g3d/utils/AnimationControllerTest.scala`):

```scala
class AnimationControllerTest extends munit.FunSuite {
  private given Sge = SgeTestFixture.testSge()      // noop application/graphics/audio/files/input/net
  …
  val modelInstance = new ModelInstance(new Model())
```

A NO-ARG constructor, and the context as a `private given` MEMBER supplied by a hand-written fixture.
So the engine is missing two things, and they are separable:

- **the ATTACHMENT decision has a third answer.** Today a declaration either takes the clause or is a
  boundary; a framework-instantiated class is neither — it must take the context WITHOUT taking a
  parameter. Whatever supplies that fact (a per-declaration policy, or `TestFrameworkTransform`
  marking what it converts) it is not derivable from the closure, because the closure only sees the
  program;
- **a per-declaration CONTEXT SOURCE — the expression that becomes the `given` member.** This is the
  half a port cannot buy for itself: the emitted suite is generated (§5.5), so no hand edit reaches
  it, and the only place a hand-written `given` could be seen from is `sge.Sge`'s own companion —
  which is precisely the **ambient default given DESIGN.md §8.4 deleted**, and which would silently
  paper over every seam in the program rather than this one.

**The hand-written half of a port is FIXABLE, and the contrast is the diagnosis.** Three ports carry
hand-written MUnit suites that construct threaded types (screens' `ScreenmanagerSuite`, vfx's
`VfxFrameBufferSuite`), and every one of them can be repaired by the port with the fixture sge wrote —
it is a `src/` file, so a human may add a `given` to it. The only unfixable case is the suite the
ENGINE emitted. A port can supply the value; it cannot supply the line.

**"Fixable" is not "unaffected", and the delivery measured the difference: eight hand-written files
across three modules** (PROGRESS §11.12) — an injected context type, an absent-service test fixture,
four shims and two suites. A hand-written shim is outside the closure, so nothing threads it and
every one of them breaks the moment the shared surface moves — and WHICH repair each takes is READ OFF
ITS GENERATED CALLER rather than chosen. Two shims in one directory took opposite answers:
`NestableFrameBuffer`'s caller is an instance method of a threaded class, so it takes the clause;
`GLUtils`' caller is a generated declaration the closure did NOT thread, so it may not, and its honest
answer is the residual global. A clause on the second would have emitted a port that does not compile,
from the file that looks most like the one that should have it. The rule is now `CLAUDE.md` §1(b).

**CLOSED, and both halves shipped together because either alone is worse than neither.**
`ContextHolder.selfSupplied` is a `Map[type FQN, Scala expression]`: the named type takes the context
WITHOUT taking a parameter — no clause on its constructors, and `private given <ctx> = <the
expression>` at the head of its body instead, which is the reference hand port's shape reached from
policy rather than by hand. `ContextNeed` treats it as a RESOLUTION and not a refusal: the reads
inside it are still `ReadPlan.Threaded` (the read plan asks `supplies`, not `classes`), so a
self-supplied type reintroduces no global, and it propagates neither down the hierarchy nor to its
instantiation sites, because its constructors are exactly what java declared and there is nothing
for a `new` to supply. The expression is `Tree.Opaque` — the node `MethodBodyTransform` already uses
for Scala the frontend never saw — emitted verbatim, in the EMITTED namespace, uncheckable by the
engine and checked by the target compiler at one attributable line. The emitted probe compiles at
**0 errors under scalac 3.8.4**, which is the M2 gate this entry is entitled to.

**And the WARNING is the half the entry was actually about**, because a policy nobody knows to write
is a policy nobody writes. `context-seam` gains `unconstructed-thread`: a threaded class NOTHING IN
THIS PROGRAM CONSTRUCTS whose ancestry leaves the program. Both halves are structural and neither
names a library — no `Instantiate` edge into it or into any owned descendant that is constructed (a
constructed subclass supplies the parent's clause through its own `extends`, so the parent IS
exercised), and a strict ancestor this program does not declare, `java.lang.Object` excluded because
it is every class's parent and counting it would fire on the whole port. It WARNS and does not
refuse, and that is the honest answer rather than timidity: from inside the program a class a
FRAMEWORK constructs and a class this library's USERS construct are the same shape, and refusing
would make the second unportable while silence made the first invisible.

**THE WARNING'S CRITERION WAS MEASURED THREE WAYS AND ONLY THE CONJUNCTION IS USABLE — and it does
NOT see the case that opened this entry. Keep the numbers; they are the argument.** The dry run is
the §11.12 one replayed with the P5 holder over libGDX core (275 threaded declarations in 177 files,
reproduced to the row), and over core PLUS its own test source set:

| criterion | warnings on libGDX core | sees `AnimationControllerTest`? |
|---|---:|---|
| never constructed **and** external ancestry (SHIPPED) | **1** (`RemoteInput`) | **no** |
| never constructed **and** never NAMED anywhere in the program | 60 | yes (+1) |
| never constructed | 74 | yes (+1) |

**CORRECTION, measured by the fourth replay: the shipped criterion's ceiling on this port is 25, not
1, and the table above is a measurement of ONE PHASE rather than of the pipeline.** The dry run ran
the globals phase alone. In the LIVE pipeline `TypeRedirectTransform` re-points
`com.badlogic.gdx.utils.Disposable` at `java.lang.AutoCloseable` (`PROGRESS.md` §11.12's P1), so 24
more threaded classes acquire an ancestor the program does not declare and meet the second criterion:
every one of the 25 reads `extends java.lang.AutoCloseable which this program does not declare`, and
every one is a public-API leaf — `Stage`, `AssetManager`, `ModelBatch`, the six tiled renderers —
i.e. the "your USERS construct this" case, correctly warned and correctly not refused. anim8 adds 5
(its PNG writers) and vfx 16. Nothing about the criterion is wrong; what is wrong is deriving a
lane's size from a run that omits the phases that decide ancestry. **A criterion measured against one
phase is not a measurement of the pipeline** — and the conclusion the entry draws is unchanged, since
`tests.tsv`'s DID-NOT-RUN gate is still the detector of record and the relaxed criteria are still not
usable.

The reason is one line of upstream Java: `public class AnimationControllerTest {` — a JUnit test class
**extends nothing**. Its `extends munit.FunSuite` is minted by `TestFrameworkTransform`, which runs
AFTER this phase, so at the moment the closure asks, the suite's ancestry has not left the program
yet. And the two relaxed criteria that do see it are not usable: at 60 and 74 of 188 threaded classes
they are `Stage`, `TextField`, `FitViewport`, `PerspectiveCamera` — every leaf of the library's public
API, which nothing INSIDE a library constructs and everything outside one does. A lane at 74 is a
lane nobody reads, and it would inflate `context-seam` from 19 to 92, which is a number `PROGRESS.md`
quotes as the size of the boundary.

**So the entry's own sentence is confirmed by measurement rather than assumed: which declarations are
framework-instantiated is NOT derivable, because from inside the program a class a framework
constructs and a class this library's USERS construct are the same shape.** What ships is the precise
conjunction — correct wherever it fires, 1 on the real corpus — and the honest statement that the
detector of record for this class of loss is the one that actually found it: `tests.tsv`'s
DID-NOT-RUN gate (§5.1), which is a required lane on every test run. **Do not retry the relaxed
criteria.** What would close the gap properly is a signal the phase does not have and could take
without guessing — the SOURCE SET, since a test source set's classes are constructed by a runner by
definition and that is an engine concept, not a library one. Unbuilt: it is plumbing from `PortRun`
through `PolicyBinder`/`RunScope` into the phase, and nothing measured yet needs it.

Four more things the fix owes, each counted rather than left to a compile:

- **a self-supplied type whose PARENT took the clause is REFUSED.** A `given` member is in scope for
  the body and NOT in the `extends` clause — the parent's constructor runs before this class's
  members exist — so the super call would have no argument and nothing to build one from. Named,
  counted (`self-supplied`, `UNSATISFIED`), and a `PolicyIssue.Unverifiable` refusal beside it.
- **a `selfSupplied` entry the closure never reached is a DEAD BINDING.** `bindType` asks *does this
  program declare this type*, which a real class answers whether or not the threading would ever
  have touched it — CT6's blindness one key over, reported here rather than measured later.
- **an entry with no expression is `Malformed`**, and is refused BEFORE it takes the type out of the
  threading: neither a clause nor a member would be one mistake producing a second, worse one.
- **the applied mode is itself a counted seam** (`self-supplied`). The warning does not vanish when a
  port answers it — it MOVES, so the boundary stays sizeable.

*Fix kind: (a) engine for both halves — the attachment decision (`ContextNeed`,
`GlobalsToImplicitsTransform`, plus the anonymous/`private` given rendering in `TirEmitter`, which is
CT3's empty-name rule one node over) and the `unconstructed-thread` warning. The EXPRESSION that
fills it is (c) per-library and belongs in the port's manifest, pointing at a fixture in its
`inject`/`src`. The mechanism stays DEFAULT-OFF: no port declares a holder, so all 13 ports are 0
members changed with every check count identical.*

**AN ARRAY ALLOCATION IS NOT A CONSTRUCTION, and it was suppressing the warning** — found by the
checkpoint-4 audit, half fixed and half OPEN, which is why it is recorded rather than only committed.
`Xref` records `UsageKind.Instantiate` for a `Tree.NewArray`'s ELEMENT type. That is the right edge
for "is this type named in a way that needs it on the classpath" and the wrong one for "is this type
CONSTRUCTED": `new Suite[8]` allocates eight null slots and runs no constructor. Two readers take it:

- **`constructedByProgram`, the CT7 warning's suppressor — FIXED.** A class only ever array-allocated
  is a class nothing in this program constructs, which is precisely the shape the warning exists for,
  and the one edge that means the opposite of what it was read as switched the warning off. Excluded
  by NODE KIND, which is the rule `instantiates` already states for `new Pool<Cell>()` applied to the
  other node kind that names a type. Zero movement on all 13 ports — `context-seam` unmoved
  everywhere, so no port in the corpus has an array-allocated-only threaded class, which is exactly
  why nothing had noticed. Pinned by a spec whose fixture array-allocates the suite.
- **the THREADING CLOSURE (`ContextNeed`, the `Instantiate` arm) — OPEN.** The same edge makes
  `new Foo[10]` impose `Foo`'s context need on the enclosing declaration, which gets a `(using T)`
  it does not need. Not fixed here because it moves EMITTED SIGNATURES and therefore owes its own
  measurement — and the direction is over-threading, which compiles, so nothing in the corpus reports
  it. `Xref` is the honest place for a fix (a distinct `UsageKind` for "named as an array element"),
  and that is a wider change than this entry.

*Fix kind for both: (a) engine. The general rule is §4.56's, one node kind over — a phase may only
conclude something about a type from what it can READ AT THE NODE, and a recorded kind that answers a
different question is not that.*

### CT8. A DEPENDENT cannot declare a `sites` policy for its OWN types — the holder is inherited and the phase is not `MergeablePolicy` — **CLOSED; the per-declaration half is what a dependent adds, and the shared half is what it may not restate**

Found in the same run, in `gdx-vfx`. The phase is `SurfacePolicy` and its holders live in the BASE
manifest (§1.5, correctly — a base and a dependent that thread differently emit signatures that
cannot meet). It is **not** `MergeablePolicy`, so a dependent that constructs its own
`GlobalsToImplicitsTransform` is a fatal `SurfaceDivergence`, and the base manifest is therefore the
only home for `sites`.

But `sites` keys name DECLARATIONS, and a dependent's boundaries are in the DEPENDENT's own types,
which the base neither governs nor parses. So the exit the seam's own diagnostic names —

> give the site a `sites` policy (`lazy-init`), or move the use into a declaration the closure can reach

— has no manifest a dependent may write it in. Measured, with the counts:

```
[vfx] CONTEXT SEAMS: 4
  2 × com.crashinvaders.vfx.gl.VfxGLUtils#<clinit>          (a class initialiser reading the holder)
  1 × com.crashinvaders.vfx.gl.VfxGLUtils#<clinit>          (unsuppliable use of DefaultVfxGlExtension)
  1 × com.crashinvaders.vfx.framebuffer.VfxFrameBuffer#tmpCam (unsuppliable use of OrthographicCamera)
```

Two of the four materialise as scalac errors and the correlator classifies both as **`EngineGap`**,
which is the right answer: the port has nowhere to put the fix. This is CT6 Face B one level up —
there the exit did not exist, here it exists and is out of reach — and it is also the FIRST thing
`MergeablePolicy` was designed for that this phase never declared (`DESIGN.md` §8.13, `ENGINE-LIMITS.md`
D9). The merge itself is easy to state: holders union by `holder` FQN, and same-holder entries merge
their `sites` maps while every other field must AGREE or the pair is a refusal. What has to be
screened is §1.5's `SurfaceIntrusion` rule — a dependent's `sites` key must name a declaration inside
its OWN units, never one of the base's, or a dependent silently re-shapes the shared surface.

**CLOSED, and the shape is the SPLIT rather than the merge.** Stating the merge as "holders union by
FQN and same-holder entries merge their `sites` maps" is right and is not sufficient: a `sites` entry
belongs to a HOLDER, so a dependent that must name one would have to restate the holder — and with
`context`, `members`, `attach`, `reader` and `boundary` all agree-or-refuse, restating the holder
means restating the base's eleven-entry member map in the dependent's manifest. That is exactly what
§1.5 forbids, arriving through the door the merge opened.

So `ContextHolderExtension` is a value of its own — `holder` plus `sites` plus `selfSupplied`, and
NO FIELD in which the shared half could be restated. The rule is structural rather than a
convention, and the config front door says the same thing the same way: a `holders` entry with no
`context` block IS an extension, and any shared-surface key written inside one is an unread key the
loader already refuses. `GlobalsToImplicitsTransform.effectiveHolders` folds each extension into the
holder of its FQN; an extension naming a holder no manifest in the chain declares is a counted
`Malformed` finding, never a silent no-op.

The merge itself then divides on one line, and every field is on the side its own failure mode puts
it:

| field | merge | why |
|---|---|---|
| `context`, `members`, `attach`, `reader`, `boundary` | AGREE, or refuse | one emitted signature per member. A dependent adding a member mapping re-points reads in code it does not own; a dependent changing `attach` emits a different signature for the base's own types |
| `promoteToClass`, `scope` | union entries (`Everywhere`/`Only` mixed is a refusal, `NullabilityTransform`'s argument verbatim) | per-TYPE keys, and the `governs` screen is what stops one naming a base type |
| `sites`, `selfSupplied` | UNION, refusing same-key-different-value | keyed on DECLARATIONS. This is the whole of CT8 |

`subjects` reports the holder FQNs and every per-declaration key, so the `governs` screen runs on a
dependent's extension exactly as on a merge — and the base's own holder FQN is not in `added` (the
base already holds it), which is what lets vfx name `com.badlogic.gdx.Gdx` as the holder it extends
while `com.crashinvaders.vfx.*` keys pass the screen. A dependent whose `sites` key names a BASE
declaration is still a fatal `SurfaceIntrusion`, which is the rule this entry asked for.

*Fix kind: (a) engine — `GlobalsToImplicitsTransform extends MergeablePolicy`, plus the
`ContextHolderExtension` value the split needs. Nothing in a port reaches it, and no port declares a
holder, so all 13 ports are 0 members changed with every check count identical.*

**A NON-FINDING, recorded because it was nearly written up as a third one.** `gdx-gltf` reads **7
scalac errors with `signature` 1** under the enablement, all `EngineGap`, in `ModelInstanceHack`,
`PBRCubemapAttribute`, `PBRTextureAttribute` and `MeshLoader#load`. Every one of them is **PRE-EXISTING
and byte-identical in the reverted run** — gltf's committed state is 7 errors, and the enablement adds
none. A dependent's error count is only evidence about a change once it has been diffed against that
dependent's own baseline, and the two libgdx-dependent lanes whose counts DID move are vfx (0 → 43)
and screens (0 → 16).

### CT9. A dependent whose OWN declarations sit inside a base's CLAIMED namespace cannot name one — and a REFUSED merge silently runs only ONE of the two instances — **CLOSED, both faces; the screen now asks what the base EMITS, and a refused pair stops the run**

**Title, for renumbering: "a dependent cannot name a per-declaration key inside a base's claimed
namespace, and a refused merge runs one instance of two".** CLOSED. (a) engine, both faces. The
diagnosis below is what the fourth P5 replay measured and is kept verbatim; the two closures are at
the end of the entry.

Found by the fourth P5 replay, which is the first one that could get this far: CT5, CT6, CT7 and CT8
are all closed, **libgdx-core reads 0 scalac errors with the whole census reproduced to the row**,
and `gdx-vfx` — the dependent CT8 was written for — **merged its `ContextHolderExtension` cleanly on
the first production run**. libgdx-test did not, and the two faces below are why. Both are (a)
engine; neither is reachable from any manifest key.

**Face A — the `governs` screen refuses a dependent's key for a type the dependent DECLARES and the
base never emits.** CT7's answer is `selfSupplied`, keyed by TYPE FQN, and CT8's answer is that a
dependent writes it as a `ContextHolderExtension`. libGDX's own suite needs exactly one entry:

```
selfSupplied = Map("com.badlogic.gdx.graphics.g3d.utils.AnimationControllerTest" -> "sge.SgeTestFixture.testSge()")
```

`SurfaceFold.intrusion` refuses it, fatally:

```
manifest 0 -> 1   SurfaceIntrusion: globals->implicits
  this module's `globals->implicits` adds "com.badlogic.gdx.graphics.g3d.utils.AnimationControllerTest",
  which is inside `libgdx-core`'s declared namespace and which `libgdx-core` emits mechanically
policy   0 -> 1   the extension names a holder neither it nor any of its bases declares
```

Both sentences are false about this program and the screen cannot know it. libGDX's test module
declares its suites **inside `com.badlogic.gdx`** — `AnimationControllerTest` shares a package with
`BaseAnimationController`, which the base does emit — so no prefix separates the two modules, and the
base never parses `gdx/test` at all: *nothing stands at that name in the base's output*, which is the
admission the screen already states in words. The screen tests a different thing — `does the base
DROP it` — and a drop is a manifest fact while emission is a run fact. **The engine already solved
this once, one artifact over**: `ManifestAgreement`'s substitution half works from UNIT ORIGINS
precisely because a prefix cannot separate these two modules, and `LibgdxPolicy.core`'s own scaladoc
has said so since it was written. The screen never got the same treatment.

Note WHICH dependent this hits, because it is the whole shape: **a dependent with a namespace of its
own passes.** vfx's two `sites` keys are `com.crashinvaders.vfx.*`, outside the base's claim, and its
extension merged with `manifest` at 0. The refused case is exactly *a module whose own declarations
live in the base's namespace* — a library's own test module, a split package, a
`com.foo.internal.impl` sibling shipped as a second artifact.

The three port-side exits were each walked to the wall and every one is worse than the wall:

| exit | what it actually does |
|---|---|
| declare the entry in the BASE manifest | `PolicyBinder.bindType` cannot resolve a `gdx/test` type in a run that parses only `gdx/src`, so it is a `NeverMatched` `policy` finding on libgdx-core AND on all five libgdx-dependent lanes — six permanently unclearable rows, which is the noise floor CLAUDE.md warns about in `beanPropertyPairs`' own refusal note |
| narrow the base's `governs` | there is no prefix: `com.badlogic.gdx.graphics.g3d.utils` holds a main type and a test type |
| add the suite to the base's `dropTypes` | it is a lie that also lands the suite in `dropped-types.tsv`, which would classify every failure in it as a DERIVED expected failure |

**Face B — a REFUSED merge runs one instance, not two, and nothing says so.** `Pipeline.order` opens
with `val byName = phases.map(p => p.name -> p).toMap` and returns `out.toList.map(byName)`, so two
same-name instances collapse to the LATER one. `SurfaceFold` appends a refused pair rather than
merging it (`phases = phases :+ p`) — which is correct and is what the pre-merge behaviour promised —
and the pipeline then silently drops the base's instance and runs the dependent's.

Measured on exactly that: with the merge refused, libgdx-test ran only its own extension-only
instance, whose `effectiveHolders` is empty and whose `run` returns its input. So **the base's entire
holder never ran for that module**: the suite emitted with NO context clause and NO `given` member,
`new Model()` failed at 2 scalac errors, and `decisions.tsv` held **0** `globals->implicits` rows. A
whole shared-surface policy vanished from one module's pipeline with no error, no check count and no
finding — the fatal `SurfaceIntrusion` beside it is about a DIFFERENT thing and would be reported
identically if the pipeline had been correct. This is worse than the refusal it accompanies, and it
is not specific to this phase: any refused merge, for any `MergeablePolicy`, has run one instance
instead of two since merging existed.

*Fix kind: (a) engine, both. Face A is a screen that must ask what the base EMITS rather than what it
DROPS — the base's published port map is where that answer already lives (`ManifestAgreement.dynamic`
holds `BasePort`s), which makes it a decision for `DESIGN.md` and its own measure cycle rather than a
line in `SurfaceFold`. Face B is `Pipeline.order` keying phases by NAME; the fix is to order
INSTANCES, and it changes what every refused pair does, so it is measured on its own.*

**FACE A IS CLOSED, and the criterion is now the base's OUTPUT** (`DESIGN.md` §8.13's amended
`governs` section). The screen used to be a pure function of manifests and could therefore only ask
manifest questions — *is it dropped, does an `inject` ship at the name* — and **a drop is a statement
about a type the base HAS.** It says nothing about a name the base has never heard of, which is the
whole of this entry. The base's PUBLISHED PORT MAP is the artifact that can say it, and it answers
three ways:

| the base's map, at that FQN | screen |
|---|---|
| an entry that is not `Dropped` (`Ported`/`Renamed`/`Substituted`) | REFUSE — a class, a rename of one, or an injected replacement stands there, all three shared surface |
| an entry that IS `Dropped` | ADMIT — the map's own words for "nothing stands at that name" |
| **no entry at all** | ADMIT — the base declares nothing there. This row is CT9 Face A |

Three consequences, each measured or argued rather than assumed:

- **The screen MOVED, and the fold stopped refusing.** A port map is a run artifact and `SurfaceFold`
  must stay a pure function of manifests, so the fold names CANDIDATES (`SurfaceFold.Intrusion`) and
  `ManifestAgreement` — which already holds the `BasePort`s — applies the emits-fact. An intrusion
  therefore no longer un-merges: a cleared candidate merges normally, and a confirmed one stops the
  run at the gate Face B installed, before any phase runs. `SurfaceFold.Cause` is back to the two
  causes that really do leave two instances.
- **STALENESS is D1's answer, not a new one.** `BasePort.map` is empty for a map never published AND
  for one proven stale — the same path, deliberately — and the screen then falls back to
  re-derivation, which is the criterion that shipped before it could read a map. That fallback
  REFUSES where the map would admit, which is the safe direction for a screen, and
  `BaseMapMissing`/`BaseMapStale` already report it as the weaker check with "run the base port" as
  the fix. **A dependent that runs before its base ever has behaves exactly as it did**, loudly.
- **Both directions are negative-tested, and the two production shapes stay put.** A dependent key at
  an FQN the base's map emits is still fatal (with the map quoted as the evidence); one at the
  dependent's own FQN inside the base's namespace is admitted. `gdx-vfx`'s own-namespace extension is
  outside every claim and never reached the screen; `ashley`'s redirect is admitted by the drop
  exactly as before, now proven from the base's output rather than from its manifest. All 13 ports: 0
  members changed, every check count identical, `manifest` 0 everywhere.

**FACE B IS CLOSED, and it took TWO changes rather than one** (`DESIGN.md` §8.13's fourth as-built
section). Ordering instances is the obvious half and it is not sufficient: with both instances
running, a refused pair applies TWO configurations of one phase to one program, which is the
approximation the refusal exists to refuse. So the refusal is now LOAD-BEARING —
`ManifestAgreement.surfaceGate` is `PortRun.execute`'s first act after anchoring the report paths, it
shares `statik`'s body so the gate and the report cannot drift, and the message carries BOTH
instances' policy fingerprints. Nothing is parsed and nothing is emitted.

- **The silent-drop shape is reproduced and then caught, in specs**, because a defect nothing could
  see is one a count cannot re-detect: `PipelineOrderSpec` runs two same-name instances over a
  program and reads the answer off the TREE (11/12, not 10/11 — the number that was missing), and
  `PortRunSpec` drives a run whose fold refused and asserts it dies naming both tables.
- **An ordering edge NAMES a phase, so it constrains EVERY instance of that name.** "after X" meaning
  "after one of the two Xs" is a schedule nobody declared.
- **Zero movement, as predicted**: no port in the corpus has a duplicate phase name, ties are still
  stable in declaration order and successors are still visited in name order, so all 13 ports read 0
  members changed with every check count identical.

**FACE B'S THIRD CHANGE — the EQUAL pair, found by the checkpoint-4 audit and closed with it**
(`DESIGN.md` §8.13's fifth as-built section). Two changes were not enough either. `SurfaceFold.of`
appended a same-name instance whenever it declined to merge, *including when the two fingerprints
were equal*, where it deliberately recorded no refusal — correct while `Pipeline.order` keyed by name
and ran one of two, and silently wrong the moment it ordered instances: **an equal pair then ran the
phase TWICE over one program.** Nothing could see it. The divergence report's own criterion was
`fingerprint(ps).distinct.size > 1`, which an equal pair fails by definition, and the corpus has no
duplicate phase name so no lane moved. The one production shape it had is idempotent
(`ClassTableTransform`), and `PortRunSpec`'s green negative was pinned on that accident — a spec
asserting the run was green while the pipeline underneath it was wrong.

Worse in the same arm: **`PortManifest.fingerprint` is NAME-ONLY for a phase that is not a
`SurfacePolicy`**, so `test-framework(suiteA)` and `test-framework(suiteB)` compared EQUAL — two
different configurations of one phase, both running over one program, with nothing in the engine able
to state that they differ. Deduping such a pair would have been Face B restored under a new name.

The fold now gives a same-name pair three answers instead of two — merge, DEDUP (provably equal:
one instance runs, the base's, at the base's position), or two instances and a fatal
`SurfaceDivergence` — and refuses outright (`Cause.Unverifiable`) a pair it can neither compose nor
COMPARE. The report's criterion moved with it, from "two distinct fingerprints" to "two instances in
the effective pipeline", so the pair the engine understands least is no longer the one it reports
nothing for. Both arms negative-tested against a spec-local phase implementing neither contract,
rather than against whichever production phase happens to lack one. All 13 ports: 0 members changed,
every check count identical.

*Fix kind: (a) engine, and the one it leaves for a phase author is also (a), in whatever repository
owns the phase: implement `SurfacePolicy` and the pair becomes verifiable.*

## 13. Retyping a PRIMITIVE to an opaque domain type

All five entries below come from the SAME work — Stage P6's attempts to enable an opaque family on
libGDX core (`PROGRESS.md` §11.25) — and none of them is in the policy that configured it. Read O1
and O2 together: they are two halves of "a retyping phase owes more than the declaration it was
pointed at", and neither was reachable from any manifest key, so a port that hit either had no exit
but the engine. O3 is a third shape — a family the spec cannot ask for at all. **O5 was the one that
blocked the family**, and it is not about translation at all: the phase MINTS a unit, and the run
emitted that unit from every module in the pipeline. Note where each was found, because the pattern
is the point — O1 and O2 by compiling the BASE, O5 only by compiling the DEPENDENTS, which the
base's own 21 green check counts cannot see. **O1, O2 and O5 are CLOSED; O3 and O4 are named
residues.**

**The family they blocked is now SHIPPED** — `LibgdxPolicy.mainPhases` carries the `TextureHandle`
`OpaqueSpec`, `just measure-all` is green on all thirteen ports with every lane headline
byte-identical, and exactly one `TextureHandle.scala` exists. So each entry below is a closed dead
end rather than an open one; what is left of this section for a NEW port is O3 and O4, plus the
rule each closed entry states about what a retyping phase owes.

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

**And every one of those numbers reproduced exactly on the full delivery run, which is why O5 was
worth reading before any of them.** The base was finished at that point and the step still could not
land, because what that proof did not contain was a single dependent. It does now: the all-13
replay in O5 is the first measurement of this family that is evidence about the STEP rather than
about the base.

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

### O5. A MINTED unit has no origin, so EVERY module in the pipeline emits it — was 24 errors, six suites stopped

**CLOSED.** (a) engine, in what a run decides to WRITE rather than in the phase's translation.
Measured on the P6 delivery attempt: libgdx-core read **0 errors** with every one of its 21 check
counts unchanged — the translation was exactly right — while **six dependent lanes went 0 → 3,
0 → 6, 0 → 3, 7 → 13, 0 → 3 and 0 → 3**, and none of their suites ran.

**The number that says it is closed is the same one that opened it.** The P6 `OpaqueSpec` re-applied
verbatim and measured on **all thirteen ports** now reads `just measure-all` green end to end:
**exactly one `TextureHandle.scala` in existence** (libgdx-core `main`), **zero new errors in every
dependent lane**, and every one of the six stopped suites running again at its committed outcome
(gdx-test 217/4 of 221 emitted, ashley 108/2 + 2 skips of 112, anim8 23/0, vfx 64/0, screens 16/0,
sg 16/0; gltf's 7 and noise4j's 2 are pre-existing and byte-identical). libgdx-core replays its
proof census unchanged — 0 errors, 1 seed, 2 `RetypedSignature` rows (`GLTexture#glHandle` and
`#getTextureObjectHandle`), 30 coercions as 14 wraps + 16 unwraps, 37 distinct members, decisions
+3 (+2 `RetypedSignature`, +1 `RenamedPackage` for the minted unit), all 21 check counts identical —
and **every dependent reads 0 members changed**, which is a second measurement rather than a
restatement: no corpus dependent EMITS a reference to the retyped surface at all (gdx-gltf's
`SharedTextureTest` is the only Java that names `getTextureObjectHandle`, and that file is not in
the gltf test port's file set). The dependent-still-coerces half is therefore pinned by
`OpaqueMintOwnershipSpec` and not by the corpus.

The phase MINTS its object as a top-level unit (`Origin.synthetic`) and appends it to
`program.units`. `PortRun.converted` classifies a unit by its recorded origin — under `sourceRoot`
is owned, under a `resolutionRoot` is not — and its documented fallback is that **a unit with no
usable origin is converted, because refusing to emit on a missing origin would be a silent
omission.** That rule is right for a parsed unit and wrong for a minted one. A dependent's model
CONTAINS the base's units (that is what `resolutionRoots` is), so the hint matches there too, the
phase mints there too, and the fallback then writes the object into the dependent's own
`src_managed`. Nine files were emitted where one was owed:

```
libgdx-core/src_managed/{main,test}/…/TextureHandle.scala   ← main is the only legitimate one
ashley-core/src_managed/{main,test}/…/TextureHandle.scala
gltf-core/src_managed/{main,test}/…/TextureHandle.scala
anim8-core/src_managed/main/…/TextureHandle.scala
vfx-core/src_managed/main/…/TextureHandle.scala
screens-core/src_managed/main/…/TextureHandle.scala
```

Each duplicate costs exactly three errors — `24 = 8 × 3` — and the second and third are the part
that makes this louder than a plain redefinition:

```
[E161] TextureHandle is already defined as object TextureHandle in libgdx-core/src_managed/main/…
[E007] def apply(v: scala.Int): TextureHandle.T = v      Found: (v : Int)   Required: …TextureHandle.T
[E007] def unwrap(v: TextureHandle.T): scala.Int = v     Found: (v : …TextureHandle.T)  Required: Int
```

Opacity is per-DEFINITION: inside the duplicate, `TextureHandle.T` binds to the FIRST definition's
opaque type, which is abstract there, so the duplicate's own `apply`/`unwrap` no longer type-check
against it. A minted opaque type therefore cannot be duplicated even harmlessly — the copy is not
merely redundant, it does not compile.

**There is NO POLICY EXIT, and that is the load-bearing negative.** `surface` is inherited through
`extendedBy` and cannot be subtracted; a dependent that declared its own instance would be a fatal
`SurfaceDivergence` (the phase implements no `MergeablePolicy`); and holding the phase back in a
dependent is the very thing §1.5 forbids, since the base emits `glHandle: TextureHandle.T` and the
dependent would emit `Int`. A `RuleScope` cannot reach it either: the fence bounds which SYMBOLS are
seeded, and the mint is a consequence of the seed set being non-empty at all — which it must be in a
dependent, because the seeded declaration is the base's.

**The failure is invisible to every count the delivery was measured by.** libgdx-core's 21 check
counts are identical, its 37 moved members are exactly the reached set, its `decisions.tsv` moves by
the expected +2 `RetypedSignature`, and the emitted code is the reference port's line for line.
Nothing in the base's report can see a file a DEPENDENT wrote. The one artifact that half-notices is
the provenance log, and it notices inconsistently: of the nine emitting ports only three recorded a
decision for the unit they wrote (libgdx-core, libgdx-test, screens, one `RenamedPackage` each) —
so the module scope that governs `decisions.tsv` already disagrees with what the emitter writes, in
the direction that hides it.

**THE FIX, both halves, and the one place it deviates from the shape recorded here.** A MINTED unit
belongs to the module that owns the declarations it was minted FOR:

- `PrimitiveToOpaqueTransform` is now `PolicyBound` and fences its mint on `RunScope.emits`. A module
  that does not mint still RETYPES and COERCES: it holds the minted symbols, `Program.owns` reports
  them external exactly as it does a JDK symbol (they hang off no unit of that run), and the emitted
  fully-qualified `Name.T` / `Name(…)` resolve against the object the owning module emitted — which
  is what a dependent lane already compiles against, since each lane passes the base's `src_managed`
  on the same `scala-cli` invocation. One consequence worth stating because it looks like a bug: the
  minted symbol is UNOWNED in a dependent, and `PackageRenameTransform` still renames it, through the
  `portOwnedPrefixes` relaxation it grew for injected replacements. The two cases are the same case —
  a type in the port's own namespace that this run did not parse.
- `PortRun` refuses to write a synthetic unit whose FQN a base's published port map claims
  (`PortRun.isSynthesised` + `claimedSynthetic`, fatal, with the §1 classification in the message).
  A DROPPED type in a base's map is not a claim — the base emits nothing to collide with. This is the
  belt: it catches the NEXT phase that mints without asking, and `GlobalsToImplicitsTransform`
  already mints a top-level unit the same way.

**The deviation: the fence reads the HINTS, not the grown seed set.** The shape recorded above said
"a seed", and a seed set GROWS along pure-move flows — it reaches a dependent's own declarations the
moment that dependent assigns the base's tagged getter to a local, which is exactly gdx-gltf's
`SharedTextureTest`. A grown-set fence hands the mint straight back to a module that merely USES the
family and reproduces O5 in full. The hints are what the SPEC NAMED, so the module declaring them is
the family's home. `OpaqueMintOwnershipSpec` pins both directions, and its fixture asserts that the
grown set really does reach the dependent's own unit while no hint does — so the trap cannot quietly
stop being exercised.

*Fix kind: (a) engine. A dependent that genuinely needs its OWN opaque family is a design question
this does not answer; the corpus has none. And the general rule is CLAUDE.md §1.5's: a phase that
SYNTHESISES a declaration owes the same one-module answer `inject` does.*

**…AND THE FENCE ADMITTED A HINT SET THAT SPANS TWO MODULES — closed by the checkpoint-4 audit.**
The fence reads the hints, which is right; it read them with `exists`, which is not. `hints` is a
`Symbol => Boolean` predicate, and while libGDX's is an exact FQN (`_.fullName == "…GLTexture#glHandle"`,
which cannot straddle), the type invites `_.name == "handle"` — the form that reads naturally and
matches whatever a dependent happens to have called a field. **One such match inside a dependent's
own units makes `exists` true THERE, and the base's own hints make it true in the BASE.** Both
modules then mint one FQN: O5 in full, with the fence in place and answering.

The belt behind it does not close this, and the reason is worth stating because it is an ASYMMETRY:
`PortRun.claimedSynthetic(_, _, Nil)` is `Nil`, so a base with no published map — or one proven
stale, which shares that path — ADMITS the second copy, whereas `DESIGN.md` §8.13's `governs` screen
asked the same "I have no map" question REFUSES. The belt's admission stays, argued rather than
flipped: an empty base list is also what a base port, a single-module port, a spec and `DebugEmit`
have, and every one of them is the module that MUST mint, so refusing on it would fail the only run
allowed to write the type — while a policy key checked against a NAMED base has no such reading. The
loud half already fires (`BaseMapMissing`/`BaseMapStale`, "run the base port once"). What was wrong
was a fence LEANING on a belt that admits by default.

So the fence answers for itself: `PrimitiveToOpaqueTransform.refuseSpanningHints` throws when the
bound hints straddle `RunScope.emits`, naming both sides, before propagation runs. The phase already
holds the scope and already resolves a symbol to its top-level unit, so this costs no new machinery.
§1(c) LIBRARY RULE and the fix is in the PORT — name one module's declarations, or declare a
separate spec at the dependent's own FQN. Negative-tested four ways in `OpaqueMintOwnershipSpec`: a
spanning name pattern refuses from BOTH modules' side, the same pattern over ONE module mints
normally (the rule is about the line, not about patterns), and an exact-FQN hint over the same
two-module tree is untouched — which is the measurement behind "zero corpus movement" rather than an
assertion about it. All 13 ports: 0 members changed, every check count identical.
