package balticporter.core

import balticporter.tir.{SymTag, Symbol}

import java.nio.file.Path

/** Principled, typed directives for replacing constructs the port must NOT
  * translate mechanically with ready-made Scala. A project declares one; the
  * engine applies it at the symbol layer (drops) and the migration injects the
  * provided sources verbatim. This is how a port opts a type or method OUT of
  * mechanical translation and supplies a hand-written or library-backed Scala
  * equivalent in its place — e.g. libGDX's `SharedLibraryLoader`, dropped
  * upstream in favour of a dedicated native-extraction library.
  *
  * The three seams:
  *   - [[dropTypes]]  — a whole type is not translated (excluded from the port
  *                      set); a Scala definition at the same FQCN is expected
  *                      from [[inject]] (or a dependency).
  *   - [[dropMethods]] — a single method is not translated (its `DefDef` is
  *                      dropped from the owning type), leaving the rest of the
  *                      type mechanically ported.
  *   - [[inject]]     — roots of ready-made Scala copied verbatim into the
  *                      emitted sources. Survives re-emit (the migration wipes
  *                      and regenerates its output each run), which is why an
  *                      injected replacement must be declared here rather than
  *                      hand-dropped into the output tree.
  *
  * Keys are fully-qualified: type FQCNs for [[dropTypes]], `owner#method` for
  * [[dropMethods]] (matching the member-key convention used across the engine).
  *
  * A key that matches NOTHING is a silent no-op — the whole point of [[policyReport]]; see
  * [[PolicyReport]] for why that is the same defect class as a silent omission, and [[matched]]
  * for what recording it costs.
  */
final case class Substitutions(
    dropTypes: Set[String] = Set.empty,
    dropMethods: Set[String] = Set.empty,
    inject: List[Path] = Nil,
) extends PolicySource:

  /** Keys that actually fired, accumulated as the frontend consults this value.
    *
    * A deliberate mutable tally on a `case class`, and the reasons, since this is exactly the kind
    * of thing that bites:
    *
    *  - It is NOT a constructor parameter, so it stays out of `equals`/`hashCode`/`toString`/
    *    `copy`. That is the semantics we want — a `Substitutions` IS its policy, and two equal
    *    policies must remain equal however each was exercised — but it also means `copy()` hands
    *    back an EMPTY tally and two structurally equal values can report differently. Read the
    *    report off the instance the frontend was given.
    *  - It accumulates for the LIFETIME of the instance. Translating two source sets with one
    *    value unions their matches, which is right for "did this key ever fire?" and wrong for
    *    "did it fire for THIS set" — call [[resetMatches]] between runs if the second is meant.
    *  - `dropsType`/`dropsMethod` therefore look pure and are not. The alternative — threading a
    *    match log out through `SpoonTir.fromTypes`' return type — changes the frontend's signature
    *    and every call site to report a configuration mistake; the tally keeps the seam at the one
    *    place that knows the answer, and the concurrent set keeps it safe if translation is ever
    *    parallelised.
    */
  @transient private val matchedKeys: java.util.Set[String] =
    java.util.concurrent.ConcurrentHashMap.newKeySet[String]()

  def dropsType(fqcn: String): Boolean =
    val hit = dropTypes.contains(fqcn)
    if hit then matchedKeys.add(fqcn)
    hit

  /** `owner#name` drops EVERY overload of that name; `owner#name(P1,P2)` — the erased parameter
    * type SIMPLE names — drops exactly one. Overload precision is what makes constructors
    * droppable at all: a type's constructors all share the one name `<init>`, so the bare key
    * could only ever mean "drop them all". */
  def dropsMethod(ownerFqcn: String, method: String, paramTypes: List[String] = Nil): Boolean =
    val bare    = s"$ownerFqcn#$method"
    val precise = s"$ownerFqcn#$method(${paramTypes.mkString(",")})"
    // record EVERY declared key this call satisfies: with both forms declared, the bare one drops
    // this overload too, so it has demonstrably fired and is not a typo.
    val hitBare    = dropMethods.contains(bare)
    val hitPrecise = dropMethods.contains(precise)
    if hitBare then matchedKeys.add(bare)
    if hitPrecise then matchedKeys.add(precise)
    hitBare || hitPrecise

  /** declared keys observed to fire so far. */
  def matched: Set[String] = scala.jdk.CollectionConverters.SetHasAsScala(matchedKeys).asScala.toSet

  def unmatchedTypes: Set[String]   = dropTypes -- matched
  def unmatchedMethods: Set[String] = dropMethods -- matched

  /** Forget every recorded match — for reusing one value across independent translations. */
  def resetMatches(): Unit = matchedKeys.clear()

  /** Declared drops that never fired. Sorted so a report is stable run to run.
    *
    * Note what this cannot say: it is only as good as the number of times the frontend was asked.
    * A report read before any translation names EVERY key, which is why the orchestrator reads it
    * after the frontend has run, not before.
    */
  def policyReport: PolicyReport =
    val seen = matched
    def find(setting: String, keys: Set[String], detail: String) =
      (keys -- seen).toList.sorted.map(k => PolicyFinding("substitutions", setting, k, PolicyIssue.NeverMatched, detail))
    PolicyReport(
      find("Substitutions.dropTypes", dropTypes,
        "no type with this fully-qualified name was translated, so nothing was dropped and any " +
          "injected replacement now shadows nothing") ++
        find("Substitutions.dropMethods", dropMethods,
          "no method matched this `owner#name` (or `owner#name(P1,P2)`) key, so nothing was dropped")
    )

object Substitutions:
  val none: Substitutions = Substitutions()

/** Marks a symbol the port must NOT emit, so INTERMEDIATE layers can still see it.
  *
  * A dropped type is deliberately kept in the model and its references stay resolved — dropping it
  * from the parse set instead would leave every use unresolved and silently degrade translation of
  * the code around it. But those uses must be recognisable as substitution targets rather than as
  * ordinary references, because a replacement is not necessarily name- or API-compatible: a
  * transform may need to rewrite `Json.readValue(…)` into a Kindlings codec call, or re-point a
  * type reference somewhere else entirely.
  *
  * So the frontend tags the symbol and any phase can detect it:
  * {{{
  * program.symbolOf(id).flatMap(Substituted.of) match
  *   case Some(Substituted(fqn)) => rewriteIntoReplacement(fqn)
  *   case None                   => leaveAlone
  * }}}
  *
  * The declaration itself is never emitted; what remains at that FQN is whatever the substitution
  * supplies — injected Scala, a rewrite performed by a phase, or nothing at all if every use was
  * rewritten away. Emission is verified against exactly that expectation.
  */
final case class Substituted(fqn: String) extends SymTag

object Substituted:
  def of(sym: Symbol): Option[Substituted] = sym.tags.collectFirst { case s: Substituted => s }
  def tags(sym: Symbol): Boolean           = of(sym).isDefined
