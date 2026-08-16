package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.CollectionsTransform

/** A BOUND METHOD REFERENCE AT A REWRITTEN MEMBER — `ENGINE-LIMITS.md` K23's named row, built.
  *
  * K23 recorded this rather than closing it, on a count: *"measured at one site in the corpus"*. Its
  * comment also carried the reason the row had been invisible — `lowerMethodRef` lowers the UNBOUND
  * form and says a bound one *"is the `Apply` case one node out"*, which is true of a CALL and false
  * of a REFERENCE. `map::get` emits as an eta-expanded `map.get`, a `Tree.Select` that no
  * `Apply`-keyed arm ever sees, so `Map.get`'s `getOrElse(null)` rewrite never happened and the
  * reference handed a `String => Option[V]` to a slot that wanted java's `V`.
  *
  * ==the receiver is bound ONCE, and that is not tidiness==
  * Java evaluates `expr` when the reference is CREATED and never again (JLS 15.13.3); a lambda
  * `(a0$) => expr.m(a0$)` evaluates it per INVOCATION. For a field read or a call that is a
  * different program, and it is a `CLAUDE.md` §4.4-shaped difference — valid scala meaning something
  * else, with no compile error and no moved count to report it. So the lowering is
  * `{ val recv$ = expr; (a0$) => … }`, which is java's own evaluation order written down.
  * `Tree.This` skips the binding because it is not a variable and no assignment can move it.
  *
  * ==the arity is JAVA'S, off the node==
  * `Tree.MethodRef.referent` — `G27`'s field, and the same one the emitter's own expansion reads.
  * Never off the symbol: an external member is interned with no `MethodType` at all and would read
  * as taking no arguments, which is §4.6's fabricated fact with the default baked into the data.
  */
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
