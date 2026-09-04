package balticporter.transform

import balticporter.core.{PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*
import balticporter.tir.TypeRepr.NoType

/** Re-points a runtime class lookup by name (`Class.forName`-shaped) at an explicit
  * name→class table the port supplies, since Scala.js/Native have no runtime class registry.
  * Rewrites `Wrapper.forName(s)` → `Table.classFor(s)`, same arguments and result type.
  * Keys/values are `owner#member`. A key naming no program member is a no-op; [[policyReport]]
  * reports it.
  */
final class ClassTableTransform(redirects: Map[String, String])
    extends Phase, PolicySource, SurfacePolicy, PolicyBound:
  def name: String = "class-table"

  /** What the run resolved each declared key to, before the pipeline started — the only thing
    * this phase learns about which members its keys name. CLAUDE.md §4.56 */
  private var bound: Map[String, Binding[List[PolicyBinder.Hit]]] = Map.empty
  private var records: List[PolicyBinder.Record] = Nil

  def bindPolicy(binder: PolicyBinder): Unit =
    bound = redirects.keys.toList.sorted
      .map(k => k -> binder.bindMembers(name, "ClassTableTransform(redirects) key", k)).toMap
    records = binder.recordsFor(name)

  /** The redirect table is part of the emitted surface a dependent module must match; sorted so
    * two agreeing manifests compare equal regardless of map iteration order. */
  def surfaceFingerprint: String = redirects.toList.sorted.map((k, v) => s"$k->$v").mkString(",")

  /** callee symbol → (table type symbol, table member symbol) */
  private var mapping: Map[SymId, (SymId, SymId)] = Map.empty

  /** Declared redirects that matched no member of this program, plus any whose value is not the
    * `owner#member` shape the rewrite needs. Complete once keys are bound, before [[run]]. */
  def policyReport: PolicyReport =
    PolicyReport.fromBindings(records) ++ PolicyReport(
      bound.toList.sortBy(_._1).collect {
        case (k, b) if b.isBound && !redirects(k).contains('#') =>
          PolicyFinding(name, "ClassTableTransform(redirects) value", k, PolicyIssue.Malformed,
            s"""the destination "${redirects(k)}" is not `owner#member`, so there is nothing to """ +
              "select — redirect skipped")
      })

  override def run(program: Program): Program =
    // keep the key alongside the hit: needed for the unmatched-key report, not recoverable from symbol ids alone.
    val hits = bound.toList.sortBy(_._1).flatMap { (k, b) =>
      b.toOption.getOrElse(Nil).flatMap(_.sym).map(id => (id, k, redirects(k)))
    }
    val wellFormed = hits.filter(_._3.contains('#'))

    val targets = wellFormed.map((id, _, d) => id -> d).sortBy(_._1.raw) // deterministic minting order
    if targets.isEmpty then program
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

      mapping = targets.map { (callee, dest) =>
        val at      = dest.lastIndexOf('#')
        val typeFqn = dest.substring(0, at)
        val member  = dest.substring(at + 1)
        // table is addressed as a value: an external type symbol with no info, rendered by FQN.
        val tableSym = intern(typeFqn.substring(typeFqn.lastIndexOf('.') + 1), typeFqn, SymId.None, NoType, Flags())
        val memberSym =
          intern(member, dest, tableSym, program.symbolOf(callee).map(_.info).getOrElse(NoType), Flags(isStatic = true))
        callee -> (tableSym, memberSym)
      }.toMap

      // decision provenance, one row per (declaration, redirect entry); recorded from the
      // pre-rewrite program, the only one that still names the callee about to be replaced.
      wellFormed.foreach { (callee, key, dest) =>
        val calleeFqn = program.symbolOf(callee).map(_.fullName).getOrElse(key)
        Decision.declarationsUsing(program, callee).foreach { (encl, origin) =>
          record(Decision(
            kind       = Decision.Kind.RedirectedCall,
            subject    = encl,
            subjectFqn = Decision.fqnOf(program, encl, calleeFqn),
            detail     = Map(
              "from" -> key,
              "to"   -> dest,
              "key"  -> key,
              "why"  -> ("a runtime class lookup by NAME has no counterpart off the JVM; this port " +
                "re-points it at an explicit name->class table it supplies itself"),
            ),
            reason = Reason.Configured(name, s"$key -> $dest"),
            origin = origin,
          ))
        }
      }

      given Program = program
      val units = program.units.map(u => StandardTraversal.mapClassDef(this, u))
      program.rebuilt(units, table) // xref rebuilt by the Pipeline

  /** `Wrapper.forName(s)` → `Table.classFor(s)` — same arguments, same result type. */
  override def transformApply(t: Tree.Apply)(using Program): Term =
    mapping.get(t.method) match
      case Some((tableSym, member)) =>
        val recv = Tree.Ident(tableSym, NoType, t.origin)
        Tree.Apply(Tree.Select(recv, member, NoType, t.origin), t.args, member, t.tpe, t.origin)
      case None => t
