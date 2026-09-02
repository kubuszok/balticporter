package balticporter.transform

import balticporter.core.{MergeablePolicy, SurfacePolicy}
import balticporter.tir.*

/** Rewrite every SUBCLASS of a nominated abstract class so that it passes the parent's constructor
  * arguments as `override val` members instead of `extends P(args)`. The nominated class itself is
  * expected to be DROPPED and INJECTED as a trait with the corresponding abstract vals -- this phase
  * does not transform the type itself.
  *
  * ==The gap this fills==
  * A reference hand port may reshape a java abstract class into a Scala trait with abstract vals.
  * The emitted code faithfully translates subclasses as `extends Pool[T](arg1, arg2)`, which is a
  * compile error against a trait. This phase rewrites the extends clause.
  *
  * ==Two populations==
  * A subclass may extend the nominated type WITH super args (the widest constructor path) or
  * WITHOUT (the nilary path, which in Java delegates with default values). Both get override vals:
  * with-args from the super args, without-args from the spec's defaults.
  *
  * ==Kind==
  * CLAUDE.md section 1(b). Empty specs = no-op.
  */
final class ClassToTraitTransform(
    val specs: Map[String, List[ClassToTraitTransform.ParamMapping]] = Map.empty,
) extends Phase, SurfacePolicy, MergeablePolicy:

  def name: String = ClassToTraitTransform.Name

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
        (fqn, myMappings) <- specs.toList
        theirMappings     <- o.specs.get(fqn).toList
        if myMappings.sortBy(_.index) != theirMappings.sortBy(_.index)
      yield fqn
      if clashes.nonEmpty then
        Left(clashes.sorted.mkString("both modules declare `class-to-trait` for ", ", ",
          " with DIFFERENT param mappings"))
      else
        Right(MergeablePolicy.Merged(
          new ClassToTraitTransform(specs ++ o.specs),
          (o.specs.keySet -- specs.keySet).map(MergeablePolicy.subjectOf)))
    case other =>
      Left(s"`${other.name}` is not a `ClassToTraitTransform`")

  override def run(program: Program): Program =
    if specs.isEmpty then return program
    given Program = program

    val fqnToSym: Map[String, SymId] =
      program.symbols.all.filter(s => specs.contains(s.fullName)).map(s => s.fullName -> s.id).toMap
    val traitSyms: Set[SymId] = fqnToSym.values.toSet
    if traitSyms.isEmpty then return program

    var next = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    def fresh(): SymId = { val id = SymId(next); next += 1; id }
    var newSymbols = List.empty[Symbol]

    val units = program.units.map { cd =>
      transformRec(cd, traitSyms, program, fresh, s => newSymbols = s :: newSymbols)
    }
    program.rebuilt(units = units, symbols = SymbolTable(program.symbols.all ++ newSymbols))

  private def transformRec(
      cd: Tree.ClassDef, traitSyms: Set[SymId], program: Program,
      fresh: () => SymId, addSym: Symbol => Unit,
  )(using Program): Tree.ClassDef =
    val body = cd.body.map {
      case nested: Tree.ClassDef => transformRec(nested, traitSyms, program, fresh, addSym)
      case other => other
    }
    val cd1 = if body ne cd.body then cd.copy(body = body) else cd
    parentTraitOf(cd1, traitSyms) match
      case Some(fqn) => rewriteSubclass(program, cd1, fqn, specs(fqn), fresh, addSym)
      case None      => cd1

  private def parentTraitOf(cd: Tree.ClassDef, traitSyms: Set[SymId])(using Program): Option[String] =
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s)      => Some(s)
      case TypeRepr.AppliedType(tc, _) => headSym(tc)
      case _                           => None
    cd.parents.iterator.flatMap {
      case tt: TypeTree => headSym(tt.tpe)
      case t: Term      => headSym(t.tpe)
    }.collectFirst { case s if traitSyms.contains(s) =>
      summon[Program].symbolOf(s).map(_.fullName).getOrElse("")
    }.filter(_.nonEmpty)

  private def rewriteSubclass(
      program: Program, cd: Tree.ClassDef, parentFqn: String,
      mappings: List[ClassToTraitTransform.ParamMapping],
      fresh: () => SymId, addSym: Symbol => Unit,
  ): Tree.ClassDef =
    val cdSym = program.symbolOf(cd.symbol) match
      case Some(s) => s
      case None    => return cd
    val origin = cd.origin

    val ctors = cd.body.collect { case d: Tree.DefDef if program.symbolOf(d.symbol).exists(_.name == "<init>") => d }

    // Find the widest constructor that calls super(args)
    val widestWithArgs = ctors
      .map(c => c -> CtorFunnel.superArgsOf(program, c))
      .filter(_._2.nonEmpty)
      .maxByOption(_._2.size)

    val overrideVals: List[Tree.ValDef] = widestWithArgs match
      case Some((_, superArgs)) =>
        mappings.flatMap { m =>
          if m.index < superArgs.size then
            Some(mkVal(m, superArgs(m.index).tpe, Some(superArgs(m.index)), origin, cd.symbol, cdSym, fresh, addSym))
          else None
        }
      case None =>
        // Nilary super -- use defaults from the spec
        val allHaveDefaults = mappings.forall(_.defaultLiteral.isDefined)
        if !allHaveDefaults then return cd
        mappings.flatMap { m =>
          m.defaultLiteral.map { lit =>
            mkVal(m, lit.tpe, Some(lit), origin, cd.symbol, cdSym, fresh, addSym)
          }
        }

    if overrideVals.isEmpty then return cd

    record(Decision(
      kind       = Decision.Kind.RetypedSignature,
      subject    = cd.symbol,
      subjectFqn = cdSym.fullName,
      detail     = Map("parent" -> parentFqn, "why" -> "class-to-trait: override vals replace super args"),
      reason     = Reason.Configured(name, parentFqn),
      origin     = origin,
    ))

    // Strip super args from the widest constructor
    val strippedBody = widestWithArgs match
      case Some((widestCtor, _)) =>
        cd.body.map {
          case d: Tree.DefDef if d.symbol == widestCtor.symbol => stripSuperArgs(program, d)
          case other => other
        }
      case None => cd.body

    // Strip replayed assignments to the nominated parent's fields from ALL constructor bodies
    val parentFieldNames = mappings.map(_.valName).toSet ++ parentOwnedFields(program, parentFqn)
    val cleanedBody = strippedBody.map {
      case d: Tree.DefDef if program.symbolOf(d.symbol).exists(_.name == "<init>") =>
        stripReplayedAssignments(program, d, parentFieldNames)
      case other => other
    }

    val (ctorDefs, rest) = cleanedBody.partition {
      case d: Tree.DefDef => program.symbolOf(d.symbol).exists(_.name == "<init>")
      case _ => false
    }
    cd.copy(body = ctorDefs ++ overrideVals ++ rest)

  private def mkVal(
      m: ClassToTraitTransform.ParamMapping, tpe: TypeRepr, rhs: Option[Term],
      origin: Origin, owner: SymId, ownerSym: Symbol,
      fresh: () => SymId, addSym: Symbol => Unit,
  ): Tree.ValDef =
    val valId = fresh()
    addSym(Symbol(valId, m.valName, s"${ownerSym.fullName}#${m.valName}",
      Flags(isOverride = true, isProtected = true), owner, tpe, origin))
    Tree.ValDef(valId, TypeTree(tpe, origin), rhs, origin)

  private def stripSuperArgs(program: Program, d: Tree.DefDef): Tree.DefDef =
    val stmts = CtorFunnel.stmtsOf(d)
    CtorFunnel.headStmt(d) match
      case Some(Tree.Apply(Tree.Select(_: Tree.Super, m, _, _), args, _, _, _))
          if program.symbolOf(m).exists(_.name == "<init>") && args.nonEmpty =>
        val newHead: Term = stmts.head match
          case t: Term => stripTopSuperArgs(program, Tree.uncomment(t)) match
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

  private def stripTopSuperArgs(program: Program, t: Term): Option[Term] = t match
    case Tree.Apply(sel @ Tree.Select(_: Tree.Super, m, _, _), args, method, tpe, origin)
        if program.symbolOf(m).exists(_.name == "<init>") && args.nonEmpty =>
      Some(Tree.Apply(sel, Nil, method, tpe, origin))
    case _ => None

  /** Strip assignments to fields that are now override vals in the trait.
    * The CtorFunnel replays parent constructor bodies as assignments in subclass constructors. */
  private def stripReplayedAssignments(program: Program, d: Tree.DefDef, fieldNames: Set[String]): Tree.DefDef =
    d.rhs match
      case Some(body) =>
        val newBody = stripAssigns(program, body, fieldNames)
        if newBody ne body then d.copy(rhs = Some(newBody)) else d
      case None => d

  private def stripAssigns(program: Program, t: Term, fieldNames: Set[String]): Term = t match
    case Tree.Block(stmts, expr, tpe, origin, trailing) =>
      val filtered = stmts.flatMap {
        case a: Tree.Assign if isFieldAssign(program, a, fieldNames) => None
        case s: Term   => Some(stripAssigns(program, s, fieldNames))
        case other     => Some(other)
      }
      val newExpr = stripAssigns(program, expr, fieldNames)
      if (filtered ne stmts) || (newExpr ne expr) then Tree.Block(filtered, newExpr, tpe, origin, trailing) else t
    case _ => t

  /** Field names owned by the nominated parent type -- assignments to these are stripped from
    * replayed constructor bodies because the trait body handles them. */
  private def parentOwnedFields(program: Program, parentFqn: String): Set[String] =
    program.symbols.all.filter { s =>
      program.symbolOf(s.owner).exists(_.fullName == parentFqn) &&
      !s.flags.isAbstract && s.info != TypeRepr.NoType && s.name != "<init>" &&
      !s.flags.isStatic
    }.map(_.name).toSet

  private def isFieldAssign(program: Program, a: Tree.Assign, fieldNames: Set[String]): Boolean =
    a.lhs match
      case Tree.Select(_, sym, _, _) => program.symbolOf(sym).exists(s => fieldNames.contains(s.name))
      case Tree.Ident(sym, _, _)     => program.symbolOf(sym).exists(s => fieldNames.contains(s.name))
      case _                         => false

object ClassToTraitTransform:
  val Name: String = "class-to-trait"

  /** @param defaultLiteral a TIR literal for subclasses that extend the type with no args (nilary
    *                       constructor path, e.g. `Pool()` -> `Pool(16, MAX_VALUE)`) */
  final case class ParamMapping(index: Int, valName: String, defaultLiteral: Option[Term] = None)
