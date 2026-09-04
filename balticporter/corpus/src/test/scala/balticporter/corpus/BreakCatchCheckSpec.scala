package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.tir.{BreakCatchCheck, Constant, Flags, MemberIndex, Origin, Program, Symbol,
  SymbolTable, SymId, Tree, TypeRepr, TypeTree, Xref}
import balticporter.tir.BreakCatchCheck.Issue

/** The `break-catch` lane: can it report, and does it read 0 once the emitter guards? */
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
    val fs = BreakCatchCheck.check(p.after, p.after.units, (_: Tree.Try) => false)
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
    assert(clue(p.emitter.breakGuardCount) > 0)
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
    assertEquals(BreakCatchCheck.check(p.after, p.after.units, (_: Tree.Try) => false), Nil)
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
    assertEquals(BreakCatchCheck.check(p.after, p.after.units, (_: Tree.Try) => false), Nil)
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
    assertEquals(BreakCatchCheck.check(p.after, p.after.units, (_: Tree.Try) => false), Nil)
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
    val fs = BreakCatchCheck.check(p.after, p.after.units, (_: Tree.Try) => false)
    assertEquals(clue(fs.map(_.jump).sorted), List("break", "break outer", "continue"))
    // …and the emitter guards that try once, which covers all three
    p.out
    assertEquals(BreakCatchCheck.check(p.after, p.after.units, p.emitter.breakGuards), Nil)
  }

  // -- two `try`s, ONE origin: a guarded one must not vouch for its unguarded sibling -------------

  test("a guarded `try` does not vouch for an unguarded sibling that shares its ORIGIN") {
    val O   = Origin("C.java", 7, 5)
    val CLS = SymId(101)
    val M   = SymId(102)
    val EX  = SymId(103)
    val E   = SymId(104)
    val exT = TypeRepr.TypeRef(TypeRepr.NoPrefix, EX)

    def aTry = Tree.Try(
      resources = Nil,
      body      = Tree.Break(None, TypeRepr.NoType, O),
      catches   = List(Tree.CatchCase(
        Tree.ValDef(E, TypeTree(exT, O), rhs = None, origin = O),
        Tree.Literal(Constant.UnitC, TypeRepr.NoType, O))),
      finalizer = None, tpe = TypeRepr.NoType, origin = O)

    val guardedTry = aTry
    val siblingTry = aTry // a DIFFERENT node, structurally equal, at the same origin
    val loop = Tree.While(Tree.Literal(Constant.BoolC(true), TypeRepr.NoType, O),
      Tree.Block(List(guardedTry, siblingTry), Tree.Literal(Constant.UnitC, TypeRepr.NoType, O),
        TypeRepr.NoType, O), TypeRepr.NoType, O)
    val d  = Tree.DefDef(M, paramss = List(Nil), returnTpt = TypeTree(TypeRepr.NoType, O),
      rhs = Some(loop), origin = O)
    val cd = Tree.ClassDef(CLS, parents = Nil, selfType = None, body = List(d), origin = O)
    val syms = SymbolTable(List(
      Symbol(CLS, "C", "demo.C", Flags(), SymId.None, TypeRepr.TypeRef(TypeRepr.NoPrefix, CLS)),
      Symbol(M, "f", "demo.C#f", Flags(), CLS, TypeRepr.MethodType(Nil, TypeRepr.NoType)),
      Symbol(EX, "Exception", "java.lang.Exception", Flags(), SymId.None, TypeRepr.NoType),
      Symbol(E, "e", "demo.C#f(e)", Flags(), M, exT),
    ))
    val program = Program(List(cd), syms, Xref.build(List(cd)), MemberIndex.empty)

    // BY TOKEN — what the emitter records: the sibling is still reported, and only it.
    val byToken = BreakCatchCheck.check(program, List(cd), t => t.id == guardedTry.id)
    assertEquals(clue(byToken).size, 1)
    assert(byToken.head.jump == "break", byToken.head.render)

    // BY ORIGIN — what this check used to ask, emulated exactly: the guarded try's origin answers
    // for the sibling too and the finding disappears. THIS is the defect.
    assertEquals(clue(BreakCatchCheck.check(program, List(cd), t => t.origin == guardedTry.origin)), Nil)

    // …and BY OBJECT IDENTITY, which reads like the obvious fix and is not one: `StandardTraversal`
    // rebuilds every node it walks, so no `try` the check holds is the object the emitter guarded
    // and NOTHING is ever recognised as guarded. Two findings where there is one defect — the
    // opposite failure, equally silent as a count.
    assertEquals(clue(BreakCatchCheck.check(program, List(cd), t => t eq guardedTry)).size, 2)
  }
