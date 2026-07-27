# libgdx-core port — measured state and remaining work

Scope: migrating libGDX's core module (`../sge/original-src/libgdx/gdx/src`, 605 types) through the
TIR into `libgdx-core/src/main/scala`. Distinct from GOAL.md's M6 phase (the xwiki cold port).

Reproduce everything below with `bash scripts/gdx_measure.sh` (re-emits, then compiles with
scala-cli 3.8.4). The migration itself prints four independent checks on every run.

## Measured state

| metric | value | source |
|---|---|---|
| clean-compile errors | **86** | `scripts/gdx_measure.sh` |
| portability (JVM-only APIs in emitted code) | **0** | `PortabilityCheck` |
| portability (injected replacements) | **clean** | `PortabilityCheck.inInjectedSource` |
| silent omissions | **266** | `OmissionCheck` |
| signature consistency | clean | `RewriteTrace.check` |
| substitutions verified removed | 10 dropped types | migration CHECK 1 + CHECK 2 |

Error breakdown: E007 37, E134 14, E051 8, E120 6, E049 6, E008 6, E171 4, E050 2, E083 1,
E006 1, + 1 uncoded.

## Remaining work, highest value first

### 1. Constructor funnelling — CORRECTNESS, largest known gap
`OmissionCheck` reports **266 constructors** whose `super(args)` is silently discarded. The emitter
rewrites a leading `super(…)` to `this()`, which is correct for a no-arg super and **lossy**
otherwise: `new DelayedRemovalArray(16)` builds an empty array and compiles cleanly.

Scala secondary constructors cannot call `super` at all — only the primary can — and
`DelayedRemovalArray` alone targets eight distinct parent overloads, so no single primary reaches
them all. A faithful fix parameterises the primary to reach each targeted parent constructor.
**The BIR path already has `CtorPlan` for exactly this; the TIR path has nothing** — port it.

Related, same area: E120 ×6 duplicate no-arg constructor (`Stage`, `RegionInfluencer`) — a class
whose Java no-arg ctor delegates `this(...)` cannot become Scala's primary without changing what
runs on every instantiation. Needs a synthesized private marker-param primary threaded through
every `extends` clause.

### 2. Type-level residue — E007 37 + E134 14
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
`Map.entrySet`, `+=` on a mapped collection; each needs a semantic mapping decision), E171 ×4
(parent-needs-args ctor — folds into item 1), E050 ×2 (local variable shadows a method name; Java
has separate namespaces, Scala does not).

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

## Guarantees that must not be weakened

Four checks run on every migration. They exist because two silent correctness bugs (dropped
`static { … }` blocks; dropped `super(args)`) were found only by accident, having compiled green
for the entire project history. If a check starts failing, fix the cause — do not relax the check.
