---
paths:
  - "balticporter/corpus/**"
  - "ported/**"
  - "**/PortMap*.scala"
  - "**/ManifestAgreement*.scala"
---

# Dependent modules, the corpus, and the reference port

Detail for `CLAUDE.md` §1.5, §2, §2.1 and §3.5.

## §1.5 — a dependent inherits the shared surface

- A port may also be a `.conf` (`DESIGN.md` §5.7); the config path CONSTRUCTS the same values
  through the same constructors, `base = "…"` IS `extendedBy`, and anything config cannot hold
  arrives only as `ServiceLoader`-discovered code, never a string that is secretly code.
- **`targets` is not-inherited with a ONE-DIRECTIONAL constraint.** Fewer platforms asks fewer
  questions; MORE is a port that cannot be built, hidden by D2's ownership filter.
  `ManifestAgreement.Kind.TargetWidening` is fatal; the escape hatch is to widen the BASE. The
  default (all three platforms) makes a widening BY OMISSION the shape the rule meets first. The
  all-platform default is the reference ports' stated intent (maintainer, 2026-08): a module is
  JVM-only ONLY where its whole point is a JVM facility; native bindings go via `java.lang.foreign`
  beside Scala Native bindings, never JNI; narrowing carries its reason; a `Verdict.Depend` is
  answered by `PortManifest.dependencies` (`scala-java-time`, `scala-java-locales`), and
  `accept-jvm-only` refusing with the targets-contradiction is the feature working (P6).
- **`inject`**: the DROP binds every module that sees the type; the INJECTION is a build artefact
  exactly one module ships, or a dependent emits a second definition of the same FQN. Every "is this
  replaced?" check holds a module to its OWN drops.
- **Emission identity is (`portRoot`, `sourceSet`)**; `PortRun` opens with an unconditional
  `wipe(emitDir)`, so a second run at the same pair DELETES the first. N maven modules under one
  package root are ONE port with a glob list. A batch is a SCOPE edit (one glob, one denominator
  name), never a new port; a needed THIRD tree gets its own port root with DISJOINT packages,
  compiled beside the base. Measured: 29 extension modules as ONE dependent at 0 errors, `manifest`
  and `base-surface` 0 over 458 shared types.
- **A synthesising phase writes a minted unit only in the module that owns the declarations it was
  synthesised FOR** (`RunScope`); a dependent's model contains its base's units, so otherwise every
  module in the chain writes the same FQN with no `Origin` to classify — 24 errors over six
  dependent lanes with six suites stopped while the base read 0 (O5).
- **`surface` composes only where the PHASE says how** (`MergeablePolicy`, `surfaceFold`, at the
  base's pipeline position, `DESIGN.md` §8.13): a `Map` of independent keys unions, an ordered list
  and a first-match table do not, a `RuleScope` composes one way for `Only` and the other for
  `Everywhere`. Undeclared keeps two instances and a fatal `SurfaceDivergence`. Same key, different
  value is reported with the phase's own sentence. A merged phase sits at the BASE's position; an
  unmerged dependent phase lands at the END, and a `runsBefore` written from there POSTPONES the
  phase it names past every unconstrained phase — `context-seam 42 -> 41` with zero bytes changed
  and the new phase SKIPPED, because `type-redirect` slid past `globals->implicits`. State the
  POSITION by an EMPTY base instance first; declare no edge the phase does not need. The fold runs on
  `policyChain` only, or the base's published map is `BaseMapStale`.
- **`SurfaceIntrusion`**: a subject inside a base's `governs` claim that the base neither drops nor
  declares is fatal; the allowed case is "nothing stands at that name" — a drop WITH an injection
  ships a file. Screen the WHOLE of a mergeable phase's `subjects`, not what a merge added. Screen
  against what the base EMITS (its PUBLISHED PORT MAP), never its `governs` claim — a test source set
  lives in the same package, and 3 fatal findings hit one `dropMethods` entry naming the dependent's
  own test class (D10). Measured on libGDX's first `TypeRedirectTransform` (D9).
- **A dependent's phase RUNS OVER ITS BASE** and re-points types inside base signatures, with no
  diff and no compile error anywhere; only the base's PUBLISHED MAP compared against this run's
  derivation sees it. So the `RuleScope` goes on the ENTRY (D12; one FATAL `base-surface` at 0 errors).
- **A DERIVED surface escapes the merge contract at an EQUAL fingerprint.** `BeanCollapse` nominates
  a pair and derives the shape from the run's descendants, override closure and writes; one subclass
  in the dependent flips `Refuse`, emitting `def getW()` where the base emitted `var w`. Publish the
  derived shape (`Surface.MemberShape.form`, STATED, never inferred from an absent row) and compare as
  a fatal `base-surface` finding, only where the base's map has a row for the OWNER TYPE.
- A merge is needed for an INSTANCE count, not a policy count: ask first *does any dependent
  CONSTRUCT this phase?* — one grep over the ports.
- `PortMapTransform.followMemberRenames` reads the base's `upstream`/`emitted` member rows and
  `form=parenless`, matches by upstream FQN through the package rename map, and applies the same
  rename via `MemberRenamer` (D14). A `MethodBodyTransform` body is verbatim text and uses the
  base's emitted accessor names; adapt it when the base's surface changes.

## §2 — adding a library to the corpus

1. Make it compile — this is where the engine's gaps surface.
2. Test-compile it, then port and run its tests. Compiling is not passing (§3).
3. Run the Auditor (§4), by the user, over the new specialisations and the shared code.

Each library moves rules (c) → (b) → (a). A rule surviving three libraries unchanged is probably
universal; one needing a new parameter per library is a (b); one that cannot be shared is a (c) and
is named as such. Add the library's identifying strings, and its DEPENDENCIES', to the §1 grep
(`PlatformLint` carried `liquid.parser.`, `ua.co.k.` and jackson; `SpoonFrontend` hard-coded
`com.fasterxml.jackson.`).

## §2.1 — a port is named for its destination

`ported/<id>` takes the reference port's own module id (`../sge/build.sbt`, `../ssg/build.sbt`):
libGDX core is `ported/sge`, Ashley `ported/sge-ecs`, liqp `ported/ssg-liquid`. `label` is written as
`module=` into `port-map.tsv`, `PortManifest.name` is what `baseChain` matches, and
`PortMapTransform.forBases(…)` names it a third time; all three agree or a dependent finds no base
map and says so quietly. The `port-report/<main class>/` directory is the MEASUREMENT identity
(`LibgdxCoreMigrate` stays); `packageRenames` is the namespace axis (`sge-screens` declares
`sge.screen`); the upstream tree keeps its name.

## §3.5 — the reference port

- Record what they EMITTED (every raw generic is `[?]` — parent, overrides and fields alike;
  `AssetLoader.getDependencies` returns `DynamicArray[AssetDescriptor[?]]`) and whether they SOLVED
  or SKIPPED it (sge renamed `AsyncTask` to `() => Unit` and did not port several classes).
- **The reference SUITE is a differential gate.** Take a CENSUS before claiming a number; adapt only
  by NAME or SHIM substitution from an enumerated table, applied to CODE never a comment; count a file
  whose assertions cannot survive as incompatible. On the first library 95 of 194 ran at 93 passing
  and every one of the 99 left out was the HAND PORT's doing (added API, reshaped accessors,
  redesigned reflection). The census is taken TWICE — a per-file typer attribution is a FLOOR, and
  compiling the candidate set ALONE moved four files and 16 tests — and FIXTURES are split out of
  incompatible files, because the classification is not closed under dependency.
- **A blocker is measured at the EMITTED SITE.** One census scoped 75 of 79 tests behind a fixture
  on a reading of the java the port no longer performed (C11 drops that call) and the java could not
  have run headless either; built, it unlocked 5. Ask: does the EMITTED code still make the call?
  would the JAVA have run under this harness? does an `ENGINE-LIMITS.md` entry already own the site?
- **A port not at zero scopes its compile**: the TRANSITIVE CLOSURE over the emitted tree of what the
  suites name, failing the lane when it reaches a file in the run's own `errors.tsv`; a suite really
  floor-blocked is (c)-by-the-floor with rows cited. Of 8 blocked files exactly 2 were floor-blocked.
- **Compile the candidates UNEDITED first** and let the errors enumerate the mapping table: `C()` →
  `new C()` and a setter route were NO-OPS (universal apply; a phase had widened the fields). Two of
  four candidates changed zero lines.
- A census's own greps mask comments before counting; a reference suite's HEADER is documentation
  (one claimed a directory did not exist that held 22 files).
- **Does the JAVA compile against the dependency this run supplies?** `build.gradle` declared gdx
  1.14.0, the vendored tree is 1.14.1, three `E007`s on `OnscreenKeyboard.show(true)`: a counted
  residue naming both versions, never a migration only the maintainer may make.
- **What the reference port emitted is evidence; what it implies mechanically is a hypothesis
  measured before it is policy** (K16: `27 -> 47`, then `27 -> 51`).
- **Divergences**: `divergence-investigator` (`.claude/agents/divergence-investigator.md`), one row
  per invocation, reads the reference repo's git history, `docs/` and `.rescale/data/*.tsv`.
  `justified` names the rule or injection to carry. `unjustified` splits by KIND: BEHAVIOUR or
  OMISSION — java wins, the hand-added test is adapted or dropped with the finding recorded; API
  divergence (name, arity, property, slot type) — EXACT parity with the hand port, carried as a rule
  marked `unjustified` with a porter note. `not-a-divergence` is a defect of `api-parity`'s
  precision — three are closed by construction: a hand-port FILE whose header names no upstream
  (`ParityRef.upstreamMarkers`; empty = every file a party) is listed as
  `api-parity(hand-original)` and compared against nothing, only a DIRECT member of a
  template body, a top-level scope or an extension group is surface at all, and a type parameter's
  NAME is not API — the parameters in scope canonicalise to `$0…` by POSITION (the owner's first,
  then the declaration's own) on both sides, so an alpha-renaming (`[T]` vs `[A]`) is no
  divergence while BOUNDS and ARITY still are.
  `ported/<module>/divergence-verdicts.tsv` is joined into `divergence.tsv` and promoted by
  `just baseline-accept` (`DESIGN.md` §8.25).
