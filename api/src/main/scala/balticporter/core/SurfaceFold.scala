package balticporter.core

import balticporter.tir.Phase

/** How one parameterised phase's POLICY composes with a NEARER manifest's instance of the same
  * phase — the merge contract (DESIGN.md §8.13, closing `ENGINE-LIMITS.md` D9).
  *
  * ==The gap this fills==
  * [[PortManifest.extendedBy]] unions the drops and the renames key by key. It CONCATENATES the
  * `surface` phases and deduplicates them by identity — and a phase's policy is a constructor
  * argument, so two instances holding two halves of one table never merged. One phase NAME carrying
  * two configurations in one effective pipeline is a fatal `SurfaceDivergence` whether the tables
  * overlap or not, which made a (b) phase configured in a BASE manifest one that no dependent could
  * ever configure: adding the first one to a base that already had dependents using it was a change
  * that could not land.
  *
  * ==Why the phase answers, and not the engine==
  * Because the merge is not a union. A `Map` of independent keys unions; an ORDERED list does not
  * (the order is policy); a first-match table does not (a later entry is shadowed, not added); a
  * `RuleScope` composes one way for `Only` and the opposite way for `Everywhere`. An engine-side
  * rule would be right for one phase and silently wrong for the next — CLAUDE.md §1's failure mode
  * with the policy in the engine's hands.
  *
  * A phase that declares nothing keeps the pre-merge behaviour exactly: both instances stay in the
  * effective pipeline and the pair is reported. That is the correct answer for a composition nobody
  * has designed.
  *
  * ==Three obligations on an implementor, none of them checkable from outside==
  *   - the result PRESERVES BOTH INPUTS' behaviour on their own keys, or refuses. `SurfaceMissing`
  *     stops firing for the base's absorbed instance on the strength of this promise;
  *   - the merge is PURE and DETERMINISTIC. The base folds its own chain and every dependent
  *     re-folds it, and the published-map freshness comparison is between those two computations;
  *   - [[SurfacePolicy.surfaceFingerprint]] MOVES whenever the merged table differs from either
  *     input, or a merge that changed the emitted surface publishes a digest saying it did not.
  */
trait MergeablePolicy extends SurfacePolicy:
  self: Phase =>

  /** Merge `later` — a NEARER manifest's instance of this same phase — into this one.
    *
    * `Left(why)` REFUSES: the two policies disagree (same key, different value), and `why` is the
    * sentence the finding carries. A refused pair stays in the pipeline and is reported, which is
    * the pre-merge behaviour.
    *
    * @return the merged phase, and the shared-surface SUBJECTS `later` contributed that this
    *         instance did not already hold — what the `governs` screen reads, and what scopes the
    *         merged phase's policy findings back to the manifest that declared them.
    */
  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged]

  /** Every shared-surface SUBJECT this instance's policy is keyed on — each key's leading FQN,
    * through [[MergeablePolicy.subjectOf]], so a phase does not spell the cut a second time.
    *
    * '''This is what makes the `governs` screen reachable without a merge.''' The screen used to
    * read only the subjects a MERGE contributed, so a dependent declaring a phase its base does not
    * have was appended to the pipeline unscreened: one instance, no divergence, no merge, and every
    * type the base emits mechanically available to re-point. `ManifestAgreement`'s
    * `SurfaceIntrusion` text states the rule unconditionally; this is the accessor that lets the run
    * enforce it unconditionally.
    *
    * Over-approximate rather than under: a subject listed here that turns out to be harmless costs
    * a refusal a port answers by naming the base's drop, while one omitted is a hole exactly where
    * the screen exists.
    */
  def subjects: Set[String]

object MergeablePolicy:

  /** @param phase the merged instance — a NEW value; neither input is mutated.
    * @param added the subjects the later instance ADDS, as FQNs. Empty is a statement: "nothing
    *              this instance contributes is keyed on a name of the shared surface". */
  final case class Merged(phase: Phase, added: Set[String])

  /** The shared-surface SUBJECT a policy key names: its leading FQN, cut at `#`.
    *
    * One body, because `ManifestAgreement` already reads a `dropMethods` key the same way and a
    * rule this easy to get wrong must have exactly one copy (the argument [[PortManifest.covers]]
    * carries about the separator cut). A key that is already a bare type FQN is its own subject.
    */
  def subjectOf(key: String): String = key.takeWhile(_ != '#').trim

/** What folding a manifest's policy chain into ONE pipeline decided.
  *
  * Everything here is derived from the chain and the phases' own [[MergeablePolicy]] answers;
  * nothing in it is configuration. `ManifestAgreement` turns [[refusals]] into findings, reads
  * [[absorbed]] so that a base phase a dependent MERGED is not also reported as missing, and the
  * run reads [[ownKeys]] to hold a merged phase's policy findings to the manifest that declared
  * them (DESIGN.md §8.13).
  *
  * @param phases   the effective pipeline. A merged phase sits at the BASE's position — a merge
  *                 changes a table, never an ordering.
  * @param absorbed the `PortManifest.fingerprint` of every instance a successful merge consumed.
  * @param refusals every same-name pair the fold could NOT merge, with the reason.
  * @param ownKeys  phase name → the subjects the FOLDED manifest itself contributed to a merge.
  *                 Absent for a phase that was not merged, which is the "no filter" answer.
  * @param intrusions every subject a nearer manifest ADDS inside a base's claim that the base's own
  *                 MANIFEST does not account for — CANDIDATES, not findings. Whether the base
  *                 actually EMITS anything at the name is a fact about its OUTPUT, which a pure
  *                 function of manifests cannot know; `ManifestAgreement` screens these against the
  *                 base's published port map and derives the fatal finding (DESIGN.md §8.13).
  */
final case class SurfaceFold(
    phases: List[Phase],
    absorbed: Set[String] = Set.empty,
    refusals: List[SurfaceFold.Refusal] = Nil,
    ownKeys: Map[String, Set[String]] = Map.empty,
    intrusions: List[SurfaceFold.Intrusion] = Nil,
)

object SurfaceFold:

  /** Why a same-name pair was not merged. The two are kept apart because the READER's next action
    * differs: write a merge contract, or reconcile two values.
    *
    * An INTRUSION is not here, and that is the correction `ENGINE-LIMITS.md` CT9 Face A made: it is
    * not a statement about two policies failing to compose, it is a statement about a base's
    * OUTPUT, and it does not leave two instances in the pipeline. See [[Intrusion]]. */
  enum Cause:
    /** the phase declares no [[MergeablePolicy]] — the pre-merge behaviour, unchanged. */
    case NoContract
    /** the phase's own merge refused: same key, different value. */
    case Conflict

  final case class Refusal(phase: String, cause: Cause, why: String)

  /** A subject a nearer manifest ADDS inside `base`'s `governs` claim, which `base`'s own manifest
    * does not account for — a CANDIDATE for `SurfaceIntrusion`, screened against `base`'s published
    * map by the layer that holds one.
    *
    * @param why the sentence the finding carries, minus the evidence the map supplies. */
  final case class Intrusion(phase: String, base: String, subject: String, why: String)

  /** THE fold. `chain` is furthest base first, `owner` is the manifest being folded (the last
    * element, and the one whose contributions [[SurfaceFold.ownKeys]] records).
    *
    * Identity dedup is kept from the pre-merge code path: inheriting one manifest through two paths
    * runs its phases once.
    */
  def of(chain: List[PortManifest], owner: PortManifest): SurfaceFold =
    var phases     = Vector.empty[Phase]
    var absorbed   = Set.empty[String]
    var refusals   = Vector.empty[Refusal]
    var ownKeys    = Map.empty[String, Set[String]]
    var intrusions = Vector.empty[Intrusion]

    for
      // each manifest of the chain, paired with the manifests that come BEFORE it — the bases whose
      // shared surface this one's additions are screened against
      (m, seen) <- chain.indices.toList.map(i => chain(i) -> chain.take(i))
      p         <- m.surface
    do
      if !phases.exists(_ eq p) then
        phases.indexWhere(_.name == p.name) match
          // NO same-name instance to compose with — and this is the arm the screen used to miss.
          // A dependent-declared phase with no counterpart in any base reaches the pipeline whole,
          // so EVERY subject it holds is a subject it adds; screen all of them. The phase joins the
          // pipeline either way, and the fatal finding `ManifestAgreement` derives from a CONFIRMED
          // candidate is what stops the run.
          case -1 =>
            intrusions = intrusions ++ (p match
              case a: MergeablePolicy => candidates(seen, p.name, a.subjects)
              case _                  => Nil)
            phases = phases :+ p
          case i  =>
            val earlier = phases(i)
            val outcome: Either[Option[Refusal], (Phase, Set[String])] = earlier match
              case a: MergeablePolicy => a.mergedWith(p) match
                case Right(MergeablePolicy.Merged(merged, added)) =>
                  // The merge STANDS whatever the screen says. An intrusion is not a failure to
                  // compose two policies — it is a statement about what the BASE emits, which only
                  // the layer holding the base's published map can make, and a confirmed one stops
                  // the run before any phase runs rather than by leaving a pipeline half-composed
                  // (`ENGINE-LIMITS.md` CT9 Face A).
                  intrusions = intrusions ++ candidates(seen, p.name, added)
                  Right(merged -> added)
                case Left(why) => Left(Some(Refusal(p.name, Cause.Conflict, why)))
              case _ =>
                // Equal policy is not drift and reported nothing before this existed, so it records
                // no refusal either — the refusal list only ever explains a finding.
                Left(Option.when(PortManifest.fingerprint(earlier) != PortManifest.fingerprint(p))(
                  Refusal(p.name, Cause.NoContract,
                    s"`${p.name}` declares no `MergeablePolicy`, so two instances of it cannot be " +
                      "composed: the engine does not know whether its policy is a union, an " +
                      "ordered list or a first-match table")))
            outcome match
              case Right((merged, added)) =>
                absorbed = absorbed + PortManifest.fingerprint(earlier) + PortManifest.fingerprint(p)
                phases   = phases.updated(i, merged)
                if m eq owner then
                  ownKeys = ownKeys.updated(p.name, ownKeys.getOrElse(p.name, Set.empty) ++ added)
              case Left(r) =>
                refusals = refusals ++ r
                phases   = phases :+ p

    SurfaceFold(phases.toList, absorbed, refusals.distinct.toList, ownKeys, intrusions.distinct.toList)

  /** The `governs` screen, MANIFEST HALF: which subjects this module adds could edit a BASE's
    * shared surface?
    *
    * The criterion is NOT a bare prefix — a base's claimed namespace holds types the base DROPS,
    * and re-pointing references at a replacement the dependent ships is the whole purpose of a
    * redirect. What is a candidate is a subject inside a base's claim that the base's own policy
    * does not account for: a dependent quietly re-pointing every reference to something the base
    * ships produces two ports that each compile alone and cannot compile together.
    *
    * '''A DROP is not the criterion; "nothing stands at that name" is.''' The two coincide only for
    * a drop the base leaves EMPTY. A drop WITH an injection is the other half of §1.5's asymmetry:
    * the base ships a replacement file at that FQN, and that replacement is shared surface exactly
    * as an emitted class is — a dependent re-pointing its references at a type of its own would
    * compile alone and could not compile against the base, which is the same failure the screen
    * exists for and the one the drop test silently admitted. So the admission asks the base whether
    * it SHIPS anything at the name (`PortManifest.shipsInjectionAt`, which translates the upstream
    * key through the base's own renames — §4.56).
    *
    * '''…and a drop is a statement about a type the base HAS.''' It says nothing about a name the
    * base has never heard of, which is the shape a library's own TEST MODULE is in: its suites are
    * declared inside the base's packages, so no prefix separates the two modules and this screen
    * calls a type the base never parsed one it "emits mechanically" (`ENGINE-LIMITS.md` CT9 Face A).
    * That half of the question is about the base's OUTPUT and a manifest cannot answer it, so these
    * are CANDIDATES: `ManifestAgreement` screens each against the base's published port map, and a
    * base with no usable map falls back to exactly the answer this function gives.
    *
    * §4.56's cut applies to the claim, through `PortManifest.covers`.
    */
  private def candidates(bases: List[PortManifest], phase: String, added: Set[String]): List[Intrusion] =
    def admitted(b: PortManifest, subject: String): Boolean =
      b.effectiveDropTypes.contains(subject) && !b.shipsInjectionAt(subject)
    for
      subject <- added.toList.sorted
      b       <- bases
      if b.claims(subject) && !admitted(b, subject)
    yield
      val why =
        if b.effectiveDropTypes.contains(subject) then
          s"""which `${b.name}` DROPS and REPLACES — its own `inject` supplies """ +
            s""""${b.renamed(subject)}", so the replacement is shared surface exactly as an """ +
            "emitted class is"
        else s"which `${b.name}` emits"
      Intrusion(phase, b.name, subject,
        s"""this module's `$phase` adds "$subject", which is inside `${b.name}`'s declared """ +
          s"namespace and $why — it would let a dependent re-shape the SHARED surface, so the two " +
          "modules would each compile alone and could not compile together. A subject the base " +
          "leaves EMPTY is the allowed case: nothing stands at that name in the base's output")
