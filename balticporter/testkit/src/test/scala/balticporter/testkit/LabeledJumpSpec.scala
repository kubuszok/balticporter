package balticporter.testkit

/** Java's labelled and mid-case jumps, through the real pipeline. */
class LabeledJumpSpec extends PortSuite:

  private def emit(java: String): String = port(java).out

  // ---- a label on a statement that is NOT a loop ----

  test("break to a label on an `if` leaves that `if`, not the enclosing loop") {
    // JsonReader's shape: `outer: if (…) { … } else { …; break outer; …; string(…); }`. Dropped,
    // the port ran on and emitted a string event for every unquoted bool/null/number.
    val out = emit("""
      package demo;
      public class L {
        void f(boolean name, String v) {
          outer:
          if (name) {
            a(v);
          } else {
            if (v.equals("true")) { b(v); break outer; }
            c(v);
          }
          d(v);
        }
        void a(String s) {} void b(String s) {} void c(String s) {} void d(String s) {}
      }""")
    assert(clue(out).contains("scala.util.boundary { (lbl$1: scala.util.boundary.Label[scala.Unit]) ?=>"))
    assert(out.contains("scala.util.boundary.break(())(using lbl$1)"))
    assert(!out.contains("/* break"), out)
    // the boundary closes BEFORE the statement that follows the labelled `if`
    val brk = out.indexOf("using lbl$1")
    val d   = out.indexOf("this.d(v)")
    assert(brk < d && d > 0, out)
  }

  test("a label on a bare block is a boundary around the block") {
    // TextField's `keys:`/`selection:`, GlyphLayout's `runEnded:`.
    val out = emit("""
      package demo;
      public class L {
        void f(int k) {
          outer:
          {
            if (k == 1) { g(1); break outer; }
            g(2);
          }
          g(3);
        }
        void g(int n) {}
      }""")
    assert(clue(out).contains("scala.util.boundary { (lbl$1: scala.util.boundary.Label[scala.Unit]) ?=>"))
    assert(out.contains("scala.util.boundary.break(())(using lbl$1)"))
    assert(!out.contains("/* break"), out)
  }

  test("a label nobody breaks to emits no boundary at all") {
    val out = emit("""
      package demo;
      public class L {
        void f(int k) {
          outer: { g(k); }
        }
        void g(int n) {}
      }""")
    assert(!clue(out).contains("boundary"), out)
  }

  test("a label on a switch: `break outer` leaves the switch, not just the case") {
    val out = emit("""
      package demo;
      public class L {
        void f(int k) {
          outer:
          switch (k) {
            case 1: g(1); if (k > 0) break outer; g(2); break;
            default: g(3);
          }
          g(4);
        }
        void g(int n) {}
      }""")
    assert(clue(out).contains("(lbl$"), out)
    assert(out.contains("scala.util.boundary.break(())(using lbl$"), out)
    assert(!out.contains("/* break"), out)
  }

  // ---- labelled jumps crossing nested loops ----

  test("break to an outer loop's label from inside a nested loop") {
    val out = emit("""
      package demo;
      public class L {
        void f(int n) {
          outer:
          for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
              if (j == i) break outer;
              g(j);
            }
          }
          g(-1);
        }
        void g(int n) {}
      }""")
    assert(clue(out).contains("(brk$"), out)
    assert(out.contains("scala.util.boundary.break(())(using brk$"), out)
    assert(!out.contains("/* break"), out)
  }

  test("continue to an outer loop's label targets that loop's BODY boundary") {
    val out = emit("""
      package demo;
      public class L {
        void f(int n) {
          outer:
          for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
              if (j == i) continue outer;
              g(j);
            }
            g(i);
          }
        }
        void g(int n) {}
      }""")
    assert(clue(out).contains("(cnt$"), out)
    assert(out.contains("scala.util.boundary.break(())(using cnt$"), out)
    assert(!out.contains("/* continue"), out)
  }

  test("a labelled statement inside a loop forces the LOOP's boundary to be named") {
    // The shielding rule: `boundary.break(())` with no `using` binds to the innermost `Label`, so
    // the labelled statement's boundary would swallow the loop's own unlabelled `break`.
    val out = emit("""
      package demo;
      public class L {
        void f(int n) {
          for (int i = 0; i < n; i++) {
            inner:
            {
              if (i == 1) break inner;
              g(i);
            }
            if (i == 2) break;
          }
        }
        void g(int n) {}
      }""")
    assert(clue(out).contains("(brk$"), out)
    // the loop's own unlabelled break must NAME the loop boundary, not fall into `lbl$`
    assert(out.contains("scala.util.boundary.break(())(using brk$"), out)
    assert(!out.contains("scala.util.boundary.break(())\n"), out)
  }

  // ---- a case's terminator ----

  test("a case's trailing UNLABELLED break is the terminator and is stripped") {
    val out = emit("""
      package demo;
      public class L {
        void f(int k) { switch (k) { case 1: g(1); break; case 2: g(2); break; } }
        void g(int n) {}
      }""")
    assert(clue(out).contains("case 1 =>"), out)
    assert(!out.contains("boundary"), out)  // nothing to leave early from
    assert(!out.contains("/* break"), out)
    // the terminator really was consumed, not duplicated into the next arm
    assert(!out.matches("(?s).*case 1 =>[^\n]*\\{[^}]*this\\.g\\(2\\).*"), out)
  }

  test("a case's trailing LABELLED break leaves the LOOP and is NOT stripped") {
    val out = emit("""
      package demo;
      public class L {
        void f(int n) {
          outer:
          while (n > 0) {
            switch (n) { case 1: g(1); break outer; case 2: g(2); break; }
            n--;
          }
        }
        void g(int n) {}
      }""")
    assert(clue(out).contains("scala.util.boundary.break(())(using brk$"), out)
    assert(!out.contains("/* break"), out)
  }

  // ---- an unlabelled break in the MIDDLE of a case ----

  test("a mid-case break stops the case; the duplicated fallthrough tail must not run") {
    // GlyphLayout's colour-tag arm: `case '[': if (ok) { …; break; } … ` falling through into
    // `default: continue outer`. Without a boundary the successful arm fell into the `continue`.
    val out = emit("""
      package demo;
      public class L {
        void f(int n) {
          outer:
          while (n > 0) {
            switch (n) {
              case 1:
                if (n > 0) { g(1); break; }
                g(2);
              default:
                continue outer;
            }
          }
        }
        void g(int n) {}
      }""")
    assert(clue(out).contains("scala.util.boundary { (case$"), out)
    assert(out.contains("scala.util.boundary.break(())(using case$"), out)
    assert(!out.contains("/* break"), out)
    // the `continue outer` really was duplicated into the arm — which is what the boundary guards
    assert(out.contains("using cnt$"), out)
  }

  test("a mid-case break with no fallthrough tail still bounds only its own arm") {
    val out = emit("""
      package demo;
      public class L {
        void f(int n) {
          switch (n) {
            case 1: if (n > 0) break; g(1); break;
            default: g(2);
          }
          g(3);
        }
        void g(int n) {}
      }""")
    assert(clue(out).contains("(case$"), out)
    assert(out.contains("scala.util.boundary.break(())(using case$"), out)
    assert(!out.contains("/* break"), out)
    val brk = out.indexOf("using case$")
    val g3  = out.indexOf("this.g(3)")
    assert(brk < g3 && g3 > 0, out) // g(3) is after the whole switch, not inside the boundary
  }

  // ---- the probe an operator can compile ----

  /** Every shape above in one Scala file, for a real compiler. */
  test("emitted probe is written for a real compiler") {
    val p = _root_.java.nio.file.Path
      .of(sys.props.getOrElse("balticporter.dumpProbe", s"${sys.props("user.dir")}/target/probe"), "LabeledJumpProbe.scala")
    _root_.java.nio.file.Files.createDirectories(p.getParent)
    _root_.java.nio.file.Files.writeString(p, emit(ProbeSource))
    println(s"[labeled-jump-probe] wrote ${p.toAbsolutePath}")
  }

  private val ProbeSource = """
    package demo;
    public class LabeledJumpProbe {
      java.lang.StringBuilder log = new java.lang.StringBuilder();
      void g(int n) { log.append(n); }

      void labelOnIf(boolean name, String v) {
        outer:
        if (name) { g(1); } else { if (v.equals("t")) { g(2); break outer; } g(3); }
        g(4);
      }

      void labelOnBlock(int k) {
        outer: { if (k == 1) { g(1); break outer; } g(2); }
        g(3);
      }

      void labelOnSwitch(int k) {
        outer:
        switch (k) { case 1: g(1); if (k > 0) break outer; g(2); break; default: g(3); }
        g(4);
      }

      void nestedLoops(int n) {
        outer:
        for (int i = 0; i < n; i++) {
          for (int j = 0; j < n; j++) { if (j == i) break outer; g(j); }
        }
        g(-1);
      }

      void labelledContinue(int n) {
        outer:
        for (int i = 0; i < n; i++) {
          for (int j = 0; j < n; j++) { if (j == i) continue outer; g(j); }
          g(i);
        }
      }

      void shielded(int n) {
        for (int i = 0; i < n; i++) {
          inner: { if (i == 1) break inner; g(i); }
          if (i == 2) break;
        }
      }

      void midCase(int n) {
        outer:
        while (n > 0) {
          switch (n) {
            case 1: if (n > 0) { g(1); break; } g(2);
            default: continue outer;
          }
        }
      }

      void caseTerminators(int n) {
        int i = n;
        outer:
        while (i > 0) {
          switch (i) { case 1: g(1); break outer; case 2: g(2); break; }
          i--;
        }
      }
    }"""
