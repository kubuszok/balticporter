package balticporter.transform

import balticporter.catalog.FixKind
import balticporter.core.{PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*

/** THE PORTABILITY MENU's other end: a port SELECTS one of `Remediator`'s verified templates at a
  * location (`PortManifest.resolutions`) and this phase performs it, inside the pipeline. Runs
  * LAST in `surface`. Every remedy applies or refuses with a counted, named guard — never silent.
  * `classTables` is the `class-table` destination, keyed `owner#member` of the CALLEE, valued
  * `owner#member` of the table's lookup; empty is the no-op. CLAUDE.md §1(b),§3,§5; DESIGN.md §8.16,§8.18 */
final class RemediationTransform(
    val classTables: Map[String, String] = Map.empty,
) extends Phase, RemedySource, PolicyBound, PolicySource, SurfacePolicy:

  def name: String = RemediationTransform.Name

  def remedies: List[Remedy] = RemediationTransform.Remedies

  /** The selections are surface, fingerprinted in the manifest; this adds only [[classTables]], the
    * one value that is a phase parameter. Targets are NOT here — they are the run's
    * (`RunScope.platform`), already compared through `PortManifest.targets`. */
  def surfaceFingerprint: String =
    classTables.toList.sorted.map((k, v) => s"$k->$v").mkString(",")

  private var plan: ResolutionPlan   = ResolutionPlan.empty
  private var binder: Option[PolicyBinder] = scala.None
  private var unusedTables: List[String]   = Nil
  /** which declarations THIS RUN emits — ENGINE-LIMITS D2 at the resolution ledger. Unguarded, a
    * dependent would re-apply a base's own selections over shared units it does not own. */
  private var scope: RunScope = RunScope.whole

  def bindPolicy(b: PolicyBinder): Unit =
    plan   = b.resolutions
    binder = Some(b)
    scope  = b.run

  /** a table entry no selection reaches is dead policy — computed in `run` rather than at bind time
    * because the question is "did any selection ask for this table", not "does this key exist". */
  def policyReport: PolicyReport =
    PolicyReport(unusedTables.sorted.map(k =>
      PolicyFinding(name, "RemediationTransform(classTables) key", k, PolicyIssue.NeverMatched,
        s"no `${RemediationTransform.ClassTable.id}` selection names this callee, so the table is " +
          "never consulted — either add the `resolutions` entry or remove the table row")))

  // -------------------------------------------------------------------------------------------

  override def run(program: Program): Program =
    if plan.isEmpty then program
    else
      // the run's question, never a set of this phase's own.
      val rules      = PortabilityCheck.rulesFor(scope.platform.targets, scope.platform.verdictOverrides)
      val violations = PortabilityCheck.check(program, rules)
      // the same verification, by the same code, that prints the snippet a human would paste.
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
    val chosen = program.units.filter(u => scope.emits(u.symbol)).flatMap { u =>
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
        // sound only because HIGH grade means nothing else refers to it; xref rebuilds after
        // every phase, so both portability lanes fall by exactly the count recorded above.
        program.rebuilt(units = program.units.filterNot(u => dropped(u.symbol)))

  // ---- static-forwarder-inline ----------------------------------------------------------------

  private def applyForwarders(
      program: Program,
      fixes: List[Remediator.Suggestion],
  ): Program =
    val chosen = program.units.filter(u => scope.emits(u.symbol)).flatMap { u =>
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
              // `drained = 0`: an inline RELOCATES a call rather than removing it, so this phase
              // cannot know if a lane row disappears — claiming rows it did not remove would break
              // `sum(drained)`.
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
    val chosen = program.symbols.all.toList.sortBy(_.id.raw)
      .filter(s => scope.emitsSymbol(program, s.id))
      .flatMap { s => plan.selected(s.id, RemediationTransform.ClassTable).map(s -> _) }
    if chosen.isEmpty then program
    else
      val redirects = chosen.flatMap { (sym, r) =>
        val origin = Decision.originOf(program, sym.id)
        classTables.get(r.declaredKey) match
          case Some(dest) if dest.contains('#') =>
            asked += r.declaredKey
            // `drained = 0`, the same claim `static-forwarder-inline` makes: a redirect RELOCATES a
            // call, and the row this lane counts (the `Class.forName` inside the wrapper's body)
            // is untouched — it falls only if the now-unreferenced wrapper is later dropped.
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

  /** the lane every remedy here drains — `portability(emitted)`, read from the check that mints the
    * rows rather than spelled here, so a renamed lane is a compile error. */
  private val Lane: String = PortabilityCheck.EmittedLane

  /** `Remediator`'s own `mechanism` strings — the id a port writes in `resolutions` is the id
    * printed beside the snippet in the `remediation` lane. */
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
