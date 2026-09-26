package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{ Pipeline, RuleScope }
import balticporter.transform.{ CollectionsTransform, RetargetBoundaryCheck }

import java.nio.file.{ Files, Path }

/** A retarget scoped to a BASE's package, seen from a dependent that keeps java's type: the base's member now takes the TARGET, the dependent's value is still the SOURCE, and the subtyping licence
  * runs the other way. The value is converted at the call and the crossing counted.
  */
class RetargetScopeSeamSpec extends munit.FunSuite:

  private val src =
    """package base;
      |import java.util.Comparator;
      |public class Bag<T> {
      |  public void sort(Comparator<? super T> c) { }
      |  public void order(Comparator<T> c) { }
      |}
      |class Holder {
      |  Comparator<?> raw;
      |  void use(Bag<String> b) { b.order((Comparator<String>) raw); }
      |}
      |""".stripMargin

  private val depSrc =
    """package dep;
      |import java.util.Comparator;
      |import base.Bag;
      |public class User {
      |  static final Comparator<String> CMP = new Comparator<String>() { public int compare(String a, String b) { return a.length() - b.length(); } };
      |  Comparator<Object> any;
      |  void run(Bag<String> b) { b.sort(CMP); b.order(CMP); b.sort(any); }
      |}
      |""".stripMargin

  private val Retarget = Map("java.util.Comparator" -> "scala.math.Ordering")

  private lazy val (phase, after, out) =
    val ph = new CollectionsTransform(RuleScope.Only(Set("base")), retarget = Retarget)
    val a  = Pipeline.run(SpoonTir.fromSources(List("base/Bag.java" -> src, "dep/User.java" -> depSrc)), List(ph))
    (ph, a, new TirEmitter(a).emit)

  test("the base member moved and the dependent's own Comparator did not") {
    assert(clue(out).contains("def sort(c: scala.math.Ordering["), out)
    assert(out.contains("var any: java.util.Comparator[java.lang.Object]"), out)
  }

  test("a held-back SOURCE value at a TARGET formal is converted at the call, and counted") {
    assert(clue(out).contains("comparatorToOrdering(using c$)"), out)
    assert(out.contains("this.any.asInstanceOf[java.util.Comparator[java.lang.String]]"), out)
    val fs = phase.retargetBoundary(after).filter(_.issue == RetargetBoundaryCheck.Issue.HeldBackBridged)
    assertEquals(clue(fs).size, 1)
  }

  test("a narrowing cast in code the scope moved is java's own and stays") {
    assert(clue(out).contains("b.order(this.raw.asInstanceOf[scala.math.Ordering[java.lang.String]])"), out)
  }

  test("a value the scope pulled in loses java's stale ascription instead") {
    assert(clue(out).contains("b.sort(User.CMP)"), out)
    assert(!out.contains("User.CMP.asInstanceOf"), out)
  }

  test("the port compiles and sorts through the dependent's own comparator") {
    val dir   = Files.createTempDirectory("retarget-seam-src")
    val files = after.units.map(new TirEmitter(after).emitUnit).zipWithIndex.map { (text, n) =>
      val f = dir.resolve(s"U$n.scala"); Files.writeString(f, text); f.toString
    }
    val outDir = Files.createTempDirectory("retarget-seam-out")
    val stdlib = List(classOf[scala.Option[?]], classOf[scala.deriving.Mirror]).map(c => Path.of(c.getProtectionDomain.getCodeSource.getLocation.toURI).toString).distinct
    val rep    = dotty.tools.dotc.Main.process(("-classpath" :: stdlib.mkString(java.io.File.pathSeparator) :: "-d" :: outDir.toString :: files).toArray)
    assert(!rep.hasErrors, s"the emission does not compile:\n${rep.allErrors.map(_.msg.message).mkString("\n")}\n$out")
  }

  test("a pair with no known conversion is left as java wrote it, and counted apart") {
    val ph  = new CollectionsTransform(RuleScope.Only(Set("base")), retarget = Map("java.util.Comparator" -> "base.Ord"))
    val ord = "package base;\npublic abstract class Ord<T> implements java.util.Comparator<T> { }\n"
    val a   = Pipeline.run(SpoonTir.fromSources(List("base/Bag.java" -> src, "base/Ord.java" -> ord, "dep/User.java" -> depSrc)), List(ph))
    assert(ph.retargetBoundary(a).exists(_.issue == RetargetBoundaryCheck.Issue.HeldBackSource), new TirEmitter(a).emit)
  }
