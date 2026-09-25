package balticporter.transform

import balticporter.tir.*

/** The decision the `Class<T>`-parameter rules share: which parameters are `Class<T>` for the method's OWN `T`, and which override components may convert — whole component or none, to a fixpoint,
  * since a refused component takes its parameters out of what a delegating call may pass.
  */
private[transform] object ClassParamComponents:

  /** one method's plan: each `Class<T>` parameter symbol with the type parameter it names. */
  final case class Plan(params: List[(SymId, SymId)])

  /** the converted members, the refused components (one member each, with why), and the class parameters of every converted member. */
  final case class Outcome(plans: Map[SymId, Plan], refused: List[(SymId, String)], candidates: Map[SymId, SymId])

  /** the `Class<T>` parameters of a method, each with the type parameter it names. */
  def classParams(d: Tree.DefDef, cls: SymId): List[(Tree.ValDef, SymId)] =
    val tps = d.tparams.map(_.symbol).toSet
    d.paramss.flatten.flatMap { v =>
      v.tpt.tpe match
        case TypeRepr.AppliedType(TypeRepr.TypeRef(_, c), List(TypeRepr.TypeRef(_, t))) if c == cls && tps(t) => List(v -> t)
        case _                                                                                                => Nil
    }

  /** the type argument a call's class argument names: a class literal, or a candidate parameter. */
  def literalArg(t: Term, candidates: Map[SymId, SymId]): Option[TypeRepr] = t match
    case Tree.Literal(Constant.ClassOfC(tp), _, _)     => Some(tp)
    case Tree.Typed(inner, _, _, _)                    => literalArg(inner, candidates)
    case Tree.Ident(s, _, _) if candidates.contains(s) => Some(TypeRepr.TypeRef(TypeRepr.NoPrefix, candidates(s)))
    case _                                             => scala.None

  /** a `Class` value whose static type is `Class[X]` for a CLASS `X`: scala infers `T` from it where the parameter stays. A type parameter or a wildcard names no type an instance can be found for. */
  private def concreteClassValue(t: Term, cls: SymId)(using p: Program): Boolean = t.tpe match
    case TypeRepr.AppliedType(TypeRepr.TypeRef(_, c), List(TypeRepr.TypeRef(_, x))) if c == cls =>
      !p.definitionOf(x).exists(_.isInstanceOf[Tree.TypeDef])
    case _ => false

  /** Decides every target's override component. `keepsParam`: the `Class` parameter stays, so a call passing a value of a concrete `Class[X]` type is accepted and no type argument is needed.
    * `memberCheck` refuses a member for a rule's own reason, given the current candidate parameters.
    */
  def decide(
    targets:     Set[SymId],
    cls:         SymId,
    keepsParam:  Boolean,
    memberCheck: (SymId, Tree.DefDef, List[(Tree.ValDef, SymId)], Map[SymId, SymId]) => Option[String]
  )(using program: Program): Outcome =
    val graph = OverrideGraph.build(program)
    var candidates: Map[SymId, SymId] =
      targets.toList
        .flatMap(t => graph.closureOf(t).members.toList)
        .distinct
        .flatMap { m =>
          program.definitionOf(m) match
            case Some(d: Tree.DefDef) if program.owned(m) => classParams(d, cls).map((v, tp) => v.symbol -> tp)
            case _                                        => Nil
        }
        .toMap
    val plans   = collection.mutable.Map.empty[SymId, Plan]
    val refused = collection.mutable.ListBuffer.empty[(SymId, String)]
    var stable  = false
    while !stable do
      stable = true
      plans.clear(); refused.clear()
      val seen = collection.mutable.Set.empty[SymId]
      targets.toList.sortBy(_.raw).foreach { t =>
        if !seen(t) then
          val closure = graph.closureOf(t)
          val comp    = closure.members
          seen ++= comp
          val defs = comp.toList.sortBy(_.raw).map(m => m -> program.definitionOf(m))
          if closure.isAnchored then refused += (t -> closure.anchorReason(program).getOrElse("the override component reaches a declaration this program cannot move"))
          else if defs.exists((m, d) => !program.owned(m) || !d.exists(_.isInstanceOf[Tree.DefDef])) then
            refused += (t -> "a member of the override component is not a method declaration this program owns")
          else
            val perMember = defs.map((m, d) => (m, d.get.asInstanceOf[Tree.DefDef], classParams(d.get.asInstanceOf[Tree.DefDef], cls)))
            val arities   = perMember.map(_._3.size).distinct
            if arities != List(perMember.head._3.size) || perMember.head._3.isEmpty then
              refused += (t -> "the override component's members do not agree on their `Class<T>` parameters (or have none)")
            else
              val bad = perMember.flatMap { (m, d, cps) =>
                val all = d.paramss.flatten
                val idx = cps.map((v, _) => all.indexOf(v))
                val calls = program.usages(m).flatMap {
                  case Usage(UsageKind.Call, a: Tree.Apply, _) =>
                    val args         = idx.map(i => a.args.lift(i))
                    val lits         = args.map(_.flatMap(literalArg(_, candidates)))
                    val alreadyTyped = a.fun.isInstanceOf[Tree.TypeApply]
                    val determined   = cps.map(_._2).toSet
                    val valueOk      = keepsParam && args.zip(lits).forall((arg, lit) => lit.isDefined || arg.exists(concreteClassValue(_, cls)))
                    if lits.exists(_.isEmpty) && !valueOk then
                      List(
                        s"a call at ${a.origin.javaPath}:${a.origin.line} passes a `Class` VALUE, which a context clause cannot take without an explicit `using` argument"
                      )
                    else if !keepsParam && !alreadyTyped && d.tparams.exists(tp => !determined(tp.symbol)) then
                      List(s"a call at ${a.origin.javaPath}:${a.origin.line} would need type arguments the class literals do not determine")
                    else Nil
                  case Usage(UsageKind.Call, other, _) => List(s"a call at ${other.origin.javaPath}:${other.origin.line} is not a plain application")
                  case Usage(UsageKind.TermRef, r, _)  => List(s"a method reference at ${r.origin.javaPath}:${r.origin.line} cannot supply the context clause")
                  case _                               => Nil
                }
                calls ++ memberCheck(m, d, cps, candidates).toList
              }
              if bad.nonEmpty then
                refused += (t -> bad.head)
                val gone = perMember.flatMap(_._3).map(_._1.symbol).toSet
                if gone.exists(candidates.contains) then
                  candidates = candidates.filterNot((p, _) => gone(p)); stable = false
              else perMember.foreach { (m, _, cps) => plans(m) = Plan(cps.map((v, tp) => (v.symbol, tp))) }
      }
    Outcome(plans.toMap, refused.toList, candidates)
