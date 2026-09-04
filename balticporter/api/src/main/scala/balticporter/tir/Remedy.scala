package balticporter.tir

import balticporter.catalog.FixKind

/** THE MENU — one named thing the engine can DO at one location, offered by the phase or check
  * that can do it, for a difference with no single right answer. The phase/check declares
  * alternatives; a port SELECTS one ([[PortManifest.resolutions]]); the engine applies and counts
  * both halves ([[Resolution]]). @field id GLOBALLY UNIQUE @field lane/kind the residue drained
  * @field emissionAffecting puts it in §1.5's MUST-agree column @field fix owning repository (`FixKind`). */
final case class Remedy(
    /** the globally-unique, kebab-case slug a manifest writes. Published API — see [[Remedy.badId]]
      * for the grammar and why anything outside it is refused rather than normalised. */
    id: String,
    /** the `CheckReport` check whose residue this remedy drains — `IdiomCheck.Refused`, not a string
      * literal, so a renamed lane is a compile error rather than a silently unwired claim. */
    lane: String,
    /** the finding KIND within [[lane]] this remedy answers. */
    kind: String,
    /** does applying it change EMITTED TEXT? See the class doc — this is what decides whether a
      * selection is shared surface. */
    emissionAffecting: Boolean,
    /** which of §1's three kinds the REMEDY is — where its code lives. */
    fix: FixKind,
    /** one sentence: what the port gets if it picks this. Rendered in the menu and in the porter
      * note beside the code, so it is written for a reader who is holding neither. */
    what: String,
    /** the OTHER kinds in [[lane]] this same remedy also answers. Empty by default (one-kind
      * remedy). A lane may split ONE SITE into several rows (`overload-risk` files up to three at
      * one call, JLS 15.12.2's three phase boundaries), and one act answers all of them — without
      * this a member holding two kinds could answer only one, leaving undrainable residue
      * (`DESIGN.md` §8.16). Does NOT widen across lanes, keeping the accounting one number. */
    alsoKinds: List[String] = Nil,
    /** WHAT KIND OF THING THE SELECTION KEY NAMES — see [[Remedy.Subject]]. Defaulted to
      * `OwnedMember`, which is what every remedy whose residue sits at a declaration this run emits
      * wants, and which is therefore the answer nothing has to state. */
    subject: Remedy.Subject = Remedy.Subject.OwnedMember,
):
  /** the `lane(kind)` pair, as the residue count spells it — every kind this remedy answers. */
  def target: String =
    if alsoKinds.isEmpty then s"$lane($kind)"
    else s"$lane(${(kind :: alsoKinds).mkString("|")})"

  /** IS THIS THE ROW IN FRONT OF ME? — the one question [[ResolutionPlan.selected]] asks.
    *
    * A method rather than two equality tests at the caller, so [[alsoKinds]] cannot become a field
    * one consulting site reads and another silently does not (`CLAUDE.md` §4.56's fast-path guard,
    * one type over). */
  def answers(l: String, k: String): Boolean =
    l == lane && (kind == Remedy.AnyKind || k == kind || alsoKinds.contains(k))

  /** every kind this remedy names, [[alsoKinds]] included. `AnyKind` is IN it and is not a kind —
    * [[overlaps]] is the only reader and treats it as the whole lane. */
  def kinds: Set[String] = (kind :: alsoKinds).toSet

  /** COULD THIS REMEDY AND THAT ONE ANSWER THE SAME ROW? — not "are they on the same lane".
    * [[ResolutionPlan.selected]] dispatches by `(lane, kind)`, so two selections with DISJOINT
    * kinds on one lane are both live (e.g. `heap-pollution`'s `Acknowledged`/`Unacknowledged`);
    * lane equality alone would report `ConflictingSelection` with no way to comply. `AnyKind`
    * overlaps everything on its lane by construction. */
  def overlaps(other: Remedy): Boolean =
    lane == other.lane && (kind == Remedy.AnyKind || other.kind == Remedy.AnyKind ||
      kinds.exists(other.kinds))

  def render: String = s"$id — $what  [drains $target; ${fix.section}]"

object Remedy:

  /** the [[Remedy.kind]] of a remedy that answers EVERY kind in its lane — [[alsoKinds]] at a
    * cardinality enumeration cannot reach: `portability(emitted)` files under an offending API's
    * FQN, an OPEN set with hundreds of values, so a per-location remedy there is about the LOCATION
    * and cannot enumerate. Read only in [[answers]], so [[drain]]/[[appliedAt]] honour it uniformly. */
  val AnyKind: String = "*"

  /** WHAT A SELECTION KEY FOR THIS REMEDY NAMES. `DESIGN.md` §8.16's original answer — a
    * `MemberKey` naming a declaration the run owns — fails at a residue sitting at a TYPE (no
    * member to name, e.g. `ENGINE-LIMITS.md` CT7) or at a member this program does NOT declare (an
    * egress row, deduplicated BY CALLEE). So the SUBJECT KIND is the remedy's own to declare, and
    * the plan binds each key accordingly (`ResolutionPlan.of`) — no `ExternalType` yet: nothing produces it. */
  enum Subject(val ownership: Ownership, val isType: Boolean):
    /** `owner#member`, naming a declaration this run emits — the default and the common case. */
    case OwnedMember    extends Subject(Ownership.Owned, false)
    /** a bare type FQN, for a residue that is a fact about the TYPE and not about one of its
      * members. Bound through `PolicyBinder.bindType`, so a `#` in such a key is `Malformed` with
      * that seam's own sentence rather than this one's. */
    case OwnedType      extends Subject(Ownership.Owned, true)
    /** `owner#member` naming a member this program REFERENCES and does not declare — the one shape
      * `Ownership.External` exists for, and the one where `ExternalOnly` is the WRONG refusal. */
    case ExternalMember extends Subject(Ownership.External, false)

  /** Why an id is not an id. Lower-case kebab, exactly as [[TransformFactory.name]] is, and for the
    * same reason: it is what a `.conf` writes and what an error message lists, so it must survive a
    * round trip through a config file and a TSV column without quoting. Refused rather than
    * normalised — a vocabulary that accepted `Wrap_Checked` and answered to `wrap-checked` would
    * publish two spellings of one published name and a port could not tell which one it wrote. */
  def badId(id: String): Option[String] =
    if id.isEmpty then Some("an empty remedy id names nothing")
    else if !id.forall(c => (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-') then
      Some(s"'$id' is not lower-case kebab: a remedy id is [a-z0-9] and `-`, and it is published API")
    else if id.startsWith("-") || id.endsWith("-") || id.contains("--") then
      Some(s"'$id' has a leading, trailing or doubled `-`")
    else if !id.head.isLetter then Some(s"'$id' does not start with a letter")
    else scala.None

/** A PHASE OR CHECK THAT OFFERS REMEDIES. A trait beside [[Phase]] rather than a field on it, for
  * [[IdiomPhase]]'s reason: a CHECK is not a phase at all, and half the residue lanes a remedy
  * could drain are plain objects the orchestrator calls. The vocabulary a RUN can act on is
  * assembled from the sources the run actually holds. */
trait RemedySource:
  /** every remedy this source can carry out. Empty is the honest answer for a source that offers
    * none, and is the default nothing has to state. */
  def remedies: List[Remedy]

/** EVERY REMEDY VISIBLE AT ONE MOMENT, by id — assembled, never enumerated in a table. Two sets:
  * KNOWN (every remedy the engine/classpath ship — a `.conf` naming an id outside it is a
  * [[ConfigError]] at load) and ACTIVE (this run's sources — a known-but-inactive id is a policy
  * finding, never silence). Refuses a duplicate id rather than picking one, like `TransformRegistry`. */
final class RemedyVocabulary private (
    val byId: Map[String, Remedy],
    /** id → the class name of the source that declared it, for the duplicate error and for the
      * "your phase is not in this pipeline" finding. */
    val declaredBy: Map[String, String],
):
  def ids: List[String]               = byId.keys.toList.sorted
  def get(id: String): Option[Remedy] = byId.get(id)
  def contains(id: String): Boolean   = byId.contains(id)
  def isEmpty: Boolean                = byId.isEmpty
  def all: List[Remedy]               = ids.flatMap(byId.get)

  /** the source that declared `id`, for a message that tells its reader what to enable. */
  def sourceOf(id: String): String = declaredBy.getOrElse(id, "?")

  /** union, with the same duplicate refusal — how a load-time KNOWN set is built from the engine's
    * own catalogue plus whatever the classpath's factories declare. */
  def ++(that: RemedyVocabulary): RemedyVocabulary =
    RemedyVocabulary.merge(List(this, that))

  /** the menu, one line per remedy, for stdout and for an error that lists the alternatives. */
  def render: String =
    if isEmpty then "  none" else all.map("  " + _.render).mkString("\n")

object RemedyVocabulary:

  val empty: RemedyVocabulary = new RemedyVocabulary(Map.empty, Map.empty)

  /** ONE source's remedies, tagged with who declared them. */
  def of(source: RemedySource): RemedyVocabulary =
    val who = source.getClass.getName
    build(source.remedies.map(r => r -> who))

  def from(sources: Iterable[RemedySource]): RemedyVocabulary =
    build(sources.iterator.flatMap(s => s.remedies.map(_ -> s.getClass.getName)).toList)

  /** …for a declarer that is not an instance — a `TransformFactory` speaking for the phase it would
    * build, which is how the KNOWN set can be complete without constructing anything. */
  def declared(who: String, remedies: List[Remedy]): RemedyVocabulary =
    build(remedies.map(_ -> who))

  private def merge(vs: List[RemedyVocabulary]): RemedyVocabulary =
    build(vs.flatMap(v => v.all.map(r => r -> v.sourceOf(r.id))))

  private def build(pairs: List[(Remedy, String)]): RemedyVocabulary =
    pairs.foreach { (r, who) =>
      Remedy.badId(r.id).foreach(why =>
        throw ConfigError(s"remedy declared by $who", why))
    }
    // A duplicate is refused and never resolved: an id is published API, and a port that wrote it is
    // entitled to know which mechanism answered. The one exception is the SAME remedy declared twice
    // — two vocabularies unioned after both saw one source — which is not a disagreement at all.
    val grouped = pairs.groupBy(_._1.id)
    grouped.foreach { (id, rs) =>
      val distinct = rs.map(_._1).distinct
      if distinct.sizeIs > 1 then
        throw ConfigError(s"remedy '$id'",
          s"${distinct.size} different remedies claim this id (declared by " +
            s"${rs.map(_._2).distinct.sorted.mkString(", ")}); a remedy id is published API and " +
            "exactly one may answer to it — the selection key is flat precisely because ids are unique")
    }
    new RemedyVocabulary(
      grouped.map((id, rs) => id -> rs.head._1),
      grouped.map((id, rs) => id -> rs.map(_._2).distinct.sorted.mkString(", ")),
    )
