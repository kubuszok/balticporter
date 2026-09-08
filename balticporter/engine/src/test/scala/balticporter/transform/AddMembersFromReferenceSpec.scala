package balticporter.transform

import java.nio.file.Files
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Pipeline, PolicyBinder, RunScope}
import balticporter.verify.ReferenceSources

/** `AddMembersTransform.fromReference`: a hand port's own member is spliced VERBATIM from the
  * reference tree by name — class or companion as the reference has it, the imports it mentions
  * ahead of it (DESIGN.md §8.30). */
class AddMembersFromReferenceSpec extends munit.FunSuite:
  private val javaSrc =
    """package com.demo;
      |public class Poly {
      |  public int count () { return 3; }
      |}
      |""".stripMargin

  private val reference =
    """package com.demo
      |
      |import scala.collection.mutable.ArrayBuffer
      |import lowlevel.Nullable
      |
      |class Poly {
      |  def count: Int = 3
      |  /** the hand port's own member */
      |  def vertex(i: Int): Int = i * 2
      |  def buffered: ArrayBuffer[Int] = ArrayBuffer(count)
      |}
      |
      |object Poly {
      |  def unit: Poly = new Poly
      |}
      |
      |trait Shape { def area: Float }
      |""".stripMargin

  private def withReference[A](body: java.nio.file.Path => A): A =
    val dir = Files.createTempDirectory("bp-ref")
    Files.writeString(dir.resolve("Poly.scala"), reference)
    body(dir)

  private def emitted(phase: AddMembersTransform, dir: java.nio.file.Path): (String, AddMembersTransform) =
    val program = SpoonTir.fromSource(javaSrc, "Poly.java")
    val lookup  = new ReferenceSources(List(dir), Map.empty, Set.empty)
    assert(clue(lookup.index.keys.toList.sorted).contains("/Poly/vertex"), clue(lookup.unparseable.toList))
    // an abstract member (a declaration) is indexed too
    assert(lookup.index.contains("/Shape/area"))
    val scope   = RunScope.of(program.units.map(_.symbol).toSet, Map.empty, referenceSource = Some(lookup))
    val (after, log) = Pipeline.runTraced(program, List(phase), new PolicyBinder(program, program.members, scope))
    (new TirEmitter(after, notes = log).emit, phase)

  test("a listed name is spliced where the reference declares it, with the imports it mentions") {
    withReference { dir =>
      val (out, phase) = emitted(new AddMembersTransform(fromReference =
        Map("com.demo.Poly" -> List("vertex", "buffered", "unit"))), dir)
      assert(clue(out).contains("def vertex(i: Int): Int = i * 2"))
      // `buffered` mentions `ArrayBuffer`, so that import rides ahead of it; `Nullable` is not mentioned
      assert(out.contains("import scala.collection.mutable.ArrayBuffer"))
      assert(!out.contains("import lowlevel.Nullable"), out)
      // the companion's member lands in the companion
      val companion = out.linesIterator.dropWhile(!_.contains("object Poly")).mkString("\n")
      assert(clue(companion).contains("def unit: Poly = new Poly"))
      assert(phase.policyReport.findings.isEmpty, phase.policyReport.findings.mkString("\n"))
    }
  }

  test("a name the reference does not declare is a counted finding; so is a run without a reference") {
    withReference { dir =>
      val (_, phase) = emitted(new AddMembersTransform(fromReference = Map("com.demo.Poly" -> List("nothing"))), dir)
      assert(phase.policyReport.findings.exists(_.detail.contains("declares no `nothing`")),
        phase.policyReport.findings.mkString("\n"))
    }
    val program = SpoonTir.fromSource(javaSrc, "Poly.java")
    val phase   = new AddMembersTransform(fromReference = Map("com.demo.Poly" -> List("vertex")))
    Pipeline.runTraced(program, List(phase))
    assert(phase.policyReport.findings.exists(_.detail.contains("no reference port")), phase.policyReport.findings.mkString("\n"))
  }

  test("the fingerprint carries the names and the merge refuses a name listed twice") {
    val a = new AddMembersTransform(fromReference = Map("com.demo.Poly" -> List("vertex")))
    val b = new AddMembersTransform(fromReference = Map("com.demo.Poly" -> List("vertex")))
    assert(a.surfaceFingerprint.contains("vertex@ref"))
    assert(a.mergedWith(b).isLeft)
    val c = new AddMembersTransform(fromReference = Map("com.demo.Other" -> List("x")))
    assert(a.mergedWith(c).isRight)
  }
