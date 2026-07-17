# Current goal state

Machine-updated by `/goal` iterations. One phase at a time; a phase advances
only when its gate (PLAN.md §13) is green with re-runnable evidence.

## Phase: M1 — Tier 1 catalog on the Liqp corpus

Gate: ≥90% of Liqp's ~135 files translate AST-equal-or-better vs the accepted
hand port in ../ssg/ssg-liquid (divergences individually classified: missing
rule / rule bug / hand-port idiom for Tier 2-3); structural API-parity gate
green.

Status: IN PROGRESS

Checklist:
- [ ] Corpus-diff runner (`diff` verb): translate a file, compare against the
      hand-ported counterpart via migration.tsv mapping (byte-equal /
      ast-equal / diverged three-state; per-file rule attribution)
- [ ] Widen frontend+printer construct coverage file-by-file, driven by
      Unsupported errors over the full liqp main sourceset (statics, enums,
      switch, loops, generics, nested types, anonymous classes, lambdas, ...)
- [ ] Tier 1 passes from PLAN.md §4.2 as needed by the corpus (boundary/return
      decision, ==/eq on references, overload disambiguation, varargs
      forwarding generalization, try/catch/finally, switch→match)
- [ ] Structural API-parity check (public surface original-BIR vs emitted tree)
- [ ] Track convergence % in this file per iteration
- [ ] GATE: ≥90% ast-equal-or-better + parity green, evidence recorded

Notes:
- The hand port maps Object→ssg.data.DataView, packages liqp→ssg.liquid, and
  applies Nullable/no-return idioms — corpus diff must normalize or classify
  these as idiom divergences (they are Tier 2/3 territory, not M1 failures).
- ssg-liquid replaced the ANTLR parser with a hand-written one: parser files
  are `skipped` in the corpus diff (documented substitution, PLAN.md §6).

## Completed phases

### M0 — skeleton + round-trip — DONE (2026-07-18)

Gate evidence (re-runnable):
- `sbt -batch "corpus-tests/runMain balticporter.corpus.LiqpM0"` →
  `[m0] determinism: OK`, `[m0] comments: OK`, `[m0] compile gate: OK`,
  `[m0] GATE GREEN` (20 files; scala-cli compile of out/liqp-m0/src +
  corpus-tests/shims under Scala 3.8.4).
- Cross-process byte-stability: two separate gate runs, sha256 over the 20
  output files identical (`CROSS-RUN-BYTE-IDENTICAL`).
- Comment invariant enforced by CommentCheck against an independent lexer
  (CommentScanner), not Spoon attachment.
- Constructor funnel proven: Filter.java's two-ctor shape → null-sentinel
  merge identical in structure to the hand-port idiom; subclasses' implicit
  `super()` binds to the translated no-arg auxiliary. PlainBigDecimal's
  two-super-ctor shape → delegate-to-primary.
- Shim disposition exercised: liqp.LValue + liqp.TemplateContext handwritten
  under corpus-tests/shims (recorded static/instance surface); liqp jar
  0.9.2.3 (closest published to vendored 0.9.2 commit) used for shadow-class
  resolution only.
