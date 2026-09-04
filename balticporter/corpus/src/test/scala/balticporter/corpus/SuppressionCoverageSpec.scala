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
      |class Anon {
      |  Actor a;
      |  Runnable r() { return new Runnable() { public void run() { a = give(); } }; }
      |  @Null Actor give() { return null; }
      |}
      |class Local {
      |  Actor a;
      |  Local(int x, int y, int z) {}
      |  Local(@Null Actor p) { Actor q = p; a = q; }
      |}
      |class Base { Base(Actor a) {} }
      |class SuperArg extends Base {
      |  SuperArg(@Null Actor p) { super(p); }
      |}
      |class AnonCtor {
      |  Runnable r;
      |  AnonCtor(@Null Actor p) { r = new Runnable() { public void run() { Actor q = p; } }; }
      |}
      |class Jdk {
      |  String s(java.io.DataInputStream in) throws Exception { return in.readLine(); }
      |  Object l() { return new java.util.Locale("en", "US"); }
      |  int ok(String x) { return x.length(); }
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

  test("an `.orNull` in a SECONDARY constructor body annotates that constructor") {
    assertEmits(ported, "this.a = p.orNull")
    assertEmitsMatch(ported, """nowarn\("msg=deprecated"\)[^\n]*\n[^\n]*def this\(p: lowlevel""")
    assertNotEmits(ported, "nowarn(\"msg=deprecated\")\nprivate class Group")
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


  test("an `.orNull` only inside an ANONYMOUS class annotates the anon member, not the enclosing one") {
    assertEmitsMatch(ported, """nowarn\("msg=deprecated"\)[^\n]*\n[^\n]*def run\(\)""")
    assertNotEmits(ported, "nowarn(\"msg=deprecated\")\n  def r()")
  }

  test("a LOCAL `val` with `.orNull` in a secondary constructor annotates that constructor") {
    assertEmitsMatch(ported, """nowarn\("msg=deprecated"\)[^\n]*\n[^\n]*def this\(p: lowlevel\.Nullable\[demo\.Actor\]\) = \{\n\s*this\(\)\n\s*val q""")
  }

  test("an `.orNull` in the primary's SUPER arguments annotates the class") {
    assertEmitsMatch(ported, """nowarn\("msg=deprecated"\)[^\n]*\n[^\n]*class SuperArg""")
  }

  test("an `.orNull` only inside an anonymous class in a PROMOTED body annotates the anon member, not the class") {
    assertNotEmits(ported, "nowarn(\"msg=deprecated\")\nprivate class AnonCtor")
    assertNotEmits(ported, "nowarn(\"msg=deprecated\")\nclass AnonCtor")
  }


  test("a call to a class-file member annotated @Deprecated annotates the calling member") {
    assertEmitsMatch(ported, """nowarn\("msg=deprecated"\)[^\n]*\n[^\n]*def s\(in""")
    assertEmitsMatch(ported, """nowarn\("msg=deprecated"\)[^\n]*\n[^\n]*def l\(\)""")
    assertNotEmits(ported, "nowarn(\"msg=deprecated\")\n  private[demo] def ok(")
  }
