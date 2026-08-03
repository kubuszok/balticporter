package balticporter.transform

import balticporter.core.{PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*

/** Replace a named method's BODY with ready-made Scala, keeping everything else about the class
  * mechanically translated.
  *
  * ==The gap this fills==
  * A port has two existing seams for code it must not translate mechanically, and they are both
  * whole-declaration: `Substitutions.dropTypes` removes a TYPE and `inject` supplies a replacement
  * FILE; `dropMethods` removes a METHOD and supplies nothing. Neither can say *keep this class,
  * replace these two bodies* — so a class that is 200 lines of ordinary code plus one method the
  * target platform cannot express had to be dropped and hand-written in full, and from that moment
  * it no longer tracks upstream.
  *
  * The worked example is Ashley's `Engine.createComponent`, which calls
  * `ClassReflection.newInstance(componentType)` and catches `ReflectionException` — two types the
  * libGDX port drops, because reflective instantiation is the one thing Scala.js and Scala Native
  * cannot do. Everything else in `Engine` ports mechanically. Dropping the type to fix one method
  * would fork 200 lines; dropping the method leaves its callers with nothing.
  *
  * ==Why a PHASE and not a `Substitutions` field==
  * Running as a phase means the replacement lands in the TIR *before* the checks read it. The
  * original body's references to dropped types are gone by the time `PortabilityCheck`,
  * `RewriteTrace` and `SubstitutionCheck` run, so each reports the truth about what will ship. A
  * text substitution applied at emission would leave every check reasoning about a body the port
  * does not emit — the [[balticporter.tir.OmissionCheck]] failure mode in a new place.
  *
  * `Tree.Opaque` is the existing node for "a term the TIR does not model, kept typed so the tree
  * stays whole"; [[PanamaFfiTransform]] already replaces a `native` method's body with one. This
  * phase is that pattern with the source text supplied by policy instead of generated.
  *
  * ==Kind==
  * CLAUDE.md §1(b): the MECHANISM — locate a member by key, swap its body, keep its signature — is
  * a fact about Java and Scala and is the same for every library. WHICH members and WHAT Scala is a
  * fact about one library and arrives as a constructor parameter. An empty map is a no-op.
  *
  * ==What it deliberately does NOT do==
  *   - **It never changes a signature.** The body is replaced; parameters, type parameters and the
  *     return type stay exactly as translated, so every call site still type-checks and
  *     `RewriteTrace`'s signature-consistency check remains meaningful. A port that needs a
  *     different signature wants `dropMethods` plus a replacement type, not this.
  *   - **It refuses CONSTRUCTORS.** `CtorFunnel` makes whole-program decisions about which
  *     constructor becomes the Scala primary and which `super(args)` can be replayed, all derived
  *     from constructor BODIES. Swapping one underneath it would silently invalidate that analysis,
  *     so a `<init>` key is reported as `Malformed` rather than honoured.
  *
  * @param bodies
  *   member key → the Scala to use as that member's body. Keys follow the same convention as
  *   `Substitutions.dropMethods`: `owner#name` matches EVERY overload of that name, and
  *   `owner#name(P1,P2)` — the erased parameter type SIMPLE names — matches exactly one. Prefer the
  *   precise form; the bare form on an overloaded member gives every overload the same body, which
  *   is almost never what is meant and is reported as `Unverifiable`.
  *
  *   The text is spliced at term position, so a multi-statement body must be a block: `{ … }`. It
  *   is emitted verbatim and is NOT type-checked by the engine — the target compiler is the gate,
  *   and CLAUDE.md §6 applies to what you write (fully-qualified names, no imports, `args*` for a
  *   vararg spread).
  */
final class MethodBodyTransform(bodies: Map[String, String] = Map.empty)
    extends Phase, PolicySource, SurfacePolicy, PolicyBound:
  def name: String = "method-body-substitution"

  /** What the RUN resolved each declared key to, before the pipeline started (§8.1) — and the only
    * thing this phase is allowed to learn about which members its keys name.
    *
    * '''`bySym` is where the bare-versus-precise precedence now lives, and it is ORDERED.''' With
    * both `X#m` and `X#m(int)` declared, the precise key must win at the member both name; built
    * from an unordered map it would win or lose by hash order, which is the kind of thing that is
    * right for a year and then is not. Bare first, precise last, both sorted. */
  private var bound: Map[String, Binding[List[PolicyBinder.Hit]]] = Map.empty
  private var bySym: Map[SymId, String] = Map.empty
  private var records: List[PolicyBinder.Record] = Nil

  def bindPolicy(binder: PolicyBinder): Unit =
    bound = bodies.keys.toList.sorted
      .map(k => k -> binder.bindMembers(name, "MethodBodyTransform", k)).toMap
    records = binder.recordsFor(name)
    val (bare, precise) = bound.toList.sortBy(_._1)
      .partition((k, _) => MemberKey.parse(k).toOption.exists(_.isBare))
    bySym = (bare ++ precise).flatMap((k, b) => b.toOption.getOrElse(Nil).flatMap(_.sym).map(_ -> k)).toMap

  /** Two modules that replace different bodies do not disagree about the shared SURFACE — a body is
    * not a signature, and exactly one module emits each type. The keys are fingerprinted anyway:
    * disagreeing about *which* members are hand-supplied is a policy divergence worth surfacing,
    * and the cost of reporting it is one string. The body text is included because a base and a
    * dependent that supply different Scala for one member have certainly made a mistake. */
  def surfaceFingerprint: String =
    bodies.toList.sorted.map((k, v) => s"$k=${v.hashCode.toHexString}").mkString(",")

  private var applied: List[String] = Nil

  /** Declared keys that matched nothing, plus the two shapes this phase refuses.
    *
    * The never-fired half comes from the BINDING and is therefore complete before [[run]]; the two
    * refusals are facts about what the bound members ARE, which is also knowable without running.
    * The private `var report` this replaces could only speak after a run, so a phase list that
    * never reached this phase reported an empty policy — silence that read as "every key fired". */
  def policyReport: PolicyReport =
    PolicyReport.fromBindings(records) ++ PolicyReport(
      bound.toList.sortBy(_._1).flatMap { (k, b) =>
        b match
          case Binding.Bound(_, hits, _) =>
            // A REFUSED key is not an UNMATCHED key. Reported as both, the second reading ("your key
            // is a typo") contradicts the first ("your key names a constructor") and the reader has
            // to work out which is true.
            val ctors = hits.count(_.key.name == "<init>")
            val refuse = Option.when(ctors > 0)(
              PolicyFinding(name, "MethodBodyTransform", k, PolicyIssue.Malformed,
                "a CONSTRUCTOR body cannot be substituted: CtorFunnel derives the class's Scala " +
                  "primary and its replayable `super(args)` from constructor bodies, and swapping " +
                  "one underneath that analysis changes it silently — drop the type and inject a " +
                  "replacement instead"))
            val many = Option.when(hits.size - ctors > 1)(
              PolicyFinding(name, "MethodBodyTransform", k, PolicyIssue.Unverifiable,
                s"matched ${hits.size - ctors} members: the bare `owner#name` form gives EVERY " +
                  "overload the same body. Use the precise `owner#name(P1,P2)` form unless that is " +
                  "genuinely intended"))
            refuse.toList ++ many.toList
          case _ => Nil
      })

  /** Member keys whose body was actually replaced, in a stable order — so a run can state the
    * number rather than leaving it to be inferred. A replaced body also changes that member's
    * digest in `srcmap`/`members.tsv`, which is what makes the change visible in a baseline diff
    * even when no count moves (CLAUDE.md §3: the translation and its check arrive together). */
  def substituted: List[String] = applied.sorted

  override def run(program: Program): Program =
    if bodies.isEmpty then
      applied = Nil
      return program

    val done = collection.mutable.ListBuffer.empty[String]

    def rewrite(cd: Tree.ClassDef): Tree.ClassDef =
      val owner = program.symbolOf(cd.symbol).map(_.fullName).getOrElse("")
      val body = cd.body.map {
        case d: Tree.DefDef =>
          // The member is identified by its SYMBOL, bound before the pipeline ran. What this
          // replaces rebuilt `owner#name(P1,P2)` from the `DefDef`'s own parameter TREES and looked
          // the string up — a second key grammar, in the emitted namespace, that spelled an array
          // `Array` where every manifest spells it `int[]`.
          bySym.get(d.symbol) match
            case None => d
            case Some(k) =>
              val nm = program.symbolOf(d.symbol).map(_.name).getOrElse("")
              // The refusal is REPORTED by `policyReport`, from the binding; here it only declines
              // to rewrite.
              if nm == "<init>" then d
              else
                done += k
                // DECISION PROVENANCE, one row per REPLACED MEMBER. Already declaration-level by
                // construction — this phase's unit of work IS a member — so there is nothing to
                // group. The signature is deliberately absent from `detail`: it did not move (that
                // is the phase's contract), and a call site cannot see from it that the behaviour
                // behind it is not upstream's. This row is the only place that says so.
                record(Decision(
                  kind       = Decision.Kind.SubstitutedBody,
                  subject    = d.symbol,
                  subjectFqn = MemberKey(owner, nm).render,
                  detail     = Map(
                    "key"  -> k,
                    "from" -> "the mechanically translated java body",
                    "to"   -> "hand-written Scala from MethodBodyTransform(bodies)",
                    "why"  -> ("the signature is UNCHANGED and every call site still type-checks; " +
                      "only the behaviour behind it is this port's rather than upstream's"),
                  ),
                  reason = Reason.Configured(name, k),
                  origin = d.origin,
                ))
                d.copy(rhs = Some(Tree.Opaque(bodies(k), d.returnTpt.tpe, d.origin)))
        case c: Tree.ClassDef => rewrite(c)
        case other            => other
      }
      cd.copy(body = body)

    val units = program.units.map(rewrite)
    applied = done.toList
    program.rebuilt(units) // xref rebuilt by the Pipeline
