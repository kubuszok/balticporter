package balticporter.testkit

import balticporter.core.RuntimeMode
import balticporter.tir.*
import balticporter.transform.PackageRenameTransform

/** THE TESTKIT'S STRUCTURAL HALF — a spec over the ASSERTIONS, not over a transform.
  *
  * `PortSuite` offered four assertions and all four read EMITTED TEXT. That is the assertion a spec
  * reaches for when it has nothing better, and it is how a rule comes to pass the corpus without
  * being right (`CLAUDE.md` §3): a substring is present for many reasons, and three of the facts a
  * port is judged on are not in its text at all —
  *
  *   - which non-mechanical DECISION the engine recorded (`decisions.tsv`, §4.575);
  *   - which FINDING a check produced (`findings.tsv`, `DESIGN.md` §6.3);
  *   - what the engine REFUSED to render, which under the shipping default is a comment and `()`
  *     and under `preview` is a `compiletime.error` plus a recorded decision (§7.4).
  *
  * Each test below exercises one assertion in BOTH directions — it fires on the fact and stays
  * silent without it — because an assertion that cannot fail is the same defect one layer down.
  */
class StructuralAssertionsSpec extends PortSuite:

  // -- assertDecides / assertNotDecides -----------------------------------------------------------

  private val renamed =
    """package p;
      |public class Renamed { public int x = 1; }
      |""".stripMargin

  test("assertDecides sees a decision a phase RECORDED, which no emitted text states") {
    val p = port(renamed, new PackageRenameTransform(Map("p" -> "q")))
    // the emitted text says `package q`; it does not say that a POLICY entry moved it, which is the
    // question an agent reading the file in another repository actually asks (§4.575).
    assertEmits(p, "package q")
    assertDecides(p, Decision.Kind.RenamedPackage, about = "p.Renamed")
  }

  test("…and it FAILS when the decision is absent — the direction that makes it worth having") {
    val p = port(renamed) // no phases, so nothing decided anything
    intercept[munit.FailException](assertDecides(p, Decision.Kind.RenamedPackage))
    assertNotDecides(p, Decision.Kind.RenamedPackage)
  }

  test("assertNotDecides FAILS when the engine did decide — a silent act is a decision that is missing") {
    val p = port(renamed, new PackageRenameTransform(Map("p" -> "q")))
    intercept[munit.FailException](assertNotDecides(p, Decision.Kind.RenamedPackage))
  }

  test("`about` matches the subject's name AT DECISION TIME — the upstream one, not the emitted one") {
    val p = port(renamed, new PackageRenameTransform(Map("p" -> "q")))
    assertDecides(p, Decision.Kind.RenamedPackage, about = "p.Renamed")
    // `q.Renamed` is what the file says and is NOT what the decision is keyed on: a rename runs
    // last (§4.56), so every decision recorded before it names an upstream symbol.
    intercept[munit.FailException](assertDecides(p, Decision.Kind.RenamedPackage, about = "q.Renamed"))
  }

  // -- assertFinds / assertNoFindings -------------------------------------------------------------

  test("assertFinds reads the FLATTENED finding — the row `findings.tsv` and every baseline holds") {
    val p = port(
      """package p;
        |public class Reflective {
        |  public Object read(Class<?> c) throws Exception { return c.getDeclaredField("x"); }
        |}
        |""".stripMargin)
    val fs = PortabilityCheck.check(p.after).map(_.report("portability")(using p.after))
    assertFinds(fs, "java.lang.Class#getDeclaredField", detail = "reflective member access is JVM-only")
  }

  test("assertNoFindings is the other direction, and prints what it found rather than a number") {
    val p  = port("package p; public class Plain { public int add(int a, int b) { return a + b; } }")
    val fs = PortabilityCheck.check(p.after).map(_.report("portability")(using p.after))
    assertNoFindings(fs)
  }

  test("both fail on the opposite fact") {
    val p  = port("package p; public class Plain { public int add(int a, int b) { return a + b; } }")
    val fs = PortabilityCheck.check(p.after).map(_.report("portability")(using p.after))
    intercept[munit.FailException](assertFinds(fs, "java.lang.Thread"))

    val q  = port("package p; public class T { public void go() { Thread.currentThread(); } }")
    val qs = PortabilityCheck.check(q.after).map(_.report("portability")(using q.after))
    intercept[munit.FailException](assertNoFindings(qs))
  }

  // -- the preview fixture ------------------------------------------------------------------------

  /** a phase that leaves a jump with NO enclosing loop — the shape the emitter's only refusal
    * mechanism exists for, and one no Java source can express (javac rejects it). Constructing it
    * here is the honest fixture: the refusal is reachable at all only because a PHASE can produce a
    * tree the frontend never would. */
  private class StrandJump extends Phase:
    def name: String = "test/strand-jump"
    override def transformDefDef(d: Tree.DefDef)(using p: Program): Tree.DefDef =
      if d.rhs.isEmpty || !p.symbolOf(d.symbol).exists(_.name == "go") then d
      else d.copy(rhs = Some(Tree.Break(Some("nowhere"), TypeRepr.NoType, d.origin)))

  private val jumpy = port("package p; public class J { public void go() { int x = 1; } }", new StrandJump)

  test("the SHIPPING default emits the residue — a comment, which is all a normal run ever says") {
    assertEmits(jumpy, "/* break nowhere: label not in scope */")
    assertNotEmits(jumpy, "compiletime.error")
    assertEquals(jumpy.emitter.emissionDecisions, Nil)
  }

  test("PREVIEW turns the same site into a declared refusal, and RECORDS it") {
    assertPreviewEmits(jumpy, "scala.compiletime.error")
    assertPreviewEmits(jumpy, "porter: unrenderable")
    assertPreviewNotEmits(jumpy, "/* break nowhere: label not in scope */")

    val ds = jumpy.previewEmitter.emissionDecisions
    assertEquals(ds.map(_.kind), List(Decision.Kind.Unrenderable))
    assertEquals(ds.head.detail("construct"), "break")
  }

  test("the two emitters are two RECORDINGS — reading the preview does not disturb `out`") {
    // `TirEmitter.srcMap` and the member digests are values one emitter owns (§5.1). A preview that
    // shared the instance would leave a spec asserting about a recording made twice.
    val first = jumpy.out
    jumpy.previewOut
    assertEquals(jumpy.out, first)
    assert(jumpy.emitter ne jumpy.previewEmitter)
  }

  test("with nothing unrenderable, preview and deliverable agree once the fences come off") {
    // `DESIGN.md` §6.4's standing claim in miniature: at zero refusals the two modes are the same
    // emitter over the same tree. This is the fixture that would see them diverge.
    val p = port(renamed)
    assertEquals(p.previewOut, p.out)
  }

  // -- Ported.plan follows the RUNTIME MODE -------------------------------------------------------

  test("`plan` is derived from the mode the fixture was given, not from a constant") {
    val dep  = port(renamed)
    val vend = portIn(RuntimeMode.Vendored, renamed)
    assertEquals(dep.runtimeMode, RuntimeMode.Dependency)
    assertEquals(vend.runtimeMode, RuntimeMode.Vendored)
    assertEquals(dep.plan.mode, RuntimeMode.Dependency)
    assertEquals(vend.plan.mode, RuntimeMode.Vendored)
  }


  // -- assertConsults / assertNotConsults / assertCites -------------------------------------------
  //
  // The FOURTH fact a port is judged on, and the one nothing could reach until the obligation log
  // existed: whether the engine CONSIDERED a Java-vs-Scala difference at this construct. Text
  // cannot say it — a lowering that happens to produce the right output without ever asking the
  // question emits exactly the same characters, and that is the state an arm regresses into.

  private val identity = "public class I { boolean f(Object a, Object b) { return a == b; } }"

  test("assertConsults sees a difference the frontend CONSIDERED, which the text cannot state") {
    val p = port(identity)
    assertEmits(p, " eq ")                                        // what the text can say
    assertConsults(p, balticporter.catalog.JS.E(1), fired = true) // what only the log can
  }

  test("...and it FAILS for a difference this construct never reaches") {
    val p = port(identity)
    // JS-E03 attaches at the STATEMENT dispatch and there is no compound assignment here.
    intercept[munit.FailException](assertConsults(p, balticporter.catalog.JS.E(3)))
    assertNotConsults(p, balticporter.catalog.JS.E(3))
  }

  test("`fired` is a SEPARATE claim - a live branch that never applies is the normal state") {
    val p = port("public class C { void f(int i) { i += 1; } }")
    // consulted, because the arm asked; not fired, because `int += int` needs no narrowing. A
    // single number could not tell those apart, which is why the assertion takes two.
    assertConsults(p, balticporter.catalog.JS.E(3))
    intercept[munit.FailException](assertConsults(p, balticporter.catalog.JS.E(3), fired = true))
  }

  test("assertNotConsults FAILS when the engine did consider it") {
    val p = port(identity)
    intercept[munit.FailException](assertNotConsults(p, balticporter.catalog.JS.E(1)))
  }

  test("assertCites reads the PHASE surface, which is a different and weaker claim") {
    // A phase does not walk one node kind, so nothing can assert it *should have* considered a
    // difference at a declaration it never visited. What a citation says is that it DID - here,
    // that `TestFrameworkTransform` re-applied java's binary numeric promotion (JS-E07) inside a
    // named member.
    val junit =
      """package p;
        |import org.junit.Assert;
        |import org.junit.Test;
        |public class T {
        |  @Test public void widens() { long v = 2L; Assert.assertEquals(1, v); }
        |}
        |""".stripMargin
    val p = port(junit, new balticporter.transform.TestFrameworkTransform())
    assertCites(p, balticporter.catalog.JS.E(7), about = "widens")
    intercept[munit.FailException](assertCites(p, balticporter.catalog.JS.E(7), about = "nosuchmember"))
  }

  test("...and a suite with no promotion cites NOTHING - the citation is not a per-phase constant") {
    val plain =
      """package p;
        |import org.junit.Assert;
        |import org.junit.Test;
        |public class U {
        |  @Test public void same() { Assert.assertEquals(1, 1); }
        |}
        |""".stripMargin
    val p = port(plain, new balticporter.transform.TestFrameworkTransform())
    intercept[munit.FailException](assertCites(p, balticporter.catalog.JS.E(7)))
  }
