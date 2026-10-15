package balticporter.frontend.spoon

import scala.jdk.CollectionConverters.*

import spoon.reflect.code.*
import spoon.reflect.declaration.{ CtElement, CtExecutable, CtType }
import spoon.reflect.visitor.filter.TypeFilter

/** JLS 14.22's "can complete normally" over java statements — what decides whether a colon-form switch case falls through into the next one. Where a rule is not modelled the answer is TRUE: a wrong
  * "cannot" deletes code java runs on into, a wrong "can" only duplicates code that is never reached.
  */
private[spoon] object SpoonCompletion:

  /** can this statement sequence complete normally? Comments are not statements; an empty sequence completes. */
  def seqCompletes(stmts: List[CtStatement]): Boolean =
    stmts.filterNot(_.isInstanceOf[CtComment]).lastOption.forall(canCompleteNormally)

  def canCompleteNormally(s: CtStatement): Boolean =
    if s == null then true
    else
      val own = s match
        case _: CtReturn[?] | _: CtThrow | _: CtYieldStatement | _: CtBreak | _: CtContinue => false
        case b: CtBlock[?] => seqCompletes(b.getStatements.asScala.toList)
        case i: CtIf       =>
          i.getElseStatement == null || canCompleteNormally(i.getThenStatement) || canCompleteNormally(i.getElseStatement)
        case w:  CtWhile        => !constantTrue(w.getLoopingExpression)
        case d:  CtDo           => !constantTrue(d.getLoopingExpression)
        case f:  CtFor          => f.getExpression != null && !constantTrue(f.getExpression)
        case sy: CtSynchronized => canCompleteNormally(sy.getBlock)
        case t:  CtTry          =>
          (canCompleteNormally(t.getBody) || t.getCatchers.asScala.exists(c => canCompleteNormally(c.getBody))) &&
          (t.getFinalizer == null || canCompleteNormally(t.getFinalizer))
        case sw: CtSwitch[?] => switchCompletes(sw)
        case _ => true
      own || breaksTo(s)

  /** a switch STATEMENT completes unless it has a default, every way out of its block is abrupt, and nothing breaks out of it. */
  private def switchCompletes(sw: CtSwitch[?]): Boolean =
    val cases = sw.getCases.asScala.toList
    def stmts(c: CtCase[?]): List[CtStatement] = c.getStatements.asScala.toList
    val hasDefault = cases.exists(c => c.getCaseExpressions.isEmpty || c.getIncludesDefault)
    if !hasDefault || cases.isEmpty then true
    else if cases.exists(_.getCaseKind == CaseKind.ARROW) then
      // an arrow arm that is an EXPRESSION (Spoon wraps it in a yield) completes
      cases.exists(c =>
        stmts(c) match
          case List(_: CtYieldStatement) => true
          case l                         => seqCompletes(l)
      )
    else seqCompletes(stmts(cases.last))

  /** is there a `break` whose target is `s` itself — the labelled statement it names, or the nearest loop or switch for an unlabelled one? */
  private def breaksTo(s: CtStatement): Boolean =
    s.getElements(new TypeFilter(classOf[CtBreak])).asScala.exists(b => targetOf(b).exists(_ eq s))

  private def targetOf(b: CtBreak): Option[CtElement] =
    val label = b.getTargetLabel
    var cur:   CtElement         = b.getParent
    var found: Option[CtElement] = None
    while found.isEmpty && cur != null && !cur.isInstanceOf[CtExecutable[?]] && !cur.isInstanceOf[CtType[?]] do
      cur match
        case st: CtStatement if label != null && label == st.getLabel => found = Some(st)
        case st @ (_: CtLoop | _: CtSwitch[?]) if label == null => found = Some(st)
        case _                                                  => ()
      cur = if cur.isParentInitialized then cur.getParent else null
    found

  private def constantTrue(e: CtExpression[?]): Boolean = e match
    case l: CtLiteral[?] => l.getValue == java.lang.Boolean.TRUE
    case _ => false
