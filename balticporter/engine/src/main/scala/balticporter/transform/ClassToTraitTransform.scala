package balticporter.transform

import balticporter.core.{MergeablePolicy, SurfacePolicy}
import balticporter.tir.*

/** Rewrite a nominated abstract class into a trait and transform every subclass (named and
  * anonymous) to use `override val` members instead of constructor arguments.
  *
  * ==Declaration side==
  * The nominated type's OWN ClassDef is rewritten in the TIR: constructors removed, the mapped
  * parameters become abstract `val` members, flags set to `isTrait`. This is done even though
  * emission drops the type in favour of the injected file, because the CtorFunnel then sees a
  * parent with no constructor -- so there is nothing to replay and nothing to widen
  * (`ctor-replay-widening` does not fire). The emitter's override derivation sees the abstract vals
  * as parent members, which settles the `max$shadow` rename by section 4.55's implementation-pair rule.
  *
  * ==Kind==
  * CLAUDE.md section 1(b). Empty specs = no-op.
  */
final class ClassToTraitTransform(
    val specs: Map[String, List[ClassToTraitTransform.ParamMapping]] = Map.empty,
) extends Phase, SurfacePolicy, MergeablePolicy:

  import ClassToTraitTransform.*

  def name: String = Name
  override def runsBefore: Set[String] = Set("package-rename")

  def surfaceFingerprint: String =
    specs.toList.sortBy(_._1).map { (fqn, mappings) =>
      val ms = mappings.sortBy(_.index).map(m => s"${m.index}:${m.valName}").mkString(",")
      s"$fqn[$ms]"
    }.mkString(";")

  def subjects: Set[String] = specs.keySet.map(MergeablePolicy.subjectOf)

  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case o: ClassToTraitTransform =>
      val clashes = for
        (fqn, my) <- specs.toList; theirs <- o.specs.get(fqn).toList
        if my.sortBy(_.index) != theirs.sortBy(_.index)
      yield fqn
      if clashes.nonEmpty then Left(clashes.sorted.mkString(
        "both modules declare `class-to-trait` for ", ", ", " with DIFFERENT param mappings"))
      else Right(MergeablePolicy.Merged(
        new ClassToTraitTransform(specs ++ o.specs),
        (o.specs.keySet -- specs.keySet).map(MergeablePolicy.subjectOf)))
    case _ => Left(s"`${later.name}` is not a `ClassToTraitTransform`")

  // ---- resolved at the start of run ----
  private var resolved = Map.empty[SymId, ResolvedSpec]
  private var fresh: () => SymId = () => SymId.None
  private var newSyms = List.newBuilder[Symbol]

  override def run(program: Program): Program =
    if specs.isEmpty then return program
    given Program = program

    // Resolve nominated types
    resolved = (for
      (fqn, mappings) <- specs.toList
      sym             <- program.symbols.all.find(_.fullName == fqn)
    yield
      val parentCd = program.units.find(_.symbol == sym.id)
      val widestCtor = parentCd.toList.flatMap { cd =>
        cd.body.collect { case d: Tree.DefDef if program.symbolOf(d.symbol).exists(_.name == "<init>") => d }
          .maxByOption(_.paramss.flatten.size)
      }.headOption
      val formalTypes = widestCtor.toList.flatMap(_.paramss.flatten.map(_.tpt.tpe))
      val defaults = parentCd.toList.flatMap { cd =>
        val ctors = cd.body.collect { case d: Tree.DefDef if program.symbolOf(d.symbol).exists(_.name == "<init>") => d }
        ctors.find(_.paramss.flatten.isEmpty).toList.flatMap { nc =>
          CtorFunnel.stmtsOf(nc).headOption.collect { case t: Term => Tree.uncomment(t) }.toList.flatMap {
            case Tree.Apply(Tree.Select(_, m, _, _), args, _, _, _)
                if program.symbolOf(m).exists(_.name == "<init>") => args
            case _ => Nil
          }
        }
      }
      val ownedFields = program.symbols.all.filter { s =>
        s.owner == sym.id && s.name != "<init>" && !s.flags.isStatic && !s.flags.isAbstract
      }.map(_.name).toSet
      sym.id -> ResolvedSpec(fqn, mappings, formalTypes, defaults, ownedFields, sym.id)
    ).toMap

    if resolved.isEmpty then return program

    var nextId = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    fresh = () => { val id = SymId(nextId); nextId += 1; id }
    newSyms = List.newBuilder[Symbol]

    // 1) Transform the nominated types THEMSELVES into traits (declaration side)
    val units1 = program.units.map { cd =>
      if resolved.contains(cd.symbol) then transformNominatedType(cd, resolved(cd.symbol), program)
      else cd
    }

    // 2) Use the standard traversal to transform subclasses (named + anonymous)
    val p1 = program.rebuilt(units1, program.symbols)
    val units2 = p1.units.map(u => StandardTraversal.mapClassDef(this, u)(using p1))
    val symbols2 = StandardTraversal.mapSymbols(this, p1.symbols)(using p1)

    // 3) Update symbol flags for nominated types (isTrait)
    val allSyms = symbols2.all.map { s =>
      if resolved.contains(s.id) then
        s.copy(flags = s.flags.copy(isTrait = true, isAbstract = false))
      else s
    } ++ newSyms.result()

    p1.rebuilt(units2, SymbolTable(allSyms))

  // ---- declaration side: transform the nominated type itself ----

  private def transformNominatedType(cd: Tree.ClassDef, spec: ResolvedSpec, program: Program): Tree.ClassDef =
    val origin = cd.origin
    // Remove all constructors from the body
    val bodyNoCtors = cd.body.filterNot {
      case d: Tree.DefDef => program.symbolOf(d.symbol).exists(_.name == "<init>")
      case _ => false
    }
    // Add abstract val definitions for the mapped parameters
    val abstractVals = spec.mappings.flatMap { m =>
      if m.index < spec.formalTypes.size then
        val tpe = spec.formalTypes(m.index)
        val valId = fresh()
        newSyms += Symbol(valId, m.valName, s"${program.symbolOf(cd.symbol).map(_.fullName).getOrElse("")}#${m.valName}",
          Flags(isAbstract = true, isProtected = true), cd.symbol, tpe, origin)
        Some(Tree.ValDef(valId, TypeTree(tpe, origin), None, origin))
      else None
    }
    record(Decision(
      kind       = Decision.Kind.RetypedSignature,
      subject    = cd.symbol,
      subjectFqn = program.symbolOf(cd.symbol).map(_.fullName).getOrElse(""),
      detail     = Map("why" -> "class-to-trait: abstract class -> trait with abstract vals"),
      reason     = Reason.Configured(name, spec.fqn),
      origin     = origin,
    ))
    cd.copy(body = abstractVals ++ bodyNoCtors)

  // ---- MiniPhase hooks: transform subclasses ----

  override def transformClassDef(cd: Tree.ClassDef)(using Program): Tree.ClassDef =
    if resolved.isEmpty then return cd
    if resolved.contains(cd.symbol) then return cd // the nominated type itself, already handled
    parentSpec(cd) match
      case Some(spec) => rewriteNamedSubclass(cd, spec)
      case None       => cd

  override def transformNew(n: Tree.New)(using program: Program): Term =
    if resolved.isEmpty then return n
    val headSym = ClassToTraitTransform.headSymOf(n.tpt.tpe)
    headSym.flatMap(resolved.get) match
      case None       => n
      case Some(spec) => rewriteAnonNew(n, spec)

  // ---- named subclass rewrite ----

  private def rewriteNamedSubclass(cd: Tree.ClassDef, spec: ResolvedSpec)(using program: Program): Tree.ClassDef =
    val cdSym = program.symbolOf(cd.symbol) match
      case Some(s) => s
      case None    => return cd
    val origin = cd.origin
    val ctors = cd.body.collect { case d: Tree.DefDef if program.symbolOf(d.symbol).exists(_.name == "<init>") => d }

    val widestWithArgs = ctors
      .map(c => c -> CtorFunnel.superArgsOf(program, c))
      .filter(_._2.nonEmpty)
      .maxByOption(_._2.size)

    val overrideVals = widestWithArgs match
      case Some((_, superArgs)) =>
        spec.mappings.flatMap { m =>
          if m.index < superArgs.size then
            val tpe = if m.index < spec.formalTypes.size then spec.formalTypes(m.index) else superArgs(m.index).tpe
            Some(mkVal(m, tpe, Some(superArgs(m.index)), origin, cd.symbol, cdSym))
          else None
        }
      case None =>
        if spec.defaults.size < spec.mappings.size then return cd
        spec.mappings.flatMap { m =>
          if m.index < spec.defaults.size && m.index < spec.formalTypes.size then
            Some(mkVal(m, spec.formalTypes(m.index), Some(spec.defaults(m.index)), origin, cd.symbol, cdSym))
          else None
        }

    if overrideVals.isEmpty then return cd

    record(Decision(
      kind       = Decision.Kind.RetypedSignature,
      subject    = cd.symbol,
      subjectFqn = cdSym.fullName,
      detail     = Map("parent" -> spec.fqn, "why" -> "class-to-trait: override vals replace super args"),
      reason     = Reason.Configured(name, spec.fqn),
      origin     = origin,
    ))

    val strippedBody = widestWithArgs match
      case Some((widestCtor, _)) =>
        cd.body.map {
          case d: Tree.DefDef if d.symbol == widestCtor.symbol => stripSuperArgs(d)(using summon[Program])
          case other => other
        }
      case None => cd.body

    val strippedFields = spec.mappings.map(_.valName).toSet ++ spec.ownedFields
    val cleanedBody = strippedBody.map {
      case d: Tree.DefDef if program.symbolOf(d.symbol).exists(_.name == "<init>") =>
        stripReplayedAssignments(d, strippedFields)(using summon[Program])
      case other => other
    }

    val (ctorDefs, rest) = cleanedBody.partition {
      case d: Tree.DefDef => program.symbolOf(d.symbol).exists(_.name == "<init>")
      case _ => false
    }
    cd.copy(body = ctorDefs ++ overrideVals ++ rest)

  // ---- anonymous subclass rewrite ----

  private def rewriteAnonNew(n: Tree.New, spec: ResolvedSpec)(using program: Program): Term =
    // The anonymous `new Pool(a, b) { ... }` appears as Apply(New(..., Some(AnonClass)), args).
    // But by the time transformNew runs, the Apply has already been traversed and transformNew sees
    // just the New node. The args are in the PARENT Apply. We need to handle this differently:
    // we add override vals to the AnonClass body with the spec defaults, since the actual args
    // will be stripped from the Apply by the time the emitter sees them.
    // Actually, the StandardTraversal calls transformNew on the New, not the Apply.
    // The constructor args are NOT part of the New -- they're in the Apply wrapping it.
    // For anonymous classes, we add override vals with defaults to the AnonClass body.
    n.anon match
      case None => n
      case Some(anon) =>
        val origin = n.origin
        val anonSym = program.symbolOf(anon.symbol).getOrElse(return n)
        val overrideVals = if spec.defaults.size >= spec.mappings.size then
          spec.mappings.flatMap { m =>
            if m.index < spec.defaults.size && m.index < spec.formalTypes.size then
              Some(mkVal(m, spec.formalTypes(m.index), Some(spec.defaults(m.index)), origin, anon.symbol, anonSym))
            else None
          }
        else Nil
        // For now, add defaults. The actual args from the Apply are handled by transformApply.
        if overrideVals.isEmpty then return n
        n.copy(anon = Some(anon.copy(body = overrideVals ++ anon.body)))

  override def transformApply(app: Tree.Apply)(using program: Program): Term =
    if resolved.isEmpty then return app
    // Check if this Apply wraps a New of a nominated type with constructor args
    app.fun match
      case n: Tree.New =>
        val headSym = ClassToTraitTransform.headSymOf(n.tpt.tpe)
        headSym.flatMap(resolved.get) match
          case Some(spec) if app.args.nonEmpty =>
            // Strip the constructor args and put them as override vals in the AnonClass body
            n.anon match
              case Some(anon) =>
                val origin = app.origin
                val anonSym = program.symbolOf(anon.symbol).getOrElse(return app)
                val overrideVals = spec.mappings.flatMap { m =>
                  if m.index < app.args.size then
                    val tpe = if m.index < spec.formalTypes.size then spec.formalTypes(m.index) else app.args(m.index).tpe
                    Some(mkVal(m, tpe, Some(app.args(m.index)), origin, anon.symbol, anonSym))
                  else None
                }
                val newAnon = anon.copy(body = overrideVals ++ anon.body)
                val newNew = n.copy(anon = Some(newAnon))
                Tree.Apply(newNew, Nil, app.method, app.tpe, app.origin)
              case None =>
                // Non-anonymous new Pool(args) -- strip args (the class itself handles override vals)
                Tree.Apply(n, Nil, app.method, app.tpe, app.origin)
          case _ => app
      case _ => app

  // ---- helpers ----

  private def mkVal(m: ParamMapping, tpe: TypeRepr, rhs: Option[Term],
      origin: Origin, owner: SymId, ownerSym: Symbol): Tree.ValDef =
    val valId = fresh()
    newSyms += Symbol(valId, m.valName, s"${ownerSym.fullName}#${m.valName}",
      Flags(isOverride = true, isProtected = true), owner, tpe, origin)
    Tree.ValDef(valId, TypeTree(tpe, origin), rhs, origin)

  private def parentSpec(cd: Tree.ClassDef)(using Program): Option[ResolvedSpec] =
    cd.parents.iterator.flatMap {
      case tt: TypeTree => headSymOf(tt.tpe)
      case t: Term      => headSymOf(t.tpe)
    }.collectFirst { case s if resolved.contains(s) => resolved(s) }

  private def stripSuperArgs(d: Tree.DefDef)(using program: Program): Tree.DefDef =
    val stmts = CtorFunnel.stmtsOf(d)
    CtorFunnel.headStmt(d) match
      case Some(Tree.Apply(Tree.Select(_: Tree.Super, m, _, _), args, _, _, _))
          if program.symbolOf(m).exists(_.name == "<init>") && args.nonEmpty =>
        val newHead: Term = stmts.head match
          case t: Term => stripTopSuperArgs(Tree.uncomment(t))(using program) match
              case Some(stripped) => Tree.recomment(t, stripped)
              case None           => t
          case _ => return d
        val newStmts = newHead :: stmts.tail
        val trailing = CtorFunnel.trailingOf(d)
        val newBody: Term = newStmts match
          case List(single: Term) if trailing.isEmpty => single
          case _ =>
            val last = newStmts.last match { case t: Term => t; case _ => return d }
            Tree.Block(newStmts.init.collect { case s: Statement => s }, last,
                       d.rhs.map(_.tpe).getOrElse(TypeRepr.NoType), d.origin, trailing)
        d.copy(rhs = Some(newBody))
      case _ => d

  private def stripTopSuperArgs(t: Term)(using program: Program): Option[Term] = t match
    case Tree.Apply(sel @ Tree.Select(_: Tree.Super, m, _, _), args, method, tpe, origin)
        if program.symbolOf(m).exists(_.name == "<init>") && args.nonEmpty =>
      Some(Tree.Apply(sel, Nil, method, tpe, origin))
    case _ => None

  private def stripReplayedAssignments(d: Tree.DefDef, fieldNames: Set[String])(using program: Program): Tree.DefDef =
    d.rhs match
      case Some(body) =>
        val newBody = stripAssigns(body, fieldNames)
        if newBody ne body then d.copy(rhs = Some(newBody)) else d
      case None => d

  private def stripAssigns(t: Term, fieldNames: Set[String])(using program: Program): Term = t match
    case Tree.Block(stmts, expr, tpe, origin, trailing) =>
      val filtered = stmts.flatMap {
        case a: Tree.Assign if isFieldAssign(a, fieldNames) => None
        case s: Term   => Some(stripAssigns(s, fieldNames))
        case other     => Some(other)
      }
      val newExpr = stripAssigns(expr, fieldNames)
      if (filtered ne stmts) || (newExpr ne expr) then Tree.Block(filtered, newExpr, tpe, origin, trailing) else t
    case _ => t

  private def isFieldAssign(a: Tree.Assign, fieldNames: Set[String])(using program: Program): Boolean =
    a.lhs match
      case Tree.Select(_, sym, _, _) => program.symbolOf(sym).exists(s => fieldNames.contains(s.name))
      case Tree.Ident(sym, _, _)     => program.symbolOf(sym).exists(s => fieldNames.contains(s.name))
      case _                         => false

object ClassToTraitTransform:
  val Name: String = "class-to-trait"

  final case class ParamMapping(index: Int, valName: String,
    defaultLiteral: Option[Term] = None)

  private[transform] final case class ResolvedSpec(
    fqn: String, mappings: List[ParamMapping], formalTypes: List[TypeRepr],
    defaults: List[Term], ownedFields: Set[String], parentSymId: SymId,
  )

  private[transform] def headSymOf(tpe: TypeRepr): Option[SymId] = tpe match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSymOf(tc)
    case _                           => None
