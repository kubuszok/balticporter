package balticporter.corpus

import balticporter.core.PolicyIssue
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.*
import balticporter.transform.BeanPropertyTransform

/** `bean-properties` END TO END — java in, emitted Scala out, through the same pipeline a port runs.
  *
  * The unit specs assert the mechanism; this asserts the ARTEFACT: the text an adopter reads, the
  * porter note beside it, and the §1(b) never-fired report. It also writes a PROBE an operator can
  * put a real compiler over, because "`def x` / `def x_=` and `o.x = v` type-check" is a claim about
  * scalac and a string assertion is not evidence for it (the shape `LabeledJumpSpec` uses, and for
  * the same reason: a forked test JVM cannot be handed a compiler).
  *
  * {{{ scala-cli compile --scala 3.8.4 --server=false <the path printed below> }}}
  */
class BeanPropertyPortSpec extends PortSuite:

  private val src =
    """
    package demo;

    /** A layer. */
    public interface Layer {
      float getOpacity();
      void setOpacity(float o);
      String getName();
    }
    """

  private val impl =
    """
    package demo;

    public class Basic implements Layer {
      private float opacity = 1.0f;
      private String name = "";

      /** the layer's opacity. */
      public float getOpacity() { return opacity; }
      public void setOpacity(float o) { this.opacity = o; }
      public String getName() { return name; }

      void fade() { setOpacity(getOpacity() * 0.5f); }
    }

    class Client {
      void go(Layer l) {
        l.setOpacity(l.getOpacity() + 0.25f);
        float f = l.getOpacity();
        String n = l.getName();
      }
    }
    """

  private val pairs = Map(
    "demo.Layer#opacity" -> "getOpacity/setOpacity",
    "demo.Layer#name"    -> "getName",
  )

  private def ported(extra: Map[String, String] = Map.empty) =
    val phase = new BeanPropertyTransform(pairs ++ extra)
    val before = SpoonTir.fromSource(src + "\n" + impl, "Demo.java")
    val (after, log) = Pipeline.runTraced(before, List(phase))
    (phase, after, log, new TirEmitter(after, notes = log).emit)

  /** the emitted CODE, with the porter notes stripped. A note names the UPSTREAM member on purpose
    * (§4.575's `from=`), so any check that searches emitted text for an upstream name has to strip
    * them first — the mistake `SubstitutionCheck.dangling` made on its first run with notes. */
  private def code(out: String): String =
    out.linesIterator.filterNot(l => l.contains(PorterNote.Marker) || l.trim.startsWith("—")).mkString("\n")

  test("the emitted SURFACE is a property on the interface and on the implementor") {
    val (phase, _, _, out) = ported()
    assertEquals(phase.policyReport.findings, Nil, phase.policyReport.render)
    assert(clue(out).contains("def opacity: scala.Float"))
    assert(out.contains("def opacity_=(o: scala.Float): scala.Unit"))
    assert(out.contains("def name: java.lang.String"))
    // the IMPLEMENTOR's declaration is a property too — an interface whose accessor became `def x`
    // and an implementor that kept `def x()` do not override each other.
    assertEquals(clue(code(out)).linesIterator.count(_.contains("override def opacity: scala.Float")), 1)
    assertEquals(code(out).linesIterator.count(_.contains("getOpacity")), 0)
    assertEquals(code(out).linesIterator.count(_.contains("setOpacity")), 0)
    assertEquals(code(out).linesIterator.count(_.contains("getName")), 0)
  }

  test("every call site moved with it — read, write, compound, and the bare in-class form") {
    val (_, _, _, out) = ported()
    assert(clue(out).contains("l.opacity = l.opacity + 0.25f"))
    assert(out.contains("val f: scala.Float = l.opacity"))
    assert(out.contains("val n: java.lang.String = l.name"))
    assert(clue(out).contains("this.opacity = this.opacity * 0.5f"))
  }

  test("the PORTER NOTE is beside the code, and the UPSTREAM COMMENT still comes first (§4.575)") {
    val (_, _, _, out) = ported()
    assert(clue(out).contains("/* porter: renamed-member"))
    assert(out.contains("phase=bean-properties"))
    assert(out.contains("key=demo.Layer#opacity"))
    assert(out.contains("reason=configured"))
    // trivia FIRST, note LAST, member next — a note above the javadoc displaces what the licence
    // obliges the port to reproduce.
    val doc  = out.indexOf("the layer's opacity")
    val note = out.indexOf("/* porter:", doc)
    val defn = out.indexOf("def opacity: scala.Float", doc)
    assert(doc >= 0 && note > doc && defn > note, s"trivia/note/member order is wrong:\n$out")
  }

  test("a pairs entry that NEVER FIRES reports through the binder — not as a silent no-op") {
    val (phase, _, log, out) = ported(Map("demo.Layer#missing" -> "getMissing/setMissing"))
    val fs = phase.policyReport.findings
    assertEquals(clue(fs).count(_.issue == PolicyIssue.NeverMatched), 2,
      "both accessors of the entry named nothing, and each is its own bound key")
    assert(fs.forall(_.setting.contains("demo.Layer#missing")))
    assert(!out.contains("missing"), "NEVER INVENT A MEMBER")
    // …and the entries that DID fire are unaffected: a broken entry is not a broken phase.
    assert(out.contains("def opacity: scala.Float"))
  }

  test("an entry naming a type the program does not DECLARE reports, and rewrites nothing") {
    val (phase, _, log, _) = ported(Map("java.lang.String#len" -> "length"))
    // the finding's KEY is the ACCESSOR key that was bound; the SETTING quotes the manifest entry.
    val fs = phase.policyReport.findings.filter(_.setting.contains("java.lang.String#len"))
    assertEquals(clue(fs).size, 1)
    assertEquals(fs.head.issue, PolicyIssue.NeverMatched)
  }

  test("EMPTY pairs leaves the port byte-identical") {
    val plain = new TirEmitter(SpoonTir.fromSource(src + "\n" + impl, "Demo.java")).emit
    val phase = new BeanPropertyTransform()
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(src + "\n" + impl, "Demo.java"), List(phase))
    assertEquals(new TirEmitter(after).emit, plain)
    assertEquals(log.all, Nil)
  }

  test("emitted probe is written for a real compiler") {
    val (_, _, _, out) = ported()
    val p = _root_.java.nio.file.Path
      .of(sys.props.getOrElse("balticporter.dumpProbe", s"${sys.props("user.dir")}/target/probe"),
          "BeanPropertyProbe.scala")
    _root_.java.nio.file.Files.createDirectories(p.getParent)
    _root_.java.nio.file.Files.writeString(p, out)
    println(s"[bean-property-probe] wrote ${p.toAbsolutePath}")
  }
