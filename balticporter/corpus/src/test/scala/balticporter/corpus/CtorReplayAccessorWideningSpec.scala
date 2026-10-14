package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

/** A field a constructor replay widens is never left narrower than a PUBLIC bean accessor of it: callers reach the property at that level already, and code written against the port sets it as a
  * field.
  */
class CtorReplayAccessorWideningSpec extends munit.FunSuite:

  private val src =
    """package demo.ui;
      |public class Base {
      |  public Base() { }
      |  public Base(int a) { }
      |  public Base(int a, int b) { }
      |}
      |public class Img extends Base {
      |  private int scaling = 0;
      |  private int hidden = 0;
      |  private int chars = 0;
      |  public Img() { super(); }
      |  public Img(int s) { super(1); this.scaling = s; this.hidden = s; this.chars = s; }
      |  public Img(int s, int t) { super(1, 2); this.scaling = s; this.hidden = t; this.chars = t; }
      |  public void setScaling(int s) { this.scaling = s; }
      |  public int getChars() { return chars; }
      |}
      |""".stripMargin

  private val out = new TirEmitter(Pipeline.run(SpoonTir.fromSource(src), Nil)).emit

  private def declOf(field: String): String =
    out.linesIterator.find(l => l.contains(s"var $field:")).getOrElse(fail(s"no declaration of `$field` in\n$out")).trim

  test("a field with a public setter, written by a this-only replay, is widened to public, not protected") {
    assert(clue(declOf("scaling")).startsWith("var scaling:"))
  }

  test("…and so is a field with only a public getter") {
    assert(clue(declOf("chars")).startsWith("var chars:"))
  }

  test("NEGATIVE: a field with no accessor keeps the narrowest access the replay needs") {
    assert(clue(declOf("hidden")).startsWith("protected"))
  }
