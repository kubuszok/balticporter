package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.tir.{BreakCatchCheck, Origin}
import balticporter.tir.BreakCatchCheck.Issue

/** The `break-catch` lane: can it report, and does it read 0 once the emitter guards?
  *
  * The negative test is the load-bearing one. A check whose crossings were read back out of the
  * emitter's own answer would be 0 by construction and could never fail — so the crossings are
  * found here, from the trees, and `guarded` is the ONLY thing the emitter contributes. Passing
  * `_ => false` is therefore the un-repaired engine exactly: same walk, same trees, nothing
  * guarded. If that reports nothing, the lane is decoration (CLAUDE.md §3, "a check reporting zero
  * is only as good as its coverage").
  */
class BreakCatchCheckSpec extends PortSuite:

  private val crossing = """
    package demo;
    public class C {
      String f(String[] pats, String s) {
        String r = null;
        for (String p : pats) {
          try {
            r = parse(s, p);
            break;
          } catch (Exception e) { }
        }
        return r;
      }
      String parse(String s, String p) { return s; }
    }"""

  test("un-repaired, the check REPORTS the crossing — with an origin and a §1 classification") {
    val p  = port(crossing)
    val fs = BreakCatchCheck.check(p.after, p.after.units, (_: Origin) => false)
    assertEquals(clue(fs).size, 1)
    assertEquals(fs.head.issue, Issue.UnguardedJump)
    assertEquals(fs.head.jump, "break")
    assertEquals(fs.head.caught, "java.lang.Exception")
    assertEquals(fs.head.owner, "demo.C#f")
    assert(fs.head.origin.line > 0, fs.head.render)
    assert(clue(Issue.classification(Issue.UnguardedJump)).contains("§1(a)"))
    assert(clue(BreakCatchCheck.summary(fs)).contains("UnguardedJump"))
    // and the row a run would write carries the lane name and a relative path
    assertEquals(fs.head.report.check, BreakCatchCheck.Name)
    assert(!fs.head.report.path.startsWith("/"), fs.head.report.path)
  }

  test("with what the emitter actually guarded, the same program reads 0") {
    val p = port(crossing)
    p.out // the guard set is a record of what was EMITTED, so emission has to have happened
    assert(clue(p.emitter.breakGuards).nonEmpty)
    assertEquals(BreakCatchCheck.check(p.after, p.after.units, p.emitter.breakGuards), Nil)
  }

  test("a narrow catch is not a crossing at all — 0 even un-repaired") {
    val p = port("""
      package demo;
      public class C {
        void f(int n) {
          while (n > 0) {
            try { if (n == 1) break; g(n); } catch (IllegalStateException e) { h(e); }
            n--;
          }
        }
        void g(int n) {} void h(Object o) {}
      }""")
    assertEquals(BreakCatchCheck.check(p.after, p.after.units, (_: Origin) => false), Nil)
  }

  test("a jump the emitter leaves as a RESIDUE is not reported here — it has no boundary to cross") {
    // No enclosing loop or switch, so the `break` never becomes a `boundary.break`: it is the
    // break-residue measure's finding, and counting it twice would make two numbers of one defect.
    val p = port("""
      package demo;
      public class C {
        void f(int n) {
          try { g(n); } catch (Exception e) { h(e); }
        }
        void g(int n) {} void h(Object o) {}
      }""")
    assertEquals(BreakCatchCheck.check(p.after, p.after.units, (_: Origin) => false), Nil)
  }

  test("a jump in the CATCH ARM is not under that try's handler") {
    val p = port("""
      package demo;
      public class C {
        void f(int n) {
          while (n > 0) {
            try { g(n); } catch (Exception e) { break; }
            n--;
          }
        }
        void g(int n) {}
      }""")
    assertEquals(BreakCatchCheck.check(p.after, p.after.units, (_: Origin) => false), Nil)
  }

  test("every jump kind that can cross is reported, and a labelled one names its label") {
    val p = port("""
      package demo;
      public class C {
        void f(int[][] rows) {
          outer:
          for (int[] row : rows) {
            for (int v : row) {
              try {
                if (v < 0) break outer;
                if (v == 0) continue;
                if (v == 1) break;
                g(v);
              } catch (Throwable t) { h(t); }
            }
          }
        }
        void g(int n) {} void h(Object o) {}
      }""")
    val fs = BreakCatchCheck.check(p.after, p.after.units, (_: Origin) => false)
    assertEquals(clue(fs.map(_.jump).sorted), List("break", "break outer", "continue"))
    // …and the emitter guards that try once, which covers all three
    p.out
    assertEquals(BreakCatchCheck.check(p.after, p.after.units, p.emitter.breakGuards), Nil)
  }
