package balticporter.transform

import balticporter.core.{PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*

/** Replace a CALL with ready-made Scala that names the call's own receiver and arguments — the
  * call-level twin of [[MethodBodyTransform]].
  *
  * ==The gap this fills==
  * A port has three seams for code it must not translate mechanically and all three are
  * WHOLE-DECLARATION: `Substitutions.dropTypes` removes a type and `inject` supplies a replacement
  * file, `dropMethods` removes a method and supplies nothing, and [[MethodBodyTransform]] replaces
  * the body of a method it keeps. None of them can say *keep this method, rewrite this one call in
  * it* — and that is the shape a dependent module keeps arriving at (`ENGINE-LIMITS.md` D7): the
  * base drops a member, the dependent still calls it, and the call sits at one line of a 464-line
  * method that is otherwise entirely mechanical. Replacing that method's body would fork it from
  * upstream permanently to change one argument; dropping it would delete the feature.
  *
  * ==What a policy entry says==
  * {{{
  * new CallSiteSubstitutionTransform(Map(
  *   "com.foo.Json#fromJson(Class,FileHandle)" -> "com.bar.Codecs.read({arg0}, {arg1}.readString())",
  *   "java.util.Collections#sort(List,Comparator)" -> "{arg0}.sortInPlace()(using {arg1})",
  * ))
  * }}}
  * The key is a [[MemberKey]] naming the RESOLVED CALLEE, bound through [[PolicyBinder]] like every
  * other keyed seam. The value is an EXPRESSION TEMPLATE: ready-made Scala with `{recv}` for the
  * call's receiver and `{arg0}`…`{argN}` for its arguments, `{{`/`}}` for a literal brace, and
  * CLAUDE.md §6 applying to what is written around them (fully-qualified names, no imports).
  *
  * Two things about the numbering, because both are easy to assume wrongly. It is the CALLEE's
  * parameter positions, not the java call's token positions: a VARARG method's trailing arguments
  * are packed into one array before the TIR sees them, so a `f(T, U...)` callee has `{arg0}` and
  * `{arg1}`, the second being the array. And a receiver is never `{arg0}` — an instance call's
  * receiver is `{recv}`, and a template that confuses the two is an arity fault at bind time
  * rather than a wrong rewrite.
  *
  * ==Exactness is REQUIRED, and that is what the key grammar is for==
  * A bare `owner#name` key names every overload, and DESIGN.md §8.1 draws the line here explicitly:
  * a bare key is legal for `dropMethods` (that is how a reflective family is dropped wholesale) and
  * is refused for a call-site substitution, because a positional template can only be right for one
  * arity. The flagship is CLAUDE.md §4.4's `remove`: a key for `remove(Object)` must not rewrite
  * `remove(int)`, and nothing but the descriptor can tell them apart — `Symbol.fullName` is the
  * same string for both. So a key that names more than one overload is `Ambiguous` with the
  * candidates listed, never one of them picked.
  *
  * ==The holes are TREES, and the template is parsed ONCE==
  * The template is parsed at BIND time into literal parts and numbered holes, so a `{arg7}` on a
  * two-argument callee is a `Malformed` finding before the pipeline runs rather than a compile
  * error in emitted Scala nobody attributes. At each site the parts and the call's own argument
  * TERMS are assembled into a [[Tree.Opaque]] with holes — not a string — which is what keeps the
  * spliced arguments visible to every later phase (the package rename runs LAST, §4.56) and to the
  * xref. Rendering them is the emitter's job, because printing a term is only ever the emitter's
  * job.
  *
  * ==Refusals, each counted rather than approximated==
  * A refusal is a call the port asked to rewrite and this phase left alone, which is exactly the
  * silent no-op [[PolicyReport]] exists for. Each is reported with its Java origin, and none is
  * best-effort'd:
  *
  *   - '''a VARARG SPREAD among the arguments.''' A [[Tree.Repeated]] holds a variable number of
  *     terms behind one argument position and a positional hole names a term. Spliced anyway it
  *     would emit a bare comma list into whatever the template wrote around it.
  *   - '''an argument count this site does not have.''' The callee's declared arity is what the
  *     template was checked against; a site carrying a different number of terms is one the
  *     frontend shaped differently, and the positions can no longer be trusted.
  *   - '''`{recv}` with no receiver.''' A static call, or one java wrote unqualified.
  *   - '''the callee used as a METHOD VALUE''' (`Foo::bar`). There is no argument list to splice,
  *     so a template with holes has nothing to say; the reference survives and names a callee the
  *     port declared it does not call.
  *
  * ==Kind==
  * CLAUDE.md §1(b). The MECHANISM — bind a callee, parse a template, splice the call's own terms —
  * is a fact about Java and Scala and is the same for every library. WHICH calls and WHAT Scala is
  * one library's knowledge and arrives as a constructor parameter. An empty map is a no-op with no
  * code path of its own.
  *
  * @param calls
  *   resolved-callee member key → the expression template to replace each call with.
  */
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

  /** callee symbol → everything a site needs. Built only from keys that BOUND exactly, whose
    * template PARSED, and whose holes fit the callee's arity — so a faulty entry is a finding and
    * never a half-applied rewrite. */
  private var bySym: Map[SymId, BoundCall] = Map.empty

  /** keys whose overload identity the engine cannot prove, and keys whose template does not fit
    * the callee — both reported by [[policyReport]], neither installed. */
  private var faults: Map[String, (PolicyIssue, String)] = Map.empty

  def bindPolicy(binder: PolicyBinder): Unit =
    // `Ownership.Either`, and for `TypeRedirectTransform`'s reason rather than by relaxation: what
    // this phase rewrites is a REFERENCE, and a callee is normally a member this module does not
    // declare — the JDK's, or a base module's. Bound as `Owned` the whole mechanism would report
    // its own correct rewrites as never-matched.
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

  /** the callee's declared parameter count — from the HIT's key when it carries one (the index's
    * own spelling, or the precise key the author wrote), otherwise from the symbol. `None` only
    * where neither exists, which [[exactnessFault]] has already refused. */
  private def arityOf(program: Program, hit: PolicyBinder.Hit): Option[Int] =
    hit.key.descriptor.map(_.arity)
      .orElse(hit.sym.flatMap(program.symbolOf).flatMap(_.descriptor).map(_.arity))

  /** Can the engine PROVE which overload this key named? `Some(why)` when it cannot.
    *
    * The proof is a descriptor, and the one place it can be missing is the one DESIGN.md §8.1
    * admits: an external member whose declaration the frontend could not resolve is interned with
    * no parameter spelling at all, so a BARE key matching it matched by owner and name alone —
    * which is the same string for every overload. A PRECISE key is unaffected by construction: the
    * binder requires the descriptor to be present and equal before it matches at all.
    *
    * Read from the HIT rather than from the declared string, so a bare key that landed on a member
    * the frontend DID record is accepted — a member with exactly one overload needs no parameter
    * list to be unambiguous, and the binder has already reported the case where it has more. */
  private def exactnessFault(program: Program, hit: PolicyBinder.Hit): Option[String] =
    if hit.key.descriptor.isDefined then None
    else if hit.sym.flatMap(program.symbolOf).flatMap(_.descriptor).isDefined then None
    else Some(
      "the callee bound through an ERASURE-APPROXIMATE identity: it names an external member whose " +
        "declaration the frontend could not resolve, so it carries no parameter spelling and this " +
        "bare key matched by owner and name alone — the same string for every overload " +
        "(DESIGN.md §8.1). Write the precise `owner#name(P1,P2)` form; if the spelling is still " +
        "absent the member is outside this run's reach and its calls cannot be substituted safely")

  /** `Some(complaint)` when the template names something this callee cannot supply.
    *
    * Checked at BIND time from the callee's own declared arity, which is the whole reason
    * `Symbol.descriptor` exists: an arity read at match time from one call's argument list would
    * accept the template and then be wrong at the next site. */
  private def templateFault(program: Program, hit: PolicyBinder.Hit, t: Template,
                            arity: Int): Option[String] =
    if t.maxArg.exists(_ >= arity) then
      Some(s"the template names {arg${t.maxArg.get}} and the callee takes $arity argument(s) — a " +
        "positional hole beyond the arity can never be filled")
    else if t.usesRecv && hit.sym.flatMap(program.symbolOf).exists(_.flags.isStatic) then
      Some("the template names {recv} and the callee is STATIC, so there is no receiver to splice")
    else None

  /** Two modules that rewrite one shared call differently produce call sites that each compile
    * alone and cannot compile together, which is [[SurfacePolicy]]'s whole subject (§1.5). The
    * TEMPLATE is fingerprinted as well as the key, because "we both rewrite `Json#fromJson`" is not
    * agreement when one writes a codec call and the other writes a throw. */
  def surfaceFingerprint: String =
    calls.toList.sorted.map((k, v) => s"$k=${v.hashCode.toHexString}").mkString(",")

  // -------------------------------------------------------------------------
  // the run's own record
  // -------------------------------------------------------------------------

  private val refused = collection.mutable.ListBuffer.empty[(String, String, Origin)]
  private val done    = collection.mutable.Map.empty[String, Int]

  /** call sites rewritten, by declared key, in a stable order — so a run can STATE the number
    * rather than leaving it to be inferred from a diff. Reflects the last [[run]]. */
  def substituted: List[(String, Int)] = done.toList.sortBy(_._1)

  /** every call this phase was asked to rewrite and did not, with its reason and its Java origin.
    * A refusal moves no error count and no test — it is a call that still says what upstream said,
    * in a port that declared it should not. */
  def refusals: List[(String, String, Origin)] =
    refused.toList.distinct.sortBy((k, w, o) => (k, o.javaPath, o.line, w))

  /** Never-fired keys, unparseable templates, unprovable overload identity, templates that do not
    * fit their callee, and every refused SITE.
    *
    * The first four are properties of the POLICY and the PROGRAM and are complete the moment the
    * keys are bound — so a phase that never ran reports the same thing a phase that ran and matched
    * nothing does. The refusals are properties of the RUN and are empty before it. */
  def policyReport: PolicyReport =
    def finding(k: String, issue: PolicyIssue, detail: String) =
      PolicyFinding(name, Setting, k, issue, detail)
    PolicyReport.fromBindings(records) ++ PolicyReport(
      templates.toList.sortBy(_._1).collect {
        case (k, Left(why)) => finding(k, PolicyIssue.Malformed, why)
      } ++
      faults.toList.sortBy(_._1).map((k, f) => finding(k, f._1, f._2)) ++
      refusals.map((k, why, o) => finding(k, PolicyIssue.Unverifiable,
        s"$why — at ${o.javaPath}:${o.line}, the call is left as upstream wrote it")))

  // -------------------------------------------------------------------------
  // the rewrite
  // -------------------------------------------------------------------------

  override def run(program: Program): Program =
    refused.clear()
    done.clear()
    if bySym.isEmpty then return program

    // DECISION PROVENANCE, one row per (DECLARATION, key) — `Decision.declarationsUsing`'s rule,
    // read from the PRE-rewrite program, which is the only one that still names the callee.
    //
    // Held to the declarations this phase will ACTUALLY rewrite: a row claiming a substitution
    // that was refused would also emit a porter note claiming it, and a note is the one artifact a
    // reader takes at face value. `siteFault` is the same predicate the traversal applies, so the
    // two cannot disagree. The site COUNT goes in the detail because a substitution is not visible
    // as a name change in the diff — that is what separates it from a redirect — so how many of a
    // method's calls the port replaced has nowhere else to be said.
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
            detail     = Map(
              "callee"  -> b.key,
              "sites"   -> rewritten(encl).toString,
              "to"      -> calls(b.key),
              "key"     -> b.key,
              "dropped" -> (if b.dropped then "yes" else "no"),
              "why"     -> ("the port does not call this member: the call is replaced by " +
                "ready-made Scala naming the same receiver and arguments, so everything around it " +
                "keeps its type and only what runs is this port's rather than upstream's"),
            ),
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

  /** `Foo::bar` naming a substituted callee. There is no argument list at a method VALUE, so a
    * positional template has nothing to splice — and left silent this is a surviving reference to
    * exactly the member the port declared it does not call. */
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

  /** the `setting` every finding this phase makes is filed under — the string an agent greps for in
    * the manifest (§4.575). */
  val Setting = "CallSiteSubstitutionTransform(calls)"

  /** one installed key: what an individual call site needs to know, resolved once at bind time. */
  final case class Bound(key: String, template: Template, arity: Int, dropped: Boolean)

  /** the RECEIVER term of a call, seen through an explicit type application.
    *
    * `xs.sort[T](c)` is `Apply(TypeApply(Select(xs, sort), [T]), …)`, and matching only `Select`
    * silently skips every explicitly-instantiated call — the shape `CollectionsTransform` records
    * having been bitten by. A call through a bare `Ident` has no receiver TERM at all (a static, or
    * an unqualified call on `this`), which is a different answer from "not a call". */
  def receiverOf(t: Tree.Apply): Option[Term] = t.fun match
    case Tree.Select(r, _, _, _)                          => Some(r)
    case Tree.TypeApply(Tree.Select(r, _, _, _), _, _, _) => Some(r)
    case _                                                => None

  /** Why this SITE cannot take the template — `None` when it can.
    *
    * ONE predicate, applied by the traversal that rewrites and by the pass that records the
    * decisions, so a decision can never claim a substitution the rewrite refused. */
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

  /** A parsed expression template — literal parts interleaved with holes.
    *
    * Parsed ONCE, at bind time, for the reason DESIGN.md §8.1 gives for binding keys there: a
    * template fault becomes a finding before the pipeline runs, where the same fault discovered at
    * emission is a compile error in generated Scala that nobody attributes to a manifest entry.
    *
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

    /** Parse a template, or say precisely what is wrong with it.
      *
      * The grammar is deliberately tiny and everything outside it is REFUSED rather than carried
      * through as literal text, for `MemberKey.parse`'s reason: the failure mode of a lenient parse
      * is a template that emits `{arg0}` into the output, where it is a compile error naming a
      * GENERATED file rather than the policy entry that caused it.
      *
      *   - `{recv}`            the call's receiver
      *   - `{arg0}` … `{argN}` its arguments, positionally
      *   - `{{` and `}}`       a literal `{` and `}`
      */
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
