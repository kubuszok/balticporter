package balticporter.tir

/** A [[Decision]] rendered as a `/* porter: <slug> k=v … */` comment beside the code.
  * DERIVED — the emitter renders only decisions it is emitting, never invents.
  * Values go through [[safe]] (no `/*`/`*/`). Original trivia is emitted first, note last. */
object PorterNote:

  /** the token every note starts with, and the only thing a scan needs to know. */
  val Marker = "/* porter:"

  /** Kinds that MUST be rendered beside the code — [[NoteCoverageCheck]] enforces this.
    * Line: a reader of the emitted code cannot explain the construct without the note.
    * Excludes `RetypedSignature` (visible in the diff) and `RedirectedCall` (visible in the body). */
  val Rendered: Set[Decision.Kind] =
    import Decision.Kind.*
    Set(RenamedType, RenamedPackage, RenamedMember, DroppedType, DroppedMember,
        SubstitutedBody, SubstitutedCall, InjectedMember, DroppedSuperCall, WidenedVisibility,
        Unrenderable, ScopedOut, RetainedSignature, DeferredInit, FunnelledCtor, RetainedParent,
        ReifiedTypeArg,
        BeanAccessor, ForcedClassInit, WidenedSeal, RecordMembers, SamLambda, CollapsedProperty,
        SelectedRemedy,
        StrippedOverride,
        SubsumedParent,
        BridgedMember,
        RebuiltPerTest,
        ParenlessConversion,
        SuppressedWarning,
        AddedMember)

  /** Placement: [[AtDeclaration]] (emitted subject), [[InBody]] (dropped member, at body head),
    * [[NotInTree]] (dropped type, carried by injected file). A kind in the wrong set never appears. */
  val InBody: Set[Decision.Kind]    = Set(Decision.Kind.DroppedMember, Decision.Kind.AddedMember)
  val NotInTree: Set[Decision.Kind] = Set(Decision.Kind.DroppedType)
  val AtDeclaration: Set[Decision.Kind] = Rendered -- InBody -- NotInTree

  /** `RenamedMember` -> `renamed-member`. Derived from the enum name. */
  def slug(k: Decision.Kind): String =
    k.toString.flatMap(c => if c.isUpper then "-" + c.toLower else c.toString).stripPrefix("-")

  /** every kind, by its slug — how a scan over emitted text turns a note back into a kind. */
  private lazy val bySlug: Map[String, Decision.Kind] =
    Decision.Kind.values.map(k => slug(k) -> k).toMap

  /** Grammar primitives shared with the port map's `shape` column via [[KeyValues]]. */
  export KeyValues.{safe, value}

  /** The `k=v` pairs: §1 classification first, then detail sorted. Concatenated, not deduplicated. */
  def pairs(d: Decision): List[(String, String)] =
    val cls = ("reason" -> d.reason.className) :: (d.reason match
      case Reason.Universal(r)     => List("rule" -> r)
      case Reason.LibraryRule(r)   => List("rule" -> r)
      case Reason.Configured(p, k) => List("phase" -> p, "key" -> k))
    cls ++ d.detail.filterNot(_._1 == "why").toList.sorted

  /** Render one decision at `indent`. Returns `""` for non-[[Rendered]] kinds. */
  def render(d: Decision, indent: String, width: Int = 110): String =
    if !Rendered(d.kind) then ""
    else
      val head = s"$indent$Marker ${slug(d.kind)} " +
        pairs(d).map((k, v) => s"$k=${value(v)}").mkString(" ")
      d.detail.get("why").map(safe).filter(_.nonEmpty) match
        case scala.None                                          => head + " */\n"
        case Some(w) if head.length + w.length + 6 <= width       => s"$head — $w */\n"
        case Some(w)                                              => s"$head\n$indent   — $w */\n"

  /** One note parsed from emitted text. `kind` is `None` for an unknown slug. */
  final case class Found(slug: String, kind: Option[Decision.Kind])

  /** Every note in `text`, in order. Reads only the slug — [[NoteCoverageCheck]] joins on recordings. */
  def scan(text: String): List[Found] =
    val out = collection.mutable.ListBuffer.empty[Found]
    var i   = text.indexOf(Marker)
    while i >= 0 do
      val rest = text.substring(i + Marker.length).dropWhile(_.isWhitespace)
      val s    = rest.takeWhile(c => !c.isWhitespace)
      out += Found(s, bySlug.get(s))
      i = text.indexOf(Marker, i + Marker.length)
    out.toList

  /** What the emitter printed — a value one emitter owns. `subject` is the SymId,
    * enabling a join that survives renames (a name-keyed check was empty on renamed decisions). */
  final case class Printed(kind: Decision.Kind, subject: SymId, subjectFqn: String, unit: String)
