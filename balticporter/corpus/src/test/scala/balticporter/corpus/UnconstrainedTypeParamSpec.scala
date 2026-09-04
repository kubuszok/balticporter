package balticporter.corpus

import balticporter.testkit.PortSuite

/** A method TYPE PARAMETER that appears in NO FORMAL, at a call that gives it no target type —
  * `ENGINE-LIMITS.md` G22. */
class UnconstrainedTypeParamSpec extends PortSuite:

  test("a type parameter constrained ONLY by its bound is pinned to that bound at the call") {
    val p = port(
      """package demo;
        |import java.util.Map;
        |class Ctx {
        |  <T extends Map<String, ?>> T registry(String name) { return null; }
        |  boolean empty() { return registry("k").isEmpty(); }
        |}
        |""".stripMargin)
    assertEmits(p, "this.registry[java.util.Map[java.lang.String, ?]](\"k\").isEmpty()")
  }

  test("…and it is NOT pinned where a FORMAL mentions it — the argument constrains it, in both languages") {
    val p = port(
      """package demo;
        |import java.util.Map;
        |class Ctx2 {
        |  <T extends Map<String, ?>> T pick(T seed) { return seed; }
        |  boolean empty(Map<String, String> m) { return pick(m).isEmpty(); }
        |}
        |""".stripMargin)
    assertNotEmits(p, "this.pick[")
  }

  test("…nor where the call HAS a target type — pinning the bound would overwrite what java inferred") {
    val p = port(
      """package demo;
        |import java.util.Map;
        |import java.util.HashMap;
        |class Ctx3 {
        |  <T extends Map<String, ?>> T registry(String name) { return null; }
        |  Map<String, Integer> counts() { Map<String, Integer> m = registry("k"); return m; }
        |}
        |""".stripMargin)
    assertNotEmits(p, "this.registry[")
  }

  test("…nor where the bound is VACUOUS — `T extends Object` is G24's territory and has no member") {
    val p = port(
      """package demo;
        |class Ctx4 {
        |  <T> T any(String name) { return null; }
        |  String show() { return any("k").toString(); }
        |}
        |""".stripMargin)
    assertNotEmits(p, "this.any[")
  }
