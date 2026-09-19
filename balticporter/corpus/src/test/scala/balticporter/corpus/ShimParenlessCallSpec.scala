package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.CollectionsTransform

/** A call the retyping moved onto a runtime shim is spelled the way the shim declares the member. The shims carry java's arity everywhere but the members in `ShimParenless`, and emitting
  * `c.isEmpty()` against a parenless declaration is a typer error the port cannot see until the whole module compiles.
  */
class ShimParenlessCallSpec extends PortSuite:

  private def out(java: String): String = port(java, new CollectionsTransform).out

  test("the exception list is the runtime's, not a guess") {
    assertEquals(CollectionsTransform.ShimParenless, Set("isEmpty"))
  }

  test("isEmpty() on a parameter retyped to the collection shim loses its parens") {
    val o = out(
      """package demo;
        |import java.util.Collection;
        |class S {
        |  static boolean go(Collection<String> c) { return c.isEmpty(); }
        |}
        |""".stripMargin
    )
    assert(o.contains("balticporter.runtime.JavaCollection[java.lang.String]"), o)
    assert(o.contains("c.isEmpty\n"), o)
    assert(!o.contains("c.isEmpty()"), o)
  }

  test("a member this program DECLARES keeps java's parens — its own emitted arity decides") {
    val o = out(
      """package demo;
        |import java.util.Collection;
        |import java.util.Iterator;
        |abstract class Own<E> implements Collection<E> {
        |  public boolean isEmpty() { return size() == 0; }
        |  boolean ask(Own<E> other) { return other.isEmpty(); }
        |}
        |""".stripMargin
    )
    assert(o.contains("other.isEmpty()"), o)
  }

  test("size() is NOT on the list — the shim declares it with java's parens") {
    val o = out(
      """package demo;
        |import java.util.Collection;
        |class S {
        |  static int go(Collection<String> c) { return c.size(); }
        |}
        |""".stripMargin
    )
    assert(o.contains("c.size()"), o)
  }
