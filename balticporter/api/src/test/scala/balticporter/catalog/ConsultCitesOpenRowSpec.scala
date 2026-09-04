package balticporter.catalog

import balticporter.tir.Origin

/** STATUS ENFORCEMENT RULE (ii): **a CONSULT that cites an `Open` or `Absent` row is a finding.** */
class ConsultCitesOpenRowSpec extends munit.FunSuite:

  private val origin = Origin("Snippet.java", 1, 1)
  /** a FRESH stand-in for the Java node being lowered — `Lowering.of`'s `subject`, which joins the
    * two dispatches of ONE node by identity. A `def`, so every call site is a different node. */
  private def node: AnyRef = new Object

  /** the rule, over a LOG. `Nil` when every consult the run made is fine. */
  private def findings(log: CatalogLog,
                       statusOf: DiffId => Option[Status] = id => Differences.byId.get(id).map(_.status)): List[String] =
    log.reached.toList.sortBy(_.toString).flatMap { id =>
      statusOf(id) match
        case scala.None => Some(s"$id is cited by a consult and is not in the registry")
        case Some(st) =>
          st match
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

    // …and the third arm, against a STATED status. The registry has no `Absent` row today —
    // `JS-C43` was the last one and left when the record lowering landed — and the arm still has to
    // be tested, because the next syntax family this engine meets will put a row back on it.
    assertEquals(Differences.all.filter(_.status.isInstanceOf[Status.Absent]), Nil)
    assert(findings(consulting(handled.id), _ => Some(Status.Absent("the frontend has no model"))).nonEmpty)
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
    // sites — `ENGINE-LIMITS.
    val open = Differences.all.filter(d => d.status.isOpen || d.status.isInstanceOf[Status.Absent])
    assert(open.nonEmpty, "the registry has no Open or Absent row — this test would be vacuous")
    // …and it is `Open` rows alone that keep it non-vacuous now: the `Absent` set is empty since
    // `JS-C43`'s lowering, so the guard above is entirely carried by the work list.
    // `Attaches.Lowered` on an Open row is EXPECTED and is not a violation: it is the work list.
    assertEquals(findings(new CatalogLog), Nil)
  }
