---
paths:
  - "Justfile"
  - "scripts/**"
  - "port-report/**"
  - "build.sbt"
---

# Measurement — lanes, baselines, correlation, paths

Detail for `CLAUDE.md` §5, §5.1 and §5.4.

## Lanes

| recipe | lane |
|---|---|
| `just gdx-measure` / `gdx-test-measure` | libGDX core — emit, checks, break residue, compile, correlate / its suite, then RUN |
| `just ashley-measure`, `anim8-measure`, `gltf-measure`, `screens-measure`, `vfx-measure`, `ai-measure`, `textra-measure`, `visui-measure` | dependent ports compiled WITH libGDX core |
| `just ai-test-measure` | gdx-ai's JUnit suite, compiled WITH libGDX core and gdx-ai |
| `just ai-diff-measure`, `textra-diff-measure`, `visui-diff-measure` | DIFFERENTIAL gates — the hand port's own suite against the emitted port (no xplat compiles) |
| `just sg-measure`, `noise4j-measure`, `jbump-measure` | simple-graphs + suite; noise4j (no upstream tests); jbump (ships NO suite, re-derives that zero) |
| `just lls-measure` | the twelve libGDX sources lls ported — then lls's OWN MUnit suite against them, as a second number (`expected-errors.suite`) |
| `just usl-measure` / `usl-test-measure` | USL, its own port root / its 7-`@Test` suite |
| `just liqp-measure` | liqp + its 105-file suite — RUN when it compiles |
| `just md-measure` / `md-test-measure` / `md-ext-measure` | flexmark core + eleven util modules / the 730-`@Test` suite / extensions as ONE dependent port |
| `just measure-all` | every lane with `BP_FULL=1`, SERIALLY |
| `just <lane>-measure-full` | adds JS, Native and ref-flags compiles |
| `just decision-counts`, `members-unchanged`, `baseline-{list,show,diff,accept}`, `upstream-pin`, `deps-lint`, `catalog-coverage` | sizes, blast radius, baselines, vendored pins (mismatch FATAL), coordinates vs manifest, corpus-wide catalog rows |
| `just injections-lint` | every injected/vendored shim (`corpus/*-overrides/`, `ported/*/src/`, `runtime/src/main/`) parses under `-no-indent` (parser-only, per file) |
| `just ecs-dropin`, `ecs-divergence`, `dropin-all` | sge-ecs drop-in and divergence census (NOT in `measure-all`; red until parity) |

Re-derive both sides (`grep -E '^[a-z0-9-]+-measure[a-z0-9-]*:' Justfile`) rather than trusting a
count. Shared mechanism (`java_test_count`, `reconcile_outcomes`, `break_residue`, `compile_guard`,
`show_check_report`, `correlate`, `headline`) is `scripts/_lib.sh`; policy is a variable at the top
of the `Justfile`. **Never `set -e` in a lane**: `grep -c` exits 1 on zero, and zero errors is the
success case. Long runs go through `launchctl submit` (the harness kills the tree at call end).

## Required checks

Every run must record: `signature`, `omissions`, `portability(all|emitted|injected)`,
`dependency-coverage(all|declared|)`, `substitution(emitted|dangling)`, `remediation`, `policy`,
`manifest`, `port-map`, `trivia(|recovered|deliberate)`, `jdk-surface`, `base-surface`,
`rewrite-callsites`, `idiom(converted|refused|residue)`,
`catalog(consulted|unreached|unmechanised|undischarged|uncited)` — plus what the run's own pipeline
registers. Why the families are split:

- `base-surface` is required of a BASE too: a run that asked nothing and one whose recording was
  skipped are indistinguishable without the row.
- `trivia`: `lost = 0` is a bar met by RECOVERING everything; `recovered` and `deliberate` are
  reported apart. `catalog`: `unreached = 0` is met by declaring every row unmeasured; `uncited` is
  never asserted on (a citation invented to silence it is worse than the gap). `idiom`: `refused = 0`
  is met by converting nothing; the denominator is recomputed every run; all three required of every
  port, `jdk-surface`'s reason.
- `dependency-coverage` is the OTHER half of `portability`: a finding needs three conjuncts (usage
  fired, no declared dependency covers it, no `verdictOverrides` alternative), read THROUGH the
  overrides not as a second filter. `(all)` is the enumeration behind the D2-filtered residue;
  `(declared)` classifies each coordinate by a 2×2 over pre-pipeline and emitted usage, read from the
  artifact's OWN JAR — `Unverifiable` is a third value, never a `no` (`DESIGN.md` §8.20, P8).
- Conditional lanes are derived: `collection-closure/boundary/retarget/internal` with
  `CollectionsTransform`, `nullability-boundary`, `opaque-boundary`, `test-framework(refused)` (a
  refusal population that was once a `println` and a prose row); `service-providers` and `resources`
  from the manifest (`DESIGN.md` §8.17, §8.22); one `api-parity(<family>)` per `ApiParityCheck.Families`
  entry when `parity` is declared. `porter-notes`, `break-catch`, `try-resource`, `switch-null`,
  `heap-pollution`, `cast-conversion`, `overload-risk`, `markers` record on every run.

## Remediation

`DESIGN.md` §8.16: a phase publishes a MENU of remedies, a port SELECTS one per location. A diff
reads `<lane> N->M, remediation(resolved) 0->(N-M)`; a lane that fell with nothing to attribute is
the residue rules' own refusal. `PolicyIssue.NeverApplied` reports a selection that did nothing.
ONE POLICY, ONE SPELLING: a remedy is never a second way to state a manifest or phase key; the menu
carries the POINTER, and a refused option carries its `ENGINE-LIMITS.md` id at the declaration
(twenty candidates, six entries, all six *the port has read this site*). An ACCEPT answers a QUESTION
(the mechanism's own words say it declined to decide — `promotionEscapes`, `preservedAnnotations`),
never a DEFECT (a residue cited as *measured worse*, a LOSS, a WORK ITEM). The arithmetic is
`sum(drained)` (`AppliedResolution.drained`, ZERO where a rewrite relocates rather than removes);
`remediation(refused)` is one row per declined site naming the guard.

## Baselines — written by the run, promoted by `just baseline-accept`, gated BOTH ways

- `expected-errors`: the headline was the one measurement nothing compared (screens went 0 -> 3
  unnoticed). Fewer fails as loudly as more.
- `expected-errors.js` / `.native` (`BP_FULL=1`, `sbt_xplat_compile`): a COMPILE gate against each
  platform's javalib, not a portability gate (P1). Pins from `project/plugins.sbt` (Scala.js 1.22.0,
  Scala Native 0.5.12). A platform `targets` excludes is skipped with a printed line.
- `expected-errors.ref` (`sbt_ref_compile`, `port-*-ref` projects with sge/ssg's `scalacOptions` —
  `-no-indent -Werror -Wunused:…`, `DESIGN.md` §8.24): errors AND warnings counted; `-Xmacro-settings`
  dropped; a dependent's `-ref` `dependsOn` the base's JVM row with `-nowarn`. Every shim under
  `balticporter/corpus/*-overrides/`, `ported/*/src/` and `balticporter/runtime/` uses brace syntax
  (checked by `just injections-lint`).
- `expected-lost` (`test_discovery_guard`): liqp's `!! TESTS LOST — 64 of 639` was a constant nobody
  read; a RECOVERED test is acknowledged, not absorbed. `TestDiff.disappeared` gates too.
- `findings.tsv` (`findings_baseline_guard`, `cut -f2-` drops the line-ordered id): counts hold over
  a moved owner, `UsageKind` or running total. Eight dependent baselines were stale (`280 -> 220`)
  since waves 0/1, mis-attributed to a worktree difference until somebody read the row.
- `port-map.tsv` (`port_map_guard`, whole file, metadata field by field): went stale twice by hand
  (60 member rows; nine `policy=` headers). No map while a baseline exists fails — `discoverIn`
  would hand dependents the COMMITTED map.
- Drop-in (`ecs-dropin`, `dropin-all`): `errors-count.dropin.<platform>`, `tests.<platform>.tsv`,
  `scalacOptions.txt`, under `baseline/dropin/`.
- `expected-failures.tsv` is the normally-empty escape hatch; deliberate failures are DERIVED from
  `dropped-types.tsv` (`upstream` TAB `emitted`, `expected#derived` vs `#declared`).

## The JDK, the guards, the dry run

- The frontend resolves external symbols from CLASS FILES, so emitted text is a function of the JDK.
  GraalVM 24 emitted `override def getChars` on `CharArray` (`CharSequence` gained it in 23); the
  JDK-22 compile answered `overrides nothing`. `jdk_version` (22) is expected; `jdk_guard` checks the
  sbt server's JVM and `jvm.txt`; the port map's `jdk=` header mismatch is fatal (M5.10). Restart the
  server under the right `JAVA_HOME`; never move `jdk_version`.
- Widening: a map-key test widened to "mentions a wildcard at any depth" moved 6 libGDX and then 9
  jbump members at 0 errors and 0 counts; done only when every other port is BYTE-IDENTICAL or the
  difference is stated. Narrowing: keeping the erased-receiver view's type arguments regressed
  libGDX 0 -> 1 because ARGUMENT erasure was one of three readings of one table (G21) — make the
  co-readers one, in its own commit, first.
- A dry run priced a warning lane at 1; the live pipeline read 25 (a `TypeRedirectTransform` gave
  24 classes an ancestor outside the program; CT7's correction).
- Migrator and correlate steps run `sbt -batch` — `sbt -client` connected to another worktree's
  server (M5.11).

## §5.1 — correlation and attribution

- `srcmap.tsv` (member → emitted line range → `Origin`) and `members.tsv` come from `TirEmitter.srcMap`,
  a value one emitter owns. `CorrelateRun` / `CorrelateMain` (`just correlate`) join compiler and
  test-runner output; `errors.tsv` splits approximate-region / engine-gap / outside-the-map, plus
  `Correlate.Lane.Declared` on a `preview = true` run.
- A baseline is a claim about the run that PRODUCED it: one `members.tsv` row diverged between a
  worktree and the primary at one commit, and the committed digest predated the same wave's own
  earlier commit. Re-run before `baseline-accept`; compare checkout against checkout at one commit.
- MUnit terminal markers: `  + ` pass, `==> X ` fail, `==> s … skipped`; the third was dropped and
  ashley recorded 110 of 112. `skipped` is PREVENTION, `ignored` a DECISION; a missing `--tests`
  path is fatal. `tests.tsv` anchors each failure on the first ported frame (`main-frame` /
  `test-frame`).
- Engine specs gate on nothing: `PortMapAcceptanceSpec` asserted 8 while the answer had been 7,
  `assume`d on an artifact a fresh worktree lacks. `sbt test` is `testQuick`; the full suite is
  `testOnly *`, run AFTER `measure-all`.
- `decisions.tsv`: `Reason` is `Universal(rule)` / `Configured(phase, key)` / `LibraryRule(rule)`;
  `Configured`'s KEY is the manifest entry verbatim. One row per DECLARATION; scoped to this
  module's declarations (libgdx-test: 961 of 1240 would otherwise be the base's).
- Artifact writes are gated on the LAYER: one unconditional `PortMap.write` published maps from
  forked test suites into the checkout (once a COMMITTED `port-report/jar/`). `CheckReport.dir`
  falls back to `port-report/<main class>` and answered `WorkerMain` under sbt; where no identity can
  be derived the layer is OFF, an explicit `reportDir` is the one thing that enables it.
- `reconcile_outcomes` reconciles against the EMITTED count; `727 outcomes against 725 emitted` on
  one suite is an abstract suite run once per concrete subclass — honest to report, wrong to gate.
- The test-discovery counter counts MUnit's CURRIED APPLICATION, not the name's spelling; honest
  negatives are a selection, a declaration and a call applied to no body (liqp's 181 `test(0)` array
  reads); anything else is reported with file and line. `scala-cli compile` needed `--test` to report
  test-scope errors (0 without, 6 with) — a lane's own command line is part of the measurement.

## §5.4 — paths

Three parts were bitten: `PortRun.converted` (635 files instead of 30), `CheckReport.relativise`
(a `..` stack depending on where the link lives), `TirEmitter.sourcePathOf` (`gdx-vfx/gdx-vfx/core/…`
in a worktree, 44 vfx + 6 noise4j digests at one commit). Ownership of a unit is
`FrontendConfig.files`, not a path prefix. `balticporter.reportPathRoot` is set by the lanes and
derived from the port's own configuration, never the operator.

- `OUTCOMES LOST` / a test row that moved to the PREVIOUS suite: the suite threw before munit's
  header line completed (a stderr stack trace splices into the header, `…scala:149)ssg.md.ext…:`),
  so the parser attributes its tests to the last clean header. Read `$MEASURE_TMP/<lane>run.txt`
  for the exception first — md-ext's was an NPE in a class initialiser reading an undeclared
  classpath resource (`PortManifest.resources`), not a lane flake (2026-09-05).
- **The first compile of a NEW port in a checkout under-reports** (lls, 2026-09-05: 100 in the first
  compile of each of two checkouts, 149 in every later one — clean, warm, js, native, `.ref` alike —
  with `TimSort` reading 0 beside its twin `ComparableTimSort` at 22). A new port's floor is taken
  from the SECOND compile, and a `clean` for a non-zero-floor port runs BEFORE the migrator: `clean`
  deletes `src_managed` (§5.5), so between migrate and compile it counts the hand-written half alone.

