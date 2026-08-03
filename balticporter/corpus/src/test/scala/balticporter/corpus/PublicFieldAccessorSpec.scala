package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.{Decision, DecisionLog, Pipeline, Program, RuleScope}
import balticporter.transform.{BeanExposureCheck, PublicFieldAccessorTransform}

/** A JAVA `public` FIELD IS NOT PUBLIC ON THE JVM ONCE IT IS SCALA — `ENGINE-LIMITS.md` K21 face 2.
  *
  * `class C { var a: Object }` emits a PRIVATE field plus `a()`/`a_$eq()`, so `getClass.getFields`
  * answers `[]` and no bean reader finds a property. The port compiles, every count is flat, and
  * the framework reads every property as ABSENT — which a library that defaults `null` then turns
  * into a plausible wrong answer rather than an error. A suite is the only instrument that sees it.
  *
  * What is asserted, and the last three are what a first cut gets wrong:
  *
  *   1. a public field in scope gains `getX`/`setX`, and a `final` one gains only the getter;
  *   2. a field java did NOT make public gains nothing — the phase reproduces java's surface, it
  *      does not invent one;
  *   3. out of scope nothing is emitted at all (an empty scope is the no-op) and the type is
  *      COUNTED instead, so a port can find the classes it should be naming;
  *   4. the getter BRIDGES an `Object` field through the run-time egress helper. This is the half a
  *      call-site rule cannot reach: a framework calls BACK IN through the accessor, and what it
  *      gets is whatever the field holds;
  *   5. an ANONYMOUS class is reached. It is the usual shape here — `new Inspectable() { public
  *      Date a; }` — and it lives inside a TERM, so a phase that only overrode the class hook would
  *      do nothing on the measured case with no error anywhere;
  *   6. a bean name java already declares is REFUSED and counted, never emitted twice;
  *   7. the bean capitalisation is `java.beans.Introspector`'s and not `capitalize` — `URL` stays
  *      `getURL`, or the accessor is one no framework looks for.
  */
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
    assert(out.contains("def getB(): java.lang.String"))
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

  test("an `Object` field's getter goes through the RUN-TIME egress bridge") {
    val (_, _, _, out) = ported(Shape)
    assert(clue(out).contains(
      "def getA(): java.lang.Object = balticporter.runtime.JavaCollections.Reified.toJavaValue(this.a)"),
      "a framework calls back IN through this accessor, one hop past the argument bridge")
    assert(out.contains("def getB(): java.lang.String = this.b"),
           "and a `String` cannot be a representation this engine introduced, so nothing is added")
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
    assert(clue(out).contains("def getTag(): java.lang.String"),
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
