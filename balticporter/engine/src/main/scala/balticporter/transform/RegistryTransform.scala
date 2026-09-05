package balticporter.transform

import balticporter.core.{MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*
import balticporter.tir.TypeRepr.NoType

/** Re-points REFLECTIVE INSTANTIATION at a `Class`-keyed registry the port supplies — the mechanism
  * three ports hand-wrote (`ENGINE-LIMITS.md` P10). `callee(classValue)` becomes
  * `<registry>.create(classValue)`, and the table/`register`/`create` are MINTED at the declared
  * placement. §1(b): mechanism universal, every name, scope and miss per-library; an empty spec is a
  * no-op. Every shape it cannot key is refused and counted ([[RegistryCheck]]). */
final class RegistryTransform(
    val entries: List[RegistryTransform.Registry] = Nil,
    /** members of an injected facade that THROWS on every reflective path: counted, never
      * rewritten, so the port's largest silent run-time refusal has a number. Empty = 0 rows. */
    val facadeMembers: Set[String] = Set.empty,
) extends Phase,
      PolicySource,
      SurfacePolicy,
      MergeablePolicy,
      PolicyBound,
      Rewrite:
  import RegistryTransform.*

  def name: String = RegistryTransform.Name

  /** the residue lanes; the reflective miss arm this phase EMITS is `portability(emitted)`'s. */
  def accountedBy: Set[String] = RegistryCheck.AllLanes + PortabilityCheck.EmittedLane

  // ---- bound state (CLAUDE.md §4.56: a phase decides from BOUND symbols, never a raw string) ----

  private var records: List[PolicyBinder.Record]   = Nil
  private var shapeFindings: List[PolicyFinding]   = Nil
  /** entry index → the callee symbols its key names. */
  private var boundCallees: Map[Int, Set[SymId]]   = Map.empty
  /** entry index → bound seed type symbols, in declared order. */
  private var boundSeeds: Map[Int, List[SymId]]    = Map.empty
  /** entry index → the exception types java's `catch` around this callee names. */
  private var boundHandles: Map[Int, Set[SymId]]   = Map.empty
  private var facadeCallees: Set[SymId]            = Set.empty
  private var runScope: RunScope                   = RunScope.whole

  def bindPolicy(binder: PolicyBinder): Unit =
    runScope = binder.run
    boundCallees = entries.zipWithIndex.map { (e, i) =>
      i -> binder.bindCallee(Name, s"Registry(${e.callee}).callee", e.callee, Ownership.Either)
             .toOption.flatMap(_.sym).toSet
    }.toMap
    boundSeeds = entries.zipWithIndex.map { (e, i) =>
      i -> e.seeds.flatMap(s =>
        binder.bindType(Name, s"Registry(${e.callee}).seeds", s, Ownership.Either).toOption)
    }.toMap
    boundHandles = entries.zipWithIndex.map { (e, i) =>
      i -> e.handles.toList.sorted.flatMap(h =>
        binder.bindType(Name, s"Registry(${e.callee}).handles", h, Ownership.Either).toOption).toSet
    }.toMap
    entries.foreach {
      case Registry(c, Placement.Member(owner, _), _, _, _, _, _) =>
        binder.bindType(Name, s"Registry($c).placement", owner)
      case _ => ()
    }
    facadeCallees = facadeMembers.toList.sorted.flatMap(m =>
      binder.bindMembers(Name, "RegistryTransform(facadeMembers)", m, Ownership.Either)
        .toOption.getOrElse(Nil).flatMap(_.sym)).toSet
    records = binder.recordsFor(Name)
    // the shape fact the binder cannot say: a seed must be CONSTRUCTIBLE with no arguments, or the
    // registration this phase writes for it cannot compile.
    shapeFindings = entries.zipWithIndex.flatMap { (e, i) =>
      boundSeeds(i).zip(e.seeds).collect {
        case (sym, fqn) if !hasNilaryCtor(binder.program, sym) =>
          PolicyFinding(Name, s"Registry(${e.callee}).seeds", fqn, PolicyIssue.Unverifiable,
            s"`$fqn` declares no visible no-argument constructor in this program, so the " +
              "`register(classOf[…], () => new …)` this phase writes for it cannot be built — " +
              "drop the seed, or register it from the consumer's bootstrap instead")
      }
    }

  /** a visible nilary constructor, read off the TIR: what a seed's `() => new X` needs. */
  private def hasNilaryCtor(p: Program, tpe: SymId): Boolean =
    p.symbols.all.exists(s =>
      s.owner == tpe && s.name == "<init>" && !s.flags.isPrivate && (s.info match
        case TypeRepr.MethodType(Nil, _, _) => true
        case _                              => false))

  // ---- surface ---------------------------------------------------------------------------------

  /** The minted members and the miss arm are emitted SIGNATURES and emitted BEHAVIOUR, so the spec
    * is shared surface (§1.5). Sorted; an EMPTY spec contributes NO segment (§1(b)'s fingerprint
    * no-op rule), so the key's arrival is flat on every port that does not use it. */
  def surfaceFingerprint: String =
    if entries.isEmpty && facadeMembers.isEmpty then ""
    else
      val es = entries.map(e =>
        s"${e.callee}->${Placement.render(e.placement)}/${Miss.render(e.miss)}" +
          e.bound.fold("")(b => s"<:$b") +
          (if e.seeds.isEmpty then "" else e.seeds.sorted.mkString("(", ",", ")")) +
          (if e.scope.isUnrestricted then "" else s"[${e.scope.fingerprint}]")).sorted.mkString(",")
      val fs = if facadeMembers.isEmpty then "" else facadeMembers.toList.sorted.mkString(";facade:", ",", "")
      es + fs

  /** Independent callees UNION; the same callee, or the same placement SLOT, with a different entry
    * REFUSES — two registries at one declaration is a conflict only a human can resolve. */
  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case o: RegistryTransform =>
      val mine  = entries.map(e => e.callee -> e).toMap
      val slots = entries.map(e => Placement.slot(e.placement) -> e).toMap
      val conflicts = o.entries.sortBy(_.callee).flatMap { e =>
        mine.get(e.callee).filter(_ != e).map(_ => s"${e.callee}: a different registry entry").toList ++
          slots.get(Placement.slot(e.placement)).filter(_ != e)
            .map(_ => s"${Placement.slot(e.placement)}: already carries a registry").toList
      }
      if conflicts.nonEmpty then Left(conflicts.distinct.mkString("; "))
      else
        Right(MergeablePolicy.Merged(
          new RegistryTransform(entries ++ o.entries.filterNot(e => mine.contains(e.callee)),
                                facadeMembers ++ o.facadeMembers),
          (o.entries.map(_.callee).toSet -- mine.keySet).map(MergeablePolicy.subjectOf)))
    case _ => Left(s"expected RegistryTransform, got ${later.getClass.getSimpleName}")

  def subjects: Set[String] =
    (entries.map(_.callee) ++ entries.map(e => Placement.owner(e.placement)) ++ facadeMembers.toList)
      .map(MergeablePolicy.subjectOf).toSet

  def policyReport: PolicyReport = PolicyReport.fromBindings(records) ++ PolicyReport(shapeFindings)

  // ---- findings --------------------------------------------------------------------------------

  private val found = collection.mutable.ListBuffer.empty[RegistryCheck.Finding]

  /** every site this run refused or counted — read by `PortRun` after the pipeline. */
  def findings: List[RegistryCheck.Finding] = found.toList

  // ---- run state (a value THIS run owns — §5.1) -------------------------------------------------

  /** callee symbol → what it rewrites to. */
  private var mapping: Map[SymId, Rewritten]      = Map.empty
  /** call ORIGINS this run classified as rewritable — the site key, since a rebuilt tree is a new
    * node and a call's `(path, line, col)` is what stays put. */
  private var admitted: Set[Origin]               = Set.empty
  /** …the subset in units THIS module EMITS: the only sites it may report about (D2). */
  private var owned: Set[Origin]                  = Set.empty
  /** minted `create` symbol → entry index; how a `Tree.Try` recognises an already-rewritten call. */
  private var created: Map[SymId, Int]            = Map.empty
  /** entry index → the placement slot a finding names. */
  private var slotOf: Map[Int, String]            = Map.empty
  /** `Placement.Member` bodies, appended at the end of the owner's own body. */
  private var appendTo: Map[SymId, List[Statement]] = Map.empty

  override def run(program: Program): Program =
    found.clear()
    mapping  = Map.empty
    admitted = Set.empty
    owned    = Set.empty
    created  = Map.empty
    appendTo = Map.empty
    slotOf   = entries.zipWithIndex.map((e, i) => i -> Placement.slot(e.placement)).toMap
    if entries.isEmpty && facadeMembers.isEmpty then return program

    given Program = program

    // members of a THROWING facade: counted wherever they are called, never rewritten.
    if facadeCallees.nonEmpty then
      program.units.foreach { u =>
        val owner = program.symbolOf(u.symbol).map(_.fullName).getOrElse("")
        StandardTraversal.scanClassDef(u, ()) {
          case (_, t: Tree.Apply) if facadeCallees(t.method) =>
            found += RegistryCheck.Finding(RegistryCheck.Issue.Facade, owner,
              program.symbolOf(t.method).map(_.fullName).getOrElse("<facade member>") +
                " throws on every reflective path; no registry replaces it", t.origin, u.symbol)
          case (_, _) => ()
        }
      }

    // CLASSIFY every call site FIRST, once: a refusal is recorded here and nowhere else, and
    // minting is conditional on this run EMITTING a rewritable site (a minted unit belongs to ONE
    // module — `ENGINE-LIMITS.md` O5).
    val sites: List[Site] =
      for
        (i, callees) <- boundCallees.toList.sortBy(_._1)
        u            <- program.units
        a            <- StandardTraversal.scanClassDef(u, List.empty[Tree.Apply]) {
                          case (acc, t: Tree.Apply) if callees(t.method) => t :: acc
                          case (acc, _)                                  => acc
                        }
      yield Site(i, entries(i), a, u.symbol)

    // D2 splits the two questions. The REWRITE runs at every admissible site in scope, a base
    // unit's included: a dependent's `Program` contains its base's units, and a model in which the
    // base still calls the retired member reports the base's dropped type as this module's residue.
    // MINTING and every FINDING are fenced to units THIS module emits — a minted unit belongs to
    // ONE module (O5), and a module does not report on a declaration another module emits.
    val (mine, theirs) = sites.partition(s => runScope.emits(s.unit))
    val here       = mine.filter(s => admits(program, s, record = true))
    val rewritable = here ++ theirs.filter(s => admits(program, s, record = false))
    admitted = rewritable.map(_.call.origin).toSet
    owned    = here.map(_.call.origin).toSet
    if rewritable.isEmpty then return program

    // One row per declared non-JVM target, for each entry this module MINTS: `JvmReflect` compiles
    // there and answers the miss value for every type no seed and no consumer registered (P10).
    // Fenced on `here` for `out-of-scope`'s reason — a module does not report on a declaration
    // another module emits (D2).
    val offJvm = runScope.platform.targets.filterNot(_ == balticporter.catalog.Platform.Jvm)
      .toList.map(_.toString).sorted
    here.map(s => s.entryIx -> s.entry).distinct.filter((_, e) => e.miss.isInstanceOf[Miss.JvmReflect])
      .sortBy(_._1).foreach { (i, e) =>
        offJvm.foreach(t =>
          found += RegistryCheck.Finding(RegistryCheck.Issue.JvmOnlyMiss, slotOf(i),
            s"`${e.callee}` resolves through the JVM reflective miss arm; on $t it answers the " +
              "miss value for every type no `seeds` entry and no consumer registered", Origin.synthetic))
      }

    // MINT one registry per entry that has a rewritable site in THIS module.
    var table = program.symbols
    var next  = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    def mint(nm: String, full: String, owner: SymId, flags: Flags, info: TypeRepr = NoType): SymId =
      val id = SymId(next); next += 1
      table = table.updated(Symbol(id, nm, full, flags, owner, info))
      id

    var minted = List.empty[Tree.ClassDef]
    val mintsUnitHere = here.map(_.entryIx).toSet
    rewritable.map(s => s.entryIx -> s.entry).distinct.sortBy(_._1).foreach { (i, e) =>
      val sp       = Placement.spelling(e.placement)
      val ownerFqn = Placement.owner(e.placement)
      val isObject = e.placement.isInstanceOf[Placement.Object]
      val ownerSym =
        if isObject then
          mint(ownerFqn.substring(ownerFqn.lastIndexOf('.') + 1), ownerFqn, SymId.None, Flags(isModule = true))
        else program.symbols.all.find(_.fullName == ownerFqn).map(_.id).getOrElse(SymId.None)
      if ownerSym != SymId.None then
        // `create` carries the RETIRED CALLEE's own signature: it stands in the same slots, so a
        // call to it must read as the same arity to `signature` and to the xref.
        val calleeInfo = boundCallees(i).toList.sortBy(_.raw).flatMap(program.symbolOf).map(_.info)
          .headOption.getOrElse(NoType)
        val createSym = mint(sp.create, MemberKey(ownerFqn, sp.create).render, ownerSym, Flags(), calleeInfo)
        mint(sp.register, MemberKey(ownerFqn, sp.register).render, ownerSym, Flags(), withFactory(calleeInfo))
        mint(sp.table, MemberKey(ownerFqn, sp.table).render, ownerSym, Flags())
        created += createSym -> i
        boundCallees(i).foreach(c => mapping += c -> Rewritten(ownerSym, createSym, i))
        val body = memberSources(e, sp, isObject, boundSeeds.getOrElse(i, Nil))
        // `Origin.synthetic`, never a call site's java path: the trivia harvest is keyed by java
        // FILE, so a minted unit claiming `Engine.java` reports every comment in that file as
        // recovered-by-the-backstop (measured: `trivia(recovered)` 0 -> 17). The licence header
        // then reads `<unknown>` — the same answer `primitive-to-opaque`'s minted companion gives
        // (§4.57, `ENGINE-LIMITS.md` P10).
        if !mintsUnitHere(i) then () // the base emits the registry; this module only calls it
        else if isObject then minted = minted :+ Tree.ClassDef(ownerSym, Nil, scala.None, body, Origin.synthetic)
        else appendTo += ownerSym -> body
        if mintsUnitHere(i) then recordMembers(e, sp, ownerFqn, ownerSym)
    }

    // decision provenance per rewritten DECLARATION, read off the PRE-rewrite program (the only one
    // that still names the callee about to be replaced).
    here.map(s => s.entryIx -> s.entry).distinct.foreach { (i, e) =>
      val to = MemberKey(Placement.owner(e.placement), Placement.spelling(e.placement).create).render
      boundCallees(i).toList.sortBy(_.raw).foreach { callee =>
        // …at declarations THIS module emits: a decision is scoped to its own module (D2).
        Decision.declarationsUsing(program, callee)
          .filter((encl, _) => runScope.emitsSymbol(program, encl)).foreach { (encl, origin) =>
          record(Decision(
            kind       = Decision.Kind.RedirectedCall,
            subject    = encl,
            subjectFqn = Decision.fqnOf(program, encl, e.callee),
            detail     = Map(
              "from" -> e.callee,
              "to"   -> to,
              "miss" -> Miss.render(e.miss),
              "why"  -> ("reflective instantiation has no counterpart off the JVM, so this port " +
                "keys construction on the `Class` value through a registry it supplies itself"),
            ),
            reason = Reason.Configured(Name, s"${e.callee} -> $to"),
            origin = origin,
          ))
        }
      }
    }

    val walked = program.units.map(u => StandardTraversal.mapClassDef(this, u))
    program.rebuilt(walked ++ minted, table) // xref rebuilt by the Pipeline

  /** `register`'s signature: the callee's own, plus the factory it stores, answering `Unit`. */
  private def withFactory(info: TypeRepr): TypeRepr = info match
    case TypeRepr.PolyType(ps, r)       => TypeRepr.PolyType(ps, withFactory(r))
    case TypeRepr.MethodType(ps, _, im) => TypeRepr.MethodType(ps :+ ("factory" -> NoType), NoType, im)
    case other                          => other

  // ---- admission -------------------------------------------------------------------------------

  /** Can the registry key this call? A refusal is recorded exactly once per site, and only where
    * `record` says this module owns the site (D2). */
  private def admits(program: Program, s: Site, record: Boolean): Boolean =
    val e       = s.entry
    val subject = program.symbolOf(s.unit).map(_.fullName).getOrElse("")
    def refuse(i: RegistryCheck.Issue, why: String): Boolean =
      if record then found += RegistryCheck.Finding(i, subject, why, s.call.origin, s.unit)
      false
    if !e.scope.includes(subject) then
      refuse(RegistryCheck.Issue.OutOfScope,
        s"`${e.callee}` is called from `$subject`, which this entry's scope does not name")
    else
      s.call.args match
        case List(arg) =>
          if namedAt(program, arg, "forName") then
            refuse(RegistryCheck.Issue.ByName,
              s"`${e.callee}`'s class is chosen by a STRING at run time, so no registration can " +
                "exist for it — a name table (`ClassTableTransform`) is the mechanism for this")
          else if namedAt(program, arg, "getClass") && !e.miss.isInstanceOf[Miss.JvmReflect] then
            refuse(RegistryCheck.Issue.SelfClone,
              s"`${e.callee}(getClass())` clones an arbitrary subtype, and with miss=${e.miss} the " +
                "registry answers only for keys somebody registered")
          else if !isClassValue(program, arg.tpe) then
            refuse(RegistryCheck.Issue.NonClassArg,
              "the argument's type is not `java.lang.Class[…]`, so there is no key to look up")
          else true
        case other =>
          refuse(RegistryCheck.Issue.NonClassArg,
            s"`${e.callee}` is called with ${other.size} arguments; a registry keys on exactly one " +
              "`Class` value")

  private def isClassValue(program: Program, t: TypeRepr): Boolean =
    def head(x: TypeRepr): Option[SymId] = x match
      case TypeRepr.AppliedType(tc, _) => head(tc)
      case TypeRepr.TypeRef(_, sym)    => Some(sym)
      case _                           => scala.None
    head(t).flatMap(program.symbolOf).exists(_.fullName == "java.lang.Class")

  /** is this argument the result of a call to a member of that NAME (`forName`, `getClass`)? */
  private def namedAt(program: Program, t: Term, member: String): Boolean = t match
    case a: Tree.Apply          => program.symbolOf(a.method).exists(_.name == member)
    case Tree.Typed(e, _, _, _) => namedAt(program, e, member)
    case Tree.Commented(_, e)   => namedAt(program, e, member)
    case _                      => false

  // ---- the rewrite -----------------------------------------------------------------------------

  /** `callee(c)` → `<registry>.create(c)`, same argument and same result type, so every phase after
    * this one and every check read the call exactly as they read the java one. */
  override def transformApply(t: Tree.Apply)(using Program): Term =
    mapping.get(t.method) match
      case Some(r) if admitted(t.origin) =>
        val recv = Tree.Ident(r.owner, NoType, t.origin)
        Tree.Apply(Tree.Select(recv, r.create, NoType, t.origin), t.args, r.create, t.tpe, t.origin)
      case _ => t

  /** A `try` whose ONLY reason to exist was the callee this phase retired — every `catch` names a
    * type the entry DECLARES as that callee's failure — is replaced by its body: the thrower is
    * gone, and the entry's `miss` is what the port says a failed creation answers. Every other
    * `try` around a rewritten call is left as java wrote it and COUNTED. */
  override def transformTerm(t: Term)(using Program): Term = t match
    case tr: Tree.Try =>
      holdsRewrittenCall(tr.body) match
        case scala.None => tr
        case Some((i, at)) =>
          val declared = boundHandles.getOrElse(i, Set.empty)
          val dead = tr.resources.isEmpty && tr.finalizer.isEmpty && tr.catches.nonEmpty &&
            declared.nonEmpty && tr.catches.forall(c => caughtTypes(c.param.tpt.tpe).forall(declared))
          if dead then tr.body
          else
            // …reported only where THIS module emits the site (D2); the try is left alone either way.
            if owned(at) then
              found += RegistryCheck.Finding(RegistryCheck.Issue.GuardedCall, slotOf.getOrElse(i, ""),
                "the rewritten call sits inside a `try` this entry's `handles` does not describe, " +
                  "so java's handler is left over a callee that can no longer throw what it catches",
                tr.origin)
            tr
    case other => other

  /** the ONE rewritten `create` call this term contains — its entry and its site — if that is all
    * it holds. */
  private def holdsRewrittenCall(t: Term)(using Program): Option[(Int, Origin)] =
    if created.isEmpty then scala.None
    else
      StandardTraversal.scanTerm(t, List.empty[(Int, Origin)]) {
        case (acc, a: Tree.Apply) => created.get(a.method).map(i => (i, a.origin) :: acc).getOrElse(acc)
        case (acc, _)             => acc
      }.distinct match
        case List(one) => Some(one)
        case _         => scala.None

  /** every type a `catch` clause names — a multi-catch is an `OrType` on the parameter. */
  private def caughtTypes(t: TypeRepr): Set[SymId] = t match
    case TypeRepr.OrType(l, r)       => caughtTypes(l) ++ caughtTypes(r)
    case TypeRepr.TypeRef(_, s)      => Set(s)
    case TypeRepr.AppliedType(tc, _) => caughtTypes(tc)
    case _                           => Set.empty

  // ---- the minted members ----------------------------------------------------------------------

  /** `Placement.Member`'s members, appended at the end of the owner's own body. */
  override def transformClassDef(cd: Tree.ClassDef)(using Program): Tree.ClassDef =
    appendTo.get(cd.symbol) match
      case Some(ms) => cd.copy(body = cd.body ++ ms)
      case _        => cd

  private def recordMembers(e: Registry, sp: Spelling, ownerFqn: String, ownerSym: SymId): Unit =
    List(sp.table -> 0, sp.register -> 2, sp.create -> 1).foreach { (nm, arity) =>
      record(Decision(
        kind       = Decision.Kind.AddedMember,
        subject    = ownerSym,
        subjectFqn = ownerFqn,
        detail     = Map(
          "member" -> nm,
          "arity"  -> arity.toString,
          "why"    -> (s"the registry that replaces `${e.callee}`: reflective instantiation does " +
            "not exist off the JVM, so construction is keyed on the `Class` value instead"),
        ),
        reason = Reason.Configured(Name, s"${e.callee} -> ${MemberKey(ownerFqn, nm).render}"),
        origin = Origin.synthetic,
      ))
    }

  /** The registry's three members as verbatim Scala (fully qualified, no imports — CLAUDE.md §6),
    * plus one `locally` per seed. A plain `mutable.HashMap` and never a concurrent one:
    * `balticporter/runtime` ships no threading, so a concurrent table is not portable (P10). */
  private def memberSources(e: Registry, sp: Spelling, isObject: Boolean, seeds: List[SymId]): List[Statement] =
    val o      = Origin.synthetic
    val tpar   = e.bound.fold("[T]")(b => s"[T <: $b]")
    val access = if isObject then "private " else "protected "
    val table  = s"${access}val ${sp.table}: scala.collection.mutable.HashMap[Class[?], () => ?] = " +
      "scala.collection.mutable.HashMap.empty"
    val register = s"def ${sp.register}$tpar(componentType: Class[T], factory: () => T): Unit = " +
      s"${sp.table}.put(componentType, factory)"
    val create = s"def ${sp.create}$tpar(componentType: Class[T]): T = " +
      s"${sp.table}.get(componentType) match { case scala.Some(f) => f.asInstanceOf[() => T]() ; " +
      s"case scala.None => ${missArm(e.miss)} }"
    val seeded = seeds.map { s =>
      val ref = TypeRepr.TypeRef(TypeRepr.NoPrefix, s)
      Tree.Opaque(s"locally { ${sp.register}(${Tree.Opaque.hole(0)}, () => ${Tree.Opaque.hole(1)}) }",
        NoType, o, List(Tree.Literal(Constant.ClassOfC(ref), NoType, o), Tree.New(TypeTree(ref, o), ref, o)))
    }
    List(Tree.Opaque(table, NoType, o), Tree.Opaque(register, NoType, o),
         Tree.Opaque(create, NoType, o)) ++ seeded

  private def missArm(m: Miss): String = m match
    case Miss.Null => "null.asInstanceOf[T]"
    case Miss.Throw(fqn, msg) =>
      val q = '"'.toString
      s"throw new $fqn($q${msg.replace("\"", "\\\"")}$q + componentType.getName())"
    case Miss.JvmReflect(onFailure) =>
      // java's own answer where reflection FAILS, never a silent null unless the port says so.
      val failed = onFailure match
        case Miss.OnFailure.Null => "null.asInstanceOf[T]"
        case Miss.OnFailure.Throw(fqn, msg) =>
          val q = '"'.toString
          s"throw new $fqn($q${msg.replace("\"", "\\\"")}$q + componentType.getName())"
      "{ try componentType.getConstructor().newInstance() catch { " +
        "case _: java.lang.NoSuchMethodException | _: java.lang.InstantiationException | " +
        s"_: java.lang.IllegalAccessException => $failed ; " +
        "case ex: java.lang.reflect.InvocationTargetException => throw ex.getCause } }"

object RegistryTransform:

  val Name = "registry"

  /** One library's reflective-instantiation fact (P10): `callee` = bound `owner#member` keyed by its
    * `Class` argument; `placement` = where the minted members live; `scope` (`Only(Set.empty)` is the
    * no-op — this phase MINTS); `seeds` = upstream FQNs with a visible nilary ctor; `handles` = the
    * exception FQNs java's `catch` names; `miss` = an unregistered key's answer; `bound` = `T`'s bound. */
  final case class Registry(
      callee: String,
      placement: Placement,
      scope: RuleScope = RuleScope.Only(Set.empty),
      seeds: List[String] = Nil,
      handles: Set[String] = Set.empty,
      miss: Miss = Miss.Null,
      bound: Option[String] = None,
  )

  /** The registry's three member names — all emitted surface. */
  final case class Spelling(table: String, register: String, create: String)

  /** WHERE the registry lives. `Member` puts it on a type the port already emits (a
    * framework-instantiated owner takes it with NO constructor parameter, CT7); `Object` mints a
    * top-level `object`, written only by the module that emits the call sites (O5). */
  enum Placement:
    case Member(owner: String, spelling: Spelling)
    case Object(fqn: String, spelling: Spelling)

  object Placement:
    def spelling(p: Placement): Spelling = p match
      case Member(_, s) => s
      case Object(_, s) => s
    def owner(p: Placement): String = p match
      case Member(o, _) => o
      case Object(f, _) => f
    /** the DECLARATION this placement occupies — the merge key, and a finding's subject. */
    def slot(p: Placement): String = MemberKey(owner(p), spelling(p).table).render
    def render(p: Placement): String =
      val s = spelling(p)
      s"${owner(p)}:${s.table}/${s.register}/${s.create}"

  /** What an unregistered key answers. Three outcomes and not one: a port that must not throw, a
    * port whose contract has its own exception, and the JVM's own reflective answer, which carries
    * what java's OWN contract says when the reflection itself fails (P10's measured conflict). */
  enum Miss:
    case Null
    case Throw(fqn: String, message: String)
    case JvmReflect(onFailure: Miss.OnFailure = Miss.OnFailure.Null)

  object Miss:

    /** What a [[Miss.JvmReflect]] arm answers when REFLECTION fails — the type has no visible
      * nilary constructor, or its constructor threw. `Null` is what a java `catch` returning null
      * meant; `Throw` restates java's own wrapping exception at that site (P10 STOP (a)). */
    enum OnFailure:
      case Null
      case Throw(fqn: String, message: String)

    /** The SURFACE rendering. `JvmReflect` with the default `Null` failure renders as the string it
      * rendered before `onFailure` existed, so the parameter's arrival is flat on every port that
      * does not use it (§1(b)'s fingerprint no-op rule). */
    def render(m: Miss): String = m match
      case JvmReflect(OnFailure.Null) => "JvmReflect"
      case other                      => other.toString

  /** one classified call site. */
  private[transform] final case class Site(entryIx: Int, entry: Registry, call: Tree.Apply, unit: SymId)

  /** what a bound callee rewrites to. */
  private[transform] final case class Rewritten(owner: SymId, create: SymId, entry: Int)
