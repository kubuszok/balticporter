package balticporter.catalog

/** STATUS ENFORCEMENT RULE (ii): **a CONSULT that cites an `Open` or `Absent` row is a finding.**
  *
  * A lowering arm that consults a difference the registry says nobody handles is a registry that has
  * stopped describing the code. The practical effect is the rule that makes the pair land together:
  * a wiring commit flips the row's status IN THE SAME CHANGE, or it does not go green — the same
  * discipline `ENGINE-LIMITS.md` gets from "add the entry in the commit that measures the failure",
  * applied to a value a test can check.
  *
  * ============================================================================================
  * THIS SPEC IS A STUB, AND SAYS SO RATHER THAN PASSING QUIETLY.
  *
  * The rule needs a list of the consults a run performed, and that list is produced by a
  * `CatalogLog` that does not exist yet: the obligation surfaces — a wrapper at the frontend's
  * dispatch, a symmetrical one at the emitter's, and a per-declaration citation from a phase — are
  * later chunks. Until one of them lands there is nothing to read, and the honest thing is a spec
  * that states the rule, tests what CAN be tested of it, and names what it cannot.
  *
  * What it tests today is the rule's PREDICATE, in isolation: given a cited id and the registry, is
  * this a finding? That much is real, it is the part a wiring commit will get wrong, and having it
  * under test means the day the log arrives the only new code is the plumbing.
  *
  * What it does NOT test, and must not be read as testing: that any consult exists, that any arm
  * cites anything, or that the registry describes the code at all. A facility with no call sites is
  * indistinguishable from one that is not there, and pretending otherwise is exactly how a coverage
  * mechanism ships inert.
  * ============================================================================================ */
class ConsultCitesOpenRowSpec extends munit.FunSuite:

  /** the rule, as a pure function of the registry. `scala.None` when the citation is fine. */
  private def findingFor(cited: DiffId): Option[String] =
    Differences.byId.get(cited) match
      case scala.None => Some(s"$cited is cited by a consult and is not in the registry")
      case Some(d) =>
        d.status match
          case Status.Open      => Some(s"$cited is consulted while the registry says nobody handles it")
          case Status.Absent(w) => Some(s"$cited is consulted while the frontend has no model for it: $w")
          case _                => scala.None

  test("the predicate: consulting an Open row is a finding, and consulting a Handled row is not") {
    val open = Differences.all.find(_.status.isOpen).getOrElse(fail("the registry has no Open row to test with"))
    assert(findingFor(open.id).isDefined, s"consulting ${open.id} must be a finding")

    val handled = Differences.all.find(_.status == Status.Handled).getOrElse(fail("no Handled row"))
    assertEquals(findingFor(handled.id), scala.None)

    val absent = Differences.all.find(_.status.isInstanceOf[Status.Absent]).getOrElse(fail("no Absent row"))
    assert(findingFor(absent.id).isDefined, s"consulting ${absent.id} must be a finding")
  }

  test("a consult citing an id the registry does not have is a finding — not a silent no-op") {
    // An unknown id is the shape a renamed or deleted row leaves behind, and the failure it produces
    // if unreported is the worst kind: a lowering arm claiming to honour a difference nobody has
    // ever written down.
    assert(findingFor(DiffId(Area.E, 99)).isDefined)
  }

  test("NOTHING IS WIRED YET — no consult exists to check, and this test is the record of that") {
    // Deliberately an assertion about the STATE OF THE DESIGN, so that the day a `CatalogLog` lands
    // this test fails and forces the real one to be written here. A stub that quietly passes forever
    // is a stub nobody removes.
    val logExists =
      try
        Class.forName("balticporter.catalog.CatalogLog"); true
      catch case _: ClassNotFoundException => false
    assert(!logExists,
      "balticporter.catalog.CatalogLog now exists — replace this stub with the real rule (ii): " +
        "read the run's consults and report every one that cites an Open or Absent row")
  }
