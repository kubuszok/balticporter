# Baltic Porter — progress

**The state of every port, and of publishability.** This is the only status document; the design and
the reasons are `DESIGN.md`, the measured dead ends are `ENGINE-LIMITS.md`, the rules are `CLAUDE.md`.

Nothing here is history. A remaining-work list is maintained by **deletion** — a done item is removed,
not moved to a "done" section — and a lesson worth keeping is lifted into `DESIGN.md`,
`ENGINE-LIMITS.md`, `CLAUDE.md`, a skill or an agent brief rather than left in a status line
(`CLAUDE.md` §3.6).

---

## 0. Reproducing every number here

Every lane is a recipe in the root `Justfile` (`just` with no argument lists them):

```
just gdx-measure          # libGDX core        (emit → checks → break residue → compile → correlate)
just gdx-test-measure     # libGDX's own suite (… → compile → RUN → correlate)
just ashley-measure       # Ashley + its suite, compiled WITH libGDX core
just anim8-measure        # anim8-gdx, compiled WITH libGDX core (a dependent port; hand-written suite, §7)
just gltf-measure         # gdx-gltf + both its suites, compiled WITH libGDX core (§8)
just screens-measure      # libgdx-screenmanager, compiled WITH libGDX core (hand-written suite, §9)
just vfx-measure          # gdx-vfx, compiled WITH libGDX core (a dependent port; hand-written suite, §10)
just sg-measure           # simple-graphs + its suite
just noise4j-measure      # noise4j (no upstream test suite — the lane asserts that, see §5)
just jbump-measure        # jbump — a library that ships NO suite; the lane re-derives that zero (§6)
just measure-all          # every lane above, serially, stopping at the first failure

just decision-counts      # decisions.tsv row counts by kind, every port
just members-unchanged    # members.tsv against its baseline — the blast radius, before a compile
just baseline-accept <port>   # promote a baseline (also: baseline-list / -show / -diff)

just debug-flags [<port>] # which layer defines each §4.6 flag, and what a run recorded
just debug-set / -clear   # the hand-written flag layer, .balticporter/debug.properties
just debug-emit <root> <fqn> [<phases>]   # one type as TIR + Scala, around a phase boundary
just correlate <out> …    # CorrelateMain on a log you produced by hand (§5.1)
just debug-selfcheck      # proves the four above — no sbt, no ports, seconds
```

Run them **serially** — `measure-all` does: each re-emits into `src_managed/` and the dependent lanes
compile against what the base lane just wrote. Every lane refuses to measure stale output if its
migration did not run, and every one prints the full check report diffed against the committed
baseline, not a filtered selection of it. The mechanism they share is `scripts/_lib.sh`; the policy
(sbt projects, upstream trees, dependency coordinates) is variables at the top of the `Justfile`.

Measurements below are from one serial run of all lanes, 2026-07-31.

---

## 1. Corpus inventory

Nine libraries are ported on the current (TIR) pipeline, across thirteen runs — a library and its own
test suite are two ports, and the suite is a *dependent* of the library:

| port | upstream | files in / out | tests | compile |
|---|---|---|---|---|
| `libgdx-core` | libGDX `gdx/src` | 604 in → **598 out** (11 dropped, 6 injected) | — | **0** |
| `libgdx-test` | libGDX `gdx/test` | 29 → **29** | **221**, 217 pass / 4 expected-fail | **0** |
| `ashley` | Ashley `ashley/src` | 21 → **21** (2 injected) | — | **0** |
| `ashley-test` | Ashley `ashley/tests` | 18 → **18** | **112**, 108 pass / 2 fail / 2 skipped | **0** |
| `anim8` | anim8-gdx `src/main/java` | 16 → **16** (0 dropped, 0 injected) | **23** hand-written, all passing — upstream has NO suite (§7.1) | **0** |
| `gltf` | gdx-gltf `gltf/src` | 135 → **135** (0 dropped, 1 injected) | — | **8** (§8.4, all classified) |
| `gltf-test` | gdx-gltf `gltf/test` | 1 of 7 → **1** (§8.1) | **8** ported + **22** hand-written, **none run** — the port does not compile | — |
| `screens` | libgdx-screenmanager `src/main/java` | 22 → **22** (0 dropped, 0 injected) | **16** hand-written, all passing — upstream's 12 need an unported BACKEND (§9 libgdx-screenmanager) | **0** |
| `vfx` | gdx-vfx `core/src` + `effects/src` | 44 → **44** (0 dropped, 0 injected) | **64** hand-written, all passing — upstream has NO test SOURCE SET (§10.1) | **0** |
| `simple-graphs` | simple-graphs `src/main` | 29 → **33** | — | **0** |
| `simple-graphs-test` | simple-graphs `src/test` | 7 → **7** | **16**, all passing | **0** |
| `noise4j` | noise4j `src` | 12 → **12** | **none upstream** (§5) | **2** |
| `jbump` | jbump `jbump/src` | 19 → **23** | **none upstream** — gated by a differential probe instead, §6.2 | **0** |

**A frozen BIR path still exists.** Nine corpus programs — liqp, flexmark, the xwiki-macros cold-port
closure, jbump and their demos — predate the TIR and run on the string-oriented BIR printer
(`DESIGN.md` §2.2). Headers on `Bir.scala` / `SpoonFrontend.scala` / `ScalaPrinter.scala` name them.
The xwiki-macros closure is the furthest that path got: 501 files emitted from a 533-file closure with
0 untranslated constructs, burned down to roughly 60 compile errors, at which point the mechanical
per-fix yield was exhausted and the remaining classes needed whole-file overrides. It is not on the
current pipeline and none of its numbers are comparable with the table above.

### 1.1 What is available and unported

Each ported Java library is meant to be handed to a **separate agent** supervising that library's port
from that library's own repository. The hand-off packet is: what the module is, which upstream it came
from, how big the job is, and — the column that matters — **what the reference hand port silently did
NOT do.** `CLAUDE.md` §3.5 is the governing rule: a construct that merely vanished from the hand port
is unported work with **no reference implementation**, and it is systematically the part the per-repo
status docs under-report.

Measured 2026-07-29 by direct `find`/`wc` and a type-name census, not from the repos' own status docs.
Where a doc disagreed with the tree, the tree won.

#### sge — 16 ports, sorted by upstream size

| module | upstream | Java files / LOC | Scala files / LOC | coverage | tests | licence |
|---|---|---|---|---|---|---|
| `sge` | libGDX `gdx/src` (+63 cherry-picked backend files) | 605 / 147,163 | 692 / 153,687 | **83 %** (100 absent; ~14 in `../lls`) | 265 / 2,185 | Apache-2.0 |
| `sge-colorful` | colorful-gdx `colorful/` | 54 / 62,656 | 46 / 38,721 | **100 %** of `colorful/`; `colorful-pure/` (38 files, 40k LOC) 0 % | 7 / 54 | Apache-2.0 |
| `sge-textra` | TextraTypist | 95 / 38,607 | 92 / 28,054 | **100 %** | 32 / 239 | Apache-2.0 + **MIT** |
| `sge-visui` | VisUI `ui/` + `usl/` | 182 / 27,236 | 156 / 20,639 | **88 %** — USL 0 of 18 | 12 / 72 | Apache-2.0 |
| `sge-anim8` | anim8-gdx | 16 / 19,594 | 16 / 9,747 | **93 %** | 4 / 21 | Apache-2.0 |
| `sge-ai` | gdx-ai | 167 / 18,086 | 134 / 14,039 | **93 %** | 24 / 196 | Apache-2.0 |
| `sge-tools` | libGDX `gdx-tools` | 80 / 17,773 (8 ported) | 8 / 3,857 | **10 %** — deliberate | 2 / 10 | Apache-2.0 |
| `sge-gltf` | gdx-gltf | 135 / 11,307 | 141 / 17,615 | **100 %** | 40 / 150 | Apache-2.0 |
| `sge-vfx` | gdx-vfx | 45 / 5,732 | 41 / 3,881 | **91 %** — upstream now ported by the engine, §8 | 6 / 29 | Apache-2.0 |
| `sge-jbump` | jbump | 19 / 4,045 | 14 / 2,054 | **73 %** | 7 / 32 | Apache-2.0 |
| `sge-graphs` | simple-graphs | 29 / 3,784 | 25 / 2,525 | **82 %** | 8 / 77 | MIT |
| `sge-controllers` | gdx-controllers | 29 / 2,884 | 18 / 2,141 | **27 %** — deliberate | 6 / 33 | Apache-2.0 |
| `sge-ecs` | Ashley | 21 / 2,523 | 24 / 2,404 | **100 %** | 18 / 172 | Apache-2.0 |
| `sge-noise` | noise4j | 12 / 2,491 | 10 / 2,608 | **83 %** — upstream now ported by the engine, §5 | 3 / 13 | Apache-2.0 |
| `sge-screens` | libgdx-screenmanager | 23 / 2,459 | 20 / 1,691 | **86 %** | 6 / 29 | Apache-2.0 |
| `sge-freetype` | libGDX `gdx-freetype` | 4 / 1,891 | 9 / 2,365 | **100 %** of the Java layer | 9 / 28 | Apache-2.0 |
| `sge-anim8` | Its `ConstantData` blobs are **WRONG** — externalised to `.bin` resources holding the UTF-8 encoding of the characters instead of `getBytes(ISO_8859_1)`, so every array is 1.43× too long and corrupt from its first non-ASCII byte, and its own suite pins the wrong lengths (§7.4). Also skipped `FastAPNG` and hoisted `Dithered.DitherAlgorithm` to a top-level type. |

**Not ports — do not assign these to a porting agent.** `sge-physics` / `sge-physics3d` are original
Scala over the Rust Rapier crates, deliberately not Box2D-shaped (`gdx-box2d`, 238 Java files, and
`gdx-bullet`, 636, are entirely unported and cannot be poured into that API); `sge-test/*`, `demos/`
and `sge-build/` are harnesses and separate builds. `sge-jvm-platform-api` / `-android` need a
**decision before assignment**: 32 of 57 files carry a `Source: com.badlogic.gdx.backends.android.*`
line but no port banner, covenant, migration row or upstream pin.

#### ssg — 2 Java ports

| module | upstream | licence | Java files / LOC | Scala files / LOC | coverage | tests |
|---|---|---|---|---|---|---|
| `ssg-md` | flexmark-java 0.64.8 | BSD-2 | 912 / 83,680 | 773 / 77,468 | 792 mapped 1:1; **24 undocumented omissions** | 205 / 34,387 |
| `ssg-liquid` | liqp 0.9.2.4-SNAPSHOT | MIT | 135 / 9,542 | 130 / 10,925 | 124 of 135; **11 absent** | 65 / 1,030 |

Package renames the engine needs: `com.vladsch.flexmark.* → ssg.md.*` (43 Maven modules collapse into
one; `util.builder → util.build`) and `liqp.* → ssg.liquid.*`.

The other ssg modules (`ssg-js`, `ssg-katex`, `ssg-mermaid`, `ssg-sass`, `ssg-minify`,
`ssg-graphs-commons`) *are* source-level ports — of JavaScript, TypeScript, Dart and Ruby, which the
Spoon frontend cannot read. `ssg-highlight` is an FFI/WASM binding. `ssg-graphviz`, `ssg-commons`,
`ssg-data-commons` and `ssg-site` are original Scala. Corroborated independently: not one of them
contains a single `Ported from:` header.

Size, stated correctly: flexmark is **1.5× libGDX core by file count but 0.57× by lines** (912 files /
83,680 LOC against 605 / 147,163). Which is "bigger" depends on whether the engine's cost is per file
or per construct.

#### Three facts that will surprise whoever picks this up

1. **The repos' own coverage databases are not reliable.** `sge/.rescale/data/migration.tsv` has 36
   rows that are a spilled Java stack trace, no rows for 11 on-disk `gltf/exporters/*.scala` and none
   for the 26 ported GWT-backend files; `ssg`'s marks 10 ported `liqp/filters/*` as `skipped`; a
   flexmark status doc claims a module is "11 files, Pass" when only 6 of 11 types exist. **Use the
   per-file `Ported from:` headers as the oracle** — they matched the on-disk census in every module
   checked.
2. **Nobody hand-ported a single upstream test.** sge's ~3,500 test cases are hand-written MUnit; liqp's
   65 suites are rewrites. Available and unported: libGDX 430 Java test files, colorful-gdx 162,
   textratypist 145, gdx-ai 113, vis-ui 32, ashley 31, liqp 105. Baltic Porter has now
   ported libGDX's 221 core tests, Ashley's 112 and simple-graphs' 16 end to end, so the capability is
   proven — but per `CLAUDE.md` §3 this remains the largest single gap in the hand-off, and it is
   invisible in every status doc because none of them tracks tests.

   **And "20 test files" is not "20 tests": anim8-gdx's are DEMOS.** This row said `anim8-gdx 20` until
   that port was attempted; the 20 files hold **zero** `@Test` annotations and every one of them is an
   `ApplicationAdapter` or a startup bench needing a libGDX backend (§7.1). Counting test FILES
   over-reports the available suite, and it is the direction that turns "port the tests" into a
   surprise. Nothing here re-counted the other rows by annotation, so treat them as upper bounds.
3. **libGDX's `utils` is split across THREE repos.** 14 core types (`ObjectMap`, `ObjectSet`,
   `OrderedMap`, `Sort`, `TimSort`, `MathUtils`, `Select`, `ArrayMap`, `DynamicArray`, …) live in a
   sibling repo `../lls`. Coverage measured against `sge/` alone under-reports, and an engine run that
   does not know this re-derives types that already exist.

#### The one surprise per module

| module | the surprise |
|---|---|
| `sge` core | **No backend was ported.** 63 cherry-picked files out of 314; LWJGL3/GWT/Android/RoboVM are original Scala over Rust FFI, GLFW and multiarch. An engine-driven port yields a library that cannot open a window. |
| `sge` core | The whole **LZMA `utils/compression` stack (13 files) vanished** → `.ubj.lzma` fonts unsupported. Also gone with no replacement: all 7 `assets/loaders/resolvers/*`, all `Json*`/`UBJson*`/`XmlWriter` (11), the HTTP stack (reimplemented on sttp-client4). |
| `sge` core | `(using Sge)` appears **1,402 times across 363 files** — the port's most pervasive shape change, with no mechanical Java analogue. |
| `sge-controllers` | 27 % is **not a gap** — Jamepad and GWT were replaced by GLFW and the browser Gamepad API. Porting the missing 21 types produces dead code. |
| `sge-visui` | The **USL skin-DSL compiler is 100 % absent** (18 files: lexer, parser, style merger, JSON writer) and undocumented. Self-contained real work, zero reference implementation. |
| `sge-textra` | Upstream ships a **second licence** — `typing-label.LICENSE`, MIT — covering exactly the `TypingLabel` + 40 `effects/*` files sge ported. **No MIT attribution appears anywhere in sge.** |
| `sge-ai` | `@TaskConstraint`/`@TaskAttribute` + the reflective behaviour-tree parser were replaced by a hand-written `TaskRegistry` of factory closures. **Pure redesign — no mechanical rule produces it.** |
| `sge-tools` | 90 % deliberately dropped (all Swing/AWT editors). Not an incomplete port. |
| `sge-freetype` | Java layer 100 % ported, but every `native` method now binds a Rust crate. Carries a **deliberate behavioural fix upstream lacks** — do not "correct" it back. |
| `sge-graphs` | Least idiomatic module: raw `null.asInstanceOf`, anonymous SAM classes, zero `Nullable`, zero renames. Any conformance check will flag it heavily — expected, not a defect. |
| `sge-jbump` | **Five of nineteen upstream types are absent and one is a name collision, not a port**: `Extra`, `util/{BooleanArray,FloatArray,IntArray,IntIntMap}` are gone, and the hand port's `util/MathUtils` holds `Extra`'s three members — upstream's own 352-line `MathUtils` (sine table, `clamp`, `lerp`, the `random` family) is 0 % ported under a name that suggests otherwise. `Collisions` also drops `Comparator<Integer>`, `compare`, `order` and three of four `keySort` overloads for a `boxed`/`applyPermutation` redesign. (The copy-constructor loss this row used to name has since been fixed by hand in sge — verified 2026-07-31 at `Collisions.scala:51`. Its upstream is now ported by the engine: §6.) |
| `sge-noise` | The 17 % gap is `Array2D` and `Object2dArray` — the whole `array` package bar `Int2dArray`, absent with no note. And every one of the three **enum constant bodies** was REDESIGNED into a flat `enum` plus a `this match`, which no mechanical rule produces; the engine emits `sealed abstract class` + `case object` instead (§5). |
| `sge-screens` | `NestableFrameBuffer` missing → screens binding their own FBO rebind incorrectly. |
| `sge-colorful` | `colorful-pure` (40k LOC) unported with **no recorded rationale anywhere**. Confirm intent before treating it as scope. |
| `sge-visui` vs core | VisUI keeps `AsyncTask` as a class while core maps it to `() => Unit`. **Two answers to one construct in one repo** — the manifest must decide which. |
| `ssg-liquid` | **2,166 lines have no Java counterpart and never can.** liqp parses with two ANTLR `.g4` grammars; the port hand-wrote lexer/parser/token. The engine cannot read, regenerate or diff `.g4`. Decide up front: permanent handwritten overrides, or re-adopt ANTLR and lose JS/Native. |
| `ssg-md` | **The green test numbers do not measure CommonMark conformance.** All 7 spec files in `src/test/resources` are loaded by no runner. The 24 undocumented omissions include the entire `util/html/ui` subpackage and a public type of the tables extension that is silently absent. |

#### Attribution gaps to close before publishing anything

- **sge**: TextraTypist's MIT `typing-label.LICENSE` is unacknowledged; `sge-tools` and `sge-freetype`
  are absent from `THIRD-PARTY-LICENSES`.
- **ssg**: `NOTICE` names flexmark only — not liqp (MIT), terser, KaTeX, Mermaid, dart-sass, rough.js
  or the Ruby gems. Per-file headers exist, so this is arguably compliant, but the NOTICE is incomplete.

Baltic Porter emits provenance headers, so a re-port fixes the per-file half automatically. The
NOTICE / THIRD-PARTY files are hand-maintained and are not.

### 1.2 Suggested assignment order

1. (Tier 1 is complete: `sge-ecs`, `sge-graphs`, `sge-noise` and `sge-jbump`'s upstreams are
   all ported by the engine — §3, §4, §5 and §6.)
2. **`sge-gltf`, `sge-vfx`** — mid-size, high coverage, few surprises. (`sge-anim8`'s and
3. **`ssg-liquid`** — small Java surface, but resolve the ANTLR decision first. Its 105 upstream test
   files are the best available proving ground for test porting after libGDX.
4. **`sge-ai`, `sge-visui`, `sge-textra`, `sge-colorful`** — large, each with a named redesign that must
   be scoped as its own decision.
5. **`ssg-md` (flexmark)** — the largest Java surface; 43 collapsed Maven modules is the hardest package
   rename on either list. Do it once manifest composition has been exercised on something smaller.
6. **`sge` core** — already ported by the engine. What remains is the 100 absent types and the backend
   question, which is a platform decision, not a port.
7. **Deferred / not port work**: `sge-controllers`, `sge-tools`, `sge-physics*`, `sge-freetype`,
   `sge-jvm-platform-*`.

---

## 2. libGDX — the spearhead port

`com.badlogic.gdx.* → sge.*`, Apache-2.0. Two ports: `libgdx-core` over `gdx/src`, and `libgdx-test`
over `gdx/test` as a **dependent** of it, inheriting its manifest.

### 2.1 Measured state

| gate | `libgdx-core` | `libgdx-test` |
|---|---|---|
| compile errors (scala-cli, Scala 3.8.4) | **0** | **0** |
| files emitted | **598** (11 dropped, 6 injected) | **29** |
| model | 605 units / 52,453 symbols | 634 units / 53,612 symbols |
| signature consistency | 0 | 0 |
| omissions | **177** | 3 |
| portability (all / emitted / injected) | 151 / 151 / 2 | 166 / 15 / 0 |
| remediation suggestions | 29 | 2 |
| substitutions (emitted / dangling) | 0 / 0 | 0 / 0 |
| manifest agreement · port map · policy | 0 · 0 · 0 | 0 · 0 · 0 |
| collection closure / boundary | **2** / 0 | 0 / 0 |
| trivia (comments lost) | **100** | **69** |
| porter notes uncovered | **0** | **0** |
| break residue (untranslated jumps) | **0** | **0** |
| source map | 594 units / 19,259 members | 623 units / 19,547 members |
| members changed vs baseline | **0** | **0** — the 28 + 6 that moved when this baseline was promoted are accounted below |
| decisions recorded | **2,163** | **279** (961 withheld as the base's) |
| **tests** | — | **221 emitted, 217 passing, 4 failing** |

All 4 failures are `expected#derived`, 0 declared: every one is a `sge.utils.JsonTest` case whose stack
reaches `com.badlogic.gdx.utils.Json` (emitted as `sge.utils.Json`), a type the manifest deliberately
drops in favour of a codec-backed replacement. Nothing is hand-listed — the classification follows the
manifest (`CLAUDE.md` §5.1).

The path OUT of those 4 is already measured (from the pre-consolidation status file, kept because
it is the analysis a fix starts from): JSON *decoding* raises `UnsupportedOperationException`
naming the swap point — chosen over returning null/empty, which would corrupt data silently.
**49 of 50 decode sites pass a `classOf[X]` literal**, so a call-site rewrite to
statically-derived codecs is viable; ONE site (`readValue("resource", null, …)`) is class-tag
driven and needs explicit handling.

`decisions.tsv` by kind, `libgdx-core`: 827 `RenamedMember`, 605 `RenamedPackage`, 335
`RetypedSignature`, 285 `FunnelledCtor`, 31 `DroppedSuperCall`, 22 `WidenedVisibility`, 21
`RedirectedCall`, 16 `DroppedMember`, 11 `DroppedType`, 10 `InjectedMember`.

#### The 34 members that moved when this baseline was promoted, and the SILENT DEFECT one of them was

`just members-unchanged` reported 28 on `libgdx-core` and 6 on `libgdx-test` for one commit, and
0 since. Every check count above was unchanged across it, both lanes still compiled to 0, and the
suite was still 217 passing / 4 expected-fail. The emitted text moved in five types on `libgdx-core`
and one on `libgdx-test`, all from engine fixes measured on a DIFFERENT library (anim8-gdx, §7.3) —
which is the whole reason a fourth library is worth adding:

- **`Cubemap` + `Cubemap$CubemapSide` — a real correctness fix, not churn** (`ENGINE-LIMITS.md` T10).
  The enum lowering dropped the CONSTRUCTOR's body, so `CubemapSide`'s
  `this.up = new Vector3(upX, upY, upZ)` and its `direction` twin never ran: **all six cubemap sides
  shipped with `up == null` and `getUp(out)` threw**. Zero compile errors, no moved check count, no
  test covering it, three libraries and every measurement to date gone past it.
- **`CharArray`, `JsonReader`, `JsonSkimmer`, `PropertiesUtils` and `JsonMatcherTests` — legibility,
  not behaviour** (`ENGINE-LIMITS.md` L1). Their char literals held raw `\b`, `\f` and NUL, which
  dotty happens to tolerate; they now render `'\b'`, `'\f'`, ``\u0000``. Same values, same behaviour.

### 2.2 Residues, named

**Omissions, 177.** The dominant kind is `promoted constructor body runs on every path` — a promoted
constructor's body executes on construction paths Java would not have run it on. Refusing that shape
instead of counting it was measured at **0 → 41 errors** and refused (`ENGINE-LIMITS.md` C7); the
targeted refusal for the shape-6 remainder (`Material`, `Table`) was measured at **0 → 35 `E120`**,
omissions 177 → 65, and also refused. The prefix strip that took omissions 193 → 177 repaired 16
construction paths across 10 classes. The rest is `super(args) dropped` (`ENGINE-LIMITS.md` C3) and 6
dropped annotations.

**The constructor residue is dominated by genuine WALLS, and the raw census over-stated what a wider
super-slot rule can reach.** `.balticporter/briefs/R1`'s pre-pipeline census put libGDX core at 144
escapes and 31 dropped `super(args)` with "the largest single reduction" available from generalising
the synthesised primary from "some root is nilary" to "all roots reach one parent constructor". The
lane says 140 and 31, and the generalisation moved the lane by **zero**: measured, libGDX core came
out byte-for-byte unchanged (0 members moved, every check identical). The 31 break down as
`DistanceFieldFont` 7 (seven roots to seven `BitmapFont` overloads — irreducible), `OrderedSet` /
`OrderedMap` / `IdentityMap` / three `RegionInfluencer` nests 3 each, `Button` 2, and three singles.
Every one of them reaches DIFFERENT parent constructors or is shadowed by one of its own; none is a
class the super-slot rule alone can express. What the same change IS worth is one of gdx-gltf's D4
errors (§8.4) and, more importantly, the soundness test in `ENGINE-LIMITS.md` C8 — without which the
widening emitted an infinitely self-delegating constructor.

**Trivia, 100 of 4,565 comments.** Classified in `ENGINE-LIMITS.md` §10: a comment on a construct the
*emission consumes* has nowhere to go. Carrying comments at all costs +33.8 % emitted bytes on libGDX
core, measured with the harvest off.

**Collection closure, 2.** Both are `java.util.concurrent` types. Portability decides those first —
they are not reachable on Scala.js or Native at all — so the closure finding is downstream of a
decision not yet made.

**Portability, 151.** Every one is inside a type the manifest already drops, which is why
`portability(emitted)` for the type-checked surface is what the number to watch actually is.

### 2.3 The scope question that is not the engine's

The port does **not** cover libGDX's backends, and the test suite that runs is JVM-only. The 221 ported
tests are MUnit and run under scala-cli on the JVM; sge's real targets are Scala.js and Scala Native,
where the suite has never been executed. "Port and run the tests" is therefore a JVM claim today, and
saying so is the point — a gate whose reach is unstated reads as broader than it is.

### 2.4 Do NOT retry — measurements, with the rules lifted

Every entry here that is a fact about Java, Scala 3, Spoon or dotty is restated in `ENGINE-LIMITS.md`
with its number, grouped by what an agent is doing when it hits the wall and classified (a)/(b)/(c).
**Read that file, not this table, before designing a fix**; this is the measurement record.

| tried | measured | entry |
|---|---|---|
| erasing DECLARATIONS (raw → `Object`-parameterised instead of wildcard) | **+277** | G1 |
| rendering an overriding method's return type from the PARENT's declaration | 162 → **438** | G5 |
| the raw fill's four knob settings (inherited fill × `?`/`Object` fallback) | 162 / 97 / 87 / **1** | G2, G4 |
| adopting sge's `[?]`-everywhere rendering | 1 → **11**, deliberately — the 1-error state was wrong identically on both sides of every override | G2 |
| restricting the ordinary name-directed fill to nested types | 1 → 14 (fill on) / 19 (coherent pairing) | G4 |
| a "mentions" guard on the inherited fill | 145 (ancestor only) / 1, no change (with own signatures) — the union is what re-admits the failing entry | G4 |
| applying the fill to an overriding member's SIGNATURE but not its BODY | 1 → **20** | G2 |
| `rawCtorArgs` erased-formal fallback — three successive gates | 2→23, 1→5, 1→**43** | G13 |
| broadening `erasedReceiverView` to a rendered wildcard / the callee's result type | 7 → **41** | G11 |
| `typeParamToObject` consulting the REFERENCE formal | 13 → **28** | G14 |
| disabling array covariance for a generic array formal | 13 → 28, and 10 → 26 | G14 |
| casting a type-parameter argument to `Object` when the formal is PRIMITIVE | **inert** | G16 |
| unconditional bound-erasing of a callee formal | **+47** | G13 |
| promoting a paramful constructor to the primary with no whole-program check | **+14** | C1 |
| inlining a promoted constructor's body without renaming what it declares | field collisions | C2 |
| tightening `supersedes` to inspect assignment right-hand sides | removes no effect, costs the argument | C6 |
| qualified `Outer.this` without a static/supertype guard | **+22** | T4 |
| binding an anonymous body's `this` to the anonymous instance in MEMBER-ACCESS position | 33 → **66** | T2 |
| treating an UNTYPED `this` inside an anonymous body as the anonymous instance | same **+33** | T2 |
| qualifying an enclosing ANONYMOUS class as `Outer.this` | names nothing in the emitted code | T3 |
| name-directed fill gated on `resolveTypeParam` instead of the barrier-aware frame | **+2** | G15 |
| qualifying an implicit access to an INHERITED instance field as `this.f` | **0 change — the code never runs** (Spoon produces no field write there) | T5 |
| `selfRawFormalArgs` / `rawToParameterized` from the scene2d branch | already present and strictly more general | G17 |
| `--js` compile as a portability gate | proves nothing — only the *linker* rejects, and only from an entry point | P1 |
| reasoning about the error count as if it were complete | it is TYPER-only until it reaches 0 | M1 |

---

## 3. Ashley — the dependent port

`com.badlogic.ashley.* → sge.ecs.*`, Apache-2.0. **The smallest library that is a genuine DEPENDENT
port**, and the reason it is in the corpus: every one of its 21 files resolves against libGDX core —
`Array`, `ObjectMap`, `Pool`, `Bits`, `SnapshotArray`, `ObjectSet` — so it exercises the thing a
single-module port cannot: agreeing with a base module's emitted surface while parsing only the base's
*Java*.

Reproduce with `just ashley-measure`. It compiles **libGDX core's emitted Scala and both
Ashley source sets on one scala-cli invocation** — Ashley is `RuntimeMode.Dependency`, so the
collection shims are vendored by libGDX core and compiling `ashley-core` alone measures nothing.

### 3.1 Scope, named rather than silently dropped

`ashley/src` (21 files) and `ashley/tests` (18 files, 112 `@Test`, 458 assertions). Excluded:

- **`benchmarks/`** (21 files) — an Artemis-vs-Ashley harness depending on a THIRD ECS library. Not
  library surface.
- **`tests/src`** (13 files) — demo applications driving a real libGDX window. They need a backend, and
  no backend is ported.

### 3.2 Measured state

| gate | `ashley` | `ashley-test` |
|---|---|---|
| compile errors (with libGDX core, Scala 3.8.4) | **0** | **0** |
| files emitted | **21** (0 dropped, 2 injected) | **18** |
| model | 626 units / 53,098 symbols | 644 units / 54,288 symbols |
| signature consistency | 0 | 0 |
| omissions | 1 | 2 |
| portability (all / emitted / injected) | 151 / **0** / 4 | 153 / 2 / 0 |
| substitutions · manifest · port map · policy | 0 · 0 · 0 · 0 | 0 · 0 · 0 · 0 |
| trivia | **0** | **0** |
| porter notes uncovered | 0 | 0 |
| break residue | **0** | **0** |
| source map | — | 633 units / 19,977 members |
| members changed vs baseline | 0 | **0** |
| decisions recorded | **168** | **213** |
| **tests** | — | **112 emitted; 108 pass, 2 fail, 2 skipped** |

`portability(emitted) = 0` and `trivia = 0` are both real zeros: Ashley emits no JVM-only API of its
own, and every comment in its 21 files survives into the output.

### 3.3 The manifest-inheritance story — what a dependent must and must not restate

Ashley's manifest is `LibgdxPolicy.core(...).extendedBy(...)`. The base's `dropTypes`, `dropMethods`,
`packageRenames` and signature-affecting phases are **inherited, not restated**; `inject` is **not**
inherited, because exactly one module ships each replacement file and libGDX core ships the ones for
the types it dropped (`CLAUDE.md` §1.5).

Two entries carry the whole rename: sge puts Ashley at `sge.ecs`, **flattening the `core` package
away**, which under longest-prefix-wins is `…ashley.core → sge.ecs` plus `…ashley → sge.ecs`. libGDX's
own `com.badlogic.gdx → sge` is inherited, not repeated.

**Ashley lands on the base's substitutions immediately**, which is the interesting part. It imports
three types libGDX core deliberately does not translate — `utils.reflect.ClassReflection`,
`ReflectionException` and `utils.ReflectionPool` — all of them the one thing Scala.js and Scala Native
cannot do. The reference hand port SOLVED rather than skipped this (a factory registry instead of
reflective pooling), which is the same shape the base's own substitution already takes. Three seams,
in a deliberate order:

| seam | why this one |
|---|---|
| `TypeRedirectTransform(ReflectionPool → ComponentPool)` | `ReflectionPool` is used as a TYPE — a field's type, a local's type, a `new`, several cast targets — so no body seam can reach it, and a dependent may not inject at the base's FQN |
| `MethodBodyTransform(Engine#createComponent(Class) → …)` | the one reflective site in 21 files. Dropping the TYPE to fix one method would fork 200 lines of mechanical entity/system bookkeeping from upstream permanently. This replaces the BODY only; the signature, and every call site, is untouched |
| `PortMapTransform.forBases("libgdx-core")` — **LAST** | it reads what the base actually emitted and reports a reference the base does not ship, so it must run after the seams that re-point those references. Run first it reported 7 findings, every one repaired by the two phases above; run last it reports what an agent must act on |

The body substitution is where the two-namespace rule bites: the KEY names the member in the
**upstream** namespace (the phase matches it before the rename runs), and the BODY is spliced verbatim
into emitted code the rename never sees, so it must already be written in the port's **final**
namespace. Getting that backwards is one compile error naming `com.badlogic` in a file that declares
`package sge.ecs`.

One inherited drop leaves a dangling call the base cannot see: `ImmutableArray#toArray(Class)` is a
one-line forwarder to `Array.toArray(Class)`, which the base drops. **Found by the orphaned-call check
on the first run, not by reading** — an inherited drop leaves a dangling call in the dependent, and the
dependent is the only module that can see it. It is now Ashley's own `dropMethods` entry; the nilary
twin beside it is untouched and is what the corpus calls.

### 3.4 The two skipped tests — a JDK-incompatible mocking dependency, and the measure-lane defect it exposed

`EntityListenerTests.entityListenerPriority` calls `Mockito.mock`. Ashley's `build.gradle` pins
**mockito-all 1.10.19** — read from upstream rather than guessed, because guessing it cost a full cycle,
and `ComponentClassFactory` uses `org.mockito.asm`, removed in 2.0. On a module JDK, mockito 1.x's cglib
cannot `setAccessible` `ClassLoader.defineClass`, so `Enhancer.<clinit>` throws
`ExceptionInInitializerError`.

An `Error` is not `NonFatal`: MUnit marks the suite **aborted** and fires an assumption failure for
every test after it. So two tests — `familyListenerPriority` and `componentHandlingInListeners` —
never ran. **The port did not break them.**

What made this worth a session is that they were invisible. MUnit prints **three** terminal markers,
not two: `  + ` (pass), `==> X ` (fail) and `==> s <suite>.<name> skipped 0.0s`. Every measure lane
counted the first two, so 108 + 2 = 110 against 112 emitted, with nothing comparing the two numbers —
`CLAUDE.md` §3's *"a test that stopped running is reported as such, never as a pass"* failing inside the
measurement itself. `reconcile_outcomes` now reconciles against the **emitted** count rather than a sum
of known markers, so a test with no recognised line is reported whatever the reason and the next marker
MUnit adds cannot repeat this. The correlator gained a `skipped` status kept apart from `ignored` — an
ignored test is a DECISION, a skipped one is PREVENTION — and `TestDiff.newlySkipped` gates on it.

The rule is lifted to `CLAUDE.md` §5.1 ("parse every TERMINAL MARKER the runner emits, and gate on
each"), because it is a fact about running any ported suite, not about Ashley.

### 3.5 Remaining

- **`EngineTests.createPrivateComponent` fails** — a genuine comparison failure, anchored `assert-site`
  at `EngineTests.java:970`, not yet diagnosed.
- **The two skipped tests** stay skipped until the mocking dependency is replaced or the suite is run on
  a JDK where mockito 1.x's cglib works. They are baselined as skipped, which is the deliberate state.
- **1 + 2 omissions**, baselined and stable.

---

## 4. simple-graphs

`space.earlygrey.simplegraphs → sge.graphs`. **The third corpus library, and the first that is neither
libGDX nor a dependent of it** — which is what makes its result meaningful. Reproduce with
`just sg-measure`.

### 4.1 Measured state

| gate | `simple-graphs` | `simple-graphs-test` |
|---|---|---|
| compile errors (scala-cli, Scala 3.8.4) | **0** — and past `RefChecks`, see §4.3 | **0** |
| files emitted | **33** (29 upstream units; 0 dropped, 0 injected) | **7** |
| model | 29 units / 1,172 symbols | 36 units / 1,414 symbols |
| signature consistency | 0 | 0 |
| omissions | 2 | 0 |
| portability (all / emitted) | 6 / 6 | 6 / 0 |
| substitutions · manifest · port map · policy | 0 · 0 · 0 · 0 | 0 · 0 · 0 · 0 |
| trivia | 1 | 1 |
| porter notes uncovered · break residue | 0 · 0 | 0 · 0 |
| source map | — | 36 units / 508 members |
| members changed vs baseline | 0 | **0** |
| decisions recorded | **125** | **26** |
| **tests** | — | **16 of 16 PASSING** (7 files) |

**No §1(c) rules.** simple-graphs needed **zero** library-specific rules: its manifest is a namespace
claim, two universal phases and a package rename. That is the outcome the corpus procedure aims for.

**And therefore the CONFIG front door's acceptance proof.** Because its whole policy is data, this
port is driven from `corpus/ports/simplegraphs/{main,test}.conf` (DESIGN.md §5.7) rather than from a
hand-written `PortRun(...)`; `SimpleGraphsMigrate` and `SimpleGraphsTestMigrate` are now a `main`
each. The lane above IS the equivalence proof — it measures the conf-driven path, and the conversion
landed with every check count in the table unchanged and **0 members changed on both source sets**.
libGDX and Ashley stay on the Scala path for now; §5.7 names the three things their conversion still
needs, none of them mechanism.

### 4.2 What this library taught the engine

Every item is an engine §1(a) fix, not per-library policy — which is what a third library is for. Each
is recorded in `ENGINE-LIMITS.md` under the key given.

| key | the gap | cost when wrong |
|---|---|---|
| **K5** | `java.util.Collection` and `AbstractCollection` mapped to different families, breaking a subtype relation Java guarantees | 13 of 20 errors |
| **K5** | the shim's abstract/concrete split, parameter types and type-parameter bounds must be `AbstractCollection`'s OWN, member for member | 1 → 8 at `RefChecks` |
| **K6** | a `java.util.stream` chain COLLAPSES; and a stream operation may only be rewritten when its receiver was already collapsed | 3 errors; 0 → 1 on libGDX when the second rule was missing |
| **K7** | a Java enhanced-for binding may be declared at a SUPERTYPE, and the port dropped the declaration | 2 errors |
| **K8** | `Type::method` is a qualified name only when the method is STATIC | 2 errors |
| — | `java.util.Collections` / `Map.Entry` statics have no receiver, so no receiver-keyed rewrite sees them | 4 errors |
| — | Java's collection COPY CONSTRUCTOR (`new ArrayList<>(c)`) is not Scala's capacity constructor | 1 error |
| — | a mapping must PRESERVE the source library's subtype relations — `ArrayDeque <: Queue` in Java, `Queue <: ArrayDeque` in Scala | 2 errors |

**The transferable rule of this port:** two of the three multi-error causes were a mapping that broke a
subtype relation the source depends on. Check that property of any new type mapping before checking
anything else.

New engine machinery, all §1(a): `balticporter.runtime.JavaCollection` (the third shim, now the
family's most detailed member) and `JavaCollections` (the fourth runtime type, mirroring
`java.util.Collections`' statics plus the `Map.Entry` statics that follow from `Map.Entry → Tuple2`);
`CollectionsTransform.coerce` (one seam for every shim-typed slot — argument, declaration, assignment —
replacing an argument-only wrapper), `.staticRewrite` (receiver-less JDK utilities, keyed `owner#name`)
and `.copyConstructor`; `TirEmitter.widenedBinding`; and a conditional's unchecked conversion applied
to its BRANCHES in the frontend.

### 4.3 What the SUITE found that no count did

`0` is a real 0 twice over: the compile count reached 0, `RefChecks` ran, the count **rose to 8**, those
8 were fixed — and then the SUITE ran and found two more defects that no count had moved. Read this
before treating any port's first 0 as a finished port.

- **`AlgorithmPath` constructed its parent with another constructor's arguments.** Two Java constructors
  reach the same parent constructor with DIFFERENT arguments; Scala allows only the primary to reach
  `super`, so the engine nominated one and silently dropped the other's. `findShortestPath` returned a
  path of size 0 instead of 39. Fixed by a **synthesised primary** whose parameters ARE the parent's,
  with every Java constructor a secondary computing its own arguments — which also EXPRESSED three super
  calls libGDX had been dropping.
- **An `asInstanceOf` that could never succeed**, compiling perfectly and throwing at run time. The
  **correction is the more transferable half**: the first form of the rule asked whether the cast's
  SOURCE type was named `java.*`, and `java.lang.Object` is. Deleting those casts turned a CORRECT
  program into a wrong one at three sites in libGDX's `Json` — and *nothing measured it*, because those
  members are inside a type libGDX's manifest drops. The test is now structural. The generalised rule is
  `CLAUDE.md` §4.56.

The engine **REPORTED** the first one: all five dropped `super(args)` were in `findings.tsv`, including
`AlgorithmPath.java:12`. It survived because nobody opened the report. **A finding nobody reads is a
finding nobody made.**

### 4.4 Do NOT retry

| tried | measured | why |
|---|---|---|
| `java.util.Collection` → `mutable.Buffer` while `AbstractCollection` → the shim | 13 of 20 errors | Java's abstract base IMPLEMENTS the interface; the two must share a family |
| mapping `Collection` to the shim while bridging ARGUMENTS only | libGDX test port 0 → 3 | a declared slot is an expected type exactly as a formal is |
| guarding the Scala-shaped rewrite table per-rewrite instead of blanket-refusing on a shim receiver | 0 → 2 | it had already failed twice; `add`→`+=` and `addAll`→`++=` survived the guard |
| appending `$e` to the ESCAPED for-each name | libGDX main 0 → 3 (E040) | `` `object`$e `` is not an identifier; escape the WHOLE name |
| rewriting `Stream#filter` on method name alone | libGDX test 0 → 1 | `"…".lines()` is a Stream with no collection behind it |
| leaving the collapsed stream node typed `Stream<E>` | `Found: Buffer[V] / Required: JavaCollection[V]` | a rewritten node must be typed as what it EMITS |
| `Collectors.toSet` / `toMap` / `unmodifiableList` mapped to something approximate | not attempted, deliberately | each needs a different target type, and both a copy and the identity compile while being wrong |
| `IO.copyFile` → `IO.write` in the vendoring generator | reverted | the staleness was sbt's classloader-layer cache, not the mtime (`ENGINE-LIMITS.md` M5.5) |

### 4.5 Licence — a discrepancy worth keeping

Upstream ships **MIT** ("MIT License, Copyright (c) 2020 earlygrey"). The reference hand port's file
headers say "Licensed under the ISC License". One of the two is wrong; since a port is a derived work
the upstream file is the authority, so this port states **MIT**. Recorded rather than silently
followed, because the reference port is otherwise this project's tie-breaker (`CLAUDE.md` §3.5).

### 4.6 Remaining

- **2 omissions and 6 portability sites** — baselined, stable and read: the omissions are
  `DirectedGraph`/`UndirectedGraph`, whose several roots reach different parent overloads (the shape
  `ENGINE-LIMITS.md` records as having no single-primary encoding). Neither is reachable from a passing
  test.
- **`Arrays.asList`** (element form, the only form this suite uses) returns a mutable `Buffer` where
  Java returns a FIXED-SIZE list. Permissive, so it cannot make a correct program incorrect. The form
  that is *not* permissive is recorded beside it: Java's `asList(arr)` over a caller-held array is a
  live view, a copy would silently detach it, and today that form fails to compile rather than
  silently copying.
- **Non-local returns**: 14 sites emit `return` inside a `for`, which Scala desugars to a closure. They
  still work in 3.8 (verified) but are deprecated — a forward-compatibility item, not a defect.

---

## 5. noise4j

`com.github.czyzby.noise4j.{map,array} → sge.noise`, Apache-2.0. **The fourth corpus library, the
second standalone base port, and the first with no upstream test suite at all.** Reproduce with
`just noise4j-measure`.

Why it was worth adding: 2,491 lines that concentrate constructs the first three do not have. Three
independent **Java enum constant bodies** (`Generator.GenerationMode`, `RoomType.DefaultRoomType`,
`DungeonGenerator.Direction`) — an abstract enum method overridden per constant, which Scala 3's
`enum` cannot express at all; an interface CONSTANT read unqualified from an implementor
(`Grid.CellConsumer.BREAK`/`CONTINUE`, two declarators in one Java field declaration); a `continue`
inside a doubly-nested `for`; and `java.util` mutation through `Iterator.remove()`.

### 5.1 Measured state

| gate | `noise4j` |
|---|---|
| compile errors (scala-cli, Scala 3.8.4) | **2** — both one cause, §5.4 |
| files emitted | **12** (12 upstream units; 0 dropped, 0 injected) |
| model | 12 units / 1,032 symbols |
| signature consistency | 0 |
| omissions | **3** — all `Object2dArray`, §5.5 |
| portability (all / emitted / injected) | 0 / 0 / 0 |
| substitutions · manifest · port map · policy | 0 · 0 · 0 · 0 |
| remediation suggestions | 0 |
| trivia (comments lost) | **0** — every comment in all 12 files reached the Scala |
| porter notes uncovered · break residue | 0 · 0 |
| source map | 12 units / 311 members |
| members changed vs baseline | 0 |
| decisions recorded | **37** (RenamedMember 20, RenamedPackage 12, FunnelledCtor 4, RetypedSignature 1) |
| **tests** | **NONE EXIST UPSTREAM** — §5.2 |

**No §1(c) rules, and one §1(b) phase.** The manifest is a namespace claim, a two-entry package
rename and `mutable-params`. Everything else this library needed was §1(a).

### 5.2 There is no behavioural gate, and that is a fact about the library

noise4j ships **no test sources**: `find` over the upstream tree returns `src/` and `examples/`
(nine PNGs) and nothing else. The 13 MUnit cases in the reference hand port (`../sge/sge-extension/
noise`, 3 files) are hand-written Scala with no Java counterpart, so there is nothing for this
pipeline to convert and there is no `test.conf` beside `main.conf`.

State the consequence rather than the absence: **every `CLAUDE.md` §4.4 form is UNMEASURED for this
port.** The four silent correctness defects found in libGDX core all compiled cleanly, and this port
has only a compile. `just noise4j-measure` therefore ASSERTS `@Test in Java: 0` instead of omitting
the discovery block — a lane that silently has no tests is indistinguishable from a lane whose tests
all vanished, and this one also has to notice the day upstream gains one.

What was read by hand instead, since a count could not be: the emitted form of every §4.4 shape this
library has. All were **correct** — `x++` as a value (`{ val $prev = index; index += 1; $prev }`),
pre-decrement as a value, `this == object` inside `equals` (`this eq object.asInstanceOf[AnyRef]`),
the chained `a = b = -1`, `continue` as a `boundary` around the loop BODY with the update outside it,
`static final` constants as `inline val`, and the interface constants reached through an `export` of
`Grid.CellConsumer`. That is reading, not measurement, and it is recorded as such.

### 5.3 What this library taught the engine

| gap | kind | cost when wrong |
|---|---|---|
| an enum constant's class body was harvested for METHODS only — its fields were dropped silently | **(a)**, fixed | **4 of 6 errors**; `ENGINE-LIMITS.md` T8 predicted it and this port is the first to hit it |
| a Java enhanced-for over a JDK `Iterable` emits `for (x <- xs)`, which needs Scala's `foreach` | **(a)**, open | **2 errors**, §5.4 |
| assignment used as a VALUE re-evaluates its left-hand side | **(a)**, open | 0 errors here — 7 sites, all with a pure index. §5.6 |

The enum fix is `SpoonTir.enumCase`, spec-pinned by `EnumConstantBodySpec` in `testkit` (two positive
tests, two negative). It moved **0 members** in every other port, so no baseline elsewhere changed.

### 5.4 The 2 errors, and why the port does NOT run `CollectionsTransform`

Both errors are one cause: `for (final Room r : rooms)` over a `java.util.List` and
`for (final Integer region : regions)` over a `java.util.Set` emit as `for (r <- rooms)`, and a JDK
collection has no `foreach`. The correlator classifies both as **EngineGap — (a)**; the rule is
`ENGINE-LIMITS.md` K9.

The port keeps `java.util` deliberately, and the alternative is measured rather than assumed:

| configuration | compile errors | what it costs |
|---|---|---|
| **no `collections` phase** (shipped) | **2**, both loud | nothing — `java.util` is what the reference hand port emits |
| `+ { transform = "collections" }` | 1 | `DungeonGenerator.removeDeadEnds` **throws at run time** |

With `collections` on, `Iterator.remove()` over the `LinkedList` of dead ends becomes
`balticporter.runtime.JavaIterator.from(deadEnds.iterator).remove()`, whose `remove()` is
`throw new UnsupportedOperationException` — correctly, because a Scala iterator cannot remove. That
is the library's headline API (`DungeonGenerator.generate` calls it unconditionally) broken by the
port's own policy, and with no test suite nothing here would ever catch it. The remaining error is
its sibling: `Generators.shuffle` uses `List.set` for its RETURN value (the previous element) and
`Buffer.update` returns `Unit`. Two compile errors that name their line are strictly better than one
compile error plus a `throw`.

`CLAUDE.md` §3.5 agrees independently: the reference hand port imports
`java.util.{ArrayList, HashMap, HashSet, LinkedList, Iterator, Set}` and renames only to dodge
Scala's own `Iterator`/`Set`. Where the reference solved it, that is the answer.

### 5.5 The 3 omissions

All `Object2dArray`, all one shape: the constructor funnel promoted `Object2dArray(int, int)` to the
primary, so its body — `this.array = getArray(width * height)` — runs on the three paths Java did not
run it on. Each of those then overwrites `array`, so the observable cost is a wasted allocation plus a
virtual call to an ABSTRACT method during construction, which Java made on one path only.
`ENGINE-LIMITS.md` C7 is the entry: refusing the promotion costs `0 -> 41` on libGDX, so the omission
is reported rather than avoided. Reported, counted, baselined — not silent.

### 5.6 Do NOT retry

| tried | measured | why |
|---|---|---|
| `{ transform = "collections" }` in the manifest | 2 → 1 error, and `Iterator.remove()` becomes a `throw` | §5.4 — the JDK forms this library uses (`Iterator.remove`, `List.set` for its return) have no Scala-collection counterpart |
| switching the emitter's `ForEach` to the iterator protocol universally | not attempted, deliberately | `it.hasNext`/`it.next()` is the JAVA arity; a Scala `Buffer`'s `iterator` is parameterless, so it would break every port that DOES run `collections`, and move every foreach loop's digest in libGDX. `ENGINE-LIMITS.md` K9 says what a real fix has to answer first |

### 5.7 Remaining

- **The 2 `for`-over-JDK-`Iterable` errors** (K9). The fix has to decide, structurally rather than
  from a type's NAME (`CLAUDE.md` §4.56), which iterables support Scala's `foreach`. A `(b)`
  parameterised phase with an empty default — so every existing lane is a no-op — is the shape that
  costs no other port anything; that is the design question, not the lowering.
- **Assignment-as-value re-evaluates its LHS.** `return grid[toIndex(x,y)] = value` emits
  `{ grid(toIndex(x,y)) = value; grid(toIndex(x,y)) }`, and the compound form evaluates the index
  THREE times. 7 sites in `Grid`, every one with a pure index, so this port is unaffected — but
  `a[i++] = v` used as a value would double-increment, silently, with a green compile. The simple
  form has an exact fix (`{ val $v = rhs; lhs = $v; $v }`, which is also what Java yields); the
  compound form needs the LHS decomposed. Recorded in `ENGINE-LIMITS.md` §9.
- **A Java enum with NO members at all** (`public enum Bare { A, B; }`) crashes the frontend with an
  NPE in `superTypes` → `originOf` on a position with no source buffer. Found while writing
  `EnumConstantBodySpec`; one member is enough to avoid it. Not on any port's path, so it is noted
  here and not fixed.

---

## 6. jbump

`com.dongbat.jbump → sge.jbump`. **The fourth corpus library, the second conf-only port, and the
first library in the corpus that ships NO TEST SUITE.** Reproduce with `just jbump-measure`.

A 19-file, 4,045-line 2D AABB collision library (a Java port of kikito's `bump.lua`), dependency-free,
Apache-2.0. The whole port is `corpus/ports/jbump/main.conf`; `JbumpMigrate` is a three-line `main`.

### 6.1 Measured state

| gate | `jbump` |
|---|---|
| compile errors (scala-cli, Scala 3.8.4) | **0** — and past `RefChecks`; the count did NOT rise at zero |
| files emitted | **23** (19 upstream units + 4 vendored runtime; 0 dropped, 0 injected) |
| model | 19 units / 1,872 symbols |
| signature consistency | 0 |
| omissions | **15** — all C7 promoted-ctor replay, all in the three primitive arrays; see §6.4 |
| portability (all / emitted / injected) | 0 / 0 / 0 |
| substitutions · manifest · port map · policy | 0 · 0 · 0 · 0 |
| trivia | **0** — every comment in all 19 files reached the emitted Scala |
| porter notes uncovered · break residue | 0 · **0** |
| source map | 19 units / 521 members; port map 23 types / 494 members |
| decisions recorded | **97** (RetypedSignature 48, RenamedPackage 19, RenamedMember 16, FunnelledCtor 10, InjectedMember 4), 23 porter notes |
| **behaviour** | **44 transcript lines, byte-identical to the upstream Java** — §6.2 |

**No §1(c) rules, and no drops.** jbump's manifest is a namespace claim, two universal phases
(`collections`, `mutable-params`) and a package rename. Nothing was substituted, nothing was
injected, and no library-specific rule was written — the second library after simple-graphs to land
that way, and the first to do it at 100 % type coverage.

### 6.2 There is no suite, so the gate is a DIFFERENTIAL PROBE

**jbump ships zero `@Test` methods.** Its `test` gradle module is a runnable libGDX demo
(`TestBump extends ApplicationAdapter`, LWJGL3 + shapedrawer, driven by mouse and WASD) whose
`build.gradle` declares a `mainClassName` and a `run` task. Running it would mean porting libGDX's
backends and a third-party drawing library to open a window. sge's 32 jbump test cases are no help
either: they were WRITTEN in Scala against a redesigned API, not translated, so there is nothing for
this engine to port. The lane re-derives the zero with `java_test_count` over the whole upstream
checkout on every run rather than taking this paragraph on trust.

CLAUDE.md §3 then leaves the port with no evidence at all, and jbump contains **six of §4.4's ten
forms** — reference `==` in four `equals` bodies, `x++`/`x--`/`++x` read as a value (19 sites, 9 of them
`IntIntMap`'s `if (size++ >= threshold) resize(...)`), `break` and `continue`, a
`switch`, a `static { }` block, and secondary constructors funnelled into a promoted primary. Not one
of them moves a compile-error count.

So the port's gate is `corpus/ports/jbump/probe/{Probe.scala,ProbeJava.java}`: **the same scenario
written twice, once against the emitted Scala and once against the upstream Java, with the two
transcripts diffed line for line.** It is hand-written and is **not a ported test** — it is a
measurement harness, in `scripts/_lib.sh`'s category, and it exists because a behavioural claim no
lane reproduces is not a measurement (CLAUDE.md §5). Nothing in it asserts an expected value: the
Java is the authority, so no expectation can be written down wrong, and widening it costs one
`println` on each side.

What the 44 lines cover, chosen as the union of §4.4's forms present and the port's own decisions:

| probe section | what would break it |
|---|---|
| `BooleanArray` / `IntArray` / `FloatArray` — nilary construction, 40 adds through two grows, `removeIndex`, `swap` | the C7 promoted-constructor replay (§6.4), `items[size++] = value` |
| `IntIntMap` — 200 puts through repeated resizes, `get`/`remove`/`containsKey`/`containsValue`, a full `Entries` iteration, `toString`, the copy constructor, `equals`/`hashCode` | `size++` as a value, the `break` in `findNextIndex`, the `continue` and the enclosing-boundary-named `break` in `toString`, the `switch` in `push`, and the `Iterable`+`Iterator` shim pair |
| `MathUtils` — `sin`/`cos` off the lookup table, `nextPowerOfTwo`/`clamp`/`floor`/`ceil`/`round`, a SEEDED `random(10)` twice | the `static { }` block that fills the sine table, and the `random` field/method §4.55 rename |
| `Point` / `IntPoint` / `Item` — `equals`, `hashCode`, `toString` | reference `==` rendered as `==` instead of `eq` (`Point` is `World`'s `HashMap` KEY, so this decides whether the grid works at all) |
| `Grid.grid_traverse` through an anonymous `TraverseCallback` | a dropped anonymous-class body (ENGINE-LIMITS T1's 156-site defect) |
| `Collisions` — the copy constructor and its independence, `sort()`, `compare`, `remove` | `Comparator<Integer>` unboxing, `Collections.swap`, `IntIntMap` iteration, the `size` field/method rename |
| `World` — `add`/`move`/`update`/`remove`/`reset`, `queryRect`/`queryPoint`/`querySegment`/`querySegmentWithCoords`/`queryRay`, `getCellsTouchedBySegment` | the whole library |
| all four `Response` constants through a caller-supplied `CollisionFilter` | interface constants that are anonymous classes, and the §4.55 captured-local rename in `World.check` |
| `Rect` / `RectHelper` statics | float arithmetic and the instance-qualified static call |

The gate is negative-tested: perturbing one `println` by `+ 1` makes the lane print
`!! PROBE DIVERGED`, name the line, and exit 1.

**What it still does not cover, stated plainly.** Ray queries with a non-axis-aligned direction;
`tileMode = false`; `Response.bounce` off a corner; `IntIntMap`'s stash path under adversarial keys;
`World.check` with a filter that returns `null` for some pairs. Each is one more `println` on each
side when somebody wants it.

### 6.3 What this library taught the engine

Two §1(a) fixes, each in its own commit, each spec-pinned, each measured at 0 members changed across
all four pre-existing lanes.

| the gap | where | cost when wrong |
|---|---|---|
| `java.util.Collections.swap` had no entry in the receiver-less static table, so the call survived verbatim against the JDK class while its argument had been retyped | `CollectionsTransform.staticRewrite` + `JavaCollections.swap` | 1 of 2 errors |
| a method's LOCAL or PARAMETER that a NESTED class's member shadows is unnameable in Scala — Java's two namespaces let `filter` the parameter and `filter(a, b)` the member coexist, Scala's one namespace gives the member both | `TirEmitter.resolveCapturedLocalClashes`, the fourth §4.55 pass | 1 of 2 errors |

The second is the transferable one, and the rule is lifted to `CLAUDE.md` §4.55: the three existing
renaming passes move a MEMBER, and this one moves the CAPTURE. It is also the one pass in that family
that must not over-approximate — a local rename is invisible, but a parameter rename moves emitted
surface, so it fires only where the capture is really shadowed. The nested class is normally
ANONYMOUS, i.e. inside a TERM, which is why it walks with `StandardTraversal`: a walk over class
bodies finds none of them.

**The (c)-vs-(b) data point the corpus wanted.** jbump's `IntIntMap` is *libGDX's own* `IntIntMap`,
vendored — same file, different repository — so it is the control experiment for two libGDX-shaped
questions, and both came back the same way:

- **The `Iterable`+`Iterator` double interface needed no rule.** `Entries extends MapIterator with
  JavaIterable[Entry] with JavaIterator[Entry]` is emitted straight onto the universal runtime shims,
  and `MapIterator`'s `hasNext` FIELD beside `Entries`' `hasNext()` METHOD is the ordinary §4.55
  field-vs-method rename. CLAUDE.md §4.5 is doing its job unparameterised.
- **`GdxSharedIteratorRule` stays a genuine §1(c).** jbump inherited libGDX's cached-iterator design
  along with the file — `entries()` returns one of two `Entries` instances, reset in place — so the
  nesting hazard is *present* in jbump. It is never *triggered*: the four `keySort` overloads iterate
  `swapMap` sequentially and nothing nests. So the rule was not needed here, and the reason is a fact
  about jbump's call sites rather than about the mechanism. A future consumer that nests iteration
  over one `IntIntMap` needs that rule, in its own repository.

### 6.4 The 15 omissions are C7 replay COST, not divergence

All 15 are `promoted constructor body runs on every path`, five each in `BooleanArray`, `FloatArray`
and `IntArray`. Each of those classes has six constructors and the funnel promoted the nilary one,
whose body is `this(true, 16)` inlined to two statements (`ordered = true; items = new T[16]`). Scala
runs a class body on every construction path, so `new IntArray(false, 64)` now allocates a 16-element
array and immediately discards it.

**Every other constructor overwrites both statements**, so the final state is identical on every
path — this is exactly ENGINE-LIMITS C5's declared replay cost in its promotion form, and the probe
demonstrates it rather than assuming it: `BooleanArray().items=16`, `FloatArray(false, 3)` grows to
14 and `IntArray()` to 49, all byte-identical to Java. Baselined, stable, not a defect.

### 6.5 Licence — the discrepancy runs the other way from simple-graphs'

Upstream ships **Apache-2.0**: the repository `LICENSE` is the full 201-line Apache text and every
one of the 19 files carries "Licensed under the Apache License, Version 2.0". The reference hand port
in `../sge/sge-extension/jbump` states "Licensed under the MIT License" in every file header. One of
the two is wrong; a port is a derived work, so the upstream file is the authority and this port states
**Apache-2.0**. Recorded rather than silently followed, for the same reason §4.5 records
simple-graphs' — the reference port is otherwise this project's tie-breaker (CLAUDE.md §3.5), and it
has now been wrong about the licence twice.

### 6.6 Where this port is BETTER than the reference, and where the reference is stale

`sge-jbump` covers 14 of 19 upstream types; this port covers 19 of 19 with nothing dropped and
nothing injected. What the hand port does not have: `Extra`, `util/IntIntMap`, `util/IntArray`,
`util/FloatArray`, `util/BooleanArray`, and — the one that reads as present and is not — upstream's
352-line `util/MathUtils`, whose name the hand port reuses for `Extra`'s three members. Within
`Collisions` it also drops `Comparator<Integer>`, `compare`, `order` and three of the four `keySort`
overloads in favour of a `boxed`/`applyPermutation` redesign. All of that ports mechanically here.

`PROGRESS.md` §1.1 used to say the hand port had **lost the copy constructor `Collisions(Collisions)`**,
pinned by a deliberately-red test. That claim is now **stale**: sge fixed it by hand, and
`../sge/sge-extension/jbump/src/main/scala/sge/jbump/Collisions.scala:51` has it (verified
2026-07-31). The engine's port has it too, mechanically, as `def this(other: Collisions)` — and the
probe pins both halves of the contract the red test specified, that the copy carries all 19 arrays
plus `size` and that clearing the copy leaves the source untouched. The §1.1 row is corrected rather
than repeated.

### 6.7 Do NOT retry

| tried | measured | why |
|---|---|---|
| reading `class BooleanArray {` and concluding the promoted nilary body was dropped | **wrong** — the body is emitted AFTER the secondary constructors, and `BooleanArray().items` is 16 | A Scala class body is not required to precede the constructors it runs before. Read the whole file, or better, RUN it (§6.2); a diagnostic over emitted code is not something to eyeball (CLAUDE.md §5.1) |
| a spec for the captured-local rename over a method-LOCAL named class | frontend refuses it: `unsupported construct: statement CtClassImpl` | recorded as ENGINE-LIMITS T9, zero sites in the whole corpus; the pass itself is indifferent between a local and an anonymous class |

### 6.8 Remaining

- **15 omissions**, all C5/C7 replay cost, all demonstrated harmless by the probe. Nothing to do
  unless the funnel gains a shape-6 answer, which ENGINE-LIMITS C7 measures as 0 → 35 errors.
- **The probe covers 44 lines and could cover more.** §6.2 names the five gaps. This is the only
  thing on this list that would find a new defect.
- **`World`'s raw `Item` parameters** emit as `Item[?]` throughout (ENGINE-LIMITS G2), which is what
  the reference port does too — but jbump's raw uses are pervasive enough that a consumer holding an
  `Item[Entity]` will need casts the Java did not. Not a defect, a surface question for whoever
  adopts this.

---

## 7. anim8-gdx — the port whose difficulty is per LINE

`com.github.tommyettinger.anim8 → sge.anim8`, Apache-2.0. **The fourth corpus library and the second
genuine dependent port.** Reproduce with `just anim8-measure`, which compiles libGDX core's emitted
Scala, anim8's emitted Scala and anim8's hand-written suite on **one** `scala-cli` invocation —
anim8 is `RuntimeMode.Dependency`, so the collection shims are vendored by libGDX core and compiling
`anim8-core` alone measures nothing.

**Why it is in the corpus.** Every library before it was many small files. anim8 is 16 files holding
19,594 lines — `PNG8` alone is 8,351 and `PaletteReducer` 5,989 — and the two shapes that dominate
them are found nowhere in libGDX, Ashley or simple-graphs:

- **enormous constant data.** `ConstantData` is 108 lines of Java holding four ISO-8859-1 string
  literals of 47,935 and three × 6,390 SOURCE characters, full of control characters and high bytes,
  decoded in a `static { }` block with `getBytes(ISO_8859_1)`.
- **arithmetic in bulk.** `OtherMath`'s `barronSpline` / `probit` / `cbrt` / `atan2` are bit-pattern
  approximations over `NumberUtils.floatToIntBits`, hex float literals (`0x1p-8`) and shifts — the
  `CLAUDE.md` §4.4 defect class, per line, for 371 lines.

### 7.1 Scope, named rather than silently dropped

`src/main/java` (16 files) only.

**`src/test/java` (20 files) is excluded because it contains ZERO `@Test` annotations.** Every file in
it is an `ApplicationAdapter` demo or a startup bench driven by `gdx-backend-lwjgl3` —
`StillImageDemo`, `VideoConvertDemo`, `InteractiveReducer`, `ShaderCaptureDemo`, the three
`bench/*StartupBench` — and no libGDX backend is ported. There is no upstream suite to migrate, so
there is no `Anim8TestMigrate`. (This corrects §1.1's third fact, which listed "anim8-gdx 20" among
the available-and-unported test suites: 20 FILES, 0 tests.)

That leaves the port with no behavioural gate at all, which `CLAUDE.md` §3 says is not a gate. So
`anim8-core/src/test/scala` holds **23 hand-written MUnit tests** — the only thing in that module a
human wrote (`src_managed/` is the build product, CLAUDE.md §5.5) — adapted from the reference hand port's own
four suites and extended where a property was checkable. `just anim8-measure`'s discovery block
prints both numbers and says out loud that the java side is legitimately zero, because `0 == 0`
reading as agreement is precisely the silent success `java_test_count` exists to prevent.

### 7.2 Measured state

| gate | `anim8` |
|---|---|
| compile errors (with libGDX core, Scala 3.8.4) | **0** |
| files emitted | **16** (0 dropped, 0 injected) |
| model | 621 units / 57,201 symbols |
| signature consistency | 0 |
| omissions | 24 |
| portability (all / emitted / injected) | 263 / **112** / 0 |
| substitutions · manifest · port map · policy | 0 · 0 · 0 · 0 |
| collection closure · boundary · shared-iterator | 0 · 0 · 0 |
| trivia | 34 |
| porter notes uncovered · break residue | 0 · **0** |
| source map | 16 units / 568 members |
| decisions recorded | 632 rows, **251** about anim8's own declarations (235 `RetypedSignature`, 16 `RenamedPackage`); the other 381 are the base's, which `ENGINE-LIMITS.md` D2 says should not be republished here |
| **tests** | **23 of 23 PASSING** (4 files, hand-written) |

**Error trajectory: 1383 → 49 → 1 → 0**, every step an engine §1(a) fix (§7.3). **`break residue` is
0** on 19,594 lines of switch-heavy image code, which is the §9.5 control-flow work paying off on a
library it was not built for.

### 7.3 What this library taught the engine — four (a) fixes, no (b), no (c)

anim8 needed **no library-specific rule and no new phase parameter**. Its whole manifest is a
namespace claim, a package rename and the base's inherited surface. What it did produce is four
universal engine defects, each in its own commit, pinned by a spec through the pipeline
(`EmitterLiteralSpec`, `EnumCtorBodySpec`) and recorded in `ENGINE-LIMITS.md`:

| key | the gap | cost |
|---|---|---|
| L1 | a literal's VALUE was not re-escaped; a raw newline ENDS the literal | **1,334 errors from one file** |
| L2 | a prefix operator rendered against its operand lexes as one token (`--`) | **48 errors in one method** |
| T10 | a java enum CONSTRUCTOR's body was dropped; every field it assigned stayed at its default | **0 errors — 6 libGDX cubemap sides silently broken** |
| T11 | a PROMOTED enum constructor parameter is a member and collided with `Enum.name()` | 1 error |

**T10 is the one that matters.** It is a pre-existing silent correctness defect in **libGDX core**,
not in anim8: `Cubemap.CubemapSide`'s constructor builds `up` and `direction` from six float
parameters, so all six sides shipped with `up == null` and `getUp(out)` threw — in a port with zero
compile errors, no moved check count and no test covering the members. Three libraries had gone past
it. It was found here only because the *same* constructor shape also trips T11, which is a compile
error, and because a hand-written test asserts `DitherAlgorithm.WREN.legibleName == "Wren"`.

**L1's lesson generalises past its own fix**: libGDX has four files with a char literal outside the
five characters the emitter escaped (`\b`, `\f`, NUL in `JsonReader`, `PropertiesUtils`, `CharArray`,
`JsonSkimmer` — 11 members), and dotty happens to *tolerate* those raw, so the port compiled and
nobody looked. A corpus that has not met a construct is not evidence that the construct is handled.

### 7.4 The reference port is measurably WRONG here — `CLAUDE.md` §3.5, in the other direction

sge's `sge-anim8` externalised `ConstantData`'s four blobs into `.bin` classpath resources, and its
own `DataEmbeddingRedSuite` pins `ENCODED_SNUGGLY.length == 47006` and `TRI_BLUE_NOISE.length ==
6143`. **Those are the UTF-8 encodings of the characters, not the `getBytes(ISO_8859_1)` bytes.**

| blob | upstream (ISO-8859-1) | sge's `.bin` | the same chars as UTF-8 |
|---|---|---|---|
| `ENCODED_SNUGGLY` | **32,768** | 47,006 | 47,006 |
| `TRI_BLUE_NOISE` (and `_B`, `_C`) | **4,096** | 6,143 | 6,143 |

Three independent confirmations that 32,768 / 4,096 are right: decoding the Java literal by Java's
own escape rules gives them; upstream's javadoc calls `TRI_BLUE_NOISE` "a 4096-element byte array as
a 64x64 grid" and 64 × 64 = 4096; and `ENCODED_SNUGGLY` is a palette mapping, whose shape everywhere
else in this library is `byte[0x8000]` = 32768. So the reference port's dither data is wrong from its
first non-ASCII byte, and the suite that was written to defend it pins the wrong values.

`ConstantDataSuite` therefore pins the numbers from that independent oracle, not from what the port
emits — pinning what the port emits against what the port emits proves nothing — and the port keeps
upstream's own in-source embedding, which is also the form that works on Scala.js and Native. **A
reference port that SOLVED a problem is not automatically a model; check the answer.**

### 7.5 The manifest-inheritance shape

`Anim8Policy.core` is `LibgdxPolicy.core(repoRoot).extendedBy(PortManifest(…))`, four fields long:

```scala
name           = "anim8",
governs        = Set("com.github.tommyettinger.anim8"),
packageRenames = Map("com.github.tommyettinger.anim8" -> "sge.anim8"),
surface        = List(PortMapTransform.forBases("libgdx-core")),
```

Everything else — `dropTypes`, `dropMethods`, libGDX's `com.badlogic.gdx -> sge` rename and all six
surface phases — is INHERITED, and `ManifestAgreement` reports 0 on every run. Three details worth
keeping:

- **the rename is ADDITIVE, not a restatement.** Longest-prefix-wins keeps `com.badlogic.gdx -> sge`
  and `com.github.tommyettinger.anim8 -> sge.anim8` apart, so the dependent adds its own namespace
  without touching the base's.
- **`PortMapTransform.forBases("libgdx-core")` goes LAST**, for the reason `AshleyPolicy` states: it
  reads what the base actually EMITTED and reports a reference the base does not ship, so it must run
  after any seam that re-points such a reference. It reports **0** — anim8 touches none of libGDX's
  dropped types.
- **`inject` is empty.** anim8 ships no replacement file: libGDX core ships the replacements for the
  types *it* dropped, and §1.5's asymmetry means a dependent must not copy them.

**The conf door cannot express this port today.** `base = "…"` in a `.conf` resolves another CONF,
and there is no `corpus/ports/libgdx/main.conf` — `LibgdxPolicy` is Scala, because `ClassTableTransform`,
`StaticForwarderTransform` and `GdxSharedIteratorRule` are behaviour rather than data. So anim8 is a
hand-written `PortRun(...)` like Ashley. Converting it needs exactly one thing and it is not new
mechanism: libGDX's own conf, with its two configured phases reached through `TransformFactory`
names as `GdxSharedIteratorFactory` already is. Nothing in anim8's own policy resists config.

### 7.6 Residues, named and classified

| residue | count | kind | why it stays |
|---|---|---|---|
| `portability(emitted)` — `java.util.zip` | 112 | **not an engine gap** (`ENGINE-LIMITS` P3) | `DeflaterOutputStream` (100), `Deflater` (6), `CRC32` (5), `CheckedOutputStream` (1). PNG *is* DEFLATE; there is no portable substitute in the library's own terms. The reference port made the same call — its `ChunkBufferSuite` lives in `src/test/scalajvm`. |
| `portability(all)` − `(emitted)` | 151 | base's, not this module's | libGDX core's own 151, seen through the resolution root. Identical to the number `just gdx-measure` reports. |
| `omissions` — promoted constructor body on every path | 24 | (a), known | `ENGINE-LIMITS` C7, 8 each in `PaletteReducer`, `QualityPalette`, `FastPalette` — the palette classes have many constructors funnelling to one. |
| `trivia` — dropped comments | 34 | (a), known | `ENGINE-LIMITS` V1: `PaletteReducer` 17, `AnimatedPNG` 12, `FastPalette` 3, `OtherMath` 1, `FastPNG` 1. All are COMMENTED-OUT CODE inside method bodies, which the TIR has no node for. |

`remediation` reports 4 suggestions, all about the `java.util.zip` residue above; the one that
"needs a value from you" proposes dropping `ChunkBuffer` and injecting a replacement. **Not taken**:
a port that drops its PNG chunk framer has dropped PNG, and the JVM is a target this port supports.

### 7.7 Do NOT retry

- **Do not route the enum lowering through `CtorFunnel`.** Measured: it plans nothing for an enum,
  `Plan.primaryParams` comes back empty, and the whole primary parameter list vanished from every
  emitted enum (`ENGINE-LIMITS` T10).
- **Do not fix the `name`/`Enum.name()` collision by renaming the parameter.** It would need a
  §4.55 pass that can see an EMITTER-synthesised member, which no phase can today; the skip is
  taken, with its semantic caveat stated in `ENGINE-LIMITS` T11.
- **Do not trust `sge-anim8`'s `ConstantData` values** (§7.4). They are UTF-8 lengths.

### 7.8 Remaining

- **`ConstantData` and `ChunkBuffer` are package-private in Java and public in the port.** Both are
  declared `class X` with no modifier upstream. This is NOT one of the port's two recorded
  `WidenedVisibility` decisions — those are `AnimatedPNG#buffer` and `#deflater`, widened because a
  parent constructor's statements are replayed in the subclass. A Java package-private TYPE
  becoming a public Scala one is not recorded at all, which is the general item: it is a real
  surface difference that nothing counts. Harmless here (the hand-written suite is in the same
  package either way) and worth a decision kind — see §9.
- **A second source set for the demos** would need a libGDX backend, which is not ported and is not
  planned (§1.1's first surprise).
- **Behavioural coverage is 23 tests over 4 of 16 types.** `PNG8`, `AnimatedGif`, `PaletteReducer`
  and `LZWEncoder` — 16,700 of the 19,594 lines — are covered only by compilation. Writing an
  end-to-end encode/decode assertion needs a `Pixmap`, and libGDX's `Pixmap` is backed by
  `Gdx2DPixmap` (JNI), so it is a real piece of work rather than a missing test.

---

## 8. gdx-gltf — the port that measures a DEPENDENT's seams

`net.mgsx.gltf → sge.gltf`, Apache-2.0. **The largest port after libGDX core itself — 135 types /
11,307 lines — and the corpus's first genuine THIRD-PARTY extension.** Reproduce with
`just gltf-measure`, which emits both source sets and compiles libGDX core's Scala, gdx-gltf's
Scala, gdx-gltf's ported suite and gdx-gltf's hand-written suite on **one** `scala-cli` invocation.
Run it after a fresh `just gdx-measure`.

**Why it is in the corpus.** Ashley and anim8 are written by libGDX's own community against a small
part of its API. gdx-gltf is stacked on libGDX's *3D pipeline*, so its difficulty is INHERITANCE
DEPTH: `PBRShader extends DefaultShader extends BaseShader`, `Scene`/`SceneManager` over
`ModelInstance`/`RenderableProvider`, sixteen `Attribute` subclasses over `Attribute`'s
`register`/`compareTo` protocol, and `AnimationControllerHack`/`ModelInstanceHack`, which exist to
reach into libGDX's protected and private constructor state. Every one of those parents is EMITTED
Scala this run never sees — it resolves against libGDX's *Java* (§1.5) — so the port is a sustained
test of whether the base's transforms produce a surface a deep subclass hierarchy can extend. **Six
of its eight remaining errors are in exactly that seam**, and none of them is visible from the base.

### 8.1 Scope, and the test census

`gltf/src` (135 files) only. Excluded and named rather than silently filtered: `demo/` (35 files, a
libGDX application with desktop/html/android launchers) and `ibl-composer/` (25, a VisUI authoring
tool).

**`gltf/test` holds SEVEN java files and exactly EIGHT `@Test` methods, all in ONE file.** The other
six — `Benchmark`, `ExportOBJTest`, `ExportSharedIndexBufferTest`, `ImportGLTFTest`,
`SharedTextureTest`, `ProceduralExamples` — are `extends Game` / `extends ApplicationAdapter`
classes with a `main` that opens a window through `com.badlogic.gdx.backends.lwjgl`, which is the
ONLY import in the whole checkout that `gdx/src` cannot resolve. They are demos with `Test` in the
name; the jbump/anim8 lesson, a third time. `GltfTestMigrate` therefore NAMES its one input file
rather than globbing, and the lane's `java_test_count` runs over the WHOLE tree so the 8 is
re-derived on every run and a second file gaining a real `@Test` is reported.

Eight attribute-comparison tests say nothing about the glTF reader that is most of the library, so
`gltf-core/src/test/scala` adds **22 hand-written MUnit tests** over `GLTFTypes` — the file where the
specification's enumerations become libGDX values, 300 lines of pure functions with no GL context,
no asset and no backend. Every assertion in it is pointed at a `CLAUDE.md` §4.4 hazard rather than at
coverage: a `switch` with no `default` falling out to a `throw`, an `Integer` scrutinee against `int`
case labels, a mutated PARAMETER (`map(CubicWeightVector, …)` does `offset += w.count` twice, so the
three groups must come from three different windows), and every `map(…, fv, offset)` read at a
NON-ZERO offset because at zero an ignored offset is indistinguishable from a respected one. The lane
prints the two numbers SEPARATELY — a ported test and a written one are different evidence — and
`reconcile_outcomes` gates on the sum.

### 8.2 Measured state

| gate | `gltf` | `gltf-test` |
|---|---|---|
| compile errors (with libGDX core, Scala 3.8.4) | **8** | — (one invocation) |
| files emitted | **135** (0 dropped, 1 injected) | **1** |
| model | 740 units / 56,368 symbols | 741 / 56,420 |
| signature consistency | 1 | 1 (the same site) |
| omissions | 3 | 0 |
| portability (all / emitted / injected) | 151 / **0** / **0** | 151 / 0 / 0 |
| substitutions · manifest · port map · policy | 0 · 0 · **0** · 0 | 0 · 0 · 0 · 0 |
| collection closure · boundary · shared-iterator | 0 · 0 · 0 | 0 · 0 · 0 |
| trivia | 10 | 0 |
| porter notes uncovered · break residue | 0 · **0** | 0 · 0 |
| source map | 135 units / 1,523 members | 1 / 9 |
| decisions recorded | 1,763 rows, **548** about gdx-gltf's own declarations; 1,215 withheld as the base's (D2) | 44, 2,937 withheld |
| **tests** | 8 ported + 22 hand-written = **30, NONE RUN** — the port does not compile (§8.4) | |

**Error trajectory: 19 → 16 → 14 → 9 → 8**, on 135 files at the first attempt. `break residue` is
**0** and `portability(emitted)` is **0** on a library full of `switch`-driven enum mapping, which is
§9.5's control-flow work and the portability rules paying off on a library they were not built for.

`trivia` moved 6 → 10 and the four are accounted for: they are the comments INSIDE the two bodies
`MethodBodyTransform` replaces ("call X via reflection to avoid compilation error with GWT"). The
code they describe is deliberately not in the port, so the comments go with it — and `TriviaCheck`
counting them is the honest behaviour, not a defect. Expect any `MethodBodyTransform` entry to cost
its body's comments.

### 8.3 What this library taught the engine — three (a) fixes and a fourth found by reading

| key | the gap | cost |
|---|---|---|
| — | `<clinit>` reached an `export` selector list; `export P.{<clinit> => _, *}` is an XML start tag to dotty | 3 errors |
| `ENGINE-LIMITS.md` D6 | an all-static java class is still a TYPE, and the object collapse had no third guard | 5 errors |
| `ENGINE-LIMITS.md` D6.5 | a DROP and its INJECTION are in different namespaces, so `Substituted` had never been produced by a renaming port | **10 false findings** |
| `ENGINE-LIMITS.md` T12 | java `protected` is dropped, and accessibility is an input to OVERLOAD RESOLUTION | 1 error |

Each of the first three shipped in its own commit with a spec, and **each moved 0 members on
libGDX** — the `<clinit>` guard cannot fire on a type with no initializer block, the type-position
guard is narrowed to declaration types and class literals, and the port-map fix moves an ARTIFACT
and no emitted text.

**D6's dead end is the part worth carrying.** The obvious implementation — read every type
occurrence from `Phase.transformType` — was built first and measured: a term's own `tpe` is an
occurrence, so `Gdx.app` (a static ACCESS, the thing the collapse exists for) reads as a type usage.
It de-collapsed **29 of libGDX core's 31** constant holders and moved **36 members**, still compiling
and moving no check count. `AllStaticClassAsTypeSpec` pins the static-access NEGATIVE for that
reason.

**D6.5 is the one nothing would have found.** `PortMap.of` had distinguished `Dropped` from
`Substituted` since it was written, deciding by `injectedFqns(fqn)` — a manifest key compared against
a set of emitted file names. False for every renaming port, so `Substituted` had never once been
produced by one, and nothing failed until a dependent read the map. gdx-gltf is the first port to
REFERENCE an injected replacement (six of its files use libGDX's substituted `Json`) and was told ten
times that the base "emits nothing at that name and nothing replaces it" about a type it compiles
against. This is `CLAUDE.md` §4.56 at a third artifact.

### 8.4 The residue — 8 errors, all classified, and why the tests cannot run

The port does not compile, so **none of its 30 tests has ever executed.** `CLAUDE.md` §3 is explicit
that a test which cannot run is not a test that passed, and the lane says so rather than reporting a
suite of zero.

| errors | site | classification |
|---|---|---|
| ~~3~~ **2** | ~~`ClippingPlaneAttribute`,~~ `PBRCubemapAttribute`, `PBRTextureAttribute` — `extends CubemapAttribute` / `extends TextureAttribute` with no arguments | **D4**, (a) engine |
| 4 | `ModelInstanceHack` — `this.copyNodes(…)`, `private` in libGDX's emitted `ModelInstance` | **D5**, (a) engine |
| 1 | `MeshLoader.java:252` — `vertexAttributes.toArray(VertexAttribute.class)`, a member the base drops | **D7**, (b) a phase that does not exist |

**8 -> 7: one of the D4 trio is fixed, and WHICH one says exactly what is left.**
`ClippingPlaneAttribute`'s two roots both call `super(Type)` — the SAME parent constructor — so
widening the synthesised primary from "some root is nilary" to "all roots reach one parent
constructor" gives it a local `class ClippingPlaneAttribute protected (sup$0: Long) extends
Attribute(sup$0)`, computed from its own Java with no seed from anywhere, and D4's cause for it is
gone exactly as `DESIGN.md` §8.2 says it should be. The other two are NOT that shape: their roots
reach `CubemapAttribute(long, TextureDescriptor)` and `(long, Cubemap)` — two DIFFERENT parent
constructors, so they are genuine WALL classes and the fixpoint is still their only answer. They need
§8.2's reduction step (both parent overloads reduce to the emitted primary's single `long` slot) or
the port-map seed D4 describes; a local derivation cannot reach them.

**D4 is the largest thing a dependent port has surfaced, and it is confirmed rather than inferred.**
`CtorFunnel.Plans` decides which java constructor becomes the Scala primary at a FIXPOINT over the
whole program, and that answer is the class's emitted parameter list. A dependent's `Program`
CONTAINS its base, so the fixpoint sees a different set of classes and can reach a different answer
for a BASE class than the base's own run did. libGDX emits `abstract class Attribute(type$p: Long)`;
gdx-gltf adds three subclasses with several roots and no shared parameter list, its fixpoint withheld
the promotion, `replayFor` accepted a nilary prologue for the parent, and the three classes emitted
`extends Attribute` with no arguments against a parent that has none.

A minimal probe settles that it is drift and not a plain funnel gap: the same shape in a SINGLE
program (a paramful parent, a subclass with two roots both calling `super(K)`) emits
`class Heir extends Parent` with the parent's promotion correctly withheld, and compiles. Two
modules, one program each, two different correct answers.

Nothing in the dependent's own run disagrees with itself: `ManifestAgreement` reports 0 because a
funnel plan is not a manifest key, and the port map records `Attribute` as `Ported`, which it is. The
disagreement exists only when the two modules are compiled together.

### 8.5 Do NOT retry

- **Reading `Phase.transformType` bare for "is this named as a type".** 29 of libGDX's 31 constant
  holders de-collapsed, 36 members moved, compile still 0 and no check count moved. See D6.
- **Refusing every cross-class private widening in `CtorFunnel`** to fix D5. libGDX core makes **22**
  `WidenedVisibility` decisions of its own, all of them within one module and all of them sound, so a
  blanket refusal regresses the base to fix the dependent. The missing input is which classes the run
  EMITS, which `Plans` does not have — the same input D4 needs.
- **Refusing the withheld promotion** to fix D4. That is `ENGINE-LIMITS.md` C1 exactly (+14 on
  libGDX) and it breaks the dependent's own subclasses instead.
- **`dropTypes` + `inject` for the four affected files** to reach a green compile. That forks
  `ClippingPlaneAttribute`, `PBRCubemapAttribute`, `PBRTextureAttribute` and `ModelInstanceHack` from
  upstream permanently to hide two engine gaps, and `ENGINE-LIMITS.md` K3 is the rule against it:
  injected sources are for SEMANTICS the target lacks, never for adapting SHAPES.

### 8.6 Remaining work, highest value first

- **D4 (3 errors).** A class the run does not EMIT must have its funnel plan READ, not recomputed —
  from the base's published port map, which is already the channel for "what did the base actually
  do". The map must carry each type's primary parameter list, and `Plans` must seed itself from it
  for every non-owned class.
- **D5 (4 errors).** `Plans` needs the same "which classes does this run emit" input, and then M6's
  answer applies unchanged: refuse the replay whose widening cannot be performed, count the dropped
  `super(args)` as an omission, and compile.
- **D7 (1 error).** A CALL-SITE substitution phase — the call-level twin of `MethodBodyTransform`,
  keyed like `dropMethods`, replacement text naming the receiver and arguments.
- **T12.** Render java's `protected` as `protected[<package>]`. Priced at 867 declarations of emitted
  text on libGDX core alone; it wants its own cycle and its own baseline promotion, and until it
  lands the port carries one `MethodBodyTransform` entry that exists only to restate the overload
  javac chose.
- **Behavioural coverage is 30 tests over 5 of 135 types**, and none has run. The exporter and the
  loaders are covered only by compilation, and their round-trip needs the reflective `Json` the base
  deliberately drops — the reference hand port replaced it with 2,268 lines of hand-written Jsoniter
  codecs (`GLTFCodecs`, `GLTFExporterJson`), which is a decision this port has not made.

### 8.7 What the reference port did — `CLAUDE.md` §3.5

`../sge/sge-extension/gltf` is **100 % coverage**, 135 upstream types → 141 Scala files, with six
files that have no upstream counterpart. Two things it settles and one it does not:

- **SOLVED, and this port took the answer**: both `ClassReflection` sites that carry an upstream
  "…via reflection to avoid compilation error with GWT" comment are a GWT workaround and nothing
  else. `PixmapBinaryLoaderHack.scala` is `new Pixmap(encodedData, offset, len)` and
  `GLTFBinaryExporter.savePNG` is `PixmapIO.writePNG(file, pixmap)`, each with the WebGL guard kept.
- **SOLVED differently, and this port did NOT follow**: `GLTFMaterialExporter.ext` uses
  `tpe.getDeclaredConstructor().newInstance()` — plain JVM reflection, which does not link on
  Scala.js or Scala Native. This port uses the factory registry libGDX core's own injected `Pools`
  and Ashley's `ComponentFactories` already use.
- **NOT solved, replaced**: the whole reflective `Json` path. sge hand-wrote `GLTFCodecs` (1,378
  lines) and `GLTFExporterJson` (890) rather than porting it. This port compiles against libGDX's
  injected `Json` facade, whose reflective paths raise `UnsupportedOperationException` naming the
  seam — so `fromJson(GLTF.class, …)` at `SeparatedDataFileResolver.java:30` and
  `BinaryDataFileResolver.java:97`, and `toJson` at `GLTFExporter.java:238`, are inert at run time.
  That is an inherited decision, not a gdx-gltf one, and it is why loading a real `.gltf` is not
  something this port can be tested for today.

---

## 9. libgdx-screenmanager — the port with a dependency the corpus does not own

`de.eskalon.commons.{screen,core,utils} → sge.screen{,.utils}`, Apache-2.0. **A dependent port of
libGDX core**, in the same shape as Ashley's and anim8's: `just screens-measure` compiles libGDX
core's emitted Scala, screenmanager's emitted Scala, screenmanager's HAND-WRITTEN support sources
and its hand-written suite on **one** `scala-cli` invocation, and must run after `just gdx-measure`.

**Why it is in the corpus.** It is the first library whose upstream depends on ANOTHER library this
corpus neither vendors nor ports. `build.gradle` declares

```
api "com.github.crykn.guacamole:gdx:v0.3.6" // is exposed because of NestableFrameBuffer
```

and ten guacamole types reach into these 22 files. That is a shape every real library has and no
corpus library had yet — libGDX core depends on nothing, and Ashley, anim8 and the test suites all
depend on a module the corpus DOES port. It is also the first port to ship a non-empty
`src/main/scala`.

### 9.1 Scope, named rather than silently dropped

`src/main/java`, 22 types (23 files minus `package-info.java`).

`src/example/java` (5 files) is out of scope and named: it is a `gdx-backend-lwjgl3` demo
application, and no libGDX backend is ported (§1.1's first surprise). `src/test/java` is §9.4.

### 9.2 Measured state

| gate | `screens` |
|---|---|
| compile errors (with libGDX core, Scala 3.8.4) | **0** |
| files emitted | **22** (0 dropped, 0 injected) |
| hand-written support sources (`src/main/scala`) | **9 files, 458 lines** — the guacamole replacements |
| model | 627 units / 52,867 symbols |
| signature consistency · omissions | 0 · **0** |
| portability (all / emitted / injected) | 151 / **0** / 0 |
| substitutions (emitted / dangling) · manifest · port map · policy · remediation | 0 · 0 · 0 · 0 · 0 · 0 |
| collection closure · boundary · shared-iterator | 0 · 0 · 0 |
| trivia | **7** |
| porter notes uncovered · break residue | 0 · **0** |
| source map | 22 units / 175 members; port map 37 types / 169 members |
| decisions recorded | 139 rows (`RenamedMember` 45, `RetypedSignature` 34, `RenamedPackage` 22, `DroppedMember` 16, `DroppedType` 11, `FunnelledCtor` 11); 1,810 withheld as the base's (`ENGINE-LIMITS` D2) |
| **tests** | **16 of 16 PASSING** (hand-written; upstream's 12 are §9.4) |

**`omissions` and `portability(emitted)` are both 0**, which no other dependent port has managed —
the 151 portability sites are libGDX core's own, seen through the resolution root, and identical to
what `just gdx-measure` reports.

**Error trajectory.** Two numbers, because two different things were being counted:

- **guacamole references the emitted Scala could not resolve: 26 → 0**, closed by the engine fix in
  §9.5. Measured by `grep -o 'de\.damios[A-Za-z0-9_.]*' screens-core/src_managed`, NOT by the
  compiler, because the compiler never saw them: the fix landed before the first compile. 23 static
  calls and 3 annotations.
- **compile errors: 5 → 0.** Four `@org.jspecify.annotations.Nullable` (the annotation jar was not
  on the lane's compile classpath — a real upstream dependency, now a `screens_deps` coordinate) and
  one shim written against a Scala vararg where the engine emits a Java `T...` as `Array[T]`
  (`ENGINE-LIMITS` K6.5, from the other side: the SHIM has to match what the emitter produces).

### 9.3 guacamole — a dependency the corpus resolves and does not port

Resolution and emission are two problems and only the first was already solved.

**Resolution** is `ScreensClasspath`, which fetches exactly what `build.gradle` declares
(`com.github.crykn.guacamole:gdx:v0.3.6` from jitpack, plus `org.jspecify:jspecify`) with `cs fetch
--classpath` and writes the one joined line `FrontendConfig.classpath` reads — the mechanism
`SimpleGraphsClasspath` established for JUnit, and for the same reason: an import the frontend
cannot resolve does not fail, it resolves WRONGLY. libGDX itself is EXCLUDED from that fetch,
because it arrives as a source resolution root and a second copy from a jar would be a second answer
to every `com.badlogic.gdx.*` name.

**Emission** is `TypeRedirectTransform` — the engine's existing §1(b) mechanism for a type a module
must reference and cannot ship — re-pointing all ten at `sge.screen.guacamole.*`, which
`screens-core/src/main/scala` supplies. That is the whole of this port's library-specific policy:
**no §1(c) rule, no new phase, no new phase parameter.** The table is nine lines in `ScreensPolicy`.

The replacements are hand-written, which is a statement about SCOPE and not about quality — they are
the hand-written half of a port (`CLAUDE.md` §5.5), each carrying guacamole's own Apache-2.0
attribution, and each with a note on what it deliberately does not reproduce (`Preconditions`'
`checkNotEmpty`, `NestableFrameBuffer`'s builder: a shim member with no caller is untested code that
reads as verified). **The day guacamole becomes a corpus port of its own, the redirect table and
this directory are deleted together** and the emitted references follow that port's own rename.

Two things the shims must get exactly right, both learned from a failure:

- **the JDK exception TYPE each precondition raises.** `checkArgument`/`checkState`/`checkNotNull`
  are `IllegalArgumentException`/`IllegalStateException`/`NullPointerException`. Scala's `require`
  is the obvious substitute and raises `IllegalArgumentException` for all three, which would turn
  "used before `initialize()`" from a state error into an argument error — silently, with a green
  compile. The suite asserts each one.
- **`Pair`'s field NAMES.** The emitted `ScreenManager` reads `pair.x` / `pair.y` directly, because
  that is what Java resolved. A `Tuple2` has `_1`/`_2` and would compile at the declaration and fail
  at every use.

### 9.4 The upstream suite is NOT migrated — 12 `@Test`, 10 of them structurally out of reach

Upstream ships 7 test files and 12 `@Test`. There is no `ScreensTestMigrate`, and the reason is not
effort:

- **`LibgdxUnitTest`, the base class of six of the seven files, boots
  `com.badlogic.gdx.backends.headless.HeadlessApplication`.** No libGDX backend is ported, by this
  engine or by the reference hand port. There is nothing to compile the fixture against.
- **`ScreenManagerUnitTest` adds `Mockito.mockStatic(ScreenFboUtils.class)` and
  `Mockito.spy(new ScreenManager())`.** Both instrument JVM bytecode of the type under test at run
  time — `mockStatic` in particular replaces a static method of the ported library so the tests
  never touch GL. The port exists for Scala.js and Scala Native, where neither is available.

Only `BasicInputMultiplexerTest` and `TimedScreenTransitionTest` need neither, and both of their
bodies are reproduced in the hand-written suite, marked `(upstream)`.

So the behavioural gate is **16 hand-written MUnit tests** in `screens-core/src/test/scala`, adapted
from upstream's two reachable tests and from the reference hand port's six suites. `just
screens-measure` prints upstream `@Test`, emitted, and hand-written side by side and says which of
the twelve are unreachable and why — `0` emitted must not read as agreement, and the day a backend
is ported that block is what says the twelve became reachable.

**What the suite found that no count did.** One assertion was written expecting `pushScreen(screen,
null)` to queue a NULL transition supplier. Upstream writes `pushScreen(() -> screen, () ->
transition)` unconditionally, so the supplier is non-null and yields null — the difference between
`render` NPE-ing and playing no transition. The port was faithful and the test was wrong; nothing
but running it could have said so.

**What the suite cannot reach, stated rather than implied.** Everything below runs with no GL
context and no `Gdx.app`. `NestableFrameBuffer`'s nesting contract, `QuadMeshGenerator`'s vertex
layout and `ScreenManager.render`'s framebuffer round trip all issue a GL call on their first
statement. They are covered by compilation only. That is the same limit anim8's suite has and for
the same reason (§7.8).

### 9.5 What this library taught the engine — two (a) fixes, no (b), no (c)

Both are completeness gaps in machinery that already existed, both are in `ENGINE-LIMITS.md`, both
are pinned by `TypeRedirectTransformSpec`, and both moved **0 members** on every other port:

| key | the gap | measured |
|---|---|---|
| `ENGINE-LIMITS` M5.8 | `StandardTraversal.mapSymbols` routed `Symbol.info` and NOT `Symbol.annotations`, so EVERY retyping phase in the engine left an annotation naming the type it had just moved | 3 sites here; 0 members changed on libGDX core, Ashley, anim8, simple-graphs, noise4j, jbump |
| `ENGINE-LIMITS` D8 | `TypeRedirectTransform` promised "every reference moves together, so a partial redirect is impossible" and rewrote `TypeRepr` only — a static access is rendered from a `Tree.Ident`'s SYMBOL, or from the member's OWNER when the type was parsed | 23 sites here; the parsed half is proven by the spec and by no corpus number |

The second one is the more transferable lesson: **a phase whose doc claims totality owes a spec per
OCCURRENCE KIND, with the negative half.** The positive assertion passes on a partial redirect. The
promise went untested for as long as it did because the first library to use the phase redirected a
type with no statics and no annotation use.

### 9.6 Where this port is strictly better than the reference hand port

`sge-extension/screens` is 20 Scala files to these 22 Java ones (§1.1: 86 % coverage). The
difference is not only arithmetic:

- **`NestableFrameBuffer` is absent from sge**, which uses a plain `FrameBuffer` for the screen
  manager's own two buffers. libGDX's `FrameBuffer.end()` calls `GLFrameBuffer.unbind()`, which
  binds framebuffer **0** — the default — whatever was bound on `begin()`. So a screen or transition
  that binds an FBO of its own inside a managed render unbinds to the DEFAULT buffer when it
  finishes, and the manager's buffer is silently lost for the rest of the frame. Nothing about that
  is a compile error and nothing throws; the frame comes out wrong. It is the type upstream depends
  on guacamole FOR, and this port carries it — **verified present in the emitted
  `ScreenManager.createFrameBuffer()`, whose return type is the redirected
  `sge.screen.guacamole.NestableFrameBuffer`.** (What is NOT verified is its RUNTIME behaviour: see
  §9.4 — the nesting contract needs a GL context.)
- **`ManagedScreenAdapter`, `BasicInputMultiplexer` and `Supplier` are simply not in sge.** All
  three are ported mechanically here, and the hand-written suite covers all three — including
  upstream's own `BasicInputMultiplexerTest`, which is coverage of a type the reference port does
  not have at all.

sge did hand-port guacamole's `QuadMeshGenerator` into `sge.screen.utils`, and it also renamed
`ScreenTransition.render`'s parameters and moved `ManagedGame` from `core` to `screen`. The package
flattening is followed (three rename pairs, §9.3); the parameter renaming is not, because it is a
redesign no mechanical rule produces.

### 9.7 Do NOT retry

- **Do not fix a static redirect by remapping the qualifier `Ident` alone.** For a type the frontend
  PARSED, `TirEmitter.staticThroughInstance` re-derives the name from the member's owner and undoes
  it one layer later — silently, with no count moving (`ENGINE-LIMITS` D8).
- **…and do not fix THAT by re-pointing the original member's `owner`.** Measured: **Ashley's
  `port-map` 0 → 6**, all six inside `ReflectionPool.java` — the type Ashley redirects — in a run
  whose emitted text was byte-identical, so `members.tsv` said nothing and only the check diff did.
  A redirected type's members are the BASE's declarations; moving their owner detaches them from
  their unit and every ownership-filtered check stops recognising them (`ENGINE-LIMITS` D2, D8).
  Mint a TWIN owned by the target instead: 6 → 0, 0 members moved.
- **Do not give a minted redirect TARGET a self `TypeRef` as its `info`.** `TirEmitter.isTypeRef`
  reads exactly `fullName` dotted, `#`-free and `info == NoType` to decide that an external symbol
  is a TYPE; a self `TypeRef` reads as a term and the static half emits an unqualified identifier —
  valid Scala naming nothing. Measured and reverted in the same cycle.
- **Do not put guacamole on the frontend classpath without excluding libGDX.** guacamole's POM
  pulls `com.badlogicgames.gdx:gdx:1.13.5`, and this port resolves libGDX from SOURCE; two answers
  to every `com.badlogic.gdx.*` name, decided by scan order, is not something to leave to chance.
- **Do not attempt the upstream suite before a libGDX backend is ported** (§9.4). Ten of the twelve
  tests are a fixture, not a body.

### 9.8 Remaining

- **7 trivia residues**, all `// don't do anything by default` / `// do nothing` / `// not needed`
  line comments inside method bodies the emitter renders as `()`. `ENGINE-LIMITS` V1: a comment on a
  construct the EMISSION consumes has nowhere to go.
- **Behavioural coverage is 16 tests over 8 of 22 types**, and the GL half of the library is covered
  by compilation only (§9.4). The eleven concrete transitions all render through a `SpriteBatch` or
  a `ShaderProgram`; `ShaderCompatibilityHelper`'s pure string rewrites are asserted, the rest is
  not.
- **guacamole is not ported.** Ten types are hand-written Scala rather than emitted, which is 458
  lines this port cannot regenerate. It is the obvious next library: it is 37 files / 3,544 lines
  across two Maven modules, Apache-2.0, and porting it would delete `ScreensPolicy.guacamole`
  outright — the cleanest available demonstration that a redirect is a stopgap and a port is not.

---

## 10. gdx-vfx — the GL-facing port, and the one whose API surface is mostly its BASE's

`com.crashinvaders.vfx → sge.vfx`, Apache-2.0. **The fifth corpus library and the third genuine
dependent port.** Reproduce with `just vfx-measure`, which compiles libGDX core's emitted Scala,
gdx-vfx's emitted Scala and gdx-vfx's hand-written suite on **one** `scala-cli` invocation — the
port is `RuntimeMode.Dependency`, so the collection shims are vendored by libGDX core and compiling
`vfx-core` alone measures nothing.

**Why it is in the corpus.** Every library before it either used libGDX as a toolbox (Ashley,
anim8) or did not use it at all. gdx-vfx's whole reason for existing is a resource the JVM does not
own: every effect is a `ShaderProgram` compiled from a `.vert`/`.frag` asset, driven through
framebuffer ping-pong. Two consequences no earlier library produced:

- **nearly every emitted signature mentions a type the BASE emitted** — `FrameBuffer`, `Mesh`,
  `ShaderProgram`, `GL20`, `Texture`, `Pixmap.Format`, `Gdx`, `WidgetGroup`. That makes it the
  sharpest test of `CLAUDE.md` §1.5 in the corpus: it can only compile if libGDX core's transforms
  did to those signatures exactly what this run assumes they did, and `ManifestAgreement` reports 0
  because the policy is `LibgdxPolicy.core` EXTENDED rather than restated.
- **a reflective branch that exists only for a backend nobody ports** — see §10.3.

### 10.1 Scope, named rather than silently dropped

The two LIBRARY gradle modules, `gdx-vfx/core/src` (23 types) and `gdx-vfx/effects/src` (21),
emitted into ONE sbt module. That is what the reference hand port does too
(`../sge/sge-extension/vfx` holds both at `sge.vfx`): they share one package root, effects depends
on core, and nothing depends on effects alone, so a second port would buy a module boundary the
consumer does not have.

Excluded, and named:

- **`gdx-vfx/gwt/src`** (1 file, `GwtVfxGlExtension`) — the GWT backend's `VfxGlExtension`. sge
  targets Scala Native and Scala.js; the reference port does not carry it either. Its absence is
  what makes §10.3's reflective branch unreachable rather than merely unported.
- **`demo/`** (74 files) — five launcher modules and an LML/VisUI-driven demo application, needing
  third-party libraries that are not in the corpus.

**`@Test` over the WHOLE upstream checkout, comments stripped: 0.** gdx-vfx ships no test source set
at all — not a set of demos misread as one (anim8) and not a runnable sample module (jbump), simply
nothing. So there is no `VfxTestMigrate`, and the behavioural gate is anim8's precedent: **64
hand-written MUnit tests** under `vfx-core/src/test/scala` (§10.5). `just vfx-measure` prints the
upstream zero and the hand-written count side by side, because `0 == 0` reading as agreement is the
silent success `java_test_count` exists to prevent.

### 10.2 Measured state

| gate | `vfx` |
|---|---|
| compile errors (with libGDX core, Scala 3.8.4) | **0** |
| files emitted | **44** (0 dropped, 0 injected), 4,936 lines from 5,663 java |
| model | 649 units / 53,996 symbols |
| signature consistency | 0 |
| omissions | **2** (§10.6) |
| portability (all / emitted / injected) | 151 / **0** / 0 |
| substitutions · manifest · port map · policy | 0 · 0 · 0 · 0 |
| collection closure · boundary · shared-iterator | 0 · 0 · 0 |
| trivia | **11** (§10.6) |
| porter notes uncovered · break residue | 0 · **0** |
| source map | 44 units / 910 members |
| decisions recorded | 350 rows about gdx-vfx's own declarations (216 `RetypedSignature`, 46 `RenamedMember`, 44 `RenamedPackage`, 16 `DroppedMember`, 15 `FunnelledCtor`, 11 `DroppedType`, 1 `RedirectedCall`, 1 `SubstitutedBody`); 1,216 withheld as the base's, per `ENGINE-LIMITS.md` D2 |
| **tests** | **64 of 64 PASSING** (6 files, hand-written) |

**Error trajectory: 11 → 10 → 7 → 6 → 5 → 4 → 1 → 0.** One §1(b) policy step and SEVEN §1(a)
engine fixes, one commit and one measurement each — six of the seven moved the error count and the
seventh moved `porter-notes` instead, which is the only gate that could see it. `portability(emitted)` is **0**: the 151 are every one
in libGDX's own files, which D2's ownership filter keeps out of this port's emitted column.

### 10.3 The one policy decision — a §1(b) body substitution, and why it is not a fork

`VfxGLUtils`' STATIC INITIALISER is gdx-vfx's only reflective site:

```java
if (Gdx.app.getType() == ApplicationType.WebGL)
  glExtension = (VfxGlExtension) ClassReflection.newInstance(
      ClassReflection.forName("com.crashinvaders.vfx.gwt.GwtVfxGlExtension"));
else
  glExtension = new DefaultVfxGlExtension();
```

Both halves are absent from this port BY DECISION: `ClassReflection` is a type the base drops
(reflective instantiation is the one thing Scala.js and Native cannot do) and `gdx-vfx/gwt` is out
of scope. So the branch is not merely unported — it is **unreachable**, and keeping the reflective
call to preserve a branch that cannot be taken would fail to compile for no behaviour.

`MethodBodyTransform` replaces the block with the else-branch alone, keyed
`com.crashinvaders.vfx.gl.VfxGLUtils#<clinit>`. The reference hand port reached the same place —
`../sge/sge-extension/vfx/.../VfxGLUtils.scala` has no WebGL branch at all, `initExtension()`
assigns `DefaultVfxGlExtension()` unconditionally, so it SOLVED this rather than skipping it
(`CLAUDE.md` §3.5). Expressing it as policy rather than as a fork keeps the other ~100 lines of that
class — shader compilation, the viewport query, the GL state query — mechanically translated and
tracking upstream. `port-map 2 → 0`, and it is the port's one `SubstitutedBody` decision.

**No §1(c) rule, no new phase parameter, no injected source.** The rest of the manifest is a
namespace claim, one package-rename pair and the base's inherited surface.

### 10.4 What this library taught the engine — seven (a) fixes, one (b), no (c)

The keys are `ENGINE-LIMITS.md` entry numbers as they stood when this was written; each row names the
entry's TITLE too, so a renumbering there does not orphan the reference.

| key | entry title | cost |
|---|---|---|
| M8 | *A note is emitted only where the emitter ASKS for one — a member on a special path has none* | `porter-notes 1 → 0` |
| K10 | *A TYPE-VARIABLE map key arrives carrying java's `Object` WIDENING* | 10 → 7 |
| K11 | *A CAPACITY hint at a HASHED collection has no one-argument scala constructor* | 7 → 6 |
| T13 | *`Enum.ordinal()` is part of every java enum's SURFACE, mentioned or not* | 6 → 5 |
| L3 | *A CLASS LITERAL needs a CLASS — an all-static class named by one must not collapse* | 5 → 4 |
| G20 | *A STATIC member sees NONE of its class's type parameters — carry it in the FRAME, not a flag* | 4 → 1 |
| G21 | *A RAW result read through an ERASED RECEIVER must be TYPED as what it emits* | 1 → **0** |

Every one is pinned by a spec through the pipeline — `MethodBodyTransformSpec`,
`CollectionsTransformSpec`, `EnumCtorBodySpec`, `StaticCollapseSpec`, `StaticTypeParamScopeSpec`,
`ErasedReceiverResultSpec` — and every one but T13 moved **0 members** on the other nine ports.

**T13 is the exception and its blast radius is accounted**: it is emitted text for every ported
enum. libGDX core 69 members, libGDX test 71, Ashley 75, anim8 71, noise4j 6, simple-graphs 0, jbump
0 — and, measured at integration because both ports landed while this one was in flight, gltf 5 and
screenmanager 1 (four enum surfaces: `Interpolation`, `SceneRenderableSorter.Hints`,
`PBRShaderConfig`, `SlidingDirection`). Every changed unit an enum or the type that declares one,
verified against the members diff, and no error count, check count or test outcome moved anywhere
(gltf errors 8 → 8). Baselines promoted in the commits that measured them.

### 10.5 The behavioural gate is HAND-WRITTEN, and it stops at the first GL call

64 tests over six suites, every expectation read off the UPSTREAM JAVA rather than off the emitted
Scala — a test written from the output can only confirm the output. They target the defects that
move no count (`CLAUDE.md` §4.4):

| suite | what it pins |
|---|---|
| `PrioritizedArraySuite` (15) | the iterator's `index++` USED AS A VALUE; `remove(int)` against `remove(T)` on an `Integer` element type; sort stability; the shared-iterator reset |
| `ValueArrayMapSuite` (11) | java's null-on-miss against scala's `Option`; `put`/`remove` returning the PREVIOUS value; `findKey`'s reference `==`; the reused scratch key array |
| `CommonUtilsSuite` (11) | the `switch` whose only exit is a throwing default; the shared `tmpColor` aliasing; `Align`'s `static final` constants; null-safe compare in both directions |
| `VfxFrameBufferSuite` (14) | the modulo in `changeToNext`; the `continue` that skips an uninitialised FBO; the two `IllegalStateException`s; the static nesting counter |
| `VfxEnumSuite` (7) | `ordinal()`, `name()`, `valueOf`, and the enum CONSTRUCTOR BODY two levels deep (`BlurType.tap.radius`) — T10's exact shape |
| `VfxGlViewportSuite` (4) | the self-returning setters `VfxGLUtils.getViewport` relies on |

**Canary-checked rather than assumed**: flipping the `Tap5x5.radius` and post-increment expectations
turns both suites red and nothing else.

**Where it stops is a fact about the library, not a compromise.** A `VfxFrameBuffer`'s constructor
only allocates matrices; `initialize(w, h)` is the first line that touches GL. So the rotation
arithmetic, the guard clauses and the ping-pong swap are reachable and the rendering is not. The
reference port covers the same ground with a headless GL stub over its own `Sge` context; this port
emits libGDX's `Gdx` statics instead and has no context to stub, so it stops at the GL line rather
than building a ~150-method `GL20` no-op.

**Not covered at all**: the 21 effect classes and `VfxManager`/`VfxRenderContext`, whose every
method is a shader draw. That is 27 of 44 types resting on compilation alone.

### 10.6 Residues, named and classified

- **omissions 2.** `ShaderVfxEffect`: one `@SuppressWarnings` dropped — a source-retention java
  annotation with no scala meaning, correctly not emitted and correctly counted (T6). And
  `PrioritizedArray`: one C7 promoted-constructor path — two roots, neither delegating, so the
  nilary body (`items = new ValueArrayMap<>()`) also runs on the capacity path and is immediately
  overwritten. Declared COST, not divergence, and `PrioritizedArraySuite`'s capacity test is what
  says the observable behaviour is identical.
- **trivia 11.** Nine are `//` comments inside bodies the emitter rewrites — the V1 category, on
  commented-out code and `// Do nothing.` markers. **Two are file-leading Apache blocks**
  (`LensFlareEffect`, `LevelsEffect`), and those are a NEW limit: both files open with two
  consecutive block comments and Spoon's compilation unit reports only the first. The licence text
  itself survives — it IS the first block, and the second is an anonymous copy of the same notice —
  so nothing legally material is lost here. `ENGINE-LIMITS.md` V3, open.
- **`portability(all)` 151, `portability(emitted)` 0.** Every finding is in libGDX's own files, which
  this port resolves against and does not emit. It is the base's number, reported here because the
  program contains the base (D2).
- **`PrioritizedArray` carries the shared-iterator hazard the `gdx-shared-iterator` rule looks for,
  and the rule does not see it.** `PrioritizedArrayIterable` is a hand-copy of libGDX's own
  alternating iterator1/iterator2 idiom, in gdx-vfx's namespace — so the §1(c) rule's list of
  cached-iterator collections, which is a fact about `com.badlogic.gdx.utils`, misses it and reports
  0. Correctly: the rule is libGDX's, and gdx-vfx re-implementing the idiom is gdx-vfx's own
  invariant. Nesting an iteration over one `PrioritizedArray` inside another would terminate the
  outer loop early, silently, exactly as it would in libGDX.

### 10.7 Do NOT retry

- **Do not subtract the class literal's OWN unit from the collapse guard.** The two branches that
  independently fixed the object-collapse guard merged into one `typeNamedElsewhere` (the resolution
  is in its doc comment): the declaration arm excludes self-naming through the owner chain, and the
  literal arm deliberately does NOT exclude the literal's own unit — `X.class` inside `X` is the
  log-tag idiom and needs `classOf[X]`. Re-adding the subtraction fails exactly
  `StaticCollapseSpec`'s "…including from inside itself".
- **Extending `knownReceiverArgs`' unchecked-conversion guard ALONE was INERT** (1 → 1). The
  plausible fix for `Found: Wrapper[Object] / Required: Wrapper[T]` is the guard; the guard was
  never the problem. The node's recorded type said `Wrapper[T]` while the emitted scala had
  `Wrapper[Object]`, so the comparison the guard feeds had already answered "these agree". Found by
  a trace at the argument level after the guard change measured nothing (`CLAUDE.md` §4.6);
  `ENGINE-LIMITS.md` G21, *A RAW result read through an ERASED RECEIVER must be TYPED as what it
  emits*.
- **Gating the static type-parameter scope at `classDef`'s `enclosingAcc` measured 0 change.** The
  reading was right — an anonymous class in a static initialiser must capture no type parameters —
  and the site was wrong twice over: `anonClass` does not go through `classDef` at all, and the
  `inStatic` flag it would have consulted is reset by `execDef` for every instance method of the
  anonymous body. The scope has to live in the FRAME; `ENGINE-LIMITS.md` G20, *A STATIC member sees
  NONE of its class's type parameters*.

### 10.8 Remaining


- **27 of 44 types rest on compilation alone** (§10.5). Every effect class needs a GL context; the
  cheapest real step is a headless `GL20` implementation in `vfx-core/src/test`, which is a large
  hand-written file rather than a missing test.
- **The two dropped file-leading licence blocks** (`ENGINE-LIMITS.md` V3, *Spoon attaches only ONE
  of several consecutive FILE-LEADING comment blocks*) are a frontend harvest away, and the fix is
  worth taking the next time a library's SECOND block carries different text.
- **`gdx-vfx/gwt` stays unported** while no GWT/Scala.js backend exists in the corpus. If one ever
  does, the §10.3 body substitution is the entry that has to be revisited — it is where the branch
  went.

---

## 11. Publishability — what sge and ssg need before they can depend on this

**The goal being evaluated.** sge and ssg stop hand-maintaining their ports and instead depend on
Baltic Porter as a published library, feeding it Java sources plus per-library configuration — with
their porting work maintained by **agents in those repositories**, without this repository's context
(`CLAUDE.md` §4.45).

The findings below come from an adversarial `porting-auditor` review at commit `8fea564` (2026-07-29),
with each item's state re-verified against the working tree.

| # | finding | state |
|---|---|---|
| 1.1 | two engines, each with half of what a consumer needs (BIR vs TIR) | **partly** — the BIR path's operational machinery (action cache with early cutoff, determinism by double-translation, `SbtGen` wiring, provenance) runs on TIR, and BIR is **explicitly frozen** with headers naming its dependents. But **9 corpus programs are still on BIR** and moving them means re-porting three libraries. One framework by declaration, two by deployment |
| 1.2 | the consumer API was "copy a 253-line file", by explicit instruction | **shipped** — `PortRun` is the single entry point; a migration program is configuration only, and `RequiredChecks` makes a forgotten check fail the run rather than shrink the report |
| 1.3 | injected runtime must be a published artifact — a CORRECTNESS requirement | **shipped** — `balticporter-runtime` is a published module and `SbtGen` adds the dependency from `RuntimePlan`; source emission survives as the explicit `Vendored` fallback |
| 1.4 | nothing is publishable, and a port cannot pin a known-good engine | **shipped** — `publishTo`, `versionScheme := early-semver`, a version from the environment, and `EnginePin` written into the generated build. Still unproven: **no CI publishing, and nothing has ever resolved the published artifact from sge or ssg** |
| 1.5 | package renaming did not exist on TIR — blocking for BOTH repos | **shipped** — `PackageRenameTransform`, run last and verified (`CLAUDE.md` §4.56) |
| 1.6 | `TirEmitter` lost provenance headers — a licence problem | **shipped** — `CLAUDE.md` §4.57 is the rule; every backend carries it |
| 2.1 | the canonical measure script threw the four checks away | **shipped** — every lane prints the full check report, diffed against the baseline |
| 2.2 | check results were stdout-only, truncated, never persisted, never diffed | **shipped** — `findings.tsv` / `counts.tsv` / `report.md` / `diff.txt` / `subject.txt` per run, with a promotable baseline |
| 2.3 | no TIR pretty-printer, no way to run/skip/dump a phase | **shipped** — `TirPrinter` (+ a `canonical` style and `digest`), `DebugEmit` (in `engine`, so it ships), and the five debug flags of `CLAUDE.md` §4.6, each reachable as a `just` recipe and each proven by a spec or by `just debug-selfcheck` |
| 2.4 | the unportable-marker design's Stage 1, plus a forced test-correlation amendment | **shipped** — source map, member digests, scalac correlation and the **test-failure** correlation lane. Stage 2 (the marker itself) deliberately unbuilt; see `DESIGN.md` §6.5 |
| 2.5 | three ad-hoc debugging techniques should become first-class | **shipped** — all three are flags or a printer |
| 3.1 | cross-port composition — blocking at sge's second module | **shipped** — `PortManifest` + `ManifestAgreement` (static and dynamic layers), and beyond the original design, the **port map** (`DESIGN.md` §5): a dependent now reads what its base *emitted*, not only what it *declared* |
| 3.2 | test-framework coverage was JUnit-4-shaped | **mostly** — `@After`, `@Ignore`, `@BeforeClass`/`@AfterClass` and the assertion set are handled (`ENGINE-LIMITS.md` X5). The target side is honestly **(b) with exactly one implemented policy value**: `intercept` and the curried `test(name){body}` shape are MUnit facts baked into the phase |
| 3.3 | incremental TIR runs; unmatched-policy-key reporting; `CollectionsTransform.typeMap` as a parameter | **two of three** — the action cache moved to TIR, and `PolicyReport` now reports a key that never fired (it found a real dead entry in libGDX's manifest the first time it was called). `typeMap` is still a private table, not a (b) parameter, so a divergent *mapping* is invisible to `ManifestAgreement` |

### 6.1 What is still NOT done, stated plainly

1. **Nine corpus programs on the frozen BIR path.** Until they move, "the framework" is two frameworks.
2. **No end-to-end proof that a generated port resolves the published runtime.** `SbtGen` writes the
   right dependency line; nothing has resolved it. That wants an sbt scripted test.
3. **"Adding a library does not mean editing this repository" is unproven from here.** What this
   repository could close is closed — nothing mechanical remains to copy and no check can be forgotten.
   Proving the rest needs the published artifacts consumed *from* sge or ssg.
4. **`ManifestAgreement` cannot see a parameterised phase's CONFIGURATION** unless it declares a
   fingerprint. `ClassTableTransform`, `StaticForwarderTransform` and `PortMapTransform` opt in;
   `CollectionsTransform` cannot until its `typeMap` becomes a parameter.
5. **No second libGDX MODULE.** Ashley is a genuine dependent (§3), but no *extension of libGDX itself*
   — gdx-ai, vis-ui — has been ported, and no diamond (two bases sharing a third) has been built, so map
   composition is untested.
6. **Nothing verifies two ports were built by the same ENGINE at the manifest level.** `EnginePin` is
   wired into the port map's freshness answer, not into `ManifestAgreement`.

### 6.2 What the audit found SOUND — clean verdicts, not courtesy

`Substitutions`' overload-precise `owner#m(P1,P2)` keys; `ClassTableTransform` and
`StaticForwarderTransform` as correct (b) mechanisms (searched for smuggled libGDX knowledge, none
found); `corpus/libgdx-overrides/**` as correct (c) content in the right place — the model for
what a *consumer repo's* `src/` holds; `PrimitiveToOpaqueTransform` (then `IntToOpaqueTransform`) as
the canonical (c) *policy* carried by a shareable (b) *mechanism*; `RewriteTrace`'s impact/check pair (blast radius *before* a rewrite — judged
unique to this codebase); the stale-emit abort in every measure lane; and `Phase` / `Pipeline` /
`StandardTraversal` as the best-documented surface in the repository.

One latent edge worth a guard when a second library configures it: `StaticForwarderTransform` matches
members by **name only**, so a wrapper whose overloads are not all receiver-first would be rewritten
wrongly. Safe under current policy.

### 6.3 What an agent in another repository would still hit first

Traced as a scenario, and the part that has not changed: **typer errors arrive with no (a)/(b)/(c)
signal at all**, and they are the bulk of a new library's first wall. The checks classify their own
domains well — a portability hit points at configuring `Substitutions`, an orphaned call names the
exact dropped member — and the correlator now locates every error to a member and a Java line. What it
cannot yet say, for an *unmarked* error, is which of the three kinds the fix is. That is exactly what
`DESIGN.md` §6's markers are for, and why Stage 2 is the next thing worth building.

---

### 11.9 The adoption-gap catalog — three-way audit, 2026-07-31

A five-agent comparison of UPSTREAM JAVA vs the HAND PORTS (sge/ssg/lls) vs the ENGINE OUTPUT, over
all nine ported libraries plus the two unported ssg ones. Method: type census through each port's
`port-map.tsv`, adaptation hunt by exhaustive pair-diffs on the small libraries and stratified
12-pair samples on libGDX/flexmark. What follows is the ranked inventory of hand-port DECISIONS the
engine does not reproduce, each with its §1 support path — the work list for closing the gap
between "mechanically faithful" and "what sge/ssg would actually adopt".

**Validated first: what the engine already does better.** Zero unexplained type-level omissions in
any port — every absence has a manifest drop, a decisions.tsv row or a porter note. The engine is
STRICTLY more complete than the hand port in 8 of 9 libraries (libGDX: the ~15-file LZMA stack and
the 9-file Json tree sge never ported; simple-graphs +4 types; noise4j +2; jbump +5 — and jbump's
hand `MathUtils.scala` is actually `Extra.java` under a reused name, with the real 352-line
`MathUtils` unported; anim8 +`FastAPNG`; vfx +4; screens +3 plus the `NestableFrameBuffer`
behaviour). gltf is at parity, 135/135 both sides.

**(a) Engine-generic fixes, actionable now:**
1. **Java `final` single-write fields emit as `var` + placeholder, should be `val`** — 9 confirmed
   in noise4j alone; the ctor-plan already computes the assignment-site data. Companion:
   `scala.compiletime.uninitialized` over `null.asInstanceOf` placeholders.
2. **`FunnelledCtor` needs the hand ports' two other shapes** — default-parameter collapse when
   candidates differ only by defaults; companion `apply` + instance `init*` when a demoted body
   does real work. Would retire C3 (`DroppedSuperCall`, 2 live sites in simple-graphs) rather than
   document it.
3. **§4.55 clash false positive**: a Java `static` factory and an instance field of the same name
   do not collide in Scala (companion vs class); Ashley's `Family` carries 3 unneeded `$field`
   renames.
4. **D4/D5 dependent-funnel drift**: `CtorFunnel.Plans` must read a non-owned base class's primary
   parameters from the base's published port map — fixes 7 of gltf's 8 residual errors.

**(b) Parameterisable phases to build (mechanism engine-side, policy in the manifest/conf):**
1. **Static-global elimination** — `Gdx.*` (556 sites / 100 files upstream) → a context case class
   threaded as `(using Sge)` (611 / 161 in sge). General mechanism (holder FQN + field→service map
   + constructor threading + `Holder.field`→`summon[T].field` rewrite); the field bundle is
   library policy. The single largest seam between engine output and sge adoption, and a BASE-port
   decision every dependent inherits (§1.5) — vfx/screens/gltf cannot fix it locally.
2. **JavaBean property transform** — 3,234 `get*/set*/is*` methods emitted verbatim on libGDX
   alone where sge made properties. Mechanism is general (pair detection + §4.55-style rename +
   call-site rewrite); the hand ports' own conversion rate is ~20% and inconsistent, so it ships
   scoped by `RuleScope`, never blanket.
3. **Interface substitution with member rename** — `Disposable`→`AutoCloseable` +
   `dispose()`→`close()` (31→47 sites): `Substitutions`-shaped drop+inject plus one member-rename
   key. Base-manifest scope.
4. **Annotation-driven nullability** — the 578 `@Null`-annotated libGDX sites (and jspecify
   elsewhere) retyped to a policy-chosen target (`T | Null` or a wrapper). ⚠ NOT the hand ports'
   `lowlevel.Nullable` `given Conversion` idiom — that is a MEASURED dead end (`given Conversion`
   never fires through overloaded calls) and flexmark, its heaviest user (1,356 sites), is
   overload-heavy. Beyond-annotation nullability (sge tracks ~2× the annotated set) needs
   null-flow analysis no phase has: open research, currently manual.
5. **D7 call-site substitution** — the call-level twin of `MethodBodyTransform` (key = resolved
   signature, replacement = expression template). Unlocks gltf's injected Jsoniter codecs (3 dead
   `Json` call sites) and every future replace-the-callee case.
6. **Collection-map extensions** — retarget entries like `gdx Array<Integer>` → `ArrayBuffer[Int]`
   with wrapper unboxing (base-manifest); `java.util.Comparator`→`Ordering` + a
   `Collections.sort`→`sortInPlace` call-site table.
7. **Rename-map extensions** — per-TYPE renames (`liqp.filters.Map`→`MapFilter`), sub-package
   restructuring and nested-type flattening knobs (simple-graphs' `internal`,
   `Connection.DirectedConnection`→top level).

**(c) Library rules, permanent injects, skip-then-patch (correctly not mechanized):**
- **Opaque types** — ~30 in sge core (GL handles, GL enums, `Pixels`/`Seconds`/unit types), zero
  emitted. The (b) mechanism exists (`PrimitiveToOpaqueTransform`); the knowledge is per-library
  config nobody has written yet. Extensions inherit the base's (§1.5). The ssg ports and the four
  small sge ports have ZERO opaque types — do not invent config there.
- **Permanent injects with no Java source**: gltf's 2,268-line Jsoniter codecs (plus (b)5 to reach
  them); liqp's ANTLR-replacement parser trio (2,166 LOC — the generated Java was never committed,
  so these are injects FOREVER, with one pinned behavioural divergence: parse-time vs render-time
  unknown-filter errors, which any engine port must consciously re-decide); ssg's `DataView` and
  `lls.Nullable` shared infra.
- **Taxonomy/design redesigns**: `SgeError` sealed-enum error taxonomy, vfx's `PrioritizedArray`
  de-pooling (one decision that transitively deletes its only two callee types), noise4j's
  hierarchy flattening, jbump's 4-into-1 `keySort` collapse. No detectable trigger; hand-patch
  after a mechanical port when wanted.

**Non-gaps, recorded so they are not re-derived**: Scala-3 `enum` syntax (cannot express constant
bodies the class encoding handles — T8/T10/T11/T13); bean names as cosmetics (CLAUDE.md §6's
beautification backend); `for`→`while` (hand and engine independently agree); sge's own SAM→function
collapse is inconsistent with itself and ssg keeps all 183 SAM traits; the typed-GL/`GLEnum` layer
is hand-authored ecosystem infrastructure with no Java source to transform.

**Bookkeeping from the audit**: anim8's decisions.tsv snapshot in §7 predates T13 (235 vs the
current 565 `RetypedSignature`) — refresh the prose next time that section is touched.

### 11.10 D2 — member identity and `PolicyBinder`, as measured

DESIGN.md §8.1, landed as five commits with a full thirteen-lane measurement between each. What the
numbers said, since none of it is visible in a count afterwards:

| | measured |
|---|---|
| D2.1 `Symbol.descriptor` | **0 members changed, `findings.tsv` byte-identical on all 13 ports** — the separate-field decision's whole justification, confirmed rather than argued |
| D2.2 `MemberIndex` + required `Program.members` | same, on 28 migrated construction sites |
| D2.3 binder BESIDE every matcher | **`policy-binding` 0 on all 13 lanes, and 0 CONTRADICTIONS anywhere** |
| D2.4 phases consume bindings | 0 members changed; `policy` findings identical to D2.3 |
| D2.5 per-port key fixes | **EMPTY** — no corpus key changed meaning |

**§8.1 predicted a `policy` RISE from newly-visible `ExternalOnly` and there was none.** The one
phase that takes a `RuleScope` already filtered through `Program.owned` and already reported an
external-only entry with WHY; the binder reproduces its answer rather than adding to it. The rise
that DID appear was twice, and both times the binder was wrong:

- **gdx-vfx `policy 0 → 1`** — `VfxGLUtils#<clinit>` refused as `SyntheticTarget`. The refusal is
  structural ("the frontend walked this owner and did not record this executable") and initialiser
  blocks were not in the `MemberIndex`, so a hand-written Java `static { }` block was reported as
  engine-created. Fixed by indexing them; the index also holds a LIST per key, since a class with
  two static blocks has two members at one identity.
- **libgdx-screenmanager `policy 0 → 10`** — every guacamole redirect refused as `ExternalOnly`.
  `TypeRedirectTransform` is the one seam whose subject is a type this module does not declare and
  cannot declare. It binds `Ownership.Either`; every other seam keeps `Owned`.

Both were FALSE STATEMENTS in a findings file, neither moved a member digest, and nothing else in
the pipeline could have seen either. That is the `policy-binding` check's entire return, and it was
deleted with the matchers it measured (a check that can only ever report zero is the `ENGINE-LIMITS`
P2 shape).

**What the source-level lint found on its first run**: six string tests in the transform package
outside the allow-list, five of them engine-owned identity (`scala.<op>#`, the JUnit vocabulary, an
`OpaqueSpec`'s primitive underlying) and one a genuine §4.56 shape —
`fullName.startsWith("org.hamcrest")`, which covers `org.hamcrestic`. Fixed rather than exempted: no
corpus package is named that, which is exactly why nothing would ever have reported it.

**Still open, and named rather than implied.** `PortMapTransform.preciseKey` and `PortMap.erase` are
the two ends of one join in the EMITTED namespace and did NOT move (see `ENGINE-LIMITS.md` D1 and
DESIGN.md §8.1). Moving both together re-publishes every `port-map.tsv` and is its own measured
commit; until then D1's 8-finding arity residue stands, scoped to that join.

### 11.11 M2 — `OverrideGraph`, `MemberRenamer`, `bean-properties`, as measured

DESIGN.md §8.5, landed as three commits. The mechanism is DEFAULT-OFF: no port declares a
`bean-properties` phase, so **every lane is 0 members changed and all 202 check counts across 13
ports are identical**. What follows is the DRY RUN — the harvested policy bound against libGDX core
in a throwaway process, with nothing enabled — because the mechanism's real numbers are otherwise
invisible until Stage P4 turns it on, and P4 should not be the first time anyone sees them.

**The harvest binds. 144 entries, 0 typos.**

| | |
|---|---|
| entries declared (R5 §4's block, de-duplicated) | **144** |
| accessor keys that did NOT bind (`NeverMatched`/`Malformed`) | **0** |
| properties APPLIED | **127** |
| properties REFUSED, each counted with its cause | **17** |
| declarations moved (`RenamedMember`, `Configured`) | **267** |

267 against 127 properties is the closure doing its job: the average property moves 2.1
declarations, and `Drawable`-shaped entries move one per implementor plus one per anonymous body.
This is the first large `Configured` rename population in the project; the previous largest was
libGDX core's 827 `RenamedMember` rows, all `Universal`.

*(R5's prose says "145 properties"; its own block yields 144 distinct keys. The block is what was
bound — the discrepancy is in the brief's count, not in the policy.)*

**The 17 refusals, all of them correct, none of them silent.**

| cause | n | what it is |
|---|---|---|
| external anchor | 12 | `Selection`/`VertexAttributes`/`TiledMapTileSet`/`OrientedBoundingBox` implement `java.lang.Iterable`, `Comparable` or `Serializable`, which the frontend never parses. Pure over-approximation — see `ENGINE-LIMITS.md` K12, which is where the number and the fix live |
| no nilary getter | 3 | `VertexAttributes#getOffset(int)`, `Polygon#getVertex(int,Vector2)`, `Polygon#getCentroid(Vector2)`. The harvest names a member that takes arguments; a property has none. **The phase refused rather than inventing a nilary twin**, which is the "NEVER INVENT A MEMBER" rule firing on real policy for the first time |
| collision the emitter will not move | 2 | `ScrollPane#scrollX`/`scrollY` — the target name is already taken by something the §4.55 passes do not relocate |

The three `no nilary getter` entries are a POLICY defect, not an engine one: whoever owns P4 either
drops them or names the accessor the hand port actually converted. That is exactly the report the
`PolicyIssue` channel exists to produce.

**The `$field` residue, which is what gates `TrivialAccessorCollapse`.**

Renaming a getter to `x` lands it on the private field's name, and the emitter's
`resolveMemberClashes` resolves that by moving the FIELD (the `DeferToEmitter` contract). So the
`$field` population grows by exactly the trivial pairs a `var` collapse would delete:

| | before | after |
|---|---|---|
| emitter field-vs-method renames (one per DECLARATION) | 176 | **278** |
| `var …$field` lines in emitted text | 197 | **299** |
| emitted characters | 7,228,838 | 7,346,577 |

**+102 declarations.** That is the number DESIGN.md §8.5 defers the collapse behind, and it is not
large: 102 fields against 127 properties says most of the harvested pairs are not trivial (a
computed getter or a side-effecting setter keeps its field either way). The collapse is worth doing
and it is worth doing SECOND, on its own measurement.

## 12. Remaining work, across the engine

Maintained by deletion. Items are ordered by what they block, not by size.

### 7.1 Provenance coverage — decisions that are not yet recorded

- **`TestFrameworkTransform`'s synthesised `beforeAll`/`afterAll` record no decision.** They are
  definitions with no Java behind them, which is precisely the case a reader cannot explain from the
  line itself.
- **Raw-generic `[?]` rendering and `uncheckedGeneric` retyping are unrecorded.** Both change a
  signature for a reason no reader can recover; recording them needs the decision log threaded through
  the frontend, which today records only from phases and the run.
- **`RetypedSignature`, `RedirectedCall` and `FunnelledCtor` carry no porter note.** The argument for
  each is in `DESIGN.md` §7.2 and stands; it is listed here so that adding one is a decision rather
  than an oversight.
- **A package-private java TYPE emits as a public Scala one, and nothing records it.**
  `WidenedVisibility` covers members widened by the constructor replay; a `class X` with no modifier
  becoming `class X` in Scala is a surface difference no decision, note or check sees. anim8 has two
  (`ConstantData`, `ChunkBuffer`, §7.8) and libGDX will have many more.

### 7.2 Control flow

- **`labelSeq` is program-global**, so a control-flow diff is never file-local: emitting one new
  boundary shifts every subsequent label name. Nothing is wrong with the output; the *diff* is
  unreadable, which is a measurement cost (`CLAUDE.md` §5).

### 7.3 Counted residues that are not defects

- **Trivia 100 / 69 / 1 / 1** (libGDX core, libGDX tests, simple-graphs main and test; Ashley 0),
  classified in `ENGINE-LIMITS.md` §10 — a comment on a construct the emission consumes has nowhere to
  go.
- **libGDX omissions 177**, dominated by the promoted-constructor-body shape. The targeted refusal for
  the `Material`/`Table` remainder was measured at **0 → 35 `E120`** and refused (`ENGINE-LIMITS.md` C7)
  — do not re-derive it.
- **2 collection-closure findings on libGDX**, both `java.util.concurrent`: portability decides those
  first.
- **`Collectors.toSet` / `toMap` deliberately unmapped** (`ENGINE-LIMITS.md` K6): each needs a different
  target type, and both a copy and the identity compile while being wrong.

### 7.4 Cosmetic

- Drop notes print `key=` twice.

### 7.5 Not run

- **The Auditor has not run over this delivery.** It is expensive (Fable 5) and the **user** runs it,
  once a whole piece of work is delivered (`CLAUDE.md` §4).
