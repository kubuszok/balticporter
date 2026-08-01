package balticporter.testkit

import balticporter.tir.*

/** `Tree.Block.trailing` — the comment java wrote at the END of a body.
  *
  * The frontend folds a comment-statement onto the statement that FOLLOWS it; with nothing after
  * it the comment was CLAIMED and then discarded, which put it beyond every coarser harvest too.
  * That single line was the largest category of comment the port lost, and none of it is visible
  * to a compile: the emitted Scala is valid with every one of these gone.
  */
class TrailingTriviaSpec extends munit.FunSuite:

  private def occurrences(hay: String, needle: String): Int =
    var n = 0; var i = hay.indexOf(needle)
    while i >= 0 do { n += 1; i = hay.indexOf(needle, i + 1) }
    n

  private def out(java: String): String = PortFixture.port(java).out

  test("a body whose whole content is a comment keeps it, inside the braces") {
    val o = out(
      """package demo;
        |public class Empty {
        |    public void update(float delta) {
        |        // Do nothing by default.
        |    }
        |}
        |""".stripMargin)
    assert(o.contains("// Do nothing by default."), o)
    // inside the method, not hoisted above it — the whole point of a slot rather than a fallback
    assert(o.indexOf("def update") < o.indexOf("Do nothing by default."), o)
    assertEquals(occurrences(o, "Do nothing by default."), 1, o)
  }

  test("commented-out code as the LAST line of a body survives") {
    val o = out(
      """package demo;
        |public class Tail {
        |    public void run() {
        |        step();
        |        //            setTransform(transform);
        |    }
        |    private void step() {}
        |}
        |""".stripMargin)
    assert(o.contains("setTransform(transform);"), o)
    val c = o.indexOf("setTransform(transform);")
    assert(c > o.indexOf("step()"), o)
    // …and it is a `//` line, so it cannot re-enter the code
    val line = o.linesIterator.find(_.contains("setTransform(transform);")).getOrElse("")
    assert(line.trim.startsWith("//"), line)
  }

  test("a comment between a case's `return` and the next label survives the switch lowering") {
    val o = out(
      """package demo;
        |public class Sw {
        |    public String name(int k) {
        |        switch (k) {
        |        case 1:
        |            return "one";
        |            // NOT_HANDLED unhandled by this switch
        |        default:
        |            // key name not found
        |            return null;
        |        }
        |    }
        |}
        |""".stripMargin)
    assert(o.contains("NOT_HANDLED unhandled by this switch"), o)
    // the sibling that always survived, for contrast: it PRECEDES a statement and folds onto it
    assert(o.contains("key name not found"), o)
    assertEquals(occurrences(o, "NOT_HANDLED unhandled by this switch"), 1, o)
  }

  test("a comment above a stripped case-terminator `break` survives") {
    // the shape the lowering MANUFACTURES: with the `break` deleted as a case terminator, the
    // comment written above it becomes the arm's last statement.
    val o = out(
      """package demo;
        |public class Brk {
        |    public int f(int k) {
        |        int r = 0;
        |        switch (k) {
        |        case 1:
        |            r = 1;
        |            // nothing else to do here
        |            break;
        |        default:
        |            r = 2;
        |        }
        |        return r;
        |    }
        |}
        |""".stripMargin)
    assert(o.contains("nothing else to do here"), o)
    assertEquals(occurrences(o, "nothing else to do here"), 1, o)
  }

  /** the A2 interaction: the funnel rewrites every multi-constructor class, so this is the
    * population `trailing` has to ride through. A SECONDARY keeps its braces and `ctorBody`
    * renders them from `stmtsOf`'s statement LIST rather than from the body block — so the slot
    * reaches it only because that rendering asks for it. The constructor the funnel CONSUMES has
    * no braces left at all; that one is the recovery backstop's, not this mechanism's. */
  private val multiCtor =
    """package demo;
      |public class Multi {
      |    private int w;
      |    private int h;
      |    public Multi() {
      |        this(1, 2);
      |        // defaulted both
      |    }
      |    public Multi(int w) {
      |        this(w, 2);
      |        // defaulted the height
      |    }
      |    public Multi(int w, int h) {
      |        this.w = w;
      |        this.h = h;
      |        // both given
      |    }
      |}
      |""".stripMargin

  test("A2 interaction: every SECONDARY constructor keeps its body's trailing comment") {
    val o = out(multiCtor)
    List("defaulted both", "defaulted the height")
      .foreach(c => assertEquals(occurrences(o, c), 1, s"'$c' in:\n$o"))
    // …and each of them PLACED, not recovered: the backstop is the completeness half and these
    // three are the attachment channel's, which is what places them where java wrote them.
    assertEquals(balticporter.tir.TriviaMark.scan(o).count(_.line == 8), 0, o)
    assertEquals(balticporter.tir.TriviaMark.scan(o).count(_.line == 12), 0, o)
  }

  test("A2 interaction: the CONSUMED constructor's is recovered — `RecoveredTriviaSpec` for how") {
    assertEquals(occurrences(out(multiCtor), "both given"), 1, out(multiCtor))
  }


  test("a nesting block comment at a block's tail still renders line-by-line as `//`") {
    val o = out(
      """package demo;
        |public class Nest {
        |    public void f() {
        |        g();
        |        /* see the /* marker */
        |    }
        |    private void g() {}
        |}
        |""".stripMargin)
    assert(o.contains("see the"), o)
    val line = o.linesIterator.find(_.contains("see the /* marker")).getOrElse("")
    assert(line.trim.startsWith("//"), s"[$line] in:\n$o")
  }

  test("the trailing slot is on the TREE — canonical elides it, digest carries it") {
    val java =
      """package demo;
        |public class D { public void f() { g(); // tail
        |} private void g() {} }
        |""".stripMargin
    val p         = PortFixture.parse(java)
    given Program = p
    val unit      = p.units.head
    assert(!TirPrinter.canonical(unit).contains("tail"), TirPrinter.canonical(unit))
    assert(TirPrinter.render(unit, TirPrinter.Style.identity).contains("tail"))
    assertNotEquals(TirPrinter.digest(unit), TirPrinter.sha256(TirPrinter.canonical(unit)))
  }

  test("a body with no trailing comment is byte-identical to the pre-slot rendering") {
    val o = out(
      """package demo;
        |public class Plain { public int f(int n) { int t = n; return t; } }
        |""".stripMargin)
    assert(o.contains("val t: scala.Int = n"), o)
    assert(!o.contains("//"), o)
  }
