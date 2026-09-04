package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir

/** A `static { }` block is not a NAME, so it may never reach an `export` selector list. */
class StaticExportInitBlockSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |/** the parent carries the statics an heir must re-export. */
      |class Parent {
      |  public static final long BASE = 1L;
      |  public static final String SHARED = "shared";
      |}
      |/** inherits statics AND declares an initializer block — both halves at once. */
      |class Heir extends Parent {
      |  public static final String SHARED = "mine";
      |  public static long REGISTERED;
      |  static { REGISTERED = BASE + 1; }
      |}
      |""".stripMargin

  private val out = new TirEmitter(SpoonTir.fromSource(src)).emit

  test("a static initializer block never appears in an export selector") {
    assert(clue(out).contains("export demo.Parent."), "the heir must still re-export the parent's companion")
    assert(!out.contains("<clinit>"), s"`<clinit>` reached an export selector:\n$out")
    assert(!out.contains("<initblock>"), s"`<initblock>` reached an export selector:\n$out")
  }

  test("an ordinary redeclared static IS still excluded — the exclusion itself is not disabled") {
    assert(clue(out).contains("SHARED => _"),
      "the heir redeclares SHARED, so the parent's must be hidden or the export is a duplicate definition")
  }
