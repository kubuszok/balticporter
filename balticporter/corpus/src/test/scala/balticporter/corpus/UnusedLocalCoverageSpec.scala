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

  test("a non-override method of an anonymous class that nothing calls is dead code, deleted") {
    assertNotEmits(ported, "def dead")
    assertEmits(ported, "def run()")
  }
