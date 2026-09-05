# Baltic Porter — progress

**The state of every port, and of publishability.** This is the only status document; the design and
the reasons are `DESIGN.md`, the measured dead ends are `ENGINE-LIMITS.md`, the rules are `CLAUDE.md`.

Nothing here is history. A remaining-work list is maintained by **deletion** — a done item is removed,
not moved to a "done" section — and a lesson worth keeping is lifted into `DESIGN.md`,
`ENGINE-LIMITS.md`, `CLAUDE.md`, a skill or an agent brief rather than left in a status line
(`CLAUDE.md` §3.6).

---

## 0. Reproducing every number here

Every lane is a recipe in the root `Justfile` (`just` with no argument lists them); the full lane
table, the baselining mechanism (`baseline/expected-errors*`, `expected-lost`, `tests.tsv`,
`findings.tsv`, `port-map.tsv`) and the `just baseline-accept` / `just debug-*` tooling are
`CLAUDE.md` §5. Run lanes serially — `just measure-all` does — and read `run-latest/` against the
committed `baseline/` after every run, never the headline alone.

---

## 1. Corpus inventory

A port's name is its **destination** module's id in the reference port (`../sge/build.sbt`,
`../ssg/build.sbt`), never the upstream library's (`CLAUDE.md` §2.1). `port-report/<X>/` is keyed on
the migrator class name, which does not track a module rename.

| port (migrator) | upstream | licence | compile (JVM/JS/Native/ref) | tests |
|---|---|---|---|---|
| `sge` (`LibgdxCoreMigrate`) | libGDX `gdx/src` | Apache-2.0 | 0/0/0/51 | — |
| `sge-test` (`LibgdxTestMigrate`) | libGDX `gdx/test` | Apache-2.0 | 0/0/0/51 | 191: 184 pass, 7 fail (expected-lost 30) |
| `sge-ecs` (`AshleyMigrate`) | Ashley `ashley/src` | Apache-2.0 | 0/0/0/0 | — |
| `sge-ecs-test` (`AshleyTestMigrate`) | Ashley `ashley/tests` | Apache-2.0 | 0/0/0/0 | 112: 108 pass, 2 fail, 2 skipped |
| `sge-ecs` drop-in (`AshleyDropIn`) | — | — | 408/408/408 (JVM/JS/Native) | 0/0/0 pass — expected red until parity, not in `measure-all` |
| `sge-anim8` (`Anim8Migrate`) | anim8-gdx | Apache-2.0 | 0/0/0/6 | 23 hand-written, all passing |
| `sge-gltf` (`GltfMigrate`) | gdx-gltf `gltf/src` | Apache-2.0 | 0/0/0/0 | — |
| `sge-gltf-test` (`GltfTestMigrate`) | gdx-gltf `gltf/test` | Apache-2.0 | 3/3/3/3 (§8.4, classified) | expected-lost 0 |
| `sge-screens` (`ScreensMigrate`) | libgdx-screenmanager | Apache-2.0 | 0/0/0/29 | 16 hand-written, all passing |
| `sge-vfx` (`VfxMigrate`) | gdx-vfx | Apache-2.0 | 0/0/0/2 | 64 hand-written, all passing |
| `sge-ai` (`GdxAiMigrate`) | gdx-ai `gdx-ai/src` | Apache-2.0 | 0/0/0/9 | — |
| `sge-ai-test` (`GdxAiTestMigrate`) | gdx-ai `gdx-ai/tests` | Apache-2.0 | 0/0/0/9 | 10: 10 pass, expected-lost 0 |
| `sge-ai-diff` (`GdxAiDifferential`) | hand port's own suite, run against emitted `sge.ai.*` | — | 0 | 95: 93 pass, 2 declared (§10.7.12) |
| `sge-textra` (`TextraTypistMigrate`) | TextraTypist | Apache-2.0 + MIT | 0/—/—/3 | no upstream `@Test` |
| `sge-textra-diff` (`TextraTypistDifferential`) | hand port's own suite, run against emitted `sge.textra.*` | — | 0 | 165: 165 pass, 0 declared |
| `sge-graphs` (`SimpleGraphsMigrate`) | simple-graphs `src/main` | MIT | 0/0/0/0 | — |
| `sge-graphs-test` (`SimpleGraphsTestMigrate`) | simple-graphs `src/test` | MIT | 0/0/0/0 | 16 pass, expected-lost 0 |
| `sge-noise` (`Noise4jMigrate`) | noise4j `src` | Apache-2.0 | 0/0/0/0 | none upstream |
| `sge-jbump` (`JbumpMigrate`) | jbump `jbump/src` | Apache-2.0 | 0/0/0/0 | none upstream — differential probe only |
| `ssg-liquid` (`LiqpMigrate`) | liqp `src/main/java` | MIT | 0 | — |
| `ssg-liquid-test` (`LiqpTestMigrate`) | liqp `src/test/java` | MIT | 0/0/0/3 | 637: 636 pass, 1 fail (declared, K18/K5.7), expected-lost 2 |
| `ssg-md` (`FlexmarkMigrate`) | flexmark-java core + 11 `flexmark-util-*` | BSD-2 | 0/0/0/136 | — |
| `ssg-md-test` (`FlexmarkTestMigrate`) | flexmark's own suite (3 upstream modules) | BSD-2 | 0/0/0/136 | 727: 725 pass, 2 fail (attributed, K18.1/K5.7), expected-lost 1 |
| `ssg-md` extensions (`FlexmarkExtMigrate`) | flexmark's 29 covered extension modules, one dependent port | BSD-2 | 0/0/0/187 | — |
| `ssg-md-ext-test` (`FlexmarkExtTestMigrate`) | same, its suite | BSD-2 | 0 | 188 pass, expected-lost 0 |
| USL (`UslMigrate`) | VisUI's skin-DSL, sibling port, no reference hand port | Apache-2.0 | 0/0/0/3 | — |
| USL test (`UslTestMigrate`) | 7 `@Test` over checked-in `.usl`/`.json` pairs | Apache-2.0 | 0/0/0/3 | 8: 6 pass, 1 ignored, expected-lost 0 |
| `sge-visui` (`VisUiMigrate`) | VisUI `ui/` | Apache-2.0 | 7/7/7/7 (§10.9.10, attributed) | — |
| `sge-visui-diff` (`VisUiDifferential`) | hand port's own suite, run against emitted `sge.visui.*` | — | 0 | 50 pass, 0 declared |

Every non-zero JVM count above is fully attributed in that port's residue list below; `-ref` counts
under `-Werror` are warnings-as-errors and are not typer errors.

### 1.1 Available and unported (sge/ssg modules with no Baltic Porter port yet)

`sge-colorful`, `sge-tools`, `sge-controllers`, `sge-freetype`, `sge-physics*`, `sge-jvm-platform-*`.
None ported by this engine; each needs its own scoping pass (`CLAUDE.md` §2, `add-corpus-library`
skill) before assignment. `sge`'s own 100 absent upstream types and the backend question are a
platform decision, not an engine gap.

Attribution gaps outstanding: `sge`'s repository-level `THIRD-PARTY-LICENSES` and `ssg`'s `NOTICE`
are still hand-maintained and incomplete for the modules this engine has not touched (§1's own
per-file headers and `provenance.notices` close the gap for ported files only, `CLAUDE.md` §4.57).

### 1.2 Suggested assignment order

1. `sge-colorful` — large, with a named redesign (`AsyncTask`-shaped: confirm it is actually one
   construct before writing a shared rule, per §10.9.4's finding).
2. `sge-tools`, `sge-controllers`, `sge-freetype`, `sge-physics*`, `sge-jvm-platform-*` — deferred,
   scope unclear or deliberately partial upstream coverage.

---

## 2. libGDX — the spearhead port

`com.badlogic.gdx.* → sge.*`, Apache-2.0. Two ports: `sge` over `gdx/src`, and `sge-test` over
`gdx/test` as a **dependent** of it, inheriting its manifest.

### 2.1 Current state

| gate | `sge` | `sge-test` |
|---|---|---|
| compile errors (JVM/JS/Native/ref) | 0/0/0/51 | 0/0/0/51 |
| files emitted | 598 (12 dropped, 7 injected) | 29 |
| omissions | 66 | 3 |
| portability (all/emitted/injected) | 151/151/2 | 166/15/0 |
| collection closure/boundary | 2/0 | 0/0 |
| context seams (boundary+warning) | 44 = 19+25 (§11.12) | 1 × self-supplied |
| trivia lost/recovered/deliberate | 0/4/12 | 0/0/0 |
| tests | — | 191: 184 pass, 7 fail, expected-lost 30 |

All standing failures are `expected#derived`: stacks reaching `com.badlogic.gdx.utils.Json`
(dropped in favour of a codec-backed replacement) or `CharArray`→`DynamicArray[Char]` retargeting
(`DynamicArray.toString` differs from java's string-concat, `fdc30967`). None declared/undefended.

### 2.2 Residues, named

- Omissions 66: dominated by `super(args) dropped` where roots reach different parent constructors
  (`DistanceFieldFont` 7, `OrderedSet`/`OrderedMap`/`IdentityMap`/3×`RegionInfluencer` 3 each,
  `Button` 2, 3 singles) — irreducible without a per-site synthesis, `ENGINE-LIMITS.md` C8. Plus
  `BitmapFont()` nilary-constructor delegation loss (`ENGINE-LIMITS.md` C11), refuse-and-count.
- Trivia: 100 of 4,565 comments have nowhere to go (a construct the emission consumes), `ENGINE-LIMITS.md` §10.
- Collection closure 2: both `java.util.concurrent`, downstream of a portability decision not yet made.
- Portability 151: all inside manifest-dropped types; `portability(emitted)` is the number to watch.
- The port covers no backend; the 191-test suite is JVM-only (MUnit under scala-cli).

### 2.3 Do NOT retry

| tried | measured | entry |
|---|---|---|
| erasing DECLARATIONS (raw → `Object`-parameterised) | +277 | G1 |
| rendering an overriding method's return type from the PARENT's declaration | 162 → 438 | G5 |
| raw-fill knob settings (inherited fill × `?`/`Object` fallback) | 162/97/87/1 | G2, G4 |
| sge's `[?]`-everywhere rendering, adopted wholesale | 1 → 11 | G2 |
| ordinary name-directed fill restricted to nested types | 1 → 14/19 | G4 |
| "mentions" guard on the inherited fill | 145 / 1, union re-admits the failing entry | G4 |
| fill applied to signature but not body | 1 → 20 | G2 |
| `rawCtorArgs` erased-formal fallback, three gates | 2→23, 1→5, 1→43 | G13 |
| broadening `erasedReceiverView` to wildcard/result type | 7 → 41 | G11 |
| `typeParamToObject` consulting the REFERENCE formal | 13 → 28 | G14 |
| disabling array covariance for a generic array formal | 13→28, 10→26 | G14 |
| casting a type-parameter argument to `Object` at a PRIMITIVE formal | inert | G16 |
| unconditional bound-erasing of a callee formal | +47 | G13 |
| promoting a paramful constructor to primary with no whole-program check | +14 | C1 |
| inlining a promoted constructor's body without renaming what it declares | field collisions | C2 |
| tightening `supersedes` to inspect assignment right-hand sides | removes no effect, costs the argument | C6 |
| qualified `Outer.this` without a static/supertype guard | +22 | T4 |
| binding an anonymous body's untyped `this` to the anonymous instance | +33 to +66 | T2 |
| qualifying an enclosing ANONYMOUS class as `Outer.this` | names nothing emitted | T3 |
| name-directed fill gated on `resolveTypeParam` instead of the barrier-aware frame | +2 | G15 |
| qualifying an implicit access to an inherited instance field as `this.f` | 0 change, code never runs | T5 |
| `--js` compile as a portability gate | proves nothing — only the linker rejects | P1 |
| reasoning about the error count as complete before it reaches 0 | typer-only until 0 | M1 |

---

## 3. Ashley — the dependent port

`com.badlogic.ashley.* → sge.ecs.*`, Apache-2.0. Dependent of `sge`: resolves against libGDX core's
Java, compiled with the base's emitted Scala on one invocation (`RuntimeMode.Dependency`).

### 3.1 Scope

`ashley/src` (21 files) and `ashley/tests` (18 files, 112 `@Test`). Excluded: `benchmarks/` (Artemis
comparison harness, not library surface) and `tests/src` (13 demo apps needing an unported backend).

### 3.2 Current state

| gate | `sge-ecs` | `sge-ecs-test` |
|---|---|---|
| compile errors (JVM/JS/Native/ref) | 0/0/0/0 | 0/0/0/0 |
| files emitted | 21 (0 dropped, 2 injected) | 18 |
| omissions | 1 | 2 |
| portability (all/emitted/injected) | 151/0/4 | 153/2/0 |
| trivia lost/recovered/deliberate | 0/0/0 | 0/0/0 |
| tests | — | 112: 108 pass, 2 fail, 2 skipped |
| drop-in (`AshleyDropIn`, not in `measure-all`) | 408 errors JVM/JS/Native, 0 tests run — expected red until parity | |

### 3.3 Manifest inheritance

`LibgdxPolicy.core(...).extendedBy(...)`: `dropTypes`/`dropMethods`/`packageRenames`/signature phases
inherited; `inject` is not (each replacement file ships from exactly one module). Ashley's own rename:
`…ashley.core → sge.ecs` + `…ashley → sge.ecs`. Three seams for the base's reflection substitution:
`TypeRedirectTransform(ReflectionPool → ComponentPool)`, `MethodBodyTransform` on
`Engine#createComponent(Class)` (the one reflective call site), and `PortMapTransform.forBases("sge")`
run **last** (reads the base's actual emitted surface; run first it over-reports). One inherited drop
(`ImmutableArray#toArray(Class)`, dangling on `Array.toArray(Class)`) is now Ashley's own
`dropMethods` entry — found by the orphaned-call check, visible only from the dependent.

### 3.4 Residues, named

- `EngineTests.createPrivateComponent` fails — comparison failure, anchored `assert-site`
  `EngineTests.java:970`, not diagnosed.
- 2 tests skipped (`familyListenerPriority`, `componentHandlingInListeners`): Ashley's
  `mockito-all 1.10.19` uses `org.mockito.asm`/cglib `setAccessible(ClassLoader.defineClass)`, which
  throws `ExceptionInInitializerError` on a module JDK — an `Error`, not `NonFatal`, so MUnit aborts
  the suite. Deliberate baselined-skipped state; blocked on replacing the mocking dependency or a JDK
  where mockito 1.x's cglib works. (The measure-lane defect this exposed — MUnit's third terminal
  marker, `==> s … skipped`, going uncounted — is fixed and lifted to `CLAUDE.md` §5.1.)
- 1 + 2 omissions, baselined and stable.
- Drop-in parity (`ecs-dropin`) is open: 408 errors on all three platforms, 0 tests run.

---

## 4. simple-graphs

`space.earlygrey.simplegraphs → sge.graphs`, MIT (upstream is MIT; the reference hand port's headers
say ISC — this port states MIT as the derived-work authority, `CLAUDE.md` §3.5). Driven from
`.conf` (`balticporter/corpus/ports/simplegraphs/{main,test}.conf`), no §1(c) rules at all.

### 4.1 Current state

| gate | `sge-graphs` | `sge-graphs-test` |
|---|---|---|
| compile errors (JVM/JS/Native/ref) | 0/0/0/0 | 0/0/0/0 |
| files emitted | 33 (29 upstream units) | 7 |
| omissions | 2 | 0 |
| portability (all/emitted) | 6/6 | 6/0 |
| tests | — | 16 of 16 passing |

### 4.2 Residues, named

- 2 omissions: `DirectedGraph`/`UndirectedGraph`, several roots reaching different parent overloads
  (no single-primary encoding, `ENGINE-LIMITS.md`), neither reachable from a passing test.
- `collection-boundary` 2 rows on `Path#removeAll`/`retainAll` describe a seam the port does not
  emit (`Array` was retyped to extend the shim, so `super.removeAll` already binds to the shim's
  own member) — not an `accept-external-callee` candidate, left open by design (`CLAUDE.md` §1,
  §4.56 K2.5).
- Three `overload-risk` rows deliberately left unresolved: two `super.remove(…)` in `Path` (T17,
  superclass's own candidate set), one `MinimumWeightSpanningTree.addVertices` (fixed-arity beside
  vararg, one candidate applicable).
- 14 non-local `return`-inside-`for` sites: compile under 3.8, deprecated — forward-compat item.
- Three per-location remedy selections active (`DESIGN.md` §8.16): `Graph#addVertices(V[])`
  `acknowledge`, `Array#remove(Object)` `ascribe-javac-choice`, `Node#removeEdge(Node)`
  `accept-risk` — drained `heap-pollution 0`, `overload-risk 3`, `remediation(resolved) 5`.

### 4.3 Do NOT retry

| tried | measured | why |
|---|---|---|
| `java.util.Collection` → `mutable.Buffer` while `AbstractCollection` → the shim | 13 of 20 errors | Java's abstract base IMPLEMENTS the interface; the two must share a family |
| mapping `Collection` to the shim while bridging ARGUMENTS only | libGDX test port 0 → 3 | a declared slot is an expected type exactly as a formal is |
| guarding the Scala-shaped rewrite table per-rewrite instead of blanket-refusing on a shim receiver | 0 → 2 | `add`→`+=`/`addAll`→`++=` survived the guard |
| appending `$e` to the ESCAPED for-each name | libGDX main 0 → 3 (E040) | `` `object`$e `` is not an identifier; escape the WHOLE name |
| rewriting `Stream#filter` on method name alone | libGDX test 0 → 1 | `"…".lines()` is a Stream with no collection behind it |
| leaving the collapsed stream node typed `Stream<E>` | `Found: Buffer[V] / Required: JavaCollection[V]` | a rewritten node must be typed as what it EMITS |
| `Collectors.toSet`/`toMap`/`unmodifiableList` mapped approximately | not attempted, deliberately | each needs a different target type; a copy and an identity both compile while wrong |

---

## 5. noise4j

`com.github.czyzby.noise4j.{map,array} → sge.noise`, Apache-2.0. No upstream test suite. One §1(b)
phase (`mutable-params`); no §1(c) rules.

### 5.1 Current state

| gate | `sge-noise` |
|---|---|
| compile errors (JVM/JS/Native/ref) | 0/0/0/0 |
| files emitted | 12 (0 dropped, 0 injected) |
| omissions | 3 (all `Object2dArray`) |
| portability | 0 |
| tests | none exist upstream — `just noise4j-measure` asserts `@Test in Java: 0` |

Deliberately keeps `java.util` (`CollectionsTransform` not run): with it on, compile errors drop
2→1 but `DungeonGenerator.removeDeadEnds` throws at run time (`Iterator.remove()` has no Scala
counterpart) — measured trade documented, not retried.

### 5.2 Residues, named

- 3 omissions, all `Object2dArray`: the constructor funnel's synthesised primary runs
  `this.array = getArray(width*height)` on paths Java did not run it on (wasted allocation + a
  virtual call to an abstract method during construction). Refusing the promotion costs 0→41 on
  libGDX, so this is reported rather than avoided (`ENGINE-LIMITS.md` C7).
- 2 `for`-over-JDK-`Iterable` errors would appear if `collections` were turned on (K9) — open design
  question, not a lowering bug: needs a structural (not name-based) test for which iterables support
  Scala `foreach`.
- Assignment-as-value re-evaluates its LHS (`return grid[toIndex(x,y)] = value`) — 7 sites here, all
  with a pure index so harmless today; the compound form (`a[i++] = v`) would double-increment
  silently elsewhere. `ENGINE-LIMITS.md` §9.
- A Java enum with no members (`enum Bare { A, B; }`) crashes the frontend (NPE in `superTypes` →
  `originOf`, no source buffer). Not on any port's path; noted, not fixed.

### 5.3 Do NOT retry

| tried | measured | why |
|---|---|---|
| `{ transform = "collections" }` in the manifest | 2→1 error, `Iterator.remove()` becomes a `throw` | the JDK forms this library uses have no Scala-collection counterpart |
| switching the emitter's `ForEach` to the iterator protocol universally | not attempted, deliberately | would break every port that DOES run `collections` and move every foreach digest in libGDX; `ENGINE-LIMITS.md` K9 |

---

## 6. jbump

`com.dongbat.jbump → sge.jbump`, Apache-2.0 (upstream is Apache-2.0; the reference hand port's
headers say MIT — this port states Apache-2.0 as the derived-work authority, `CLAUDE.md` §3.5). No
upstream test suite; conf-only port, no §1(c) rules, nothing dropped or injected.

### 6.1 Current state

| gate | `sge-jbump` |
|---|---|
| compile errors (JVM/JS/Native/ref) | 0/0/0/0 |
| files emitted | 23 (19 upstream + 4 vendored runtime) |
| omissions | 15 (all C7 promoted-ctor replay, §6.3) |
| portability | 0 |
| behaviour | 44 transcript lines, byte-identical to upstream Java (`probe/{Probe.scala,ProbeJava.java}`, §6.2) |

`sge-jbump` covers 19 of 19 upstream types with nothing dropped or injected, against the reference
hand port's 14 of 19 (it lacks `Extra`, `util/{IntIntMap,IntArray,FloatArray,BooleanArray}` and
upstream's 352-line `util/MathUtils`, whose name it reuses for `Extra`'s members; `Collisions` drops
`Comparator<Integer>`/`compare`/`order`/3 of 4 `keySort` overloads for a `boxed`/`applyPermutation`
redesign) — all of it ports mechanically here.

### 6.2 The gate — a differential PROBE, not a ported suite

jbump ships zero `@Test`; its only upstream test module is a runnable libGDX demo needing an unported
backend, and the reference port's 32 tests were written in Scala against a redesigned API, so there
is nothing to convert. The gate is a hand-written probe: the same scenario run against the emitted
Scala and against upstream Java, transcripts diffed line for line, Java as the authority (no
expectation is ever written down wrong). Negative-tested (perturbing one `println` fails the lane).
Not covered: non-axis-aligned ray queries, `tileMode = false`, `Response.bounce` off a corner,
`IntIntMap`'s stash path under adversarial keys, `World.check` with a filter returning `null` for
some pairs.

### 6.3 Residues, named

- 15 omissions, all `promoted constructor body runs on every path` (five each in `BooleanArray`,
  `FloatArray`, `IntArray`) — every later constructor overwrites both statements, so final state is
  identical on every path; the probe demonstrates this rather than assuming it. `ENGINE-LIMITS.md` C5/C7.
- `World`'s raw `Item` parameters emit `Item[?]` throughout (G2, matches the reference port) — not a
  defect, a surface question for a consumer holding `Item[Entity]`.
- The probe could cover more: ray/filter/stash edge cases listed above.

### 6.4 Do NOT retry

| tried | measured | why |
|---|---|---|
| reading `class BooleanArray {` and concluding the promoted nilary body was dropped | wrong — body is emitted AFTER the secondaries, `BooleanArray().items` is 16 | a Scala class body need not precede the constructors it runs before; run the code, don't eyeball it (`CLAUDE.md` §5.1) |
| a spec for the captured-local rename over a method-LOCAL named class | frontend refuses: `unsupported construct: statement CtClassImpl` | `ENGINE-LIMITS.md` T9, zero sites in the corpus |

---

## 7. anim8-gdx — the port whose difficulty is per LINE

`com.github.tommyettinger.anim8 → sge.anim8`, Apache-2.0. Dependent of `sge` (`RuntimeMode.Dependency`,
compiled with libGDX core's emitted Scala on one invocation). 16 files, 19,594 lines, dominated by
huge constant data (`ConstantData`, `PaletteReducer`) and bit-pattern arithmetic (`OtherMath`).

### 7.1 Scope

`src/main/java` (16 files) only. `src/test/java` (20 files) is excluded: zero `@Test` annotations —
every file is an `ApplicationAdapter` demo/bench needing an unported backend. No `Anim8TestMigrate`;
instead `ported/sge-anim8/src/test/scala` holds 23 hand-written MUnit tests adapted from the
reference hand port's four suites.

### 7.2 Current state

| gate | `sge-anim8` |
|---|---|
| compile errors (JVM/JS/Native/ref) | 0/0/0/6 |
| files emitted | 16 (0 dropped, 0 injected) |
| omissions | 24 (all C7 promoted-ctor replay: 8 each in `PaletteReducer`, `QualityPalette`, `FastPalette`) |
| portability (all/emitted/injected) | 263/112/0 |
| context seams | 5, all `unconstructed-thread` warnings (PNG writers, §11.12) |
| trivia lost/recovered/deliberate | 0/0/0 |
| tests | 23 of 23 passing (4 files, hand-written) |

### 7.3 Residues, named

- `portability(emitted)` 112, all `java.util.zip` (`DeflaterOutputStream`/`Deflater`/`CRC32`/
  `CheckedOutputStream`) — not an engine gap, PNG is DEFLATE and there is no portable substitute;
  the reference port made the same call (`ENGINE-LIMITS.md` P3). Not dropping `ChunkBuffer`: a port
  without PNG chunk framing has dropped PNG on the JVM target this port supports.
- `portability(all) − (emitted)` 151 is the base's (libGDX core's own), seen through the resolution
  root — not this module's residue.
- 24 omissions, C7, known and stable.
- Behavioural coverage is 23 tests over 4 of 16 types; `PNG8`/`AnimatedGif`/`PaletteReducer`/
  `LZWEncoder` (16,700 of 19,594 lines) are covered only by compilation — an end-to-end
  encode/decode assertion needs a `Pixmap` (`Gdx2DPixmap`, JNI), real work rather than a missing test.
- A second source set for the demos needs an unported libGDX backend; not planned.

### 7.4 The reference port is measurably WRONG here — `CLAUDE.md` §3.5, in the other direction

sge's `sge-anim8` externalised `ConstantData`'s four blobs into `.bin` classpath resources holding
the **UTF-8** encoding of the characters, not `getBytes(ISO_8859_1)`: `ENCODED_SNUGGLY` should be
32,768 bytes (it ships 47,006) and `TRI_BLUE_NOISE`/`_B`/`_C` should be 4,096 (it ships 6,143) — three
independent confirmations (Java's own escape decoding, upstream's javadoc "4096-element … 64x64
grid", and `ENCODED_SNUGGLY`'s palette-mapping shape elsewhere in the library being `byte[0x8000]`).
sge's own `DataEmbeddingRedSuite` pins the wrong values. This port's `ConstantDataSuite` pins the
numbers from the independent oracle and keeps upstream's own in-source embedding (also the form that
works on Scala.js and Native). A reference port that SOLVED a problem is not automatically a model.

### 7.5 Manifest inheritance

Four fields (`name`, `governs`, `packageRenames`, `surface = [PortMapTransform.forBases("sge")]`);
everything else inherited from `LibgdxPolicy.core(...).extendedBy(...)`, `ManifestAgreement` reports
0. Rename is additive (longest-prefix-wins keeps `com.badlogic.gdx → sge` and
`com.github.tommyettinger.anim8 → sge.anim8` apart); `forBases` runs last; `inject` is empty (libGDX
core ships the replacements for types *it* dropped, per §1.5's asymmetry). Not conf-driven: no
`libgdx/main.conf` exists (`LibgdxPolicy` is Scala because `ClassTableTransform`/
`StaticForwarderTransform`/`GdxSharedIteratorRule` are behaviour, not data) — converting anim8 to
`.conf` needs libGDX's own conf first, no new mechanism.

### 7.6 Do NOT retry

- Do not route the enum lowering through `CtorFunnel` — plans nothing for an enum, the primary
  parameter list vanishes from every emitted enum (`ENGINE-LIMITS.md` T10).
- Do not fix the `name`/`Enum.name()` collision by renaming the parameter — needs a §4.55 pass that
  can see an emitter-synthesised member, which no phase can today; skip taken (`ENGINE-LIMITS.md` T11).
- Do not trust `sge-anim8`'s `ConstantData` values (§7.4) — they are UTF-8 lengths.

---

## 8. gdx-gltf — the port that measures a DEPENDENT's seams

`net.mgsx.gltf → sge.gltf`, Apache-2.0. 135 files / 11,307 lines, the corpus's first genuine
third-party extension stacked on libGDX's 3D pipeline (deep inheritance: `PBRShader extends
DefaultShader extends BaseShader`, sixteen `Attribute` subclasses, `ModelInstanceHack`/
`AnimationControllerHack` reaching into libGDX's protected/private constructor state). Compiled with
libGDX core's Scala on one invocation.

### 8.1 Scope

`gltf/src` (135 files). Excluded: `demo/` (35, needs an unported backend) and `ibl-composer/` (25, a
VisUI authoring tool). `gltf/test` holds 7 files / 8 `@Test`, all in one file (the other six are
window-opening demos); `GltfTestMigrate` names that one file rather than globbing.
`ported/sge-gltf/src/test/scala` adds 22 hand-written MUnit tests over `GLTFTypes` (pure enum-mapping
functions, no GL context).

### 8.2 Current state

| gate | `sge-gltf` |
|---|---|
| compile errors (JVM/JS/Native/ref) | 3/3/3/3 (§8.4, all classified) |
| files emitted | 135 (0 dropped, 1 injected) |
| omissions | 12 |
| portability (all/emitted/injected) | 151/0/0 |
| context seams | 7, all `unconstructed-thread` warnings (§11.12) |
| trivia lost/recovered/deliberate | 0/4/0 |
| tests | 8 ported + 22 hand-written = 30, **none run — the port does not compile** |

### 8.3 The residue — 3 errors, all classified

| errors | site | classification |
|---|---|---|
| 2 | `PBRCubemapAttribute`, `PBRTextureAttribute` — `extends CubemapAttribute`/`TextureAttribute` with no arguments | D4: two roots reach two DIFFERENT parent constructor overloads; a Scala `extends` clause reaches only the primary, so this is confirmed as a genuine wall (`CtorFunnel.Plans` fixpoint over a dependent's whole program disagrees with the base's own run), not a seeding problem. Needs either a reduction step (both parent overloads reduce to the primary's single slot) or a port-map-published seed |
| 1 | `MeshLoader.java:252` — `vertexAttributes.toArray(VertexAttribute.class)`, a member the base drops | D7 — `CallSiteSubstitutionTransform` mechanism exists (dry-run: 3/3 bound, 0 findings) but is not enabled: the replacement needs a codec decision this port has not made (see §8.5) |

Previously-open D5 (private-member replay into `ModelInstanceHack`) and D6/D6.5 (static-class type
occurrence, drop/injection namespace mismatch) are CLOSED — `ENGINE-LIMITS.md` D5, D6, D6.5.

### 8.4 What the reference port did differently — `CLAUDE.md` §3.5

`../sge/sge-extension/gltf` is 100% coverage. Two GWT-reflection workarounds it solved are taken
here unchanged (`PixmapBinaryLoaderHack`, `GLTFBinaryExporter.savePNG`). Its
`GLTFMaterialExporter.ext` uses plain JVM reflection (`newInstance()`), which does not link on JS/
Native — this port uses the factory-registry pattern libGDX core's `Pools` and Ashley's
`ComponentFactories` already use instead. Its whole reflective `Json` path is REPLACED by 2,268 lines
of hand-written Jsoniter codecs (`GLTFCodecs`, `GLTFExporterJson`) rather than ported — this port
instead compiles against libGDX's injected `Json` facade, whose reflective paths throw
`UnsupportedOperationException` at the three D7 call sites; loading a real `.gltf` is untestable
until the codec decision above is made.

### 8.5 Do NOT retry

- Reading `Phase.transformType` bare for "is this named as a type" (D6): de-collapses 29 of 31
  constant holders, moves 36 members, no check count moves — false positive on static ACCESS.
- Refusing every cross-class private widening in `CtorFunnel` to fix D5: libGDX core makes 22 sound
  within-module `WidenedVisibility` decisions; a blanket refusal regresses the base. Scope, don't remove.
- Refusing the withheld promotion to fix D4: this is `ENGINE-LIMITS.md` C1 (+14 on libGDX), breaks
  the dependent's own subclasses instead.
- `dropTypes` + `inject` for the four affected files to reach green: forks four files from upstream
  permanently to hide two engine gaps (`ENGINE-LIMITS.md` K3 — injected sources are for semantics
  the target lacks, never for adapting shapes).

### 8.6 Remaining

- D4 (2 errors): base's published port map needs to carry each type's primary parameter list so a
  dependent's `Plans` can seed non-owned classes from it instead of re-deriving a wall.
- D7 (1 error) + `MeshLoader`'s `Array#toArray(Class)`: enable `CallSiteSubstitutionTransform` once a
  JSON codec replacement is chosen (sge's hand-written Jsoniter codecs are one candidate).
- T12 (`protected` → `protected[<package>]`): priced at 867 declarations on libGDX core alone,
  its own cycle and baseline promotion. Until it lands one `MethodBodyTransform` entry exists only
  to restate an overload javac already chose.
- Behavioural coverage is 30 tests over 5 of 135 types, none run (port does not compile). Exporter/
  loader round-trip needs the reflective `Json` the base drops.

---

## 9. libgdx-screenmanager — the port with a dependency the corpus does not own

`de.eskalon.commons.{screen,core,utils} → sge.screen{,.utils}`, Apache-2.0. Dependent of `sge`. First
corpus library whose upstream depends on a THIRD library the corpus neither vendors nor ports
(`com.github.crykn.guacamole:gdx:v0.3.6`, 10 types), and the first port with non-empty
`src/main/scala` (hand-written guacamole replacements).

### 9.1 Scope

`src/main/java`, 22 types. Excluded: `src/example/java` (5 files, needs an unported backend).
`src/test/java` (§9.3) is not migrated.

### 9.2 Current state

| gate | `sge-screens` |
|---|---|
| compile errors (JVM/JS/Native/ref) | 0/0/0/29 |
| files emitted | 22 (0 dropped, 0 injected) |
| hand-written support sources (`src/main/scala`) | 9 files, 503 lines — guacamole replacements |
| omissions | 0 |
| portability (all/emitted/injected) | 151/0/0 — all 151 are libGDX core's own, seen through the resolution root |
| context seams | 10, all `unconstructed-thread` warnings (§11.12) |
| trivia lost/recovered/deliberate | 0/1/0 |
| tests | 16 of 16 passing (hand-written; upstream's 12 are §9.3) |

### 9.3 guacamole — resolved, not ported

`ScreensClasspath` fetches exactly `build.gradle`'s declared coordinates (`cs fetch --classpath`),
excluding libGDX (resolved from source instead, to avoid a second answer for `com.badlogic.gdx.*`).
`TypeRedirectTransform` re-points all ten guacamole types at `sge.screen.guacamole.*`, hand-written
in `ported/sge-screens/src/main/scala` — no §1(c) rule, no new phase. Guacamole becoming its own
corpus port would delete this redirect table and directory together.

Upstream's 12 `@Test` (7 files) are not migrated: `LibgdxUnitTest` (base of 6 files) boots
`HeadlessApplication` (no backend ported); `ScreenManagerUnitTest` uses `Mockito.mockStatic`/`spy`
(JVM bytecode instrumentation, unavailable on JS/Native). Only `BasicInputMultiplexerTest` and
`TimedScreenTransitionTest` are reachable and are reproduced (marked `(upstream)`) inside the 16
hand-written MUnit tests.

### 9.4 Where this port is strictly better than the reference hand port

The reference hand port (`sge-extension/screens`) lacks `NestableFrameBuffer` (uses a plain
`FrameBuffer`, whose `end()` unbinds to the DEFAULT framebuffer rather than what was bound on
`begin()` — a silent GL-state loss this port avoids, verified present in the emitted
`ScreenManager.createFrameBuffer()`'s redirected return type; runtime behaviour still needs a GL
context, §9.3) and lacks `ManagedScreenAdapter`/`BasicInputMultiplexer`/`Supplier` entirely, all
three ported mechanically here.

### 9.5 Do NOT retry

- Fix a static redirect by remapping the qualifier `Ident` alone — a type the frontend PARSED is
  re-derived from the member's owner one layer later, silently undoing it (`ENGINE-LIMITS.md` D8).
- Fix that by re-pointing the original member's `owner` — detaches the member from its unit for
  every ownership-filtered check (measured: Ashley `port-map` 0→6, byte-identical emitted text).
  Mint a twin owned by the target instead.
- Give a minted redirect target a self `TypeRef` as its `info` — reads as a term, static half emits
  an unqualified identifier naming nothing.
- Put guacamole on the frontend classpath without excluding libGDX — guacamole's POM pulls its own
  gdx jar, two answers to every `com.badlogic.gdx.*` name.
- Attempt the upstream suite before a libGDX backend is ported.

### 9.6 Remaining

- Behavioural coverage is 16 tests over 8 of 22 types; the GL half (eleven concrete transitions
  rendering through `SpriteBatch`/`ShaderProgram`) is covered by compilation only.
- guacamole is not ported — 10 types / 458 lines are hand-written Scala rather than emitted. Next
  obvious library: 37 files / 3,544 lines, Apache-2.0.

---

## 10. gdx-vfx — the GL-facing port, and the one whose API surface is mostly its BASE's

`com.crashinvaders.vfx → sge.vfx`, Apache-2.0. Dependent of `sge`. Nearly every emitted signature
mentions a base-emitted type (`FrameBuffer`, `Mesh`, `ShaderProgram`, `GL20`, `Texture`, `Gdx`), and
it has a reflective branch for a GWT backend the corpus does not port.

### 10.1 Scope

`gdx-vfx/core/src` (23 types) + `gdx-vfx/effects/src` (21), one sbt module (matches the reference
port). Excluded: `gdx-vfx/gwt/src` (1 file, unreachable — see §10.3) and `demo/` (74 files, needs
third-party libraries not in the corpus). No upstream test source set at all; gate is 64 hand-written
MUnit tests.

### 10.2 Current state

| gate | `sge-vfx` |
|---|---|
| compile errors (JVM/JS/Native/ref) | 0/0/0/2 |
| files emitted | 44 (0 dropped, 0 injected) |
| omissions | 2 |
| portability (all/emitted/injected) | 151/0/0 — all in libGDX core's own files (D2 ownership filter) |
| context seams | 20 = 16 `unconstructed-thread` + 3 `residual-global-read` + 1 `deferred-init` (§11.12) |
| trivia lost/recovered/deliberate | 0/2/0 |
| tests | 64 of 64 passing (6 files, hand-written) |

### 10.3 The policy decisions — three §1(b) body substitutions and one context extension

`VfxGLUtils#<clinit>`'s WebGL/`ClassReflection` branch is unreachable (base drops `ClassReflection`,
`gdx-vfx/gwt` out of scope) — `MethodBodyTransform` keeps only the else branch, matching the
reference hand port's own answer (`CLAUDE.md` §3.5). Since `DefaultVfxGlExtension` reads `Gdx.gl`
(threaded), its construction moved out of the now-empty `<clinit>` into
`VfxFrameBuffer#getBoundFboHandle` behind a null guard (nearest caller carrying the clause) — a
`DeferredInit`/`unsuppliable-use` seam, counted. Because `getBoundFboHandle()`/`glExtension` are
PUBLIC API a consumer may call without going through `VfxFrameBuffer`, a third substitution makes the
now-uninitialisable static throw `IllegalStateException` naming the initialisation path, rather than
NPE at an unrelated line — residue the reference port (whose generated member takes the clause
directly) does not carry, and not exercised by any test (no GL-context suite asserts the null
branch). `VfxFrameBuffer#tmpCam` uses the `lazy-init` `ContextHolderExtension` mechanism
(`ENGINE-LIMITS.md` CT8) to move a threaded-type static to first read.

### 10.4 Residues, named

- 2 omissions: `ShaderVfxEffect`'s dropped `@SuppressWarnings` (correct, no scala meaning, T6);
  `PrioritizedArray`'s C7 promoted-ctor replay (two non-delegating roots, capacity path overwritten
  immediately — `PrioritizedArraySuite`'s capacity test confirms identical behaviour).
- `PrioritizedArray` re-implements libGDX's cached-iterator idiom in gdx-vfx's own namespace, so the
  §1(c) `gdx-shared-iterator` rule (scoped to `com.badlogic.gdx.utils`) correctly does not see it —
  nesting two iterations over one `PrioritizedArray` would terminate the outer loop early, silently,
  same as in libGDX; gdx-vfx's own invariant to watch if that shape is ever nested.
- 27 of 44 types (21 effect classes + `VfxManager`/`VfxRenderContext`) rest on compilation alone —
  every method is a shader draw needing a GL context; suite fixture uses `sge.SgeTestFixture.testSge()`
  with every service ABSENT so a test that crosses the GL line fails loudly (NPE at the exact field)
  rather than silently passing against a stub.
- `gdx-vfx/gwt` stays unported while no GWT/Scala.js backend exists in the corpus; if one arrives,
  §10.3's substitution is the entry to revisit.

### 10.5 Do NOT retry

- Subtract the class literal's OWN unit from the object-collapse guard — `X.class` inside `X` is the
  log-tag idiom and needs `classOf[X]`; `StaticCollapseSpec` fails on "…including from inside itself".
- Extend `knownReceiverArgs`' unchecked-conversion guard alone to fix `Found: Wrapper[Object] /
  Required: Wrapper[T]` — INERT (1→1); the node's recorded type already said `Wrapper[T]` while the
  emitted scala had `Wrapper[Object]`, so the guard's own comparison had already answered "agree"
  (`ENGINE-LIMITS.md` G21).
- Gate the static type-parameter scope at `classDef`'s `enclosingAcc` — 0 change; `anonClass` does
  not go through `classDef`, and the `inStatic` flag is reset by `execDef` per instance method. The
  scope has to live in the FRAME (`ENGINE-LIMITS.md` G20).

---

## 10.5 liqp — the first port measured from OUTSIDE the sge/ssg family

`liqp.* → ssg.liquid.*`, MIT. The first port measured from outside the sge/ssg family — 135 java
files, 105 test files, an ANTLR-generated parser this port's own build step rewrites into the emitted
namespace rather than a dependency it resolves.

### 10.5.1 Current state

| gate | `ssg-liquid` | `ssg-liquid-test` |
|---|---|---|
| compile errors (JVM/JS/Native/ref) | 0 | 0/0/0/3 |
| files emitted | 139 (0 dropped, 4 injected) | 105 (nothing excluded, T9 closed) |
| omissions | 1 (`Map.Entry.setValue`, K5.7 refusal) | 8 (dropped `@SuppressWarnings` on anonymous-class fields) |
| tests | — | 637 emitted of 639 upstream (2 lost, D-liqp-7/G24); 636 pass, 1 fail (declared, K18) |

### 10.5.2 Residues, named

- 1 failing test (declared, `baseline/expected-failures.tsv`): `SortTest.testSortMap` —
  `Sort$ComparableMapEntry cannot be cast to scala.Tuple2`. `Map.Entry → Tuple2` is an
  `UninheritableTarget` (K5.7) and `Tuple2` is a concrete target no live view can be, so K18 refuses
  the reified cast. Maintainer decision 2026-08-14: stays refused, scala's idiom for
  custom-comparison sorting is an `Ordering`, not an entry subclass.
- 2 tests dropped at member granularity (D-liqp-7): `ComparingExpressionNodeTest`'s
  `cartesianProduct` helper and its two `@Test`s. Blocked on G24 (open, engine): java's `<T>` bound
  is vacuous, the emitted `T <: java.lang.Object` is not, and pinning the bound corpus-wide was
  priced and refused (libGDX core 0 → 50 errors). `ENGINE-LIMITS.md` G24.
- `collection-boundary` carries `OpaqueEgress` review rows (11 main / 6 test) naming external
  callees with an opaque formal a retyped value may reach — a candidate list, not a residue; one
  entry (`JsonGenerator#writeObject`) was acted on via `reflectiveSinks` (T16/D-liqp-9).
- `portability(emitted)` on the test port is 1,467, dominated by hamcrest — deliberately left in
  place, counted by `ENGINE-LIMITS.md` X6's `org.hamcrest.` rule.

### 10.5.3 Do NOT retry

| tried | measured | why |
|---|---|---|
| pin the vacuous `<T>` bound corpus-wide (to fix G24) | libGDX core 0 → 50 errors (all bounds), 0 → 6 (methods only) | scala 3 roots `java.io.Serializable` at `Any`; the fix's blast radius is every generic signature in the corpus. Port decision instead: drop the 2 tests (D-liqp-7) |
| `dropMethods` on `Map.Entry.setValue` as a port decision | one `Not Found` traded for one `needs to be abstract`, invisible until 0 typer errors | K5.7 keeps java's parent; dropping the member leaves the class abstract against an interface it kept. `CLAUDE.md` §1(b) |
| a hand-written replacement (`NodeVisitor`, `LValue`, `Template`, `LiquidSupport`, spi files, filters) to reach green | not taken — this is the library, by hand | every one of these types is mechanically portable; the disagreements are engine gaps, not shape questions a substitution should paper over |
| scope `CollectionsTransform` out of the declarations at the seams | 27 → 47 with an 18-entry `except`, 27 → 51 with the phase off entirely | `ENGINE-LIMITS.md` K16 — a scope withdraws the phase's rewrites too, not only its retyping; liqp's collection types are its currency |

`ssg-liquid` needed roughly forty engine (a) fixes and zero (c) rules to reach this state — every one
recorded in `ENGINE-LIMITS.md` under its own key (K5–K22, L3–L4, T11/T14/T15, X2, C3/C12, D8/D10),
not restated here per `CLAUDE.md` §3.6.

---

## 10.6 ssg-md — flexmark-java, the largest Java surface either reference repo has

`com.vladsch.flexmark.* → ssg.md.*`, BSD-2-Clause. flexmark-java 0.64.8, 53 maven modules under one
package root (rename verified collision-free, 116 packages in/out). Milestone 1 (core + eleven
`flexmark-util-*`) is a base port; milestone 2 is all 29 covered extension modules as ONE dependent
port. Deferred: 3 uncovered extensions, 7 converter/tooling modules, `util-experimental`,
`tree-iteration`.

### 10.6.1 Scope

| | scope | main files |
|---|---|---:|
| milestone 1 (this base port) | `flexmark` core + 11 `flexmark-util-*` | 486 |
| milestone 2 (dependent, `ported/ssg-md-ext`) | all 29 covered `flexmark-ext-*`, one port root, tree disjoint by package from the base | 386 |
| deferred | 3 untouched extensions + 7 converter/tooling + `util-experimental` + `tree-iteration` | 219 |

28 undocumented hand-port omissions (not 24): the whole `util/html/ui` subpackage, eight
`*JiraRenderer`, `flexmark-util-dependency`'s `Flat*` extension-resolution family (not dead code),
two `@Deprecated` forwarders, `util-misc`'s `FileUtil`/`ImageUtils`.

### 10.6.2 Current state — milestone 1

| gate | `ssg-md` | `ssg-md-test` (`flexmark-util`'s own 52-file suite) |
|---|---|---|
| compile errors (JVM/JS/Native/ref) | 0/0/0/136 | 0/0/0/136 |
| files emitted | 468 (0 dropped, 0 injected) | 52 |
| omissions | 61 (44 `@SuppressWarnings` dropped, 12 `super(args) dropped`, 3 C7 promoted-ctor, 2 residue) | 15 (scope widened at §10.6.7 to 3 upstream trees) |
| tests | — | 727 outcomes: 725 pass, 2 fail (declared, K18.1/K5.7) |
| `break_residue` / `trivia` / `signature` / `manifest` / `port-map` | 0 across the board | 0 |

Errors closed over 23 waves (243 → 0); the typer reached 0 at wave 17, `RefChecks` then rose to 131
and closed to 0 at wave 23 — `CLAUDE.md` §3's gate finishing its sentence, not a regression. Every
riser classified in `ENGINE-LIMITS.md` (K28, K28.1, K28.2, G8.10, G21, G26, G28, G30). History in
git; nothing above is open.

### 10.6.3 The census, classified per §1 — CLOSED

Cited by `ENGINE-LIMITS.md`. Was a 23-wave, ~240-error census; every family is closed and classified
under its own `ENGINE-LIMITS.md` key (see §10.6.2's list). Current residue is the 61 + 15 omissions
above and the 2 declared test failures — nothing else is open. History in git.

### 10.6.4 Do NOT retry

- Argument inference for a vararg pack's element type when the argument is a raw class literal
  (`addNodes(Class<? extends Node>...)`) — measured `81 → 83` twice; take the DECLARED component
  through the inherited-formal lookup instead (`ENGINE-LIMITS.md` G26).
- Read the hand port's deviations as milestone-1 policy: `NullabilityTransform` on `@Nullable` (594
  annotated files, highest-leverage entry this port will ever have), a `collections` scope
  (`ENGINE-LIMITS.md` K16, measured 27→47/27→51 on liqp), a `bean-properties` entry — all
  deliberately absent from `main.conf` (D-md-5). Quote the shape, price it, before it becomes policy.
- `BitField`/`EnumBitField`: the hand port's enum-reflection replacement is a redesign, not a rule to
  derive from one library.
- `SegmentedSequenceTree`'s `ThreadLocal<Cache>` → `var`: the hand port drops thread confinement;
  correctness-relevant, not a general rule.

### 10.6.5 (was: what the first run taught) — folded into `ENGINE-LIMITS.md`

`AnnotationPolicy` marker-annotation carriage and `java_test_count`'s empty-path bug are both fixed
engine-wide; no residue.

### 10.6.6 The test port — CLOSED, floor is the 2 declared K18.1 failures

Dependent of `main.conf` (`test.conf`, `base = "main.conf"`), adds `test-framework`. Scope is a
13th module (the `flexmark-util` aggregator's own `src/test`, 52 files). Current: 0 compile errors
both source sets, 723 of 723 `@Test` emitted (0 lost), suite 721 passing / 2 failing (both declared,
`Pair.equals` over a reified `Map.Entry`, same engine family as liqp's `SortTest`, `ENGINE-LIMITS.md`
K18.1/K5.7) — 0 open. `test-framework(refused)` lane: 26 rows (`@RunWith(Suite.class)` × 9,
`@Suite.SuiteClasses` × 9, `@Rule` fields × 6, hamcrest `Description`/`BaseMatcher` × 2). JUnit's
`ExpectedException` `@Rule` (37 sites) is fully modelled (`ENGINE-LIMITS.md` X5), not refused.
`@RunWith(Suite.class)` aggregator drop remains a deferred (not built) fixpoint-guarded remedy —
0 rows applied, moves neither side of the discovery count when built.

### 10.6.7 CommonMark conformance census — 1,870 of 1,870 (100%)

`just md-conformance` (not in `measure-all`, constant inputs). Three live spec versions
(`spec.txt`, `0.27`, `0.28`), 1,870 of 1,870 examples pass, matching a measured-green java control
byte-for-byte on all four specs including the ones neither side gets right. `spec.0.29.txt` is
disabled upstream (`ResourceLocation.NULL`) — driven anyway: 629/649 both-pass, 20/649 are spec rules
flexmark itself never implemented (java's control fails them identically), 0 port-only defects.
`spec.0.26.txt`/`spec.0.30.txt` have no test class at all, upstream or here. The 42-example gap this
census started with was ONE defect (three dropped `super(args)` three constructors up from
`HtmlRenderer.builder`, `ENGINE-LIMITS.md` C3) — closed, `omissions` 61→54 at the time, no compile
error moved. `Html5Entities`' classpath-resource lookup (a string literal, untouched by rename) is
now shipped via `PortManifest.resources` (`DESIGN.md` §8.22).

### 10.6.8 Milestone 2 — the extensions, ONE dependent port. CLOSED at 29 of 29

`ported/ssg-md-ext`, tree disjoint by package from the base, compiled beside it on every run.

| gate | `ssg-md-ext` | `ssg-md-ext-test` |
|---|---|---|
| compile errors (JVM/JS/Native/ref) | 0/0/0/187 | 0 |
| files emitted | 331 (0 dropped, 0 injected) | — |
| shared surface | `manifest` 0 over 458 shared types, `base-surface` 0, `port-map` 0 | |
| counted residue | `overload-risk` 288, `omissions` 18, `idiom(refused)` 10, `dependency-coverage` 4, `base-surface` 3, `jdk-surface` 3, `portability(emitted)` 3, `trivia(recovered)` 5, `collection-boundary` 1 | |
| tests | — | 188 of 188 passing (13 admitted plain-`@Test` files, 9 modules), expected-lost 0 |

Five engine gaps bought and closed (G32, K24 face 4, C15, C16 ×2). Excluded with reasons: 3 modules
with no reference-hand-port coverage (`spec-example`, `xwiki-macros`, `zzzzzz`); 114
`ComboSpecTestCase` subclasses + 59 `@RunWith(Suite.class)` aggregators (documented refusal, need a
hand-written MUnit driver). `flexmark-ext-autolink`'s third-party dependency (`org.nibor.autolink`)
is DECLARED, not substituted (2×2 = `covered`, `dependency-coverage(declared)` 0→1) — the reference
hand port substituted a regex, measured wrong elsewhere on this corpus (`ENGINE-LIMITS.md` K16). One
open residue nothing checks today: `autolink` is JVM-only while this module inherits the all-platform
default — no instrument compares per-platform artifact availability to `targets` yet.

Per-extension checklist for the deferred tier (imports → glob → module-name → test triage by the
"declares plain `@Test` AND imports nothing from `com.vladsch.flexmark.{test,core.test}`" pair
predicate → `md-measure && md-ext-measure` in order → read the split → triage any
`base-surface`/`super(args) dropped` pair AT THE CALL SITES → classify → baseline-accept both) is
unchanged from what closed the 29; reuse it rather than re-deriving.

---

## 10.7 gdx-ai — the dependent that lives INSIDE its base's namespace

`com.badlogic.gdx.ai.* → sge.ai.*`, Apache-2.0. Dependent of `sge`, 166 files, the largest libGDX
dependent. Declares INSIDE the base's namespace (`com.badlogic.gdx.ai` under `com.badlogic.gdx`), so
it is the first dependent that must declare NO rename of its own (§10.7.2).

### 10.7.1 Scope

`gdx-ai/gdx-ai/src`, 166 of 167 `.java` files — excluded is upstream's own GWT super-source
`StandaloneFileSystem.java` (name collision with the JVM one, filtered on the path segment
`com/badlogic/gdx/emu/`). `gdx-ai/gdx-ai/tests` (2 files, 10 `@Test`, real JUnit) is
`GdxAiTestMigrate`. The top-level `gdx-ai/tests` gradle project (111 files) is demos, not tests
(zero `@Test`). The reference hand port's own 24-file / 196-`test(…)` MUnit suite is the corpus's
differential gate for this library (§10.7.12), not an upstream suite.

### 10.7.2 Current state

| gate | `sge-ai` | `sge-ai-test` | `sge-ai-diff` |
|---|---|---|---|
| compile errors (JVM/JS/Native/ref) | 0/0/0/9 | 0/0/0/9 | 0 |
| files emitted | 166 (1 dropped, 3 injected) | 2 | — (hand-written, `ported/sge-ai/src/test/scala`) |
| context seams | 0 (was 5, all `residual-global-read`, closed) | — | — |
| tests | — | 10 of 10 passing | 95 of the hand port's 196: 93 pass, 2 declared |

Manifest declares only a `governs` sub-claim (`com.badlogic.gdx.ai`) and
`PortMapTransform.forBases("sge")` last; everything else (drops, collections, mutable-params) is
inherited as the base's ONE instance.

### 10.7.3 Why milestone 1 drops nothing — the parser is not a leaf

The reflective parser subtree (`BehaviorTreeParser`, `BehaviorTreeLoader`, two `btree.annotation`
types) looked like a droppable leaf; it is not — `BehaviorTreeLibrary`/`-Manager` and `Include` (an
ordinary decorator task every tree uses) all reach into it, so the closure is seven types plus a cut
into a live task class. Milestone 1 converts everything upstream compiles instead
(`substitution(dangling) 0`).

### 10.7.4 Residues, named

- Family 1 (was 12 errors): `com.badlogic.gdx.utils.reflect.*` reflection in `BehaviorTreeParser`,
  `Task.cloneTask()`, `CircularBuffer.resize` — CLOSED (§10.7.5, §10.7.8): a `TaskRegistry`/
  `TaskField` injected table (matching the reference hand port's own design) plus a scoped type
  redirect (`Only("com.badlogic.gdx.ai")`) replace the reflective reads; `Task.cloneTask()` throws
  `TaskCloneException` naming `Task.TASK_CLONER`, java's own documented GWT escape hatch — louder
  than java, declared where the differential suite exercises it (§10.7.12).
- Family 2 (was 4 errors + 5 context seams): `GdxAI`'s static-field context seam — CLOSED. `GdxAI`
  is this port's one `dropTypes` + `inject`: an object installing java's own negative branch
  (`NullLogger` unconditionally; `getFileSystem()` throws naming the fix line for the filesystem,
  since `StandaloneFileSystem` now takes a threaded context a static initialiser cannot supply).
- Family 3 (was 4 errors): a java `@interface`'s elements (`TaskConstraint.minChildren`/
  `maxChildren`) were dropped from the emitted class — CLOSED, `ENGINE-LIMITS.md` T22: elements
  become `val` constructor parameters. Retention (reading one back via `getAnnotation`) stays open
  but moot — family 1 removes the reflective reads.
- `sge-ai-diff`'s 2 declared failures (`Task.cloneTask()` on both `sge.ai.btree.TaskSuite` tests) —
  the port is right; the hand port redesigned `Task` with a `newInstance()` member instead.
- 14 of 24 hand-port suite files / 101 of 196 tests are class (c), not an engine gap: 52 tests behind
  an API shape the hand port changed (`Timepiece` param, `Parallel` arity, a collapsed overload), 20
  behind the parser registry redesign, 17 behind a property reshape (`Steerable`/`Location`/`Pool`),
  12 behind an access widening (`protected`/`private` fields the hand port made public). `steer`
  (23 tests) is the package with real coverage still available, reachable only by writing NEW
  fixtures against this port's own `Steerable`.

### 10.7.5 The 20 errors, classified per §1 — CLOSED

Cited by `ENGINE-LIMITS.md` T22. All three families above are closed; see §10.7.4 for current state.

### 10.7.6 Do NOT retry

- `packageRenames { "com.badlogic.gdx.ai" -> "sge.ai" }` — fatal `RenameOverride`; the base's
  inherited rename already produces `sge.ai.*`.
- `dropTypes` for the reflective parser subtree as four files — leaves dangling references (§10.7.3).
- Pre-dropping all nine `GdxAI`-facade files — only `GdxAI` itself asks the impossible question; the
  other eight port mechanically.
- `sites = { GdxAI#logger -> lazy-init, GdxAI#fileSystem -> lazy-init }` — measured 14→14 errors (4
  new), `DeferredInit` has no shape for a MUTABLE static (`GdxAI.logger` is reassignable via `setLogger`).
- `boundary = "residual-global"` for the same two sites — answers only the read's spelling; the
  `unsuppliable use` seams have no read to spell, and `sge.Sge` has no `global` member to name.
- Compiling the differential suite's 24 reference files together and reading per-file error counts
  as the census — that is a typer-only pass and is wrong by 4 files / 16 tests in the dangerous
  direction (`RefChecks` unmeasured while any typer error stands). Compile the candidate set alone.

### 10.7.7 Remaining

- `steer` package: 23 tests behind a property reshape, reachable via new hand-written fixtures
  against this port's own `Steerable` (not a differential gate, since the hand port's shape differs).
- Annotation RETENTION (`ClassReflection.getAnnotation` reading a scala `StaticAnnotation` back) is
  open but moot on this port (`ENGINE-LIMITS.md` T22).

### 10.7.8 The parser's reflective half — the cut

Cited by `ENGINE-LIMITS.md`. CLOSED — see §10.7.4 family 1. Mechanism: `TypeRedirectTransform`
(`Field → sge.ai.btree.utils.TaskField`, scoped `Only("com.badlogic.gdx.ai")` — unscoped it corrupts
libGDX's own `Json$FieldMetadata` contract, `ENGINE-LIMITS.md` D12) plus five `MethodBodyTransform`
substitutions and an injected `TaskRegistry`/`TaskField` table
(`balticporter/corpus/gdxai-overrides/sge/ai/btree/utils/`), matching the reference hand port's own
factory-plus-setter-closure design. `@Inherited` is reproduced (nearest `@TaskConstraint` wins,
attributes accumulate). Loses: a task type nobody `register`s cannot be instantiated from a `.btree`
file where java would find it on the classpath — built-ins are pre-registered under both the emitted
and upstream FQCN.

---

## 10.8 sge-textra — TextraTypist, the dependent with a THIRD-PARTY jar and a TRIPLE licence

`com.github.tommyettinger.textra.* → sge.textra.*`, Apache-2.0 + two MIT notices. Dependent of `sge`,
92 files / 38,607 LOC (largest by line count among libGDX dependents), heaviest scene2d surface in
the corpus. Compile dependency `com.github.tommyettinger:regexodus:0.1.21` (JVM-only, DECLARED not
routed around — same shape as `ssg-md-ext`'s `org.nibor.autolink`). No upstream test suite at all
(`src/test/java` is 128 files, zero `@Test`, all manual LWJGL3 demos) — behavioural evidence is
entirely differential (§10.8.13–§10.8.17).

### 10.8.1 Scope and licence

`src/main/java`, 92 of 95 files (3 `package-info.java` excluded). Licence is TRIPLE: Apache-2.0
per-file headers (82 of 92 emitted files), emoji-regex MIT inline in `EmojiProcessor.java`, and
typing-label MIT with NO per-file header anywhere — met via `Provenance.notices` copying both root
licence files into `src_managed/`. 10 files upstream carry no header at all. The reference hand port
ships neither MIT notice.

### 10.8.2 Current state

| gate | `sge-textra` | `sge-textra-diff` |
|---|---|---|
| compile errors (JVM/ref) | 0/3 | 0 |
| files emitted | 92 (0 dropped, 0 injected) | — (hand-written, `ported/sge-textra/src/test/scala`) |
| omissions | 52 (41 `super(args)` dropped, concentrated in the widget family) | — |
| base-surface | 2 (`Button#initialize` replayed into two subclasses — base emits it `private`, §1(a) in the base) | — |
| port-map | 1 (`BitmapFont#<init>()`, base's own C11 drop) | — |
| jdk-surface | 5 (`Arrays.binarySearch/fill` overloads, `ArrayList#clear()` — configure or cite-refuse) | — |
| context seams | 45 (42 captured-context, 2 unconstructed-thread, 1 self-supplied) | — |
| tests | — | 165 of the reference hand port's 239: 165 pass, 0 fail, 0 skipped |

### 10.8.3 Residues, named

- 52 omissions (§10.8.7): 41 `super(args)` dropped, 8 C7 promoted-ctor (`TypingLabel`), 2 annotation
  dropped, 1 enum without its `java.lang.Enum` supertype.
- `base-surface` 2, `port-map` 1: both attributed to the BASE (`sge`), not fixable from this module.
- `jdk-surface` 5: unmapped `Arrays`/`ArrayList` members — open §1(b) configuration item.
- `overload-risk` 62 (of 17,661 calls), COUNTED and deliberately not resolved.
- `regexodus`'s JVM-only availability under this port's three-platform `targets` — no instrument
  compares per-platform artifact availability to `targets` yet (same open item as `ssg-md-ext`'s
  autolink, §10.6.8).
- 18 differential-suite files / 69 tests are class (c) permanently: every one constructs a nilary
  `Font()`/`TextraLabel()`/`TypingLabel()`, whose java-side `BitmapFont()` delegation the base's own
  C11 drops (no fixture reaches it; the hand port diverged by giving `Font()` an empty-nilary
  primary java does not have). Not work on this port — recorded so nobody re-scopes it as such.

### 10.8.4 Do NOT retry

- Read `TypingConfig`'s `E172` as `TypingConfig`'s own seam — it is `LinkEffect`'s (a lambda inside
  `TypingConfig`'s registry), and a drop-and-inject of `TypingConfig` would replace a 40-entry
  registry to work around one method in another file.
- `sites = { ... -> lazy-init }` / `boundary = "residual-global"` for that seam — neither mechanism
  reaches a construction inside a lambda; see gdx-ai §10.7.6 for the same two refusals.
- Apply a `.isEmpty`-shaped mapping row by member NAME rather than by RECEIVER in the differential
  suite adaptation — `Nullable.isEmpty` and `FloatArray.isEmpty()` mean different things; a blind
  regex inverted 8 assertions silently (`CLAUDE.md` §4.56's hazard met in a text edit).
- Build service implementations for the 18 class-(b) differential files — they fail on `data` being
  null one frame inside `Font`, not on an absent service; shipping the font asset would not help
  either (the port performs no lookup to load it, and java's own path ends at a GL call).
- Read the hand port's `LzmaUtils` hoist to top-level as milestone policy — the base already ports
  `com.badlogic.gdx.utils.compression` (392 port-map rows), so the inherited rename resolves it and
  nothing outside the destination package consumes the hoisted spelling.

### 10.8.5 Engine gaps this port found (all closed, in `ENGINE-LIMITS.md`)

T22 (annotation elements), a `return` in a promoted constructor body (JS-S21/local-`def` lowering), a
synthesised-primary/`null` overload ambiguity (`TirEmitter.markerArg` ascription), and CT6 face C
(`C::new` method-reference construction the closure never walked to — `ContextNeed.ctorRefUses`,
232 corpus-wide sites). `ContextSeamCheck.Kind.UnsuppliableUse` (a threaded class constructed from a
site that cannot supply) and `ContextHolder.retain` (a `val` naming the context inside a threaded
type's own body, read by another declaration's `selfSupplied`) are both engine mechanism this port's
`LinkEffect`/`TextraLabel` pair motivated — §10.8.11.

### 10.8.11 The FOURTH EXIT — `retain`, the reference hand port's own answer

Cited by `CLAUDE.md` §10.8.11. `ContextHolder.retain` (`Map[type FQN, member name]`, empty = no-op,
per-declaration union, `CLAUDE.md` §1's ADD-rule default) mints `val <name>: <context> =
summon[<context>]` at the head of a threaded type's body; a `selfSupplied` expression on a holder of
one reads `<value>.<name>`. Matches the reference hand port's own shape exactly
(`TextraLabel.sgeContext`, read from `LinkEffect.onApply`). Shipped as
`retain = Map("…TextraLabel" -> "sgeContext")`, `selfSupplied = Map("…LinkEffect" ->
"this.label.sgeContext")` — took the port from 1 error to 0 (§10.8.12), 4 member digests, residue
empty.

### 10.8.17 The FIXTURE — 75 measured at the SITE, where it is 5

Cited by `ENGINE-LIMITS.md` (this port's differential residue). `ported/sge-textra/src/test/scala/
sge/textra/HeadlessSge.scala` — five of six services ABSENT (fail loudly at the exact field touched,
never a noop that passes silently), the sixth (`application`) answers only `getType()`/lifecycle
registration because `KnownFonts.java` itself refuses a null `Gdx.app`. Unlocked 5 tests
(`TextraLzmaFontRedSuite`, reproducing the hand port's own stale "not yet ported" LZMA stub — the
mechanical port emits java's real dispatch and passes). The remaining 69 (18 files) are permanently
class (c): all three entry points (`Font()`, `TextraLabel()`, `TypingLabel()`) die at the same frame,
`Font.<init>` reading a null `BitmapFontData` — java's own `BitmapFont()` cannot run headless either
(needs GL for its `Texture`); the reference suites can only construct one because the hand port's
`Font()` is a divergent nilary primary java does not have.

---

## 10.9 sge-visui — VisUI, the port whose licence obligation is not about CODE

`com.kotcrab.vis.ui.* → sge.visui.*`, Apache-2.0 + a CC BY-ND 3.0 icon notice. Dependent of `sge`,
162 files / 25,588 LOC, 22 packages, the most scene2d-saturated library in the corpus (56% of its
imports are `scenes.scene2d.*`). `usl/` (the skin-DSL compiler) is a SIBLING port (§10.9.13), not a
glob added here — the two share a git repo but are independent maven artifacts with no cross-imports
in either direction.

### 10.9.1 Scope and licence

`ui/src/main/java`, 162 of 164 files. Licence is DOUBLE: Apache-2.0 headers (161 of 162 emitted
files) plus a CC BY-ND 3.0 notice (`ui/NOTICE` + `ui/icons-license`) on shipped ICON assets — met via
`Provenance.notices` (unconditional, independent of whether the icon atlas itself ships). The
reference hand port ships neither notice file.

### 10.9.2 Current state

| gate | `sge-visui` | `sge-visui-diff` | USL (`sge-visui-usl`) | USL test |
|---|---|---|---|---|
| compile errors (JVM/JS/Native/ref) | 7/7/7/7 | 0 | 0/0/0/3 | 0/0/0/3 |
| files emitted | 162 (0 dropped, 0 injected) | — (hand-written) | 18 | — |
| resources shipped | 22 of 22 upstream (§10.9.3) | — | — | — |
| tests | — | 50 of the hand port's 72: 50 pass, 0 fail | — | 7 of 7 emitted: 6 pass, 1 ignored (upstream's own `@Ignore`) |

### 10.9.3 The resource residue — CLOSED

VisUI loads its skin/i18n/shaders via hardcoded classpath string literals at the UPSTREAM path (a
rename must not touch a string literal, §4.56). `PortManifest.resources` ships all 22 files
verbatim at upstream paths — CLOSED wave 7, 0 member digests. Mechanism generalised from
`serviceProviders`; same shape as `ssg-md`'s `Html5Entities` resource and now policy (§1(b)).

### 10.9.4 Residues, named

- `portability(emitted)` 47 (JVM threads/executors/reflection in the async task and file chooser
  machinery) — the shape §1.5's all-platform default is meant to surface, not an engine gap.
- `rewrite-callsites` 2 `Unaccounted` (`primitive->opaque:TextureHandle`, `type-redirect`) — both the
  BASE's phases seen through this dependent; engine-wide item, §12.
- USL's `portability` 9, all `IncludeLoader`'s remote-download path (`java.net.URL`,
  `HttpURLConnection`) and two `System.getProperty` — stated residue, `targets` deliberately not
  narrowed (upstream itself `@Ignore`s the one test that exercises it).
- One double-ascription cosmetic residue from G33 (USL's `CollectionUtils`) — fix belongs in
  `TirEmitter`'s general `Tree.Typed` arm, corpus-wide sweep, not yet done.

### 10.9.10 The 7-error floor, every one attributed

Cited by `ENGINE-LIMITS.md`. `22 -> 8` (wave 4) `-> 7` (wave 7).

| n | code | what it is | §1 | status |
|---|---|---|---|---|
| 3 | `E134` | `VisScrollPane`/`VisSlider`/`VisWindow` — three ROOTS each calling a DIFFERENT `super`, only the primary can replay one | (a) engine, REFUSED | `ENGINE-LIMITS.md` C3 |
| 3 | `E007` | `VisTextField` × 3 — `keyboard.show(true)` at an `OnscreenKeyboard.show(TextField)`-only overload | NEITHER — upstream/vendored-gdx VERSION SKEW (`build.gradle` pins 1.14.0, vendored tree is 1.14.1) | `CLAUDE.md` §3.5 |
| 1 | `E172` | `Draggable#BLOCKER` — a `static { }` block installing a listener on a threaded static; `lazy-init` can defer only the field half of java's ONE step-9 init sequence, not the block | (a) engine, exit UNBUILT | `ENGINE-LIMITS.md` CT11 |

The one row that WAS closeable (`DragPane#findActor` overriding the base's `T | Null` at an abstract
`T`, K13) closed at wave 7 via a member-level (not type-level) scope exit on the BASE.

### 10.9.6 Do NOT retry

- Compile the whole emitted tree for the differential lane — the 8/7-error floor means `RefChecks`
  never runs and no class file is written (47 errors, zero outcomes measured that way).
- Price a `new`-insertion or property-to-setter mapping row for the differential suite from a
  reading of the java — both are worth zero at the emitted site (Scala 3's universal apply already
  means `new`; `ctor-replay-widening` C15 already makes the fields `public var`).
- Open a fixture wave for the 9 differential tests blocked on PNG decode — probed: they die in the
  BASE's `Gdx2DPixmap` native-image symbol lookup, which no service stub is on the path to. Priced
  exits are a native-image loader wiring or a §1(c) decode-path substitution (as the reference hand
  port did with ImageIO), not a fixture (`ENGINE-LIMITS.md` X7).
- `sites = { ... -> lazy-init }` for `Draggable#BLOCKER` — moves the `E172` to the `<clinit>`
  instead of removing it (§10.9.10).
- `packageRenames`/redirect keyed on the shared simple name `AsyncTask` — libGDX core's and VisUI's
  are two unrelated types with total isolation on both sides (§4.56's trap; corrected §1.1 row).

### 10.9.7 The 32 errors, classified per §1 — CLOSED, floor is §10.9.10

Cited by `ENGINE-LIMITS.md`. History in git — the enum/context-threading family (10 of 11 `E172`
on a java `enum`, `java.lang.Enum#grid`-shaped member gaps) and the `Disposable→AutoCloseable`
retarget-not-reaching-a-dependent family are both CLOSED; current residue is §10.9.10's 7-error
floor.

### 10.9.13 USL — a sibling port, no reference port at all

`com.kotcrab.vis.usl.* → sge.visui.usl.*`, Apache-2.0, 18 files / 1,604 LOC (lexer, recursive-descent
parser, style merger, JSON writer), own port root (`ported/sge-visui-usl`), standalone (no base — the
"dependent-shaped run with no base" fatal-finding signature does not apply since it has no
resolution roots at all). Four scope facts re-derived on every run (0 cross-imports either
direction, 0 non-JDK imports) plus 18/18 Apache headers (obligation met by construction, no
`notices` key needed).

**The oracle**: 19 `.usl` fixtures diffed against upstream JAVA — all 19 IDENTICAL (3,654 transcript
lines), 7 of them reproducing the library's own RELEASED `uiskin.json` byte-for-byte (x1/x2 are the
same string). Not evidence for post-increment translation (all 28 sites in this library are
statement-position, none read as a value) — evidence for the rest of the scanner/parser pipeline.

### 10.9.8 Remaining

- The PNG decode path (native-image symbol resolution) if this port is ever to run graphics
  headlessly — two priced, undecided exits (§10.9.4/Do NOT retry).
- `Draggable#BLOCKER`'s exit — an engine mechanism (defer a whole step-9 field+block init sequence,
  not just the field) that does not exist yet; gates 1 of the 7 errors and 3 differential tests.
- A `visui-test` port for the 2 upstream `@Test` (`GreaterThanValidatorTest`/`LesserThanValidatorTest`)
  — marginal behavioural value (the differential suite already covers the same ground more broadly)
  but exercises the CONVERSION path itself, which no differential lane can.
- The G33 double-ascription cosmetic fix, corpus-wide.

---

## 11. Publishability — what sge and ssg need before they can depend on this

**The goal.** sge and ssg stop hand-maintaining their ports and instead depend on Baltic Porter as a
published library, feeding it Java sources plus per-library configuration, maintained by agents in
those repositories without this repository's context (`CLAUDE.md` §4.45).

### 6.1 What is still NOT done, stated plainly

Checklist (from the `porting-auditor` review at `8fea564`, re-verified; nine of the original
fourteen items are shipped and deleted per §3.7, item 1.3 — the published `balticporter-runtime`
distribution — among them):

1. **Seven programs still on the frozen BIR path** — five scouts/engine gates and the xwiki-macros
   cold-port closure. No PORT runs on BIR (liqp/flexmark/jbump all have TIR ports). One framework by
   declaration, two by deployment until xwiki-macros moves.
2. **No end-to-end proof a generated port resolves the published runtime** — `SbtGen` writes the
   dependency line; nothing has resolved it from sge or ssg. Needs an sbt scripted test.
3. **"Adding a library needs no edit to this repository" is unproven from outside** — provable only
   by consuming the published artifacts from sge or ssg.
4. **`ManifestAgreement` cannot see an unfingerprinted phase's configuration.** Fourteen transforms
   opt in; `TestFrameworkTransform` is the named holdout (`suite`/`testMember` don't reach the
   fingerprint, `ENGINE-LIMITS.md` CT9).
5. **No diamond (two bases sharing a third) has been built** — every corpus dependent is one hop
   from libGDX core; map composition beyond one hop is untested.
6. **Nothing verifies two ports were built by the same ENGINE at the manifest level** — `EnginePin`
   is wired into port-map freshness, not into `ManifestAgreement`.
7. **Nothing checks a CONSUMER's build puts a port's resource directory on its classpath.** Shipping
   is closed (`sge-visui`'s 22 files, `ssg-md`'s `Html5Entities`); a hand-written build that ignores
   `src_managed/<config>/resources` still gets a static-initialiser exception at first use, invisible
   to every check.
8. **Typer errors still arrive with no (a)/(b)/(c) signal** for an UNMARKED region — the marker
   mechanism (`Tree.Unportable`/`UnportableKind`/`MarkerCheck`) is built and emission-neutral (0
   markers mint on all fifteen lanes); what remains is the definition-level `SymTag`, four mint sites
   a term wrapper cannot take, and the correlation join (`DESIGN.md` §6.5).

**Found sound, not just untouched**: `Substitutions`' overload-precise keys; `ClassTableTransform`/
`StaticForwarderTransform` as (b) mechanisms with no smuggled library knowledge; the
`libgdx-overrides` tree as correct (c) placement; `PrimitiveToOpaqueTransform` as the canonical
(b)-mechanism/(c)-policy split; `RewriteTrace`'s blast-radius-before-a-rewrite pair; the stale-emit
abort in every lane. One latent edge, safe under current policy: `StaticForwarderTransform` matches
members by name only, so a wrapper with non-receiver-first overloads would misfire.

### 11.9 The adoption-gap catalog — three-way audit, 2026-07-31

Cited by `DESIGN.md`. A five-agent UPSTREAM-JAVA vs HAND-PORTS vs ENGINE-OUTPUT comparison over nine
ported + two unported ssg libraries. The engine is strictly more complete than the hand port in 8 of
9 (zero unexplained type-level omissions anywhere). Open items from the catalog, by kind:

**(a) engine-generic, actionable now:**
1. Java `final` single-write fields emit `var` + placeholder; should be `val` (9+ sites, noise4j alone).
2. `FunnelledCtor` needs default-parameter collapse and companion-`apply`/instance-`init*` shapes —
   would retire C3's `DroppedSuperCall` documentation instead of just recording it.
3. §4.55 clash false positive: a `static` factory and an instance field of the same name do not
   collide in Scala (companion vs class) — Ashley's `Family` carries 3 unneeded `$field` renames.
4. D4/D5 dependent-funnel drift is now closed for gltf's kind (base-published primary parameters);
   generalize to every dependent.

**(b) parameterisable phases still to build:**
1. JavaBean property transform beyond what P4 shipped — 3,234 `get*/set*/is*` still verbatim in
   places the hand ports made properties (hand-port rate itself only ~20%, so any expansion is
   `RuleScope`-scoped, never blanket).
2. Beyond-annotation nullability — sge tracks ~2× the annotated `@Null` set; needs null-flow
   analysis no phase has. Open research.
3. Collection-map retarget entries beyond `Comparator → Ordering` (P2, delivered, §11.16) — e.g.
   `gdx Array<Integer>` → `ArrayBuffer[Int]` with wrapper unboxing.

**(c) library rules, permanent injects, correctly not mechanized:** opaque types beyond libGDX's
~30 GL handles/enums (zero elsewhere, do not invent); per-type renames/sub-packaging (mechanism
built, `DESIGN.md` §8.7 — priced but unenabled candidates: simple-graphs' `flattenNestedTypes`/
`subPackages`, liqp's `typeRenames`); permanent injects with no Java source (gltf's Jsoniter codecs,
liqp's ANTLR-replacement parser); taxonomy/design redesigns (`SgeError`, vfx's `PrioritizedArray`
de-pooling, noise4j's hierarchy flattening, jbump's `keySort` collapse) — no detectable trigger,
hand-patch after a mechanical port when wanted.

**Non-gaps, recorded so they are not re-derived**: Scala 3 `enum` syntax cannot express constant
bodies (T8/T10/T11/T13 already handle it); bean names as cosmetics (a beautification backend,
`CLAUDE.md` §6); `for`→`while` (hand and engine agree); sge's SAM→function collapse is inconsistent
with itself; the typed-GL/`GLEnum` layer is hand-authored infrastructure with no Java source.

### 11.12 M1+P5 (globals→context) and M3 (annotation-driven nullability) — DELIVERED

Both cited by `ENGINE-LIMITS.md`. `GlobalsToImplicitsTransform` (`Gdx.* → (using Sge)`, the single
largest engine-to-adoption seam identified by §11.9) and `NullabilityTransform` are both shipped,
base-manifest-scoped policy every dependent inherits (§1.5). Their residues live at each port's own
`Current state`/`Residues` section above (context seams, `nullability-boundary`) — nothing left open
at this heading.

### 11.15 P1 — `Disposable → AutoCloseable` — DELIVERED

Cited by `ENGINE-LIMITS.md`. `Substitutions`-shaped drop+inject plus one member-rename key
(`dispose() → close()`), base-manifest scope, first production firing of the surface-merge fold. No
open residue at this heading.

### 11.17 P3 — the `@Null` union floor — DELIVERED on libGDX, REFUSED on screens

Cited by `ENGINE-LIMITS.md`. Delivered as `NullabilityTransform`'s default target; `sge-screens`
refuses it because the shape it guards against does not occur in that library — a fact, not a gap.

### 11.25 P6 — the opaque families — DELIVERED

Cited by `ENGINE-LIMITS.md`. First engine step whose gate was a DEPENDENT's suite (anim8/gltf/etc
re-measured). "sge's ~200 `GL_*` values are hand-authored constants with no Java source" — recorded
so it is not re-derived as a gap.

---

## 11.99 The idiom layer — wave 0's published denominators

Cited by `ENGINE-LIMITS.md`. `SamLambda`/`NarrowedReturn` idiom conversion, with the refusal
population as the load-bearing lane (`CLAUDE.md` §3's rule: a refused-population count is the only
honest evidence for an idiom transform, not the converted count). Per-port current numbers live in
each port's own `Current state` table (`idiom(converted/refused/residue)`). I9 closed while finding a
live silent defect in libGDX core (T15, receiver-is-an-operand); Wave 1's blast was predicted before
being measured.

---

## 12. Remaining work, across the engine

Maintained by deletion. Items ordered by what they block, not by size.

### 12.1 Provenance coverage — decisions not yet recorded

Cited by `ENGINE-LIMITS.md`. Open gaps in `decisions.tsv`/porter-note coverage:

- A REFUSAL (a policy entry the phase declined, `subject = SymId.None`) renders no porter note —
  reaches `decisions.tsv` and `findings.tsv` but never the emitted line (measured by P4: 5
  `bean-properties` refusals, 0 notes). `PorterNote.InBody` on the owning type is the shape.
- A base's per-entry refusals are republished in every dependent's `decisions.tsv` (the one decision
  kind with no declaration to scope by D2) — fix is to scope by the OWNER FQN the key names.
- Raw-generic `[?]` rendering and `uncheckedGeneric` retyping are unrecorded.
- A retyped PARENT records nothing (`CollectionsTransform.recordRetypings` reads `Symbol.info`, not
  `Definition.parents`) — measured by P2: 9 classes across 5 ports with changed members and zero
  `Configured` rows.
- A scoped-out PARAMETER records no decision against its owning METHOD
  (`NullabilityTransform.scopedOut`) — measured on screens: only one of two needed scope entries
  produced a row.
- A RETYPED parameter is the same hole on `PrimitiveToOpaqueTransform.record`'s side.
- `RetypedSignature` and `RedirectedCall` carry no porter note by design (`DESIGN.md` §7.2) — listed
  so adding one is a decision, not an oversight.

### 12.1.5 Base-surface sites the published map cannot answer

Five sites migrated (funnel fixpoint, class/object collapse, ctor-replay `vis=`, §4.55's two rename
passes, `export` exclusion `statics=`). Still open, each needing a contract-map column: promoted
constructor parameter renames (no emitted declaration to carry a row), parent-alignment rendered
TYPE (`sig=` needed beside the erased `primary=` descriptor), whether a base parent method is
CONCRETE (`concrete` flag), and whether a base declaration carries a `(using C)` clause / is
opaque-retyped / is inside a dependent-grown `RuleScope.Only` (`usingClause=`/`retyped=` columns).
Armed but not yet triggered — no port's declared holder/`OpaqueSpec`/`RuleScope.Only` reaches a base
declaration yet.

### 12.2 Control flow and residues

- `labelSeq` is program-global, so a control-flow diff is never file-local (measurement cost, not a
  defect).
- `catalog(unreached)` grows by one on every port each time a modern-java (SE14+) row is mechanised
  — expected and evidenced only by fixtures, since no corpus library is written past Java 8.
- `TirEmitter.diamondOverrides`, `TypeRedirectTransform`, `PortMapTransform` are three more loose-key
  `(name, arity)`/`owner#name` member indexes with no measured site yet (§10.9.7 family 5's sibling
  walks) — flagged, not yet fixed (§12.3.5).
- Drop notes print `key=` twice in three `PortRun` loops (`dropTypes`/`dropMethods`/
  `supportSources`) — cosmetic, unfixed (§12.4).
- `RegexConstructCheck` for Scala Native's RE2 unsupported-construct list is priced and not built —
  the Scala.js side of the list was never fetched, and a check asserting coverage it lacks is the
  failure mode the catalog exists to avoid.

### 12.2.5 `findings.tsv` — now gated

Cited by `CLAUDE.md`. Was a committed baseline no lane compared; `findings_baseline_guard` now
diffs the id-stripped file in either direction. Found and fixed: eight stale dependent baselines
(accepted at waves 0/1, never re-accepted after the base's declaration count moved 280→220) —
re-accepted from a fresh sweep. `port-map.tsv` had the same shape and is now gated too (§12.4.6).

### 12.2.6–12.2.9 Remediation menus — CLOSED, current state

`substitutions-drop`/`static-forwarder-inline`/`class-table`/`accept-jvm-only` ship with fixture
coverage; `portability(emitted)` now correctly excludes dropped types on a renaming port (was
`153 = (all)` everywhere, fixed — `ENGINE-LIMITS.md` P7). `serviceProviders` (P5) is closed — liqp's
hand-written `META-INF/services` file is deleted, the run emits the renamed equivalent. `omissions`
and `jdk-surface` menus are live with four selections across three ports (gdx-gltf, gdx-vfx ×2,
anim8, liqp-test), draining `omissions` 97→92, `jdk-surface` 57→55, `remediation(resolved)` +7.

### 12.2.8 Deliberately not built

Cited by `ENGINE-LIMITS.md`. Reflective-instantiation-to-registry as a §1(b) phase — READ OUT AND
REFUSED (`ENGINE-LIMITS.md` P10): the three hand-written instances (`Pools`, `ComponentFactories`,
`GLTFExtensionFactories`) need a `Class`-keyed `Map`, which the runtime package's own admission test
excludes, and the extraction would save only 8 net lines across three ports. The loud-refusal facade
(static half + throwing reflective half) stays hand-written — its split is a semantic fact about
each library's API (§1(c)), not a scaffoldable shape.

### 12.3 Counted residues that are not defects

- Modern-java (SE14+) catalog rows are structurally unreachable by any corpus port (all written to
  Java 8 or below) — evidenced by fixtures only, not corpus sites.

### 12.4.5–12.4.7 Platform matrix and dependency declaration — WIRED

`PortabilityCheck` is now a §1(b) phase (`PortManifest.targets`). `dependency-coverage` (declared
artifacts vs. usage) is wired on three ports (libGDX core: `scala-java-locales`; liqp: + `scala-
java-time`/`-tzdb`), draining their residue to 0 with `(all)` flat — the enumeration still runs, only
coverage subtracts. Two coordinate defects found and recorded (not fixed): four of five artifacts
need `%%%` not `%%` (per-platform publishing) — fixed in the survey; `com.dedipresta:scala-crypto`
has no Scala 3 build at all — no corpus port uses it, open for whoever ports a hashing library.
`java.util.ServiceLoader` maps to `com.kubuszok %%% multiarch-serviceloader` (`DESIGN.md` §8.19),
closed end-to-end on liqp including the `dependency-coverage(declared)` 2×2 (`policy` 1→0). Accepted
cost: liqp's declared-row check needs network (the artifact is a Central Portal snapshot); offline it
degrades to `Unverifiable`, never a wrong instruction.

### 12.4.6 `port-map.tsv` gated

Cited by `CLAUDE.md`. Same shape as §12.2.5, one artifact over — publishing `Surface.MemberShape.form`
moved 60 base member rows unacknowledged; nine dependent maps had stale `policy=` headers for days.
`port_map_guard` now diffs the whole file (rows verbatim, header field by field) in either direction,
gated across all fifteen maps, negative-tested on a real lane.

### 12.5 Not run

- `PortMapAcceptanceSpec` `assume`s itself out from a worktree (path-shape mismatch, pre-existing,
  unrelated to catalog work) — a green `testOnly *` from a worktree does not mean this spec ran.
- The Auditor has not run over this delivery — user-run only, once a whole piece of work ships.

---

## 13. The parity campaign — every module, every sge/ssg adjustment, every limit (decided 2026-08-25)

The goal: an agent in sge or ssg regenerates its port from upstream java WITHOUT editing this
repository. What separates emitted code from the hand ports is per-module policy those agents would
otherwise invent, plus every engine gap that keeps such policy from being expressible. Decided
2026-08-25, binding for every wave until this section is empty:

| question | decision |
|---|---|
| scope of "adjustments" | EVERYTHING the hand ports did gets an explicit spelling HERE — a manifest key, a (b) parameter, a plugged-in (c) rule, or an injection. No natural spelling ⇒ engine gap ⇒ fixed here. |
| done bar per module | DROP-IN: the emitted tree replaces the module's `Ported from` files inside sge/ssg's own `projectMatrix` build; JVM + JS + Native compile, and the full suite (upstream tests + hand-added tests, copied verbatim) passes on every platform the hand port runs it on. |
| API parity | EXACT — SGE-/SSG-original files, demos and `sge-it-*` are never edited. The emitted public surface equals the hand port's: names, arity, nullability spelling, packages, companions, operators. |
| skipped classes | only with a stated justification recorded in `decisions.tsv`. "The previous porter skipped it" is not one. |
| java vs hand-port behaviour | java is the default contract. Every divergence goes through the `divergence-investigator` agent: justified ⇒ a NAMED rule/injection; unjustified ⇒ a hand-port defect, adapted or dropped with the finding recorded. |
| `ENGINE-LIMITS.md` | FIX EVERYTHING. No entry may end open, limit, refused or do-not-retry without a measured exit. |
| non-java hand ports | port the java sge KEPT; the non-java half (Rust freetype, GLFW controllers, Rapier, platform bindings) stays hand-written `src/`. gdx-box2d/bullet and the non-java ssg modules are out by construction. |
| CI / publishing | sge/ssg's job once they consume; the `just` lanes are the gate here. |
| vendored upstream trees | re-pinned to the commit sge/ssg's submodule holds; a mismatch is FATAL. |

### 13.1 Phases — open work

**Phase 0 (instruments) and Phase 1 (parity mechanisms) are built**: drop-in lane, `api-parity`
check (scalameta both sides, 15 divergence families, `unclassified` gate), `divergence` census,
cross-platform COMPILE gate in 20 of 23 lanes, reference-build-flags compile (`.ref`, `flags_compile`),
JDK pin + guard (`jdk_guard`, port-map `jdk=` schema field, fatal on mismatch), `dependency-coverage`
2×2, `multiarch-serviceloader` wrapper.

**Phase 2 — `ENGINE-LIMITS.md` drained.** Checklist as declared 2026-08-25 (status per entry has
moved since; per-port current residues are each port's own section above, not restated here):
OPEN 0.1, 0.2, G24, G33, C11, T7, T8, T9, T22, T23, K6, K9, K14, K15, K18.1, K28, K28.1, K32, K33,
P9, M10, F7, CT10, CT11, O3, O4, G12, T11.5, T16.5, X4/X5/X6 residues, V1; plus the hygiene that
blocks them — the 142 bare catches in `SpoonTir.scala`, `CollectionsTransform.scala` split into its
four mechanisms, `Tree.Unportable` mint sites, the test framework as policy.

**Phase 3 — modules to the drop-in bar**, base first: `sge` core (578 files + 71 backend-derived
files via a `platformDirs` key); the existing dependents; `sge-colorful` (`colorful/` and
`colorful-pure/`); `sge-freetype`, `sge-controllers`, `sge-tools`, `sge-jvm-platform-android`;
`ssg-md`'s deferred tier and 28 hand-port omissions; `ssg-liquid` against the hand parser kept as
`src/`.

### 13.3 Wave 2.1 — `SpoonTir.scala` bare-catch census — CLOSED

Cited by `ENGINE-LIMITS.md`. History in git; the census closed at 0 emitted bytes moved on four
ports.

### 13.13 Wave 3.0 — `api-parity` instrument turned on for every port with a hand-port twin — CLOSED

Cited by `DESIGN.md`. History in git; first corpus-wide census, per-port families are each port's
own `api-parity` numbers where they exist (§10.7.12, §10.8.13, §10.9.12).

### 13.26 Current residue — compile errors under the reference build's own flags (`.ref`)

The bar this campaign measures against (`CLAUDE.md` §5's fourth compile, `-Werror` promoting every
warning). Per-port breakdown, classification and Do NOT retry lists are in each port's own section
above; this is the corpus-wide summary.

| port | `.ref` errors | dominant family |
|---|---:|---|
| `sge` / `sge-test` | 1 / 1 | 2026-09-05 (was 51): one provably-false type test at a FINAL retarget target (`Label`: `CharSequence` vs `DynamicArray[?]`, K18) — finality of an external type is a class-file fact the frontend does not intern yet |
| `sge-ecs` / `sge-ecs-test` | 0 / 0 | closed |
| `sge-ecs` drop-in (JVM/JS/Native) | 408 / 408 / 408 | `-no-indent` cascade from the injected `ComponentFactories.scala`, not in `measure-all` |
| `sge-anim8` | 6 | same `-Wunused` family |
| `sge-gltf` / `sge-gltf-test` | 3 / 3 | D4 (§8.3) — genuine, tracked at every flag level |
| `sge-screens` | 29 | guacamole's JVM-only logging surface (c, per-library, §9) |
| `sge-vfx` | 2 | `-Wunused` family |
| `sge-ai` / `sge-ai-test` | 0 / 0 | closed 2026-09-05 (deprecated boxing ctors -> `valueOf`, JS-E19; class-file `@Deprecated` interned) |
| `sge-textra` | 0 | closed 2026-09-05 (the `remove(K, default)` Templates simplified) |
| `sge-graphs` / `-test` | 0 / 0 | closed |
| `sge-noise` | 0 | closed |
| `sge-jbump` | 0 | closed |
| `ssg-liquid` / `-test` | 3 / 3 | K18/G24 residue (§10.5) |
| `ssg-md` / `-test` | 127 / 136 | `-Wunused` family, largest port (main 136 -> 127 on 2026-09-05; test unmeasured since) |
| `ssg-md-ext` | 183 | `-Wunused` family (2026-09-05: 187 -> 183; the admonition SVG resources are now declared and shipped, the suite no longer dies in `<clinit>`) |
| USL / USL-test | 3 / 3 | `-Wunused` family |
| `sge-visui` | 3 | three `OnscreenKeyboard.show(Boolean)` sites: gdx 1.14.0 vs vendored 1.14.1 (§3.5's fourth question), counted |

### 13.23 Wave 4.0 — ports as sbt subprojects: measured cost of a lane

Migrated from `scala-cli --server=false` cold compiles to `port-*` sbt subprojects with a warm
per-worktree server (`SBT_GLOBAL_SERVER_DIR`, fixing the shared-socket hang of `ENGINE-LIMITS.md`
M5.11). Every count and `tests.tsv` row identical to the scala-cli lane at the same commit.

| lane / step | before (scala-cli) | now (sbt subprojects) |
|---|---|---|
| `gdx-measure`, cold server | ~25 min | 257 s |
| `gdx-measure`, warm, nothing changed | ~25 min | 131–166 s |
| one-row policy iteration + `gdx-measure` | ~27 min | 172 s |
| `gdx-measure-full` (JVM+JS+Native+`.ref`) | ~25 min | 237 s |
| `gdx-test-measure` (191 tests) | ~20 min | 117 s |
| `ashley-measure` (dependent, 112 tests) | ~20 min | 199 s |
| engine suites (api/engine/corpus/frontend-spoon), warm | — | 108 s |

Residue: the migration (Spoon parse of ~600 java files + pipeline + checks, ~100 s) is now the
bottleneck; a per-step timer and a parse cache keyed on the vendored tree's hash are the next
instrument and the next candidate respectively.

### 13.27 Body substitutions — audit (2026-09-04)

Every `MethodBodyTransform` key in the corpus, classified per the design card (§8): (i) hand-port
divergence with a recorded verdict, (ii) engine gap papered over, (iii) reflection replacement
(Phase 1.11 `RegistryTransform`, P10), (iv) upstream version break (§3.5 fourth question). No
`.conf`-spelled `bodies`/`methodBody` keys exist (grep over `balticporter/corpus/ports/*/*.conf`
hit only prose). Grouped rows share one classification; every key is listed.

| key(s) | port | class | replacement (item/id or verdict row) | note |
|---|---|---|---|---|
| `VisTextField#focusField`, `#next(boolean)`, `$TextFieldClickListener#touchDown(...)` | visui | iv | item 6 | gdx 1.14.0 (VisUI target) vs vendored 1.14.1 `OnscreenKeyboard.show` |
| `Dialogs#getStackTrace(Throwable)`, `#getStackTrace(Throwable,CharArray)` | visui | ii | item 7 | missing retarget row `CharArray#append(Object)` |
| `JsonMatcherTests#toString(JsonMatcher,String[])`, `#toString(Array)` | gdx-test | i | uncited — no `ported/sge/divergence-verdicts.tsv` | comment 3.1ae: sge dropped CharArray builder API |
| `SelectBox#getSelectedIndex`, `List#getSelectedIndex` | gdx | ii | K37 | `collection-internal`: `OrderedSet <: ObjectSet` has no image in lls |
| `PixmapBinaryLoaderHack#load`, `GLTFBinaryExporter#savePNG` | gltf | i | uncited — no `ported/sge-gltf/divergence-verdicts.tsv`; cites CLAUDE.md §3.5 in-line | GWT reflection workaround; hand port makes the direct call instead |
| `GLTFMaterialExporter#ext` | gltf | iii | Phase 1.11 / P10 | Class-keyed factory registry (`GLTFExtensionFactories`), same shape as Ashley's |
| `TextraListBox#getSelectedIndex`, `TextraSelectBox#getSelectedIndex` | textra | ii | K37 | `collection-internal`: `OrderedSet` does not extend `ObjectSet` |
| `VfxGLUtils#<clinit>`, `VfxFrameBuffer#getBoundFboHandle`, `VfxGLUtils#getBoundFboHandle` | vfx | iii | Phase 1.11 / P10 | `<clinit>` reflectively probes a GWT-only extension (`ClassReflection.newInstance`) before `new DefaultVfxGlExtension()`; the two accessors carry the lazy init the emptied `<clinit>` no longer performs (CT11 shape) — all three fall with the reflection card, not with item 5 (re-read 2026-09-05) |
| `CircularBuffer#resize(int)` | ai | iii | Phase 1.11 / P10 | `ArrayReflection.newInstance` — gdx reflection the base drops; measured alive 2026-09-04 (`value ArrayReflection is not a member`) |
| `Task#cloneTask()` | ai | iii | Phase 1.11 / P10 | the java falls back to `ClassReflection.newInstance(getClass())`, a reflective self-clone the port drops; the body keeps `TASK_CLONER` and throws the contract's own `TaskCloneException` — a registry (P10) is the mechanical image, not an emitter (reclassified 2026-09-05) |
| `...openTask(String,boolean)`, `...findMetadata(Class)`, `...getField(Class,String)`, `...setField(Field,Task,Object)`, `...castValue(Field,Object)` (`BehaviorTreeParser$DefaultBehaviorTreeReader`) | ai | iii | Phase 1.11 / P10 | named verbatim in the design card as the reflection-replacement family |
| `Engine#createComponent(Class)` | ashley | iii | Phase 1.11 / P10 + `divergence-verdicts.tsv:146` (`Engine#componentFactories`, justified) | `ComponentFactories` registry |
| `ImmutableArrayTests#forbiddenRemoval` | ashley | i | `divergence-verdicts.tsv:141` (`ImmutableArraySuite`, justified) | java `iterator().remove()` throws; Scala's `Iterator` has none — verify read-only instead |

Counts by class: i=5, ii=18, iii=8, iv=3 (34 keys, 19 grouped rows); retired 2026-09-04: `Selection#iterator`, `MapLayers/MapObjects#getByType`; 2026-09-05: `BitmapFont#<init>(…)` (dead key), `Selection#toArray` x2, vfx x3 reclassified iii; 2026-09-05b: `FirstPersonCameraController#keyUp` (IntIntMap remove Template), `Node#calculateBoneTransforms` + `ModelInstance#invalidate` (IndexedField via), `NodePart#set` + `MapProperties#putAll` (putAll compiles without cast); 2026-09-05c: `Actor#<clinit>` (Construct at C::new), `AssetManager#getAssetFileName` (return inside nested foreachEntry), `ModelLoader#getDependencies` + `ParticleEffectLoader#getDependencies` (Tuple2 construct-then-assign fold); 2026-09-05d: `AssetManager#clear` (Keys toArray Template + getAndIncrement), `ArraySelection#validate` (OrderedSet removing iterator K36), `AssetLoadingTask#removeDuplicates` (DropWrite K36) (ii=10 remain).
Counts by port: visui=5 (iv=3, ii=2), gdx=9 (i=2 test, ii=7 main), gltf=3 (i=2, iii=1),
textra=2 (ii=2), vfx=3 (iii=3), ai=7 (ii=2, iii=5), ashley=2 (i=1, iii=1).
