package balticporter.transform

import balticporter.core.{ MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy }
import balticporter.tir.*

/** A method whose `Class<T>` parameter (`T` its own type parameter) is used to CONSTRUCT a `T` reflectively takes a port-supplied type class `F[T]` as a context clause instead, and every construction
  * becomes `summon[F[T]].<create>()`: no reflection on any platform, and a type the instance cannot be derived for is a compile error. Whole override component or none; a call passing a `Class`
  * VALUE, a method reference, an undetermined type parameter, or reflection on the class this rule cannot express refuses, counted. An empty rule list is a no-op.
  */
final class TypeClassParamsTransform(val rules: List[TypeClassParamsTransform.Rule] = Nil)
    extends Phase,
      PolicySource,
      SurfacePolicy,
      MergeablePolicy,
      PolicyBound,
      Rewrite:
  import TypeClassParamsTransform.*

  def name: String = TypeClassParamsTransform.Name

  /** every refusal is a `policy` finding; the retyping itself opens no seam a call cannot see. */
  def accountedBy: Set[String] = Set(balticporter.runner.PortRun.Policy)

  /** The type class, its creating member, the spelling and the class-value answer are all emitted signature or body, so every field is surface. Empty rules contribute no segment. */
  def surfaceFingerprint: String = rules.map(Rule.render).sorted.mkString(";")

  def subjects: Set[String] = rules.flatMap(_.members).map(MergeablePolicy.subjectOf).toSet

  /** Rules UNION; one member named by two different rules refuses — two type classes for one parameter is a conflict only a human can resolve. */
  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case o: TypeClassParamsTransform =>
      val mine      = rules.flatMap(r => r.members.map(_ -> r)).toMap
      val conflicts = o.rules.flatMap(r => r.members.toList.sorted.filter(m => mine.get(m).exists(_ != r)))
      if conflicts.nonEmpty then Left(conflicts.map(m => s"$m: already converted by a different type-class rule").mkString("; "))
      else
        val added = o.rules.filterNot(rules.contains)
        Right(MergeablePolicy.Merged(new TypeClassParamsTransform(rules ++ added), (added.flatMap(_.members).toSet -- mine.keySet).map(MergeablePolicy.subjectOf)))
    case _ => Left(s"expected TypeClassParamsTransform, got ${later.getClass.getSimpleName}")

  private var bound:        Map[Int, Set[SymId]]      = Map.empty
  private var instantiator: Map[Int, Set[SymId]]      = Map.empty
  private var handled:      Map[Int, Set[SymId]]      = Map.empty
  private var records:      List[PolicyBinder.Record] = Nil
  private val findings = collection.mutable.ListBuffer.empty[PolicyFinding]

  def bindPolicy(binder: PolicyBinder): Unit =
    val ix = rules.zipWithIndex
    bound = ix.map((r, i) => i -> r.members.toList.sorted.flatMap(k => binder.bindMembers(name, "TypeClassParamsTransform(members)", k).toOption.getOrElse(Nil).flatMap(_.sym)).toSet).toMap
    instantiator = ix.map { (r, i) =>
      i -> r.instantiators.toList.sorted.flatMap(k => binder.bindCallee(name, "TypeClassParamsTransform(instantiators)", k, Ownership.Either).toOption.flatMap(_.sym)).toSet
    }.toMap
    handled = ix.map((r, i) => i -> r.handles.toList.sorted.flatMap(h => binder.bindType(name, "TypeClassParamsTransform(handles)", h, Ownership.Either).toOption).toSet).toMap
    records = binder.recordsFor(name)

  def policyReport: PolicyReport = PolicyReport.fromBindings(records) ++ PolicyReport(findings.toList)

  private def refuse(p: Program, m: SymId, why: String): Unit =
    val fqn = p.symbolOf(m).map(_.fullName).getOrElse("?")
    findings += PolicyFinding(name, "TypeClassParamsTransform", fqn, PolicyIssue.Unverifiable, why)
    record(
      Decision(
        kind = Decision.Kind.ScopedOut,
        subject = m,
        subjectFqn = fqn,
        detail = Map("refused" -> Name, "why" -> why),
        reason = Reason.Configured(name, fqn),
        origin = Decision.originOf(p, m)
      )
    )

  override def run(program: Program): Program =
    findings.clear()
    // the JDK's own `Class` and `Constructor`: the reflective-construction members java itself declares (PolicyKeyLintSpec allow-list)
    val classSym = program.symbols.all.find(_.fullName == "java.lang.Class").map(_.id)
    val ctorSym  = program.symbols.all.find(_.fullName == "java.lang.reflect.Constructor").map(_.id)
    if bound.values.forall(_.isEmpty) || classSym.isEmpty then return program
    given Program = program
    val cls       = classSym.get
    val mint      = new ClassTagParamsTransform.Mint(program)
    val shapes    = new Shapes(program, cls, ctorSym)

    // ---- decide, per rule ----
    val plans      = collection.mutable.Map.empty[SymId, (Int, ClassParamComponents.Plan)]
    val candidates = collection.mutable.Map.empty[Int, Map[SymId, SymId]]
    rules.zipWithIndex.foreach { (r, i) =>
      val targets = bound.getOrElse(i, Set.empty)
      if targets.nonEmpty then
        val inst    = instantiator.getOrElse(i, Set.empty)
        val keeps   = r.spelling == Spelling.KeepParameter
        val outcome = ClassParamComponents.decide(targets, cls, keeps, (_, d, cps, cands) => shapes.bodyRefusal(d, cps.map(_._1.symbol).toSet, cands, inst, r, keeps))
        outcome.refused.foreach((t, why) => refuse(program, t, why))
        outcome.plans.foreach { (m, p) =>
          if plans.contains(m) then refuse(program, m, "two type-class rules convert this member's override component")
          else plans(m) = (i, p)
        }
        candidates(i) = outcome.candidates
    }
    if plans.isEmpty then return program

    // ---- minted external symbols ----
    val summonSym = mint.member("summon", MemberKey("scala.Predef", "summon").render, mint.tpe("Predef", "scala.Predef"), TypeRepr.NoType, Flags(isStatic = true))
    val ctSym     = mint.tpe("ClassTag", "scala.reflect.ClassTag")
    val rtSym     = mint.member("runtimeClass", MemberKey("scala.reflect.ClassTag", "runtimeClass").render, ctSym, TypeRepr.NoType, Flags())
    val usedRules = plans.values.map(_._1).toSet
    val tcSym     = usedRules.toList.sorted.map { i =>
      val fqn = rules(i).typeClass
      i -> mint.tpe(fqn.substring(fqn.lastIndexOf('.') + 1), fqn)
    }.toMap
    val createSym = usedRules.toList.sorted.map(i => i -> mint.member(rules(i).create, MemberKey(rules(i).typeClass, rules(i).create).render, tcSym(i), TypeRepr.NoType, Flags())).toMap
    val keySym    = usedRules.toList.sorted.flatMap { i =>
      rules(i).classValue match
        case ClassValue.Member(n) => List(i -> mint.member(n, MemberKey(rules(i).typeClass, n).render, tcSym(i), TypeRepr.NoType, Flags()))
        case _                    => Nil
    }.toMap
    val created         = createSym.map(_.swap)
    val allInstantiator = instantiator.values.flatten.toSet

    def ref(tp: SymId):                  TypeRepr = TypeRepr.TypeRef(TypeRepr.NoPrefix, tp)
    def applied(tc: SymId, tp: SymId):   TypeRepr = TypeRepr.AppliedType(TypeRepr.TypeRef(TypeRepr.NoPrefix, tc), List(ref(tp)))
    def summoned(tc: SymId, tp: SymId, at: Origin): Term =
      Tree.TypeApply(Tree.Ident(summonSym, TypeRepr.NoType, at), List(TypeTree(applied(tc, tp), at)), applied(tc, tp), at)
    // class parameter → (rule, its type parameter), for every converted member
    val paramOf: Map[SymId, (Int, SymId)] = plans.values.toList.flatMap((i, p) => p.params.map((v, tp) => v -> (i, tp))).toMap

    val edit = new Phase:
      def name = s"$Name/edit"

      override def transformApply(a: Tree.Apply)(using p: Program): Term =
        shapes.constructed(a, allInstantiator).flatMap(paramOf.get) match
          case Some((i, tp)) =>
            Tree.Apply(Tree.Select(summoned(tcSym(i), tp, a.origin), createSym(i), ref(tp), a.origin), Nil, createSym(i), a.tpe, a.origin)
          case scala.None =>
            plans.get(a.method) match
              case Some((i, plan)) if rules(i).spelling == Spelling.TypeArgument => typeArgumentCall(a, plan, candidates(i))
              case _                                                             => a

      /** `m(X.class, rest)` → `m[X](rest)`; a method whose only value clause went is the type application alone. */
      private def typeArgumentCall(a: Tree.Apply, plan: ClassParamComponents.Plan, cands: Map[SymId, SymId])(using p: Program): Term =
        val d    = p.definitionOf(a.method).get.asInstanceOf[Tree.DefDef]
        val all  = d.paramss.flatten
        val idx  = plan.params.map((v, _) => all.indexWhere(_.symbol == v))
        val lits = idx.flatMap(i => a.args.lift(i).flatMap(ClassParamComponents.literalArg(_, cands)))
        val rest = a.args.zipWithIndex.collect { case (t, i) if !idx.contains(i) => t }
        val byTp = plan.params.map(_._2).zip(lits).toMap
        val fun  = a.fun match
          case ta: Tree.TypeApply => ta
          case f => Tree.TypeApply(f, d.tparams.map(tp => TypeTree(byTp.getOrElse(tp.symbol, ref(tp.symbol)), a.origin)), a.tpe, a.origin)
        if all.forall(v => plan.params.exists(_._1 == v.symbol)) && rest.isEmpty then fun else a.copy(fun = fun, args = rest)

      /** A `try` whose every `catch` names a type the rule DECLARES as the retired construction's failure, around a rewritten construction, is its body: the thrower is gone. Any other `try` there is
        * left as java wrote it, and counted.
        */
      override def transformTerm(t: Term)(using p: Program): Term = t match
        case tr: Tree.Try =>
          val inside = StandardTraversal.scanTerm(tr.body, Set.empty[Int]) {
            case (acc, a: Tree.Apply) => created.get(a.method).fold(acc)(acc + _)
            case (acc, _)             => acc
          }
          if inside.isEmpty || tr.catches.isEmpty then tr
          else
            val declared = inside.flatMap(handled.getOrElse(_, Set.empty))
            if tr.resources.isEmpty && tr.finalizer.isEmpty && tr.catches.forall(c => caughtTypes(c.param.tpt.tpe).forall(declared)) then tr.body
            else
              findings += PolicyFinding(
                name,
                "TypeClassParamsTransform(handles)",
                s"${tr.origin.javaPath}:${tr.origin.line}",
                PolicyIssue.Unverifiable,
                "the rewritten construction sits inside a `try` whose handlers `handles` does not describe, so java's handler is left over a call that can no longer throw what it catches"
              )
              tr
        case other => other

      override def transformDefDef(d: Tree.DefDef)(using p: Program): Tree.DefDef =
        plans.get(d.symbol).fold(d) { (i, plan) =>
          val r     = rules(i)
          val at    = d.origin
          val fqn   = p.symbolOf(d.symbol).map(_.fullName).getOrElse("?")
          val drops = r.spelling == Spelling.TypeArgument
          val tag   = drops && r.classValue == ClassValue.Tag
          def usingParam(tc: SymId, tp: SymId, slot: String): Tree.ValDef =
            val tpe = applied(tc, tp)
            Tree.ValDef(mint.member("", MemberKey(fqn, slot).render + "/" + tp.raw, d.symbol, tpe, Flags(isParam = true, isGiven = true)), TypeTree(tpe, at), scala.None, at)
          val clause = plan.params.flatMap { (_, tp) =>
            usingParam(tcSym(i), tp, "<using-typeclass>") :: (if tag then List(usingParam(ctSym, tp, "<using-classtag>")) else Nil)
          }
          val dropped = if drops then plan.params.map(_._1).toSet else Set.empty[SymId]
          val kept    = d.paramss.map(_.filterNot(v => dropped(v.symbol))).filter(_.nonEmpty)
          // a class value the body still reads, where the parameter went: bound at the head from the rule's answer
          val stillRead = d.rhs.toList.flatMap(b =>
            StandardTraversal.scanTerm(b, Set.empty[SymId]) {
              case (acc, Tree.Ident(s, _, _)) if dropped(s) => acc + s
              case (acc, _)                                 => acc
            }
          )
          val binds = plan.params.filter((v, _) => stillRead.contains(v)).map { (v, tp) =>
            val classTpe = applied(cls, tp)
            val answer: Term = r.classValue match
              case ClassValue.Member(_) => Tree.Select(summoned(tcSym(i), tp, at), keySym(i), classTpe, at)
              case _ => Tree.Typed(Tree.Select(summoned(ctSym, tp, at), rtSym, TypeRepr.NoType, at), TypeTree(classTpe, at), classTpe, at)
            Tree.ValDef(v, TypeTree(classTpe, at), Some(answer), at)
          }
          val rhs =
            if binds.isEmpty then d.rhs
            else
              d.rhs.map {
                case b: Tree.Block => b.copy(stats = binds ++ b.stats)
                case other => Tree.Block(binds, other, other.tpe, at)
              }
          val tps = plan.params.map((_, tp) => p.symbolOf(tp).map(_.name).getOrElse("T"))
          record(
            Decision(
              kind = Decision.Kind.RetypedSignature,
              subject = d.symbol,
              subjectFqn = fqn,
              detail = Map(
                "from" -> plan.params.map((v, _) => s"${p.symbolOf(v).map(_.name).getOrElse("?")}: Class[T]").mkString(", "),
                "to" -> tps.map(t => s"(using ${r.typeClass}[$t]${if tag then s", ClassTag[$t]" else ""})").mkString,
                "spelling" -> Spelling.render(r.spelling),
                "why" -> ("java constructs the class a caller names by reflection, which only the JVM has; the port asks for the " +
                  "type class at compile time instead, so a type it cannot derive an instance for is a compile error where java " +
                  "failed at run time")
              ),
              reason = Reason.Configured(name, fqn),
              origin = at
            )
          )
          d.copy(paramss = kept :+ clause, rhs = rhs)
        }

    val units = program.units.map(u => StandardTraversal.mapClassDef(edit, u))
    program.rebuilt(units = units, symbols = SymbolTable(program.symbols.all ++ mint.minted))

  /** every type a `catch` clause names — a multi-catch is an `OrType` on the parameter. */
  private def caughtTypes(t: TypeRepr): Set[SymId] = t match
    case TypeRepr.OrType(l, r)       => caughtTypes(l) ++ caughtTypes(r)
    case TypeRepr.TypeRef(_, s)      => Set(s)
    case TypeRepr.AppliedType(tc, _) => caughtTypes(tc)
    case _                           => Set.empty

object TypeClassParamsTransform:

  val Name = "type-class-params"

  /** How the converted member is SPELLED. `TypeArgument` drops the `Class` parameter (`m[X]`, as `class-tag-params` does); `KeepParameter` keeps it beside the clause (`m(classOf[X])`), so a caller
    * reads as java wrote it and the class value stays available to the body.
    */
  enum Spelling:
    case TypeArgument, KeepParameter

  object Spelling:
    def render(s: Spelling): String = s match
      case TypeArgument  => "type-argument"
      case KeepParameter => "keep-parameter"
    def parse(s: String): Option[Spelling] = values.find(render(_) == s)

  /** What a use of the `Class` value OTHER than construction reads, where the parameter is dropped (a map key, an argument to something else). `Refuse` refuses the component; `Tag` adds a
    * `ClassTag[T]` clause and reads its `runtimeClass` (every platform has it); `Member(name)` reads the type class's own `name`, which must answer `Class[T]`.
    */
  enum ClassValue:
    case Refuse
    case Tag
    case Member(name: String)

  object ClassValue:
    def render(c: ClassValue): String = c match
      case Refuse    => "refuse"
      case Tag       => "class-tag"
      case Member(n) => s"member:$n"
    def parse(s: String): Option[ClassValue] = s match
      case "refuse"                          => Some(Refuse)
      case "class-tag"                       => Some(Tag)
      case m if m.startsWith("member:") && m.length > 7 => Some(Member(m.drop(7)))
      case _                                 => scala.None

  /** One type class and the members it replaces the `Class` parameter of. `typeClass` is the port-supplied `F[_]`'s FQN; `create` its nullary member building a `T` (called `create()`);
    * `instantiators` library callees `m(classValue)` that construct reflectively (the JDK's own `Class.newInstance` and `getConstructor().newInstance()` need no key); `handles` the exceptions a
    * java `catch` names for the retired construction.
    */
  final case class Rule(
    members:       Set[String],
    typeClass:     String,
    create:        String = "create",
    instantiators: Set[String] = Set.empty,
    spelling:      Spelling = Spelling.TypeArgument,
    classValue:    ClassValue = ClassValue.Refuse,
    handles:       Set[String] = Set.empty
  )

  object Rule:
    /** the surface rendering: an empty field contributes nothing, so a field's arrival is flat. */
    def render(r: Rule): String =
      val parts = List(
        s"${r.typeClass}.${r.create}",
        s"members=${r.members.toList.sorted.mkString(",")}",
        if r.instantiators.isEmpty then "" else s"instantiators=${r.instantiators.toList.sorted.mkString(",")}",
        if r.spelling == Spelling.TypeArgument then "" else s"spelling=${Spelling.render(r.spelling)}",
        if r.classValue == ClassValue.Refuse then "" else s"classValue=${ClassValue.render(r.classValue)}",
        if r.handles.isEmpty then "" else s"handles=${r.handles.toList.sorted.mkString(",")}"
      )
      parts.filter(_.nonEmpty).mkString("[", "|", "]")

  /** The construction SHAPES this rule recognises, and what else a body does with the class value. */
  final private class Shapes(program: Program, cls: SymId, ctor: Option[SymId]):
    private val Reflective = Set("newInstance", "getConstructor", "getDeclaredConstructor", "getConstructors", "getDeclaredConstructors")

    private def member(m: SymId, owner: SymId, nm: String): Boolean = program.symbolOf(m).exists(s => s.owner == owner && s.name == nm)

    /** the class value an expression names, when it names a parameter directly. */
    private def param(t: Term): Option[SymId] = t match
      case Tree.Ident(s, _, _)        => Some(s)
      case Tree.Typed(e, _, _, _)     => param(e)
      case Tree.Commented(_, e)       => param(e)
      case _                          => scala.None

    /** no argument at all — a java vararg call with nothing passed carries one EMPTY repeated argument. */
    def noArgs(args: List[Term]): Boolean = args match
      case Nil                                 => true
      case List(Tree.Repeated(Nil, _, _))      => true
      case _                                   => false

    /** The parameter a call constructs from: `c.newInstance()`, `c.getConstructor().newInstance()` / `getDeclaredConstructor`, or a named instantiator `f(c)`. */
    def constructed(a: Tree.Apply, instantiators: Set[SymId]): Option[SymId] =
      if instantiators(a.method) then
        a.args match
          case List(arg) => param(arg)
          case _         => scala.None
      else if !noArgs(a.args) then scala.None
      else
        a.fun match
          case Tree.Select(q, _, _, _) if member(a.method, cls, "newInstance") => param(q)
          case Tree.Select(inner: Tree.Apply, _, _, _) if ctor.exists(member(a.method, _, "newInstance")) && noArgs(inner.args) &&
              (member(inner.method, cls, "getConstructor") || member(inner.method, cls, "getDeclaredConstructor")) =>
            inner.fun match
              case Tree.Select(q, _, _, _) => param(q)
              case _                       => scala.None
          case _ => scala.None

    /** Why a member's body cannot convert: reflection on its class value this rule cannot express (a constructor with arguments, an instantiator given more than the class), or — where the
      * parameter is dropped and the rule declares no answer — any other read of the class value. Counted by occurrence: every read not consumed by a construction or a delegating call.
      */
    def bodyRefusal(
      d:             Tree.DefDef,
      params:        Set[SymId],
      candidates:    Map[SymId, SymId],
      instantiators: Set[SymId],
      rule:          Rule,
      keeps:         Boolean
    )(using Program): Option[String] =
      d.rhs.flatMap { body =>
        final case class Count(reads: Int = 0, consumed: Int = 0, chains: Int = 0, getters: Int = 0, reflective: Option[Origin] = scala.None)
        val c = StandardTraversal.scanTerm(body, Count()) {
          case (acc, Tree.Ident(s, _, _)) if params(s) => acc.copy(reads = acc.reads + 1)
          case (acc, a: Tree.Apply)                    =>
            if constructed(a, instantiators).exists(params) then
              acc.copy(consumed = acc.consumed + 1, chains = acc.chains + (if a.fun.isInstanceOf[Tree.Select] && !instantiators(a.method) && !member(a.method, cls, "newInstance") then 1 else 0))
            else
              val delegated = program.definitionOf(a.method) match
                case Some(callee: Tree.DefDef) =>
                  val cps = callee.paramss.flatten
                  a.args.zipWithIndex.count((arg, i) => param(arg).exists(params) && cps.lift(i).exists(v => candidates.contains(v.symbol)))
                case _ => 0
              val onClass = a.fun match
                case Tree.Select(q, _, _, _) => param(q).exists(params) && program.symbolOf(a.method).exists(s => s.owner == cls && Reflective(s.name))
                case _                       => false
              val badInstantiator = instantiators(a.method) && a.args.exists(param(_).exists(params))
              // a nilary `c.getConstructor()` is the inner half of a construction; one with no construction around it is counted below
              val getter     = onClass && noArgs(a.args) && program.symbolOf(a.method).exists(s => s.name == "getConstructor" || s.name == "getDeclaredConstructor")
              val reflective = (onClass && !getter) || badInstantiator
              acc.copy(
                consumed = acc.consumed + delegated,
                getters = acc.getters + (if getter then 1 else 0),
                reflective = if reflective then acc.reflective.orElse(Some(a.origin)) else acc.reflective
              )
          case (acc, _) => acc
        }
        c.reflective.orElse(if c.getters > c.chains then Some(d.origin) else scala.None) match
          case Some(at) =>
            Some(s"the body at ${at.javaPath}:${at.line} uses the `Class` value reflectively in a way this rule cannot express (a constructor with arguments, or an instantiator given more than the class)")
          case scala.None if !keeps && rule.classValue == ClassValue.Refuse && c.reads > c.consumed =>
            Some(s"the body of ${d.origin.javaPath}:${d.origin.line} reads the `Class` value beyond constructing from it, and this rule declares no `classValue` to answer it with")
          case _ => scala.None
      }
