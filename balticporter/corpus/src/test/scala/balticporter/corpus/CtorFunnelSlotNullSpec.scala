package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

/** A `null` in a delegation THE ENGINE MINTED is ASCRIBED — `ENGINE-LIMITS.md` C8's own sentence,
  * one argument to the left of where it was already written. */
class CtorFunnelSlotNullSpec extends munit.FunSuite:

  private def emit(src: String): String =
    new TirEmitter(Pipeline.run(SpoonTir.fromSource(src), Nil)).emit

  /** the shape, reduced from a real port: `Data()` and `Data(Handle, String)` both reach the same
    * (nilary) parent constructor, so the primary is SYNTHESISED; `path` is assigned in the leading
    * run of one root, so it is a FIELD SLOT; and `Data(Handle)` is a real one-argument constructor
    * at a reference type, which is what `this(null)` is also applicable to. */
  private val src =
    """package demo;
      |public class Handle {}
      |public class Base { protected Base() {} }
      |public class Data extends Base {
      |  public String path = null;
      |  public Data() { super(); }
      |  public Data(Handle h) { this(h, null); }
      |  public Data(Handle h, String imagePath) { super(); path = imagePath; }
      |}
      |""".stripMargin

  private lazy val out = emit(src)

  test("the synthesised primary really does hoist the field — the shape this is about") {
    assert(clue(out).contains("class Data protected (f$path: java.lang.String)"))
    assert(out.contains("def this(h: demo.Handle)"), "the ambiguous sibling is not in the fixture")
  }

  test("the MINTED delegation ascribes its `null`, so scalac has one applicable candidate") {
    assert(clue(out).contains("this((null: java.lang.String))"),
           "the engine's own delegation still writes a bare `null`, which is applicable to every " +
             "reference-typed one-argument constructor as well as to the primary")
    assert(!out.linesIterator.exists(_.trim == "this(null)"), clue(out))
  }

  test("JAVA'S OWN delegation is untouched — java resolved it and the engine has no standing to") {
    // `Data(Handle h) { this(h, null); }` is a delegation the SOURCE wrote, against a candidate set
    // javac already chose from. Ascribing there would be the engine re-deciding a resolved call.
    assert(clue(out).contains("this(h, null)"), "java's own `this(h, null)` was rewritten")
  }

  test("a delegation with no `null` in it is byte-identical — the guard is the literal, not the slot") {
    val valued = emit(
      """package demo;
        |public class Handle {}
        |public class Base { protected Base() {} }
        |public class Data extends Base {
        |  public String path = "";
        |  public Data() { super(); }
        |  public Data(Handle h) { this(h, "x"); }
        |  public Data(Handle h, String imagePath) { super(); path = imagePath; }
        |}
        |""".stripMargin)
    assert(!valued.contains("(null:"), clue(valued))
    assert(clue(valued).contains("""this("")"""))
  }
