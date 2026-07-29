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
final class MethodBodyTransform(bodies: Map[String, String] = Map.empty) extends Phase, PolicySource, SurfacePolicy:
  def name: String = "method-body-substitution"

  /** Two modules that replace different bodies do not disagree about the shared SURFACE — a body is
    * not a signature, and exactly one module emits each type. The keys are fingerprinted anyway:
    * disagreeing about *which* members are hand-supplied is a policy divergence worth surfacing,
    * and the cost of reporting it is one string. The body text is included because a base and a
    * dependent that supply different Scala for one member have certainly made a mistake. */
  def surfaceFingerprint: String =
    bodies.toList.sorted.map((k, v) => s"$k=${v.hashCode.toHexString}").mkString(",")

  private var report: PolicyReport = PolicyReport.empty
  private var applied: List[String] = Nil

  /** Declared keys that matched nothing, plus the two shapes this phase refuses. Reflects the last
    * [[run]]; empty before the first, and empty for an empty policy. */
  def policyReport: PolicyReport = report

  /** Member keys whose body was actually replaced, in a stable order — so a run can state the
    * number rather than leaving it to be inferred. A replaced body also changes that member's
    * digest in `srcmap`/`members.tsv`, which is what makes the change visible in a baseline diff
    * even when no count moves (CLAUDE.md §3: the translation and its check arrive together). */
  def substituted: List[String] = applied.sorted

  /** `owner#name` and `owner#name(P1,P2)` for a member — the `Substitutions.dropMethods`
    * convention, so one library states a member the same way wherever it states it. The parameter
    * names are the erased type SIMPLE names as the frontend recorded them, primitives included
    * (`int`, not `Int`), which is what libGDX's own manifest already writes in
    * `Array#<init>(boolean,int,Class)`. Pinned by `MethodBodyTransformSpec`. */
  private def keysOf(program: Program, owner: String, d: Tree.DefDef): (String, String) =
    val ps = d.paramss.headOption.getOrElse(Nil).map { p =>
      val n = program.symbolOf(p.tpt.tpe match {
        case TypeRepr.TypeRef(_, s)      => s
        case TypeRepr.AppliedType(TypeRepr.TypeRef(_, s), _) => s
        case _                           => SymId.None
      }).map(_.name).getOrElse("?")
      n
    }
    val nm = program.symbolOf(d.symbol).map(_.name).getOrElse("?")
    (s"$owner#$nm", s"$owner#$nm(${ps.mkString(",")})")

  override def run(program: Program): Program =
    if bodies.isEmpty then
      report = PolicyReport.empty; applied = Nil
      return program

    val fired    = collection.mutable.Set.empty[String]
    val findings = collection.mutable.ListBuffer.empty[PolicyFinding]
    val hitCount = collection.mutable.Map.empty[String, Int].withDefaultValue(0)
    val done     = collection.mutable.ListBuffer.empty[String]

    def rewrite(cd: Tree.ClassDef): Tree.ClassDef =
      val owner = program.symbolOf(cd.symbol).map(_.fullName).getOrElse("")
      val body = cd.body.map {
        case d: Tree.DefDef =>
          val (bare, precise) = keysOf(program, owner, d)
          val key = if bodies.contains(precise) then Some(precise) else Option.when(bodies.contains(bare))(bare)
          key match
            case None => d
            case Some(k) =>
              val nm = program.symbolOf(d.symbol).map(_.name).getOrElse("")
              if nm == "<init>" then
                // A REFUSED key is not an UNMATCHED key. Without this the same key is reported
                // twice, as Malformed and again as NeverMatched, and the second reading ("your key
                // is a typo") contradicts the first ("your key names a constructor") — the reader
                // then has to work out which is true.
                fired += k
                findings += PolicyFinding(name, "MethodBodyTransform", k, PolicyIssue.Malformed,
                  "a CONSTRUCTOR body cannot be substituted: CtorFunnel derives the class's Scala " +
                    "primary and its replayable `super(args)` from constructor bodies, and swapping " +
                    "one underneath that analysis changes it silently — drop the type and inject a " +
                    "replacement instead")
                d
              else
                fired += k
                hitCount(k) += 1
                done += k
                d.copy(rhs = Some(Tree.Opaque(bodies(k), d.returnTpt.tpe, d.origin)))
        case c: Tree.ClassDef => rewrite(c)
        case other            => other
      }
      cd.copy(body = body)

    val units = program.units.map(rewrite)

    (bodies.keySet -- fired).toList.sorted.foreach { k =>
      findings += PolicyFinding(name, "MethodBodyTransform", k, PolicyIssue.NeverMatched,
        "no member of that name (and parameter list, if given) occurs in this program, so the " +
          "supplied Scala was never used and the mechanically translated body still stands")
    }
    hitCount.toList.filter(_._2 > 1).sortBy(_._1).foreach { (k, n) =>
      findings += PolicyFinding(name, "MethodBodyTransform", k, PolicyIssue.Unverifiable,
        s"matched $n members: the bare `owner#name` form gives EVERY overload the same body. " +
          "Use the precise `owner#name(P1,P2)` form unless that is genuinely intended")
    }

    report  = PolicyReport(findings.toList)
    applied = done.toList
    new Program(units, program.symbols, program.xref) // xref rebuilt by the Pipeline
