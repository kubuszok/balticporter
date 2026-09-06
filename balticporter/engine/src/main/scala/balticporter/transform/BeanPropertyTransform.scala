package balticporter.transform

import balticporter.core.{MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*

/** Turns a configured JavaBean accessor pair (`getX`/`setX`) into a Scala property (`x`/`x_=`) and
  * rewrites every call site; `pairs` is an explicit include list keyed by upstream FQN (§4.56), not
  * a detected pattern. Applied whole or refused (unparsed parent, fluent/set-only setter,
  * value-position reference, static accessor, name collision) — never invented. Default target keeps
  * bodies verbatim; `var`/`val` collapse is opt-in per entry. CLAUDE.md §1(b), DESIGN.md §8.5. */
final class BeanPropertyTransform(pairs: Map[String, String] = Map.empty,
                                  targets: Map[String, BeanPropertyTransform.Target] = Map.empty,
                                  exposedFields: RuleScope = RuleScope.Only(Set.empty),
                                  scope: RuleScope = RuleScope.Only(Set.empty))
    extends Phase, PolicySource, SurfacePolicy, MergeablePolicy, PolicyBound, IdiomPhase, Rewrite:

  def name: String = "bean-properties"

  override def runsBefore: Set[String] = Set("java-collections->scala", "package-rename")

  /** Idiom kinds this run reports: `BeanCollapse` always, plus `BeanDetect` only when the
    * auto-detection scope is active — an inactive kind must not report a misleading zero. */
  def idiomKinds: Set[IdiomKind] =
    if scope == RuleScope.Only(Set.empty) then Set(IdiomKind.BeanCollapse)
    else Set(IdiomKind.BeanCollapse, IdiomKind.BeanDetect)

  /** Check lane that counts what a collapse moved and did not rewrite (not `policy`, which counts
    * never-fired keys). ENGINE-LIMITS K2.5. */
  def accountedBy: Set[String] = Set(IdiomCheck.Residue)

  /** Threads `PublicFieldAccessorTransform`'s scope in, so a field it exposed reflectively is not
    * also collapsed away by this phase (K21 face 2). Returns a copy. */
  def withExposed(exposed: RuleScope): BeanPropertyTransform =
    new BeanPropertyTransform(pairs, targets, exposed, scope)

  /** The shape an entry asked for; `DefPair` where it said nothing (DESIGN.md §8.5). */
  def targetOf(key: String): BeanPropertyTransform.Target =
    targets.getOrElse(key, BeanPropertyTransform.Target.DefPair)

  /** The pairs, sorted and rendered with their target — two modules that agree must compare
    * equal (§1.5); the target is rendered unconditionally or `SurfaceMissing` cannot see two
    * configs naming the same accessors at different shapes (ENGINE-LIMITS CT9). */
  def surfaceFingerprint: String =
    val pairsFp = pairs.toList.sorted.map((k, v) => s"$k=$v>${targetOf(k).config}").mkString(",")
    // §1(b): omit the scope segment at the default (`Only(Set.empty)`) — an empty parameter
    // contributes no segment to the fingerprint, so the mechanism's arrival is flat.
    val isDefault = scope == RuleScope.Only(Set.empty)
    if isDefault then pairsFp
    else
      val scopeFp = scope.fingerprint
      if pairsFp.isEmpty then s"detect=$scopeFp"
      else s"$pairsFp;detect=$scopeFp"

  /** The port's own pairs table, verbatim — used only to compare a derived collapse shape against
    * the base's published one (`PortRun.collapseDivergence`); the verdict itself comes from the
    * idiom log (ENGINE-LIMITS K2.5). */
  def pairsTable: Map[String, String] = pairs

  /** The auto-detection scope, exposed for the merge contract and the fingerprint. */
  def detectScope: RuleScope = scope

  /** Every type this instance's policy is keyed on, including scope entries — an auto-detected
    * pair's property name is emitted surface too (§1.5). */
  def subjects: Set[String] = pairs.keySet.map(MergeablePolicy.subjectOf) ++ scope.entries

  /** The merge contract (DESIGN.md §8.13): independent keys union; same key with a different
    * accessor value or target is refused. An absent target (`DefPair`) yields to an explicit one. */
  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case b: BeanPropertyTransform =>
      val myPairs    = pairsTable
      val theirPairs = b.pairsTable
      val conflicts  = (myPairs.keySet intersect theirPairs.keySet).filter(k => myPairs(k) != theirPairs(k))
      if conflicts.nonEmpty then
        Left(s"`bean-properties` has ${conflicts.size} key(s) with different accessor spellings: " +
          conflicts.toList.sorted.take(3).map(k =>
            s"`$k` (${myPairs(k)} vs ${theirPairs(k)})").mkString("; "))
      else
        val allKeys = myPairs.keySet ++ theirPairs.keySet
        val targetConflicts = allKeys.filter { k =>
          val mine   = targetOf(k)
          val theirs = b.targetOf(k)
          mine != theirs && mine != BeanPropertyTransform.Target.DefPair &&
            theirs != BeanPropertyTransform.Target.DefPair
        }
        if targetConflicts.nonEmpty then
          Left(s"`bean-properties` has ${targetConflicts.size} key(s) with different targets: " +
            targetConflicts.toList.sorted.take(3).map(k =>
              s"`$k` (${targetOf(k).config} vs ${b.targetOf(k).config})").mkString("; "))
        else
          // non-default side wins; both default -> omit
          val mergedTargets = allKeys.flatMap { k =>
            val mine   = targetOf(k)
            val theirs = b.targetOf(k)
            val chosen = if mine != BeanPropertyTransform.Target.DefPair then mine else theirs
            if chosen != BeanPropertyTransform.Target.DefPair then Some(k -> chosen) else scala.None
          }.toMap
          // Only/Only union include sets, Everywhere/Everywhere union except sets, mixed is refused;
          // the no-op side yields to the other's scope.
          val noOp = RuleScope.Only(Set.empty)
          val mergedScope: Either[String, RuleScope] = (detectScope, b.detectScope) match
            case (s, `noOp`) => Right(s)
            case (`noOp`, s) => Right(s)
            case (RuleScope.Only(mine), RuleScope.Only(theirs))             =>
              Right(RuleScope.Only(mine ++ theirs))
            case (RuleScope.Everywhere(mine), RuleScope.Everywhere(theirs)) =>
              Right(RuleScope.Everywhere(mine ++ theirs))
            case _ =>
              Left(s"`bean-properties` scope disagrees: one is `Only` and the other is " +
                "`Everywhere` — the two directions cannot compose")
          mergedScope match
            case Left(reason) => Left(reason)
            case Right(composedScope) =>
              val merged = new BeanPropertyTransform(
                pairs   = myPairs ++ theirPairs,
                targets = mergedTargets,
                exposedFields = exposedFields,
                scope   = composedScope)
              val added = b.subjects -- subjects
              Right(MergeablePolicy.Merged(merged, added))
    case _ =>
      Left(s"`bean-properties` cannot merge with ${later.getClass.getSimpleName}")

  // ---- policy, bound before the pipeline starts ---------------------------------------------

  private var parsed: List[BeanPropertyTransform.Entry] = Nil
  private var bound: Map[String, List[PolicyBinder.Hit]] = Map.empty
  private var records: List[PolicyBinder.Record]         = Nil
  private var ownFindings: List[PolicyFinding]           = Nil
  /** Types the base or this module SUBSTITUTED — detection skips these owners (D14, §1.5). */
  private var substitutedOwners: Set[String]             = Set.empty
  /** which units this run emits: a base's declaration is read literally, never derived on (K51). */
  private var runScope: RunScope                         = RunScope.whole

  def bindPolicy(binder: PolicyBinder): Unit =
    runScope          = binder.run
    substitutedOwners = binder.run.baseSubstitutedOwners ++ binder.run.ownSubstitutedOwners
    val (entries, malformed) = BeanPropertyTransform.parse(pairs)
    parsed      = entries
    ownFindings = malformed
    bound = entries.flatMap { e =>
      // bare key names every overload; only the one with the right shape converts (§8.5)
      e.accessors.map { a =>
        val key = MemberKey(e.owner, a).render
        key -> binder.bindMembers(name, s"BeanPropertyTransform(pairs) `${e.key}`", key)
          .toOption.getOrElse(Nil)
      }
    }.toMap
    records = binder.recordsFor(name)

  /** The never-fired half (from the binding) plus this phase's own malformed entries and counted
    * refusals. */
  def policyReport: PolicyReport = PolicyReport.fromBindings(records) ++ PolicyReport(ownFindings)

  // ---- the run ------------------------------------------------------------------------------

  /** Every declaration of the getter's whole override COMPONENT -> the property, and likewise for
    * the setter — an interface `getX()` and an implementor's kept `def x()` would otherwise not
    * override each other. */
  private var getters: Map[SymId, BeanPropertyTransform.Property] = Map.empty
  private var setters: Map[SymId, BeanPropertyTransform.Property] = Map.empty

  /** setter declaration -> the SAME-owner getter declaration whose name its assignment's LHS must
    * carry, so the emitted `this.x = v` names a member of the class it is written in. */
  private var lhsOf: Map[SymId, SymId] = Map.empty

  /** the collapses this run applied — decided once, applied once, reported once. */
  private var collapsed: List[BeanPropertyTransform.Collapsed] = Nil

  override def run(program: Program): Program =
    getters = Map.empty; setters = Map.empty; lhsOf = Map.empty; collapsed = Nil
    val scopeActive = scope != RuleScope.Only(Set.empty)
    if pairs.isEmpty && !scopeActive then return program

    val refusals = collection.mutable.ListBuffer.empty[PolicyFinding]
    def refuse(e: BeanPropertyTransform.Entry, why: String): scala.None.type =
      refusals += PolicyFinding(name, s"BeanPropertyTransform(pairs) `${e.key}`", e.key,
        PolicyIssue.Unverifiable, why)
      record(Decision(
        kind       = Decision.Kind.ScopedOut,
        subject    = SymId.None,
        subjectFqn = e.key,
        detail     = Map(
          "refused"  -> "bean-property",
          "property" -> e.property,
          "why"      -> why,
        ),
        reason = Reason.Configured(name, e.key),
        origin = Origin.synthetic,
      ))
      scala.None

    val graph = OverrideGraph.build(program)

    // ---- 1. shape validation, per entry, on the PRE-rename program -------------------------
    val configuredProperties = parsed.flatMap(e => validate(program, graph, e, refuse))

    // ---- 1b. AUTO-DETECTION: scan owned types in scope for bean accessor pairs ---------------
    val detectedProperties =
      if !scopeActive then Nil
      else
        // skip an accessor already claimed by a configured pair, or a derived property name
        // colliding with a configured one's key (the two can differ, e.g. getDragActor -> currentDragActor)
        val configuredAccessors: Set[String] = parsed.flatMap { e =>
          e.accessors.map(a => MemberKey(e.owner, a).render)
        }.toSet
        val configuredPropertyKeys: Set[String] = pairs.keySet
        val detected = BeanPropertyTransform.detect(program, graph, scope,
          configuredAccessors, configuredPropertyKeys,
          substitutedOwners, emitted = s => runScope.emitsSymbol(program, s),
          isVoid = (p, t) => isVoid(p, t), headOf = t => headOf(t),
          callsAreRewritable = (comp, ar) =>
            comp.forall(s => program.usages(s).forall {
              case Usage(UsageKind.Call, a: Tree.Apply, _) =>
                a.args.sizeIs == ar && receiverOf(a.fun).isDefined
              case Usage(UsageKind.Call, _, _) => false
              case Usage(UsageKind.TermRef, _, _) => false
              case _ => true
            }),
          this)
        detected

    val properties = configuredProperties ++ detectedProperties

    // ---- 2. the rename: both accessors of a pair in ONE group, so half a property is impossible
    val requests = properties.flatMap { p =>
      val reason = if detectedProperties.contains(p) then Reason.Universal("bean-detect")
                   else Reason.Configured(name, p.key)
      MemberRenamer.Request(p.getter, p.property, reason, p.key, p.key) ::
        p.setter.map(s =>
          MemberRenamer.Request(s, p.property + "_=", reason, p.key, p.key)).toList
    }
    val (renamed, renameRefusals) = MemberRenamer.rename(
      program, graph, requests, MemberRenamer.OnCollision.DeferToEmitter, decisions)

    val refusedKeys = renameRefusals.map(_.request.key).toSet
    renameRefusals.map(_.request.key).distinct.foreach { k =>
      val why = renameRefusals.find(_.request.key == k).map(_.why).getOrElse("refused")
      parsed.find(_.key == k).foreach(e => refuse(e, why))
      detectedProperties.find(_.key == k).foreach { p =>
        consider(IdiomCandidate(IdiomKind.BeanDetect,
          IdiomVerdict.Refused("RenameRefused", why), p.key,
          s"auto-detected pair `${p.property}`", Decision.originOf(program, p.getter)))
      }
    }
    ownFindings = ownFindings.filterNot(f => refusals.exists(_.key == f.key)) ++ refusals.toList

    val applied = properties.filterNot(p => refusedKeys.contains(p.key))

    // ---- 3. the collapse decision, per entry, on the pre-rename program ----------------------
    // filed for every parsed entry (incl. def-pair refusals) since `idiom(refused)` is a denominator
    lazy val written = BeanCollapse.writtenSymbols(program)
    val verdicts = parsed.map { e =>
      e -> (applied.find(_.key == e.key) match
        case scala.None =>
          BeanCollapse.Verdict.Refuse(BeanCollapse.Guard.PairRefused)
        case Some(prop) =>
          BeanCollapse.decide(program, graph, prop, targetOf(e.key), exposedFields, written))
    }
    verdicts.foreach { (e, v) =>
      val at   = applied.find(_.key == e.key)
        .map(p => Decision.originOf(program, p.getter)).getOrElse(Origin.synthetic)
      val what = s"property `${e.property}` via `${e.value}`"
      val verdict = v match
        case BeanCollapse.Verdict.Collapse(_) => IdiomVerdict.Converted
        case BeanCollapse.Verdict.Refuse(g)   => IdiomVerdict.Refused(g.toString, g.why)
      consider(IdiomCandidate(IdiomKind.BeanCollapse, verdict, e.key, what, at))
    }
    collapsed = verdicts.collect { case (e, BeanCollapse.Verdict.Collapse(f)) =>
      val p = applied.find(_.key == e.key).get
      BeanPropertyTransform.Collapsed(e.key, e.value, p.property, p.getter, p.setter, f,
        targetOf(e.key),
        triviaOf(program, p.getter) ++ p.setter.toList.flatMap(s => triviaOf(program, s)))
    }

    if applied.isEmpty then return renamed

    getters = applied.flatMap(p => p.getterMembers.map(_ -> p)).toMap
    setters = applied.flatMap(p => p.setterMembers.map(_ -> p)).toMap
    lhsOf = applied.flatMap { p =>
      val byOwner = p.getterMembers.iterator.map(g => renamed.symbolOf(g).map(_.owner) -> g).toMap
      p.setterMembers.map(s => s -> byOwner.getOrElse(renamed.symbolOf(s).map(_.owner), p.getter))
    }.toMap

    // ---- 4. the tree edits: the getter's empty parameter clause, and every call site ---------
    // `paramss = Nil` renders no `()`, turning the java nilary method into a scala parameterless
    // one; call sites move in the same pass since `o.x()` on a parameterless def is a type error.
    // `info` stays `MethodType(Nil, R)` — every arity reader in the engine reads `paramss`.
    val paired =
      given Program = renamed
      renamed.rebuilt(units = renamed.units.map(u => StandardTraversal.mapClassDef(this, u)))

    // ---- 5. …and the collapse, applied last, over what the def-pair path has already moved ---
    if collapsed.isEmpty then paired else applyCollapse(paired)

  override def transformDefDef(t: Tree.DefDef)(using Program): Tree.DefDef =
    if getters.contains(t.symbol) then t.copy(paramss = Nil) else t

  /** `o.getX()` -> `o.x`, `o.setX(v)` -> `o.x = v`. Bottom-up, so `o.setX(o.getX() + 1)` needs no
    * special case. The assignment's LHS names the GETTER symbol — scalac desugars `recv.x = v` to
    * `x_=` — which is also why a set-only entry has nothing to put on an LHS. */
  override def transformApply(t: Tree.Apply)(using Program): Term =
    if getters.contains(t.method) && t.args.isEmpty then
      receiverOf(t.fun) match
        case Some(scala.None)    => Tree.Ident(t.method, t.tpe, t.origin)
        case Some(Some(qual))    => Tree.Select(qual, t.method, t.tpe, t.origin)
        case scala.None          => t
    else if setters.contains(t.method) && t.args.sizeIs == 1 then
      val g   = lhsOf.getOrElse(t.method, setters(t.method).getter)
      val res = summon[Program].symbolOf(g).map(_.info).getOrElse(TypeRepr.NoType) match
        case TypeRepr.MethodType(_, r, _) => r
        case other                        => other
      receiverOf(t.fun) match
        case Some(scala.None) => Tree.Assign(Tree.Ident(g, res, t.origin), t.args.head, t.tpe, t.origin)
        case Some(Some(qual)) => Tree.Assign(Tree.Select(qual, g, res, t.origin), t.args.head, t.tpe, t.origin)
        case scala.None       => t
    else t

  /** The call's receiver: `Some(None)` bare, `Some(Some(q))` qualified, `None` a shape `validate`
    * has already refused the pair for. */
  private def receiverOf(fun: Term): Option[Option[Term]] = fun match
    case _: Tree.Ident            => Some(scala.None)
    case Tree.Select(q, _, _, _)  => Some(Some(q))
    case _                        => scala.None

  // ---- the collapse ---------------------------------------------------------------------------

  /** An accessor's own comments, harvested before its declaration is deleted, in declaration order
    * (getter then setter) and appended after the field's own (§4.58). */
  private def triviaOf(p: Program, m: SymId): List[Trivia] =
    p.definitionOf(m).collect { case d: Tree.DefDef => d.leading }.getOrElse(Nil)

  /** Apply the collapses: the getter's symbol becomes the property's storage, the field's `ValDef`
    * (kept in place — its position is what the class computes, §4.55/JLS 12.5) becomes the
    * property's declaration, and both accessors' declarations go. Visibility comes from the
    * accessors (the surface); every other flag from the field, except `isMutable`, which is the
    * target shape (`Var`/`Val`) the guards made sound. */
  private def applyCollapse(p: Program): Program =
    val byGetter = collapsed.map(c => c.getter -> c).toMap
    val syms = p.symbols.all.map { s =>
      byGetter.get(s.id).flatMap(c => p.symbolOf(c.field).map(f => (c, f))) match
        case Some((c, f)) =>
          s.copy(
            info = f.info,
            // a field has no parameter spelling (§8.1)
            descriptor = scala.None,
            flags = f.flags.copy(
              isPrivate        = s.flags.isPrivate,
              isProtected      = s.flags.isProtected,
              isPackagePrivate = s.flags.isPackagePrivate,
              isOverride       = false,
              isMutable        = c.target == BeanPropertyTransform.Target.Var,
            ))
        case scala.None => s
    }
    val retyped = p.rebuilt(symbols = SymbolTable(syms))
    val out =
      given Program = retyped
      retyped.rebuilt(units = retyped.units.map(u =>
        StandardTraversal.mapClassDef(new BeanPropertyTransform.Collapser(collapsed), u)))
    val indexed = out.rebuilt(xref = Xref.build(out.units))
    recordCollapse(indexed)
    indexed

  /** One decision per surviving declaration (§5.1), recording that the JVM method names moved
    * (`getName()`/`setName()` -> `name()`/`name_$eq()` — invisible to any compiler or test check,
    * relevant to a reflective reader, ENGINE-LIMITS K21), plus the residue the shape cannot rule out. */
  private def recordCollapse(p: Program): Unit =
    collapsed.foreach { c =>
      record(Decision(
        kind       = Decision.Kind.CollapsedProperty,
        subject    = c.getter,
        subjectFqn = Decision.fqnOf(p, c.getter, c.key),
        detail = Map(
          "form"  -> c.target.config,
          "from"  -> c.accessors,
          "to"    -> c.property,
          "field" -> Decision.fqnOf(p, c.field, "?"),
          "was"   -> c.accessors.split('/').map(_.trim + "()").mkString(" "),
          "why"   -> ("java's bean pair over a trivial field and a scala property are the same " +
            "value with two spellings, and the port publishes the scala one — but the JVM METHOD " +
            "NAMES move with it, so a framework that discovers `getX`/`setX` reflectively finds " +
            "neither" + finality(c)),
        ),
        reason = Reason.Configured(name, c.key),
        origin = Decision.originOf(p, c.getter),
      ))
      // these three should read zero — an unchecked claim is the K2.5 shape
      c.setter.foreach(s => residue(p, c, s, "a reference to the setter this collapse deleted"))
      residue(p, c, c.field, "a reference to the backing field the property replaced")
      residue(p, c, c.getter, "a CALL of a member that is now a `var`", _.kind == UsageKind.Call)
    }

  /** For `target = "val"` only: scalac emits the backing field `final` where java's was not, so a
    * reflective writer (`setAccessible` + `Field.set`) that worked against the java field no longer
    * works (ENGINE-LIMITS K21). Not recorded for `var`, whose field is not final. */
  private def finality(c: BeanPropertyTransform.Collapsed): String =
    if c.target != BeanPropertyTransform.Target.Val then ""
    else "; and this one is a `val`, so its BACKING FIELD is `final` on the JVM where java's was " +
      "not — a reflective writer (`setAccessible` + `Field.set`) that worked against the java " +
      "field does not work against this one"

  private def residue(p: Program, c: BeanPropertyTransform.Collapsed, sym: SymId, what: String,
                      keep: Usage => Boolean = _ => true): Unit =
    p.usages(sym).filter(keep).map(_.enclosing).distinct.sortBy(_.raw).foreach { encl =>
      consider(IdiomCandidate(IdiomKind.BeanCollapse, IdiomVerdict.Residue(what),
        Decision.fqnOf(p, encl, c.key), s"property `${c.property}`", Decision.originOf(p, encl)))
    }

  // ---- validation ---------------------------------------------------------------------------

  private def validate(p: Program, graph: OverrideGraph, e: BeanPropertyTransform.Entry,
                       refuse: (BeanPropertyTransform.Entry, String) => scala.None.type)
      : Option[BeanPropertyTransform.Property] =

    def defOf(s: SymId): Option[Tree.DefDef] = p.definitionOf(s).collect { case d: Tree.DefDef => d }
    def hits(a: String): List[SymId] = bound.getOrElse(MemberKey(e.owner, a).render, Nil).flatMap(_.sym)

    /** The java NILARY overload, and only it: `getX(int)` stays where `getX()` moves (D1 — match by
      * descriptor, never by name-and-guess). */
    def nilary(cands: List[SymId]): List[SymId] =
      cands.filter(s => defOf(s).exists(_.paramss.forall(_.isEmpty)))
    def unary(cands: List[SymId]): List[SymId] =
      cands.filter(s => defOf(s).exists(d => d.paramss.map(_.size).sum == 1))

    def isStatic(s: SymId): Boolean = p.symbolOf(s).exists(_.flags.isStatic)

    /** Every recorded call of every declaration in the whole override COMPONENT is an ordinary
      * `Apply` of the right arity through a movable receiver — a `MethodRef`/bare `Select` is a
      * method value, not the SAM java saw. */
    def callsAreRewritable(component: Set[SymId], arity: Int): Boolean =
      component.forall(s => p.usages(s).forall {
        case Usage(UsageKind.Call, a: Tree.Apply, _) =>
          a.args.sizeIs == arity && receiverOf(a.fun).isDefined
        case Usage(UsageKind.Call, _, _) => false // a `MethodRef` — a method VALUE
        case Usage(UsageKind.TermRef, _, _) => false // a bare `Select`/`Ident` of the method
        case _ => true
      })

    e.setterName match
      case scala.None if e.accessors.sizeIs != 1 =>
        refuse(e, s"`${e.value}` names ${e.accessors.size} accessors; the value is `getter` or " +
          "`getter/setter`")
      case _ =>
        nilary(hits(e.getterName)) match
          case Nil =>
            // binder already reports a key naming nothing; here it named something with no getter shape
            if hits(e.getterName).isEmpty then scala.None
            else refuse(e, s"`${e.getterName}` has no NILARY overload, so there is no getter to " +
              "convert (an overload with parameters is left exactly as it is)")
          case g :: rest if rest.nonEmpty =>
            refuse(e, s"`${e.getterName}` has ${rest.size + 1} nilary declarations in this program " +
              "and a property has one getter")
          case g :: Nil =>
            val gd    = defOf(g).get
            val gComp = graph.closureOf(g).members
            if isStatic(g) then
              refuse(e, s"`${e.getterName}` is STATIC; a companion property is out of scope for " +
                "this phase (v1), so the accessor is left as it is")
            else if isVoid(p, gd.returnTpt.tpe) then
              refuse(e, s"`${e.getterName}` returns void, so it is not a getter")
            else if !callsAreRewritable(gComp, 0) then
              refuse(e, s"`${e.getterName}` is referenced in VALUE position (a method reference or " +
                "a bare selection); an eta-expanded accessor is not the SAM java saw")
            else
              e.setterName match
                case scala.None =>
                  Some(BeanPropertyTransform.Property(e.key, e.property, g, scala.None, gComp, Set.empty))
                case Some(sn) =>
                  val cands = unary(hits(sn)).filter(s => paramHead(p, s) == headOf(gd.returnTpt.tpe))
                  cands match
                    case Nil =>
                      if hits(sn).isEmpty then scala.None
                      else refuse(e, s"`$sn` has no single-parameter overload taking " +
                        s"`${e.getterName}`'s return type, so the two are not a pair")
                    case s :: rest if rest.nonEmpty =>
                      refuse(e, s"`$sn` has ${rest.size + 1} declarations matching the getter's type")
                    case s :: Nil =>
                      val sd = defOf(s).get
                      if isStatic(s) then
                        refuse(e, s"`$sn` is STATIC; a companion property is out of scope (v1)")
                      else if headOf(sd.returnTpt.tpe).exists(h => ownerTypeOf(p, s).contains(h)) then
                        refuse(e, s"`$sn` is FLUENT — it returns its own declaring type for " +
                          "chaining, and `o.x = v` is Unit, so a chain has no assignment rendering")
                      else if !isVoid(p, sd.returnTpt.tpe) then
                        refuse(e, s"`$sn` returns a value, and an assignment discards it")
                      else if !callsAreRewritable(graph.closureOf(s).members, 1) then
                        refuse(e, s"`$sn` is referenced in VALUE position; an eta-expanded `x_=` is " +
                          "not the SAM java saw")
                      else {
                        val sComp = graph.closureOf(s).members
                        val getterOwners = gComp.flatMap(gg => p.symbolOf(gg).map(_.owner))
                        val setterOnly = sComp.flatMap(sm => p.symbolOf(sm).map(_.owner))
                          .filterNot(getterOwners.contains)
                          .flatMap(o => p.symbolOf(o).map(_.fullName)).toList.distinct.sorted
                        if setterOnly.nonEmpty then
                          refuse(e, s"the setter's override component reaches " +
                            s"${setterOnly.mkString(", ")} which declares the setter without a " +
                            "getter — `x.prop = v` needs `prop` in scope on the receiver")
                        else Some(BeanPropertyTransform.Property(e.key, e.property, g, Some(s),
                          gComp, sComp))
                      }

  private def ownerTypeOf(p: Program, m: SymId): Option[SymId] = p.symbolOf(m).map(_.owner)

  private def paramHead(p: Program, m: SymId): Option[SymId] =
    p.definitionOf(m).collect { case d: Tree.DefDef => d }
      .flatMap(_.paramss.flatten.headOption).flatMap(v => headOf(v.tpt.tpe))

  private def headOf(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headOf(tc)
    case _                           => scala.None

  /** Java `void`, as the frontend writes it — not `scala.Unit`, the engine's own rendering.
    * See `PolicyKeyLintSpec`'s allow-list. */
  private def isVoid(p: Program, t: TypeRepr): Boolean = t match
    case TypeRepr.TypeRef(_, s) => p.symbolOf(s).exists(_.fullName == "scala.Unit")
    case _                      => false

object BeanPropertyTransform:

  /** The shape an entry asks for: a `var` needs a setter to be a faithful surface, a `val` needs
    * the storage written once — naming them lets the phase refuse a mismatch rather than pick one
    * silently. `DefPair` is the default (§1(b)'s no-op, at the entry granularity). */
  enum Target:
    /** `def x` / `def x_=(v: R): Unit`, bodies verbatim — the phase's original and only shape. */
    case DefPair
    /** a public `var x`: the accessors go, and the backing field becomes the property. */
    case Var
    /** a public `val x`: a get-only entry over storage java wrote once. */
    case Val

    /** The spelling a `.conf` writes; derived nowhere else so the two cannot drift. */
    def config: String = this match
      case DefPair => "def-pair"
      case Var     => "var"
      case Val     => "val"

  object Target:
    /** The closed set a `target = …` key is read against. */
    val byConfigName: Map[String, Target] = values.map(t => t.config -> t).toMap

  /** One collapsed property: the policy key, the accessors as spelled, the emitted name, the
    * surviving symbol, the setter that goes, the storage field, the target shape, and the deleted
    * declarations' comments. */
  final case class Collapsed(key: String, accessors: String, property: String,
                             getter: SymId, setter: Option[SymId], field: SymId,
                             target: Target, trivia: List[Trivia])

  /** The tree edit — one traversal, bottom-up, so every reference has been re-pointed by the time
    * the owning class's body is rebuilt.
    * A `StandardTraversal` phase and not a private recursion (§3), so an anonymous or method-local
    * body is reached too. */
  private[transform] final class Collapser(cs: List[Collapsed]) extends Phase:
    def name: String = "bean-properties/collapse"

    private val byField = cs.map(c => c.field -> c).toMap
    private val gone    = cs.flatMap(c => c.getter :: c.setter.toList).toSet

    override def transformClassDef(t: Tree.ClassDef)(using Program): Tree.ClassDef =
      val touches = t.body.exists {
        case v: Tree.ValDef => byField.contains(v.symbol)
        case d: Tree.DefDef => gone.contains(d.symbol)
        case _              => false
      }
      if !touches then t
      else t.copy(body = t.body.flatMap {
        case v: Tree.ValDef if byField.contains(v.symbol) =>
          val c = byField(v.symbol)
          List(v.copy(symbol = c.getter, leading = v.leading ++ c.trivia))
        case d: Tree.DefDef if gone.contains(d.symbol) => Nil
        case other                                     => List(other)
      })

    /** Every read and write of the backing field now names the property — sound because the
      * trivial-body guard already established `this.f` and `this.x` are the same storage. */
    override def transformIdent(t: Tree.Ident)(using Program): Term =
      byField.get(t.sym).map(c => t.copy(sym = c.getter)).getOrElse(t)

    override def transformSelect(t: Tree.Select)(using Program): Term =
      byField.get(t.sym).map(c => t.copy(sym = c.getter)).getOrElse(t)

  /** One applied property: the policy key, the emitted name, and every declaration of each
    * accessor's whole override component, which is what the arity edit and call-site rewrite
    * range over. */
  final case class Property(key: String, property: String, getter: SymId, setter: Option[SymId],
                            getterMembers: Set[SymId], setterMembers: Set[SymId])

  /** One declared entry, parsed. `key` is kept verbatim — the string an agent edits. */
  final case class Entry(key: String, value: String, owner: String, property: String,
                         getterName: String, setterName: Option[String]):
    def accessors: List[String] = getterName :: setterName.toList

  /** Parse the declared map; anything outside the grammar is a `Malformed` finding rather than a
    * best-effort reading. */
  def parse(pairs: Map[String, String]): (List[Entry], List[PolicyFinding]) =
    val findings = collection.mutable.ListBuffer.empty[PolicyFinding]
    def bad(k: String, what: String): scala.None.type =
      findings += PolicyFinding("bean-properties", s"BeanPropertyTransform(pairs) `$k`", k,
        PolicyIssue.Malformed, what)
      scala.None
    val entries = pairs.toList.sortBy(_._1).flatMap { (k, v) =>
      MemberKey.parse(k) match
        case Left(m) => bad(k, m.what)
        case Right(mk) if mk.descriptor.isDefined =>
          bad(k, "the KEY names the emitted PROPERTY, which has no parameter list — write " +
            "`owner#property`, and name the accessors in the value")
        case Right(mk) =>
          v.split("/", -1).toList.map(_.trim) match
            case List(g) if g.nonEmpty      => Some(Entry(k, v, mk.owner, mk.name, g, scala.None))
            case List(g, s) if g.nonEmpty && s.nonEmpty =>
              Some(Entry(k, v, mk.owner, mk.name, g, Some(s)))
            case List(g) if g.isEmpty       => bad(k, "the value is empty — it names the accessor(s)")
            case List(g, _) if g.isEmpty    =>
              bad(k, "a SET-ONLY entry (`/setX`) is refused: scala's `o.x = v` sugar needs `x` in " +
                "scope, and the assignment's left-hand side names the GETTER symbol — with no " +
                "getter there is nothing to put on an LHS. Name both accessors, or neither")
            case _ =>
              bad(k, s"`$v` is not `getter` or `getter/setter` — a value with more than one `/` " +
                "names more accessors than a property has")
    }
    (entries, findings.toList)

  // ---- auto-detection helpers ----------------------------------------------------------------

  /** Derive a property name from a getter method name following the standard JavaBeans convention
    * (`java.beans.Introspector`): `getX` -> `x`, `getURL` -> `URL`, `isReady` -> `ready`. Returns
    * `None` if the name does not match `get[A-Z].*` or `is[A-Z].*`. */
  def propertyNameOf(methodName: String): Option[String] =
    if methodName.startsWith("get") && methodName.length > 3 && methodName.charAt(3).isUpper then
      Some(decapitalize(methodName.substring(3)))
    else if methodName.startsWith("is") && methodName.length > 2 && methodName.charAt(2).isUpper then
      Some(decapitalize(methodName.substring(2)))
    else scala.None

  /** The standard `java.beans.Introspector.decapitalize` rule: lowercase the first character UNLESS
    * the first TWO characters are uppercase, in which case leave the string as-is. */
  def decapitalize(s: String): String =
    if s.length > 1 && s.charAt(0).isUpper && s.charAt(1).isUpper then s
    else if s.nonEmpty then s.updated(0, s.charAt(0).toLower) else s

  /** Scan the program for bean accessor pairs in the types named by `scope` (checked on the owner
    * type). A configured pair at the same key wins; each candidate goes through the same shape
    * checks as the configured path, filing a refusal as `IdiomKind.BeanDetect` with its guard.
    * @param substitutedOwners upstream FQNs of types the base SUBSTITUTED — skipped so a rename
    *                          the injected file did not perform is not applied (D14).
    * @param emitted does this run emit the owner's unit — a base's is skipped (K51). */
  def detect(program: Program, graph: OverrideGraph, scope: RuleScope,
             configuredAccessors: Set[String], configuredPropertyKeys: Set[String],
             substitutedOwners: Set[String], emitted: SymId => Boolean,
             isVoid: (Program, TypeRepr) => Boolean,
             headOf: TypeRepr => Option[SymId],
             callsAreRewritable: (Set[SymId], Int) => Boolean,
             phase: BeanPropertyTransform): List[Property] =
    val result = collection.mutable.ListBuffer.empty[Property]

    def defOf(s: SymId): Option[Tree.DefDef] = program.definitionOf(s).collect { case d: Tree.DefDef => d }
    def isStatic(s: SymId): Boolean = program.symbolOf(s).exists(_.flags.isStatic)
    def ownerTypeOf(m: SymId): Option[SymId] = program.symbolOf(m).map(_.owner)

    def paramHead(m: SymId): Option[SymId] =
      defOf(m).flatMap(_.paramss.flatten.headOption).flatMap(v => headOf(v.tpt.tpe))

    // static methods stay in the scan so the per-getter check can file a counted refusal
    val ownedByType = collection.mutable.Map.empty[SymId, List[Symbol]]
    program.symbols.all.foreach { s =>
      if program.owned(s.id) then
        val owner = s.owner
        ownedByType.updateWith(owner) {
          case Some(list) => Some(s :: list)
          case scala.None => Some(List(s))
        }
    }

    ownedByType.foreach { (ownerSym, members) =>
      val ownerSymObj = program.symbolOf(ownerSym)
      val ownerFqn = ownerSymObj.map(_.fullName).getOrElse("")
      // skip types the base SUBSTITUTED — their members are java's, not the injected Scala (D14, §1.5)
      if !substitutedOwners.contains(ownerFqn) && emitted(ownerSym) && ownerSymObj.exists(o => scope.includes(program, o)) then

        // nilary methods matching get[A-Z]* or is[A-Z]*
        val getterCandidates = members.flatMap { s =>
          propertyNameOf(s.name).flatMap { propName =>
            defOf(s.id).flatMap { d =>
              if d.paramss.forall(_.isEmpty) then Some((s, propName, d))
              else scala.None
            }
          }
        }

        // two getters mapping to the same property name is a collision — skip both
        val propNameCounts = getterCandidates.groupBy(_._2).view.mapValues(_.size)
        val ambiguousProps = propNameCounts.collect { case (n, c) if c > 1 => n }.toSet

        getterCandidates.foreach { (getterSym, propName, getterDef) =>
          val key = MemberKey(ownerFqn, propName).render
          // configured pair may use a different property name, so check the accessor name too
          val getterKey = MemberKey(ownerFqn, getterSym.name).render
          if !configuredAccessors.contains(getterKey) && !configuredPropertyKeys.contains(key) &&
             !ambiguousProps.contains(propName) then
            val getterHead = headOf(getterDef.returnTpt.tpe)
            val getterReturnVoid = isVoid(program, getterDef.returnTpt.tpe)
            val gComp = graph.closureOf(getterSym.id).members

            if isStatic(getterSym.id) then
              phase.consider(IdiomCandidate(IdiomKind.BeanDetect,
                IdiomVerdict.Refused("Static", "the getter is static; a companion property is out of scope"),
                key, s"auto-detected `$propName`", Decision.originOf(program, getterSym.id)))
            else if getterReturnVoid then
              phase.consider(IdiomCandidate(IdiomKind.BeanDetect,
                IdiomVerdict.Refused("VoidGetter", "the getter returns void"),
                key, s"auto-detected `$propName`", Decision.originOf(program, getterSym.id)))
            else if !callsAreRewritable(gComp, 0) then
              phase.consider(IdiomCandidate(IdiomKind.BeanDetect,
                IdiomVerdict.Refused("ValuePosition",
                  "the getter is referenced in value position"),
                key, s"auto-detected `$propName`", Decision.originOf(program, getterSym.id)))
            else
              // a refused setter skips the whole pair — no getter-only fallback
              val setterName = "set" + propName.updated(0, propName.charAt(0).toUpper)
              val setterCands = members.filter { s =>
                s.name == setterName && !s.flags.isStatic &&
                  defOf(s.id).exists(d => d.paramss.map(_.size).sum == 1 &&
                    paramHead(s.id) == getterHead)
              }

              var setterRefused = false
              val setterOpt = setterCands match
                case List(s) =>
                  val sd = defOf(s.id).get
                  if headOf(sd.returnTpt.tpe).exists(h => ownerTypeOf(s.id).contains(h)) then
                    phase.consider(IdiomCandidate(IdiomKind.BeanDetect,
                      IdiomVerdict.Refused("FluentSetter",
                        "the setter is fluent — returns its own type for chaining"),
                      key, s"auto-detected `$propName`", Decision.originOf(program, getterSym.id)))
                    setterRefused = true; scala.None
                  else if !isVoid(program, sd.returnTpt.tpe) then
                    phase.consider(IdiomCandidate(IdiomKind.BeanDetect,
                      IdiomVerdict.Refused("NonVoidSetter",
                        "the setter returns a value, and an assignment discards it"),
                      key, s"auto-detected `$propName`", Decision.originOf(program, getterSym.id)))
                    setterRefused = true; scala.None
                  else if !callsAreRewritable(graph.closureOf(s.id).members, 1) then
                    phase.consider(IdiomCandidate(IdiomKind.BeanDetect,
                      IdiomVerdict.Refused("ValuePosition",
                        "the setter is referenced in value position"),
                      key, s"auto-detected `$propName`", Decision.originOf(program, getterSym.id)))
                    setterRefused = true; scala.None
                  else Some(s)
                case _ => scala.None  // 0 or >1 matching setters: get-only pair

              if !setterRefused then
                val accessorsStr = setterOpt match
                  case Some(s) => s"${getterSym.name}/${s.name}"
                  case scala.None => getterSym.name

                val sMembers = setterOpt.map(s => graph.closureOf(s.id).members).getOrElse(Set.empty)

                // a setter-only owner (no corresponding getter) is a seam: `x.prop = v`'s LHS
                // names the getter symbol, which the receiver does not have there. Excluded where
                // a descendant INHERITS the getter from an ancestor declaring the same pair.
                val setterOnlyOwners = setterOpt.toList.flatMap { s =>
                  val sComp = graph.closureOf(s.id).members
                  val getterOwners = gComp.flatMap(g => program.symbolOf(g).map(_.owner))
                  sComp.flatMap { sm =>
                    program.symbolOf(sm).map(_.owner).filterNot(getterOwners.contains)
                  }.filterNot { owner =>
                    val pairOwners = getterOwners.filter { go =>
                      sComp.exists(sm => program.symbolOf(sm).exists(_.owner == go))
                    }
                    pairOwners.exists(d => d == owner || graph.ancestorsOf(owner).contains(d))
                  }.flatMap(o => program.symbolOf(o).map(_.fullName)).toList.distinct.sorted
                }
                if setterOnlyOwners.nonEmpty then
                  phase.consider(IdiomCandidate(IdiomKind.BeanDetect,
                    IdiomVerdict.Refused("SetterOnlyInterface",
                      s"the setter's override component reaches ${setterOnlyOwners.mkString(", ")} " +
                      "which declares the setter without a getter — `x.prop = v` needs " +
                      "`prop` in scope on the receiver, and the interface has none"),
                    key, s"auto-detected `$propName`", Decision.originOf(program, getterSym.id)))
                else {
                  val prop = Property(key, propName, getterSym.id, setterOpt.map(_.id), gComp, sMembers)

                  phase.consider(IdiomCandidate(IdiomKind.BeanDetect, IdiomVerdict.Converted,
                    key, s"auto-detected pair `$propName` via `$accessorsStr`",
                    Decision.originOf(program, getterSym.id)))
                  phase.record(Decision(
                    kind       = Decision.Kind.RenamedMember,
                    subject    = getterSym.id,
                    subjectFqn = key,
                    detail     = Map(
                      "property"  -> propName,
                      "accessors" -> accessorsStr,
                      "source"    -> "auto-detect",
                    ),
                    reason = Reason.Universal("bean-detect"),
                    origin = Decision.originOf(program, getterSym.id),
                  ))
                  result += prop
                }
        }
    }
    result.toList

