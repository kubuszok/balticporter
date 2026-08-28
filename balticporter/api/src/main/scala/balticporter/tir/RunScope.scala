package balticporter.tir

/** What the RUN knows about ITSELF, and a PHASE cannot derive from the `Program` it is handed.
  *
  * ==The two questions, and why neither is answerable from the program==
  * A dependent module's `Program` CONTAINS its base (`resolutionRoots` parses the base's Java), so
  * [[Program.owned]] — which roots its climb on `program.units`, all of them — is a
  * '''program-vs-JDK''' filter and answers `true` for every base symbol. That is exactly right for
  * what it is asked there (a rename must not rewrite the JDK), and it is not the question a phase
  * has when it is about to REWRITE a declaration: *does this run emit that declaration at all?*
  * `ENGINE-LIMITS.md` D2 records five instances of the same shape on the REPORTING side and names
  * the rewriting side as the one none of them reached. [[emits]] is that predicate, and only the run
  * can compute it — it is decided from the unit's `Origin` against the run's own source and
  * resolution roots, realpathed on both sides (CLAUDE.md §5.4), and nothing in the TIR carries it.
  *
  * The second question is the same one, asked of POLICY rather than of code. After
  * `balticporter.core.SurfaceFold` merges a base's instance of a phase with a dependent's, the
  * phase that RUNS holds both modules' tables and cannot tell them apart — and "which of these keys
  * did MY manifest contribute?" is the question that decides whether a rewrite is this module's to
  * make. [[contributed]] is the fold's own answer (`SurfaceFold.ownKeys`), handed down.
  *
  * ==Why it arrives on the BINDER==
  * `PolicyBinder` is already THE object the run hands every `PolicyBound` phase before the pipeline
  * starts, once per translation, and it already carries the `Program`. A second channel would be a
  * second thing a caller must remember, and `Pipeline.runTraced`'s own scaladoc records what
  * happens then: a phase run without it takes a silently different code path.
  *
  * ==The default is the pre-existing behaviour, exactly==
  * [[RunScope.whole]] answers "this run emits everything" and "no phase's policy is scoped", which
  * is the truth for a BASE port, for a single-module port, for a spec and for `DebugEmit` — so a
  * consumer cannot take a different code path under test than it does in a port, which is how a
  * mechanism ships untested (the same argument `TrivialSurface` carries).
  */
trait RunScope:

  /** Does this run EMIT the declarations of this top-level unit? `false` for a unit the run merely
    * resolved against — a base module's. Asked with the unit symbol, which every phase can reach by
    * climbing a symbol's owner chain. */
  def emits(unit: SymId): Boolean

  /** The shared-surface SUBJECTS the folded manifest itself contributed to this phase's effective
    * policy — `balticporter.core.SurfaceFold.ownKeys`, or, for a phase this module declares that no
    * base has a counterpart for, all of its own subjects.
    *
    * `None` means "this module declares no instance of this phase", which is the no-filter answer:
    * every key the phase holds is a base's, so every rewrite it makes is one the base's own run
    * made identically and there is nothing for this module to be held to.
    */
  def contributed(phase: String): Option[Set[String]]

  /** WHICH BACKENDS THIS MODULE IS PORTED FOR, and where it ships its own answer — the third thing
    * a phase cannot derive from its `Program` and must not restate.
    *
    * `PortManifest.targets` decides the rule list `PortabilityCheck` asks its questions from, and
    * `verdictOverrides` narrows it further. A phase that reasons about portability was taking BOTH
    * as constructor parameters of its own, defaulted to all-three-and-nothing — which agrees with
    * the manifest by CONVENTION and by nothing else: a port with `targets = ["jvm"]` reports an
    * empty `portability(emitted)` lane (no rule in the list asks about the JVM) while the phase at
    * its default computed violations against all three and could claim to drain rows from a lane
    * reading zero. Two spellings of one fact, in two files, with no comparison between them.
    *
    * It belongs here for [[emits]]'s reason exactly: it is a fact about the RUN, the run holds the
    * manifest, and the binder is already the one object every `PolicyBound` phase is handed.
    * [[RunScope.PlatformPolicy.everyPlatform]] is the default and is precisely the pre-parameterised
    * behaviour — every question the check asked before it had a target set (`CLAUDE.md` §1(b)). */
  def platform: RunScope.PlatformPolicy = RunScope.PlatformPolicy.everyPlatform

  /** Types the base port SUBSTITUTED — dropped and replaced by a hand-written injection.
    *
    * A dependent's `Program` contains its base (D2), so a detection phase like `BeanPropertyTransform`
    * or `NullaryArityTransform` sees the base's members and may auto-detect pairs on a type the base
    * REPLACED by an injected file. Those members are from the JAVA source, not from the injected Scala,
    * so a rename the detection plans is a rename the injected file did not perform — the dependent's
    * emitted code calls a property the shim never declared.
    *
    * The set is populated from the base's published port map (`PortMap.Disposition.Substituted`) and
    * is empty for a base port, a single-module port and every spec. */
  def baseSubstitutedOwners: Set[String] = Set.empty

  /** UPSTREAM member descriptors from the base's PUBLISHED PORT MAP — the set a dependent phase
    * reads to decide whether the base RETYPED a parameter (O8 dependent blast, wave 2.11).
    *
    * Each string is the `upstream` column of a member row:
    * `com.badlogic.gdx.graphics.glutils.ShaderProgram#setUniformf(UniformLocation,float)`. A
    * retyping phase checks whether any entry for the callee's method mentions its own opaque FQN,
    * which is a direct read of what the base published and not a re-derivation (CLAUDE.md §4.55). */
  def baseMemberUpstream: Set[String] = Set.empty

  /** …the SAME question asked of a MEMBER, which is what a phase actually holds.
    *
    * [[emits]] takes a top-level unit because that is the granularity the run classifies at; every
    * caller holds a declaration and has to climb the owner chain to it. Written here once because
    * the climb is not the caller's decision — it is fuel-bounded so a corrupt owner chain cannot
    * hang a phase, and a caller that wrote its own would be free to choose a different bound or to
    * stop at a nested type, which answers `emits` about a symbol that is not a unit at all. */
  final def emitsSymbol(program: Program, id: SymId): Boolean =
    emits(RunScope.unitOf(program, id))

object RunScope:

  /** the TOP-LEVEL unit a symbol belongs to. Fuel-bounded for [[Program.owned]]'s reason: a cycle
    * in an owner chain must not hang a run, and a truncated climb answers about a symbol that is
    * not a unit — which [[RunScope.whole]] accepts and a real scope refuses, the direction that
    * does not silently widen a rewrite. */
  private[tir] def unitOf(p: Program, id: SymId, fuel: Int = 64): SymId =
    p.symbolOf(id) match
      case Some(s) if s.owner != SymId.None && fuel > 0 => unitOf(p, s.owner, fuel - 1)
      case _                                            => id


  /** WHAT THIS MODULE'S MANIFEST SAYS ABOUT PLATFORMS — see [[RunScope.platform]].
    *
    * A record and not two members, because the two are asked TOGETHER and always through
    * `PortabilityCheck.rulesFor(targets, overrides)`: a phase that read one of them and not the
    * other would ask a question the run does not report on, which is the same disagreement one field
    * down.
    *
    * @param targets          the backends this module is ported for (`PortManifest.targets`).
    * @param verdictOverrides the port's own answers where it disagrees with the catalog's
    *                         RECOMMENDATION — never with its availability. */
  final case class PlatformPolicy(
      targets: Set[balticporter.catalog.Platform],
      verdictOverrides: Map[balticporter.catalog.DiffId,
                            Map[balticporter.catalog.Platform, balticporter.catalog.Verdict]] = Map.empty,
  )

  object PlatformPolicy:
    /** every question the portability rules asked before a target set existed — the §1(b) default,
      * and the answer for a run with no manifest, a spec and `DebugEmit`. */
    val everyPlatform: PlatformPolicy =
      PlatformPolicy(balticporter.catalog.Platform.values.toSet, Map.empty)

  /** the whole program is this run's, and no phase's policy is scoped — the default everywhere. */
  val whole: RunScope = new RunScope:
    def emits(unit: SymId): Boolean                     = true
    def contributed(phase: String): Option[Set[String]] = scala.None

  /** @param emitted       the top-level unit symbols this run converts.
    * @param own            phase name → the subjects THIS manifest contributed to that phase's policy.
    * @param platform       the manifest's own platform declaration, defaulted to the pre-parameterised
    *                       answer so every existing construction keeps its behaviour exactly.
    * @param substituted    upstream FQNs of types the base SUBSTITUTED — dropped and replaced by an
    *                       injected file. Detection phases skip these owners so they do not rename
    *                       members the injected file never renamed (D14, §1.5). Empty for a base port. */
  def of(emitted: Set[SymId], own: Map[String, Set[String]],
         platform: PlatformPolicy = PlatformPolicy.everyPlatform,
         substituted: Set[String] = Set.empty,
         memberUpstream: Set[String] = Set.empty): RunScope =
    val p = platform
    val s = substituted
    val mu = memberUpstream
    new RunScope:
      def emits(unit: SymId): Boolean                     = emitted(unit)
      def contributed(phase: String): Option[Set[String]] = own.get(phase)
      override def platform: PlatformPolicy               = p
      override def baseSubstitutedOwners: Set[String]     = s
      override def baseMemberUpstream: Set[String]        = mu
