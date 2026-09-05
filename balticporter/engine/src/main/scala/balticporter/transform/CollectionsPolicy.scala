package balticporter.transform

import balticporter.core.{MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource, RequiresRuntime, RuntimeArtifact, SurfacePolicy}
import balticporter.tir.*

/** Policy shape, surface fingerprint, merge, scope and validation split out of CollectionsTransform (context diet S3). */
private[transform] trait CollectionsPolicy:
  self: CollectionsTransform =>
  import CollectionsTransform.{JavaCollectionFqn, JavaCollectionsFqn, JavaIterableFqn, JavaIteratorFqn, Kind}

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
                self.record(Decision(
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

