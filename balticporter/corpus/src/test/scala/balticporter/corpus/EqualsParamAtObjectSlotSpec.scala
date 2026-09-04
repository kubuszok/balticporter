package balticporter.corpus

import balticporter.testkit.PortSuite

/** `ENGINE-LIMITS.md` G8.9 — the frontend widens a java `equals(Object)`'s parameter to `scala.Any`
  * so that it OVERRIDES `Object.equals` instead of clashing with it, and every forwarding of that
  * parameter to an `Object` slot then has an argument scala types as strictly wider. */
class EqualsParamAtObjectSlotSpec extends PortSuite:

  test("a widened `equals` parameter forwarded to an `Object` slot is cast") {
    val p = port(
      """package demo;
        |class Util { static boolean same(CharSequence a, Object o) { return false; } }
        |class Seq implements CharSequence {
        |  public char charAt(int i) { return ' '; }
        |  public int length() { return 0; }
        |  public CharSequence subSequence(int a, int b) { return this; }
        |  @Override public boolean equals(Object o) { return Util.same(this, o); }
        |}
        |""".stripMargin)
    assertEmits(p, "override def equals(o: scala.Any)")
    assertEmits(p, "same(this, o.asInstanceOf[java.lang.Object])")
  }

  test("NEGATIVE: an ORDINARY `Object` parameter forwarded to an `Object` slot takes no cast") {
    val p = port(
      """package demo;
        |class Util2 { static boolean same(Object a, Object o) { return false; } }
        |class Holder2 {
        |  boolean check(Object o) { return Util2.same(this, o); }
        |}
        |""".stripMargin)
    // nothing was widened, so nothing needs boxing — the cast would be noise on every `Object`
    // parameter in a corpus.
    assertEmits(p, "same(this, o)")
    assertNotEmits(p, "o.asInstanceOf[java.lang.Object]")
  }

  test("NEGATIVE: a TWO-argument `equals` is not `Object.equals` and is not widened") {
    val p = port(
      """package demo;
        |class Util3 { static boolean same(Object a, Object o) { return false; } }
        |class Holder3 {
        |  boolean equals(Object o, int depth) { return Util3.same(this, o); }
        |}
        |""".stripMargin)
    assertEmits(p, "def equals(o: java.lang.Object, depth: scala.Int)")
    assertNotEmits(p, "o.asInstanceOf[java.lang.Object]")
  }
