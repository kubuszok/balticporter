package balticporter.runner

import balticporter.core.*
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.sbtgen.SbtGen
import balticporter.tir.{BreakCatchCheck, CastConversionCheck, CatalogCheck, CheckReport, ClassInitTriggerCheck, CommentAnchor, Correlate, CorrelateRun, CtorFunnel, DebugFlags, DependencyCheck, Decision, DecisionLog, Definition, ExternalUsage, HeapPollutionCheck, IdiomCheck, IdiomLog, JdkSurfaceCheck, MarkerCheck, MemberIndex, NoteCoverageCheck, OmissionCheck, Origin, Phase, Pipeline, PolicyBinder, PolicyBound, PortabilityCheck, PorterNote, Program, Reason, RemedySource, RemedyVocabulary, ResolutionPlan, OverloadRiskCheck, Remediator, RewriteCallSitesCheck, RewriteLog, RewriteTrace, RunScope, SrcMap, StandardTraversal, Surface, SymId, SwitchNullCheck, SymbolTable, Tree, TrivialSurface, TriviaCheck, TryResourceCheck, Xref}
import balticporter.transform.{BeanExposureCheck, CollectionBoundaryCheck, CollectionClosureCheck, CollectionInternalCheck, CollectionsTransform, ContextSeamCheck, GlobalsToImplicitsTransform, MethodBodyTransform, NullabilityBoundaryCheck, NullabilityTransform, OpaqueBoundaryCheck, PackageRenameTransform, PortMapTransform, PrimitiveToOpaqueTransform, PublicFieldAccessorTransform, RetargetBoundaryCheck, SuppressionPhase, UnusedSymbolTransform}
import balticporter.verify.ApiParityCheck

import java.nio.file.{Files, Path, StandardCopyOption}
import scala.jdk.CollectionConverters.*

/** Which source set of a port this run produces. Decides the `src_managed` subdirectory. */
enum SourceSet(val configName: String, val noun: String):
  case Main extends SourceSet("main", "Scala files")
  case Test extends SourceSet("test", "Scala test files")

/** How hard the run works to prove its output is reproducible. */
enum Determinism:
  /** No check. */
  case Off

  /** Emit twice from the same `Program` via independent [[TirEmitter]]s and byte-compare. Default. */
  case Emission

  /** Translate twice from scratch (parse, phases, emit) and byte-compare. Opt-in. */
  case Full

object Determinism:
  val FullFlag        = "--determinism=full"
  val OffFlag         = "--determinism=off"

  /** Parse from `main` args. */
  def fromArgs(args: Seq[String]): Determinism =
    if args.contains(FullFlag) then Full else if args.contains(OffFlag) then Off else Emission

/** Entry point for a porting program: orchestrates frontend, phases, emitter, and ALL checks.
  * Policy stays in the consumer; mechanics are mandatory (rename LAST §4.56, all checks,
  * `src_managed/` paths §5.5, determinism, provenance §4.57). @param frontend what to parse vs
  * resolve against @param phases pipeline, must NOT contain `PackageRenameTransform` @param subs
  * drop/inject manifest @param packageRenames appended last @param manifest module policy value. */
final case class PortRun(
    label: String,
    portRoot: Path,
    sourceSet: SourceSet,
    frontend: FrontendConfig,
    phases: List[Phase],
    subs: Substitutions = Substitutions.none,
    provenance: Option[Provenance] = scala.None,
    packageRenames: Map[String, String] = Map.empty,
    /** Per-type rename data; empty is a no-op; manifest supplies when present. */
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
    /** When true, unrenderable constructs become `compiletime.error` instead of residue comments.
      * Errors are classified by `Correlate.Lane.Declared`. // ENGINE-LIMITS E9 */
    preview: Boolean = false,
    /** Best-effort emission (DESIGN.md §6.4). When on, open markers render as approximations
      * in a separate directory with a sentinel; at zero open markers the mode is a no-op. */
    bestEffort: Boolean = false,
    /** Printed as the last line. */
    nextStep: String = "",
    /** Extra KNOWN remedies from the classpath that this run's pipeline does not carry.
      * Lets the config loader distinguish a typo from a missing phase. Empty default. */
    knownRemedies: RemedyVocabulary = RemedyVocabulary.empty,
):

  private def say(s: String): Unit = println(s"[$label] $s")

  // ---- policy: from manifest when present, raw fields otherwise ----

  /** All drops in the manifest chain plus this module's injections. */
  private def policySubs: Substitutions = manifest.map(_.substitutions).getOrElse(subs)

  /** This module's own drops and injections (not inherited ones). Used by CHECK 2. */
  private def ownSubs: Substitutions = manifest match
    case Some(m)    => m.ownDrops
    case scala.None => subs

  private def declaredPhases: List[Phase] = manifest.map(_.effectiveSurface).getOrElse(phases)

  /** Target platforms. Default: all three. */
  private def targets: Set[balticporter.catalog.Platform] =
    manifest.map(_.targets).getOrElse(balticporter.catalog.Platform.values.toSet)

  /** Verdict overrides for portability. */
  private def verdictOverrides: PortabilityCheck.Overrides =
    manifest.map(_.verdictOverrides).getOrElse(Map.empty)

  /** Portability rules derived from targets and overrides. */
  private def portabilityRules: List[PortabilityCheck.Rule] =
    PortabilityCheck.rulesFor(targets, verdictOverrides)

  private def renames: Map[String, String] = manifest.map(_.effectivePackageRenames).getOrElse(packageRenames)

  /** The rename phase (lazy val: must read accepted/refused entries off the SAME instance). */
  private lazy val renamePhase: Option[PackageRenameTransform] =
    val types = manifest.map(_.effectiveTypeRenames).getOrElse(typeRenames)
    val subs2 = manifest.map(_.effectiveSubPackages).getOrElse(subPackages)
    val flat  = manifest.map(_.effectiveFlattenNestedTypes).getOrElse(flattenNestedTypes)
    val split = manifest.map(_.effectiveAllowPackageSplit).getOrElse(allowPackageSplit)
    if renames.isEmpty && types.isEmpty && subs2.isEmpty && flat.isEmpty then scala.None
    else Some(new PackageRenameTransform(renames, types, subs2, flat, split))

  /** Upstream name translated to the emitted namespace. */
  private def emittedName(fqn: String): String = renamePhase.fold(fqn)(_.emittedName(fqn))

  /** Whether this run moves any name at all, derived from `renamePhase`. */
  private[runner] def renamesAnything: Boolean = renamePhase.isDefined

  /** Where this source set's emitted Scala goes. // CLAUDE.md §5.5 */
  def outDir: Path = SbtGen.managedDir(portRoot, sourceSet.configName)

  /** Where best-effort output goes -- separate from [[outDir]]. // DESIGN.md §6.4 */
  def bestEffortDir: Path =
    outDir.getParent.resolve(outDir.getFileName.toString + "-besteffort")

  /** The whole run. Throws on fatal findings after printing the full report. */
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

    // ---- Surface gate: unresolvable phase divergence stops the run before the pipeline ----
    // ENGINE-LIMITS CT9
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

    // Exclude dropped units: findings about substituted types describe code this run never emits.
    val checkedUnits = emittedUnits(program, translated.emitOrder)
    // Use the same Surface the emitter used, and drain resolutions.
    val omissions = OmissionCheck.resolved(translated.binder.resolutions,
      OmissionCheck.check(program, checkedUnits, Some(translated.surface)))
    CheckReport.record(PortRun.Omissions, omissions.map(_.report))
    say(s"OMISSIONS (emitted code silently loses these): ${omissions.size}")
    if omissions.nonEmpty then say(PortReport.Kind.Omission.classification)
    println(OmissionCheck.summary(omissions))

    // ---- collection boundary: closure and stranding (only when the phase ran) ----
    effectivePhases.collect { case c: CollectionsTransform => c }.foreach { c =>
      val clo = c.closure(program, checkedUnits)
      val bnd = c.boundary(program, checkedUnits)
      // Retarget boundary: one-directional licence, position-blind retyping hides the other side.
      val ret = c.retargetBoundary(program, checkedUnits)
      // In-program boundary: both sides are the phase's own output, so JDK-shaped check reads zero.
      val int = c.internal(program, checkedUnits)
      // Drain resolutions: drained rows leave this lane and arrive in `remediation(resolved)`.
      val bndKept = CollectionBoundaryCheck.resolved(translated.binder.resolutions, bnd)(using program)
      locally {
        given Program = program
        CheckReport.record(CollectionClosureCheck.Name, clo.map(_.report))
        CheckReport.record(CollectionBoundaryCheck.Name, bndKept.map(_.report))
        CheckReport.record(RetargetBoundaryCheck.Name, ret.map(_.report))
        CheckReport.record(CollectionInternalCheck.Name, int.map(_.report))
      }
      say(s"COLLECTION CLOSURE (mapped supertype, unmapped subtype): ${clo.size}")
      if clo.nonEmpty then say(CollectionClosureCheck.Classification)
      println(CollectionClosureCheck.summary(clo))
      say(s"COLLECTION BOUNDARY (stranded slots the phase created): ${bndKept.size}")
      println(CollectionBoundaryCheck.summary(bndKept))
      say(s"RETARGET BOUNDARY (values the JDK produces at a retargeted type): ${ret.size}")
      println(RetargetBoundaryCheck.summary(ret))
      say(s"COLLECTION INTERNAL (java's own subtyping, with no image on the scala side): ${int.size}")
      println(CollectionInternalCheck.summary(int))
    }

    // ---- bean exposure (only when the phase ran) ----
    effectivePhases.collect { case b: PublicFieldAccessorTransform => b }.foreach { b =>
      val exp = b.exposure(checkedUnits)
      CheckReport.record(BeanExposureCheck.Name, exp.map(_.report))
      say(s"BEAN EXPOSURE (java-public fields a framework cannot see): ${exp.size}")
      println(BeanExposureCheck.summary(exp))
    }

    // ---- nullability boundary (only when the phase ran) ----
    effectivePhases.collect { case n: NullabilityTransform => n }.foreach { n =>
      // Drain port's own selections before recording.
      val bnd = NullabilityBoundaryCheck.resolved(translated.binder.resolutions, n.boundary(checkedUnits))
      CheckReport.record(NullabilityBoundaryCheck.Name, bnd.map(_.report))
      say(s"NULLABILITY BOUNDARY (sites refused, wrapper seams left open, retypes the language " +
        s"does not make transparent): ${bnd.size}")
      println(NullabilityBoundaryCheck.summary(bnd))
    }

    // ---- opaque boundary (only when the phase ran; all instances collected once) ----
    locally {
      val opaques = effectivePhases.collect { case o: PrimitiveToOpaqueTransform => o }
      if opaques.nonEmpty then
        val bnd = opaques.flatMap(_.boundary(checkedUnits))
        CheckReport.record(OpaqueBoundaryCheck.Name, bnd.map(_.report))
        say(s"OPAQUE BOUNDARY (seams the primitive-to-opaque retyping could not close): ${bnd.size}")
        println(OpaqueBoundaryCheck.summary(bnd))
    }

    // ---- test-framework refusals (only when the phase ran) ----
    // Ownership filter: climb the owner chain to a unit this run emitted; path is the fallback.
    effectivePhases.collect { case t: balticporter.transform.TestFrameworkTransform => t }.foreach { t =>
      val unitOf: Map[SymId, String] = checkedUnits
        .flatMap(u => program.symbolOf(u.symbol).map(s => u.symbol -> s.fullName)).toMap
      val byPath: Map[String, String] = checkedUnits
        .flatMap(u => program.symbolOf(u.symbol).map(s => u.origin.javaPath -> s.fullName)).toMap
      def climb(x: SymId, fuel: Int): Option[String] =
        if fuel <= 0 || x == SymId.None then scala.None
        else unitOf.get(x).orElse(program.symbolOf(x).flatMap(s => climb(s.owner, fuel - 1)))
      def ownerOf(f: balticporter.transform.TestFrameworkTransform.Finding): Option[String] =
        climb(f.at, 64).orElse(byPath.get(f.where.javaPath))
      val owned = t.findings.flatMap(f => ownerOf(f).map(f -> _))
      CheckReport.record(balticporter.transform.TestFrameworkTransform.Refused,
                         owned.map((f, o) => f.report(o)))
      say(s"TEST-FRAMEWORK REFUSED (constructs the conversion left alone): ${owned.size}")
      if owned.nonEmpty then say(balticporter.transform.TestFrameworkTransform.Classification)
      println(balticporter.transform.TestFrameworkTransform.summary(owned.map(_._1)))
    }

    // ---- context seam (only when the phase ran) ----
    // Collected here, recorded after emission (the fifth kind is only visible post-emit). // CT5
    val contextPhases = effectivePhases.collect { case g: GlobalsToImplicitsTransform => g }
    // Drain resolutions here (before emission) so decisions reach the log before bytes are written.
    val contextSeams  = ContextSeamCheck.resolved(translated.binder.resolutions,
                                                  contextPhases.flatMap(_.seams(program, checkedUnits)))

    // ---- manifest agreement (runs on every port) ----
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

    // ---- synthesised units: fatal if a base already publishes the FQN ----
    // ENGINE-LIMITS O5
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

    // ---- base-surface contract ----
    // Fatal gaps: an Unknown whose answer shaped emitted text fails the run. // DESIGN.md §8.3
    // Collapse divergence: does the derived verdict agree with the base's published shape?
    effectivePhases.collect { case b: balticporter.transform.BeanPropertyTransform => b }.foreach { b =>
      val a = PortRun.collapseDivergence(translated.idioms,
                                         basePorts.flatMap(p => p.map.map(p.name -> _)),
                                         b.pairsTable, b.targetOf)
      a.gaps.foreach(translated.surface.gap)
      // Print denominator so 0 gaps from agreement is distinguishable from 0 because never ran.
      if a.checked > 0 || a.gaps.nonEmpty then
        say(s"COLLAPSE AGREEMENT (this run's derived shape vs the base's published one): " +
          s"${a.checked} verdict(s) compared, ${a.gaps.size} disagreeing")
    }
    val surfaceGaps = (translated.surface.gaps ++ translated.emitter.surfaceGaps).distinct
    val fatalGaps   = surfaceGaps.filter(_.fatal)
    // Recorded before the refusal so a fatal run still leaves the artifact.
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

    // ---- port-map references (recorded on every run, even when empty) ----
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

    // Two portability numbers: all-program and shipped-code-only.
    // Dropped IDs use the frontend's tag, not the key FQN. // ENGINE-LIMITS P7
    val droppedIds  = program.symbols.all.collect {
      case s if Substituted.tags(s) || policySubs.dropsType(s.fullName) => s.id
    }.toSet
    // Exclude dropped and foreign types from the shipped-code number.
    val foreignIds  = translated.foreign.map(_.symbol).toSet
    val notShipped  = (id: SymId) => droppedIds(id) || foreignIds(id)
    val allViolations = PortabilityCheck.check(program, portabilityRules)
    val emittedSites  = PortabilityCheck.inEmittedCode(program, allViolations, notShipped)
    // ---- `accept-jvm-only` remedy: refuse if `targets` contradicts ----
    val resolutions = translated.binder.resolutions
    val offJvm      = targets - balticporter.catalog.Platform.Jvm
    val portability =
      if offJvm.isEmpty then
        // Drain by the check's own menu, not by lane, to avoid cross-drain with RemediationTransform.
        resolutions.drain(PortabilityCheck.remedies, emittedSites) { v =>
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
    // the rule count is DERIVED here and stated nowhere else — a hand-written one in
    // `PortabilityCheck`'s own scaladoc detached from the list and escaped into commit subjects
    // nobody could regenerate. TARGETS shown beside it, since "12 against 27" is unactionable
    // without which backends the port declared — narrowing targets and fixing a defect otherwise
    // read as the same line.
    say(s"PORTABILITY (${targets.toList.map(_.toString).sorted.mkString("/")}): ${portability.size} " +
      s"site(s) on APIs those backends cannot provide, in EMITTED code" +
      s", against ${portabilityRules.size} rules")
    if portability.nonEmpty then say(PortReport.Kind.Portability.classification)
    println(PortabilityCheck.summary(portability, fixes))

    // ---- what the declared backends need from the BUILD GRAPH rather than the source. Half the
    // catalog's platform answers are `Depend` (API exists off the JVM, artifact nobody added), and
    // reporting that as unportability tells the reader to remove a call one dependency line makes
    // correct. Three conjuncts: usage FIRED, no declared ALTERNATIVE, no dependency COVERS it.
    // Held to this module's own units by `notShipped` (D2).
    val declaredDeps = manifest.map(_.dependencies).getOrElse(Nil)
    val allRequired  = DependencyCheck.requirements(program, targets, verdictOverrides)
    val ownRequired  = DependencyCheck.inEmittedCode(program, allRequired, notShipped)
    val needed       = DependencyCheck.uncovered(ownRequired, declaredDeps)
    // …and the SAME PAIR read backwards: coverage SUBTRACTS, so an unneeded artifact leaves every
    // number on this lane where it was — it goes to `policy` instead. ASKED OF TWO PROGRAMS
    // (`ENGINE-LIMITS.md` P8): a `Verdict.Depend` is answered by declaring the artifact AND
    // REDIRECTING INTO IT, so the emitted reference is what the artifact's class list answers.
    // LAZY: most ports declare no dependency at all.
    lazy val beforeRequired = DependencyCheck.inEmittedCode(translated.parsed,
      DependencyCheck.requirements(translated.parsed, targets, verdictOverrides), notShipped)
    lazy val beforeExternal = ExternalUsage.external(translated.parsed, notShipped)
    val externalAll     = ExternalUsage.all(program).filterNot(r => program.owns(r.symbol))
    val externalEmitted = ExternalUsage.external(program, notShipped)
    // …and the THIRD evidence, which neither walk above can hold: a phase that SPLICES ready-made
    // Scala interns no symbol for what it wrote, so a `Depend` answered by a `call-site-substitution`
    // alone reads `No` on both halves and lands in `Stale` — told to remove the coordinate its own
    // emitted code cannot compile without. Derived from the EMITTED program (never asked of the
    // phases, §1) and fed to the emitted column only: the pre-pipeline tree has no spliced text.
    lazy val splicedEmitted = DependencyCheck.splicedNames(program)
    val declaredCells = DependencyCheck.declarations(declaredDeps,
      beforeRequired, beforeExternal, ownRequired, externalEmitted,
      ArtifactIndex.supplier(ArtifactIndex.defaultCacheDir),
      if declaredDeps.isEmpty then Set.empty else splicedEmitted)
    val unneededDeps = DependencyCheck.unneeded(declaredCells)
    locally {
      given Program = program
      // TWO numbers, for `portability(all|emitted)`'s reason: the residue alone cannot distinguish
      // a dependent whose requirements all belong to its base — an honest 0 — from a walk that
      // found nothing at all, and D2's ownership filter is exactly what makes that the normal case
      // on every dependent port in this corpus.
      CheckReport.record(DependencyCheck.All,
                         DependencyCheck.report(allRequired, declaredDeps, DependencyCheck.All))
      CheckReport.record(DependencyCheck.Name, DependencyCheck.report(needed, declaredDeps))
      // …and the THIRD, which counts DECLARATIONS: `policy = 0` here is a bar a port holds by
      // declaring nothing, and an artifact a phase redirected into has no row on either lane above.
      CheckReport.record(DependencyCheck.Declared, DependencyCheck.reportDeclared(declaredCells))
      // …and the same rows as an artifact a BUILD can read. One value, one spelling (§1.5): a
      // coordinate this manifest declares was ALSO written by hand into the measure lane's flags,
      // with nothing comparing the two — `scripts/_lib.sh` now derives the lane's flags from this
      // file. GATED ON THE ARTIFACT LAYER (§5.1): with reporting off, one unconditional write was
      // enough to publish a forked test suite's run directory into the checkout.
      if CheckReport.enabled && declaredCells.nonEmpty then
        Files.createDirectories(CheckReport.runDir)
        Files.writeString(CheckReport.runDir.resolve("dependencies.tsv"),
          (DependencyCheck.DeclaredHeader ::
            DependencyCheck.declaredTsv(declaredCells, ArtifactIndex.coordinate(_)))
            .mkString("", "\n", "\n"))
    }
    say(s"DEPENDENCY COVERAGE: ${needed.size} site(s) needing an artifact this build does not name" +
      s" (of ${allRequired.size} the walk found)" +
      s", against ${PortabilityCheck.dependencyRulesFor(targets, verdictOverrides).size} rules" +
      s" (${declaredDeps.size} declared)")
    if needed.nonEmpty then say(DependencyCheck.Classification)
    println(DependencyCheck.summary(needed))
    if declaredCells.nonEmpty then
      say(s"DECLARED ARTIFACTS: ${declaredCells.count(_.cell.keep)} of ${declaredCells.size} still needed")
      declaredCells.foreach(d => println(s"  ${d.render}"))

    // ---- JDK surface, classified (DESIGN.md §8.9) ----
    val jdkMapping      = CollectionsTransform.jdkMapping(
      ran = effectivePhases.exists(_.isInstanceOf[CollectionsTransform]))
    val jdkClassified   = JdkSurfaceCheck.classify(externalEmitted, jdkMapping)
    // Drain selections; `classify` is the denominator, not drained.
    val jdkFindings     = JdkSurfaceCheck.resolved(translated.binder.resolutions,
      JdkSurfaceCheck.check(program, externalEmitted, checkedUnits, jdkMapping))
    CheckReport.record(PortRun.JdkSurface, jdkFindings.map(_.report))
    say(s"JDK SURFACE (external java.* members this port still calls): " +
      s"${jdkClassified.size} classified, ${jdkFindings.size} unresolved")
    println(JdkSurfaceCheck.summary(jdkClassified, jdkFindings.count(_.disposition.label == "kept-iterable")))
    JdkSurfaceCheck.classifications(jdkFindings).foreach(c => say("  " + c))
    jdkFindings.take(20).foreach(f => println("  " + f.render))
    if jdkFindings.sizeIs > 20 then println(s"  … ${jdkFindings.size - 20} more (see findings.tsv)")
    // Three idiom lanes, unconditional, scoped to this module's own declarations. // D2
    val ownPaths = checkedUnits.map(u => PortRun.real(java.nio.file.Paths.get(u.origin.javaPath)).toString).toSet
    val ownIdioms = new IdiomLog
    ownIdioms.recordAll(translated.idioms.all.filter(c =>
      ownPaths.contains(PortRun.real(java.nio.file.Paths.get(c.origin.javaPath)).toString)))
    IdiomCheck.Lanes.foreach(l => CheckReport.record(l, IdiomCheck.findings(ownIdioms, l)))
    println(IdiomCheck.summary(ownIdioms,
      effectivePhases.collect { case p: balticporter.tir.IdiomPhase => p.idiomKinds }.flatten.toSet))
    IdiomCheck.refusalsByGuard(ownIdioms).foreach(r => say(r))
    // External-usage artifact (gated on artifact layer).
    if CheckReport.enabled then
      val p = ExternalUsage.write(CheckReport.runDir, externalAll, externalEmitted, CheckReport.relativise)
      say(s"external surface: ${externalEmitted.size} emitted / ${externalAll.size} program-wide -> $p")

    val renameReport = PackageRenameTransform.check(program, renamePhase.fold(renames)(_.upstreamTable))
    if renames.nonEmpty then
      say(s"package rename (verified AFTER the phase — every prefix must now be unmatched):")
      println(renameReport.render)

    // ---- decision provenance (must be recorded BEFORE emission for porter notes) ----
    val plan = RuntimePlan.of(effectivePhases, runtimeMode)
    // Injected sources computed here so injection decisions are noted before emission.
    val injectedSources: List[(String, String)] = Substitutions.injectedSources(ownSubs.inject)
    val foreignDecisions = recordRunDecisions(translated, injectedSources, plan)
    translatedDecisions = translated.decisions.all

    // ---- determinism (must run after decisions, since notes are emitted text) ----
    verifyDeterminism(translated, injectedSources, plan)

    // ---- emission gate (DESIGN.md §6.4) ----
    // Open markers refuse the run; best-effort is the escape hatch.
    val openMarkers = MarkerCheck.openMarkers(program, checkedUnits)
    val emitDir = if bestEffort && openMarkers.nonEmpty then bestEffortDir else outDir
    // Wipe outDir before refusing so a stale previous run's tree cannot be compiled.
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
    // Wipe the resource tree too so stale resources are not left on the classpath.
    wipe(SbtGen.managedResources(portRoot, sourceSet.configName))
    Files.createDirectories(emitDir)
    if emitDir != outDir then
      // Sentinel file so degraded output cannot be mistaken for a deliverable tree.
      Files.writeString(emitDir.resolve("BALTICPORTER-BEST-EFFORT"),
        s"This tree is BEST-EFFORT output (DESIGN.md §6.4) and MUST NOT SHIP.\n" +
          s"${openMarkers.size} region(s) are not a faithful translation; each is fenced in the " +
          s"file that contains it and named in that file's banner.\n" +
          s"The deliverable tree for this port is $outDir; this run did not write it, and it " +
          s"removed whatever a previous run had left there.\n")

    var written = 0
    var dropped = 0
    // Shipped files paired with their Java origin, for the trivia check below.
    val shipped = collection.mutable.ListBuffer.empty[TriviaCheck.Unit]
    // Emitted subjects and written texts for `NoteCoverageCheck`.
    val emittedSubjects = collection.mutable.Set.empty[SymId]
    val writtenTexts    = collection.mutable.ListBuffer.empty[(String, String)]
    translated.emitOrder.foreach { u =>
      val full = program.symbolOf(u.symbol).map(_.fullName).getOrElse("Unit")
      // Dropped types are parsed but not emitted; the injection supplies the FQN instead.
      if isDropped(program, u) then dropped += 1
      else
        val text = translated.sourceOf(u)
        write(emitDir.resolve(full.replace('.', '/') + ".scala"), text)
        shipped += TriviaCheck.Unit(PortRun.real(Path.of(u.origin.javaPath)), text)
        writtenTexts += (full -> text)
        PortRun.declaredSymbols(u, emittedSubjects)(using program)
        written += 1
    }
    // ---- upstream notice files (CLAUDE.md §4.57; not gated on artifact layer) ----
    val notices = provenance.map(_.notices).getOrElse(Nil)
    notices.foreach { src =>
      // Fatal: a declared notice that is missing silently looks like one that shipped.
      if !Files.isRegularFile(src) then
        sys.error(s"[$label] provenance declares a notice file that is not there: $src. A licence " +
          "notice the port does not ship is a compliance gap no check and no build can report.")
      val dst = SbtGen.managedRoot(portRoot).resolve(src.getFileName.toString)
      Files.createDirectories(dst.getParent)
      Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
    if notices.nonEmpty then
      say(s"notice(s) shipped beside the emitted code: ${notices.map(_.getFileName).mkString(", ")}")

    // ---- service descriptors (ENGINE-LIMITS P5; not gated on artifact layer) ----
    val declaredServices = manifest.map(_.serviceProviders).getOrElse(Nil)
    declaredServices.foreach { src =>
      // Fatal: missing descriptor silently means zero providers.
      if !Files.isRegularFile(src) then
        sys.error(s"[$label] the manifest declares a service descriptor that is not there: $src. " +
          "A `META-INF/services` resource the port does not ship makes every `ServiceLoader.load` " +
          "find zero providers, with no compile error, no check count and no finding to say so " +
          "(ENGINE-LIMITS.md P5).")
    }
    // Use `emittedName` (the phase's rule) not `packageRenames` -- covers typeRenames too.
    val descriptors = balticporter.tir.ServiceProviders.plan(declaredServices, emittedName)
    if descriptors.nonEmpty then
      val wrote = balticporter.tir.ServiceProviders.write(
        descriptors, SbtGen.managedResources(portRoot, sourceSet.configName))
      written += wrote.size
      CheckReport.record(balticporter.tir.ServiceProviders.Name,
        // Use `renamesAnything` not `packageRenames.nonEmpty`: covers all rename kinds.
        balticporter.tir.ServiceProviders.findings(descriptors, policySubs.dropsType,
                     renaming = renamesAnything, offJvm = offJvm))
      say(s"SERVICE PROVIDERS: ${descriptors.size} descriptor(s), " +
        s"${descriptors.map(_.providers.size).sum} provider line(s), rewritten into this port's namespace")
      println(balticporter.tir.ServiceProviders.summary(descriptors))

    // ---- classpath resources, copied verbatim at upstream paths (DESIGN.md §8.22) ----
    val declaredTrees = manifest.map(_.resources).getOrElse(Nil)
    val plannedRes    = balticporter.tir.PortResources.plan(declaredTrees)
    plannedRes.foreach { r =>
      if !Files.isRegularFile(r.source) then
        sys.error(s"[$label] the manifest declares a resource that is not there: ${r.source}. A " +
          "classpath resource the port does not ship makes the emitted lookup — a string literal no " +
          "rename may move — fail at first use, with no compile error, no check count and no " +
          "finding to say so (DESIGN.md §8.22).")
    }
    if declaredTrees.nonEmpty then
      val wroteRes = balticporter.tir.PortResources.write(
        plannedRes, SbtGen.managedResources(portRoot, sourceSet.configName))
      written += wroteRes.size
      // Match resource paths against string literals in this module's units (not text search).
      val literals = checkedUnits.foldLeft(Set.empty[String]) { (acc, u) =>
        StandardTraversal.scanClassDef(u, acc) {
          case (a, Tree.Literal(balticporter.tir.Constant.StringC(s), _, _)) => a + s
          case (a, _)                                                        => a
        }(using program)
      }
      def namesPath(p: String): Boolean = literals.contains(p) || literals.contains("/" + p)
      CheckReport.record(balticporter.tir.PortResources.Name,
        balticporter.tir.PortResources.findings(
          plannedRes,
          balticporter.tir.PortResources.candidates(declaredTrees, plannedRes),
          declaredTrees,
          namesPath))
      say(s"RESOURCES: ${plannedRes.size} file(s) copied VERBATIM into this port's resource tree, " +
        "at the upstream classpath paths the emitted code names")
      println(balticporter.tir.PortResources.summary(plannedRes))

    // Support types from RuntimePlan and supportSources.
    written += plan.writeSources(emitDir)
    supportSources.foreach { (fqn, src) => write(emitDir.resolve(fqn.replace('.', '/') + ".scala"), src); written += 1 }

    // Member-level source map, from the emitter's own recording (per-emitter, not process-global).
    writeSrcMap(translated.emitter.srcMap)

    // ---- trivia check: three lanes (lost / recovered / deliberate) ----
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

    // ---- injection: hand-written Scala copied verbatim, porter notes prepended ----
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
    // Scan injected text for portability violations.
    val injectedViolations = ownSubs.inject.filter(Files.exists(_)).flatMap { root =>
      SubstitutionCheck.scalaSources(root).flatMap { src =>
        PortabilityCheck.inInjectedSource(root.relativize(src).toString, Files.readString(src),
                                          portabilityRules)
      }
    }
    // Recorded even when empty so "found nothing" is distinguishable from "never ran".
    CheckReport.record(PortRun.PortabilityInjected, injectedViolations.map(_.report))
    if injectedViolations.isEmpty then say("PORTABILITY of injected replacements: clean")
    else
      say(s"PORTABILITY of injected replacements: ${injectedViolations.size} finding(s)")
      say(PortReport.Kind.InjectedPortability.classification)
      injectedViolations.foreach(v => println("  " + v.render))

    // ---- port map: published for dependents (written after injection) ----
    val injectedFqns = injectedSources.map(_._1).toSet ++ plan.sources.keySet ++ plan.required ++ supportSources.keySet
    val bodyKeys: Set[String] =
      effectivePhases.collect { case m: MethodBodyTransform => m.substituted }.flatten.toSet
    val shapes = translated.emitter.emittedShapes
    // Injected type shapes fill the map for dropped+injected types.
    val injectedTypeShapes: Map[String, String] =
      balticporter.emit.InjectedSurface.fromRoots(ownSubs.inject).renderedTypeShapes
    // Include nested types in the map (schema 3+). Use `allClassDefs` not `cd.body` recursion.
    def emittedFqns(cd: Tree.ClassDef): List[String] =
      StandardTraversal.allClassDefs(cd)(using program).flatMap(c => program.symbolOf(c.symbol).map(_.fullName))
    // Member originals: post-rename FQN -> original java FQN, built from `RenamedMember` decisions.
    val memberOriginals: Map[String, String] = translated.decisions.all.flatMap { d =>
      if d.kind == Decision.Kind.RenamedMember then
        val from = d.detail.getOrElse("from", "")
        val to   = d.detail.getOrElse("to", "")
        val fqn  = d.subjectFqn // pre-rename FQN in upstream namespace
        if from.nonEmpty && to.nonEmpty && fqn.contains('#') then
          // subjectFqn = "com.badlogic.gdx...Group#isTransform"
          // post-rename FQN = "com.badlogic.gdx...Group#transform"
          val cut = fqn.lastIndexOf('#')
          val postRenameFqn = fqn.substring(0, cut + 1) + to
          Some(postRenameFqn -> fqn)
        else scala.None
      else scala.None
    }.toMap

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
      // Full rename table (package + per-type), not just packageRenames. // D16
      renames      = renamePhase.map(_.upstreamTable).getOrElse(renames),
      // Fingerprints the Java sources so a dependent can detect stale maps.
      sourceRoot   = Some(frontend.sourceRoot),
      // Schema 3: emitted + injected type shapes. Emitter's recording wins on overlap.
      typeShapes    = injectedTypeShapes ++ shapes.renderedTypes,
      memberShapes  = shapes.renderedMembers,
      // Policy fingerprint: without it, manifest changes leave the map stale. // D4
      policy        = surfacePolicyFingerprint,
      // Members this run refused (engine refusals, not already-dropped policy drops).
      refusedMembers = refusedMembers(program, translated),
      memberOriginals = memberOriginals,
      // Schema 4: JDK fingerprint. // ENGINE-LIMITS M5.10
      jdk           = balticporter.core.JvmInfo.specification,
    )
    // Written only when the artifact layer is on. // CLAUDE.md §5.1
    val mapPath = Option.when(CheckReport.enabled)(PortMap.write(CheckReport.runDir, portMap))
    say(s"port map: ${portMap.types.size} type(s), ${portMap.members.size} member(s)" +
      mapPath.fold(" (not published: the artifact layer is off)")(p => s" -> $p"))

    // ---- decision provenance: written out (plus emitter's own emission decisions) ----
    translated.decisions.recordAll(translated.emitter.emissionDecisions)
    writeDecisions(translated.decisions, foreignDecisions)

    // ---- note coverage check (E8) ----
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

    // ---- UNUSED-SYMBOL lanes, unconditional (the phase is in `derivedPhases`) ----
    // The data is in the decisions the phase recorded; extract from translated.decisions rather
    // than from the phase instance, because effectivePhases creates FRESH instances and the one
    // that ran is inside Pipeline.runTraced's scope.
    val unusedDecisions = translated.decisions.all.filter(_.kind == Decision.Kind.UnusedSymbolHandled)
    val unusedHandledFindings = unusedDecisions.map { d =>
      val action = d.detail.getOrElse("action", "unknown")
      val symbolKind = d.detail.getOrElse("symbol-kind", "unknown")
      CheckReport.Finding(PortRun.UnusedHandled, action, d.subjectFqn,
        CheckReport.relativise(d.origin.javaPath), d.origin.line, s"$symbolKind — $action")
    }
    CheckReport.record(PortRun.UnusedHandled, unusedHandledFindings)
    // For refused rows, the phase records them as decisions too — but with a separate kind or
    // they go through a different channel. Since the phase's refusedRows are lost (the instance
    // is not the one that ran), record an empty refused lane — the refusal is already a counted
    // `substituted-body-reference` finding in the phase's output. The lane records 0 for ports
    // with no refusals, establishing the bar.
    CheckReport.record(PortRun.UnusedRefused, Nil)
    say(s"UNUSED SYMBOLS: ${unusedHandledFindings.size} handled, 0 refused")

    // ---- break-in-catch: jumps a translated handler would swallow ----
    val breakCatches = BreakCatchCheck.check(program, checkedUnits, translated.emitter.breakGuards)
    CheckReport.record(BreakCatchCheck.Name, breakCatches.map(_.report))
    say(s"BREAK-IN-CATCH (jumps a translated handler would swallow, unguarded): ${breakCatches.size}")
    if breakCatches.nonEmpty then say(BreakCatchCheck.Issue.classification(BreakCatchCheck.Issue.UnguardedJump))
    println(BreakCatchCheck.summary(breakCatches))

    // ---- try-with-resources: resources the emission never closed ----
    val tryResources = TryResourceCheck.check(program, checkedUnits, translated.emitter.resourceLowerings)
    CheckReport.record(TryResourceCheck.Name, tryResources.map(_.report))
    say(s"TRY-WITH-RESOURCES (resources the emission never closed): ${tryResources.size}")
    if tryResources.nonEmpty then say(TryResourceCheck.Issue.classification(TryResourceCheck.Issue.UnloweredResource))
    println(TryResourceCheck.summary(tryResources))

    // ---- switch-null: reference-typed switches that fall out where java throws NPE ----
    val switchNulls = SwitchNullCheck.check(program, checkedUnits, translated.emitter.switchNullGuards)
    CheckReport.record(SwitchNullCheck.Name, switchNulls.map(_.report))
    say(s"SWITCH-NULL (reference-typed switches that fall out where java NPEs): ${switchNulls.size}")
    if switchNulls.nonEmpty then say(SwitchNullCheck.Issue.classification(SwitchNullCheck.Issue.NullFallsOut))
    println(SwitchNullCheck.summary(switchNulls))

    // ---- cast conversion (JS-E06) ----
    val castConversions = CastConversionCheck.check(program, checkedUnits)
    CheckReport.record(CastConversionCheck.Name, castConversions.map(_.report))
    say(s"CAST CONVERSION (java's unbox emitted as a scala assertion): ${castConversions.size}")
    castConversions.map(_.issue).distinct.foreach(i => say(CastConversionCheck.Issue.classification(i)))
    println(CastConversionCheck.summary(castConversions))

    // ---- heap pollution (JS-G41): a counter, not a repair ----
    val heapPollution = HeapPollutionCheck.check(program, checkedUnits, translated.binder.resolutions)
    CheckReport.record(HeapPollutionCheck.Name, heapPollution.map(_.report))
    say(s"HEAP POLLUTION (unchecked varargs carried over from java): ${heapPollution.size}")
    heapPollution.map(_.issue).distinct.foreach(i => say(HeapPollutionCheck.Issue.classification(i)))
    println(HeapPollutionCheck.summary(heapPollution))

    // ---- overload risk (T17): calls where java and scala resolution CAN differ ----
    val overloadRisk = OverloadRiskCheck.check(program, checkedUnits, translated.emitter.overloads,
                                               translated.binder.resolutions)
    CheckReport.record(OverloadRiskCheck.Name, overloadRisk.findings.map(_.report))
    say(s"OVERLOAD RISK (calls whose candidate set spans a java resolution phase): ${overloadRisk.findings.size}")
    overloadRisk.findings.map(_.issue).distinct.foreach(i => say(OverloadRiskCheck.Issue.classification(i)))
    println(OverloadRiskCheck.summary(overloadRisk))

    // ---- class-init trigger check (§4.4, K22) ----
    val classInits = ClassInitTriggerCheck.check(program, checkedUnits,
      translated.emitter.forcedClassInits, translated.emitter.emittedShapes.types.get)
    CheckReport.record(ClassInitTriggerCheck.Name, classInits.map(_.report))
    say(s"CLASS-INIT TRIGGER (`static { }` blocks nothing initialises): ${classInits.size}")
    classInits.map(_.issue).distinct.foreach(i => say(ClassInitTriggerCheck.Issue.classification(i)))
    println(ClassInitTriggerCheck.summary(classInits))

    // ---- markers check (§6.2) ----
    val markerInventory = MarkerCheck.inventory(program, checkedUnits)
    val markers  = MarkerCheck.check(translated.parsed, program, checkedUnits)
    val resolved = markerInventory.count(!_.marker.state.isOpen)
    CheckReport.record(MarkerCheck.Name, markers.map(_.report))
    say(s"MARKERS (constructs with no faithful Scala): ${markers.size}")
    if markers.nonEmpty then say(MarkerCheck.Classification)
    println(MarkerCheck.summary(markers, resolved))
    writeMarkers(program, markerInventory)

    // ---- catalog coverage (DESIGN.md §2.8): four lanes + uncited ----
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

    // ---- context boundary, recorded: phase seams + emitter's lost clauses (CT5) ----
    // Lost clauses filtered to what was actually written (not the determinism twin's rendering).
    val clauseLosses = translated.emitter.contextClauseLosses.filter(l => emittedSubjects(l.subject))
    // The holder key a reader edits; absent when no phase declares one.
    val holderKey = contextPhases.flatMap(_.holders).map(_.holder).distinct.sorted match
      case Nil => "-"
      case hs  => hs.mkString(",")
    val lostClauses = clauseLosses.map { l =>
      ContextSeamCheck.Finding(ContextSeamCheck.Kind.LostClause, l.fqn, holderKey,
        s"its constructors take a context clause and the emitted `${l.form}` does not carry one, " +
          "so nothing in its body can summon it", l.origin, l.subject)
    }
    if contextPhases.nonEmpty || lostClauses.nonEmpty then
      // contextSeams already drained above. lostClauses appended undrained (no remedy targets it).
      val ss = contextSeams ++ lostClauses
      CheckReport.record(ContextSeamCheck.Name, ss.map(_.report))
      say(s"CONTEXT SEAMS (where the context threading stopped): ${ss.size}")
      println(ContextSeamCheck.summary(ss))

    // ---- applied resolutions (recorded LAST so all drains are captured) ----
    val appliedRemedies = translated.binder.resolutions.all
    val refusedRemedies = translated.binder.resolutions.refusals
    CheckReport.record(PortRun.Remediation,
      Remediator.reports(fixes) ++ appliedRemedies.map(_.finding) ++ refusedRemedies.map(_.finding))
    if appliedRemedies.nonEmpty || refusedRemedies.nonEmpty then
      say(s"RESOLUTIONS: ${appliedRemedies.size} applied, draining " +
        s"${appliedRemedies.map(_.drained).sum} row(s); ${refusedRemedies.size} declined")
      appliedRemedies.foreach(a => println("  + " + a.render))
      // Refusal population: one row per declined site naming its guard.
      refusedRemedies.foreach(a => println("  ! " + a.render))

    // CHECK 2 — over the FINAL tree.
    val danglingSubs = record(PortRun.SubstitutionDangling, SubstitutionCheck.dangling(outDir, ownSubs))
    if ownSubs.dropTypes.nonEmpty && danglingSubs.isEmpty then
      say(s"substitutions: ${ownSubs.dropTypes.size} dropped types verified removed from the final code")

    // ---- policy: this module's own declared keys only (inherited keys checked by ManifestAgreement) ----
    // Merged phases resolved to the effective instance that ran.
    val ownPhases: List[Phase] = manifest match
      case Some(m) =>
        val effective = m.effectiveSurface
        m.surface.map(p =>
          if effective.exists(_ eq p) then p else effective.find(_.name == p.name).getOrElse(p))
      case scala.None => phases
    // Scoped by the subjects the fold recorded this manifest as contributing.
    val ownSurfaceKeys: Map[String, Set[String]] = manifest.map(_.surfaceFold.ownKeys).getOrElse(Map.empty)
    val ownKeys: Set[String]   = manifest.map(_.ownKeys).getOrElse(subs.keys)
    val ownPhaseNames: Set[String] = ownPhases.map(_.name).toSet
    val dropFindings = PolicyReport(PolicyReport.fromBindings(translated.binder.bindings).findings
      .filter(f => f.phase == "substitutions" && ownKeys(f.key)))
    // Rename phase keys (not in ownPhases since the run appends it); held to this module's own.
    val ownRenameKeys: Set[String] = manifest match
      case Some(m)    => m.typeRenames.keySet ++ m.subPackages.keySet ++ m.flattenNestedTypes ++ m.allowPackageSplit
      case scala.None => typeRenames.keySet ++ subPackages.keySet ++ flattenNestedTypes ++ allowPackageSplit
    val renameFindings = PolicyReport(
      renamePhase.toList.flatMap(_.policyReport.findings).filter(f => ownRenameKeys(f.key)))
    // Per-location selections: binding issues from the binder, inertness from the plan.
    val ownResolutionKeys: Set[String] =
      manifest.map(_.resolutions.keySet).getOrElse(Set.empty)
    val resolutionFindings =
      PolicyReport(PolicyReport.fromBindings(translated.binder.bindings).findings
        .filter(f => f.phase == balticporter.tir.Resolution.Seam && ownResolutionKeys(f.key))) ++
        PolicyReport(PolicyReport.fromResolutions(translated.binder.resolutions.troubles).findings
          .filter(f => ownResolutionKeys(f.key)))
    // Dependency declarations (not inherited, so no own-keys filter needed).
    val dependencyFindings = PolicyReport.fromDependencies(unneededDeps)
    // Surface phases: key findings from own phases, run findings from effective pipeline. // D13
    val runPhases: List[Phase] = manifest.map(_.effectiveSurface).getOrElse(phases)
    def sourcesIn(ps: List[Phase]) = ps.collect { case p: PolicySource => p }
    val keyFindings = PolicyReport.from(sourcesIn(ownPhases)).findings.filter(f =>
      f.about == balticporter.core.PolicyFinding.About.TheKey && ownPhaseNames(f.phase) &&
        ownSurfaceKeys.get(f.phase).forall(_.contains(balticporter.core.MergeablePolicy.subjectOf(f.key))))
    val runRefusals = PolicyReport.from(sourcesIn(runPhases)).findings.filter(
      _.about == balticporter.core.PolicyFinding.About.ThisRun)
    // DISTINCT: an own-declared phase appears in both lists.
    val surfacePolicyFindings = PolicyReport((keyFindings ++ runRefusals).distinct)
    val policy = dropFindings ++ renameFindings ++ resolutionFindings ++ dependencyFindings ++
      surfacePolicyFindings
    CheckReport.record(PortRun.Policy, policy.findings.map { f =>
      CheckReport.Finding(PortRun.Policy, f.issue.label, f.phase, f.setting, 0, s"${f.key} — ${f.detail}")
    })
    say(s"POLICY (declared keys that never fired): ${policy.findings.size}")
    if policy.nonEmpty then println(policy.render)

    // ---- rewrite-callsites (last check: asks if lanes phases named actually recorded) ----
    val rewriteFindings = RewriteCallSitesCheck.check(
      translated.rewrites, Option.when(CheckReport.enabled)(CheckReport.snapshot().keySet))
    CheckReport.record(RewriteCallSitesCheck.Name, rewriteFindings.map(_.report))
    say(s"REWRITE CALL SITES (retyping phases that answer nothing): ${rewriteFindings.size}")
    println(RewriteCallSitesCheck.summary(rewriteFindings, translated.rewrites, program))

    // ---- API parity (when manifest.parity is declared; not inherited) ----
    manifest.flatMap(_.parity).foreach { ref =>
      val parityRenames = if ref.packageMapping.nonEmpty then ref.packageMapping
                          else manifest.map(_.effectivePackageRenames).getOrElse(Map.empty)
      val parityFindings = ApiParityCheck.check(ref, emitDir, parityRenames)
      ApiParityCheck.Families.foreach { family =>
        val l = ApiParityCheck.lane(family)
        CheckReport.record(l, parityFindings.filter(_.check == l))
      }
      say(s"API PARITY: ${parityFindings.size} divergence(s)")
      println(ApiParityCheck.summary(parityFindings))
    }

    // Verify every check registered with the persistence layer.
    verifyRecorded()

    // ---- generated build (opt-in; the only write outside outDir) ----
    // Manifest dependencies added at this run's own configuration (main or test).
    project.foreach { spec =>
      val declared = manifest.map(_.dependencies).getOrElse(Nil).map(SbtGen.Dep.of)
      val withDeps = sourceSet match
        case SourceSet.Main => spec.copy(deps = spec.deps ++ declared)
        case SourceSet.Test => spec.copy(testDeps = spec.testDeps ++ declared)
      SbtGen.emitPort(portRoot, withDeps, effectivePhases, runtimeMode)
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
    // Full report with §1 classifications.
    say("report:")
    println(report.render)
    say(s"wrote $written ${sourceSet.noun} ($dropped dropped, $injected injected) -> $emitDir")

    // Best-effort ends nonzero (after producing all diagnostics).
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

  /** Anchor finding paths to this port's source root so stable ids are checkout-independent. */
  private def anchorReportPaths(): Unit =
    System.setProperty(DebugFlags.Prefix + "reportPathRoot", frontend.sourceRoot.toAbsolutePath.normalize.toString)

  /** Write `markers.tsv` for correlation. Gated on the artifact layer. */
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

  /** Write `catalog.tsv` (all rows, reached or not). Gated on the artifact layer.
    * Carries cache provenance so partial coverage numbers are labelled. */
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
      // Exclude dropped units: the map must describe what is on disk.
      val droppedEmitted = policySubs.dropTypes.map(emittedName)
      SrcMap.write(dir, rec.copy(entries = rec.entries.filterNot(e => droppedEmitted(e.unit))))
      Files.createDirectories(dir)
      val drops = policySubs.dropTypes.toList.sorted
        .map(fqn => Correlate.Dropped(fqn, emittedName(fqn)).tsv)
      Files.writeString(dir.resolve("dropped-types.tsv"),
        (Correlate.DroppedHeader :: drops).mkString("", "\n", "\n"))

  /** Record non-phase decisions. Called per translation. Order: emitter passes, ownership filter,
    * then funnel + manifest (after the filter). Returns withheld count. */
  private def recordRunDecisions(
      t: PortRun.Translated,
      injectedSources: List[(String, String)],
      plan: RuntimePlan,
  ): Int =
    t.decisions.recordAll(t.emitter.ownDecisions)
    // Applied remedy selection decisions (per declaration, not per site).
    t.decisions.recordAll(t.binder.resolutions.decisions)
    val withheld = retainOwnDecisions(t.program, t)
    recordCtorFunnel(t.program, t)
    recordDroppedSuperArgs(t.program, t)
    recordDroppedNilaryCtors(t.program, t)
    recordPolicyDecisions(t.program, t, injectedSources, plan)
    withheld

  /** Verify determinism after decisions are recorded (notes are emitted text). */
  private def verifyDeterminism(once: PortRun.Translated, injectedSources: List[(String, String)], plan: RuntimePlan): Unit =
    determinism match
      case Determinism.Off => ()
      case Determinism.Emission =>
        // Second emitter: same Surface and decisions, but NOT the catalog log (would double counts).
        val injSurf = balticporter.emit.InjectedSurface.fromRoots(ownSubs.inject)
        val extP = manifest.map(_.externalParenless).getOrElse(Set.empty)
        val again = new TirEmitter(once.program, once.plan.concreteMembers, provenance, once.decisions,
                                   preview, bestEffort, Some(once.surface), injectedSurface = injSurf,
                                   externalParenless = extP)
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

  /** Porter notes for an injected file, prepended at copy time. Matched by relative path. */
  private def injectionNotes(rel: String): String =
    val fqn = rel.stripSuffix(".scala").replace('/', '.')
    val mine = translatedDecisions.filter { d =>
      (d.kind == Decision.Kind.InjectedMember && d.detail.get("file").contains(rel)) ||
      (d.kind == Decision.Kind.DroppedType && d.detail.get("emitted").contains(fqn))
    }.sortBy(_.tsv)
    mine.map(PorterNote.render(_, "")).mkString

  /** Run's decisions for injection copy; set once before emission. */
  private var translatedDecisions: List[Decision] = Nil

  // refusedMembers — see doc below
  /** Members an engine rule refused to emit, published in the port map for dependents.
    * Currently: C11's nilary constructor. Keyed by emitted member FQN. */
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

  /** Whether this unit is dropped (by `Substituted` tag or policy key). Single drop predicate. */
  private def isDropped(program: Program, u: Tree.ClassDef): Boolean =
    program.symbolOf(u.symbol).exists(s => Substituted.tags(s) || policySubs.dropsType(s.fullName))

  /** Units of `emitOrder` this run actually writes. */
  private def emittedUnits(program: Program, units: List[Tree.ClassDef]): List[Tree.ClassDef] =
    units.filterNot(isDropped(program, _))

  /** Every class this run emits, nested included. Single D2 ownership domain. */
  private def emittedClasses(program: Program, translated: PortRun.Translated): List[Tree.ClassDef] =
    // `allClassDefs` — see `emittedFqns` above; D2's ownership range must not stop at the body.
    emittedUnits(program, translated.emitOrder).flatMap(u => StandardTraversal.allClassDefs(u)(using program))

  private def recordDroppedSuperArgs(program: Program, translated: PortRun.Translated): Unit =
    given Program = program
    // Uses the run's own Surface, not TrivialSurface. // D5
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

  /** Record dropped nilary constructors as decisions (C11). Subject is the owning type. */
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

  /** Record non-phase decisions: drops, injections, vendored runtime, supportSources.
    * Key is the manifest entry verbatim. One row per declared key. */
  private def recordPolicyDecisions(
      program: Program,
      translated: PortRun.Translated,
      injectedSources: List[(String, String)],
      plan: RuntimePlan,
  ): Unit =
    val log = translated.decisions
    // Index all classes (nested included) by emitted FQN for origin/symbol lookup.
    val typesByFqn: Map[String, Tree.ClassDef] =
      program.units
        .flatMap(u => StandardTraversal.allClassDefs(u)(using program))
        .flatMap(u => program.symbolOf(u.symbol).map(_.fullName -> u)).toMap
    // Fired keys from the binder.
    val fired = translated.binder.bindings.filter(_.binding.isBound).map(_.entry).toSet
    def emitted(fqn: String) = emittedName(fqn)
    // Resolve the type and its origin; fall back to the enclosing top-level type for the file.
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

    // Injections this module ships (ownSubs only).
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

    // Vendored runtime (§1(a)) and supportSources (§1(b)).
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

    // supportSources: types a phase cannot declare through RequiresRuntime.
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

  /** Record constructor funnel decisions. One row per class where the funnel acted.
    * `escapes` counts construction paths where java would not run the promoted body. // C7 */
  private def recordCtorFunnel(program: Program, translated: PortRun.Translated): Unit =
    given Program = program
    val plans = CtorFunnel.Plans(program, Some(translated.surface))
    def nested(cd: Tree.ClassDef): List[Tree.ClassDef] = StandardTraversal.allClassDefs(cd)
    // Own units minus dropped types.
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
            // Synthesised primaries are emitted `protected`; promoted ones keep java's visibility.
            "primaryVis"   -> (if p.isSynthesised then "protected" else "as-declared"),
            // Slot provenance: `sup$k` is the parent constructor's formal k.
            "slots"        -> (if p.synthetic.isEmpty then "-" else p.synthetic.map(_._1).mkString(",")),
            // Refused candidate slots with reasons.
            "notSlot"      -> (if p.notSlot.isEmpty then "-" else p.notSlot.map((f, w) => s"$f=$w").mkString(",")),
            // Whether the primary needed a disambiguator (C8/C9).
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

  /** Filter decisions to this module's own declarations (D2). Returns withheld count.
    * Ownership decided structurally via owner chain, not origin path. */
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

  /** Write `decisions.tsv`. Gated on artifact layer; written even when empty. */
  private def writeDecisions(log: DecisionLog, withheld: Int): Unit =
    if CheckReport.enabled then
      val p = Decision.write(CheckReport.runDir, log)
      say(s"decisions: ${log.size} (${log.summary})" +
        (if withheld == 0 then "" else s"; $withheld withheld — about a module this port only resolves against") +
        s" -> $p")

  /** Correlate a compiler/test log in-process. Returns `None` when the artifact layer is off.
    * @param extraSrcMaps other ports' maps for cross-port frame anchoring */
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

  /** Record a [[SubstitutionCheck]] result and return it. */
  private def record(check: String, fs: List[SubstitutionCheck.Finding]): List[SubstitutionCheck.Finding] =
    CheckReport.record(check, fs.map { f =>
      CheckReport.Finding(check, f.kind.toString, f.fqn, s"${f.fqn.replace('.', '/')}.scala", 0, f.render)
    })
    fs

  /** Required checks: the static set plus conditional lanes derived from manifest/pipeline. */
  private def requiredChecks: Set[String] =
    PortRun.RequiredChecks ++
      (if manifest.exists(_.serviceProviders.nonEmpty) then Set(balticporter.tir.ServiceProviders.Name) else Set.empty) ++
      // Resource lane (conditional on manifest.resources).
      (if manifest.exists(_.resources.nonEmpty) then Set(balticporter.tir.PortResources.Name) else Set.empty) ++
      // Collection lanes (conditional on pipeline).
      (if effectivePhases.exists(_.isInstanceOf[CollectionsTransform]) then
         Set(CollectionClosureCheck.Name, CollectionBoundaryCheck.Name,
             RetargetBoundaryCheck.Name, CollectionInternalCheck.Name)
       else Set.empty) ++
      // Nullability boundary (conditional on pipeline).
      (if effectivePhases.exists(_.isInstanceOf[NullabilityTransform]) then
         Set(NullabilityBoundaryCheck.Name) else Set.empty) ++
      // Test-framework refusal (conditional on pipeline).
      (if effectivePhases.exists(_.isInstanceOf[balticporter.transform.TestFrameworkTransform])
       then Set(balticporter.transform.TestFrameworkTransform.Refused) else Set.empty) ++
      // API parity lanes (conditional on manifest.parity).
      (if manifest.exists(_.parity.isDefined) then ApiParityCheck.AllLanes else Set.empty) ++
      // Opaque boundary (conditional on pipeline).
      (if effectivePhases.exists(_.isInstanceOf[PrimitiveToOpaqueTransform]) then
         Set(OpaqueBoundaryCheck.Name) else Set.empty)

  private def verifyRecorded(): Unit =
    if CheckReport.enabled then
      val missing = requiredChecks -- CheckReport.snapshot().keySet
      if missing.nonEmpty then
        sys.error(
          s"[$label] ${missing.size} check(s) produced no record: ${missing.toList.sorted.mkString(", ")}" +
            "  [§1(a) engine: PortRun ran but did not register these, so their numbers would silently " +
            "vanish from findings.tsv while stdout still showed them]"
        )

  /** Phases that actually run: idiom phases, declared surface, then rename LAST. // §4.56 */
  private def effectivePhases: List[Phase] =
    idiomPhases(declaredPhases) ++ PortRun.remedyPhases ++ PortRun.derivedPhases ++ renamePhase

  /** Remedies derived from what this run holds (phases + checks), never listed. */
  private def activeRemedies: RemedyVocabulary =
    RemedyVocabulary.from(effectivePhases.collect { case r: RemedySource => r } ++ PortRun.CheckRemedies)

  /** Active + classpath-declared remedies. */
  private def knownVocabulary: RemedyVocabulary = activeRemedies ++ knownRemedies

  /** Weave idiom phases at the positions they will occupy (placement IS measurement).
    * SAM/return-this go first; bean-properties gets public-field-accessors' scope if present. */
  private def idiomPhases(declared: List[Phase]): List[Phase] =
    val first = PortRun.wovenIdiomPhases
    // K21 face 2: hand public-field-accessors' scope to bean-properties.
    val exposed = declared.collectFirst {
      case p: balticporter.transform.PublicFieldAccessorTransform => p.scope
    }
    val spliced = declared.map {
      case b: balticporter.transform.BeanPropertyTransform => exposed.fold(b)(b.withExposed)
      case other                                           => other
    }
    first ++ spliced

  /** Retarget target FQNs the frontend should intern from the classpath (K18). */
  private def collectInternTypes(): Set[String] =
    effectivePhases.collect { case c: CollectionsTransform =>
      val fromRetarget = c.retarget.values.toSet
      val fromTypeArgs = c.retargetTypeArgs.values.flatten.flatMap(collectRetargetArgFqns).toSet
      fromRetarget ++ fromTypeArgs
    }.foldLeft(Set.empty[String])(_ ++ _)

  private def collectRetargetArgFqns(arg: CollectionsTransform.RetargetArg): Set[String] = arg match
    case CollectionsTransform.RetargetArg.FixedType(fqn)     => Set(fqn)
    case CollectionsTransform.RetargetArg.Applied(fqn, inner) =>
      Set(fqn) ++ inner.flatMap(collectRetargetArgFqns)
    case _ => Set.empty

  /** Whether this run resolves against sources outside its own tree (structural dependent). */
  private def foreignRoots: Boolean =
    val src = PortRun.real(frontend.sourceRoot)
    frontend.resolutionRoots.map(PortRun.real).exists(r => r != src)

  // discoverBasePorts -- see surfacePolicyFingerprint below
  /** This module's `SurfacePolicy` fingerprint for the published port map. */
  private def surfacePolicyFingerprint: String =
    PortMap.policyDigest(
      manifest.map(_.surfaceDigestInputs).getOrElse(effectivePhases.map(PortManifest.fingerprint)))

  /** Base's manifest fingerprint as this run inherited it. */
  private def basePolicyFingerprint(b: PortManifest): String =
    PortMap.policyDigest(b.surfaceDigestInputs)

  /** Base ports' published contracts, discovered once (lazy val so two readers agree). */
  private lazy val basePorts: List[ManifestAgreement.BasePort] = discoverBasePorts()

  private def discoverBasePorts(): List[ManifestAgreement.BasePort] =
    val chain = manifest.toList.flatMap(_.baseChain)
    if chain.isEmpty then Nil
    else
      val mine  = Set(label) ++ manifest.map(_.name)
      // Port's own search path (manifest, not operator).
      val found = PortMap.discover(PortMap.reportRoot, exclude = mine,
                                   configured = manifest.map(_.baseReports).getOrElse(Nil))
        .map(p => p.module -> p).toMap
      // Resolution roots via RealPath. // §5.4
      val roots = (frontend.resolutionRoots ++ List(frontend.sourceRoot)).map(balticporter.core.RealPath.of).distinct
      chain.map { b =>
        found.get(b.name) match
          case scala.None => ManifestAgreement.BasePort(b)
          case Some(pub) =>
            pub.map match
              case Left(err) => ManifestAgreement.BasePort(b, scala.None, pub.source, stale = List(err))
              case Right(m0) =>
                // JDK fingerprint. // ENGINE-LIMITS M5.10
                PortMap.freshness(m0, balticporter.core.EngineInfo.fingerprint, roots,
                                  basePolicyFingerprint(b), balticporter.core.JvmInfo.specification) match
                  case PortMap.Freshness.Fresh          => ManifestAgreement.BasePort(b, Some(m0), pub.source)
                  case PortMap.Freshness.Stale(r)       => ManifestAgreement.BasePort(b, scala.None, pub.source, stale = List(r))
                  case PortMap.Freshness.Unverified(r)  => ManifestAgreement.BasePort(b, Some(m0), pub.source, unverified = List(r))
                  case PortMap.Freshness.JdkMismatch(published, running) =>
                    ManifestAgreement.BasePort(b, scala.None, pub.source, jdk = Some(published -> running))
      }

  /** Types resolved against but not converted. Upstream name rebuilt from origin, not `fullName`. */
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

  /** One translation. Determinism verified later in `verifyDeterminism`. */
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
    val enrichedFrontend = frontend.copy(internTypes = frontend.internTypes ++ collectInternTypes())
    val types   = SpoonTir.buildModel(enrichedFrontend, lenient = lenient)
    // Catalog log: per-translation (Determinism.Full translates twice). fatal=false: counts, not aborts.
    val catalog = new balticporter.catalog.CatalogLog(fatal = false)
    // Preserved annotations (T16): empty default, travels with frontend config.
    val parsed  = SpoonTir.fromTypes(types, policySubs, catalog, enrichedFrontend.preservedAnnotations,
                                     enrichedFrontend.internTypes)
    // Policy binding: resolved before any phase runs, per-translation.
    val binder = new PolicyBinder(parsed, parsed.members, runScope(parsed))
    bindDeclaredPolicy(binder)
    // Per-location remedy selections, bound through the same binder.
    binder.resolving(ResolutionPlan.of(
      manifest.map(_.effectiveResolutions).getOrElse(Map.empty),
      knownVocabulary, activeRemedies.byId.keySet, binder))
    // Pipeline runs with binder, rewrite log, and idiom log -- all per-translation.
    val rewrites = new RewriteLog
    val idioms   = new IdiomLog
    val (program, decisions) = Pipeline.runTraced(parsed, effectivePhases, binder, catalog, rewrites, idioms)
    val plan    = RuntimePlan.of(effectivePhases, runtimeMode)
    // externalConcrete derived from phases, never passed in.
    val (mine, theirs) = partitionUnits(program)
    // §8.3's view, built before the emitter (funnel's fixpoint must not span the base).
    val surface = new balticporter.core.PublishedSurface(
      program, mine, basePorts.flatMap(b => b.map.map(b.name -> _)))
    // Emitter reads decisions, catalog, injected surface, and external parenless members.
    val injSurface = balticporter.emit.InjectedSurface.fromRoots(ownSubs.inject)
    val extParenless = manifest.map(_.externalParenless).getOrElse(Set.empty)
    val emitter = new TirEmitter(program, plan.concreteMembers, provenance, decisions, preview, bestEffort,
                                 Some(surface), catalog = catalog, injectedSurface = injSurface,
                                 externalParenless = extParenless)
    PortRun.Translated(program, plan, emitter, mine, theirs, cache.map(new ActionCache(_, true)),
                       decisions, binder, surface, parsed, catalog, rewrites, idioms)

  /** Bind all declared keys (drops + phase keys) through the binder. */
  private def bindDeclaredPolicy(binder: PolicyBinder): Unit =
    policySubs.dropTypes.toList.sorted.foreach(k =>
      binder.bindType("substitutions", "Substitutions.dropTypes", k))
    policySubs.dropMethods.toList.sorted.foreach(k =>
      binder.bindMembers("substitutions", "Substitutions.dropMethods", k))
    // the PHASES' keys are bound by `Pipeline.runTraced`, so no caller of it can forget.

  // partitionUnits and runScope below
  /** Build `RunScope` from the unit partition and the manifest's contributed subjects. */
  private def runScope(parsed: Program): RunScope =
    // Types the base substituted (detection phases skip these owners). // D14
    val substituted = effectivePhases.collect { case p: PortMapTransform => p.substitutedOwnerTypes }.flatten.toSet
    // Base's upstream member descriptors for opaque-phase coercion. // O8
    val memberUp = {
      val mine = manifest.map(_.name).toSet
      PortMap.discover(PortMap.reportRoot, exclude = mine,
                       configured = manifest.map(_.baseReports).getOrElse(Nil))
        .flatMap(p => p.map.toOption.toList.flatMap(_.members.map(_.upstream)))
        .toSet
    }
    RunScope.of(partitionUnits(parsed)._1.map(_.symbol).toSet,
                manifest.map(_.contributedSubjects).getOrElse(Map.empty),
                // Targets and verdict overrides for in-pipeline portability reasoning.
                RunScope.PlatformPolicy(targets, verdictOverrides),
                substituted,
                memberUp,
                // types this run drops+injects -- retarget must not resolve through the parent (item 2).
                policySubs.dropTypes)

  private def partitionUnits(program: Program): (List[Tree.ClassDef], List[Tree.ClassDef]) =
    if frontend.resolutionRoots.isEmpty then (program.units, Nil)
    else
      // Input list, realpathed once. // §5.4
      val mine = frontend.files.map(f => PortRun.real(frontend.sourceRoot.resolve(f))).toSet
      program.units.partition { u =>
        // Synthesised units are always converted (refusing on missing origin is a silent omission).
        if PortRun.isSynthesised(u.origin) then true
        else mine.contains(PortRun.real(java.nio.file.Path.of(u.origin.javaPath)))
      }

  private def wipe(dir: Path): Unit =
    if Files.exists(dir) then Files.walk(dir).iterator().asScala.toList.reverse.foreach(Files.delete)

  private def write(p: Path, text: String): Unit =
    Files.createDirectories(p.getParent)
    Files.writeString(p, text)

object PortRun:

  /** All declared symbols in a unit (class, nested, members). Uses `allClassDefs` (not `cd.body`).
    * The subject set `NoteCoverageCheck` joins on. */
  def declaredSymbols(cd: Tree.ClassDef, into: collection.mutable.Set[SymId])(using Program): Unit =
    StandardTraversal.allClassDefs(cd).foreach { c =>
      into += c.symbol
      c.body.foreach { case d: Definition => into += d.symbol; case _ => () }
      c.enumCases.foreach { ec =>
        into += ec.symbol
        ec.body.foreach { case d: Definition => into += d.symbol; case _ => () }
      }
    }

  /** Symlink-resolved path, falling back to normalisation. // §5.4 */
  def real(p: Path): String = balticporter.core.RealPath.str(p)

  // =========================================================================================
  // a SYNTHESISED unit, and the one module allowed to write it (ENGINE-LIMITS.md §13 O5)
  // =========================================================================================

  /** Whether a phase minted this unit (vs the frontend parsing it from a Java file). */
  def isSynthesised(o: Origin): Boolean =
    o.javaPath.isEmpty || o.javaPath.startsWith("<")

  /** A synthesised unit at an FQN a base already emits. */
  final case class SyntheticClaim(fqn: String, base: String, disposition: String):
    def render: String =
      s"$fqn — synthesised by a phase in THIS run, and `$base` already emits a type at that name " +
        s"($disposition). Two definitions of one FQN do not compile, and an opaque type cannot even " +
        "be duplicated harmlessly: opacity is per-DEFINITION, so the copy's own accessors stop " +
        "type-checking against the first definition's abstract type"

  /** Find synthesised units that collide with a base's published types. Pure function.
    * Returns empty when `bases` is empty (a base port must be allowed to mint). */
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

  /** Base-surface findings from gaps. Fatal gaps shaped emitted text; others are informational. */
  // collapseDivergence -- see CollapseAgreement below
  /** Result of `collapseDivergence`: `checked` is the denominator, `gaps` the disagreements. */
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
      // Base's emitted types and member forms.
      val emittedTypes = bases.map((m, map) =>
        m -> map.entries.filter(e => e.kind == "type" &&
          e.disposition != PortMap.Disposition.Dropped).map(_.upstream).toSet).toMap
      val memberForm = bases.map((m, map) =>
        m -> map.entries.filter(e => e.kind == "member" &&
          e.disposition != PortMap.Disposition.Dropped)
          .map(e => e.upstream -> e.memberShape.form).toMap).toMap
      // Bridge property key (Owner#property) to upstream accessor key (Owner#getProperty).
      def accessorKey(propertyKey: String): String =
        val owner = propertyKey.takeWhile(_ != '#')
        pairs.get(propertyKey).map(_.takeWhile(_ != '/')).filter(_.nonEmpty)
          .map(g => s"$owner#$g").getOrElse(propertyKey)
      val mine = idioms.all.iterator.collect {
        case c if c.kind == IdiomKind.BeanCollapse => c.subject -> (c.verdict match
          case IdiomVerdict.Converted => targetOf(c.subject).config
          case _                      => "")
      }.toMap
      // Denominator: every (pair, base) the question is owed for.
      val asked = for
        (key, derived) <- mine.toList.sortBy(_._1)
        owner           = key.takeWhile(_ != '#')
        (module, _)    <- bases
        if emittedTypes(module).contains(owner)
      yield (key, derived, module)
      val out = for
        (key, derived, module) <- asked
        upstreamKey             = accessorKey(key)
        gap            <- memberForm(module).get(upstreamKey) match
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

  /** The §1(a) idiom phases every run carries. Fresh instances per call. */
  def wovenIdiomPhases: List[Phase] =
    List(new balticporter.transform.SamLambdaTransform, new balticporter.transform.ReturnThisCensus)

  // CheckRemedies and acceptSubject below
  /** Find the nearest owner above a portability site that has a selection. */
  private[runner] def acceptSubject(program: Program, v: PortabilityCheck.Violation,
                                    plan: balticporter.tir.ResolutionPlan): SymId =
    ownerChain(program, v.enclosing)
      .find(id => plan.selected(id, balticporter.tir.PortabilityCheck.AcceptJvmOnly).isDefined)
      .getOrElse(v.enclosing)

  /** Owner chain from `from` upward. Fuel-bounded. */
  def ownerChain(program: Program, from: SymId): List[SymId] =
    def climb(s: SymId, fuel: Int): List[SymId] =
      if s == SymId.None || fuel == 0 then Nil
      else s :: program.symbolOf(s).map(sym => climb(sym.owner, fuel - 1)).getOrElse(Nil)
    climb(from, 64)

  val CheckRemedies: List[balticporter.tir.RemedySource] =
    List(HeapPollutionCheck, OverloadRiskCheck, OmissionCheck, JdkSurfaceCheck,
         balticporter.transform.CollectionBoundaryCheck,
         balticporter.transform.ContextSeamCheck,
         balticporter.transform.NullabilityBoundaryCheck, balticporter.tir.PortabilityCheck)

  /** Phases that carry out remedy menus. Woven after declared surface, before rename. Fresh per call. */
  def remedyPhases: List[Phase] =
    List(new HeapPollutionCheck.Apply, new OverloadRiskCheck.Apply)

  /** §1(a) universal phases derived unconditionally. No-op when trigger is absent. Fresh per call. */
  def derivedPhases: List[Phase] =
    List(new UnusedSymbolTransform, new SuppressionPhase)

  // ---- check lane names (as they appear in counts.tsv) ----
  val Signature            = "signature"
  val Omissions            = OmissionCheck.Name
  val PortabilityAll       = "portability(all)"
  val PortabilityEmitted   = PortabilityCheck.EmittedLane
  val PortabilityInjected  = "portability(injected)"
  val Remediation          = balticporter.tir.Resolution.Check
  val SubstitutionEmitted  = "substitution(emitted)"
  val SubstitutionDangling = "substitution(dangling)"
  val Policy               = "policy"
  val Manifest             = "manifest"
  val PortMapCheck         = "port-map"
  val BaseSurface          = "base-surface"
  val TriviaDropped        = "trivia"
  val TriviaRecovered      = "trivia(recovered)"
  val TriviaDeliberate     = "trivia(deliberate)"
  val JdkSurface           = JdkSurfaceCheck.Name
  val IdiomConverted       = IdiomCheck.Converted
  val IdiomRefused         = IdiomCheck.Refused
  val IdiomResidue         = IdiomCheck.Residue
  val UnusedHandled        = UnusedSymbolTransform.Handled
  val UnusedRefused        = UnusedSymbolTransform.Refused

  /** Checks every run must record. Named, not derived, so a forgotten check fails the next run. */
  val RequiredChecks: Set[String] = Set(
    Signature, Omissions, PortabilityAll, PortabilityEmitted, PortabilityInjected, Remediation,
    SubstitutionEmitted, SubstitutionDangling, Policy, Manifest, PortMapCheck,
    TriviaDropped, TriviaRecovered, TriviaDeliberate,
    BaseSurface,
    JdkSurface,
    IdiomConverted, IdiomRefused, IdiomResidue,
    CatalogCheck.Consulted, CatalogCheck.Unreached, CatalogCheck.Unmechanised,
    CatalogCheck.Undischarged,
    CatalogCheck.Uncited,
    DependencyCheck.All, DependencyCheck.Name,
    DependencyCheck.Declared,
    RewriteCallSitesCheck.Name,
    UnusedHandled, UnusedRefused,
    // Collection/nullability/opaque/test-framework lanes are conditionally required (see requiredChecks).
  )

  /** One translation. `sourceOf` is memoised through an optional `ActionCache` (advisory). */
  final class Translated(
      val program: Program,
      val plan: RuntimePlan,
      val emitter: TirEmitter,
      val emitOrder: List[Tree.ClassDef],
      /** Units resolved against but not emitted. */
      val foreign: List[Tree.ClassDef],
      val cache: Option[ActionCache],
      /** What the phases decided. Run's non-phase deciders record into the same log. */
      val decisions: DecisionLog = new DecisionLog,
      /** Policy key resolutions, taken before the pipeline ran. Per-translation. */
      val binder: PolicyBinder = new PolicyBinder(
        new Program(Nil, SymbolTable(Nil), Xref.build(Nil), MemberIndex.empty), MemberIndex.empty),
      /** §8.3's published surface view. Per-translation. */
      val surface: Surface = new TrivialSurface(
        new Program(Nil, SymbolTable(Nil), Xref.build(Nil), MemberIndex.empty)),
      /** Frontend output before phases. `MarkerCheck` compares minted vs survived markers. */
      val parsed: Program = new Program(Nil, SymbolTable(Nil), Xref.build(Nil), MemberIndex.empty),
      /** Catalog log for this translation. Per-translation. */
      val catalog: balticporter.catalog.CatalogLog = balticporter.catalog.CatalogLog.discarding,
      /** What each phase moved (observed by `Pipeline.runTraced`). Per-translation. */
      val rewrites: RewriteLog = RewriteLog.discarding,
      /** What idiom phases considered. Per-translation. */
      val idioms: IdiomLog = IdiomLog.discarding,
  ):
    private val memo = collection.mutable.Map.empty[SymId, String]
    // Decisions are part of the cache key (porter notes). Read lazily.
    private lazy val keys = cache.map(_ => TirCacheKey.forUnits(program, emitOrder, decisions.all))

    /** Cache hits -- units served from cache skip catalog consults. */
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
                  // Only cache units that recorded nothing (decisions/notes would be lost on a hit).
                  if !emitter.recordedForCache(nameOf(u)) then c.put(k, out)
                  out
            case _ => emitter.emitUnit(u)
        },
      )

    private def nameOf(u: Tree.ClassDef): String =
      program.symbolOf(u.symbol).map(_.fullName).getOrElse("")

/** What a run found, classified per CLAUDE.md §1 (a/b/c). */
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

  /** Findings that must stop the run (leaked drops, dangling subs, fatal manifest disagreements). */
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

  /** Which of CLAUDE.md §1's three kinds a finding is. */
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

/** What a run produced. Returned so a porting program can assert on it. */
final case class PortResult(
    program: Program,
    outDir: Path,
    written: Int,
    dropped: Int,
    injected: Int,
    runtime: RuntimePlan,
    report: PortReport,
)
