package balticporter.corpus

import balticporter.testkit.PortSuite

/** A RECEIVER IS AN OPERAND, and `.m` binds tighter than every control-flow expression. */
class EmitterReceiverParensSpec extends PortSuite:

  private val src =
    """package demo;
      |class P {
      |  String pick(boolean c, Object a, Object b)   { return (c ? a : b).toString(); }
      |  boolean isStr(boolean c, Object a, Object b) { return (c ? a : b) instanceof String; }
      |  int arrLen(boolean c, int[] a, int[] b)      { return (c ? a : b).length; }
      |  int at(boolean c, int[] a, int[] b, int i)   { return (c ? a : b)[i]; }
      |  int cat(String a, String b)                  { return (a + b).length(); }
      |  String plain(Object a)                       { return a.toString(); }
      |}
      |""".stripMargin

  test("a CONDITIONAL receiver is parenthesised — the call is on the conditional, not on a branch") {
    assertEmits(port(src), "(if (c) a else b).toString()")
  }

  test("…and so is an `instanceof` operand") {
    assertEmits(port(src), "(if (c) a else b).isInstanceOf[java.lang.String]")
  }

  test("…and an array `.length`") {
    assertEmits(port(src), "(if (c) a else b).length")
  }

  test("…and an array INDEX receiver, where the misparse is a call on one branch") {
    assertEmits(port(src), "(if (c) a else b)(i)")
  }

  test("an OPERATOR application receiver is parenthesised for precedence") {
    // `a + b.length()` is a different program: `+` binds looser than `.`, so the unparenthesised
    // form concatenates `a` with `b`'s length.
    assertEmits(port(src), "(a + b).length()")
  }

  test("an ordinary receiver gains NOTHING — the negative") {
    assertEmits(port(src), "return a.toString()")
    assertNotEmits(port(src), "(a).toString()")
  }
