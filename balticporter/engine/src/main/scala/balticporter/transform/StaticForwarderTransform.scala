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
final class StaticForwarderTransform(forwarders: List[StaticForwarderTransform.Forwarder])
    extends Phase, PolicySource, SurfacePolicy, PolicyBound:
  def name: String = "static-forwarder-inline"

  /** What the RUN resolved each declared wrapper and member to, before the pipeline started (§8.1).
    *
    * A member is bound as the FULL key `wrapper#member`, which is also now what a finding about it
    * quotes: a `PolicyFinding`'s key must be the string an agent edits (§4.575), and a bare
    * `getSimpleName` is not editable without first working out which forwarder it belongs to. */
  private var boundMembers: Map[String, Binding[List[PolicyBinder.Hit]]] = Map.empty
  private var records: List[PolicyBinder.Record] = Nil
  private var shapeFindings: List[PolicyFinding] = Nil
  /** the members to inline, with the forwarder that named them — bound, and in a DETERMINISTIC
    * order, because the symbols this phase mints are numbered in it. The scan it replaces read
    * `program.symbols.all`, whose order is a hash order. */
  private var boundTargets: List[(Symbol, StaticForwarderTransform.Forwarder)] = Nil

  private def memberSetting(f: StaticForwarderTransform.Forwarder): String =
    s"""Forwarder("${f.wrapper}").members"""

  def bindPolicy(binder: PolicyBinder): Unit =
    forwarders.foreach(f => binder.bindType(name, "Forwarder.wrapper", f.wrapper))
    boundMembers = forwarders.flatMap { f =>
      f.members.toList.sorted.map { m =>
        val key = MemberKey(f.wrapper, m).render
        key -> binder.bindMembers(name, memberSetting(f), key)
      }
    }.toMap
    records = binder.recordsFor(name)

    // What the BINDER cannot say, because it is a fact about the bound members' SIGNATURES rather
    // than about the key: a member that provably takes no arguments cannot forward to a
    // first-argument receiver, and a name that matched several overloads will rewrite all of them.
    // Both are computed here, from the same program the keys were bound against, so the whole
    // report is complete before the pipeline runs.
    val hits: List[(StaticForwarderTransform.Forwarder, String, List[Symbol])] =
      forwarders.flatMap { f =>
        f.members.toList.sorted.map { m =>
          val ss = boundMembers.get(MemberKey(f.wrapper, m).render).toList
            .flatMap(_.toOption.getOrElse(Nil)).flatMap(_.sym).flatMap(binder.program.symbolOf)
          (f, m, ss)
        }
      }
    shapeFindings = hits.flatMap { (f, m, ss) =>
      val key = MemberKey(f.wrapper, m).render
      val (none0, usable) = ss.partition(nullary)
      none0.map(_ =>
        PolicyFinding(name, memberSetting(f), key, PolicyIssue.Malformed,
          s"`${f.wrapper}.$m` takes no arguments, so it cannot forward to a first-argument " +
            s"receiver of type ${f.receiver} — not inlined")) ++
        (if usable.sizeIs > 1 then
           List(PolicyFinding(name, memberSetting(f), key, PolicyIssue.Unverifiable,
             s"matched by NAME ONLY: ${usable.size} overloads of `${f.wrapper}.$m` will ALL be " +
               s"rewritten to `arg1.$m(rest…)`; check that every one takes ${f.receiver} first, " +
               "or split the policy so only the receiver-first overloads are listed"))
         else Nil)
    }
    boundTargets = hits.flatMap((f, _, ss) => ss.filterNot(nullary).map(_ -> f)).sortBy(_._1.id.raw)

  /** Inlining a forwarder REMOVES a dependency from the emitted code, so a dependent module that
    * inlines a different set of members compiles against a wrapper the base no longer references —
    * the forwarder list is part of the shared surface. Sorted at every level, or two agreeing
    * manifests compare unequal on a `Set`'s iteration order. */
  def surfaceFingerprint: String =
    forwarders.map(f => s"${f.wrapper}->${f.receiver}${f.members.toList.sorted.mkString("(", ",", ")")}")
      .sorted.mkString(",")

  private var mapping: Map[SymId, SymId] = Map.empty

  /** Declared wrappers/members that matched nothing, plus matched members whose receiver-first
    * shape the engine could not verify. A property of the POLICY and the PROGRAM, complete the
    * moment the keys are bound — the `var` this replaces spoke only after a run, so a pipeline that
    * never reached this phase reported an empty policy, which reads as "every key fired".
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
  def policyReport: PolicyReport =
    // The binder's rows carry both halves of "named nothing" — a wrapper the program does not have
    // (its own `bindType`) and a member the wrapper does not declare — and it distinguishes an
    // EXTERNAL-only match from a typo, which the name scan this replaces could not.
    //
    // A wrapper that named nothing is reported ONCE, not once per member: the member rows under it
    // all say the same thing for the same reason, and a forwarder with nine members would turn one
    // typo into ten findings. Suppressed here rather than never bound, because the BINDING of each
    // member is still what the phase decides from.
    val deadWrappers = records.collect {
      case r if r.setting == "Forwarder.wrapper" && r.binding.isUnbound => r.entry
    }.toSet
    PolicyReport.fromBindings(records.filterNot { r =>
      r.setting != "Forwarder.wrapper" && deadWrappers.exists(w => r.entry.startsWith(w + "#"))
    }) ++ PolicyReport(shapeFindings)

  /** provably not receiver-first: a known signature with no parameters has no first argument. */
  private def nullary(s: Symbol): Boolean = s.info match
    case TypeRepr.MethodType(Nil, _, _) => true
    case _                              => false

  override def run(program: Program): Program =
    // (wrapper static symbol, the forwarder entry that matched it — its `receiver` drives the
    // rewrite, and the whole entry is what a decision names as the policy key). BOUND, and sorted
    // by symbol id: the scan this replaces walked `program.symbols.all`, whose iteration order is a
    // hash order, and this phase MINTS symbols in exactly that order.
    val targets = boundTargets
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

      mapping = targets.map { (t, f) =>
        val owner = ownerOf(f.receiver)
        val id    = SymId(next); next += 1
        table = table.updated(Symbol(id, t.name, MemberKey(f.receiver, t.name).render, Flags(), owner, NoType))
        t.id -> id
      }.toMap

      // DECISION PROVENANCE, one row per (DECLARATION, forwarder member) — never one per call
      // site (`Decision.declarationsUsing`). Read off the PRE-rewrite program: after the traversal
      // the site names `receiver#member` and the wrapper this decision is about is gone from it.
      targets.foreach { (t, f) =>
        val from = MemberKey(f.wrapper, t.name).render
        val to   = MemberKey(f.receiver, t.name).render
        Decision.declarationsUsing(program, t.id).foreach { (encl, origin) =>
          record(Decision(
            kind       = Decision.Kind.RedirectedCall,
            subject    = encl,
            subjectFqn = Decision.fqnOf(program, encl, from),
            detail     = Map(
              "from" -> from,
              "to"   -> to,
              "key"  -> f.wrapper,
              "why"  -> ("the wrapper's statics are plain members of their FIRST argument, so " +
                "inlining the call removes a dependency the port does not need and cannot have"),
            ),
            reason = Reason.Configured(name, s"$from -> $to"),
            origin = origin,
          ))
        }
      }

      given Program = program
      val units = program.units.map(u => StandardTraversal.mapClassDef(this, u))
      program.rebuilt(units, table) // xref rebuilt by the Pipeline

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
