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
  * FOUR DISCHARGE SURFACES, ONE LOG. Most rows do not discharge in the frontend at all:
  *
  *   - **frontend lowering** (Java AST → TIR) — [[Lowering.of]] at `SpoonTir`'s statement and
  *     expression dispatches, [[Obligations]] inside;
  *   - **emitter rendering** (TIR → Scala text) — [[Rendering.of]] at `TirEmitter`'s `stat`/`term`
  *     dispatches, keyed on the `Tree` kind rather than on the Java node kind;
  *   - **types** — [[Typing]], at `SpoonTir.tpe` and `TirEmitter.tpe`. One surface with two ends,
  *     because a TYPE is not a node at either: a `CtTypeReference` is not a statement or an
  *     expression, and a `TypeRepr` is not a `Tree` at all;
  *   - **phases** (whole-program rewrites) — a phase does not walk one node kind, so a wrapper is
  *     the wrong shape. A phase CITES its row through [[CatalogLog.cite]], one row per DECLARATION
  *     it decides about, exactly the granularity `Decision` already uses (`CLAUDE.md` §5.1: one row
  *     per declaration, never one per expression). Deliberately weaker than an obligation, and
  *     reported as its own thing: nothing can assert that a phase *should have* considered a
  *     difference at a declaration it never visited.
  *
  * The four feed ONE log, because the coverage question is "was this row reached at all,
  * anywhere" and four per-surface artifacts would answer four narrower questions and not that one.
  *
  * A row whose surface does not exist carries [[Attaches.Unmechanised]] and is COUNTED in its own
  * lane, because a lane reporting it as fine on the strength of a mechanism nobody built would be
  * worse than no lane. That number has gone 112 → 88 → 47 → 20 → 10 as the surfaces landed, and it
  * is the only honest alternative to measuring: a number that can go down, rather than a silence
  * that reads as coverage.
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
    * shared, allocation-free [[Obligations]] and skips the settle entirely.
    *
    * ==`subject` and the DELEGATION SEAM==
    *
    * The frontend's two dispatches are not disjoint: a node reached as a STATEMENT is routinely
    * handed straight to the EXPRESSION arm (`case inv: CtInvocation => expr(inv)`), so ONE node is
    * lowered inside TWO scopes, and the consults all happen in the inner one. A row attached at
    * `(kind, Statement)` would then be reported as a hole at every such node while the arm had in
    * fact considered it — a phantom on the work list, which is the one thing a work list may not
    * have. No row is in that position today; every one of them would be, the moment a statement
    * kind whose arm delegates gains an attachment.
    *
    * So a consult marks the row seen in the enclosing scope too, and the scopes are joined by NODE
    * IDENTITY (`subject`) rather than by kind or by origin. Identity is the exact question: two
    * different nodes of the same kind on one line (`x += (y += 1)`) are two obligations, and reading
    * `at` or `kind` would silently discharge the outer one from the inner node's consult. */
  def of[A](kind: String, dispatch: Dispatch, at: Origin, subject: AnyRef)(body: Obligations ?=> A)(using log: CatalogLog): A =
    scoped(Differences.owedAt(kind, dispatch), kind, dispatch, at, subject)(body)

  /** the scope both dispatch surfaces enter — ONE implementation, because the delegation seam, the
    * allocation-free fast path and the settle are the same question at either end of the pipeline
    * and two copies would be two answers (`ENGINE-LIMITS.md` F8). */
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

/** THE EMITTER'S HALF — the same wrapper at the OTHER end of the pipeline, keyed on the `Tree` kind.
  *
  * §2.3(c)'s second discharge surface. Most `JS-S` rows do not discharge in the frontend at all: a
  * `switch` with no `default`, a `break` in the middle of a case, a `boundary` the emitter
  * interposes, a `try`'s resources, a labelled statement — every one of them is a decision about
  * TEXT, taken while rendering, and the frontend has already done its job correctly by the time they
  * arise. A lowering-only mechanism can say nothing about any of them, which is why they carried
  * `Attaches.Unmechanised` and were COUNTED rather than claimed.
  *
  * Two things are deliberately identical to [[Lowering]] and one is deliberately different.
  *
  * IDENTICAL: the wrapper is at the DISPATCH (`TirEmitter.stat` / `TirEmitter.term`), so no arm can
  * decline to wrap; and the scopes are joined by NODE IDENTITY, because the emitter has the same
  * delegation seam the frontend does — `stat` hands every `Term` straight to `term`, so one node is
  * rendered inside two scopes and the consults all happen in the inner one.
  *
  * DIFFERENT: there is no [[Dispatch]]. Java gives one node kind two meanings by POSITION (`i += 1`
  * as a statement discards its value; as an expression it yields one), which is a fact about JLS
  * 14.8 vs 15.26.2 and is why the frontend's key carries it. The TIR has already resolved that
  * question — the position is in the tree — so a second axis here would be a distinction with no
  * fact behind it. `Dispatch.Either` is what the shared machinery records for a rendering, and it
  * reads correctly: both of the emitter's dispatches owe it.
  */
object Rendering:

  /** Enter the obligation scope for ONE `Tree` node, then render it.
    *
    * `kind` is the node's `productPrefix` — the same name `EmissionFieldCoverageSpec` derives from
    * the class files, so a row attaching to a kind the IR does not have is caught by a spec rather
    * than by silence. */
  def of[A](kind: String, at: Origin, subject: AnyRef)(body: Obligations ?=> A)(using log: CatalogLog): A =
    Lowering.scoped(Differences.owedAtRender(kind), kind, Dispatch.Either, at, subject)(body)

/** THE FOURTH SURFACE — a TYPE, at both ends of the pipeline.
  *
  * The first three surfaces are all about a NODE: a java statement or expression ([[Lowering]]), a
  * `Tree` ([[Rendering]]), a declaration a whole-program pass decided about ([[CatalogLog.cite]]).
  * A whole family of differences is about none of them — a use-site wildcard, a raw type's fill, an
  * F-bound no instantiation can eliminate, a type variable with no binder in scope, a nested type
  * that is path-dependent in one language and not in the other. Every one is decided while a TYPE
  * is lowered or rendered, and a type is not a node at either end: a `CtTypeReference` is not a
  * `CtStatement` or a `CtExpression`, and a `TypeRepr` is not a `Tree` at all — it is the algebra a
  * `TypeTree` carries, and the `TypeTree` is rendered through its parent.
  *
  * So neither existing wrapper could enter one, and ten rows carried [[Attaches.Unmechanised]]
  * saying exactly that. This is the surface that retires them.
  *
  * ONE SURFACE, TWO ENDS — the same shape the node surface has, and for the same reason. The
  * pipeline has two ends and a type is decided at both: the frontend chooses the IMAGE (what a raw
  * use fills with, whether `? super Object` is a wildcard at all, which variable has no binder) and
  * the emitter chooses the TEXT (`? <: X`, a projection or a value path, the `?` that stands in for
  * a marker). Two [[Attaches]] cases rather than one because the KEYS are two different
  * vocabularies: Spoon's reference-interface names on one side — `SpoonKinds.references`, whose
  * totality is derived from `spoon.reflect.reference` exactly as the node registry's is from
  * `code`/`declaration` — and the `TypeRepr` case's own `productPrefix` on the other.
  *
  * NEITHER CARRIES A [[Dispatch]], for [[Rendering]]'s reason: java gives a NODE two meanings by
  * position (JLS 14.8 vs 15.26.2), and a type reference has only ever had one.
  *
  * ==The origin, which the emitter half does not have==
  *
  * A `CtTypeReference` is a `CtElement` with a source position, so the frontend passes its own. A
  * `TypeRepr` carries none and cannot: it is a VALUE the IR shares between every position naming
  * the same type, so there is no one place it was written. What does exist is the origin of the
  * node the type is being rendered FOR, which is what [[CatalogLog.currentOrigin]] holds. Reporting
  * that is exact rather than approximate — a finding's job is to name a java file and line somebody
  * can open, and the line where the type was NAMED is the one they want — where an
  * `Origin.synthetic` would put `-`/0 on every type-surface finding in the catalog, which is a
  * diagnostic nobody can act on.
  */
object Typing:

  /** the FRONTEND's type-reference dispatch — `SpoonTir.tpe`.
    *
    * `kind` is the registry name of the Spoon *reference* interface, resolved by
    * `SpoonKinds.refNameOf`'s most-specific rule — so a `CtWildcardReference` is not silently read
    * as the `CtTypeParameterReference` its implementation extends, which is the same absorption
    * `SpoonKinds` exists to prevent one package over. */
  def ofReference[A](kind: String, at: Origin, subject: AnyRef)(body: Obligations ?=> A)(using log: CatalogLog): A =
    Lowering.scoped(Differences.owedAtLowerType(kind), kind, Dispatch.Either, at, subject)(body)

  /** the EMITTER's type dispatch — `TirEmitter.tpe`. No `at`: see this object's header. */
  def ofRepr[A](kind: String, subject: AnyRef)(body: Obligations ?=> A)(using log: CatalogLog): A =
    Lowering.scoped(Differences.owedAtRenderType(kind), kind, Dispatch.Either, log.currentOrigin, subject)(body)

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
  * the exact shape a per-library policy takes. A row that genuinely discharges at two surfaces has
  * [[Attaches.Both]] instead — a product of enum cases, which the spec admits by the recursion it
  * already performs.
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
