# libgdx port — measured state and remaining work

**GOAL (set 2026-07-28): the WHOLE libgdx project ports; the ported code compiles AND passes the
migrated tests.**

That last clause is the important one. Everything measured in this document so far is *compiles* —
and four silent correctness defects were found this session (§0) that all compiled green, one of
them for the project's entire history. `gdx/test` holds **221 JUnit test methods making ~900
assertions**. Porting and RUNNING them is the first behavioural gate this project has ever had, and
it is worth more than any number of additional compile fixes.

Reproduce the compile numbers below with `bash scripts/gdx_measure.sh` (re-emits, then compiles with
scala-cli 3.8.4). The migration itself prints four independent checks on every run.

## Scope of the goal

`../sge/original-src/libgdx`, 1534 Java files across 18 modules. They are not equally in scope —
sge targets Scala Native and Scala.js, so the platform backends are largely irrelevant to it:

| module | files | standing |
|---|---|---|
| `gdx/src` | 605 | **in progress** — 7 typer errors, this document |
| `gdx/test` | 29 | **the goal's test clause** — 221 `@Test`, ~900 assertions |
| `backends/gdx-backend-headless` | 15 | plausible next port target — no windowing, pure JVM |
| `backends/gdx-backend-lwjgl3` | 39 | desktop JVM backend |
| `backends/gdx-backend-lwjgl` | 32 | superseded by lwjgl3 upstream |
| `backends/gdx-backends-gwt` | 209 | GWT/JS — sge replaces this wholesale with Scala.js |
| `backends/gdx-backend-android`, `robovm`, `robovm-metalangle` | 144 | platform SDKs; out of scope for a Native/JS target |
| `extensions/gdx-tools` | 80 | desktop authoring tools, not runtime |
| `extensions/gdx-bullet`, `gdx-freetype`, `gdx-lwjgl3-angle` | 9 | JNI wrappers — `PanamaFfiTransform` territory |
| `tests/gdx-tests` | 372 | visual demo apps, NOT assertions — a compile target, not a gate |

### Ordering, and why

1. **Close the last 7 typer errors in `gdx/src`.** Nothing downstream can run until this is 0.
2. **Then RefChecks runs for the first time** (§0.1) and a new error class appears — missing
   `override`, unimplemented members, variance. Expect the count to RISE here; that is the gate
   beginning to tell the truth, not a regression.
3. **Port `gdx/test` and run it.** JUnit 4 (`org.junit.Test`, `Assert.*`, one `Parameterized`).
   This converts the project from "compiles" to "verified" and is where the goal's second clause
   is actually met.
4. **Widen**: `gdx-backend-headless` first (15 files, no windowing), then lwjgl3, then
   `tests/gdx-tests` as a bulk compile target.

## Measured state

| metric | value | source |
|---|---|---|
| clean-compile errors (TYPER only — see §0.1) | **7** | `scripts/gdx_measure.sh` |
| portability (JVM-only APIs in emitted code) | **0** | `PortabilityCheck` |
| portability (injected replacements) | **clean** | `PortabilityCheck.inInjectedSource` |
| silent omissions | **31** (30 `super(args)` + 1; 0 anonymous-class members) | `OmissionCheck` |
| signature consistency | clean | `RewriteTrace.check` |
| substitutions verified removed | 10 dropped types | migration CHECK 1 + CHECK 2 |

Error breakdown: E007 4, E134 2, E120 1. Two of the seven now share a root cause, and it is worth
naming because it is the deepest remaining one:

**CONTEXT-DEPENDENT RAW FILL (`AssetManager:486`, `OrderedMap:285`).** The same Java type renders
differently depending on where it is read. `OrderedMap.keys` is declared `Array<K>`; inside the
STATIC nested `Entries` class `K` is not nameable, so the DECLARATION renders `Array[?]` — while a
use site re-renders Spoon's `Array<K>` with whatever `K` is in scope there. Two renderings of one
type, and only the declaration's is what the emitted Scala actually has. Same for
`AssetManager.loaders`, declared `[?, ?]` and read as `[T, P]` inside `setLoader<T, P>`.

The fix is to make a field READ carry the FIELD's declared rendering rather than re-render Spoon's
type in the reading scope — the same class of fix as `erasedFieldReceiver` (a node's `tpe` must be
what the emitted Scala has). It needs the field symbol's `info` to be reliably populated before
first use, which is the part to check first.

| where | what | standing |
|---|---|---|
| `AssetManager:486` | context-dependent raw fill, above | root cause identified |
| `OrderedMap:285` | context-dependent raw fill, above | root cause identified |
| `Skin:513` | RAW `new ReadOnlySerializer(){…}`; Scala infers `[Nothing]` where Java erased. A raw generic `new` must not be left to inference — unconstrained inference gives `Nothing`, which is never what Java meant | open |
| `ParticleEffectLoader:25` | RAW `new AssetDescriptor(…)`: no explicit type args to substitute (the applied form is fixed) | open |
| `CharArray:718` | a `ForEach` binding typed `T` in an `Object` formal. NOT mis-resolution to a primitive overload — that was tried and is inert | cause unknown |
| `NetJavaImpl:196` | a `java.util` value crossing from the JDK into retyped code. **DELIBERATE**: needs a conversion at the boundary (`.asScala`), not un-retyping; a CLASS of issue, not one site | by design |
| `RegionInfluencer:11` | nilary Java constructor vs Scala's implicit primary. **DELIBERATE**: needs a **discriminator-parameter funnel** — primary `(regionsCount: Int = 1, seedDefault: Boolean = false)` with every call site and `extends` clause updated, which `RewriteTrace` exists to verify | by design |

~~`DepthShader:111`~~ — **FIXED**, and it had been recorded as BLOCKED. Spoon really does not resolve
that reference to a `CtFieldWrite` under noClasspath — but the TIR already knew the symbol was an
instance member of an ancestor, and the emitter already knew which static names each companion
carries (`staticOwnersOf`, built for the export diamonds). **"The frontend cannot see it" is not
"we cannot see it."** Check what the TIR and emitter already know before recording a blocker.

### Where the engine ends and a library's manifest begins

Nothing in `core` / `frontend-spoon` / `scala-emit` may name a ported library. Swept
2026-07-28: the one violation was `ReflectionToPortableTransform`, which hard-coded
`com.badlogic.gdx.utils.reflect.ClassReflection` and its member list; it is now the general
`StaticForwarderTransform(List[Forwarder])` with the libgdx policy stated in `LibgdxCoreMigrate`.
The per-library seams are:

| seam | states | where libgdx's lives |
|---|---|---|
| `Substitutions` | types/methods not to emit, Scala to inject instead | `LibgdxCoreMigrate.subs` |
| `ClassTableTransform(Map)` | `forName` → an explicit name→class table | `LibgdxCoreMigrate.classTable` |
| `StaticForwarderTransform(List)` | wrapper statics that are plain members of arg 1 | `LibgdxCoreMigrate.unwrapReflection` |
| `corpus-tests/libgdx-overrides/**` | the injected Scala itself | that directory |

libgdx names DO appear in engine doc comments — `GL30Interceptor` witnessing the export diamond,
`Array<? extends T>` witnessing array covariance. Those are worked examples explaining why a
general rule exists, and drive no behaviour. Re-run the sweep with
`grep -rn --include='*.scala' -E "badlogic|libgdx" core frontend-spoon scala-emit | grep -vE ":\s*(\*|//)"`.

## 0. Anonymous class bodies — FIXED

**Found and fixed 2026-07-28.** `SpoonTir.ctorCall` translated a `CtConstructorCall` and never asked
whether the node was the `CtNewClass` SUBTYPE, so every Java anonymous class was emitted as a bare
constructor call with its BODY DISCARDED — **156 sites**, and every libGDX button silently did
nothing when clicked while the gate stayed green. Only the four `java.util.Comparator` sites failed
to compile, which is the only reason it was ever noticed.

All 156 are now emitted (`grep -c` over the Java corpus and over the emitted Scala agree exactly),
carrying 208 methods and the anonymous classes' fields:

```scala
private def initialize(): scala.Unit = {
  this.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled)
  this.addListener({
    this.clickListener = new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
      override def clicked(event: …InputEvent, x: scala.Float, y: scala.Float): scala.Unit = {
        if (Button.this.isDisabled()) { return } else ()
        Button.this.setChecked(!Button.this.isChecked$field, true)
      }
    }
    this.clickListener
  })
}
```

How it is modelled:

- `Tree.New` carries `anon: Option[Tree.AnonClass]`. `AnonClass` has the members and `dropped` —
  the names of any member the frontend could not carry, which `OmissionCheck.droppedAnonMembers`
  reports. `None` means "not an anonymous class"; `Some(Nil)` means `new Base(){}`, which is a
  DIFFERENT type from `new Base()` and still renders its braces.
- `StandardTraversal` descends into the body, so every transform reaches it; `Xref` indexes its
  declarations and usages, so `PortabilityCheck` and `RewriteTrace` see inside it too.
- The members are owned by a SYNTHETIC symbol — two listeners in one class both declaring `clicked`
  would otherwise intern to one symbol — but their bodies translate with `this` bound to the
  ENCLOSING class, because that is what Spoon reports for the implicit `this` of every enclosing
  member they reach. `TirEmitter.anonBody` pushes the synthetic symbol on `classStack`, so those
  render `Outer.this.m`: inside a Scala anonymous class a bare `this` is the anonymous instance,
  exactly as in Java.
- A `this` used as a VALUE and explicitly typed by Spoon as the anonymous class stays a bare `this`
  (`DragAndDrop`'s listener passes itself to `cancelTouchFocusExcept(EventListener, Actor)`).
- Anonymous methods carry `override` when they redefine an inherited member — Scala requires it for
  a concrete one (`ClickListener.clicked` has an empty body) and permits it for an abstract one
  (`Comparator.compare`), so marking every genuine override is both necessary and safe. 207 of 208
  are marked; the one that is not is `Window`'s `scrolled(InputEvent,float,float,int)`, which is
  dead upstream Java — `InputListener.scrolled` takes five parameters, so it overrides nothing.
- Captured locals need no lowering: Scala closes over them where javac had to synthesise
  constructor parameters. They are seeded by NAME into the nested translator so the xref resolves
  them to the real local rather than a `?var$x` stub.

Net effect on the gate: **37 → 33**. The four `Comparator` E007s and `NetJavaImpl:143` are gone;
two errors are NEWLY VISIBLE in code that had never been compiled (§0.2). Fifteen `E052
Reassignment to val` appeared and were then fixed at the cause — `MutableParamsTransform` walked
class bodies by hand and so never saw a method of an anonymous class, and libGDX reassigns
parameters inside listener bodies constantly (`ScrollPane`'s `deltaX = 0`, `Interpolation`'s
`a = a * 2`). It is now built on `StandardTraversal`, like every other transform.

`OmissionCheck` 30 → 31, still ALL `super(args) dropped` and **zero** dropped anonymous members.
The +1 is `Dialog` (2 → 3): a constructor whose `super(args)` replay was eligible only while the
parent's body was incomplete.

### 0.1 The error count is a TYPER-ONLY gate — RefChecks has never run

Measured, not inferred. dotty's `Phase.isRunnable` is `!ctx.reporter.hasErrors`, so **one** typer
error anywhere in the run skips every later phase for the WHOLE program — including `RefChecks`,
which is where missing-`override` (E164), unimplemented-member and variance errors are reported.

```scala
// A.scala
class TyperError { val x: Int = "not an int" }   // E007
// B.scala
class Base { def f(): Int = 1 }
class Sub extends Base { def f(): Int = 2 }      // E164 — NOT REPORTED
```
`scala-cli compile` on that pair reports **1 error**, not 2. The corpus is in exactly that state:
`Button.draw` overrides `Table.draw` with no `override` modifier and the gate says nothing.

Consequences:
- The 33 is a floor, not a total. **Expect a step up in errors the first time the typer count
  reaches 0**, and treat that as the RefChecks pass finally running, not as a regression.
- Emitting `override` correctly is currently UNVERIFIABLE through this gate. The anonymous-class
  shapes were therefore verified against a standalone scala-cli file that reproduces them (concrete
  supertype + `override`, abstract supertype + `override`, `Outer.this`, a captured local, and a
  nested anonymous class reaching the outer anonymous class's own member): it compiles clean, so
  RefChecks accepts them.
- The port emits `override` nowhere else. That is the next thing this gate will expose, and it is a
  whole-program job (`Symbol.flags.isOverride` is only populated for anonymous-class members).

### 0.2 Newly visible, in code that had never been compiled

- `Skin.scala:513` (E007) — Java's RAW `new ReadOnlySerializer() { … }` (Skin.java:602). Without a
  body Scala infers the type argument from the expected type; WITH one the anonymous class's type is
  fixed, and a raw use gives `ReadOnlySerializer[Nothing]`. Naming the argument does not help
  either: the body declares `Object read(…)`, written against the erasure, so it only overrides
  under the `Object` instantiation, and `Serializer[Object]` still does not conform to
  `Serializer[TintedDrawable]`. Java accepts it as an unchecked conversion; expressing that needs an
  argument-position cast whose target mentions the CALLEE's type variable, which is on the
  do-not-retry list. Left refused and reported here rather than approximated.
- `NetJavaImpl.scala:196` (E007) — `connection.getHeaderFields()` inside an anonymous
  `HttpResponse`. `CollectionsTransform` territory (`java.util.Map` vs `scala.collection.mutable.Map`),
  the same gap as the `NetJavaImpl:143` that this change removed.

### 0.3 Residual thin path

`SpoonTir.enumCase` reads an enum constant's anonymous body but collects only `CtMethod` members —
a field or instance-initializer block in a constant body would be dropped silently, with no
`OmissionCheck` finding (unlike the expression path, which records `dropped`). **Zero sites in this
corpus** (no enum constant in libGDX core has a class body at all), which is why it was left rather
than given its own TIR field.

## Remaining work, highest value first

### 1. Constructor funnelling — mostly DONE, 31 left
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

The 31 that remain, and why each is refused rather than approximated:

| class(es) | n | why |
|---|---|---|
| `GdxRuntimeException`, `SerializationException`, `ReflectionException` | 9 | parent is `java.lang.RuntimeException` — no body to replay, and `super(msg)` vs `super(msg, null)` differ in `Throwable`'s cause semantics (unset vs null), which no delegation reproduces |
| `DistanceFieldFont` (+`DistanceFieldFontCache`) | 8 | `BitmapFont()`'s nilary path loads the default Liberation Sans font from the classpath; replaying after it would do that I/O and leak a `Texture` |
| `RegionInfluencer$Single/$Random/$Animated` | 6 | `super` targets a VARARGS parent constructor; the argument is a single element, not a `Repeated` |
| `Dialog`, `Button`, `ScaleInfluencer`, `DepthShader$Config`, `FloatFrameBuffer` | 8 | prologue not superseded — the parent's nilary path does work the constructor does not re-do. `Dialog` went 2 → 3 when anonymous bodies started being emitted: one of its constructors was replay-eligible only while the parent's body was incomplete |

The remaining E120 ×5 are unrelated (4 × `GL*Interceptor` export collisions, 1 × `RegionInfluencer`,
whose nilary Java constructor delegates `this(1)` while two paramful roots leave nothing to promote).

### 2. Type-level residue — E007 11 + E134 6
Long tail of distinct patterns, none bigger than four. What is left:

- ~~**`java.util.Comparator` raw ×4**~~ — **FIXED by §0**; they were the anonymous-class omission
  surfacing, not a type-level problem.
- ~~**`Array[T]` vs `Array[Object]` ×3** and **capture through a BOUNDED wildcard receiver ×4**~~ —
  **FIXED** by `SpoonTir.arrayFormalCast`. Both were the same thing: Java arrays are covariant and
  erase their element type, Scala's are invariant, so `a.items` (an `Array[a.T]` capture) and a
  `T[]` in an `Object[]` formal are Java-legal conversions Scala rejects. The cast is driven by the
  DECLARATION's formal, never the reference's — a reference erases `T[]` to `Object[]` under
  noClasspath, and casting to `Array[Object]` is exactly what would break our own
  `addAll(Array[T], …)`. Type-variable identity is checked against the id its declaring type minted
  (`<owner>$$T`), not by name, so a callee's `<T>` can never bind to an unrelated in-scope one.
- ~~**`AssetDescriptor` ctor (applied form)**, **`IntMap`/`LongMap` `Keys`**, **`OrderedMap:233`**,
  **`ParticleControllerRenderer[AnyRef, AnyRef]`**~~ — **FIXED**, by three rules that all write out a
  conversion Java performed silently:
  - **The TIR must record the ERASED type.** `fieldAccess` cast a wildcard receiver to its erased
    view and then kept Spoon's un-erased type on the `Select`, so the TIR said `data.type : Class[T]`
    while the emitted Scala had `Class[Object]`. Every later rule that consults `t.tpe` therefore
    concluded there was nothing to convert. `erasedFieldReceiver` now returns the receiver view and
    the field's type through it TOGETHER — producing one without the other IS the inconsistency.
  - **`appliedCtorArgs`**: at a `new C<targs>(args)`, coerce each argument to the formal with C's own
    parameters replaced by the explicit type ARGUMENTS. Without the substitution the formal names a
    variable that exists only inside the callee, which is why `uncheckedGeneric` declines these — it
    would render a `?T` stub. `rawCtorArgs` is the raw counterpart.
  - **`uncheckedFrom`**: decide unchecked conversion on the RENDERED types too. Narrow by
    construction — same type constructor, same arity, every differing argument collapsed to `Object`
    or a wildcard. A subtype or an unrelated mismatch is not this shape.
  - **Wildcards in an `extends` clause take the parameter's DECLARED bound**, not `AnyRef`,
    resolving left to right so a later bound can name an earlier parameter (`T <: ParticleBatch[D]`);
    and super-constructor arguments get the same elimination as a cast, since the parent head and
    the parameter beside it are one raw type read in two positions.
- ~~`TextArea` inner-class supertype (E083 + E008)~~ — **FIXED**: an inner class of an ANCESTOR is an
  inherited member type, in scope by simple name. The projection was not merely verbose but illegal.

### 3. Re-derive the scene2d agent's rules — DONE
All nine rules from `worktree-agent-a3f9b81b6a4184e28` (HEAD `0223788`) were triaged one at a time
against the current engine. Six were ported and paid; three were rejected as already-covered or
inert. See "Rules ported / rejected" below.

### 4. Smaller clusters
E008 is down to ×1 (`TextArea` exporting `TextField.TextFieldClickListener`, a NON-static Java inner
class used as a supertype — the same cause as the one E083, `TextField#TextFieldClickListener` not
being an immutable path).

E120 is down to ×1: `RegionInfluencer`'s explicit nilary Java constructor clashes with Scala's
implicit primary. `CtorFunnel` nominates `RegionInfluencer(int)` as primary but the whole-program
fixpoint WITHHOLDS it, because three subclasses reach the class with an argument-free `extends`
clause. The nilary constructor cannot become the primary either — it opens with `this(1)`, which a
primary may not do. **No faithful single-primary encoding is known for this shape**; a default
argument does not express it (the nilary constructor has a body the paramful one does not), and
neither does inlining (`def this(int)` would then re-run the nilary body). Left as a compile error
DELIBERATELY rather than approximated — the compiler is a louder tracker than a silent omission.

~~4 × `GL*Interceptor` export collisions~~ — **FIXED**: exports now dedupe by DECLARING TYPE, so a
diamond (`GLInterceptor` and `GL30` meeting at `GL20`) drops the duplicate while a genuine
redeclaration (`GL31` shadowing `GL30`) keeps the most specific.
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

- **Broadening `erasedReceiverView` to fire on a RENDERED wildcard, and to consider the callee's
  RESULT type**: 7 → **41** (21 × E008 `not a member`). Casting a receiver to its erased view LOSES
  members — `Array[Object]` has no `com.badlogic…` members the code then calls. The erased view is
  only safe where the capture is genuinely unusable, which Spoon's own actuals are the right signal
  for. The context-dependent-fill problem it was aimed at needs the FIELD's declared rendering at
  the read, not a wider receiver cast.
- **`typeParamToObject` consulting the REFERENCE formal when the declaration is unavailable**:
  13 → 28. A reference erases a generic `T` to `Object` under noClasspath, so this casts our own
  `foo(x: T)` arguments to `Object`.
- **Disabling array covariance for a generic array formal** (`Arrays.copyOf(T[], int)`): 13 → 28,
  and again 10 → 26 in the "result shares the argument's type variable" form. A JDK shadow reports
  `T[]` while the real Scala-visible signature is `Object[]`; the erasure cast is right. What was
  wrong was the RESULT type we recorded — see `erasedResult`.
- **Casting a type-parameter argument to `Object` when the resolved formal is PRIMITIVE**: inert (no
  change). The reasoning is sound — a type variable can never denote a primitive, so Spoon
  mis-resolved the overload — but it fires nowhere, so `CharArray:718` has a different cause.

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
- **Binding an anonymous body's `this` to the anonymous instance in MEMBER-ACCESS position**:
  **+33 errors** (33 → 66, all `E008 value X is not a member of InputListener{…}`). Spoon reports
  the anonymous class as the target type of an implicit `this` whatever the member's real owner is
  — `List`'s key listener calling `setSelectedIndex`, which is declared on `List` — so this loses
  every enclosing-member access at once. Only a `this` in VALUE position may be rebound
  (`SpoonTir.thisOf`); as an access target the existing resolution, whose fallback is the bare name
  Scala resolves lexically exactly as Java did, is correct.
- **Treating an UNTYPED `this` inside an anonymous body as the anonymous instance**: same +33.
  Spoon leaves `CtThisAccess.getType` null for many implicit accesses, so `forall` (vacuously true
  on `None`) must not be used to decide this — require an explicit match on the anonymous class's
  qualified name.
- **Qualifying an enclosing ANONYMOUS class as `Outer.this`**: an anonymous class has no name, so
  the qualifier Spoon suggests (`Pixmap$1`) names nothing in the emitted code. Emit the reference
  bare; Scala resolves it lexically to that enclosing anonymous class's member, which is what Java
  resolved. (`SpoonTir.outerThis`, `isAnonymous` guard.)
- **`--js`-style reasoning about the error count**: the count is TYPER-only (§0.1). Do not conclude
  from a green or falling number that `override`/abstract-member/variance checking passed — dotty
  never ran `RefChecks` while any typer error remained.

## Guarantees that must not be weakened

Four checks run on every migration. They exist because silent correctness bugs (dropped
`static { … }` blocks; dropped `super(args)`; dropped anonymous-class bodies, §0) were found only by
accident, having compiled green for the entire project history. If a check starts failing, fix the
cause — do not relax the check.

**A check that reports zero is only as good as its coverage.** The anonymous-class omission (§0) was
green for the project's entire history because nothing looked for it. When adding a translation
path, add the corresponding check at the same time — and walk the tree with `StandardTraversal`
rather than a private recursion, so a node added later is covered for free. Two of the bugs fixed in
this pass (the omission itself, and `MutableParamsTransform` never seeing an anonymous class's
methods) were both hand-rolled traversals that stopped one node short.

**And the compiler is not a check you own.** §0.1: the error count is a TYPER-only measurement,
because dotty skips every later phase once the typer has reported anything. Whatever `RefChecks`
would say about this port is currently unmeasured.
