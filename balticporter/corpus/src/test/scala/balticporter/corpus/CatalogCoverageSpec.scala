package balticporter.corpus

import balticporter.catalog.{Attaches, CatalogLog, Differences, Dispatch, DiffId, JS, Lowering, Obligations, Status}
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{CatalogCheck, Origin}

/** THE COVERAGE LANES, AND THE PROOF THAT THEY CAN FAIL.
  *
  * A coverage mechanism that only ever sees passing input cannot distinguish "the rule holds" from
  * "the rule is inert", and `DESIGN.md` §2.8 names the exact failure to avoid: a facility with no
  * call sites is indistinguishable from one that is not there. So every lane here is exercised in
  * BOTH directions — a run that discharges its obligations reports nothing, and a run that does not
  * reports precisely the row it skipped.
  *
  * The negatives are built from the SURFACE, never by poking the log: a probe that wrote a hole
  * into the log directly would prove that the reporting works and nothing about whether the wrapper
  * ever produces one.
  */
class CatalogCoverageSpec extends munit.FunSuite:

  private val origin = Origin("Snippet.java", 1, 1)

  // -------------------------------------------------------------------------------------------
  // The wrapper, in isolation — the mechanism, before any Java is involved.
  // -------------------------------------------------------------------------------------------

  test("an arm that CONSULTS its attached row leaves no hole") {
    val log = new CatalogLog
    given CatalogLog = log
    Lowering.of("CtOperatorAssignment", Dispatch.Statement, origin) {
      Obligations.consult(JS.E(3), origin)(scala.None)
    }
    assertEquals(log.undischarged.map(_.id), Nil)
    assertEquals(log.consulted(JS.E(3)), 1)
    assertEquals(log.fired(JS.E(3)), 0)
  }

  test("THE NEGATIVE: an arm that returns WITHOUT consulting is reported, with its kind and site") {
    // The `_ => false`-style probe every check in this engine owes. `Lowering.of` is entered
    // exactly as the frontend enters it and the body simply declines to ask — which is the defect
    // shape, written down.
    val log = new CatalogLog
    given CatalogLog = log
    Lowering.of("CtOperatorAssignment", Dispatch.Statement, origin)(())
    val holes = log.undischarged
    assertEquals(holes.map(_.id), List(JS.E(3)))
    assertEquals(holes.head.kind, "CtOperatorAssignment")
    assertEquals(holes.head.dispatch, Dispatch.Statement)
    assertEquals(CatalogCheck.undischarged(log).map(_.kind), List("ENGINE GAP"))
  }

  test("…and the hole is one finding per ROW, however many sites produced it") {
    val log = new CatalogLog
    given CatalogLog = log
    (1 to 40).foreach(_ => Lowering.of("CtOperatorAssignment", Dispatch.Statement, origin)(()))
    assertEquals(log.undischarged.size, 1)
    assertEquals(log.undischarged.head.sites, 40)
  }

  test("the DISPATCH is part of the key — JS-E03 is owed at a statement and JS-E04 at an expression") {
    // The pair the whole mechanism was designed around. A kind-only attachment could not tell them
    // apart, and the two rows exist precisely because java gives one node kind two meanings.
    assertEquals(Differences.owedAt("CtOperatorAssignment", Dispatch.Statement), List(JS.E(3)))
    assertEquals(Differences.owedAt("CtOperatorAssignment", Dispatch.Expression), List(JS.E(4)))
    assertEquals(Differences.owedAt("CtLiteral", Dispatch.Expression), Nil)
  }

  test("FATAL mode raises on a hole the registry claims is handled, and never on a declared-open row") {
    val fatal = new CatalogLog(fatal = true)
    intercept[AssertionError] {
      given CatalogLog = fatal
      Lowering.of("CtOperatorAssignment", Dispatch.Statement, origin)(())
    }
    // JS-E04 is `Open`. It attaches, it is never consulted, and a testkit run must NOT die on it —
    // it is the work list, and a mode that died on the work list would make the work list
    // unrunnable. This is the one exemption, and it is derived from the row's own status.
    val alsoFatal = new CatalogLog(fatal = true)
    given CatalogLog = alsoFatal
    Lowering.of("CtOperatorAssignment", Dispatch.Expression, origin)(())
    assertEquals(alsoFatal.undischarged.map(_.id), List(JS.E(4)))
  }

  // -------------------------------------------------------------------------------------------
  // The lanes, over a real lowering.
  // -------------------------------------------------------------------------------------------

  private def lower(java: String): CatalogLog =
    val log = new CatalogLog
    SpoonTir.fromSource(java, catalog = log)
    log

  test("a real lowering reaches the JS-E rows the frontend wires, and reports the one it does not") {
    val log = lower("""
      public class S {
        int f(Object a, Object b, int i, byte c, String s) {
          boolean same = a == b;
          // `i++` as a VALUE. As a bare statement it reaches `stmtKind`'s own increment arm, which
          // lowers to a plain `Assign` because the value is discarded — so the row's
          // `Dispatch.Expression` attachment is exactly right and the statement arm owes nothing.
          int m = i++;
          c += 3;
          String t = a + "x";
          int j = (i > 0) ? i : 0;
          int k = (j = i);
          return k;
        }
      }
    """)
    // consulted AND fired: the difference was considered here and it applied.
    List(JS.E(1), JS.E(2), JS.E(3), JS.E(14), JS.E(15)).foreach { id =>
      assert(log.consulted(id) > 0, s"$id was never consulted")
      assert(log.fired(id) > 0, s"$id was consulted and never applied")
    }
    // JS-E04 attaches at the expression dispatch (`k = (j = i)` is a plain assignment; a compound
    // one in expression position is what would owe it) and is `Open`, so nothing consults it.
    assert(!log.reached.contains(JS.E(4)), "JS-E04 is Open — no arm may consult it (rule (ii))")
  }

  test("THE NEGATIVE for `unreached`: a port that lowers nothing reports every mechanised row") {
    val empty = new CatalogLog
    val rows  = CatalogCheck.unreached(empty).map(_.owner).toSet
    assertEquals(rows, Differences.mechanised.map(_.id.toString).toSet)
    assert(rows.nonEmpty, "no row is mechanised — the unreached lane would be vacuous")
  }

  test("…and `unreached` is NARROWED to rows whose surface exists — the [rev-1] claim, mechanised") {
    // A lane reporting "this row is live" on the strength of a surface nobody built would be
    // reporting about the mechanism and not about the port. So an `Unmechanised` row may never
    // appear on the unreached lane, however unreached it is.
    val unreached    = CatalogCheck.unreached(new CatalogLog).map(_.owner).toSet
    val unmechanised = Differences.unmechanised.map(_.id.toString).toSet
    assertEquals(unreached.intersect(unmechanised), Set.empty[String])
    assert(unmechanised.nonEmpty, "nothing is unmechanised — this narrowing would be vacuous")
    assertEquals(CatalogCheck.unmechanised.map(_.owner).toSet, unmechanised)
  }

  test("the `consulted` lane counts rows and not sites, and moves with the wiring") {
    val log = lower("public class S { boolean f(Object a, Object b) { return a == b; } }")
    val reached = CatalogCheck.consulted(log).map(_.owner).toSet
    assert(reached.contains(JS.E(1).toString))
    // JS-E03 attaches at the STATEMENT dispatch and this snippet has no compound assignment, so it
    // is consulted zero times — which is the honest answer and is what makes the lane a measurement
    // rather than a constant.
    assert(!reached.contains(JS.E(3).toString))
  }

  test("catalog.tsv holds EVERY row, reached or not — the artifact answers `never touched`") {
    val log  = lower("public class S { int f(int i) { return i; } }")
    val rows = CatalogCheck.tsv(log)
    assertEquals(rows.size, Differences.all.size)
    assert(rows.exists(_.startsWith("JS-E04\t")), "a never-reached row must still have a line")
  }

  test("every row's attachment is HONEST about the surface that exists") {
    // The registry's own guard rail, and the one an area wave will be tempted to bend: a row may
    // not claim a lowering attachment at a Spoon kind the frontend does not dispatch on, because
    // the obligation would then be owed at a site nothing ever enters — a claim that reads as
    // coverage and can never fail.
    val dispatched = balticporter.frontend.spoon.SpoonKinds.lowered.map(_.name).toSet
    val bad = Differences.all.collect {
      case d if d.attaches.isInstanceOf[Attaches.Lowered] =>
        val Attaches.Lowered(k, _) = d.attaches: @unchecked
        (d.id, k)
    }.filterNot((_, k) => dispatched.contains(k))
    assertEquals(bad, Nil, s"attached to a kind no arm lowers: $bad")
  }
