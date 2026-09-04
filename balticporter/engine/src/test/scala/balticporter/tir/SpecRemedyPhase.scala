package balticporter.tir

import balticporter.catalog.FixKind

/** A PHASE THAT OFFERS A REMEDY, for specs — the fixture the whole `resolutions` loop is asserted
  * through. */
final class SpecRemedyPhase(
    val lane: String = SpecRemedyPhase.Lane,
    val kind: String = SpecRemedyPhase.Kind,
    val remedy: Remedy = SpecRemedyPhase.Noop,
) extends Phase, RemedySource, PolicyBound:

  def name: String = "spec-remedy"

  def remedies: List[Remedy] = List(remedy)

  /** the run's plan, taken through the binder every `PolicyBound` phase is already handed — the seam
    * a real phase uses, so the fixture exercises the wiring rather than a shortcut around it. */
  private var plan: ResolutionPlan = ResolutionPlan.empty

  def bindPolicy(binder: PolicyBinder): Unit = plan = binder.resolutions

  /** every DECLARATION this program owns, asked once. A phase with a real remedy would ask at each
    * residue site it was about to file; asking per declaration here is the same question with the
    * denominator left out, which is all a fixture needs. */
  override def run(program: Program): Program =
    program.symbols.all.filter(s => program.owns(s.id)).foreach { s =>
      plan.selected(s.id, lane, kind).foreach { r =>
        plan.applied(r, s.fullName, s.id, Decision.originOf(program, s.id),
                     "spec fixture: selection recorded, tree unchanged")
      }
    }
    program

object SpecRemedyPhase:
  val Lane: String = "spec-lane"
  val Kind: String = "spec-kind"

  val Noop: Remedy = Remedy(
    id = "spec-noop", lane = Lane, kind = Kind, emissionAffecting = true,
    fix = FixKind.Parameterised, what = "record the selection and leave the body alone")

  /** a SECOND remedy on the same lane, so "a selection narrows to the id the port wrote" is
    * assertable rather than true by there being only one. */
  val Other: Remedy = Noop.copy(id = "spec-other", what = "the alternative nobody picked")
