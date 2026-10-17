package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

import java.nio.file.{ Files, Path }

/** A subclass whose constructors reach DIFFERENT parent constructors keeps each `super(args)` by replaying the parent constructor after `this()`, even where its own promoted nilary body is not a
  * field write: that body runs on every path whether or not the replay happens (counted on its own), so it is no reason to drop the parent's arguments. Compiled under `-Werror` and run.
  */
class CtorReplayPromotedBodySpec extends munit.FunSuite:

  private val src =
    """package demo;
      |public class Widget { public int touches; public void touch() { touches++; } }
      |public class Label extends Widget {
      |  public String font; public String style; public String text;
      |  public Label() { font = "default"; style = "s:" + font; text = ""; }
      |  public Label(String t, String st) { this(t, st, st + "-font"); }
      |  public Label(String t, String st, String f) { font = f; style = st; text = t; touch(); }
      |  public Label(String t, String f, int c) { font = f; style = "none"; text = t + c; }
      |  public Label(String t, StringBuilder f) { this(t, f, null); }
      |  public Label(String t, StringBuilder f, String c) { font = f.toString(); style = "sb"; text = t; if (c != null) text = t + c; if (c == null) touch(); }
      |}
      |public class Typing extends Label {
      |  public int texts;
      |  public Typing() { super(); setText(""); }
      |  public Typing(String t, String st) { super(t.trim(), st); setText(text); }
      |  public Typing(String t, String st, String f) { super(t, st, f); setText(text); }
      |  public Typing(String t, String f, int c) { super(t, f, c); setText(text); }
      |  public Typing(String t, StringBuilder f) { super(t, f); setText(text); }
      |  void setText(String s) { texts++; }
      |  String show() { return font + "/" + style + "/" + text + "/" + touches; }
      |  public static String run() {
      |    return new Typing().show() + "|" + new Typing(" x ", "st").show() + "|" + new Typing("y", "st2", "F").show() + "|" +
      |      new Typing("z", "G", 3).show() + "|" + new Typing("w", new StringBuilder("H")).show();
      |  }
      |}
      |""".stripMargin

  private lazy val emitted: List[String] =
    val p  = Pipeline.run(SpoonTir.fromSource(src), Nil)
    val em = new TirEmitter(p)
    p.units.map(em.emitUnit)

  private lazy val compiled: Path =
    val dir   = Files.createTempDirectory("ctor-replay-promoted-src")
    val files = emitted.zipWithIndex.map { (text, n) =>
      val f = dir.resolve(s"U$n.scala"); Files.writeString(f, text); f.toString
    }
    val out    = Files.createTempDirectory("ctor-replay-promoted-out")
    val stdlib = List(classOf[scala.Option[?]], classOf[scala.deriving.Mirror]).map(c => Path.of(c.getProtectionDomain.getCodeSource.getLocation.toURI).toString).distinct
    val args   = "-Werror" :: "-classpath" :: stdlib.mkString(java.io.File.pathSeparator) :: "-d" :: out.toString :: files
    val rep    = dotty.tools.dotc.Main.process(args.toArray)
    assert(
      !rep.hasErrors,
      s"the emission does not compile:\n${rep.allErrors.map(_.msg.message).mkString("\n")}\n${emitted.mkString("\n")}"
    )
    out

  test("each subclass constructor runs the parent constructor java's `super(args)` named") {
    val loader = new java.net.URLClassLoader(Array(compiled.toUri.toURL), getClass.getClassLoader)
    val got    = loader.loadClass("demo.Typing").getMethod("run").invoke(null).asInstanceOf[String]
    assertEquals(got, "default/s:default//0|st-font/st/x/1|F/st2/y/1|G/none/z3/0|H/sb/w/1", emitted.mkString("\n"))
  }
