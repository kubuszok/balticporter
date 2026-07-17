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
- [x] Coverage baseline runner (LiqpCorpus): per-file tolerant translation over
      all 117 done-status files from ssg migration.tsv; report at
      out/liqp-corpus-report.tsv. Evidence (2026-07-18):
      `sbt "corpus-tests/runMain balticporter.corpus.LiqpCorpus"` →
      `OK=62 UNSUPPORTED=54 NO_COUNTERPART=1` (53% translate cleanly, comments
      preserved, before any Tier-1 widening).
- [ ] Corpus-diff stage on the OK files: compare against hand-ported
      counterpart (byte-equal / ast-equal / diverged three-state; per-file
      rule attribution)
- [x] Widen construct coverage, wave 1 (2026-07-18): statics→companion (class
      AND interface constants), classic for→while, for-each→index/iterator
      loop (iterated expr hoisted, evaluates once), try/catch/finally +
      multi-catch, fallthrough-free switch→match (empty-case grouping, missing
      default → `case _ => ()`), array initializers incl. `{}`, i++/i-- as
      statements, catch-var refs, expression lambdas, non-final ctor-assigned
      fields (`_p` rename), default-init fields, multi identity-super ctors
      (max-arity primary), @FunctionalInterface/Jackson annotation drops.
      Evidence: LiqpCorpus `OK=95 UNSUPPORTED=20 NO_COUNTERPART=2` — 81% from
      53% baseline. M0 gate re-verified GREEN.
- [ ] Widen construct coverage, wave 2 (measured remaining): nested types (8),
      method references Foo::bar (3), general ctor funnel — private synthetic
      primary + this-delegation for 2/3/6-ctor field-logic shapes (4+2 arity
      mismatches), loop break/continue → boundary (2), two-ctor merge with
      extra body stmts (1)
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
- Resolution architecture decision (2026-07-18): liqp types resolve from the
  whole vendored SOURCE tree (`FrontendConfig.resolutionRoots`), never from the
  published jar — the jar is version-skewed (0.9.2.3 vs vendored 0.9.2) and
  shades ANTLR. liquid.parser.v4 classes are regenerated from the vendored .g4
  with upstream's pinned ANTLR 4.13.0 (cs launch + javac, cached under out/).
  Classpath carries only true externals (antlr4-runtime, jackson, strftime4j).
  M0 gate re-verified green after this change.

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
