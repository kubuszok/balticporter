package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.Pipeline

/** java's multi-catch `catch (A | B e)` is a scala UNION TYPE in a typed pattern — and the
  * parentheses round it are a fact about scala's GRAMMAR, not about its types. */
class MultiCatchUnionSpec extends PortSuite:

  private val src =
    """package mc;
      |import java.io.IOException;
      |class Reader {
      |  int read(String s) {
      |    try {
      |      return Integer.parseInt(s);
      |    } catch (NumberFormatException | IndexOutOfBoundsException e) {
      |      return -1;
      |    }
      |  }
      |  void single(String s) {
      |    try { throw new IOException(s); } catch (IOException e) { e.printStackTrace(); }
      |  }
      |}
      |""".stripMargin

  private val out = new TirEmitter(Pipeline.run(SpoonTir.fromSource(src), Nil)).emit

  test("a multi-catch renders a PARENTHESISED union in the typed pattern") {
    assert(clue(out).contains(
      "case _: (java.lang.NumberFormatException | java.lang.IndexOutOfBoundsException) =>"))
    assert(!out.contains("case _: java.lang.NumberFormatException | "))
  }

  test("NEGATIVE — a single-type catch is unparenthesised, exactly as it always was") {
    assert(clue(out).contains("case e: java.io.IOException =>"))
    assert(!out.contains("case e: (java.io.IOException)"))
  }
