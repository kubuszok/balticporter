package balticporter.corpus

import balticporter.testkit.PortSuite

/** THE TWO SHAPES `JS-C30`'s LOWERING DOES NOT REACH, pinned in both directions.
  *
  * T9 closed the method-LOCAL class (JLS 14.3): it is a `BlockStatement`, it lowers to a Scala
  * local `class`/`object`, and twenty-eight whole-program walks moved onto
  * `StandardTraversal.allClassDefs` so they can see one. Neither shape below occurs in any of the
  * fifteen ported modules, which is exactly why the lowering could close without meeting them — and
  * `AbsorbedProbeSpec`'s rule applies: a construct nothing in the corpus exercises needs a fixture
  * that says what is emitted TODAY, in both directions, or the next change to that path moves it
  * silently.
  *
  * BOTH ARE LOUD. Neither ships a plausible wrong answer: each is a scalac error at §3's gate, so
  * the instrument that counts them is the port's own error count. That is the whole reason they are
  * pinned rather than refused — a frontend refusal here would take the shapes that DO work with it.
  *
  * `ENGINE-LIMITS.md` T9 carries both with the emitted text.
  */
class T9ResidueProbeSpec extends PortSuite:

  // -- 1. a local class that collides with a nested TYPE of the same name ------------------------
  //
  // §4.55's "count what the constructor funnel PROMOTES" reaches its parameters and its top-level
  // LOCALS. A local CLASS is neither, and it becomes a member of the emitted class body exactly as
  // a promoted local does — so it can collide with a nested type, and the member-clash pass does
  // not see it.
  //
  // The collision is narrower than it looks, and the negative below is why: java's STATIC nested
  // class is emitted into the COMPANION OBJECT and the local class into the CLASS, which are two
  // Scala namespaces. Only a non-static INNER class shares a body with it.

  test("a promoted-constructor local class beside a STATIC nested class of the same name is FINE — two namespaces") {
    val out = port(
      """package demo;
        |class Holder {
        |  static class Inner { int v() { return 1; } }
        |  int x;
        |  Holder(int n) {
        |    class Inner { int v() { return 2; } }
        |    x = new Inner().v();
        |  }
        |}
        |""".stripMargin).out
    // the local one is a member of the CLASS …
    assert(clue(out).contains("class Inner private[demo] () {\n    private[demo] def v(): scala.Int = {\n      return 2"))
    // … and java's static nested one is a member of the OBJECT.
    assert(clue(out).contains("object Holder {"))
    assertEquals(out.split("\n").count(_.trim.startsWith("class Inner")), 1)
  }

  test("…and beside a non-static INNER class it is a DUPLICATE DEFINITION — one body, one namespace") {
    val out = port(
      """package demo;
        |class Holder {
        |  class Inner { int v() { return 1; } }
        |  int x;
        |  Holder(int n) {
        |    class Inner { int v() { return 2; } }
        |    x = new Inner().v();
        |  }
        |}
        |""".stripMargin).out
    // BOTH are emitted into `class Holder`'s body, so scalac reports `Inner is already defined`.
    // Pinned as the defect it is: the member-clash pass renames a field that shadows a field and a
    // field that clashes with a method, and does not consider a class a member at all.
    assertEquals(clue(out).split("\n").count(_.trim.endsWith("class Inner private[demo] () {")), 2)
    assert(!out.contains("object Holder"), "the fixture must not accidentally split the two across namespaces")
  }

  // -- 2. a method-local ENUM --------------------------------------------------------------------
  //
  // `CtEnum <: CtClass`, so the statement dispatch's `case c: CtClass[?]` takes it with no arm
  // aware an enum was there — the same absorption `JS-C43` recorded for a record, one construct
  // over, until that row's own flag made the question askable.
  // The DECLARATION survives that: the enum lowering runs and emits the type. The REFERENCE does
  // not.

  test("a method-local enum LOWERS — the declaration is the ordinary scala 3 `enum`") {
    val out = port(
      """package demo;
        |class Holder {
        |  int pick() {
        |    enum Level { LOW, HIGH }
        |    return Level.HIGH.ordinal();
        |  }
        |}
        |""".stripMargin).out
    assert(clue(out).contains("enum Level extends java.lang.Enum[Level]"))
    assert(clue(out).contains("case HIGH extends Level"))
    // …and NOT the sealed shape's hand-written `values()`: the desugaring supplies a PARENLESS one
    // (`ENGINE-LIMITS.md` T21).
    assert(!out.contains("def values(): scala.Array[Level]"))
  }

  test("…and its REFERENCE is emitted as javac's BINARY NAME, projected through the enclosing type") {
    val out = port(
      """package demo;
        |class Holder {
        |  int pick() {
        |    enum Level { LOW, HIGH }
        |    return Level.HIGH.ordinal();
        |  }
        |}
        |""".stripMargin).out
    // `demo.Holder.1Level.HIGH` — two defects in one path, and the pin is on the exact text so the
    // day either is fixed this test says so:
    //   - `1Level` is javac's binary simple name for the FIRST `Level` in the type. The DECLARATION
    //     strips that leading digit run (`SpoonTir.localName`, and JLS 3.8 makes the strip safe);
    //     the reference reads `Symbol.fullName`, which still holds it, and `1Level` is not a Scala
    //     identifier at all;
    //   - the projection is wrong whatever the name: a method-local type is not a member of the
    //     enclosing class, it is lexically in scope, so the reference wants the SIMPLE name.
    assert(clue(out).contains("demo.Holder.1Level.HIGH"))
  }

  test("…while a local class's own STATIC member resolves by simple name, which is what the enum wants") {
    // the control, and the reason the defect above is about the REFERENCE path and not the
    // lowering: an all-static local class becomes a local `object` and its member is reached
    // without any projection at all.
    val out = port(
      """package demo;
        |class Holder {
        |  int pick() {
        |    class Local { static int k() { return 3; } }
        |    return Local.k();
        |  }
        |}
        |""".stripMargin).out
    assert(clue(out).contains("object Local"))
    assert(clue(out).contains("return Local.k()"))
  }
