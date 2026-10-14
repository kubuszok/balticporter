package balticporter.transform

import balticporter.core.{ MergeablePolicy, SurfacePolicy }
import balticporter.tir.*
import balticporter.tir.Tree.*

/** How one JS library builds its classes at runtime: a call `function(name, "field field", ctor, methods, base)` declares class `classPrefix + name` extending `base`, or `rootClass` when absent. The
  * library's own spelling, so the consumer passes it; the empty value recognises nothing.
  */
final case class RuntimeClassBuilder(function: String, classPrefix: String, rootClass: String):
  def isEmpty: Boolean = function.isEmpty

  /** the class a builder call named `name` declares. */
  def className(name: String): String = classPrefix + name

object RuntimeClassBuilder:
  val none: RuntimeClassBuilder = RuntimeClassBuilder("", "", "")

/** Normalises a JS library's runtime class-building calls (a prototype hierarchy a static frontend cannot see) into explicit `ClassDef`s: class name from the first string argument, fields from the
  * space-split second, parent from the fifth. Which function builds classes and how its classes are spelled is the consumer's [[RuntimeClassBuilder]]; the empty builder or the empty scope is a no-op,
  * and the scope defaults to `Only(Set.empty)` because the phase mints declarations.
  */
final class DefnodeNormalizationRule(
  val scope:   RuleScope,
  val builder: RuntimeClassBuilder = RuntimeClassBuilder.none
) extends Phase,
      SurfacePolicy,
      MergeablePolicy:
  def name = "defnode-normalization"

  def surfaceFingerprint: String =
    val entries     = scope.entries
    val scopePart   = if entries.isEmpty then Nil else List(s"scope=${entries.toList.sorted.mkString(",")}")
    val builderPart =
      if builder.isEmpty then Nil else List(s"builder=${builder.function},${builder.classPrefix},${builder.rootClass}")
    (scopePart ++ builderPart).mkString(";")

  def subjects: Set[String] = scope.entries

  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case d: DefnodeNormalizationRule =>
      val merged =
        if builder.isEmpty then Right(d.builder)
        else if d.builder.isEmpty || d.builder == builder then Right(builder)
        else Left(s"`$name` two different class builders cannot merge")
      (scope, d.scope, merged) match
        case (RuleScope.Only(a), RuleScope.Only(b), Right(bld)) =>
          Right(MergeablePolicy.Merged(DefnodeNormalizationRule(RuleScope.Only(a ++ b), bld), a ++ b))
        case (_, _, Left(why)) => Left(why)
        case _                 => Left(s"`$name` non-Only scopes cannot merge")
    case other => Left(s"`$name` cannot merge with ${other.getClass.getSimpleName}")

  override def run(program: Program): Program =
    if scope == RuleScope.Only(Set.empty) || builder.isEmpty then return program
    given Program = program

    var result     = program
    val recognized = scala.collection.mutable.ListBuffer.empty[(String, DefnodeCall)]

    for unit <- program.units do
      for stmt <- allStatements(unit) do
        recognizeDefnode(stmt, program) match
          case Some(call) if scope.includes(call.className) =>
            recognized += (call.className -> call)
          case _ => ()

    if recognized.isEmpty then return program

    val newUnits = scala.collection.mutable.ListBuffer.empty[ClassDef]
    var syms     = program.symbols
    var nextId   = syms.all.map(_.id.raw).maxOption.getOrElse(0) + 1

    def freshId(): SymId =
      val id = SymId(nextId)
      nextId += 1
      id

    for (jsName, call) <- recognized do
      val className = builder.className(jsName)
      val origin    = call.origin
      val classId   = freshId()
      val classSym  = Symbol(
        id = classId,
        name = className,
        fullName = className,
        flags = Flags(),
        owner = SymId.None,
        info = TypeRepr.NoType,
        origin = origin
      )
      syms = syms.updated(classSym)

      val fields = call.props.map { propName =>
        val fieldId  = freshId()
        val fieldSym = Symbol(
          id = fieldId,
          name = propName,
          fullName = s"$className.$propName",
          flags = Flags(isMutable = true),
          owner = classId,
          info = TypeRepr.NoType,
          origin = origin
        )
        syms = syms.updated(fieldSym)
        ValDef(fieldId, TypeTree(TypeRepr.NoType, origin), None, origin)
      }

      val methods = call.methods.map { methodName =>
        val methodId  = freshId()
        val methodSym = Symbol(
          id = methodId,
          name = methodName,
          fullName = s"$className.$methodName",
          flags = Flags(),
          owner = classId,
          info = TypeRepr.NoType,
          origin = origin
        )
        syms = syms.updated(methodSym)
        DefDef(methodId, Nil, TypeTree(TypeRepr.NoType, origin), None, origin)
      }

      val parent = call.base.map { baseName =>
        TypeTree(TypeRepr.TypeRef(TypeRepr.NoPrefix, SymId.None), origin)
      }

      newUnits += ClassDef(classId, parent.toList, None, fields ++ methods, origin)

      record(
        Decision(
          kind = Decision.Kind.InjectedMember,
          subject = classId,
          subjectFqn = className,
          detail = Map(
            "props" -> call.props.size.toString,
            "methods" -> call.methods.size.toString,
            "base" -> call.base.getOrElse(builder.rootClass)
          ),
          reason = Reason.Configured("defnode-normalization", s"scope=$jsName"),
          origin = origin
        )
      )

    program.rebuilt(units = program.units ++ newUnits.toList, symbols = syms)

  private case class DefnodeCall(className: String, props: List[String], base: Option[String], methods: List[String], origin: Origin)

  private def recognizeDefnode(stmt: Statement, program: Program): Option[DefnodeCall] =
    stmt match
      case vd: ValDef =>
        vd.rhs match
          case Some(apply: Apply) =>
            apply.fun match
              case ident: Ident if program.symbols.get(ident.sym).exists(_.name == builder.function) =>
                extractDefnodeArgs(apply.args, vd.origin, program)
              case _ => None
          case _ => None
      case _ => None

  private def extractDefnodeArgs(args: List[Term], origin: Origin, program: Program): Option[DefnodeCall] =
    if args.size < 3 then return None
    val className = args.head match
      case Literal(Constant.StringC(s), _, _) => s
      case _                                  => return None
    val props = args(1) match
      case Literal(Constant.StringC(s), _, _) => s.split("\\s+").toList.filter(_.nonEmpty)
      case Literal(Constant.NullC, _, _)      => Nil
      case _                                  => Nil
    val base = if args.size >= 5 then
      args(4) match
        case i: Ident => program.symbols.get(i.sym).map(_.name)
        case _ => None
    else None
    val methods = if args.size >= 4 then
      args(3) match
        case _ => Nil
      // methods extraction from object literal would go here
    else Nil
    Some(DefnodeCall(className, props, base, methods, origin))

  private def allStatements(cd: ClassDef): List[Statement] =
    cd.body.flatMap {
      case d: DefDef => d.rhs.toList.flatMap(collectStatements)
      case other => List(other)
    }

  private def collectStatements(term: Term): List[Statement] = term match
    case Block(stmts, expr, _, _, _) => stmts ++ collectStatements(expr)
    case _                           => List(term)

object DefnodeNormalizationRule:

  /** The phase from a port's configuration: `scope` as every minting phase reads it, and `class-builder { function, class-prefix, root-class }`; an absent builder is the no-op. */
  def fromConfig(config: ConfigView): DefnodeNormalizationRule =
    val scope   = TransformFactory.scopeOf(config, default = RuleScope.Only(Set.empty))
    val builder = config.child("class-builder") match
      case Some(b) =>
        RuntimeClassBuilder(b.requireString("function"), b.string("class-prefix").getOrElse(""), b.string("root-class").getOrElse(""))
      case None => RuntimeClassBuilder.none
    DefnodeNormalizationRule(scope, builder)
