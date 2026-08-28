package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.Pipeline

/** java's multi-catch `catch (A | B e)` is a scala UNION TYPE in a typed pattern — and the
  * parentheses round it are a fact about scala's GRAMMAR, not about its types.
  *
  * `case e: A | B =>` parses the `|` as a PATTERN ALTERNATIVE, and a pattern alternative may not
  * bind a variable: `Illegal variable e in pattern alternative`. `case e: (A | B) =>` is the typed
  * pattern java meant, and `e`'s type is the union — which is at least as precise as java's, whose
  * multi-catch parameter has the LUB and therefore exactly the members common to both.
  *
  * The frontend has built the `OrType` since multi-catch was modelled (`JS-S14`) and the catalog has
  * carried the row as handled for as long, because nothing between the two rendered the parentheses.
  * That is the shape §3's `catalog(consulted)` cannot see: the lowering fired, the row is reached,
  * and the emitted text does not compile.
  *
  * The negative is what keeps the fix narrow: parenthesising every catch type would move emitted
  * text on every port for a construct that never needed it.
  */
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
