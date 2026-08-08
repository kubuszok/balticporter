package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.tir.{IdiomCandidate, IdiomCheck, IdiomKind, IdiomLog, IdiomVerdict, Origin, Sam, Tree}
import balticporter.transform.{BeanCollapseCensus, ReturnThisCensus, SamLambda, SamLambdaCensus}

/** WAVE 0 OF THE IDIOM LAYER — the census phases, and the three lanes they feed.
  *
  * ==What is being asserted, and why it is asserted here rather than on emitted text==
  * These phases are EMISSION-INERT by construction: `run` returns its argument, and the wave's own
  * gate is 0 member digests on every port. So the emitted text says nothing about whether they
  * work, and a spec written against it could not tell a census that classified every site correctly
  * from one that never ran. What carries the meaning is the CANDIDATE LOG — one row per site
  * considered, with the guard that declined it — which is exactly the surface an idiom transform's
  * safety argument rests on (`DESIGN.md` §8.15: a refusal enumeration, not a suite result).
  *
  * ==And the fixtures are the evidence for the guards, not the corpus==
  * `JS-E06`'s third commit is the precedent: a defect found by the test for the cell NEXT DOOR, at
  * zero corpus sites. Every guard below has a fixture whose shape is chosen to fail if the guard
  * were removed, including the two nobody would write a corpus case for — the qualified outer
  * `this` that must NOT be refused, and the non-capturing lambda that must be.
  */
class IdiomCensusSpec extends PortSuite:

  // -------------------------------------------------------------------------------------------
  // guard 1/2 — is it a SAM at all, and was the class file readable
  // -------------------------------------------------------------------------------------------

  test("a java.lang.Runnable anonymous class with a capture is what the transformer will convert") {
    val p = port(
      """class C {
        |  Runnable make(final String s) {
        |    return new Runnable() { public void run() { System.out.println(s); } };
        |  }
        |}""".stripMargin, new SamLambdaCensus)
    // filed under PhaseNotEnabled, which is the wave-0 denominator: NOT a property of the site.
    assertIdiomRefuses(p, IdiomKind.SamLambda, "PhaseNotEnabled", "C#make")
  }

  test("java's SAM rule EXCLUDES java.lang.Object's public methods — a Comparator IS one") {
    // The cell that would silently break the whole transformer: `java.util.Comparator` redeclares
    // `equals(Object)` beside `compare`, so a naive abstract-method count says TWO and every
    // Comparator in every corpus reads as `NotSam`. JLS 9.8 excludes a method override-equivalent
    // to a public method of `java.lang.Object`, which is exactly why Comparator is a functional
    // interface, and this pins that the oracle applies the exclusion.
    val p = port(
      """import java.util.Comparator;
        |class C {
        |  Comparator<String> byLen(final int bias) {
        |    return new Comparator<String>() {
        |      public int compare(String a, String b) { return a.length() - b.length() + bias; }
        |    };
        |  }
        |}""".stripMargin, new SamLambdaCensus)
    assertIdiomRefuses(p, IdiomKind.SamLambda, "PhaseNotEnabled", "C#byLen")
  }

  test("an interface with TWO abstract methods is NotSam, and the refusal says so") {
    val p = portAll(List(
      "I.java" -> "interface I { void a(); void b(); }",
      "C.java" -> """class C { I make(final int n) { return new I() {
                    |  public void a() { System.out.println(n); }
                    |  public void b() {}
                    |}; } }""".stripMargin), new SamLambdaCensus)
    assertIdiomRefuses(p, IdiomKind.SamLambda, "NotSam", "C#make")
  }

  test("an anonymous subclass of a CLASS is NotSam — java's rule is interfaces only") {
    val p = portAll(List(
      "B.java" -> "abstract class B { abstract void go(); }",
      "C.java" -> """class C { B make(final int n) { return new B() {
                    |  void go() { System.out.println(n); }
                    |}; } }""".stripMargin), new SamLambdaCensus)
    assertIdiomRefuses(p, IdiomKind.SamLambda, "NotSam", "C#make")
  }

  test("an UNREADABLE class file refuses under its own guard and is never silently `NotSam`") {
    // §4.6 read literally: the default must not be indistinguishable from a real answer. Both
    // answers lead to the same ACTION here, which is precisely why they must be different ROWS —
    // a port whose classpath is incomplete would otherwise read as a port with no SAM sites.
    val anon = Tree.AnonClass(balticporter.tir.SymId(1), Nil, Origin.synthetic, Nil, Sam.Answer.Unreadable)
    assertEquals(anon.sam, Sam.Answer.Unreadable)
    // …and the default a hand-built tree carries is that same conservative arm.
    assertEquals(Tree.AnonClass(balticporter.tir.SymId(1), Nil, Origin.synthetic).sam, Sam.Answer.Unreadable)
  }

  // -------------------------------------------------------------------------------------------
  // guard 3 — the body is exactly the one method
  // -------------------------------------------------------------------------------------------

  test("a body with a FIELD beside the method is refused: a lambda has nowhere to put it") {
    val p = port(
      """class C {
        |  Runnable make(final String s) {
        |    return new Runnable() {
        |      int calls = 0;
        |      public void run() { calls++; System.out.println(s + calls); }
        |    };
        |  }
        |}""".stripMargin, new SamLambdaCensus)
    assertIdiomRefuses(p, IdiomKind.SamLambda, "BodyNotSingle", "C#make")
  }

  test("a body with a HELPER method beside the SAM method is refused for the same reason") {
    val p = port(
      """class C {
        |  Runnable make(final String s) {
        |    return new Runnable() {
        |      private String hi() { return "hi " + s; }
        |      public void run() { System.out.println(hi()); }
        |    };
        |  }
        |}""".stripMargin, new SamLambdaCensus)
    assertIdiomRefuses(p, IdiomKind.SamLambda, "BodyNotSingle", "C#make")
  }

  test("an EMPTY anonymous body — the super-type-token idiom — is refused, not converted") {
    // `new I(){}` really has no members, which `AnonClass` states rather than confuses with "not an
    // anonymous class". There is no method to become a lambda.
    val p = port(
      """class C { Runnable make() { return new Runnable() {}; } }""", new SamLambdaCensus)
    assertIdiomRefuses(p, IdiomKind.SamLambda, "BodyNotSingle", "C#make")
  }

  // -------------------------------------------------------------------------------------------
  // guard 4 — `this` binds to the ANON in java and to the ENCLOSING class in scala
  // -------------------------------------------------------------------------------------------

  test("`this` reaching an ANON member is refused THROUGH A NODE THAT IS NOT THERE") {
    // The face a guard reading `Tree.This` cannot see, and the reason guard 4 is written on the
    // OWNER instead. `this.toString()` inside an anonymous `Runnable` arrives as a bare
    // `Tree.Ident(java.lang.Object#toString)` — the frontend drops the receiver on purpose, because
    // Spoon types it as the anonymous class whatever the member's real owner is, and scala's
    // LEXICAL resolution then lands on the same member java chose. Inside a LAMBDA it lands on the
    // ENCLOSING object instead: valid scala, green compile, printing a different object. Zero
    // corpus sites; this fixture is the whole evidence.
    val p = port(
      """class C {
        |  Runnable make(final String s) {
        |    return new Runnable() {
        |      public void run() { System.out.println(this.toString() + s); }
        |    };
        |  }
        |}""".stripMargin, new SamLambdaCensus)
    assertIdiomRefuses(p, IdiomKind.SamLambda, "SelfReference", "C#make")
  }

  test("…and the SAME shape with NO `this` written at all — `toString()` bare — refuses too") {
    // java resolves a bare `toString()` inside an anonymous class to the ANON's, exactly as if
    // `this.` had been written (JLS 15.12.1). The two spellings must not disagree here, and a guard
    // keyed on the source's `this` keyword would let this one through.
    val p = port(
      """class C {
        |  Runnable make(final String s) {
        |    return new Runnable() {
        |      public void run() { System.out.println(toString() + s); }
        |    };
        |  }
        |}""".stripMargin, new SamLambdaCensus)
    assertIdiomRefuses(p, IdiomKind.SamLambda, "SelfReference", "C#make")
  }

  test("…and a QUALIFIED OUTER `this` is NOT refused — the test is on the SYMBOL, not the node") {
    // The cell that decides whether the transformer is worth having: `Outer.this.field` is most of
    // the real population, and a guard written on the node KIND would decline every one of them.
    val p = port(
      """class C {
        |  int n = 1;
        |  Runnable make() {
        |    return new Runnable() { public void run() { System.out.println(C.this.n); } };
        |  }
        |}""".stripMargin, new SamLambdaCensus)
    assertIdiomRefuses(p, IdiomKind.SamLambda, "PhaseNotEnabled", "C#make")
  }

  // -------------------------------------------------------------------------------------------
  // guard 5 — INSTANCE IDENTITY, licensed by the specification's SILENCE
  // -------------------------------------------------------------------------------------------

  test("a NON-CAPTURING anonymous class is refused: a lambda's identity is UNSPECIFIED") {
    val p = port(
      """class C { Runnable make() { return new Runnable() {
        |  public void run() { System.out.println("hi"); }
        |}; } }""".stripMargin, new SamLambdaCensus)
    assertIdiomRefuses(p, IdiomKind.SamLambda, "NonCapturing", "C#make")
  }

  test("…and the refusal's WHY cites the specification, never a measured JDK") {
    // Not decoration: written the other way round ("HotSpot caches, so refuse") the guard reads as
    // removable the day somebody measures a JDK that does not cache — which is exactly backwards,
    // because the next JDK is not bound by that measurement.
    val why = SamLambda.Guard.NonCapturing.why
    assert(clue(why).contains("UNSPECIFIED"))
    assert(why.contains("LambdaMetafactory"))
    assert(!why.toLowerCase.contains("hotspot"))
  }

  test("an OUTER `this` capture counts as a capture — the lambda closes over the instance") {
    val p = port(
      """class C {
        |  int n = 1;
        |  Runnable make() {
        |    return new Runnable() { public void run() { System.out.println(C.this.n); } };
        |  }
        |}""".stripMargin, new SamLambdaCensus)
    assertIdiomIgnores(p, IdiomKind.SamLambda, "NonCapturing")
  }

  // -------------------------------------------------------------------------------------------
  // guard 6 — serialization
  // -------------------------------------------------------------------------------------------

  test("a SERIALIZABLE functional interface is refused, and NOT as `NotSam`") {
    // Two different facts must not share one constructor: the type IS a functional interface and
    // the port declines the CONVERSION. Reported as `NotSam` a reader would look for a second
    // abstract method that is not there.
    val p = portAll(List(
      "S.java" -> "interface S extends java.io.Serializable { int f(int x); }",
      "C.java" -> """class C { S make(final int b) { return new S() {
                    |  public int f(int x) { return x + b; }
                    |}; } }""".stripMargin), new SamLambdaCensus)
    assertIdiomRefuses(p, IdiomKind.SamLambda, "Serializable", "C#make")
  }

  // -------------------------------------------------------------------------------------------
  // the `return this` census — the go/no-go split, and its POPULATION
  // -------------------------------------------------------------------------------------------

  test("a fluent setter returning the DECLARING class is SelfTyped") {
    val p = port(
      """class B { int x; B withX(int v) { this.x = v; return this; } }""", new ReturnThisCensus)
    assertIdiomRefuses(p, IdiomKind.NarrowedReturn, "SelfTyped", "B#withX")
  }

  test("…and one returning a STRICT ANCESTOR is AncestorTyped — the bucket a wave would be FOR") {
    val p = portAll(List(
      "A.java" -> "class A { }",
      "B.java" -> "class B extends A { int x; A withX(int v) { this.x = v; return this; } }"),
      new ReturnThisCensus)
    assertIdiomRefuses(p, IdiomKind.NarrowedReturn, "AncestorTyped", "B#withX")
  }

  test("a method that answers `this` on one path and something else on another is NotAlwaysThis") {
    val p = port(
      """class B {
        |  B other;
        |  B pick(boolean b) { if (b) { return this; } return this.other; }
        |}""".stripMargin, new ReturnThisCensus)
    assertIdiomRefuses(p, IdiomKind.NarrowedReturn, "NotAlwaysThis", "B#pick")
  }

  test("THE POPULATION is `return this`, never every method — a census's denominator must be one\n" +
       "     a reader recognises") {
    // The wrong denominator is not a cosmetic problem: filed for every `DefDef`, the lane reports
    // thousands of rows saying *this method does not return `this`*, which is true of almost every
    // method ever written, and a reader stops reading the number.
    val p = port(
      """class B {
        |  int plain(int v) { return v + 1; }
        |  static B mk() { return new B(); }
        |  B self() { return this; }
        |}""".stripMargin, new ReturnThisCensus)
    assertIdiomConsiders(p, IdiomKind.NarrowedReturn, "B#self")
    assertIdiomIgnores(p, IdiomKind.NarrowedReturn, "B#plain")
    assertIdiomIgnores(p, IdiomKind.NarrowedReturn, "B#mk")
  }

  // -------------------------------------------------------------------------------------------
  // the phases are INERT, and the lanes report apart
  // -------------------------------------------------------------------------------------------

  test("both census phases are EMISSION-INERT — the tree they hand back IS the tree they got") {
    val src =
      """class C {
        |  int n = 1;
        |  C self() { return this; }
        |  Runnable make() { return new Runnable() { public void run() { System.out.println(n); } }; }
        |}""".stripMargin
    val bare  = port(src)
    val censused = port(src, new SamLambdaCensus, new ReturnThisCensus)
    assertEquals(censused.out, bare.out)
    assert(clue(censused.idioms.size) > 0, "the phases must be inert, not idle")
  }

  test("the three lanes are DISJOINT and their union is the log — `refused = 0` cannot be held by\n" +
       "     converting nothing") {
    val log = new IdiomLog
    log.record(IdiomCandidate(IdiomKind.SamLambda, IdiomVerdict.Converted, "a#m", "x", Origin.synthetic))
    log.record(IdiomCandidate(IdiomKind.SamLambda, IdiomVerdict.Refused("NotSam", "…"), "b#m", "x",
      Origin.synthetic))
    log.record(IdiomCandidate(IdiomKind.SamLambda, IdiomVerdict.Residue("u"), "c#m", "x", Origin.synthetic))
    val sizes = IdiomCheck.Lanes.map(l => IdiomCheck.findings(log, l).size)
    assertEquals(sizes, List(1, 1, 1))
    assertEquals(sizes.sum, log.size)
  }

  test("the DENOMINATOR is recomputed and printed beside the numerator, per kind") {
    val log = new IdiomLog
    log.record(IdiomCandidate(IdiomKind.SamLambda, IdiomVerdict.Converted, "a#m", "x", Origin.synthetic))
    log.record(IdiomCandidate(IdiomKind.SamLambda, IdiomVerdict.Refused("NotSam", "…"), "b#m", "x",
      Origin.synthetic))
    val s = IdiomCheck.summary(log)
    assert(clue(s).contains("SamLambda: 2 considered, 1 converted, 1 refused"))
    // a kind that never ran has no row at all: "no phase" and "a phase that found nothing" are two
    // different facts and the summary must not average them.
    assert(!s.contains("BeanCollapse"))
  }

  test("a phase that RAN and found nothing prints a zero; a phase that is ABSENT prints no row") {
    // The two are different facts and the report must not average them: a census whose population
    // fell to zero is exactly what a conversion regression looks like from here, and no other
    // instrument can see it.
    val empty = new IdiomLog
    assertEquals(IdiomCheck.summary(empty), "IDIOM: this pipeline carries no idiom phase")
    val ran = IdiomCheck.summary(empty, Set(IdiomKind.SamLambda))
    assert(clue(ran).contains("SamLambda: 0 considered"))
    assert(!ran.contains("NarrowedReturn"))
  }

  test("…and each census phase DECLARES the kind it files, so that zero can be printed at all") {
    assertEquals(new SamLambdaCensus().idiomKinds, Set(IdiomKind.SamLambda))
    assertEquals(new ReturnThisCensus().idiomKinds, Set(IdiomKind.NarrowedReturn))
    assertEquals(new BeanCollapseCensus(Map.empty).idiomKinds, Set(IdiomKind.BeanCollapse))
  }

  test("every lane carries a §1 CLASSIFICATION — an error an agent cannot classify costs a full\n" +
       "     investigation (§4.45)") {
    IdiomCheck.Lanes.foreach { l =>
      val c = IdiomCheck.classification(l)
      assert(clue(c).startsWith("§1(a) ENGINE"), s"$l does not classify itself")
      assert(!c.startsWith("unknown"))
    }
  }

  test("every SamLambda guard has a WHY that says whether the refusal is permanent") {
    SamLambda.Guard.values.foreach { g =>
      val w = g.why
      assert(clue(w).nonEmpty, s"$g has no why")
      assert(w.contains("PERMANENT") || w.contains("NOT a statement") ||
             w.contains("does not implement") || w.contains("engine defect"),
        s"$g's why does not say whether it is permanent: $w")
    }
  }
