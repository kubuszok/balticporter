package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.GlobalsToImplicitsTransform

/** A class that constructs a given-bound class at SEVERAL of its own type parameters takes a witness for each of them: `Pair[K, V]` building `Bag[K]` and `Bag[V]` needs both. */
class RequiredGivensEveryParamSpec extends PortSuite:

  private val src =
    """package rg;
      |public class Bag<T> {
      |  public Bag(int n) {}
      |}
      |public class Pair<K, V> {
      |  Bag<K> keys = new Bag<K>(1);
      |  Bag<V> values = new Bag<V>(1);
      |}
      |public class Keys<K, V> {
      |  Bag<K> keys = new Bag<K>(1);
      |}
      |""".stripMargin

  private lazy val ported = port(src, new GlobalsToImplicitsTransform(requiredGivens = Map("rg.Bag" -> "demo.MkArray")))

  test("a construction at each of two type parameters threads a witness for each") {
    assertEmits(ported, "(using demo.MkArray[K], demo.MkArray[V])")
  }

  test("a construction at the first type parameter alone keeps the one witness") {
    val keys = ported.out.linesIterator.filter(_.contains("class Keys")).mkString("\n")
    assert(clue(keys).contains("(using demo.MkArray[K])"))
  }
