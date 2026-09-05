package balticporter.transform

import balticporter.core.{MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*
import balticporter.tir.TypeRepr.NoType

/** Re-points a runtime class lookup by name (`Class.forName`-shaped) at an explicit
  * name→class table the port supplies, since Scala.js/Native have no runtime class registry.
  * Rewrites `Wrapper.forName(s)` → `Table.classFor(s)`, same arguments/result type. Keys/values are
  * `owner#member`; a key naming no program member is a no-op, reported by [[policyReport]]. */
final class ClassTableTransform private (private[transform] val entries: List[ClassTableTransform.Entry])
    extends Phase, PolicySource, SurfacePolicy, MergeablePolicy, PolicyBound:
  import ClassTableTransform.Entry

  /** @param redirects `owner#member` → `owner#member`
    * @param scope WHERE the redirect applies. A REDIRECT, so `Everywhere(Set.empty)` is both the
    *              no-op and the pre-scope code path (`.claude/rules/phases.md`); a site the scope
    *              leaves out keeps java's call, counted by `portability(emitted)`. */
  def this(redirects: Map[String, String], scope: RuleScope = RuleScope.everywhere) =
    this(redirects.toList.sortBy(_._1).map((k, v) => ClassTableTransform.Entry(k, v, scope)))

  def name: String = "class-table"

  /** What the run resolved each declared key to, before the pipeline started — the only thing
    * this phase learns about which members its keys name. CLAUDE.md §4.56 */
  private var bound: Map[String, Binding[List[PolicyBinder.Hit]]] = Map.empty
  private var records: List[PolicyBinder.Record] = Nil
  /** scope entries this run saw a call under — [[RuleScope.neverFired]]'s complement. */
  private var firedScope: Set[String] = Set.empty

  def bindPolicy(binder: PolicyBinder): Unit =
    bound = entries.map(_.from).distinct.sorted
      .map(k => k -> binder.bindMembers(name, "ClassTableTransform(redirects) key", k)).toMap
    records = binder.recordsFor(name)

  /** The redirect table is part of the emitted surface a dependent module must match; sorted so
    * two agreeing manifests compare equal regardless of map iteration order. An entry's scope
    * contributes NOTHING while it is the unrestricted default (§1(b)'s fingerprint no-op rule). */
  def surfaceFingerprint: String =
    entries.map(e => s"${e.from}->${e.to}" +
      (if e.scope.isUnrestricted then "" else s"[${e.scope.fingerprint}]")).sorted.mkString(",")

  /** Independent callees UNION. The SAME callee at a DIFFERENT table composes only where the two
    * scopes are DISJOINT — no site can then be claimed twice — and REFUSES where they overlap
    * (`ENGINE-LIMITS.md` P10, D12): two tables over one call is a conflict only a human resolves. */
  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case o: ClassTableTransform =>
      val conflicts = for
        e <- entries
        f <- o.entries
        if e.from == f.from && e.to != f.to && !RuleScope.disjoint(e.scope, f.scope)
      yield s"${e.from}: already redirected to `${e.to}` over an overlapping scope"
      if conflicts.nonEmpty then Left(conflicts.distinct.mkString("; "))
      else
        val merged = (entries ++ o.entries.filterNot(entries.contains)).sortBy(e => (e.from, e.to))
        Right(MergeablePolicy.Merged(new ClassTableTransform(merged),
          (o.subjects -- subjects)))
    case _ => Left(s"expected ClassTableTransform, got ${later.getClass.getSimpleName}")

  def subjects: Set[String] =
    entries.flatMap(e => List(e.from, e.to)).map(MergeablePolicy.subjectOf).toSet

  /** callee symbol → the entries that may redirect it, in declared order. */
  private var candidates: Map[SymId, List[Entry]] = Map.empty
  /** (callee, call site) → (table type symbol, table member symbol); one entry decided each. */
  private var admitted: Map[(SymId, Origin), (SymId, SymId)] = Map.empty

  /** Declared redirects that matched no member of this program, plus any whose value is not the
    * `owner#member` shape the rewrite needs, plus scope entries no call site was under. */
  def policyReport: PolicyReport =
    PolicyReport.fromBindings(records) ++ PolicyReport(
      entries.filter(e => bound.get(e.from).exists(_.isBound) && !e.to.contains('#')).map(e =>
        PolicyFinding(name, "ClassTableTransform(redirects) value", e.from, PolicyIssue.Malformed,
          s"""the destination "${e.to}" is not `owner#member`, so there is nothing to """ +
            "select — redirect skipped")) ++
      entries.flatMap(e => e.scope.neverFired(firedScope).toList.sorted.map(s =>
        PolicyFinding(name, "ClassTableTransform(scope)", s, PolicyIssue.NeverMatched,
          s"""no call of `${e.from}` is declared under "$s", so this scope entry decided nothing"""))
      ).distinct)

  override def run(program: Program): Program =
    firedScope = Set.empty
    candidates = Map.empty
    admitted   = Map.empty
    val wellFormed = entries.filter(_.to.contains('#'))
    // keep the key alongside the hit: needed for the unmatched-key report, not recoverable from symbol ids alone.
    wellFormed.foreach { e =>
      bound.get(e.from).flatMap(_.toOption).getOrElse(Nil).flatMap(_.sym).foreach(id =>
        candidates += id -> (candidates.getOrElse(id, Nil) :+ e))
    }
    if candidates.isEmpty then program
    else
      var table = program.symbols
      var next  = program.symbols.all.map(_.id.raw).max + 1

      /** reuse the interned symbol if the program already names it, otherwise mint one. */
      def intern(name: String, fullName: String, owner: SymId, info: TypeRepr, flags: Flags): SymId =
        table.all.find(_.fullName == fullName).map(_.id).getOrElse {
          val id = SymId(next); next += 1
          table = table.updated(Symbol(id, name, fullName, flags, owner, info))
          id
        }

      // deterministic minting order: by callee id, then by the entry's own declared strings.
      val targets = candidates.toList.sortBy(_._1.raw).flatMap((id, es) => es.map(id -> _))
      val destOf: Map[(SymId, Entry), (SymId, SymId)] = targets.map { (callee, e) =>
        val at      = e.to.lastIndexOf('#')
        val typeFqn = e.to.substring(0, at)
        val member  = e.to.substring(at + 1)
        // table is addressed as a value: an external type symbol with no info, rendered by FQN.
        val tableSym = intern(typeFqn.substring(typeFqn.lastIndexOf('.') + 1), typeFqn, SymId.None, NoType, Flags())
        val memberSym =
          intern(member, e.to, tableSym, program.symbolOf(callee).map(_.info).getOrElse(NoType), Flags(isStatic = true))
        (callee, e) -> (tableSym, memberSym)
      }.toMap

      // CLASSIFY every call site FIRST: the scope is read through the DECLARATION the call is in,
      // never through the call node, so a narrowed scope leaves a site java's own (§1(b)).
      program.units.foreach { u =>
        val subject = program.symbolOf(u.symbol).map(_.fullName).getOrElse("")
        StandardTraversal.scanClassDef(u, ()) {
          case (_, t: Tree.Apply) if candidates.contains(t.method) =>
            candidates(t.method).filter(_.scope.includes(subject)) match
              case e :: _ =>
                e.scope.entryFor(subject).foreach(s => firedScope += s)
                admitted += (t.method, t.origin) -> destOf((t.method, e))
              case Nil => ()
          case (_, _) => ()
        }
      }
      if admitted.isEmpty then return program

      // decision provenance, one row per (declaration, redirect entry); recorded from the
      // pre-rewrite program, the only one that still names the callee about to be replaced.
      targets.foreach { (callee, e) =>
        val calleeFqn = program.symbolOf(callee).map(_.fullName).getOrElse(e.from)
        Decision.declarationsUsing(program, callee)
          .filter((encl, _) => program.symbolOf(encl).exists(s => e.scope.includes(program, s)))
          .foreach { (encl, origin) =>
            record(Decision(
              kind       = Decision.Kind.RedirectedCall,
              subject    = encl,
              subjectFqn = Decision.fqnOf(program, encl, calleeFqn),
              detail     = Map(
                "from" -> e.from,
                "to"   -> e.to,
                "key"  -> e.from,
                "why"  -> ("a runtime class lookup by NAME has no counterpart off the JVM; this port " +
                  "re-points it at an explicit name->class table it supplies itself"),
              ),
              reason = Reason.Configured(name, s"${e.from} -> ${e.to}"),
              origin = origin,
            ))
          }
      }

      given Program = program
      val units = program.units.map(u => StandardTraversal.mapClassDef(this, u))
      program.rebuilt(units, table) // xref rebuilt by the Pipeline

  /** `Wrapper.forName(s)` → `Table.classFor(s)` — same arguments, same result type. */
  override def transformApply(t: Tree.Apply)(using Program): Term =
    admitted.get((t.method, t.origin)) match
      case Some((tableSym, member)) =>
        val recv = Tree.Ident(tableSym, NoType, t.origin)
        Tree.Apply(Tree.Select(recv, member, NoType, t.origin), t.args, member, t.tpe, t.origin)
      case None => t

object ClassTableTransform:

  /** One redirect: `from` and `to` are `owner#member`, `scope` is WHERE it applies. A list and not
    * a map, so one callee can carry two tables over disjoint scopes (`ENGINE-LIMITS.md` P10). */
  final case class Entry(from: String, to: String, scope: RuleScope)
