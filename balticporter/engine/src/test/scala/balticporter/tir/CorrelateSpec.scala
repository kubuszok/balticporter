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

  // ---- the ANCHOR half of a DECLARED entry -------------------------------------------------

  test("a declared row may be ANCHORED, and holds only while the failure is still at that class") {
    // `wraps` anchors `main-frame` at `p.Buf` (see the anchoring tests above), so the row holds…
    val at = List(Correlate.Expected("p.BufTest", "wraps", "substituted", frame = Some("p.Buf")))
    val d  = Correlate.diffTests(Map.empty, Correlate.locateTests(Correlate.parseTests(testLog), idx, at))
    assertEquals(d.expectedFailing.map(_.outcome.name), List("wraps"))
    assertEquals(d.staleExpectations, Nil)
  }

  test("…and a NEW failure with a DIFFERENT cause in the same test is no longer absorbed silently") {
    // Keyed on (suite, test) alone this row matches whatever `wraps` does: the artifact reads
    // `expected#declared`, no count moves, and the sentence in the `reason` column is about a
    // failure that is not happening. Anchored, the claim stops holding the moment the failure moves.
    val stale = List(Correlate.Expected("p.BufTest", "wraps", "substituted", frame = Some("p.SomewhereElse")))
    val d = Correlate.diffTests(Map.empty, Correlate.locateTests(Correlate.parseTests(testLog), idx, stale))
    assertEquals(d.expectedFailing, Nil)
    assertEquals(d.newlyFailing.map(_.outcome.name), List("compares", "wraps"))
    assertEquals(d.staleExpectations.map(_.outcome.name), List("wraps"))
    // the artifact says WHICH kind of unexpected it is, so the row is findable from the file
    val tsv = d.staleExpectations.map(_.tsv).mkString("\n")
    assert(clue(tsv).contains("unexpected#stale-declaration"))
    assert(clue(Correlate.renderTests(Nil, d)).contains("NO LONGER HOLDS"))
  }

  test("a row with NO anchor keeps today's behaviour exactly — the column is optional") {
    // the compatibility half: these files predate the column, and reading an absent anchor as "does
    // not match" would turn every port's escape hatch into a wall of unexpected failures.
    val at = List(Correlate.Expected("p.BufTest", "wraps", "substituted"))
    val d  = Correlate.diffTests(Map.empty, Correlate.locateTests(Correlate.parseTests(testLog), idx, at))
    assertEquals(d.expectedFailing.map(_.outcome.name), List("wraps"))
    assertEquals(d.staleExpectations, Nil)
  }

  test("the anchor column is PARSED from the file, tagged rather than positional") {
    val f = java.nio.file.Files.createTempFile("bp-expected", ".tsv")
    java.nio.file.Files.writeString(f,
      s"${Correlate.ExpectedHeader}\n" +
      "p.BufTest\twraps\tthe reason, with prose\tframe=p.Buf\n" +
      "p.BufTest\tcompares\tan older row with no anchor at all\n")
    val List(a, b) = Correlate.parseExpected(f): @unchecked
    assertEquals(a.frame, Some("p.Buf"))
    assertEquals(a.reason, "the reason, with prose")
    // the reason has always absorbed every trailing field, so a positional column would be read as
    // reason text by one side and as an anchor by the other.
    assertEquals(b.frame, scala.None)
    assertEquals(b.reason, "an older row with no anchor at all")
    java.nio.file.Files.deleteIfExists(f)
  }

  test("a DERIVED expectation is never reported stale — a drop is a fact, not a claim") {
    // a declared row beside a drop that explains the same failure: the drop classifies it, and the
    // claim is neither wrong nor the reader's next step.
    val dropped = Set(Correlate.Dropped("p.Buf"))
    val stale   = List(Correlate.Expected("p.BufTest", "wraps", "something else", frame = Some("p.Nope")))
    val ts = Correlate.locateTests(Correlate.parseTests(testLog), idx, stale, Set.empty, dropped)
    val d  = Correlate.diffTests(Map.empty, ts)
    assertEquals(d.expectedFailing.map(_.expected.get.source), List("derived"))
    assertEquals(d.staleExpectations, Nil)
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

  // -----------------------------------------------------------------------------------------
  // MUnit's THIRD terminal marker — a test that never ran
  // -----------------------------------------------------------------------------------------

  private val skipLog =
    """|p.BufTest:
       |  + addsOne 0.01s
       |==> X p.BufTest.wraps  0.001s java.lang.ArrayIndexOutOfBoundsException: Index 8
       |    at p.Buf.add(Buf.scala:14)
       |==> s p.BufTest.grows skipped 0.0s
       |==> s p.BufTest.shrinks skipped 0.0s
       |""".stripMargin

  test("a SKIPPED test is recorded as skipped — dropping the marker loses the test entirely") {
    val os = Correlate.parseTests(skipLog)
    assertEquals(os.map(o => o.name -> o.status),
                 List("addsOne" -> "pass", "wraps" -> "fail", "grows" -> "skipped", "shrinks" -> "skipped"))
    // the name must NOT swallow the word `skipped` — widening the failure pattern to `[XiIs]` does
    // exactly that, and the test then appears under a name no baseline can ever match.
    assert(os.forall(o => !o.name.contains("skipped")), clue(os.map(_.name)))
    assertEquals(os.find(_.name == "grows").map(_.suite), Some("p.BufTest"))
  }

  test("a skip is neither a pass nor a fail, and it GATES: pass -> skipped is a regression") {
    val ts = Correlate.locateTests(Correlate.parseTests(skipLog), idx)
    val d  = Correlate.diffTests(Map("p.BufTest\taddsOne" -> "pass", "p.BufTest\twraps" -> "fail",
                                     "p.BufTest\tgrows"   -> "pass", "p.BufTest\tshrinks" -> "pass"), ts)
    assertEquals(d.newlySkipped.map(_.outcome.name), List("grows", "shrinks"))
    // it is NOT any of the existing buckets — which is precisely why it moved no gate before
    assertEquals(d.newlyFailing, Nil)
    assertEquals(d.disappeared, Nil)
    assert(d.regressed, "a test that stopped RUNNING must fail the gate")
    val r = Correlate.renderTests(ts, d)
    assert(clue(r).contains("SKIPPED=2"))
    assert(clue(r).contains("NEWLY SKIPPED"))
  }

  test("a skip the baseline already records as skipped is not a new regression") {
    val ts = Correlate.locateTests(Correlate.parseTests(skipLog), idx)
    val d  = Correlate.diffTests(Map("p.BufTest\taddsOne" -> "pass", "p.BufTest\twraps" -> "fail",
                                     "p.BufTest\tgrows"   -> "skipped", "p.BufTest\tshrinks" -> "skipped"), ts)
    assertEquals(d.newlySkipped, Nil)
    assert(!d.regressed)
  }

  test("a test that stopped running is not a test that passed — and it GATES") {
    // …which it did not, for the life of the field. `disappeared` was rendered and left out of
    // `regressed` on the grounds that deleting a test is a decision somebody made — true of a
    // DELETION and false of the failure this project actually has: a CONVERSION regression that
    // stops emitting a suite removes its tests from both sides at once, so no pass count falls, no
    // fail count rises, and the run reports success on a smaller suite. That is the same shape as
    // a skip (`newlySkipped`, already gated) with the row gone instead of unrun.
    //
    // A deliberate deletion is acknowledged the way every other baseline change is — by
    // re-accepting — which is what makes "somebody decided this" a recorded fact rather than an
    // assumption the gate has to make.
    val d = Correlate.diffTests(Map("p.A\tt" -> "pass"), Nil)
    assertEquals(d.disappeared, List("p.A\tt"))
    assert(Correlate.renderTests(Nil, d).contains("DID NOT RUN"))
    assert(d.regressed, "a test that stopped RUNNING must fail the gate, however it stopped")
  }

  test("…and a run with nothing disappeared is not a regression — the negative test") {
    val d = Correlate.diffTests(Map.empty, Nil)
    assertEquals(d.disappeared, Nil)
    assert(!d.regressed)
  }

  // =========================================================================================
  // expected BY CONSTRUCTION — derived from the port's own drops, not from a hand-written list
  // =========================================================================================

  test("a failure whose stack reaches a DROPPED type is expected by construction, with no list") {
    val ts = Correlate.locateTests(Correlate.parseTests(testLog), idx, Nil, Set.empty, Set(Correlate.Dropped("p.Buf")))
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
                                   Set.empty, Set(Correlate.Dropped("p.Buf")))
    val d = Correlate.diffTests(Map.empty, ts)
    assertEquals(d.newlyFailing, Nil)
    assertEquals(d.expectedFailing.map(t => t.outcome.name -> t.expected.get.source).sorted,
                 List("compares" -> "declared", "wraps" -> "derived"))
    // the artifact keeps the two apart, so a declared claim can never be read as a fact about the manifest
    val tsv = d.expectedFailing.map(_.tsv).mkString("\n")
    assert(clue(tsv).contains("expected#derived") && tsv.contains("expected#declared"))
  }

  test("a DERIVED expectation never applies to a passing test — it has no failure stack to reach") {
    val ts = Correlate.locateTests(Correlate.parseTests(testLog), idx, Nil, Set.empty, Set(Correlate.Dropped("p.Buf"), Correlate.Dropped("p.BufTest")))
    assertEquals(ts.filter(_.outcome.status == "pass").flatMap(_.expected), Nil)
    assertEquals(Correlate.diffTests(Map.empty, ts).expectedButPassing, Nil)
  }

  // -----------------------------------------------------------------------------------------
  // …across a PACKAGE RENAME, which is the case that had never once worked
  // -----------------------------------------------------------------------------------------

  /** the same failure as `testLog`, but the port emitted its library into another namespace: the
    * drop is declared upstream (`p.Buf`) and every frame says `sge.Buf`. Note the second suite,
    * whose package merely SHARES A PREFIX with the dropped type. */
  private val renamedLog =
    """|sge.BufTest:
       |==> X sge.BufTest.wraps  0.001s java.lang.UnsupportedOperationException: not ported
       |    at sge.Buf$.codec(Buf.scala:57)
       |    at sge.Buf.add(Buf.scala:14)
       |    at sge.BufTest.$init$$$anonfun$1(BufTest.scala:6)
       |sge.BufferedTest:
       |==> X sge.BufferedTest.grows  0.0s java.lang.ArrayIndexOutOfBoundsException: 8
       |    at sge.Buffered.grow(Buffered.scala:14)
       |    at sge.BufferedTest.$init$$$anonfun$1(BufferedTest.scala:6)
       |""".stripMargin

  test("a drop declared UPSTREAM classifies a failure whose frames are in the EMITTED namespace") {
    // The defect this closes: `dropped-types.tsv` held `p.Buf` (policy is written upstream, §4.56),
    // every frame said `sge.Buf`, and the comparison matched nothing — on every renaming port,
    // silently, for the whole life of the rule. The port writes both names now.
    val ts = Correlate.locateTests(Correlate.parseTests(renamedLog), SrcMap.Index.empty,
                                   Nil, Set.empty, Set(Correlate.Dropped("p.Buf", "sge.Buf")))
    val d  = Correlate.diffTests(Map.empty, ts)
    assertEquals(d.expectedFailing.map(_.outcome.name), List("wraps"))
    val why = d.expectedFailing.head.expected.get
    assert(why.derived, "a drop-explained failure must be marked DERIVED, never declared")
    // the reason names the MANIFEST key — the thing a reader would have to go and edit — and says
    // what it is emitted as, because that is the only name the stack ever showed.
    assert(clue(why.reason).contains("p.Buf"), "the reason must name the upstream key")
    assert(clue(why.reason).contains("sge.Buf"), "…and the emitted name the frame actually carried")
    // …and it fires with NO source map at all: a dropped type is the one type the port does not
    // emit, so it has no srcmap entry to resolve through. Matching resolved units could never work.
    assert(d.expectedFailing.head.entry.isEmpty)
  }

  test("a package that merely SHARES A PREFIX with a drop is not covered — com.foo vs com.foobar") {
    val ts = Correlate.locateTests(Correlate.parseTests(renamedLog), SrcMap.Index.empty,
                                   Nil, Set.empty, Set(Correlate.Dropped("p.Buf", "sge.Buf")))
    val d  = Correlate.diffTests(Map.empty, ts)
    // `sge.Buffered` and `sge.BufferedTest` both start with `sge.Buf`; neither is under it.
    assertEquals(d.newlyFailing.map(_.outcome.name), List("grows"))
    assertEquals(d.expectedFailing.map(_.outcome.name), List("wraps"))
    // stated directly, at the rule rather than through a fixture
    assert(Correlate.covers("sge.Buf", "sge.Buf"))
    assert(Correlate.covers("sge.Buf", "sge.Buf$"))          // companion
    assert(Correlate.covers("sge.Buf", "sge.Buf$Ref"))       // nested type
    assert(Correlate.covers("sge.Buf", "sge.Buf$$anonfun$3")) // lambda
    assert(Correlate.covers("sge.Buf", "sge.Buf#add"))       // member key form
    assert(!Correlate.covers("sge.Buf", "sge.Buffered"))
    assert(!Correlate.covers("sge.Buf", "sge.BufTest"))
    assert(!Correlate.covers("sge.Buf", "sge.Bu"))
    assert(!Correlate.covers("com.foo", "com.foobar.Thing"))
  }

  test("dropped-types.tsv round-trips BOTH namespaces; a one-column line means no rename") {
    val p = java.nio.file.Files.createTempFile("dropped", ".tsv")
    java.nio.file.Files.writeString(p,
      s"${Correlate.DroppedHeader}\np.Buf\tq.Buf\n\n# a note\np.Other\n")
    assertEquals(Correlate.parseDropped(p),
                 Set(Correlate.Dropped("p.Buf", "q.Buf"), Correlate.Dropped("p.Other", "p.Other")))
    assertEquals(Correlate.parseDropped(p.resolveSibling("nope.tsv")), Set.empty[Correlate.Dropped])
  }

  test("changedMembers is symmetric: added, removed and altered all count") {
    val b = Map("u\tm1" -> "a", "u\tm2" -> "b")
    val l = Map("u\tm1" -> "a", "u\tm2" -> "B", "u\tm3" -> "c")
    assertEquals(Correlate.changedMembers(b, l), Set("u\tm2", "u\tm3"))
  }
