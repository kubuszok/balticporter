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
final class ClassTableTransform(redirects: Map[String, String])
    extends Phase, PolicySource, SurfacePolicy, PolicyBound:
  def name: String = "class-table"

  /** What the RUN resolved each declared key to, before the pipeline started (§8.1) — and the ONLY
    * thing this phase is allowed to learn about which members its keys name. It used to reconstruct
    * `owner#name` from every symbol in the program and look that string up, which is the defect
    * §4.56 generalises: a name is not a structural fact about anything, and this one could not tell
    * three overloads of `Class#forName` apart. */
  private var bound: Map[String, Binding[List[PolicyBinder.Hit]]] = Map.empty
  private var records: List[PolicyBinder.Record] = Nil

  def bindPolicy(binder: PolicyBinder): Unit =
    bound = redirects.keys.toList.sorted
      .map(k => k -> binder.bindMembers(name, "ClassTableTransform(redirects) key", k)).toMap
    records = binder.recordsFor(name)

  /** Two ports that redirect different lookups produce different call sites for the same shared
    * code, so the redirect table is part of the emitted surface a dependent module has to match.
    * Sorted — an unsorted rendering would make two agreeing manifests compare unequal on a map's
    * iteration order. */
  def surfaceFingerprint: String = redirects.toList.sorted.map((k, v) => s"$k->$v").mkString(",")

  /** callee symbol → (table type symbol, table member symbol) */
  private var mapping: Map[SymId, (SymId, SymId)] = Map.empty

  /** Declared redirects that matched no member of this program, plus any whose VALUE is not the
    * `owner#member` shape the rewrite needs.
    *
    * A property of the POLICY and the PROGRAM, so it is complete the moment the keys are bound and
    * does not wait for [[run]] — which is what the private `var report` this replaces could not
    * say. A phase that never ran now reports the same thing a phase that ran and matched nothing
    * does, which is the truth in both cases. */
  def policyReport: PolicyReport =
    PolicyReport.fromBindings(records) ++ PolicyReport(
      bound.toList.sortBy(_._1).collect {
        case (k, b) if b.isBound && !redirects(k).contains('#') =>
          PolicyFinding(name, "ClassTableTransform(redirects) value", k, PolicyIssue.Malformed,
            s"""the destination "${redirects(k)}" is not `owner#member`, so there is nothing to """ +
              "select — redirect skipped")
      })

  override def run(program: Program): Program =
    // keep the KEY alongside the hit: "which declared keys fired" is exactly what an unmatched-key
    // report is the complement of, and it cannot be recovered from the symbol ids afterwards.
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
        // the table is addressed as a VALUE (a static-access receiver): an external type symbol
        // with no info, which the emitter renders by its fully-qualified name.
        val tableSym = intern(typeFqn.substring(typeFqn.lastIndexOf('.') + 1), typeFqn, SymId.None, NoType, Flags())
        val memberSym =
          intern(member, dest, tableSym, program.symbolOf(callee).map(_.info).getOrElse(NoType), Flags(isStatic = true))
        callee -> (tableSym, memberSym)
      }.toMap

      // DECISION PROVENANCE, one row per (DECLARATION, redirect entry) — see
      // `Decision.declarationsUsing` for why this is not one row per call site. Recorded from the
      // PRE-rewrite program, which is the only one that still names the callee this phase is about
      // to replace; after the traversal every site names the table member instead.
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
