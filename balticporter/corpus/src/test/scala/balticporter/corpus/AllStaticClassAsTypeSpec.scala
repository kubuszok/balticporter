package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir

/** An all-static Java class is still a TYPE — `object` is a value, and no value is a type. */
class AllStaticClassAsTypeSpec extends munit.FunSuite:

  private def emit(src: String): String = new TirEmitter(SpoonTir.fromSource(src)).emit

  private val named =
    """package demo;
      |/** every member static, and NOTHING here extends or instantiates it — but `Consumer` names
      |  * it in a type position and passes its class literal. */
      |class Ext {
      |  public static final String EXT = "an_extension";
      |}
      |""".stripMargin

  private val consumer =
    """package demo;
      |class Consumer {
      |  Ext held;
      |  Class<Ext> which() { return Ext.class; }
      |}
      |""".stripMargin

  private val unnamed =
    """package demo;
      |/** the ordinary constant holder: statics only, and nobody anywhere needs the NAME to be a
      |  * type. This one must still collapse. */
      |class Align {
      |  public static final int center = 1;
      |  public static final int top = 2;
      |}
      |""".stripMargin

  test("a constant holder ANOTHER unit names in a type position stays a class") {
    val out = emit(named + consumer)
    assert(clue(out).contains("class Ext"), "Ext is named as a type, so it must survive as a class")
    assert(out.contains("object Ext"), "…with its statics in the companion, which is where they live")
    assert(out.contains("inline val EXT"), "and the constant must still be reachable")
  }

  test("a constant holder NOBODY names as a type still collapses to an object") {
    val out = emit(unnamed)
    assert(clue(out).contains("object Align"))
    assert(!out.contains("class Align"),
      "the collapse must survive this fix — de-collapsing every constant holder is not the goal")
  }

  test("a CLASS LITERAL alone keeps it a class — the half a declaration type cannot see") {
    // `ext(KHRMaterialsUnlit.class, …)` infers the callee's `T` and declares nothing, so no
    // symbol's `info` mentions the type. `Ext.class` still requires the name to BE a type.
    val out = emit(named +
      """package demo;
        |class LiteralOnly {
        |  Object which() { return Ext.class; }
        |}
        |""".stripMargin)
    assert(clue(out).contains("class Ext"))
  }

  test("a STATIC ACCESS does NOT name a type — the over-approximation this rule must not make") {
    // `Gdx.app` is the very thing a collapsed object is right for. Reading `Phase.transformType`
    // bare counts a term's own `tpe` as a type occurrence and de-collapses it: measured on libGDX
    // core, 29 of its 31 constant holders and 36 members of emitted text, for a question none of
    // them answers — and it still compiled, which is what makes this worth pinning rather than
    // leaving to a count.
    val out = emit(unnamed +
      """package demo;
        |class Reader {
        |  int read() { return Align.center | Align.top; }
        |}
        |""".stripMargin)
    assert(clue(out).contains("object Align"))
    assert(!out.contains("class Align"),
      "a static ACCESS is not a type position; counting it de-collapses every constant holder")
  }

  test("the type's OWN unit does not count as naming it") {
    // Every class names itself constantly — its `this`, its members' owner types, its synthesised
    // constructor. Counted whole-program, the reference set would contain every symbol and the
    // collapse would be disabled outright rather than narrowed. `unnamed` alone proves the
    // exclusion holds; this adds the case where the holder's own members mention it BY NAME.
    val selfNaming =
      """package demo;
        |class Registry {
        |  public static final int A = 1;
        |  public static int twice() { return Registry.A * 2; }
        |}
        |""".stripMargin
    val out = emit(selfNaming)
    assert(clue(out).contains("object Registry"))
    assert(!out.contains("class Registry"))
  }
