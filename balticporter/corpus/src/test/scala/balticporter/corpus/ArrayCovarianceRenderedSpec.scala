package balticporter.corpus

import balticporter.testkit.PortSuite

/** Java's array covariance (JLS 10.10) asked at the RENDERING, because java's own erasure can write
  * both sides of the slot as one type — `ENGINE-LIMITS.md` G13.5, which is §0's rule read at a slot.
  *
  * `arrayCovSlot` compares the two recorded JAVA array types and is exact wherever java wrote two
  * different ones. `<E extends Enum<E>> E[] getUniverse(…)` assigned to an `Enum<?>[]` local is not
  * that shape: both sides read `java.lang.Enum[]`, so there is nothing to compare — while the emitted
  * term is an `Array[E]` at an `Array[Enum[?]]` slot, and scala's arrays are INVARIANT.
  *
  * The predicate's whole safety argument is arithmetic, which is what the negatives pin: two
  * DIFFERENT `Array[…]` renderings conform in neither direction, so it can only add a cast at a slot
  * scala would have rejected outright, and where the renderings agree it declines by construction.
  */
class ArrayCovarianceRenderedSpec extends PortSuite:

  test("an ERASE-EQUAL array slot still takes java's covariance cast") {
    val p = port(
      """package demo;
        |class Universe {
        |  static <E extends Enum<E>> E[] all(Class<E> t) { return null; }
        |  static <E extends Enum<E>> int size(Class<E> elementType) {
        |    Enum<?>[] universe = all(elementType);
        |    return universe.length;
        |  }
        |}
        |""".stripMargin)
    // java's two array types ERASE TO ONE, so the java-name comparison sees nothing at all; the
    // emitted term is the only side carrying a rendering the compiler will see.
    assertEmitsMatch(p, """universe: scala\.Array\[java\.lang\.Enum\[\?\]\] = .*asInstanceOf\[scala\.Array\[java\.lang\.Enum\[\?\]\]\]""")
  }

  test("the WRITTEN covariance — two different java array types — is unchanged") {
    val p = port(
      """package demo;
        |class Boxes {
        |  static String[] names() { return null; }
        |  static int go() { Object[] xs = names(); return xs.length; }
        |}
        |""".stripMargin)
    // this is the cell `arrayCovSlot` always answered; the rendered test must not change it.
    assertEmitsMatch(p, """xs: scala\.Array\[java\.lang\.Object\] = .*asInstanceOf\[scala\.Array\[java\.lang\.Object\]\]""")
  }

  test("NEGATIVE — an array slot whose renderings AGREE gets nothing") {
    val p = port(
      """package demo;
        |class Same {
        |  static String[] names() { return null; }
        |  static int go() { String[] xs = names(); return xs.length; }
        |}
        |""".stripMargin)
    // the predicate declines by arithmetic here, which is what keeps it from putting a no-op
    // `asInstanceOf` on every array initialiser in every port — the over-approximation §5 has no
    // instrument for.
    assertNotEmits(p, "xs: scala.Array[java.lang.String] = demo.Same.names().asInstanceOf")
  }

  test("NEGATIVE — a NON-array slot is not a party to this rule") {
    val p = port(
      """package demo;
        |class Plain {
        |  static String name() { return null; }
        |  static int go() { CharSequence s = name(); return s.length(); }
        |}
        |""".stripMargin)
    // `String` really is a `CharSequence` in scala too, so java's own widening needs no cast and one
    // here would be text for nothing.
    assertNotEmits(p, "demo.Plain.name().asInstanceOf[java.lang.CharSequence]")
  }
