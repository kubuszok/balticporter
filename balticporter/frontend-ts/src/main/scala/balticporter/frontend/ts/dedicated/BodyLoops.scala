package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.RastNode
import BodySyntax.*

/** JS loops as Scala `while` loops: a `break` leaves through a boundary around the loop and a `continue` through one around the body, and a `for` loop's update still runs after a `continue`. */
private[dedicated] trait BodyLoops extends BodyScope:

  protected def translateCondition(node: RastNode): String

  protected def translateStatementBody(node: RastNode, indent: String, isLast: Boolean): Unit

  protected def widenedNumber(name: String): Option[String]

  /** Emit a while or do-while loop with named boundary wrapping for break/continue. */
  protected def emitLoop(indent: String, cond: String, bodyNode: RastNode, isDoWhile: Boolean): Unit =
    val hasBreak      = containsBreak(bodyNode)
    val hasContinue   = containsContinue(bodyNode)
    val savedBreak    = breakLabel
    val savedContinue = continueLabel
    if hasBreak then
      val brkLbl = freshLabel("brk")
      breakLabel = brkLbl
      sb.append(s"${indent}scala.util.boundary { ($brkLbl: scala.util.boundary.Label[scala.Unit]) ?=>\n")
    val loopIndent = if hasBreak then indent + "  " else indent
    if hasContinue then
      val cntLbl = freshLabel("cnt")
      continueLabel = cntLbl
    if isDoWhile then
      if hasContinue then
        sb.append(s"${loopIndent}var $$doFirst = true\n")
        sb.append(
          s"${loopIndent}while ($$doFirst || $cond) scala.util.boundary { ($continueLabel: scala.util.boundary.Label[scala.Unit]) ?=>\n"
        )
        sb.append(s"$loopIndent  $$doFirst = false\n")
        translateStatementBody(bodyNode, loopIndent + "  ", false)
        sb.append(s"$loopIndent}\n")
      else
        sb.append(s"${loopIndent}var $$doFirst = true\n")
        sb.append(s"${loopIndent}while ($$doFirst || $cond) {\n")
        sb.append(s"$loopIndent  $$doFirst = false\n")
        translateStatementBody(bodyNode, loopIndent + "  ", false)
        sb.append(s"$loopIndent}\n")
    else if hasContinue then
      sb.append(s"${loopIndent}while ($cond) scala.util.boundary { ($continueLabel: scala.util.boundary.Label[scala.Unit]) ?=>\n")
      translateStatementBody(bodyNode, loopIndent + "  ", false)
      sb.append(s"$loopIndent}\n")
    else
      sb.append(s"${loopIndent}while ($cond) {\n")
      translateStatementBody(bodyNode, loopIndent + "  ", false)
      sb.append(s"$loopIndent}\n")
    if hasBreak then sb.append(s"$indent}\n")
    breakLabel = savedBreak
    continueLabel = savedContinue

  protected def translateForStatement(node: RastNode, indent: String): Unit =
    // ForStatement children: [init?, condition?, update?, body]
    // After R1 token filtering, children are semantic only
    val children = node.children
    // Find components by kind
    val init = children.find(c =>
      c.kind == "VariableDeclarationList" || c.kind == "FirstStatement" ||
        (c.kind == "BinaryExpression" && c.operator.contains("EqualsToken"))
    )
    val body = children.find(_.kind == "Block").orElse(children.lastOption)
    // Condition and update are harder — they're positioned between init and body
    val nonInitBody = children.filterNot(c => c == init.orNull || c == body.orNull)
    val cond        = nonInitBody.headOption
    val update      = nonInitBody.lift(1)

    // Emit as: { init; while (cond) { body; update } }
    val bodyNode      = body.getOrElse(RastNode("Block", 0, (0, 0)))
    val hasBreak      = containsBreak(bodyNode)
    val hasContinue   = containsContinue(bodyNode)
    val savedBreak    = breakLabel
    val savedContinue = continueLabel

    sb.append(s"$indent{\n")
    init.foreach { i =>
      if i.kind == "VariableDeclarationList" then
        val decls = i.children.filter(_.kind == "VariableDeclaration")
        for d <- decls do
          val name     = d.children.find(_.kind == "Identifier").flatMap(_.text).getOrElse("_i")
          val initNode = d.children.drop(1).find(c => !isTypeNode(c))
          // JS leaves it `undefined`, which no Scala number says
          if initNode.isEmpty then refuse("uninitialised-local")
          val initExpr = initNode.map(translateExpr).getOrElse("0")
          // a counter starting from an integer that a step makes fractional is a double, as every JS number is
          val widened = if initNode.exists(n => n.kind == "NumericLiteral" && exprType(n).contains("Int")) then widenedNumber(name) else None
          localNames += snakeToCamel(name)
          widened.orElse(initNode.flatMap(exprType)).foreach(t => localTypes(snakeToCamel(name)) = t)
          sb.append(s"$indent  var ${snakeToCamel(name)}${widened.fold("")(t => s": $t")} = $initExpr\n")
      else sb.append(s"$indent  ${withStatementRoot(unparenthesized(i))(translateExpr(i))}\n")
    }
    val condStr = cond.map(translateCondition).getOrElse("true")
    def updateText(u: RastNode): String = withStatementRoot(unparenthesized(u))(translateExpr(u))
    // For a C-style for loop, continue skips the rest of the body but update still runs.
    // The continue boundary wraps only the body; the update is emitted after it.
    val loopIndent = indent + "  "
    if hasBreak then
      val brkLbl = freshLabel("brk")
      breakLabel = brkLbl
      sb.append(s"${loopIndent}scala.util.boundary { ($brkLbl: scala.util.boundary.Label[scala.Unit]) ?=>\n")
    val whileIndent = if hasBreak then loopIndent + "  " else loopIndent
    if hasContinue then
      val cntLbl = freshLabel("cnt")
      continueLabel = cntLbl
      sb.append(s"${whileIndent}while ($condStr) {\n")
      sb.append(s"$whileIndent  scala.util.boundary { ($cntLbl: scala.util.boundary.Label[scala.Unit]) ?=>\n")
      translateStatementBody(bodyNode, whileIndent + "    ", false)
      sb.append(s"$whileIndent  }\n")
      update.foreach(u => sb.append(s"$whileIndent  ${updateText(u)}\n"))
      sb.append(s"$whileIndent}\n")
    else
      sb.append(s"${whileIndent}while ($condStr) {\n")
      translateStatementBody(bodyNode, whileIndent + "  ", false)
      update.foreach(u => sb.append(s"$whileIndent  ${updateText(u)}\n"))
      sb.append(s"$whileIndent}\n")
    if hasBreak then sb.append(s"$loopIndent}\n")
    breakLabel = savedBreak
    continueLabel = savedContinue
    sb.append(s"$indent}\n")
