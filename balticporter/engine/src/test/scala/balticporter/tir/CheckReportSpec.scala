package balticporter.tir

import java.nio.file.{Files, Path}

class CheckReportSpec extends munit.FunSuite:

  private def f(check: String, kind: String, owner: String, path: String, line: Int, detail: String) =
    CheckReport.Finding(check, kind, owner, path, line, detail)

  private def withProps[A](kv: (String, String)*)(body: => A): A =
    val saved = kv.map((k, _) => k -> Option(System.getProperty(k)))
    kv.foreach((k, v) => System.setProperty(k, v))
    try body
    finally saved.foreach {
      case (k, Some(v)) => System.setProperty(k, v)
      case (k, scala.None) => System.clearProperty(k)
    }

  test("a finding's id ignores the LINE — an upstream whitespace edit must not orphan a baseline entry") {
    val a = f("omissions", "super(args) dropped", "p.Foo", "p/Foo.java", 12, "1 argument(s) discarded")
    assertEquals(a.id, a.copy(line = 900).id)
    assertNotEquals(a.id, a.copy(owner = "p.Bar").id)
    assertNotEquals(a.id, a.copy(detail = "2 argument(s) discarded").id)
  }

  test("findings that differ ONLY by line are still distinguishable — the seq suffix") {
    // excluding the line from the id is what keeps a baseline entry alive across a whitespace
    // edit; without a disambiguator it also merges a member's three identical findings into one,
    // and two of the three could then be fixed invisibly.
    val three = List(
      f("omissions", "super(args) dropped", "p.Foo", "p/Foo.java", 43, "3 argument(s) discarded"),
      f("omissions", "super(args) dropped", "p.Foo", "p/Foo.java", 55, "3 argument(s) discarded"),
      f("omissions", "super(args) dropped", "p.Foo", "p/Foo.java", 59, "3 argument(s) discarded"),
    )
    val ids = CheckReport.assignSeq(three).map(_.id)
    assertEquals(ids.distinct.size, 3)
    assertEquals(ids.head, three.head.baseId) // the first keeps the bare id
    assertEquals(ids(1), s"${three.head.baseId}/2")
    // and the id survives a line shift of the whole group
    val shifted = three.map(x => x.copy(line = x.line + 10))
    assertEquals(CheckReport.assignSeq(shifted).map(_.id), ids)
  }

  test("tsv is one line per finding and round-trips") {
    val a = f("omissions", "k", "p.Foo", "p/Foo.java", 12, "detail\twith\ttabs\nand a newline")
    assertEquals(a.tsv.count(_ == '\n'), 0)
    val back = CheckReport.parse(a.tsv).get
    assertEquals(back.check, "omissions")
    assertEquals(back.line, 12)
    assertEquals(back.detail, "detail with tabs and a newline")
    assertEquals(CheckReport.parse(CheckReport.Header), scala.None)
  }

  test("diff reports before->after per check plus what appeared and disappeared") {
    val base = List(
      f("omissions", "k", "p.A", "p/A.java", 1, "x"),
      f("omissions", "k", "p.B", "p/B.java", 2, "x"),
      f("signature", "call arity", "p.C#m", "p/C.java", 3, "expects 1, found 2"),
    )
    val now = List(
      f("omissions", "k", "p.A", "p/A.java", 5, "x"),   // same finding, moved line
      f("omissions", "k", "p.D", "p/D.java", 9, "x"),   // new
      f("signature", "call arity", "p.C#m", "p/C.java", 3, "expects 1, found 2"),
    )
    val d = CheckReport.diff(base, now, Set("omissions", "signature"), Set("omissions", "signature"), hasBaseline = true)
    val om = d.deltas.find(_.check == "omissions").get
    assertEquals(om.before, 2)
    assertEquals(om.after, 2)
    assertEquals(om.appeared.map(_.owner), List("p.D"))
    assertEquals(om.disappeared.map(_.owner), List("p.B"))
    val sig = d.deltas.find(_.check == "signature").get
    assertEquals(sig.appeared, Nil)
    assertEquals(sig.disappeared, Nil)
    assertEquals(CheckReport.subject(d), "omissions 2, signature 1")
  }

  test("the commit subject is COMPUTED as before->after, not remembered") {
    val base = (1 to 31).map(i => f("omissions", "k", s"p.T$i", "p/T.java", i, "x")).toList
    val now  = (1 to 33).map(i => f("omissions", "k", s"p.T$i", "p/T.java", i, "x")).toList
    val d = CheckReport.diff(base, now, Set("omissions"), Set("omissions"), hasBaseline = true)
    assertEquals(CheckReport.subject(d), "omissions 31->33")
  }

  test("a check that stopped RUNNING is not a check that found nothing") {
    val base = List(f("portability(emitted)", "java.net.", "p.A", "p/A.java", 1, "TermRef — networking"))
    val d = CheckReport.diff(base, Nil, Set("portability(emitted)"), Set("omissions"), hasBaseline = true)
    val p = d.deltas.find(_.check == "portability(emitted)").get
    assertEquals(p.ran, false)
    val rendered = CheckReport.renderDiff(d)
    assert(rendered.contains("CHECK DID NOT RUN"), rendered)
    assert(rendered.contains("is NOT a check that found nothing"), rendered)
    // and it must never be summarised as "1->0", which reads as a fix
    assertEquals(CheckReport.subject(d), "omissions 0, portability(emitted) 1->NOT-RUN")
  }

  test("with no baseline the diff says so and names the command that makes one") {
    val d = CheckReport.diff(Nil, List(f("omissions", "k", "p.A", "p/A.java", 1, "x")), Set.empty, Set("omissions"), hasBaseline = false)
    val r = CheckReport.renderDiff(d)
    assert(r.contains("NO BASELINE"), r)
    assert(r.contains("just baseline-accept"), r)
  }

  test("the written artifact is byte-identical across two runs of the same input (determinism, R3)") {
    val tmp = Files.createTempDirectory("bp-report")
    try
      withProps("balticporter.report" -> "on", "balticporter.reportDir" -> tmp.toString) {
        def run(dst: String): Path =
          CheckReport.reset()
          // recorded in a DIFFERENT order each time: the artifact must not depend on it
          if dst == "a" then
            CheckReport.record("signature", Nil)
            CheckReport.record("omissions", List(f("omissions", "k", "p.B", "p/B.java", 2, "x"), f("omissions", "k", "p.A", "p/A.java", 1, "x")))
          else
            CheckReport.record("omissions", List(f("omissions", "k", "p.A", "p/A.java", 1, "x"), f("omissions", "k", "p.B", "p/B.java", 2, "x")))
            CheckReport.record("signature", Nil)
          val out = tmp.resolve(dst)
          CheckReport.write(out)
          out
        val a = run("a")
        val b = run("b")
        List("findings.tsv", "counts.tsv", "diff.txt", "subject.txt").foreach { n =>
          assertNoDiff(Files.readString(a.resolve(n)), Files.readString(b.resolve(n)), s"$n differed between runs")
        }
        assert(!Files.readString(a.resolve("findings.tsv")).contains(tmp.toString), "an absolute path reached the artifact")
        // an empty check is still REGISTERED — "0" and "never ran" must stay distinguishable
        assert(Files.readString(a.resolve("counts.tsv")).contains("signature\t0"), Files.readString(a.resolve("counts.tsv")))
      }
    finally
      CheckReport.reset()
      Files.walk(tmp).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete(_))
  }

  test("recording is a no-op when reporting is off — a check stays a pure function") {
    withProps("balticporter.report" -> "off") {
      CheckReport.reset()
      CheckReport.record("omissions", List(f("omissions", "k", "p.A", "p/A.java", 1, "x")))
      assertEquals(CheckReport.snapshot(), Map.empty[String, List[CheckReport.Finding]])
    }
  }

  test("a BUILD TOOL's main is not a port identity — reporting stays OFF and no directory is named") {
    // `CheckReport.dir` falls back to `port-report/<sun.java.command's simple name>`, and under a
    // forked test JVM that command is the build's own worker — `sbt.internal.worker1.WorkerMain`
    // under sbt 2. Any suite that turned reporting on without naming a directory therefore
    // published `<subproject>/port-report/WorkerMain/` into the checkout: an artifact write that
    // was gated on a FLAG and not on the artifact layer (§5.1, the `PortMap.write` precedent).
    withProps("sun.java.command" -> "sbt.internal.worker1.WorkerMain --tcp 49786",
              "balticporter.report" -> "on") {
      assertEquals(CheckReport.mainClassKey, scala.None)
      assert(!CheckReport.enabled, "reporting must not turn on for a JVM with no port identity")
      assertEquals(CheckReport.dir.getFileName.toString, CheckReport.NoMainClass)
    }
    // …while a port's OWN migration main still names its directory, which is the measurement
    // identity CLAUDE.md §2.1 keeps stable across a module rename.
    withProps("sun.java.command" -> "com.example.port.WidgetMigrate",
              "balticporter.report" -> "on") {
      assertEquals(CheckReport.mainClassKey, Some("WidgetMigrate"))
      assert(CheckReport.enabled)
      assertEquals(CheckReport.dir.getFileName.toString, "WidgetMigrate")
    }
    // …and an EXPLICIT directory is an identity the caller supplied, so it enables reporting even
    // under the build tool's main.
    withProps("sun.java.command" -> "sbt.internal.worker1.WorkerMain",
              "balticporter.report" -> "on", "balticporter.reportDir" -> "/tmp/bp-explicit") {
      assert(CheckReport.enabled)
      assertEquals(CheckReport.dir.getFileName.toString, "bp-explicit")
    }
  }

  test("with reporting ON but no reportDir, a forked test JVM leaves NOTHING in the checkout") {
    // Asserted on the FILESYSTEM, not on `enabled`: what is being pinned is that nothing was
    // created, and a `false` proves only that a branch was taken. This is the shape PortRunSpec
    // pins for the port map and for correlation, at the layer both of them go through.
    val here   = DebugFlags.root.resolve("port-report")
    def listed = if !Files.exists(here) then Set.empty[String]
                 else Files.walk(here).sorted().toArray.map(_.toString).toSet
    val before = listed
    withProps("balticporter.report" -> "on") {
      CheckReport.reset()
      CheckReport.record("omissions", List(f("omissions", "k", "p.A", "p/A.java", 1, "x")))
      CheckReport.writeNow()
    }
    CheckReport.reset()
    assertEquals(listed, before, "a run nobody asked to publish must not create its own home")
  }

  test("relativise never emits an absolute path when a source root is known") {
    withProps("balticporter.reportPathRoot" -> "/abs/src") {
      assertEquals(CheckReport.relativise("/abs/src/p/Foo.java"), "p/Foo.java")
      assertEquals(CheckReport.relativise("p/Foo.java"), "p/Foo.java") // already relative
    }
  }
