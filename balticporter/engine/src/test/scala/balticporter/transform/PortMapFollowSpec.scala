package balticporter.transform

import balticporter.core.PortMap
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Pipeline, RuleScope}

/** [[PortMapTransform]]'s follow of a base's PUBLISHED member spelling where only the FORM moved
  * (`first()` -> `first`): no name to rename, still a call site to rewrite (ENGINE-LIMITS K51). */
class PortMapFollowSpec extends munit.FunSuite:

  test("a form-only entry strips `()` at the dependent's call sites without any name change") {
    val base = """package com.base;
                 |public class Box { public int width () { return 1; } }
                 |""".stripMargin
    val demo = """package com.demo;
                 |class Use { int go (com.base.Box b) { return b.width(); } }
                 |""".stripMargin
    val before = SpoonTir.fromSources(List("Box.java" -> base, "Use.java" -> demo))
    val map = PortMap.Map0("base", engine = "test", entries = List(
      PortMap.Entry("member", "com.base.Box#width()", "com.base.Box#width", PortMap.Disposition.Renamed,
        shape = "form=parenless")))
    val after = new PortMapTransform(List(map)).run(before)
    val out   = new TirEmitter(after).emit
    assert(out.contains("b.width\n") || out.contains("b.width "), out)
    assert(!out.contains("b.width()"), out)
  }

  test("a field and a method sharing a name: the method row follows the method, the field stays") {
    val base = """package com.base;
                 |public class Node { public Node parent; public Node parent () { return parent; } }
                 |""".stripMargin
    val demo = """package com.demo;
                 |class Use { Object go (com.base.Node n) { return n.parent(); } }
                 |""".stripMargin
    val before = SpoonTir.fromSources(List("Node.java" -> base, "Use.java" -> demo))
    val map = PortMap.Map0("base", engine = "test", entries = List(
      PortMap.Entry("member", "com.base.Node#parent", "com.base.Node#parent$field", PortMap.Disposition.Renamed,
        shape = "name=parent$field"),
      PortMap.Entry("member", "com.base.Node#parent()", "com.base.Node#parent", PortMap.Disposition.Renamed,
        shape = "form=parenless")))
    val out = new TirEmitter(new PortMapTransform(List(map)).run(before)).emit
    assert(out.contains("n.parent\n") || out.contains("n.parent "), out)
    assert(!out.contains("n.parent()") && !out.contains("n.parent$field"), out)
  }

  test("a renamed AND parenless entry (`isLeaf()` -> `leaf`) strips `()` at the call too") {
    val base = """package com.base;
                 |public class Node { public boolean isLeaf () { return true; } }
                 |""".stripMargin
    val demo = """package com.demo;
                 |class Use { boolean go (com.base.Node n) { return n.isLeaf(); } }
                 |""".stripMargin
    val before = SpoonTir.fromSources(List("Node.java" -> base, "Use.java" -> demo))
    val map = PortMap.Map0("base", engine = "test", entries = List(
      PortMap.Entry("member", "com.base.Node#isLeaf()", "com.base.Node#leaf", PortMap.Disposition.Renamed,
        shape = "form=parenless")))
    val out = new TirEmitter(new PortMapTransform(List(map)).run(before)).emit
    assert(out.contains("n.leaf\n") || out.contains("n.leaf "), out)
    assert(!out.contains("n.leaf()"), out)
  }

  test("a detected getter is PUBLISHED parenless — the shape a dependent's follow keys on") {
    val java = """package com.demo;
                 |class Node { private boolean leaf; public boolean isLeaf () { return leaf; } }
                 |""".stripMargin
    val before = SpoonTir.fromSource(java, "Node.java")
    val phase  = new BeanPropertyTransform(Map.empty, Map.empty, scope = RuleScope.Everywhere())
    val (after, log) = Pipeline.runTraced(before, List(phase))
    val emitter = new TirEmitter(after, notes = log)
    val out = emitter.emit
    assert(out.contains("def leaf: scala.Boolean"), out)
    val shapes = emitter.emittedShapes.renderedMembers
    val row = shapes.collectFirst { case (k, v) if k.endsWith("Node#leaf()") => v }
    assert(row.exists(_.contains("form=parenless")), shapes.toString)
  }
