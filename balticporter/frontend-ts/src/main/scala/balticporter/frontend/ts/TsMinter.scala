package balticporter.frontend.ts

import balticporter.catalog.CatalogLog
import balticporter.core.Substitutions
import balticporter.tir.*
import balticporter.tir.Tree.*

/** Converts RAST v1 JSON (TypeScript resolved AST) into a TIR Program.
  *
  * The minter is the Scala-side lowering: it reads what the TS compiler resolved and decides how each construct maps to TIR. Every lowering rule lives here, not in the Node.js exporter.
  */
object TsMinter:

  def mint(files: List[RastFile], subs: Substitutions, catalog: CatalogLog): Program =
    val ctx   = new MintContext(subs, catalog)
    val units = files.flatMap(f => ctx.mintFile(f))
    ctx.buildProgram(units)

private class MintContext(subs: Substitutions, catalog: CatalogLog):
  private val symbols    = scala.collection.mutable.ListBuffer.empty[Symbol]
  private var symCounter = 0

  def mintFile(file: RastFile): List[ClassDef] =
    file.nodes.flatMap(n => mintTopLevel(n, file))

  def buildProgram(units: List[ClassDef]): Program =
    val symTable = SymbolTable(symbols.toList)
    val xref     = Xref.build(units)
    val members  = MemberIndex.empty
    Program(units, symTable, xref, members)

  private def freshSym(name: String, fullName: String, flags: Flags, owner: SymId, info: TypeRepr, origin: Origin): SymId =
    val id = SymId(symCounter)
    symCounter += 1
    symbols += Symbol(id = id, name = name, fullName = fullName, flags = flags, owner = owner, info = info, origin = origin)
    id

  private def mintTopLevel(node: RastNode, file: RastFile): List[ClassDef] =
    val origin = Origin(file.path, node.pos._1, node.pos._2)
    node.kind match
      case "InterfaceDeclaration" =>
        val name    = nameOf(node)
        val sym     = freshSym(name, name, Flags(isTrait = true), SymId.None, TypeRepr.NoType, origin)
        val members = node.children.filter(isClassMember).map(c => mintMember(c, sym, name, file))
        List(ClassDef(sym, Nil, None, members, origin))

      case "ClassDeclaration" =>
        val name    = nameOf(node)
        val sym     = freshSym(name, name, Flags(), SymId.None, TypeRepr.NoType, origin)
        val members = node.children.filter(isClassMember).map(c => mintMember(c, sym, name, file))
        List(ClassDef(sym, Nil, None, members, origin))

      case "FunctionDeclaration" =>
        val name       = nameOf(node)
        val moduleName = name + "$module"
        val moduleSym  = freshSym(moduleName, moduleName, Flags(isModule = true), SymId.None, TypeRepr.NoType, origin)
        val methodSym  = freshSym(name, s"$moduleName.$name", Flags(isStatic = true), moduleSym, TypeRepr.NoType, origin)
        val body       = mintBody(node, file)
        val params     = mintParams(node, file, methodSym)
        val retType    = resolveTypeTree(node, file, origin)
        val defDef     = DefDef(methodSym, List(params), retType, Some(body), origin)
        List(ClassDef(moduleSym, Nil, None, List(defDef), origin))

      case "FirstStatement" | "VariableStatement" =>
        mintVariableStatement(node, file)

      case _ =>
        Nil

  private def mintVariableStatement(node: RastNode, file: RastFile): List[ClassDef] =
    val decls = node.children.flatMap { child =>
      if child.kind == "VariableDeclarationList" then child.children.filter(_.kind == "VariableDeclaration")
      else Nil
    }
    if decls.isEmpty then return Nil

    val origin     = Origin(file.path, node.pos._1, node.pos._2)
    val moduleName = file.path.split('/').last.stripSuffix(".ts") + "$module"
    val moduleSym  = freshSym(moduleName, moduleName, Flags(isModule = true), SymId.None, TypeRepr.NoType, origin)

    val members = decls.map { d =>
      val name      = nameOf(d)
      val isConst   = d.flags.contains("const")
      val valOrigin = Origin(file.path, d.pos._1, d.pos._2)
      val flags     = if isConst then Flags() else Flags(isMutable = true)
      val sym       = freshSym(name, s"$moduleName.$name", flags, moduleSym, TypeRepr.NoType, valOrigin)
      val tpe       = resolveTypeTree(d, file, valOrigin)
      val init      = findInit(d, file)
      ValDef(sym, tpe, init, valOrigin)
    }
    List(ClassDef(moduleSym, Nil, None, members, origin))

  private def isClassMember(node: RastNode): Boolean =
    node.kind match
      case "MethodDeclaration" | "MethodSignature" | "PropertyDeclaration" | "PropertySignature" | "Constructor" | "GetAccessor" | "SetAccessor" => true
      case _                                                                                                                                     => false

  private def mintMember(node: RastNode, owner: SymId, ownerName: String, file: RastFile): Statement =
    val origin = Origin(file.path, node.pos._1, node.pos._2)
    val name   = nameOf(node)
    node.kind match
      case "MethodDeclaration" | "MethodSignature" =>
        val sym     = freshSym(name, s"$ownerName.$name", Flags(), owner, TypeRepr.NoType, origin)
        val params  = mintParams(node, file, sym)
        val retType = resolveTypeTree(node, file, origin)
        val body    = if node.kind == "MethodDeclaration" then Some(mintBody(node, file)) else None
        DefDef(sym, List(params), retType, body, origin)
      case "PropertyDeclaration" | "PropertySignature" =>
        val isReadonly = node.flags.contains("ReadonlyKeyword")
        val flags      = if isReadonly then Flags() else Flags(isMutable = true)
        val sym        = freshSym(name, s"$ownerName.$name", flags, owner, TypeRepr.NoType, origin)
        val tpe        = resolveTypeTree(node, file, origin)
        val init       = findInit(node, file)
        ValDef(sym, tpe, init, origin)
      case _ =>
        val placeholderType = resolveTypeRepr(node, file)
        Unportable(
          Literal(Constant.UnitC, TypeRepr.NoType, origin),
          UnportableKind.FrontendBlindSpot,
          MarkerState.Open,
          None,
          s"TS ${node.kind}",
          placeholderType,
          origin
        )

  private def mintParams(node: RastNode, file: RastFile, owner: SymId): List[ValDef] =
    node.children.filter(_.kind == "Parameter").map { p =>
      val name   = nameOf(p)
      val origin = Origin(file.path, p.pos._1, p.pos._2)
      val sym    = freshSym(name, name, Flags(isParam = true), owner, TypeRepr.NoType, origin)
      val tpe    = resolveTypeTree(p, file, origin)
      ValDef(sym, tpe, None, origin)
    }

  private def mintBody(node: RastNode, file: RastFile): Term =
    val block = node.children.find(_.kind == "Block")
    block match
      case Some(b) =>
        val origin = Origin(file.path, b.pos._1, b.pos._2)
        val stmts  = b.children.map(c => mintStatement(c, file))
        if stmts.isEmpty then Literal(Constant.UnitC, TypeRepr.NoType, origin)
        else
          val (init, last) = (stmts.init, stmts.last)
          last match
            case t: Term => Block(init, t, TypeRepr.NoType, origin)
            case _ => Block(stmts, Literal(Constant.UnitC, TypeRepr.NoType, origin), TypeRepr.NoType, origin)
      case None =>
        val origin = Origin(file.path, node.pos._1, node.pos._2)
        Literal(Constant.UnitC, TypeRepr.NoType, origin)

  private def mintStatement(node: RastNode, file: RastFile): Statement =
    val origin = Origin(file.path, node.pos._1, node.pos._2)
    node.kind match
      case "ReturnStatement" =>
        val expr = node.children.headOption.map(c => mintExpr(c, file))
        Return(expr, TypeRepr.NoType, origin)
      case "IfStatement" =>
        val cond       = mintExpr(node.children.head, file)
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
            case _ => Block(stmts, Literal(Constant.UnitC, TypeRepr.NoType, origin), TypeRepr.NoType, origin)
      case "ExpressionStatement" =>
        node.children.headOption.map(c => mintExpr(c, file)).getOrElse(Literal(Constant.UnitC, TypeRepr.NoType, origin))
      case "FirstStatement" | "VariableStatement" =>
        mintLocalVarDecl(node, file)
      case "WhileStatement" =>
        val cond = mintExpr(node.children.head, file)
        val body = mintExpr(node.children(1), file)
        While(cond, body, TypeRepr.NoType, origin)
      case "ThrowStatement" =>
        val expr = mintExpr(node.children.head, file)
        Throw(expr, TypeRepr.NoType, origin)
      case "ForStatement" =>
        val parts = node.children
        val init  = parts.filter(c => c.kind == "VariableDeclarationList" || c.kind == "FirstStatement").flatMap(c => List(mintStatement(c, file)))
        val cond  = parts.find(c => c.kind != "VariableDeclarationList" && c.kind != "FirstStatement" && c.kind != "Block" && isExprKind(c.kind)).map(c => mintExpr(c, file))
        val body  = parts.find(_.kind == "Block").map(c => mintExpr(c, file)).getOrElse(Literal(Constant.UnitC, TypeRepr.NoType, origin))
        For(init, cond, Nil, body, TypeRepr.NoType, origin)
      case "ForOfStatement" | "ForInStatement" =>
        val binding = node.children
          .find(c => c.kind == "VariableDeclarationList")
          .flatMap(_.children.headOption)
          .map { d =>
            val name = nameOf(d)
            val sym  = freshSym(name, name, Flags(isParam = true), SymId.None, TypeRepr.NoType, origin)
            ValDef(sym, TypeTree(TypeRepr.NoType, origin), None, origin)
          }
          .getOrElse {
            val sym = freshSym("$it", "$it", Flags(isParam = true), SymId.None, TypeRepr.NoType, origin)
            ValDef(sym, TypeTree(TypeRepr.NoType, origin), None, origin)
          }
        val iterable = node.children.find(c => isExprKind(c.kind)).map(c => mintExpr(c, file)).getOrElse(Literal(Constant.UnitC, TypeRepr.NoType, origin))
        val body     = node.children.find(_.kind == "Block").map(c => mintExpr(c, file)).getOrElse(Literal(Constant.UnitC, TypeRepr.NoType, origin))
        ForEach(binding, iterable, body, TypeRepr.NoType, origin)
      case "SwitchStatement" =>
        val scrutinee = mintExpr(node.children.head, file)
        val caseBlock = node.children.find(_.kind == "CaseBlock")
        val cases     = caseBlock.toList.flatMap(_.children).map { clause =>
          val isDefault = clause.kind == "DefaultClause"
          val labels    = if isDefault then Nil
          else clause.children.headOption.toList.map(c => mintExpr(c, file))
          val bodyStmts = clause.children.drop(if isDefault then 0 else 1).map(c => mintStatement(c, file))
          val bodyTerm  = if bodyStmts.isEmpty then Literal(Constant.UnitC, TypeRepr.NoType, origin)
          else
            bodyStmts.last match
              case t: Term => Block(bodyStmts.init, t, TypeRepr.NoType, origin)
              case _ => Block(bodyStmts, Literal(Constant.UnitC, TypeRepr.NoType, origin), TypeRepr.NoType, origin)
          CaseDef(labels, None, bodyTerm, isDefault)
        }
        Match(scrutinee, cases, TypeRepr.NoType, origin)
      case "DoStatement" =>
        val body = mintExpr(node.children.head, file)
        val cond = node.children.lift(1).map(c => mintExpr(c, file)).getOrElse(Literal(Constant.BoolC(true), TypeRepr.NoType, origin))
        DoWhile(body, cond, TypeRepr.NoType, origin)
      case "BreakStatement" =>
        Break(None, TypeRepr.NoType, origin)
      case "ContinueStatement" =>
        Continue(None, TypeRepr.NoType, origin)
      case "TryStatement" =>
        val tryBody     = node.children.find(_.kind == "Block").map(c => mintExpr(c, file)).getOrElse(Literal(Constant.UnitC, TypeRepr.NoType, origin))
        val catchClause = node.children.find(_.kind == "CatchClause")
        val catches     = catchClause.toList.map { cc =>
          val paramNode = cc.children.find(_.kind == "VariableDeclaration").orElse(cc.children.find(_.kind == "Identifier"))
          val paramName = paramNode.flatMap(_.text).orElse(paramNode.map(nameOf)).getOrElse("e")
          val paramSym  = freshSym(paramName, paramName, Flags(isParam = true), SymId.None, TypeRepr.NoType, origin)
          val paramDef  = ValDef(paramSym, TypeTree(TypeRepr.NoType, origin), None, origin)
          val body      = cc.children.find(_.kind == "Block").map(c => mintExpr(c, file)).getOrElse(Literal(Constant.UnitC, TypeRepr.NoType, origin))
          CatchCase(paramDef, body)
        }
        val finallyBody = node.children.lastOption.filter(c => c.kind == "Block" && catchClause.exists(_ ne c)).map(c => mintExpr(c, file))
        Try(Nil, tryBody, catches, finallyBody, TypeRepr.NoType, origin)
      case _ =>
        Unportable(
          Literal(Constant.UnitC, TypeRepr.NoType, origin),
          UnportableKind.FrontendBlindSpot,
          MarkerState.Open,
          None,
          s"TS stmt: ${node.kind}",
          TypeRepr.NoType,
          origin
        )

  private def mintLocalVarDecl(node: RastNode, file: RastFile): Statement =
    val origin = Origin(file.path, node.pos._1, node.pos._2)
    val decls  = node.children.flatMap { child =>
      if child.kind == "VariableDeclarationList" then child.children.filter(_.kind == "VariableDeclaration")
      else Nil
    }
    val stmts: List[Statement] = decls.map { d =>
      val name      = nameOf(d)
      val isConst   = d.flags.contains("const")
      val valOrigin = Origin(file.path, d.pos._1, d.pos._2)
      val flags     = if isConst then Flags() else Flags(isMutable = true)
      val sym       = freshSym(name, name, flags, SymId.None, TypeRepr.NoType, valOrigin)
      val tpe       = resolveTypeTree(d, file, valOrigin)
      val init      = findInit(d, file)
      ValDef(sym, tpe, init, valOrigin)
    }
    if stmts.length == 1 then stmts.head
    else Block(stmts, Literal(Constant.UnitC, TypeRepr.NoType, origin), TypeRepr.NoType, origin)

  private def mintExpr(node: RastNode, file: RastFile): Term =
    val origin = Origin(file.path, node.pos._1, node.pos._2)
    node.kind match
      case "Identifier" =>
        Ident(SymId.None, resolveTypeRepr(node, file), origin)
      case "NumericLiteral" =>
        val v = node.value match
          case Some(RastValue.Num(n)) => n
          case _                      => 0.0
        Literal(Constant.DoubleC(v), TypeRepr.NoType, origin)
      case "StringLiteral" | "FirstTemplateToken" | "NoSubstitutionTemplateLiteral" =>
        val v = node.value match
          case Some(RastValue.Str(s)) => s
          case _                      => ""
        Literal(Constant.StringC(v), TypeRepr.NoType, origin)
      case "TrueKeyword" =>
        Literal(Constant.BoolC(true), TypeRepr.NoType, origin)
      case "FalseKeyword" =>
        Literal(Constant.BoolC(false), TypeRepr.NoType, origin)
      case "NullKeyword" =>
        Literal(Constant.NullC, TypeRepr.NoType, origin)
      case "BinaryExpression" =>
        val left  = mintExpr(node.children.head, file)
        val right = mintExpr(node.children.last, file)
        Apply(left, List(right), SymId.None, resolveTypeRepr(node, file), origin)
      case "CallExpression" =>
        val fn   = mintExpr(node.children.head, file)
        val args = node.children.drop(1).map(c => mintExpr(c, file))
        Apply(fn, args, SymId.None, resolveTypeRepr(node, file), origin)
      case "PropertyAccessExpression" =>
        val obj = mintExpr(node.children.head, file)
        Select(obj, SymId.None, resolveTypeRepr(node, file), origin)
      case "ElementAccessExpression" =>
        val obj = mintExpr(node.children.head, file)
        val idx = mintExpr(node.children.last, file)
        ArrayAccess(obj, idx, resolveTypeRepr(node, file), origin)
      case "ArrayLiteralExpression" =>
        val elems   = node.children.map(c => mintExpr(c, file))
        val elemTpe = TypeTree(resolveTypeRepr(node, file), origin)
        NewArray(elemTpe, Nil, Some(elems), resolveTypeRepr(node, file), origin)
      case "ParenthesizedExpression" =>
        node.children.headOption.map(c => mintExpr(c, file)).getOrElse(Literal(Constant.UnitC, TypeRepr.NoType, origin))
      case "ConditionalExpression" if node.children.length >= 3 =>
        val cond     = mintExpr(node.children(0), file)
        val thenExpr = mintExpr(node.children(1), file)
        val elseExpr = mintExpr(node.children(2), file)
        If(cond, thenExpr, elseExpr, resolveTypeRepr(node, file), origin)
      case "AsExpression" | "TypeAssertionExpression" =>
        node.children.headOption.map(c => mintExpr(c, file)).getOrElse(Literal(Constant.UnitC, TypeRepr.NoType, origin))
      case "ArrowFunction" | "FunctionExpression" =>
        val params = mintParams(node, file, SymId.None)
        val body   = mintBody(node, file)
        Lambda(params, body, resolveTypeRepr(node, file), origin)
      case "TemplateExpression" =>
        val parts = node.children.flatMap { c =>
          c.kind match
            case "TemplateHead" | "TemplateTail" | "TemplateMiddle" =>
              val s = c.value match { case Some(RastValue.Str(v)) => v; case _ => c.text.getOrElse("") }
              if s.isEmpty then Nil else List(Literal(Constant.StringC(s), TypeRepr.NoType, origin))
            case "TemplateSpan" => c.children.map(cc => mintExpr(cc, file))
            case _              => List(mintExpr(c, file))
        }
        parts
          .reduceOption { (a, b) =>
            Apply(a, List(b), SymId.None, TypeRepr.NoType, origin)
          }
          .getOrElse(Literal(Constant.StringC(""), TypeRepr.NoType, origin))
      case "NewExpression" =>
        val tpe     = resolveTypeRepr(node, file)
        val args    = node.children.drop(1).filter(c => isExprKind(c.kind)).map(c => mintExpr(c, file))
        val newExpr = New(TypeTree(tpe, origin), tpe, origin)
        if args.isEmpty then newExpr
        else Apply(newExpr, args, SymId.None, tpe, origin)
      case "ObjectLiteralExpression" =>
        Unportable(
          Literal(Constant.UnitC, TypeRepr.NoType, origin),
          UnportableKind.FrontendBlindSpot,
          MarkerState.Open,
          None,
          "TS object literal",
          resolveTypeRepr(node, file),
          origin
        )
      case "SpreadElement" =>
        val inner = node.children.headOption.map(c => mintExpr(c, file)).getOrElse(Literal(Constant.UnitC, TypeRepr.NoType, origin))
        Spread(inner, resolveTypeRepr(node, file), origin)
      case "PrefixUnaryExpression" =>
        mintExpr(node.children.head, file)
      case "PostfixUnaryExpression" =>
        mintExpr(node.children.head, file)
      case "TypeOfExpression" =>
        Literal(Constant.StringC("object"), TypeRepr.NoType, origin)
      case "VoidExpression" =>
        Literal(Constant.UnitC, TypeRepr.NoType, origin)
      case "DeleteExpression" =>
        Literal(Constant.BoolC(true), TypeRepr.NoType, origin)
      case "Block" =>
        val stmts = node.children.map(c => mintStatement(c, file))
        if stmts.isEmpty then Literal(Constant.UnitC, TypeRepr.NoType, origin)
        else
          val (init, last) = (stmts.init, stmts.last)
          last match
            case t: Term => Block(init, t, TypeRepr.NoType, origin)
            case _ => Block(stmts, Literal(Constant.UnitC, TypeRepr.NoType, origin), TypeRepr.NoType, origin)
      case _ =>
        Unportable(
          Literal(Constant.UnitC, TypeRepr.NoType, origin),
          UnportableKind.FrontendBlindSpot,
          MarkerState.Open,
          None,
          s"TS expr: ${node.kind}",
          resolveTypeRepr(node, file),
          origin
        )

  private def isExprKind(kind: String): Boolean =
    kind match
      case "Identifier" | "NumericLiteral" | "StringLiteral" | "TrueKeyword" | "FalseKeyword" | "NullKeyword" | "BinaryExpression" | "CallExpression" | "PropertyAccessExpression" |
          "ElementAccessExpression" | "ArrayLiteralExpression" | "ObjectLiteralExpression" | "ParenthesizedExpression" | "ConditionalExpression" | "ArrowFunction" | "FunctionExpression" |
          "TemplateExpression" | "NewExpression" | "PrefixUnaryExpression" | "PostfixUnaryExpression" | "AsExpression" | "TypeAssertionExpression" | "SpreadElement" | "TypeOfExpression" |
          "VoidExpression" | "DeleteExpression" | "FirstTemplateToken" | "NoSubstitutionTemplateLiteral" | "RegularExpressionLiteral" | "TaggedTemplateExpression" | "AwaitExpression" |
          "YieldExpression" | "ThisKeyword" | "SuperKeyword" | "NonNullExpression" | "SatisfiesExpression" | "CommaListExpression" =>
        true
      case _ => false

  private def nameOf(node: RastNode): String =
    node.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse(node.text.getOrElse("$anon"))

  private def findInit(node: RastNode, file: RastFile): Option[Term] =
    node.children
      .find(c =>
        c.kind != "Identifier" && !c.kind.contains("Type") && !c.kind.contains("Keyword") &&
          c.kind != "Parameter" && c.kind != "Block" && c.kind != "DotDotDotToken"
      )
      .map(c => mintExpr(c, file))

  private def resolveTypeTree(node: RastNode, file: RastFile, origin: Origin): TypeTree =
    TypeTree(resolveTypeRepr(node, file), origin)

  private def resolveTypeRepr(node: RastNode, file: RastFile): TypeRepr =
    node.`type`.flatMap(file.types.get).map(rastTypeToRepr(_, file)).getOrElse(TypeRepr.NoType)

  private def rastTypeToRepr(rt: RastType, file: RastFile): TypeRepr =
    rt.kind match
      case "string"    => TypeRepr.TypeRef(TypeRepr.NoPrefix, SymId.None)
      case "number"    => TypeRepr.TypeRef(TypeRepr.NoPrefix, SymId.None)
      case "boolean"   => TypeRepr.TypeRef(TypeRepr.NoPrefix, SymId.None)
      case "void"      => TypeRepr.NoType
      case "null"      => TypeRepr.ConstantType(Constant.NullC)
      case "undefined" => TypeRepr.ConstantType(Constant.NullC)
      case "never"     => TypeRepr.NoType
      case "any"       => TypeRepr.NoType
      case "union"     =>
        val types = rt.types.getOrElse(Nil).flatMap(file.types.get).map(rastTypeToRepr(_, file))
        types.reduceOption((a, b) => TypeRepr.OrType(a, b)).getOrElse(TypeRepr.NoType)
      case "function" =>
        val params = rt.parameters.getOrElse(Nil).map { p =>
          val pType = file.types.get(p.`type`).map(rastTypeToRepr(_, file)).getOrElse(TypeRepr.NoType)
          (p.name, pType)
        }
        val ret = rt.returnType.flatMap(file.types.get).map(rastTypeToRepr(_, file)).getOrElse(TypeRepr.NoType)
        TypeRepr.MethodType(params, ret)
      case _ => TypeRepr.NoType
