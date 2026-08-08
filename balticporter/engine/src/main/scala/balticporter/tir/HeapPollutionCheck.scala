package balticporter.tir

import balticporter.catalog.FixKind

/** Every declaration whose VARARG parameter has a NON-REIFIABLE component type — java's heap
  * pollution, carried into the port with nothing on the Scala side that mentions it.
  *
  * ==What the difference actually is (catalog `JS-G41`, JLS 9.6.4.7 and 4.12.2)==
  * A `T...`/`List<String>...` parameter is an array of a type the JVM cannot check, so the array
  * javac creates at each call site can be polluted and the method body can hand out a value of the
  * wrong type with no `ClassCastException` where the mistake was made. **Java's own answer is not a
  * fix, it is a CONVERSATION**: javac WARNS at the declaration, and `@SafeVarargs` is the author
  * asserting they checked the body. Neither half exists in Scala — there is no `@SafeVarargs`, and
  * scalac issues no such warning — so both the risk and the acknowledgement cross the port
  * silently.
  *
  * '''There is nothing to translate, which is exactly why this is a COUNTER and not an obligation
  * the emission can discharge.''' The port reproduces java's semantics precisely: the unsoundness
  * is identical on both sides, and a phase that "fixed" it would be emitting a different program.
  * What can be reported is WHERE the port inherited it, which is a number a reader can act on and
  * was a silence before.
  *
  * ==And the annotation makes it WORSE than a silence, which is the part worth seeing==
  * `SpoonTir.annotationsOf` carries a MARKER annotation unconditionally, so `@SafeVarargs` is
  * emitted verbatim onto the Scala method — where it is decoration twice over. `JS-G37` renders a
  * java `T...` as a plain `Array[T]` parameter (the port materialises the array at its own call
  * sites), so the emitted method is not even variadic, and scalac neither checks the annotation's
  * placement nor derives anything from it. A reader of the emitted file sees java's acknowledgement
  * of a risk sitting on a declaration where it says nothing at all.
  *
  * ==Both halves are counted, and the UNACKNOWLEDGED one is the population that matters==
  * A census keyed on the annotation would report java's acknowledgements and call that the risk —
  * which is `CLAUDE.md` §4.56's lesson about an instrument whose silence is a wrong answer. The
  * declarations javac warned about and the author never annotated carry the same unsoundness and
  * have no marker of any kind, so they are the ones a reader cannot otherwise find. Reifiability is
  * JLS 4.7's own rule, asked of the emitted component type: a type VARIABLE is not reifiable, and
  * neither is a parameterised type unless every argument is an UNBOUNDED wildcard.
  *
  * '''Why the reifiability test is structural.''' "Is this a type variable" is answered from the
  * symbol's own `info` being a [[TypeRepr.TypeBounds]] — which is what `SpoonTir.mintTypeParams`
  * gives a type parameter and what nothing else has — never from the `$$` in its interning key
  * (§4.56).
  */
object HeapPollutionCheck extends RemedySource:

  val Name = "heap-pollution"

  /** java's own annotation, by FQN. The one library-free string in this file, beside the emitted
    * spelling of an array — both are facts about java and about this engine's own rendering. */
  private val SafeVarargs = "java.lang.SafeVarargs"
  private val ScalaArray  = "scala.Array"

  enum Issue:
    /** java wrote `@SafeVarargs`: the author asserted the body is safe, and the assertion crosses
      * into the emitted file as an annotation that says nothing to scalac. */
    case Acknowledged
    /** javac WARNED at this declaration and the author left it; nothing on the Scala side warns. */
    case Unacknowledged

  object Issue:
    def classification(i: Issue): String = i match
      case Acknowledged =>
        "§1(a) ENGINE, and DELIBERATELY NOT FIXED: the port reproduces java's heap pollution " +
          "exactly, so there is nothing to translate — what has no Scala image is the " +
          "ACKNOWLEDGEMENT. `@SafeVarargs` is emitted verbatim onto a method whose vararg is now a " +
          "plain `Array` parameter (JS-G37), where scalac neither checks its placement nor derives " +
          "anything from it. Read this row as `the author of the java said they had checked this " +
          "body`, and read the port as carrying that promise unverified."
      case Unacknowledged =>
        "§1(a) ENGINE, and DELIBERATELY NOT FIXED for the same reason: javac warns at a " +
          "declaration whose vararg component is not reifiable (JLS 4.7) and scalac has no such " +
          "warning, so this declaration crossed with neither an annotation nor a diagnostic. " +
          "Nothing in the emitted file mentions it and no other count can see it."

  // -------------------------------------------------------------------------------------------
  // THE MENU (`DESIGN.md` §8.16) — what a port may ASK FOR at one of these declarations
  // -------------------------------------------------------------------------------------------

  /** JAVA'S OWN ANSWER, GIVEN A HOME — the operator states that the vararg use at this member is
    * safe, and the row moves from the residue into `remediation(resolved)`.
    *
    * ==Why an acknowledgement is a REMEDY and not a suppression==
    * There is nothing to translate here and there never will be: the port reproduces java's
    * unsoundness exactly, so a phase that "fixed" it would be emitting a different program. What
    * java HAS and scala has not is the CONVERSATION — javac warns at the declaration and
    * `@SafeVarargs` is the author answering it. This remedy is that answer, carried out where the
    * java one could not be: it is recorded as a `Decision`, so it reaches `decisions.tsv` AND the
    * emitted line as a porter note (§4.575), which is strictly more than the java said, because
    * scalac neither checks `@SafeVarargs`'s placement nor derives anything from it.
    *
    * ==NOT emission-affecting, and that is a claim about the SIGNATURE==
    * Applying it changes no type, no parameter and no body — only the porter note, which is a
    * comment. Two modules choosing differently therefore cannot produce two ports that compile
    * alone and fail together, which is exactly the test §1.5 puts a selection to.
    *
    * ==It answers `Unacknowledged` ALONE, on purpose==
    * An `Acknowledged` row is a DIFFERENT statement — java's own author already made this call, and
    * the row exists to say that the promise crossed into a file where nothing can check it.
    * Draining it with the port's own acknowledgement would erase the distinction the two kinds were
    * split for, and would let a port report zero for a lane whose whole point is that java's
    * assertion is now unverified. The two kinds here PARTITION the lane; they do not split a site.
    */
  val Acknowledge: Remedy = Remedy(
    id = "acknowledge", lane = Name, kind = Issue.Unacknowledged.toString,
    emissionAffecting = false, fix = FixKind.Universal,
    what = "the operator states this vararg use is safe — java's `@SafeVarargs` conversation, " +
      "carried out where java could not always hold it")

  /** …and WHAT IS NOT ON THE MENU, stated where the menu is so a reader sees a refusal and not a gap.
    *
    *   - '''emit a scala-side marker equivalent to `@SafeVarargs`'''. REFUSED. Scala has no warning
    *     to suppress, so the marker would be inert exactly as the carried-over annotation already is
    *     (see the class doc: `@SafeVarargs` crosses verbatim onto a method whose vararg is now a
    *     plain `Array` parameter, where scalac derives nothing from it). A second inert marker is a
    *     mechanism a port could select and get nothing from, which is worse than the count;
    *   - '''prove the body safe'''. REFUSED as a remedy: it is not a choice a port makes, it is an
    *     analysis nothing here performs. [[Acknowledge]] is the honest shape — the operator asserts
    *     it, and the assertion is RECORDED with their name on it rather than derived;
    *   - '''change the emitted signature so the pollution cannot arise'''. REFUSED: the port would
    *     stop being the library. `JS-G37` already renders a `T…` as `Array[T]`, and narrowing it
    *     further is a different API.
    */
  def remedies: List[Remedy] = List(Acknowledge)

  /** @param param     the vararg parameter's name
    * @param component the emitted COMPONENT type — the thing that is not reifiable */
  final case class Finding(issue: Issue, owner: String, param: String, component: String, origin: Origin):
    def detail: String =
      s"vararg `$param` has component `$component`, which JLS 4.7 does not make reifiable: java " +
        (if issue == Issue.Acknowledged then "warned here and the author wrote `@SafeVarargs`"
         else "WARNED here and the author did not annotate it") +
        ", and scala has neither the warning nor the annotation — the unsoundness crosses unmarked"
    def render: String = s"$issue $owner: $param: $component  (${origin.javaPath}:${origin.line})"
    def report: CheckReport.Finding =
      CheckReport.Finding(Name, issue.toString, owner, CheckReport.relativise(origin.javaPath),
        origin.line, detail)

  /** THE PREDICATE, STATED ONCE — read by this check and by the emitter's `JS-G41` consult, so the
    * count and the obligation cannot disagree about which declarations the row is even about
    * (`ENGINE-LIMITS.md` F8's rule).
    *
    * `None` where the declaration carries no unchecked vararg at all, which is the answer at the
    * overwhelming majority of methods and is what makes the consult a discharge rather than a fire.
    */
  def uncheckedVararg(d: Tree.DefDef)(using p: Program): Option[Finding] =
    val owner = p.symbolOf(d.symbol).map(_.fullName).getOrElse("?")
    val ack   = p.symbolOf(d.symbol).exists(_.annotations.exists(a => headFqn(a.tpe).contains(SafeVarargs)))
    d.paramss.flatten.collectFirst {
      case v if p.symbolOf(v.symbol).exists(_.flags.isVararg) && !reifiable(componentOf(v.tpt.tpe)) =>
        Finding(if ack then Issue.Acknowledged else Issue.Unacknowledged, owner,
          p.symbolOf(v.symbol).map(_.name).getOrElse("?"),
          render(componentOf(v.tpt.tpe)), d.origin)
    }

  /** the element of the array a `T...` became (`JS-G37`), or the type itself where a phase has
    * already moved it off `scala.Array` — in which case there is no component to judge and the
    * type stands in for one. */
  private def componentOf(t: TypeRepr)(using Program): TypeRepr = t match
    case TypeRepr.AppliedType(tc, List(a)) if headFqn(tc).contains(ScalaArray) => a
    case other                                                                    => other

  /** JLS 4.7, asked of the emitted type. Deliberately CONSERVATIVE in the direction that reports:
    * a shape this algebra cannot classify is left reifiable, because a row a reader cannot check is
    * worse than a silence they can. */
  private def reifiable(t: TypeRepr)(using Program): Boolean = t match
    case TypeRepr.TypeRef(_, s) => !isTypeVariable(s)
    // …an ARRAY is reifiable iff its COMPONENT is (JLS 4.7's own clause), and it has to be its own
    // arm because an array is spelled as an application here: read through the general rule below,
    // `String[]…` would be reported for having a non-wildcard argument, which javac does not warn
    // about and this lane would then be counting a risk that is not there.
    case TypeRepr.AppliedType(tc, List(a)) if headFqn(tc).contains(ScalaArray) => reifiable(a)
    case TypeRepr.AppliedType(tc, as)                                         => reifiable(tc) && as.forall(unboundedWildcard)
    case TypeRepr.AndType(_, _)                                               => false
    case TypeRepr.OrType(_, _)                                                => false
    case _                                                                    => true

  /** a type PARAMETER, structurally: `SpoonTir.mintTypeParams` gives one its BOUNDS as its `info`,
    * and no other symbol kind has a `TypeBounds` there. Never the `$$` in the interning key. */
  private def isTypeVariable(s: SymId)(using p: Program): Boolean =
    p.symbolOf(s).exists(x => x.flags.isParam && x.info.isInstanceOf[TypeRepr.TypeBounds])

  /** `List<?>` is reifiable and `List<? extends X>` is not — JLS 4.7's one distinction between two
    * things that render two characters apart. */
  private def unboundedWildcard(t: TypeRepr)(using Program): Boolean = t match
    case TypeRepr.TypeBounds(_, hi) =>
      hi == TypeRepr.NoType ||
        headFqn(hi).exists(f => f == "java.lang.Object" || f == "scala.Any" || f == "scala.AnyRef")
    case _ => false

  private def headFqn(t: TypeRepr)(using p: Program): Option[String] = t match
    case TypeRepr.TypeRef(_, s)      => p.symbolOf(s).map(_.fullName)
    case TypeRepr.AppliedType(tc, _) => headFqn(tc)
    case _                           => scala.None

  private def render(t: TypeRepr)(using p: Program): String = t match
    case TypeRepr.AppliedType(tc, as) => s"${render(tc)}[${as.map(render).mkString(", ")}]"
    case TypeRepr.TypeBounds(_, hi)   => if hi == TypeRepr.NoType then "?" else s"? <: ${render(hi)}"
    case other                        => headFqn(other).getOrElse(other.toString)

  /** Over the units the run EMITS — the same D2 filter every other per-site report carries.
    *
    * @param resolutions what the port SELECTED, as the phase above recorded it. A row a remedy
    *   answered has left this lane for `remediation(resolved)`, and reporting it here as well would
    *   put two rows on one site and leave the lane unable to fall by what `resolved` gained
    *   (`CLAUDE.md` §5). Read off the LEDGER and not off the manifest, so a selection the phase
    *   examined and refused still has its finding — see [[ResolutionPlan.appliedAt]]. Empty is the
    *   default and the pre-menu behaviour exactly.
    */
  def check(program: Program, units: List[Tree.ClassDef],
            resolutions: ResolutionPlan = ResolutionPlan.empty): List[Finding] =
    given Program = program
    val out = collection.mutable.ListBuffer.empty[Finding]
    // `StandardTraversal`, never a private recursion (§3): a vararg declaration inside an ANONYMOUS
    // class lives in a TERM, and a walk over class bodies would find none of them.
    val scan = new Phase:
      def name: String = "heap-pollution/scan"
      override def transformDefDef(d: Tree.DefDef)(using Program): Tree.DefDef =
        uncheckedVararg(d)
          .filterNot(f => resolutions.appliedAt(d.symbol, Name, f.issue.toString))
          .foreach(out += _)
        d
    units.foreach(u => StandardTraversal.mapClassDef(scan, u))
    out.toList.sortBy(f => (f.issue.toString, f.origin.javaPath, f.origin.line, f.param))

  /** THE MENU, CARRIED OUT — a phase, because a resolution has to be recorded BEFORE emission.
    *
    * ==Why the applier is not the check==
    * The check runs after the units are written, which is exactly right for a count and impossible
    * for a resolution: an applied one produces a `Decision`, the emitter renders that decision as a
    * porter note while it writes the member (§4.575), and the run records its decisions before the
    * first file is emitted. Recorded from the check the row would reach `decisions.tsv` too late for
    * any note — a `NoteCoverageCheck` failure at best, and at worst a decision the reader at the
    * line is never told about. So the DECLARER of the menu is this check (it mints the rows, and
    * `PortRun.CheckRemedies` is where it says so) and the CARRIER is a phase in the pipeline. Both
    * read [[uncheckedVararg]], which is the predicate stated once, so the count and the application
    * cannot disagree about which declarations the row is about.
    *
    * ==A no-op with no selections, by construction==
    * With an empty plan every `transformDefDef` returns immediately, and the phase is woven into
    * every port for that reason: it has no constructor policy of its own — its whole configuration
    * is `PortManifest.resolutions`, which is already part of the surface fingerprint — so there is
    * nothing for two modules to configure differently and nothing for a `surface` line to add.
    *
    * ==And it applies only where THIS RUN emits the declaration==
    * A dependent's `Program` contains its base's units, so an inherited selection binds there too;
    * applied, it would file a `remediation(resolved)` row about a declaration this module does not
    * emit — `ENGINE-LIMITS.md` D2 read at the resolution ledger. `RunScope` is the run's own answer
    * and arrives on the binder like every other thing a phase may not derive from the tree.
    */
  final class Apply extends Phase, PolicyBound:
    def name: String = "heap-pollution/remedy"

    private var plan: ResolutionPlan = ResolutionPlan.empty
    private var scope: RunScope      = RunScope.whole

    def bindPolicy(binder: PolicyBinder): Unit =
      plan  = binder.resolutions
      scope = binder.run

    override def transformDefDef(d: Tree.DefDef)(using p: Program): Tree.DefDef =
      if plan.isEmpty || !scope.emitsSymbol(p, d.symbol) then d
      else
        uncheckedVararg(d).foreach { f =>
          plan.selected(d.symbol, Name, f.issue.toString).foreach { r =>
            plan.applied(r, f.owner, d.symbol, d.origin,
              s"vararg `${f.param}` (component `${f.component}`) is stated safe by this port: " +
                "java warned here and scala neither warns nor carries an annotation that means " +
                "anything — the assertion had no other home")
          }
        }
        d

  def summary(fs: List[Finding]): String =
    if fs.isEmpty then "  none"
    else
      fs.groupBy(_.issue).toList.sortBy((_, v) => -v.size).map { (issue, vs) =>
        val head  = s"  ${vs.size} × $issue\n  ${Issue.classification(issue)}"
        val sites = vs.sortBy(f => (f.origin.javaPath, f.origin.line)).take(10).map("    " + _.render)
        (head :: sites).mkString("\n")
      }.mkString("\n")
