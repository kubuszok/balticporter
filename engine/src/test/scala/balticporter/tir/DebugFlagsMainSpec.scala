package balticporter.tir

import java.nio.file.{Files, Path}

/** `just debug-flags`, which is this class's [[DebugFlagsMain.render]] and nothing else.
  *
  * The recipe answers one question — "why did my flag not reach the run" — and every way of
  * answering it wrongly is silent: naming the wrong layer, hiding the layer that was shadowed,
  * saying nothing about an entry no accessor will ever read. So the assertions are on the rendered
  * text, which is what an operator acts on.
  */
class DebugFlagsMainSpec extends munit.FunSuite:

  private def root(files: (String, String)*): Path =
    val r = Files.createTempDirectory("bp-flags-main")
    Files.createDirectories(r.resolve(".balticporter"))
    files.foreach((n, body) => Files.writeString(r.resolve(".balticporter").resolve(n), body + "\n"))
    r

  test("every layer is listed, in increasing precedence, with ABSENT said out loud") {
    val out = DebugFlagsMain.render(root("run.properties" -> "balticporter.skipPhases=a"), Map.empty, scala.None)
    val ls  = out.linesIterator.dropWhile(!_.startsWith("LAYERS")).drop(1).take(3).toList
    assert(ls(0).contains("run.properties") && ls(0).contains("1 flag(s)"), ls.mkString("\n"))
    assert(ls(1).contains("debug.properties") && ls(1).contains("ABSENT"), ls.mkString("\n"))
    assert(ls(2).contains("system properties"), ls.mkString("\n"))
  }

  test("the effective value names its source AND what it shadowed") {
    val r = root(
      "run.properties"   -> "balticporter.skipPhases=collections",
      "debug.properties" -> "balticporter.skipPhases=*",
    )
    val out = DebugFlagsMain.render(r, Map.empty, scala.None)
    val line = out.linesIterator.find(_.contains("balticporter.skipPhases =")).getOrElse(fail(out))
    assert(line.contains("= *"), line)
    assert(line.contains("[debug.properties]"), line)
    assert(line.contains("shadows run.properties=collections"), line)
  }

  test("nothing set says so — an operator must be able to tell 'no flags' from 'not shown'") {
    val out = DebugFlagsMain.render(root(), Map.empty, scala.None)
    assert(out.contains("(none — every flag is unset"), out)
  }

  test("a key with no `balticporter.` prefix is called out as IGNORED") {
    val out = DebugFlagsMain.render(root("debug.properties" -> "skipPhases=*"), Map.empty, scala.None)
    assert(out.contains("!! IGNORED"), out)
    assert(out.contains("debug.properties: skipPhases=*"), out)
  }

  test("a MISSPELT flag is marked unknown — nothing else in the engine can see one") {
    val out = DebugFlagsMain.render(root("debug.properties" -> "balticporter.skipPhase=collections"), Map.empty, scala.None)
    assert(out.contains("!! UNKNOWN KEY"), out)
    // …and a correctly spelt one is not
    val ok = DebugFlagsMain.render(root("debug.properties" -> "balticporter.skipPhases=collections"), Map.empty, scala.None)
    assert(!ok.contains("UNKNOWN KEY"), ok)
  }

  test("a flag a PORT is supposed to supply is marked as the FALLBACK it is") {
    // The one thing an operator cannot see otherwise: `baseReports` changes what a run EMITS (it
    // decides which base contracts are found), so a leftover entry makes this checkout emit
    // differently at the same commit with every count identical — §4.6's `reportPathRoot` lesson.
    val out = DebugFlagsMain.render(root("debug.properties" -> "balticporter.baseReports=/tmp/x"),
                                    Map.empty, scala.None)
    assert(out.contains("(FALLBACK"), out)
    assert(out.contains("a port states this in its own configuration"), out)
    // …and an ordinary diagnostic flag is not marked, or the marking says nothing
    val plain = DebugFlagsMain.render(root("debug.properties" -> "balticporter.skipPhases=*"),
                                      Map.empty, scala.None)
    assert(!plain.contains("(FALLBACK"), plain)
  }

  test("the forked-JVM caveat is always printed — it is the answer as often as the table is") {
    val out = DebugFlagsMain.render(root(), Map.empty, scala.None)
    assert(out.contains("FORKED from the sbt server"), out)
  }

  test("--port reads back what that run RECORDED, and says so when there is no run") {
    val r  = root()
    val md = r.resolve("port-report/P/run-latest")
    Files.createDirectories(md)
    Files.writeString(md.resolve("report.md"), "# Port check report\n\ndebug flags: skipPhases=*\n")
    val out = DebugFlagsMain.render(r, Map.empty, Some("P"))
    assert(out.contains("AS RECORDED BY THE LAST RUN of P"), out)
    assert(out.contains("debug flags: skipPhases=*"), out)

    val missing = DebugFlagsMain.render(r, Map.empty, Some("Q"))
    assert(missing.contains("!! no run for 'Q'"), missing)
    assert(missing.contains("ports with a report directory: P"), missing)
  }

  test("a system property is shown as beating both files — the layer this JVM speaks for") {
    val r   = root("debug.properties" -> "balticporter.dumpOnly=p.File")
    val out = DebugFlagsMain.render(r, Map("balticporter.dumpOnly" -> "p.Prop"), scala.None)
    val line = out.linesIterator.find(_.contains("balticporter.dumpOnly =")).getOrElse(fail(out))
    assert(line.contains("= p.Prop"), line)
    assert(line.contains("[system properties]"), line)
    assert(line.contains("shadows debug.properties=p.File"), line)
  }
