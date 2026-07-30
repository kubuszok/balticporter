package balticporter.core

import balticporter.tir.Phase

import java.nio.file.Path

/** The porting policy of ONE module, as a value a DEPENDENT module imports and extends.
  *
  * ==The problem this exists for==
  * A library is rarely one module. An extension port (a plugin, an add-on, the library's own test
  * suite) references the base module's types — but its frontend can only parse JAVA, so it resolves
  * against the base's *upstream* sources through `FrontendConfig.resolutionRoots`, never against
  * the Scala the base port emitted. The base's transforms changed those emitted signatures:
  * collections were retyped, members were dropped, a namespace was renamed, a shim type was
  * substituted in. Nothing in the pipeline connects the two runs, so the only way the extension
  * agrees with the base is that somebody copied the base's configuration correctly and kept copying
  * it correctly. That is not a mechanism; it is a habit, and it fails one module at a time.
  *
  * A manifest makes the shared surface a VALUE. The dependent writes
  * {{{ base.extendedBy(PortManifest(name = "…", surface = List(new MyOwnPhase))) }}}
  * and inherits the base's drops, renames and surface phases by construction — and
  * [[ManifestAgreement]] verifies the agreement anyway, because a consumer is free to write the
  * dependent's policy out longhand (which is what every port does today) and a check that only
  * works when you used the convenience is a check that does not work.
  *
  * ==This is a VALUE, not a DSL==
  * Everything here is ordinary Scala the consumer's compiler type-checks: `Set[String]`,
  * `Map[String, String]`, `List[Phase]`. A configuration language would move the policy OUT of the
  * consumer's repository and out of reach of its compiler, which is the opposite of what CLAUDE.md
  * §1 asks for. The composition operator is [[extendedBy]] and there is nothing else to learn.
  *
  * ==MUST agree vs MAY differ — the line, and why it is drawn there==
  * The fields of this class are precisely the things a dependent module must NOT decide for itself.
  * Everything a [[balticporter.runner.PortRun]] takes that is NOT here is free to differ:
  *
  *   - '''MUST agree (here)''' — [[dropTypes]], [[dropMethods]], [[packageRenames]], [[surface]].
  *     Each one changes the SHAPE of the shared surface as the dependent will compile against it. A
  *     type the base does not translate mechanically must not be translated mechanically here
  *     either, or this port emits references to a class the base never wrote. A method the base
  *     dropped must be dropped here, or this port emits a call to a member that does not exist. A
  *     namespace the base moved must be moved identically, or this port's `import`s name nothing. A
  *     signature-affecting phase the base ran must run here, or this port re-derives the base's
  *     signatures differently from the base itself — the case that motivated the whole item, since
  *     retyping `java.util.List` to a `Buffer` in one module and not the other produces two ports
  *     that each compile alone and cannot compile together.
  *
  *   - '''MAY differ (not here)''' — the source set, the source root, the file list, the output
  *     directory, the provenance header, `runtimeMode`, `supportSources`, the generated sbt
  *     project, determinism, the action cache, leniency. None of these is observable in a
  *     signature; all of them are properties of THIS module's build. `runtimeMode` is the clearest
  *     case: the two source sets of one module deliberately disagree, because vendoring the support
  *     types twice defines every one of them twice.
  *
  *   - '''[[inject]] is the interesting one, and it MAY differ ON PURPOSE.''' A drop and its
  *     replacement look like one decision and are two. The DROP is an observation about the shared
  *     API — "this type is not mechanically translatable" — and every port that sees the type must
  *     agree with it. The INJECTION is a build artefact: exactly one module ships the replacement
  *     file, and a dependent that copied the base's `inject` would emit a second definition of the
  *     same FQN into its own source set and fail to compile. So [[inject]] is declared per manifest
  *     and is NOT inherited by [[extendedBy]], while the drops beside it are. This asymmetry is the
  *     single most important thing on this page to get right, and it is why `inject` is a field of
  *     the manifest rather than being folded into [[substitutions]] wholesale.
  *
  * ==What [[governs]] is for==
  * A namespace claim, used only where a check genuinely needs prefixes — the package-rename
  * comparison. Substitution agreement does NOT use it: that check works from unit ORIGINS, so it is
  * exact even when the two modules interleave their packages (a library's own test suite typically
  * declares its suites in the very packages it tests, and no prefix can separate those). Leave it
  * empty when the layout is interleaved; that costs only the rename-override diagnosis.
  */
final case class PortManifest(
    /** for reports — the module this policy belongs to. */
    name: String,
    /** FQN prefixes this module claims. Optional; see the class doc. */
    governs: Set[String] = Set.empty,
    /** types NOT translated mechanically. Inherited by dependents. */
    dropTypes: Set[String] = Set.empty,
    /** methods NOT translated mechanically (`owner#name`, `owner#name(P1,P2)`). Inherited. */
    dropMethods: Set[String] = Set.empty,
    /** upstream prefix → port prefix. Inherited. */
    packageRenames: Map[String, String] = Map.empty,
    /** the phases that shape EMITTED SIGNATURES. Inherited, and placed before a dependent's own. */
    surface: List[Phase] = Nil,
    /** ready-made Scala this module ships. NOT inherited — see the class doc. */
    inject: List[Path] = Nil,
    /** the modules this one is a dependent OF, nearest last. */
    bases: List[PortManifest] = Nil,
    /** Does this manifest INHERIT its [[bases]]' policy, or merely declare that it must AGREE
      * with them?
      *
      * `true` (from [[extendedBy]]) is the normal case and the one to reach for: the shared surface
      * arrives as a value, and drift in it is not merely detected but unrepresentable.
      *
      * `false` (from [[mirroring]]) is for a module that states its policy IN FULL and wants that
      * statement checked against a base — a port being migrated onto manifests, a port whose base
      * value it cannot import because the base lives in a repository it only mirrors, or a port
      * whose author wants the policy legible in one file. It is strictly weaker: agreement is now
      * verified rather than guaranteed, and every finding [[ManifestAgreement]] can produce becomes
      * reachable, because there is a second, independent statement of the same policy to disagree
      * with. That is the whole reason the flag exists — with inheritance alone, `MissingDrop` and
      * `SurfaceMissing` could never fire, and a check that cannot fire is not a check.
      */
    inherit: Boolean = true,
) extends PolicySource:

  /** THE composition operation: `base.extendedBy(dependent)`.
    *
    * Reads in dependency order and returns the DEPENDENT, because that is the value the dependent's
    * `PortRun` needs. Repeated application composes: `a.extendedBy(b).extendedBy(c)` makes `c` a
    * dependent of `b` which is a dependent of `a`, and `c`'s effective policy is the union of all
    * three — bases first, so the nearest declaration wins a conflict and the check reports it.
    */
  def extendedBy(dependent: PortManifest): PortManifest =
    dependent.copy(
      bases   = (bases.filterNot(dependent.bases.contains) ++ (this :: dependent.bases)).distinct,
      inherit = true,
    )

  /** declare a base to be CHECKED against without inheriting its policy — see [[inherit]]. */
  def mirroring(base: PortManifest*): PortManifest =
    copy(bases = (bases ++ base).distinct, inherit = false)

  /** every manifest this one declares a dependency on, transitively, furthest base first.
    * Independent of [[inherit]]: what is checked is always the whole declared chain. */
  lazy val baseChain: List[PortManifest] =
    bases.flatMap(b => b.baseChain :+ b).distinct

  /** the manifests whose policy this one APPLIES, in precedence order. */
  lazy val policyChain: List[PortManifest] = if inherit then baseChain :+ this else List(this)

  def effectiveDropTypes: Set[String]   = policyChain.flatMap(_.dropTypes).toSet
  def effectiveDropMethods: Set[String] = policyChain.flatMap(_.dropMethods).toSet

  /** bases first, this manifest last — so a dependent's declaration wins, and
    * [[ManifestAgreement]] reports the override rather than the engine hiding it. */
  def effectivePackageRenames: Map[String, String] =
    policyChain.foldLeft(Map.empty[String, String])((acc, m) => acc ++ m.packageRenames)

  /** base phases first, then this module's own. Deduplicated by IDENTITY, so inheriting one
    * manifest through two paths runs its phases once, while two distinct instances of the same
    * phase class stay distinct — that pair is drift, and [[ManifestAgreement]] names it. */
  def effectiveSurface: List[Phase] =
    policyChain.flatMap(_.surface).foldLeft(List.empty[Phase]) { (acc, p) =>
      if acc.exists(_ eq p) then acc else acc :+ p
    }

  /** The [[Substitutions]] value this run hands the frontend: every drop in the chain, and only
    * THIS module's injections.
    *
    * A `lazy val` rather than a `def`, and it matters: `Substitutions` accumulates which of its
    * keys actually fired, and the report has to be read off the same instance the frontend was
    * given. Two calls returning two equal-but-distinct values would produce an empty tally.
    */
  lazy val substitutions: Substitutions =
    Substitutions(effectiveDropTypes, effectiveDropMethods, inject)

  /** What THIS module's own drops did — never an inherited key.
    *
    * A §1(b) policy finding says "fix this key in the library's manifest". An inherited key lives in
    * the BASE's manifest, so reporting it here would tell every dependent module about a mistake
    * none of them can fix, and one bad key in a library with eighteen modules would be eighteen
    * findings. The inherited half is not unchecked, though — it is checked more precisely, as
    * [[ManifestAgreement.Kind.InheritedKeyNeverFired]], which says which BASE the key came from.
    */
  def policyReport: PolicyReport =
    val own = ownDrops.dropTypes ++ ownDrops.dropMethods
    PolicyReport(substitutions.policyReport.findings.filter(f => own.contains(f.key)))

  /** base drop keys that never fired during this run. */
  def inheritedKeysNeverFired: Map[String, Set[String]] =
    val fired = substitutions.matched
    baseChain.map(b => b.name -> ((b.dropTypes ++ b.dropMethods) -- fired)).filter(_._2.nonEmpty).toMap

  /** the drops THIS module is answerable for — its own, minus anything a base also declares.
    *
    * A base's drop obliges this module to MODEL the type as substituted; it does not oblige it to
    * SHIP the replacement, and exactly one module must. Subtracting the bases' keys is what lets a
    * module restate the shared policy in full ([[mirroring]]) without being asked to replace types
    * whose replacement its base already emits. */
  def ownDrops: Substitutions =
    val baseKeys = baseChain.flatMap(b => b.dropTypes ++ b.dropMethods).toSet
    Substitutions(dropTypes -- baseKeys, dropMethods -- baseKeys, inject)

  /** the same manifest with every phase in the chain removed — for a structural-only re-emit. */
  def withoutSurface: PortManifest =
    copy(surface = Nil, bases = bases.map(_.withoutSurface))

  /** `fqn` after this manifest's effective renames, longest prefix first — the name a dependent
    * module will see the type by. Cut only at a separator, exactly as
    * `PackageRenameTransform` cuts it, or `com.foo` would cover `com.foobar`. */
  def renamed(fqn: String): String =
    PortManifest.longestPrefix(fqn, effectivePackageRenames.keySet) match
      case Some(from) => effectivePackageRenames(from) + fqn.substring(from.length)
      case None       => fqn

  /** does this manifest claim `fqn`? False for an empty [[governs]] — no claim, not "everything". */
  def claims(fqn: String): Boolean = governs.exists(PortManifest.covers(fqn, _))

object PortManifest:

  /** `.` separates packages and the top-level type, `$` precedes a nested type, `#` a member — the
    * same three boundaries `PackageRenameTransform` cuts at, and for the same reason. */
  def isBoundary(c: Char): Boolean = c == '.' || c == '$' || c == '#'

  def covers(fullName: String, prefix: String): Boolean =
    prefix.nonEmpty && fullName.startsWith(prefix) &&
      (fullName.length == prefix.length || isBoundary(fullName.charAt(prefix.length)))

  def longestPrefix(fullName: String, prefixes: Set[String]): Option[String] =
    prefixes.filter(covers(fullName, _)).maxByOption(_.length)

  /** A phase's SIGNATURE-AFFECTING identity, for comparing two modules' pipelines.
    *
    * `name` alone answers the question that actually breaks a build — "did the dependent run this
    * phase at all?" — and it is available on every `Phase` without the engine reaching inside one.
    * A phase whose POLICY can differ between two equally-named instances implements
    * [[SurfacePolicy]] and contributes its policy too.
    *
    * Be clear about the limit: a parameterised phase that does NOT implement [[SurfacePolicy]] is
    * compared by name only, so two instances configured differently compare EQUAL. That is a real
    * blind spot and it is opt-in by design — the alternative is reflection over private fields,
    * which would compare things that are not policy and break on every refactor.
    */
  def fingerprint(p: Phase): String = p match
    case s: SurfacePolicy => s"${p.name}[${s.surfaceFingerprint}]"
    case _                => p.name

/** Implemented by a phase whose CONFIGURATION changes emitted signatures, so that two modules'
  * instances of it can be compared. See [[PortManifest.fingerprint]] for what not implementing it
  * costs.
  *
  * The string must be a pure, stable, order-independent rendering of the phase's policy — sort
  * anything set-like, or two ports that agree will compare unequal on a `HashSet`'s iteration
  * order and the check becomes noise.
  */
trait SurfacePolicy:
  def surfaceFingerprint: String
