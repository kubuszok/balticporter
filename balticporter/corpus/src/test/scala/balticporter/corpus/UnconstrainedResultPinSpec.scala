package balticporter.corpus

import balticporter.testkit.PortSuite

/** `ENGINE-LIMITS.md` G8.7 — G22's pin at the shape its FOURTH condition declines. */
class UnconstrainedResultPinSpec extends PortSuite:

  test("an F-BOUNDED result variable is ascribed at its bound, own variables wildcarded") {
    val p = port(
      """package demo;
        |interface Builder<B extends Builder<B, S>, S extends CharSequence> {
        |  B add(CharSequence c);
        |  S toSequence();
        |}
        |abstract class Seq<T extends CharSequence> {
        |  abstract <B extends Builder<B, T>> B getBuilder();
        |  T twice(CharSequence c) { return getBuilder().add(c).add(c).toSequence(); }
        |}
        |""".stripMargin)
    // `B` is the METHOD's own and becomes `?`; `T` is the enclosing class's and is written.
    assertEmits(p, "this.getBuilder().asInstanceOf[demo.Builder[?, T]].add(c).add(c).toSequence()")
  }

  test("…and the DECLARING type's variable is resolved through the RECEIVER's instantiation") {
    val p = port(
      """package demo;
        |interface Builder2<B extends Builder2<B, S>, S extends CharSequence> {
        |  B add(CharSequence c);
        |  S toSequence();
        |}
        |interface Rich<T extends CharSequence> {
        |  <B extends Builder2<B, T>> B getBuilder();
        |}
        |abstract class RichBase<U extends CharSequence> implements Rich<U> {
        |  U twice(CharSequence c) { return getBuilder().add(c).add(c).toSequence(); }
        |}
        |""".stripMargin)
    // `Rich`'s `T` and `RichBase`'s `U` are DIFFERENT declarations, so "is this literally the
    // variable in scope?" answers no — and the receiver's `Rich<U>` says `T := U` exactly.
    assertEmits(p, "asInstanceOf[demo.Builder2[?, U]]")
  }

  test("NEGATIVE: a bound with NO named variable stays G22's TYPE ARGUMENT — one seam, one pin") {
    val p = port(
      """package demo;
        |import java.util.Map;
        |class Ctx {
        |  <T extends Map<String, ?>> T getRegistry(String n) { return null; }
        |  boolean empty() { return getRegistry("x").isEmpty(); }
        |}
        |""".stripMargin)
    assertEmits(p, "getRegistry[java.util.Map[java.lang.String, ?]]")
    assertNotEmits(p, "getRegistry(\"x\").asInstanceOf[")
  }

  test("NEGATIVE: a call that is NOT a receiver has a target type and is left alone") {
    val p = port(
      """package demo;
        |interface Builder3<B extends Builder3<B, S>, S extends CharSequence> { S toSequence(); }
        |abstract class Seq3<T extends CharSequence> {
        |  abstract <B extends Builder3<B, T>> B getBuilder();
        |  Object keep() { Object o = getBuilder(); return o; }
        |}
        |""".stripMargin)
    // an assignment gives java AND scala a target, so neither language is guessing.
    assertNotEmits(p, "asInstanceOf[demo.Builder3[")
  }

  test("NEGATIVE: a FORMAL mentioning the variable constrains it in both languages") {
    val p = port(
      """package demo;
        |interface Builder4<B extends Builder4<B, S>, S extends CharSequence> { S toSequence(); }
        |abstract class Seq4<T extends CharSequence> {
        |  abstract <B extends Builder4<B, T>> B reuse(B b);
        |  T twice(Builder4 b) { return reuse(b).toSequence(); }
        |}
        |""".stripMargin)
    assertNotEmits(p, "reuse(b).asInstanceOf[demo.Builder4[")
  }

  test("NEGATIVE: a bound naming a variable only the CALLEE'S OWNER declares has no honest text") {
    val p = port(
      """package demo;
        |interface Builder5<B extends Builder5<B, S>, S extends CharSequence> { S toSequence(); }
        |interface Rich5<T extends CharSequence> { <B extends Builder5<B, T>> B getBuilder(); }
        |class Use5 {
        |  CharSequence go(Rich5 raw) { return raw.getBuilder().toSequence(); }
        |}
        |""".stripMargin)
    // a RAW receiver says nothing about `T`, so `receiverTypeArgs` is empty and the pin declines —
    // the call keeps its error rather than gaining a type this scope cannot write (§4.6).
    assertNotEmits(p, "asInstanceOf[demo.Builder5[")
  }
