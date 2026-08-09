package balticporter.transform

import balticporter.catalog.FixKind
import balticporter.core.RuntimeArtifact
import balticporter.tir.*

/** The JDK/Scala collection BOUNDARY, counted — every slot the retyping opened and did not close.
  *
  * ==Why this exists==
  * `CollectionsTransform` retypes a library's collection signatures while its bodies, and every
  * JDK type the mapping does not cover, stay where they are. That creates two collection worlds
  * that cannot meet — a problem java does not have, where a `List` IS an `Iterable`
  * (ENGINE-LIMITS K2). `coerce` bridges the slots it knows; everything else arrives at the
  * compiler as a bare `Found: … / Required: …`, which is CLAUDE.md §4.45's exact complaint: an
  * error an agent cannot classify as (a) an engine bug, (b) a phase to configure or (c) a
  * library-specific rule costs it a full investigation, and these are the BULK of a new library's
  * first wall.
  *
  * So the residue is a NUMBER with an origin and a §1 classification, available before any
  * compiler runs. A new port's first wall becomes a triaged list.
  *
  * ==What counts as a stranded slot==
  * Four slot kinds — the same four `coerce` reaches, which is not a coincidence: this measures
  * exactly what that seam did not close.
  *
  *   - a call ARGUMENT against its formal parameter,
  *   - a `val`/field DECLARATION against its initialiser,
  *   - an ASSIGNMENT target against its right-hand side,
  *   - a `return` against the enclosing method's declared return type.
  *
  * …plus one the four cannot express, because it has no formal to compare against: an argument to a
  * call on a SCOPED-OUT receiver, which binds to the JDK's own API (an external symbol carries no
  * signature). See `onScopedReceiver` — that call is the shape a scope produces most often, and it
  * was invisible here while the rewrite was producing uncompilable Scala for it.
  *
  * A slot is STRANDED when the two sides are on opposite sides of one of THREE lines the phase
  * itself drew — the third being the one a port draws itself:
  *
  *   - **JDK against retyped** — one side is a JDK collection-family type the mapping left alone,
  *     the other is a `scala.collection.*` or `balticporter.runtime` type the mapping produced.
  *     `Stream<String> st = f.stream()` is the shape (K6): the value collapsed to a `Buffer`, the
  *     declaration still says `Stream`.
  *   - **shim against scala** — both sides are things the phase produced, but on opposite sides of
  *     the `JavaCollection`/`mutable.Buffer` split the mapping is built on. This is K2's residue
  *     proper, and it is the one a reader would not think to look for, because both types look
  *     "already ported". `m.keySet()` into a `Collection` slot is the standing example, refused on
  *     purpose (`coerce.isKeySetView`) and therefore still counted here: a deliberate refusal is a
  *     finding, not an absence.
  *   - **scoped-out against rewritten** — one side is a declaration the port's
  *     `CollectionsTransform(scope)` deliberately held back, so it kept the JDK type while the code
  *     meeting it moved. This line is the port's own and is the reason a scope is safe to offer at
  *     all: NO wrap can close it (a `mutable.Buffer` is not a `java.util.List`, and the runtime
  *     shims bridge the other direction only), so it is refused, counted, and told which policy
  *     entry to move. A scope whose seams were silent would be worse than no scope.
  *
  *     Note this is the one line whose sites the node types alone CANNOT show — see [[check]]'s
  *     `actualOf`, and read it before trusting a zero here.
  *
  * A wrap `coerce` DID insert is excluded by construction and needs no special case: the wrap is
  * typed as the EXPECTED type, so the two sides agree and the slot is not a boundary at all. Same
  * for any node the phase minted — its symbol carries `NoType`, so it offers no formals and the
  * argument arm skips it.
  *
  * ==Both directions of `Kind`==
  * `scala.Tuple2` is a mapping target and is deliberately NOT on the scala side of the line: it is
  * `Kind.Entry`, a pair, not a collection, so a `Tuple2` meeting a JDK slot is not a collection
  * boundary and reporting it would be noise (`coerce`'s own coverage table says the same).
  *
  * ==Universal, parameterised by the mapping==
  * §1(a) in mechanism — two type families that cannot meet is a fact about java and scala — and it
  * takes the mapping as a PARAMETER, so an empty mapping makes it a no-op by arithmetic. It holds
  * no type list of its own except [[CollectionClosureCheck.jdkFamily]], which is the JDK's, and
  * the stream family below, which is the JDK's too.
  */
object CollectionBoundaryCheck extends RemedySource:

  /** The check's name in `findings.tsv`. */
  val Name = "collection-boundary"

  /** THE MENU — see [[balticporter.tir.Remedy]] and `DESIGN.md` §8.16.
    *
    * Two entries, both `accept`-shaped, both at an EXTERNAL callee, and the reason they are the only
    * two is the ONE-SPELLING rule: a remedy may not be a second way to state something a manifest key
    * already states. Every other act this check's classifications name is already spelled somewhere:
    *
    *   - `UnmappedSubtype` → add the row to `CollectionsTransform(typeMap)`;
    *   - `ScopedOut` → move the declaration in or out of `CollectionsTransform(scope)`. RULED OUT as
    *     a general remedy besides: scope-as-residue-reduction measured `27 -> 47` errors scoped and
    *     `27 -> 51` turned off (`ENGINE-LIMITS.md` K16), so it is safe only for a genuine island;
    *   - `OpaqueEgress`'s WRAP → name the callee's owner in `CollectionsTransform(reflectiveSinks)`
    *     and the phase bridges the argument through `JavaCollections.Reified.toJavaValue`. A bridged
    *     slot never reaches this lane, so a `wrap-at-seam` remedy here would be a second spelling of
    *     that key with no site left to act on.
    *
    * And three acts are ABSENT rather than unspelled, each with the measurement that refused it:
    *
    *   - `wrap-at-seam` for `ShimBoundary`/`ExternalCallee` — a row reaches this lane only where the
    *     phase found NO factory for the pair, which the phase itself now asserts (`ENGINE-LIMITS.md`
    *     K2.5: 5 findings had been misreading a switched-off fix as a residue for the life of a
    *     port). A remedy that forced one would have to invent the converter, and a bridge over a
    *     value the phase REFUSED to move names the wrapper instead of the boundary — the seam stops
    *     being findable (K6.5);
    *   - `copy-detach` for `ExternalCallee` — a copy compiles and DETACHES BOTH DIRECTIONS (§4.4),
    *     which is a behaviour change with no error and no count behind it. `ENGINE-LIMITS.md` K15 is
    *     the measurement that made this lane exist at all; it is not closed by making the seam
    *     silent;
    *   - a second, shim-typed target for `InexpressibleParent` — `ENGINE-LIMITS.md` K5.7: "a second
    *     truth about one java type", every crossing needing a coercion in both directions. And a
    *     coercion for a CONCRETE `ReifiedOccurrence` target is refused structurally (K18): no live
    *     view can BE a `mutable.HashMap`. Neither kind takes an `accept` either — they are known
    *     divergences the engine refuses to repair, not review lists, and accepting one would drain a
    *     defect rather than a question.
    *
    * And the NEIGHBOURING lane declares no menu at all, which belongs here because a reader arriving
    * with a collections question will look at this object first: [[RetargetBoundaryCheck]]'s
    * `ExternalProducer` would need a per-pair wrapper synthesised from the `retarget` table, and
    * `ENGINE-LIMITS.md` K14 refused exactly that — "a coercion would have to arrive as policy beside
    * the entry, a factory FQN the port supplies … that is a table shape, not a rule the engine can
    * derive". A remedy there would be that missing table wearing a one-word name, and the corpus has
    * 0 real producers to measure one against.
    */
  def remedies: List[Remedy] = List(
    Remedy(
      id = "accept-external-callee", lane = Name, kind = Issue.ExternalCallee.toString,
      emissionAffecting = false, fix = FixKind.Parameterised,
      subject = Remedy.Subject.ExternalMember,
      what = "the port has READ this seam and states that the value crossing it is one this callee " +
        "handles — the row moves to remediation(resolved), the emitted text does not change, and " +
        "nothing is wrapped or copied"),
    Remedy(
      id = "accept-opaque-egress", lane = Name, kind = Issue.OpaqueEgress.toString,
      emissionAffecting = false, fix = FixKind.Parameterised,
      subject = Remedy.Subject.ExternalMember,
      what = "the port has READ this external method and states that it does NOT read the runtime " +
        "representation it is handed — the complement of a `reflectiveSinks` entry, which is the " +
        "only answer this review list previously had no way to record"),
  )

  /** what kind of stranding this is, which is what decides who fixes it (CLAUDE.md §1). */
  enum Issue:
    /** the JDK type is a SUBTYPE of one the mapping covers — the closure hole
      * [[CollectionClosureCheck]] reports as a type, met here as a site. */
    case UnmappedSubtype
    /** a JDK collection family the engine deliberately does not retype at all. */
    case UntranslatedFamily
    /** a type that IS in the mapping and reached this slot anyway. */
    case MappedTypeSurvived
    /** both sides are the phase's own output, on opposite sides of the shim/scala split. */
    case ShimBoundary
    /** …and the same slot where the VALUE is not the phase's output at all: it is produced by a
      * call one of the phase's own static arms covers and DECLINED to rewrite, so the emitted text
      * keeps the JDK name and the value is java's. The node's type says otherwise, because the
      * position-blind retyping moved it — which is exactly why this is a separate row and not a
      * [[ShimBoundary]] (whose classification tells a reader to go extend `coerce`). */
    case RefusedSource
    /** one side is a declaration the phase's [[balticporter.tir.RuleScope]] deliberately held back,
      * so it kept its JDK type while the code meeting it moved. */
    case ScopedOut
    /** one side is a method the PROGRAM DOES NOT DECLARE, whose signature is a fact about a
      * compiled class file and cannot be retyped — and whose seam nothing in this check can see,
      * because the position-blind retyping moved the node's type on both sides of it. Recorded BY
      * THE PHASE, at the moment the external signature is still readable, and reported here so a
      * reader finds the whole residue in one place. */
    case ExternalCallee
    /** a class the program declares IMPLEMENTS a java type this phase maps, and the target CANNOT
      * BE A PARENT. The parent is left as java's rather than emitted at a type that cannot carry
      * it — see the classification for why that is the honest answer and not a gap. */
    case InexpressibleParent
    /** an `instanceof` or a downcast at a type this phase retyped, whose TARGET no live view can
      * be. The occurrence is REIFIED — it asks about a runtime object, and the retyping moved
      * neither the objects nor their classes — so it is the one seam with no slot to look at and
      * no compile error behind it. */
    case ReifiedOccurrence
    /** an external callee with a `java.lang.Object` FORMAL, reached by a value this phase cannot
      * prove it did not retype. Nothing is wrong with the slot — the value conforms — and that is
      * the finding: if the callee READS the representation it was handed, the port hands it a
      * different one from the java it was ported from, silently. */
    case OpaqueEgress

  object Issue:
    /** which of §1's three kinds the fix is — the thing a bare typer error cannot say. */
    def classification(i: Issue): String = i match
      case UnmappedSubtype =>
        "§1(b): the JDK type is a subtype of one CollectionsTransform maps, so the mapping is not " +
          "closed downwards — add the type to `typeMap` with a target that keeps the JDK relation " +
          "(CollectionClosureCheck reports the same hole as a type)."
      case UntranslatedFamily =>
        "§1(a) unbuilt, and REFUSED on purpose: this JDK family is not retyped, so the slot has no " +
          "translation and the error is loud rather than silent (ENGINE-LIMITS K6, M6). Closing it " +
          "needs the family retyped, not a wider guard on an existing rewrite."
      case MappedTypeSurvived =>
        "§1(a) engine bug: this type IS in `typeMap`, so no occurrence of it should have survived " +
          "`transformType`. A node minted with a type computed from an unmapped one is the usual cause."
      case ShimBoundary =>
        "§1(a) engine gap: a scala collection meets a `balticporter.runtime` shim slot (or the " +
          "reverse) with no wrap — extend `CollectionsTransform.coerce` to this slot, or, if the " +
          "cell is a deliberate refusal, it stays counted here (ENGINE-LIMITS K2's coverage table)."
      case RefusedSource =>
        "§1(a) engine, and REFUSED on purpose UPSTREAM OF THIS SLOT: the value here comes from a " +
          "call `CollectionsTransform` covers and declined to rewrite — the aliasing " +
          "`Arrays.asList(arr)` is the measured one — so the emitted text keeps the JDK name and " +
          "the value really is a `java.util.*`. The `Found` side above is the NODE's type, which " +
          "the position-blind retyping moved on both sides of that call; do not read it as the " +
          "value's. Do NOT close this by wrapping: a factory over a refused value names the " +
          "WRAPPER instead of the boundary and the refusal stops being findable (ENGINE-LIMITS " +
          "K2.5, K6.5). It closes when the REFUSAL closes — for `asList` that is one frontend fact, " +
          "the erased element type."
      case ScopedOut =>
        "§1(b) PER-LIBRARY: one side of this slot is a declaration this port's " +
          "`CollectionsTransform(scope)` deliberately held back, so it kept its JDK type while the " +
          "other side moved. The direction that CAN be closed already is — a retyped value at a " +
          "held-back java formal goes through `JavaCollections.toJava`, a live view — so what is " +
          "left here is the other one: a value the held-back declaration PRODUCES, arriving where " +
          "the port expects a scala collection. Widen the scope to cover this declaration, or " +
          "narrow it to exclude the other side of the slot too. The engine needs no change; a " +
          "scope that produced this seam SILENTLY would be worse than no scope."
      case InexpressibleParent =>
        "§1(a) engine, and REFUSED on purpose: this class IMPLEMENTS a java type the mapping " +
          "covers, and the target cannot BE a parent — `scala.Tuple2` is final, has no `setValue` " +
          "and takes its two components in its constructor, so `extends Tuple2[K, V]` is three " +
          "errors with no fix available from inside the class. The parent is therefore left as " +
          "JAVA's: the class really does implement `java.util.Map.Entry`, which is on the " +
          "classpath and whose members it already declares, so the class compiles and the error " +
          "moves to the SLOTS where the port hands it to a `Tuple2` — which is where it belongs. " +
          "A second target for the implements-case is NOT the fix: `entrySet()` yields a `Tuple2` " +
          "everywhere in every port, so a shim-typed class would need a coercion at every crossing " +
          "in both directions, which is a second truth about one java type (ENGINE-LIMITS K5.7)."
      case ReifiedOccurrence =>
        "§1(a) engine, and REFUSED on purpose: this is an `instanceof` or a downcast at a type the " +
          "mapping covers — a REIFIED occurrence, which asks about a RUNTIME OBJECT while the " +
          "retyping moved only the static type. `JavaCollections.Reified` answers java's question " +
          "over both representations wherever the target is one a live view can BE " +
          "(`mutable.Buffer`/`Set`/`Map`, the three shims); this site's target is a CONCRETE one " +
          "(`mutable.HashMap`, `ArrayBuffer`, `ArrayDeque`, `scala.Tuple2`) that no view can be, so " +
          "there is nothing to coerce to. Note there is no compile error to look for and never " +
          "was: the emitted test and cast are valid Scala asking a DIFFERENT question, so this " +
          "count is the only instrument that sees the site (`ENGINE-LIMITS.md` K18). Closing it " +
          "needs the mapping to send this java type to an abstract target, or the java code to " +
          "test the interface rather than the implementation class."
      case OpaqueEgress =>
        "§1(b) PER-LIBRARY, and NOTHING IS BROKEN AT THIS SLOT — which is the whole reason it is " +
          "reported. The formal is `java.lang.Object`, so a collection this port retyped conforms " +
          "perfectly and there is no compile error, no wrong type and no failing check to look " +
          "for. What changed is what the CALLEE sees: java handed it a `java.util.*` and this port " +
          "hands it a `scala.collection.*`, so anything that reads the value's runtime " +
          "representation — a serialiser, a bean mapper, an injector, a `toString` — answers " +
          "differently (`ENGINE-LIMITS.md` K21 face 1: a JSON filter emitted " +
          "`{\"scala$collection$mutable$HashMap$$table\":[…]}` where java emitted the entries). " +
          "This row is a REVIEW LIST and not a defect: one line per external method, and most of " +
          "them do not care. Where one does, name its owner in " +
          "`CollectionsTransform(reflectiveSinks)` and the phase bridges the argument through " +
          "`JavaCollections.Reified.toJavaValue` — a run-time, deep, live view, because the " +
          "argument's own static type is `Object` too and nothing at the call site can decide it. " +
          "The engine cannot derive the list: which dependency reflects is a fact about the " +
          "library, and java itself guarantees no such type."
      case ExternalCallee =>
        "§1(a) engine, and REFUSED here on purpose: the other side of this slot is a method the " +
          "program does not declare, so its signature is a fact about a compiled class file and no " +
          "phase can move it. Where a LIVE wrapper exists the phase inserts one " +
          "(`JavaCollections.fromJava`/`toJava`); this site is one where it does not — a target " +
          "with no converter, a nested element type a one-level wrap would silently lie about, or " +
          "a class file the frontend could only partly resolve. A COPY would compile and detach " +
          "both directions (§4.4), so the seam is counted instead. If the dependency is itself " +
          "portable, the answer is to PORT it rather than to bridge it. Where the formal is " +
          "`java.lang.Object` there is no compile error to look for and never was: the retyped " +
          "value CONFORMS, and what changed is what the callee's `toString`, `instanceof` and " +
          "serializer see."

  /** one stranded slot. */
  final case class Finding(issue: Issue, slot: String, expected: String, actual: String, origin: Origin, enclosing: SymId):
    def detail: String = s"$slot: Found $actual / Required $expected"
    def render: String = s"$issue $slot — Found $actual / Required $expected  (${origin.javaPath}:${origin.line})"
    def report(using program: Program): CheckReport.Finding =
      CheckReport.Finding(Name, issue.toString, owner, CheckReport.relativise(origin.javaPath),
                          origin.line, detail)

    /** the string both artifacts key on — the drained row's `remediation(resolved)` finding uses this
      * one too, so a reader can join the two halves of the move by the column they are looking at.
      * For the two remedied kinds `enclosing` is the CALLEE, which is what a selection names. */
    def owner(using program: Program): String =
      program.symbolOf(enclosing).map(_.fullName).getOrElse("?")

  /** DRAIN what this port selected — see [[remedies]] and `CLAUDE.md` §5. Returns the findings that
    * remain; the rest are in the plan's ledger and become `remediation(resolved)` rows. */
  def resolved(plan: ResolutionPlan, findings: List[Finding])(using Program): List[Finding] =
    plan.drain(remedies, findings)(f =>
      ResolutionPlan.Residue(f.issue.toString, f.enclosing, f.owner, f.origin, f.detail))

  /** JDK collection families that are NOT in the closure of anything `typeMap` covers and are not
    * retyped at all. `java.util.stream` is the whole of it today and the reason the list exists:
    * its refusal is deliberate and its sites must still be counted, or "we know about that one"
    * lives in prose (CLAUDE.md §5.1's objection to a hand-maintained expected-failure list). */
  val untranslatedFamilies: List[String] = List("java.util.stream.")

  private enum Side:
    case Jdk, Shim, Scala, Universal, Other

  /** which side of the boundary a type is on, decided from the MAPPING's own targets wherever a
    * choice exists.
    *
    * The shim side is exactly `targets` restricted to the runtime package — not a hardcoded list
    * of three names — so a mapping that adds a fourth shim widens this with nothing to edit. The
    * scala side is decided by PACKAGE rather than by membership, because the phase also mints
    * `scala.collection.Set` (the `keySet` view type) and `scala.collection.mutable.Buffer` (the
    * stream collapse's type) without either being a `typeMap` target.
    *
    * `Universal` is `java.lang.Object` and is its own side rather than `Other`, because it is the
    * one type at which a retyped value CONFORMS — so a slot against it produces no compile error
    * and had therefore been reported by nothing at all, while the callee behind it (a serializer, a
    * `toString`, an `instanceof`) sees a value java never handed it. `Other` keeps its meaning of
    * "not a party to this boundary", which every third-party type genuinely is. */
  private def sideOf(fqn: String, shims: Set[String]): Side =
    if shims.contains(fqn) then Side.Shim
    else if fqn.startsWith("scala.collection.") then Side.Scala
    else if CollectionClosureCheck.jdkFamily.contains(fqn) || untranslatedFamilies.exists(fqn.startsWith) then Side.Jdk
    else if fqn == CollectionsTransform.ObjectFqn then Side.Universal
    else Side.Other

  /** Every stranded slot in `program`, which must be the program AFTER the phase ran: this counts
    * the residue the retyping created, so running it before means counting nothing.
    *
    * `mapped` is `CollectionsTransform.mappedTypes` and `targets` is `retypedTargets` — the
    * phase's own policy, read back, so the check concludes about a type only from what the phase
    * did to it (CLAUDE.md §4.56). */
  def check(program: Program, mapped: Set[String], targets: Set[String] = Set.empty,
            scopedOut: Set[SymId] = Set.empty): List[Finding] =
    check(program, program.units, mapped, targets, scopedOut)

  /** …restricted to the units the run actually EMITS — the same filter `OmissionCheck` and
    * `PortabilityCheck.inEmittedCode` carry, and for the same measured reason: a DEPENDENT port's
    * `Program` holds the base module's units too (`FrontendConfig.resolutionRoots`), and a stranded
    * slot inside one of those is the BASE's finding, reported by a repository that cannot act on it
    * (ENGINE-LIMITS D2). A base port passes `program.units` and this is the identity. */
  def check(program: Program, units: List[Tree.ClassDef], mapped: Set[String], targets: Set[String],
            scopedOut: Set[SymId]): List[Finding] =
    val out   = collection.mutable.ListBuffer[Finding]()
    val shims = targets.filter(_.startsWith(RuntimeArtifact.Package + "."))
    given Program = program

    def fqn(t: TypeRepr): Option[String] = headSym(t).flatMap(program.symbolOf).map(_.fullName)

    def issueFor(jdk: String, scoped: Boolean, external: Boolean = false): Issue =
      // A MAPPED type reached this slot from a CLASS FILE's own signature, which is not the same
      // fact as one surviving `transformType` and must not be reported as one. `MappedTypeSurvived`
      // reads "§1(a) engine bug — no occurrence of this type should have survived", and sending a
      // reader after a phase that never had the chance to move it costs the full investigation
      // §4.45 is about. It became reachable the day the frontend started interning external members
      // with their `MethodType`: before that this arm never saw an external formal at all.
      if external && mapped.contains(jdk) then Issue.ExternalCallee
      else if scoped && mapped.contains(jdk) then Issue.ScopedOut
      else if mapped.contains(jdk) then Issue.MappedTypeSurvived
      else if untranslatedFamilies.exists(jdk.startsWith) then Issue.UntranslatedFamily
      else if CollectionClosureCheck.supertypesOf(jdk).exists(mapped.contains) then Issue.UnmappedSubtype
      // a JDK family type with no mapped supertype at all: the mapping never touched anything it
      // relates to, so the slot is a gap in COVERAGE rather than a broken relation.
      else Issue.UnmappedSubtype

    /** The type a term REALLY has, which for a reference to a SCOPED-OUT declaration is not the one
      * the node carries — `CollectionsTransform.scopedType`, which the TRANSFORM reads through the
      * same function so that what it refuses to rewrite and what this counts are drawn on one line.
      * Read its doc before trusting a zero here. */
    def actualOf(t: Term): (TypeRepr, Boolean) =
      CollectionsTransform.scopedType(t, scopedOut).map(_ -> true).getOrElse(t.tpe -> false)

    /** is this callee a THIRD PARTY's, rather than one of the collection API's own members?
      *
      * `CollectionsTransform.externalCallee`'s second exclusion, restated where this check can ask
      * it: `java.util.List#indexOf(Object)` is an external member with a universal formal, and its
      * RECEIVER has already been retyped, so the call binds to scala's own `indexOf` and the class
      * file's formal describes nothing that will be emitted. Reading it would put a row on every
      * such call. The narrower question is asked ONLY by the universal arm, so the existing
      * `expectedExternal` classification is untouched. */
    def foreign(m: SymId): Boolean =
      !program.owns(m) && !program.symbolOf(m).flatMap(c => program.symbolOf(c.owner))
        .exists(o => mapped.contains(o.fullName) || targets.contains(o.fullName))

    /** is this value produced by a call the phase's own static arms cover and DECLINED to rewrite?
      *
      * `CollectionsTransform.refusedRewriteSource`'s question, asked from the reporting side and
      * keyed the same way (§4.56 — the phase's own table, never a test on a name). Every arm that
      * FIRED left its minted helper's symbol behind, so a callee still standing at one of these
      * `owner#name` keys is one the phase left under the JDK's name. */
    def refusedSource(t: Term): Boolean = t match
      case a: Tree.Apply =>
        program.symbolOf(a.method).flatMap(c => program.symbolOf(c.owner).map(o => MemberKey(o.fullName, c.name).render))
          .exists(CollectionsTransform.handledStatics.contains)
      case _ => false

    def slot(kind: String, expected: TypeRepr, actual: Term, origin: Origin, enclosing: SymId,
             expectedScoped: Boolean, expectedExternal: Boolean = false,
             expectedForeign: Boolean = false): Unit =
      val (actualT, actualScoped) = actualOf(actual)
      val scoped = expectedScoped || actualScoped
      (fqn(expected), fqn(actualT)) match
        case (Some(e), Some(a)) if e != a =>
          (sideOf(e, shims), sideOf(a, shims)) match
            // `expectedExternal` describes the EXPECTED side only, so it is passed on the arm where
            // the JDK type came from there and nowhere else.
            case (Side.Jdk, Side.Scala | Side.Shim) => out += Finding(issueFor(e, scoped, expectedExternal), kind, e, a, origin, enclosing)
            case (Side.Scala | Side.Shim, Side.Jdk) => out += Finding(issueFor(a, scoped), kind, e, a, origin, enclosing)
            case (Side.Shim, Side.Scala) | (Side.Scala, Side.Shim) =>
              // …and the one shape whose `Found` side is not what the emitter printed. The phase's
              // own static table answers it — a call still standing at one of those names is a call
              // it declined to rewrite (`CollectionsTransform.handledStatic`, restated where this
              // check can ask it), so the value is java's however the node reads.
              out += Finding(if refusedSource(actual) then Issue.RefusedSource else Issue.ShimBoundary,
                             kind, e, a, origin, enclosing)
            // java's UNIVERSAL formal, at a CLASS FILE. The pair fell through this match entirely —
            // `java.lang.Object` was `Other` — which is exactly why it was the seam nothing could
            // report: it produces no compile error either, because a retyped collection conforms.
            // `CollectionsTransform.coerce` now bridges it with `toJava` wherever a live view exists
            // (and a bridged slot never reaches here, its two sides agreeing), so what is left is
            // the refusals: an element type a one-level view would lie about, and a shim source.
            // OWNED callees are excluded by `expectedForeign` — their `Object` formal belongs to
            // scala this port emits, and the scala collection is what it wants — and so are the
            // collection API's own members, whose receiver has already moved (see `foreign`).
            case (Side.Universal, Side.Scala | Side.Shim) if expectedForeign =>
              out += Finding(Issue.ExternalCallee, kind, e, a, origin, enclosing)
            case _ => ()
        case _ => ()

    val scan = new Phase:
      def name: String = "collection-boundary-check"

      /** the enclosing DEFINITION, for attribution. The traversal is bottom-up, so this is set by
        * the DefDef hook AFTER its body — which is why the returns are collected from the DefDef
        * hook itself rather than as they are visited. */
      override def transformApply(t: Tree.Apply)(using Program): Term =
        val formals = program.symbolOf(t.method).map(_.info).collect {
          case TypeRepr.MethodType(ps, _, _)                       => ps.map(_._2)
          case TypeRepr.PolyType(_, TypeRepr.MethodType(ps, _, _)) => ps.map(_._2)
        }.getOrElse(Nil)
        if formals.sizeIs == t.args.size then
          val external = !program.owns(t.method)
          val third    = foreign(t.method)
          t.args.zip(formals).foreach((a, f) =>
            slot("argument", f, a, a.origin, t.method, scopedOut(t.method), external, third))
        else onScopedReceiver(t)
        t

      /** The scope seam the four slot kinds CANNOT reach: an argument whose FORMAL is unknown.
        *
        * A call on a scoped-out declaration binds to the JDK's own API — `b.raw.addAll(mine)` where
        * `raw` kept its `java.util.List` — and `java.util.List#addAll` is an EXTERNAL symbol the
        * frontend interned with no signature, so the arm above has no formal to compare against and
        * skips the call entirely. Measured: the probe that found this reported ZERO stranded slots
        * while emitting an in-scope `Buffer` into a `java.util.Collection` parameter.
        *
        * So the JDK's own contract stands in for the missing formal, and only where the phase's own
        * record licenses it: the receiver must resolve THROUGH A SCOPED-OUT DECLARATION (never from
        * the node's type, which the position-blind retyping already moved) to a type the mapping
        * covers. A `java.util.List` receiver takes java collections and nothing else, so an argument
        * on the scala or shim side of the line is stranded whatever the method's arity. Gated on
        * `scopedOut`, so an empty scope cannot reach this at all. */
      private def onScopedReceiver(t: Tree.Apply)(using Program): Unit = t.fun match
        case Tree.Select(recv, _, _, _) =>
          val (recvT, recvScoped) = actualOf(recv)
          if recvScoped then
            fqn(recvT).filter(r => sideOf(r, shims) == Side.Jdk).foreach { r =>
              t.args.foreach { a =>
                val (actualT, _) = actualOf(a)
                fqn(actualT).filter(x => sideOf(x, shims) == Side.Scala || sideOf(x, shims) == Side.Shim)
                  .foreach(x => out += Finding(issueFor(r, scoped = true), "argument (scoped-out receiver)",
                                              r, x, a.origin, t.method))
              }
            }
        case _ => ()

      override def transformTerm(t: Term)(using Program): Term =
        t match
          case a: Tree.Assign =>
            val (lhsT, lhsScoped) = actualOf(a.lhs)
            slot("assignment", lhsT, a.rhs, a.origin, SymId.None, lhsScoped)
          case _ => ()
        t

      override def transformValDef(t: Tree.ValDef)(using Program): Tree.ValDef =
        t.rhs.foreach(r => slot("declaration", t.tpt.tpe, r, t.origin, t.symbol, scopedOut(t.symbol)))
        t

      override def transformDefDef(t: Tree.DefDef)(using Program): Tree.DefDef =
        t.rhs.foreach(b => returnsIn(b).foreach { r =>
          r.expr.foreach(e => slot("return", t.returnTpt.tpe, e, r.origin, t.symbol, scopedOut(t.symbol)))
        })
        t

    units.foreach(u => StandardTraversal.mapClassDef(scan, u))
    out.toList

  /** every `return` that belongs to THIS method — the same DELIBERATELY BOUNDED walk
    * `CollectionsTransform.coerceReturns` performs, for the same reason and with the same failure
    * direction.
    *
    * A `return` inside a lambda, an anonymous class's method or a local class returns from THAT,
    * so comparing it against the enclosing method's declared type would be a WRONG finding. Only
    * the statement-carrying node kinds are followed and the default arm does NOT descend, which
    * makes a node kind added later a MISSED finding — loud by construction, never wrong. (Java's
    * `return` is a statement, so it cannot occur inside an argument or an operand.)
    *
    * It is a separate walk from the transform's because that one MAPS and this one COLLECTS, and
    * `CollectionsTransformSpec`/`CollectionBoundaryCheckSpec` pin the two against each other: a
    * lambda-local `return` is asserted uncoerced there and unreported here, so the day the two
    * lists diverge one of them fails. */
  def returnsIn(t: Term): List[Tree.Return] = t match
    case x: Tree.Return       => List(x)
    case x: Tree.Block        => x.stats.collect { case s: Term => returnsIn(s) }.flatten ++ returnsIn(x.expr)
    case x: Tree.If           => returnsIn(x.thenp) ++ returnsIn(x.elsep)
    case x: Tree.While        => returnsIn(x.body)
    case x: Tree.DoWhile      => returnsIn(x.body)
    case x: Tree.For          => returnsIn(x.body)
    case x: Tree.ForEach      => returnsIn(x.body)
    case x: Tree.Synchronized => returnsIn(x.body)
    case x: Tree.Try          => returnsIn(x.body) ++ x.catches.flatMap(c => returnsIn(c.body)) ++ x.finalizer.toList.flatMap(returnsIn)
    case x: Tree.Match        => x.cases.flatMap(c => returnsIn(c.body))
    case _                    => Nil

  private def headSym(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSym(tc)
    case _                           => scala.None

  /** grouped one-line summary, worst family first, each with its §1 classification — the whole
    * point of the check is that a reader does not have to work out who fixes it. */
  def summary(fs: List[Finding]): String =
    if fs.isEmpty then "  none"
    else
      fs.groupBy(_.issue).toList.sortBy((_, v) => -v.size).map { (issue, vs) =>
        val head = s"  ${vs.size} × $issue\n  ${Issue.classification(issue)}"
        val sites = vs.groupBy(f => (f.slot, f.expected, f.actual)).toList.sortBy((_, v) => -v.size).take(10)
          .map { case ((slot, e, a), ss) => s"    ${ss.size} × $slot: Found $a / Required $e" }
        (head :: sites).mkString("\n")
      }.mkString("\n")
