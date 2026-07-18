# Current goal state

Machine-updated by `/goal` iterations. One phase at a time; a phase advances
only when its gate (PLAN.md §13) is green with re-runnable evidence.

## Phase: M3 — tests

Gate (PLAN.md §13): Liqp's upstream test suite ported through the engine and
green on JVM; failures triaged into the ledger with reasons or fixed by rules.

Status: IN PROGRESS

Checklist:
- [x] Survey (2026-07-18): 105 JUnit4 files, 169 Hamcrest + 149 junit imports,
      ZERO Parameterized runners, 5 junit.framework (JUnit3-style), no test
      resources. Strategy decision: port tests onto JUnit4/Hamcrest themselves
      for the JVM gate (they're plain JVM libs, sbt junit-interface runs
      them); munit conversion deferred to the cross-platform phase.
- [x] Test translation pipeline (commit 2659441): annotation preservation
      (BAnnotation, @Test(expected=...) etc.), local classes,
      assignment/inc-dec as expressions, array-write exprs; sbt-gen test
      deps + src/test/scala. **104/105 translate** (1 inner-class holdout in
      the ANTLR parser test — ledger candidate).
- [x] Test/compile GREEN (2026-07-18, commit 27cdbfd): 100→0 errors (literal
      slice validation, local-class fixes, String.valueOf concat, generic-
      return + return-into-T casts)
- [x] Suite RUNS: 102 classes / 625 tests execute serially — **553 pass
      (88.5%)** on the first behavioral gate. Fixtures copied; parallel
      execution disabled (shared registries). NOTE: flaky sbt-client output
      faked partial runs earlier — always confirm with junit-interface -v.
- [x] Triage wave 1 (commit 08526fa): 72 → 35. Include cluster = missing
      fixture roots (_includes etc., environment). Array cluster = REAL
      ENGINE BUG: cast-blind call typing — ((Object[]) x) into varargs must
      spread; typedArg now honors outermost casts. First bug class invisible
      to compile+skeleton gates, caught only behaviorally.
- [x] Triage wave 2 (commit 2caafbe): 35 → 16. Two more runtime-shape rules:
      @BeanProperty on public fields (Java reflection visibility — 15 tests)
      and varargs-into-Object materialization (Java varargs ARE Object[] at
      runtime — 4 tests). Both invisible to every static gate.
- [ ] Triage wave 3 (16 left): strftime/date-format cluster (~8: epoch
      ms-vs-s ×1000 suspect, pattern application '31 Dec,' → 'Thu Dec 31',
      char-parse ','), java.util.Date yes/no compare flips (4), blank
      asserts (3), birthday ISO date (1)
- [ ] GATE: suite green on JVM with ledgered exceptions

Deferred from M2 scoping (needed by M5, not gate-blocking): declarative Tier-2
vocabulary engine, Tier-3 rule API productization (currently: passes are
engine-internal + corpus-program config).

## Completed phases

### M2 — build emission + whole-corpus compile — DONE (2026-07-18)

Gate: `translate && emit-build` on Liqp yields an sbt 2.0 project where
`sbt Test/compile` passes on JVM with zero manual edits; corpus convergence
≥97% classified; divergences documented.

Evidence:
- `sbt "corpus-tests/runMain balticporter.corpus.LiqpProject"` then
  `cd out/liqp-project && sbt compile` → **exit=0** (134/134 files: 133
  machine-translated + 1 documented whole-file override). Zero manual edits
  of generated output. Compile-error burn: 135→56→38→15→0 (commits
  420fb84, 47d72df, 0d3eac4, b42249c).
- Corpus convergence 116/117 = 99.1% classified (EQUAL=73 ACCEPTED=17
  IDIOM=13 HAND_ADDITIONS=10 SUBSTITUTED=3); divergence ledger
  fingerprint-pinned (staleness detection fired and was re-verified during
  this phase — the mechanism works).
- Comment invariant clean over all 134; M0 gate GREEN.

Checklist:
- [ ] Vocabulary engine (Tier 2, PLAN.md §6): declarative symbol/call-shape
      mapping tables (Java stdlib policy first: keep java.* on JVM); loader +
      table format
- [ ] Tier 3 project-rule API: BIR passes + Scalameta post-passes registered
      per plan (first rules: ssg header template, package rename liqp→<target>)
- [x] sbt-gen + whole-corpus assembly (2026-07-18, commit 420f b84): LiqpProject
      emits all 134 upstream files (done + ssg-skipped packages) + generated
      build.sbt + ANTLR parser jar; whole-file override mechanism (header-
      checked) with LiquidException as first override. New rules en route:
      identity-basis ctor funnel (+uses-this purity guard), static/instance
      init blocks (comment invariant caught silent drops!), i++-aware
      mutability, boxed-name collisions, raw-type wildcard fill.
- [x] Whole-corpus scalac gate GREEN (2026-07-18, commit b42249c):
      135 → 56 → 38 → 15 → 0. `sbt compile` exit=0 on out/liqp-project —
      134/134 files (133 machine-translated + LiquidException override).
      Final rules: static-inheritance owner resolution (RESEARCH trap 7 caught
      live), param/local-vs-member collision renames, generic class literals
      → Class[AnyRef] + unchecked result casts, nested-private widening,
      precise-receiver method refs, this.-qualified own-field reads.
      Corpus convergence re-verified 116/117 (TemplateContext fingerprint
      staleness fired as designed → re-verified, re-pinned). M0 GREEN.
- [x] Unsupported tail 10→4 (2026-07-18, commit fd0c584): SentinelRegistry
      (cross-unit fixpoint; super() ≡ this(null) under sentinel-merged parents
      — Tag/Block chains), multi-statement lambdas, unbound method refs as
      typed lambdas, mixed break+continue via named boundary Label. Plus 2
      verified ledger entries (For: Stack→ArrayDeque; Insertions naming).
      Corpus: 113/117 (96.6%) classified — EQUAL=73 ACCEPTED=15 IDIOM=13
      HAND_ADDITIONS=9 SUBSTITUTED=3; UNSUPPORTED=4. M0 GREEN.
- [x] Final ctor shapes (2026-07-18, commit 74247a9): blank-final
      definite-assignment fallback (Template), same-super/no-arg-primary shape
      (Date), generalized N-field sentinel merge with own-init/Java-default
      no-arg branches + param-rename rewriting (TemplateParser.Builder);
      comment preservation through all merges (assign trivia → field decls,
      promoted-param Javadoc hoisted). Corpus: 116/117 (99.1%) classified,
      UNSUPPORTED=1 (LiquidException — 3 different super calls, the genuinely
      Scala-inexpressible shape; whole-file override with sbt-gen). M0 GREEN.
- [ ] Convergence to ≥97%: byte/AST-level comparison tier (currently skeleton-level)

## Completed phases

### M1 — Tier 1 catalog on the Liqp corpus — DONE (2026-07-18)

Gate: ≥90% of the 117 done-status Liqp files equal-or-better vs the accepted
hand port, divergences individually classified; structural API-parity green.

Evidence (re-runnable: `sbt "corpus-tests/runMain balticporter.corpus.LiqpCorpus"`):
- Final counts: SKEL_EQUAL=71, SKEL_IDIOM=12, SKEL_ACCEPTED=12 (ledger,
  fingerprint-pinned, per-file verified reasons), SKEL_HAND_ADDITIONS=9,
  SUBSTITUTED=3 (documented dependency replacements) → **107/117 = 91.5%**
  classified equal-or-better; UNSUPPORTED=10 with named constructs.
- PARITY_FAIL=0 — every non-private original member present in emitted output
  (the computed covenant; it caught keyword-package-segment and
  anonymous-body-drop bugs during development).
- Comment invariant + determinism enforced on every translated unit.
- M0 gate re-verified GREEN after every wave (last: this commit).
- Journey: 53% baseline → 81% (wave 1) → 92% translate (wave 2) → honest dip
  to 88% (parity caught silent anonymous-body drops) → 91.5% with anonymous
  classes implemented (multi-line expression rendering).

### M0 — skeleton + round-trip — DONE (2026-07-18)

(unchanged — see git history for details; 20-file gate: determinism,
comment preservation, scala-cli compile, byte-identical across processes)
