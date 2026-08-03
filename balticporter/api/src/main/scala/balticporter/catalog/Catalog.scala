package balticporter.catalog

/** THE DIFFERENCE CATALOG — every Java-vs-Scala semantic difference this engine knows about, as
  * CODE rather than as a document.
  *
  * WHY CODE. Four reasons, in descending weight:
  *
  *   1. a lowering arm, a transform and a check must be able to CITE a row, and a coverage lane
  *      must be able to report that a row is never reached. A markdown table can do neither;
  *   2. `CLAUDE.md` §3.6 admits six documents and forbids a seventh, and none of the six fits — 126
  *      rows would swamp `DESIGN.md` (decisions, not inventories), `ENGINE-LIMITS.md` is measured
  *      dead ends and most of these rows are neither measured nor dead ends, `PROGRESS.md` is
  *      per-port state;
  *   3. `CLAUDE.md` §4.45 — the consumer is an agent in ANOTHER repository. A catalog that ships in
  *      the engine jar is one that agent has; a table in this repo's markdown is one it does not;
  *   4. the precedent is this codebase's own (`DESIGN.md` §6.2 on `UnportableKind`): *a closed
  *      engine enum — a new kind is an engine change that arrives with its mint sites and its
  *      report text, which is the correct friction*. A [[Difference]] is that, one level finer.
  *
  * THE HARD RULE THAT PROTECTS `CLAUDE.md` §1's TAXONOMY: **a [[Difference]] takes no parameter.**
  * No `RuleScope`, no `Set[String]`, no predicate, no `SurfacePolicy`. A difference is a fact about
  * Java and Scala; if it needs to know something about a LIBRARY it is not a difference, it is a
  * (b) phase's policy, and it belongs in that phase's constructor where §1.5's merge contract
  * already governs it. `DifferenceTakesNoParameterSpec` is that rule, mechanised — every field of
  * every row, recursively, must be a literal or an enum case.
  *
  * WHAT IS DELIBERATELY NOT HERE YET. A row carries no `attaches` and no `tests`. Both are real
  * fields of the design and both depend on a mechanism that does not exist — the obligation
  * surfaces at the frontend dispatch, at the emitter dispatch and at a phase's per-declaration
  * citation. A field whose mechanism is not built is the `Unmechanised` trap: it reads as a claim
  * the engine cannot honour, and the lane that would check it would report every row as fine. They
  * arrive with the surfaces that discharge them, not before.
  *
  * NO ROW MAY CARRY A NUMBER. Numbers live where §3.6 says: a measurement in `ENGINE-LIMITS.md` or
  * `PROGRESS.md`, and the row points at it with its [[Twin]]. This includes the catalog's own SIZE
  * — `Differences.all.size` is derived and is written down nowhere, which is `PortabilityCheck`'s
  * phantom "34 rules" lesson applied to the thing that recorded it.
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

/** `JS-E04`. Stable, NEVER reused and NEVER renumbered — which is why [[Differences.retired]]
  * exists: an id absorbed into another row keeps its number out of circulation rather than freeing
  * it. Always rendered with the `JS-` prefix, so a catalog id can never be mistaken for an
  * `ENGINE-LIMITS.md` one (both files have a `G22`, and they are different facts). */
final case class DiffId(area: Area, n: Int):
  override def toString: String = f"JS-$area%s$n%02d"

/** what a reader of the emitted code would SEE if the difference were mishandled — which decides
  * what evidence could exist for the row at all. */
enum Severity:
  /** compiles, no count moves; only a behavioural test can see it */
  case Silent
  /** a compile error, or a wired check fires */
  case Loud
  /** loud in one direction and silent in the other */
  case Mixed
  /** a checked NON-difference: the two languages agree and this row records that they were checked */
  case NoImpact

/** where the engine stands on a difference TODAY.
  *
  * A status is a claim about a moving target — four headline rows went from `Open` to fixed inside
  * one week while the document describing them was being written — so it is re-derived
  * mechanically (`scripts/catalog-status.sh`) and pinned by `ClosedTwinStatusSpec`, never
  * transcribed by hand from a document. */
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

/** the empirical record this row PREDICTS, which is what makes the catalog answerable to reality
  * rather than to itself. */
enum Twin:
  /** an `ENGINE-LIMITS.md` entry, by its stable id (`F5`, `C12`, `K5.6`) — the id `ClosedTwinStatusSpec` resolves */
  case EngineLimit(id: String)
  /** `CLAUDE.md` §4.4's table — the rows an agent is expected to know by heart */
  case Rule44
  /** the engine's own source states the difference at the site that handles it; `where` is the symbol */
  case InCode(where: String)
  /** derived from the specs and NEVER observed in the corpus. An honest state, and the one a row
    * claiming [[Status.Open]] most often has */
  case Predicted
  /** no twin, and none is owed — the normal state of a checked non-difference */
  case NoTwin

/** which of `CLAUDE.md` §1's three kinds a FIX for this difference would be. The reader's first
  * question is which repository the fix lives in (§4.45), so it is a field and not prose. */
enum FixKind:
  /** (a) universal — the engine is wrong or incomplete for every library */
  case Universal
  /** (b) the mechanism exists; a port supplies its values */
  case Parameterised
  /** (c) a rule a porting program plugs in */
  case LibraryRule
  /** the row is a non-difference; there is nothing to fix */
  case NoFix

/** ONE ROW of the language half of the catalog — `JS-{E,S,C,G}`.
  *
  * Every field is a literal or an enum case, and `DifferenceTakesNoParameterSpec` enforces exactly
  * that. See [[Catalog]] for why that rule is the single most important guard rail here.
  *
  * @param id        stable, never reused (see [[DiffId]])
  * @param title     ONE line. A row that needs three paragraphs is a `DESIGN.md` decision or an
  *                  `ENGINE-LIMITS.md` measurement, and the row cites it instead
  * @param jls       the Java citation, verbatim section numbers
  * @param scala     the Scala-side rule, with a citation where one was located and the literal
  *                  prefix `UNCITED — ` where none was. The gap is DATA rather than a footnote:
  *                  `ClosedTwinStatusSpec` counts it, so "we could not find the reference page" is
  *                  a number that can go down instead of a sentence nobody re-reads
  * @param severity  what a mishandling would look like
  * @param status    re-derived against HEAD, never copied from a document
  * @param twin      the empirical record; not optional for a row claiming [[Status.Open]]
  * @param fix       §1's classification of the FIX
  * @param evidence  the SYMBOL that handles the difference, or — for an open row — the symbol where
  *                  the gap is, with the file when the symbol alone is ambiguous. **Never a line
  *                  number**: this pass alone re-read a dozen line numbers that had moved, and a
  *                  registry of 126 stale line numbers would be worse than none
  */
final case class Difference(
    id: DiffId,
    title: String,
    jls: String,
    scala: String,
    severity: Severity,
    status: Status,
    twin: Twin,
    fix: FixKind,
    evidence: String,
)

/** an id that was ABSORBED into another row and is therefore out of circulation forever.
  *
  * `CLAUDE.md`-shaped reason: "never reused, never renumbered" is a rule nothing can enforce unless
  * the retirements are DATA. A retired id with no record is an id the next agent assigns to a new
  * difference, and every citation written before that day then resolves to the wrong fact. */
final case class Retired(id: DiffId, into: Option[DiffId], why: String)
