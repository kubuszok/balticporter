package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.UnusedSymbolTransform

/** `UnusedSymbolTransform` reaches an unused local wherever java can declare one — a constructor
  * body (promoted or not), a static method, an instance method: pure initialisers are deleted,
  * effectful ones kept as bare statements. */
class UnusedLocalCoverageSpec extends PortSuite:

  private val java =
    """package demo;
      |class C {
      |  int x;
      |  static int compute(int a) { return a + 1; }
      |  C(int x) { int unusedEffect = compute(x); int unusedPure = x * 2; this.x = x; }
      |  C() { this(0); int alsoUnused = compute(3); }
      |  int m(int t) { int dt = 1 - t; return t; }
      |  static int s(int t) { int t3 = t * t; return t; }
      |  Runnable r() { return new Runnable() { public void run() {} void dead(int q) {} }; }
      |  Object carrier() { return new Object() { private String name = "tobi"; private int n = 3; }; }
      |}
      |""".stripMargin

  private lazy val ported = port(java, new UnusedSymbolTransform)

  test("a pure unused local is deleted in a constructor, a method and a static method") {
    assertNotEmits(ported, "unusedPure")
    assertNotEmits(ported, "val dt")
    assertNotEmits(ported, "val t3")
  }

  test("an effectful unused local keeps its initialiser as a bare statement") {
    assertNotEmits(ported, "val unusedEffect")
    assertNotEmits(ported, "val alsoUnused")
    assertEmits(ported, "C.compute(x$p)")
    assertEmits(ported, "C.compute(3)")
  }

  test("a non-override method of an anonymous class is KEPT (it may implement a default method) and suppressed") {
    assertEmitsMatch(ported, """nowarn\("msg=unused"\)[^\n]*\n[^\n]*def dead\(""")
  }

  test("an anonymous class's private field is state for its consumer (reflection, K21): kept, suppressed") {
    assertEmits(ported, "name: java.lang.String = \"tobi\"")
    assertEmits(ported, "n: scala.Int = 3")
  }
