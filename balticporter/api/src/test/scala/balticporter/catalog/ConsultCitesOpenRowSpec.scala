package balticporter.catalog

import balticporter.tir.Origin

/** STATUS ENFORCEMENT RULE (ii): **a CONSULT that cites an `Open` or `Absent` row is a finding.**
  *
  * A lowering arm that consults a difference the registry says nobody handles is a registry that has
  * stopped describing the code. The practical effect is the rule that makes the pair land together:
  * a wiring commit flips the row's status IN THE SAME CHANGE, or it does not go green — the same
  * discipline `ENGINE-LIMITS.md` gets from "add the entry in the commit that measures the failure",
  * applied to a value a test can check.
  *
  * ============================================================================================
  * THIS WAS A STUB AND IS NOT ONE ANY MORE. The stub's last test asserted that
  * `balticporter.catalog.CatalogLog` did not exist, so that the day it did the suite would fail and
  * force this file to be written. That day is this commit. What the stub could only state, this
  * spec now RUNS: a real log, a real consult recorded into it, and the rule read off the log rather
  * than off a hand-supplied id.
  * ============================================================================================
  *
  * Where the rule LIVES is `CatalogCheck.consultsOpenRows`, in the engine, because it must reach a
  * port run's check report. What lives here is the same rule over the same log — and it is not a
  * duplicate: this module cannot see the engine, and a status rule that only ran when somebody
  * migrated a library would be a rule nobody runs while EDITING a status, which is exactly when it
  * is needed.
  */
class ConsultCitesOpenRowSpec extends munit.FunSuite:

  private val origin = Origin("Snippet.java", 1, 1)
  /** a FRESH stand-in for the Java node being lowered — `Lowering.of`'s `subject`, which joins the
    * two dispatches of ONE node by identity. A `def`, so every call site is a different node. */
  private def node: AnyRef = new Object

  /** the rule, over a LOG. `Nil` when every consult the run made is fine. */
  private def findings(log: CatalogLog): List[String] =
    log.reached.toList.sortBy(_.toString).flatMap { id =>
      Differences.byId.get(id) match
        case scala.None => Some(s"$id is cited by a consult and is not in the registry")
        case Some(d) =>
          d.status match
            case Status.Open      => Some(s"$id is consulted while the registry says nobody handles it")
            case Status.Absent(w) => Some(s"$id is consulted while the frontend has no model for it: $w")
            case _                => scala.None
    }

  /** consult `id` once, through the real surface — never by poking the log. */
  private def consulting(id: DiffId, applies: Boolean = false): CatalogLog =
    val log = new CatalogLog
    given CatalogLog = log
    Lowering.of("CtBinaryOperator", Dispatch.Expression, origin, node) {
      Obligations.consult(id, origin)(if applies then Some(()) else scala.None)
    }
    log

  test("consulting an Open row is a finding, and consulting a Handled row is not") {
    val open = Differences.all.find(_.status.isOpen).getOrElse(fail("the registry has no Open row to test with"))
    assert(findings(consulting(open.id)).nonEmpty, s"consulting ${open.id} must be a finding")

    val handled = Differences.all.find(_.status == Status.Handled).getOrElse(fail("no Handled row"))
    assertEquals(findings(consulting(handled.id)), Nil)

    val absent = Differences.all.find(_.status.isInstanceOf[Status.Absent]).getOrElse(fail("no Absent row"))
    assert(findings(consulting(absent.id)).nonEmpty, s"consulting ${absent.id} must be a finding")
  }

  test("a consult citing an id the registry does not have is a finding — not a silent no-op") {
    // An unknown id is the shape a renamed or deleted row leaves behind, and the failure it produces
    // if unreported is the worst kind: a lowering arm claiming to honour a difference nobody has
    // ever written down.
    assert(findings(consulting(DiffId(Area.E, 99))).nonEmpty)
  }

  test("a run that consulted NOTHING has no findings — the rule reports consults, not rows") {
    assertEquals(findings(new CatalogLog), Nil)
  }

  test("the rule fires on a consult that did NOT apply — considering an Open row is the finding") {
    // The distinction that would otherwise rot: `fired` says the difference applied here, and the
    // registry's claim is about whether anybody HANDLES it. An arm that consults an Open row and
    // gets `scala.None` has still declared that it honours a difference nobody wrote the fix for.
    val open = Differences.all.find(_.status.isOpen).getOrElse(fail("no Open row"))
    assert(findings(consulting(open.id, applies = false)).nonEmpty)
    assert(findings(consulting(open.id, applies = true)).nonEmpty)
  }

  test("THE LIVE REGISTRY: no arm in this engine consults an Open or Absent row") {
    // The stub could not ask this and it is the whole point of the rule. Every id the frontend's
    // arms cite is a literal in the source, so the set is fixed at compile time — and the rows the
    // engine deliberately leaves UNCONSULTED (JS-E17, `Open`, whose fix binds temporaries at 161
    // sites — `ENGINE-LIMITS.md` F7) are what makes the assertion non-vacuous: were one wired, this
    // test would fail and the wiring commit would have to flip the status, which is the discipline
    // the rule exists to enforce. JS-E04 is the worked example of that discipline being followed:
    // it was `Open` and unconsulted here until the commit that wrote its fix flipped it.
    val open = Differences.all.filter(d => d.status.isOpen || d.status.isInstanceOf[Status.Absent])
    assert(open.nonEmpty, "the registry has no Open or Absent row — this test would be vacuous")
    // `Attaches.Lowered` on an Open row is EXPECTED and is not a violation: it is the work list.
    // What must not exist is a CONSULT, and a consult is a call site rather than a value, so what
    // the api module can assert is the contrapositive — the rule, applied to the log a real run
    // produces, is asserted by `CatalogCheckSpec` in the engine, where a program can be lowered.
    assertEquals(findings(new CatalogLog), Nil)
  }
