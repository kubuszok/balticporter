package balticporter.catalog

/** The difference catalog — every Java-vs-Scala semantic difference this engine knows about, as code rather than a document. Enables citation and coverage reporting (a markdown table cannot), ships
  * to a consumer repository's agent, and follows `UnportableKind`'s closed-enum discipline. A [[Difference]] takes no parameter — every row is a literal or enum case. No row carries a number:
  * measurements live outside this registry.
  */
object Catalog

/** the JLS area a difference belongs to. The letter is part of every id and never changes. */
enum Area:
  /** expressions and operators — JLS 15, 5.6 */
  case E

  /** statements and control flow — JLS 14, 16 */
  case S

  /** classes, objects, initialization, members, visibility — JLS 8, 9, 12, 6.6 */
  case C

  /** generics, arrays, erasure, boxing, varargs, method references — JLS 4, 5, 10, 15.12/15.13, 18 */
  case G

  /** library surface — `java.lang` / `java.util` / `java.time` / `java.text`. An [[ApiRow]] */
  case L

  /** platform capability — systems-facing JDK APIs off the JVM. An [[ApiRow]] */
  case P

/** `JS-E04`. Stable, never reused and never renumbered — which is why [[Differences.retired]] exists: an id absorbed into another row keeps its number out of circulation rather than freeing it.
  * Rendered with a language prefix (`JS-` for Java, `TS-` for TypeScript, `DT-` for Dart), so a catalog id is never confused with an id from a different numbering scheme.
  */
final case class DiffId(area: Area, n: Int, lang: balticporter.core.Language = balticporter.core.Language.Java):
  override def toString: String = f"${lang.prefix}-$area%s$n%02d"

/** what a reader of the emitted code would SEE if the difference were mishandled — which decides what evidence could exist for the row at all.
  */
enum Severity:
  /** compiles, no count moves; only a behavioural test can see it */
  case Silent

  /** a compile error, or a wired check fires */
  case Loud

  /** loud in one direction and silent in the other */
  case Mixed

  /** a checked NON-difference: the two languages agree and this row records that they were checked */
  case NoImpact

/** where the engine stands on a difference TODAY — never transcribed by hand from a stale note, because a status is a claim about a moving target.
  */
enum Status:
  /** the engine reproduces Java's meaning; [[Difference.evidence]] names the symbol that does it */
  case Handled

  /** part of the difference is reproduced; `missing` is the part that is not */
  case Partial(missing: String)

  /** the engine does not reproduce it, and nothing yet does */
  case Open

  /** the frontend has no model for the construct at all */
  case Absent(why: String)

  /** no faithful Scala image EXISTS — a refusal by design, not a gap */
  case Refused(why: String)

  /** the two languages agree; `why` is what was checked */
  case NonDiff(why: String)

  def isOpen: Boolean = this match
    case Status.Open => true
    case _           => false

/** the empirical record this row PREDICTS, which is what makes the catalog answerable to reality rather than to itself.
  */
enum Twin:
  /** the measured observation that this difference exists and how it showed up — one plain sentence carrying the fact itself, rather than a pointer to where it was recorded.
    */
  case Measured(evidence: String)

  /** the project rules' table of java-statement semantics differences — rows an agent is expected to know by heart
    */
  case Rule44

  /** the engine's own source states the difference at the site that handles it; `where` is the symbol */
  case InCode(where: String)

  /** derived from the specs and NEVER observed in the corpus. An honest state, and the one a row claiming [[Status.Open]] most often has
    */
  case Predicted

  /** no twin, and none is owed — the normal state of a checked non-difference */
  case NoTwin

/** Which of the three kinds (universal, parameterised, library-specific) a fix for this difference would be. The reader's first question is which repository the fix lives in, so it is a field and not
  * prose.
  */
enum FixKind:
  /** (a) universal — the engine is wrong or incomplete for every library */
  case Universal

  /** (b) the mechanism exists; a port supplies its values */
  case Parameterised

  /** (c) a rule a porting program plugs in */
  case LibraryRule

  /** the row is a non-difference; there is nothing to fix */
  case NoFix

  /** WHICH REPOSITORY the fix lives in, spelled as [[balticporter.tir.Reason.section]] spells it — one vocabulary. A method rather than an enum PARAMETER: `DifferenceTakesNoParameterSpec` holds every
    * catalog value to a literal or bare case.
    */
  def section: String = this match
    case Universal     => "engine (true of every Java program)"
    case Parameterised => "port policy"
    case LibraryRule   => "library-specific rule"
    case NoFix         => "no fix owed"

/** One row of the language half of the catalog — `JS-{E,S,C,G}`. Every field a literal or enum case (`DifferenceTakesNoParameterSpec`, see [[Catalog]]). @param id stable @param title one line (longer
  * belongs elsewhere, cited) @param jls/scala citations, `UNCITED — ` prefix where none was found (counted) @param status re-derived, never copied @param evidence the symbol, never a line number
  * (they go stale) @param attaches where a decision is owed ([[Attaches]]).
  */
final case class Difference(
  id:       DiffId,
  title:    String,
  jls:      String,
  scala:    String,
  severity: Severity,
  status:   Status,
  twin:     Twin,
  fix:      FixKind,
  evidence: String,
  attaches: Attaches
)

/** an id that was absorbed into another row and is therefore out of circulation forever.
  *
  * "Never reused, never renumbered" is a rule nothing can enforce unless the retirements are data. A retired id with no record is an id the next agent assigns to a new difference, and every citation
  * written before that day then resolves to the wrong fact.
  */
final case class Retired(id: DiffId, into: Option[DiffId], why: String)
