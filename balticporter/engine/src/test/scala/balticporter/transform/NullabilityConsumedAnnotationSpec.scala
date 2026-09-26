package balticporter.transform

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{ Pipeline, RuleScope }

/** A nullability annotation the policy CONSUMES is never emitted, whether or not the phase retypes the member it sits on: an override of an external `Iterator#next` cannot take the wrapper, and the
  * annotation left on it names a class the port's build does not have.
  */
class NullabilityConsumedAnnotationSpec extends munit.FunSuite:
  private val javaSrc =
    """package com.demo;
      |import java.lang.annotation.*;
      |@Retention(RetentionPolicy.CLASS) @interface Null {}
      |class Chain<E> implements java.util.Iterator<E> {
      |  E head;
      |  public boolean hasNext() { return head != null; }
      |  @Null public E next() { E h = head; head = null; return h; }
      |  public @Null E peek() { return head; }
      |}
      |""".stripMargin

  private def emit(target: NullabilityTransform.Target, scope: RuleScope = RuleScope.Everywhere(Set.empty)): (String, List[NullabilityBoundaryCheck.Finding]) =
    val phase        = new NullabilityTransform(annotations = Set("com.demo.Null"), target = target, scope = scope)
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(javaSrc, "Chain.java"), List(phase))
    (new TirEmitter(after, notes = log).emit, phase.boundary(after.units))

  test("an override the wrapper cannot reach keeps its type, loses the annotation, and is counted") {
    val (out, fs) = emit(NullabilityTransform.Target.Named("demo.Nullable"))
    assert(!clue(out).contains("com.demo.Null"), out)
    assert(!out.contains("@Null"), out)
    assert(out.contains("demo.Nullable[E]"), "the member the phase CAN retype still is")
    assert(
      clue(fs).exists(f => f.subject.endsWith("Chain#next") && f.issue.toString == "OverrideCrossing"),
      fs.map(_.render).mkString("\n")
    )
  }

  test("a member the scope leaves out loses the annotation too") {
    val (out, _) = emit(NullabilityTransform.Target.Named("demo.Nullable"), RuleScope.Only(Set("com.other")))
    assert(!clue(out).contains("com.demo.Null"), out)
  }
