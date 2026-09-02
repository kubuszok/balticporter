package balticporter.transform

import balticporter.core.{MergeablePolicy, SurfacePolicy}
import balticporter.tir.*

/** Rewrite every SUBCLASS of a nominated abstract class so that constructor arguments to the
  * nominated parent become `override val` members. The nominated class itself is DROPPED and
  * INJECTED as a trait with abstract vals.
  *
  * ==Two populations==
  * A subclass may extend the nominated type WITH super args (widest constructor path) or WITHOUT
  * (nilary path, delegating in Java to `Pool(16, Integer.MAX_VALUE)`). Both get override vals --
  * with-args from the super args, without-args from the Java nilary delegation defaults.
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
        (fqn, my) <- specs.toList; theirs <- o.specs.get(fqn).toList
        if my.sortBy(_.index) != theirs.sortBy(_.index)
      yield fqn
      if clashes.nonEmpty then Left(clashes.sorted.mkString(
        "both modules declare `class-to-trait` for ", ", ", " with DIFFERENT param mappings"))
      else Right(MergeablePolicy.Merged(
        new ClassToTraitTransform(specs ++ o.specs),
        (o.specs.keySet -- specs.keySet).map(MergeablePolicy.subjectOf)))
    case _ => Left(s"`${later.name}` is not a `ClassToTraitTransform`")

  override def run(program: Program): Program =
    if specs.isEmpty then return program
    given Program = program

    // Resolve nominated types to their SymIds and precompute their formal parameter types.
    // The dropped type is STILL in the program (a drop is an emission decision).
    val resolved: Map[SymId, ClassToTraitTransform.ResolvedSpec] = (for
      (fqn, mappings) <- specs.toList
      sym             <- program.symbols.all.find(_.fullName == fqn)
    yield
      // Find the nominated type's ClassDef and its widest constructor
      val parentCd = program.units.find(_.symbol == sym.id)
      val formalTypes: List[TypeRepr] = parentCd.toList.flatMap { cd =>
        val ctors = cd.body.collect { case d: Tree.DefDef if program.symbolOf(d.symbol).exists(_.name == "<init>") => d }
        ctors.maxByOption(_.paramss.flatten.size).toList.flatMap(_.paramss.flatten.map(_.tpt.tpe))
      }
      // Build default vals from the nilary constructor's delegation chain:
      // Pool() -> Pool(16, MAX_VALUE). Read the this() delegation args.
      val defaults: List[Term] = parentCd.toList.flatMap { cd =>
        val ctors = cd.body.collect { case d: Tree.DefDef if program.symbolOf(d.symbol).exists(_.name == "<init>") => d }
        val nilary = ctors.find(_.paramss.flatten.isEmpty)
        nilary.toList.flatMap { nc =>
          // The nilary constructor calls this(args) -- extract those args
          CtorFunnel.stmtsOf(nc).headOption.collect { case t: Term => Tree.uncomment(t) }.toList.flatMap {
            case Tree.Apply(Tree.Select(_, m, _, _), args, _, _, _)
                if program.symbolOf(m).exists(_.name == "<init>") => args
            case _ => Nil
          }
        }
      }
      // Collect field names owned by the parent (for stripping replayed assignments)
      val ownedFields: Set[String] = program.symbols.all.filter { s =>
        s.owner == sym.id && s.name != "<init>" && !s.flags.isStatic && !s.flags.isAbstract
      }.map(_.name).toSet
      sym.id -> ClassToTraitTransform.ResolvedSpec(fqn, mappings, formalTypes, defaults, ownedFields)
    ).toMap

    if resolved.isEmpty then return program

    var next = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    def fresh(): SymId = { val id = SymId(next); next += 1; id }
    var newSymbols = List.empty[Symbol]

    val units = program.units.map { cd =>
      transformRec(cd, resolved, program, fresh, s => newSymbols = s :: newSymbols)
    }
    program.rebuilt(units = units, symbols = SymbolTable(program.symbols.all ++ newSymbols))

  private def transformRec(
      cd: Tree.ClassDef, resolved: Map[SymId, ClassToTraitTransform.ResolvedSpec],
      program: Program, fresh: () => SymId, addSym: Symbol => Unit,
  )(using Program): Tree.ClassDef =
    val body = cd.body.map {
      case nested: Tree.ClassDef => transformRec(nested, resolved, program, fresh, addSym)
      case other => other
    }
    val cd1 = if body ne cd.body then cd.copy(body = body) else cd
    parentSpec(cd1, resolved) match
      case Some(spec) => rewriteSubclass(program, cd1, spec, fresh, addSym)
      case None       => cd1

  private def parentSpec(cd: Tree.ClassDef, resolved: Map[SymId, ClassToTraitTransform.ResolvedSpec])(using Program): Option[ClassToTraitTransform.ResolvedSpec] =
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s)      => Some(s)
      case TypeRepr.AppliedType(tc, _) => headSym(tc)
      case _                           => None
    cd.parents.iterator.flatMap {
      case tt: TypeTree => headSym(tt.tpe)
      case t: Term      => headSym(t.tpe)
    }.collectFirst { case s if resolved.contains(s) => resolved(s) }

  private def rewriteSubclass(
      program: Program, cd: Tree.ClassDef, spec: ClassToTraitTransform.ResolvedSpec,
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
        spec.mappings.flatMap { m =>
          if m.index < superArgs.size && m.index < spec.formalTypes.size then
            val tpe = spec.formalTypes(m.index) // use the DECLARED type, not the arg's inferred type
            Some(mkVal(m, tpe, Some(superArgs(m.index)), origin, cd.symbol, cdSym, fresh, addSym))
          else if m.index < superArgs.size then
            Some(mkVal(m, superArgs(m.index).tpe, Some(superArgs(m.index)), origin, cd.symbol, cdSym, fresh, addSym))
          else None
        }
      case None =>
        // Nilary super -- use defaults from the nominated type's nilary delegation chain
        if spec.defaults.size < spec.mappings.size then return cd
        spec.mappings.flatMap { m =>
          if m.index < spec.defaults.size && m.index < spec.formalTypes.size then
            val tpe = spec.formalTypes(m.index)
            Some(mkVal(m, tpe, Some(spec.defaults(m.index)), origin, cd.symbol, cdSym, fresh, addSym))
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

    // Strip super args from the widest constructor
    val strippedBody = widestWithArgs match
      case Some((widestCtor, _)) =>
        cd.body.map {
          case d: Tree.DefDef if d.symbol == widestCtor.symbol => stripSuperArgs(program, d)
          case other => other
        }
      case None => cd.body

    // Strip replayed assignments to ALL parent-owned fields (freeObjects, max, peak, etc.)
    // from constructor bodies. These are CtorFunnel replays of the parent's constructor body
    // (the parent is now a trait whose body initialises its own fields from the override vals).
    val strippedFields = spec.mappings.map(_.valName).toSet ++ spec.ownedFields
    val cleanedBody = strippedBody.map {
      case d: Tree.DefDef if program.symbolOf(d.symbol).exists(_.name == "<init>") =>
        stripReplayedAssignments(program, d, strippedFields)
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

  private def isFieldAssign(program: Program, a: Tree.Assign, fieldNames: Set[String]): Boolean =
    a.lhs match
      case Tree.Select(_, sym, _, _) => program.symbolOf(sym).exists(s => fieldNames.contains(s.name))
      case Tree.Ident(sym, _, _)     => program.symbolOf(sym).exists(s => fieldNames.contains(s.name))
      case _                         => false

object ClassToTraitTransform:
  val Name: String = "class-to-trait"

  final case class ParamMapping(index: Int, valName: String,
    /** default literal for the nilary-constructor population -- UNUSED now: defaults are read
      * from the nominated type's own nilary constructor delegation chain. Kept for future
      * .conf spelling compatibility. */
    defaultLiteral: Option[Term] = None)

  /** Precomputed at the start of `run`, once per nominated type. */
  private[transform] final case class ResolvedSpec(
    fqn: String,
    mappings: List[ParamMapping],
    formalTypes: List[TypeRepr],
    defaults: List[Term],
    ownedFields: Set[String],
  )
