package balticporter.tir

/** WHAT A PHASE MOVED, and WHICH LANE COUNTS THE SEAMS IT COULD NOT CLOSE. Closes CLAUDE.md §1's
  * "every seam is counted" obligation. A phase declares only [[Rewrite.accountedBy]] (the lane);
  * `Pipeline.runTraced` DERIVES what it retyped by comparing BOTH type records (a symbol's `info`
  * AND its tree's `tpt`) across the phase, so it cannot under-report. Not a second boundary count
  * (the checks keep their own numbers) and not a pre-phase site count (measured wrong — K5.10). */
trait Rewrite extends Phase:

  /** The check lanes that COUNT every seam this phase's retyping opened and could not close. Named
    * via `CheckReport` constants (e.g. `CollectionBoundaryCheck.Name`), not string literals, so a
    * renamed lane is a compile error. A phase genuinely counted by nothing should not extend this
    * trait — the empty set and the absent trait read as one finding in `rewrite-callsites`. */
  def accountedBy: Set[String]

/** ONE PHASE'S RECORD for one translation: what its rewrite moved, and what it claims counts the
  * residue. `retyped` is OBSERVED by the pipeline (owned symbols whose `info` changed);
  * `accountedBy` is DECLARED by the phase — see [[Rewrite]] for why. An external's signature is a
  * class-file fact no phase may move (§4.56); `StandardTraversal.mapSymbols` already refuses it. */
final case class Patch(phase: String, retyped: Set[SymId], accountedBy: Set[String]):
  def isAccounted: Boolean = accountedBy.nonEmpty

/** The patches one translation produced — a value THAT RUN owns.
  *
  * Never a process-global table, for the reason `TirEmitter.srcMap`, `DecisionLog` and `CatalogLog`
  * are values one run owns (`CLAUDE.md` §5.1): `Determinism.Full` translates twice, and two
  * translations sharing a log would double every count in it. */
final class RewriteLog:
  private val entries = collection.mutable.ListBuffer.empty[Patch]

  def record(p: Patch): Unit = entries += p

  /** every phase that moved a declaration's type, in pipeline order. */
  def all: List[Patch] = entries.toList

  /** the phases that moved something and claim no lane — `rewrite-callsites`'s first finding. */
  def unaccounted: List[Patch] = all.filterNot(_.isAccounted)

  def clear(): Unit = entries.clear()

object RewriteLog:
  /** for a caller that does not want the record — a testkit fixture, a `§1(c)` rule's own harness.
    * A shared instance would accumulate across callers, so this is a factory and not a value. */
  def discarding: RewriteLog = new RewriteLog
