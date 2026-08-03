package balticporter.corpus

import balticporter.testkit.PortSuite

/** JAVA'S UNBOXING CONVERSION IS TWO STEPS, and the shortcut through `Number` names a member that
  * does not exist on two of the eight wrappers.
  *
  * JLS 5.1.8 unboxes at the wrapper's OWN primitive; a widening primitive conversion (5.1.2) then
  * takes it to the slot. `SpoonTir.unbox` collapsed the pair into one `xxxValue()` call keyed on the
  * TARGET, which is exact for the six `java.lang.Number` wrappers — every one of them carries the
  * whole `byteValue()`/`shortValue()`/`intValue()`/`longValue()`/`floatValue()`/`doubleValue()`
  * family, so `Long -> double` really is `doubleValue()`.
  *
  * `Character` and `Boolean` are NOT `Number`s. They carry `charValue()` and `booleanValue()` and
  * nothing else, so a `Character` flowing into an `int` emitted `c.intValue()` — a member no class
  * in the chain declares. That one is LOUD (`value intValue is not a member of java.lang.Character`)
  * rather than silent, which is why it is a spec here and not an `ENGINE-LIMITS.md` entry: it fails
  * a compile at the line, and the fix is to emit the two steps java performs.
  *
  * BOTH CALL SITES, because the collapse was written once and read twice: `coerce`'s cross-type
  * unbox clause (any slot with an expected type) and `promotedBranch`'s boxed arm (a conditional
  * operand, which has none). A fixture reaching only one of them would have left the other emitting
  * `intValue()` on a `Character`.
  */
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
