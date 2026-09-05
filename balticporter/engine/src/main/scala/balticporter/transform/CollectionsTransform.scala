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
    /** Indexed field rewrites keyed by (source FQN, field name) to [[RetargetRewrite.IndexedField]].
      * Separate from [[retargetRewrites]] so a field and a method of the same name can coexist
      * (e.g. ArrayMap `keys` method -> Collect AND `keys` field -> IndexedField). Scanned
      * alongside [[retargetRewrites]] for `indexedFieldSyms`. Empty = no-op.
      * // CLAUDE.md §1(b) */
    val retargetIndexedFields: Map[String, Map[String, CollectionsTransform.RetargetRewrite.IndexedField]] = Map.empty,
) extends Phase, Rewrite, RequiresRuntime, PolicySource, SurfacePolicy, MergeablePolicy, PolicyBound,
    CollectionsRetarget, CollectionsReified, CollectionsBoundary:
  def name = "java-collections->scala"

  /** Check lanes that count this retyping's residue: [[CollectionClosureCheck]] (unmapped types),
    * [[CollectionBoundaryCheck]] (JDK-side slot seams), [[RetargetBoundaryCheck]] (producer-side). */
  def accountedBy: Set[String] =
    Set(CollectionClosureCheck.Name, CollectionBoundaryCheck.Name, RetargetBoundaryCheck.Name)

  /** Resolved bindings for each declared scope entry. */
  private[transform] var boundScope: Map[String, Binding[Unit]] = Map.empty

  /** Resolved bindings for each retarget source. `Ownership.Either` — retargets are referenced, not declared. */
  private[transform] var boundRetarget: Map[String, Binding[SymId]] = Map.empty

  /** Resolved bindings for each reified carrier. `Ownership.Either` — carriers are only referenced. */
  private[transform] var boundCarriers: Map[String, Binding[SymId]] = Map.empty

  /** Resolved bindings for each reflective sink. `Ownership.Either`. */
  private[transform] var boundSinks: Map[String, Binding[SymId]] = Map.empty

  /** Resolved bindings for each family source. `Ownership.Either`. */
  private[transform] var boundFamilies: Map[String, Binding[SymId]] = Map.empty

  /** Base's + this run's own SUBSTITUTED (dropped+injected) owners, upstream FQNs. Item 2. */
  private[transform] var substitutedOwners: Set[String] = Set.empty

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
    substitutedOwners = binder.run.baseSubstitutedOwners ++ binder.run.ownSubstitutedOwners

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
      scala.Option.when(retargetIndexedFields.nonEmpty)(
        "retargetIndexedFields=" + retargetIndexedFieldsDigest),
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
      // --- retargetIndexedFields clashes ---
      val idxFieldClash = (retargetIndexedFields.keySet & o.retargetIndexedFields.keySet)
        .filter(k => retargetIndexedFields(k) != o.retargetIndexedFields(k))
      // --- carrier/sink disagreements are NOT surface and therefore NOT a refusal ---
      if retargetClash.nonEmpty || familyClash.nonEmpty || crossClash.nonEmpty ||
          scopeClash.nonEmpty || rewriteClash.nonEmpty || descRewriteClash.nonEmpty ||
          typeArgsClash.nonEmpty || idxFieldClash.nonEmpty then
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
             s"""both modules declare retarget type args for "$k" and disagree""") ++
           idxFieldClash.toList.sorted.map(k =>
             s"""both modules declare retarget indexed fields for "$k" and disagree"""))
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
        val mergedIndexedFields = retargetIndexedFields ++ o.retargetIndexedFields
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
            retargetCoercions = mergedCoercions,
            retargetIndexedFields = mergedIndexedFields),
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
    val orphanIndexedFields = retargetIndexedFields.keySet -- retarget.keySet
    require(orphanIndexedFields.isEmpty,
      s"CollectionsTransform: retargetIndexedFields key(s) ${orphanIndexedFields.mkString(", ")} have no " +
        "matching retarget entry — an indexed-field table for a type this phase does not retarget is dead code")
  }

  /** JDK + families merged type map: java FQN to (scala FQN, Kind). */
  private[transform] val typeMap: Map[String, (String, Kind)] = CollectionsTransform.typeMap ++ families

  /** the RuleScope a FAMILY ENTRY declares — `Everywhere(Set.empty)` (the pre-scope code path)
    * when the source FQN has no explicit scope. JDK entries are not scoped through this map;
    * they use the phase-level `scope` as they always have. */
  def familyScopeOf(from: String): RuleScope = familyScopes.getOrElse(from, RuleScope.everywhere)

  /** digest of the `families` table — sorted by source FQN, carrying the target and kind. The
    * scope is part of the fingerprint too (a scope difference is a surface difference), so it joins
    * the sorted string. Used only when `families.nonEmpty`. */
  private[transform] def familiesDigest: String =
    balticporter.tir.TirPrinter.sha256(
      families.toList.map((k, v) => s"$k->${v._1}:${v._2};scope=${familyScopeOf(k).fingerprint}")
        .sorted.mkString(",")).take(16)

  /** digest of the `retargetRewrites` + `retargetRewritesByDesc` tables — sorted by source FQN
    * then by key rendering. Arity-keyed entries render as `src#name/arity->Rw`, descriptor-keyed
    * as `src#name/(desc)->Rw`. The two maps are combined so one digest covers both. */
  private[transform] def retargetRewritesDigest: String =
    def renderRw(rw: CollectionsTransform.RetargetRewrite): String = rw match
      case CollectionsTransform.RetargetRewrite.Rename(t) => s"Rename($t)"
      case CollectionsTransform.RetargetRewrite.BoolDispatch(f, t, ff) => s"BoolDispatch($f,$t,$ff)"
      case CollectionsTransform.RetargetRewrite.Construct(c, m, dt, ft, ev) =>
        val base = if dt == 0 then s"Construct($c,$m)" else s"Construct($c,$m,$dt)"
        val filled = if ft then s"$base+fill" else base
        ev.fold(filled)(e => s"$filled+ev:$e")
      case CollectionsTransform.RetargetRewrite.ForEach(t, a) => s"ForEach($t,$a)"
      case CollectionsTransform.RetargetRewrite.Collect(v, i) => s"Collect($v,$i)"
      case CollectionsTransform.RetargetRewrite.Chain(ms, ps, da) =>
        val base = if ps.isEmpty then s"Chain(${ms.mkString(";")})"
        else s"Chain(${ms.mkString(";")};parens=${ps.toList.sorted.mkString(",")})"
        if da then s"$base;dropArgs" else base
      case CollectionsTransform.RetargetRewrite.FieldWrite(f, m) => s"FieldWrite($f,$m)"
      case CollectionsTransform.RetargetRewrite.DropWrite(f, rt, _) => s"DropWrite($f,$rt)"
      case CollectionsTransform.RetargetRewrite.IndexedField(f, v, vw) =>
        val extra = List(
          scala.Option.when(v != "apply")(s"via=$v"),
          scala.Option.when(vw != "update")(s"viaWrite=$vw")).flatten
        if extra.isEmpty then s"IndexedField($f)" else s"IndexedField($f,${extra.mkString(",")})"
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
  private[transform] def retargetTypeArgsDigest: String =
    def renderArg(a: CollectionsTransform.RetargetArg): String = a match
      case CollectionsTransform.RetargetArg.SourceArg(i) => s"arg($i)"
      case CollectionsTransform.RetargetArg.FixedType(fqn) => s"fixed($fqn)"
      case CollectionsTransform.RetargetArg.Applied(fqn, inner) =>
        s"applied($fqn,${inner.map(renderArg).mkString("+")})"
    balticporter.tir.TirPrinter.sha256(
      retargetTypeArgs.toList.sortBy(_._1).map { (src, args) =>
        s"$src->${args.map(renderArg).mkString(",")}"
      }.mkString(";")).take(16)

  /** Digest of [[retargetIndexedFields]]. Sorted by (source FQN, field name); renders each entry
    * the same way as [[retargetRewritesDigest]] renders an `IndexedField`. */
  private[transform] def retargetIndexedFieldsDigest: String =
    val entries = retargetIndexedFields.toList.sortBy(_._1).flatMap { (src, tbl) =>
      tbl.toList.sortBy(_._1).map { case (fld, idx) =>
        val extra = List(
          scala.Option.when(idx.via != "apply")(s"via=${idx.via}"),
          scala.Option.when(idx.viaWrite != "update")(s"viaWrite=${idx.viaWrite}")).flatten
        val rw = if extra.isEmpty then s"IndexedField($fld)" else s"IndexedField($fld,${extra.mkString(",")})"
        s"$src#$fld->$rw"
      }
    }
    balticporter.tir.TirPrinter.sha256(entries.mkString(",")).take(16)

  /** The java types this phase retypes. */
  def mappedTypes: Set[String] = typeMap.keySet

  /** Target FQN for a mapped type, or `"?"` if not mapped. */
  def targetOf(fqn: String): String = typeMap.get(fqn).map(_._1).getOrElse("?")

  /** All scala/shim types this phase produces. */
  def retypedTargets: Set[String] = typeMap.values.map(_._1).toSet

  /** scala nullary accessors that take NO parens (`def size: Int`) — a Java `size()`
    * emitted as `size()` would be an illegal application. Strip the `Apply`. */
  private val parenless = Set("size", "isEmpty", "iterator", "keySet", "values", "nonEmpty", "hasNext", "next")

  // prepared in `run`, read by the hooks.
  private[transform] var remap: Map[SymId, SymId]    = Map.empty
  /** Target SymIds of the full remap — for `transformType`'s wildcard-strip checks. */
  private[transform] var remapTargets: Set[SymId]   = Set.empty
  /** fullName to minted SymId fallback for `transformType` — covers dependent-interned SymIds. */
  private[transform] var remapByFullName: Map[String, SymId] = Map.empty
  /** source SymId to java FQN for family remap entries — for per-entry scope (D12). */
  private[transform] var familyRemapSources: Map[SymId, String] = Map.empty
  /** Declared classes by symbol — source of class type parameters. */
  private[transform] var classDefsBySym: Map[SymId, Tree.ClassDef] = Map.empty
  private[transform] var kindOf: Map[SymId, Kind]    = Map.empty // scala collection symbol → kind
  /** Per-class info about minted collection parents. `kinds` = set (a class may implement several).
    * `probes` = first type arg of each mapped parent. `shims` = standalone targets (no member clash).
    * `targets` = scala FQNs. `declared` = this class's own mapped clauses (non-transitive). */
  private[transform] final case class MintedParents(kinds: Set[Kind], probes: List[TypeRepr],
                                         tparams: List[SymId], shims: Set[String],
                                         targets: Set[String] = Set.empty,
                                         subsumed: Map[String, String] = Map.empty,
                                         declared: List[(Kind, List[TypeRepr])] = Nil)
  private[transform] var parentClash: Map[SymId, MintedParents] = Map.empty
  /** Collected `super.<JDK default>` rewrites: (enclosing class, callee, member name). */
  private[transform] val superDefaults = collection.mutable.ListBuffer.empty[(SymId, SymId, String)]
  private[transform] var opPlusEq, opMinusEq, opPlusPlusEq: SymId = SymId.None
  /** Plain arithmetic operators for compound-FieldWrite expansion: `size -= 1` -> `setSize(size - 1)`. */
  private[transform] var compoundOps: Map[String, SymId] = Map.empty
  private[transform] var updateSym, insertSym, getOrElseSym, containsSym: SymId = SymId.None
  /** `mutable.Map.put`/`remove` — return previous value (unlike `update`/`-=`). */
  private[transform] var putSym, removeSym: SymId = SymId.None
  /** Deque members — `poll`/`peek` go through `Option`/`orNull` (null-on-empty vs throw). */
  private[transform] var removeHeadOptionSym, headOptionSym, orNullSym, prependSym: SymId = SymId.None
  /** `Stack.empty()` renamed to `isEmpty` — `empty` on a `Buffer` is the companion's factory. */
  private[transform] var isEmptySym: SymId = SymId.None
  /** `Option` members for `Kind.Opt` arms. */
  private[transform] var getSym, isDefinedSym, someSym, noneSym: SymId = SymId.None
  /** `JavaEnumMap.ofType` and `JavaEnumSet` static factory symbols (minted). */
  private[transform] var enumMapOfTypeSym: SymId = SymId.None
  private[transform] var enumSetSyms: Map[String, SymId] = Map.empty
  /** this run's symbol for a scala/shim FQN, or `SymId.None` where the program never names it. */
  private[transform] var byScalaSyms: Map[String, SymId] = Map.empty
  private[transform] def byScalaSym(fqn: String): SymId = byScalaSyms.getOrElse(fqn, SymId.None)
  private[transform] def enumSetSym(n: String): SymId   = enumSetSyms.getOrElse(n, SymId.None)
  /** java 8 `Collection.forEach(Consumer)` — scala's is `foreach`, differing only in case, which
    * makes the failure read like a typo rather than a missing mapping. */
  private[transform] var foreachSym: SymId = SymId.None
  private[transform] var key1Sym, value2Sym, selfParamSym: SymId = SymId.None
  /** Bound method-ref receiver binding and lambda argument parameter symbols (max arity 2). */
  private[transform] var recvBindSym: SymId = SymId.None
  private[transform] var argParamSyms: Vector[SymId] = Vector.empty
  /** the scala side of a BRIDGED member (`ENGINE-LIMITS.md` K28.1) — the types its signature is
    * written in, and the two `asScala` views its body reaches java's answer through.
    * `iteratorMemberSym` is scala's parameterless `iterator`, for a `Map` with no java
    * `iterator()` reaching `entrySet().iterator()`. Resolved-or-minted like `unsupportedOpSym`. */
  private[transform] var optionSym, scalaIteratorSym, scalaIterableSym, iterableOnceSym: SymId = SymId.None
  private[transform] var tuple2Sym, boolSym, intSym, unitSym: SymId = SymId.None
  private[transform] var asScalaIteratorSym, asScalaIterableSym, iteratorMemberSym: SymId = SymId.None
  private[transform] var unitTpe: TypeRepr = TypeRepr.NoType

  /** `JavaIterable` + its `from` factory — see `coerce`. */
  private[transform] var javaIterableSym, iterableFromSym: SymId = SymId.None
  /** `JavaCollection` + its `from` factory — the same seam, one type up. `unmodifiableFromSym` is
    * the read-only sibling (a `Map.values()` view); `unmodifiableSym` is
    * `Collections.unmodifiableCollection`. */
  private[transform] var javaCollectionSym, collectionFromSym: SymId = SymId.None
  /** the `Kind.Set` source's factory into a `JavaCollection` slot — a DISTINCT NAME rather than an
    * overload of `from`, for the reason `JavaCollection.unmodifiableFrom` gives: an overload
    * resolves on the static type, and every candidate here is a `scala.collection.Iterable`. */
  private[transform] var collectionFromSetSym: SymId = SymId.None
  private[transform] var unmodifiableFromSym, unmodifiableSym: SymId = SymId.None
  /** each scala collection symbol → its companion's `from` factory, for `copyConstructor`. */
  private[transform] var fromSyms: Map[SymId, SymId] = Map.empty
  /** each HASHED scala collection symbol → its companion's `defaultLoadFactor`, for
    * [[capacityConstructor]]. Keyed on the phase's OWN targets, exactly as `fromSyms` is. */
  private[transform] var loadFactorSyms: Map[SymId, SymId] = Map.empty
  /** `JavaCollections`' statics, by name — see `sym`. */
  private[transform] var staticSyms: Map[String, SymId] = Map.empty
  /** the `java.util.stream` collapse — see `staticRewrite`. */
  private[transform] var asScalaBufferSym, filteredSym: SymId = SymId.None
  /** scala's own `toBuffer` — how a `Kind.Set` or `Kind.Map` stream SOURCE reaches the `Buffer`
    * every collapsed operation is declared over. See `streamSource`. */
  private[transform] var toBufferSym: SymId = SymId.None
  /** `mutable.Buffer`, so a collapsed stream can be TYPED as what it now emits. */
  private[transform] var bufferSym: SymId = SymId.None
  /** scala's own `sum` — a plain MEMBER name on the collapsed buffer, not a `JavaCollections` helper. */
  private[transform] var sumSym: SymId = SymId.None
  /** scala's own `map` — a plain member on a collapsed buffer, for the stream chain. */
  private[transform] var mapSym: SymId = SymId.None
  /** scala's own `exists`/`forall` — java's `anyMatch`/`allMatch`, which mean exactly these. */
  private[transform] var existsSym, forallSym: SymId = SymId.None
  /** `JavaIterator.from` — the `iterator` counterpart of `wrapIterableArgs`. */
  private[transform] var iteratorFromSym, javaIteratorSym: SymId = SymId.None
  /** `JavaListIterator` and its write-through cursor `JavaListIterator.over` — the `listIterator`
    * rewrite's target (`ENGINE-LIMITS.md` K23). `SymId.None` unless the program names
    * `java.util.ListIterator`, so the arm declines by arithmetic everywhere else. */
  private[transform] var javaListIteratorSym, listIteratorOverSym: SymId = SymId.None
  /** `JavaCollections.{spliterator, orderedSpliterator, distinctSpliterator}` — java's THREE own
    * defaults for `spliterator()`, one per owner it re-declares the member at
    * (`ENGINE-LIMITS.md` K23). Three symbols and not one, because the emitted call has to NAME which
    * java declaration it reproduces rather than carry a characteristics constant. */
  private[transform] var orderedSpliteratorSym, distinctSpliteratorSym: SymId = SymId.None
  /** `JavaCollections.fromJava` / `toJava` — the EXTERNAL seam's two directions. */
  private[transform] var fromJavaSym, toJavaSym, toStreamSym: SymId = SymId.None

  /** java's three `Object`-keyed map members, and the two `Object`-keyed collection ones, for a
    * receiver or an ARGUMENT at which the element type cannot be named — see [[objectProbe]]. */
  private[transform] var mapGetSym, mapContainsKeySym, mapRemoveSym: SymId = SymId.None
  private[transform] var setContainsSym, setRemoveSym: SymId               = SymId.None

  /** `JavaCollections.entryToPair` — the projection [[detachedEntries]] licenses. */
  private[transform] var entryToPairSym: SymId = SymId.None

  private[transform] var stringTpe: TypeRepr        = TypeRepr.NoType

  /** `java.lang.Object` as THIS run's symbol — the top of java's reference hierarchy, and the one
    * type an argument can carry that conforms to no scala element type at all ([[objectProbe]]). */
  private[transform] var objectSym: SymId = SymId.None

  /** is this symbol one the PROGRAM declares? Structural (`Program.owned`), never a name test
    * (§4.56), and computed once per run because the external-seam arms ask it per call. */
  private[transform] var ownedSym: SymId => Boolean = _ => true

  /** every symbol THIS PHASE minted in [[run]] — the rewrites' own targets. They are owned by
    * nothing and named by no class file, so the external-seam arms would otherwise read each of
    * them as a third party's method. */
  private[transform] var mintedSyms: Set[SymId] = Set.empty

  /** every symbol this run's [[scope]] held OUT of the rewrite. EMPTY for the default scope — and
    * for any scope whose entries matched nothing — which is what makes the no-op a no-op. */
  private[transform] var excluded: Set[SymId] = Set.empty

  /** …read back, so [[CollectionBoundaryCheck]] can classify a seam the scope created from the
    * phase's OWN record of what it held back rather than guessing from a type name (§4.56). */
  def scopedOut: Set[SymId] = excluded

  /** every member this run held back because it OVERRIDES A CLASS FILE — see
    * [[classFileOverridesIn]]. EMPTY for a program that extends no unconverted java type, which is
    * what makes this a no-op by arithmetic exactly as an unrestricted scope is. */
  private[transform] var retainedOverrides: Set[SymId] = Set.empty

  /** …plus the PARAMETER symbols of those members, because a tree that says `java.util.Collection`
    * over a symbol that says `JavaCollection` is the lie [[mapSignatures]] already refuses to
    * write for the scope. Held apart from [[classFileOverrides]] so the boundary check keys on
    * MEMBERS and nothing else. */
  private[transform] var retainedOwners: Set[SymId] = Set.empty

  /** …read back, for [[scopedOut]]'s reason and with a DIFFERENT classification: the scope's seam
    * names a manifest key, and this one names nothing a port can edit (§4.56). */
  def classFileOverrides: Set[SymId] = retainedOverrides

  /** the union — every declaration whose type this run reads LITERALLY, whichever refusal held it.
    * `isEmpty` is the pre-refusal code path by arithmetic, which both halves need. */
  private[transform] def literal(s: SymId): Boolean = excluded(s) || retainedOverrides(s) || retainedOwners(s)
  private[transform] def literalEmpty: Boolean      = excluded.isEmpty && retainedOverrides.isEmpty

  /** the declaration → the scope ENTRY that admitted it, for `Reason.Configured`'s key (§4.575:
    * the key is the manifest entry VERBATIM, because it is the string an agent edits). */
  private[transform] var admittedBy: Map[SymId, String] = Map.empty

  private[transform] var report: PolicyReport = PolicyReport.empty

  /** the setting every retarget finding is filed under — the string an agent greps for (§4.575). */
  private[transform] val RetargetSetting = "CollectionsTransform(retarget) entry"

  /** …and the same for a reified carrier. */
  private[transform] val CarrierSetting = "CollectionsTransform(reifiedCarriers) entry"

  /** …and for a reflective sink. */
  private[transform] val SinkSetting = "CollectionsTransform(reflectiveSinks) entry"

  /** …and for a collection family entry. */
  private[transform] val FamilySetting = "CollectionsTransform(families) entry"

  /** Effective retarget entries (port-declared minus any `typeMap` collision). */
  private[transform] val effectiveRetarget: Map[String, String] =
    retarget.filterNot((k, _) => typeMap.contains(k))

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
    // Scans BOTH retargetRewrites and retargetIndexedFields (the latter avoids a key collision
    // when a field and a method of the same name need different rewrite kinds).
    val rwIdxFields = retargetRewrites.flatMap { (srcFqn, tbl) =>
      tbl.collect { case ((fieldName, 0), idx: CollectionsTransform.RetargetRewrite.IndexedField) =>
        program.symbols.all.filter(s => s.fullName == srcFqn).flatMap { ownerSym =>
          program.symbols.all.filter(m => m.owner == ownerSym.id && m.name == fieldName)
            .map(m => m.id -> (srcFqn, idx))
        }
      }.flatten
    }
    val separateIdxFields = retargetIndexedFields.flatMap { (srcFqn, tbl) =>
      tbl.flatMap { (fieldName, idx) =>
        program.symbols.all.filter(s => s.fullName == srcFqn).flatMap { ownerSym =>
          program.symbols.all.filter(m => m.owner == ownerSym.id && m.name == fieldName)
            .map(m => m.id -> (srcFqn, idx))
        }
      }
    }
    indexedFieldSyms = rwIdxFields ++ separateIdxFields

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
        case CollectionsTransform.RetargetRewrite.Construct(companionFqn, factoryMethod, _, _, _) =>
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
        case CollectionsTransform.RetargetRewrite.DropWrite(_, readTarget, _) =>
          List((src, readTarget) -> mint(readTarget, s"$src#retargetRewrite:$readTarget"))
        case CollectionsTransform.RetargetRewrite.IndexedField(_, v, vw) =>
          // always mint symbols for via/viaWrite — the handler resolves them by name from
          // retargetRewriteSyms; a default-via IndexedField on a source with no Rename("apply")
          // would otherwise fall through to updateSym.
          List((src, v) -> mint(v, s"$src#retargetRewrite:$v"),
               (src, vw) -> mint(vw, s"$src#retargetRewrite:$vw"))
        case _: CollectionsTransform.RetargetRewrite.Template =>
          Nil // no minted symbol needed — the template is rendered as Opaque text
      }
    } ++ retargetIndexedFields.flatMap { (src, tbl) =>
      tbl.values.flatMap { idx =>
        List((src, idx.via) -> mint(idx.via, s"$src#retargetRewrite:${idx.via}"),
             (src, idx.viaWrite) -> mint(idx.viaWrite, s"$src#retargetRewrite:${idx.viaWrite}"))
      }
    } ++ retargetRewritesByDesc.flatMap { (src, tbl) =>
      tbl.values.flatMap {
        case CollectionsTransform.RetargetRewrite.Construct(companionFqn, factoryMethod, _, _, _) =>
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
        case CollectionsTransform.RetargetRewrite.DropWrite(_, readTarget, _) =>
          List((src, readTarget) -> mint(readTarget, s"$src#retargetRewrite:$readTarget"))
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
  private[transform] def finishRun(program: Program, symbols: SymbolTable): Program =
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
  private[transform] def inFamilyScope(sc: RuleScope, p: Program, id: SymId): Boolean =
    if sc.isUnrestricted then true
    else p.symbolOf(id) match
      case Some(s)    => sc.includes(p, s)
      case scala.None => sc match
        case RuleScope.Everywhere(_) => true
        case RuleScope.Only(_)       => false

  private[transform] def applyScope(p: Program): Unit =
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
  private[transform] def collectionValued(p: Program, info: TypeRepr): Boolean = info match
    case TypeRepr.MethodType(_, r, _)                       => isMapped(p, r)
    case TypeRepr.PolyType(_, TypeRepr.MethodType(_, r, _)) => isMapped(p, r)
    case other                                              => isMapped(p, other)

  private[transform] def isMapped(p: Program, t: TypeRepr): Boolean =
    headSym(t).flatMap(p.symbolOf)
      .exists(s => typeMap.contains(s.fullName) || effectiveRetarget.contains(s.fullName))

  /** a symbol and every owner above it, fuel-bounded. */
  private[transform] def ownerChain(p: Program, id: SymId, fuel: Int = 64): List[SymId] =
    if id == SymId.None || fuel <= 0 then Nil
    else id :: p.symbolOf(id).toList.flatMap(s => ownerChain(p, s.owner, fuel - 1))

  /** the nearest ancestor-or-self that is a MEMBER of a type rather than of a method — the unit a
    * body is rewritten in. A propagated parameter or local pulls this in with it, because half a
    * rewritten body is not a translation. */
  private[transform] def memberOf(p: Program, id: SymId): Option[SymId] =
    ownerChain(p, id).find(x => !p.symbolOf(x).flatMap(s => p.symbolOf(s.owner)).exists(o => isMethodLike(o.info)))

  private[transform] def isMethodLike(t: TypeRepr): Boolean = t match
    case _: TypeRepr.MethodType => true
    case _: TypeRepr.PolyType   => true
    case _                      => false

  /** Restores scoped-out members by splicing originals back into the mapped unit. Empty
    * `excluded` returns the unit untouched; an excluded class restores only its own positions
    * (parents, tparams) without short-circuiting its body. */
  private[transform] def restoreExcluded(orig: Tree.ClassDef, mapped: Tree.ClassDef): Tree.ClassDef =
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

  /** Drop a minted parent already subsumed by another minted parent (e.g. `JavaIterable`
    * subsumed by `mutable.Map <: Iterable`). No-op when no subsumption exists.
    * // ENGINE-LIMITS K28.1 */
  private[transform] def dropSubsumedParents(u: Tree.ClassDef)(using p: Program): Tree.ClassDef =
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

  /** `StandardTraversal.mapSymbols`, minus the symbols the scope held back, so an excluded
    * declaration's signature stays exactly as the frontend read it — matching the restored tree. */
  private[transform] def mapSignatures(tbl: SymbolTable)(using p: Program): SymbolTable =
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
  private[transform] def recordScopedOut(before: SymbolTable)(using p: Program): Unit =
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
  private[transform] var retainedAnchors: Map[SymId, String] = Map.empty

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
      // Same-arity retarget target is invariant: a wildcard arg is invalid, strip to the LOWER
      // bound when present. Upper-only is left alone HERE — a DECLARATION (a parameter, a field)
      // may keep `?`, which is valid Scala and the right image of java's own covariant wildcard;
      // stripping the upper bound is licensed only at a CAST TARGET, see [[stripCastWildcard]].
      // An UNBOUNDED wildcard (`?`) from a raw java type is bounded by Object: java's raw erasure
      // is Object (JLS 4.8), so `T[?]` must read as `T[? <: Object]` — without this, `apply`
      // returns `Any` and does not conform to `Object` slots. G2, CLAUDE.md §1(b).
      val objectRef = TypeRepr.TypeRef(TypeRepr.NoPrefix, objectSym)
      val stripped = args.map {
        case TypeRepr.TypeBounds(lo, _) if lo != TypeRepr.NoType => lo
        // only at a type THIS PHASE retargeted: a runtime shim declares `[?]` itself, and an override
        // of its member must keep that spelling or clash after erasure (simplegraphs 0 -> 4)
        case TypeRepr.TypeBounds(TypeRepr.NoType, TypeRepr.NoType) if retargetTargetToSource.contains(s) =>
          TypeRepr.TypeBounds(TypeRepr.NoType, objectRef)
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

  /** A raw-type occurrence's substituted bound still carries its OWN unresolved wildcards (a
    * self-bounded generic's erasure); stripping an upper bound to such a bound mis-narrows an
    * invariant slot, so upper-bound stripping only fires where this is `false` (subplan item 3). */
  private[transform] def hasNestedBound(t: TypeRepr): Boolean = t match
    case _: TypeRepr.TypeBounds       => true
    case TypeRepr.AppliedType(tc, as) => hasNestedBound(tc) || as.exists(hasNestedBound)
    case _                            => false

  /** `asInstanceOf[T]` needs a REIFIABLE `T`; a DECLARATION may keep `?` (valid Scala, the right
    * image of java's wildcard) but a cast target may not, so the upper-bound strip [[hasNestedBound]]
    * guards belongs here and not in [[transformType]] (subplan item 3). */
  private[transform] def stripCastWildcard(t: Tree.Typed): Tree.Typed =
    def strip(tp: TypeRepr): TypeRepr = tp match
      case TypeRepr.AppliedType(tc @ TypeRepr.TypeRef(_, s), args) if retargetTargetToSource.contains(s) =>
        TypeRepr.AppliedType(tc, args.map {
          case TypeRepr.TypeBounds(_, hi) if hi != TypeRepr.NoType && !hasNestedBound(hi) => hi
          case a => strip(a)
        })
      case TypeRepr.AppliedType(tc, args) => TypeRepr.AppliedType(strip(tc), args.map(strip))
      case other                          => other
    val stripped = strip(t.tpe)
    if stripped == t.tpe then t else t.copy(tpt = TypeTree(stripped, t.tpt.origin), tpe = stripped)

  /** WHICH type constructors' arguments this run must not move — the carriers, resolved to this
    * program's own symbols. `false` by arithmetic where the port declares none and the program names
    * no `java.lang.Class`, which is the §1(b) no-op with no code path. */
  override def preservesTypeArgsOf(tc: TypeRepr)(using Program): Boolean =
    carrierSyms.nonEmpty && headSym(tc).exists(carrierSyms.contains)

  /** does this type mention a java type THIS PHASE maps? The question `mentionsRetyped` asks in the
    * other direction — that one reads the types the phase PRODUCED, this one the keys it consumes —
    * and both are §4.56's "conclude only from what the phase itself did". */
  private[transform] def mentionsMapped(t: TypeRepr)(using Program): Boolean =
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

  private[transform] def transformValDefRhs(t: Tree.ValDef)(using Program): Tree.ValDef = t.rhs match
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
    case ty: Tree.Typed   => reifiedCast(stripCastWildcard(ty))
    case io: Tree.InstanceOf => reifiedTest(wildcardReifiedTest(io))
    case fe: Tree.ForEach =>
      retargetForEach(fe).getOrElse {
        val wt = writeThroughEntries(fe)
        ensureUnitForEachBody(wt)
      }
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

  /** A METHOD REFERENCE at a member this phase rewrites — `Map.Entry::getKey` inside a stream,
    * or `C::new` at a retarget source with a `Construct` entry (CT6 face C, CLAUDE.md §4.56).
    *
    * Lowers the reference into a lambda with the rewritten term as body.
    * Bound references (`expr::m`) bind the receiver ONCE (JLS 15.13.3).
    * Parameters left unannotated (scalac infers from expected function type). */
  private[transform] def lowerMethodRef(mr: Tree.MethodRef)(using p: Program): Term =
    if selfParamSym == SymId.None then return mr
    // the NODE's answer and not the symbol's, which is the same one derivation the emitter reads
    // (`Tree.MethodRef.referent`, F8): an external member is interned with no `Flags`, so
    // `flags.isStatic` reads `false` for every JDK static and this phase would lower one.
    mr.qualifier match
      case Left(tt) if !mr.referent.isInstanceOf[Referent.Static] =>
        kindOf.get(headSym(tt.tpe).getOrElse(SymId.None)) match
          case None    =>
            // CT6: a `C::new` reference at a retarget source with a Construct entry —
            // emit the factory lambda through `retargetConstruct` (one derivation). §4.56
            retargetConstructRef(mr, tt) match
              case Some(lam) => lam
              case scala.None => mr
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

  /** `C::new` at a retarget source — builds a synthetic `Tree.Apply(Tree.New, args)` and
    * delegates to `retargetConstruct` so the factory-call derivation is ONE path. The result
    * is wrapped in a lambda whose parameters match the constructor's arity. CT6 face C. */
  private[transform] def retargetConstructRef(mr: Tree.MethodRef, tt: TypeTree)(using p: Program): Option[Term] =
    val isCtor = p.symbolOf(mr.method).exists(_.name == "<init>")
    if !isCtor then return scala.None
    val arity = mr.referent match
      case Referent.Instance(n) => n
      case _                    => return scala.None
    if arity > argParamSyms.size then
      retargetSeam("constructor reference arity > argParamSyms pool",
        s"C::new with arity $arity", "Construct not applied — pool has ${argParamSyms.size}",
        mr.origin, SymId.None)
      return scala.None
    val o = mr.origin
    val newNode = Tree.New(tt, tt.tpe, o)
    val ps = argParamSyms.take(arity).toList
    val args = ps.map(s => Tree.Ident(s, TypeRepr.NoType, o))
    val syntheticApply = Tree.Apply(newNode, args, mr.method, tt.tpe, o)
    retargetConstruct(syntheticApply).map { body =>
      val params = ps.map(s => Tree.ValDef(s, TypeTree(TypeRepr.NoType, o), scala.None, o))
      Tree.Lambda(params, body, mr.tpe, o)
    }

  /** replace the head (type-constructor) symbol of a `TypeRef` / `AppliedType`, keeping args. */
  private[transform] def withHead(t: TypeRepr, s: SymId): TypeRepr = t match
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
  private[transform] val implicitPending = collection.mutable.Set[Origin]()
  /** Selects rewritten by the Chain/Template handler in `retargetSelectRewrite`, tracked by
    * identity of the RESULT term, so the Apply handler can strip an outer `()` a chain-rewritten
    * parenless target should not have. */
  private[transform] val selectChainRewritten = java.util.Collections.newSetFromMap(
    new java.util.IdentityHashMap[Term, java.lang.Boolean]())

  /** [[inheritedKind]] with no receiver to read. The scope suppression `inheritedKind` applies is
    * about the RECEIVER's declaration, and there is no receiver here; the enclosing class's own
    * [[kindAt]] — which does go through `actualOf` — is what stands in for it at the claim. */
  private[transform] def implicitInheritedKind(m: SymId)(using p: Program): Option[Kind] =
    if mintedSyms.contains(m) then scala.None
    else p.symbolOf(m).flatMap(s => p.symbolOf(s.owner)).flatMap(o => typeMap.get(o.fullName)).map(_._2)

  private[transform] def noteImplicitReceiver(t: Tree.Apply)(using Program): Unit = t.fun match
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

  /** Does `needle` occur anywhere inside `hay`, as a whole type? Structural equality via
    * [[StandardTraversal.mapType]] (§3). */
  private[transform] def occursIn(needle: TypeRepr, hay: TypeRepr)(using Program): Boolean =
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

  /** Does this callee name a member one of the phase's own static arms covers (§4.56, `MemberKey`
    * form)? A call still standing at such a name is one the phase DECLINED to rewrite — its value
    * is whatever java's was, whatever the node's retyped `tpe` now says. */
  private[transform] def handledStatic(m: SymId)(using p: Program): Boolean =
    memberKeyOf(m).exists(CollectionsTransform.handledStatics.contains)

  /** Java's collection copy constructor — `new ArrayList<>(c)`, `new HashSet<>(c)`, etc. A
    * capacity hint (`new ArrayList<>(10)`) maps correctly by accident; a COPY needs
    * `<Companion>.from(c)` instead, gated on the argument being a collection. */

  /** Java's class-token constructor — `new EnumMap<K, V>(K.class)` — routed to a named factory
    * since the shim orders by `ordinal` and the token has nothing to size. Ordered before
    * [[copyConstructor]]; disjoint anyway (takes a `classOf[…]` literal). */
  private[transform] def tokenConstructor(t: Tree.Apply)(using Program): Option[Term] = t.fun match
    case n: Tree.New if enumMapOfTypeSym != SymId.None =>
      val isToken = t.args match
        case List(Tree.Literal(Constant.ClassOfC(_), _, _)) => true
        case _                                              => false
      for tgt <- headSym(n.tpe) if isToken && tgt == byScalaSym(CollectionsTransform.JavaEnumMapFqn)
      yield Tree.Apply(Tree.Ident(enumMapOfTypeSym, TypeRepr.NoType, t.origin), t.args,
                       enumMapOfTypeSym, n.tpe, t.origin)
    case _ => scala.None

  private[transform] def copyConstructor(t: Tree.Apply)(using Program): Option[Term] = t.fun match
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
  private[transform] def capacityConstructor(t: Tree.Apply)(using Program): Option[Term] = t.fun match
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
  private[transform] def asListArgs(args: List[Term])(using p: Program): AsList =
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
  private[transform] def elementArg(t: Tree.Apply)(using p: Program): Option[TypeTree] =
    soleTypeArg(t.tpe).collect {
      case a if a != TypeRepr.NoType && !a.isInstanceOf[TypeRepr.TypeBounds] && !namesUnresolved(a) =>
        TypeTree(a, t.origin)
    }

  /** Does this type mention an inference marker (G2) or wildcard (K10) anywhere inside it —
    * either of which cannot be written as an explicit type argument? Read through
    * `Symbol.isUnresolvedTypeVar`, never a local spelling. */
  private[transform] def namesUnresolved(t: TypeRepr)(using p: Program): Boolean = t match
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
  private[transform] def asListViewArg(arg: Term, call: Tree.Apply): Term = arg match
    case Tree.Typed(inner, _, _, _) =>
      val wanted = soleTypeArg(call.tpe)
      val have   = soleTypeArg(inner.tpe)
      if wanted.isDefined && wanted == have then inner else arg
    case _ => arg

  /** The single type argument of an applied type, or `None` — the one shape [[asListViewArg]]
    * compares. Not a general "element type of": `Buffer[A]`/`Array[A]` both have exactly one. */
  private[transform] def soleTypeArg(t: TypeRepr): Option[TypeRepr] = t match
    case TypeRepr.AppliedType(_, List(a)) if a != TypeRepr.NoType => Some(a)
    case _                                                        => scala.None

  /** Which of `Arrays.asList`'s two java shapes a call site is — see [[asListArgs]]. Not an
    * `Option`: the aliasing form is a DIFFERENT helper, not "no arguments to pass". */
  private[transform] enum AsList:
    case Elements(args: List[Term])
    case Aliased(array: Term)

  /** A `JavaCollections` static by name. Minted eagerly in `run`, before the traversal consults
    * it. An unlisted name yields `SymId.None`, treated as "not available", never a dangling ref. */
  private[transform] def sym(name: String): SymId = staticSyms.getOrElse(name, SymId.None)

  /** the method a collector expression calls, so `collect`'s argument can be identified. */
  private[transform] def collectorOf(t: Term): SymId = t match
    case a: Tree.Apply => a.method
    case _             => SymId.None

  /** Convert a `stream()` receiver to the scala sequence the collapse consumes.
    * Shims use `asScalaBuffer`; `Set`/`Map` sources use `.toBuffer`. */
  private[transform] def streamSource(r: Term, m: SymId)(using p: Program): Term =
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
  private[transform] def scalaView(t: Term): Term =
    if asScalaBufferSym != SymId.None && headSym(t.tpe).exists(shimSyms.contains)
    then Tree.Select(t, asScalaBufferSym, asBuffer(t.tpe), t.origin)
    else t

  /** True when the receiver is already collapsed from a `Stream` to a scala collection. Shims
    * excluded (collapse consumes them, not produces). Keyed on `kindOf`. */
  private[transform] def collapsed(recv: Option[Term]): Boolean =
    recv.flatMap(r => headSym(r.tpe)).exists(s => kindOf.get(s).contains(Kind.Seq) && !shimSyms.contains(s))

  /** The same type with `Buffer` as its head — what `asScalaBuffer` on a `JavaCollection[E]`
    * returns. Falls back to a bare `Buffer` when the input has no head (`NoType`, common on an
    * external call's node) — the head is the only part any caller reads. */
  private[transform] def asBuffer(t: TypeRepr): TypeRepr =
    if bufferSym == SymId.None then t
    else
      val h = withHead(t, bufferSym)
      if headSym(h).contains(bufferSym) then h else TypeRepr.TypeRef(TypeRepr.NoPrefix, bufferSym)

  /** Could this value be a representation this phase introduced? A type it retyped, one of its
    * own shims, or `java.lang.Object` (says nothing). Read from the phase's own tables (§4.56). */
  private[transform] def mayBeRetypedValue(a: Term)(using p: Program): Boolean =
    headSym(a.tpe).exists(s => kindOf.contains(s) || shimSyms.contains(s) ||
      p.symbolOf(s).exists(_.fullName == CollectionsTransform.ObjectFqn))

  /** `java.lang.Object` as this program spells it — the bridge's result type. Falls back to the
    * argument's own type where the program never names `Object`. */
  private[transform] def objectTpe(a: Term)(using p: Program): TypeRepr =
    p.symbols.all.find(_.fullName == CollectionsTransform.ObjectFqn)
      .map(s => TypeRepr.TypeRef(TypeRepr.NoPrefix, s.id)).getOrElse(a.tpe)

  /** A `return` is a shim-typed slot exactly as a formal or `val` is — the declared return type
    * is the expected type of every `return` in the body. DELIBERATELY BOUNDED: a `return` inside a
    * lambda/anon/local class returns from THAT, so an unhandled kind MISSES a coercion (a loud
    * compile error) rather than wrongly coercing. Tail expression is not a return value here —
    * every java method exits through `Tree.Return`. */
  override def transformDefDef(t: Tree.DefDef)(using Program): Tree.DefDef =
    citeIfReified(t.symbol)
    val coerced = t.copy(rhs = t.rhs.map(coerceReturns(t.returnTpt.tpe, _)))
    if retFeReturnApplies.isEmpty then coerced
    else coerced.copy(rhs = coerced.rhs.map(wrapReturnBoundary(t.returnTpt.tpe, _)))

  /** the runtime shims, as scala symbols — a source already typed as one is never re-wrapped. */
  private[transform] def shimSyms: Set[SymId] =
    Set(javaIterableSym, javaIteratorSym, javaListIteratorSym, javaCollectionSym)

  /** the shims as FQNs, so a `typeMap` target can be recognised as one — [[shimSyms]] answers
    * only for a program that names the shim's java original, interned on first reference. */
  private[transform] def shimFqns: Set[String] = CollectionsTransform.ShimFqns

  /** True when the type is a shim or inherits from one (transitively, fuel-bounded).
    * Suppresses arity rewrites on receivers that carry java's member shape. */
  private[transform] def shimShaped(t: TypeRepr)(using p: Program): Boolean =
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
      // one, so the view is the map itself (Tuple2 loses setValue write-through). list.iterator()
      // yields a scala.collection.Iterator, but a java.util.Iterator-derived declaration wants the
      // removal-capable shim; decided on provenance.
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
    * class nor any subclass IN THIS PROGRAM declares `m` (the port cannot answer beyond its own
    * scope; the alternative is a refused rewrite that does not compile). Both walks read class
    * definitions, not the symbol table; the subclass walk is transitive. */
  private[transform] def superIsThis(recv: Term, member: String)(using p: Program): Boolean = recv match
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
  private[transform] def superLostItsDefault(recv: Term, member: String)(using p: Program): Boolean = recv match
    case Tree.Super(cls, _, _) if cls != SymId.None =>
      parentClash.get(cls).exists(_.kinds.nonEmpty) && !ancestorDeclares(cls, member)
    case _ => false

  /** does any class this PROGRAM declares, strictly ABOVE `cls`, declare `member`? */
  private[transform] def ancestorDeclares(cls: SymId, member: String)(using p: Program): Boolean =
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
  private[transform] def thisOf(recv: Term): Term = recv match
    case Tree.Super(cls, tpe, so) => Tree.This(cls, tpe, so)
    case other                    => other

  /** Does every `super` in this rewritten term stand where scala allows one — a member
    * selection's qualifier, nowhere else? Java has no such restriction, so a rewrite can put
    * `super` where scala forbids it (`E040`). Asked of the RESULT, not the arm, so a later rewrite
    * is covered by construction. `ENGINE-LIMITS.md` M6. */
  private[transform] def superPlaced(t: Term)(using Program): Boolean =
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
  private[transform] def stripLegalSuper(t: Term)(using Program): Term =
    val strip = new Phase:
      def name = "super-strip"
      override def transformTerm(x: Term)(using Program): Term = x match
        case s @ Tree.Select(sup: Tree.Super, m, tp, o) => Tree.Select(Tree.This(SymId.None, sup.tpe, sup.origin), m, tp, o)
        case _                                          => x
    StandardTraversal.mapTerm(strip, t)

  /** did java resolve `Collection.remove(Object)` (by value, returning `boolean`) rather than
    * `List.remove(int)` (by index)? A call whose result type the frontend could not record
    * answers `false` and falls back to scala's index removal. */
  private[transform] def removesByValue(t: Tree.Apply)(using p: Program): Boolean =
    headSym(t.tpe).flatMap(p.symbolOf).exists(_.fullName == "scala.Boolean")

  /** `recv.op(args)` where `op` is tagged an operator → emitted infix (`recv op arg`). */
  private[transform] def infix(recv: Term, op: SymId, args: List[Term], t: Tree.Apply, so: Origin): Term =
    call(recv, op, args, t, so)

  /** `recv.member(args)`. */
  private[transform] def call(recv: Term, member: SymId, args: List[Term], t: Tree.Apply, so: Origin): Term =
    Tree.Apply(Tree.Select(recv, member, TypeRepr.NoType, so), args, member, t.tpe, t.origin)

  /** `JavaCollections.member(args)` — a runtime helper, typed as what the java call it replaces
    * was recorded at (ENGINE-LIMITS K6's first rule: a node describes what it emits). */
  private[transform] def staticCall(member: SymId, args: List[Term], t: Tree.Apply, so: Origin): Term =
    Tree.Apply(Tree.Ident(member, TypeRepr.NoType, so), args, member, t.tpe, t.origin)

  /** `null` — the faithful default for a Java `Map.get` miss (Java map values are always
    * reference types, so `null` always type-checks). Ascribed to `V` by [[dflt]]. */
  private[transform] def nullOf(so: Origin): Term = Tree.Literal(Constant.NullC, TypeRepr.NoType, so)

  /** Ascribe a `getOrElse` default to the map's value type `V` (`default.asInstanceOf[V]`),
    * so inference gives `getOrElse` result type `V` instead of widening to `V | Default`
    * (which breaks e.g. `m.getOrElse(k, 0) + 1` when `V = java.lang.Integer`). Falls back
    * to the bare default when the receiver's `Map[K, V]` isn't fully applied. */
  private[transform] def dflt(default: Term, recv: Term, so: Origin): Term = valueType(recv.tpe) match
    case Some(v) => Tree.Typed(default, TypeTree(v, so), v, so)
    case None    => default

  private[transform] def valueType(t: TypeRepr): Option[TypeRepr] = t match
    case TypeRepr.AppliedType(_, List(_, v)) => Some(v)
    case _                                   => None

  private[transform] def keyType(t: TypeRepr): Option[TypeRepr] = t match
    case TypeRepr.AppliedType(_, List(k, _)) => Some(k)
    case _                                   => None

  /** a one-argument collection's ELEMENT type — [[keyType]]'s counterpart at a `Set`/`Buffer`. */
  private[transform] def elemType(t: TypeRepr): Option[TypeRepr] = t match
    case TypeRepr.AppliedType(_, List(e)) => Some(e)
    case _                                => None

  /** A key argument, with the coercion java's formal required stripped when the scala member's
    * formal is exactly what lies beneath it. Java's `Map.get`/`remove`/`containsKey` widen a
    * type-variable key with `asInstanceOf[Object]` (G14); after this phase retypes the receiver,
    * that widening is all that stands between the argument and `K` (`ENGINE-LIMITS.md` K5.6).
    * Structural, names no type — stripped only when what it wraps already has the wanted type. */
  private[transform] def keyArg(arg: Term, recv: Term): Term = (arg, keyType(recv.tpe)) match
    case (Tree.Typed(inner, _, _, _), Some(k)) if k != TypeRepr.NoType && inner.tpe == k => inner
    case _                                                                               => arg

  /** [[keyArg]]'s rule at `toArray(T[])`: the erasure coercion the frontend synthesised off
    * java's `Object[]` formal, stripped when `JavaCollections.toArray[A]` (which infers `A` from
    * the argument) wants what lies beneath the cast — else it infers `Object` where java inferred
    * the real element type. Structural and names no type (CLAUDE.md §4.56): strip only when the
    * cast's inner already has the call's own result type. */
  private[transform] def arrayArg(arg: Term, t: Tree.Apply): Term = arg match
    case Tree.Typed(inner, _, _, _) if inner.tpe != TypeRepr.NoType && inner.tpe == t.tpe => inner
    case _                                                                                => arg

  private[transform] def methodName(m: SymId)(using p: Program): String = p.symbolOf(m).map(_.name).getOrElse("")

  /** the receiver's (already-retyped, bottom-up) head type, if it is one of our scala
    * collections → its [[Kind]]. */
  private[transform] def kindAt(recv: Term)(using Program): Option[Kind] = headSym(actualOf(recv)._1).flatMap(kindOf.get)

  /** Kind of a call via an inherited JDK collection method (resolved method's owner in `typeMap`).
    * Covers `extends HashMap` etc. where `kindAt` returns `None`. Suppressed for scoped-out receivers. */
  private[transform] def inheritedKind(recv: Term, m: SymId)(using p: Program): Option[Kind] =
    if actualOf(recv)._2 then scala.None
    else p.symbolOf(m).flatMap(s => p.symbolOf(s.owner)).flatMap(o => typeMap.get(o.fullName)).map(_._2)

  /** the type a term really has — [[CollectionsTransform.scopedType]] against this run's
    * [[excluded]] set, with a flag for whether the answer came from a scope hold-back.
    * `excluded.isEmpty` always answers `(t.tpe, false)`, the pre-scope code path by arithmetic. */
  private[transform] def actualOf(t: Term)(using Program): (TypeRepr, Boolean) =
    if literalEmpty then (t.tpe, false)
    else CollectionsTransform.scopedType(t, literal).map(_ -> true).getOrElse(t.tpe -> false)

  // resolving the ambiguous-overload clash this phase's own parent made (§4.5): a scala
  // parent's remove(K) beside a kept java remove(Object) resolves scala's E051 where java did not.

  private[transform] def headSym(t: TypeRepr): Option[SymId] = t match
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
    case class Construct(companionFqn: String, factoryMethod: String, dropTrailing: Int = 0, fillTypeArgs: Boolean = false,
        /** a `given` clause (`Type = expr`, `$T0` = the element type), in scope when the element is a type variable */
        typeVarEvidence: Option[String] = None) extends RetargetRewrite

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

    /** Dropped field write: `recv.field = value` is elided (the target's field is immutable or the
      * write is a no-op), and `recv.field` on the read side maps to `readTarget`. A
      * `Decision.Kind.DroppedFieldWrite` is recorded at each dropped site. K36. */
    case class DropWrite(field: String, readTarget: String, why: String) extends RetargetRewrite

    /** Indexed field bypass: `recv.field[i]` -> `recv.via(i)`, `field[i] = v` -> `recv.viaWrite(i, v)`.
      * `via`/`viaWrite` default to `apply`/`update`; non-default enters the fingerprint.
      * Fires in `retargetSelectRewrite` by stripping the field select.
      * // CLAUDE.md §1(b) */
    case class IndexedField(field: String, via: String = "apply", viaWrite: String = "update") extends RetargetRewrite

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
