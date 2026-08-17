package balticporter.corpus

import balticporter.testkit.PortSuite

/** `override` ACROSS A SUBSTITUTION — `ENGINE-LIMITS.md` K28.2.
  *
  * Java writes no `@Override` annotation for most of its overrides, so the frontend's `isOverride`
  * rests on the hierarchy: Spoon's own resolution first, and a SIGNATURE COMPARISON as the fallback.
  * That comparison was exact-string, and a generic superclass makes one member two strings —
  * `handle(N, …)` above, `handle(Item, …)` below — so wherever the parser's own resolution declined,
  * the fallback declined too and the member shipped with no modifier at all.
  *
  * Scala REQUIRES the modifier where the parent's member is concrete, and ``needs `override`
  * modifier`` is a `RefChecks` diagnostic: it does not run while any typer error stands (§3), so this
  * is invisible until a port reaches zero and then arrives in a member nobody was looking at.
  *
  * ==What this spec can and cannot pin==
  * The two answers are OR-ed, and on a fixture this size Spoon's own resolution succeeds — so the
  * positives below are a REGRESSION GUARD on the emitted modifier and not a measurement of the
  * fallback's own reach. The fallback's coverage is measured where it actually declines, on a port:
  * three rows and 48 member digests, `ENGINE-LIMITS.md` K28.2. Stating that here is §4.59's caution
  * read from the other side — a fixture only promotes a fact it can actually distinguish.
  *
  * The NEGATIVES are what this file guards, and they are the direction the change could break: a
  * signature that does NOT match after substitution is an ordinary java OVERLOAD, and `override` on
  * one is an error scalac reports (`overrides nothing`) and java has no opinion about.
  */
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
