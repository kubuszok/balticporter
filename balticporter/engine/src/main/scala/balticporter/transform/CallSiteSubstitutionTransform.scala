package balticporter.transform

import balticporter.core.{ MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy }
import balticporter.tir.*

/** Replaces a CALL with ready-made Scala naming the call's own receiver and arguments — the call-level twin of [[MethodBodyTransform]], used where a base drops a member a dependent still calls and
  * only that one call, never the whole declaration, can be rewritten. An entry is a [[MemberKey]] naming the resolved callee, a template with `{recv}`, `{this}`, `{arg0}`…`{argN}`, `{{`/`}}`, and the
  * SCOPE of the declarations whose calls it rewrites. One callee may carry several entries: at each call the most specific scope admitting the enclosing declaration wins, and a tie is refused and
  * counted.
  */
final class CallSiteSubstitutionTransform(val entries: List[CallSiteSubstitutionTransform.Entry]) extends Phase, PolicySource, SurfacePolicy, MergeablePolicy, PolicyBound:

  /** one unscoped entry per key — the table every port wrote before an entry could be scoped. */
  def this(calls: Map[String, String] = Map.empty) =
    this(calls.toList.sorted.map((k, t) => CallSiteSubstitutionTransform.Entry(k, t)))

  def name: String = "call-site-substitution"

  import CallSiteSubstitutionTransform.{ Bound as BoundCall, Entry, NoEnclosingSelf, Setting, Template, ThisRefused, enclosingSelf, rankKeys, receiverOf, selfTerm, siteFault, ties }

  /** callee key → its template, for the keys whose only entry is the unscoped default — what a port with no scoped entry reads. */
  def calls: Map[String, String] = entries.filter(_.scope.isUnrestricted).map(e => e.call -> e.template).toMap

  /** A call keyed on its callee is re-pointed before any phase can move that callee elsewhere — a redirect twins the member onto its target type and the key would then name no call.
    */
  override def runsBefore: Set[String] = Set("type-redirect")

  // -------------------------------------------------------------------------
  // BINDING — every question about what a key names is answered here, once
  // -------------------------------------------------------------------------

  private var bound:     Map[String, Binding[PolicyBinder.Hit]] = Map.empty
  private var templates: Map[Entry, Either[String, Template]]   = Map.empty
  private var records:   List[PolicyBinder.Record]              = Nil

  /** callee symbol → every entry installed for it, each with what a site needs. Built only from keys that bound exactly, whose template parsed, and whose holes fit the callee's arity.
    */
  private var bySym: Map[SymId, List[(Entry, BoundCall)]] = Map.empty

  /** keys whose overload identity the engine cannot prove, templates that do not fit the callee, and entries tied with another at one scope — all reported by [[policyReport]]. */
  private var faults: List[(String, PolicyIssue, String)] = Nil

  def bindPolicy(binder: PolicyBinder): Unit =
    // Ownership.Either: a callee is normally a member this module does not declare (JDK's or a
    // base module's); bound as Owned this would report its own correct rewrites as never-matched.
    bound = entries.map(_.call).distinct.sorted.map(k => k -> binder.bindCallee(name, Setting, k)).toMap
    records = binder.recordsFor(name)
    templates = entries.map(e => e -> Template.parse(e.template)).toMap

    val program = binder.program
    val ok      = collection.mutable.Map.empty[SymId, List[(Entry, BoundCall)]]
    val bad     = List.newBuilder[(String, PolicyIssue, String)]
    entries.groupBy(_.call).toList.sortBy(_._1).foreach { (key, es) =>
      ties(es).foreach((e, f, at) =>
        bad += ((
          key,
          PolicyIssue.Malformed,
          s"""two entries rewrite this callee with different templates at the same scope ("$at"), so neither is more specific at a call there: """ +
            s"`${e.template}` and `${f.template}`. Such a call is refused and left as upstream wrote it"
        ))
      )
      bound.get(key).flatMap(_.toOption).foreach { hit =>
        val arity = arityOf(program, hit)
        exactnessFault(program, hit) match
          case Some(why) => bad += ((key, PolicyIssue.Unverifiable, why))
          case None      =>
            es.foreach { e =>
              templates(e) match
                case Right(tmpl) =>
                  arity.flatMap(a => templateFault(program, hit, tmpl, a)) match
                    case Some(why) => bad += ((key, PolicyIssue.Malformed, why))
                    case None      =>
                      hit.sym.foreach(s => ok.update(s, ok.getOrElse(s, Nil) :+ (e -> BoundCall(key, tmpl, arity.getOrElse(0), hit.dropped))))
                case Left(_) => () // an unparseable template already has its finding
            }
      }
    }
    bySym = ok.toMap
    faults = bad.result().distinct

  /** the callee's declared parameter count, from the hit's key or else the symbol. `None` only where neither exists, which [[exactnessFault]] has already refused.
    */
  private def arityOf(program: Program, hit: PolicyBinder.Hit): Option[Int] =
    hit.key.descriptor.map(_.arity).orElse(hit.sym.flatMap(program.symbolOf).flatMap(_.descriptor).map(_.arity))

  /** Can the engine prove which overload this key named? `Some(why)` when it cannot — an external member the frontend could not resolve interns with no descriptor, so a bare key matches by owner and
    * name alone, the same string for every overload.
    */
  private def exactnessFault(program: Program, hit: PolicyBinder.Hit): Option[String] =
    if hit.key.descriptor.isDefined then None
    else if hit.sym.flatMap(program.symbolOf).flatMap(_.descriptor).isDefined then None
    else
      Some(
        "the callee bound through an ERASURE-APPROXIMATE identity: it names an external member whose " +
          "declaration the frontend could not resolve, so it carries no parameter spelling and this " +
          "bare key matched by owner and name alone — the same string for every overload. " +
          "Write the precise `owner#name(P1,P2)` form; if the spelling is still " +
          "absent the member is outside this run's reach and its calls cannot be substituted safely"
      )

  /** `Some(complaint)` when the template names something this callee cannot supply. Checked at bind time from the callee's declared arity — reading it at match time from one call's argument list
    * would accept the template and then be wrong at the next site.
    */
  private def templateFault(program: Program, hit: PolicyBinder.Hit, t: Template, arity: Int): Option[String] =
    if t.maxArg.exists(_ >= arity) then
      Some(
        s"the template names {arg${t.maxArg.get}} and the callee takes $arity argument(s) — a " +
          "positional hole beyond the arity can never be filled"
      )
    else if t.usesRecv && hit.sym.flatMap(program.symbolOf).exists(_.flags.isStatic) then Some("the template names {recv} and the callee is STATIC, so there is no receiver to splice")
    else None

  /** Two modules rewriting one shared call differently produce sites that cannot compile together, so the template is fingerprinted along with the key; an unscoped entry renders exactly as it did
    * before an entry could carry a scope.
    */
  def surfaceFingerprint: String =
    entries.map(e => s"${e.call}=${e.template.hashCode.toHexString}" + (if e.scope.isUnrestricted then "" else s"@${e.scope.fingerprint}")).distinct.sorted.mkString(",")

  /** Independent entries union; an entry both hold is the one decision stated twice; two entries for one callee with different templates at a common scope refuse (a conflict only a human can
    * resolve). A dependent's instance folds into the base's at the base's position instead of a fatal `SurfaceDivergence`.
    */
  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case o: CallSiteSubstitutionTransform =>
      val fresh     = o.entries.filterNot(entries.contains)
      val conflicts = ties((entries ++ fresh).distinct).filter((e, f, _) => fresh.contains(e) || fresh.contains(f)).map((e, _, at) => s"${e.call}: templates differ at scope \"$at\"")
      if conflicts.nonEmpty then Left(conflicts.distinct.sorted.mkString("; "))
      else
        Right(
          MergeablePolicy.Merged(new CallSiteSubstitutionTransform(entries ++ fresh), fresh.map(e => MergeablePolicy.subjectOf(e.call)).toSet)
        )
    case _ => Left(s"expected CallSiteSubstitutionTransform, got ${later.getClass.getSimpleName}")

  def subjects: Set[String] = entries.map(e => MergeablePolicy.subjectOf(e.call)).toSet

  /** Where this instance rewrites calls of one subject's members: unrestricted when any entry for it is, else the union of its entries' `Only` scopes — so a dependent rewriting a base type's calls
    * only inside its own namespace is not an edit of the base's surface.
    */
  override def subjectScope(subject: String): RuleScope =
    val mine = entries.filter(e => MergeablePolicy.subjectOf(e.call) == subject).map(_.scope)
    if mine.isEmpty then RuleScope.everywhere
    else
      val only = mine.collect { case RuleScope.Only(inc) => inc }
      if only.size == mine.size then RuleScope.Only(only.flatten.toSet) else RuleScope.everywhere

  // -------------------------------------------------------------------------
  // the run's own record
  // -------------------------------------------------------------------------

  private val refused = collection.mutable.ListBuffer.empty[(String, String, Origin)]
  private val done    = collection.mutable.Map.empty[String, Int]

  /** scope entries this run saw a rewritten call under — [[RuleScope.neverFired]]'s complement. */
  private var firedScope: Set[String] = Set.empty

  /** (callee, call site) → the entry the site's enclosing declaration selected, or why none could be. Read from the pre-rewrite xref. */
  private var chosen: Map[(SymId, Origin), Either[String, (Entry, BoundCall)]] = Map.empty

  /** (callee, call site) → the `Only` scope entry that admitted it, marked fired once the site is really rewritten. */
  private var firedAt: Map[(SymId, Origin), String] = Map.empty

  /** (callee, call site) → the class whose instance `{this}` names there, or why none can be named. Only for sites whose chosen template has the hole. */
  private var selfAt: Map[(SymId, Origin), Either[String, SymId]] = Map.empty

  /** call sites rewritten, by declared key, in a stable order. Reflects the last [[run]]. */
  def substituted: List[(String, Int)] = done.toList.sortBy(_._1)

  /** every call this phase was asked to rewrite and did not, with its reason and Java origin. */
  def refusals: List[(String, String, Origin)] =
    refused.toList.distinct.sortBy((k, w, o) => (k, o.javaPath, o.line, w))

  /** Never-fired keys, unparseable templates, unprovable overload identity, templates that do not fit their callee, tied entries, scope entries no rewritten call sat under, and every refused site.
    */
  def policyReport: PolicyReport =
    def finding(k: String, issue: PolicyIssue, detail: String) =
      PolicyFinding(name, Setting, k, issue, detail)
    PolicyReport.fromBindings(records) ++ PolicyReport(
      templates.toList
        .sortBy(_._1.call)
        .collect { case (e, Left(why)) =>
          finding(e.call, PolicyIssue.Malformed, why)
        }
        .distinct ++
        faults.sortBy(_._1).map((k, i, d) => finding(k, i, d)) ++
        refusals.map((k, why, o) => finding(k, PolicyIssue.Unverifiable, s"$why — at ${o.javaPath}:${o.line}, the call is left as upstream wrote it")) ++
        entries
          .filter(e => !e.scope.isUnrestricted && bound.get(e.call).exists(_.isBound))
          .flatMap(e =>
            e.scope
              .neverFired(firedScope)
              .toList
              .sorted
              .map(s =>
                PolicyFinding(
                  name,
                  "CallSiteSubstitutionTransform(scope)",
                  s,
                  PolicyIssue.NeverMatched,
                  s"""no rewritten call of `${e.call}` sits under "$s", so this scope entry decided nothing"""
                )
              )
          )
          .distinct ++
        // a key that bound and rewrote nothing: either nothing calls it, or an EARLIER phase already
        // re-pointed those calls, so this phase's callee symbol occurs nowhere. Ordering is the
        // port's to fix; nothing else in the pipeline can see it.
        bySym.values.flatten
          .map(_._2.key)
          .toList
          .distinct
          .sorted
          .filterNot(k => done.contains(k) || refused.exists(_._1 == k))
          .map(k =>
            finding(
              k,
              PolicyIssue.NeverMatched,
              "the key bound to a real callee and NO call site named it when this phase ran. Either " +
                "nothing in this program calls that member, or a phase that ran EARLIER re-pointed " +
                "those calls at something else — a call-site substitution must be placed before any " +
                "phase that rewrites the same callee, and `Pipeline.order` is stable in the order the " +
                "port declares its surface"
            )
          )
    )

  // -------------------------------------------------------------------------
  // the rewrite
  // -------------------------------------------------------------------------

  /** The entry a call inside `encl` takes: the most specific scope admitting it — an `Only` entry by the length of the entry that names the declaration, above every unscoped one. `Left` when two
    * entries with different templates are equally specific there, or when none admits it (`Right(None)`).
    */
  private def select(program: Program, cands: List[(Entry, BoundCall)], encl: Option[Symbol]): Either[String, Option[(Entry, BoundCall)]] =
    def rank(sc: RuleScope): Option[Int] = sc match
      case RuleScope.Only(_) => encl.flatMap(s => sc.entryFor(program, s)).map(_.length)
      // an unresolvable enclosing declaration takes the conservative arm — IN for `Everywhere`
      case RuleScope.Everywhere(_) => Option.when(encl.forall(s => sc.includes(program, s)))(-1)
    val admitted = cands.flatMap(c => rank(c._1.scope).map(_ -> c))
    admitted.map(_._1).maxOption match
      case None      => Right(None)
      case Some(top) =>
        admitted.filter(_._1 == top).map(_._2).distinctBy(_._1.template) match
          case List(one) => Right(Some(one))
          case many      =>
            Left(
              s"entries with templates ${many.map(c => s"`${c._1.template}`").mkString(" and ")} are equally specific at this call's declaration, so neither is chosen"
            )

  override def run(program: Program): Program =
    refused.clear()
    done.clear()
    firedScope = Set.empty
    chosen = Map.empty
    firedAt = Map.empty
    selfAt = Map.empty
    if bySym.isEmpty then return program

    // CLASSIFY every call site FIRST, through the DECLARATION the call is in (the xref's nearest
    // enclosing definition), never through the call node — the traversal hooks cannot see it.
    val sel           = collection.mutable.Map.empty[(SymId, Origin), Either[String, (Entry, BoundCall)]]
    val fired         = collection.mutable.Map.empty[(SymId, Origin), String]
    val selves        = collection.mutable.Map.empty[(SymId, Origin), Either[String, SymId]]
    lazy val anonTpts = CallSiteSubstitutionTransform.anonSupertypes(program)
    val byEncl        = collection.mutable.Map.empty[(SymId, Entry), Int]
    bySym.toList.sortBy(_._1.raw).foreach { (callee, cands) =>
      program.usages(callee).foreach { u =>
        u.site match
          case a: Tree.Apply if a.method == callee =>
            val encl = program.symbolOf(u.enclosing)
            val pick: Option[Either[String, (Entry, BoundCall)]] =
              select(program, cands, encl) match
                case Left(why)      => Some(Left(why))
                case Right(None)    => None
                case Right(Some(c)) =>
                  c._1.scope match
                    case sc: RuleScope.Only => encl.flatMap(sc.entryFor(program, _)).foreach(s => fired.update((callee, a.origin), s))
                    case _ => ()
                  Some(Right(c))
            pick.foreach { p =>
              val at = (callee, a.origin)
              sel.get(at) match
                case Some(prev) if prev != p =>
                  sel.update(at, Left("two calls of this callee share one source position and their declarations select different entries"))
                case _ => sel.update(at, p)
              p.foreach { (_, b) =>
                if b.template.usesThis then
                  val self = enclosingSelf(program, u.enclosing, anonTpts)
                  selves.get(at) match
                    case Some(prev) if prev != self => selves.update(at, Left(s"$ThisRefused: two calls share one source position and sit under different instances"))
                    case _                          => selves.update(at, self)
              }
            }
          case _ => ()
      }
    }
    chosen = sel.toMap
    firedAt = fired.toMap
    selfAt = selves.toMap
    // one decision row per (declaration, entry), read from the pre-rewrite program. Held to sites
    // this phase will ACTUALLY rewrite via siteFault, the same predicate the traversal applies,
    // so a row can never claim a substitution that was refused.
    bySym.toList.sortBy(_._1.raw).foreach { (callee, _) =>
      program.usages(callee).foreach { u =>
        u.site match
          case a: Tree.Apply if a.method == callee =>
            chosen.get((callee, a.origin)).foreach {
              case Right((e, b)) if siteFault(a, b, selfFor(a)).isEmpty => byEncl.update((u.enclosing, e), byEncl.getOrElse((u.enclosing, e), 0) + 1)
              case _                                                    => ()
            }
          case _ => ()
      }
    }
    bySym.toList.sortBy((s, cs) => (cs.head._2.key, s.raw)).foreach { (callee, cands) =>
      val key       = cands.head._2.key
      val calleeFqn = program.symbolOf(callee).map(_.fullName).getOrElse(key)
      Decision.declarationsUsing(program, callee).foreach { (encl, origin) =>
        cands.map(_._1).distinct.foreach { e =>
          byEncl.get((encl, e)).foreach { n =>
            record(
              Decision(
                kind = Decision.Kind.SubstitutedCall,
                subject = encl,
                subjectFqn = Decision.fqnOf(program, encl, calleeFqn),
                // key not repeated here: Reason.Configured already carries it.
                detail = Map(
                  "sites" -> n.toString,
                  "to" -> e.template,
                  "why" -> ("the port does not call this member: the call is replaced by " +
                    "ready-made Scala naming the same receiver and arguments, so everything around it " +
                    "keeps its type and only what runs is this port's rather than upstream's")
                ) ++ Option.when(!e.scope.isUnrestricted)("scope" -> e.scope.fingerprint) ++ Option.when(cands.head._2.dropped)(
                  "callee" ->
                    "DROPPED by this port's Substitutions.dropMethods — there is no declaration to return to"
                ),
                reason = Reason.Configured(name, key),
                origin = origin
              )
            )
          }
        }
      }
    }

    given Program = program
    val units     = program.units.map(u => StandardTraversal.mapClassDef(this, u))
    program.rebuilt(units) // xref rebuilt by the Pipeline

  override def transformApply(t: Tree.Apply)(using p: Program): Term =
    bySym.get(t.method) match
      case None        => t
      case Some(cands) =>
        // a site the xref did not see is decided with no enclosing declaration: unscoped entries only
        val pick = chosen.get((t.method, t.origin)).map(Some(_)).getOrElse(select(p, cands, None).fold(w => Some(Left(w)), _.map(Right(_))))
        pick match
          case None                => t
          case Some(Left(why))     => refused += ((cands.head._2.key, why, t.origin)); t
          case Some(Right((e, b))) =>
            val self = selfFor(t)
            siteFault(t, b, self) match
              case Some(why) => refused += ((b.key, why, t.origin)); t
              case None      =>
                done.update(b.key, done.getOrElse(b.key, 0) + 1)
                firedAt.get((t.method, t.origin)).foreach(firedScope += _)
                b.template.splice(receiverOf(t), t.args, t.tpe, t.origin, self.toOption)

  /** the `{this}` term at a call site — a site the xref did not see has no known enclosing declaration, so no instance to name. */
  private def selfFor(t: Tree.Apply): Either[String, Term] =
    selfAt.getOrElse((t.method, t.origin), Left(NoEnclosingSelf)).map(selfTerm(_, t.origin))

  /** `Foo::bar` naming a substituted callee — no argument list to splice a template into. */
  override def transformTerm(t: Term)(using Program): Term =
    t match
      case mr: Tree.MethodRef if bySym.contains(mr.method) =>
        refused += ((bySym(mr.method).head._2.key,
                     "the callee is used as a METHOD VALUE (`::`), which has no argument list for a " +
                       "positional template to name",
                     mr.origin
        ))
        mr
      case other => other

/** the template grammar, and the two structural questions the phase asks of a site. */
object CallSiteSubstitutionTransform:

  /** the `setting` every finding this phase makes is filed under. */
  val Setting = "CallSiteSubstitutionTransform(calls)"

  /** One rewrite: the callee key, its template, and the declarations whose calls it rewrites — unrestricted by default, the behaviour every entry had before it could be scoped.
    */
  final case class Entry(call: String, template: String, scope: RuleScope = RuleScope.everywhere)

  /** The scope strings at which an entry competes: every unscoped entry competes at one shared place, an `Only` entry at each package, type or member it names. */
  def rankKeys(sc: RuleScope): Set[String] = sc match
    case RuleScope.Everywhere(_) => Set("*")
    case RuleScope.Only(inc)     => inc

  /** Pairs of entries for one callee that can meet at one call with DIFFERENT templates and equal specificity, with the scope string they share. */
  def ties(es: List[Entry]): List[(Entry, Entry, String)] =
    val v = es.distinct.toVector
    for
      i <- v.indices.toList
      j <- (i + 1 until v.size).toList
      e = v(i)
      f = v(j)
      if e.call == f.call && e.template != f.template
      at <- (rankKeys(e.scope) & rankKeys(f.scope)).toList.sorted.headOption
    yield (e, f, at)

  /** one installed key: what an individual call site needs, resolved once at bind time. */
  final case class Bound(key: String, template: Template, arity: Int, dropped: Boolean)

  /** the receiver term of a call, seen through an explicit type application (`xs.sort[T](c)` is `Apply(TypeApply(Select(xs, sort), [T]), …)` — matching only `Select` misses it). A bare `Ident` call
    * has no receiver term at all (static, or unqualified on `this`).
    */
  def receiverOf(t: Tree.Apply): Option[Term] = t.fun match
    case Tree.Select(r, _, _, _)                          => Some(r)
    case Tree.TypeApply(Tree.Select(r, _, _, _), _, _, _) => Some(r)
    case _                                                => None

  /** Why this site cannot take the template — `None` when it can. One predicate, applied by both the rewriting traversal and the decision recorder, so a decision can never claim a substitution the
    * rewrite refused.
    */
  def siteFault(t: Tree.Apply, b: Bound, self: Either[String, Term] = Left(NoEnclosingSelf)): Option[String] =
    if t.args.exists(a => Tree.uncomment(a).isInstanceOf[Tree.Repeated]) then Some("the call passes a VARARG SPREAD, which no positional hole can name")
    else if b.template.maxArg.isDefined && t.args.sizeIs != b.arity then
      Some(
        s"this call site carries ${t.args.size} argument term(s) where the callee declares " +
          s"${b.arity}, so the template's positions no longer name what they were checked against"
      )
    else if b.template.usesRecv && receiverOf(t).isEmpty then Some("the template names {recv} and this call has no receiver term (a static or unqualified call)")
    else if b.template.usesThis then self.left.toOption
    else None

  /** the prefix of every reason a `{this}` site is refused with. */
  val ThisRefused = "{this} refused"

  /** why `{this}` is refused at a call site the xref did not place in any declaration. */
  val NoEnclosingSelf: String = s"$ThisRefused: no enclosing member — the call sits in no declaration the cross-reference index recorded, so no instance can be named"

  /** `{this}` as a TREE rather than text, so the emitter qualifies it (`Outer.this`) wherever the site renders inside a nested class body, and a lambda keeps Scala's own lexical `this`. */
  def selfTerm(cls: SymId, at: Origin): Term = Tree.This(cls, TypeRepr.TypeRef(TypeRepr.NoPrefix, cls), at)

  /** The class whose instance `{this}` names at a call inside `encl`: the nearest NAMED class whose instance member lexically encloses the call. A lambda is transparent (Scala's lambda keeps the
    * enclosing `this`) and so is an anonymous class, climbed through its creation site, because its own `this` is not the class a scope entry names. `Left(reason)` wherever that path crosses a STATIC
    * member (no instance exists) or cannot be read.
    */
  def enclosingSelf(program: Program, encl: SymId, anonTpts: => Map[SymId, TypeTree]): Either[String, SymId] =
    def isClass(s: SymId)  = program.definitionOf(s).exists(_.isInstanceOf[Tree.ClassDef])
    def isAnon(s:  SymId)  = program.symbolOf(s).exists(_.name == "<anon>")
    def staticIn(s: SymId) = Left(
      s"$ThisRefused: static member — the call sits in STATIC `${program.symbolOf(s).map(_.fullName).getOrElse("?")}`, which has no instance"
    )
    @annotation.tailrec
    def climb(at: SymId, fuel: Int): Either[String, SymId] =
      if fuel <= 0 || at == SymId.None then Left(NoEnclosingSelf)
      else if isClass(at) then Right(at)
      else if isAnon(at) then
        anonCreationSite(program, at, anonTpts) match
          case Some(site) => climb(site, fuel - 1)
          case None       =>
            Left(
              s"$ThisRefused: anonymous class — the member enclosing the `new` of this anonymous class is unknown, so its enclosing instance cannot be named"
            )
      else
        program.symbolOf(at) match
          case None                        => Left(NoEnclosingSelf)
          case Some(s) if s.flags.isStatic => staticIn(at)
          case Some(s)                     => climb(s.owner, fuel - 1)
    if isClass(encl) then Left(s"$ThisRefused: no enclosing member — the call sits in a class header, where java has no `this` to name")
    else climb(encl, 64)

  /** every anonymous class in `program` → the supertype its `new` names, the key its creation site is indexed under. */
  def anonSupertypes(program: Program): Map[SymId, TypeTree] =
    program.units.flatMap(u => StandardTraversal.allAnonClasses(u)(using program)).map((a, tpt) => a.symbol -> tpt).toMap

  /** the declaration enclosing the `new` that created anonymous class `anon`, read off the cross-reference index's usage of that `new`'s supertype. */
  private def anonCreationSite(program: Program, anon: SymId, anonTpts: Map[SymId, TypeTree]): Option[SymId] =
    def heads(t: TypeRepr): List[SymId] = t match
      case TypeRepr.TypeRef(_, s)      => List(s)
      case TypeRepr.AppliedType(tc, _) => heads(tc)
      case TypeRepr.AndType(l, r)      => heads(l) ++ heads(r)
      case _                           => Nil
    anonTpts.get(anon).flatMap(tpt => heads(tpt.tpe).iterator.flatMap(program.usages).collectFirst { case Usage(_, n: Tree.New, enc) if n.anon.exists(_.symbol == anon) && enc != SymId.None => enc })

  /** ONE hole. */
  enum Hole:
    case Recv
    case This
    case Arg(index: Int)

  /** A parsed expression template — literal parts interleaved with holes. Parsed once at bind time, so a template fault becomes a finding before the pipeline runs. `parts` always has exactly one more
    * element than `holes`.
    */
  final case class Template(parts: List[String], holes: List[Hole]):
    def usesRecv: Boolean = holes.contains(Hole.Recv)

    /** does the template name the instance enclosing the call site? */
    def usesThis: Boolean = holes.contains(Hole.This)

    /** the highest `{argN}` index the template names, or `None` when it names no argument. */
    def maxArg: Option[Int] = holes.collect { case Hole.Arg(i) => i }.maxOption

    /** the template with `recv`, `self` and `args` spliced in — as a [[Tree.Opaque]] carrying the TERMS, never a rendered string (see `Tree.Opaque`). Guarded by [[siteFault]] at every caller.
      */
    def splice(recv: Option[Term], args: List[Term], tpe: TypeRepr, origin: Origin, self: Option[Term] = None): Term =
      val terms = holes.map {
        case Hole.Recv   => recv.get
        case Hole.This   => self.get
        case Hole.Arg(i) => args(i)
      }
      Tree.Opaque.spliced(parts, terms, tpe, origin)

  object Template:

    /** Parse a template, or say precisely what is wrong with it. Tiny grammar, refused outside it rather than carried through as literal text — a lenient parse would emit `{arg0}` as an
      * unattributable compile error. `{recv}` the receiver; `{this}` the instance enclosing the call site; `{arg0}`…`{argN}` positional arguments; `{{`/`}}` a literal brace.
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
          case '}'                                                     => bad = Some(s"an unmatched `}` at index $i — write `}}` for a literal brace")
          case '{'                                                     =>
            val close = text.indexOf('}', i + 1)
            if close < 0 then bad = Some(s"an unclosed `{` at index $i — write `{{` for a literal brace")
            else
              val nm = text.substring(i + 1, close)
              holeOf(nm) match
                case Some(h) => parts += cur.toString; cur.clear(); holes += h; i = close + 1
                case None    =>
                  bad = Some(
                    s"`{$nm}` at index $i is not a hole: write `{recv}` for the receiver, `{this}` for the enclosing instance, " +
                      "`{arg0}`…`{argN}` for the arguments, or `{{` for a literal brace"
                  )
          case c => cur.append(c); i += 1
      bad.toLeft {
        parts += cur.toString
        Template(parts.result(), holes.result())
      }

    private def holeOf(nm: String): Option[Hole] =
      if nm == "recv" then Some(Hole.Recv)
      else if nm == "this" then Some(Hole.This)
      else if nm.startsWith("arg") then nm.drop(3).toIntOption.filter(_ >= 0).map(Hole.Arg.apply)
      else None
