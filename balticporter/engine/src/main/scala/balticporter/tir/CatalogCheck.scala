package balticporter.tir

import balticporter.catalog.{Attaches, CatalogLog, Difference, Differences, DiffId, Status}

/** Five coverage lanes over a [[CatalogLog]]: `consulted`, `unreached` (mechanised rows only),
  * `unmechanised` (registry-derived), `undischarged` (one finding per row), and `uncited`
  * (registry-derived, never a failing assertion). Counted here, fatal in the testkit. */
object CatalogCheck:

  val Consulted    = "catalog(consulted)"
  val Unreached    = "catalog(unreached)"
  val Unmechanised = "catalog(unmechanised)"
  val Undischarged = "catalog(undischarged)"
  val Uncited      = "catalog(uncited)"

  /** §1(a) classification — every catalog hole is an engine gap. */
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
        val where = Differences.leaves(d.attaches).map {
          case Attaches.Lowered(k, disp) => s"lowering $k/$disp"
          case Attaches.Rendered(k)      => s"rendering $k"
          case Attaches.LoweredType(k)   => s"lowering-type $k"
          case Attaches.RenderedType(k)  => s"rendering-type $k"
          case Attaches.Cited(ph)        => s"phase $ph"
          case Attaches.Unmechanised(_)  => "unmechanised"
          case Attaches.NoObligation(_)  => "none"
          case _: Attaches.Both          => "?" // `leaves` never yields one
        }.mkString(" + ")
        CheckReport.Finding(Consulted, "reached", d.id.toString, path, line,
          s"$where — consulted ${log.consulted(d.id)}, fired ${log.fired(d.id)}, " +
            s"declarations ${log.declarations(d.id)}: ${d.title}")
      }

  /** Mechanised rows this port never reached. Aggregated across the corpus by `just catalog-coverage`. */
  def unreached(log: CatalogLog): List[CheckReport.Finding] =
    Differences.mechanised
      .filter(d => log.consulted(d.id) == 0 && log.declarations(d.id) == 0)
      .map(d => CheckReport.Finding(Unreached, kindOf(d), d.id.toString, "-", 0,
        s"never reached by this port — ${d.evidence}: ${d.title}"))

  /** rows nothing is instrumented to answer for. Derived from the REGISTRY, not from the run. */
  def unmechanised: List[CheckReport.Finding] =
    Differences.unmechanised.map { d =>
      val why = Differences.leaves(d.attaches).collect { case Attaches.Unmechanised(w) => w }.mkString("; ")
      CheckReport.Finding(Unmechanised, kindOf(d), d.id.toString, "-", 0, s"$why: ${d.title}")
    }

  /** Rows with no Scala-side normative citation. Registry-derived, same on every port.
    * Never a failing assertion — a spec failing here incentivises inventing citations. */
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

  /** Consults citing an `Open`/`Absent` row or an unknown id. Reported on the `undischarged` lane. */
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

  /** The row's status as the finding's `kind`. */
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
      val attaches = Differences.leaves(r.attaches).map {
        case Attaches.Lowered(k, disp)  => s"lowering:$k/$disp"
        case Attaches.Rendered(k)       => s"rendering:$k"
        case Attaches.LoweredType(k)    => s"lowering-type:$k"
        case Attaches.RenderedType(k)   => s"rendering-type:$k"
        case Attaches.Cited(ph)         => s"phase:$ph"
        case Attaches.Unmechanised(_)   => "unmechanised"
        case Attaches.NoObligation(_)   => "none"
        case _: Attaches.Both           => "?" // `leaves` never yields one
      }.mkString("+")
      s"${r.id}\t${kindOf(Differences.byId(r.id))}\t$attaches\t${r.consulted}\t${r.fired}\t${r.declarations}"
    }

  val TsvHeader = "#id\tstatus\tattaches\tconsulted\tfired\tdeclarations"
