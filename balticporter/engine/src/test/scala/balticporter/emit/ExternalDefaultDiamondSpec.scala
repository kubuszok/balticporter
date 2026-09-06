package balticporter.emit

import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

/** JLS 8.4.8: where a SUPERCLASS declares a concrete member and an EXTERNAL interface parent
  * carries a JLS 9.4.3 `default` of the same name and arity, the class member wins in java and
  * scala reports `E164`. The diamond forwarder mints the class-wins override off the frontend's
  * recorded defaults (`ENGINE-LIMITS.md` K39, `CLAUDE.md` §4.56). */
class ExternalDefaultDiamondSpec extends munit.FunSuite:

  private def emit(src: String): String =
    new TirEmitter(Pipeline.run(SpoonTir.fromSource(src), Nil)).emit

  private val base =
    """package extdef;
      |public class CursorBase {
      |  public void remove () { }
      |  public boolean hasNext () { return false; }
      |}
      |""".stripMargin

  test("a class-file DEFAULT beside a concrete superclass member mints the forwarder") {
    val out = emit(base +
      """public class Cursor extends CursorBase implements java.util.Iterator<String> {
        |  public String next () { return null; }
        |}
        |""".stripMargin)
    assert(clue(out).contains("override def remove(): scala.Unit = super[CursorBase].remove()"))
  }

  test("an ABSTRACT interface method mints nothing — `hasNext` is concrete on the superclass only") {
    val out = emit(base +
      """public class Cursor extends CursorBase implements java.util.Iterator<String> {
        |  public String next () { return null; }
        |}
        |""".stripMargin)
    assert(!clue(out).contains("super[CursorBase].hasNext()"))
  }

  test("a class that DECLARES the member itself mints nothing — no override to disambiguate") {
    val out = emit(base +
      """public class OwnCursor extends CursorBase implements java.util.Iterator<String> {
        |  public String next () { return null; }
        |  public void remove () { }
        |}
        |""".stripMargin)
    assert(!clue(out).contains("super[CursorBase].remove()"))
  }

  test("no external interface parent is the no-op") {
    val out = emit(base +
      """public class Plain extends CursorBase { }
        |""".stripMargin)
    assert(!clue(out).contains("super[CursorBase]"))
  }
