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
      // `{}` is a map only where its slot declares a MUTABLE one, which JS may then fill; elsewhere it is an object of a shape nothing names.
      // The map takes the slot's key and value types, written out where the body can name them and inferred from the slot otherwise.
      val mutableMap = expected.filter(rt => rt.startsWith("mutable.Map[") || rt.startsWith("scala.collection.mutable.Map["))
      if mutableMap.isEmpty then refuse("js-map-construction")
      mutableMap.map(rt => rt.substring(rt.indexOf('['))).filter(writableType).fold("scala.collection.mutable.Map.empty")(args => s"scala.collection.mutable.Map.empty$args")
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
      val tagged = taggedClass(litEntries, expected)
      // the target file must be able to write the class
      if tagged.exists(c => spelled(c).isEmpty) then refuse("class-not-nameable")
      val typeName = tagged.orElse(expected.map(_.takeWhile(c => c != '[' && c != ' ')))
      val entries  = if tagged.isDefined then litEntries.filterNot((k, _) => conventions.tagField.contains(k)) else litEntries
      typeName.flatMap(ctorSchema.get) match
        case Some(ctorParams) =>
          // a key names the parameter of its own spelling, of the reference's renamed spelling, or of either backticked
          def paramFor(k: String): Option[ReferenceSignatures.CtorParam] =
            val names = Set(snakeToCamel(k), k, memberRenames.getOrElse(snakeToCamel(k), snakeToCamel(k))).map(_.stripPrefix("`").stripSuffix("`"))
            ctorParams.find(p => names(p.name.stripPrefix("`").stripSuffix("`")))
          val keyed = entries.map((k, v) => (k, paramFor(k), v))
          // a field JS leaves out is optional in its interface, as the reference's default for it says
          val missing = ctorParams.filterNot(p => keyed.exists(_._2.contains(p))).filterNot(_.hasDefault)
          val copied  = if tagged.isEmpty then caseCopy(props, typeName.get) else None
          if copied.isDefined then copied.get
          // A key the class has no field for would be dropped silently.
          else if keyed.exists(k => k._2.isEmpty || k._3.isEmpty) || props.exists(p => p.kind != "PropertyAssignment" && p.kind != "ShorthandPropertyAssignment") then
            refuse("object-literal-unknown-field")
            emitObjectLiteralAsMap(props, None)
          else if missing.nonEmpty || keyed.map(_._2).distinct.size != keyed.size then
            refuse("object-literal-missing-field")
            emitObjectLiteralAsMap(props, None)
          else
            // in the literal's own order, which is the order JS evaluates its values in
            val args = keyed.collect { case (_, Some(param), Some(valueNode)) =>
              // a parameter the reference names with a keyword is written backticked, as the reference writes it
              val name = param.name.stripPrefix("`").stripSuffix("`")
              s"${if scalaKeywords.contains(name) then s"`$name`" else name} = ${translateExprAt(valueNode, param.tpe)}"
            }
            s"${tagged.flatMap(spelled).getOrElse(typeName.get)}(${args.mkString(", ")})"
        case None =>
          // No schema: fall back to mutable.Map, refuse unless the slot declares a Map
          if !expected.exists(rt => baseName(rt) == "Map" && mapValueType(rt).isDefined) then refuse("js-map-construction")
          emitObjectLiteralAsMap(props, expected)

  /** `{ ...src, k: v }` at a slot of the case class `cls`, where `src` is of that class: JS copies src's fields and then sets each key, as `src.copy(k = v)` does, evaluating src and then the values
    * in the literal's order. None unless the spread comes first and alone and every key names a distinct constructor parameter.
    */
  protected def caseCopy(props: List[RastNode], cls: String): Option[String] =
    props match
      case spread :: rest if spread.kind == "SpreadAssignment" && spread.children.nonEmpty && !rest.exists(_.kind == "SpreadAssignment") =>
        val src = spread.children.head
        for
          params <- ctorSchema.get(cls)
          if ctorSchema.isCase(cls) && exprType(src).contains(cls)
          pairs <- plainProperties(RastNode("ObjectLiteralExpression", 0, spread.pos, children = rest))
          keyed = pairs.map { (k, v) =>
            val names = Set(snakeToCamel(k), k, memberRenames.getOrElse(snakeToCamel(k), snakeToCamel(k)))
            (params.find(p => names(p.name.stripPrefix("`").stripSuffix("`"))), v)
          }
          if keyed.forall(_._1.isDefined) && keyed.map(_._1).distinct.size == keyed.size
        yield
          val args = keyed.collect { case (Some(p), v) =>
            val name = p.name.stripPrefix("`").stripSuffix("`")
            s"${if scalaKeywords.contains(name) then s"`$name`" else name} = ${translateExprAt(v, p.tpe)}"
          }
          s"${translateExpr(src)}.copy(${args.mkString(", ")})"
      case _ => None

  /** `Object.assign({}, src)` or `Object.assign({}, src, { k: v })`: a fresh copy of `src` with those keys set, read as the literal `{ ...src, k: v }`. None for any other argument list. */
  protected def objectAssignCopy(args: List[RastNode]): Option[String] =
    args match
      case target :: src :: more if target.kind == "ObjectLiteralExpression" && target.children.isEmpty && more.forall(_.kind == "ObjectLiteralExpression") && more.size <= 1 =>
        val spread = RastNode("SpreadAssignment", 0, src.pos, children = List(src))
        exprType(src).map(baseName).flatMap(cls => caseCopy(spread :: more.flatMap(_.children), cls))
      case _ => None

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

  /** An object literal used as a dictionary, built as the map its slot declares (`expected`): each key the string JS makes of it, each value at the map's value type and evaluated in the literal's
    * order. A spread, a method or a computed key builds what no list of pairs says, and is refused.
    */
  private def emitObjectLiteralAsMap(props: List[RastNode], expected: Option[String]): String =
    val valueType = expected.flatMap(mapValueType)
    val entries   = props.map { p =>
      val keyNode = p.children.headOption
      val key     = keyNode.flatMap(k => if k.kind == "Identifier" then k.text else if k.kind == "StringLiteral" then k.value.collect { case balticporter.frontend.ts.RastValue.Str(s) => s } else None)
      def value(v: RastNode): String = s"(${valueType.fold(translateExpr(v))(translateExprAt(v, _))})"
      (p.kind, key) match
        case ("PropertyAssignment", Some(k)) if p.children.size >= 2 => s"\"${escapeString(k)}\" -> ${value(p.children(1))}"
        case ("ShorthandPropertyAssignment", Some(k))                => s"\"${escapeString(k)}\" -> ${value(keyNode.get)}"
        case _                                                       =>
          refuse("object-literal-entry")
          "??? /* entry */"
    }
    val mutable = expected.exists(rt => rt.startsWith("mutable.Map[") || rt.startsWith("scala.collection.mutable.Map["))
    s"scala.collection.${if mutable then "mutable" else "immutable"}.Map(${entries.mkString(", ")})"

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
