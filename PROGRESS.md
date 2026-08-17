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
just liqp-measure         # liqp + its own 105-file suite (§10.5)
just md-measure           # flexmark-java core + the eleven util modules — no test set IN SCOPE (§10.6)
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
baseline, not a filtered selection of it.

**The compile-error count is baselined too** (`baseline/expected-errors`, one line per lane, written
by the run and promoted by `just baseline-accept`), and a mismatch fails the lane in EITHER
direction. Until that gate shipped it was the only number nothing compared — which is how a 0 -> 3
regression walked through `measure-all` reporting success (`ENGINE-LIMITS.md` M9). The mechanism they share is `scripts/_lib.sh`; the policy
(sbt projects, upstream trees, dependency coordinates) is variables at the top of the `Justfile`.

Measurements below are from one serial run of all lanes, 2026-07-31.

---

## 1. Corpus inventory

Eleven libraries are ported on the current (TIR) pipeline, across sixteen runs — a library and its
own test suite are two ports, and the suite is a *dependent* of the library:

**A port's name is its DESTINATION module's** (`CLAUDE.md` §2.1) — the id of the module in the
reference port (`../sge/build.sbt`, `../ssg/build.sbt`) that this output is going to become. The
directory, the `label` and the `PortManifest.name` all carry it, and it is what a dependent writes
in `forBases(…)`. **Every port was renamed at once, and MEASUREMENT RECORDS BELOW KEEP THE NAME THE
MODULE HAD WHEN THE NUMBER WAS TAKEN** — a row reading `libgdx-core scalac errors` is `sge`'s, from
before the rename. What did NOT move is `port-report/<X>/`, which is keyed on the migrator class:
`LibgdxCoreMigrate` is still where `sge`'s baseline lives, so every historical diff still resolves.

| upstream library | port, before | port, now |
|---|---|---|
| libGDX core / its suite | `libgdx-core` / `libgdx-test` | `sge` / `sge-test` |
| Ashley / its suite | `ashley` / `ashley-test` | `sge-ecs` / `sge-ecs-test` |
| gdx-gltf / its suite | `gltf` / `gltf-test` | `sge-gltf` / `sge-gltf-test` |
| anim8-gdx | `anim8` | `sge-anim8` |
| gdx-vfx | `vfx` | `sge-vfx` |
| libgdx-screenmanager | `screens` | `sge-screens` |
| jbump | `jbump` | `sge-jbump` |
| noise4j | `noise4j` | `sge-noise` |
| simple-graphs / its suite | `simple-graphs` / `simple-graphs-test` | `sge-graphs` / `sge-graphs-test` |
| liqp / its suite | `liqp` / `liqp-test` | `ssg-liquid` / `ssg-liquid-test` |
| flexmark-java | — (added after the rename) | `ssg-md` |

| port | upstream | files in / out | tests | compile |
|---|---|---|---|---|
| `sge` | libGDX `gdx/src` | 604 in → **598 out** (11 dropped, 6 injected) | — | **0** |
| `sge-test` | libGDX `gdx/test` | 29 → **29** | **221**, 217 pass / 4 expected-fail | **0** |
| `sge-ecs` | Ashley `ashley/src` | 21 → **21** (2 injected) | — | **0** |
| `sge-ecs-test` | Ashley `ashley/tests` | 18 → **18** | **112**, 108 pass / 2 fail / 2 skipped | **0** |
| `sge-anim8` | anim8-gdx `src/main/java` | 16 → **16** (0 dropped, 0 injected) | **23** hand-written, all passing — upstream has NO suite (§7.1) | **0** |
| `sge-gltf` | gdx-gltf `gltf/src` | 135 → **135** (0 dropped, 1 injected) | — | **3** (§8.4, all classified) |
| `sge-gltf-test` | gdx-gltf `gltf/test` | 1 of 7 → **1** (§8.1) | **8** ported + **22** hand-written, **none run** — the port does not compile | — |
| `sge-screens` | libgdx-screenmanager `src/main/java` | 22 → **22** (0 dropped, 0 injected) | **16** hand-written, all passing — upstream's 12 need an unported BACKEND (§9 libgdx-screenmanager) | **0** |
| `sge-vfx` | gdx-vfx `core/src` + `effects/src` | 44 → **44** (0 dropped, 0 injected) | **64** hand-written, all passing — upstream has NO test SOURCE SET (§10.1) | **0** |
| `sge-graphs` | simple-graphs `src/main` | 29 → **33** | — | **0** |
| `sge-graphs-test` | simple-graphs `src/test` | 7 → **7** | **16**, all passing | **0** |
| `sge-noise` | noise4j `src` | 12 → **12** | **none upstream** (§5) | **2** |
| `sge-jbump` | jbump `jbump/src` | 19 → **23** | **none upstream** — gated by a differential probe instead, §6.2 | **0** |
| `ssg-liquid` | liqp `src/main/java` | 135 → **139** (0 dropped, 4 injected) | — | **0** |
| `ssg-liquid-test` | liqp `src/test/java` | 105 → **105** (nothing excluded since T9 closed, §10.5.4) | **637** emitted, **637 run — 636 passing, 1 failing, expected 1 / unexpected 0** (§10.5.4's classification: T16 took the three jackson ones; the last is K18's counted refusal, DECLARED expected by maintainer decision 2026-08-14 — `Map.Entry` stays `Tuple2` and an entry-IMPLEMENTING class is unsupported, scala's custom-comparison idiom being an `Ordering`; `baseline/expected-failures.tsv` carries it, and the test still runs so a pass would be reported as news) | **0** |
| `ssg-md` | flexmark-java `flexmark` + 11 `flexmark-util-*` | 458 → **468** (0 dropped, 0 injected; 486 in scope, 28 declaration-only) | its suite is a THIRTEENTH module (`flexmark-util`) and a second lane — **723** emitted, **0 lost**, not yet run (§10.6.6) | **19** main + **0** test (243 at first emit; §10.6.3, all classified) |

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
| `ssg-md` | flexmark-java 0.64.8 | BSD-2 | 872 / 79,825 covered (1,091 / 106,170 on disk) | 767 / 77,291 | 761 mapped 1:1; **28 undocumented omissions** | 178 / 25,274 |
| `ssg-liquid` | liqp 0.9.2.4-SNAPSHOT | MIT | 135 / 9,542 | 130 / 10,925 | 124 of 135; **11 absent** | 65 / 1,030 |

Package renames the engine needs: `com.vladsch.flexmark.* → ssg.md.*` (one uniform prefix replace
with exactly one deviation, `util.builder → util.build`) and `liqp.* → ssg.liquid.*`. **The "43 Maven
modules collapse into one namespace" framing this row used to carry overstates the risk to nothing**:
every module already declares under the single root `com.vladsch.flexmark`, so the module split is a
`pom.xml` fact and the map is injective by construction — verified collision-free over all 53
modules, 116 distinct packages in, 116 out (§10.6.1).

The other ssg modules (`ssg-js`, `ssg-katex`, `ssg-mermaid`, `ssg-sass`, `ssg-minify`,
`ssg-graphs-commons`) *are* source-level ports — of JavaScript, TypeScript, Dart and Ruby, which the
Spoon frontend cannot read. `ssg-highlight` is an FFI/WASM binding. `ssg-graphviz`, `ssg-commons`,
`ssg-data-commons` and `ssg-site` are original Scala. Corroborated independently: not one of them
contains a single `Ported from:` header.

Size, stated correctly: flexmark is **1.4× libGDX core by file count but 0.54× by lines** (872 covered
files / 79,825 LOC against 605 / 147,163; 1,091 / 106,170 counting every module on disk). Which is
"bigger" depends on whether the engine's cost is per file or per construct. The `912 / 83,680` this
paragraph used to quote reconciles with no on-disk total and came from a status doc — the tree is the
oracle (§10.6.1).

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
| `ssg-md` | **The green test numbers do not measure CommonMark conformance.** All 7 spec files in `src/test/resources` are loaded by no runner — and upstream ships four `FullOrigSpec*CoreTest` classes that drive them through PLAIN `@Test` methods, i.e. inside the engine's mechanical reach as they stand (§10.6.1). The **28** (not 24) undocumented omissions include the entire `util/html/ui` subpackage, eight `*JiraRenderer`, and `flexmark-util-dependency`'s `Flat*` extension-resolution algorithm, which is not dead code. |

#### Attribution gaps to close before publishing anything

- **sge**: TextraTypist's MIT `typing-label.LICENSE` is unacknowledged; `sge-tools` and `sge-freetype`
  are absent from `THIRD-PARTY-LICENSES`.
- **ssg**: `NOTICE` names flexmark only — not liqp (MIT), terser, KaTeX, Mermaid, dart-sass, rough.js
  or the Ruby gems. Per-file headers exist, so this is arguably compliant, but the NOTICE is incomplete.

Baltic Porter emits provenance headers, so a re-port fixes the per-file half automatically — and
since liqp it also SHIPS the upstream notice files a port declares (`provenance { notices = […] }`,
copied into `src_managed/`), which is the half a banner cannot meet for a library with no per-file
headers at all: MIT's one condition is inclusion, and liqp carries zero headers, so the emitted
`Original license: MIT (see liqp upstream)` was a pointer and nothing else (CLAUDE.md §4.57). The
repository-level NOTICE / THIRD-PARTY files are still hand-maintained and are not.

### 1.2 Suggested assignment order

1. (Tier 1 is complete: `sge-ecs`, `sge-graphs`, `sge-noise` and `sge-jbump`'s upstreams are
   all ported by the engine — §3, §4, §5 and §6.)
2. **`sge-gltf`, `sge-vfx`** — mid-size, high coverage, few surprises. (`sge-anim8`'s and
3. **`ssg-liquid`** — small Java surface, but resolve the ANTLR decision first. Its 105 upstream test
   files are the best available proving ground for test porting after libGDX.
4. **`sge-ai`, `sge-visui`, `sge-textra`, `sge-colorful`** — large, each with a named redesign that must
   be scoped as its own decision.
5. **`ssg-md` (flexmark)** — the largest Java surface. Milestone 1 (core + the eleven `flexmark-util-*`
   modules) is now ported by the engine and stands at a measured, classified wall: §10.6. The rename
   turned out to be the easy half — one uniform prefix with one deviation, collision-free. What
   remains is the 29 extension modules as DEPENDENT ports, and the tests, which milestone 1 has none
   of.
6. **`sge` core** — already ported by the engine. What remains is the 100 absent types and the backend
   question, which is a platform decision, not a port.
7. **Deferred / not port work**: `sge-controllers`, `sge-tools`, `sge-physics*`, `sge-freetype`,
   `sge-jvm-platform-*`.

---

## 2. libGDX — the spearhead port

`com.badlogic.gdx.* → sge.*`, Apache-2.0. Two ports: `sge` over `gdx/src`, and `sge-test`
over `gdx/test` as a **dependent** of it, inheriting its manifest.

### 2.1 Measured state

| gate | `sge` | `sge-test` |
|---|---|---|
| compile errors (scala-cli, Scala 3.8.4) | **0** | **0** |
| files emitted | **598** (12 dropped, 7 injected) | **29** |
| model | 605 units / 52,453 symbols | 634 units / 53,612 symbols |
| signature consistency | 0 | 0 |
| omissions | **66** | 3 |
| portability (all / emitted / injected) | 151 / 151 / 2 | 166 / 15 / 0 |
| remediation suggestions | 29 | 2 |
| substitutions (emitted / dangling) | 0 / 0 | 0 / 0 |
| manifest agreement · port map · policy | 0 · 0 · 0 | 0 · 0 · 0 |
| collection closure / boundary | **2** / 0 | 0 / 0 |
| context seams (boundary + warning) | **44** = 19 + 25 (§11.12) | **1** × `self-supplied` |
| trivia lost / recovered / deliberate | **0** / 4 / 12 | **0** / 0 / 0 |
| porter notes uncovered | **0** | **0** |
| break residue (untranslated jumps) | **0** | **0** |
| source map | 594 units / 19,288 members | 623 units / 19,547 members |
| members changed vs baseline | **0** | **0** — the 2,570 + 23 that moved in D3's designed re-baseline are accounted below |
| decisions recorded | **3,893** | **284** (the base's withheld — D2) |
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

`decisions.tsv` by kind, `libgdx-core`: 1,258 `RenamedMember`, 1,246 `RetypedSignature`, 606
`RenamedPackage`, 337 `WidenedVisibility`, 292 `FunnelledCtor`, 53 `ScopedOut`, 30
`DroppedSuperCall`, 25 `DroppedMember`, 21 `RedirectedCall`, 12 `DroppedType`, 11 `InjectedMember`,
**2 `DeferredInit`** — 3,893 rows. 286 of them are the globals policy's (§11.12): 275
`RetypedSignature` — 188 classes and 87 methods, across 177 java files — 9 `DroppedMember` for the
holder statics that lost their last reader, and the 2 `DeferredInit`.

#### The visibility mapping (D3) — what shipped, and the residue by cause

`DESIGN.md` §8.7 landed as one designed re-baseline: **2,570 of 19,288 member digests moved**,
against a prediction of ≈1,970 from the modifier census — the excess is the porter notes the new
residue carries plus every promoted constructor that gained a modifier. **Compile errors 0 → 0**,
all seventeen check counts identical, `tests 217 passing / 4 failing` unmoved.

| what the port emits now | count |
|---|---|
| `private[<pkg>]` — java package-private, and java `private` inside a nested type | **1,409** |
| `protected[<pkg>]` — java `protected`, package half restored | **849** |
| of which `protected[sge]` (a common-ancestor qualifier, or the root package's own) | 39 |
| RESIDUE — `WidenedVisibility` rows | **142** |
| — `protected-static` (P8: a companion object is nobody's supertype) | 99 |
| — `x-pkg-protected-override` (the nearest common enclosing package) | 28 |
| — `ctor-replay-widening` (pre-existing, now carrying the same `cause=` pair) | 15 |
| — `qualifier-shadowed` · `unnameable-package` · `x-pkg-pkg-private-override` | 0 · 0 · 0 |

Two numbers are worth more than their size. The **28** cross-package protected overrides are where
the census predicted **10**: the other 18 are ANONYMOUS classes (`new Pool<T>() { protected T
newObject() … }`), which are `Tree.New` nodes rather than `ClassDef`s and which a walk over class
bodies cannot see — measured as 14 `E164 has weaker access privileges` errors before the override
graph learned to reach them. And the **99** `protected-static` rows against a census of 71 are the
nested `protected static` TYPES, which move to the companion for the same reason its members do.

The three zeroes are the guards, and each was asserted rather than assumed: no enclosing type in
this library is named like its package tail, nothing is in the default package, and the Java
non-override shadow (a package-private method re-declared in a different-package subclass) is **0
sites over 9,346 method declarations** — cheap insurance, not a live cost.

**Across the corpus**, from the one serial `just measure-all` that promoted the baselines. Note what
did NOT move: **`findings.tsv` and `counts.tsv` are byte-identical for all thirteen ports**, and so
is every `tests.tsv` — the promotion is `members.tsv` (emitted text) and `port-map.tsv` (the
published surface, which now carries visibility) and nothing else.

| port | errors | members moved | `protected-static` | `x-pkg-protected-override` | `ctor-replay` |
|---|---:|---:|---:|---:|---:|
| libgdx-core | 0 | 2,570 | 99 | 28 | 15 |
| libgdx-test | 0 (217 pass / 4 fail) | 23 | — | — | — |
| ashley | 0 | 62 | — | 5 | — |
| ashley-test | 0 (108 / 2 / 2 skipped) | 97 | — | — | 5 |
| anim8 | 0 (23 / 23) | 128 | 11 | — | — |
| gltf | **7** | 151 | 2 | 19 | — |
| gltf-test | 0 | 0 | — | — | — |
| vfx | 0 (64 / 64) | 62 | — | 4 | — |
| screens | 0 (16 / 16) | 34 | — | — | — |
| simple-graphs | 0 (16 / 16) | 129 | — | — | — |
| sg-test | 0 | 8 | — | — | — |
| noise4j | 2 (pre-existing, §5.4) | 57 | 2 | 2 | — |
| jbump | 0, differential probe **IDENTICAL** | 31 | — | — | — |

**192 residual rows corpus-wide, of which 20 are the pre-existing `ctor-replay-widening`** — so the
mapping's own residue is 172 across 24,000+ declarations. Zero `qualifier-shadowed`, zero
`unnameable-package` and zero `x-pkg-pkg-private-override` in any port: all three guards are
insurance that has never had to fire.

**The one prediction that did not hold, and what it actually means.** The plan priced gdx-gltf at
**−1 error** for T12's retirement. Measured, gdx-gltf is **7 → 7**: the ambiguity was never IN the
residue, because the hand-written body substitution was suppressing it, so deleting the workaround
costs nothing rather than saving one. Verified both ways in this change — with the
`MethodBodyTransform` entry restored the port still reports 7, and with it deleted no `E051 Ambiguous
overload` appears anywhere (against `ENGINE-LIMITS.md` T12's own measurement that without the mapping
it does). What the port really gains is the divergence from upstream: one `SubstitutedBody` row, 4 →
**3**.

#### The constructor funnel after A2 — what a SYNTHESISED primary bought, in numbers

The funnel no longer PROMOTES a java constructor wherever every root reaches ONE parent constructor,
the implicit `super()` included; it synthesises a `protected` primary and every java constructor
stays a `def this`. Measured on this lane, against the pre-A2 baseline:

| | before | after |
|---|---|---|
| omissions | 177 | **67** |
| — promoted body runs on every path (`ENGINE-LIMITS.md` C7) | 140 | **31** |
| — `super(args)` dropped (C3) | 30 | **30**, and the set is the baseline's MINUS `DistanceFieldFontCache` (C8's own worked example, repaired by the marker) |
| — annotation dropped | 6 | 6 |
| compile errors | 0 | **0** |
| classes carrying a marker disambiguator | — | **6** |
| field slots hoisted | — | **53 across 31 classes**, of which **5 bind as `val`** and 48 stay `var` |
| fields REFUSED a slot, with the reason recorded | — | **166 across 64 classes: 146 `order`, 15 `interleaved`, 5 `no-default`** |

**A1's residue is a PLACEHOLDER, and the placeholder replaces the CAST and nothing else.** A field the
funnel could not hoist keeps a `var … = <blank>`, and `<blank>` is `scala.compiletime.uninitialized`
exactly where the alternative was `null.asInstanceOf[T]` — a cast in a position where nothing is
being cast. Every type that STATES a default keeps stating it: `0`/`false` for a primitive, and
`null` for the nullability phase's `T | scala.Null`, which is the very cast that phase exists to
retire. Keyed on "field with no initialiser" instead, the substitution silently took that union
default back off, and only `NullabilitySpec` asserting BOTH halves of its rule caught it. libGDX
core: 1,184 placeholders, against 2,466 under the unkeyed version.

**The 30 dropped `super(args)` are all WALLS — the COLLAPSE contributes none of them, and that is a
measurement, not an assumption.** A collapse promotes a root whose parameters ARE the parent's
formals, and its siblings' `super(args)` used to go through the type-matched fill, which declines on
a type mismatch that means nothing there (`super(s, 7)` against a promoted `(Object, int)`): both
arguments discarded, compiling. The delegation is positional for a collapse now (`Plan.collapse`,
`DESIGN.md` §8.2 as-built 5), and the corpus delta is **0 on every one of the 13 lanes — 0 members
changed, 0 counts moved**. The residue is unaffected because every one of the 30 is a class whose
roots reach DIFFERENT parent constructors (`DistanceFieldFont` 7, `OrderedSet`/`OrderedMap`/
`IdentityMap` 3 each, the three `RegionInfluencer` bodies 3 each, `Button` 2, four singles), which
is the wall the synthesis refuses by design and no delegation can express. The defect was real and
the corpus does not exercise it: it was found by reading the spec that pinned it as correct, which
is the only instrument that could have.

**The order-safety rule is the whole residue.** 146 of 166 refusals are `reason=order` — the value
was not composed of the constructor's own parameters, literals and operators, so hoisting it into a
delegation argument list would evaluate it before `super(...)` and before the instance initialisers
where java evaluated it after both. R1's census predicted 129 non-hoistable CLASSES corpus-wide; on
libGDX core alone 64 synthesised classes carry at least one refusal, so the order rule grows that set
substantially rather than marginally. A purity allow-list is the obvious next lever and is not built:
the number says it would be worth designing, which is exactly what the measurement was for.

**And the funnel now says all of that BESIDE THE CODE.** `FunnelledCtor` was excluded from
`PorterNote.Rendered` on the argument that "one primary and N secondaries IS the funnel, in the
code" — true of a PROMOTION, false of a SYNTHESIS, which is what the table above made the normal
case: a reader in another repository (§4.45) is looking at a `protected` constructor no java
declared, with `sup$k`/`f$name` slots and possibly a `Funnel` marker parameter, and there is no
upstream line for the source map to point at. The note is `AtDeclaration` on the class and carries
the `shape`/`slots`/`notSlot`/`disambiguator`/`escapes` detail the decision already had. Per port
(= `FunnelledCtor` rows, one note each): **libgdx-core 292, gltf 16, vfx 15, anim8 10, jbump 10,
screens 11, ashley 8, simple-graphs 7, ashley-test 6, noise4j 4, libgdx-test 1, gltf-test 0,
simple-graphs-test 0 — 380 corpus-wide.** libGDX core's emitted notes go 906 → 1,198. Blast radius,
accounted in full: **610 member digests on libgdx-core = 2 × (292 decided classes + 13 ENCLOSING
units of a decided nested type)**, and every other port is exactly 2 × its own row count in the same
way. `porter-notes` stays 0 both ways on all thirteen ports, `substitution(dangling)` stays 0
(the note names the UPSTREAM FQN on purpose and `SubstitutionCheck.withoutPorterNotes` strips it),
every check count is unchanged and every lane still compiles and runs as it did.

**What still escapes, 31 paths.** Wall classes (roots reaching different parent overloads —
`FloatAction`/`IntAction`, 3 each), JDK-throwable parents, and the UNIQUE-ROOT class whose paramful
promotion the C1 fixpoint withholds (`ObjectMap`, `ObjectSet`, `OrderedMap`, `OrderedSet`, 3 each —
one root, so the synthesis's two-root condition excludes it). `Material` — one of C7's three
observable divergences — is repaired outright; `Button` (5) and `Table` (1) are wall classes and
remain.

**The COLLAPSE no longer keeps an escape the marker would not.** `DESIGN.md` §8.2 orders collapse
before the marker so the ~100 measured collisions come out byte-for-byte unchanged — but a collapse
PROMOTES a real constructor, so its body becomes the class body and C7 applies to it again. The
refinement this section recorded as "one predicate, not built" is built: **collapse only where the
promotion has NO escaping path.** It is asked through `CtorFunnel.escapesOf` — the same function
`OmissionCheck.promotedBodyOnEveryPath` counts with, prefix strip included — rather than through a
second predicate written at the nomination, so what the funnel believes a promotion costs and what
the check reports cannot diverge (C7's `droppedSuperArgs` failure, in its other form). Where the
collapse is declined the class falls through to the marker, which synthesises and promotes nothing.

Measured, one predicate, thirteen ports:

| lane | omissions | what moved |
|---|---|---|
| noise4j | **3 → 0** | `Object2dArray` — three roots reaching one `Array2D(int, int)`, one a pure pass-through, whose `this.array = getArray(width * height)` ran on all three paths. That was the port's ENTIRE residue; noise4j is now 0 findings. |
| libgdx-core | **67 → 65** | `Dialog` — NOT predicted. Its promoted `initialize()` ran on two construction paths java never ran it on, so this is a behavioural repair of the `Button`/`Table` kind rather than a bookkeeping one. |
| the other eleven | unchanged | the collapse still fires wherever it did — the escaping promotion is the rare case, not the common one |

Cost: **5 member digests on libgdx-core, 8 on noise4j, 0 on the other eleven**; compile unchanged
everywhere (libgdx 0, gltf 7, noise4j 2); every other check count identical; every suite unchanged.
Two classes gained a companion `Funnel` — the price the ordering was refusing to pay for a
divergence it had already counted.

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

**Omissions, 67**, and the composition is the table above. What used to dominate — `promoted
constructor body runs on every path`, 140 of 177 — is now 31, because the funnel synthesises a
`protected` primary instead of promoting a java constructor wherever every root reaches one parent
constructor. Refusing the promotion had been measured twice and refused twice (blanket **0 → 41**,
targeted **0 → 35 `E120`**); the answer was not to refuse it but to stop needing it. The prefix strip
that took omissions 193 → 177 still applies, on the wall.

**The remaining `super(args) dropped` set is exactly the pre-A2 one minus `DistanceFieldFontCache`.**
A pre-pipeline census put libGDX core at 144 escapes and 31 dropped
`super(args)`; the lane said 140 and 31, and generalising the synthesis to "all roots reach one
parent constructor" moved the DROPPED-SUPER count by zero — those 30 break down as
`DistanceFieldFont` 7 (seven roots to seven `BitmapFont` overloads — irreducible), `OrderedSet` /
`OrderedMap` / `IdentityMap` / three `RegionInfluencer` nests 3 each, `Button` 2, and three singles,
every one reaching DIFFERENT parent constructors. What the widening was worth is elsewhere: the 109
escaping paths above, and the soundness test in `ENGINE-LIMITS.md` C8 — without which it emitted an
infinitely self-delegating constructor. The 31st, `DistanceFieldFontCache`, is C8's own worked
example and the marker disambiguator repaired it.

**The 66th omission is `BitmapFont()`, and it was SILENT until it was counted** (`omissions`
**65 → 66**, 0 members moved, compile 0 → 0, suite 217/4 unchanged). `TirEmitter.orderBody` drops
every nilary java constructor in front of scala's implicit nilary primary; for
`BitmapFont() { this(classpath("lsans-15.fnt"), classpath("lsans-15.png"), false, true); }` that
drop loses the default 15pt face, so `new BitmapFont()` built a font with no data, no page and no
glyph. Instrumenting the predicate over all thirteen ports found **exactly one site**, this one. The
three ways to keep the delegation each emit a WRONG answer rather than a missing one and are priced
in `ENGINE-LIMITS.md` C11; the outcome is refuse-and-count, and the published contract stopped
listing `()` among `BitmapFont`'s `secondaries=` in the same commit.

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
collection shims are vendored by libGDX core and compiling `ported/sge-ecs` alone measures nothing.

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
| trivia lost / recovered / deliberate | **0** / 0 / 0 | **0** / 0 / 0 |
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
| `PortMapTransform.forBases("sge")` — **LAST** | it reads what the base actually emitted and reports a reference the base does not ship, so it must run after the seams that re-point those references. Run first it reported 7 findings, every one repaired by the two phases above; run last it reports what an agent must act on |

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
| trivia lost / recovered / deliberate | **0** / 0 / 0 | **0** / 0 / 0 |
| porter notes uncovered · break residue | 0 · 0 | 0 · 0 |
| source map | — | 36 units / 508 members |
| members changed vs baseline | 0 | **0** |
| decisions recorded | **125** | **26** |
| **tests** | — | **16 of 16 PASSING** (7 files) |

**No §1(c) rules.** simple-graphs needed **zero** library-specific rules: its manifest is a namespace
claim, two universal phases and a package rename. That is the outcome the corpus procedure aims for.

**And therefore the CONFIG front door's acceptance proof.** Because its whole policy is data, this
port is driven from `balticporter/corpus/ports/simplegraphs/{main,test}.conf` (DESIGN.md §5.7) rather than from a
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

### 4.2b Open residue — two `ExternalCallee` rows the emitted text does not have

`collection-boundary` reports `Path#removeAll`/`retainAll` (Path.java:103, 109) as
`Found balticporter.runtime.JavaCollection / Required java.util.Collection`. **Both rows describe a
seam the port does not emit**, and the compile is the proof: were the callee really
`java.util.Collection#removeAll`, handing it a `JavaCollection` would be an error, and this port is
at 0. What happened is that `Array` — `Path`'s parent — was itself retyped to extend the shim, so the
emitted `super.removeAll(c)` binds to `JavaCollection#removeAll`, whose formal IS `JavaCollection`.
The finding is minted from the CALLEE SYMBOL the frontend interned against java's own
`Collection`, which is right for a call whose receiver stayed java's and describes nothing here.

The check already knows how to ask this — `CollectionBoundaryCheck.foreign` excludes a callee whose
owner the mapping moved — but that question is asked only on the `Side.Universal` arm, deliberately
("the existing `expectedExternal` classification is untouched"). Widening it is a change to a
classification two ports depend on, so it is recorded rather than done: the number is 2 of this
port's 3 `collection-boundary` rows, at 0 errors and 16 of 16 tests passing.

**It is not an `accept-external-callee` candidate** (`DESIGN.md` §8.16). A remedy would move both rows
into `remediation(resolved)` and read as a port's judgement about a third party's method, when what
is actually true is that the engine's own retyping already closed the slot — `CLAUDE.md` §1's rule
that an obligation the engine's translation created is not a port's to discharge, and §4.56's
K2.5 lesson read one lane over: a residue count is only as good as the assumption that a reported
seam is one nothing already closed.

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

### 4.45 The corpus's FIRST per-location remedy selections — the drain, measured

simple-graphs is the first port to write `resolutions` (`DESIGN.md` §8.16), and it carries one
selection per remedy on the two lanes that gained a menu first. It was chosen for the demonstration
because it is the smallest port that holds all three shapes, and because its 16 tests RUN — an
emission-affecting remedy whose only evidence is a green compile is evidence of nothing (§3).

| key | remedy | why THIS site |
|---|---|---|
| `Graph#addVertices(V[])` | `acknowledge` | the body reads each element AT `V` and hands it to `addVertex(V)` — no store, no write into the array, no cast. And java gave the author **nowhere to put the assertion**: JLS 9.6.4.7 allows `@SafeVarargs` only on a static, final or private method, and this is a public non-final instance method of a public abstract class |
| `Array#remove(Object)` | `ascribe-javac-choice` | `remove(indexOf(item))` beside `T remove(int)` — `CLAUDE.md` §4.4's `remove` shape asked of a library's own declarations. `indexOf` returns `int`, so javac bound `remove(int)` in phase 1 while `remove(Object)` is applicable only after boxing, and the two members do entirely different things |
| `Node#removeEdge(Node)` | `accept-risk` | TWO calls of that same pair, with a `Connection<V>` argument that converts to `int` in neither language — so both compilers can only bind `remove(Object)` and the rows are the lane's declared over-approximation. One key, both sites: §8.16's broadcast, measured |

**The drain, in one baseline diff:** `heap-pollution 1 -> 0`, `overload-risk 6 -> 3`,
`remediation 1 -> 5` — four rows left the two refusal lanes and four arrived as
`remediation(resolved)`, which is the arithmetic §5 requires and the only reading under which those
two lanes falling is an improvement rather than a check that stopped asking. **0 errors before and
after, 16 of 16 tests still passing**, and the `members.tsv` blast is **6**: the three selected
declarations and the three whole-class digests that contain them, every one attributable to a
`Decision` row. The published `port-map.tsv` policy digest moved on BOTH modules, which is the field
being shared surface — a base whose selections moved cannot look fresh to a dependent.

The emitted ascription is `(this.remove: (scala.Int) => T)(this.indexOf(item))` — a method-value
ascription, which is what pins the overload, verified compiling under 3.8.4 and named by a porter
note beside the `def`.

**Three overload-risk rows are deliberately LEFT.** Two are `super.remove(…)` in `Path`, where the
candidate set is the SUPERCLASS's and the check leaves the root at the callee's own owner (T17); one
is `MinimumWeightSpanningTree`'s `addVertices(graph.getVertices())`, a fixed-arity candidate beside a
vararg one where the argument is a `Collection` and only one candidate is applicable at all. A port
that selected them would be reporting that somebody looked at sites nobody had.

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
Apache-2.0. The whole port is `balticporter/corpus/ports/jbump/main.conf`; `JbumpMigrate` is a three-line `main`.

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

So the port's gate is `balticporter/corpus/ports/jbump/probe/{Probe.scala,ProbeJava.java}`: **the same scenario
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
`ported/sge-anim8` alone measures nothing.

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
`ported/sge-anim8/src/test/scala` holds **23 hand-written MUnit tests** — the only thing in that module a
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
| context seams | **5**, every one an `unconstructed-thread` WARNING — anim8's five PNG writers, whose users construct them (§11.12) |
| trivia lost / recovered / deliberate | **0** / 0 / 0 |
| porter notes uncovered · break residue | 0 · **0** |
| source map | 16 units / 568 members |
| decisions recorded | 705 rows about anim8's own declarations (623 `RetypedSignature`, 16 `RenamedPackage`); the base's are withheld, which `ENGINE-LIMITS.md` D2 says is where they belong |
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
| T15 | a RECEIVER is an operand — a CONDITIONAL receiver's call landed INSIDE one branch | **0 errors — `writeBigPalette(null)` wrote nothing at all** |

**T15 is T10's shape again, and it was found from ANOTHER LIBRARY.** `writeBigPalette` is
`(filename == null ? Gdx.files.local(…) : filename).writeString(…)`; emitted without parentheses the
`writeString` bound to the `else` branch, both branches are a `FileHandle` so it COMPILED, and the
method wrote the file only when the caller passed a handle. 0 errors, every check count flat, 23
tests passing, for the life of this port — the two liqp errors that exposed the rule are in a
different library, and this port moved 2 members on the fix.

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
surface        = List(PortMapTransform.forBases("sge")),
```

Everything else — `dropTypes`, `dropMethods`, libGDX's `com.badlogic.gdx -> sge` rename and all six
surface phases — is INHERITED, and `ManifestAgreement` reports 0 on every run. Three details worth
keeping:

- **the rename is ADDITIVE, not a restatement.** Longest-prefix-wins keeps `com.badlogic.gdx -> sge`
  and `com.github.tommyettinger.anim8 -> sge.anim8` apart, so the dependent adds its own namespace
  without touching the base's.
- **`PortMapTransform.forBases("sge")` goes LAST**, for the reason `AshleyPolicy` states: it
  reads what the base actually EMITTED and reports a reference the base does not ship, so it must run
  after any seam that re-points such a reference. It reports **0** — anim8 touches none of libGDX's
  dropped types.
- **`inject` is empty.** anim8 ships no replacement file: libGDX core ships the replacements for the
  types *it* dropped, and §1.5's asymmetry means a dependent must not copy them.

**The conf door cannot express this port today.** `base = "…"` in a `.conf` resolves another CONF,
and there is no `balticporter/corpus/ports/libgdx/main.conf` — `LibgdxPolicy` is Scala, because `ClassTableTransform`,
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
| `trivia` — dropped comments | **0** | closed | Was 34 (`PaletteReducer` 17, `AnimatedPNG` 12, `FastPalette` 3, `OtherMath` 1, `FastPNG` 1) — all COMMENTED-OUT CODE at the END of a method body, which is `Tree.Block.trailing`'s category exactly. anim8 is the port where D4t's second mechanism took a residue to zero on its own, with nothing recovered and nothing deliberate. |

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
`ported/sge-gltf/src/test/scala` adds **22 hand-written MUnit tests** over `GLTFTypes` — the file where the
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
| compile errors (with libGDX core, Scala 3.8.4) | **3** | — (one invocation) |
| files emitted | **135** (0 dropped, 1 injected) | **1** |
| model | 740 units / 56,368 symbols | 741 / 56,420 |
| signature consistency | 1 | 1 (the same site) |
| omissions | **12** | 0 |
| portability (all / emitted / injected) | 151 / **0** / **0** | 151 / 0 / 0 |
| substitutions · manifest · port map · policy | 0 · 0 · **0** · 0 | 0 · 0 · 0 · 0 |
| collection closure · boundary · shared-iterator | 0 · 0 · 0 | 0 · 0 · 0 |
| context seams | **7**, every one an `unconstructed-thread` WARNING (§11.12) | 0 |
| trivia lost / recovered / deliberate | **0** / 4 / 0 | **0** / 0 / 0 |
| porter notes uncovered · break residue | 0 · **0** | 0 · 0 |
| source map | 135 units / 1,523 members | 1 / 9 |
| decisions recorded | 2,065 rows about gdx-gltf's own declarations (1,767 `RetypedSignature`); the base's withheld (D2) | 47 |
| **tests** | 8 ported + 22 hand-written = **30, NONE RUN** — the port does not compile (§8.4) | |

**Error trajectory: 19 → 16 → 14 → 9 → 8 → 7 → 3**, on 135 files at the first attempt. `break residue` is
**0** and `portability(emitted)` is **0** on a library full of `switch`-driven enum mapping, which is
§9.5's control-flow work and the portability rules paying off on a library they were not built for.

`trivia` is `0 lost / 4 recovered / 0 deliberate` (was 10 lost). The 4 that the backstop relocates
are the comments INSIDE the two bodies `MethodBodyTransform` replaces ("call X via reflection to
avoid compilation error with GWT") plus two of the same shape: the code they describe is
deliberately not in the port, so they have no statement left to sit above and are put back after
their member with a `/* trivia: recovered … */` line naming the java they came from. Expect any
`MethodBodyTransform` entry to move its body's comments into the `recovered` lane — that is the
honest behaviour rather than a defect, and it is a candidate for an honest home (the substituted
body could carry them) if the lane ever grows.

### 8.3 What this library taught the engine — three (a) fixes and a fourth found by reading

| key | the gap | cost |
|---|---|---|
| — | `<clinit>` reached an `export` selector list; `export P.{<clinit> => _, *}` is an XML start tag to dotty | 3 errors |
| `ENGINE-LIMITS.md` D6 | an all-static java class is still a TYPE, and the object collapse had no third guard | 5 errors |
| `ENGINE-LIMITS.md` D6.5 | a DROP and its INJECTION are in different namespaces, so `Substituted` had never been produced by a renaming port | **10 false findings** |
| `ENGINE-LIMITS.md` T12 | java `protected` is dropped, and accessibility is an input to OVERLOAD RESOLUTION | 1 error — CLOSED by `DESIGN.md` §8.7; the `MethodBodyTransform` workaround is deleted and this port's `SubstitutedBody` rows are 4 → **3** |

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

**And the D5 refusal NARROWED three members on `ashley-test`, which is the same fix seen from the
other side.** A replay that reached an `sge-ecs` `private` member had been accepted, and everything
it touched was widened with it — including three of the TEST port's own `private static` fields
(`IntervalSystemTest.deltaTime`, `IntervalIteratingTest.deltaTime`,
`SortedIteratingSystemTest.comparator`), which were emitted with java's `private` dropped for no
reason anybody could state. With the replay refused they are `private` again, matching java. 6 member
digests, `vis=private` on three port-map rows, every check count identical and the suite unchanged at
108 / 2 / 2.

### 8.4 The residue — 3 errors, all classified, and why the tests cannot run

The port does not compile, so **none of its 30 tests has ever executed.** `CLAUDE.md` §3 is explicit
that a test which cannot run is not a test that passed, and the lane says so rather than reporting a
suite of zero.

| errors | site | classification |
|---|---|---|
| **2** | `PBRCubemapAttribute`, `PBRTextureAttribute` — `extends CubemapAttribute` / `extends TextureAttribute` with no arguments | **C3**, (a) engine — see below |
| ~~4~~ **0** | ~~`ModelInstanceHack` — `this.copyNodes(…)`, `private` in libGDX's emitted `ModelInstance`~~ | ~~**D5**~~ CLOSED — the replay is refused and counted |
| 1 | `MeshLoader.java:252` — `vertexAttributes.toArray(VertexAttribute.class)`, a member the base drops | **D7**, (b) a phase that does not exist |

**D5 CLOSED, 7 -> 3, and `omissions` 3 -> 12 is the price it names.** `CtorFunnel.Plans` asks the
SURFACE — not its own symbol table — whether a replay may reach a base's `private` member, and
refuses where the base published it `private`. `ModelInstanceHack`'s two `super(args)` are then
DROPPED rather than replayed into a call that cannot compile, which is M6/C3's answer applied
unchanged, and `run-latest/report.md` carries the one gap that says so:
`[base: sge]  [§1(a) ENGINE, in the BASE]`.

**Seven of the nine new omissions were ALWAYS TRUE and nothing was reporting them.** `OmissionCheck`
and both decision recorders built their own `CtorFunnel.Plans` with no surface — a `TrivialSurface`,
under which the funnel's fixpoint spans the base — so they answered over a DIFFERENT plan from the
one the emitter used. `PBRCubemapAttribute` and `PBRTextureAttribute` had been emitting a bare
`this()` for seven `super(args)` while the check called them expressed and the port map's decisions
agreed with the check. The emitted CODE for those seven is byte-identical; what appeared is nine
`dropped-super-call` porter notes that should always have been there. 12 member digests moved, all
in those three classes (`ENGINE-LIMITS.md` D5).

**The remaining 2 are NOT D4, and the base-surface contract is what proves it — 7 -> 7.** With
schema 3's `primary=` rows published and the funnel's fixpoint scoped to this run's own classes
(`DESIGN.md` §8.3), gdx-gltf reports **0 unanswered contract questions**: every base class's plan
this run derives now agrees with the row libGDX core published, cross-checked, and 9 member digests
moved to match. The two errors survive that unchanged, and the row says precisely why:
`CubemapAttribute` is published `primary=(long) primaryKind=unique-root primaryVis=public` with
`(long,TextureDescriptor)` and `(long,Cubemap)` among its `secondaries`, and both of
`PBRCubemapAttribute`'s roots call exactly those two. **A Scala `extends` clause reaches only the
PRIMARY**, and §8.2's synthesis is inadmissible for this class because its roots reach *different*
parent constructors. The contract therefore **confirms the wall rather than removing it** — the
honest outcome is a counted refusal (C3/M6), not a seeded plan, and it must not be re-attempted as a
seeding problem. This is §8.3's honest-scope statement arriving at a real site.

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
  blanket refusal regresses the base to fix the dependent. The guard that landed is a SCOPE, not a
  removal: the within-module widening is untouched and `BaseSurfaceSpec` pins both directions.
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
- **D7 — the MECHANISM exists; what is left is one policy decision.**
  `CallSiteSubstitutionTransform` (DESIGN.md §8.12) is the call-level twin of `MethodBodyTransform`,
  keyed like `dropMethods` and overload-exact. Dry-run against this port's three dead `Json` sites:
  **3/3 bound, 3 sites rewritten, 0 policy findings**, at `SeparatedDataFileResolver.java:30`
  (`fromJson(Class,FileHandle)`), `BinaryDataFileResolver.java:97` (`fromJson(Class,String)`) and
  `GLTFExporter.java:241` (`prettyPrint(Object)`). It is NOT enabled: the replacement has to name a
  codec, and this port has not made the codec decision (see the last bullet). Enabling it against a
  codec that does not exist would trade three INERT calls for three that do not COMPILE.
  `MeshLoader.java:252`'s `Array#toArray(Class)` — the original 1-error case — is reachable by the
  same mechanism and wants the same decision.
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
| hand-written support sources (`src/main/scala`) | **9 files, 503 lines** — the guacamole replacements; four of them carry the context the base threads (§11.12) |
| model | 627 units / 52,867 symbols |
| signature consistency · omissions | 0 · **0** |
| portability (all / emitted / injected) | 151 / **0** / 0 |
| substitutions (emitted / dangling) · manifest · port map · policy · remediation | 0 · 0 · 0 · 0 · 0 · 0 |
| collection closure · boundary · shared-iterator | 0 · 0 · 0 |
| context seams | **10**, every one an `unconstructed-thread` WARNING — the ten transitions, whose users construct them (§11.12) |
| trivia lost / recovered / deliberate | **0** / 1 / 0 |
| porter notes uncovered · break residue | 0 · **0** |
| source map | 22 units / 175 members; port map 37 types / 169 members |
| decisions recorded | 186 rows (`RetypedSignature` 70, `RenamedMember` 52, `RenamedPackage` 22, `DroppedMember` 16, `DroppedType` 12, `FunnelledCtor` 11, `ScopedOut` 3); the base's withheld (`ENGINE-LIMITS` D2) |
| **tests** | **16 of 16 PASSING** (hand-written; upstream's 12 are §9.4) |

**`omissions` and `portability(emitted)` are both 0**, which no other dependent port has managed —
the 151 portability sites are libGDX core's own, seen through the resolution root, and identical to
what `just gdx-measure` reports.

**Error trajectory.** Two numbers, because two different things were being counted:

- **guacamole references the emitted Scala could not resolve: 26 → 0**, closed by the engine fix in
  §9.5. Measured by `grep -o 'de\.damios[A-Za-z0-9_.]*' ported/sge-screens/src_managed`, NOT by the
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
`ported/sge-screens/src/main/scala` supplies. That is the whole of this port's library-specific policy:
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

So the behavioural gate is **16 hand-written MUnit tests** in `ported/sge-screens/src/test/scala`, adapted
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

- ~~7 trivia residues~~ **0 lost, 1 recovered.** Six of the seven were `// don't do anything by
  default` / `// do nothing` line comments that ARE an empty body, which is `Tree.Block.trailing`'s
  category exactly and is now placed where java wrote it; the seventh is relocated by the backstop
  with its coordinates (D4t).
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
`ported/sge-vfx` alone measures nothing.

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
hand-written MUnit tests** under `ported/sge-vfx/src/test/scala` (§10.5). `just vfx-measure` prints the
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
| context seams | **20** — 16 `unconstructed-thread` WARNINGS, 3 `residual-global-read` and 1 `deferred-init` (§11.12) |
| trivia lost / recovered / deliberate | **0** / 2 / 0 |
| porter notes uncovered · break residue | 0 · **0** |
| source map | 44 units / 910 members |
| decisions recorded | 416 rows about gdx-vfx's own declarations (261 `RetypedSignature`, 57 `RenamedMember`, 44 `RenamedPackage`, 16 `DroppedMember`, 15 `FunnelledCtor`, 12 `DroppedType`, 4 `WidenedVisibility`, 3 `SubstitutedBody`, 2 `ScopedOut`, 1 `RedirectedCall`, **1 `DeferredInit`**); the base's withheld, per `ENGINE-LIMITS.md` D2 |
| **tests** | **64 of 64 PASSING** (6 files, hand-written) |

**Error trajectory: 11 → 10 → 7 → 6 → 5 → 4 → 1 → 0.** One §1(b) policy step and SEVEN §1(a)
engine fixes, one commit and one measurement each — six of the seven moved the error count and the
seventh moved `porter-notes` instead, which is the only gate that could see it. `portability(emitted)` is **0**: the 151 are every one
in libGDX's own files, which D2's ownership filter keeps out of this port's emitted column.

### 10.3 The policy decisions — three §1(b) body substitutions and one context extension

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
tracking upstream. `port-map 2 → 0`, and it is the first of the port's two `SubstitutedBody`
decisions.

**A SECOND substitution joined it when the base's globals policy landed, and the pair is one
decision.** `DefaultVfxGlExtension` reads `Gdx.gl`, so it is one of the classes the base threads and
constructing one now takes a context — which a `static { }` block has no way to hold. The class
initialiser therefore becomes `{ }` and the construction moves, behind a null guard, into
`VfxFrameBuffer#getBoundFboHandle`, the nearest caller whose enclosing class carries the clause.
That is the reference port's own shape (`initExtension()(using Sge)` called from
`getBoundFboHandle()(using Sge)`), landing one member further out because a body substitution may
change what a member DOES and never what it TAKES — the emitted `VfxGLUtils.getBoundFboHandle()`
reads no holder, so nothing threaded it, and no manifest key can. The boundary is COUNTED either
way: `VfxGLUtils#<clinit>` reports `unsuppliable use: this declaration uses DefaultVfxGlExtension,
which now takes a context`.

**A THIRD substitution followed, because `VfxGLUtils.getBoundFboHandle()` is PUBLIC API.** The null
guard above covers the port's own only reader; it does not cover upstream's own entry point.
`public static int getBoundFboHandle()` and the `public static VfxGlExtension glExtension` field
beside it are gdx-vfx's surface, and a consumer — sge, an effect written against this library,
anything outside the port — may call the first without ever having touched a `VfxFrameBuffer`. In
JAVA that always worked, because the class initialiser had run by definition; with `<clinit>` emptied
it is a bare NPE at a line whose text says nothing about why.

The member cannot initialise itself: constructing a `DefaultVfxGlExtension` takes the threaded
`sge.Sge`, and this is a `static` with no clause and no caller to take one from — the same boundary
that moved the initialisation out of `<clinit>`, and exactly why the guard sits one member further
out on `VfxFrameBuffer`. What it CAN do is fail informatively, so it throws an
`IllegalStateException` naming the initialisation path (bind a `VfxFrameBuffer`, or assign
`VfxGLUtils.glExtension` yourself). This is residue the reference hand port does not carry:
`initExtension()(using Sge)` is a hand-written member that takes the clause, and a generated one
cannot be edited to.

Blast, accounted: **2 members** — the substituted `def` and its enclosing object's digest — plus this
module's own `policy=` fingerprint (`93e61bca → 3d3364cc`, and gdx-vfx is a leaf, so no dependent
reads it). Every check count and all 64 tests unchanged; nine other ports 0 members.

**RESIDUE: the throw is not exercised by a test.** The hand-written suite is value-level and holds no
GL context, `glExtension` is a global `var`, and a suite that asserted the null branch would depend
on nothing else having initialised it — order-coupled state in a suite that has none today.
The `if` is not evidence of behaviour; CLAUDE.md §3 applies and this is the one place in the port
where it is unmet.

**And the one `ContextHolderExtension` — `ENGINE-LIMITS.md` CT8's mechanism in production.**
`VfxFrameBuffer#tmpCam` is a `private static final OrthographicCamera` initialised at class
initialisation, and `OrthographicCamera` is threaded; a `sites` `lazy-init` entry moves it to first
READ and turns a scalac error into a counted `deferred-init` seam plus a `DeferredInit` decision with
a porter note. It is a per-declaration key about gdx-vfx's OWN type, contributed as an extension with
no field in which the base's shared surface could be restated (§1.5). `VfxGLUtils#<clinit>`
deliberately gets no such key — it READS the holder rather than initialising a static from a threaded
construction, and `lazy-init` there was measured as a `never matched` policy finding.

**No §1(c) rule, no new phase parameter, no injected source.** The rest of the manifest is a
namespace claim, one package-rename pair, the base's inherited surface, and the per-declaration half
above.

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
stops at the GL line rather than building a ~150-method `GL20` no-op.

**Since §11.12 landed, that boundary is ENFORCED rather than described.** `VfxFrameBuffer` now takes
`(using sge.Sge)`, so the suite declares one — `sge.SgeTestFixture.testSge()`, whose every service is
ABSENT rather than a noop. A test that started to cross the GL line therefore fails with a
`NullPointerException` at the exact field, where a stub would have answered and let it pass while
asserting nothing. That is the whole argument for the fixture's shape, and this suite is where it
does visible work.

**Not covered at all**: the 21 effect classes and `VfxManager`/`VfxRenderContext`, whose every
method is a shader draw. That is 27 of 44 types resting on compilation alone.

### 10.6 Residues, named and classified

- **omissions 2.** `ShaderVfxEffect`: one `@SuppressWarnings` dropped — a source-retention java
  annotation with no scala meaning, correctly not emitted and correctly counted (T6). And
  `PrioritizedArray`: one C7 promoted-constructor path — two roots, neither delegating, so the
  nilary body (`items = new ValueArrayMap<>()`) also runs on the capacity path and is immediately
  overwritten. Declared COST, not divergence, and `PrioritizedArraySuite`'s capacity test is what
  says the observable behaviour is identical.
- **trivia 11 → 0 lost, 2 recovered** (D4t). The two file-leading Apache blocks
  (`LensFlareEffect`, `LevelsEffect`) were `ENGINE-LIMITS.md` V3 and are now emitted, both of them,
  above the `package` clause: the file header is harvested by POSITION, so which of a file's
  leading blocks the parser attached where stops mattering. Nine were comments at the end of a body
  — `Tree.Block.trailing`'s category — and seven of those are now placed exactly; the last two are
  relocated by the backstop with their java coordinates.
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
  cheapest real step is a headless `GL20` implementation in `ported/sge-vfx/src/test`, which is a large
  hand-written file rather than a missing test.
- **The two dropped file-leading licence blocks** (`ENGINE-LIMITS.md` V3, *Spoon attaches only ONE
  of several consecutive FILE-LEADING comment blocks*) are a frontend harvest away, and the fix is
  worth taking the next time a library's SECOND block carries different text.
- **`gdx-vfx/gwt` stays unported** while no GWT/Scala.js backend exists in the corpus. If one ever
  does, the §10.3 body substitution is the entry that has to be revisited — it is where the branch
  went.

---

## 10.5 liqp — the first port measured from OUTSIDE the sge/ssg family

**Milestone 1 measures a WALL, not a green port.** 135 java files converted, 139 Scala files
written, 987 members in the source map. `just liqp-measure`.

### 10.5.1 Measured state

| | |
|---|---|
| scalac errors | main source set **126 -> 31 -> 27 -> 8 -> 1 -> 0**; BOTH source sets **90 -> … -> 50 -> 38 -> 26 -> 22 -> 19 -> 14 -> 13 -> 10 -> 3 -> 0** (the collections endgame: K6.5's aliasing view took 12, G23's wildcard `addAll` 4, `asList`'s explicit type argument 5, three unnamed JDK members 3, K5.8's `super` placement 1, and D-liqp-1b's build-step rename the three namespace seams; then the residue wave took **10 -> … -> 3**, one engine change each — K5.8's `this` fallback, K5.7's loop-reachable `setValue`, K5.9's method-reference lowering, K15's external FIELD, K6's unterminated stream, K15's occurrence-not-equality pass-through, and G22's bound pin; and the closing wave took **3 -> 2 -> 1 -> 0** — X2's REFERENCE widening, K5.7's retained-parent member, and D-liqp-7's two dropped tests) |
| `break_residue` | **0** — liqp has loops and switches, and §4.4's control-flow table cost this port nothing |
| `signature` / `trivia`(all three lanes) | **0** on the first run of a 135-file library nothing in the engine was tuned against |
| `jdk-surface` | **19 -> 10 -> 9** — `anyMatch`/`sortNatural`/`ConcurrentHashMap` stopped reading as this port's wall once the tables named them |
| `collection-boundary` (main) | **6 -> 14 -> 13 -> 8 -> 18 -> 14 -> 15** — the residue nothing could count before (K15). It rose when the seam was first counted, fell to 12 when the frontend made the formals readable (two slots BRIDGED, two re-classified from "cannot verify" to what they actually are), rose by the one `InexpressibleParent` refusal K5.7 counts, and fell by five when the OWNED-callee bridge stopped being switched off by a shim this library never names (K2.5). It then ROSE to 18 and fell to 14 in one step, because the aliasing refusal became a TRANSLATION: twelve `Arrays.asList(arr)` sites stopped being untranslated calls the check cannot see and became boundaries it can, four of which the same change closed. The fifteenth is K5.7's other half: the member a RETAINED PARENT declares and the target cannot carry, now emitted as the interface's own documented refusal and counted at the slot. It then fell to **14** when K20's carrier turned the `convertValue(Object, TypeReference)` refusal into a bridge, and rose to **25** when K21 face 1 gave the check its `OpaqueEgress` lane — ELEVEN rows, one per external callee with a `java.lang.Object` formal a value this port may have retyped reaches. That is a REVIEW LIST and not a residue: it is where a port reads its `reflectiveSinks` candidates off, and on its first run it named `JsonGenerator#writeObject`. **It then fell to 24 when that row was finally acted on** — T16's harvest restored the `@JsonSerialize` that routes a retyped map through exactly that call, and the shallow one-level `toJava` it had been getting answered about the wrong object. A review row is worth what it costs to read: this one sat unread for two waves and then decided three tests |
| `catalog(unmechanised)` (every port) | **5 -> 4** when the `EnumMap`/`EnumSet` shims landed and `JS-C42` became `Handled`. The trade is stated rather than absorbed: `catalog(unreached)` rose by 1 on all fifteen and `JS-C42` joined `just catalog-coverage`'s never-reached set as a non-`Open` row, because **no ported module names either type**. That output's own warning — "a row unreached on all fifteen is dead code or an untested rule" — is answered here by the fixture suites the row's `evidence` names (`JavaEnumCollectionsSpec`, `CollectionsEnumOptionalSpec`), which is the third case the warning does not enumerate: a rule with no corpus site and a test |
| `collection-closure` (main) | **8 -> 0** — all eight were `java.util.Stack`, which `liqp/blocks/For.java` uses as its for-loop drop stack and which the mapping had no key for. It is the only `java.util.Stack` in any ported module and it is what made chunk 17's "all six rows are latent, zero corpus uses" premise false. The target is the SHIM (`ENGINE-LIMITS.md` K10.5) and not the stdlib `mutable.Stack`, whose `push` prepends; 4 members moved, the suite stayed **633/4** |
| `omissions` | **6 -> 4 -> 1** — `PlainBigDecimal`'s two `super(args)` are no longer dropped (with the external constructor's signature readable the funnel reaches K5.5's synthesised primary), and `LiquidException`'s three reach a primary synthesised at the JDK throwable's widest overload (C3) |
| tests | 639 `@Test` upstream, **575 emitted until T9 closed and 637 after — 161 -> 357 -> 364 -> 392 -> 552 -> 554 -> 559 -> 567 -> 572 -> 574 of 575, then 631 -> 633 of 637 passing** (§10.5.5, and §10.5.4 for the last two steps: T9's 62 recovered tests revealed five failures nothing had ever run, and K21 face 2's bridge guard took two of them), **then 633 -> 636 when T16's type-annotation harvest landed with the sink it revealed**. What remains is **1**: K18's counted `InexpressibleParent` refusal |
| `bean-exposure` | **0** on the test port — every type is in scope (D-liqp-10 declares `except = []`) and no java class in this suite declares a bean name the phase would have needed. The MAIN port does not run the phase, so it reports nothing: the row exists to make a scope's own refusals visible, not to census a library's public fields |

**The behavioural gate is now the measurement.** Every number above except the last row is a
compile-time one, and §3 is explicit about what that is worth — which this port then demonstrated
twice at scale. First: **409 of the first run's 414 failures were ONE constructor's statement ORDER,
at 0 scalac errors and every check count flat** (`ENGINE-LIMITS.md` C12, now closed). Then, with that
closed, **196 tests flipped to passing, 0 newly failed, and the 218 that then remained are four families the
first census could not see at all** — every one of them behind the same constructor, and every one of
them still at 0 scalac errors with every check count flat. The compile said nothing about any of it.

### 10.5.2 What this library taught the engine

Every entry below is an ENGINE fix, none of them (b) or (c) — which is the useful surprise of
porting a library from outside the family the engine grew up in.

- **`ENGINE-LIMITS.md` K15** — *a retyping phase owes a boundary count at EXTERNAL callees, not
  only at JDK ones*, now also `CLAUDE.md` §1(b). 15 compile errors at one third-party package
  against 0 findings, because the position-blind retyping moved the node's type on BOTH sides. The
  producer half is closed by a live `scala.jdk` wrap. **The measurement underneath it is the
  transferable one: every external member the frontend interned carried `NoType`** — 1157 of them
  here, not one with a `MethodType` — and that is what blocked the consumer half. `SpoonTir` now
  interns an external member WITH its `MethodType` wherever a class file can be read for one
  SCOPE-FREE and in full, so the consumer half is closed too (`toJava`, a live view) and what is
  left is a count for class files the parse cannot resolve. The fix landed a second, unlooked-for
  improvement: with an external constructor's signature readable, `PlainBigDecimal`'s two dropped
  `super(args)` become K5.5's synthesised primary.
- **`ENGINE-LIMITS.md` K6** — `Collectors.toSet`/`toMap` built (java's two-argument `toMap` THROWS
  on a duplicate key; `.toMap` over pairs keeps the last), and the stream collapse's
  wrong-table-lookup bug found: it emitted the SHIM's `asScalaBuffer` on `Buffer` and `Map`
  receivers. **No check could see that one** — the collapse fired, so nothing reported an
  untranslated chain.
- **`ENGINE-LIMITS.md` K6 (`Collections`)** — the `unmodifiable*` family CLOSED, with the rule that
  reopened it: *"scala has no such type" is a claim about the STDLIB, and this engine ships a
  runtime.*
- **`ENGINE-LIMITS.md` K5** — a class that EXTENDS a mapped collection now has its INHERITED calls
  rewritten (the kind comes from the resolved method's declaring type). `super` is refused blanket,
  measured: `super.putAll(m)` rendered `super ++= m`, an E040 SYNTAX error, which is strictly worse
  than the type error it replaced.
- **`ENGINE-LIMITS.md` K6.5** — the aliasing refusal is now a KNOWN construction blocked on one
  frontend fact, with the number (`76 -> 67` if taken accidentally).
- **`ENGINE-LIMITS.md` K5.7** — a class that IMPLEMENTS `Map.Entry`. The mapping is right for every
  USE and impossible as a PARENT (`Tuple2` is final, takes `(_1, _2)`, has no `setValue`), so the
  parent stays java's and the refusal is counted. A second target for the implements-case is a
  second truth about one java type and is refused with its reason.
- **the wildcard `Map[?, ?]`** — java declares `get`/`containsKey`/`remove` over `Object`, scala
  over `K`, and at a wildcard receiver `K` is unnameable and `V` renders as a bare `?` in a TERM
  position. One rewrite, two error families, nine call sites, **18 errors**. K10's rule met at the
  other kind of unnameable key.
- **`? super java.lang.Object` is `Object`** — one filter dropped the `Object` bound from BOTH
  wildcards, which is right for the upper one and destroys the lower one, java having no supertype
  of `Object`. It moved no count and turned `Required: IterableOnce[Nothing & Any]` into
  `Required: IterableOnce[Object]`, which is §4.45's bar.
- **`toArray()`/`toArray(T[])`, `subList`, `putIfAbsent`** — four JDK members whose scala namesakes
  mean something else, each a §4.4 shape with no compile error of its own: an `Object[]` component
  type, a filled-not-allocated argument, a null terminator, a write-through view, and a return
  value that is the PREVIOUS one.
- **`ENGINE-LIMITS.md` L4 (new)** — a Scala KEYWORD as a PACKAGE SEGMENT was never escaped. `esc`
  has had the keyword set from the beginning and every name the emitter renders BY HAND goes
  through it; a `Symbol.fullName` is a PATH and reached the output verbatim at four call sites.
  Five corpus libraries had no keyword segment; jackson ships one (`…core.type.TypeReference`).
  Nothing but scalac can count it and that is the right counting — the failure is a SYNTAX error, so
  the emitted text is not a file and there is no emitted-text check to add.
- **`ENGINE-LIMITS.md` K6.5, third case** — the vararg PACK stops at the program's edge. The loud
  half was 9 errors; the SILENT half was 9 more in the same library, because `Array[Object]`
  conforms to an `Object...` slot and `String.format(fmt, Array[Object](a, b))` passes the whole
  array as one `%s`. **No check can be built for that one**, and the reason is a fact rather than an
  omission: `SpoonTir.descriptorOf` spells `T…` and `T[]` identically BY CONSTRUCTION, so the
  vararg-ness lives only in the class file the frontend read and a tree-level check could do nothing
  but read the frontend's answer back — which `BreakCatchCheck`'s contract forbids.
- **`ENGINE-LIMITS.md` T15 (new, closed)** — a RECEIVER IS AN OPERAND. `(c ? a : b).toString()`
  emitted `if (c) a else b.toString()`, which parses and calls the method on ONE BRANCH. `operand`
  has known the rule since it was written and four receiver positions were not asking it — the
  half-applied form is why nothing found it. 2 errors here, **and one SILENT site in a port measured
  green**: `anim8`'s `writeBigPalette` wrote nothing at all when called with `null`, at 0 errors and
  23 passing tests, for the life of that port. That is the whole of §3 in one member.
- **`ENGINE-LIMITS.md` K5 (extended, closed)** — an inherited collection call with NO RECEIVER
  WRITTEN, which is what java's double-brace initialiser is made of. Inside a NAMED class the
  frontend already supplies `this.`/`Outer.this.`; inside an ANONYMOUS class it does not, so the
  bare `Tree.Ident` never reached the rewrite. 4 errors — and 22 SILENT sites beside them: `add` has
  no scala namesake so it fails to compile, `put` does, so an unclaimed `put(k, v)` compiled with
  scala's `Option` result where java returns the previous value (§4.4). The four errors named one
  member of a family the claim repaired whole.
- **`ENGINE-LIMITS.md` T14 (closed)** — a java STATIC reached through a SUBCLASS name. 20 errors in
  this suite from one upstream idiom (`ZoneOffset.systemDefault()`), and the fix is the frontend
  reading the interned symbol's OWNER instead of re-deriving the receiver from the written name. The
  transferable half is what it exposed on a library nobody was looking at: the FIELD half had been
  shipping since libGDX with a SUPERCLASS-only walk, so a java INTERFACE CONSTANT — `CLAUDE.md`
  §1(a)'s own example of this exact rule — had never once been re-qualified. It compiled anyway
  because the emitter re-exports a parent's companion, which is why nothing found it: 10 libGDX
  members moved on this commit and its error count did not.
- **`ENGINE-LIMITS.md` G2 (extended)** — an INFERENCE VARIABLE is not a raw type and reads like one.
  A diamond's inferred argument has no binder in the reading scope, so the frontend interns a
  MARKER; printed, `?E` names nothing and does not lex. The marker prefix now lives in `api`
  (`Symbol.UnresolvedTypeVarPrefix`) and both sides read it there.
- **`ENGINE-LIMITS.md` K7 (extended)** — an enhanced-for BINDING may be REASSIGNED, which K7's type
  half does not cover. Same java fact `MutableParamsTransform` handles for a parameter, one node
  kind out; the two reasons to re-bind compose into one alias.
- **`ENGINE-LIMITS.md` C3 (closed)** — the JDK-throwable half §4.4 has prescribed since it was
  written. `LiquidException`'s three roots reach three different `super(...)` and not one passes its
  parameters through, so nothing was promoted and **every exception the port threw carried a null
  message and no cause** — 0 compile errors, no count moving, exactly §3's defect class. A primary
  is now SYNTHESISED at the family's widest overload and each root pads into it. Two things the
  build turned on: the overload has to be read off the TARGET CONSTRUCTOR's formals (an argument may
  be a SUBTYPE — `super(createMessage(e), e)` passes a `RecognitionException`, and the head-name fill
  declines it and drops both arguments), and K5.5's throwable fence is NARROWED to "wherever it
  nominates" rather than removed. `omissions` **4 -> 1**, errors 31 -> 31, 8 member digests — all of
  them that one class — and every other corpus port byte-for-byte unchanged.
- **`ENGINE-LIMITS.md` K6.5 (CLOSED, the aliasing half)** — *what was refused was the COPY, never the
  form.* `Arrays.asList(arr)` returns a live view and the runtime helper's `A*` copies, so the
  rewrite refused it and the emitted text kept the JDK name. `JavaCollections.asListView(arr)` is
  java's own answer — reads and WRITES go through to the array, `add`/`remove` throw where java
  throws — and what "blocked" it for two waves was a fact about the ARGUMENT read as a fact about
  the TREE: the frontend's erasure coercion is a `Tree.Typed` and java's own inference is recorded
  on the CALL. **12 sites.** And the aliasing form has THREE node shapes, not two: after K6.5's
  fourth case an external callee's array pass-through is a `Tree.Spread`, which the single-argument
  arm read as an array and passed through, emitting `asListView(arr*)` at every site — this entry's
  own rule about owing every shape of the vararg convention, met one arm down.
- **`ENGINE-LIMITS.md` K6.5 (fifth case)** — java infers a vararg's `T` across ALL the arguments and
  BOXES; at an INFERRED `A` scalac declines the boxing conversion outright, so a heterogeneous
  `asList(98, "97", true, false, null)` reported one mismatch per element. Java's answer is on the
  call and is now written down as the explicit type argument. **5 errors**, and what is left is a
  different fact entirely (G24, below).
- **`ENGINE-LIMITS.md` G23 (new)** — *java's `?` is bounded by `Object`; scala's is bounded by
  `Any`.* `list.addAll(valueList)` off a `List<?>` needs no cast in java and does not type as `++=`
  in scala. Stated at the ONE operation it blocks rather than by widening what `?` renders as, which
  G2 already measured. **4 errors.**
- **`ENGINE-LIMITS.md` K5.8 (new)** — *a `super` receiver is a SYNTAX question, and it is answered of
  the RESULT.* K5's blanket refusal was bought to stop a rewrite putting `super` outside a member
  selection; the emitter now renders `super.++=(m)` (the only legal spelling) and the phase checks
  the property structurally on the term it just built, so a new arm is covered by construction.
- **`ENGINE-LIMITS.md` G24 (new, OPEN)** — *java's `<T>` bound is VACUOUS and the emitted
  `T <: java.lang.Object` is not.* Scala 3 roots `java.io.Serializable` at `Any`, so a
  `Buffer[Buffer[Serializable]]` does not conform to `Buffer[Buffer[T]]`. liqp is the first corpus
  library to write `Serializable` as a type argument, which is why five libraries went past it.
- **three JDK members the collections tables never named** — `Stream.anyMatch`/`allMatch`/`noneMatch`,
  `Collections.sort`'s natural overload at java's own `<T extends Comparable<? super T>>` bound (not
  `Comparable<T>` — transcribing a JDK signature means transcribing its wildcards), and
  `ConcurrentHashMap`, which IS a `java.util.Map` and splits the subtype relation if only the
  interface is mapped. One error each, and each invisible until a compile named it — `JdkSurfaceCheck`
  reads the same tables, so an unlisted member reads as the PORT's wall rather than an engine gap.
- **`ENGINE-LIMITS.md` T11 (closed)** — the DECLARED-collidee half of the promoted enum parameter.
  What blocked it was not visibility but the ROUTE: `enumDef` promotes the parameters without
  consulting `CtorFunnel`, so the §4.55 pass had nothing to place. NARROW where the plan-based arm
  is blanket, because an enum parameter is emitted SURFACE.
- **`ENGINE-LIMITS.md` X2 (extended, closed)** — java's `assertEquals(Object, Object)` has TWO
  widenings and the numeric one hid the reference one for four ports. The transferable half is where
  the trap is: a ROOT operand reads as "the pair already relates" and is the exact opposite — MUnit's
  constraint is already VACUOUS there, and a root is the one answer the TIR cannot be trusted on,
  because an earlier phase's boundary wrap is typed as the FORMAL it was inserted for.
- **`ENGINE-LIMITS.md` K5.7 (extended, closed)** — a RETAINED PARENT is an OBLIGATION, so the
  refusal owed an emission. The lesson beyond this class is the middle row of that entry's table: a
  `dropMethods` on a member an emitted parent declares leaves the class abstract, and NOTHING reports
  it until the port is at 0 typer errors (§3). Now `CLAUDE.md` §1(b).
- **`ENGINE-LIMITS.md` D10 (new, closed)** — `governs` is a NAMESPACE and a TEST SOURCE SET is always
  inside its base's, so the `ExtraDrop` screen made every key such a module writes about its OWN
  members an intrusion — a rule with no way to comply with it. The base's published PORT MAP is what
  §1.5 actually asks for. Six libraries went past it because no corpus port had declared a test-set
  drop before.
- **`ENGINE-LIMITS.md` C12 (new, CLOSED)** — and the one only the RUN could find: a promoted
  constructor local keeps its name (C2/§4.55) and loses its POSITION, so `Template`'s locals
  initialised ahead of the assignments java wrote first. **409 of 414 test failures at 0 scalac
  errors and every check count flat**; closed by giving `TirEmitter.orderBody` the class symbol and
  hoisting only that class's own FIELDS, which is JLS 12.5's own cut and a §4.56 ownership test
  rather than a name. **161/414 -> 357/218 passing on liqp; 29 libGDX classes' emitted text moved and
  every suite in the corpus kept identical outcomes** (§10.5.5).
- **`ENGINE-LIMITS.md` K17 (new, OPEN)** — what C12 was hiding, and the same defect twice: **a java
  CONVERSION emitted as a scala CAST**. A lambda at an external functional-interface slot is cast
  rather than SAM-converted (27 failures), and a `? :` over `Long`/`Double` is cast to java's
  JLS 15.25 promoted type without performing the promotion (7). Both emit valid Scala with the right
  static type, so no compile, no check and no member digest can see either.

### 10.5.3 Remaining, classified

The three families that dominated the 70 are settled, and what settled them was one FRONTEND fact
plus two rules about position:

| was | now |
|---|---|
| a wildcard `Map[?,?]`'s `getOrElse` key and the `null.asInstanceOf[?]` it minted — 18 errors over 9 call sites | **0.** Java declares `get`/`containsKey`/`remove` over `Object`, so the three runtime helpers take the key as `Any` and supply the `null` where `V` is a real parameter (K10's rule at the other kind of unnameable key) |
| `Map.Entry` where a class IMPLEMENTS it — read as M6's answer, an inexpressible parent | **a counted refusal, and 2 fewer errors.** The parent stays JAVA's, because a phase may not emit a parent its target cannot BE; `setValue` remains the deliberate refusal K2 already recorded (K5.7) |
| a retyped collection at a class file's FORMAL | **0.** K15's consumer half, unblocked by `SpoonTir` interning external members with their `MethodType` |

**…and the emitter-and-constructor families that dominated the rest are settled too**, by six
changes none of which is (b) or (c):

| was | now |
|---|---|
| a Scala KEYWORD in an emitted package segment (`com.fasterxml.jackson.core.type`) — 3 errors plus the `MAP_TYPE_REF` E134 that followed from it | **0.** `esc` answered for an IDENTIFIER and a `Symbol.fullName` is a PATH; `escPath` escapes per segment at §4.56's separators (L4, new) |
| an EXTERNAL java vararg pack — 9 errors, **and 9 more that COMPILED** (`String.format(fmt, Array[Object](a, b))` conforms and passes the whole array as one `%s`) | **0.** The pack stops at the program's edge: `Tree.Repeated` for an external callee, flattened in argument position (K6.5, third case) |
| a diamond's INFERRED type argument printed as `?E` — 1 error, and the only E035 4a's wildcard work left | **0.** A marker is not a name; the bound is dropped and `?` is what G2 already settled (G2) |
| an enhanced-for BINDING reassigned in the body | **0.** K7's alias with `var` and no cast — the same fact `MutableParamsTransform` handles for a parameter (K7) |
| a promoted ENUM parameter against a DECLARED method — 2 errors | **0.** T11's remaining half; what blocked it was the ROUTE, not visibility — `enumDef` promotes without `CtorFunnel`, so the §4.55 pass had nothing to place (T11) |
| `PlainBigDecimal`'s dropped `super(args)` | **0**, by K5.5 once the external signature became readable |
| `Insertions.of`/`Filters.of` — `E134 None of the overloaded alternatives`, 5 errors, read as an overload problem | **0 of the five, 3 fewer errors.** It was never overload resolution: liqp names `java.util.Collection` and never names `java.lang.Iterable`, and the pass that bridges a retyped argument into a shim-typed formal opened with `if javaIterableSym == SymId.None then t` — so the whole bridge was inert for this port, with no check, no policy entry and no member digest able to say so (K2.5, new). The remaining two sites were the `Arrays.asList(arr)` aliasing refusal wearing an E134 mask, and now report it |
| `LiquidException` — 0 errors and a SILENT §4.4 defect: three roots, three different `super(...)`, none promotable, so every exception it threw had a null message and no cause | **fixed, and it moves no error count at all**, which is §3 in one line. C3's synthesised primary at the JDK throwable's widest overload; `omissions` 4 -> 1 |

**…and the collections endgame took 38 (27 main + 11 test) to 13 (8 main + 5 test)**, in six
engine changes, none of them (b) or (c) and none of them a port-policy lever:

| was | now |
|---|---|
| `Arrays.asList(arr)`'s aliasing REFUSAL — 11 in the main set, 1 in the test set, kept under the JDK name so the error reads as an untranslated call | **0.** What was refused was the COPY, never the form: `JavaCollections.asListView(arr)` is java's own live, fixed-size view — reads and WRITES go through to the array, `add`/`remove` throw where java throws. The element type that "was gone" is recorded on the CALL, so the erasure coercion comes off (K6.5, closed) |
| `list ++= valueList` where the source is a `Buffer[?]` — 4 | **0.** Java's `?` is bounded by `Object` and scala's by `Any`, so the difference is stated at the ONE operation it blocks rather than by widening what `?` means (G23, new) |
| a heterogeneous `Arrays.asList(98, "97", true, false, null)` — 6 per-element mismatches | **1.** Java infers `T` across all the arguments and boxes; at an INFERRED `A` scalac declines the boxing conversion outright. Java's answer is on the call, so it is written down as the explicit type argument and `Predef.int2Integer` applies. What is left is one aggregate mismatch, below |
| three JDK members the phase's tables never named — `Stream.anyMatch`, `Collections.sort`'s natural overload at java's own `<T extends Comparable<? super T>>` bound, and `ConcurrentHashMap` | **0**, one site each |

**The D-liqp-1 × D-liqp-2 namespace seam is CLOSED by D-liqp-1b — 13 -> 10, three sites, zero member
digests moved.** It was the one residue this port had classified as a decision rather than a bug
(`Template#parse`, `TestUtils`, `LiquidParserTest`, all three `Found: ssg.liquid.TemplateParser
.ErrorMode` against the generated parser's `liqp.` formal), and what unlocked it was noticing what
the parser IS: not a dependency but a BUILD PRODUCT of this port's own build step, regenerated from
the grammars and untracked. A build product may be built against what this build is producing. So
`LiqpClasspath` copies the six generated files, rewrites their references INTO the ported library to
the emitted namespace, and javacs THAT — D-liqp-1 unchanged, the parser still external, still a
later milestone to port through the engine.

Three things it cost, and the second is the one no compile could have found:

- **the §4.56 cut.** `liqp.` is rewritten only where a qualified name STARTS; the parser's OWN
  package `liquid.parser.v4` is one letter away and is untouched, as is anything merely containing
  the string. Ten cases in `LiqpParserRewriteSpec`, which is where that discipline is held;
- **the ENUM CONSTANT ACCESS, which is a §3 defect wearing a build step's clothes.** The port emits
  a java enum as a Scala `sealed abstract class` plus a companion `object` of `case object`s, and
  Scala's static forwarders put `values()`/`valueOf(String)` on the companion CLASS while each
  constant is a static field of the MODULE class. So `ErrorMode.LAX` compiles against ANY java enum
  and is a `NoSuchFieldError` at RUN time — the rename alone would have produced a green compile and
  a suite that could not construct a parser. `ErrorMode.valueOf("LAX")` reaches the forwarder and
  returns the same singleton, so the parser's own `errorMode == ErrorMode.STRICT` reference
  comparison still holds. Verified standalone against real scalac output before it was written;
- **the STUB is shape-honest or it is worse than nothing.** javac resolves the rewritten names
  against `balticporter/corpus/ports/liqp/javac-stub`, read as a `-sourcepath` under `-implicit:none` so it is
  never written into the output (84 class files, all `liquid/parser/v4`). It declares no constants,
  so a form the runtime could not link is a javac error here rather than a run-time one; and
  upstream `liqp` is on NO classpath of that step, so a reference the rewrite missed cannot resolve
  and the build refuses.

**…and a fourth, found by audit-2 (F5): the CACHE KEY did not name the generated sources.**
`LiqpClasspath.ensure` asked three questions — do the parser classes exist, are these the
coordinates, is this the rewrite policy — and not the one about the input javac actually reads. The
generated tree is UNTRACKED and is rebuilt by `./mvnw generate-sources`, so a grammar change writes
new `.java` under an unchanged key beside a `parserClasses` directory that still holds `.class`
files, and `hasParserClasses` is an EXISTENCE test. The port then resolves against a parser it no
longer has — this cache's own stated failure (an import that resolves WRONGLY rather than failing),
one input further in, with the port compiling and every count flat.

`generatedDigest` folds the tree into the key: every `.java` under it by RELATIVE PATH and by
CONTENT, sorted. Both halves are load-bearing — content alone misses a RENAME (ANTLR renames a
generated class when the grammar's name changes, and javac then produces a class file at a name
nothing imports), the path set alone misses every edit to a rule body. An absent tree digests to a
distinct stated value rather than throwing, because this is consulted on a freshness question and
`compileParser`'s refusal — which can print the command that fixes it — is the right place for the
fatality. Five cases in `LiqpParserRewriteSpec`; liqp re-measured at **0 errors, 357/218, 0 member
digests** with the parser rebuilt under the new key.

**And `out/liqp-upstream-classes` is GONE.** That directory — upstream liqp compiled beside the
parser, for scalac only — existed for exactly this seam: scalac reading `liqp.TemplateParser$ErrorMode`
without it threw `AssertionError: failure to resolve inner class` out of `ClassfileParser` and
ABORTED, which reads as a smaller error count rather than as a failure. With no seam left there is
nothing for it to soften, and ONE directory now serves the frontend, scalac and the test run.

**WHAT IS LEFT IS NOTHING — 3 -> 0**, and the three closed in three different places, which is the
useful part of the number:

| n | family | closed by |
|---|---|---|
| 1 | K2/K5.7's `Map.Entry.setValue` at the case with NO LOOP AND NO MAP — the receiver is a FIELD whose value, after the retyping, is a detached pair | **(a) ENGINE.** The refusal stands and it now has an EMISSION: `java.util.Map.Entry.setValue` is an OPTIONAL operation whose contract is `UnsupportedOperationException` where the backing map cannot take the write, and a ported entry with no reachable map IS that entry. Louder than java, never quieter, counted at the slot — the opposite of the `SimpleEntry` K2 rejects, which writes to a detached copy and changes nothing. `collection-boundary` 14 -> 15 |
| 1 | MUnit's `Compare` needs a common type and two `toJava` calls infer different element types | **(a) ENGINE.** X2's fact at the REFERENCE overload: java's `assertEquals(Object, Object)` widened both operands exactly as `assertEquals(long, long)` widened its numeric ones, so the port writes java's own widening down as the call's type arguments. The trap is that BOTH operands read as `java.lang.Object` in the TIR — a boundary wrap is typed as the FORMAL it was inserted for — so "the types agree" is what a naive screen concludes about the one pair that needs the widening |
| 1 | G24 — java's `<T>` bound is VACUOUS and the emitted `T <: java.lang.Object` is not | **PORT DECISION D-liqp-7.** The engine answer is priced and refused in both directions (libGDX core 0 -> 50 for every bound, 0 -> 6 for methods only, the two halves coupled through §4.4's reference identity and G2's wildcard capture), and the cheaper place to stand was evaluated and does not exist — `Serializable` is WRITTEN in the java as the declared type of `cases`, so pinning the inferred lub moves the mismatch one line down. So the port says which tests it does not run: `ComparingExpressionNodeTest`'s helper and the two `@Test`s that call it, **2 of the suite's 639**, at the smallest granularity that compiles. Excluding the FILE would have cost 5. Deleted, not narrowed, the day G24 closes |

**Two of the three were reached only because the first attempt at them was measured**, and both
lessons are lifted:

- the `Map.Entry` one was pre-authorised as a PORT decision — `dropMethods` on `setValue` — and the
  drop **does not stand**: K5.7 keeps java's parent, so removing the member leaves the class abstract
  against the interface it kept. Measured as one `Not Found` traded for one `needs to be abstract`,
  and the new error is invisible until the port is at 0 (§3). The rule is now `CLAUDE.md` §1(b) and
  `ENGINE-LIMITS.md` K5.7: an obligation the ENGINE's own translation created is not a port's to
  discharge with a drop;
- the G24 one, declared as a test-set `dropMethods`, was refused by `ManifestAgreement` as three
  fatal `ExtraDrop`s — because a base's `governs` claim is a NAMESPACE and a test source set is
  always inside its base's. That screen now reads the base's published PORT MAP, which is what §1.5
  actually says (`ENGINE-LIMITS.md` D10). `manifest` 3 -> 0 here, 0 unchanged on every other port,
  and no member digest moved by the change.

**And nothing among the three was a manifest question about TYPES.** There is no drop or injection
for this port that is not a rewrite: every one of these types is mechanically portable and the
disagreements were TYPES, so the only honest `dropTypes` + `inject` would have been a hand-written
`NodeVisitor` (849 ll.), `LValue`, `Template`, `LiquidSupport`, five `spi` files and nine filters —
which is the library, by hand, and the exact "false covenant" the reference port's own history warns
about (two of ssg's files shipped marked `Covenant: full-port` while carrying a stub, and both had to
be redone). A hand-written replacement for code the engine can translate is not port policy; it is a
port that stopped measuring the engine.

The phase's own §1(b) knob was the other candidate and is a **measured dead end in both
directions** — `ENGINE-LIMITS.md` K16: 27 → 47 with an 18-entry `except`, 27 → 51 with the phase a
no-op, so `CollectionsTransform` being ON is worth 24 errors to this library. A scope splits a call
graph, and liqp's collection types are its currency. The scoped-out `NodeVisitor` alone went 2 → 22,
almost all of it K9's enhanced-for over a real `java.util.List` — a scope withdraws the phase's
REWRITES too, not only its retyping. **Do NOT retry.**

**The one residue that was ever attributed to a DECISION turned out not to be one**, and that is
worth keeping: the parser's bytecode asking for a `liqp.TemplateParser.ErrorMode` closed at the
BUILD (D-liqp-1b above). No key rewrites an ARGUMENT and a `type-redirect` at that enum would move
the port's own type everywhere — all true, and none of it meant no fix existed. **A residue
attributed to a decision is worth re-reading once, because what is being decided about may not be
what you think it is.** "External" was never in question; what was mis-stated was that an external
generated parser is a fixed artefact, when it is this port's own build step's output.

### 10.5.4 The test port — how it is built, and what it excludes

`balticporter/corpus/ports/liqp/test.conf` is a §1.5 dependent of `main.conf` (it inherits
`packageRenames { liqp = "ssg.liquid" }` and the base's surface phases, and adds `test-framework`).
`LiqpTestClasspath` derives its frontend classpath from the main one and adds `junit:junit:4.13.1`,
the ONE test-scope coordinate the pom declares — `org.hamcrest:hamcrest-core:1.3` arrives with it
transitively and is deliberately not named. `just liqp-measure` runs both ports, compiles both
source sets on one invocation and splits the wall by the path scalac printed.

| | |
|---|---|
| emitted | **105 Scala test files** from 105 java (nothing excluded since T9 closed), members in the source map |
| tests | 639 `@Test` upstream -> **637 emitted** (munit 637, junit residue **0** — the whole JUnit surface converted; the only 2 lost are D-liqp-7's) |
| scalac errors | **main 27 -> 8 -> 7 -> 1 -> 0, test 49 -> 29 -> 25 -> 23 -> 11 -> 5 -> 3 -> 2 -> 0**; the two source sets are never summed, because a test-set error is frequently a cascade of a main-set one |
| `portability(emitted)` | **1467**, dominated by hamcrest (725 `assertThat` + 667 `is`/`equalTo`), which the conversion deliberately leaves in place and `ENGINE-LIMITS.md` X6's `org.hamcrest.` rule is what counts |
| `omissions` | **8** — dropped `@SuppressWarnings` on anonymous-class fields |
| `trivia` | **0 lost**; recovered 1 -> 21 and deliberate 0 -> 36, both of them D-liqp-7's — the three dropped members took their comments with them, and the split is the point: a `lost = 0` held by RECOVERING everything says nothing |
| `break_residue` | **0** over both source sets |

**The four excluded files are BACK, and with them 62 tests — the loss is now 2 of 639.**
`ENGINE-LIMITS.md` T9 had its first corpus sites here — five method-LOCAL named classes in
`ReadmeSamplesTest`, `TemplateTest`, `DateTest` and `LiquidSupportTest`, each a
`class X implements Inspectable {…}` inside a `@Test` body — and closing it deleted the four
`excludeGlobs` lines and the D-liqp-5 injection their cascade needed, exactly as `test.conf` said
it would. Discovery **575 -> 637 of 639** at **0 scalac errors before and after**; the lane's gate
now reads `!! TESTS LOST — 2 of 639`, and the two are D-liqp-7's.

**The suite went 574/1 -> 631/6, and the five new failures are the number to read.** None of them
is in a local class: every one of the five recovered sites lowers and passes. The five are this
port's own reflective-surface residue — K21's bean seam and a dropped jackson annotation, reached
through liqp's `Inspectable`/`LiquidSupport` SPI (`TemplateTest.testRenderInspectable` and
`testDeepInspectable`, `LiquidSupportTest.testLookupNode2c`, `testMapFilter2c` and
`renderLiquidSupportWithNewRenderingSettings`) — and they were invisible only because the tests
that exercise them had never run. Closing T9 did not cause five failures; it revealed five, which
is CLAUDE.md §1's "N failures are gated behind this one is a HYPOTHESIS" read from the other end.

**THE SIX, CLASSIFIED, AND FIVE OF THEM ARE CLOSED — 631/6 -> 633/4 -> 636/1.** Read each through
`test-failures.tsv` (§5.1), never by opening an emitted file:

| n | family | §1 | what says so |
|---|---|---|---|
| ~~2~~ **0** | K21 FACE 2's own bridge guard. `TemplateTest`'s two `Inspectable` fixtures declare `public final Map<String,String> some` and `public List<Object> d` — java-public fields whose type the COLLECTIONS RETYPING moved — and the bean accessor bridged only at a `java.lang.Object` field, so `getSome()` handed jackson a `scala.collection.mutable.Map`. The phase's own doc named this as its gap and said no corpus port had such a field; T9 gave it four | (a) engine, `ENGINE-LIMITS.md` **K21** — CLOSED: the accessor is a REFLECTIVE surface, not a java one, so it is typed `java.lang.Object` and ALWAYS bridged | `TemplateTest.testRenderInspectable` obtained empty output; `testDeepInspectable` threw `VariableNotExistException: 'a.b[2].d[3].e'` |
| ~~3~~ **0** | `@JsonSerialize(using = LiquidSupport.LiquidSerializer.class)` on the `LiquidSupport` INTERFACE was DROPPED — a TYPE's annotations are harvested at `defineType`, where no expression translator existed, so every argument-bearing one went to `omissions`. Without it jackson bean-serialised a `LiquidSupport` instead of calling `toLiquid()`, and only the EAGER evaluate mode goes through jackson — which is why each failing test had a passing non-eager sibling. **It took TWO changes, not one**: the harvest alone flipped one test and BROKE another (net 633/4), because the serializer it restored hands `toLiquid()`'s retyped map to `JsonGenerator.writeObject` — K21 face 1, met at a sink this port had not declared and which its own `OpaqueEgress` list had been naming since K21 shipped | (a) engine + (b) policy, `ENGINE-LIMITS.md` **T16** — CLOSED, with `reflectiveSinks` gaining `com.fasterxml.jackson.core.JsonGenerator` (D-liqp-9) | `omissions` **1 -> 0**, `collection-boundary` **25 -> 24**, and the suite **633/4 -> 636/1** |
| **1** | `Sort$ComparableMapEntry implements Map.Entry<K,V>`, retyped to `scala.Tuple2`, which no class can implement | (a) engine, K18 family — a COUNTED REFUSAL, not a silence | `collection-boundary` `InexpressibleParent` on `liqp/filters/Sort.java:75`, one of the main port's 25 |

Every one of the four that remain is already a row in a check this run records. That is the state
the port is meant to be in: a failure whose only evidence is the suite is the failure this project
cannot afford, and none of these is one.

**The other 2 are D-liqp-7** (§10.5.3) — `ComparingExpressionNodeTest`'s `cartesianProduct` helper
and the two `@Test`s that call it, dropped at MEMBER granularity against G24's priced-and-refused
bound change. Dropping the FILE would have cost 5; the other three tests in that suite run.

**The 49, classified — and ALL of them closed.** Every one is (a) engine except the three named
below, and none is `TestFrameworkTransform`'s gap — the one that WAS its job is the last row, and it
is now built. **The test source set reads 0.**

| n | family | where it goes |
|---|---|---|
| ~~20~~ **0** | `ZoneOffset.systemDefault()` — a static reached through a SUBCLASS name. Java inherits statics, Scala companions do not | `ENGINE-LIMITS.md` **T14**, CLOSED in the frontend: the receiver is the interned symbol's OWNER, and the FIELD half's superclass-only walk became the inheritance closure (a java interface constant is inherited through `implements`) |
| ~~12~~ **0** | `value TemplateTest is not a member of ssg.liquid` — four suites `import liqp.TemplateTest` for its nested `ComparableBase` | **NOT (a)** — closed first by D-liqp-5, an `inject` of that one nested type at the emitted FQN, and now closed by the real thing: T9 is CLOSED, `TemplateTest.java` ports, and both the exclusion and the injection are DELETED rather than grown, exactly as each said it would be |
| ~~4~~ **0** | an unqualified inherited `add(…)` inside a double-brace anonymous subclass of a retyped collection | `ENGINE-LIMITS.md` **K5** (extended), CLOSED: inside a NAMED class the frontend already supplies `this.`/`Outer.this.`; inside an ANONYMOUS one it does not, so the enclosing `new … { … }` claims the pending call — and the same claim repaired 22 SILENT `put` sites the four errors never named |
| ~~2~~ **0** | `Found: Object / Required: String` at `InsertionTest`'s two anonymous `Block.render` bodies | `ENGINE-LIMITS.md` **T15** (new), CLOSED: `(c ? a : b).toString()` emitted the call INSIDE the else branch. A receiver is an operand, and four receiver positions were not asking `operand` |
| ~~6~~ **0** | one heterogeneous `Arrays.asList(98, "97", true, false, null)` — six per-element `Found: (98 : Int) / Required: String` | (a). Java infers `T` across all the arguments at once and BOXES; at an INFERRED `A` scalac declines the boxing conversion outright ("implicit conversions were not tried because the result of an implicit conversion must be more specific than T"). Java's answer is recorded on the CALL, so the rewrite writes it down as the explicit type argument and `Predef.int2Integer` applies. **What is left is ONE aggregate mismatch, and it is a different fact**: `cartesianProduct[T <: java.lang.Object]` will not take a `Buffer[Buffer[Serializable]]`, because scala 3 roots `java.io.Serializable` at `Any` (value classes are serialisable) while java's `<T>` bound — `T extends Object` — is VACUOUS and scala's `<: Object` is not. Reproduced standalone; the fix is to stop emitting the vacuous bound, whose blast radius is every generic signature in the corpus — built, measured at libGDX core 0 -> 50, reverted, and answered by D-liqp-7 instead (§10.5.3) |
| ~~2~~ **0** | `Found: (templateParser.errorMode : ssg.liquid.TemplateParser.ErrorMode)` at `TestUtils` and `LiquidParserTest`, against the generated parser's `liqp.TemplateParser.ErrorMode` formal | **NOT (a)** — D-liqp-1 × D-liqp-2, the same one the main set carried, and CLOSED with it by **D-liqp-1b**: the generated parser is this port's own build product, so the build step rewrites its references into the emitted namespace before javac reads them (§10.5.3) |
| ~~1~~ **0** | `LiquidParserTest#array` — `JavaCollections.toArray(java.util.Arrays.asList(arr*), …)` | K6.5's aliasing refusal in the test set, CLOSED with the eleven the main port carried: what was refused was the copy, and `asListView` is java's own live view |
| ~~1~~ **0** | `RenderSettingsTest` — `E172 Can't compare these two types: java.util.List[Object] / java.util.List[String]` at `assertEquals(toJava(list), toJava(asList(…)))` | (a) CLOSED, X2's fact met at a REFERENCE pair rather than a numeric one: java's `assertEquals(Object, Object)` WIDENED both operands and MUnit's `Compare[A, B]` needs the two to relate, which two invariant `List`s at different element types do not. `TestFrameworkTransform` now writes java's own widening down as the call's type arguments wherever MUnit's stronger check cannot be met — and the trap is that BOTH operands read as `java.lang.Object` here, a boundary wrap being typed as the FORMAL it was inserted for |

The 12 `JavaCollections.fromJava` errors that used to head this table — java's double-brace
`new HashMap<>(){{ … }}` handed to a retyped value — are GONE, taken by A2/A3/A4 (the external wrap
gated on what `fromJava` can produce). What replaced them at the bottom of the table is smaller and
different: six per-element mismatches at one heterogeneous `asList`, and one `Can't compare these
two types` at `RenderSettingsTest`'s `toJava`-vs-`toJava` assertion.

**Twelve of those messages then changed without the count moving.** `ENGINE-LIMITS.md` K6.5's fourth
case — an array FORWARDED through an external `T...` slot is now spread — turned
`Found: java.util.List[Array[Object]]` into `Found: java.util.List[Object]` at twelve slots across
both source sets (`LValue.asList`, `For.renderArray`, `Concat`, `Reverse`, `Uniq`, `Sort_Natural`,
`ContainsNode`, `LiquidParserTest.array`, `Insertions.of`, `Filters.of`). The remaining disagreement
at each is the collections retyping, not the arity: the value is now the N-element list java built.
44 members moved across the two source sets and no check count did.

The last row moved when `Arrays.asList`'s rewrite learned the EXTERNAL-callee pack shape
(`ENGINE-LIMITS.md` K6.5, the composition): three `LookupNodeTest` sites were repaired and one
heterogeneous `asList(98, "97", true, false, null)` replaced a single aggregate mismatch with six
per-element ones — java boxes those literals into `Serializable`, Scala's `Int`/`Boolean` are not,
and the compiler can only say so once the elements are separate arguments.

**What the suite exercises**, which nothing before it did: 46 `@Test(expected=…)`, 2 `@Before`, 38
anonymous classes, a `switch` on `String` with NO `default` (`nodes/ComparingExpressionNodeTest
.java:142`, §4.4's fall-out row — and its NULL-selector sibling, which the emitter now guards), and
the `ServiceLoader` lookup below. §10.5.5 is what it found.

**`META-INF/services` is hand-written** (`ported/ssg-liquid/src/main/resources/META-INF/services/ssg.liquid.spi.TypesSupport`),
because the engine emits `.scala` and nothing else and this file's NAME and CONTENTS are both
upstream FQNs a rename has to move — `ENGINE-LIMITS.md` P5, whose counting half is shipped and whose
artefact lane is not. `Template`'s `static { }` block reaches `ServiceLoader.load(TypesSupport.class)`,
so absent it the whole suite runs with zero providers and says nothing. The lane carries it with
`--resource-dir` and refuses to run if the file is gone.

**The run's CWD is part of the measurement.** 45 tests read `./snippets/`, `./_includes/` and
`src/test/jekyll/` relatively, and `TemplateTest.parseWithInputStream` goes through a
`FileInputStream`, which `-Duser.dir` does not reach. The lane builds a symlink fixture tree under
its own scratch and runs `scala-cli test --workspace` from inside it, so nothing is written into the
ssg submodule. `alternative_includes/` at the upstream root is referenced by nothing and `ruby/` is a
human's shell harness — neither is linked, and no test shells out.

### 10.5.5 THE SUITE, RUN — the census

**`just liqp-measure` fires the run stage for the first time.** 0 scalac errors over both source
sets, then the fixture symlink tree, the `--resource-dir` services file and `scala-cli test
--workspace` from inside it.

| | |
|---|---|
| upstream `@Test` | **639** |
| emitted munit registrations | **575** (64 lost: 62 to T9's four excluded files, 2 to D-liqp-7 — §10.5.4) |
| **outcomes recorded** | **575 of 575 emitted** — the full accounting, nothing inferred from a sum of markers |
| passing | **161 -> 357** (C12 closed) **-> 364** (K17 face 2 / `JS-E05`) **-> 392** (K17 face 1 / `JS-G31`) |
| failing | **414 -> 218 -> 211 -> 183** |
| newly passing / newly failing / newly skipped | **196 / 0 / 0**, then **7 / 0 / 0**, then **28 / 0 / 0** |
| did not run (skipped) | **0** |
| ignored | **0** |
| derived expected failures | **0** — this port has no `dropTypes`, so `dropped-types.tsv` is empty and no failure is deliberate. All 211 are `unexpected`, which is the honest reading and not a gap |
| declared expected failures | **0** — no `baseline/expected-failures.tsv` at all, which is the normal state of that escape hatch (§5.1) |

The `expected 0` line is worth reading rather than skipping: liqp drops no TYPE, so there is nothing
for the derived rule to classify from, and a run that reported some other number would be reporting a
list somebody had maintained by hand.

**THE FIRST CENSUS: the 414, by family** — three, and the first was 99% of the number:

| n | family | §1 | representative site |
|---|---|---|---|
| **409** | **`Template`'s promoted constructor locals initialise BEFORE the statements java wrote first**, so `this.templateParser` is still `uninitialized` when `blockNames$p` reads it. `TirEmitter.orderBody` hoisted every `ValDef` in a class body ahead of every statement — right for a real FIELD, wrong for a constructor local the funnel promoted. 394 arrived as the bare `NullPointerException`, 9 through an `intercept[LiquidException]` that catches the wrong exception, and 6 through a cast of it — one defect, three shapes | **(a) engine, `ENGINE-LIMITS.md` C12 — CLOSED** | `ssg.liquid.InsertionTest.breakTest` → `blockNames` [`liqp/Template.java:57`] |
| **2** | jackson cannot REFLECTIVELY CONSTRUCT a type the collections retyping moved | (a) engine, K15 family | `LiquidSupport$LiquidSupportFromInspectable#objectToMap` |
| **3** | genuinely per-test | unclassified | `ssg.liquid.nodes.GtNodeTest.testBug267StringVsNumber` |

**409 of 414 was one member, and that is the whole argument of §3 in one number.** The port was at 0
scalac errors, every check count flat, `break_residue` 0, `trivia` 0 lost, and 22 checks green.
Nothing in the pipeline could see it, because a Scala class body IS its constructor and the emitted
order is valid Scala meaning something else — §4.4's defect class, arriving at the scale the rule
warns about. `Template` is what every test parses through, so one ordering took 71% of the suite.

**THE SECOND CENSUS: the 218 that remained, by ROOT cause — of which 7 have since closed and 211
remain.** C12's fix flipped 196 and failed nothing
new; what it did do is let every test that used to die in `Template`'s constructor run far enough to
reach the defect that was always underneath. The families below are therefore FINDINGS UNMASKED, not
regressions — the first census could not have named one of them, and the run that produced them reads
`errors 0`, every check count identical to the run before it, and exactly **one** member digest moved
in the whole port (`Template`):

| n | root cause | §1 |
|---|---|---|
| **155** | **the collections retyping met at a PRODUCER**, in four shapes: `java.util.HashMap cannot be cast to scala.collection.mutable.Map` (138, `Template#renderToObject`'s `mapper.readValue(json, HashMap.class)`), jackson's `Cannot construct instance of scala.collection.mutable.Map` (10, reflective), `java.util.ArrayList` → `mutable.Map` (4) and `JavaCollections$FrozenBuffer` → `mutable.Map` (2), plus one `cannot sort: ArrayBuffer`. Every one is a value a THIRD PARTY produced at a declaration the port retyped — K14's counted direction and K15's family, met at run time rather than at a formal. `collection-retarget` reads **0**, which is what that check exists to say it cannot see here | **(a) engine, K14/K15 family** |
| ~~27~~ **0** | **a java LAMBDA at an external functional-interface slot, emitted as a cast instead of a SAM conversion** — `TemplateParser$$Lambda cannot be cast to java.util.function.Supplier` at `Optional.orElseGet(() -> …)`. **CLOSED** — a poly expression (JLS 15.2) is typed by its target in BOTH languages, so the fix is to stop interposing: `SpoonTir.polyArgsUncast` restores every lambda/method-reference argument to what `expr` produced, after all six argument arms have run. **28 flipped, not 27** — the census counted the failures whose stack showed the `ClassCastException`, and one more `IncludeTest` was failing downstream of the same two sites. 0 newly failing, 0 newly skipped; the whole blast is `TemplateParser` and its two `parse` overloads | **(a) engine, `ENGINE-LIMITS.md` K17 face 1, catalog `JS-G31`** |
| ~~7~~ **0** | **a `? :` over mixed boxed numerics** — JLS 15.25 gives `Long`/`Double` binary numeric promotion and the port emitted `if`/`else` plus `asInstanceOf[Double]`, which asserts instead of converting: `java.lang.Long cannot be cast to java.lang.Double` at `LValue#asNumber`. **CLOSED** — `SpoonTir.promotedBranch` performs the conversion on each OPERAND, so the `if` has java's type and there is nothing to assert. All seven flipped, 0 newly failed, and the whole blast on this port is `LValue` and its `asNumber` | **(a) engine, `ENGINE-LIMITS.md` K17 face 2, catalog `JS-E05`** |
| **29** | assertion / comparison failures with no exception behind them — 27 `munit.ComparisonFailException` (the `Pop`/`Push`/`Shift`/`Unshift` filter families, the two `where` impls, `LookupNode`, `IncludeRelative`, `Json`) and 2 `AssertionError` in `ForTest`. Several are downstream of the 155 (a filter handed a shim renders differently); none has been read individually yet | unclassified — the next wave's work, and the first place a per-test read is worth doing |

**Two of the three unmasked families are the SAME defect** — a java CONVERSION emitted as a scala
CAST — which is why they are one `ENGINE-LIMITS.md` entry and not two. Neither is visible to a
compile, to any check, or to a member digest: both emit valid Scala carrying java's own static type.
**Both are now closed, by two DIFFERENT mechanisms, and the difference is the part worth keeping**:
the conditional's fix SYNTHESISES a conversion from java's own computed type, while the lambda's
REMOVES one — a poly expression has no type to convert from, and the slot does the work in Scala
exactly as it did in java. The second was expected to need the expected type read out of the
external signature (K15's rule), and it needed no signature at all. `PROGRESS.md` recorded the
opposite for two waves, on the strength of the entry's own prose.

**The lane's own infrastructure held, on all three runs.** No failure is a missing fixture, an unresolved
`META-INF/services` provider or a working-directory miss: the 45 relative-path tests reach the
symlink tree, the `ServiceLoader` finds its two providers, and the parser's class files resolve. What
the run measured is the translation, which is what it is for.

**What is NOT yet evidence.** 392 passes is a real coverage statement in a way 161 was not — the §4.4
forms §10.5.4 lists (46 `@Test(expected=…)`, the two `@Before`s, the 38 anonymous classes, the
`default`-less `switch`) are now exercised broadly rather than only by whatever got past `Template`.

**THE THIRD CENSUS: the 183 were 160 of ONE defect, and it was not the one this section named.**
`392 -> 552 passing, 183 -> 23 failing, 0 newly failing, 0 newly skipped`, at `errors 0` before and
after and every other check count flat except `collection-boundary 12 -> 14`. The defect is
`ENGINE-LIMITS.md` K18 / catalog `JS-G48`: **a retyping moves STATIC types, and an `instanceof` and a
downcast ask about a RUNTIME OBJECT.** `value instanceof java.util.Map` became
`value.isInstanceOf[mutable.Map[?, ?]]` — valid Scala asking a different question, since the retyping
moved neither the objects nor their classes, and liqp's whole data model is `Map<String,Object>` with
`Object` values discriminated by exactly that test.

**Read what that says about the second census's own classification, which was wrong in a useful way.**
This section had the 155 as "the collections retyping met at a PRODUCER … there is no one node to fix",
and warned against expecting a cheap closure. Both halves were mistaken and the reason is worth
keeping: **the four shapes it listed were SYMPTOMS at four different distances from one cause.** The
`readValue` `ClassCastException` is the cause one hop away; the `ArrayList`/`FrozenBuffer` casts and
the renderings that silently produced one table cell are the same cause reached through data the
producer made. Grouping failures by their EXCEPTION grouped them by distance, not by defect. The
producer direction was real and is worth **44 of the 160** on its own (measured: wrapping the
`readValue` result in `fromJava` alone reads 436) — it stops there because `asScala` is one level and
the values inside a deserialised map are still java's.

**THE RESIDUE — 23, read individually and classified.** Nothing here is a fixture, a `ServiceLoader`
or a working-directory miss; the lane's infrastructure has held on every run.

| n | root cause | §1 |
|---|---|---|
| **10** | **a REIFIED TYPE ARGUMENT at an external carrier** (`ENGINE-LIMITS.md` K20, which holds the rule; the per-site diagnosis stays here) — `LiquidSupport`'s `MAP_TYPE_REF: TypeReference<Map<String,Object>>` is jackson's super-type token, and the retyping moved the argument to `mutable.Map`, which jackson then reads out of the class file's generic signature and tries to CONSTRUCT (`Cannot construct instance of scala.collection.mutable.Map`). K18's fact one position over: not a test or a cast, but a type argument a THIRD PARTY reifies. 9 through `objectToMap`, 1 through a filter | **(b)** — the mechanism (do not retype a reified type argument; bridge at the use) is universal; WHICH external generic types are reified carriers is per-library policy, and `java.lang.Class` is the only one java itself guarantees |
| **4** | `java.util.ArrayList cannot be cast to mutable.Map` in `filters/where` — an item reaching `((Map<?,?>) e).get(p)` as a LIST. Downstream of the row above: every one of these tests renders through `Template.render(true, …)`, whose `putStringKey` builds its data with `mapper.convertValue(value, Map.class)` | **(a)**, pending — re-read after the row above closes |
| **5** | `munit.ComparisonFailException` in `JekyllWhereImplTest` / `LiquidWhereImplTest`, same two files and the same `render(true, …)` path | as above |
| **2** | `java.lang.Long cannot be cast to java.lang.Double` at `Ceil`/`Floor` — a PRIMITIVE cast that is a CONVERSION in java, K17's third face | **(a) engine**, `ENGINE-LIMITS.md` K17 / catalog `JS-E06`, still `Partial` |
| **1** | `Sort$ComparableMapEntry cannot be cast to scala.Tuple2` | **(a), REFUSED and COUNTED** — `Map.Entry -> Tuple2` is `UninheritableTargets` (K5.7) and `Tuple2` is a concrete target no live view can be, so K18 refuses it at the reified cast too. One of the port's two `ReifiedOccurrence` findings |
| **1** | `JsonTest` comparison | unclassified — the only one of the original 29 that is not accounted for by a named family |

**Of the "29 unread" the second census listed, 23 flipped with K18 and 6 remain** (the 5 `where`
comparisons and the `JsonTest` one); the two `ForTest` `AssertionError`s were among the 23. That is
the whole of what "read them individually" found: they were not 29 separate translation defects, they
were the same seam observed at the ASSERTION instead of at the exception. **The lesson generalises
past this port and is why the reading was worth doing at all**: the second census grouped failures by
their EXCEPTION CLASS, which grouped them by DISTANCE FROM THE CAUSE and not by cause — a
`ClassCastException` is the defect one hop away, a wrong rendering is the same defect three hops
away, and an assertion failure with no exception behind it is the same defect at the end of the
chain. A census is only a work list if its rows are causes; ours had four rows for one.

`tests.tsv` and `expected-errors` are accepted from the run that produced these numbers, so the next
change is diffed against 552/23.

**THE FOURTH CENSUS — K20 closed, and the residue is now 21 rows over THREE causes.**
`552 -> 554 passing, 23 -> 21 failing`, at `errors 0` before and after. The `TypeReference` carrier
entry (D-liqp-8 in `main.conf`) took the `Cannot construct instance of scala.collection.mutable.Map`
family **10 -> 0**. Blast over all eleven lanes: **4 member digests, all in `LiquidSupport`**, 0
everywhere else. **One check count moved in the whole corpus** — `collection-boundary` on the MAIN
port, **15 -> 14**, and the finding that left is
`convertValue(Object, TypeReference)` at `LiquidSupport.java:87` filed as
"external result (unverified pass-through, no signature)". K15's residue lane had been naming this
call for the life of the port; K20 turned the refusal into a bridge.

**Two of the ten flipped, and the other eight are the reason this census exists.** The row above
predicted "9 more gated behind it"; that was a hypothesis about a cause nobody had seen, and it is
wrong in the way `CLAUDE.md` §1 now records. K20's exception was thrown at the FIRST statement of
each of those tests, so it was hiding everything after it on the same path. Probed directly with the
carrier in place, `objectToMap` is provably correct — the returned map is a live `JMapWrapper` over
jackson's own `LinkedHashMap`, all four temporal values restored to their real types, and the
comparison answering `no` where it must. The eight are `ENGINE-LIMITS.md` K21.

| n | root cause | §1 |
|---|---|---|
| **8** | **K21 — a retyped VALUE and an emitted CLASS read out of the class file at the other end of the same call.** Probed, not inferred: `writeValueAsString(mutable.HashMap("key" -> "value"))` is `{"scala$collection$mutable$HashMap$$table":[…],"empty":false,"traversableAgain":true,"class":"…"}`, whose first value is the internal `table` — a `java.util.ArrayList`, which is the `ArrayList cannot be cast to mutable.Map` the `where` filter throws three hops later at a cast correct in both languages; and `Meta2`'s three java `public` fields, emitted as scala `var`s, give `getFields = []` and `objectToMap(meta2).size = 0`. The four `testDateTypes` are the second face and pass three assertions each on absent data (`null` coerced to `BigDecimal.ZERO` answers `yes` correctly for `a>=a`, `a>=b`, `a>=d`) | **(a) engine**, both faces, `ENGINE-LIMITS.md` K21. Neither is counted by anything today |
| **4** | `JsonTest` + `Relative_UrlTest` + `SizeTest` + `ForTest` — comparison failures on the same face-1 string | **(a)**, K21 face 1. **This is the verdict on the row the third census left "unclassified"**: `JsonTest`'s `mapper.writeValueAsString(value)` is that one line, with the port's own `mutable.HashMap` as `value` |
| **2** | `java.lang.Long cannot be cast to java.lang.Double` at `Ceil`/`Floor` | **(a) engine**, `ENGINE-LIMITS.md` K17 / catalog `JS-E06`, still `Partial` — unchanged |
| **1** | `Sort$ComparableMapEntry cannot be cast to scala.Tuple2` | **(a), REFUSED and COUNTED** — unchanged (K5.7 / K18's `ReifiedOccurrence`) |
| **1** | `AppendTest.testAppendToDateTypeEager` | K21 face 2 by inspection (an `Inspectable` with public fields); not probed |
| **5** | the remaining `JekyllWhereImplTest` comparison failures | **(a)**, K21 face 1/2 — same two data classes, observed at the assertion rather than at the throw |

**The lesson is the third census's own, applied to itself.** That census said a census grouped by
EXCEPTION CLASS is grouped by distance from the cause; this one adds that a census grouped by
*which fix is first on the path* is grouped by ORDER OF THROWING. Both give a work list whose rows
are not causes. The number that means something here is `10 -> 0` on the family K20 names, not
`552 -> 554` on the suite.

**THE FIFTH CENSUS — K21 closed on both faces, and the residue is 8 rows over THREE causes.**
`554 -> 559 -> 567 passing, 21 -> 16 -> 8 failing`, at `errors 0` throughout — face 1 (the egress
bridge, D-liqp-9) and face 2 (bean accessors, D-liqp-10) measured one at a time. It is small enough
to enumerate to the test, so it is enumerated to the test, with each row's §1 classification:

| n | test | root cause | §1 |
|---|---|---|---|
| **2** | `CeilTest.applyTest`, `FloorTest.applyTest` | `java.lang.Long cannot be cast to java.lang.Double` — a primitive cast is a CONVERSION in java and an assertion in Scala once a phase has retyped the value | **(a) engine**, `ENGINE-LIMITS.md` K17 / catalog `JS-E06`, still `Partial`. Unchanged since the third census |
| **1** | `SortTest.testSortMap` | `Sort$ComparableMapEntry cannot be cast to scala.Tuple2` — `Map.Entry -> Tuple2` is an `UninheritableTarget` and `Tuple2` is a concrete target no live view can be, so K18 refuses the reified cast | **(a), REFUSED AND COUNTED** — one of this port's two `ReifiedOccurrence` findings. Unchanged since the third census |
| **5** | `GtNodeTest`, `GtEqNodeTest`, `LtNodeTest`, `LtEqNodeTest` `.testDateTypes`; `LiquidWhereImplTest.testWhereWhenDateCompatibleTypes` | **K22 — a java `static { }` block emitted into the companion `object` and initialised by nothing.** `Template`'s block registers liqp's date-type SPI providers; `new Template(…)` does not touch the object, so `isCustomDateType(aDate)` is `false`, `asRubyDate` falls through to `ZonedDateTime.now()`, and every temporal comparison answers about NOW. Probed: forcing `SPIHelper.applyCustomDateTypes()` by hand flips all five, and it reproduces with a PLAIN `Map` and no reflection anywhere | **(a) engine**, `ENGINE-LIMITS.md` K22 — **FIXED**, see the sixth census below |

**THE SIXTH CENSUS — K22's instantiation trigger, and the residue is 3 rows over TWO causes.**
`567 -> 572 passing, 8 -> 3 failing`, at `errors 0`, with the five newly-passing tests exactly the
five the fifth census predicted — the only wave in this port so far whose prediction was right to
the test. The emission blast on liqp is **3 members** (`Template`, `filters.date.Parser`,
`filters.where.PropertyResolverHelper`, each gaining one `val _ = <Type>` line and its porter note);
corpus-wide it is 7 more on libGDX core, with every other lane's errors, check counts and suite
outcomes identical. What is left:

| n | test | root cause | §1 |
|---|---|---|---|
| **2** | `CeilTest.applyTest`, `FloorTest.applyTest` | `java.lang.Long cannot be cast to java.lang.Double` — a primitive cast is a CONVERSION in java and an assertion in Scala once a phase has retyped the value | **(a) engine**, `ENGINE-LIMITS.md` K17 / catalog `JS-E06`, still `Partial`. Unchanged since the third census — **FIXED, see the seventh census below** |
| **1** | `SortTest.testSortMap` | `Sort$ComparableMapEntry cannot be cast to scala.Tuple2` — `Map.Entry -> Tuple2` is an `UninheritableTarget` and `Tuple2` is a concrete target no live view can be, so K18 refuses the reified cast | **(a), REFUSED AND COUNTED**. Unchanged since the third census |

**And the new lane was not vacuous on either run**, which is what a coverage check most often is.
`class-init-trigger` reported `Unforced` **0 on all fifteen ports** from the first measurement —
every `static { }` block reached the instantiation trigger — and `SubclassInitUnforced` **18 on
libGDX core** (every `Actor` descendant with a companion) **and 1 on anim8**
(`QualityPalette <- PaletteReducer`). Those 19 are JLS 12.4.1 item 7, which the instantiation
trigger does not reach when nothing is instantiated; the second commit of the wave took them to 0.
A residue that existed, was counted, and then went to zero is the only evidence a lane can move at
all — and it is why the two triggers were landed and measured one at a time rather than together.

**Three things this census records that the numbers do not.**

- **The fourth census's attribution was wrong in two rows, in both directions.**
  `AppendTest.testAppendToDateTypeEager` was filed "K21 face 2 by inspection (not probed)" and
  flipped with face 1; the four `testDateTypes` were filed as face 2 and are only face 2 as far as
  the data — with the fields exposed they still fail, for K22. **An unprobed row in a census is a
  guess wearing a table's clothes**, and this one cost nothing only because the wave probed before
  it fixed.
- **K21 face 2 was hiding K22 exactly as K20 hid K21.** With no properties visible those tests
  compared `null` against `null`; the comparison could not be wrong until the data arrived. Third
  time in this port that closing one defect revealed the next — which is why the honest form of
  "N are gated behind this" is a re-census, never a prediction (`CLAUDE.md` §1).
- **The two new review lanes are populated and were right on their first run.**
  `collection-boundary` carries 11 `OpaqueEgress` rows on the main port and 6 on the test port
  (**4 until the dedup stopped keeping one GLOBAL origin per callee** — the base reached
  `BigDecimal#valueOf(Object)` and `StringBuilder#append(Object)` from an earlier path, so the D2
  filter dropped the test module's own rows for those two and nothing said so), and
  one of them names `JsonGenerator#writeObject` — a jackson sink `main.conf` does NOT declare.
  `bean-exposure` is 0 on the test port (everything in scope, no name clash). Neither number is a
  defect count; both are lists a port picks entries from.

**THE SEVENTH CENSUS — K17 face 3, and the residue is 1 row that STAYS.**
`572 -> 574 passing, 3 -> 1 failing`, at `errors 0`, with the two newly passing being exactly
`CeilTest.applyTest` and `FloorTest.applyTest` — the pair the sixth census named. What is left is
the counted `ReifiedOccurrence` refusal alone:

| n | test | root cause | §1 |
|---|---|---|---|
| **1** | `SortTest.testSortMap` | `Sort$ComparableMapEntry cannot be cast to scala.Tuple2` — `Map.Entry -> Tuple2` is an `UninheritableTarget` and `Tuple2` is a concrete target no live view can be, so K18 refuses the reified cast | **(a), REFUSED AND COUNTED.** Unchanged since the third census, and it stays: the refusal is the honest answer, not a gap |

**574 of 575 was this port's floor while 575 was the denominator.** Trajectory over the catalog
waves: `161 -> 357 -> 364 -> 392 -> 552 -> 554 -> 559 -> 567 -> 572 -> 574`. Closing
`ENGINE-LIMITS.md` T9 then moved the denominator to **637** and the floor with it — see §10.5.4 for
the census of what the 62 recovered tests revealed and where the four that remain are counted.

**Three things this census records that the numbers do not.**

- **The blast was 1,800 members and MEANT something at two of them.** `coerce` reading the type the
  term actually has moves every expression that carries a source cast, so the emission moved on
  fourteen of fifteen port artifacts (libGDX core 1248, anim8 164, liqp 102, jbump 76, gltf 66,
  liqp-test 50, sg 36, libgdx-test 28, ashley 20, noise4j 14, screens 8, vfx 8). Diffed line by line
  against a re-emission at the previous commit, **libGDX core's 683 changed lines are 683 removals of
  an adjacent duplicate identical cast and nothing else**, and liqp's 45 are 42 of those plus one
  redundant upcast (`PlainBigDecimal` to its own supertype) plus **the 2 wrapper corrections that are
  the fix**. A blast this size is only readable because it was classified; the member count alone
  would have said nothing, and "errors 0, suites flat" would have been the same sentence for a
  change that broke something.
- **The measurement order was decided by a REGRESSION, and the regression is the useful part.**
  `coerce` alone took libGDX **0 -> 4 errors** (`ComparableTimSort`'s raw `(Comparable)` casts),
  because the accidental thing that had been emitting java's unchecked conversion was the very
  branch the fix corrects. So the wave landed `uncheckedGeneric` FIRST as its own measured commit
  (blast 6 on libGDX, 0 on the other fourteen, every lane flat) and `coerce` on top of it. A single
  commit would have been green and would have hidden which half did what.
- **An edge-case suite found a defect neither the corpus nor the compile can see, and its fix has
  ZERO blast.** The five `JS-E06` tests in `CatalogAreaESpec` include the cells that must STAY a
  `ClassCastException` because java throws there too — and writing them surfaced that a java cast of
  a statically-known WRAPPER to a primitive (`(double) aLong`) emitted `v.asInstanceOf[scala.Double]`,
  a checkcast that throws where java unboxes and widens (JLS 5.1.8 + 5.1.2). It was broken before
  this wave too, and worse: the older emission put a `.doubleValue()` AFTER that checkcast, where
  nothing can reach it. The fix (`SpoonTir.castOf`) moves **0 members on all fifteen port artifacts**
  — no library in the corpus writes the shape — so the tests are the entire evidence that it is
  fixed, and would have been the entire evidence that it was broken. This is the third time in this
  port's history that a defect was found by writing the test for the cell NEXT to the one being
  fixed, and the first where the corpus could never have found it at all.

---

## 10.6 ssg-md — flexmark-java, the largest Java surface either reference repo has

**Milestone 1 measures a WALL, not a green port.** `just md-measure`. 486 java files in scope, 28 of
them declaration-only, **458 units converted → 468 Scala files written** (0 dropped, 0 injected),
21,571 symbols, 9,361 members in the source map.

### 10.6.1 The scope, and the four milestones behind it

flexmark-java 0.64.8, BSD-2-Clause, vendored under `ssg/original-src/flexmark-java` (the hand port
pins `bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14` in every file header). Fifty-three maven modules,
**1,091 main java files**, all declaring under the single package root `com.vladsch.flexmark` — so
the module split is a fact about `pom.xml` files and not about java, and the "43 modules collapse
into one namespace" framing this document used to carry overstates the rename risk to nothing: the
map is injective by construction and was verified collision-free over the whole tree.

| | scope | main files |
|---|---|---:|
| **milestone 1 — this port** | `flexmark` core + the eleven `flexmark-util-*` libraries | **486** |
| milestone 2a | 25 covered `flexmark-ext-*` with no intra-extension dependency, as DEPENDENT ports | 326 |
| milestone 2b | `abbreviation`, `enumerated-reference`, `jekyll-front-matter`, `macros` — each after its own sibling | 60 |
| deferred | 3 untouched extensions + 7 converter/tooling modules + `util-experimental` + `tree-iteration` | 219 |

Milestone 1 first because everything else depends on it: it is the shared surface 29 dependent
manifests will extend rather than restate (§1.5), and it exercises the package rename, the licence
obligation and most of `CLAUDE.md` §4.4 before any manifest composition is attempted.

**THE 28-OMISSION CORRECTION.** This document said the hand port had "24 undocumented omissions".
The number is **28**, enumerated against the 761 `Ported from:` headers: the whole
`util/html/ui/*` subpackage (10 files), eight `*JiraRenderer.java` (dead weight beside the two
absent converter modules), `flexmark-util-dependency`'s `Flat*` family (5 — and NOT dead code: it is
upstream's priority-ordered extension-resolution algorithm, so confirm during the port whether
registration order depends on it), two `@Deprecated` forwarding classes, and
`util-misc`'s `FileUtil`/`ImageUtils`. Note that the last two are exactly what `portability(emitted)`
independently proposed dropping on this run's first pass — the hand port's silent skip and the
engine's measured finding agree, which is the first time the two instruments have met on one file.

**The consumer budget is TWO TYPES.** `ssg-site` is the only real consumer of `ssg.md.*`
(`ssg-highlight` declares a `dependsOn` and references nothing), and it names exactly
`ssg.md.parser.Parser` (builder + `parse(String)`) and `ssg.md.html.HtmlRenderer` (builder +
`render(Node)`), with no extensions and no options configured. The `Node` flowing between them is
never spelled at the call site. Everything else under `ssg.md.*` — 767 hand-port files — is internal
as far as compile-time coupling goes.

**And there is a conformance oracle nobody has ever run.** Upstream ships six versions of the
CommonMark spec as classpath resources (618–652 examples each) and four `FullOrigSpec*CoreTest`
classes drive them through PLAIN `@Test` methods — not `@RunWith(Parameterized.class)`, so they are
inside `TestFrameworkTransform`'s mechanical reach as they stand. The hand port loads none of them
(this document's own §1 "one surprise per module" says so). That is behavioural evidence this
project has never had for any library, and it costs two ported classes plus
`flexmark-test-util`'s non-reflective half. The 114 `ComboSpecTestCase` subclasses and the 59
`@RunWith(Suite.class)` aggregators are the documented refusal and need a hand-written MUnit driver,
not an engine change.

### 10.6.2 First emit — measured state

| | |
|---|---|
| scalac errors | **243** at first emit (coded 241 + bare 2), **171** after wave 1, **106** after wave 2, **81** after wave 3, **69** after wave 4, **58** after wave 5, **47** after wave 6, **43** after wave 7, **40** after wave 8, **38** after wave 9, **35** after wave 10, **34** after wave 11, **30** after wave 12, **19** after wave 13 and **18** after wave 14 (coded 18 + bare 0), all `EngineGap`, 0 `Approx`, 0 `Unmapped`. Concentrated in **60 of the 468 emitted files** at first emit and **15** now, counted from `errors.tsv` rather than by eye — 96.8 % of the port compiles clean |
| `break_residue` | **0** — on a character-level markdown parser, which is the densest control flow any corpus library has had. §4.4's whole jump table cost this port nothing |
| `signature` / `trivia` (all three lanes) / `manifest` / `policy` / `port-map` / `substitution(*)` / `porter-notes` / `markers` / `switch-null` / `break-catch` / `try-resource` / `cast-conversion` / `class-init-trigger` / `rewrite-callsites` / `base-surface` | **0** on the first run of a 486-file library nothing in the engine was tuned against. `trivia(recovered)` is **4** — four comments the attachment channel could not place, quoted back with their java coordinates |
| `omissions` | **61** (64 at first emit; wave 1's SAM adaptation closed three `lambda return with an unnameable result type` rows) — 44 `annotation dropped` (`@SuppressWarnings`, the family no port claims), 12 `super(args) dropped`, 3 `promoted constructor body runs on every path`, and the residue |
| `jdk-surface` | **456 classified, 38 unresolved at first emit** (shimmed 7, mapped 44, kept 367) and **25 after wave 3** (27 after wave 2). The 38 were the retyped-owner members `CollectionsTransform`'s tables had no entry for, and they were the SAME 33 errors the compile reported — the two instruments agree exactly, and they moved together when the SE8 members were mapped (`ENGINE-LIMITS.md` K23). **26 after wave 12**, and the +1 is a row that MOVED LANES rather than appeared: mapping `java.util.AbstractSet` took `BitFieldSet`'s `super.equals(Object)` off `collection-boundary`'s `OpaqueEgress` (it is no longer an external callee) and onto this one, where *retyped to `mutable.Set`, no rewrite* is the honest sentence for a member K29's table deliberately does not carry |
| `collection-internal` | **5 after wave 9**, from the 7 the lane arrived at (`ENGINE-LIMITS.md` K26). What is left is the five `SplitTypeVariable` rows — exactly the five `MutableDataHolder.set` errors — and closing those needs the coercion to run at the INFERENCE site, because the formal has no head to coerce against. The two `DeclaredSubtype` rows (`OrderedMultiMap#keys`/`#values`) CLOSED at wave 9 with their two compile errors. The lane's own arrival was provably flat: 0 errors moved, 0 member digests on any of the sixteen port reports, and every other port still reads **0** |
| `collection-boundary` / `collection-closure` / `collection-retarget` | **28 / 3 / 0** at first emit, **27 / 3 / 0** after wave 2, **26 / 3 / 0** after wave 4 and **22 / 3 / 0** after wave 5 — one seam left the lane when the call at it became a helper call rather than a member on a retyped receiver, one more when the `keySet()` view gave `coerce` a factory for it (`ENGINE-LIMITS.md` K2.7), and FOUR when K25 held `BitFieldSet`'s class-file overrides literally, so each `super.<same>(c)` stopped handing a shim to `java.util.AbstractSet` — those four fell together with the four errors they caused, which is the attribution §5 requires of a lane that falls. **21 / 2 / 0 after wave 12** — the `collection-closure` row that fell is the one `ENGINE-LIMITS.md` K29 opens with (*`java.util.AbstractSet` unmapped while `AbstractCollection` is mapped, so the JDK relation is lost*), closed by mapping it, and the boundary row is `super.equals(Object)` moving to `jdk-surface` above. The 21 are `OpaqueEgress` 13, `ExternalCallee` 4, `InexpressibleParent` 2 and `ReifiedOccurrence` 2 — `ShimBoundary` is **0**, here and on every other port |
| `overload-risk` | **563**, with its denominator recomputed beside it: 26,166 program-declared calls examined, 3,915 with more than one applicable candidate, 563 spanning a java resolution phase |
| `heap-pollution` | **13**, every one `Acknowledged` — java warned and the author wrote `@SafeVarargs`, which scala has neither of |
| `idiom(converted / refused / residue)` | **0 / 315 / 0** — `SamLambda` 28 considered and 28 refused, `NarrowedReturn` 287 and 287 refused. The refusal population is the lane; nothing was converted |
| `portability(emitted)` | **18** sites against 37 rules — `javax.imageio.ImageIO` 8, `java.lang.reflect.Array` 2, `java.nio.file.Files` 2, and one each of `javax.swing.*` (2), `java.net.URL*` (3), `java.text.MessageFormat`. Six `substitutions-drop` remedies published, four of them naming exactly the types the hand port silently omitted |
| `dependency-coverage` | **10 of 10** — `java.util.Locale` 6 and `java.text.NumberFormat` 4, both answered by `scala-java-locales` on both non-JVM backends. Not a call to remove; a coordinate to declare |
| `decisions.tsv` | **2,014 rows** — 715 `RetypedSignature`, 507 `RenamedMember`, 458 `RenamedPackage`, 146 `ForcedClassInit`, 107 `FunnelledCtor`, 57 `WidenedVisibility`, 12 `DroppedSuperCall`, 10 `InjectedMember`, 2 `RetainedParent` |
| tests | **723, EMITTED AS OF WAVE 7 and not yet run** — see §10.6.6. The twelve scoped modules still ship no `src/test` at all, which is what `md-measure`'s own discovery block asserts; the suite for the code they emit lives in the `flexmark-util` AGGREGATOR and is now a second lane (`just md-test-measure`, `port-report/FlexmarkTestMigrate`) |

**The behavioural gate is now BUILT and does not yet RUN, which is a smaller gap than the one this
paragraph used to describe and is not zero.** §3 is explicit about what a compile-error count is
worth, and every number above except the last row is a compile-time one. On liqp the compile said
nothing about **409 of 414** first-run failures; this library is a parser, so the population §4.4
governs here is larger, not smaller. What wave 7 changed is that the 723 tests now EXIST as emitted
MUnit registrations with a lane, a discovery guard and an error baseline holding them — §10.6.6 —
so the remaining distance is a number (44 test-set errors on top of the library's 40) rather than an
absent source set.

### 10.6.3 The census, classified per §1 — **243 → 171 after wave 1, → 106 after wave 2, → 81 after wave 3, → 69 after wave 4, → 58 after wave 5, → 47 after wave 6, → 43 after wave 7, → 40 after wave 8, → 38 after wave 9, → 35 after wave 10, → 34 after wave 11, → 30 after wave 12 (and the TEST SET to 0), → 19 after wave 13, → 18 after wave 14**

Every error is `EngineGap`. Eight waves have run. Each table below is the state AFTER its wave, with
what each family cost, because a census that only lists what is left cannot be checked against the
commits that moved it.

**Closed by wave 1** (each is an `ENGINE-LIMITS.md` entry with its number, and each ships fixture
specs with the negative beside the positive):

| before → after | family | where |
|---|---|---|
| 243 → 201 | **an inherited type PARAMETER carried into a subclass unsubstituted** — a diamond forwarder's result and parameters, a synthesised primary's `sup$k` slots, and a forwarded GENERIC METHOD's own `[V]`. Four spellings of one substitution, of which two callers had it and two did not | G25 |
| 201 → 182 | **a SHIM's arity is INHERITED** — the `parenless` refusal was asked of the receiver's head symbol against three shim symbols, and a library's own `Cursor extends java.util.Iterator` is no shim by that test | K2.6 |
| 182 → 181 | **a `new` is not a CALL** — the external-producer bridge wrapped an anonymous class that already implements the shim in a converter FROM java | K15 |
| 181 → 177 | **a SAM's generic result is ADAPTED at the target** — `Function<Flags, Pattern>` says what `R` is; I9's refusal was a mechanism-absence argument. `omissions` 64 → 61, and 3 of the 7 sites were the SILENT non-local-return kind | I9 |
| 177 → 175 | **an enum's primary is java's ROOT constructor** — `ctors.head` was not a refusal but a wrong answer: an empty primary, plus a constant that silently took the field's default where java ran `this(1)` | T11.5 |
| 175 → 171 | **a multi-catch's union type needs PARENTHESES** — the frontend has built the `OrType` since the construct was modelled and the catalog has read `Handled` for as long | L5 |

The `No given instance of … boundary.Label` row (3) and the `fromJava` overload rows (12 in the
first census) were CASCADES and collapsed with their causes; that is why the per-family arithmetic
above sums to more than the family sizes the first census printed.

**Closed by wave 2**, and both are engine (a)/(b) with no manifest entry anywhere:

| before → after | family | where |
|---|---|---|
| 171 → 137 | **a ported java ENUM is not a `java.lang.Enum`** — and no `sealed abstract class` may name that supertype at all (scalac: *"only enums defined with the enum syntax can"*), so the shape had to become the scala 3 `enum`. The refusals — a constant with a class BODY, a member colliding with one of java.lang.Enum's FINAL names — keep the sealed lowering byte-for-byte and are COUNTED | T21 |
| 137 → 106 | **SE8's DEFAULT METHODS on `List`/`Map`/`Collection`** — `sort`, `computeIfAbsent`, `removeIf`, `containsValue`, `containsAll`, `ensureCapacity` mapped onto helpers (each because scala HAS the operation and it means something else); `listIterator`/`spliterator` REFUSED and cited; a bound method reference at a mapped member NAMED | K23 |

**Closed by wave 3**, all engine (a) with no manifest entry anywhere:

| before → after | family | where |
|---|---|---|
| 106 → 89 | **java declares `get`, `contains` and `remove` over `Object` ON PURPOSE** — the lookup is BY VALUE, so a probe of an unrelated type is meant to MISS. The phase already routed those three MAP members through `Any`-keyed helpers when the RECEIVER was wildcard-applied; the other face of the same seam is the ARGUMENT — a class implementing `java.util.Map<String,T>` DECLARING `remove(Object)`, and the frontend's G14 coercion, which are one shape. NOT a cast to the element type: `asInstanceOf[String]` throws where java answers `null` | K24 |
| 89 → 81 | **an enum's promoted constructor parameter SUPERSEDING a field it is not** — read off the NAME, so `HtmlMatch(String open)` dropped `public final Pattern open` and the enum shipped the PARAMETER's type under the FIELD's name. Java's two variable scopes make that shape ordinary: the constructor exists to COMPUTE the field from the parameter. Decided from the emitted TYPE, in ONE derivation the drop, the rename and the self-assignment elision all read | T11 |

**Closed by wave 4**, engine (a) with no manifest entry anywhere:

| before → after | family | where |
|---|---|---|
| 81 → 69 | **java's two `Set`-typed VIEWS of a map.** `m.keySet()` emitted a `scala.collection.Set` while its node claimed the retyped `mutable.Set`, and `m.entrySet()` handed back the MAP — so the phase carried ONE LOCAL PATCH PER POSITION it could reach (retype a `val`, refuse a coercion source) and answered at neither a method RESULT nor a branch of a conditional, where there is no slot to patch at all. Closed at the REWRITE with live write-through views in the runtime, whose two refusals are java's own; both patches deleted. `collection-boundary` 27 → 26, and `Issue.ShimBoundary` is now empty on all fifteen ports | K2.7 |

**Closed by wave 5**, engine (a) with no manifest entry anywhere:

| before → after | family | where |
|---|---|---|
| 69 → 67 | **a member that OVERRIDES A CLASS FILE may not have its formals moved.** §4.56's sentence read at an override rather than at a call: `BitFieldSet extends java.util.AbstractSet` declares `containsAll(Collection<?>)` over a parent the mapping does not cover, so the class file's member still takes java's collection and the retyped one overrides nothing. The literal-reading machinery a `RuleScope` already has does the holding; what could not be reused is the CLASSIFICATION, since `ScopedOut` says *widen your scope* and there is no key. `collection-boundary` 26 → 22, the four `ExternalCallee` rows at those `super.<same>(c)` calls closing with the four errors | K25 |
| 67 → 58 | **java's UNCHECKED CONVERSION at an *INHERITED* formal.** `AstActionHandler<C,N,A,H>.addActionHandler(H)` reached from a subclass whose `extends` clause says what `H` is, with the subclass's own RAW parameter: java admits it by JLS 5.1.9 and `uncheckedGeneric` declined at its first gate, because the formal is literally a type VARIABLE and `isGenericUse` answers false for one. G12's rule — *a callee's own type variables do not resolve at the call site* — has exactly one more exception than `appliedCtorArgs`, and the lookup is keyed by (declaring type, name) because a name key is what `inheritedTp` measured at 161/142/141. The ARRAY-DIMENSION guard is the half that decides it: cast at a `H[]...` slot the argument's arity is still wrong and the cast makes it COMPILE, throwing at run time (67 → 49 without the guard, 67 → 58 with it, `markers` 0 both ways) | G12 |

**Closed by wave 6**, engine (a) with no manifest entry anywhere:

| before → after | family | where |
|---|---|---|
| 58 → 49 | **a `T[]...` slot's ARITY is decided by ASSIGNABILITY.** At an `H[]...` slot the parameter is `H[][]`, a one-dimensional `H[]` is assignable to the COMPONENT and not to the parameter, so java PACKS — and `varargHoldsArray` read the slot as *is the argument an array*, exact for every corpus vararg until one had an ARRAY component. `dims(arg) >= dims(comp) + 1` beside the primitive test it belongs with: one assignability rule at its two kinds. The ELEMENT is what refused this twice before (`markers` 0 → 1 at the `?H` sentinel, 81 → 83 at argument inference) and wave 5 supplied it — the declared component rendered through the inherited-formal lookup. What NEITHER earlier measurement had reached is that the pack renders the type TWICE: `coerce`'s array-covariance arm cast the element with a bare `tpe` and emitted `Array[?]` inside a correct `Array[Array[Box[String]]]`, so the fix is the same lookup at the arm beside wave 5's. **Nine and not six** — the three `toArray(EMPTY_HANDLERS)` rows the census had filed apart are the same call shape reached from a different producer. The blast is 12 member digests, all inside the family, plus **263 `findings.tsv` rows and 4 member KEYS that are one mint counter incrementing** (`ENGINE-LIMITS.md` M10's diagnostic half, priced at 2 there and re-measured here) | G26 |
| 49 → 47 | **a wildcard-bearing map KEY the probe does not SPELL — and the obvious diagnosis was WRONG, which two ports had to say.** It reads as §4.56's partial type walk (*the key is a capture one constructor deeper than `wildcardMapCall` looked*) and that does not survive the corpus: a wildcard-APPLIED type is perfectly NAMEABLE — `Class[? <: N]` and `Item[?]` are types a call site can write. What fails is that scala's `Map[K, V]` is INVARIANT in `K`, so the probe's `Class[?]` does not conform to the key's `Class[? <: N]`; java's `get(Object)` never asked. So the condition is CONFORMANCE at the probe, stated as an EQUALITY (decidable; a subtype test is not, §4.56) beside the two bare-capture conditions that were already there. Deliberately NOT a widening of K24's `objectProbe` guard, which declines here correctly. **Both looser spellings were measured on ports with no such seam**: deep on key AND value moved 6 libGDX members at 0 errors, deep on the key without the equality moved 9 jbump members and 20 of ssg-md's own — review noise §1 refuses, and an over-approximation moves no count, so the DIFF is the only instrument that can see one. Final: 18 member digests on ssg-md, 2 on jbump (the documented subtype over-approximation), every other port flat | K24 |

**Closed by wave 7**, engine (a) with no manifest entry anywhere:

| before → after | family | where |
|---|---|---|
| 45 → 43 | **…and the CONSTRUCTOR form is the same sentence a third time.** `Type::new` was emitted `(() => new T())` with the referenced constructor's ARITY ignored, which is exact for as long as every `::new` in a corpus is nilary or an array — the 232 sites in the sge upstreams all are, and 0 emitted bytes moved on any of their eleven lanes. The two that are not are this port's, and both had been filed apart in the residue as `not enough arguments for constructor`, which is what the error TEXT says and not what the defect is: `Parser.REFERENCES` names `ReferenceRepository::new` and `DocumentParser.INLINE_PARSER_FACTORY` is `CommonmarkInlineParser::new`. A one-line follow-up rather than a second investigation, because the fact was already on the node (a constructor is never `static`, JLS 8.8.3) and the un-annotated parameters are what keep the nilary rendering byte-identical | G27 |
| 47 → 45 | **an EXTERNAL member's SYMBOL answers a question it was never told the answer to.** `Type::method` is one java syntax naming two functions, and the emitter read the split off `flags.isStatic` and the arity off `methodParams` — neither of which an external member's symbol carries. `Minter.external` interns with `Flags()`, so every JDK static reads *not static*; `externalSignature` refuses a slot it cannot name scope-free, so `Comparable.compareTo(T)`'s one type-variable parameter makes the whole `MethodType` absent and reads *takes no arguments*. §4.6's fabricated fact with the default baked into the data structure instead of into a `catch`. The two facts move onto the NODE (`Tree.MethodRef.referent`), read off the parser's own executable, and `CollectionsTransform.lowerMethodRef`'s independent second derivation reads the same one (F8). Eleven other lanes BYTE-IDENTICAL, 4 member digests here, both inside the two types holding the two references | G27 |

**Closed by wave 8**, engine (a) with no manifest entry anywhere:

| before → after | family | where |
|---|---|---|
| 43 → 40 | **a callee's type variable resolves from the RECEIVER's instantiation, and a `null` is where that shows.** G12's rule has a THIRD source beside `new C<targs>` and the `extends` clause, and `receiverTypeArgs` had been computing it for `varargPack` since wave 6 with no other reader: `OrderedSet<E>.add(E)` on an `OrderedSet<V>` field inside `OrderedMultiMap<K, V>` says `E := V` exactly. Two obstacles, both one line — the gate was `tpConcrete`, which answers `false` for a type PARAMETER and so discarded the substitution, repaired by `tpNameableHere` (the same walk with the variable arm widened to `sameVarInScope`, which is DECLARATION identity and not a name); and `coerceArgsFixed` never received `recvSubst`, so it is THREADED and not re-derived (F8). **THREE closed where the census predicted two**: `OrderedMap#addNulls` had been filed as *a rewritten call where the coercion is discarded*, and it is not — the cast is inserted by the FRONTEND and `CollectionsTransform`'s `add` → `+=` runs over a tree that already carries it. 16 member digests here (5 the closed sites, 7 a correct-but-unnecessary cast where the receiver now names a concrete type, 4 M10's `@<raw>` keys at byte-identical digests) and 6 on libGDX, of the same over-approximating shape, at 0 errors and every count flat | G12 |

**Closed by wave 9**, engine (a) with no manifest entry anywhere:

| before → after | family | where |
|---|---|---|
| 40 → 38 | **`coerce` reads a source's kind out of `kindOf`, which knows only this phase's own SCALA TARGETS — so it says nothing about a type the PROGRAM declares, which is exactly K26's `DeclaredSubtype` blindness read at the FIX rather than at the count.** `OrderedMultiMap#keys`/`#values` return an `OrderedSet` — a class this phase re-parented onto `mutable.Set` — at a `Collection`-typed result whose target is the standalone shim. The class really IS a `mutable.Set` there BECAUSE THIS PHASE MADE IT ONE, so `JavaCollection.fromSet` conforms; the record is read TRANSITIVELY, because the `implements` clause may sit on an abstract base the library declares. `collection-internal` **7 → 5** here and **16 → 0** on the test set, the two lanes falling by exactly the errors that closed | K26 |

**Closed by wave 10**, engine (a) with no manifest entry anywhere:

| before → after | family | where |
|---|---|---|
| 38 → 37, and the TEST set 25 → 12 | **a POLY EXPRESSION takes its type from the SLOT, and an OVERLOAD SET is not a slot.** `polyExpression`'s rule — a lambda has no type of its own and the slot gives it one, so never write a cast at one — is exact for a single formal and says nothing about a name standing for two alternatives of the same arity. Scalac types the literal BEFORE it can use an expected type, so all of them fail at once (`E134`, twelve of them on `tagLine(CharSequence, boolean)` beside `tagLine(CharSequence, Runnable)`) while `overload-risk` correctly reads ZERO: java's candidate set spans no resolution phase, so this is not T17's family. The fix is the ONE alternative javac picked, restated as an ASCRIPTION — `TirEmitter.polyOperand` already renders a `Tree.Typed` over a poly term as `(e: T)` rather than as a cast, which is why `polyExpression`'s refusal (the cast would assert a `Function0` IS a `Runnable`) still stands and why no emitter arm moved. Three conjuncts keep it off every other port: a LAMBDA only (a method reference is `TirEmitter.samAscribed`'s, and the STATIC form renders as a bare NAME where an ascription APPLIES a nilary method — `Found: Unit`, measured), the callee overloaded AT THIS INDEX (the unoverloaded `tagIndent` sits in the SAME java statement and takes the bare literal), and a nameable target with no cast java wrote. 10 member digests here and 12 on the test set, every one a declaration holding such a lambda. **It OVER-APPROXIMATES on TWO other ports and that is STATED rather than narrowed away** (§5's own rule): simple-graphs' `setWeight((a, b) -> weight)` beside `setWeight(WeightFunction<V>)` and screens' `pushScreen(() -> screen, () -> transition)` beside `pushScreen(S, T)` are the same shape and scalac resolves both unaided, so 3 digests took a correct-but-unnecessary ascription at 0 errors before and after. **What separates the failing site from those is NOT known**, and the first guess died on the next lane: *a nilary literal* is disproved by `pushScreen`, which is nilary and resolves, and *the competing alternative's kind* by `tagLine` and `setWeight` both competing against a primitive while `pushScreen` competes against a type variable. Every candidate narrowing would decline `tagLine` or admit the other two for a reason neither language states, so the guard stays where java's own answer is. A THIRD port moved a check COUNT — liqp's `portability(all|emitted)` **54 → 55**, one attributed row, because an ascription NAMES a type and a named type is a usage: `executorService.submit(() -> …)` now names `java.util.concurrent.Callable` in emitted text. That row is honest and it is also the argument FOR the over-approximation — `submit(Runnable)` discards the value where `submit(Callable<T>)` returns it, and before this only the `val`'s expected type pinned java's choice | G28 |

| 37 → 35, and the TEST set 13 → 7 | **a BOUND METHOD REFERENCE at a rewritten member — K23's own named row, BUILT because the count it was refused on was wrong.** K23 recorded it at *one site in the corpus*, and one site is what a search for the SHAPE finds: the other two on this port had been filed in the table below under their error TEXT, and the larger carried a diagnosis this fix disproves. `Utils#withDefaults` is `putIfMissing(map, entry.getKey(), entry::getValue)` — the census called it *"the `getKey` → `_1` arm fired on one access of `entry` and the `getValue` → `_2` arm beside it did not"*, which reads the emitted text exactly and explains it wrongly: nothing differs between the two receivers, what differs is the NODE. `lowerMethodRef` lowered the UNBOUND form only and its own comment said the bound one *"is the `Apply` case one node out"* — true of a CALL, false of a REFERENCE. The built arm takes java's ARITY off the node (`referent`, G27's field), BINDS THE RECEIVER ONCE (`{ val recv$ = expr; (a0$) => … }`, because JLS 15.13.3 evaluates the qualifier at reference CREATION and a lambda would re-read it per invocation — a §4.4-shaped difference with no compile error to report it), and skips the binding for `this`, which is not a variable. 4 member digests here, 7 on the test set; every `collection-*` lane, `jdk-surface` and `remediation` flat, which is honest — this closes SITES and no lane was counting them | K23 |

| 35 → 34, and the TEST set 42 → 40 | **a callee's own TYPE VARIABLE written down by the EMITTER — G12's rule at a fourth minting site, and the first one that is not in the frontend.** `TirEmitter.numericOverloadAscription` pins the alternative javac chose (java resolves an overload by exact match, scala widens numerics first) by ascribing the callee's function type, whose RESULT is the callee's DECLARED result — and on the shape every fluent java builder is made of that result is the declaring type's own parameter: `class B<S extends B<S>> { S append(char,int); S append(int,int); }` rendered `(segments.append: (Char, Int) => S)`, `Not found: type S`. The `extends` clause says what `S` is, so this is `CLAUDE.md` §4.56's synthesised-into-a-subclass rule read at a CALL and the substitution is `ParentSubst` — the receiver's own application composed onto `ParentSubst.of`, one map rather than a hop to chase. The PARAMETERS are deliberately offered no substitution: the pin only fires where every formal's head is one of the nine numeric primitives, so a formal cannot mention a variable. **Two ways the position has no honest text and a RAW receiver reaches both**: declining on the variable alone traded `=> S` for `=> ?`, because the raw use's argument IS a wildcard and the substitution binds it faithfully, so the guard is the pair — spelled as `OverloadRiskCheck.ascription`'s already is, top-level wildcard only. Where it declines the call renders as java wrote it, which is T17's stated refusal with `overload-risk` counting the risk. **Exactly two member digests over the two lanes and all thirteen other lanes byte-identical**; the only other number that moved is `catalog(consulted)`'s `JS-C29 fired` total inside its own row's text, −2 per lane, being the two renderings of a bare type parameter that stopped happening. The main-set site (`SequenceBuilder#append(char,int)`) was in the undiagnosed 8-row residue below; the test-set site is the one §10.6.6 had already re-diagnosed to here | G12 |

| 34 → 30, and the TEST SET 6 → 0 | **the JDK DEFAULTS a re-parenting removes — K29 BUILT, in four commits of which the first three are provably FLAT on all seventeen port reports.** The mapping this port had wanted since it began (`java.util.AbstractSet -> mutable.Set`, `A MAPPING MUST PRESERVE THE SOURCE LIBRARY'S OWN SUBTYPE RELATIONS` for the fourth time) could not be written until the phase answered what the re-parenting REMOVES: `java.util.AbstractCollection`'s `containsAll`/`addAll`/`removeAll`/`retainAll` are members `BitFieldSet` INHERITS and calls through `super` to delegate the general case, and `mutable.Set` has three of them not at all. **Two of the four errors closed before any mapping moved** — widening `JavaCollections.addAll` onto java's own `Collection` receiver contract took `ScopedDataSet#getKeys`, whose BOTH parameters were on the wrong side of the old signature, which is two of the four *SHIM against a scala collection, inside the program* rows below and which no lane counted (K26 says why). The `super` → `this` substitution is licensed PER MEMBER by the JDK default's own VIRTUAL dispatch and is a TABLE, not a rule: `super.clone()` and `super.subList(a,b)` read the receiver's own FIELDS and go on being refused in the same emitted file. Lane arithmetic is one row closing and one moving sideways: `collection-closure` 3 → 2 (the row K29 opens with), `collection-boundary` 22 → 21 here and 6 → 4 on the test lane, `jdk-surface` 25 → 26 — the same `super.equals(Object)` arriving where it belongs. 12 member digests over the two lanes, every one in `BitFieldSet`/`BitFieldSetTest` | K29 |

**What is left, by mechanism** (the census re-read at 30, every row counted from `errors.tsv`):

| n | family | §1 |
|---:|---|---|
| 2 | **the raw-generic family's residue, all three of its halves now closed.** Wave 5 took the sites whose arity was already right (G12), wave 6 took the nine whose arity was wrong (G26) and the two map-key probes (K24). What is left is two unrelated one-offs: **1** `toArray(EMPTY_HANDLERS)` at an OVERLOAD selection on the class's own `addHandlers` (java's `Collection.toArray(T[])` returns `T[]` and the port erased the empty-array argument to `Array[Object]`, which matches none of the three overloads); and **1** cascade at `new NodeVisitor(…)` | **(a)**, two one-offs |
| 2 | **the SHIM against a scala collection, inside the program** — the RESIDUE of that row after wave 5 closed its rule half (K25), wave 9 closed its `DeclaredSubtype` half (K26) and **wave 12 closed the `ScopedDataSet#getKeys` PAIR** by widening `JavaCollections.addAll` onto java's own `Collection` receiver contract (K29 step 1: both of that call's parameters were on the wrong side of a signature written for `mutable.Buffer` alone). A `JavaCollection` formal or result meeting a `Buffer`/`Set`/own-`OrderedSet` value at a call the port declares; what is left is `Attributes#values` and `BuilderBase#extensions`. What wave 9 took was `OrderedMultiMap#keys`/`#values`, the two the lane could SEE; what is left sits at a callee THIS PHASE MINTED, which carries no signature, and K26 records both the removed operand-only arm and its repair | **(a)** |
| — | **CLOSED AT WAVE 12, and it took the whole TEST SET with it.** The family was `BitFieldSet#iterator()` held to `java.util.AbstractSet.iterator()`'s class-file result (2 main) while every caller's `for (x <- bitFields)` found no `foreach` (6 test), and it existed for one reason: `java.util.AbstractSet` was not in `typeMap` while `java.util.Set` was, which `collection-closure` had been REPORTING as a lost JDK relation since the port began. Wave 11 measured the bare mapping and REVERTED it — it opened four `super.<JDK default>` rows `mutable.Set` cannot answer — and wave 12 built the phase obligation those four are, so the mapping landed free: main **32 → 30**, test set **6 → 0**, and not one of the four opened. `ENGINE-LIMITS.md` K29 carries the four commits and their numbers | **(a)**, `ENGINE-LIMITS.md` K25, K29 — CLOSED |
| — | **CLOSED AT WAVE 13 — `Nothing` at a structural type, and the REFUSAL was about the wrong half.** `IRichSequenceBase`'s `?{ append: ? }` / `?{ add: ? }` slots are six calls to one member: `<B extends ISequenceBuilder<B, T>> B getBuilder()`, whose variable appears in no formal and whose result is the RECEIVER of the next selection — G22's shape exactly, and G22's pin declines on its FOURTH condition, correctly and for both of that condition's reasons at once. The bound `ISequenceBuilder<B, T>` is an F-bound *and* names the enclosing interface's `T`, so it is not a type this call site can write down; filling `B` while `T`'s own bound still mentions `B` is `G8`'s "either every formal comes from the enclosing scope or none can", which four measured attempts have already priced. Wave 6 called this the expressiveness limit G8 names and it is one HALF of it: G8 is about a FILL, and these six sites never wanted one. Every one is a SELECTION on the result, and scala's own text says so — `Found: Nothing / Required: ?{ append: ? }`. The argument may stay `Nothing` (it is below every bound); what has no text is the type the selection READS, and an ASCRIPTION supplies that while satisfying nothing: `getBuilder().asInstanceOf[ISequenceBuilder[?, T]]`, whose `append` returns the capture, which is itself an `ISequenceBuilder[capture, T]`. Six errors, one commit, every check count flat. **The caution to keep is the classification, not the number**: a row read as REFUSED for two waves was refused against the wrong question | **(a)**, `ENGINE-LIMITS.md` G8.7 — CLOSED |
| 5 | **`MutableDataHolder.set` overload resolution** — `DataKey<Collection<Extension>>` retypes its type ARGUMENT to the SHIM while the value is an `ArrayBuffer`, so no `T` unifies. The same shim-against-`Buffer` seam one level in, at a TYPE ARGUMENT rather than a formal. Wave 6 read the sites: every one is `set(SharedDataKeys.EXTENSIONS, xs)` where java's `Collection<Extension>` became `JavaCollection[Extension]` in the KEY and java's `ArrayList<Extension>` became `ArrayBuffer[Extension]` in the VALUE — one java subtyping edge that the mapping sends to two unrelated scala types, so the seam is the phase's own and not a missing coercion at one call. It belongs with the six-row shim-inside-the-program row above and not apart from it: both are `collection-boundary`'s internal half, which that lane cannot see | **(a)** |
| — | **CLOSED AT WAVE 13 — `null` at a type PARAMETER, the two positions with NO FORMAL to read.** Wave 8 closed the ARGUMENT shape through the receiver's own instantiation (G12); what was left was a LAMBDA whose body is a bare `null` at a SAM result of `T` (`NullableDataKey`'s `(options: DataHolder) => null`) and a `null` RECEIVER — `HashMap.from(null.getAll())` — which was never a slot at all but the CONSTRUCTOR FUNNEL's own substitution of `this(null)` into `other`'s uses. JLS 5.2 gives java's `null` the type of the slot it is written at, so both are the same rule at positions `declFormals(i)` cannot reach. The lambda half needed a third fix underneath it: `samAbstracts` counted a `@Override` RE-DECLARATION as a second abstract method, so `DataValueFactory<T> extends Function<DataHolder, T>` — a functional interface java accepts a lambda for — was not a SAM to this frontend at all | **(a)**, `ENGINE-LIMITS.md` G8.5 — CLOSED |
| 3 | the JDK members wave 2 did NOT map — `listIterator` 2 and `spliterator` 1, REFUSED with their citation because each hands back a JDK protocol rather than a value. The fourth row here was the ONE bound method reference (`this.headings::add`), and it CLOSED at wave 10 with the two the census had filed elsewhere | **(a)**, all three `ENGINE-LIMITS.md` K23 |
| 3 | **`MapEntry` against `Tuple2`** — a class that IMPLEMENTS `java.util.Map.Entry` keeps java's parent (`K5.7`'s `UninheritableTargets` refusal, counted as `InexpressibleParent`), so its value does not conform where the retyping expects a pair | **(a)**, the counted half of K5.7 |
| 4 | the residue, no family above 2. **`Not found: byteOffset$p$` (2, `Segment$Base` and `Segment$Text`) CLOSED AT WAVE 13** and wave 6's diagnosis of it was exactly right — a SUPER CALL naming a MEMBER: the funnel promoted the constructor parameter, §4.55's promoted-ctor-scope rename split it into a parameter `byteOffset$arg$p` and a member `var byteOffset$p$ = byteOffset$arg$p`, and the `extends Segment(…, byteOffset$p$, …)` clause named the MEMBER, which is evaluated before the class body exists. What the diagnosis did not say is WHICH pass wrote that name: not the rename, but `MutableParamsTransform`, which repurposes a REASSIGNED parameter as a `var` and inserts it after the delegation while leaving the delegation itself naming the repurposed symbol (`ENGINE-LIMITS.md` C14). **`Utils#withDefaults` CLOSED at wave 10 and its diagnosis here was wrong** — this table said the `getKey` → `_1` arm fired on one access of `entry` while the `getValue` → `_2` arm beside it did not, and blamed the receiver's own `Kind`; the java is `putIfMissing(map, entry.getKey(), entry::getValue)` and the second reading is a METHOD REFERENCE, which no `Apply`-keyed arm sees (K23). FOUR of the rows this entry used to carry CLOSED at wave 7 and all four were one defect (G27): the two `Type::method` eta-expansions, and the two `not enough arguments for constructor` rows — `Parser#REFERENCES` and `DocumentParser#INLINE_PARSER_FACTORY` — which read as an inference problem and were a `Type::new` emitted with no arguments. That is worth keeping as a caution about this table: a residue row is filed under the error TEXT, and two of these four were filed apart from the family they belong to for exactly that reason. **`IRichSequenceBase#equals` CLOSED at wave 13** — the SAM row this entry used to carry, which was not a SAM problem at all but the frontend's OWN widening of a java `equals(Object)` parameter to `scala.Any` reaching a `java.lang.Object` slot (G8.9); the SIXTH residue row whose cause differed from the one its text named. What is left is FOUR: the two `Object` results of an ERASED `Function` receiver (`G11` at a use — the widening is on the RESULT, so NOT K24's family, and G21's rule is what they want, at a receiver the erasure sent to `Function[Object, Object]` when `Function[Object, Class[?]]` would keep the result), `Array[E]` against `Array[Enum[?]]` (java's array COVARIANCE at a LOCAL INITIALISER, which `arrayFormalCast` reaches only at an argument), and `Collections.EMPTY_SET` at a vararg. **`SequenceBuilder#append(char,int)` CLOSED at wave 11 and was the THIRD residue row whose cause was a different defect from the one its text named**: an `E006 Not found: type S`, filed here as a one-off, was the emitter's own numeric-overload pin writing the callee's F-bounded result down at a call site that cannot name it (G12). Same caution as `Utils#withDefaults`: read a residue row before counting it | mixed |

**Where wave 13 ended, and the one thing it is evidence for.** Five commits took **30 → 19** and
four of the five closed a family this table had already looked at and classified. Three of those
four were classified WRONG, in three different ways, and that is the durable result rather than the
count:

| the row said | what it was |
|---|---|
| G8's six are "an expressiveness limit, the honest answer is a REPORT" | G8 is about a FILL and every one of those sites wanted a SELECTION's type. An ASCRIPTION satisfies no bound and needed none (`ENGINE-LIMITS.md` G8.7) |
| a `null` RECEIVER "that was never this family" | it is exactly the family, one position over: JLS 5.2 gives java's `null` the SLOT's type, and the funnel's own `this(null)` substitution is a slot with no argument list (G8.5) |
| `Segment$Base`'s super call names a MEMBER — the RENAME's doing | the rename was right; `MutableParamsTransform` wrote that name, leaving a constructor's delegation reading a `var` it inserts one line below (C14) |

So the standing caution this section has carried since wave 7 — *read a residue row before counting
it* — now applies to the REFUSED column too, and with a sharper test: a refusal is only as good as
the question it was refused against, and G8's was a fill nobody at those six sites had asked for.

**Where the next wave starts.** Wave 12 took the TEST SET to **0** — the six `BitFieldSet` rows were
K25's held-back `iterator()` met from outside, and K29's built phase obligation let the mapping that
closes them land free — so **every remaining error in this port is a MAIN-set one and there is no
second lane's residue to read any more**. The wall past the first zero is priced rather
than predicted (`ENGINE-LIMITS.md` K28), and it is now the ONLY thing between this port and its first
behavioural evidence: the suite is emitted, discovered, guarded and compiling, and gated on a figure
that is entirely the library's. On the MAIN set the position is unchanged: wave 9 took the
`collection-internal` lane's `DeclaredSubtype` half at the SLOT — `coerce` now reads a program-declared value's kind out of the phase's own re-parenting
record, transitively (K26) — so the lane reads **5** here and **0** on the test set, and what it
still counts is the five `SplitTypeVariable` rows: one type VARIABLE bound, inside one argument list,
to both ends of an edge `typeMap` has no image for. Closing those needs the coercion to run at the
INFERENCE site, because the formal has no head to coerce against, and that is the row a next wave is
working against on the MAIN set.

**On the TEST set the remaining family is a main-set residue read from a caller.** `tagLine` (12)
and `PlaceholderReplacer` (6) both CLOSED at wave 10 — the first a java lambda at a `Runnable` SAM
slot on an OVERLOADED callee, where scala types the literal as a `Function0` before the expected type
can be used (`ENGINE-LIMITS.md` G28), the second K23's own named bound METHOD REFERENCE at a mapped
member (`map::get` against `mutable.Map.get`, which returns an `Option`). The one
`PlainSegmentBuilder` residue closed at wave 11 (G12 at the emitter). What is left is
`BitFieldSet` (6) and nothing else — K25's held-back `iterator()` met by a `for` loop over a class
whose parent stayed `java.util.AbstractSet`.

Nothing left in this port needs a manifest entry, and no residue above is per-library policy.

#### The FLOOR the refusal ledger defines — 13 of the 18 after wave 14, and what the other 5 are

Twelve waves took 243 → 30 and every one of them closed a FAMILY. What is left divides into rows a
named refusal already answers and rows nobody has diagnosed, and the two are worth separating,
because a census that only counts is one an operator reads as *fifteen more waves*. **Nineteen of the
thirty were attributed to a refusal or a limit this repository has already measured and written down**
— closing any of them means re-opening that entry with a number, not finding a bug. **Wave 13 did
exactly that to SIX of them, and the lesson is the classification rather than the count**: G8's six
were refused against the wrong question (a FILL, where every site wanted a SELECTION's type), so the
table below now reads THIRTEEN refused:

| n | attributed to | what the refusal SAYS |
|---:|---|---|
| ~~6~~ | **LEFT THIS TABLE AT WAVE 13** — `ENGINE-LIMITS.md` **G8.7** | the row said *a partially-nameable F-BOUNDED class has no consistent fill*, which is true and was not the question these six sites asked. Every one is a SELECTION on `getBuilder()`'s result, so the argument may stay `Nothing` and what needs text is the type the selection reads — an ASCRIPTION, which satisfies no bound. This is the entry to re-read before treating a refusal row as a floor |
| 2 | **K25**'s residue — the in-program half, its two `collection-internal` rows CLOSED at wave 9 and the `ScopedDataSet#getKeys` PAIR at wave 12 | a `JavaCollection` formal or result meeting a `Buffer`/`Set`/own-`OrderedSet` value at a call the PORT declares. K25 closed the rule half (an override of a class file keeps java's formals), K26 closed `DeclaredSubtype`, and K29's widened `addAll` closed the pair whose BOTH parameters sat on the wrong side of a signature written for `mutable.Buffer` alone; what is left sits at a callee THIS PHASE MINTED, which carries no signature at all, and K26 records both the removed operand-only arm and its repair (mint the helpers with their signatures) |
| 5 | the same seam one level in, at a TYPE ARGUMENT — **all five on `collection-internal`** | `DataKey<Collection<Extension>>` retypes its argument to the shim while the value is an `ArrayBuffer`: one java subtyping edge that the mapping sends to two unrelated scala types. Wave 6 established this belongs WITH the six above rather than apart from them, and wave 8's lane counts it as `SplitTypeVariable` — one type VARIABLE bound to both sides inside one argument list |
| 3 | **K23**'s counted refusals | `listIterator` (2) and `spliterator` (1) hand back a JDK PROTOCOL rather than a value, and there is nothing to map them onto. The fourth row was the one bound method reference, and wave 10 BUILT it — the refusal had been priced at one site and there were seven (`ENGINE-LIMITS.md` K23) |
| 3 | **K5.7**'s `UninheritableTargets`, counted as `InexpressibleParent` | a class that IMPLEMENTS `java.util.Map.Entry` keeps java's parent, because the retyping's target is a `Tuple2` and no class can extend one |
| **13** | | |

The other **5** are open work with no entry behind them: **1** raw-generic one-off and the residue.
So the honest reading of 18 is *13 refused, 5 open*. Wave 13 took FIVE out of the open
column and SIX out of the refused one, in four commits; **wave 14's first commit took a sixth out of
the open column, and its diagnosis is this table's own standing lesson read once more.**
`TextCollectingVisitor` had been filed under its error text (`None of the overloaded alternatives of
constructor NodeVisitor`) as a raw-generic one-off, and the cause is neither raw nor generic: the
same `new NodeVisitor(h1…h6)` PACKS its vararg array one file away in `LineCollectingVisitor` and
does not here, because this one carries an ANONYMOUS BODY and the parser hands a synthesised
constructor to every rule keyed on the callee's declaration (`ENGINE-LIMITS.md` G29). That is the
SIXTH census row whose cause turned out to be a different defect from the one its text names.

Wave 13's four commits:

- the `Segment$Base`/`Segment$Text` PAIR (`ENGINE-LIMITS.md` C14) — a reassigned constructor
  parameter read by the DELEGATION, which the funnel then hoists into the `extends` clause where no
  class member is in scope. The FIFTH residue row whose cause was a different defect from the one
  its error text named;
- G8's SIX (`ENGINE-LIMITS.md` G8.7) — an unconstrained F-bounded RESULT, ascribed rather than
  instantiated. A type argument must satisfy `X <: ISequenceBuilder[X, T]` and none is denotable;
  an ascription need not, because the argument still infers `Nothing` legally and what the
  ascription supplies is the type the SELECTION reads;
- `IRichSequenceBase#equals` (`ENGINE-LIMITS.md` G8.9) — the frontend's OWN widening of a java
  `equals(Object)` parameter to `scala.Any`, forwarded to an `Object` slot. The third value scala
  types wider than `java.lang.Object` and the only one the port made rather than found;
- the two `null`-at-a-type-parameter rows the wave-8 fix did not reach (`ENGINE-LIMITS.md` G8.5) —
  both are slots with NO FORMAL to read, a lambda BODY (`DataValueFactory<T>`'s SAM result) and an
  INLINED `this(null)` (the funnel's own substitution, which handed a `Null` RECEIVER to
  `other.getAll()`). The second turned out to need a third fix underneath it: `samAbstracts` counted
  a `@Override` RE-DECLARATION as a second abstract method, so a functional interface java accepts
  a lambda for was not a SAM to this frontend at all.
**Wave 10 moved BOTH columns, which no earlier wave had**: G28's ascription
took one out of the open residue and K23's built arm took one out of the refused column and one more
out of the residue — the second being `Utils#withDefaults`, which the residue had filed under its
error text with a diagnosis that was wrong (§10.6.3's wave-10 row). **Wave 11 took a third out of the
residue the same way** — an `E006 Not found: type S` on `SequenceBuilder#append(char,int)` whose cause
was the EMITTER's own numeric-overload pin naming the callee's F-bounded result (G12), not the one-off
its text suggested. That is now the fourth census row whose cause turned out to be a different defect
from the one its text names, and it is the argument for reading a residue row before counting it.

**Wave 12 moved both columns again and did it by BUILDING a refusal rather than by finding a bug**,
which is the shape worth naming: the `BitFieldSet#iterator()` pair left the OPEN column (it was never
a refusal — K25 had made it loud, and K29's own first measurement had said the mapping that closes it
opens four worse rows), and the `ScopedDataSet#getKeys` pair left the REFUSED column, closed by a
signature widening nobody predicted would close anything. Both fell to the same commit sequence, and
neither of them is where the wave was aimed — it was aimed at the TEST SET, which went to **0**.

Two things this table is deliberately NOT:

- **it is not a claim that 19 is a floor forever.** G8 and K5.7 are expressiveness limits of the
  IMAGE this engine emits, and a different image (a `Tuple2`-shaped `Map.Entry` shim, a filled
  F-bound) would move them — each at the cost its entry already records. What the table says is that
  no row in it is a defect somebody has not looked at. **And wave 12 is the proof that a row can
  leave it**: `AbstractSet` was in nobody's table at all, and closing it moved this column by two.
  **Wave 13 is the stronger proof and a different one**: G8's six left because the REFUSAL was
  answering a question those sites never asked, so a row can leave this table without its entry
  being wrong about anything it actually says (`ENGINE-LIMITS.md` G8.7);
- **it is not a substitute for the counts — and that objection is now DISCHARGED.** Seven of the
  thirteen remaining are the collections family's INTERNAL seam, and `collection-boundary` — the lane that
  exists to count that residue — reads **21** and sees NONE of them, because it counts the external
  half. Wave 8 gave the internal half its own lane: `collection-internal` reads **5** here and **0**
  on the test set, at 0 errors moved and 0 member digests on any port report when it arrived
  (`ENGINE-LIMITS.md` K26). What it attributes is the five `MutableDataHolder.set` rows
  (`SplitTypeVariable` — one type variable bound to both sides of a broken edge); its two
  `DeclaredSubtype` rows (`OrderedMultiMap#keys`/`#values`) closed at wave 9. What it does NOT
  attribute is stated rather than averaged over: `Attributes#values` and `BuilderBase#extensions` sit
  at a callee THIS PHASE MINTED, which carries no signature, and the arm that read such a call from
  its OPERANDS alone was built, measured at 2 rows of which 1 was false, and removed — K26 carries
  the number and the repair (mint the helpers with their signatures). The `ScopedDataSet#getKeys`
  pair that used to sit beside them closed at wave 12 and, exactly as this bullet predicts, **no lane
  fell with it**.

**AND A DECISION PASS CANNOT TAKE THIS PORT TO ZERO — refused at wave 12, with the reason, because
it is the obvious next move and it is the wrong one.** Reaching 0 by declaring the residue is
mechanically available: every row above has an owner, and a `dropMethods` key per owner with a §1
classification and a porter note would empty the census in one commit. It is refused on `CLAUDE.md`
§1's own sentence — *an obligation THE ENGINE'S OWN TRANSLATION created is not a port's to
discharge* — and thirteen of the nineteen are exactly that: `Map.Entry` against `Tuple2` is the
retyping's target, the shim-against-a-scala-collection seam is the mapping's own split, `listIterator`
and `spliterator` are the mapping's counted refusals, and G8's F-bound is the emitted IMAGE. None is a
statement anybody could make about flexmark's surface. Three further costs, each of which is a rule
this repository already carries:

- **a drop of a member an emitted PARENT declares BREAKS an obligation rather than removing one**
  (§1, measured at K5.7): `IRichSequenceBase`'s six are interface members with implementors all over
  the library, and the classes would need to be abstract — which **nothing reports until the port is
  already at 0**, because `RefChecks` does not run before then. The drop would therefore buy a zero
  that the very next compile takes away, in members nobody was looking at;
- **the census would fall with nothing to attribute the fall to**, which is the shape §5 refuses on
  every lane: `19 → 0` by drops is indistinguishable from `19 → 0` by fixes, and the thirteen
  `ENGINE-LIMITS.md` entries behind those rows would lose the only instrument that keeps them honest;
- **the SUITE would run over a library missing the members its tests call**, so the discovery guard's
  `expected-lost` and the first pass/fail census would both be measuring a different program. A zero
  bought this way makes the behavioural evidence §3 exists for LESS available, not more.

What the port may honestly declare is what it already does: nothing here needs a `dropMethods` key, an
`excludeGlobs` entry or a scope, and §10.6.6 says so. The route to zero is thirteen engine entries and
six diagnoses, and it is a route the engine walks — not one this manifest can shortcut. **Wave 13 walked
eleven of them without a single manifest key**, which is the strongest evidence this argument has.

**And the port has not yet met `RefChecks`** (§3): 30 typer errors means it has never run, so every
missing `override`, unimplemented member and variance violation in 468 emitted files is unmeasured
and the count will RISE at the first zero. Wave 10 put the whole emitted shape through
`scala-cli compile --scala 3.8.4` rather than reading it by eye, and the wall is now PRICED and its
verdicts enumerated — `ENGINE-LIMITS.md` K28. **Nine errors per concrete class, in five distinct
verdicts, over SEVEN classes** (`OrderedMap`, `OrderedMultiMap`, `ItemFactoryMap`, `NodeRepository`,
`IndexedItemSetMap`, `OrderedSet`, `TrackedOffsetList` — read off the emitted text, not guessed), so
the collection wall alone is 30–50 errors and is not the whole rise.

**Two of the three shapes this section used to name were WRONG, and both were read by eye**, which is
the caution worth keeping more than the numbers. `NodeRepository`'s `override def keySet():
mutable.Set[String]` was called a §4.5 failure against scala's PARAMETERLESS `keySet` — it COMPILES,
and so does every other `()`-arity member on a `Map` (`size()`, `isEmpty()`, `clear()`, `values()`).
And `OrderedMap`'s `override def remove(o: java.lang.Object): V`, measured at wave 9 as a real `E038`,
needs one WORD removed rather than a member bridge: stripped of `override` it is an overload beside
scala's `remove(K): Option[V]`, which is exactly the pair K27's pin is written for. The same is true
of `entrySet`, `containsKey`, `containsValue`, `putAll` and `forEach` — five `E037 overrides nothing`
rows that a stripped modifier closes, measured.

What is genuinely open is smaller and sharper than the wall this section described: **two `E164
has incompatible type` rows** (`put(K,V): V` against `Option[V]`, and `iterator(): java.util.Iterator`
against scala's parameterless `Iterator` — same parameters, different RESULT, so no modifier helps),
**one `E164 cannot override final`** (`size()` on a `Buffer`, where `SeqOps.size` is final — the SAME
member that compiles on a `Map`, which is why the answer has to be a table and not a rule), and
**three abstract members to synthesise** (`get(K): Option[V]`, `addOne`, `subtractOne`). That is what
§4.5's *a parent adds MEMBERS* costs on the emission side, and it is the reason the first zero on this
port is a milestone and not an ending.

### 10.6.4 What the first run already taught, at 0 cost

- **A MARKER ANNOTATION IS CARRIED WHATEVER THE PORT CLAIMS, and it is 237 of 468 emitted files
  here.** `AnnotationPolicy`'s default claims no family and `SpoonTir.annotationsOf` gates on
  `claimed` only for an annotation WITH ARGUMENTS; a marker takes the unconditional arm. flexmark
  annotates 594 files with `org.jetbrains.annotations.{NotNull, Nullable}`, so the first compile read
  **1976 of 2184 errors** as `value jetbrains is not a member of org`. The lane now puts that jar on
  the compile line, which is a statement about what the port EMITS and not a translation fix — and it
  leaves two open questions with a measurement each: whether a marker whose family the port does not
  claim should be emitted at all, and the portability half beside it (this module claims all three
  platforms and `org.jetbrains:annotations` is a JVM-only jar).
- **`java_test_count` RETURNED THE EMPTY STRING, not `0`, when its path set matched no java file.**
  BSD `xargs -0` does not run its utility at all on empty input. Every lane before this one happened
  to pass a directory holding java — noise4j its `src`, jbump its whole checkout — so the counter
  always ran and the emptiness was unreachable; this is the first port whose scope has no `src/test`
  directory at all, and on its first run the "a suite has appeared upstream" alarm fired, naming no
  count, over a suite of zero. `test_discovery_guard`'s `$((java - scala))` would have been a bash
  error rather than a number. Fixed in `scripts/_lib.sh` by LOOKING FOR A FILE rather than
  defaulting the empty answer (§4.6): `${n:-0}` after the pipe would have covered a crashed counter
  with the same digit.

### 10.6.5 Do NOT retry

- **ARGUMENT INFERENCE as the source of a vararg pack's element type.** The packing itself SHIPPED at
  wave 6 (`ENGINE-LIMITS.md` G26, 58 → 49) taking the DECLARED component and rendering it through the
  inherited-formal lookup; what stays refused is the other branch, measured at **81 → 83 twice** —
  Spoon types a class literal as RAW `Class`, so `addNodes(Class<? extends Node>...)` inferred
  `Array[Class[?]]` against a formal declaring `Array[Class[? <: Node]]`, which is Ashley's 94-error
  shape. Do not re-derive javac's five cells and do not re-try either measured guard; the declared
  component needs neither.
- **Reading the hand port's deviations as milestone-1 policy.** Three of them are tempting and all
  three are deliberately absent from `main.conf` (D-md-5): `NullabilityTransform` at
  `Target.Wrapper("ssg.md.Nullable")` (300 hand-port files, 594 annotated upstream files — the
  highest-leverage entry this port will ever have, and it moves every emitted signature at once), a
  `scope { }` on `collections` (48 hand-port files keep `java.util` — the same shape that cost
  ssg-liquid `27 → 47` and then `27 → 51`, `ENGINE-LIMITS.md` K16), and a `bean-properties` entry
  (`Node.getParent()` → `def parent`). §3.5 is the rule: quote the reference port for the SHAPE, get
  a number before it becomes a manifest entry.
- **`BitField`/`EnumBitField`.** The hand port replaced java's enum reflection
  (`getDeclaringClass`/`isEnum`/`getEnumConstants`) with a `given` type class. That is a redesign, not
  a translation — §1(c) or a hand-written injection, never a rule to derive from one library.
- **`SegmentedSequenceTree`'s `ThreadLocal<Cache>`.** The hand port dropped it to a plain `var`, so
  thread confinement is simply gone. Correctness-relevant, and not a general "ThreadLocal → var" rule.
### 10.6.6 The test port — the first behavioural evidence this port has, and its first census

`just md-test-measure`, `balticporter/corpus/ports/ssg-md/test.conf`, `port-report/FlexmarkTestMigrate`.
A DEPENDENT of `main.conf` (`base = "main.conf"`), so it inherits `packageRenames` and both surface
phases and adds exactly one of its own — `test-framework`.

**The scope is a THIRTEENTH module, and that is not a scope slip.** The twelve modules this port
converts ship no `src/test` at all: the split `flexmark-util-*` libraries are tested from the
`flexmark-util` AGGREGATOR, whose own `src/main/java` is empty and whose pom depends on all eleven.
So `md-measure`'s discovery block goes on asserting its zero — a true statement about ITS scope — and
this lane counts a different tree. The two numbers are not each other's residue.

| | |
|---|---|
| files | **52 converted → 52 Scala test files** (0 dropped, 0 injected) |
| `@Test` → emitted | **723 → 723 munit registrations, `expected-lost` = 0.** Not the raw grep's 730: `java_test_count` is comment-aware and seven are commented out upstream. The discovery guard holds it in BOTH directions from the first run |
| scalac errors | **19 total on one compile of both source sets after wave 13 — 19 main, ZERO test, 0 elsewhere** (30 = 30 + 0 after wave 12; 40 = 34 + 6 after wave 11; 42 = 35 + 7 after wave 10; 63 = 38 + 25 after wave 9; 84 = 40 + 44 before that). Wave 10 closed twenty-one in two commits: G28's ascription took the twelve `tagLine` rows and one of the main set's, and K23's built bound-method-reference arm took six `PlaceholderReplacer` rows and two more of the main set's. Wave 11 closed two more with ONE fix, one on each side. Wave 13 closed eleven in five commits, every one of them main-set. Baselined at 19 against `FlexmarkTestMigrate`, which is the whole-compile figure; §10.6.3's 19 stays `md-measure`'s and is reproduced by that lane alone — **the two figures are equal now precisely because the test set is empty, and they are still two measurements** |
| **THE TEST SOURCE SET IS AT ZERO — wave 12** | The last six were ONE family and it was a MAIN-set residue read from a caller: `BitFieldSet`, K25's held-back `iterator()` met by a `for` loop over a class whose parent stayed `java.util.AbstractSet` (three `foreach is not a member`, three `Found: java.util.Iterator`). Wave 11 had measured the mapping that closes it and REVERTED it, because alone it opens four `super.<JDK default>` rows `mutable.Set` cannot answer; wave 12 built that obligation into the phase and the mapping landed free (`ENGINE-LIMITS.md` K29). `collection-boundary` on this lane 6 → 4, the two `ClassFileOverride` rows on `BitFieldSetTest#iterator` falling with the errors they named — the attribution §5 requires of a lane that falls. 6 member digests moved, all in `BitFieldSetTest`. **What this does NOT mean is that the suite runs**: the lane compiles BOTH source sets and gates the run on the whole figure, which is 30 |
| by owner | **0 of fifty-two files.** `HtmlAppendableBaseTest`, `HtmlBuilderTest`, `PlaceholderReplacerTest`, `PlainSegmentBuilderTest` and now `BitFieldSetTest` are all clean. Every wave from 9 onwards closed a test-set family by closing a MAIN-set one, which is the shape this table has been recording since it was written: no error in this suite was ever the suite's own |
| test-framework refusals | **26, every one reported by the phase with its §1 classification** — `@RunWith(Suite.class)` × 9 and its `@Suite.SuiteClasses` × 9 (aggregators that declare no `@Test`, so they move neither side of the discovery count), `@Rule` × 6 (`ExpectedException`; the field is emitted and NEVER APPLIED, so an expected throw propagates and MUnit records a FAILURE rather than a silent pass), and one hamcrest `Description`. `junit.framework.TestCase`'s static import is NOT among them: the phase's `AssertClasses` already names JUnit 3's assertion class |

**And the two families that make up 24 of those 26 are each ONE GUARDED translation away, which is
worth stating as a design rather than re-deriving.** Neither is built; both are engine (a) and
neither needs a manifest entry.

- **`@RunWith(Suite.class)` + `@Suite.SuiteClasses` (9 + 9).** The phase's advice — *a custom runner
  changes how tests are ENUMERATED, so the converted suite runs a different SET* — is exactly right
  for `Parameterized` and `Enclosed`, and it is the wrong sentence for an AGGREGATOR: `Suite` runs
  the classes it lists and nothing else, and every one of those classes becomes an MUnit suite the
  runner discovers on its own. So where **every FQN in `@Suite.SuiteClasses` is a class this run
  converts AND declares at least one `@Test`**, the aggregation is redundant and the honest emission
  is a DROP with a `Decision` saying so, not a class carrying an annotation nothing reads. That
  conjunct is the whole safety argument and it has to be checked rather than assumed: a listed class
  the port does NOT convert means the port runs FEWER tests than java, which is the one direction
  §5's discovery guard exists to catch, and a listed class with no `@Test` of its own is a nested
  aggregator whose own members have to be resolved first. Where either fails, the refusal stands
  with its current text. Cost: it moves emitted FILES (nine of this port's fifty-two) on six test
  lanes, and moves NEITHER side of the discovery count, which is what makes it measurable.

  **AND THE NESTING IS REAL, which wave 9 confirmed by reading the nine.** `UtilTestSuite` lists the
  eight OTHER aggregators and nothing else, so every FQN in it declares zero `@Test` and the guard's
  second conjunct fails on the FIRST pass — the drop has to be a FIXPOINT (*converted, and either
  declares a `@Test` or is itself droppable*), cycle-safe, or the one class at the root of the tree
  stays refused while its whole subtree goes. The other eight list real suites and pass as written.
- **`@Rule ExpectedException` (6).** The `@Test(expected = E.class)` row of `CLAUDE.md` §4.4, met at
  JUnit's other spelling — and with the same failure, one step louder: the field is emitted, nothing
  applies it, so the expected throw propagates and MUnit records a FAILURE where java recorded a
  pass. The mechanical image exists: `thrown.expect(E.class)` at statement position means java wraps
  the REST OF THE TEST, so the arm is `intercept[E] { <the remaining statements> }` — the shape the
  phase already emits for `@Test(expected)`. Four deltas to enumerate before it ships, each of which
  must be a guard or a counted refusal: `expectMessage`/`expectCause` (an added assertion, not a
  different wrap), a `thrown` reference the arm does not understand (refuse), an `expect` call that
  is CONDITIONAL rather than at statement position (refuse — java's rule is armed either way and
  scala's `intercept` is not), and the `@Before` methods this phase inlines at the head of each test
  body, which JUnit runs INSIDE the rule and which must therefore be inside the `intercept` too.

  **THE POPULATION WAS READ AT WAVE 9 AND MOST OF IT TAKES THE REFUSAL ARM, which changes what this
  design is worth rather than whether it is right.** Of the sites in this suite, `UtilsTest`'s five
  are the shape the design assumes — `thrown.expect(NullPointerException.class)` at the top level of
  the test body; `BitFieldSetTest`'s twelve pass a HAMCREST MATCHER
  (`ExceptionMatcher.match(IllegalArgumentException.class, "…")`), which is not a class literal and
  is the fifth delta nobody had listed — and `OrderedMultiMapTest`'s eight sit INSIDE a `while` loop,
  which is the conditional-position refusal exactly. So a shipped arm converts roughly one field's
  worth of tests here and REFUSES the rest, and the refusal population is what has to be reported
  (§3). Two more deltas the enumeration needs and did not have: JUnit's rules are the OUTERMOST
  statement (`BlockJUnit4ClassRunner.methodBlock` wraps `withRules` around `withBefores`/`withAfters`),
  so an `@After` that throws is compared against the expectation in java and sits in a `finally`
  OUTSIDE the `intercept` in the port; and a SECOND `expect` call in one test accumulates matchers in
  java, which one `intercept` cannot express. Both are refusals, and both are cheaper to state now
  than to re-derive.

**BOTH ARE STILL DEFERRED AT WAVE 12, and the reason is unchanged even though the number that used to
carry it has gone.** The TEST SET is now at **0** — wave 12 closed its last six with K29 — and the
suite still does not run, because the lane gates on the WHOLE compile and the MAIN set is at 30. So a
conversion's only evidence would still be that the emitted text changed shape, which is precisely the
evidence `CLAUDE.md` §3 says a transform of this kind may not ship on. Both also move emitted FILES on
six test lanes (nine of this port's fifty-two for the `Suite` drop, six fields for the rule), which is
a blast worth spending once a suite can validate it and not before. The order is therefore unchanged
and only its first step has moved: close the MAIN set, run the suite, then convert — at which point
`ExpectedException`'s five convertible sites become five tests that stop FAILING, which is a number,
rather than five statements that changed.

| `omissions` / `portability(emitted)` / `collection-boundary` / `overload-risk` | 13 / 14 / **4** / 1,655 — the boundary lane fell by two at wave 12, and both were the `ClassFileOverride` rows on `BitFieldSetTest#iterator` that the K29 mapping closed together with the errors they named |
| `collection-internal` | **0 after wave 9**, from 16 — every one of which was `DeclaredSubtype` and the SAME seam: `OrderedSet`, which `implements java.util.Set` and was therefore re-parented onto `mutable.Set`, handed to its own `addAll`/`retainAll` whose formal is the `Collection` target `JavaCollection`. scalac reported **9** of those 16 sites, because it stops after a few per statement, and all nine closed with the lane — the attribution `CLAUDE.md` §5 requires of a lane that falls (`ENGINE-LIMITS.md` K26) |
| `manifest` | **1** — `BaseMapUnverified`, and see below |

**THE SUITE DOES NOT RUN, and the lane says so rather than skipping the stage.** The run is gated on
0 errors exactly as `liqp-measure` and `sg-measure` gate theirs; at 30 it prints that 723 tests are
emitted and none of them runs. A lane that silently skipped that stage would read as a lane whose
tests passed. **And the gate is on the WHOLE compile, which is the right gate and is worth saying now
that this source set is clean**: the suite links against the emitted library, so 30 main-set errors
mean 30 declarations whose emitted form nothing has compiled — a suite run over them would be running
against a library that does not exist. A gate on this source set alone would have gone green at wave
12 and told nobody anything.

**What building it found in the BASE, which is the part worth carrying forward.** The first run
reported **459 fatal `manifest` findings** — one per type — saying the base's published map had no
entry for classes the base emits perfectly well. It did not: for seven waves the base had been
publishing `flexmark.src.main.java.com.vladsch.flexmark.ast.Heading` as the UPSTREAM name of
`ssg.md.ast.Heading`, because `PortMap.upstreamOf` reads the package off the java file's DIRECTORY
and D-md-1 makes this port's `sourceRoot` a 53-module checkout. 9,261 of 9,370 rows. Nothing could
see it — the column is read by a dependent and there was none — and the first dependent is the
instrument. `ENGINE-LIMITS.md` D11; `manifest` 459 → 1 with every other count flat. **And it was not
only this port**: fixing it moved 1,792 rows of `gdx-vfx`'s map too, whose `sourceRoot` spans that
library's `core/src` and `effects/src` — the same defect, equally invisible, and found by
`port_map_guard` on the corpus-wide run rather than by anything here.

**The residual 1 CLOSED at wave 8 — `manifest` 1 → 0, and it was D11's own insight read at a
PATH.** `BaseMapUnverified` said the base's map records source paths relative to ITS root, so all
**422 of 422** lay outside this dependent's resolution roots and `PortMap.freshness` could check
nothing — a gap every one of milestone 2's 29 dependents would have inherited. The fix adds no
schema column and guesses nothing: the declared package is a SUFFIX of the path-derived one by
construction (which is exactly what `upstreamOf` already exploits), and the package is in the map's
own `upstream` column, so a consumer derives the package-relative form from rows it already holds and
resolves it under the module roots it already has. Ambiguity DECLINES — two roots holding one
package-relative path could be two different files — so `Unverified` still means *I could not
check*.

**What the census does NOT yet include, and why it is not a skip.** Nothing in this tree needs a
`dropMethods` key, an `excludeGlobs` entry or a `public-field-accessors` scope, so `test.conf`
carries none — the residues above are the engine's, they are counted, and every one of the 723 tests
is emitted. The 114 `ComboSpecTestCase` subclasses, the CommonMark conformance oracle and
`flexmark-core-test` remain out of scope for milestone 1 and want `flexmark-test-util`, which is the
documented refusal §10.6.1 already states.


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
| 2.4 | the unportable-marker design's Stage 1, plus a forced test-correlation amendment | **shipped** — source map, member digests, scalac correlation and the **test-failure** correlation lane. **Stage 2 (the marker) is shipped too**: `Tree.Unportable` + `UnportableKind` + `MarkerCheck` + the emission gate + best-effort fences, landed together, with `SpoonTir.unsupported`'s two default arms as the first mint site and `markers.tsv` written for §6.3's marked-region lane to read. Zero markers mint on all fifteen lanes, so it was emission-neutral; what is still owed is the DEFINITION-level `SymTag`, four mint sites whose shape a term wrapper cannot take, and the correlation join — `DESIGN.md` §6.5 names each |
| 2.5 | three ad-hoc debugging techniques should become first-class | **shipped** — all three are flags or a printer |
| 3.1 | cross-port composition — blocking at sge's second module | **shipped** — `PortManifest` + `ManifestAgreement` (static and dynamic layers), and beyond the original design, the **port map** (`DESIGN.md` §5): a dependent now reads what its base *emitted*, not only what it *declared* |
| 3.2 | test-framework coverage was JUnit-4-shaped | **mostly** — `@After`, `@Ignore`, `@BeforeClass`/`@AfterClass` and the assertion set are handled (`ENGINE-LIMITS.md` X5), and the VOCABULARY and SCOPE gaps a second library found are closed too (X6: JUnit 3's two assertion classes, `assertThrows`, and the rewrite no longer skipping a class that declares no `@Test`). The target side is honestly **(b) with exactly one implemented policy value**: `intercept` and the curried `test(name){body}` shape are MUnit facts baked into the phase |
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
found); `balticporter/corpus/libgdx-overrides/**` as correct (c) content in the right place — the model for
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
`DESIGN.md` §6's markers are for — and the marker is now BUILT, which moves the sentence rather than
answering it. A marked region carries its `UnportableKind`, its catalog id and a ranked remedy list
whose first entry states the §1 kind; what an unmarked typer error carries is still nothing, because
the engine did not know it was going to be one. Closing the rest is a matter of MINT SITES, which is
why §6.5 stages them one at a time and each one is measured.

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
5. **Collection-map extensions** — retarget entries like `gdx Array<Integer>` → `ArrayBuffer[Int]`
   with wrapper unboxing (base-manifest). The `java.util.Comparator`→`Ordering` half is BUILT as
   `CollectionsTransform(retarget = …)` and is **ENABLED in the libGDX base manifest** since P2
   (§11.16 — 32 `Configured` rows, 110 members, zero refusals, every check and every suite
   unchanged); what it measured is that the
   `Collections.sort`→`sortInPlace` call-site table it was supposed to need does not exist —
   `sortInPlace` is not a `mutable.Buffer` member, and after the retarget the existing
   `JavaCollections.sort` arm is already correct (DESIGN.md §8.12).
**(c) Library rules, permanent injects, skip-then-patch (correctly not mechanized):**
- **Opaque types** — ~30 in sge core (GL handles, GL enums, `Pixels`/`Seconds`/unit types), zero
  emitted. The (b) mechanism exists (`PrimitiveToOpaqueTransform`); the knowledge is per-library
  config nobody has written yet. Extensions inherit the base's (§1.5). The ssg ports and the four
  small sge ports have ZERO opaque types — do not invent config there.
- **Per-TYPE renames, sub-packaging and nested-type flattening** — the same shape one line up. The
  (b) mechanism is BUILT (`PackageRenameTransform`'s `typeRenames` / `subPackages` /
  `flattenNestedTypes` / `allowPackageSplit`, `DESIGN.md` §8.7); no port enables any of it, and the
  knowledge of WHICH type is per-library. What a dry run against the reference hand ports says the
  policy would be, so it is not re-derived: **simple-graphs** —
  `flattenNestedTypes = ["…Connection$DirectedConnection", "…Connection$UndirectedConnection"]`
  (0 findings), `subPackages { BinaryHeap = internal, NodeMap = internal }` (0 findings), and
  `typeRenames { "…simplegraphs.Array" = "…simplegraphs.internal.InternalArray" }`, which needs
  `allowPackageSplit` beside it — `Array#strictResize` is `protected` and inherited by
  `algorithms.AlgorithmPath`, so the move takes the subclass out of the declaring package's subtree
  and the §8.7 qualifier must widen (1 refusal, or 1 recorded `package-split` widening). **liqp** —
  `typeRenames { "liqp.filters.Map" = "MapFilter" }` and the same for its `DateParser`; liqp is not
  in the corpus, so that pair is a candidate and not a measurement.
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

### 11.10b D1 — the published base surface, as measured

`DESIGN.md` §8.3 (and its AS BUILT subsection). Landed as one piece because the schema must not
change twice: the `shape` column and the header's `policy=` fingerprint ship together, the `Surface`
view lands with them, and two of the nine drift sites are migrated onto it.

**The measurements, and what each one settles:**

| | measured |
|---|---|
| every lane's `members.tsv` except gdx-gltf's | **0 members changed** — the contract is WRITTEN in this work and read at only two sites, so emitted text must be byte-identical everywhere the two reads do not bite |
| gdx-gltf | **9 members moved**, all of them `PBRCubemapAttribute` / `PBRTextureAttribute` and their constructors — the two classes whose parent's plan the fixpoint used to demote. Errors **7 -> 7** |
| every check count, every lane | identical |
| libGDX core's map | 605 units -> **983 type rows** (nested types now carried), 18620 member rows, **237** of them publishing an emitted `name=` that existed in no artifact before |
| gdx-gltf, ashley | **0** unanswered contract questions, so every base plan this run derives agrees with the row the base published |
| D6's cross-module face | **0** across the corpus, which is D6's own observation restated: libGDX emits 31 bare objects and no dependent names one as a type |

**Three things fired that nothing else in the pipeline could have seen**, and each is the mechanism
working rather than a surprise:

- **5 FATAL cross-check failures on the first dependent run, every one an ENUM and every one false.**
  `TirEmitter.enumDef` lowers a Java enum directly and consults the funnel for nothing, so the plan
  is `Plan.none` while the row records the constructor's real slots — two derivations compared.
  Excluded structurally; recorded in `DESIGN.md` §8.3 AS BUILT.
- **A determinism violation on exactly the two gltf wall classes.** The `Determinism.Emission` twin
  was built without the `Surface`, so it re-derived every base primary the pre-§8.3 way. The view is
  an INPUT to emission, not a report about it, and the determinism gate is what said so.
- **The `unrename` of a member name was built and measured INERT**, then deleted (§3.10). The §4.55
  passes rewrite `Symbol.name` and not `Symbol.fullName`, so the map's join key was already right;
  the EMITTED name was the missing half.

**What this does NOT close, stated rather than implied.** gdx-gltf's two wall-class errors survive
the seeding and cannot be removed by it — see §8.4, where the published row is quoted and the reason
is that a Scala `extends` clause reaches only the primary. D5's `vis=` row now exists on every member
and nothing reads it (4 errors). Seven of the nine drift sites are still bare `program.units` scans,
each correct for the subjects the emitter renders and wrong only as an answer about a base type; the
four latent behind default-off phases are the ordering constraint on Stage P5/P6.

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

**The 17 refusals, all of them correct, none of them silent — now 5.**

| cause | n | what it is |
|---|---|---|
| external anchor | 12 → **0** | `Selection`/`VertexAttributes`/`TiledMapTileSet`/`OrientedBoundingBox` implement `java.lang.Iterable`, `Comparable` or `Serializable`. `ExternalSurface.jdkPlatform` (D5j step 4) knows all three exactly, because the platform closes their member sets, so all twelve are free — 17 of 18 accessors anchored before, 0 after. `ENGINE-LIMITS.md` K12 holds the measurement and the rule that a DEMAND-DERIVED surface may not fill that map |
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
### 11.12 M3 — annotation-driven nullability, as measured

DESIGN.md §8.6, landed default-off as two commits with a thirteen-lane measurement between them.

**The (a) prerequisite, alone.** `SpoonTir.annotationsOf` was never called for a PARAMETER, so a
library's nullability contract — which it states mostly on parameters — reached **389 upstream sites
and 0 symbols** on the most-annotated port. The gap was invisible from both ends: nothing renders a
parameter annotation, so the emitted file is byte-identical with them and without, and no check can
report a symbol property that is never populated. Measured after the fix:

| | |
|---|---|
| emitted text | **0 members changed on twelve ports**; `LibgdxCoreMigrate` shows 4 rows, which are **2 member KEYS re-indexed with byte-identical digests** (`@1352#<init>(String,String)#…` → `@1353#…`) |
| check counts | identical on every lane. The only finding that diffs anywhere is the same re-index — one `portability(all)` row per lane, same count, same text, new id |
| tests | unchanged: gdx-test 217/4, ashley 108/2 (+2 pre-existing skips), anim8 23/0, screens 16/0, vfx 64/0, sg 16/0 |

**The re-index is intrinsic, not a defect to design around.** An external ANONYMOUS owner's
`fullName` embeds its raw `SymId`, and interning one annotation type earlier moves every id after
it by one. Nothing but a member key embeds an id, and the digest beside it is what says the emitted
text did not move. Nine baselines promoted; `LibgdxCoreMigrate/baseline/port-map.tsv` additionally
picked up a PRE-EXISTING staleness this work did not cause (three `Dropped`+`Added` pairs are now the
single `Substituted` row the current engine writes), which moves no check count.

**The phase, default-off.** `nullability` (§1(b)), both targets built — the union floor and the
wrapper — plus `nullability-boundary`. Every lane 0 members changed and every check count identical
with it absent, which is the (b) proof; 24 specs, of which the negatives are the load-bearing half
(vararg, primitive, argument-carrying annotation, non-value position, override-crossing under the
wrapper, uncoercible seam, unknown FQN never-fired, empty-config byte-identity).

**P3 dry run — `@Null` → union floor on libGDX core, bound, counted and COMPILED, then reverted.**
The phase was added to `mainPhases`, the port emitted and compiled, and the tree restored. This is
the UNSCOPED measurement, kept because it is what produced K13; §11.17 is what P3 actually shipped.
Measured:

| | |
|---|---|
| declarations retyped (`decisions.tsv` rows for `nullability:com.badlogic.gdx.utils.Null`) | **632** |
| positions moved | **707** — **182** returns, **389** parameters, **136** fields |
| `nullability-boundary` | **174** — 155 `AbstractTypeParameter`, 17 `NotAValuePosition`, 2 `VarargParameter` |
| `@sge.utils.Null` in the emitted Scala | **161 → 0**, every rendered marker consumed |
| `| scala.Null` in the emitted Scala | **0 → 632** |
| compile errors, libGDX core alone | **0 → 35** |

**389 is the census's parameter count EXACTLY, and it was 377 until the run corrected the engine.**
A parameter the reassigned-parameter transform demotes keeps its name on the method's `MethodType`
while its SLOT becomes `<name>$arg`, so matching the two lists by NAME moved the emitted parameter
and silently left the signature behind. Joined BY POSITION the two agree, and the number then
matches an independently-measured upstream census to the site. Nothing else in the run could have
shown it: the emitted text was right, every check was 0, and only the artifact disagreed with the
census.

**And the run REFUTED §8.6's central claim — see DESIGN.md §8.6, amended.** "N1 costs nothing at use
sites" is true at a CONCRETE reference type and false at an ABSTRACT one: `Null` is a subtype of
`String` and not of a `T <: Object`, which is the very reason a `return null` at a `T` return needs
a cast. **0 → 35 errors**, 34 of them `Found: T | Null / Required: T` and one an arity change at
`ObjectMap#get`'s defaulted overload — an overload-resolution movement the design said could not
happen. Every error is inside a generic type (`IntMap`, `LongMap`, `ObjectMap`, `OrderedMap`,
`Array`, `Queue`, `AtomicQueue`, `Tree`, `List`, `SelectBox`, `Selection`). The probes behind the
claim used `String`; none used a type parameter.

The engine's answer is to COUNT rather than refuse — the declaration is fine and the cost is
entirely at the uses — so `Issue.AbstractTypeParameter` flags all **155** annotated occurrences
whose type mentions an abstract type parameter, a deliberate over-approximation of the 35 that
actually fail. **P3 is therefore not a free enablement**, and it has three exits, all policy: scope
libGDX's generic containers out of `nullability`; accept 35 errors; or land N2
(`-Yexplicit-nulls -language:unsafeNulls`), under which the whole class disappears.

**One further interaction to measure at P3 rather than discover.** `TirEmitter.rawParentAlignment`
tests `hasWildcardArg`, which does not look inside a union, so an annotated parameter whose type
carries a wildcard AND overrides a parent method stops being aligned. It produced no error in the
dry run; it is named because a silent un-alignment is not the kind of thing a count would show.

**A pre-existing defect the lanes surfaced along the way, unrelated to this work.** A `trivia`
finding whose detail contains a TAB can never match its own baseline: the detail is written with
tabs normalised to spaces and the id is recomputed from the parsed row on read, so the same finding
is reported as removed-and-re-added on every run. Three rows in gdx-gltf, counts identical, and
`just baseline-accept` does not settle it because the next read re-derives the same mismatch.

### 11.12 M1 + P5 — globals → context: DELIVERED, on the fifth replay and after five engine gaps

**Read the top of this section for the mechanism and the bottom for the delivered census.** What is
in between is the record of four replays that were each measured and REVERTED, and it is kept
deliberately: every one of them found an engine gap that a compile could not (CT5, CT6), that a
compile could not even see (CT7), that only a DEPENDENT could produce (CT8), or that only a
dependent inside the base's own namespace could produce (CT9). The four "do NOT retry" paragraphs
are still exactly what they say; the exits they refuse have not become available.

DESIGN.md §8.4. The mechanism is DEFAULT-OFF: no port declares a holder, so **every one of the ten
lanes is 0 members changed and all 13 ports' check counts are identical**, and `context-seam` records
nothing at all — the phase returns its input before building anything, so the check is a no-op by
arithmetic exactly the way `collection-boundary` is for a port with no collections phase.

**The emitted output COMPILES, which is the only evidence that matters here.** The end-to-end fixture
— a holder, an interface with three implementors across a subclass chain, a caller through the
interface, an anonymous `Runnable` body, a field initialiser and a class initialiser — is written one
file per unit and put through `scala-cli compile --scala 3.8.4`: **0 errors**. That is the M2 lesson
applied: an anonymous `(using T)` resolving across ten emitted files is a claim about scalac, and a
string assertion is not evidence for it.

**The DRY RUN — the reference bundle's config bound against libGDX core, nothing enabled.** Holder
`com.badlogic.gdx.Gdx`, injected `sge.Sge`, the 11-field path map from the hand port (`gl*` routed
through `graphics`), in a throwaway process. Read the two columns against each other: they are the
argument for class attachment, in numbers.

| | `attach = method` | `attach = class` |
|---|---|---|
| threaded declarations (`RetypedSignature` rows) | **2,497** | **275** |
| distinct files touched | **324** | **177** |
| seams, total | **162** | **17** |
|  — `residual-global-read` | 83 | 4 |
|  — `captured-context` | 47 | 13 |
|  — `frozen-component` | **32** | **0** |
| refused components (§1(b) findings) | 15 | 0 |
| residual holder: fields dropped of 11 | 8 (`app`/`files`/`graphics` keep a reader) | 9 (`app`/`graphics`) |
| `DeferredInit` sites | 0 | 0 |

Four things follow, none of them re-derivable from R4's census alone:

- **Class attachment reproduces the reference port's size; method attachment does not.** R4 measured
  the hand port at **159 attachment files against 97 direct-reader files — 1.6×**. Class mode lands
  at 177/97 = **1.8×**; method mode at 324/97 = **3.3×**. The over-approximation method mode pays is
  real and it is roughly double.
- **`frozen-component` goes to ZERO under class attachment**, which is §8.4's prediction confirmed on
  real code rather than argued: class mode changes no method signature, so an override component
  anchored on `java.lang.Runnable`, `Comparable` or an unparsed parent is simply not a problem.
  Method mode refuses 32 declarations across 15 components — `RemoteInput#run`, `Timer$Task`,
  `TextField`'s listener bodies — every one of them a `Runnable`-shaped anchor.
- **`DeferredInit` is 0 in base, in both modes**, matching R4's census exactly: the corpus's one true
  class-initialiser read is `gdx-vfx`'s `VfxGLUtils`, a DEPENDENT. The eager→lazy machinery ships
  unexercised by libGDX core on purpose.
- **The residual holder really does mostly vanish**, derived and not configured: 8 of 11 fields lose
  their last reader under method attachment, 9 under class. What survives is what a boundary still
  reads.

*(The dry run runs the globals phase ALONE, so it does not see what the base's other surface phases
do to the same signatures. P5's numbers will move; the ratio between the two columns is the finding.)*

**`attach = "class"` EMITS — the refusal landed, and then so did the fix. 5 scalac errors → 0.** For
one release the TIR edit was complete and the emission was not: the constructor funnel undid the
clause three ways (`ENGINE-LIMITS.md` CT4), so the knob recorded a `PolicyIssue.Unverifiable` finding
naming all three rather than shipping code that does not compile. All three turned out to be one
thing — `paramss.flatten`, which answers *what does this constructor take* where the question is
*what did JAVA declare*. The funnel now models parameter GROUPS (`CtorFunnel.Plan.givens` beside
`primaryParams`), every nilary/pass-through/erasure question goes through `CtorFunnel.valueParams`,
and the emitter renders the clause as its own group through `paramClause`. **This phase gained no
code**, which is the evidence that the refusal was pointing at the right module.

Validated by RUNNING, not by asserting: the fixture is emitted one file per unit and put through
`scala-cli 3.8.4` in both modes at **0 errors**, and the synthesised-primary shape —
`class Panel protected (sup$0: Int, sup$1: Boolean)(using demo.Ctx)` reached by two secondaries and
by a subclass's argument-free `extends` — compiles and RUNS. At libGDX-core scale class attachment
emits **578 `(using sge.Sge)` clauses, 0 flattened into a value parameter and 0 synthesised empty
primaries** — CT4's first two causes read off the emitted text rather than argued.

The dry-run table above reproduces byte for byte after the fix (275 / 177 / 17 against 2,497 / 324 /
162, `frozen-component` 32 → 0, refusals 15 → 0, residual holder 8 → 9 of 11, `DeferredInit` 0 in
both). Class attachment is still DEFAULT-OFF and no port declares a holder, so every lane is 0
members changed.

**P5 — the ENABLEMENT was attempted, measured and REVERTED. It is blocked on one engine gap, and
everything else about it landed.** The base manifest declared the holder (`com.badlogic.gdx.Gdx`,
injected `sge.Sge`, the 11-field path map with `gl*` routed through `graphics`, `attach = "class"`,
`reader = "summon"`, `boundary = "refuse"`, no `promoteToClass`), with four `Graphics#gl2x ->
getGL2x/setGL2x` bean pairs beside it — a path segment is an identifier, so `graphics.gl20` can only
land on a member of that name. The run is the dry run reproduced at LIVE scale, beside the other
nine surface phases:

| | dry run (§11.12) | LIVE, whole pipeline |
|---|---|---|
| threaded declarations (`RetypedSignature`) | 275 | **275** — 188 classes + 87 methods |
| distinct java files threaded | 177 | **177** |
| seams, total | 17 | **17** — 13 `captured-context`, 4 `residual-global-read`, **0** `frozen-component` |
| refused components (`policy` findings) | 0 | **0** — `policy` stayed at its 2-row noise floor |
| residual holder: fields dropped of 11 | 9 (`app`/`graphics` keep a reader) | **9**, the same two |
| `DeferredInit` sites | 0 | **0** |
| emitted `(using sge.Sge)` clauses | 578 | **575** across 176 files |

Four things it settles, none of which the dry run could:

- **The 1.6× pricing holds.** 177 threaded files against the 100 files that name `Gdx.` upstream is
  **1.77×**, against the reference hand port's measured 1.6× and the plan's ≈1.6×. Method
  attachment's 3.3× is still the number that is wrong.
- **`GLProfiler` needs no drop and no §1(c) rule — CONFIRMED by compiling it.** The funnel promoted
  its primary and carried the clause (`class GLProfiler(graphics$p: sge.Graphics)(using sge.Sge)`),
  its ten global rebindings rewrote along the mapped path into the service's own setters, and its
  five re-sync lines became self-assignments — which is the reference hand port's conclusion (it
  deleted them by hand and recorded why) reached mechanically. 0 errors in that file.
- **D2 holds, which was the gate.** The dependent that reads no holder is **0 members changed and
  `context-seam` 0** on both its source sets; nothing the phase decided about the base's units
  reached the dependent's artifacts.
- **The test lane's first zero is confirmed**: `gdx/test` contains 0 `Gdx.*` references upstream.

**What blocked it AT THE TIME: 57 scalac errors, ONE cause, and an engine gap no manifest key
reaches.** (Closed since — see the replay below; the census is kept because it is what the fix was
measured against.)
`ENGINE-LIMITS.md` **CT5** — a class the funnel neither promotes nor synthesises keeps Scala's
implicit nilary primary, which carries no `using` clause, so the clause reaches only the `def this`
secondaries and the class body has no given in scope. 19 top-level classes plus at least 3 nested
ones of the 188 threaded: `Mesh` 14 errors, `IndexBufferObjectSubData` 11, `IndexBufferObject` 9,
`TextField` 7, `VertexBufferObject` 5, the three `GLFrameBuffer` builders, the four
`ScalingViewport` descendants, the five tiled renderers, `Table`, `TextArea`, `Pixmap`,
`ParticleEffectActor`, and `BitmapFont`/`DistanceFieldFont` as the two `E051` ambiguities. **55 of
the 57 are that cause.** The other two are the port's own boundary, not the engine's, and both have
a policy answer waiting: `TextField#DEFAULT_ONSCREEN_KEYBOARD` is one of the four counted
`residual-global-read` seams materialising as an error (a static field initialiser constructing a
now-threaded type — its exit is a `sites` entry), and the injected `sge/utils/Pools.scala` registers
factories for types whose constructors now take a context, which is a hand-written shim this port
owns.

**Do not try to buy the enablement with policy.** The two exits that look available are both wrong:
scoping the 22 classes out is a hand-maintained list derived from an emitter internal (it rots the
first time upstream adds a constructor) and it leaves the globals in exactly the heaviest `Gdx.gl20`
readers; `attach = "method"` is the mode §11.12 measured at 3.3× with 32 frozen components. The
revert is byte-for-byte — libgdx-core back to 0 errors and **0 members changed**, every check count
identical, `context-seam` gone.

**CT5 IS NOW CLOSED, and P5 replays unchanged: 57 → 3, with none of the 3 the engine's.** The
mechanism shipped default-off (`ENGINE-LIMITS.md` CT5, `DESIGN.md` §8.2's seventh as-built): a plan
with no primary of its own carries the context clause its own constructors carry, so a `Plan.none`
class emits `class X(using sge.Sge)` — hosting the clause, lifting no super argument, leaving every
secondary's delegation and every counted omission exactly where they were (`omissions` 65 → 65 in
the enablement run itself). It is CLAUSE-CONDITIONAL, so the mechanism commit is **0 members changed
on all 13 ports with every check count identical** and no baseline moved — the re-baseline the
enablement was priced with did not materialise, because nothing about the emitted text changes until
a port declares a holder.

**The enablement, replayed in a worktree with the policy applied and then reverted** (the P5 config
above, verbatim, plus the injected `sge.Sge` and the four `Graphics#gl2x` bean pairs):

| | P5, blocked | replayed after CT5 |
|---|---:|---:|
| scalac errors | **57** | **3** |
| — the missing clause on `Plan.none` classes | 55 | **0** |
| — the port's own boundary | 2 | **3** |
| `context-seam` | 17 | **17** (13 captured, 4 residual-global, 0 frozen, **0 `lost-clause`**) |
| `omissions` | 65 | **65** |
| every other check | baseline | **identical** |

The three that remain are one category and a half, both the PORT's:

- **two STATIC field initialisers that construct a now-threaded type** — `TextField#DEFAULT_ONSCREEN_KEYBOARD`
  (`new DefaultOnscreenKeyboard()`) and `Table#cellPool` (`new Pool<Cell>(){ … new Cell() … }`). A
  static has no constructor clause in scope, which is the boundary the phase already counts.
  **Only the first was named when P5 was measured — the second was inside `Table`, one of the 22
  classes whose 55 errors CT5 was producing, so it could not be seen until they cleared.**
- **one INJECTED shim**, `sge/utils/Pools.scala`, registering factories for constructors that now
  take a context. It is hand-written Scala this port owns, outside the source map by construction,
  and the correlator says so (`Unmapped`, not an engine gap).

**THE REPLAY WAS THEN RUN AS A DELIVERY, and it is STILL BLOCKED — on `ENGINE-LIMITS.md` CT6, which
is what measuring the exit rather than quoting it found.** Everything the plan priced reproduced,
to the row:

| | §11.12, blocked on CT5 | the delivery replay |
|---|---:|---:|
| threaded declarations | 275 (188 classes + 87 methods) | **275** — 188 + 87 |
| distinct java files threaded | 177 | **177** — 1.77× the 100 files that name `Gdx.` upstream |
| `context-seam` | 17 | **17** — 13 captured, 4 residual-global, 0 frozen, 0 `lost-clause` |
| refusals (`policy`) | 0 above the 2-row floor | **0** |
| holder fields dropped of 11 | 9 (`app`/`graphics` keep a reader) | **9**, the same two |
| `DeferredInit` | 0 | **0** |
| `omissions` | 65 | **65** |
| every other check | baseline | **identical** |
| libgdx-core blast | ~1,800 members | **1,799** |
| libgdx-test | 0 upstream `Gdx.` refs | **0** upstream, **0** emitted holder refs, `context-seam` **0**, 2 members |
| ashley — the D2 gate | 0 members, 0 seams | **0 members on BOTH source sets, 0 seams on both** |
| emitted `(using sge.Sge)` clauses | 575 in 176 files | **598 in 177 files** |
| scalac errors | 3 | **3** |

Two of those moved and both are explained. The clause count is **+23 in one more file** because that
is precisely CT5's fix arriving: the 19 top-level plus ≥3 nested `Plan.none` classes that used to
LOSE the clause now carry it. And the blast is 1,799 against the correlator's own 1,316, which count
different things — `just members-unchanged` is the digest baseline (the number to quote), the
correlator's is its own join.

**What blocks it is CT6, and it is two faces of one thing: the ESCAPE HATCH the engine names does not
exist.** Both were measured, not reasoned:

- **`Table#cellPool` is an error with NO seam.** `new Cell()` at a GENERIC class records a `Tycon`
  usage rather than an `Instantiate` one (`Xref.walkType`'s `AppliedType` arm re-labels the kind), so
  §8.4's instantiate edge is absent for every generic class — no threading, no `impose`, and
  therefore no `residual-global-read` row. 1 error, 0 seams, against the non-generic
  `TextField#DEFAULT_ONSCREEN_KEYBOARD` beside it at 1 error and 1 counted seam.
- **The `sites` `lazy-init` entry the seam's own diagnostic names does nothing.** Both keys were
  added and measured: emitted output **byte-identical** (1,799 members changed either way, 17 seams,
  `deferred-init` 0, 3 errors), and `policy` stayed at 2 — the keys BIND, so the never-fired
  machinery cannot see that they are dead. `ContextNeed.deferrals` is keyed on reads of a mapped
  static and `planDeferral` filters on `readsHolder(rhs)`; an initialiser that CONSTRUCTS a threaded
  type reads no holder and is in neither set.

**So the enablement is REVERTED again, byte-for-byte** — `just measure-all` exit 0, all 13 ports 0
members changed, every check count identical, `context-seam` gone, every suite outcome unchanged.
**Do NOT retry it as a policy exercise**: the two exits that look available are still the two §11.12
rejected (a hand-maintained scope list, `attach = "method"`), and the third that was believed to
exist — a `sites` entry — has now been measured not to.

**CT6 IS NOW CLOSED, and P5 REPLAYS AT 1 ERROR — which is the port's own shim and nothing else.**
The mechanism shipped default-off (`ENGINE-LIMITS.md` CT6): both readers ask the `Tree.New` NODE
what it CONSTRUCTS rather than the kind the shared index recorded, and the deferral's candidates come
from the `sites` entries the binder resolved rather than from reads of a mapped static. The
enablement, applied in a worktree with the P5 config above verbatim — the two `sites` `lazy-init`
keys included — and then reverted:

| | delivery replay, blocked on CT6 | replayed after CT6 |
|---|---:|---:|
| scalac errors | **3** | **1** — the injected `sge/utils/Pools.scala`, `Unmapped` |
| — a generic `new` with no seam (`Table#cellPool`) | 1 | **0** |
| — a static initialiser with no reachable `sites` exit (`TextField#DEFAULT_ONSCREEN_KEYBOARD`) | 1 | **0** |
| — the port's own injected shim | 1 | **1** |
| `context-seam` | 17 | **19** |
|  — `captured-context` | 13 | **14** |
|  — `residual-global-read` | 4 | **3** |
|  — `deferred-init` | **0** | **2** |
|  — `frozen-component` / `lost-clause` | 0 / 0 | **0 / 0** |
| threaded declarations | 275 (188 classes + 87 methods) | **275** — 188 + 87 |
| distinct java files threaded | 177 | **177** |
| `policy` | 2 (the `bean-properties` floor) | **2**, the same two |
| `omissions` | 65 | **65** |
| every other check | baseline | **identical** |
| blast (`just members-unchanged`) | 1,799 | **1,807** |
| emitted `(using sge.Sge)` clauses | 598 | **600** in 176 emitted files |

Four things it settles, and one it does not:

- **The two former errors are now COUNTED, which was the sharper half of the complaint.** The `+1`
  capture is `Table$114#newObject` — the anonymous subclass of `Pool<Cell>` that had no lexical home
  at all — and the two `deferred-init` rows are the `sites` keys FIRING for the first time. A
  boundary the engine cannot see is worse than one it refuses (CLAUDE.md §1); there are none left in
  this run.
- **`residual-global-read` 4 → 3 is a MOVE, not a fix.** `TextField#DEFAULT_ONSCREEN_KEYBOARD` left
  that lane for `deferred-init`. The 3 that remain — `GLErrorListener#LOGGING_LISTENER` ×2 and
  `ParticleShader$Setters#screenWidth` — are static initialisers that READ the holder, a different
  shape with the exits it always had (`boundary = "residual-global"`, or `lazy-init`).
- **`policy` did NOT rise**, and that is the report working rather than not firing: both entries
  selected a site, so neither is a dead binding. A third key naming something undeferrable would now
  be a `policy` row instead of silence.
- **`+8` blast and `+2` clauses are exactly the two deferrals** — a cache pair and a `def` each.
  Emitted-file count reads 176 against the java-file count of 177, which are different denominators;
  every one of the 167 threaded TOP-LEVEL types carries a clause and `lost-clause` is 0.
- **What is left is not the engine's.** `sge/utils/Pools.scala`'s eager registration block constructs
  types that now take a context, so it belongs behind a `def registerDefaults()(using sge.Sge)` the
  bootstrap calls. That is this port's hand-written Scala, and the correlator classifies it as
  `Unmapped` rather than an engine gap.

**THE THIRD REPLAY DELIVERED THE BASE AND WAS REVERTED ANYWAY — the shim fix works, libgdx-core reads
0 errors, and RUNNING the suites found three things no compile can.** The base module is finished
work: the config re-applied verbatim, the `Pools` block moved behind
`def registerDefaults()(using sge.Sge)` (see below for why the whole block moves), and **every number
of the census above reproduced to the row**.

| | CT6's proof run | the delivery replay |
|---|---:|---:|
| libgdx-core scalac errors | 1 (the shim) | **0** |
| threaded declarations | 275 = 188 + 87 | **275 = 188 + 87** |
| distinct java files threaded | 177 | **177** |
| `context-seam` | 19 | **19** — 14 captured, 3 residual-global, 2 deferred-init, 0 frozen, 0 lost-clause |
| `policy` / `omissions` | 2 / 65 | **2 / 65** |
| every other check | baseline | **identical** |
| blast (`just members-unchanged`) | 1,807 | **1,807** |
| emitted `(using sge.Sge)` clauses | 600 in 176 files | **600 in 176 files** |

**`Pools.registerDefaults` — the shape, and why it is the whole block.** Registration CONSTRUCTS:
`set(factory)` calls the factory once to learn the `Class` that keys the map, and `Net.HttpRequest` —
one of the 38 types upstream's `static { }` pre-registers — is one of the 188 threaded classes. An
object initialiser has no clause and no caller to take one from. Splitting the block (37 eager, one
deferred) is a hand-maintained list derived from which classes the closure happens to reach today, so
the whole block moves and the miss message names the method. The reference port never had this
problem and its shape says why: sge carries no context-free global pool registry at all —
`Actor.POOLS` and `Actions.ACTION_POOLS` register only context-free types, and its one
context-needing pool lives on `SgeHttpClient`, an INSTANCE that already holds the context.

**Then the suites ran, and this is what a green compile was hiding.** Two findings,
`ENGINE-LIMITS.md` **CT7** and **CT8**. Every count below is against that lane's OWN committed
baseline, which is what turned a would-be third finding into a non-finding:

| lane | errors (baseline → enablement) | classification | suite |
|---|---:|---|---|
| libgdx-core | 0 → **0** | — | — |
| **libgdx-test** | 0 → **0** | — | 217/4 → **212 / 5, and 5 baseline tests DID NOT RUN** — `AnimationControllerTest` lost whole (CT7) |
| **ashley — the D2 gate** | 0 → 0 | — | 108 / 2 + 2 skips unchanged, **0 members changed on BOTH source sets, `context-seam` 0 on both** |
| anim8 | 0 → 0 | — | **23 passing**, unchanged |
| gltf | 7 → **7** | **unchanged — all seven pre-existing**, `signature` 1 both ways | not run in either state |
| **vfx** | 0 → **43** | 2 `EngineGap` (CT8), 41 `Unmapped` (one hand-written suite) | 64 → not run, does not compile |
| **screens** | 0 → **16** | all `Unmapped` — 4 hand-written shims + 1 hand-written suite | 16 → not run, does not compile |
| sg / jbump / noise4j | unchanged | not affected: no libGDX dependency, so no manifest in reach of this policy | unchanged |

**gltf's seven were nearly written up as a third engine gap.** They are `EngineGap`-classified, they
are in gltf's own emitted code, and they look exactly like a base constructor gaining a clause and
breaking a dependent's `super(…)`. They are byte-identical in the reverted run. A dependent's error
COUNT is evidence about a change only after it has been diffed against that dependent's baseline —
the same rule §5.1 states for members, one artifact over.

- **CT7 is the one that decides it, and it is invisible to every count.** libgdx-test compiles at 0
  errors, `context-seam` 0, `policy` 0, and the emitted Scala is valid — while
  `class AnimationControllerTest(using sge.Sge) extends munit.FunSuite` cannot be instantiated by a
  test runner. Only §5.1's `tests.tsv` diff sees it. **This is the first time the enablement's tests
  were RUN rather than compiled** — the two earlier replays reverted before the suite — and it is
  CLAUDE.md §3 paying for itself.
- **CT8 is in a DEPENDENT, which is where the first two replays could not look.** vfx's four counted
  seams name an exit — `give the site a sites policy` — that a dependent has no manifest to write it
  in: the holder is inherited shared surface (§1.5) and `GlobalsToImplicitsTransform` is not
  `MergeablePolicy`, so a second instance is a fatal `SurfaceDivergence` and the base cannot name a
  dependent's types. Two of the four become scalac errors the correlator classifies `EngineGap`,
  correctly, because the port has nowhere to put the fix.
- **The port-side cost is now known and is not the blocker.** screens' 16 and 41 of vfx's 43 are
  hand-written `src/` Scala reading `Gdx.gl*` or constructing threaded types — the same category as
  `Pools.scala` and fixable the same way, plus a `SgeTestFixture`-shaped noop fixture for the two
  hand-written suites. Roughly six files. **A GENERATED suite cannot be fixed that way, which is
  exactly CT7.**

**So the enablement is REVERTED a third time, byte-for-byte** — `just measure-all` exit 0, all 13
ports 0 members changed, every check count identical, `context-seam` absent from every report.

**Do NOT retry it as a policy exercise.** The three exits that looked available for CT7 were each
walked to the wall and are tabulated in `ENGINE-LIMITS.md`; `scope` turns a lost suite into a compile
error, `sites` speaks about reads, and `attach = "method"` puts the clause in the same place for 3.3×
the cost. And do not spend a cycle re-deriving the numbers above for the base: it is finished and the
shim fix is the recorded shape.

**CT7 AND CT8 ARE NOW CLOSED, AND P5 REPLAYED A FOURTH TIME.** Both mechanisms
shipped DEFAULT-OFF and were measured alone: `just measure-all` exit 0, all ten lanes 0 members
changed, every check count identical, no baseline moved, on each commit.

- **CT7 — the THIRD ANSWER.** `selfSupplied` names a framework-instantiated TYPE and the expression
  that yields its context; the type keeps java's constructor signature and gets
  `private given <ctx> = <expression>` at the head of its body, which is sge's own shape
  (`private given Sge = SgeTestFixture.testSge()` on a no-arg suite) reached from policy. It is a
  RESOLUTION, not a refusal — the reads inside it stay threaded, so no global comes back — and it
  propagates nothing. Validated by RUNNING: the emitted probe goes through scala-cli 3.8.4 at **0
  errors**.
- **CT8 — the merge, and the split it needed.** `GlobalsToImplicitsTransform` is `MergeablePolicy`;
  the shared half must agree, `sites`/`selfSupplied` union refusing same-key-different-value, and a
  dependent writes a `ContextHolderExtension` — a value with no field in which the shared half could
  be restated, and a `holders` entry with no `context` block in the `.conf`. vfx's four seams now
  have a manifest.

**The `unconstructed-thread` warning is 1 on this port, and the dry run says why that is the ceiling.**
Replayed with the P5 holder, the globals phase alone over libGDX core reproduces **275 threaded
declarations in 177 files** exactly, and the new warning fires **once** —
`com.badlogic.gdx.input.RemoteInput`, which is honest (nothing in the library constructs it) and is
the "your users construct this" case the classification names. It does **not** fire on
`AnimationControllerTest`, because a JUnit class extends nothing in Java and its `munit.FunSuite`
parent is minted by a later phase; the two relaxed criteria that do see it read **60** and **74** of
188 threaded classes and are every leaf of the public API. The numbers and the "do not retry" are in
`ENGINE-LIMITS.md` CT7. **The detector of record for this class of loss remains `tests.tsv`'s
DID-NOT-RUN gate**, which is what found it.

**THE FOURTH REPLAY RAN THE WHOLE PORT-SIDE LIST AND IS REVERTED ON ONE MORE ENGINE GAP —
`ENGINE-LIMITS.md` CT9.** It is the first replay that reached the DEPENDENTS with a policy in hand,
and what it found there divides cleanly: the base is finished, CT8's mechanism works in production,
and the one dependent it does not work for is libGDX's own test module.

**The base DELIVERED again, and the census reproduced to the row.** The §11.12 config verbatim
(holder `com.badlogic.gdx.Gdx`, injected `sge.Sge`, the 11-field path map with the five `gl*`
two-hop through `graphics`, `attach = class`, `reader = summon`, `boundary = refuse`, the four
`Graphics#gl2x` bean pairs, the two `sites` `lazy-init` keys), plus the recorded
`Pools.registerDefaults()(using sge.Sge)` shape:

| | third replay (delivered) | fourth replay |
|---|---:|---:|
| libgdx-core scalac errors | **0** | **0** |
| `context-seam` — the BOUNDARY | 19 | **19** — 14 captured, 3 residual-global, 2 deferred-init, 0 frozen, 0 lost-clause |
| `context-seam` — the WARNING lane | (did not exist) | **+25 `unconstructed-thread`**, so the check reads **44** |
| blast (`just members-unchanged`) | 1,807 | **1,807** |
| emitted `(using sge.Sge)` clauses | 600 in 176 files | **603 in 178 files** |
| `policy` / `omissions` | 2 / 65 | **2 / 65** |
| every other check | baseline | **identical** |
| decisions | 3,890 | **3,893** (`DeferredInit` 2, `RetypedSignature` 1,246) |

**The 25 warnings are P1's redirect, not a new boundary, and the arithmetic is worth keeping.** CT7
measured this lane at **1** (`RemoteInput`) and called that its ceiling — but that dry run was the
globals phase ALONE. In the LIVE pipeline `disposableRedirect` re-points libGDX's own `Disposable`
at `java.lang.AutoCloseable`, so 24 more threaded classes acquire an ancestor this
program does not declare and meet the warning's second criterion. Every one of the 25 says
`extends java.lang.AutoCloseable which this program does not declare`, and every one is a leaf of
the public API — `Stage`, `AssetManager`, `ModelBatch`, the six tiled renderers — which is the
"your USERS construct this" case the classification names and not a defect. **A criterion measured
against one phase is not a measurement of the pipeline**; the honest ceiling on this port is 25, and
`context-seam` reads 19 + 25 = 44. anim8 adds 5 of the same shape (its five PNG writers) and vfx 16.

**CT8's `ContextHolderExtension` MERGED ON ITS FIRST PRODUCTION RUN.** vfx declared the extension
CT8 was designed for — `holder = "com.badlogic.gdx.Gdx"`, two `sites` keys in its own namespace —
and `manifest` stayed at **0**: no `SurfaceDivergence`, no `SurfaceIntrusion`, the per-declaration
half folded into the base's holder at the base's pipeline position.
`VfxFrameBuffer#tmpCam` FIRED as a `deferred-init` seam and its scalac error went with it —
**vfx `EngineGap` 2 → 1**. The other key is a `never matched` finding and the report is right:
`VfxGLUtils#<clinit>` READS the holder, it does not initialise a static from a threaded
construction, so `lazy-init` is the wrong site kind there and its exit is `residual-global`.

| lane | fourth replay | classification |
|---|---|---|
| libgdx-core | **0 errors**, census above | DELIVERED |
| **libgdx-test** | **BLOCKED — CT9.** `manifest` 0 → **1 FATAL**, `policy` 0 → 1, 2 scalac errors, suite still 212 / 5 with **5 DID NOT RUN** | (a) engine |
| **ashley — the D2 gate** | **0 members changed on BOTH source sets**, `context-seam` 0 on both, 0 errors, 108 / 2 + 2 skips, every check identical | HELD |
| anim8 | **0 errors, 23 passing** unchanged; blast 192; 5 seams, all `unconstructed-thread` | fine |
| gltf | **7 errors / `signature` 1 — PRE-EXISTING**, byte-identical to its own baseline; blast 208 main / 0 test | non-finding, as CT8 recorded |
| vfx | 42 errors = **1 `EngineGap` + 41 `Unmapped`**; `context-seam` 20 (1 deferred-init, 3 residual-global, 16 warnings); `manifest` 0 | port-side + the extension WORKS |
| screens | 16 errors, **all `Unmapped`, 0 `EngineGap`**; `context-seam` 10 | port-side |

**What CT9 is, in one line: a dependent whose OWN declarations live inside the base's `governs`
claim may not name one.** libGDX's suite is that dependent —
`com.badlogic.gdx.graphics.g3d.utils.AnimationControllerTest` shares a package with
`BaseAnimationController` — so the one `selfSupplied` entry CT7 exists to make writable is refused as
a `SurfaceIntrusion` before it can be applied. A dependent with a namespace of its own is unaffected,
which is why vfx merged and libgdx-test did not. Face B of the same entry is worse and was found by
the same run: a REFUSED merge leaves two same-name instances in the pipeline and `Pipeline.order`
keys phases by NAME, so only the later one runs — the base's whole holder silently did not run for
libgdx-test (0 `globals->implicits` decisions, the suite emitted with neither a clause nor a
`given`). Numbers, the three port-side exits and why each is worse than the wall: `ENGINE-LIMITS.md`
CT9.

**So the enablement is REVERTED a fourth time, byte-for-byte** — `just measure-all` exit 0, all 13
ports 0 members changed, every check count identical, `context-seam` absent from every report, every
suite outcome unchanged.

**Do NOT retry it as a policy exercise.** Everything port-side is now MEASURED and none of it is the
blocker: the base's config, the `Pools.registerDefaults` shape, vfx's extension and the fixture
expression all work. The three exits for CT9 Face A — put the entry in the base manifest, narrow
`governs`, drop the suite — are each measured worse in `ENGINE-LIMITS.md` CT9, and the first of them
buys a green run with six permanently unclearable `policy` rows, which is the noise floor this file
already warns about one section up.

**BOTH CT9 COMMITS HAVE LANDED, AND THE TRIGGER WAS RE-MEASURED AS A PROOF GATE.** Face B —
`Pipeline.order` orders instances, and a refused pair stops the run before the pipeline — and Face A
— the `governs` screen asks what the base EMITS, per its published map (`ENGINE-LIMITS.md` CT9,
`DESIGN.md` §8.13's two new as-built sections). Each shipped DEFAULT-OFF and was measured alone at
`just measure-all` exit 0, all 13 ports
0 members changed, every check count identical, no baseline moved. **P5 is ready for the FIFTH
replay, and what is left is entirely the port-side list below.**

The gate was the §11.12 config re-applied verbatim as UNCOMMITTED SCRATCH — the holder, the 11-field
path map, the four `Graphics#gl2x` bean pairs, the two `sites` `lazy-init` keys, the injected
`sge.Sge` and the `Pools.registerDefaults()(using sge.Sge)` shape — plus the ONE `selfSupplied` entry
that is CT9-A's trigger, then reverted:

| | fourth replay (CT9 open) | the CT9 proof gate |
|---|---:|---:|
| libgdx-core scalac errors | **0** | **0** |
| `context-seam` (base) | 44 | **44** — 25 unconstructed-thread, 14 captured-context, 3 residual-global, 2 deferred-init, 0 frozen, 0 lost-clause |
| decisions (base) | 3,893 (`DeferredInit` 2, `RetypedSignature` 1,246) | **3,893** — `DeferredInit` 2, `RetypedSignature` 1,246 |
| `policy` / `omissions` (base) | 2 / 65 | **2 / 65** |
| **libgdx-test `manifest`** | **1 FATAL `SurfaceIntrusion`** | **0** |
| **libgdx-test `policy`** | **1** (the extension's holder "neither it nor any of its bases declares") | **0** |
| libgdx-test `context-seam` | 0 — the base's holder never ran | **1 × `self-supplied`**, `AnimationControllerTest` |
| libgdx-test scalac errors | **2** | **1** |

Four things it settles, and one it deliberately does not:

- **Face A CLEARS ON THE REAL PORT.** `manifest` 0 against the base's published map — the screen
  found no entry at `com.badlogic.gdx.graphics.g3d.utils.AnimationControllerTest` and admitted it,
  which is the whole of CT9 Face A. The base still `governs = com.badlogic.gdx` and nothing about the
  claim moved.
- **Face B is visible in `policy` 0.** The dangling-extension finding was a CONSEQUENCE of the
  refused merge, not a second defect: with the merge standing, `effectiveHolders` folds the extension
  into the base's holder and the base's whole holder RUNS for libgdx-test. It cleared with no code
  aimed at it.
- **The entry APPLIES, and the emitted text is the evidence.**
  `class AnimationControllerTest extends munit.FunSuite` — no `using` clause — with
  `private given sge.Sge = sge.SgeTestFixture.testSge()` at the head of its body and the
  `injected-member` porter note above it. CT7's third answer, reached from a DEPENDENT's manifest.
- **The remaining 1 error is the PORT's, and it names itself**:
  `value SgeTestFixture is not a member of sge` at that `given`. The fixture file is the next row of
  the port-side list and was deliberately NOT written here — a proof gate that writes the port's
  fixture stops being a measurement of the engine.
- **The suite therefore did not RUN** (`not running the suite: it does not compile`), so the
  217/4 outcome line in that lane is the committed baseline echoed, not a result. The fifth replay
  is what runs it.

---

#### THE FIFTH REPLAY DELIVERED — the enablement is IN, and every suite in the corpus RAN

Five replays, five engine gaps, and then the port-side list landed exactly as the fourth replay had
priced it. **The base census reproduced to the row on the FIRST run of the fifth replay**, which is
what four reverted replays' worth of measurement was for.

| | fourth replay (CT9 open) | the CT9 proof gate | **DELIVERED** |
|---|---:|---:|---:|
| libgdx-core scalac errors | **0** | **0** | **0** |
| `context-seam` (base) | 44 | 44 | **44** — 25 unconstructed-thread, 14 captured-context, 3 residual-global-read, 2 deferred-init, **0** frozen-component, **0** lost-clause |
| threaded declarations | 275 (188 classes + 87 methods) | — | **275** — 188 + 87, in **177** java files |
| blast (`just members-unchanged`) | 1,807 | — | **1,807** |
| decisions (base) | 3,893 | 3,893 | **3,893** — `DeferredInit` 2, `RetypedSignature` 1,246 |
| `policy` / `omissions` (base) | 2 / 65 | 2 / 65 | **2 / 65** |
| every other base check | baseline | baseline | **identical** |
| libgdx-test `manifest` / `policy` | **1 FATAL** / 1 | 0 / 0 | **0 / 0** |
| libgdx-test `context-seam` | 0 — the holder never ran | 1 × `self-supplied` | **1 × `self-supplied`** |
| libgdx-test scalac errors | 2 | 1 (the fixture was deliberately not written) | **0** |
| **libgdx-test SUITE** | **212 / 5, and 5 DID NOT RUN** | not run — it did not compile | **217 passing / 4 failing, 221 of 221 emitted tests with an outcome, DID-NOT-RUN 0** |

**The `(using sge.Sge)` clause count is 604 occurrences across 178 files**, of which **600 in the 176
EMITTED files** — CT6's census to the occurrence — plus 4 in the two injected ones, and two of THOSE
four are prose inside `sge.Sge`'s `@implicitNotFound` message. Quote the emitted number; a grep for
the clause counts the sentence that explains it too.

#### The five recovered tests, one at a time

CT7 cost `AnimationControllerTest` whole — five tests, at 0 scalac errors and 0 findings, visible
only to `tests.tsv`. All five run again and all five **pass**, byte-identical to the committed
baseline. They are scrutinised individually rather than counted, because a pass under an
ABSENT-service fixture is a claim about what the test touches:

| test | what it exercises | why a pass is honest here |
|---|---|---|
| `testGetFirstKeyframeIndexAtTimeNominal` | `BaseAnimationController.getFirstKeyframeIndexAtTime` over four keyframes, seven probes | pure binary search over an `Array[NodeKeyframe]`; no service is on the path |
| `testGetFirstKeyframeIndexAtTimeSingleKey` | the same, one keyframe | as above |
| `testGetFirstKeyframeIndexAtTimeEmpty` | the same, zero keyframes | as above |
| `testEndUpActionAtDurationTime` | `AnimationController`'s loop→action→loop state machine over `new ModelInstance(new Model())` | `Model` is threaded and carries the clause for PROPAGATION only — the emitted body dereferences the context nowhere, and an empty `Model` loads no asset. An absent `graphics`/`files` would NPE at the field if it did |
| `testEndUpActionAtDurationTimeReverse` | the same at a negative speed | as above |

The emitted head of the suite is CT7's third answer, reached from a DEPENDENT's manifest:

```scala
/* porter: injected-member reason=configured phase=globals->implicits key=…AnimationControllerTest
   from="a constructor clause the closure would otherwise have attached" given=sge.Sge
   source=sge.SgeTestFixture.testSge() to="a `private given sge.Sge` member of this type" — … */
class AnimationControllerTest extends munit.FunSuite {
  private given sge.Sge = sge.SgeTestFixture.testSge()
```

No `using` clause on the class, java's constructor signature intact, and the note says which manifest
key to edit.

#### The whole corpus, and the D2 gate

| lane | errors | `context-seam` | suite |
|---|---:|---|---|
| **libgdx-core** | **0** | **44** (19 boundary + 25 warning) | — |
| **libgdx-test** | **0** | **1** × `self-supplied` | **217 / 4**, 221 of 221 RUN |
| **ashley — the D2 gate** | 0 | **0 on BOTH source sets** | 108 / 2 + 2 skips, 112 of 112 accounted; **0 members changed on BOTH source sets** |
| anim8 | 0 | 5, all `unconstructed-thread` | **23 / 23** |
| gltf | **7 — PRE-EXISTING**, `signature` 1 | 7, all `unconstructed-thread` (0 on its test set) | not run in either state (§8.4) |
| **vfx** | **0** (was 42) | 20 — 16 warning, 3 residual-global-read, 1 deferred-init | **64 / 64** |
| **screens** | **0** (was 16) | 10, all `unconstructed-thread` | **16 / 16** |
| sg / jbump / noise4j | unchanged | — | unchanged — no libGDX dependency, so no manifest in reach of this policy; **0 members changed**, byte-identical |

**The corpus-wide seam census is 87, and it reconciles: 24 BOUNDARIES and 63 WARNINGS.** The
boundaries are libgdx-core's 19 (14 captured-context, 3 residual-global-read, 2 deferred-init),
libgdx-test's 1 `self-supplied` and vfx's 4 (3 residual-global-read, 1 deferred-init). The warnings
are `unconstructed-thread` — 25 base, 16 vfx, 10 screens, 7 gltf, 5 anim8 — and every one of them is
a public-API leaf whose USERS construct it, which is the case the classification names and not a
defect. **`frozen-component` is 0 and `lost-clause` is 0 on every port**, which are the two counts
that would say the mechanism had refused something or dropped a clause it attached.

**gltf's seven are the same seven, and that is checked rather than asserted**: 2 × `E134` on
`PBRCubemapAttribute`/`PBRTextureAttribute`, 4 × `E008` on `ModelInstanceHack#copyNodes`, 1 × `E007`
at `MeshLoader`'s `toArray(VertexAttribute.class)` — §8.4's table, entry for entry, with `signature`
still 1. A dependent's error COUNT is evidence about a change only after it has been diffed against
that dependent's own baseline (`CLAUDE.md` §5.1, one artifact over).

#### Decisions, before → after

`just decision-counts`, every port, reverted state → delivered:

| port | before | after | what moved |
|---|---:|---:|---|
| libgdx-core | 3,598 | **3,893** | `RetypedSignature` 971 → 1,246 (the 275 threaded declarations), `DroppedMember` 16 → 25 (the nine holder statics that lost their last reader), `RenamedMember` 1,250 → 1,258 (the four `Graphics#gl2x` bean pairs), `InjectedMember` 10 → 11, `DeferredInit` 0 → **2** |
| libgdx-test | 283 | **284** | `InjectedMember` 0 → 1 — the one `selfSupplied` `given` |
| ashley (both sets) | 186 / 219 | **186 / 219** | nothing. This is the D2 gate stated in the provenance artifact rather than in members |
| anim8 | 748 | **705** | `RetypedSignature` 666 → 623: fewer of anim8's declarations are retyped, because the base now expresses through a context what anim8 used to see as a `Gdx` read |
| gltf (both sets) | 2,048 / 47 | **2,065 / 47** | `RetypedSignature` 1,750 → 1,767 |
| screens | 172 | **186** | `RetypedSignature` 56 → 70 |
| vfx | 414 | **415** | `DeferredInit` 0 → **1** (`VfxFrameBuffer#tmpCam`), `SubstitutedBody` 1 → **2**, `RetypedSignature` 262 → 261 |

#### What the PORT had to write, and the rule it produced

Everything below is `src/` or manifest — no engine code changed in the delivery commit.

| where | what | category |
|---|---|---|
| `LibgdxPolicy.globalsToContext` | the holder: `com.badlogic.gdx.Gdx`, injected `sge.Sge`, the 11-field path map with the five `gl*` two-hop through `graphics`, `attach = class`, `reader = summon`, `boundary = refuse`, two `sites` `lazy-init` keys, no `promoteToClass`, no `scope` | attach-clause |
| `LibgdxPolicy.beanPropertyPairs` | four `Graphics#gl2x -> getGL2x/setGL2x` pairs — a path segment is an identifier, so `graphics.gl20` needs a member of that name | reader-rewrite |
| `balticporter/corpus/libgdx-overrides/sge/Sge.scala` | the INJECTED context type: the six services, `@implicitNotFound`, a public constructor and the `apply()` sugar. NOT minted — it is published API and the reference port's shape | hand-written |
| `balticporter/corpus/libgdx-overrides/sge/utils/Pools.scala` | the whole `static { }` block behind `def registerDefaults()(using sge.Sge)`; `Pools.get`'s miss message names it | deferred |
| `LibgdxPolicy.selfSuppliedSuites` (libgdx-test) | ONE `ContextHolderExtension` `selfSupplied` entry → `sge.SgeTestFixture.testSge()` | self-supplied |
| `ported/sge/src/test/scala/sge/SgeTestFixture.scala` | the ABSENT-service fixture, plus that directory on the `gdx-test-measure`, `vfx-measure` and `screens-measure` compile lines | hand-written |
| `VfxPolicy` — a `ContextHolderExtension` | `VfxFrameBuffer#tmpCam` → `lazy-init`. `VfxGLUtils#<clinit>` deliberately gets NO key: it READS the holder, so `lazy-init` is the wrong site kind and was a `never matched` finding when it was tried | deferred |
| `VfxPolicy` — a second `MethodBodyTransform` entry | `VfxGLUtils#<clinit>` → `{ }`, and the construction moves to `VfxFrameBuffer#getBoundFboHandle` behind a null guard | boundary-exit |
| `ported/sge-vfx/src/test/.../VfxFrameBufferSuite.scala` | one `private given sge.Sge` line — it is what turns "this suite stops at the first GL call" from a comment into an NPE | hand-written |
| `ported/sge-screens/src/main/.../NestableFrameBuffer.scala` | `(using sge.Sge)` on both constructors; three `Gdx.gl20` reads become `summon` | attach-clause |
| `ported/sge-screens/src/main/.../QuadMeshGenerator.scala` | `(using sge.Sge)` on all three overloads (it constructs a `Mesh`) | attach-clause |
| `ported/sge-screens/src/main/.../ShaderProgramFactory.scala` | `(using sge.Sge)` on the three `fromString`s, on `ShaderCompatibilityHelper.fromString`, `mustUse32CShader` and `getDefaultShaderVersionStatement`; `Gdx.gl30` becomes `summon` | attach-clause |
| `ported/sge-screens/src/main/.../GLUtils.scala` | `Gdx.gl` → `Gdx.graphics.gl20` — the RESIDUAL global, and NOT a clause | seam |
| `ported/sge-screens/src/test/.../ScreenmanagerSuite.scala` | one `private given sge.Sge` line | hand-written |

**The last two screens rows are the finding, and it is now a rule (`CLAUDE.md` §1b).** `GLUtils` and
`NestableFrameBuffer` are two shims in one directory, and they take opposite answers — because the
answer is READ OFF THE GENERATED CALLER and not chosen. `NestableFrameBuffer`'s only caller is
`ScreenManager.createFrameBuffer()`, an instance method of a threaded class, so a clause is available
there. `GLUtils`' only caller is `ScreenFboUtils.retrieveFboStatus()`, which the closure did NOT
thread — it sees a call to an EXTERNAL guacamole symbol and cannot know the shim behind it reaches
the global — and that caller is GENERATED, so no manifest key and no hand edit can give it one. A
clause on `GLUtils` would therefore have emitted a port that does not compile, in the file that looks
most like the one that should have it.

**And vfx's `<clinit>` is the same rule for a BODY SUBSTITUTION**: it may change what a member DOES
and never what it TAKES. `MethodBodyTransform` could not give `VfxGLUtils.getBoundFboHandle()` a
context (the closure threaded nothing there — its body reads no holder), so the construction moved
one member further out, to `VfxFrameBuffer#getBoundFboHandle`, whose enclosing class carries the
clause. That is the reference hand port's own shape — `initExtension()(using Sge)` called from
`getBoundFboHandle()(using Sge)` — landing one member out because the emitted signature is not the
port's to choose. The seam is COUNTED either way: `VfxGLUtils#<clinit>` reports
`unsuppliable use: this declaration uses DefaultVfxGlExtension, which now takes a context`, which is
the boundary naming its own exit.

#### The residues, all named

- **25 `unconstructed-thread` warnings on the base, 16 on vfx, 10 on screens, 5 on anim8** — the
  `TypeRedirectTransform` interaction §11.12 already explains, and every one is a public-API leaf
  whose USERS construct it. They are warnings, not boundaries.
- **3 `residual-global-read` on the base** — `GLErrorListener#LOGGING_LISTENER` ×2 and
  `ParticleShader$Setters#screenWidth`, static initialisers that READ the holder. Their exits are
  `boundary = "residual-global"` or a `sites` key; neither is taken, because both would change what
  the emitted code names for no behaviour.
- **`sge.Gdx` still exists, with `app` and `graphics`** — 9 of 11 statics lost their last reader and
  are dropped with a porter note each. The two that remain are what the boundaries above still read,
  and `screens`' `GLUtils` is the one hand-written file that deliberately joins them.
- **2 `policy` rows** — the `ScrollPane#scrollX`/`scrollY` bean pairs, unchanged and unrelated.

#### Do NOT retry

- **Do not put the `selfSupplied` entry in the BASE manifest.** It buys a green run and six
  permanently unclearable `policy` rows, one per module that inherits a key it can never match.
- **Do not give `GLUtils` a `(using sge.Sge)`**, and do not "fix" the residual `Gdx.graphics.gl20`
  read in it. Its generated caller is not threaded and cannot be made so.
- **Do not give `VfxGLUtils#<clinit>` a `sites` `lazy-init` key.** Measured: `never matched`. It
  reads the holder; it does not initialise a static from a threaded construction.
- **Do not soften `SgeTestFixture` into noop services.** The one suite it reaches touches none, and
  a fixture that answers everything can never fail — which would make the threading unmeasurable at
  exactly the place CT7 proved a compile is blind.

### 11.13 D5j — the demand-derived JDK surface, as measured

DESIGN.md §8.9, landed as three commits. `ExternalUsage` (step 1) is the enumeration
`PortabilityCheck` used to perform inline and discard; `JdkSurfaceCheck` (step 2) classifies it;
step 3 wires both into `PortRun` — `jdk-surface` in `RequiredChecks`, `external-surface.tsv` in
every run directory. **0 members changed on all 13 ports**, every other check count identical: the
whole delivery is a report over a walk that already ran.

**The initial classification, per port.** This is the number the design asked for — how much
error-driven JDK coverage never got recorded anywhere a first run could read.

| port | classified | shimmed | mapped | mappable | kept | **findings** |
|---|---:|---:|---:|---:|---:|---:|
| libgdx-core | 631 | 2 | 13 | — | 592 | **24** unhandled |
| libgdx-test | 58 | 2 | 8 | — | 45 | **3** unhandled |
| ashley | 10 | — | — | — | 10 | 0 |
| ashley-test | 10 | 2 | 1 | — | 7 | 0 |
| anim8 | 83 | — | — | — | 76 | **7** unhandled |
| gltf | 93 | — | — | — | 93 | 0 |
| gltf-test | 1 | — | — | — | 1 | 0 |
| vfx | 36 | — | 3 | — | 32 | **1** unhandled |
| simple-graphs | 54 | 5 | 11 | — | 32 | **6** unhandled |
| simple-graphs-test | 14 | — | 4 | — | 9 | **1** unhandled |
| noise4j | 53 | 1 | — | **25** | 27 | **2** kept-iterable |
| jbump | 55 | — | 12 | — | 36 | **7** unhandled |
| screens | 8 | 2 | 1 | — | 5 | 0 |

**51 findings across 13 ports**, and the shape of them is the result. noise4j is the only port with
`mappable` rows, because it is the only one that runs no collections phase — 25 members it could
map if it wanted to, reported as an OFFER and not as a wall (`ran` is a parameter of the check,
not a property of the phase). Everywhere else the findings are `unhandled`: a member on a type the
phase RETYPED, with no entry in its tables. Those divide into two kinds and both are worth having:

- **coverage by coincidence** — `List#contains`, `Map#clear`, `Set#contains`, `ArrayDeque#clear`.
  The emitted call compiles because Scala happens to spell the member the same way. Nothing
  recorded it, and nothing would notice it changing.
- **a hole in a family the phase does rewrite** — `Arrays#fill`/`sort`/`copyOf` beside the
  `Arrays#asList` it maps, `Collectors#joining` beside the `toList`/`toCollection` it collapses,
  `Collections#addAll` beside `sort`/`swap`/`shuffle`. `Arrays#fill` alone is 114 call sites in
  anim8 and had never appeared in any report.

**Two false-positive classes were removed before the baseline, not after.** The first run reported
**28** on libgdx-core; the number is 24, and the difference is CONSTRUCTORS. A `new HashMap<>()` on a
retyped type emits `new scala.collection.mutable.HashMap()` and calls nothing java at all — retyping
the type IS the rewrite for `new`, and the arity correspondence between the two constructors is the
phase's business (ENGINE-LIMITS K11 is exactly that correspondence failing and being fixed). Left in,
18 `#<init>` rows sat in front of the `clear`/`contains` rows that are the real work list. The second
is `Kept`: 592 of libgdx-core's 631 rows are members nothing in the port claims, `java.lang.Math#max`
among them, and reporting those would have made the check the thing whose false positives you learn
to skip.

**The refusal table fires nowhere on this corpus, and that is the honest state.** Its seven entries
(`Collections#unmodifiable{List,Set,Map}`, `Map.Entry#setValue` in both spellings,
`Collectors#toSet`/`toMap`) name members no port still references after its pipeline. They were
written down because they existed only in doc comments and `case _ => None` arms, where no run could
report them; the day a library calls one, the row says why and cites where.

**K9 is closed as an invisible problem.** noise4j's two enhanced-for errors are exactly two derived
`kept-iterable` findings, at the loops rather than at the enclosing members the compiler names.
Every other port reports zero, which is arithmetic: they all retype, so no receiver survives in
`java.*`. See ENGINE-LIMITS K9.

**Step 4 — K12's twelve frozen properties, and the surface that did NOT fix them.** The seam K12
named is `ExternalSurface(known)`, and it was right; the value that fills it is not the demand-
derived surface this step was queued to wire. `ExternalUsage`'s rows say what a program CALLS and
`known`'s contract is that a present type is answered EXACTLY — so an absence from the rows lifts an
anchor on evidence that was never there, and on this corpus it would have lifted the same twelve for
the wrong reason. What landed instead is `ExternalSurface.jdkPlatform`: the eight platform types
whose member sets the JDK CLOSES (`Serializable` and `Cloneable` declare nothing, `Comparable`
declares `compareTo`, `Iterable` declares three), arity-only so it over-matches in the refusing
direction, §1(a) for the same reason `java.lang.Object`'s member set is. Rebuilding libGDX core's
graph under each surface: **17 of 18 accessors anchored before, 0 after — 12 of 12 properties
freed**, with 0 members changed and every check count identical on all 13 ports, because
`bean-properties` is still default-off. `java.util.Comparator` is deliberately NOT in the table and
still anchors: its default methods grew across releases, and an incomplete entry is worse than none.

### 11.14 D4t — the trivia hybrid, as measured

`DESIGN.md` §8.8 (+ §8.8 AS BUILT). Three mechanisms, one commit each, `lost = 0` on every port.

| port | lost, before | lost | recovered | deliberate |
|---|---|---|---|---|
| libGDX core | 100 | **0** | 4 | 12 |
| libGDX tests | 69 | **0** | 0 | 0 |
| anim8 | 34 | **0** | 0 | 0 |
| gdx-vfx | 11 | **0** | 2 | 0 |
| gdx-gltf | 10 | **0** | 4 | 0 |
| screens | 7 | **0** | 1 | 0 |
| simple-graphs / its suite | 1 / 1 | **0** | 0 | 0 |
| ashley, ashley-test, gltf-test, jbump, noise4j | 0 | **0** | 0 | 0 |
| **corpus** | **233** | **0** | **11** | **12** |

**Per mechanism, corpus-wide: 233 → 198 → 77 → 0.**

| mechanism | what it retired |
|---|---|
| position-based file-leading harvest | 233 → 198. libGDX 100 → 65 (the `JsonReader`/`JsonSkimmer`/`PatternParser`/`XmlReader` APACHE NOTICES, `GL30/31/32`, `Base64Coder`'s multi-licence header), vfx 11 → 9 (V3's two recorded sites) |
| `Tree.Block.trailing` | 198 → 77. anim8 34 → 0 outright, screens 7 → 1, libGDX 65 → 18, its suite 69 → 51 |
| the backstop + the `deliberate` lane + the `@Test` javadoc's honest home | 77 → 0 |

**What each residue IS**, because "11 recovered" is only useful if it names a category:

- **libGDX core's 4**: a promoted enum constructor's field javadoc (`Cubemap`), a comment inside a
  parameter LIST (`GL32`, `// int length,` — a position the TIR has no node for at all), and two
  comments that are the whole body of a degenerate private constructor the funnel drops
  (`MipMapGenerator`, `Base64Coder`).
- **gdx-gltf's 4 and vfx's 2**: bodies `MethodBodyTransform` replaces, plus vfx's two end-of-body
  comments in constructs the emitter rewrites. A substituted body carrying its upstream comments is
  the honest home if this lane ever grows.
- **libGDX core's 12 deliberate**: exactly the rows `ENGINE-LIMITS.md` D1 predicted —
  `Array#toArray(Class)` and its four deprecated `Class`-taking siblings, `ArrayMap`, `Queue`,
  and `Skin#setEnabledReflection`'s three body comments. Every one documents a member the manifest
  DROPS, so its absence is a decision; before this they were counted as engine loss.

**The lane earned its keep on its first run.** `trivia(recovered)` opened at **51** on libGDX's
suite, all of one category: a `@Test` method's javadoc, lost because `TestFrameworkTransform` turns
the method into a `test("…") { … }` statement and `leading` went with the `def`. The honest home is
`Tree.Commented` on that statement; taking it dropped the lane to 0. Shipping the backstop over it
would have hidden 51 comments at member granularity behind a green `lost = 0`.

**Do NOT retry / three defects that cost a cycle each**, all invisible to every count:

- **`TriviaCheck.normalize` stripped `//` LAST.** A nesting block comment is emitted as `//` lines
  (§4.58), so its javadoc opener arrived with a `//` in front and the two sides normalised
  differently — the comment was reported lost while sitting in the file. Whatever the emitter WRAPS
  the text in comes off first.
- **A finding carries the check name it is filed under.** Passing the lane name to `record` while
  leaving `"trivia"` inside the finding filed every deliberate row against `lost`: the run printed
  `0 lost, 4 recovered, 12 deliberate` and the artifact recorded `trivia 12, trivia(deliberate) 0`.
- **§5.4, a fourth time.** `CommentAnchor`'s map is keyed by java path and its two consumers hold
  the path in different spellings — parser-recorded and orchestrator-resolved. Compared raw, the
  check's lookup missed EVERY file in a worktree and the deliberate lane read zero on a port with
  twelve of them.

And one the design got wrong rather than the implementation: **"between slots, so no member digest
moves" holds only while slots do not nest.** A comment placed after a NESTED member falls inside the
enclosing class's recorded text, and `srcMapOf` — which finds a member by searching for exactly that
string — then cannot: 2 UNLOCATABLE members on libGDX core. The splice now applies the insertion to
every enclosing slot, whose digest moves honestly because it did gain a line.

**Cost, accounted**: 75 member digests on libGDX core, 136 on its suite, 88 anim8, 93 screens, 88
vfx, 83 gltf, 75 ashley, 2 simple-graphs; **0 on jbump and noise4j**, whose emitted text is
byte-identical (jbump's differential probe is therefore identical too — 44 transcript lines).
Compile unchanged everywhere (libGDX 0, gltf 7, noise4j 2, the last two pre-existing); every other
check count identical on all thirteen ports; every suite unchanged (gdx-test 217/4, ashley 108/2
plus its two pre-existing skips, anim8 23, vfx 64, simple-graphs 16, screens 16).

### 11.15 P1 — `Disposable → AutoCloseable`: DELIVERED, and the first production firing of M5m's fold

The policy is three lines — `TypeRedirectTransform("com.badlogic.gdx.utils.Disposable" ->
"java.lang.AutoCloseable", memberRenames = "dispose" -> "close")` last in `mainPhases`, plus
`dropTypes += Disposable` with NO injection, plus 6 hand-written suite sites. It was measured end to
end on libGDX core and its suite, then reverted rather than baselined, because **what stopped it was
not the policy: it was that the libGDX base manifest could not gain ANY new (b) phase while two
dependents configured the same one** (D9). `ashley` and `screens` each reported `1 fatal
SurfaceDivergence`; the escape route through the base manifest was closed by D1's published-map
contract, measured as `BaseMapStale` → 309 fatal base-surface gaps. Both numbers, and why there was
no third place to put the entry, are in D9.

**D9 was closed by the M5m merge contract** (`DESIGN.md` §8.13) and P1 then re-issued UNCHANGED. The
re-issue is a replay and it replayed: **every one of the ten lane headlines is byte-identical
before and after** — every check count, every error count, every test count, on all thirteen ports —
and the only things that moved are member digests, `port-map.tsv`'s `policy=` digest, one
`portability(all)` row, and `decisions.tsv`.

**This is the first time the fold has run in production, and `screens` is where it is visible.** One
`type-redirect` instance in that port's effective pipeline carries BOTH tables: the base's
`Disposable` entry (2 `RetypedSignature` + 7 `RenamedMember` rows against screens' own declarations)
and screens' own ten guacamole entries (33 rows). `ashley`'s merged instance carries the base's entry
(0 rows — ashley emits no `Disposable` reference) beside its own `ReflectionPool → ComponentPool`
(6 rows), and the `governs` intrusion screen admits `ReflectionPool` because the base DROPS it. Both
ports: `manifest 0`, `port-map 0`. The same configuration was `1 fatal SurfaceDivergence` each
before M5m.

| gate | before | with P1 |
|---|---|---|
| libgdx-core compile errors | 0 | **0** |
| all 19 libgdx-core check counts | — | **identical**, finding for finding, except the two `portability` rows whose SUBJECT is now `AsyncExecutor#close` |
| libgdx-core files emitted | 598 (11 dropped) | **597 (12 dropped)** — `sge/utils/Disposable.scala` is not written |
| libgdx-core member digests moved | 0 | **248** |
| libgdx-test | 217 pass / 4 fail | **217 / 4, 0 members moved** — 0 upstream `Disposable` references, as predicted |
| ashley | 108 / 2 (+2 pre-existing skips) | **identical, 0 members moved** — the D2 gate: a dependent that references nothing renamed re-emits nothing |
| `manifest` on ashley and screens | 1 fatal `SurfaceDivergence` each (pre-M5m) | **0 and 0** |
| libgdx-core `decisions.tsv` | 2,305 | **2,439** = +66 `RenamedMember` +67 `RetypedSignature` (`phase=type-redirect`) +1 `DroppedType`, every one `Configured` with the key verbatim |
| counted refusals (`ScopedOut refused=member-rename`) | — | **0** — no component in the closure reaches an unparsed parent |

The `decisions.tsv` pair is the ONE number that is not the pre-block one, and it moved for a reason
that is not P1: the first measurement quoted `2,278 → 2,412`, taken before §11.16's retarget added
27 `Configured` rows to the same port. **The DELTA is identical to the digit** — +66 / +67 / +1 —
which is what the replay was asserting.

**The rename is exact, and the accounting closes.** 74 upstream `void dispose()` declarations = **66
in `Disposable`'s override component**, all renamed whole, + **8 outside it** that correctly keep the
name (`LifecycleListener`/`ApplicationListener`/`Game`/`ApplicationAdapter` and a `Timer` anonymous
body; `ImmediateModeRenderer` + `ImmediateModeRenderer20`; `ParticleController` — none of them
implements `Disposable`, directly or through `Screen`). Emitted `def close()` = 66 renamed + 10
pre-existing upstream `void close()` = **76**. **47** declaration lines gain `extends
java.lang.AutoCloseable`, with 13 further occurrences as a field/parameter/local type.

**The 248 moved digests, by category** (the promotion accounting the re-issue owes):

| | rows |
|---|---:|
| `#close()` keys added | 64 |
| `#dispose()` keys removed | 65 |
| `Model#manageDisposable` / `ModelBuilder#manage` re-keyed `(Disposable)` → `(AutoCloseable)` | 2 + 2 |
| the `sge.utils.Disposable` class row, removed with the unit | 1 |
| class rows changed in place | 89 — 47 that gained the parent, 42 whose body holds a renamed declaration or a moved call |
| other member rows changed in place (call sites) | 25 (23 `def`, 2 `val`) |

66 renamed declarations against 64 added `#close()` rows is not a gap: one is
`sge.utils.Disposable#close()` in the unit the drop removes, and one is
`PixmapPacker$Page$6#close()` inside an ANONYMOUS class body, which `members.tsv` does not index
separately and which is present in the emitted file.

Three things that behaved exactly as designed and are worth not re-checking: `jdk-surface` stayed
**24** and `external-surface.tsv` carries `java.lang.AutoCloseable` at **63** references, classified
`Kept` — not a finding, which is why the check count does not move for a type the port now leans on
sixty-three times; `dropped-types.tsv` gained `com.badlogic.gdx.utils.Disposable` TAB
`sge.utils.Disposable`, both namespaces (§4.56); and every renamed declaration carries its porter
note — **65** of them emitted, `NoteCoverageCheck` **0/0**, in the §4.575 grammar with the manifest
entry verbatim —
`reason=configured phase=type-redirect key="com.badlogic.gdx.utils.Disposable#dispose -> close" component=66`.
65 notes against 66 renames is the drop: the 66th declaration is `sge.utils.Disposable#close()`, in
the unit that is not written. The DROPPED TYPE itself gets no note at all, and that is the shape of
D8 rather than a gap — a dropped type's note is `PorterNote.NotInTree`, carried by the injected file
that supplies its FQN, and this drop has no injection because the target is the JDK's own type.

**The dependent half, which the first attempt never reached** (it stopped at ashley's refusal). Every
dependent lane green, every check count and every suite identical, and the blast is exactly the
`Disposable` implementors each module declares:

| port | own member-diff lines | what moved |
|---|---:|---|
| libgdx-core | 362 (248 members) | the table above |
| gdx-gltf | 88 | 12 renamed decls, 16 class rows, 16 `def` rows in place |
| gdx-vfx | 66 | 11 renamed decls, 14 class rows, 8 `def` rows in place |
| screens | 40 | 7 renamed decls, 8 class rows, 5 `def` rows in place |
| anim8 | 12 | 3 renamed decls (`AnimatedPNG`, `FastPNG`, `PNG8`), 3 class rows |
| ashley, ashley-test, libgdx-test, gltf-test, sg, sg-test, noise4j, jbump | **0** | nothing — byte-identical emit |

Compile unchanged everywhere (libGDX 0, gltf 7 pre-existing, noise4j 2 pre-existing); suites
unchanged (gdx-test 217/4, ashley 108/2 + 2 pre-existing skips, anim8 23, vfx 64, sg 16, screens 16);
jbump's differential probe still 44 transcript lines, IDENTICAL. `decisions.tsv` gained `+1
DroppedType` on every port in the libGDX family — the inherited drop, §1.5 — plus, only where the
module declares an implementor, its own renames and retypes (gltf +12/+1, vfx +11/+2, screens +7/+2,
anim8 +3/0, **ashley +0/+0**).

**`findings.tsv` moved on nine ports by exactly ONE row and no count moved with it**:
`portability(all)`'s subject re-keyed `sge.utils.async.AsyncExecutor#dispose` →
`#close`. That is the base's member seen through every dependent's inherited surface, and it is the
cheapest possible demonstration that the rename reached the shared signature rather than only the
base's own file.

**Every `policy=` digest in the family moved, including screens' and gltf-test's**, which emit
nothing new: the redirect joins `TypeRedirectTransform.surfaceFingerprint`, so a dependent that had
NOT inherited the entry would now be a `SurfaceDivergence`. Same §1.5 mechanism §11.16 recorded for
the retarget, now carrying a merged table instead of an inherited one.

The 6 hand-written suite sites the enablement must fix are `ScreenmanagerSuite` (three overrides —
`ScreenTransition implements Disposable` and `ManagedScreen implements Screen`, which
`extends Disposable` — and two calls) and `VfxFrameBufferSuite`'s one call on
`VfxFrameBufferQueue implements Disposable`.

#### The checkpoint-4 audit of the fold — three holes, all of them silent, all closed

Found by adversarial review of the delivered mechanism, and every one of them is a screen or a
filter that could not fire rather than one that fired wrongly — which is why no count in the tables
above had moved for any of them. **All thirteen ports re-measured after the three: every check count
identical, 0 members changed on every port, every suite unchanged.** They are recorded here because
the numbers say something the fix messages cannot — a corpus that cannot exercise a screen is not
evidence that the screen works.

| what could not fire | how the corpus hid it | closed by |
|---|---|---|
| `SurfaceIntrusion` ran only inside the merge arm, so a dependent declaring a phase NO base has was appended unscreened — one instance, so no divergence; no merge, so no `added` | both consumers of `TypeRedirectTransform` merge with the base's instance, and the one whose subject is inside `com.badlogic.gdx` (ashley) is admitted by the drop | `MergeablePolicy.subjects`, screened on the no-counterpart arm; `ManifestAgreement.statik` derives the finding from any refusal the divergence arm did not report |
| a member-rename clash compared MAP KEYS, so `dispose -> close` merged cleanly with `dispose() -> shutdown` and the drift landed as `MemberRenamer`'s NON-FATAL two-claimants refusal | no port spells one member two ways | both sides through `MemberKey.parseIn`, refusal keyed on the parsed NAME |
| the run's own-keys filter reads a finding's subject off its KEY, and member-level `Malformed` findings were keyed by the bare SEGMENT — so a DEPENDENT's typo'd `memberRenames` entry was dropped from its own report | every corpus key is well-formed, so the filter never had one to drop | keyed `owner#member` (`MemberKey.spell`), plus two run-level specs over a real merged pipeline |

And a fourth, in the criterion rather than the reach: **the intrusion screen admitted every DROPPED
subject, where the honest test is "nothing stands at that name"**. A drop WITH an injection ships a
file at that FQN and that shim is shared surface; a dependent re-pointing away from it produces two
ports that cannot compile together. `PortManifest.shipsInjectionAt` answers it through `renamed`,
because the drop key is upstream and the shim's FQN is emitted. **Measured before assuming**:
libGDX's overrides hold `Json`, `Os`, `Pools`, `SharedLibraryLoader`, `ReflectionException` and
`AssetTypeRegistry` and **no `ReflectionPool`**, so ashley — the one production case — is admitted
for the honest reason rather than by the approximation, and `manifest` stays 0 on every port.

### 11.16 P2 — `Comparator → Ordering`: DELIVERED, and what a retarget costs

The libGDX base manifest's one `retarget` entry —
`CollectionsTransform(retarget = "java.util.Comparator" -> "scala.math.Ordering")`, in
`LibgdxPolicy.core` and inherited by all five dependents (§1.5). **Every lane green, every check
count identical on all thirteen ports, every suite unchanged.**

**Why this one is NOT D9.** The base already carries a `CollectionsTransform`; P2 sets a
CONSTRUCTOR PARAMETER on the instance the dependents already inherit. No dependent constructs a
`CollectionsTransform` of its own — the corpus grep is the whole proof — so there is no second
instance for `extendedBy` to fail to merge. D9 closes a base manifest to a NEW (b) PHASE, not to new
policy on a phase it already has, and P2 is the measurement that says so.

| gate | before | with P2 |
|---|---|---|
| compile errors | libGDX 0, gltf 7, noise4j 2 | **identical** — gltf's 7 are the same pre-existing `MeshLoader` / `PBRCubemapAttribute` / `PBRTextureAttribute` / `ModelInstanceHack`×4 |
| all check counts, 13 ports | — | **identical, finding for finding.** `findings.tsv` and `counts.tsv` are byte-unchanged on every port; only `members.tsv` and `port-map.tsv` moved |
| tests | gdx-test 217/4, ashley 108/2 (+2 pre-existing skips), anim8 23, vfx 64, sg 16, screens 16 | **identical**; jbump's differential probe still 44 lines, IDENTICAL |
| `collection-boundary` | 0 | **0** — the refusal composition is EMPTY, which is the retarget's defining property, not luck (below) |
| `decisions.tsv`, corpus-wide | — | **+32**, every one `RetypedSignature` / `Reason.Configured` with the key verbatim: `java-collections->scala:java.util.Comparator -> scala.math.Ordering` |
| `porter-notes` | 0/0 | **0/0** |

**The promotion, by port and by category** (own members; the lane's own figure adds the base's 75
through the shared srcmap, so `gdx-test` 84 = 75 + 9, `ashley` 85 = 75 + 8 + 2, `anim8` 85 = 75 + 10,
`gltf` 76 = 75 + 1, `vfx` 80 = 75 + 5):

| port | re-keyed (decl-retype) | changed in place | own total | `Configured` rows |
|---|---:|---:|---:|---:|
| libgdx-core | 21 + 21 | 33 | **75** | 27 |
| libgdx-test | 0 | 9 | 9 | 0 |
| ashley | 1 + 1 | 6 | 8 | 3 |
| ashley-test | 0 | 2 | 2 | 0 |
| anim8 | 0 | 10 | 10 | 1 |
| gdx-gltf | 0 | 1 | 1 | 0 |
| gdx-vfx | 1 + 1 | 3 | 5 | 1 |
| gltf-test, screens, sg, sg-test, noise4j, jbump | 0 | 0 | **0** | 0 |

**`port-map.tsv` moved on NINE ports and `members.tsv` on seven, and the two extra are the point.**
`screens` and `gltf-test` emit not one changed character and their published maps still differ by
one line: `policy=` in the header, because the retarget joins
`CollectionsTransform.surfaceFingerprint`.
That is §1.5 working in the direction nobody watches — a dependent that had NOT inherited the entry
would now be a `SurfaceDivergence`, and the fingerprint is what makes that impossible to miss.

**The refusal composition is empty, and that is a property rather than an outcome.** `Ordering[T]
<: Comparator[T]`, so a retyped value reaches every slot that still says `Comparator` bare — the
JDK's own `Arrays.sort`, the engine's `JavaCollections.sort`, and every dependent call site.
`collection-boundary` stayed 0 on all thirteen ports and `coerce` inserted nothing. Where that
subtyping does not hold, the type belongs in `typeMap` with a kind and a factory, and the seam is
then a counted `coerce` boundary — the two are not alternatives (`DESIGN.md` §8.12).

**Zero call sites were rewritten, and no call-site table shipped.** `cmp.compare(a, b)` binds to
`Ordering.compare` unchanged; `implements Comparator<T>` became `extends Ordering[T]` with the
`compare` under it structurally identical (four anonymous `new Comparator<X>(){…}` in libGDX core
became `new Ordering[X]{…}` with no other edit). The `Collections.sort` → `sortInPlace` shape was
already refuted by the compiler and `Arrays.sort` trades a stability guarantee, both measured under
M5c.

**One engine gap this measured, invisible to every count.** A class whose PARENT is retargeted
records no decision: `recordRetypings` compares `Symbol.info`, and a parent list is not in it. Six
classes changed their emitted declaration line with nothing in `decisions.tsv` —
`Attributes`, `ModelCache$Sorter`, `SimpleOrthoGroupStrategy$Comparator`,
`DefaultRenderableSorter` (libgdx-core), `SortTest$NullsFirstComparator` (its suite),
`SystemManager$SystemComparator` and `SortedIteratingSystemTest$OrderComparator` (ashley),
`SceneRenderableSorter` (gltf), `PrioritizedArray$WrapperComparator` (vfx). It is why `gltf`,
`gltf-test`, `libgdx-test` and `ashley-test` each moved members with **zero** `Configured` rows.
The emitted code is correct; the PROVENANCE is incomplete, and it is the same class of hole as the
two already listed in §12.1, where it now sits.

**The PRODUCER direction was uncounted, and is now a check that reads 0** (checkpoint-4 audit;
`ENGINE-LIMITS.md` K14). The `collection-boundary 0` in the table above is honest and it is also
narrower than it looks: `Ordering[T] <: Comparator[T]` licenses a retyped value flowing INTO a
`Comparator` slot and says nothing about a `Comparator` the JDK HANDS BACK reaching a slot the phase
moved — and nothing could see that direction, because a retarget joins neither `mappedTypes` nor
`retypedTargets` and the position-blind `transformType` had already moved the producing node's own
type, so both sides of such a slot read `Ordering`. `RetargetBoundaryCheck` (`collection-retarget`)
counts three shapes — an external producer, a static receiver left naming the java type, and a cast
to the target — with the §1 classification.

| | |
|---|---|
| corpus, all thirteen ports | **0**, and the check now says so in `counts.tsv` (one new baseline row on the 12 ports that run the phase; noise4j runs none and gains none). Nothing else in any baseline moved: **0 members changed on every port**, findings/tests/port-map untouched |
| first corpus run, before the constructor exclusion | **11 findings, every one a `new Comparator<T>(){…}`** — 4 libGDX core, 6 its suite, 1 anim8 — all of them CORRECT code. An anonymous class's `<init>` does not climb to a unit symbol, so `Program.owns` reads it as external. Constructors are excluded structurally; the shape is pinned in `ComparatorOrderingPortSpec` |
| the counter's own proof | a SYNTHETIC producer (`Collections.reverseOrder()`, `Comparator.naturalOrder()`, `String.CASE_INSENSITIVE_ORDER`, a cast): 3 + 1 + 1 findings, with `CollectionBoundaryCheck` reporting **0** on the identical program |

**No coercion is synthesised, and K14 says why**: a wrapper is expressible for THIS pair and is not a
general answer — a retarget's target may be any type, so a coercion has to arrive as policy beside
the entry, which is a table shape and not a rule the engine can derive. Building one against a
synthetic case is exactly the guess CLAUDE.md §1 warns about. The alternative already exists: move
the type into `typeMap` with a kind and a factory, where the seam becomes a counted `coerce`
boundary.

**Not a note, by design.** `RetypedSignature` is deliberately absent from `PorterNote.Rendered`
(the new type is written in the declaration and the diff shows it; 362 notes on libGDX core
restating signatures would bury the ones that carry information). So P2 emits **no new porter
note** — the only note text that moved is `funnelled-ctor`'s `primary=` on `TimSort` and
`SortedIteratingSystem`, which now spells `scala.math.Ordering`. `NoteCoverageCheck` 0/0 on every
port throughout.

### 11.17 P3 — the `@Null` union floor: DELIVERED on libGDX, REFUSED on screens

`NullabilityTransform(annotations = {"com.badlogic.gdx.utils.Null"}, target = Union, scope =
Everywhere(except = 12 types))` in `LibgdxPolicy.core`'s `mainPhases`, inherited by all five
dependents (§1.5). **Every lane green, every check count identical on all thirteen ports except the
new `nullability-boundary`, every suite unchanged.**

**Why it is not D9 and needs no merge**, on P2's argument: `nullability` is a phase NO dependent
constructs — the corpus grep is the whole proof — so there is one instance in every effective
pipeline and `extendedBy` has nothing to fold. That is also exactly why the screens half is refused
(below): screens would have had to construct a second one.

**The plan's central claim was refuted before this ran, and P3 is where the correction is paid
for.** `ENGINE-LIMITS.md` K13: `Null` is a subtype of every CONCRETE reference type and not of an
abstract `T <: Object`, so "0 error-count movement" holds for the floor's declarations and not for
its uses. Measured here, unscoped, reproducing K13 to the site:

| | unscoped | scoped (delivered) |
|---|---:|---:|
| declarations retyped (`decisions.tsv`, `nullability:com.badlogic.gdx.utils.Null`) | 632 | **540** |
| positions moved | 707 | **608** — 150 return, 341 param, 117 field |
| `ScopedOut` decisions (and porter notes) | 0 | **51** |
| `nullability-boundary` | 174 (155 / 17 / 2) | **109** — 90 `AbstractTypeParameter`, 17 `NotAValuePosition`, 2 `VarargParameter` |
| compile errors, libGDX core | **35** | **0** |

**The three exits, and which one this port took.** K13 leaves the abstract-type class as a policy
decision: accept the errors, scope the generic declarations out, or land §8.6's N2 (`-Yexplicit-nulls
-language:unsafeNulls`, under which the class disappears). **The second**, because the first fails
the measure lane's compile guard and the third is gated on A1/A2's placeholder removal, which is not
done. What it exempts and why is `LibgdxPolicy.nullabilityExempt`: twelve types whose `@Null` members
are typed by their OWN type parameter — eight containers (`Array`, `SnapshotArray`,
`DelayedRemovalArray`, `AtomicQueue`, `IntMap`, `LongMap`, `ObjectMap`, `Queue`) and four generic
widgets (`List`, `SelectBox`, `Tree`, `Selection`). **92 of 632 declarations held back to clear 35
errors** — an over-approximation whose price is stated rather than hidden, because a `RuleScope` says
TYPES and the failing set is a predicate over DECLARATIONS. Every other generic type in the library
— `ArrayMap`, `ObjectSet`, `PooledLinkedList`, `ObjectFloatMap`, `Pool` — keeps the floor.

**Two things the exit taught, both measured, both now in K13.**

- **It closes over the subtypes that ANNOTATE.** The eleven types the errors landed in took 35 → 6;
  the six survivors were all in `SnapshotArray` and `DelayedRemovalArray`, which extend the
  scoped-out `Array` and re-state `@Null` on their own `T` in two overrides each. A scoped-out parent
  beside a retyped override is half an override pair — the one shape a floor may not emit — and
  nothing computes that closure: a `RuleScope` is a set of FQNs and the phase's override test is
  wrapper-mode-only, so the compile is the only thing that finds a missing entry. Adding the two
  took it to **0**.
- **…and it stops there, because `OrderedMap` was DEAD POLICY.** It extends `ObjectMap` and
  `OrderedMap$OrderedMapValues` overrides an annotated `ObjectMap$Values#toArray`, so the unscoped
  run put an error in it and the first draft listed it. It re-states no annotation of its own (zero
  `@Null` in the whole upstream file), so scoping `ObjectMap` out settles both ends and the entry
  held back nothing. **Proved rather than argued: with and without it, `members.tsv` is
  byte-identical.** Nothing would have reported it — `PolicyBinder.bindScope` asks "did anything in
  the program fall inside this region", the type exists, and `policy` stays 0. An inert scope entry
  is the one §1(b) no-op the never-fired machinery cannot see.

**What the floor bought, in emitted text** (`skipPhases=nullability` against the delivered run, the
§4.6 kill switch used as a measurement rather than as a debug aid):

| in `ported/sge/src_managed` | without | with |
|---|---:|---:|
| `\| scala.Null` | 0 | **543** (540 declarations; the 65-position gap is `Json`, which the port DROPS and never emits) |
| `@sge.utils.Null` | 161 | **32** — every consumed marker stripped; the 32 that remain are on refused and scoped-out declarations, deliberately, so the contract stays readable at the line |
| `null.asInstanceOf` | 278 | **262** — **16 placeholders retired**, both of §8.6's shapes: an annotated generic return, and an uninitialised annotated field that now states its own default (`private var stage: Stage \| scala.Null = null`) |

**Per-port promotion.** Two ports move and eleven do not:

| port | `just members-unchanged` | `nullability-boundary` | new `decisions.tsv` rows |
|---|---:|---:|---:|
| libgdx-core | **1,298** = 850 changed in place + **224 RE-KEYED** | 109 | 540 `RetypedSignature` + 51 `ScopedOut` |
| libgdx-test | **4** = one re-keyed member + its owning class | 0 | **1** `RetypedSignature` |
| every other port (11) | **0** | 0 | **0** |

The 224 re-keys are not churn to explain away: a `MemberKey` descriptor is built from the parameter
types, and a parameter that becomes `String | Null` renders `?`, so
`JsonMatcherTests#test(String,String,Array<String>,Array<String>)` is now
`#test(?,String,Array<String>,Array<String>)`. The lane's own correlate figure is **846**, which
counts only what it can map through the source map; `just members-unchanged` counts every row.

**And `libgdx-test`'s 4 disposes of the open question about a dependent's re-keys, measured.** The test port
declares NO nullability policy of its own and gets one anyway, through §1.5 inheritance of the base
manifest — its single `@Null` in test sources (`JsonMatcherTests#test`'s first parameter) is retyped
by the base's instance, with the decision recorded in the TEST port's `decisions.tsv` because the
declaration is the test port's own. Inherit-only was the right answer and it is no longer a claim.

`just decision-counts`, `LibgdxCoreMigrate`: **2,439 → 3,030 rows**, the whole delta being
`RetypedSignature` 429 → 969 and a `ScopedOut` kind this port had never recorded (0 → 51). Every
other port's totals are unchanged but for libgdx-test's single row.

**Ashley is the D2 gate and it holds**: `just members-unchanged AshleyMigrate` reports 0, and
`AshleyMigrate/run-latest/decisions.tsv` contains not one `nullability` row — a dependent's phases
decide about its base's units, and those decisions are the base's. `port-map.tsv` still moves on
every port, because the phase joins `surfaceFingerprint`; that is §1.5 working in the direction
nobody watches, exactly as P2 measured.

**No new porter note kind, and that is the P2 rule applied rather than an omission.**
`RetypedSignature` is outside `PorterNote.Rendered` — the type reads `T | scala.Null` and the
annotation is gone, which is the whole of what a note would say, and 540 of them would bury the ones
that carry information. Its COMPLEMENT `ScopedOut` **is** rendered, and those are the 51: a
declaration that kept its upstream type shows nothing in the diff, so the reader has no local
evidence at all. `NoteCoverageCheck` 0/0 on every port throughout.

**The interaction §11.12 named to be MEASURED at P3, measured: it does not fire.**
`TirEmitter.rawParentAlignment` tests `hasWildcardArg`, which does not look inside a union, so an
annotated parameter whose type carries a wildcard AND overrides a parent method would silently stop
being aligned — and a silent un-alignment is not a thing any count shows. The delivered output has
**five** wildcard-inside-union sites (`Action#pool`/`getPool`/`setPool`, `Cell#merge`,
`Button#getButtonGroup`) and **none of them is an `override`**, so the shape does not occur in libGDX
and nothing was hidden. It remains live for the next library, and the fix is one predicate.

**The SCREENS half — refused once, then delivered, and the refusal is why it is worth reading.**
P3's plan asked for `annotations = ["org.jspecify.annotations.Nullable"]` on screens and the
retirement of `--dependency org.jspecify:jspecify:0.3.0` from `screens_deps`. It could not land as
policy: `ScreensPolicy.core` is `LibgdxPolicy.core(...).extendedBy(...)`, so a `nullability` in its
own `surface` is a SECOND instance of a phase the base now carries, `NullabilityTransform` declared
no `MergeablePolicy`, `SurfaceFold.of` recorded `Cause.NoContract` and `ManifestAgreement` turned it
into a fatal `SurfaceDivergence`. The finding named the fix and its kind — *"the phase declares no
`MergeablePolicy` (that is §1(a), engine: give it one)"* — and widening the mechanism inside a policy
commit is the thing P1 established must not happen.

Folding jspecify's FQN into the BASE's annotation set is not the alternative it looks like: it
would put a fact about a dependent's own sources into the shared surface, and report a never-fired
policy entry on every libGDX lane forever. **Do NOT retry that shape.** The order was: give
`NullabilityTransform` a `MergeablePolicy`, measure that alone, then land screens' entry — and both
steps are now done.

**Step one, measured alone** (`DESIGN.md` §8.13): annotations union, `target` must agree, the scope
unions its ENTRIES in both directions and refuses across them, and `subjects` covers both the
annotation FQNs and the scope entries so the intrusion screen sees the whole policy. **0 members
changed and every check count identical on all thirteen ports** — what a contract nobody has
instantiated yet must measure.

**Step two, the first production nullability merge.** `ScreensPolicy.nullability` states screens'
own `org.jspecify.annotations.Nullable`, the fold composes it with the base's
`com.badlogic.gdx.utils.Null` into ONE instance at the base's position, and `manifest` is **0** on
screens — the number that was fatal before the contract existed.

| screens | before | after |
|---|---:|---:|
| `@org.jspecify.annotations.Nullable` in `src_managed` | 4 | **0** |
| `\| scala.Null` occurrences in emitted CODE | 0 | **20** (30 counting the ones a funnelled-ctor note quotes) |
| `nullability-boundary` | 0 | **2**, both `AbstractTypeParameter` |
| members whose emitted text moved | — | **25** (12 class lines whose funnelled primary took the union, 6 field/method retypes, 1 re-key: `ShaderTransition#<init>(String,String,boolean,float,Interpolation)` is now `…,?)`) |
| `decisions.tsv` | 172 rows | **170** — `RetypedSignature` 59 → 56 and a `ScopedOut` this port had never recorded (0 → 1) |
| lane dependencies | jspecify + munit | **munit** |

**The jar's retirement is the proof, not a tidy-up.** The annotation is CONSUMED, so nothing emitted
names it; with the jar still on the compile line a surviving annotation would resolve and the port
would look converted while it was not. It stays on the FRONTEND classpath, where the Java sources
still carry it and it has to resolve for the phase to see it at all.

**And K13 arrived again, at member scale this time.** `ScreenManager<S extends ManagedScreen, T
extends ScreenTransition>` annotates its own type parameters, and the unscoped run put **3 errors**
in it — an overload-resolution failure at `pushScreen` and two `T | Null` mismatches inside
`render`. The exit is the same one libGDX took and the scope is spelled one level finer:
`ScreensPolicy.nullabilityExempt` is **two MEMBER keys**, `ScreenManager#transition` and
`ScreenManager#pushScreen`, because the failing set here is four declarations in one class rather
than a container's whole API. `3 -> 0`. The two travel together — the field is assigned from the
parameter, so scoping out one and retyping the other is `T | Null` into `T`, K13's half-a-pair shape
one level down. `#getCurrentScreen` and `#getLastScreen` are annotated at an abstract `S` too and
are deliberately NOT exempt: they are the 2 counted `nullability-boundary` findings, and nothing in
reach uses them in a position `S | Null` does not satisfy. **The exit is what the compiler measured,
not every declaration that could in principle have failed.**

Screens' scope entries are inside screens' OWN `governs` (`de.eskalon.commons`) and therefore pass
the intrusion screen for the honest reason rather than by luck; a scope entry naming a libGDX type
would be a fatal `SurfaceIntrusion`, which is the whole point of `subjects` carrying the scope half.

Every other port is byte-identical: **0 members changed on the other twelve**, every check count
unmoved, suites unchanged (gdx-test 217/4, ashley 108/2 + 2 skipped, anim8 23, vfx 64, sg 16, jbump
no suite, screens 16/16; gltf 7 and noise4j 2 pre-existing errors).

**And one residue P3 FOUND by reading the emitted output, then fixed in its own measurement.** Every
`ScopedOut` note rendered its key TWICE — `/* porter: scoped-out reason=configured phase=nullability
key=com.…ObjectMap key=com.…ObjectMap */`. `PorterNote.pairs` emits the `Reason.Configured` key and
then the decision's own `detail`, and three phases put the same string in both:
`NullabilityTransform`, `CollectionsTransform`, `GlobalsToImplicitsTransform` (`TypeRedirect` and
`BeanProperty` did not). An M3-era engine defect, systemic rather than nullability's; P3 is simply
the first corpus run that emits a `ScopedOut` note at all.

**Fixed at the DECIDER, not at the renderer**, and the layer is the whole of the argument: a dedup
inside `PorterNote.pairs` would leave `decisions.tsv`'s `detail` column restating its own `reason`
column — the same redundancy in the artifact the renderer never touches — and would silently swallow
a `key` a future decider means as something OTHER than the classification's. `Decision.detail`'s
scaladoc now states the rule where a decider reads it, and the three phases stopped.

**Measured: 51 note texts on libGDX core and 1 on screens, and nothing else anywhere.** The
member-digest blast is larger than the note count and exactly accountable — **libgdx-core 70
members (140 by `just members-unchanged`, which counts a changed row twice) = the 51 declarations
carrying a note PLUS the 19 enclosing types whose body text contains them; screens 2 (4) = its one
note and its class.** Every check count identical on all thirteen ports, 0 members changed on the
other eleven, every suite unchanged. Baselines promoted for those two, accounted.

`PortRun`'s three drop-note sites are the same defect at a fourth decider and are left as their own
measurement (§7.4): their notes land in emitted type BODIES and in injected files, so the blast is
a different set.

### 11.18 P4 — the bean-property subset: DELIVERED, and the first large CONFIGURED rename

`BeanPropertyTransform(pairs = 144 entries)` first in `LibgdxPolicy.core`'s `mainPhases`, inherited
by all five dependents (§1.5). **Every lane green, 0 compile errors on libGDX core, every suite
unchanged, and exactly one check moved anywhere: `policy` 0 -> 5 on libgdx-core, which is the five
counted refusals.**

**Why it needs no merge, on P2/P3's argument.** `bean-properties` is a phase NO dependent constructs
— the corpus grep is the whole proof — so there is one instance in every effective pipeline and
`extendedBy` has nothing to fold. The instance-count question (§1.5) is asked BEFORE the policy is
written, and here it answers "no fold", which is why this landed as one policy commit where P3's
screens half could not.

**The harvest binds completely: 144 entries, 0 typos, 0 invented members.**

| | dry run (§11.11) | LIVE |
|---|---:|---:|
| entries declared (R5 §4's block) | 144 | **144** |
| accessor keys that did not bind (`NeverMatched`/`Malformed`) | 0 | **0** |
| properties APPLIED | 127 | **139** |
| properties REFUSED, each counted | 17 | **5** |
| declarations moved (`RenamedMember`, `Configured`) | 267 | **295** |

**The 12-refusal delta is K12, and the live run is its confirmation.** `ENGINE-LIMITS.md` K12's
`ExternalSurface.jdkPlatform` closes the member sets of the platform types the JDK itself closes, so
`Selection` / `VertexAttributes` / `TiledMapTileSet` / `OrientedBoundingBox` no longer anchor their
override components on `java.lang.Iterable`, `Comparable` or `Serializable`. 127 + 12 = 139 and
17 - 12 = 5, exactly. The 28 extra declarations are those twelve properties' own components.

**The 5 survivors, all correct, none silent** — a `PolicyIssue.Unverifiable` finding with its cause
AND a `ScopedOut` decision, each:

| cause | n | entries |
|---|---:|---|
| no NILARY getter | 3 | `VertexAttributes#offset` (`getOffset(int)`), `Polygon#vertex` (`getVertex(int,Vector2)`), `Polygon#centroid` (`getCentroid(Vector2)`) — **the phase refused rather than inventing a nilary twin**, which is "NEVER INVENT A MEMBER" firing on real policy |
| collision the emitter will not move | 2 | `ScrollPane#scrollX`/`scrollY` — the target name is taken by a member the §4.55 passes do not relocate |

All five are POLICY defects this manifest owns, not engine ones: drop them, or name the
accessor sge actually converted. **THE "LEAVE THEM IN DELIBERATELY" ARGUMENT WAS WRONG FOR THE FIRST
THREE AND §11.24 REVERSES IT.** "A refusal that is counted, explained and reproducible is the report
the channel exists to produce" is true of a refusal a port can ACT on; the three no-nilary-getter
entries name accessors that take arguments, so the phase will refuse them on every run for as long
as the upstream stands, and a finding that can never be cleared is a noise floor that teaches its
reader to skim the number. They are deleted; the two `ScrollPane` collisions stay, because those ARE
pending work and the count is the thing that keeps them visible.

**Every applied property landed on its EXACT requested name: zero suffixed rename targets.** The
collisions that would have needed one were absorbed by the emitter moving the FIELD
(`MemberRenamer.OnCollision.DeferToEmitter`) — the `$field` residue below — and the two it could not
absorb are the two refusals above. That is the delegation contract measured end to end.

**The `$field` residue — the number `TrivialAccessorCollapse` is gated behind (DESIGN.md §8.5).**
Renaming a getter to `x` lands it on the private field's name, so the emitter's universal
field-vs-method pass moves the field:

| libgdx-core | before | after |
|---|---:|---:|
| emitter field-vs-method renames (`RenamedMember`, `Universal`, one per DECLARATION) | 168 | **281** |
| `var …$field` declarations in emitted text | 163 | **274** |

**+113 declarations**, against the dry run's +102 prediction. That is the gate's input and it is this
commit's OUTPUT, not its change: 113 fields against 139 applied properties says most of the harvest
is not trivial — a computed getter or a side-effecting setter keeps its field either way — and
`BaseDrawable` is the worked example, seven `var x$field` under seven `override def x` /
`override def x_=`. The collapse is worth doing and it is worth doing SECOND, on its own measurement
(§5's *change one thing*).

**`just decision-counts`, `LibgdxCoreMigrate`: 3,030 -> 3,436 rows**, and the +406 is fully
accounted:

| | |
|---|---:|
| `RenamedMember` **`Configured`** — the property renames | **+295** |
| `RenamedMember` `Universal` — the field-vs-method fallout above | +113 |
| `ScopedOut` — the five refusals | +5 |
| `WidenedVisibility` | **-7** — see the provenance gaps below |

**295 `Configured` rename rows is the largest configured population of any kind in the project**
(P3's was 540 `RetypedSignature`; the largest rename population is libGDX's 882 `Universal` rows).
They cover **255 DISTINCT declarations** — 2.1 per applied property — and the 40-row gap is a POLICY
redundancy worth naming: nine harvested entries name the same property on two or three types of ONE
override component (`Texture`/`Cubemap`/`TextureArray`#`managed` all resolve into
`GLTexture#isManaged`; `AnimatedTiledMapTile`/`StaticTiledMapTile`'s six pairs both resolve into
`TiledMapTile`). The rename is idempotent and applies once — only the decision log double-counts —
but collapsing them into the interface entry, exactly as the harvest already did for `Drawable`, is
a Q30 completion edit.

**And the `Drawable` fan-out expectation is REFUTED by the code, which is worth recording because
the brief predicted otherwise.** `Drawable` was priced at "4+ implementors"; upstream declares
`getLeftWidth` in exactly TWO types — the `Drawable` interface and `BaseDrawable` — and every other
implementor (`TiledDrawable`, `TextureRegionDrawable`, `NinePatchDrawable`, `SpriteDrawable`,
`TransformDrawable`) inherits it. So each `Drawable` property moves 4 declarations (2 accessors × 2
declaring types) and **the fan-out that is real is the CALL-SITE one**, in the `scene2d.ui` widgets
that only ever call it. sge's per-implementor `getLeftWidth` rows record what its hand port WROTE
per file, not what upstream declares — a reference port's rename header is evidence about the port,
never about the java (§3.5).

**Per-port promotion, accounted.** Nine baselines promoted, four untouched:

| port | `just members-unchanged` | what moved |
|---|---:|---|
| libgdx-core | **1,538** = 255 re-keyed away + 255 re-keyed to + 514 digest-moved (two diff lines each) | the 255 accessor declarations RE-KEY (`#getVolume()` → `#volume()`, `#setVolume(float)` → `#volume_=(float)`); of the 514 moved in place, **255 `def`** (call sites in method bodies), **122 `val`** (the renamed fields — a field's member KEY is its upstream name, so a `$field` rename is a digest move and not a re-key), **93 `class`**, **27 `stmt`**, **17 `ctor`** |
| libgdx-test | **14** | `PolygonTest` and `IntersectorTest` only — `polygon.vertices`, `polygon.transformedVertices`. §1.5 inheritance in the direction that matters: the test port declares no bean policy and its call sites move anyway |
| gltf | **4** | one member: `GLTFBinaryExporter#export`, now `texture.textureData.getFormat()` |
| anim8, ashley, ashley-test, gltf-test, screens, vfx | **0** | `port-map.tsv` header only — the `policy=` digest, because the phase joins `surfaceFingerprint` (P2/P3's "§1.5 working in the direction nobody watches") |
| jbump, noise4j, sg, sg-test | **0**, port-map **0** | not libGDX dependents; nothing to inherit |

**Suites, all unchanged**: gdx-test **221: 217 passing / 4 failing, all 4 `expected#derived`** from
`Substitutions.dropTypes com.badlogic.gdx.utils.Json`, **0 unexpected and 0 declared** — the derived
classification still doing the work §4.56 gave it; ashley 108/2 + 2 skipped (the same two); anim8 23,
vfx 64, sg 16, screens 16, jbump no suite; gltf 7 and noise4j 2 pre-existing compile errors, the same
`MeshLoader` / `PBRCubemapAttribute` / `PBRTextureAttribute` / `ModelInstanceHack`×4 set. **No test
anchored in a renamed member changed state.**

**Porter notes**: 295 `renamed-member` notes across 45 files, `NoteCoverageCheck` **0/0** on every
port throughout.

**Three provenance gaps this delivery MEASURED and deliberately did not fix** — all engine-side
(§1a/b), and widening a mechanism inside a policy commit is what P1 established must not happen:

- **A refusal has no porter note, and it is the one decision whose reader cannot find it.** A
  `BeanPropertyTransform` refusal records `subject = SymId.None` — there is no declaration to sit
  above, because the point is that nothing moved — so the five `ScopedOut` rows reach `decisions.tsv`
  and the `policy` findings and NOTHING reaches the emitted line. An agent reading
  `def getScrollX(): Float` in `ScrollPane.scala` has no local evidence that a policy entry asked for
  it and was refused, which is exactly the §4.575 question. `PorterNote.InBody` on the owning type is
  the shape that fits.
- **`WidenedVisibility` 142 → 135: seven decisions stopped being recorded while the emitted text
  still widens.** The seven are precisely `BaseDrawable`'s seven private fields (`leftWidth`,
  `rightWidth`, `topHeight`, `bottomHeight`, `minWidth`, `minHeight`, `name`), each of which carried
  a `cause=ctor-replay-widening; from=private; to=public` row before and is still emitted
  `public var …$field` after. Nothing catches this: the emitted visibility is unchanged, the compile
  is unchanged, and `porter-notes` is 0 because `NoteCoverageCheck` compares decisions to notes,
  never decisions to reality. **THE DIAGNOSIS WAS WRONG AND §11.22 CORRECTS IT.** The rename did move
  those seven out from under the ctor-replay widener, but that widener was never the decider for
  them: the §4.55 CLASH PASS strips `private` from every field it renames, before `widen` ever runs,
  and it recorded nothing at all — so the seven are not rows one decider lost, they are seven of ~280
  rows a second decider never had. Filed against the wrong decider, the item asks for a fix that
  would have re-recorded seven and left the other ~273 exactly as invisible.
- **A base's refusals are republished in every dependent's `decisions.tsv`** — 2 rows each in anim8,
  ashley, ashley-test, gltf, gltf-test, screens, vfx (5 until §11.24 deleted the three permanently
  refused entries). `ENGINE-LIMITS.md` D2's module scope filters by DECLARATION and a refusal has
  none, so the one decision kind that cannot be scoped is the one that leaks. The `policy` findings
  ARE correctly scoped (0 on every dependent); only the provenance artifact is not.

### 11.19 Checkpoint-4 audit remediation — F1: a dependent's ANNOTATION may not retype its BASE

`balticporter.tir.RunScope` (`api`), carried on the `PolicyBinder`; `NullabilityTransform` refuses at
PLAN time; `PortManifest.contributedSubjects` and `PortManifest.declaresPolicy`;
`ManifestAgreement.Kind.BaseNamespaceUnclaimed`. **Every check count identical on all thirteen ports,
0 members changed on every one, every suite unchanged, and the defect is proven on a synthetic
base+dependent instead.** `DESIGN.md` §8.13's last subsection and `ENGINE-LIMITS.md` D2 carry the
rule; this section carries the numbers.

**The defect, and why the corpus could not show it.** `SurfaceFold`'s `governs` screen reads policy
KEYS, and an ANNOTATION FQN is the one key that names none of the declarations it moves. libGDX's
own `@Null` is inside libGDX's claim and is declared by the BASE, so every dependent inherits the one
instance and there is nothing to screen; screens' jspecify entry was retired at
`5902823a` in favour of the base's marker. The corpus therefore contains **no module whose own
annotation FQN reaches a base declaration** — which is exactly why the hole shipped, and exactly why
its proof has to be synthetic (`NullabilityBaseSurfaceSpec`, 7 tests). Neutralised, the screen's two
positive tests fail and the five negatives pass, which is the failing-before evidence.

| what the synthetic dependent does | before | after |
|---|---|---|
| `p.Base#find` / `p.Base#cached`, annotated in the BASE's Java with the DEPENDENT's marker | retyped `String \| Null` by the dependent's run | **kept**, exactly as the base's own run emitted them |
| `q.Mine#own`, this module's own declaration | retyped | **retyped** — the phase still does its job |
| `decisions.tsv` rows about `p.Base…` | present | **none** (D2 governs provenance too) |
| `policy` findings | 0 | **1**, keyed on the annotation FQN, counting 2 refused declarations |

**The severity is argued, not defaulted: NON-FATAL.** The refusal has already made the emission
correct, so what is wrong is the manifest's claim rather than the port's output; a fatal finding
would stop a run whose bytes are right. It lands in `policy` rather than `nullability-boundary`
because `NullabilityTransform.boundary` filters to the units the run EMITS — the D2 filter that makes
that check correct is the same filter that would silently drop this finding.

**One key kind, and that is a proof.** A SCOPE entry names an FQN, so an entry that reaches a base
declaration is inside that base's `governs` claim by construction and is already a fatal
`SurfaceIntrusion` at manifest time. Do not add a run-time screen for the other key kinds.

**The corollary, negative-tested and 0 on the corpus.** An empty `governs` makes `PortManifest.claims`
false for every FQN, so a base that states policy and claims nothing admits every subject every
dependent adds — a screen that cannot be told from one that passed. Every corpus base claims one
(`com.badlogic.gdx`, `com.badlogic.ashley`, `net.mgsx.gltf`, `de.eskalon.commons`,
`com.crashinvaders.vfx`, `com.github.tommyettinger.anim8`), so `manifest` stays **0 on all thirteen
ports**; the two engine fixtures that did not (`SurfaceFoldSpec`'s chain middle, `PortConfigSpec`'s
base conf) now do, which is the finding firing correctly on the only two manifests in the repository
that were silently unscreened.

### 11.20 Checkpoint-4 audit remediation — F2: K13's two blindnesses become PLAN-TIME reports

`RuleScope.neverFired` called by `NullabilityTransform` at the end of its plan, and
`NullabilityBoundaryCheck.Issue.ScopedOutParent` for the closure a `RuleScope` does not compute.
**Both rules are `ENGINE-LIMITS.md` K13's, unchanged; what moved is that the RUN now enforces them.**
K13 keeps its numbers and says so.

| K13 said | evidence it had | evidence it has now |
|---|---|---|
| a scope exit on a generic type names the type AND every owned subtype that RE-STATES the annotation | the COMPILER: 35 errors -> 6 -> 0, the six being `SnapshotArray`/`DelayedRemovalArray` | a `nullability-boundary` `ScopedOutParent` finding per (retyped declaration, scoped-out annotating ancestor), §1(b)-classified, at plan time |
| …and it STOPS there — a subtype that merely inherits needs no entry, and adding one is dead policy | a BYTE-IDENTITY experiment (`OrderedMap` in, `OrderedMap` out, `members.tsv` identical) | a `policy` `NeverMatched` finding per declared scope entry that named no ANNOTATED declaration |

**Both are 0 on the corpus, which is the delivery rather than a gap.** libGDX's twelve
`nullabilityExempt` entries and screens' two member keys each hold something back — the closure was
completed by hand at P3 and the inert `OrderedMap` entry was deleted then — so the reports confirm a
scope that is already correct. That is why the mechanism is proven on fixtures instead: three tests
in `NullabilitySpec` (a bound-but-inert entry; an entry naming nothing, reported ONCE by the binder
and not twice; a scoped-out parent whose annotating child is reported, closed by naming the child,
silent when nothing is scoped). Neutralised, the two positives fail and the rest pass.

**Where each finding lands is structural, not taste.** The dead ENTRY is a manifest key with no site
in emitted code, so it is a `policy` finding — the check already scoped to this module's own keys.
The CLOSURE names an emitted declaration, so it is a `nullability-boundary` finding and survives that
check's D2 emitted-unit filter, which is the same filter that would have dropped it had the subject
been the scoped-out PARENT. The subject is therefore the CHILD, which is also the end a port can move.

**The predicate over-approximates by NAME, deliberately.** It reads `Definition.parents` and the
annotation hits the plan already computed; it does not resolve overriding. A same-named annotated
ancestor member that is not really an override names a pair a port dismisses in one reading, while a
signature test would need the override closure this phase does not have (its wrapper-mode test says
the same thing one method up).

### 11.21 Checkpoint-4 audit remediation — F4: the SCOPED-OUT declarations become a counted lane

`NullabilityBoundaryCheck.Issue.ScopedOut`, raised beside the `ScopedOut` decision the phase already
records. **The only check count that moves, and it moves on exactly the two ports that scope
anything.**

**Why the residue needed a number and not a grep.** P3 stated it as "the 32 `@sge.utils.Null` markers
that remain are on refused and scoped-out declarations, deliberately". That number is not the
residue: the emitter renders a CLASS's and a METHOD's annotations and neither a FIELD's nor a
PARAMETER's, so the emitted text under-reports every held-back field and parameter by construction,
and the only complete evidence was `decisions.tsv` — an artifact with no baseline diff and no line in
any headline. A residue nobody counts is a residue that grows; every other lane of this check exists
for the same reason.

| port | `nullability-boundary` | of which `ScopedOut` |
|---|---:|---:|
| libgdx-core | 109 -> **160** | **51** — the same 51 declarations `LibgdxPolicy.nullabilityExempt`'s twelve entries hold back |
| screens-core | 2 -> **3** | **1** — `ScreenManager#transition` |
| every other port | unchanged | 0, by arithmetic |

**It counts DECLARATIONS, exactly as the decision does**, so the two artifacts agree and a divergence
between them would be visible; a scoped-out PARAMETER is still recorded by neither, which is §12.1's
open item and is deliberately not widened here (a lane that counted parameters would disagree with
the decisions it sits beside). Two baselines promoted, accounted.

### 11.22 Checkpoint-4 audit remediation — F3: the CLASH PASS is the visibility decider, and it recorded nothing

`TirEmitter.recordClashWidening`, called by both §4.55 field-clash passes. **`WidenedVisibility`
135 -> 337 on libgdx-core (+202), 213 members and 78 enclosing types moved across seven ports, every
check count identical everywhere, 0 compile errors, every suite unchanged.**

**The defect.** `resolveFieldShadowing` and `resolveMemberClashes` both end with
`flags.copy(isPrivate = false, isProtected = false)` on every field they rename, unconditionally —
and they must: a renamed field has to stay reachable from wherever Java read it, and Scala's own
access rules do not grant that at the new name. The RENAME was recorded; the WIDENING was not. So a
member emitted `public` where the upstream wrote `private` carried a `RenamedMember` row that says
nothing about visibility and NO row that does.

**Nothing in the pipeline could see it.** The emitted visibility is what it always was, the compile
is unchanged, and `NoteCoverageCheck` compares decisions to NOTES rather than decisions to reality —
so a widening with no decision is invisible to it in the one direction that matters. This is the
same shape as §11.18's third bullet and is why that bullet's DIAGNOSIS was wrong: `WidenedVisibility`
142 -> 135 was filed as "a renamed member escapes the ctor-replay visibility decider", and the fix it
asks for would have re-recorded seven rows and left the other 195 exactly as invisible. The clash
pass runs BEFORE `TirEmitter.widen`, strips `private` first, and had no decider at all.

| | |
|---|---:|
| fields the two clash passes rename, libgdx-core | 300 (281 `field-vs-method` + 19 `shadows-inherited`) |
| …of which were `private` or `protected` — one row each, the `widen` discipline | **202** |
| `WidenedVisibility`, libgdx-core | 135 -> **337** |
| the 7 `BaseDrawable` fields §11.18 filed against the wrong decider | now `cause=member-rename`, among the 202 |

**The blast, priced BEFORE it was taken and exactly reconciled after.** `WidenedVisibility` is
already in `PorterNote.Rendered` for its other five causes and `Rendered` is per KIND, so
"decision-row-only" is not expressible without splitting the kind — and splitting a kind to hide half
of it is what §4.575 says a note must not do. The P2 `RetypedSignature` precedent does not transfer:
a retyped signature IS the declaration and the diff shows it, while an ABSENT `private` is only
meaningful against Java the reader does not have (which is exactly the asymmetry that already admits
`ScopedOut`). So the notes ship, and the digests move:

| port | own members changed | = decls + enclosing types |
|---|---:|---|
| libgdx-core | **278** | 201 emitted declarations + 3 nested types + 74 top-level units. The 202nd row is `Json#sortFields`, in a type this port DROPS — a decision about a declaration nobody emits, and therefore no digest |
| ashley | 9 | 5 + 4 |
| noise4j | 4 | 2 + 2 |
| ashley-test, gltf | 3 each | 1 + 2, 2 + 1 |
| jbump, simple-graphs | 2 each | 1 + 1 |
| anim8, gltf-test, libgdx-test, screens, vfx, simple-graphs-test | 0 | no field-clash rename is private |

`porter-notes` stays **0** on every port, which is the check confirming the other half: 202 new
decisions, 202 new notes, none orphaned in either direction. Seven baselines promoted.

### 11.23 Checkpoint-4 audit remediation — F5: the small ones, and the spec that came back clean

Docs, cross-references and one spec; no engine behaviour, 0 members changed on every port, every
check count identical.

**The MemberKey collision is NOT real, and the spec that says so is the deliverable.** The hazard the
audit named is plausible: `Descriptor.Param.Unresolved` renders `?`, `Descriptor.ofInfo` cannot spell
a `TypeRepr.OrType`, and the union floor turns `m(String)` into `m(String | Null)` — so two overloads
differing only in a nullable-retyped parameter could both key as `Holder#m(?)`, one key naming two
members. `NullabilityMemberKeySpec` pins that it does not, and pins BOTH reasons rather than one,
because either alone would be a coincidence a later change could withdraw: `Symbol.descriptor` is
recorded by the FRONTEND from the java signature and no phase rewrites it, so a retyped member keeps
the key its java always had; and the engine's fallback REFUSES rather than guessing —
`Descriptor.total` is all of the parameters or none, so an unspellable slot yields NO descriptor
rather than a `?` that collides. **Verdict: no defect.**

**§12's subsections were numbered `7.1`–`7.5`, colliding with anim8's own `7.1`–`7.8`** — so a
reference to "§7.1" named two different lists, and three of them pointed at the wrong one. Renumbered
`12.1`–`12.5`; anim8's untouched. The three cross-references the audit named now resolve:
`ENGINE-LIMITS.md` K13's tail -> §12.1 (the scoped-out-parameter row, which still exists);
`Decision.detail`'s scaladoc and `PorterNote.pairs`' -> §12.4, because the row they cited (the
duplicate `key=` rendering) was DELETED by `b2c27684` when the three phases were fixed, and §12.4's
"drop notes print `key=` twice" is the surviving half of the same defect, in `PortRun`'s three loops.

**`DESIGN.md` §8.13** now states that per-module scope DIRECTIONS are inexpressible in one merged
instance — one instance carries one `RuleScope`, a `RuleScope` carries one direction, and a merge
that kept both would have to be two instances, which is the `SurfaceDivergence` the fold removes — so
P5/P6 do not re-litigate the `Everywhere` × `Only` refusal.

**NOT DONE, deliberately: `CLAUDE.md` §5's check-count sentence.** It reads "nineteen engine checks …
so its lanes show twenty" and the libgdx-core baseline now carries **21** rows, of which 20 are the
engine's. The correction is stated in this delivery's report for a human to apply; an agent does not
edit its own operating instructions on another agent's say-so. The sentence should also stop quoting
a constant and state the MECHANISM — the fifteen `PortRun.RequiredChecks` rows plus every check the
run's own pipeline registers (`porter-notes` always, the three `collection-*` with
`CollectionsTransform`, `nullability-boundary` with `NullabilityTransform`) — because a number in
prose is what went stale, twice.

### 11.24 Checkpoint-4 audit remediation — F6: the `policy` noise floor, and a duplicate the corpus was emitting

`LibgdxPolicy`'s bean-property harvest, 144 entries -> **133**. **`policy` 5 -> 2 on libgdx-core, and
that is the ONLY check that moves anywhere.** Every other count identical on all thirteen ports, 0
compile errors, every suite unchanged, twelve ports 0 members changed.

**Three entries were PERMANENTLY refused, and a finding that can never be cleared is a noise floor.**
`VertexAttributes#getOffset(int)`, `Polygon#getVertex(int,Vector2)` and `Polygon#getCentroid(Vector2)`
all take ARGUMENTS, so there is no nilary getter to convert and the phase refuses them on every run
for as long as the upstream stands. Deleted. The **two survivors are real pending work** and stay
counted: `ScrollPane#scrollX`/`scrollY` hit a name the emitter's §4.55 passes will not relocate, and
completing those get-only entries against an upstream that has setters is a manifest edit nobody has
made. `policy > 0` on this port now means something again.

**And nine per-implementor entries were DUPLICATES the corpus was emitting twice.** The phase renames
the whole override COMPONENT, so an entry on the type that DECLARES a member already reaches every
implementor; the `Drawable` family was collapsed to its interface when the harvest was written and
two more families were owed the same treatment. `isManaged` is declared abstract on `GLTexture` (one
entry replaces three) and `TiledMapTile` declares all seven of its properties (seven entries replace
thirteen).

**THE AUDIT PREDICTED ZERO MEMBER MOVEMENT AND THAT WAS WRONG — 49 members moved, and the reason is
the point.** A duplicate entry is not inert: `MemberRenamer` records one `RenamedMember` decision per
member of the component PER REQUEST, and the emitter renders every decision about a subject it is
emitting — so `StaticTiledMapTile.scala` carried TWO notes on `def id`, in two different `key=`s,
telling its reader the same thing twice. Removing the duplicates removes the second note.

| | |
|---|---:|
| `policy`, libgdx-core | 5 -> **2** |
| `RenamedMember` decisions | 1290 -> **1250** (-40, exactly the duplicates) |
| `ScopedOut` decisions | 56 -> **53** (-3, the deleted refusals; they leaked into all seven dependents' artifacts too, §12.1's last row, now 2 each) |
| members changed, libgdx-core | **49** = 41 members + 8 enclosing types |
| …the 41 | 5 `managed` (`GLTexture` + `Cubemap` + `Texture` + `Texture3D` + `TextureArray`) and 36 tile accessors (12 each across `TiledMapTile`, `AnimatedTiledMapTile`, `StaticTiledMapTile`) |
| members changed, every other port | **0** |

**Nothing SEMANTIC moved and the artifact proves it**: the same 41 members are renamed to the same
names by the same components — `def id`, `def id_=` are emitted exactly as before — and what changed
is one note and its `key=`. The three deleted refusals moved no member at all, which is what "inert
on the code" means and what the audit's prediction was right about.

### 11.25 P6 — the opaque families: DELIVERED, and the first step whose gate was a DEPENDENT's suite

`PrimitiveToOpaqueTransform(OpaqueSpec(TextureHandle))` is in `LibgdxPolicy.mainPhases`, between
`disposableRedirect` and `nullability`. The same `OpaqueSpec` had been applied and reverted four
times before this; the translation had been correct since the second one, and what stopped it was
never what the phase EMITS but what the run WRITES, in modules the first two attempts never
compiled. That was `ENGINE-LIMITS.md` §13 O5, closed in the engine, and the delivery below is the
fifth application of a value that has not changed since attempt 1.

**The five columns are the same `OpaqueSpec` against the same baseline**, so every difference
between them is an ENGINE difference:

| | attempt 1 (pre-O1/O2) | O1+O2 proof | attempt 2 (all 13) | O5 proof (all 13) | **DELIVERED** |
|---|---:|---:|---:|---:|---:|
| libgdx-core scalac errors | 6 (`EngineGap`) | 0 | 0 | 0 | **0** |
| seeded | 1 | 1 | 1 | 1 | **1** |
| `RetypedSignature` decisions | 2 | 2 | 2 | 2 | **2** |
| coercions | 27 (14 wrap + 13 unwrap) | 30 (14 + 16) | 30 | 30 | **30** (14 + 16) |
| libgdx-core members changed | 34 | 37 | 37 | 37 | **37 keys / 68 diff lines** |
| libgdx-core check counts (21) | identical | identical | identical | identical | **identical** |
| DEPENDENT scalac errors | not measured | not measured | +24, six lanes | +0, every lane | **+0, every lane** |
| `TextureHandle.scala` in existence | — | — | 9 | 1 | **1** |
| suites that ran | — | — | none of the six | all six | **all six** |

**Per lane, `just measure-all` exit 0, and every one of the ten lane HEADLINES is byte-identical to
the pre-policy run of the same checkout.** The before column is not the committed baseline quoted
from memory: the policy was stashed out, `measure-all` run, and all thirteen ports read **0 members
changed** — so the after column below is a diff against a reproduction, not against a claim.

| lane | errors | suite |
|---|---:|---|
| libgdx-core | 0 → **0** | — (the one legitimate `main` emission) |
| libgdx-test | 0 → **0** | **217 passing / 4 failing, 221 of 221 emitted**; all 4 DERIVED from `Substitutions.dropTypes com.badlogic.gdx.utils.Json`, 0 declared |
| ashley (+ test port) | 0 → **0** | **108 / 2, plus the 2 committed skips, 112 of 112** |
| anim8 | 0 → **0** | **23 / 0** |
| gdx-gltf (+ test ports) | 7 → **7** | does not run — the 7 are pre-existing `EngineGap`, byte-identical |
| gdx-vfx | 0 → **0** | **64 / 0** |
| screens | 0 → **0** | **16 / 0** |
| simple-graphs (+ test) | 0 → **0** | **16 / 0** |
| noise4j | 2 → **2** | pre-existing, byte-identical |
| jbump | 0 → **0** | ships no suite; the lane re-derives that zero |

`SYNTHESISED UNITS` reads **`1, 0 at an FQN a base already emits` on libgdx-core and `0, 0` on all
twelve other ports**, and `find -name TextureHandle.scala` returns exactly one path
(`ported/sge/src_managed/main/scala/sge/graphics/TextureHandle.scala`). That pair is the whole of
O5's closure, measured rather than argued.

#### The libgdx-core census, and the one number whose PROSE was wrong

| | measured (delivery) |
|---|---|
| seeded | **1** — `GLTexture#glHandle`, the only hint |
| propagated | the ctor parameter, `getTextureObjectHandle`'s return, `FrameBufferCubemap`'s local; **2 `RetypedSignature` decisions**, the declaration-level count the phase records (parameters and locals are deliberately not rows — §12.1 holds the parameter hole) |
| coerced at the boundary | **30** = 14 `TextureHandle(...)` wraps + 16 `TextureHandle.unwrap(...)` unwraps, counted in the emitted text |
| members changed | **37 distinct keys / 68 `members-unchanged` diff lines** — see the split below |
| every check count, all 21 | **identical**, `nullability-boundary` 160 → 160 included; `findings.tsv` and `counts.tsv` diff to **zero lines** |
| `decisions.tsv` | 3595 → **3598**: +2 `RetypedSignature`, +1 `RenamedPackage` (the minted unit). Every other port: unchanged, to the row |
| libgdx-core scalac errors | **0 -> 0** |

**`members-unchanged` counts DIFF LINES, not members, and the two numbers are not the same one.**
68 = 32 `<` + 36 `>`, over **37 distinct member keys**, and the split is:

- **31 moved** — same key, new digest;
- **1 key RENAMED** — `Texture#<init>(int,int,TextureData)` → `#<init>(int,T,TextureData)`, which is
  one member appearing as one `<` and one `>`;
- **4 genuinely new rows** — the minted unit `sge.graphics.TextureHandle` and its three members
  (`T`, `apply(int)`, `unwrap(T)`).

31 + 1 + 1 + 4 = 37 keys; 32 + 36 = 68 lines. The earlier prose in this section said "31 moved + 6
new rows: the minted unit and its three members, plus `Texture`/`Cubemap` unit digests" — the count
37 was right and the DECOMPOSITION was not: the `Texture` and `Cubemap` unit digests are in the
MOVED set, not the new one. Quote the split above.

**The decisions carry `reason=library-rule rule=primitive->opaque:com.badlogic.gdx.graphics.TextureHandle`**
— not `configured`, because the phase records the §1(c) classification the mechanism's own doc argues
for: which primitives are really a domain value is knowledge about one library, so the reader is sent
to that library's rule and not to a manifest key. Neither row renders a porter note, which is the
P2/P6 precedent standing (`RetypedSignature` and `RedirectedCall` are the two kinds `DESIGN.md` §7.2
deliberately leaves noteless), and `porter-notes` reports 0/0 accordingly. The minted unit's own
`RenamedPackage` DOES render a note, at the head of the file it names.

**The emitted code is what sge emits, in shape and site for site:**
`protected[graphics] var glHandle: sge.graphics.TextureHandle.T`,
`this(glTarget, sge.graphics.TextureHandle(sge.Gdx.gl.glGenTexture()))`,
`glBindTexture(this.glTarget, sge.graphics.TextureHandle.unwrap(this.glHandle))`,
`this.glHandle = sge.graphics.TextureHandle(0)`, and `def getTextureObjectHandle():
sge.graphics.TextureHandle.T`. Both engine fixes are visible in the output: O1's coercion is PUSHED
INTO EACH BRANCH at `TextureDescriptor#hashCode`/`#compareTo` (`if (this.texture == null) 0 else
TextureHandle.unwrap(...)`), and O2's retyped formal reaches the funnel, so `Texture`'s synthesised
primary reads `class Texture protected (sup$0: scala.Int, sup$1: sge.graphics.TextureHandle.T)`.
The two differences from sge's file are both un-landed OTHER work, not this step's: sge threads
`(using Sge)` (P5, reverted on CT7/CT8) and offers `.toInt`/`TextureHandle.none` as its own
extension sugar over the same opaque type.

**The FENCE is the load-bearing half, and its reason is structural rather than measured.**
`FlowPropagation.refSym` admits a NULLARY CALL, so `glHandle = Gdx.gl.glGenTexture()` is a real flow
edge to `GL20#glGenTexture`, whose `int` return makes it eligible — an unfenced run would grow the
seed set into the GL interface and retype it, which sge does not do (`GL20.scala:89` keeps
`def glGenTexture(): Int`). The unfenced variant was NOT run, so treat that as a derivation from the
edge rule and not as a number. What the fence buys IS measured: with the four GL interfaces scoped
out, every one of those crossings is a counted coercion instead, and they are 30 of them.

#### The BLAST is not confined to libgdx-core, and the shape is P1's

**Nine of the thirteen ports needed a baseline promotion, and eight of them for one line.** Adding a
phase to the base `surface` moves the `policy=` fingerprint in every module that INHERITS it through
`extendedBy`, so each of `libgdx-test`, `ashley` (+ test), `anim8`, `gdx-gltf` (+ test), `gdx-vfx`
and `screens` republishes a `port-map.tsv` whose header digest changed and whose every other row is
byte-identical. `simple-graphs` (+ test), `noise4j` and `jbump` do not inherit libGDX's manifest and
did not move at all.

| promoted | what moved |
|---|---|
| `LibgdxCoreMigrate` | `members.tsv` 68 lines (the 37 keys above) + `port-map.tsv` 56 lines: the `policy=` digest, `GLTexture`/`Texture`'s `primary=(int,T)` rows, one NEW `type … TextureHandle … form=object` row, and the member digests |
| the 8 libGDX dependents | `port-map.tsv`, **one header line each** — `policy=` only |
| `findings.tsv` / `counts.tsv`, every port | **nothing** — zero diff lines anywhere |
| `tests.tsv`, every port | **nothing** |

This is exactly the shape §11.15 recorded for P1 and §1.5 records for `CollectionsTransform`'s
retarget — "the fingerprint change reaching nine published port maps and nothing else moving". It is
not a surprise and it is not confined to one port; a delivery of a base-surface phase that promotes
only the base leaves eight stale published maps behind.

**"One header line each" is this wave's number and NOT the shape's, which the next wave to quote it
found out.** A base-surface change moves the `policy=` digest and nothing else in a dependent's map
*only when the wave adds no RUNTIME ARTIFACT*. `RuntimeArtifact` vendoring is unconditional — the
published module's types are listed in every module's map whether or not that module names one — so
a wave that adds a runtime type adds a SECOND line, `type <fqn> Added`, to every port map in the
corpus at the same time. Chunk 17 was measured against the sentence above and read TWO lines per
dependent, not one: the `policy=` digest and `type balticporter.runtime.JavaStack Added`. Both are
consistent and neither is a defect; what was wrong was quoting a wave's number as a rule. The gate
to state before such a step is therefore: **`policy=` per inheriting module, PLUS one `Added` row
per new runtime type in EVERY module's map** — and `simple-graphs`, `noise4j` and `jbump`, which
inherit no libGDX manifest, move for the second reason and not the first.

#### The GL evidence says ONE family, not twenty — and that is a measurement, not a scoping choice

The plan carried "GLHandle 8, GLEnum 12, Pixels/Seconds" from the adoption-gap catalog (§11.9). Those
are counts of what sge DECLARES. What matters for a transform is what sge APPLIES to a declaration
libGDX declares, and re-read against the hand port the two are not the same set:

| sge declares | applied to a ported declaration? | evidence |
|---|---|---|
| `TextureHandle` | **YES** | `GLTexture.scala:42` — `abstract class GLTexture(val glTarget: TextureTarget, private[graphics] var glHandle: TextureHandle)`, `def textureObjectHandle: TextureHandle` |
| `ProgramHandle`, `ShaderHandle` | no | `ShaderProgram.scala:96,99,102` — `private var program: Int`, `vertexShaderHandle: Int`, `fragmentShaderHandle: Int` |
| `FramebufferHandle`, `RenderbufferHandle` | no | `GLFrameBuffer.scala:73,79` — `depthStencilPackedBufferHandle: Int`, `colorBufferHandles: DynamicArray[Int]` |
| `BufferHandle`, `UniformLocation` | no | `GLHandle.scala:125` — the family's real home is `GLHandleOps`, EXTENSION methods on `GL20` |
| `AttributeLocation` | yes, but **INEXPRESSIBLE** — see below | `Mesh.scala:555` types libGDX's `void bind(ShaderProgram, int[], int[])` as `Array[AttributeLocation]` |

So six of the eight handle types are a typed layer offered to CONSUMERS beside the raw one, and
`GL20.scala:89` keeps `def glGenTexture(): Int` to prove it. The `GLEnum` family is a second shape
again: sge types GL20's PARAMETERS (`glCreateShader(type: ShaderType)`) but its ~200 `GL_*` values
are hand-authored named constants with no Java counterpart, and this mechanism retypes declarations
— it does not mint a constant vocabulary. §11.9's sentence about that layer being ecosystem
infrastructure is now a measurement rather than an impression.

**Configuring those would have emitted a surface the reference port deliberately does not have.**
That is the §1(c)-written-from-a-wish failure, and the reason the GL half of this step is one
`OpaqueSpec` and not twenty.

**And `AttributeLocation` is a MECHANISM limit rather than a policy choice, which is worth its own
line because it is the one row an agent would otherwise re-attempt.** sge really does retype
`Mesh#bind`'s `int[] locations` to `Array[AttributeLocation]` — a ported declaration — but the
element of an array is not something this phase can name. `taggablePrim` tests a symbol's OWN info
against the spec's primitive, and `int[]` is not `scala.Int`, so neither a hint nor a pure-move edge
can reach the element; `FlowPropagation`'s edges are between symbols, and an array's element has no
symbol of its own. An `OpaqueSpec` whose family lands inside a container is therefore inexpressible
today. That is a mechanism gap and not a `RuleScope` question, and it is NOT counted in the 6 errors
of attempt 1 because the family was never configured.

**It is still inexpressible and it is no longer SILENT.** The half that made this expensive was that
a hint naming such a declaration matched nothing and read exactly like a typo; the phase now reports
it as a `policy` finding that says (a) ENGINE and points at `ENGINE-LIMITS.md` §13 O3. So a port that
writes this hint learns in ONE RUN that the mechanism cannot reach it, instead of hunting a
misspelling that is not there.

#### The policy, as shipped

```scala
def textureHandle: PrimitiveToOpaqueTransform =
  new PrimitiveToOpaqueTransform(OpaqueSpec(
    fqn        = "com.badlogic.gdx.graphics.TextureHandle",
    hints      = _.fullName == "com.badlogic.gdx.graphics.GLTexture#glHandle",
    underlying = OpaqueSpec.Primitive.Int,
    scope      = RuleScope.Everywhere(except = Set(
      "com.badlogic.gdx.graphics.GL20", "com.badlogic.gdx.graphics.GL30",
      "com.badlogic.gdx.graphics.GL31", "com.badlogic.gdx.graphics.GL32")),
  ))
```

The 6 errors attempt 1 cost were `ENGINE-LIMITS.md` **O1** (3 — a coercion reads the boundary term's
own type, so a seed reaching it through an `if` is invisible) and **O2** (3 — a retyped PARAMETER
leaves its method's `MethodType` stale, and the ctor funnel correctly reads the signature). **Neither
had a policy exit**, and that is the load-bearing negative: O1's errors are at CALLERS of a retyped
member, so no `RuleScope` can un-retype the callee; O2's are in a SUBCLASS of the seeded class, so
scoping the subclass out cannot change the parent's formal. The 24 of attempt 2 were **O5**, which
has the same shape one level up and is why it was also the engine's. All three are closed.

#### Two interactions settled, both negative, both worth the words

- **P3's `@Null` union floor does not meet this.** `NullabilityTransform` REFUSES a bare primitive
  (`Issue.PrimitiveType`) and this phase seeds ONLY bare primitives (`taggablePrim`), so the two
  domains are disjoint by construction and no `TextureHandle.T | Null` can arise. Confirmed
  empirically on the delivery run: `nullability-boundary` **160 -> 160**. There is no ordering hazard
  behind that either — the union floor would have to reach an `int` for one to exist, and
  `NullabilityTransform`'s own note records that no bare-primitive annotation exists in the corpus.
- **No dependent CONSTRUCTS a `primitive->opaque` phase** (the §1.5 instance-count question — the
  only corpus construction site is `demo/OpaqueDemo`, which is not a port). One instance, inherited
  through `extendedBy`; nothing to merge, and D9's shape is not in play, so `MergeablePolicy` is
  deliberately not implemented (`DESIGN.md` §8.13). **What that question did NOT ask, and O5 is the
  answer to, is whether the ONE inherited instance RUNS in each dependent — it does, and running is
  what mints.** The two are different questions and only the first was asked.

**Members changed: 37 on libgdx-core and 0 on every other port** — and the second number is a
MEASUREMENT rather than a restatement of the first. No corpus dependent emits a reference to the
retyped surface at all: gdx-gltf's `SharedTextureTest` is the only Java in the corpus that names
`getTextureObjectHandle`, and it is not in the gltf test port's file set. So the corpus cannot
witness "a dependent that mints nothing still coerces"; `OpaqueMintOwnershipSpec` pins that, with a
fixture whose propagated seed provably lands in a dependent-owned unit.

#### `Pixels` and `Seconds` are NOT measured, and the reason is not that they lack evidence

Stated plainly because the omission is the kind a later reader would otherwise mistake for a
verdict. **Both families have broad, real evidence** — far broader than `TextureHandle`'s. `Pixels`
appears at 171 annotation sites across sge, on ported declarations including `Input#getX`,
`InputProcessor#touchDown` and `ApplicationListener#resize`; `Seconds` types every
`act(float delta)` in the scene2d tree (`Actor`, `Stage`, `Group`, `Action`) plus `Screen#render`
and `Graphics#deltaTime`. (The 171 is a count of SITES, not of ported declarations — the per-family
seed harvest is part of the task that configures them.) Neither is a consumer-side layer; both are
exactly the shape this mechanism exists for.

They were not configured with `TextureHandle` because §5's "change one thing, then measure" cuts
here exactly as it does anywhere: two families in one commit could not be told apart, and before O1
and O2 were closed a run would have reproduced the same two diagnoses at a larger multiple and
bought nothing the 6 errors had not already bought. O5 then blocked them for the same reason it
blocked `TextureHandle` — every family mints a unit, so every family duplicated it into every
dependent, and the cost scaled with the number of DEPENDENTS rather than with the family's size.

So the honest state is **`Pixels`/`Seconds`: evidenced, unconfigured, unmeasured** — and now
unblocked. With O5 closed the mint is one module's whatever the family's size, so they are the next
step and the one that will say what the mechanism costs at scale. Note what `TextureHandle` does NOT
tell you about them: it is one FIELD, and O2's fix is exercised there by a single constructor slot.
`Pixels` and `Seconds` are parameters almost exclusively, so they are the first real measurement of
the parameter path — expect that to be where the next shape appears, and configure them ONE AT A
TIME. §12.1's "a RETYPED parameter records no decision at all" is the hole they will walk into
first.

##### Do NOT retry

- **Do not configure `GLEnum`, `ProgramHandle`/`ShaderHandle`/`FramebufferHandle`/
  `RenderbufferHandle`/`BufferHandle`, or `UniformLocation` on libGDX.** The reference port applies
  none of them to a ported declaration; the table above is the evidence, and re-deriving it costs a
  session. This does NOT extend to `Pixels`/`Seconds`, which are a different case entirely — see
  above.
- **Do not re-derive the 6 errors of attempt 1, or the 24 of attempt 2.** They were O1, O2 and O5,
  all three closed, and the five columns above are the proof.
- **Do not measure a family of this phase on libgdx-core alone.** A libgdx-core-only run reproduces
  every number in the base census and is worth nothing as evidence about the step — that is exactly
  how the first two proofs passed while the step could not land. The gate is `just measure-all` and
  the per-lane table, suite outcomes included.
- **Do not promote only the base's baseline when a base-SURFACE phase lands.** Eight dependents'
  `port-map.tsv` headers move with it, by one line each, and leaving them is eight stale published
  maps that the next run silently re-diffs.
- **Do not reach for a `RuleScope`, an `extendedBy` subtraction or a dependent-side drop to clear a
  residue of this family.** All three gaps were outside every scope's reach, for the reasons given
  above, and any successor gap in this phase should be classified the same way before a manifest
  entry is written.

## 11.99 The idiom layer — wave 0's published denominators, and what they decide

`DESIGN.md` §8.15 says what licenses this layer and why its safety argument is a refusal enumeration
rather than a suite result. This section is the NUMBERS: what the three census phases found, per
port, on the run that shipped them. They are reproducible with `just measure-all` and they are
re-derived on every run — the check prints its own denominator, so nothing here can go stale as
prose while the code moves.

**The wave was EMISSION-INERT and that is what makes the numbers worth anything**: 0 member digests
on all fifteen ports, every `expected-errors` and every `expected-lost` unchanged, every suite
outcome identical. A census that moved emitted text would be measuring a tree it had already
changed.

### What the three lanes count, and the one thing to read first

A row is `Converted` or `Refused(guard)`, and the guard is the first thing to read. `NotRequested` is
NOT a property of the site: it means *this site passes every guard and the port has not asked*, so it
is the DENOMINATOR a widening converts. Every other guard is a delta the port carries deliberately,
and a guard's own `why` says whether it is permanent. At wave 0 every row here was a refusal because
nothing converted yet; the SAM lane and the bean lane both convert now, and the censuses that
published their denominators are retired — a transformer files one row per site CONSIDERED, which IS
the denominator, and a census beside it would be a second answer to its own question (§4.6).

### Per port

| port | SamLambda considered / convertible | BeanCollapse considered / collapsible | `return this` considered / ancestor-typed |
|---|---|---|---|
| `sge` (libGDX core) | **155 / 23** — 127 `NotSam`, 5 `NonCapturing` | **137 / 60 CONVERTED** — 30 `ComputedBody`, 30 `MutableStorage`, 14 `OverriddenBelow`, 2 `PairRefused`, 1 `ConcreteRelative` | **709 / 2** — 678 `SelfTyped`, 29 `NotAlwaysThis` |
| `sge` test set | 9 / 0 — 6 `NonCapturing`, 3 `NotSam` | — | 0 |
| `sge-ecs` | 0 | — | 5 / 0 |
| `sge-ecs` test set | 13 / 0 — all `NotSam` | — | 0 |
| `sge-gltf` | 48 / 0 — all `NotSam` | — | **29 / 2** |
| `sge-gltf` test sets | 0 | — | 0 |
| `sge-vfx` | 1 / 0 | — | 14 / 0 |
| `sge-anim8` | 3 / 0 — all `NonCapturing` | — | 5 / 0 |
| `sge-screens` | 0 | — | 0 |
| `sge-graphs` | 0 | — | 0 |
| `sge-noise` | 3 / 1 | — | 14 / 0 |
| `sge-jbump` | 9 / 3 — 6 `NonCapturing` | — | 0 |
| `ssg-liquid` | 24 / 0 — 23 `NotSam`, 1 `NonCapturing` | — | 29 / 0 — 26 `SelfTyped`, 3 `NotAlwaysThis` |
| `ssg-liquid` test set | 50 / 0 — all `NotSam` | — | 0 |

Every row is scoped to THAT MODULE's own declarations (`ENGINE-LIMITS.md` D2). The first run of these
lanes was not, and it showed exactly the shape D2 describes: five dependents each reported the libGDX
base's identical 24 convertible SAM sites as their own, so a dependent's own rows were a minority in
its own report. `BeanCollapse` has a row only where the port carries a `bean-properties` phase, which
is the libGDX base alone.

### What the numbers decide

- **the SAM transformer has a real population and a large honest refusal set.** On the libGDX base,
  155 anonymous-class sites considered: **23 convertible**, 127 declined because java's own SAM rule
  (JLS 9.8) does not admit the target at all — libGDX's listeners are abstract CLASSES, not
  interfaces — and 5 declined on instance identity. A **15 %** conversion rate, which is the shape
  §8.15's charter narrowing predicts and not the raw `197 anonymous-class instantiations` an
  upstream grep suggests. It is also the number wave 1's blast must PREDICT: 23 declarations on the
  base, 3 on jbump, 1 on noise4j, and zero everywhere else;
- **the bean `var` collapse CONVERTS 60 of 137 on the only port that carries the phase**, and the
  three numbers this line used to hold are the reason the census behind it is gone. Wave 0's census
  said 91, this document said 93, and the transformer says the collapsible population was 90 —
  because a census beside a transformer answers the transformer's question without asking the
  transformer's guards (2 of its rows were pairs the def-pair path itself refuses, 1 a concrete
  relative). The port asks for all 90 and gets 60; the 30 it does not get are one shape under one
  guard, `MutableStorage` — a get-only property over storage the program assigns elsewhere, where a
  `val` would not compile and a `var` would publish a writer java never had, so the `def` pair is the
  faithful form and stays. The other 77 rows are permanent library facts: 30 `ComputedBody` (the
  exact refusal §8.5 kept bodies verbatim for), 14 `OverriddenBelow`, 2 `PairRefused`, 1
  `ConcreteRelative`. `NotRequested` is 0 — nothing collapsible is left unasked. The residue the
  whole design was gated on fell with it: **280 -> 220 `$field` DECLARATIONS** in
  `ported/sge/src_managed/main`, exactly one per collapsed pair, and 13 references in
  `ported/ssg-liquid` untouched because that port carries no `bean-properties` phase;
- **`return this` → `this.type` is REFUSED on its own number.** 709 methods in the libGDX base answer
  `this`; **678** already declare the declaring class as their return type, where `this.type` buys
  precision only at a call on a subclass, and **29** answer something else on another path. The
  bucket that removes a downcast at every chained call — a declared return type that is a strict
  ANCESTOR — is **2** on the base and **4** on gdx-gltf. A wave spent on 709 member digests for six
  removed downcasts is a wave spent on churn, so the transformer is not built. This is the row whose
  go/no-go nobody had a number for, and the number says no.

### I9 is CLOSED, and closing it found a live silent defect in the spearhead library

`Tree.Lambda` now carries `resultTpt` — the SAM METHOD's own result type, filled by whoever holds
the method — so the emitter's `JS-S21` arm can interpose the nested `def` that restores java's
*`return` leaves the LAMBDA* instead of refusing. `SamLambdaTransform` fills it from the anonymous
class's own `DefDef`, which is exactly the type M6 said the TIR did not have.

What the narrowing left is COUNTED (`OmissionCheck.unnameableLambdaReturn`), and the count is the
finding: **libGDX core `omissions` 66 → 69, its test set 3 → 4, at 0 compile errors on both, 0 member
digests on all fifteen ports and every other count flat.** Those four rows are lambdas the SOURCE
wrote — `TextField$NativeOnscreenKeyboard`'s validator and close callback, and `JsonMatcherTests`'
watcher — where the port emits `(toCheck: String) => { …; return true }` inside a `def …: scala.Unit`.
That is a scala NON-LOCAL RETURN from the enclosing method: java returns `true` to the framework, and
the port unwinds out of `openNativeInputField`, long after it returned. M6's *"left as a compile
error deliberately"* is false for this construct and `ENGINE-LIMITS.md` M6 now says so with these
numbers.

**All four are now fixed, and the residue M6 still stands for is 0.** The frontend is the second
supplier — a lambda the source wrote has its method in a CLASS FILE, which is where java's SAM
question is already asked, so `SpoonTir.samResultTpt` reads the result off the same abstract-method
list `Sam.Answer` is computed from. **`omissions` 69 → 66 on libGDX core and 4 → 3 on its test set, at
0 errors, with a blast of exactly 4 members** (`TextField$NativeOnscreenKeyboard#show` and
`#openNativeInputField`, `JsonMatcherTests#watcher`, and the two enclosing class digests) and 0 on
the other thirteen ports; suite outcomes identical (gdx-test 217/4, liqp 636/1). What is still
refused is a SAM whose result type is a TYPE VARIABLE (`Supplier<String>.get` is `T get()`), which is
0 sites corpus-wide and non-vacuous by fixture.

### Wave 1 is WIRED, and its blast was PREDICTED rather than discovered

`SamLambdaTransform` is in every port's pipeline, FIRST, `runsBefore` the two engine phases whose
retyping would move what it ascribes to. It is §1(a), so `PortRun.idiomPhases` weaves it exactly as
it appends the package rename: no manifest entry, no fingerprint, no switch.

**The wave-0 census is retired with it.** `SamLambdaCensus` was this decision with the rewrite
removed, and its whole purpose — publish the population before anything converts — is served. With
the transformer in the pipeline it would be a SECOND answer to a question the transformer already
answers, since the transformer files one row per site CONSIDERED (`Converted`, or `Refused` naming
the guard), which IS the denominator. Two phases at one position filing about one site would double
every row in the lane; §4.6's one-mechanism-one-seam retired it, and `SamLambda.decide` — which both
always called — is what survives.

| port | wave-0 predicted | wave-1 converted | `idiom(refused)` | `members.tsv` |
|---|---|---|---|---|
| `sge` (libGDX core) | 23 of 155 | **23** | 1001 -> 978 | 23 members |
| `sge-jbump` | 3 of 9 | **3** | 9 -> 6 | 4 members |
| `sge-noise` | 1 of 3 | **1** | 17 -> 16 | 2 members |
| every other port | 0 | **0** | flat | **0** |

Every changed digest is attributable: 11 of the 23 libGDX rows are members carrying a `SamLambda`
decision (`AsyncExecutor#<stmt1>` is the promoted constructor's body, whose decision is subjected at
`#<init>`), and the other 12 are the enclosing class digests those members sit in. **`Δ \ blast` is
empty on all three ports.** The twelfth decision, `NetJavaImpl#sendHttpRequest`, has no member row at
all — that type is dropped — which is why `porter-notes` stays 0 rather than reporting it.

Three other counts moved and each is explained:

- **`trivia(recovered)` 4 -> 6** on the libGDX base, predicted in the design: the anon method's
  Javadoc loses its carrier and the emitter's backstop quotes it with its coordinates.
  `trivia(lost)` stays 0, which is the gate;
- **`context-seam` 44 -> 43**, because a converted anonymous class is one fewer synthetic type for
  that walk to reach;
- **`catalog(consulted)` +1 and `catalog(unreached)` -1 on jbump and noise4j and NOWHERE ELSE.**
  This is the design's `[rev-2: D4]` correction measured from the other side: the count is a count of
  ROWS, so a port where `JS-S21` was already consulted cannot move it — and a port that had no lambda
  at all until the conversion made one moves it by exactly one. A constant prediction would have been
  wrong on both halves of the corpus.

Two engine defects were found by this wave's own gate and fixed in their own commits before it
landed: the emitter's `body$N` counter was PROGRAM-global (`ENGINE-LIMITS.md` M10's defect at a
second construct — conversions in `Cubemap` renamed `TextField`'s lambdas), and a `SamLambda`
decision subjected at a PROMOTED CONSTRUCTOR had no `def` to sit above, so its note never appeared
(`porter-notes` 0 -> 1). The promoted constructor's notes now join the class's, where its Javadoc
already goes.

### Two things the census found that no count would have

- **`Pipeline.order`'s tie-break was a FIFO**, so a phase added with a `runsBefore` edge reordered
  the phases already there — `CollectionsTransform` ran ELEVENTH on the libGDX base where the port
  wrote it SECOND. Found because two phases whose `run` returns its argument moved a
  `collection-boundary` count 22 → 20 and two member digests. Fixed in its own commit
  (`DESIGN.md` §8.13's last subsection), corpus blast 0;
- **guard 4 (`this` binds to the ANON in java, to the ENCLOSING class in scala) is blind to the
  shape it will meet most**, because where `this` is the TARGET of a member access the frontend
  builds no `Tree.This` at all — `this.toString()` in an anonymous `Runnable` arrives as a bare
  `Tree.Ident(java.lang.Object#toString)`. Zero corpus sites; the fixture is the whole evidence.
  `DESIGN.md` §8.15's last subsection carries the rule.

---

## 12. Remaining work, across the engine

Maintained by deletion. Items are ordered by what they block, not by size.

### 12.1 Provenance coverage — decisions that are not yet recorded

- **`TestFrameworkTransform`'s synthesised `beforeAll`/`afterAll` record no decision.** They are
  definitions with no Java behind them, which is precisely the case a reader cannot explain from the
  line itself.
- **A REFUSAL renders no porter note, and a refusal is the decision a reader can least reconstruct.**
  A decision whose `subject` is `SymId.None` — a policy entry the phase declined to apply — has no
  declaration to sit above, so it reaches `decisions.tsv` and the check report and never the emitted
  line. Measured by P4 (§11.18): five `bean-properties` refusals, five `policy` findings, zero notes,
  and an agent reading `def getScrollX(): Float` has no local evidence that a policy entry asked for
  it. `PorterNote.InBody` on the OWNING type is the shape that fits, and the same hole covers every
  future phase that refuses per entry.
- **A base's per-entry REFUSALS are republished in every dependent's `decisions.tsv`.** `D2`'s module
  scope filters by DECLARATION, and the one decision kind with no declaration is therefore the one
  kind that leaks: P4's five refusals appear in all seven libGDX dependents' artifacts (§11.18). The
  `policy` findings are correctly scoped — only the provenance artifact is not — so the fix is to
  scope a subject-less decision by the OWNER FQN the key names, which the run already translates
  through `PackageRenameTransform.renamed` for `dropped-types.tsv`.
- **Raw-generic `[?]` rendering and `uncheckedGeneric` retyping are unrecorded.** Both change a
  signature for a reason no reader can recover; recording them needs the decision log threaded through
  the frontend, which today records only from phases and the run.
- **A retyped PARENT records nothing.** `CollectionsTransform.recordRetypings` walks the symbol
  table and fires on `Symbol.info`; a class's parent list is in its `Definition`, not its `info`, so
  a `class X implements Comparator<T>` that becomes `class X extends Ordering[T]` moves its emitted
  declaration line with no `decisions.tsv` row behind it. **Measured by P2 (§11.16): nine classes
  across five ports, and it is why four of those ports show changed members against zero
  `Configured` rows.** The same hole covers any phase that retypes a parent — the retarget is
  simply the first policy that does. The fix is one more pass over `Definition.parents` in the same
  `before`/`after` comparison, at the DECLARATION level the channel already uses.
- **A scoped-out PARAMETER records no decision at all.** `NullabilityTransform.scopedOut` skips a
  symbol whose `flags.isParam` — the §5.1 rule that a decision is recorded at the DECLARATION —
  but it does not then record against the parameter's METHOD, so a scope entry that holds back only
  a parameter is a policy the artifact cannot see. Measured on screens (§11.17): two entries were
  needed to clear the K13 errors, `ScreenManager#transition` and `ScreenManager#pushScreen`, and
  only the first produced a `ScopedOut` row and a porter note; the method whose signature the scope
  is the reason for shows nothing. The same shape reaches any retyping phase with a scope. The fix
  is to attribute a scoped-out parameter to its owner, once per owner, exactly as
  `declarationOf` already does for the retype side.
- **…and a RETYPED parameter is the same hole on the other side.**
  `PrimitiveToOpaqueTransform.record` fires on `seeds(s) && Decision.isDeclaration(s)`, and a
  parameter is neither: it is skipped as a parameter and its METHOD is not a seed, so a constructor
  whose formal became an opaque type moves its emitted signature with no `RetypedSignature` row
  behind it. `Decision`'s own doc is what says that row is owed — "a method's `info` is a
  `MethodType` carrying its parameter types, so a parameter whose type moved moved the method's
  signature and is one decision, not two" — and here the one row is the one nobody records. Seen
  while closing `ENGINE-LIMITS.md` §13 O2, which is what MADE the method's signature move; left
  deliberately out of that delivery so the replay's decision count stayed comparable to P6's
  (§5's "change one thing"). Fixing it moves `RetypedSignature` and adds a porter note, so it is a
  measured step of its own, and it covers every retyping phase whose seeds can be parameters.
- **`RetypedSignature` and `RedirectedCall` carry no porter note.** The argument for each is in
  `DESIGN.md` §7.2 and stands — the retyped signature IS the declaration and the redirected call IS
  the body — so it is listed here so that adding one is a decision rather than an oversight.
  (`FunnelledCtor` was on this list and is now rendered: its argument was written for a PROMOTION
  and is false for a SYNTHESIS, which is what the funnel now does for most multi-constructor
  classes. See §7's A2 section.)

### 12.1.5 Base-surface sites the published map CANNOT answer

Every whole-program index in the emitter is a question a dependent asks about a program that
CONTAINS its base (`DESIGN.md` §8.3). Five are migrated — the funnel's fixpoint, the class-vs-object
collapse, the constructor replay's `vis=` lookup, §4.55's two field-rename passes, and the `export`
exclusion lists' `statics=`. The rest are named here rather than left to be re-discovered, each with
THE CONTRACT KEY IT AWAITS, because a site whose answer is wrong only across a module boundary is one
nothing in a single-module run can see:

| site | what it answers about a BASE type | the key it needs | why the map does not carry it |
|---|---|---|---|
| `TirEmitter.funnelParamRenames` | the `$p` rename of a base class's PROMOTED constructor parameter | a member row for an ENGINE-MINTED member | a promoted parameter has no emitted DECLARATION — it IS the class's parameter list, so the source map records no row for it. `Surface.NotCarried` names it in code. **Its FUNNEL is scoped now** — it was the fourth site to build `CtorFunnel.Plans` with no view, so the plan it read a base class's promoted parameters from was not the plan the base emitted (D4). What is still open is the member row, not the fixpoint |
| `TirEmitter.rawParentAlignment` / `overrideAlign` | the TYPE at which a base parent's parameter was RENDERED | a `sig=` row | schema 3 publishes a `primary=` DESCRIPTOR, which is the erased source spelling; an override alignment needs the rendered Scala type, which no column holds |
| `TirEmitter.diamondOverrides` | is a base parent's method CONCRETE | a `concrete` flag on the member row | not published; `MemberShape` carries `name`/`vis`/`placement` only |
| `GlobalsToImplicitsTransform` seeds + closure | does a base declaration carry a `(using C)` clause | a `usingClause=` on the member row | not published, and the phase is default-off in every port |
| `PrimitiveToOpaqueTransform` seeds | is a base declaration RETYPED to an opaque type | a `retyped=` on the member row | as above |
| `CollectionsTransform` scope (`RuleScope.Only` growth) | is a base declaration inside the retyping scope | as above | as above |
| `FlowPropagation.edges` | — | — | whole-program by construction; it is the SUBSTRATE the three phases above grow over, so it is answered by whatever they are |

**The last four are ARMED, not latent.** P2/P5/P6 landed, so the phases exist and run; what keeps
them from producing a cross-module divergence today is that no port declares a holder, an
`OpaqueSpec` or a `RuleScope.Only` that reaches a base declaration. The first one that does will hit
this table, and the entry to write then is a contract column — not a re-derivation, which is the
whole of what `ENGINE-LIMITS.md` D4/D5 measured.

### 12.2 Control flow

- **`labelSeq` is program-global**, so a control-flow diff is never file-local: emitting one new
  boundary shifts every subsequent label name. Nothing is wrong with the output; the *diff* is
  unreadable, which is a measurement cost (`CLAUDE.md` §5).

### 12.2.5 `findings.tsv` was a COMMITTED baseline that no lane compared — and the divergence it hid was a STALE ACCEPT, not a checkout

`just baseline-accept` promotes seven artifacts and the lanes gated on five of them at the time:
`counts.tsv`
through `show_check_report`, `members.tsv` through `just members-unchanged`, `tests.tsv` through the
correlator's diff, plus `expected-errors` and `expected-lost`. `findings.tsv` was promoted and never
diffed, so **every row's DETAIL — the owner it is attributed to, the `UsageKind` it was seen at, and
the running totals some checks print inside their own text — could move with nothing reporting it.**
That is deliberate for the row IDs, which are position-sensitive and would churn; it was never
deliberate for the content, and `findings_baseline_guard` now compares the id-stripped file
(`cut -f2-`) and fails the lane in either direction.

**The first thing it found was its own reason for existing, and the attribution recorded here was
WRONG.** Eight committed baselines — every libGDX DEPENDENT, and not the base — carried rows the
checkout could not reproduce: `catalog(consulted)`'s member-clash row, and two `portability(all)`
rows on libgdx-test attributed to `AsyncExecutor#<init>` at `TypeRefPos` where the baseline said
`AsyncExecutor$165#newThread` at `MemberType`. This section previously read that as belonging to the
CHECKOUT — a worktree-versus-primary difference of the §5.4 family — on the evidence that the parent
commit reproduced it byte for byte in the same worktree. That evidence is real and it does not
support that conclusion: reproducing at the parent proves the divergence is PRE-EXISTING, which is
equally true of a stale accept.

What settles it is the CONTENT of the row, which nobody had looked at. It reads
`phase member-clash — consulted 0, fired 0, declarations 281`, and the base's own wave-2 commit moved
that population `280 -> 220`. The dependents' number is therefore the one from BEFORE that commit:
the baselines were last accepted at waves 0/1 (`git log -1 -- port-report/<dep>/baseline/findings.tsv`
names them), the engine moved under them, and no gate could demand a re-accept because nothing
compared the file. A declaration COUNT and an anonymous-class ordinal are not path-derived, so no
symlink and no relativisation can produce either difference — this is `M11`'s shape exactly, one
artifact over.

The eight are re-accepted from a fresh sweep of every lane at the commit that ships the gate, and the
gate is what keeps them current: the same drift now fails the lane on the run that causes it. The
worktree caution below still stands on its own terms, and is now enforced rather than advised —
re-run the lane before `just baseline-accept` (`CLAUDE.md` §5.1), because a baseline promoted from a
`run-latest` that predates a later edit in the same wave is exactly what produced these eight.

**`port-map.tsv` was the SAME SHAPE and stayed ungated for two more waves — SHIPPED in §12.4.6.** It
was never a hypothetical: publishing `Surface.MemberShape.form` moved **60 member rows** in the
libGDX base's map in one commit, and the only reason anybody saw it was that the commit was expected
to move exactly those rows and somebody diffed the file by hand. A base's map decides a dependent's
EMITTED TEXT (`TirEmitter.baseName`, and now the collapse comparison), so a row that changes there
without an acknowledgement is the F3 failure with a larger blast radius. `port_map_guard` is that
gate; the second incident, the numbers it landed on and the one place it is NOT a plain `diff` are in
§12.4.6.

### 12.2.6 The portability MENU, and the two things measuring it found

`DESIGN.md` §8.16 shipped the selection plumbing with no menu; §8.18 is the first one — `Remediator`'s
three verified templates made selectable (`RemediationTransform`: `substitutions-drop`,
`static-forwarder-inline`, `class-table`) plus `PortabilityCheck`'s own `accept-jvm-only`. Two things
came out of trying to demonstrate it on real ports, and both are results rather than notes.

**`portability(emitted)` had never excluded a dropped type on a RENAMING port** (`ENGINE-LIMITS.md`
P7). The emission gate reads the frontend's `Substituted` TAG; the portability block read the
`dropTypes` KEY against a renamed `Symbol.fullName`, so the set was always empty and
`portability(emitted)` equalled `portability(all)` exactly. libGDX core **153 -> 69**, `remediation`
**30 -> 15**, `jdk-surface` **24 -> 22**, at 0 compile errors and **0 moved member digests** — nothing
in the run could see it, and what found it was asking why the menu had no defensible site.

**And after the fix it still has almost none, which is the honest state of this corpus.** Of the
three tree-changing remedies:

- `substitutions-drop` — two genuine HIGH chokepoints remain on libGDX core (`sge.input.RemoteInput`,
  `sge.utils.AtomicQueue`) and neither is droppable for a reason OUTSIDE the engine: the reference
  hand port PORTS `RemoteInput` (§3.5 — quote what they emitted), and `AtomicQueue` has a suite in
  this port's own test source set. That is exactly what `Remediator`'s own doc says the HIGH grade is
  and is not: the engine's half of the claim is measured, and whether the port SHOULD drop is the
  port's judgement;
- `static-forwarder-inline` and `class-table` — the one port that had a site for either is libGDX
  core, and it already carries the hand-written equivalents (`unwrapReflection`, `classTable`) plus
  the `dropTypes` entry that removes the wrapper. With P7 fixed, both suggestions correctly disappear.

So the three ship with **fixture coverage and no live selection** (`RemediationMenuSpec`, 14 cases
including every refusal guard), and saying so is better than inventing a demonstration on a port that
does not want one.

**`accept-jvm-only` WAS demonstrated live on liqp, the arm that fired was the refusal, and the site is
now FIXED** (`ENGINE-LIMITS.md` P6). `liqp.spi.SPIHelper#findProviders` is
`ServiceLoader.load(TypesSupport.class)` — the site §12.2.7's descriptor exists for — and liqp
declares all three platforms, so accepting was a contradiction the run reported with both real knobs
named: `remediation` **12 -> 14** with `portability(emitted)` FLAT at 56, the refusal not draining,
which is exactly what its own row said. §12.4.7 is what that refusal was pointing at, and the
selection is gone rather than kept as commentary — a key that binds, names a live remedy and can
never apply is an inert selection, which is a `policy` finding of its own. **The corpus therefore has
no live `accept-jvm-only` selection**, and that is the honest state: the one port that reached for it
has a real answer now, and the entry's fixture coverage (`RemediationMenuSpec`) is unchanged.

### 12.2.7 `serviceProviders` — P5's second half, and liqp's last hand-written file

`ENGINE-LIMITS.md` P5 is CLOSED. `PortManifest.serviceProviders` names the upstream
`META-INF/services` descriptors a module ships and the run copies each into
`src_managed/<config>/resources/`, translating the FILE NAME and every PROVIDER LINE through the run's
own `emittedName` (`DESIGN.md` §8.17). liqp is the only corpus library that ships one, and it is the
whole of the real-port evidence: its hand-written
`ported/ssg-liquid/src/main/resources/META-INF/services/ssg.liquid.spi.TypesSupport` — kept in step
with `packageRenames` by hand — is DELETED, the same two lines are emitted renamed, the lane's
`--resource-dir` moved to the build product, and the suite is unchanged (**636 passing, 1 failing**,
no new failure). New lane `service-providers` **0 -> 2** on that port and recorded on no other, which
is `PortRun.requiredChecks` deriving the requirement from the same declaration the work is.

The negatives — a dropped provider, a dropped service, an empty descriptor, a name the rename did not
move, a line the format does not admit — are reachable on NO corpus port and are `ServiceProvidersSpec`'s
ten cases. That spec is the coverage.

### 12.2.8 What the menu program deliberately did not build

Two recurring hand-written shapes were surveyed for mechanisation alongside the menus and left
unbuilt, each with the reason stated so the decision is not re-derived:

- **Reflective-instantiation-to-registry as a §1(b) phase — READ OUT AND REFUSED**
  (`ENGINE-LIMITS.md` P10). Three ports hand-wrote the same shape — libGDX core's injected `Pools`,
  Ashley's `ComponentFactories`, gdx-gltf's `GLTFExtensionFactories` — and this entry recorded it as
  three instances of one mechanism with two free parameters, the strongest candidate for the next
  engine wave. Both halves of that description were wrong, and the correction is what the reading
  produced. **The two parameters are one**: both survivors expose a public `register` (gdx-gltf's is
  open, not closed), and "reflective fallback vs none" and "null vs throw" are the same axis — what
  the port does at a MISS — which `Pools` then breaks by reading ONE table two ways (`getOrNull`
  answers `null`, `get` throws). **And it is not one mechanism**: the abstraction that covers all
  three has to be value-generic, because `Pools` holds shared `Pool` VALUES rather than factories and
  derives its key by probing a factory — value-generic it is a `Class`-keyed `Map`, which
  `balticporter/runtime/package.scala` does not admit (it takes *semantics the target LACKS*, and a
  `Map` is not one; its stated "could a correct emitter have avoided it?" test answers NO and would
  let it in, which is the half P10 spells out). Narrowed to the two real instance registries it stops
  being a `Map` and is then deliverable only by a BASE port, which neither of those two is —
  `RuntimePlan` reads its required set off the phases that ran, a dependent inherits the base's, and
  vendoring on a dependent writes every base shim twice. What the extraction would have saved is the
  table declaration and the hit/miss dispatch and nothing else — **5 lines of `ComponentFactories`
  (of 51), 5 of `GLTFExtensionFactories` (of 66), 4 of `Pools` (of 169)**, less a construction line
  back in each, so **8 net across three ports** — plus **one** finding on **one** port (Ashley's
  `portability(injected)` 4 → 3, the `java.util.concurrent` map; libGDX's `Pools` files 0 there).
  Two of the three sites were already reached by one `MethodBodyTransform` key each and the third by
  `dropTypes` + `inject`, so no transform was missing either. The three instances now cite P10 in
  their own doc comments; the two conditions that would re-open it are stated there.
- **The loud-refusal facade stays hand-written.** A faithful static half beside a reflective half
  that throws `UnsupportedOperationException` naming the seam (libGDX's injected `Json` is the
  worked example) recurs as a SHAPE, but each instance's split — which members are static-faithful,
  which throw — is a semantic fact about that library's API, which is §1(c)'s definition. A scaffold
  would generate the throwing half from a member list, and a member list is most of the work.

### 12.2.9 The LAST two menus — `omissions` and `jdk-surface`, and the five kinds that take nothing

`DESIGN.md` §8.21 is the decision; this is what it measured. These are the two lanes the menu
programme priced and never assigned, and between them they were the largest per-site residues in the
corpus with no front door at all — `omissions` at 97 rows and `jdk-surface` at 57.

**The MENU shipped flat, and that was the first measurement.** Four remedies added to the vocabulary,
both checks registered in `PortRun.CheckRemedies`, both draining through `ResolutionPlan.drain`, and
no port selecting anything: **all eleven lanes green, every check count identical, every
`errors vs baseline` unchanged, and `members whose EMITTED TEXT changed: 0` on all eleven.** That is
§1(b)'s empty-parameter rule read at a menu, and it is a structural claim rather than a lucky one —
`drain` returns its input on the first line when the plan is empty.

**Then FOUR PORTS SELECTED, one per remedy, on four different lanes-and-subjects.** Each site was
READ before it was written; the reasoning is in each port's own file beside the key, which is where
§4.575 says it belongs.

| port | key | remedy | why THIS site |
|---|---|---|---|
| gdx-gltf | `net.mgsx.gltf.loaders.shared.GLTFLoaderBase#<init>(TextureResolver)` | `accept-promoted-body` | the funnel promoted the NILARY constructor, whose java body is `this(null)`, so its five inlined statements are the class body and run on the paramful path too. Those five are `textureResolver = null` plus four `new`s of `AnimationLoader`/`NodeResolver`/`MeshLoader`/`SkinLoader`, and the secondary re-assigns all five before the object escapes: all four have java's implicit no-arg constructor and initialise nothing but their own containers, and `null` is upstream's own sentinel for `textureResolver`. Four allocations discarded, final state java's exactly — the "most only waste an allocation" half of C6's own census, stated per site |
| gdx-vfx | `com.crashinvaders.vfx.effects.ShaderVfxEffect` | `accept-dropped-type-annotation` | `@SuppressWarnings("unchecked")` on a class whose 193 lines hold no cast, no type variable and no raw type — the annotation suppresses nothing even in JAVA. The port drops a marker that was already vestigial upstream |
| gdx-vfx | `java.util.Map#clear()` | `accept-jdk-member` | one site, `ValueArrayMap#clear()`, whose `map` field the collections phase retyped to `mutable.Map`. `mutable.Map` inherits `clear()` from `Clearable` with java's own semantics, so the emitted call is correct — coverage by COINCIDENCE, which is exactly what the row is for |
| anim8-gdx | `java.util.Arrays#fill(float[],int,int,float)` | `accept-jdk-member` | 195 sites in `PNG8`'s and `AnimatedGif`'s dithering loops, clearing an error-diffusion row. The receiver is a `scala.Array[scala.Float]`, which IS a `float[]`, so the emitted call is the java call; and `Arrays.fill` over a primitive array has no scala-collection image, which is why nothing was ever written for it |
| liqp (test) | `liqp.TemplateTest$Foo#c`, `liqp.filters.where.JekyllWhereImplTest#array_of_objects1`, `…3` | `accept-dropped-annotation` | three `@SuppressWarnings` on FIELDS — one `"unused"` (a private field with no accessor, the fixture for what liqp's property lookup must NOT see) and two `"serial"` (the double-brace `new HashMap<>() {{ … }}` idiom). Neither warning exists in scala and neither does the annotation |

**The drain, per lane** — `<lane> N->M` beside `remediation(resolved) 0->(N-M)`, which is the only
reading under which a falling residue is an improvement rather than a check that stopped asking:

| lane | before -> after | member digests | errors |
|---|---|---|---|
| `GltfMigrate` | `omissions 12 -> 11`, `remediation 0 -> 1` | **2** — the selected constructor and its enclosing class | 3 = 3 |
| `VfxMigrate` | `omissions 1 -> 0`, `jdk-surface 1 -> 0`, `remediation 0 -> 2` | **1** — `ShaderVfxEffect`, the type the note sits above | 0 = 0 |
| `Anim8Migrate` | `jdk-surface 7 -> 6`, `remediation 4 -> 5` | **0** | 0 = 0 |
| `LiqpTestMigrate` | `omissions 10 -> 7`, `remediation 8 -> 11` | **6** — three selected `val`s plus the three enclosing class digests | 0 = 0 |

Corpus-wide: **`omissions` 97 -> 92, `jdk-surface` 57 -> 55, `remediation(resolved)` +7**, and every
other port flat. Every changed digest is attributable to a `Decision` this run recorded, and the
residue is empty.

**The `jdk-surface` selections moved NO digest, and that is the mechanism working rather than a
weaker demonstration.** Their subject is an EXTERNAL member, which has no emitted declaration for a
porter note to sit above, so `PortRun.declaredSymbols` excludes the decision from note coverage by
construction. The `omissions` ones do move digests, because their subjects are declarations this run
emits — which is exactly the split `Remedy.Subject` exists to state.

**Three things the demonstration found that no spec had asked for.**

- **liqp's test source set is `ResolutionIntrusion`'s D10 case, and it passed.** All three keys name
  declarations inside the base's `governs` claim (`liqp`), because a test source set always declares
  its suites in the base's packages. The screen reads the base's PUBLISHED PORT MAP rather than the
  claim, so it asks what the base EMITS, finds no entry, and admits all three. A claim-based screen
  would have been three fatal findings for a manifest with no way to comply.
- **Seven of liqp's ten rows are unselectable, and saying so is the point.** Every one is a
  `@SuppressWarnings` on a field of an ANONYMOUS class — `ReadmeSamplesTest$21`, `ForTest$34`,
  `Relative_UrlTest$39`, `SizeTest$40` — whose owner is named by a FRONTEND-MINTED index. §4.56
  forbids keying policy on a name the engine invented and is free to renumber, so those rows stay,
  and the port's file says why rather than leaving a reader to wonder which three were special.
- **A selection is shared surface even when it changes no byte.** All four ports' published
  `port-map.tsv` policy digests moved, which is the field doing its job — a base whose choices moved
  cannot look fresh to a dependent — and `GltfTestMigrate` inherited gdx-gltf's selection, bound it,
  found no row of its own to drain (D2), and reported nothing, because `PolicyReport` holds a module
  to its OWN keys.

### 12.3 Counted residues that are not defects

- **`catalog(unreached)` GROWS by one on every port each time a MODERN-JAVA row is mechanised, and
  that is the lane telling the truth.** Every corpus library is written to java 8 or below, so a row
  about SE14+ syntax — a switch expression, `yield`, a pattern label, an `instanceof` binding — can
  never be reached by a corpus port however well it is built. Before it is mechanised such a row
  sits in `catalog(unmechanised)`; the moment it gains a discharge surface it moves to
  `catalog(unreached)`, on all fifteen lanes at once. Measured twice in one wave — `JS-S09` and then
  `JS-S10`, each `unmechanised` -1 and `unreached` +1 everywhere, at **0 member digests on all
  fifteen and every other count flat**. The evidence for such a row is therefore ENTIRELY FIXTURES
  (`SwitchExpressionSpec`, `PatternSwitchSpec`), which is the same position `TextBlockSpec` was
  written from — and `just catalog-coverage`'s "unreached on all fifteen means dead code or an
  untested rule" heuristic does NOT apply to this family: the rule is tested, by construction,
  somewhere the corpus cannot see.
  **…and the THIRD one in the family moved a different pair, which is what says the rule above is
  about the SURFACE and not about the syntax.** `JS-C43` (java `record`) went `unmechanised` -1 and
  **`catalog(consulted)` +1** on all fifteen, with `unreached` FLAT — because its consult sits at the
  `ClassDef` rendering, which every port reaches thousands of times and at which the difference
  simply never applies (`consulted 27, fired 0` on jbump). A modern-java row lands in `unreached`
  when its surface is the CONSTRUCT and in `consulted` when its surface is a declaration every
  program has; the row is equally untestable by the corpus either way, and only the fixtures
  (`RecordSpec`, `PatternSwitchSpec`) are evidence for it.
  **…and "how many never-reached rows are non-`Open`" IS THEREFORE A MOVING NUMBER, so it must not
  be quoted as a constant.** `just catalog-coverage` reports **8** never-reached mechanised rows
  today and **2** of them are non-`Open` — `JS-C42` (`handled`, `phase:collections`) and `JS-S09`
  (`handled`, `lowering:CtSwitchExpression/Expression`). The second one arrived two commits after the
  first (`ffe50854`, which is also the commit whose own §12.3 entry above records its
  `unreached +1`), so a prose claim written at `6c0daec2` that `JS-C42` was *the one* non-`Open` row
  was true when it was written and stale by the end of the same day, with nothing able to report it:
  the number lives inside a recipe's stdout and no baseline holds an opinion about it. The two rows
  have the SAME shape and it is this section's shape read once more — a rule with a discharge
  surface, no corpus site and fixtures for evidence — so the honest statement is the shape and the
  recipe, never the count. Quote `just catalog-coverage`; do not transcribe it.
- **`overload-risk` is a RISK COUNTER with its own denominator, and the denominator is the point.**
  Java resolves an overload in three phases and scala in one, so javac and scalac can bind the same
  call to different members with no error on either side (`ENGINE-LIMITS.md` T17, catalog
  `JS-C22`/`JS-C23`). Predicting WHICH is a resolver and is refused; the calls where the two rules
  CAN differ are counted, and the population is derived from JLS 15.12.2's own phase boundaries —
  boxing, varargs, the generic tie-break — rather than from "this call is overloaded". libGDX core:
  **63,037 calls examined, 5,049 with more than one applicable candidate, 364 spanning a phase**, so
  the narrowing declines 93% of the overloaded calls and says so on every run. Not a defect count and
  not closable: read a row, look at the emitted call, and check which member it names.
- **`rewrite-callsites` is 2 on the libGDX family and 0 everywhere else, and those 2 ARE the work
  list.** The lane asks the question `ENGINE-LIMITS.md` K5.6 left open — does every phase that
  RETYPES declarations count the seams that creates — and its first run named two phases that never
  had: `PrimitiveToOpaqueTransform` (7 declarations moved) and `TypeRedirectTransform` (15). Neither
  looked silent, because both hold a `PolicyReport`; `policy` counts DECLARED KEYS THAT NEVER FIRED,
  which is a different residue from a slot whose two sides disagree, and naming it in `accountedBy`
  would be the suppression the lane exists to prevent. Closing either means the same work the other
  four retyping phases already did — a check of its own, with a §1 classification per issue kind —
  and is a measured step of its own, not a rider. The lane reads 0 on every port that carries neither
  phase, which is a provable zero rather than an absence: the population is derived from what each
  phase DID, on every run (`DESIGN.md` §8.14).
- **Trivia `lost` is 0 on every port** (D4t, below). What remains counted is `trivia(recovered)` —
  comments the attachment channel could not place and the backstop relocated with their java
  coordinates — and `trivia(deliberate)`, derived from the port's own drops. Neither is a defect;
  `recovered` is the work list for the categories that still want an honest home.
- **libGDX omissions 66** (the committed baseline), dominated by the promoted-constructor-body shape.
  The targeted refusal for the `Material`/`Table` remainder was measured at **0 → 35 `E120`** and
  refused (`ENGINE-LIMITS.md` C7) — do not re-derive it. *The figure quoted here was `177` for as
  long as anybody looked, against a baseline that has said `66` since the check was scoped to EMITTED
  units; a residue number restated in prose beside the artifact that computes it is a number that
  goes stale silently.*
- **`heap-pollution` — java's unchecked varargs, carried over and now counted** (`JS-G41`). Two
  issues and both fire: **libGDX core 17 `Unacknowledged`** (every one a `T...`, and libGDX writes no
  `@SafeVarargs` anywhere), **Ashley 6 `Acknowledged` + 1 `Unacknowledged`** — the six are
  `Family.java`'s and the seventh is `ComponentType#getBitsFor`, which carries the same
  `Class<? extends Component>...` with no annotation at all. **0 on the other thirteen.** Nothing is
  translated and nothing can be: the port reproduces java's unsoundness exactly, and what has no
  Scala image is javac's warning at the declaration and the `@SafeVarargs` that answers it — which
  `SpoonTir.annotationsOf` carries into the emitted file, onto a method `JS-G37` has already made
  non-variadic. The seventh row is the argument for deriving the population from JLS 4.7 rather than
  from the annotation: a census keyed on `@SafeVarargs` would have reported 6 and called that the
  risk.
- **`cast-conversion` — 0 on all fifteen, and kept** (`JS-E06`, `ENGINE-LIMITS.md` K17 face 3's
  residue). Java's unboxing conversion emitted as a Scala type ASSERTION, at the one cell the
  emitter can check: a primitive target over a wrapper of a DIFFERENT primitive. The frontend
  answers that shape from the java, so no translation can produce it and only a PHASE that retypes
  an operand after the frontend decided can — which no corpus port does. The lane is `try-resource`'s
  argument applied a second time: that construct was dropped WHOLE for the life of a backend because
  nothing counted a path nobody had exercised. Its non-vacuity is therefore a FIXTURE (a five-line
  spec phase retypes one `Object` operand to `java.lang.Long` and the consult fires), which is
  `JS-G12`'s evidence shape read in the other direction.
- **`try-resource` — 0 on all fifteen, and the reason is the CORPUS rather than the lowering.** A
  literal `try (` appears **0 times** across every upstream tree this engine parses, so the whole
  JLS 14.20.3 lowering — one of the most intricate pieces of the emitter — is structurally
  unreachable on this corpus and its zero says nothing about whether it works. Its evidence is
  therefore its FIXTURES and nothing else, which is the same footing `cast-conversion` is on one
  bullet up. Recorded because a reader who takes the zero as coverage is reading it backwards, and
  because the next library to arrive with a `try`-with-resources is the first to test it.
- **2 collection-closure findings on libGDX**, both `java.util.concurrent`: portability decides those
  first.
- **`Collectors.toSet` / `toMap` deliberately unmapped** (`ENGINE-LIMITS.md` K6): each needs a different
  target type, and both a copy and the identity compile while being wrong.
- **The difference catalog's four coverage lanes** (`DESIGN.md` §2.8). Their first honest numbers,
  and none of them is a defect count:

  | lane | reads | what it means |
  |---|---|---|
  | `catalog(consulted)` | **75–94 per port** (was 66–85, 46–63, 14–29, 5–8) | rows the run REACHED, at any of the FOUR surfaces. The type surface is what last moved it, by **+8 or +9 on every port** — the ninth is `JS-G06`, whose citation only fires where a raw parent really is aligned (26 declarations on libGDX core, none on simple-graphs). The artifact shows the split rather than a total, and the type surface's own spread is the widest in the registry: `JS-G07` and `JS-G08` share **393,520** consults on libGDX core (every plain class reference in the program, primitives included) and fire 5,145 / 1,212; `JS-C29` reads 71,001 / 6,681; `JS-G12` reads 82,399 / **560**, which is the one row whose fire an in-memory fixture cannot reach at all (`resolveTypeParam` searches every enclosing frame by NAME, so a snippet always resolves) and which real libraries reach 560 times; and `JS-G03` reads **1,044 consults and 0 fires** — libGDX writes no `? super Object`, which is what a live branch that does not apply looks like. Earlier waves' figures still read the same way: `JS-S17` fires 1,365 of 1,366 consults, `JS-S18` 92 of 92, `JS-S13` **211 consults and 0 fires**, which is `ENGINE-LIMITS.md` F5's "the corpus count is ZERO" re-derived by a different instrument on a different day. Area G's own spread is the sharpest yet: `JS-G09`/`JS-G13`/`JS-G14` share **63,675** consults on libGDX core (the six slot dispatches) and fire 1,132 / 192 / 104; `JS-G21` fires 262 of 262 because every `instanceof` IS the reifiability question; and `JS-G34` reads **6,258 consults and 0 fires** — libGDX writes no intersection cast, which is what a live branch that does not apply looks like |
  | `catalog(unreached)` | **7–26 per port** (was 6–25, 4–21, 3–18) | a row whose surface EXISTS and which this port never reached. Per-port informational, and a real spread: `JS-S02`/`JS-S06`/`JS-S08` are reached on libGDX core and unreached on its TEST source set, which is exactly what a coverage lane should look like. Corpus-wide (`just catalog-coverage`) it is the `Open` rows and nothing else — JS-C12, JS-E17, JS-S23, JS-G16, JS-G36 and now JS-G02, all unconsultable by rule (ii), the work list seen from the other side. **That the corpus-wide set is EXACTLY the six open rows is the evidence that no mechanised row is dead code**, and it is what the fourth surface had to be re-checked against: ten rows moved onto a new mechanism at once, and every one of them is reached by at least one port |
  | `catalog(unmechanised)` | **1**, identical on all fifteen (was 2, 3, 4, 5, 7, 8, 9, 10, 20, 47, 88, 111, 112) | rows nothing is instrumented to answer for. Derived from the REGISTRY and not from the run, deliberately, so a reader comparing two ports can see it did not move. The FOURTH SURFACE took it **20 → 10** (`DESIGN.md` §2.8): nine `JS-G` type-reference rows plus `JS-C29`, which was area C's own residue and the same fact one area over. Three came off it one at a time after that — `JS-C30` when the frontend learned the method-LOCAL class (T9); `JS-G41` when its RISK COUNTER shipped; and `JS-E06`, whose claim that the emitter had no surface either was simply false, `Tree.Typed` having entered the rendering dispatch all along. `JS-C22` and `JS-C23` came off together when the OVERLOAD RISK COUNTER shipped, and the pair is the worked example of what `Unmechanised` is FOR: their sentence said no surface existed to owe a consult, and what did not exist was a RESOLVER — the rendered call was a surface all along, and what it owes is the RISK and not the answer (`ENGINE-LIMITS.md` T17). Four more came off one at a time after those, and the last two are the sharpest reading of the classification the lane has produced. `JS-C43` (java `record`) was filed `AbsorbedSilently` at its Spoon kind, which said no arm could owe a consult — and what did not exist was never a SURFACE: a record renders through the same `ClassDef` dispatch as every other declaration, and the whole of the gap was that no arm could KNOW one was there. One flag on the type symbol (`Flags.isRecord`) made the question askable, and the row went `Absent` -> `Partial` at `Rendered("ClassDef")`, taking the lane to **1** and area C's own `Unmechanised` set to **ZERO**, from 47 when it opened. What is left is `JS-G20` alone, whose reason is per-phase discipline and stands |
  | `catalog(undischarged)` | **6** on the two libGDX lanes, **3–5** on the other thirteen (was 5 / 2–5, 3 / 2) | the WORK LIST, and every entry on it is DECLARED. The newest is **JS-G02** (capture conversion, owed at every `CtWildcardReference` and synthesised nowhere), which the type surface added exactly as area G's wave added JS-G16 and JS-G36 — a wave attaches the rows nobody handles, and the count is the number of known holes rather than the number somebody remembered. JS-E17, the lvalue's single evaluation (`ENGINE-LIMITS.md` F7), on every port that lowers a compound assignment; **JS-C12**, a forward reference to a later field, which java rejects and scala reads as a default; **JS-S23**, java's `assert` being off without `-ea` while Scala's always runs, at 136 `Tree.Assert` renderings in libGDX core alone; and area G's two — **JS-G16** (`Array[T]` needs a `ClassTag`, owed wherever a `Tree.NewArray` renders) and **JS-G36** (an override's type-parameter bounds, owed wherever a `Tree.DefDef` does). All five are `Open` rows, so a fatal log counts them rather than raising — a mode that died on the work list would make the work list unrunnable. The rises are the design working: each wave attaches rows nobody handles, and the count is the number of known holes rather than the number somebody remembered. JS-E04 was an entry and is gone; **JS-C44 was an entry for one commit** and is gone the same way — a `WidenedSeal` decision now records the half scala cannot express, and both closings cost **0 member digests on all fifteen ports** because no corpus library has the shape |
  | `catalog(uncited)` | **121**, identical on all fifteen | NOT a coverage lane — rows with no Scala-side normative citation, derived from the registry like `unmechanised`. It is a lane because the number was a `println` in one spec beside `assert(uncited <= all)`, which no registry can fail, so nothing could report the 122nd; `counts.tsv` is the only thing here that diffs a number. Never asserted on, in either reader |

  The wrapper's COST was measured where it is largest, because a per-node cost on the frontend's hot
  path is the one thing that could have made this design the wrong one: three runs of the libGDX
  core migration with the dispatch wrapper live read **35.96 / 38.01 / 34.97 s**, and three with it
  bypassed at the same commit read **35.55 / 38.98 / 34.43 s**. The difference is inside the
  run-to-run spread. **The bypass is one uncommitted line** — `Lowering.of` returning
  `body(using log.unattached)` before it consults `Differences.owedAt` — which removes the whole
  per-node cost (the lookup, the allocation, the settle) and leaves the pipeline otherwise identical;
  time `just gdx-measure`'s migration three times each way, at one commit. **And the EMITTER's
  wrapper was priced the same way when it landed**, because `TirEmitter.term` is at least as hot as
  the frontend's dispatch (`JS-E01` alone is consulted 28,960 times on libGDX core): three runs with
  `Rendering.of` live read **36 / 34 / 33 s** and three with the same one-line bypass read
  **38 / 33 / 33 s** — again inside the spread, and again the fallback is refused ON A NUMBER rather
  than on the shape of the design. **And the TYPE surface was priced the same way and is the one
  that could genuinely have been too expensive**: `JS-G07`/`JS-G08` attach at every plain class
  reference, which is **393,520 consults on libGDX core** against `JS-E01`'s 28,960 at the emitter's
  term dispatch — an order of magnitude more scopes, each one an `Obligations` allocation and a
  settle rather than the allocation-free path the other two mostly take. Three runs of the libGDX
  core migration with both type dispatches live read **36 / 37 / 37 s** and three with the same
  one-line bypass in `Typing.ofReference`/`ofRepr` read **38 / 37 / 40 s**: the live runs are if
  anything the faster set, so the cost is below the run-to-run spread at ten times the node count,
  and the compile-time-only fallback stays refused. Written down because a
  price nobody can re-derive is a price that gets re-argued: there is no flag for this and there
  should not be (§4.6's flags are debuggers', and a flag that silently disabled the obligation
  mechanism would be one somebody leaves on). The fallback the design priced — compile-time-only enforcement, which keeps the
  guarantee for every difference that has a test and loses it for every one that does not — is
  therefore not taken, and it was not taken on a number rather than on a preference.

### 12.4 Cosmetic

- **Drop notes print `key=` twice** — the last decider still restating what `Reason.Configured`
  already carries (`Decision.detail`'s own scaladoc now forbids it, and the three phases that did it
  were fixed at P3). The three sites are all in `PortRun`: the `dropTypes`, `dropMethods` and
  `supportSources` loops. `DroppedMember` is `InBody` and `DroppedType` is `NotInTree`, so the blast
  is emitted-type bodies plus the injected files' prepended headers — 16 + 12 notes on libGDX core
  alone, which is why it did not travel with the phase fix.

### 12.4.5 The platform matrix — what the target set turned on, and what it found

`PortabilityCheck` is a §1(b) phase now (`DESIGN.md` §2.8, `ENGINE-LIMITS.md` P6): one mechanism, one
parameter (`PortManifest.targets`), and a rule list carrying the platforms each rule is a refusal
FOR, each rule citing the `ApiRows` row that holds the availability claim and its version anchor.
Four things this repository can now say that it could not before, and only one of them is a defect:

| | |
|---|---|
| the RE-SCOPING | 8 of the pre-existing rules claim Scala.js alone (7 measured too broad for Scala Native 0.5.x, plus `System#getProperty`, whose `why` was not broad but FALSE). **No port's count moved for it**: the default target set is all three platforms, so a JS-refusal still fires. It is observable only to a port declaring `targets = [jvm, native]`, and none does |
| the MATCHER | `startsWith` became `RuleScope.covers` with the trailing separator stripped (§4.56). The one live victim: `java.lang.Thread` covering `java.lang.ThreadLocal`, which Scala.js implements — **LiqpMigrate 54 → 52, LiqpTestMigrate 1521 → 1519**, and no other corpus library writes a `ThreadLocal` |
| the MISSING rules | the time/text/locale area had **zero**. Its refusal residue found a real site on the first run: libGDX's `TextFormatter` uses `java.text.MessageFormat`, which no surveyed source tree implements on either backend — **LibgdxCoreMigrate 151 → 153**, `remediation` 29 → 30 with a concrete `dropTypes` line, and +2 through the resolution root on all six gdx-family dependents with their `portability(emitted)` flat. liqp gains **4** `java.util.Calendar` sites; `IDN` and `ServiceLoader` are re-attributions at net 0 |
| `dependency-coverage` | the twenty-second required check, and the OTHER half of the same enumeration. Half the matrix's answers are `Depend` — the API exists off the JVM, in an artifact the build does not name — and reported as an unportability the finding is unanswerable. First run, and it is not vacuous anywhere it should not be: **libGDX 37 sites** (`java.util.Locale` → `scala-java-locales`), against **15 dependency rules** and **0 declared artifacts** on every port |

**Nothing was wired into any port at that point.** That is the state the machinery was landed into
deliberately — whether sge and ssg genuinely ship all three backends is their maintainers' answer,
and the rev-1 default is what makes the wrong answer cheap either way: a port that never declares one
is checked exactly as it was. The maintainer's answer arrived in 2026-08 (`CLAUDE.md` §1.5): all
three platforms wherever possible, JVM-only reserved for modules whose whole point is a JVM facility.
§12.4.6 is what that unlocked. `targets` and `verdictOverrides` are still declared by no port, which
is now a statement rather than a gap: no module has claimed the exception.

**Not built, and priced rather than attempted:** `RegexConstructCheck`. Scala Native's
`java.util.regex` is RE2-backed and its unsupported-construct list is 40 items long and verbatim
from that project's own docs; the Scala.js side of that list was never fetched, and the survey says
so. A construct check shipping a JS list on an INFERENCE would be a check asserting coverage it does
not have, which is the one failure mode the whole catalog is built against — so it ships when the
list is fetched, or it ships Native-only, and neither happened here.

### 12.4.6 The `Depend` half, WIRED — three ports declare, and what the wiring found on the way

§12.4.5 landed the lane with `0 declared artifacts on every port`. This is the wave that answered it,
under `CLAUDE.md` §1.5's maintainer statement: a `Verdict.Depend` is answered by DECLARING the
artifact, never by rewriting a call one `libraryDependencies` line makes correct.

| port | `dependency-coverage` | `(all)` | declared |
|---|---|---|---|
| LibgdxCoreMigrate | **37 → 0** | 37 (flat) | `scala-java-locales` |
| LiqpMigrate | **101 → 0** | 101 (flat) | `scala-java-time`, `scala-java-locales` |
| LiqpTestMigrate | **134 → 0** | 235 (flat) | the same two plus `scala-java-time-tzdb` |
| the other twelve | 0 (flat) | flat | nothing — their requirements are their BASE's (D2) |

**The residue falls and the enumeration does not, which is the shape a DRAINED lane has here.**
Coverage subtracts from the residue and never from the walk, so `(all)` is exactly the number that
says the walk still runs — a distinction §12.4.5 built the pair for and this is the first wave to
exercise. Every other count is flat on all fifteen lanes, **0 members' emitted text moved anywhere**,
every error count and test-discovery figure is unchanged (libGDX 217/4, ashley 108/2 + 2 not run,
gltf 64/0, liqp 636/1, sg 16/0, screens 16/0, anim8 23/0), and no port gained a `policy` row — which
is the check that says the five declarations are exactly the ones some requirement wanted.

**What the wiring had to build, because declaring alone would have made the check a liar.**

- **a declaration that answers NOTHING is a `policy` finding** (`DependencyCheck.unneeded`,
  `PolicyIssue.NeverApplied`). Coverage subtracts, so an entry naming an artifact no requirement
  wants leaves both numbers where they were — 0 before, 0 after — while the port resolves and ships a
  jar on every backend for a call it does not make. It is asked of the EMITTED requirements, so a
  dependent cannot take credit for a call only its base makes;
- **the artifacts land at the RUN's own `SourceSet`.** A `sourceSet = Test` run's declarations are
  its suite's, and written into the main `libraryDependencies` they would publish, on the shipped
  library, a coordinate only its tests call. liqp's tzdb entry is the live case;
- **the credit was UNTESTED.** Nothing asserted that a declared coordinate reaches a build file at
  all — a check that stops reporting a requirement on the strength of a manifest entry is a liar
  until it does. `PortRunProjectSpec` asserts both halves now. (No corpus port generates a build:
  `project` is opt-in and every port here is emitted into a build the repository already owns, so
  this is asserted in the engine's own spec and not by a lane.)

**And the coordinates themselves were checked against the repository, which found two defects in the
survey.** An artifact a `Depend` names is one a NON-JVM backend has to resolve:

- **four of the five coordinates were `%%` where only `%%%` resolves.** `scala-java-time`,
  `scala-java-time-tzdb` and `scala-java-locales` are published per PLATFORM (`_sjs1_3`,
  `_native0.5_3`) as well as per Scala version, and so is `scala-native-crypto` — which exists at
  `_native0.5_3` and NOWHERE else. `%%` asks a JS build for the JVM jar, which for the first three
  resolves and then fails at link time. `CrossKind.Platform` on all four; `scalajs-weakreferences`
  already carried it with the reasoning written out beside it, and the other four were that same fact
  unchecked;
- **one coordinate a Scala 3 port cannot use at all.** `com.dedipresta:scala-crypto` is published for
  2.12 and 2.13 and for no Scala 3, so the JS half of `MessageDigest`/`SecureRandom` (`JS-P26`,
  `JS-P27`) names an artifact that resolves nothing. Recorded in both rows' `why` and at the artifact
  rather than repaired with an invented replacement — a survey row may not fabricate a fact (§4.6).
  No corpus port references either API, so no lane moves for it; it is open work for whoever ports a
  library that hashes.

The JVM resolution of all five declared coordinates was verified directly
(`cs fetch --scala-version 3.8.4`); the JS/Native halves are the catalog's claim, and this wave does
not build for those backends.

**One thing the accepts exposed and this wave did NOT cause: `port-map.tsv` is a SIXTH committed
baseline that no lane compares.** Nine dependent maps (`Anim8`, both `Ashley`, both `Gltf`,
`LibgdxTest`, `LiqpTest`, `Screens`, `Vfx`) had a stale `policy=` header — the digest covers the
POLICY CHAIN, so libGDX core's `reviewedBoundaries` (2026-08-09) and liqp's `resolutions` moved every
dependent's digest while only the two bases' baselines were re-accepted. The control is decisive: the
two manifests this wave actually edited are the two whose own maps did NOT move, because
`surfaceDigestInputs` reads the surface and the selections and not `dependencies`. The diff is the
header line and nothing else on all nine, and they are refreshed here. `just baseline-accept` promotes
the file, `headline` gates the other five, and nothing reads this one — which is §12.2.5's finding
exactly, one artifact over.

**GATED, in the wave after this one.** `port_map_guard` sits beside `findings_baseline_guard` in
`scripts/_lib.sh`, is called by all fifteen report dirs across the eleven lanes, and defers its
verdict to `headline` through a marker file for the reason the other four do. It landed **flat on
every port** — 27,330 rows over fifteen maps, header and rows unchanged, no baseline re-accepted and
no other count moved (libGDX core 19,498; liqp 996; liqp-test 953; vfx 953; gltf 1,562; anim8 607;
jbump 532; sg 476; ashley and ashley-test 401 each; libgdx-test 329; noise4j 311; screens 213;
gltf-test 47; sg-test 51).

Three things about it that are decisions rather than mechanism:

- **nothing is stripped.** `findings_baseline_guard` drops `Finding.id` because it is renumbered in
  LINE order by unrelated edits; the map has no such column. Every header field is a fact somebody
  has to acknowledge — `engine=` most of all, since `PortMap.freshness` turns a mismatch into `Stale`
  outright — and every row column is deterministic: `javaPath` is relativised against a root DERIVED
  from the unit's own FQN (`SrcMap.sourceRootOf`, a string operation, so none of §5.4's realpath
  hazard reaches it), `javaLine` is upstream java's, `digest` is the one `members.tsv` is already
  baselined on, and the row order is `PortMap.of`'s own section-by-section sort. The fifteen flat
  runs are the evidence for that claim rather than the argument for it;
- **the metadata line is diffed FIELD BY FIELD**, because this section's own incident read as a raw
  diff is two sixteen-character digests and says nothing about which of six moved or what it means;
- **a run that published NO map fails too**, and is not symmetric with a missing baseline:
  `PortMap.discoverIn` prefers `run-latest` and falls back to `baseline`, so a port that stopped
  publishing hands its dependents the COMMITTED map and nothing else can see it.

**Negative-tested on a real lane, both halves** — a guard that has never failed is not known to work,
which is `CLAUDE.md` §3's "a check reporting zero is only as good as its coverage" read at the
instrument rather than at the port. With jbump's committed
`policy=` digest edited and every row left alone, `just jbump-measure` exited 1 at
`policy= 0000stale0000dead -> 3dc15a4b2f41e628 / the base's MANIFEST changed`, `rows: unchanged`; with
the header restored and one `shape` cell moved `form=class -> form=trait`, it exited 1 naming the two
rows; restored, it exits 0 at `532 row(s), header and rows unchanged`. Sixteen `just lane-selfcheck`
cells hold the same shapes without sbt.

### 12.4.7 `java.util.ServiceLoader` maps to a cross-platform WRAPPER — the first `Depend` a port both declares AND redirects into

`DESIGN.md` §8.19's ruling, built. `com.kubuszok %%% multiarch-serviceloader` is published (Central
Portal snapshots, all three platform artifacts verified resolvable with `cs fetch`), the catalog row
`JS-P25`'s two non-JVM verdicts moved from `Refuse`/`MapTo("link-time provider enlistment")` to
`Depend`, and liqp — the corpus's only `ServiceLoader` user, one site — redirects into it.

**The ruling was wrong about Scala Native, and the correction is the reason the wrapper exists.**
Native's `ServiceLoader.load` is a link-time INTRINSIC that only accepts a literal `classOf`, so no
`Class`-taking wrapper can delegate to it and `nativeConfig.withServiceProviders` enlistment serves
only direct `load(classOf[Concrete])` sites, which a ported library's generic lookup never is. Native
therefore registers exactly as Scala.js does, off the same `META-INF/services` descriptor §12.2.7
already ships. Measured against Scala Native 0.5.12 and recorded in the row's availability half.

**No new engine phase.** `java.util.ServiceLoader` is a FACTORY and a HANDLE in one type and the
wrapper splits them the way scala does, so the port names two existing keyed seams —
`call-site-substitution` for `load(Class)`, `type-redirect` for the type — ordered against each other
and against `collections` for reasons its `.conf` states. The one seam the redirect creates is the
wrapper's `iterator()`, which carries java's ARITY and not java's ELEMENT TYPE: it answers a
`scala.collection.Iterator` where this port retypes `java.util.Iterator` to
`balticporter.runtime.JavaIterator` (§4.5). Left to the collections phase's external-callee coercion
that is `E134`, **0 -> 1 errors, measured**; substituted ahead of it and bridged through
`JavaIterator.from` it is 0.

**What moved, and every line of it attributed:**

| lane | LiqpMigrate | why |
|---|---|---|
| `remediation` | **20 -> 17** | the two `accept-jvm-only` refusal rows (`ENGINE-LIMITS.md` P6, discharged) and the `substitutions-drop` candidate derived from the same two findings |
| `portability(all)` / `(emitted)` | **56 -> 54** | the two `java.util.ServiceLoader` rows; the rules are `dependencyRulesFor` now |
| `policy` | **0 -> 1**, and **1 -> 0** in the wave that CLOSED `ENGINE-LIMITS.md` P8 | the declared coordinate was invisible to a walk that enumerates JDK usages, because the redirect is what removed the usage. The check reads TWO programs now, and the artifact's own jar for the emitted half |
| `rewrite-callsites` | **0 -> 1** | `type-redirect` names no accounting lane; the identical row libGDX core and screens have carried since they gained the phase |
| `members.tsv` | **2** | `SPIHelper` and `SPIHelper#findProviders()`, one `RetypedSignature` and two `SubstitutedCall` decisions between them |
| errors / suite | **0 / 636 passing, 1 expected failure** | both unchanged |

On `LiqpTestMigrate`: `portability(all)` **1605 -> 1603** (the same two rows, seen through the
resolution root), `rewrite-callsites` **0 -> 1**, `portability(emitted)` flat at 1549, 0 members
moved. The other thirteen lanes are byte-identical.

**`dependency-coverage(all)` did NOT grow, and that WAS P8.** The expectation was that the walk would
find a ServiceLoader requirement for the declaration to cover. It cannot: the redirect removes the
JDK usage, which is its job, so the requirement never arrives and `DependencyCheck.unneeded` read
the coordinate as never applied. The entry stays — the emitted Scala names
`multiarch.serviceloader.PlatformServiceLoader` outright and a build without the coordinate cannot
resolve it.

**CLOSED (2026-08-14).** A declared coordinate is now a 2×2 over BOTH programs (`DESIGN.md` §8.20),
and the emitted half reads the artifact's OWN class list. liqp `policy` **1 -> 0**, with the row
arriving on the new `dependency-coverage(declared)` lane where both usage lanes were blind to it:

```
covered  com.kubuszok:::multiarch-serviceloader:…
  original: 2 site(s) at java.util.ServiceLoader — a catalog `Depend` row names it
  emitted:  1 reference(s) to multiarch.serviceloader.ServiceProviders — the artifact's own class list declares them
```

Note the CELL, because the prediction was `introduced by translation` and the answer is `covered`:
`ApiRows` p(25) maps liqp's ORIGINAL `java.util.ServiceLoader` usage to this artifact, so the
pre-pipeline column answers from the catalog and the pair is yes/yes. The provides-set is still what
makes the row right rather than merely different — without it the emitted column reads `No`, the cell
is `stale`, and the remove instruction is wrong in the same way for a different reason. No corpus port
lands in `introduced` or in either removable cell today; `dependency-coverage(declared)` is `1` on
libGDX core and its dependents, `3` on both liqp lanes and `0` elsewhere.

**THE ACCEPTED COST: a FRESH OFFLINE CHECKOUT cannot reproduce this port's declared row.** The cell
above is the only one in the corpus whose emitted half is read from a JAR, and the jar is a snapshot
on a repository that is not Maven Central. With a cold `.balticporter/artifact-index` cache and no
network, `cs fetch` fails, the coordinate answers `Provides.Unverifiable`, and the row becomes
`unverifiable` — which KEEPS, so no wrong instruction is produced and the `policy` and
`dependency-coverage*` COUNTS do not move. What does move is the row's own TEXT, so
`findings_baseline_guard` fails the liqp lane (`CLAUDE.md` §5's third baseline, which diffs the six
columns that carry meaning). That is the right direction — the alternative is a run that quietly
answers a different question than the baseline it is being compared against — and it is stated here
rather than worked around: the fix for an operator who hits it is a network, not a flag. Every other
port resolves nothing at all, because the catalog half is asked first.

**The coordinate is the first to need a RESOLVER.** `ArtifactDep.resolver` carries the URL beside the
`org`/`name`/`rev`/`cross` it is the fourth fact of, and `SbtGen` renders one deduplicated `resolvers`
block from the dependencies it is already writing, naming each entry from its own URL. The revision is
a git-described SNAPSHOT and is provisional until multiarch-scala's next release, stated at the
coordinate in both places it appears. The measure lane spells the same pair as
`--dependency … --repository …`, because it compiles with `scala-cli` and no generated build.

### 12.5 Not run

- **`PortMapAcceptanceSpec` still `assume`s itself out in a WORKTREE.** It resolves
  `<repoRoot>/../sge/original-src/...`, and a worktree's parent is `.claude/worktrees/`, not the
  checkout's parent — so the vendored-sources `assume` fires and the spec is reported `Skipped 1` by
  every `testOnly *` run made from one. §5.1's own rule is what this trips: a spec that `assume`s on
  an artifact is one nobody is running unless something produces that artifact first, and here the
  something is a path shape rather than a run. Pre-existing, unrelated to any catalog work, and named
  here because a green `testOnly *` from a worktree does not mean this spec passed.
- **The Auditor has not run over this delivery.** It is expensive (Fable 5) and the **user** runs it,
  once a whole piece of work is delivered (`CLAUDE.md` §4).
