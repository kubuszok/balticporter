# Port inventory — what each sge / ssg module is, and what its agent inherits

Purpose: each ported Java library is to be handed to a **separate agent** that supervises that
library's port, driving Baltic Porter from the library's Java sources plus per-library configuration.
This document is the hand-off packet: what the module is, which upstream it came from, how big the
job is, and — the column that matters — **what the hand port silently did NOT do.**

Measured 2026-07-29 by direct `find`/`wc` and a type-name census, not from the repos' own status
docs. Where a doc disagrees with the tree, the tree wins and the disagreement is recorded.

---

## 0. Read this before assigning anyone

### A skip is not a model

`CLAUDE.md` §3.5 is the governing rule: the reference port tells you what it EMITTED and whether it
SOLVED or SKIPPED the problem. **A construct that merely vanished from the hand port is unported
work with no reference implementation** — the engine must produce something no one has produced
before. Every "absent" entry below is that kind of work, and it is systematically the part the
per-repo status docs under-report.

### The repos' own coverage databases are NOT reliable

Three independent inaccuracies were found, each of which would mislead an agent that trusted it:

| source | defect |
|---|---|
| `sge/.rescale/data/migration.tsv` | 36 rows are a **spilled Java stack trace**. All 11 `gltf/exporters/*.scala` exist on disk and have no row. Zero rows for the 26 ported GWT-backend files. |
| `ssg/.rescale/data/migration.tsv` | Marks all 10 `liqp/filters/{date,where}/*` as `skipped`. **They are ported**, with `Covenant: full-port` headers. |
| `ssg/docs/.../flexmark-port-status.md` | Claims `flexmark-util-dependency` is "11 files, Pass". **Only 6 of 11 types exist.** Also disagrees with two sibling docs on the test count (1645 vs 5889). |
| `sge/CLAUDE.md:4` | "539 of 605 converted" — stale; the header census says 503. |

**Use the per-file `Ported from:` / `Original source:` headers as the oracle.** They matched the
on-disk type census in every module checked. Anything derived from a TSV needs re-verification.

### Nobody ported a single upstream test

`sge`'s migration DB has **zero** rows targeting `src/test`. sge's ~3,500 test cases are hand-written
MUnit; liqp's 65 ssg suites are rewrites (only 5 carry attribution). Available and unported:

| upstream | Java test files |
|---|---|
| libGDX | 430 |
| colorful-gdx | 162 |
| textratypist | 145 |
| gdx-ai | 113 |
| vis-ui | 32 |
| ashley | 31 |
| anim8-gdx | 20 |
| liqp | 105 |

Baltic Porter has now ported libGDX's 221 core tests end to end (217 passing), so the capability is
proven. But per CLAUDE.md §3 — *"prefer running ported tests over any number of further compile
fixes"* — **this is the largest single gap in the whole hand-off**, and it is invisible in every
status doc because none of them tracks tests.

### libGDX's `utils` is split across THREE repos

14 core types (`ObjectMap`, `ObjectSet`, `OrderedMap`, `Sort`, `TimSort`, `MathUtils`, `Select`,
`ArrayMap`, `DynamicArray`, …) live in a **sibling repo `../lls`** (118 Scala files, package
`lowlevel.*`), documented at `sge/docs/contributing/type-mappings.md:107-128`. Coverage measured
against `sge/` alone under-reports, and an engine run that does not know this will re-derive types
that already exist. `sge-jbump`, `sge-noise` and `sge-graphs` also drop their libraries' private
collection layers in favour of `lowlevel.util.*`.

---

## 1. sge — 16 ports, 4 non-ports

Sorted by upstream size. "Coverage" is the type-name census; renames are counted as present.

| module | upstream | Java files / LOC | Scala files / LOC | coverage | tests | licence |
|---|---|---|---|---|---|---|
| `sge` | libGDX `gdx/src` (+63 cherry-picked backend files) | 605 / 147,163 | 692 / 153,687 | **83%** (100 absent; ~14 in `../lls`) | 265 / 2,185 | Apache-2.0 |
| `sge-colorful` | colorful-gdx `colorful/` | 54 / 62,656 | 46 / 38,721 | **100%** of `colorful/`; `colorful-pure/` (38 files, 40k LOC) 0% | 7 / 54 | Apache-2.0 |
| `sge-textra` | TextraTypist | 95 / 38,607 | 92 / 28,054 | **100%** | 32 / 239 | Apache-2.0 + **MIT** |
| `sge-visui` | VisUI `ui/` + `usl/` | 182 / 27,236 | 156 / 20,639 | **88%** — USL 0 of 18 | 12 / 72 | Apache-2.0 |
| `sge-anim8` | anim8-gdx | 16 / 19,594 | 16 / 9,747 | **93%** | 4 / 21 | Apache-2.0 |
| `sge-ai` | gdx-ai | 167 / 18,086 | 134 / 14,039 | **93%** | 24 / 196 | Apache-2.0 |
| `sge-tools` | libGDX `gdx-tools` | 80 / 17,773 (8 ported) | 8 / 3,857 | **10%** — deliberate | 2 / 10 | Apache-2.0 |
| `sge-gltf` | gdx-gltf | 135 / 11,307 | 141 / 17,615 | **100%** | 40 / 150 | Apache-2.0 |
| `sge-vfx` | gdx-vfx | 45 / 5,732 | 41 / 3,881 | **91%** | 6 / 29 | Apache-2.0 |
| `sge-jbump` | jbump | 19 / 4,045 | 14 / 2,054 | **73%** | 7 / 32 | Apache-2.0 |
| `sge-graphs` | simple-graphs | 29 / 3,784 | 25 / 2,525 | **82%** | 8 / 77 | MIT |
| `sge-controllers` | gdx-controllers | 29 / 2,884 | 18 / 2,141 | **27%** — deliberate | 6 / 33 | Apache-2.0 |
| `sge-ecs` | Ashley | 21 / 2,523 | 24 / 2,404 | **100%** | 18 / 172 | Apache-2.0 |
| `sge-noise` | noise4j | 12 / 2,491 | 10 / 2,608 | **83%** | 3 / 13 | Apache-2.0 |
| `sge-screens` | libgdx-screenmanager | 23 / 2,459 | 20 / 1,691 | **86%** | 6 / 29 | Apache-2.0 |
| `sge-freetype` | libGDX `gdx-freetype` | 4 / 1,891 | 9 / 2,365 | **100%** of the Java layer | 9 / 28 | Apache-2.0 |

### sge — NOT ports. Do not assign these to a porting agent.

- **`sge-physics`, `sge-physics3d`** — original Scala over the Rust **Rapier2D/3D** crates. The API
  is deliberately not Box2D-shaped: `PhysicsWorld` / `RigidBody` / `Collider` / `CollisionGroups`,
  with no `World`, `Body`, `Fixture` or `BodyDef` anywhere. **`gdx-box2d` (238 Java files) and
  `gdx-bullet` (636) are entirely unported** and cannot be poured into this API. Scope at
  `sge/docs/architecture/physics-limitations.md:3-8`.
- **`sge-test/*`** — original harnesses (regression, gauntlet, it-desktop, …). Zero ports.
- **`demos/`, `sge-build/`** — separate sbt builds, not aggregated.
- **`sge-jvm-platform-api`, `sge-jvm-platform-android`** — **decide before assigning.** 32 of 57
  files carry a `Source: com.badlogic.gdx.backends.android.*` line, but they have no `Ported from`
  banner, no covenant, no migration row and no upstream pin. They are an *adaptation* of libGDX's
  Android backend held deliberately outside the port corpus. Evidence is genuinely contradictory.

---

## 2. ssg — 2 ports, 12 non-ports

| module | upstream | licence | Java files / LOC | Scala files / LOC | coverage | tests |
|---|---|---|---|---|---|---|
| `ssg-md` | flexmark-java 0.64.8 | BSD-2 | 912 / 83,680 | 773 / 77,468 | 792 mapped 1:1; **24 undocumented omissions** | 205 / 34,387 |
| `ssg-liquid` | liqp 0.9.2.4-SNAPSHOT | MIT | 135 / 9,542 | 130 / 10,925 | 124 of 135; **11 absent** | 65 / 1,030 |

Package renames the engine needs (`PackageRenameTransform`):
`com.vladsch.flexmark.* → ssg.md.*` (43 Maven modules collapse into one; `util.builder → util.build`)
and `liqp.* → ssg.liquid.*`.

### ssg — NOT ports of Java. Do not assign these to a Java porting agent.

`ssg-js` (terser, JavaScript), `ssg-katex` (TypeScript), `ssg-mermaid` (TypeScript), `ssg-sass`
(Dart), `ssg-minify` (Ruby gems), `ssg-graphs-commons` (rough.js / dagre-d3-es / d3-shape,
TypeScript) — these ARE source-level ports, but of languages the Spoon frontend cannot read.
`ssg-highlight` is an FFI/WASM binding to tree-sitter. `ssg-graphviz`, `ssg-commons`,
`ssg-data-commons`, `ssg-site` are original Scala. Corroborated independently: **not one of these
modules contains a single `Ported from:` header.**

### Size, stated correctly

| | files | LOC | LOC/file |
|---|---:|---:|---:|
| libGDX core `gdx/src` | 605 | 147,163 | 243 |
| flexmark, ported subset | 912 | 83,680 | 92 |

flexmark is **1.5× libGDX core by file count but 0.57× by lines**. Which is "bigger" depends on
whether the engine's cost is per-file or per-construct. (An earlier claim that flexmark is "more
than twice" libGDX core compared flexmark's *whole repo including tests* against libGDX *core only*.)

---

## 3. Hand-off risks — the one thing that will surprise each agent

| module | the surprise |
|---|---|
| `sge` core | **No backend was ported.** 63 cherry-picked files out of 314; LWJGL3/GWT/Android/RoboVM are original Scala over Rust FFI, GLFW and multiarch. An engine-driven port yields a library that cannot open a window. |
| `sge` core | The whole **LZMA `utils/compression` stack (13 files) vanished** → `.ubj.lzma` fonts unsupported (**ISS-617, open, major**). Also gone with no replacement: all 7 `assets/loaders/resolvers/*`, all `Json*`/`UBJson*`/`XmlWriter` (11), the HTTP stack (reimplemented on sttp-client4). |
| `sge` core | `(using Sge)` appears **1,402 times across 363 files**. It is the port's most pervasive shape change and has no mechanical Java analogue. |
| `sge-controllers` | 27% is **not a gap** — Jamepad and GWT were replaced by GLFW and the browser Gamepad API. Porting the missing 21 types produces dead code. |
| `sge-visui` | The **USL skin-DSL compiler is 100% absent** (18 files: lexer, parser, style merger, JSON writer) and undocumented. Self-contained real work, zero reference implementation. |
| `sge-textra` | Depends on the LZMA stack core never ported (ISS-617). Upstream also ships a **second licence** — `typing-label.LICENSE`, MIT, © 2017 Rafael Skoberg — covering exactly the `TypingLabel` + 40 `effects/*` files sge ported. **No MIT attribution appears anywhere in sge.** |
| `sge-ai` | `@TaskConstraint`/`@TaskAttribute` + the `ClassReflection` behaviour-tree parser were replaced by a hand-written `TaskRegistry` of factory closures. **Pure redesign — no mechanical rule produces it.** Also 20 `*Distribution` files merged into one. |
| `sge-tools` | 90% deliberately dropped (all Swing/AWT editors). Not an incomplete port. Missing from `THIRD-PARTY-LICENSES`. |
| `sge-freetype` | Java layer 100% ported, but every `native` method now binds a Rust crate. Carries a **deliberate behavioural fix upstream lacks** (`FreeTypeFontGenerator.scala:13`, Skyline vs Guillotine) — do not "correct" it back. |
| `sge-graphs` | Least idiomatic module: raw `null.asInstanceOf`, anonymous SAM classes, zero `Nullable`, zero renames. Any conformance check will flag it heavily — that is expected, not a defect. |
| `sge-jbump` | Lost a **public copy constructor** `Collisions(Collisions)`, pinned by a deliberately-red test. |
| `sge-screens` | `NestableFrameBuffer` missing → screens binding their own FBO rebind incorrectly (**ISS-657, open, major**). |
| `sge-colorful` | `colorful-pure` (40k LOC) unported with **no recorded rationale anywhere**. Confirm intent before treating it as scope. |
| `sge-visui` vs core | VisUI keeps `AsyncTask` as a class while core maps it to `() => Unit`. **Two answers to one construct in one repo** — the manifest must decide which. |
| `ssg-liquid` | **2,166 lines have no Java counterpart and never can.** liqp parses with two ANTLR `.g4` grammars; the port hand-wrote lexer/parser/token. The engine cannot read, regenerate or diff `.g4`. Decide up front: permanent handwritten overrides, or re-adopt ANTLR and lose JS/Native. |
| `ssg-md` | **The green test numbers do not measure CommonMark conformance.** All 7 spec files in `src/test/resources` are loaded by no runner. The 24 undocumented omissions include the entire `util/html/ui` subpackage (11 AWT files) and `ast.util.TextCollectingVisitor` — where the port maps the *other*, smaller class of the same name, so a public type of the tables extension is silently absent. |

---

## 4. Attribution gaps to close before publishing anything

- **sge**: TextraTypist's MIT `typing-label.LICENSE` is unacknowledged. `sge-tools` and
  `sge-freetype` are absent from `THIRD-PARTY-LICENSES` (possibly covered by the libGDX entry —
  confirm).
- **ssg**: `NOTICE` names flexmark only. **liqp (MIT) is not there**, nor terser, KaTeX, Mermaid,
  dart-sass, rough.js or the Ruby gems. Per-file headers exist, so this is arguably compliant, but
  the NOTICE is incomplete.

Baltic Porter now emits provenance headers (`TirEmitter` + `Provenance`), so a re-port fixes the
per-file half automatically. The NOTICE/THIRD-PARTY files are hand-maintained and are not.

---

## 5. Suggested assignment order

1. **`sge-ecs` (Ashley)** — 21 files, 100% coverage, 172 tests. The cleanest possible second corpus
   library; it will validate the engine's manifest/PortRun path with almost no library noise.
2. **`sge-noise`, `sge-jbump`, `sge-graphs`** — small, mechanical, each exercising the
   `lowlevel.util.*` split. `sge-graphs` additionally tests behaviour against a *non*-idiomatic port.
3. **`sge-gltf`, `sge-anim8`, `sge-vfx`, `sge-screens`** — mid-size, high coverage, few surprises.
4. **`ssg-liquid`** — small Java surface, but resolve the ANTLR decision first. Its upstream test
   suite (105 files) is the best available proving ground for test porting after libGDX.
5. **`sge-ai`, `sge-visui`, `sge-textra`, `sge-colorful`** — large, each with a named redesign
   (TaskRegistry / USL / LZMA dependency / colorful-pure) that must be scoped as its own decision.
6. **`ssg-md` (flexmark)** — the largest Java surface. Do it once the manifest composition path has
   been exercised on something smaller, because 43 collapsed Maven modules is the hardest package
   rename on either list.
7. **`sge` core** — already ported by the engine (596 files, 0 errors, 217/221 tests). What remains
   is the 100 absent types and the backend question, which is a platform decision, not a port.
8. **Deferred / not port work**: `sge-controllers` and `sge-tools` (deliberate replacements),
   `sge-physics*` (Rapier, not Box2D), `sge-freetype` (Rust FFI), `sge-jvm-platform-*` (decide
   status first).
