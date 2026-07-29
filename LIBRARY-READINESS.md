# Baltic Porter as a LIBRARY — what sge and ssg need before they can depend on it

Source: adversarial review by the `porting-auditor` (Fable 5), 2026-07-29, at commit `8fea564`.
Every claim marked CONFIRMED below was independently re-verified against the working tree after
the review; the commands are given so they can be re-run. Nothing here has been implemented.

**The goal being evaluated.** `../sge` (a hand-written Scala 3 port of libGDX and 17 extensions)
and `../ssg` (hand-ported Java libraries — liqp, flexmark) stop hand-maintaining their ports and
instead depend on Baltic Porter as a published library, feeding it Java sources plus per-library
configuration. Their porting work is then maintained by **agents working in those repositories**,
without this repository's context.

**Verdict: not today, and the gap is not polish.** The mechanisms are largely sound and correctly
classified against CLAUDE.md §1 — the auditor tried to break the seams and mostly failed. What is
missing is the *product*: a single pipeline, an entry point, published artifacts, and a feedback
loop an agent elsewhere could act on. Most of the right designs are already written down in this
repository (`UNPORTABLE-DESIGN.md` §8, commit `1a9f517`'s "must ship as a dependency"); they have
simply not been executed.

State at the time of review: libGDX core emits 596 files with **0 compile errors**, 221/221 tests
discoverable (all MUnit), **217 passing** — the 4 failures are the deliberate `Json.fromJson`
substitution. `sbt test` green.

---

## Tier 1 — BLOCKS ADOPTION AT ALL

### 1.1 There are two engines, and each has half of what a consumer needs

**CONFIRMED** — `grep -rln "SpoonFrontend\|ScalaPrinter" corpus-tests/src/main/scala/balticporter/corpus/*.scala` → 10 files; the same for `SpoonTir\|TirEmitter` → 9.

| path | consumers | has |
|---|---|---|
| BIR (`core/Bir.scala`, `SpoonFrontend`, `ScalaPrinter`) | liqp, xwiki, jbump, flexmark | `ActionCache` + `EngineFingerprint` + `InterfaceHash` incremental keys, `CommentCheck`, determinism-by-double-translation, `SbtGen.emit` wiring, `PlatformLint`, `PackageRenamePass` |
| TIR (`tir/Tir.scala`, `SpoonTir`, `TirEmitter`) | libGDX core + tests | every CLAUDE.md §3/§4.4 lesson, all four checks, all six production transforms, MUnit conversion, portability |

`RECOMPILER.md` declares BIR the wrong substrate — but ssg's only two Java libraries live on it,
and the TIR path never received the BIR path's caching, provenance, determinism check, or
`SbtGen.emit` wiring. A consumer must choose a substrate and lose half the framework.

**Done when:** the BIR path's operational machinery (action cache, determinism check, `SbtGen`
wiring, provenance) runs on TIR, BIR is deleted or explicitly frozen, and every corpus program uses
one pipeline. Until then sge would adopt TIR and ssg BIR, and "the framework" is two frameworks.

### 1.2 The consumer API is "copy a 253-line file", by explicit instruction

**CONFIRMED** — `.claude/skills/add-corpus-library/SKILL.md` §2 says to write a migration object
"modelled on `LibgdxCoreMigrate`", in *this* repo's `corpus-tests`.

`LibgdxCoreMigrate.scala` is not policy — lines 175–250 inline **engine logic**: the dropped-type
emission skip, `runtimeSources` write-out, injection copying, CHECK 1 (dropped type still emitted,
line 203) and CHECK 2 (dangling substitution, lines 237–249). This is why `LibgdxTestMigrate` went
its whole life without calling `PortabilityCheck` — check invocation is copy-paste, not
orchestration. It is the `ReflectionToPortableTransform` mistake one level up: a (b) mechanism with
the policy inlined, per CLAUDE.md §1.

**Design.** One entry point in `runner` (which today holds only the BIR `M0Pipeline`):

```scala
PortRun(
  frontend: FrontendConfig,
  subs:     Substitutions,
  phases:   List[Phase],
  outMain:  Path,
  outTest:  Option[Path],
  project:  Option[SbtGen.ProjectSpec],
).execute()   // pipeline, ALL checks unconditionally, emission, injection, verification, report
```

Policy stays in the consumer's repo **as Scala code** — a manifest DSL is not needed and would be
worse; `Substitutions(...)` and transform constructors are typed and checkable. What must stop
being copyable is the mechanics and the checks. `UNPORTABLE-DESIGN.md` §8 already prescribes
lifting CHECK 1/2 into a core `SubstitutionCheck`.

**Done when:** a migration program is configuration only, no check can be forgotten, and adding a
library does not mean editing this repository.

### 1.3 Injected runtime must be a published artifact — a CORRECTNESS requirement, not hygiene

**CONFIRMED by design reasoning; not yet observed, because only one module has been ported.**

`CollectionsTransform.runtimeSources` writes `balticporter.runtime.JavaIterator` / `JavaIterable`
into **each port's `src_managed`**, and those types appear in the port's *public signatures* —
every ported `java.util.Iterator` parameter and return is now a `JavaIterator`. sge is core plus 17
interdependent extensions. Therefore:

1. When `sge-ai`'s port and core's port each emit their own copy at the same FQN, the Scala.js and
   Native linkers see duplicate definitions — a hard error on exactly the platforms sge exists for.
2. Where the JVM tolerates it, cross-module type compatibility holds only because the FQN collides:
   two ports pinned to different engine versions carry silently divergent bodies at one name.

**Design.** Publish `balticporter-runtime` (`JavaIterator`, `JavaIterable`, `Asserts`, and whatever
suite base `TestFrameworkTransform` retypes onto), version-locked to the engine.
`SbtGen.ProjectSpec.deps` gains it automatically when a phase that requires it has run. Keep source
emission only as an explicit `--vendored-runtime` fallback for zero-dependency ports.

**Corollary.** `TirEmitter`'s `externalConcrete` parameter (`TirEmitter.scala:22`) exists *only*
because the shims are unparseable text. With a real dependency its data could be derived. Today,
forgetting to pass it silently disables diamond-conflict detection — a footgun the orchestrator of
§1.2 should own rather than the caller.

Already concluded in commits `d52ab11` / `1a9f517`: "the helper is justified but must ship as a
dependency." Recorded, unimplemented.

### 1.4 Nothing is publishable, and a port cannot pin a known-good engine

**CONFIRMED** — `build.sbt` has `version := "0.1.0-SNAPSHOT"`, no `publishTo`, no CI publishing, no
versioning policy. `find testkit -name '*.scala' | wc -l` → **0**: `testkit` is a declared module
with no sources. `grep -c balticporter ../sge/build.sbt ../ssg/build.sbt` → **0** for both.

Module split is otherwise right (`core`, `frontend-spoon`, `scala-emit`, `sbt-gen`, `verify`,
`vocab`, `runner` publishable; `corpus-tests`, `libgdx-core` skipped). `EngineFingerprint` exists
but is consumed only by the BIR cache and the `SbtGen` header.

**Done when:** artifacts publish with a real version, a port declares an engine version, and the
ghost module is either filled or deleted.

### 1.5 Package renaming does not exist on TIR — blocking for BOTH repos

**CONFIRMED** — `PackageRename` appears only in `vocab/Vocabulary.scala`, `JbumpCorpus.scala`,
`VocabDemo.scala`; nothing on the TIR path.

sge is `package sge` (`../sge/sge/src/main/scala/sge/Gdx.scala` — "Ported from …
com/badlogic/gdx/Gdx.java"); ssg-liquid is `package ssg.liquid`. The TIR emitter derives packages
from `Symbol.fullName` and emits `com.badlogic.gdx.*` / `liqp.*`. Neither repo can adopt output in
the upstream namespace — their entire dependent codebases use the renamed one.

**Design.** A clean CLAUDE.md §1(b): `PackageRenameTransform(Map[String, String])` rewriting
`Symbol.fullName` prefixes; empty map = no-op. `JbumpCorpus` already validated
`com.dongbat.jbump → sge.jbump` against the hand port on BIR, so the policy shape is proven.

### 1.6 `TirEmitter` lost provenance headers — a licence problem, not cosmetics

**CONFIRMED** — `grep -c Provenance` → `TirEmitter.scala` **0**, `ScalaPrinter.scala` **2**.

`TirEmitter.emitUnit` (lines 42–47) emits `package …` plus the body and nothing else. The BIR
`ScalaPrinter.print` takes a `Provenance`; the hand ports carry attribution headers (ssg-liquid
files open with copyright and "Ported from" blocks, sge likewise). **libGDX is Apache-2.0** —
shipping a derived port without attribution notices is a compliance gap. The TIR path regressed a
feature the BIR path had solved.

**Done when:** `TirEmitter` takes a `Provenance` and emits the same header, plus the do-not-edit
banner `SbtGen` already writes.

---

## Tier 2 — BLOCKS *AGENT-MAINTAINED* ADOPTION

### 2.1 The canonical measure script throws the four checks away

**CONFIRMED** — `scripts/gdx_measure.sh:14` is `grep -E "wrote" <<<"$MIGRATE_OUT" | head -1`.
`gdx_test_measure.sh:17` keeps only summary lines.

Signature consistency, `OmissionCheck`, `PortabilityCheck` and the substitution results are all
computed and then filtered out of the one command CLAUDE.md §5 tells everyone to run. CLAUDE.md's
claim that "the migration prints four independent checks on every run" is true of the migration and
false of the workflow. (Observed during this session: getting the omission list required running
`runMain` directly.)

**This is the cheapest high-value fix in the document** — it is actively hiding working diagnostics.

### 2.2 Check results are stdout-only, truncated, never persisted, never diffed

**CONFIRMED** — `take(20)` / `take(14)` truncation in the render paths; no baseline file anywhere.

There is no way to answer "did my change move omissions from 31 to 33" except scrollback
archaeology. This is `UNPORTABLE-DESIGN.md` Stage 1(c)/(d), unbuilt.

### 2.3 There is no TIR pretty-printer, and no way to run, skip, or dump a single phase

**CONFIRMED** — `DebugEmit.scala` is BIR-only and hardcodes liqp's source root. "See the TIR before
and after a phase" is impossible today short of case-class `toString`.

`Pipeline.run` is a five-line fold over **named** phases, so `-Dbalticporter.skipPhases=` and
`-Dbalticporter.dumpTirAfter=` are each an afternoon's work.

### 2.4 `UNPORTABLE-DESIGN.md` — still the right skeleton, with one forced amendment

The 507-line design (unportable markers, failure report, best-effort emission, semantic diff)
remains correct, but the weights have shifted:

- **Stage 1 is now the single highest-value debugging investment** — member digests, a
  `srcmap.tsv` (member → emitted lines → Java `Origin`), error correlation, baseline
  classification. It needs no TIR surgery, and its value multiplies when the maintainers are agents
  in other repositories with no session context.
- **AMENDMENT (forced by CLAUDE.md §4.4).** The design's §6.3 correlation triages *scalac errors* —
  and §4.4's entire point is a defect class with **zero scalac errors**. The correlation lane must
  extend to the behavioural gate: map *test failures* through `srcmap.tsv` to members and Java
  origins, and diff pass/fail sets between runs the way §5.3 diffs findings. The design predates
  the port having a runnable suite; now that 217/221 run, this is buildable and is the only lane
  that catches §4.4-shaped regressions.
- **Trim Stage 2.** The `MarkState` machine with per-phase multiset invariants (§2.1) is the
  heaviest part and defends against a failure mode — a phase erasing a marker — that the simpler
  counted-finding-plus-gate already makes loud. Ship `Approx` + the emission gate + fences; defer
  conservation until a phase actually eats a marker.
- **Keep best-effort emission unchanged.** The observation that `gdx_measure.sh` "has been running
  in unlabelled best-effort mode all along" is correct, and that is the mode a new library lives in
  for weeks.

### 2.5 Three ad-hoc debugging techniques should become first-class

Recorded because this session needed all three, and CLAUDE.md §4.6 only writes down the folklore:

| technique used | should be |
|---|---|
| edit a function to return early, gate on a marker file | `-Dbalticporter.skipPhases=<phase>` in `Pipeline.run` — answers "is this phase even responsible" in one run with no source edit, and works through `sbt -client` because it is a property on the forked run, not an environment variable |
| tracer added to all 16 `Tree.Typed` construction sites | construction provenance in a debug mode: finding ids on emitter special forms plus `-Dbalticporter.traceNode=Typed`. Do NOT add a field to every node for this |
| copy `src_managed`, flip a local `debug` flag, recompile | a TIR `DebugEmit`: model once, emit one FQN to stdout, optionally after each phase |

---

## Tier 3 — UNPLEASANT, NOT IMPOSSIBLE (each becomes blocking at a known moment)

### 3.1 Cross-port composition — becomes blocking at sge's SECOND module

An extension port (gdx-ai, ashley, vis-ui…) references core types whose *emitted* signatures the
transforms changed: collections retyping, dropped members, runtime shim types, renamed packages
(§1.5). The extension's frontend can only parse **Java** — it resolves against `gdx/src` via
`resolutionRoots` — so to agree with the core port it must re-run an identical pipeline
configuration over the shared surface. Today that agreement is copy-paste and hope.

**Design.** Make the migration policy a first-class value (`PortManifest`) that a dependent port
imports and extends, plus a check that a resolution-root type tagged `Substituted` in port A is
identically tagged in port B. Without it, sge's 17 modules drift one at a time.

### 3.2 Test-framework coverage is JUnit-4-shaped — becomes blocking at liqp's test port

Handled: `@Test`, `@Test(expected=)`, `@Before`, nine `Assert` members.
**CONFIRMED absent** (`grep -c "AfterAnn\|@After\|Ignore\|assertThat"` → 0):

- **`@After`** — a `tearDown` stays an ordinary never-called method. The *same silent shape* as the
  `@Before` defect the transform's own comment at lines 110–118 documents, but on the release side:
  tests pass and leak state.
- **`@Ignore`** — a disabled test would be *enabled*.
- **Hamcrest `assertThat`** — liqp's suite depends on `hamcrest-all` (`LiqpProject.scala:159`).
- `@BeforeClass` / `@AfterClass` / `@Rule`; JUnit 5 and TestNG (these degrade semi-loudly via the
  `org.junit.` portability rule); `@RunWith(Parameterized)` (known, count 1).

Also: the *target* side is only nominally parameterised. `suite` and `testMember` are constructor
parameters, but `intercept` and the curried `test(name){body}` application shape are MUnit facts
baked into the phase. A utest target would not work by changing the parameter. Honest label:
**(b) with exactly one implemented policy value.**

### 3.3 Smaller items

- **Incremental TIR runs** — whole-model Spoon build and full re-emit every run. Fine at 596 files;
  flexmark is far larger. The BIR `ActionCache` design (source digest + dep interface hash + engine
  fingerprint) is right and should move with §1.1.
- **Unmatched-policy-key reporting** — a typo'd `dropTypes`/`dropMethods` key silently no-ops
  (`SpoonTir.scala:803` consults the set; nothing reports keys that never matched). CHECK 2 catches
  a drop that *fired* and dangles; nothing catches a drop that never fired.
  `StaticForwarderTransform` and `ClassTableTransform` have the same gap. One-line fix in the
  orchestrator of §1.2, symmetric to "declared substitution not carried out".
- **`CollectionsTransform`'s `typeMap`** should become a defaulted (b) parameter. The mappings are
  Java/Scala facts, but "retype to scala collections *at all*" is a per-port decision — liqp's JVM
  gate deliberately keeps `java.util`. Off = omit the phase, which suffices today.

---

## Incidental engine defect found during the review

`MutableParamsTransform.reassignedIn` / `subterms` (`MutableParamsTransform.scala:85–117`) is a
hand-rolled recursion of exactly the kind CLAUDE.md §3 bans. `subterms` returns `Nil` for
`Tree.New` and has no `Lambda`, `NewArray`-init or `Repeated` cases. Java's effectively-final rule
shields the lambda case, and a missed `p++` inside `new int[]{ p++ }` degrades loudly (the emitted
`val` reassignment fails to compile), so this is **safe-but-fragile rather than wrong**. Convert to
a `StandardTraversal` scan when next touched.

---

## What the audit found SOUND (clean verdicts, not courtesy)

- `Substitutions(dropTypes, dropMethods, inject)` — right seam; the overload-precise
  `owner#m(P1,P2)` keys are well designed. Its deficiencies are feedback and orchestration, not the
  seam.
- `ClassTableTransform(Map)` — correct (b). Searched for smuggled libGDX knowledge; found none.
- `StaticForwarderTransform(List[Forwarder])` — correct (b), with one latent edge: members are
  matched by **name only** (`StaticForwarderTransform.scala:35`), so a wrapper whose overloads are
  not all receiver-first would be rewritten wrongly. Safe under current policy; worth a guard when
  a second library configures it.
- `corpus-tests/libgdx-overrides/**` — correct (c) content in the right place, engine-side scanned
  by `PortabilityCheck.inInjectedSource`. This is the model for what a *consumer repo's* `src/`
  holds.
- `IntToOpaqueTransform` — CLAUDE.md's canonical (c) *policy* carried by a shareable (b)
  *mechanism*; correctly placed. Its "agent-in-the-loop `extraHints`" doc comment is the best
  existing model of the intended workflow.
- `RewriteTrace`'s impact/check pair — blast radius *before* a rewrite; the auditor judged this
  unique to this codebase.
- The stale-emit abort in both measure scripts.
- `Phase` / `Pipeline` / `StandardTraversal` — the best-documented surface in the repo, and
  something an agent could genuinely write against **once an example exists** (see below).
- The `JavaIterator` / `JavaIterable` shim *design*. Its distribution is the problem (§1.3), not
  its shape.

---

## Q4 traced: what an agent in sge would actually experience

Scenario — an agent points the engine at `gdx-ai`, never ported.

1. **There is no runner to point.** Step zero is copying `LibgdxCoreMigrate.scala` into… somewhere.
   Neither sge nor ssg depends on balticporter (§1.4).
2. `buildModel(lenient = true)` silently shadow-resolves anything it cannot see.
3. The four checks print — *if* the copied file calls them (§1.2, and see `LibgdxTestMigrate`).
4. scala-cli produces a wall of errors in emitted `.scala` files.

From there:

- **Errors are not attributable.** No source map, no provenance comments, `Origin` surfaced
  nowhere in emission. The agent must open the emitted file and mentally reverse the emitter to
  find the Java — every error, every iteration.
- **Nothing distinguishes (a) / (b) / (c).** The checks classify *their own* domains well: a
  `PortabilityCheck` hit with its `why` string points at configuring `Substitutions`; a
  `RewriteTrace` orphaned call names the exact dropped member. But **typer errors — the bulk of a
  new library's wall — arrive with no signal at all.**
- **The knowledge that would help is filed in the wrong place.** `LIBGDX-PORT-STATUS.md`'s
  do-not-retry table is in this repo, libGDX-flavoured, loaded by nothing in sge. CLAUDE.md §3.6's
  own rule is currently violated by its most valuable lessons: the *engine-limit* entries
  (raw-anon refusal, `given Conversion` never firing, wildcard round-tripping) are engine-scoped
  but per-library-filed.
- **Writing a phase**: the `Phase` trait is discoverable and well documented — but there is **no
  worked example of a rule plugged in from OUTSIDE the engine**. Every phase, including the
  canonical (c) `IntToOpaqueTransform`, lives in `core/transform`. A consumer agent has no
  precedent for where its file goes, how it enters the pipeline, or how it is tested. The first
  external phase will be written by pattern-matching on engine internals.

**The three cheapest changes that would flip this answer**, in order:

1. **`UNPORTABLE-DESIGN.md` Stage 1** — `srcmap.tsv` + error correlation splits every scalac error
   into "at a site the engine knew was approximate" versus "engine gap, auto-located to member and
   Java origin". *That is the (a)-vs-(b/c) discriminator, mechanized.*
2. **`Remediator`-style snippets on the existing checks** — a portability finding whose sites all
   route through one wrapper should print the ready-made `Forwarder(...)` line. The design's §4.2
   example is exactly right and is computable from `Xref` today.
3. **Move engine-limit do-not-retry entries into an engine-owned home** (this file, CLAUDE.md, or
   the skill), so an agent in another repo inherits the measured dead ends instead of re-deriving
   them.

---

## Suggested goal ordering

A defensible sequence, each step independently measurable:

1. `gdx_measure.sh` surfaces the four checks (§2.1) — minutes, and unblocks every later diagnosis.
2. `PortRun` entry point owning checks + emission; lift CHECK 1/2 into `SubstitutionCheck` (§1.2).
3. `PackageRenameTransform` on TIR (§1.5) and `Provenance` headers in `TirEmitter` (§1.6) — both
   small, both hard blockers.
4. `balticporter-runtime` published; `SbtGen` adds the dependency (§1.3, §1.4).
5. Port the BIR operational machinery onto TIR; freeze or delete BIR (§1.1).
6. `UNPORTABLE-DESIGN.md` Stage 1 **with the test-failure correlation amendment** (§2.4).
7. Phase skip/dump flags and a TIR printer (§2.3, §2.5).
8. `PortManifest` cross-port composition (§3.1), before sge's second module.
9. `@After` / `@Ignore` / Hamcrest (§3.2), before liqp's tests move to TIR.

Steps 1–4 are what make adoption *possible*. Steps 6–7 are what make it *maintainable by agents*,
which is the actual goal.
