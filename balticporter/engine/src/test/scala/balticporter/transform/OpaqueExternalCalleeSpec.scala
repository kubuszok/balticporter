package balticporter.transform

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{OpaqueSpec, Pipeline, RuleScope}

/** An opaque value reaching an EXTERNAL callee is unwrapped: the class-file formal is java's
  * primitive (CLAUDE.md §4.56, K15), and the seam is counted. */
class OpaqueExternalCalleeSpec extends munit.FunSuite:
  private val java =
    """package com.demo;
      |class Timer {
      |  static float delta () { return 0.016f; }
      |  static float clamped () { return Math.min(delta(), 0.5f); }
      |}
      |interface Screen { void render (float delta); }
      |class Adapter implements Screen { public void render (float delta) {} }
      |class Game { void tick (Screen s) { s.render(Timer.delta()); } }
      |""".stripMargin

  test("an opaque argument at an external callee is unwrapped and the seam counted") {
    val phase = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "com.demo.Seconds", hints = Set("com.demo.Timer#delta"),
      underlying = OpaqueSpec.Primitive.Float, scope = RuleScope.Everywhere(Set.empty)))
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(java, "Demo.java"), List(phase))
    val out  = new TirEmitter(after, notes = log).emit
    val line = out.linesIterator.find(_.contains("Math.min(")).getOrElse("")
    assert(!line.contains("Math.min(Timer.delta(), 0.5f)"), line)
    assert(line.contains("Timer.delta()") && line.contains("0.5f"), line)
    assert(phase.boundary(after.units).exists(_.issue == OpaqueBoundaryCheck.Issue.ExternalCallee))
  }

  test("a retyped parameter moves with its whole override component — the implementer too") {
    val phase = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "com.demo.Seconds", hints = Set("com.demo.Timer#delta"),
      underlying = OpaqueSpec.Primitive.Float, scope = RuleScope.Everywhere(Set.empty)))
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(java, "Demo.java"), List(phase))
    val out = new TirEmitter(after, notes = log).emit
    val renders = out.linesIterator.filter(_.contains("def render(")).toList
    assert(renders.sizeIs == 2 && renders.forall(_.contains("delta: com.demo.Seconds")), renders.mkString("\n"))
  }
