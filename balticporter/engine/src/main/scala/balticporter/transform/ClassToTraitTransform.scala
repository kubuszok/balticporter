package balticporter.transform

import balticporter.core.{MergeablePolicy, SurfacePolicy}
import balticporter.tir.*

/** Rewrite every SUBCLASS of a nominated abstract class so that it passes the parent's constructor
  * arguments as `override val` members instead of `extends P(args)`. The nominated class itself is
  * expected to be DROPPED and INJECTED as a trait with the corresponding abstract vals — this phase
  * does not transform the type itself.
  *
  * ==The gap this fills==
  * Java's `Pool(int initialCapacity, int max)` is an abstract class whose constructor sets fields.
  * sge hand-ported it as a `trait Pool[A]` with `protected val initialCapacity: Int` and
  * `protected val max: Int` as abstract vals, and every subclass overrides them. The mechanical port
  * faithfully emits `extends Pool[T](args)`, which does not compile against the trait.
  *
  * ==Kind==
  * CLAUDE.md §1(b). The MECHANISM — strip super args, add override vals — is a fact about Java
  * abstract classes becoming Scala traits, true whenever a hand port makes that choice. WHICH class
  * and WHICH constructor params map to WHICH val names is per-library policy. An empty map is a
  * no-op.
  *
  * ==Configuration==
  * {{{
  * { transform = "class-to-trait"
  *   specs {
  *     "com.badlogic.gdx.utils.Pool" {
  *       params = [
  *         { index = 0, name = "initialCapacity" }
  *         { index = 1, name = "max" }
  *       ]
  *     }
  *   }
  * }
  * }}}
  *
  * ==Ordering==
  * BEFORE `package-rename` (the keys are upstream FQNs). No other ordering constraints.
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
          " with DIFFERENT param mappings — two answers for one type"))
      else
        Right(MergeablePolicy.Merged(
          new ClassToTraitTransform(specs ++ o.specs),
          (o.specs.keySet -- specs.keySet).map(MergeablePolicy.subjectOf)))
    case other =>
      Left(s"`${other.name}` is not a `ClassToTraitTransform`, so there is no table to compose")

  override def run(program: Program): Program =
    if specs.isEmpty then return program

    given Program = program

    val fqnToSym: Map[String, SymId] =
      program.symbols.all.filter(s => specs.contains(s.fullName)).map(s => s.fullName -> s.id).toMap

    val traitSyms: Set[SymId] = fqnToSym.values.toSet

    if traitSyms.isEmpty then return program

    var next    = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    def fresh(): SymId = { val id = SymId(next); next += 1; id }

    var newSymbols = List.empty[Symbol]

    val units = program.units.map { cd =>
      val parentTraitFqn = parentTraitOf(cd, traitSyms)
      parentTraitFqn match
        case Some(fqn) =>
          val mappings = specs(fqn)
          rewriteSubclass(program, cd, fqn, mappings, fresh, newSymbols = s => newSymbols = s :: newSymbols)
        case None => cd
    }

    val allSyms = program.symbols.all ++ newSymbols
    program.rebuilt(units = units, symbols = SymbolTable(allSyms))

  /** Find whether any parent of `cd` is a nominated trait type. */
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

  /** Rewrite a subclass: strip super(args), add override val members. */
  private def rewriteSubclass(
      program: Program,
      cd: Tree.ClassDef,
      parentFqn: String,
      mappings: List[ClassToTraitTransform.ParamMapping],
      fresh: () => SymId,
      newSymbols: Symbol => Unit,
  ): Tree.ClassDef =
    val cdSym   = program.symbolOf(cd.symbol) match
      case Some(s) => s
      case None    => return cd // no symbol = nothing to transform
    val origin  = cd.origin

    val ctors = cd.body.collect { case d: Tree.DefDef if program.symbolOf(d.symbol).exists(_.name == "<init>") => d }
    val widestCtor = ctors.maxByOption(_.paramss.flatten.size) match
      case Some(c) => c
      case None    => return cd // no constructors = nothing to strip

    val superArgs = CtorFunnel.superArgsOf(program, widestCtor)
    if superArgs.isEmpty then return cd

    val overrideVals = mappings.flatMap { m =>
      if m.index < superArgs.size then
        val arg    = superArgs(m.index)
        val valId  = fresh()
        val valSym = Symbol(
          id       = valId,
          name     = m.valName,
          fullName = s"${cdSym.fullName}#${m.valName}",
          flags    = Flags(isOverride = true, isProtected = true),
          owner    = cd.symbol,
          info     = arg.tpe,
          origin   = origin,
        )
        newSymbols(valSym)
        Some(Tree.ValDef(
          symbol  = valId,
          tpt     = TypeTree(arg.tpe, origin),
          rhs     = Some(arg),
          origin  = origin,
        ))
      else None
    }

    if overrideVals.isEmpty then return cd

    record(Decision(
      kind       = Decision.Kind.RetypedSignature,
      subject    = cd.symbol,
      subjectFqn = cdSym.fullName,
      detail     = Map(
        "parent" -> parentFqn,
        "why"    -> "class-to-trait: super args replaced with override vals",
      ),
      reason     = Reason.Configured(name, parentFqn),
      origin     = origin,
    ))

    val strippedBody = cd.body.map {
      case d: Tree.DefDef if d.symbol == widestCtor.symbol =>
        stripSuperArgs(program, d)
      case other => other
    }

    val (ctorDefs, rest) = strippedBody.partition {
      case d: Tree.DefDef => program.symbolOf(d.symbol).exists(_.name == "<init>")
      case _ => false
    }

    cd.copy(body = ctorDefs ++ overrideVals ++ rest)

  /** Strip args from the super() call in a constructor DefDef.
    *
    * Uses [[CtorFunnel.stmtsOf]] / [[CtorFunnel.headStmt]] to see through `Commented` wrappers —
    * the same path `superArgsOf` uses, so a comment above the super call does not defeat the
    * strip. The body is rebuilt from the statement list with the head's Apply replaced. */
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

object ClassToTraitTransform:
  val Name: String = "class-to-trait"

  final case class ParamMapping(index: Int, valName: String)
