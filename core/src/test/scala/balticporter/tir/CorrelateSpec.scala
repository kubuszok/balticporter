package balticporter.tir

class CorrelateSpec extends munit.FunSuite:

  private def e(unit: String, member: String, s: Int, en: Int, jl: Int, scope: String = "main") =
    SrcMap.Entry(unit, member, "def", s, en, unit.replace('.', '/') + ".java", jl, "d", scope)

  private val idx = SrcMap.Index.of(List(
    SrcMap.Entry("p.Buf", "p.Buf", "class", 1, 90, "p/Buf.java", 3, "d", "main"),
    e("p.Buf", "p.Buf#add(int)", 10, 20, 40),
    SrcMap.Entry("p.BufTest", "p.BufTest", "class", 1, 30, "p/BufTest.java", 3, "d", "test"),
    SrcMap.Entry("p.BufTest", "p.BufTest#<stmt1>", "stmt", 4, 8, "p/BufTest.java", 12, "d", "test"),
  ))

  // =========================================================================================
  // scalac
  // =========================================================================================

  // verbatim shape of a dotty report, both header forms
  private val compileLog =
    """|-- [E007] Type Mismatch Error: /w/port/src_managed/main/scala/p/Buf.scala:12:20 ------
       |12 |    return x + 1
       |   |           ^^^^^
       |   |           Found:    (x : String)
       |   |           Required: Int
       |-- Error: /w/port/src_managed/main/scala/p/Buf.scala:15:4 ------------------------
       |15 |  def this() = this()
       |   |  ^
       |   |  secondary constructor must call a preceding constructor
       |-- [E006] Not Found Error: /w/other/Shim.scala:3:2 -------------------------------------
       | 3 |  nope
       |   |  ^^^^
       |   |  Not found: nope
       |2 errors found
       |""".stripMargin

  test("both the coded and the BARE error header are parsed — counting only coded ones undercounts") {
    val es = Correlate.parseScalac(compileLog)
    assertEquals(es.size, 3)
    assertEquals(es.map(_.code), List("E007", "", "E006"))
    assertEquals(es(1).message, "secondary constructor must call a preceding constructor")
    assertEquals(es.head.line, 12)
  }

  test("an error is located to the MEMBER and the Java it came from") {
    val ls = Correlate.locateErrors(Correlate.parseScalac(compileLog), idx)
    assertEquals(ls.head.lane, Correlate.Lane.EngineGap)
    assertEquals(ls.head.entry.map(_.member), Some("p.Buf#add(int)"))
    assertEquals(ls.head.entry.map(_.javaAt), Some("p/Buf.java:40"))
  }

  test("an error outside the source map is UNMAPPED, not silently an engine gap") {
    val ls = Correlate.locateErrors(Correlate.parseScalac(compileLog), idx)
    assertEquals(ls(2).lane, Correlate.Lane.Unmapped)   // an injected shim, not emitted code
    assertEquals(ls(1).lane, Correlate.Lane.EngineGap)  // between members -> the unit entry
  }

  test("with a marker set, an error at a marked member moves to the Approx lane (Stage 2 seam)") {
    val ls = Correlate.locateErrors(Correlate.parseScalac(compileLog), idx, Set("p.Buf\tp.Buf#add(int)"))
    assertEquals(ls.head.lane, Correlate.Lane.Approx)
    // …and an EMPTY marker set — the state today — is a legal input that classifies everything
    assert(Correlate.locateErrors(Correlate.parseScalac(compileLog), idx).forall(_.lane != Correlate.Lane.Approx))
  }

  // =========================================================================================
  // the test runner — the lane that catches CLAUDE.md §4.4
  // =========================================================================================

  // verbatim shape of MUnit under scala-cli, including the two cases that anchor differently
  private val testLog =
    """|p.BufTest:
       |  + addsOne 0.01s
       |  + growsTwice 0.0s
       |==> X p.BufTest.wraps  0.001s java.lang.ArrayIndexOutOfBoundsException: Index 8 out of bounds
       |    at p.Buf.add(Buf.scala:14)
       |    at p.BufTest.$init$$$anonfun$3(BufTest.scala:6)
       |==> X p.BufTest.compares  0.0s munit.ComparisonFailException: /w/p/BufTest.scala:6
       |    at munit.Assertions.assertEquals(Assertions.scala:70)
       |    at p.BufTest.$init$$$anonfun$4(BufTest.scala:7)
       |p.OtherTest:
       |  + fine 0.0s
       |""".stripMargin

  test("passes and failures are parsed, with the failure's stack") {
    val os = Correlate.parseTests(testLog)
    assertEquals(os.count(_.status == "pass"), 3)
    assertEquals(os.count(_.status == "fail"), 2)
    assertEquals(os.find(_.name == "wraps").map(_.suite), Some("p.BufTest"))
    assertEquals(os.find(_.name == "wraps").map(_.frames.size), Some(2))
    assertEquals(os.find(_.name == "fine").map(_.suite), Some("p.OtherTest"))
  }

  test("a failure that threw INSIDE the library anchors on the library member — the §4.4 case") {
    val t = Correlate.locateTests(Correlate.parseTests(testLog), idx).find(_.outcome.name == "wraps").get
    assertEquals(t.anchor, "main-frame")
    assertEquals(t.entry.map(_.member), Some("p.Buf#add(int)"))
    assertEquals(t.entry.map(_.javaAt), Some("p/Buf.java:40"))
  }

  test("a bare assertion mismatch anchors on the TEST body — weaker, and SAID to be weaker") {
    val t = Correlate.locateTests(Correlate.parseTests(testLog), idx).find(_.outcome.name == "compares").get
    // munit frames resolve to nothing; the deepest ported frame is the test itself
    assertEquals(t.anchor, "test-frame")
    assertEquals(t.entry.map(_.member), Some("p.BufTest#<stmt1>"))
    assertEquals(t.entry.map(_.javaAt), Some("p/BufTest.java:12"))
  }

  test("a member whose emitted text changed since the baseline is FLAGGED on a failing test") {
    val ts = Correlate.locateTests(Correlate.parseTests(testLog), idx, Nil, Set("p.Buf\tp.Buf#add(int)"))
    assertEquals(ts.find(_.outcome.name == "wraps").map(_.digestChanged), Some(true))
    assertEquals(ts.find(_.outcome.name == "compares").map(_.digestChanged), Some(false))
  }

  // =========================================================================================
  // pass/fail diff and expected failures
  // =========================================================================================

  test("an EXPECTED failure is not a regression, and the rule is DATA, not a name in the engine") {
    val expected = List(Correlate.Expected("p.BufTest", "wraps", "type substituted by the port"))
    val ts = Correlate.locateTests(Correlate.parseTests(testLog), idx, expected)
    val d  = Correlate.diffTests(Map.empty, ts)
    assertEquals(d.expectedFailing.map(_.outcome.name), List("wraps"))
    assertEquals(d.newlyFailing.map(_.outcome.name), List("compares"))
    // a whole suite can be declared expected
    val all = Correlate.locateTests(Correlate.parseTests(testLog), idx, List(Correlate.Expected("p.BufTest", "*", "r")))
    assertEquals(Correlate.diffTests(Map.empty, all).newlyFailing, Nil)
  }

  test("newly-failing / newly-passing / still-failing against a baseline") {
    val ts = Correlate.locateTests(Correlate.parseTests(testLog), idx)
    val base = Map(
      "p.BufTest\twraps"    -> "fail",   // still failing
      "p.BufTest\taddsOne"  -> "fail",   // newly passing
      "p.BufTest\tcompares" -> "pass",   // newly failing
      "p.Gone\tvanished"    -> "pass",   // did not run at all
    )
    val d = Correlate.diffTests(base, ts)
    assertEquals(d.stillFailing.map(_.outcome.name), List("wraps"))
    assertEquals(d.newlyPassing.map(_.outcome.name), List("addsOne"))
    assertEquals(d.newlyFailing.map(_.outcome.name), List("compares"))
    assertEquals(d.disappeared, List("p.Gone\tvanished"))
    assert(d.regressed)
  }

  test("an expected failure that started PASSING is reported — a substitution that works is news") {
    val ts = Correlate.locateTests(Correlate.parseTests(testLog), idx,
                                   List(Correlate.Expected("p.BufTest", "addsOne", "was substituted")))
    assertEquals(Correlate.diffTests(Map.empty, ts).expectedButPassing.map(_.test), List("addsOne"))
  }

  test("an IGNORED test is recorded as ignored, not lost — MUnit prints it with no duration") {
    val os = Correlate.parseTests("p.S:\n==> i p.S.skipped ignored because\n  + ok 0.0s\n")
    assertEquals(os.map(o => o.name -> o.status), List("skipped" -> "ignored", "ok" -> "pass"))
  }

  test("a test that stopped running is not a test that passed") {
    val d = Correlate.diffTests(Map("p.A\tt" -> "pass"), Nil)
    assertEquals(d.disappeared, List("p.A\tt"))
    assert(Correlate.renderTests(Nil, d).contains("DID NOT RUN"))
  }

  // =========================================================================================
  // expected BY CONSTRUCTION — derived from the port's own drops, not from a hand-written list
  // =========================================================================================

  test("a failure whose stack reaches a DROPPED type is expected by construction, with no list") {
    val ts = Correlate.locateTests(Correlate.parseTests(testLog), idx, Nil, Set.empty, Set("p.Buf"))
    val d  = Correlate.diffTests(Map.empty, ts)
    // `wraps` threw inside p.Buf, which the port drops; `compares` did not reach it.
    assertEquals(d.expectedFailing.map(_.outcome.name), List("wraps"))
    assertEquals(d.newlyFailing.map(_.outcome.name), List("compares"))
    val why = d.expectedFailing.head.expected.get
    assert(why.derived, "a drop-explained failure must be marked DERIVED, never declared")
    assertEquals(why.source, "derived")
    assert(clue(why.reason).contains("p.Buf"))
  }

  test("no dropped types ⇒ the same failure is a plain regression: the derivation is what classifies it") {
    val ts = Correlate.locateTests(Correlate.parseTests(testLog), idx, Nil, Set.empty, Set.empty)
    assertEquals(Correlate.diffTests(Map.empty, ts).expectedFailing, Nil)
    assertEquals(Correlate.diffTests(Map.empty, ts).newlyFailing.size, 2)
  }

  test("the DECLARED hatch still classifies a failure no drop explains, and stays distinguishable") {
    val ts = Correlate.locateTests(Correlate.parseTests(testLog), idx,
                                   List(Correlate.Expected("p.BufTest", "compares", "upstream asserts a JVM locale")),
                                   Set.empty, Set("p.Buf"))
    val d = Correlate.diffTests(Map.empty, ts)
    assertEquals(d.newlyFailing, Nil)
    assertEquals(d.expectedFailing.map(t => t.outcome.name -> t.expected.get.source).sorted,
                 List("compares" -> "declared", "wraps" -> "derived"))
    // the artifact keeps the two apart, so a declared claim can never be read as a fact about the manifest
    val tsv = d.expectedFailing.map(_.tsv).mkString("\n")
    assert(clue(tsv).contains("expected#derived") && tsv.contains("expected#declared"))
  }

  test("a DERIVED expectation never applies to a passing test — it has no failure stack to reach") {
    val ts = Correlate.locateTests(Correlate.parseTests(testLog), idx, Nil, Set.empty, Set("p.Buf", "p.BufTest"))
    assertEquals(ts.filter(_.outcome.status == "pass").flatMap(_.expected), Nil)
    assertEquals(Correlate.diffTests(Map.empty, ts).expectedButPassing, Nil)
  }

  test("dropped-types.tsv round-trips, comments and blanks ignored") {
    val p = java.nio.file.Files.createTempFile("dropped", ".tsv")
    java.nio.file.Files.writeString(p, s"${Correlate.DroppedHeader}\np.Buf\n\n# a note\np.Other\n")
    assertEquals(Correlate.parseDropped(p), Set("p.Buf", "p.Other"))
    assertEquals(Correlate.parseDropped(p.resolveSibling("nope.tsv")), Set.empty[String])
  }

  test("changedMembers is symmetric: added, removed and altered all count") {
    val b = Map("u\tm1" -> "a", "u\tm2" -> "b")
    val l = Map("u\tm1" -> "a", "u\tm2" -> "B", "u\tm3" -> "c")
    assertEquals(Correlate.changedMembers(b, l), Set("u\tm2", "u\tm3"))
  }
