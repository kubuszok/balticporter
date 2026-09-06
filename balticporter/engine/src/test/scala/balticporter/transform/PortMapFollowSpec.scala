package balticporter.transform

import balticporter.core.PortMap
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir

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
