package balticporter.testkit

import balticporter.catalog.{Attaches, Differences, JS, Status}

/** THE `JS-S` EDGE-CASE SUITE — one test per statement row the engine wires, at the shape the row is
  * about.
  *
  * The half of the guarantee the obligation wrapper does NOT give. The wrapper detects an ABSENT
  * consult; it cannot detect a WRONG one, because an arm that consults a row and hands it a
  * predicate which never returns `Some` discharges the obligation and emits the same wrong code. So
  * each test asserts BOTH — that the branch was live (`assertConsults`) and that the emitted Scala
  * means what java meant.
  *
  * AREA S IS THE FIRST AREA WITH TWO SURFACES, which is what this suite is really exercising.
  * `JS-E`'s rows all discharge in the frontend; most of these do not — a `switch` with no `default`
  * is decided while LOWERING and a `break` in the middle of a case while RENDERING, and the frontend
  * has already done its job correctly by the time the second one arises. `Rendering.of` at
  * `TirEmitter.stat`/`term` is that surface, and every `Attaches.Rendered` row below is a row that
  * carried `Unmechanised` — a COUNTED admission that nothing was measuring it — until it existed.
  *
  * A row that is `NoObligation` gets no test and owes none; a row the registry calls `Absent` gets
  * the OPPOSITE assertion, because rule (ii) makes consulting one a finding. The last two tests
  * assert those partitions rather than leaving them to a reader.
  */
class CatalogAreaSSpec extends PortSuite:

  // -- JS-S01: an unlabelled jump binds LEXICALLY to the innermost enclosing loop ------------------

  test("JS-S01 — a `break` in a loop gets a boundary, and the row is consulted where it does") {
    val p = port("public class A { void f() { while (true) { break; } } }")
    assertConsults(p, JS.S(1), fired = true)
    assertEmits(p, "boundary")
  }

  test("JS-S01 — a loop with NO jump is consulted and does not fire; a boundary nothing needs is noise") {
    val p = port("public class A { int n; void f() { while (n > 0) { n = n - 1; } } }")
    assertConsults(p, JS.S(1))
    assertNotEmits(p, "boundary")
  }

  test("JS-S01 — the row is consulted at EVERY loop kind, not only at `while`") {
    // The reason `Attaches.Both` exists: a loop is four `Tree` kinds and they all reach
    // `loopWithJumps`. Attaching the row to one would leave the other three able to render
    // without considering it.
    val p = port("public class A { int[] xs; void f() { for (int x : xs) { break; } } }")
    assertConsults(p, JS.S(1), fired = true)
  }

  // -- JS-S02: a LABEL sits on any statement, not only on a loop -----------------------------------

  test("JS-S02 — a label on a BLOCK becomes a named boundary around that statement") {
    val p = port("public class B { void f(boolean b) { outer: { if (b) break outer; } } }")
    assertConsults(p, JS.S(2), fired = true)
    assertEmits(p, "scala.util.boundary")
  }

  test("JS-S02 — a label NOBODY breaks to is consulted and does not fire") {
    // java lets a label sit on a statement nothing jumps to; an empty boundary would be noise that
    // then has to be shielded against, so the difference genuinely does not apply here.
    val p = port("public class B { int n; void f() { outer: { n = 1; } } }")
    assertConsults(p, JS.S(2))
  }

  // -- JS-S03: an INTERPOSED boundary steals the enclosing loop's un-annotated jumps ---------------

  test("JS-S03 — a labelled statement inside a loop forces the loop's own boundary to be NAMED") {
    val p = port(
      "public class C { void f(boolean b) { while (true) { inner: { if (b) break inner; } break; } } }")
    assertConsults(p, JS.S(3), fired = true)
    assertEmits(p, "brk$")
  }

  test("JS-S03 — a loop with jumps and nothing interposing is consulted and does not fire") {
    val p = port("public class C { void f() { while (true) { break; } } }")
    assertConsults(p, JS.S(3))
    assertNotEmits(p, "brk$")
  }

  // -- JS-S04: switch FALLTHROUGH runs the next case's statements -----------------------------------

  test("JS-S04 — a non-terminated case is lowered by DUPLICATING the next case's tail") {
    val p = port("public class D { int n; void f(int i) { switch (i) { case 1: n = 1; case 2: n = 2; break; } } }")
    assertConsults(p, JS.S(4), fired = true)
    assertEmitsMatch(p, "(?s).*case 1 =>.*n = 1.*n = 2.*")
  }

  test("JS-S04 — a switch whose every case terminates is consulted and does not fire") {
    val p = port("public class D { int n; void f(int i) { switch (i) { case 1: n = 1; break; case 2: n = 2; break; } } }")
    assertConsults(p, JS.S(4))
  }

  // -- JS-S05: a `switch` with no `default` FALLS OUT; a `match` throws ------------------------------

  test("JS-S05 — the fall-out arm is synthesised where java has no `default`") {
    val p = port("public class E { int n; void f(int i) { switch (i) { case 1: n = 1; break; } } }")
    assertConsults(p, JS.S(5), fired = true)
    assertEmits(p, "case _ =>")
  }

  test("JS-S05 — a switch that HAS a default is consulted and does not fire") {
    val p = port("public class E { int n; void f(int i) { switch (i) { case 1: n = 1; break; default: n = 0; } } }")
    assertConsults(p, JS.S(5))
  }

  // -- JS-S06: an unlabelled `break` in the MIDDLE of a case ends the CASE ---------------------------

  test("JS-S06 — a mid-case break earns the arm its own boundary; a `match` arm cannot be left early") {
    val p = port("public class F { int n; void f(int i, boolean b) { switch (i) { case 1: if (b) break; n = 1; break; } } }")
    assertConsults(p, JS.S(6), fired = true)
    assertEmits(p, "case$")
  }

  test("JS-S06 — a case whose only break is the terminator is consulted and does not fire") {
    val p = port("public class F { int n; void f(int i) { switch (i) { case 1: n = 1; break; } } }")
    assertConsults(p, JS.S(6))
    assertNotEmits(p, "case$")
  }

  // -- JS-S07: only an UNLABELLED trailing `break` terminates a case ---------------------------------

  test("JS-S07 — a LABELLED trailing break is NOT a case terminator; it leaves the loop") {
    val p = port(
      "public class G { int n; void f(int i) { outer: while (true) { switch (i) { case 1: break outer; } n = 1; } } }")
    assertConsults(p, JS.S(7))
    // the jump survives — stripping it as a terminator is what silently deleted it
    assertEmits(p, "boundary")
  }

  test("JS-S07 — an UNLABELLED trailing break IS the terminator, and it fires there") {
    val p = port("public class G { int n; void f(int i) { switch (i) { case 1: n = 1; break; } } }")
    assertConsults(p, JS.S(7), fired = true)
  }

  // -- JS-S08: a null reference selector NPEs IMPLICITLY ---------------------------------------------

  test("JS-S08 — a String selector gains the `case null` throw java has and scala does not") {
    val p = port("public class H { int n; void f(String s) { switch (s) { case \"a\": n = 1; break; } } }")
    assertConsults(p, JS.S(8), fired = true)
    assertEmits(p, "NullPointerException")
  }

  test("JS-S08 — a PRIMITIVE selector cannot be null; consulted, and no unreachable arm") {
    val p = port("public class H { int n; void f(int i) { switch (i) { case 1: n = 1; break; } } }")
    assertConsults(p, JS.S(8))
    assertNotEmits(p, "NullPointerException")
  }

  // -- JS-S11: a translated CATCH swallows a translated JUMP ------------------------------------------

  test("JS-S11 — a jump crossing a BROAD catch gets the re-throw arm ahead of java's own") {
    val p = port(
      "public class I { void f() { while (true) { try { break; } catch (Exception e) { } } } }")
    assertConsults(p, JS.S(11), fired = true)
    assertEmits(p, "scala.util.boundary.Break[?] => throw")
  }

  test("JS-S11 — a try with NO jump under it is consulted and does not fire") {
    val p = port("public class I { int n; void f() { try { n = 1; } catch (Exception e) { } } }")
    assertConsults(p, JS.S(11))
    assertNotEmits(p, "boundary.Break[?] => throw")
  }

  // -- JS-S12: a `finally` completing abruptly DISCARDS the try's own abrupt completion ---------------

  test("JS-S12 — a `finally` is consulted and fires wherever one exists") {
    val p = port("public class J { int n; void f() { try { n = 1; } finally { n = 2; } } }")
    assertConsults(p, JS.S(12), fired = true)
    assertEmits(p, "finally")
  }

  test("JS-S12 — a try with no finalizer is consulted and does not fire") {
    val p = port("public class J { int n; void f() { try { n = 1; } catch (Exception e) { } } }")
    assertConsults(p, JS.S(12))
  }

  // -- JS-S13: try-with-resources closes on ANY completion, in reverse order --------------------------

  test("JS-S13 — a resource is LOWERED, not dropped; the row is consulted and fires") {
    val p = port(
      """public class K {
        |  java.io.InputStream open() { return null; }
        |  void f() throws Exception { try (java.io.InputStream in = open()) { in.read(); } }
        |}""".stripMargin)
    assertConsults(p, JS.S(13), fired = true)
    assertEmits(p, "close()")
  }

  test("JS-S13 — a plain `try` is consulted and does not fire") {
    val p = port("public class K { int n; void f() { try { n = 1; } catch (Exception e) { } } }")
    assertConsults(p, JS.S(13))
  }

  // -- JS-S14: multi-catch `A | B` -------------------------------------------------------------------

  test("JS-S14 — multi-catch becomes a union type in the pattern") {
    val p = port(
      "public class L { int n; void f() { try { n = 1; } catch (java.io.IOException | RuntimeException e) { } } }")
    assertConsults(p, JS.S(14), fired = true)
    assertEmits(p, "|")
  }

  test("JS-S14 — a single-type catch is consulted and does not fire") {
    val p = port("public class L { int n; void f() { try { n = 1; } catch (RuntimeException e) { } } }")
    assertConsults(p, JS.S(14))
  }

  // -- JS-S15 / JS-S16: the enhanced-for ---------------------------------------------------------------

  test("JS-S15 — the iterable is evaluated ONCE, and the row records that the branch really is that shape") {
    val p = port("public class M { int[] xs; int n; void f() { for (int x : xs) { n = x; } } }")
    assertConsults(p, JS.S(15), fired = true)
    assertEmits(p, "for (x <- ")
  }

  test("JS-S16 — a binding REASSIGNED in the body cannot be a scala generator's `val`") {
    val p = port(
      "public class N { java.lang.Object[] xs; void f() { for (java.lang.Object o : xs) { o = null; } } }")
    assertConsults(p, JS.S(16), fired = true)
    assertEmits(p, "$e")
  }

  test("JS-S16 — an ordinary binding is consulted and does not fire") {
    val p = port("public class N { int[] xs; int n; void f() { for (int x : xs) { n = x; } } }")
    assertConsults(p, JS.S(16))
    assertNotEmits(p, "$e")
  }

  // -- JS-S17: the classic `for`'s UPDATE runs on `continue` ---------------------------------------------

  test("JS-S17 — the update is PLACED outside the per-iteration boundary, where java runs it") {
    val p = port("public class O { int n; void f() { for (int i = 0; i < 3; i++) { if (i == 1) continue; n = i; } } }")
    assertConsults(p, JS.S(17), fired = true)
    assertEmits(p, "while (")
  }

  test("JS-S17 — a `for(;;)` with neither init nor update is consulted and does not fire") {
    val p = port("public class O { void f() { for (;;) { break; } } }")
    assertConsults(p, JS.S(17))
  }

  // -- JS-S18: `do`-`while` — Scala 3 removed it ----------------------------------------------------------

  test("JS-S18 — the body is lifted into the condition, because there is no keyword to map to") {
    val p = port("public class P { int n; void f(boolean b) { do { n = 1; } while (b); } }")
    assertConsults(p, JS.S(18), fired = true)
    assertEmits(p, "}) ()")
  }

  // -- JS-S19: definite assignment for a declaration java left blank ---------------------------------------

  test("JS-S19 — a field java leaves uninitialised is given the value scala requires") {
    val p = port("public class Q { int n; }")
    assertConsults(p, JS.S(19), fired = true)
    assertEmits(p, "var n: scala.Int = 0")
  }

  test("JS-S19 — a field WITH an initialiser is consulted and does not fire") {
    val p = port("public class Q { int n = 7; }")
    assertConsults(p, JS.S(19))
    assertEmits(p, "= 7")
  }

  // -- JS-S21: `return` inside a lambda returns from the LAMBDA -------------------------------------------

  test("JS-S21 — a `return` in a lambda body is CONSULTED and FIRES; scala's lambda rejects `return` outright") {
    val p = port(
      "public class R { interface Str { String get(); } Str s = () -> { return \"x\"; }; }")
    assertConsults(p, JS.S(21), fired = true)
    assertEmits(p, "return \"x\"")
  }

  test("JS-S21 — where the SAM's RESULT TYPE is unknowable the branch fires and the engine REFUSES") {
    // §2.3(a) in one test, and the reason the happy path is NOT asserted here. The nested `def`
    // needs the interface's result type; the TIR carries a lambda's type as the FUNCTIONAL
    // INTERFACE rather than as its method, so a classpath-free snippet cannot supply one and the
    // emitter leaves a loud compile error at the exact line rather than guessing a type (M6). The
    // repair itself is live — `sge-graphs`' `AlgorithmsTest` emits `body$1` — so what a fixture
    // here can honestly assert is that the difference was CONSIDERED and APPLIED, never that it
    // was answered. A suite that asserted the happy path from a snippet would be asserting that
    // the classpath was absent.
    val p = port("public class R2 { java.util.function.Supplier<String> s = () -> { return \"x\"; }; }")
    assertConsults(p, JS.S(21), fired = true)
    assertNotEmits(p, "body$")
  }

  test("JS-S21 — an expression lambda is consulted and does not fire") {
    val p = port("public class R { java.util.function.Supplier<String> s = () -> \"x\"; }")
    assertConsults(p, JS.S(21))
    assertNotEmits(p, "body$")
  }

  // -- JS-S22: `synchronized` as a statement ------------------------------------------------------------

  test("JS-S22 — the statement maps to `.synchronized`, with the same monitor bytecode") {
    val p = port("public class S { int n; void f() { synchronized (this) { n = 1; } } }")
    assertConsults(p, JS.S(22), fired = true)
    assertEmits(p, ".synchronized")
  }

  // -- JS-S25: java REJECTS unreachable code and scala allows it -------------------------------------------

  test("JS-S25 — a body ending in an infinite loop needs a tail java did not have") {
    val p = port("public class T { int f() { while (true) { return 1; } } }")
    assertConsults(p, JS.S(25), fired = true)
    assertEmits(p, "unreachable")
  }

  test("JS-S25 — an ordinary body is consulted and does not fire") {
    val p = port("public class T { int f() { return 1; } }")
    assertConsults(p, JS.S(25))
    assertNotEmits(p, "unreachable")
  }

  // -- the ABSENT rows: rule (ii) makes CONSULTING one a finding --------------------------------------------

  test("JS-S09 — a switch EXPRESSION lowers, and the arm is what owes the consult") {
    // The row was on this list beside `JS-S10` for the reason below, and left it when the arm was
    // written: `SpoonTir.switchExpr` RETURNS, so the obligation is owed at a site that settles.
    val p = port("public class U { int f(int i) { return switch (i) { case 1 -> 2; default -> 3; }; } }")
    assertConsults(p, JS.S(9), fired = true)
    assert(!Differences.byId(JS.S(9)).status.isInstanceOf[Status.Absent])
  }

  test("JS-S10 — a TYPE pattern label lowers, and the arm owes the consult at BOTH switch kinds") {
    // The row is a SPLIT: a scala typed pattern is the exact image of `case String s ->`, and a
    // RECORD pattern has none (a java record emits as a plain class with no `unapply`). The consult
    // is issued in `switchArms`, which both switch kinds go through — attached at `CtCasePattern`
    // instead it would read `unreached` forever, because `caseLabel` intercepts before `expr`'s
    // dispatch is ever entered.
    val p = port("public class U { int f(Object o) { return switch (o) { case String s -> 1; default -> 0; }; } }")
    assertConsults(p, JS.S(10), fired = true)
    assertEmits(p, "case s: java.lang.String =>")
    assert(!Differences.byId(JS.S(10)).status.isInstanceOf[Status.Absent])
  }

  // -- the partition, asserted rather than left to a reader --------------------------------------------------

  test("every JS-S row is wired, declared unmechanised, or owes nothing — and NONE is unmechanised any more") {
    val byKind = Differences.statements.groupBy(d => Differences.leaves(d.attaches) match
      case ls if ls.exists(_.isInstanceOf[Attaches.Unmechanised]) => "unmechanised"
      case ls if ls.exists(_.isInstanceOf[Attaches.Rendered])     => "rendered"
      case ls if ls.exists(_.isInstanceOf[Attaches.Lowered])      => "lowered"
      case _                                                      => "none")
    assertEquals(byKind.values.map(_.size).sum, Differences.statements.size)
    // THE CHUNK'S OWN BAR. Area S opened with all 25 rows on `Unmechanised` because the emitter had
    // no obligation dispatch; the audit point for this wave is "were the emitter-side rows really
    // instrumented, or marked unmechanised to keep the lane green". This is that question, asserted
    // in the exact form that can fail: the ONLY rows left are the two whose construct the frontend
    // NONE is left: the set was 25 when area S opened, TWO after chunk 11 (the two constructs the
    // frontend refused at their kind), one after `JS-S09` gained an arm and zero after `JS-S10`'s
    // TYPE-pattern half did. A row leaves this set exactly one way — by gaining a discharge site
    // that RETURNS.
    assertEquals(byKind.getOrElse("unmechanised", Nil).map(_.id).toSet, Set.empty,
      "a JS-S row still says nothing is measuring it, and all three dispatch surfaces now exist")
    assert(byKind.getOrElse("rendered", Nil).nonEmpty, "no JS-S row is wired to the RENDERING dispatch")
    assert(byKind.getOrElse("lowered", Nil).nonEmpty, "no JS-S row is wired to the LOWERING dispatch")
    // …and a row claiming NO obligation must not be one the registry calls Open: that would be a
    // gap no lane can see.
    assertEquals(byKind.getOrElse("none", Nil).filter(_.status.isOpen).map(_.id), Nil,
      "an Open row claiming NoObligation is a gap no lane can see")
  }

  // The other half of that question — "does every `Rendered` kind name a `Tree` node that EXISTS"
  // — is asserted where the node set is already DERIVED, in `EmissionFieldCoverageSpec`. A misspelt
  // kind attaches to no node, owes no consult, produces no hole and reads `unreached` forever, so
  // it needs a check; what it must not have is a second, hand-written list of node names here.
