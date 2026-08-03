package balticporter.tir

import balticporter.catalog.{Attaches, CatalogLog, Difference, Differences, DiffId, Status}

/** COVERAGE OF THE DIFFERENCE CATALOG — four lanes over one run's [[CatalogLog]], and a fifth over
  * the REGISTRY.
  *
  * A registry nothing consults is dead weight, and the failure mode to avoid is named in
  * `DESIGN.md` §2.8: a facility with no call sites is indistinguishable from one that is not there.
  * `TirTrace` is this repository's own worked example. So the mechanism ships with the lane that can
  * FAIL on it.
  *
  * ==Four lanes, and why a single number would be met the wrong way==
  *
  * The `trivia(|recovered|deliberate)` precedent applies exactly: `lost = 0` is a bar a run could
  * hold by recovering everything, and reporting the bar without the two residues says nothing about
  * how it was met. Here:
  *
  *   - `catalog(consulted)` — rows this run REACHED, at either surface. The positive number, and
  *     the one that makes `unreached` honest: a wiring regression that silently removes a consult
  *     moves this lane down and the next one up, and a reader can see both halves;
  *   - `catalog(unreached)` — a row whose discharge surface EXISTS and which this port never
  *     reached. **The claim is narrowed to mechanised rows on purpose**: a lane reporting "this row
  *     is live" on the strength of a surface nobody built would be reporting about the mechanism
  *     and not about the port;
  *   - `catalog(unmechanised)` — rows whose discharge surface is NOT built. The number that says
  *     "we are not measuring these", which is the only honest alternative to measuring them. It
  *     moves when a wave builds a surface, and it is derived from the registry rather than counted
  *     from a run, so it is the same on every port — deliberately, because it is a fact about the
  *     ENGINE and a reader comparing two ports must be able to see that it did not move;
  *   - `catalog(undischarged)` — a lowering that returned without consulting a row the catalog
  *     attaches to it. This is the WORK LIST, derived rather than guessed. One finding per ROW and
  *     never per site: a compound assignment in every method of a library would otherwise file a
  *     thousand findings saying one thing.
  *
  * ==And one lane that is not about coverage at all==
  *
  *   - `catalog(uncited)` — rows carrying no Scala-side normative citation. Not a coverage
  *     question: it says nothing about whether the engine handles the difference, only about
  *     whether the registry can point at the text that governs the Scala half. It is here because
  *     `counts.tsv` is the artifact a baseline diffs, and the number was previously a `println` in
  *     one spec beside an assertion no registry could fail. Never a failing assertion, anywhere: a
  *     spec that failed on it is a spec somebody silences by inventing a citation.
  *
  * `catalog(refused)`, the fifth lane the design named, is not here and must not be added: it is
  * the `markers` lane, which already records a `Tree.Unportable` mint with its catalog id on every
  * run. Two lanes counting one thing is how two numbers start disagreeing.
  *
  * ==What is NOT claimed==
  *
  * The undischarged lane detects an ABSENT consult. It cannot detect a WRONG one — an arm that
  * consults a row and hands it a predicate which never returns `Some` discharges the obligation and
  * emits the same wrong code. The honest claim is *a difference cannot be silently UNCONSIDERED at
  * a site the catalog attaches it to*; that the consideration is CORRECT is carried by the
  * per-difference edge-case suites. An over-claimed guarantee is how a mechanism stops being
  * audited.
  *
  * ==Enforcement==
  *
  * Counted here, FATAL in the testkit (`CatalogLog(fatal = true)`). A port run that died because a
  * rule is incomplete is a port run that produces no diagnostics at all, which is the wrong trade —
  * `ENGINE-LIMITS.md` M6 is about refusing to APPROXIMATE, not about refusing to REPORT.
  */
object CatalogCheck:

  val Consulted    = "catalog(consulted)"
  val Unreached    = "catalog(unreached)"
  val Unmechanised = "catalog(unmechanised)"
  val Undischarged = "catalog(undischarged)"
  val Uncited      = "catalog(uncited)"

  /** the §1 classification every finding here carries, because an error an agent cannot classify as
    * (a)/(b)/(c) costs it a full investigation (`CLAUDE.md` §4.45). Every one of these is (a): the
    * catalog is the (a) layer by construction, and a hole in it is an engine gap. */
  val Classification: String =
    "[§1(a) engine] the difference catalog is the universal layer; a row it attaches and an arm " +
      "does not consult is an engine gap, never a port's to configure"

  private def at(id: DiffId, log: CatalogLog): (String, Int) =
    log.exampleSite(id).map(o => (CheckReport.relativise(o.javaPath), o.line)).getOrElse(("-", 0))

  /** rows this run reached, at either surface. */
  def consulted(log: CatalogLog): List[CheckReport.Finding] =
    Differences.all
      .filter(d => log.consulted(d.id) > 0 || log.declarations(d.id) > 0)
      .map { d =>
        val (path, line) = at(d.id, log)
        val where = d.attaches match
          case Attaches.Lowered(k, disp) => s"lowering $k/$disp"
          case Attaches.Cited(ph)        => s"phase $ph"
          case _                         => "?"
        CheckReport.Finding(Consulted, "reached", d.id.toString, path, line,
          s"$where — consulted ${log.consulted(d.id)}, fired ${log.fired(d.id)}, " +
            s"declarations ${log.declarations(d.id)}: ${d.title}")
      }

  /** a row whose surface EXISTS and which this port never reached.
    *
    * Informational per port and aggregated across the corpus by `just catalog-coverage`: a row
    * unreached on jbump (2,000 lines, no suite) is normal, and a row unreached on all fifteen is a
    * branch nothing in the corpus exercises — which is either dead code or an untested rule, and
    * `CLAUDE.md` §3's whole argument is that those are the same thing until a test says otherwise. */
  def unreached(log: CatalogLog): List[CheckReport.Finding] =
    Differences.mechanised
      .filter(d => log.consulted(d.id) == 0 && log.declarations(d.id) == 0)
      .map(d => CheckReport.Finding(Unreached, kindOf(d), d.id.toString, "-", 0,
        s"never reached by this port — ${d.evidence}: ${d.title}"))

  /** rows nothing is instrumented to answer for. Derived from the REGISTRY, not from the run. */
  def unmechanised: List[CheckReport.Finding] =
    Differences.unmechanised.map { d =>
      val why = d.attaches match
        case Attaches.Unmechanised(w) => w
        case _                        => ""
      CheckReport.Finding(Unmechanised, kindOf(d), d.id.toString, "-", 0, s"$why: ${d.title}")
    }

  /** rows with NO Scala-side normative citation. Derived from the REGISTRY, not from the run.
    *
    * `UNCITED — ` is a real state and a deliberate prefix rather than an empty field: for many of
    * these rules there is a Scala 3 reference page and nobody located it, and for some there is
    * genuinely no normative text. What the prefix buys is a number that can go DOWN instead of a
    * sentence in a document nobody re-reads — and that only works if something DIFFS the number.
    * It was a `println` in one spec beside `assert(uncited <= all)`, a comparison no registry can
    * fail, so 121 of them sat there with nothing able to report the 122nd.
    *
    * Recorded here for the same reason and in the same shape as [[unmechanised]]: `counts.tsv` and
    * `findings.tsv` are the artifacts a baseline diffs, so a row that gains a citation shows up as a
    * removed finding and a row added without one shows up as a new one. Deliberately NOT a failing
    * assertion anywhere — a spec that failed on this gap is a spec somebody silences by INVENTING a
    * citation, which is worse than the gap it closes.
    *
    * Registry-derived, so it reads the same on every port. That is the point, exactly as it is for
    * `unmechanised`: it is a fact about the ENGINE's own documentation, and a reader comparing two
    * ports must be able to see that it did not move. */
  def uncited: List[CheckReport.Finding] =
    Differences.all.filter(_.scala.startsWith("UNCITED")).map { d =>
      CheckReport.Finding(Uncited, kindOf(d), d.id.toString, "-", 0,
        s"${d.scala} (JLS side: ${d.jls}): ${d.title}")
    }

  /** a lowering that returned without consulting an attached row — one finding per ROW. */
  def undischarged(log: CatalogLog): List[CheckReport.Finding] =
    log.undischarged.map { h =>
      val known = if CatalogLog.knownHole(h.id) then "declared open" else "ENGINE GAP"
      CheckReport.Finding(Undischarged, known, h.id.toString,
        CheckReport.relativise(h.at.javaPath), h.at.line,
        s"${h.kind}/${h.dispatch} lowered ${h.sites} time(s) without consulting it — " +
          s"${Differences.byId.get(h.id).map(_.title).getOrElse("?")}")
    }

  /** STATUS ENFORCEMENT RULE (ii) — a consult that cites an `Open` or `Absent` row, or an id the
    * registry does not have.
    *
    * A lowering arm consulting a difference the registry says nobody handles is a registry that has
    * stopped describing the code. The practical effect is what makes a wiring commit land as a
    * pair: it flips the row's status IN THE SAME CHANGE, or it does not go green. Reported on the
    * `undischarged` lane rather than in one of its own, because both are the same statement — the
    * registry and the arms disagree — and a second lane would let one of them be read without the
    * other. */
  def consultsOpenRows(log: CatalogLog): List[CheckReport.Finding] =
    log.reached.toList.sortBy(_.toString).flatMap { id =>
      Differences.byId.get(id) match
        case scala.None =>
          Some(CheckReport.Finding(Undischarged, "unknown-row", id.toString, "-", 0,
            "cited by a consult and is not in the registry — a renamed or deleted row leaves this " +
              "behind, and an arm claiming to honour a difference nobody wrote down is the worst " +
              "shape it can take"))
        case Some(d) =>
          val (path, line) = at(id, log)
          d.status match
            case Status.Open =>
              Some(CheckReport.Finding(Undischarged, "consults-open", id.toString, path, line,
                s"consulted while the registry says nobody handles it — flip the status in the " +
                  s"commit that wires it: ${d.title}"))
            case Status.Absent(w) =>
              Some(CheckReport.Finding(Undischarged, "consults-absent", id.toString, path, line,
                s"consulted while the frontend has no model for it ($w): ${d.title}"))
            case _ => scala.None
    }

  /** every finding the `undischarged` lane records — the holes AND rule (ii)'s violations. */
  def undischargedAll(log: CatalogLog): List[CheckReport.Finding] =
    undischarged(log) ++ consultsOpenRows(log)

  /** the row's own status, as the finding's `kind`, so a reader can sort the lane by how much is
    * claimed for the branch that was not reached. */
  private def kindOf(d: Difference): String = d.status match
    case Status.Handled    => "handled"
    case Status.Partial(_) => "partial"
    case Status.Open       => "open"
    case Status.Absent(_)  => "absent"
    case Status.Refused(_) => "refused"
    case Status.NonDiff(_) => "non-diff"

  /** one line per catalog row — `catalog.tsv`'s body. */
  def tsv(log: CatalogLog): List[String] =
    log.rows.map { r =>
      val attaches = r.attaches match
        case Attaches.Lowered(k, disp)  => s"lowering:$k/$disp"
        case Attaches.Cited(ph)         => s"phase:$ph"
        case Attaches.Unmechanised(_)   => "unmechanised"
        case Attaches.NoObligation(_)   => "none"
      s"${r.id}\t${kindOf(Differences.byId(r.id))}\t$attaches\t${r.consulted}\t${r.fired}\t${r.declarations}"
    }

  val TsvHeader = "#id\tstatus\tattaches\tconsulted\tfired\tdeclarations"
