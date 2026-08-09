package balticporter.transform

import balticporter.catalog.{FixKind, Platform}
import balticporter.core.{PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*

/** THE PORTABILITY MENU — `Remediator`'s loop, closed from the other end.
  *
  * ==What was already here, and what was missing==
  * `Remediator` verifies three templates against the program and prints the manifest line that would
  * fix each. It is deliberately narrow — a template that cannot verify its precondition does not
  * fire, and a wrong suggestion costs its reader more than silence would — and it has always
  * stopped one step short: a human reads the snippet, opens the manifest and pastes it. That
  * round trip is the whole of `DESIGN.md` §8.16's argument, and this phase is the other end of it.
  * A port SELECTS one of the templates at the location it applies to
  * (`PortManifest.resolutions`), and the engine performs it — the same precondition, verified by
  * the same code, at a position in the pipeline where the tree can still be changed.
  *
  * ==§1(b), and where the line falls==
  * The MECHANISM is the engine's: which shapes are safe to drop, inline or redirect is a fact about
  * java and about this program, and it is `Remediator`'s already. What is the PORT'S is (i) WHICH
  * locations, which arrives as a `resolutions` selection and not as a parameter here, and (ii) the
  * one VALUE a template cannot compute — the destination table for `class-table`, which is a type
  * the port has to write. Both empty is the no-op: with no selections and no table this phase
  * returns its input.
  *
  * ==Why a phase, and why placed LAST in `surface`==
  * `Remediator` runs AFTER the pipeline, over the final program, and this runs INSIDE it. Those are
  * two different programs and `CLAUDE.md` §5's dry-run rule is exactly about the difference: a phase
  * measures what it is HANDED, so a chokepoint that exists after `StaticForwarderTransform` has
  * inlined nine members may not exist before it. So this phase is placed LAST in a port's `surface`
  * (the namespace rename still runs after it, §4.56), which is as close to `Remediator`'s own view
  * as a phase can stand — and the residue lane is what reconciles the two: whatever this did not
  * take out, `Remediator` still suggests.
  *
  * ==The four answers, and the three refusals that are not silence==
  * Every remedy here can decline, and a decline is a COUNTED row naming its guard
  * (`Resolution.RefusedKind`), never a selection that quietly did nothing. That is `CLAUDE.md` §3's
  * refusal-enumeration rule read at a menu: a count of applications says nothing about what was
  * declined, and the three guards have three different next actions —
  *
  *   - `substitutions-drop` fires only at `Remediator`'s HIGH grade, which is the measured claim
  *     that every site of the chokepointed APIs is inside this one type AND no other unit in the
  *     port references it. At the MEDIUM grade the drop still needs an injected replacement at the
  *     same FQN, which is a file this phase cannot write and a `Substitutions(inject = …)` key it
  *     has no standing to add — so it declines and says so;
  *   - `class-table` applies the mechanism and cannot invent the table: a redirect with no
  *     destination would leave the lookup reflective and the port JVM-only with nothing said, which
  *     is the exact silent no-op `ClassTableTransform`'s own doc calls out. So a selection with no
  *     entry in [[classTables]] is a classified refusal;
  *   - `static-forwarder-inline` fires where the template verified the receiver-first shape, and
  *     inlines exactly the members `Remediator` did NOT exclude — a member that forwards to an
  *     `X#m` which is itself unportable relocates the dependency rather than removing it.
  *
  * ==What is deliberately NOT on this menu==
  *   - a MEDIUM-grade drop with an engine-written replacement. The replacement is library semantics
  *     (`ENGINE-LIMITS.md` P1's family — a loud-refusal facade is a per-library decision about which
  *     paths are real), and a stand-in that compiles and silently does nothing is what
  *     `CLAUDE.md` §1 refuses;
  *   - a `dropMethods`-scale drop of only the touching members. `Remediator` OBSERVES it (the ratio
  *     is stated rather than thresholded) and it is not offered here, because "nothing calls them"
  *     is the port's judgement about its own consumers and not a fact this program holds;
  *   - `accept-jvm-only`, which is not a phase's remedy at all: it changes no tree and re-files a
  *     row, so it belongs to the CHECK that mints the row (`PortabilityCheck`).
  *
  * @param classTables the destination for a `class-table` selection, keyed by the SAME manifest key
  *                    the selection uses (`owner#member` of the CALLEE), valued `owner#member` of
  *                    the table's lookup. Empty is the no-op and makes every such selection a
  *                    counted refusal rather than a silent one.
  * @param targets     WHICH BACKENDS the questions are asked for — `PortabilityCheck`'s own §1(b)
  *                    parameter, with `PortabilityCheck`'s own default: all three, i.e. every
  *                    question the check asked before it had one. A port that narrowed
  *                    `PortManifest.targets` states the same set here, and the phase then reasons
  *                    about the same rule list its run reports on.
  */
final class RemediationTransform(
    val classTables: Map[String, String] = Map.empty,
    val targets: Set[Platform] = Platform.values.toSet,
) extends Phase, RemedySource, PolicyBound, PolicySource, SurfacePolicy:

  def name: String = RemediationTransform.Name

  def remedies: List[Remedy] = RemediationTransform.Remedies

  /** Two ports that resolve different locations emit different signatures for shared code — a
    * dropped type is not there at all — so the selections are surface. They live in the MANIFEST and
    * are fingerprinted there (`PortManifest.surfaceDigestInputs`); what this adds is the one value
    * that is a phase parameter, so a base and a dependent redirecting one lookup at two different
    * tables cannot compare equal. */
  def surfaceFingerprint: String =
    classTables.toList.sorted.map((k, v) => s"$k->$v").mkString(",") +
      "|" + targets.toList.map(_.toString).sorted.mkString(",")

  private var plan: ResolutionPlan   = ResolutionPlan.empty
  private var binder: Option[PolicyBinder] = scala.None
  private var unusedTables: List[String]   = Nil

  def bindPolicy(b: PolicyBinder): Unit =
    plan   = b.resolutions
    binder = Some(b)

  /** A table entry no selection reaches is DEAD POLICY, and the failure mode is the §1(b) silent
    * no-op: the port wrote a destination, the lookup stayed reflective, and nothing said so. It is
    * computed in `run` rather than at bind time because the question is not "did this key name a
    * member" — the binder answers that for the SELECTION — but "did any selection ask for this
    * table". */
  def policyReport: PolicyReport =
    PolicyReport(unusedTables.sorted.map(k =>
      PolicyFinding(name, "RemediationTransform(classTables) key", k, PolicyIssue.NeverMatched,
        s"no `${RemediationTransform.ClassTable.id}` selection names this callee, so the table is " +
          "never consulted — either add the `resolutions` entry or remove the table row")))

  // -------------------------------------------------------------------------------------------

  override def run(program: Program): Program =
    if plan.isEmpty then program
    else
      val rules      = PortabilityCheck.rulesFor(targets)
      val violations = PortabilityCheck.check(program, rules)
      // The SAME verification, by the same code, that prints the snippet a human would paste. A
      // second implementation of "is this a chokepoint" would be free to disagree with the one the
      // report shows, which is the shape §4.6 calls two answers to one question.
      val fixes = Remediator.suggest(program, violations)
      val asked = collection.mutable.Set.empty[String]

      var p = program
      p = applyDrops(p, fixes, violations)
      p = applyForwarders(p, fixes)
      p = applyClassTables(p, asked)
      unusedTables = (classTables.keySet -- asked).toList
      p

  // ---- substitutions-drop ---------------------------------------------------------------------

  private def applyDrops(
      program: Program,
      fixes: List[Remediator.Suggestion],
      violations: List[PortabilityCheck.Violation],
  ): Program =
    val chosen = program.units.flatMap { u =>
      plan.selected(u.symbol, RemediationTransform.Drop).map(u -> _)
    }
    if chosen.isEmpty then program
    else
      val fqn = (s: SymId) => program.symbolOf(s).map(_.fullName).getOrElse("?")
      val dropped = chosen.flatMap { (unit, r) =>
        val name = fqn(unit.symbol)
        fixes.find(f => f.mechanism == RemediationTransform.Drop.id && f.subject == name) match
          case Some(f) if f.confidence == Remediator.Confidence.High =>
            val sites = f.payload.get("sites").flatMap(_.toIntOption).getOrElse(1)
            plan.applied(r, name, unit.symbol, unit.origin,
              s"dropped this type outright — ${f.observed}", drained = sites)
            Some(unit.symbol)
          case Some(f) =>
            plan.refuse(r, name, unit.origin, "needs-injection",
              s"${f.confidence.label}: ${f.observed}. Dropping it here would leave every referring " +
                "type naming a class this port does not emit, and the replacement at the same FQN is " +
                "a file only the port can write — declare `dropTypes` WITH `inject` in the manifest " +
                "instead [§1(b)]")
            scala.None
          case scala.None =>
            plan.refuse(r, name, unit.origin, "not-a-chokepoint",
              "no portability site inside this type is chokepointed here, so dropping it removes no " +
                "unportable API — the selection is aimed at a type this run has nothing to say about " +
                "[§1(b): check the `remediation` lane for the type the sites really cluster in]")
            scala.None
      }.toSet
      if dropped.isEmpty then program
      else
        // The unit LEAVES the program, which is what a `Substitutions.dropTypes` entry would have
        // done at the frontend — with one difference that is the whole reason the HIGH grade is the
        // only one offered: a frontend drop still parses the type so every reference resolves, and
        // this one cannot, so it is only sound where nothing refers to it. The pipeline rebuilds the
        // xref after every phase, so the sites inside it stop being usages and BOTH portability
        // lanes fall by exactly the count recorded above.
        program.rebuilt(units = program.units.filterNot(u => dropped(u.symbol)))

  // ---- static-forwarder-inline ----------------------------------------------------------------

  private def applyForwarders(
      program: Program,
      fixes: List[Remediator.Suggestion],
  ): Program =
    val chosen = program.units.flatMap { u =>
      plan.selected(u.symbol, RemediationTransform.Forward).map(u -> _)
    }
    if chosen.isEmpty then program
    else
      val fqn = (s: SymId) => program.symbolOf(s).map(_.fullName).getOrElse("?")
      val forwarders = chosen.flatMap { (unit, r) =>
        val name = fqn(unit.symbol)
        fixes.find(f => f.mechanism == RemediationTransform.Forward.id && f.subject == name) match
          case Some(f) =>
            val members = f.payload.getOrElse("members", "").split(',').filter(_.nonEmpty).toSet
            val receiver = f.payload.getOrElse("receiver", "")
            if members.isEmpty || receiver.isEmpty then
              plan.refuse(r, name, unit.origin, "nothing-forwardable",
                "the template matched this wrapper and every member it found forwards to an API that " +
                  "is ITSELF unportable — inlining those relocates the dependency rather than " +
                  "removing it [§1(c): those members need a real replacement]")
              scala.None
            else
              // `drained = 0`, and it is a CLAIM rather than a shortfall: an inline RELOCATES a
              // call from the wrapper to each of its call sites, so whether a lane row disappears
              // depends on where the call lands and this phase cannot know it. Claiming rows it did
              // not remove would make `sum(drained)` disagree with the lane it is read against,
              // which is the one arithmetic the drain rule rests on.
              plan.applied(r, name, unit.symbol, unit.origin,
                s"inlined ${members.size} static forwarder(s) to $receiver " +
                  s"(${members.toList.sorted.mkString(", ")}); the lane's own before/after is the " +
                  "measurement, because an inline relocates a call rather than removing one",
                drained = 0)
              Some(StaticForwarderTransform.Forwarder(name, receiver, members))
          case scala.None =>
            plan.refuse(r, name, unit.origin, "not-a-forwarder",
              "no static member of this type was verified to forward its first argument to a " +
                "same-named member of an external type, which is the only shape this rewrite is " +
                "faithful for [§1(b): the wrapper's members may already have been inlined by a " +
                "`StaticForwarderTransform` earlier in this pipeline]")
            scala.None
      }
      if forwarders.isEmpty then program
      else
        val delegate = new StaticForwarderTransform(forwarders)
        binder.foreach(delegate.bindPolicy)
        delegate.run(program)

  // ---- class-table -----------------------------------------------------------------------------

  private def applyClassTables(program: Program, asked: collection.mutable.Set[String]): Program =
    val chosen = program.symbols.all.toList.sortBy(_.id.raw).flatMap { s =>
      plan.selected(s.id, RemediationTransform.ClassTable).map(s -> _)
    }
    if chosen.isEmpty then program
    else
      val redirects = chosen.flatMap { (sym, r) =>
        val origin = Decision.originOf(program, sym.id)
        classTables.get(r.declaredKey) match
          case Some(dest) if dest.contains('#') =>
            asked += r.declaredKey
            // `drained = 0`, and it is the SAME claim `static-forwarder-inline` makes one arm up: a
            // redirect RELOCATES a call. `Wrapper.forName(s)` becomes `Table.classFor(s)` at each
            // CALL SITE, and the row this lane counts is the `Class.forName` inside the WRAPPER'S
            // BODY — which this rewrite does not touch, so the lane falls by nothing here and only
            // falls at all if the now-unreferenced wrapper is later dropped.
            //
            // It used to claim the number of CALL SITES of the wrapper, which is neither the rows
            // removed nor a number this lane holds: `resolved` gained N while the lane fell by 0,
            // and `sum(drained)` — the one arithmetic §5's drain rule rests on — was fiction. A
            // remedy that cannot know what it moved claims ZERO and says why (`DESIGN.md` §8.18).
            plan.applied(r, sym.fullName, sym.id, origin,
              s"redirected this runtime class lookup at $dest; the lookup INSIDE this member is " +
                "still what the lane counts, so the redirect claims no rows — the lane's own " +
                "before/after is the measurement",
              drained = 0)
            Some(r.declaredKey -> dest)
          case Some(bad) =>
            asked += r.declaredKey
            plan.refuse(r, sym.fullName, origin, "table-not-a-member",
              s"""the destination "$bad" is not `owner#member`, so there is nothing to select — """ +
                "the redirect is skipped and the lookup stays reflective [§1(b): fix the " +
                "`RemediationTransform(classTables)` value]")
            scala.None
          case scala.None =>
            plan.refuse(r, sym.fullName, origin, "no-table",
              "the MECHANISM applies here and the TABLE is still the port's: a redirect with no " +
                "destination would leave the lookup reflective and the port JVM-only with nothing " +
                s"""said. Add `classTables = { "${r.declaredKey}" = "<your.pkg.TypeTable>#classFor" }` """ +
                "and an injected object mapping each name this port can round-trip to a `classOf[…]` " +
                "literal [§1(b)]")
            scala.None
      }.toMap
      if redirects.isEmpty then program
      else
        val delegate = new ClassTableTransform(redirects)
        binder.foreach(delegate.bindPolicy)
        delegate.run(program)

object RemediationTransform:

  val Name: String = "remediation"

  /** the lane every remedy here drains. A type this phase removes takes its sites with it, so both
    * portability lanes fall — `portability(emitted)` is the one named because it is the DELIVERABLE
    * number and the one a drain is read against.
    *
    * Read from the CHECK that mints the rows (`PortabilityCheck.EmittedLane`) and never spelled
    * here: `Remedy.lane`'s own scaladoc asks for a constant so that a renamed lane is a compile
    * error, and this file was one of three places the name was written out. */
  private val Lane: String = PortabilityCheck.EmittedLane

  /** `Remediator`'s own `mechanism` strings, and that is not a coincidence to be tidied away: the id
    * a port writes in `resolutions` is the id printed beside the snippet in the `remediation` lane,
    * so the loop reads as one thing from both ends. */
  val Drop: Remedy = Remedy(
    id = "substitutions-drop", lane = Lane, kind = Remedy.AnyKind, emissionAffecting = true,
    fix = FixKind.Parameterised, subject = Remedy.Subject.OwnedType,
    what = "drop this type outright — offered only at the HIGH grade, where every site of the " +
      "chokepointed APIs is inside it AND no other unit in this port references it")

  val Forward: Remedy = Remedy(
    id = "static-forwarder-inline", lane = Lane, kind = Remedy.AnyKind, emissionAffecting = true,
    fix = FixKind.Parameterised, subject = Remedy.Subject.OwnedType,
    what = "inline this wrapper's verified receiver-first statics into calls on the receiver, " +
      "leaving the members that forward to an unportable API alone")

  val ClassTable: Remedy = Remedy(
    id = "class-table", lane = Lane, kind = Remedy.AnyKind, emissionAffecting = true,
    fix = FixKind.Parameterised, subject = Remedy.Subject.OwnedMember,
    what = "re-point this runtime class lookup at an explicit table — the mechanism is the " +
      "engine's, the table is still yours (`classTables`)")

  val Remedies: List[Remedy] = List(Drop, Forward, ClassTable)
