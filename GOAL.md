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
- [ ] sbt-gen: emit build.sbt (sbt 2.0.x, Scala 3.8.4) + project/ +
      module layout for the translated Liqp tree; scala-cli gate replaced by
      real `sbt Test/compile`
- [ ] Whole-corpus compile: translate all supported files (107) into the
      generated project, shims for the unsupported 10, compile on JVM
- [ ] Remaining unsupported tail (10, from M1): general super-ctor funnel
      (Block/Tag/TemplateParser/Date/LiquidException — needs the
      different-super-args strategy or overrides), unbound method refs (2),
      mixed break+continue (For), multi-statement lambda
      (RenderTransformerDefaultImpl), final-field-no-init (Template)
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
