package balticporter.testkit

/** A translated jump that leaves a translated `try` — the catch that swallows it. */
class BreakInCatchSpec extends PortSuite:

  private def emit(java: String): String = port(java).out

  /** the arm the emitter must interpose, spelled once. */
  private val Guard = "case brkThru$: scala.util.boundary.Break[?] => throw brkThru$"

  /** the second arm, owed only where the catch can receive a `ControlThrowable` (a `Throwable` catch). */
  private val SentinelGuard = "case brkThru$: scala.util.control.ControlThrowable => throw brkThru$"

  // ---- the jump crosses the catch ----

  test("`break` inside a try with `catch (Exception)` gets a re-throw arm ahead of it") {
    // a loop over attempts, each one guarded, success leaving the loop. Without the guard the
    // `break` is caught by the handler meant to ignore one failed attempt, and the loop tries every
    // remaining attempt instead of stopping.
    val out = emit(
      """
      package demo;
      public class L {
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
    )
    assert(clue(out).contains(Guard))
    // …and it is the FIRST arm: java's own arm must not see the jump.
    val g = out.indexOf(Guard)
    val j = out.indexOf("java.lang.Exception")
    assert(g > 0 && j > g, out)
    assert(out.contains("scala.util.boundary.break(())"), out)
  }

  test("`continue` inside a try with a broad catch gets the same arm") {
    val out = emit(
      """
      package demo;
      public class L {
        void f(String[] xs) {
          for (String x : xs) {
            try {
              if (x == null) continue;
              g(x);
            } catch (RuntimeException e) { h(e); }
          }
        }
        void g(String s) {} void h(Object o) {}
      }"""
    )
    assert(clue(out).contains(Guard))
    val g = out.indexOf(Guard)
    val j = out.indexOf("case e: java.lang.RuntimeException")
    assert(g > 0 && j > g, out)
  }

  test("`catch (Throwable)` is caught by the same rule") {
    val out = emit(
      """
      package demo;
      public class L {
        void f(int n) {
          while (n > 0) {
            try { if (n == 1) break; g(n); } catch (Throwable t) { h(t); }
            n--;
          }
        }
        void g(int n) {} void h(Object o) {}
      }"""
    )
    assert(clue(out).contains(Guard))
  }

  test("a LABELLED break crossing a nested try's broad catch is guarded at that try") {
    // `break outer` from an inner loop, with the inner body guarded. The label makes the jump cross
    // two constructs by definition; the catch it crosses is the inner one.
    val out = emit(
      """
      package demo;
      public class L {
        void f(int[][] rows) {
          outer:
          for (int[] row : rows) {
            for (int v : row) {
              try {
                if (v < 0) break outer;
                g(v);
              } catch (Exception e) { h(e); }
            }
          }
        }
        void g(int n) {} void h(Object o) {}
      }"""
    )
    assert(clue(out).contains(Guard))
    assert(out.contains("throw brk$1"), out)
    // `catch (Exception)` cannot receive a ControlThrowable: no sentinel arm, the guard is exact
    assert(!out.contains(SentinelGuard), out)
  }

  test("a LABELLED break crossing `catch (Throwable)` rethrows the SENTINEL ahead of java's arm") {
    // the named loop boundary is a ControlThrowable; `NonFatal` spares it, `case t: Throwable` does not
    val out = emit(
      """
      package demo;
      public class L {
        void f(int[][] rows) {
          outer:
          for (int[] row : rows) {
            for (int v : row) {
              try {
                if (v < 0) break outer;
                g(v);
              } catch (Throwable t) { h(t); }
            }
          }
        }
        void g(int n) {} void h(Object o) {}
      }"""
    )
    assert(clue(out).contains(Guard))
    assert(out.contains(SentinelGuard), out)
    val s = out.indexOf(SentinelGuard)
    val j = out.indexOf("case t: java.lang.Throwable")
    assert(s > 0 && j > s, out)
  }

  test("a jump crossing TWO nested broad catches is guarded at both") {
    val out = emit(
      """
      package demo;
      public class L {
        void f(int n) {
          while (n > 0) {
            try {
              try { if (n == 1) break; g(n); } catch (RuntimeException e) { h(e); }
            } catch (Exception e2) { h(e2); }
            n--;
          }
        }
        void g(int n) {} void h(Object o) {}
      }"""
    )
    val first = out.indexOf(Guard)
    assert(clue(first) > 0, out)
    assert(out.indexOf(Guard, first + 1) > first, out)
  }

  // ---- what must NOT be touched ----

  test("a NARROW catch cannot match a Break and is left exactly as it was") {
    val out = emit(
      """
      package demo;
      public class L {
        void f(int n) {
          while (n > 0) {
            try { if (n == 1) break; g(n); } catch (IllegalStateException e) { h(e); }
            n--;
          }
        }
        void g(int n) {} void h(Object o) {}
      }"""
    )
    assert(!clue(out).contains("Break["), out)
    assert(out.contains("case e: java.lang.IllegalStateException"), out)
  }

  test("a jump in the CATCH ARM is not under that try's catch, so nothing is added") {
    val out = emit(
      """
      package demo;
      public class L {
        void f(int n) {
          while (n > 0) {
            try { g(n); } catch (Exception e) { break; }
            n--;
          }
        }
        void g(int n) {}
      }"""
    )
    assert(!clue(out).contains("Break["), out)
    assert(out.contains("scala.util.boundary.break(())"), out)
  }

  test("try/FINALLY with a jump crossing it is untouched — a finally is not a handler") {
    // Both languages run the finalizer and let the jump through; there is nothing to repair.
    val out = emit(
      """
      package demo;
      public class L {
        void f(int n) {
          while (n > 0) {
            try { if (n == 1) break; g(n); } finally { h(n); }
            n--;
          }
        }
        void g(int n) {} void h(int n) {}
      }"""
    )
    assert(!clue(out).contains("Break["), out)
    assert(out.contains("finally"), out)
  }

  test("a broad catch with NO jump under it is untouched") {
    val out = emit(
      """
      package demo;
      public class L {
        void f(int n) {
          while (n > 0) {
            try { g(n); } catch (Exception e) { h(e); }
            n--;
          }
        }
        void g(int n) {} void h(Object o) {}
      }"""
    )
    assert(!clue(out).contains("Break["), out)
  }

  test("a broad catch outside any loop is untouched — there is no boundary to cross") {
    val out = emit(
      """
      package demo;
      public class L {
        void f(int n) {
          try { g(n); } catch (Exception e) { h(e); }
        }
        void g(int n) {} void h(Object o) {}
      }"""
    )
    assert(!clue(out).contains("Break["), out)
  }

  // ---- the behaviour, executed by this JVM ----

  test("BEHAVIOUR: an unguarded catch really does swallow the jump") {
    var attempts = 0
    val r        = scala.util.boundary { (brk: scala.util.boundary.Label[String]) ?=>
      for p <- List("bad", "good", "also-good") do
        try
          attempts += 1
          if p.startsWith("good") then scala.util.boundary.break(p)(using brk)
        catch case _: java.lang.Exception => () // java could never catch the jump; scala just did
      "none"
    }
    assertEquals(
      r,
      "none",
      "the naive shape is supposed to lose the break — a boundary.Break extends RuntimeException, so a broad catch swallows it silently"
    )
    assertEquals(attempts, 3, "…and the loop ran to the end")
  }

  test("BEHAVIOUR: the re-throw arm restores java's meaning exactly") {
    var attempts = 0
    val r        = scala.util.boundary { (brk: scala.util.boundary.Label[String]) ?=>
      for p <- List("bad", "good", "also-good") do
        try
          attempts += 1
          if p.startsWith("good") then scala.util.boundary.break(p)(using brk)
        catch
          case brkThru: scala.util.boundary.Break[?] => throw brkThru
          case _:       java.lang.Exception          => ()
      "none"
    }
    assertEquals(r, "good")
    assertEquals(attempts, 2, "the loop stopped at the first match, as java's `break` does")
  }

  test("BEHAVIOUR: the arm re-throws a FOREIGN break too, and still handles real exceptions") {
    // The arm is unconditional on the label: an inner boundary's own `Break` is re-thrown here and
    // caught by whichever boundary owns it, which is what `boundary.apply` does anyway.
    var handled = 0
    val r       = scala.util.boundary { (outer: scala.util.boundary.Label[String]) ?=>
      try
        scala.util.boundary { (inner: scala.util.boundary.Label[String]) ?=>
          throw new java.lang.IllegalStateException("real")
        }
      catch
        case brkThru: scala.util.boundary.Break[?] => throw brkThru
        case _:       java.lang.Exception          => handled += 1
      "done"
    }
    assertEquals(r, "done")
    assertEquals(handled, 1, "a real exception must still reach the java handler")
  }
