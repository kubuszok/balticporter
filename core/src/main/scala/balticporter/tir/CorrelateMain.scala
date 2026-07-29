package balticporter.tir

import java.nio.file.{Files, Path}

/** The correlation step as a REQUEST, so it can be run in-process or from a command line.
  *
  * ==Why this is not just a `main`==
  *
  * It used to be. `CorrelateMain` was the whole of it, which meant the only way to correlate was to
  * start a second JVM — fine for a shell script that has just run a compiler, useless for a porting
  * program that drives the compile itself and already holds the run directory. Splitting the
  * REQUEST from the argument parsing costs nothing and gives `balticporter.runner.PortRun` an
  * in-process path ([[PortRun.correlate]]) while leaving the standalone entry point intact —
  * correlation must stay runnable against a compile or test log produced independently of a
  * migration run, because that is how an agent debugging a wall actually uses it.
  *
  * ==Paths are ABSOLUTE, and that is load-bearing==
  *
  * Every path in a request is resolved against [[DebugFlags.root]] before use. sbt's non-forked
  * `run` has the SUBPROJECT as its working directory, so a relative path that reads correctly in a
  * shell silently resolves to nothing here, and the whole correlation then reports "0 units" as if
  * the port had no members. [[Request.absolute]] is where that is fixed once; a missing input is
  * additionally NAMED, with the directory it was looked for in.
  */
object CorrelateRun:

  /** @param srcmaps  `scope -> srcmap.tsv`; scope is `main` or `test` so a library frame can be
    *                 preferred over a test frame when anchoring a failure.
    * @param out      where errors.tsv / tests.tsv / correlate.txt go — normally a run directory.
    * @param baseline the promotable artifacts to diff against.
    * @param markers  `unit<TAB>member` regions the engine marked approximate (Stage 2). Absent is
    *                 the state today and every scalac error lands in the engine-gap lane, which is
    *                 the honest answer while the engine marks nothing.
    */
  final case class Request(
      srcmaps: List[(String, Path)] = Nil,
      scalac: Option[Path] = scala.None,
      tests: Option[Path] = scala.None,
      out: Path,
      baseline: Option[Path] = scala.None,
      markers: Option[Path] = scala.None,
  ):
    /** every path made absolute against the engine root — see the class doc. */
    def absolute: Request =
      def abs(p: Path): Path = if p.isAbsolute then p.normalize else DebugFlags.root.resolve(p).normalize
      copy(srcmaps = srcmaps.map((s, p) => s -> abs(p)), scalac = scalac.map(abs), tests = tests.map(abs),
           out = abs(out), baseline = baseline.map(abs), markers = markers.map(abs))

    def baselineDir: Path = baseline.getOrElse(out.getParent.resolve("baseline"))

  final case class Result(report: String, regressed: Boolean, errors: List[Correlate.LocatedError], tests: List[Correlate.LocatedTest])

  /** Run the join and write the artifacts. Prints nothing — the caller decides. */
  def run(req0: Request): Result =
    val req    = req0.absolute
    val outDir = req.out
    val base   = req.baselineDir
    Files.createDirectories(outDir)

    // A missing input file must SAY SO — see the class doc on why a silent one is so expensive.
    (req.srcmaps.map(_._2) ++ req.scalac ++ req.tests ++ req.markers).filterNot(Files.isRegularFile(_)).foreach { p =>
      System.err.println(s"[correlate] NOT FOUND: $p   (resolved against ${Path.of("").toAbsolutePath})")
    }

    val entries = req.srcmaps.flatMap((scope, p) => SrcMap.parseAll(p, scope))
    val idx     = SrcMap.Index.of(entries)
    if idx.isEmpty then
      System.err.println("[correlate] the source map is EMPTY — every diagnostic will be unattributable.")
      System.err.println("[correlate] re-run the migration so PortRun writes srcmap.tsv, then re-run this.")

    val markerSet = req.markers.filter(Files.isRegularFile(_)).map { p =>
      Files.readAllLines(p).toArray(Array.empty[String]).toList
        .filterNot(l => l.startsWith("#") || l.isBlank).map(_.trim).toSet
    }.getOrElse(Set.empty)

    // The member-digest delta spans EVERY source map supplied, not just the port `out` names.
    // A test failure is anchored on a LIBRARY member, and the library is a different port from the
    // suite: comparing only the test port's members would report "0 changed" for a change that
    // rewrote half the library — which is exactly the case the flag exists for. Latest digests come
    // from the maps just loaded; each map's baseline is its own port's `baseline/members.tsv`.
    val portDirs    = req.srcmaps.map(_._2).flatMap(p => Option(p.getParent).flatMap(x => Option(x.getParent)))
    val baseMembers = portDirs.flatMap(port => SrcMap.parseMembers(port.resolve("baseline/members.tsv"))).toMap ++
                      SrcMap.parseMembers(base.resolve("members.tsv"))
    val nowMembers  = entries.map(e => s"${e.unit}\t${e.member}" -> e.digest).toMap
    // With no member baseline, EVERY member differs from nothing — reporting that as "changed"
    // would decorate every finding with a flag that means nothing. No baseline ⇒ no claim.
    val changed     = if baseMembers.isEmpty then Set.empty[String] else Correlate.changedMembers(baseMembers, nowMembers)

    // The DROPPED TYPES of every port whose map is loaded. `PortRun` writes the file beside the
    // source map on every run, so the expected-failure set follows the manifest automatically; the
    // union is right because a test suite's failure lands in the LIBRARY's dropped type, which is a
    // different port from the suite (exactly the shape the digest union above exists for).
    val dropped = (req.srcmaps.map(_._2.getParent) ++ portDirs.map(_.resolve("run-latest")) :+ outDir)
      .distinct.flatMap(d => Correlate.parseDropped(d.resolve("dropped-types.tsv"))).toSet

    val sb = new StringBuilder
    sb.append(s"units in source map: ${idx.units.size}   members: ${entries.size}\n")
    if baseMembers.nonEmpty then
      sb.append(s"members whose EMITTED TEXT changed since the baseline: ${changed.size}\n")
      Files.writeString(outDir.resolve("members-changed.tsv"),
        ("#unit\tmember" :: changed.toList.sorted).mkString("", "\n", "\n"))
    else sb.append("no member baseline yet — accept one to get the blast-radius answer.\n")

    var located = List.empty[Correlate.LocatedError]
    req.scalac.filter(Files.isRegularFile(_)).foreach { p =>
      val errs = Correlate.parseScalac(Files.readString(p))
      located = Correlate.locateErrors(errs, idx, markerSet)
      Correlate.writeErrors(outDir, located)
      sb.append('\n').append(Correlate.renderErrors(located))
    }

    var regressed  = false
    var locatedTst = List.empty[Correlate.LocatedTest]
    req.tests.filter(Files.isRegularFile(_)).foreach { p =>
      val outs     = Correlate.parseTests(Files.readString(p))
      val declared = Correlate.parseExpected(base.resolve("expected-failures.tsv"))
      locatedTst   = Correlate.locateTests(outs, idx, declared, changed, dropped)
      Correlate.writeTests(outDir, locatedTst)
      val d = Correlate.diffTests(Correlate.parseTestsTsv(base.resolve("tests.tsv")), locatedTst)
      Files.writeString(outDir.resolve("tests-diff.txt"), Correlate.renderTests(locatedTst, d))
      sb.append('\n').append(Correlate.renderTests(locatedTst, d))
      regressed = d.regressed
    }

    val report = sb.result()
    Files.writeString(outDir.resolve("correlate.txt"), report)
    Result(report, regressed, located, locatedTst)

/** The correlation step as a COMMAND the measure scripts run AFTER the compiler and the test runner
  * have produced their output.
  *
  * {{{
  * core/runMain balticporter.tir.CorrelateMain \
  *   --srcmap      port-report/<MainPort>/run-latest/srcmap.tsv \
  *   --srcmap      test=port-report/<TestPort>/run-latest/srcmap.tsv \
  *   --scalac      /tmp/compile.txt \
  *   --tests       /tmp/test.txt \
  *   --out         port-report/<TestPort>/run-latest \
  *   --baseline    port-report/<TestPort>/baseline
  * }}}
  *
  * It stays a separate process on purpose even though [[CorrelateRun]] can now be called in-process:
  * a compiler and a test runner both run long after the migration JVM has exited, the join is over
  * FILES, and an agent debugging a wall needs to correlate a log it produced by hand. Paths are
  * explicit rather than derived because [[CheckReport.dir]]'s main-class derivation would name
  * *this* program, not the port.
  */
object CorrelateMain:

  private def usage(): Nothing =
    System.err.println(
      """usage: CorrelateMain [options]
        |  --srcmap [scope=]<file>   source map; repeatable. scope defaults to `main`, use
        |                            `test=` for the test source set so a library frame can be
        |                            preferred over a test frame when anchoring a failure.
        |  --scalac <file>           compiler output to triage
        |  --tests  <file>           test-runner output to triage
        |  --out    <dir>            where errors.tsv / tests.tsv / correlate.txt go
        |  --baseline <dir>          baseline dir (default <out>/../baseline)
        |  --markers <file>          `unit<TAB>member` regions marked approximate (Stage 2)
        |  --fail-on-regression      exit 1 when a test newly fails or an error is unclassified
        |""".stripMargin)
    sys.exit(2)

  def main(args: Array[String]): Unit =
    var srcmaps  = List.empty[(String, Path)]
    var scalac   = scala.Option.empty[Path]
    var tests    = scala.Option.empty[Path]
    var out      = scala.Option.empty[Path]
    var baseline = scala.Option.empty[Path]
    var markers  = scala.Option.empty[Path]
    var strict   = false
    var i        = 0
    while i < args.length do
      def next(): String = { i += 1; if i < args.length then args(i) else usage() }
      args(i) match
        case "--srcmap" =>
          val v = next()
          val (s, p) = v.split("=", 2) match
            case Array(sc, pp) if sc == "main" || sc == "test" => (sc, pp)
            case _                                            => ("main", v)
          srcmaps = srcmaps :+ (s -> Path.of(p))
        case "--scalac"             => scalac = Some(Path.of(next()))
        case "--tests"              => tests = Some(Path.of(next()))
        case "--out"                => out = Some(Path.of(next()))
        case "--baseline"           => baseline = Some(Path.of(next()))
        case "--markers"            => markers = Some(Path.of(next()))
        case "--fail-on-regression" => strict = true
        case "-h" | "--help"        => usage()
        case other                  => System.err.println(s"unknown option: $other"); usage()
      i += 1

    val result = CorrelateRun.run(CorrelateRun.Request(
      srcmaps = srcmaps, scalac = scalac, tests = tests,
      out = out.getOrElse(usage()), baseline = baseline, markers = markers))
    println(result.report)
    if strict && result.regressed then
      System.err.println("[correlate] a test newly FAILED — see NEWLY FAILING above")
      sys.exit(1)
