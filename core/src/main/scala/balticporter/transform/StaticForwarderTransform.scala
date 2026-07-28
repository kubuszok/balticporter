package balticporter.transform

import balticporter.tir.*
import balticporter.tir.TypeRepr.NoType

/** Inline a STATIC FORWARDER: `Wrapper.m(x, rest…)` → `x.m(rest…)`.
  *
  * A Java library that must swap implementations per platform routes calls through a static
  * wrapper class whose methods take the real receiver as their first argument. Where the wrapper
  * merely forwards, the wrapper is a dependency the port does not need — and often one it CANNOT
  * have, because the platform-specific implementation is exactly what a Scala.js / Native target
  * lacks. Rewriting those sites to the direct member call removes the dependency without changing
  * behaviour or any signature.
  *
  * The mechanism is general; WHICH class forwards WHICH members to WHICH receiver is a fact about
  * one library, so it is supplied by that library's manifest ([[Forwarder]]) rather than written
  * here. An empty policy makes this phase a no-op.
  *
  * What still references the wrapper afterwards is exactly the irreducible residue — the members
  * that are not plain forwarding and need a real per-library replacement. Keeping that residue
  * small and EXPLICIT is the point; this phase never hides a site it cannot honestly rewrite.
  *
  * Mints proper `<receiver>#<member>` symbols rather than re-using the wrapper's, so the xref and
  * [[PortabilityCheck]] see each call for what it now is.
  */
final class StaticForwarderTransform(forwarders: List[StaticForwarderTransform.Forwarder]) extends Phase:
  def name: String = "static-forwarder-inline"

  private var mapping: Map[SymId, SymId] = Map.empty

  override def run(program: Program): Program =
    // (wrapper static symbol, the receiver type it forwards to)
    val targets = forwarders.flatMap { f =>
      program.symbols.all.toList.collect {
        case s if f.members(s.name) && program.symbolOf(s.owner).exists(_.fullName == f.wrapper) => s -> f.receiver
      }
    }
    if targets.isEmpty then program
    else
      var table = program.symbols
      var next  = program.symbols.all.map(_.id.raw).max + 1

      // reuse the interned receiver symbol if the program already refers to it, else mint one
      val owners = collection.mutable.Map[String, SymId]()
      def ownerOf(fqn: String): SymId = owners.getOrElseUpdate(fqn,
        program.symbols.all.find(_.fullName == fqn).map(_.id).getOrElse {
          val id = SymId(next); next += 1
          table = table.updated(Symbol(id, fqn.split('.').last, fqn, Flags(), SymId.None, NoType))
          id
        })

      mapping = targets.map { (t, receiver) =>
        val owner = ownerOf(receiver)
        val id    = SymId(next); next += 1
        table = table.updated(Symbol(id, t.name, s"$receiver#${t.name}", Flags(), owner, NoType))
        t.id -> id
      }.toMap

      given Program = program
      val units = program.units.map(u => StandardTraversal.mapClassDef(this, u))
      new Program(units, table, program.xref) // xref rebuilt by the Pipeline

  /** `Wrapper.m(x, rest…)` → `x.m(rest…)`. */
  override def transformApply(t: Tree.Apply)(using Program): Term =
    mapping.get(t.method) match
      case Some(target) if t.args.nonEmpty =>
        Tree.Apply(Tree.Select(t.args.head, target, NoType, t.origin), t.args.tail, target, t.tpe, t.origin)
      case _ => t

object StaticForwarderTransform:
  /** One library's forwarding fact: the statics of `wrapper` named `members` are plain members of
    * `receiver`, reached through the call's FIRST argument. Stated by the library's manifest — the
    * engine has no business knowing any particular wrapper's name. */
  final case class Forwarder(wrapper: String, receiver: String, members: Set[String])
