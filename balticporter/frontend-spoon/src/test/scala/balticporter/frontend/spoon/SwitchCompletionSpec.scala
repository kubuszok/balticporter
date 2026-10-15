package balticporter.frontend.spoon

import balticporter.tir.*

/** A colon-form switch case runs on into the next one only when it can complete normally (JLS 14.22); every other case's arm holds its own statements and nothing of the cases after it. */
class SwitchCompletionSpec extends munit.FunSuite:

  private val program = SpoonTir.fromSource(
    """package demo;
      |class Sw {
      |  void a() {} void b() {} void c() {} void d() {}
      |  int run(int[] xs, boolean f) {
      |    outer:
      |    for (int x : xs) {
      |      switch (x) {
      |        case 1: a(); continue outer;
      |        case 2: if (f) { return 1; } else { return 2; }
      |        case 3: b(); break outer;
      |        case 4: { int k = 1; while (true) { k++; if (k > 3) return k; } }
      |        case 5: try { c(); return 5; } finally { d(); }
      |        case 6: if (f) { return 6; }
      |        case 7: while (true) { if (f) break; }
      |        case 8: c();
      |        default: d();
      |      }
      |    }
      |    return 0;
      |  }
      |}
      |""".stripMargin
  )

  /** the names of the methods each arm calls, in arm order. */
  private lazy val armCalls: List[Set[String]] =
    given Program = program
    val sym       = program.symbols.all.find(_.fullName == "demo.Sw#run").map(_.id).getOrElse(fail("no run"))
    val body      = program.definitionOf(sym) match
      case Some(d: Tree.DefDef) => d.rhs.getOrElse(fail("no body"))
      case _                    => fail("run is not a method")
    val m = StandardTraversal
      .scanTerm(body, List.empty[Tree.Match]) {
        case (acc, m: Tree.Match) => m :: acc
        case (acc, _)             => acc
      }
      .head
    m.cases.map { c =>
      StandardTraversal.scanTerm(c.body, Set.empty[String]) {
        case (acc, a: Tree.Apply) => acc ++ program.symbolOf(a.method).map(_.name).filter(n => n.length == 1 && n.head.isLetter)
        case (acc, _)             => acc
      }
    }

  test("an arm ending in an abrupt statement holds none of the cases after it") {
    assertEquals(armCalls(0), Set("a")) // continue L
    assertEquals(armCalls(1), Set.empty) // if/else, both branches return
    assertEquals(armCalls(2), Set("b")) // break L
    assertEquals(armCalls(3), Set.empty) // a block ending in an infinite loop
    assertEquals(armCalls(4), Set("c", "d")) // try whose body returns, finally completes
  }

  test("an arm that CAN complete normally still falls through") {
    assertEquals(armCalls(5), Set("c", "d")) // if without else
    assertEquals(armCalls(6), Set("c", "d")) // a loop with a break out of it
    assertEquals(armCalls(7), Set("c", "d"))
  }
