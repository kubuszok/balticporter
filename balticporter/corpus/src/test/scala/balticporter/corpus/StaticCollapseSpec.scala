package balticporter.corpus

import balticporter.testkit.PortSuite

/** The ALL-STATIC COLLAPSE and the three things that must withhold it. */
class StaticCollapseSpec extends PortSuite:

  test("an all-static class with no such use COLLAPSES to an object") {
    val p = port(
      """package demo;
        |class Util {
        |  static int twice(int n) { return n * 2; }
        |}
        |""".stripMargin
    )
    assertEmits(p, "object Util {")
    assertNotEmits(p, "class Util")
  }

  test("…but NOT when something EXTENDS it") {
    val p = port(
      """package demo;
        |class Base { static int twice(int n) { return n * 2; } }
        |class Sub extends Base { }
        |""".stripMargin
    )
    assertEmits(p, "class Base")
  }

  test("…nor when something INSTANTIATES it") {
    val p = port(
      """package demo;
        |class Bag { static int twice(int n) { return n * 2; } }
        |class User { Bag make() { return new Bag(); } }
        |""".stripMargin
    )
    assertEmits(p, "class Bag")
  }

  test("…nor when a CLASS LITERAL names it — including from inside itself") {
    // The java idiom for a log tag, and where this was measured: gdx-vfx's `VfxGLUtils` opens with
    // `private static final String TAG = VfxGLUtils.class.getSimpleName();` and every other member
    // is static too, so the collapse fired on the very class the literal names.
    val p = port(
      """package demo;
        |class Tagged {
        |  private static final String TAG = Tagged.class.getSimpleName();
        |  static String tag() { return TAG; }
        |}
        |""".stripMargin
    )
    assertEmits(p, "class Tagged")
    assertEmits(p, "classOf[Tagged]")
    // the statics are still reachable by the same call — they moved to the companion object, which
    // is exactly where the collapse would have put them.
    assertEmits(p, "object Tagged {")
  }

  test("a class literal naming a DIFFERENT type does not hold that type's neighbour open") {
    // the guard is per-symbol, not "somewhere in this file there is a classOf".
    val p = port(
      """package demo;
        |class Named { }
        |class Util {
        |  static String of() { return Named.class.getSimpleName(); }
        |}
        |""".stripMargin
    )
    assertEmits(p, "object Util {")
    assertNotEmits(p, "class Util")
  }
