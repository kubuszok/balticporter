package balticporter.catalog

import balticporter.tir.Origin

/** THE OBLIGATION SURFACES -- the half of the catalog that makes a row answerable to the
  * code. A row declares WHERE the engine owes it a decision, and the engine records
  * whether that decision was taken. GUARANTEE: the wrapper detects an ABSENT consult, never
  * a WRONG one -- correctness is carried by the per-difference edge-case suites.
  *
  * THE WRAPPER GOES AT THE DISPATCH, NEVER IN AN ARM, so no arm can opt out.
  *
  * FOUR DISCHARGE SURFACES, ONE LOG: frontend lowering ([[Lowering.of]]), emitter rendering
  * ([[Rendering.of]]), types ([[Typing]], one surface with two ends since a TYPE is not a
  * node at either), and phases (whole-program rewrites CITE their row via
  * [[CatalogLog.cite]], one row per DECLARATION, weaker than an obligation). One log because
  * the coverage question is "was this row reached at all, anywhere".
  *
  * A row with no surface carries [[Attaches.Unmechanised]] and is COUNTED in its own lane --
  * a number that can go down, rather than a silence that reads as coverage.
  *
  * A LOG IS A VALUE ONE RUN OWNS, never a process-global table (CLAUDE.md §5.1):
  * `Determinism.Full` translates twice, and a shared log would double every count.
  */
object Lowering:

  /** Enter the obligation scope for ONE node of `kind` at `dispatch`, then run its
    * lowering. On exit, every row the catalog attaches to that (kind, dispatch) the body
    * did not consult is recorded as a HOLE. Costs nothing where nothing attaches.
    *
    * `subject` joins the DELEGATION SEAM by NODE IDENTITY: a node reached as a STATEMENT is
    * routinely handed straight to the EXPRESSION arm, so one node is lowered inside two
    * scopes and a consult marks the row seen in the enclosing scope too -- keyed on
    * identity rather than kind, since two nodes of the same kind on one line
    * (`x += (y += 1)`) are two separate obligations. */
  def of[A](kind: String, dispatch: Dispatch, at: Origin, subject: AnyRef)(body: Obligations ?=> A)(using log: CatalogLog): A =
    scoped(Differences.owedAt(kind, dispatch), kind, dispatch, at, subject)(body)

  /** the scope both dispatch surfaces enter -- ONE implementation, since the delegation
    * seam, the allocation-free fast path and the settle are the same question at either
    * end (`ENGINE-LIMITS.md` F8). */
  private[catalog] def scoped[A](owed: List[DiffId], kind: String, dispatch: Dispatch, at: Origin,
                                 subject: AnyRef)(body: Obligations ?=> A)(using log: CatalogLog): A =
    val outer = log.enterSubject(subject, at)
    try
      if owed.isEmpty then body(using log.unattached)
      else
        val o = new Obligations(log, owed, subject)
        log.enterScope(o)
        val r =
          try body(using o)
          finally log.exitScope()
        o.settle(kind, dispatch, at)
        r
    finally log.exitSubject(outer)

/** THE EMITTER'S HALF -- the same wrapper at the OTHER end of the pipeline, keyed on the
  * `Tree` kind. Most `JS-S` rows discharge only here: a decision about TEXT taken while
  * rendering, which a lowering-only mechanism cannot see.
  *
  * IDENTICAL to [[Lowering]]: wrapper at the DISPATCH, scopes joined by NODE IDENTITY.
  * DIFFERENT: no [[Dispatch]] -- the TIR has already resolved the position question a java
  * node's dual meaning needed; `Dispatch.Either` is what the shared machinery records.
  */
object Rendering:

  /** Enter the obligation scope for ONE `Tree` node, then render it. `kind` is the node's
    * `productPrefix`, the same name `EmissionFieldCoverageSpec` derives from the class
    * files. */
  def of[A](kind: String, at: Origin, subject: AnyRef)(body: Obligations ?=> A)(using log: CatalogLog): A =
    Lowering.scoped(Differences.owedAtRender(kind), kind, Dispatch.Either, at, subject)(body)

/** THE FOURTH SURFACE -- a TYPE, at both ends of the pipeline. Neither [[Lowering]] nor
  * [[Rendering]] can enter one: a `CtTypeReference` is not a statement or expression, and a
  * `TypeRepr` is not a `Tree` at all.
  *
  * ONE SURFACE, TWO ENDS: the frontend chooses the IMAGE, the emitter chooses the TEXT --
  * two [[Attaches]] cases since the KEYS are two different vocabularies (Spoon's
  * reference-interface names vs `TypeRepr`'s own `productPrefix`). NEITHER carries a
  * [[Dispatch]] (a type reference has only ever had one meaning).
  *
  * The origin the emitter half reports is [[CatalogLog.currentOrigin]] -- the node the type
  * is being rendered FOR -- because a `TypeRepr` is a VALUE shared across every position
  * naming it and has no origin of its own.
  */
object Typing:

  /** the FRONTEND's type-reference dispatch -- `SpoonTir.tpe`. `kind` is resolved by
    * `SpoonKinds.refNameOf`'s most-specific rule, so a `CtWildcardReference` is not
    * silently read as the `CtTypeParameterReference` its implementation extends. */
  def ofReference[A](kind: String, at: Origin, subject: AnyRef)(body: Obligations ?=> A)(using log: CatalogLog): A =
    Lowering.scoped(Differences.owedAtLowerType(kind), kind, Dispatch.Either, at, subject)(body)

  /** the EMITTER's type dispatch — `TirEmitter.tpe`. No `at`: see this object's header. */
  def ofRepr[A](kind: String, subject: AnyRef)(body: Obligations ?=> A)(using log: CatalogLog): A =
    Lowering.scoped(Differences.owedAtRenderType(kind), kind, Dispatch.Either, log.currentOrigin, subject)(body)

/** WHICH of the frontend's two term dispatches an obligation attaches at. Not decoration:
  * java gives the SAME node kind two different meanings by position (`i += f` discards its
  * value as a statement, JLS 14.8, and yields it as an expression, JLS 15.26.2), so a
  * single kind-keyed attachment could not tell `JS-E03` from `JS-E04`. */
enum Dispatch:
  /** the node reached as a STATEMENT — JLS 14.8's expression statement. `SpoonTir.stmtKind` */
  case Statement
  /** the node reached as an EXPRESSION whose value is used — JLS 15. `SpoonTir.exprNoCast` */
  case Expression
  /** the node reached as a MEMBER DECLARATION -- neither Statement nor Expression. A
    * `CtField` enters neither dispatch (walked from its type's member list), which made a
    * field's INITIALISER assignment-conversion slot invisible until named explicitly. Not
    * folded into [[Statement]]: `CatalogCoverageSpec` derives legal dispatches from Spoon's
    * own hierarchy and would reject that claim. */
  case Declaration
  /** both of the two POSITIONAL dispatches owe the consult. Not [[Declaration]]: a kind reached as
    * a declaration is reached at exactly one place, so a row that means it says so. */
  case Either

/** WHERE a row's obligation is discharged. ONE value per row, never a list
  * (`DifferenceTakesNoParameterSpec`'s no-parameter rule); a row discharging at two
  * surfaces uses [[Attaches.Both]] instead.
  */
enum Attaches:
  /** the frontend's lowering dispatch owes a consult for every node of `kind` at `dispatch`.
    * `kind` is Spoon's INTERFACE name, the key `SpoonKinds` registers. */
  case Lowered(kind: String, dispatch: Dispatch)

  /** the EMITTER's rendering dispatch owes a consult for every `Tree` node of `kind` — the node's
    * `productPrefix`, which is the same name `EmissionFieldCoverageSpec` derives from the class
    * files. See [[Rendering]] for why this case carries no [[Dispatch]]. */
  case Rendered(kind: String)

  /** the FRONTEND's TYPE-REFERENCE dispatch owes a consult for every reference of `kind` —
    * `SpoonTir.tpe`. `kind` is Spoon's reference-INTERFACE name, the key `SpoonKinds.references`
    * registers. See [[Typing]] for why the type surface is two cases and not one, and why neither
    * carries a [[Dispatch]]. */
  case LoweredType(kind: String)

  /** the EMITTER's TYPE dispatch owes a consult for every `TypeRepr` of `kind` — `TirEmitter.tpe`.
    * `kind` is the `TypeRepr` case's `productPrefix`, derived from the class files by
    * `EmissionFieldCoverageSpec` exactly as the `Tree` kinds are, so a row naming a case the
    * algebra does not have is caught by a spec rather than by silence. */
  case RenderedType(kind: String)

  /** a PHASE decides this row, and cites it per declaration. `phase` is the phase's `name`, so the
    * citation and the phase that owes it can be joined without reading either. */
  case Cited(phase: String)

  /** the row discharges at more than one place, and every one of them owes it.
    *
    * Two different facts need this, and both arrived with area S:
    *
    *   - MORE THAN ONE KIND at one surface. An unlabelled jump binds to the innermost enclosing
    *     loop (`JS-S01`), and a loop is four `Tree` kinds — `While`, `For`, `ForEach`, `DoWhile` —
    *     all of which go through `TirEmitter.loopWithJumps`. Attaching the row to one of them would
    *     leave the other three able to render without considering it;
    *   - TWO SURFACES. `JS-S18` (`do`-`while`, which Scala 3 removed) is decided in the frontend,
    *     which maps `CtDo` to a node the language has no keyword for, and again in the emitter,
    *     which chooses `while ({ body; cond }) ()` as the image. Either half alone is a claim about
    *     coverage the other half does not have.
    *
    * A product of enum cases, so `DifferenceTakesNoParameterSpec` admits it by the recursion it
    * already performs — which is the reason it is this and not a `List`. A list is the exact shape a
    * per-library policy takes, and relaxing that spec to hold one would take the whole rule with it.
    *
    * A `Both` whose leaves are not all instrumented is NOT mechanised: `Differences.mechanised`
    * requires every leaf, so a row half of whose discharge nobody built keeps saying so. */
  case Both(a: Attaches, b: Attaches)

  /** NO obligation surface exists for this row yet, and `why` says which one it wants -- a
    * number that can go down rather than a silence that reads as coverage.
    *
    * A construct the frontend REFUSES takes THIS, not [[Lowered]] (no arm to owe the
    * consult) and not [[NoObligation]] (there IS a gap): `SpoonKinds` records the refused
    * kind and the `markers` lane counts every mint.
    *
    * "No surface exists" is a HYPOTHESIS about the engine, twice falsified
    * (`ENGINE-LIMITS.md` T17, the record row): ask whether the surface is missing or only
    * the INFORMATION at it, before writing this case. */
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
final class Obligations private[catalog] (log: CatalogLog, owed: List[DiffId],
                                          private[catalog] val subject: AnyRef = null):
  private var seen: List[DiffId] = Nil

  /** mark `id` considered HERE — reached from [[CatalogLog.markSeen]] for the enclosing scope of a
    * delegated node, and from [[consult]] for this one. */
  private[catalog] def see(id: DiffId): Unit = if !seen.contains(id) then seen ::= id

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
    see(id)
    // …and in every scope this node is ALSO being lowered inside. See `Lowering.of`'s note on the
    // delegation seam: the statement dispatch hands whole nodes to the expression arm, and the arm
    // that considered the difference is the inner one.
    log.markSeen(id)
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

  // ---- the LIVE scope stack, for the delegation seam (`Lowering.of`) ----------------------------
  //
  // A var on the log and not a `given` chain, for the reason `Obligations` is not thread-safe and
  // says so: one lowering is one call stack, and the log is a value ONE RUN owns. It has to live
  // here rather than in a parent pointer on `Obligations` because the inner scope of a delegated
  // node is frequently the ALLOCATION-FREE `unattached` one — nothing attaches at the expression
  // dispatch for that kind — and that instance is shared and can hold no parent.

  /** the attached scopes running right now, INNERMOST FIRST. */
  private var liveScopes: List[Obligations] = Nil
  /** the node whose lowering is running right now, whether or not anything attaches to it. */
  private var currentSubject: AnyRef = null

  /** …and WHERE it is, which is the one thing a `TypeRepr` cannot answer for itself.
    *
    * A type is a value the IR shares across every position that names it, so it has no origin of
    * its own; the node it is being rendered FOR does, and that is the line a reader of a
    * type-surface finding wants to open ([[Typing]]). Kept beside `currentSubject` and restored by
    * the same caller, so the two can never disagree about which scope is innermost. */
  private var origin: Origin = Origin.synthetic

  /** the innermost live scope's origin — `Origin.synthetic` outside every scope, which is honest:
    * nothing is being lowered or rendered, so there is no site. */
  def currentOrigin: Origin = origin

  private[catalog] def enterScope(o: Obligations): Unit = liveScopes ::= o
  private[catalog] def exitScope(): Unit = liveScopes = liveScopes.tail

  /** set the node under lowering and where it is; the caller restores what this returns. */
  private[catalog] def enterSubject(s: AnyRef, at: Origin): (AnyRef, Origin) =
    val prev = (currentSubject, origin)
    currentSubject = s
    // a SYNTHETIC origin says nothing and must not blank out the enclosing node's, which is the
    // only site a reader could open. An origin-less scope inherits rather than overwrites.
    if at != Origin.synthetic then origin = at
    prev
  private[catalog] def exitSubject(prev: (AnyRef, Origin)): Unit =
    currentSubject = prev._1
    origin = prev._2

  /** DISCHARGE `id` in every live scope that is lowering THIS SAME NODE.
    *
    * Innermost first, stopping at the first scope whose subject is a different node — so a consult
    * inside a CHILD (`if (x) y += 1`, the assignment's consult under the `CtIf`'s scope) discharges
    * nothing of the parent's, which is the whole point of asking by identity. */
  private[catalog] def markSeen(id: DiffId): Unit =
    val s = currentSubject
    if s != null then liveScopes.iterator.takeWhile(_.subject eq s).foreach(_.see(id))

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
