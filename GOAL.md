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
- [x] Corpus-diff stage (2026-07-18, commit f611639): SkeletonDiff in the new
      `verify` module (Scalameta 4.17.2) — declaration-surface comparison with
      idiom classification (getter/setter collapse, mutability narrowing,
      hand-port additions). Convergence: SKEL_EQUAL=70 + IDIOM=9 +
      HAND_ADDITIONS=10 = 89/117 (76%) equal-or-better; SKEL_DIFF=15.
      M0 gate re-verified GREEN.
- [x] Enum translation + mapping entries (2026-07-18, commit 937fbb3): Java
      enum → parameterized `enum E(vals) extends java.lang.Enum[E]` (Flavor now
      SKEL_EQUAL); SUBSTITUTED status for the 3 documented dependency
      replacements; renamed counterpart + member-rename normalization
      (MapFilter, unparsedLine). Corpus: EQUAL=71 IDIOM=12 HAND_ADDITIONS=9
      SUBSTITUTED=3 → 95/117 (81%) accounted; DIFF=13, UNSUPPORTED=9.
      M0 gate GREEN.
- [ ] Close the SKEL_DIFF=13: most are Tier-2 vocabulary renames in disguise
      (toLiquid→toDataView, temporalAsArray→temporalAsVector) — start the
      project-rename vocabulary table + accepted-divergence ledger with
      per-file verified reasons (LValue BREAK/CONTINUE relocation, Sort
      rewrite, Strip_HTML helper split, NameResolver.Default,
      RenderTransformer* restructure, TemplateContext, Decrement/Increment
      INITIAL inlining)
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
- [x] Widen construct coverage, wave 2 (2026-07-18): static nested types →
      companion members, this(...)-chain ctor funnel (post-delegation stmts,
      depth-ordered auxiliaries), static/bound method refs, break/continue →
      scala.util.boundary. Evidence: LiqpCorpus `OK=104 UNSUPPORTED=9
      NO_COUNTERPART=4` → 92% translate (commit a55a578). M0 gate green.
- [ ] Hard tail (9 files, measured): unbound instance method refs (2),
      two-super-call ctor shapes (2+1+1 — may end as documented divergences or
      overrides, cf. RESEARCH.md §6 trap 2), mixed break+continue loop (1),
      final field with no init path (1), 3-ctor field logic (1)
- [ ] Map the 4 NO_COUNTERPART files (hand port merged/renamed them —
      manifest mapping entries, not translation failures)
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
