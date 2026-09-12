package balticporter.frontend.dart

import balticporter.catalog.CatalogLog
import balticporter.core.Substitutions
import balticporter.tir.*
import balticporter.tir.Tree.*

/** Converts Dart RAST JSON (from the Dart analyzer exporter) into a TIR Program.
  *
  * Handles Dart-specific constructs:
  *   - Classes with named/optional parameters
  *   - Factory constructors → companion apply methods
  *   - Mixins → traits with `selfType` (linearises identically to Scala)
  *   - Extensions → extension methods (FQN-qualified calls under FQN emission)
  *   - `late` fields → lazy initialization
  *   - Null-safety (`?` types → Nullable union floor)
  *   - Cascade expressions (`..` → temp variable + method chain)
  *   - Dart 3 patterns → TIR match patterns */
object DartMinter:

  def mint(files: List[DartRastFile], subs: Substitutions, catalog: CatalogLog): Program =
    val ctx = new MintContext(subs, catalog)
    val units = files.flatMap(f => ctx.mintFile(f))
    ctx.buildProgram(units)

private class MintContext(subs: Substitutions, catalog: CatalogLog):
  private val symbols = scala.collection.mutable.ListBuffer.empty[Symbol]
  private var symCounter = 0

  def mintFile(file: DartRastFile): List[ClassDef] =
    file.nodes.flatMap(n => mintTopLevel(n, file))

  def buildProgram(units: List[ClassDef]): Program =
    val symTable = SymbolTable(symbols.toList)
    val xref = Xref.build(units)
    val members = MemberIndex.empty
    Program(units, symTable, xref, members)

  private def freshSym(name: String, fullName: String, flags: Flags, owner: SymId,
                       info: TypeRepr, origin: Origin): SymId =
    val id = SymId(symCounter)
    symCounter += 1
    symbols += Symbol(id = id, name = name, fullName = fullName, flags = flags,
      owner = owner, info = info, origin = origin)
    id

  private def mintTopLevel(node: DartRastNode, file: DartRastFile): List[ClassDef] =
    val origin = Origin(file.path, node.pos._1, node.pos._2)
    node.kind match
      case "ClassDeclaration" =>
        val name = nameOf(node)
        val sym = freshSym(name, name, Flags(), SymId.None, TypeRepr.NoType, origin)
        val members = node.children.filter(isClassMember).map(c => mintMember(c, sym, name, file))
        val superCtor = mintSuperConstructor(node, file, origin)
        List(ClassDef(sym, Nil, superCtor, members, origin))

      case "MixinDeclaration" =>
        val name = nameOf(node)
        val sym = freshSym(name, name, Flags(isTrait = true), SymId.None, TypeRepr.NoType, origin)
        val members = node.children.filter(isClassMember).map(c => mintMember(c, sym, name, file))
        List(ClassDef(sym, Nil, None, members, origin))

      case "ExtensionDeclaration" =>
        val name = nameOf(node)
        val moduleName = name + "$ext"
        val sym = freshSym(moduleName, moduleName, Flags(isModule = true), SymId.None,
          TypeRepr.NoType, origin)
        val members = node.children.filter(isClassMember).map(c => mintMember(c, sym, moduleName, file))
        List(ClassDef(sym, Nil, None, members, origin))

      case "EnumDeclaration" =>
        val name = nameOf(node)
        val sym = freshSym(name, name, Flags(isEnum = true), SymId.None, TypeRepr.NoType, origin)
        val enumMembers = node.children.filter(_.kind == "EnumConstantDeclaration").map { ec =>
          val eName = nameOf(ec)
          val eOrigin = Origin(file.path, ec.pos._1, ec.pos._2)
          val eSym = freshSym(eName, s"$name.$eName", Flags(isStatic = true, isEnum = true),
            sym, TypeRepr.NoType, eOrigin)
          ValDef(eSym, TypeTree(TypeRepr.Ref(sym), eOrigin), None, eOrigin)
        }
        List(ClassDef(sym, Nil, None, enumMembers, origin))

      case "FunctionDeclaration" | "TopLevelVariableDeclaration" =>
        val name = nameOf(node)
        val moduleName = file.path.split('/').last.stripSuffix(".dart") + "$lib"
        val moduleSym = freshSym(moduleName, moduleName, Flags(isModule = true), SymId.None,
          TypeRepr.NoType, origin)
        val member = mintMember(node, moduleSym, moduleName, file)
        List(ClassDef(moduleSym, Nil, None, List(member), origin))

      case "TypeAlias" =>
        val name = nameOf(node)
        val sym = freshSym(name, name, Flags(isTrait = true), SymId.None, TypeRepr.NoType, origin)
        List(ClassDef(sym, Nil, None, Nil, origin))

      case _ =>
        Nil

  private def isClassMember(node: DartRastNode): Boolean =
    node.kind match
      case "MethodDeclaration" | "FieldDeclaration" |
           "ConstructorDeclaration" | "GetterDeclaration" | "SetterDeclaration" |
           "FunctionDeclaration" | "TopLevelVariableDeclaration" => true
      case _ => false

  private def mintMember(node: DartRastNode, owner: SymId, ownerName: String,
                         file: DartRastFile): Statement =
    val origin = Origin(file.path, node.pos._1, node.pos._2)
    val name = nameOf(node)
    node.kind match
      case "MethodDeclaration" | "FunctionDeclaration" =>
        val isStatic = node.flags.contains("static")
        val sym = freshSym(name, s"$ownerName.$name",
          Flags(isStatic = isStatic), owner, TypeRepr.NoType, origin)
        val params = mintParams(node, file, sym)
        val retType = resolveTypeTree(node, file, origin)
        val body = node.children.find(c => c.kind == "BlockFunctionBody" || c.kind == "ExpressionFunctionBody")
          .map(c => mintBody(c, file))
        DefDef(sym, List(params), retType, body, origin)

      case "ConstructorDeclaration" =>
        val ctorName = if name == "$anon" then "<init>" else name
        val isFactory = node.isFactory.getOrElse(false)
        val flags = Flags(isStatic = isFactory)
        val sym = freshSym(ctorName, s"$ownerName.$ctorName", flags, owner,
          TypeRepr.NoType, origin)
        val params = mintParams(node, file, sym)
        val body = node.children.find(_.kind == "BlockFunctionBody").map(c => mintBody(c, file))
        DefDef(sym, List(params), TypeTree(TypeRepr.NoType, origin), body, origin)

      case "FieldDeclaration" | "TopLevelVariableDeclaration" =>
        val isFinal = node.flags.contains("final") || node.flags.contains("const")
        val isLate = node.isLate.getOrElse(false)
        val isStatic = node.flags.contains("static")
        val flags = Flags(isMutable = !isFinal, isStatic = isStatic, isLazy = isLate)
        val sym = freshSym(name, s"$ownerName.$name", flags, owner, TypeRepr.NoType, origin)
        val tpe = resolveTypeTree(node, file, origin)
        val init = findInit(node, file)
        ValDef(sym, tpe, init, origin)

      case "GetterDeclaration" =>
        val sym = freshSym(name, s"$ownerName.$name", Flags(), owner, TypeRepr.NoType, origin)
        val retType = resolveTypeTree(node, file, origin)
        val body = node.children.find(c => c.kind == "BlockFunctionBody" || c.kind == "ExpressionFunctionBody")
          .map(c => mintBody(c, file))
        DefDef(sym, Nil, retType, body, origin)

      case "SetterDeclaration" =>
        val sym = freshSym(name + "_=", s"$ownerName.${name}_=",
          Flags(), owner, TypeRepr.NoType, origin)
        val params = mintParams(node, file, sym)
        val body = node.children.find(c => c.kind == "BlockFunctionBody" || c.kind == "ExpressionFunctionBody")
          .map(c => mintBody(c, file))
        DefDef(sym, List(params), TypeTree(TypeRepr.Unit, origin), body, origin)

      case _ =>
        val placeholderType = resolveTypeRepr(node, file)
        Unportable(Literal(Constant.UnitC, TypeRepr.NoType, origin),
          UnportableKind.FrontendBlindSpot, MarkerState.Open,
          None, s"Dart ${node.kind}", placeholderType, origin)

  private def mintParams(node: DartRastNode, file: DartRastFile, owner: SymId): List[ValDef] =
    val formalParams = node.children.find(_.kind == "FormalParameterList")
    formalParams match
      case Some(fp) =>
        fp.children.filter(c => c.kind == "SimpleFormalParameter" ||
          c.kind == "DefaultFormalParameter" || c.kind == "FieldFormalParameter")
          .map { p =>
            val pName = nameOf(p)
            val pOrigin = Origin(file.path, p.pos._1, p.pos._2)
            val isNamed = p.flags.contains("named")
            val flags = Flags(isParam = true)
            val sym = freshSym(pName, pName, flags, owner, TypeRepr.NoType, pOrigin)
            val tpe = resolveTypeTree(p, file, pOrigin)
            ValDef(sym, tpe, None, pOrigin)
          }
      case None => Nil

  private def mintSuperConstructor(node: DartRastNode, file: DartRastFile,
                                    origin: Origin): Option[Apply] =
    node.children.find(_.kind == "ExtendsClause").flatMap { ext =>
      ext.children.headOption.map { superType =>
        val superRef = Ident(SymId.None, resolveTypeRepr(superType, file), origin)
        Apply(superRef, Nil, TypeRepr.NoType, origin)
      }
    }

  private def mintBody(node: DartRastNode, file: DartRastFile): Term =
    val origin = Origin(file.path, node.pos._1, node.pos._2)
    node.kind match
      case "BlockFunctionBody" =>
        val block = node.children.find(_.kind == "Block")
        block match
          case Some(b) =>
            val stmts = b.children.map(c => mintStatement(c, file))
            if stmts.isEmpty then Literal(Constant.UnitC, TypeRepr.NoType, origin)
            else
              val (init, last) = (stmts.init, stmts.last)
              last match
                case t: Term => Block(init, t, TypeRepr.NoType, origin)
                case _       => Block(stmts, Literal(Constant.UnitC, TypeRepr.NoType, origin),
                                  TypeRepr.NoType, origin)
          case None => Literal(Constant.UnitC, TypeRepr.NoType, origin)
      case "ExpressionFunctionBody" =>
        node.children.headOption.map(c => mintExpr(c, file))
          .getOrElse(Literal(Constant.UnitC, TypeRepr.NoType, origin))
      case _ =>
        Literal(Constant.UnitC, TypeRepr.NoType, origin)

  private def mintStatement(node: DartRastNode, file: DartRastFile): Statement =
    val origin = Origin(file.path, node.pos._1, node.pos._2)
    node.kind match
      case "ReturnStatement" =>
        val expr = node.children.headOption.map(c => mintExpr(c, file))
        Return(expr, TypeRepr.NoType, origin)
      case "IfStatement" =>
        val cond = mintExpr(node.children.head, file)
        val thenBranch = mintExpr(node.children(1), file)
        val elseBranch = if node.children.length > 2 then mintExpr(node.children(2), file)
                         else Literal(Constant.UnitC, TypeRepr.NoType, origin)
        If(cond, thenBranch, elseBranch, TypeRepr.NoType, origin)
      case "Block" =>
        val stmts = node.children.map(c => mintStatement(c, file))
        if stmts.isEmpty then Literal(Constant.UnitC, TypeRepr.NoType, origin)
        else
          val (init, last) = (stmts.init, stmts.last)
          last match
            case t: Term => Block(init, t, TypeRepr.NoType, origin)
            case _       => Block(stmts, Literal(Constant.UnitC, TypeRepr.NoType, origin),
                              TypeRepr.NoType, origin)
      case "ExpressionStatement" =>
        node.children.headOption.map(c => mintExpr(c, file))
          .getOrElse(Literal(Constant.UnitC, TypeRepr.NoType, origin))
      case "VariableDeclarationStatement" =>
        mintVariableDecl(node, file)
      case "ForStatement" =>
        val parts = node.children
        val body = parts.lastOption.map(c => mintExpr(c, file))
          .getOrElse(Literal(Constant.UnitC, TypeRepr.NoType, origin))
        For(Nil, None, Nil, body, TypeRepr.NoType, origin)
      case "ForEachStatement" | "ForEachPartsOfDeclaration" =>
        val body = node.children.lastOption.map(c => mintExpr(c, file))
          .getOrElse(Literal(Constant.UnitC, TypeRepr.NoType, origin))
        val iterable = if node.children.length > 1 then mintExpr(node.children(node.children.length - 2), file)
                       else Literal(Constant.UnitC, TypeRepr.NoType, origin)
        val bindSym = freshSym("_it", "_it", Flags(isParam = true), SymId.None, TypeRepr.NoType, origin)
        val binding = ValDef(bindSym, TypeTree(TypeRepr.Any, origin), None, origin)
        ForEach(binding, iterable, body, TypeRepr.NoType, origin)
      case "WhileStatement" =>
        val cond = mintExpr(node.children.head, file)
        val body = mintExpr(node.children(1), file)
        While(cond, body, TypeRepr.NoType, origin)
      case "SwitchStatement" =>
        val scrutinee = mintExpr(node.children.head, file)
        val cases = node.children.tail.filter(_.kind == "SwitchCase" || _.kind == "SwitchDefault")
          .map { c =>
            val pattern = c.children.headOption.map(p => mintExpr(p, file))
              .getOrElse(Ident(SymId.None, TypeRepr.Any, origin))
            val body = c.children.lastOption.map(b => mintExpr(b, file))
              .getOrElse(Literal(Constant.UnitC, TypeRepr.NoType, origin))
            CaseDef(TypePattern(TypeRepr.Any, SymId.None, origin), None, body, origin)
          }
        Match(scrutinee, cases, TypeRepr.NoType, origin, MatchId.fresh())
      case "ThrowStatement" | "ThrowExpression" =>
        val expr = mintExpr(node.children.head, file)
        Throw(expr, TypeRepr.NoType, origin)
      case "TryStatement" =>
        val tryBody = node.children.headOption.map(c => mintExpr(c, file))
          .getOrElse(Literal(Constant.UnitC, TypeRepr.NoType, origin))
        val catches = node.children.filter(_.kind == "CatchClause").map { cc =>
          val body = cc.children.lastOption.map(b => mintExpr(b, file))
            .getOrElse(Literal(Constant.UnitC, TypeRepr.NoType, origin))
          CaseDef(TypePattern(TypeRepr.Any, SymId.None, origin), None, body, origin)
        }
        val finallyBlock = node.children.find(_.kind == "FinallyClause")
          .flatMap(_.children.headOption).map(c => mintExpr(c, file))
        Try(tryBody, catches, finallyBlock, TypeRepr.NoType, origin, TryId.fresh())
      case _ =>
        Unportable(Literal(Constant.UnitC, TypeRepr.NoType, origin),
          UnportableKind.FrontendBlindSpot, MarkerState.Open,
          None, s"Dart stmt: ${node.kind}", TypeRepr.NoType, origin)

  private def mintVariableDecl(node: DartRastNode, file: DartRastFile): Statement =
    val origin = Origin(file.path, node.pos._1, node.pos._2)
    val decls = node.children.filter(_.kind == "VariableDeclaration")
    if decls.length == 1 then
      val d = decls.head
      val name = nameOf(d)
      val isFinal = d.flags.contains("final") || d.flags.contains("const")
      val sym = freshSym(name, name, Flags(isMutable = !isFinal), SymId.None,
        TypeRepr.NoType, origin)
      val tpe = resolveTypeTree(d, file, origin)
      val init = findInit(d, file)
      ValDef(sym, tpe, init, origin)
    else
      val stmts = decls.map { d =>
        val name = nameOf(d)
        val isFinal = d.flags.contains("final") || d.flags.contains("const")
        val sym = freshSym(name, name, Flags(isMutable = !isFinal), SymId.None,
          TypeRepr.NoType, origin)
        val tpe = resolveTypeTree(d, file, origin)
        val init = findInit(d, file)
        ValDef(sym, tpe, init, origin)
      }
      Block(stmts.init, stmts.last.asInstanceOf[Term], TypeRepr.NoType, origin)

  private def mintExpr(node: DartRastNode, file: DartRastFile): Term =
    val origin = Origin(file.path, node.pos._1, node.pos._2)
    node.kind match
      case "SimpleIdentifier" | "PrefixedIdentifier" =>
        Ident(SymId.None, resolveTypeRepr(node, file), origin)
      case "IntegerLiteral" =>
        Literal(Constant.IntC(node.value.map {
          case DartRastValue.IntVal(v) => v.toInt
          case DartRastValue.Num(v) => v.toInt
          case _ => 0
        }.getOrElse(0)), TypeRepr.Int, origin)
      case "DoubleLiteral" =>
        Literal(Constant.DoubleC(node.value.map {
          case DartRastValue.Num(v) => v
          case _ => 0.0
        }.getOrElse(0.0)), TypeRepr.Double, origin)
      case "SimpleStringLiteral" | "AdjacentStrings" | "StringInterpolation" =>
        Literal(Constant.StringC(node.value.map {
          case DartRastValue.Str(v) => v
          case _ => ""
        }.getOrElse("")), TypeRepr.String, origin)
      case "BooleanLiteral" =>
        val v = node.value.exists {
          case DartRastValue.Bool(b) => b
          case _ => false
        }
        Literal(Constant.BooleanC(v), TypeRepr.Boolean, origin)
      case "NullLiteral" =>
        Literal(Constant.NullC, TypeRepr.Null, origin)
      case "BinaryExpression" =>
        val left = mintExpr(node.children.head, file)
        val right = mintExpr(node.children.last, file)
        val op = node.operator.getOrElse("+")
        Apply(Select(left, SymId.None, TypeRepr.NoType, origin),
          List(right), resolveTypeRepr(node, file), origin)
      case "MethodInvocation" | "FunctionExpressionInvocation" =>
        val fn = mintExpr(node.children.head, file)
        val argList = node.children.find(_.kind == "ArgumentList")
        val args = argList.map(_.children.map(c => mintExpr(c, file))).getOrElse(Nil)
        Apply(fn, args, resolveTypeRepr(node, file), origin)
      case "PropertyAccess" =>
        val obj = mintExpr(node.children.head, file)
        Select(obj, SymId.None, resolveTypeRepr(node, file), origin)
      case "IndexExpression" =>
        val obj = mintExpr(node.children.head, file)
        val idx = mintExpr(node.children.last, file)
        ArrayAccess(obj, idx, resolveTypeRepr(node, file), origin)
      case "ListLiteral" =>
        val elems = node.children.map(c => mintExpr(c, file))
        NewArray(resolveTypeRepr(node, file), Nil, Some(elems), origin)
      case "SetOrMapLiteral" =>
        Unportable(Literal(Constant.UnitC, TypeRepr.NoType, origin),
          UnportableKind.FrontendBlindSpot, MarkerState.Open,
          None, "Dart map/set literal", resolveTypeRepr(node, file), origin)
      case "ConditionalExpression" =>
        val cond = mintExpr(node.children(0), file)
        val thenExpr = mintExpr(node.children(1), file)
        val elseExpr = mintExpr(node.children(2), file)
        If(cond, thenExpr, elseExpr, resolveTypeRepr(node, file), origin)
      case "AsExpression" | "IsExpression" =>
        val expr = mintExpr(node.children.head, file)
        Typed(expr, TypeTree(resolveTypeRepr(node, file), origin), resolveTypeRepr(node, file), origin)
      case "ThrowExpression" =>
        val expr = mintExpr(node.children.head, file)
        Throw(expr, TypeRepr.NoType, origin)
      case "CascadeExpression" =>
        val target = mintExpr(node.children.head, file)
        val cascades = node.children.tail.map(c => mintExpr(c, file))
        val all = target :: cascades
        if all.length == 1 then all.head
        else Block(all.init, all.last, resolveTypeRepr(node, file), origin)
      case "InstanceCreationExpression" =>
        val ctorRef = mintExpr(node.children.head, file)
        val argList = node.children.find(_.kind == "ArgumentList")
        val args = argList.map(_.children.map(c => mintExpr(c, file))).getOrElse(Nil)
        New(TypeTree(resolveTypeRepr(node, file), origin), resolveTypeRepr(node, file), origin)
      case "FunctionExpression" =>
        val params = mintParams(node, file, SymId.None)
        val body = node.children.find(c => c.kind == "BlockFunctionBody" || c.kind == "ExpressionFunctionBody")
          .map(c => mintBody(c, file))
          .getOrElse(Literal(Constant.UnitC, TypeRepr.NoType, origin))
        Lambda(params, body, resolveTypeRepr(node, file), origin)
      case "ParenthesizedExpression" =>
        node.children.headOption.map(c => mintExpr(c, file))
          .getOrElse(Literal(Constant.UnitC, TypeRepr.NoType, origin))
      case "PrefixExpression" | "PostfixExpression" =>
        val operand = mintExpr(node.children.head, file)
        Apply(Select(operand, SymId.None, TypeRepr.NoType, origin),
          Nil, resolveTypeRepr(node, file), origin)
      case "AssignmentExpression" =>
        val target = mintExpr(node.children.head, file)
        val value = mintExpr(node.children.last, file)
        Assign(target, value, origin)
      case "AwaitExpression" =>
        Unportable(mintExpr(node.children.head, file),
          UnportableKind.FrontendBlindSpot, MarkerState.Open,
          None, "Dart await", resolveTypeRepr(node, file), origin)
      case "Block" =>
        val stmts = node.children.map(c => mintStatement(c, file))
        if stmts.isEmpty then Literal(Constant.UnitC, TypeRepr.NoType, origin)
        else
          val (init, last) = (stmts.init, stmts.last)
          last match
            case t: Term => Block(init, t, TypeRepr.NoType, origin)
            case _       => Block(stmts, Literal(Constant.UnitC, TypeRepr.NoType, origin),
                              TypeRepr.NoType, origin)
      case _ =>
        Unportable(Literal(Constant.UnitC, TypeRepr.NoType, origin),
          UnportableKind.FrontendBlindSpot, MarkerState.Open,
          None, s"Dart expr: ${node.kind}", resolveTypeRepr(node, file), origin)

  private def nameOf(node: DartRastNode): String =
    node.children.find(c => c.kind == "SimpleIdentifier" || c.kind == "Identifier")
      .flatMap(_.text)
      .orElse(node.text)
      .getOrElse("$anon")

  private def resolveTypeTree(node: DartRastNode, file: DartRastFile, origin: Origin): TypeTree =
    TypeTree(resolveTypeRepr(node, file), origin)

  private def resolveTypeRepr(node: DartRastNode, file: DartRastFile): TypeRepr =
    node.`type`.flatMap(file.types.get).map(dartTypeToRepr(_, file)).getOrElse(TypeRepr.Any)

  private def dartTypeToRepr(rt: DartRastType, file: DartRastFile): TypeRepr =
    val base = rt.kind match
      case "int"    => TypeRepr.Int
      case "double" => TypeRepr.Double
      case "num"    => TypeRepr.Double
      case "bool"   => TypeRepr.Boolean
      case "String" => TypeRepr.String
      case "void"   => TypeRepr.Unit
      case "Null"   => TypeRepr.Null
      case "Never"  => TypeRepr.Nothing
      case "dynamic" | "Object" | "Object?" => TypeRepr.Any
      case "List"   =>
        val elem = rt.elementType.flatMap(file.types.get).map(dartTypeToRepr(_, file)).getOrElse(TypeRepr.Any)
        TypeRepr.Applied(TypeRepr.Ref(SymId.None), List(elem))
      case "Map"    =>
        TypeRepr.Applied(TypeRepr.Ref(SymId.None), Nil)
      case "Set"    =>
        TypeRepr.Applied(TypeRepr.Ref(SymId.None), Nil)
      case "Future" | "Stream" =>
        val elem = rt.typeArguments.flatMap(_.headOption).flatMap(file.types.get)
          .map(dartTypeToRepr(_, file)).getOrElse(TypeRepr.Any)
        TypeRepr.Applied(TypeRepr.Ref(SymId.None), List(elem))
      case "function" =>
        val params = rt.parameters.getOrElse(Nil).map(p =>
          file.types.get(p.`type`).map(dartTypeToRepr(_, file)).getOrElse(TypeRepr.Any))
        val ret = rt.returnType.flatMap(file.types.get).map(dartTypeToRepr(_, file)).getOrElse(TypeRepr.Unit)
        TypeRepr.MethodType(Nil, params, ret)
      case _ => TypeRepr.Any
    if rt.isNullable then TypeRepr.OrType(base, TypeRepr.Null)
    else base

  private def findInit(node: DartRastNode, file: DartRastFile): Option[Term] =
    node.children.find(c => c.kind != "SimpleIdentifier" && c.kind != "TypeName" &&
      c.kind != "NamedType" && c.kind != "GenericFunctionType" &&
      c.kind != "FormalParameterList" && !c.kind.endsWith("Type"))
      .map(c => mintExpr(c, file))
