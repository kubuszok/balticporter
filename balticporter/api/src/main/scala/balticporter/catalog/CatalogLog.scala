package balticporter.catalog

import balticporter.tir.Origin

/** THE OBLIGATION SURFACES — the half of the catalog that makes a row answerable to the code.
  *
  * A registry nothing consults is dead weight, and a facility with no call sites is
  * indistinguishable from one that is not there (`TirTrace` is this repository's own worked
  * example: the mechanism shipped, two call sites were wired, and nothing could report the
  * difference). So a row does not merely describe a difference — it declares WHERE the engine owes
  * it a decision, and the engine records whether that decision was taken.
  *
  * WHAT IS GUARANTEED, stated at the strength it actually holds. The wrapper detects an ABSENT
  * consult. It cannot detect a WRONG one: an arm that calls `consult(JS.E(3))` and hands it a
  * predicate that never returns `Some` discharges the obligation and emits the same wrong code. So
  * the claim is *a difference cannot be silently UNCONSIDERED at a site the catalog attaches it
  * to*, and the other half — that the consideration is CORRECT — is carried by the per-difference
  * edge-case suites, which is why those suites are a definition-of-done and not an afterthought.
  * An over-claimed guarantee is how a mechanism stops being audited (`DESIGN.md` §2.8).
  *
  * THE WRAPPER GOES AT THE DISPATCH, NEVER IN AN ARM. If [[Lowering.of]] were written inside each
  * `case`, an arm could decline to wrap and the mechanism would see nothing — and an arm that opts
  * out is the same shape as the defect the mechanism exists to catch. Entered at the dispatch, an
  * arm is incapable of escaping its obligations because it never had the choice.
  *
  * THREE DISCHARGE SURFACES, ONE LOG. Most rows do not discharge in the frontend at all:
  *
  *   - **frontend lowering** (Java AST → TIR) — [[Lowering.of]] at the dispatch, [[Obligations]]
  *     inside. This is the surface built today;
  *   - **emitter rendering** (TIR → Scala text) — a symmetrical wrapper keyed on the `Tree` kind
  *     rather than on the Java node kind. NOT BUILT: the rows that would discharge there carry
  *     [[Attaches.Unmechanised]] and are counted, because a lane that reported them as fine on the
  *     strength of a surface that does not exist would be worse than no lane;
  *   - **phases** (whole-program rewrites) — a phase does not walk one node kind, so a wrapper is
  *     the wrong shape. A phase CITES its row through [[CatalogLog.cite]], one row per DECLARATION
  *     it decides about, exactly the granularity `Decision` already uses (`CLAUDE.md` §5.1: one row
  *     per declaration, never one per expression). Deliberately weaker than an obligation, and
  *     reported as its own thing: nothing can assert that a phase *should have* considered a
  *     difference at a declaration it never visited.
  *
  * The three feed ONE log, because the coverage question is "was this row reached at all,
  * anywhere" and three per-surface artifacts would answer three narrower questions and not that one.
  *
  * A LOG IS A VALUE ONE RUN OWNS — never a process-global table, for the reason `TirEmitter.srcMap`
  * and `DecisionLog` are values one run owns (`CLAUDE.md` §5.1). `Determinism.Full` translates
  * twice, and two translations sharing a log would double every count in it.
  */
object Lowering:

  /** Enter the obligation scope for ONE node of `kind` at `dispatch`, then run its lowering.
    *
    * On exit, every row the catalog attaches to that (kind, dispatch) and the body did not consult
    * is recorded as a HOLE. A body that throws settles nothing — `SpoonTir.unsupported` fails the
    * whole compilation unit, so there is no partial result to report an obligation about.
    *
    * Costs nothing where nothing attaches: the common case is an empty `owed` list, which takes a
    * shared, allocation-free [[Obligations]] and skips the settle entirely. */
  def of[A](kind: String, dispatch: Dispatch, at: Origin)(body: Obligations ?=> A)(using log: CatalogLog): A =
    val owed = Differences.owedAt(kind, dispatch)
    if owed.isEmpty then body(using log.unattached)
    else
      val o = new Obligations(log, owed)
      val r = body(using o)
      o.settle(kind, dispatch, at)
      r

/** WHICH of the frontend's two term dispatches an obligation attaches at.
  *
  * Not decoration, and not a frontend implementation detail leaking into the registry: java gives
  * the SAME node kind two different meanings by position, and the catalog has two rows for exactly
  * that reason. `i += f` as a statement (JLS 14.8) discards the compound assignment's value; the
  * same node as an expression (JLS 15.26.2) yields it, and the narrowing that is right in one
  * position is a different obligation in the other. A single kind-keyed attachment could not tell
  * `JS-E03` from `JS-E04`, which is the pair the whole mechanism was designed around. */
enum Dispatch:
  /** the node reached as a STATEMENT — JLS 14.8's expression statement. `SpoonTir.stmtKind` */
  case Statement
  /** the node reached as an EXPRESSION whose value is used — JLS 15. `SpoonTir.exprNoCast` */
  case Expression
  /** both dispatches owe the consult */
  case Either

/** WHERE a row's obligation is discharged — the field that makes a row answerable to the code.
  *
  * ONE value per row rather than a list, and that is the [[Difference]] no-parameter rule holding:
  * `DifferenceTakesNoParameterSpec` rejects a collection in any row field, because a collection is
  * the exact shape a per-library policy takes. A row that genuinely discharges at two surfaces will
  * want a `Both(a, b)` case — a product of enum cases, which the spec admits by recursion — and it
  * is deliberately not here yet, because a case with no constructor is the same dead facility this
  * file's header warns about.
  */
enum Attaches:
  /** the frontend's lowering dispatch owes a consult for every node of `kind` at `dispatch`.
    * `kind` is Spoon's INTERFACE name, the key `SpoonKinds` registers. */
  case Lowered(kind: String, dispatch: Dispatch)

  /** a PHASE decides this row, and cites it per declaration. `phase` is the phase's `name`, so the
    * citation and the phase that owes it can be joined without reading either. */
  case Cited(phase: String)

  /** NO obligation surface exists for this row yet, and `why` says which one it wants.
    *
    * This is the honest alternative to measuring: a row whose discharge site has no mechanism is
    * EXCLUDED from the undischarged lane and counted in its own, so "we are not measuring these" is
    * a number that can go down rather than a silence that reads as coverage. */
  case Unmechanised(why: String)

  /** there is NOTHING to discharge — the row records a checked non-difference, or a difference the
    * translation satisfies by construction with no site-level decision to take. `why` is what was
    * checked. Distinct from [[Unmechanised]] on purpose: one says the surface is missing, the other
    * says no surface is owed, and collapsing them would hide the first inside the second. */
  case NoObligation(why: String)

/** ONE node's obligations, live for the duration of its lowering.
  *
  * Allocated per node of an ATTACHED kind and never otherwise (see [[Lowering.of]]). Not
  * thread-safe and deliberately so: one lowering is one call stack, and a shared counter would be
  * the process-global table §5.1 forbids.
  */
final class Obligations private[catalog] (log: CatalogLog, owed: List[DiffId]):
  private var seen: List[DiffId] = Nil

  /** CONSULT a difference at this site. `f` returns `Some(fix)` when the difference APPLIES here.
    *
    * Consulting is what discharges the obligation — FIRING is not required and must not be, because
    * "this difference does not apply at this site" is the answer at the overwhelming majority of
    * sites and an obligation that demanded a fix everywhere would be an obligation nobody could
    * meet. Both numbers are recorded: `consulted` says the branch is live, `fired` says it did
    * something, and a branch with a high consult count and a zero fire count is exactly the shape
    * §2.3(a) warns cannot be distinguished from a correct one without an edge-case suite. */
  def consult[A](id: DiffId, at: Origin)(f: => Option[A]): Option[A] =
    val r = f
    seen ::= id
    log.record(id, fired = r.isDefined, at)
    r

  private[catalog] def settle(kind: String, dispatch: Dispatch, at: Origin): Unit =
    owed.foreach(id => if !seen.contains(id) then log.hole(id, kind, dispatch, at))

object Obligations:
  /** the consult, reached without naming the receiver — what a lowering arm writes. */
  def consult[A](id: DiffId, at: Origin)(f: => Option[A])(using o: Obligations): Option[A] =
    o.consult(id, at)(f)

/** WHAT ONE RUN CONSULTED — the value `catalog.tsv` and the four `catalog(…)` lanes are computed
  * from.
  *
  * @param fatal testkit / `just debug-emit` enforcement. An undischarged obligation on a row the
  *   registry says WORKS is an error there, because every difference gets an edge-case suite and
  *   that suite runs in the mode where a hole is a failure. A PORT RUN counts instead: a run that
  *   died because a rule is incomplete is a run that produces no diagnostics at all, which is the
  *   wrong trade (`ENGINE-LIMITS.md` M6 is about refusing to APPROXIMATE, not about refusing to
  *   REPORT). A row the registry says is `Open` or `Absent` is never fatal in either mode — it is a
  *   KNOWN hole, it is the work list, and a mode that died on it would make the work list
  *   unrunnable.
  */
final class CatalogLog(val fatal: Boolean = false):

  private val consults  = collection.mutable.LinkedHashMap.empty[DiffId, Int]
  private val fires     = collection.mutable.LinkedHashMap.empty[DiffId, Int]
  private val holes     = collection.mutable.LinkedHashMap.empty[DiffId, CatalogLog.Hole]
  private val firstFired = collection.mutable.LinkedHashMap.empty[DiffId, Origin]
  private val citations = collection.mutable.LinkedHashMap.empty[DiffId, collection.mutable.LinkedHashSet[String]]

  /** the [[Obligations]] handed to a dispatch nothing attaches to. Owes nothing, settles nothing,
    * and still RECORDS — an arm may consult a row the catalog attaches elsewhere, and a consult
    * that vanished because its kind had no attachment would be a coverage number that lies low. */
  private[catalog] val unattached: Obligations = new Obligations(this, Nil)

  private[catalog] def record(id: DiffId, fired: Boolean, at: Origin): Unit =
    consults(id) = consults.getOrElse(id, 0) + 1
    if fired then
      fires(id) = fires.getOrElse(id, 0) + 1
      // ONE example site per row, and the FIRST one — a row's finding has to carry a path and a
      // line somebody can open, and "the first site that fired" is the only choice that is stable
      // across runs. A later site would move with every unrelated edit above it, which is exactly
      // the noisy-diff failure `CheckReport` was built to avoid.
      if !firstFired.contains(id) then firstFired(id) = at

  private[catalog] def hole(id: DiffId, kind: String, dispatch: Dispatch, at: Origin): Unit =
    val h = holes.get(id) match
      case Some(prev) => prev.copy(sites = prev.sites + 1)
      case scala.None =>
        if fatal && !CatalogLog.knownHole(id) then
          throw new AssertionError(
            s"$id attaches to $kind at $dispatch and the lowering returned without consulting it " +
              s"(${at.javaPath}:${at.line}) — either the arm owes the consult, or the row's " +
              "`attaches` is wrong. [§1(a) engine]")
        CatalogLog.Hole(id, kind, dispatch, at, sites = 1)
    holes(id) = h

  /** a PHASE decided this row at `decl`. Idempotent per declaration: a phase that rewrites four
    * assertions in one method cites the method once, which is the granularity `Decision` uses and
    * the only one at which a phase citation means anything (§5.1). */
  def cite(id: DiffId, decl: String): Unit =
    citations.getOrElseUpdate(id, collection.mutable.LinkedHashSet.empty) += decl

  def consulted(id: DiffId): Int    = consults.getOrElse(id, 0)
  def fired(id: DiffId): Int        = fires.getOrElse(id, 0)
  def declarations(id: DiffId): Int = citations.get(id).fold(0)(_.size)

  /** the first site at which this row's consult FIRED, so a lane's finding names a line somebody
    * can open. `scala.None` for a row that was consulted and never applied — which is the normal
    * state of most rows at most sites and is not a defect. */
  def exampleSite(id: DiffId): Option[Origin] = firstFired.get(id)

  /** the declarations a PHASE cited this row at, in citation order. */
  def citedAt(id: DiffId): List[String] = citations.get(id).fold(Nil)(_.toList)

  /** every row this run reached, by either surface. */
  def reached: Set[DiffId] = consults.keySet.toSet ++ citations.keySet.toSet

  /** the holes, one per ROW — never one per site. A compound assignment in every method of a
    * library would otherwise produce a thousand findings saying one thing. */
  def undischarged: List[CatalogLog.Hole] = holes.values.toList.sortBy(_.id.toString)

  /** the artifact's rows: EVERY catalog row, reached or not, so `catalog.tsv` answers "which
    * branches does this port never touch" and not only "which did it". */
  def rows: List[CatalogLog.Row] =
    Differences.all.map(d =>
      CatalogLog.Row(d.id, d.status, d.attaches, consulted(d.id), fired(d.id), declarations(d.id)))

object CatalogLog:

  /** a lowering that returned without consulting an attached row, with one example site. */
  final case class Hole(id: DiffId, kind: String, dispatch: Dispatch, at: Origin, sites: Int)

  /** one line of `catalog.tsv`. */
  final case class Row(id: DiffId, status: Status, attaches: Attaches,
                       consulted: Int, fired: Int, declarations: Int)

  /** a hole the registry ALREADY declares — an `Open` or `Absent` row cannot be consulted (rule
    * (ii)), so it is undischarged by construction and is the work list rather than a defect. */
  def knownHole(id: DiffId): Boolean =
    Differences.byId.get(id).exists(d => d.status.isOpen || d.status.isInstanceOf[Status.Absent])

  /** the log a caller that does not want one still has to pass. Not a global: each call makes a new
    * one, so nothing accumulates across two runs. */
  def discarding: CatalogLog = new CatalogLog(fatal = false)
