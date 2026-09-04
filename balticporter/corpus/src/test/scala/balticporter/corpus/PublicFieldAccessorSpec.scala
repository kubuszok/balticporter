package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.{Decision, DecisionLog, Pipeline, Program, RuleScope}
import balticporter.transform.{BeanExposureCheck, CollectionsTransform, PublicFieldAccessorTransform}

/** A JAVA `public` FIELD IS NOT PUBLIC ON THE JVM ONCE IT IS SCALA — `ENGINE-LIMITS.md` K21 face 2. */
class PublicFieldAccessorSpec extends PortSuite:

  private def ported(source: String, scope: RuleScope = RuleScope.Everywhere())
      : (DecisionLog, Program, PublicFieldAccessorTransform, String) =
    val ph             = new PublicFieldAccessorTransform(scope)
    val (after, notes) = Pipeline.runTraced(SpoonTir.fromSource(source), List(ph))
    (notes, after, ph, new TirEmitter(after, notes = notes).emit)

  private def rows(ph: PublicFieldAccessorTransform, p: Program) = ph.exposure(p.units)

  private val Shape =
    """package demo;
      |class T {
      |  public Object a;
      |  public final String b = "b";
      |  private Object hidden;
      |  Object packaged;
      |  protected Object prot;
      |  public static Object statik;
      |}
      |""".stripMargin

  // -------------------------------------------------------------------------
  // 1-2. what java made public, and only that
  // -------------------------------------------------------------------------

  test("a public field gains `getX`/`setX`; a FINAL one gains only the getter") {
    val (_, _, _, out) = ported(Shape)
    assert(clue(out).contains("def getA(): java.lang.Object"))
    assert(out.contains("def setA(v: java.lang.Object)"))
    assert(out.contains("def getB(): java.lang.Object"),
           "the GETTER's type is the phase's own, never the field's — see test 4")
    assert(!out.contains("def setB("), "java had no setter for a `final` field and neither has this")
  }

  test("a field java did NOT make public gains nothing — the phase reproduces java's surface") {
    val (_, _, _, out) = ported(Shape)
    for absent <- List("getHidden", "getPackaged", "getProt", "getStatik") do
      assert(!clue(out).contains(absent), s"$absent is not on java's class-file surface")
  }

  // -------------------------------------------------------------------------
  // 3. the no-op, and what stands in its place
  // -------------------------------------------------------------------------

  test("out of scope nothing is emitted — an empty `Only` is the no-op with no code path") {
    val (log, _, _, out) = ported(Shape, RuleScope.Only(Set.empty))
    assert(!clue(out).contains("def getA"))
    assertEquals(log.all.count(_.kind == Decision.Kind.BeanAccessor), 0)
  }

  test("…and the type is COUNTED instead — one row per TYPE, which is the review list") {
    val (_, after, ph, _) = ported(Shape, RuleScope.Only(Set.empty))
    val fs = rows(ph, after).filter(_.issue == BeanExposureCheck.Issue.Unexposed)
    assertEquals(clue(fs).size, 1, "the question `is this class read reflectively?` is asked once " +
      "per class, so a row per FIELD would bury it")
    assertEquals(fs.head.detail, "a,b", "and it names the fields, so a reader can judge without " +
      "opening the java")
  }

  test("…and a type IN scope is exposed rather than counted — a closed seam is not a residue") {
    val (_, after, ph, _) = ported(Shape)
    assertEquals(clue(rows(ph, after)), Nil)
  }

  // -------------------------------------------------------------------------
  // 4. the bridge, which is the half a call-site rule cannot reach
  // -------------------------------------------------------------------------

  test("EVERY field's getter goes through the RUN-TIME egress bridge, at `java.lang.Object`") {
    val (_, _, _, out) = ported(Shape)
    assert(clue(out).contains(
      "def getA(): java.lang.Object = balticporter.runtime.JavaCollections.Reified.toJavaValue(this.a)"),
      "a framework calls back IN through this accessor, one hop past the argument bridge")
    assert(out.contains(
      "def getB(): java.lang.Object = balticporter.runtime.JavaCollections.Reified.toJavaValue(this.b)"),
      "…and a field typed as anything else takes the SAME accessor: java declared a FIELD and not a " +
      "getter, so this signature is the phase's own and its only reader is the framework, reading " +
      "the RUNTIME value. `toJavaValue` is the identity on a `String`, so this is behaviour-" +
      "identical where the old `Object`-only bridge was right — and correct where it was not, at a " +
      "field whose type a retyping phase moved (§4.56 forbids this phase asking which)")
  }

  test("…including a field whose type a RETYPING phase moved — the case the old rule missed") {
    // K21 face 2's second half. `public Map<String,String> some` is a `scala.collection.mutable.Map`
    // by the time this phase sees it, so the getter handed jackson the map's INTERNALS, exactly as
    // K21 face 1's argument seam did one hop earlier. Asserted through the SETTER as well, because
    // the two types are now deliberately different and a reader has to be able to see which is which.
    val ph  = new PublicFieldAccessorTransform(RuleScope.Everywhere())
    val src =
      """package demo;
        |import java.util.Map;
        |class T { public Map<String,String> some; }
        |""".stripMargin
    val (after, notes) =
      Pipeline.runTraced(SpoonTir.fromSource(src), List(new CollectionsTransform(), ph))
    val out = new TirEmitter(after, notes = notes).emit
    assert(clue(out).contains("var some: scala.collection.mutable.Map"),
           "the premise: the retyping really did move this field, so the test is not vacuous")
    assert(out.contains(
      "def getSome(): java.lang.Object = balticporter.runtime.JavaCollections.Reified.toJavaValue(this.some)"),
      "…and the accessor a bean reader sees is java's representation of it, not the scala map")
  }

  // -------------------------------------------------------------------------
  // 5. the anonymous class
  // -------------------------------------------------------------------------

  test("an ANONYMOUS class inside a method body is reached — it is the usual shape here") {
    val (_, _, _, out) = ported(
      """package demo;
        |class T {
        |  static Object make() {
        |    return new Object() { public String tag = "x"; };
        |  }
        |}
        |""".stripMargin)
    assert(clue(out).contains("def getTag(): java.lang.Object"),
           "an anonymous class lives in a TERM, and a walk over class bodies finds none of them")
  }

  // -------------------------------------------------------------------------
  // 6. the refusal
  // -------------------------------------------------------------------------

  test("a bean name java already declares is REFUSED and counted, never emitted twice") {
    val (_, after, ph, out) = ported(
      """package demo;
        |class T {
        |  public Object mapper;
        |  public Object getMapper() { return mapper; }
        |}
        |""".stripMargin)
    assertEquals(clue(out).sliding("def getMapper".length).count(_ == "def getMapper"), 1,
                 "a second one is a duplicate-definition error the port cannot recover from")
    val fs = rows(ph, after).filter(_.issue == BeanExposureCheck.Issue.NameTaken)
    assertEquals(clue(fs).size, 1)
    assert(fs.head.subject.endsWith("#mapper"), clue(fs.head.subject))
  }

  // -------------------------------------------------------------------------
  // 7. the record, and the capitalisation
  // -------------------------------------------------------------------------

  test("the accessor is RECORDED and noted — an invented member has no upstream line behind it") {
    val (log, _, _, out) = ported(Shape)
    val ds = log.all.filter(_.kind == Decision.Kind.BeanAccessor)
    assertEquals(clue(ds).size, 2)
    assertEquals(ds.map(_.reason.className).distinct, List("configured"))
    assert(clue(out).contains("/* porter: bean-accessor reason=configured"),
           "the reader is looking at a `def getA()` the source map cannot answer for (§4.575)")
  }

  test("the bean suffix is `java.beans.Introspector`'s, not `capitalize`") {
    assertEquals(PublicFieldAccessorTransform.beanSuffix("url"), "Url")
    assertEquals(PublicFieldAccessorTransform.beanSuffix("URL"), "URL",
                 "two leading capitals keep their spelling — `getURL` is what a bean reader looks " +
                   "for, and `getURL` is what `decapitalize` inverts")
    assertEquals(PublicFieldAccessorTransform.beanSuffix("a"), "A")
  }

  test("a name `decapitalize` cannot INVERT is REFUSED and counted — the accessor would be for nobody") {
    // `eMail` -> `getEMail`, and `Introspector.decapitalize("EMail")` is `"EMail"` (two leading
    // capitals keep their spelling), so the property a bean reader registers is `EMail` and the one
    // the framework asks for is `eMail`. That is K21 face 2's OWN failure class arriving through
    // the repair for it: an accessor is emitted, the port compiles, every count is flat, and the
    // lookup reads absent. `lowerUpper` is not exotic — `eTag`, `xAxis`, `iValue`.
    val (_, after, ph, out) = ported(
      """package demo;
        |class T {
        |  public String eMail;
        |  public String name;
        |}
        |""".stripMargin)
    assert(!clue(out).contains("getEMail"),
           "an accessor no bean reader will look for is worse than none — it reads as coverage")
    assert(out.contains("def getName(): java.lang.Object"),
           "and the field beside it is unaffected: this is a refusal about ONE name")
    val fs = rows(ph, after).filter(_.issue == BeanExposureCheck.Issue.NameUnreachable)
    assertEquals(clue(fs).size, 1)
    assert(fs.head.subject.endsWith("#eMail"), clue(fs.head.subject))
    assert(clue(BeanExposureCheck.Issue.classification(BeanExposureCheck.Issue.NameUnreachable)).contains("§1(a)"))
  }

  test("…and the same field gets NO accessors at all, never a getter without a setter") {
    val (_, _, _, out) = ported(
      """package demo;
        |class T { public String eMail; }
        |""".stripMargin)
    assert(!clue(out).contains("def get"), out)
    assert(!out.contains("def set"), out)
  }

  test("`invertible` is exactly the round trip — a name it admits IS what a bean reader registers") {
    // The invariant the refusal exists to hold, stated as the round trip rather than as a list of
    // shapes: what a bean reader registers is `decapitalize(suffix)`, and a framework handed this
    // port's object asks for the FIELD's name. Every name is on one side of the line or the other,
    // and the predicate is the line.
    for f <- List("url", "URL", "a", "A", "name", "aB", "eMail", "isbn", "ISBNCode", "") do
      val back = PublicFieldAccessorTransform.decapitalize(PublicFieldAccessorTransform.beanSuffix(f))
      assertEquals(PublicFieldAccessorTransform.invertible(f), back == f, clue(f))
    assert(PublicFieldAccessorTransform.invertible("url"))
    assert(PublicFieldAccessorTransform.invertible("URL"))
    assert(!PublicFieldAccessorTransform.invertible("eMail"))
    assert(!PublicFieldAccessorTransform.invertible("A"), "single upper decapitalises to `a`")
  }

  test("two fields cannot COLLIDE on one bean name once the round trip is the gate") {
    // The audit's third minor, and the answer is that it cannot arise rather than that it is
    // handled: `decapitalize(beanSuffix(f)) == f` makes `beanSuffix` INJECTIVE on the names this
    // phase admits, so two admitted fields never want the same `getX`. `a` and `A` — the shape that
    // would collide — are separated because `A` is not invertible (it decapitalises to `a`), so it
    // is refused and only one `def getA` is ever minted.
    val (_, after, ph, out) = ported(
      """package demo;
        |class T {
        |  public String a;
        |  public String A;
        |}
        |""".stripMargin)
    assertEquals(clue(out).sliding("def getA(".length).count(_ == "def getA("), 1, out)
    assertEquals(clue(rows(ph, after)).map(_.issue), List(BeanExposureCheck.Issue.NameUnreachable))
  }

  test("an INHERITED accessor is seen — the screen climbs the parents this program declares") {
    // `memberNames` read the type's OWN body, so a subclass whose PARENT declares `getMapper()`
    // minted a second one: a bare typer error with no finding and no §1 classification behind it.
    // An ancestor outside the program is a class file this pass cannot read, and K21 states that
    // half rather than guessing at it.
    val (_, after, ph, out) = ported(
      """package demo;
        |class Base { public Object getMapper() { return null; } }
        |class Sub extends Base { public Object mapper; }
        |""".stripMargin)
    assertEquals(clue(out).sliding("def getMapper".length).count(_ == "def getMapper"), 1, out)
    val fs = rows(ph, after).filter(_.issue == BeanExposureCheck.Issue.NameTaken)
    assertEquals(clue(fs).size, 1)
    assert(fs.head.subject.endsWith("#mapper"), clue(fs.head.subject))
  }
