package balticporter.transform

import balticporter.core.{PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
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
  *
  * Two ways the POLICY can be wrong without the mechanism noticing, both surfaced by
  * [[policyReport]]: a wrapper or member name that matches NOTHING (the rewrite silently does not
  * happen, and the dependency the port was configured to remove is still there), and a member
  * matched by NAME where the name is overloaded (receiver-first is an assumption about the
  * wrapper's shape that a name alone cannot carry).
  */
final class StaticForwarderTransform(forwarders: List[StaticForwarderTransform.Forwarder]) extends Phase, PolicySource, SurfacePolicy:
  def name: String = "static-forwarder-inline"

  /** Inlining a forwarder REMOVES a dependency from the emitted code, so a dependent module that
    * inlines a different set of members compiles against a wrapper the base no longer references —
    * the forwarder list is part of the shared surface. Sorted at every level, or two agreeing
    * manifests compare unequal on a `Set`'s iteration order. */
  def surfaceFingerprint: String =
    forwarders.map(f => s"${f.wrapper}->${f.receiver}${f.members.toList.sorted.mkString("(", ",", ")")}")
      .sorted.mkString(",")

  private var mapping: Map[SymId, SymId] = Map.empty

  private var report: PolicyReport = PolicyReport.empty

  /** Declared wrappers/members that matched nothing, plus matched members whose receiver-first
    * shape the engine could not verify. Reflects the last [[run]]; empty before the first, and
    * empty for an empty policy.
    *
    * On the name-only match this DIAGNOSES rather than guards, deliberately. The only guard worth
    * having would be "the first parameter's type is the receiver", and it cannot be applied
    * honestly: that parameter is legitimately a SUPERTYPE of the receiver in some wrappers, and an
    * external symbol whose `info` this program never resolved in others. A guard built on it would
    * refuse correct rewrites — the residue this phase exists to keep visible, inverted and silent,
    * which is worse than the risk. So an overload set gets an `Unverifiable` finding naming the
    * wrapper and member, and the reader (who has the Java source) decides.
    *
    * One case IS guarded, because it is proved rather than suspected: a member whose KNOWN
    * signature takes no parameters can never be receiver-first, so it is excluded from the rewrite
    * set. A member whose `info` is unresolved is left in — an unknown signature is not evidence.
    */
  def policyReport: PolicyReport = report

  /** provably not receiver-first: a known signature with no parameters has no first argument. */
  private def nullary(s: Symbol): Boolean = s.info match
    case TypeRepr.MethodType(Nil, _, _) => true
    case _                              => false

  /** Everything the policy DECLARES, checked against what the program actually has. Separate from
    * target selection below so that selection — and therefore the order symbols are minted in —
    * stays exactly what it was. */
  private def audit(program: Program): PolicyReport =
    val all       = program.symbols.all.toList
    val membersOf = all.groupBy(s => program.symbolOf(s.owner).map(_.fullName).getOrElse(""))
    val typeNames = all.iterator.map(_.fullName).toSet
    PolicyReport(forwarders.flatMap { f =>
      val declared = membersOf.getOrElse(f.wrapper, Nil)
      if declared.isEmpty && !typeNames.contains(f.wrapper) then
        List(PolicyFinding(name, "Forwarder.wrapper", f.wrapper, PolicyIssue.NeverMatched,
          "no type of that name occurs in this program, so none of its members were inlined and " +
            "every call still goes through the wrapper"))
      else
        val setting = s"""Forwarder("${f.wrapper}").members"""
        f.members.toList.sorted.flatMap { m =>
          val hits = declared.filter(_.name == m)
          if hits.isEmpty then
            List(PolicyFinding(name, setting, m, PolicyIssue.NeverMatched,
              "the wrapper is present but declares no member of that name"))
          else
            val (none0, usable) = hits.partition(nullary)
            none0.map(_ =>
              PolicyFinding(name, setting, m, PolicyIssue.Malformed,
                s"`${f.wrapper}.$m` takes no arguments, so it cannot forward to a first-argument " +
                  s"receiver of type ${f.receiver} — not inlined")) ++
              (if usable.sizeIs > 1 then
                 List(PolicyFinding(name, setting, m, PolicyIssue.Unverifiable,
                   s"matched by NAME ONLY: ${usable.size} overloads of `${f.wrapper}.$m` will ALL be " +
                     s"rewritten to `arg1.$m(rest…)`; check that every one takes ${f.receiver} first, " +
                     "or split the policy so only the receiver-first overloads are listed"))
               else Nil)
        }
    })

  override def run(program: Program): Program =
    report = audit(program)
    // (wrapper static symbol, the receiver type it forwards to)
    val targets = forwarders.flatMap { f =>
      program.symbols.all.toList.collect {
        case s if f.members(s.name) && program.symbolOf(s.owner).exists(_.fullName == f.wrapper) && !nullary(s) =>
          s -> f.receiver
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
