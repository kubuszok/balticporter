package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.RastNode
import BodySyntax.*

/** Destructuring: a JS binding pattern rendered as one Scala binding per name, read off a source evaluated once, wherever the source's declared type says what each name reads. */
private[dedicated] trait BodyBindings extends BodyScope:

  /** The (property, bound name) pairs of an object pattern, or the bound names of an array pattern (`None` for a hole), when every element is a plain name; none when one has a default, a rest or a
    * nested pattern, each of which binds something a field read does not.
    */
  private def plainElements(pattern: RastNode): Option[List[Option[(String, String)]]] =
    val elems = pattern.children.map { e =>
      e.kind match
        case "OmittedExpression" if pattern.kind == "ArrayBindingPattern"            => Some(None)
        case "BindingElement" if !e.flags.exists(f => f == "rest" || f == "default") =>
          val ids = e.children.filter(_.kind == "Identifier").flatMap(_.text)
          if ids.size != e.children.size then None
          else if e.flags.contains("renamed") && ids.size == 2 then Some(Some((ids.head, ids(1))))
          else if !e.flags.contains("renamed") && ids.size == 1 then Some(Some((ids.head, ids.head)))
          else None
        case _ => None
    }
    if elems.forall(_.isDefined) then Some(elems.flatten) else None

  /** The names a plain pattern binds; none for any other. */
  protected def bindingsDeclaredBy(pattern: RastNode): List[String] =
    plainElements(pattern).toList.flatten.flatten.map(_._2)

  /** `const {a, b: c} = o` reads each field off `o`'s declared class; `const [a, b] = p` reads a tuple record's fields or a Scala tuple's components by position. Anything else is refused: a JS array
    * read past its end is `undefined`, where Scala throws.
    */
  protected def destructure(pattern: RastNode, init: Option[RastNode], keyword: String, indent: String): Unit =
    val elements = plainElements(pattern)
    if init.isEmpty || elements.isEmpty then
      refuse("destructuring-pattern")
      sb.append(s"$indent??? /* destructuring */\n")
    else
      val src     = init.get
      val srcExpr = translateExpr(src)
      // the source is evaluated once, as JS does
      val path =
        if isSimplePath(srcExpr) then srcExpr
        else
          val tmp = freshLabel("d")
          sb.append(s"${indent}val $tmp = $srcExpr\n")
          tmp
      destructureFrom(pattern, path, exprType(src), keyword, indent)

  /** The bindings of `pattern` read off `path`, a value of type `srcType` that reading twice evaluates nothing twice. */
  protected def destructureFrom(pattern: RastNode, path: String, srcType: Option[String], keyword: String, indent: String): Unit =
    val elements = plainElements(pattern)
    if elements.isEmpty then
      refuse("destructuring-pattern")
      sb.append(s"$indent??? /* destructuring */\n")
    else
      def bind(name: String, read: String, tpe: Option[String]): Unit =
        val scName = snakeToCamel(name)
        localNames += scName
        tpe.foreach(localTypes(scName) = _)
        sb.append(s"$indent$keyword $scName = $read\n")
      val cls = srcType.map(baseName)
      if pattern.kind == "ObjectBindingPattern" then
        for (prop, name) <- elements.get.flatten do
          val camel = snakeToCamel(prop)
          cls.filter(memberIndex.knowsType) match
            case Some(c) =>
              List(memberRenames.getOrElse(camel, camel), camel, prop).find(memberIndex.hasMember(c, _)) match
                case Some(member) => bind(name, s"$path.$member", memberIndex.memberType(c, member))
                case None         =>
                  refuse("wrong-member-access")
                  bind(name, s"$path.$camel", None)
            case None =>
              // a field read off a value whose class nothing declares cannot be checked against anything
              refuse("receiver-type-unknown")
              bind(name, s"$path.$camel", None)
      else
        val names = elements.get
        cls.flatMap(c => conventions.tupleRecords.get(c).map(c -> _)) match
          case Some((c, fields)) if names.size <= fields.size =>
            for (n, f) <- names.zip(fields); (_, name) <- n do bind(name, s"$path.$f", memberIndex.memberType(c, f))
          case _ =>
            srcType.flatMap(tupleComponents) match
              case Some(parts) if parts.size == names.size =>
                for ((n, t), i) <- names.zip(parts).zipWithIndex; (_, name) <- n do bind(name, s"$path._${i + 1}", Some(t))
              case _ =>
                refuse("array-destructuring")
                for (n, i) <- names.zipWithIndex; (_, name) <- n do bind(name, s"$path($i)", None)
