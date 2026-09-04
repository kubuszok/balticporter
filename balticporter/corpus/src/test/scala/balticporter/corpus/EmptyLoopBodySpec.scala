package balticporter.corpus

import balticporter.testkit.PortSuite

/** A java loop with an EMPTY body lowers without a `()` statement (scalac E129 under `-Werror`). */
class EmptyLoopBodySpec extends PortSuite:

  private val java =
    """package demo;
      |class T {
      |  int m(int n) {
      |    int i;
      |    for (i = 0; i < n; i++) ;
      |    while (i > 0) i--;
      |    for (int j = 0; j < n; j++) {}
      |    return i;
      |  }
      |}
      |""".stripMargin

  private lazy val ported = port(java)

  test("no bare `()` statement is emitted for an empty loop body") {
    assertEmits(ported, "while (i < n) { i = i + 1 }")
    assert(!clue(ported.out).linesIterator.exists(_.trim == "()"), "a bare `()` statement")
  }
