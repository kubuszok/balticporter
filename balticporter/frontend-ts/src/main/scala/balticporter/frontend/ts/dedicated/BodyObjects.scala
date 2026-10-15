package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.{ RastNode, ReferenceSignatures }
import BodySyntax.*

/** JS object literals: the class a slot declares built from one, the named arguments a reference method's parameters spell one as, and a library's node factories. */
private[dedicated] trait BodyObjects extends BodyScope:

  protected def translateExprAt(node: RastNode, expectedType: String): String

  /** The `(key, value)` pairs of an object literal whose every property is a plain `k: v` or `k`; none when one is a spread, a method or a computed key, each of which builds what a key list cannot
    * say.
    */
  protected def plainProperties(node: RastNode): Option[List[(String, RastNode)]] =
    val props = node.children.filterNot(_.kind == "Comment")
    val pairs = props.flatMap { p =>
      p.kind match
        case "PropertyAssignment" if p.children.headOption.exists(k => k.kind == "Identifier" || k.kind == "StringLiteral") =>
          for
            key <- p.children.head.text.orElse(p.children.head.value.collect { case balticporter.frontend.ts.RastValue.Str(s) => s })
            value <- p.children.drop(1).headOption
          yield (key, value)
        case "ShorthandPropertyAssignment" => p.children.headOption.flatMap(id => id.text.map(_ -> id))
        case _                             => None
    }
    if pairs.size == props.size then Some(pairs) else None

  /** `f({ a: x, b: y })` at a reference method that spells the options bag as parameters: `f(a = x, b = y)`, each value at its parameter's type, an omitted key left to the parameter's default. None
    * when a key names no parameter or a parameter with no default is left out — then the bag has no such reading.
    */
  protected def namedArguments(params: List[ReferenceSignatures.ParamSig], objLit: RastNode): Option[List[String]] =
    plainProperties(objLit).flatMap { pairs =>
      val byName = pairs.map((k, v) => (memberRenames.getOrElse(snakeToCamel(k), snakeToCamel(k)), v))
      val known  = byName.forall((k, _) => params.exists(_.name == k))
      val filled = params.forall(p => p.hasDefault || byName.exists(_._1 == p.name))
      if params.size < 2 || !known || !filled || byName.map(_._1).distinct.size != byName.size then None
      else Some(byName.map((k, v) => s"$k = ${translateExprAt(v, params.find(_.name == k).get.tpe)}"))
    }

  /** The named arguments of a call passing one object literal to a signature that spells it as parameters; none for any other call. */
  protected def optionsBag(sig: Option[ReferenceSignatures.MethodSig], args: List[RastNode]): Option[List[String]] =
    (sig, args) match
      case (Some(s), List(o)) if o.kind == "ObjectLiteralExpression" => namedArguments(s.params, o)
      case _                                                         => None

  /** A JS object literal builds the class its SLOT declares (`expected`); with no declared slot type there is no faithful Scala value. */
  protected def translateObjectLiteral(node: RastNode, expected: Option[String]): String =
    val props = node.children.filter(c =>
      c.kind == "PropertyAssignment" || c.kind == "ShorthandPropertyAssignment" ||
        c.kind == "SpreadAssignment" || c.kind == "MethodDeclaration"
    )
    if props.isEmpty then
      // `{}` is a map only where its slot declares one; elsewhere it is an object of a shape nothing names
      if !expected.exists(rt => rt.contains("Map") || rt.contains("map")) then refuse("js-map-construction")
      "scala.collection.mutable.Map.empty[String, Any]"
    else
      // Extract literal key-value pairs
      val litEntries = props.flatMap { p =>
        p.kind match
          case "PropertyAssignment" =>
            val key = p.children.headOption.flatMap(_.text).getOrElse("?")
            Some((key, p.children.drop(1).headOption))
          case "ShorthandPropertyAssignment" =>
            val key = p.children.headOption.flatMap(_.text).getOrElse("?")
            Some((key, Some(p.children.head)))
          case _ => None
      }
      // A literal carrying the library's type tag builds the one class declaring that tag, where the slot admits it; any other builds the slot's class
      val tagged   = taggedClass(litEntries, expected)
      val typeName = tagged.orElse(expected.map(_.takeWhile(c => c != '[' && c != ' ')))
      val entries  = if tagged.isDefined then litEntries.filterNot((k, _) => conventions.tagField.contains(k)) else litEntries
      typeName.flatMap(ctorSchema.get) match
        case Some(ctorParams) =>
          val keyMap          = entries.map { case (k, v) => snakeToCamel(k) -> v }.toMap
          val missingRequired = ctorParams.filterNot(_.hasDefault).filterNot(p => keyMap.contains(p.name))
          // A key the class has no field for would be dropped silently.
          val unknownKeys = keyMap.keySet -- ctorParams.map(_.name)
          if unknownKeys.nonEmpty || props.exists(p => p.kind != "PropertyAssignment" && p.kind != "ShorthandPropertyAssignment") then
            refuse("object-literal-unknown-field")
            emitObjectLiteralAsMap(props)
          else if missingRequired.nonEmpty then
            refuse("object-literal-missing-field")
            emitObjectLiteralAsMap(props)
          else
            val args = ctorParams.flatMap { param =>
              keyMap.get(param.name) match
                case Some(Some(valueNode)) => Some(s"${param.name} = ${translateExprAt(valueNode, param.tpe)}")
                case Some(None)            => Some(s"${param.name} = ???")
                case None                  => None // has default, omitted
            }
            s"${typeName.get}(${args.mkString(", ")})"
        case None =>
          // No schema: fall back to mutable.Map, refuse unless the slot declares a Map
          if !expected.exists(rt => rt.contains("Map") || rt.contains("map")) then refuse("js-map-construction")
          emitObjectLiteralAsMap(props)

  /** The class a `{ <tag>: "Y", … }` literal builds: the ONE class the reference declares with that tag, when the slot names no type or a type the class extends. */
  private def taggedClass(entries: List[(String, Option[RastNode])], expected: Option[String]): Option[String] =
    for
      (tag, member) <- conventions.typeTag
      valueNode <- entries.collectFirst { case (k, Some(v)) if k == tag => v }
      value <- valueNode.value.collect { case balticporter.frontend.ts.RastValue.Str(s) if valueNode.kind == "StringLiteral" => s }
      cls <- memberIndex.classesDeclaring(member, value) match
        case List(c) => Some(c)
        case _       => None
      if expected.forall(e => memberIndex.ancestry(cls).contains(baseName(e)) || Set("Any", "AnyRef")(baseName(e)))
    yield cls

  private def emitObjectLiteralAsMap(props: List[RastNode]): String =
    val entries = props.map { p =>
      p.kind match
        case "PropertyAssignment" =>
          val key   = p.children.headOption.flatMap(_.text).getOrElse("?")
          val value = p.children.drop(1).headOption.map(translateExpr).getOrElse("???")
          s"\"${escapeString(key)}\" -> $value"
        case "ShorthandPropertyAssignment" =>
          val key = p.children.headOption.flatMap(_.text).getOrElse("?")
          s"\"${escapeString(key)}\" -> ${snakeToCamel(key)}"
        case "SpreadAssignment" =>
          val expr = p.children.headOption.map(translateExpr).getOrElse("???")
          s"/* spread */ $expr"
        case "MethodDeclaration" =>
          val name = p.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("?")
          refuse(s"MethodInObject:$name")
          s"\"$name\" -> ??? /* method */"
        case _ => "??? /* unknown prop */"
    }
    s"scala.collection.mutable.Map(${entries.mkString(", ")})"

  /** A node-factory call `f(Cls, orig, { prop: val, ... })`: `{ val $n = new Cls(); $n.prop = val; $n }`. */
  protected def translateMakeNode(args: List[RastNode]): String =
    if args.isEmpty then return "??? /* node factory: no args */"
    val classArg   = args.head
    val className  = classArg.text.getOrElse("")
    val scalaClass = conventions.scalaClassName(className)

    // Optional origin argument and props argument
    val propsArg = args.find(_.kind == "ObjectLiteralExpression")
    // what a factory takes from its origin node (source positions, say) is the library's own rule, which no assignment here reproduces
    if args.drop(1).exists(a => a.kind != "ObjectLiteralExpression" && a.kind != "NullKeyword" && !a.text.contains("undefined")) then refuse("node-factory-origin")

    propsArg match
      case Some(objLit) =>
        val props = objLit.children.filter(c => c.kind == "PropertyAssignment" || c.kind == "ShorthandPropertyAssignment")
        if props.isEmpty then s"new $scalaClass()"
        else
          val assignments = props.map { p =>
            p.kind match
              case "PropertyAssignment" =>
                val key   = p.children.headOption.flatMap(_.text).getOrElse("?")
                val value = p.children.drop(1).headOption.map(translateExpr).getOrElse("???")
                s"$$n.${snakeToCamel(key)} = $value"
              case "ShorthandPropertyAssignment" =>
                val key = p.children.headOption.flatMap(_.text).getOrElse("?")
                s"$$n.${snakeToCamel(key)} = ${snakeToCamel(key)}"
              case _ => "???"
          }
          s"{ val $$n = new $scalaClass(); ${assignments.mkString("; ")}; $$n }"
      case None =>
        s"new $scalaClass()"
