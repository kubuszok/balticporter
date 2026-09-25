package balticporter.transform

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{ DerivedPolicy, Pipeline, PolicyBinder, RunScope }
import balticporter.verify.{ ApiParityCheck, ReferencePolicy }

/** `VisibilityTransform.narrow`: a listed java `protected` member ships plain `protected`, its override component with it, unless a reference needs java's package access. */
class VisibilityNarrowSpec extends munit.FunSuite:
  private val base =
    """package com.demo.sys;
      |public abstract class Iterating {
      |  public void update () { processEntity(1); }
      |  protected abstract void processEntity (int e);
      |  protected void keep () { }
      |}
      |""".stripMargin
  private val sub =
    """package com.demo.sys;
      |public class Sorted extends Iterating {
      |  protected void processEntity (int e) { super.keep(); }
      |}
      |""".stripMargin
  private val other =
    """package com.demo.other;
      |public class Counting extends com.demo.sys.Iterating {
      |  protected void processEntity (int e) { this.keep(); }
      |}
      |""".stripMargin

  private def emit(sources: List[(String, String)], phase: VisibilityTransform): String =
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSources(sources), List(phase))
    new TirEmitter(after, notes = log).emit

  private def declLine(out: String, name: String): List[String] = out.linesIterator.filter(_.contains(s"def $name(")).map(_.trim).toList

  test("a listed member and every override in its component emit plain `protected`, across packages too") {
    val phase = new VisibilityTransform(narrow = Set("com.demo.sys.Iterating#processEntity"))
    val out   = emit(List("Iterating.java" -> base, "Sorted.java" -> sub, "Counting.java" -> other), phase)
    val decls = declLine(out, "processEntity")
    assertEquals(decls.size, 3, out)
    assert(
      decls.forall(l => l.startsWith("protected def") || l.startsWith("protected override def") || l.startsWith("override protected def")),
      decls.mkString("\n")
    )
    assert(clue(declLine(out, "keep")).forall(_.startsWith("protected[sys]")), out)
    assert(out.contains("porter: narrowed-visibility"), out)
    assert(phase.policyReport.findings.isEmpty, phase.policyReport.findings.mkString("\n"))
  }

  test("a same-package caller that is not a subclass refuses the narrowing, counted, and java's `protected[pkg]` stays") {
    val caller =
      """package com.demo.sys;
        |public class Driver {
        |  void drive (Iterating it) { it.processEntity(2); }
        |}
        |""".stripMargin
    val phase    = new VisibilityTransform(narrow = Set("com.demo.sys.Sorted#processEntity"))
    val out      = emit(List("Iterating.java" -> base, "Sorted.java" -> sub, "Driver.java" -> caller), phase)
    val findings = phase.policyReport.findings
    assertEquals(findings.size, 1, findings.mkString("\n"))
    assertEquals(findings.head.key, "com.demo.sys.Sorted#processEntity")
    assert(clue(findings.head.detail).contains("com.demo.sys.Driver"), findings.head.detail)
    assert(declLine(out, "processEntity").forall(_.contains("protected[sys]")), out)
  }

  test("a subclass calling through a receiver of the PARENT type refuses: scala's `protected` needs the accessing class") {
    val peer =
      """package com.demo.sys;
        |public class Peer extends Iterating {
        |  protected void processEntity (int e) { }
        |  void poke (Iterating other) { other.processEntity(3); }
        |}
        |""".stripMargin
    val phase = new VisibilityTransform(narrow = Set("com.demo.sys.Iterating#processEntity"))
    emit(List("Iterating.java" -> base, "Peer.java" -> peer), phase)
    assertEquals(phase.policyReport.findings.map(_.key), List("com.demo.sys.Iterating#processEntity"))
    assert(phase.policyReport.findings.head.detail.contains("receiver"), phase.policyReport.findings.head.detail)
  }

  test("an empty narrowing list emits byte-identical output") {
    val sources = List("Iterating.java" -> base, "Sorted.java" -> sub, "Counting.java" -> other)
    assertEquals(emit(sources, new VisibilityTransform(narrow = Set.empty)), emit(sources, new VisibilityTransform()))
  }

  test("the fingerprint carries no narrowing segment when the list is empty, and one when it is not") {
    assertEquals(new VisibilityTransform(narrow = Set.empty, deriveNarrow = false).surfaceFingerprint, "")
    assertEquals(new VisibilityTransform(Set.empty, false).surfaceFingerprint, "")
    assertEquals(new VisibilityTransform(narrow = Set("a.B#m")).surfaceFingerprint, "narrow=a.B#m")
    assert(new VisibilityTransform(deriveNarrow = true).surfaceFingerprint.contains("narrow-derive=reference"))
  }

  test("the reference's plain `protected` derives a Protected row, and the derive switch narrows by it") {
    val reference =
      """package com.demo.sys
        |abstract class Iterating {
        |  def update(): Unit = ()
        |  protected def processEntity(e: Int): Unit
        |  protected[sys] def keep(): Unit = ()
        |}
        |class Sorted extends Iterating {
        |  override protected def processEntity(e: Int): Unit = ()
        |}
        |""".stripMargin
    val dir = java.nio.file.Files.createTempDirectory("narrowderive")
    java.nio.file.Files.writeString(dir.resolve("Iterating.scala"), reference)
    val decls   = ApiParityCheck.parseSurface(List(dir)).toOption.get
    val program = SpoonTir.fromSources(List("Iterating.java" -> base, "Sorted.java" -> sub))
    val derived = ReferencePolicy.derive(program, decls, program.units.map(_.symbol).toSet, Map.empty, Set.empty, Set.empty)
    val rows    = derived.policy.rows.filter(_.family == DerivedPolicy.Family.Protected).map(_.upstream).toSet
    assert(clue(rows).contains("com.demo.sys.Iterating#processEntity"))
    assert(!rows.contains("com.demo.sys.Iterating#keep"), rows)
    val scope        = RunScope.of(program.units.map(_.symbol).toSet, Map.empty, derivedPolicy = derived.policy.resolved(program))
    val phase        = new VisibilityTransform(deriveNarrow = true)
    val (after, log) = Pipeline.runTraced(program, List(phase), new PolicyBinder(program, program.members, scope))
    val out          = new TirEmitter(after, notes = log).emit
    assert(declLine(out, "processEntity").forall(!_.contains("protected[")), out)
    assert(declLine(out, "keep").forall(_.startsWith("protected[sys]")), out)
  }
