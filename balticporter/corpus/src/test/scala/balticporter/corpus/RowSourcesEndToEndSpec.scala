package balticporter.corpus

import balticporter.core.*
import balticporter.runner.{ PortRun, SourceSet }
import balticporter.sbtgen.SbtGen

import java.nio.file.{ Files, Path }
import scala.jdk.CollectionConverters.*

/** `rowSources` end to end: each row's tree is COMPILED together with the shared tree by the Scala compiler, and the compiled code RUNS with that row's behaviour. The rows declare one FQN each, so
  * they cannot share one build — each is compiled on its own here.
  */
class RowSourcesEndToEndSpec extends munit.FunSuite:

  private def java(dir: Path, rel: String, src: String): Unit =
    val p = dir.resolve(rel)
    Files.createDirectories(p.getParent)
    Files.writeString(p, src)

  private lazy val port: Path =
    val root = Files.createTempDirectory("rowsources-e2e")
    val src  = root.resolve("upstream/lib/src")
    val emu  = root.resolve("upstream/web/emu")
    java(
      src,
      "com/demo/Clock.java",
      "package com.demo;\npublic class Clock {\n  private final String where;\n  public Clock() { this.where = System.getProperty(\"java.vendor\") == null ? \"?\" : \"jvm\"; }\n  public String name() { return \"core-\" + where; }\n}"
    )
    java(
      emu,
      "com/demo/Clock.java",
      "package com.demo;\npublic class Clock {\n  public Clock() {}\n  public String name() { return \"emu\"; }\n}"
    )
    java(
      src,
      "com/demo/User.java",
      "package com.demo;\npublic class User {\n  public static String tell() { return \"clock=\" + new Clock().name(); }\n}"
    )
    PortRun(
      label = "e2e",
      portRoot = root.resolve("port"),
      sourceSet = SourceSet.Main,
      frontend = FrontendConfig(src, List("com/demo/Clock.java", "com/demo/User.java"), Nil),
      phases = Nil,
      manifest = Some(PortManifest("e2e", rowSources = Map("js" -> List(RowSource(emu)))))
    ).execute()
    root.resolve("port")

  private def scalaFiles(dir: Path): List[String] =
    Files.walk(dir).iterator().asScala.filter(_.toString.endsWith(".scala")).map(_.toString).toList.sorted

  /** compile the shared tree with one row's tree, then call the shared code the row's type answers. */
  private def compileAndRun(row: String): String =
    val out     = Files.createTempDirectory(s"rowsources-$row")
    val sources = scalaFiles(SbtGen.managedMain(port)) ++ scalaFiles(SbtGen.managedDir(port, row))
    // the test JVM's class path is the build tool's worker, so name the standard library's jars outright
    val stdlib   = List(classOf[scala.Option[?]], classOf[scala.deriving.Mirror]).map(c => Path.of(c.getProtectionDomain.getCodeSource.getLocation.toURI).toString).distinct
    val reporter = dotty.tools.dotc.Main.process(("-classpath" :: stdlib.mkString(_root_.java.io.File.pathSeparator) :: "-d" :: out.toString :: sources).toArray)
    assert(
      !reporter.hasErrors,
      s"row `$row` with the shared tree does not compile: ${reporter.allErrors.map(_.msg.message).mkString("\n")}"
    )
    val loader = new _root_.java.net.URLClassLoader(Array(out.toUri.toURL), getClass.getClassLoader)
    loader.loadClass("com.demo.User").getMethod("tell").invoke(null).asInstanceOf[String]

  test("the JVM row compiles with the shared tree and answers with the core translation") {
    assertEquals(compileAndRun("jvm"), "clock=core-jvm")
  }

  test("the JS row compiles with the SAME shared tree and answers with its own upstream file's translation") {
    assertEquals(compileAndRun("js"), "clock=emu")
  }

  test("the Native row gets the core translation, byte for byte the JVM row's") {
    assertEquals(
      Files.readString(SbtGen.managedDir(port, "native").resolve("com/demo/Clock.scala")),
      Files.readString(SbtGen.managedDir(port, "jvm").resolve("com/demo/Clock.scala"))
    )
  }
