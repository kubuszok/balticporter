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

  test("a FIELD qualifier is bound once to a local first, since a field is no stable path") {
    val out = emit(
      """package demo;
        |class Chooser {
        |  class Item { }
        |}
        |class Adapter {
        |  private final Chooser chooser;
        |  Adapter(Chooser c) { chooser = c; }
        |  Chooser.Item make() { return chooser.new Item(); }
        |}
        |""".stripMargin
    )
    assert(clue(out).contains("val outer$q: demo.Chooser = this.chooser"), out)
    assert(out.contains("new outer$q.Item()"), out)
    assert(!out.contains("new demo.Chooser#Item"), out)
    // …and the local the path names is a READ: the unused-symbol pass keeps it
    val kept = new TirEmitter(
      balticporter.tir.Pipeline.run(
        SpoonTir.fromSource(
          "package demo;\nclass C { class I {} }\nclass A { private final C c; A(C c) { this.c = c; } C.I make() { return c.new I(); } }\n"
        ),
        List(new balticporter.transform.UnusedSymbolTransform)
      )
    ).emit
    assert(clue(kept).contains("val outer$q"), kept)
  }
