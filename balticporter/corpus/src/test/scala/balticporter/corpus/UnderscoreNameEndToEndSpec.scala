package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

import java.nio.file.{ Files, Path }

/** A java identifier ending in `_` before a `:` — scala lexes `other_:` as one operator identifier — in every position the emitter writes `name: Type`: a field, a local, a parameter, a parenless
  * member, a catch binder and a type-pattern binder. The port is COMPILED under `-Werror` and run.
  */
class UnderscoreNameEndToEndSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |public class Pair {
      |  public final Object a_;
      |  public Pair(Object a_) { this.a_ = a_; }
      |  public Object first_() { return a_; }
      |  @Override public boolean equals(Object other) {
      |    if (!(other instanceof Pair)) return false;
      |    Pair other_ = (Pair) other;
      |    return a_.equals(other_.a_);
      |  }
      |  @Override public int hashCode() { return a_.hashCode(); }
      |  public static String describe(Object o) {
      |    String t = switch (o) { case String s_ -> "s:" + s_; default -> ""; };
      |    if (!t.isEmpty()) return t;
      |    try { Integer.parseInt((String) o); return "n"; }
      |    catch (RuntimeException e_) { return "x:" + e_.getClass().getSimpleName(); }
      |  }
      |  public static String run() {
      |    Pair p_ = new Pair("q");
      |    return p_.equals(new Pair("q")) + "," + p_.first_() + "," + describe("hi") + "," + describe(Integer.valueOf(1));
      |  }
      |}
      |""".stripMargin

  private lazy val emitted: List[String] =
    val p  = Pipeline.run(SpoonTir.fromSource(src), Nil)
    val em = new TirEmitter(p)
    p.units.map(em.emitUnit)

  private lazy val compiled: Path =
    val dir   = Files.createTempDirectory("underscore-name-e2e-src")
    val files = emitted.zipWithIndex.map { (text, n) =>
      val f = dir.resolve(s"U$n.scala"); Files.writeString(f, text); f.toString
    }
    val out    = Files.createTempDirectory("underscore-name-e2e-out")
    val stdlib = List(classOf[scala.Option[?]], classOf[scala.deriving.Mirror]).map(c => Path.of(c.getProtectionDomain.getCodeSource.getLocation.toURI).toString).distinct
    val args   = "-Werror" :: "-classpath" :: stdlib.mkString(java.io.File.pathSeparator) :: "-d" :: out.toString :: files
    val rep    = dotty.tools.dotc.Main.process(args.toArray)
    assert(
      !rep.hasErrors,
      s"the emission does not compile:\n${rep.allErrors.map(_.msg.message).mkString("\n")}\n${emitted.mkString("\n")}"
    )
    out

  test("a `_`-final name never meets a `:` directly") {
    val all = emitted.mkString("\n")
    assert(!clue(all).matches("(?s).*[A-Za-z0-9]_:.*"), all)
  }

  test("the port compiles and answers as java does") {
    val loader = new java.net.URLClassLoader(Array(compiled.toUri.toURL), getClass.getClassLoader)
    val got    = loader.loadClass("demo.Pair").getMethod("run").invoke(null).asInstanceOf[String]
    assertEquals(got, "true,q,s:hi,x:ClassCastException")
  }
