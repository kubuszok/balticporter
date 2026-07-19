package balticporter.frontend.spoon

import balticporter.tir.*

/** Locks in body-construct coverage: a single method exercising arrays (new/access/length),
  * classic-for with break/continue, for-each, while, if, try/catch/finally, switch,
  * instanceof, lambda, operators, return. If any construct regresses to `Unsupported`,
  * `fromSource` throws and this fails — a construct-level regression net independent of the
  * corpus. Also checks the call graph survives all of them. */
class SpoonTirBodySpec extends munit.FunSuite:

  private val src =
    """package demo;
      |class Ops {
      |  int[] data;
      |  int sum() {
      |    int total = 0;
      |    for (int i = 0; i < data.length; i++) {
      |      total = total + data[i];
      |      if (total < 0) { continue; }
      |      if (total > 100) { break; }
      |    }
      |    for (Object o : new Object[]{ "a", "b" }) {
      |      if (o instanceof String) { total = total + 1; }
      |    }
      |    while (total > 0) { total = total - 1; }
      |    try { total = risky(); } catch (RuntimeException e) { total = -1; } finally { total = total + 0; }
      |    switch (total) { case 0: total = 0; break; default: total = 9; }
      |    java.util.function.IntUnaryOperator f = x -> x + 1;
      |    return total;
      |  }
      |  int risky() { return 1; }
      |}
      |""".stripMargin

  private val program = SpoonTir.fromSource(src) // throws on any Unsupported construct

  private def member(full: String): SymId =
    program.symbols.all.find(_.fullName == full).map(_.id).getOrElse(fail(s"no member $full"))

  test("a method using every supported construct translates with no Unsupported") {
    // reaching here means fromSource did not throw — all constructs translated.
    assert(program.definitionOf(member("demo.Ops#sum")).isDefined)
  }

  test("the call graph survives all body constructs") {
    val sum   = member("demo.Ops#sum")
    val risky = member("demo.Ops#risky")
    // sum() calls risky() inside a try block — the edge is still traced.
    assert(program.usagesOf(risky, UsageKind.Call).nonEmpty)
    assertEquals(program.callersOf(risky), List(sum))
  }

  test("instanceof records the tested type as a usage") {
    val string = program.symbols.all.find(_.fullName == "java.lang.String").map(_.id)
    assert(string.exists(id => program.usagesOf(id).nonEmpty))
  }

  // second batch: constructs surfaced by the flexmark corpus
  private val src2 =
    """package demo;
      |class More {
      |  int run(int n) {
      |    int i = 0;
      |    int total = 0;
      |    assert n > 0 : "positive";
      |    do { total = total + n; i++; } while (i < n);
      |    synchronized (this) { total = total + 1; }
      |    int[] a = new int[3];
      |    a[i % 3] = a[0]++;                 // inc/dec in expression + array write
      |    int x;
      |    while ((x = a[0]) > 0) { break; }  // assignment as expression
      |    switch (n) {
      |      case 1: total = total + 1;       // genuine fallthrough
      |      case 2: total = total + 2; break;
      |      default: total = 0;
      |    }
      |    return total;
      |  }
      |}
      |""".stripMargin

  test("assert / do-while / synchronized / inc-dec-expr / assign-expr / switch-fallthrough all translate") {
    val p = SpoonTir.fromSource(src2) // throws on any Unsupported
    assert(p.symbols.all.exists(_.fullName == "demo.More#run"))
  }

  test("try-with-resources translates (resources kept structural on the Try)") {
    val p = SpoonTir.fromSource(
      """package demo;
        |import java.io.*;
        |class R {
        |  void go() throws Exception {
        |    try (BufferedReader r = new BufferedReader(new FileReader("x"))) { r.readLine(); }
        |    catch (IOException e) { }
        |  }
        |}
        |""".stripMargin
    )
    assert(p.symbols.all.exists(_.fullName == "demo.R#go"))
  }
