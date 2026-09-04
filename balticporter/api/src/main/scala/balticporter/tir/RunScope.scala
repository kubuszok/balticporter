package balticporter.tir

/** What the RUN knows about ITSELF, and a PHASE cannot derive from the `Program` it is handed —
  * does this run EMIT a declaration at all ([[emits]], from the unit's `Origin` against the run's
  * roots, realpathed — CLAUDE.md §5.4, `ENGINE-LIMITS.md` D2), and which policy keys did MY
  * manifest contribute after a `SurfaceFold` merge ([[contributed]]). Arrives on the `PolicyBinder`.
  * [[RunScope.whole]] (default) is the pre-existing behaviour: everything is this run's own. */
trait RunScope:

  /** Does this run EMIT the declarations of this top-level unit? `false` for a unit the run merely
    * resolved against — a base module's. Asked with the unit symbol, which every phase can reach by
    * climbing a symbol's owner chain. */
  def emits(unit: SymId): Boolean

  /** The shared-surface SUBJECTS the folded manifest itself contributed to this phase's effective
    * policy — `SurfaceFold.ownKeys`, or, for a phase no base has a counterpart for, all of its own
    * subjects. `None` means "no instance of this phase here": every key is a base's, and every
    * rewrite it makes is one the base's own run made identically. */
  def contributed(phase: String): Option[Set[String]]

  /** WHICH BACKENDS THIS MODULE IS PORTED FOR, and where it ships its own answer — a fact about the
    * RUN a phase must not restate as its own constructor parameter (measured: a phase defaulting to
    * all-three disagreed silently with a port's `targets = ["jvm"]`). Belongs on the binder for
    * [[emits]]'s reason. [[RunScope.PlatformPolicy.everyPlatform]] is the pre-parameterised default. */
  def platform: RunScope.PlatformPolicy = RunScope.PlatformPolicy.everyPlatform

  /** Types the base port SUBSTITUTED — dropped and replaced by a hand-written injection. A
    * dependent's `Program` contains its base (D2), so a detection phase may auto-detect a pair on a
    * type the base REPLACED, from JAVA members the injected Scala shim never declared. Populated
    * from the base's published port map (`PortMap.Disposition.Substituted`); empty for a base port. */
  def baseSubstitutedOwners: Set[String] = Set.empty

  /** Types THIS RUN drops and replaces with an injected file. A rewrite dispatcher resolving a
    * call's table off the callee's owner (rather than the receiver's own type) must not fire on
    * one of these — the injected surface has its own API. Subplan item 2. */
  def ownSubstitutedOwners: Set[String] = Set.empty

  /** UPSTREAM member descriptors from the base's PUBLISHED PORT MAP — the set a dependent phase
    * reads to decide whether the base RETYPED a parameter (O8 dependent blast, wave 2.11). Each
    * string is a member row's `upstream` column; a retyping phase checks it against the callee's
    * own opaque FQN, a direct read rather than a re-derivation (CLAUDE.md §4.55). */
  def baseMemberUpstream: Set[String] = Set.empty

  /** …the SAME question asked of a MEMBER, which is what a phase actually holds. [[emits]] takes a
    * top-level unit (the run's classification granularity); every caller climbs the owner chain to
    * it. Written once here since the climb is fuel-bounded and a caller's own would be free to
    * choose a different bound or stop short of a unit. */
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


  /** WHAT THIS MODULE'S MANIFEST SAYS ABOUT PLATFORMS — see [[RunScope.platform]]. A record, not
    * two members, since both are asked TOGETHER through `PortabilityCheck.rulesFor(targets,
    * overrides)`. @param targets backends this module is ported for @param verdictOverrides the
    * port's own answers where it disagrees with the catalog's RECOMMENDATION, never its availability. */
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

  /** @param emitted the top-level unit symbols this run converts @param own phase name → subjects
    * THIS manifest contributed @param platform the manifest's platform declaration, defaulted to
    * the pre-parameterised answer @param substituted upstream FQNs of types the base SUBSTITUTED —
    * detection phases skip these owners so they don't rename what the injected file never did (D14)
    * @param ownSubstituted see [[RunScope.ownSubstitutedOwners]]. Empty for a run with no drops. */
  def of(emitted: Set[SymId], own: Map[String, Set[String]],
         platform: PlatformPolicy = PlatformPolicy.everyPlatform,
         substituted: Set[String] = Set.empty,
         memberUpstream: Set[String] = Set.empty,
         ownSubstituted: Set[String] = Set.empty): RunScope =
    val p = platform
    val s = substituted
    val mu = memberUpstream
    val os = ownSubstituted
    new RunScope:
      def emits(unit: SymId): Boolean                     = emitted(unit)
      def contributed(phase: String): Option[Set[String]] = own.get(phase)
      override def platform: PlatformPolicy               = p
      override def baseSubstitutedOwners: Set[String]     = s
      override def baseMemberUpstream: Set[String]        = mu
      override def ownSubstitutedOwners: Set[String]      = os
