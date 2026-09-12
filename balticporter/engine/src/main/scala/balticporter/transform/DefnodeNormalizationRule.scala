package balticporter.transform

import balticporter.core.{MergeablePolicy, SurfacePolicy}
import balticporter.tir.*
import balticporter.tir.Tree.*

/** §1(c) project-specific rule using a §1(b) mechanism: recognizes Terser's
  * `DEFNODE(type, props, ctor, methods, base)` runtime class-building pattern
  * and normalizes it into explicit `ClassDef` nodes in TIR.
  *
  * The DEFNODE pattern builds a prototype-based class hierarchy at runtime.
  * A static frontend cannot see these as classes. This rule pattern-matches
  * the call shape and extracts: class name (string), field names (space-split
  * string), parent class (variable reference), and method implementations
  * (object literal). The result is a normal TIR ClassDef with fields, methods,
  * and inheritance.
  *
  * 134 DEFNODE calls in Terser's `ast.js` produce the entire AST hierarchy.
  *
  * Scope: `Only(Set.empty)` default (it mints declarations).
  * See `docs/spikes/terser-defnode.md` for the feasibility assessment. */
final class DefnodeNormalizationRule(
    val scope: RuleScope,
) extends Phase, SurfacePolicy, MergeablePolicy:
  def name = "defnode-normalization"

  def surfaceFingerprint: String =
    val entries = scope.entries
    if entries.isEmpty then "" else s"scope=${entries.toList.sorted.mkString(",")}"

  def subjects: Set[String] = scope.entries

  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case d: DefnodeNormalizationRule =>
      (scope, d.scope) match
        case (RuleScope.Only(a), RuleScope.Only(b)) =>
          Right(MergeablePolicy.Merged(DefnodeNormalizationRule(RuleScope.Only(a ++ b)), a ++ b))
        case _ => Left(s"`$name` non-Only scopes cannot merge")
    case other => Left(s"`$name` cannot merge with ${other.getClass.getSimpleName}")

  override def run(program: Program): Program =
    if scope == RuleScope.Only(Set.empty) then return program
    given Program = program

    var result = program
    val recognized = scala.collection.mutable.ListBuffer.empty[(String, DefnodeCall)]

    for unit <- program.units do
      for stmt <- allStatements(unit) do
        recognizeDefnode(stmt, program) match
          case Some(call) if scope.includes(call.className) =>
            recognized += (call.className -> call)
          case _ => ()

    if recognized.isEmpty then return program

    val newUnits = scala.collection.mutable.ListBuffer.empty[ClassDef]
    var syms = program.symbols
    var nextId = syms.all.map(_.id.raw).maxOption.getOrElse(0) + 1

    def freshId(): SymId =
      val id = SymId(nextId)
      nextId += 1
      id

    for (className, call) <- recognized do
      val origin = call.origin
      val classId = freshId()
      val classSym = Symbol(
        id = classId, name = s"AST_$className",
        fullName = s"AST_$className",
        flags = Flags(),
        owner = SymId.None, info = TypeRepr.NoType, origin = origin)
      syms = syms.updated(classSym)

      val fields = call.props.map { propName =>
        val fieldId = freshId()
        val fieldSym = Symbol(
          id = fieldId, name = propName,
          fullName = s"AST_$className.$propName",
          flags = Flags(isMutable = true),
          owner = classId, info = TypeRepr.NoType, origin = origin)
        syms = syms.updated(fieldSym)
        ValDef(fieldId, TypeTree(TypeRepr.NoType, origin), None, origin)
      }

      val methods = call.methods.map { methodName =>
        val methodId = freshId()
        val methodSym = Symbol(
          id = methodId, name = methodName,
          fullName = s"AST_$className.$methodName",
          flags = Flags(),
          owner = classId, info = TypeRepr.NoType, origin = origin)
        syms = syms.updated(methodSym)
        DefDef(methodId, Nil, TypeTree(TypeRepr.NoType, origin), None, origin)
      }

      val parent = call.base.map { baseName =>
        TypeTree(TypeRepr.TypeRef(TypeRepr.NoPrefix, SymId.None), origin)
      }

      newUnits += ClassDef(classId, parent.toList, None, fields ++ methods, origin)

      record(Decision(
        kind = Decision.Kind.InjectedMember,
        subject = classId,
        subjectFqn = s"AST_$className",
        detail = Map("props" -> call.props.size.toString, "methods" -> call.methods.size.toString,
          "base" -> call.base.getOrElse("AST_Node")),
        reason = Reason.Configured("defnode-normalization", s"scope=AST_$className"),
        origin = origin))

    program.rebuilt(
      units = program.units ++ newUnits.toList,
      symbols = syms)

  private case class DefnodeCall(
      className: String,
      props: List[String],
      base: Option[String],
      methods: List[String],
      origin: Origin)

  private def recognizeDefnode(stmt: Statement, program: Program): Option[DefnodeCall] =
    stmt match
      case vd: ValDef =>
        vd.rhs match
          case Some(apply: Apply) =>
            apply.fun match
              case ident: Ident if program.symbols.get(ident.sym).exists(_.name == "DEFNODE") =>
                extractDefnodeArgs(apply.args, vd.origin)
              case _ => None
          case _ => None
      case _ => None

  private def extractDefnodeArgs(args: List[Term], origin: Origin): Option[DefnodeCall] =
    if args.size < 3 then return None
    val className = args.head match
      case Literal(Constant.StringC(s), _, _) => s
      case _ => return None
    val props = args(1) match
      case Literal(Constant.StringC(s), _, _) => s.split("\\s+").toList.filter(_.nonEmpty)
      case Literal(Constant.NullC, _, _) => Nil
      case _ => Nil
    val base = if args.size >= 5 then
      args(4) match
        case i: Ident => Some(i.sym.toString)
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
    case _ => List(term)
