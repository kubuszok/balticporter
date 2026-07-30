package balticporter.runner

import balticporter.core.*
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.sbtgen.SbtGen
import balticporter.tir.{CheckReport, Correlate, CorrelateRun, DebugFlags, Decision, DecisionLog, OmissionCheck, Origin, Phase, Pipeline, PortabilityCheck, Program, Reason, Remediator, RewriteTrace, SrcMap, SymId, Tree, TriviaCheck}
import balticporter.transform.{CollectionBoundaryCheck, CollectionClosureCheck, CollectionsTransform, MethodBodyTransform, PackageRenameTransform, PortMapTransform}

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
  * @param project    emit the sbt skeleton for the port (build.sbt, .gitignore, the engine pin, the
  *                   `src_managed` wiring). `None` for a port whose build is maintained by hand.
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
    runtimeMode: RuntimeMode = RuntimeMode.Dependency,
    supportSources: Map[String, String] = Map.empty,
    project: Option[SbtGen.ProjectSpec] = scala.None,
    determinism: Determinism = Determinism.Emission,
    cache: Option[Path] = scala.None,
    lenient: Boolean = true,
    manifest: Option[PortManifest] = scala.None,
    /** printed as the last line — what the operator does next. */
    nextStep: String = "",
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

  private def renames: Map[String, String] = manifest.map(_.effectivePackageRenames).getOrElse(packageRenames)

  /** where this source set's emitted Scala goes. From [[SbtGen]], never composed by hand (§5.5). */
  def outDir: Path = SbtGen.managedDir(portRoot, sourceSet.configName)

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
      manifest.isEmpty || (phases.isEmpty && subs == Substitutions.none && packageRenames.isEmpty),
      s"[$label] a `manifest` SUPPLIES `phases`, `subs` and `packageRenames`; passing either " +
        "source alongside it would give this run two policies and no way to say which one the " +
        "dependent modules have to agree with. Move the values into the PortManifest.",
    )

    anchorReportPaths()

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
    val checkedUnits = translated.emitOrder.filterNot(u =>
      program.symbolOf(u.symbol).map(_.fullName).exists(policySubs.dropsType))
    val omissions = OmissionCheck.check(program, checkedUnits)
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
      locally {
        given Program = program
        CheckReport.record(CollectionClosureCheck.Name, clo.map(_.report))
        CheckReport.record(CollectionBoundaryCheck.Name, bnd.map(_.report))
      }
      say(s"COLLECTION CLOSURE (mapped supertype, unmapped subtype): ${clo.size}")
      if clo.nonEmpty then say(CollectionClosureCheck.Classification)
      println(CollectionClosureCheck.summary(clo))
      say(s"COLLECTION BOUNDARY (stranded slots the phase created): ${bnd.size}")
      println(CollectionBoundaryCheck.summary(bnd))
    }

    // ---- cross-port composition: does the shared surface agree with the module that emits it? ----
    // Runs on EVERY port. On a base port `shared` is empty and the check is a no-op by arithmetic
    // rather than by a branch — the same discipline as an empty policy making a phase a no-op.
    val basePorts = discoverBasePorts()
    val agreement = ManifestAgreement.check(manifest, sharedSurface(program, translated.foreign), foreignRoots, basePorts)
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
    val droppedIds  = program.symbols.all.collect { case s if policySubs.dropsType(s.fullName) => s.id }.toSet
    // A type this run does not ship is either DROPPED by policy or FOREIGN — resolved against and
    // emitted by another module. Both must be excluded from the shipped-code number, or a dependent
    // port reports its base's findings as its own (see `PortabilityCheck.inEmittedCode`).
    val foreignIds  = translated.foreign.map(_.symbol).toSet
    val notShipped  = (id: SymId) => droppedIds(id) || foreignIds(id)
    val allViolations = PortabilityCheck.check(program)
    val portability   = PortabilityCheck.inEmittedCode(program, allViolations, notShipped)
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
    CheckReport.record(PortRun.Remediation, Remediator.reports(fixes))
    say(s"PORTABILITY (Scala.js/Native): ${portability.size} site(s) on JVM-only APIs in EMITTED code")
    if portability.nonEmpty then say(PortReport.Kind.Portability.classification)
    println(PortabilityCheck.summary(portability, fixes))

    val renameReport = PackageRenameTransform.check(program, renames)
    if renames.nonEmpty then
      say(s"package rename (verified AFTER the phase — every prefix must now be unmatched):")
      println(renameReport.render)

    // ---- emission ----
    val plan = RuntimePlan.of(effectivePhases, runtimeMode)
    wipe(outDir)
    Files.createDirectories(outDir)

    var written = 0
    var dropped = 0
    // what was actually SHIPPED, paired with the Java it came from — the input to the trivia check
    // below, which compares text against text and so must see exactly the files that were written.
    val shipped = collection.mutable.ListBuffer.empty[TriviaCheck.Unit]
    translated.emitOrder.foreach { u =>
      val full = program.symbolOf(u.symbol).map(_.fullName).getOrElse("Unit")
      // Substitutions.dropTypes: PARSED (so every reference to it still resolves) but NOT emitted —
      // the injected replacement supplies this FQN instead.
      //
      // Decided by the frontend's `Substituted` TAG, not by matching `full` against the drop set.
      // `full` is the name AFTER the package rename, and every drop key is written in the UPSTREAM
      // namespace — so a renamed port matched none of them and emitted all 11 dropped types while
      // reporting `0 dropped`. The tag is applied at parse time and is therefore rename-proof.
      // (Kept as a fallback for a symbol the frontend never tagged.)
      val substituted = program.symbolOf(u.symbol).exists(Substituted.tags)
      if substituted || policySubs.dropsType(full) then dropped += 1
      else
        val text = translated.sourceOf(u)
        write(outDir.resolve(full.replace('.', '/') + ".scala"), text)
        shipped += TriviaCheck.Unit(PortRun.real(Path.of(u.origin.javaPath)), text)
        written += 1
    }
    // Support types a phase RETYPED code onto. Two feeds, one rule: what the phases DECLARE
    // (RequiresRuntime → RuntimePlan) and what a phase that cannot declare it hands over.
    written += plan.writeSources(outDir)
    supportSources.foreach { (fqn, src) => write(outDir.resolve(fqn.replace('.', '/') + ".scala"), src); written += 1 }

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
    val shippedUnits = shipped.toList
    val triviaLost   = TriviaCheck.check(shippedUnits)
    val triviaFiles  = TriviaCheck.comparable(shippedUnits)
    CheckReport.record(PortRun.TriviaDropped, triviaLost.map(_.report))
    say(s"TRIVIA (comments in the Java that did not reach the Scala): ${triviaLost.size}")
    if triviaLost.nonEmpty then say(PortReport.Kind.Trivia.classification)
    println(TriviaCheck.summary(triviaLost, triviaFiles))

    // CHECK 1 — before injection, so a file at a dropped type's path can only be the emitter's.
    val leaked = record(PortRun.SubstitutionEmitted, SubstitutionCheck.emittedDroppedTypes(outDir, policySubs))

    // ---- injection: ready-made Scala copied verbatim (survives the wipe above) ----
    var injected = 0
    ownSubs.inject.filter(Files.exists(_)).foreach { root =>
      Files.walk(root).iterator().asScala
        .filter(p => p.toString.endsWith(".scala"))
        .toList.sorted
        .foreach { src =>
          val dst = outDir.resolve(root.relativize(src).toString)
          Files.createDirectories(dst.getParent)
          Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING)
          injected += 1
        }
    }
    // injected replacements bypass the TIR — scan their TEXT for the same portability rules, so a
    // hand-written shim cannot quietly reintroduce the API the substitution was meant to remove.
    val injectedViolations = ownSubs.inject.filter(Files.exists(_)).flatMap { root =>
      SubstitutionCheck.scalaSources(root).flatMap { src =>
        PortabilityCheck.inInjectedSource(root.relativize(src).toString, Files.readString(src))
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
    val injectedSources: List[(String, String)] = ownSubs.inject.filter(Files.exists(_)).flatMap { root =>
      SubstitutionCheck.scalaSources(root).map { src =>
        val rel = root.relativize(src).toString.replace('\\', '/')
        rel.stripSuffix(".scala").replace('/', '.') -> rel
      }
    }
    val injectedFqns = injectedSources.map(_._1).toSet ++ plan.sources.keySet ++ plan.required ++ supportSources.keySet
    val bodyKeys: Set[String] =
      effectivePhases.collect { case m: MethodBodyTransform => m.substituted }.flatten.toSet
    val portMap = PortMap.of(
      module       = label,
      engine       = balticporter.core.EngineInfo.fingerprint,
      emittedTypes = translated.emitOrder.map(u => program.symbolOf(u.symbol).map(_.fullName).getOrElse(""))
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

    // ---- DECISION PROVENANCE: how this run arrived at the code it just wrote ----
    // The phases recorded theirs while the pipeline ran; the run's own non-phase deciders — the
    // substitution manifest and the injection copy — record here, into the SAME log, because the
    // question an investigating agent asks does not care which layer answered it.
    recordPolicyDecisions(program, translated, injectedSources, plan)
    writeDecisions(translated.decisions)

    // CHECK 2 — over the FINAL tree.
    val danglingSubs = record(PortRun.SubstitutionDangling, SubstitutionCheck.dangling(outDir, ownSubs))
    if ownSubs.dropTypes.nonEmpty && danglingSubs.isEmpty then
      say(s"substitutions: ${ownSubs.dropTypes.size} dropped types verified removed from the final code")

    // ---- policy: every (b) seam this run holds, read AFTER the frontend has consulted it ----
    // Only the policy THIS module declares — its own drops, and the phases in its own `surface`.
    //
    // A §1(b) finding says "fix this key in the library's manifest", and an INHERITED key lives in
    // the base's manifest: reporting it here tells every dependent module about a mistake none of
    // them can fix, and one bad key in a library with eighteen modules becomes eighteen findings
    // that all mean the same thing. The inherited half is not unchecked — it is checked more
    // precisely by `ManifestAgreement`, which says which base the key came from and whether it
    // fired HERE (`InheritedKeyNeverFired`).
    val ownPolicy: PolicySource   = manifest.getOrElse(subs)
    val ownPhases: List[Phase]    = manifest.map(_.surface).getOrElse(phases)
    val policy = PolicyReport.from(ownPolicy :: ownPhases.collect { case p: PolicySource => p })
    CheckReport.record(PortRun.Policy, policy.findings.map { f =>
      CheckReport.Finding(PortRun.Policy, f.issue.label, f.phase, f.setting, 0, s"${f.key} — ${f.detail}")
    })
    say(s"POLICY (declared keys that never fired): ${policy.findings.size}")
    if policy.nonEmpty then println(policy.render)

    // Every check this run believes it ran must ALSO have registered itself with the persistence
    // layer, or a number reaches the operator's terminal and never reaches `findings.tsv`. That is
    // the same class of gap as a check nobody invoked, one layer down, and it is invisible without
    // this assertion.
    verifyRecorded()

    // ---- the generated build ----
    project.foreach { spec => SbtGen.emitPort(portRoot, spec, effectivePhases, runtimeMode) }

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
    say(s"wrote $written ${sourceSet.noun} ($dropped dropped, $injected injected) -> $outDir")

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
      val droppedEmitted = policySubs.dropTypes.map(fqn => PackageRenameTransform.renamed(fqn, renames))
      SrcMap.write(dir, rec.copy(entries = rec.entries.filterNot(e => droppedEmitted(e.unit))))
      Files.createDirectories(dir)
      val drops = policySubs.dropTypes.toList.sorted
        .map(fqn => Correlate.Dropped(fqn, PackageRenameTransform.renamed(fqn, renames)).tsv)
      Files.writeString(dir.resolve("dropped-types.tsv"),
        (Correlate.DroppedHeader :: drops).mkString("", "\n", "\n"))

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
    val unitsByFqn: Map[String, Tree.ClassDef] =
      program.units.flatMap(u => program.symbolOf(u.symbol).map(_.fullName -> u)).toMap
    val fired = policySubs.matched
    def emitted(fqn: String) = PackageRenameTransform.renamed(fqn, renames)
    // The unit a policy key is ABOUT, and its Java file. A NESTED type is not a unit and has no
    // origin of its own, so the enclosing top-level type supplies the FILE —
    // `ParallelArray$ChannelDescriptor` lives in `ParallelArray.java`, and a row saying
    // `<synthetic>` would be unnavigable for the sake of a `$`. Its SYMBOL is not borrowed with
    // it: that id names a different type, and a wrong id is worse than none. Cut at the separator
    // (§4.56).
    def at(fqn: String): (SymId, Origin) =
      val e = emitted(fqn)
      unitsByFqn.get(e) match
        case Some(u)    => (u.symbol, u.origin)
        case scala.None => (SymId.None, unitsByFqn.get(e.takeWhile(_ != '$')).map(_.origin).getOrElse(Origin.synthetic))

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

  /** Write `decisions.tsv` beside the run's other artifacts, on every reporting run.
    *
    * Gated on exactly what the source map is gated on, so one switch turns the artifact layer off
    * and a unit-test JVM leaves nothing behind. Written even when EMPTY: a port with no policy made
    * no recorded decisions, and a header-only file says that, where a missing file cannot be told
    * from a run that never got this far — the same distinction `CheckReport` keeps for a check that
    * found nothing. */
  private def writeDecisions(log: DecisionLog): Unit =
    if CheckReport.enabled then
      val p = Decision.write(CheckReport.runDir, log)
      say(s"decisions: ${log.size} (${log.summary}) -> $p")

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
    * @param extraSrcMaps other ports' maps, scoped `main`/`test`. A test suite's failure is
    *                     anchored on the LIBRARY member that threw, which lives in another port —
    *                     so pass the library's map to get a `main-frame` anchor instead of a
    *                     `test-frame` one. */
  def correlate(
      scalac: Option[Path] = scala.None,
      tests: Option[Path] = scala.None,
      extraSrcMaps: List[(String, Path)] = Nil,
  ): CorrelateRun.Result =
    val mine = CheckReport.runDir.resolve("srcmap.tsv")
    CorrelateRun.run(CorrelateRun.Request(
      srcmaps  = extraSrcMaps :+ (sourceSet.configName -> mine),
      scalac   = scalac,
      tests    = tests,
      out      = CheckReport.runDir,
      baseline = Some(CheckReport.baselineDir),
    ))

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
  private def verifyRecorded(): Unit =
    if CheckReport.enabled then
      val missing = PortRun.RequiredChecks -- CheckReport.snapshot().keySet
      if missing.nonEmpty then
        sys.error(
          s"[$label] ${missing.size} check(s) produced no record: ${missing.toList.sorted.mkString(", ")}" +
            "  [§1(a) engine: PortRun ran but did not register these, so their numbers would silently " +
            "vanish from findings.tsv while stdout still showed them]"
        )

  /** the phases that actually RUN: the declared surface, then the namespace rename LAST (§4.56). */
  private def effectivePhases: List[Phase] =
    if renames.isEmpty then declaredPhases else declaredPhases :+ new PackageRenameTransform(renames)

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
  private def discoverBasePorts(): List[ManifestAgreement.BasePort] =
    val chain = manifest.toList.flatMap(_.baseChain)
    if chain.isEmpty then Nil
    else
      val mine  = Set(label) ++ manifest.map(_.name)
      val found = PortMap.discover(PortMap.reportRoot, exclude = mine).map(p => p.module -> p).toMap
      val roots = (frontend.resolutionRoots ++ List(frontend.sourceRoot)).map(_.toAbsolutePath.normalize).distinct
      chain.map { b =>
        found.get(b.name) match
          case scala.None => ManifestAgreement.BasePort(b)
          case Some(pub) =>
            pub.map match
              case Left(err) => ManifestAgreement.BasePort(b, scala.None, pub.source, stale = List(err))
              case Right(m0) =>
                PortMap.freshness(m0, balticporter.core.EngineInfo.fingerprint, roots) match
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

  private def translate(): PortRun.Translated =
    val once = translateOnce()
    determinism match
      case Determinism.Off      => once
      case Determinism.Emission =>
        // a SECOND emitter over the same program: independent mutable state, independent lazy
        // tables, same bytes required.
        val again = new TirEmitter(once.program, once.plan.concreteMembers, provenance)
        val diffs = once.emitOrder.filter(u => again.emitUnit(u) != once.sourceOf(u))
        if diffs.nonEmpty then determinismViolation("emission", once, diffs)
        say(s"determinism: ${once.emitOrder.size} units emitted twice, byte-identical " +
          s"(${Determinism.FullFlag} also re-parses)")
        once
      case Determinism.Full =>
        val twice = translateOnce()
        if twice.emitOrder.size != once.emitOrder.size then
          sys.error(s"[$label] determinism violation: ${once.emitOrder.size} units first, ${twice.emitOrder.size} second")
        val diffs = once.emitOrder.zip(twice.emitOrder).collect {
          case (a, b) if once.sourceOf(a) != twice.sourceOf(b) => a
        }
        if diffs.nonEmpty then determinismViolation("full translation", once, diffs)
        say(s"determinism: ${once.emitOrder.size} units translated twice from scratch, byte-identical")
        once

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
    // `runTraced`, so the phases' DECISIONS travel with the program they produced. The log belongs
    // to THIS translation: `Determinism.Full` translates twice and the run keeps the first, which
    // is only coherent because neither log is shared (CLAUDE.md §5.1).
    val (program, decisions) = Pipeline.runTraced(SpoonTir.fromTypes(types, policySubs), effectivePhases)
    val plan    = RuntimePlan.of(effectivePhases, runtimeMode)
    // `externalConcrete` is DERIVED, never passed in: a caller who has to remember it is a caller
    // who forgets it, and forgetting it silently disables diamond-conflict detection against an
    // injected parent.
    val emitter = new TirEmitter(program, plan.concreteMembers, provenance)
    val (mine, theirs) = partitionUnits(program)
    PortRun.Translated(program, plan, emitter, mine, theirs, cache.map(new ActionCache(_, true)), decisions)

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

  /** a path with symlinks resolved, falling back to lexical normalisation when it does not exist
    * (a synthetic origin, a root that was never created). */
  def real(p: Path): String =
    try p.toRealPath().toString
    catch case _: Exception => p.toAbsolutePath.normalize.toString

  /** Every check's name as it appears in `counts.tsv`. Named here, in the orchestrator, because the
    * orchestrator is now the only thing that records: a check is a pure function of a `Program` and
    * does not know it is being persisted. */
  val Signature            = "signature"
  val Omissions            = "omissions"
  val PortabilityAll       = "portability(all)"
  val PortabilityEmitted   = "portability(emitted)"
  val PortabilityInjected  = "portability(injected)"
  val Remediation          = "remediation"
  /** the two [[SubstitutionCheck]] halves. */
  val SubstitutionEmitted  = "substitution(emitted)"
  val SubstitutionDangling = "substitution(dangling)"
  val Policy               = "policy"
  val Manifest             = "manifest"
  /** references a base module's PUBLISHED port map says are not in its output. */
  val PortMapCheck         = "port-map"
  /** comments in the upstream Java that did not reach the emitted Scala (a LICENCE among them). */
  val TriviaDropped        = "trivia"

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
    SubstitutionEmitted, SubstitutionDangling, Policy, Manifest, PortMapCheck, TriviaDropped,
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
  ):
    private val memo = collection.mutable.Map.empty[SymId, String]
    private lazy val keys = cache.map(_ => TirCacheKey.forUnits(program, emitOrder))

    def sourceOf(u: Tree.ClassDef): String =
      memo.getOrElseUpdate(
        u.symbol, {
          val key = keys.flatMap(_.get(u.symbol))
          (cache, key) match
            case (Some(c), Some(k)) =>
              c.get(k) match
                case Some(hit) => hit
                case scala.None =>
                  val out = emitter.emitUnit(u)
                  c.put(k, out)
                  out
            case _ => emitter.emitUnit(u)
        },
      )

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
        "says nothing about them (CLAUDE.md §3). Fix in core/scala-emit, or record the limit in ENGINE-LIMITS.md.")
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
        "these is a §4.57 obligation, not a formatting nicety. Fix in frontend-spoon/scala-emit.")
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
