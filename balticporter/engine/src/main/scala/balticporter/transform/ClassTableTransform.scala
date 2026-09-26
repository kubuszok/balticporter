package balticporter.transform

import balticporter.core.{ MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy }
import balticporter.tir.*
import balticporter.tir.TypeRepr.NoType

/** Re-points a runtime class lookup by name (`Class.forName`-shaped) at an explicit name→class table, since Scala.js/Native have no runtime class registry. Rewrites `Wrapper.forName(s)` →
  * `Table.classFor(s)`, same arguments/result type. Keys/values are `owner#member`; a key naming no program member is a no-op, reported by [[policyReport]]. The table is either the port's own
  * (nothing minted) or a declared [[ClassTableTransform.Table]], MINTED as an object from its seed list; an empty table list mints nothing.
  */
final class ClassTableTransform private (
  private[transform] val entries: List[ClassTableTransform.Entry],
  /** the tables this phase MINTS, by placement. Empty = every redirect points at a table the port supplies itself. */
  val tables: List[ClassTableTransform.Table]
) extends Phase,
      PolicySource,
      SurfacePolicy,
      MergeablePolicy,
      PolicyBound:
  import ClassTableTransform.{ Entry, Table }

  /** @param redirects
    *   `owner#member` → `owner#member`
    * @param scope
    *   WHERE the redirect applies. A REDIRECT, so `Everywhere(Set.empty)` is both the no-op and the pre-scope code path (`.claude/rules/phases.md`); a site the scope leaves out keeps java's call,
    *   counted by `portability(emitted)`.
    */
  def this(redirects: Map[String, String], scope: RuleScope = RuleScope.everywhere) =
    this(ClassTableTransform.entriesOf(redirects, scope), Nil)

  /** As above, plus the tables to MINT: a redirect whose destination owner is a table's placement calls the minted object. */
  def this(redirects: Map[String, String], scope: RuleScope, tables: List[ClassTableTransform.Table]) =
    this(ClassTableTransform.entriesOf(redirects, scope), tables.sortBy(_.placement))

  def name: String = "class-table"

  /** What the run resolved each declared key to, before the pipeline started — the only thing this phase learns about which members its keys name.
    */
  private var bound:   Map[String, Binding[List[PolicyBinder.Hit]]] = Map.empty
  private var records: List[PolicyBinder.Record]                    = Nil

  /** placement → the seeds that bound, in declared order, with the FQN each was declared by. */
  private var boundSeeds: Map[String, List[(String, SymId)]] = Map.empty

  /** placement → the seeds a construction lambda can be written for (a subset of [[boundSeeds]]). */
  private var constructible: Map[String, Set[SymId]] = Map.empty

  /** the seed and placement refusals, decided at bind time from the declarations. */
  private var tableFindings: List[PolicyFinding] = Nil
  private var runScope:      RunScope            = RunScope.whole

  /** scope entries this run saw a call under — [[RuleScope.neverFired]]'s complement. */
  private var firedScope: Set[String] = Set.empty

  def bindPolicy(binder: PolicyBinder): Unit =
    runScope = binder.run
    bound = entries.map(_.from).distinct.sorted.map(k => k -> binder.bindMembers(name, "ClassTableTransform(redirects) key", k)).toMap
    val p = binder.program
    boundSeeds = tables.map { t =>
      t.placement -> t.seeds.flatMap(s => binder.bindType(name, s"ClassTableTransform(tables).seeds", s, Ownership.Either).toOption.map(s -> _))
    }.toMap
    records = binder.recordsFor(name)
    val collisions = tables
      .filter(t => p.symbols.all.exists(s => s.fullName == t.placement))
      .map(t =>
        PolicyFinding(
          name,
          "ClassTableTransform(tables).placement",
          t.placement,
          PolicyIssue.Unverifiable,
          s"`${t.placement}` is already a declaration of this program, so a table cannot be minted there — nothing minted"
        )
      )
    val seedRefusals = tables.flatMap { t =>
      boundSeeds(t.placement)
        .flatMap { (fqn, sym) =>
          ClassTableTransform.unnameable(p, sym, t.placement).map(why => fqn -> why).toList ++
            (if t.construct.isEmpty || ClassTableTransform.unnameable(p, sym, t.placement).nonEmpty then Nil
             else ClassTableTransform.unconstructible(p, sym, t.placement).map(why => fqn -> why).toList)
        }
        .map { (fqn, why) =>
          PolicyFinding(
            name,
            s"ClassTableTransform(tables).seeds",
            fqn,
            PolicyIssue.Unverifiable,
            s"$why — refused for the table at `${t.placement}`"
          )
        }
    }
    tableFindings = collisions ++ seedRefusals
    constructible = tables.map { t =>
      t.placement -> boundSeeds(t.placement).collect {
        case (_, s) if ClassTableTransform.unnameable(p, s, t.placement).isEmpty && ClassTableTransform.unconstructible(p, s, t.placement).isEmpty => s
      }.toSet
    }.toMap

  /** The redirect table and the minted tables are part of the emitted surface a dependent module must match; sorted so two agreeing manifests compare equal regardless of map iteration order. An
    * entry's scope contributes NOTHING while it is the unrestricted default, and an empty table list contributes no segment.
    */
  def surfaceFingerprint: String =
    entries
      .map(e =>
        s"${e.from}->${e.to}" +
          (if e.scope.isUnrestricted then "" else s"[${e.scope.fingerprint}]")
      )
      .sorted
      .mkString(",") +
      (if tables.isEmpty then "" else tables.map(Table.render).sorted.mkString(";tables:", ",", ""))

  /** Independent callees UNION. The SAME callee at a DIFFERENT table composes only where the two scopes are DISJOINT — no site can then be claimed twice — and REFUSES where they overlap: two tables
    * over one call is a conflict only a human resolves. Two different tables at ONE placement refuse the same way.
    */
  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case o: ClassTableTransform =>
      val conflicts = (for
        e <- entries
        f <- o.entries
        if e.from == f.from && e.to != f.to && !RuleScope.disjoint(e.scope, f.scope)
      yield s"${e.from}: already redirected to `${e.to}` over an overlapping scope") ++
        (for
          t <- tables
          u <- o.tables
          if t.placement == u.placement && t != u
        yield s"${t.placement}: already carries a different table")
      if conflicts.nonEmpty then Left(conflicts.distinct.mkString("; "))
      else
        val merged = (entries ++ o.entries.filterNot(entries.contains)).sortBy(e => (e.from, e.to))
        val ts     = (tables ++ o.tables.filterNot(tables.contains)).sortBy(_.placement)
        Right(MergeablePolicy.Merged(new ClassTableTransform(merged, ts), o.subjects -- subjects))
    case _ => Left(s"expected ClassTableTransform, got ${later.getClass.getSimpleName}")

  def subjects: Set[String] =
    (entries.flatMap(e => List(e.from, e.to)) ++ tables.map(_.placement)).map(MergeablePolicy.subjectOf).toSet

  /** callee symbol → the entries that may redirect it, in declared order. */
  private var candidates: Map[SymId, List[Entry]] = Map.empty

  /** (callee, call site) → (table type symbol, table member symbol); one entry decided each. */
  private var admitted: Map[(SymId, Origin), (SymId, SymId)] = Map.empty

  /** a redirect into a minted table must name one of the members the table mints. */
  private def missesMintedMember(e: Entry): Option[Table] =
    val at = e.to.lastIndexOf('#')
    if at < 0 then scala.None
    else tables.find(_.placement == e.to.substring(0, at)).filterNot(t => t.members.contains(e.to.substring(at + 1)))

  /** Declared redirects that matched no member of this program, plus any whose value is not the `owner#member` shape the rewrite needs, plus scope entries no call site was under, plus every refused
    * seed and placement of a minted table.
    */
  def policyReport: PolicyReport =
    PolicyReport.fromBindings(records) ++ PolicyReport(
      entries
        .filter(e => bound.get(e.from).exists(_.isBound) && !e.to.contains('#'))
        .map(e =>
          PolicyFinding(
            name,
            "ClassTableTransform(redirects) value",
            e.from,
            PolicyIssue.Malformed,
            s"""the destination "${e.to}" is not `owner#member`, so there is nothing to """ +
              "select — redirect skipped"
          )
        ) ++
        entries.flatMap(e =>
          missesMintedMember(e).map(t =>
            PolicyFinding(
              name,
              "ClassTableTransform(redirects) value",
              e.from,
              PolicyIssue.Malformed,
              s"""the destination "${e.to}" is inside the minted table `${t.placement}`, which mints only ${t.members.mkString("`", "`, `", "`")} — redirect skipped"""
            )
          )
        ) ++
        entries
          .flatMap(e =>
            e.scope
              .neverFired(firedScope)
              .toList
              .sorted
              .map(s =>
                PolicyFinding(
                  name,
                  "ClassTableTransform(scope)",
                  s,
                  PolicyIssue.NeverMatched,
                  s"""no call of `${e.from}` is declared under "$s", so this scope entry decided nothing"""
                )
              )
          )
          .distinct ++
        tableFindings
    )

  override def run(program: Program): Program =
    firedScope = Set.empty
    candidates = Map.empty
    admitted = Map.empty
    val wellFormed = entries.filter(e => e.to.contains('#') && missesMintedMember(e).isEmpty)
    // keep the key alongside the hit: needed for the unmatched-key report, not recoverable from symbol ids alone.
    wellFormed.foreach { e =>
      bound.get(e.from).flatMap(_.toOption).getOrElse(Nil).flatMap(_.sym).foreach(id => candidates += id -> (candidates.getOrElse(id, Nil) :+ e))
    }
    if candidates.isEmpty then program
    else
      var table     = program.symbols
      var next      = program.symbols.all.map(_.id.raw).max + 1
      given Program = program

      /** reuse the interned symbol if the program already names it, otherwise mint one. */
      def intern(name: String, fullName: String, owner: SymId, info: TypeRepr, flags: Flags): SymId =
        table.all.find(_.fullName == fullName).map(_.id).getOrElse {
          val id = SymId(next); next += 1
          table = table.updated(Symbol(id, name, fullName, flags, owner, info))
          id
        }

      // a minted table's placement is an OBJECT this phase declares, so its symbol is minted as one before
      // any redirect interns it as a plain external; a placement the program already declares mints nothing.
      val declared = program.symbols.all.map(_.fullName).toSet
      val mintable = tables.filterNot(t => declared(t.placement))
      mintable.foreach(t => intern(t.placement.substring(t.placement.lastIndexOf('.') + 1), t.placement, SymId.None, NoType, Flags(isModule = true)))

      // deterministic minting order: by callee id, then by the entry's own declared strings.
      val targets = candidates.toList.sortBy(_._1.raw).flatMap((id, es) => es.map(id -> _))
      val destOf: Map[(SymId, Entry), (SymId, SymId)] = targets.map { (callee, e) =>
        val at      = e.to.lastIndexOf('#')
        val typeFqn = e.to.substring(0, at)
        val member  = e.to.substring(at + 1)
        // table is addressed as a value: an external type symbol with no info, rendered by FQN.
        val tableSym  = intern(typeFqn.substring(typeFqn.lastIndexOf('.') + 1), typeFqn, SymId.None, NoType, Flags())
        val memberSym =
          intern(member, e.to, tableSym, program.symbolOf(callee).map(_.info).getOrElse(NoType), Flags(isStatic = true))
        (callee, e) -> (tableSym, memberSym)
      }.toMap

      // CLASSIFY every call site FIRST: the scope is read through the DECLARATION the call is in,
      // never through the call node, so a narrowed scope leaves a site java's own.
      var sitesAt = Map.empty[SymId, List[SymId]] // table symbol → the units whose sites call it
      program.units.foreach { u =>
        val subject = program.symbolOf(u.symbol).map(_.fullName).getOrElse("")
        StandardTraversal.scanClassDef(u, ()) {
          case (_, t: Tree.Apply) if candidates.contains(t.method) =>
            candidates(t.method).filter(_.scope.includes(subject)) match
              case e :: _ =>
                e.scope.entryFor(subject).foreach(s => firedScope += s)
                val dest = destOf((t.method, e))
                admitted += (t.method, t.origin) -> dest
                sitesAt += dest._1 -> (u.symbol :: sitesAt.getOrElse(dest._1, Nil))
              case Nil => ()
          case (_, _) => ()
        }
      }
      if admitted.isEmpty then return program

      // decision provenance, one row per (declaration, redirect entry); recorded from the
      // pre-rewrite program, the only one that still names the callee about to be replaced.
      targets.foreach { (callee, e) =>
        val calleeFqn = program.symbolOf(callee).map(_.fullName).getOrElse(e.from)
        Decision.declarationsUsing(program, callee).filter((encl, _) => program.symbolOf(encl).exists(s => e.scope.includes(program, s))).foreach { (encl, origin) =>
          record(
            Decision(
              kind = Decision.Kind.RedirectedCall,
              subject = encl,
              subjectFqn = Decision.fqnOf(program, encl, calleeFqn),
              detail = Map(
                "from" -> e.from,
                "to" -> e.to,
                "key" -> e.from,
                "why" -> ("a runtime class lookup by NAME has no counterpart off the JVM; this port " +
                  "re-points it at an explicit name->class table it supplies itself")
              ),
              reason = Reason.Configured(name, s"${e.from} -> ${e.to}"),
              origin = origin
            )
          )
        }
      }

      // MINT each table a redirect reaches — only in the module that emits its call sites, and only where no
      // unit this run does NOT emit calls it too (that module, the base, mints it: a minted unit has ONE writer).
      val minted = mintable.flatMap { t =>
        table.all.find(_.fullName == t.placement).map(_.id).filter(sitesAt.contains).flatMap { sym =>
          val units = sitesAt(sym)
          if units.exists(u => !runScope.emits(u)) then scala.None
          else
            t.members.foreach { m =>
              intern(m, MemberKey(t.placement, m).render, sym, NoType, Flags(isStatic = true))
              record(
                Decision(
                  kind = Decision.Kind.AddedMember,
                  subject = sym,
                  subjectFqn = t.placement,
                  detail = Map(
                    "member" -> m,
                    "arity" -> "1",
                    "why" -> ("the name->class table a runtime class lookup by name is re-pointed at: no runtime " +
                      "class registry exists off the JVM, so the port lists the classes it can name")
                  ),
                  reason = Reason.Configured(name, MemberKey(t.placement, m).render),
                  origin = Origin.synthetic
                )
              )
            }
            val seeds = boundSeeds.getOrElse(t.placement, Nil).map(_._2).filter(s => ClassTableTransform.unnameable(program, s, t.placement).isEmpty)
            val built = seeds.filter(constructible.getOrElse(t.placement, Set.empty))
            Some(Tree.ClassDef(sym, Nil, scala.None, ClassTableTransform.memberSources(t, seeds, built), Origin.synthetic))
        }
      }

      val units = program.units.map(u => StandardTraversal.mapClassDef(this, u))
      program.rebuilt(units ++ minted, table) // xref rebuilt by the Pipeline

  /** `Wrapper.forName(s)` → `Table.classFor(s)` — same arguments, same result type. */
  override def transformApply(t: Tree.Apply)(using Program): Term =
    admitted.get((t.method, t.origin)) match
      case Some((tableSym, member)) =>
        val recv = Tree.Ident(tableSym, NoType, t.origin)
        Tree.Apply(Tree.Select(recv, member, NoType, t.origin), t.args, member, t.tpe, t.origin)
      case None => t

object ClassTableTransform:

  /** One redirect: `from` and `to` are `owner#member`, `scope` is WHERE it applies. A list and not a map, so one callee can carry two tables over disjoint scopes.
    */
  final case class Entry(from: String, to: String, scope: RuleScope)

  /** A table this phase MINTS as `object <placement>`: `lookup(name): Class[?]` over the `seeds` (upstream type FQNs, keyed by each class's RUNTIME name), and, when `construct` is set,
    * `construct(name): Object` building a fresh instance through its no-argument constructor. An unknown name throws java's `ClassNotFoundException`, or the library's own wrapper around it
    * ([[Missing]]).
    */
  final case class Table(
    placement: String,
    seeds:     List[String],
    lookup:    String = "classFor",
    construct: Option[String] = None,
    missing:   Option[Missing] = None
  ):
    def members: List[String] = lookup :: construct.toList

  object Table:
    /** the SURFACE rendering: every field decides an emitted signature or emitted behaviour. */
    def render(t: Table): String =
      s"${t.placement}:${t.lookup}" + t.construct.fold("")(c => s"/$c") + t.seeds.sorted.mkString("(", ",", ")") +
        t.missing.fold("")(m => s"!${m.exception}(${m.notFound}|${m.notInstantiable})")

  /** How the library's own API WRAPS the reflective failure: `new <exception>(<notFound> + name, new java.lang.ClassNotFoundException(name))`, and the same with `notInstantiable` around a
    * `java.lang.InstantiationException` for a listed class with no factory. `exception` is the EMITTED Scala FQN with a `(String, Throwable)` constructor.
    */
  final case class Missing(exception: String, notFound: String = "", notInstantiable: String = "")

  private[transform] def entriesOf(redirects: Map[String, String], scope: RuleScope): List[Entry] =
    redirects.toList.sortBy(_._1).map((k, v) => Entry(k, v, scope))

  /** the package a type FQN is declared in — the part before the top-level type's own name. */
  private def packageOf(fqn: String): String =
    val top = fqn.takeWhile(_ != '$')
    top.substring(0, math.max(top.lastIndexOf('.'), 0))

  /** why the minted object cannot NAME this class (`classOf[X]`), if it cannot: a private class, or a package-private one in another package. */
  private[transform] def unnameable(p: Program, sym: SymId, placement: String): Option[String] =
    p.symbolOf(sym).flatMap { s =>
      if s.flags.isPrivate then Some(s"`${s.fullName}` is private, so the table cannot name it")
      else if s.flags.isPackagePrivate && packageOf(s.fullName) != packageOf(placement) then Some(s"`${s.fullName}` is package-private in another package than the table, so the table cannot name it")
      else scala.None
    }

  /** why the minted object cannot write `() => new X` for this class, if it cannot — there is no VALUE to build without a constructor the table can call with no arguments. */
  private[transform] def unconstructible(p: Program, sym: SymId, placement: String): Option[String] =
    p.symbolOf(sym).flatMap { s =>
      val inner = !s.flags.isStatic && s.fullName.contains('$') && p.symbolOf(s.owner).exists(o => !o.flags.isModule && !o.flags.isTrait)
      if s.flags.isAbstract || s.flags.isTrait || s.flags.isEnum || s.flags.isAnnotation then Some(s"`${s.fullName}` is abstract, an interface or an enum, so it has no instance to construct")
      else if inner then Some(s"`${s.fullName}` is an inner class, whose constructor needs an enclosing instance the table does not have")
      else
        val ctors = p.symbols.all.filter(c =>
          c.owner == sym && c.name == "<init>" && (c.info match
            case TypeRepr.MethodType(Nil, _, _) => true
            case _                              => false)
        )
        val callable = ctors.exists(c => !c.flags.isPrivate && !c.flags.isProtected && (!c.flags.isPackagePrivate || packageOf(s.fullName) == packageOf(placement)))
        if callable then scala.None
        else Some(s"`${s.fullName}` declares no no-argument constructor the table can call (none, or not accessible from `$placement`)")
    }

  /** The table's members as verbatim Scala (fully qualified, no imports). `classOf[X]` and a `() => new X` lambda, so LISTING a class never runs its initialiser; a plain immutable map, since
    * `balticporter/runtime` ships no threading.
    */
  private def memberSources(t: Table, seeds: List[SymId], built: List[SymId]): List[Statement] =
    val o = Origin.synthetic
    def ref(s:     SymId) = TypeRepr.TypeRef(TypeRepr.NoPrefix, s)
    def classOf(s: SymId) = Tree.Literal(Constant.ClassOfC(ref(s)), NoType, o)
    def str(s:     String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    def fail(cause: String, msg: Missing => String): String =
      val raw = s"new java.lang.$cause(name)"
      t.missing.fold(raw)(m => s"new ${m.exception}(${str(msg(m))} + name, $raw)")
    val notFound = fail("ClassNotFoundException", _.notFound)
    val classes  = Tree.Opaque(
      "private val bpClassTable: scala.collection.immutable.Map[java.lang.String, java.lang.Class[?]] = " +
        "scala.collection.immutable.List[java.lang.Class[?]](" + seeds.indices.map(Tree.Opaque.hole).mkString(", ") + ")" +
        ".map(c => (c.getName, c)).toMap",
      NoType,
      o,
      seeds.map(classOf)
    )
    val lookup = Tree.Opaque(
      s"def ${t.lookup}(name: java.lang.String): java.lang.Class[?] = bpClassTable.getOrElse(name, throw $notFound)",
      NoType,
      o
    )
    val construct = t.construct.toList.flatMap { c =>
      val factories = Tree.Opaque(
        "private val bpFactoryTable: scala.collection.immutable.Map[java.lang.String, () => java.lang.Object] = " +
          "scala.collection.immutable.Map[java.lang.String, () => java.lang.Object](" +
          built.indices.map(i => s"(${Tree.Opaque.hole(2 * i)}.getName, () => ${Tree.Opaque.hole(2 * i + 1)})").mkString(", ") + ")",
        NoType,
        o,
        built.flatMap(s => List(classOf(s), Tree.New(TypeTree(ref(s), o), ref(s), o)))
      )
      val make = Tree.Opaque(
        s"def $c(name: java.lang.String): java.lang.Object = bpFactoryTable.get(name) match { " +
          "case scala.Some(f) => f() ; " +
          s"case scala.None => if (bpClassTable.contains(name)) throw ${fail("InstantiationException", _.notInstantiable)} else throw $notFound }",
        NoType,
        o
      )
      List(factories, make)
    }
    classes :: lookup :: construct
