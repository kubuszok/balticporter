# libgdx-core port — measured state and remaining work

Scope: migrating libGDX's core module (`../sge/original-src/libgdx/gdx/src`, 605 types) through the
TIR into `libgdx-core/src/main/scala`. Distinct from GOAL.md's M6 phase (the xwiki cold port).

Reproduce everything below with `bash scripts/gdx_measure.sh` (re-emits, then compiles with
scala-cli 3.8.4). The migration itself prints four independent checks on every run.

## Measured state

| metric | value | source |
|---|---|---|
| clean-compile errors | **83** | `scripts/gdx_measure.sh` |
| portability (JVM-only APIs in emitted code) | **0** | `PortabilityCheck` |
| portability (injected replacements) | **clean** | `PortabilityCheck.inInjectedSource` |
| silent omissions | **30** | `OmissionCheck` |
| signature consistency | clean | `RewriteTrace.check` |
| substitutions verified removed | 10 dropped types | migration CHECK 1 + CHECK 2 |

Error breakdown: E007 39, E134 14, E051 8, E049 6, E008 6, E120 5, E050 2, E083 1, E006 1,
+ 1 uncoded.

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

### 2. Type-level residue — E007 39 + E134 14
Long tail of distinct patterns, no single dominant cause left. Known shapes: `classOf[X[?]]` vs
`Class[X[T]]`; raw JDK types needing a concrete argument (`Comparator` → `Comparator[Pixmap]`);
raw rendering that differs between a field's declaration scope and a method's (name-directed raw
fill picking up same-named method type params — a real fix makes raw rendering scope-independent).

### 3. Re-derive the scene2d agent's rules — E051 8
Branch `worktree-agent-a3f9b81b6a4184e28` (HEAD `0223788`) measured 151→107 with nine engine rules,
including shadowed-implicit call qualification and qualified `Outer.this`. Its engine was superseded
by the g3d agent's (which measured 86), so those rules are **not** in the current engine. E051 8 is
the visible cost. The branch is preserved; re-derive on the current engine.

### 4. Smaller clusters
E049 ×6 (multiple-inheritance ambiguity), E008 ×6 (CollectionsTransform API gaps — `Iterator.remove`,
`Map.entrySet`, `+=` on a mapped collection; each needs a semantic mapping decision), E050 ×2
(local variable shadows a method name; Java has separate namespaces, Scala does not).
E171 (parent-needs-args ctor) is **gone** — constructor funnelling fixed all four.

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

## Guarantees that must not be weakened

Four checks run on every migration. They exist because two silent correctness bugs (dropped
`static { … }` blocks; dropped `super(args)`) were found only by accident, having compiled green
for the entire project history. If a check starts failing, fix the cause — do not relax the check.
