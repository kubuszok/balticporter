package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.tir.{IdiomCandidate, IdiomCheck, IdiomKind, IdiomLog, IdiomVerdict, Origin, Sam, Tree}
import balticporter.transform.{BeanPropertyTransform, ReturnThisCensus, SamLambda, SamLambdaTransform}

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
        |}""".stripMargin, new SamLambdaTransform)
    assertIdiomConverts(p, IdiomKind.SamLambda, "C#make")
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
        |}""".stripMargin, new SamLambdaTransform)
    assertIdiomConverts(p, IdiomKind.SamLambda, "C#byLen")
  }

  test("an interface with TWO abstract methods is NotSam, and the refusal says so") {
    val p = portAll(List(
      "I.java" -> "interface I { void a(); void b(); }",
      "C.java" -> """class C { I make(final int n) { return new I() {
                    |  public void a() { System.out.println(n); }
                    |  public void b() {}
                    |}; } }""".stripMargin), new SamLambdaTransform)
    assertIdiomRefuses(p, IdiomKind.SamLambda, "NotSam", "C#make")
  }

  test("an anonymous subclass of a CLASS is NotSam — java's rule is interfaces only") {
    val p = portAll(List(
      "B.java" -> "abstract class B { abstract void go(); }",
      "C.java" -> """class C { B make(final int n) { return new B() {
                    |  void go() { System.out.println(n); }
                    |}; } }""".stripMargin), new SamLambdaTransform)
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
        |}""".stripMargin, new SamLambdaTransform)
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
        |}""".stripMargin, new SamLambdaTransform)
    assertIdiomRefuses(p, IdiomKind.SamLambda, "BodyNotSingle", "C#make")
  }

  test("an EMPTY anonymous body — the super-type-token idiom — is refused, not converted") {
    // `new I(){}` really has no members, which `AnonClass` states rather than confuses with "not an
    // anonymous class". There is no method to become a lambda.
    val p = port(
      """class C { Runnable make() { return new Runnable() {}; } }""", new SamLambdaTransform)
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
        |}""".stripMargin, new SamLambdaTransform)
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
        |}""".stripMargin, new SamLambdaTransform)
    assertIdiomRefuses(p, IdiomKind.SamLambda, "SelfReference", "C#make")
  }

  test("…and an inherited DEFAULT METHOD is refused too — `java.lang.Object` was never the whole list") {
    // The enumeration guard 4 rests on is "the members a bare reference can reach that a lambda
    // would re-resolve", and it was spelled as `java.lang.Object`'s. That is only the half every
    // anonymous class inherits UNCONDITIONALLY. A functional interface may also carry `default`
    // methods — `java.util.Comparator` ships six — and java binds a bare `helper()` inside the anon
    // to the INTERFACE's, through the anon instance. A lambda has no such member, so the emitted
    // call either resolves to nothing or, worse, SILENTLY re-resolves to a same-named member of the
    // enclosing class. Zero corpus sites; this fixture is the whole evidence.
    val p = portAll(List(
      "F.java" -> """interface F {
                    |  void go();
                    |  default int helper() { return 7; }
                    |}""".stripMargin,
      "C.java" -> """class C {
                    |  F make(final int b) {
                    |    return new F() { public void go() { System.out.println(helper() + b); } };
                    |  }
                    |}""".stripMargin), new SamLambdaTransform)
    assertIdiomRefuses(p, IdiomKind.SamLambda, "SelfReference", "C#make")
  }

  test("…and an inherited interface CONSTANT is answered by the QUALIFIER, not by this guard —\n" +
       "     which is why it CONVERTS and is still correct") {
    // The other half of what an interface contributes, and it was PREDICTED as a second face of the
    // defect above and MEASURED not to be one. A field declared in an interface is implicitly
    // `public static final` (JLS 9.3) and is inherited, so java binds a bare `K` inside the anon to
    // the interface's — but the frontend does not hand that over as a bare reference: it resolves
    // the implicit static access to `Select(Ident(F), F#K)`, so the emitted Scala names `F` and
    // re-resolves to the same constant with or without a lambda around it.
    //
    // Asserted on the EMITTED QUALIFIER and not merely on the conversion, because the qualifier IS
    // the mechanism: a `converts` assertion alone would go on passing if the frontend ever started
    // emitting the bare form, which is precisely the shape the guard next door exists for.
    val p = portAll(List(
      "F.java" -> """interface F {
                    |  int K = 3;
                    |  void go();
                    |}""".stripMargin,
      "C.java" -> """class C {
                    |  F make(final int b) {
                    |    return new F() { public void go() { System.out.println(K + b); } };
                    |  }
                    |}""".stripMargin), new SamLambdaTransform)
    assertIdiomConverts(p, IdiomKind.SamLambda, "C#make")
    assertEmits(p, "F.K")
  }

  test("…and a member of an ENCLOSING anonymous class is NOT one of those, however it is declared —\n" +
       "     java resolves a bare name INNERMOST-FIRST, and only the INNER anon's own members move") {
    // The cell that decides how wide guard 4's complement may be, and it is a corpus shape rather
    // than an invented one: this is `Pixmap.downloadFromUrl`. The inner `Runnable`'s body calls
    // `failed(t)` — a member of the OUTER anonymous class, DECLARED by the interface that one
    // implements. Read as "the owner is a type that does not lexically enclose the site" the guard
    // refuses it, because `L` is not an enclosing type; read as the ANON'S OWN ANCESTRY it converts,
    // because the inner `Runnable` does not inherit `failed` and java therefore bound it to the
    // enclosing instance — which a lambda around the inner one does not move. Measured at
    // `idiom(converted) 83 -> 82` on the libGDX base for the wide reading, on a site that was never
    // a defect.
    val p = portAll(List(
      "L.java" -> """interface L {
                    |  void handle(int code);
                    |  void failed(java.lang.Throwable t);
                    |}""".stripMargin,
      "C.java" -> """class C {
                    |  L make(final int b) {
                    |    return new L() {
                    |      public void handle(int code) {
                    |        java.lang.Runnable r = new java.lang.Runnable() {
                    |          public void run() {
                    |            try { System.out.println(code + b); }
                    |            catch (java.lang.Throwable t) { failed(t); }
                    |          }
                    |        };
                    |        r.run();
                    |      }
                    |      public void failed(java.lang.Throwable t) { t.printStackTrace(); }
                    |    };
                    |  }
                    |}""".stripMargin), new SamLambdaTransform)
    // the OUTER anon has two members, so it is `BodyNotSingle`; the INNER one is the subject here.
    assertIdiomConverts(p, IdiomKind.SamLambda, "C#make")
  }

  test("…and a NESTED functional interface's own QUALIFIER is not mistaken for one of those —\n" +
       "     an over-refusal here would decline a large share of the real population") {
    // The cell that keeps guard 4's complement honest. `Outer.F` reaches the body as an `Ident`
    // whose symbol's OWNER is `Outer` — a TYPE, and not one of the site's enclosing types — which
    // is exactly the shape the guard refuses. It is a TYPE REFERENCE, not a member reference, and a
    // type reference re-resolves identically under a lambda.
    val p = portAll(List(
      "Outer.java" -> """public class Outer {
                        |  public interface F { void go(); }
                        |  public static final int K = 3;
                        |}""".stripMargin,
      "C.java" -> """class C {
                    |  Outer.F make(final int b) {
                    |    return new Outer.F() { public void go() { System.out.println(Outer.K + b); } };
                    |  }
                    |}""".stripMargin), new SamLambdaTransform)
    assertIdiomConverts(p, IdiomKind.SamLambda, "C#make")
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
        |}""".stripMargin, new SamLambdaTransform)
    assertIdiomConverts(p, IdiomKind.SamLambda, "C#make")
  }

  // -------------------------------------------------------------------------------------------
  // guard 5 — INSTANCE IDENTITY, licensed by the specification's SILENCE
  // -------------------------------------------------------------------------------------------

  test("a NON-CAPTURING anonymous class is refused: a lambda's identity is UNSPECIFIED") {
    val p = port(
      """class C { Runnable make() { return new Runnable() {
        |  public void run() { System.out.println("hi"); }
        |}; } }""".stripMargin, new SamLambdaTransform)
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

  test("…and a bare reference to an ENCLOSING INSTANCE MEMBER is a capture too — the node that is\n" +
       "     not there, read at guard 5 instead of at guard 4") {
    // `isEnclosingBinding` asks for an owner whose DEFINITION is a `DefDef` — a local or a
    // parameter — so a bare reference to a MEMBER of an enclosing instance answers "not capturing",
    // and the site is refused under a reason that is not true: the lambda closes over that instance
    // and allocates at every evaluation, which is exactly what guard 5 is buying.
    //
    // The fixture is the `Pixmap.downloadFromUrl` shape with everything else taken away, because
    // that is what makes it reachable: `C.this.n` and a bare `n` on the ENCLOSING CLASS both arrive
    // as a `Tree.This` and were already counted. What does not is a member reached through the
    // frontend's receiver-dropping fallback (`SpoonTir.thisOf`) — here `failed(...)`, declared by
    // the interface the OUTER anonymous class implements, which arrives as `Tree.Ident(L#failed)`
    // owned by a TYPE.
    val p = portAll(List(
      "L.java" -> """interface L {
                    |  void handle(int code);
                    |  void failed(java.lang.Throwable t);
                    |}""".stripMargin,
      "C.java" -> """class C {
                    |  L make() {
                    |    return new L() {
                    |      public void handle(int code) {
                    |        java.lang.Runnable r = new java.lang.Runnable() {
                    |          public void run() { failed(null); }
                    |        };
                    |        r.run();
                    |      }
                    |      public void failed(java.lang.Throwable t) { t.printStackTrace(); }
                    |    };
                    |  }
                    |}""".stripMargin), new SamLambdaTransform)
    assertNoGuard(p, "NonCapturing")
    assertIdiomConverts(p, IdiomKind.SamLambda, "C#make")
  }

  test("…and a STATIC member is NOT a capture — nothing is closed over, so the identity gap stands") {
    // The cell that keeps the widening honest, and the reason the test is on the `static` FLAG and
    // not on the owner's kind. A `static` member is reached without an instance, so a lambda naming
    // it captures nothing and MAY be the same object at two evaluations — which is precisely the
    // unspecified identity guard 5 refuses on.
    val p = portAll(List(
      "K.java" -> "class K { static int n = 1; }",
      "C.java" -> """class C {
                    |  Runnable make() {
                    |    return new Runnable() { public void run() { System.out.println(K.n); } };
                    |  }
                    |}""".stripMargin), new SamLambdaTransform)
    assertIdiomRefuses(p, IdiomKind.SamLambda, "NonCapturing", "C#make")
  }

  test("an OUTER `this` capture counts as a capture — the lambda closes over the instance") {
    val p = port(
      """class C {
        |  int n = 1;
        |  Runnable make() {
        |    return new Runnable() { public void run() { System.out.println(C.this.n); } };
        |  }
        |}""".stripMargin, new SamLambdaTransform)
    assertNoGuard(p, "NonCapturing")
  }

  /** NO `SamLambda` refusal was filed under this GUARD.
    *
    * Spelled here rather than reached for from the testkit because the testkit's nearest helper —
    * `assertIdiomIgnores` — matches its third argument against the SUBJECT, and the two calls that
    * passed a guard name to it were asserting *no candidate has a subject containing
    * "NonCapturing"*, which no candidate ever could. Two vacuous assertions, both in the cell that
    * decides whether guard 5 declines the population it is meant to. */
  private def assertNoGuard(p: balticporter.testkit.Ported, guard: String)(using munit.Location): Unit =
    val hits = p.idioms.all.filter(_.verdict match
      case IdiomVerdict.Refused(g, _) => g == guard
      case _                          => false)
    if hits.nonEmpty then
      fail(s"a refusal under guard `$guard` was filed and should not have been:\n" +
        hits.map("  " + _.render).mkString("\n"))

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
                    |}; } }""".stripMargin), new SamLambdaTransform)
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

  test("the CENSUS phase is EMISSION-INERT — the tree it hands back IS the tree it got") {
    // The wave-0 property, still true of the ONE phase that is still a census. Neither the SAM
    // phase nor the bean collapse is one any more: each wired its transformer, and a census beside
    // one is a second answer to its own question (§4.6). What replaces this assertion for those two
    // lanes is the refusal assertion in each transformer's own suite —
    // `SamLambdaTransformSpec`'s "every REFUSAL leaves the anonymous class BYTE-IDENTICAL" and
    // `BeanPropertySpec`'s "a refused collapse degenerates to the def-pair, byte for byte" — which
    // is the same property asked of the sites the transformer declines.
    val src =
      """class C {
        |  int n = 1;
        |  C self() { return this; }
        |  Runnable make() { return new Runnable() { public void run() { System.out.println(n); } }; }
        |}""".stripMargin
    val bare  = port(src)
    val censused = port(src, new ReturnThisCensus)
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
    assertEquals(new SamLambdaTransform().idiomKinds, Set(IdiomKind.SamLambda))
    assertEquals(new ReturnThisCensus().idiomKinds, Set(IdiomKind.NarrowedReturn))
    assertEquals(new BeanPropertyTransform().idiomKinds, Set(IdiomKind.BeanCollapse))
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
