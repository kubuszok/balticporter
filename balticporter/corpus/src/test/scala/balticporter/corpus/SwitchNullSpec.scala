package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.tir.{SwitchNullCheck, Tree}
import balticporter.tir.SwitchNullCheck.Issue

/** A java `switch` on a REFERENCE type NPEs on a null selector (JLS 14.11); a `match` falls out.
  *
  * This is the fall-out arm's own defect read at the other selector value. Both come from the same
  * mechanism: without the fall-out arm an ordinary value throws `MatchError` where java falls out,
  * and without this repair a null value falls out where java throws. Neither moves a compile-error
  * count — the emitted `match` is valid Scala with or without the arm.
  */
class SwitchNullSpec extends PortSuite:

  private val onString = """
    package demo;
    public class C {
      int f(String s) {
        switch (s) {
          case "a": return 1;
          case "b": return 2;
        }
        return 0;
      }
    }"""

  test("a STRING selector gets java's implicit NPE, ahead of the java arms") {
    val p = port(onString)
    assertEmits(p, "case null => throw new java.lang.NullPointerException")
    // …and it precedes the literal arms, which cannot match null anyway but would otherwise let
    // the fall-out arm run first once one is `case _`.
    val guard = p.out.indexOf("case null =>")
    val first = p.out.indexOf("case \"a\"")
    assert(guard > 0 && first > 0, p.out)
    assert(guard < first, s"the null arm must come first\n${p.out}")
  }

  test("an ENUM selector too — JLS 14.11.2 is the same rule") {
    val p = port("""
      package demo;
      public class C {
        enum E { A, B }
        int f(E e) {
          switch (e) {
            case A: return 1;
          }
          return 0;
        }
      }""")
    assertEmits(p, "case null => throw new java.lang.NullPointerException")
  }

  test("a BOXED selector too — the unboxing NPE is the same general text") {
    val p = port("""
      package demo;
      public class C {
        int f(Integer n) {
          switch (n) {
            case 1: return 1;
          }
          return 0;
        }
      }""")
    assertEmits(p, "case null => throw new java.lang.NullPointerException")
  }

  test("a PRIMITIVE selector gets nothing — an `int` switch can never see null") {
    val p = port("""
      package demo;
      public class C {
        int f(char c) {
          switch (c) {
            case 'a': return 1;
          }
          return 0;
        }
      }""")
    assertNotEmits(p, "case null =>")
  }

  test("…and neither does a `long` or an `int`, which is most of the corpus's switches") {
    val p = port("""
      package demo;
      public class C {
        int f(int n, long m) {
          switch (n) { case 1: return 1; }
          switch (m) { case 2L: return 2; }
          return 0;
        }
      }""")
    assertNotEmits(p, "case null =>")
  }

  test("java that already writes `case null` keeps its own behaviour — no synthetic throw") {
    // SE21's pattern-switch opt-out (JLS 14.11.1). The label is java deliberately handling null,
    // and a throw ahead of it would invert exactly what it exists to state.
    val p = port("""
      package demo;
      public class C {
        int f(String s) {
          switch (s) {
            case null: return -1;
            case "a": return 1;
            default: return 0;
          }
        }
      }""")
    assertNotEmits(p, "throw new java.lang.NullPointerException")
    assertEmits(p, "case null =>")
  }

  // ---- the CHECK lane ------------------------------------------------------------------------

  test("un-repaired, the check REPORTS the fall-out — owner, origin and a §1 classification") {
    val p  = port(onString)
    val fs = SwitchNullCheck.check(p.after, p.after.units, (_: Tree.Match) => false)
    assertEquals(clue(fs).size, 1)
    assertEquals(fs.head.issue, Issue.NullFallsOut)
    assertEquals(fs.head.selector, "java.lang.String")
    assertEquals(fs.head.owner, "demo.C#f")
    assert(fs.head.origin.line > 0, fs.head.render)
    assert(clue(Issue.classification(Issue.NullFallsOut)).contains("§1(a)"))
    assert(clue(SwitchNullCheck.summary(fs)).contains("NullFallsOut"))
    assertEquals(fs.head.report.check, SwitchNullCheck.Name)
    assert(!fs.head.report.path.startsWith("/"), fs.head.report.path)
  }

  test("with what the emitter actually guarded, the same program reads 0") {
    val p = port(onString)
    p.out
    assertEquals(clue(p.emitter.switchNullGuardCount), 1)
    assertEquals(SwitchNullCheck.check(p.after, p.after.units, p.emitter.switchNullGuards), Nil)
  }

  test("a primitive switch is not a finding — 0 even un-repaired") {
    val p = port("""
      package demo;
      public class C {
        int f(int n) {
          switch (n) {
            case 1: return 1;
          }
          return 0;
        }
      }""")
    assertEquals(SwitchNullCheck.check(p.after, p.after.units, (_: Tree.Match) => false), Nil)
  }

  test("the guard set is keyed by TOKEN — a guarded switch may not vouch for its sibling") {
    val p = port("""
      package demo;
      public class C {
        int f(String a, String b) {
          switch (a) { case "x": return 1; }
          switch (b) { case "y": return 2; }
          return 0;
        }
      }""")
    val ms = p.after.units.flatMap(collectMatches(_)(using p.after))
    assertEquals(clue(ms).size, 2)
    val byToken = SwitchNullCheck.check(p.after, p.after.units, m => m.id == ms.head.id)
    assertEquals(clue(byToken).size, 1)
  }

  private def collectMatches(u: Tree.ClassDef)(using balticporter.tir.Program): List[Tree.Match] =
    val out = collection.mutable.ListBuffer.empty[Tree.Match]
    balticporter.tir.StandardTraversal.scanClassDef(u, ()) { (_, t) =>
      t match
        case m: Tree.Match => out += m
        case _             => ()
      ()
    }
    out.toList
