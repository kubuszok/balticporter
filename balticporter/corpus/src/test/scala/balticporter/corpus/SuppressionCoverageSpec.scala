package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.{NullabilityTransform, SuppressionPhase}
import balticporter.transform.NullabilityTransform.Target

/** `SuppressionPhase` covers every body position the emitter can place an `.orNull` in — a
  * constructor body renders as `def this` without annotations, so the CLASS carries the `@nowarn`. */
class SuppressionCoverageSpec extends PortSuite:

  private val W = "lowlevel.Nullable"

  private val java =
    """package demo;
      |import java.lang.annotation.*;
      |@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
      |@interface Null {}
      |class Actor {}
      |class Group {
      |  Actor a;
      |  Group(int x, int y, int z) {}
      |  Group(@Null Actor p) { a = p; }
      |}
      |class Direct {
      |  Actor a;
      |  Direct(@Null Actor p) { a = p; }
      |}
      |enum Kind {
      |  A { Actor pick(@Null Actor p) { Actor q = p; return q; } };
      |  Actor pick(@Null Actor p) { return null; }
      |}
      |class Method {
      |  Actor a;
      |  @Null Actor give() { return null; }
      |  void use() { a = give(); }
      |}
      |""".stripMargin

  private lazy val ported =
    port(java, new NullabilityTransform(Set("demo.Null"), Target.Named(W)), new SuppressionPhase)

  private val nowarn = "@scala.annotation.nowarn(\"msg=deprecated\")"

  test("an `.orNull` in a SECONDARY constructor body annotates the class") {
    assertEmits(ported, "this.a = p.orNull")
    assertEmitsMatch(ported, """(?s)nowarn\("msg=deprecated"\)[^\n]*\n\s*(private |final |open )*class Group""")
  }

  test("an `.orNull` in a PROMOTED constructor body annotates the class") {
    assertEmitsMatch(ported, """(?s)nowarn\("msg=deprecated"\)[^\n]*\n\s*(private |final |open )*class Direct""")
  }

  test("an `.orNull` in a method body annotates the method, not the class") {
    assertEmitsMatch(ported, """nowarn\("msg=deprecated"\)[^\n]*\n[^\n]*def use\(\)""")
    assertNotEmits(ported, "nowarn(\"msg=deprecated\")\nprivate class Method")
  }

  test("an `.orNull` in an ENUM CONSTANT body annotates that member") {
    assertEmitsMatch(ported, """nowarn\("msg=deprecated"\)[^\n]*\n[^\n]*def pick\(p: lowlevel""")
  }
