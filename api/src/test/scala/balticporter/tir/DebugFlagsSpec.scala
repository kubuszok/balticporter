package balticporter.tir

import java.nio.file.{Files, Path}

/** The FLAG RESOLUTION of CLAUDE.md §4.6, proven rather than described.
  *
  * The rule — system property, then `debug.properties`, then `run.properties` — was stated in three
  * documents and asserted nowhere, and it is exactly the rule an agent blames when a flag "did not
  * work". Every test here writes real files and reads the answer back through the same accessors a
  * migration uses.
  */
class DebugFlagsSpec extends munit.FunSuite:

  private def tempRoot(): Path =
    val d = Files.createTempDirectory("bp-flags")
    Files.createDirectories(d.resolve(".balticporter"))
    d

  private def write(root: Path, name: String, lines: String*): Unit =
    Files.writeString(root.resolve(".balticporter").resolve(name), lines.mkString("", "\n", "\n"))

  /** point `DebugFlags.root` at `r` for the duration, and restore — the marker-file cache is keyed
    * on the root, so this is all that is needed to make the file layers readable in a test. */
  private def at[A](r: Path, props: (String, String)*)(body: => A): A =
    val keys  = (DebugFlags.Prefix + "root") +: props.map(_._1)
    val saved = keys.map(k => k -> Option(System.getProperty(k)))
    System.setProperty(DebugFlags.Prefix + "root", r.toString)
    props.foreach((k, v) => System.setProperty(k, v))
    try body
    finally saved.foreach {
      case (k, Some(v))    => System.setProperty(k, v)
      case (k, scala.None) => System.clearProperty(k)
    }

  private def resolved(rs: List[DebugFlags.Resolved], key: String): DebugFlags.Resolved =
    rs.find(_.key == key).getOrElse(fail(s"no resolution for $key in ${rs.map(_.key)}"))

  test("debug.properties BEATS run.properties — the hand-written layer wins, and says what it beat") {
    val r = tempRoot()
    write(r, "run.properties", "balticporter.skipPhases=collections")
    write(r, "debug.properties", "balticporter.skipPhases=*")
    at(r) {
      val one = resolved(DebugFlags.resolution(), "balticporter.skipPhases")
      assertEquals(one.value, "*")
      assertEquals(one.source, "debug.properties")
      assertEquals(one.shadowed, List("run.properties" -> "collections"))
      // …and the accessors agree, which is the property that matters: the diagnostic must not be a
      // second implementation of the precedence rule.
      assertEquals(DebugFlags.skipPhases, Set("*"))
      assert(DebugFlags.skips("anything"))
    }
  }

  test("a system property beats BOTH files — that is what lets a main class set a flag for itself") {
    val r = tempRoot()
    write(r, "run.properties", "balticporter.dumpOnly=p.FromRun")
    write(r, "debug.properties", "balticporter.dumpOnly=p.FromDebug")
    at(r, DebugFlags.Prefix + "dumpOnly" -> "p.FromProps") {
      val one = resolved(DebugFlags.resolution(), "balticporter.dumpOnly")
      assertEquals(one.value, "p.FromProps")
      assertEquals(one.source, "system properties")
      assertEquals(one.shadowed.map(_._2), List("p.FromRun", "p.FromDebug"))
      assertEquals(DebugFlags.dumpOnly, Some("p.FromProps"))
    }
  }

  test("the layers are listed in INCREASING precedence — the fold that resolves is the one printed") {
    val r = tempRoot()
    at(r) {
      assertEquals(DebugFlags.layers(r, Map.empty).map(_.name),
        List("run.properties", "debug.properties", "system properties"))
    }
  }

  test("a key with no `balticporter.` prefix is reported IGNORED, never merged") {
    val r = tempRoot()
    write(r, "debug.properties", "skipPhases=*", "balticporter.tracePhases=true")
    at(r) {
      val debug = DebugFlags.fileLayers(r).find(_.name == "debug.properties").get
      assertEquals(debug.ignored, Map("skipPhases" -> "*"))
      assertEquals(debug.props.keySet, Set("balticporter.tracePhases"))
      // the ignored entry does exactly nothing, which is the point of reporting it
      assertEquals(DebugFlags.skipPhases, Set.empty[String])
      assert(DebugFlags.tracePhases)
    }
  }

  test("`get` and `resolution` cannot disagree — every resolved key reads back through the accessor") {
    val r = tempRoot()
    write(r, "run.properties", "balticporter.reportPathRoot=/tmp/x", "balticporter.skipPhases=a")
    write(r, "debug.properties", "balticporter.skipPhases=b", "balticporter.traceNode=Typed")
    at(r) {
      DebugFlags.resolution().foreach(x => assertEquals(DebugFlags.get(x.key), Some(x.value), x.key))
    }
  }

  test("an absent marker file is not an error — every flag is empty and every consumer a no-op") {
    val r = tempRoot()
    at(r) {
      assertEquals(DebugFlags.resolution(r, Map.empty), Nil)
      assertEquals(DebugFlags.skipPhases, Set.empty[String])
      assertEquals(DebugFlags.banner, scala.None)
      assertEquals(DebugFlags.active, Nil)
    }
  }

  test("the banner names every diagnosis flag that is on — including tracePhases") {
    val r = tempRoot()
    write(r, "debug.properties",
      "balticporter.skipPhases=collections", "balticporter.tracePhases=true",
      "balticporter.dumpTirAfter=collections", "balticporter.dumpOnly=p.Foo",
      "balticporter.traceNode=Typed")
    at(r) {
      val b = DebugFlags.banner.getOrElse(fail("no banner"))
      List("skipPhases=collections", "dumpTirAfter=collections", "dumpOnly=p.Foo",
        "tracePhases=true", "traceNode=Typed").foreach(f => assert(b.contains(f), b))
    }
  }

  test("the cache is keyed on the ROOT — a second root is read, not answered from the first") {
    val a = tempRoot(); write(a, "debug.properties", "balticporter.dumpOnly=p.A")
    val b = tempRoot(); write(b, "debug.properties", "balticporter.dumpOnly=p.B")
    at(a) { assertEquals(DebugFlags.dumpOnly, Some("p.A")) }
    at(b) { assertEquals(DebugFlags.dumpOnly, Some("p.B")) }
  }
