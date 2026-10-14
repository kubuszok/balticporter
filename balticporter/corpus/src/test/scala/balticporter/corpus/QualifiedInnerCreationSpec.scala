package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir

/** `outer.new Inner()` names its enclosing instance, so scala names it in the path, `new outer.Inner()`. */
class QualifiedInnerCreationSpec extends munit.FunSuite:

  private def emit(src: String): String = new TirEmitter(SpoonTir.fromSource(src)).emit

  private val src =
    """package demo;
      |class Effect {
      |  static class Mask {
      |    int size = 3;
      |    class Cursor { int at() { return size; } }
      |    Cursor own() { return new Cursor(); }
      |  }
      |  int run(Mask mask) {
      |    Mask.Cursor c = mask.new Cursor();
      |    return c.at();
      |  }
      |}
      |""".stripMargin

  test("a qualified inner-class creation keeps its enclosing instance in the path") {
    val out = emit(src)
    assert(clue(out).contains("new mask.Cursor()"))
    // the unqualified creation inside the outer class is unchanged
    assert(out.contains("new Cursor()"))
  }
