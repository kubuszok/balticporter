# Baltic Porter as a LIBRARY — what sge and ssg need before they can depend on it

Source: adversarial review by the `porting-auditor` (Fable 5), 2026-07-29, at commit `8fea564`.
Every claim marked CONFIRMED below was independently re-verified against the working tree after
the review; the commands are given so they can be re-run.

> ## IMPLEMENTED, 2026-07-29 — read this before the body
>
> **Every item below has been built.** The body is preserved as the ANALYSIS — it is why each
> thing was done, and it is still the best statement of the problems. Individual items carry a
> `STATUS` block where the answer diverged from the design. **Two of them say NOT done for part of
> their scope; those are the honest residue and are listed here, not buried.**
>
> Measured on the merged tree, through `scripts/gdx_measure.sh` and `scripts/gdx_test_measure.sh`:
>
> | gate | at review | now |
> |---|---|---|
> | emitted files / dropped / injected | 596 / 11 / 6 | 596 / 11 / 6 |
> | main compile errors | 0 | **0** |
> | ported tests | 217 of 221 | **217 of 221**, the 4 now classified `expected#derived` |
> | engine tests | 33 | **267**, 0 failures |
> | determinism on TIR | did not exist | **605 units emitted twice, byte-identical** |
> | manifest agreement | did not exist | **605 shared types, 0 disagreements** |
> | srcmap | did not exist | **19 528 members over 605 units**, 0 unlocatable |
> | emitted text vs baseline | did not exist | **0 members changed** |
>
> ### What is NOT done, stated plainly
>
> - **§1.1's "every corpus program uses one pipeline."** Only the two libGDX programs are on TIR.
>   Ten others — liqp, xwiki, flexmark, jbump, i.e. ssg's actual Java libraries — are still on the
>   BIR path, which is now **explicitly frozen** (headers on `Bir.scala` / `SpoonFrontend.scala` /
>   `ScalaPrinter.scala` naming its ten dependents) but not deleted. Moving them means re-porting
>   three libraries, each with its own measurement. **The framework is one framework by declaration
>   and two by deployment.**
> - **§1.2's "adding a library does not mean editing this repository."** What this repository could
>   close is closed: nothing mechanical remains to copy, and no check can be forgotten. Proving the
>   rest needs the published artifacts actually consumed *from* sge or ssg, which cannot be
>   demonstrated from here.
> - **No end-to-end proof that a generated port resolves the published runtime.** `SbtGen` writes
>   the right dependency line; nothing has resolved it. That wants an sbt scripted test.
> - **`ManifestAgreement` cannot see a parameterised phase's CONFIGURATION** unless the phase
>   declares a fingerprint. `ClassTableTransform` and `StaticForwarderTransform` opt in;
>   `CollectionsTransform` cannot until its `typeMap` becomes a parameter (§3.3), so a divergent
>   collection *mapping* — as opposed to a missing phase — is still invisible.
> - **Nothing verifies two ports were built by the same ENGINE.** `EnginePin` exists and is not
>   wired into `ManifestAgreement`.
> - **Stage 2 of `UNPORTABLE-DESIGN.md` is deliberately unbuilt** (§2.4 says to trim it). The
>   correlation lane already accepts a marker set and an empty one is a tested, legal input; Stage 2
>   only has to WRITE `markers.tsv`.
>
> ### Two things the audit itself got wrong, corrected during implementation
>
> - **§1.3 cited the superseded position on `Asserts`** — see the CORRECTION in that item. The
>   scaffold was deleted rather than published, and 880 assertion sites now map straight onto MUnit.
> - **§3.2 claimed JUnit 5 and TestNG "degrade semi-loudly."** JUnit 5 does, but only via the *term*
>   reference from an assertion call — annotation types are not in the xref at all. **TestNG matches
>   no rule whatsoever.** A test now pins that so it fails the day a rule is added.
>
> ### Found while implementing, not in the audit
>
> - `PortabilityCheck`'s nine `exactMember` rules **had never fired** — see the section below. Fixed;
>   `portability(all)` 139→151 (core) and 148→166 (test), every new finding inside an already-dropped
>   type, zero false positives.
> - The rule list had the *plural* `getDeclaredFields` but not the singular `getDeclaredField` /
>   `getMethod` / `getField`. Four sites had produced **no finding at all**.
> - `MutableParamsTransform`'s hand-rolled recursion missed **seven** ordinary Java forms, not the
>   three the audit listed — including `int c = p++;`, because `Block.stats` was filtered to
>   `case x: Term` and a `ValDef` is a `Definition`, so **local initialisers were never scanned**.
> - The libGDX manifest declares `getName` on `ClassReflection`, which has no such member. A silent
>   no-op since it was written; `PolicyReport` reports it the first time it was ever called.
> - `EngineInfo.version` said `0.1.0-M0` while the build published `0.1.0-SNAPSHOT`, and the version
>   is baked into emitted headers.
> - An engine test was **passing for the wrong reason**: `SpoonTir.fromSource` builds with
>   `noClasspath`, so a one-file snippet's `import static` resolved to `this.assertEquals(…)` and the
>   rewrite never fired — the assertion was checking *unrewritten* output.
> - Bare `sbt test` maps to `testQuick` in this build and silently reports "No tests to run". **A
>   green `sbt test` has never been a gate here.** Use `testOnly *`.

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

> **STATUS — mostly done.** All four moved onto TIR inside `runner/PortRun`: the action cache
> (`core/Cache.scala`'s `TirCacheKey` — engine fingerprint + the unit's canonical digest + its
> dependencies' interface hashes, with the same early cutoff BIR had), determinism by
> double-translation (`Determinism.Emission` on every run, `Full` behind `--determinism=full`;
> proven on libGDX core at 605 units), `SbtGen.emitPort` wiring, and `Provenance`. BIR is
> **explicitly frozen**, not deleted: a header on `Bir.scala` / `SpoonFrontend.scala` /
> `ScalaPrinter.scala` says so, says why, and names the ten corpus programs that still depend on
> it (liqp, xwiki/flexmark, jbump — ssg's Java libraries).
>
> **NOT done: "every corpus program uses one pipeline."** Only the two libGDX programs are on TIR;
> the other ten are still BIR, and moving them means re-porting three libraries, each with its own
> measurement. Until that happens ssg's libraries are still on the frozen path — the framework is
> one framework by declaration, and two by deployment.

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

> **STATUS — done, and the sketch above was short of six things.** `runner/PortRun.scala`. The real
> signature adds `label`, `portRoot` + `sourceSet` (the output paths are DERIVED from
> `SbtGen.managedDir`, never passed — §5.5), `provenance` (§4.57 makes it a licence obligation),
> `packageRenames` as DATA rather than a phase (§4.56: it must run after every other phase, which
> `runsAfter` cannot state — so `PortRun` appends it last and *refuses* a `PackageRenameTransform`
> passed in `phases`), `runtimeMode`, `supportSources`, `determinism` and an optional action-cache
> directory. `externalConcrete` is gone from the caller's hands entirely: `RuntimePlan.of(phases,
> mode)` derives it.
>
> "No check can be forgotten" is enforced, not asserted: `PortRun.RequiredChecks` is compared
> against what actually registered with `CheckReport`, so a number that reaches stdout and not
> `findings.tsv` fails the run. CHECK 1/2 are lifted to `core/SubstitutionCheck.scala` and are
> recorded like the rest. `PolicyReport.collect` is finally called — on libGDX it immediately
> reported one real §1(b) finding that had been invisible.
>
> `LibgdxCoreMigrate` is 253 → 195 lines of which the run is 20; `LibgdxTestMigrate` is 97 → 72 and
> now runs every check, including the `PortabilityCheck` it never called. The skill (§2, §2.1) tells
> a new port to write a `PortRun` configuration and points at `GdxSharedIteratorRule` — a §1(c) rule
> that lives OUTSIDE the engine — as the model for a library-specific phase.
>
> **NOT done: "adding a library does not mean editing this repository."** That needs the published
> artifacts of §1.3/§1.4 and a consumer repository to try it from. What is closed is the part this
> repository can close: nothing mechanical remains to copy.

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

**Design.** Publish `balticporter-runtime` (`JavaIterator`, `JavaIterable`), version-locked to the
engine. `SbtGen.ProjectSpec.deps` gains it automatically when a phase that requires it has run. Keep
source emission only as an explicit `--vendored-runtime` fallback for zero-dependency ports.

**CORRECTION (2026-07-29) — `Asserts` and the suite base do NOT belong in this artifact.** An
earlier version of this section listed them, citing "the helper is justified but must ship as a
dependency." That sentence is from commit `4e4a42f`, which commit `d52ab11` explicitly corrects, and
`1a9f517` is about raw-generic rendering, not this. `LIBGDX-PORT-STATUS.md` carries the three
sections in *reverse* order of authority — the one headed "Superseded: the argument that `Asserts`
should SHIP as a dependency" is the superseded one — which is how the misattribution happened.

The current verdict stands at the section headed "CORRECTION: `Asserts` is NOT justified either":
the 33 errors that appeared to justify the helper are 26 mixed-numeric comparisons the engine can
widen from its own static types, 6 Java `static` helpers wrongly emitted into the companion object
where MUnit's instance members are invisible, and 1 unrelated. Every shape was probed against MUnit
1.0.2 and compiles. So `Asserts` is **shape adaptation**, and the criterion is:

- **semantics the target lacks → a runtime the port DEPENDS ON** (`JavaIterator`, `JavaIterable` —
  a removal-capable iterator is genuinely absent from Scala; this is what the artifact is for);
- **shapes the engine can emit correctly → the engine emits them, and nothing ships** (`Asserts`,
  `PortedSuite` — eliminated, not published).

Eliminating `Asserts` needs a curried-application node in the TIR (its absence is the whole reason
`testCase(name, body)` exists), operand widening in the transform, and static test helpers emitted
as suite members. That is tracked separately; it does not change this item, which is about the two
collection shims.

**Corollary.** `TirEmitter`'s `externalConcrete` parameter (`TirEmitter.scala:22`) exists *only*
because the shims are unparseable text. With a real dependency its data could be derived. Today,
forgetting to pass it silently disables diamond-conflict detection — a footgun the orchestrator of
§1.2 should own rather than the caller.

The distribution rule itself — semantics the target lacks become a dependency, never copied source —
was already concluded and recorded; it had simply not been implemented.

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

> **STATUS — done.** `core/PortManifest.scala` (the value) and `core/ManifestAgreement.scala` (the
> check); `PortRun` gains `manifest`, and `manifest` joins `PortRun.RequiredChecks`.
>
> **The value.** `PortManifest(name, governs, dropTypes, dropMethods, packageRenames, surface,
> inject, bases, inherit)`, composed with `base.extendedBy(dependent)` — ordinary Scala the
> consumer's compiler type-checks, not a DSL. `PortRun` takes it INSTEAD of `phases`/`subs`/
> `packageRenames` and refuses both at once, so a run never holds two policies.
>
> **Where the MUST-agree / MAY-differ line is drawn.** Everything on the manifest changes the SHAPE
> of the shared surface as a dependent compiles against it; everything a `PortRun` takes that is not
> on it is a property of this module's build (`sourceSet`, `frontend`, `provenance`, `runtimeMode`,
> `supportSources`, `project`, `determinism`, `cache`). The one deliberate split is inside
> `Substitutions`: **`dropTypes`/`dropMethods` are inherited and `inject` is not.** A drop is an
> observation about the shared API and binds every module that sees the type; an injection is a
> build artefact, and exactly one module must ship each replacement file — a dependent that copied
> `inject` would emit a second definition of the same FQN. `SubstitutionCheck`'s CHECK 2 follows the
> same line: it holds a module to `ownDrops` (its own, minus its bases'), because "dropped,
> unreplaced, still referenced" is a question about the module that ships the replacement.
>
> **The check, in two layers.** STATIC compares declaration against declaration
> (`MissingDrop`, `ExtraDrop`, `RenameDivergence`, `RenameOverride`, `SurfaceMissing`,
> `SurfaceDivergence`, `NoBaseDeclared`). DYNAMIC compares the base's policy against what the run
> actually MODELLED of the shared surface — for every unit resolved against and not converted, is it
> tagged `Substituted` exactly when the base drops it (`TagMissing`/`TagUnexpected`), and does it
> carry the name the base gives it (`SurfaceNameDivergence`)? Shared types are identified by unit
> ORIGIN, not by package prefix, because a library's own test suite declares its suites in the very
> packages it tests. Every finding ends in its §1 classification; the fatal ones abort the run.
>
> **Measured on the real pair.** `LibgdxCoreMigrate` is now a base manifest and `LibgdxTestMigrate`
> a dependent of it — 605 shared types, 0 disagreements. Every number is unchanged (596/11/6, 605
> units/52421 symbols, 46 omissions, 67 portability(emitted), 139 portability(all), 2 injected, 0
> signature, 1 policy; 29 test files, 634 units, 49 omissions, 0 signature, 0 policy; 0 compile
> errors, 217 passing / 4 failing) with ONE exception, explained below. Perturbing the dependent's
> manifest three ways, each caught and fatal:
>
> | perturbation | findings |
> |---|---|
> | dependent renames `com.badlogic.gdx -> sge`, base does not | `RenameOverride` |
> | dependent omits the base's `dropTypes` entry for `utils.Json` | `MissingDrop` + `InheritedKeyNeverFired` + **`TagMissing`** on the resolution-root type |
> | dependent omits `CollectionsTransform` from its surface | `SurfaceMissing: java-collections->scala` |
>
> **The one number that moved: `libgdx-test` `portability(emitted)` 148 → 76.** Not a regression and
> not an adjustment. The test port previously declared NO substitutions, so
> `PortabilityCheck.inEmittedCode`'s dropped-type filter had nothing to filter and the number
> counted violations inside `Json`, `Pools`, `NetJavaImpl` and the seven `reflect` types — code
> neither port emits. Inheriting the base's manifest is what made the two runs agree about which
> types those are. `portability(all)` is unchanged at 148, which is what proves nothing else moved.
> Both numbers still count violations in resolution-root units the run does not emit; scoping
> `inEmittedCode` to converted units would shrink it further and is a separate change.
>
> **What the check CANNOT see** — stated because a composition check that silently misses a class of
> drift is worse than none:
>
> 1. a parameterised phase's CONFIGURATION unless it implements `SurfacePolicy`. Comparison is by
>    `Phase.name` plus, for an opted-in phase, its policy digest; `ClassTableTransform` and
>    `StaticForwarderTransform` opt in, `CollectionsTransform` does not (its `typeMap` is not a
>    parameter yet — §3.3), so two differently-configured instances of it compare EQUAL. The
>    alternative is reflection over private fields, which compares things that are not policy.
> 2. nested-type drops — the dynamic layer walks top-level units, so those are covered only by the
>    never-fired tally.
> 3. a phase whose output differs for reasons outside its declared policy.
> 4. **the base's emitted OUTPUT.** This compares policy and the dependent's model of the shared
>    surface; it never reads the Scala the base wrote, so a base built by a different engine version
>    is invisible here. That is `EnginePin`'s job and it is not wired to this.
> 5. `MissingDrop` and `SurfaceMissing` are unreachable under `extendedBy`, because inheritance
>    makes that drift unrepresentable — which is the point, but it also means those two branches only
>    fire for a manifest declared with `mirroring` (policy stated in full, base checked rather than
>    inherited). `mirroring` exists so the branches are reachable at all; a check that cannot fire is
>    not a check.
>
> **NOT done:** nothing verifies that two ports were built by the same ENGINE (point 4), and no
> second libGDX MODULE has been ported — the evidence is `gdx/test` as a dependent of `gdx/src`,
> which is a real two-port pair with a 605-type shared surface but not a real extension library.

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

## Found while IMPLEMENTING this document (2026-07-29)

### `PortabilityCheck`'s nine `exactMember` rules have never fired — a check reporting zero because it is blind

Confirmed twice, independently. `Minter.external` (`SpoonTir.scala:94`) mints every external symbol
with `owner = SymId.None` and `fullName` set to the *interning key*, so an external member comes out
as `fullName=@8#forName(java.lang.String), owner=-1`. `PortabilityCheck.scala:72` then computes
`memberName = program.symbolOf(sym.owner).map(o => s"${o.fullName}#${sym.name}")`, which is `None`
for every one of them — and the `exactMember` branch tests `None.contains(r.api)`, always false. The
prefix branch cannot match either, since the `fullName` is `@8#…`.

So `Class#forName`, `Class#newInstance`, `System#getProperty` and the six reflective
`getDeclared*`/`get*` rules — the exact APIs the check names as its reason for existing — have never
been detected. The comment two lines above the bug shows the author intended `owner#name` to carry
the meaning; it silently never did.

This is CLAUDE.md §3 verbatim: *"a check reporting zero is only as good as its coverage."* Fixing it
means giving external member symbols their owner id in the frontend. **Expect the portability count
to RISE** — that is the gate beginning to tell the truth, not a regression (§3 again).

**RESOLVED (2026-07-29).** `Minter.external` takes the owner for members; core **139 → 147**, test
port **148 → 156**, and nothing else moved (596 files, 46/49 omissions, 0 signature, 0 compile
errors, 217 pass / 4 fail). All eight newly-visible findings are inside
`utils/reflect/ClassReflection.java`, which the libGDX manifest already drops — so
`portability(emitted)` for the core port is unchanged at 67 and no manifest change was needed. The
engine-scoped rule is `ENGINE-LIMITS.md` P4, including the two OTHER mechanisms that key on the same
string and were equally blind.

### `Remediator`-style snippets — item 2 of the three cheapest changes, DONE (2026-07-29)

`core/.../tir/Remediator.scala`, fed by `PortabilityCheck.inEmittedCode`, printed by its `summary`
and persisted as the `remediation` check. Three templates whose preconditions are verified against
the program — chokepoint → `Substitutions(dropTypes = …)`, forwarding wrapper →
`StaticForwarderTransform.Forwarder(…)`, name lookup → `ClassTableTransform(Map(…))` — and an
observation fallback that measures the distribution and proposes nothing. On libGDX core it emits 8
paste-ready drop lines and 6 observations, covering every one of the 24 unportable APIs exactly once.
Two of the three templates were **impossible before the owner fix above**: recognising a forwarder
requires knowing which external MEMBER a call reaches, not merely which external type.

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
2. ~~**`Remediator`-style snippets on the existing checks**~~ — DONE, see above. It was computable
   from `Xref`, but only after external members carried their owner: "routes through one wrapper"
   is a claim about a MEMBER, and the frontend had made every external member anonymous.
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
