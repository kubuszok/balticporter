package balticporter.transform

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Decision, Pipeline}

/** A DECLARED per-type package move publishes the type's package-private members, readers or no
  * readers in this program (a base never sees its dependent's, ENGINE-LIMITS.md K43). */
class PackageRenameBoundarySpec extends munit.FunSuite:
  private val java =
    """package com.demo;
      |class Table {
      |  static int tableSize (int capacity) { return capacity * 2; }
      |  int size;
      |  int slot (int i) { return i; }
      |  public int get () { return size; }
      |}
      |class Sub extends Table {
      |  int slot (int i) { return i + 1; }
      |}
      |class Other {
      |  int f () { return Table.tableSize(3); }
      |}
      |""".stripMargin

  private def run(allow: Set[String]) =
    val phase = new PackageRenameTransform(
      typeRenames = Map("com.demo.Table" -> "elsewhere.util.Table"), allowPackageSplit = allow)
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(java, "Demo.java"), List(phase))
    (phase, new TirEmitter(after, notes = log).emit, log)

  test("a declared move renders the moved type's package-private members public, one widening each") {
    val (phase, out, log) = run(Set("com.demo.Table"))
    assert(clue(out).contains("def tableSize(capacity: scala.Int)"))
    assert(!out.contains("private[util] def tableSize"), out.linesIterator.filter(_.contains("tableSize")).mkString("\n"))
    val declared = phase.recordedWidenings.filter(_.readerFqn == "(declared)").map(_.subjectFqn)
    // the package-private default constructor is a member too (JLS 8.8.9 gives it the class's access)
    // the package-private TYPE itself is published with its members: it left the package its readers share
    assertEquals(declared.sorted, List("com.demo.Table", "com.demo.Table#<init>", "com.demo.Table#size", "com.demo.Table#slot", "com.demo.Table#tableSize"))
    assert(out.linesIterator.exists(l => l.startsWith("class Table") || l.startsWith("final class Table")), out.linesIterator.filter(_.contains("class Table")).mkString("\n"))
    assert(log.of(Decision.Kind.WidenedVisibility).exists(_.subjectFqn == "com.demo.Table#tableSize"))
  }

  test("a subclass left behind overrides the widened member public — an override may not be narrower") {
    val (_, out, _) = run(Set("com.demo.Table"))
    val sub = out.linesIterator.dropWhile(!_.contains("class Sub")).mkString("\n")
    assert(sub.contains("override def slot(i: scala.Int)"), sub)
    assert(!sub.contains("private[demo] override def slot"), sub)
  }

  test("an undeclared move keeps java's package qualifier — nothing is widened silently") {
    val (phase, out, _) = run(Set.empty)
    assert(phase.recordedWidenings.isEmpty)
    assert(!out.contains("(declared)"))
  }
