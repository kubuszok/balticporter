---
paths:
  - "Justfile"
  - "scripts/**"
  - "build.sbt"
  - "balticporter/engine/**/core/SubstitutionCheck.scala"
---

# Measurement — baselines, correlation, paths

Detail for `CLAUDE.md` §5, §5.1 and §5.4.

Per-library measure lanes, baselines and port reports live in each consumer repository (lls, sge,
ssg). The engine verifies itself against the consumers with `just consumers-check`.

## The consumers check proves SOURCE compatibility, not the published chain

`consumers-check` publishes the engine AND each consumer's port artifact (`lls-port`) to the ivy-local
repository, and a dependent consumer with `Resolver.defaultLocal` picks that fresh `lls-port` up. So a
change to a value type a consumer's policy links against (`PortManifest` gained a parameter: the
case-class `apply`/`copy` signatures moved) passes every consumer locally and throws
`NoSuchMethodError` on the dependent's CI, which resolves the PUBLISHED `lls-port` (sge PR #155,
2026-09-24). A change to `PortManifest`, `RunScope` or any type a `*-port` artifact constructs is a
RELEASE of every base's port artifact before a dependent may pin the engine; say so in the commit.

## Required checks

Every run must record: `signature`, `omissions`, `portability(all|emitted|injected)`,
`dependency-coverage(all|declared|)`, `substitution(emitted|dangling|doc-mention)`, `remediation`, `policy`,
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
- `substitution(dangling)` is fatal, so it reads CODE only (universal): a text search counted a
  dropped exception's name in two copied `@throws` Javadoc lines as dangling at 0 code references,
  and comments cannot be rewritten. Mask comments, literals and porter notes with the Scala 3
  tokenizer, match the upstream AND the emitted (renamed) FQN at identifier boundaries, and count a
  comment-only mention apart in the non-fatal `substitution(doc-mention)`, one row per type and file.
  The emitted name is read AFTER the rename phase binds its type renames, and a replacement a
  platform row or `providedSources` declares counts as present: read from the pre-binding
  `droppedEmittedNames` and `outDir` alone, a consumer went 0 -> 4 false fatals.
- `dependency-coverage` is the OTHER half of `portability`: a finding needs three conjuncts (usage
  fired, no declared dependency covers it, no `verdictOverrides` alternative), read THROUGH the
  overrides not as a second filter. `(all)` is the enumeration behind the residue filtered by
  structural ownership (a dependent module's program contains its base, so findings are attributed to
  the module that owns the declaration, never to the base);
  `(declared)` classifies each coordinate by a 2×2 over pre-pipeline and emitted usage, read from the
  artifact's OWN JAR: whether a declared dependency is still needed is decided from both the original
  and the emitted program, and what the artifact provides is read from its own jar —
  `Unverifiable` is a third value, never a `no` (unfetchable means unverifiable, not unused).
- Conditional lanes are derived: `collection-closure/boundary/retarget/internal` with
  `CollectionsTransform`, `nullability-boundary`, `opaque-boundary`, `test-framework(refused)` (a
  refusal population that was once a `println` and a prose row); `service-providers` and `resources`
  from the manifest; one `api-parity(<family>)` per `ApiParityCheck.Families`
  entry when `parity` is declared. `porter-notes`, `break-catch`, `try-resource`, `switch-null`,
  `heap-pollution`, `cast-conversion`, `overload-risk`, `reflection-visibility`, `markers` record on every run.

## Remediation

A phase publishes a MENU of remedies, a port SELECTS one per location. A diff
reads `<lane> N->M, remediation(resolved) 0->(N-M)`; a lane that fell with nothing to attribute is
the residue rules' own refusal. `PolicyIssue.NeverApplied` reports a selection that did nothing.
ONE POLICY, ONE SPELLING: a remedy is never a second way to state a manifest or phase key; the menu
carries the POINTER, and a refused option carries the name of the recorded limit that owns the site
at the declaration
(twenty candidates, six entries, all six *the port has read this site*). An ACCEPT answers a QUESTION
(the mechanism's own words say it declined to decide — `promotionEscapes`, `preservedAnnotations`),
never a DEFECT (a residue cited as *measured worse*, a LOSS, a WORK ITEM). The arithmetic is
`sum(drained)` (`AppliedResolution.drained`, ZERO where a rewrite relocates rather than removes);
`remediation(refused)` is one row per declined site naming the guard.

## Baselines — written by the run, promoted by `just baseline-accept`, gated BOTH ways

- `expected-errors`: the headline was the one measurement nothing compared (screens went 0 -> 3
  unnoticed). Fewer fails as loudly as more.
- `expected-errors.js` / `.native` (`BP_FULL=1`, `sbt_xplat_compile`): a COMPILE gate against each
  platform's javalib, not a portability gate — compiling for Scala.js proves nothing about
  portability, since only the linker rejects missing JDK APIs and a library has no entry point, so
  portability is checked over the typed tree instead. Pins from `project/plugins.sbt` (Scala.js 1.22.0,
  Scala Native 0.5.12). A platform `targets` excludes is skipped with a printed line.
- `expected-errors.ref` (`sbt_ref_compile`, `port-*-ref` projects with sge/ssg's `scalacOptions` —
  `-no-indent -Werror -Wunused:…`): errors AND warnings counted; `-Xmacro-settings`
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
- `counts.tsv`'s `upstream` row (`<upstreamName>@<sha>`, written by `PortRun` from the run's
  `Provenance`; no provenance, no row): `upstream_guard` prints `!! UPSTREAM MOVED a -> b` and
  REPORTS ONLY. anim8's submodule moved to `89e0557` and the lane read `suite REGRESSED 0 -> 2`
  plus 222 digests: no baseline said which java tree it was taken on.

## The JDK, the guards, the dry run

- The frontend resolves external symbols from CLASS FILES, so emitted text is a function of the JDK.
  GraalVM 24 emitted `override def getChars` on `CharArray` (`CharSequence` gained it in 23); the
  JDK-22 compile answered `overrides nothing`. The JDK version is an input to every measurement: the
  frontend and the compiler must run on the same recorded JDK, otherwise a correct override reports
  as overriding nothing. `jdk_version` (22) is expected; `jdk_guard` checks the
  sbt server's JVM and `jvm.txt`; the port map's `jdk=` header mismatch is fatal. Restart the
  server under the right `JAVA_HOME`; never move `jdk_version`.
- Widening: a map-key test widened to "mentions a wildcard at any depth" moved 6 libGDX and then 9
  jbump members at 0 errors and 0 counts; done only when every other port is BYTE-IDENTICAL or the
  difference is stated. Narrowing: keeping the erased-receiver view's type arguments regressed
  libGDX 0 -> 1 because ARGUMENT erasure was one of three readings of one table — a raw result read
  through an erased receiver is typed as the erased instantiation, decided per argument position, and
  narrowing that guard regressed another port — make the
  co-readers one, in its own commit, first.
- A dry run priced a warning lane at 1; the live pipeline read 25 (a `TypeRedirectTransform` gave
  24 classes an ancestor outside the program the dry run never walked).
- Migrator and correlate steps run `sbt -batch` — in a git worktree `sbt --client` can connect to
  another worktree's server and write run artifacts into the wrong checkout, so each worktree must
  use its own server directory.
- GUARD ORDER, every lane: `show_check_report`, `upstream_guard`, then the compile, then
  `error_baseline_guard`, then `full_compiles` (js/native/ref, `BP_FULL`), THEN the suite and its
  `test_outcome_guard`. Run after the suite, `errors-count.{js,native,ref}` were left STALE by the
  first `exit` and a renamed hand test refused the promotion for the wrong reason.

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
  `testFull`, run AFTER `measure-all`, and it is the engine's pre-push gate.
- **A spec that reads repository files at run time declares them as INPUTS, or sbt 2's `test`
  replays its cached pass** (universal, a build fix): `test` reruns a suite only when the digest of
  the classes it links changes, and neither a file read through a path nor a generated resource's
  content is in it. `PolicyKeyLintSpec` passed from the cache after a main-source edit that broke it
  and reached master green. The engine folds every production source into `Test / extraTestDigests`
  (`sourceTreeDigest`, uncached); a scratch violation went skipped -> failing under `test`.
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
  classpath resource (`PortManifest.resources`), not a lane flake.
- **sbt prints at most `maxErrors` (100) diagnostics; the lane counts diagnostics.** lls read 100 on
  every REAL compile while scalac said `149 errors found`; the 149 the lanes had read came
  from a cached-failure replay that printed more. Every port and `-ref` project sets `maxErrors :=
  100000` (`portSettings`/`refPortSettings`); a `-Xmax-errors` scalac flag does not lift sbt's cap. A
  `clean` for a non-zero-floor port runs BEFORE the migrator (`clean` deletes `src_managed`, §5.5).

- **sbt 2's disk action cache replays a previously FAILED compile without its diagnostics**
  (`sbt.util.CachedCompileFailure`, liqp `.ref`: 3 -> "no countable error"); `clean` does
  not bypass it. The `-ref` projects carry a per-execution nonce in `scalacOptions`
  (`refPortSettings`) so every diagnostics compile is a real one; `compile_guard` names a replay
  instead of calling it DID NOT RUN. A JVM port with a non-zero floor that starts replaying gets the
  same nonce, never a `clean`.

