# libgdx-core port — measured state and remaining work

Scope: migrating libGDX's core module (`../sge/original-src/libgdx/gdx/src`, 605 types) through the
TIR into `libgdx-core/src/main/scala`. Distinct from GOAL.md's M6 phase (the xwiki cold port).

Reproduce everything below with `bash scripts/gdx_measure.sh` (re-emits, then compiles with
scala-cli 3.8.4). The migration itself prints four independent checks on every run.

## Measured state

| metric | value | source |
|---|---|---|
| clean-compile errors | **43** | `scripts/gdx_measure.sh` |
| portability (JVM-only APIs in emitted code) | **0** | `PortabilityCheck` |
| portability (injected replacements) | **clean** | `PortabilityCheck.inInjectedSource` |
| silent omissions | **30** (see §0 — the check does not yet see anonymous classes) | `OmissionCheck` |
| signature consistency | clean | `RewriteTrace.check` |
| substitutions verified removed | 10 dropped types | migration CHECK 1 + CHECK 2 |

Error breakdown: E007 18, E134 11, E008 6, E120 5, E083 1, E051 1, E049 1.
(E050 and E006 are gone; E051 8→1, E049 6→1.)

## 0. UNTRACKED SILENT OMISSION — anonymous class bodies are dropped

**Found 2026-07-28. Not counted by `OmissionCheck`, and the worst failure mode the project
recognises: it compiles green and the program misbehaves at runtime.**

`SpoonTir.ctorCall` translates a `CtConstructorCall` and never looks at `CtNewClass.getAnonymousClass`,
so every Java anonymous class is emitted as a bare constructor call with its BODY DISCARDED:

```java
comparator = new Comparator<Pixmap>() {                 // PixmapPacker.java:535
    public int compare (Pixmap o1, Pixmap o2) { … }
};
addListener(clickListener = new ClickListener() {        // Button.java:89
    public void clicked (InputEvent event, float x, float y) { … }
});
```
```scala
this.comparator = new java.util.Comparator[com.badlogic.gdx.graphics.Pixmap]()   // compare() GONE
this.clickListener = new com.badlogic.gdx.scenes.scene2d.utils.ClickListener()   // clicked() GONE
```

**~156 sites** in the corpus (`grep -rnE 'new [A-Za-z0-9_.]+(<[^>]*>)?\([^;{}]*\)[[:space:]]*\{[[:space:]]*$'`
over `../sge/original-src/libgdx/gdx/src`), across `Button`, `TextField`, `ScrollPane`, `SelectBox`,
`Tree`, `List`, `Slider`, `Dialog`, `Window`, `Skin`, `DragAndDrop`, `GestureDetector`, `Net`,
`NetJavaImpl` and more. Most compile green because the supertype is concrete (a no-op listener);
only the four `java.util.Comparator` sites fail to compile (E007 ×4), which is how it surfaced.

The enum path already does this correctly — `enumCase` reads the constant's anonymous-class body —
so only the expression path is missing.

Fix sketch (deliberately not started here: it changes shared TIR node shapes while another agent was
editing `transform/`):
1. TIR: give `Tree.New` an optional body, or add a node carrying `(parentTpt, args, List[Statement])`.
   Every exhaustive `Term` match needs the case (`Pass`, `Phase`, `Xref`, the transforms).
2. `SpoonTir.ctorCall`: on a `CtNewClass` with a non-null anonymous class, translate its members with
   the existing `classDef` machinery (fields/methods/init blocks) and attach them.
3. `TirEmitter`: render `new Base(args) { members }` — Scala handles the enclosing-local capture that
   javac has to lower into synthetic constructor parameters, so no capture analysis is needed.
4. `OmissionCheck`: add a finding for any `CtNewClass` whose body did not survive, so this can never
   silently regress again. **Expect the omission count to rise from 30 when it lands — that is the
   check starting to tell the truth, not a regression.**

## Remaining work, highest value first

### 1. Constructor funnelling — mostly DONE, 30 left
`tir.CtorFunnel` makes the primary-constructor nomination a whole-program decision that BOTH the
emitter and `OmissionCheck` derive from, so the check can never drift from what is emitted. Two
mechanisms, both exact:

- **promotion** — the class's UNIQUE ROOT constructor (the only one not delegating `this(args)`)
  becomes the Scala primary whatever its arity: super arguments into `extends`, parameters into
  class parameters, body into class body. `TirEmitter.funnelParamRenames` suffixes `$p` on any
  name that would then collide with an own or inherited member. A promotion that would remove a
  nilary construction path a subclass needs is withheld (fixpoint in `CtorFunnel.Plans`).
- **replay** — a secondary's `super(args)` becomes `this()` plus the parent constructor's own
  statements, when the prologue `this()` runs is provably DEAD by the time the constructor returns
  (pure field assignment, every field re-assigned afterwards) and the arguments can be substituted
  without changing Java's evaluate-once. Private parent members the replay reaches are widened
  (`TirEmitter.widen`).

**Declared caveat:** where the prologue is dead but not empty, the replay repeats work Java did
once — `new DelayedRemovalArray(1000)` allocates the nilary path's 16-element backing array and
discards it. Final state is identical; this is a cost, not a behavioural difference, and it
replaces the previous behaviour of silently returning the 16-element array.

The 30 that remain, and why each is refused rather than approximated:

| class(es) | n | why |
|---|---|---|
| `GdxRuntimeException`, `SerializationException`, `ReflectionException` | 9 | parent is `java.lang.RuntimeException` — no body to replay, and `super(msg)` vs `super(msg, null)` differ in `Throwable`'s cause semantics (unset vs null), which no delegation reproduces |
| `DistanceFieldFont` (+`DistanceFieldFontCache`) | 8 | `BitmapFont()`'s nilary path loads the default Liberation Sans font from the classpath; replaying after it would do that I/O and leak a `Texture` |
| `RegionInfluencer$Single/$Random/$Animated` | 6 | `super` targets a VARARGS parent constructor; the argument is a single element, not a `Repeated` |
| `Dialog`, `Button`, `ScaleInfluencer`, `DepthShader$Config`, `FloatFrameBuffer` | 7 | prologue not superseded — the parent's nilary path does work the constructor does not re-do |

The remaining E120 ×5 are unrelated (4 × `GL*Interceptor` export collisions, 1 × `RegionInfluencer`,
whose nilary Java constructor delegates `this(1)` while two paramful roots leave nothing to promote).

### 2. Type-level residue — E007 18 + E134 11
Long tail of distinct patterns, none bigger than four. What is left:

- **`java.util.Comparator` raw ×4** (`PixmapPacker` ×2, `TextureAtlas`, `CameraGroupStrategy`) — NOT
  a type-level problem at all. These are the anonymous-class omission of §0 surfacing: the body is
  dropped, so `new Comparator[Pixmap]()` instantiates the bare interface. Fixing §0 fixes these.
- **`Array[T]` vs `Array[Object]` ×3** (`Array`, `Sort`, `TimSort`) — a generic array formal that
  Spoon erased at the reference; array covariance is deliberately disabled for our own callees
  (see `coerceArgsFixed`), and re-enabling it there breaks the invariant `Array[T]` overloads.
- **capture through a BOUNDED wildcard receiver ×4** (`Array.addAll(array.items, …)`,
  `ObjectSet` idem) — `erasedFieldReceiver` deliberately lets a bounded wildcard keep its capture
  ("`map.zeroValue` conforms to `V`"), which holds for a bare variable but NOT when the variable sits
  in an INVARIANT position: `Array[array.T]` does not conform to `Array[T]`. The fix is to treat a
  bounded wildcard as needing the receiver view too when the field's declared type mentions the
  variable NESTED, and to cast to the BOUND-substituted instantiation (`Array[? <: T]` → `Array[T]`),
  not the erased one — the erased `Array[Object]` does not conform either.
- `IntMap`/`LongMap` `Keys.map$p : IntMap[?]` vs `IntMap[AnyRef]` ×2; `OrderedMap` ×2;
  `ParallelArray` ×2; `AssetDescriptor` ctor ×2; and singletons in `AssetManager`, `FileHandle`,
  `BitmapFont`, `ResourceData`, `CharArray`, `ParticleControllerRenderer`.
- `NetJavaImpl` / `HttpParametersUtils` (2) are `CollectionsTransform` territory, not type residue.

### 3. Re-derive the scene2d agent's rules — DONE
All nine rules from `worktree-agent-a3f9b81b6a4184e28` (HEAD `0223788`) were triaged one at a time
against the current engine. Six were ported and paid; three were rejected as already-covered or
inert. See "Rules ported / rejected" below.

### 4. Smaller clusters
E008 ×6 (CollectionsTransform API gaps — `Iterator.remove`, `Map.entrySet`, `+=` on a mapped
collection; each needs a semantic mapping decision). E120 ×5 (4 × `GL*Interceptor` export
collisions, 1 × `RegionInfluencer` nilary/paramful constructor clash).
E049 is down to ×1 (`DepthShader.Config.defaultCullFace`: an implicit access Java resolved to an
INHERITED instance field, which Scala finds ambiguous with the companion's re-exported static of the
same name. `this.defaultCullFace` says what Java meant — but Spoon does NOT resolve this reference to
a `CtFieldWrite` under noClasspath, so the frontend never reaches the field path; see the do-not-retry
list). E051 ×1 is `Sprite.setRegion(int,int,int,int)` vs `(float,float,float,float)` — Java picks the
`int` overload, Scala finds both applicable via numeric widening with no most-specific winner.
E050 (local shadows a method name), E006 and E171 are **gone**.

## Rules ported / rejected this pass (83 → 43)

Every one measured on its own, committed on its own.

| rule | where | measured |
|---|---|---|
| ctor-ref ascribed to its resolved Java target SAM | `TirEmitter.samAscribed` | 83→76 (−7) |
| qualified `Outer.this` for this-as-value | `SpoonTir.outerThis` + `TirEmitter.thisRef` | 76→72 (−4) |
| shadowed implicit call qualification | `SpoonTir.shadowedImplicitCall` | 72→70 (−2) |
| assign to a member DECLARED as a type param | `SpoonTir.toDeclaredTypeParam` | 70→68 (−2) |
| call through a RAW-bounded type variable | `SpoonTir.typeVarReceiverArgs` | 68→67 (−1) |
| nested type vars erase through their declaration | `SpoonTir.erasedType` | 67→66 (−1) |
| unchecked conversion at an arg with a KNOWN receiver instantiation | `SpoonTir.knownReceiverArgs` | 66→63 (−3) |
| RAW `new`: fill its formals by the same name-directed rule as the raw type | `SpoonTir.rawCtorArgs` | 63→56 (−7) |
| nested-type path picks its separator PER LEVEL | `TirEmitter.nestedPath` | 56→52 (−4) |
| keep Java's `Outer.this` qualification on a CALL target | `SpoonTir.invocation` | 52→45 (−7) |
| …and on a FIELD access | `SpoonTir.fieldAccess` | 45→45 (faithfulness) |
| only the HEAD of a parent type is a named-inner position | `TirEmitter.parentTpe` | 45→44 (−1) |
| …same for a `new` type | `TirEmitter.ctorTpe` | 44→43 (−1) |

Two of the nine scene2d rules were **rejected on measurement** — see the do-not-retry list.

Three things the port now gets right that it did not before, independent of the error count:
`Outer.this.f` is no longer flattened to a bare `f` (it could bind to an inherited member of the same
name); `ModelInfluencer.Random#ModelInstancePool` names a real type; and a Java raw `new` fills its
argument types by the same rule that fills the type itself, so the two halves of one raw use agree.

## Substitutions in force

Per-library adjustments (sge replaced these rather than porting them). Declared in
`corpus-tests/src/main/scala/balticporter/corpus/LibgdxCoreMigrate.scala`, injected Scala under
`corpus-tests/libgdx-overrides/`.

| construct | disposition |
|---|---|
| `utils.Json` | injected facade — write path real, **decoding inert** until a Kindlings codec is bound in `Json.codec` |
| `utils.reflect.*` (7 types) | **eliminated**; portable calls re-pointed at `java.lang.Class` by `ReflectionToPortableTransform` |
| `utils.Pools` / `ReflectionPool` | injected factory-based `Pools`; `ReflectionPool` dropped (upstream deprecated it for `DefaultPool`) |
| `SharedLibraryLoader` / `Os` | injected shims (removed upstream in sge) |
| `ResourceData` class-by-name | `AssetTypeRegistry` name→class table via `ClassTableTransform` |

**Open behavioural caveat:** JSON *decoding* raises `UnsupportedOperationException` naming the swap
point. Chosen over returning null/empty, which would corrupt data silently. 49 of 50 decode sites
pass a `classOf[X]` literal, so a call-site rewrite to statically-derived codecs is viable; one
site (`readValue("resource", null, …)`) is class-tag driven and needs explicit handling.

## Do NOT retry (measured failures)

- **Erasing declarations** (raw → `Object`-parameterised instead of wildcard): **+277 errors**.
  `Array[?]` accepts an `Array[String]`; `Array[Object]` does not. The rule is *erase uses (casts),
  never declarations*.
- **`--js` compile as a portability gate**: proves nothing. Scala.js type-checks against JDK
  signatures and compiles `java.lang.reflect` happily; only the **linker** rejects it, and only for
  code reachable from an entry point, which a library lacks. Hence `PortabilityCheck` over the TIR.
- **Unconditional bound-erasing of a callee formal**: +47. Spoon types `X.class` as raw `Class`,
  but we emit a precise `classOf[X]`.
- **Argument-position raw→parameterized via `coerce`**: an executable reference's formal can name
  the callee's own type variables, which do not resolve at the call site.
- **Promoting a paramful constructor to the primary without a whole-program check**: +14 errors.
  It removes the class's nilary construction path, and every subclass whose `extends` clause passes
  no arguments then fails (`FillViewport extends ScalingViewport`, `FloatAttribute extends
  Attribute`). `CtorFunnel.Plans` withholds those promotions at a fixpoint.
- **Inlining a promoted constructor's body without renaming what it declares**: its parameters
  become class parameters and its top-level locals become class members, both then colliding with
  fields (`GLVersion.vendorString`, `PolygonRegion.textureCoords`). `TirEmitter.funnelParamRenames`
  suffixes `$p`; the INHERITED-name case matters as much as the own-name one, since it captures
  unqualified reads instead of failing to compile.
- **`selfRawFormalArgs`** (raw arg into a self-typed formal, `Cell.set(Cell)`; from the scene2d
  branch): **0 change**. The current engine's `uncheckedGeneric` already emits that cast; the rule
  only appends a second, identical `asInstanceOf` in `Table.scala`. Superseded, not needed.
- **`rawToParameterized` in `coerce`** (also from the scene2d branch, incl. the "keyed on the Java
  raw type" refinement): already present and STRICTLY MORE GENERAL as `SpoonTir.uncheckedGeneric`
  (`rawTarget` / `ownScope` flags). Do not re-add.
- **Qualifying an implicit access to an INHERITED instance field as `this.f`** (for the last E049,
  `DepthShader.Config.defaultCullFace`): **0 change — the code never runs.** Under noClasspath Spoon
  does not resolve that reference to a `CtFieldWrite` at all, so neither the `null`-target nor the
  implicit-`CtThisAccess` branch of `fieldAccess` sees it. Any fix has to start by finding out what
  node Spoon actually produces there.
- **Qualified `Outer.this` without a static/supertype guard**: **+22 errors**. libGDX nests
  subclasses inside their own base (`DynamicsModifier.FaceDirection extends DynamicsModifier`) and
  Spoon reports a plain `this` reaching an INHERITED member under the member's DECLARING type. Two
  guards are both required: the frontend walks out only through non-static inner classes
  (`capturesEnclosing`), and the emitter refuses to qualify a symbol the innermost class INHERITS
  from (`TirEmitter.inheritsFrom`) — constructor replay moves the base's `this` statements into the
  subclass body, where the bare `this` is right.
- **Name-directed fill gated on `resolveTypeParam` instead of the barrier-aware frame**: +2 `Not
  found: type T`. `resolveTypeParam` sees every enclosing scope by name; a `static` nested class
  cannot actually name the outer class's parameters. Gate synthesized casts on `accessibleTp`
  (`SpoonTir.tpAccessibleHere`).

## Guarantees that must not be weakened

Four checks run on every migration. They exist because silent correctness bugs (dropped
`static { … }` blocks; dropped `super(args)`; and now dropped anonymous-class bodies, §0) were found
only by accident, having compiled green for the entire project history. If a check starts failing,
fix the cause — do not relax the check.

**A check that reports zero is only as good as its coverage.** The anonymous-class omission (§0) was
green for the project's entire history because nothing looked for it. When adding a translation
path, add the corresponding check at the same time.
