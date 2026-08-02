package balticporter.core

import balticporter.tir.{Phase, RuleScope}

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
  * ==What [[governs]] is for — and what an EMPTY one switches off==
  * A namespace claim, used where a check genuinely needs prefixes. Substitution agreement does NOT
  * use it: that check works from unit ORIGINS, so it is exact even when the two modules interleave
  * their packages (a library's own test suite typically declares its suites in the very packages it
  * tests, and no prefix can separate those).
  *
  * '''An empty `governs` is not merely a lost diagnosis — it DISABLES THE INTRUSION SCREEN for this
  * module entirely.''' [[claims]] is `false` for every FQN when the set is empty ("no claim", never
  * "everything"), and `SurfaceFold`'s `governs` screen — the thing that stops a DEPENDENT adding
  * policy that re-shapes this module's emitted surface (§1.5, `DESIGN.md` §8.13) — asks exactly
  * that question of each base. A base with no claim therefore admits every subject every dependent
  * adds, silently and by arithmetic: there is no code path that reports it, because a screen with
  * nothing to screen against is indistinguishable from a screen that passed. `ManifestAgreement`
  * reports it ([[ManifestAgreement.Kind.BaseNamespaceUnclaimed]]) for that reason.
  *
  * Three checks read it: the package-rename comparison (`RenameOverride`, `TypeRenameDivergence`'s
  * extra half), the `ExtraDrop` comparison, and the intrusion screen. Leave it empty only for a
  * module nothing depends on, or for the empty manifest that declares "this resolution root is not
  * a ported module" — where there is no policy to protect and [[declaresPolicy]] says so.
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
    /** upstream TYPE FQN → its name in the port, as a whole upstream FQN or a bare simple name.
      * Inherited, for exactly the reason [[packageRenames]] is: a type whose emitted name differs
      * between two modules gives the dependent references the base never wrote. */
    typeRenames: Map[String, String] = Map.empty,
    /** upstream TYPE FQN → a sub-package to nest it under, in place. Inherited. */
    subPackages: Map[String, String] = Map.empty,
    /** NESTED type FQNs (`p.Outer$Inner`) promoted to top level. Inherited. */
    flattenNestedTypes: Set[String] = Set.empty,
    /** upstream TYPE FQNs whose per-type move crosses an access boundary Java gave them, and which
      * this port DECLARES deliberate — see `PackageRenameTransform`'s boundary rule. Inherited,
      * because the boundary it moves is the SHARED one: a dependent that inherited the rename and
      * not the declaration would refuse a move its base performed. */
    allowPackageSplit: Set[String] = Set.empty,
    /** the phases that shape EMITTED SIGNATURES. Inherited, and placed before a dependent's own. */
    surface: List[Phase] = Nil,
    /** ready-made Scala this module ships. NOT inherited — see the class doc. */
    inject: List[Path] = Nil,
    /** the modules this one is a dependent OF, nearest last. */
    bases: List[PortManifest] = Nil,
    /** WHERE THOSE BASES PUBLISHED. Extra directories to look for a base module's `port-map.tsv`
      * under, nearest first; the run's own report tree is always searched first and cannot be
      * shadowed.
      *
      * NOT inherited, and it sits here rather than in a flag for the reason CLAUDE.md §4.6 gives for
      * `reportPathRoot`: a base's map decides EMITTED TEXT (the constructor plan, the class-vs-object
      * question, §4.55's field names, the `export` lists), so which maps a run discovers is part of
      * that run's identity. Left to `balticporter.baseReports`, it was the operator's — a leftover
      * `debug.properties` entry adds a base, and two checkouts at the same commit emit differently
      * with every count identical. The flag survives as the fallback for a tool with no port
      * configuration at all (`DebugEmit`), and `PortMap.searchPath` is the one place the two meet.
      *
      * Empty is the ordinary case and the whole of this repository: every port here publishes under
      * one `port-report/`, which `PortMap.reportRoot` already finds. §4.45's consumer, whose base was
      * run in another repository, is the caller this exists for. */
    baseReports: List[Path] = Nil,
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
):

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

  /** the per-TYPE half of the rename policy, composed exactly as [[effectivePackageRenames]] is —
    * bases first, so a dependent's declaration wins and [[ManifestAgreement]] reports the override
    * rather than the engine hiding it. */
  def effectiveTypeRenames: Map[String, String] =
    policyChain.foldLeft(Map.empty[String, String])((acc, m) => acc ++ m.typeRenames)

  def effectiveSubPackages: Map[String, String] =
    policyChain.foldLeft(Map.empty[String, String])((acc, m) => acc ++ m.subPackages)

  def effectiveFlattenNestedTypes: Set[String] = policyChain.flatMap(_.flattenNestedTypes).toSet

  def effectiveAllowPackageSplit: Set[String] = policyChain.flatMap(_.allowPackageSplit).toSet

  /** every per-TYPE destination this manifest declares, keyed by the type it names — the one view
    * [[ManifestAgreement]] compares, so that a base saying `subPackages` and a dependent saying the
    * equivalent `typeRenames` are not reported as agreeing when they are two different keys. It is
    * NOT the resolved destination (that needs a `Program`, and this value has none): it is the
    * DECLARATION, which is what two manifests have to agree about. */
  /** every per-TYPE entry RESOLVED to its upstream-namespace destination — what [[renamed]] applies
    * before the package renames, and what a check comparing NAMES (rather than declarations) reads. */
  def effectiveTypeMoves: Map[String, String] = PortManifest.declaredTypeMoves(this)

  def perTypeDestinations: Map[String, String] =
    effectiveTypeRenames.map((k, v) => k -> s"typeRenames=$v") ++
      effectiveSubPackages.map((k, v) => k -> s"subPackages=$v") ++
      effectiveFlattenNestedTypes.map(k => k -> "flattenNestedTypes")

  /** base phases first, then this module's own — with same-name phases folded through their
    * declared [[MergeablePolicy]] where they declare one. See [[surfaceFold]] for everything the
    * fold decides; this is its `phases`, which is what a run's pipeline is built from. */
  def effectiveSurface: List[Phase] = surfaceFold.phases

  /** THE fold: this manifest's effective pipeline, and everything deciding it produced.
    *
    * A `lazy val`, and for [[substitutions]]' reason rather than for speed: a MERGED phase is a new
    * instance holding a run's mutable binding state, so recomputing the fold would leave the run
    * reading the `policyReport` of an instance the pipeline never ran (DESIGN.md §8.13).
    */
  lazy val surfaceFold: SurfaceFold = SurfaceFold.of(policyChain, this)

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
  def ownKeys: Set[String] = ownDrops.keys

  /** base drop keys that never fired during this run.
    *
    * `fired` is supplied by the RUN rather than accumulated on `substitutions`, which used to carry
    * a mutable tally of the keys it had been asked about. That tally answered "did this key ever
    * fire on this INSTANCE", which is not the question — two source sets translated through one
    * manifest unioned their answers, and `copy()` silently emptied it. `PolicyBinder` answers from
    * the program and the frontend's index instead. */
  def inheritedKeysNeverFired(fired: Set[String]): Map[String, Set[String]] =
    baseChain.map(b =>
      b.name -> ((b.dropTypes ++ b.dropMethods ++
        b.typeRenames.keySet ++ b.subPackages.keySet ++ b.flattenNestedTypes) -- fired))
      .filter(_._2.nonEmpty).toMap

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
    * `PackageRenameTransform` cuts it, or `com.foo` would cover `com.foobar`.
    *
    * PER-TYPE entries are applied first and the package renames to their RESULT, which is the
    * composition §4.56 asks for: every target is written upstream, so a port can add a package
    * rename without rewriting a single type entry. What this cannot do — and the phase can — is
    * REFUSE an entry: refusal is a structural judgement about a `Program` (is the key owned, is the
    * destination free, does the move split a package), and a manifest holds no program. So this is
    * what the policy DECLARES; `PackageRenameTransform.emittedName` on the instance a run placed
    * last is what the run PERFORMED, and the two differ exactly by the refusals, each of which is
    * a §1(b) finding on that run. */
  def renamed(fqn: String): String =
    val moves = effectiveTypeMoves
    val once = PortManifest.longestPrefix(fqn, moves.keySet) match
      case Some(from) => moves(from) + fqn.substring(from.length)
      case None       => fqn
    PortManifest.longestPrefix(once, effectivePackageRenames.keySet) match
      case Some(from) => effectivePackageRenames(from) + once.substring(from.length)
      case None       => once

  /** does this manifest claim `fqn`? False for an empty [[governs]] — no claim, not "everything". */
  def claims(fqn: String): Boolean = governs.exists(PortManifest.covers(fqn, _))

  /** Phase name → the shared-surface SUBJECTS THIS manifest contributed to that phase's EFFECTIVE
    * policy — what a phase reads through `balticporter.tir.RunScope.contributed`.
    *
    * Two cases, and the second is the one with no constraint on it at all:
    *
    *   - the fold MERGED this module's instance into a base's, and `SurfaceFold.ownKeys` is its own
    *     record of what this module added;
    *   - no base declares the phase, so the instance reaches the pipeline whole and EVERY subject it
    *     holds is one this module contributed. `ownKeys` has no entry for it — the fold only records
    *     a merge — so the phase's own `subjects` is the answer.
    *
    * A phase this manifest does NOT declare is absent from the map, which the reader takes as "no
    * filter": every key it holds came from a base, and the base's own run applied it identically.
    *
    * One derivation, here rather than in the run, for the reason [[surfaceFold]] is on the manifest:
    * the `.conf` path and the Scala path build the same value through the same constructors, and a
    * second copy in the orchestrator would be free to drift from this one. */
  lazy val contributedSubjects: Map[String, Set[String]] =
    surface.collect { case p: MergeablePolicy =>
      p.name -> surfaceFold.ownKeys.getOrElse(p.name, p.subjects)
    }.toMap

  /** Does this manifest state any SHARED-SURFACE policy at all?
    *
    * An empty manifest is the documented way to say "this resolution root is not a ported module"
    * (CLAUDE.md §1.5), and every obligation a base carries — publish a map, claim a namespace — is
    * an obligation only where there is policy to protect. One predicate, read by
    * `ManifestAgreement` on both sides of that line. */
  def declaresPolicy: Boolean =
    dropTypes.nonEmpty || dropMethods.nonEmpty || packageRenames.nonEmpty || surface.nonEmpty

  /** the EMITTED FQNs this module's own [[inject]] roots supply — one derivation, in
    * [[Substitutions.injectedSources]], which the run's copy loop and `PortMap` read too.
    *
    * `lazy`, because it walks the filesystem and the fold asks it once per screened subject. Own
    * injections only, exactly as [[inject]] is declared per module (§1.5). */
  lazy val injectedFqns: Set[String] = Substitutions.injectedSources(inject).map(_._1).toSet

  /** does this module — or anything in its policy chain — SHIP ready-made Scala at `fqn`?
    *
    * `fqn` is UPSTREAM (a manifest key) and an injection root holds the port's own namespace, so
    * the question is asked at [[renamed]]: the two sides are in different namespaces and comparing
    * them directly is the §4.56 failure `PortMap.Disposition.Substituted` was bitten by, where
    * `Substituted` had never once been produced by a renaming port.
    *
    * The chain is included because "does something stand at that name in the shared output" is a
    * question about the whole base layer, and exactly one module in it ships each replacement. */
  def shipsInjectionAt(fqn: String): Boolean =
    val at = renamed(fqn)
    policyChain.exists(_.injectedFqns.contains(at))

object PortManifest:

  /** `.` separates packages and the top-level type, `$` precedes a nested type, `#` a member — the
    * same three boundaries `PackageRenameTransform` cuts at, and for the same reason.
    *
    * These three FORWARD to [[balticporter.tir.RuleScope]], which is the one implementation of
    * CLAUDE.md §4.56's separator cut: the rule is about `Symbol.fullName`, and a manifest key and a
    * rule scope ask the identical question of it. They are kept as names here because every caller
    * in the engine reaches them through the manifest, and because a rule this easy to get wrong
    * must have exactly one body — a second copy is a second thing to fix when the trap is sprung
    * again. */
  def isBoundary(c: Char): Boolean = RuleScope.isBoundary(c)

  def covers(fullName: String, prefix: String): Boolean = RuleScope.covers(fullName, prefix)

  def longestPrefix(fullName: String, prefixes: Set[String]): Option[String] =
    RuleScope.longestPrefix(fullName, prefixes)

  /** THE derivation of a per-TYPE destination, in the UPSTREAM namespace.
    *
    * ONE body, and that is the point of it being here rather than in the phase that performs it:
    * the manifest answers "what name will a dependent module see" and `PackageRenameTransform`
    * answers "what do I rewrite this symbol to", and two copies of a string rule this easy to get
    * wrong is one copy too many (the same argument [[covers]] carries about the separator cut).
    * `Left` is the reason the entry is not one that can be carried out at all — a MALFORMED key or
    * value, never a structural judgement about a program, which is the phase's alone.
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
      else if value.exists(c => c == '#' || c == '$') then
        Left("a sub-package is a package: `.`-separated segments, never `$` or `#`")
      else if key.contains('$') then
        Left("a NESTED type has no package of its own — sub-package its enclosing type, or flatten " +
          "it first with `flattenNestedTypes`")
      else Right(enclosureOf(key) + value + "." + simpleNameOf(key))

    /** a `flattenNestedTypes` key: the nested type, at its enclosure's PACKAGE. */
    def flatten(key: String): Either[String, String] =
      if !key.contains('$') then
        Left("`flattenNestedTypes` names a NESTED type (`p.Outer$Inner`); this key has no `$`")
      else
        val head = key.substring(0, key.indexOf('$'))
        val i    = head.lastIndexOf('.')
        Right((if i < 0 then "" else head.substring(0, i + 1)) + simpleNameOf(key))

  /** the per-TYPE destinations a manifest DECLARES, upstream key → upstream target, with anything
    * malformed left out — a manifest reports nothing, and the phase's binding is where a bad entry
    * becomes a finding. */
  private[core] def declaredTypeMoves(m: PortManifest): Map[String, String] =
    (m.effectiveTypeRenames.toList.map((k, v) => k -> TypeMove.renameTo(k, v)) ++
      m.effectiveSubPackages.toList.map((k, v) => k -> TypeMove.subPackage(k, v)) ++
      m.effectiveFlattenNestedTypes.toList.map(k => k -> TypeMove.flatten(k)))
      .collect { case (k, Right(v)) => k -> v }.toMap

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
