package balticporter.transform

import balticporter.core.{PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*
import balticporter.tir.TypeRepr.NoType

/** Re-points a runtime class LOOKUP BY NAME at an explicit name→class table supplied by the port.
  *
  * `Class.forName(s)` — and every wrapper that forwards to it, such as libGDX's
  * `ClassReflection.forName` — asks the RUNTIME to turn a string into a class. Scala.js and Scala
  * Native have no such runtime: there is no class registry to consult, and the linker prunes any
  * type nothing statically mentions. So a port that must round-trip a type through a persisted
  * string has to state the candidate set up front — a table populated with `classOf[…]` literals,
  * which every backend resolves at COMPILE time.
  *
  * That is the whole rewrite this phase performs: `Wrapper.forName(s)` → `Table.classFor(s)`, one
  * static call swapped for another with the same shape, so nothing around the call site changes —
  * the argument, the result type, and the exception the caller already catches all stay as they
  * were. Supplying the table itself is the port's job (an injected object listing the types it can
  * name); this phase only makes the emitted code ask it instead of the runtime.
  *
  * Keys and values are `owner#member`, e.g.
  * {{{
  * new ClassTableTransform(Map(
  *   "com.badlogic.gdx.utils.reflect.ClassReflection#forName" ->
  *     "com.badlogic.gdx.graphics.g3d.particles.AssetTypeRegistry#classFor"
  * ))
  * }}}
  *
  * Mints proper symbols for the table and its member, so the xref, [[RewriteTrace]] and
  * [[PortabilityCheck]] all see the call for what it now is rather than for what it was.
  *
  * A key naming a member the program does not have is a NO-OP — the lookup stays reflective and
  * the port stays JVM-only, with nothing said. [[policyReport]] is what says it.
  */
final class ClassTableTransform(redirects: Map[String, String]) extends Phase, PolicySource, SurfacePolicy:
  def name: String = "class-table"

  /** Two ports that redirect different lookups produce different call sites for the same shared
    * code, so the redirect table is part of the emitted surface a dependent module has to match.
    * Sorted — an unsorted rendering would make two agreeing manifests compare unequal on a map's
    * iteration order. */
  def surfaceFingerprint: String = redirects.toList.sorted.map((k, v) => s"$k->$v").mkString(",")

  /** callee symbol → (table type symbol, table member symbol) */
  private var mapping: Map[SymId, (SymId, SymId)] = Map.empty

  private var report: PolicyReport = PolicyReport.empty

  /** Declared redirects that matched no member of this program, plus any whose VALUE is not the
    * `owner#member` shape the rewrite needs. Reflects the last [[run]]; empty before the first,
    * and empty for an empty policy. */
  def policyReport: PolicyReport = report

  override def run(program: Program): Program =
    // keep the KEY alongside the hit: "which declared keys fired" is exactly what an unmatched-key
    // report is the complement of, and it cannot be recovered from the symbol ids afterwards.
    val hits = program.symbols.all.toList.flatMap { s =>
      program.symbolOf(s.owner).map(o => s"${o.fullName}#${s.name}").flatMap(k => redirects.get(k).map(d => (s.id, k, d)))
    }
    val (wellFormed, malformed) = hits.partition(_._3.contains('#'))
    val fired    = hits.map(_._2).toSet
    val unmatched = (redirects.keySet -- fired).toList.sorted.map { k =>
      PolicyFinding(name, "ClassTableTransform(redirects) key", k, PolicyIssue.NeverMatched,
        "no member `owner#name` of that name occurs in this program, so the runtime class lookup " +
          "was left in place")
    }
    report = PolicyReport(unmatched ++ malformed.map { (_, k, d) =>
      PolicyFinding(name, "ClassTableTransform(redirects) value", k, PolicyIssue.Malformed,
        s"""the destination "$d" is not `owner#member`, so there is nothing to select — redirect skipped""")
    }.distinct)

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
        // the table is addressed as a VALUE (a static-access receiver): an external type symbol
        // with no info, which the emitter renders by its fully-qualified name.
        val tableSym = intern(typeFqn.substring(typeFqn.lastIndexOf('.') + 1), typeFqn, SymId.None, NoType, Flags())
        val memberSym =
          intern(member, dest, tableSym, program.symbolOf(callee).map(_.info).getOrElse(NoType), Flags(isStatic = true))
        callee -> (tableSym, memberSym)
      }.toMap

      given Program = program
      val units = program.units.map(u => StandardTraversal.mapClassDef(this, u))
      new Program(units, table, program.xref) // xref rebuilt by the Pipeline

  /** `Wrapper.forName(s)` → `Table.classFor(s)` — same arguments, same result type. */
  override def transformApply(t: Tree.Apply)(using Program): Term =
    mapping.get(t.method) match
      case Some((tableSym, member)) =>
        val recv = Tree.Ident(tableSym, NoType, t.origin)
        Tree.Apply(Tree.Select(recv, member, NoType, t.origin), t.args, member, t.tpe, t.origin)
      case None => t
