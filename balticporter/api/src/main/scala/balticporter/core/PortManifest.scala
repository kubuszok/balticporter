package balticporter.core

import balticporter.catalog.{ DiffId, Platform, Verdict }
import balticporter.tir.{ MemberKey, Phase, RuleScope }

import java.nio.file.Path

/** The porting policy of one module, imported and extended by a dependent (`base.extendedBy(...)`) — ordinary Scala, not a DSL. Must agree: [[dropTypes]], [[dropMethods]], [[packageRenames]],
  * [[surface]], [[resolutions]] (shape of shared surface). May differ: build properties, and [[inject]]/[[serviceProviders]] (build artefacts, exactly one module ships each). [[governs]] is the
  * namespace claim gating the intrusion screen — empty disables it.
  */

/** One upstream resource root and the files under it this module ships — copied verbatim at the upstream path the emitted code already names. Complement of [[PortManifest.serviceProviders]] (SPI
  * descriptors are rewritten; everything else is bytes). A declaration, not a scan. Empty is the no-op; a declared missing file is fatal. @param root the upstream root @param files classpath paths
  * under it, `/`-separated.
  */
final case class ResourceTree(root: Path, files: List[String])

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
  /** upstream TYPE FQN → its name in the port, as a whole upstream FQN or a bare simple name. Inherited, for exactly the reason [[packageRenames]] is: a type whose emitted name differs between two
    * modules gives the dependent references the base never wrote.
    */
  typeRenames: Map[String, String] = Map.empty,
  /** upstream TYPE FQN → a sub-package to nest it under, in place. Inherited. */
  subPackages: Map[String, String] = Map.empty,
  /** NESTED type FQNs (`p.Outer$Inner`) promoted to top level. Inherited. */
  flattenNestedTypes: Set[String] = Set.empty,
  /** upstream TYPE FQNs whose per-type move crosses an access boundary Java gave them, and which this port DECLARES deliberate — see `PackageRenameTransform`'s boundary rule. Inherited, because the
    * boundary it moves is the SHARED one: a dependent that inherited the rename and not the declaration would refuse a move its base performed.
    */
  allowPackageSplit: Set[String] = Set.empty,
  /** the phases that shape EMITTED SIGNATURES. Inherited, and placed before a dependent's own. */
  surface: List[Phase] = Nil,
  /** Per-location remedy selection — `owner#member` → the id of a remedy a phase or check offered there ([[balticporter.tir.Remedy]]), for a decision one word long rather than a library-specific
    * rule. Key is `MemberKey` in the upstream namespace; value a globally-unique remedy id. Must-agree surface — [[effectiveResolutions]] unions bases-first; the same key with two values, or a
    * dependent key naming a base-emitted declaration, is a fatal finding.
    */
  resolutions: Map[String, String] = Map.empty,
  /** ready-made Scala this module ships. NOT inherited — see the class doc. */
  inject: List[Path] = Nil,
  /** ready-made Scala per platform row — a name from [[PortManifest.PlatformRows]] → roots copied to `src_managed/<row>/scala`, which only that row compiles; `inject` is the shared row. A hand port's
    * `scalajvm`/`scaladesktop`/`scalajs`/`scalanative` layers land here, and so does a replacement whose ANSWER differs per platform — a JVM arm beside a refusal the other two rows carry. Not
    * inherited (a build artefact, like `inject`).
    */
  platformDirs: Map[String, List[Path]] = Map.empty,
  /** Scala the consumer's own build compiles from its own sources — typically where the replacement of a dropped type lives. Read like [[inject]] (the replacement's members, factories and property
    * spellings, and "something stands at this name") but never copied into `src_managed`, since a second copy would drift from the one that compiles. Not inherited (a build fact, like `inject`);
    * empty is the no-op.
    */
  providedSources: List[Path] = Nil,
  /** Upstream `META-INF/services/<interface FQN>` files this module ships — the SPI half of the deliverable no phase can carry. Missing it means `ServiceLoader.load` finds zero providers, silently,
    * with no compile error or check count. A declaration, not a scan: which resources are descriptors is per-library knowledge. Not inherited — a build artefact, exactly one module ships each; the
    * drops that affect it are inherited.
    */
  serviceProviders: List[Path] = Nil,
  /** The rest of this module's classpath resources — copied verbatim at the upstream paths the emitted code already names (see [[ResourceTree]]). Missing one throws at first use in the consumer's
    * build, with no compile error or check count here. Not inherited, for [[serviceProviders]]'s reason — a resource lands at one classpath path. Empty is the default and the no-op.
    */
  resources: List[ResourceTree] = Nil,
  /** the modules this one is a dependent OF, nearest last. */
  bases: List[PortManifest] = Nil,
  /** Extra directories to look for a base module's `port-map.tsv` under, nearest first — the run's own report tree is always searched first and cannot be shadowed. Not inherited: a base's map decides
    * emitted text, so which maps a run discovers is part of that run's identity. Empty is the ordinary case; a cross-repository consumer is who this exists for.
    */
  baseReports: List[Path] = Nil,
  /** WHICH BACKENDS this module is ported FOR — the parameter `PortabilityCheck` runs by. Default is ALL platforms (today's behaviour before this field existed); narrowing is the port's own decision,
    * in `decisions.tsv`. NOT inherited (decides which findings are reported, not emitted signatures) — but a dependent may only target FEWER platforms than its base, never more:
    * `ManifestAgreement.Kind.TargetWidening`.
    */
  targets: Set[Platform] = Platform.values.toSet,
  /** WHERE THIS PORT DISAGREES with the catalog's recommendation, per platform. An [[balticporter.catalog.ApiRow]]'s `by` is a FACT nothing here may contradict; its `verdict` is a recommended default
    * this overrides (e.g. "this port ships its own shim"), recorded as a `Decision` with `Reason.Configured` naming this entry. NOT inherited, for `targets`' reason: it decides which findings this
    * module is told about.
    */
  verdictOverrides: Map[DiffId, Map[Platform, Verdict]] = Map.empty,
  /** THE ARTIFACTS this module's build adds. Not inherited — a build fact, exactly one module's build file names each coordinate. Answers a `Verdict.Depend`; `SbtGen` writes these into
    * `libraryDependencies` and `dependency-coverage` reports every requirement no entry covers. Empty is the default and the honest state of an unaudited port.
    */
  dependencies: List[balticporter.catalog.ArtifactDep] = Nil,
  /** External members parenless on some platforms — exact FQNs. The frontend reads JVM class files (always `()`), but a JS/Native platform shim may declare the same member parenless, so the emitted
    * `x.getFoo()` fails there (`E050`). Listing a member here emits calls to it without parens on every platform — legal on the JVM too. Mechanism is universal, membership is per-library. Not
    * inherited (a classpath fact); no `SurfacePolicy` (calls only).
    */
  externalParenless: Set[String] = Set.empty,
  /** The reference hand port for this module — the parameter for `ApiParityCheck`.
    *
    * Not inherited. A hand port is a fact about this module's destination, not the shared surface. A dependent does not inherit its base's parity reference — the two have different hand-port trees.
    * Empty / absent = the check is a no-op and records nothing.
    */
  parity: Option[ParityRef] = None,
  /** Does this manifest INHERIT its [[bases]]' policy ([[extendedBy]], the normal case — drift becomes unrepresentable), or merely declare that it must AGREE with them ([[mirroring]])? The latter is
    * for a module that states its policy in full and wants it checked against a base it cannot import — strictly weaker, since agreement is now verified rather than guaranteed, but it is what lets
    * `MissingDrop`/`SurfaceMissing` ever fire at all.
    */
  inherit: Boolean = true,
  /** A frozen derived-policy file (same TSV format `derived-policy.tsv` writes). When set, phases that derive spelling policy read it from this file instead of requiring a reference tree. When BOTH
    * this and a `parity` reference are given, the run derives from the reference and fails if the result differs from this file (a stale frozen file). Not inherited: like `parity`, a fact about this
    * module's build, not the shared surface. Absent = derive from the reference as before.
    */
  frozenDerivedPolicy: Option[Path] = None
):

  /** THE composition operation: `base.extendedBy(dependent)`. Returns the DEPENDENT with the union of the chain's policy — bases first, so the nearest declaration wins a conflict and the check
    * reports it. Repeated application composes: `a.extendedBy(b).extendedBy(c)`.
    */
  def extendedBy(dependent: PortManifest): PortManifest =
    dependent.copy(
      bases = (bases.filterNot(dependent.bases.contains) ++ (this :: dependent.bases)).distinct,
      inherit = true
    )

  /** declare a base to be CHECKED against without inheriting its policy — see [[inherit]]. */
  def mirroring(base: PortManifest*): PortManifest =
    copy(bases = (bases ++ base).distinct, inherit = false)

  /** every manifest this one declares a dependency on, transitively, furthest base first. Independent of [[inherit]]: what is checked is always the whole declared chain.
    */
  lazy val baseChain: List[PortManifest] =
    bases.flatMap(b => b.baseChain :+ b).distinct

  /** the manifests whose policy this one APPLIES, in precedence order. */
  lazy val policyChain: List[PortManifest] = if inherit then baseChain :+ this else List(this)

  def effectiveDropTypes:   Set[String] = policyChain.flatMap(_.dropTypes).toSet
  def effectiveDropMethods: Set[String] = policyChain.flatMap(_.dropMethods).toSet

  /** bases first, this manifest last — so a dependent's declaration wins, and [[ManifestAgreement]] reports the override rather than the engine hiding it.
    */
  def effectivePackageRenames: Map[String, String] =
    policyChain.foldLeft(Map.empty[String, String])((acc, m) => acc ++ m.packageRenames)

  /** the per-TYPE half of the rename policy, composed exactly as [[effectivePackageRenames]] is — bases first, so a dependent's declaration wins and [[ManifestAgreement]] reports the override rather
    * than the engine hiding it.
    */
  def effectiveTypeRenames: Map[String, String] =
    policyChain.foldLeft(Map.empty[String, String])((acc, m) => acc ++ m.typeRenames)

  def effectiveSubPackages: Map[String, String] =
    policyChain.foldLeft(Map.empty[String, String])((acc, m) => acc ++ m.subPackages)

  def effectiveFlattenNestedTypes: Set[String] = policyChain.flatMap(_.flattenNestedTypes).toSet

  /** every per-location remedy SELECTION in the chain, bases first — so the nearest declaration wins a conflict and [[ManifestAgreement.Kind.ResolutionDivergence]] reports it rather than the engine
    * hiding it. Composed exactly as [[effectivePackageRenames]] is, for the same reason.
    */
  def effectiveResolutions: Map[String, String] =
    policyChain.foldLeft(Map.empty[String, String])((acc, m) => acc ++ m.resolutions)

  /** …and the KEYS TWO MANIFESTS IN THIS CHAIN ANSWER DIFFERENTLY, which the union above cannot show. One row per contested key: the key, and every (manifest name, remedy id) pair that claims it. The
    * union stays the effective policy; disagreement is a FATAL finding beside it.
    */

  /** …and it compares what two keys NAME, never the two strings — `Foo#bar` and `Foo#bar(int)` are two legal spellings of one selection, so string grouping missed real disagreements.
    * `MemberKey.mayNameSame` groups by overload set; each claimant carries the key it wrote.
    */
  def resolutionConflicts: List[(String, List[(String, String, String)])] =
    policyChain
      .flatMap(m => m.resolutions.toList.map((k, v) => (k, m.name, v)))
      .groupBy((k, _, _) => MemberKey.overloadSetOf(k))
      .toList
      .flatMap { (subject, rows) =>
        val contested = rows.filter { (k, _, id) =>
          rows.exists((k2, _, id2) => id2 != id && MemberKey.mayNameSame(k, k2))
        }
        if contested.isEmpty then Nil
        else List(subject -> contested.map((k, who, id) => (who, k, id)).distinct.sorted)
      }
      .sortBy(_._1)

  /** WHAT THIS MODULE'S SHARED SURFACE IS FINGERPRINTED FROM — the effective surface phases, and every per-location selection beside them, since a resolution also decides emitted text a dependent
    * compiles against. One derivation because two would drift — used by both this module's own fingerprint and a base's, through the same method.
    */
  def surfaceDigestInputs: List[String] =
    effectiveSurface.map(PortManifest.fingerprint) ++
      effectiveResolutions.toList.sorted.map((k, v) => s"resolution[$k=$v]")

  def effectiveAllowPackageSplit: Set[String] = policyChain.flatMap(_.allowPackageSplit).toSet

  /** every per-TYPE destination this manifest declares, keyed by the type it names — the DECLARATION two manifests have to agree about, not the resolved destination (needs a `Program`, which this has
    * none of).
    */

  /** every per-TYPE entry RESOLVED to its upstream-namespace destination — what [[renamed]] applies before the package renames.
    */
  def effectiveTypeMoves: Map[String, String] = PortManifest.declaredTypeMoves(this)

  def perTypeDestinations: Map[String, String] =
    effectiveTypeRenames.map((k, v) => k -> s"typeRenames=$v") ++
      effectiveSubPackages.map((k, v) => k -> s"subPackages=$v") ++
      effectiveFlattenNestedTypes.map(k => k -> "flattenNestedTypes")

  /** base phases first, then this module's own — with same-name phases folded through their declared [[MergeablePolicy]] where they declare one. See [[surfaceFold]] for everything the fold decides;
    * this is its `phases`, which is what a run's pipeline is built from.
    */
  def effectiveSurface: List[Phase] = surfaceFold.phases

  /** THE fold: this manifest's effective pipeline, and everything deciding it produced. A `lazy val` because a merged phase is a new instance holding a run's mutable binding state — a recomputed fold
    * would read a `policyReport` off an instance the pipeline never ran.
    */
  lazy val surfaceFold: SurfaceFold = SurfaceFold.of(policyChain, this)

  /** The [[Substitutions]] value this run hands the frontend: every drop in the chain, only THIS module's injections. A `lazy val`, not a `def` — it accumulates which keys fired, and the report must
    * be read off the same instance the frontend was given.
    */
  lazy val substitutions: Substitutions =
    Substitutions(effectiveDropTypes, effectiveDropMethods, inject, providedSources)

  /** What this module's own drops did — never an inherited key, since a finding names a key to fix and an inherited one lives in the base's manifest. The inherited half is checked separately and more
    * precisely, as [[ManifestAgreement.Kind.InheritedKeyNeverFired]].
    */
  def ownKeys: Set[String] = ownDrops.keys

  /** Base drop keys that never fired during this run. `fired` is supplied by the RUN rather than accumulated on `substitutions` — a mutable tally answered "on this INSTANCE", which broke when two
    * source sets shared one manifest. `PolicyBinder` derives it from the program instead.
    */
  def inheritedKeysNeverFired(fired: Set[String]): Map[String, Set[String]] =
    baseChain
      .map(b =>
        b.name -> ((b.dropTypes ++ b.dropMethods ++
          b.typeRenames.keySet ++ b.subPackages.keySet ++ b.flattenNestedTypes ++
          // …and the base's per-location SELECTIONS, which are keys like any other and bind through
          // the same binder, so `fired` already answers for them. A narrower dependent legitimately
          // never reaches the declaration a base selected a remedy at, which is exactly what this
          // non-fatal finding is for.
          b.resolutions.keySet) -- fired)
      )
      .filter(_._2.nonEmpty)
      .toMap

  /** the drops THIS module is answerable for — its own, minus anything a base also declares. A base's drop obliges this module to MODEL the type as substituted, not to SHIP the replacement; exactly
    * one module must, so [[mirroring]] can restate policy without re-shipping.
    */
  def ownDrops: Substitutions =
    val baseKeys = baseChain.flatMap(b => b.dropTypes ++ b.dropMethods).toSet
    Substitutions(dropTypes -- baseKeys, dropMethods -- baseKeys, inject, providedSources)

  /** the same manifest with every phase in the chain removed — for a structural-only re-emit. */
  def withoutSurface: PortManifest =
    copy(surface = Nil, bases = bases.map(_.withoutSurface))

  /** `fqn` after this manifest's effective renames, longest prefix first — cut only at a separator. Per-type entries apply first, package renames to their result. What this cannot do — and
    * `PackageRenameTransform` can — is refuse an entry: refusal needs a `Program`, which a manifest holds none of. This is the policy declared; the phase is what ran.
    */
  def renamed(fqn: String): String =
    val moves = effectiveTypeMoves
    val once  = PortManifest.longestPrefix(fqn, moves.keySet) match
      case Some(from) => moves(from) + fqn.substring(from.length)
      case None       => fqn
    PortManifest.longestPrefix(once, effectivePackageRenames.keySet) match
      case Some(from) => effectivePackageRenames(from) + once.substring(from.length)
      case None       => once

  /** does this manifest claim `fqn`? False for an empty [[governs]] — no claim, not "everything". */
  def claims(fqn: String): Boolean = governs.exists(PortManifest.covers(fqn, _))

  /** Phase name → the shared-surface SUBJECTS THIS manifest contributed to that phase's effective policy (read via `RunScope.contributed`). Where the fold merged this instance into a base's,
    * `SurfaceFold.ownKeys` answers; where no base declares the phase, every subject the instance holds is this module's own. A phase this manifest does not declare is absent — "no filter".
    */
  lazy val contributedSubjects: Map[String, Set[String]] =
    surface.collect { case p: MergeablePolicy =>
      p.name -> surfaceFold.ownKeys.getOrElse(p.name, p.subjects)
    }.toMap

  /** Does this manifest state any shared-surface policy at all? An empty manifest is the documented way to say "this resolution root is not a ported module", and every obligation a base carries is
    * owed only where there is policy to protect.
    */
  def declaresPolicy: Boolean =
    dropTypes.nonEmpty || dropMethods.nonEmpty || packageRenames.nonEmpty || surface.nonEmpty ||
      // a SELECTION is shared surface (see [[resolutions]]), so a module that states one is a
      // module whose surface a dependent must be screened against — and therefore one that owes a
      // `governs` claim and a published map.
      resolutions.nonEmpty

  /** the EMITTED FQNs this module's own [[inject]] roots supply — one derivation, in [[Substitutions.injectedSources]], which the run's copy loop and `PortMap` read too.
    *
    * `lazy`, because it walks the filesystem and the fold asks it once per screened subject. Own injections only, exactly as [[inject]] is declared per module. A [[providedSources]] file counts only
    * where it REPLACES a drop (its name is a dropped type's emitted name): the rest of the consumer's tree supplies nothing this port answers for.
    */
  lazy val injectedFqns: Set[String] =
    val replacements = effectiveDropTypes.map(renamed)
    Substitutions.injectedSources(inject ++ platformDirs.values.flatten.toList).map(_._1).toSet ++
      Substitutions.injectedSources(providedSources).map(_._1).filter(replacements).toSet

  /** does this module — or anything in its policy chain — ship ready-made Scala at `fqn`? `fqn` is upstream; asked through [[renamed]] since an injection root is in the port's own namespace and
    * comparing them directly would silently miss. Chain included: exactly one module in the base layer ships each replacement.
    */
  def shipsInjectionAt(fqn: String): Boolean =
    val at = renamed(fqn)
    policyChain.exists(_.injectedFqns.contains(at))

object PortManifest:

  /** The platform row names [[PortManifest.platformDirs]] accepts, one per [[balticporter.catalog.Platform]], spelled the way the generated build spells a row DIRECTORY (sbt-projectmatrix's
    * `VirtualAxis.PlatformAxis.directorySuffix`). A row a build does not compile is a directory nothing reads, so an unknown name is refused at load rather than silently ignored.
    */
  val PlatformRows: Map[String, balticporter.catalog.Platform] = Map(
    "jvm" -> balticporter.catalog.Platform.Jvm,
    "js" -> balticporter.catalog.Platform.ScalaJs,
    "native" -> balticporter.catalog.Platform.ScalaNative
  )

  /** `.` separates packages/top-level type, `$` precedes a nested type, `#` a member — the same three boundaries `PackageRenameTransform` cuts at. Forwards to [[balticporter.tir.RuleScope]], the one
    * implementation, so a rule this easy to get wrong has exactly one body.
    */
  def isBoundary(c: Char): Boolean = RuleScope.isBoundary(c)

  def covers(fullName: String, prefix: String): Boolean = RuleScope.covers(fullName, prefix)

  def longestPrefix(fullName: String, prefixes: Set[String]): Option[String] =
    RuleScope.longestPrefix(fullName, prefixes)

  /** THE derivation of a per-TYPE destination, in the upstream namespace — one body shared with the manifest's "what will a dependent see" question and the phase's "what do I rewrite to", so a string
    * rule this easy to get wrong is not duplicated. `Left` is a malformed key/value, never a structural program judgement (the phase's alone).
    */
  object TypeMove:

    /** everything up to and including the last separator — the enclosure a bare simple name keeps. */
    def enclosureOf(key: String): String =
      key.substring(0, (key.lastIndexWhere(isBoundary) + 1).max(0))

    /** the last segment, at any of the three separators. */
    def simpleNameOf(key: String): String =
      val i = key.lastIndexWhere(isBoundary)
      if i < 0 then key else key.substring(i + 1)

    /** a `typeRenames` value: a whole upstream FQN, or a bare SIMPLE NAME renaming it in place. */
    def renameTo(key: String, value: String): Either[String, String] =
      if value.isEmpty then Left("an empty target names nothing")
      else if value.contains('#') then Left("`#` makes this a MEMBER name; a type rename names a TYPE")
      else if value.exists(isBoundary) then Right(value)
      else Right(enclosureOf(key) + value)

    /** a `subPackages` value: `.`-separated package segments the type is nested under, in place. */
    def subPackage(key: String, value: String): Either[String, String] =
      if value.isEmpty then Left("an empty sub-package names nothing")
      else if value.exists(c => c == '#' || c == '$') then Left("a sub-package is a package: `.`-separated segments, never `$` or `#`")
      else if key.contains('$') then
        Left(
          "a NESTED type has no package of its own — sub-package its enclosing type, or flatten " +
            "it first with `flattenNestedTypes`"
        )
      else Right(enclosureOf(key) + value + "." + simpleNameOf(key))

    /** a `flattenNestedTypes` key: the nested type, at its enclosure's PACKAGE. */
    def flatten(key: String): Either[String, String] =
      if !key.contains('$') then Left("`flattenNestedTypes` names a NESTED type (`p.Outer$Inner`); this key has no `$`")
      else
        val head = key.substring(0, key.indexOf('$'))
        val i    = head.lastIndexOf('.')
        Right((if i < 0 then "" else head.substring(0, i + 1)) + simpleNameOf(key))

  /** the per-TYPE destinations a manifest DECLARES, upstream key → upstream target, with anything malformed left out — a manifest reports nothing, and the phase's binding is where a bad entry becomes
    * a finding.
    */
  private[core] def declaredTypeMoves(m: PortManifest): Map[String, String] =
    (m.effectiveTypeRenames.toList.map((k, v) => k -> TypeMove.renameTo(k, v)) ++
      m.effectiveSubPackages.toList.map((k, v) => k -> TypeMove.subPackage(k, v)) ++
      m.effectiveFlattenNestedTypes.toList.map(k => k -> TypeMove.flatten(k))).collect { case (k, Right(v)) => k -> v }.toMap

  /** A phase's SIGNATURE-AFFECTING identity, for comparing two modules' pipelines. `name` alone answers "did the dependent run this phase at all?"; a phase whose policy can differ between instances
    * also implements [[SurfacePolicy]] and contributes its policy. A parameterised phase that does NOT implement it compares by name only — a real, opt-in blind spot.
    */
  def fingerprint(p: Phase): String = p match
    case s: SurfacePolicy => s"${p.name}[${s.surfaceFingerprint}]"
    case _ => p.name

/** Implemented by a phase whose CONFIGURATION changes emitted signatures, so two modules' instances can be compared — see [[PortManifest.fingerprint]] for the cost of not implementing it. The string
  * must be a pure, stable, order-independent rendering of the phase's policy (sort anything set-like).
  */
trait SurfacePolicy:
  def surfaceFingerprint: String
