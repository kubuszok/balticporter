package balticporter.corpus

import balticporter.testkit.PortSuite

/** AN F-BOUNDED TYPE APPLIED TO A WILDCARD CANNOT CAPTURE-CONVERT — `ENGINE-LIMITS.md` G31. */
class ForEachFBoundReceiverSpec extends PortSuite:

  test("a `for` over an F-bounded wildcard receiver iterates at the Iterable supertype java read") {
    val p = port(
      """package demo;
        |import java.util.Iterator;
        |interface Seq<S extends Seq<S>> extends Iterable<Object> {
        |  int length();
        |}
        |class Use {
        |  static int count(Seq<?> builder) {
        |    int n = 0;
        |    for (Object part : builder) { n = n + 1; }
        |    return n;
        |  }
        |}
        |""".stripMargin
    )
    // K9: the F-bound fix casts the receiver to `java.lang.Iterable[Object]`, and that type is a
    // kept JDK iterable — so the emitter uses the while-loop form (JLS 14.14.2). The upcast still
    // appears inside the iterator binding, which is correct: it evaluates the iterable once.
    assertEmits(p, "builder.asInstanceOf[java.lang.Iterable[java.lang.Object]].iterator()")
  }

  test("an ORDINARY bounded wildcard capture-converts, so no view is interposed") {
    // The guard is the F-BOUND and not the wildcard: `Plain[?]` at `Plain<X extends Thing>` applies
    // the extension unaided (measured at scalac 3.8.4), and a view there would be a
    // correct-but-unnecessary rewrite on every port that has one.
    val p = port(
      """package demo2;
        |class Thing { }
        |interface Plain<X extends Thing> extends Iterable<Object> { }
        |class Use2 {
        |  static void go(Plain<?> xs) {
        |    for (Object part : xs) { }
        |  }
        |}
        |""".stripMargin
    )
    // K9 (2026-09-05): a program type reaching `java.lang.Iterable` with no `foreach` iterates by
    // java's own protocol; the F-bound guard still interposes no view.
    assertEmits(p, "xs.iterator()")
    assertNotEmits(p, "xs.asInstanceOf[java.lang.Iterable")
  }

  test("a fully written receiver is untouched — the guard is a WILDCARD at the F-bounded slot") {
    val p = port(
      """package demo3;
        |interface Seq<S extends Seq<S>> extends Iterable<Object> { }
        |class Leaf implements Seq<Leaf> { public java.util.Iterator<Object> iterator() { return null; } }
        |class Use3 {
        |  static void go(Seq<Leaf> xs) {
        |    for (Object part : xs) { }
        |  }
        |}
        |""".stripMargin
    )
    // K9 (2026-09-05): a program type reaching `java.lang.Iterable` with no `foreach` iterates by
    // java's own protocol; the F-bound guard still interposes no view.
    assertEmits(p, "xs.iterator()")
    assertNotEmits(p, "xs.asInstanceOf[java.lang.Iterable")
  }
