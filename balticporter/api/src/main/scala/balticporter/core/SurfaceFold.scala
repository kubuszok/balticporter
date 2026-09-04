package balticporter.core

import balticporter.tir.Phase

/** How one parameterised phase's POLICY composes with a NEARER manifest's instance of the same
  * phase — the merge contract (DESIGN.md §8.13, `ENGINE-LIMITS.md` D9). `extendedBy` concatenates
  * `surface` phases by identity, so two instances of one NAME never merge without this — fatal
  * `SurfaceDivergence`. The PHASE answers (merge semantics differ per phase); three obligations:
  * preserve both inputs' own keys or refuse, be PURE/DETERMINISTIC, move `surfaceFingerprint`. */
trait MergeablePolicy extends SurfacePolicy:
  self: Phase =>

  /** Merge `later` — a NEARER manifest's instance of this same phase — into this one. `Left(why)`
    * REFUSES (same key, different value); a refused pair stays in the pipeline, reported. @return
    * the merged phase, and the shared-surface SUBJECTS `later` contributed that this instance did
    * not already hold — what the `governs` screen reads. */
  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged]

  /** Every shared-surface SUBJECT this instance's policy is keyed on — each key's leading FQN, via
    * [[MergeablePolicy.subjectOf]]. Makes the `governs` screen reachable without a MERGE: a
    * dependent declaring a phase its base lacks was previously unscreened. Over-approximate rather
    * than under — a harmless subject costs a refusal, an omitted one is a hole. */
  def subjects: Set[String]

object MergeablePolicy:

  /** @param phase the merged instance — a NEW value; neither input is mutated.
    * @param added the subjects the later instance ADDS, as FQNs. Empty is a statement: "nothing
    *              this instance contributes is keyed on a name of the shared surface". */
  final case class Merged(phase: Phase, added: Set[String])

  /** The shared-surface SUBJECT a policy key names: its leading FQN, cut at `#`. One body, since
    * `ManifestAgreement` already reads a `dropMethods` key the same way ([[PortManifest.covers]]'s
    * argument). A key that is already a bare type FQN is its own subject. */
  def subjectOf(key: String): String = key.takeWhile(_ != '#').trim

/** What folding a manifest's policy chain into ONE pipeline decided — derived, never configuration.
  * @param phases the effective pipeline (a merged phase sits at the BASE's position) @param
  *   absorbed fingerprints consumed by a merge @param refusals same-name pairs that could NOT
  *   merge, with why @param ownKeys phase → subjects the folded manifest contributed @param
  *   intrusions subjects added inside a base's claim — CANDIDATES screened against its published map. */
final case class SurfaceFold(
    phases: List[Phase],
    absorbed: Set[String] = Set.empty,
    refusals: List[SurfaceFold.Refusal] = Nil,
    ownKeys: Map[String, Set[String]] = Map.empty,
    intrusions: List[SurfaceFold.Intrusion] = Nil,
)

object SurfaceFold:

  /** Why a same-name pair was not merged. Kept apart because the READER's next action differs:
    * write a merge contract, or reconcile two values. An INTRUSION is not here (`ENGINE-LIMITS.md`
    * CT9 Face A): it is a statement about a base's OUTPUT, not about two policies failing to
    * compose, and does not leave two instances in the pipeline. See [[Intrusion]]. */
  enum Cause:
    /** the phase declares no [[MergeablePolicy]] and the two policies are DEMONSTRABLY different —
      * the pre-merge behaviour, unchanged. */
    case NoContract
    /** the phase declares no [[MergeablePolicy]] and is not even a [[SurfacePolicy]], so the two
      * policies cannot be COMPARED, let alone composed. See [[of]]. */
    case Unverifiable
    /** the phase's own merge refused: same key, different value. */
    case Conflict

  final case class Refusal(phase: String, cause: Cause, why: String)

  /** What the fold decided about ONE same-name pair. Three answers and not two, because "equal
    * policies" is neither a merge nor a refusal: it is the pre-CT9 by-name dedup, which
    * `Pipeline.order` used to perform by accident and no longer does. */
  private enum Outcome:
    /** the phase's own `mergedWith` composed them. */
    case Merged(phase: Phase, added: Set[String])
    /** the two instances are the SAME POLICY, provably. ONE of them runs — the base's, in the
      * base's position — and the later one is dropped, which is exactly what the pre-CT9 pipeline
      * did and what nothing has done since `Pipeline.order` started ordering INSTANCES. */
    case Deduplicated
    /** two instances stay in the pipeline, and the pair is a fatal `SurfaceDivergence`. */
    case Kept(refusal: Refusal)

  /** A subject a nearer manifest ADDS inside `base`'s `governs` claim, which `base`'s own manifest
    * does not account for — a CANDIDATE for `SurfaceIntrusion`, screened against `base`'s published
    * map by the layer that holds one.
    *
    * @param why the sentence the finding carries, minus the evidence the map supplies. */
  final case class Intrusion(phase: String, base: String, subject: String, why: String)

  /** THE fold. `chain` is furthest base first, `owner` is the manifest being folded (the last
    * element, whose contributions [[SurfaceFold.ownKeys]] records). Identity dedup kept from the
    * pre-merge code path: inheriting one manifest through two paths runs its phases once. */
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
            val outcome: Outcome = earlier match
              case a: MergeablePolicy => a.mergedWith(p) match
                case Right(MergeablePolicy.Merged(merged, added)) =>
                  // The merge STANDS whatever the screen says. An intrusion is not a failure to
                  // compose two policies — it is a statement about what the BASE emits, which only
                  // the layer holding the base's published map can make, and a confirmed one stops
                  // the run before any phase runs rather than by leaving a pipeline half-composed
                  // (`ENGINE-LIMITS.md` CT9 Face A).
                  intrusions = intrusions ++ candidates(seen, p.name, added)
                  Outcome.Merged(merged, added)
                case Left(why) => Outcome.Kept(Refusal(p.name, Cause.Conflict, why))
              case _ => noContract(earlier, p)
            outcome match
              case Outcome.Merged(merged, added) =>
                absorbed = absorbed + PortManifest.fingerprint(earlier) + PortManifest.fingerprint(p)
                phases   = phases.updated(i, merged)
                if m eq owner then
                  ownKeys = ownKeys.updated(p.name, ownKeys.getOrElse(p.name, Set.empty) ++ added)
              case Outcome.Deduplicated =>
                // `p` is NOT appended: one instance runs, which is the whole content of this answer.
                ()
              case Outcome.Kept(r) =>
                refusals = refusals :+ r
                phases   = phases :+ p

    SurfaceFold(phases.toList, absorbed, refusals.distinct.toList, ownKeys, intrusions.distinct.toList)

  /** A same-name pair whose EARLIER instance declares no [[MergeablePolicy]] — the arm that had to
    * change once `Pipeline.order` began ordering INSTANCES (running an equal pair twice is harmless
    * only if the phase happens to be idempotent). Equal collapses to ONE instance at the base's
    * position; "equal" is only sayable of a [[SurfacePolicy]] (name-only fingerprint compares
    * unequal configs equal), so a non-`SurfacePolicy` pair REFUSES loudly (`ENGINE-LIMITS.md` CT9 Face B). */
  private def noContract(earlier: Phase, later: Phase): Outcome = (earlier, later) match
    case (_: SurfacePolicy, _: SurfacePolicy) =>
      if PortManifest.fingerprint(earlier) == PortManifest.fingerprint(later) then Outcome.Deduplicated
      else Outcome.Kept(Refusal(later.name, Cause.NoContract,
        s"`${later.name}` declares no `MergeablePolicy`, so two instances of it cannot be " +
          "composed: the engine does not know whether its policy is a union, an ordered list or a " +
          "first-match table"))
    case _ =>
      Outcome.Kept(Refusal(later.name, Cause.Unverifiable,
        s"`${later.name}` implements neither `MergeablePolicy` nor `SurfacePolicy`, so two " +
          "instances of it can neither be composed NOR COMPARED: `PortManifest.fingerprint` falls " +
          "back to the phase NAME, under which two different configurations render identically. " +
          "Equality cannot be verified, so it is not assumed — both instances would otherwise run " +
          "over one program with nothing able to say whether that is one policy or two"))

  /** The `governs` screen, MANIFEST HALF: which subjects this module adds could edit a BASE's
    * shared surface? Not a bare prefix (a base's claim holds types it DROPS, and redirecting into
    * a dependent's replacement is legitimate) — a candidate is a subject the base's OWN policy does
    * not account for. "Nothing stands at that name" is the criterion, not "is dropped"
    * (`PortManifest.shipsInjectionAt`, §4.56). Screened against the base's published port map. */
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
