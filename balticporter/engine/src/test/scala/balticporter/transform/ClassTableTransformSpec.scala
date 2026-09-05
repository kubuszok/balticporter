package balticporter.transform

import balticporter.core.{MergeablePolicy, PortManifest, SurfaceFold}
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Phase, Pipeline, Program, RuleScope}

/** `ClassTableTransform` — the §1(b) REDIRECT of a name lookup at a port's own table. Its scope is
  * an opt-OUT (`Everywhere(Set.empty)` is the pre-scope path) and two instances compose only over
  * DISJOINT scopes (`ENGINE-LIMITS.md` P10). */
class ClassTableTransformSpec extends munit.FunSuite:

  private val java =
    """package com.demo;
      |class Reflect { static Class<?> forName(String n) { return null; } }
      |class Alpha { Object go(String s) { return Reflect.forName(s); } }
      |class Beta  { Object go(String s) { return Reflect.forName(s); } }
      |""".stripMargin

  private val Key   = "com.demo.Reflect#forName"
  private val ToOne = "com.demo.TableOne#classFor"
  private val ToTwo = "com.demo.TableTwo#classFor"

  private def parse(): Program = SpoonTir.fromSource(java, "Demo.java")

  private def emit(p: Phase): String =
    val (after, log) = Pipeline.runTraced(parse(), List(p))
    new TirEmitter(after, notes = log).emit

  private def table(to: String = ToOne, scope: RuleScope = RuleScope.everywhere) =
    new ClassTableTransform(Map(Key -> to), scope)

  // ---- the no-op and the pre-scope path ---------------------------------------------------------

  test("an empty table is a no-op and contributes NO fingerprint segment") {
    val t = new ClassTableTransform(Map.empty)
    assertEquals(t.surfaceFingerprint, "")
    val before = parse()
    assertEquals(t.run(before).units.size, before.units.size)
  }

  test("the DEFAULT scope adds no fingerprint segment, so the parameter's arrival is flat") {
    assertEquals(table().surfaceFingerprint, s"$Key->$ToOne")
    assertEquals(table(scope = RuleScope.Only(Set("com.demo.Alpha"))).surfaceFingerprint,
      s"$Key->$ToOne[only:com.demo.Alpha]")
  }

  test("the unrestricted default redirects EVERY call, as it did before it had a scope") {
    val out = emit(table())
    assertEquals(clue(out).split("com.demo.TableOne.classFor", -1).length - 1, 2)
    assert(!out.contains("Reflect.forName"), clue(out))
  }

  // ---- the scope --------------------------------------------------------------------------------

  test("a scope redirects INSIDE it and leaves java's own call OUTSIDE it") {
    val out = emit(table(scope = RuleScope.Only(Set("com.demo.Alpha"))))
    assertEquals(clue(out).split("com.demo.TableOne.classFor", -1).length - 1, 1)
    // …the seam the scope made: java's lookup survives, loudly, for `portability(emitted)` to count.
    assert(out.contains("Reflect.forName(s)"), clue(out))
  }

  test("`except` points the other way: the named declaration keeps java's call") {
    val out = emit(table(scope = RuleScope.Everywhere(Set("com.demo.Beta"))))
    assertEquals(clue(out).split("com.demo.TableOne.classFor", -1).length - 1, 1)
    assert(out.contains("Reflect.forName(s)"), clue(out))
  }

  test("a scope entry no call site is under is REPORTED, never a silent no-op") {
    val p = table(scope = RuleScope.Only(Set("com.demo.Alpha", "com.demo.Gamma")))
    Pipeline.runTraced(parse(), List(p))
    val keys = p.policyReport.findings.map(_.key)
    assert(clue(keys).contains("com.demo.Gamma"))
    assert(!keys.contains("com.demo.Alpha"))
  }

  test("a key naming no member, and a value that is not `owner#member`, are both reported") {
    val absent = new ClassTableTransform(Map("com.demo.NoSuch#gone" -> ToOne))
    Pipeline.runTraced(parse(), List(absent))
    assert(clue(absent.policyReport.findings.map(_.key)).contains("com.demo.NoSuch#gone"))
    val malformed = new ClassTableTransform(Map(Key -> "com.demo.TableOne"))
    Pipeline.runTraced(parse(), List(malformed))
    assert(clue(malformed.policyReport.findings.map(_.detail).mkString)
      .contains("is not `owner#member`"))
  }

  // ---- merge ------------------------------------------------------------------------------------

  private def merged(a: ClassTableTransform, b: ClassTableTransform) =
    a.mergedWith(b).map { case MergeablePolicy.Merged(p, added) =>
      (p.asInstanceOf[ClassTableTransform], added) }

  test("independent keys UNION, and the later instance's subjects are what it ADDS") {
    val other = new ClassTableTransform(Map("com.demo.Alpha#go" -> ToTwo))
    val Right((p, added)) = merged(table(), other): @unchecked
    assertEquals(p.entries.map(_.from).sorted, List("com.demo.Alpha#go", Key))
    assert(clue(added).contains("com.demo.Alpha"))
  }

  test("the SAME key at a DIFFERENT table composes over DISJOINT scopes, per SITE") {
    val base = table(ToOne, RuleScope.Only(Set("com.demo.Alpha")))
    val dep  = table(ToTwo, RuleScope.Only(Set("com.demo.Beta")))
    val Right((p, _)) = merged(base, dep): @unchecked
    assertEquals(p.entries.size, 2)
    val out = emit(p)
    assert(clue(out).contains("com.demo.TableOne.classFor"))
    assert(out.contains("com.demo.TableTwo.classFor"))
    assert(!out.contains("Reflect.forName"), clue(out))
  }

  test("the SAME key at a DIFFERENT table REFUSES where the scopes OVERLAP") {
    // the unrestricted base covers the dependent's package, so a site would be claimed twice.
    assert(clue(merged(table(ToOne), table(ToTwo, RuleScope.Only(Set("com.demo.Beta"))))).isLeft)
    assert(merged(table(ToOne, RuleScope.Only(Set("com.demo"))),
                  table(ToTwo, RuleScope.Only(Set("com.demo.Beta")))).isLeft)
    // …and an `except` that CARVES OUT the dependent's declaration is disjoint, so it composes.
    assert(merged(table(ToOne, RuleScope.Everywhere(Set("com.demo.Beta"))),
                  table(ToTwo, RuleScope.Only(Set("com.demo.Beta")))).isRight)
  }

  test("the same key at the SAME table is idempotent, whatever the scopes") {
    val Right((p, _)) = merged(table(), table(ToOne, RuleScope.Only(Set("com.demo.Beta")))): @unchecked
    assertEquals(p.entries.map(_.to).distinct, List(ToOne))
  }

  test("a manifest chain folds the pair into ONE instance, and a conflict is a fatal refusal") {
    val base = PortManifest("base", governs = Set("com.demo"),
      surface = List(table(ToOne, RuleScope.Only(Set("com.demo.Alpha")))))
    val ok   = base.extendedBy(PortManifest("dep",
      surface = List(table(ToTwo, RuleScope.Only(Set("com.demo.Beta"))))))
    assertEquals(ok.surfaceFold.refusals, Nil)
    assertEquals(ok.effectiveSurface.collect { case c: ClassTableTransform => c }.size, 1)
    val bad = PortManifest("base", governs = Set("com.demo"), surface = List(table(ToOne)))
      .extendedBy(PortManifest("dep",
        surface = List(table(ToTwo, RuleScope.Only(Set("com.demo.Beta"))))))
    assertEquals(clue(bad.surfaceFold.refusals).map(_.cause), List(SurfaceFold.Cause.Conflict))
  }
