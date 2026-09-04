package balticporter.tir

import balticporter.catalog.{DiffId, FixKind}

/** THE MARKER'S TAXONOMY — why a construct has no faithful Scala image, as a CLOSED engine enum
  * (`DESIGN.md` §6.2: a new kind arrives with mint sites and report text, the correct friction).
  * Each kind carries [[UnportableKind.remedies]] classified by §1's three kinds (§1c ranks LAST
  * unless inherently semantic); every string is a TEMPLATE, never a library name (§1's grep gate).
  * Not a `Difference` (a FACT true regardless of code) — a FAILURE shape, joined via [[Tree.Unportable.diff]]. */
enum UnportableKind(val slug: String, val summary: String):

  /** a raw Java generic whose Scala image would have to fill a type argument the source does not
    * state — and where the fill is not `?` (`ENGINE-LIMITS.md` G2's shipped answer) but a name the
    * program cannot spell. */
  case RawGenericConversion
      extends UnportableKind("raw-generic-conversion",
        "a raw generic whose Scala image needs a type argument the Java does not state")

  /** the same shape, where the fill DEPENDS on where the type is used — so no one rendering is
    * right for every scope (`ENGINE-LIMITS.md` G8). */
  case ContextDependentRawFill
      extends UnportableKind("context-dependent-raw-fill",
        "the type argument a raw generic needs differs per USE, so no single rendering is faithful")

  /** a value crossing the seam between a retyped declaration and a JDK signature no phase can
    * move, where no coercion exists in the direction required. */
  case JdkBoundaryFlow
      extends UnportableKind("jdk-boundary-flow",
        "a value crosses a JDK seam in a direction no coercion closes")

  /** a Java constructor graph with no single-primary Scala encoding (`ENGINE-LIMITS.md` C3). */
  case ConstructorTopology
      extends UnportableKind("constructor-topology",
        "the constructor graph has no single-primary Scala encoding")

  /** an API that exists on the JVM and not on a platform this port targets. The one kind whose
    * FIRST remedy is (b): which platforms a port is for is the port's statement, not the engine's. */
  case PlatformHostileApi
      extends UnportableKind("platform-hostile-api",
        "the API is JVM-only and this port is not")

  /** a reflective lookup whose target this engine cannot resolve to a symbol, so no explicit table
    * can replace it. */
  case ReflectiveLookup
      extends UnportableKind("reflective-lookup",
        "a reflective lookup whose target cannot be resolved to a declaration")

  /** javac and scalac would choose DIFFERENT overloads for the same argument list. */
  case OverloadDivergence
      extends UnportableKind("overload-divergence",
        "javac and scalac resolve this call to different alternatives")

  /** the frontend HAS an arm for the construct and the arm could not complete — a resolution the
    * parser could not give it, a declaration absent from the model. Distinct from
    * [[UnmodelledNodeKind]], which is the case where no arm exists at all. */
  case FrontendBlindSpot
      extends UnportableKind("frontend-blind-spot",
        "the frontend's own arm could not complete for this node")

  /** NO ARM EXISTS for this Java node kind. `spoonKind` is the parser's own class name
    * (`CtSwitchExpression`, `CtRecord`), joining directly to `SpoonKinds.registry` — without the
    * name a run could report "N constructs refused" and nothing could say WHICH (§4.45). */
  case UnmodelledNodeKind(spoonKind: String)
      extends UnportableKind("unmodelled-node-kind",
        "no arm in the frontend lowers this Java node kind")

  /** an annotation whose ARGUMENTS would not translate, so emitting it bare would emit a DIFFERENT
    * annotation. */
  case AnnotationResidue
      extends UnportableKind("annotation-residue",
        "the annotation's arguments do not translate, and a bare `@A` is a different annotation")

  /** the detail a mint site adds to [[slug]] — the Spoon kind, where there is one. */
  def detail: Option[String] = this match
    case UnmodelledNodeKind(k) => Some(k)
    case _                     => scala.None

  /** how a report names this marker: the slug, plus whatever the kind itself knows. */
  def label: String = detail.fold(slug)(d => s"$slug($d)")

  /** DEFAULT ranked remediations. See the class doc for the two rules these follow. */
  def remedies: List[RemedyHint] = this match
    case RawGenericConversion => List(
      RemedyHint(FixKind.Universal, "render the raw reference `[?]` at every position, parents and " +
        "overrides included — the answer measured against the reference port"),
      RemedyHint(FixKind.Parameterised, "if one fill is right for this library, state it as a type " +
        "redirect rather than letting the frontend guess"),
    )
    case ContextDependentRawFill => List(
      RemedyHint(FixKind.Universal, "bind the value to a NAMED local of the de-wildcarded type, so the " +
        "two uses see one capture"),
      RemedyHint(FixKind.LibraryRule, "where the shape recurs in one library only, a rule may rewrite " +
        "the declaration; four general answers have been measured worse"),
    )
    case JdkBoundaryFlow => List(
      RemedyHint(FixKind.Universal, "insert the coercion at the seam, in the direction the DECLARATION " +
        "requires — never the reference node's type, which a position-blind retyping already moved"),
      RemedyHint(FixKind.Parameterised, "scope the retyping phase OUT of the declaration that must " +
        "keep the JDK shape"),
    )
    case ConstructorTopology => List(
      RemedyHint(FixKind.Universal, "promote the widest super call to the PRIMARY and delegate; where " +
        "no single primary exists the residue is counted rather than padded"),
    )
    case PlatformHostileApi => List(
      RemedyHint(FixKind.Parameterised, "drop the type and inject a replacement, or re-point the call " +
        "at a portable one — which of the two is the port's judgement"),
      RemedyHint(FixKind.Universal, "accept it, and record that this port targets the JVM only"),
    )
    case ReflectiveLookup => List(
      RemedyHint(FixKind.Parameterised, "re-point the lookup at an EXPLICIT table, so the answer is a " +
        "declaration rather than a name"),
      RemedyHint(FixKind.LibraryRule, "where the lookup's targets are knowable only for one library, a " +
        "plugged-in rule supplies them"),
    )
    case OverloadDivergence => List(
      RemedyHint(FixKind.Universal, "make the chosen alternative EXPLICIT — an ascription at the " +
        "argument javac's phase would have taken"),
      RemedyHint(FixKind.Universal, "where the divergence cannot be decided, COUNT it: a resolver for " +
        "Scala's own overload resolution is a compiler-sized project and a guess is worse than a " +
        "number"),
    )
    case FrontendBlindSpot => List(
      RemedyHint(FixKind.Universal, "narrow the lookup that absented — a broad `catch` around a " +
        "resolution turns an unknown into a DEFAULT, and the default is what ships"),
    )
    case UnmodelledNodeKind(_) => List(
      RemedyHint(FixKind.Universal, "add the lowering arm, with its edge-case suite; the kind registry " +
        "is what says this one was never claimed"),
      RemedyHint(FixKind.Parameterised, "until then, drop the declaration that uses it and inject a " +
        "replacement — a port that ships without the member is a statement, and a port that ships " +
        "it wrong is not"),
    )
    case AnnotationResidue => List(
      RemedyHint(FixKind.Universal, "translate the annotation's arguments; emitting `@A` where Java " +
        "wrote `@A(x)` is a different annotation and the port would compile"),
    )

/** one ranked remediation, in PROSE, with the §1 classification that says whose it is. A HINT, not
  * a [[Remedy]]: this is advice a human carries out, a `Remedy` is a NAMED alternative the engine
  * itself performs. They share [[balticporter.catalog.FixKind]] (the same first question — which
  * repository owns the fix) and nothing else. */
final case class RemedyHint(fix: FixKind, what: String):
  /** deliberately NOT [[balticporter.catalog.FixKind.section]]: this rendering reaches emitted
    * marker text, so its exact spelling is a fact about the output rather than about the
    * classification, and the two are free to differ. */
  def render: String = s"${label(fix)} ${what}"
  private def label(f: FixKind): String = f match
    case FixKind.Universal     => "§1(a) ENGINE:"
    case FixKind.Parameterised => "§1(b) CONFIGURE:"
    case FixKind.LibraryRule   => "§1(c) LIBRARY RULE:"
    case FixKind.NoFix         => "—"

/** OPEN, or discharged by a named phase (`DESIGN.md` §6.2: discharge is an explicit act,
  * `Open → Resolved(byPhase, how)`, and a marker never leaves the tree until emission). A phase
  * that DELETES a marked subtree has erased a finding rather than fixed one; [[MarkerCheck]] tells
  * the two apart only because a discharge leaves a `Resolved` node behind. */
enum MarkerState:
  case Open
  case Resolved(byPhase: String, how: String)

  def isOpen: Boolean = this match
    case Open => true
    case _    => false
