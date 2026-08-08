package balticporter.tir

import balticporter.catalog.FixKind

/** THE MENU — one named thing the engine can DO at one location, offered by the phase or check that
  * can do it.
  *
  * ==Why a menu exists at all==
  * `CLAUDE.md` §1 splits every rule three ways: (a) universal, (b) a mechanism the engine owns whose
  * policy a manifest supplies, (c) a rule that could only ever apply to one library. That split is
  * about MECHANISMS, and it leaves a gap the corpus keeps falling into: a difference with no single
  * right answer, where the engine can perform any of two or three faithful translations and only the
  * port knows which one it wants HERE. Today the port's only reachable answers are "accept the
  * finding" and "write a (c) rule", and the second is a whole compiled artifact for a decision that
  * is one word long.
  *
  * A remedy is that one word. The phase or check that mints the residue finding declares the
  * alternatives it can carry out; a port SELECTS one per location
  * ([[balticporter.core.PortManifest.resolutions]]); the engine applies it and both halves are
  * counted ([[Resolution]]). Nothing about the mechanism moves into the manifest — the manifest
  * names a choice from a list the engine published, which is exactly the shape §1(b) already has
  * for a `RuleScope` and a `verdictOverrides` entry.
  *
  * ==What a declaration has to carry, and why each half is not optional==
  *   - [[id]] is GLOBALLY UNIQUE across every source in a run, which is what lets the selection key
  *     stay flat: `"owner#member" = "remedy-id"` and never a compound `(lane, kind, id)`. A port
  *     writes one string and the engine knows which phase it is talking to. Uniqueness is enforced
  *     where the vocabulary is assembled ([[RemedyVocabulary.from]]), because that is the only layer
  *     that sees two declarers at once;
  *   - [[lane]] and [[kind]] name the RESIDUE this remedy drains — the `CheckReport` check and the
  *     finding kind within it. Without them a resolution is a verdict with no denominator: the
  *     refusal lane it empties must fall by exactly the number that moved into `remediation(resolved)`
  *     (`CLAUDE.md` §5's trivia-family rule, one artifact over), and nothing can check that if the
  *     remedy does not say which lane it is draining;
  *   - [[emissionAffecting]] is what puts a selection in §1.5's MUST-agree column. A remedy that
  *     changes emitted text at a shared declaration and is chosen differently by two modules
  *     produces two ports that each compile alone and cannot compile together. Declared rather than
  *     assumed, because the day a remedy only re-files a finding under another lane, that one
  *     belongs in the not-inherited column beside `verdictOverrides` and the field is where the
  *     difference is stated;
  *   - [[fix]] is `CLAUDE.md` §1's classification of the REMEDY ITSELF — which repository owns the
  *     code that carries it out. It is not the classification of the SELECTION, which is always
  *     §1(b) by construction (a manifest key), and that is why this is a
  *     [[balticporter.catalog.FixKind]] and not a [[Reason]]: a `Reason.Configured` needs the phase
  *     and the key an applied resolution has and a declared remedy does not. The catalog's enum is
  *     REUSED rather than restated — two enums with the same three cases would be two vocabularies
  *     for one question, which is what `RemedyHint` and this type already avoid by sharing it.
  */
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
):
  /** the `lane(kind)` pair, as the residue count spells it. */
  def target: String = s"$lane($kind)"

  def render: String = s"$id — $what  [drains $target; ${fix.section}]"

object Remedy:

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

/** A PHASE OR CHECK THAT OFFERS REMEDIES.
  *
  * A trait beside [[Phase]] rather than a field on it, for [[IdiomPhase]]'s reason: a phase that
  * offers nothing should not be asked, and a CHECK is not a phase at all — half the residue lanes a
  * remedy could drain are produced by plain objects the orchestrator calls. Both answer here.
  *
  * The vocabulary a RUN can act on is assembled from the sources the run actually holds — the
  * pipeline's phases plus the checks the orchestrator registers — which is `Rewrite.accountedBy`'s
  * shape one level up: a claim a phase makes about itself, gathered per run rather than kept in a
  * table somebody has to remember to edit.
  */
trait RemedySource:
  /** every remedy this source can carry out. Empty is the honest answer for a source that offers
    * none, and is the default nothing has to state. */
  def remedies: List[Remedy]

/** EVERY REMEDY VISIBLE AT ONE MOMENT, by id — assembled, never enumerated in a table.
  *
  * Two different sets are built from this type and the difference between them is the whole of the
  * `resolutions` staleness story:
  *
  *   - the KNOWN set — every remedy this engine and this classpath ship, whether or not the port
  *     put the declaring phase in its pipeline. A `.conf` naming an id outside it is a
  *     [[ConfigError]] at load, because it is a typo and no run could ever act on it;
  *   - the ACTIVE set — the remedies of the sources THIS RUN holds. An id that is known and not
  *     active is a port that selected a remedy from a phase it did not enable: a policy finding
  *     naming the phase, never silence.
  *
  * Assembly REFUSES a duplicate id rather than picking one, exactly as `TransformRegistry` refuses
  * two factories claiming one name: an id is published API and a port that wrote it is entitled to
  * know which mechanism answered.
  */
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
