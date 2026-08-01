package balticporter.transform

import balticporter.core.{MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource}
import balticporter.tir.*


/** Re-point every reference to one type at ANOTHER type, at a different fully-qualified name.
  *
  * ==The gap this fills==
  * A port has three ways to deal with a type it must not translate, and until now all three were
  * only available to the module that OWNS the type:
  *
  *   - `Substitutions.dropTypes` + `inject` — replace it at the SAME FQN;
  *   - `StaticForwarderTransform` — inline a static wrapper into its first argument;
  *   - `ClassTableTransform` — re-point one reflective METHOD at an explicit table.
  *
  * A DEPENDENT module has none of them. When a base drops a type outright — no replacement, because
  * the base itself had no remaining use for it — a dependent that DOES use it is stuck: it cannot
  * inject at the base's FQN (exactly one module ships each replacement, or the two definitions
  * collide on the Scala.js and Native linkers), and it cannot un-drop it.
  *
  * The worked example is Ashley's `PooledEngine.ComponentPools`, whose `ReflectionPool` uses are
  * all TYPE occurrences — a field's type, a local's type, a `new`, and several cast targets — so
  * [[MethodBodyTransform]] cannot reach them; a body seam cannot change a field's type. libGDX drops
  * `ReflectionPool` because reflective pooling is the one thing Scala.js and Native cannot do, and
  * every remaining libGDX use went with it. Ashley's did not.
  *
  * ==What the dependent must supply==
  * A type of its own, at its own FQN, SHAPE-COMPATIBLE with the one being replaced: the same arity,
  * a constructor with the same parameters, and the members the referring code calls. The engine does
  * not and cannot verify that — the target is ordinary Scala the port ships, and the target compiler
  * is the gate. What the engine does guarantee is that every reference moves together, so a partial
  * redirect is impossible.
  *
  * ==Kind==
  * CLAUDE.md §1(b). The MECHANISM — rewrite every occurrence of one type symbol to another — is a
  * fact about namespacing. WHICH type becomes WHICH is a fact about one library and arrives as a
  * constructor parameter. An empty map is a no-op.
  *
  * ==Why this is safe where a rename is not==
  * [[PackageRenameTransform]] rewrites a symbol the program OWNS and must therefore run last, after
  * every phase whose policy is written in the upstream namespace. This does the opposite: it
  * re-points references at a type the program does NOT own, leaving the original symbol untouched
  * and still resolvable. It is therefore ordering-insensitive, and a phase that runs after it sees
  * the redirected type — which is what a later portability or substitution check needs to see.
  *
  * ==`memberRenames` — when the target calls the member something else==
  * A shape-compatible target is not always a NAME-compatible one: `Disposable#dispose()` and
  * `java.lang.AutoCloseable#close()` are the same member under two names, and a redirect alone
  * emits a class that claims to be an `AutoCloseable` and does not implement it. So an entry may
  * carry `memberRenames`, member simple name → the target's name for it, and the phase renames
  * every declaration of that member's override component before re-pointing anything.
  *
  * '''The rename runs against the PRE-REDIRECT override graph, INSIDE this one phase, and that is
  * not a scheduling convenience.''' Post-redirect every implementor's parent edge points at the
  * external target, so [[OverrideGraph]] has no owned ancestor joining them and the N-declaration
  * component splits into singletons: [[MemberRenamer]]'s whole-or-none guarantee then guarantees
  * nothing, and a single anchored declaration no longer refuses the rest — half a hierarchy is
  * renamed, and the refusal that should have stopped all of it reports success. How that half
  * program then fails is not something the engine gets to choose: measured on this phase's own
  * fixture it is two scalac errors, because `AutoCloseable#close` is abstract; where the target's
  * member is concrete, or where the split leaves an interface nothing implements, the same
  * half-rename compiles and no count moves for it. Two phases in series cannot express any of this;
  * one phase that builds the graph, renames, and only then redirects can.
  * (`TypeRedirectMemberRenameSpec` constructs the wrong order and measures the split.)
  *
  * '''And the new name must exist on the TARGET.''' Renaming `dispose` to something
  * `java.lang.AutoCloseable` does not declare emits a program that calls a method the target does
  * not have — a compile error three lanes downstream, in the consumer's repository. Where the
  * target's surface is KNOWN ([[ExternalSurface]] — the JDK platform types whose member sets the
  * JDK itself closes) the rename is checked against it and refused, counted, with why. Where it is
  * not known — the ordinary case, a shape-compatible type the port ships itself — the check cannot
  * run and the target compiler stays the gate, exactly as it is for the redirect's shape.
  */
final class TypeRedirectTransform(
    val redirects: Map[String, String] = Map.empty,
    val memberRenames: Map[String, Map[String, String]] = Map.empty,
    val external: ExternalSurface = ExternalSurface.default,
) extends Phase, PolicySource, MergeablePolicy, PolicyBound:
  def name: String = "type-redirect"

  /** What the RUN resolved each declared SOURCE type to, before the pipeline started (§8.1).
    *
    * '''`Ownership.Either`, and this is the phase that defines why that parameter exists.''' Every
    * OTHER keyed seam wants `Owned`: an entry naming a type the program merely REFERENCES matches
    * the interned external, the phase rewrites no declaration, and the entry counts as having fired
    * — the §1(b) silent no-op. This phase is the exception BY CONSTRUCTION. Its whole subject is a
    * type this module does not declare and cannot declare (the base dropped it, and exactly one
    * module may ship a replacement at a given FQN); what it rewrites is not a declaration but every
    * REFERENCE to one. Bound as `Owned` it reported ten perfectly good redirects as never-matched
    * on the one port that uses it, which the `policy-binding` check measured (`screens`,
    * `policy 0 -> 10`) before anything depended on the answer. */
  private var bound: Map[String, Binding[SymId]] = Map.empty
  private var records: List[PolicyBinder.Record] = Nil

  /** the member renames, PARSED and BOUND — one row per declaration a rename entry named. Bound
    * `Owned`, unlike the type above: a rename rewrites a DECLARATION, and a key naming only an
    * interned external has nothing to rewrite (`ExternalOnly` says exactly that). */
  private var boundRenames: List[TypeRedirectTransform.Rename] = Nil

  /** this phase's OWN findings — a malformed member segment, a `memberRenames` block for a type
    * that is not redirected, and every counted refusal the run makes. */
  private var ownFindings: List[PolicyFinding] = Nil

  def bindPolicy(binder: PolicyBinder): Unit =
    bound = redirects.keys.toList.sorted
      .map(k => k -> binder.bindType(name, "TypeRedirectTransform(redirects) source", k, Ownership.Either))
      .toMap
    val bad = collection.mutable.ListBuffer.empty[PolicyFinding]
    boundRenames = memberRenames.toList.sortBy(_._1).flatMap { (from, renames) =>
      redirects.get(from) match
        // A `memberRenames` block for a type nothing redirects is not a rename this phase could
        // ever perform: the whole point of the rename is that the TARGET spells the member
        // differently, and with no target there is no other spelling. Config cannot express it
        // (the block is nested inside its entry); a Scala embedder can, and it must not read as a
        // rename that silently did nothing.
        case scala.None =>
          bad += PolicyFinding(name, "TypeRedirectTransform(memberRenames)", from, PolicyIssue.Malformed,
            "`memberRenames` names a type this phase does not redirect — a member rename exists " +
              "because the REDIRECT TARGET spells the member differently, so with no `redirects` " +
              "entry for this type there is nothing it could be renamed towards")
          Nil
        case Some(to) => renames.toList.sortBy(_._1).flatMap { (member, newName) =>
          MemberKey.parseIn(from, member) match
            case Left(m) =>
              bad += PolicyFinding(name, s"TypeRedirectTransform(memberRenames) of `$from`", member,
                PolicyIssue.Malformed, m.what)
              Nil
            case Right(_) if newName.isEmpty =>
              bad += PolicyFinding(name, s"TypeRedirectTransform(memberRenames) of `$from`", member,
                PolicyIssue.Malformed, "the new name is empty, which names nothing")
              Nil
            case Right(mk) =>
              val entry = mk.render
              // `flatMap(_.sym)` and not `map`: a hit with no symbol is a member the port DROPPED,
              // which the binder counts as having fired and which there is nothing left to rename.
              // That is the honest answer — a drop is a decision the port already made about this
              // member — and it is why an empty `hits` below is a no-op rather than a finding.
              val hits  = binder.bindMembers(name, s"TypeRedirectTransform(memberRenames) of `$from`", entry)
                .toOption.getOrElse(Nil).flatMap(_.sym)
              List(TypeRedirectTransform.Rename(from, to, entry, newName, hits))
        }
    }
    ownFindings = bad.toList
    records = binder.recordsFor(name)

  /** Re-pointing a type CHANGES EMITTED SIGNATURES — a field, a parameter and a return type all
    * move — so it is part of the shared surface and two modules must not disagree about it. A
    * member rename moves the signature twice over, so it is rendered here too; an entry with no
    * renames spells exactly what it always did, so two modules that predate this feature still
    * compare equal. */
  def surfaceFingerprint: String = redirects.toList.sorted.map { (f, t) =>
    memberRenames.getOrElse(f, Map.empty).toList.sorted match
      case Nil => s"$f->$t"
      case rs  => s"$f->$t[" + rs.map((m, n) => s"$m=$n").mkString(",") + "]"
  }.mkString(",")

  /** THE MERGE CONTRACT (DESIGN.md §8.13). Both tables are keyed by INDEPENDENT upstream FQNs, so a
    * key only one side holds is an addition, a key both hold with the same value is agreement, and a
    * key both hold with DIFFERENT values is the refusal — two answers for one type is a rewrite
    * whose outcome depends on which manifest was read.
    *
    * `external` is UNIONED and is not part of the refusal, because it is not policy: it is what this
    * engine happens to know about a platform type's members, and two ports that know different
    * amounts about `java.lang.AutoCloseable` still emit the same signatures. It is not in
    * [[surfaceFingerprint]] for the same reason.
    *
    * `added` is the SUBJECT side of what the later instance contributes — redirect sources it adds,
    * plus the owner of any member rename it adds. Those are the names a dependent could use to
    * re-shape a base's emitted surface, which is what `SurfaceFold` screens against `governs`.
    */
  /** every type this instance's policy is KEYED on — a redirect source, and the owner of a member
    * rename. The same set `mergedWith` reports as `added` when the whole table is new, which is not
    * a coincidence: the screen asks the identical question of a merged instance and of one that had
    * nothing to merge with. */
  def subjects: Set[String] =
    (redirects.keySet ++ memberRenames.keySet).map(MergeablePolicy.subjectOf)

  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case o: TypeRedirectTransform =>
      val typeClash = (redirects.keySet & o.redirects.keySet).filter(k => redirects(k) != o.redirects(k))
      // reported as OWNER and SEGMENT rather than as a rebuilt `owner#member` string: the member-key
      // grammar has exactly one renderer (§8.1) and a refusal message is no reason for a second.
      val memberClash = for
        (t, rs)   <- o.memberRenames.toList
        (mem, to) <- rs
        if memberRenames.getOrElse(t, Map.empty).get(mem).exists(_ != to)
      yield (t, mem)
      if typeClash.nonEmpty || memberClash.nonEmpty then
        Left(
          (typeClash.toList.sorted.map(k =>
             s"""both modules redirect "$k", to "${redirects(k)}" and "${o.redirects(k)}"""") ++
            memberClash.sorted.map((t, mem) =>
              s"""both modules rename the member `$mem` of "$t", to two different names"""))
            .mkString("; ") +
            " — two answers for one key is a rewrite whose outcome depends on which manifest was read")
      else
        val renames = o.memberRenames.foldLeft(memberRenames) { (acc, e) =>
          acc.updated(e._1, acc.getOrElse(e._1, Map.empty) ++ e._2)
        }
        val addedRenameOwners = o.memberRenames.collect {
          case (t, rs) if rs.exists((m, n) => !memberRenames.getOrElse(t, Map.empty).get(m).contains(n)) => t
        }.toSet
        Right(MergeablePolicy.Merged(
          new TypeRedirectTransform(
            redirects     = redirects ++ o.redirects,
            memberRenames = renames,
            external      = ExternalSurface(external.known ++ o.external.known)),
          (o.redirects.keySet -- redirects.keySet) ++ addedRenameOwners))
    case other =>
      Left(s"`${other.name}` is not a `TypeRedirectTransform`, so there is no table to compose")

  private var mapping: Map[SymId, SymId]     = Map.empty
  private var memberTwins: Map[SymId, SymId] = Map.empty

  /** refusals this RUN made — reset at the head of every run, because a phase instance is reused
    * across two translations (`Determinism.Full`) and the first run's refusals are not the
    * second's. */
  private var runFindings: List[PolicyFinding] = Nil

  /** Declared sources that occur nowhere — the redirect silently did not happen and the dependency
    * the port was configured to remove is still there. The symmetric failure to every other (b)
    * seam's, and the reason [[PolicyReport]] exists. Derived from the BINDING, so it is complete
    * before the pipeline runs and says the same thing whether or not this phase ran — plus this
    * phase's own malformed entries and counted rename refusals, which the binding cannot see. */
  def policyReport: PolicyReport =
    PolicyReport.fromBindings(records) ++ PolicyReport(ownFindings ++ runFindings)

  override def run(program0: Program): Program =
    runFindings = Nil
    if redirects.isEmpty then
      mapping = Map.empty; memberTwins = Map.empty
      return program0

    // THE RENAME FIRST, against the graph of the program as JAVA declared it — see this class's
    // note on why the two cannot be two phases.
    val program = renameMembers(program0)

    val byName = program.symbols.all.iterator.map(s => s.fullName -> s).toMap
    var table  = program.symbols
    var next   = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1

    // Reuse the target symbol when the program already refers to it, else mint one. Minting is what
    // makes a redirect to a type NOT otherwise mentioned work at all — the dependent's injected
    // replacement is never parsed, so nothing has interned it.
    //
    // `NoType` and `SymId.None` are not placeholders here: together with a dotted, `#`-free name
    // they are exactly the shape the emitter reads as "an external TYPE", which is what lets the
    // minted symbol stand in a static-access receiver position. Giving it a self `TypeRef` — the
    // `info` a DECLARED class carries — reads as a term instead and emits an unqualified name.
    def targetOf(fqn: String): SymId = byName.get(fqn).map(_.id).getOrElse {
      val id = SymId(next); next += 1
      table = table.updated(Symbol(id, fqn.split('.').last, fqn, Flags(), SymId.None, TypeRepr.NoType))
      id
    }

    // The SOURCE comes from the binding; only the TARGET is looked up by name, and it has to be —
    // it is a type this program may not mention at all, which is why `targetOf` mints one.
    mapping = redirects.toList.sortBy(_._1).flatMap { (from, to) =>
      bound.get(from).flatMap(_.toOption).map(_ -> targetOf(to))
    }.toMap

    // THE MEMBERS MOVE WITH THE TYPE, and there are TWO ways a member reference is rendered.
    //
    //   - a redirected type the frontend NEVER PARSED (a jar on the classpath) reaches its statics
    //     through an explicit `Select(Ident(type), member)`, and `transformIdent` below moves that;
    //   - a redirected type the frontend DID parse — a resolution root, which is the ordinary case
    //     for a dependent port — is re-qualified BY THE EMITTER from the member symbol's OWNER
    //     (`TirEmitter.staticThroughInstance`), which deliberately ignores the qualifier in the
    //     tree. Remapping the qualifier alone is therefore undone one layer later, silently.
    //
    // The second is answered by a TWIN: a new symbol with the same name and signature whose OWNER is
    // the target, with the references re-pointed at it. `staticThroughInstance` then finds the
    // qualifier and the owner in agreement and leaves the reference alone.
    //
    // A twin, and NOT a re-pointing of the original symbol's owner — which is the same fix in one
    // fewer line and was measured worse. A redirected type is normally one this module RESOLVES
    // AGAINST, so its members are the base's declarations, and moving their owner detaches them from
    // the unit they belong to: the ownership climb (CLAUDE.md §4.56) no longer reaches a
    // `program.units` symbol, and every per-site check that filters "the base's declarations, not
    // mine" (ENGINE-LIMITS D2) stops recognising them. Measured on a dependent that redirects one
    // parsed type: `port-map` 0 -> 6, all six inside the redirected type's OWN Java file, in a run
    // whose emitted text was byte-identical. The originals are left exactly as the base declared
    // them.
    memberTwins = mapping.flatMap { (fromType, toType) =>
      // Read BOTH names out of `table`, never out of `program`: a MINTED target is in the former
      // only, and read from the latter it is the empty string — which silently leaves every twin
      // named after the type the redirect just removed.
      val fromFqn = table.get(fromType).map(_.fullName).getOrElse("")
      val toFqn   = table.get(toType).map(_.fullName).getOrElse("")
      // STATIC members only. An instance member is reached through a receiver whose TYPE this phase
      // has already moved, so it needs nothing; twinning it would be surface with no reference.
      program.symbols.all.filter(m => m.owner == fromType && m.flags.isStatic).toList.map { m =>
        val id = SymId(next); next += 1
        table = table.updated(m.copy(
          id       = id,
          owner    = toType,
          // the owner's prefix replaced, carrying the `#`/`$` separators across verbatim
          // (CLAUDE.md §4.56) rather than re-deriving them
          fullName = if m.fullName.startsWith(fromFqn) then toFqn + m.fullName.drop(fromFqn.length)
                     else m.fullName,
        ))
        m.id -> id
      }
    }

    val membersOf: Map[SymId, List[SymId]] =
      mapping.map((fromType, _) => fromType -> program.symbols.all.filter(_.owner == fromType).map(_.id).toList)

    // DECISION PROVENANCE, one row per (DECLARATION, redirect entry). `RetypedSignature` and not
    // `RedirectedCall`: what moves is a TYPE occurrence — a field's type, a parameter's, a `new`,
    // a cast target — so the thing an agent sees changed in the emitted file is the declaration's
    // signature, and no call was re-pointed at all. Read from the PRE-rewrite program, which is
    // the only one where a usage still names the type this phase is redirecting AWAY from.
    //
    // A declaration that only CALLS a static of the redirected type uses the member symbol and
    // never the type's own, so the type's usages alone under-report it — the members are folded in
    // here for the same reason they are re-pointed above.
    redirects.toList.sorted.foreach { (from, to) =>
      bound.get(from).flatMap(_.toOption).foreach { fromId =>
        val sites = (fromId :: membersOf.getOrElse(fromId, Nil)).flatMap(Decision.declarationsUsing(program, _))
          .groupBy(_._1).toList
          .flatMap((encl, os) => os.map(_._2).minByOption(o => (o.javaPath, o.line)).map(encl -> _))
          .sortBy((encl, o) => (o.javaPath, o.line, encl.raw))
        sites.foreach { (encl, origin) =>
          record(Decision(
            kind       = Decision.Kind.RetypedSignature,
            subject    = encl,
            subjectFqn = Decision.fqnOf(program, encl, from),
            detail     = Map(
              "from" -> from,
              "to"   -> to,
              "key"  -> from,
              "why"  -> ("a module this port depends on drops this type outright, and exactly one " +
                "module may ship a replacement at a given FQN — so every reference is re-pointed " +
                "at a shape-compatible type this port declares itself"),
            ),
            reason = Reason.Configured(name, s"$from -> $to"),
            origin = origin,
          ))
        }
      }
    }

    if mapping.isEmpty then
      program.rebuilt(symbols = table)
    else
      given Program = program.rebuilt(symbols = table)
      // The STANDARD traversal, so every type occurrence the tree has — parents, self-types, tpts,
      // type arguments, `new`, ascriptions — and every symbol `info` is routed through
      // `transformType`. A private walk here would reach the ones it remembered and miss whichever
      // node kind is added next (CLAUDE.md §3).
      val units   = program.units.map(u => StandardTraversal.mapClassDef(this, u))
      val symbols = StandardTraversal.mapSymbols(this, table)
      program.rebuilt(units, symbols) // xref rebuilt by the Pipeline

  // -------------------------------------------------------------------------------------------
  // the member renames — everything below runs BEFORE a single reference has moved
  // -------------------------------------------------------------------------------------------

  /** Rename every declaration of each named member's override COMPONENT to what the target calls
    * it, then hand the rewritten program on to the redirect.
    *
    * Three screens, in this order, and each one is a counted refusal rather than a silent skip:
    *
    *   1. the TARGET must declare the new name, where its surface is known at all;
    *   2. the component must be one this program may move — [[OverrideGraph.Closure]]'s anchors,
    *      which is [[MemberRenamer]]'s own screen and not repeated here;
    *   3. the new name must be free in the component's classes. [[MemberRenamer.OnCollision.Refuse]]
    *      and not `DeferToEmitter`: the requested name is the TARGET's name for the member, so a
    *      name landing one `$` along is not a name the target declares, which is the whole thing
    *      screen 1 exists to prevent. A class that implements the redirected type AND already has a
    *      method of the target's name is a genuine conflict for the port to resolve.
    */
  private def renameMembers(program: Program): Program =
    if boundRenames.isEmpty then program
    else
      // NO `baseUnits`, and that is the dependent case working rather than an omission. A dependent
      // module's `Program` contains its base (`ENGINE-LIMITS.md` D2) and does not EMIT it, so
      // renaming the base's declaration in this run's symbol table defines nothing twice — and it
      // is exactly what makes the dependent's own overrides come out under the name the base
      // already emitted. Base-anchoring here would refuse every dependent and emit an `override`
      // of a member that no longer exists. What keeps the two modules honest is `SurfacePolicy`:
      // the rename is part of the fingerprint, so a base and a dependent that disagree are a §1.5
      // finding before either compiles.
      val graph = OverrideGraph.build(program, external)
      val screened        = boundRenames.map(r => r -> targetRefusal(graph, r))
      val (refused, live) = (screened.collect { case (r, Some(why)) => (r, why) },
                             screened.collect { case (r, scala.None) => r })
      refused.foreach((r, why) => refuseRename(r, why))
      val requests = live.flatMap(r => r.hits.map(h =>
        MemberRenamer.Request(h, r.newName, Reason.Configured(name, r.key), r.key, r.key)))
      if requests.isEmpty then program
      else
        val (out, refusals) = MemberRenamer.rename(
          program, graph, requests, MemberRenamer.OnCollision.Refuse, decisions)
        refusals.map(_.request.key).distinct.foreach { k =>
          val why = refusals.find(_.request.key == k).map(_.why).getOrElse("refused")
          live.find(_.key == k).foreach(r => refuseRename(r, why))
        }
        out

  /** Does the redirect TARGET declare the name this entry asks for? `None` when it does, or when
    * nothing is known about the target — an unknown surface cannot refuse anything, and the
    * direction of THAT error is the one the phase already lives with for shape (the target compiler
    * is the gate). The signature is the member's own, with the new name substituted, so an arity
    * mismatch is caught too where the surface carries one. */
  private def targetRefusal(graph: OverrideGraph, r: TypeRedirectTransform.Rename): Option[String] =
    if r.hits.isEmpty || !external.isKnown(r.target) then scala.None
    else
      val declared = external.known.getOrElse(r.target, Set.empty)
      val ok = r.hits.forall { h =>
        graph.signatureOf(h) match
          case Some(sig) => declared.exists(_.matches(sig.copy(name = r.newName)))
          case scala.None => declared.exists(_.name == r.newName)
      }
      val has = declared.toList.map(_.name).distinct.sorted match
        case Nil => "declares nothing at all"
        case ns  => s"declares ${ns.mkString(", ")}"
      Option.when(!ok)(
        s"`${r.target}` does not declare `${r.newName}` with this member's shape — its surface is " +
          s"known to this engine and it $has. A rename to a name the target does not have emits " +
          "code that calls a method which does not exist, which is a compile error in the " +
          "consumer's repository and not here")

  /** one counted refusal: a `PolicyReport` row for the never-fired half, and a `ScopedOut` decision
    * for the provenance half. `ScopedOut` and not a new enum case, for DESIGN.md §8.5's reason — a
    * declaration that kept its upstream form while the code around it moved IS a scope-out, and the
    * enum is closed on purpose. */
  private def refuseRename(r: TypeRedirectTransform.Rename, why: String): Unit =
    runFindings = runFindings :+ PolicyFinding(
      name, s"TypeRedirectTransform(memberRenames) of `${r.source}`", r.key, PolicyIssue.Unverifiable, why)
    record(Decision(
      kind       = Decision.Kind.ScopedOut,
      subject    = SymId.None,
      subjectFqn = r.entry,
      detail     = Map(
        "refused" -> "member-rename",
        "to"      -> r.newName,
        "target"  -> r.target,
        "why"     -> why,
      ),
      reason = Reason.Configured(name, r.key),
      origin = Origin.synthetic,
    ))

  override def transformType(t: TypeRepr)(using Program): TypeRepr = t match
    case TypeRepr.TypeRef(p, s) if mapping.contains(s) => TypeRepr.TypeRef(p, mapping(s))
    case other                                         => other

  /** A TYPE also occurs as a TERM: the receiver of a static access is an `Ident` of the type's own
    * symbol (`Preconditions.checkArgument(x)`, `SlidingDirection.UP`), and the emitter renders it
    * from that SYMBOL, not from the node's `TypeRepr`. `transformType` therefore cannot see it, and
    * a redirect that stopped there left the static half of the surface naming the very type the
    * port was configured not to have — a PARTIAL redirect, which this phase's contract says is
    * impossible. It went unmeasured until a library redirected a type with STATICS: the first one
    * to use the phase had none, and no count moves for it (the emitted file is uncompilable at
    * exactly the sites the redirect was supposed to fix).
    *
    * Only TYPE symbols are in `mapping`, so a term of the same name cannot be caught by accident.
    * The MEMBER symbol keeps its identity — a static of the replacement is reached through the
    * replacement, which is precisely what "shape-compatible" above requires of it. */
  override def transformIdent(t: Tree.Ident)(using Program): Term =
    if mapping.contains(t.sym) then t.copy(sym = mapping(t.sym))
    else if memberTwins.contains(t.sym) then t.copy(sym = memberTwins(t.sym))
    else t

  /** …and the MEMBER half of it. Re-pointing the qualifier alone is not enough for a redirected type
    * the frontend PARSED: the emitter re-derives a static access from the member symbol's OWNER
    * (`TirEmitter.staticThroughInstance`) precisely so that Java's legal `instance.staticMethod()`
    * emits as Scala's required `Type.staticMethod()`, and that re-derivation undoes the qualifier
    * rewrite one layer later, silently. Pointing at the twin — same name, same signature, owned by
    * the target — puts qualifier and owner back in agreement. */
  override def transformSelect(t: Tree.Select)(using Program): Term =
    if memberTwins.contains(t.sym) then t.copy(sym = memberTwins(t.sym)) else t

  override def transformApply(t: Tree.Apply)(using Program): Term =
    if memberTwins.contains(t.method) then t.copy(method = memberTwins(t.method)) else t

object TypeRedirectTransform:

  /** One declared member rename, parsed and bound.
    *
    * @param source the redirected type, as the policy spells it
    * @param target what it is redirected TO — the type whose name for the member is being adopted
    * @param entry  the bound member key (`owner#name`, or one overload), rendered from a `MemberKey`
    * @param newName the target's name for it
    * @param hits   every declaration the key named. The override CLOSURE of each is what actually
    *               moves; this is only what the key itself pointed at.
    */
  final case class Rename(source: String, target: String, entry: String, newName: String,
                          hits: List[SymId]):
    /** the string an agent edits, in one piece — the `Reason.Configured` key and the id a refusal
      * is reported under (§4.575). */
    def key: String = s"$entry -> $newName"
