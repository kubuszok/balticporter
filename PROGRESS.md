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
just sg-measure           # simple-graphs + its suite
just measure-all          # the four above, serially, stopping at the first failure

just decision-counts      # decisions.tsv row counts by kind, every port
just members-unchanged    # members.tsv against its baseline — the blast radius, before a compile
just baseline-accept <port>   # promote a baseline (also: baseline-list / -show / -diff)
```

Run them **serially** — `measure-all` does: each re-emits into `src_managed/` and the dependent lanes
compile against what the base lane just wrote. Every lane refuses to measure stale output if its
migration did not run, and every one prints the full check report diffed against the committed
baseline, not a filtered selection of it. The mechanism they share is `scripts/_lib.sh`; the policy
(sbt projects, upstream trees, dependency coordinates) is variables at the top of the `Justfile`.

Measurements below are from one serial run of all five, 2026-07-30.

---

## 1. Corpus inventory

Three libraries are ported on the current (TIR) pipeline, across six runs — a library and its own test
suite are two ports, and the suite is a *dependent* of the library:

| port | upstream | files in / out | tests | compile |
|---|---|---|---|---|
| `libgdx-core` | libGDX `gdx/src` | 604 in → **598 out** (11 dropped, 6 injected) | — | **0** |
| `libgdx-test` | libGDX `gdx/test` | 29 → **29** | **221**, 217 pass / 4 expected-fail | **0** |
| `ashley` | Ashley `ashley/src` | 21 → **21** (2 injected) | — | **0** |
| `ashley-test` | Ashley `ashley/tests` | 18 → **18** | **112**, 108 pass / 2 fail / 2 skipped | **0** |
| `simple-graphs` | simple-graphs `src/main` | 29 → **33** | — | **0** |
| `simple-graphs-test` | simple-graphs `src/test` | 7 → **7** | **16**, all passing | **0** |

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
| `sge-vfx` | gdx-vfx | 45 / 5,732 | 41 / 3,881 | **91 %** | 6 / 29 | Apache-2.0 |
| `sge-jbump` | jbump | 19 / 4,045 | 14 / 2,054 | **73 %** | 7 / 32 | Apache-2.0 |
| `sge-graphs` | simple-graphs | 29 / 3,784 | 25 / 2,525 | **82 %** | 8 / 77 | MIT |
| `sge-controllers` | gdx-controllers | 29 / 2,884 | 18 / 2,141 | **27 %** — deliberate | 6 / 33 | Apache-2.0 |
| `sge-ecs` | Ashley | 21 / 2,523 | 24 / 2,404 | **100 %** | 18 / 172 | Apache-2.0 |
| `sge-noise` | noise4j | 12 / 2,491 | 10 / 2,608 | **83 %** | 3 / 13 | Apache-2.0 |
| `sge-screens` | libgdx-screenmanager | 23 / 2,459 | 20 / 1,691 | **86 %** | 6 / 29 | Apache-2.0 |
| `sge-freetype` | libGDX `gdx-freetype` | 4 / 1,891 | 9 / 2,365 | **100 %** of the Java layer | 9 / 28 | Apache-2.0 |

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
   textratypist 145, gdx-ai 113, vis-ui 32, ashley 31, anim8-gdx 20, liqp 105. Baltic Porter has now
   ported libGDX's 221 core tests, Ashley's 112 and simple-graphs' 16 end to end, so the capability is
   proven — but per `CLAUDE.md` §3 this remains the largest single gap in the hand-off, and it is
   invisible in every status doc because none of them tracks tests.
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
| `sge-jbump` | Lost a **public copy constructor** `Collisions(Collisions)`, pinned by a deliberately-red test. |
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

1. **`sge-noise`, `sge-jbump`, `sge-graphs`** — small, mechanical, each exercising the `lowlevel.util.*`
   split. (`sge-ecs` and `sge-graphs`' upstream are already done: §3 and §4.)
2. **`sge-gltf`, `sge-anim8`, `sge-vfx`, `sge-screens`** — mid-size, high coverage, few surprises.
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
| source map | 594 units / 19,257 members | 623 units / 19,547 members |
| members changed vs baseline | **0** | **0** |
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

### 2.2 Residues, named

**Omissions, 177.** The dominant kind is `promoted constructor body runs on every path` — a promoted
constructor's body executes on construction paths Java would not have run it on. Refusing that shape
instead of counting it was measured at **0 → 41 errors** and refused (`ENGINE-LIMITS.md` C7); the
targeted refusal for the shape-6 remainder (`Material`, `Table`) was measured at **0 → 35 `E120`**,
omissions 177 → 65, and also refused. The prefix strip that took omissions 193 → 177 repaired 16
construction paths across 10 classes. The rest is `super(args) dropped` (`ENGINE-LIMITS.md` C3) and 6
dropped annotations.

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

## 5. Publishability — what sge and ssg need before they can depend on this

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
| 2.3 | no TIR pretty-printer, no way to run/skip/dump a phase | **shipped** — `TirPrinter` (+ a `canonical` style and `digest`), `DebugEmit`, and the five debug flags of `CLAUDE.md` §4.6 |
| 2.4 | the unportable-marker design's Stage 1, plus a forced test-correlation amendment | **shipped** — source map, member digests, scalac correlation and the **test-failure** correlation lane. Stage 2 (the marker itself) deliberately unbuilt; see `DESIGN.md` §6.5 |
| 2.5 | three ad-hoc debugging techniques should become first-class | **shipped** — all three are flags or a printer |
| 3.1 | cross-port composition — blocking at sge's second module | **shipped** — `PortManifest` + `ManifestAgreement` (static and dynamic layers), and beyond the original design, the **port map** (`DESIGN.md` §5): a dependent now reads what its base *emitted*, not only what it *declared* |
| 3.2 | test-framework coverage was JUnit-4-shaped | **mostly** — `@After`, `@Ignore`, `@BeforeClass`/`@AfterClass` and the assertion set are handled (`ENGINE-LIMITS.md` X5). The target side is honestly **(b) with exactly one implemented policy value**: `intercept` and the curried `test(name){body}` shape are MUnit facts baked into the phase |
| 3.3 | incremental TIR runs; unmatched-policy-key reporting; `CollectionsTransform.typeMap` as a parameter | **two of three** — the action cache moved to TIR, and `PolicyReport` now reports a key that never fired (it found a real dead entry in libGDX's manifest the first time it was called). `typeMap` is still a private table, not a (b) parameter, so a divergent *mapping* is invisible to `ManifestAgreement` |

### 5.1 What is still NOT done, stated plainly

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

### 5.2 What the audit found SOUND — clean verdicts, not courtesy

`Substitutions`' overload-precise `owner#m(P1,P2)` keys; `ClassTableTransform` and
`StaticForwarderTransform` as correct (b) mechanisms (searched for smuggled libGDX knowledge, none
found); `corpus-tests/libgdx-overrides/**` as correct (c) content in the right place — the model for
what a *consumer repo's* `src/` holds; `IntToOpaqueTransform` as the canonical (c) *policy* carried by a
shareable (b) *mechanism*; `RewriteTrace`'s impact/check pair (blast radius *before* a rewrite — judged
unique to this codebase); the stale-emit abort in every measure lane; and `Phase` / `Pipeline` /
`StandardTraversal` as the best-documented surface in the repository.

One latent edge worth a guard when a second library configures it: `StaticForwarderTransform` matches
members by **name only**, so a wrapper whose overloads are not all receiver-first would be rewritten
wrongly. Safe under current policy.

### 5.3 What an agent in another repository would still hit first

Traced as a scenario, and the part that has not changed: **typer errors arrive with no (a)/(b)/(c)
signal at all**, and they are the bulk of a new library's first wall. The checks classify their own
domains well — a portability hit points at configuring `Substitutions`, an orphaned call names the
exact dropped member — and the correlator now locates every error to a member and a Java line. What it
cannot yet say, for an *unmarked* error, is which of the three kinds the fix is. That is exactly what
`DESIGN.md` §6's markers are for, and why Stage 2 is the next thing worth building.

---

## 6. Remaining work, across the engine

Maintained by deletion. Items are ordered by what they block, not by size.

### 6.1 Provenance coverage — decisions that are not yet recorded

- **`TestFrameworkTransform`'s synthesised `beforeAll`/`afterAll` record no decision.** They are
  definitions with no Java behind them, which is precisely the case a reader cannot explain from the
  line itself.
- **Raw-generic `[?]` rendering and `uncheckedGeneric` retyping are unrecorded.** Both change a
  signature for a reason no reader can recover; recording them needs the decision log threaded through
  the frontend, which today records only from phases and the run.
- **`RetypedSignature`, `RedirectedCall` and `FunnelledCtor` carry no porter note.** The argument for
  each is in `DESIGN.md` §7.2 and stands; it is listed here so that adding one is a decision rather
  than an oversight.

### 6.2 Control flow

- **`labelSeq` is program-global**, so a control-flow diff is never file-local: emitting one new
  boundary shifts every subsequent label name. Nothing is wrong with the output; the *diff* is
  unreadable, which is a measurement cost (`CLAUDE.md` §5).

### 6.3 Counted residues that are not defects

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

### 6.4 Cosmetic

- Drop notes print `key=` twice.

### 6.5 Not run

- **The Auditor has not run over this delivery.** It is expensive (Fable 5) and the **user** runs it,
  once a whole piece of work is delivered (`CLAUDE.md` §4).
