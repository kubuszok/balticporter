package balticporter.tir

import java.nio.file.{Files, Path}

/** The correlation step, as a command the measure scripts run AFTER the compiler and the test
  * runner have produced their output.
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
  * It is a separate process on purpose: the compiler and the test runner both run long after the
  * migration JVM has exited, and the join is over FILES, so nothing has to be kept alive. Paths are
  * explicit rather than derived because [[CheckReport.dir]]'s main-class derivation would name
  * *this* program, not the port.
  *
  * `--markers` is the Stage-2 seam: a two-column `unit<TAB>member` file of the regions the engine
  * marked approximate. Absent (the state today) every scalac error lands in the engine-gap lane,
  * which is the honest answer while the engine marks nothing.
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

    val outDir = out.getOrElse(usage())
    val base   = baseline.getOrElse(outDir.getParent.resolve("baseline"))
    Files.createDirectories(outDir)

    // A missing input file must SAY SO. This program is normally launched through sbt, whose
    // working directory is the subproject's, not the build root — so a relative path that reads
    // correctly in a shell silently resolves to nothing here, and the whole correlation then
    // reports "0 units" as if the port had no members. Name the file and the directory it was
    // looked for in; the measure scripts pass absolute paths for the same reason.
    (srcmaps.map(_._2) ++ scalac ++ tests ++ markers).filterNot(Files.isRegularFile(_)).foreach { p =>
      System.err.println(s"[correlate] NOT FOUND: $p   (resolved against ${Path.of("").toAbsolutePath})")
    }

    val entries = srcmaps.flatMap((scope, p) => SrcMap.parseAll(p, scope))
    val idx     = SrcMap.Index.of(entries)
    if idx.isEmpty then
      System.err.println("[correlate] the source map is EMPTY — every diagnostic will be unattributable.")
      System.err.println("[correlate] re-run the migration so TirEmitter writes srcmap.tsv, then re-run this.")

    val markerSet = markers.filter(Files.isRegularFile(_)).map { p =>
      Files.readAllLines(p).toArray(Array.empty[String]).toList
        .filterNot(l => l.startsWith("#") || l.isBlank).map(_.trim).toSet
    }.getOrElse(Set.empty)

    // The member-digest delta spans EVERY source map supplied, not just the port `--out` names.
    // A test failure is anchored on a LIBRARY member, and the library is a different port from the
    // suite: comparing only the test port's members would report "0 changed" for a change that
    // rewrote half the library — which is exactly the case the flag exists for. Latest digests come
    // from the maps just loaded; each map's baseline is its own port's `baseline/members.tsv`.
    val baseMembers = srcmaps.map(_._2)
      .flatMap(p => Option(p.getParent).flatMap(x => Option(x.getParent)).toList)
      .flatMap(port => SrcMap.parseMembers(port.resolve("baseline/members.tsv")))
      .toMap ++ SrcMap.parseMembers(base.resolve("members.tsv"))
    val nowMembers  = entries.map(e => s"${e.unit}\t${e.member}" -> e.digest).toMap
    // With no member baseline, EVERY member differs from nothing — reporting that as "changed"
    // would decorate every finding with a flag that means nothing. No baseline ⇒ no claim.
    val changed     = if baseMembers.isEmpty then Set.empty[String] else Correlate.changedMembers(baseMembers, nowMembers)

    val sb = new StringBuilder
    sb.append(s"units in source map: ${idx.units.size}   members: ${entries.size}\n")
    if baseMembers.nonEmpty then
      sb.append(s"members whose EMITTED TEXT changed since the baseline: ${changed.size}\n")
      Files.writeString(outDir.resolve("members-changed.tsv"),
        ("#unit\tmember" :: changed.toList.sorted).mkString("", "\n", "\n"))
    else sb.append("no member baseline yet — accept one to get the blast-radius answer.\n")

    scalac.filter(Files.isRegularFile(_)).foreach { p =>
      val errs = Correlate.parseScalac(Files.readString(p))
      val ls   = Correlate.locateErrors(errs, idx, markerSet)
      Correlate.writeErrors(outDir, ls)
      sb.append('\n').append(Correlate.renderErrors(ls))
    }

    var regressed = false
    tests.filter(Files.isRegularFile(_)).foreach { p =>
      val outs     = Correlate.parseTests(Files.readString(p))
      val expected = Correlate.parseExpected(base.resolve("expected-failures.tsv"))
      val located  = Correlate.locateTests(outs, idx, expected, changed)
      Correlate.writeTests(outDir, located)
      val d = Correlate.diffTests(Correlate.parseTestsTsv(base.resolve("tests.tsv")), located)
      Files.writeString(outDir.resolve("tests-diff.txt"), Correlate.renderTests(located, d))
      sb.append('\n').append(Correlate.renderTests(located, d))
      regressed = d.regressed
    }

    val report = sb.result()
    Files.writeString(outDir.resolve("correlate.txt"), report)
    println(report)
    if strict && regressed then
      System.err.println("[correlate] a test newly FAILED — see NEWLY FAILING above")
      sys.exit(1)
