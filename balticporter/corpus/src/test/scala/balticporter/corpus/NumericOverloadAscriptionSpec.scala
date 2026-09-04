package balticporter.corpus

import balticporter.testkit.PortFixture

/** THE NUMERIC-OVERLOAD PIN, and the type variable it may not write down. */
class NumericOverloadAscriptionSpec extends munit.FunSuite:

  private def out(java: String): String = PortFixture.port(java).out

  // -- POSITIVE 1: the shape the pin was written for, no generics anywhere -----------------------
  test("a widened sibling pins the alternative javac chose") {
    val o = out(
      """package demo;
        |class Plain {
        |    void put(int a, int b) {}
        |    void put(float a, float b) {}
        |    void go() { put(1, 2); }
        |}
        |""".stripMargin)
    assert(o.contains("(this.put: (scala.Int, scala.Int) => scala.Unit)(1, 2)"), o)
  }

  // -- POSITIVE 2: the result is the CALLEE's variable, and the receiver's `extends` clause says
  //    what it is. Emitted un-substituted this reads `=> S`, which is `Not found: type S`.
  test("an F-bounded result is substituted through the receiver's extends clause") {
    val o = out(
      """package demo;
        |class Builder<S extends Builder<S>> {
        |    S append(char c, int repeat) { return null; }
        |    S append(int start, int end) { return null; }
        |}
        |class Plain extends Builder<Plain> {}
        |class Caller {
        |    void go(Plain p) { p.append(' ', 2); }
        |}
        |""".stripMargin)
    assert(o.contains("(p.append: (scala.Char, scala.Int) => demo.Plain)"), o)
    assert(!o.contains("=> S)"), o)
  }

  // -- POSITIVE 3 (nameability, not substitution): inside the DECLARING class the variable really
  //    is in scope, so the pin is written with the variable's own name.
  test("a call inside the declaring class keeps the variable, because it is nameable there") {
    val o = out(
      """package demo;
        |class Builder<S extends Builder<S>> {
        |    S append(char c, int repeat) { return null; }
        |    S append(int start, int end) { return null; }
        |    S twice() { return append(' ', 2); }
        |}
        |""".stripMargin)
    assert(o.contains("(this.append: (scala.Char, scala.Int) => S)"), o)
  }

  // -- NEGATIVE 1: no sibling is wider, so nothing is ambiguous and nothing is written ------------
  test("a sibling that does not absorb these arguments gets no pin") {
    val o = out(
      """package demo;
        |class Narrow {
        |    void put(int a, int b) {}
        |    void put(char a, char b) {}
        |    void go() { put(1, 2); }
        |}
        |""".stripMargin)
    assert(!o.contains("this.put:"), o)
    assert(o.contains("this.put(1, 2)"), o)
  }

  // -- NEGATIVE 2: a RAW receiver binds nothing, so the substitution cannot reach the variable and
  //    the pin DECLINES rather than writing a name that resolves to nothing. This is the arm that
  //    keeps the fix from being an approximation.
  test("a raw receiver declines the pin instead of naming an unresolvable variable") {
    val o = out(
      """package demo;
        |class Builder<S extends Builder<S>> {
        |    S append(char c, int repeat) { return null; }
        |    S append(int start, int end) { return null; }
        |}
        |class RawCaller {
        |    @SuppressWarnings("rawtypes")
        |    void go(Builder b) { b.append(' ', 2); }
        |}
        |""".stripMargin)
    assert(!o.contains("=> S)"), o)
    // …and not `=> ?` either: binding the variable to the wildcard the raw use carries names
    // nothing, which is the second half of the same decline.
    assert(!o.contains("=> ?)"), o)
    assert(o.contains("b.append(' ', 2)"), o)
  }

  // -- NEGATIVE 3: a variable owned by the callee's own METHOD has no `extends` clause to resolve
  //    it at all, so it takes the same conservative arm (`ENGINE-LIMITS.md` G12's own sentence).
  test("a method-level type variable in the result declines the pin") {
    val o = out(
      """package demo;
        |class Picker {
        |    <T> T pick(char a, int b) { return null; }
        |    <T> T pick(int a, int b) { return null; }
        |    void go() { pick(' ', 2); }
        |}
        |""".stripMargin)
    assert(!o.contains("=> T)"), o)
  }
