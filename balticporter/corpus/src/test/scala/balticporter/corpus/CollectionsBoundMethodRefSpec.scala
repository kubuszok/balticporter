package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.CollectionsTransform

/** A BOUND METHOD REFERENCE AT A REWRITTEN MEMBER — `ENGINE-LIMITS.md` K23's named row, built. */
class CollectionsBoundMethodRefSpec extends PortSuite:

  test("a BOUND reference at a rewritten map member lowers to the lambda, receiver bound once") {
    val p = port(
      """package demo;
        |import java.util.*;
        |import java.util.function.Function;
        |class Uses {
        |  void go(HashMap<String, String> map) { apply(map::get); }
        |  void apply(Function<String, String> f) { }
        |}
        |""".stripMargin, new CollectionsTransform)
    // scala's `Map.get` answers an `Option`; java's answers the value or `null`, which is the
    // rewrite the phase already performs at a CALL — now performed at the REFERENCE too.
    assertEmits(p, "val recv$")
    assertEmits(p, "(a0$) => recv$.getOrElse(a0$, null.asInstanceOf[java.lang.String])")
    // …and NOT the bare eta-expansion, which is what the port emitted before.
    assertNotEmits(p, "apply(map.get)")
  }

  test("a two-argument member takes TWO parameters — the arity is java's, off the node") {
    val p = port(
      """package demo;
        |import java.util.*;
        |import java.util.function.BiFunction;
        |class Uses {
        |  void go(HashMap<String, String> map) { apply(map::put); }
        |  void apply(BiFunction<String, String, String> f) { }
        |}
        |""".stripMargin, new CollectionsTransform)
    assertEmits(p, "a0$")
    assertEmits(p, "a1$")
  }

  test("a `this`-qualified receiver needs NO binding — it is not a variable") {
    val p = port(
      """package demo;
        |import java.util.*;
        |import java.util.function.Function;
        |class Holder extends HashMap<String, String> {
        |  void go() { apply(this::get); }
        |  void apply(Function<String, String> f) { }
        |}
        |""".stripMargin, new CollectionsTransform)
    // a `val recv$ = this` would be emitted text for nothing: no assignment can move `this`, so the
    // per-invocation reading and the at-creation reading are the same program.
    assertNotEmits(p, "val recv$ = this")
  }

  test("NEGATIVE — a receiver this phase did NOT retype keeps its bare eta-expansion") {
    val p = port(
      """package demo;
        |import java.util.function.Function;
        |class Table { String get(String k) { return k; } }
        |class Uses {
        |  void go(Table t) { apply(t::get); }
        |  void apply(Function<String, String> f) { }
        |}
        |""".stripMargin, new CollectionsTransform)
    // `kindOf` is the phase's OWN record of what it moved (§4.56). A receiver it never touched has
    // the member java gave it, so there is nothing to lower and lowering would be pure churn.
    assertEmits(p, "apply(t.get)")
    assertNotEmits(p, "val recv$")
  }

  test("NEGATIVE — a retyped receiver at a member with NO rewrite is left alone") {
    val p = port(
      """package demo;
        |import java.util.*;
        |import java.util.function.Supplier;
        |class Uses {
        |  void go(ArrayList<String> xs) { apply(xs::hashCode); }
        |  void apply(Supplier<Integer> f) { }
        |}
        |""".stripMargin, new CollectionsTransform)
    // `rewrite` answering `None` is the second gate and it is the one that keeps this from wrapping
    // every bound reference on every retyped receiver in a lambda for no difference at all.
    assertNotEmits(p, "val recv$")
  }
