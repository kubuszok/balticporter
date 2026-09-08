package balticporter.transform

import balticporter.core.{MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*

/** A method's `Class<T>` parameter, `T` its own type parameter, becomes a `ClassTag[T]` context
  * clause: the body reads the class off the tag, an owned call passing `X.class` names `[X]`. Whole
  * override component or none; a call passing a `Class` VALUE, a method reference, a type parameter
  * the literals do not determine, or an unowned component member refuses, counted (DESIGN.md §8.30).
  * @param members method keys (`C#m`, `C#m(desc)`) @param derive `ClassTagParam` rows off the reference. */
final class ClassTagParamsTransform(val members: Set[String] = Set.empty, val derive: Boolean = false)
    extends Phase, PolicySource, SurfacePolicy, MergeablePolicy, PolicyBound:
  import ClassTagParamsTransform.*

  def name: String = "class-tag-params"

  def surfaceFingerprint: String =
    val ms = if members.isEmpty then "" else s"members=${members.toList.sorted.mkString(",")}"
    val dr = if derive then "derive=reference" else ""
    List(ms, dr).filter(_.nonEmpty).mkString(";")

  def subjects: Set[String] = members.map(MergeablePolicy.subjectOf)

  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case o: ClassTagParamsTransform =>
      Right(MergeablePolicy.Merged(new ClassTagParamsTransform(members ++ o.members, derive || o.derive),
        (o.members -- members).map(MergeablePolicy.subjectOf)))
    case _ => Left(s"expected ClassTagParamsTransform, got ${later.getClass.getSimpleName}")

  private var bound: Set[SymId]      = Set.empty
  private var derivedIds: Set[SymId] = Set.empty
  private var records: List[PolicyBinder.Record] = Nil
  private val findings = collection.mutable.ListBuffer.empty[PolicyFinding]

  def bindPolicy(binder: PolicyBinder): Unit =
    bound = members.toList.sorted.flatMap(k =>
      binder.bindMembers(name, "ClassTagParamsTransform(members)", k).toOption.getOrElse(Nil).flatMap(_.sym)).toSet
    if derive then derivedIds = binder.run.derived.classTagIds
    records = binder.recordsFor(name)

  def policyReport: PolicyReport = PolicyReport.fromBindings(records) ++ PolicyReport(findings.toList)

  private def refuse(p: Program, m: SymId, why: String): Unit =
    val fqn = p.symbolOf(m).map(_.fullName).getOrElse("?")
    findings += PolicyFinding(name, "ClassTagParamsTransform", fqn, PolicyIssue.Unverifiable, why)
    record(Decision(kind = Decision.Kind.ScopedOut, subject = m, subjectFqn = fqn,
      detail = Map("refused" -> "class-tag-params", "why" -> why),
      reason = Reason.Configured(name, fqn), origin = Decision.originOf(p, m)))

  override def run(program: Program): Program =
    findings.clear()
    val targets = bound ++ derivedIds
    // the JDK's own `Class`: the class-literal type, a fact of the language (PolicyKeyLintSpec allow-list)
    val classSym = program.symbols.all.find(_.fullName == "java.lang.Class").map(_.id)
    if targets.isEmpty || classSym.isEmpty then return program
    given Program = program
    val cls   = classSym.get
    val graph = OverrideGraph.build(program)
    val mint  = new Mint(program)

    /** the `Class<T>` parameters of a method, each with the type parameter it names. */
    def classParams(d: Tree.DefDef): List[(Tree.ValDef, SymId)] =
      val tps = d.tparams.map(_.symbol).toSet
      d.paramss.flatten.flatMap { v =>
        v.tpt.tpe match
          case TypeRepr.AppliedType(TypeRepr.TypeRef(_, c), List(TypeRepr.TypeRef(_, t))) if c == cls && tps(t) => List(v -> t)
          case _ => Nil
      }
    /** the type argument a call's class-literal argument names, if it is one. */
    def literalArg(t: Term): Option[TypeRepr] = t match
      case Tree.Literal(Constant.ClassOfC(tp), _, _) => Some(tp)
      case Tree.Typed(inner, _, _, _)                => literalArg(inner)
      case _                                         => scala.None

    // ---- decide, whole component or none ------------------------------------------------------
    val plans = collection.mutable.Map.empty[SymId, Plan]
    val seen  = collection.mutable.Set.empty[SymId]
    targets.toList.sortBy(_.raw).foreach { t =>
      if !seen(t) then
        val closure = graph.closureOf(t)
        val comp    = closure.members
        seen ++= comp
        val defs = comp.toList.sortBy(_.raw).map(m => m -> program.definitionOf(m))
        if closure.isAnchored then
          refuse(program, t, closure.anchorReason(program).getOrElse("the override component reaches a declaration this program cannot move"))
        else if defs.exists((m, d) => !program.owned(m) || !d.exists(_.isInstanceOf[Tree.DefDef])) then
          refuse(program, t, "a member of the override component is not a method declaration this program owns")
        else
          val perMember = defs.map { (m, d) => m -> classParams(d.get.asInstanceOf[Tree.DefDef]) }
          val arities   = perMember.map(_._2.size).distinct
          if arities != List(perMember.head._2.size) || perMember.head._2.isEmpty then
            refuse(program, t, "the override component's members do not agree on their `Class<T>` parameters (or have none)")
          else
            // every owned call must pass class LITERALS that determine every type parameter
            val bad = perMember.flatMap { (m, cps) =>
              val idx = cps.map((v, _) => program.definitionOf(m).get.asInstanceOf[Tree.DefDef].paramss.flatten.indexOf(v))
              val d   = program.definitionOf(m).get.asInstanceOf[Tree.DefDef]
              program.usages(m).flatMap {
                case Usage(UsageKind.Call, a: Tree.Apply, _) =>
                  val lits = idx.map(i => a.args.lift(i).flatMap(literalArg))
                  val alreadyTyped = a.fun.isInstanceOf[Tree.TypeApply]
                  val determined = cps.map(_._2).toSet
                  if lits.exists(_.isEmpty) then List(s"a call at ${a.origin.javaPath}:${a.origin.line} passes a `Class` VALUE, which a context clause cannot take without an explicit `using` argument")
                  else if !alreadyTyped && d.tparams.exists(tp => !determined(tp.symbol)) then
                    List(s"a call at ${a.origin.javaPath}:${a.origin.line} would need type arguments the class literals do not determine")
                  else Nil
                case Usage(UsageKind.Call, other, _) => List(s"a call at ${other.origin.javaPath}:${other.origin.line} is not a plain application")
                case Usage(UsageKind.TermRef, r, _)  => List(s"a method reference at ${r.origin.javaPath}:${r.origin.line} cannot supply the tag")
                case _                               => Nil
              }
            }
            if bad.nonEmpty then refuse(program, t, bad.head)
            else perMember.foreach { (m, cps) => plans(m) = Plan(cps.map((v, tp) => (v.symbol, tp))) }
    }
    if plans.isEmpty then return program

    // ---- the signature edit: drop the parameter, add the clause, bind the class in the body ----
    val summonSym = mint.member("summon", MemberKey("scala.Predef", "summon").render, mint.tpe("Predef", "scala.Predef"),
                                TypeRepr.NoType, Flags(isStatic = true))
    val ctSym     = mint.tpe("ClassTag", "scala.reflect.ClassTag")
    val rtSym     = mint.member("runtimeClass", MemberKey("scala.reflect.ClassTag", "runtimeClass").render, ctSym, TypeRepr.NoType, Flags())
    def ctOf(tp: SymId): TypeRepr = TypeRepr.AppliedType(TypeRepr.TypeRef(TypeRepr.NoPrefix, ctSym), List(TypeRepr.TypeRef(TypeRepr.NoPrefix, tp)))
    def classOfTag(tp: SymId, at: Origin): Term =
      val classTpe = TypeRepr.AppliedType(TypeRepr.TypeRef(TypeRepr.NoPrefix, cls), List(TypeRepr.TypeRef(TypeRepr.NoPrefix, tp)))
      val summon   = Tree.TypeApply(Tree.Ident(summonSym, TypeRepr.NoType, at), List(TypeTree(ctOf(tp), at)), ctOf(tp), at)
      val rt       = Tree.Select(summon, rtSym, TypeRepr.NoType, at)
      Tree.Typed(rt, TypeTree(classTpe, at), classTpe, at)
    val edit = new Phase:
      def name = "class-tag-params/edit"
      override def transformDefDef(d: Tree.DefDef)(using p: Program): Tree.DefDef =
        plans.get(d.symbol).fold(d) { plan =>
          val dropped = plan.params.map(_._1).toSet
          val kept    = d.paramss.map(_.filterNot(v => dropped(v.symbol))).filter(_.nonEmpty)
          val at      = d.origin
          val clause  = plan.params.map { (_, tp) =>
            Tree.ValDef(mint.member("", MemberKey(p.symbolOf(d.symbol).map(_.fullName).getOrElse("?"), "<using-classtag>").render + "/" + tp.raw,
                                    d.symbol, ctOf(tp), Flags(isParam = true, isGiven = true)),
                        TypeTree(ctOf(tp), at), scala.None, at)
          }
          val binds = plan.params.map { (v, tp) =>
            val pv = p.symbolOf(v)
            Tree.ValDef(v, TypeTree(TypeRepr.AppliedType(TypeRepr.TypeRef(TypeRepr.NoPrefix, cls),
              List(TypeRepr.TypeRef(TypeRepr.NoPrefix, tp))), at), Some(classOfTag(tp, at)), at)
          }
          val rhs = d.rhs.map {
            case b: Tree.Block => b.copy(stats = binds ++ b.stats)
            case other         => Tree.Block(binds, other, other.tpe, at)
          }
          record(Decision(kind = Decision.Kind.RetypedSignature, subject = d.symbol,
            subjectFqn = p.symbolOf(d.symbol).map(_.fullName).getOrElse("?"),
            detail = Map(
              "from" -> plan.params.map((v, _) => s"${p.symbolOf(v).map(_.name).getOrElse("?")}: Class[T]").mkString(", "),
              "to"   -> plan.params.map((_, tp) => s"(using ClassTag[${p.symbolOf(tp).map(_.name).getOrElse("T")}])").mkString,
              "why"  -> ("the class a caller names is the type argument's own; the reference port asks for it as " +
                "a `ClassTag` context bound and reads the class off the tag — an owned call passing `X.class` names `[X]`"),
            ),
            reason = Reason.Configured(name, p.symbolOf(d.symbol).map(_.fullName).getOrElse("?")),
            origin = at))
          d.copy(paramss = kept :+ clause, rhs = rhs)
        }
      override def transformApply(a: Tree.Apply)(using p: Program): Term =
        plans.get(a.method).fold(a) { plan =>
          val d   = p.definitionOf(a.method).get.asInstanceOf[Tree.DefDef]
          val all = d.paramss.flatten
          val idx = plan.params.map((v, _) => all.indexWhere(_.symbol == v))
          val lits = idx.flatMap(i => a.args.lift(i).flatMap(literalArg))
          val rest = a.args.zipWithIndex.collect { case (t, i) if !idx.contains(i) => t }
          val byTp = plan.params.map(_._2).zip(lits).toMap
          val fun  = a.fun match
            case ta: Tree.TypeApply => ta
            case f =>
              val targs = d.tparams.map(tp => TypeTree(byTp.getOrElse(tp.symbol, TypeRepr.TypeRef(TypeRepr.NoPrefix, tp.symbol)), a.origin))
              Tree.TypeApply(f, targs, a.tpe, a.origin)
          // a method whose only value clause went: the call is the type application alone
          val clauseGone = all.forall(v => plan.params.exists(_._1 == v.symbol))
          if clauseGone && rest.isEmpty then fun else a.copy(fun = fun, args = rest)
        }
    val units = program.units.map(u => StandardTraversal.mapClassDef(edit, u))
    program.rebuilt(units = units, symbols = SymbolTable(program.symbols.all ++ mint.minted))

object ClassTagParamsTransform:
  /** one method's plan: each `Class<T>` parameter symbol with the type parameter it names. */
  final case class Plan(params: List[(SymId, SymId)])

  /** a symbol minter for one run (external types, the `summon` and `runtimeClass` members). */
  final class Mint(program: Program):
    private var next = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    private val buf  = collection.mutable.ListBuffer.empty[Symbol]
    def minted: List[Symbol] = buf.toList
    private def fresh(): SymId = { val id = SymId(next); next += 1; id }
    def tpe(nm: String, full: String): SymId =
      val id = fresh()
      buf += Symbol(id, nm, full, Flags(), SymId.None, TypeRepr.TypeRef(TypeRepr.NoPrefix, id))
      id
    def member(nm: String, full: String, owner: SymId, info: TypeRepr, flags: Flags): SymId =
      val id = fresh()
      buf += Symbol(id, nm, full, flags, owner, info)
      id
