package balticporter.transform

import balticporter.core.{PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*

/** Replaces a CALL with ready-made Scala naming the call's own receiver and arguments — the
  * call-level twin of [[MethodBodyTransform]] (`ENGINE-LIMITS.md` D7). A key is a [[MemberKey]]
  * naming the resolved callee (exact — else arity-ambiguous); the value is a template with
  * `{recv}`, `{arg0}`…`{argN}`, `{{`/`}}`, parsed once, spliced as a [[Tree.Opaque]] over terms.
  * §1(b): mechanism universal, `calls` per-library; every refusal counted, not approximated. */
final class CallSiteSubstitutionTransform(calls: Map[String, String] = Map.empty)
    extends Phase, PolicySource, SurfacePolicy, PolicyBound:
  def name: String = "call-site-substitution"

  import CallSiteSubstitutionTransform.{Bound as BoundCall, Setting, Template, receiverOf, siteFault}

  // -------------------------------------------------------------------------
  // BINDING — every question about what a key names is answered here, once (§8.1)
  // -------------------------------------------------------------------------

  private var bound: Map[String, Binding[PolicyBinder.Hit]] = Map.empty
  private var templates: Map[String, Either[String, Template]] = Map.empty
  private var records: List[PolicyBinder.Record] = Nil

  /** callee symbol → everything a site needs. Built only from keys that bound exactly, whose
    * template parsed, and whose holes fit the callee's arity. */
  private var bySym: Map[SymId, BoundCall] = Map.empty

  /** keys whose overload identity the engine cannot prove, and keys whose template does not fit
    * the callee — both reported by [[policyReport]], neither installed. */
  private var faults: Map[String, (PolicyIssue, String)] = Map.empty

  def bindPolicy(binder: PolicyBinder): Unit =
    // Ownership.Either: a callee is normally a member this module does not declare (JDK's or a
    // base module's); bound as Owned this would report its own correct rewrites as never-matched.
    bound     = calls.keys.toList.sorted.map(k => k -> binder.bindCallee(name, Setting, k)).toMap
    records   = binder.recordsFor(name)
    templates = calls.map((k, t) => k -> Template.parse(t))

    val program = binder.program
    val ok      = Map.newBuilder[SymId, BoundCall]
    val bad     = Map.newBuilder[String, (PolicyIssue, String)]
    bound.toList.sortBy(_._1).foreach { (key, b) =>
      (b.toOption, templates(key)) match
        case (Some(hit), Right(tmpl)) =>
          val arity = arityOf(program, hit)
          (exactnessFault(program, hit), arity.flatMap(a => templateFault(program, hit, tmpl, a))) match
            case (Some(why), _) => bad += key -> (PolicyIssue.Unverifiable, why)
            case (_, Some(why)) => bad += key -> (PolicyIssue.Malformed, why)
            case _              =>
              hit.sym.foreach(s => ok += s -> BoundCall(key, tmpl, arity.getOrElse(0), hit.dropped))
        case _ => () // an unbound key or an unparseable template; both already have their finding
    }
    bySym  = ok.result()
    faults = bad.result()

  /** the callee's declared parameter count, from the hit's key or else the symbol. `None` only
    * where neither exists, which [[exactnessFault]] has already refused. */
  private def arityOf(program: Program, hit: PolicyBinder.Hit): Option[Int] =
    hit.key.descriptor.map(_.arity)
      .orElse(hit.sym.flatMap(program.symbolOf).flatMap(_.descriptor).map(_.arity))

  /** Can the engine prove which overload this key named? `Some(why)` when it cannot — an external
    * member the frontend could not resolve interns with no descriptor, so a bare key matches by
    * owner and name alone, the same string for every overload. DESIGN.md §8.1 */
  private def exactnessFault(program: Program, hit: PolicyBinder.Hit): Option[String] =
    if hit.key.descriptor.isDefined then None
    else if hit.sym.flatMap(program.symbolOf).flatMap(_.descriptor).isDefined then None
    else Some(
      "the callee bound through an ERASURE-APPROXIMATE identity: it names an external member whose " +
        "declaration the frontend could not resolve, so it carries no parameter spelling and this " +
        "bare key matched by owner and name alone — the same string for every overload " +
        "(DESIGN.md §8.1). Write the precise `owner#name(P1,P2)` form; if the spelling is still " +
        "absent the member is outside this run's reach and its calls cannot be substituted safely")

  /** `Some(complaint)` when the template names something this callee cannot supply. Checked at
    * bind time from the callee's declared arity — reading it at match time from one call's
    * argument list would accept the template and then be wrong at the next site. */
  private def templateFault(program: Program, hit: PolicyBinder.Hit, t: Template,
                            arity: Int): Option[String] =
    if t.maxArg.exists(_ >= arity) then
      Some(s"the template names {arg${t.maxArg.get}} and the callee takes $arity argument(s) — a " +
        "positional hole beyond the arity can never be filled")
    else if t.usesRecv && hit.sym.flatMap(program.symbolOf).exists(_.flags.isStatic) then
      Some("the template names {recv} and the callee is STATIC, so there is no receiver to splice")
    else None

  /** Two modules rewriting one shared call differently produce sites that cannot compile
    * together (§1.5), so the template is fingerprinted along with the key. */
  def surfaceFingerprint: String =
    calls.toList.sorted.map((k, v) => s"$k=${v.hashCode.toHexString}").mkString(",")

  // -------------------------------------------------------------------------
  // the run's own record
  // -------------------------------------------------------------------------

  private val refused = collection.mutable.ListBuffer.empty[(String, String, Origin)]
  private val done    = collection.mutable.Map.empty[String, Int]

  /** call sites rewritten, by declared key, in a stable order. Reflects the last [[run]]. */
  def substituted: List[(String, Int)] = done.toList.sortBy(_._1)

  /** every call this phase was asked to rewrite and did not, with its reason and Java origin. */
  def refusals: List[(String, String, Origin)] =
    refused.toList.distinct.sortBy((k, w, o) => (k, o.javaPath, o.line, w))

  /** Never-fired keys, unparseable templates, unprovable overload identity, templates that do not
    * fit their callee, and every refused site. The first four are complete once keys are bound,
    * before [[run]]; refusals are properties of the run and empty before it. */
  def policyReport: PolicyReport =
    def finding(k: String, issue: PolicyIssue, detail: String) =
      PolicyFinding(name, Setting, k, issue, detail)
    PolicyReport.fromBindings(records) ++ PolicyReport(
      templates.toList.sortBy(_._1).collect {
        case (k, Left(why)) => finding(k, PolicyIssue.Malformed, why)
      } ++
      faults.toList.sortBy(_._1).map((k, f) => finding(k, f._1, f._2)) ++
      refusals.map((k, why, o) => finding(k, PolicyIssue.Unverifiable,
        s"$why — at ${o.javaPath}:${o.line}, the call is left as upstream wrote it")) ++
      // a key that bound and rewrote nothing: either nothing calls it, or an EARLIER phase already
      // re-pointed those calls, so this phase's callee symbol occurs nowhere. Ordering is the
      // port's to fix; nothing else in the pipeline can see it.
      bySym.values.map(_.key).toList.distinct.sorted
        .filterNot(k => done.contains(k) || refused.exists(_._1 == k))
        .map(k => finding(k, PolicyIssue.NeverMatched,
          "the key bound to a real callee and NO call site named it when this phase ran. Either " +
            "nothing in this program calls that member, or a phase that ran EARLIER re-pointed " +
            "those calls at something else — a call-site substitution must be placed before any " +
            "phase that rewrites the same callee, and `Pipeline.order` is stable in the order the " +
            "port declares its surface")))

  // -------------------------------------------------------------------------
  // the rewrite
  // -------------------------------------------------------------------------

  override def run(program: Program): Program =
    refused.clear()
    done.clear()
    if bySym.isEmpty then return program

    // one decision row per (declaration, key), read from the pre-rewrite program. Held to
    // declarations this phase will ACTUALLY rewrite via siteFault, the same predicate the
    // traversal applies, so a row can never claim a substitution that was refused.
    bySym.toList.sortBy((s, b) => (b.key, s.raw)).foreach { (callee, b) =>
      val calleeFqn = program.symbolOf(callee).map(_.fullName).getOrElse(b.key)
      val rewritten = program.usages(callee).groupBy(_.enclosing).view
        .mapValues(_.count(u => u.site match
          case a: Tree.Apply => siteFault(a, b).isEmpty
          case _             => false))
        .toMap
      Decision.declarationsUsing(program, callee)
        .filter((encl, _) => rewritten.getOrElse(encl, 0) > 0)
        .foreach { (encl, origin) =>
          record(Decision(
            kind       = Decision.Kind.SubstitutedCall,
            subject    = encl,
            subjectFqn = Decision.fqnOf(program, encl, calleeFqn),
            // key not repeated here: Reason.Configured already carries it.
            detail     = Map(
              "sites" -> rewritten(encl).toString,
              "to"    -> calls(b.key),
              "why"   -> ("the port does not call this member: the call is replaced by " +
                "ready-made Scala naming the same receiver and arguments, so everything around it " +
                "keeps its type and only what runs is this port's rather than upstream's"),
            ) ++ Option.when(b.dropped)("callee" ->
              "DROPPED by this port's Substitutions.dropMethods — there is no declaration to return to"),
            reason = Reason.Configured(name, b.key),
            origin = origin,
          ))
        }
    }

    given Program = program
    val units = program.units.map(u => StandardTraversal.mapClassDef(this, u))
    program.rebuilt(units) // xref rebuilt by the Pipeline

  override def transformApply(t: Tree.Apply)(using Program): Term =
    bySym.get(t.method) match
      case None => t
      case Some(b) =>
        siteFault(t, b) match
          case Some(why) => refused += ((b.key, why, t.origin)); t
          case None =>
            done.update(b.key, done.getOrElse(b.key, 0) + 1)
            b.template.splice(receiverOf(t), t.args, t.tpe, t.origin)

  /** `Foo::bar` naming a substituted callee — no argument list to splice a template into. */
  override def transformTerm(t: Term)(using Program): Term =
    t match
      case mr: Tree.MethodRef if bySym.contains(mr.method) =>
        refused += ((bySym(mr.method).key,
          "the callee is used as a METHOD VALUE (`::`), which has no argument list for a " +
            "positional template to name", mr.origin))
        mr
      case other => other

/** the template grammar, and the two structural questions the phase asks of a site. */
object CallSiteSubstitutionTransform:

  /** the `setting` every finding this phase makes is filed under (§4.575). */
  val Setting = "CallSiteSubstitutionTransform(calls)"

  /** one installed key: what an individual call site needs, resolved once at bind time. */
  final case class Bound(key: String, template: Template, arity: Int, dropped: Boolean)

  /** the receiver term of a call, seen through an explicit type application (`xs.sort[T](c)` is
    * `Apply(TypeApply(Select(xs, sort), [T]), …)` — matching only `Select` misses it). A bare
    * `Ident` call has no receiver term at all (static, or unqualified on `this`). */
  def receiverOf(t: Tree.Apply): Option[Term] = t.fun match
    case Tree.Select(r, _, _, _)                          => Some(r)
    case Tree.TypeApply(Tree.Select(r, _, _, _), _, _, _) => Some(r)
    case _                                                => None

  /** Why this site cannot take the template — `None` when it can. One predicate, applied by both
    * the rewriting traversal and the decision recorder, so a decision can never claim a
    * substitution the rewrite refused. */
  def siteFault(t: Tree.Apply, b: Bound): Option[String] =
    if t.args.exists(a => Tree.uncomment(a).isInstanceOf[Tree.Repeated]) then
      Some("the call passes a VARARG SPREAD, which no positional hole can name")
    else if b.template.maxArg.isDefined && t.args.sizeIs != b.arity then
      Some(s"this call site carries ${t.args.size} argument term(s) where the callee declares " +
        s"${b.arity}, so the template's positions no longer name what they were checked against")
    else if b.template.usesRecv && receiverOf(t).isEmpty then
      Some("the template names {recv} and this call has no receiver term (a static or unqualified call)")
    else None

  /** ONE hole. */
  enum Hole:
    case Recv
    case Arg(index: Int)

  /** A parsed expression template — literal parts interleaved with holes. Parsed once at bind
    * time, so a template fault becomes a finding before the pipeline runs. DESIGN.md §8.1
    * `parts` always has exactly one more element than `holes`. */
  final case class Template(parts: List[String], holes: List[Hole]):
    def usesRecv: Boolean   = holes.contains(Hole.Recv)
    /** the highest `{argN}` index the template names, or `None` when it names no argument. */
    def maxArg: Option[Int] = holes.collect { case Hole.Arg(i) => i }.maxOption

    /** the template with `recv` and `args` spliced in — as a [[Tree.Opaque]] carrying the TERMS,
      * never a rendered string (see `Tree.Opaque`). Guarded by [[siteFault]] at every caller. */
    def splice(recv: Option[Term], args: List[Term], tpe: TypeRepr, origin: Origin): Term =
      val terms = holes.map {
        case Hole.Recv   => recv.get
        case Hole.Arg(i) => args(i)
      }
      Tree.Opaque.spliced(parts, terms, tpe, origin)

  object Template:

    /** Parse a template, or say precisely what is wrong with it. Tiny grammar, refused outside it
      * rather than carried through as literal text — a lenient parse would emit `{arg0}` as an
      * unattributable compile error. `{recv}` the receiver; `{arg0}`…`{argN}` positional
      * arguments; `{{`/`}}` a literal brace. */
    def parse(text: String): Either[String, Template] =
      val parts = List.newBuilder[String]
      val holes = List.newBuilder[Hole]
      val cur   = new StringBuilder
      var i     = 0
      var bad   = Option.empty[String]
      while bad.isEmpty && i < text.length do
        text.charAt(i) match
          case '{' if i + 1 < text.length && text.charAt(i + 1) == '{' => cur.append('{'); i += 2
          case '}' if i + 1 < text.length && text.charAt(i + 1) == '}' => cur.append('}'); i += 2
          case '}' => bad = Some(s"an unmatched `}` at index $i — write `}}` for a literal brace")
          case '{' =>
            val close = text.indexOf('}', i + 1)
            if close < 0 then bad = Some(s"an unclosed `{` at index $i — write `{{` for a literal brace")
            else
              val nm = text.substring(i + 1, close)
              holeOf(nm) match
                case Some(h) => parts += cur.toString; cur.clear(); holes += h; i = close + 1
                case None =>
                  bad = Some(s"`{$nm}` at index $i is not a hole: write `{recv}` for the receiver, " +
                    "`{arg0}`…`{argN}` for the arguments, or `{{` for a literal brace")
          case c => cur.append(c); i += 1
      bad.toLeft {
        parts += cur.toString
        Template(parts.result(), holes.result())
      }

    private def holeOf(nm: String): Option[Hole] =
      if nm == "recv" then Some(Hole.Recv)
      else if nm.startsWith("arg") then nm.drop(3).toIntOption.filter(_ >= 0).map(Hole.Arg.apply)
      else None
