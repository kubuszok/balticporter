package balticporter.transform

import balticporter.core.{MergeablePolicy, PolicyFinding, PolicyIssue}
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.*

/** `member-rename` — the manifest's way to reach [[MemberRenamer]] (CLAUDE.md §1(b), §4.55). */
class MemberRenameTransformSpec extends munit.FunSuite:

  // ---- fixtures ------------------------------------------------------------------------------

  /** A window hierarchy with a `close()` at three levels and a caller — the shape a widget toolkit
    * actually has. NOTHING here has an unparsed parent, so every refusal below is caused by the
    * thing it names and not by an anchor. */
  private val windows =
    """package com.demo;
      |
      |class Window {
      |  /** shuts it. */
      |  protected void close() {}
      |  void fadeOut() {}
      |}
      |
      |class Dialog extends Window {
      |  protected void close() {}
      |}
      |
      |class Picker extends Window {
      |  protected void close() {}
      |  void go() { close(); }
      |}
      |
      |class Client {
      |  void go(Picker p) { p.go(); }
      |}
      |""".stripMargin

  private def parse(java: String): Program = SpoonTir.fromSource(java, "Demo.java")

  private case class Ported(before: Program, after: Program, out: String, log: DecisionLog):
    def nameOf(fqn: String): Option[String] =
      after.symbolOf(sym(before, fqn)).map(_.name)

  private def run(java: String, p: Phase): Ported =
    val before       = parse(java)
    val (after, log) = Pipeline.runTraced(before, List(p))
    Ported(before, after, new TirEmitter(after, notes = log).emit, log)

  private def sym(p: Program, fqn: String): SymId =
    p.symbols.all.find(_.fullName == fqn).map(_.id).getOrElse(fail(s"no symbol named $fqn"))

  /** the emitted CODE with the porter notes stripped — a note names the UPSTREAM member on purpose
    * (§4.575's `from=`), so a text search that forgets reports a phantom. */
  private def code(out: String): String =
    out.linesIterator.filterNot(l => l.contains(PorterNote.Marker) || l.trim.startsWith("—")).mkString("\n")

  // ---- 1. the no-op --------------------------------------------------------------------------

  test("an empty table is a STRUCTURAL no-op — §1(b)'s 'turned off needs no code path'") {
    val ph     = new MemberRenameTransform()
    val before = parse(windows)
    ph.bindPolicy(new PolicyBinder(before, before.members))
    assertEquals(ph.policyReport.findings, Nil)
    assertEquals(ph.surfaceFingerprint, "")
    assertEquals(ph.subjects, Set.empty[String])
    // the program came back UNTOUCHED, not merely equal — asked of the PHASE and not of
    // `Pipeline.run`, which rebuilds the xref after every phase whatever the phase returned.
    assert(clue(ph.run(before)) eq before, "an empty table rebuilt the program")
  }

  // ---- 2. the happy path: the whole component moves -------------------------------------------

  test("a rename takes every declaration of the override COMPONENT, and the call sites follow") {
    val ph = new MemberRenameTransform(Map("com.demo.Window#close" -> "closeWindow"))
    val r  = run(windows, ph)

    assertEquals(ph.policyReport.findings, Nil, ph.policyReport.render)
    List("com.demo.Window#close", "com.demo.Dialog#close", "com.demo.Picker#close")
      .foreach(f => assertEquals(r.nameOf(f), Some("closeWindow"), f))

    // the call site inside `Picker#go` follows the symbol, for free (§4.55's exactness argument)
    assert(clue(code(r.out)).contains("closeWindow()"), r.out)
    assert(!code(r.out).contains(" close()"), s"`close` survives somewhere:\n${r.out}")
    // …and a member that merely SHARES the class is untouched
    assertEquals(r.nameOf("com.demo.Window#fadeOut"), Some("fadeOut"))

    // one RenamedMember decision per renamed DECLARATION, each carrying the manifest entry verbatim
    val renamed = r.log.all.filter(_.kind == Decision.Kind.RenamedMember)
    assertEquals(clue(renamed).size, 3)
    assert(renamed.forall(_.reason == Reason.Configured("member-rename", "com.demo.Window#close")))

    // …and the note is beside the code (§4.575), AFTER the upstream comment, never before it
    assert(clue(r.out).contains("/* porter: renamed-member"), r.out)
    assert(r.out.contains("phase=member-rename"))
    val doc  = r.out.indexOf("shuts it.")
    val note = r.out.indexOf("/* porter:", doc)
    assert(doc >= 0 && note > doc, s"the note displaced the upstream trivia:\n${r.out}")
  }

  test("a precise key names ONE overload, and leaves the other alone") {
    val java = windows.replace("void fadeOut() {}", "protected void close(int ms) {}")
    val ph   = new MemberRenameTransform(Map("com.demo.Window#close()" -> "closeWindow"))
    val r    = run(java, ph)
    assertEquals(ph.policyReport.findings, Nil, ph.policyReport.render)
    assertEquals(r.nameOf("com.demo.Window#close"), Some("closeWindow"))
    // the arity-1 overload is a DIFFERENT member and keeps its name
    assert(clue(code(r.out)).contains("close(ms"), r.out)
  }

  // ---- 3. the reported failures — each is a finding, never silence -----------------------------

  test("a key that names nothing is a REPORTED typo, not a silent no-op") {
    val ph = new MemberRenameTransform(Map("com.demo.Window#clos" -> "closeWindow"))
    run(windows, ph)
    val f = ph.policyReport.findings
    assertEquals(clue(f).size, 1)
    assertEquals(f.head.issue, PolicyIssue.NeverMatched)
    assertEquals(f.head.about, PolicyFinding.About.TheKey)
  }

  test("a MALFORMED key is reported as malformed, never as never-matched — they read opposite") {
    val ph = new MemberRenameTransform(Map("com.demo.Window" -> "closeWindow"))
    run(windows, ph)
    val f = ph.policyReport.findings
    assertEquals(clue(f).size, 1)
    assertEquals(f.head.issue, PolicyIssue.Malformed)
    assert(f.head.detail.contains("`#`"), f.head.detail)
  }

  test("a value that is not a BARE MEMBER NAME names an act this phase does not perform") {
    // every one of these is a spelling of some OTHER act: an arity change, a re-point, a key.
    List("", "close()", "a.b.close", "com.demo.Other#close", "close me", "2close").foreach { bad =>
      val ph = new MemberRenameTransform(Map("com.demo.Window#close" -> bad))
      val r  = run(windows, ph)
      val f  = ph.policyReport.findings
      assertEquals(clue(f).size, 1, s"value `$bad`")
      assertEquals(f.head.issue, PolicyIssue.Malformed, s"value `$bad`")
      // …and NOTHING was renamed: a malformed entry may not half-apply
      assertEquals(r.nameOf("com.demo.Window#close"), Some("close"), s"value `$bad` renamed anyway")
    }
  }

  test("a COLLISION refuses, names the collider, and is About.ThisRun") {
    // `fadeOut` is declared beside the member being renamed — the port's chosen name is taken.
    val ph = new MemberRenameTransform(Map("com.demo.Window#close" -> "fadeOut"))
    val r  = run(windows, ph)

    // nothing moved — whole or none
    List("com.demo.Window#close", "com.demo.Dialog#close", "com.demo.Picker#close")
      .foreach(f => assertEquals(r.nameOf(f), Some("close"), f))

    val f = ph.policyReport.findings
    assertEquals(clue(f).size, 1)
    assertEquals(f.head.issue, PolicyIssue.Unverifiable)
    // the classification is the whole point: the KEY may be a base's and correct there; what
    // refused is THIS RUN, over declarations only this module has (`PolicyFinding.About`).
    assertEquals(f.head.about, PolicyFinding.About.ThisRun)
    assert(f.head.detail.contains("com.demo.Window#fadeOut"), f.head.detail)
  }

  test("a component that reaches a declaration this run does NOT EMIT is refused, base named") {
    // the D2 shape: a dependent's `Program` contains its base. `RunScope.whole` is the base port's
    // answer and would let this through; a real scope refuses, because renaming a base's
    // declaration here emits an `override` of a member the base does not have (§1.5).
    val before = parse(windows)
    val ph     = new MemberRenameTransform(Map("com.demo.Picker#close" -> "closeWindow"))
    val theirs = before.units.map(_.symbol)
      .filter(u => before.symbolOf(u).exists(_.fullName.contains("Window"))).toSet
    ph.bindPolicy(new PolicyBinder(before, before.members, RunScope.of(
      emitted = before.units.map(_.symbol).toSet -- theirs, own = Map.empty)))
    val after = ph.run(before)

    assertEquals(after.symbolOf(sym(before, "com.demo.Picker#close")).map(_.name), Some("close"))
    val f = ph.policyReport.findings
    assertEquals(clue(f).size, 1, f.map(_.render).mkString("\n"))
    assertEquals(f.head.about, PolicyFinding.About.ThisRun)
    assert(f.head.detail.contains("resolution root"), f.head.detail)
  }

  // ---- 4. the merge contract (§1.5, DESIGN.md §8.13) -------------------------------------------

  private def merge(a: Map[String, String], b: Map[String, String]) =
    new MemberRenameTransform(a).mergedWith(new MemberRenameTransform(b))

  test("independent members UNION, and the added SUBJECTS are what `governs` screens") {
    val m = merge(Map("com.demo.Window#close" -> "closeWindow"),
                  Map("com.other.Stream#close" -> "closeStream"))
    m match
      case Right(MergeablePolicy.Merged(p: MemberRenameTransform, added)) =>
        assertEquals(p.renames.size, 2)
        assertEquals(added, Set("com.other.Stream"))
      case other => fail(s"expected a merge, got $other")
  }

  test("the SAME member with two names REFUSES — and the two spellings are compared as MEMBERS") {
    // identical keys
    assert(merge(Map("com.demo.Window#close" -> "a"), Map("com.demo.Window#close" -> "b")).isLeft)
    // …and the trap: a BARE key is every overload, so these two strings may be ONE member. Compared
    // by map key they merge cleanly and the disagreement arrives at `MemberRenamer` as its
    // NON-FATAL two-claimants refusal, where the contract owes a fatal `SurfaceDivergence`.
    val l = merge(Map("com.demo.Window#close" -> "a"), Map("com.demo.Window#close()" -> "b"))
    assert(clue(l).isLeft)
    assert(l.left.exists(_.contains("ONE member")), l.toString)
    // two DISTINCT descriptors really are two members and do not refuse
    assert(merge(Map("com.demo.Window#close(int)" -> "a"),
                 Map("com.demo.Window#close(long)" -> "b")).isRight)
    // agreeing on the same value is agreement, not a clash
    assert(merge(Map("com.demo.Window#close" -> "a"), Map("com.demo.Window#close" -> "a")).isRight)
  }

  test("a different phase has no table to compose with") {
    assert(new MemberRenameTransform().mergedWith(new MutableParamsTransform).isLeft)
  }

  // ---- 5. THE PIPELINE POSITION — the measured one ---------------------------------------------

  test("the phase declares exactly the two edges it needs, and NOT the collections one") {
    val ph = new MemberRenameTransform()
    assertEquals(ph.runsBefore, Set("type-redirect", "package-rename"))
    // the negative that has a number: an edge onto `java-collections->scala` — copied from
    // `bean-properties`, which needs it and this does not — postponed `type-redirect` past
    // `globals->implicits` on `sge-visui` and moved `context-seam` 42 -> 41 at ZERO emitted bytes,
    // with the phase SKIPPED for the measurement.
    assert(!ph.runsBefore.contains("java-collections->scala"))
  }

  test("declared AHEAD of `type-redirect`, the phase POSTPONES NOTHING") {
    // `Pipeline.order` is a min-heap on declaration index, so this is the position a base gives the
    // phase and the reason an empty instance in a base's surface is worth its one fingerprint field.
    val named = (n: String) => new Phase { def name = n }
    val order = Pipeline.order(List(
      named("collections"), named("a"), new MemberRenameTransform(),
      named("type-redirect"), named("b"), named("globals->implicits")))
    assertEquals(order.map(_.name),
      List("collections", "a", "member-rename", "type-redirect", "b", "globals->implicits"))
  }

  test("declared BEHIND it — where an unmerged dependent phase lands — it postpones it: the defect") {
    val named = (n: String) => new Phase { def name = n }
    val order = Pipeline.order(List(
      named("collections"), named("a"), named("type-redirect"),
      named("b"), named("globals->implicits"), new MemberRenameTransform()))
    // `type-redirect` has slid past BOTH `b` and `globals->implicits`, which is exactly the
    // reordering that moved a check count with no emitted change. Pinned so that a port which
    // declares this phase without a base position can see what it is buying.
    assertEquals(order.map(_.name),
      List("collections", "a", "b", "globals->implicits", "member-rename", "type-redirect"))
  }

  // ---- 6. SYMBOLIC NAMES with @targetName (CLAUDE.md §1(b)) ------------------------------------

  /** A Vec2 hierarchy with `add/sub/scl/dot/len2`, overloads, and an override chain — the shape a
    * game-engine math library actually has. The reference hand port (`sge.math.Vector2`) renames
    * `add` to `+`, `sub` to `-`, etc. with `@targetName`. */
  private val vectors =
    """package com.math;
      |
      |class Vec2 {
      |  float x, y;
      |  /** the component-wise sum. */
      |  Vec2 add(Vec2 other) { x += other.x; y += other.y; return this; }
      |  Vec2 add(float dx, float dy) { x += dx; y += dy; return this; }
      |  Vec2 sub(Vec2 other) { x -= other.x; y -= other.y; return this; }
      |  float dot(Vec2 other) { return x * other.x + y * other.y; }
      |  float len2() { return x * x + y * y; }
      |  Vec2 scl(float s) { x *= s; y *= s; return this; }
      |  Vec2 negate() { x = -x; y = -y; return this; }
      |}
      |
      |class Vec3 extends Vec2 {
      |  float z;
      |  Vec2 add(Vec2 other) { super.add(other); return this; }
      |}
      |
      |class User {
      |  float dist2(Vec2 a, Vec2 b) { Vec2 d = new Vec2(); d.add(a); d.sub(b); return d.dot(d); }
      |}
      |""".stripMargin

  test("a symbolic rename emits @targetName and the whole override component follows") {
    val ph = new MemberRenameTransform(Map("com.math.Vec2#add" -> "+"))
    val r  = run(vectors, ph)

    assertEquals(ph.policyReport.findings, Nil, ph.policyReport.render)
    // ALL overloads of `add` are renamed to `+`
    assertEquals(r.nameOf("com.math.Vec2#add"), Some("+"))
    // the override in Vec3 follows the component
    assertEquals(r.nameOf("com.math.Vec3#add"), Some("+"))
    // `sub` is NOT renamed — it is a different member
    assertEquals(r.nameOf("com.math.Vec2#sub"), Some("sub"))

    // the emitted code carries `@scala.annotation.targetName("add")`
    val c = r.out
    assert(clue(c).contains("@scala.annotation.targetName(\"add\")"), c)
    // the member is emitted with the symbolic name
    assert(clue(code(c)).contains("def +("), c)
    // …and the call site in User.dist2 follows through the symbol table rewrite
    assert(clue(code(c)).contains(".+("), s"call site not rewritten:\n$c")
    // one RenamedMember decision per renamed DECLARATION
    val renamed = r.log.all.filter(_.kind == Decision.Kind.RenamedMember)
    // Vec2#add (both overloads = 2) + Vec3#add (1) = 3 declarations
    assertEquals(clue(renamed).size, 3)
  }

  test("multiple operators: `add` -> `+`, `sub` -> `-`, `scl` -> `*`") {
    val ph = new MemberRenameTransform(Map(
      "com.math.Vec2#add" -> "+",
      "com.math.Vec2#sub" -> "-",
      "com.math.Vec2#scl" -> "*",
    ))
    val r = run(vectors, ph)
    assertEquals(ph.policyReport.findings, Nil, ph.policyReport.render)
    assertEquals(r.nameOf("com.math.Vec2#add"), Some("+"))
    assertEquals(r.nameOf("com.math.Vec2#sub"), Some("-"))
    assertEquals(r.nameOf("com.math.Vec2#scl"), Some("*"))
    // each carries its own @targetName with its OWN original name
    assert(r.out.contains("@scala.annotation.targetName(\"add\")"), r.out)
    assert(r.out.contains("@scala.annotation.targetName(\"sub\")"), r.out)
    assert(r.out.contains("@scala.annotation.targetName(\"scl\")"), r.out)
  }

  test("a precise key names ONE overload with a symbolic name") {
    val ph = new MemberRenameTransform(Map("com.math.Vec2#add(Vec2)" -> "+"))
    val r  = run(vectors, ph)
    assertEquals(ph.policyReport.findings, Nil, ph.policyReport.render)
    // the (Vec2) overload is renamed to `+` with @targetName
    val c = code(r.out)
    assert(c.contains("def +(other"), r.out)
    // the (float,float) overload keeps its name `add`
    assert(c.contains("def add(dx"), r.out)
    // the @targetName only appears on the renamed overload
    assert(r.out.contains("@scala.annotation.targetName(\"add\")"), r.out)
  }

  test("`unary_-` is valid for a NULLARY method, and emits @targetName") {
    val ph = new MemberRenameTransform(Map("com.math.Vec2#negate" -> "unary_-"))
    val r  = run(vectors, ph)
    assertEquals(ph.policyReport.findings, Nil, ph.policyReport.render)
    assertEquals(r.nameOf("com.math.Vec2#negate"), Some("unary_-"))
    assert(r.out.contains("@scala.annotation.targetName(\"negate\")"), r.out)
    assert(code(r.out).contains("def unary_-"), r.out)
  }

  test("`unary_-` on a method with PARAMETERS is refused as malformed") {
    // `add(Vec2)` takes a parameter, so `unary_-` is not valid
    val ph = new MemberRenameTransform(Map("com.math.Vec2#add" -> "unary_-"))
    run(vectors, ph)
    val f = ph.policyReport.findings
    assertEquals(clue(f).size, 1)
    assertEquals(f.head.issue, PolicyIssue.Malformed)
    assert(f.head.detail.contains("prefix operator"), f.head.detail)
    assert(f.head.detail.contains("nullary"), f.head.detail)
  }

  test("an alphanumeric name does NOT emit @targetName — only symbolic ones do") {
    val ph = new MemberRenameTransform(Map("com.math.Vec2#add" -> "plus"))
    val r  = run(vectors, ph)
    assertEquals(ph.policyReport.findings, Nil, ph.policyReport.render)
    assertEquals(r.nameOf("com.math.Vec2#add"), Some("plus"))
    // no @targetName annotation since `plus` is alphanumeric
    assert(!r.out.contains("@scala.annotation.targetName"), r.out)
  }

  test("the merge contract unions symbolic entries and refuses a disagreement") {
    // independent symbolic names merge cleanly
    val m = merge(Map("com.math.Vec2#add" -> "+"), Map("com.math.Vec2#sub" -> "-"))
    assert(m.isRight, m.toString)
    // same member, different symbols refuses
    val l = merge(Map("com.math.Vec2#add" -> "+"), Map("com.math.Vec2#add" -> "*"))
    assert(clue(l).isLeft)
  }

  test("fingerprint segment is empty for an empty table — §1(b) no-op") {
    assertEquals(new MemberRenameTransform().surfaceFingerprint, "")
    // …and non-empty when entries exist
    assert(new MemberRenameTransform(Map("a#b" -> "+")).surfaceFingerprint.nonEmpty)
  }

  test("MemberRenamer.isSymbolic classifies names correctly") {
    // symbolic operators
    assert(MemberRenamer.isSymbolic("+"))
    assert(MemberRenamer.isSymbolic("*"))
    assert(MemberRenamer.isSymbolic("<="))
    assert(MemberRenamer.isSymbolic("+="))
    assert(MemberRenamer.isSymbolic("++"))
    // prefix operators
    assert(MemberRenamer.isSymbolic("unary_-"))
    assert(MemberRenamer.isSymbolic("unary_!"))
    assert(MemberRenamer.isSymbolic("unary_~"))
    // NOT symbolic
    assert(!MemberRenamer.isSymbolic("add"))
    assert(!MemberRenamer.isSymbolic("close"))
    assert(!MemberRenamer.isSymbolic("closeWindow"))
    assert(!MemberRenamer.isSymbolic(""))
    // edge: `unary_` alone is NOT a valid prefix name (no trailing operator char)
    assert(!MemberRenamer.isSymbolic("unary_"))
    // edge: `unary_ab` is not valid (more than one char after the prefix)
    assert(!MemberRenamer.isSymbolic("unary_ab"))
  }

  test("MemberRenamer.isValidMemberName accepts both families") {
    // alphanumeric
    assert(MemberRenamer.isValidMemberName("add"))
    assert(MemberRenamer.isValidMemberName("closeWindow"))
    assert(MemberRenamer.isValidMemberName("style$shadow"))
    // symbolic
    assert(MemberRenamer.isValidMemberName("+"))
    assert(MemberRenamer.isValidMemberName("unary_-"))
    // invalid
    assert(!MemberRenamer.isValidMemberName(""))
    assert(!MemberRenamer.isValidMemberName("a.b"))
    assert(!MemberRenamer.isValidMemberName("a b"))
    assert(!MemberRenamer.isValidMemberName("2close"))
  }
