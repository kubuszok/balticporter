package balticporter.corpus

import balticporter.testkit.PortSuite

/** JAVA'S UNBOXING CONVERSION IS TWO STEPS, and the shortcut through `Number` names a member that
  * does not exist on two of the eight wrappers. */
class WrapperUnboxSpec extends PortSuite:

  test("a `Character` at an `int` SLOT unboxes at `char` and widens — never `intValue()`") {
    val p = port("public class A { int f(Character c) { return c; } }")
    assertEmits(p, "charValue()")
    assertNotEmits(p, "intValue")
  }

  test("…and at a CONDITIONAL operand, which is the other call site") {
    val p = port("public class A { int f(boolean b, Character c, int i) { return b ? c : i; } }")
    assertEmits(p, "charValue()")
    assertNotEmits(p, "intValue")
  }

  test("a `Character` at a `char` slot is the SAME-TYPE unbox and stays one step") {
    val p = port("public class A { char f(Character c) { return c; } }")
    assertNotEmits(p, "asInstanceOf[scala.Char]")
  }

  test("a NUMBER wrapper keeps the one-step form: `Long` at a `double` really is `doubleValue()`") {
    // The half that must NOT move. K17 face 2's measured shape asserts this text in
    // `CatalogAreaESpec`; asserted here too, because the change that breaks it is a change to this
    // function and a reader of this file has to see which side of the line each wrapper is on.
    val p = port("public class A { double f(Long v) { return v; } }")
    assertEmits(p, "doubleValue()")
    assertNotEmits(p, "longValue")
  }

  test("a `Boolean` at a `boolean` slot is `booleanValue()` and nothing else") {
    val p = port("public class A { boolean f(Boolean v) { return v; } }")
    assertNotEmits(p, "asInstanceOf[scala.Boolean]")
  }
