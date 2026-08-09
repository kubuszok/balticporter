package balticporter.corpus

import balticporter.catalog.Platform
import balticporter.core.PolicyIssue
import balticporter.testkit.PortFixture
import balticporter.tir.*
import balticporter.transform.RemediationTransform

/** THE PORTABILITY MENU, at each of its answers.
  *
  * Every case here is silent by default. A selection that binds perfectly and does nothing produces
  * the same emitted Scala, the same compile and the same member digests as one that worked; a
  * selection that fires produces a smaller port with no error anywhere to say why. So the assertions
  * are on the LEDGER — what was applied, what was declined and which guard declined it — and on the
  * tree, which is the only pair that can tell the two apart (`CLAUDE.md` §3, §5's drain rule).
  *
  * The fixture is deliberately the shape `Remediator`'s first template is about: a JVM-only API used
  * from exactly one declared type, once with no other referrer (the HIGH grade) and once with one
  * (the MEDIUM grade). That difference is the whole of the drop's precondition, and it is a fact
  * about the program rather than about any library.
  */
class RemediationMenuSpec extends munit.FunSuite:

  /** `java.lang.reflect.` is a rule every target list asks about, so the fixture does not depend on
    * which platforms a spec declares. */
  private val Chokepoint =
    """package com.demo;
      |public class Reflector {
      |  public static Object make(Class<?> c) throws Exception {
      |    return c.getDeclaredConstructor().newInstance();
      |  }
      |}""".stripMargin

  private val Referrer =
    """package com.demo;
      |public class Uses {
      |  public Object go(Class<?> c) throws Exception { return Reflector.make(c); }
      |}""".stripMargin

  private def unitNames(p: Program): Set[String] =
    p.units.flatMap(u => p.symbolOf(u.symbol).map(_.fullName)).toSet

  // -------------------------------------------------------------------------------------------
  // substitutions-drop — the HIGH grade, and only the HIGH grade
  // -------------------------------------------------------------------------------------------

  test("a HIGH chokepoint selected for `substitutions-drop` LEAVES THE PROGRAM, and the ledger says how many rows went with it") {
    val out = PortFixture.portResolving(
      Chokepoint, Map("com.demo.Reflector" -> "substitutions-drop"), new RemediationTransform())
    assert(unitNames(out.before).contains("com.demo.Reflector"), unitNames(out.before))
    assertEquals(unitNames(out.after), Set.empty[String])
    val plan = out.binder.resolutions
    assertEquals(plan.refusals, Nil)
    val List(a) = plan.all: @unchecked
    assertEquals(a.remedy.id, "substitutions-drop")
    assertEquals(a.subjectFqn, "com.demo.Reflector")
    // the DRAIN, carried on the value: one applied row answering for several lane rows, so the
    // arithmetic a baseline diff does is `sum(drained)` and never `count(rows)`.
    assert(a.drained >= 1, clue(a.drained))
    assert(a.finding.detail.contains("portability(emitted)"), a.finding.detail)
  }

  test("…and the same selection at a MEDIUM chokepoint is DECLINED, naming the guard and the manifest key that would work") {
    val out = PortFixture.portAllResolving(
      List("Reflector.java" -> Chokepoint, "Uses.java" -> Referrer),
      Map("com.demo.Reflector" -> "substitutions-drop"), new RemediationTransform())
    // the type is STILL THERE — a refusal leaves the construct alone (ENGINE-LIMITS M6)
    assert(unitNames(out.after).contains("com.demo.Reflector"), unitNames(out.after))
    val plan = out.binder.resolutions
    assertEquals(plan.all, Nil)
    val List(r) = plan.refusals: @unchecked
    assertEquals(r.guard, "needs-injection")
    assert(r.why.contains("inject"), r.why)
    assertEquals(r.finding.kind, Resolution.RefusedKind)
    assert(r.finding.detail.contains("did NOT drain"), r.finding.detail)
  }

  test("a `substitutions-drop` at a type with no chokepointed site is DECLINED as such, not silently applied") {
    val plain =
      """package com.demo;
        |public class Plain { public int n() { return 1; } }""".stripMargin
    val out = PortFixture.portResolving(
      plain, Map("com.demo.Plain" -> "substitutions-drop"), new RemediationTransform())
    assert(unitNames(out.after).contains("com.demo.Plain"), unitNames(out.after))
    val List(r) = out.binder.resolutions.refusals: @unchecked
    assertEquals(r.guard, "not-a-chokepoint")
  }

  // -------------------------------------------------------------------------------------------
  // class-table — the mechanism is the engine's, the table is still the port's
  // -------------------------------------------------------------------------------------------

  private val Lookup =
    """package com.demo;
      |public class Names {
      |  public static Class<?> forName(String n) throws Exception { return Class.forName(n); }
      |}""".stripMargin

  test("a `class-table` selection with NO table entry is a CLASSIFIED refusal, never a silent success") {
    val out = PortFixture.portResolving(
      Lookup, Map("com.demo.Names#forName" -> "class-table"), new RemediationTransform())
    val List(r) = out.binder.resolutions.refusals: @unchecked
    assertEquals(r.guard, "no-table")
    assert(r.why.contains("classTables"), r.why)
    // the lookup is untouched: a redirect with no destination leaves the port JVM-only, and saying
    // nothing about that is the failure the refusal exists for.
    assert(out.out.contains("forName"))
  }

  test("…and WITH one the lookup is redirected, and the applied row names the table") {
    val out = PortFixture.portResolving(
      Lookup, Map("com.demo.Names#forName" -> "class-table"),
      new RemediationTransform(classTables = Map("com.demo.Names#forName" -> "com.demo.Table#classFor")))
    assertEquals(out.binder.resolutions.refusals, Nil)
    val List(a) = out.binder.resolutions.all: @unchecked
    assert(a.what.contains("com.demo.Table#classFor"), a.what)
  }

  test("a table row no selection reaches is DEAD POLICY and is reported — the §1(b) silent no-op") {
    val out = PortFixture.portResolving(
      Lookup, Map("com.demo.Names#forName" -> "class-table"),
      new RemediationTransform(classTables = Map(
        "com.demo.Names#forName" -> "com.demo.Table#classFor",
        "com.demo.Gone#forName"  -> "com.demo.Table#classFor")))
    val phase = out.phases.collectFirst { case p: RemediationTransform => p }.get
    val fs    = phase.policyReport.findings
    assertEquals(fs.map(_.key), List("com.demo.Gone#forName"))
    assertEquals(fs.head.issue, PolicyIssue.NeverMatched)
  }

  // -------------------------------------------------------------------------------------------
  // static-forwarder-inline
  // -------------------------------------------------------------------------------------------

  private val Wrapper =
    """package com.demo;
      |public class ClassWrap {
      |  public static String getSimpleName(Class<?> c) { return c.getSimpleName(); }
      |  public static boolean isArray(Class<?> c) { return c.isArray(); }
      |}
      |""".stripMargin

  test("a wrapper whose statics forward receiver-first is INLINED where the template verified it") {
    val out = PortFixture.portResolving(
      Wrapper, Map("com.demo.ClassWrap" -> "static-forwarder-inline"), new RemediationTransform())
    val plan = out.binder.resolutions
    // Either the template verified this wrapper and it was inlined, or it did not and the decline
    // says which guard — what may NOT happen is silence, and that is what this asserts.
    assertEquals(plan.all.size + plan.refusals.size, 1)
    plan.all.headOption.foreach { a =>
      assertEquals(a.remedy.id, "static-forwarder-inline")
      // an inline RELOCATES a call; it claims no lane rows, and says so rather than over-claiming.
      assertEquals(a.drained, 0)
    }
    plan.refusals.headOption.foreach(r =>
      assert(Set("not-a-forwarder", "nothing-forwardable").contains(r.guard), r.guard))
  }

  // -------------------------------------------------------------------------------------------
  // the plumbing the menu needed, and the no-op
  // -------------------------------------------------------------------------------------------

  test("with NO selection the phase returns its input — §1(b)'s empty parameter is a no-op") {
    val out = PortFixture.port(Chokepoint, new RemediationTransform())
    assertEquals(unitNames(out.after), unitNames(out.before))
    assertEquals(out.binder.resolutions.all, Nil)
    assertEquals(out.binder.resolutions.refusals, Nil)
  }

  test("a TYPE-subject remedy binds through `bindType`, so a `#` key at one is the binder's own Malformed") {
    val out = PortFixture.portResolving(
      Chokepoint, Map("com.demo.Reflector#make" -> "substitutions-drop"), new RemediationTransform())
    val bad = out.binder.unbound.filter(_.phase == Resolution.Seam)
    assertEquals(bad.map(_.entry), List("com.demo.Reflector#make"))
    assert(bad.head.binding.why.get.detail.contains("MEMBER key"), bad.head.binding.why.get.detail)
  }

  test("…and a MEMBER-subject remedy at a bare type key is Malformed the other way round") {
    val out = PortFixture.portResolving(
      Lookup, Map("com.demo.Names" -> "class-table"), new RemediationTransform())
    val bad = out.binder.unbound.filter(_.phase == Resolution.Seam)
    assertEquals(bad.map(_.entry), List("com.demo.Names"))
  }

  test("every declared remedy is on the FACTORY too — a typo and a missing `surface` line are two errors") {
    val factory = new balticporter.runner.RemediationFactory
    assertEquals(factory.remedies, new RemediationTransform().remedies)
    assertEquals(factory.name, new RemediationTransform().name)
  }

  test("ONE spelling of the drained lane — the check's constant, read by the phase and by the run") {
    // `Remedy.lane`'s own scaladoc: a lane is a constant so a rename is a COMPILE ERROR. This lane
    // had three literals (here, `AcceptJvmOnly`, `PortRun`), which agree by inspection and cannot be
    // made to disagree by a compiler — so the assertion is on the IDENTITY of the constant, and the
    // grep beside it is what would fail if a fourth literal reappeared.
    assertEquals(PortabilityCheck.EmittedLane, "portability(emitted)")
    assertEquals(balticporter.runner.PortRun.PortabilityEmitted, PortabilityCheck.EmittedLane)
    assertEquals(PortabilityCheck.AcceptJvmOnly.lane, PortabilityCheck.EmittedLane)
    assertEquals(new RemediationTransform().remedies.map(_.lane).distinct,
                 List(PortabilityCheck.EmittedLane))
  }

  test("the phase's remedies all name the lane they drain, and all of them are the portability one") {
    val rs = new RemediationTransform().remedies
    assertEquals(rs.map(_.lane).distinct, List(PortabilityCheck.EmittedLane))
    assert(rs.forall(_.emissionAffecting))
    assertEquals(rs.map(_.id).sorted, List("class-table", "static-forwarder-inline", "substitutions-drop"))
  }

  test("`accept-jvm-only` is the CHECK's remedy, changes no emitted text, and is in the run's active set") {
    assertEquals(PortabilityCheck.remedies.map(_.id), List("accept-jvm-only"))
    assert(!PortabilityCheck.AcceptJvmOnly.emissionAffecting)
    assert(balticporter.runner.PortRun.CheckRemedies.contains(PortabilityCheck))
  }

  test("…and NO rule in the portability list asks about the JVM, which is why accepting contradicts a JS/Native target") {
    // The measured fact behind `AcceptJvmOnly`'s consistency test: `targets = Set(Jvm)` empties the
    // rule list, so a port with nothing to accept is the only port that could accept consistently.
    assertEquals(PortabilityCheck.rulesFor(Set(Platform.Jvm)), Nil)
    assert(PortabilityCheck.rulesFor(Platform.values.toSet).nonEmpty)
  }
