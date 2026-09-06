package balticporter.transform

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{OpaqueSpec, Pipeline, RuleScope, RunScope}
import balticporter.tir.PolicyBinder

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

  test("a formal in a unit this run does not emit is a seam, never a hub the flow crosses") {
    val hub  = """package com.base;
                 |public class Hub { public static float clamp (float v) { return v; } }
                 |""".stripMargin
    val demo = """package com.demo;
                 |class Timer { static float delta () { return 0.016f; } }
                 |class Use {
                 |  void feed () { com.base.Hub.clamp(Timer.delta()); }
                 |  void go (float y) { com.base.Hub.clamp(y); }
                 |}
                 |""".stripMargin
    val before = SpoonTir.fromSources(List("Hub.java" -> hub, "Demo.java" -> demo))
    val phase  = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "com.demo.Seconds", hints = Set("com.demo.Timer#delta"),
      underlying = OpaqueSpec.Primitive.Float, scope = RuleScope.Everywhere(Set.empty)))
    val theirs = before.units.map(_.symbol).filter(u => before.symbolOf(u).exists(_.fullName == "com.base.Hub")).toSet
    phase.bindPolicy(new PolicyBinder(before, before.members, RunScope.of(
      emitted = before.units.map(_.symbol).toSet -- theirs, own = Map.empty)))
    val after = phase.run(before)
    val out   = new TirEmitter(after).emit
    assert(out.contains("go(y: scala.Float)"), out)
    assert(out.contains("Hub.clamp(com.demo.Seconds.unwrap(com.demo.Timer.delta()))"), out)
  }

  test("a constant variable is never a seed: the call wraps the read, the `inline val` stays") {
    val java = """package com.demo;
                 |interface GL { int DEPTH = 2929; void enable (int cap); }
                 |class Use { void go (GL gl) { gl.enable(GL.DEPTH); } }
                 |""".stripMargin
    val before = SpoonTir.fromSource(java, "GL.java")
    val phase  = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "com.demo.Cap", hints = Set("com.demo.GL#enable#cap"),
      underlying = OpaqueSpec.Primitive.Int, scope = RuleScope.Everywhere(Set.empty)))
    val (after, log) = Pipeline.runTraced(before, List(phase))
    val out = new TirEmitter(after, notes = log).emit
    assert(out.contains("inline val DEPTH = 2929"), out)
    assert(out.contains("enable(cap: com.demo.Cap.T)"), out)
    assert(out.contains("gl.enable(com.demo.Cap(GL.DEPTH))") || out.contains("gl.enable(com.demo.Cap(com.demo.GL.DEPTH))"), out)
  }

  test("an enum case's constructor arguments are coerced against the retyped constructor") {
    val java = """package com.demo;
                 |interface GL { int POINTS = 0; int LINES = 1; void draw (int mode); }
                 |enum Shape {
                 |  Point(GL.POINTS), Line(GL.LINES);
                 |  final int glType;
                 |  Shape (int glType) { this.glType = glType; }
                 |  void go (GL gl) { gl.draw(glType); }
                 |}
                 |""".stripMargin
    val before = SpoonTir.fromSource(java, "GL.java")
    val phase  = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "com.demo.Mode", hints = Set("com.demo.GL#draw#mode"),
      underlying = OpaqueSpec.Primitive.Int, scope = RuleScope.Everywhere(Set.empty)))
    val (after, log) = Pipeline.runTraced(before, List(phase))
    val out = new TirEmitter(after, notes = log).emit
    assert(out.contains("com.demo.Mode(") && out.contains("POINTS)"), out)
    assert(!out.contains("Shape(com.demo.GL.POINTS)") && !out.contains("Shape(GL.POINTS)"), out)
  }
