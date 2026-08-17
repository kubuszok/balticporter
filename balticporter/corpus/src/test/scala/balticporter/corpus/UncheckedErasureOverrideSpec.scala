package balticporter.corpus

import balticporter.testkit.PortSuite

/** `ENGINE-LIMITS.md` G8.10 — java's UNCHECKED override, where an F-BOUNDED result-only method type
  * parameter is erased at the DECLARATION.
  *
  * JLS 8.4.2 makes a signature a SUBSIGNATURE of one whose ERASURE it is, so java lets
  * `SequenceBuilder getBuilder()` override `<B extends ISequenceBuilder<B,T>> B getBuilder()` with
  * an unchecked warning. Scala has no such rule, so the pair is `E038 has a different signature` at
  * the narrowing declaration and `needs to be abstract` at every concrete class below it.
  *
  * G8 measured four ways of INSTANTIATING such a parameter and every one was worse, for a reason it
  * measured rather than assumed: no denotable `X` satisfies `X <: ISequenceBuilder<X, T>`. So the
  * parameter is UNWRITABLE — no caller can supply an argument and no implementation can produce one
  * without a cast — and its bound, self-reference wildcarded, is the only text there is.
  *
  * The three negatives are what keep this from erasing ordinary generic java: a variable a FORMAL
  * mentions is constrained by its argument, a variable with an ORDINARY bound has denotable
  * instantiations callers really write, and a variable the RESULT does not mention changes no
  * emitted type.
  */
class UncheckedErasureOverrideSpec extends PortSuite:

  test("an F-BOUNDED, RESULT-ONLY method type parameter is erased to its bound at the DECLARATION") {
    val p = port(
      """package demo;
        |interface Builder<B extends Builder<B, S>, S extends CharSequence> {
        |  B add(CharSequence c);
        |  S toSequence();
        |}
        |interface Rich<T extends CharSequence> {
        |  <B extends Builder<B, T>> B getBuilder();
        |}
        |""".stripMargin)
    // no `[B <: …]` clause survives, and `B`'s own occurrence inside the bound is `?`.
    assertEmits(p, "def getBuilder(): demo.Builder[?, T]")
    assertNotEmits(p, "def getBuilder[B")
  }

  test("…and the BODY's own `(B)` cast is erased through the same frame, so the two agree") {
    val p = port(
      """package demo;
        |interface Builder2<B extends Builder2<B, S>, S extends CharSequence> { S toSequence(); }
        |interface Rich2<T extends CharSequence> { <B extends Builder2<B, T>> B getBuilder(); }
        |class Impl2 implements Rich2<String> {
        |  public <B extends Builder2<B, String>> B getBuilder() { return (B) null; }
        |}
        |""".stripMargin)
    // java's own `(B)` — written under a `//noinspection unchecked` in every library that has this
    // shape — becomes a cast to the SAME erasure the result carries, so the body conforms.
    assertEmits(p, "def getBuilder(): demo.Builder2[?, java.lang.String]")
    assertNotEmits(p, "asInstanceOf[B]")
  }

  test("the UNCHECKED OVERRIDE is what this buys: a NON-GENERIC narrowing becomes a covariant one") {
    val p = port(
      """package demo;
        |interface Builder3<B extends Builder3<B, S>, S extends CharSequence> { S toSequence(); }
        |interface Rich3<T extends CharSequence> { <B extends Builder3<B, T>> B getBuilder(); }
        |final class Narrow implements Builder3<Narrow, StringBuilder> {
        |  public StringBuilder toSequence() { return null; }
        |}
        |interface Based extends Rich3<StringBuilder> {
        |  @Override Narrow getBuilder();
        |}
        |""".stripMargin)
    // `Based`'s declaration is untouched — it was never generic — and the parent it overrides is
    // now `getBuilder(): Builder3[?, StringBuilder]`, which `Narrow` conforms to. What makes the
    // override legal is that NO generic clause survives anywhere on this name.
    assertEmits(p, "def getBuilder(): demo.Narrow")
    assertNotEmits(p, "def getBuilder[B")
  }

  test("NEGATIVE: a FORMAL mentioning the variable constrains it, so both languages infer it") {
    val p = port(
      """package demo;
        |interface Builder4<B extends Builder4<B, S>, S extends CharSequence> { S toSequence(); }
        |abstract class Seq4<T extends CharSequence> {
        |  abstract <B extends Builder4<B, T>> B reuse(B b);
        |}
        |""".stripMargin)
    assertEmits(p, "def reuse[B <: demo.Builder4[B, T]]")
  }

  test("NEGATIVE: an ORDINARY bound has denotable instantiations — only an F-BOUND has none") {
    val p = port(
      """package demo;
        |interface Node { }
        |abstract class Tree {
        |  abstract <N extends Node> N first();
        |}
        |""".stripMargin)
    // `class MyNode implements Node` satisfies `N <: Node` perfectly, and java callers DO write it;
    // erasing this to `Node` would throw away the caller's own answer (G8's conjunct).
    assertEmits(p, "def first[N <: demo.Node]")
  }

  test("NEGATIVE: a VACUOUS bound is not an F-bound either — `<T> List<T> emptyList()` stays") {
    val p = port(
      """package demo;
        |import java.util.List;
        |class Empties {
        |  <T> List<T> emptyList() { return null; }
        |}
        |""".stripMargin)
    // java's `<T>` IS `<T extends Object>` (§4.55's own note), which is a bound with no variable in
    // it — the clause survives, which is the point.
    assertEmits(p, "def emptyList[T <: java.lang.Object]")
  }

  test("NEGATIVE: a variable the RESULT does not mention is not erased — nothing would change") {
    val p = port(
      """package demo;
        |interface Builder5<B extends Builder5<B, S>, S extends CharSequence> { S toSequence(); }
        |abstract class Seq5<T extends CharSequence> {
        |  abstract <B extends Builder5<B, T>> int count();
        |}
        |""".stripMargin)
    assertEmits(p, "def count[B <: demo.Builder5[B, T]]")
  }
