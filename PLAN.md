# Baltic Porter — Implementation Plan

Companion to `RESEARCH.md` (which holds the evidence and tooling rationale).
This document specifies **what gets built**: a Scala 3 framework, plus
per-port programs written against it. Dates/effort are deliberately absent;
milestones are ordered by risk and gated by measurable acceptance criteria.

---

## 1. Product shape

**Baltic Porter is a framework (a set of libraries), not an application.**
Each "port" (sge, ssg, any future library set) is an ordinary Scala 3
program depending on the framework: it declares a `PortPlan` (modules,
upstream pins, vocabularies, idiom rules, dispositions, layout) and gets the
whole pipeline — analysis, translation, emission, verification, caching,
upstream bumps — from the framework's runner.

**End-to-end contract:** given a set of upstream modules (from one or more
libraries) and a plan, produce a directory tree that is a complete,
self-contained **sbt 2.0 project** — build definition, main sources, test
sources, test resources, license/NOTICE files — that compiles and passes
tests on the platforms the plan declares (JVM always; JS/Native per plan).

Non-goals (v1): languages other than Java as input; emitting anything but
Scala 3; in-place refactoring of existing hand-written Scala; IDE
integration; incremental *watch* mode (batch runs + cache only).

---

## 2. Repository layout of the framework itself

sbt 2.0 build, Scala 3, JVM-only (the framework never cross-compiles;
only its *output* does).

```
balticporter/
  build.sbt
  core/            // plan model, BIR, trivia, pass framework, graph, manifest,
                   // determinism utilities, cache
  frontend-spoon/  // Frontend implementation on Spoon (full-classpath)
  frontend-check/  // optional javac-based resolution oracle (sampled)
  scala-emit/      // BIR → Scalameta lowering, printer, scalafmt, headers
  vocab/           // vocabulary model + loaders + built-in Java→Scala stdlib maps
  test-port/       // JUnit4/5 → munit translation rules, fixture handling
  sbt-gen/         // sbt 2.0 project layout + build definition emission
  verify/          // API parity, corpus diff, platform lint, differential harness
  runner/          // CLI verbs, orchestration, cache-driven execution
  testkit/         // golden-test harness for rule authors (used by ports too)
  corpus-tests/    // framework's own acceptance tests against ../ssg, ../sge
```

Dependency directions: everything depends on `core`; `runner` depends on
all; nothing depends on `runner`. `frontend-spoon` is the only module that
sees Spoon types; `scala-emit` is the only one that sees Scalameta types
(the insulation rule from RESEARCH.md §2.1 — both are swappable).

---

## 3. Core domain model (`core`)

### 3.1 The plan

```scala
final case class PortPlan(
  upstreams:  Seq[Upstream],          // git submodule path + pinned SHA + license info
  modules:    Seq[ModulePlan],        // output sbt modules
  layout:     SbtLayout,              // scala version, platforms, org, base package
  overrides:  OverrideLayer,          // replayable deltas (see §7)
  toolchain:  ToolchainPins,          // scalameta, scalafmt, scalac, sbt versions
)

final case class ModulePlan(
  name:         String,               // sbt module name, e.g. "ssg-liquid"
  sources:      Seq[SourceSet],       // upstream roots + include/exclude globs
  testSources:  Seq[SourceSet],
  moduleDeps:   Seq[String],          // other ModulePlan names (module graph)
  vocabulary:   Vocabulary,           // Tier 2 tables (stacked: stdlib ++ project ++ module)
  idioms:       Seq[Rule],            // Tier 3 passes, ordered
  dispositions: Dispositions,         // external-library handling (§6)
  mapping:      MappingPolicy,        // path convention + explicit merge/split entries
  platforms:    Seq[Platform],        // JVM, JS, Native (subset of layout.platforms)
)
```

A port program is:

```scala
object LiqpPort extends balticporter.PortProgram:   // main() provided by runner
  def plan: PortPlan = ...
```

`PortProgram` gives the CLI for free (§9). Plans are plain values — ports can
share/compose fragments (e.g. an `SsgConventions` object providing the common
vocabulary, header template, and idiom stack across all ssg modules).

### 3.2 Bridge IR (BIR)

Own tree, Java semantics, built once per translation unit from the frontend:

- Nodes for the full Java surface (through Java 25: records, sealed,
  pattern switch) — but **post-resolution**: every name reference carries its
  resolved symbol (FQCN + member signature), every call carries the *chosen
  overload* and boxing/widening decisions, every implicit conversion is
  explicit. BIR has no "ambiguous" states; the frontend must resolve or fail.
- **Trivia is first-class**: every node owns `leading: Seq[Trivia]`,
  `trailing: Seq[Trivia]` (comments incl. Javadoc, blank-line hints).
  Structural invariant checked at emission: every source comment appears in
  the output or in the unit's explicit drop-list.
- Symbols and types are interned in a per-run `SymbolTable`; unit boundaries
  reference symbols, never frontend objects (this is what makes units
  cacheable and the frontend swappable).
- Each unit exports an **interface fingerprint**: digest of its public/
  protected API surface as seen by other units (used for cache early-cutoff
  and for API-parity checks).

### 3.3 Pass framework

```scala
trait BirPass:
  def id: PassId              // stable, e.g. "core/boundary-returns"
  def version: Int            // bumped on any behavior change → cache key
  def phase: Phase            // Normalize | Semantics | Vocabulary | Idiom
  def run(unit: BirUnit)(using PassCtx): BirUnit   // pure; PassCtx = symbols,
                                                   // vocab, diagnostics, graph view
trait ScalaPass:              // post-emission, over Scalameta trees (scalafix-style)
  def id: PassId; def version: Int
  def run(tree: scala.meta.Tree)(using ScalaCtx): scala.meta.Tree
```

- Passes are **small, single-concern, individually versioned** (nj2k's ~54
  granularity). Order is explicit in the plan; the framework refuses
  unordered registration.
- Determinism contract enforced by `testkit` lint on the framework and on
  port programs: no wall clock, no randomness, no hash-order iteration
  (sorted collections only in pass code), no filesystem reads outside
  declared inputs. Every pass ships with golden tests (`testkit` provides
  the harness: input Java snippet → expected Scala output).
- Diagnostics are values (`PortError`, `PortWarning` with stable codes),
  aggregated per unit; a unit with errors fails the run — there is no
  "best effort" emission (the anti-omission stance).

### 3.4 Graph

From the resolved model: nodes = top-level types (+ synthetic nodes for
merge groups from `MappingPolicy`), typed edges (extends, implements, uses,
calls, static-access, annotation). Tarjan SCC → condensation → Kahn with
lexicographic FQCN tie-break ⇒ unique deterministic order of translation
batches (an SCC = one batch with a shared symbol environment). The same
machinery, one level up, orders modules. Graph is also queryable by passes
(e.g. the `(using Sge)` idiom needs reachability over constructor edges) and
by `verify` (dead-mapping detection).

### 3.5 Manifest

The persistent per-file record, evolving `.rescale/data/migration.tsv` so
existing data imports:

```
unit_id | source_paths (1..n) | target_path | status | source_digests |
upstream_pin | interface_fp | output_fp | engine_fp | last_run
```

`status ∈ {generated, handwritten, skipped(reason), shimmed}`.
`handwritten` grandfathers existing hand-ports: the engine verifies API
parity against them but does not overwrite. The manifest is an *output* of
runs (plus declared statuses), never hand-edited.

---

## 4. Pipeline (what a `translate` run does)

1. **Load plan; verify pins.** Upstream submodule SHAs must match the plan;
   mismatch fails loudly (freshness is a deliberate `bump`, §8).
2. **Frontend.** Per module: build Spoon full-classpath model (source roots +
   dependency JARs from dispositions). Comments enabled. Sampled javac
   cross-check of resolved types (`frontend-check`) in CI mode.
3. **Graph + batching.** §3.4. Compute per-unit action keys; consult cache;
   schedule only misses (§10).
4. **BIR construction** per unit (resolution baked in, trivia attached).
5. **Passes**: Normalize (desugar to canonical BIR: explicit boxing,
   explicit `this`, fallthrough closures computed, constructor graphs
   funneled) → Semantics (Tier 1 catalog, RESEARCH.md §4.2) → Vocabulary
   (Tier 2 tables + platform lint) → Idiom (Tier 3 from plan).
6. **Lowering** to Scalameta trees + provenance header synthesis (original
   paths, authors/license parsed from upstream headers, upstream pin, engine
   fingerprint).
7. **ScalaPasses** (project post-transforms), pinned printer, pinned
   scalafmt. Byte-stable output.
8. **Placement** per `MappingPolicy`: shared vs platform source dirs
   (`scala/`, `scalajvm/`, `scalajs/`, `scalanative/` — the layout sge/ssg
   already use), merges, splits.
9. **Verification gates** (§11) — configurable subset per invocation.
10. **Manifest + cache update**; run report (translated / cached / failed /
    skipped, with diagnostics).

Test sources go through **the same pipeline** with `test-port` rules added
(§5). Resources (spec fixtures) are copied content-addressed with digests
recorded in the manifest.

---

## 5. Test porting (`test-port`)

First-class, because the goal explicitly includes tests and because ported
upstream tests are oracle #2 (RESEARCH.md §4.7).

- **JUnit4 → munit** rule set: `@Test` → `test("name")`, `@Before/@After` →
  `beforeEach/afterEach` overrides, `@BeforeClass` → suite-local lazy/init,
  `assertEquals(expected, actual)` argument-order mapping, `assertThrows`/
  `@Test(expected=…)` → `intercept[E]`, `assertArrayEquals` → sameElements
  helpers, Hamcrest subset → assertions vocabulary table. JUnit5 variant of
  the same. (Both target repos already standardize on munit.)
- **Parameterized tests** (`@RunWith(Parameterized)`, `@ParameterizedTest`)
  → dynamic test registration (the pattern ssg already uses for flexmark
  spec suites).
- **Data-driven spec fixtures** (flexmark `*_spec.md`, terser `compress/*.js`
  style): not translated — copied as test resources + a generic runner
  emitted once per suite family. Plan hook: `SpecRunner` templates.
- **Skip/expected-fail ledger**: explicit plan entries only, each requiring a
  reason + issue reference; the ledger is emitted into the run report and
  ratcheted (count can only decrease unless the plan changes) — the C3/C4
  anti-cheat lessons, enforced structurally.
- Test-only semantic traps get their own passes: reflection-using tests
  flagged for JS/Native exclusion, timing/concurrency tests platform-gated.

---

## 6. External-dependency dispositions (`vocab` + plan)

Every external symbol referenced by upstream must have exactly one
disposition; unresolved ⇒ `analyze` reports it and `translate` fails:

```scala
enum Disposition:
  case PortInRepo(module: String)          // becomes another ModulePlan
  case MapTo(vocab: VocabTable)            // API mapped onto existing Scala/cross lib
  case Shim(sourceDir: Path)               // handwritten companion impl (never generated)
  case PlatformProvided                    // java.* covered by JVM + javalib (checked
                                           // against JS/Native coverage tables)
  case Drop(reason: String)                // with the unit exclusions it implies
```

`vocab` ships: (a) the Java→Scala stdlib/core mapping tables, (b) the
**platform coverage + lint data** (RE2 regex limits on Native, missing
`java.text`/`java.time`/`Locale`, no executors on JS, `@safePublish`, string
identity on JS…), each lint keyed to the platforms the module targets, (c)
loaders for a simple text table format so mappings are diffable data, with a
code hook for call-shape adaptations that tables can't express.

---

## 7. Override layer

Replayable, never patches on generated text (RESEARCH.md §2.3):

1. **Tier 3 rules / ScalaPasses** — preferred; whole-pattern, survives
   upstream drift.
2. **Declaration overrides** — `overrides/<unit>/<Member>.scala` fragments
   spliced by symbol id (the j2cl `.native.js` analog); engine verifies the
   overridden symbol still exists upstream, else errors on `bump`.
3. **Whole-file handwritten** — `status = handwritten` in mapping policy;
   engine checks API parity against the original but does not emit.
4. **Comment additions/edits** — keyed by symbol id, applied at emission.

All override applications are logged in the run report; orphaned overrides
(target symbol gone) are errors, not silent skips.

---

## 8. Upstream freshness (`bump` verb)

`bump --module ssg-liquid --to <sha>`:
diff old..new pin → per-unit source-digest comparison → retranslate changed
units + interface-ripple (early cutoff via interface fingerprints) → GumTree
declaration-level report of upstream changes (what changed, not just that) →
orphaned-override / disposition-gap check → regenerate → gates → single
reviewable diff of generated tree + updated manifest. Plan pin is updated
only when gates pass.

---

## 9. Runner CLI (per-port program gets these verbs)

```
analyze     build model + graph; report externals without dispositions,
            mapping gaps, SCC clusters, platform-lint preview
translate   full pipeline → generated tree (cache-aware; --unit/--module scoping)
emit-build  (re)generate sbt project skeleton (§10)
verify      gates: parity | compile[:jvm,js,native] | tests | corpus-diff | all
diff        corpus convergence against an existing hand-port tree (reports
            per-file: byte-equal / ast-equal / diverged, with rule attribution)
bump        §8
report      manifest + coverage + ledger dashboards (text/JSON)
```

Exit codes and JSON output are stable — port programs run in CI.

---

## 10. sbt 2.0 output contract (`sbt-gen`)

Generated tree (one repo per port program):

```
<out>/
  build.sbt                  // Scala 3 build definition, generated
  project/build.properties   // sbt 2.0.x pinned from ToolchainPins
  project/plugins.sbt        // sbt-projectmatrix, sbt-scalajs, sbt-scala-native,
                             // per layout.platforms
  .scalafmt.conf             // pinned version + full config (byte-reproducibility)
  NOTICE / THIRD-PARTY-LICENSES   // synthesized from upstream license metadata
  <module>/src/main/scala/...          // shared generated sources
  <module>/src/main/scalajvm|scalajs|scalanative/...
  <module>/src/main/handwritten/...    // shims (§6), added as extra src dir, never written
  <module>/src/test/scala/... + resources/
```

- Build emission uses `projectMatrix` with the suffix-source-dir convention
  both repos already use on sbt 2 (sge is the proof it works end-to-end
  with JVM/JS/Native); per-platform settings blocks come from the plan
  (JS module kind, Native config, `--enable-native-access` etc. as options).
- Module graph → `dependsOn` edges; external Scala deps (scala-java-time,
  munit, `lls`, …) come from dispositions and vocabulary requirements —
  `sbt-gen` computes the dependency list from what the vocabulary actually
  mapped onto, so no unused deps.
- Everything generated carries the do-not-edit provenance header; the build
  file itself included. `emit-build` is idempotent and byte-stable.
- Acceptance: `sbt +Test/compile` and `sbt test` succeed on declared
  platforms with **zero manual edits** after `translate` + `emit-build`.

---

## 11. Verification gates (`verify`)

Ordered by oracle strength; all deterministic:

1. **corpus-diff** (during bootstrap): engine output vs accepted hand-ports
   in ../ssg/../sge, three-state per file, aggregated convergence %.
2. **parity**: structural public-API comparison original-BIR vs emitted
   Scala tree (the computed covenant). Every upstream public symbol must be
   present, mapped, overridden, or explicitly dropped-with-reason.
   Comment-preservation invariant checked here too.
3. **compile**: scalac per platform per module, batched in graph order;
   errors mapped back to units + originating passes where possible.
4. **tests**: run ported suites per platform; ledger ratchet enforced.
5. **differential** (opt-in per module): run original Java (JVM) vs ported
   Scala (JVM/JS/Native) on generated/property inputs for value-level APIs;
   harness scaffolding in `verify`, cases declared in the plan.

---

## 12. Cache (`core.cache`)

As specified in RESEARCH.md §4.6 — CAS blob dir + SQLite action index in
`<out>/.balticporter/cache` (location configurable; safe to delete).
Action key = source digests + dependency interface fingerprints + engine
fingerprint (framework version + ordered pass ids/versions + vocab digests +
plan-relevant config) + toolchain pins + salt. Early cutoff on interface
fingerprints. Frontend model build is scoped per module and skipped when the
module's file-set digest is unchanged. Cache is advisory: `--no-cache`
reproduces byte-identical output (CI spot-checks this).

---

## 13. Milestones (ordered, gated; no dates)

**M0 — skeleton + round-trip.**
`core` BIR + trivia, `frontend-spoon`, `scala-emit` minimal (identity-ish
translation of a Java subset), `testkit`.
*Gate:* 20 hand-picked Liqp files translate to compiling Scala; every source
comment present in output; two consecutive runs byte-identical.

**M1 — Tier 1 catalog on the Liqp corpus.**
Implement the semantic pass set (RESEARCH.md §4.2) driven entirely by
`diff` against ssg's accepted `ssg-liquid` port; graph + batching in place.
*Gate:* ≥90% of Liqp's 135 files ast-equal-or-better vs the hand port
(divergences individually classified: missing rule / rule bug / hand-port
idiom to be encoded in Tier 2-3); `parity` gate green.

**M2 — vocabulary + idioms + build emission.**
`vocab` engine + stdlib tables + nullability pass; Tier 3 API with ssg's
idioms (Nullable, no-return, headers); `sbt-gen`.
*Gate:* `translate && emit-build` on Liqp yields an sbt 2.0 project where
`sbt Test/compile` passes on JVM with zero manual edits; corpus convergence
≥97% ast-equal; remaining divergences documented as accepted improvements.

**M3 — tests.**
`test-port` (JUnit4→munit, parameterized, fixtures), ledger.
*Gate:* Liqp's upstream test suite ported and green on JVM; failures
triaged into ledger with reasons or fixed by rules.

**M4 — cross-platform + cache.**
Platform lint tables, platform source-dir placement, JS/Native compile +
test orchestration; full cache with early cutoff.
*Gate:* Liqp module compiles + tests green on JVM/JS/Native; warm re-run
touches zero units; `--no-cache` byte-identical.

**M5 — scale + second corpus.**
flexmark (1,100 files: merges, skips, visitor patterns, F-bounded generics)
and one sge extension (e.g. jbump or noise4j) with the `(using Sge)` Tier 3
pass (exercises graph-wide idioms + platform splits + `lls` vocabulary).
*Gate:* both reach ≥95% ast-equal convergence; `bump` demo: move one
upstream pin forward and land the regen diff with gates green.

**M6 — cold port (the real acceptance test).**
A library neither repo has ported (candidate from sge's ecosystem list or a
flexmark extension left unported). No corpus to lean on: gates 2-5 only.
*Gate:* generated sbt project + ported tests green on declared platforms;
human review of the output signs off on idiom quality; framework docs +
example port program published.

Throughout: LLM agents are used to *write passes, tables, and dispositions*
against `testkit` golden tests and `diff` output — never to translate units
or stamp statuses (the division of labor the evidence supports).

---

## 14. Risks and their mitigations (top 5)

1. **Spoon model gaps/normalization quirks** (implicit nodes, lexical
   fidelity) → BIR insulation + `frontend-check` javac oracle + fallback
   frontend (JDT) behind the same interface; trivia recovery via
   `SourcePosition` slicing where Spoon's attachment is off.
2. **Scalameta printer churn/gaps for Scala 3 constructs** → pinned version,
   golden tests on every pass, printer behind an interface with the
   typed-line-printer (smithy4s pattern) as the escape hatch.
3. **Corpus convergence plateaus** (hand-ports contain unprincipled one-off
   decisions) → three-state diff separates "engine wrong" from "hand-port
   idiosyncratic"; the latter become documented accepted divergences or
   per-file overrides, not rule contortions.
4. **Constructor graphs / statics init order producing subtly wrong
   semantics** → these passes get differential tests (gate 5) not just
   golden tests; known-trap list from RESEARCH.md §6 is the review
   checklist.
5. **sbt 2.0 / JS / Native plugin ecosystem drift** → `ToolchainPins` in the
   plan; `sbt-gen` templates versioned like passes; sge's working sbt 2
   cross-platform build is the reference configuration.
