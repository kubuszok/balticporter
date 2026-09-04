package balticporter.corpus

import balticporter.testkit.PortSuite

/** `override` ACROSS A SUBSTITUTION — `ENGINE-LIMITS.md` K28.2. */
class SubstitutedOverrideSpec extends PortSuite:

  private val files = List(
    "Item.java"    -> "package demo.model; public class Item { }",
    "Action.java"  -> "package demo.model; public class Action<N> { }",
    "Handler.java" -> "package demo.model; public class Handler<N, A> { }",
    "Sink.java"    -> "package demo.model; public class Sink extends Action<Item> { }",
    "Hook.java"    -> "package demo.model; public class Hook extends Handler<Item, Sink> { }",
    "Base.java" ->
      """package demo.base;
        |import java.util.function.BiConsumer;
        |public abstract class Base<C extends Base<C, N, A, H>, N, A extends demo.model.Action<N>,
        |                           H extends demo.model.Handler<N, A>> {
        |  protected void handle(N node, boolean deep, BiConsumer<N, A> f) { }
        |}
        |""".stripMargin,
    "Mid.java" ->
      """package demo.leaf;
        |public class Mid extends demo.base.Base<Mid, demo.model.Item, demo.model.Sink, demo.model.Hook> { }
        |""".stripMargin,
    "Leaf.java" ->
      """package demo.leaf;
        |import java.util.function.BiConsumer;
        |public class Leaf extends Mid {
        |  public void handle(demo.model.Item node, boolean deep,
        |                     BiConsumer<demo.model.Item, demo.model.Sink> f) { }
        |  public void handle(String s) { }
        |  public void handle(String s, boolean deep, BiConsumer<String, demo.model.Sink> f) { }
        |}
        |""".stripMargin,
    "Uses.java" ->
      """package demo.leaf;
        |import java.util.function.BiConsumer;
        |public class Uses {
        |  Mid make() {
        |    return new Mid() {
        |      public void handle(demo.model.Item node, boolean deep,
        |                         BiConsumer<demo.model.Item, demo.model.Sink> f) { }
        |    };
        |  }
        |}
        |""".stripMargin,
  )

  private val p = portAll(files)

  test("a member whose parameter is the SUPERCLASS's type variable, substituted, carries `override`") {
    assertEmits(p, "override def handle(node: demo.model.Item, deep: scala.Boolean")
  }

  test("an ANONYMOUS body gets the same answer — the same question at a different node") {
    // two occurrences: `Leaf`'s and the anonymous `Mid`'s. `parentClash`-style walks aside, an
    // anonymous class's members go through the very same `overridesInherited`.
    assertEquals(
      java.util.regex.Pattern.quote("override def handle(node: demo.model.Item").r.findAllIn(p.out).size,
      2, clue(p.out))
  }

  test("NEGATIVE — a same-name OVERLOAD at another arity overrides nothing") {
    assertNotEmits(p, "override def handle(s: java.lang.String)")
    assertEmits(p, "def handle(s: java.lang.String)")
  }

  test("NEGATIVE — a same-ARITY overload whose substituted parameter differs overrides nothing") {
    assertNotEmits(p, "override def handle(s: java.lang.String, deep:")
    assertEmits(p, "def handle(s: java.lang.String, deep:")
  }
