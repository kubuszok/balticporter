package balticporter.frontend.spoon

import balticporter.tir.*

/** A parameter's `Symbol.fullName` is `Class#method#paramName`, derived from the METHOD's fullName
  * computed from its OWNER (whose symbol is already set at parameter-creation time). Before the fix,
  * it was `?#paramName` because `minter.fullNameOf(methodSymId)` returned `"?"` — the method's
  * symbol had not been `set` yet when its parameters were `define`d. */
class ParameterFullNameSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |class Util {
      |  static float factor(int align) { return 0.5f; }
      |  void process(String name, int count) {}
      |}
      |""".stripMargin

  private val program = SpoonTir.fromSource(src)

  test("a method parameter's fullName is Class#method#paramName, never ?#paramName") {
    val alignParam = program.symbols.all.find(s =>
      s.name == "align" && s.flags.isParam
    ).getOrElse(fail("no parameter named 'align' found"))

    assertEquals(alignParam.fullName, "demo.Util#factor#align",
      "parameter fullName must be derived from the method's fullName, not from an unset symbol")
    assert(!alignParam.fullName.startsWith("?"),
      s"parameter fullName must not start with '?' — got '${alignParam.fullName}'")
  }

  test("each parameter in a multi-param method has the correct fullName") {
    val nameParam = program.symbols.all.find(s =>
      s.name == "name" && s.flags.isParam &&
        program.symbols.get(s.owner).exists(_.fullName == "demo.Util#process")
    ).getOrElse(fail("no parameter 'name' on 'process'"))

    val countParam = program.symbols.all.find(s =>
      s.name == "count" && s.flags.isParam &&
        program.symbols.get(s.owner).exists(_.fullName == "demo.Util#process")
    ).getOrElse(fail("no parameter 'count' on 'process'"))

    assertEquals(nameParam.fullName, "demo.Util#process#name")
    assertEquals(countParam.fullName, "demo.Util#process#count")
  }
