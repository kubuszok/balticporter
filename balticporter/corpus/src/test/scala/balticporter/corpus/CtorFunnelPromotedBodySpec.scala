package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{OmissionCheck, Pipeline}

/** A PROMOTED constructor's body becomes the class body, and a scala class body runs on EVERY
  * construction path. Java's did not.
  *
  * `CtorFunnel` nominates one java constructor as scala's primary; every other constructor is a
  * secondary whose first statement must be `this(...)`, so the primary's body runs before each of
  * them. Two java constructors that do not delegate to each other ran DISJOINT bodies, and that
  * separation has no single-primary encoding. `ENGINE-LIMITS.md` C6's probe is the shape:
  *
  * {{{
  * Base() { this.n = Audit.bump(); }   Base(int n) { this.n = n; }
  * }}}
  *
  * `new Base(5)` bumps in the port and does not in java — measured by running both:
  * java printed `n=5 bumps=0`, the emitted scala `n=5 bumps=1`.
  *
  * REFUSING the promotion is not available and is not a matter of taste: with every escaping
  * promotion dropped to `Plan.none`, libGDX measured **0 -> 41 compile errors**, all `E120
  * Conflicting definitions` — the refused class emits `def this()` beside scala's implicit nilary
  * primary, which is the clash the promotion exists to prevent. So the emission stands and the
  * divergence is COUNTED (CLAUDE.md §4.4; `ENGINE-LIMITS.md` M6).
  *
  * The half that matters as much as the report is the SILENCE: a constructor that delegates
  * `this()` — at any arity — did run the promoted body in java too, and reporting it would bury
  * the real ones. Both directions are asserted here, so a fix in either alone fails.
  */
class CtorFunnelPromotedBodySpec extends munit.FunSuite:

  private val src =
    """package demo;
      |public class Audit {
      |  static int bumps = 0;
      |  static int bump() { bumps++; return -1; }
      |}
      |public class Parent { Parent(int a) {} Parent(int a, int b) {} }
      |/** C6's probe, ON THE WALL — two roots, neither delegating to the other, reaching two
      |  * DIFFERENT parent constructors. The wall is what keeps a PROMOTION here at all: where every
      |  * root reaches ONE parent constructor the funnel synthesises a primary instead, promotes
      |  * nothing, and there is no body to escape. That is the whole of A2, and it is why this
      |  * fixture had to grow a parent — measured, when it did not have one, as this spec failing
      |  * because the divergence it asserts had been REPAIRED. */
      |public class Base extends Parent {
      |  int n;
      |  Base() { super(0); this.n = Audit.bump(); }
      |  Base(int n) { super(n, 1); this.n = n; }
      |}
      |/** the paramful constructor DELEGATES with an explicit `this()`, so java ran the nilary body
      |  * on this path as well: nothing escapes and nothing may be reported. */
      |public class Delegating {
      |  int n;
      |  Delegating() { this.n = Audit.bump(); }
      |  Delegating(int n) { this(); this.n = n; }
      |}
      |/** a UNIQUE root: every other constructor reaches it through `this(args)`, so its body ran on
      |  * every java path too. */
      |public class Unique {
      |  int n;
      |  Unique(int n) { this.n = Audit.bump() + n; }
      |  Unique(String s) { this(s.length()); }
      |}
      |/** an EMPTY promoted body adds nothing to any path, whatever the other roots do. */
      |public class Empty {
      |  int n;
      |  Empty() { }
      |  Empty(int n) { this.n = n; }
      |}
      |""".stripMargin

  private val program  = Pipeline.run(SpoonTir.fromSource(src), Nil)
  private val out      = new TirEmitter(program).emit
  private val findings = OmissionCheck.promotedBodyOnEveryPath(program)

  test("the promoted nilary body really is in the class body, where every path runs it") {
    // this is the emission the finding describes; asserting it here is what stops the check from
    // drifting into a claim about code the emitter no longer produces
    assert(clue(out).contains("class Base private[demo] () extends demo.Parent(0) {"))
    assert(out.contains("this.n = demo.Audit.bump()"))
    assert(out.contains("def this(n: scala.Int) = {"))
  }

  test("exactly the escaping constructor is reported") {
    assertEquals(findings.map(f => (f.owner, f.detail)),
                 List(("demo.Base",
                       "1 statement(s) of the promoted constructor also run here; java ran them only on its own path")))
  }

  test("a constructor that delegates `this()` is NOT reported — java ran the body there too") {
    assertEquals(findings.filter(_.owner == "demo.Delegating"), Nil)
  }

  test("a unique root reached by every other constructor is NOT reported") {
    assertEquals(findings.filter(_.owner == "demo.Unique"), Nil)
  }

  test("an empty promoted body is NOT reported — it adds nothing to the other path") {
    assertEquals(findings.filter(_.owner == "demo.Empty"), Nil)
  }

  test("the whole check carries it, not just the direct call") {
    assert(OmissionCheck.check(program).exists(_.what == "promoted constructor body runs on every path"))
  }

  // A comment above the delegation must not manufacture an escape. `reachesCtor` reads the head
  // statement THROUGH its `Tree.Commented` wrapper (`headStmt`); matched raw, the wrapped
  // `this(...)` fell to the default arm and the constructor was reported as running the promoted
  // body on a path java skipped — a finding created by a comment (found at the trivia merge).
  test("a COMMENT above the `this()` delegation does not turn it into an escape") {
    val commented =
      """package demo2;
        |public class Audited {
        |  int n;
        |  Audited() { this.n = -1; }
        |  Audited(int k) {
        |    // delegate first, as every sibling does
        |    this();
        |  }
        |}
        |""".stripMargin
    val program = Pipeline.run(SpoonTir.fromSource(commented), Nil)
    assertEquals(OmissionCheck.promotedBodyOnEveryPath(program).map(_.owner), Nil)
  }
