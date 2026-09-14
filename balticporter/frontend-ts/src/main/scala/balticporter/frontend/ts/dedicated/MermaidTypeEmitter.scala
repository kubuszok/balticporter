package balticporter.corpus.mermaid

import balticporter.frontend.ts.dedicated.{DefmethodBodyTranslator, DefmethodEntry, DefnodeClass, FreeFunction}

import balticporter.frontend.ts.{RastFile, RastNode, RastType}

/** RAST-based emitter for Mermaid type definition modules.
  *
  * Handles `*Types.ts`, `config.type.ts`, and `defaultConfig.ts` files.
  * These define TypeScript interfaces and type aliases that become
  * Scala case classes and type aliases.
  *
  * Translation:
  *   - `interface Foo { x: string; y: number }` -> `final case class Foo(x: String, y: Double)`
  *   - `type Sections = Map<string, number>` -> `type Sections = mutable.Map[String, Double]`
  *   - `interface FooDB extends DiagramDB { ... }` -> (skipped, the Db class IS the interface)
  *   - Properties with function types -> skipped (methods live on the Db)
  */
object MermaidTypeEmitter {

  /** A parsed interface from the RAST. */
  private case class InterfaceDecl(
      name: String,
      fields: List[FieldDecl],
      extendsTypes: List[String],
      isExported: Boolean,
  )

  /** A single field in an interface. */
  private case class FieldDecl(
      name: String,
      scalaType: String,
      optional: Boolean,
      isFunctionType: Boolean,
  )

  /** A parsed type alias from the RAST. */
  private case class TypeAliasDecl(
      name: String,
      targetType: String,
      isExported: Boolean,
  )

  /** Emits Scala types from a RAST type file.
    *
    * @param rast        the parsed RAST file
    * @param objectName  enclosing object name, or empty for top-level
    * @param pkg         e.g. "pie" (the diagram's package)
    * @param extraPkg    additional package segments (e.g. List("common"))
    * @param extraImports additional imports
    * @return the complete Scala source
    */
  def emitTypes(
      rast: RastFile,
      objectName: String,
      pkg: String,
      extraPkg: List[String] = Nil,
      extraImports: List[String] = Nil,
  ): String = {
    val sb = new StringBuilder
    sb.append(header(rast.path, if (objectName.nonEmpty) s"$objectName.scala" else "types.scala"))

    // Package
    sb.append(s"package ssg\npackage mermaid\n")
    if (pkg.nonEmpty) sb.append(s"package diagrams\npackage $pkg\n")
    for (p <- extraPkg) sb.append(s"package $p\n")
    sb.append("\n")

    // Imports
    val interfaces = extractInterfaces(rast)
    val typeAliases = extractTypeAliases(rast)

    val needsMutable = interfaces.exists(i => i.fields.exists(f =>
      f.scalaType.contains("mutable.") || f.scalaType.contains("ArrayBuffer") ||
      f.scalaType.contains("Map[")
    )) || typeAliases.exists(_.targetType.contains("mutable."))

    if (needsMutable) sb.append("import scala.collection.mutable\n")
    for (imp <- extraImports) sb.append(s"import $imp\n")
    if (needsMutable || extraImports.nonEmpty) sb.append("\n")

    // Type aliases
    for (ta <- typeAliases) {
      sb.append(s"type ${ta.name} = ${ta.targetType}\n\n")
    }

    // Interfaces -> case classes (skip DB interfaces that just declare methods)
    for (iface <- interfaces) {
      if (isDbInterface(iface)) {
        sb.append(s"// ${iface.name} — method declarations live on the Db class\n\n")
      } else if (iface.fields.isEmpty) {
        sb.append(s"// ${iface.name} — empty interface\n\n")
      } else {
        emitCaseClass(sb, iface)
        sb.append("\n")
      }
    }

    sb.toString
  }

  /** An interface that just declares DiagramDB methods (getConfig, clear, etc.)
    * should be skipped — those live on the Db class itself. */
  private def isDbInterface(iface: InterfaceDecl): Boolean = {
    val dbPatterns = Set("DiagramDB", "DiagramDb", "DB")
    val extendsDb = iface.extendsTypes.exists(t => dbPatterns.exists(p => t.contains(p)))
    val allFunctions = iface.fields.nonEmpty && iface.fields.forall(_.isFunctionType)
    extendsDb || (iface.name.endsWith("DB") && allFunctions)
  }

  private def emitCaseClass(sb: StringBuilder, iface: InterfaceDecl): Unit = {
    val dataFields = iface.fields.filterNot(_.isFunctionType)
    if (dataFields.isEmpty) {
      sb.append(s"// ${iface.name} — all members are functions, lives on the Db class\n")
      return
    }

    sb.append(s"final case class ${iface.name}(\n")
    val params = dataFields.map { f =>
      val tpe = if (f.optional && !f.scalaType.startsWith("Option["))
        s"Option[${f.scalaType}] = None"
      else if (f.optional)
        s"${f.scalaType} = None"
      else
        f.scalaType
      val default = if (f.optional && !tpe.contains("= None"))
        s"$tpe = ${defaultFor(f.scalaType)}"
      else tpe
      s"  ${safeName(f.name)}: $default"
    }
    sb.append(params.mkString(",\n"))
    sb.append("\n)\n")
  }

  /** Extracts InterfaceDeclaration nodes from the RAST. */
  private def extractInterfaces(rast: RastFile): List[InterfaceDecl] = {
    rast.nodes.flatMap { node =>
      if (node.kind == "InterfaceDeclaration") {
        val name = nameOf(node)
        val isExported = node.flags.contains("ExportKeyword")
        val extends_ = node.children.filter(_.kind == "HeritageClause").flatMap { hc =>
          hc.children.filter(_.kind == "ExpressionWithTypeArguments").map { ewta =>
            ewta.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("")
          }
        }
        val fields = node.children.filter(_.kind == "PropertySignature").map { ps =>
          val fName = nameOf(ps)
          val isOptional = ps.flags.contains("QuestionToken") ||
            ps.children.exists(_.kind == "QuestionToken")
          val isFuncType = ps.children.exists(_.kind == "FunctionType")
          val scalaType = if (isFuncType) "Any /* function */"
            else inferFieldType(ps, rast)
          FieldDecl(fName, scalaType, isOptional, isFuncType)
        }
        Some(InterfaceDecl(name, fields, extends_, isExported))
      } else None
    }
  }

  /** Extracts TypeAliasDeclaration nodes from the RAST. */
  private def extractTypeAliases(rast: RastFile): List[TypeAliasDecl] = {
    rast.nodes.flatMap { node =>
      if (node.kind == "TypeAliasDeclaration") {
        val name = nameOf(node)
        val isExported = node.flags.contains("ExportKeyword")
        val targetNode = node.children.find(c =>
          c.kind != "Identifier" && c.kind != "TypeParameter"
        )
        val targetType = targetNode.map(t => tsTypeNodeToScala(t, rast)).getOrElse("Any")
        Some(TypeAliasDecl(name, targetType, isExported))
      } else None
    }
  }

  /** Resolves a PropertySignature's type. */
  private def inferFieldType(ps: RastNode, rast: RastFile): String = {
    val typeNode = ps.children.find(c =>
      c.kind == "TypeReference" || isTypeKeyword(c.kind) || c.kind == "ArrayType" ||
      c.kind == "UnionType" || c.kind == "TypeLiteral" || c.kind == "IntersectionType" ||
      c.kind == "LiteralType"
    )
    typeNode match {
      case Some(t) => tsTypeNodeToScala(t, rast)
      case None =>
        ps.`type`.flatMap(rast.types.get).map(t => rastTypeToScala(t, rast)).getOrElse("Any")
    }
  }

  private def tsTypeNodeToScala(node: RastNode, rast: RastFile): String = {
    node.kind match {
      case "StringKeyword" => "String"
      case "NumberKeyword" => "Double"
      case "BooleanKeyword" => "Boolean"
      case "VoidKeyword" => "Unit"
      case "AnyKeyword" => "Any"
      case "NeverKeyword" => "Nothing"
      case "NullKeyword" => "Null"
      case "UndefinedKeyword" => "Unit"
      case "TypeReference" =>
        val name = node.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("Any")
        val typeArgs = node.children.filter(c => c.kind != "Identifier").map(c => tsTypeNodeToScala(c, rast))
        name match {
          case "Array" =>
            val elemType = typeArgs.headOption.getOrElse("Any")
            s"mutable.ArrayBuffer[$elemType]"
          case "Map" =>
            if (typeArgs.size >= 2) s"mutable.Map[${typeArgs(0)}, ${typeArgs(1)}]"
            else "mutable.Map[String, Any]"
          case "Set" =>
            val elemType = typeArgs.headOption.getOrElse("Any")
            s"mutable.Set[$elemType]"
          case "Record" =>
            if (typeArgs.size >= 2) s"mutable.Map[${typeArgs(0)}, ${typeArgs(1)}]"
            else "mutable.Map[String, Any]"
          case "Required" | "RequiredDeep" | "Readonly" =>
            typeArgs.headOption.getOrElse("Any")
          case "Partial" =>
            typeArgs.headOption.map(t => s"Option[$t]").getOrElse("Any")
          case other =>
            if (typeArgs.nonEmpty) s"$other[${typeArgs.mkString(", ")}]"
            else other
        }
      case "ArrayType" =>
        val elem = node.children.headOption.map(c => tsTypeNodeToScala(c, rast)).getOrElse("Any")
        s"mutable.ArrayBuffer[$elem]"
      case "UnionType" =>
        val types = node.children.map(c => tsTypeNodeToScala(c, rast)).distinct
        if (types.contains("Null") || types.contains("Unit")) {
          val real = types.filterNot(t => t == "Null" || t == "Unit")
          if (real.size == 1) s"Option[${real.head}]"
          else types.mkString(" | ")
        } else types.mkString(" | ")
      case "LiteralType" =>
        node.children.headOption.map(_.kind) match {
          case Some("StringLiteral") => "String"
          case Some("NumericLiteral") => "Double"
          case Some("TrueKeyword" | "FalseKeyword") => "Boolean"
          case Some("NullKeyword") => "Null"
          case _ => "Any"
        }
      case "TypeLiteral" =>
        // Object literal type: { x: string, y: number }
        val fields = node.children.filter(_.kind == "PropertySignature").map { ps =>
          val fName = nameOf(ps)
          val fType = inferFieldType(ps, rast)
          s"$fName: $fType"
        }
        if (fields.isEmpty) "Any"
        else "Any /* object type */"
      case "IntersectionType" =>
        val types = node.children.map(c => tsTypeNodeToScala(c, rast))
        types.headOption.getOrElse("Any") // Simplification: take first type
      case "FunctionType" => "Any /* function */"
      case _ => "Any"
    }
  }

  private def rastTypeToScala(rt: RastType, rast: RastFile): String = {
    rt.kind match {
      case "string" => "String"
      case "number" => "Double"
      case "boolean" => "Boolean"
      case "void" => "Unit"
      case "null" | "undefined" => "Null"
      case "any" => "Any"
      case "never" => "Nothing"
      case "array" =>
        val elemType = rt.elementType.flatMap(rast.types.get).map(t => rastTypeToScala(t, rast)).getOrElse("Any")
        s"mutable.ArrayBuffer[$elemType]"
      case "reference" =>
        val target = rt.target.flatMap(rast.types.get)
        val typeArgs = rt.typeArguments.getOrElse(Nil).flatMap(rast.types.get).map(t => rastTypeToScala(t, rast))
        val baseName = target.map(_.text).getOrElse(rt.text.takeWhile(_ != '<'))
        baseName match {
          case s if s == "Map" || s.startsWith("Map") =>
            if (typeArgs.size >= 2) s"mutable.Map[${typeArgs(0)}, ${typeArgs(1)}]"
            else "mutable.Map[String, Any]"
          case s if s == "Set" || s.startsWith("Set") =>
            s"mutable.Set[${typeArgs.headOption.getOrElse("Any")}]"
          case s if s == "Array" || s.startsWith("Array") =>
            s"mutable.ArrayBuffer[${typeArgs.headOption.getOrElse("Any")}]"
          case _ =>
            if (typeArgs.nonEmpty) s"$baseName[${typeArgs.mkString(", ")}]"
            else baseName
        }
      case "object" =>
        val txt = rt.text.trim
        if (txt.nonEmpty && txt.head.isUpper && txt.forall(c => c.isLetterOrDigit || c == '_')) txt
        else "Any"
      case _ => "Any"
    }
  }

  private def isTypeKeyword(kind: String): Boolean =
    kind == "StringKeyword" || kind == "NumberKeyword" || kind == "BooleanKeyword" ||
    kind == "VoidKeyword" || kind == "AnyKeyword" || kind == "NeverKeyword" ||
    kind == "NullKeyword" || kind == "UndefinedKeyword"

  private def nameOf(node: RastNode): String =
    node.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("$anon")

  private def safeName(name: String): String = {
    val reserved = Set("type", "class", "object", "trait", "val", "var", "def",
      "import", "package", "match", "case", "if", "else", "while", "for",
      "do", "return", "throw", "try", "catch", "finally", "yield", "new",
      "extends", "with", "super", "this", "abstract", "final", "sealed",
      "private", "protected", "override", "implicit", "lazy", "forSome",
      "macro", "true", "false", "null")
    if (reserved.contains(name)) s"`$name`"
    else name
  }

  private def defaultFor(tpe: String): String = tpe match {
    case "String" => "\"\""
    case "Double" | "Int" => "0"
    case "Boolean" => "false"
    case t if t.contains("ArrayBuffer") => "mutable.ArrayBuffer.empty"
    case t if t.contains("Map") => "mutable.Map.empty"
    case t if t.contains("Set") => "mutable.Set.empty"
    case _ => "null"
  }

  private def header(sourcePath: String, @annotation.nowarn("msg=unused") targetFile: String): String =
    s"""/*
       | * Mermaid diagramming engine - Scala 3 port
       | *
       | * Ported from: $sourcePath
       | * Original license: MIT
       | *
       | * Auto-generated by MermaidTypeEmitter from RAST v1
       | */
       |""".stripMargin
}
