package balticporter.runner

import balticporter.core.*
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.sbtgen.SbtGen
import balticporter.tir.{CheckReport, DebugFlags, OmissionCheck, Phase, Pipeline, PortabilityCheck, Program, RewriteTrace, SymId, Tree}
import balticporter.transform.PackageRenameTransform

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
    if mismatches.isEmpty then say("signature check: all call sites agree with their declarations")
    else
      say(s"signature check: ${mismatches.size} site(s) disagree with their declaration")
      say(PortReport.Kind.Signature.classification)
      mismatches.take(20).foreach(m => println("  " + m.render))

    val omissions = OmissionCheck.check(program)
    say(s"OMISSIONS (emitted code silently loses these): ${omissions.size}")
    if omissions.nonEmpty then say(PortReport.Kind.Omission.classification)
    println(OmissionCheck.summary(omissions))

    // ---- cross-port composition: does the shared surface agree with the module that emits it? ----
    // Runs on EVERY port. On a base port `shared` is empty and the check is a no-op by arithmetic
    // rather than by a branch — the same discipline as an empty policy making a phase a no-op.
    val agreement = ManifestAgreement.check(manifest, sharedSurface(program, translated.foreign), foreignRoots)
    CheckReport.record(PortRun.Manifest, agreement.map { f =>
      CheckReport.Finding(PortRun.Manifest, f.kind.toString, f.subject, "", 0, f.render)
    })
    val bases = manifest.toList.flatMap(_.baseChain).map(_.name)
    say(s"MANIFEST agreement (${if bases.isEmpty then "no base module declared" else s"base(s): ${bases.mkString(", ")}"}, " +
      s"${translated.foreign.size} shared type(s)): ${agreement.size} disagreement(s)")
    if agreement.nonEmpty then
      say(PortReport.Kind.Manifest.classification)
      agreement.take(40).foreach(f => println("  " + f.render))

    val droppedIds  = program.symbols.all.collect { case s if policySubs.dropsType(s.fullName) => s.id }.toSet
    val portability = PortabilityCheck.inEmittedCode(program, PortabilityCheck.check(program), droppedIds)
    say(s"PORTABILITY (Scala.js/Native): ${portability.size} site(s) on JVM-only APIs in EMITTED code")
    if portability.nonEmpty then say(PortReport.Kind.Portability.classification)
    println(PortabilityCheck.summary(portability))

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
    translated.emitOrder.foreach { u =>
      val full = program.symbolOf(u.symbol).map(_.fullName).getOrElse("Unit")
      // Substitutions.dropTypes: PARSED (so every reference to it still resolves) but NOT emitted —
      // the injected replacement supplies this FQN instead.
      if policySubs.dropsType(full) then dropped += 1
      else
        write(outDir.resolve(full.replace('.', '/') + ".scala"), translated.sourceOf(u))
        written += 1
    }
    // Support types a phase RETYPED code onto. Two feeds, one rule: what the phases DECLARE
    // (RequiresRuntime → RuntimePlan) and what a phase that cannot declare it hands over.
    written += plan.writeSources(outDir)
    supportSources.foreach { (fqn, src) => write(outDir.resolve(fqn.replace('.', '/') + ".scala"), src); written += 1 }

    // CHECK 1 — before injection, so a file at a dropped type's path can only be the emitter's.
    val leaked = record(PortRun.SubstitutionEmitted, SubstitutionCheck.emittedDroppedTypes(outDir, policySubs))

    // ---- injection: ready-made Scala copied verbatim (survives the wipe above) ----
    // Register the injected-portability check even with nothing to inject: it is an APPEND-per-file
    // check, so with no files it would never name itself and `counts.tsv` could not tell "found
    // nothing" from "never ran" — the one distinction the persistence layer exists to keep.
    CheckReport.append("portability(injected)", Nil)
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
    if injectedViolations.isEmpty then say("PORTABILITY of injected replacements: clean")
    else
      say(s"PORTABILITY of injected replacements: ${injectedViolations.size} finding(s)")
      say(PortReport.Kind.InjectedPortability.classification)
      injectedViolations.foreach(v => println("  " + v))

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
    val program = Pipeline.run(SpoonTir.fromTypes(types, policySubs), effectivePhases)
    val plan    = RuntimePlan.of(effectivePhases, runtimeMode)
    // `externalConcrete` is DERIVED, never passed in: a caller who has to remember it is a caller
    // who forgets it, and forgetting it silently disables diamond-conflict detection against an
    // injected parent.
    val emitter = new TirEmitter(program, plan.concreteMembers, provenance)
    val (mine, theirs) = partitionUnits(program)
    PortRun.Translated(program, plan, emitter, mine, theirs, cache.map(new ActionCache(_, true)))

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

  /** the two [[SubstitutionCheck]] halves, as they appear in `counts.tsv`. */
  val SubstitutionEmitted  = "substitution(emitted)"
  val SubstitutionDangling = "substitution(dangling)"
  val Policy               = "policy"
  val Manifest             = "manifest"

  /** Every check a run MUST have recorded by the time it finishes. Named rather than derived,
    * because the property being asserted is "the orchestrator invoked all of them" — deriving the
    * list from what was invoked would assert nothing. Adding a check to `PortRun` means adding it
    * here, and forgetting to fails the next run rather than shipping a silently narrower report. */
  val RequiredChecks: Set[String] = Set(
    "signature", "omissions", "portability(all)", "portability(emitted)", "portability(injected)",
    SubstitutionEmitted, SubstitutionDangling, Policy, Manifest,
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
    injectedPortability: List[String],
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
