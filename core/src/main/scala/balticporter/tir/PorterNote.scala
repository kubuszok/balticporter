package balticporter.tir

/** A PORTER NOTE — one [[Decision]], rendered as a comment BESIDE the code it explains.
  *
  * ## Why the decision channel is not enough on its own
  *
  * `decisions.tsv` answers "why does the port look like this" for an agent that knows the artifact
  * exists, holds the run directory, and thinks to join on a name. The agent this project actually
  * has (CLAUDE.md §4.45) is reading ONE emitted file in another repository, and the question it
  * asks is asked at a line of Scala: *why is this field called `style$shadow`, why is this method
  * simply absent, why does this file live in a package the upstream never had.* A record in a
  * sibling TSV cannot be found from there.
  *
  * So the note is the same fact, placed where the question is asked. It is DERIVED — the emitter
  * renders only decisions whose subject it is emitting, and invents nothing — which is what keeps
  * the two artifacts from being able to disagree, and what makes [[NoteCoverageCheck]] a real
  * check rather than a tautology.
  *
  * ## The grammar
  *
  * {{{
  * /* porter: <kind-slug> k=v … — <free text> */
  * }}}
  *
  * One line unless the free text does not fit, in which case the text moves to a second line and
  * the comment closes there. Deliberately ONE grammar for every kind, and deliberately greppable:
  * `grep -rn '/\* porter:' src_managed` is the whole inventory of non-mechanical translation in a
  * port, and `grep 'porter: dropped-member'` is one class of it.
  *
  * `<kind-slug>` is [[Decision.Kind]] in kebab case — the enum, not a string a decider chose, for
  * the reason `Decision.Kind` is closed at all.
  *
  * `k=v` pairs are the decision's `detail`, sorted, with `why` lifted out as the free text, plus
  * the §1 classification: `reason=universal|configured|library-rule` and then `rule=` (universal /
  * library rule) or `phase=`+`key=` (configured). The classification is not decoration — it is the
  * first question an agent has, and it is what says which repository the fix lives in.
  *
  * ## Two things a note must never do
  *
  *   - '''Open a comment.''' Scala block comments NEST and Java's do not (§4.58), so a `/*` or
  *     `*/` reaching a note's text would swallow the rest of the file. Every rendered value goes
  *     through [[safe]]; there is no path that skips it.
  *   - '''Displace trivia.''' The upstream's own comment is the thing a licence obliges the port to
  *     reproduce and the thing a reader wants first. Original trivia is emitted FIRST and the note
  *     LAST, immediately above the member — enforced at each call site in the emitter, and visible
  *     as an ordering the [[TriviaCheck]] would fail if it were ever inverted into the comment's
  *     place.
  */
object PorterNote:

  /** the token every note starts with, and the only thing a scan needs to know. */
  val Marker = "/* porter:"

  /** Which kinds MUST be rendered beside the code, and are therefore what [[NoteCoverageCheck]]
    * holds the emitter to.
    *
    * The line is drawn at "would a reader of this line be unable to explain it from the line
    * itself". A rename, a drop, a substitution and an injection all leave the emitted code saying
    * something the upstream Java does not, with no local evidence of why.
    *
    * The three that are NOT here are not oversights:
    *
    *   - [[Decision.Kind.RetypedSignature]] — the new type is written in the declaration and the
    *     diff against the Java shows it; a note per retyped member is 335 comments on libGDX core
    *     restating what the signature already says, and the noise would bury the ones that carry
    *     information nothing else does.
    *   - [[Decision.Kind.RedirectedCall]] — recorded per DECLARATION for the reason
    *     `Decision.declarationsUsing` gives, and the rewritten call is right there in the body.
    *   - [[Decision.Kind.FunnelledCtor]] — the emitted class has one primary and N secondaries,
    *     which is the funnel, in the code. (Its ESCAPING paths are a different matter and are
    *     counted by `OmissionCheck.promotedBodyOnEveryPath`; that is a finding, not a note.)
    *
    * Add a kind here and the check immediately demands it — which is the point.
    */
  val Rendered: Set[Decision.Kind] =
    import Decision.Kind.*
    Set(RenamedType, RenamedPackage, RenamedMember, DroppedType, DroppedMember,
        SubstitutedBody, InjectedMember, DroppedSuperCall, WidenedVisibility, Unrenderable)

  /** WHERE each rendered kind's note goes, which is not a style question: the three answers are
    * three different pieces of machinery and a kind in the wrong one is a note that never appears.
    *
    *   - [[AtDeclaration]] — the subject IS emitted, so the note sits immediately above it (after
    *     its original trivia). Every rename, every substituted body, every widening.
    *   - [[InBody]] — the subject is a TYPE and the decision is about something that is NOT in it.
    *     A dropped member has no `def` to sit above, so its note goes at the head of the owning
    *     type's body, where a reader looking for the member finds it instead.
    *   - [[NotInTree]] — no emitted unit corresponds to the subject at all. A dropped TYPE's note
    *     is carried by the INJECTED file that supplies its FQN (`PortRun` prepends it at copy
    *     time); when nothing replaces it, `decisions.tsv` and the port map are the whole record,
    *     and that is the honest answer rather than a note in a file that does not exist.
    */
  val InBody: Set[Decision.Kind]    = Set(Decision.Kind.DroppedMember)
  val NotInTree: Set[Decision.Kind] = Set(Decision.Kind.DroppedType)
  val AtDeclaration: Set[Decision.Kind] = Rendered -- InBody -- NotInTree

  /** `RenamedMember` -> `renamed-member`. The enum name is the source; a decider never names a
    * slug, so two deciders cannot spell one act two ways. */
  def slug(k: Decision.Kind): String =
    k.toString.flatMap(c => if c.isUpper then "-" + c.toLower else c.toString).stripPrefix("-")

  /** every kind, by its slug — how a scan over emitted text turns a note back into a kind. */
  private lazy val bySlug: Map[String, Decision.Kind] =
    Decision.Kind.values.map(k => slug(k) -> k).toMap

  /** Neutralise anything that could open or close a comment, and flatten to one line.
    *
    * NOT a rejection: a value that cannot be rendered safely is still information, and dropping it
    * would make the note say less than the TSV for no reason a reader could see. The delimiters are
    * spaced apart (`/ *`), which survives grep and cannot nest. */
  def safe(s: String): String =
    s.replace("/*", "/ *").replace("*/", "* /")
      .replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')
      .replaceAll(" +", " ").trim

  /** A VALUE, as it appears after the `=`.
    *
    * Quoted when it contains whitespace, and that is not cosmetic: the pair list is
    * whitespace-separated, so an unquoted `key=com.badlogic.gdx -> sge` is three tokens and every
    * reader — [[scan]] included — silently truncates the value at the first space. That is exactly
    * how the coverage check's first run reported 594 notes as unbacked: both sides were reading a
    * value neither of them had written. */
  def value(v: String): String =
    val s = safe(v)
    if s.exists(_.isWhitespace) || s.isEmpty then "\"" + s.replace("\"", "'") + "\"" else s

  /** the `k=v` half, in a fixed order: the §1 classification first (it is the question an agent
    * asks first), then the decision's own detail, sorted. */
  def pairs(d: Decision): List[(String, String)] =
    val cls = ("reason" -> d.reason.className) :: (d.reason match
      case Reason.Universal(r)     => List("rule" -> r)
      case Reason.LibraryRule(r)   => List("rule" -> r)
      case Reason.Configured(p, k) => List("phase" -> p, "key" -> k))
    cls ++ d.detail.filterNot(_._1 == "why").toList.sorted

  /** Render one decision at `indent`, with a trailing newline. `""` for a kind that carries no
    * note — so a call site can splice this in unconditionally and a kind leaving [[Rendered]]
    * removes its notes with no other edit. */
  def render(d: Decision, indent: String, width: Int = 110): String =
    if !Rendered(d.kind) then ""
    else
      val head = s"$indent$Marker ${slug(d.kind)} " +
        pairs(d).map((k, v) => s"$k=${value(v)}").mkString(" ")
      d.detail.get("why").map(safe).filter(_.nonEmpty) match
        case scala.None                                          => head + " */\n"
        case Some(w) if head.length + w.length + 6 <= width       => s"$head — $w */\n"
        case Some(w)                                              => s"$head\n$indent   — $w */\n"

  /** One note as it stands IN THE EMITTED TEXT. `kind` is `None` for a slug no [[Decision.Kind]]
    * has — which is not a parse failure to swallow but the loudest thing this scan can find: a
    * note nothing in the engine could have derived. */
  final case class Found(slug: String, kind: Option[Decision.Kind])

  /** every note in `text`, in order. Deliberately reads only the SLUG: the `k=v` half is for a
    * human and for grep, and a check that parsed it would be asserting on a rendering rather than
    * on the fact. [[NoteCoverageCheck]] joins on what the emitter RECORDED instead, which is the
    * only side that can be authoritative about which decision produced which note. */
  def scan(text: String): List[Found] =
    val out = collection.mutable.ListBuffer.empty[Found]
    var i   = text.indexOf(Marker)
    while i >= 0 do
      val rest = text.substring(i + Marker.length).dropWhile(_.isWhitespace)
      val s    = rest.takeWhile(c => !c.isWhitespace)
      out += Found(s, bySlug.get(s))
      i = text.indexOf(Marker, i + Marker.length)
    out.toList

  /** What the EMITTER printed, recorded as it printed it — the same discipline as
    * [[SrcMap.Recording]], and a value one emitter owns rather than a process-global table (§5.1).
    *
    * `subject` is the SymId the note was rendered for, which is what makes the coverage check a
    * join rather than a name match: a member renamed by the emitter has a different name at
    * emission than the decision recorded, and every name-keyed version of this check was quietly
    * empty on exactly the decisions it exists to police. */
  final case class Printed(kind: Decision.Kind, subject: SymId, subjectFqn: String, unit: String)
