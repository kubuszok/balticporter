package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.tir.{Tree, TryResourceCheck}
import balticporter.tir.TryResourceCheck.Issue

/** Java's try-with-resources (JLS 14.20.3) — the lowering, and the lane that can see it go missing.
  *
  * ==What this is a regression test for==
  * `Tree.Try.resources` was populated by the frontend and printed by `TirPrinter`, and
  * `TirEmitter.tryStr` rendered the resources into a local it then never interpolated. The
  * resource `val`s, every `close()`, the ordering and the suppression were dropped from the output
  * whole. A resource referenced inside its own body failed to compile, which is loud; a resource
  * opened for its side effect alone compiled cleanly with nothing acquired and nothing released.
  *
  * ==Two halves, and neither is the other's evidence==
  * The tests below assert the SHAPE the emitter writes. The tests in `TryResourceBehaviourSpec`
  * assert what that shape DOES — close order, suppression, close-on-jump — by running it. Neither
  * is enough alone: a shape assertion cannot say the semantics are java's, and a behaviour
  * assertion over hand-written Scala cannot say the emitter writes it.
  */
class TryResourceSpec extends PortSuite:

  private val oneResource = """
    package demo;
    import java.io.Closeable;
    public class C {
      int f(Closeable c) throws Exception {
        try (Closeable r = c) {
          return 1;
        }
      }
    }"""

  private val twoResources = """
    package demo;
    import java.io.Closeable;
    public class C {
      void f(Closeable a, Closeable b) throws Exception {
        try (Closeable first = a; Closeable second = b) {
          use(first);
        }
      }
      void use(Closeable c) { }
    }"""

  test("a resource is BOUND and CLOSED — the binding, the finally and the close all reach the output") {
    val p = port(oneResource)
    assertEmits(p, "val r: java.io.Closeable = c")
    assertEmits(p, "r.close()")
    assert(clue(p.out).contains("finally if r != null then"), p.out)
    // the body is still inside, and the `return` is still a `return` — the reason this lowering is
    // statements and not `Using(r) { r => … }` (a jump cannot leave a lambda unchanged).
    assertEmits(p, "return 1")
  }

  test("the body's exception is PRIMARY and a failing close() is SUPPRESSED, never a replacement") {
    val p = port(oneResource)
    assertEmits(p, "var primary$1: java.lang.Throwable = null")
    assertEmits(p, "primary$1.addSuppressed")
    // …and the body's own throwable is re-thrown rather than swallowed, which is also why this
    // catch-all needs no §4.4 BreakGuard beside it: a `boundary.Break` crossing it is re-thrown.
    assert(clue(p.out).contains("throw thrown$1"), p.out)
  }

  test("a JUMP takes its own arm AHEAD of the recorder — so `primary` stays null") {
    // Java's `break` carries no exception object, so JLS 14.20.3.1 has nothing for a failing
    // `close()` to be suppressed into: it replaces the jump and propagates. Scala's break IS an
    // exception, so recorded as `primary` it routed the `finally` to the SUPPRESSING arm — and
    // `boundary.Break` disables suppression, making `addSuppressed` a no-op that dropped the close
    // exception entirely. The arm below is what keeps `primary` null on a jump.
    // `TryResourceBehaviourSpec` runs the difference; this asserts the shape that produces it.
    val p   = port(oneResource)
    val out = p.out
    val jump = out.indexOf("scala.util.boundary.Break[?] => throw brkThru$")
    val rec  = out.indexOf("primary$1 = thrown$1")
    assert(clue(jump) >= 0, out)
    assert(clue(jump) < clue(rec), "the Break arm must precede the recorder, or it never matches")
  }

  test("two resources close in REVERSE declaration order, with distinct binders") {
    val p = port(twoResources)
    val out = p.out
    // the SECOND resource is the inner block, so its `finally` runs first
    val firstClose  = out.indexOf("first.close()")
    val secondClose = out.indexOf("second.close()")
    assert(firstClose > 0 && secondClose > 0, out)
    assert(secondClose < firstClose, s"second.close() must be nested INSIDE first's block\n$out")
    // …and nothing shadows: two nestings, two `primary`s
    assertEmits(p, "primary$1")
    assertEmits(p, "primary$2")
  }

  test("a plain `try` is untouched — no resource machinery for a statement that has none") {
    val p = port("""
      package demo;
      public class C {
        int f() { try { return 1; } catch (Exception e) { return 2; } }
      }""")
    assertNotEmits(p, "addSuppressed")
    assertNotEmits(p, "primary$")
  }

  test("resources close BEFORE this try's own catch — JLS 14.20.3.2's nesting") {
    val p = port("""
      package demo;
      import java.io.Closeable;
      public class C {
        int f(Closeable c) {
          try (Closeable r = c) { return 1; }
          catch (Exception e) { return 2; }
        }
      }""")
    val out = p.out
    // the java `catch` is OUTSIDE the resource block: `r.close()` comes before it in the text
    val close = out.indexOf("r.close()")
    val arm   = out.indexOf("case e: java.lang.Exception")
    assert(close > 0 && arm > 0, out)
    assert(close < arm, s"the resource must close before this try's own catch runs\n$out")
  }

  // ---- the CHECK lane ------------------------------------------------------------------------
  //
  // The negative test is the load-bearing one, exactly as in `BreakCatchCheckSpec`: the
  // resource-carrying `try`s are found from the TREES here, and `lowered` is the only thing the
  // emitter contributes — so `_ => false` reproduces the un-repaired engine on the same trees.

  test("un-repaired, the check REPORTS the drop — with an owner, an origin and a §1 classification") {
    val p  = port(oneResource)
    val fs = TryResourceCheck.check(p.after, p.after.units, (_: Tree.Try) => false)
    assertEquals(clue(fs).size, 1)
    assertEquals(fs.head.issue, Issue.UnloweredResource)
    assertEquals(fs.head.resources, List("r"))
    assertEquals(fs.head.owner, "demo.C#f")
    assert(fs.head.origin.line > 0, fs.head.render)
    assert(clue(Issue.classification(Issue.UnloweredResource)).contains("§1(a)"))
    assert(clue(TryResourceCheck.summary(fs)).contains("UnloweredResource"))
    assertEquals(fs.head.report.check, TryResourceCheck.Name)
    assert(!fs.head.report.path.startsWith("/"), fs.head.report.path)
  }

  test("with what the emitter actually lowered, the same program reads 0") {
    val p = port(oneResource)
    p.out // the lowering set is a record of what was EMITTED, so emission has to have happened
    assertEquals(clue(p.emitter.resourceLoweringCount), 1)
    assertEquals(TryResourceCheck.check(p.after, p.after.units, p.emitter.resourceLowerings), Nil)
  }

  test("a `try` with NO resources is not a finding — 0 even un-repaired") {
    val p = port("""
      package demo;
      public class C {
        int f() {
          try {
            return 1;
          } finally {
          }
        }
      }""")
    assertEquals(TryResourceCheck.check(p.after, p.after.units, (_: Tree.Try) => false), Nil)
  }

  test("the lowering set is keyed by TOKEN — a lowered try may not vouch for its sibling") {
    val p = port("""
      package demo;
      import java.io.Closeable;
      public class C {
        void f(Closeable a, Closeable b) throws Exception {
          try (Closeable x = a) { use(x); }
          try (Closeable y = b) { use(y); }
        }
        void use(Closeable c) { }
      }""")
    val tries = p.after.units.flatMap(collectTries(_)(using p.after))
    assertEquals(clue(tries).size, 2)
    // one lowered by token: the OTHER is still reported
    val byToken = TryResourceCheck.check(p.after, p.after.units, t => t.id == tries.head.id)
    assertEquals(clue(byToken).size, 1)
    assertEquals(byToken.head.resources, List("y"))
  }

  private def collectTries(u: Tree.ClassDef)(using balticporter.tir.Program): List[Tree.Try] =
    val out = collection.mutable.ListBuffer.empty[Tree.Try]
    balticporter.tir.StandardTraversal.scanClassDef(u, ()) { (_, t) =>
      t match
        case tr: Tree.Try => out += tr
        case _            => ()
      ()
    }
    out.toList
