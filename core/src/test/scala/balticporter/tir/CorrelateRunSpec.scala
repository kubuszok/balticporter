package balticporter.tir

import java.nio.file.{Files, Path}

/** [[CorrelateRun]] as a library call — the in-process half of what `CorrelateMain` does from a
  * shell.
  *
  * The correlation used to exist ONLY as a `main`, so the only way to run it was to start a second
  * JVM. That is right for a measure script (the compiler and the test runner have long since
  * exited) and wrong for a porting program that drives the compile itself, so the request is now a
  * value and `balticporter.runner.PortRun.correlate` calls it directly.
  */
class CorrelateRunSpec extends munit.FunSuite:

  private def fixture(): Path =
    val port = Files.createTempDirectory("bp-correlate")
    val run  = port.resolve("run-latest")
    Files.createDirectories(run)
    Files.createDirectories(port.resolve("baseline"))
    Files.writeString(run.resolve("srcmap.tsv"), List(
      SrcMap.Header,
      SrcMap.Entry("p.Buf", "p.Buf", "class", 1, 90, "p/Buf.java", 3, "d").tsv,
      SrcMap.Entry("p.Buf", "p.Buf#add(int)", "def", 10, 20, "p/Buf.java", 40, "d").tsv,
      SrcMap.Entry("p.BufTest", "p.BufTest", "class", 1, 30, "p/BufTest.java", 3, "d").tsv,
    ).mkString("", "\n", "\n"))
    Files.writeString(run.resolve("dropped-types.tsv"), s"${Correlate.DroppedHeader}\np.Buf\n")
    port

  private val testLog =
    """|p.BufTest:
       |  + addsOne 0.01s
       |==> X p.BufTest.wraps  0.001s java.lang.UnsupportedOperationException: not ported
       |    at p.Buf.add(Buf.scala:14)
       |    at p.BufTest.$init$$$anonfun$3(BufTest.scala:6)
       |""".stripMargin

  test("a full correlation runs in-process and writes the same artifacts a second JVM would") {
    val port = fixture()
    val log  = port.resolve("run.txt")
    Files.writeString(log, testLog)
    val r = CorrelateRun.run(CorrelateRun.Request(
      srcmaps = List("main" -> port.resolve("run-latest/srcmap.tsv")),
      tests   = Some(log),
      out     = port.resolve("run-latest"),
    ))
    assert(Files.isRegularFile(port.resolve("run-latest/tests.tsv")))
    assert(Files.isRegularFile(port.resolve("run-latest/correlate.txt")))
    assert(Files.isRegularFile(port.resolve("run-latest/tests-diff.txt")))
    assertEquals(r.tests.size, 2)
    // the failure is expected BY CONSTRUCTION — its stack reaches a dropped type — so it is not a
    // regression, and no hand-written expected-failures.tsv exists in this fixture at all.
    assert(!r.regressed, clue(r.report))
    assertEquals(r.tests.filter(_.outcome.status == "fail").flatMap(_.expected).map(_.source), List("derived"))
  }

  test("a RELATIVE path is resolved against the engine root, never against the caller's cwd") {
    // sbt's non-forked `run` has the SUBPROJECT as cwd, so a relative path that reads correctly in
    // a shell silently resolves to nothing — and the correlation then reports "0 units" as if the
    // port had no members. Every path in a Request is absolutised before use.
    val rel = CorrelateRun.Request(
      srcmaps  = List("main" -> Path.of("port-report/X/run-latest/srcmap.tsv")),
      scalac   = Some(Path.of("out.txt")),
      out      = Path.of("port-report/X/run-latest"),
      baseline = Some(Path.of("port-report/X/baseline")),
    ).absolute
    assert(rel.out.isAbsolute && rel.baselineDir.isAbsolute)
    assert(rel.srcmaps.forall(_._2.isAbsolute) && rel.scalac.forall(_.isAbsolute))
    assertEquals(rel.out, DebugFlags.root.resolve("port-report/X/run-latest").normalize)
  }

  test("a --tests file that does not exist is FATAL — never a green report over a file never opened") {
    // The defect: a missing input was one line on stderr, which the measure scripts filter out of
    // the correlate block by design. The run then wrote a header-only tests.tsv and a headline of
    // "tests 0 passing, 0 failing" — a whole suite reported as green because a path was wrong.
    val port = fixture()
    val e = intercept[CorrelateRun.MissingInput] {
      CorrelateRun.run(CorrelateRun.Request(
        srcmaps = List("main" -> port.resolve("run-latest/srcmap.tsv")),
        tests   = Some(port.resolve("nope.txt")),
        out     = port.resolve("run-latest"),
      ))
    }
    assertEquals(e.paths.map(_.getFileName.toString), List("nope.txt"))
    assert(clue(e.getMessage).contains("NOT FOUND"))
    // …and nothing was written: a run that refuses must not leave an artifact claiming otherwise
    assert(!Files.exists(port.resolve("run-latest/tests.tsv")))
  }

  test("an ABSOLUTE path is left alone, and the default baseline is <out>/../baseline") {
    val port = fixture()
    val req  = CorrelateRun.Request(out = port.resolve("run-latest")).absolute
    assertEquals(req.out, port.resolve("run-latest"))
    assertEquals(req.baselineDir, port.resolve("baseline"))
  }
