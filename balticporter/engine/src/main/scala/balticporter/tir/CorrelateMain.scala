package balticporter.tir

import java.nio.file.{Files, Path}

/** Correlation step as a request — runnable in-process ([[PortRun.correlate]]) or from CLI.
  * All paths are resolved against [[DebugFlags.root]] before use; a missing declared input is fatal. */
object CorrelateRun:

  /** @param srcmaps  `scope -> srcmap.tsv`; scope is `main` or `test`.
    * @param out      output directory for errors.tsv / tests.tsv / correlate.txt.
    * @param baseline baseline directory to diff against.
    * @param markers  `unit<TAB>member` approximate regions (Stage 2). */
  final case class Request(
      srcmaps: List[(String, Path)] = Nil,
      scalac: Option[Path] = scala.None,
      tests: Option[Path] = scala.None,
      out: Path,
      baseline: Option[Path] = scala.None,
      markers: Option[Path] = scala.None,
  ):
    /** Every path made absolute against [[DebugFlags.root]]. */
    def absolute: Request =
      def abs(p: Path): Path = if p.isAbsolute then p.normalize else DebugFlags.root.resolve(p).normalize
      copy(srcmaps = srcmaps.map((s, p) => s -> abs(p)), scalac = scalac.map(abs), tests = tests.map(abs),
           out = abs(out), baseline = baseline.map(abs), markers = markers.map(abs))

    def baselineDir: Path = baseline.getOrElse(out.getParent.resolve("baseline"))

  final case class Result(report: String, regressed: Boolean, errors: List[Correlate.LocatedError], tests: List[Correlate.LocatedTest])

  /** Fatal — a declared input path was not found. Names the path and its resolution root. */
  final class MissingInput(val paths: List[Path])
      extends RuntimeException(
        s"[correlate] ${paths.size} declared input(s) NOT FOUND — refusing to report on a file it " +
          s"never opened:\n" + paths.map(p => s"  $p").mkString("\n") +
          s"\n  (relative paths resolve against ${DebugFlags.root}; pass absolute ones, or set " +
          "balticporter.root)")

  /** Run the join and write the artifacts. Prints nothing — the caller decides. */
  def run(req0: Request): Result =
    val req    = req0.absolute
    val outDir = req.out
    val base   = req.baselineDir
    Files.createDirectories(outDir)

    val missing = (req.srcmaps.map(_._2) ++ req.scalac ++ req.tests ++ req.markers)
      .filterNot(Files.isRegularFile(_))
    if missing.nonEmpty then
      throw MissingInput(missing)

    val entries = req.srcmaps.flatMap((scope, p) => SrcMap.parseAll(p, scope))
    val idx     = SrcMap.Index.of(entries)
    if idx.isEmpty then
      System.err.println("[correlate] the source map is EMPTY — every diagnostic will be unattributable.")
      System.err.println("[correlate] re-run the migration so PortRun writes srcmap.tsv, then re-run this.")

    val markerSet = req.markers.filter(Files.isRegularFile(_)).map { p =>
      Files.readAllLines(p).toArray(Array.empty[String]).toList
        .filterNot(l => l.startsWith("#") || l.isBlank).map(_.trim).toSet
    }.getOrElse(Set.empty)

    // digest delta spans all supplied source maps (a test failure anchors on a library member)
    val portDirs    = req.srcmaps.map(_._2).flatMap(p => Option(p.getParent).flatMap(x => Option(x.getParent)))
    val baseMembers = portDirs.flatMap(port => SrcMap.parseMembers(port.resolve("baseline/members.tsv"))).toMap ++
                      SrcMap.parseMembers(base.resolve("members.tsv"))
    val nowMembers  = entries.map(e => s"${e.unit}\t${e.member}" -> e.digest).toMap
    // no baseline ⇒ no claim — every member would diff against nothing
    val changed     = if baseMembers.isEmpty then Set.empty[String] else Correlate.changedMembers(baseMembers, nowMembers)

    // union of dropped types from all loaded ports, in both namespaces
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

/** CLI entry point for [[CorrelateRun]] — the measure lanes run this after the compiler/test runner. */
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

    // missing input on stdout AND stderr — measure scripts filter stderr from the correlate block
    val result =
      try
        CorrelateRun.run(CorrelateRun.Request(
          srcmaps = srcmaps, scalac = scalac, tests = tests,
          out = out.getOrElse(usage()), baseline = baseline, markers = markers))
      catch
        case e: CorrelateRun.MissingInput =>
          println(e.getMessage)
          System.err.println(e.getMessage)
          sys.exit(2)
    println(result.report)
    if strict && result.regressed then
      System.err.println("[correlate] a test newly FAILED or was SKIPPED — see the report above")
      sys.exit(1)
