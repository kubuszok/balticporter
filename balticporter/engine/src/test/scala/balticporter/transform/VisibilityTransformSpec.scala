package balticporter.transform

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

/** `VisibilityTransform`: a listed member ships public where java declared it narrower (DESIGN.md §8.30). */
class VisibilityTransformSpec extends munit.FunSuite:
  private val javaSrc =
    """package com.demo;
      |public class A {
      |  protected A (int x) { }
      |  protected void m () { }
      |  protected void keep () { }
      |}
      |""".stripMargin

  test("the listed constructor and method lose their `protected`; an unlisted one keeps it") {
    val phase = new VisibilityTransform(widen = Set("com.demo.A#<init>", "com.demo.A#m"))
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(javaSrc, "A.java"), List(phase))
    val out = new TirEmitter(after, notes = log).emit
    assert(clue(out).contains("class A(x$p: scala.Int)"), out)
    assert(out.linesIterator.exists(l => l.trim.startsWith("def m()")), out)
    assert(out.linesIterator.exists(l => l.contains("protected") && l.contains("def keep()")), out)
    assert(phase.policyReport.findings.isEmpty, phase.policyReport.findings.mkString("\n"))
  }

  test("the fingerprint is the switch and the members, empty when neither is set") {
    assertEquals(new VisibilityTransform().surfaceFingerprint, "")
    assert(new VisibilityTransform(derive = true).surfaceFingerprint.contains("derive=reference"))
  }
