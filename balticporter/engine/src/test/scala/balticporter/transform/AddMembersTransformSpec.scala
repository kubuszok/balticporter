package balticporter.transform

import balticporter.core.{MergeablePolicy, PortManifest, SurfaceFold}
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Decision, DecisionLog, Pipeline, Program, Reason}

/** AddMembersTransform — the §1(b) mechanism for appending hand-port members to a mechanically
  * translated class. */
class AddMembersTransformSpec extends munit.FunSuite:
  import AddMembersTransform.MemberSpec

  private def amt(entries: (String, List[MemberSpec])*) = new AddMembersTransform(entries.toMap)

  private def base(surface: List[balticporter.tir.Phase]) =
    PortManifest("base", governs = Set("com.demo"), surface = surface)

  private def spec(name: String, arity: Int, source: String, why: String = "") =
    MemberSpec(name, arity, source, Reason.Configured("add-members", s"owner#$name"), Some(why))

  private def parse(java: String): Program = SpoonTir.fromSource(java, "Demo.java")

  private case class Ported(before: Program, after: Program, out: String, log: DecisionLog)

  private def run(java: String, p: balticporter.tir.Phase): Ported =
    val before       = parse(java)
    val (after, log) = Pipeline.runTraced(before, List(p))
    Ported(before, after, new TirEmitter(after, notes = log).emit, log)

  // ---- fingerprint: empty vs non-empty ----

  test("empty map produces empty fingerprint (omitted segment, no-op)") {
    val t = amt()
    assertEquals(t.surfaceFingerprint, "")
  }

  test("non-empty map produces a fingerprint naming each owner and member") {
    val t = amt("com.demo.A" -> List(spec("foo", 0, "val foo: Int = 0")))
    assert(clue(t.surfaceFingerprint).nonEmpty)
    assert(t.surfaceFingerprint.contains("com.demo.A"))
    assert(t.surfaceFingerprint.contains("foo/0"))
  }

  // ---- merge: independent owners union ----

  test("independent owners from base and dependent merge into one instance") {
    val b = base(List(amt("com.demo.A" -> List(spec("foo", 0, "val foo: Int = 0")))))
    val dep = b.extendedBy(PortManifest("dep",
      surface = List(amt("com.dep.B" -> List(spec("bar", 2, "def bar(a: Int, b: Int): Int = a + b"))))))

    assertEquals(dep.surfaceFold.refusals, Nil)
    val eff = dep.effectiveSurface.collect { case t: AddMembersTransform => t }
    assertEquals(clue(eff.size), 1)
    assertEquals(eff.head.members.size, 2)
    assert(eff.head.members.contains("com.demo.A"))
    assert(eff.head.members.contains("com.dep.B"))
  }

  // ---- merge: same owner+name refuses ----

  test("same owner and member name refuses — two members at the same declaration is a conflict") {
    val b = base(List(amt("com.demo.A" -> List(spec("foo", 0, "val foo: Int = 0")))))
    val dep = b.extendedBy(PortManifest("dep",
      surface = List(amt("com.demo.A" -> List(spec("foo", 0, "val foo: String = \"x\""))))))

    assert(clue(dep.surfaceFold.refusals).nonEmpty)
    assert(dep.surfaceFold.refusals.head.cause == SurfaceFold.Cause.Conflict)
    // two instances stay in the pipeline
    val eff = dep.effectiveSurface.collect { case t: AddMembersTransform => t }
    assertEquals(clue(eff.size), 2)
  }

  // ---- merge: same owner, different members, union ----

  test("same owner with different member names merges into one instance") {
    val b = base(List(amt("com.demo.A" -> List(spec("foo", 0, "val foo: Int = 0")))))
    val dep = b.extendedBy(PortManifest("dep",
      surface = List(amt("com.demo.A" -> List(spec("bar", 1, "def bar(x: Int): Unit = ()"))))))

    assertEquals(dep.surfaceFold.refusals, Nil)
    val eff = dep.effectiveSurface.collect { case t: AddMembersTransform => t }
    assertEquals(clue(eff.size), 1)
    assertEquals(eff.head.members("com.demo.A").size, 2)
  }

  // ---- subjects ----

  test("subjects returns the owner FQNs for the governs screen") {
    val t = amt(
      "com.demo.A" -> List(spec("foo", 0, "val foo: Int = 0")),
      "com.demo.B" -> List(spec("bar", 1, "def bar(x: Int): Unit = ()")),
    )
    assertEquals(t.subjects, Set("com.demo.A", "com.demo.B"))
  }

  // ---- run: added def and added protected val ----

  test("run appends members to the owner's body and records AddedMember decisions") {
    val java =
      """package com.demo;
        |public class Engine {
        |  public int x = 1;
        |}""".stripMargin

    val phase = amt("com.demo.Engine" -> List(
      spec("factories", 0,
        "protected val factories: scala.collection.mutable.HashMap[Class[?], () => ?] = scala.collection.mutable.HashMap.empty",
        "factory registry"),
      spec("register", 2,
        "def register[T](cls: Class[T], f: () => T): Unit = factories.put(cls, f)",
        "register factory"),
    ))

    val ported = run(java, phase)
    val decisions = ported.log.all.filter(_.kind == Decision.Kind.AddedMember)
    assertEquals(clue(decisions.size), 2)
    assert(decisions.exists(_.detail("member") == "factories"))
    assert(decisions.exists(_.detail("member") == "register"))
    // every decision has the right reason classification
    assert(decisions.forall(_.reason.isInstanceOf[Reason.Configured]))
    // the emitted output contains both members
    assert(clue(ported.out).contains("protected val factories"))
    assert(clue(ported.out).contains("def register"))
    // porter notes are present in emitted output
    assert(ported.out.contains("porter: added-member"))
  }

  // ---- run: a STATIC member reaches the COMPANION ----

  test("a static spec is emitted in the companion object, not the class body") {
    val java =
      """package com.demo;
        |public class Widget {
        |  public int x = 1;
        |}""".stripMargin

    val phase = amt("com.demo.Widget" -> List(
      MemberSpec("apply", 0, "def apply(): Widget = new Widget()",
        Reason.Configured("add-members", "com.demo.Widget#apply"), Some("factory"), static = true),
      MemberSpec("twice", 0, "def twice: Int = x * 2",
        Reason.Configured("add-members", "com.demo.Widget#twice"), Some("instance")),
    ))

    val ported = run(java, phase)
    val out    = ported.out
    // the companion exists BECAUSE of the static member — this java class declares no static
    assert(clue(out).contains("object Widget"))
    val objIdx = out.indexOf("object Widget")
    assert(clue(out.indexOf("def apply(): Widget")) > clue(objIdx))
    assert(clue(out.indexOf("def twice")) < clue(objIdx))
    assertEquals(clue(ported.log.all.count(_.kind == Decision.Kind.AddedMember)), 2)
    assert(ported.log.all.exists(_.detail.get("home").contains("companion")))
  }

  test("static is part of the fingerprint and of the merge key") {
    val inst = amt("com.demo.A" -> List(spec("foo", 0, "def foo: Int = 0")))
    val stat = amt("com.demo.A" -> List(
      MemberSpec("foo", 0, "def foo: Int = 0", Reason.Configured("add-members", "x"), None, static = true)))
    assert(clue(inst.surfaceFingerprint) != clue(stat.surfaceFingerprint))
    // …so the two are INDEPENDENT keys and compose rather than refusing
    val dep = base(List(inst)).extendedBy(PortManifest("dep", surface = List(stat)))
    assertEquals(clue(dep.surfaceFold.refusals), Nil)
  }
