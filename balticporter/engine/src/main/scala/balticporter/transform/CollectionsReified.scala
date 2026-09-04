package balticporter.transform

import balticporter.tir.*

/** REIFIED positions (K18/K20/K21: instanceof/cast over both representations, reified type-argument carriers, reflective-sink egress bridging) split out of CollectionsTransform (context diet S3). */
private[transform] trait CollectionsReified:
  self: CollectionsTransform =>

  /** the mapping targets `JavaCollections.fromJava` can actually PRODUCE — see
    * [[CollectionsTransform.liveWrappable]], read as symbols so [[externalProducer]] asks a
    * membership question about what this run minted rather than a question about a name. EMPTY when
    * the program names none of them, which makes the wrap arm decline by arithmetic. */
  private[transform] var liveWrappableSyms: Set[SymId] = Set.empty

  /** each mapping target this run named → the `JavaCollections.Reified` member that answers java's
    * `instanceof` / performs java's cast at it. Keyed on `byScala`, so a target the program never
    * names is simply absent and the reified arms decline by arithmetic — the same shape
    * [[liveWrappableSyms]] takes, and for the same reason (§4.56: the phase's own record). */
  private[transform] var reifiedIsSyms, reifiedAsSyms: Map[SymId, SymId] = Map.empty

  /** every symbol THIS PROGRAM names that is an unmapped SUPERTYPE of a type this phase retypes —
    * see [[unmappedReified]]. EMPTY where the program names none of them, which makes the refusal
    * decline by arithmetic exactly as the two maps above do. */
  private[transform] var unmappedSupertypeSyms: Set[SymId] = Set.empty

  /** did a REIFIED occurrence get translated inside the declaration currently being closed? The
    * traversal is bottom-up, so this is set at the rewrite and drained at the nearest enclosing
    * `DefDef`/`ValDef` — a citation is per DECLARATION (§5.1). */
  private[transform] var reifiedHere: Boolean = false

  // ---- the RuleScope's own record, for THIS run (see `applyScope`) ----

  /** the carriers that actually RUN — what the port declared plus the one java guarantees. A `val`,
    * so [[preservesTypeArgsOf]], the recorder and the fingerprint cannot disagree. */
  private[transform] val effectiveCarriers: Set[String] = reifiedCarriers ++ CollectionsTransform.UniversalCarriers

  /** …resolved to THIS program's symbols, once per run. Read off `program.symbols` and not off the
    * mapping, for the reason [[unmappedSupertypeSyms]] states: a carrier is a type this phase leaves
    * alone, so its symbol keeps the id it arrived with. EMPTY where the program names none of them,
    * which is what makes every arm below a no-op by arithmetic. */
  private[transform] var carrierSyms: Set[SymId] = Set.empty

  /** the REFLECTIVE SINKS this program actually names, as this program's own symbols. EMPTY where
    * the port declares none, which is what makes the egress bridge a no-op with no code path. */
  private[transform] var sinkSyms: Set[SymId] = Set.empty

  /** `JavaCollections.Reified.toJavaValue` — the EGRESS bridge (K21 face 1). Minted like every
    * other `Reified` member: nothing in a java program declares it. */
  private[transform] var toJavaValueSym: SymId = SymId.None

  /** every (sink callee, declared sink FQN) the egress bridge actually fired on, drained into
    * `decisions.tsv` at the end of the run. A per-SITE rewrite recorded per DECLARATION (§5.1). */
  private[transform] val bridgedSinkCallees = collection.mutable.Set[(SymId, String)]()

  /** …and every external callee with an OPAQUE formal this port has NOT declared a sink, keyed by
    * (callee, JAVA FILE) with the earliest site in that file — the review list [[opaqueEgress]]
    * exists to publish. Keyed per-file, not globally, so D2's per-module filter applies AFTER the
    * site is chosen and a dependent's row does not vanish behind its base's earlier path. */
  private[transform] val opaqueEgressSites = collection.mutable.Map[(SymId, String), Origin]()

  /** Records why a reified type-argument (K20) at a declaration was preserved rather than retyped,
    * one row per declaration. `Universal` for `java.lang.Class`, `Configured` for a port-declared
    * carrier. Only fires where the argument mentions a type this phase maps — an untouched carrier
    * decided nothing and would be noise. // CLAUDE.md §4.56, K20 */
  private[transform] def recordReifiedTypeArgs(after: SymbolTable)(using p: Program): Unit =
    if carrierSyms.isEmpty then return
    after.all.foreach { s =>
      if Decision.isDeclaration(p, s) then
        val hits = preservedCarrierArgs(s.info)
        if hits.nonEmpty then
          val carriers = hits.map(_._1).distinct.sorted
          val (reason, why) = carriers.filterNot(CollectionsTransform.UniversalCarriers) match
            case Nil =>
              (Reason.Universal("reified-type-arg"),
               "`java.lang.Class` is reified by java itself, so this argument names the class the " +
                 "JVM will be asked for at run time and not a slot — retyped, it would name a " +
                 "scala type no class file has")
            case declared =>
              (Reason.Configured(name, declared.mkString(", ")),
               "this port declares the carrier as one whose type arguments a third party reads " +
                 "back out of the class file's generic signature and CONSTRUCTS from, so the " +
                 "argument stays java's and the value is bridged where it is used")
          record(Decision(
            kind       = Decision.Kind.ReifiedTypeArg,
            subject    = s.id,
            subjectFqn = s.fullName,
            detail = Map(
              "carrier" -> carriers.mkString(","),
              "kept"    -> hits.map((_, a) => TirPrinter.tpe(a, TirPrinter.Style.canonical)).distinct.sorted.mkString(","),
              "why"     -> why,
            ),
            reason = reason,
            origin = Decision.originOf(p, s.id),
          ))
    }

  /** Records the egress bridge (K21 face 1) at each declaration handing a value to a declared
    * reflective sink, keyed on the sink FQN. No porter note: the emitted call already names the
    * bridge. // CLAUDE.md §4.56, K21 */
  private[transform] def recordEgressBridges()(using p: Program): Unit =
    bridgedSinkCallees.toList.sortBy((m, fqn) => (fqn, m.raw)).foreach { (callee, sink) =>
      val calleeName = p.symbolOf(callee).map(_.fullName).getOrElse("?")
      Decision.declarationsUsing(p, callee).foreach { (encl, origin) =>
        record(Decision(
          kind       = Decision.Kind.BridgedEgress,
          subject    = encl,
          subjectFqn = Decision.fqnOf(p, encl, calleeName),
          detail = Map(
            "sink"   -> sink,
            "callee" -> calleeName,
            "why"    -> ("this port declares the callee's owner as a type that reads the RUNTIME " +
              "representation of what it is handed, so a collection this phase retyped is " +
              "presented as java's at the call — the formal is `java.lang.Object`, which is why " +
              "the port compiles either way and nothing static can see the difference"),
          ),
          reason = Reason.Configured(name, sink),
          origin = origin,
        ))
      }
    }

  /** Every (carrier FQN, preserved argument) pair in a signature whose argument mentions a type this
    * phase maps. Walked with [[StandardTraversal.mapType]], never a private recursion. // CLAUDE.md §3 */
  private[transform] def preservedCarrierArgs(t: TypeRepr)(using Program): List[(String, TypeRepr)] =
    val hits = collection.mutable.ListBuffer[(String, TypeRepr)]()
    val scan = new Phase:
      def name = "reified-carrier-scan"
      override def transformType(x: TypeRepr)(using p: Program): TypeRepr =
        x match
          case TypeRepr.AppliedType(tc, as) =>
            for
              h <- headSym(tc).toList if carrierSyms.contains(h)
              fqn <- p.symbolOf(h).map(_.fullName).toList
              a <- as if mentionsMapped(a)
            do hits += (fqn -> a)
          case _ => ()
        x
    StandardTraversal.mapType(scan, t)
    hits.toList

  /** java's `x instanceof T` where this phase retyped `T`. */
  private[transform] def reifiedTest(t: Tree.InstanceOf)(using p: Program): Term =
    reifiedTarget(t.tpt.tpe) match
      case scala.None => unmappedReified("reified type test", t.tpt.tpe, t.origin); t
      case Some(tgt)  => reifiedIsSyms.get(tgt) match
        case Some(f) =>
          reifiedHere = true
          Tree.Apply(Tree.Ident(f, TypeRepr.NoType, t.origin), List(t.expr), f, t.tpe, t.origin)
        case scala.None =>
          reifiedSeam("reified type test", t.tpt.tpe, t.origin)
          t

  /** java's `(T) x` where this phase retyped `T` and cannot vouch for what `x` produces. The
    * cast is KEPT and the coercion goes inside it — exact rather than tidy, since java's cast to a
    * generic type is unchecked in its type arguments (JLS 5.5), which the surviving `asInstanceOf`
    * expresses, while the coercion answers only the erased class java checked. */
  private[transform] def reifiedCast(t: Tree.Typed)(using p: Program): Term =
    // asked before vouched deliberately: vouched says the phase knows the value's own
    // representation, which at a target outside the mapping makes the divergence certain.
    if !isNullLiteral(t.expr) && reifiedTarget(t.tpt.tpe).isEmpty then
      unmappedReified("reified cast", t.tpt.tpe, t.origin)
    if vouched(t.expr) || isNullLiteral(t.expr) then t
    else reifiedTarget(t.tpt.tpe) match
      case scala.None => t
      case Some(tgt)  => reifiedAsSyms.get(tgt) match
        case Some(f) =>
          reifiedHere = true
          t.copy(expr = Tree.Apply(Tree.Ident(f, TypeRepr.NoType, t.origin), List(t.expr), f,
                                   t.tpt.tpe, t.origin))
        case scala.None =>
          reifiedSeam("reified cast", t.tpt.tpe, t.origin)
          t

  /** the head symbol of a type THIS PHASE produced, or `None` (§4.56, asked of the phase's own
    * tables, never a name) — nothing in java source names a scala collection or a runtime shim. */
  private[transform] def reifiedTarget(t: TypeRepr): Option[SymId] =
    headSym(t).filter(s => kindOf.contains(s) || shimSyms.contains(s))

  /** Can this phase vouch for the representation this expression produces? Yes for a
    * declaration it retyped or one the program owns; no for an external producer (K15). */
  private[transform] def vouched(e: Term)(using p: Program): Boolean =
    (reifiedTarget(e.tpe).isDefined || headSym(e.tpe).exists(p.owns)) && !foreignProducer(e)

  /** the one exception: a call or field read the program does not declare (`externalCallee`, K15). */
  private[transform] def foreignProducer(e: Term)(using p: Program): Boolean = e match
    case a: Tree.Apply  => externalCallee(a.method)
    case s: Tree.Select => externalCallee(s.sym)
    case _              => false

  /** `null` is an instance of nothing and a cast of it checks nothing — no runtime object for a
    * reified question. Left as-is, keeping `null.asInstanceOf[T]` recognisable downstream. */
  private[transform] def isNullLiteral(e: Term): Boolean = e match
    case Tree.Literal(Constant.NullC, _, _) => true
    case _                                  => false

  /** a reified occurrence at a target no live view can BE. Refused and counted (M6). */
  /** drains [[reifiedHere]] at the declaration the rewrite happened in. */
  private[transform] def citeIfReified(sym: SymId)(using p: Program): Unit =
    if reifiedHere then
      cite(balticporter.catalog.JS.G(48), p.symbolOf(sym).map(_.fullName).getOrElse(sym.toString))
      reifiedHere = false

  private[transform] def reifiedSeam(slot: String, target: TypeRepr, origin: Origin)(using Program): Unit =
    seam(slot, "a representation-agnostic test or coercion",
         TirPrinter.tpe(target, TirPrinter.Style.canonical), origin, SymId.None,
         CollectionBoundaryCheck.Issue.ReifiedOccurrence)

  /** reified occurrence at an unmapped JDK supertype (e.g. `RandomAccess`). Refused and
    * counted, derived from `typeMap`'s supertype closure. K18 */
  private[transform] def unmappedReified(slot: String, target: TypeRepr, origin: Origin)(using Program): Unit =
    if headSym(target).exists(unmappedSupertypeSyms) then
      seam(slot, "no coercion exists: the target is OUTSIDE the mapping, and a retyped value is not one",
           TirPrinter.tpe(target, TirPrinter.Style.canonical), origin, SymId.None,
           CollectionBoundaryCheck.Issue.ReifiedOccurrence)

  /** Rewrites `Collections.EMPTY_LIST/SET/MAP` to the typed `emptyList()`/etc. helper.
    * Consulted ahead of [[externalFieldProducer]] to avoid wrapping a raw type. */

  /** A cast to a runtime shim whose source this phase retyped OUT of the shim family — no value
    * can satisfy it, since the phase guaranteed the runtime value is a scala collection. Decided
    * from `remap`/`kindOf` (the phase's own record), never from the source type's name — a prefix
    * test swept up `java.lang.Object` and broke an ordinary downcast the phase never touched
    * (§4.56). Dropping the cast also lets `coerce` see and bridge the argument properly. */
  private[transform] def impossibleShimCast(t: Tree.Typed): Boolean =
    def scalaSym(s: SymId) = remap.getOrElse(s, s)
    val to   = headSym(t.tpt.tpe).map(scalaSym)
    val from = headSym(t.expr.tpe).map(scalaSym)
    to.exists(shimSyms.contains) && from.exists(f => !shimSyms.contains(f) && kindOf.contains(f))

  /** The seam with nothing type-wrong: a `java.lang.Object` formal on an external callee takes a
    * retyped value, and the port compiles, but the callee's `toString`/`instanceof`/serialiser see
    * something different. Only the port knows which such callees READ the representation
    * ([[reflectiveSinks]]); this is the review list that makes a missing entry visible
    * (K21 face 1). Deduplicated by CALLEE, not by site — a declared sink is bridged and skipped. */
  private[transform] def opaqueEgress(t: Tree.Apply)(using p: Program): Unit =
    if !externalCallee(t.method) || sinkOf(t.method).isDefined then return
    val formals = formalsOf(t)
    def objectTyped(x: TypeRepr) =
      headSym(x).flatMap(p.symbolOf).exists(_.fullName == CollectionsTransform.ObjectFqn)
    val opaque =
      if formals.sizeIs == t.args.size then
        // the argument is one the phase cannot rule out: a retyped value, or one whose static
        // type is Object and says nothing
        t.args.zip(formals).exists((a, f) => objectTyped(f) && mayBeRetypedValue(a))
      else
        // no readable signature, so no formal to ask — held to an Object-typed argument
        t.args.exists(a => objectTyped(a.tpe))
    if opaque then
      // earliest site PER JAVA FILE, never per callee — a base's site would otherwise win the
      // minimum for the whole program and a dependent's row would silently disappear
      val key  = t.method -> t.origin.javaPath
      val prev = opaqueEgressSites.get(key)
      if prev.forall(o => t.origin.line < o.line) then opaqueEgressSites(key) = t.origin

  /** The same bridge where there is no formal to read — a generic external method has no
    * readable `MethodType`, so [[bridgeJavaFormals]]'s arity test declines. For an ordinary
    * external callee that refusal is the honest answer and counted; for a DECLARED SINK it is
    * not, since the port already stated the fact the signature would have carried — the argument
    * decides instead: a retyped value or `java.lang.Object`. Measured: with the arity path alone,
    * one of liqp's seven sink sites bridged. */
  private[transform] def bridgeSinkArgs(t: Tree.Apply)(using p: Program): Tree.Apply =
    if formalsOf(t).sizeIs == t.args.size || !externalCallee(t.method) then t
    else sinkOf(t.method) match
      case scala.None => t
      case Some(fqn) =>
        val as = t.args.map(a =>
          if !mayBeRetypedValue(a) || toJavaValueSym == SymId.None then a
          else Tree.Apply(Tree.Ident(toJavaValueSym, TypeRepr.NoType, a.origin), List(a),
                          toJavaValueSym, objectTpe(a), a.origin))
        if as == t.args then t
        else
          bridgedSinkCallees += (t.method -> fqn)
          t.copy(args = as)

  /** The declared reflective sink this callee belongs to, by its OWNER — the phase's own policy
    * read as symbols, never a name test (§4.56). `None` where the port declares none. */
  private[transform] def sinkOf(m: SymId)(using p: Program): Option[String] =
    if sinkSyms.isEmpty then scala.None
    else p.symbolOf(m).map(_.owner).filter(sinkSyms.contains).flatMap(p.symbolOf).map(_.fullName)

  /** is this source's sole element type an unnameable wildcard? `java.util.List<?>` means
    * `List<? extends Object>`, so `list.addAll(valueList)` type-checks in java with no cast;
    * scala's `?` is bounded by `Any`, so `Buffer[?]` is `IterableOnce[Any]` and `++=` on
    * `Buffer[Object]` fails. Widening scala's `?` is a measured dead end (G2); the difference is
    * stated at this one operation instead, by a helper doing java's own read. Narrow to a sole
    * `TypeBounds` argument — a real element type stays the idiomatic `++=`. F11
    */
  private[transform] def wildcardElement(t: TypeRepr): Boolean = t match
    case TypeRepr.AppliedType(_, List(_: TypeRepr.TypeBounds)) => true
    case _                                                     => false

  /** The other reason `++=` cannot serve java's `addAll`: the source is one of this phase's
    * standalone targets, not a `scala.collection` type — `JavaCollection extends JavaIterable`
    * and nothing else (§4.5), so it is never an `IterableOnce`. The helper this routes to already
    * takes `IterableOnce[?] | JavaIterable[?]`. Read from `shimSyms`, never a package name (§4.56). */
  private[transform] def standaloneSource(t: TypeRepr): Boolean = headSym(t).exists(shimSyms.contains)

  /** Is this a call on a map whose type arguments are wildcards, at one of the three members java
    * declares over `Object` (`get`/`containsKey`/`remove`)? Scala's `Map[K,V]` declares the same
    * three over `K`, so a wildcard receiver would emit an unnameable `K`/`V`. `put`/`getOrDefault`
    * are absent: each needs a value at the capture, which javac itself rejects on `Map<?,?>`.
    * Measured on liqp at 10 and 8 errors from the same nine call sites. K10 */
  /** does this type mention a wildcard at any depth? Not a nameability test — a wildcard-applied
    * type IS nameable (`Class[? <: N]`) — but a narrower question for [[wildcardMapCall]]: could
    * scala's invariance bite at this key. Complete over `TypeRepr`, never a partial walk. */
  private[transform] def mentionsWildcard(t: TypeRepr): Boolean = t match
    case _: TypeRepr.TypeBounds             => true
    case TypeRepr.AppliedType(tc, args)     => mentionsWildcard(tc) || args.exists(mentionsWildcard)
    case TypeRepr.TypeRef(p, _)             => mentionsWildcard(p)
    case TypeRepr.TermRef(p, _)             => mentionsWildcard(p)
    case TypeRepr.SuperType(t1, t2)         => mentionsWildcard(t1) || mentionsWildcard(t2)
    case TypeRepr.AndType(l, r)             => mentionsWildcard(l) || mentionsWildcard(r)
    case TypeRepr.OrType(l, r)              => mentionsWildcard(l) || mentionsWildcard(r)
    case TypeRepr.ByNameType(u)             => mentionsWildcard(u)
    case TypeRepr.Refinement(p, _, i)       => mentionsWildcard(p) || mentionsWildcard(i)
    case TypeRepr.MethodType(ps, r, _)      => ps.exists((_, p) => mentionsWildcard(p)) || mentionsWildcard(r)
    case TypeRepr.PolyType(_, r)            => mentionsWildcard(r)
    case TypeRepr.TypeLambda(_, b)          => mentionsWildcard(b)
    case TypeRepr.NoPrefix | TypeRepr.NoType | _: TypeRepr.ConstantType | _: TypeRepr.ThisType => false

  /** Route `Object`-keyed map members through helpers when the key/value has a bare capture
    * or the probe's type disagrees with the key's (invariance). */
  private[transform] def wildcardMapCall(name: String, recv: Term, key: Term)(using Program): Boolean =
    CollectionsTransform.WildcardMapMembers.contains(name) && wildcardMapSym(name) != SymId.None &&
      (actualOf(recv)._1 match
        case TypeRepr.AppliedType(_, List(k, v)) =>
          k.isInstanceOf[TypeRepr.TypeBounds] || v.isInstanceOf[TypeRepr.TypeBounds] ||
            (mentionsWildcard(k) && key.tpe != k)
        case TypeRepr.AppliedType(_, args) => args.exists(_.isInstanceOf[TypeRepr.TypeBounds])
        case _                             => false)

  /** True when the argument type is `Object` and the expected element type is not — routes
    * through a helper that widens the probe position at erasure. */
  private[transform] def objectProbe(arg: Term, want: Option[TypeRepr]): Boolean =
    objectSym != SymId.None && headSym(arg.tpe).contains(objectSym) &&
      want.exists(w => w != TypeRepr.NoType && !headSym(w).contains(objectSym))

  /** The third face of the same seam: a probe at a proper ancestor of the element type.
    * [[objectProbe]] is exact only at `java.lang.Object` (the top of the hierarchy); scala's
    * `Map[K,V]` is invariant in `K`, so a probe of an unrelated ancestor type also needs the
    * helper. Answered structurally by walking this run's own `extends` edges from the element type
    * up to the probe's head (CLAUDE.md §4.56) — no subtype test, and a probe the walk cannot
    * account for takes the ordinary rewrite. Not a cast: a cast would throw where java's probe
    * answers `false`; this widens the erased probe position instead. ENGINE-LIMITS K24
    */
  private[transform] def ancestorProbe(arg: Term, want: Option[TypeRepr]): Boolean =
    (headSym(arg.tpe), want.flatMap(headSym)) match
      case (Some(a), Some(e)) if a != SymId.None && e != SymId.None && a != e =>
        def parentsOf(c: Tree.ClassDef): List[SymId] = c.parents.flatMap {
          case tt: TypeTree => headSym(tt.tpe)
          case term: Term   => headSym(term.tpe)
        }
        // fuel-bounded; an exhausted walk answers false, the conservative arm here.
        def reaches(id: SymId, fuel: Int): Boolean =
          fuel > 0 && classDefsBySym.get(id).exists(c =>
            parentsOf(c).exists(s => s == a || reaches(s, fuel - 1)))
        reaches(e, 64)
      case _ => false

  private[transform] def probeMapCall(name: String, key: Term, recv: Term)(using Program): Boolean =
    CollectionsTransform.WildcardMapMembers.contains(name) && wildcardMapSym(name) != SymId.None &&
      (objectProbe(key, keyType(actualOf(recv)._1)) || ancestorProbe(key, keyType(actualOf(recv)._1)))

  private[transform] def probeSetCall(x: Term, recv: Term)(using Program): Boolean =
    objectProbe(x, elemType(actualOf(recv)._1)) || ancestorProbe(x, elemType(actualOf(recv)._1))

  private[transform] def wildcardMapSym(name: String): SymId = name match
    case "get"         => mapGetSym
    case "containsKey" => mapContainsKeySym
    case "remove"      => mapRemoveSym
    case _             => SymId.None

