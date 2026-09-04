package balticporter.transform

import balticporter.core.{PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*
import balticporter.tir.TypeRepr.NoType

/** Inlines a static forwarder — `Wrapper.m(x, rest…)` -> `x.m(rest…)` — removing a dependency on a
  * platform-swapping wrapper the port does not need (and often cannot have, off the JVM). Which
  * class forwards which members to which receiver is per-library policy (`Forwarder`); empty is a
  * no-op. Mints proper `<receiver>#<member>` symbols rather than reusing the wrapper's, so the xref
  * and `PortabilityCheck` see each call for what it now is. `policyReport` surfaces two policy
  * failure modes: a name matching nothing, and a name matching an overload set (receiver-first is
  * assumed, not provable from a name alone).
  */
final class StaticForwarderTransform(forwarders: List[StaticForwarderTransform.Forwarder])
    extends Phase, PolicySource, SurfacePolicy, PolicyBound:
  def name: String = "static-forwarder-inline"

  /** What the run resolved each declared wrapper and member to (§8.1), bound as the full key
    * `wrapper#member` — what a finding quotes, since a bare `getSimpleName` is not editable
    * without first working out which forwarder it belongs to (§4.575). */
  private var boundMembers: Map[String, Binding[List[PolicyBinder.Hit]]] = Map.empty
  private var records: List[PolicyBinder.Record] = Nil
  private var shapeFindings: List[PolicyFinding] = Nil
  /** The members to inline, with the forwarder that named them — bound in a deterministic order,
    * since the symbols this phase mints are numbered in it (unlike `program.symbols.all`'s hash order). */
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

    // shape facts the binder cannot say — a nullary member cannot forward, and an overloaded name
    // rewrites every candidate — computed here so the report is complete before the pipeline runs
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

  /** Inlining a forwarder removes a dependency from the emitted code, so the forwarder list is
    * shared surface (§1.5). Sorted at every level, or two agreeing manifests compare unequal on a
    * `Set`'s iteration order. */
  def surfaceFingerprint: String =
    forwarders.map(f => s"${f.wrapper}->${f.receiver}${f.members.toList.sorted.mkString("(", ",", ")")}")
      .sorted.mkString(",")

  private var mapping: Map[SymId, SymId] = Map.empty

  /** Declared wrappers/members that matched nothing, plus matched members whose receiver-first
    * shape the engine could not verify. On a name-only overload match this diagnoses rather than
    * guards — the receiver-first assumption cannot be checked honestly (the first parameter is
    * legitimately a supertype in some wrappers, unresolved in others), so a wrong guard would
    * silently refuse correct rewrites. Only a provably nullary member is excluded outright. */
  def policyReport: PolicyReport =
    // a wrapper that named nothing is reported ONCE, not once per member under it
    val deadWrappers = records.collect {
      case r if r.setting == "Forwarder.wrapper" && r.binding.isUnbound => r.entry
    }.toSet
    PolicyReport.fromBindings(records.filterNot { r =>
      r.setting != "Forwarder.wrapper" && deadWrappers.exists(w => r.entry.startsWith(w + "#"))
    }) ++ PolicyReport(shapeFindings)

  /** Provably not receiver-first: a known signature with no parameters has no first argument. */
  private def nullary(s: Symbol): Boolean = s.info match
    case TypeRepr.MethodType(Nil, _, _) => true
    case _                              => false

  override def run(program: Program): Program =
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

      // one decision row per declaration, read off the pre-rewrite program (the post-rewrite site
      // names `receiver#member`, and the wrapper this decision is about is gone from it)
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
