package balticporter.corpus

import balticporter.testkit.PortSuite

/** A RECEIVER IS AN OPERAND, and `.m` binds tighter than every control-flow expression.
  *
  * `(c ? a : b).toString()` is ordinary java. Rendered as `if (c) a else b.toString()` scala parses
  * it as `if (c) a else (b.toString())` — the call moved INTO one branch. That is `CLAUDE.md` §4.4's
  * shape at the emitter rather than at a statement: where the two branches have different types it
  * is a type error attributed to the wrong thing, and where they do not it COMPILES and calls the
  * method on one branch only.
  *
  * `TirEmitter.operand` has always known which terms need parenthesising as an operand — an
  * operator application and a control-flow expression — and three receiver positions rendered their
  * qualifier with `term` instead: a `Select`'s, an `InstanceOf`'s, an `ArrayLength`'s and an
  * `ArrayAccess`'s. `Tree.Typed` and `Tree.Spread` already went through `operand`, which is why the
  * rule was half-applied rather than absent.
  *
  * Found by porting a test suite, not by compiling a library (`InsertionTest`'s
  * `(nodes.length >= 2 ? nodes[1].render(c) : nodes[0].render(c)).toString()`).
  */
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
