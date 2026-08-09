package balticporter.runner

import balticporter.core.*
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.sbtgen.SbtGen
import balticporter.tir.{BreakCatchCheck, CastConversionCheck, CatalogCheck, CheckReport, ClassInitTriggerCheck, CommentAnchor, Correlate, CorrelateRun, CtorFunnel, DebugFlags, DependencyCheck, Decision, DecisionLog, Definition, ExternalUsage, HeapPollutionCheck, IdiomCheck, IdiomLog, JdkSurfaceCheck, MarkerCheck, MemberIndex, NoteCoverageCheck, OmissionCheck, Origin, Phase, Pipeline, PolicyBinder, PolicyBound, PortabilityCheck, PorterNote, Program, Reason, RemedySource, RemedyVocabulary, ResolutionPlan, OverloadRiskCheck, Remediator, RewriteCallSitesCheck, RewriteLog, RewriteTrace, RunScope, SrcMap, StandardTraversal, Surface, SymId, SwitchNullCheck, SymbolTable, Tree, TrivialSurface, TriviaCheck, TryResourceCheck, Xref}
import balticporter.transform.{BeanExposureCheck, CollectionBoundaryCheck, CollectionClosureCheck, CollectionsTransform, ContextSeamCheck, GlobalsToImplicitsTransform, MethodBodyTransform, NullabilityBoundaryCheck, NullabilityTransform, PackageRenameTransform, PortMapTransform, PublicFieldAccessorTransform, RetargetBoundaryCheck}

import java.nio.file.{Files, Path, StandardCopyOption}
import scala.jdk.CollectionConverters.*

/** Which source set of a port this run produces. Decides the `src_managed` subdirectory and
  * nothing else — the mechanics are identical, which is the point (a test port that quietly ran
  * fewer checks than the main port is the defect [[PortRun]] exists to remove).
  */
enum SourceSet(val configName: String, val noun: String):
  case Main extends SourceSet("main", "Scala files")
  case Test extends SourceSet("test", "Scala test files")

/** How hard the run works to prove its output is REPRODUCIBLE.
  *
  * Determinism is not a nicety here: every workflow this project has — `before->after` counts, the
  * emitted-code diff, the action cache, the baseline comparison — assumes that the same inputs
  * produce the same bytes. A non-deterministic emitter invalidates all of them at once and shows up
  * as noise attributed to whatever was changed last.
  */
enum Determinism:
  /** no check. For a bulk re-emit whose output is about to be thrown away. */
  case Off

  /** emit TWICE from the same `Program`, through two independent [[TirEmitter]] instances, and
    * byte-compare. Catches the realistic failure — the emitter's own mutable state and its
    * hash-ordered lazy tables — at the cost of one extra emission. This is the default because it
    * is cheap enough to leave on for every run, and a check that gets turned off is a check that
    * does not exist. */
  case Emission

  /** translate twice FROM SCRATCH — parse, phases, emit — and byte-compare, the BIR path's
    * `M0Pipeline.translateDeterministic` on TIR. Also covers the frontend and the phase pipeline
    * (symbol interning order, `SymId` allocation, any `Map`/`Set` iteration a phase leaks into its
    * output). Costs a whole second translation, so it is opt-in per run. */
  case Full

object Determinism:
  val FullFlag        = "--determinism=full"
  val OffFlag         = "--determinism=off"

  /** the flag a migration program passes straight through from its `main`. */
  def fromArgs(args: Seq[String]): Determinism =
    if args.contains(FullFlag) then Full else if args.contains(OffFlag) then Off else Emission

/** THE entry point for a porting program: everything that is not this library's policy.
  *
  * ==Why this exists==
  * `LibgdxCoreMigrate` was 253 lines, of which ~80 were engine logic — the dropped-type emission
  * skip, the support-source write-out, the injection copy, and the two substitution checks — and
  * the skill for adding a library told the next port to COPY it. The proof that this is the wrong
  * shape is not aesthetic: the second porting program in the same repository went its whole life
  * without ever calling `PortabilityCheck`, because check invocation was copy-paste rather than
  * orchestration. That is the `ReflectionToPortableTransform` mistake one level up (CLAUDE.md §1) —
  * a (b) mechanism with the policy inlined — and the consumer who pays for it is an agent in
  * another repository (§4.45) that has no way to know which checks it failed to copy.
  *
  * ==The division==
  * POLICY stays in the consumer's repo, as Scala: [[Substitutions]], the constructor arguments of
  * the parameterised transforms, the library-specific phases it plugs in, the package renames, the
  * provenance. All of it is typed and checkable by the compiler, which a manifest DSL would not be.
  *
  * MECHANICS live here, and cannot be opted out of:
  *   - the pipeline (frontend → phases → emitter), with the package rename forced LAST (§4.56);
  *   - ALL of the checks, on every run, whichever source set it is;
  *   - `src_managed/{main,test}/scala` from [[SbtGen]], never a hardcoded path (§5.5);
  *   - `externalConcrete` derived from the phases via [[RuntimePlan]], never passed by a caller —
  *     a caller-supplied one silently disables diamond-conflict detection when it is forgotten;
  *   - the [[PolicyReport]] from every policy-bearing value the run holds;
  *   - determinism by double-translation;
  *   - provenance on every emitted file, which is a licence obligation (§4.57).
  *
  * ==Reading the report==
  * Every finding names which of CLAUDE.md §1's three kinds its fix is — (a) engine bug, (b)
  * configure an existing phase, (c) write a library-specific rule — because an agent that cannot
  * classify a finding pays a full investigation for it. See [[PortReport]].
  *
  * @param label      prefix for every console line, e.g. the module name
  * @param portRoot   the PORT MODULE's root. `src_managed/{main,test}/scala` hangs off it; never
  *                   pass an output directory directly.
  * @param frontend   what to parse and what merely to resolve against. Units originating under a
  *                   `resolutionRoot` and not under `sourceRoot` are NOT emitted: that is what
  *                   "participates in resolution but is not converted" means, and doing it here is
  *                   what stops a second porting program from re-emitting the first one's output.
  * @param phases     the transform pipeline, in declaration order. Must NOT contain a
  *                   [[PackageRenameTransform]] — see `packageRenames`.
  * @param subs       drop/inject manifest. Empty is a no-op and reports nothing.
  * @param packageRenames
  *                   upstream prefix → port prefix. Taken as DATA rather than as a phase because
  *                   the phase has an ordering obligation no `runsAfter` can state (CLAUDE.md
  *                   §4.56): it must run after every other phase, since all of their policy is
  *                   written in the upstream namespace. The run appends it last and verifies the
  *                   rename with `PackageRenameTransform.check` before and after.
  * @param runtimeMode
  *                   how `balticporter-runtime` reaches this source set. `Dependency` for anything
  *                   with a real build; `Vendored` only for a SINGLE source set compiled standalone
  *                   — a port that vendors into both `main` and `test` defines every support type
  *                   twice.
  * @param supportSources
  *                   FQN → source for support types a phase's output references but does not
  *                   declare through [[RequiresRuntime]]. A phase that CAN declare them should:
  *                   this parameter is the seam for the ones that cannot, not an alternative to it.
  * @param project    OPT-IN build generation, and the ONLY seam that writes anything outside
  *                   [[outDir]] and the report directory. `Some(spec)` emits the sbt skeleton for
  *                   the port — `build.sbt`, `project/build.properties`, `.gitignore` and the
  *                   engine pin — into `portRoot`; `None`, the default, emits none of it.
  *
  *                   `None` is the case the engine's real consumer is in (§4.45): a repository
  *                   calling this from inside a build it already owns, whose build file and ignore
  *                   rules are DECISIONS it has already made. A run with `project = None` writes
  *                   exactly the sources — emitted units, injected replacements, `supportSources`,
  *                   and the vendored runtime under [[RuntimeMode.Vendored]] — all under `outDir`,
  *                   plus its report directory when the artifact layer is on. `PortRunProjectSpec`
  *                   asserts that file set exactly, in both directions.
  * @param manifest   this module's policy as a VALUE, and — through `PortManifest.bases` — the
  *                   modules it is a dependent of. When present it SUPPLIES `phases`, `subs` and
  *                   `packageRenames` (which must then be left at their defaults) and enables
  *                   [[balticporter.core.ManifestAgreement]], the check that this module's shared
  *                   surface agrees with the module that emits it. A run with resolution roots
  *                   outside its own source root is structurally a DEPENDENT port, and one that
  *                   declares no base is itself a finding — that is what makes the check
  *                   unskippable, the same way `RequiredChecks` makes the others unskippable.
  */
final case class PortRun(
    label: String,
    portRoot: Path,
    sourceSet: SourceSet,
    frontend: FrontendConfig,
    phases: List[Phase],
    subs: Substitutions = Substitutions.none,
    provenance: Option[Provenance] = scala.None,
    packageRenames: Map[String, String] = Map.empty,
    /** the PER-TYPE half of the same phase — see `packageRenames` for why all four arrive as data
      * rather than as a surface entry, and `PackageRenameTransform` for what each one says. Empty
      * is a no-op; a `manifest` supplies them and they must then be left at their defaults. */
    typeRenames: Map[String, String] = Map.empty,
    subPackages: Map[String, String] = Map.empty,
    flattenNestedTypes: Set[String] = Set.empty,
    allowPackageSplit: Set[String] = Set.empty,
    runtimeMode: RuntimeMode = RuntimeMode.Dependency,
    supportSources: Map[String, String] = Map.empty,
    project: Option[SbtGen.ProjectSpec] = scala.None,
    determinism: Determinism = Determinism.Emission,
    cache: Option[Path] = scala.None,
    lenient: Boolean = true,
    manifest: Option[PortManifest] = scala.None,
    /** DIAGNOSTIC mode (E9), orthogonal to [[RuntimeMode]] and OFF by default.
      *
      * With it off — the shipping behaviour — a construct the engine cannot render faithfully
      * leaves the residue comment `ENGINE-LIMITS.md` M6 counts, and the port compiles. With it on,
      * each such site becomes `scala.compiletime.error("balticporter: <what>: <why>; <what an agent
      * must do>; origin <javaPath>:<line>")`: the port deliberately does NOT compile, and every
      * error names the construct, the reason, the action and the upstream line.
      *
      * For the first week of a new library, where the operator is an agent in another repository
      * (§4.45) that has to FIND the residue before it can act on it. Those errors never mix with
      * real ones — `Correlate.Lane.Declared` classifies them by the message the engine itself
      * wrote, ahead of the source-map lookup. */
    preview: Boolean = false,
    /** BEST-EFFORT emission (`DESIGN.md` §6.4), orthogonal to [[preview]] and OFF by default.
      *
      * The mode a library lives in for its first weeks. With it OFF, the emission GATE runs before
      * a byte is written and an `Open` marker means the deliverable tree is not written at all —
      * which is the anti-omission stance (§3.4) applied to the one construct class the engine
      * admits it cannot translate. With it ON, the run still writes: each open marker renders as
      * its approximation inside deterministic comment fences, each affected file carries a banner,
      * the output goes to a SEPARATE directory beside the deliverable one with a sentinel file in
      * it, and the run exits NONZERO. Three of those four exist so degraded output can never be
      * mistaken for the real thing — dotty's own best-effort compilation is where that discipline
      * is borrowed from (§6.1).
      *
      * At zero open markers the mode changes nothing: no marker, no fence, no banner, and the
      * emitted text is byte-identical by construction rather than by comparison (§6.4). */
    bestEffort: Boolean = false,
    /** printed as the last line — what the operator does next. */
    nextStep: String = "",
    /** REMEDIES THIS CLASSPATH DECLARES that this run's own pipeline does not — see
      * [[balticporter.tir.RemedyVocabulary]] for why the KNOWN set and the ACTIVE set are two sets.
      *
      * The active set is derived from what the run holds (its phases, and the checks the
      * orchestrator registers) and needs no parameter. This is the rest of the KNOWN one: a
      * `TransformFactory` on the classpath speaks for the phase it would build, so the config loader
      * can tell a TYPO (`ConfigError`, at load) from a port that selected a remedy and forgot to
      * enable the phase (a policy finding naming the phase). Without it the second reads as the
      * first, and the reader is sent looking for a spelling mistake in a correct id.
      *
      * Empty is the default and the whole of the Scala front door, where there is no registry to
      * ask — a run built by hand knows exactly the phases it was given. */
    knownRemedies: RemedyVocabulary = RemedyVocabulary.empty,
):

  private def say(s: String): Unit = println(s"[$label] $s")

  // ---- the policy this run applies: the manifest's when it has one, the raw fields otherwise ----
  // Both sources produce the SAME values; a manifest additionally records where each came from,
  // which is what makes the agreement check possible. A run may use one or the other and never
  // both, so there is never a question of which won.

  /** every drop in the manifest chain, plus THIS module's injections. Stable instance: the
    * `Substitutions` tally of which keys fired has to be read off the value the frontend was
    * handed, and `PortManifest.substitutions` is a `lazy val` for exactly that reason. */
  private def policySubs: Substitutions = manifest.map(_.substitutions).getOrElse(subs)

  /** the drops THIS module DECLARES and the replacements it SHIPS — what CHECK 2 holds it to.
    *
    * A dependent module inherits a base's drops (it must model those types as substituted) but not
    * the base's injections (exactly one module ships each replacement file, or the FQN is defined
    * twice). So "dropped, unreplaced and still referenced" is a question about the base's own
    * output, not this one's, and asking it here would report every inherited drop as dangling. */
  private def ownSubs: Substitutions = manifest match
    case Some(m)    => m.ownDrops
    case scala.None => subs

  private def declaredPhases: List[Phase] = manifest.map(_.effectiveSurface).getOrElse(phases)

  /** THE BACKENDS this module is ported for — the §1(b) parameter `PortabilityCheck` reads.
    *
    * A run with no manifest gets the same default a manifest carries (all three platforms), which is
    * the semantics the check had before it took a parameter at all. That equality is the point: no
    * port's `portability(*)` baseline moves because the engine gained a target set, and a port that
    * wants the narrower question has to say so. */
  private def targets: Set[balticporter.catalog.Platform] =
    manifest.map(_.targets).getOrElse(balticporter.catalog.Platform.values.toSet)

  /** …and the port's own answers where it disagrees with the catalog's RECOMMENDATION. Never with
    * its availability: `by` is the (a) fact and no manifest reaches it. */
  private def verdictOverrides: PortabilityCheck.Overrides =
    manifest.map(_.verdictOverrides).getOrElse(Map.empty)

  /** …and the rules those targets ask about. One derivation, read by both the emitted-code lane and
    * the injected-source scan, so the two numbers can never be against different rule sets. */
  private def portabilityRules: List[PortabilityCheck.Rule] =
    PortabilityCheck.rulesFor(targets, verdictOverrides)

  private def renames: Map[String, String] = manifest.map(_.effectivePackageRenames).getOrElse(packageRenames)

  /** THE rename phase this run appends, or none — a `lazy val` and not a `def`, because the phase
    * now owns two answers the run has to read back off the SAME instance: which per-TYPE entries it
    * ACCEPTED (a refused one must not reach `dropped-types.tsv` or the source map) and what it
    * REFUSED (a §1(b) finding for the `policy` check). A fresh instance per call would answer both
    * questions with silence, and nothing would move a count. */
  private lazy val renamePhase: Option[PackageRenameTransform] =
    val types = manifest.map(_.effectiveTypeRenames).getOrElse(typeRenames)
    val subs2 = manifest.map(_.effectiveSubPackages).getOrElse(subPackages)
    val flat  = manifest.map(_.effectiveFlattenNestedTypes).getOrElse(flattenNestedTypes)
    val split = manifest.map(_.effectiveAllowPackageSplit).getOrElse(allowPackageSplit)
    if renames.isEmpty && types.isEmpty && subs2.isEmpty && flat.isEmpty then scala.None
    else Some(new PackageRenameTransform(renames, types, subs2, flat, split))

  /** what an UPSTREAM name is emitted as, under the policy this run ACCEPTED (§4.56: any artifact
    * joining policy to observed code carries both namespaces, and only the run holds both). */
  private def emittedName(fqn: String): String = renamePhase.fold(fqn)(_.emittedName(fqn))

  /** where this source set's emitted Scala goes. From [[SbtGen]], never composed by hand (§5.5). */
  def outDir: Path = SbtGen.managedDir(portRoot, sourceSet.configName)

  /** where BEST-EFFORT output goes (`DESIGN.md` §6.4) — a SEPARATE directory, never [[outDir]].
    *
    * Borrowed from dotty's own best-effort compilation, which writes degraded artifacts to their
    * own directory with a distinct header so they can never masquerade as real ones (§6.1). Beside
    * the deliverable tree rather than inside it, so `clean` reaches it and no build that globs
    * `src_managed/main/scala` picks it up by accident. */
  def bestEffortDir: Path =
    outDir.getParent.resolve(outDir.getFileName.toString + "-besteffort")

  /** The whole run. Throws on a FATAL finding — a leaked dropped type, a dangling substitution, a
    * determinism violation — after printing the full report, so an operator sees every finding and
    * not just the first one that aborts. */
  def execute(): PortResult =
    require(
      !declaredPhases.exists(_.isInstanceOf[PackageRenameTransform]),
      s"[$label] PackageRenameTransform must not be listed in `phases`: it has to run AFTER every " +
        "other phase (CLAUDE.md §4.56), which `runsAfter` cannot express. Pass `packageRenames` " +
        "instead and PortRun places it last.",
    )
    require(
      manifest.isEmpty || (phases.isEmpty && subs == Substitutions.none && packageRenames.isEmpty &&
        typeRenames.isEmpty && subPackages.isEmpty && flattenNestedTypes.isEmpty && allowPackageSplit.isEmpty),
      s"[$label] a `manifest` SUPPLIES `phases`, `subs` and every rename map; passing either " +
        "source alongside it would give this run two policies and no way to say which one the " +
        "dependent modules have to agree with. Move the values into the PortManifest.",
    )

    anchorReportPaths()

    // ---- THE SURFACE GATE: a pair the fold could not compose stops the run BEFORE the pipeline --
    //
    // Every other manifest finding is reported after the translation, beside the emitted text it
    // describes. This one cannot wait, because it is a statement about the PIPELINE THAT IS ABOUT TO
    // RUN: two instances of one phase name, carrying two policies, with no merge to compose them.
    //
    // It used to fall through — `Pipeline.order` keyed phases by NAME, so of the two instances the
    // LATER one ran and the other silently did not. Measured: a base's whole `globals->implicits`
    // holder vanished from one module's pipeline with no error, no check count and no finding, while
    // the fatal finding reported beside it was about something else entirely (`ENGINE-LIMITS.md` CT9
    // Face B). Ordering INSTANCES is the other half of that fix and is what makes this gate
    // necessary rather than merely tidy: with both instances running, a refused pair would apply two
    // conflicting configurations of one phase to one program.
    //
    // So the refusal is LOAD-BEARING. Nothing is parsed, nothing is emitted, and the message carries
    // BOTH instances' policy fingerprints — which is the pair a reader has to reconcile, and the one
    // thing the silent drop made unreadable.
    //
    // The bases' PUBLISHED MAPS come with it, because the other question the gate asks — does a
    // subject this module adds edit something a base actually EMITS — is a fact about the base's
    // OUTPUT and not about its manifest (CT9 Face A). `basePorts` is discovered once and read here
    // and by the check below; a base with no usable map takes the re-derived answer, loudly.
    val surfaceStop = ManifestAgreement.surfaceGate(manifest, basePorts)
    if surfaceStop.nonEmpty then
      surfaceStop.foreach(f => System.err.println(s"[$label] FATAL — ${f.render}"))
      sys.error(
        s"[$label] ${surfaceStop.size} shared-surface finding(s) the effective pipeline cannot be " +
          "run with:\n" + surfaceStop.map("  " + _.render).mkString("\n") +
          "\n  [the run stops HERE, before any phase runs. A DIVERGENCE leaves two instances of one " +
          "phase that would both transform this program with two policies — give the phase a " +
          "`MergeablePolicy`, reconcile the two values, or share one instance. An INTRUSION would " +
          "re-shape a namespace a base module emits, so the two ports could not compile together — " +
          "move the entry to the base's manifest. DESIGN.md §8.13]")

    val roots = if frontend.resolutionRoots.isEmpty then "" else s" (resolving against ${frontend.resolutionRoots.size} extra root(s))"
    say(s"building model over ${frontend.files.size} file(s)$roots…")

    val translated = translate()
    val program    = translated.program
    say(s"TIR: ${program.units.size} units, ${program.symbols.all.size} symbols")

    // ---- checks over the PROGRAM (before anything is written) ----
    val substituted = program.symbols.all.collect { case s if Substituted.tags(s) => s.id }.toSet
    if substituted.nonEmpty then
      say(s"substitution blast radius:\n${RewriteTrace.impactSummary(program, substituted)}")

    val mismatches = RewriteTrace.check(program)
    CheckReport.record(PortRun.Signature, mismatches.map(_.report))
    if mismatches.isEmpty then say("signature check: all call sites agree with their declarations")
    else
      say(s"signature check: ${mismatches.size} site(s) disagree with their declaration")
      say(PortReport.Kind.Signature.classification)
      mismatches.take(20).foreach(m => println("  " + m.render))

    // minus the DROPPED units: `emitOrder` still carries them (the write loop below skips them at
    // write time), but a finding about a member of a type the manifest substitutes away describes
    // code this run never emits — the classpath holds the injected replacement, not the reported
    // construct. The check's own contract is "the units the run actually EMITS"; hold it to that.
    // The filter lives HERE because the drop set is policy and the check stays library-blind (§1).
    val checkedUnits = emittedUnits(program, translated.emitOrder)
    // …and the SAME `Surface` the emitter used. Every constructor lane here shadows a
    // `CtorFunnel.Plans` decision, and `Plans` takes the view: built without it the check gets a
    // `TrivialSurface` (everything is mine), so a replay the emitter REFUSED because it reaches a
    // base's `private` member is still reported EXPRESSED here, and the `super(args)` the port drops
    // moves no count at all. Measured on gdx-gltf: `omissions` sat at 3 while the emitter had just
    // lowered two constructors to a bare `this()` (`ENGINE-LIMITS.md` D5).
    val omissions = OmissionCheck.check(program, checkedUnits, Some(translated.surface))
    CheckReport.record(PortRun.Omissions, omissions.map(_.report))
    say(s"OMISSIONS (emitted code silently loses these): ${omissions.size}")
    if omissions.nonEmpty then say(PortReport.Kind.Omission.classification)
    println(OmissionCheck.summary(omissions))

    // ---- the collection boundary the phase itself drew: closure and stranding, triaged ----
    // Only when the phase RAN — the checks read its typeMap-derived tables, and a port that never
    // retyped a collection has no boundary to police. Over `checkedUnits` for the same reason the
    // omission check is: a finding about a resolution root belongs to the module that emits it.
    effectivePhases.collect { case c: CollectionsTransform => c }.foreach { c =>
      val clo = c.closure(program, checkedUnits)
      val bnd = c.boundary(program, checkedUnits)
      // …and the RETARGET's own direction, which neither of the two above can see: they read
      // `mappedTypes`/`retypedTargets`, and a retarget joins neither (its precondition says its
      // target is usable wherever its source was). That licence is one-directional — it covers the
      // retyped value reaching a JDK slot, never a JDK-PRODUCED value reaching a retyped one — and
      // the position-blind retyping has already moved the node type on both sides of such a slot,
      // so a check reading node types reports zero on exactly the sites the retarget made.
      // Recorded even at zero, for the reason the other two are: a number nobody prints is a
      // sentence living in prose.
      val ret = c.retargetBoundary(program, checkedUnits)
      // …minus what the port SELECTED a remedy for. A resolution is a MOVE, so the drained rows
      // leave this lane and arrive in `remediation(resolved)` (CLAUDE.md §5), and every reader below
      // — the record, the count and the summary — sees the SAME list, which is what stops the three
      // from ever disagreeing about how big this residue is.
      val bndKept = CollectionBoundaryCheck.resolved(translated.binder.resolutions, bnd)(using program)
      locally {
        given Program = program
        CheckReport.record(CollectionClosureCheck.Name, clo.map(_.report))
        CheckReport.record(CollectionBoundaryCheck.Name, bndKept.map(_.report))
        CheckReport.record(RetargetBoundaryCheck.Name, ret.map(_.report))
      }
      say(s"COLLECTION CLOSURE (mapped supertype, unmapped subtype): ${clo.size}")
      if clo.nonEmpty then say(CollectionClosureCheck.Classification)
      println(CollectionClosureCheck.summary(clo))
      say(s"COLLECTION BOUNDARY (stranded slots the phase created): ${bndKept.size}")
      println(CollectionBoundaryCheck.summary(bndKept))
      say(s"RETARGET BOUNDARY (values the JDK produces at a retargeted type): ${ret.size}")
      println(RetargetBoundaryCheck.summary(ret))
    }

    // ---- what a reflective framework cannot see: java-public fields with no bean property ----
    // Recorded only when the phase RAN, for the reason the collection checks are: a port that never
    // declared a reflective consumer has no policy for this to be a residue of, and the population
    // would then be every public field in the library. Over `checkedUnits` (ENGINE-LIMITS D2).
    effectivePhases.collect { case b: PublicFieldAccessorTransform => b }.foreach { b =>
      val exp = b.exposure(checkedUnits)
      CheckReport.record(BeanExposureCheck.Name, exp.map(_.report))
      say(s"BEAN EXPOSURE (java-public fields a framework cannot see): ${exp.size}")
      println(BeanExposureCheck.summary(exp))
    }

    // ---- the nullability boundary: every annotated site the phase refused, and every wrapper
    // seam it could not close. Recorded only when the phase RAN, for the same reason the two
    // collection checks are — a port that configures no annotation has no boundary to police — and
    // over `checkedUnits`, so a dependent does not report its base's refusals (ENGINE-LIMITS D2).
    effectivePhases.collect { case n: NullabilityTransform => n }.foreach { n =>
      // …minus the port's own selections, drained BEFORE the record — see the collection lane above.
      val bnd = NullabilityBoundaryCheck.resolved(translated.binder.resolutions, n.boundary(checkedUnits))
      CheckReport.record(NullabilityBoundaryCheck.Name, bnd.map(_.report))
      say(s"NULLABILITY BOUNDARY (sites refused, wrapper seams left open, retypes the language " +
        s"does not make transparent): ${bnd.size}")
      println(NullabilityBoundaryCheck.summary(bnd))
    }

    // ---- the CONTEXT boundary the globals phase drew: every place the threading stopped ----
    // Only when the phase RAN, and only over `checkedUnits`, for the two reasons above. A port that
    // declared no holder records nothing here at all — the phase returns its input before building
    // anything, so this is a no-op by arithmetic rather than by a branch.
    //
    // COLLECTED here and RECORDED after emission, which is a departure from every other check in
    // this block and is forced by the fifth kind: a clause the phase attached and the emitter did
    // not write is not visible in the tree these four are read from (`ENGINE-LIMITS.md` CT5). One
    // `CheckReport.record` per check name is the contract — a second call REPLACES the first — so
    // the two halves cannot be recorded where each is computed.
    val contextPhases = effectivePhases.collect { case g: GlobalsToImplicitsTransform => g }
    // …and the DRAIN runs HERE, where the other lanes' do, even though the record cannot. A
    // resolution has two artifacts and `AppliedResolution` exists so they cannot disagree — but the
    // decision half has a deadline the finding half does not: every decision must be in the log
    // BEFORE a byte is emitted, because a porter note IS emitted text. Drained at the record site
    // instead, these three applications reached `remediation(resolved)` and no `decisions.tsv` row
    // at all, which is exactly the disagreement that type was written to make impossible. The fifth
    // kind (`LostClause`) is the one that cannot move: it is the emitter's own reading of the header
    // it wrote, and no remedy targets it — which is stated in the menu rather than left to the fact
    // that it is appended after this line.
    val contextSeams  = ContextSeamCheck.resolved(translated.binder.resolutions,
                                                  contextPhases.flatMap(_.seams(program, checkedUnits)))

    // ---- cross-port composition: does the shared surface agree with the module that emits it? ----
    // Runs on EVERY port. On a base port `shared` is empty and the check is a no-op by arithmetic
    // rather than by a branch — the same discipline as an empty policy making a phase a no-op.
    // `fired` comes from the RUN's binder — the drop keys that resolved to something. It used to be
    // a mutable tally on `Substitutions`, which answered "did this key ever fire on this INSTANCE"
    // and unioned two source sets translated through one manifest.
    val firedKeys = translated.binder.bindings.filter(_.binding.isBound).map(_.entry).toSet
    val agreement = ManifestAgreement.check(manifest, sharedSurface(program, translated.foreign),
                                            foreignRoots, basePorts, firedKeys)
    CheckReport.record(PortRun.Manifest, agreement.map { f =>
      CheckReport.Finding(PortRun.Manifest, f.kind.toString, f.subject, "", 0, f.render)
    })
    val bases = manifest.toList.flatMap(_.baseChain).map(_.name)
    val howBases =
      if bases.isEmpty then "no base module declared"
      else s"base(s): ${basePorts.map(p => s"${p.name}=${if p.map.isDefined then s"published map (${p.source})" else "re-derived"}").mkString(", ")}"
    say(s"MANIFEST agreement ($howBases, " +
      s"${translated.foreign.size} shared type(s)): ${agreement.size} disagreement(s)")
    if agreement.nonEmpty then
      say(PortReport.Kind.Manifest.classification)
      agreement.take(40).foreach(f => println("  " + f.render))

    // ---- what this run SYNTHESISED, and whether a base already publishes it ----------------------
    //
    // `ENGINE-LIMITS.md` §13 O5 and CLAUDE.md §1.5: a phase that MINTS a top-level unit owes the same
    // one-module answer an `inject` does, and the run cannot fall back on `converted` to hold it to
    // one — a minted unit has no `Origin`, and the documented rule for a unit with no usable origin
    // is to CONVERT it, because refusing to emit on a missing origin would be a silent omission.
    // That is right for a parsed unit and blind here, so the phase fences its own mint on `RunScope`
    // and this is the belt: a synthesised unit at an FQN a base's published map already claims is
    // FATAL, whichever phase minted it. Written to catch the NEXT one, which will not have read O5.
    //
    // Counted on every run, `0 of 0` included, for the reason every check here is: a number nobody
    // prints is a sentence living in prose, and "found nothing" must be distinguishable from "never
    // looked" (this looked, and a base with no published map is reported as such above).
    val synthesised = translated.emitOrder.filter(u => PortRun.isSynthesised(u.origin))
    val claimed     = PortRun.claimedSynthetic(program, synthesised, basePorts.flatMap(b => b.map.map(b.name -> _)))
    say(s"SYNTHESISED UNITS (minted by a phase, no Java file behind them): ${synthesised.size}" +
      s", ${claimed.size} at an FQN a base already emits")
    if claimed.nonEmpty then
      claimed.foreach(c => System.err.println(s"[$label] FATAL — ${c.render}"))
      sys.error(
        s"[$label] ${claimed.size} synthesised unit(s) would be written at an FQN a base module " +
          "already emits:\n" + claimed.map("  " + _.render).mkString("\n") +
          "\n  [§1(a) ENGINE — the phase that minted these must fence its mint on `RunScope.emits`, " +
          "so the unit is written by the module that owns the declarations it was minted FOR and by " +
          "no other. A dependent still retypes and coerces; it resolves the name against the base's " +
          "emitted output. There is no manifest key for this: `surface` is inherited through " +
          "`extendedBy` and cannot be subtracted, and holding the phase back in a dependent is " +
          "CLAUDE.md §1.5's compile-alone-but-not-together failure. See ENGINE-LIMITS.md §13 O5]")

    // ---- THE BASE-SURFACE CONTRACT: what this run could not answer, and what that cost -----------
    //
    // The one behavioural change §8.3 asks for, and it is a deliberate departure from the
    // loud-but-non-fatal `BaseMapStale` / `BaseMapMissing` above. Those FALL BACK to re-derivation,
    // and falling back is exactly how `ENGINE-LIMITS.md` D4 produced three compile errors while
    // every check in the run reported clean: nothing in a dependent's run disagrees with itself, so
    // there is no count for the fallback to move.
    //
    // The rule is per QUESTION, not per map: an `Unknown` no emission consumed is a finding; an
    // `Unknown` whose answer SHAPED EMITTED TEXT fails the run, naming the base module, the type,
    // and which of §1's three kinds the fix is (§4.45). Only the consumer knows which it was, which
    // is why `Surface.Gap.fatal` is set by the asker.
    //
    // THE EMPTY BASE MANIFEST stays the escape hatch, and what it exempts is precise. A resolution
    // root that is genuinely not a ported module is a STATEMENT a port makes (§1.5) — the run says
    // so loudly, above, through `ManifestAgreement`. It does NOT exempt a question: the questions
    // below are asked about a non-owned CLASS, whichever root it came from, and a class that root
    // supplies is as unanswerable as any other. What keeps that honest rather than fatal is the
    // per-QUESTION rule itself: a class whose plan cannot drift is a finding, and only a class whose
    // emitted `extends` clause depended on an answer nobody published fails. If a port ever needs
    // more than that, the fix is to run the base — not to widen the exemption, which would restore
    // exactly the fallback this replaces.
    // …and the one contract question no PHASE can ask, because it needs the base's published map and
    // this run's own idiom log at once: does the collapse verdict this run DERIVED over a BASE's
    // declaration agree with the shape the base emitted? See `collapseDivergence` — the verdict is
    // whole-program-dependent, so a dependent's own subclass silently re-decides it, at an equal
    // fingerprint and with every count flat.
    effectivePhases.collect { case b: balticporter.transform.BeanPropertyTransform => b }.foreach { b =>
      val a = PortRun.collapseDivergence(translated.idioms,
                                         basePorts.flatMap(p => p.map.map(p.name -> _)),
                                         b.pairsTable, b.targetOf)
      a.gaps.foreach(translated.surface.gap)
      // the DENOMINATOR beside the finding, and §3's rule read at a check this run owns: `0 gaps`
      // because sixty verdicts agreed and `0 gaps` because the comparison never ran are the same
      // line otherwise, and the second is every way this silently stops working.
      if a.checked > 0 || a.gaps.nonEmpty then
        say(s"COLLAPSE AGREEMENT (this run's derived shape vs the base's published one): " +
          s"${a.checked} verdict(s) compared, ${a.gaps.size} disagreeing")
    }
    val surfaceGaps = (translated.surface.gaps ++ translated.emitter.surfaceGaps).distinct
    val fatalGaps   = surfaceGaps.filter(_.fatal)
    // RECORDED BEFORE THE REFUSAL BELOW, and including the fatal ones. A fatal gap stops the run, so
    // a successful run's row only ever counts the non-fatal half — but a run that dies must still
    // leave the artifact that says why, and a recording placed after `sys.error` writes nothing on
    // exactly the run a reader most needs it for.
    CheckReport.record(PortRun.BaseSurface, PortRun.baseSurfaceFindings(surfaceGaps))
    say(s"BASE SURFACE (contract questions this run could not answer): ${surfaceGaps.size}" +
      (if fatalGaps.isEmpty then "" else s", ${fatalGaps.size} of them FATAL"))
    surfaceGaps.take(40).foreach(g => println("  " + g.render))
    if fatalGaps.nonEmpty then
      sys.error(
        s"[$label] ${fatalGaps.size} contract question(s) shaped emitted text and could not be " +
          "answered from a base's published port map:\n" +
          fatalGaps.map("  " + _.render).mkString("\n") +
          "\n  [a run that falls back to re-deriving these emits text that compiles alone and cannot " +
          "compile against the module it resolves against — DESIGN.md §8.3]")

    // ---- what a base's PUBLISHED map says about the references this module is about to emit ----
    // Recorded on EVERY run, `Nil` included: without a `PortMapTransform` in the pipeline the list
    // is empty, and `counts.tsv` must be able to tell "found nothing" from "never ran" — which is
    // exactly what a check that only registers itself when it has something to say destroys.
    val mapFindings = effectivePhases.collect { case p: PortMapTransform => p.findings }.flatten
    CheckReport.record(PortRun.PortMapCheck, mapFindings.map(_.report))
    if effectivePhases.exists(_.isInstanceOf[PortMapTransform]) then
      say(s"PORT MAP (references the base module does not emit): ${mapFindings.size}")
      mapFindings.groupBy(_.issue).toList.sortBy(_._1.toString).foreach { (issue, fs) =>
        say(s"  ${fs.size} × $issue")
        say("  " + PortMapTransform.classification(issue))
        fs.take(10).foreach(f => println("    " + f.render))
        if fs.sizeIs > 10 then println(s"    … ${fs.size - 10} more (see findings.tsv)")
      }

    // Two portability numbers, recorded separately: what the PROGRAM references, and what the
    // SHIPPED code references. A run that reported only one of them could not show a substitution
    // moving a violation out of the deliverable.
    // …by the TAG the frontend attached, and only THEN by the key. §4.56's "an artifact that joins
    // POLICY to OBSERVED code carries BOTH names", found the way that rule keeps being found: a
    // `dropTypes` key is the UPSTREAM FQN and `Symbol.fullName` here is the EMITTED one, so on a
    // RENAMING port this set was always EMPTY and `portability(emitted)` was `portability(all)` —
    // every violation inside a type the port deliberately does not ship, counted as shipped, and
    // `Remediator` suggesting a `dropTypes` entry the manifest already has. `isDropped` (the
    // emission gate) had the tag from the start and this line did not, which is why the two agreed
    // on a non-renaming port and disagreed on every other one (ENGINE-LIMITS.md P7).
    val droppedIds  = program.symbols.all.collect {
      case s if Substituted.tags(s) || policySubs.dropsType(s.fullName) => s.id
    }.toSet
    // A type this run does not ship is either DROPPED by policy or FOREIGN — resolved against and
    // emitted by another module. Both must be excluded from the shipped-code number, or a dependent
    // port reports its base's findings as its own (see `PortabilityCheck.inEmittedCode`).
    val foreignIds  = translated.foreign.map(_.symbol).toSet
    val notShipped  = (id: SymId) => droppedIds(id) || foreignIds(id)
    val allViolations = PortabilityCheck.check(program, portabilityRules)
    val emittedSites  = PortabilityCheck.inEmittedCode(program, allViolations, notShipped)
    // ---- `accept-jvm-only`, the one remedy this CHECK carries out (`PortabilityCheck.AcceptJvmOnly`)
    //
    // It changes no tree, so it is `ResolutionPlan.drain`'s half of the rule and not `appliedAt`'s:
    // the check that mints the row is the only thing that ever holds it, so the partition and the
    // ledger row come from one traversal. ONE drain path — never a third.
    //
    // The CONSISTENCY test comes first and is a fact about the MODULE, so it is asked once rather
    // than per site: a port's `targets` says which backends it is built for, and accepting an API
    // those backends cannot provide is the same module saying the opposite. Where they contradict,
    // every selection is REFUSED with both real knobs named and nothing drains.
    val resolutions = translated.binder.resolutions
    val offJvm      = targets - balticporter.catalog.Platform.Jvm
    val portability =
      if offJvm.isEmpty then
        resolutions.drain(PortRun.PortabilityEmitted, emittedSites) { v =>
          val at = PortRun.acceptSubject(program, v, resolutions)
          balticporter.tir.ResolutionPlan.Residue(v.api, at,
            program.symbolOf(at).map(_.fullName).getOrElse("?"),
            v.origin, s"accepted as JVM-only: ${v.api} — ${v.why}")
        }
      else
        emittedSites.foreach { v =>
          val at = PortRun.acceptSubject(program, v, resolutions)
          resolutions.selected(at, PortabilityCheck.AcceptJvmOnly).foreach { r =>
            resolutions.refuse(r, program.symbolOf(at).map(_.fullName).getOrElse("?"), v.origin,
              "targets-contradiction",
              s"this port declares `targets = ${targets.toList.map(_.toString).sorted.mkString("[", ", ", "]")}`" +
                s", and ${v.api} is exactly what ${offJvm.toList.map(_.toString).sorted.mkString(" / ")} " +
                "cannot provide — a module cannot be built for a backend and accept an API that " +
                "backend does not have. The two honest knobs are `targets` (module-wide) and " +
                "`verdictOverrides` (per API, where this port ships its own answer) [§1(b)]")
          }
        }
        emittedSites
    // the remediations are computed HERE, where the `Program` is in scope, and handed to `summary`
    // as an argument. They used to travel through a `private var` in `PortabilityCheck` keyed to
    // the exact violation list, because `summary` has no `Program` and there was no orchestrator to
    // hold the pair (CLAUDE.md §1(a) — a stopgap, documented as one).
    val fixes = Remediator.suggest(program, portability)
    locally {
      given Program = program
      CheckReport.record(PortRun.PortabilityAll, allViolations.map(_.report(PortRun.PortabilityAll)))
      CheckReport.record(PortRun.PortabilityEmitted, portability.map(_.report(PortRun.PortabilityEmitted)))
    }
    // the rule count is DERIVED here and stated nowhere else. `PortabilityCheck`'s own scaladoc
    // carried a hand-written one for long enough that it detached from the list and then escaped
    // into two commit subjects nobody can regenerate; the fix is a number the code computes, at the
    // one line a reader is looking at when they ask "against what?".
    // …and the TARGETS beside the count, because the rule set is now derived from them: "12 sites
    // against 27 rules" says nothing an operator can act on unless they can see WHICH backends this
    // port declared. A run reporting fewer sites because it narrowed its targets and one reporting
    // fewer because it fixed something are otherwise the same line.
    say(s"PORTABILITY (${targets.toList.map(_.toString).sorted.mkString("/")}): ${portability.size} " +
      s"site(s) on APIs those backends cannot provide, in EMITTED code" +
      s", against ${portabilityRules.size} rules")
    if portability.nonEmpty then say(PortReport.Kind.Portability.classification)
    println(PortabilityCheck.summary(portability, fixes))

    // ---- and the OTHER half of the same enumeration: what the declared backends need from the
    // BUILD GRAPH rather than from the source. Half the catalog's platform answers are `Depend` —
    // the API exists off the JVM, in an artifact nobody has added — and reported as an
    // unportability that finding is unanswerable, because the reader is told to remove a call one
    // `libraryDependencies` line makes correct. Three conjuncts, and two of them are structural:
    // the usage FIRED (this walk), the port declared no ALTERNATIVE (read through
    // `verdictOverrides`, so it cannot disagree with the first), and no declared dependency
    // COVERS it (the one real filter, below). Held to this module's own units by the same
    // `notShipped` predicate every other check carries (D2).
    val declaredDeps = manifest.map(_.dependencies).getOrElse(Nil)
    val allRequired  = DependencyCheck.requirements(program, targets, verdictOverrides)
    val needed       = DependencyCheck.uncovered(
                         DependencyCheck.inEmittedCode(program, allRequired, notShipped), declaredDeps)
    locally {
      given Program = program
      // TWO numbers, for `portability(all|emitted)`'s reason: the residue alone cannot distinguish
      // a dependent whose requirements all belong to its base — an honest 0 — from a walk that
      // found nothing at all, and D2's ownership filter is exactly what makes that the normal case
      // on every dependent port in this corpus.
      CheckReport.record(DependencyCheck.All,
                         DependencyCheck.report(allRequired, declaredDeps, DependencyCheck.All))
      CheckReport.record(DependencyCheck.Name, DependencyCheck.report(needed, declaredDeps))
    }
    say(s"DEPENDENCY COVERAGE: ${needed.size} site(s) needing an artifact this build does not name" +
      s" (of ${allRequired.size} the walk found)" +
      s", against ${PortabilityCheck.dependencyRulesFor(targets, verdictOverrides).size} rules" +
      s" (${declaredDeps.size} declared)")
    if needed.nonEmpty then say(DependencyCheck.Classification)
    println(DependencyCheck.summary(needed))

    // ---- the port's JDK WALL, classified — DESIGN.md §8.9 ----
    // Second consumer of the enumeration `PortabilityCheck` just used, with no new traversal. The
    // EMITTED lane, held to this module's own units by the same `notShipped` predicate for the same
    // measured reason (ENGINE-LIMITS D2): a dependent's program holds its base's units, and a
    // dependency declared inside one of those belongs to the base.
    //
    // `ran` is the difference between a demand and an offer, and it is the RUN that knows it: with
    // the retyping phase in the pipeline an unmapped member on a retyped owner is a hole the phase
    // MADE; with the phase absent — which noise4j chooses deliberately — the same member is JDK code
    // the port KEPT, and the row says only that a mapping exists if the port wants it.
    val externalAll     = ExternalUsage.all(program).filterNot(r => program.owns(r.symbol))
    val externalEmitted = ExternalUsage.external(program, notShipped)
    val jdkMapping      = CollectionsTransform.jdkMapping(
      ran = effectivePhases.exists(_.isInstanceOf[CollectionsTransform]))
    val jdkClassified   = JdkSurfaceCheck.classify(externalEmitted, jdkMapping)
    val jdkFindings     = JdkSurfaceCheck.check(program, externalEmitted, checkedUnits, jdkMapping)
    CheckReport.record(PortRun.JdkSurface, jdkFindings.map(_.report))
    say(s"JDK SURFACE (external java.* members this port still calls): " +
      s"${jdkClassified.size} classified, ${jdkFindings.size} unresolved")
    println(JdkSurfaceCheck.summary(jdkClassified, jdkFindings.count(_.disposition.label == "kept-iterable")))
    JdkSurfaceCheck.classifications(jdkFindings).foreach(c => say("  " + c))
    jdkFindings.take(20).foreach(f => println("  " + f.render))
    if jdkFindings.sizeIs > 20 then println(s"  … ${jdkFindings.size - 20} more (see findings.tsv)")
    // THE THREE IDIOM LANES, recorded UNCONDITIONALLY and beside the JDK wall on purpose: this is
    // `JdkSurface`'s wiring, verbatim, because it carries the same argument. A port with no idiom
    // phase records three rows of ZERO, and a run that asked nothing is otherwise indistinguishable
    // from a run whose recording was skipped.
    //
    // The DATA comes from the phases (`translated.idioms`), never from a second walk here: a check
    // that re-derived "would this have converted" would be a second answer to the phase's own
    // question, free to disagree with it (§4.6; `ENGINE-LIMITS.md` K2.5 is the measured shape of
    // that disagreement).
    // …scoped to THIS MODULE's own declarations (`ENGINE-LIMITS.md` D2). A dependent's model
    // CONTAINS its base's units, so an idiom phase considers the base's sites too and republishing
    // those puts a module's own rows in a minority in its own report — measured on the first run of
    // these lanes, where five dependents each reported the libGDX base's identical 24 convertible
    // SAM sites as their own. Filtered on the JAVA PATH of the units this run CONVERTS, which is the
    // same partition every other owner question in this file uses.
    val ownPaths = checkedUnits.map(u => PortRun.real(java.nio.file.Paths.get(u.origin.javaPath)).toString).toSet
    val ownIdioms = new IdiomLog
    ownIdioms.recordAll(translated.idioms.all.filter(c =>
      ownPaths.contains(PortRun.real(java.nio.file.Paths.get(c.origin.javaPath)).toString)))
    IdiomCheck.Lanes.foreach(l => CheckReport.record(l, IdiomCheck.findings(ownIdioms, l)))
    println(IdiomCheck.summary(ownIdioms,
      effectivePhases.collect { case p: balticporter.tir.IdiomPhase => p.idiomKinds }.flatten.toSet))
    IdiomCheck.refusalsByGuard(ownIdioms).foreach(r => say(r))
    // …and the ARTIFACT, both lanes. Gated on the artifact layer without exception (§5.1): with
    // reporting off the report directory falls back to `<cwd>/port-report/…`, and a forked test's
    // cwd is the subproject.
    if CheckReport.enabled then
      val p = ExternalUsage.write(CheckReport.runDir, externalAll, externalEmitted, CheckReport.relativise)
      say(s"external surface: ${externalEmitted.size} emitted / ${externalAll.size} program-wide -> $p")

    val renameReport = PackageRenameTransform.check(program, renamePhase.fold(renames)(_.upstreamTable))
    if renames.nonEmpty then
      say(s"package rename (verified AFTER the phase — every prefix must now be unmatched):")
      println(renameReport.render)

    // ---- DECISION PROVENANCE, BEFORE emission ----
    // The emitter RENDERS these as porter notes beside the code they explain (CLAUDE.md §4.57's
    // note grammar), so every decision this run makes has to be in the log before a byte is
    // written. That is the whole reason this block sits above the emission loop rather than after
    // it, where it used to: a record written afterwards can describe the output and cannot be part
    // of it.
    //
    // The phases recorded theirs while the pipeline ran; the run's own non-phase deciders — the
    // substitution manifest, the injection copy, the constructor funnel, the emitter's own
    // renaming passes — record here, into the SAME log, because the question an investigating
    // agent asks does not care which layer answered it.
    val plan = RuntimePlan.of(effectivePhases, runtimeMode)
    // What this port SHIPS as ready-made Scala. Computed here rather than beside the copy loop
    // because the injection decisions are notes on the copied files, and a note cannot be written
    // after the file it belongs in.
    val injectedSources: List[(String, String)] = Substitutions.injectedSources(ownSubs.inject)
    val foreignDecisions = recordRunDecisions(translated, injectedSources, plan)
    translatedDecisions = translated.decisions.all

    // ---- determinism, with the notes in place ----
    // Run AFTER the decisions, because a note is emitted text: comparing two emissions of which
    // only one could see the run's decisions would report every noted member as a violation.
    verifyDeterminism(translated, injectedSources, plan)

    // ---- THE EMISSION GATE (`DESIGN.md` §6.4) -------------------------------------------------
    //
    // An OPEN marker says the engine has no faithful Scala for a construct this port uses. §3.4's
    // stance is that such a thing is fatal and never silently best-effort, so the gate runs BEFORE
    // a byte is written and the deliverable tree is simply not written. It is deliberately not a
    // finding-with-a-baseline: a baselined open marker is a construct somebody accepted once and
    // nobody looks at again, which is the shape §5.1 says rots.
    //
    // The gate reads the PROGRAM rather than the emitted text, because the emitted text of a marked
    // unit is a `compiletime.error` — a compile failure downstream, in another repository, with the
    // engine's diagnosis in a string. That is the right last resort and the wrong first one.
    //
    // Best-effort mode is the escape hatch and it is escape-shaped: the output moves to its own
    // directory, carries a sentinel, and the run ends nonzero.
    // …over `checkedUnits`, which is what the run WRITES. Over `emitOrder` the gate refused a port
    // for a marker inside a type the manifest DROPS — the remediation the gate itself prints — and
    // the `markers` lane, scoped to the emitted units, called the same run clean (see [[isDropped]]).
    val openMarkers = MarkerCheck.openMarkers(program, checkedUnits)
    val emitDir = if bestEffort && openMarkers.nonEmpty then bestEffortDir else outDir
    // …and the DELIVERABLE TREE GOES, on both refusing paths, BEFORE the message is printed.
    // "Nothing was written" is a true statement about THIS run and says nothing about the last one:
    // `outDir` is `src_managed/<config>/scala`, a build product the consumer's `sourceGenerators`
    // compiles whatever produced it. Left in place, a refusal ships the PREVIOUS run's port with the
    // gate's own diagnosis in the log beside it, and the two never meet because the compile
    // succeeds. Best-effort wipes it for the same reason and one more: its degraded tree is a
    // diagnostic, and a stale deliverable beside it is the only thing that could be mistaken for the
    // real one.
    if openMarkers.nonEmpty then wipe(outDir)
    if openMarkers.nonEmpty && !bestEffort then
      val head = openMarkers.take(10).map { s =>
        s"    ${s.ownerFqn} — ${s.marker.kind.label}${s.marker.diff.fold("")(d => s" [$d]")}: " +
          s"${s.marker.what}  (${s.marker.origin.javaPath}:${s.marker.origin.line})\n" +
          s.marker.kind.remedies.map(r => s"        ${r.render}\n").mkString
      }.mkString
      sys.error(
        s"[$label] EMISSION REFUSED: ${openMarkers.size} open unportability marker(s). Nothing was " +
          s"written, and any tree a previous run left at $outDir was REMOVED.\n$head" +
          (if openMarkers.size > 10 then s"    … and ${openMarkers.size - 10} more\n" else "") +
          "  Close them in the engine, drop the declarations that use them and inject replacements, " +
          "or re-run with best-effort emission to inspect the degraded output (DESIGN.md §6.4). " +
          "A port that ships an approximation it cannot name is the failure this gate exists for.")

    // ---- emission ----
    wipe(emitDir)
    Files.createDirectories(emitDir)
    if emitDir != outDir then
      // the SENTINEL. A degraded tree that looks like a deliverable one is the single thing this
      // mode must never produce, and a directory name is not enough — a directory gets copied.
      Files.writeString(emitDir.resolve("BALTICPORTER-BEST-EFFORT"),
        s"This tree is BEST-EFFORT output (DESIGN.md §6.4) and MUST NOT SHIP.\n" +
          s"${openMarkers.size} region(s) are not a faithful translation; each is fenced in the " +
          s"file that contains it and named in that file's banner.\n" +
          s"The deliverable tree for this port is $outDir; this run did not write it, and it " +
          s"removed whatever a previous run had left there.\n")

    var written = 0
    var dropped = 0
    // what was actually SHIPPED, paired with the Java it came from — the input to the trivia check
    // below, which compares text against text and so must see exactly the files that were written.
    val shipped = collection.mutable.ListBuffer.empty[TriviaCheck.Unit]
    // every symbol this run EMITS a declaration for, and the text of every file it writes — the two
    // inputs `NoteCoverageCheck` joins on. Collected here, at the one place that knows what was
    // shipped, rather than re-derived from the filesystem afterwards: an injected replacement is
    // also on disk and is not something the emitter was ever asked to note.
    val emittedSubjects = collection.mutable.Set.empty[SymId]
    val writtenTexts    = collection.mutable.ListBuffer.empty[(String, String)]
    translated.emitOrder.foreach { u =>
      val full = program.symbolOf(u.symbol).map(_.fullName).getOrElse("Unit")
      // Substitutions.dropTypes: PARSED (so every reference to it still resolves) but NOT emitted —
      // the injected replacement supplies this FQN instead. Asked through [[isDropped]], which is
      // where the tag-then-key reasoning lives and which the checks and the gate read too.
      if isDropped(program, u) then dropped += 1
      else
        val text = translated.sourceOf(u)
        write(emitDir.resolve(full.replace('.', '/') + ".scala"), text)
        shipped += TriviaCheck.Unit(PortRun.real(Path.of(u.origin.javaPath)), text)
        writtenTexts += (full -> text)
        PortRun.declaredSymbols(u, emittedSubjects)(using program)
        written += 1
    }
    // ---- the upstream NOTICE, beside the derived work (CLAUDE.md §4.57) --------------------
    // A per-file banner names the licence; for a library whose licence lives in ONE file — an MIT
    // project with no per-file headers is the ordinary case — naming it is not reproducing it, and
    // MIT's single condition is inclusion. Copied rather than embedded so the port ships the
    // upstream's own bytes.
    //
    // NOT gated on the artifact layer, deliberately: this is a licence obligation, and one that
    // held only when a diagnostic switch was on would be met by accident. What keeps it from
    // writing anywhere unexpected is the same thing that makes it a §1(b) no-op — a port that
    // declares no notice writes nothing — plus the destination, which is `src_managed/`: the
    // BUILD PRODUCT this run already owns and `clean` already removes (§5.5), never the port root,
    // where an untracked file would blur the one distinction `git status` has to keep.
    val notices = provenance.map(_.notices).getOrElse(Nil)
    notices.foreach { src =>
      // FATAL rather than skipped, like a declared `classpathFile` that is not there: a notice the
      // port meant to ship and silently did not looks exactly like one it shipped.
      if !Files.isRegularFile(src) then
        sys.error(s"[$label] provenance declares a notice file that is not there: $src. A licence " +
          "notice the port does not ship is a compliance gap no check and no build can report.")
      val dst = SbtGen.managedRoot(portRoot).resolve(src.getFileName.toString)
      Files.createDirectories(dst.getParent)
      Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
    if notices.nonEmpty then
      say(s"notice(s) shipped beside the emitted code: ${notices.map(_.getFileName).mkString(", ")}")

    // ---- the upstream SERVICE DESCRIPTORS, with BOTH namespaces moved (ENGINE-LIMITS.md P5) ----
    // The one deliverable of a port that is not `.scala`. Nothing in the pipeline could carry it: a
    // provider is constructed reflectively from OUTSIDE the program, so the closure sees no
    // instantiation and concludes correctly and uselessly that nothing has to be fixed — and with
    // the resource absent the loader finds ZERO providers, at no compile error, no check count and
    // no finding. Ungated on the artifact layer for the notices' reason above; scoped by the same
    // two things — an empty declaration writes nothing, and the destination is `src_managed/`.
    val declaredServices = manifest.map(_.serviceProviders).getOrElse(Nil)
    declaredServices.foreach { src =>
      // FATAL, `Provenance.notices`' rule exactly: a descriptor the port meant to ship and silently
      // did not looks exactly like one it shipped — and this one is worse, because the library then
      // answers "not registered" as a plausible wrong result rather than as an error.
      if !Files.isRegularFile(src) then
        sys.error(s"[$label] the manifest declares a service descriptor that is not there: $src. " +
          "A `META-INF/services` resource the port does not ship makes every `ServiceLoader.load` " +
          "find zero providers, with no compile error, no check count and no finding to say so " +
          "(ENGINE-LIMITS.md P5).")
    }
    // `emittedName` and not `packageRenames`: the run's own rename PHASE answers for `typeRenames`
    // and `subPackages` too, and a provider moved by one of those is a line a prefix map cannot
    // translate (§4.56 — never a hand-written `startsWith`).
    val descriptors = balticporter.tir.ServiceProviders.plan(declaredServices, emittedName)
    if descriptors.nonEmpty then
      val wrote = balticporter.tir.ServiceProviders.write(
        descriptors, SbtGen.managedResources(portRoot, sourceSet.configName))
      written += wrote.size
      CheckReport.record(balticporter.tir.ServiceProviders.Name,
        balticporter.tir.ServiceProviders.findings(descriptors, policySubs.dropsType,
                     renaming = manifest.exists(_.effectivePackageRenames.nonEmpty)))
      say(s"SERVICE PROVIDERS: ${descriptors.size} descriptor(s), " +
        s"${descriptors.map(_.providers.size).sum} provider line(s), rewritten into this port's namespace")
      println(balticporter.tir.ServiceProviders.summary(descriptors))

    // Support types a phase RETYPED code onto. Two feeds, one rule: what the phases DECLARE
    // (RequiresRuntime → RuntimePlan) and what a phase that cannot declare it hands over.
    written += plan.writeSources(emitDir)
    supportSources.foreach { (fqn, src) => write(emitDir.resolve(fqn.replace('.', '/') + ".scala"), src); written += 1 }

    // The MEMBER-LEVEL source map for what was just emitted — written HERE, from the emitter's own
    // recording, rather than accumulated in a process-global table and flushed by a shutdown hook.
    // Two emitters in one JVM (the determinism double-emission is one; sbt running every suite in
    // one JVM is another) shared that table and contaminated each other's map.
    writeSrcMap(translated.emitter.srcMap)

    // The COMMENTS that did not survive — including, if it ever regresses, the upstream licence
    // notice this project is obliged to reproduce (§4.57). Over the SHIPPED text against the
    // SOURCE text, so it is blind to how the trivia got there and would still fire if the whole
    // frontend harvest silently returned `Nil` (it did once; see `TriviaCheck`). Injected
    // replacements are excluded by construction: they are hand-written Scala with no Java behind
    // them, and this check compares against a Java file or reports nothing.
    //
    // THREE LANES, and the split is what makes `lost` mean something: `recovered` is what the
    // emitter's backstop had to put back (a residue), and `deliberate` is a comment documenting a
    // member this port DROPS — derived from the run's own drops through `CommentAnchor`, exactly
    // as the expected-failure ledger is derived from `dropped-types.tsv`, so the set follows the
    // manifest with nobody editing a list.
    val shippedUnits  = shipped.toList
    val triviaMembers = CommentAnchor.membersOf(program)
    val trivia        = TriviaCheck.check(shippedUnits, triviaMembers)
    val triviaFiles   = TriviaCheck.comparable(shippedUnits)
    CheckReport.record(PortRun.TriviaDropped, trivia.lost.map(_.report(PortRun.TriviaDropped)))
    CheckReport.record(PortRun.TriviaRecovered, trivia.recovered.map(_.report))
    CheckReport.record(PortRun.TriviaDeliberate, trivia.deliberate.map(_.report(PortRun.TriviaDeliberate)))
    say(s"TRIVIA (comments in the Java that did not reach the Scala): ${trivia.lost.size} lost, " +
        s"${trivia.recovered.size} recovered, ${trivia.deliberate.size} deliberate")
    if trivia.lost.nonEmpty then say(PortReport.Kind.Trivia.classification)
    println(TriviaCheck.summary(trivia, triviaFiles))

    // CHECK 1 — before injection, so a file at a dropped type's path can only be the emitter's.
    val leaked = record(PortRun.SubstitutionEmitted, SubstitutionCheck.emittedDroppedTypes(outDir, policySubs))

    // ---- injection: ready-made Scala copied verbatim (survives the wipe above) ----
    // Verbatim EXCEPT for the porter notes prepended at copy time: an injected file is the only
    // place a DROPPED TYPE's decision can be read from, because no emitted unit corresponds to a
    // type this run refuses to translate. The source file in the port's overrides directory is
    // hand-written and must stay so — the note belongs to the BUILD PRODUCT (§5.5), which is why
    // it is added on the way out and never written back.
    var injected = 0
    ownSubs.inject.filter(Files.exists(_)).foreach { root =>
      Files.walk(root).iterator().asScala
        .filter(p => p.toString.endsWith(".scala"))
        .toList.sorted
        .foreach { src =>
          val rel = root.relativize(src).toString.replace('\\', '/')
          val dst = emitDir.resolve(root.relativize(src).toString)
          Files.createDirectories(dst.getParent)
          Files.writeString(dst, injectionNotes(rel) + Files.readString(src))
          injected += 1
        }
    }
    // injected replacements bypass the TIR — scan their TEXT for the same portability rules, so a
    // hand-written shim cannot quietly reintroduce the API the substitution was meant to remove.
    val injectedViolations = ownSubs.inject.filter(Files.exists(_)).flatMap { root =>
      SubstitutionCheck.scalaSources(root).flatMap { src =>
        PortabilityCheck.inInjectedSource(root.relativize(src).toString, Files.readString(src),
                                          portabilityRules)
      }
    }
    // Recorded even with nothing to inject: a check that never names itself leaves `counts.tsv`
    // unable to tell "found nothing" from "never ran" — the one distinction the persistence layer
    // exists to keep, and the reason this is a `record` of the COMPLETE list rather than a
    // per-file increment.
    CheckReport.record(PortRun.PortabilityInjected, injectedViolations.map(_.report))
    if injectedViolations.isEmpty then say("PORTABILITY of injected replacements: clean")
    else
      say(s"PORTABILITY of injected replacements: ${injectedViolations.size} finding(s)")
      say(PortReport.Kind.InjectedPortability.classification)
      injectedViolations.foreach(v => println("  " + v.render))

    // ---- the PORT MAP: what this module did to its upstream surface, published for dependents ----
    // Written AFTER injection, so `Substituted` and `Added` are decided by what actually stands in
    // the output rather than by what policy intended. Assembly only: every input below is something
    // the run already holds (see `PortMap`'s scaladoc for the source of each field).
    //
    // A module's map is an OUTPUT and never an input to its own run — only DEPENDENTS read it.
    // Otherwise it becomes a second source of truth able to disagree with the manifest, and a port
    // stops being reproducible from sources plus policy (CLAUDE.md §5.5).
    val injectedFqns = injectedSources.map(_._1).toSet ++ plan.sources.keySet ++ plan.required ++ supportSources.keySet
    val bodyKeys: Set[String] =
      effectivePhases.collect { case m: MethodBodyTransform => m.substituted }.flatten.toSet
    val shapes = translated.emitter.emittedShapes
    // NESTED types are in the map from schema 3 on, and that is not a tidy-up. The contract's
    // constructor rows exist so a dependent can stop re-deriving a base class's primary over a
    // program the base never had (`ENGINE-LIMITS.md` D4), and a dependent extends a base's NESTED
    // class as readily as its top-level one — libGDX's `Attribute` hierarchy is exactly that. A map
    // carrying only units would answer `Unknown` for precisely the questions §8.3 exists for.
    // Dropped types are filtered out on the same rule as the units', which is why this shares one
    // expression with them rather than a second one that can drift.
    // `allClassDefs`, not a `cd.body` recursion: a class body is the type's MEMBERS, one node
    // short of java — a method-LOCAL class (`JS-C30`) stands in a member's block, and a port map
    // that omits it claims the port emitted a type it did not.
    def emittedFqns(cd: Tree.ClassDef): List[String] =
      StandardTraversal.allClassDefs(cd)(using program).flatMap(c => program.symbolOf(c.symbol).map(_.fullName))
    val portMap = PortMap.of(
      module       = label,
      engine       = balticporter.core.EngineInfo.fingerprint,
      emittedTypes = translated.emitOrder.flatMap(emittedFqns)
                       .filterNot(f => f.isEmpty || policySubs.dropsType(f)),
      srcMap       = translated.emitter.srcMap,
      dropTypes    = policySubs.dropTypes,
      dropMethods  = policySubs.dropMethods,
      injectedFqns = injectedFqns,
      bodyKeys     = bodyKeys,
      renames      = renames,
      // The map fingerprints the JAVA it was derived from, so a dependent can tell that the base's
      // sources moved under it (design risk R1) instead of reading an entry that describes a run
      // that no longer exists. `SrcMap` records each member's Java path relative to THIS root.
      sourceRoot   = Some(frontend.sourceRoot),
      // ---- schema 3: THE BASE-SURFACE CONTRACT (`DESIGN.md` §8.3) ----------------------------
      // What this module EMITTED, taken from the emitter's own recording — never re-derived here.
      // A second derivation would be a third answer free to disagree with both the emission and the
      // consumer, which is the drift the contract exists to end.
      typeShapes    = shapes.renderedTypes,
      memberShapes  = shapes.renderedMembers,
      // …and the THIRD fingerprint. `engine=` and `sources=` both stay put when the base's MANIFEST
      // changes, and the payload above is full of policy outcomes — so without this the map is
      // `Fresh` and WRONG, which is D4's failure re-entering through the artifact built to prevent
      // it. The same value `ManifestAgreement` compares, not a new derivation (§1.5).
      policy        = surfacePolicyFingerprint,
      // …and the members this run REFUSED. A policy drop is already a `Dropped` row; an engine
      // refusal was published nowhere at all — see `refusedMembers` for what that cost.
      refusedMembers = refusedMembers(program, translated),
    )
    // …and written only when the ARTIFACT LAYER IS ON, like every other file this run produces.
    //
    // Unconditional, this wrote `<cwd>/port-report/<sun.java.command>/run-latest/port-map.tsv` for
    // any run that had not opted in — and under a forked test JVM the working directory is the
    // SUBPROJECT's, so the engine's own suites published maps INTO THE REPOSITORY
    // (`runner/port-report/…`, and once a committed `port-report/jar/` holding `PortRunSpec`'s
    // fixture). A `git status` that cannot distinguish a decision from an artefact is the one thing
    // §5.5 says the measurement discipline depends on.
    //
    // Gating it here rather than fixing the callers is not convenience: a map nobody can DISCOVER
    // is useless, and `PortMap.discover` reads `CheckReport.dir`'s parent — so a run with no report
    // directory has nowhere to publish TO, and every spec that ever calls `execute()` would
    // otherwise have to remember the same wrapper. `ManifestSpec` already documents this as the
    // expected behaviour ("a unit-test JVM has CheckReport off, so no PortRun here publishes a port
    // map"); it is now true rather than nearly true.
    val mapPath = Option.when(CheckReport.enabled)(PortMap.write(CheckReport.runDir, portMap))
    say(s"port map: ${portMap.types.size} type(s), ${portMap.members.size} member(s)" +
      mapPath.fold(" (not published: the artifact layer is off)")(p => s" -> $p"))

    // ---- DECISION PROVENANCE: written out (it was RECORDED before emission, above) ----
    // …plus the ones the EMITTER could only make while rendering: preview mode's `Unrenderable`
    // rows. They cannot travel with `ownDecisions`, which is a value fixed at construction, and
    // they are added here rather than dropped because a refusal the port declared IN THE OUTPUT
    // must also be in the artifact — the two are read by different people.
    translated.decisions.recordAll(translated.emitter.emissionDecisions)
    writeDecisions(translated.decisions, foreignDecisions)

    // ---- E8: did every decision that must carry a note actually get one? ----
    // Beside the other checks rather than inside the emitter, for the reason `record` gives: the
    // orchestrator is the layer that knows a run is happening, and this check needs BOTH the run's
    // decisions and the text that was written. Deliberately NOT in `RequiredChecks` — it registers
    // on every run, but so do the collection checks' siblings, and the wiring living here is what
    // makes it unskippable (see the comment on `RequiredChecks`).
    val noteFindings = NoteCoverageCheck.check(
      decisions = translated.decisions.all,
      printed   = translated.emitter.notesPrinted,
      emitted   = emittedSubjects.toSet,
      texts     = writtenTexts.toList,
    )
    CheckReport.record(NoteCoverageCheck.Name, noteFindings.map(_.report))
    say(s"PORTER NOTES (decisions with no note in the code, and notes with no decision): ${noteFindings.size}")
    if noteFindings.nonEmpty then say(NoteCoverageCheck.Classification)
    println(NoteCoverageCheck.summary(noteFindings, translated.emitter.notesPrinted.size))

    // ---- §4.4: a translated JUMP that a translated `catch` would swallow ----
    // Here rather than beside the omission check for the same reason the note coverage is: the
    // question needs BOTH the trees and what the emitter did with them. The crossings are found
    // from the trees (`BreakCatchCheck` walks with `StandardTraversal`); the emitter contributes
    // only the set of sites it guarded, so the two disagree exactly when the emitter's boundary
    // state missed a shape this walk can see. Over `checkedUnits` (ENGINE-LIMITS D2).
    val breakCatches = BreakCatchCheck.check(program, checkedUnits, translated.emitter.breakGuards)
    CheckReport.record(BreakCatchCheck.Name, breakCatches.map(_.report))
    say(s"BREAK-IN-CATCH (jumps a translated handler would swallow, unguarded): ${breakCatches.size}")
    if breakCatches.nonEmpty then say(BreakCatchCheck.Issue.classification(BreakCatchCheck.Issue.UnguardedJump))
    println(BreakCatchCheck.summary(breakCatches))

    // ---- §4.4: a try-with-resources whose resources nothing closed ----
    // Beside the jump check for the same reason: the question needs BOTH the trees (which `try`s
    // carry resources) and what the emitter did with them (which it lowered). Over `checkedUnits`
    // (ENGINE-LIMITS D2). Reports 0 on every corpus port today — no upstream tree writes one —
    // which is exactly why the drop survived the life of the TIR backend unnoticed.
    val tryResources = TryResourceCheck.check(program, checkedUnits, translated.emitter.resourceLowerings)
    CheckReport.record(TryResourceCheck.Name, tryResources.map(_.report))
    say(s"TRY-WITH-RESOURCES (resources the emission never closed): ${tryResources.size}")
    if tryResources.nonEmpty then say(TryResourceCheck.Issue.classification(TryResourceCheck.Issue.UnloweredResource))
    println(TryResourceCheck.summary(tryResources))

    // ---- §4.4: a reference-typed `switch` that falls out on null where java throws ----
    // The fall-out arm's own defect read at the other selector value, and the same two-source
    // question: the reference-typed switches come from the trees, the guarded set from the emitter.
    val switchNulls = SwitchNullCheck.check(program, checkedUnits, translated.emitter.switchNullGuards)
    CheckReport.record(SwitchNullCheck.Name, switchNulls.map(_.report))
    say(s"SWITCH-NULL (reference-typed switches that fall out where java NPEs): ${switchNulls.size}")
    if switchNulls.nonEmpty then say(SwitchNullCheck.Issue.classification(SwitchNullCheck.Issue.NullFallsOut))
    println(SwitchNullCheck.summary(switchNulls))

    // ---- JS-E06: java's UNBOXING CONVERSION emitted as a scala type ASSERTION ----
    // A lane that reads 0 on every corpus port, and kept for `try-resource`'s reason: the frontend
    // answers this cell from the java, so the only way to reach it is a PHASE that retypes an
    // operand after the frontend decided — a path nobody has exercised, which is exactly the kind
    // that gets dropped whole for the life of a backend. Over `checkedUnits` (ENGINE-LIMITS D2).
    val castConversions = CastConversionCheck.check(program, checkedUnits)
    CheckReport.record(CastConversionCheck.Name, castConversions.map(_.report))
    say(s"CAST CONVERSION (java's unbox emitted as a scala assertion): ${castConversions.size}")
    castConversions.map(_.issue).distinct.foreach(i => say(CastConversionCheck.Issue.classification(i)))
    println(CastConversionCheck.summary(castConversions))

    // ---- JS-G41: java's HEAP POLLUTION, carried over with no scala warning and no annotation ----
    // A COUNTER and not a repair, which is the whole shape of this row: the port reproduces java's
    // unsoundness exactly, so there is nothing to translate — what has no scala image is javac's
    // warning at a non-reifiable vararg and the `@SafeVarargs` that answers it. Over `checkedUnits`
    // (ENGINE-LIMITS D2), through the same predicate the emitter's consult reads.
    // …minus the rows a SELECTION already answered (`DESIGN.md` §8.16). `acknowledge` is not
    // emission-affecting, so the declaration it answered is still standing where this walk goes:
    // without the drain the run would report the residue BESIDE the `remediation(resolved)` row
    // saying it was answered, and the lane could not fall by what `resolved` gained (§5).
    val heapPollution = HeapPollutionCheck.check(program, checkedUnits, translated.binder.resolutions)
    CheckReport.record(HeapPollutionCheck.Name, heapPollution.map(_.report))
    say(s"HEAP POLLUTION (unchecked varargs carried over from java): ${heapPollution.size}")
    heapPollution.map(_.issue).distinct.foreach(i => say(HeapPollutionCheck.Issue.classification(i)))
    println(HeapPollutionCheck.summary(heapPollution))

    // ---- JS-C22 / JS-C23: java resolves an overload in THREE PHASES and scala in ONE ----
    // A RISK COUNTER and explicitly not a resolver (`ENGINE-LIMITS.md` T17): predicting which member
    // scala picks means modelling its resolution well enough to disagree with javac about a program
    // neither compiler rejects. What is counted is the calls where the two rules CAN differ, and the
    // population is derived from JLS 15.12.2's own phase boundaries — boxing, varargs, and the
    // generic tie-break — rather than from "this call is overloaded". Over `checkedUnits`
    // (ENGINE-LIMITS D2), through the same predicate the emitter's two consults read.
    // …minus the rows a SELECTION already answered (`DESIGN.md` §8.16), matched at the SITE: a
    // selection broadcasts across a member, but `ascribe-javac-choice` REFUSES where javac's
    // alternative cannot be written, so one member may have one call answered and one not.
    val overloadRisk = OverloadRiskCheck.check(program, checkedUnits, translated.emitter.overloads,
                                               translated.binder.resolutions)
    CheckReport.record(OverloadRiskCheck.Name, overloadRisk.findings.map(_.report))
    say(s"OVERLOAD RISK (calls whose candidate set spans a java resolution phase): ${overloadRisk.findings.size}")
    overloadRisk.findings.map(_.issue).distinct.foreach(i => say(OverloadRiskCheck.Issue.classification(i)))
    println(OverloadRiskCheck.summary(overloadRisk))

    // ---- §4.4: a `static { }` block emitted into a companion that nothing initialises ----
    // The same two-source shape once more: the census of `static { }` blocks comes from the trees,
    // the set of triggers actually attached from the emitter — and the FORM from the emitter too,
    // because the all-static class-to-`object` collapse is decided inline and exists nowhere else
    // (`ENGINE-LIMITS.md` K22).
    val classInits = ClassInitTriggerCheck.check(program, checkedUnits,
      translated.emitter.forcedClassInits, translated.emitter.emittedShapes.types.get)
    CheckReport.record(ClassInitTriggerCheck.Name, classInits.map(_.report))
    say(s"CLASS-INIT TRIGGER (`static { }` blocks nothing initialises): ${classInits.size}")
    classInits.map(_.issue).distinct.foreach(i => say(ClassInitTriggerCheck.Issue.classification(i)))
    println(ClassInitTriggerCheck.summary(classInits))

    // ---- §6.2's CONSERVATION LAW: a refusal may be DISCHARGED, never erased ----
    // Beside the three above and recorded the same way, because it asks the same two-source
    // question — what the FRONTEND minted against what the pipeline left — and because a mechanism
    // whose failure nothing counts is one that fails silently (§3). The failure it exists for is
    // invisible to every other number here: a phase that DELETES a marked subtree removes the
    // finding rather than the problem, the emitted code compiles, the error count does not move,
    // and no member digest changes because that member was going to be rewritten anyway.
    //
    // Over `checkedUnits` (ENGINE-LIMITS D2) on both sides. Deliberately NOT in `RequiredChecks`,
    // for the reason stated there: it records on EVERY run and the wiring living here is what makes
    // it unskippable, exactly as for `porter-notes`, `break-catch`, `try-resource` and
    // `switch-null`.
    // ONE inventory, read three times. It is a full scan of every term the run owns, and asking
    // for it per consumer would triple that walk over the largest port for no new information.
    val markerInventory = MarkerCheck.inventory(program, checkedUnits)
    val markers  = MarkerCheck.check(translated.parsed, program, checkedUnits)
    val resolved = markerInventory.count(!_.marker.state.isOpen)
    CheckReport.record(MarkerCheck.Name, markers.map(_.report))
    say(s"MARKERS (constructs with no faithful Scala): ${markers.size}")
    if markers.nonEmpty then say(MarkerCheck.Classification)
    println(MarkerCheck.summary(markers, resolved))
    writeMarkers(program, markerInventory)

    // ---- the DIFFERENCE CATALOG's four coverage lanes, and the REGISTRY's own (`DESIGN.md` §2.8) --
    //
    // Four and not one, following the `trivia(|recovered|deliberate)` precedent exactly: `lost = 0`
    // is a bar a run could hold by recovering everything, and here `unreached = 0` is a bar a run
    // could hold by declaring every row `Unmechanised`. So the positive (`consulted`), the two
    // residues (`unreached`, `unmechanised`) and the work list (`undischarged`) are reported apart,
    // and every one of them is in `RequiredChecks` — a number that reaches stdout and not
    // `findings.tsv` fails the run.
    //
    // `catalog(uncited)` rides beside them and is NOT a coverage lane: it counts registry rows with
    // no Scala-side normative citation. It is here because `counts.tsv` is what a baseline diffs and
    // that number was a `println` in one spec, beside an assertion (`uncited <= all`) that no
    // registry could ever fail. It is never asserted on — a spec failing on it is a spec somebody
    // silences by inventing a citation.
    //
    // `catalog(refused)` is deliberately NOT a lane at all: it is the `markers` lane above, which
    // already records a `Tree.Unportable` mint with its catalog id. Two lanes counting one thing is
    // how two numbers start disagreeing.
    val catalogLog     = translated.catalog
    val catConsulted   = CatalogCheck.consulted(catalogLog)
    val catUnreached   = CatalogCheck.unreached(catalogLog)
    val catUnmech      = CatalogCheck.unmechanised
    val catUncited     = CatalogCheck.uncited
    val catUndischarged = CatalogCheck.undischargedAll(catalogLog)
    CheckReport.record(CatalogCheck.Consulted, catConsulted)
    CheckReport.record(CatalogCheck.Unreached, catUnreached)
    CheckReport.record(CatalogCheck.Unmechanised, catUnmech)
    CheckReport.record(CatalogCheck.Uncited, catUncited)
    CheckReport.record(CatalogCheck.Undischarged, catUndischarged)
    say(s"CATALOG: ${catConsulted.size} row(s) consulted, ${catUnreached.size} mechanised and " +
      s"unreached, ${catUnmech.size} not instrumented, ${catUncited.size} without a scala-side " +
      s"citation, ${catUndischarged.size} undischarged")
    if catUndischarged.nonEmpty then
      say(CatalogCheck.Classification)
      catUndischarged.take(10).foreach(f => say("  " + f.render))
    writeCatalog(catalogLog, translated.cacheHits)

    // ---- the CONTEXT boundary, RECORDED: the four the phase drew (collected above) plus the one
    // only the emitted text can show — a `using` clause the threading attached to a class's
    // constructors that the emitted type does not carry (`ENGINE-LIMITS.md` CT5).
    //
    // The loss list is the EMITTER's own recording of the header it wrote, for the reason
    // `NoteCoverageCheck` joins on `notesPrinted` rather than re-reading the files: the question is
    // what the emitter DID, and re-deriving it from the plan would have passed on the day CT4
    // flattened a clause into a value parameter. Reported even when no globals phase is in the
    // pipeline, because a loss list that is non-empty is by definition a clause somebody attached —
    // and a check that could only fire when the usual phase ran would be silent for the next one.
    //
    // …held to what this run actually WROTE, which is not the same as what the emitter RENDERED:
    // the determinism twin re-renders every unit in `emitOrder`, dropped types included, and a
    // finding about a type whose replacement is injected Scala describes nothing on disk. The same
    // filter `checkedUnits` applies to the other four, expressed through the set the write loop
    // built (`ENGINE-LIMITS.md` D2 for the dependent half: a base's unit is not in it).
    val clauseLosses = translated.emitter.contextClauseLosses.filter(l => emittedSubjects(l.subject))
    // the key a reader edits: the holder whose threading this is. Absent (`-`) when no phase in
    // this pipeline declares one, which is the shape a future clause-attaching phase would have.
    val holderKey = contextPhases.flatMap(_.holders).map(_.holder).distinct.sorted match
      case Nil => "-"
      case hs  => hs.mkString(",")
    val lostClauses = clauseLosses.map { l =>
      ContextSeamCheck.Finding(ContextSeamCheck.Kind.LostClause, l.fqn, holderKey,
        s"its constructors take a context clause and the emitted `${l.form}` does not carry one, " +
          "so nothing in its body can summon it", l.origin, l.subject)
    }
    if contextPhases.nonEmpty || lostClauses.nonEmpty then
      // `contextSeams` has ALREADY been drained, up where the phase's own seams are collected — see
      // there for why the drain cannot wait for this line. `lostClauses` is appended undrained and
      // that is not an omission: no remedy targets `lost-clause`, because it is an engine bug in the
      // constructor region reachable from no manifest key (`ENGINE-LIMITS.md` CT5).
      val ss = contextSeams ++ lostClauses
      CheckReport.record(ContextSeamCheck.Name, ss.map(_.report))
      say(s"CONTEXT SEAMS (where the context threading stopped): ${ss.size}")
      println(ContextSeamCheck.summary(ss))

    // ---- what a remedy SELECTION actually did — the OTHER half of `remediation` ----
    //
    // One lane and not two, deliberately. `remediation` already carries `Remediator`'s suggestions —
    // the manifest line an operator would paste — and an APPLIED resolution is that loop closed: the
    // engine pasted it. It is also already a `RequiredChecks` member, so a run that stopped
    // recording resolutions fails exactly the way a run that stopped recording suggestions does,
    // which a new top-level check would only have got by somebody remembering to add it to that set.
    //
    // The kind column is what keeps them apart (`resolved`), and it is what makes the accounting
    // readable: a baseline diff must show `remediation(resolved) 0->N` beside the refusal lane the
    // remedy drained falling by exactly N. That is `CLAUDE.md` §5's trivia-family rule one artifact
    // over — a number that only ever grows says nothing about what it was drawn from.
    //
    // …RECORDED HERE, after every lane, because the ledger is read ONCE and a lane drained after
    // this line contributes nothing to it — silently, with the drained rows simply missing from the
    // count that is supposed to balance them. The DECISION half has the tighter deadline of the two
    // (`recordRunDecisions`, which must run before a byte is emitted, because a porter note IS
    // emitted text), so a drain is bound by that one; this position costs nothing and removes the
    // second way to get it wrong.
    val appliedRemedies = translated.binder.resolutions.all
    val refusedRemedies = translated.binder.resolutions.refusals
    CheckReport.record(PortRun.Remediation,
      Remediator.reports(fixes) ++ appliedRemedies.map(_.finding) ++ refusedRemedies.map(_.finding))
    if appliedRemedies.nonEmpty || refusedRemedies.nonEmpty then
      say(s"RESOLUTIONS: ${appliedRemedies.size} applied, draining " +
        s"${appliedRemedies.map(_.drained).sum} row(s); ${refusedRemedies.size} declined")
      appliedRemedies.foreach(a => println("  + " + a.render))
      // the refusal POPULATION, one row per declined site naming its GUARD — §3's rule at a menu.
      refusedRemedies.foreach(a => println("  ! " + a.render))

    // CHECK 2 — over the FINAL tree.
    val danglingSubs = record(PortRun.SubstitutionDangling, SubstitutionCheck.dangling(outDir, ownSubs))
    if ownSubs.dropTypes.nonEmpty && danglingSubs.isEmpty then
      say(s"substitutions: ${ownSubs.dropTypes.size} dropped types verified removed from the final code")

    // ---- policy: every (b) seam this run holds, ALL OF IT FROM THE BINDING ----
    // Only the policy THIS module declares — its own drops, and the phases in its own `surface`.
    //
    // A §1(b) finding says "fix this key in the library's manifest", and an INHERITED key lives in
    // the base's manifest: reporting it here tells every dependent module about a mistake none of
    // them can fix, and one bad key in a library with eighteen modules becomes eighteen findings
    // that all mean the same thing. The inherited half is not unchecked — it is checked more
    // precisely by `ManifestAgreement`, which says which base the key came from and whether it
    // fired HERE (`InheritedKeyNeverFired`).
    //
    // The DROPS' half no longer comes from a mutable tally on `Substitutions` accumulated as the
    // frontend consulted it; it comes from the same binder every phase reads, which also
    // distinguishes an EXTERNAL-only match from a typo, and says WHY. `policy-binding` measured
    // the two answers against each other on all thirteen lanes before this replaced that one.
    // …and a MERGED phase is read through the instance that actually RAN. A phase whose policy the
    // fold composed with a base's (DESIGN.md §8.13) leaves this module's own declared instance
    // bound to nothing at all, so reading that one reports NOTHING — a typo'd key silently
    // no-oping, which is the one thing `PolicyReport` exists to close. Resolve each own-declared
    // phase to the effective instance that absorbed it; with no merge every phase resolves to
    // itself and this is the identity.
    val ownPhases: List[Phase] = manifest match
      case Some(m) =>
        val effective = m.effectiveSurface
        m.surface.map(p =>
          if effective.exists(_ eq p) then p else effective.find(_.name == p.name).getOrElse(p))
      case scala.None => phases
    // The merged instance holds the BASE's keys too, and a §1(b) finding must name a key this
    // module can fix — the same rule the drops below follow. Scoped by the SUBJECT the fold
    // recorded this manifest as contributing; absent for an unmerged phase, which means no filter.
    val ownSurfaceKeys: Map[String, Set[String]] = manifest.map(_.surfaceFold.ownKeys).getOrElse(Map.empty)
    val ownKeys: Set[String]   = manifest.map(_.ownKeys).getOrElse(subs.keys)
    val ownPhaseNames: Set[String] = ownPhases.map(_.name).toSet
    val dropFindings = PolicyReport(PolicyReport.fromBindings(translated.binder.bindings).findings
      .filter(f => f.phase == "substitutions" && ownKeys(f.key)))
    // The RENAME phase is never in `ownPhases` — the run appends it itself, because its position
    // is an obligation no `runsAfter` can state (§4.56) — so its per-TYPE keys would otherwise be
    // the one (b) seam with no policy report at all. Held to THIS module's own keys by the same
    // rule the drops are: an inherited type rename that matched nothing here is
    // `ManifestAgreement`'s to report, and it says which base it came from.
    val ownRenameKeys: Set[String] = manifest match
      case Some(m)    => m.typeRenames.keySet ++ m.subPackages.keySet ++ m.flattenNestedTypes ++ m.allowPackageSplit
      case scala.None => typeRenames.keySet ++ subPackages.keySet ++ flattenNestedTypes ++ allowPackageSplit
    val renameFindings = PolicyReport(
      renamePhase.toList.flatMap(_.policyReport.findings).filter(f => ownRenameKeys(f.key)))
    // …and the per-location SELECTIONS. Two halves, from two places, because they are two questions:
    // whether the KEY named a declaration is the binder's (it recorded one row per selection under
    // `Resolution.Seam`, so `Ambiguous`, `NeverMatched` and `Malformed` arrive with everything
    // else's), and whether the SELECTION did anything is the plan's — a key can bind perfectly and
    // be inert, which no binding can say. Held to THIS module's own keys by the rule the drops
    // follow: an inherited selection that matched nothing here is `ManifestAgreement`'s to report,
    // and it says which base declared it.
    val ownResolutionKeys: Set[String] =
      manifest.map(_.resolutions.keySet).getOrElse(Set.empty)
    val resolutionFindings =
      PolicyReport(PolicyReport.fromBindings(translated.binder.bindings).findings
        .filter(f => f.phase == balticporter.tir.Resolution.Seam && ownResolutionKeys(f.key))) ++
        PolicyReport(PolicyReport.fromResolutions(translated.binder.resolutions.troubles).findings
          .filter(f => ownResolutionKeys(f.key)))
    val policy = dropFindings ++ renameFindings ++ resolutionFindings ++ PolicyReport(
      PolicyReport.from(ownPhases.collect { case p: PolicySource if ownPhaseNames(p.name) => p })
        .findings.filter(f =>
          ownSurfaceKeys.get(f.phase).forall(_.contains(balticporter.core.MergeablePolicy.subjectOf(f.key)))))
    CheckReport.record(PortRun.Policy, policy.findings.map { f =>
      CheckReport.Finding(PortRun.Policy, f.issue.label, f.phase, f.setting, 0, s"${f.key} — ${f.detail}")
    })
    say(s"POLICY (declared keys that never fired): ${policy.findings.size}")
    if policy.nonEmpty then println(policy.render)

    // ---- the question every RETYPING phase owes, asked of the pipeline (`Rewrite`) ----
    //
    // LAST of the checks, and that position is the check itself: its second finding asks whether a
    // lane a phase NAMES actually recorded in this run, and a lane that has not been called yet has
    // not. Recorded unconditionally — including the empty result, and including a pipeline with no
    // retyping phase in it — for `RequiredChecks`'s own reason: a port whose phases all account and
    // a port whose check never ran are one silence otherwise. The retyped sets come from
    // `Pipeline.runTraced`'s observation, so nothing here trusts a phase's account of its own reach.
    // `Option`, never the bare set: with the artifact layer off nothing has recorded, and reading
    // that as an answer would report every accounted phase as unwired — a finding manufactured by a
    // diagnostic switch. See the check's own doc.
    val rewriteFindings = RewriteCallSitesCheck.check(
      translated.rewrites, Option.when(CheckReport.enabled)(CheckReport.snapshot().keySet))
    CheckReport.record(RewriteCallSitesCheck.Name, rewriteFindings.map(_.report))
    say(s"REWRITE CALL SITES (retyping phases that answer nothing): ${rewriteFindings.size}")
    println(RewriteCallSitesCheck.summary(rewriteFindings, translated.rewrites, program))

    // Every check this run believes it ran must ALSO have registered itself with the persistence
    // layer, or a number reaches the operator's terminal and never reaches `findings.tsv`. That is
    // the same class of gap as a check nobody invoked, one layer down, and it is invisible without
    // this assertion.
    verifyRecorded()

    // ---- the generated build: OPT-IN, and the only write above that leaves `outDir` ----
    // Everything before this point lands under `outDir` or, gated on the artifact layer, under the
    // report directory — so `project = None` (the default) makes this run a pure source emitter, for
    // a consumer whose build already exists and whose `build.sbt` and `.gitignore` are its own
    // decisions. One gate, at the one call, for the reason §5.1 gives about artifact writes: a
    // wrapper every caller must remember is a wrapper one caller will not.
    //
    // It emits the BUILD and no sources. The vendored runtime was written above, into `outDir`,
    // which is the only place that knows this run's `sourceSet`; `emitPort` used to write it too,
    // into `managedMain` unconditionally, so a `sourceSet = Test` port with a generated project
    // defined every support type twice — and did so only when `project` was `Some`, which made the
    // emitted file set depend on whether a build was also generated.
    // …plus whatever the MANIFEST says this module's build must add. `PortManifest.dependencies` is
    // where a port records that it took a `Verdict.Depend`'s advice, and the generated build is the
    // only place that fact can have an effect — so the two meet here rather than in the caller's
    // `ProjectSpec`, which would leave a port free to declare the dependency and not ship it.
    // Empty on every port today, so no generated build file moves.
    project.foreach { spec =>
      val declared = manifest.map(_.dependencies).getOrElse(Nil).map(SbtGen.Dep.of)
      SbtGen.emitPort(portRoot, spec.copy(deps = spec.deps ++ declared), effectivePhases, runtimeMode)
    }

    val report = PortReport(
      label = label,
      signature = mismatches,
      omissions = omissions,
      portability = portability,
      injectedPortability = injectedViolations,
      substitution = leaked ++ danglingSubs,
      policy = policy,
      rename = renameReport,
      manifest = agreement,
    )
    // The one place every count appears together WITH the §1 classification of its fix. An agent
    // in another repository reads this and knows, per line, whether the next step is in the engine,
    // in its manifest, or in a rule of its own (CLAUDE.md §4.45).
    say("report:")
    println(report.render)
    say(s"wrote $written ${sourceSet.noun} ($dropped dropped, $injected injected) -> $emitDir")

    // BEST EFFORT ENDS NONZERO (`DESIGN.md` §6.4). Here and not at the gate, because the whole
    // value of the mode is the diagnostics ABOVE this line — the report, the marker inventory, the
    // per-file banners. A run that died at the gate would produce none of them, which is the wrong
    // trade (`ENGINE-LIMITS.md` M6 is about refusing to APPROXIMATE, not about refusing to REPORT).
    // What it must never do is succeed: an exit code is the one signal a build reads without being
    // asked to, and a degraded tree that a script treats as a delivery is exactly what the separate
    // directory and the sentinel exist to prevent.
    if emitDir != outDir then
      System.err.println(s"[$label] BEST-EFFORT emission: ${openMarkers.size} open marker(s); " +
        s"the degraded tree is at $emitDir and MUST NOT SHIP")
      sys.error(s"[$label] BEST-EFFORT run: ${openMarkers.size} open unportability marker(s). " +
        s"Nothing was written to the deliverable tree ($outDir); the degraded output is at " +
        s"$emitDir, beside a ${"BALTICPORTER-BEST-EFFORT"} sentinel, with every region fenced and " +
        "named in its file's banner.")

    if report.fatal.nonEmpty then
      report.fatal.foreach(f => System.err.println(s"[$label] FATAL — $f"))
      sys.error(s"[$label] ${report.fatal.size} fatal finding(s); see the report above")
    if nextStep.nonEmpty then say(s"now: $nextStep")
    PortResult(program, outDir, written, dropped, injected, plan, report)

  // -------------------------------------------------------------------------
  // internals
  // -------------------------------------------------------------------------

  /** Anchor persisted finding paths to THIS PORT's source root.
    *
    * A finding's stable id hashes its path, so the path has to be relative to something every
    * checkout agrees on, and the only value that qualifies is the root the port was parsed from —
    * which the run already holds. Left to the operator it comes from a shell script, and a
    * migration invoked directly (exactly what the add-a-library skill tells a new port to do)
    * silently falls back to a repo-relative path: every finding then diffs as removed-and-re-added
    * against a baseline whose COUNTS are identical, which is the most expensive shape a diff can
    * take. CLAUDE.md §4.6: a flag that carries measurement identity comes from the port.
    *
    * Set as a system property, which [[DebugFlags]] resolves ahead of the marker files — so the
    * port's own answer wins over a script's, deliberately. */
  private def anchorReportPaths(): Unit =
    System.setProperty(DebugFlags.Prefix + "reportPathRoot", frontend.sourceRoot.toAbsolutePath.normalize.toString)

  /** Write this run's source map, and the data the CORRELATOR needs to classify a test failure as
    * expected BY CONSTRUCTION.
    *
    * `dropped-types.tsv` is the second half and the point of it: a test whose failure stack reaches
    * a type in `Substitutions.dropTypes` fails because the port deliberately does not have that
    * type. That is a CONSEQUENCE of the manifest, so it is generated from the manifest on every
    * run — `port-report/<Port>/baseline/expected-failures.tsv` listing the same failures by hand is
    * exactly the artifact that rots into "we always ignore those four" and then hides a fifth. The
    * hand-written file survives as the explicit escape hatch for a failure no drop explains, and
    * `Correlate.Expected.derived` keeps the two from being confused.
    *
    * The whole DROP CHAIN is written, not just this module's own: a dependent port's suite fails
    * inside the BASE's dropped type, and holding a suite to its own module's drops would classify
    * every one of those as a regression. (Contrast `ownSubs`, which is right for CHECK 2 for the
    * mirror-image reason.) `policySubs` and `renames` both read through the manifest chain, so a
    * dependent's file carries the base's drops UNDER THE BASE'S RENAME — which is the case the
    * correlator actually needs, since the suite is the port whose failures are being classified.
    *
    * BOTH NAMESPACES are written, and that is what makes the rule fire at all. Policy is declared
    * upstream and `PackageRenameTransform` runs LAST (CLAUDE.md §4.56), so this run is the last
    * place that holds the manifest FQN and the rename map together; a stack frame, by the time the
    * correlator sees one, only ever says `sge.utils.Json`. Writing the manifest name alone left the
    * correlator comparing two namespaces, silently — the derived classification had never once
    * fired on a renaming port, with the four deliberate libGDX failures reported as unexpected
    * regressions on every run. The rename is applied by the phase's OWN rule
    * ([[PackageRenameTransform.renamed]]): longest prefix, cut only at a separator, so `com.foo`
    * can never rewrite `com.foobar`. */
  /** `markers.tsv` — one line per marker, keyed the way [[SrcMap]] keys members.
    *
    * `DESIGN.md` §6.5 asks for exactly this and says why: the correlation lane already ACCEPTS a
    * marker set and an empty one is a tested, legal input, so the marker side has only to write the
    * file for §6.3's third lane — a diagnostic at a MARKED region, classified and expected rather
    * than triaged — and for the false-positive lane, which is one set-difference over the same two
    * inputs. A marked region with NO error is the most interesting row the engine can produce: an
    * approximation that happens to compile is precisely the silent-defect class §6 exists for.
    *
    * GATED ON THE ARTIFACT LAYER, without exception (§5.1). This is written from a check's own
    * inventory, so it is reachable from more test paths than `PortMap` is — and one unconditional
    * `PortMap.write` was enough to publish run directories into the checkout from a JVM with no
    * port identity at all. */
  private def writeMarkers(prog: Program, sited: List[MarkerCheck.Sited]): Unit =
    if CheckReport.enabled then
      val dir = CheckReport.runDir
      Files.createDirectories(dir)
      val rows = sited.map { s =>
        val unit  = prog.symbolOf(s.unit).map(_.fullName).getOrElse("?")
        val state = if s.marker.state.isOpen then "open" else "resolved"
        s"$unit\t${s.ownerFqn}\t$state\t${s.marker.kind.label}\t${s.marker.diff.fold("-")(_.toString)}\t" +
          s"${CheckReport.relativise(s.marker.origin.javaPath)}\t${s.marker.origin.line}\t${s.marker.what}"
      }
      Files.writeString(dir.resolve("markers.tsv"),
        ("#unit\tmember\tstate\tkind\tcatalog\tjavaPath\tline\twhat" :: rows).mkString("", "\n", "\n"))

  /** `catalog.tsv` — one row per catalog entry, reached or not.
    *
    * Every row and not only the reached ones, because the question the artifact exists to answer is
    * "which branches does this port never touch", and a file listing only what fired answers the
    * other one. `just catalog-coverage` aggregates these across the corpus, which is the answer an
    * agent needs before claiming a rule is live: a row unreached on one small library is normal, a
    * row unreached on all fifteen is dead code or an untested rule.
    *
    * GATED ON THE ARTIFACT LAYER, without exception (§5.1). This is written from the FRONTEND's own
    * log, so it is reachable from more test paths than `PortMap` is — and one unconditional
    * `PortMap.write` was enough to publish run directories into the checkout from a JVM with no
    * port identity at all.
    *
    * …AND IT CARRIES ITS OWN PROVENANCE, which is the half a count cannot state. A unit served from
    * the action cache is a unit this run did not RENDER, so every `Rendering`/`Typing` consult it
    * would have made is missing from the numbers below — and a partial coverage number that cannot
    * say it is partial reads exactly like a smaller one. Zero on every port in this corpus (no port
    * sets `cache`), and written anyway: a provenance line that only appears when it is non-zero is
    * one no reader learns to look for. */
  private def writeCatalog(log: balticporter.catalog.CatalogLog, fromCache: Int): Unit =
    if CheckReport.enabled then
      val dir = CheckReport.runDir
      Files.createDirectories(dir)
      val provenance = s"#units-served-from-cache\t$fromCache"
      Files.writeString(dir.resolve("catalog.tsv"),
        (provenance :: CatalogCheck.TsvHeader :: CatalogCheck.tsv(log)).mkString("", "\n", "\n"))

  private def writeSrcMap(rec: balticporter.tir.SrcMap.Recording): Unit =
    if CheckReport.enabled then
      val dir = CheckReport.runDir
      // minus the DROPPED units. The emitter faithfully records every unit it renders — including
      // the ones this run then refuses to WRITE — so the map carried phantom members for types the
      // classpath holds an injected replacement of, and a frame inside that replacement resolved
      // to a fabricated member and Java origin (`sge.utils.Json` line 57 landing on
      // `Json#addClassTag [Json.java:118]`). The map must describe what is ON DISK; a frame in
      // injected code then resolves to nothing, which the correlator already classifies honestly
      // as "outside the source map" (§5.1). Entries are keyed by EMITTED unit name, so the drop
      // set is translated the same way `dropped-types.tsv`'s second column is.
      val droppedEmitted = policySubs.dropTypes.map(emittedName)
      SrcMap.write(dir, rec.copy(entries = rec.entries.filterNot(e => droppedEmitted(e.unit))))
      Files.createDirectories(dir)
      val drops = policySubs.dropTypes.toList.sorted
        .map(fqn => Correlate.Dropped(fqn, emittedName(fqn)).tsv)
      Files.writeString(dir.resolve("dropped-types.tsv"),
        (Correlate.DroppedHeader :: drops).mkString("", "\n", "\n"))

  /** Everything this run decides OUTSIDE the phase pipeline, into the run's own log, in the one
    * order the artifact and the emitted notes can both be derived from.
    *
    * Called once per TRANSLATION rather than once per run, because `Determinism.Full` produces two
    * of them and the second must render byte-identical notes to be comparable at all — a check
    * that can only pass because one side had no decisions is not a determinism check.
    *
    * The order inside is the only one that works:
    *   1. the EMITTER's own passes (the three §4.55 renames, the replay widening). They happened
    *      when the emitter was constructed; they are recorded here, once, from the emitter the run
    *      keeps — never from the emitter's constructor, or the determinism twin doubles every row.
    *   2. the OWNERSHIP FILTER, which scopes everything recorded so far — the phases' rows and the
    *      emitter's — to this module's own declarations.
    *   3. the CONSTRUCTOR FUNNEL and the SUBSTITUTION MANIFEST, after it.
    *
    * Step 3 is deliberately AFTER the filter and not before. A funnel row is already restricted to
    * the units this run emits, so filtering it would be a no-op; a MANIFEST row is a statement
    * about a policy KEY this run applied, and a dependent applies its base's drops (§1.5) — the
    * `own` detail is what says whose manifest holds the key, and withholding the row instead would
    * leave a port unable to say which types it is compiling WITHOUT. That is the same reason
    * `dropped-types.tsv` carries the whole drop chain.
    *
    * @return how many rows were withheld as another module's
    */
  private def recordRunDecisions(
      t: PortRun.Translated,
      injectedSources: List[(String, String)],
      plan: RuntimePlan,
  ): Int =
    t.decisions.recordAll(t.emitter.ownDecisions)
    // …and one row per APPLIED REMEDY SELECTION. Recorded by the RUN and not by the phase that
    // applied it, so the row and the `remediation(resolved)` finding beside it come from ONE value
    // (`AppliedResolution`) and cannot disagree about what the resolution did. `Reason.Configured`
    // names the manifest entry, which is the string an agent edits; the porter note follows from
    // `Decision.Kind.SelectedRemedy` being in `PorterNote.Rendered`, so the reader at the emitted
    // line is told there was a menu and which entry this port picked.
    // …`decisions` and not `all.map(_.decision)`: the ledger is per SITE, because that is what the
    // drained lane's count has to balance against, and a decision is per DECLARATION (§5.1). One per
    // site put the same sentence twice above one `val` the first time a selection broadcast.
    t.decisions.recordAll(t.binder.resolutions.decisions)
    val withheld = retainOwnDecisions(t.program, t)
    recordCtorFunnel(t.program, t)
    recordDroppedSuperArgs(t.program, t)
    recordDroppedNilaryCtors(t.program, t)
    recordPolicyDecisions(t.program, t, injectedSources, plan)
    withheld

  /** Prove the emitted text is REPRODUCIBLE — after the decisions, because a porter note is
    * emitted text and an emitter that could not see the run's log renders a different file.
    *
    * `Determinism.Emission` builds a second emitter over the same program and hands it the SAME
    * decision log: it renders the same notes and records none of its own (`ownDecisions` is a value
    * nobody reads), which is exactly the property that lets one log serve both. */
  private def verifyDeterminism(once: PortRun.Translated, injectedSources: List[(String, String)], plan: RuntimePlan): Unit =
    determinism match
      case Determinism.Off => ()
      case Determinism.Emission =>
        // a SECOND emitter over the same program: independent mutable state, independent lazy
        // tables, same bytes required.
        // …and the SAME `Surface`. Not an optimisation: the view is an INPUT to emission (it scopes
        // the constructor funnel's fixpoint), so a twin built without it re-derives every base
        // class's primary the pre-§8.3 way and reports a determinism violation for exactly the
        // classes the contract fixed. Measured: 2 units on gdx-gltf, both of them the wall classes
        // this item exists for.
        // …and NOT the run's catalog log. It shares the DECISION log deliberately (it renders the
        // same notes and records none of its own), but a consult is a COUNT: re-rendering every
        // unit into the same log would double every number `catalog.tsv` reports, for a second
        // emitter whose whole purpose is that its bytes are thrown away.
        val again = new TirEmitter(once.program, once.plan.concreteMembers, provenance, once.decisions,
                                   preview, bestEffort, Some(once.surface))
        val diffs = once.emitOrder.filter(u => again.emitUnit(u) != once.sourceOf(u))
        if diffs.nonEmpty then determinismViolation("emission", once, diffs)
        say(s"determinism: ${once.emitOrder.size} units emitted twice, byte-identical " +
          s"(${Determinism.FullFlag} also re-parses)")
      case Determinism.Full =>
        val twice = translateOnce()
        recordRunDecisions(twice, injectedSources, plan)
        if twice.emitOrder.size != once.emitOrder.size then
          sys.error(s"[$label] determinism violation: ${once.emitOrder.size} units first, ${twice.emitOrder.size} second")
        val diffs = once.emitOrder.zip(twice.emitOrder).collect {
          case (a, b) if once.sourceOf(a) != twice.sourceOf(b) => a
        }
        if diffs.nonEmpty then determinismViolation("full translation", once, diffs)
        say(s"determinism: ${once.emitOrder.size} units translated twice from scratch, byte-identical")

  /** The porter notes an INJECTED file carries, prepended when it is copied into `src_managed`.
    *
    * An injected replacement is the only place a `DroppedType` decision can be read from: the type
    * it replaces is deliberately not emitted, so no unit in the tree corresponds to it and there is
    * no `class` keyword to sit above. Matched by the file's RELATIVE PATH, which is what
    * `recordPolicyDecisions` records for an injection and the only key the two sides share — an
    * injected file has no `SymId` and no Java behind it.
    *
    * Prepended at COPY time and never written back to the overrides directory: the emitted tree is
    * a build product (§5.5) and the hand-written source is a decision. Re-copying reads the
    * pristine source again, so a note can never accumulate. */
  private def injectionNotes(rel: String): String =
    val fqn = rel.stripSuffix(".scala").replace('/', '.')
    val mine = translatedDecisions.filter { d =>
      (d.kind == Decision.Kind.InjectedMember && d.detail.get("file").contains(rel)) ||
      (d.kind == Decision.Kind.DroppedType && d.detail.get("emitted").contains(fqn))
    }.sortBy(_.tsv)
    mine.map(PorterNote.render(_, "")).mkString

  /** the run's decisions, for the injection copy — set once, before emission. A `var` because the
    * copy loop runs inside `execute()` and the alternative is threading the log through three
    * unrelated parameters; scoped to one run by construction, since `PortRun` is a value a program
    * builds and calls once. */
  private var translatedDecisions: List[Decision] = Nil

  /** A secondary constructor whose `super(args)` could not be expressed, as a DECISION beside the
    * omission the same function already counts.
    *
    * Derived from `CtorFunnel.Plans.superExpressed` — the same predicate `OmissionCheck
    * .droppedSuperArgs` reads and the same one the emitter renders its delegation from, asked at
    * the same granularity (per CONSTRUCTOR). Two derivations of one fact is exactly how a shadow
    * becomes a claim; this is one function's answer, recorded twice for two audiences.
    *
    * A finding says "this many arguments were discarded" and is a number to watch. The DECISION
    * says which constructor, in the code, so the reader of that `def this` learns that the
    * arguments java passed to `super` are gone and why (`ENGINE-LIMITS.md` C3: padding is a guess
    * everywhere but the JDK throwables). */
  /** The members an ENGINE RULE refused to emit, for the published contract — EMITTED member key →
    * the `shape` payload naming the rule.
    *
    * One entry today, `ENGINE-LIMITS.md` C11's nilary constructor, and the shape of the hole it
    * closes is why this is a member row rather than another type-shape key. The drop DID reach the
    * contract before, through `TypeShape.secondaries`, which SUBTRACTS the constructor — so a
    * dependent read `primary=() primaryKind=not-funnelled` with no `()` among the secondaries, which
    * is precisely what a benign class with one constructor publishes. `secondaries` is write-only for
    * this question: an absence is not a disposition, and `new C()` in a dependent therefore compiled
    * into the empty-font wrong answer with nothing counting it (§4.4 — it compiles and means
    * something else).
    *
    * As a `Dropped` MEMBER row it lands in the lane `PortMapTransform` already has for a dropped
    * member's call sites, with no new consumer and no second artifact; the `refusal` key is what
    * keeps it apart from a POLICY drop, which is a different §1 kind and a different fix.
    *
    * Derived from `CtorFunnel.Plans.droppedNilaryCtor` through the run's own `Surface` — the same
    * function the emitter drops with, `OmissionCheck` counts from and `recordDroppedNilaryCtors`
    * records; four readings, one answer. */
  private def refusedMembers(program: Program, translated: PortRun.Translated): Map[String, String] =
    val plans = CtorFunnel.Plans(program, Some(translated.surface))
    emittedClasses(program, translated).flatMap { cd =>
      plans.droppedNilaryCtor(cd).flatMap { _ =>
        program.symbolOf(cd.symbol).map(_.fullName).filter(_.nonEmpty).map { owner =>
          s"$owner#<init>()" ->
            balticporter.tir.Surface.render(
              balticporter.tir.Surface.MemberShape(refusal = "ctor-funnel/nilary-dropped(C11)"))
        }
      }
    }.toMap

  /** IS THIS UNIT ONE THE RUN WILL NOT WRITE? — the one drop question, asked in one place.
    *
    * Two facts decide it and BOTH are needed: the frontend's `Substituted` TAG, applied at parse
    * time and therefore rename-proof, and the manifest key as a fallback for a symbol the frontend
    * never tagged. The write loop has asked both since a renamed port emitted all 11 of its dropped
    * types while reporting `0 dropped`; the CHECKS asked only the second, and the EMISSION GATE
    * asked neither.
    *
    * That third reader is what makes this a function rather than a habit. The gate ran over the raw
    * `emitOrder`, so a port that took the gate's own remediation — *drop the declarations that use
    * the marker and inject a replacement* — was refused forever, while the `markers` lane beside it
    * reported the same run as clean because it IS scoped to the emitted units. One run, two answers,
    * and no way out of it. Every reader of "what does this run emit" goes through here. */
  private def isDropped(program: Program, u: Tree.ClassDef): Boolean =
    program.symbolOf(u.symbol).exists(s => Substituted.tags(s) || policySubs.dropsType(s.fullName))

  /** the units of `emitOrder` this run actually WRITES — [[isDropped]] over a list. */
  private def emittedUnits(program: Program, units: List[Tree.ClassDef]): List[Tree.ClassDef] =
    units.filterNot(isDropped(program, _))

  /** every class this run EMITS, nested ones included — the domain every decision recorder below
    * ranges over, spelled once so two of them cannot disagree about which classes are this module's
    * (`ENGINE-LIMITS.md` D2). */
  private def emittedClasses(program: Program, translated: PortRun.Translated): List[Tree.ClassDef] =
    // `allClassDefs` — see `emittedFqns` above; D2's ownership range must not stop at the body.
    emittedUnits(program, translated.emitOrder).flatMap(u => StandardTraversal.allClassDefs(u)(using program))

  private def recordDroppedSuperArgs(program: Program, translated: PortRun.Translated): Unit =
    given Program = program
    // the run's own view — the same one the emitter and `OmissionCheck` hold. A decision recorder
    // that re-derived the funnel over a `TrivialSurface` would describe a plan this run did not
    // emit, which is the shadow-becomes-a-claim failure in its provenance form (D5).
    val plans = CtorFunnel.Plans(program, Some(translated.surface))
    emittedClasses(program, translated).foreach { cd =>
      CtorFunnel.ctorsOf(program, cd.body).foreach { d =>
        val args = CtorFunnel.superArgsOf(program, d)
        if args.nonEmpty && !plans.superExpressed(cd, d) then
          translated.decisions.record(Decision(
            kind       = Decision.Kind.DroppedSuperCall,
            subject    = d.symbol,
            subjectFqn = program.symbolOf(cd.symbol).map(_.fullName).getOrElse("?"),
            detail = Map(
              "owner"     -> program.symbolOf(cd.symbol).map(_.fullName).getOrElse("?"),
              "arguments" -> args.size.toString,
              "why"       -> ("java lets EVERY constructor pick its own `super(...)`; scala lets " +
                "only the primary reach super and a secondary must begin with `this(...)`. This " +
                "root's arguments reach neither the extends clause nor a replay, so they are gone " +
                "— padding them would be a guess (ENGINE-LIMITS.md C3)"),
            ),
            reason = Reason.Universal("ctor-funnel/super-args-dropped(C3)"),
            origin = d.origin,
          ))
      }
    }

  /** The NILARY constructor `orderBody` drops in front of a nilary primary, as a DECISION beside the
    * omission the same predicate already counts (`ENGINE-LIMITS.md` C11).
    *
    * The exact sibling of [[recordDroppedSuperArgs]], and for the same reason: a finding is a number
    * to watch and a decision is the sentence the reader of that FILE needs. Here the gap between the
    * two is at its widest, because the subject is a member that is not there — an agent reading the
    * emitted `BitmapFont` sees no `def this()` and has nothing to grep for. `Decision.Kind
    * .DroppedMember` is `PorterNote.InBody`, so the note heads the owning type's body, which is where
    * somebody looking for the constructor looks.
    *
    * Derived from `CtorFunnel.Plans.droppedNilaryCtor` — the same function `OmissionCheck
    * .droppedNilaryCtors` counts from and the same one `TirEmitter.orderBody` drops with — asked
    * through the run's own `Surface`, so this cannot describe a plan the run did not emit (D5).
    *
    * The subject is the OWNING TYPE and not the constructor: an `InBody` note is rendered against the
    * type whose body carries it, and the dropped constructor's own symbol is never emitted. That
    * matches the `dropMethods` rows in `recordPolicyDecisions`, which are the other `DroppedMember`
    * decider and key the same way. */
  private def recordDroppedNilaryCtors(program: Program, translated: PortRun.Translated): Unit =
    val plans = CtorFunnel.Plans(program, Some(translated.surface))
    emittedClasses(program, translated).foreach { cd =>
      plans.droppedNilaryCtor(cd).foreach { d =>
        val owner = program.symbolOf(cd.symbol).map(_.fullName).getOrElse("?")
        val args  = CtorFunnel.delegationOnlyNilary(program, d).map(_.size).getOrElse(0)
        translated.decisions.record(Decision(
          kind       = Decision.Kind.DroppedMember,
          subject    = cd.symbol,
          subjectFqn = s"$owner#<init>()",
          detail = Map(
            "owner"     -> owner,
            "member"    -> "<init>()",
            "arguments" -> args.toString,
            "why"       -> ("java ran this nilary constructor's delegation and scala's implicit " +
              "nilary primary runs nothing; `def this()` beside that primary is `E120`, so there is " +
              "nowhere to put it. `new " + owner.substring(owner.lastIndexOf('.') + 1) + "()` " +
              "therefore builds an object java could not build. Emitting, promoting and " +
              "marker-disambiguating it were each measured and each emits a WRONG answer in place " +
              "of a missing one (ENGINE-LIMITS.md C11); a port that needs the behaviour writes the " +
              "constructor by hand (§1.5's `inject`)"),
          ),
          reason = Reason.Universal("ctor-funnel/nilary-dropped(C11)"),
          origin = d.origin,
        ))
      }
    }

  /** The DECISIONS this run made outside any phase — the substitution manifest, the injection copy
    * and the two OTHER ways a definition reaches the output with no Java behind it (the vendored
    * runtime, `supportSources`) — recorded into the run's own log (CLAUDE.md §4.45: make it obvious
    * to an investigating agent HOW the porter arrived at the code, and in which of §1's three
    * repositories the fix lives).
    *
    * The drops and the injection copy are `Reason.Configured`, and the KEY is the manifest entry verbatim. That is the whole
    * value of the record: an agent holding `sge.utils.Json` learns not merely that the type is
    * substituted but that `Substitutions.dropTypes` contains `com.badlogic.gdx.utils.Json`, which
    * is the exact string it must remove to change the outcome.
    *
    * One row per DECLARED key, not per key that fired. A key that matched nothing is a decision the
    * run made and failed to carry out, and `detail("fired")` says so — the same defect `PolicyReport`
    * reports as a finding, visible here in the provenance an agent reads for a different reason.
    *
    * `detail("own")` separates a drop THIS module declares from one it inherited (§1.5), because
    * they live in different manifests and only one of them is this module's to edit. `detail`
    * carries the EMITTED name too: policy is upstream and the rename runs last (§4.56), so a
    * record that named only one of the two would be unjoinable with anything observed about the
    * running port — the mistake `dropped-types.tsv` shipped with.
    */
  private def recordPolicyDecisions(
      program: Program,
      translated: PortRun.Translated,
      injectedSources: List[(String, String)],
      plan: RuntimePlan,
  ): Unit =
    val log = translated.decisions
    // A dropped type is PARSED, so the run usually still holds its unit — and with it the Java file
    // the decision is about. Keyed by EMITTED name, since that is what `fullName` is by now.
    // EVERY class this run declares, nested ones included — not only the top-level units.
    //
    // A nested type used to fall through to "borrow the enclosing file's origin, keep no symbol",
    // which was right about the id (a wrong one is worse than none) and cost the row its subject.
    // A nested type HAS a symbol and HAS an origin of its own; the reason it was not found was
    // that the index only held units. With the id, a `DroppedMember` on
    // `ParallelArray$ChannelDescriptor` renders its porter note inside that nested class instead of
    // nowhere — which is the whole point of recording a subject.
    val typesByFqn: Map[String, Tree.ClassDef] =
      program.units
        .flatMap(u => StandardTraversal.allClassDefs(u)(using program))
        .flatMap(u => program.symbolOf(u.symbol).map(_.fullName -> u)).toMap
    // Which declared keys FIRED, from the run's binder — see `firedKeys` above for why this is no
    // longer a tally accumulated on the policy value itself.
    val fired = translated.binder.bindings.filter(_.binding.isBound).map(_.entry).toSet
    def emitted(fqn: String) = emittedName(fqn)
    // The type a policy key is ABOUT, and its Java file. Where even the nested lookup fails (a key
    // that matched nothing, a type this run does not parse) the enclosing top-level type still
    // supplies the FILE — `ParallelArray$ChannelDescriptor` lives in `ParallelArray.java`, and a
    // row saying `<synthetic>` would be unnavigable for the sake of a `$`. Its SYMBOL is not
    // borrowed with it: that id names a different type. Cut at the separator (§4.56).
    def at(fqn: String): (SymId, Origin) =
      val e = emitted(fqn)
      typesByFqn.get(e) match
        case Some(u)    => (u.symbol, u.origin)
        case scala.None => (SymId.None, typesByFqn.get(e.takeWhile(_ != '$')).map(_.origin).getOrElse(Origin.synthetic))

    policySubs.dropTypes.toList.sorted.foreach { key =>
      val (sym, origin) = at(key)
      log.record(Decision(
        kind       = Decision.Kind.DroppedType,
        subject    = sym,
        subjectFqn = key,
        detail = Map(
          "key"     -> key,
          "emitted" -> emitted(key),
          "fired"   -> (if fired(key) then "yes" else "no"),
          "own"     -> (if ownSubs.dropTypes(key) then "yes" else "no"),
          "why"     -> "declared in Substitutions.dropTypes: parsed so references still resolve, never emitted",
        ),
        reason = Reason.Configured("substitutions", key),
        origin = origin,
      ))
    }

    policySubs.dropMethods.toList.sorted.foreach { key =>
      val owner         = key.takeWhile(_ != '#')
      val (sym, origin) = at(owner)
      log.record(Decision(
        kind       = Decision.Kind.DroppedMember,
        subject    = sym,
        subjectFqn = key,
        detail = Map(
          "key"   -> key,
          "owner" -> emitted(owner),
          "fired" -> (if fired(key) then "yes" else "no"),
          "own"   -> (if ownSubs.dropMethods(key) then "yes" else "no"),
          "why"   -> "declared in Substitutions.dropMethods: the rest of the owning type is ported mechanically",
        ),
        reason = Reason.Configured("substitutions", key),
        origin = origin,
      ))
    }

    // The injections THIS module ships (`ownSubs`) — a dependent must not restate its base's, or
    // the same FQN is defined twice (§1.5). The origin is the file inside the injection root: an
    // injected definition has no Java at all, and saying so is the point.
    injectedSources.foreach { (fqn, rel) =>
      log.record(Decision(
        kind       = Decision.Kind.InjectedMember,
        subject    = SymId.None,
        subjectFqn = fqn,
        detail     = Map("file" -> rel, "why" -> "hand-written Scala copied verbatim; it never passed through the TIR"),
        reason     = Reason.Configured("substitutions", "inject"),
        origin     = Origin(rel, 0, 0),
      ))
    }

    // ---- the OTHER two ways a definition reaches the output without a Java file behind it ----
    //
    // Both are injections in the only sense that matters to a reader of the emitted tree — a type
    // stands there that no upstream source declares — and they differ in WHICH of §1's three kinds
    // an agent must act in, which is exactly what `Reason` is for.
    //
    // The VENDORED RUNTIME is §1(a). Neither the requirement nor the text is anybody's policy:
    // `RuntimePlan.of` derives it from the phases that ran (`RequiresRuntime`), and the sources are
    // a verbatim copy of the published `balticporter-runtime` module. A port cannot choose the
    // shape of `JavaCollection`; the only per-port choice is `runtimeMode`, and that decides
    // whether these are FILES or a build dependency — under `Dependency` this loop writes nothing,
    // so it records nothing, which is the honest answer: no definition was injected.
    plan.sources.toList.sorted.foreach { (fqn, _) =>
      log.record(Decision(
        kind       = Decision.Kind.InjectedMember,
        subject    = SymId.None,
        subjectFqn = fqn,
        detail = Map(
          "file"    -> (fqn.replace('.', '/') + ".scala"),
          "mode"    -> runtimeMode.toString,
          "required"-> (if plan.required(fqn) then "directly" else "closure"),
          "why"     -> ("a support type a phase retyped this port's code ONTO, vendored into the " +
            "source set because this port carries no library dependency; the text is a verbatim " +
            "copy of the published balticporter-runtime module"),
        ),
        reason = Reason.Universal("runtime-vendoring"),
        origin = Origin.synthetic,
      ))
    }

    // `supportSources` is §1(b): a MAP THIS PORT WRITES, for a phase that cannot declare its
    // support types through `RequiresRuntime`. The key is the FQN, verbatim, because that is the
    // entry an agent removes to stop the file being written.
    supportSources.toList.sorted.foreach { (fqn, _) =>
      log.record(Decision(
        kind       = Decision.Kind.InjectedMember,
        subject    = SymId.None,
        subjectFqn = fqn,
        detail = Map(
          "file" -> (fqn.replace('.', '/') + ".scala"),
          "key"  -> fqn,
          "why"  -> ("supplied by this port's `supportSources`: a phase's output references it and " +
            "no phase declares it through RequiresRuntime, so the run writes it"),
        ),
        reason = Reason.Configured("support-sources", fqn),
        origin = Origin.synthetic,
      ))
    }

  /** The CONSTRUCTOR FUNNEL's decisions — recorded by the RUN, because the funnel is not a phase.
    *
    * `CtorFunnel.Plans` is consulted at EMISSION (`TirEmitter` holds one), so there is no phase
    * buffer to drain and no `Phase.record` to call; the run is the only layer that holds both the
    * translated program and the decision log. Recording it here rather than in the emitter also
    * keeps the emitter a pure function of a `Program` — the same division `PortRun.record` draws
    * for the substitution checks.
    *
    * WHY IT MATTERS MORE THAN ANY OTHER ROW HERE: this is the one decision that changes what the
    * emitted class DOES rather than what it is called. A promoted constructor's body becomes the
    * class body, and a scala class body runs on EVERY construction path where java's did not — 59
    * of libGDX's 771 promotions, `Material` bumping a static id counter on every construction among
    * them (`ENGINE-LIMITS.md` C7). `detail("escapes")` carries that count per class; refusing the
    * promotion is not available (measured 0 -> 41 compile errors), so the honest outcome is the
    * recorded one.
    *
    * ONE ROW PER CLASS, and only where the funnel ACTED — a class the funnel nominated nothing for
    * made no decision, and a class with a single constructor that became the primary is java's own
    * structure surviving unchanged, which is the definition of a row nobody needs.
    *
    * The plans are rebuilt over THIS run's program rather than read off the emitter's, which holds
    * its own over a name-normalised copy (`TirEmitter.prepared`). Nomination reads constructor
    * bodies, parameter lists and parent constructor sets — none of which a rename touches — so the
    * shapes agree; what can differ is a promoted parameter's NAME, which the emitter may suffix
    * `$p` to keep it from capturing an inherited member. `detail("primary")` is therefore the
    * signature as the TIR holds it, which is the form every other row here is written in.
    */
  private def recordCtorFunnel(program: Program, translated: PortRun.Translated): Unit =
    given Program = program
    val plans = CtorFunnel.Plans(program, Some(translated.surface))
    def nested(cd: Tree.ClassDef): List[Tree.ClassDef] = StandardTraversal.allClassDefs(cd)
    // this run's OWN units, minus the ones it does not write: a dropped type's constructors are
    // replaced wholesale by injected Scala, so the funnel's answer about them describes nothing on
    // disk. (`translated.foreign` is excluded by construction — `emitOrder` is the other half.)
    val mine = translated.emitOrder.filterNot { u =>
      program.symbolOf(u.symbol).exists(s => Substituted.tags(s) || policySubs.dropsType(s.fullName))
    }
    mine.flatMap(nested).foreach { cd =>
      val p     = plans(cd)
      val ctors = plans.constructorsOf(cd)
      val acted = p.primary.isDefined || p.isSynthesised
      val trivial = !p.isSynthesised && ctors.sizeIs <= 1
      if acted && !trivial then
        val primary =
          if p.isSynthesised then
            p.synthetic.map((n, t) => s"$n: ${balticporter.tir.TirPrinter.tpe(t, balticporter.tir.TirPrinter.Style.canonical)}")
              .mkString("(", ", ", ")")
          else
            p.primaryParams.map { v =>
              val n = program.symbolOf(v.symbol).map(_.name).getOrElse("_")
              s"$n: ${balticporter.tir.TirPrinter.tpe(v.tpt.tpe, balticporter.tir.TirPrinter.Style.canonical)}"
            }.mkString("(", ", ", ")")
        translated.decisions.record(Decision(
          kind       = Decision.Kind.FunnelledCtor,
          subject    = cd.symbol,
          subjectFqn = program.symbolOf(cd.symbol).map(_.fullName).getOrElse("?"),
          detail = Map(
            "shape"        -> plans.shape(cd),
            "primary"      -> primary,
            // WHOSE constructor the primary is, which is the reader's next question after `primary`
            // and is only answerable here: a SYNTHESISED primary is a member no java declared, so it
            // is emitted `protected` — narrow enough that no client can call a constructor java
            // never exposed, wide enough that a subclass in ANY module still reaches it from its
            // `extends` clause (`private` is class-private in scala, so even a same-package subclass
            // could not). A promoted one keeps whatever java gave the constructor it promotes.
            "primaryVis"   -> (if p.isSynthesised then "protected" else "as-declared"),
            // WHICH java thing each slot of a synthesised primary came from, so a reader can join
            // the emitted signature back to the java WITHOUT the run directory (`DESIGN.md` §8.2):
            // `sup$k` is the parent constructor's formal k. A promoted primary's parameters are
            // java's own and need no such key.
            "slots"        -> (if p.synthetic.isEmpty then "-" else p.synthetic.map(_._1).mkString(",")),
            // every field that was a candidate slot and was REFUSED, with the reason — the sentence
            // an agent asking "why is this field a `var`" needs, which A1 has no other channel for.
            "notSlot"      -> (if p.notSlot.isEmpty then "-" else p.notSlot.map((f, w) => s"$f=$w").mkString(",")),
            // …and whether the primary needed a disambiguator to be DECLARABLE beside, and
            // REACHABLE past, this class's real constructors (`ENGINE-LIMITS.md` C8/C9). Never the
            // marker's FQN: a companion-`protected` type is not a name any consumer may resolve, so
            // the contract row says `marker` and nothing more (§8.1 F4).
            "disambiguator" -> (if p.marker.isDefined then "marker" else "none"),
            "constructors" -> ctors.size.toString,
            "superArgs"    -> p.superArgs.size.toString,
            "escapes"      -> plans.promotionEscapes(cd).size.toString,
            "why"          -> (if p.isSynthesised then
              "java lets every constructor pick its own `super(...)` and scala lets only the " +
              "PRIMARY reach super; no java constructor here can be that primary, so a protected " +
              "one taking the PARENT constructor's own parameters is synthesised and every java " +
              "constructor becomes a `def this` computing its arguments"
            else
              "java lets every constructor pick its own `super(...)`; scala lets " +
              "only the PRIMARY reach super, so one is nominated, its super arguments become the " +
              "`extends` clause and its body becomes the class body — which runs on every " +
              "construction path, `escapes` of which java did not run it on"),
          ),
          reason = Reason.Universal("ctor-funnel"),
          origin = cd.origin,
        ))
    }

  /** Reduce the PHASES' decisions to the ones about THIS MODULE, and say how many were withheld.
    *
    * A dependent's `Program` CONTAINS its base — `resolutionRoots` parses it, so every phase runs
    * over the base's units too and decides about them identically to the base's own run. Unfiltered,
    * `libgdx-test` published 634 `RenamedPackage` rows of which **605 were libGDX core's**: the same
    * 605 rows, byte for byte, that `sge`'s own `decisions.tsv` already carries, in a file
    * whose reader is looking for the 29 that are the test module's. That is `ENGINE-LIMITS.md` D2 —
    * now its fifth instance, after `OmissionCheck`, `PortabilityCheck`, the port-map findings and
    * the collection closure check — and its conclusion is not "annotate them": a report a repository
    * cannot act on is not its report.
    *
    * WITHHELD, not sectioned. A second section in the same file would still have to be read past,
    * would still be diffed by anything comparing the artifact, and would still make "how many
    * decisions did this port make" a question with two answers. The rows are not lost: the module
    * that OWNS the declaration emits them, and it is the only module that can change them. The
    * COUNT is printed on every run, so "withheld" can never be mistaken for "none were made".
    *
    * Ownership is decided STRUCTURALLY (§4.56), never from the origin path — that is the lexical
    * comparison §5.4 documents as broken across a symlinked worktree, and it is the same climb
    * `PackageRenameTransform.ownedSymbols` and `PortMapTransform.ownedByBase` make. The roots are
    * `emitOrder` — the units this run CONVERTS — rather than the ones it writes, so a dropped type's
    * rename row stays beside the `DroppedType` row that explains it.
    *
    * A decision with NO subject (`SymId.None`) is kept: it is a statement about a policy KEY, not
    * about a declaration, and every such row this run makes is its own. A subject whose owner chain
    * reaches no unit is an EXTERNAL symbol — the frontend's own marker for "this program does not
    * declare it" — so no line of emitted code corresponds to it and the row is withheld with the
    * foreign ones.
    *
    * @return how many rows were withheld
    */
  private def retainOwnDecisions(program: Program, translated: PortRun.Translated): Int =
    if translated.foreign.isEmpty then 0
    else
      val roots = translated.emitOrder.map(_.symbol).toSet
      def mine(s: SymId, fuel: Int): Boolean =
        s != SymId.None && fuel > 0 &&
          (roots(s) || program.symbolOf(s).exists(sym => mine(sym.owner, fuel - 1)))
      val (kept, withheld) = translated.decisions.drain().partition(d => d.subject == SymId.None || mine(d.subject, 64))
      translated.decisions.recordAll(kept)
      withheld.size

  /** Write `decisions.tsv` beside the run's other artifacts, on every reporting run.
    *
    * Gated on exactly what the source map is gated on, so one switch turns the artifact layer off
    * and a unit-test JVM leaves nothing behind. Written even when EMPTY: a port with no policy made
    * no recorded decisions, and a header-only file says that, where a missing file cannot be told
    * from a run that never got this far — the same distinction `CheckReport` keeps for a check that
    * found nothing. */
  private def writeDecisions(log: DecisionLog, withheld: Int): Unit =
    if CheckReport.enabled then
      val p = Decision.write(CheckReport.runDir, log)
      say(s"decisions: ${log.size} (${log.summary})" +
        (if withheld == 0 then "" else s"; $withheld withheld — about a module this port only resolves against") +
        s" -> $p")

  /** Correlate a compiler or test-runner log back to the members and Java origins of THIS run,
    * IN-PROCESS.
    *
    * `CorrelateMain` is a second JVM, which is right for a shell script that has just run a
    * compiler and wrong for a porting program that drives the compile itself and already holds the
    * run directory. Both work; this is the one that does not fork.
    *
    * Every path is made ABSOLUTE by `CorrelateRun.Request.absolute` before use, including the ones
    * a caller passes. sbt's non-forked `run` has the SUBPROJECT as its working directory, so a
    * relative path that reads correctly in a shell resolves to nothing here and the correlation
    * silently reports "0 units" as if the port had no members.
    *
    * ==Gated on the ARTIFACT LAYER, like every other write this run makes==
    *
    * `None` when reporting is off, for the reason §5.1 states without exception: this writes
    * `errors.tsv`, `tests.tsv` and `correlate.txt` into `CheckReport.runDir`, and with the layer
    * off that path falls back to `<cwd>/port-report/<sun.java.command>/run-latest` — the SUBPROJECT
    * under a forked test JVM, i.e. the checkout. `CorrelateRun.run` creates it before it has even
    * validated its inputs, so an unreporting run left an empty artifact directory behind and then
    * threw `MissingInput` on the source map this run was never asked to write. A `git status` that
    * cannot tell a decision from an artefact is what §5.5's discipline rests on.
    *
    * The gate is here, at the one place that names `CheckReport.runDir`, and not in each caller —
    * a wrapper every caller must remember is a wrapper one caller will not. It is an `Option` and
    * not an empty `Result` on purpose: a `Result(regressed = false)` is indistinguishable from a
    * clean correlation, which is the "whole suite reported green from a log it never opened"
    * failure `CorrelateRun.MissingInput` exists to make impossible.
    *
    * @param extraSrcMaps other ports' maps, scoped `main`/`test`. A test suite's failure is
    *                     anchored on the LIBRARY member that threw, which lives in another port —
    *                     so pass the library's map to get a `main-frame` anchor instead of a
    *                     `test-frame` one.
    * @return the correlation, or `None` when the artifact layer is off and there is neither a
    *         source map to join through nor anywhere to publish the answer */
  def correlate(
      scalac: Option[Path] = scala.None,
      tests: Option[Path] = scala.None,
      extraSrcMaps: List[(String, Path)] = Nil,
  ): Option[CorrelateRun.Result] =
    Option.when(CheckReport.enabled) {
      val mine = CheckReport.runDir.resolve("srcmap.tsv")
      CorrelateRun.run(CorrelateRun.Request(
        srcmaps  = extraSrcMaps :+ (sourceSet.configName -> mine),
        scalac   = scalac,
        tests    = tests,
        out      = CheckReport.runDir,
        baseline = Some(CheckReport.baselineDir),
      ))
    }

  /** record a [[SubstitutionCheck]] result under `check`, and hand it straight back.
    *
    * These two were the only checks in the engine that reached stdout and never reached
    * `findings.tsv`, because they were inline filesystem code in one porting program. Recording
    * them here rather than inside `SubstitutionCheck` keeps that object a pure function of a
    * directory — the orchestrator is the layer that knows a run is happening. */
  private def record(check: String, fs: List[SubstitutionCheck.Finding]): List[SubstitutionCheck.Finding] =
    CheckReport.record(check, fs.map { f =>
      CheckReport.Finding(check, f.kind.toString, f.fqn, s"${f.fqn.replace('.', '/')}.scala", 0, f.render)
    })
    fs

  /** Every check named here must have registered a result. The persistence layer already
    * distinguishes "found 0" from "never ran"; this is the same guarantee one layer up, where the
    * decision to invoke a check is made. */
  /** …plus the lanes a run owes because of what its own MANIFEST declares.
    *
    * `PortRun.RequiredChecks` is what every run owes whatever it is configured as. A CONDITIONAL
    * lane cannot go in that set — a port with no `serviceProviders` key records nothing there and
    * requiring it would fail every other port — and it must not simply be left out either, because
    * then a run that stopped writing descriptors would report success with the row gone. So the
    * requirement is DERIVED from the same declaration the work is. */
  private def requiredChecks: Set[String] =
    PortRun.RequiredChecks ++
      (if manifest.exists(_.serviceProviders.nonEmpty) then Set(balticporter.tir.ServiceProviders.Name) else Set.empty)

  private def verifyRecorded(): Unit =
    if CheckReport.enabled then
      val missing = requiredChecks -- CheckReport.snapshot().keySet
      if missing.nonEmpty then
        sys.error(
          s"[$label] ${missing.size} check(s) produced no record: ${missing.toList.sorted.mkString(", ")}" +
            "  [§1(a) engine: PortRun ran but did not register these, so their numbers would silently " +
            "vanish from findings.tsv while stdout still showed them]"
        )

  /** the phases that actually RUN: the ENGINE's own idiom phases, the declared surface, then the
    * namespace rename LAST (§4.56).
    *
    * The idiom phases are woven by the RUN and not declared by the port, exactly as the rename is,
    * and for a reason §1 states rather than a convenience: an idiom transformer is §1(a) —
    * "is this interface single-abstract-method" is a fact about a class file and "is this body one
    * method" is a fact about a tree, so there is no library policy in it and '''a knob on an (a) is
    * the shape §1 forbids'''. Woven here they are also OUTSIDE the manifest `surface`, so no
    * `SurfacePolicy` fingerprint moves, no published port map changes and §1.5 owes nothing —
    * which is exactly right, because there is nothing for two modules to configure differently.
    *
    * @see [[idiomPhases]] for WHERE each one is placed, which is the whole of the D1 argument. */
  private def effectivePhases: List[Phase] =
    idiomPhases(declaredPhases) ++ PortRun.remedyPhases ++ renamePhase

  /** THE REMEDIES THIS RUN CAN ACTUALLY CARRY OUT — derived from what the run HOLDS, never listed.
    *
    * `Rewrite.accountedBy`'s shape one level up: a claim each source makes about itself, gathered per
    * run. A table of "the engine's remedies" kept beside this would be a second answer somebody has
    * to remember to edit, and its failure mode is the worst one available here — a port selects a
    * remedy the table forgot and is told it does not exist.
    *
    * Both halves are here because a residue lane has two kinds of producer: the pipeline's PHASES,
    * and the CHECKS the orchestrator calls, which are plain objects and not phases at all. */
  private def activeRemedies: RemedyVocabulary =
    RemedyVocabulary.from(effectivePhases.collect { case r: RemedySource => r } ++ PortRun.CheckRemedies)

  /** …and the KNOWN set: what this run can carry out, PLUS whatever the classpath declared and this
    * pipeline did not enable. The difference between the two is the whole of the staleness answer —
    * see [[PortRun.knownRemedies]]. */
  private def knownVocabulary: RemedyVocabulary = activeRemedies ++ knownRemedies

  /** THE IDIOM LAYER, WOVEN AT THE POSITION EACH PHASE WILL OCCUPY.
    *
    * `CLAUDE.md` §5's dry-run rule read in the other direction: '''a phase measures what it is
    * HANDED'''. A census that runs where its transformer will not run measures a tree the
    * transformer will never see, and over- or under-counts depending on which surface phase moved
    * what. So placement is not scheduling here — it is the measurement:
    *
    *   - the SAM TRANSFORMER and the `return this` census go FIRST, so the descriptors and the
    *     return types they match are java's own. A `CollectionsTransform` retarget moving
    *     `java.util.Comparator` to `scala.math.Ordering` changes what a SAM conversion would
    *     ASCRIBE to, and a phase asked afterwards would write a type java never named at that
    *     site. The SAM CENSUS that stood here through wave 0 is RETIRED, and by the rule that put
    *     it there: the transformer files one row per site considered — `Converted` or
    *     `Refused(guard)` — which IS the denominator, so a census beside it would be a second
    *     answer to its own question and would double every row in the lane (§4.6);
    *   - the BEAN COLLAPSE census that stood immediately before `bean-properties` is RETIRED for the
    *     same reason and by the same rule. That phase now DECIDES the collapse and files one row per
    *     configured pair — `Converted` or `Refused(guard)` — which IS the intersection the census
    *     published, taken at the position the census occupied and from the same `PolicyBinder`
    *     bindings. Two phases answering one question in two spellings is the disagreement §4.6 is
    *     about, seen in the report rather than in the tree, and it would have doubled every row in
    *     the lane. A port with no `bean-properties` phase still gets no `BeanCollapse` row, which is
    *     not a gap but the honest answer: there are no configured pairs to intersect;
    *   - `bean-properties` is handed `public-field-accessors`' own scope where the port declares
    *     both. It is the one contradiction the collapse cannot see for itself — that phase PUTS
    *     java-bean names on a field for a reflective framework to find and this one TAKES them off
    *     (`ENGINE-LIMITS.md` K21 face 2) — and the run is the only place that holds both policies.
    *     A COPY of the phase, never a mutation of it, and no second policy home: the scope stays the
    *     one the port wrote once.
    */
  private def idiomPhases(declared: List[Phase]): List[Phase] =
    val first = PortRun.wovenIdiomPhases
    // the K21-face-2 contradiction the collapse must refuse, and the ONE place both halves of it are
    // visible: a port's `public-field-accessors` scope is that phase's own policy, and handing it to
    // `bean-properties` here is what keeps it one policy with one home (§8.5's "Rejected").
    val exposed = declared.collectFirst {
      case p: balticporter.transform.PublicFieldAccessorTransform => p.scope
    }
    val spliced = declared.map {
      case b: balticporter.transform.BeanPropertyTransform => exposed.fold(b)(b.withExposed)
      case other                                           => other
    }
    first ++ spliced

  /** does this run resolve against sources OUTSIDE its own tree? That is the structural signature
    * of a dependent port — a root that is merely the run's own tree (self-resolution, which several
    * ports do) is not a second module and carries no agreement obligation. */
  private def foreignRoots: Boolean =
    val src = PortRun.real(frontend.sourceRoot)
    frontend.resolutionRoots.map(PortRun.real).exists(r => r != src)

  /** Locate each declared base's PUBLISHED port map, and decide whether it may be believed.
    *
    * Done HERE rather than in [[ManifestAgreement]] because it is filesystem work, and the check is
    * worth more as a pure function of three lists than as something that needs a run directory to
    * be testable — the same division as `record`.
    *
    * Two rules that are not negotiable:
    *
    *   - '''This module's own map is excluded''' (design risk R2). A module's map is an OUTPUT and
    *     never an input to its own run; consuming it would make a port's behaviour depend on its
    *     previous output, and `PortMapSpec` pins that deleting a module's own map and re-running
    *     gives byte-identical output. Both the manifest name and the run LABEL are excluded, because
    *     a port is free to let those differ.
    *   - '''A map proven STALE is refused, not merely annotated''' (design risk R1). It is dropped
    *     from the `BasePort` so the dynamic layer takes the re-derivation path for that base, and
    *     the reason travels as a finding. Using a stale entry and mentioning it in passing is the
    *     failure this mechanism exists to prevent.
    */
  /** THIS module's own `SurfacePolicy` fingerprint, for the map it publishes — the same value
    * `ManifestAgreement` compares (`PortManifest.fingerprint` over the effective surface), sorted
    * and digested.
    *
    * A module with NO manifest still publishes one (the digest of the empty list), so an empty
    * `policy=` in a map can only ever mean "published before schema 3". "This module declares no
    * surface policy" and "this engine could not say" are different answers, and a fingerprint that
    * conflated them would make the comparison silently inert for every port with an empty surface —
    * which is most of the corpus. */
  private def surfacePolicyFingerprint: String =
    PortMap.policyDigest(
      manifest.map(_.surfaceDigestInputs).getOrElse(effectivePhases.map(PortManifest.fingerprint)))

  /** …and the fingerprint of the BASE's manifest, as THIS run inherited it (§1.5 — a value the
    * dependent holds, never the base's build). What `PortMap.freshness` compares the published one
    * against. */
  private def basePolicyFingerprint(b: PortManifest): String =
    PortMap.policyDigest(b.surfaceDigestInputs)

  /** the bases' published contracts, discovered ONCE.
    *
    * A `lazy val` and not a call, because it is read in two places that must agree: the translation
    * builds `Surface` from it and the manifest check reports on it. Two discoveries of one file
    * within a run is D6.5's failure shape — the same artifact answering two questions differently —
    * and here it would also mean two filesystem walks and, under `Determinism.Full`, four. */
  private lazy val basePorts: List[ManifestAgreement.BasePort] = discoverBasePorts()

  private def discoverBasePorts(): List[ManifestAgreement.BasePort] =
    val chain = manifest.toList.flatMap(_.baseChain)
    if chain.isEmpty then Nil
    else
      val mine  = Set(label) ++ manifest.map(_.name)
      // THE PORT'S OWN SEARCH PATH, not the operator's. `PortMapTransform` reads its maps at
      // CONSTRUCTION time from this same value (`PortMap.searchPath`), and two loads of one artifact
      // answering differently is D6.5's failure shape — which is why the manifest holds it and a
      // flag does not.
      val found = PortMap.discover(PortMap.reportRoot, exclude = mine,
                                   configured = manifest.map(_.baseReports).getOrElse(Nil))
        .map(p => p.module -> p).toMap
      // the same roots `partitionUnits` and `sharedSurface` spell through §5.4's helper, spelled the
      // same way. These reach `PortMap.freshness`, which only probes them for existence today — so
      // this is a spelling inconsistency and not yet a bug, and it is fixed for that reason: the
      // next reader to add a prefix test against these roots would inherit the §5.4 failure
      // silently, and two independent reviews flagged the divergence before anything else did.
      val roots = (frontend.resolutionRoots ++ List(frontend.sourceRoot)).map(balticporter.core.RealPath.of).distinct
      chain.map { b =>
        found.get(b.name) match
          case scala.None => ManifestAgreement.BasePort(b)
          case Some(pub) =>
            pub.map match
              case Left(err) => ManifestAgreement.BasePort(b, scala.None, pub.source, stale = List(err))
              case Right(m0) =>
                PortMap.freshness(m0, balticporter.core.EngineInfo.fingerprint, roots,
                                  basePolicyFingerprint(b)) match
                  case PortMap.Freshness.Fresh          => ManifestAgreement.BasePort(b, Some(m0), pub.source)
                  case PortMap.Freshness.Stale(r)       => ManifestAgreement.BasePort(b, scala.None, pub.source, stale = List(r))
                  case PortMap.Freshness.Unverified(r)  => ManifestAgreement.BasePort(b, Some(m0), pub.source, unverified = List(r))
      }

  /** Every type this run RESOLVED AGAINST but did not convert — the shared surface, as this run
    * modelled it.
    *
    * The upstream name is rebuilt from the unit's ORIGIN: the directory under the resolution root
    * gives the package, and the unit's own simple name gives the rest. Not from the file name,
    * because a Java file may declare a package-private second top-level type, and not from
    * `fullName`, because after a package rename `fullName` is not the upstream name at all — which
    * is precisely the disagreement being checked for. */
  private def sharedSurface(program: Program, foreign: List[Tree.ClassDef]): List[ManifestAgreement.SharedType] =
    if foreign.isEmpty then Nil
    else
      val roots = frontend.resolutionRoots.map(PortRun.real).sortBy(-_.length)
      foreign.flatMap { u =>
        for
          sym <- program.symbolOf(u.symbol)
          pkg <- upstreamPackage(u.origin.javaPath, roots)
        yield ManifestAgreement.SharedType(
          upstreamFqn = if pkg.isEmpty then sym.name else s"$pkg.${sym.name}",
          emittedFqn  = sym.fullName,
          substituted = Substituted.tags(sym),
        )
      }

  private def upstreamPackage(javaPath: String, roots: List[String]): Option[String] =
    if javaPath.isEmpty then scala.None
    else
      val real = PortRun.real(Path.of(javaPath))
      val sep  = java.io.File.separatorChar
      roots.collectFirst { case r if real.startsWith(r) =>
        val rel = real.substring(r.length).dropWhile(_ == sep)
        val cut = rel.lastIndexOf(sep)
        if cut < 0 then "" else rel.substring(0, cut).replace(sep, '.')
      }

  /** ONE translation. The determinism gate is `verifyDeterminism`, run later — see its scaladoc for
    * why it cannot happen here any more. */
  private def translate(): PortRun.Translated = translateOnce()

  private def determinismViolation(what: String, t: PortRun.Translated, diffs: List[Tree.ClassDef]): Nothing =
    val names = diffs.flatMap(u => t.program.symbolOf(u.symbol).map(_.fullName)).take(10)
    sys.error(
      s"[$label] determinism violation ($what): ${diffs.size} unit(s) differ between two runs — " +
        names.mkString(", ") +
        "  [§1(a) engine: the emitter or a phase leaks unordered iteration; every diff-based " +
        "workflow (baselines, the action cache, before->after counts) is invalid until it is fixed]"
    )

  private def translateOnce(): PortRun.Translated =
    val types   = SpoonTir.buildModel(frontend, lenient = lenient)
    // The run's OBLIGATION LOG (`DESIGN.md` §2.8). Created HERE, inside `translateOnce`, for the
    // reason the decision log and the policy binder are: `Determinism.Full` translates twice, and a
    // log shared between the two translations would report every consult twice. `fatal = false` —
    // a port run COUNTS an undischarged obligation, because a run that died on an incomplete rule
    // is a run that produces no diagnostics at all; the testkit is where a hole is an error.
    val catalog = new balticporter.catalog.CatalogLog(fatal = false)
    // `frontend.preservedAnnotations` — WHICH argument-bearing annotation families this port claims
    // on a TYPE (`ENGINE-LIMITS.md` T16). Empty is the default and the no-op; it travels with the
    // frontend config because it is a fact about what the HARVEST carries, and the harvest is the
    // frontend's.
    val parsed  = SpoonTir.fromTypes(types, policySubs, catalog, frontend.preservedAnnotations)
    // ---- POLICY BINDING (§8.1) — every declared key resolved ONCE, before any phase runs ----
    //
    // Before the pipeline and not inside it, for two reasons that are not scheduling. Every policy
    // key is written in the UPSTREAM namespace and the package rename runs LAST (§4.56), so binding
    // here resolves each key against the names its author wrote and a phase's POSITION can no longer
    // change what its keys mean. And "did this key fire?" becomes a property of the policy and the
    // program rather than of the order phases happened to run in — which is what lets one value own
    // the never-fired answer instead of five private `var report`s.
    //
    // The binder is per-TRANSLATION for the reason the decision log is (`Determinism.Full`
    // translates twice): a value one run owns, never a process-global table (§5.1).
    val binder = new PolicyBinder(parsed, parsed.members, runScope(parsed))
    bindDeclaredPolicy(binder)
    // …and the PER-LOCATION REMEDY SELECTIONS, bound through the same binder and at the same moment,
    // for the same two reasons: every key is written in the upstream namespace, and "did this
    // selection fire?" must be a property of the policy and the program rather than of phase order.
    // The plan rides on the binder because a selection IS a bound key (`ResolutionPlan`), so a phase
    // that already binds its own policy reaches it with no wiring anybody can forget.
    binder.resolving(ResolutionPlan.of(
      manifest.map(_.effectiveResolutions).getOrElse(Map.empty),
      knownVocabulary, activeRemedies.byId.keySet, binder))
    // `runTraced`, so the phases' DECISIONS travel with the program they produced. The log belongs
    // to THIS translation: `Determinism.Full` translates twice and the run keeps the first, which
    // is only coherent because neither log is shared (CLAUDE.md §5.1).
    // The binder is handed to the pipeline, which binds every `PolicyBound` phase before the first
    // one runs — a phase run unbound matches nothing, silently.
    // …and the REWRITE log, which the pipeline fills by OBSERVING each phase rather than by asking
    // it (`Rewrite`): a value this translation owns, for the same reason the two logs above are.
    val rewrites = new RewriteLog
    // …and the IDIOM log, which the pipeline drains from every `IdiomPhase` as it returns: a value
    // this translation owns, for the reason the three above are (`Determinism.Full` translates
    // twice, and a shared log would double every candidate).
    val idioms   = new IdiomLog
    val (program, decisions) = Pipeline.runTraced(parsed, effectivePhases, binder, catalog, rewrites, idioms)
    val plan    = RuntimePlan.of(effectivePhases, runtimeMode)
    // `externalConcrete` is DERIVED, never passed in: a caller who has to remember it is a caller
    // who forgets it, and forgetting it silently disables diamond-conflict detection against an
    // injected parent.
    // the emitter READS this log to render porter notes and never writes to it — its own decisions
    // come back as `TirEmitter.ownDecisions` and are recorded once, by `recordRunDecisions`.
    val (mine, theirs) = partitionUnits(program)
    // §8.3's view, built BEFORE the emitter because the emitter's constructor runs the constructor
    // funnel, and the funnel's fixpoint is the first thing that must stop spanning the base.
    // `mine` is the same partition every other owner question in this file uses, realpathed on both
    // sides (§5.4) — so the six climbs that each answered "mine or my base's?" differently now have
    // one root set.
    val surface = new balticporter.core.PublishedSurface(
      program, mine, basePorts.flatMap(b => b.map.map(b.name -> _)))
    // the emitter READS this log to render porter notes and never writes to it — its own decisions
    // come back as `TirEmitter.ownDecisions` and are recorded once, by `recordRunDecisions`.
    // …and the emitter takes the run's OBLIGATION log, because it is the second discharge surface
    // (`balticporter.catalog.Rendering`): most `JS-S` rows are decided while rendering and the
    // frontend has nothing to say about them. THIS emitter and no other — the determinism twin
    // below re-renders every unit, and a shared log would count every consult twice.
    val emitter = new TirEmitter(program, plan.concreteMembers, provenance, decisions, preview, bestEffort,
                                 Some(surface), catalog = catalog)
    PortRun.Translated(program, plan, emitter, mine, theirs, cache.map(new ActionCache(_, true)),
                       decisions, binder, surface, parsed, catalog, rewrites, idioms)

  /** Ask the binder about every key this run DECLARES — its drops, and every keyed phase's own.
    *
    * The drops bound here are the EFFECTIVE ones, inherited keys included, because
    * `PortManifest.inheritedKeysNeverFired` is a real report about a base's key that did not fire
    * HERE. Which of them reach the `policy` check is a separate decision, taken where that check is
    * assembled and taken the same way it always was: a §1(b) finding says "fix this key in the
    * library's manifest", and an inherited key lives in the base's.
    */
  private def bindDeclaredPolicy(binder: PolicyBinder): Unit =
    policySubs.dropTypes.toList.sorted.foreach(k =>
      binder.bindType("substitutions", "Substitutions.dropTypes", k))
    policySubs.dropMethods.toList.sorted.foreach(k =>
      binder.bindMembers("substitutions", "Substitutions.dropMethods", k))
    // the PHASES' keys are bound by `Pipeline.runTraced`, so no caller of it can forget.

  /** Units this run CONVERTS, as opposed to units it merely resolved against.
    *
    * `FrontendConfig.resolutionRoots` exists so a second source set can see the first's Java
    * without re-emitting it, and the model spans both — so "which units are mine" is a question
    * every port asks and the engine should answer once. Decided from the unit's `Origin`, which is
    * the only thing that survives a package rename: after one, a unit's FQN has nothing to do with
    * the file it came from. A unit under `sourceRoot` is always converted (even if a resolution
    * root happens to contain it); anything else under a resolution root is not; a unit with no
    * usable origin is converted, because refusing to emit on a missing origin would be a silent
    * omission — exactly the failure class this engine keeps finding.
    *
    * Compared through `toRealPath`, on BOTH sides. A source root reached across a symlink — which
    * is the normal case in a git worktree, and was the case that made this return every unit in
    * the model on its first run — is lexically unrelated to the path the parser recorded, and a
    * prefix test then matches nothing while looking correct. */
  /** What a PHASE may conclude about ITSELF, built before the pipeline runs (`RunScope`).
    *
    * Two facts, and neither is derivable from the `Program` a phase is handed. The first is
    * `partitionUnits` — the same realpathed origin split every other owner question in this file
    * uses (§5.4) — computed over the PARSED program, because origins are what a phase cannot change
    * and unit symbols are stable across the pipeline. The second is the merge contract's own answer
    * to "which of this phase's keys did MY manifest contribute" (`DESIGN.md` §8.13): `ownKeys` where
    * the fold merged this module's instance into a base's, and — for a phase this module declares
    * that no base has a counterpart for, the shape with no constraint on it at all — every subject
    * that instance holds. A phase this module does not declare is ABSENT from the map, which is the
    * "no filter" answer: every key it holds is a base's, and the base's own run applied it
    * identically.
    *
    * Both halves are the identity for a BASE port: no resolution roots means every unit is emitted,
    * so nothing a phase asks can refuse anything.
    */
  private def runScope(parsed: Program): RunScope =
    RunScope.of(partitionUnits(parsed)._1.map(_.symbol).toSet,
                manifest.map(_.contributedSubjects).getOrElse(Map.empty))

  private def partitionUnits(program: Program): (List[Tree.ClassDef], List[Tree.ClassDef]) =
    if frontend.resolutionRoots.isEmpty then (program.units, Nil)
    else
      val src   = PortRun.real(frontend.sourceRoot)
      val other = frontend.resolutionRoots.map(PortRun.real)
      program.units.partition { u =>
        val p = u.origin.javaPath
        if p.isEmpty then true
        else
          val real = PortRun.real(java.nio.file.Path.of(p))
          real.startsWith(src) || !other.exists(real.startsWith)
      }

  private def wipe(dir: Path): Unit =
    if Files.exists(dir) then Files.walk(dir).iterator().asScala.toList.reverse.foreach(Files.delete)

  private def write(p: Path, text: String): Unit =
    Files.createDirectories(p.getParent)
    Files.writeString(p, text)

object PortRun:

  /** Every symbol a unit DECLARES — the class, its nested classes, and every member of each.
    *
    * The subject set `NoteCoverageCheck` joins on, and it must be exactly "what has a declaration
    * in the file that was written". Not the symbol table filtered by owner: that also holds
    * parameters, locals and type parameters, none of which the emitter renders a note above, so a
    * decision about one would report as an uncovered finding forever. Read from the TREE, which is
    * the thing that was emitted.
    *
    * WHICH class defs, through `StandardTraversal.allClassDefs` and never a `cd.body` recursion
    * (§3): a METHOD-LOCAL class is a `BlockStatement` (JLS 14.3), not a type member, so a walk over
    * class bodies answers *no nested types here* about a type the program declares — and the
    * consequence HERE is the quietest one that shape has. The subject set is what
    * `NoteCoverageCheck` joins on, and a decision about a subject that is not in it is EXCLUDED
    * DELIBERATELY (a policy key that matched nothing, a type another module owns). So a local
    * class's members were not an uncovered finding; they were a silent EXEMPTION from note
    * coverage, indistinguishable from the three cases that exemption is for.
    *
    * The emission half moves with it, which is M8's rule: a local class is emitted through
    * `TirEmitter.statArm`'s `ClassDef` arm into `classDef`, which renders `declNotes` for every
    * non-top-level type and the ordinary `def`/`val` notes for its members. So the check can now
    * ask, and the emitter can already answer. */
  def declaredSymbols(cd: Tree.ClassDef, into: collection.mutable.Set[SymId])(using Program): Unit =
    StandardTraversal.allClassDefs(cd).foreach { c =>
      into += c.symbol
      c.body.foreach { case d: Definition => into += d.symbol; case _ => () }
      c.enumCases.foreach { ec =>
        into += ec.symbol
        ec.body.foreach { case d: Definition => into += d.symbol; case _ => () }
      }
    }

  /** a path with symlinks resolved, falling back to lexical normalisation when it does not exist
    * (a synthetic origin, a root that was never created) — §5.4's rule, which
    * [[balticporter.core.RealPath]] is the one implementation of. Kept as a `String`-returning
    * alias because that is what this file's prefix tests compare. */
  def real(p: Path): String = balticporter.core.RealPath.str(p)

  // =========================================================================================
  // a SYNTHESISED unit, and the one module allowed to write it (ENGINE-LIMITS.md §13 O5)
  // =========================================================================================

  /** Did a phase MINT this unit, rather than the frontend parse it out of a Java file?
    *
    * Read from the ORIGIN, which is the only thing a rename cannot move (§4.57) — and read the way
    * `PortMap.javaPaths` already reads it, because `Origin.synthetic`'s path is the placeholder
    * `<synthetic>` and not the empty string, so an emptiness test alone would classify every minted
    * unit as parsed. Anything in angle brackets is a placeholder and not a path. */
  def isSynthesised(o: Origin): Boolean =
    o.javaPath.isEmpty || o.javaPath.startsWith("<")

  /** A unit this run would WRITE that no Java file produced, at an FQN a BASE module already emits.
    *
    * @param fqn         the EMITTED name — both sides of this comparison are emitted names, which is
    *                    §4.56's rule for any artifact joining policy to observed code: the minted
    *                    unit has been through this run's rename phase and the base's `emitted`
    *                    column has been through the base's, and the two agree because a dependent
    *                    inherits the rename policy (§1.5).
    * @param base        which base module's published map claims it.
    * @param disposition how the base's map describes it, so the reader can tell "the base emits this
    *                    type" from "the base is where the injected replacement ships". */
  final case class SyntheticClaim(fqn: String, base: String, disposition: String):
    def render: String =
      s"$fqn — synthesised by a phase in THIS run, and `$base` already emits a type at that name " +
        s"($disposition). Two definitions of one FQN do not compile, and an opaque type cannot even " +
        "be duplicated harmlessly: opacity is per-DEFINITION, so the copy's own accessors stop " +
        "type-checking against the first definition's abstract type"

  /** The refusal, as a pure function of what this run would write and what its bases published —
    * testable without a run directory, the same division `discoverBasePorts` documents.
    *
    * A DROPPED type in a base's map is not a claim: the base does not emit it, so nothing collides.
    * Only a name the base actually writes is one this run may not also write.
    *
    * ==NO MAPS ADMITS, and that is argued rather than inherited==
    * `bases = Nil` returns `Nil`, so a run with no usable base map writes its synthesised units. Note
    * that `DESIGN.md` §8.13's `governs` screen, asked the same "I have no map" question, REFUSES —
    * and the asymmetry is deliberate in both directions:
    *
    *   - **`Nil` here conflates two things and must.** A base port, a single-module port, a spec and
    *     `DebugEmit` all have no bases at all, and every one of them is the module that MUST mint.
    *     Refusing on an empty list would fail the only run allowed to write the type. The screen's
    *     input is a POLICY KEY an operator wrote against a named base, which has no such reading.
    *   - **the loud half already fires.** A base that published no map, or one proven stale, is
    *     `BaseMapMissing`/`BaseMapStale` beside this line, with "run the base port once" as the fix.
    *     The screen has no equivalent because a policy key is checked once, before anything runs.
    *   - **this is a BELT, and the fence in front of it now refuses for itself.** A phase that mints
    *     fences its mint on `RunScope`, and `PrimitiveToOpaqueTransform.refuseSpanningHints` makes
    *     that fence answer rather than guess when its hints straddle two modules. A belt that admits
    *     by default is only a hazard while it is the sole floor.
    *
    * So it stays as it is. What must not happen is this being read as the same answer the screen
    * gives, or a fence being written that leans on it. */
  def claimedSynthetic(program: Program, synthesised: List[Tree.ClassDef],
                       bases: List[(String, PortMap.Map0)]): List[SyntheticClaim] =
    if synthesised.isEmpty || bases.isEmpty then Nil
    else
      val claims: Map[String, (String, String)] =
        bases.reverse.flatMap { (name, m) =>
          m.types.iterator
            .filter(e => e.emitted.nonEmpty && e.disposition != PortMap.Disposition.Dropped)
            .map(e => e.emitted -> (name, e.disposition.toString))
        }.toMap
      synthesised.iterator
        .flatMap(u => program.symbolOf(u.symbol).map(_.fullName))
        .flatMap(fqn => claims.get(fqn).map((b, d) => SyntheticClaim(fqn, b, d)))
        .toList.distinct.sortBy(c => (c.fqn, c.base))

  /** The [[BaseSurface]] findings for a run's gaps — a PURE function, so the negative case ("an
    * unconsumed `Unknown` must count") is testable without a two-module port on disk.
    *
    * `kind` splits the two halves the §8.3 rule splits: an `Unknown` that shaped emitted text FAILS
    * the run, and one that did not is a finding. Both are recorded, because a run that dies must
    * leave the artifact naming what killed it. There is no origin — a contract question is about a
    * SYMBOL, not a line of Java — so the path is empty rather than a plausible-looking guess (§4.57's
    * rule, from the other side), and the SUBJECT is the FQN a reader greps for. The `fix` rides in
    * `detail` so an agent holding only `findings.tsv` still gets §1's classification (§4.45). */
  /** DOES THIS RUN'S COLLAPSE VERDICT AGREE WITH THE ONE THE BASE PUBLISHED? A pure function, for
    * `baseSurfaceFindings`' reason — the disagreement is testable without a two-module port on disk.
    *
    * ==Why the question exists at all==
    * Every other §1(b) policy is a TABLE, and a dependent inherits the base's instance, so the two
    * modules agree by construction. `BeanCollapse`'s verdict is not a table: it is DERIVED, from
    * `overriddenBelow` over the run's descendants, `concreteRelative` over the run's override
    * closure, `writtenSymbols` over the run's assignments and `closureOf(_).isAnchored` over the
    * run's parents. Every one of those ranges over the WHOLE PROGRAM, and a dependent's model
    * CONTAINS its base's units plus its own. So a dependent that declares ONE subclass overriding
    * the accessor — or one write of the field, for a `val` — re-derives `Refuse` for a pair the base
    * COLLAPSED, and emits `def getW()` where the base emitted `var w`.
    *
    * Nothing else can see it. The manifest entry is identical on both sides, so `surfaceFingerprint`
    * is EQUAL and `SurfaceDivergence` has nothing to compare; the phase agrees with itself, so
    * `idiom(refused)` reports an honest refusal with a real guard; every count is flat; and the two
    * ports each compile alone. §1.5's failure exactly, arriving through a derivation rather than
    * through a table.
    *
    * ==What is compared, and what is NOT evidence==
    * The base's answer is read from its published map's `MemberShape.form`, which exists precisely
    * because the ABSENCE of an accessor row is not evidence — a `dropMethods` entry produces the
    * same absence. The question is asked only where the base's map has a TYPE row for the pair's
    * OWNER, which is §1.5's rule read here: ask what the base EMITS, never what its `governs` claim
    * says, and never about a pair this module declares over its own type.
    *
    * Three answers, and the third is not the second:
    *
    *   - the two agree — nothing;
    *   - they DISAGREE — a FATAL gap naming both, because this run has already emitted the losing
    *     shape and `Surface.Gap.fatal` is set by the asker (`DESIGN.md` §8.3);
    *   - the base emitted the type and published NO member row for the pair — an `Unknown`, and a
    *     non-fatal gap. A run that assumed "not collapsed" there would be manufacturing the base's
    *     answer out of a map that does not carry it, which is §4.6's fabricated fact.
    *
    * @param idioms  what this run's `BeanPropertyTransform` DECIDED — the verdict is read from the
    *                phase's own log and never re-derived here (§4.6, `ENGINE-LIMITS.md` K2.5)
    * @param bases   each declared base's name and its published map, in `baseChain` order
    * @param pairs   the port's configured pairs, `key -> "getX/setX"`
    * @param targetOf the shape each key asked for, as the phase itself answers it */
  /** what [[collapseDivergence]] found AND how many verdicts it compared to find it.
    *
    * The denominator is not decoration and it is §3's own rule read at a check this wave built: a
    * dependent that reports `base-surface 0` because sixty verdicts AGREED and one that reports 0
    * because the comparison never ran are indistinguishable from the outside, and the second is
    * every way this can silently stop working — a base map that was not discovered, a pairs table
    * the merge did not carry, a type row the base stopped emitting. `checked` is the only thing that
    * can tell them apart, so the run prints it beside the gaps. */
  final case class CollapseAgreement(checked: Int, gaps: List[balticporter.tir.Surface.Gap])

  def collapseDivergence(idioms: balticporter.tir.IdiomLog,
                         bases: List[(String, balticporter.core.PortMap.Map0)],
                         pairs: Map[String, String],
                         targetOf: String => balticporter.transform.BeanPropertyTransform.Target)
      : CollapseAgreement =
    import balticporter.core.PortMap
    import balticporter.tir.{IdiomKind, IdiomVerdict, Surface}
    if bases.isEmpty || pairs.isEmpty then CollapseAgreement(0, Nil)
    else
      // what the base EMITTED, per base: the types it wrote, and the `form=` of each member row.
      val emittedTypes = bases.map((m, map) =>
        m -> map.entries.filter(e => e.kind == "type" &&
          e.disposition != PortMap.Disposition.Dropped).map(_.upstream).toSet).toMap
      val memberForm = bases.map((m, map) =>
        m -> map.entries.filter(e => e.kind == "member" &&
          e.disposition != PortMap.Disposition.Dropped)
          .map(e => e.upstream -> e.memberShape.form).toMap).toMap
      val mine = idioms.all.iterator.collect {
        case c if c.kind == IdiomKind.BeanCollapse => c.subject -> (c.verdict match
          case IdiomVerdict.Converted => targetOf(c.subject).config
          case _                      => "")
      }.toMap
      // every (pair, base) the question is OWED for — the denominator, computed once and used for
      // both, so the count and the findings can never be about different populations.
      val asked = for
        (key, derived) <- mine.toList.sortBy(_._1)
        owner           = key.takeWhile(_ != '#')
        (module, _)    <- bases
        if emittedTypes(module).contains(owner)
      yield (key, derived, module)
      val out = for
        (key, derived, module) <- asked
        gap            <- memberForm(module).get(key) match
          case Some(published) if published == derived => Nil
          case Some(published) =>
            List(Surface.Gap(key,
              s"the collapse verdict DISAGREES with the base: it published " +
                describe(published) + s" and this run derived " + describe(derived) +
                " — the collapse's verdict is DERIVED over the whole program (`overriddenBelow` " +
                "reaches this run's subclasses, `writtenSymbols` this run's assignments), so a " +
                "dependent that declares one override or one write re-decides a base declaration's " +
                "shape. The manifest entry is identical on both sides, so no fingerprint moves and " +
                "no count moves",
              Some(module), fatal = true,
              fix = "§1(b) PER-LIBRARY: this module may not re-shape the base's surface (§1.5). " +
                s"Either drop `$key` from the pairs table so the base's shape stands, or move the " +
                "declaration that changed the derivation (the overriding accessor, or the write) " +
                "and re-run the BASE so both modules derive the same verdict"))
          case scala.None if derived.isEmpty => Nil
          case scala.None =>
            List(Surface.Gap(key,
              s"this run COLLAPSED a pair on a type the base emits, and the base's map carries no " +
                s"member row for it — so ${describe(derived)} could not be checked against what the " +
                "base published. Assuming the base did not collapse it would be a fabricated fact",
              Some(module), fatal = false,
              fix = "§1(b) PER-LIBRARY, OPERATIONAL: re-run the base port with this engine so its " +
                "port map carries a `form=` row for this member"))
      yield gap
      CollapseAgreement(asked.size, out.distinct)

  private def describe(form: String): String =
    if form.isEmpty then "NO collapse (the `def` pair)" else s"a collapsed `$form`"

  def baseSurfaceFindings(gaps: List[balticporter.tir.Surface.Gap]): List[CheckReport.Finding] =
    gaps.map { g =>
      CheckReport.Finding(BaseSurface, if g.fatal then "shaped emitted text" else "unanswered",
                          g.subject, "", 0,
                          g.why + g.module.fold("")(m => s"  [base: $m]") + s"  [${g.fix}]")
    }

  /** THE IDIOM PHASES EVERY RUN CARRIES, whatever its manifest says — constructed HERE so the one
    * list is the one every door opens.
    *
    * They are §1(a) and therefore reach no `TransformFactory`: a factory would make them nameable in
    * a port `.conf`, which is the knob §1 forbids on an (a). The consequence is that they are also
    * invisible to every consumer of the SPI — and `DebugEmit` is one, so `--phases sam-anon->lambda`
    * answered "unknown transform" for a phase that runs in every port. §4.6's promise is that "is
    * this phase even responsible" costs one run and no diff; a phase the tooling cannot name breaks
    * exactly that, and it breaks it for the two phases nobody can turn off.
    *
    * A LIST and not a second construction site, for `DESIGN.md` §5.7's reason one level down: two
    * places building the woven set would be free to drift, and the drift would be a diagnostic that
    * models a pipeline the run does not have. Fresh instances per call, because a phase carries the
    * buffers it fills. */
  def wovenIdiomPhases: List[Phase] =
    List(new balticporter.transform.SamLambdaTransform, new balticporter.transform.ReturnThisCensus)

  /** THE CHECK-SIDE REMEDY SOURCES — the objects that mint a residue finding and can also answer it.
    *
    * A list beside [[wovenIdiomPhases]] and for its reason: half of the lanes a remedy could drain
    * are produced by plain objects the orchestrator calls, which are not phases and cannot be
    * collected out of the pipeline. Naming them here is what makes
    * [[balticporter.tir.RemedyVocabulary]]'s ACTIVE set complete without a table of ids anybody has
    * to maintain — each object still declares its own menu.
    *
    * A check that gains a menu adds itself here. It shipped EMPTY with the plumbing, so the
    * mechanism's own arrival was provably flat on all fifteen lanes; what a name on this list buys
    * is that a `.conf` can VALIDATE the id at load (`PortConfig.knownRemedies` reads it), which is
    * how a typo is told apart from a real remedy whose phase a port forgot to enable.
    *
    * The three BOUNDARY lanes joined them and are a DIFFERENT SHAPE, which is worth one sentence:
    * every one of their entries is `accept`-shaped and NOT emission-affecting, because every act
    * that changes the emission at one of those seams already has a manifest key and a remedy
    * restating one would be a second spelling of it (`DESIGN.md` §8.16). So they need no `Apply`
    * phase below — nothing is carried out, a row is MOVED — and the check that mints the residue
    * drains it where it records. */
  /** WHERE A PORTABILITY SITE'S SELECTION IS ASKED FOR — the site's enclosing DEFINITION, or the
    * nearest owner above it that a key can name.
    *
    * `PortabilityCheck.Violation.enclosing` is the nearest enclosing definition, which for a site
    * inside a method body is routinely a LOCAL — the corpus's one `ServiceLoader` row is owned by a
    * val called `loader`. A selection key is per MEMBER (`Resolution`), so asking only at `enclosing`
    * answers `None` for exactly the sites a port most wants to speak about, and the fix is not a
    * finer key: it is to ask at the declaration the key CAN name. Returns `enclosing` unchanged when
    * nothing above it was selected, so an undrained row keeps the position it always had. */
  private[runner] def acceptSubject(program: Program, v: PortabilityCheck.Violation,
                                    plan: balticporter.tir.ResolutionPlan): SymId =
    ownerChain(program, v.enclosing)
      .find(id => plan.selected(id, balticporter.tir.PortabilityCheck.AcceptJvmOnly).isDefined)
      .getOrElse(v.enclosing)

  /** a symbol and every owner above it, nearest first. Fuel-bounded for `Program.owned`'s reason: a
    * corrupt owner cycle must not hang a check. */
  def ownerChain(program: Program, from: SymId): List[SymId] =
    def climb(s: SymId, fuel: Int): List[SymId] =
      if s == SymId.None || fuel == 0 then Nil
      else s :: program.symbolOf(s).map(sym => climb(sym.owner, fuel - 1)).getOrElse(Nil)
    climb(from, 64)

  val CheckRemedies: List[balticporter.tir.RemedySource] =
    List(HeapPollutionCheck, OverloadRiskCheck,
         balticporter.transform.CollectionBoundaryCheck,
         balticporter.transform.ContextSeamCheck,
         balticporter.transform.NullabilityBoundaryCheck, balticporter.tir.PortabilityCheck)

  /** …and the phases that CARRY those menus out — woven, never declared by a port.
    *
    * A resolution has to be recorded BEFORE emission: an applied one produces a `Decision`, the
    * emitter renders that decision as a porter note while it writes the member (§4.575), and
    * `recordRunDecisions` runs before the first file is written. A check runs after all of it, so
    * the object that DECLARES a menu and the phase that CARRIES it out are two things — see
    * `HeapPollutionCheck.Apply`, whose doc is the argument in full.
    *
    * A menu whose entries only MOVE a row needs no entry here, which is why the boundary trio has
    * none: there is nothing to carry out, and the check that mints the residue drains it at the
    * moment it records (its own `resolved`).
    *
    * Woven for [[wovenIdiomPhases]]'s reason, arrived at from the other side: these phases have no
    * constructor policy at all. Their whole configuration is `PortManifest.resolutions`, which is
    * already a manifest field and already part of the surface fingerprint, so there is nothing for
    * two modules to configure differently, nothing for a `surface` line to add, and no
    * `SurfacePolicy` fingerprint to move. With no selections each is a no-op on its first line,
    * which is §1(b)'s empty-parameter rule read at a whole phase.
    *
    * AFTER the declared surface and BEFORE the rename: a remedy answers what the port's own phases
    * left, and every key is written in the upstream namespace (§4.56) — though the keys are bound at
    * the front of the run either way, so this position decides what the phase SEES and not what it
    * binds. Fresh instances per call, because a phase carries the state it binds. */
  def remedyPhases: List[Phase] =
    List(new HeapPollutionCheck.Apply, new OverloadRiskCheck.Apply)

  /** Every check's name as it appears in `counts.tsv`. Named here, in the orchestrator, because the
    * orchestrator is now the only thing that records: a check is a pure function of a `Program` and
    * does not know it is being persisted. */
  val Signature            = "signature"
  val Omissions            = "omissions"
  val PortabilityAll       = "portability(all)"
  /** …read off the CHECK, because a REMEDY names this lane too and `Remedy.lane` asks for a constant
    * rather than a literal so a rename is a compile error. Three literals used to spell it — here,
    * `PortabilityCheck.AcceptJvmOnly` and `RemediationTransform.Lane` — which agree by inspection and
    * cannot be made to disagree by a compiler. */
  val PortabilityEmitted   = PortabilityCheck.EmittedLane
  val PortabilityInjected  = "portability(injected)"
  /** …and the lane an APPLIED remedy selection files under too — one name, read off the api value
    * that builds the row, so the check and the finding cannot drift apart
    * ([[balticporter.tir.Resolution.Check]] says why they share a lane at all). */
  val Remediation          = balticporter.tir.Resolution.Check
  /** the two [[SubstitutionCheck]] halves. */
  val SubstitutionEmitted  = "substitution(emitted)"
  val SubstitutionDangling = "substitution(dangling)"
  val Policy               = "policy"
  val Manifest             = "manifest"
  /** references a base module's PUBLISHED port map says are not in its output. */
  val PortMapCheck         = "port-map"
  /** contract questions about a BASE type this run could not answer (`DESIGN.md` §8.3).
    *
    * The FATAL half fails the run, which is §8.3's enforcement and deliberately not a check — "a
    * drift check is rejected on evidence". The other half is specified as a FINDING and was a line of
    * stdout: an `Unknown` no emission consumed. A number nobody persists is a number nobody diffs,
    * which is exactly what `counts.tsv` exists to prevent, and it is the only place a base-surface
    * question can start appearing without anything saying so. */
  val BaseSurface          = "base-surface"
  /** comments in the upstream Java that did not reach the emitted Scala (a LICENCE among them). */
  val TriviaDropped        = "trivia"
  /** …the ones the emitter's backstop had to PUT BACK: a counted residue, never a success. */
  val TriviaRecovered      = "trivia(recovered)"
  /** …and the ones documenting a member this port DROPS, derived from the run's own drops. */
  val TriviaDeliberate     = "trivia(deliberate)"
  /** the port's JDK wall — every `java.*` member the emitted code still calls, classified. */
  val JdkSurface           = JdkSurfaceCheck.Name
  /** the IDIOM layer's three lanes — what it converted, what it declined and why, and what it moved
    * and did not rewrite ([[balticporter.tir.IdiomCheck]]). */
  val IdiomConverted       = IdiomCheck.Converted
  val IdiomRefused         = IdiomCheck.Refused
  val IdiomResidue         = IdiomCheck.Residue

  /** Every check a run MUST have recorded by the time it finishes. Named rather than derived,
    * because the property being asserted is "the orchestrator invoked all of them" — deriving the
    * list from what was invoked would assert nothing. Adding a check to `PortRun` means adding it
    * here, and forgetting to fails the next run rather than shipping a silently narrower report.
    *
    * This is the guarantee that made moving `record` out of the checks safe. The checks used to
    * record themselves so a caller could not forget them; now `PortRun` calls every one of them and
    * this asserts that every number which reached stdout also reached `findings.tsv`. That is
    * strictly stronger — a self-recording check could only ever vouch for itself once called, and
    * `LibgdxTestMigrate` never called `PortabilityCheck` at all. */
  val RequiredChecks: Set[String] = Set(
    Signature, Omissions, PortabilityAll, PortabilityEmitted, PortabilityInjected, Remediation,
    SubstitutionEmitted, SubstitutionDangling, Policy, Manifest, PortMapCheck,
    // all three trivia lanes: a run that reported `lost` alone could hold the bar at zero by
    // recovering everything, and nothing would say so.
    TriviaDropped, TriviaRecovered, TriviaDeliberate,
    // required of EVERY port, a base with no `base = "…"` included: a run that asked no contract
    // question and one whose recording was skipped are indistinguishable without the row.
    BaseSurface,
    // required of EVERY port, including one that runs no retyping phase: with the phase absent the
    // check still reports the port's kept JDK surface and K9's ForEach demand, and a port that
    // reported nothing there would be indistinguishable from one whose check never ran.
    JdkSurface,
    // …and all THREE idiom lanes, for the trivia family's reason one artifact over:
    // `idiom(refused) = 0` is a bar a run could hold by converting NOTHING, and
    // `idiom(converted) = N` says nothing about the population N was drawn from. So the positive,
    // the refusal population and the unrewritten-usage residue are reported apart. Required of
    // EVERY port including one with no idiom phase, for `JdkSurface`'s own reason: three rows of
    // zero and a check that never ran are one row otherwise.
    IdiomConverted, IdiomRefused, IdiomResidue,
    // all four catalog lanes, and all four for the trivia family's reason: `unreached = 0` is a bar
    // a run could hold by declaring every row `Unmechanised`, and reporting the bar without the
    // residues says nothing about how it was met. `unmechanised` is derived from the REGISTRY
    // rather than from the run, so it reads the same on every port — deliberately, because it is a
    // fact about the engine and a reader comparing two ports must be able to see it did not move.
    CatalogCheck.Consulted, CatalogCheck.Unreached, CatalogCheck.Unmechanised,
    CatalogCheck.Undischarged,
    // …and the registry's own lane, which is not about coverage: `catalog(uncited)` is the count of
    // rows with no Scala-side normative citation. Required for the reason it exists — the number
    // was a `println` nothing diffed — and never asserted on anywhere.
    CatalogCheck.Uncited,
    // …and the build-graph half of the portability enumeration, BOTH lanes. Required for the same
    // reason `portability(injected)` is: each records on every run, `0 of 0` included, and a port
    // whose artifact list nobody has written is indistinguishable from one whose check never ran.
    // The PAIR is required for `portability(all|emitted)`'s reason on top of that — a dependent's
    // honest 0 and a walk that found nothing are one row until the enumeration is beside it.
    DependencyCheck.All, DependencyCheck.Name,
    // …and the standing question every RETYPING phase owes (`Rewrite`, ENGINE-LIMITS K5.6).
    // Required of EVERY port, including one whose pipeline retypes nothing: the check reports the
    // pipeline's own phases, so a run with no retyping phase and a run whose check never ran are
    // indistinguishable without the row — the same argument `JdkSurface` and `BaseSurface` carry.
    RewriteCallSitesCheck.Name,
    // recorded only when CollectionsTransform is in the pipeline; RequiredChecks asserts against
    // what RECORDED, and a port without the phase records neither, so requiring them here would
    // fail every phase-less port. They are made unskippable by the wiring living beside the
    // omission block rather than by this set — see the guard where they are recorded.
  )

  /** One translation, plus everything derived from it that must not be recomputed inconsistently.
    *
    * `sourceOf` is memoised through the optional [[ActionCache]]: the emitted text of a unit is a
    * pure function of the unit, its dependencies' signatures and the engine, so it is exactly the
    * shape an action cache wants (`TirCacheKey`). The cache is ADVISORY — deleting the directory
    * must reproduce byte-identical output, which is what `Determinism` asserts on every run. */
  final class Translated(
      val program: Program,
      val plan: RuntimePlan,
      val emitter: TirEmitter,
      val emitOrder: List[Tree.ClassDef],
      /** units the run RESOLVED against and does not emit — another module's, by construction. */
      val foreign: List[Tree.ClassDef],
      val cache: Option[ActionCache],
      /** what the PHASES decided while producing `program`. The run's non-phase deciders record
        * into the same log before it is written (`decisions.tsv`). */
      val decisions: DecisionLog = new DecisionLog,
      /** what every declared POLICY KEY resolved to, taken before the pipeline ran (§8.1). A value
        * this translation owns, for the same reason `decisions` is. */
      val binder: PolicyBinder = new PolicyBinder(
        new Program(Nil, SymbolTable(Nil), Xref.build(Nil), MemberIndex.empty), MemberIndex.empty),
      /** §8.3's view, shared by the funnel and the emitter so a run has ONE list of unanswered
        * contract questions rather than one per consumer. A value this translation owns, for the
        * same reason `decisions` is: `Determinism.Full` translates twice and the run keeps the
        * first (§5.1). */
      val surface: Surface = new TrivialSurface(
        new Program(Nil, SymbolTable(Nil), Xref.build(Nil), MemberIndex.empty)),
      /** the FRONTEND's own output, before any phase ran.
        *
        * Carried and not re-derived, because the one question it answers cannot be asked of the
        * final program alone: `MarkerCheck` compares the markers the frontend MINTED against the
        * markers that SURVIVED, and a marker that a phase deleted is by definition absent from the
        * only program anything else in this run holds. Re-parsing to get it back would be a second
        * translation free to disagree with the first — the same rule the decision log and the
        * source map follow (§5.1). Defaults to an empty program so a hand-built `Translated` in a
        * spec is still constructible; the check then compares an empty minting set, which reports
        * nothing and claims nothing. */
      val parsed: Program = new Program(Nil, SymbolTable(Nil), Xref.build(Nil), MemberIndex.empty),
      /** what this translation CONSULTED of the difference catalog — the three discharge surfaces'
        * one log (`DESIGN.md` §2.8). A value this translation owns, for the same reason `decisions`
        * is: `Determinism.Full` translates twice and the run keeps the first, which is only
        * coherent because neither log is shared (§5.1). Defaults to a fresh discarding log so a
        * hand-built `Translated` in a spec is still constructible; every catalog lane then reports
        * the registry's own answer and claims nothing about a run. */
      val catalog: balticporter.catalog.CatalogLog = balticporter.catalog.CatalogLog.discarding,
      /** what each PHASE MOVED — the observed half of `Rewrite`'s contract (`RewriteCallSitesCheck`).
        *
        * Observed by `Pipeline.runTraced` rather than declared by the phases, so it cannot be
        * carried anywhere else: the symbol table on both sides of a phase exists only while the
        * pipeline is running it. A value this translation owns, for the same reason `decisions` is.
        * Defaults to an empty log so a hand-built `Translated` in a spec is still constructible; the
        * check then reports nothing and claims nothing, which is the honest answer for a program no
        * pipeline produced. */
      val rewrites: RewriteLog = RewriteLog.discarding,
      /** what every `IdiomPhase` CONSIDERED — the denominator the three `idiom(*)` lanes report
        * apart (`IdiomCheck`).
        *
        * Carried rather than re-derived for the reason the log exists: a check that asked "would
        * this have converted" would be a second answer to the phase's own question, free to
        * disagree with it (§4.6, and `ENGINE-LIMITS.md` K2.5's measured shape). Defaults to an
        * empty log, so a hand-built `Translated` reports three honest zeros. */
      val idioms: IdiomLog = IdiomLog.discarding,
  ):
    private val memo = collection.mutable.Map.empty[SymId, String]
    // the DECISIONS are part of the key: they are not in the tree and they are in the emitted text
    // (porter notes). Read lazily, so the log is complete by the time the first unit is emitted.
    private lazy val keys = cache.map(_ => TirCacheKey.forUnits(program, emitOrder, decisions.all))

    /** units this run was SERVED FROM THE CACHE — the provenance `catalog.tsv` carries.
      *
      * A hit skips `emitUnit`, so it skips every `Rendering`/`Typing` consult that unit would have
      * made, and the catalog's per-row counts are then about the units this run RENDERED rather
      * than the units it wrote. That is a legitimate thing for an advisory cache to trade away and
      * an illegitimate thing to leave unsaid: a coverage number nobody can tell is partial is worse
      * than a smaller one. Decisions and notes take the other answer — see
      * `TirEmitter.recordedForCache`: they are not traded away, they are never cached. */
    private var served = 0
    def cacheHits: Int = served

    def sourceOf(u: Tree.ClassDef): String =
      memo.getOrElseUpdate(
        u.symbol, {
          val key = keys.flatMap(_.get(u.symbol))
          (cache, key) match
            case (Some(c), Some(k)) =>
              c.get(k) match
                case Some(hit) => served += 1; hit
                case scala.None =>
                  val out = emitter.emitUnit(u)
                  // …and it is STORED only if rendering it recorded nothing the text cannot carry.
                  // A unit whose rendering produced a `Decision` or a porter-note record would, on
                  // a later hit, come back as text with the note still in it and neither the
                  // decision nor the record behind it — `decisions.tsv` short a row, and
                  // `NoteCoverageCheck` blind to the pair because both of its inputs derive from
                  // the rendering that was skipped. Refused at the STORE rather than at the hit, so
                  // no such unit is ever in the cache to be hit.
                  if !emitter.recordedForCache(nameOf(u)) then c.put(k, out)
                  out
            case _ => emitter.emitUnit(u)
        },
      )

    private def nameOf(u: Tree.ClassDef): String =
      program.symbolOf(u.symbol).map(_.fullName).getOrElse("")

/** What a run FOUND, classified per CLAUDE.md §1 so an agent in another repository can act on it
  * without this session's context (§4.45).
  *
  * The classification is per KIND rather than per finding because it is a property of the CHECK:
  * an omission is always an engine gap, an unmatched policy key is always a manifest mistake, and
  * no amount of per-site detail changes which repository the fix belongs in. What the individual
  * finding adds is WHERE — and each check already renders that.
  */
final case class PortReport(
    label: String,
    signature: List[RewriteTrace.Mismatch],
    omissions: List[OmissionCheck.Finding],
    portability: List[PortabilityCheck.Violation],
    injectedPortability: List[PortabilityCheck.InjectedViolation],
    substitution: List[SubstitutionCheck.Finding],
    policy: PolicyReport,
    rename: PackageRenameTransform.Report,
    manifest: List[ManifestAgreement.Finding] = Nil,
):

  /** Findings that must STOP the run. A leaked dropped type means the emitted tree contains a
    * mechanical translation the manifest said not to produce; a dangling substitution means the
    * port depends on a type it claims not to have; a fatal manifest disagreement means this module
    * and the module it compiles against do not describe the same shared surface. None is a number
    * to watch — all are incoherent output. Everything else is a measurement. */
  def fatal: List[String] = substitution.map(_.render) ++ manifest.filter(_.kind.fatal).map(_.render)

  def render: String =
    val rows = List(
      s"signature mismatches: ${signature.size}"     -> PortReport.Kind.Signature,
      s"omissions: ${omissions.size}"                -> PortReport.Kind.Omission,
      s"portability: ${portability.size}"            -> PortReport.Kind.Portability,
      s"injected portability: ${injectedPortability.size}" -> PortReport.Kind.InjectedPortability,
      s"substitution: ${substitution.size}"          -> PortReport.Kind.Substitution,
      s"policy: ${policy.findings.size}"             -> PortReport.Kind.Policy,
      s"manifest agreement: ${manifest.size}"        -> PortReport.Kind.Manifest,
    )
    rows.map((line, k) => if line.endsWith(": 0") then s"  $line" else s"  $line\n  ${k.classification}").mkString("\n")

object PortReport:

  /** Which of CLAUDE.md §1's three kinds a finding of this check is, in one line an agent can act
    * on. A finding whose reader cannot tell (a) from (b) from (c) costs a full investigation —
    * that is the whole reason these strings exist. */
  enum Kind(val classification: String):
    case Signature extends Kind(
      "  §1(a) ENGINE: a call site disagrees with its declaration's CURRENT signature — a rewrite " +
        "changed one and not the other. Fix the phase that moved the signature; no manifest change helps.")
    case Omission extends Kind(
      "  §1(a) ENGINE: the TIR carries these constructs and emission loses them. A green compile " +
        "says nothing about them (CLAUDE.md §3). Fix in the engine, or record the limit in ENGINE-LIMITS.md.")
    case Portability extends Kind(
      "  §1(b)/(c) PER-LIBRARY: the JDK APIs listed are absent from Scala.js/Native. Either drop " +
        "the type and inject a replacement (`Substitutions`), re-point it (`StaticForwarderTransform`/" +
        "`ClassTableTransform`), or accept it if this port targets the JVM only.")
    case InjectedPortability extends Kind(
      "  §1(b) PER-LIBRARY: a hand-written replacement reintroduced the very API its substitution " +
        "removed. Fix the injected source in this port's overrides directory.")
    case Substitution extends Kind(
      "  §1(a)/(b): a dropped type was EMITTED (engine — the skip did not fire) or is dropped, " +
        "unreplaced and still referenced (manifest — inject a replacement or rewrite its uses).")
    case Policy extends Kind(
      "  §1(b) PER-LIBRARY: a declared key matched nothing, so the rule silently did not run. Fix " +
        "the key in this library's manifest; the engine needs no change.")
    case Trivia extends Kind(
      "  §1(a) ENGINE: a comment in the upstream Java reached no harvest point in the frontend, or " +
        "an emission path renders its node without the `leading` it carries. Nothing else reports " +
        "it — the output compiles perfectly with the comment gone, and a LICENCE notice among " +
        "these is a §4.57 obligation, not a formatting nicety. Fix in frontend-spoon or the engine emitter.")
    case Manifest extends Kind(
      "  §1(b) PER-LIBRARY: this module's policy for the SHARED surface differs from the module " +
        "that emits it — the two ports each compile alone and cannot compile together. Configure " +
        "this port's `PortManifest` to match its base, or inherit it with `base.extendedBy(...)`. " +
        "Every finding below carries its own, more specific classification.")

/** What a run PRODUCED. Returned rather than printed so a porting program can assert on it — the
  * corpus's own regression tests do exactly that. */
final case class PortResult(
    program: Program,
    outDir: Path,
    written: Int,
    dropped: Int,
    injected: Int,
    runtime: RuntimePlan,
    report: PortReport,
)
