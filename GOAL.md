# Current goal state

Machine-updated by `/goal` iterations. One phase at a time; a phase advances
only when its gate (PLAN.md §13) is green with re-runnable evidence.

## Phase: M2 — vocabulary + idioms + build emission

Gate (PLAN.md §13): `translate && emit-build` on Liqp yields an sbt 2.0
project where `sbt Test/compile` passes on JVM with zero manual edits; corpus
convergence ≥97% ast-equal-or-classified; remaining divergences documented as
accepted improvements.

Status: IN PROGRESS

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
- [ ] Whole-corpus scalac gate: errors 135 → 56 → 38 (commit 47d72df).
      Remaining worklist (measured): Array[Any]↔AnyRef flows (7+2),
      conflicting definitions (5 — inspect), anon class extends final
      StringBuilder (2 — Java allows shadow-final? needs
      composition/override), Any-has-no-ctor (2), Collector variance (1),
      singles (Flavor, AtomNode, where-impls, FuzzyDateDateParser)
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
