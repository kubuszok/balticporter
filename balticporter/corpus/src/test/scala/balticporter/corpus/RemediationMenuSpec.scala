package balticporter.corpus

import balticporter.catalog.Platform
import balticporter.core.PolicyIssue
import balticporter.testkit.PortFixture
import balticporter.tir.*
import balticporter.transform.RemediationTransform

/** THE PORTABILITY MENU, at each of its answers. */
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

  test("…and it claims ZERO rows, because a redirect RELOCATES a call and the lane counts the body") {
    // The row `portability(emitted)` holds for this port is the `Class.forName` INSIDE
    // `Names#forName`; the redirect rewrites the wrapper's CALL SITES and leaves that body alone,
    // so the lane falls by nothing here. `drained` used to be the number of call sites of the
    // wrapper, which is neither the rows removed nor a number this lane holds — `resolved` gained N
    // while the lane fell by 0, and `sum(drained)` is the one arithmetic §5's drain rule rests on.
    val caller =
      """package com.demo;
        |public class Uses {
        |  public Class<?> a() throws Exception { return Names.forName("x"); }
        |  public Class<?> b() throws Exception { return Names.forName("y"); }
        |}""".stripMargin
    val out = PortFixture.portAllResolving(
      List("Names.java" -> Lookup, "Uses.java" -> caller),
      Map("com.demo.Names#forName" -> "class-table"),
      new RemediationTransform(classTables = Map("com.demo.Names#forName" -> "com.demo.Table#classFor")))
    val List(a) = out.binder.resolutions.all: @unchecked
    assertEquals(a.drained, 0)
    assert(a.what.contains("claims no rows"), a.what)
    assert(a.finding.detail.contains("draining 0 row(s)"), a.finding.detail)
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

  /** the suggestions `Remediator` prints for a program, as the run computes them. */
  private def suggestions(sources: List[(String, String)]): List[Remediator.Suggestion] =
    val p = PortFixture.portAll(sources).before
    Remediator.suggest(p, PortabilityCheck.check(p, PortabilityCheck.rulesFor(Platform.values.toSet)))

  test("the KEY `Remediator` prints for `class-table` is one the REMEDY can bind — the wrapper's") {
    val List(s) = suggestions(List("Names.java" -> Lookup)).filter(_.mechanism == "class-table"): @unchecked
    assertEquals(s.subject, "com.demo.Names#forName")
    // …and the two ends agree: the printed key, pasted into `resolutions`, BINDS.
    val out = PortFixture.portResolving(Lookup, Map(s.subject -> "class-table"), new RemediationTransform())
    assertEquals(out.binder.unbound.filter(_.phase == Resolution.Seam), Nil)
  }

  test("…and where there is no wrapper it proposes NOTHING, rather than a key no door can accept") {
    // `java.lang.Class#forName` is `ExternalOnly` at BOTH doors — `ClassTableTransform.bindPolicy`
    // and the `class-table` remedy's `Remedy.Subject.OwnedMember` — so printing it as the key of a
    // pasteable snippet costs its reader a cycle to disprove, which is the one thing this file's
    // design forbids.
    val direct =
      """package com.demo;
        |public class Direct {
        |  public Class<?> a(String n) throws Exception { return Class.forName(n); }
        |}""".stripMargin
    val List(s) = suggestions(List("Direct.java" -> direct)).filter(_.mechanism == "class-table"): @unchecked
    assertEquals(s.confidence, Remediator.Confidence.Observation)
    assertEquals(s.snippet, scala.None)
    assert(clue(s.observed).contains("NO SELECTABLE KEY"))
    // and the proof that this is the right refusal: that key really does bind nowhere.
    val out = PortFixture.portResolving(direct, Map("java.lang.Class#forName" -> "class-table"),
                                        new RemediationTransform())
    assertEquals(out.binder.unbound.filter(_.phase == Resolution.Seam).map(_.entry),
                 List("java.lang.Class#forName"))
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
  // D2 — a dependent's Program CONTAINS its base's units, and a selection is INHERITED
  // -------------------------------------------------------------------------------------------

  /** run the phase under a `RunScope` this spec chooses — which is how a run reaches it: the two
    * facts a phase may not derive (what this module EMITS, and which BACKENDS it is ported for)
    * arrive on the binder and nowhere else. */
  private def underScope(sources: List[(String, String)], resolutions: Map[String, String],
                         phase: RemediationTransform, scope: Program => RunScope): (Program, PolicyBinder) =
    val p      = PortFixture.portAll(sources).before
    val vocab  = RemedyVocabulary.from(List(phase))
    val binder = new PolicyBinder(p, p.members, scope(p))
    binder.resolving(ResolutionPlan.of(resolutions, vocab, vocab.byId.keySet, binder))
    (Pipeline.runTraced(p, List(phase), binder)._1, binder)

  /** the dependent's shape: this run emits NOTHING of what it is handed, which is what a base's
    * units look like from inside a module that only resolves against them (`RunScope.of(Set.empty)`,
    * the same fixture `HeapPollutionRemedySpec` and `OverloadRiskRemedySpec` use). */
  private def asDependent(sources: List[(String, String)], resolutions: Map[String, String],
                          phase: RemediationTransform): (Program, PolicyBinder) =
    underScope(sources, resolutions, phase, _ => RunScope.of(Set.empty, Map.empty))

  test("a base's selection does NOT re-apply in a dependent — the D2 guard both Wave B appliers carry") {
    // `PortManifest.resolutions` is inherited (§8.16: a remedy decides emitted text at a shared
    // declaration), and a dependent's model holds its base's units — so the key binds HERE too, at
    // the very same symbol. Unguarded, this phase drops a base's unit out of the dependent's model
    // and files `remediation(resolved)` rows and `SelectedRemedy` decisions about declarations this
    // module does not write. The base already did all of it in its own run.
    val (out, binder) = asDependent(
      List("Reflector.java" -> Chokepoint), Map("com.demo.Reflector" -> "substitutions-drop"),
      new RemediationTransform())
    assert(unitNames(out).contains("com.demo.Reflector"), unitNames(out))
    assertEquals(binder.resolutions.all, Nil)
    // …and NOT a refusal either: a refusal row names a declaration, and this one is the base's.
    assertEquals(binder.resolutions.refusals, Nil)
  }

  test("…and the same holds for a member-keyed selection, which walks the SYMBOLS and not the units") {
    val (out, binder) = asDependent(
      List("Names.java" -> Lookup), Map("com.demo.Names#forName" -> "class-table"),
      new RemediationTransform(classTables = Map("com.demo.Names#forName" -> "com.demo.Table#classFor")))
    assertEquals(binder.resolutions.all, Nil)
    assertEquals(binder.resolutions.refusals, Nil)
    val untouched = PortFixture.portAll(List("Names.java" -> Lookup)).before
    assertEquals(new balticporter.emit.TirEmitter(out).emit, new balticporter.emit.TirEmitter(untouched).emit)
  }

  // -------------------------------------------------------------------------------------------
  // WHICH BACKENDS the questions are asked for is the RUN's — never a set of the phase's own
  // -------------------------------------------------------------------------------------------

  test("a port that targets the JVM ALONE has nothing to remediate, and the phase asks THAT question") {
    // `rulesFor(Set(Jvm))` is empty (no rule in the list asks about the JVM), so this port's
    // `portability(emitted)` lane reads 0 and there is no chokepoint to drop. The phase used to take
    // its own `targets`, defaulted to all three, so it computed violations the run does not report
    // and could claim to drain rows from a lane reading zero — two spellings of one manifest field.
    val (out, binder) = underScope(
      List("Reflector.java" -> Chokepoint), Map("com.demo.Reflector" -> "substitutions-drop"),
      new RemediationTransform(),
      p => RunScope.of(p.units.map(_.symbol).toSet, Map.empty,
                       RunScope.PlatformPolicy(Set(Platform.Jvm))))
    assert(unitNames(out).contains("com.demo.Reflector"), unitNames(out))
    assertEquals(binder.resolutions.all, Nil)
    val List(r) = binder.resolutions.refusals: @unchecked
    assertEquals(r.guard, "not-a-chokepoint")
  }

  test("…and the SAME program under the run's default target set is dropped, so the difference is the scope") {
    val (out, binder) = underScope(
      List("Reflector.java" -> Chokepoint), Map("com.demo.Reflector" -> "substitutions-drop"),
      new RemediationTransform(), p => RunScope.of(p.units.map(_.symbol).toSet, Map.empty))
    assertEquals(unitNames(out), Set.empty[String])
    assertEquals(binder.resolutions.all.map(_.remedy.id), List("substitutions-drop"))
  }

  test("the phase declares no target set of its own — nothing for a manifest to state twice") {
    // A `SurfacePolicy` fingerprint over a COPY of `PortManifest.targets` would be exactly the second
    // spelling this fix removed, so the fingerprint is the `classTables` table and nothing else.
    assertEquals(new RemediationTransform().surfaceFingerprint, "")
    assertEquals(new RemediationTransform(classTables = Map("a.B#c" -> "d.E#f")).surfaceFingerprint,
                 "a.B#c->d.E#f")
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
