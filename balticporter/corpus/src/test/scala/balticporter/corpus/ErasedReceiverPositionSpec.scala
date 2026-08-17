package balticporter.corpus

import balticporter.testkit.PortSuite

/** THE ERASED VIEW IS DECIDED PER POSITION — `ENGINE-LIMITS.md` G21's second half.
  *
  * G11's erased-receiver view asks ONE question of the whole argument list (*are the receiver's
  * arguments unknown — raw, or does any of them hold a wildcard?*) and then applies the answer at
  * EVERY position. That is exact for a RAW use, where the source wrote nothing anywhere, and wrong
  * for the ordinary MIXED one: `Fn<? super D, Class<?>>` leaves position 0 unknown and WRITES
  * position 1, and java's own view of `apply` there returns `Class<?>`, not `Object`. Erased at
  * both, a value java handed straight to a `Class<?>` slot arrives needing a conversion java never
  * performed — `Found: Object / Required: Class[?]`.
  *
  * The narrowing is what makes it shippable and it is not a tuning. This call's ARGUMENT side is
  * three readings of one erasure (`eraseDependentArgs`, `knownReceiverArgs`, and the erasure
  * `coerceArgsFixed` synthesises at the executable reference's already-erased formal), and the
  * receiver's view has to agree with all three. They agree about a VARIABLE-FREE written argument,
  * which no substitution can move; they provably do not about one that mentions a variable —
  * libGDX `OrderedMap#putAll` measured at 0 → 1. So a position is carried only where the source
  * wrote it AND it mentions no type variable, and the second test below is that guard, standing in
  * for the port that measured it.
  */
class ErasedReceiverPositionSpec extends PortSuite:

  private val mixed =
    """package demo;
      |interface Fn<A, B> { B apply(A a); }
      |class Item { }
      |class Use {
      |  static <D extends Item> Class<?> go(D d, Fn<? super D, Class<?>> ex) { return ex.apply(d); }
      |}
      |""".stripMargin

  test("a WRITTEN, variable-free position is carried; the unknown one beside it still erases") {
    val p = port(mixed)
    // position 0 is `? super D` — what the source left UNKNOWN — and erases to `Object`;
    // position 1 is `Class<?>`, which the source WROTE and this port can write back.
    assertEmits(p, "asInstanceOf[demo.Fn[java.lang.Object, java.lang.Class[?]]]")
    // and the negative that makes it a fix rather than a rendering: the whole-list erasure is gone.
    assertNotEmits(p, "demo.Fn[java.lang.Object, java.lang.Object]")
  }

  test("a written position that MENTIONS A TYPE VARIABLE still erases — the co-reader guard") {
    // `Fn<? super D, D>`: position 1 is written, and it is a type VARIABLE. Carried, the receiver
    // would say `D` while the argument erasure three functions away still says `Object`, which is
    // the 0 → 1 regression G21 records. Both positions erase until those three are one derivation.
    val p = port(
      """package demo;
        |interface Fn<A, B> { B apply(A a); }
        |class Item { }
        |class Use2 {
        |  static <D extends Item> D go(D d, Fn<? super D, D> ex) { return ex.apply(d); }
        |}
        |""".stripMargin
    )
    assertEmits(p, "asInstanceOf[demo.Fn[java.lang.Object, java.lang.Object]]")
  }

  test("a RAW receiver writes NOTHING anywhere, so every position still erases") {
    // The case the one-question form was exact for, and the reason `unknown` stays the gate: with
    // no actuals at all there is no written position to carry and the view is unchanged.
    val p = port(
      """package demo;
        |interface Fn<A, B> { B apply(A a); }
        |class Item { }
        |class Use3 {
        |  static Object go(Item d, Fn ex) { return ex.apply(d); }
        |}
        |""".stripMargin
    )
    assertEmits(p, "asInstanceOf[demo.Fn[java.lang.Object, java.lang.Object]]")
  }
