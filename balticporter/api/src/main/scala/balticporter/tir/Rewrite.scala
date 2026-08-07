package balticporter.tir

/** WHAT A PHASE MOVED, and WHICH LANE COUNTS THE SEAMS IT COULD NOT CLOSE.
  *
  * ==The hole this closes==
  * `CLAUDE.md` §1 states the obligation on a retyping phase in prose: *every seam the scope creates
  * is COUNTED — where a coercion exists, insert it; where none can, refuse and report with the §1
  * classification.* Four phases in this engine answer it, each with its own check
  * (`collection-boundary`, `collection-retarget`, `nullability-boundary`, `context-seam`), and each
  * answer was arrived at the same way: a port shipped, a wall of `Found: … / Required: …` arrived,
  * and somebody wrote the count afterwards. `ENGINE-LIMITS.md` K5.6 records the residue of that
  * process in one sentence — *a NEW retyping phase can reintroduce this shape until it too asks the
  * question* — and nothing in the engine was able to notice a phase that did not.
  *
  * A phase that moves a declaration's type and counts nothing is invisible to every instrument this
  * repository has. It emits code that compiles (the retyping is position-blind, so both sides of
  * most slots move together), no check count moves (there is no check), no member digest is wrong
  * (the output really is what the phase meant), and the seams it left arrive at whoever compiles the
  * port next as bare typer errors with no §1 classification — which §4.45 names as the bulk of a new
  * library's first wall.
  *
  * ==What is DECLARED and what is OBSERVED — they are deliberately not the same half==
  * A phase declares [[Rewrite.accountedBy]]: the check lanes that count its residue. It does NOT
  * declare what it retyped, and that is the design. `Pipeline.runTraced` DERIVES the retyped set by
  * comparing each owned symbol's `info` across the phase, so a phase cannot under-report its own
  * reach — the one number a phase could be wrong about, or silently stop maintaining, is the one it
  * is not asked for. What a phase alone can say is which lane it accounts through, and that claim is
  * checked too: [[balticporter.tir.Patch.accountedBy]] naming a lane that did not RECORD in this run
  * is `rewrite-callsites`'s second finding, which is exactly the shape of `PortRun`'s own worst
  * wiring defect (`LibgdxTestMigrate` never called `PortabilityCheck` at all, and nothing said so
  * until `RequiredChecks` existed).
  *
  * ==Two things this deliberately is NOT==
  *
  *   - **it is not a second boundary count.** The four checks keep their coercion logic and their
  *     numbers; nothing here re-derives a seam. A `Patch` that carried its own refusal list would be
  *     two numbers for one residue, free to disagree — which is the reason `DESIGN.md` §2.8's
  *     catalog family has four lanes and not five, and the reason chunk 4's `markers` lane was not
  *     given a second name;
  *   - **it is not a site count taken BEFORE the phase runs.** That was the other half of the
  *     design, and it is measured and refused: every retyping phase in this engine resolves the
  *     symbols its `transformType` reads INSIDE its own `run` (`CollectionsTransform`'s `remap`,
  *     `NullabilityTransform`'s annotation binding), so applying `transformType` to the symbol table
  *     beforehand moves NOTHING and a dry-run site count is a confident zero. `ENGINE-LIMITS.md`
  *     K5.10 carries the number and `RewriteCallSitesSpec`'s last test re-derives it, so the claim
  *     dies the day a phase becomes predictable rather than living in this comment.
  */
trait Rewrite extends Phase:

  /** The check lanes that COUNT every seam this phase's retyping opened and could not close.
    *
    * One or more `CheckReport` names — `CollectionBoundaryCheck.Name`, not a string literal, so a
    * renamed lane is a compile error rather than a silently unwired claim. A phase whose residue is
    * genuinely counted by nothing has no honest value to put here and should not extend this trait:
    * the empty set and the absent trait mean the same thing, and `rewrite-callsites` reports both as
    * the same finding. */
  def accountedBy: Set[String]

/** ONE PHASE'S RECORD for one translation: what its rewrite moved, and what it claims counts the
  * residue.
  *
  * `retyped` is OBSERVED by the pipeline (owned symbols whose `info` the phase changed) and
  * `accountedBy` is DECLARED by the phase — see [[Rewrite]] for why the two halves come from
  * different places. `retyped` is an owned-symbol set: an external's signature is a fact about a
  * class file and no phase may move one (§4.56), so a phase that appears to have moved one has a
  * defect of a different kind and `StandardTraversal.mapSymbols` already refuses it. */
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
