package balticporter.transform

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
  */
final class ClassTableTransform(redirects: Map[String, String]) extends Phase:
  def name: String = "class-table"

  /** callee symbol → (table type symbol, table member symbol) */
  private var mapping: Map[SymId, (SymId, SymId)] = Map.empty

  override def run(program: Program): Program =
    val targets = program.symbols.all.toList
      .flatMap { s =>
        program.symbolOf(s.owner).map(o => s"${o.fullName}#${s.name}").flatMap(redirects.get).map(s.id -> _)
      }
      .sortBy(_._1.raw) // deterministic minting order
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
