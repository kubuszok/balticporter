package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir

/** A field/method clash is decided by PLACEMENT — `TirEmitter.resolveMemberClashes`. */
class MemberClashPlacementSpec extends munit.FunSuite:

  private def emit(src: String): String = new TirEmitter(SpoonTir.fromSource(src)).emit

  // -------------------------------------------------------------------------------------------
  // the false positive — a private constructor's static factory beside the field it fills
  // -------------------------------------------------------------------------------------------

  private val factoryIdiom =
    """package demo;
      |public class Family {
      |  private final int all;
      |  private final int one;
      |  private Family (int all, int one) { this.all = all; this.one = one; }
      |  public int getAll () { return all; }
      |  public static Family all (int n) { return new Family(n, 0); }
      |  public static Family one (int n) { return new Family(0, n); }
      |}
      |""".stripMargin

  test("a STATIC factory does not clash with the instance field it fills — nothing is renamed") {
    val out = emit(factoryIdiom)
    assert(!clue(out).contains("all$field"))
    assert(!out.contains("one$field"))
  }

  test("…and the two really do land in different scopes: the field in the class, the factory in the companion") {
    val out = emit(factoryIdiom)
    val cls = out.indexOf("class Family")
    val obj = out.indexOf("object Family")
    assert(clue(cls) >= 0 && clue(obj) > cls)
    // the factory is emitted after the `object` keyword; the field's reader before it
    assert(out.indexOf("def all(") > obj)
    assert(out.indexOf("def getAll(") < obj)
  }

  // -------------------------------------------------------------------------------------------
  // NEGATIVES — the pass must still rename every clash that is real
  // -------------------------------------------------------------------------------------------

  test("NEGATIVE: an instance field and an INSTANCE method of the same name still clash") {
    val out = emit(
      """package demo;
        |public class Builder {
        |  private int all;
        |  public Builder all (int n) { this.all = n; return this; }
        |}
        |""".stripMargin)
    assert(clue(out).contains("all$field"))
  }

  test("NEGATIVE: an instance field and a method in a SUBCLASS still clash — the instance scope is inherited") {
    val out = emit(
      """package demo;
        |class Base { protected int hasNext; }
        |class Sub extends Base { public boolean hasNext () { return hasNext != 0; } }
        |""".stripMargin)
    assert(clue(out).contains("hasNext$field"))
  }

  test("NEGATIVE: a STATIC field and a STATIC method of the same name still clash — one companion") {
    val out = emit(
      """package demo;
        |public class Holder {
        |  private static int count;
        |  private int keepMeAClass;
        |  public static int count (int n) { return n; }
        |}
        |""".stripMargin)
    assert(clue(out).contains("count$field"))
  }

  test("the fresh name KEEPS APPENDING until it is free — `$` is an ordinary java identifier char") {
    // `x$field` is a name java can declare, so appending once may land the rename straight on top of
    // a member that is already there — and the failure is silent, because the emitted duplicate is a
    // name neither the rename decision nor any count mentions. Both sibling passes keep appending
    // (`style$shadow`, `funnelParamRenames`); this one did not.
    val field = emit(
      """package demo;
        |public class C {
        |  private int x;
        |  private int x$field;
        |  public int x () { return x; }
        |}
        |""".stripMargin)
    assert(clue(field).contains("var x$field$:"), field)
    assertEquals(clue(field.linesIterator.count(_.contains("var x$field:"))), 1, field)

    // (ii) …and a METHOD of the fresh name, which the clash test itself already holds.
    val method = emit(
      """package demo;
        |public class E {
        |  private int y;
        |  public int y () { return y; }
        |  public int y$field () { return 0; }
        |}
        |""".stripMargin)
    assert(clue(method).contains("var y$field$:"), method)
    assertEquals(clue(method.linesIterator.count(_.contains("def y$field("))), 1, method)
  }

  test("NEGATIVE: with nothing in the way it appends exactly once — the loop is a guard, not a policy") {
    val out = emit(
      """package demo;
        |public class D {
        |  private int x;
        |  public int x () { return x; }
        |}
        |""".stripMargin)
    assert(clue(out).contains("x$field"), out)
    assert(!clue(out).contains("x$field$"), out)
  }

  // -------------------------------------------------------------------------------------------
  // THE JOIN KEY — a rename moves `Symbol.name` and must NOT move `Symbol.fullName`
  // -------------------------------------------------------------------------------------------

  /** Every artifact that joins POLICY to EMITTED CODE keys a member on `owner#name` (`MemberKey`),
    * and the port map's `upstream` column is that key spelled in JAVA's names. That is right today
    * for one reason and one only: the §4.55 passes rewrite `Symbol.name`, which the emitter renders,
    * and leave `Symbol.fullName`, which is a separate stored field. */
  test("a §4.55 rename moves the emitted NAME and leaves `Symbol.fullName` spelling JAVA's") {
    val src =
      """package demo;
        |public class Builder {
        |  private int all;
        |  public Builder all (int n) { this.all = n; return this; }
        |}
        |""".stripMargin
    val e   = new TirEmitter(balticporter.frontend.spoon.SpoonTir.fromSource(src))
    val out = e.emit
    assert(clue(out).contains("all$field"), out)

    // the published member row is keyed on `owner#name` with JAVA's name — never `#all$field` —
    // and the emitted name arrives beside it as `name=`.
    val keys = e.emittedShapes.members.keySet
    assert(clue(keys).contains("demo.Builder#all"), keys.mkString(", "))
    assert(!keys.exists(_.contains("all$field")), keys.mkString(", "))
    assertEquals(e.emittedShapes.members.get("demo.Builder#all").map(_.name), Some("all$field"))
  }

  test("the other cross-placement pair is clean too: a STATIC field beside an INSTANCE method") {
    val out = emit(
      """package demo;
        |public class Holder {
        |  private static int limit;
        |  public int limit () { return limit; }
        |}
        |""".stripMargin)
    assert(!clue(out).contains("limit$field"))
  }
