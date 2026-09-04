package balticporter.transform

import balticporter.core.{MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource, RequiresRuntime, RuntimeArtifact, SurfacePolicy}
import balticporter.tir.*

/** `java.util` collections to `scala.collection.mutable`. Retypes every collection
  * occurrence via the symbol table, rewrites call shapes kind-aware, coerces at
  * JDK/external seams. [[RuleScope]] controls which declarations are reached;
  * unclosable scope seams are counted by [[CollectionBoundaryCheck]].
  */
final class CollectionsTransform(
    val scope: RuleScope = RuleScope.Everywhere(),
    /** Java FQN to scala FQN retarget: retyped at every occurrence, NO kind-aware rewrites.
      * The scala target must extend/implement the java source (no coercion needed).
      * A key colliding with `typeMap` is refused. Empty = no-op. */
    val retarget: Map[String, String] = Map.empty,
    /** Per-retarget member call-site rewrites, keyed `(memberName, arity)` to [[RetargetRewrite]].
      * Descriptor-keyed [[retargetRewritesByDesc]] wins over arity-keyed at the same member.
      * Empty = no-op. `MergeablePolicy` unions independent keys. */
    val retargetRewrites: Map[String, Map[(String, Int), CollectionsTransform.RetargetRewrite]] = Map.empty,
    /** Descriptor-keyed retarget rewrites — `(name, Descriptor)` to [[RetargetRewrite]].
      * Wins over arity-keyed [[retargetRewrites]] at the same member.
      * For members overloaded at the same arity. Empty = no-op. */
    val retargetRewritesByDesc: Map[String, Map[(String, Descriptor), CollectionsTransform.RetargetRewrite]] = Map.empty,
    /** Type argument mapping for arity-changing retargets — source FQN to target arg template.
      * Each element: `SourceArg(i)` or `FixedType(fqn)`. List length must equal target arity.
      * Orphan keys (no `retarget` entry) refused at construction. Empty = no-op. */
    val retargetTypeArgs: Map[String, List[CollectionsTransform.RetargetArg]] = Map.empty,
    /** External generic types whose type arguments a third party reifies at run time.
      * Arguments stay in java's namespace; values bridged at use via [[externalProducer]].
      * [[UniversalCarriers]] (`java.lang.Class`) always added. Empty = no-op.
      * // ENGINE-LIMITS K20 */
    val reifiedCarriers: Set[String] = Set.empty,
    /** External types that read the runtime representation of a value at an opaque slot.
      * Bridge is [[ReifiedFqn]]`.toJavaValue` (identity for non-retyped, deep-by-view for retyped).
      * Per-library policy; empty = no-op. NOT part of [[surfaceFingerprint]].
      * // ENGINE-LIMITS K21 */
    val reflectiveSinks: Set[String] = Set.empty,
    /** Additional collection families — java FQN to (scala FQN, Kind), merged into [[typeMap]]
      * at construction. Collisions with JDK entries or `retarget` refused. Empty = no-op. */
    val families: Map[String, (String, CollectionsTransform.Kind)] = Map.empty,
    /** Per-entry scopes for [[families]] — java source FQN to `RuleScope`.
      * A key with no family entry is ignored. Default `Everywhere(Set.empty)`.
      * // ENGINE-LIMITS D12 */
    val familyScopes: Map[String, RuleScope] = Map.empty,
    /** Retarget coercions — `(actualHeadFQN, expectedHeadFQN)` to template string.
      * `$0` = actual value. Rendered as `Tree.Opaque.spliced` at type boundaries.
      * Empty = no-op. `MergeablePolicy` unions; same pair with different template refuses. */
    val retargetCoercions: Map[(String, String), String] = Map.empty,
) extends Phase, Rewrite, RequiresRuntime, PolicySource, SurfacePolicy, MergeablePolicy, PolicyBound:
  def name = "java-collections->scala"

  /** Check lanes that count this retyping's residue: [[CollectionClosureCheck]] (unmapped types),
    * [[CollectionBoundaryCheck]] (JDK-side slot seams), [[RetargetBoundaryCheck]] (producer-side). */
  def accountedBy: Set[String] =
    Set(CollectionClosureCheck.Name, CollectionBoundaryCheck.Name, RetargetBoundaryCheck.Name)

  /** Resolved bindings for each declared scope entry. */
  private var boundScope: Map[String, Binding[Unit]] = Map.empty

  /** Resolved bindings for each retarget source. `Ownership.Either` — retargets are referenced, not declared. */
  private var boundRetarget: Map[String, Binding[SymId]] = Map.empty

  /** Resolved bindings for each reified carrier. `Ownership.Either` — carriers are only referenced. */
  private var boundCarriers: Map[String, Binding[SymId]] = Map.empty

  /** Resolved bindings for each reflective sink. `Ownership.Either`. */
  private var boundSinks: Map[String, Binding[SymId]] = Map.empty

  /** Resolved bindings for each family source. `Ownership.Either`. */
  private var boundFamilies: Map[String, Binding[SymId]] = Map.empty

  def bindPolicy(binder: PolicyBinder): Unit =
    val setting = s"CollectionsTransform(scope) ${scope.productPrefix} entry"
    boundScope = scope.entries.toList.sorted.map(e => e -> binder.bindScope(name, setting, e)).toMap
    boundRetarget = retarget.keys.toList.sorted
      .map(k => k -> binder.bindType(name, RetargetSetting, k, Ownership.Either)).toMap
    boundCarriers = reifiedCarriers.toList.sorted
      .map(k => k -> binder.bindType(name, CarrierSetting, k, Ownership.Either)).toMap
    boundSinks = reflectiveSinks.toList.sorted
      .map(k => k -> binder.bindType(name, SinkSetting, k, Ownership.Either)).toMap
    boundFamilies = families.keys.toList.sorted
      .map(k => k -> binder.bindType(name, FamilySetting, k, Ownership.Either)).toMap

  /** Fingerprint covering scope, mapping table, retargets, carriers and families.
    * Segments omitted when their parameter is empty, so no baseline moves. */
  def surfaceFingerprint: String =
    val parts = List(
      // mapping table digest — rendered unconditionally
      Some("mapping=" + CollectionsTransform.mappingDigest),
      scala.Option.when(retarget.nonEmpty)(
        "retarget=" + retarget.toList.sorted.map((k, v) => s"$k->$v").mkString(",")),
      scala.Option.when(retargetRewrites.nonEmpty || retargetRewritesByDesc.nonEmpty)(
        "retargetRewrites=" + retargetRewritesDigest),
      scala.Option.when(retargetTypeArgs.nonEmpty)(
        "retargetTypeArgs=" + retargetTypeArgsDigest),
      scala.Option.when(reifiedCarriers.nonEmpty)(
        "carriers=" + reifiedCarriers.toList.sorted.mkString(",")),
      scala.Option.when(families.nonEmpty)(
        "families=" + familiesDigest),
    ).flatten
    s"${scope.fingerprint};${parts.mkString(";")}"

  // ---- MergeablePolicy: HOW this table composes with a nearer manifest's instance ----

  /** Shared-surface subjects: retarget keys + family keys (not JDK entries, carriers or sinks). */
  def subjects: Set[String] =
    (retarget.keySet ++ families.keySet).map(MergeablePolicy.subjectOf)

  /** Merge `later` into this. A dependent ADDS families and retargets; same source with a different
    * target refuses with the phase's sentence. Scopes on the SAME family source must agree. */
  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case o: CollectionsTransform =>
      // --- retarget clashes ---
      val retargetClash = (retarget.keySet & o.retarget.keySet).filter(k => retarget(k) != o.retarget(k))
      // --- family clashes: same source, different (target, kind) ---
      val familyClash = (families.keySet & o.families.keySet).filter(k => families(k) != o.families(k))
      // --- family/retarget cross-clash: a key in both tables ---
      val crossClash = (families.keySet & o.retarget.keySet) ++ (retarget.keySet & o.families.keySet)
      // --- scope clashes on families both sides declare ---
      val scopeClash = (families.keySet & o.families.keySet).toList.sorted
        .filter(k => familyScopeOf(k) != o.familyScopeOf(k))
      // --- scope clashes on retargets both sides declare ---
      val retargetScopeClash = (retarget.keySet & o.retarget.keySet).toList.sorted
        .filter(k =>
          scope.entries.intersect(Set(retarget(k))).nonEmpty !=
          o.scope.entries.intersect(Set(o.retarget(k))).nonEmpty) // rough — retargets do not have per-entry scopes
      // --- retargetRewrites clashes: same source FQN, different rewrite table ---
      val rewriteClash = (retargetRewrites.keySet & o.retargetRewrites.keySet)
        .filter(k => retargetRewrites(k) != o.retargetRewrites(k))
      val descRewriteClash = (retargetRewritesByDesc.keySet & o.retargetRewritesByDesc.keySet)
        .filter(k => retargetRewritesByDesc(k) != o.retargetRewritesByDesc(k))
      // --- retargetTypeArgs clashes: same source FQN, different arg mapping ---
      val typeArgsClash = (retargetTypeArgs.keySet & o.retargetTypeArgs.keySet)
        .filter(k => retargetTypeArgs(k) != o.retargetTypeArgs(k))
      // --- carrier/sink disagreements are NOT surface and therefore NOT a refusal ---
      if retargetClash.nonEmpty || familyClash.nonEmpty || crossClash.nonEmpty ||
          scopeClash.nonEmpty || rewriteClash.nonEmpty || descRewriteClash.nonEmpty || typeArgsClash.nonEmpty then
        Left(
          (retargetClash.toList.sorted.map(k =>
             s"""both modules retarget "$k", to "${retarget(k)}" and "${o.retarget(k)}"""") ++
           familyClash.toList.sorted.map(k =>
             s"""both modules declare a family for "$k", """ +
               s"""to (${families(k)._1}, ${families(k)._2}) and (${o.families(k)._1}, ${o.families(k)._2})""") ++
           crossClash.toList.sorted.map(k =>
             s""""$k" appears in families on one side and retarget on the other — two answers for one type""") ++
           scopeClash.map(k =>
             s"""both modules scope the family "$k" and disagree — """ +
               s""""${familyScopeOf(k).fingerprint}" and "${o.familyScopeOf(k).fingerprint}"; a scope """ +
               "decides which declarations carry the family type in their signatures") ++
           rewriteClash.toList.sorted.map(k =>
             s"""both modules declare retarget rewrites for "$k" and disagree""") ++
           descRewriteClash.toList.sorted.map(k =>
             s"""both modules declare descriptor-keyed retarget rewrites for "$k" and disagree""") ++
           typeArgsClash.toList.sorted.map(k =>
             s"""both modules declare retarget type args for "$k" and disagree"""))
            .mkString("; ") +
            " — two answers for one key is a rewrite whose outcome depends on which manifest was read")
      else
        val mergedRetarget = retarget ++ o.retarget
        val mergedRewrites = retargetRewrites ++ o.retargetRewrites
        val mergedDescRewrites = retargetRewritesByDesc ++ o.retargetRewritesByDesc
        val mergedTypeArgs = retargetTypeArgs ++ o.retargetTypeArgs
        val mergedFamilies = families ++ o.families
        val mergedFamilyScopes = familyScopes ++ o.familyScopes
        // carriers, sinks and coercions: union without a clash (they are not surface)
        val mergedCarriers = reifiedCarriers ++ o.reifiedCarriers
        val mergedSinks = reflectiveSinks ++ o.reflectiveSinks
        val mergedCoercions = retargetCoercions ++ o.retargetCoercions
        val addedRetargetSubjects = (o.retarget.keySet -- retarget.keySet).map(MergeablePolicy.subjectOf)
        val addedFamilySubjects = (o.families.keySet -- families.keySet).map(MergeablePolicy.subjectOf)
        Right(MergeablePolicy.Merged(
          new CollectionsTransform(
            scope            = scope, // the base's scope — inherited
            retarget         = mergedRetarget,
            retargetRewrites = mergedRewrites,
            retargetRewritesByDesc = mergedDescRewrites,
            retargetTypeArgs = mergedTypeArgs,
            reifiedCarriers  = mergedCarriers,
            reflectiveSinks  = mergedSinks,
            families         = mergedFamilies,
            familyScopes     = mergedFamilyScopes,
            retargetCoercions = mergedCoercions),
          addedRetargetSubjects ++ addedFamilySubjects))
    case other =>
      Left(s"`${other.name}` is not a `CollectionsTransform`, so there is no table to compose")

  /** Runtime types this phase retypes onto. */
  def runtimeTypes: Set[String] = CollectionsTransform.runtimeTypes

  import CollectionsTransform.{JavaCollectionFqn, JavaCollectionsFqn, JavaIterableFqn, JavaIteratorFqn, Kind}

  // ---- COLLISION CHECK ----
  locally {
    val jdkClash = families.keySet & CollectionsTransform.typeMap.keySet
    require(jdkClash.isEmpty,
      s"CollectionsTransform: families key(s) ${jdkClash.mkString(", ")} also appear in the JDK " +
        "typeMap — two answers for one type is not a thing a policy author can reason about")
    val retargetClash = families.keySet & retarget.keySet
    require(retargetClash.isEmpty,
      s"CollectionsTransform: families key(s) ${retargetClash.mkString(", ")} also appear in " +
        "retarget — two answers for one type is not a thing a policy author can reason about")
    val orphanRewrites = retargetRewrites.keySet -- retarget.keySet
    require(orphanRewrites.isEmpty,
      s"CollectionsTransform: retargetRewrites key(s) ${orphanRewrites.mkString(", ")} have no " +
        "matching retarget entry — a rewrite table for a type this phase does not retarget is dead code")
    val orphanTypeArgs = retargetTypeArgs.keySet -- retarget.keySet
    require(orphanTypeArgs.isEmpty,
      s"CollectionsTransform: retargetTypeArgs key(s) ${orphanTypeArgs.mkString(", ")} have no " +
        "matching retarget entry — an arg mapping for a type this phase does not retarget is dead code")
    val orphanDescRewrites = retargetRewritesByDesc.keySet -- retarget.keySet
    require(orphanDescRewrites.isEmpty,
      s"CollectionsTransform: retargetRewritesByDesc key(s) ${orphanDescRewrites.mkString(", ")} have no " +
        "matching retarget entry — a rewrite table for a type this phase does not retarget is dead code")
  }

  /** Descriptor-keyed retarget rewrites. Keys are in the UPSTREAM namespace. */
  private lazy val remappedDescRewrites: Map[String, Map[(String, Descriptor), CollectionsTransform.RetargetRewrite]] =
    retargetRewritesByDesc

  /** Look up a retarget rewrite. Descriptor-keyed wins over arity-keyed. */
  private def lookupRewrite(srcFqn: String, name: String, arity: Int, desc: Option[Descriptor]): Option[CollectionsTransform.RetargetRewrite] =
    desc.flatMap { d =>
      remappedDescRewrites.get(srcFqn).flatMap { tbl =>
        tbl.collectFirst { case ((n, dd), rw) if n == name && dd.matches(d) => rw }
      }
    }.orElse(
      retargetRewrites.get(srcFqn).flatMap(_.get((name, arity)))
    )

  /** JDK + families merged type map: java FQN to (scala FQN, Kind). */
  private val typeMap: Map[String, (String, Kind)] = CollectionsTransform.typeMap ++ families

  /** the RuleScope a FAMILY ENTRY declares — `Everywhere(Set.empty)` (the pre-scope code path)
    * when the source FQN has no explicit scope. JDK entries are not scoped through this map;
    * they use the phase-level `scope` as they always have. */
  def familyScopeOf(from: String): RuleScope = familyScopes.getOrElse(from, RuleScope.everywhere)

  /** digest of the `families` table — sorted by source FQN, carrying the target and kind. The
    * scope is part of the fingerprint too (a scope difference is a surface difference), so it joins
    * the sorted string. Used only when `families.nonEmpty`. */
  private def familiesDigest: String =
    balticporter.tir.TirPrinter.sha256(
      families.toList.map((k, v) => s"$k->${v._1}:${v._2};scope=${familyScopeOf(k).fingerprint}")
        .sorted.mkString(",")).take(16)

  /** digest of the `retargetRewrites` + `retargetRewritesByDesc` tables — sorted by source FQN
    * then by key rendering. Arity-keyed entries render as `src#name/arity->Rw`, descriptor-keyed
    * as `src#name/(desc)->Rw`. The two maps are combined so one digest covers both. */
  private def retargetRewritesDigest: String =
    def renderRw(rw: CollectionsTransform.RetargetRewrite): String = rw match
      case CollectionsTransform.RetargetRewrite.Rename(t) => s"Rename($t)"
      case CollectionsTransform.RetargetRewrite.BoolDispatch(f, t, ff) => s"BoolDispatch($f,$t,$ff)"
      case CollectionsTransform.RetargetRewrite.Construct(c, m, dt, ft) =>
        val base = if dt == 0 then s"Construct($c,$m)" else s"Construct($c,$m,$dt)"
        if ft then s"$base+fill" else base
      case CollectionsTransform.RetargetRewrite.ForEach(t, a) => s"ForEach($t,$a)"
      case CollectionsTransform.RetargetRewrite.Collect(v, i) => s"Collect($v,$i)"
      case CollectionsTransform.RetargetRewrite.Chain(ms, ps, da) =>
        val base = if ps.isEmpty then s"Chain(${ms.mkString(";")})"
        else s"Chain(${ms.mkString(";")};parens=${ps.toList.sorted.mkString(",")})"
        if da then s"$base;dropArgs" else base
      case CollectionsTransform.RetargetRewrite.FieldWrite(f, m) => s"FieldWrite($f,$m)"
      case CollectionsTransform.RetargetRewrite.IndexedField(f) => s"IndexedField($f)"
      case CollectionsTransform.RetargetRewrite.Template(e) => s"Template($e)"
    val arityEntries = retargetRewrites.toList.sortBy(_._1).flatMap { (src, tbl) =>
      tbl.toList.sortBy(_._1.toString).map { case ((m, ar), rw) => s"$src#$m/$ar->${renderRw(rw)}" }
    }
    val descEntries = retargetRewritesByDesc.toList.sortBy(_._1).flatMap { (src, tbl) =>
      tbl.toList.sortBy(_._1.toString).map { case ((m, d), rw) => s"$src#$m/(${d.render})->${renderRw(rw)}" }
    }
    balticporter.tir.TirPrinter.sha256((arityEntries ++ descEntries).mkString(",")).take(16)

  /** digest of the `retargetTypeArgs` table — sorted by source FQN, each arg rendered as
    * `arg(i)`, `fixed(fqn)`, or `applied(fqn,args)`. Used only when `retargetTypeArgs.nonEmpty`. */
  private def retargetTypeArgsDigest: String =
    def renderArg(a: CollectionsTransform.RetargetArg): String = a match
      case CollectionsTransform.RetargetArg.SourceArg(i) => s"arg($i)"
      case CollectionsTransform.RetargetArg.FixedType(fqn) => s"fixed($fqn)"
      case CollectionsTransform.RetargetArg.Applied(fqn, inner) =>
        s"applied($fqn,${inner.map(renderArg).mkString("+")})"
    balticporter.tir.TirPrinter.sha256(
      retargetTypeArgs.toList.sortBy(_._1).map { (src, args) =>
        s"$src->${args.map(renderArg).mkString(",")}"
      }.mkString(";")).take(16)

  /** The java types this phase retypes. */
  def mappedTypes: Set[String] = typeMap.keySet

  /** Target FQN for a mapped type, or `"?"` if not mapped. */
  def targetOf(fqn: String): String = typeMap.get(fqn).map(_._1).getOrElse("?")

  /** All scala/shim types this phase produces. */
  def retypedTargets: Set[String] = typeMap.values.map(_._1).toSet

  /** [[CollectionClosureCheck]] over this phase's own mapping. */
  def closure(program: Program): List[CollectionClosureCheck.Finding] =
    closure(program, program.units)

  /** Closure check scoped to emitted units only. // ENGINE-LIMITS D2 */
  def closure(program: Program, units: List[Tree.ClassDef]): List[CollectionClosureCheck.Finding] =
    CollectionClosureCheck.check(program, units, mappedTypes, targetOf)

  /** [[CollectionBoundaryCheck]] — run AFTER the phase. */
  def boundary(program: Program): List[CollectionBoundaryCheck.Finding] =
    boundary(program, program.units)

  /** Boundary check scoped to emitted units. [[scopedOut]] classifies scope-created seams. */
  def boundary(program: Program, units: List[Tree.ClassDef]): List[CollectionBoundaryCheck.Finding] =
    CollectionBoundaryCheck.check(program, units, mappedTypes, retypedTargets, scopedOut,
                                  classFileOverrides) ++
      // external seams recorded during traversal, filtered to emitted units (D2)
      externalSeams.toList.filter(f => emittedPaths(units).contains(f.origin.javaPath)) ++
      // opaque egress review list (K21) — one row per external callee, deduped per (callee, java file), D2-filtered
      opaqueEgressSites.toList
        .filter((k, _) => emittedPaths(units).contains(k._2))
        .groupBy((k, _) => k._1)
        .toList
        .map((m, rows) => m -> rows.map(_._2).minBy(o => (o.javaPath, o.line)))
        .sortBy((m, o) => (o.javaPath, o.line, m.raw))
        .map((m, o) => CollectionBoundaryCheck.Finding(
          CollectionBoundaryCheck.Issue.OpaqueEgress,
          s"argument (external callee, java.lang.Object formal): ${calleeLabel(m)(using program)}",
          "java's own representation, IF this callee reads it",
          "a value this port may have retyped", o, m))

  /** External callee label: `<owner FQN>#<member>`. */
  private def calleeLabel(m: SymId)(using p: Program): String =
    memberKeyOf(m).getOrElse("?")

  /** `<owner FQN>#<member>` for a callee. */
  private def memberKeyOf(m: SymId)(using p: Program): Option[String] =
    p.symbolOf(m).flatMap(c => p.symbolOf(c.owner).map(o => MemberKey(o.fullName, c.name).render))

  /** Java source paths of emitted units — the D2 filter. */
  private def emittedPaths(units: List[Tree.ClassDef]): Set[String] =
    units.map(_.origin.javaPath).toSet

  /** [[CollectionInternalCheck]] — in-program half of the boundary residue. Run AFTER the phase. */
  def internal(program: Program): List[CollectionInternalCheck.Finding] =
    internal(program, program.units)

  /** Internal check scoped to emitted units. */
  def internal(program: Program, units: List[Tree.ClassDef]): List[CollectionInternalCheck.Finding] =
    CollectionInternalCheck.check(program, units, mappedTypes, targetOf,
                                  CollectionsTransform.standaloneTargets)

  /** [[RetargetBoundaryCheck]] — producer direction. Run AFTER the phase. */
  def retargetBoundary(program: Program): List[RetargetBoundaryCheck.Finding] =
    retargetBoundary(program, program.units)

  /** Retarget boundary check scoped to emitted units. */
  def retargetBoundary(program: Program, units: List[Tree.ClassDef]): List[RetargetBoundaryCheck.Finding] =
    RetargetBoundaryCheck.check(program, units, effectiveRetarget)

  /** scala nullary accessors that take NO parens (`def size: Int`) — a Java `size()`
    * emitted as `size()` would be an illegal application. Strip the `Apply`. */
  private val parenless = Set("size", "isEmpty", "iterator", "keySet", "values", "nonEmpty", "hasNext", "next")

  // prepared in `run`, read by the hooks.
  private var remap: Map[SymId, SymId]    = Map.empty
  /** Target SymIds of the full remap — for `transformType`'s wildcard-strip checks. */
  private var remapTargets: Set[SymId]   = Set.empty
  /** fullName to minted SymId fallback for `transformType` — covers dependent-interned SymIds. */
  private var remapByFullName: Map[String, SymId] = Map.empty
  /** source SymId to java FQN for family remap entries — for per-entry scope (D12). */
  private var familyRemapSources: Map[SymId, String] = Map.empty
  /** Declared classes by symbol — source of class type parameters. */
  private var classDefsBySym: Map[SymId, Tree.ClassDef] = Map.empty
  private var kindOf: Map[SymId, Kind]    = Map.empty // scala collection symbol → kind
  /** Per-class info about minted collection parents. `kinds` = set (a class may implement several).
    * `probes` = first type arg of each mapped parent. `shims` = standalone targets (no member clash).
    * `targets` = scala FQNs. `declared` = this class's own mapped clauses (non-transitive). */
  private final case class MintedParents(kinds: Set[Kind], probes: List[TypeRepr],
                                         tparams: List[SymId], shims: Set[String],
                                         targets: Set[String] = Set.empty,
                                         subsumed: Map[String, String] = Map.empty,
                                         declared: List[(Kind, List[TypeRepr])] = Nil)
  private var parentClash: Map[SymId, MintedParents] = Map.empty
  /** Collected `super.<JDK default>` rewrites: (enclosing class, callee, member name). */
  private val superDefaults = collection.mutable.ListBuffer.empty[(SymId, SymId, String)]
  private var opPlusEq, opMinusEq, opPlusPlusEq: SymId = SymId.None
  /** Plain arithmetic operators for compound-FieldWrite expansion: `size -= 1` -> `setSize(size - 1)`. */
  private var compoundOps: Map[String, SymId] = Map.empty
  private var updateSym, insertSym, getOrElseSym, containsSym: SymId = SymId.None
  /** `mutable.Map.put`/`remove` — return previous value (unlike `update`/`-=`). */
  private var putSym, removeSym: SymId = SymId.None
  /** Deque members — `poll`/`peek` go through `Option`/`orNull` (null-on-empty vs throw). */
  private var removeHeadOptionSym, headOptionSym, orNullSym, prependSym: SymId = SymId.None
  /** `Stack.empty()` renamed to `isEmpty` — `empty` on a `Buffer` is the companion's factory. */
  private var isEmptySym: SymId = SymId.None
  /** `Option` members for `Kind.Opt` arms. */
  private var getSym, isDefinedSym, someSym, noneSym: SymId = SymId.None
  /** `JavaEnumMap.ofType` and `JavaEnumSet` static factory symbols (minted). */
  private var enumMapOfTypeSym: SymId = SymId.None
  private var enumSetSyms: Map[String, SymId] = Map.empty
  /** this run's symbol for a scala/shim FQN, or `SymId.None` where the program never names it. */
  private var byScalaSyms: Map[String, SymId] = Map.empty
  /** retarget target SymId to source FQN. Injective when sources have rewrite tables. */
  private var retargetTargetToSource: Map[SymId, String] = Map.empty
  /** FQN-based fallback: target FQN to set of source FQNs (ambiguity-aware). */
  private lazy val retargetTargetFqnToSources: Map[String, Set[String]] =
    retarget.groupMap(_._2)(_._1).view.mapValues(_.toSet).toMap
  /** Resolve retarget source FQN from a SymId (minted path, then FQN fallback). */
  private def retargetSourceOf(s: SymId)(using p: Program): Option[String] =
    retargetTargetToSource.get(s).orElse(
      p.symbolOf(s).flatMap { sym =>
        retargetTargetFqnToSources.get(sym.fullName).map(_.head)
          // FQN fallback 2: un-remapped source symbol
          .orElse(effectiveRetarget.get(sym.fullName).map(_ => sym.fullName))
      })
  /** True when the source FQN was resolved through `retargetTargetToSource` (the MINTED SymId —
    * unambiguous) rather than the FQN fallback (which may be ambiguous). */
  private def isUnambiguousSource(s: SymId): Boolean = retargetTargetToSource.contains(s)
  /** Declaring symbol to retarget source FQN — exact origin for rewrite table selection. */
  private var retargetDeclOrigin: Map[SymId, String] = Map.empty
  /** Extract result-type head SymId from a symbol's info (descends through MethodType/PolyType). */
  private def infoResultHead(info: TypeRepr): Option[SymId] = info match
    case TypeRepr.MethodType(_, result, _) => infoResultHead(result)
    case TypeRepr.PolyType(_, result)      => infoResultHead(result)
    case other                              => headSym(other)
  /** Resolve retarget source FQN from a receiver expression via `retargetDeclOrigin`. */
  private def resolveRecvOrigin(recv: Term): Option[String] =
    if retargetDeclOrigin.isEmpty then return scala.None
    recv match
      case id: Tree.Ident     => retargetDeclOrigin.get(id.sym)
      case sel: Tree.Select   => retargetDeclOrigin.get(sel.sym)
      case app: Tree.Apply    => retargetDeclOrigin.get(app.method)
      case ta: Tree.TypeApply  => resolveRecvOrigin(ta.fun)
      case b: Tree.Block       => b.stats.lastOption.collect { case t: Term => t }.flatMap(resolveRecvOrigin)
      case t: Tree.Typed       => resolveRecvOrigin(t.expr)
      case _                   => scala.None
  /** Look up retarget rewrite handling multi-source ambiguity. `recvOrigin` disambiguates. */
  private def lookupRewriteForReceiver(recvHeadSym: SymId, srcFqn: String,
      name: String, arity: Int, desc: Option[Descriptor],
      recvOrigin: Option[String] = None)(using Program): Option[CollectionsTransform.RetargetRewrite] =
    if isUnambiguousSource(recvHeadSym) then
      lookupRewrite(srcFqn, name, arity, desc)
    else
      // --- 3.1ap: if the receiver has a recorded origin, try that source FIRST ---
      recvOrigin.flatMap(origin => lookupRewrite(origin, name, arity, desc)).orElse {
        // FQN fallback — multiple sources may share this target.  Try each source's table.
        val targetFqn = retarget.getOrElse(srcFqn, "")
        val allSources = retargetTargetFqnToSources.getOrElse(targetFqn, Set(srcFqn))
        val answers = allSources.flatMap(src => lookupRewrite(src, name, arity, desc).map(src -> _))
        if answers.isEmpty then None
        else if answers.size == 1 then Some(answers.head._2)
        else
          // Multiple sources have entries — check if they all agree
          val distinct = answers.map(_._2).toSet
          if distinct.size == 1 then Some(distinct.head)
          else None // genuinely ambiguous — different sources want different rewrites
      }
  /** minted symbols for retarget rewrite target member names: `(sourceFqn, memberName)` -> SymId. */
  private var retargetRewriteSyms: Map[(String, String), SymId] = Map.empty
  /** source SymId -> arg mapping, for arity-changing retargets (keyed by the ORIGINAL symbol). */
  private var retargetArgsBySource: Map[SymId, List[CollectionsTransform.RetargetArg]] = Map.empty
  /** target (minted) SymId -> arg mapping, for the AppliedType case in transformType. */
  private var retargetArgsByTarget: Map[SymId, List[CollectionsTransform.RetargetArg]] = Map.empty
  /** minted SymIds for FixedType FQNs in retargetTypeArgs. */
  private var retargetFixedTypeSyms: Map[String, SymId] = Map.empty
  /** retarget target SymIds whose source is an Entry-like type (mapped to Tuple2). Used by
    * [[retargetSelectRewrite]] to fire `.key -> ._1` / `.value -> ._2` by SYMBOL, not by name. */
  private var retargetEntryTargets: Set[SymId] = Set.empty
  /** SOURCE member SymIds for IndexedField entries — the `items` field SymId on each retarget
    * source type. Used by [[retargetIndexedField]] to match the member by SYMBOL after the
    * bottom-up traversal has already visited (and potentially remapped) the `Select` node.
    * Keyed on `(ownerFqn, fieldName)` -> source SymId, so we identify the source FQN for the
    * rewrite table lookup. */
  private var indexedFieldSyms: Map[SymId, String] = Map.empty
  private def byScalaSym(fqn: String): SymId = byScalaSyms.getOrElse(fqn, SymId.None)
  private def enumSetSym(n: String): SymId   = enumSetSyms.getOrElse(n, SymId.None)
  /** java 8 `Collection.forEach(Consumer)` — scala's is `foreach`, differing only in case, which
    * makes the failure read like a typo rather than a missing mapping. */
  private var foreachSym: SymId = SymId.None
  private var key1Sym, value2Sym, selfParamSym: SymId = SymId.None
  /** Bound method-ref receiver binding and lambda argument parameter symbols (max arity 2). */
  private var recvBindSym: SymId = SymId.None
  private var argParamSyms: Vector[SymId] = Vector.empty
  /** Monotonic counter for unique lambda parameter names in [[retargetForEach]]. */
  private var forEachSeq: Int = 0
  private var forEachKeyPool: Array[SymId] = Array.empty
  private var forEachValPool: Array[SymId] = Array.empty
  private var forEachElemPool: Array[SymId] = Array.empty
  /** sequence counter for return-boundary labels in [[retargetForEach]]. */
  private var retFeSeq: Int = 0
  /** sequence counter for collect-block temp variables in [[emitCollect]]. */
  private var collectSeq: Int = 0
  /** set to `true` during the Collect post-pass so [[collectPhase]] fires `emitCollect`. */
  private var collectPassActive: Boolean = false
  /** Collect blocks whose original receiver is a map and whose `.iterator()` should produce a
    * REMOVING iterator — keyed on the Opaque block's identity (same identity model as
    * `selectChainRewritten`). Value is `(originalReceiver, srcFqn, collectVia)` so the
    * iterator wrapping can emit a removing iterator that removes from the ORIGINAL map by key,
    * rather than a read-only wrapper over the snapshot. */
  private val collectBlockReceivers: java.util.IdentityHashMap[AnyRef, (Term, String, String)] =
    new java.util.IdentityHashMap()

  /** Post-pass phase: rewrites standalone `keys()`/`values()` on retarget targets into collect
    * blocks, and strips `()` from calls chained on a Collect result. */
  private val collectPhase: Phase = new Phase:
    def name = "retarget-collect"
    override def transformApply(t: Tree.Apply)(using p: Program): Term =
      t.fun match
        case Tree.Select(recv, m, _, so) =>
          // First: try to rewrite the call itself as a Collect
          // --- 3.1ap: receiver-origin disambiguation via lookupRewriteForReceiver ---
          val recvHead = headSym(recv.tpe)
          val collectResult = recvHead.flatMap(retargetSourceOf).flatMap { srcFqn =>
            val mName = methodName(m)
            val rhs = recvHead.getOrElse(SymId.None)
            lookupRewriteForReceiver(rhs, srcFqn, mName, 0, None, resolveRecvOrigin(recv)).flatMap {
              case rw: CollectionsTransform.RetargetRewrite.Collect =>
                emitCollect(recv, srcFqn, rw, so)
              case _ => scala.None
            }
          }
          if collectResult.isDefined then collectResult.get
          // Second: strip empty parens from calls chained on a Collect block, unless
          // `toArray`/`iterator`, whose scala return type is not what the caller expects.
          else recv match
            case _: Tree.Opaque if t.args.isEmpty =>
              val mName = methodName(m)
              if mName == "toArray" then
                // When the ORIGINAL type head is a retarget target, the caller expects a
                // DynamicArray, not a scala.Array. Return the Collect block as-is.
                val retargetTargetFqns = retarget.values.toSet
                headSym(t.tpe) match
                  case Some(h) if p.symbolOf(h).exists(s => retargetTargetFqns(s.fullName)) => recv
                  case Some(h) if retargetTargetToSource.contains(h) => recv
                  case Some(h) if remap.contains(h) && retargetTargetToSource.contains(remap(h)) => recv
                  case _ => Tree.Select(recv, m, t.tpe, so)
              else if mName == "iterator" && iteratorFromSym != SymId.None && javaIteratorSym != SymId.None then
                // Wrap .iterator with JavaIterator.from when the caller expects JavaIterator.
                // K36: if this Collect block's receiver is tracked, emit a REMOVING iterator
                // that removes from the original MAP by key rather than wrapping the read-only
                // DynamicArray snapshot.
                headSym(t.tpe) match
                  case Some(h) if (h == javaIteratorSym || (remap.contains(h) && remap(h) == javaIteratorSym) ||
                      p.symbolOf(h).exists(s => s.fullName == "java.util.Iterator" || s.fullName == "balticporter.runtime.JavaIterator")) &&
                      collectBlockReceivers.containsKey(recv) =>
                    val (mapRecv, srcFqn, via) = collectBlockReceivers.get(recv)
                    emitRemovingIteratorForCollect(mapRecv, srcFqn, via, t.tpe, so)
                  case Some(h) if h == javaIteratorSym || (remap.contains(h) && remap(h) == javaIteratorSym) ||
                      p.symbolOf(h).exists(s => s.fullName == "java.util.Iterator" || s.fullName == "balticporter.runtime.JavaIterator") =>
                    val iterSelect = Tree.Select(recv, m, TypeRepr.NoType, so)
                    Tree.Apply(Tree.Ident(iteratorFromSym, TypeRepr.NoType, so),
                               List(iterSelect), iteratorFromSym, t.tpe, so)
                  case _ => Tree.Select(recv, m, t.tpe, so)
              else
                // Strip parens ONLY when the Opaque's own type head is a retarget target —
                // i.e. it was produced by a Collect whose `into` type is DynamicArray or similar.
                // A Template-produced Opaque has the ORIGINAL call's return type (e.g. GroupPlug),
                // and chained calls on that type must keep their parens. 3.1ai: measured at 1 gdx
                // error (afterGroup must be called with () argument) without this guard.
                val isCollectBlock = headSym(recv.tpe).exists(h =>
                  retargetTargetToSource.contains(h) ||
                  p.symbolOf(h).exists(s => retarget.values.toSet(s.fullName)))
                if isCollectBlock then Tree.Select(recv, m, t.tpe, so) else t
            case _ => t
        case _ => t
  /** Apply nodes produced by [[retargetForEach]] that need a value-carrying boundary wrapper.
    * Keyed on the Apply's identity (the object itself); value is the label name used for the
    * `boundary.break` calls inside the lambda body. [[transformDefDef]] reads this to wrap
    * the Apply + its sibling Return in a `boundary[R]`. */
  private var retFeReturnApplies: java.util.IdentityHashMap[Term, String] = new java.util.IdentityHashMap()
  /** the scala side of a BRIDGED member (`ENGINE-LIMITS.md` K28.1) — the types its signature is
    * written in, and the two `asScala` views its body reaches java's answer through.
    * `iteratorMemberSym` is scala's parameterless `iterator`, for a `Map` with no java
    * `iterator()` reaching `entrySet().iterator()`. Resolved-or-minted like `unsupportedOpSym`. */
  private var optionSym, scalaIteratorSym, scalaIterableSym, iterableOnceSym: SymId = SymId.None
  private var tuple2Sym, boolSym, intSym, unitSym: SymId = SymId.None
  private var asScalaIteratorSym, asScalaIterableSym, iteratorMemberSym: SymId = SymId.None
  private var unitTpe: TypeRepr = TypeRepr.NoType

  /** `JavaIterable` + its `from` factory — see `coerce`. */
  private var javaIterableSym, iterableFromSym: SymId = SymId.None
  /** `JavaCollection` + its `from` factory — the same seam, one type up. `unmodifiableFromSym` is
    * the read-only sibling (a `Map.values()` view); `unmodifiableSym` is
    * `Collections.unmodifiableCollection`. */
  private var javaCollectionSym, collectionFromSym: SymId = SymId.None
  /** the `Kind.Set` source's factory into a `JavaCollection` slot — a DISTINCT NAME rather than an
    * overload of `from`, for the reason `JavaCollection.unmodifiableFrom` gives: an overload
    * resolves on the static type, and every candidate here is a `scala.collection.Iterable`. */
  private var collectionFromSetSym: SymId = SymId.None
  private var unmodifiableFromSym, unmodifiableSym: SymId = SymId.None
  /** each scala collection symbol → its companion's `from` factory, for `copyConstructor`. */
  private var fromSyms: Map[SymId, SymId] = Map.empty
  /** each HASHED scala collection symbol → its companion's `defaultLoadFactor`, for
    * [[capacityConstructor]]. Keyed on the phase's OWN targets, exactly as `fromSyms` is. */
  private var loadFactorSyms: Map[SymId, SymId] = Map.empty
  /** `JavaCollections`' statics, by name — see `sym`. */
  private var staticSyms: Map[String, SymId] = Map.empty
  /** the `java.util.stream` collapse — see `staticRewrite`. */
  private var asScalaBufferSym, filteredSym: SymId = SymId.None
  /** scala's own `toBuffer` — how a `Kind.Set` or `Kind.Map` stream SOURCE reaches the `Buffer`
    * every collapsed operation is declared over. See `streamSource`. */
  private var toBufferSym: SymId = SymId.None
  /** `mutable.Buffer`, so a collapsed stream can be TYPED as what it now emits. */
  private var bufferSym: SymId = SymId.None
  /** scala's own `sum` — a plain MEMBER name on the collapsed buffer, not a `JavaCollections` helper. */
  private var sumSym: SymId = SymId.None
  /** scala's own `map` — a plain member on a collapsed buffer, for the stream chain. */
  private var mapSym: SymId = SymId.None
  /** scala's own `exists`/`forall` — java's `anyMatch`/`allMatch`, which mean exactly these. */
  private var existsSym, forallSym: SymId = SymId.None
  /** `JavaIterator.from` — the `iterator` counterpart of `wrapIterableArgs`. */
  private var iteratorFromSym, javaIteratorSym: SymId = SymId.None
  /** `JavaListIterator` and its write-through cursor `JavaListIterator.over` — the `listIterator`
    * rewrite's target (`ENGINE-LIMITS.md` K23). `SymId.None` unless the program names
    * `java.util.ListIterator`, so the arm declines by arithmetic everywhere else. */
  private var javaListIteratorSym, listIteratorOverSym: SymId = SymId.None
  /** `JavaCollections.{spliterator, orderedSpliterator, distinctSpliterator}` — java's THREE own
    * defaults for `spliterator()`, one per owner it re-declares the member at
    * (`ENGINE-LIMITS.md` K23). Three symbols and not one, because the emitted call has to NAME which
    * java declaration it reproduces rather than carry a characteristics constant. */
  private var orderedSpliteratorSym, distinctSpliteratorSym: SymId = SymId.None
  /** the mapping targets `JavaCollections.fromJava` can actually PRODUCE — see
    * [[CollectionsTransform.liveWrappable]], read as symbols so [[externalProducer]] asks a
    * membership question about what this run minted rather than a question about a name. EMPTY when
    * the program names none of them, which makes the wrap arm decline by arithmetic. */
  private var liveWrappableSyms: Set[SymId] = Set.empty

  /** each mapping target this run named → the `JavaCollections.Reified` member that answers java's
    * `instanceof` / performs java's cast at it. Keyed on `byScala`, so a target the program never
    * names is simply absent and the reified arms decline by arithmetic — the same shape
    * [[liveWrappableSyms]] takes, and for the same reason (§4.56: the phase's own record). */
  private var reifiedIsSyms, reifiedAsSyms: Map[SymId, SymId] = Map.empty

  /** every symbol THIS PROGRAM names that is an unmapped SUPERTYPE of a type this phase retypes —
    * see [[unmappedReified]]. EMPTY where the program names none of them, which makes the refusal
    * decline by arithmetic exactly as the two maps above do. */
  private var unmappedSupertypeSyms: Set[SymId] = Set.empty

  /** did a REIFIED occurrence get translated inside the declaration currently being closed? The
    * traversal is bottom-up, so this is set at the rewrite and drained at the nearest enclosing
    * `DefDef`/`ValDef` — a citation is per DECLARATION (§5.1). */
  private var reifiedHere: Boolean = false

  // ---- the RuleScope's own record, for THIS run (see `applyScope`) ----

  /** `JavaCollections.fromJava` / `toJava` — the EXTERNAL seam's two directions. */
  private var fromJavaSym, toJavaSym, toStreamSym: SymId = SymId.None

  /** java's three `Object`-keyed map members, and the two `Object`-keyed collection ones, for a
    * receiver or an ARGUMENT at which the element type cannot be named — see [[objectProbe]]. */
  private var mapGetSym, mapContainsKeySym, mapRemoveSym: SymId = SymId.None
  private var setContainsSym, setRemoveSym: SymId               = SymId.None

  /** the java symbols this run's mapping sends to a target that CANNOT BE A PARENT — see
    * [[restoreUninheritableParents]]. EMPTY unless the program actually names one, which makes the
    * pass a no-op by arithmetic on every port that does not. */
  private var uninheritableSyms: Set[SymId] = Set.empty

  /** …and the classes among them whose value at the TARGET's own slot really is a detached one,
    * with the target FQN it may be projected to — see [[detachedEntriesIn]]. EMPTY unless the
    * program declares such a class AND the class already refuses the write, so the projection
    * declines by arithmetic on every port that does neither. */
  private var detachedEntries: Map[SymId, String] = Map.empty

  /** `JavaCollections.entryToPair` — the projection [[detachedEntries]] licenses. */
  private var entryToPairSym: SymId = SymId.None

  /** `java.lang.UnsupportedOperationException`, as this run's own type — see
    * [[CollectionsTransform.UnsupportedOnTarget]]. */
  private var unsupportedOpTpe: TypeRepr = TypeRepr.NoType
  private var unsupportedOpSym: SymId    = SymId.None
  private var stringTpe: TypeRepr        = TypeRepr.NoType

  /** `java.lang.Object` as THIS run's symbol — the top of java's reference hierarchy, and the one
    * type an argument can carry that conforms to no scala element type at all ([[objectProbe]]). */
  private var objectSym: SymId = SymId.None

  /** is this symbol one the PROGRAM declares? Structural (`Program.owned`), never a name test
    * (§4.56), and computed once per run because the external-seam arms ask it per call. */
  private var ownedSym: SymId => Boolean = _ => true

  /** every symbol THIS PHASE minted in [[run]] — the rewrites' own targets. They are owned by
    * nothing and named by no class file, so the external-seam arms would otherwise read each of
    * them as a third party's method. */
  private var mintedSyms: Set[SymId] = Set.empty

  /** every external seam this run could NOT close, in the order it met them. Reported through
    * [[boundary]], because it is the same residue `CollectionBoundaryCheck` counts and a reader
    * looking for "what did the retyping leave open" must find all of it in one place. */
  private val externalSeams = collection.mutable.ListBuffer[CollectionBoundaryCheck.Finding]()

  /** every symbol this run's [[scope]] held OUT of the rewrite. EMPTY for the default scope — and
    * for any scope whose entries matched nothing — which is what makes the no-op a no-op. */
  private var excluded: Set[SymId] = Set.empty

  /** …read back, so [[CollectionBoundaryCheck]] can classify a seam the scope created from the
    * phase's OWN record of what it held back rather than guessing from a type name (§4.56). */
  def scopedOut: Set[SymId] = excluded

  /** every member this run held back because it OVERRIDES A CLASS FILE — see
    * [[classFileOverridesIn]]. EMPTY for a program that extends no unconverted java type, which is
    * what makes this a no-op by arithmetic exactly as an unrestricted scope is. */
  private var retainedOverrides: Set[SymId] = Set.empty

  /** …plus the PARAMETER symbols of those members, because a tree that says `java.util.Collection`
    * over a symbol that says `JavaCollection` is the lie [[mapSignatures]] already refuses to
    * write for the scope. Held apart from [[classFileOverrides]] so the boundary check keys on
    * MEMBERS and nothing else. */
  private var retainedOwners: Set[SymId] = Set.empty

  /** …read back, for [[scopedOut]]'s reason and with a DIFFERENT classification: the scope's seam
    * names a manifest key, and this one names nothing a port can edit (§4.56). */
  def classFileOverrides: Set[SymId] = retainedOverrides

  /** the union — every declaration whose type this run reads LITERALLY, whichever refusal held it.
    * `isEmpty` is the pre-refusal code path by arithmetic, which both halves need. */
  private def literal(s: SymId): Boolean = excluded(s) || retainedOverrides(s) || retainedOwners(s)
  private def literalEmpty: Boolean      = excluded.isEmpty && retainedOverrides.isEmpty

  /** the declaration → the scope ENTRY that admitted it, for `Reason.Configured`'s key (§4.575:
    * the key is the manifest entry VERBATIM, because it is the string an agent edits). */
  private var admittedBy: Map[SymId, String] = Map.empty

  private var report: PolicyReport = PolicyReport.empty

  /** the setting every retarget finding is filed under — the string an agent greps for (§4.575). */
  private val RetargetSetting = "CollectionsTransform(retarget) entry"

  /** …and the same for a reified carrier. */
  private val CarrierSetting = "CollectionsTransform(reifiedCarriers) entry"

  /** …and for a reflective sink. */
  private val SinkSetting = "CollectionsTransform(reflectiveSinks) entry"

  /** …and for a collection family entry. */
  private val FamilySetting = "CollectionsTransform(families) entry"

  /** the carriers that actually RUN — what the port declared plus the one java guarantees. A `val`,
    * so [[preservesTypeArgsOf]], the recorder and the fingerprint cannot disagree. */
  private val effectiveCarriers: Set[String] = reifiedCarriers ++ CollectionsTransform.UniversalCarriers

  /** …resolved to THIS program's symbols, once per run. Read off `program.symbols` and not off the
    * mapping, for the reason [[unmappedSupertypeSyms]] states: a carrier is a type this phase leaves
    * alone, so its symbol keeps the id it arrived with. EMPTY where the program names none of them,
    * which is what makes every arm below a no-op by arithmetic. */
  private var carrierSyms: Set[SymId] = Set.empty

  /** the REFLECTIVE SINKS this program actually names, as this program's own symbols. EMPTY where
    * the port declares none, which is what makes the egress bridge a no-op with no code path. */
  private var sinkSyms: Set[SymId] = Set.empty

  /** `JavaCollections.Reified.toJavaValue` — the EGRESS bridge (K21 face 1). Minted like every
    * other `Reified` member: nothing in a java program declares it. */
  private var toJavaValueSym: SymId = SymId.None

  /** every (sink callee, declared sink FQN) the egress bridge actually fired on, drained into
    * `decisions.tsv` at the end of the run. A per-SITE rewrite recorded per DECLARATION (§5.1). */
  private val bridgedSinkCallees = collection.mutable.Set[(SymId, String)]()

  /** …and every external callee with an OPAQUE formal this port has NOT declared a sink, keyed by
    * (callee, JAVA FILE) with the earliest site in that file — the review list [[opaqueEgress]]
    * exists to publish. Keyed per-file, not globally, so D2's per-module filter applies AFTER the
    * site is chosen and a dependent's row does not vanish behind its base's earlier path. */
  private val opaqueEgressSites = collection.mutable.Map[(SymId, String), Origin]()

  /** Effective retarget entries (port-declared minus any `typeMap` collision). */
  private val effectiveRetarget: Map[String, String] =
    retarget.filterNot((k, _) => typeMap.contains(k))

  /** Retarget source to target map. Not folded into `mappedTypes`/`retypedTargets`. */
  def retargetedTypes: Map[String, String] = effectiveRetarget

  /** Policy report including never-fired scope entries and retarget collisions. */
  def policyReport: PolicyReport =
    report ++ PolicyReport.fromBindings(boundRetarget.toList.sortBy(_._1).map { (k, b) =>
      PolicyBinder.Record(name, RetargetSetting, k, b.forget)
    }) ++ PolicyReport.fromBindings(boundCarriers.toList.sortBy(_._1).map { (k, b) =>
      PolicyBinder.Record(name, CarrierSetting, k, b.forget)
    }) ++ PolicyReport.fromBindings(boundFamilies.toList.sortBy(_._1).map { (k, b) =>
      PolicyBinder.Record(name, FamilySetting, k, b.forget)
    }) ++ PolicyReport(
      retarget.keys.toList.sorted.filter(typeMap.contains).map { k =>
        PolicyFinding(name, RetargetSetting, k, PolicyIssue.Malformed,
          s"`$k` already has a COLLECTION mapping (-> ${targetOf(k)}), which retypes its call " +
            "shapes and bridges its slots as well as moving the type. A retarget entry does only " +
            "the last of those, so the two answers are not refinements of one another — the entry " +
            "is ignored and the collection mapping stands")
      })

  override def run(program: Program): Program =
    // the class index [[classTparams]] reads — built once, from the program as PARSED, because a
    // type parameter's symbol is one thing this phase never moves.
    classDefsBySym = program.units.flatMap(StandardTraversal.allClassDefs(_)(using program))
                            .map(cd => cd.symbol -> cd).toMap
    val added = collection.mutable.ListBuffer[Symbol]()
    var next  = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    def mint(name: String, full: String): SymId =
      val id = SymId(next); next += 1
      added += Symbol(id, name, full, Flags(), SymId.None, TypeRepr.NoType)
      id
    // one scala symbol per DISTINCT scala type (so two java types mapping to the same
    // scala type — e.g. Deque & ArrayDeque → ArrayDeque — share it and its kind).
    val byScala = collection.mutable.Map[String, SymId]()
    remap = program.symbols.all.flatMap { s =>
      // …RETARGET first, so a key the port also finds in `typeMap` cannot silently take the
      // collection answer: `effectiveRetarget` has already removed any such key and reported it.
      effectiveRetarget.get(s.fullName).map { sc =>
        // a source with its own rewrite table/type args gets a DISTINCT SymId, so multiple
        // sources sharing a target do not collapse onto one entry (§4.55: a loose key -> List).
        val needsOwnSym = retargetTypeArgs.contains(s.fullName) ||
          retargetRewrites.contains(s.fullName) || retargetRewritesByDesc.contains(s.fullName)
        val sym = if needsOwnSym then
          mint(sc.substring(sc.lastIndexOf('.') + 1), sc)
        else
          byScala.getOrElseUpdate(sc, mint(sc.substring(sc.lastIndexOf('.') + 1), sc))
        s.id -> sym
      }.orElse(typeMap.get(s.fullName).map(_._1).map { sc =>
        s.id -> byScala.getOrElseUpdate(sc, mint(sc.substring(sc.lastIndexOf('.') + 1), sc))
      })
    }.toMap
    // fullName -> minted SymId fallback for `transformType`: a dependent port may intern the
    // same java type under a SECOND SymId not in `remap`, so fall back to the source's fullName.
    remapByFullName = program.symbols.all.flatMap { s =>
      remap.get(s.id).map(tgt => s.fullName -> tgt)
    }.toMap
    // …and the reverse map for per-entry family scoping (D12): which remap entries came from
    // `families` (not the JDK companion typeMap, not retarget). Used by `finishRun` to narrow
    // the mapping per pass, so a dependent's family entries only retype its own declarations.
    familyRemapSources = program.symbols.all.flatMap { s =>
      if remap.contains(s.id) && families.contains(s.fullName) then Some(s.id -> s.fullName)
      else scala.None
    }.toMap
    kindOf = program.symbols.all.flatMap { s =>
      typeMap.get(s.fullName).map { case (sc, k) => byScala(sc) -> k }
    }.toMap
    opPlusEq     = mint("+=", "scala.<op>#+=")   // rendered infix by the emitter
    opMinusEq    = mint("-=", "scala.<op>#-=")
    opPlusPlusEq = mint("++=", "scala.<op>#++=")
    // Plain arithmetic operators for compound-FieldWrite expansion (`size -= 1` -> `setSize(size - 1)`).
    compoundOps  = Map("-" -> mint("-", "scala.<op>#-"), "+" -> mint("+", "scala.<op>#+"),
                       "*" -> mint("*", "scala.<op>#*"), "/" -> mint("/", "scala.<op>#/"),
                       "%" -> mint("%", "scala.<op>#%"), "|" -> mint("|", "scala.<op>#|"),
                       "&" -> mint("&", "scala.<op>#&"), "^" -> mint("^", "scala.<op>#^"),
                       "<<" -> mint("<<", "scala.<op>#<<"), ">>" -> mint(">>", "scala.<op>#>>"),
                       ">>>" -> mint(">>>", "scala.<op>#>>>"))
    updateSym    = mint("update", "update")
    insertSym    = mint("insert", "insert")
    getOrElseSym = mint("getOrElse", "getOrElse")
    containsSym  = mint("contains", "contains")
    key1Sym      = mint("_1", "_1") // Map.Entry#getKey   on a Tuple2
    value2Sym    = mint("_2", "_2") // Map.Entry#getValue on a Tuple2
    // the receiver parameter of a LOWERED unbound method reference — see [[lowerMethodRef]]. ONE
    // symbol serves every site, and that is a fact about the shape rather than a shortcut: the
    // lowered body is a single member access on the parameter, so it can contain no second lowered
    // reference and two of these lambdas can never nest. The name matches what `TirEmitter` already
    // spells for a method reference it expands itself, so the two paths read alike.
    selfParamSym = mint("self$", "self$")
    recvBindSym  = mint("recv$", "recv$")
    argParamSyms = (0 until 4).toVector.map(k => mint(s"a$k$$", s"a$k$$"))
    javaIterableSym = byScala.getOrElse(JavaIterableFqn, SymId.None)
    iterableFromSym = mint("from", JavaIterableFqn + ".from")
    javaCollectionSym   = byScala.getOrElse(JavaCollectionFqn, SymId.None)
    collectionFromSym   = mint("from", JavaCollectionFqn + ".from")
    collectionFromSetSym = mint("fromSet", JavaCollectionFqn + ".fromSet")
    unmodifiableFromSym = mint("unmodifiableFrom", JavaCollectionFqn + ".unmodifiableFrom")
    unmodifiableSym     = mint("unmodifiable", JavaCollectionFqn + ".unmodifiable")
    // `asScalaBuffer` is an EXTENSION in JavaCollection's companion, which is exactly where scala 3
    // looks for one on that receiver type — so it needs no import, like every other name the
    // structural backend emits fully qualified (CLAUDE.md §6).
    asScalaBufferSym    = mint("asScalaBuffer", JavaCollectionFqn + ".asScalaBuffer")
    filteredSym         = mint("filtered", JavaCollectionFqn + ".filtered")
    bufferSym           = byScala.getOrElse("scala.collection.mutable.Buffer", SymId.None)
    sumSym              = mint("sum", "sum")
    mapSym              = mint("map", "map")
    existsSym           = mint("exists", "exists")
    forallSym           = mint("forall", "forall")
    toBufferSym         = mint("toBuffer", "toBuffer")
    staticSyms = CollectionsTransform.StaticHelpers
      .map(n => n -> mint(n, s"$JavaCollectionsFqn.$n")).toMap
    // one `from` per DISTINCT scala target, so `new ArrayList<>(c)` copies through the companion the
    // target type actually has. `Tuple2` is excluded: it is a `Kind.Entry`, not a collection, and
    // `Tuple2.from` does not exist — the `kindOf` gate in `copyConstructor` never offers it one.
    fromSyms = byScala.collect {
      // …and the RUNTIME targets that publish a `from` of their own. Listed rather than matched on
      // the package, because "is this one of mine" is a membership test against the phase's own
      // record and a prefix is not a structural fact about anything (§4.56). `JavaEnumMap` is the
      // only one: `JavaStack`'s java type has no copy constructor and `JavaEnumSet`'s copy is a
      // STATIC, so neither is ever reached through a `new`.
      case (fqn, id) if fqn.startsWith("scala.collection.") || fqn == CollectionsTransform.JavaEnumMapFqn =>
        id -> mint("from", s"$fqn.from")
    }.toMap
    // The scala collections whose only paramful constructor is `(initialCapacity, loadFactor)`.
    // Listed rather than derived because there is nothing in the TIR to derive it FROM — these are
    // external types with no declaration the frontend ever saw — but the list is closed over the
    // phase's own `typeMap` targets, so it is the phase's record and not a name test (§4.56).
    loadFactorSyms = List(
      "scala.collection.mutable.HashMap", "scala.collection.mutable.LinkedHashMap",
      "scala.collection.mutable.HashSet", "scala.collection.mutable.LinkedHashSet",
    ).flatMap(fqn => byScala.get(fqn).map(_ -> mint("defaultLoadFactor", s"$fqn.defaultLoadFactor"))).toMap
    iteratorFromSym = mint("from", JavaIteratorFqn + ".from")
    javaIteratorSym = byScala.getOrElse(JavaIteratorFqn, SymId.None)
    javaListIteratorSym = byScala.getOrElse(CollectionsTransform.JavaListIteratorFqn, SymId.None)
    listIteratorOverSym = mint("over", CollectionsTransform.JavaListIteratorFqn + ".over")
    orderedSpliteratorSym  = mint("orderedSpliterator", JavaCollectionsFqn + ".orderedSpliterator")
    distinctSpliteratorSym = mint("distinctSpliterator", JavaCollectionsFqn + ".distinctSpliterator")
    // …the five targets a LIVE view exists for, as this run's own symbols. Keyed on `byScala`, so a
    // target the program never names is simply absent and the wrap declines by arithmetic.
    liveWrappableSyms = byScala.collect {
      case (fqn, id) if CollectionsTransform.liveWrappable(fqn) => id
    }.toSet
    // …and the REIFIED pair per target the program names. Two symbols each, minted rather than
    // resolved: nothing in a java program declares `JavaCollections.Reified`.
    reifiedIsSyms = byScala.collect {
      case (fqn, id) if CollectionsTransform.reifiedHelper.contains(fqn) =>
        val n = "is" + CollectionsTransform.reifiedHelper(fqn)
        id -> mint(n, s"${CollectionsTransform.ReifiedFqn}.$n")
    }.toMap
    reifiedAsSyms = byScala.collect {
      case (fqn, id) if CollectionsTransform.reifiedHelper.contains(fqn) =>
        val n = "as" + CollectionsTransform.reifiedHelper(fqn)
        id -> mint(n, s"${CollectionsTransform.ReifiedFqn}.$n")
    }.toMap
    // …and the targets a reified occurrence can name that this phase did NOT retype, as symbols of
    // THIS program — see [[unmappedReified]]. Read off `program.symbols` and not off the mapping,
    // because these are types the phase leaves alone: their symbols keep the ids they arrived with.
    unmappedSupertypeSyms = program.symbols.all.collect {
      case s if CollectionsTransform.unmappedSupertypes(s.fullName) => s.id
    }.toSet
    // …and the REIFIED CARRIERS this program actually names (K20). Resolved BEFORE the traversal
    // starts, because `preservesTypeArgsOf` is asked from inside it.
    carrierSyms = program.symbols.all.collect {
      case s if effectiveCarriers(s.fullName) => s.id
    }.toSet
    // …and the REFLECTIVE SINKS (K21), read the same way and for the same reason: a sink is a type
    // this phase leaves alone, so its symbol keeps the id it arrived with.
    sinkSyms = program.symbols.all.collect {
      case s if reflectiveSinks(s.fullName) => s.id
    }.toSet
    toJavaValueSym = mint("toJavaValue", s"${CollectionsTransform.ReifiedFqn}.toJavaValue")
    foreachSym          = mint("foreach", "foreach")
    forEachSeq          = 0
    collectSeq          = 0
    // 64 entries — never wraps; libGDX core uses ~30 forEach rewrites across the whole port.
    // An assertion in retargetForEach guards the upper bound rather than silently shadowing.
    forEachKeyPool      = (0 until 64).map(i => mint(s"k$$fe$i", s"k$$fe$i")).toArray
    forEachValPool      = (0 until 64).map(i => mint(s"v$$fe$i", s"v$$fe$i")).toArray
    forEachElemPool     = (0 until 64).map(i => mint(s"x$$fe$i", s"x$$fe$i")).toArray
    removeHeadOptionSym = mint("removeHeadOption", "removeHeadOption")
    headOptionSym       = mint("headOption", "headOption")
    orNullSym           = mint("orNull", "orNull")
    prependSym          = mint("prepend", "prepend")
    isEmptySym          = mint("isEmpty", "isEmpty")
    getSym              = mint("get", "get")
    isDefinedSym        = mint("isDefined", "isDefined")
    someSym             = mint("Some", "scala.Some")
    noneSym             = mint("None", "scala.None")
    byScalaSyms         = byScala.toMap
    // retarget rewrite wiring: build reverse map from target SymId to source FQN, and mint
    // symbols for each rewrite target member name.
    // retarget reverse map: target SymId -> source FQN. Built from `remap` so that per-source
    // minted symbols (arity-changing retargets) are included — `byScala` does not hold those.
    retargetTargetToSource = program.symbols.all.flatMap { s =>
      effectiveRetarget.get(s.fullName).flatMap(_ => remap.get(s.id).map(tgtSym => tgtSym -> s.fullName))
    }.toMap
    retargetEntryTargets = program.symbols.all.flatMap { s =>
      effectiveRetarget.get(s.fullName).filter(CollectionsTransform.UninheritableTargets.contains)
        .flatMap(_ => remap.get(s.id))
    }.toSet

    // IndexedField: collect the SOURCE member SymIds for field names in IndexedField entries.
    // The bottom-up traversal visits the inner Select BEFORE the ArrayAccess, so by the time
    // retargetIndexedField fires, the Select's member symbol may have been remapped. We match on
    // the ORIGINAL source member SymId (the field declared by the source type), keyed to its
    // source FQN so we can look up the rewrite table.
    indexedFieldSyms = retargetRewrites.flatMap { (srcFqn, tbl) =>
      tbl.collect { case ((fieldName, 0), _: CollectionsTransform.RetargetRewrite.IndexedField) =>
        // find the source type's SymId and then its member with this name
        program.symbols.all.filter(s => s.fullName == srcFqn).flatMap { ownerSym =>
          program.symbols.all.filter(m => m.owner == ownerSym.id && m.name == fieldName)
            .map(m => m.id -> srcFqn)
        }
      }.flatten
    }

    // resolve FixedType and Applied FQNs — reuse an EXISTING symbol where one is already in
    // byScala or in the program, so no FQN ends up with two SymIds. 3.1ai / O9: minting a
    // duplicate `scala.Int` gives `SymbolTable` two entries with the same `fullName`, and any
    // phase resolving a primitive by `fullName` may bind the wrong one.
    // 3.1aw-3: Applied entries contribute their OWN FQN (the type constructor) to the same pool.
    def collectFqns(arg: CollectionsTransform.RetargetArg): Set[String] = arg match
      case CollectionsTransform.RetargetArg.FixedType(fqn) => Set(fqn)
      case CollectionsTransform.RetargetArg.Applied(fqn, inner) =>
        Set(fqn) ++ inner.flatMap(collectFqns)
      case _ => Set.empty
    retargetFixedTypeSyms = retargetTypeArgs.values.flatten.flatMap(collectFqns).toSet.map { fqn =>
      val sym = byScala.getOrElseUpdate(fqn, {
        // check program symbols before minting — the frontend may already have this FQN
        program.symbols.all.find(_.fullName == fqn).map(_.id)
          .getOrElse(mint(fqn.substring(fqn.lastIndexOf('.') + 1), fqn))
      })
      fqn -> sym
    }.toMap

    // build per-source and per-target arg mappings
    retargetArgsBySource = program.symbols.all.flatMap { s =>
      retargetTypeArgs.get(s.fullName).map(args => s.id -> args)
    }.toMap
    retargetArgsByTarget = retargetArgsBySource.flatMap { (srcId, args) =>
      remap.get(srcId).map(tgtId => tgtId -> args)
    }
    retargetRewriteSyms = retargetRewrites.flatMap { (src, tbl) =>
      tbl.values.flatMap {
        case CollectionsTransform.RetargetRewrite.Rename(target) =>
          List((src, target) -> mint(target, s"$src#retargetRewrite:$target"))
        case CollectionsTransform.RetargetRewrite.BoolDispatch(_, onTrue, onFalse) =>
          List(
            (src, onTrue)  -> mint(onTrue, s"$src#retargetRewrite:$onTrue"),
            (src, onFalse) -> mint(onFalse, s"$src#retargetRewrite:$onFalse"))
        case CollectionsTransform.RetargetRewrite.Construct(companionFqn, factoryMethod, _, _) =>
          val fqn = s"$companionFqn.$factoryMethod"
          List((src, fqn) -> mint(factoryMethod, fqn))
        case CollectionsTransform.RetargetRewrite.ForEach(targetMethod, _) =>
          List((src, targetMethod) -> mint(targetMethod, s"$src#retargetRewrite:$targetMethod"))
        case CollectionsTransform.RetargetRewrite.Collect(via, _) =>
          List((src, via) -> mint(via, s"$src#retargetRewrite:$via"))
        case CollectionsTransform.RetargetRewrite.Chain(members, _, _) =>
          members.map(m => (src, m) -> mint(m, s"$src#retargetRewrite:$m"))
        case CollectionsTransform.RetargetRewrite.FieldWrite(_, method) =>
          List((src, method) -> mint(method, s"$src#retargetRewrite:$method"))
        case _: CollectionsTransform.RetargetRewrite.IndexedField =>
          Nil // no minted symbol needed — the field select is stripped, not renamed
        case _: CollectionsTransform.RetargetRewrite.Template =>
          Nil // no minted symbol needed — the template is rendered as Opaque text
      }
    } ++ retargetRewritesByDesc.flatMap { (src, tbl) =>
      tbl.values.flatMap {
        case CollectionsTransform.RetargetRewrite.Construct(companionFqn, factoryMethod, _, _) =>
          val fqn = s"$companionFqn.$factoryMethod"
          List((src, fqn) -> mint(factoryMethod, fqn))
        case CollectionsTransform.RetargetRewrite.Rename(target) =>
          List((src, target) -> mint(target, s"$src#retargetRewrite:$target"))
        case CollectionsTransform.RetargetRewrite.BoolDispatch(_, onTrue, onFalse) =>
          List(
            (src, onTrue)  -> mint(onTrue, s"$src#retargetRewrite:$onTrue"),
            (src, onFalse) -> mint(onFalse, s"$src#retargetRewrite:$onFalse"))
        case CollectionsTransform.RetargetRewrite.ForEach(targetMethod, _) =>
          List((src, targetMethod) -> mint(targetMethod, s"$src#retargetRewrite:$targetMethod"))
        case CollectionsTransform.RetargetRewrite.Collect(via, _) =>
          List((src, via) -> mint(via, s"$src#retargetRewrite:$via"))
        case CollectionsTransform.RetargetRewrite.Chain(members, _, _) =>
          members.map(m => (src, m) -> mint(m, s"$src#retargetRewrite:$m"))
        case CollectionsTransform.RetargetRewrite.FieldWrite(_, method) =>
          List((src, method) -> mint(method, s"$src#retargetRewrite:$method"))
        case _: CollectionsTransform.RetargetRewrite.IndexedField => Nil
        case _: CollectionsTransform.RetargetRewrite.Template => Nil
      }
    }
    enumMapOfTypeSym    = mint("ofType", s"${CollectionsTransform.JavaEnumMapFqn}.ofType")
    enumSetSyms = List("noneOf", "allOf", "of", "copyOf", "range", "complementOf")
      .map(n => n -> mint(n, s"${CollectionsTransform.JavaEnumSetFqn}.$n")).toMap
    putSym       = mint("put", "put")     // scala `mutable.Map.put`: returns the PREVIOUS value
    removeSym    = mint("remove", "remove") // scala `mutable.Map.remove`: returns the REMOVED value
    entryToPairSym = staticSyms.getOrElse("entryToPair", SymId.None)
    fromJavaSym  = staticSyms.getOrElse("fromJava", SymId.None)
    toJavaSym    = staticSyms.getOrElse("toJava", SymId.None)
    toStreamSym  = staticSyms.getOrElse("toStream", SymId.None)
    mapGetSym         = staticSyms.getOrElse("mapGet", SymId.None)
    mapContainsKeySym = staticSyms.getOrElse("mapContainsKey", SymId.None)
    mapRemoveSym      = staticSyms.getOrElse("mapRemove", SymId.None)
    setContainsSym    = staticSyms.getOrElse("setContains", SymId.None)
    setRemoveSym      = staticSyms.getOrElse("setRemove", SymId.None)
    // …the refusal a RETAINED PARENT's own contract prescribes (`UnsupportedOnTarget`). Resolved
    // from the program where it already holds the symbol and minted only where it does not: two
    // symbols for one FQN print the same text and compare unequal, which is how a later reader ends
    // up asking about a type this run has twice.
    def named(fqn: String, nm: String): SymId =
      // 3.1ai / O9: check `byScala` too — a FixedType resolution may have already minted a symbol
      // for this FQN (e.g. `scala.Int`), and a second mint gives `SymbolTable` two entries with the
      // same `fullName`. Minting duplicates is the root cause of textra's 58 Align opaque errors.
      byScala.get(fqn).orElse(program.symbols.all.find(_.fullName == fqn).map(_.id))
        .getOrElse(mint(nm, fqn))
    // …the BRIDGED members' own vocabulary (K28.1). `named` for every type, minted for the two
    // `asScala` views and for scala's `iterator`, which nothing in a java program declares.
    optionSym          = named("scala.Option", "Option")
    scalaIteratorSym   = named("scala.collection.Iterator", "Iterator")
    scalaIterableSym   = named("scala.collection.Iterable", "Iterable")
    iterableOnceSym    = named("scala.collection.IterableOnce", "IterableOnce")
    tuple2Sym          = named("scala.Tuple2", "Tuple2")
    boolSym            = named("scala.Boolean", "Boolean")
    intSym             = named("scala.Int", "Int")
    unitSym            = named("scala.Unit", "Unit")
    unitTpe            = TypeRepr.TypeRef(TypeRepr.NoPrefix, unitSym)
    asScalaIteratorSym = mint("asScala", JavaIteratorFqn + ".asScala")
    asScalaIterableSym = mint("asScala", JavaIterableFqn + ".asScala")
    iteratorMemberSym  = mint("iterator", "iterator")
    unsupportedOpSym = named(CollectionsTransform.UnsupportedOperationFqn, "UnsupportedOperationException")
    unsupportedOpTpe = TypeRepr.TypeRef(TypeRepr.NoPrefix, unsupportedOpSym)
    stringTpe        = TypeRepr.TypeRef(TypeRepr.NoPrefix, named("java.lang.String", "String"))
    objectSym        = named("java.lang.Object", "Object")
    externalSeams.clear()
    implicitPending.clear()
    bridgedSinkCallees.clear()

    mintedSyms = added.map(_.id).toSet
    val symbols = SymbolTable(program.symbols.all ++ added)
    given Program = program.rebuilt(symbols = symbols)
    // …resolved once. The external-seam arms ask it per CALL, and `Program.owned` walks an owner
    // chain, so asking it inside the traversal would be quadratic on a library of any size.
    val ownedNow = summon[Program].owned
    ownedSym = ownedNow
    applyScope(summon[Program]) // fills `excluded`, `admittedBy` and `report` — a no-op by default
    applyClassFileOverrides(summon[Program]) // …and the refusal no policy asked for
    uninheritableSyms = program.symbols.all.collect {
      case s if typeMap.get(s.fullName).exists((tgt, _) => CollectionsTransform.UninheritableTargets(tgt)) => s.id
    }.toSet
    // …and the half of that refusal that is not one. Read off the ORIGINAL units, before
    // `restoreUninheritableParents` retains a parent and before this phase substitutes any body:
    // the licence is the LIBRARY's own refusal to write, and a throw this phase wrote is not it.
    detachedEntries = detachedEntriesIn(summon[Program])
    parentClash = declaredParentKinds(summon[Program])
    superDefaults.clear()
    // …and the SURFACE the minted parent declares (K28.1). Planned here, where the java members
    // still carry java's names, and APPLIED as a rename before anything else reads the table: every
    // later step — the traversal, `mapSignatures`, `strippedOverrides` — must see the name the
    // emitted member will actually have, or two of them disagree about one declaration.
    bridges = planBridges(summon[Program])
    finishRun(program, renameBridgeDelegates(summon[Program]))

  /** the rest of [[run]], over the symbol table the bridge renames produced — a separate method
    * because a second `given Program` in one scope after the rename would be an ambiguity, not a
    * shadow. */
  private def finishRun(program: Program, symbols: SymbolTable): Program =
    given Program = program.rebuilt(symbols = symbols)
    ownedSym = summon[Program].owned

    // per-entry family scoping (D12): a family scoped non-Everywhere must only retype
    // declarations within that scope. One pass for JDK + everywhere-scoped families, then one
    // pass per distinct non-everywhere family scope with a narrowed remap.
    val fullRemap = remap
    remapTargets = fullRemap.values.toSet

    // built before the traversal retypes any tree: for every owned symbol whose result-type head
    // is a retarget source, record symbol -> source FQN, so a call-site rewrite can pick the right
    // table when several sources share a target (e.g. FloatArray/IntArray/Array -> DynamicArray).
    retargetDeclOrigin =
      if effectiveRetarget.isEmpty then Map.empty
      else
        val p = summon[Program]
        val buf = collection.mutable.Map[SymId, String]()
        p.symbols.all.foreach { s =>
          if p.owns(s.id) then
            infoResultHead(s.info).foreach { headId =>
              p.symbolOf(headId).foreach { hs =>
                if effectiveRetarget.contains(hs.fullName) then
                  buf(s.id) = hs.fullName
              }
            }
        }
        buf.toMap

    val scopedFamilyIds: Set[SymId] = familyRemapSources.collect {
      case (srcId, fqn) if !familyScopeOf(fqn).isUnrestricted => srcId
    }.toSet

    // pass 1: JDK entries + everywhere-scoped families (unrestricted, this is the full remap).
    remap = if scopedFamilyIds.isEmpty then fullRemap
            else fullRemap.filterNot { (k, _) => scopedFamilyIds(k) }
    var units: List[Tree.ClassDef] = program.units.map(u =>
      dropSubsumedParents(
        restoreUninheritableParents(u, restoreExcluded(u, StandardTraversal.mapClassDef(this, u)))))

    // pass 2+: one pass per distinct non-everywhere family scope, narrowing remap to that
    // scope's entries and its units only — composes because each pass rewrites its own symbols.
    if scopedFamilyIds.nonEmpty then
      val scopedGroups = scopedFamilyIds.toList.groupBy(id => familyScopeOf(familyRemapSources(id)))
      val scopedP = summon[Program] // the Program the scope is asked against
      scopedGroups.toList.sortBy(_._1.fingerprint).foreach { (sc, srcIds) =>
        remap = fullRemap.view.filterKeys(srcIds.toSet).toMap
        units = units.map { u =>
          if inFamilyScope(sc, scopedP, u.symbol) then
            dropSubsumedParents(
              restoreUninheritableParents(u, StandardTraversal.mapClassDef(this, u)))
          else u
        }
      }
    remap = fullRemap // restore for signature processing, recordings, and checks

    // collect post-pass: standalone keys()/values() calls the main pass left for retargetForEach's
    // for-each consumption; whatever remains here is a standalone call.
    if retargetRewrites.values.exists(_.values.exists(_.isInstanceOf[CollectionsTransform.RetargetRewrite.Collect])) ||
        retargetRewritesByDesc.values.exists(_.values.exists(_.isInstanceOf[CollectionsTransform.RetargetRewrite.Collect])) then
      collectPassActive = true
      units = units.map(u => StandardTraversal.mapClassDef(collectPhase, u))
      collectPassActive = false

    // Signature pass — also multi-pass when family scopes exist.
    val symbols2 =
      if scopedFamilyIds.isEmpty then mapSignatures(symbols) // unchanged code path
      else
        // Pass 1: JDK + everywhere families
        remap = fullRemap.filterNot { (k, _) => scopedFamilyIds(k) }
        var tbl = mapSignatures(symbols)
        // Pass 2+: scoped families, only on in-scope symbols
        val scopedGroups = scopedFamilyIds.toList.groupBy(id => familyScopeOf(familyRemapSources(id)))
        val scopedP = summon[Program]
        scopedGroups.toList.sortBy(_._1.fingerprint).foreach { (sc, srcIds) =>
          remap = fullRemap.view.filterKeys(srcIds.toSet).toMap
          tbl = tbl.all.foldLeft(tbl) { (t, s) =>
            if literal(s.id) || !scopedP.owns(s.id) || !inFamilyScope(sc, scopedP, s.id) then t
            else t.updated(s.copy(info = StandardTraversal.mapType(this, s.info)))
          }
        }
        remap = fullRemap
        tbl
    // the modifier the re-parenting invalidated (K28); applied here rather than inside
    // mapSignatures, whose contract is about types, not flags.
    val stripped = strippedOverrides(symbols2)
    val symbols3 =
      if stripped.isEmpty then symbols2
      else symbols2.all.foldLeft(symbols2) { (t, s) =>
        if stripped(s.id) then t.updated(s.copy(flags = s.flags.copy(isOverride = false))) else t
      }
    recordRetypings(symbols, symbols3)
    recordScopedOut(symbols)
    recordRetainedSignatures(symbols)
    recordStrippedOverrides(stripped, symbols2)
    recordSuperDefaults
    recordReifiedTypeArgs(symbols3)
    recordEgressBridges()
    // the minted parent's members are added last: already scala-shaped, so strippedOverrides
    // must not see them before they exist.
    val (units2, synthesised) = synthesiseBridges(units, symbols3)
    program.rebuilt(units2, SymbolTable(symbols3.all ++ synthesised))

  // -------------------------------------------------------------------------
  // RuleScope — WHICH declarations this run rewrites (CLAUDE.md §1(b))
  // -------------------------------------------------------------------------

  /** is this symbol one the given scope admits? IN for `Everywhere` (pre-scope code path), OUT
    * for `Only` (rewrites nothing unasked). Used by the per-entry family scope pass (D12). */
  private def inFamilyScope(sc: RuleScope, p: Program, id: SymId): Boolean =
    if sc.isUnrestricted then true
    else p.symbolOf(id) match
      case Some(s)    => sc.includes(p, s)
      case scala.None => sc match
        case RuleScope.Everywhere(_) => true
        case RuleScope.Only(_)       => false

  private def applyScope(p: Program): Unit =
    excluded = Set.empty; admittedBy = Map.empty; report = PolicyReport.empty
    if scope.isUnrestricted then return

    // owned symbols only (CLAUDE.md §4.56): a JDK-type entry would otherwise match the
    // externally-interned symbol and count as fired while doing nothing.
    val owned = p.owned
    val named: List[(Symbol, String)] =
      p.symbols.all.toList.flatMap(s => if owned(s.id) then scope.entryFor(p, s).map(s -> _) else scala.None)
    val fired = named.map(_._2).toSet
    // an entry whose only matches were external is reported with why: "no such name" would be a
    // lie the name is right and the knob is wrong.
    val externalOnly = p.symbols.all.iterator
      .filterNot(s => owned(s.id))
      .flatMap(s => RuleScope.longestPrefix(s.fullName, scope.entries))
      .toSet -- fired

    scope match
      case RuleScope.Everywhere(_) =>
        excluded = named.map(_._1.id).toSet
      case RuleScope.Only(include) =>
        val es       = FlowPropagation.edges(p)
        def eligible(id: SymId): Boolean = p.symbolOf(id).exists(s => collectionValued(p, s.info))
        // one growth per entry, sorted, so the attribution is deterministic when two entries reach
        // the same declaration; `admittedBy` keeps the first (i.e. the alphabetically first entry).
        val byEntry = include.toList.sorted.map { e =>
          val seeds = named.collect { case (s, `e`) => s.id }.toSet
          e -> (seeds ++ FlowPropagation.grow(es, seeds, eligible))
        }
        val pulled = byEntry.flatMap((_, ids) => ids ++ ids.flatMap(memberOf(p, _))).toSet
        admittedBy = byEntry.reverse.flatMap((e, ids) => ids.map(_ -> e)).toMap
        excluded   = p.symbols.all.collect { case s if !ownerChain(p, s.id).exists(pulled) => s.id }.toSet

    report = PolicyReport(scope.neverFired(fired).toList.sorted.map { k =>
      PolicyFinding(name, s"CollectionsTransform(scope) ${scope.productPrefix} entry", k, PolicyIssue.NeverMatched,
        if externalOnly(k) then
          "this names a type THIS PROGRAM DOES NOT DECLARE — an external the frontend interned on " +
            "first reference, a JDK type most likely. A scope selects DECLARATIONS the port emits, " +
            "and there is no such declaration to hold back or admit, so the phase ran as if the " +
            "entry were absent. The JDK side of this phase is the MAPPING, not the scope: to stop " +
            "`java.util.List` becoming a scala collection everywhere, that is a `typeMap` question; " +
            "to keep it in ONE of your own classes, name that class."
        else
          "no package, type or member with this fully-qualified name occurs in this program, so the " +
            "scope neither held anything back nor admitted anything — the phase ran as if the entry " +
            "were absent")
    })

  /** could this phase retype what this symbol DECLARES? A value's own type, or a method's RESULT —
    * the two positions a pure-move flow moves a collection through. */
  private def collectionValued(p: Program, info: TypeRepr): Boolean = info match
    case TypeRepr.MethodType(_, r, _)                       => isMapped(p, r)
    case TypeRepr.PolyType(_, TypeRepr.MethodType(_, r, _)) => isMapped(p, r)
    case other                                              => isMapped(p, other)

  private def isMapped(p: Program, t: TypeRepr): Boolean =
    headSym(t).flatMap(p.symbolOf)
      .exists(s => typeMap.contains(s.fullName) || effectiveRetarget.contains(s.fullName))

  /** a symbol and every owner above it, fuel-bounded. */
  private def ownerChain(p: Program, id: SymId, fuel: Int = 64): List[SymId] =
    if id == SymId.None || fuel <= 0 then Nil
    else id :: p.symbolOf(id).toList.flatMap(s => ownerChain(p, s.owner, fuel - 1))

  /** the nearest ancestor-or-self that is a MEMBER of a type rather than of a method — the unit a
    * body is rewritten in. A propagated parameter or local pulls this in with it, because half a
    * rewritten body is not a translation. */
  private def memberOf(p: Program, id: SymId): Option[SymId] =
    ownerChain(p, id).find(x => !p.symbolOf(x).flatMap(s => p.symbolOf(s.owner)).exists(o => isMethodLike(o.info)))

  private def isMethodLike(t: TypeRepr): Boolean = t match
    case _: TypeRepr.MethodType => true
    case _: TypeRepr.PolyType   => true
    case _                      => false

  /** Restores scoped-out members by splicing originals back into the mapped unit. Empty
    * `excluded` returns the unit untouched; an excluded class restores only its own positions
    * (parents, tparams) without short-circuiting its body. */
  private def restoreExcluded(orig: Tree.ClassDef, mapped: Tree.ClassDef): Tree.ClassDef =
    if literalEmpty then mapped
    else
      val body = CollectionsTransform.spine(orig.body, mapped.body, orig.symbol).map {
        case (o: Tree.ClassDef, m: Tree.ClassDef) => restoreExcluded(o, m)
        case (o: Tree.DefDef, m: Tree.DefDef)     => if literal(o.symbol) then o else m
        case (o: Tree.ValDef, m: Tree.ValDef)     => if literal(o.symbol) then o else m
        case (_, m)                               => m
      }
      val own =
        if !excluded(orig.symbol) then mapped
        else mapped.copy(parents = orig.parents, selfType = orig.selfType,
                         tparams = orig.tparams, enumCases = orig.enumCases)
      own.copy(body = body)

  /** A parent whose target cannot be inherited (e.g. `Map.Entry` to `Tuple2` which is final)
    * is left as java's; the seam is counted. No-op when [[uninheritableSyms]] is empty.
    * // ENGINE-LIMITS K5.7 */
  private def restoreUninheritableParents(orig: Tree.ClassDef, mapped: Tree.ClassDef)(using Program): Tree.ClassDef =
    if uninheritableSyms.isEmpty then mapped
    else
      def tpeOf(p: Term | TypeTree): TypeRepr = p match
        case tt: TypeTree => tt.tpe
        case t: Term      => t.tpe
      // members this class's retained parents declare that their targets cannot carry.
      val unimplementable = collection.mutable.Set.empty[CollectionsTransform.MemberSig]
      val parents =
        // lengths agree by construction; a mismatch means the traversal changed shape, so the
        // mapped list is the honest answer rather than a zip that silently truncates (see spine).
        if orig.parents.sizeIs != mapped.parents.size then mapped.parents
        else orig.parents.zip(mapped.parents).map { (o, m) =>
          headSym(tpeOf(o)).filter(uninheritableSyms.contains) match
            case scala.None => m
            case Some(_)    =>
              val kept   = TirPrinter.tpe(tpeOf(o), TirPrinter.Style.canonical)
              val target = TirPrinter.tpe(tpeOf(m), TirPrinter.Style.canonical)
              headSym(tpeOf(m)).flatMap(summon[Program].symbolOf).map(_.fullName)
                .flatMap(CollectionsTransform.UnsupportedOnTarget.get)
                .foreach(unimplementable ++= _)
              seam("parent (implements)", target, kept, orig.origin, orig.symbol,
                   CollectionBoundaryCheck.Issue.InexpressibleParent)
              // a porter note beside the class (§4.575) — the diff against upstream shows
              // nothing at exactly the line the question is asked at.
              record(Decision(
                kind       = Decision.Kind.RetainedParent,
                subject    = orig.symbol,
                subjectFqn = summon[Program].symbolOf(orig.symbol).map(_.fullName).getOrElse(kept),
                detail = Map(
                  "kept"       -> kept,
                  "instead-of" -> target,
                  "why" -> ("this class IMPLEMENTS a java type the collections mapping covers, and " +
                    "the target cannot BE a parent — it is final, has no write-through member and " +
                    "takes its components in its constructor. The parent stays java's so the class " +
                    "itself compiles; a value of it meeting the target is counted at the slot"),
                ),
                reason = Reason.Universal("inexpressible-parent(K5.7)"),
                origin = orig.origin,
              ))
              o
        }
      val body = CollectionsTransform.spine(orig.body, mapped.body, orig.symbol).map {
        case (o: Tree.ClassDef, m: Tree.ClassDef) => restoreUninheritableParents(o, m)
        // the member half of the same refusal, under both conditions: declaresUnimplementable
        // (really the interface's member) and brokenByMapping (the phase can point at what broke).
        case (_, m: Tree.DefDef) if unimplementable.nonEmpty =>
          val broken =
            if declaresUnimplementable(m, unimplementable.toSet) then brokenByMapping(m) else scala.None
          broken.fold(m)(refuseOnTarget(m, orig, _))
        case (_, m)                               => m
      }
      mapped.copy(parents = parents, body = body)

  /** Drop a minted parent already subsumed by another minted parent (e.g. `JavaIterable`
    * subsumed by `mutable.Map <: Iterable`). No-op when no subsumption exists.
    * // ENGINE-LIMITS K28.1 */
  private def dropSubsumedParents(u: Tree.ClassDef)(using p: Program): Tree.ClassDef =
    if parentClash.forall((_, mp) => mp.subsumed.isEmpty) then u
    else
      def tpeOf(x: Term | TypeTree): TypeRepr = x match
        case tt: TypeTree => tt.tpe
        case t: Term      => t.tpe
      val ph = new Phase:
        def name: String = "collections/drop-subsumed-parents"
        override def transformClassDef(cd: Tree.ClassDef)(using Program): Tree.ClassDef =
          val drop = parentClash.get(cd.symbol).map(_.subsumed).getOrElse(Map.empty)
          if drop.isEmpty then cd
          else
            val kept = cd.parents.filter { pt =>
              headSym(tpeOf(pt)).flatMap(p.symbolOf).map(_.fullName).forall(!drop.contains(_))
            }
            if kept.sizeIs == cd.parents.size then cd
            else
              cd.parents.filterNot(kept.contains).foreach { pt =>
                val gone = TirPrinter.tpe(tpeOf(pt), TirPrinter.Style.canonical)
                val by   = headSym(tpeOf(pt)).flatMap(p.symbolOf).map(_.fullName).flatMap(drop.get)
                // recorded on THIS phase's buffer, not the walk's throwaway `new Phase` one, or
                // decisions.tsv would say nothing about a parent that really was dropped.
                CollectionsTransform.this.record(Decision(
                  kind       = Decision.Kind.SubsumedParent,
                  subject    = cd.symbol,
                  subjectFqn = p.symbolOf(cd.symbol).map(_.fullName).getOrElse(gone),
                  detail = Map(
                    "dropped"     -> gone,
                    "subsumed-by" -> by.getOrElse("?"),
                    "why" -> ("java related this class's two `implements` clauses at ONE MEMBER " +
                      "spelled two ways, and the parent this mapping minted for the other clause " +
                      "already carries that relation. Minting both would declare one name at two " +
                      "arities, which scala's single namespace cannot hold and which no repair at " +
                      "the member can fix (CLAUDE.md §4.5). A value of this class meeting a slot " +
                      "typed at the dropped shim is coerced there instead"),
                  ),
                  reason = Reason.Universal("subsumed-minted-parent(§4.5, K28.1)"),
                  origin = cd.origin,
                ))
              }
              cd.copy(parents = kept)
      StandardTraversal.mapClassDef(ph, u)

  /** Is this the interface's member, or a method that merely shares its name? Tested by the
    * retained parent's own signature (e.g. `Map.Entry` declares exactly `setValue(V)`), never the
    * bare name — a class may declare `setValue(int, int)` beside it, which java resolves
    * separately. See [[CollectionsTransform.MemberSig]] for `arity`. CLAUDE.md §3 */
  private def declaresUnimplementable(d: Tree.DefDef, sigs: Set[CollectionsTransform.MemberSig])(
      using p: Program): Boolean =
    p.symbolOf(d.symbol).exists(s =>
      sigs.contains(CollectionsTransform.MemberSig(s.name, d.paramss.map(_.size).sum)))

  /** Can this phase point at what it broke? The second condition, keeping the refusal a
    * translation rather than a policy: `Map.Entry.setValue` throwing is conforming only for an
    * entry that genuinely cannot perform the write, so the licence is read off the BODY (a call to
    * a member `UnsupportedOnTarget` says the retyped receiver's target cannot express), never off
    * the declaration (§4.56). Returns the reference found, so the decision can name what it broke. */
  private def brokenByMapping(d: Tree.DefDef)(using p: Program): Option[String] =
    d.rhs.flatMap { body =>
      StandardTraversal.scanTerm(body, Option.empty[String]) { (acc, t) =>
        if acc.nonEmpty then acc
        else
          t match
            case Tree.Select(recv, m, _, _) =>
              for
                tgt  <- headSym(recv.tpe).flatMap(p.symbolOf).map(_.fullName)
                sigs <- CollectionsTransform.UnsupportedOnTarget.get(tgt)
                nm   <- p.symbolOf(m).map(_.name)
                if sigs.exists(_.name == nm)
              yield MemberKey(tgt, nm).render
            case _ => scala.None
      }
    }

  /** Substitute `UnsupportedOperationException` for a member the retained parent declares
    * and the mapping target cannot carry. Both guards required: [[declaresUnimplementable]]
    * and [[brokenByMapping]]. // ENGINE-LIMITS K5.7 */
  private def refuseOnTarget(d: Tree.DefDef, owner: Tree.ClassDef, broke: String)(using p: Program): Tree.DefDef =
    val nm  = p.symbolOf(d.symbol).map(_.name).getOrElse("")
    val fqn = p.symbolOf(d.symbol).map(_.fullName).getOrElse(nm)
    val o   = d.origin
    val why =
      s"$nm: this java.util.Map.Entry was ported to a detached pair, so the backing map is not " +
        "reachable from the entry. java declares this an OPTIONAL operation whose contract is this " +
        "exception; writing to the detached copy would succeed and change nothing"
    val exn = Tree.Apply(Tree.New(TypeTree(unsupportedOpTpe, o), unsupportedOpTpe, o),
                         List(Tree.Literal(Constant.StringC(why), stringTpe, o)),
                         unsupportedOpSym, unsupportedOpTpe, o)
    seam(s"member (implements) $nm", TirPrinter.tpe(d.returnTpt.tpe, TirPrinter.Style.canonical),
         CollectionsTransform.UnsupportedOperationFqn, o, d.symbol,
         CollectionBoundaryCheck.Issue.InexpressibleParent)
    record(Decision(
      kind       = Decision.Kind.SubstitutedBody,
      subject    = d.symbol,
      subjectFqn = fqn,
      detail = Map(
        "member"  -> nm,
        "throws"  -> CollectionsTransform.UnsupportedOperationFqn,
        "owner"   -> p.symbolOf(owner.symbol).map(_.fullName).getOrElse(""),
        // the reference the mapping removed, verbatim — the one fact a reader cannot recover from the emitted throw.
        "broke"   -> broke,
        "why" -> ("the RETAINED PARENT declares this member and the mapping target cannot carry " +
          "it, so the body is java's own documented refusal for an optional operation. Writing to " +
          "the detached pair would compile and change nothing (K2); dropping the member would " +
          "leave the class abstract against the parent it kept"),
      ),
      reason = Reason.Universal("inexpressible-parent(K5.7)"),
      origin = o,
    ))
    d.copy(rhs = Some(Tree.Throw(exn, d.returnTpt.tpe, o)))

  /** `StandardTraversal.mapSymbols`, minus the symbols the scope held back, so an excluded
    * declaration's signature stays exactly as the frontend read it — matching the restored tree. */
  private def mapSignatures(tbl: SymbolTable)(using p: Program): SymbolTable =
    if literalEmpty then StandardTraversal.mapSymbols(this, tbl)
    // same ownership guard mapSymbols carries: an external member's signature belongs to a class
    // file this phase cannot move. K15
    else tbl.all.foldLeft(tbl) { (t, s) =>
      if literal(s.id) || !p.owns(s.id) then t
      else t.updated(s.copy(info = StandardTraversal.mapType(this, s.info)))
    }

  /** One decision row per declaration the scope held back that the mapping would otherwise have
    * moved — without it, the `Everywhere(except)` direction is invisible in `decisions.tsv` (§5.1).
    * Only declarations, and only ones a retyping would actually have reached. */
  private def recordScopedOut(before: SymbolTable)(using p: Program): Unit =
    if excluded.isEmpty then return
    scope match
      case RuleScope.Everywhere(_) =>
        before.all.foreach { s =>
          if excluded(s.id) && collectionValued(p, s.info) && Decision.isDeclaration(p, s) then
            scope.entryFor(p, s).foreach { entry =>
              record(Decision(
                kind       = Decision.Kind.ScopedOut,
                subject    = s.id,
                subjectFqn = s.fullName,
                // no `key` in detail — Reason.Configured already carries the entry.
                detail = Map(
                  "kept" -> TirPrinter.tpe(s.info, TirPrinter.Style.canonical),
                  "why"  -> ("this port's collections scope excludes this declaration, so it keeps " +
                    "the JDK type while the code around it moved to scala's"),
                ),
                reason = Reason.Configured(name, entry),
                origin = Decision.originOf(p, s.id),
              ))
            }
        }
      case RuleScope.Only(_) => () // the admitted half is recorded by `recordRetypings`

  // -------------------------------------------------------------------------
  // the refusal no policy asked for — a member that overrides a class file
  // -------------------------------------------------------------------------

  /** the anchor a held member is held BY — `<fqn>#<member>`, for the decision's own detail. */
  private var retainedAnchors: Map[SymId, String] = Map.empty

  /** Fills [[retainedOverrides]]: members overriding a class-file declaration whose signature
    * this phase would move (isOverride, mentionsMapped, no program-declared ancestor, an
    * uncovered external ancestor could declare it). Holds the member and all overriders below. */
  private def applyClassFileOverrides(p: Program): Unit =
    retainedOverrides = Set.empty; retainedOwners = Set.empty; retainedAnchors = Map.empty
    given Program = p
    val owned = p.owned
    val seeds = p.symbols.all.iterator.filter { s =>
      s.flags.isOverride && owned(s.id) && isMethodLike(s.info) && mentionsMapped(s.info)
    }.map(_.id).toList
    if seeds.isEmpty then return
    val graph  = OverrideGraph.build(p)
    val held   = collection.mutable.Set.empty[SymId]
    val anchor = collection.mutable.Map.empty[SymId, String]
    seeds.foreach { m =>
      if graph.overridden(m).isEmpty then
        val sig       = graph.signatureOf(m)
        val declarers = graph.externalAncestorsOf(graph.ownerOf(m))
          .filter(fqn => sig.exists(ExternalSurface.default.mayDeclare(fqn, _)))
        // the shim half decides it: where a candidate declarer is a type the mapping covers,
        // the parent is already shim-shaped, so hold back only when every candidate stayed java.
        val kept = if declarers.exists(coveredExternally) then Nil else declarers
        if kept.nonEmpty then
          val label = kept.sorted.map(fqn => MemberKey(fqn, sig.map(_.name).getOrElse("?")).render).mkString(", ")
          (m :: graph.overriders(m)).filter(owned).filter(spliceable(graph))
            .foreach { x => held += x; anchor += (x -> label) }
    }
    retainedOverrides = held.toSet
    retainedAnchors   = anchor.toMap
    // a held member's parameter symbols go with it, since restoreExcluded splices the original ValDefs back.
    retainedOwners = p.symbols.all.collect { case s if retainedOverrides(s.owner) => s.id }.toSet

  /** is this external type one the phase's OWN tables move — so that the emitted parent is a shim
    * and an override of its members belongs in shim shape? Read from the tables, never from the
    * name (§4.56). */
  private def coveredExternally(fqn: String): Boolean =
    typeMap.contains(fqn) || effectiveRetarget.contains(fqn)

  /** True when [[restoreExcluded]] can reach this member (owner has a `ClassDef` definition).
    * Anonymous classes hang off `Tree.New` inside a term and are not on the splice spine. */
  private def spliceable(graph: OverrideGraph)(m: SymId)(using p: Program): Boolean =
    p.definitionOf(graph.ownerOf(m)).exists(_.isInstanceOf[Tree.ClassDef])

  // -------------------------------------------------------------------------
  // …and the MODIFIER a re-parenting invalidated — `ENGINE-LIMITS.md` K28
  // -------------------------------------------------------------------------

  /** Strip `override` from members whose only anchor was a parent this phase re-parented
    * and whose emitted target does not declare it. Four conjuncts: owned + `isOverride`,
    * re-parented owner ([[parentClash]]), no program ancestor declares it, no unknown
    * ancestor could declare it. No-op when `parentClash` is empty. // ENGINE-LIMITS K28 */
  private def strippedOverrides(before: SymbolTable)(using p: Program): Set[SymId] =
    if parentClash.isEmpty then return Set.empty
    val graph = OverrideGraph.build(p)
    val owned = p.owned
    before.all.iterator.filter { s =>
      s.flags.isOverride && owned(s.id) && isMethodLike(s.info) &&
        parentClash.get(s.owner).exists(mp => mp.kinds.nonEmpty || mp.shims.nonEmpty) &&
        graph.signatureOf(s.id).exists { sig =>
          !ExternalSurface.javaLangObjectDeclares(sig) &&
            !programAncestorDeclares(graph, s.owner, sig) &&
            !graph.externalAncestorsOf(s.owner).filterNot(tabulatedTarget)
              .exists(ExternalSurface.default.mayDeclare(_, sig)) &&
            !mintedParentDeclares(s.owner, sig)
        }
    }.map(_.id).toSet

  /** Did this phase move `fqn` to a target THIS FILE tabulates the surface of? Deliberately not
    * [[coveredExternally]] (a wider question whose `retarget` disjunct has no surface here — a
    * parent moved into one is as unknown as any unparsed type, and excluding it lost an `override`
    * that `scala.math.Ordering` really does declare). The positive test §4.56 asks for: what did
    * the phase do, and can it answer for the result. */
  private def tabulatedTarget(fqn: String): Boolean =
    typeMap.get(fqn).exists { (tgt, k) =>
      if CollectionsTransform.standaloneTargets(tgt) then CollectionsTransform.OverridesShim.contains(tgt)
      else CollectionsTransform.OverridesTarget.contains(k.toString)
    }

  /** Does an ancestor THIS PROGRAM DECLARES declare `sig`, by name and arity, deliberately
    * looser than the override edge? `OverrideGraph.overridden` is exact and wrong here — a java
    * interface may permute a type-parameter name its implementor uses differently, so `overridden`
    * answers empty for a real override. Asked at the looser key; the error direction is refusal. D1 */
  private def programAncestorDeclares(graph: OverrideGraph, owner: SymId,
                                      sig: OverrideGraph.Signature)(using Program): Boolean =
    graph.ancestorsOf(owner).exists { a =>
      graph.membersOf(a).exists(m =>
        graph.signatureOf(m).exists(o => o.name == sig.name && o.arity == sig.arity))
    }

  /** Does any parent this phase minted for `cls` declare `sig`? OR across the parents, not AND:
    * a class routinely has several (§4.5), and one parent declaring the member is enough to keep
    * the modifier true. */
  private def mintedParentDeclares(cls: SymId, sig: OverrideGraph.Signature): Boolean =
    parentClash.get(cls).exists { mp =>
      mp.kinds.exists(k => CollectionsTransform.OverridesTarget.get(k.toString).exists(_.exists(_.matches(sig)))) ||
        mp.shims.exists(s => CollectionsTransform.OverridesShim.get(s).exists(_.exists(_.matches(sig))))
    }

  /** One decision row per member for [[strippedOverrides]]. `Reason.Universal`, since there is
    * no key to point a reader at — the engine chose the target, not the port. `parent=` answers the
    * reader's next question (`overrides nothing` names a type the java file never mentions). */
  private def recordStrippedOverrides(stripped: Set[SymId], before: SymbolTable)(using p: Program): Unit =
    stripped.toList.flatMap(id => before.all.find(_.id == id)).sortBy(_.fullName).foreach { s =>
      val parents = parentClash.get(s.owner).toList
        .flatMap(mp => mp.targets.toList ++ mp.shims.toList).distinct.sorted
      record(Decision(
        kind       = Decision.Kind.StrippedOverride,
        subject    = s.id,
        subjectFqn = s.fullName,
        detail = Map(
          "member" -> s.name,
          "parent" -> (if parents.isEmpty then "?" else parents.mkString(", ")),
          "why" -> ("java's own hierarchy justified this `override` and this phase moved the parent " +
            "that justified it, so the modifier was a statement about a type the emitted class no " +
            "longer extends. The member itself is unchanged"),
        ),
        reason = Reason.Universal("minted-parent-override(§1, K28)"),
        origin = Decision.originOf(p, s.id),
      ))
    }

  // -------------------------------------------------------------------------
  // the surface the re-parenting owes. ENGINE-LIMITS K28.1
  // -------------------------------------------------------------------------

  /** one bridge this run will build: the class, the kind it was minted at, the parent's java type
    * arguments, the row, and the java member the body delegates to. `rename` is
    * `CapturedByTarget`'s answer, carried so the rename pass and body builder cannot disagree. */
  private final case class Bridge(cls: SymId, kind: Kind, args: List[TypeRepr],
                                  row: CollectionsTransform.Bridged, java: SymId, rename: Boolean)

  private var bridges: List[Bridge] = Nil

  /** the java types the mapping sends to a given target — the inverse of `typeMap`. `subsumed`
    * is keyed on the target FQN; an override anchor is spelled with the java type, so the two are
    * joined through this table rather than by a name that looks alike. */
  private def shimSource(target: String): Set[String] =
    typeMap.collect { case (fqn, (tgt, _)) if tgt == target => fqn }.toSet

  /** how many type arguments a kind's target needs before a bridge can name its key, value or
    * element type. A RAW clause supplies none, and inventing `java.lang.Object` for them would be
    * §4.6's fabricated fact at the emitted signature — so the whole class declines, counted. */
  private def kindArity(k: Kind): Int = if k == Kind.Map then 2 else 1

  /** Which bridges this run owes — one row per (class, row) the table names and the class can
    * answer. Asked of `declared`, never `kinds`, so the synthesis lands on the base and not on
    * each subclass (a second copy there would define one surface twice — §1.5's shape one module in). */
  private def planBridges(p: Program): List[Bridge] =
    if parentClash.isEmpty then return Nil
    given Program = p
    val graph = OverrideGraph.build(p)
    def sigOf(m: SymId): Option[OverrideGraph.Signature] = graph.signatureOf(m)
    /** the delegate, on THIS class and no ancestor of it — so one bridge lands on the component
      * rather than once per subclass, and a java interface (which declares none of these) declines
      * structurally. */
    def ownMember(cls: SymId, want: ExternalSurface.Member): Option[SymId] =
      if graph.ancestorsOf(cls).exists(a => graph.membersOf(a).exists(m => sigOf(m).exists(want.matches)))
      then scala.None
      else
        // where the key names several, java's own resolution order picks: add(E) beside
        // add(E...) share a (name, arity) key, and java admits the fixed-arity candidate before
        // the pack (JLS 15.12.2). A last-array candidate is still taken when it's the only one.
        val cands = graph.membersOf(cls).filter(m => sigOf(m).exists(want.matches) && !literal(m))
        def packs(m: SymId): Boolean = sigOf(m).flatMap(_.descriptor).exists(_.params.lastOption match
          case Some(Param.Arr(_)) => true
          case _                  => false)
        cands.find(m => !packs(m)).orElse(cands.headOption)
    parentClash.toList.sortBy((c, _) => p.symbolOf(c).map(_.fullName).getOrElse("")).flatMap { (cls, mp) =>
      mp.declared.distinct.flatMap { (k, args) =>
        val rows = CollectionsTransform.BridgedTarget.getOrElse(k.toString, Nil)
        // a type declaring none of the delegates is not the implementor — owes nothing, not reported.
        val found = rows.filter(_.from.nonEmpty).flatMap(r => r.from.iterator.flatMap(ownMember(cls, _)).nextOption())
        if rows.isEmpty || found.isEmpty then Nil
        else if args.sizeIs != kindArity(k) then
          // a raw implements Map names no key/value; leaving the compiler's own E164 is the honest arm.
          refuseBridge(p, cls, k, "raw-parent",
            s"the mapped `implements` clause is RAW, so the ${kindArity(k)} type argument(s) the " +
              "bridged signatures need are not written anywhere")
          Nil
        else rows.flatMap { row =>
          if row.from.isEmpty then Some(Bridge(cls, k, args, row, SymId.None, false))
          else row.from.iterator.flatMap(ownMember(cls, _)).nextOption() match
            case Some(j) =>
              val captured = CollectionsTransform.CapturedByTarget.getOrElse(k.toString, Set.empty)
                .exists(m => sigOf(j).exists(m.matches))
              Some(Bridge(cls, k, args, row, j, captured))
            case scala.None if !row.required => scala.None
            case scala.None =>
              refuseBridge(p, cls, k, "no-java-member",
                s"`${row.scalaName}/${row.arity}` is declared by the emitted parent and this class " +
                  s"declares none of ${row.from.map(m => s"${m.name}/${m.arity}").mkString(", ")} " +
                  "to build it from")
              scala.None
        }
      }
    }

  /** the refusal lane: one row per bridge this run could not build, naming the guard (§3), on
    * `collection-boundary` under its own `Issue` — the class is missing a member scalac will demand. */
  private def refuseBridge(p: Program, cls: SymId, k: Kind, guard: String, why: String): Unit =
    seam(s"minted-parent surface [$guard]", k.toString, why,
         Decision.originOf(p, cls), cls, CollectionBoundaryCheck.Issue.UnbridgedMember)

  /** Renames every captured delegate out of the way through `MemberRenamer` (§4.55): expands
    * through the override closure, screens for an external anchor, reads effective names
    * parents-first. `SuffixUntilFree`, not `Refuse` — the body reads the delegate's name back out
    * of the symbol table. Refused per owning class, since half a bridged surface compiles worse
    * than the class this started from. */
  private def renameBridgeDelegates(p: Program): SymbolTable =
    val wanted = bridges.filter(b => b.rename && b.java != SymId.None).map(_.java).distinct
    if wanted.isEmpty then return p.symbols
    val graph = OverrideGraph.build(p)
    val owners = bridges.filter(b => b.java != SymId.None).map(b => b.java -> b.cls).toMap
    // parents this phase removed from this class: java types the mapping re-parented away from,
    // plus shim clauses dropSubsumedParents deleted (K28.1). Per request, not per call.
    val reParented: Set[String] = typeMap.collect {
      case (fqn, (tgt, _)) if !CollectionsTransform.standaloneTargets(tgt) &&
                              !CollectionsTransform.UninheritableTargets(tgt) => fqn
    }.toSet
    def detachedFor(cls: SymId): Set[String] =
      reParented ++ parentClash.get(cls).toList.flatMap(_.subsumed.keySet).flatMap(shimSource)
    val requests = wanted.flatMap { j =>
      p.symbolOf(j).map { s =>
        val cls      = owners.getOrElse(j, SymId.None)
        val ownerFqn = p.symbolOf(cls).map(_.fullName).getOrElse("")
        MemberRenamer.Request(j, s.name + CollectionsTransform.BridgeSuffix,
                              Reason.Universal("minted-parent-surface(§1, K28.1)"),
                              MemberKey(ownerFqn, s.name).render, ownerFqn, detachedFor(cls))
      }
    }
    val (renamed, refusals) = MemberRenamer.rename(
      p, graph, requests, MemberRenamer.OnCollision.SuffixUntilFree, decisions)
    if refusals.nonEmpty then
      val lost = refusals.flatMap(r => owners.get(r.request.member)).toSet
      refusals.foreach { r =>
        refuseBridge(p, owners.getOrElse(r.request.member, SymId.None),
                     Kind.Map, "rename-refused", r.why)
      }
      // the whole class, not the one member — see the group note above.
      bridges = bridges.filterNot(b => lost.contains(b.cls))
    renamed.symbols

  /** The synthesis, appended to each owning class's body — not spliced at a position, since
    * these members have no java counterpart to sit beside and JLS 12.5's ordering rule (§4.55) has
    * nothing to say about them. */
  private def synthesiseBridges(units: List[Tree.ClassDef], symbols: SymbolTable)
                               (using p: Program): (List[Tree.ClassDef], List[Symbol]) =
    if bridges.isEmpty then return (units, Nil)
    val added = collection.mutable.ListBuffer[Symbol]()
    var next  = symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    def mint(nm: String, full: String, owner: SymId, info: TypeRepr, isOverride: Boolean): SymId =
      val id = SymId(next); next += 1
      added += Symbol(id, nm, full, Flags(isOverride = isOverride), owner, info)
      id
    val byClass = bridges.groupBy(_.cls)
    val ph = new Phase:
      def name: String = "collections/minted-parent-surface"
      override def transformClassDef(cd: Tree.ClassDef)(using Program): Tree.ClassDef =
        byClass.get(cd.symbol) match
          case scala.None     => cd
          case Some(myRows)   =>
            val built = myRows.flatMap(b => buildBridge(b, cd, symbols, mint))
            if built.isEmpty then cd else cd.copy(body = cd.body ++ built)
    // synthesised symbols are handed back, not kept in a field: added after strippedOverrides
    // runs, so ordering alone answers "is this one of mine".
    (units.map(u => StandardTraversal.mapClassDef(ph, u)), added.toList)

  /** One bridged member, as a tree. Every body is a delegation: java's own behaviour, unchanged,
    * reached under a new name, plus one of four shape conversions the parent asked for
    * (`Option(x)`, `{ x; this }`, `.asScala`, `{ x; () }`). The three `Kind.Seq` rows with no
    * delegate are documented at their bodies (`JavaCollections.buffer*`). `None` for a row this
    * builder has no arm for — the honest arm rather than a crash. */
  private def buildBridge(b: Bridge, cd: Tree.ClassDef, symbols: SymbolTable,
                          mint: (String, String, SymId, TypeRepr, Boolean) => SymId)
                         (using p: Program): Option[Tree.DefDef] =
    val o        = cd.origin
    val cls      = cd.symbol
    val selfT    = TypeRepr.ThisType(cls)
    def self     = Tree.This(cls, selfT, o)
    val args     = b.args.map(t => StandardTraversal.mapType(this, t))
    val ownerFqn = p.symbolOf(cls).map(_.fullName).getOrElse("")
    def tpe(s: SymId, as: TypeRepr*): TypeRepr =
      if as.isEmpty then TypeRepr.TypeRef(TypeRepr.NoPrefix, s)
      else TypeRepr.AppliedType(TypeRepr.TypeRef(TypeRepr.NoPrefix, s), as.toList)
    val javaInfo = symbols.all.find(_.id == b.java).map(_.info)
    val javaRes  = javaInfo.collect { case TypeRepr.MethodType(_, r, _) => r }
      .getOrElse(TypeRepr.NoType)
    val javaName = p.symbolOf(b.java).map(_.name).getOrElse("")
    def callJ(as: List[Term], res: TypeRepr): Term =
      Tree.Apply(Tree.Select(self, b.java, TypeRepr.NoType, o), as, b.java, res, o)
    def stat(as: List[Term], res: TypeRepr, tail: Term, t: TypeRepr): Term =
      Tree.Block(List(callJ(as, res)), tail, t, o)
    def unit: Term = Tree.Literal(Constant.UnitC, unitTpe, o)
    def param(nm: String, t: TypeRepr): Tree.ValDef =
      Tree.ValDef(mint(nm, nm, SymId.None, t, false), TypeTree(t, o), scala.None, o)
    def ref(v: Tree.ValDef): Term = Tree.Ident(v.symbol, v.tpt.tpe, o)
    def opt(x: Term, of: TypeRepr): Term =
      Tree.Apply(Tree.Ident(optionSym, TypeRepr.NoType, o), List(x), optionSym, tpe(optionSym, of), o)
    /** the shim result at a `scala.collection` slot. Asked of the phase's OWN record — is the head
      * one of the shims this mapping produces — and never of the name (§4.56); a java member the
      * mapping already retyped to a `scala.collection` type conforms as it stands. */
    def asIterable(x: Term, elem: TypeRepr): Term =
      if headSym(javaRes).exists(shimSyms.contains)
      then Tree.Select(x, asScalaIterableSym, tpe(scalaIterableSym, elem), o)
      else x
    /** …and the ITERATOR half, whose discriminator is the RENAME rather than a type. A delegate this
      * pass renamed is the one java's `Iterable` declares, so its result is a `JavaIterator`-shaped
      * value and needs the view; a delegate it did NOT rename is `entrySet()`, whose result the
      * mapping already retyped to a `scala.collection` and whose `.iterator` is java's own idiom for
      * the same traversal. */
    def asIterator(elem: TypeRepr): Term =
      val call = callJ(Nil, javaRes)
      val want = tpe(scalaIteratorSym, elem)
      if b.rename then Tree.Select(call, asScalaIteratorSym, want, o)
      else Tree.Select(call, iteratorMemberSym, want, o)
    def defd(nm: String, ps: List[Tree.ValDef], res: TypeRepr, body: Term,
             tps: List[Tree.TypeDef] = Nil): Option[Tree.DefDef] =
      val info = TypeRepr.MethodType(
        ps.map(v => p.symbolOf(v.symbol).map(_.name).getOrElse("x") -> v.tpt.tpe), res)
      val sym = mint(nm, MemberKey(ownerFqn, nm).render, cls, info, true)
      recordBridge(b, sym, MemberKey(ownerFqn, nm).render,
                   if b.java == SymId.None then "-" else MemberKey(ownerFqn, javaName).render, o)
      Some(Tree.DefDef(sym, if ps.isEmpty then Nil else List(ps), TypeTree(res, o), Some(body), o,
                       tparams = tps))

    (b.kind, b.row.scalaName, b.row.arity) match
      case (Kind.Map, nm, ar) =>
        val k = args.head; val v = args(1); val pair = tpe(tuple2Sym, k, v)
        (nm, ar) match
          case ("put", 2) =>
            val pk = param("key", k); val pv = param("value", v)
            defd("put", List(pk, pv), tpe(optionSym, v), opt(callJ(List(ref(pk), ref(pv)), v), v))
          case ("get", 1) =>
            val pk = param("key", k)
            defd("get", List(pk), tpe(optionSym, v), opt(callJ(List(ref(pk)), v), v))
          case ("addOne", 1) =>
            val pe = param("elem", pair)
            defd("addOne", List(pe), selfT,
                 stat(List(Tree.Select(ref(pe), key1Sym, k, o), Tree.Select(ref(pe), value2Sym, v, o)),
                      v, self, selfT))
          case ("subtractOne", 1) =>
            val pk = param("key", k)
            defd("subtractOne", List(pk), selfT, stat(List(ref(pk)), v, self, selfT))
          case ("iterator", 0) => defd("iterator", Nil, tpe(scalaIteratorSym, pair), asIterator(pair))
          case ("values", 0)   =>
            defd("values", Nil, tpe(scalaIterableSym, v), asIterable(callJ(Nil, javaRes), v))
          case ("keys", 0)     =>
            defd("keys", Nil, tpe(scalaIterableSym, k), asIterable(callJ(Nil, javaRes), k))
          case _ => scala.None
      case (Kind.Set, nm, ar) =>
        val e = args.head
        (nm, ar) match
          case ("contains", 1) | ("indexOf", 1) =>
            val pe = param("elem", e)
            defd("contains", List(pe), tpe(boolSym), callJ(List(ref(pe)), tpe(boolSym)))
          case ("addOne", 1) =>
            val pe = param("elem", e)
            defd("addOne", List(pe), selfT, stat(List(ref(pe)), tpe(boolSym), self, selfT))
          case ("subtractOne", 1) =>
            val pe = param("elem", e)
            defd("subtractOne", List(pe), selfT, stat(List(ref(pe)), tpe(boolSym), self, selfT))
          case ("iterator", 0) => defd("iterator", Nil, tpe(scalaIteratorSym, e), asIterator(e))
          case _ => scala.None
      case (Kind.Seq, nm, ar) =>
        val a = args.head
        def helper(n: String, as: List[Term], res: TypeRepr): Term =
          val s = sym(n)
          Tree.Apply(Tree.Ident(s, TypeRepr.NoType, o), self :: as, s, res, o)
        (nm, ar) match
          case ("apply", 1) =>
            val pi = param("i", tpe(intSym))
            defd("apply", List(pi), a, callJ(List(ref(pi)), a))
          case ("length", 0) => defd("length", Nil, tpe(intSym), callJ(Nil, tpe(intSym)))
          case ("update", 2) =>
            val pi = param("idx", tpe(intSym)); val pe = param("elem", a)
            defd("update", List(pi, pe), unitTpe, stat(List(ref(pi), ref(pe)), a, unit, unitTpe))
          case ("insert", 2) =>
            val pi = param("idx", tpe(intSym)); val pe = param("elem", a)
            defd("insert", List(pi, pe), unitTpe, stat(List(ref(pi), ref(pe)), unitTpe, unit, unitTpe))
          case ("prepend", 1) =>
            val pe = param("elem", a)
            defd("prepend", List(pe), selfT,
                 stat(List(Tree.Literal(Constant.IntC(0), tpe(intSym), o), ref(pe)), unitTpe, self, selfT))
          case ("addOne", 1) =>
            val pe = param("elem", a)
            defd("addOne", List(pe), selfT, stat(List(ref(pe)), tpe(boolSym), self, selfT))
          case ("remove", 1) =>
            val pi = param("idx", tpe(intSym))
            defd("remove", List(pi), a, callJ(List(ref(pi)), a))
          case ("iterator", 0) => defd("iterator", Nil, tpe(scalaIteratorSym, a), asIterator(a))
          case ("contains", 1) | ("indexOf", 1) =>
            // the two GENERIC bridges. `SeqOps.contains`/`indexOf` take `A1 >: A` — scala's own widening, so
            // that `xs.contains(anAny)` type-checks — and a bridge declared at `A` would erase to
            // the same `(Object)Boolean` as java's own member and reproduce the clash it is here to
            // close. The cast is what java's `contains(Object)` formal already asks of every
            // caller: `A1` may be `Any`, which is not a `java.lang.Object`.
            val a1  = mint("A1", "A1", cls, TypeRepr.NoType, false)
            val a1t = TypeRepr.TypeRef(TypeRepr.NoPrefix, a1)
            val objT = TypeRepr.TypeRef(TypeRepr.NoPrefix, objectSym)
            val pe  = param("elem", a1t)
            val res = if b.row.scalaName == "contains" then tpe(boolSym) else tpe(intSym)
            defd(b.row.scalaName, List(pe), res,
                 callJ(List(Tree.Typed(ref(pe), TypeTree(objT, o), objT, o)), res),
                 List(Tree.TypeDef(a1, TypeTree(TypeRepr.TypeBounds(a, TypeRepr.NoType), o), o)))
          case ("remove", 2) =>
            val pi = param("idx", tpe(intSym)); val pc = param("count", tpe(intSym))
            defd("remove", List(pi, pc), unitTpe,
                 helper("bufferRemoveRange", List(ref(pi), ref(pc)), unitTpe))
          case ("insertAll", 2) =>
            val pi = param("idx", tpe(intSym)); val pe = param("elems", tpe(iterableOnceSym, a))
            defd("insertAll", List(pi, pe), unitTpe,
                 helper("bufferInsertAll", List(ref(pi), ref(pe)), unitTpe))
          case ("patchInPlace", 3) =>
            val pf = param("from", tpe(intSym)); val pp = param("patch", tpe(iterableOnceSym, a))
            val pr = param("replaced", tpe(intSym))
            defd("patchInPlace", List(pf, pp, pr), selfT,
                 Tree.Block(List(helper("bufferPatchInPlace", List(ref(pf), ref(pp), ref(pr)), unitTpe)),
                            self, selfT, o))
          case _ => scala.None
      case _ => scala.None

  /** DECISION PROVENANCE for one bridge — see [[Decision.Kind.BridgedMember]] for what the detail
    * has to carry and why. `Reason.Universal`, because there is no key: java said
    * `implements java.util.Map`, this phase chose the target, and telling the reader to edit a
    * scope would cost them the session §4.45 is about. */
  private def recordBridge(b: Bridge, sym: SymId, fqn: String, delegate: String, o: Origin): Unit =
    record(Decision(
      kind       = Decision.Kind.BridgedMember,
      subject    = sym,
      subjectFqn = fqn,
      detail = Map(
        "member"   -> s"${b.row.scalaName}/${b.row.arity}",
        "parent"   -> b.kind.toString,
        "delegate" -> delegate,
        "why" -> ("the parent this phase minted declares this member and java's own is the wrong " +
          "SHAPE for it, so java's member was RENAMED and scala's is synthesised over it. " +
          "Retyping java's member instead would close the same error and delete whatever its " +
          "result type was carrying; a rename moves a name and nothing else, and §4.55's machinery " +
          "re-points every reference exactly"),
      ),
      reason = Reason.Universal("minted-parent-surface(§1, K28.1)"),
      origin = o,
    ))

  /** Records the `super` -> `this` substitution per declaration, filtered to classes whose
    * `super` this run substituted. ENGINE-LIMITS K29 */
  private def recordSuperDefaults(using p: Program): Unit =
    if superDefaults.isEmpty then return
    val classes = superDefaults.map(_._1).toSet
    superDefaults.toList.groupBy(r => (r._2, r._3)).foreach { case ((callee, member), _) =>
      Decision.declarationsUsing(p, callee)
        .filter((encl, _) => p.symbolOf(encl).exists(s => classes.contains(s.owner)))
        .foreach { (encl, o) =>
          record(Decision(
            kind       = Decision.Kind.SubstitutedCall,
            subject    = encl,
            subjectFqn = Decision.fqnOf(p, encl, member),
            detail = Map(
              "was"        -> s"super.$member",
              "now"        -> s"balticporter.runtime.JavaCollections.$member(this, …)",
              "jdkDefault" -> CollectionsTransform.VirtualJdkDefaultBodies(member),
              "why" -> ("this class's emitted PARENT is a scala collection this phase minted, so " +
                "the JDK default `super` named is gone and no configuration key can bring it back. " +
                "The helper reproduces that default's own body, which dispatches VIRTUALLY through " +
                "`this` — so standing it on `this` is what `super` meant, and is licensed for this " +
                "member and not in general"),
            ),
            reason = Reason.Universal("jdk-default-at-this(§1)"),
            origin = o,
          ))
        }
    }

  /** One decision row per [[applyClassFileOverrides]] retention — the other §1 classification
    * from [[recordScopedOut]], since a reader told to widen a scope that does not exist has been
    * sent after a key nothing in the port can supply. CLAUDE.md §4.45 */
  private def recordRetainedSignatures(before: SymbolTable)(using p: Program): Unit =
    if retainedOverrides.isEmpty then return
    before.all.foreach { s =>
      if retainedOverrides(s.id) && mentionsMapped(s.info) && Decision.isDeclaration(p, s) then
        record(Decision(
          kind       = Decision.Kind.RetainedSignature,
          subject    = s.id,
          subjectFqn = s.fullName,
          detail = Map(
            "kept"      -> TirPrinter.tpe(s.info, TirPrinter.Style.canonical),
            "overrides" -> retainedAnchors.getOrElse(s.id, "?"),
            "why" -> ("this member OVERRIDES a declaration in a compiled class file, whose " +
              "signature no phase may move — retyped, it would override nothing and its own " +
              "`super` call could not compile. Nothing in this port's configuration changes " +
              "that; the seam moves to the callers, where it is counted"),
          ),
          reason = Reason.Universal("class-file-override(§4.56)"),
          origin = Decision.originOf(p, s.id),
        ))
    }

  /** Record a `Decision` for each declaration whose `info` moved between before/after symbol tables.
    * `Reason.Universal` for the default scope; `Reason.Configured` under `RuleScope.Only`. */
  private def recordRetypings(before: SymbolTable, after: SymbolTable)(using p: Program): Unit =
    before.all.foreach { s =>
      after.get(s.id).foreach { now =>
        if now.info != s.info && Decision.isDeclaration(p, s) then
          val (reason, why) = admittedBy.get(s.id) match
            case Some(entry) =>
              (Reason.Configured(name, entry),
               "this port's collections scope admits this declaration (directly, or through a " +
                 "pure-move flow from something it names), and a signature that moves without its " +
                 "call sites is a compile error one call away")
            // a retarget entry is per-library policy, so it may not read as the engine's own doing (§4.45).
            case scala.None if retargetKeysIn(s.info).nonEmpty =>
              val ks = retargetKeysIn(s.info).toList.sorted
              (Reason.Configured(name, ks.map(k => s"$k -> ${effectiveRetarget(k)}").mkString(", ")),
               "this port RETARGETS the type at every occurrence: the scala counterpart is usable " +
                 "wherever the java one was, so the declaration moves with no bridge and no " +
                 "call-shape change")
            case scala.None =>
              (Reason.Universal("collections-retype"),
               "a JDK collection type has a scala counterpart on every backend, and the JDK's own " +
                 "is on none of them")
          record(Decision(
            kind       = Decision.Kind.RetypedSignature,
            subject    = s.id,
            subjectFqn = s.fullName,
            detail = Map(
              // no key: where admittedBy supplied one, reason above already carries it.
              "from" -> TirPrinter.tpe(s.info, TirPrinter.Style.canonical),
              "to"   -> TirPrinter.tpe(now.info, TirPrinter.Style.canonical),
              "why"  -> why,
            ),
            reason = reason,
            origin = Decision.originOf(p, s.id),
          ))
          // order-keeping targets are catalog row JS-C42, discharged by the table rather than
          // per-site, since the same arm lowers every type reference.
          if mentionsOrderedShim(now.info) then
            cite(balticporter.catalog.JS.C(42), s.fullName)
      }
    }

  /** does this signature mention one of the two ordinal-order shims anywhere inside it? Read off
    * the phase's own mapping (§4.56) and walked with `StandardTraversal.mapType`, not a private
    * recursion that would miss a `MethodType`'s parameters. */
  private def mentionsOrderedShim(t: TypeRepr)(using Program): Boolean =
    val targets = Set(byScalaSym(CollectionsTransform.JavaEnumMapFqn),
                      byScalaSym(CollectionsTransform.JavaEnumSetFqn)) - SymId.None
    if targets.isEmpty then false
    else
      var found = false
      val scan = new Phase:
        def name = "ordered-shim-scan"
        override def transformType(x: TypeRepr)(using Program): TypeRepr =
          x match
            case TypeRepr.TypeRef(_, s) if targets.contains(s) => found = true
            case _                                             => ()
          x
      StandardTraversal.mapType(scan, t)
      found

  /** which retarget entries this signature mentions, anywhere inside it — `Set.empty` when
    * none. Walked with [[StandardTraversal.mapType]], not a private recursion, or every method
    * would silently answer "no retarget" and be attributed to the engine instead of the manifest. */
  private def retargetKeysIn(t: TypeRepr)(using Program): Set[String] =
    if effectiveRetarget.isEmpty then Set.empty
    else
      val seen = collection.mutable.Set.empty[String]
      val scan = new Phase:
        def name = "retarget-scan"
        override def transformType(x: TypeRepr)(using p: Program): TypeRepr =
          x match
            case TypeRepr.TypeRef(_, s) =>
              p.symbolOf(s).map(_.fullName).filter(effectiveRetarget.contains).foreach(seen += _)
            case _ => ()
          x
      StandardTraversal.mapType(scan, t)
      seen.toSet

  /** true when a retarget arg mapping can be resolved without any source type args — every
    * leaf is a `FixedType`, and `Applied` entries contain only fixed leaves. */
  private def allFixed(mapping: List[CollectionsTransform.RetargetArg]): Boolean =
    def isFixed(a: CollectionsTransform.RetargetArg): Boolean = a match
      case _: CollectionsTransform.RetargetArg.FixedType => true
      case CollectionsTransform.RetargetArg.Applied(_, inner) => inner.forall(isFixed)
      case _ => false
    mapping.forall(isFixed)

  override def transformType(t: TypeRepr)(using Program): TypeRepr = t match
    case TypeRepr.TypeRef(prefix, s) if remap.contains(s) =>
      val newSym = remap(s)
      retargetArgsBySource.get(s) match
        case Some(mapping) if allFixed(mapping) =>
          // arity-changing retarget with a zero-param source (IntIntMap -> ObjectMap[Int,Int]):
          // every target arg is fixed, so this TypeRef never appears as an AppliedType's tycon.
          // The only case where transformType may return an AppliedType for a bare TypeRef.
          val args = mapping.map(resolveRetargetArg(_, Nil))
          TypeRepr.AppliedType(TypeRepr.TypeRef(prefix, newSym), args)
        case _ =>
          // same-arity retarget, or arity-changing with SourceArg entries: the frontend already
          // filled the type params, so this TypeRef is an AppliedType's tycon; the arm below rearranges args.
          TypeRepr.TypeRef(prefix, newSym)
    case TypeRepr.AppliedType(TypeRepr.TypeRef(prefix, s), existingArgs) if retargetArgsByTarget.contains(s) =>
      // Arity-changing retarget with existing type args: rearrange the args according to the
      // mapping. By the time this runs, `s` is already the TARGET sym (mapType recurses into the
      // tycon first and transformType swaps it above), and the args have already been recursed into.
      val mapping = retargetArgsByTarget(s)
      val newArgs = mapping.map(resolveRetargetArg(_, existingArgs))
      TypeRepr.AppliedType(TypeRepr.TypeRef(prefix, s), newArgs)
    case TypeRepr.AppliedType(tc @ TypeRepr.TypeRef(_, s), args) if remapTargets.contains(s) && args.exists(_.isInstanceOf[TypeRepr.TypeBounds]) =>
      // strip wildcard bounds on same-arity retarget targets (invariant, so a wildcard is invalid).
      // Lower-bounded only: stripping an upper bound on a raw-type occurrence breaks invariant
      // sites passing DynamicArray[N <: Node] through the raw slot.
      val stripped = args.map {
        case TypeRepr.TypeBounds(lo, _) if lo != TypeRepr.NoType => lo
        case a => a
      }
      if stripped == args then t else TypeRepr.AppliedType(tc, stripped)
    // FQN fallback for un-remapped SymIds: a dependent port may hold a second SymId for a
    // retarget source, interned from the base's resolution root and never seen by remap — every
    // SymId for one source FQN must map to the same target, or the port names both halves.
    case TypeRepr.TypeRef(prefix, s) if !remap.contains(s) && s != SymId.None =>
      summon[Program].symbolOf(s) match
        case Some(sym) =>
          remapByFullName.get(sym.fullName) match
            case Some(newSym) =>
              // same logic as the primary remap path, looked up by the fullName-matched original SymId.
              retargetArgsBySource.get(s).orElse(
                remap.collectFirst { case (srcId, `newSym`) if retargetArgsBySource.contains(srcId) =>
                  retargetArgsBySource(srcId) }
              ) match
                case Some(mapping) if allFixed(mapping) =>
                  val args = mapping.map(resolveRetargetArg(_, Nil))
                  TypeRepr.AppliedType(TypeRepr.TypeRef(prefix, newSym), args)
                case _ =>
                  TypeRepr.TypeRef(prefix, newSym)
            case None => t
        case None => t
    case other => other

  private def resolveRetargetArg(arg: CollectionsTransform.RetargetArg, sourceArgs: List[TypeRepr]): TypeRepr =
    arg match
      case CollectionsTransform.RetargetArg.SourceArg(i) =>
        if i < sourceArgs.size then
          // strip wildcard bounds on arity-changing retarget args: the target is invariant, so a
          // wildcard SourceArg is invalid — take the lower bound when present, else the upper.
          sourceArgs(i) match
            case TypeRepr.TypeBounds(lo, hi) =>
              if lo != TypeRepr.NoType then lo
              else if hi != TypeRepr.NoType then hi
              else TypeRepr.AnyBounds
            case other => other
        else TypeRepr.AnyBounds // raw source — fill with ?
      case CollectionsTransform.RetargetArg.FixedType(fqn) =>
        retargetFixedTypeSyms.get(fqn) match
          case Some(sym) => TypeRepr.TypeRef(TypeRepr.NoPrefix, sym)
          case None      => TypeRepr.AnyBounds // should not happen if validated
      case CollectionsTransform.RetargetArg.Applied(fqn, innerArgs) =>
        // 3.1aw-3: a composed type — resolve the type constructor and recursively resolve
        // each inner arg. E.g. Applied("scala.Tuple2", List(SourceArg(0), SourceArg(1)))
        // produces AppliedType(TypeRef(Tuple2), List(K, V)).
        retargetFixedTypeSyms.get(fqn) match
          case Some(sym) =>
            val resolved = innerArgs.map(resolveRetargetArg(_, sourceArgs))
            TypeRepr.AppliedType(TypeRepr.TypeRef(TypeRepr.NoPrefix, sym), resolved)
          case None => TypeRepr.AnyBounds

  // ---- Reified carrier type arguments — preserved in java's namespace ----
  // // ENGINE-LIMITS K20

  /** WHICH type constructors' arguments this run must not move — the carriers, resolved to this
    * program's own symbols. `false` by arithmetic where the port declares none and the program names
    * no `java.lang.Class`, which is the §1(b) no-op with no code path. */
  override def preservesTypeArgsOf(tc: TypeRepr)(using Program): Boolean =
    carrierSyms.nonEmpty && headSym(tc).exists(carrierSyms.contains)

  /** Records why a reified type-argument (K20) at a declaration was preserved rather than retyped,
    * one row per declaration. `Universal` for `java.lang.Class`, `Configured` for a port-declared
    * carrier. Only fires where the argument mentions a type this phase maps — an untouched carrier
    * decided nothing and would be noise. // CLAUDE.md §4.56, K20 */
  private def recordReifiedTypeArgs(after: SymbolTable)(using p: Program): Unit =
    if carrierSyms.isEmpty then return
    after.all.foreach { s =>
      if Decision.isDeclaration(p, s) then
        val hits = preservedCarrierArgs(s.info)
        if hits.nonEmpty then
          val carriers = hits.map(_._1).distinct.sorted
          val (reason, why) = carriers.filterNot(CollectionsTransform.UniversalCarriers) match
            case Nil =>
              (Reason.Universal("reified-type-arg"),
               "`java.lang.Class` is reified by java itself, so this argument names the class the " +
                 "JVM will be asked for at run time and not a slot — retyped, it would name a " +
                 "scala type no class file has")
            case declared =>
              (Reason.Configured(name, declared.mkString(", ")),
               "this port declares the carrier as one whose type arguments a third party reads " +
                 "back out of the class file's generic signature and CONSTRUCTS from, so the " +
                 "argument stays java's and the value is bridged where it is used")
          record(Decision(
            kind       = Decision.Kind.ReifiedTypeArg,
            subject    = s.id,
            subjectFqn = s.fullName,
            detail = Map(
              "carrier" -> carriers.mkString(","),
              "kept"    -> hits.map((_, a) => TirPrinter.tpe(a, TirPrinter.Style.canonical)).distinct.sorted.mkString(","),
              "why"     -> why,
            ),
            reason = reason,
            origin = Decision.originOf(p, s.id),
          ))
    }

  /** Records the egress bridge (K21 face 1) at each declaration handing a value to a declared
    * reflective sink, keyed on the sink FQN. No porter note: the emitted call already names the
    * bridge. // CLAUDE.md §4.56, K21 */
  private def recordEgressBridges()(using p: Program): Unit =
    bridgedSinkCallees.toList.sortBy((m, fqn) => (fqn, m.raw)).foreach { (callee, sink) =>
      val calleeName = p.symbolOf(callee).map(_.fullName).getOrElse("?")
      Decision.declarationsUsing(p, callee).foreach { (encl, origin) =>
        record(Decision(
          kind       = Decision.Kind.BridgedEgress,
          subject    = encl,
          subjectFqn = Decision.fqnOf(p, encl, calleeName),
          detail = Map(
            "sink"   -> sink,
            "callee" -> calleeName,
            "why"    -> ("this port declares the callee's owner as a type that reads the RUNTIME " +
              "representation of what it is handed, so a collection this phase retyped is " +
              "presented as java's at the call — the formal is `java.lang.Object`, which is why " +
              "the port compiles either way and nothing static can see the difference"),
          ),
          reason = Reason.Configured(name, sink),
          origin = origin,
        ))
      }
    }

  /** Every (carrier FQN, preserved argument) pair in a signature whose argument mentions a type this
    * phase maps. Walked with [[StandardTraversal.mapType]], never a private recursion. // CLAUDE.md §3 */
  private def preservedCarrierArgs(t: TypeRepr)(using Program): List[(String, TypeRepr)] =
    val hits = collection.mutable.ListBuffer[(String, TypeRepr)]()
    val scan = new Phase:
      def name = "reified-carrier-scan"
      override def transformType(x: TypeRepr)(using p: Program): TypeRepr =
        x match
          case TypeRepr.AppliedType(tc, as) =>
            for
              h <- headSym(tc).toList if carrierSyms.contains(h)
              fqn <- p.symbolOf(h).map(_.fullName).toList
              a <- as if mentionsMapped(a)
            do hits += (fqn -> a)
          case _ => ()
        x
    StandardTraversal.mapType(scan, t)
    hits.toList

  /** does this type mention a java type THIS PHASE maps? The question `mentionsRetyped` asks in the
    * other direction — that one reads the types the phase PRODUCED, this one the keys it consumes —
    * and both are §4.56's "conclude only from what the phase itself did". */
  private def mentionsMapped(t: TypeRepr)(using Program): Boolean =
    var hit = false
    val scan = new Phase:
      def name = "mapped-mention-scan"
      override def transformType(x: TypeRepr)(using Program): TypeRepr =
        x match
          case TypeRepr.TypeRef(_, s) if remap.contains(s) => hit = true
          case _                                           => ()
        x
    StandardTraversal.mapType(scan, t)
    hit

  /** A `val`'s declared type is an expected type exactly as a formal parameter is, so it routes to
    * the same [[coerce]] every other position does. */
  override def transformValDef(t: Tree.ValDef)(using Program): Tree.ValDef =
    citeIfReified(t.symbol)
    transformValDefRhs(t)

  private def transformValDefRhs(t: Tree.ValDef)(using Program): Tree.ValDef = t.rhs match
    // a DECLARED slot is an expected type exactly as a formal parameter is — see `coerce`.
    case Some(rhs) => t.copy(rhs = Some(coerce(t.tpt.tpe, rhs, literal(t.symbol))))
    case _ => t

  /** An assignment's LHS declares the expected type as a `val`'s `tpt` does; a cast this phase made
    * IMPOSSIBLE (both sides sent to unrelated families) is dropped rather than emitted, turning a
    * runtime `ClassCastException` into a compile error. // CLAUDE.md §4.4, ENGINE-LIMITS M6 */
  override def transformTerm(t: Term)(using Program): Term = t match
    case a: Tree.Assign =>
      // FieldWrite: `recv.field = value` -> `recv.method(value)` on a retarget target.
      // Checked BEFORE the coercion path, because the field is NOT writable on the target
      // and the assignment would be a compile error.
      retargetFieldWrite(a).orElse(retargetIndexedFieldWrite(a)) match
        case Some(rewritten) => rewritten
        case scala.None =>
          // the TARGET may itself be a reference to a scoped-out declaration, in which case the slot
          // is a JDK one however the node reads — the same `actualOf` the argument side takes.
          val (want, wantScoped) = actualOf(a.lhs)
          a.copy(rhs = coerce(want, a.rhs, wantScoped))
    case ty: Tree.Typed if impossibleShimCast(ty) => ty.expr
    case ty: Tree.Typed   => reifiedCast(ty)
    case io: Tree.InstanceOf => reifiedTest(io)
    case fe: Tree.ForEach => retargetForEach(fe).getOrElse(writeThroughEntries(fe))
    case mr: Tree.MethodRef => lowerMethodRef(mr)
    case lit @ Tree.Literal(Constant.ClassOfC(tp), tpe, _) => retargetClassOf(lit, tp, tpe)
    case sel: Tree.Select => retargetSelectRewrite(sel).getOrElse(staticFieldRewrite(sel).getOrElse(externalFieldProducer(sel)))
    case aa: Tree.ArrayAccess => retargetIndexedField(aa).getOrElse(aa)
    case id: Tree.IncDec => retargetIncDec(id).getOrElse(id)
    case other          => other

  /** Entry copy-construction fold: `val e2 = Tuple2(default, default); e2._1 = X; e2._2 = Y`
    * folds into `val e2 = Tuple2(X, Y)` — java's `Entry` has mutable public fields, `Tuple2` has
    * `val _1/_2`, so the copy-construct pattern is faithfully rewritten to construct with the
    * right values from the start. Guards: type head is `retargetEntryTargets`, assigns target
    * `_1`/`_2` contiguously right after the ValDef, variable not reassigned elsewhere. */
  override def transformBlock(b: Tree.Block)(using p: Program): Term =
    if retargetEntryTargets.isEmpty then b
    else foldEntryCopyConstruction(b)

  private def foldEntryCopyConstruction(b: Tree.Block)(using p: Program): Tree.Block =
    // scan stats for the pattern; build a new stats list with folded entries
    val newStats = scala.collection.mutable.ListBuffer.empty[Statement]
    var i = 0
    val stats = b.stats
    val len = stats.size
    var changed = false
    while i < len do
      stats(i) match
        case vd: Tree.ValDef if vd.rhs.isDefined =>
          // check if the variable's type head is an entry target (Tuple2)
          val isEntry = headSym(vd.tpt.tpe).exists(retargetEntryTargets.contains)
          if isEntry && i + 2 < len then
            // look for _1 and _2 assigns immediately following
            val (a1Opt, a2Opt) = (stats(i + 1), stats(i + 2)) match
              case (a1: Tree.Assign, a2: Tree.Assign) =>
                val a1Field = assignedEntryField(vd.symbol, a1)
                val a2Field = assignedEntryField(vd.symbol, a2)
                (a1Field, a2Field) match
                  case (Some(1), Some(2)) => (Some(a1.rhs), Some(a2.rhs))
                  case (Some(2), Some(1)) => (Some(a2.rhs), Some(a1.rhs))
                  case _                 => (scala.None, scala.None)
              case _ => (scala.None, scala.None)
            (a1Opt, a2Opt) match
              case (Some(rhs1), Some(rhs2)) =>
                // fold: replace the constructor's default args with the assign RHSes
                val newRhs = replaceConstructArgs(vd.rhs.get, rhs1, rhs2)
                newStats += vd.copy(rhs = Some(newRhs))
                i += 3 // skip the ValDef and both assigns
                changed = true
              case _ =>
                newStats += vd
                i += 1
          else
            newStats += vd
            i += 1
        case other =>
          newStats += other
          i += 1
    if changed then b.copy(stats = newStats.toList)
    else b

  /** is this assign writing to `_1` or `_2` of the given variable? */
  private def assignedEntryField(varSym: SymId, a: Tree.Assign): Option[Int] = a.lhs match
    case Tree.Select(Tree.Ident(`varSym`, _, _), m, _, _) =>
      if m == key1Sym then Some(1)
      else if m == value2Sym then Some(2)
      else scala.None
    case _ => scala.None

  /** replaces the first two arguments of a constructor/factory call with the given values. */
  private def replaceConstructArgs(rhs: Term, arg1: Term, arg2: Term): Term = rhs match
    case a @ Tree.Apply(fun, args, method, tpe, origin) if args.sizeIs >= 2 =>
      a.copy(args = arg1 :: arg2 :: args.drop(2))
    case other => other // should not happen for a retargetConstruct-produced Tuple2

  // -------------------------------------------------------------------------------------------
  // ---- Reified occurrences — instanceof/cast over retyped collections ----
  // // ENGINE-LIMITS K18

  /** java's `x instanceof T` where this phase retyped `T`. */
  private def reifiedTest(t: Tree.InstanceOf)(using p: Program): Term =
    reifiedTarget(t.tpt.tpe) match
      case scala.None => unmappedReified("reified type test", t.tpt.tpe, t.origin); t
      case Some(tgt)  => reifiedIsSyms.get(tgt) match
        case Some(f) =>
          reifiedHere = true
          Tree.Apply(Tree.Ident(f, TypeRepr.NoType, t.origin), List(t.expr), f, t.tpe, t.origin)
        case scala.None =>
          reifiedSeam("reified type test", t.tpt.tpe, t.origin)
          t

  /** java's `(T) x` where this phase retyped `T` and cannot vouch for what `x` produces. The
    * cast is KEPT and the coercion goes inside it — exact rather than tidy, since java's cast to a
    * generic type is unchecked in its type arguments (JLS 5.5), which the surviving `asInstanceOf`
    * expresses, while the coercion answers only the erased class java checked. */
  private def reifiedCast(t: Tree.Typed)(using p: Program): Term =
    // asked before vouched deliberately: vouched says the phase knows the value's own
    // representation, which at a target outside the mapping makes the divergence certain.
    if !isNullLiteral(t.expr) && reifiedTarget(t.tpt.tpe).isEmpty then
      unmappedReified("reified cast", t.tpt.tpe, t.origin)
    if vouched(t.expr) || isNullLiteral(t.expr) then t
    else reifiedTarget(t.tpt.tpe) match
      case scala.None => t
      case Some(tgt)  => reifiedAsSyms.get(tgt) match
        case Some(f) =>
          reifiedHere = true
          t.copy(expr = Tree.Apply(Tree.Ident(f, TypeRepr.NoType, t.origin), List(t.expr), f,
                                   t.tpt.tpe, t.origin))
        case scala.None =>
          reifiedSeam("reified cast", t.tpt.tpe, t.origin)
          t

  /** the head symbol of a type THIS PHASE produced, or `None` (§4.56, asked of the phase's own
    * tables, never a name) — nothing in java source names a scala collection or a runtime shim. */
  private def reifiedTarget(t: TypeRepr): Option[SymId] =
    headSym(t).filter(s => kindOf.contains(s) || shimSyms.contains(s))

  /** Can this phase vouch for the representation this expression produces? Yes for a
    * declaration it retyped or one the program owns; no for an external producer (K15). */
  private def vouched(e: Term)(using p: Program): Boolean =
    (reifiedTarget(e.tpe).isDefined || headSym(e.tpe).exists(p.owns)) && !foreignProducer(e)

  /** the one exception: a call or field read the program does not declare (`externalCallee`, K15). */
  private def foreignProducer(e: Term)(using p: Program): Boolean = e match
    case a: Tree.Apply  => externalCallee(a.method)
    case s: Tree.Select => externalCallee(s.sym)
    case _              => false

  /** `null` is an instance of nothing and a cast of it checks nothing — no runtime object for a
    * reified question. Left as-is, keeping `null.asInstanceOf[T]` recognisable downstream. */
  private def isNullLiteral(e: Term): Boolean = e match
    case Tree.Literal(Constant.NullC, _, _) => true
    case _                                  => false

  /** a reified occurrence at a target no live view can BE. Refused and counted (M6). */
  /** drains [[reifiedHere]] at the declaration the rewrite happened in. */
  private def citeIfReified(sym: SymId)(using p: Program): Unit =
    if reifiedHere then
      cite(balticporter.catalog.JS.G(48), p.symbolOf(sym).map(_.fullName).getOrElse(sym.toString))
      reifiedHere = false

  private def reifiedSeam(slot: String, target: TypeRepr, origin: Origin)(using Program): Unit =
    seam(slot, "a representation-agnostic test or coercion",
         TirPrinter.tpe(target, TirPrinter.Style.canonical), origin, SymId.None,
         CollectionBoundaryCheck.Issue.ReifiedOccurrence)

  /** reified occurrence at an unmapped JDK supertype (e.g. `RandomAccess`). Refused and
    * counted, derived from `typeMap`'s supertype closure. K18 */
  private def unmappedReified(slot: String, target: TypeRepr, origin: Origin)(using Program): Unit =
    if headSym(target).exists(unmappedSupertypeSyms) then
      seam(slot, "no coercion exists: the target is OUTSIDE the mapping, and a retyped value is not one",
           TirPrinter.tpe(target, TirPrinter.Style.canonical), origin, SymId.None,
           CollectionBoundaryCheck.Issue.ReifiedOccurrence)

  /** Rewrites `Collections.EMPTY_LIST/SET/MAP` to the typed `emptyList()`/etc. helper.
    * Consulted ahead of [[externalFieldProducer]] to avoid wrapping a raw type. */

  /** A `classOf[T]` literal whose inner type was retarget-mapped — syncs the `const` field to
    * match, since `mapTerm` remaps `tpe` but not the `Constant.ClassOfC` the emitter reads. Counted
    * on `collection-retarget`: a third party sees the lls class, not the upstream one (K20). */
  private def retargetClassOf(lit: Tree.Literal, tp: TypeRepr, tpe: TypeRepr)(using p: Program): Term =
    // maps only through retarget entries, never the JDK §1(a) table — a classOf on a JDK-table
    // source keeps java's class (K20: a reified carrier holds java's own class; fromJava bridges at the use).
    def mapInner(t: TypeRepr): TypeRepr = t match
      case TypeRepr.TypeRef(prefix, s) if remap.get(s).exists(retargetTargetToSource.contains) =>
        TypeRepr.TypeRef(prefix, remap(s))
      case TypeRepr.AppliedType(tc, as) =>
        val mc = mapInner(tc)
        TypeRepr.AppliedType(mc, as.map(mapInner))
      case other => other
    val mapped = mapInner(tp)
    if mapped != tp then
      headSym(mapped).foreach { h =>
        if retargetTargetToSource.contains(h) then
          seam("classOf at retarget type (K20)", "reified class literal",
               TirPrinter.tpe(mapped, TirPrinter.Style.canonical), lit.origin, SymId.None,
               issue = CollectionBoundaryCheck.Issue.ReifiedOccurrence)
      }
      lit.copy(const = Constant.ClassOfC(mapped))
    else lit

  /** A field write on a retarget target — `recv.field = value` -> `recv.method(value)` — for a
    * java field the target exposes only as a method. Keyed on symbol via
    * [[retargetTargetToSource]], never a name (§4.56). */
  private def retargetFieldWrite(a: Tree.Assign)(using p: Program): Option[Term] =
    if retargetRewrites.isEmpty && retargetRewritesByDesc.isEmpty then return scala.None
    a.lhs match
      case sel: Tree.Select =>
        headSym(sel.qual.tpe).flatMap(retargetTargetToSource.get).flatMap { srcFqn =>
          val mName = methodName(sel.sym)
          lookupRewrite(srcFqn, mName, 0, None).flatMap {
            case CollectionsTransform.RetargetRewrite.FieldWrite(_, method) =>
              retargetRewriteSyms.get((srcFqn, method)).map { tgtSym =>
                // compound assignment (size -= 1) expands to method(field op rhs)
                val effectiveRhs = a.compound match
                  case Some((op, narrow)) =>
                    compoundOps.get(op) match
                      case Some(opSym) =>
                        val binOp = Tree.Apply(
                          Tree.Select(sel, opSym, a.rhs.tpe, a.origin),
                          List(a.rhs), opSym, a.rhs.tpe, a.origin)
                        narrow.fold(binOp: Term)(nt =>
                          Tree.Typed(binOp, TypeTree(nt, a.origin), nt, a.origin))
                      case None => a.rhs // unknown operator, fall through to simple assign
                  case None => a.rhs
                Tree.Apply(
                  Tree.Select(sel.qual, tgtSym, TypeRepr.NoType, a.origin),
                  List(effectiveRhs), tgtSym, TypeRepr.NoType, a.origin)
              }
            case _ => scala.None
          }
        }
      case _ => scala.None

  /** A pre-/post-increment/decrement on a retarget FieldWrite field. Java's `--stack.size` emits
    * `{ stack.size -= 1; stack.size }`, which does not compile against a read-only `def` — the
    * faithful image is `{ setSize(size - 1); size }` (pre) or a temp-bound post form, as a
    * `Tree.Block`. */
  private def retargetIncDec(id: Tree.IncDec)(using p: Program): Option[Term] =
    if retargetRewrites.isEmpty && retargetRewritesByDesc.isEmpty then return scala.None
    id.target match
      case sel: Tree.Select =>
        headSym(sel.qual.tpe).flatMap(retargetTargetToSource.get).flatMap { srcFqn =>
          val mName = methodName(sel.sym)
          lookupRewrite(srcFqn, mName, 0, None).flatMap {
            case CollectionsTransform.RetargetRewrite.FieldWrite(_, method) =>
              retargetRewriteSyms.get((srcFqn, method)).flatMap { tgtSym =>
                compoundOps.get(id.op).map { opSym =>
                  val one = Tree.Literal(balticporter.tir.Constant.IntC(1), id.tpe, id.origin)
                  val binOp = Tree.Apply(
                    Tree.Select(sel, opSym, id.tpe, id.origin),
                    List(one), opSym, id.tpe, id.origin)
                  val call = Tree.Apply(
                    Tree.Select(sel.qual, tgtSym, TypeRepr.NoType, id.origin),
                    List(binOp), tgtSym, TypeRepr.NoType, id.origin)
                  if !id.post then
                    Tree.Block(List(call), sel, id.tpe, id.origin)
                  else
                    // post-decrement needs a temp whose SymId cannot be minted here; counted on collection-retarget.
                    return scala.None
                }
              }
            case _ => scala.None
          }
        }
      case _ => scala.None

  /** An indexed field read on a retarget target — `arr.items[i]` -> `arr.apply(i)`. Matches on
    * the SOURCE member's SymId ([[indexedFieldSyms]]), not through `retargetTargetToSource`,
    * since the bottom-up traversal has already remapped the receiver's type by the time this
    * arm sees the `ArrayAccess`. */
  private def retargetIndexedField(aa: Tree.ArrayAccess)(using p: Program): Option[Term] =
    if indexedFieldSyms.isEmpty then return scala.None
    aa.array match
      case sel: Tree.Select =>
        indexedFieldSyms.get(sel.sym).flatMap { srcFqn =>
          val applySym = retargetRewriteSyms.getOrElse((srcFqn, "apply"),
            byScalaSyms.getOrElse("apply", updateSym))
          Some(Tree.Apply(
            Tree.Select(sel.qual, applySym, aa.tpe, aa.origin),
            List(aa.index), applySym, aa.tpe, aa.origin))
        }
      case _ => scala.None

  /** An indexed field write — `arr.items[i] = v` -> `arr.update(i, v)`. Same SymId-based
    * matching as [[retargetIndexedField]]. */
  private def retargetIndexedFieldWrite(a: Tree.Assign)(using p: Program): Option[Term] =
    if indexedFieldSyms.isEmpty then return scala.None
    a.lhs match
      case aa: Tree.ArrayAccess => aa.array match
        case sel: Tree.Select =>
          indexedFieldSyms.get(sel.sym).map { srcFqn =>
            Tree.Apply(
              Tree.Select(sel.qual, updateSym, TypeRepr.NoType, a.origin),
              List(aa.index, a.rhs), updateSym, TypeRepr.NoType, a.origin)
          }
        case _ => scala.None
      // children are mapped before this method sees the Assign, so the LHS ArrayAccess has
      // already become Apply(Select(recv, applySym), List(idx)) — match that shape.
      case app: Tree.Apply => app.fun match
        case sel: Tree.Select if app.args.size == 1 && methodName(sel.sym) == "apply" =>
          val recv = sel.qual
          headSym(recv.tpe).flatMap(retargetTargetToSource.get).map { _ =>
            Tree.Apply(
              Tree.Select(recv, updateSym, TypeRepr.NoType, a.origin),
              List(app.args.head, a.rhs), updateSym, TypeRepr.NoType, a.origin)
          }
        case _ => scala.None
      case _ => scala.None

  /** A field access on a retarget target — `entry.key` -> `entry._1`, `entry.value` -> `entry._2`.
    * [[retargetRewrite]] handles call sites; a bare field select has no call node for it to see.
    * Keyed on symbol (§4.56), not a name. */
  private def retargetSelectRewrite(sel: Tree.Select)(using p: Program): Option[Term] =
    // Entry field rewrites: .key/.value -> ._1/._2
    val entryResult =
      if retargetEntryTargets.isEmpty then scala.None
      else headSym(sel.qual.tpe).flatMap { h =>
        if !retargetEntryTargets.contains(h) then scala.None
        else
          val mName = methodName(sel.sym)
          if mName == "key" || mName == "getKey" then
            Some(Tree.Select(sel.qual, key1Sym, sel.tpe, sel.origin))
          else if mName == "value" || mName == "getValue" then
            Some(Tree.Select(sel.qual, value2Sym, sel.tpe, sel.origin))
          else scala.None
      }
    if entryResult.isDefined then return entryResult
    // rename entries at a Select (nullary property access, e.g. bean-renamed isEmpty -> empty):
    // retargetRewrite fires only on Tree.Apply, so this handles the Tree.Select form.
    if retargetRewrites.nonEmpty || retargetRewritesByDesc.nonEmpty then
      val selHead = headSym(sel.qual.tpe)
      selHead.flatMap(retargetSourceOf).orElse(
        for
          mSym <- p.symbolOf(sel.sym)
          oSym <- p.symbolOf(mSym.owner)
          if effectiveRetarget.contains(oSym.fullName)
        yield oSym.fullName
      ).flatMap { srcFqn =>
        val mName = methodName(sel.sym)
        val rhs = selHead.getOrElse(SymId.None)
        lookupRewriteForReceiver(rhs, srcFqn, mName, 0, None, resolveRecvOrigin(sel.qual)).flatMap {
          case CollectionsTransform.RetargetRewrite.Rename(target) =>
            retargetRewriteSyms.get((srcFqn, target)).map { tgtSym =>
              Tree.Select(sel.qual, tgtSym, sel.tpe, sel.origin)
            }
          // a parameterless iterator on a retarget target whose declared return is JavaIterator[T]
          // (java.util.Iterator redirect): NullaryArityTransform already made this a Select, so
          // the Chain handler in retargetRewrite never sees it — wrap with JavaIterator.from.
          case CollectionsTransform.RetargetRewrite.Chain(members, _, _)
              if members.lastOption.contains("iterator") && iteratorFromSym != SymId.None =>
            // K36: for targets supporting indexed removal, emit a removing iterator over the receiver.
            val targetFqn = effectiveRetarget.get(srcFqn)
            val removingResult = targetFqn.flatMap(tgt => emitRemovingIterator(sel.qual, tgt, sel.tpe, sel.origin))
            if removingResult.isDefined then Some(removingResult.get)
            else
              Some(Tree.Apply(Tree.Ident(iteratorFromSym, TypeRepr.NoType, sel.origin),
                              List(sel), iteratorFromSym, sel.tpe, sel.origin))
          // Chain at a Select (parenless, made so by bean-property/NullaryArityTransform):
          // apply with no arguments, same logic as the Apply path. The outer Apply may still
          // wrap this in () if java called it with (); tracked in selectChainRewritten to strip it.
          case CollectionsTransform.RetargetRewrite.Chain(members, hasParens, _) if members.nonEmpty =>
            val syms = members.flatMap(m => retargetRewriteSyms.get((srcFqn, m)))
            if syms.size != members.size then scala.None
            else
              var cur: Term =
                if hasParens(members.head) then
                  Tree.Apply(Tree.Select(sel.qual, syms.head, TypeRepr.NoType, sel.origin),
                             Nil, syms.head, TypeRepr.NoType, sel.origin)
                else
                  Tree.Select(sel.qual, syms.head, TypeRepr.NoType, sel.origin)
              syms.tail.zip(members.tail).foreach { (s, mName) =>
                if hasParens(mName) then
                  cur = Tree.Apply(Tree.Select(cur, s, TypeRepr.NoType, sel.origin),
                                   Nil, s, TypeRepr.NoType, sel.origin)
                else
                  cur = Tree.Select(cur, s, TypeRepr.NoType, sel.origin)
              }
              selectChainRewritten.add(cur)
              Some(cur)
          // Template at a Select (parenless): a Template expression with no arguments — the
          // member was made parenless but the rewrite needs a template (e.g.
          // `("length", 0) -> Template("(if ($recv.isEmpty) 0 else $recv.last + 1)")`).
          // Rendered with an empty argument list; only $recv and type-level placeholders
          // ($T0, $Target) are available. Same caveat as Chain above — tracked for the Apply path.
          case CollectionsTransform.RetargetRewrite.Template(expr) =>
            val result = renderTemplate(expr, sel.qual, Nil, srcFqn, sel.tpe, sel.origin)
            selectChainRewritten.add(result)
            Some(result)
          // IndexedField is NOT handled here — it fires only on Tree.ArrayAccess (see
          // retargetIndexedField). Stripping the field select on a bare Tree.Select would turn
          // `someMethod(arr.items)` into `someMethod(arr)`, changing the type from Array[T] to
          // DynamicArray[T] and opening new E007 errors. The rewrite must be scoped to the
          // ArrayAccess node that actually indexes into the backing array.
          case _ => scala.None
        }
      }
    else scala.None

  private def staticFieldRewrite(sel: Tree.Select)(using p: Program): Option[Term] =
    for
      m   <- p.symbolOf(sel.sym)
      o   <- p.symbolOf(m.owner)
      nm  <- CollectionsTransform.StaticFieldFactories.get(MemberKey(o.fullName, m.name).render)
      f    = sym(nm)
      if f != SymId.None
    yield Tree.Apply(Tree.Ident(f, TypeRepr.NoType, sel.origin), Nil, f, sel.tpe, sel.origin)

  /** Wrap an external FIELD whose class-file type is a mapped collection. Uses `declaredFieldHead`
    * (not the node type) to distinguish fields from methods. // ENGINE-LIMITS K15 */
  private def externalFieldProducer(sel: Tree.Select)(using p: Program): Term =
    if fromJavaSym == SymId.None || !externalCallee(sel.sym) then sel
    else declaredFieldHead(sel.sym) match
      case scala.None => sel
      case Some(_)    => headSym(sel.tpe).filter(liveWrappableSyms.contains) match
        case scala.None => sel
        case Some(_) if mentionsRetyped(sel.tpe) =>
          seam("external field (nested element)", "a one-level wrap",
               TirPrinter.tpe(sel.tpe, TirPrinter.Style.canonical), sel.origin, sel.sym)
          sel
        case Some(_) =>
          Tree.Apply(Tree.Ident(fromJavaSym, TypeRepr.NoType, sel.origin), List(sel),
                     fromJavaSym, sel.tpe, sel.origin)

  /** the head of a symbol's declared type where that type is a FIELD's — `None` for a method (whose
    * `info` is a `MethodType`), for an unreadable class file (`NoType`), and for anything the
    * mapping does not cover. See [[externalFieldProducer]] for why the method/field distinction has
    * to come from here and cannot come from the node. */
  private def declaredFieldHead(s: SymId)(using p: Program): Option[SymId] =
    p.symbolOf(s).map(_.info).flatMap {
      case _: TypeRepr.MethodType => scala.None
      case TypeRepr.NoType        => scala.None
      case t                      => headSym(t).filter(h => p.symbolOf(h).exists(x => typeMap.contains(x.fullName)))
    }

  /** A METHOD REFERENCE at a member this phase rewrites — `Map.Entry::getKey` inside a stream.
    *
    * Lowers the reference into a lambda with the rewritten term as body.
    * Bound references (`expr::m`) bind the receiver ONCE (JLS 15.13.3).
    * Parameters left unannotated (scalac infers from expected function type). */
  private def lowerMethodRef(mr: Tree.MethodRef)(using p: Program): Term =
    if selfParamSym == SymId.None then return mr
    // the NODE's answer and not the symbol's, which is the same one derivation the emitter reads
    // (`Tree.MethodRef.referent`, F8): an external member is interned with no `Flags`, so
    // `flags.isStatic` reads `false` for every JDK static and this phase would lower one.
    mr.qualifier match
      case Left(tt) if !mr.referent.isInstanceOf[Referent.Static] =>
        kindOf.get(headSym(tt.tpe).getOrElse(SymId.None)) match
          case None    => mr
          case Some(k) =>
            val o    = mr.origin
            val self = Tree.Ident(selfParamSym, tt.tpe, o)
            // the `Apply` the reference stands for, so the rewrite runs against the same shape it
            // was written for. Its result type is the reference's own, which for a method VALUE is
            // the functional interface — unused by every arm that answers here (they read the
            // RECEIVER's kind), and honest about what is known.
            val callT = Tree.Apply(Tree.Select(self, mr.method, TypeRepr.NoType, o), Nil,
                                   mr.method, TypeRepr.NoType, o)
            rewrite(k, self, mr.method, o, callT) match
              case None       => mr
              case Some(body) =>
                val param = Tree.ValDef(selfParamSym, TypeTree(TypeRepr.NoType, o), scala.None, o)
                Tree.Lambda(List(param), body, mr.tpe, o)
      // …and the BOUND form, whose receiver is a TERM. The arity is java's, off the node
      // (`Tree.MethodRef.referent` — `G27`'s field, and the same one the emitter's own expansion
      // reads), never off the symbol: an external member is interned with no `MethodType` and would
      // read as taking no arguments.
      case Right(recv) if recvBindSym != SymId.None =>
        kindOf.get(headSym(recv.tpe).getOrElse(SymId.None)) match
          case None    => mr
          case Some(k) =>
            val arity = mr.referent match
              case Referent.Instance(n) => n
              case Referent.Static(_)   => -1 // a bound reference is never static; decline rather than guess
            if arity < 0 || arity > argParamSyms.size then mr
            else
              val o = mr.origin
              // java evaluated the qualifier ONCE, at reference creation — see the doc above.
              val (self, stats) = recv match
                case _: Tree.This => (recv, Nil)
                case _            =>
                  (Tree.Ident(recvBindSym, recv.tpe, o),
                   List(Tree.ValDef(recvBindSym, TypeTree(recv.tpe, o), Some(recv), o)))
              val ps   = argParamSyms.take(arity).toList
              val args = ps.map(s => Tree.Ident(s, TypeRepr.NoType, o))
              val callT = Tree.Apply(Tree.Select(self, mr.method, TypeRepr.NoType, o), args,
                                     mr.method, TypeRepr.NoType, o)
              rewrite(k, self, mr.method, o, callT) match
                case None       => mr
                case Some(body) =>
                  // UNANNOTATED for the arm above's reason: a reference is a poly expression and the
                  // target types its parameters, which is the job javac had.
                  val params = ps.map(s => Tree.ValDef(s, TypeTree(TypeRepr.NoType, o), scala.None, o))
                  val lam    = Tree.Lambda(params, body, mr.tpe, o)
                  if stats.isEmpty then lam else Tree.Block(stats, lam, mr.tpe, o)
      case _ => mr

  /** Rewrite `entry.setValue(v)` to `map.put(entry._1, v)` when the map is reachable from
    * the enclosing for-each loop. Guards: map-kind source, loop-binding receiver, pure
    * path, no reassignment. Detached entries (no loop) stay refused. // ENGINE-LIMITS K2 */
  /** Lower a for-each over a retarget target's entries/keys/values into a lambda-based
    * iteration method. `return` in body is refused and counted (non-local return).
    * Arity-2 rewrites `.key`/`.value` selects to lambda parameters. */
  private def retargetForEach(fe: Tree.ForEach)(using p: Program): Option[Term] =
    if retargetRewrites.isEmpty && retargetRewritesByDesc.isEmpty then return scala.None
    // receiver+member from `recv.member()`, or a bare Kind.Map reference — java's implicit
    // entry iteration, since the retarget removed the Iterable[Entry] parent.
    val (recv, memberSym, srcFqn) = fe.iterable match
      case Tree.Apply(Tree.Select(r, m, _, _), Nil, _, _, _) =>
        headSym(r.tpe).flatMap(retargetTargetToSource.get) match
          case Some(src) => (r, m, src)
          case _         => return scala.None
      case bareRef =>
        headSym(bareRef.tpe).flatMap(retargetTargetToSource.get) match
          case Some(src) if lookupRewrite(src, "entries", 0, None)
                .exists(_.isInstanceOf[CollectionsTransform.RetargetRewrite.ForEach]) =>
            (bareRef, SymId.None, src)
          case _ => return scala.None
    val mName = if memberSym == SymId.None then "entries" else methodName(memberSym)
    val rewrite = lookupRewrite(srcFqn, mName, 0, None) match
      case Some(rw: CollectionsTransform.RetargetRewrite.ForEach) => rw
      case Some(rw: CollectionsTransform.RetargetRewrite.Collect) =>
        CollectionsTransform.RetargetRewrite.ForEach(rw.via, 1)
      case _ => return scala.None
    val hasReturn = returnsInForEach(fe.body)
    // returnsInForEach stops at nested lambdas/defs/anon classes, so an inner loop's returns are
    // already break(v) and this reflects only this level's.
    val bound = fe.binding.symbol
    if bound == SymId.None then return scala.None
    if rewrite.arity == 2 then
      // arity-2: the binding must be used only via .key/.value selects
      if hasNonFieldUsage(bound, fe.body) then return scala.None
    // look up the minted symbol for the target method
    val tgtSym = retargetRewriteSyms.getOrElse((srcFqn, rewrite.targetMethod), SymId.None)
    if tgtSym == SymId.None then return scala.None
    val so = fe.origin
    // a `return` in the body becomes boundary.break(v)(using retFe$N); the Apply is registered
    // for wrapping in transformDefDef.
    val label = if hasReturn then { retFeSeq += 1; Some(s"retFe$$$retFeSeq") } else scala.None
    def bodyWithBreaks(body: Term): Term =
      if !hasReturn then body
      else rewriteReturnsToBreaks(body, label.get, so)
    // unique lambda parameter symbols per rewrite, or nested entry loops shadow each other
    val n = { val i = forEachSeq; forEachSeq += 1
      require(i < forEachKeyPool.length,
        s"CollectionsTransform: forEach lambda counter reached ${forEachKeyPool.length} — " +
          "pool exhausted (was 8, now 64; if a port genuinely needs more, grow the pool)")
      i }
    val apply =
      if rewrite.arity == 2 then
        // recv.foreachEntry((k, v) => body')
        val kTpe = keyType(recv.tpe).getOrElse(TypeRepr.NoType)
        val vTpe = valueType(recv.tpe).getOrElse(TypeRepr.NoType)
        val kSym = forEachKeyPool(n)
        val vSym = forEachValPool(n)
        val kParam = Tree.ValDef(kSym, TypeTree(kTpe, so), scala.None, so)
        val vParam = Tree.ValDef(vSym, TypeTree(vTpe, so), scala.None, so)
        val rewrittenBody = rewriteEntrySelects(bound, kSym, kTpe, vSym, vTpe, fe.body, so)
        val lambda = Tree.Lambda(List(kParam, vParam), bodyWithBreaks(rewrittenBody), unitTpe, so)
        Tree.Apply(Tree.Select(recv, tgtSym, TypeRepr.NoType, so), List(lambda), tgtSym, unitTpe, so)
      else
        // recv.foreachKey(k => body) or recv.foreachValue(v => body)
        val paramTpe = fe.binding.tpt.tpe
        val eSym = forEachElemPool(n)
        val param = Tree.ValDef(eSym, TypeTree(paramTpe, so), scala.None, so)
        val rewrittenBody = rewriteBindingRefs(bound, eSym, paramTpe, fe.body, so)
        val lambda = Tree.Lambda(List(param), bodyWithBreaks(rewrittenBody), unitTpe, so)
        Tree.Apply(Tree.Select(recv, tgtSym, TypeRepr.NoType, so), List(lambda), tgtSym, unitTpe, so)
    if hasReturn then retFeReturnApplies.put(apply, label.get)
    Some(apply)

  /** Emit a standalone `Collect` block: `{ val r$coN = Into[E](); recv.via(r$coN.add); r$coN }`,
    * for keys()/values() calls `retargetRewrite` left as `None` so `retargetForEach` could
    * consume the for-each iterables first. Built from TIR nodes (not `Tree.Opaque` text) so the
    * package rename reaches the element type FQN. */
  private def emitCollect(recv: Term, srcFqn: String,
      rw: CollectionsTransform.RetargetRewrite.Collect, so: Origin)(using p: Program): Option[Term] =
    val viaSym = retargetRewriteSyms.getOrElse((srcFqn, rw.via), SymId.None)
    if viaSym == SymId.None then return scala.None
    val elemTpe = if rw.via.contains("Key") then keyType(recv.tpe).getOrElse(TypeRepr.NoType)
                  else valueType(recv.tpe).getOrElse(TypeRepr.NoType)
    if elemTpe == TypeRepr.NoType then return scala.None
    val n = { collectSeq += 1; collectSeq }
    val varName = s"r$$co$n"
    val addName = "add"
    val block = Tree.Opaque.spliced(
      List(s"{ val $varName = ${rw.into}[", s"](); ", s".${rw.via}($varName.$addName); $varName }"),
      List(Tree.Ident(headSym(elemTpe).getOrElse(SymId.None), elemTpe, so), recv),
      TypeRepr.NoType,
      so
    )
    // Track map Collect receivers so `.iterator()` chained on the block can emit a REMOVING
    // iterator that removes from the original MAP rather than from the DynamicArray snapshot.
    if rw.via == "foreachValue" || rw.via == "foreachKey" then
      collectBlockReceivers.put(block, (recv, srcFqn, rw.via))
    Some(block)

  /** K36: emit a removing iterator for a direct `recv.iterator` on a retarget target, keyed on
    * the target FQN. `None` for targets the shim does not support (caller falls back to
    * read-only `JavaIterator.from`). */
  private def emitRemovingIterator(recv: Term, targetFqn: String, tpe: TypeRepr, so: Origin)(using p: Program): Option[Term] =
    targetFqn match
      case "scala.collection.mutable.ArrayDeque" =>
        Some(Tree.Opaque.spliced(
          List("balticporter.runtime.JavaIterator.removingFromBuffer(", ")"),
          List(recv), tpe, so))
      case "lowlevel.util.DynamicArray" =>
        // $recv appears 3 times, so bind to a val to avoid multiple evaluation
        val n = { collectSeq += 1; collectSeq }
        val tmpName = s"bp$$da$n"
        val riName  = "bp$ri"
        Some(Tree.Opaque.spliced(
          List(s"{ val $tmpName = ", s"; balticporter.runtime.JavaIterator.removing(() => $tmpName.size, ($riName: scala.Int) => $tmpName.apply($riName), ($riName: scala.Int) => { $tmpName.removeIndex($riName); () }) }"),
          List(recv), tpe, so))
      case _ => scala.None

  /** K36: emit a removing iterator for a map Collect whose `.iterator()` was chained — a block
    * collecting both keys and values into parallel DynamicArrays, whose `removeAt` removes from
    * the original map (`mapRecv`) by key and prunes both snapshots. */
  private def emitRemovingIteratorForCollect(mapRecv: Term, srcFqn: String, via: String,
      tpe: TypeRepr, so: Origin)(using p: Program): Term =
    val isValues = via == "foreachValue"
    val keyTpe   = keyType(mapRecv.tpe).getOrElse(TypeRepr.NoType)
    val valTpe   = valueType(mapRecv.tpe).getOrElse(TypeRepr.NoType)
    val elemTpe  = if isValues then valTpe else keyTpe
    val n = { collectSeq += 1; collectSeq }
    val ksName   = s"bp$$ks$n"
    val vsName   = s"bp$$vs$n"
    val mapName  = s"bp$$map$n"
    val riName   = "bp$ri"
    val into     = "lowlevel.util.DynamicArray"
    // Build the block as an Opaque.spliced with the map receiver, key type and value type as holes.
    // The block collects keys and values in parallel, then creates a removing JavaIterator whose
    // removeAt callback removes from the map by key AND from both snapshot arrays.
    val iterExpr = if isValues then
      s"{ val $mapName = "; val part2 = s"""; val $ksName = $into["""; val part3 = s"""](); val $vsName = $into["""; val part4 =
        s"""](); $mapName.foreachEntry((bp$$k: """ ; val part5 = s""", bp$$v: """; val part6 =
        s""") => { $ksName.add(bp$$k); $vsName.add(bp$$v) }); balticporter.runtime.JavaIterator.removing(() => $vsName.size, ($riName: scala.Int) => $vsName.apply($riName), ($riName: scala.Int) => { $mapName.remove($ksName.apply($riName)); $ksName.removeIndex($riName); $vsName.removeIndex($riName); () }) }"""
      val keySym = headSym(keyTpe).getOrElse(SymId.None)
      val valSym = headSym(valTpe).getOrElse(SymId.None)
      Tree.Opaque.spliced(
        List(s"{ val $mapName = ", s"; val $ksName = $into[", s"](); val $vsName = $into[",
             s"](); $mapName.foreachEntry((bp$$k: ", s", bp$$v: ",
             s") => { $ksName.add(bp$$k); $vsName.add(bp$$v) }); balticporter.runtime.JavaIterator.removing(() => $vsName.size, ($riName: scala.Int) => $vsName.apply($riName), ($riName: scala.Int) => { $mapName.remove($ksName.apply($riName)); $ksName.removeIndex($riName); $vsName.removeIndex($riName); () }) }"),
        List(mapRecv,
             Tree.Ident(keySym, keyTpe, so),
             Tree.Ident(valSym, valTpe, so),
             Tree.Ident(keySym, keyTpe, so),
             Tree.Ident(valSym, valTpe, so)),
        tpe, so)
    else // foreachKey — iterator over keys, remove by key
      val keySym = headSym(keyTpe).getOrElse(SymId.None)
      Tree.Opaque.spliced(
        List(s"{ val $mapName = ", s"; val $ksName = $into[",
             s"](); $mapName.foreachKey($ksName.add); balticporter.runtime.JavaIterator.removing(() => $ksName.size, ($riName: scala.Int) => $ksName.apply($riName), ($riName: scala.Int) => { $mapName.remove($ksName.apply($riName)); $ksName.removeIndex($riName); () }) }"),
        List(mapRecv, Tree.Ident(keySym, keyTpe, so)),
        tpe, so)
    iterExpr

  /** does the for-each body contain a `return`? Stops at lambdas, nested defs, anonymous classes. */
  private def returnsInForEach(t: Any): Boolean = t match
    case _: Tree.Return                                     => true
    case _: Tree.Lambda | _: Tree.DefDef | _: Tree.AnonClass => false
    case xs: Iterable[?]                                    => xs.exists(returnsInForEach)
    case Some(x)                                            => returnsInForEach(x)
    case p: Product                                         => p.productIterator.exists(returnsInForEach)
    case _                                                  => false

  /** Replace `Return(Some(v))` with `Opaque("boundary.break(v)(using label)")` in the for-each
    * body; `[[wrapReturnBoundary]]` in `[[transformDefDef]]` produces the wrapper. Stops at
    * lambdas, nested defs and anonymous classes, matching `[[returnsInForEach]]`. */
  private def rewriteReturnsToBreaks(body: Term, label: String, so: Origin)(using Program): Term =
    val rw = new Phase:
      def name = "return-to-break"
      override def transformTerm(t: Term)(using Program): Term = t match
        case r: Tree.Return =>
          val v = r.expr.getOrElse(Tree.Opaque("()", unitTpe, so))
          Tree.Opaque.spliced(
            List(s"scala.util.boundary.break(", s")(using $label)"),
            List(v),
            unitTpe,
            so
          )
        case _ => t
      override def transformDefDef(t: Tree.DefDef)(using Program): Tree.DefDef = t
    // StandardTraversal.mapTerm descends into lambdas by default; walk manually instead
    rewriteReturnsToBreaksWalk(rw, body)

  /** walk the body replacing returns, but stop at lambdas, nested defs, anon classes. */
  private def rewriteReturnsToBreaksWalk(rw: Phase, t: Term)(using Program): Term = t match
    case _: Tree.Lambda => t // lambdas open their own return scope
    case r: Tree.Return => rw.transformTerm(r)
    case x: Tree.Block =>
      x.copy(
        stats = x.stats.map {
          case s: Term => rewriteReturnsToBreaksWalk(rw, s)
          case other   => other
        },
        expr = rewriteReturnsToBreaksWalk(rw, x.expr)
      )
    case x: Tree.If =>
      x.copy(thenp = rewriteReturnsToBreaksWalk(rw, x.thenp),
             elsep = rewriteReturnsToBreaksWalk(rw, x.elsep))
    case x: Tree.While    => x.copy(body = rewriteReturnsToBreaksWalk(rw, x.body))
    case x: Tree.DoWhile  => x.copy(body = rewriteReturnsToBreaksWalk(rw, x.body))
    case x: Tree.For      => x.copy(body = rewriteReturnsToBreaksWalk(rw, x.body))
    case x: Tree.ForEach  => x.copy(body = rewriteReturnsToBreaksWalk(rw, x.body))
    case x: Tree.Synchronized => x.copy(body = rewriteReturnsToBreaksWalk(rw, x.body))
    case x: Tree.Labeled  => x.copy(stmt = rewriteReturnsToBreaksWalk(rw, x.stmt))
    case x: Tree.Commented => x.copy(stmt = rewriteReturnsToBreaksWalk(rw, x.stmt))
    case x: Tree.Try =>
      x.copy(body = rewriteReturnsToBreaksWalk(rw, x.body),
             catches = x.catches.map(c => c.copy(body = rewriteReturnsToBreaksWalk(rw, c.body))),
             finalizer = x.finalizer.map(rewriteReturnsToBreaksWalk(rw, _)))
    case x: Tree.Match =>
      x.copy(cases = x.cases.map(c => c.copy(body = rewriteReturnsToBreaksWalk(rw, c.body))))
    case other => other

  /** Does the body reference `bound` other than via `.key`/`.value`? A bare use (e.g.
    * `list.add(entry)`) has no lls image. Walks TOP-DOWN (Product reflection), since
    * `StandardTraversal.mapTerm`'s bottom-up order would flag every `.key`/`.value` access
    * as a bare ident first. */
  private def hasNonFieldUsage(bound: SymId, body: Term)(using p: Program): Boolean =
    def walk(t: Any): Boolean = t match
      // a .key/.value select on the bound entry — this is the ALLOWED usage, skip the inner Ident
      case Tree.Select(Tree.Ident(`bound`, _, _), m, _, _) =>
        val mn = methodName(m)
        mn != "key" && mn != "value" && mn != "getKey" && mn != "getValue" && mn != "_1" && mn != "_2"
      // a bare ident reference to bound — NOT allowed, the entry has no lls image
      case Tree.Ident(`bound`, _, _) => true
      // stop at constructs that rebind (lambdas, nested defs, anonymous classes)
      case _: Tree.Lambda | _: Tree.DefDef | _: Tree.AnonClass => false
      case xs: Iterable[?]     => xs.exists(walk)
      case Some(x)             => walk(x)
      case p: Product          => p.productIterator.exists(walk)
      case _                   => false
    walk(body)

  /** rewrite `.key`/`.value` selects on `bound` to `kSym`/`vSym` idents. */
  private def rewriteEntrySelects(bound: SymId, kSym: SymId, kTpe: TypeRepr,
      vSym: SymId, vTpe: TypeRepr, body: Term, so: Origin)(using Program): Term =
    val rw = new Phase:
      def name = "entry-select-rewrite"
      override def transformTerm(x: Term)(using Program): Term = x match
        case Tree.Select(Tree.Ident(`bound`, _, _), m, _, _) =>
          val mn = methodName(m)
          if mn == "key" || mn == "getKey" || mn == "_1" then Tree.Ident(kSym, kTpe, so)
          else if mn == "value" || mn == "getValue" || mn == "_2" then Tree.Ident(vSym, vTpe, so)
          else x
        case _ => x
    StandardTraversal.mapTerm(rw, body)

  /** rewrite all references to `bound` as references to `paramSym`. */
  private def rewriteBindingRefs(bound: SymId, paramSym: SymId, paramTpe: TypeRepr,
      body: Term, so: Origin)(using Program): Term =
    val rw = new Phase:
      def name = "binding-ref-rewrite"
      override def transformTerm(x: Term)(using Program): Term = x match
        case Tree.Ident(`bound`, _, _) => Tree.Ident(paramSym, paramTpe, so)
        case _ => x
    StandardTraversal.mapTerm(rw, body)

  private def writeThroughEntries(fe: Tree.ForEach)(using p: Program): Tree.ForEach =
    entrySource(fe.iterable).filter(purePath) match
    case scala.None      => fe
    case Some(src) =>
      val bound = fe.binding.symbol
      if bound == SymId.None || reassigned(bound, fe.body) then fe
      else
        val rw = new Phase:
          def name = "entry-set-write-through"
          override def transformApply(t: Tree.Apply)(using Program): Term = t.fun match
            case Tree.Select(Tree.Ident(`bound`, bt, bo), m, _, so)
              if methodName(m) == "setValue" && t.args.sizeIs == 1 =>
              val key = Tree.Select(Tree.Ident(bound, bt, bo), key1Sym, keyType(src.tpe).getOrElse(TypeRepr.NoType), bo)
              call(call(src, putSym, List(key, t.args.head), t, so), getOrElseSym,
                   List(dflt(nullOf(so), src, so)), t, so)
            case _ => t
        fe.copy(body = StandardTraversal.mapTerm(rw, fe.body))

  /** The map a for-loop's entry source is a view OF — this phase's own `entrySet()` rewrite,
    * whichever shape it took: an application of the `entrySetView` symbol this run minted, or
    * (where that helper is absent) a source retyped to `Kind.Map`. §4.56: asked of the phase's
    * own record, never a name. */
  private def entrySource(src: Term)(using Program): Option[Term] = src match
    case Tree.Apply(_, List(m), f, _, _) if f != SymId.None && f == sym("entrySetView") => Some(m)
    case _ if kindAt(src).contains(Kind.Map)                                            => Some(src)
    case _                                                                              => scala.None

  /** an expression java may evaluate a SECOND time without changing what the program does — an
    * identifier, `this`, or a selection chain over one. Deliberately narrow: the question is asked
    * of a loop source about to be repeated inside the body, and over-approximating it duplicates an
    * effect that no compile error and no check count would report. */
  private def purePath(t: Term): Boolean = t match
    case _: Tree.Ident | _: Tree.This       => true
    case Tree.Select(q, _, _, _)            => purePath(q)
    case _                                  => false

  /** is `s` the target of an assignment anywhere under `body`? `StandardTraversal`'s walk, per
    * CLAUDE.md §3 — a hand-rolled recursion that stopped one node short would answer "no" for the
    * shape this test exists to catch. */
  private def reassigned(s: SymId, body: Term)(using Program): Boolean =
    var hit = false
    val scan = new Phase:
      def name = "binding-reassignment"
      override def transformTerm(x: Term)(using Program): Term =
        x match
          case Tree.Assign(Tree.Ident(`s`, _, _), _, _, _, _) => hit = true; x
          case _                                           => x
    StandardTraversal.mapTerm(scan, body)
    hit

  /** A cast to a runtime shim whose source this phase retyped OUT of the shim family — no value
    * can satisfy it, since the phase guaranteed the runtime value is a scala collection. Decided
    * from `remap`/`kindOf` (the phase's own record), never from the source type's name — a prefix
    * test swept up `java.lang.Object` and broke an ordinary downcast the phase never touched
    * (§4.56). Dropping the cast also lets `coerce` see and bridge the argument properly. */
  private def impossibleShimCast(t: Tree.Typed): Boolean =
    def scalaSym(s: SymId) = remap.getOrElse(s, s)
    val to   = headSym(t.tpt.tpe).map(scalaSym)
    val from = headSym(t.expr.tpe).map(scalaSym)
    to.exists(shimSyms.contains) && from.exists(f => !shimSyms.contains(f) && kindOf.contains(f))

  /** replace the head (type-constructor) symbol of a `TypeRef` / `AppliedType`, keeping args. */
  private def withHead(t: TypeRepr, s: SymId): TypeRepr = t match
    case TypeRepr.TypeRef(prefix, _)    => TypeRepr.TypeRef(prefix, s)
    case TypeRepr.AppliedType(tc, args) => TypeRepr.AppliedType(withHead(tc, s), args)
    case other                          => other

  override def transformApply(t: Tree.Apply)(using Program): Term =
    val t2 = wrapIterableArgs(t)
    val out = tokenConstructor(t2).orElse(copyConstructor(t2)).orElse(capacityConstructor(t2))
      .orElse(retargetConstruct(t2))
      .orElse(staticRewrite(t2)).getOrElse {
      // when retargetSelectRewrite's Chain/Template handler rewrote a Select that is the fun of
      // a 0-arg Apply, the outer Apply still wraps it in () for a parenless target — strip it
      // (checked by identity in selectChainRewritten) and carry the CALL's type, not the fun's.
      if t2.args.isEmpty && selectChainRewritten.remove(t2.fun) then
        t2.tpe match
          case TypeRepr.NoType | _: TypeRepr.MethodType => t2.fun
          case vt => t2.fun match
            case b: Tree.Block  => b.copy(tpe = vt)
            case a: Tree.Apply  => a.copy(tpe = vt)
            case s: Tree.Select => s.copy(tpe = vt)
            case other => other
      else t2.fun match
        case Tree.Select(recv, m, _, so) => kindAt(recv).orElse(inheritedKind(recv, m)) match
          case Some(k) => rewrite(k, recv, m, so, t2).getOrElse(t2)
          // neither answered: java resolved a class member, possibly beside a phase-given scala
          // parent — try a retarget rewrite before pinnedByObject
          case None    => retargetRewrite(recv, m, so, t2).orElse(pinnedByObject(recv, m, t2)).getOrElse(t2)
        // TypeApply(Select(recv, m), targs): the bottom-up traversal visits the inner Select
        // before the outer TypeApply, so a generic call arrives wrapped this way
        case Tree.TypeApply(Tree.Select(recv, m, _, so), _, _, _) =>
          kindAt(recv).orElse(inheritedKind(recv, m)) match
            case Some(k) => rewrite(k, recv, m, so, t2).getOrElse(t2)
            case None    => retargetRewrite(recv, m, so, t2).orElse(pinnedByObject(recv, m, t2)).getOrElse(t2)
        // retargetSelectRewrite may have replaced the fun Select with an Apply/Opaque wrap
        // (JavaIterator.from, K36 removing iterator); collapse the outer Nil-arg Apply
        case inner: Tree.Apply if t2.args.isEmpty => inner
        case inner: Tree.Opaque if t2.args.isEmpty => inner
        case _ => t2
    }
    // seam arms see only what nothing else rewrote — ordering them before the rewrites would
    // report an already-retargeted call as an unverifiable external argument
    val res =
      if out ne t2 then out
      else
        // bridge the consumer half where a live view exists, before the count runs
        val bridged = bridgeSinkArgs(bridgeJavaFormals(t2))
        externalArgs(bridged)
        externalProducer(bridged)
    res match
      case a: Tree.Apply => noteImplicitReceiver(a); a
      case other         => other

  // ---- Inherited collection call with no receiver (anonymous class double-brace idiom) ----
  // // ENGINE-LIMITS K5

  /** Call sites recorded by [[transformApply]], awaiting an enclosing class that can supply
    * `this`. Keyed by ORIGIN, not node identity — `StandardTraversal.mapTerm` rebuilds every
    * node, so no identity survives. Cleared per translation in [[run]]. */
  private val implicitPending = collection.mutable.Set[Origin]()
  /** Selects rewritten by the Chain/Template handler in `retargetSelectRewrite`, tracked by
    * identity of the RESULT term, so the Apply handler can strip an outer `()` a chain-rewritten
    * parenless target should not have. */
  private val selectChainRewritten = java.util.Collections.newSetFromMap(
    new java.util.IdentityHashMap[Term, java.lang.Boolean]())

  /** [[inheritedKind]] with no receiver to read. The scope suppression `inheritedKind` applies is
    * about the RECEIVER's declaration, and there is no receiver here; the enclosing class's own
    * [[kindAt]] — which does go through `actualOf` — is what stands in for it at the claim. */
  private def implicitInheritedKind(m: SymId)(using p: Program): Option[Kind] =
    if mintedSyms.contains(m) then scala.None
    else p.symbolOf(m).flatMap(s => p.symbolOf(s.owner)).flatMap(o => typeMap.get(o.fullName)).map(_._2)

  private def noteImplicitReceiver(t: Tree.Apply)(using Program): Unit = t.fun match
    case Tree.Ident(m, _, _) if t.origin != Origin.synthetic && implicitInheritedKind(m).isDefined =>
      implicitPending += t.origin
    case _ => ()

  override def transformNew(t: Tree.New)(using Program): Term = t.anon match
    case Some(a) if implicitPending.nonEmpty =>
      // the anonymous class's own kind decides whether it can SUPPLY the receiver; the claimer
      // drains what it finds either way, so the drop happens at the innermost anonymous class.
      val supplies = kindAt(t)
      val claimer = new Phase:
        def name: String = "collections/implicit-receiver"
        override def transformApply(x: Tree.Apply)(using Program): Term =
          if !implicitPending.remove(x.origin) then x
          else if supplies.isEmpty then x
          else x.fun match
            case Tree.Ident(m, _, so) =>
              implicitInheritedKind(m)
                .flatMap(k => rewrite(k, Tree.This(a.symbol, t.tpe, x.origin), m, so, x))
                .getOrElse(x)
            case _ => x
      t.copy(anon = Some(a.copy(body = a.body.map(StandardTraversal.mapStat(claimer, _)))))
    case _ => t

  // ---- External callee seam — boundary at compiled class files, both directions ----
  // // ENGINE-LIMITS K15, M6

  /** Wrap an external call whose result is a collection this phase retypes.
    * Guards: unowned callee, owner not in `typeMap`, node type is a `liveWrappable` target,
    * type args mention nothing retyped. // ENGINE-LIMITS K6, K15 */
  private def externalProducer(t: Tree.Apply)(using p: Program): Term =
    if fromJavaSym == SymId.None || !externalCallee(t.method) || instantiation(t) then t
    else headSym(t.tpe).filter(s => kindOf.contains(s) || shimSyms.contains(s)) match
      case scala.None => t
      // pass-through checked after collection-head filter
      case Some(_) if passesThrough(t) =>
        // unreadable signature = unverified pass-through (different residue from cannot-verify)
        if !signatureReadable(t) then
          seam("external result (unverified pass-through, no signature)",
               "a live scala view, IF the value was ever java's",
               TirPrinter.tpe(t.tpe, TirPrinter.Style.canonical), t.origin, t.method)
        t
      case Some(s) if !liveWrappableSyms.contains(s) =>
        seam("external result", "a live scala view", TirPrinter.tpe(t.tpe, TirPrinter.Style.canonical),
             t.origin, t.method)
        t
      case Some(_) if mentionsRetyped(t.tpe) =>
        seam("external result (nested element)", "a one-level wrap",
             TirPrinter.tpe(t.tpe, TirPrinter.Style.canonical), t.origin, t.method)
        t
      case Some(_) =>
        Tree.Apply(Tree.Ident(fromJavaSym, TypeRepr.NoType, t.origin), List(t), fromJavaSym, t.tpe, t.origin)

  /** True if this application is a `new` (constructor), not an external call.
    * Constructors hand back the object this program built, not a java value to wrap. */
  private def instantiation(t: Tree.Apply)(using p: Program): Boolean =
    t.fun.isInstanceOf[Tree.New] || p.symbolOf(t.method).exists(_.name == "<init>")

  /** True when the result type occurs on the input side (a generic pass-through).
    * Class file checked first; structural guess used only when no signature is readable. */
  private def passesThrough(t: Tree.Apply)(using p: Program): Boolean =
    !declaredResultIsMapped(t) && {
      val want = t.tpe
      // OCCURRENCE on both sides (argument and receiver), never equality on one alone — an
      // argument's TYPE ARGUMENT can pin the result too (TypeReference<Map<String,Object>>).
      want != TypeRepr.NoType && (
        t.args.exists(a => occursIn(want, a.tpe)) || (t.fun match
          case Tree.Select(recv, _, _, _) => occursIn(want, recv.tpe)
          case _                          => false))
    }

  /** Does the class file declare this callee's result to be a mapped collection? Read literally,
    * never through `remap` (§4.56) — `None` (no signature) answers `false`, leaving the
    * structural guess in charge. */
  private def declaredResultIsMapped(t: Tree.Apply)(using p: Program): Boolean =
    declaredResult(t).flatMap(headSym).flatMap(p.symbolOf).exists(s => typeMap.contains(s.fullName))

  /** the callee's DECLARED result type, where the class file could be read for one. */
  private def declaredResult(t: Tree.Apply)(using p: Program): Option[TypeRepr] =
    p.symbolOf(t.method).map(_.info).collect {
      case TypeRepr.MethodType(_, ret, _)                       => ret
      case TypeRepr.PolyType(_, TypeRepr.MethodType(_, ret, _)) => ret
    }

  /** True when the call reads a wildcard capture java answered with `Object` (JLS 4.4).
    * Structural: none of the standalone shims' members return bare `Object`.
    * // ENGINE-LIMITS G23, G33 */
  private def capturedObjectRead(t: Tree.Apply)(using p: Program): Boolean =
    def isObject(x: TypeRepr): Boolean =
      headSym(x).flatMap(p.symbolOf).exists(_.fullName == CollectionsTransform.ObjectFqn)
    isObject(t.tpe) && !declaredResult(t).exists(isObject)

  /** could the callee's class file be read for a signature at all? The two answers a suppression has
    * to be told apart by: a refusal the CLASS FILE licensed, and one resting on a GUESS. */
  private def signatureReadable(t: Tree.Apply)(using p: Program): Boolean =
    p.symbolOf(t.method).exists(_.info != TypeRepr.NoType)

  /** Does `needle` occur anywhere inside `hay`, as a whole type? Structural equality via
    * [[StandardTraversal.mapType]] (§3). */
  private def occursIn(needle: TypeRepr, hay: TypeRepr)(using Program): Boolean =
    if hay == needle then true
    else
      var hit = false
      val scan = new Phase:
        def name = "passthrough-scan"
        override def transformType(x: TypeRepr)(using Program): TypeRepr =
          if x == needle then hit = true
          x
      StandardTraversal.mapType(scan, hay)
      hit

  /** Is this a method the program does not declare, and not the collection API's own? Excludes
    * a minted symbol, a callee owned by a mapped type or its target, an owner-less symbol, and
    * one `[[handledStatic]]` covers (a REFUSED arm, kept under the JDK name — M6). */
  private def externalCallee(m: SymId)(using p: Program): Boolean =
    m != SymId.None && !ownedSym(m) && !mintedSyms.contains(m) &&
      p.symbolOf(m).exists(_.owner != SymId.None) &&
      !p.symbolOf(m).flatMap(c => p.symbolOf(c.owner))
        .exists(o => typeMap.contains(o.fullName) || retypedTargets.contains(o.fullName)) &&
      !handledStatic(m)

  /** Does this callee name a member one of the phase's own static arms covers (§4.56, `MemberKey`
    * form)? A call still standing at such a name is one the phase DECLINED to rewrite — its value
    * is whatever java's was, whatever the node's retyped `tpe` now says. */
  private def handledStatic(m: SymId)(using p: Program): Boolean =
    memberKeyOf(m).exists(CollectionsTransform.handledStatics.contains)

  /** The source half: a value PRODUCED by a call this phase refused to rewrite (`Arrays.asList`,
    * K6.5) — emitted text keeps the JDK name, node's `tpe` says `Buffer`. Read from the node
    * alone, [[coerce]] would name the wrapper instead of the boundary (K2.5). */
  private def refusedRewriteSource(t: Term)(using Program): Boolean = t match
    case a: Tree.Apply => handledStatic(a.method)
    case _             => false

  /** Does this type mention, inside its ARGUMENTS, a type this phase produced? The node is
    * already mapped, so a nested `java.util.List<java.util.List<String>>` reads
    * `Buffer[Buffer[String]]` — a one-level `asScala` would leave a stale inner `List`. Walked
    * with [[StandardTraversal.mapType]] (§3); head excluded, already established by the caller. */
  private def mentionsRetyped(t: TypeRepr)(using p: Program): Boolean = t match
    case TypeRepr.AppliedType(_, args) => args.exists { a =>
      var hit = false
      val scan = new Phase:
        def name = "external-nesting-scan"
        override def transformType(x: TypeRepr)(using pp: Program): TypeRepr =
          x match
            case TypeRepr.TypeRef(_, s) => if kindOf.contains(s) || shimSyms.contains(s) then hit = true
            case _                      => ()
          x
      StandardTraversal.mapType(scan, a)
      hit
    }
    case _ => false

  /** Count external-callee argument seams where no signature is readable (cannot-verify).
    * Where a signature IS readable, [[CollectionBoundaryCheck]] classifies it instead. */
  private def externalArgs(t: Tree.Apply)(using p: Program): Unit =
    if externalCallee(t.method) && p.symbolOf(t.method).forall(_.info == TypeRepr.NoType) then
      t.args.foreach { a =>
        headSym(a.tpe).filter(s => kindOf.contains(s) || shimSyms.contains(s)).foreach { _ =>
          seam("argument (external callee, no signature)", "unknown — the callee is a class file",
               TirPrinter.tpe(a.tpe, TirPrinter.Style.canonical), a.origin, t.method)
        }
      }
    opaqueEgress(t)

  /** The seam with nothing type-wrong: a `java.lang.Object` formal on an external callee takes a
    * retyped value, and the port compiles, but the callee's `toString`/`instanceof`/serialiser see
    * something different. Only the port knows which such callees READ the representation
    * ([[reflectiveSinks]]); this is the review list that makes a missing entry visible
    * (K21 face 1). Deduplicated by CALLEE, not by site — a declared sink is bridged and skipped. */
  private def opaqueEgress(t: Tree.Apply)(using p: Program): Unit =
    if !externalCallee(t.method) || sinkOf(t.method).isDefined then return
    val formals = formalsOf(t)
    def objectTyped(x: TypeRepr) =
      headSym(x).flatMap(p.symbolOf).exists(_.fullName == CollectionsTransform.ObjectFqn)
    val opaque =
      if formals.sizeIs == t.args.size then
        // the argument is one the phase cannot rule out: a retyped value, or one whose static
        // type is Object and says nothing
        t.args.zip(formals).exists((a, f) => objectTyped(f) && mayBeRetypedValue(a))
      else
        // no readable signature, so no formal to ask — held to an Object-typed argument
        t.args.exists(a => objectTyped(a.tpe))
    if opaque then
      // earliest site PER JAVA FILE, never per callee — a base's site would otherwise win the
      // minimum for the whole program and a dependent's row would silently disappear
      val key  = t.method -> t.origin.javaPath
      val prev = opaqueEgressSites.get(key)
      if prev.forall(o => t.origin.line < o.line) then opaqueEgressSites(key) = t.origin

  /** record one external seam this phase could not close, for [[boundary]] to report. A refusal
    * that is not counted is indistinguishable from a seam that does not exist (M6). */
  private def seam(slot: String, expected: String, actual: String, origin: Origin, enclosing: SymId,
                   issue: CollectionBoundaryCheck.Issue = CollectionBoundaryCheck.Issue.ExternalCallee): Unit =
    externalSeams += CollectionBoundaryCheck.Finding(issue, slot, expected, actual, origin, enclosing)

  /** Java's collection copy constructor — `new ArrayList<>(c)`, `new HashSet<>(c)`, etc. A
    * capacity hint (`new ArrayList<>(10)`) maps correctly by accident since `ArrayBuffer(10)`
    * means the same; a COPY (`new ArrayList<>(c)`) needs `<Companion>.from(c)` instead, gated on
    * the argument being a collection. */
  /** Java's class-token constructor — `new EnumMap<K, V>(K.class)` — routed to a named factory
    * since the shim orders by `ordinal` and the token has nothing to size. Ordered before
    * [[copyConstructor]]; disjoint anyway (takes a `classOf[…]` literal). */
  private def tokenConstructor(t: Tree.Apply)(using Program): Option[Term] = t.fun match
    case n: Tree.New if enumMapOfTypeSym != SymId.None =>
      val isToken = t.args match
        case List(Tree.Literal(Constant.ClassOfC(_), _, _)) => true
        case _                                              => false
      for tgt <- headSym(n.tpe) if isToken && tgt == byScalaSym(CollectionsTransform.JavaEnumMapFqn)
      yield Tree.Apply(Tree.Ident(enumMapOfTypeSym, TypeRepr.NoType, t.origin), t.args,
                       enumMapOfTypeSym, n.tpe, t.origin)
    case _ => scala.None

  private def copyConstructor(t: Tree.Apply)(using Program): Option[Term] = t.fun match
    case n: Tree.New =>
      val target = headSym(n.tpe).filter(kindOf.contains)
      val single = t.args match
        case List(a) if headSym(a.tpe).exists(kindOf.contains) => Some(a)
        case _                                                 => scala.None
      for
        tgt <- target
        arg <- single
        f   <- fromSyms.get(tgt)
      // typed as the TARGET, not the argument: new HashMap<>(aTreeMap) is a HashMap
      yield Tree.Apply(Tree.Ident(f, TypeRepr.NoType, t.origin), List(scalaView(arg)), f, n.tpe, t.origin)
    case _ => scala.None

  /** Java's capacity-hint constructor at a HASHED collection — `new HashMap<>(16)`. Unlike the
    * sequence targets, scala's `mutable.HashMap` has no one-arg `(initialCapacity: Int)`
    * constructor, so the java one-arg form is completed with `defaultLoadFactor` (0.75, java's own
    * `DEFAULT_LOAD_FACTOR`) rather than left to fail (M6). Disjoint from [[copyConstructor]] by
    * argument type. */
  private def capacityConstructor(t: Tree.Apply)(using Program): Option[Term] = t.fun match
    case n: Tree.New =>
      val isInt = t.args match
        case List(a) => headSym(a.tpe).flatMap(summon[Program].symbolOf).exists(_.fullName == "scala.Int")
        case _       => false
      for
        tgt <- headSym(n.tpe)
        lf  <- loadFactorSyms.get(tgt) if isInt
      yield Tree.Apply(t.fun, t.args :+ Tree.Ident(lf, TypeRepr.NoType, t.origin), t.method, t.tpe, t.origin)
    case _ => scala.None

  /** `java.util.Collections`' static utilities — receiver-less, so `rewrite` never sees them and
    * the call is emitted verbatim against the real JDK class unless mapped here. Keyed on
    * `owner#name` (`PortabilityCheck.exactMember`'s identification). Deliberately small: an
    * unmapped static fails to COMPILE rather than silently approximating (a read-only `Buffer`
    * view for `unmodifiableList` would drop the immutability with a green compile). */
  private def staticRewrite(t: Tree.Apply)(using p: Program): Option[Term] =
    def qualified(s: SymId) = for
      m <- p.symbolOf(s)
      o <- p.symbolOf(m.owner)
    yield MemberKey(o.fullName, m.name).render
    val member  = qualified(t.method)
    // an explicitly-instantiated call arrives as Apply(TypeApply(Select(xs, m))), not Apply(Select(...))
    val recv    = t.fun match
      case Tree.Select(r, _, _, _)                          => Some(r)
      case Tree.TypeApply(Tree.Select(r, _, _, _), _, _, _)  => Some(r)
      case _                                                 => None
    def factory(f: SymId, args: List[Term]) =
      Tree.Apply(Tree.Ident(f, TypeRepr.NoType, t.origin), args, f, t.tpe, t.origin)
    (member, t.args) match
      case (Some("java.util.Collections#unmodifiableCollection"), List(c)) if unmodifiableSym != SymId.None =>
        Some(factory(unmodifiableSym, List(c)))

      // ---- java.util.Collections / Map.Entry statics — see JavaCollections ----
      case (Some("java.util.Collections#sort"), List(xs, cmp))    => Some(factory(sym("sort"), List(xs, cmp)))
      case (Some("java.util.Collections#sort"), List(xs))         => Some(factory(sym("sortNatural"), List(xs)))
      case (Some("java.util.Collections#reverse"), List(xs))      => Some(factory(sym("reverse"), List(xs)))
      case (Some("java.util.Collections#swap"), List(xs, i, j))   => Some(factory(sym("swap"), List(xs, i, j)))
      case (Some("java.util.Collections#shuffle"), List(xs, rnd))  => Some(factory(sym("shuffle"), List(xs, rnd)))
      // the IMMUTABLE PRODUCERS: a value the JDK hands BACK at a slot this phase already moved,
      // so nothing coerces it — the rewrite must produce the scala value directly. Targets
      // REPRODUCE java's immutability rather than dropping it (mutable.ArrayBuffer.empty would
      // turn an UnsupportedOperationException into a silent write).
      case (Some("java.util.Collections#emptyList"), Nil)           => Some(factory(sym("emptyList"), Nil))
      case (Some("java.util.Collections#emptyMap"), Nil)            => Some(factory(sym("emptyMap"), Nil))
      case (Some("java.util.Collections#emptySet"), Nil)            => Some(factory(sym("emptySet"), Nil))
      case (Some("java.util.Collections#singletonList"), List(x))   => Some(factory(sym("singletonList"), List(x)))
      case (Some("java.util.Collections#singleton"), List(x))       => Some(factory(sym("singleton"), List(x)))
      case (Some("java.util.Collections#singletonMap"), List(k, v)) => Some(factory(sym("singletonMap"), List(k, v)))
      // unmodifiable VIEWS: scala has no read-only Buffer/Set/Map view (K6), so the runtime's
      // Frozen* delegate every READ to the wrapped collection.
      // java.util.EnumSet has no public constructor; class tokens are KEPT (not dropped) since
      // allOf/range/complementOf need the enum's constants via Class.getEnumConstants
      case (Some("java.util.EnumSet#noneOf"), List(c))       => Some(factory(enumSetSym("noneOf"), List(c)))
      case (Some("java.util.EnumSet#allOf"), List(c))        => Some(factory(enumSetSym("allOf"), List(c)))
      case (Some("java.util.EnumSet#copyOf"), List(c))       => Some(factory(enumSetSym("copyOf"), List(c)))
      case (Some("java.util.EnumSet#range"), List(a, b))     => Some(factory(enumSetSym("range"), List(a, b)))
      case (Some("java.util.EnumSet#complementOf"), List(s)) => Some(factory(enumSetSym("complementOf"), List(s)))
      case (Some("java.util.EnumSet#of"), args)              => Some(factory(enumSetSym("of"), args))
      // primitive optionals: target is an alias for Option[…], so of(x) IS Some(x), empty() IS None.
      // ofNullable has no arm — OptionalInt cannot be null and reference Optional is not mapped.
      case (Some("java.util.OptionalInt#of" | "java.util.OptionalLong#of" | "java.util.OptionalDouble#of"), List(x)) =>
        Some(factory(someSym, List(x)))
      case (Some("java.util.OptionalInt#empty" | "java.util.OptionalLong#empty" | "java.util.OptionalDouble#empty"), Nil) =>
        Some(Tree.Ident(noneSym, t.tpe, t.origin))
      case (Some("java.util.Collections#unmodifiableList"), List(c)) => Some(factory(sym("unmodifiableList"), List(c)))
      case (Some("java.util.Collections#unmodifiableSet"), List(c))  => Some(factory(sym("unmodifiableSet"), List(c)))
      case (Some("java.util.Collections#unmodifiableMap"), List(c))  => Some(factory(sym("unmodifiableMap"), List(c)))
      // Arrays.asList shares the table (same kind of receiver-less JDK factory); its runtime
      // counterpart is the ONE rewritten static using a scala vararg — see [[asListArgs]]
      case (Some("java.util.Arrays#asList"), args)                 =>
        asListArgs(args) match
          // explicit type argument for mixed-type lists (scalac needs it for boxing)
          case AsList.Elements(as) =>
            Some(elementArg(t).fold(factory(sym("asList"), as))(a =>
              Tree.Apply(Tree.TypeApply(Tree.Ident(sym("asList"), TypeRepr.NoType, t.origin), List(a),
                                        TypeRepr.NoType, t.origin),
                         as, sym("asList"), t.tpe, t.origin)))
          case AsList.Aliased(arr) => Some(factory(sym("asListView"), List(asListViewArg(arr, t))))
      // `Map.Entry` became a `Tuple2`, so `Entry`'s own statics must come along or the call survives
      // to the compiler naming a type the port no longer produces.
      case (Some("java.util.Map$Entry#comparingByKey" | "java.util.Map.Entry#comparingByKey"), List(cmp)) =>
        Some(factory(sym("comparingByKey"), List(cmp)))
      case (Some("java.util.Map$Entry#comparingByValue" | "java.util.Map.Entry#comparingByValue"), List(cmp)) =>
        Some(factory(sym("comparingByValue"), List(cmp)))

      // java.util.stream: chain collapses to scala collection operations // ENGINE-LIMITS K6
      case (Some("java.util.Collection#stream" | "java.util.List#stream" | "java.util.Set#stream"), Nil) =>
        recv.map(streamSource(_, t.method))
      // `IntStream.range(a, b)` is a stream SOURCE with no collection behind it — the one shape the
      // "only collapse a collapsed receiver" rule would otherwise leave untranslated forever, since
      // nothing can ever collapse it. It becomes the range itself, and the chain proceeds normally.
      case (Some("java.util.stream.IntStream#range"), List(a, b)) =>
        Some(Tree.Apply(Tree.Ident(sym("intRange"), TypeRepr.NoType, t.origin), List(a, b),
                        sym("intRange"), asBuffer(t.tpe), t.origin))
      // The TYPE APPLICATION is carried across, and it is load-bearing: java's
      // `mapToObj(i -> i)` against `Stream<Integer>` BOXES, and `Buffer[Int].map(i => i)` does not —
      // it yields `Buffer[Int]`, which then fails to be a `Collection<Integer>` one call further out.
      // Re-applying the explicit `[Integer]` gives the lambda body the expected type java gave it,
      // and scala inserts the same boxing.
      case (Some("java.util.stream.IntStream#mapToObj" | "java.util.stream.Stream#map"), List(f)) if collapsed(recv) =>
        val targs = t.fun match { case Tree.TypeApply(_, ts, _, _) => ts; case _ => Nil }
        recv.map { r =>
          val sel: Term = Tree.Select(r, mapSym, TypeRepr.NoType, t.origin)
          val fun = if targs.isEmpty then sel else Tree.TypeApply(sel, targs, TypeRepr.NoType, t.origin)
          Tree.Apply(fun, List(f), mapSym, asBuffer(r.tpe), t.origin)
        }
      // a stream operation is rewritten only when its receiver is a collection this phase ALREADY
      // collapsed, never on the method name alone — a stream from a non-collection source (e.g.
      // "…".lines()) is simply not translated.
      case (Some("java.util.stream.Stream#mapToDouble"), List(f)) if collapsed(recv) =>
        recv.map(r => Tree.Apply(Tree.Ident(sym("mapToDouble"), TypeRepr.NoType, t.origin), List(r, f),
                                 sym("mapToDouble"), asBuffer(r.tpe), t.origin))
      case (Some("java.util.stream.DoubleStream#sum" | "java.util.stream.IntStream#sum" |
                 "java.util.stream.LongStream#sum"), Nil) if collapsed(recv) =>
        recv.map(r => Tree.Select(r, sumSym, t.tpe, t.origin))
      case (Some("java.util.stream.Stream#sorted"), List(cmp)) if collapsed(recv) =>
        recv.map(r => Tree.Apply(Tree.Ident(sym("sortedWith"), TypeRepr.NoType, t.origin), List(r, cmp),
                                 sym("sortedWith"), r.tpe, t.origin))
      case (Some("java.util.stream.Stream#collect"), List(collector))
          if collapsed(recv) && qualified(collectorOf(collector)).contains("java.util.stream.Collectors#toCollection") =>
        // toCollection(Factory::new) carries its target inside the collector, as a factory
        val f = collector match { case a: Tree.Apply => a.args; case _ => Nil }
        if f.sizeIs != 1 then None
        else recv.map(r => factory(sym("into"), List(r, f.head)))
      case (Some("java.util.stream.Stream#filter"), List(pred)) if filteredSym != SymId.None && collapsed(recv) =>
        recv.map(r => Tree.Apply(Tree.Ident(filteredSym, TypeRepr.NoType, t.origin), List(r, pred),
                                 filteredSym, r.tpe, t.origin))
      // `anyMatch`/`allMatch` = `exists`/`forall`; `noneMatch` is a helper.
      case (Some("java.util.stream.Stream#anyMatch"), List(pred)) if existsSym != SymId.None && collapsed(recv) =>
        recv.map(r => Tree.Apply(Tree.Select(r, existsSym, TypeRepr.NoType, t.origin), List(pred),
                                 existsSym, t.tpe, t.origin))
      case (Some("java.util.stream.Stream#allMatch"), List(pred)) if forallSym != SymId.None && collapsed(recv) =>
        recv.map(r => Tree.Apply(Tree.Select(r, forallSym, TypeRepr.NoType, t.origin), List(pred),
                                 forallSym, t.tpe, t.origin))
      case (Some("java.util.stream.Stream#noneMatch"), List(pred)) if sym("noneMatch") != SymId.None && collapsed(recv) =>
        recv.map(r => factory(sym("noneMatch"), List(r, pred)))
      // `collect(toList)` terminal — the receiver already IS the sequence.
      case (Some("java.util.stream.Stream#collect"), List(collector))
          if collapsed(recv) && qualified(collectorOf(collector)).contains("java.util.stream.Collectors#toList") =>
        recv
      // `collect(toSet)` / `collect(toMap)` — need helpers, cannot guess target type.
      case (Some("java.util.stream.Stream#collect"), List(collector))
          if collapsed(recv) && qualified(collectorOf(collector)).contains("java.util.stream.Collectors#toSet") =>
        recv.map(r => factory(sym("toSet"), List(r)))
      case (Some("java.util.stream.Stream#collect"), List(collector))
          if collapsed(recv) && qualified(collectorOf(collector)).contains("java.util.stream.Collectors#toMap") =>
        // mappers are inside the collector; java has a two- or three-arg form and nothing else
        val fs = collector match { case a: Tree.Apply => a.args; case _ => Nil }
        if fs.sizeIs != 2 && fs.sizeIs != 3 then None else recv.map(r => factory(sym("toMap"), r :: fs))
      case _ => None

  /** Build args for `JavaCollections.asList`. Elements (packed or `Repeated`) are opened;
    * a single caller-held array becomes a live `asListView` (aliased writes preserved).
    * Returns `AsList.Refuse` to leave the JDK call untranslated. // ENGINE-LIMITS K6.5 */
  private def asListArgs(args: List[Term])(using p: Program): AsList =
    def isArray(t: TypeRepr) = headSym(t).flatMap(p.symbolOf).exists(_.fullName == "scala.Array")
    args match
      case init :+ Tree.NewArray(_, Nil, Some(elems), _, _) => AsList.Elements(init ++ elems)
      // the external-callee shape of the same pack — opened, never read as one array argument
      case init :+ Tree.Repeated(elems, _, _)               => AsList.Elements(init ++ elems)
      // java forwards an array through T... as a Tree.Spread at an external callee (arr*); the
      // spread comes off since asListView takes the array itself
      case List(Tree.Spread(e, _, _)) if isArray(e.tpe)     => AsList.Aliased(e)
      case List(a) if isArray(a.tpe)                        => AsList.Aliased(a)
      case _                                                => AsList.Elements(args)

  /** The element type a `TypeTree` may be written for — java's own inference, made explicit.
    * Yielded only when the result really names one type: not a wildcard (K10), not an unresolved
    * marker (G2), not `NoType`. Otherwise left to scala's own inference. */
  private def elementArg(t: Tree.Apply)(using p: Program): Option[TypeTree] =
    soleTypeArg(t.tpe).collect {
      case a if a != TypeRepr.NoType && !a.isInstanceOf[TypeRepr.TypeBounds] && !namesUnresolved(a) =>
        TypeTree(a, t.origin)
    }

  /** Does this type mention an inference marker (G2) or wildcard (K10) anywhere inside it —
    * either of which cannot be written as an explicit type argument? Read through
    * `Symbol.isUnresolvedTypeVar`, never a local spelling. */
  private def namesUnresolved(t: TypeRepr)(using p: Program): Boolean = t match
    case TypeRepr.TypeRef(_, s)      => p.symbolOf(s).exists(x => Symbol.isUnresolvedTypeVar(x.fullName))
    case TypeRepr.AppliedType(c, as) => namesUnresolved(c) || as.exists(namesUnresolved)
    case _: TypeRepr.TypeBounds      => true
    case TypeRepr.AndType(l, r)      => namesUnresolved(l) || namesUnresolved(r)
    case TypeRepr.OrType(l, r)       => namesUnresolved(l) || namesUnresolved(r)
    case _                           => false

  /** The argument `asListView` should receive at `Arrays.asList(T[])`. Java's erased formal is
    * `Object[]`, so the frontend synthesises `arr.asInstanceOf[Array[Object]]` off it (G14);
    * `asListView[A]` infers `A` from the argument, so the cast must be stripped or it infers
    * `Object`. Strip when the cast wraps an array whose element type is the call's own result type
    * (§4.56, structural, names no type) — a genuine `(Object[]) value` cast survives underneath. */
  private def asListViewArg(arg: Term, call: Tree.Apply): Term = arg match
    case Tree.Typed(inner, _, _, _) =>
      val wanted = soleTypeArg(call.tpe)
      val have   = soleTypeArg(inner.tpe)
      if wanted.isDefined && wanted == have then inner else arg
    case _ => arg

  /** The single type argument of an applied type, or `None` — the one shape [[asListViewArg]]
    * compares. Not a general "element type of": `Buffer[A]`/`Array[A]` both have exactly one. */
  private def soleTypeArg(t: TypeRepr): Option[TypeRepr] = t match
    case TypeRepr.AppliedType(_, List(a)) if a != TypeRepr.NoType => Some(a)
    case _                                                        => scala.None

  /** Which of `Arrays.asList`'s two java shapes a call site is — see [[asListArgs]]. Not an
    * `Option`: the aliasing form is a DIFFERENT helper, not "no arguments to pass". */
  private enum AsList:
    case Elements(args: List[Term])
    case Aliased(array: Term)

  /** A `JavaCollections` static by name. Minted eagerly in `run`, before the traversal consults
    * it. An unlisted name yields `SymId.None`, treated as "not available", never a dangling ref. */
  private def sym(name: String): SymId = staticSyms.getOrElse(name, SymId.None)

  /** the method a collector expression calls, so `collect`'s argument can be identified. */
  private def collectorOf(t: Term): SymId = t match
    case a: Tree.Apply => a.method
    case _             => SymId.None

  /** Convert a `stream()` receiver to the scala sequence the collapse consumes.
    * Shims use `asScalaBuffer`; `Set`/`Map` sources use `.toBuffer`. */
  private def streamSource(r: Term, m: SymId)(using p: Program): Term =
    val effective = headSym(r.tpe)
      .filter(s => kindOf.contains(s) || shimSyms.contains(s))
      .orElse(p.symbolOf(m).flatMap(x => p.symbolOf(x.owner)).flatMap(o => remap.get(o.id)))
    effective match
      case Some(s) if shimSyms.contains(s) && asScalaBufferSym != SymId.None =>
        Tree.Select(r, asScalaBufferSym, asBuffer(r.tpe), r.origin)
      case Some(s) if kindOf.get(s).contains(Kind.Seq)                       => r
      case Some(s) if kindOf.get(s).contains(Kind.Set) && toBufferSym != SymId.None =>
        Tree.Select(r, toBufferSym, asBuffer(r.tpe), r.origin)
      // a Map[K, V] copies to a Buffer[(K, V)] — arity changes, so asBuffer's head-swap would
      // be wrong; the bare constructor is the honest record and only its head is ever read
      case Some(s) if kindOf.get(s).contains(Kind.Map) && toBufferSym != SymId.None =>
        Tree.Select(r, toBufferSym, TypeRepr.TypeRef(TypeRepr.NoPrefix, bufferSym), r.origin)
      case _ => r

  /** The other direction from [[coerce]]: a shim reaching a slot that wants a scala collection —
    * `new ArrayList<>(c)` routed through `ArrayBuffer.from`, which needs an `IterableOnce`. Both
    * directions exist because the two families are deliberately unrelated. */
  private def scalaView(t: Term): Term =
    if asScalaBufferSym != SymId.None && headSym(t.tpe).exists(shimSyms.contains)
    then Tree.Select(t, asScalaBufferSym, asBuffer(t.tpe), t.origin)
    else t

  /** True when the receiver is already collapsed from a `Stream` to a scala collection. Shims
    * excluded (collapse consumes them, not produces). Keyed on `kindOf`. */
  private def collapsed(recv: Option[Term]): Boolean =
    recv.flatMap(r => headSym(r.tpe)).exists(s => kindOf.get(s).contains(Kind.Seq) && !shimSyms.contains(s))

  /** The same type with `Buffer` as its head — what `asScalaBuffer` on a `JavaCollection[E]`
    * returns. Falls back to a bare `Buffer` when the input has no head (`NoType`, common on an
    * external call's node) — the head is the only part any caller reads. */
  private def asBuffer(t: TypeRepr): TypeRepr =
    if bufferSym == SymId.None then t
    else
      val h = withHead(t, bufferSym)
      if headSym(h).contains(bufferSym) then h else TypeRepr.TypeRef(TypeRepr.NoPrefix, bufferSym)

  /** Bridge a scala collection into a shim-typed parameter, at the call site — `java.util.List`
    * becomes `Buffer`, `java.lang.Iterable` becomes [[JavaIterable]], and together they leave the
    * port unable to pass its own collections where java accepted `List` as `Iterable`. Both
    * obvious repairs are dead ends (K2): `given Conversion` never fires without an overload
    * match, and widening the parameter breaks iterate-and-remove bodies. */
  private def wrapIterableArgs(t: Tree.Apply)(using p: Program): Tree.Apply =
    // owned callee only (shim formals belong to emitted declarations, not class files)
    // not gated on `javaIterableSym` — `JavaCollection` half is independent
    if !ownedSym(t.method) || keepsJavaFormals(t) then t
    else
      val formals = instantiatedFormals(t, formalsOf(t))
      if formals.sizeIs != t.args.size then t
      else
        val as = t.args.zip(formals).map((a, f) => coerce(f, a))
        if as == t.args then t else t.copy(args = as)

  /** Substitute type variables in formals from this call's own argument types. Only
    * method-owned type parameters are bound (class parameters skipped). Parameterised formals
    * bind; bare type variables pass through unchanged. K26 */
  private def instantiatedFormals(t: Tree.Apply, formals: List[TypeRepr])(using p: Program): List[TypeRepr] =
    if formals.sizeIs != t.args.size then formals
    else
      val bound = collection.mutable.HashMap.empty[SymId, TypeRepr]
      // at a constructor the class's own parameters are bound too — fixed by the RECEIVER, and a
      // `new` has no receiver, so the instantiation is READ off the node's own type rather than
      // reconstructed (exact for a diamond the frontend already inferred)
      val ctorBound: Map[SymId, TypeRepr] =
        if !instantiation(t) then Map.empty
        else
          val owner = p.symbolOf(t.method).map(_.owner).getOrElse(SymId.None)
          (classTparams(owner), t.tpe) match
            case (ps, TypeRepr.AppliedType(tc, as))
              if ps.nonEmpty && ps.sizeIs == as.size && headSym(tc).contains(owner) =>
              ps.zip(as).toMap
            case _ => Map.empty
      def bindable(s: SymId): Boolean = p.symbolOf(s).exists(_.owner == t.method)
      // recursion through MATCHING HEADS only — an AppliedType with differing heads is a slot the
      // boundary lane already reports. First wins: a well-typed java call binds a variable once.
      def bind(f: TypeRepr, a: TypeRepr): Unit = (f, a) match
        case (TypeRepr.TypeRef(_, s), _) if bindable(s) && a != TypeRepr.NoType =>
          if !bound.contains(s) then bound(s) = a
        case (TypeRepr.AppliedType(ftc, fs), TypeRepr.AppliedType(atc, as))
          if headSym(ftc) == headSym(atc) && headSym(ftc).isDefined && fs.sizeIs == as.size =>
          fs.lazyZip(as).foreach(bind)
        case _ => ()
      formals.lazyZip(t.args).foreach {
        // a bare-reference formal is the slot being answered, never the binder
        case (_: TypeRepr.TypeRef, _) => ()
        case (f, a)                   => bind(f, a.tpe)
      }
      if bound.isEmpty && ctorBound.isEmpty then formals
      else formals.map {
        case f @ TypeRepr.TypeRef(_, s) if bindable(s)    => bound.getOrElse(s, f)
        case f @ TypeRepr.TypeRef(_, s)                   => ctorBound.getOrElse(s, f)
        case other                                        => other
      }

  /** The type parameters a class declares, in order, or `Nil` for one the program does not
    * declare (a class-file fact, §4.56, not bindable here either way). */
  private def classTparams(owner: SymId)(using p: Program): List[SymId] =
    classDefsBySym.get(owner).map(_.tparams.map(_.symbol)).getOrElse(Nil)

  /** Does this call's callee keep java formals — i.e. is its signature one this phase did not
    * and cannot move? Three cases: the callee is a declaration this run's scope held back; the
    * receiver resolves through a held-back declaration to a java collection; or the callee is a
    * genuine external seam ([[externalCallee]]). Not "not owned" alone: a refused `super.putAll`
    * bridged anyway would name the helper instead of the member it could not rewrite (K6.5). */
  private def keepsJavaFormals(t: Tree.Apply)(using Program): Boolean =
    literal(t.method) || externalCallee(t.method) || (t.fun match
      case Tree.Select(recv, _, _, _) => actualOf(recv)._2
      case _                          => false)

  /** the callee's declared formals, or `Nil` where it has none. */
  private def formalsOf(t: Tree.Apply)(using p: Program): List[TypeRepr] =
    p.symbolOf(t.method).map(_.info).collect {
      case TypeRepr.MethodType(ps, _, _)                       => ps.map(_._2)
      case TypeRepr.PolyType(_, TypeRepr.MethodType(ps, _, _)) => ps.map(_._2)
    }.getOrElse(Nil)

  /** The consumer half of the external seam — a value this phase retyped, at a formal it did
    * not and cannot (a class file's, K15, or a held-back declaration's). Bridged with a live
    * `JavaCollections.toJava` view. Runs where the seam count runs — on a call nothing else
    * rewrote — never in `wrapIterableArgs`, since a `java.util.*` formal may belong to a method
    * this phase is about to RETARGET, and bridging first would hand the rewritten call a wrapped
    * argument its new target does not want (measured: 8 specs the first time merged). */
  private def bridgeJavaFormals(t: Tree.Apply)(using p: Program): Tree.Apply =
    if !keepsJavaFormals(t) then t
    else
      val formals = formalsOf(t)
      if formals.sizeIs != t.args.size then t
      else
        // an Object formal bridges only at a CLASS FILE's slot — a scoped-out or held-back
        // declaration's own body keeps expecting what it always did
        val external = externalCallee(t.method)
        // a declared reflective sink reads the runtime representation it is handed (K21 face 1);
        // asked here and not in coerce, since the sink is a fact about the CALLEE
        val sink = if external then sinkOf(t.method) else scala.None
        val as = t.args.zip(formals).map((a, f) =>
          coerce(f, a, expectedScoped = true, expectedExternal = external,
                 expectedSink = sink.isDefined))
        if as != t.args then sink.foreach(fqn => bridgedSinkCallees += (t.method -> fqn))
        if as == t.args then t else t.copy(args = as)

  /** The same bridge where there is no formal to read — a generic external method has no
    * readable `MethodType`, so [[bridgeJavaFormals]]'s arity test declines. For an ordinary
    * external callee that refusal is the honest answer and counted; for a DECLARED SINK it is
    * not, since the port already stated the fact the signature would have carried — the argument
    * decides instead: a retyped value or `java.lang.Object`. Measured: with the arity path alone,
    * one of liqp's seven sink sites bridged. */
  private def bridgeSinkArgs(t: Tree.Apply)(using p: Program): Tree.Apply =
    if formalsOf(t).sizeIs == t.args.size || !externalCallee(t.method) then t
    else sinkOf(t.method) match
      case scala.None => t
      case Some(fqn) =>
        val as = t.args.map(a =>
          if !mayBeRetypedValue(a) || toJavaValueSym == SymId.None then a
          else Tree.Apply(Tree.Ident(toJavaValueSym, TypeRepr.NoType, a.origin), List(a),
                          toJavaValueSym, objectTpe(a), a.origin))
        if as == t.args then t
        else
          bridgedSinkCallees += (t.method -> fqn)
          t.copy(args = as)

  /** Could this value be a representation this phase introduced? A type it retyped, one of its
    * own shims, or `java.lang.Object` (says nothing). Read from the phase's own tables (§4.56). */
  private def mayBeRetypedValue(a: Term)(using p: Program): Boolean =
    headSym(a.tpe).exists(s => kindOf.contains(s) || shimSyms.contains(s) ||
      p.symbolOf(s).exists(_.fullName == CollectionsTransform.ObjectFqn))

  /** `java.lang.Object` as this program spells it — the bridge's result type. Falls back to the
    * argument's own type where the program never names `Object`. */
  private def objectTpe(a: Term)(using p: Program): TypeRepr =
    p.symbols.all.find(_.fullName == CollectionsTransform.ObjectFqn)
      .map(s => TypeRepr.TypeRef(TypeRepr.NoPrefix, s.id)).getOrElse(a.tpe)

  /** The declared reflective sink this callee belongs to, by its OWNER — the phase's own policy
    * read as symbols, never a name test (§4.56). `None` where the port declares none. */
  private def sinkOf(m: SymId)(using p: Program): Option[String] =
    if sinkSyms.isEmpty then scala.None
    else p.symbolOf(m).map(_.owner).filter(sinkSyms.contains).flatMap(p.symbolOf).map(_.fullName)

  /** A `return` is a shim-typed slot exactly as a formal or `val` is — the declared return type
    * is the expected type of every `return` in the body. The walk is DELIBERATELY BOUNDED: a
    * `return` inside a lambda, anon class or local class returns from THAT, so only node kinds
    * carrying a statement of the same method are followed; an unhandled kind therefore MISSES a
    * coercion (a loud compile error) rather than wrongly coercing one. A method body's tail
    * expression is not a return value here — every java method exits through `Tree.Return`, so
    * `Block.expr` is a statement or `()`. */
  override def transformDefDef(t: Tree.DefDef)(using Program): Tree.DefDef =
    citeIfReified(t.symbol)
    val coerced = t.copy(rhs = t.rhs.map(coerceReturns(t.returnTpt.tpe, _)))
    if retFeReturnApplies.isEmpty then coerced
    else coerced.copy(rhs = coerced.rhs.map(wrapReturnBoundary(t.returnTpt.tpe, _)))

  private def coerceReturns(want: TypeRepr, t: Term)(using Program): Term = t match
    case x: Tree.Return       => x.copy(expr = x.expr.map(coerce(want, _)))
    case x: Tree.Block        => x.copy(stats = x.stats.map(coerceReturnsIn(want, _)), expr = coerceReturns(want, x.expr))
    case x: Tree.If           => x.copy(thenp = coerceReturns(want, x.thenp), elsep = coerceReturns(want, x.elsep))
    case x: Tree.While        => x.copy(body = coerceReturns(want, x.body))
    case x: Tree.DoWhile      => x.copy(body = coerceReturns(want, x.body))
    case x: Tree.For          => x.copy(body = coerceReturns(want, x.body))
    case x: Tree.ForEach      => x.copy(body = coerceReturns(want, x.body))
    case x: Tree.Synchronized => x.copy(body = coerceReturns(want, x.body))
    case x: Tree.Labeled      => x.copy(stmt = coerceReturns(want, x.stmt))
    // must read through the comment wrapper (§4.58) — a return under a comment is still a return
    case x: Tree.Commented    => x.copy(stmt = coerceReturns(want, x.stmt))
    case x: Tree.Try =>
      x.copy(body = coerceReturns(want, x.body),
             catches = x.catches.map(c => c.copy(body = coerceReturns(want, c.body))),
             finalizer = x.finalizer.map(coerceReturns(want, _)))
    case x: Tree.Match => x.copy(cases = x.cases.map(c => c.copy(body = coerceReturns(want, c.body))))
    case other         => other

  /** a `Block` statement that is a TERM continues this method's return scope; a `ValDef` cannot
    * contain a `return` at all, and a nested `DefDef`/`ClassDef` opens its own. */
  private def coerceReturnsIn(want: TypeRepr, s: Statement)(using Program): Statement = s match
    case t: Term => coerceReturns(want, t)
    case other   => other

  /** Wrap Apply nodes registered in [[retFeReturnApplies]] with a `boundary[R]` whose
    * fallthrough value is whatever code follows the Apply in the enclosing Block. The `Return`
    * nodes inside the lambda body are already `boundary.break(v)(using label)` (via
    * [[rewriteReturnsToBreaks]]); a tail `Return` becomes the boundary's fallthrough expression. */
  private def wrapReturnBoundary(retType: TypeRepr, body: Term)(using p: Program): Term = body match
    case b: Tree.Block =>
      // scan stats for a registered Apply
      val idx = b.stats.indexWhere {
        case t: Term => retFeReturnApplies.containsKey(t)
        case _       => false
      }
      if idx < 0 then
        // recurse into statement-carrying nodes
        b.copy(
          stats = b.stats.map {
            case t: Term => wrapReturnBoundary(retType, t)
            case other   => other
          },
          expr = wrapReturnBoundary(retType, b.expr)
        )
      else
        val applyNode = b.stats(idx).asInstanceOf[Term]
        val label = retFeReturnApplies.get(applyNode)
        retFeReturnApplies.remove(applyNode)
        val so = applyNode.origin
        // gather tail: everything after the Apply in the Block
        val tailStats = b.stats.drop(idx + 1)
        val tailExpr  = b.expr
        // fallthrough is the tail statements with Return stripped. Every java method exits
        // through Tree.Return, so Block.expr is (); if a tail Return exists its value IS the
        // fallthrough and the block's () is excluded, else the block's expr IS the fallthrough.
        val tailHasReturn = tailStats.exists {
          case _: Tree.Return => true
          case _ => false
        }
        val fallthroughParts =
          if tailHasReturn then
            tailStats.collect { case t: Term => stripReturn(t) }
          else
            tailStats.collect { case t: Term => stripReturn(t) } :+ stripReturn(tailExpr)
        // the return type is rendered as an Opaque.spliced with the HEAD SYMBOL as an AST hole so
        // PackageRenameTransform reaches it — a text-rendered fullName would be the upstream FQN
        val retTypeRendered: Term = retType match
          case TypeRepr.TypeRef(_, s) =>
            Tree.Ident(s, retType, so)
          case TypeRepr.AppliedType(TypeRepr.TypeRef(_, s), args) =>
            val argsText = args.map(renderTypeForBoundary).mkString(", ")
            Tree.Opaque.spliced(List("", s"[$argsText]"), List(Tree.Ident(s, retType, so)), retType, so)
          case _ =>
            // fallback: render as text (primitive types, Unit, etc.)
            Tree.Opaque(renderTypeForBoundary(retType), retType, so)
        // two type holes: one for boundary[R] and one for Label[R]
        val allHoles    = retTypeRendered :: retTypeRendered :: applyNode :: fallthroughParts
        // boundary[R] { (label: Label[R]) ?=> hole0; hole1; ...; holeN }
        val parts       = new collection.mutable.ListBuffer[String]
        parts += "scala.util.boundary["
        parts += s"] { ($label: scala.util.boundary.Label["
        parts += "]) ?=> "
        for i <- 0 until (allHoles.size - 2 - 1) do parts += "; "
        parts += " }"
        val boundaryNode = Tree.Opaque.spliced(parts.toList, allHoles, retType, so)
        // replace the Apply + tail with the boundary
        val prefix = b.stats.take(idx).map {
          case t: Term => wrapReturnBoundary(retType, t)
          case other   => other
        }
        if prefix.isEmpty then boundaryNode
        else Tree.Block(prefix.toList, boundaryNode, retType, so)
    case x: Tree.If =>
      x.copy(thenp = wrapReturnBoundary(retType, x.thenp),
             elsep = wrapReturnBoundary(retType, x.elsep))
    case x: Tree.Labeled => x.copy(stmt = wrapReturnBoundary(retType, x.stmt))
    case x: Tree.Commented => x.copy(stmt = wrapReturnBoundary(retType, x.stmt))
    case x: Tree.Synchronized => x.copy(body = wrapReturnBoundary(retType, x.body))
    case x: Tree.Try =>
      x.copy(body = wrapReturnBoundary(retType, x.body))
    case _ => body

  /** Strip a `Return` wrapper, keeping only its value expression. Used to convert a method-level
    * `return false` into the boundary's fallthrough `false`. */
  private def stripReturn(t: Term): Term = t match
    case Tree.Return(Some(v), _, _) => v
    case Tree.Return(scala.None, _, so) => Tree.Opaque("()", unitTpe, so)
    case other => other

  /** Strip a `Return` wrapper from a Statement. */
  private def stripReturn(s: Statement): Term = s match
    case t: Term => stripReturn(t)
    case _ => Tree.Opaque("()", unitTpe, Origin.synthetic)

  /** Render a TypeRepr as a fully-qualified name for the boundary's type parameter.
    * Only needs to handle the return types that java methods actually produce — primitives,
    * classes, applied generics. A type that cannot be rendered falls back to `scala.Any`,
    * which is the conservative answer (the boundary accepts any value). */
  private def renderTypeForBoundary(t: TypeRepr)(using p: Program): String = t match
    case TypeRepr.TypeRef(_, s) =>
      p.symbolOf(s).map(_.fullName).getOrElse("scala.Any")
    case TypeRepr.AppliedType(tc, args) =>
      val baseName = renderTypeForBoundary(tc)
      s"$baseName[${args.map(renderTypeForBoundary).mkString(", ")}]"
    // a wildcard type argument is a TypeBounds in the TIR; render as `?` with its bounds so
    // boundary[BaseLight[?]] is legal — writable inside an argument position (CLAUDE.md §4.56).
    case TypeRepr.TypeBounds(TypeRepr.NoType, TypeRepr.NoType) => "?"
    case TypeRepr.TypeBounds(TypeRepr.NoType, hi) => s"? <: ${renderTypeForBoundary(hi)}"
    case TypeRepr.TypeBounds(lo, TypeRepr.NoType) => s"? >: ${renderTypeForBoundary(lo)}"
    case TypeRepr.TypeBounds(lo, hi) =>
      s"? >: ${renderTypeForBoundary(lo)} <: ${renderTypeForBoundary(hi)}"
    case TypeRepr.NoType => "scala.Any"
    case _ => "scala.Any"

  /** Bridge a scala collection into a shim-typed slot (argument, val, assignment, return).
    * Wraps only when the source is a scala collection this phase introduced (`kindOf`).
    * `Kind.Map` into `JavaCollection` is refused (java `Map` is not a `Collection`).
    * Shims are excluded on both sides. // ENGINE-LIMITS M6 */
  private def coerce(expected: TypeRepr, actual: Term, expectedScoped: Boolean = false,
                     expectedExternal: Boolean = false, expectedSink: Boolean = false)(using p: Program): Term =
    // the symbol table is retyped AFTER the trees, so a formal read here is still java's
    // original symbol; compare through `remap`. A scoped-out side is taken literally (no factory
    // matches, left for CollectionBoundaryCheck to count as ScopedOut).
    def scalaSym(x: SymId, scoped: Boolean): SymId = if scoped then x else remap.getOrElse(x, x)
    // a conditional's conversion belongs to its branches (JLS 15.25): recurse through this
    // function per-branch rather than around the whole If, identity-preserving where unmoved.
    actual match
      case i: Tree.If =>
        val th = coerce(expected, i.thenp, expectedScoped, expectedExternal, expectedSink)
        val el = coerce(expected, i.elsep, expectedScoped, expectedExternal, expectedSink)
        return if (th ne i.thenp) || (el ne i.elsep) then i.copy(thenp = th, elsep = el) else i
      case _ => ()
    val (actualT, actualScoped) = actualOf(actual)
    val wants = headSym(expected).map(scalaSym(_, expectedScoped))
    val got   = headSym(actualT).map(scalaSym(_, actualScoped))
    // where the value is a type the PROGRAM declares, kindOf says nothing (keyed on this
    // phase's own scala targets); mintedSourceKind reads the minted ancestry instead. K26
    val from  = got.filterNot(shimSyms.contains)
                   .flatMap(g => kindOf.get(g).orElse(mintedSourceKind(g, wants)))
    // the slot that is literally a java collection (K15): expectedScoped means the expected
    // side is a scope hold-back or an external callee's formal, so a retyped value meets a
    // java.util.* that stayed and the wrap goes the other way.
    val wantsJava = expectedScoped &&
      wants.flatMap(p.symbolOf).exists(o => typeMap.contains(o.fullName))
    // the slot with no type error behind it: an Object formal takes anything, so a retyped
    // collection conforms silently while reflective third-party code sees the wrong shape.
    // toJava is faithful — java's value there really was a java collection. External only.
    val wantsUniversal = expectedExternal &&
      wants.flatMap(p.symbolOf).exists(_.fullName == CollectionsTransform.ObjectFqn)
    // the slot this phase's own stream collapse creates: where the chain's terminal crosses
    // back out to java at a Stream formal, toStream is the faithful answer. External only.
    val wantsStream = expectedExternal &&
      wants.flatMap(p.symbolOf).exists(_.fullName == CollectionsTransform.StreamFqn)
    // guards against an absent shim (SymId.None) matching an unresolved-head `wants` by accident.
    def wantsIs(s: SymId) = s != SymId.None && wants.contains(s)
    val factory = from match
      case _ if wants.isEmpty || refusedRewriteSource(actual) => SymId.None
      // the egress bridge (K21 face 1), ahead of every arm below: a declared reflective sink
      // walks the whole tree where toJava is only one level. Fired on the formal, not on `from`.
      case _ if expectedSink && wantsUniversal && toJavaValueSym != SymId.None => toJavaValueSym
      // Kind.Stack rides with Kind.Seq here: JavaStack extends mutable.ArrayBuffer, so at a
      // boundary slot it IS a Kind.Seq value and conforms to both bridges. K2.5
      case Some(Kind.Seq | Kind.Stack | Kind.Set | Kind.Map) if wantsIs(javaIterableSym) => iterableFromSym
      case Some(Kind.Seq | Kind.Stack)          if wantsIs(javaCollectionSym)  => collectionFromSym
      case Some(Kind.Set)                       if wantsIs(javaCollectionSym)  => collectionFromSetSym
      // asJava converts one level, so a nested Buffer[Buffer[...]] would lie one arg in — refused and counted.
      case Some(Kind.Seq | Kind.Stack | Kind.Set | Kind.Map)
        if (wantsJava || wantsUniversal || wantsStream) && mentionsRetyped(actualT)     => SymId.None
      case Some(Kind.Seq | Kind.Stack | Kind.Set | Kind.Map)
        if (wantsJava || wantsUniversal) && toJavaSym != SymId.None                     => toJavaSym
      // a Stream formal takes the collapse's result back to java; Kind.Map excluded (no stream() on java Map).
      case Some(Kind.Seq | Kind.Stack | Kind.Set) if wantsStream && toStreamSym != SymId.None => toStreamSym
      // the retained parent's own slot (K5.7): a class keeping java's Map.Entry meets the
      // Tuple2 slot every use of that interface got. Decided in detachedEntriesIn, never here.
      case _ if entryToPairSym != SymId.None &&
                got.flatMap(detachedEntries.get).exists(tgt =>
                  wants.flatMap(p.symbolOf).exists(_.fullName == tgt))            => entryToPairSym
      // a retarget target's iterator() returns scala.collection.Iterator[T], but JavaIterator[T]
      // is expected (java.util.Iterator redirect) — wrap with JavaIterator.from, compared by FQN.
      case _ if wantsIs(javaIteratorSym) && iteratorFromSym != SymId.None &&
               got.flatMap(p.symbolOf).exists(_.fullName == "scala.collection.Iterator") => iteratorFromSym
      case _                                                                          => SymId.None
    if factory == SymId.None then
      // retarget coercion: a §1(b) parameterised boundary wrap between a retarget target and
      // its expected type via a `retargetCoercions` template, keyed (actual FQN, expected FQN).
      if retargetCoercions.nonEmpty then
        val gotFqn   = got.flatMap(p.symbolOf).map(_.fullName)
        val wantsFqn = wants.flatMap(p.symbolOf).map(_.fullName)
        (gotFqn, wantsFqn) match
          case (Some(gf), Some(wf)) if retargetCoercions.contains((gf, wf)) =>
            val template = retargetCoercions((gf, wf))
            renderRetargetCoercion(template, actual, expected, actual.origin)
          case _ => actual
      else actual
    else
      // typed as the RETYPED expected type, not the one read above (the symbol table retypes
      // after the trees) — else this node would claim a java type the port no longer produces. K6
      val tpe = wants.map(withHead(expected, _)).getOrElse(expected)
      Tree.Apply(Tree.Ident(factory, TypeRepr.NoType, actual.origin), List(actual),
                 factory, tpe, actual.origin)

  /** Render a retarget coercion template, wrapping `actual` in a `Tree.Opaque.spliced` expression.
    * `$0` in the template is the actual value; everything else is literal text. The result is typed
    * at the `expected` type. */
  private def renderRetargetCoercion(template: String, actual: Term, expected: TypeRepr,
      origin: Origin): Term =
    val ph      = "$0"
    val indices = scala.collection.mutable.ListBuffer.empty[Int]
    var idx     = 0
    while { idx = template.indexOf(ph, idx); idx >= 0 } do
      indices += idx
      idx += ph.length
    if indices.isEmpty then
      Tree.Opaque(template, expected, origin)
    else
      val parts = scala.collection.mutable.ListBuffer.empty[String]
      val holes = scala.collection.mutable.ListBuffer.empty[Term]
      var pos   = 0
      for p <- indices do
        parts += template.substring(pos, p)
        holes += actual
        pos = p + ph.length
      parts += template.substring(pos)
      Tree.Opaque.spliced(parts.toList, holes.toList, expected, origin)

  /** the runtime shims, as scala symbols — a source already typed as one is never re-wrapped. */
  private def shimSyms: Set[SymId] =
    Set(javaIterableSym, javaIteratorSym, javaListIteratorSym, javaCollectionSym)

  /** the shims as FQNs, so a `typeMap` target can be recognised as one — [[shimSyms]] answers
    * only for a program that names the shim's java original, interned on first reference. */
  private def shimFqns: Set[String] = CollectionsTransform.ShimFqns

  /** True when the type is a shim or inherits from one (transitively, fuel-bounded).
    * Suppresses arity rewrites on receivers that carry java's member shape. */
  private def shimShaped(t: TypeRepr)(using p: Program): Boolean =
    def isShim(s: SymId): Boolean =
      shimSyms.contains(s) ||
        p.symbolOf(s).map(_.fullName).exists(fq => typeMap.get(fq).exists((tgt, _) => shimFqns(tgt)))
    // what sits above this symbol: a class's parents, or a type parameter's upper bound (a
    // receiver typed I extends Cursor<Integer> has Cursor's members exactly as a subclass would).
    def above(s: SymId): List[SymId] = p.definitionOf(s) match
      case Some(c: Tree.ClassDef) => c.parents.flatMap {
        case tt: TypeTree => headSym(tt.tpe)
        case x: Term      => headSym(x.tpe)
      }
      case Some(td: Tree.TypeDef) => td.rhs.tpe match
        case TypeRepr.TypeBounds(_, hi) => headSym(hi).toList
        case other                      => headSym(other).toList
      case _ => Nil
    def go(s: SymId, fuel: Int): Boolean =
      s != SymId.None && fuel > 0 && (isShim(s) || above(s).exists(go(_, fuel - 1)))
    headSym(t).exists(go(_, 16))

  /** kind-aware call rewrite; `None` = leave the call as-is (same-named method binds to
    * the scala API against the retyped receiver at compile time). */
  private def rewrite(k: Kind, recv: Term, m: SymId, so: Origin, t: Tree.Apply)(using Program): Option[Term] =
    val name = methodName(m)
    /** is the receiver one of the runtime SHIMS rather than a scala collection? Java's arity and
      * member names mean the scala-shaped rewrites below must leave them alone — a blanket refusal
      * (`case _ if onShim`), asked of the ANCESTRY via [[shimShaped]], not the head symbol. */
    val onShim = shimShaped(recv.tpe)
    /** is the receiver `super`? Scala allows `super` only as a Select qualifier; several rewrites
      * below would otherwise place it elsewhere (E040 syntax error). Checked structurally after the
      * rewrite is built — see [[superPlaced]] — so a new arm cannot reintroduce it by omission. */
    val onSuper = recv.isInstanceOf[Tree.Super]
    val out = (name, t.args, k) match
      // java 8's forEach has no shim counterpart; JavaIterable supplies foreach as an extension (§4.5).
      case ("forEach", List(f), _) => Some(call(recv, foreachSym, List(f), t, so))
      // `toArray`: strip erasure coercion via `arrayArg` (no call reshape needed)
      // // ENGINE-LIMITS G14
      // than a new callee.
      case ("toArray", List(a), _) if onShim =>
        val stripped = arrayArg(a, t)
        Option.when(stripped ne a)(t.copy(args = List(stripped)))
      // wildcard capture read coercion: `asInstanceOf[Object]` for unbounded `?` on a shim
      // // ENGINE-LIMITS G23, G24, G33
      case _ if onShim && wildcardElement(recv.tpe) && capturedObjectRead(t) =>
        Some(Tree.Typed(t, TypeTree(t.tpe, t.origin), t.tpe, t.origin))
      case _ if onShim             => None
      // JDK bulk defaults (`containsAll`/`addAll`/`removeAll`/`retainAll`) via VirtualJdkDefaults
      // // ENGINE-LIMITS K29
      case (n, List(c), Kind.Seq | Kind.Set)
        if onSuper && CollectionsTransform.VirtualJdkDefaults.contains(n)
           && sym(CollectionsTransform.VirtualJdkDefaults(n)) != SymId.None
           && superLostItsDefault(recv, n) =>
        val f = sym(CollectionsTransform.VirtualJdkDefaults(n))
        recv match { case Tree.Super(cls, _, _) => superDefaults += ((cls, m, n)); case _ => () }
        Some(Tree.Apply(Tree.Ident(f, TypeRepr.NoType, so), List(thisOf(recv), c), f, t.tpe, t.origin))
      // Stack push/pop/peek/search are shim members, no rewrite. empty() can't be java's — scala's
      // `empty` is already the factory — so it's renamed to the predicate asking the same question.
      case ("empty", Nil, Kind.Stack) => Some(Tree.Select(recv, isEmptySym, t.tpe, t.origin))
      case ("push" | "pop" | "peek" | "search", _, Kind.Stack) => None
      // java.util.Optional{Int,Long,Double}, target is an Option[...] alias — pure renames
      // except orElse. get/isDefined are parameterless (Select, not Apply). orElseThrow() is get
      // (both throw NoSuchElementException on empty); the supplier overload has no arm.
      case ("getAsInt" | "getAsLong" | "getAsDouble" | "orElseThrow", Nil, Kind.Opt) =>
        Some(Tree.Select(recv, getSym, t.tpe, t.origin))
      case ("isPresent", Nil, Kind.Opt)      => Some(Tree.Select(recv, isDefinedSym, t.tpe, t.origin))
      // orElse is the one non-rename: java evaluates the argument eagerly, Option.getOrElse
      // lazily — optionalOrElse restores java's by-value evaluation. CLAUDE.md §4.4
      case ("orElse", List(d), Kind.Opt) if sym("optionalOrElse") != SymId.None =>
        val f = sym("optionalOrElse")
        Some(Tree.Apply(Tree.Ident(f, TypeRepr.NoType, so), List(recv, d), f, t.tpe, t.origin))
      case ("ifPresent", List(f), Kind.Opt)  => Some(call(recv, foreachSym, List(f), t, so))
      // m.entrySet() is the VIEW of the map as (key, value) pairs; a scala Map[K, V] already IS
      // an Iterable[(K, V)], so the view is the map itself (Tuple2 loses setValue write-through,
      // which now fails to compile rather than writing to a detached copy).
      // list.iterator() yields a scala.collection.Iterator, but every declaration derived from
      // java.util.Iterator wants the removal-capable shim; decided on provenance (a scala
      // collection's iterator is scala's). Not on shims themselves — already JavaIterator.
      case ("iterator", Nil, _) if iteratorFromSym != SymId.None =>
        val sel = Tree.Select(recv, m, t.tpe, t.origin) // parenless, as the generic case below
        Some(Tree.Apply(Tree.Ident(iteratorFromSym, TypeRepr.NoType, so), List(sel), iteratorFromSym, t.tpe, so))
      // list.listIterator()/listIterator(i) — java's bidirectional cursor, refused as
      // scala.collection.Iterator (K23) but the receiver is mutable.Buffer, whose indexed
      // read/update/insert/remove ARE ListIterator's contract — a §4.5 standalone shim.
      // `over` writes through to the caller's buffer; Kind.Seq only, java declares it on List
      case ("listIterator", args @ (Nil | List(_)), Kind.Seq | Kind.Stack)
        if listIteratorOverSym != SymId.None =>
        Some(Tree.Apply(Tree.Ident(listIteratorOverSym, TypeRepr.NoType, so), recv :: args,
                        listIteratorOverSym, t.tpe, so))
      // c.spliterator() — K23's other refusal, kept refused unlike listIterator: nothing about
      // streams is modelled, so java's DEFAULT METHOD characteristics are stated directly
      // (Collection=0, List=ORDERED, Set=DISTINCT, all OR SIZED|SUBSIZED) rather than delegated
      // to the converter's wrapper — they follow JAVA'S declaration at the receiver's kind.
      case ("spliterator", Nil, k @ (Kind.Seq | Kind.Stack | Kind.Set))
        if orderedSpliteratorSym != SymId.None =>
        val f = if k == Kind.Set then distinctSpliteratorSym else orderedSpliteratorSym
        Some(Tree.Apply(Tree.Ident(f, TypeRepr.NoType, so), List(recv), f, t.tpe, so))
      // m.values() is the same provenance problem as iterator() above: Map.values() is declared
      // Collection<V>, so downstream slots want the shim while the emitted m.values is scala's
      // Iterable. Wrapping restores the invariant that a node's type describes what it emits.
      // unmodifiableFrom, not from: java's values() is a read-only view.
      case ("values", Nil, Kind.Map) if unmodifiableFromSym != SymId.None =>
        val sel = Tree.Select(recv, m, t.tpe, t.origin) // parenless, as the generic case below
        Some(Tree.Apply(Tree.Ident(unmodifiableFromSym, TypeRepr.NoType, so), List(sel), unmodifiableFromSym, t.tpe, so))
      // m.keySet()/m.entrySet() are java's live, write-through map views — the same provenance
      // gap values() has, widest here (keySet lost a capability, entrySet lost the Set shape
      // entirely). Fixed at the source: the rewrite emits a value that really has the type the
      // node claims, so every downstream position is answered at once (11 errors closed).
      // A VIEW, not a copy — java's is live in both directions.
      case ("keySet", Nil, Kind.Map) if sym("keySetView") != SymId.None =>
        Some(staticCall(sym("keySetView"), List(recv), t, so))
      case ("entrySet", Nil, Kind.Map) if sym("entrySetView") != SymId.None =>
        Some(staticCall(sym("entrySetView"), List(recv), t, so))
      case ("entrySet", Nil, Kind.Map)          => Some(recv)
      case ("getKey", Nil, Kind.Entry)          => Some(Tree.Select(recv, key1Sym, t.tpe, t.origin))
      case ("getValue", Nil, Kind.Entry)        => Some(Tree.Select(recv, value2Sym, t.tpe, t.origin))
      // never on a shim receiver (blanket guard above): shims deliberately carry java's arity
      // (iterator(), hasNext(), next()), stripping () there emits it.hasNext against def hasNext()
      case (n, Nil, _) if parenless(n)          => Some(Tree.Select(recv, m, t.tpe, t.origin)) // drop `()`
      case ("get", List(i), Kind.Seq)           => Some(Tree.Apply(recv, List(i), m, t.tpe, t.origin)) // xs(i)
      // a wildcard-typed map is java's three Object-keyed members and nothing else (wildcardMapCall);
      // the same helpers answer an Object PROBE at a moved key type (objectProbe). keyArg runs first.
      case (n, List(key), Kind.Map) if wildcardMapCall(n, recv, keyArg(key, recv)) || probeMapCall(n, keyArg(key, recv), recv) =>
        Some(staticCall(wildcardMapSym(n), List(recv, keyArg(key, recv)), t, so))
      case ("get", List(key), Kind.Map)         => Some(call(recv, getOrElseSym, List(keyArg(key, recv), dflt(nullOf(so), recv, so)), t, so))
      case ("getOrDefault", List(key, d), _)    => Some(call(recv, getOrElseSym, List(keyArg(key, recv), dflt(d, recv, so)), t, so))
      case ("set", List(i, x), Kind.Seq)        => Some(call(recv, updateSym, List(i, x), t, so)) // xs(i) = x
      // java's Map.put RETURNS THE PREVIOUS VALUE; scala's put keeps it as an Option, so
      // getOrElse(null) restores java's contract that update() would have discarded
      case ("put", List(key, v), Kind.Map)      =>
        Some(call(call(recv, putSym, List(keyArg(key, recv), v), t, so), getOrElseSym, List(dflt(nullOf(so), recv, so)), t, so))
      // likewise `Map.remove`, which returns the value that was there.
      case ("remove", List(key), Kind.Map)      =>
        Some(call(call(recv, removeSym, List(keyArg(key, recv)), t, so), getOrElseSym, List(dflt(nullOf(so), recv, so)), t, so))
      // `remove(Object)` by-value overload — distinguished from `remove(int)` by result type (CLAUDE.md §4.4)
      case ("remove", List(x), Kind.Seq) if removesByValue(t) && sym("removeValue") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("removeValue"), TypeRepr.NoType, so), List(recv, x),
                        sym("removeValue"), t.tpe, t.origin))
      // Collection.toArray()/toArray(T[]): scala's toArray is parenless, so xs.toArray() misparses
      // as an Array index (missing argument for apply). JavaCollections helpers restore java's
      // contract — toArray() allocates Object[]; toArray(T[]) fills the caller's array or
      // allocates on the runtime component type with a null terminator (§4.4).
      case ("toArray", Nil, Kind.Seq | Kind.Set) if sym("toArray") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("toArray"), TypeRepr.NoType, so), List(recv),
                        sym("toArray"), t.tpe, t.origin))
      case ("toArray", List(a), Kind.Seq | Kind.Set) if sym("toArray") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("toArray"), TypeRepr.NoType, so), List(recv, arrayArg(a, t)),
                        sym("toArray"), t.tpe, t.origin))
      // subList is a write-through view (java) where slice is a copy; putIfAbsent returns the
      // PREVIOUS value (null on success), the opposite of getOrElseUpdate — §4.4 shapes.
      case ("subList", List(a, b), Kind.Seq) if sym("subList") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("subList"), TypeRepr.NoType, so), List(recv, a, b),
                        sym("subList"), t.tpe, t.origin))
      case ("putIfAbsent", List(key, v), Kind.Map) if sym("putIfAbsent") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("putIfAbsent"), TypeRepr.NoType, so),
                        List(recv, keyArg(key, recv), v), sym("putIfAbsent"), t.tpe, t.origin))
      // SE8 default methods on the interfaces, each with a scala near-miss: sort mutates in place
      // (sorted copies); computeIfAbsent treats null as absent and records nothing on a null
      // factory result; removeIf is filterInPlace's complement returning java's boolean;
      // containsValue/containsAll ask the PROBE's equals; ensureCapacity is a no-op hint.
      case ("sort", List(c), Kind.Seq) if sym("sort") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("sort"), TypeRepr.NoType, so), List(recv, c),
                        sym("sort"), t.tpe, t.origin))
      case ("computeIfAbsent", List(key, f), Kind.Map) if sym("computeIfAbsent") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("computeIfAbsent"), TypeRepr.NoType, so),
                        List(recv, keyArg(key, recv), f), sym("computeIfAbsent"), t.tpe, t.origin))
      // the Set spelling is a different helper, not an overload: the two erase alike, so the
      // choice is made by receiver kind at the call rather than by run-time dispatch.
      case ("removeIf", List(p), Kind.Seq) if sym("removeIf") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("removeIf"), TypeRepr.NoType, so), List(recv, p),
                        sym("removeIf"), t.tpe, t.origin))
      case ("removeIf", List(p), Kind.Set) if sym("removeIfSet") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("removeIfSet"), TypeRepr.NoType, so), List(recv, p),
                        sym("removeIfSet"), t.tpe, t.origin))
      case ("containsValue", List(v), Kind.Map) if sym("containsValue") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("containsValue"), TypeRepr.NoType, so), List(recv, v),
                        sym("containsValue"), t.tpe, t.origin))
      case ("containsAll", List(c), Kind.Seq | Kind.Set) if sym("containsAll") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("containsAll"), TypeRepr.NoType, so), List(recv, c),
                        sym("containsAll"), t.tpe, t.origin))
      // containsAll's two mutating siblings: mutable.Buffer has neither at all. `--=` removes one
      // occurrence per argument element where java removes every occurrence; filterInPlace keeps
      // the complement and returns the collection, not java's boolean.
      case ("removeAll", List(c), Kind.Seq | Kind.Set) if sym("removeAll") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("removeAll"), TypeRepr.NoType, so), List(recv, c),
                        sym("removeAll"), t.tpe, t.origin))
      case ("retainAll", List(c), Kind.Seq | Kind.Set) if sym("retainAll") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("retainAll"), TypeRepr.NoType, so), List(recv, c),
                        sym("retainAll"), t.tpe, t.origin))
      case ("ensureCapacity", List(n), Kind.Seq) if sym("ensureCapacity") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("ensureCapacity"), TypeRepr.NoType, so), List(recv, n),
                        sym("ensureCapacity"), t.tpe, t.origin))
      case ("add", List(i, x), Kind.Seq)        => Some(call(recv, insertSym, List(i, x), t, so)) // insert at index
      case ("add", List(x), _)                  => Some(infix(recv, opPlusEq, List(x), t, so))    // xs += x
      // java Deque (LinkedList/ArrayDeque): addLast/offer append, addFirst prepends.
      case ("addLast" | "offer" | "offerLast", List(x), Kind.Seq) => Some(infix(recv, opPlusEq, List(x), t, so))
      case ("addFirst" | "offerFirst", List(x), Kind.Seq)         => Some(call(recv, prependSym, List(x), t, so))
      // poll/peek return null on an empty deque; remove(0)/head throw, so a direct mapping would
      // turn "empty" into an exception. orNull is a Select (parameterless), never an Apply.
      case ("poll" | "pollFirst", Nil, Kind.Seq) =>
        Some(Tree.Select(call(recv, removeHeadOptionSym, Nil, t, so), orNullSym, t.tpe, so))
      case ("peek" | "peekFirst" | "element", Nil, Kind.Seq) =>
        Some(Tree.Select(Tree.Select(recv, headOptionSym, TypeRepr.NoType, so), orNullSym, t.tpe, so))
      // addAll from a wildcard-elemented source is not ++= — see [[wildcardElement]].
      case ("addAll", List(c), _) if (wildcardElement(c.tpe) || standaloneSource(c.tpe)) &&
                                     sym("addAll") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("addAll"), TypeRepr.NoType, so), List(recv, c),
                        sym("addAll"), t.tpe, t.origin))
      // java's positional addAll(int, Collection), insert's bulk sibling — left to fall through,
      // scala AUTO-TUPLES the two arguments against Growable.addAll(IterableOnce), silently
      // appending a pair at an Any element type instead of inserting (§4.4).
      case ("addAll", List(i, c), Kind.Seq | Kind.Stack) if sym("insertAll") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("insertAll"), TypeRepr.NoType, so), List(recv, i, c),
                        sym("insertAll"), t.tpe, t.origin))
      case ("addAll" | "putAll", List(c), _)    => Some(infix(recv, opPlusPlusEq, List(c), t, so))// xs ++= c
      // the Set half of objectProbe's seam: scala's Set.contains is already java's own lookup at
      // an ordinary argument, so this arm exists solely for the widened probe.
      case ("contains", List(x), Kind.Set) if setContainsSym != SymId.None && probeSetCall(x, recv) =>
        Some(staticCall(setContainsSym, List(recv, x), t, so))
      case ("remove", List(x), Kind.Set) if setRemoveSym != SymId.None && probeSetCall(x, recv) =>
        Some(staticCall(setRemoveSym, List(recv, x), t, so))
      case ("remove", List(x), Kind.Set)        => Some(infix(recv, opMinusEq, List(x), t, so)) // xs -= x
      case ("containsKey", List(key), Kind.Map) => Some(call(recv, containsSym, List(keyArg(key, recv)), t, so))
      // a Stack is a List for everything the five LIFO arms above did not take (Stack extends
      // Vector extends List) — a RE-ENTRY at Kind.Seq, not a second copy of the table.
      case _ if k == Kind.Stack                 => rewrite(Kind.Seq, recv, m, so, t)
      case _                                    => None
    if !onSuper then out
    else
      // where the shape puts `super` somewhere scala has no position for, retry standing on
      // `this` instead — exact only under [[superIsThis]]'s whole-program condition.
      out.filter(superPlaced).orElse(
        if superIsThis(recv, name) then rewrite(k, thisOf(recv), m, so, t).filter(superPlaced)
        else scala.None)

  /** May a rewrite that cannot stand on `super` stand on `this` instead — do `super.m` and
    * `this.m` name the same member for every value this expression can have? True iff neither the
    * class itself nor any subclass IN THIS PROGRAM declares `m` (a whole-program question the port
    * cannot answer beyond its own scope, but the alternative is no emission at all — a refused
    * rewrite that does not compile). Both walks read class definitions, not the symbol table;
    * the subclass walk is transitive. */
  private def superIsThis(recv: Term, member: String)(using p: Program): Boolean = recv match
    case Tree.Super(cls, _, _) if cls != SymId.None =>
      val all      = PackageRenameTransform.allClasses(p)
      val byId     = all.map(c => c.symbol -> c).toMap
      def declares(c: Tree.ClassDef): Boolean = c.body.exists {
        case d: Tree.DefDef => methodName(d.symbol) == member
        case _              => false
      }
      def parentsOf(c: Tree.ClassDef): List[SymId] = c.parents.flatMap {
        case tt: TypeTree => headSym(tt.tpe)
        case term: Term   => headSym(term.tpe)
      }
      /** does `c` reach `cls` through its parents? Fuel-bounded; an exhausted walk counts as
        * reaching, the conservative answer since the caller refuses on `true`. */
      def below(c: Tree.ClassDef, fuel: Int): Boolean =
        fuel <= 0 || parentsOf(c).exists(s => s == cls || byId.get(s).exists(below(_, fuel - 1)))
      byId.get(cls).exists(!declares(_)) &&
        !all.exists(c => c.symbol != cls && declares(c) && below(c, 64))
    case _ => false

  /** True when re-parenting removed the JDK default this `super.<member>` targeted and no
    * program-declared ancestor declares the member. */
  private def superLostItsDefault(recv: Term, member: String)(using p: Program): Boolean = recv match
    case Tree.Super(cls, _, _) if cls != SymId.None =>
      parentClash.get(cls).exists(_.kinds.nonEmpty) && !ancestorDeclares(cls, member)
    case _ => false

  /** does any class this PROGRAM declares, strictly ABOVE `cls`, declare `member`? */
  private def ancestorDeclares(cls: SymId, member: String)(using p: Program): Boolean =
    val byId = PackageRenameTransform.allClasses(p).map(c => c.symbol -> c).toMap
    def declares(c: Tree.ClassDef): Boolean = c.body.exists {
      case d: Tree.DefDef => methodName(d.symbol) == member
      case _              => false
    }
    def parentsOf(c: Tree.ClassDef): List[SymId] = c.parents.flatMap {
      case tt: TypeTree => headSym(tt.tpe)
      case term: Term   => headSym(term.tpe)
    }
    def up(id: SymId, fuel: Int): Boolean =
      fuel <= 0 || byId.get(id).exists(c => declares(c) || parentsOf(c).exists(up(_, fuel - 1)))
    byId.get(cls).exists(c => parentsOf(c).exists(up(_, 64)))

  /** the `this` standing where `recv`'s `super` stood — same class, same origin, whose type
    * every rewrite here reads the receiver's kind from. */
  private def thisOf(recv: Term): Term = recv match
    case Tree.Super(cls, tpe, so) => Tree.This(cls, tpe, so)
    case other                    => other

  /** Does every `super` in this rewritten term stand where scala allows one — as a member
    * selection's qualifier, nowhere else? Java has no such restriction, so a rewrite can put
    * `super` where scala's grammar forbids it (`entrySet()` -> bare `super`; `Seq` `get` ->
    * `super(i)`), both `E040` syntax errors. Asked of the RESULT, not the arm, so a rewrite added
    * later is covered by construction. Walked with `StandardTraversal` (CLAUDE.md §3). ENGINE-LIMITS M6
    */
  private def superPlaced(t: Term)(using Program): Boolean =
    var bad = false
    val scan = new Phase:
      def name = "super-placement"
      override def transformTerm(x: Term)(using Program): Term =
        x match
          // a super as a Select's qualifier is the one legal position; every other occurrence is bad.
          case Tree.Select(_: Tree.Super, _, _, _) => x
          case _: Tree.Super                       => bad = true; x
          case _                                   => x
    // the qualifier of a legal Select is still visited on descent, so strip the legal ones first.
    StandardTraversal.mapTerm(scan, stripLegalSuper(t))
    !bad

  /** replace every legal `super.member` with a marker the placement scan does not object to
    * (`Tree.This`, discarded after the scan), so the scan sees only misplaced occurrences. */
  private def stripLegalSuper(t: Term)(using Program): Term =
    val strip = new Phase:
      def name = "super-strip"
      override def transformTerm(x: Term)(using Program): Term = x match
        case s @ Tree.Select(sup: Tree.Super, m, tp, o) => Tree.Select(Tree.This(SymId.None, sup.tpe, sup.origin), m, tp, o)
        case _                                          => x
    StandardTraversal.mapTerm(strip, t)

  /** is this source's sole element type an unnameable wildcard? `java.util.List<?>` means
    * `List<? extends Object>`, so `list.addAll(valueList)` type-checks in java with no cast;
    * scala's `?` is bounded by `Any`, so `Buffer[?]` is `IterableOnce[Any]` and `++=` on
    * `Buffer[Object]` fails. Widening scala's `?` is a measured dead end (G2); the difference is
    * stated at this one operation instead, by a helper doing java's own read. Narrow to a sole
    * `TypeBounds` argument — a real element type stays the idiomatic `++=`. F11
    */
  private def wildcardElement(t: TypeRepr): Boolean = t match
    case TypeRepr.AppliedType(_, List(_: TypeRepr.TypeBounds)) => true
    case _                                                     => false

  /** The other reason `++=` cannot serve java's `addAll`: the source is one of this phase's
    * standalone targets, not a `scala.collection` type — `JavaCollection extends JavaIterable`
    * and nothing else (§4.5), so it is never an `IterableOnce`. The helper this routes to already
    * takes `IterableOnce[?] | JavaIterable[?]`. Read from `shimSyms`, never a package name (§4.56). */
  private def standaloneSource(t: TypeRepr): Boolean = headSym(t).exists(shimSyms.contains)

  /** did java resolve `Collection.remove(Object)` (by value, returning `boolean`) rather than
    * `List.remove(int)` (by index)? A call whose result type the frontend could not record
    * answers `false` and falls back to scala's index removal. */
  private def removesByValue(t: Tree.Apply)(using p: Program): Boolean =
    headSym(t.tpe).flatMap(p.symbolOf).exists(_.fullName == "scala.Boolean")

  /** `recv.op(args)` where `op` is tagged an operator → emitted infix (`recv op arg`). */
  private def infix(recv: Term, op: SymId, args: List[Term], t: Tree.Apply, so: Origin): Term =
    call(recv, op, args, t, so)

  /** `recv.member(args)`. */
  private def call(recv: Term, member: SymId, args: List[Term], t: Tree.Apply, so: Origin): Term =
    Tree.Apply(Tree.Select(recv, member, TypeRepr.NoType, so), args, member, t.tpe, t.origin)

  /** `JavaCollections.member(args)` — a runtime helper, typed as what the java call it replaces
    * was recorded at (ENGINE-LIMITS K6's first rule: a node describes what it emits). */
  private def staticCall(member: SymId, args: List[Term], t: Tree.Apply, so: Origin): Term =
    Tree.Apply(Tree.Ident(member, TypeRepr.NoType, so), args, member, t.tpe, t.origin)

  /** counter for template temporary variables — one run-scoped namespace so names are stable. */
  private var templateSeq: Int = 0

  /** Renders a `RetargetRewrite.Template(expr)` into a `Tree.Opaque.spliced` (or a `Tree.Block`
    * wrapping one when temp `val` bindings are needed for repeated term placeholders).
    * Type-level placeholders (`$Target`, `$T0`…) are text-substituted; term-level ones (`$recv`,
    * `$0`…) become AST holes. A term placeholder used more than once is bound to a `val`
    * (CLAUDE.md §4.4/F7). */
  private def renderTemplate(expr: String, recv: Term, args: List[Term],
      srcFqn: String, tpe: TypeRepr, so: Origin)(using p: Program): Term =
    // $Target is text-only; also check retarget (not just typeMap) or it resolves to the source FQN.
    val targetFqn = typeMap.get(srcFqn).map(_._1).orElse(retarget.get(srcFqn)).getOrElse(srcFqn)
    var text = expr.replace("$Target", targetFqn)
    // $T0, $T1... become AST holes (not text) so a later phase (package rename) can still reach
    // the symbol's fullName. An applied type arg needs a nested spliced Opaque to keep its own
    // type arguments, or a plain Ident would render only the head.
    def typeArgToTerm(ta: TypeRepr): Term = ta match
      case TypeRepr.AppliedType(tc, innerArgs) =>
        val headTerm = typeArgToTerm(tc)
        val argTerms = innerArgs.map(typeArgToTerm)
        val parts = scala.collection.mutable.ListBuffer.empty[String]
        val holes = scala.collection.mutable.ListBuffer.empty[Term]
        parts += ""            // before the head
        holes += headTerm
        parts += "["           // between head and first arg
        argTerms.zipWithIndex.foreach { (at, j) =>
          holes += at
          if j < argTerms.size - 1 then parts += ", " else parts += "]"
        }
        Tree.Opaque.spliced(parts.toList, holes.toList, ta, so)
      case TypeRepr.TypeBounds(lo, hi) =>
        // A wildcard — render as `?` (no AST hole needed, no FQN to rename).
        Tree.Opaque("?", ta, so)
      case _ =>
        val sym = headSym(ta).getOrElse(SymId.None)
        Tree.Ident(sym, ta, so)
    val typeArgTerms = scala.collection.mutable.LinkedHashMap.empty[String, Term]
    recv.tpe match
      case TypeRepr.AppliedType(_, targs) =>
        targs.zipWithIndex.foreach { (ta, i) =>
          val ph = s"$$T$i"
          if text.contains(ph) then
            typeArgTerms(ph) = typeArgToTerm(ta)
        }
      case _ => ()
    // a term placeholder is $recv or $N (argument index); must not collide with $T0/$Target
    // (text-substituted above, may survive unresolved with no type args) or $10 matching $1+0
    def findTermPh(txt: String, ph: String): List[Int] =
      val results = scala.collection.mutable.ListBuffer.empty[Int]
      val isTypeArgPh = ph.startsWith("$T") && ph.length > 2 && ph.charAt(2).isDigit
      var idx = 0
      while { idx = txt.indexOf(ph, idx); idx >= 0 } do
        // $recv/$T0..: accept as-is; $0..$N: skip if preceded by T or followed by a digit
        val precOk = ph == "$recv" || isTypeArgPh || idx == 0 || txt.charAt(idx - 1) != 'T'
        val suffOk = ph == "$recv" || isTypeArgPh || {
          val afterEnd = idx + ph.length
          afterEnd >= txt.length || !txt.charAt(afterEnd).isDigit
        }
        if precOk && suffOk then
          results += idx
          idx += ph.length
        else
          idx += 1
      results.toList
    val termPh = scala.collection.mutable.LinkedHashMap.empty[String, Term]
    // type arg placeholders are term holes; bind before $0 etc.
    for (ph, term) <- typeArgTerms do termPh(ph) = term
    if findTermPh(text, "$recv").nonEmpty then termPh("$recv") = recv
    for i <- args.indices do
      val ph = s"$$$i"
      if findTermPh(text, ph).nonEmpty then termPh(ph) = args(i)
    val counts = termPh.map { (ph, _) => ph -> findTermPh(text, ph).size }.toMap
    // placeholders appearing >1 time bind to a temp val; subsequent occurrences become the temp name
    val bindings = scala.collection.mutable.ListBuffer.empty[(String, Term, String)]
    for (ph, term) <- termPh do
      if counts.getOrElse(ph, 0) > 1 then
        templateSeq += 1
        val tmpName = s"bp$$tpl$templateSeq"
        bindings += ((ph, term, tmpName))
        // explicit substring, not append(CharSequence,start,end) — avoids Scala 3 auto-tupling
        val phPositions = findTermPh(text, ph)
        val sb = new StringBuilder
        var pos0 = 0
        for p <- phPositions do
          sb.append(text.substring(pos0, p))
          sb.append(tmpName)
          pos0 = p + ph.length
        sb.append(text.substring(pos0))
        text = sb.toString
    // split around remaining (single-occurrence) placeholders to build parts/holes
    val positions = scala.collection.mutable.ListBuffer.empty[(Int, Int, String)]
    for (ph, _) <- termPh if counts.getOrElse(ph, 0) <= 1 do
      for p <- findTermPh(text, ph) do
        positions += ((p, p + ph.length, ph))
    val sortedPositions = positions.sortBy(_._1).toList
    val parts = scala.collection.mutable.ListBuffer.empty[String]
    val holes = scala.collection.mutable.ListBuffer.empty[Term]
    var pos = 0
    for (start, end, ph) <- sortedPositions do
      parts += text.substring(pos, start)
      holes += termPh(ph)
      pos = end
    parts += text.substring(pos)
    val opaque =
      if parts.size == 1 && holes.isEmpty then Tree.Opaque(text, tpe, so)
      else Tree.Opaque.spliced(parts.toList, holes.toList, tpe, so)
    if bindings.isEmpty then opaque
    else
      val stmts = bindings.toList.map { (_, term, tmpName) =>
        Tree.Opaque.spliced(
          List(s"val $tmpName = ", ""),
          List(term),
          TypeRepr.NoType,
          so
        )
      }
      Tree.Block(stmts, opaque, tpe, so)


  /** Is this a call on a map whose type arguments are wildcards, at one of the three members java
    * declares over `Object` (`get`/`containsKey`/`remove`)? Scala's `Map[K,V]` declares the same
    * three over `K`, so a wildcard receiver would emit an unnameable `K`/`V`. `put`/`getOrDefault`
    * are absent: each needs a value at the capture, which javac itself rejects on `Map<?,?>`.
    * Measured on liqp at 10 and 8 errors from the same nine call sites. K10 */
  /** does this type mention a wildcard at any depth? Not a nameability test — a wildcard-applied
    * type IS nameable (`Class[? <: N]`) — but a narrower question for [[wildcardMapCall]]: could
    * scala's invariance bite at this key. Complete over `TypeRepr`, never a partial walk. */
  private def mentionsWildcard(t: TypeRepr): Boolean = t match
    case _: TypeRepr.TypeBounds             => true
    case TypeRepr.AppliedType(tc, args)     => mentionsWildcard(tc) || args.exists(mentionsWildcard)
    case TypeRepr.TypeRef(p, _)             => mentionsWildcard(p)
    case TypeRepr.TermRef(p, _)             => mentionsWildcard(p)
    case TypeRepr.SuperType(t1, t2)         => mentionsWildcard(t1) || mentionsWildcard(t2)
    case TypeRepr.AndType(l, r)             => mentionsWildcard(l) || mentionsWildcard(r)
    case TypeRepr.OrType(l, r)              => mentionsWildcard(l) || mentionsWildcard(r)
    case TypeRepr.ByNameType(u)             => mentionsWildcard(u)
    case TypeRepr.Refinement(p, _, i)       => mentionsWildcard(p) || mentionsWildcard(i)
    case TypeRepr.MethodType(ps, r, _)      => ps.exists((_, p) => mentionsWildcard(p)) || mentionsWildcard(r)
    case TypeRepr.PolyType(_, r)            => mentionsWildcard(r)
    case TypeRepr.TypeLambda(_, b)          => mentionsWildcard(b)
    case TypeRepr.NoPrefix | TypeRepr.NoType | _: TypeRepr.ConstantType | _: TypeRepr.ThisType => false

  /** Route `Object`-keyed map members through helpers when the key/value has a bare capture
    * or the probe's type disagrees with the key's (invariance). */
  private def wildcardMapCall(name: String, recv: Term, key: Term)(using Program): Boolean =
    CollectionsTransform.WildcardMapMembers.contains(name) && wildcardMapSym(name) != SymId.None &&
      (actualOf(recv)._1 match
        case TypeRepr.AppliedType(_, List(k, v)) =>
          k.isInstanceOf[TypeRepr.TypeBounds] || v.isInstanceOf[TypeRepr.TypeBounds] ||
            (mentionsWildcard(k) && key.tpe != k)
        case TypeRepr.AppliedType(_, args) => args.exists(_.isInstanceOf[TypeRepr.TypeBounds])
        case _                             => false)

  /** True when the argument type is `Object` and the expected element type is not — routes
    * through a helper that widens the probe position at erasure. */
  private def objectProbe(arg: Term, want: Option[TypeRepr]): Boolean =
    objectSym != SymId.None && headSym(arg.tpe).contains(objectSym) &&
      want.exists(w => w != TypeRepr.NoType && !headSym(w).contains(objectSym))

  /** The third face of the same seam: a probe at a proper ancestor of the element type.
    * [[objectProbe]] is exact only at `java.lang.Object` (the top of the hierarchy); scala's
    * `Map[K,V]` is invariant in `K`, so a probe of an unrelated ancestor type also needs the
    * helper. Answered structurally by walking this run's own `extends` edges from the element type
    * up to the probe's head (CLAUDE.md §4.56) — no subtype test, and a probe the walk cannot
    * account for takes the ordinary rewrite. Not a cast: a cast would throw where java's probe
    * answers `false`; this widens the erased probe position instead. ENGINE-LIMITS K24
    */
  private def ancestorProbe(arg: Term, want: Option[TypeRepr]): Boolean =
    (headSym(arg.tpe), want.flatMap(headSym)) match
      case (Some(a), Some(e)) if a != SymId.None && e != SymId.None && a != e =>
        def parentsOf(c: Tree.ClassDef): List[SymId] = c.parents.flatMap {
          case tt: TypeTree => headSym(tt.tpe)
          case term: Term   => headSym(term.tpe)
        }
        // fuel-bounded; an exhausted walk answers false, the conservative arm here.
        def reaches(id: SymId, fuel: Int): Boolean =
          fuel > 0 && classDefsBySym.get(id).exists(c =>
            parentsOf(c).exists(s => s == a || reaches(s, fuel - 1)))
        reaches(e, 64)
      case _ => false

  private def probeMapCall(name: String, key: Term, recv: Term)(using Program): Boolean =
    CollectionsTransform.WildcardMapMembers.contains(name) && wildcardMapSym(name) != SymId.None &&
      (objectProbe(key, keyType(actualOf(recv)._1)) || ancestorProbe(key, keyType(actualOf(recv)._1)))

  private def probeSetCall(x: Term, recv: Term)(using Program): Boolean =
    objectProbe(x, elemType(actualOf(recv)._1)) || ancestorProbe(x, elemType(actualOf(recv)._1))

  private def wildcardMapSym(name: String): SymId = name match
    case "get"         => mapGetSym
    case "containsKey" => mapContainsKeySym
    case "remove"      => mapRemoveSym
    case _             => SymId.None

  /** `null` — the faithful default for a Java `Map.get` miss (Java map values are always
    * reference types, so `null` always type-checks). Ascribed to `V` by [[dflt]]. */
  private def nullOf(so: Origin): Term = Tree.Literal(Constant.NullC, TypeRepr.NoType, so)

  /** Ascribe a `getOrElse` default to the map's value type `V` (`default.asInstanceOf[V]`),
    * so inference gives `getOrElse` result type `V` instead of widening to `V | Default`
    * (which breaks e.g. `m.getOrElse(k, 0) + 1` when `V = java.lang.Integer`). Falls back
    * to the bare default when the receiver's `Map[K, V]` isn't fully applied. */
  private def dflt(default: Term, recv: Term, so: Origin): Term = valueType(recv.tpe) match
    case Some(v) => Tree.Typed(default, TypeTree(v, so), v, so)
    case None    => default

  private def valueType(t: TypeRepr): Option[TypeRepr] = t match
    case TypeRepr.AppliedType(_, List(_, v)) => Some(v)
    case _                                   => None

  private def keyType(t: TypeRepr): Option[TypeRepr] = t match
    case TypeRepr.AppliedType(_, List(k, _)) => Some(k)
    case _                                   => None

  /** a one-argument collection's ELEMENT type — [[keyType]]'s counterpart at a `Set`/`Buffer`. */
  private def elemType(t: TypeRepr): Option[TypeRepr] = t match
    case TypeRepr.AppliedType(_, List(e)) => Some(e)
    case _                                => None

  /** A key argument, with the coercion java's formal required stripped when the scala member's
    * formal is exactly what lies beneath it. Java declares `Map.get`/`remove`/`containsKey` over
    * `Object`, so the frontend widens a type-variable key with `asInstanceOf[Object]` (G14);
    * once this phase retypes the receiver to `Map[K,V]` that widening is the one thing between
    * the argument and `K` (ENGINE-LIMITS K5.6, a coercion that only becomes wrong after a
    * retyping). Structural and names no type (CLAUDE.md §4.56): stripped exactly when what it
    * wraps already has the wanted type; left alone otherwise, so a genuine mismatch fails
    * compilation naming both types (ENGINE-LIMITS M6). */
  private def keyArg(arg: Term, recv: Term): Term = (arg, keyType(recv.tpe)) match
    case (Tree.Typed(inner, _, _, _), Some(k)) if k != TypeRepr.NoType && inner.tpe == k => inner
    case _                                                                               => arg

  /** [[keyArg]]'s rule at `toArray(T[])`: the erasure coercion the frontend synthesised off
    * java's `Object[]` formal, stripped when `JavaCollections.toArray[A]` (which infers `A` from
    * the argument) wants what lies beneath the cast — else it infers `Object` where java inferred
    * the real element type. Structural and names no type (CLAUDE.md §4.56): strip only when the
    * cast's inner already has the call's own result type. */
  private def arrayArg(arg: Term, t: Tree.Apply): Term = arg match
    case Tree.Typed(inner, _, _, _) if inner.tpe != TypeRepr.NoType && inner.tpe == t.tpe => inner
    case _                                                                                => arg

  private def methodName(m: SymId)(using p: Program): String = p.symbolOf(m).map(_.name).getOrElse("")

  /** the receiver's (already-retyped, bottom-up) head type, if it is one of our scala
    * collections → its [[Kind]]. */
  private def kindAt(recv: Term)(using Program): Option[Kind] = headSym(actualOf(recv)._1).flatMap(kindOf.get)

  /** Kind of a call via an inherited JDK collection method (resolved method's owner in `typeMap`).
    * Covers `extends HashMap` etc. where `kindAt` returns `None`. Suppressed for scoped-out receivers. */
  private def inheritedKind(recv: Term, m: SymId)(using p: Program): Option[Kind] =
    if actualOf(recv)._2 then scala.None
    else p.symbolOf(m).flatMap(s => p.symbolOf(s.owner)).flatMap(o => typeMap.get(o.fullName)).map(_._2)

  /** the type a term really has — [[CollectionsTransform.scopedType]] against this run's
    * [[excluded]] set, with a flag for whether the answer came from a scope hold-back.
    * `excluded.isEmpty` always answers `(t.tpe, false)`, the pre-scope code path by arithmetic. */
  private def actualOf(t: Term)(using Program): (TypeRepr, Boolean) =
    if literalEmpty then (t.tpe, false)
    else CollectionsTransform.scopedType(t, literal).map(_ -> true).getOrElse(t.tpe -> false)

  // resolving the ambiguous-overload clash this phase's own parent made (§4.5): a scala
  // parent's remove(K) beside a kept java remove(Object) resolves scala's E051 where java did not.

  /** Per-class `MintedParents`, read from original units (before traversal moves parents),
    * transitive over program-declared parents; scoped-out/uninheritable/unmapped parents
    * excluded, standalone targets recorded in `shims`. */
  private def declaredParentKinds(p: Program): Map[SymId, MintedParents] =
    given Program = p
    def tpeOf(x: Term | TypeTree): TypeRepr = x match
      case t: TypeTree => t.tpe
      case t: Term     => t.tpe
    val classes = p.units.flatMap(StandardTraversal.allClassDefs)
    val anons   = p.units.flatMap(StandardTraversal.allAnonClasses)
    /** (what this type extends, what type parameters it declares) — for both class bodies and
      * anonymous classes (one parent, written at the `new`, no type parameters). */
    val shapeOf: Map[SymId, (List[TypeRepr], List[SymId])] =
      classes.map(cd => cd.symbol -> (cd.parents.map(tpeOf), cd.tparams.map(_.symbol))).toMap ++
        anons.map((a, tpt) => a.symbol -> (List(tpt.tpe), Nil))
    val memo    = collection.mutable.Map.empty[SymId, MintedParents]

    def resolve(id: SymId, seen: Set[SymId]): MintedParents =
      memo.getOrElse(id, {
        val out = shapeOf.get(id) match
          case _ if seen(id) || excluded.contains(id) || uninheritableSyms.contains(id) =>
            MintedParents(Set.empty, Nil, Nil, Set.empty)
          case scala.None => MintedParents(Set.empty, Nil, Nil, Set.empty)
          case Some((parents, tparams)) =>
            val heads = parents.flatMap(tp => headSym(tp).map(_ -> tp))
            val targets = heads.flatMap { (h, tp) =>
              p.symbolOf(h).flatMap(s => typeMap.get(s.fullName)).map(_ -> tp)
            }.filterNot { case ((tgt, _), _) => CollectionsTransform.UninheritableTargets(tgt) }
            val mapped = targets.collect {
              case ((tgt, k), tp) if !CollectionsTransform.standaloneTargets(tgt) => k -> firstTypeArg(tp)
            }
            // …this class's OWN mapped clauses, and its ANCESTORS' read THROUGH the clause that
            // names them. An inherited clause with the ancestor's own variables in it is the
            // an ancestor's clause must arrive substituted into THIS class's own type variables
            // or the emitted signature names types out of scope (§4.56); ParentSubst does it.
            val declared = targets.collect {
              case ((tgt, k), tp) if !CollectionsTransform.standaloneTargets(tgt) => k -> typeArgs(tp)
            } ++ heads.flatMap { (h, tp) =>
              if !shapeOf.contains(h) then Nil
              else
                val formals = shapeOf.get(h).map(_._2).getOrElse(Nil)
                val actuals = typeArgs(tp)
                val sub = if formals.sizeIs == actuals.size then formals.zip(actuals).toMap
                          else Map.empty[SymId, TypeRepr]
                resolve(h, seen + id).declared.map((k, as) => k -> as.map(ParentSubst.subst(_, sub)))
            }
            val shimParents = targets.collect {
              case ((tgt, _), tp) if CollectionsTransform.standaloneTargets(tgt) => tgt -> tp
            }
            val kindParents = targets.collect {
              case ((tgt, k), tp) if !CollectionsTransform.standaloneTargets(tgt) => (tgt, k, tp)
            }
            val above  = heads.map(_._1).filter(shapeOf.contains).map(resolve(_, seen + id))
            val scalas = targets.collect {
              case ((tgt, _), _) if !CollectionsTransform.standaloneTargets(tgt) => tgt
            }.toSet
            // the duplicate relation among this class's OWN clauses only (K28.1) — an inherited
            // kind's element type is the ancestor's, unsubstituted; reading it here would guess.
            val subsumed = shimParents.flatMap { (sh, shTpe) =>
              kindParents.collectFirst {
                case (tgt, k, kTpe) if CollectionsTransform.SubsumesShim.get(k.toString).exists(_(sh)) &&
                                       carriesElement(k, kTpe, shTpe) => sh -> tgt
              }
            }.toMap
            val shims = shimParents.map(_._1).toSet -- subsumed.keySet
            MintedParents(mapped.map(_._1).toSet ++ above.flatMap(_.kinds),
                          mapped.flatMap(_._2) ++ above.flatMap(_.probes),
                          tparams,
                          shims ++ above.flatMap(_.shims),
                          scalas ++ above.flatMap(_.targets),
                          subsumed,
                          declared)
        memo(id) = out
        out
      })

    shapeOf.keys.map(id => id -> resolve(id, Set.empty))
      .filter((_, mp) => mp.kinds.nonEmpty || mp.shims.nonEmpty || mp.subsumed.nonEmpty).toMap

  private def firstTypeArg(t: TypeRepr): Option[TypeRepr] = t match
    case TypeRepr.AppliedType(_, a :: _) => Some(a)
    case _                               => scala.None

  /** every type argument the clause writes — `Nil` for a raw one, since inventing `Object` for
    * a raw `implements Map` would be §4.6's fabricated fact. */
  private def typeArgs(t: TypeRepr): List[TypeRepr] = t match
    case TypeRepr.AppliedType(_, as) => as
    case _                           => Nil

  /** Does the kind parent really iterate what the shim parent says this class iterates?
    * `SubsumesShim` claims a `scala.collection` target answers for `JavaIterable`'s one member,
    * but only where the two clauses agree on the element (`implements Map<K,V>,
    * Iterable<Map.Entry<K,V>>` agrees; `Iterable<String>` does not). Asked in java's own types,
    * since [[declaredParentKinds]] reads the original units. Declines on a raw clause or an
    * arity this table has no row for, leaving the duplicate parent and scalac's own `E164`.
    */
  private def carriesElement(k: Kind, kindParent: TypeRepr, shimParent: TypeRepr)(using p: Program): Boolean =
    def entryOf(t: TypeRepr): Option[(TypeRepr, TypeRepr)] = t match
      case TypeRepr.AppliedType(_, a :: b :: Nil) =>
        headSym(t).flatMap(p.symbolOf).map(_.fullName)
          .filter(fqn => typeMap.get(fqn).exists((_, ek) => ek == Kind.Entry))
          .map(_ => (a, b))
      case _ => scala.None
    (k, kindParent, firstTypeArg(shimParent)) match
      case (Kind.Map, TypeRepr.AppliedType(_, kk :: vv :: Nil), Some(el)) =>
        entryOf(el).contains((kk, vv))
      case (Kind.Seq | Kind.Set | Kind.Stack, TypeRepr.AppliedType(_, e :: Nil), Some(el)) =>
        el == e
      case _ => false

  /** Classes with a retained (uninheritable) parent where every unsupported member throws first,
    * allowing a copy-projection to `Tuple2`. Read from ORIGINAL units (not mapped, to avoid
    * this phase's own refuseOnTarget licensing its own projection). // ENGINE-LIMITS K5.7 */
  private def detachedEntriesIn(p: Program): Map[SymId, String] =
    if uninheritableSyms.isEmpty then Map.empty
    else
      given Program = p
      def tpeOf(x: Term | TypeTree): TypeRepr = x match
        case t: TypeTree => t.tpe
        case t: Term     => t.tpe
      val classes = p.units.flatMap(StandardTraversal.allClassDefs)
      val byId    = classes.map(cd => cd.symbol -> cd).toMap
      def parentsOf(cd: Tree.ClassDef): List[SymId] = cd.parents.flatMap(x => headSym(tpeOf(x)))

      // the uninheritable TARGET this class's ancestry reaches, if any. A cycle takes the empty arm
      // at the repeat — the conservative direction here, since it only ever declines a projection.
      def targetOf(id: SymId, seen: Set[SymId]): Option[String] =
        if seen(id) then scala.None
        else byId.get(id).flatMap { cd =>
          parentsOf(cd).iterator.flatMap { h =>
            p.symbolOf(h).flatMap(s => typeMap.get(s.fullName)).map(_._1)
              .filter(CollectionsTransform.UninheritableTargets)
              .orElse(targetOf(h, seen + id))
          }.nextOption()
        }

      // the NEAREST declaration of one signature, self before parents, and only one with a body:
      // an abstract re-declaration says nothing about what an implementor does.
      def nearest(id: SymId, sig: CollectionsTransform.MemberSig, seen: Set[SymId]): Option[Tree.DefDef] =
        if seen(id) then scala.None
        else byId.get(id).flatMap { cd =>
          cd.body.collectFirst {
            case d: Tree.DefDef
              if d.rhs.nonEmpty && d.paramss.map(_.size).sum == sig.arity &&
                 p.symbolOf(d.symbol).exists(_.name == sig.name) => d
          }.orElse(parentsOf(cd).iterator.flatMap(nearest(_, sig, seen + id)).nextOption())
        }

      classes.flatMap { cd =>
        targetOf(cd.symbol, Set.empty).filter { tgt =>
          val sigs = CollectionsTransform.UnsupportedOnTarget.getOrElse(tgt, Set.empty)
          sigs.nonEmpty && sigs.forall(sig =>
            nearest(cd.symbol, sig, Set.empty).flatMap(_.rhs).exists(throwsFirst))
        }.map(cd.symbol -> _)
      }.toMap

  /** does this body THROW before it does anything else? The capability test [[detachedEntriesIn]]
    * rests on, and it is asked of the first statement rather than of the whole body, because that is
    * exactly the property that makes a write impossible — anything after an unconditional throw is
    * unreachable. A conditional throw answers `false`: java's own `setValue` may refuse for one
    * receiver state and write for another, and that class writes through. */
  private def throwsFirst(t: Term): Boolean = t match
    case _: Tree.Throw     => true
    case b: Tree.Block     => b.stats.headOption match
      case Some(s: Term) => throwsFirst(s)
      case Some(_)       => false
      case scala.None    => throwsFirst(b.expr)
    case c: Tree.Commented => c.stmt match
      case s: Term => throwsFirst(s)
      case _       => false
    case _ => false

  /** The value's own minted ancestry, as a coercion source — K26's `DeclaredSubtype` half.
    * `coerce` reads a source's kind out of `kindOf`, keyed on the phase's own scala target
    * symbols, so it answers `None` for a type the PROGRAM declares (`OrderedSet implements
    * java.util.Set`, emitted `extends mutable.Set`, handed to its own `retainAll` — java's
    * `Set <: Collection` edge has no image). `None` where the value already conforms. Which kind,
    * where a class carries two, is [[Kind]]'s own declaration order (deterministic), never a
    * `Set`'s iteration order. ENGINE-LIMITS K26 */
  private def mintedSourceKind(head: SymId, wants: Option[SymId]): Option[Kind] =
    parentClash.get(head).filterNot { mp =>
      (wants.contains(javaIterableSym) &&
        (mp.shims(CollectionsTransform.JavaIterableFqn) || mp.shims(CollectionsTransform.JavaCollectionFqn))) ||
      (wants.contains(javaCollectionSym) && mp.shims(CollectionsTransform.JavaCollectionFqn)) ||
      (wants.contains(javaIteratorSym)   && mp.shims(CollectionsTransform.JavaIteratorFqn))
    }.flatMap(_.kinds.toList.sortBy(_.ordinal).headOption)

  /** Rewrites a call on a retarget target — `bits.get(i)` -> `bits.apply(i)` — when the
    * receiver's head symbol is a retarget target and `(memberName, arity)` has a
    * `retargetRewrites` entry. `BoolDispatch` on a non-literal flag returns `None`, counted on
    * `collection-retarget`. */
  private def retargetRewrite(recv: Term, m: SymId, so: Origin, t: Tree.Apply)(using p: Program): Option[Term] =
    if retargetRewrites.isEmpty && retargetRewritesByDesc.isEmpty then return scala.None
    // static companion reference fallback: a static call's receiver Ident carries a freshly
    // minted external SymId not in `remap`, so resolve the source FQN from the method's owner instead.
    val recvHead0 = headSym(recv.tpe)
    recvHead0.flatMap(retargetSourceOf).orElse(
      for
        mSym   <- p.symbolOf(m)
        oSym   <- p.symbolOf(mSym.owner)
        if effectiveRetarget.contains(oSym.fullName)
      yield oSym.fullName
    ).flatMap { srcFqn =>
      val mName = methodName(m)
      val arity = t.args.size
      val desc = p.symbolOf(m).flatMap(_.descriptor)
      val rhs = recvHead0.getOrElse(SymId.None)
      // receiver-origin tracking, to disambiguate when the FQN fallback above fires
      lookupRewriteForReceiver(rhs, srcFqn, mName, arity, desc, resolveRecvOrigin(recv)).flatMap {
        case CollectionsTransform.RetargetRewrite.Rename(target) =>
          retargetRewriteSyms.get((srcFqn, target)).map { tgtSym =>
            call(recv, tgtSym, t.args, t, so)
          }
        case CollectionsTransform.RetargetRewrite.BoolDispatch(flagIndex, onTrue, onFalse) =>
          if flagIndex < 0 || flagIndex >= t.args.size then scala.None
          else
            val flagArg = t.args(flagIndex)
            val remaining = t.args.take(flagIndex) ++ t.args.drop(flagIndex + 1)
            flagArg match
              case Tree.Literal(balticporter.tir.Constant.BoolC(true), _, _) =>
                retargetRewriteSyms.get((srcFqn, onTrue)).map { tgtSym =>
                  call(recv, tgtSym, remaining, t, so)
                }
              case Tree.Literal(balticporter.tir.Constant.BoolC(false), _, _) =>
                retargetRewriteSyms.get((srcFqn, onFalse)).map { tgtSym =>
                  call(recv, tgtSym, remaining, t, so)
                }
              case _ =>
                // non-literal boolean: emit `if (flag) recv.onTrue(args) else recv.onFalse(args)`,
                // evaluate-once binding for receiver and args (CLAUDE.md §4.4/F7).
                (retargetRewriteSyms.get((srcFqn, onTrue)), retargetRewriteSyms.get((srcFqn, onFalse))) match
                  case (Some(trueSym), Some(falseSym)) =>
                    val n = { templateSeq += 1; templateSeq }
                    val recvTmp = s"bp$$bd$n"
                    val argTmps = remaining.indices.map(i => s"bp$$bd${n}a$i")
                    val recvBind = s"val $recvTmp = "
                    val argBinds = argTmps.map(t => s"; val $t = ")
                    val argList = argTmps.mkString(", ")
                    val trueCall  = s"$recvTmp.${p.symbolOf(trueSym).map(_.name).getOrElse(onTrue)}($argList)"
                    val falseCall = s"$recvTmp.${p.symbolOf(falseSym).map(_.name).getOrElse(onFalse)}($argList)"
                    val tail = s"; if (" // flag hole follows
                    val afterFlag = s") $trueCall else $falseCall }"
                    val parts = List("{ " + recvBind) ++ argBinds.toList ++ List(tail, afterFlag)
                    val holes = List(recv) ++ remaining.toList ++ List(flagArg)
                    Some(Tree.Opaque.spliced(parts, holes, t.tpe, so))
                  case _ => scala.None
        // Construct entries are handled by retargetConstruct (Tree.New path); a call reaching
        // here is a name/arity collision with an "<init>" entry — leave it for RetargetBoundaryCheck.
        case _: CollectionsTransform.RetargetRewrite.Construct => scala.None
        // ForEach entries are handled on the enclosing Tree.ForEach; a call reaching here is a
        // standalone entries()/keys()/values() with no lls image.
        case _: CollectionsTransform.RetargetRewrite.ForEach => scala.None
        // Collect entries are handled on ForEach and by the collect post-pass; None here so the
        // bottom-up traversal does not steal the iterable before retargetForEach sees the ForEach.
        case _: CollectionsTransform.RetargetRewrite.Collect => scala.None
        // for a static call, recv.tpe has no type arguments so $T0 does not resolve — borrow t.tpe
        // (the call's return type) for the type-arg extraction instead.
        case CollectionsTransform.RetargetRewrite.Template(expr) =>
          val effectiveRecv = recv.tpe match
            case TypeRepr.AppliedType(_, _) => recv // instance call: recv already has type args
            case _ => t.tpe match
              case TypeRepr.AppliedType(_, _) =>
                recv match
                  case id: Tree.Ident => id.copy(tpe = t.tpe)
                  case _              => recv
              case _ => recv
          Some(renderTemplate(expr, effectiveRecv, t.args, srcFqn, t.tpe, so))
        case CollectionsTransform.RetargetRewrite.Chain(members, hasParens, dropAllArgs) if members.nonEmpty =>
          val syms = members.flatMap(m => retargetRewriteSyms.get((srcFqn, m)))
          if syms.size != members.size then scala.None
          else
            // first member: call() when source args are non-empty or parens says (); else Select.
            // the terminal chain node carries the call's type (for TestFrameworkTransform.promote);
            // intermediates keep NoType.
            val isSingle = members.size == 1
            var cur: Term =
              if !dropAllArgs && (t.args.nonEmpty || hasParens(members.head)) then
                call(recv, syms.head, t.args, t, so)
              else if hasParens(members.head) then
                val tp = if isSingle then t.tpe else TypeRepr.NoType
                Tree.Apply(Tree.Select(recv, syms.head, TypeRepr.NoType, so), Nil, syms.head, tp, so)
              else
                val tp = if isSingle then t.tpe else TypeRepr.NoType
                Tree.Select(recv, syms.head, tp, so)
            // tail members: parameterless -> Select; in parens -> Apply with Nil args.
            syms.tail.zip(members.tail).zipWithIndex.foreach { case ((s, mName), idx) =>
              val isLast = idx == syms.tail.size - 1
              val tp = if isLast then t.tpe else TypeRepr.NoType
              if hasParens(mName) then
                cur = Tree.Apply(Tree.Select(cur, s, TypeRepr.NoType, so), Nil, s, tp, so)
              else
                cur = Tree.Select(cur, s, tp, so)
            }
            // a retarget target's iterator returns scala.collection.Iterator but the declared
            // return type is JavaIterator; the Chain node's NoType hides the mismatch from the
            // return-coercion path, so wrap with JavaIterator.from(it) here instead.
            if members.last == "iterator" && iteratorFromSym != SymId.None && javaIteratorSym != SymId.None then
              val wantsJavaIterator = headSym(t.tpe) match
                case Some(h) if h == javaIteratorSym => true
                case Some(h) if remap.contains(h) && remap(h) == javaIteratorSym => true
                case Some(h) if p.symbolOf(h).exists(s =>
                    s.fullName == "java.util.Iterator" || s.fullName == "balticporter.runtime.JavaIterator") => true
                case _ => false
              if wantsJavaIterator then
                // K36: for targets supporting indexed removal, emit a removing iterator over the
                // receiver rather than a read-only JavaIterator.from wrapping.
                val targetFqn = effectiveRetarget.get(srcFqn)
                val removingResult = targetFqn.flatMap(tgt => emitRemovingIterator(recv, tgt, t.tpe, so))
                if removingResult.isDefined then
                  cur = removingResult.get
                else
                  cur = Tree.Apply(Tree.Ident(iteratorFromSym, TypeRepr.NoType, so),
                                   List(cur), iteratorFromSym, t.tpe, so)
            // toArray() returns scala.Array[T], but the call's type head may still be the
            // retarget target (the caller expects e.g. DynamicArray) — drop .toArray and return
            // the receiver, which the preceding rewrite already built as that target.
            if members.last == "toArray" && members.size == 1 then
              val retargetTargetFqns = retarget.values.toSet
              headSym(t.tpe) match
                case Some(h) if retargetTargetToSource.contains(h) =>
                  cur = recv  // the DynamicArray already built by the preceding rewrite
                case Some(h) if remap.contains(h) && retargetTargetToSource.contains(remap(h)) =>
                  cur = recv
                case Some(h) if p.symbolOf(h).exists(s => retargetTargetFqns(s.fullName)) =>
                  cur = recv
                case _ => ()
            Some(cur)
        case _: CollectionsTransform.RetargetRewrite.Chain => scala.None
        // FieldWrite is handled in transformTerm on Tree.Assign; a call reaching here is a
        // same-(name,arity) method call — return None.
        case _: CollectionsTransform.RetargetRewrite.FieldWrite => scala.None
        // IndexedField is handled in retargetSelectRewrite; a call reaching here is standalone on the field.
        case _: CollectionsTransform.RetargetRewrite.IndexedField => scala.None
      }
    }

  /** Rewrites a construction of a retarget target — `new Source[A](args)` -> `Target.factory[A](args)`
    * — via a minted companion-factory symbol, when `retargetRewrites` has a `Construct` entry for
    * `("<init>", arity)`. The factory's `inline apply[A](…)(using MkArray[A])` needs the type
    * argument explicit (else scala infers `Any`); taken from `n.tpe`, `AnyRef` for a raw source
    * (G2), emitted faithfully for a type-parameter element (`MkArray[T]` must then be threaded or
    * the construction is counted). */
  private def retargetConstruct(t: Tree.Apply)(using p: Program): Option[Term] = t.fun match
    case n: Tree.New if retargetRewrites.nonEmpty || retargetRewritesByDesc.nonEmpty =>
      val newHead = headSym(n.tpe)
      newHead.flatMap(retargetSourceOf).flatMap { srcFqn =>
        val arity = t.args.size
        val ctorSym = p.symbolOf(t.method)
        val desc = ctorSym.flatMap(_.descriptor).orElse(ctorSym.flatMap(s => Descriptor.ofInfo(p, s)))
        // receiver-origin disambiguation at the member level
        val rhs = newHead.getOrElse(SymId.None)
        lookupRewriteForReceiver(rhs, srcFqn, "<init>", arity, desc).flatMap {
          case CollectionsTransform.RetargetRewrite.Construct(companionFqn, factoryMethod, dropTrailing, fillTypeArgs) =>
            val fqn = s"$companionFqn.$factoryMethod"
            retargetRewriteSyms.get((srcFqn, fqn)).map { factorySym =>
              val rawArgs = if dropTrailing > 0 then t.args.dropRight(dropTrailing) else t.args
              val effectiveArgs =
                if rawArgs.nonEmpty then rawArgs
                else if !fillTypeArgs then Nil
                else
                  val targs = n.tpe match
                    case TypeRepr.AppliedType(_, as) => as
                    case _ => Nil
                  targs.map { a =>
                    // wildcards (TypeBounds) become Object — an unbound wildcard is not term-position syntax
                    val safe = a match
                      case _: TypeRepr.TypeBounds => TypeRepr.TypeRef(TypeRepr.NoPrefix, objectSym)
                      case other                 => other
                    Tree.Typed(
                      Tree.Literal(balticporter.tir.Constant.NullC, safe, t.origin),
                      TypeTree(safe, t.origin), safe, t.origin)
                  }
              // extract type args from the retargeted type so the factory call carries them
              // explicitly (else scala infers Any and summonInline[MkArray[Any]] fails). A
              // type-parameter element is emitted faithfully; MkArray[T] must then be provided
              // by the enclosing scope or the error is counted on collection-retarget.
              val targsFromType: List[TypeTree] = n.tpe match
                case TypeRepr.AppliedType(_, as) =>
                  as.map {
                    case _: TypeRepr.TypeBounds =>
                      TypeTree(TypeRepr.TypeRef(TypeRepr.NoPrefix, objectSym), t.origin)
                    case a => TypeTree(a, t.origin)
                  }
                case _ => Nil
              // derive element type from a dropped supplier argument: a raw-type constructor
              // interns with no/Object type arg, but a dropped Sprite[]::new MethodRef's
              // qualifier carries the real component type — use it rather than fabricate [Object]. §4.56
              val targs: List[TypeTree] =
                // `[Object]` is what the frontend's unchecked conversion fills a RAW `new` with;
                // it is not a fact about the element and is replaced exactly as `Nil` is.
                val allObject = targsFromType.nonEmpty && targsFromType.forall(tt => headSym(tt.tpe).contains(objectSym))
                if dropTrailing > 0 && (targsFromType.isEmpty || allObject) then
                  val droppedArgs = t.args.takeRight(dropTrailing)
                  val supplierDerived = droppedArgs.collectFirst {
                    case mr: Tree.MethodRef => mr.qualifier match
                      case Left(tt) => tt.tpe match
                        case TypeRepr.AppliedType(tc, List(componentType)) if headSym(tc).flatMap(p.symbolOf).exists(_.fullName == "scala.Array") =>
                          List(TypeTree(componentType, t.origin))
                        case _ => Nil
                      case Right(term) => term.tpe match
                        case TypeRepr.AppliedType(tc, List(componentType)) if headSym(tc).flatMap(p.symbolOf).exists(_.fullName == "scala.Array") =>
                          List(TypeTree(componentType, t.origin))
                        case _ => Nil
                  }
                  supplierDerived.getOrElse(targsFromType)
                else targsFromType
              val ident = Tree.Ident(factorySym, TypeRepr.NoType, t.origin)
              val fun: Term =
                if targs.nonEmpty then Tree.TypeApply(ident, targs, TypeRepr.NoType, t.origin)
                else ident
              Tree.Apply(fun, effectiveArgs, factorySym, n.tpe, t.origin)
            }
          case CollectionsTransform.RetargetRewrite.Template(expr) =>
            Some(renderTemplate(expr, Tree.Ident(SymId.None, n.tpe, t.origin), t.args, srcFqn, n.tpe, t.origin))
          case _ => scala.None // Rename/BoolDispatch at <init> is meaningless; ignore
        }
      }
    case _ => scala.None

  private def pinnedByObject(recv: Term, m: SymId, t: Tree.Apply)(using p: Program): Option[Term] =
    /** is this parent's probe position `java.lang.Object` HERE — written so, or instantiated so? */
    def probeIsObject(probe: TypeRepr, mp: MintedParents, recvTpe: TypeRepr): Boolean =
      headSym(probe).exists { h =>
        h == objectSym || (mp.tparams.indexOf(h) match
          case -1 => false
          case i  => recvTpe match
            case TypeRepr.AppliedType(_, as) if as.sizeIs > i => headSym(as(i)).contains(objectSym)
            case _                                            => false)
      }
    for
      _    <- Option.when(t.args.sizeIs == 1)(())
      s    <- p.symbolOf(m)
      mp   <- parentClash.get(s.owner)
      sigs  = mp.kinds.flatMap(k => CollectionsTransform.ShadowedByTarget.getOrElse(k.toString, Set.empty))
      if sigs.contains(CollectionsTransform.MemberSig(s.name, 1))
      d    <- p.definitionOf(m).collect { case x: Tree.DefDef => x }
      ps    = d.paramss.flatten
      if ps.sizeIs == 1 && headSym(ps.head.tpt.tpe).contains(objectSym)
      arg   = t.args.head
      if !headSym(arg.tpe).contains(objectSym)
      if !mp.probes.exists(probeIsObject(_, mp, actualOf(recv)._1))
    yield
      val tpe = TypeRepr.TypeRef(TypeRepr.NoPrefix, objectSym)
      t.copy(args = List(Tree.Typed(arg, TypeTree(tpe, arg.origin), tpe, arg.origin)))

  private def headSym(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSym(tc)
    case _                           => None

object CollectionsTransform:

  /** Zip original and mapped body by position. Throws if lengths differ (splice integrity). */
  private[transform] def spine(orig: List[Statement], mapped: List[Statement], of: SymId)
      : List[(Statement, Statement)] =
    if orig.sizeIs != mapped.size then
      throw IllegalStateException(
        s"CollectionsTransform.restoreExcluded: the declaration spine of ${of.raw} changed length " +
          s"under the traversal (${orig.size} member(s) before, ${mapped.size} after). Held-back " +
          "members are spliced back BY POSITION, which is only sound while the mapped body is the " +
          "same kinds in the same order; zipping two different lengths would silently drop the tail " +
          "of the restore and emit the mapped form for members the scope excluded.")
    orig.zip(mapped)

  /** The declared type of a scoped-out declaration's symbol, or `None` if not scoped out.
    * Reads `Symbol.info` instead of the node's `tpe` (which `transformType` already remapped). */
  def scopedType(t: Term, scopedOut: SymId => Boolean)(using program: Program): Option[TypeRepr] =
    def declared(s: SymId, isCall: Boolean) =
      program.symbolOf(s).map(_.info).map {
        case TypeRepr.MethodType(_, r, _) if isCall                       => r
        case TypeRepr.PolyType(_, TypeRepr.MethodType(_, r, _)) if isCall => r
        case other                                                        => other
      }.filter(_ != TypeRepr.NoType)
    t match
      case Tree.Ident(s, _, _) if scopedOut(s)       => declared(s, isCall = false)
      case Tree.Select(_, s, _, _) if scopedOut(s)   => declared(s, isCall = false)
      case Tree.Apply(_, _, m, _, _) if scopedOut(m) => declared(m, isCall = true)
      case _                                         => scala.None

  /** A call-site rewrite for a retarget entry's member. `retarget` swaps the type;
    * `retargetRewrites` rewrites call sites on that retarget's target, keyed on the retarget's
    * source FQN, each entry mapping `(memberName, arity)` to one of these. */
  sealed trait RetargetRewrite
  object RetargetRewrite:
    /** Simple rename: `recv.old(args)` -> `recv.new(args)`. */
    case class Rename(target: String) extends RetargetRewrite
    /** Boolean-dispatched: inspect `args(flagIndex)` — literal `true` calls `onTrue(remaining)`,
      * literal `false` calls `onFalse(remaining)`, non-literal is refused and counted. */
    case class BoolDispatch(flagIndex: Int, onTrue: String, onFalse: String) extends RetargetRewrite
    /** Construction rewrite: `new Source(args)` to `companionFqn.factoryMethod(args)`.
      * `dropTrailing` strips trailing args; `fillTypeArgs` generates null placeholders for 0-arg case. */
    case class Construct(companionFqn: String, factoryMethod: String, dropTrailing: Int = 0, fillTypeArgs: Boolean = false) extends RetargetRewrite

    /** For-each structural rewrite: `for (E e : recv.sourceMethod())` over a retarget target
      * lowers to `recv.targetMethod(e => body)` (or a 2-arg lambda for entry iteration). `break`/
      * `continue` become `boundary` around the call/lambda body; `return` has no faithful image
      * (no explicit iterator) and is refused and counted on `collection-retarget`, as is any
      * usage of `sourceMethod()` outside a for-each header. `arity` is the lambda parameter count. */
    case class ForEach(targetMethod: String, arity: Int) extends RetargetRewrite

    /** Standalone collection: `recv.sourceMethod()` outside a for-each header collects eagerly
      * into a `DynamicArray` via `into`'s `apply()`, walked by `via` (the forEach method name);
      * inside a for-each header it lowers like `ForEach(via, 1)`. Deltas (eager copy vs java's
      * live view; no `ConcurrentModificationException`) are counted on `collection-retarget`. */
    case class Collect(via: String, into: String) extends RetargetRewrite

    /** Member chain: `recv.sourceMethod(args)` -> `recv.m1.m2…`. First member takes the original
      * arguments; later members take none. Parenless by default (F9's arity-from-callee rule);
      * `parens` opts a member into `()`. `dropArgs` drops the source call's arguments entirely
      * (parenless first member) for a source parameter the target does not need. */
    case class Chain(members: List[String], parens: Set[String] = Set.empty, dropArgs: Boolean = false) extends RetargetRewrite

    /** Field write rewrite: `recv.field = value` -> `recv.method(value)`, for a java public field
      * whose target exposes only a getter method. Keyed at `(field, 0)`, the same slot a
      * `Rename`/`Chain` occupies for the read side — the two fire on different node kinds
      * (`Assign` vs `Select`/`Apply`) and coexist at one key. */
    case class FieldWrite(field: String, method: String) extends RetargetRewrite

    /** Indexed field bypass: `recv.field[i]` -> `recv(i)`, for a java public backing-array field
      * whose target's own indexed access (`apply`/`update`) replaces it. Fires in
      * `retargetSelectRewrite` by stripping the field select so the enclosing `ArrayAccess`
      * reads straight off the receiver. */
    case class IndexedField(field: String) extends RetargetRewrite

    /** Expression template with placeholders (`$recv`, `$0`/`$1`… for arguments, `$T0`… for the
      * receiver's type arguments as text, `$Target` for the retarget target's FQN as text),
      * rendered as `Tree.Opaque.spliced`. A term placeholder used more than once is bound to a
      * temporary to avoid double side effects (CLAUDE.md §4.4/F7); type placeholders may repeat freely. */
    case class Template(expr: String) extends RetargetRewrite

  /** How to construct a retarget target's type arguments from the source type's — one element
    * per target type parameter. E.g. `IntMap<V>` (1 param) -> `ObjectMap[K,V]` (2 params) needs
    * `List(FixedType("scala.Int"), SourceArg(0))`. */
  sealed trait RetargetArg
  object RetargetArg:
    /** Carry the source type's i-th argument to this position. */
    case class SourceArg(index: Int) extends RetargetArg
    /** Insert a fixed type at this position, resolved to a minted symbol at run time. */
    case class FixedType(fqn: String) extends RetargetArg
    /** An applied type at this position (e.g. `Tuple2[K,V]` for a nested Entry type in the
      * source), whose own type arguments are each a `RetargetArg`. */
    case class Applied(fqn: String, args: List[RetargetArg]) extends RetargetArg

  /** the shape of a collection, which decides the call rewrite (a `Seq` `get` is `apply`,
    * a `Map` `get` is `getOrElse`). */
  enum Kind:
    case Seq, Map, Set
    /** a `java.util.Map.Entry`, mapped to `Tuple2` — `getKey`/`getValue` are `_1`/`_2`. */
    case Entry
    /** a `java.util.Stack`: a [[Seq]] plus five LIFO members, one of which (`peek`) is the
      * opposite end from `Deque.peek` — java's `Stack.peek()` throws on empty, `Deque.peek()`
      * returns null, so one arm cannot answer both. Falls back to [[Seq]] once the five decline. */
    case Stack
    /** a `java.util.Optional{Int,Long,Double}`, mapped to an `Option[…]` alias — not a collection,
      * but its member names differ the same way (`getAsInt`/`isPresent`/`orElse`). */
    case Opt

  val JavaIteratorFqn = s"${RuntimeArtifact.Package}.JavaIterator"
  val JavaListIteratorFqn = s"${RuntimeArtifact.Package}.JavaListIterator"
  val JavaIterableFqn = s"${RuntimeArtifact.Package}.JavaIterable"
  val JavaCollectionFqn = s"${RuntimeArtifact.Package}.JavaCollection"
  /** `java.util.Stack`'s target — a `mutable.ArrayBuffer` carrying java's own LIFO five. See the
    * [[typeMap]] entry for why the stdlib type is the wrong answer and why this is not a rewrite. */
  val JavaStackFqn = s"${RuntimeArtifact.Package}.JavaStack"
  /** `java.util.EnumMap`/`EnumSet`'s targets — a `mutable.Map`/`Set` that iterates in ORDINAL
    * order, which is the GUARANTEE no stdlib type carries (catalog `JS-C42`). */
  val JavaEnumMapFqn = s"${RuntimeArtifact.Package}.JavaEnumMap"
  val JavaEnumSetFqn = s"${RuntimeArtifact.Package}.JavaEnumSet"
  /** `java.util.Optional{Int,Long,Double}`'s targets — type ALIASES for `Option[…]`, because the
    * retype is arity-changing and the head swap is not. See the alias's own doc. */
  val JavaOptionalIntFqn    = s"${RuntimeArtifact.Package}.JavaOptionalInt"
  val JavaOptionalLongFqn   = s"${RuntimeArtifact.Package}.JavaOptionalLong"
  val JavaOptionalDoubleFqn = s"${RuntimeArtifact.Package}.JavaOptionalDouble"
  /** `java.util.Collections`' statics — a receiver-less utility class, which is why they need their
    * own home rather than a rewrite keyed on a receiver's collection kind. */
  val JavaCollectionsFqn = s"${RuntimeArtifact.Package}.JavaCollections"

  /** Targets with no `scala.collection.*` parent (standalone shims per CLAUDE.md §4.5).
    * Keyed by FQN, not package — three runtime targets DO extend scala collections. */
  val standaloneTargets: Set[String] =
    Set(JavaIterableFqn, JavaCollectionFqn, JavaIteratorFqn, JavaListIteratorFqn)

  /** `java.lang.Object` — the one formal at which every value conforms and conformance proves nothing. */
  private[balticporter] val ObjectFqn = "java.lang.Object"

  /** `java.util.stream.Stream` — formal for un-terminated collapsed stream chains. */
  private[balticporter] val StreamFqn = "java.util.stream.Stream"

  /** Java FQN to (scala FQN, Kind). In companion so checks can read it without a phase instance.
    * Must stay BELOW the `*Fqn` vals (declaration-order initialisation). */
  /** Shim FQNs. Must stay BELOW the `*Fqn` vals. */
  private[balticporter] val ShimFqns: Set[String] =
    Set(JavaIterableFqn, JavaIteratorFqn, JavaListIteratorFqn, JavaCollectionFqn)

  private[balticporter] val typeMap: Map[String, (String, Kind)] = Map(
    "java.util.List"          -> ("scala.collection.mutable.Buffer", Kind.Seq),
    "java.util.ArrayList"     -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq),
    // LinkedList: List AND Deque, used as queue. Queue extends ArrayDeque extends Buffer.
    "java.util.LinkedList"    -> ("scala.collection.mutable.Queue", Kind.Seq),
    // Vector: legacy, absent from Scala.js. Does NOT preserve `synchronized`.
    "java.util.Vector"        -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq),
    // Stack: own shim (NOT mutable.Stack — different ordering semantics, CLAUDE.md §4.4).
    "java.util.Stack"         -> (JavaStackFqn, Kind.Stack),
    // Queue/Deque/ArrayDeque: all to ArrayDeque (java/scala order these types oppositely).
    "java.util.Queue"         -> ("scala.collection.mutable.ArrayDeque", Kind.Seq),
    "java.util.Deque"         -> ("scala.collection.mutable.ArrayDeque", Kind.Seq),
    "java.util.ArrayDeque"    -> ("scala.collection.mutable.ArrayDeque", Kind.Seq),
    // Collection/AbstractCollection: both to shim (must preserve subtype relation; CLAUDE.md §4.5).
    "java.util.Collection"         -> (JavaCollectionFqn, Kind.Seq),
    "java.util.AbstractCollection" -> (JavaCollectionFqn, Kind.Seq),
    // Iterable: shim (java's iterator has remove; scala's does not).
    "java.lang.Iterable"      -> (JavaIterableFqn, Kind.Seq),
    // Iterator: shim (java has `remove`, scala does not).
    "java.util.Iterator"      -> (JavaIteratorFqn, Kind.Seq),
    // ListIterator: shim, preserves `ListIterator extends Iterator` edge. // ENGINE-LIMITS K23
    "java.util.ListIterator"  -> (JavaListIteratorFqn, Kind.Seq),
    "java.util.Map"           -> ("scala.collection.mutable.Map", Kind.Map),
    "java.util.HashMap"       -> ("scala.collection.mutable.HashMap", Kind.Map),
    "java.util.LinkedHashMap" -> ("scala.collection.mutable.LinkedHashMap", Kind.Map),
    "java.util.TreeMap"       -> ("scala.collection.mutable.TreeMap", Kind.Map),
    // ConcurrentHashMap: TrieMap (preserves subtype relation and thread-safety).
    "java.util.concurrent.ConcurrentHashMap" -> ("scala.collection.concurrent.TrieMap", Kind.Map),
    "java.util.concurrent.ConcurrentMap"     -> ("scala.collection.concurrent.Map", Kind.Map),
    // Map.Entry -> Tuple2. Both `$` and `.` separators covered.
    "java.util.Map$Entry"     -> ("scala.Tuple2", Kind.Entry),
    "java.util.Map.Entry"     -> ("scala.Tuple2", Kind.Entry),
    // EnumMap/EnumSet: shims (guarantee ordinal-order iteration, absent from JS/Native).
    "java.util.EnumMap"       -> (JavaEnumMapFqn, Kind.Map),
    "java.util.EnumSet"       -> (JavaEnumSetFqn, Kind.Set),
    // Primitive optionals: type aliases for `Option[…]` (arity-changing retype).
    "java.util.OptionalInt"    -> (JavaOptionalIntFqn, Kind.Opt),
    "java.util.OptionalLong"   -> (JavaOptionalLongFqn, Kind.Opt),
    "java.util.OptionalDouble" -> (JavaOptionalDoubleFqn, Kind.Opt),
    "java.util.Set"           -> ("scala.collection.mutable.Set", Kind.Set),
    // AbstractSet: preserves `AbstractSet <: Set`; JDK defaults supplied by the phase. // ENGINE-LIMITS K29
    "java.util.AbstractSet"   -> ("scala.collection.mutable.Set", Kind.Set),
    "java.util.HashSet"       -> ("scala.collection.mutable.HashSet", Kind.Set),
    "java.util.LinkedHashSet" -> ("scala.collection.mutable.LinkedHashSet", Kind.Set),
    "java.util.TreeSet"       -> ("scala.collection.mutable.TreeSet", Kind.Set),
  )

  /** Stable digest of [[typeMap]] for [[surfaceFingerprint]]. Sorted by java FQN, includes kind. */
  private[transform] def mappingDigest: String =
    balticporter.tir.TirPrinter.sha256(
      typeMap.toList.map((k, v) => s"$k->${v._1}:${v._2}").sorted.mkString(",")).take(16)

  /** True if `fromJava`/`toJava` can express a live view for this target. `JavaCollection`
    * has no live wrapper (copy would detach both directions). // ENGINE-LIMITS M6 */
  private[transform] def liveWrappable(target: String): Boolean = Set(
    "scala.collection.mutable.Buffer", "scala.collection.mutable.Set", "scala.collection.mutable.Map",
    JavaIteratorFqn, JavaIterableFqn,
  ).contains(target)

  /** Targets where a reified occurrence (instanceof/cast) can be translated via `Reified`.
    * Concrete targets absent (no live view can be one); those are refused and counted.
    * // ENGINE-LIMITS K18 */
  private[transform] val reifiedHelper: Map[String, String] = Map(
    "scala.collection.mutable.Buffer" -> "Buffer",
    "scala.collection.mutable.Set"    -> "Set",
    "scala.collection.mutable.Map"    -> "Map",
    JavaCollectionFqn                 -> "Collection",
    JavaIterableFqn                   -> "Iterable",
    JavaIteratorFqn                   -> "Iterator",
  )

  /** `JavaCollections.Reified`, whose members [[reifiedHelper]] names. */
  val ReifiedFqn = s"$JavaCollectionsFqn.Reified"

  /** JDK supertypes a retyped value stops being (derived from typeMap keys' class hierarchy).
    * Excludes `java.lang.Object`. // ENGINE-LIMITS K18 */
  private[transform] lazy val unmappedSupertypes: Set[String] =
    def closure(c: Class[?]): Set[String] =
      if c == null then Set.empty
      else Set(c.getName) ++ closure(c.getSuperclass) ++ c.getInterfaces.flatMap(closure).toSet
    val all = typeMap.keys.flatMap { k =>
      try closure(Class.forName(k)) catch { case _: Throwable => Set.empty[String] }
    }.toSet
    all -- typeMap.keySet - "java.lang.Object"

  /** every `JavaCollections` member the transform may emit. One list, so a new JDK utility is one
    * line here, one arm in `staticRewrite` and one method in the runtime object — and a typo is a
    * `SymId.None` that declines the rewrite rather than a dangling name in emitted code. */
  /** the three members java declares over `Object`, so a `Map<?, ?>` receiver supports them and
    * scala's `Map[K, V]` does not — see `wildcardMapCall`. */
  private[balticporter] val WildcardMapMembers: Set[String] = Set("get", "containsKey", "remove")

  /** Mapping TARGETS that cannot be a PARENT, however right they are as a use.
    *
    * `scala.Tuple2` is the one: final, no `setValue`, and a constructor taking the two components.
    * A class that IMPLEMENTS `java.util.Map.Entry` therefore cannot be emitted at that target at
    * all — see `restoreUninheritableParents`, which keeps java's parent and counts the refusal. */
  private[balticporter] val UninheritableTargets: Set[String] = Set("scala.Tuple2")

  /** `java.lang.Class` — the one reified carrier java itself guarantees. // ENGINE-LIMITS K20 */
  private[balticporter] val UniversalCarriers: Set[String] = Set("java.lang.Class")

  /** Members a retained parent declares that its mapping target cannot carry.
    * `setValue` on `Map.Entry` is the only entry. */
  private[balticporter] val UnsupportedOnTarget: Map[String, Set[MemberSig]] =
    Map("scala.Tuple2" -> Set(MemberSig("setValue", 1)))

  /** One interface member by (name, arity) — arity only, since the declaring interface is external. */
  private[balticporter] final case class MemberSig(name: String, arity: Int)

  /** Members a minted parent declares at its type parameter that shadow java's `Object`-keyed
    * members. Keyed by Kind. Used by [[pinnedByObject]] to resolve ambiguous overloads. */
  private[balticporter] val ShadowedByTarget: Map[String, Set[MemberSig]] = Map(
    // `mutable.Map`: `get`/`remove`/`contains`/`apply` all take `K`.
    Kind.Map.toString   -> Set(MemberSig("get", 1), MemberSig("remove", 1),
                               MemberSig("contains", 1), MemberSig("apply", 1)),
    // `mutable.Set`: `remove`/`contains`/`apply` all take `A`.
    Kind.Set.toString   -> Set(MemberSig("remove", 1), MemberSig("contains", 1),
                               MemberSig("apply", 1)),
    // `mutable.Buffer`: `contains`/`indexOf`/`lastIndexOf` take `A`. `remove` is deliberately NOT
    // here — scala's is `remove(Int)`, which java's `remove(Object)` does not clash with at a
    // reference argument, and where the element IS an `Integer` the phase already answers through
    // `removeValue` (§4.4's own row).
    Kind.Seq.toString   -> Set(MemberSig("contains", 1), MemberSig("indexOf", 1),
                               MemberSig("lastIndexOf", 1)),
    // a `JavaStack` IS a `mutable.ArrayBuffer`, so it inherits exactly the Seq row's three.
    Kind.Stack.toString -> Set(MemberSig("contains", 1), MemberSig("indexOf", 1),
                               MemberSig("lastIndexOf", 1)),
  )

  // ---- WHAT A MINTED PARENT ACTUALLY OVERRIDES ----
  // // ENGINE-LIMITS K28
  // Table, not derivation (far side is an unparsed scala trait). Both error directions loud
  // except abstract parent strips (silent, read against error rows via members.tsv).

  /** Members a java member at this Kind's target really overrides. Keyed by Kind. */
  private[balticporter] val OverridesTarget: Map[String, Set[ExternalSurface.Member]] = Map(
    // mutable.Map
    Kind.Map.toString -> Set(
      ExternalSurface.Member("size", 0), ExternalSurface.Member("isEmpty", 0),
      ExternalSurface.Member("clear", 0), ExternalSurface.Member("keySet", 0),
      ExternalSurface.Member("keys", 0), ExternalSurface.Member("values", 0),
      ExternalSurface.Member("iterator", 0), ExternalSurface.Member("put", 2),
    ),
    // mutable.Set
    Kind.Set.toString -> Set(
      ExternalSurface.Member("size", 0), ExternalSurface.Member("isEmpty", 0),
      ExternalSurface.Member("clear", 0), ExternalSurface.Member("add", 1),
      ExternalSurface.Member("iterator", 0),
    ),
    // mutable.Buffer — `remove` needs descriptor to distinguish by-index from by-value
    Kind.Seq.toString -> Set(
      ExternalSurface.Member("size", 0), ExternalSurface.Member("isEmpty", 0),
      ExternalSurface.Member("clear", 0), ExternalSurface.Member("iterator", 0),
      ExternalSurface.Member("remove", 1, Some(Descriptor(List(Param.Prim("int"))))),
    ),
    // a `JavaStack` IS a `mutable.ArrayBuffer`, so it inherits exactly the Seq row — the same
    // sentence `ShadowedByTarget` carries for the same pair.
    Kind.Stack.toString -> Set(
      ExternalSurface.Member("size", 0), ExternalSurface.Member("isEmpty", 0),
      ExternalSurface.Member("clear", 0), ExternalSurface.Member("iterator", 0),
      ExternalSurface.Member("remove", 1, Some(Descriptor(List(Param.Prim("int"))))),
    ),
  )

  /** Override surface of standalone shim targets (engine's own runtime traits). */
  /** Shims already subsumed by a Kind's target (e.g. `JavaIterable` subsumed by any
    * `scala.collection.Iterable`-derived target). Keyed by Kind. // ENGINE-LIMITS K28.1 */
  private[balticporter] val SubsumesShim: Map[String, Set[String]] = Map(
    // every one of these targets is a `scala.collection.Iterable`, which declares `iterator` — the
    // one member `JavaIterable` has.
    Kind.Map.toString   -> Set(JavaIterableFqn),
    Kind.Set.toString   -> Set(JavaIterableFqn),
    Kind.Seq.toString   -> Set(JavaIterableFqn),
    Kind.Stack.toString -> Set(JavaIterableFqn),
  )

  private[balticporter] val OverridesShim: Map[String, Set[ExternalSurface.Member]] = Map(
    JavaIterableFqn     -> Set(ExternalSurface.Member("iterator", 0)),
    JavaIteratorFqn     -> Set(ExternalSurface.Member("hasNext", 0), ExternalSurface.Member("next", 0),
                               ExternalSurface.Member("remove", 0)),
    JavaListIteratorFqn -> Set(ExternalSurface.Member("hasNext", 0), ExternalSurface.Member("next", 0),
                               ExternalSurface.Member("remove", 0), ExternalSurface.Member("hasPrevious", 0),
                               ExternalSurface.Member("previous", 0), ExternalSurface.Member("nextIndex", 0),
                               ExternalSurface.Member("previousIndex", 0), ExternalSurface.Member("set", 1),
                               ExternalSurface.Member("add", 1)),
    JavaCollectionFqn   -> Set(ExternalSurface.Member("iterator", 0), ExternalSurface.Member("size", 0),
                               ExternalSurface.Member("isEmpty", 0), ExternalSurface.Member("contains", 1),
                               ExternalSurface.Member("add", 1), ExternalSurface.Member("remove", 1),
                               ExternalSurface.Member("clear", 0), ExternalSurface.Member("containsAll", 1),
                               ExternalSurface.Member("addAll", 1), ExternalSurface.Member("removeAll", 1),
                               ExternalSurface.Member("retainAll", 1), ExternalSurface.Member("removeIf", 1),
                               ExternalSurface.Member("toArray", 0), ExternalSurface.Member("toArray", 1)),
  )

  /** the exception java's own contract names for an optional operation a receiver cannot perform. */
  private[balticporter] val UnsupportedOperationFqn = "java.lang.UnsupportedOperationException"

  // ---- Surface the minted parent declares that java's member cannot satisfy ----
  // Rename java member to `<name>$java`, synthesise scala-shaped bridge.
  // Both error directions loud. // ENGINE-LIMITS K28.1

  /** One bridged member: scala name/arity, with `from` = java member preference list to delegate to.
    * `from = Nil` means unconditional (no java counterpart; body from `JavaCollections`). */
  private[balticporter] final case class Bridged(scalaName: String, arity: Int,
                                                 from: List[ExternalSurface.Member],
                                                 required: Boolean = true)

  private val ObjectArg = Some(Descriptor(List(Param.Named("Object"))))
  private val IntArg    = Some(Descriptor(List(Param.Prim("int"))))

  /** keyed by [[Kind]] for [[OverridesTarget]]'s reason: several map kinds share one `MapOps`
    * trait surface. `Kind.Stack`'s target has nothing abstract; `Kind.Entry`/`Kind.Opt` are never
    * a parent ([[UninheritableTargets]]) — all three are a no-op here by arithmetic. */
  private[balticporter] val BridgedTarget: Map[String, List[Bridged]] = Map(
    Kind.Map.toString -> List(
      // MapOps.put is concrete and returns Option[V]; java's returns the value or null.
      Bridged("put",         2, List(ExternalSurface.Member("put", 2)), required = false),
      // MapOps.get is abstract and takes K; java's takes Object on purpose (K24) — a legal overload pair.
      Bridged("get",         1, List(ExternalSurface.Member("get", 1, ObjectArg))),
      // Growable.addOne / Shrinkable.subtractOne ride on the two rows above; no java member is named for them.
      Bridged("addOne",      1, List(ExternalSurface.Member("put", 2))),
      Bridged("subtractOne", 1, List(ExternalSurface.Member("remove", 1, ObjectArg))),
      Bridged("iterator",    0, List(ExternalSurface.Member("iterator", 0),
                                  ExternalSurface.Member("entrySet", 0))),
      // the two MapOps declares concretely; only a same-named java member is a problem.
      Bridged("values",      0, List(ExternalSurface.Member("values", 0)), required = false),
      Bridged("keys",        0, List(ExternalSurface.Member("keys", 0)),   required = false),
    ),
    Kind.Set.toString -> List(
      Bridged("contains",    1, List(ExternalSurface.Member("contains", 1, ObjectArg))),
      Bridged("addOne",      1, List(ExternalSurface.Member("add", 1))),
      Bridged("subtractOne", 1, List(ExternalSurface.Member("remove", 1, ObjectArg))),
      Bridged("iterator",    0, List(ExternalSurface.Member("iterator", 0))),
    ),
    Kind.Seq.toString -> List(
      Bridged("apply",       1, List(ExternalSurface.Member("get", 1, IntArg))),
      Bridged("length",      0, List(ExternalSurface.Member("size", 0))),
      Bridged("update",      2, List(ExternalSurface.Member("set", 2))),
      Bridged("insert",      2, List(ExternalSurface.Member("add", 2))),
      Bridged("prepend",     1, List(ExternalSurface.Member("add", 2))),
      Bridged("addOne",      1, List(ExternalSurface.Member("add", 1))),
      // java declares both remove(int) and remove(Object); the descriptor keeps the by-value overload out.
      Bridged("remove",      1, List(ExternalSurface.Member("remove", 1, IntArg))),
      Bridged("iterator",    0, List(ExternalSurface.Member("iterator", 0))),
      // SeqOps.contains is concrete and generic ([A1 >: A]) — must be carried or it clashes at erasure (E120).
      Bridged("contains",    1, List(ExternalSurface.Member("contains", 1, ObjectArg)), required = false),
      Bridged("indexOf",     1, List(ExternalSurface.Member("indexOf", 1, ObjectArg)),  required = false),
      // the three with no java counterpart at all. See JavaCollections' own note.
      Bridged("remove",       2, Nil),
      Bridged("insertAll",    2, Nil),
      Bridged("patchInPlace", 3, Nil),
    ),
  )

  /** Which delegates the emitted parent would CAPTURE — the set that decides the rename. Where
    * the parent declares the same (name, arity), a bridge body's call binds to the PARENT's own
    * member, making the bridge an infinite recursion; a captured delegate is renamed out of the
    * way. Deliberately absent: every delegate the target does not declare (`entrySet()`, a
    * `Buffer`'s indexed members) since renaming those would move surface for no hazard. */
  private[balticporter] val CapturedByTarget: Map[String, Set[ExternalSurface.Member]] = Map(
    // MapOps declares all six (put/values/keys concretely, get abstractly); iterator from
    // IterableOnce, remove from mutable.MapOps.
    Kind.Map.toString -> Set(
      ExternalSurface.Member("put", 2), ExternalSurface.Member("get", 1, ObjectArg),
      ExternalSurface.Member("remove", 1, ObjectArg), ExternalSurface.Member("iterator", 0),
      ExternalSurface.Member("values", 0), ExternalSurface.Member("keys", 0),
    ),
    // mutable.SetOps declares add/remove concretely over addOne/subtractOne — the recursion this breaks.
    Kind.Set.toString -> Set(
      ExternalSurface.Member("contains", 1, ObjectArg), ExternalSurface.Member("add", 1),
      ExternalSurface.Member("remove", 1, ObjectArg), ExternalSurface.Member("iterator", 0),
    ),
    // SeqOps.size is final — the rename is the only repair there is here. ENGINE-LIMITS K28
    Kind.Seq.toString -> Set(
      ExternalSurface.Member("size", 0), ExternalSurface.Member("remove", 1, IntArg),
      ExternalSurface.Member("iterator", 0), ExternalSurface.Member("contains", 1, ObjectArg),
      ExternalSurface.Member("indexOf", 1, ObjectArg),
    ),
  )

  /** the SUFFIX a captured java member is renamed with. `$java` and not `$1` or a counter: an
    * emitted name keyed on anything wider than the declaration that holds it turns `members.tsv`
    * into churn (`ENGINE-LIMITS.md` M10). */
  private[balticporter] val BridgeSuffix = "$java"

  // the JDK defaults a re-parenting removes — licensed per-member by the JDK body reaching only
  // public virtual members of the receiver (ArrayList.clone/AbstractList.subList read fields
  // instead and are refused via superPlaced). ENGINE-LIMITS K29

  /** member NAME → the [[balticporter.runtime.JavaCollections]] helper that reproduces its
    * `java.util.AbstractCollection` default. */
  private[balticporter] val VirtualJdkDefaults: Map[String, String] = Map(
    "containsAll" -> "containsAll",
    "addAll"      -> "addAll",
    "removeAll"   -> "removeAll",
    "retainAll"   -> "retainAll",
  )

  /** the body each entry above stands for, from the JDK's own source, so the licence is readable
    * at the emitted call. Rendered into the decision (§4.575). Every member named is public and
    * virtual on the receiver, which IS the argument. */
  private[balticporter] val VirtualJdkDefaultBodies: Map[String, String] = Map(
    "containsAll" -> "for (Object e : c) if (!contains(e)) return false; return true;",
    "addAll"      -> "for (E e : c) if (add(e)) modified = true; return modified;",
    "removeAll"   -> "while (it.hasNext()) if (c.contains(it.next())) { it.remove(); … }",
    "retainAll"   -> "while (it.hasNext()) if (!c.contains(it.next())) { it.remove(); … }",
  )

  val StaticHelpers: List[String] =
    List("sort", "sortNatural", "reverse", "shuffle", "swap", "asList", "asListView",
         "addAll", "insertAll", "noneMatch", "removeValue",
         "computeIfAbsent", "removeIf", "removeIfSet", "containsValue", "containsAll",
         "removeAll", "retainAll", "ensureCapacity",
         "comparingByKey", "comparingByValue", "sortedWith", "into", "mapToDouble", "intRange",
         "toArray", "emptyList", "emptyMap", "emptySet", "singletonList", "singleton", "singletonMap",
         "unmodifiableList", "unmodifiableSet", "unmodifiableMap", "subList", "putIfAbsent",
         "toSet", "toMap", "fromJava", "toJava", "toStream", "entryToPair",
         "mapGet", "mapContainsKey", "mapRemove",
         "setContains", "setRemove", "keySetView", "entrySetView",
         "optionalOrElse",
         // the three mutable.Buffer members a re-parented java.util.List owes with no java
         // counterpart (K28.1). Named buffer* to avoid an ambiguity with insertAll's own overload.
         "bufferRemoveRange", "bufferInsertAll", "bufferPatchInPlace")

  // -------------------------------------------------------------------------------------------
  // what this phase handles, as data — the answer JdkSurfaceCheck needs and match arms cannot give.
  // -------------------------------------------------------------------------------------------
  // declared rather than derived at runtime; agreement with the arms is asserted by
  // CollectionsHandledDerivationSpec, which scans this file's source text in both directions.

  /** every `owner#name` a [[staticRewrite]] arm matches, including the two collector keys the
    * `collect` arms read out of a guard — a table that omitted them would report the port's own
    * translation as its wall. */
  val handledStatics: Set[String] = Set(
    "java.util.Arrays#asList",
    "java.util.Collection#stream",
    // java's three raw constants are fields, not calls — see StaticFieldFactories/staticFieldRewrite.
    "java.util.Collections#EMPTY_LIST",
    "java.util.Collections#EMPTY_MAP",
    "java.util.Collections#EMPTY_SET",
    "java.util.Collections#emptyList",
    "java.util.Collections#emptyMap",
    "java.util.Collections#emptySet",
    "java.util.Collections#reverse",
    "java.util.Collections#shuffle",
    "java.util.Collections#singleton",
    "java.util.Collections#singletonList",
    "java.util.Collections#singletonMap",
    "java.util.Collections#sort",
    "java.util.Collections#swap",
    "java.util.Collections#unmodifiableCollection",
    "java.util.Collections#unmodifiableList",
    "java.util.Collections#unmodifiableMap",
    // java.util.EnumSet has no public constructor; it reaches its shim entirely through these six.
    "java.util.EnumSet#noneOf",
    "java.util.EnumSet#allOf",
    "java.util.EnumSet#of",
    "java.util.EnumSet#copyOf",
    "java.util.EnumSet#range",
    "java.util.EnumSet#complementOf",
    // the primitive optionals' two, which are Some/None and need no runtime member.
    "java.util.OptionalInt#of",
    "java.util.OptionalInt#empty",
    "java.util.OptionalLong#of",
    "java.util.OptionalLong#empty",
    "java.util.OptionalDouble#of",
    "java.util.OptionalDouble#empty",
    "java.util.Collections#unmodifiableSet",
    "java.util.List#stream",
    "java.util.Map$Entry#comparingByKey",
    "java.util.Map$Entry#comparingByValue",
    "java.util.Map.Entry#comparingByKey",
    "java.util.Map.Entry#comparingByValue",
    "java.util.Set#stream",
    "java.util.stream.Collectors#toCollection",
    "java.util.stream.Collectors#toList",
    "java.util.stream.Collectors#toMap",
    "java.util.stream.Collectors#toSet",
    "java.util.stream.DoubleStream#sum",
    "java.util.stream.IntStream#mapToObj",
    "java.util.stream.IntStream#range",
    "java.util.stream.IntStream#sum",
    "java.util.stream.LongStream#sum",
    "java.util.stream.Stream#allMatch",
    "java.util.stream.Stream#anyMatch",
    "java.util.stream.Stream#collect",
    "java.util.stream.Stream#filter",
    "java.util.stream.Stream#map",
    "java.util.stream.Stream#mapToDouble",
    "java.util.stream.Stream#noneMatch",
    "java.util.stream.Stream#sorted",
  )

  /** java's RAW static CONSTANTS → the typed factory java itself says they are, read by
    * `staticFieldRewrite`. Three entries, and the list is closed by JAVA rather than by this table:
    * these are the only members of `java.util.Collections` that are fields at all. */
  private[balticporter] val StaticFieldFactories: Map[String, String] = Map(
    "java.util.Collections#EMPTY_LIST" -> "emptyList",
    "java.util.Collections#EMPTY_SET"  -> "emptySet",
    "java.util.Collections#EMPTY_MAP"  -> "emptyMap",
  )

  /** collection KIND → the instance member names [[rewrite]] handles for it, with
    * `JdkSurfaceCheck.AnyKind` for an arm whose kind pattern is `_`. Read by eye and deliberately
    * not asserted: assigning a name to more kinds than its arm covers can only make the check
    * kinder, never miss a real hole. */
  val handledInstance: Map[String, Set[String]] = Map(
    balticporter.tir.JdkSurfaceCheck.AnyKind -> Set(
      // arms whose kind pattern is `_` …
      "forEach", "iterator", "getOrDefault", "add", "addAll", "putAll",
      // … and `parenless`, which is an arm of its own (`case (n, Nil, _) if parenless(n)`)
      "size", "isEmpty", "keySet", "values", "nonEmpty", "hasNext", "next",
    ),
    Kind.Seq.toString   -> Set("get", "set", "remove", "addLast", "offer", "offerLast",
                               "addFirst", "offerFirst", "poll", "pollFirst", "peek", "peekFirst", "element",
                               "toArray", "subList",
                               // SE8 defaults on List/Collection plus AbstractCollection's two bulk mutators.
                               "sort", "removeIf", "containsAll", "removeAll", "retainAll",
                               "ensureCapacity",
                               // java's bidirectional cursor; only on Kind.Seq since only List declares it. K23
                               "listIterator",
                               // spliterator's own ORDERED|SIZED|SUBSIZED characteristics, not an asJava wrapper's.
                               "spliterator"),
    Kind.Map.toString   -> Set("get", "put", "remove", "containsKey", "entrySet", "values", "putIfAbsent",
                               "computeIfAbsent", "containsValue"),
    // contains rewrites to setContains at a widened Object probe; not on Kind.Seq, where Buffer.contains differs.
    Kind.Set.toString   -> Set("remove", "contains", "toArray", "removeIf", "containsAll",
                               "removeAll", "retainAll",
                               // Set.spliterator's own default passes DISTINCT where List's passes ORDERED.
                               "spliterator"),
    Kind.Entry.toString -> Set("getKey", "getValue"),
    // a Stack's own five plus everything Kind.Seq covers — the re-entry arm at the foot of rewrite
    // answers those for a stack receiver too.
    Kind.Opt.toString   -> Set("getAsInt", "getAsLong", "getAsDouble", "orElseThrow",
                               "isPresent", "orElse", "ifPresent"),
    Kind.Stack.toString -> (Set("push", "pop", "peek", "search", "empty") ++
                            Set("get", "set", "remove", "addLast", "offer", "offerLast",
                                "addFirst", "offerFirst", "poll", "pollFirst", "peekFirst", "element",
                                "toArray", "subList")),
  )

  /** This phase's record, in the shape [[balticporter.tir.JdkSurfaceCheck]] reads. `ran` is the
    * caller's to supply: absent, an unhandled member on a mapped type is an offer (`mappable`,
    * report-only); present, the same member is a hole the phase MADE, and is a finding. */
  def jdkMapping(ran: Boolean): balticporter.tir.JdkSurfaceCheck.Mapping =
    balticporter.tir.JdkSurfaceCheck.Mapping(
      phase        = "java-collections->scala",
      ran          = ran,
      types        = typeMap.view.mapValues((target, kind) => (target, kind.toString)).toMap,
      statics      = handledStatics,
      instance     = handledInstance,
      // the shims' own members, pinned to the published runtime sources by RuntimeMembersDerivationSpec.
      shimMembers  = RuntimeArtifact.concreteMembers.view.mapValues(_.map(_._1)).toMap,
      iterableShim = Some(JavaIterableFqn),
      // `new` on a retyped type is rewritten by three paths, none a member table entry — a
      // constructor is not a member call. ENGINE-LIMITS K11
      constructors = true,
    )

  /** Runtime shim types this retyping requires (e.g. `JavaIterator` = scala `Iterator` + `remove`).
    * Live in `balticporter-runtime`. */
  val runtimeTypes: Set[String] =
    Set(JavaIteratorFqn, JavaListIteratorFqn, JavaIterableFqn, JavaCollectionFqn, JavaCollectionsFqn,
        JavaStackFqn, JavaEnumMapFqn, JavaEnumSetFqn,
        JavaOptionalIntFqn, JavaOptionalLongFqn, JavaOptionalDoubleFqn)

  /** What [[runtimeSources]] brings, for a consumer that must reason about the injected
    * supertypes it cannot parse — `JavaIterator.remove` is concrete, so a class extending both it
    * and a superclass also defining `remove` is a scala linearisation conflict `TirEmitter` needs
    * to know about. Prefer `RuntimePlan.of(phases).concreteMembers`, which derives this. */
  lazy val runtimeConcreteMembers: Map[String, Set[(String, List[Int])]] =
    RuntimeArtifact.concreteMembers.filter((fqn, _) => runtimeTypes.contains(fqn))

  /** The support sources, as text, for a port that vendors them instead of depending on the
    * artifact ([[balticporter.core.RuntimeMode.Vendored]]). Not the source of truth — the build-time
    * copy of `runtime/src/main/scala`. Prefer `RuntimePlan.of(phases, mode).writeSources(dir)`. */
  lazy val runtimeSources: Map[String, String] =
    runtimeTypes.map(fqn => fqn -> RuntimeArtifact.sourceOf(fqn)).toMap
