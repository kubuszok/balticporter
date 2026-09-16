package balticporter.transform

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{ Pipeline, RuleScope }

/** A java BOXED primitive at a nullable slot is boxed only to admit `null`; under a wrapper that carries the absence the element is the primitive again (`Integer` → `Nullable[Int]`), the
  * reference's own spelling. The union target keeps the box.
  */
class NullabilityBoxedSpec extends munit.FunSuite:
  private val javaSrc =
    """package com.demo;
      |import java.lang.annotation.*;
      |@Retention(RetentionPolicy.CLASS) @interface Null {}
      |class Cell {
      |  @Null Integer align;
      |  @Null Float fill;
      |  public @Null Integer getAlign() { return align; }
      |  public void setAlign(@Null Integer a) { align = a; }
      |  public int raw() { return align; }
      |}
      |""".stripMargin

  private def emit(target: NullabilityTransform.Target): String =
    val phase        = new NullabilityTransform(annotations = Set("com.demo.Null"), target = target, scope = RuleScope.Everywhere(Set.empty))
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(javaSrc, "Cell.java"), List(phase))
    new TirEmitter(after, notes = log).emit

  test("a boxed primitive under the named wrapper is the primitive") {
    val out = emit(NullabilityTransform.Target.Named("demo.Nullable"))
    assert(clue(out).contains("demo.Nullable[scala.Int]"))
    assert(clue(out).contains("demo.Nullable[scala.Float]"))
    assert(!out.contains("demo.Nullable[java.lang."))
    // the primitive read still coerces off the wrapper
    assert(clue(out).contains(".get"))
  }

  test("the union target keeps the box — `Int | Null` is no reference type") {
    val out = emit(NullabilityTransform.Target.Union)
    assert(clue(out).contains("java.lang.Integer | Null") || out.contains("java.lang.Integer | scala.Null"))
  }
