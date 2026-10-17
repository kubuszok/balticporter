package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.RastNode
import BodySyntax.*
import DefmethodBodyTranslator.ReturnTypePlaceholder

/** A JS `switch` as a Scala `match`. JS runs on from a clause control does not leave into the next one, compares each label with `===` in order, and runs `default` only when no label matched,
  * wherever it stands; a `match` arm cannot be left early, so a `break` anywhere but at a clause's end is refused.
  */
private[dedicated] trait BodySwitch extends BodyScope:

  protected def translateExprAt(node: RastNode, expectedType: String): String

  // a clause's statements without the unlabelled `break` that ends it, and whether control leaves the switch there
  final private case class Clause(node: RastNode, body: List[RastNode], leaves: Boolean)

  protected def translateSwitchStatement(node: RastNode, indent: String, isLast: Boolean): Unit =
    node.children.headOption match
      case None =>
        refuse("EmptySwitch")
        sb.append(s"$indent??? /* empty switch */\n")
      case Some(selector) =>
        val clauses = node.children.find(_.kind == "CaseBlock").toList.flatMap(_.children).filter(c => c.kind == "CaseClause" || c.kind == "DefaultClause").map { c =>
          val stmts = if c.kind == "CaseClause" then c.children.drop(1) else c.children
          val ends  = stmts.lastOption.exists(s => s.kind == "BreakStatement" && s.children.isEmpty)
          val body  = if ends then stmts.init else stmts
          if body.exists(containsBreak) then refuse("switch-break-in-middle")
          Clause(c, body, ends || body.lastOption.exists(exitsAlways))
        }
        // what runs from clause i: its own statements, then the next clause's while control falls through
        def arm(i: Int): List[RastNode] = clauses(i).body ++ (if clauses(i).leaves || i + 1 >= clauses.size then Nil else arm(i + 1))
        val selType = exprType(selector)
        sb.append(s"$indent(${translateExpr(selector)}) match {\n")
        for (c, i) <- clauses.zipWithIndex if c.node.kind == "CaseClause" do
          sb.append(s"$indent  ${pattern(c.node.children.head, selType)} =>\n")
          renderArm(arm(i), indent + "    ", isLast)
        // `default` is tried only after every label, wherever it stands; without one nothing runs. Where the labels name every member of an
        // enumeration selector, only an absent selector reaches it, and Scala reports a `case _` there unreachable
        val members = selType.toList.flatMap(t => enumIndex.byValue.keys.filter(_._1 == baseName(t)).map(_._2)).toSet
        val labels  = clauses.filter(_.node.kind == "CaseClause").flatMap(_.node.children.headOption).flatMap(_.value.collect { case balticporter.frontend.ts.RastValue.Str(s) => s }).toSet
        sb.append(s"$indent  ${if members.nonEmpty && members.subsetOf(labels) then "case null" else "case _"} =>\n")
        clauses.indexWhere(_.node.kind == "DefaultClause") match
          case -1 => renderArm(Nil, indent + "    ", isLast)
          case d  => renderArm(arm(d), indent + "    ", isLast)
        sb.append(s"$indent}\n")

  // In tail position an arm ending in a return or a throw is the body's value; one control runs off answers what falling off the function's end does.
  private def renderArm(stmts: List[RastNode], indent: String, isLast: Boolean): Unit =
    val exits = stmts.lastOption.exists(exitsAlways)
    translateSequence(stmts, indent, isLast && exits)
    if !(isLast && exits) then
      if isLast && declaredReturnType.exists(rt => rt != "Unit" && rt != ReturnTypePlaceholder) then
        sb.append(s"$indent${translateExprAt(RastNode("UndefinedKeyword", 0, (0, 0)), declaredReturnType.get)}\n")
      else if stmts.isEmpty then sb.append(s"$indent()\n")

  /** The pattern a case label is, compared as JS's `===` compares it: a string or a boolean literal by value, a string at an enumeration's slot as its member, any other label by a guard. */
  private def pattern(label: RastNode, selType: Option[String]): String =
    label.kind match
      case "StringLiteral" =>
        selType match
          case Some("String") => s"case ${translateExpr(label)}"
          case Some(t)        =>
            val member = translateExprAt(label, t)
            if !member.contains('.') then refuse("switch-label-type")
            s"case $member"
          case None =>
            // a string pattern against a selector of unknown type may compare nothing JS compares
            refuse("switch-label-type")
            s"case ${translateExpr(label)}"
      case "TrueKeyword" | "FalseKeyword" => s"case ${translateExpr(label)}"
      case _                              =>
        val labelType = exprType(label)
        val agree     = (labelType, selType) match
          case (Some(l), Some(s)) => baseName(l) == baseName(s) || (numericTypes(baseName(l)) && numericTypes(baseName(s)))
          case _                  => false
        if !agree then refuse("switch-label-type")
        val v = freshLabel("s")
        val e = translateExpr(label)
        if labelType.exists(comparesByIdentity) then s"case $v if $v eq ($e)" else s"case $v if $v == ($e)"
