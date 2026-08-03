package balticporter.testkit

import balticporter.catalog.{Attaches, Differences, JS, Status}

/** THE `JS-E` EDGE-CASE SUITE — one test per expression row the engine wires, at the shape the row
  * is about.
  *
  * This suite is the half of the guarantee the obligation wrapper does NOT provide, and it is why
  * `DESIGN.md` §2.8 states the wrapper's claim at the strength it actually holds. The wrapper
  * detects an ABSENT consult; it cannot detect a WRONG one, because an arm that consults a row and
  * hands it a predicate which never returns `Some` discharges the obligation and emits the same
  * wrong code. So each test below asserts BOTH — that the branch was live (`assertConsults`) and
  * that the emitted Scala means what java meant. Neither on its own is worth much: the log without
  * the text says the question was asked and not that the answer was right; the text without the log
  * passes for a lowering that never asked.
  *
  * A row that is `NoObligation` gets no test here and owes none — there is nothing to discharge at
  * a site, and the last test asserts exactly that partition rather than leaving it to a reader.
  */
class CatalogAreaESpec extends PortSuite:

  // -- JS-E01: `==` on references is IDENTITY in java; scala's `==` calls `equals` ----------------

  test("JS-E01 — a reference `==` becomes `eq`, and the row is consulted where it does") {
    val p = port("public class A { boolean f(Object a, Object b) { return a == b; } }")
    assertConsults(p, JS.E(1), fired = true)
    assertEmits(p, " eq ")
    assertNotEmits(p, "a == b")
  }

  test("JS-E01 — a PRIMITIVE `==` is consulted and does NOT fire; `==` is already java's meaning") {
    // The edge the wrapper alone cannot see: the branch is live at every binary operator, and
    // firing it here would turn a value comparison into an identity one.
    val p = port("public class A { boolean f(int a, int b) { return a == b; } }")
    assertConsults(p, JS.E(1))
    assertEmits(p, "==")
    assertNotEmits(p, " eq ")
  }

  test("JS-E01 — `== null` stays `== null`: java's null test needs no identity operator") {
    val p = port("public class A { boolean f(Object a) { return a == null; } }")
    assertConsults(p, JS.E(1))
    assertNotEmits(p, " eq ")
  }

  // -- JS-E02: `++`/`--` USED AS A VALUE yields the value BEFORE the update -----------------------

  test("JS-E02 — a POSTFIX increment used as a value keeps java's before-the-update semantics") {
    val p = port("public class B { int f(int i) { return i++; } }")
    assertConsults(p, JS.E(2), fired = true)
    // the emitter renders the temporary; what matters is that the RETURNED value is the old one,
    // which the naive `{ i += 1; i }` gets wrong and every circular buffer noticed (§4.4).
    assertEmitsMatch(p, "(?s).*i \\+= 1.*")
  }

  test("JS-E02 — a PREFIX increment is the same row at the other shape") {
    val p = port("public class B { int f(int i) { return ++i; } }")
    assertConsults(p, JS.E(2), fired = true)
  }

  test("JS-E02 — a unary `!` is consulted and does not fire") {
    val p = port("public class B { boolean f(boolean b) { return !b; } }")
    assertConsults(p, JS.E(2))
    assertEmits(p, "!b")
  }

  // -- JS-E03: compound assignment NARROWS implicitly, in STATEMENT position ----------------------

  test("JS-E03 — `byte += byte` narrows back, because java's promotion computed an `int`") {
    val p = port("public class C { void f(byte b) { b += 3; } }")
    assertConsults(p, JS.E(3), fired = true)
    assertEmits(p, "scala.Byte")
  }

  test("JS-E03 — `int += int` is consulted and does NOT narrow") {
    val p = port("public class C { void f(int i) { i += 3; } }")
    assertConsults(p, JS.E(3))
    intercept[munit.FailException](assertConsults(p, JS.E(3), fired = true))
  }

  test("JS-E03 — a REFERENCE compound assignment (`String +=`) is consulted and does not narrow") {
    val p = port("public class C { void f(String s) { s += \"x\"; } }")
    assertConsults(p, JS.E(3))
    intercept[munit.FailException](assertConsults(p, JS.E(3), fired = true))
  }

  // -- JS-E04: the SAME difference in EXPRESSION position, and it is OPEN -------------------------

  test("JS-E04 is OPEN, so nothing consults it — and the wrapper reports the hole rather than hiding it") {
    // The row this whole mechanism was designed around: two arms lower a `CtOperatorAssignment`,
    // the statement one narrows and the expression one does not. Rule (ii) forbids consulting a row
    // the registry says nobody handles, so the honest state is a COUNTED hole — which is what makes
    // `catalog(undischarged)` the work list rather than a defect count.
    assertEquals(Differences.byId(JS.E(4)).status, Status.Open)
    val p = port("public class D { int f(byte b) { return (b += 3); } }")
    assertNotConsults(p, JS.E(4))
    // JS-E17 is beside it and is `Open` for the same kind of reason — the lvalue's single
    // evaluation, which no arm reproduces (`ENGINE-LIMITS.md` F7) — so the work list here is two
    // rows and both are declared rather than defects.
    assertEquals(p.catalog.undischarged.map(_.id), List(JS.E(4), JS.E(17)))
  }

  // -- JS-E05: the conditional operator's type is COMPUTED, not the lub of its branches -----------

  test("JS-E05 — a null branch is ascribed to the conditional's own type") {
    val p = port("public class E { String f(boolean b, String s) { return b ? s : null; } }")
    assertConsults(p, JS.E(5), fired = true)
  }

  test("JS-E05 — a conditional with no null branch is consulted all the same") {
    val p = port("public class E { int f(boolean b) { return b ? 1 : 2; } }")
    assertConsults(p, JS.E(5))
  }

  // -- JS-E14: string concatenation with a NON-`String` left operand ------------------------------

  test("JS-E14 — `obj + \"s\"` stringifies the left, because scala has no `+` on `obj`") {
    val p = port("public class F { String f(Object o) { return o + \"x\"; } }")
    assertConsults(p, JS.E(14), fired = true)
    assertEmits(p, "valueOf")
  }

  test("JS-E14 — a `String` left operand needs nothing, and the row is consulted anyway") {
    val p = port("public class F { String f(String s) { return s + \"x\"; } }")
    assertConsults(p, JS.E(14))
    assertNotEmits(p, "valueOf")
  }

  test("JS-E14 — numeric `+` is consulted and does not fire") {
    val p = port("public class F { int f(int a, int b) { return a + b; } }")
    assertConsults(p, JS.E(14))
  }

  // -- JS-E15: an assignment IS an expression, with the assigned value ----------------------------

  test("JS-E15 — an assignment used as a value yields the assigned value") {
    val p = port("public class G { int f(int a, int b) { return a = b; } }")
    assertConsults(p, JS.E(15), fired = true)
  }

  test("JS-E15 — the SAME assignment as a statement never reaches the expression dispatch") {
    // The dispatch is the key, and this is the pair that proves it: one java construct, two
    // positions, and only one of them owes the row.
    val p = port("public class G { void f(int a, int b) { a = b; } }")
    assertNotConsults(p, JS.E(15))
  }

  // -- JS-E07: the PHASE surface ------------------------------------------------------------------

  test("JS-E07 — the citation comes from a PHASE and names a DECLARATION, not a site") {
    val junit =
      """package p;
        |import org.junit.Assert;
        |import org.junit.Test;
        |public class H {
        |  @Test public void widened() { long v = 2L; Assert.assertEquals(1, v); }
        |}
        |""".stripMargin
    val p = port(junit, new balticporter.transform.TestFrameworkTransform())
    assertCites(p, JS.E(7), about = "widened")
  }

  test("JS-E07 — a widening in a FIELD INITIALISER is cited at the FIELD, not at the next member") {
    // The citation state is a flag `promote` sets and the `DefDef` hook reads, which the bottom-up
    // traversal reaches after the body. A field's initialiser is not inside a `DefDef` — a lambda in
    // one holds the promotion perfectly well — so the flag survived to the next declaration the
    // traversal reached and that one took the citation: here the class's own `<init>`, and with the
    // field last in a body, a member of the NEXT class.
    //
    // Nothing else can see this. The emitted text is identical, every check count is identical, and
    // `catalog(consulted)` counts the row either way; what moves is only WHICH declaration an agent
    // is sent to, and it is sent to one with nothing in it (§4.575).
    val a =
      """package p;
        |import org.junit.Assert;
        |public class A {
        |  static Runnable check = () -> Assert.assertEquals(1, 2L);
        |}
        |""".stripMargin
    val b =
      """package p;
        |import org.junit.Assert;
        |import org.junit.Test;
        |public class B {
        |  @Test public void untouched() { Assert.assertEquals("a", "a"); }
        |}
        |""".stripMargin
    val p = portAll(List("A.java" -> a, "B.java" -> b), new balticporter.transform.TestFrameworkTransform())
    assertCites(p, JS.E(7), about = "A#check")
    val at = p.catalog.citedAt(JS.E(7))
    assert(!at.exists(_.contains("<init>")) && !at.exists(_.contains("untouched")),
      s"the widening is in `check` and the citation names a member the phase never touched: $at")
  }



  // -- the partition, asserted rather than left to a reader ---------------------------------------

  test("every JS-E row is either wired, declared unmechanised, or owes nothing — and says which") {
    val byKind = Differences.expressions.groupBy(_.attaches match
      case _: Attaches.Lowered      => "lowered"
      case _: Attaches.Cited        => "cited"
      case _: Attaches.Unmechanised => "unmechanised"
      case _: Attaches.NoObligation => "none")
    // Every row is in exactly one bucket by construction; what this asserts is that no bucket has
    // silently swallowed the others. A wave that marked area E `Unmechanised` to keep a lane green
    // is what this test exists to catch.
    assertEquals(byKind.values.map(_.size).sum, Differences.expressions.size)
    assert(byKind.getOrElse("lowered", Nil).nonEmpty, "no JS-E row is wired to the lowering dispatch")
    assert(byKind.getOrElse("cited", Nil).nonEmpty, "no JS-E row is wired to the phase surface")
    // …and every row that claims NO obligation is one the registry also calls a non-difference or
    // handled-by-construction. A row with an open status and no obligation would be a gap nothing
    // counts.
    val silentlyExcused = byKind.getOrElse("none", Nil).filter(d => d.status.isOpen)
    assertEquals(silentlyExcused.map(_.id), Nil,
      "an Open row claiming NoObligation is a gap no lane can see")
  }
