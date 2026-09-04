package balticporter.transform

import balticporter.tir.*

/** BOUNDARY/seam recording (seam findings, class-file-override and bridge synthesis, uninheritable-parent restoration) split out of CollectionsTransform (context diet S3). */
private[transform] trait CollectionsBoundary:
  self: CollectionsTransform =>
  import CollectionsTransform.Kind

  /** [[CollectionClosureCheck]] over this phase's own mapping. */
  def closure(program: Program): List[CollectionClosureCheck.Finding] =
    closure(program, program.units)

  /** Closure check scoped to emitted units only. // ENGINE-LIMITS D2 */
  def closure(program: Program, units: List[Tree.ClassDef]): List[CollectionClosureCheck.Finding] =
    CollectionClosureCheck.check(program, units, mappedTypes, targetOf)

  /** [[CollectionBoundaryCheck]] — run AFTER the phase. */
  def boundary(program: Program): List[CollectionBoundaryCheck.Finding] =
    boundary(program, program.units)

  /** Boundary check scoped to emitted units. [[scopedOut]] classifies scope-created seams. */
  def boundary(program: Program, units: List[Tree.ClassDef]): List[CollectionBoundaryCheck.Finding] =
    CollectionBoundaryCheck.check(program, units, mappedTypes, retypedTargets, scopedOut,
                                  classFileOverrides) ++
      // external seams recorded during traversal, filtered to emitted units (D2)
      externalSeams.toList.filter(f => emittedPaths(units).contains(f.origin.javaPath)) ++
      // opaque egress review list (K21) — one row per external callee, deduped per (callee, java file), D2-filtered
      opaqueEgressSites.toList
        .filter((k, _) => emittedPaths(units).contains(k._2))
        .groupBy((k, _) => k._1)
        .toList
        .map((m, rows) => m -> rows.map(_._2).minBy(o => (o.javaPath, o.line)))
        .sortBy((m, o) => (o.javaPath, o.line, m.raw))
        .map((m, o) => CollectionBoundaryCheck.Finding(
          CollectionBoundaryCheck.Issue.OpaqueEgress,
          s"argument (external callee, java.lang.Object formal): ${calleeLabel(m)(using program)}",
          "java's own representation, IF this callee reads it",
          "a value this port may have retyped", o, m))

  /** External callee label: `<owner FQN>#<member>`. */
  private[transform] def calleeLabel(m: SymId)(using p: Program): String =
    memberKeyOf(m).getOrElse("?")

  /** `<owner FQN>#<member>` for a callee. */
  private[transform] def memberKeyOf(m: SymId)(using p: Program): Option[String] =
    p.symbolOf(m).flatMap(c => p.symbolOf(c.owner).map(o => MemberKey(o.fullName, c.name).render))

  /** Java source paths of emitted units — the D2 filter. */
  private[transform] def emittedPaths(units: List[Tree.ClassDef]): Set[String] =
    units.map(_.origin.javaPath).toSet

  /** [[CollectionInternalCheck]] — in-program half of the boundary residue. Run AFTER the phase. */
  def internal(program: Program): List[CollectionInternalCheck.Finding] =
    internal(program, program.units)

  /** Internal check scoped to emitted units. */
  def internal(program: Program, units: List[Tree.ClassDef]): List[CollectionInternalCheck.Finding] =
    CollectionInternalCheck.check(program, units, mappedTypes, targetOf,
                                  CollectionsTransform.standaloneTargets)

  /** [[RetargetBoundaryCheck]] — producer direction. Run AFTER the phase. */
  def retargetBoundary(program: Program): List[RetargetBoundaryCheck.Finding] =
    retargetBoundary(program, program.units)

  /** Retarget boundary check scoped to emitted units. */
  def retargetBoundary(program: Program, units: List[Tree.ClassDef]): List[RetargetBoundaryCheck.Finding] =
    RetargetBoundaryCheck.check(program, units, effectiveRetarget)

  /** the java symbols this run's mapping sends to a target that CANNOT BE A PARENT — see
    * [[restoreUninheritableParents]]. EMPTY unless the program actually names one, which makes the
    * pass a no-op by arithmetic on every port that does not. */
  private[transform] var uninheritableSyms: Set[SymId] = Set.empty

  /** …and the classes among them whose value at the TARGET's own slot really is a detached one,
    * with the target FQN it may be projected to — see [[detachedEntriesIn]]. EMPTY unless the
    * program declares such a class AND the class already refuses the write, so the projection
    * declines by arithmetic on every port that does neither. */
  private[transform] var detachedEntries: Map[SymId, String] = Map.empty

  /** `java.lang.UnsupportedOperationException`, as this run's own type — see
    * [[CollectionsTransform.UnsupportedOnTarget]]. */
  private[transform] var unsupportedOpTpe: TypeRepr = TypeRepr.NoType
  private[transform] var unsupportedOpSym: SymId    = SymId.None
  /** every external seam this run could NOT close, in the order it met them. Reported through
    * [[boundary]], because it is the same residue `CollectionBoundaryCheck` counts and a reader
    * looking for "what did the retyping leave open" must find all of it in one place. */
  private[transform] val externalSeams = collection.mutable.ListBuffer[CollectionBoundaryCheck.Finding]()

  /** A parent whose target cannot be inherited (e.g. `Map.Entry` to `Tuple2` which is final)
    * is left as java's; the seam is counted. No-op when [[uninheritableSyms]] is empty.
    * // ENGINE-LIMITS K5.7 */
  private[transform] def restoreUninheritableParents(orig: Tree.ClassDef, mapped: Tree.ClassDef)(using Program): Tree.ClassDef =
    if uninheritableSyms.isEmpty then mapped
    else
      def tpeOf(p: Term | TypeTree): TypeRepr = p match
        case tt: TypeTree => tt.tpe
        case t: Term      => t.tpe
      // members this class's retained parents declare that their targets cannot carry.
      val unimplementable = collection.mutable.Set.empty[CollectionsTransform.MemberSig]
      val parents =
        // lengths agree by construction; a mismatch means the traversal changed shape, so the
        // mapped list is the honest answer rather than a zip that silently truncates (see spine).
        if orig.parents.sizeIs != mapped.parents.size then mapped.parents
        else orig.parents.zip(mapped.parents).map { (o, m) =>
          headSym(tpeOf(o)).filter(uninheritableSyms.contains) match
            case scala.None => m
            case Some(_)    =>
              val kept   = TirPrinter.tpe(tpeOf(o), TirPrinter.Style.canonical)
              val target = TirPrinter.tpe(tpeOf(m), TirPrinter.Style.canonical)
              headSym(tpeOf(m)).flatMap(summon[Program].symbolOf).map(_.fullName)
                .flatMap(CollectionsTransform.UnsupportedOnTarget.get)
                .foreach(unimplementable ++= _)
              seam("parent (implements)", target, kept, orig.origin, orig.symbol,
                   CollectionBoundaryCheck.Issue.InexpressibleParent)
              // a porter note beside the class (§4.575) — the diff against upstream shows
              // nothing at exactly the line the question is asked at.
              record(Decision(
                kind       = Decision.Kind.RetainedParent,
                subject    = orig.symbol,
                subjectFqn = summon[Program].symbolOf(orig.symbol).map(_.fullName).getOrElse(kept),
                detail = Map(
                  "kept"       -> kept,
                  "instead-of" -> target,
                  "why" -> ("this class IMPLEMENTS a java type the collections mapping covers, and " +
                    "the target cannot BE a parent — it is final, has no write-through member and " +
                    "takes its components in its constructor. The parent stays java's so the class " +
                    "itself compiles; a value of it meeting the target is counted at the slot"),
                ),
                reason = Reason.Universal("inexpressible-parent(K5.7)"),
                origin = orig.origin,
              ))
              o
        }
      val body = CollectionsTransform.spine(orig.body, mapped.body, orig.symbol).map {
        case (o: Tree.ClassDef, m: Tree.ClassDef) => restoreUninheritableParents(o, m)
        // the member half of the same refusal, under both conditions: declaresUnimplementable
        // (really the interface's member) and brokenByMapping (the phase can point at what broke).
        case (_, m: Tree.DefDef) if unimplementable.nonEmpty =>
          val broken =
            if declaresUnimplementable(m, unimplementable.toSet) then brokenByMapping(m) else scala.None
          broken.fold(m)(refuseOnTarget(m, orig, _))
        case (_, m)                               => m
      }
      mapped.copy(parents = parents, body = body)

  /** Is this the interface's member, or a method that merely shares its name? Tested by the
    * retained parent's own signature (e.g. `Map.Entry` declares exactly `setValue(V)`), never the
    * bare name — a class may declare `setValue(int, int)` beside it, which java resolves
    * separately. See [[CollectionsTransform.MemberSig]] for `arity`. CLAUDE.md §3 */
  private[transform] def declaresUnimplementable(d: Tree.DefDef, sigs: Set[CollectionsTransform.MemberSig])(
      using p: Program): Boolean =
    p.symbolOf(d.symbol).exists(s =>
      sigs.contains(CollectionsTransform.MemberSig(s.name, d.paramss.map(_.size).sum)))

  /** Can this phase point at what it broke? The second condition, keeping the refusal a
    * translation rather than a policy: `Map.Entry.setValue` throwing is conforming only for an
    * entry that genuinely cannot perform the write, so the licence is read off the BODY (a call to
    * a member `UnsupportedOnTarget` says the retyped receiver's target cannot express), never off
    * the declaration (§4.56). Returns the reference found, so the decision can name what it broke. */
  private[transform] def brokenByMapping(d: Tree.DefDef)(using p: Program): Option[String] =
    d.rhs.flatMap { body =>
      StandardTraversal.scanTerm(body, Option.empty[String]) { (acc, t) =>
        if acc.nonEmpty then acc
        else
          t match
            case Tree.Select(recv, m, _, _) =>
              for
                tgt  <- headSym(recv.tpe).flatMap(p.symbolOf).map(_.fullName)
                sigs <- CollectionsTransform.UnsupportedOnTarget.get(tgt)
                nm   <- p.symbolOf(m).map(_.name)
                if sigs.exists(_.name == nm)
              yield MemberKey(tgt, nm).render
            case _ => scala.None
      }
    }

  /** Substitute `UnsupportedOperationException` for a member the retained parent declares
    * and the mapping target cannot carry. Both guards required: [[declaresUnimplementable]]
    * and [[brokenByMapping]]. // ENGINE-LIMITS K5.7 */
  private[transform] def refuseOnTarget(d: Tree.DefDef, owner: Tree.ClassDef, broke: String)(using p: Program): Tree.DefDef =
    val nm  = p.symbolOf(d.symbol).map(_.name).getOrElse("")
    val fqn = p.symbolOf(d.symbol).map(_.fullName).getOrElse(nm)
    val o   = d.origin
    val why =
      s"$nm: this java.util.Map.Entry was ported to a detached pair, so the backing map is not " +
        "reachable from the entry. java declares this an OPTIONAL operation whose contract is this " +
        "exception; writing to the detached copy would succeed and change nothing"
    val exn = Tree.Apply(Tree.New(TypeTree(unsupportedOpTpe, o), unsupportedOpTpe, o),
                         List(Tree.Literal(Constant.StringC(why), stringTpe, o)),
                         unsupportedOpSym, unsupportedOpTpe, o)
    seam(s"member (implements) $nm", TirPrinter.tpe(d.returnTpt.tpe, TirPrinter.Style.canonical),
         CollectionsTransform.UnsupportedOperationFqn, o, d.symbol,
         CollectionBoundaryCheck.Issue.InexpressibleParent)
    record(Decision(
      kind       = Decision.Kind.SubstitutedBody,
      subject    = d.symbol,
      subjectFqn = fqn,
      detail = Map(
        "member"  -> nm,
        "throws"  -> CollectionsTransform.UnsupportedOperationFqn,
        "owner"   -> p.symbolOf(owner.symbol).map(_.fullName).getOrElse(""),
        // the reference the mapping removed, verbatim — the one fact a reader cannot recover from the emitted throw.
        "broke"   -> broke,
        "why" -> ("the RETAINED PARENT declares this member and the mapping target cannot carry " +
          "it, so the body is java's own documented refusal for an optional operation. Writing to " +
          "the detached pair would compile and change nothing (K2); dropping the member would " +
          "leave the class abstract against the parent it kept"),
      ),
      reason = Reason.Universal("inexpressible-parent(K5.7)"),
      origin = o,
    ))
    d.copy(rhs = Some(Tree.Throw(exn, d.returnTpt.tpe, o)))

  /** Fills [[retainedOverrides]]: members overriding a class-file declaration whose signature
    * this phase would move (isOverride, mentionsMapped, no program-declared ancestor, an
    * uncovered external ancestor could declare it). Holds the member and all overriders below. */
  private[transform] def applyClassFileOverrides(p: Program): Unit =
    retainedOverrides = Set.empty; retainedOwners = Set.empty; retainedAnchors = Map.empty
    given Program = p
    val owned = p.owned
    val seeds = p.symbols.all.iterator.filter { s =>
      s.flags.isOverride && owned(s.id) && isMethodLike(s.info) && mentionsMapped(s.info)
    }.map(_.id).toList
    if seeds.isEmpty then return
    val graph  = OverrideGraph.build(p)
    val held   = collection.mutable.Set.empty[SymId]
    val anchor = collection.mutable.Map.empty[SymId, String]
    seeds.foreach { m =>
      if graph.overridden(m).isEmpty then
        val sig       = graph.signatureOf(m)
        val declarers = graph.externalAncestorsOf(graph.ownerOf(m))
          .filter(fqn => sig.exists(ExternalSurface.default.mayDeclare(fqn, _)))
        // the shim half decides it: where a candidate declarer is a type the mapping covers,
        // the parent is already shim-shaped, so hold back only when every candidate stayed java.
        val kept = if declarers.exists(coveredExternally) then Nil else declarers
        if kept.nonEmpty then
          val label = kept.sorted.map(fqn => MemberKey(fqn, sig.map(_.name).getOrElse("?")).render).mkString(", ")
          (m :: graph.overriders(m)).filter(owned).filter(spliceable(graph))
            .foreach { x => held += x; anchor += (x -> label) }
    }
    retainedOverrides = held.toSet
    retainedAnchors   = anchor.toMap
    // a held member's parameter symbols go with it, since restoreExcluded splices the original ValDefs back.
    retainedOwners = p.symbols.all.collect { case s if retainedOverrides(s.owner) => s.id }.toSet

  /** is this external type one the phase's OWN tables move — so that the emitted parent is a shim
    * and an override of its members belongs in shim shape? Read from the tables, never from the
    * name (§4.56). */
  private[transform] def coveredExternally(fqn: String): Boolean =
    typeMap.contains(fqn) || effectiveRetarget.contains(fqn)

  /** True when [[restoreExcluded]] can reach this member (owner has a `ClassDef` definition).
    * Anonymous classes hang off `Tree.New` inside a term and are not on the splice spine. */
  private[transform] def spliceable(graph: OverrideGraph)(m: SymId)(using p: Program): Boolean =
    p.definitionOf(graph.ownerOf(m)).exists(_.isInstanceOf[Tree.ClassDef])

  // -------------------------------------------------------------------------
  // …and the MODIFIER a re-parenting invalidated — `ENGINE-LIMITS.md` K28
  // -------------------------------------------------------------------------

  /** Strip `override` from members whose only anchor was a parent this phase re-parented
    * and whose emitted target does not declare it. Four conjuncts: owned + `isOverride`,
    * re-parented owner ([[parentClash]]), no program ancestor declares it, no unknown
    * ancestor could declare it. No-op when `parentClash` is empty. // ENGINE-LIMITS K28 */
  private[transform] def strippedOverrides(before: SymbolTable)(using p: Program): Set[SymId] =
    if parentClash.isEmpty then return Set.empty
    val graph = OverrideGraph.build(p)
    val owned = p.owned
    before.all.iterator.filter { s =>
      s.flags.isOverride && owned(s.id) && isMethodLike(s.info) &&
        parentClash.get(s.owner).exists(mp => mp.kinds.nonEmpty || mp.shims.nonEmpty) &&
        graph.signatureOf(s.id).exists { sig =>
          !ExternalSurface.javaLangObjectDeclares(sig) &&
            !programAncestorDeclares(graph, s.owner, sig) &&
            !graph.externalAncestorsOf(s.owner).filterNot(tabulatedTarget)
              .exists(ExternalSurface.default.mayDeclare(_, sig)) &&
            !mintedParentDeclares(s.owner, sig)
        }
    }.map(_.id).toSet

  /** Did this phase move `fqn` to a target THIS FILE tabulates the surface of? Deliberately not
    * [[coveredExternally]] (a wider question whose `retarget` disjunct has no surface here — a
    * parent moved into one is as unknown as any unparsed type, and excluding it lost an `override`
    * that `scala.math.Ordering` really does declare). The positive test §4.56 asks for: what did
    * the phase do, and can it answer for the result. */
  private[transform] def tabulatedTarget(fqn: String): Boolean =
    typeMap.get(fqn).exists { (tgt, k) =>
      if CollectionsTransform.standaloneTargets(tgt) then CollectionsTransform.OverridesShim.contains(tgt)
      else CollectionsTransform.OverridesTarget.contains(k.toString)
    }

  /** Does an ancestor THIS PROGRAM DECLARES declare `sig`, by name and arity, deliberately
    * looser than the override edge? `OverrideGraph.overridden` is exact and wrong here — a java
    * interface may permute a type-parameter name its implementor uses differently, so `overridden`
    * answers empty for a real override. Asked at the looser key; the error direction is refusal. D1 */
  private[transform] def programAncestorDeclares(graph: OverrideGraph, owner: SymId,
                                      sig: OverrideGraph.Signature)(using Program): Boolean =
    graph.ancestorsOf(owner).exists { a =>
      graph.membersOf(a).exists(m =>
        graph.signatureOf(m).exists(o => o.name == sig.name && o.arity == sig.arity))
    }

  /** Does any parent this phase minted for `cls` declare `sig`? OR across the parents, not AND:
    * a class routinely has several (§4.5), and one parent declaring the member is enough to keep
    * the modifier true. */
  private[transform] def mintedParentDeclares(cls: SymId, sig: OverrideGraph.Signature): Boolean =
    parentClash.get(cls).exists { mp =>
      mp.kinds.exists(k => CollectionsTransform.OverridesTarget.get(k.toString).exists(_.exists(_.matches(sig)))) ||
        mp.shims.exists(s => CollectionsTransform.OverridesShim.get(s).exists(_.exists(_.matches(sig))))
    }

  /** One decision row per member for [[strippedOverrides]]. `Reason.Universal`, since there is
    * no key to point a reader at — the engine chose the target, not the port. `parent=` answers the
    * reader's next question (`overrides nothing` names a type the java file never mentions). */
  private[transform] def recordStrippedOverrides(stripped: Set[SymId], before: SymbolTable)(using p: Program): Unit =
    stripped.toList.flatMap(id => before.all.find(_.id == id)).sortBy(_.fullName).foreach { s =>
      val parents = parentClash.get(s.owner).toList
        .flatMap(mp => mp.targets.toList ++ mp.shims.toList).distinct.sorted
      record(Decision(
        kind       = Decision.Kind.StrippedOverride,
        subject    = s.id,
        subjectFqn = s.fullName,
        detail = Map(
          "member" -> s.name,
          "parent" -> (if parents.isEmpty then "?" else parents.mkString(", ")),
          "why" -> ("java's own hierarchy justified this `override` and this phase moved the parent " +
            "that justified it, so the modifier was a statement about a type the emitted class no " +
            "longer extends. The member itself is unchanged"),
        ),
        reason = Reason.Universal("minted-parent-override(§1, K28)"),
        origin = Decision.originOf(p, s.id),
      ))
    }

  // -------------------------------------------------------------------------
  // the surface the re-parenting owes. ENGINE-LIMITS K28.1
  // -------------------------------------------------------------------------

  /** one bridge this run will build: the class, the kind it was minted at, the parent's java type
    * arguments, the row, and the java member the body delegates to. `rename` is
    * `CapturedByTarget`'s answer, carried so the rename pass and body builder cannot disagree. */
  private[transform] final case class Bridge(cls: SymId, kind: Kind, args: List[TypeRepr],
                                  row: CollectionsTransform.Bridged, java: SymId, rename: Boolean)

  private[transform] var bridges: List[Bridge] = Nil

  /** the java types the mapping sends to a given target — the inverse of `typeMap`. `subsumed`
    * is keyed on the target FQN; an override anchor is spelled with the java type, so the two are
    * joined through this table rather than by a name that looks alike. */
  private[transform] def shimSource(target: String): Set[String] =
    typeMap.collect { case (fqn, (tgt, _)) if tgt == target => fqn }.toSet

  /** how many type arguments a kind's target needs before a bridge can name its key, value or
    * element type. A RAW clause supplies none, and inventing `java.lang.Object` for them would be
    * §4.6's fabricated fact at the emitted signature — so the whole class declines, counted. */
  private[transform] def kindArity(k: Kind): Int = if k == Kind.Map then 2 else 1

  /** Which bridges this run owes — one row per (class, row) the table names and the class can
    * answer. Asked of `declared`, never `kinds`, so the synthesis lands on the base and not on
    * each subclass (a second copy there would define one surface twice — §1.5's shape one module in). */
  private[transform] def planBridges(p: Program): List[Bridge] =
    if parentClash.isEmpty then return Nil
    given Program = p
    val graph = OverrideGraph.build(p)
    def sigOf(m: SymId): Option[OverrideGraph.Signature] = graph.signatureOf(m)
    /** the delegate, on THIS class and no ancestor of it — so one bridge lands on the component
      * rather than once per subclass, and a java interface (which declares none of these) declines
      * structurally. */
    def ownMember(cls: SymId, want: ExternalSurface.Member): Option[SymId] =
      if graph.ancestorsOf(cls).exists(a => graph.membersOf(a).exists(m => sigOf(m).exists(want.matches)))
      then scala.None
      else
        // where the key names several, java's own resolution order picks: add(E) beside
        // add(E...) share a (name, arity) key, and java admits the fixed-arity candidate before
        // the pack (JLS 15.12.2). A last-array candidate is still taken when it's the only one.
        val cands = graph.membersOf(cls).filter(m => sigOf(m).exists(want.matches) && !literal(m))
        def packs(m: SymId): Boolean = sigOf(m).flatMap(_.descriptor).exists(_.params.lastOption match
          case Some(Param.Arr(_)) => true
          case _                  => false)
        cands.find(m => !packs(m)).orElse(cands.headOption)
    parentClash.toList.sortBy((c, _) => p.symbolOf(c).map(_.fullName).getOrElse("")).flatMap { (cls, mp) =>
      mp.declared.distinct.flatMap { (k, args) =>
        val rows = CollectionsTransform.BridgedTarget.getOrElse(k.toString, Nil)
        // a type declaring none of the delegates is not the implementor — owes nothing, not reported.
        val found = rows.filter(_.from.nonEmpty).flatMap(r => r.from.iterator.flatMap(ownMember(cls, _)).nextOption())
        if rows.isEmpty || found.isEmpty then Nil
        else if args.sizeIs != kindArity(k) then
          // a raw implements Map names no key/value; leaving the compiler's own E164 is the honest arm.
          refuseBridge(p, cls, k, "raw-parent",
            s"the mapped `implements` clause is RAW, so the ${kindArity(k)} type argument(s) the " +
              "bridged signatures need are not written anywhere")
          Nil
        else rows.flatMap { row =>
          if row.from.isEmpty then Some(Bridge(cls, k, args, row, SymId.None, false))
          else row.from.iterator.flatMap(ownMember(cls, _)).nextOption() match
            case Some(j) =>
              val captured = CollectionsTransform.CapturedByTarget.getOrElse(k.toString, Set.empty)
                .exists(m => sigOf(j).exists(m.matches))
              Some(Bridge(cls, k, args, row, j, captured))
            case scala.None if !row.required => scala.None
            case scala.None =>
              refuseBridge(p, cls, k, "no-java-member",
                s"`${row.scalaName}/${row.arity}` is declared by the emitted parent and this class " +
                  s"declares none of ${row.from.map(m => s"${m.name}/${m.arity}").mkString(", ")} " +
                  "to build it from")
              scala.None
        }
      }
    }

  /** the refusal lane: one row per bridge this run could not build, naming the guard (§3), on
    * `collection-boundary` under its own `Issue` — the class is missing a member scalac will demand. */
  private[transform] def refuseBridge(p: Program, cls: SymId, k: Kind, guard: String, why: String): Unit =
    seam(s"minted-parent surface [$guard]", k.toString, why,
         Decision.originOf(p, cls), cls, CollectionBoundaryCheck.Issue.UnbridgedMember)

  /** Renames every captured delegate out of the way through `MemberRenamer` (§4.55): expands
    * through the override closure, screens for an external anchor, reads effective names
    * parents-first. `SuffixUntilFree`, not `Refuse` — the body reads the delegate's name back out
    * of the symbol table. Refused per owning class, since half a bridged surface compiles worse
    * than the class this started from. */
  private[transform] def renameBridgeDelegates(p: Program): SymbolTable =
    val wanted = bridges.filter(b => b.rename && b.java != SymId.None).map(_.java).distinct
    if wanted.isEmpty then return p.symbols
    val graph = OverrideGraph.build(p)
    val owners = bridges.filter(b => b.java != SymId.None).map(b => b.java -> b.cls).toMap
    // parents this phase removed from this class: java types the mapping re-parented away from,
    // plus shim clauses dropSubsumedParents deleted (K28.1). Per request, not per call.
    val reParented: Set[String] = typeMap.collect {
      case (fqn, (tgt, _)) if !CollectionsTransform.standaloneTargets(tgt) &&
                              !CollectionsTransform.UninheritableTargets(tgt) => fqn
    }.toSet
    def detachedFor(cls: SymId): Set[String] =
      reParented ++ parentClash.get(cls).toList.flatMap(_.subsumed.keySet).flatMap(shimSource)
    val requests = wanted.flatMap { j =>
      p.symbolOf(j).map { s =>
        val cls      = owners.getOrElse(j, SymId.None)
        val ownerFqn = p.symbolOf(cls).map(_.fullName).getOrElse("")
        MemberRenamer.Request(j, s.name + CollectionsTransform.BridgeSuffix,
                              Reason.Universal("minted-parent-surface(§1, K28.1)"),
                              MemberKey(ownerFqn, s.name).render, ownerFqn, detachedFor(cls))
      }
    }
    val (renamed, refusals) = MemberRenamer.rename(
      p, graph, requests, MemberRenamer.OnCollision.SuffixUntilFree, decisions)
    if refusals.nonEmpty then
      val lost = refusals.flatMap(r => owners.get(r.request.member)).toSet
      refusals.foreach { r =>
        refuseBridge(p, owners.getOrElse(r.request.member, SymId.None),
                     Kind.Map, "rename-refused", r.why)
      }
      // the whole class, not the one member — see the group note above.
      bridges = bridges.filterNot(b => lost.contains(b.cls))
    renamed.symbols

  /** The synthesis, appended to each owning class's body — not spliced at a position, since
    * these members have no java counterpart to sit beside and JLS 12.5's ordering rule (§4.55) has
    * nothing to say about them. */
  private[transform] def synthesiseBridges(units: List[Tree.ClassDef], symbols: SymbolTable)
                               (using p: Program): (List[Tree.ClassDef], List[Symbol]) =
    if bridges.isEmpty then return (units, Nil)
    val added = collection.mutable.ListBuffer[Symbol]()
    var next  = symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    def mint(nm: String, full: String, owner: SymId, info: TypeRepr, isOverride: Boolean): SymId =
      val id = SymId(next); next += 1
      added += Symbol(id, nm, full, Flags(isOverride = isOverride), owner, info)
      id
    val byClass = bridges.groupBy(_.cls)
    val ph = new Phase:
      def name: String = "collections/minted-parent-surface"
      override def transformClassDef(cd: Tree.ClassDef)(using Program): Tree.ClassDef =
        byClass.get(cd.symbol) match
          case scala.None     => cd
          case Some(myRows)   =>
            val built = myRows.flatMap(b => buildBridge(b, cd, symbols, mint))
            if built.isEmpty then cd else cd.copy(body = cd.body ++ built)
    // synthesised symbols are handed back, not kept in a field: added after strippedOverrides
    // runs, so ordering alone answers "is this one of mine".
    (units.map(u => StandardTraversal.mapClassDef(ph, u)), added.toList)

  /** One bridged member, as a tree. Every body is a delegation: java's own behaviour, unchanged,
    * reached under a new name, plus one of four shape conversions the parent asked for
    * (`Option(x)`, `{ x; this }`, `.asScala`, `{ x; () }`). The three `Kind.Seq` rows with no
    * delegate are documented at their bodies (`JavaCollections.buffer*`). `None` for a row this
    * builder has no arm for — the honest arm rather than a crash. */
  private[transform] def buildBridge(b: Bridge, cd: Tree.ClassDef, symbols: SymbolTable,
                          mint: (String, String, SymId, TypeRepr, Boolean) => SymId)
                         (using p: Program): Option[Tree.DefDef] =
    val o        = cd.origin
    val cls      = cd.symbol
    val selfT    = TypeRepr.ThisType(cls)
    def self     = Tree.This(cls, selfT, o)
    val args     = b.args.map(t => StandardTraversal.mapType(this, t))
    val ownerFqn = p.symbolOf(cls).map(_.fullName).getOrElse("")
    def tpe(s: SymId, as: TypeRepr*): TypeRepr =
      if as.isEmpty then TypeRepr.TypeRef(TypeRepr.NoPrefix, s)
      else TypeRepr.AppliedType(TypeRepr.TypeRef(TypeRepr.NoPrefix, s), as.toList)
    val javaInfo = symbols.all.find(_.id == b.java).map(_.info)
    val javaRes  = javaInfo.collect { case TypeRepr.MethodType(_, r, _) => r }
      .getOrElse(TypeRepr.NoType)
    val javaName = p.symbolOf(b.java).map(_.name).getOrElse("")
    def callJ(as: List[Term], res: TypeRepr): Term =
      Tree.Apply(Tree.Select(self, b.java, TypeRepr.NoType, o), as, b.java, res, o)
    def stat(as: List[Term], res: TypeRepr, tail: Term, t: TypeRepr): Term =
      Tree.Block(List(callJ(as, res)), tail, t, o)
    def unit: Term = Tree.Literal(Constant.UnitC, unitTpe, o)
    def param(nm: String, t: TypeRepr): Tree.ValDef =
      Tree.ValDef(mint(nm, nm, SymId.None, t, false), TypeTree(t, o), scala.None, o)
    def ref(v: Tree.ValDef): Term = Tree.Ident(v.symbol, v.tpt.tpe, o)
    def opt(x: Term, of: TypeRepr): Term =
      Tree.Apply(Tree.Ident(optionSym, TypeRepr.NoType, o), List(x), optionSym, tpe(optionSym, of), o)
    /** the shim result at a `scala.collection` slot. Asked of the phase's OWN record — is the head
      * one of the shims this mapping produces — and never of the name (§4.56); a java member the
      * mapping already retyped to a `scala.collection` type conforms as it stands. */
    def asIterable(x: Term, elem: TypeRepr): Term =
      if headSym(javaRes).exists(shimSyms.contains)
      then Tree.Select(x, asScalaIterableSym, tpe(scalaIterableSym, elem), o)
      else x
    /** …and the ITERATOR half, whose discriminator is the RENAME rather than a type. A delegate this
      * pass renamed is the one java's `Iterable` declares, so its result is a `JavaIterator`-shaped
      * value and needs the view; a delegate it did NOT rename is `entrySet()`, whose result the
      * mapping already retyped to a `scala.collection` and whose `.iterator` is java's own idiom for
      * the same traversal. */
    def asIterator(elem: TypeRepr): Term =
      val call = callJ(Nil, javaRes)
      val want = tpe(scalaIteratorSym, elem)
      if b.rename then Tree.Select(call, asScalaIteratorSym, want, o)
      else Tree.Select(call, iteratorMemberSym, want, o)
    def defd(nm: String, ps: List[Tree.ValDef], res: TypeRepr, body: Term,
             tps: List[Tree.TypeDef] = Nil): Option[Tree.DefDef] =
      val info = TypeRepr.MethodType(
        ps.map(v => p.symbolOf(v.symbol).map(_.name).getOrElse("x") -> v.tpt.tpe), res)
      val sym = mint(nm, MemberKey(ownerFqn, nm).render, cls, info, true)
      recordBridge(b, sym, MemberKey(ownerFqn, nm).render,
                   if b.java == SymId.None then "-" else MemberKey(ownerFqn, javaName).render, o)
      Some(Tree.DefDef(sym, if ps.isEmpty then Nil else List(ps), TypeTree(res, o), Some(body), o,
                       tparams = tps))

    (b.kind, b.row.scalaName, b.row.arity) match
      case (Kind.Map, nm, ar) =>
        val k = args.head; val v = args(1); val pair = tpe(tuple2Sym, k, v)
        (nm, ar) match
          case ("put", 2) =>
            val pk = param("key", k); val pv = param("value", v)
            defd("put", List(pk, pv), tpe(optionSym, v), opt(callJ(List(ref(pk), ref(pv)), v), v))
          case ("get", 1) =>
            val pk = param("key", k)
            defd("get", List(pk), tpe(optionSym, v), opt(callJ(List(ref(pk)), v), v))
          case ("addOne", 1) =>
            val pe = param("elem", pair)
            defd("addOne", List(pe), selfT,
                 stat(List(Tree.Select(ref(pe), key1Sym, k, o), Tree.Select(ref(pe), value2Sym, v, o)),
                      v, self, selfT))
          case ("subtractOne", 1) =>
            val pk = param("key", k)
            defd("subtractOne", List(pk), selfT, stat(List(ref(pk)), v, self, selfT))
          case ("iterator", 0) => defd("iterator", Nil, tpe(scalaIteratorSym, pair), asIterator(pair))
          case ("values", 0)   =>
            defd("values", Nil, tpe(scalaIterableSym, v), asIterable(callJ(Nil, javaRes), v))
          case ("keys", 0)     =>
            defd("keys", Nil, tpe(scalaIterableSym, k), asIterable(callJ(Nil, javaRes), k))
          case _ => scala.None
      case (Kind.Set, nm, ar) =>
        val e = args.head
        (nm, ar) match
          case ("contains", 1) | ("indexOf", 1) =>
            val pe = param("elem", e)
            defd("contains", List(pe), tpe(boolSym), callJ(List(ref(pe)), tpe(boolSym)))
          case ("addOne", 1) =>
            val pe = param("elem", e)
            defd("addOne", List(pe), selfT, stat(List(ref(pe)), tpe(boolSym), self, selfT))
          case ("subtractOne", 1) =>
            val pe = param("elem", e)
            defd("subtractOne", List(pe), selfT, stat(List(ref(pe)), tpe(boolSym), self, selfT))
          case ("iterator", 0) => defd("iterator", Nil, tpe(scalaIteratorSym, e), asIterator(e))
          case _ => scala.None
      case (Kind.Seq, nm, ar) =>
        val a = args.head
        def helper(n: String, as: List[Term], res: TypeRepr): Term =
          val s = sym(n)
          Tree.Apply(Tree.Ident(s, TypeRepr.NoType, o), self :: as, s, res, o)
        (nm, ar) match
          case ("apply", 1) =>
            val pi = param("i", tpe(intSym))
            defd("apply", List(pi), a, callJ(List(ref(pi)), a))
          case ("length", 0) => defd("length", Nil, tpe(intSym), callJ(Nil, tpe(intSym)))
          case ("update", 2) =>
            val pi = param("idx", tpe(intSym)); val pe = param("elem", a)
            defd("update", List(pi, pe), unitTpe, stat(List(ref(pi), ref(pe)), a, unit, unitTpe))
          case ("insert", 2) =>
            val pi = param("idx", tpe(intSym)); val pe = param("elem", a)
            defd("insert", List(pi, pe), unitTpe, stat(List(ref(pi), ref(pe)), unitTpe, unit, unitTpe))
          case ("prepend", 1) =>
            val pe = param("elem", a)
            defd("prepend", List(pe), selfT,
                 stat(List(Tree.Literal(Constant.IntC(0), tpe(intSym), o), ref(pe)), unitTpe, self, selfT))
          case ("addOne", 1) =>
            val pe = param("elem", a)
            defd("addOne", List(pe), selfT, stat(List(ref(pe)), tpe(boolSym), self, selfT))
          case ("remove", 1) =>
            val pi = param("idx", tpe(intSym))
            defd("remove", List(pi), a, callJ(List(ref(pi)), a))
          case ("iterator", 0) => defd("iterator", Nil, tpe(scalaIteratorSym, a), asIterator(a))
          case ("contains", 1) | ("indexOf", 1) =>
            // the two GENERIC bridges. `SeqOps.contains`/`indexOf` take `A1 >: A` — scala's own widening, so
            // that `xs.contains(anAny)` type-checks — and a bridge declared at `A` would erase to
            // the same `(Object)Boolean` as java's own member and reproduce the clash it is here to
            // close. The cast is what java's `contains(Object)` formal already asks of every
            // caller: `A1` may be `Any`, which is not a `java.lang.Object`.
            val a1  = mint("A1", "A1", cls, TypeRepr.NoType, false)
            val a1t = TypeRepr.TypeRef(TypeRepr.NoPrefix, a1)
            val objT = TypeRepr.TypeRef(TypeRepr.NoPrefix, objectSym)
            val pe  = param("elem", a1t)
            val res = if b.row.scalaName == "contains" then tpe(boolSym) else tpe(intSym)
            defd(b.row.scalaName, List(pe), res,
                 callJ(List(Tree.Typed(ref(pe), TypeTree(objT, o), objT, o)), res),
                 List(Tree.TypeDef(a1, TypeTree(TypeRepr.TypeBounds(a, TypeRepr.NoType), o), o)))
          case ("remove", 2) =>
            val pi = param("idx", tpe(intSym)); val pc = param("count", tpe(intSym))
            defd("remove", List(pi, pc), unitTpe,
                 helper("bufferRemoveRange", List(ref(pi), ref(pc)), unitTpe))
          case ("insertAll", 2) =>
            val pi = param("idx", tpe(intSym)); val pe = param("elems", tpe(iterableOnceSym, a))
            defd("insertAll", List(pi, pe), unitTpe,
                 helper("bufferInsertAll", List(ref(pi), ref(pe)), unitTpe))
          case ("patchInPlace", 3) =>
            val pf = param("from", tpe(intSym)); val pp = param("patch", tpe(iterableOnceSym, a))
            val pr = param("replaced", tpe(intSym))
            defd("patchInPlace", List(pf, pp, pr), selfT,
                 Tree.Block(List(helper("bufferPatchInPlace", List(ref(pf), ref(pp), ref(pr)), unitTpe)),
                            self, selfT, o))
          case _ => scala.None
      case _ => scala.None

  /** DECISION PROVENANCE for one bridge — see [[Decision.Kind.BridgedMember]] for what the detail
    * has to carry and why. `Reason.Universal`, because there is no key: java said
    * `implements java.util.Map`, this phase chose the target, and telling the reader to edit a
    * scope would cost them the session §4.45 is about. */
  private[transform] def recordBridge(b: Bridge, sym: SymId, fqn: String, delegate: String, o: Origin): Unit =
    record(Decision(
      kind       = Decision.Kind.BridgedMember,
      subject    = sym,
      subjectFqn = fqn,
      detail = Map(
        "member"   -> s"${b.row.scalaName}/${b.row.arity}",
        "parent"   -> b.kind.toString,
        "delegate" -> delegate,
        "why" -> ("the parent this phase minted declares this member and java's own is the wrong " +
          "SHAPE for it, so java's member was RENAMED and scala's is synthesised over it. " +
          "Retyping java's member instead would close the same error and delete whatever its " +
          "result type was carrying; a rename moves a name and nothing else, and §4.55's machinery " +
          "re-points every reference exactly"),
      ),
      reason = Reason.Universal("minted-parent-surface(§1, K28.1)"),
      origin = o,
    ))

  /** Records the `super` -> `this` substitution per declaration, filtered to classes whose
    * `super` this run substituted. ENGINE-LIMITS K29 */
  private[transform] def recordSuperDefaults(using p: Program): Unit =
    if superDefaults.isEmpty then return
    val classes = superDefaults.map(_._1).toSet
    superDefaults.toList.groupBy(r => (r._2, r._3)).foreach { case ((callee, member), _) =>
      Decision.declarationsUsing(p, callee)
        .filter((encl, _) => p.symbolOf(encl).exists(s => classes.contains(s.owner)))
        .foreach { (encl, o) =>
          record(Decision(
            kind       = Decision.Kind.SubstitutedCall,
            subject    = encl,
            subjectFqn = Decision.fqnOf(p, encl, member),
            detail = Map(
              "was"        -> s"super.$member",
              "now"        -> s"balticporter.runtime.JavaCollections.$member(this, …)",
              "jdkDefault" -> CollectionsTransform.VirtualJdkDefaultBodies(member),
              "why" -> ("this class's emitted PARENT is a scala collection this phase minted, so " +
                "the JDK default `super` named is gone and no configuration key can bring it back. " +
                "The helper reproduces that default's own body, which dispatches VIRTUALLY through " +
                "`this` — so standing it on `this` is what `super` meant, and is licensed for this " +
                "member and not in general"),
            ),
            reason = Reason.Universal("jdk-default-at-this(§1)"),
            origin = o,
          ))
        }
    }

  /** One decision row per [[applyClassFileOverrides]] retention — the other §1 classification
    * from [[recordScopedOut]], since a reader told to widen a scope that does not exist has been
    * sent after a key nothing in the port can supply. CLAUDE.md §4.45 */
  private[transform] def recordRetainedSignatures(before: SymbolTable)(using p: Program): Unit =
    if retainedOverrides.isEmpty then return
    before.all.foreach { s =>
      if retainedOverrides(s.id) && mentionsMapped(s.info) && Decision.isDeclaration(p, s) then
        record(Decision(
          kind       = Decision.Kind.RetainedSignature,
          subject    = s.id,
          subjectFqn = s.fullName,
          detail = Map(
            "kept"      -> TirPrinter.tpe(s.info, TirPrinter.Style.canonical),
            "overrides" -> retainedAnchors.getOrElse(s.id, "?"),
            "why" -> ("this member OVERRIDES a declaration in a compiled class file, whose " +
              "signature no phase may move — retyped, it would override nothing and its own " +
              "`super` call could not compile. Nothing in this port's configuration changes " +
              "that; the seam moves to the callers, where it is counted"),
          ),
          reason = Reason.Universal("class-file-override(§4.56)"),
          origin = Decision.originOf(p, s.id),
        ))
    }

  /** Record a `Decision` for each declaration whose `info` moved between before/after symbol tables.
    * `Reason.Universal` for the default scope; `Reason.Configured` under `RuleScope.Only`. */
  private[transform] def recordRetypings(before: SymbolTable, after: SymbolTable)(using p: Program): Unit =
    before.all.foreach { s =>
      after.get(s.id).foreach { now =>
        if now.info != s.info && Decision.isDeclaration(p, s) then
          val (reason, why) = admittedBy.get(s.id) match
            case Some(entry) =>
              (Reason.Configured(name, entry),
               "this port's collections scope admits this declaration (directly, or through a " +
                 "pure-move flow from something it names), and a signature that moves without its " +
                 "call sites is a compile error one call away")
            // a retarget entry is per-library policy, so it may not read as the engine's own doing (§4.45).
            case scala.None if retargetKeysIn(s.info).nonEmpty =>
              val ks = retargetKeysIn(s.info).toList.sorted
              (Reason.Configured(name, ks.map(k => s"$k -> ${effectiveRetarget(k)}").mkString(", ")),
               "this port RETARGETS the type at every occurrence: the scala counterpart is usable " +
                 "wherever the java one was, so the declaration moves with no bridge and no " +
                 "call-shape change")
            case scala.None =>
              (Reason.Universal("collections-retype"),
               "a JDK collection type has a scala counterpart on every backend, and the JDK's own " +
                 "is on none of them")
          record(Decision(
            kind       = Decision.Kind.RetypedSignature,
            subject    = s.id,
            subjectFqn = s.fullName,
            detail = Map(
              // no key: where admittedBy supplied one, reason above already carries it.
              "from" -> TirPrinter.tpe(s.info, TirPrinter.Style.canonical),
              "to"   -> TirPrinter.tpe(now.info, TirPrinter.Style.canonical),
              "why"  -> why,
            ),
            reason = reason,
            origin = Decision.originOf(p, s.id),
          ))
          // order-keeping targets are catalog row JS-C42, discharged by the table rather than
          // per-site, since the same arm lowers every type reference.
          if mentionsOrderedShim(now.info) then
            cite(balticporter.catalog.JS.C(42), s.fullName)
      }
    }

  /** does this signature mention one of the two ordinal-order shims anywhere inside it? Read off
    * the phase's own mapping (§4.56) and walked with `StandardTraversal.mapType`, not a private
    * recursion that would miss a `MethodType`'s parameters. */
  private[transform] def mentionsOrderedShim(t: TypeRepr)(using Program): Boolean =
    val targets = Set(byScalaSym(CollectionsTransform.JavaEnumMapFqn),
                      byScalaSym(CollectionsTransform.JavaEnumSetFqn)) - SymId.None
    if targets.isEmpty then false
    else
      var found = false
      val scan = new Phase:
        def name = "ordered-shim-scan"
        override def transformType(x: TypeRepr)(using Program): TypeRepr =
          x match
            case TypeRepr.TypeRef(_, s) if targets.contains(s) => found = true
            case _                                             => ()
          x
      StandardTraversal.mapType(scan, t)
      found

  private[transform] def foldEntryCopyConstruction(b: Tree.Block)(using p: Program): Tree.Block =
    // scan stats for the pattern; build a new stats list with folded entries
    val newStats = scala.collection.mutable.ListBuffer.empty[Statement]
    var i = 0
    val stats = b.stats
    val len = stats.size
    var changed = false
    while i < len do
      stats(i) match
        case vd: Tree.ValDef if vd.rhs.isDefined =>
          // check if the variable's type head is an entry target (Tuple2)
          val isEntry = headSym(vd.tpt.tpe).exists(retargetEntryTargets.contains)
          if isEntry && i + 2 < len then
            // look for _1 and _2 assigns immediately following
            val (a1Opt, a2Opt) = (stats(i + 1), stats(i + 2)) match
              case (a1: Tree.Assign, a2: Tree.Assign) =>
                val a1Field = assignedEntryField(vd.symbol, a1)
                val a2Field = assignedEntryField(vd.symbol, a2)
                (a1Field, a2Field) match
                  case (Some(1), Some(2)) => (Some(a1.rhs), Some(a2.rhs))
                  case (Some(2), Some(1)) => (Some(a2.rhs), Some(a1.rhs))
                  case _                 => (scala.None, scala.None)
              case _ => (scala.None, scala.None)
            (a1Opt, a2Opt) match
              case (Some(rhs1), Some(rhs2)) =>
                // fold: replace the constructor's default args with the assign RHSes
                val newRhs = replaceConstructArgs(vd.rhs.get, rhs1, rhs2)
                newStats += vd.copy(rhs = Some(newRhs))
                i += 3 // skip the ValDef and both assigns
                changed = true
              case _ =>
                newStats += vd
                i += 1
          else
            newStats += vd
            i += 1
        case other =>
          newStats += other
          i += 1
    if changed then b.copy(stats = newStats.toList)
    else b

  /** is this assign writing to `_1` or `_2` of the given variable? */
  private[transform] def assignedEntryField(varSym: SymId, a: Tree.Assign): Option[Int] = a.lhs match
    case Tree.Select(Tree.Ident(`varSym`, _, _), m, _, _) =>
      if m == key1Sym then Some(1)
      else if m == value2Sym then Some(2)
      else scala.None
    case _ => scala.None

  /** replaces the first two arguments of a constructor/factory call with the given values. */
  private[transform] def replaceConstructArgs(rhs: Term, arg1: Term, arg2: Term): Term = rhs match
    case a @ Tree.Apply(fun, args, method, tpe, origin) if args.sizeIs >= 2 =>
      a.copy(args = arg1 :: arg2 :: args.drop(2))
    case other => other // should not happen for a retargetConstruct-produced Tuple2

  // -------------------------------------------------------------------------------------------
  // ---- Reified occurrences — instanceof/cast over retyped collections ----
  // // ENGINE-LIMITS K18

  /** Wrap an external FIELD whose class-file type is a mapped collection. Uses `declaredFieldHead`
    * (not the node type) to distinguish fields from methods. // ENGINE-LIMITS K15 */
  private[transform] def externalFieldProducer(sel: Tree.Select)(using p: Program): Term =
    if fromJavaSym == SymId.None || !externalCallee(sel.sym) then sel
    else declaredFieldHead(sel.sym) match
      case scala.None => sel
      case Some(_)    => headSym(sel.tpe).filter(liveWrappableSyms.contains) match
        case scala.None => sel
        case Some(_) if mentionsRetyped(sel.tpe) =>
          seam("external field (nested element)", "a one-level wrap",
               TirPrinter.tpe(sel.tpe, TirPrinter.Style.canonical), sel.origin, sel.sym)
          sel
        case Some(_) =>
          Tree.Apply(Tree.Ident(fromJavaSym, TypeRepr.NoType, sel.origin), List(sel),
                     fromJavaSym, sel.tpe, sel.origin)

  /** Wrap an external call whose result is a collection this phase retypes.
    * Guards: unowned callee, owner not in `typeMap`, node type is a `liveWrappable` target,
    * type args mention nothing retyped. // ENGINE-LIMITS K6, K15 */
  private[transform] def externalProducer(t: Tree.Apply)(using p: Program): Term =
    if fromJavaSym == SymId.None || !externalCallee(t.method) || instantiation(t) then t
    else headSym(t.tpe).filter(s => kindOf.contains(s) || shimSyms.contains(s)) match
      case scala.None => t
      // pass-through checked after collection-head filter
      case Some(_) if passesThrough(t) =>
        // unreadable signature = unverified pass-through (different residue from cannot-verify)
        if !signatureReadable(t) then
          seam("external result (unverified pass-through, no signature)",
               "a live scala view, IF the value was ever java's",
               TirPrinter.tpe(t.tpe, TirPrinter.Style.canonical), t.origin, t.method)
        t
      case Some(s) if !liveWrappableSyms.contains(s) =>
        seam("external result", "a live scala view", TirPrinter.tpe(t.tpe, TirPrinter.Style.canonical),
             t.origin, t.method)
        t
      case Some(_) if mentionsRetyped(t.tpe) =>
        seam("external result (nested element)", "a one-level wrap",
             TirPrinter.tpe(t.tpe, TirPrinter.Style.canonical), t.origin, t.method)
        t
      case Some(_) =>
        Tree.Apply(Tree.Ident(fromJavaSym, TypeRepr.NoType, t.origin), List(t), fromJavaSym, t.tpe, t.origin)

  /** True if this application is a `new` (constructor), not an external call.
    * Constructors hand back the object this program built, not a java value to wrap. */
  private[transform] def instantiation(t: Tree.Apply)(using p: Program): Boolean =
    t.fun.isInstanceOf[Tree.New] || p.symbolOf(t.method).exists(_.name == "<init>")

  /** True when the result type occurs on the input side (a generic pass-through).
    * Class file checked first; structural guess used only when no signature is readable. */
  private[transform] def passesThrough(t: Tree.Apply)(using p: Program): Boolean =
    !declaredResultIsMapped(t) && {
      val want = t.tpe
      // OCCURRENCE on both sides (argument and receiver), never equality on one alone — an
      // argument's TYPE ARGUMENT can pin the result too (TypeReference<Map<String,Object>>).
      want != TypeRepr.NoType && (
        t.args.exists(a => occursIn(want, a.tpe)) || (t.fun match
          case Tree.Select(recv, _, _, _) => occursIn(want, recv.tpe)
          case _                          => false))
    }

  /** Does the class file declare this callee's result to be a mapped collection? Read literally,
    * never through `remap` (§4.56) — `None` (no signature) answers `false`, leaving the
    * structural guess in charge. */
  private[transform] def declaredResultIsMapped(t: Tree.Apply)(using p: Program): Boolean =
    declaredResult(t).flatMap(headSym).flatMap(p.symbolOf).exists(s => typeMap.contains(s.fullName))

  /** the callee's DECLARED result type, where the class file could be read for one. */
  private[transform] def declaredResult(t: Tree.Apply)(using p: Program): Option[TypeRepr] =
    p.symbolOf(t.method).map(_.info).collect {
      case TypeRepr.MethodType(_, ret, _)                       => ret
      case TypeRepr.PolyType(_, TypeRepr.MethodType(_, ret, _)) => ret
    }

  /** True when the call reads a wildcard capture java answered with `Object` (JLS 4.4).
    * Structural: none of the standalone shims' members return bare `Object`.
    * // ENGINE-LIMITS G23, G33 */
  private[transform] def capturedObjectRead(t: Tree.Apply)(using p: Program): Boolean =
    def isObject(x: TypeRepr): Boolean =
      headSym(x).flatMap(p.symbolOf).exists(_.fullName == CollectionsTransform.ObjectFqn)
    isObject(t.tpe) && !declaredResult(t).exists(isObject)

  /** could the callee's class file be read for a signature at all? The two answers a suppression has
    * to be told apart by: a refusal the CLASS FILE licensed, and one resting on a GUESS. */
  private[transform] def signatureReadable(t: Tree.Apply)(using p: Program): Boolean =
    p.symbolOf(t.method).exists(_.info != TypeRepr.NoType)

  /** Is this a method the program does not declare, and not the collection API's own? Excludes
    * a minted symbol, a callee owned by a mapped type or its target, an owner-less symbol, and
    * one `[[handledStatic]]` covers (a REFUSED arm, kept under the JDK name — M6). */
  private[transform] def externalCallee(m: SymId)(using p: Program): Boolean =
    m != SymId.None && !ownedSym(m) && !mintedSyms.contains(m) &&
      p.symbolOf(m).exists(_.owner != SymId.None) &&
      !p.symbolOf(m).flatMap(c => p.symbolOf(c.owner))
        .exists(o => typeMap.contains(o.fullName) || retypedTargets.contains(o.fullName)) &&
      !handledStatic(m)

  /** The source half: a value PRODUCED by a call this phase refused to rewrite (`Arrays.asList`,
    * K6.5) — emitted text keeps the JDK name, node's `tpe` says `Buffer`. Read from the node
    * alone, [[coerce]] would name the wrapper instead of the boundary (K2.5). */
  private[transform] def refusedRewriteSource(t: Term)(using Program): Boolean = t match
    case a: Tree.Apply => handledStatic(a.method)
    case _             => false

  /** Does this type mention, inside its ARGUMENTS, a type this phase produced? The node is
    * already mapped, so a nested `java.util.List<java.util.List<String>>` reads
    * `Buffer[Buffer[String]]` — a one-level `asScala` would leave a stale inner `List`. Walked
    * with [[StandardTraversal.mapType]] (§3); head excluded, already established by the caller. */
  private[transform] def mentionsRetyped(t: TypeRepr)(using p: Program): Boolean = t match
    case TypeRepr.AppliedType(_, args) => args.exists { a =>
      var hit = false
      val scan = new Phase:
        def name = "external-nesting-scan"
        override def transformType(x: TypeRepr)(using pp: Program): TypeRepr =
          x match
            case TypeRepr.TypeRef(_, s) => if kindOf.contains(s) || shimSyms.contains(s) then hit = true
            case _                      => ()
          x
      StandardTraversal.mapType(scan, a)
      hit
    }
    case _ => false

  /** Count external-callee argument seams where no signature is readable (cannot-verify).
    * Where a signature IS readable, [[CollectionBoundaryCheck]] classifies it instead. */
  private[transform] def externalArgs(t: Tree.Apply)(using p: Program): Unit =
    if externalCallee(t.method) && p.symbolOf(t.method).forall(_.info == TypeRepr.NoType) then
      t.args.foreach { a =>
        headSym(a.tpe).filter(s => kindOf.contains(s) || shimSyms.contains(s)).foreach { _ =>
          seam("argument (external callee, no signature)", "unknown — the callee is a class file",
               TirPrinter.tpe(a.tpe, TirPrinter.Style.canonical), a.origin, t.method)
        }
      }
    opaqueEgress(t)

  /** record one external seam this phase could not close, for [[boundary]] to report. A refusal
    * that is not counted is indistinguishable from a seam that does not exist (M6). */
  private[transform] def seam(slot: String, expected: String, actual: String, origin: Origin, enclosing: SymId,
                   issue: CollectionBoundaryCheck.Issue = CollectionBoundaryCheck.Issue.ExternalCallee): Unit =
    externalSeams += CollectionBoundaryCheck.Finding(issue, slot, expected, actual, origin, enclosing)

  /** Bridge a scala collection into a shim-typed parameter, at the call site — `java.util.List`
    * becomes `Buffer`, `java.lang.Iterable` becomes [[JavaIterable]], and together they leave the
    * port unable to pass its own collections where java accepted `List` as `Iterable`. Both
    * obvious repairs are dead ends (K2): `given Conversion` never fires without an overload
    * match, and widening the parameter breaks iterate-and-remove bodies. */
  private[transform] def wrapIterableArgs(t: Tree.Apply)(using p: Program): Tree.Apply =
    // owned callee only (shim formals belong to emitted declarations, not class files)
    // not gated on `javaIterableSym` — `JavaCollection` half is independent
    if !ownedSym(t.method) || keepsJavaFormals(t) then t
    else
      val formals = instantiatedFormals(t, formalsOf(t))
      if formals.sizeIs != t.args.size then t
      else
        val as = t.args.zip(formals).map((a, f) => coerce(f, a))
        if as == t.args then t else t.copy(args = as)

  /** Substitute type variables in formals from this call's own argument types. Only
    * method-owned type parameters are bound (class parameters skipped). Parameterised formals
    * bind; bare type variables pass through unchanged. K26 */
  private[transform] def instantiatedFormals(t: Tree.Apply, formals: List[TypeRepr])(using p: Program): List[TypeRepr] =
    if formals.sizeIs != t.args.size then formals
    else
      val bound = collection.mutable.HashMap.empty[SymId, TypeRepr]
      // at a constructor the class's own parameters are bound too — fixed by the RECEIVER, and a
      // `new` has no receiver, so the instantiation is READ off the node's own type rather than
      // reconstructed (exact for a diamond the frontend already inferred)
      val ctorBound: Map[SymId, TypeRepr] =
        if !instantiation(t) then Map.empty
        else
          val owner = p.symbolOf(t.method).map(_.owner).getOrElse(SymId.None)
          (classTparams(owner), t.tpe) match
            case (ps, TypeRepr.AppliedType(tc, as))
              if ps.nonEmpty && ps.sizeIs == as.size && headSym(tc).contains(owner) =>
              ps.zip(as).toMap
            case _ => Map.empty
      def bindable(s: SymId): Boolean = p.symbolOf(s).exists(_.owner == t.method)
      // recursion through MATCHING HEADS only — an AppliedType with differing heads is a slot the
      // boundary lane already reports. First wins: a well-typed java call binds a variable once.
      def bind(f: TypeRepr, a: TypeRepr): Unit = (f, a) match
        case (TypeRepr.TypeRef(_, s), _) if bindable(s) && a != TypeRepr.NoType =>
          if !bound.contains(s) then bound(s) = a
        case (TypeRepr.AppliedType(ftc, fs), TypeRepr.AppliedType(atc, as))
          if headSym(ftc) == headSym(atc) && headSym(ftc).isDefined && fs.sizeIs == as.size =>
          fs.lazyZip(as).foreach(bind)
        case _ => ()
      formals.lazyZip(t.args).foreach {
        // a bare-reference formal is the slot being answered, never the binder
        case (_: TypeRepr.TypeRef, _) => ()
        case (f, a)                   => bind(f, a.tpe)
      }
      if bound.isEmpty && ctorBound.isEmpty then formals
      else formals.map {
        case f @ TypeRepr.TypeRef(_, s) if bindable(s)    => bound.getOrElse(s, f)
        case f @ TypeRepr.TypeRef(_, s)                   => ctorBound.getOrElse(s, f)
        case other                                        => other
      }

  /** The type parameters a class declares, in order, or `Nil` for one the program does not
    * declare (a class-file fact, §4.56, not bindable here either way). */
  private[transform] def classTparams(owner: SymId)(using p: Program): List[SymId] =
    classDefsBySym.get(owner).map(_.tparams.map(_.symbol)).getOrElse(Nil)

  /** Does this call's callee keep java formals — i.e. is its signature one this phase did not
    * and cannot move? Three cases: the callee is a declaration this run's scope held back; the
    * receiver resolves through a held-back declaration to a java collection; or the callee is a
    * genuine external seam ([[externalCallee]]). Not "not owned" alone: a refused `super.putAll`
    * bridged anyway would name the helper instead of the member it could not rewrite (K6.5). */
  private[transform] def keepsJavaFormals(t: Tree.Apply)(using Program): Boolean =
    literal(t.method) || externalCallee(t.method) || (t.fun match
      case Tree.Select(recv, _, _, _) => actualOf(recv)._2
      case _                          => false)

  /** the callee's declared formals, or `Nil` where it has none. */
  private[transform] def formalsOf(t: Tree.Apply)(using p: Program): List[TypeRepr] =
    p.symbolOf(t.method).map(_.info).collect {
      case TypeRepr.MethodType(ps, _, _)                       => ps.map(_._2)
      case TypeRepr.PolyType(_, TypeRepr.MethodType(ps, _, _)) => ps.map(_._2)
    }.getOrElse(Nil)

  /** The consumer half of the external seam — a value this phase retyped, at a formal it did
    * not and cannot (a class file's, K15, or a held-back declaration's). Bridged with a live
    * `JavaCollections.toJava` view. Runs where the seam count runs — on a call nothing else
    * rewrote — never in `wrapIterableArgs`, since a `java.util.*` formal may belong to a method
    * this phase is about to RETARGET, and bridging first would hand the rewritten call a wrapped
    * argument its new target does not want (measured: 8 specs the first time merged). */
  private[transform] def bridgeJavaFormals(t: Tree.Apply)(using p: Program): Tree.Apply =
    if !keepsJavaFormals(t) then t
    else
      val formals = formalsOf(t)
      if formals.sizeIs != t.args.size then t
      else
        // an Object formal bridges only at a CLASS FILE's slot — a scoped-out or held-back
        // declaration's own body keeps expecting what it always did
        val external = externalCallee(t.method)
        // a declared reflective sink reads the runtime representation it is handed (K21 face 1);
        // asked here and not in coerce, since the sink is a fact about the CALLEE
        val sink = if external then sinkOf(t.method) else scala.None
        val as = t.args.zip(formals).map((a, f) =>
          coerce(f, a, expectedScoped = true, expectedExternal = external,
                 expectedSink = sink.isDefined))
        if as != t.args then sink.foreach(fqn => bridgedSinkCallees += (t.method -> fqn))
        if as == t.args then t else t.copy(args = as)

  /** Bridge a scala collection into a shim-typed slot (argument, val, assignment, return).
    * Wraps only when the source is a scala collection this phase introduced (`kindOf`).
    * `Kind.Map` into `JavaCollection` is refused (java `Map` is not a `Collection`).
    * Shims are excluded on both sides. // ENGINE-LIMITS M6 */
  private[transform] def coerce(expected: TypeRepr, actual: Term, expectedScoped: Boolean = false,
                     expectedExternal: Boolean = false, expectedSink: Boolean = false)(using p: Program): Term =
    // the symbol table is retyped AFTER the trees, so a formal read here is still java's
    // original symbol; compare through `remap`. A scoped-out side is taken literally (no factory
    // matches, left for CollectionBoundaryCheck to count as ScopedOut).
    def scalaSym(x: SymId, scoped: Boolean): SymId = if scoped then x else remap.getOrElse(x, x)
    // a conditional's conversion belongs to its branches (JLS 15.25): recurse through this
    // function per-branch rather than around the whole If, identity-preserving where unmoved.
    actual match
      case i: Tree.If =>
        val th = coerce(expected, i.thenp, expectedScoped, expectedExternal, expectedSink)
        val el = coerce(expected, i.elsep, expectedScoped, expectedExternal, expectedSink)
        return if (th ne i.thenp) || (el ne i.elsep) then i.copy(thenp = th, elsep = el) else i
      case _ => ()
    val (actualT, actualScoped) = actualOf(actual)
    val wants = headSym(expected).map(scalaSym(_, expectedScoped))
    val got   = headSym(actualT).map(scalaSym(_, actualScoped))
    // where the value is a type the PROGRAM declares, kindOf says nothing (keyed on this
    // phase's own scala targets); mintedSourceKind reads the minted ancestry instead. K26
    val from  = got.filterNot(shimSyms.contains)
                   .flatMap(g => kindOf.get(g).orElse(mintedSourceKind(g, wants)))
    // the slot that is literally a java collection (K15): expectedScoped means the expected
    // side is a scope hold-back or an external callee's formal, so a retyped value meets a
    // java.util.* that stayed and the wrap goes the other way.
    val wantsJava = expectedScoped &&
      wants.flatMap(p.symbolOf).exists(o => typeMap.contains(o.fullName))
    // the slot with no type error behind it: an Object formal takes anything, so a retyped
    // collection conforms silently while reflective third-party code sees the wrong shape.
    // toJava is faithful — java's value there really was a java collection. External only.
    val wantsUniversal = expectedExternal &&
      wants.flatMap(p.symbolOf).exists(_.fullName == CollectionsTransform.ObjectFqn)
    // the slot this phase's own stream collapse creates: where the chain's terminal crosses
    // back out to java at a Stream formal, toStream is the faithful answer. External only.
    val wantsStream = expectedExternal &&
      wants.flatMap(p.symbolOf).exists(_.fullName == CollectionsTransform.StreamFqn)
    // guards against an absent shim (SymId.None) matching an unresolved-head `wants` by accident.
    def wantsIs(s: SymId) = s != SymId.None && wants.contains(s)
    val factory = from match
      case _ if wants.isEmpty || refusedRewriteSource(actual) => SymId.None
      // the egress bridge (K21 face 1), ahead of every arm below: a declared reflective sink
      // walks the whole tree where toJava is only one level. Fired on the formal, not on `from`.
      case _ if expectedSink && wantsUniversal && toJavaValueSym != SymId.None => toJavaValueSym
      // Kind.Stack rides with Kind.Seq here: JavaStack extends mutable.ArrayBuffer, so at a
      // boundary slot it IS a Kind.Seq value and conforms to both bridges. K2.5
      case Some(Kind.Seq | Kind.Stack | Kind.Set | Kind.Map) if wantsIs(javaIterableSym) => iterableFromSym
      case Some(Kind.Seq | Kind.Stack)          if wantsIs(javaCollectionSym)  => collectionFromSym
      case Some(Kind.Set)                       if wantsIs(javaCollectionSym)  => collectionFromSetSym
      // asJava converts one level, so a nested Buffer[Buffer[...]] would lie one arg in — refused and counted.
      case Some(Kind.Seq | Kind.Stack | Kind.Set | Kind.Map)
        if (wantsJava || wantsUniversal || wantsStream) && mentionsRetyped(actualT)     => SymId.None
      case Some(Kind.Seq | Kind.Stack | Kind.Set | Kind.Map)
        if (wantsJava || wantsUniversal) && toJavaSym != SymId.None                     => toJavaSym
      // a Stream formal takes the collapse's result back to java; Kind.Map excluded (no stream() on java Map).
      case Some(Kind.Seq | Kind.Stack | Kind.Set) if wantsStream && toStreamSym != SymId.None => toStreamSym
      // the retained parent's own slot (K5.7): a class keeping java's Map.Entry meets the
      // Tuple2 slot every use of that interface got. Decided in detachedEntriesIn, never here.
      case _ if entryToPairSym != SymId.None &&
                got.flatMap(detachedEntries.get).exists(tgt =>
                  wants.flatMap(p.symbolOf).exists(_.fullName == tgt))            => entryToPairSym
      // a retarget target's iterator() returns scala.collection.Iterator[T], but JavaIterator[T]
      // is expected (java.util.Iterator redirect) — wrap with JavaIterator.from, compared by FQN.
      case _ if wantsIs(javaIteratorSym) && iteratorFromSym != SymId.None &&
               got.flatMap(p.symbolOf).exists(_.fullName == "scala.collection.Iterator") => iteratorFromSym
      case _                                                                          => SymId.None
    if factory == SymId.None then
      // retarget coercion: a §1(b) parameterised boundary wrap between a retarget target and
      // its expected type via a `retargetCoercions` template, keyed (actual FQN, expected FQN).
      if retargetCoercions.nonEmpty then
        val gotFqn   = got.flatMap(p.symbolOf).map(_.fullName)
        val wantsFqn = wants.flatMap(p.symbolOf).map(_.fullName)
        (gotFqn, wantsFqn) match
          case (Some(gf), Some(wf)) if retargetCoercions.contains((gf, wf)) =>
            val template = retargetCoercions((gf, wf))
            renderRetargetCoercion(template, actual, expected, actual.origin)
          case _ => actual
      else actual
    else
      // typed as the RETYPED expected type, not the one read above (the symbol table retypes
      // after the trees) — else this node would claim a java type the port no longer produces. K6
      val tpe = wants.map(withHead(expected, _)).getOrElse(expected)
      Tree.Apply(Tree.Ident(factory, TypeRepr.NoType, actual.origin), List(actual),
                 factory, tpe, actual.origin)

  /** Per-class `MintedParents`, read from original units (before traversal moves parents),
    * transitive over program-declared parents; scoped-out/uninheritable/unmapped parents
    * excluded, standalone targets recorded in `shims`. */
  private[transform] def declaredParentKinds(p: Program): Map[SymId, MintedParents] =
    given Program = p
    def tpeOf(x: Term | TypeTree): TypeRepr = x match
      case t: TypeTree => t.tpe
      case t: Term     => t.tpe
    val classes = p.units.flatMap(StandardTraversal.allClassDefs)
    val anons   = p.units.flatMap(StandardTraversal.allAnonClasses)
    /** (what this type extends, what type parameters it declares) — for both class bodies and
      * anonymous classes (one parent, written at the `new`, no type parameters). */
    val shapeOf: Map[SymId, (List[TypeRepr], List[SymId])] =
      classes.map(cd => cd.symbol -> (cd.parents.map(tpeOf), cd.tparams.map(_.symbol))).toMap ++
        anons.map((a, tpt) => a.symbol -> (List(tpt.tpe), Nil))
    val memo    = collection.mutable.Map.empty[SymId, MintedParents]

    def resolve(id: SymId, seen: Set[SymId]): MintedParents =
      memo.getOrElse(id, {
        val out = shapeOf.get(id) match
          case _ if seen(id) || excluded.contains(id) || uninheritableSyms.contains(id) =>
            MintedParents(Set.empty, Nil, Nil, Set.empty)
          case scala.None => MintedParents(Set.empty, Nil, Nil, Set.empty)
          case Some((parents, tparams)) =>
            val heads = parents.flatMap(tp => headSym(tp).map(_ -> tp))
            val targets = heads.flatMap { (h, tp) =>
              p.symbolOf(h).flatMap(s => typeMap.get(s.fullName)).map(_ -> tp)
            }.filterNot { case ((tgt, _), _) => CollectionsTransform.UninheritableTargets(tgt) }
            val mapped = targets.collect {
              case ((tgt, k), tp) if !CollectionsTransform.standaloneTargets(tgt) => k -> firstTypeArg(tp)
            }
            // …this class's OWN mapped clauses, and its ANCESTORS' read THROUGH the clause that
            // names them. An inherited clause with the ancestor's own variables in it is the
            // an ancestor's clause must arrive substituted into THIS class's own type variables
            // or the emitted signature names types out of scope (§4.56); ParentSubst does it.
            val declared = targets.collect {
              case ((tgt, k), tp) if !CollectionsTransform.standaloneTargets(tgt) => k -> typeArgs(tp)
            } ++ heads.flatMap { (h, tp) =>
              if !shapeOf.contains(h) then Nil
              else
                val formals = shapeOf.get(h).map(_._2).getOrElse(Nil)
                val actuals = typeArgs(tp)
                val sub = if formals.sizeIs == actuals.size then formals.zip(actuals).toMap
                          else Map.empty[SymId, TypeRepr]
                resolve(h, seen + id).declared.map((k, as) => k -> as.map(ParentSubst.subst(_, sub)))
            }
            val shimParents = targets.collect {
              case ((tgt, _), tp) if CollectionsTransform.standaloneTargets(tgt) => tgt -> tp
            }
            val kindParents = targets.collect {
              case ((tgt, k), tp) if !CollectionsTransform.standaloneTargets(tgt) => (tgt, k, tp)
            }
            val above  = heads.map(_._1).filter(shapeOf.contains).map(resolve(_, seen + id))
            val scalas = targets.collect {
              case ((tgt, _), _) if !CollectionsTransform.standaloneTargets(tgt) => tgt
            }.toSet
            // the duplicate relation among this class's OWN clauses only (K28.1) — an inherited
            // kind's element type is the ancestor's, unsubstituted; reading it here would guess.
            val subsumed = shimParents.flatMap { (sh, shTpe) =>
              kindParents.collectFirst {
                case (tgt, k, kTpe) if CollectionsTransform.SubsumesShim.get(k.toString).exists(_(sh)) &&
                                       carriesElement(k, kTpe, shTpe) => sh -> tgt
              }
            }.toMap
            val shims = shimParents.map(_._1).toSet -- subsumed.keySet
            MintedParents(mapped.map(_._1).toSet ++ above.flatMap(_.kinds),
                          mapped.flatMap(_._2) ++ above.flatMap(_.probes),
                          tparams,
                          shims ++ above.flatMap(_.shims),
                          scalas ++ above.flatMap(_.targets),
                          subsumed,
                          declared)
        memo(id) = out
        out
      })

    shapeOf.keys.map(id => id -> resolve(id, Set.empty))
      .filter((_, mp) => mp.kinds.nonEmpty || mp.shims.nonEmpty || mp.subsumed.nonEmpty).toMap

  private[transform] def firstTypeArg(t: TypeRepr): Option[TypeRepr] = t match
    case TypeRepr.AppliedType(_, a :: _) => Some(a)
    case _                               => scala.None

  /** every type argument the clause writes — `Nil` for a raw one, since inventing `Object` for
    * a raw `implements Map` would be §4.6's fabricated fact. */
  private[transform] def typeArgs(t: TypeRepr): List[TypeRepr] = t match
    case TypeRepr.AppliedType(_, as) => as
    case _                           => Nil

  /** Does the kind parent really iterate what the shim parent says this class iterates?
    * `SubsumesShim` claims a `scala.collection` target answers for `JavaIterable`'s one member,
    * but only where the two clauses agree on the element (`implements Map<K,V>,
    * Iterable<Map.Entry<K,V>>` agrees; `Iterable<String>` does not). Asked in java's own types,
    * since [[declaredParentKinds]] reads the original units. Declines on a raw clause or an
    * arity this table has no row for, leaving the duplicate parent and scalac's own `E164`.
    */
  private[transform] def carriesElement(k: Kind, kindParent: TypeRepr, shimParent: TypeRepr)(using p: Program): Boolean =
    def entryOf(t: TypeRepr): Option[(TypeRepr, TypeRepr)] = t match
      case TypeRepr.AppliedType(_, a :: b :: Nil) =>
        headSym(t).flatMap(p.symbolOf).map(_.fullName)
          .filter(fqn => typeMap.get(fqn).exists((_, ek) => ek == Kind.Entry))
          .map(_ => (a, b))
      case _ => scala.None
    (k, kindParent, firstTypeArg(shimParent)) match
      case (Kind.Map, TypeRepr.AppliedType(_, kk :: vv :: Nil), Some(el)) =>
        entryOf(el).contains((kk, vv))
      case (Kind.Seq | Kind.Set | Kind.Stack, TypeRepr.AppliedType(_, e :: Nil), Some(el)) =>
        el == e
      case _ => false

  /** Classes with a retained (uninheritable) parent where every unsupported member throws first,
    * allowing a copy-projection to `Tuple2`. Read from ORIGINAL units (not mapped, to avoid
    * this phase's own refuseOnTarget licensing its own projection). // ENGINE-LIMITS K5.7 */
  private[transform] def detachedEntriesIn(p: Program): Map[SymId, String] =
    if uninheritableSyms.isEmpty then Map.empty
    else
      given Program = p
      def tpeOf(x: Term | TypeTree): TypeRepr = x match
        case t: TypeTree => t.tpe
        case t: Term     => t.tpe
      val classes = p.units.flatMap(StandardTraversal.allClassDefs)
      val byId    = classes.map(cd => cd.symbol -> cd).toMap
      def parentsOf(cd: Tree.ClassDef): List[SymId] = cd.parents.flatMap(x => headSym(tpeOf(x)))

      // the uninheritable TARGET this class's ancestry reaches, if any. A cycle takes the empty arm
      // at the repeat — the conservative direction here, since it only ever declines a projection.
      def targetOf(id: SymId, seen: Set[SymId]): Option[String] =
        if seen(id) then scala.None
        else byId.get(id).flatMap { cd =>
          parentsOf(cd).iterator.flatMap { h =>
            p.symbolOf(h).flatMap(s => typeMap.get(s.fullName)).map(_._1)
              .filter(CollectionsTransform.UninheritableTargets)
              .orElse(targetOf(h, seen + id))
          }.nextOption()
        }

      // the NEAREST declaration of one signature, self before parents, and only one with a body:
      // an abstract re-declaration says nothing about what an implementor does.
      def nearest(id: SymId, sig: CollectionsTransform.MemberSig, seen: Set[SymId]): Option[Tree.DefDef] =
        if seen(id) then scala.None
        else byId.get(id).flatMap { cd =>
          cd.body.collectFirst {
            case d: Tree.DefDef
              if d.rhs.nonEmpty && d.paramss.map(_.size).sum == sig.arity &&
                 p.symbolOf(d.symbol).exists(_.name == sig.name) => d
          }.orElse(parentsOf(cd).iterator.flatMap(nearest(_, sig, seen + id)).nextOption())
        }

      classes.flatMap { cd =>
        targetOf(cd.symbol, Set.empty).filter { tgt =>
          val sigs = CollectionsTransform.UnsupportedOnTarget.getOrElse(tgt, Set.empty)
          sigs.nonEmpty && sigs.forall(sig =>
            nearest(cd.symbol, sig, Set.empty).flatMap(_.rhs).exists(throwsFirst))
        }.map(cd.symbol -> _)
      }.toMap

  /** does this body THROW before it does anything else? The capability test [[detachedEntriesIn]]
    * rests on, and it is asked of the first statement rather than of the whole body, because that is
    * exactly the property that makes a write impossible — anything after an unconditional throw is
    * unreachable. A conditional throw answers `false`: java's own `setValue` may refuse for one
    * receiver state and write for another, and that class writes through. */
  private[transform] def throwsFirst(t: Term): Boolean = t match
    case _: Tree.Throw     => true
    case b: Tree.Block     => b.stats.headOption match
      case Some(s: Term) => throwsFirst(s)
      case Some(_)       => false
      case scala.None    => throwsFirst(b.expr)
    case c: Tree.Commented => c.stmt match
      case s: Term => throwsFirst(s)
      case _       => false
    case _ => false

