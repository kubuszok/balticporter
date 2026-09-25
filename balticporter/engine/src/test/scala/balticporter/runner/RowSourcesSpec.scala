package balticporter.runner

import balticporter.catalog.Platform
import balticporter.core.*
import balticporter.sbtgen.SbtGen
import balticporter.tir.SrcMap

import java.nio.file.{ Files, Path }
import scala.jdk.CollectionConverters.*

/** `PortManifest.rowSources`: a row's own upstream file replaces a main type on that row only. */
class RowSourcesSpec extends munit.FunSuite:

  private def java(dir: Path, rel: String, src: String): Unit =
    val p = dir.resolve(rel)
    Files.createDirectories(p.getParent)
    Files.writeString(p, src)

  private val coreClock =
    """package com.demo;
      |public class Clock {
      |  private long base = 7;
      |  public long now() { return System.nanoTime() + base; }
      |  public String name() { return "core"; }
      |}""".stripMargin

  private val emuClock =
    """package com.demo;
      |public class Clock {
      |  public long now() { return 42L; }
      |  public String name() { return "emu"; }
      |}""".stripMargin

  /** `upstream/lib/src` is the main tree, `upstream/web/emu` the browser emulation beside it. */
  private def fixture(emu: String = emuClock): (Path, Path, Path) =
    val root = Files.createTempDirectory("rowsources")
    val src  = root.resolve("upstream/lib/src")
    val emuR = root.resolve("upstream/web/emu")
    java(src, "com/demo/Clock.java", coreClock)
    java(
      src,
      "com/demo/User.java",
      "package com.demo;\npublic class User {\n  public String tell(Clock c) { return c.name() + c.now(); }\n}"
    )
    java(emuR, "com/demo/Clock.java", emu)
    (root, src, emuR)

  private def run(root: Path, src: Path, rows: Map[String, List[RowSource]], targets: Set[Platform] = Platform.values.toSet): PortResult =
    PortRun(
      label = "demo",
      portRoot = root.resolve("port"),
      sourceSet = SourceSet.Main,
      frontend = FrontendConfig(src, List("com/demo/Clock.java", "com/demo/User.java"), Nil),
      phases = Nil,
      provenance = Some(Provenance("demo-upstream", "abc123", "Apache-2.0", "lib/src", sourceRoot = src.toString)),
      manifest = Some(PortManifest("demo", rowSources = rows, targets = targets))
    ).execute()

  private def files(dir: Path): List[String] =
    if !Files.exists(dir) then Nil
    else Files.walk(dir).iterator().asScala.filter(_.toString.endsWith(".scala")).map(p => dir.relativize(p).toString.replace('\\', '/')).toList.sorted

  private def row(root: Path, r: String): Path = SbtGen.managedDir(root.resolve("port"), r)

  test(
    "a row's file replaces the main type on that row only; the other rows get the main translation, and the shared tree neither"
  ) {
    val (root, src, emu) = fixture()
    run(root, src, Map("js" -> List(RowSource(emu))))
    assertEquals(files(row(root, "main")), List("com/demo/User.scala"))
    List("jvm", "js", "native").foreach(r => assertEquals(files(row(root, r)), List("com/demo/Clock.scala"), r))
    val js  = Files.readString(row(root, "js").resolve("com/demo/Clock.scala"))
    val jvm = Files.readString(row(root, "jvm").resolve("com/demo/Clock.scala"))
    assert(clue(js).contains("42L"))
    assert(!js.contains("nanoTime"))
    assert(clue(jvm).contains("nanoTime"))
    assertEquals(Files.readString(row(root, "native").resolve("com/demo/Clock.scala")), jvm)
  }

  test("each row's attribution header names the upstream file that row was translated from") {
    val (root, src, emu) = fixture()
    run(root, src, Map("js" -> List(RowSource(emu, List("com/demo/*.java")))))
    val js  = Files.readString(row(root, "js").resolve("com/demo/Clock.scala"))
    val jvm = Files.readString(row(root, "jvm").resolve("com/demo/Clock.scala"))
    // the emulation sits beside the main tree in one upstream checkout, so both are named from its top
    assert(clue(js).contains("Ported from: web/emu/com/demo/Clock.java"))
    assert(clue(jvm).contains("Ported from: lib/src/com/demo/Clock.java"))
  }

  test("the source map of a row's own translation is written per row and resolves a compiler path in that row's tree") {
    val (root, src, emu) = fixture()
    val rep              = root.resolve("report")
    PortRunSpecSupport.withReport(rep)(run(root, src, Map("js" -> List(RowSource(emu)))))
    val shared = SrcMap.parseAll(rep.resolve("run-latest/srcmap.tsv"))
    val rows   = SrcMap.parseRows(rep.resolve("run-latest/srcmap.tsv"), "main")
    // the shared map keeps its package-relative spelling; a row's map names the file as its header does
    assert(shared.exists(e => e.unit == "com.demo.Clock" && e.javaPath == "com/demo/Clock.java"), shared.map(_.javaPath))
    assert(rows.nonEmpty && rows.forall(e => e.row == "js" && e.javaPath == "web/emu/com/demo/Clock.java"), rows)
    val idx  = SrcMap.Index.of(shared ++ rows)
    val line = rows.find(_.member.contains("#now")).map(_.start).getOrElse(fail("no `now` entry on the row"))
    assertEquals(
      idx.resolveFile("port/src_managed/js/scala/com/demo/Clock.scala", line).map(_.javaPath),
      Some("web/emu/com/demo/Clock.java")
    )
    assertEquals(idx.resolveFile("port/src_managed/jvm/scala/com/demo/Clock.scala", line).map(_.javaPath), Some("com/demo/Clock.java"))
  }

  test("no row sources is the no-op: the same trees as a port that never heard of the key") {
    val (root, src, emu) = fixture()
    run(root, src, Map.empty)
    assertEquals(files(row(root, "main")), List("com/demo/Clock.scala", "com/demo/User.scala"))
    List("jvm", "js", "native").foreach(r => assert(!Files.exists(row(root, r)), r))
    // a row declared with nothing in it replaces nothing either
    run(root, src, Map("js" -> Nil))
    assertEquals(files(row(root, "main")), List("com/demo/Clock.scala", "com/demo/User.scala"))
    assert(Files.exists(emu))
  }

  private def refused(root: Path, src: Path, rows: Map[String, List[RowSource]], targets: Set[Platform] = Platform.values.toSet): String =
    intercept[RuntimeException](run(root, src, rows, targets)).getMessage

  test("a row name the port does not build is refused") {
    val (root, src, emu) = fixture()
    assert(clue(refused(root, src, Map("desktop" -> List(RowSource(emu))))).contains("undeclared-row"))
    // `js` is a row, but not one a JVM-only port builds
    assert(clue(refused(root, src, Map("js" -> List(RowSource(emu))), Set(Platform.Jvm))).contains("undeclared-row"))
  }

  test("a row file declaring a type the main set does not have is refused, by path and by type") {
    val (root, src, emu) = fixture()
    java(emu, "com/demo/Extra.java", "package com.demo;\npublic class Extra {}")
    assert(clue(refused(root, src, Map("js" -> List(RowSource(emu))))).contains("no-main-type"))
    // same path, but the file declares a second top-level type the main file does not
    val (root2, src2, emu2) = fixture(emuClock + "\nclass Helper {}")
    val msg                 = refused(root2, src2, Map("js" -> List(RowSource(emu2))))
    assert(clue(msg).contains("no-main-type") && msg.contains("com.demo.Helper"))
  }

  test("a declared entry matching no file is refused") {
    val (root, src, emu) = fixture()
    assert(clue(refused(root, src, Map("js" -> List(RowSource(emu, List("com/demo/Nope.java")))))).contains("missing"))
  }

  test(
    "two surfaces whose reachable signatures differ are refused, naming each differing member, and no tree is left behind"
  ) {
    val (root, src, emu) = fixture(
      """package com.demo;
        |public class Clock {
        |  public int now() { return 42; }
        |  public String name() { return "emu"; }
        |  public void extra() {}
        |}""".stripMargin
    )
    val msg = refused(root, src, Map("js" -> List(RowSource(emu))))
    assert(clue(msg).contains("com.demo.Clock#now") && msg.contains("scala.Long") && msg.contains("scala.Int"))
    assert(msg.contains("com.demo.Clock#extra — main: absent"))
    assert(!msg.contains("Clock#name"), "an identical member is not a difference")
    assert(!msg.contains("base"), "a private member is not surface")
    List("main", "jvm", "js", "native").foreach(r => assertEquals(files(row(root, r)), Nil, r))
  }

  test("RowSurface ignores parameter names, bodies and private members, and keeps visibility") {
    val a = RowSurface
      .of(
        "package p\nclass A(val x: Int) {\n  def f(a: Int): Int = a\n  private def g(): Unit = ()\n  private[p] def h(): Unit = ()\n}\n",
        "a"
      )
      .toOption
      .get
    val b = RowSurface
      .of(
        "package p\nclass A(val x: Int) {\n  def f(other: Int): Int = other + 1\n  private[p] def h(): Unit = println()\n}\n",
        "b"
      )
      .toOption
      .get
    assertEquals(RowSurface.diff(a, b), Nil)
    val c = RowSurface.of("package p\nclass A(val x: Int) {\n  def f(a: Int): Int = a\n  protected def h(): Unit = ()\n}\n", "c").toOption.get
    assertEquals(RowSurface.diff(a, c).map(_.member), List("p.A#h"))
  }

/** the artifact layer, on for one run into a directory of the test's own. */
object PortRunSpecSupport:
  def withReport[A](dir: Path)(f: => A): A =
    val keys  = List("balticporter.report" -> "on", "balticporter.reportDir" -> dir.toString)
    val saved = keys.map((k, _) => k -> Option(System.getProperty(k)))
    keys.foreach((k, v) => System.setProperty(k, v))
    try f
    finally
      saved.foreach {
        case (k, Some(v))    => System.setProperty(k, v)
        case (k, scala.None) => System.clearProperty(k)
      }
