package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.*
import balticporter.transform.TypeRedirectTransform

/** `type-redirect` + `memberRenames` END TO END — java in, emitted Scala out, through the pipeline a
  * port runs.
  *
  * The unit spec (`TypeRedirectMemberRenameSpec`) asserts the mechanism. This asserts the ARTEFACT,
  * and specifically the property no unit assertion can reach: CROSS-FILE COHERENCE. Four files come
  * out of this fixture, and the thing that goes wrong when a rename reaches some declarations and
  * not others is that each file is perfectly valid on its own — the interface declares `close`, the
  * implementor declares `dispose`, and only a COMPILER over all four says so.
  *
  * So the suite writes a PROBE an operator (and CI) can put a real compiler over, which is the shape
  * `BeanPropertyPortSpec` and `LabeledJumpSpec` use and for the same reason: a forked test JVM
  * cannot be handed a compiler.
  *
  * {{{ scala-cli compile --scala 3.8.4 --server=false <the path printed below> }}}
  */
class TypeRedirectRenamePortSpec extends PortSuite:

  /** The shape a `Disposable`-style redirect meets: an interface, two implementors, one of them
    * subclassed, a caller that uses the interface as a TYPE, and a caller that calls the member
    * through each of the three declarations — the case where a call through an implementor's symbol
    * is a different `SymId` from a call through the interface's. */
  private val java =
    """package com.demo;
      |
      |/** Something that holds a native resource. */
      |public interface Disposable {
      |  /** frees whatever it holds. */
      |  void dispose();
      |}
      |
      |public class Buffer implements Disposable {
      |  private boolean freed = false;
      |  public void dispose() { freed = true; }
      |  public boolean isFreed() { return freed; }
      |}
      |
      |public class Pooled implements Disposable {
      |  public void dispose() {}
      |}
      |
      |public class Sub extends Buffer {
      |  public void dispose() { super.dispose(); }
      |}
      |
      |public class Client {
      |  Disposable held;
      |
      |  void keep(Disposable d) { this.held = d; }
      |
      |  void freeAll(Buffer b, Pooled p, Sub s, Disposable any) {
      |    b.dispose();
      |    p.dispose();
      |    s.dispose();
      |    any.dispose();
      |    this.held.dispose();
      |  }
      |}
      |""".stripMargin

  private def ported(renames: Map[String, String] = Map("dispose" -> "close")) =
    val phase  = new TypeRedirectTransform(
      redirects     = Map("com.demo.Disposable" -> "java.lang.AutoCloseable"),
      memberRenames = Map("com.demo.Disposable" -> renames))
    val before       = SpoonTir.fromSource(java, "Demo.java")
    val (after, log) = Pipeline.runTraced(before, List(phase))
    (phase, after, log, new TirEmitter(after, notes = log).emit)

  /** the emitted CODE with the porter notes stripped — a note names the UPSTREAM member on purpose
    * (§4.575's `from=`), so a text search for an upstream name has to strip them first. */
  private def code(out: String): String =
    out.linesIterator.filterNot(l => l.contains(PorterNote.Marker) || l.trim.startsWith("—")).mkString("\n")

  test("the emitted SURFACE is the TARGET's, in every file — the interface, both implementors, the subclass") {
    val (phase, _, _, out) = ported()
    assertEquals(phase.policyReport.findings, Nil, phase.policyReport.render)
    val c = code(out)
    assert(clue(c).contains("class Buffer extends java.lang.AutoCloseable"))
    assert(c.contains("class Pooled extends java.lang.AutoCloseable"))
    assertEquals(c.linesIterator.count(_.contains("def close()")), 4,
      "one declaration per member of the component — the interface and three implementations")
    assertEquals(c.linesIterator.count(_.contains("dispose")), 0, s"`dispose` survives:\n$c")
  }

  test("every CALL moved with it, through all three receiver shapes") {
    val (_, _, _, out) = ported()
    val c = code(out)
    List("b.close()", "p.close()", "s.close()", "any.close()", "this.held.close()")
      .foreach(call => assert(c.contains(call), s"$call is not in:\n$c"))
    // …and the field's TYPE moved, which is the redirect's own half
    assert(c.contains("var held: java.lang.AutoCloseable"), c)
  }

  test("a REFUSED rename leaves the whole component upstream — never half of it") {
    // `shutdown` is not on `java.lang.AutoCloseable`, whose surface this engine knows exactly.
    val (phase, _, _, out) = ported(Map("dispose" -> "shutdown"))
    val c = code(out)
    assertEquals(c.linesIterator.count(_.contains("def dispose()")), 4)
    assertEquals(c.linesIterator.count(_.contains("shutdown")), 0, "NEVER INVENT A MEMBER")
    assertEquals(phase.policyReport.findings.size, 1, phase.policyReport.render)
  }

  test("emitted probe is written for a real compiler — ONE FILE PER UNIT, as a port writes it") {
    // The only instrument that sees cross-file coherence. Five valid-looking files that do not
    // agree about a member's name compile INDIVIDUALLY and fail together.
    //
    // Written per UNIT and not as `TirEmitter.emit`'s whole-program concatenation: five
    // `package com.demo` clauses in one file NEST (`com.demo.com.demo.…`), which is a fact about
    // scala's syntax and nothing to do with this phase — a probe in that shape fails for a reason
    // no port has, and would read as this feature being broken.
    val (phase, after, log, _) = ported()
    val emitter = new TirEmitter(after, notes = log)
    val dir = _root_.java.nio.file.Path
      .of(sys.props.getOrElse("balticporter.dumpProbe", s"${sys.props("user.dir")}/target/probe"),
          "type-redirect-rename")
    _root_.java.nio.file.Files.createDirectories(dir)
    after.units.foreach { u =>
      val f = dir.resolve(after.symbolOf(u.symbol).map(_.name).getOrElse("Unit") + ".scala")
      _root_.java.nio.file.Files.writeString(f, emitter.emitUnit(u))
    }
    assertEquals(phase.policyReport.findings, Nil, phase.policyReport.render)
    println(s"[type-redirect-rename-probe] wrote ${after.units.size} files under ${dir.toAbsolutePath}")
  }
