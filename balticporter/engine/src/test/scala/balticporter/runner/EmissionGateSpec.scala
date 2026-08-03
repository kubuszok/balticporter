package balticporter.runner

import balticporter.core.*
import balticporter.tir.*

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** THE EMISSION GATE (`DESIGN.md` §6.4) — a port with an OPEN marker does not get written.
  *
  * §3.4's anti-omission stance applied to the one construct class the engine admits it cannot
  * translate: what is forbidden is not best effort, it is SILENT best effort. So a run with an open
  * marker has exactly two outcomes and both of them are loud —
  *
  *   - the DELIVERABLE run refuses, writes nothing, and names every marker with the §1
  *     classification of its first remedy;
  *   - the BEST-EFFORT run writes, to a SEPARATE directory, with a sentinel in it and a nonzero
  *     exit. Borrowed from dotty (§6.1), which writes degraded artifacts where they cannot
  *     masquerade as real ones.
  *
  * What is not on offer either way is a tree on disk that looks shippable.
  */
class EmissionGateSpec extends munit.FunSuite:

  private def java(dir: Path, rel: String, src: String): Unit =
    val p = dir.resolve(rel)
    Files.createDirectories(p.getParent)
    Files.writeString(p, src)

  private def fixture(): (Path, Path) =
    val root = Files.createTempDirectory("emission-gate")
    val src  = root.resolve("java")
    java(src, "com/demo/Plain.java",
      """package com.demo;
        |public class Plain { public int twice(int a) { return a + a; } }""".stripMargin)
    (root, src)

  /** mints an OPEN marker at `twice`'s body — standing in for a frontend refusal point, which is
    * what §6.5 adopts first and which no fixture Java can reach on demand. */
  private class Mint extends Phase:
    def name: String = "test/mint"
    override def transformDefDef(d: Tree.DefDef)(using p: Program): Tree.DefDef =
      if !p.symbolOf(d.symbol).exists(_.name == "twice") then d
      else d.copy(rhs = d.rhs.map(r =>
        Tree.Unportable.open(r, UnportableKind.ConstructorTopology, scala.None,
          "the fixture's stand-in for a construct with no faithful Scala", r.tpe, r.origin)))

  private def run(root: Path, src: Path, phases: List[Phase], bestEffort: Boolean = false): PortRun =
    PortRun(
      label      = "demo",
      portRoot   = root.resolve("port"),
      sourceSet  = SourceSet.Main,
      frontend   = FrontendConfig(src, List("com/demo/Plain.java"), Nil),
      phases     = phases,
      bestEffort = bestEffort,
    )

  private def scalaFiles(dir: Path): List[String] =
    if !Files.exists(dir) then Nil
    else Files.walk(dir).iterator().asScala.filter(_.toString.endsWith(".scala"))
      .map(p => dir.relativize(p).toString.replace('\\', '/')).toList.sorted

  test("with NO markers the run is exactly what it always was — the gate is a no-op") {
    val (root, src) = fixture()
    val r = run(root, src, Nil).execute()
    assertEquals(scalaFiles(r.outDir), List("com/demo/Plain.scala"))
  }

  test("an OPEN marker REFUSES the deliverable emission, and nothing is written") {
    val (root, src) = fixture()
    val port = run(root, src, List(new Mint))
    val e    = intercept[RuntimeException](port.execute())
    assert(e.getMessage.contains("EMISSION REFUSED"), e.getMessage)
    assert(e.getMessage.contains("1 open unportability marker(s)"), e.getMessage)
    // the message carries the §1 classification of the fix, because an error an agent cannot
    // classify costs it a full investigation (§4.45).
    assert(e.getMessage.contains("§1(a) ENGINE:"), e.getMessage)
    assert(e.getMessage.contains("constructor-topology"), e.getMessage)
    // NOTHING on disk. Not a partial tree, not an older one — the gate runs before the wipe.
    assertEquals(scalaFiles(port.outDir), Nil)
  }

  test("BEST EFFORT writes instead — to its OWN directory, with a sentinel, and still ends nonzero") {
    val (root, src) = fixture()
    val port = run(root, src, List(new Mint), bestEffort = true)
    // the run still FAILS: degraded output is a diagnostic, never a delivery. What changes is that
    // there is something to read.
    val e = intercept[RuntimeException](port.execute())
    assert(e.getMessage.contains("BEST-EFFORT"), e.getMessage)

    assertEquals(scalaFiles(port.outDir), Nil, "the deliverable tree must stay empty")
    assertEquals(scalaFiles(port.bestEffortDir), List("com/demo/Plain.scala"))
    assert(Files.isRegularFile(port.bestEffortDir.resolve("BALTICPORTER-BEST-EFFORT")),
      "a directory name is not enough — a directory gets copied")

    val text = Files.readString(port.bestEffortDir.resolve("com/demo/Plain.scala"))
    assert(text.contains("BEST-EFFORT OUTPUT"), text)
    assert(text.contains("/* balticporter:unportable constructor-topology"), text)
    assert(text.contains("a + a"), "the approximation must be readable — that is the whole mode")
  }

  test("a DISCHARGED marker passes the gate — the discharge is what the state is for") {
    val (root, src) = fixture()
    class Discharge extends Phase:
      def name: String = "test/discharge"
      override def transformTerm(t: Term)(using Program): Term = t match
        case m: Tree.Unportable => m.resolved(name, "the fixture answered it")
        case other              => other
    val r = run(root, src, List(new Mint, new Discharge)).execute()
    assertEquals(scalaFiles(r.outDir), List("com/demo/Plain.scala"))
    val text = Files.readString(r.outDir.resolve("com/demo/Plain.scala"))
    assert(text.contains("a + a"))
    assert(!text.contains("balticporter:unportable"))
  }
