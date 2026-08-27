package balticporter.corpus

import balticporter.testkit.PortSuite

/** AN F-BOUNDED TYPE APPLIED TO A WILDCARD CANNOT CAPTURE-CONVERT — `ENGINE-LIMITS.md` G31.
  *
  * `for (Object part : builder)` over a `Seq<?>` whose declaration is `Seq<S extends Seq<S>>` is
  * ordinary java: JLS 14.14.2 looks `Iterable<T>` up in the expression's type and iterates at `T`.
  * Scala's `for` is a `foreach` CALL, the shim's `foreach` is an EXTENSION, and applying one to a
  * wildcard application means capture conversion — which dotty performs by substituting `Any` for
  * the F-bounded parameter, so the capture's upper bound is `Seq[Any]` while its own slot asks for
  * `Seq[CAP]`. `E057`, at an INFERRED type, and no spelling of the wildcard repairs it: the java
  * form (`Seq<? extends Seq<?>>`) fails identically, measured.
  *
  * So the emission is java's own lookup — the iterable expression put at the `java.lang.Iterable`
  * supertype java itself read, through the same `Tree.Typed` view every other receiver view in this
  * frontend uses. It is an UPCAST and not a reified question (`ENGINE-LIMITS.md` K18's exclusion):
  * the value already has that type, so the `checkcast` cannot fail and nothing is asserted that the
  * program does not already know.
  */
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
    assertEmits(p, "<- xs)")
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
    assertEmits(p, "<- xs)")
    assertNotEmits(p, "xs.asInstanceOf[java.lang.Iterable")
  }
