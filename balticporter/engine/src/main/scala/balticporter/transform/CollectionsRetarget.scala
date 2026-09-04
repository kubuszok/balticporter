package balticporter.transform

import balticporter.tir.*

/** Per-library RETARGET mechanism (retarget/retargetRewrites/retargetTypeArgs, templates, forEach lowering, wrapReturnBoundary) split out of CollectionsTransform (context diet S3). */
private[transform] trait CollectionsRetarget:
  self: CollectionsTransform =>
  import CollectionsTransform.{JavaCollectionFqn, JavaIterableFqn, JavaIteratorFqn, Kind}

  /** Descriptor-keyed retarget rewrites. Keys are in the UPSTREAM namespace. */
  private[transform] lazy val remappedDescRewrites: Map[String, Map[(String, Descriptor), CollectionsTransform.RetargetRewrite]] =
    retargetRewritesByDesc

  /** Look up a retarget rewrite. Descriptor-keyed wins over arity-keyed. */
  private[transform] def lookupRewrite(srcFqn: String, name: String, arity: Int, desc: Option[Descriptor]): Option[CollectionsTransform.RetargetRewrite] =
    desc.flatMap { d =>
      remappedDescRewrites.get(srcFqn).flatMap { tbl =>
        tbl.collectFirst { case ((n, dd), rw) if n == name && dd.matches(d) => rw }
      }
    }.orElse(
      retargetRewrites.get(srcFqn).flatMap(_.get((name, arity)))
    )

  /** retarget target SymId to source FQN. Injective when sources have rewrite tables. */
  private[transform] var retargetTargetToSource: Map[SymId, String] = Map.empty
  /** FQN-based fallback: target FQN to set of source FQNs (ambiguity-aware). */
  private[transform] lazy val retargetTargetFqnToSources: Map[String, Set[String]] =
    retarget.groupMap(_._2)(_._1).view.mapValues(_.toSet).toMap
  /** Resolve retarget source FQN from a SymId (minted path, then FQN fallback). */
  private[transform] def retargetSourceOf(s: SymId)(using p: Program): Option[String] =
    retargetTargetToSource.get(s).orElse(
      p.symbolOf(s).flatMap { sym =>
        retargetTargetFqnToSources.get(sym.fullName).map(_.head)
          // FQN fallback 2: un-remapped source symbol
          .orElse(effectiveRetarget.get(sym.fullName).map(_ => sym.fullName))
      })
  /** True when the source FQN was resolved through `retargetTargetToSource` (the MINTED SymId —
    * unambiguous) rather than the FQN fallback (which may be ambiguous). */
  private[transform] def isUnambiguousSource(s: SymId): Boolean = retargetTargetToSource.contains(s)
  /** Declaring symbol to retarget source FQN — exact origin for rewrite table selection. */
  private[transform] var retargetDeclOrigin: Map[SymId, String] = Map.empty
  /** Extract result-type head SymId from a symbol's info (descends through MethodType/PolyType). */
  private[transform] def infoResultHead(info: TypeRepr): Option[SymId] = info match
    case TypeRepr.MethodType(_, result, _) => infoResultHead(result)
    case TypeRepr.PolyType(_, result)      => infoResultHead(result)
    case other                              => headSym(other)
  /** Resolve retarget source FQN from a receiver expression via `retargetDeclOrigin`. */
  private[transform] def resolveRecvOrigin(recv: Term): Option[String] =
    if retargetDeclOrigin.isEmpty then return scala.None
    recv match
      case id: Tree.Ident     => retargetDeclOrigin.get(id.sym)
      case sel: Tree.Select   => retargetDeclOrigin.get(sel.sym)
      case app: Tree.Apply    => retargetDeclOrigin.get(app.method)
      case ta: Tree.TypeApply  => resolveRecvOrigin(ta.fun)
      case b: Tree.Block       => b.stats.lastOption.collect { case t: Term => t }.flatMap(resolveRecvOrigin)
      case t: Tree.Typed       => resolveRecvOrigin(t.expr)
      case _                   => scala.None
  /** Look up retarget rewrite handling multi-source ambiguity. `recvOrigin` disambiguates. */
  private[transform] def lookupRewriteForReceiver(recvHeadSym: SymId, srcFqn: String,
      name: String, arity: Int, desc: Option[Descriptor],
      recvOrigin: Option[String] = None)(using Program): Option[CollectionsTransform.RetargetRewrite] =
    if isUnambiguousSource(recvHeadSym) then
      lookupRewrite(srcFqn, name, arity, desc)
    else
      // --- 3.1ap: if the receiver has a recorded origin, try that source FIRST ---
      recvOrigin.flatMap(origin => lookupRewrite(origin, name, arity, desc)).orElse {
        // FQN fallback — multiple sources may share this target.  Try each source's table.
        val targetFqn = retarget.getOrElse(srcFqn, "")
        val allSources = retargetTargetFqnToSources.getOrElse(targetFqn, Set(srcFqn))
        val answers = allSources.flatMap(src => lookupRewrite(src, name, arity, desc).map(src -> _))
        if answers.isEmpty then None
        else if answers.size == 1 then Some(answers.head._2)
        else
          // Multiple sources have entries — check if they all agree
          val distinct = answers.map(_._2).toSet
          if distinct.size == 1 then Some(distinct.head)
          else None // genuinely ambiguous — different sources want different rewrites
      }
  /** minted symbols for retarget rewrite target member names: `(sourceFqn, memberName)` -> SymId. */
  private[transform] var retargetRewriteSyms: Map[(String, String), SymId] = Map.empty
  /** source SymId -> arg mapping, for arity-changing retargets (keyed by the ORIGINAL symbol). */
  private[transform] var retargetArgsBySource: Map[SymId, List[CollectionsTransform.RetargetArg]] = Map.empty
  /** target (minted) SymId -> arg mapping, for the AppliedType case in transformType. */
  private[transform] var retargetArgsByTarget: Map[SymId, List[CollectionsTransform.RetargetArg]] = Map.empty
  /** minted SymIds for FixedType FQNs in retargetTypeArgs. */
  private[transform] var retargetFixedTypeSyms: Map[String, SymId] = Map.empty
  /** retarget target SymIds whose source is an Entry-like type (mapped to Tuple2). Used by
    * [[retargetSelectRewrite]] to fire `.key -> ._1` / `.value -> ._2` by SYMBOL, not by name. */
  private[transform] var retargetEntryTargets: Set[SymId] = Set.empty
  /** SOURCE member SymIds for IndexedField entries — the `items` field SymId on each retarget
    * source type. Used by [[retargetIndexedField]] to match the member by SYMBOL after the
    * bottom-up traversal has already visited (and potentially remapped) the `Select` node.
    * Keyed on `(ownerFqn, fieldName)` -> source SymId, so we identify the source FQN for the
    * rewrite table lookup. */
  private[transform] var indexedFieldSyms: Map[SymId, String] = Map.empty
  /** Monotonic counter for unique lambda parameter names in [[retargetForEach]]. */
  private[transform] var forEachSeq: Int = 0
  private[transform] var forEachKeyPool: Array[SymId] = Array.empty
  private[transform] var forEachValPool: Array[SymId] = Array.empty
  private[transform] var forEachElemPool: Array[SymId] = Array.empty
  /** sequence counter for return-boundary labels in [[retargetForEach]]. */
  private[transform] var retFeSeq: Int = 0
  /** sequence counter for collect-block temp variables in [[emitCollect]]. */
  private[transform] var collectSeq: Int = 0
  /** set to `true` during the Collect post-pass so [[collectPhase]] fires `emitCollect`. */
  private[transform] var collectPassActive: Boolean = false
  /** Collect blocks whose original receiver is a map and whose `.iterator()` should produce a
    * REMOVING iterator — keyed on the Opaque block's identity (same identity model as
    * `selectChainRewritten`). Value is `(originalReceiver, srcFqn, collectVia)` so the
    * iterator wrapping can emit a removing iterator that removes from the ORIGINAL map by key,
    * rather than a read-only wrapper over the snapshot. */
  /** Iterator-typed Collect wrappers -> their inner DynamicArray snapshot (for a chained `toArray`). */
  private[transform] val iteratorBlocks: java.util.IdentityHashMap[Term, Term] = new java.util.IdentityHashMap()
  private[transform] val collectBlockReceivers: java.util.IdentityHashMap[AnyRef, (Term, String, String)] =
    new java.util.IdentityHashMap()

  /** Post-pass phase: rewrites standalone `keys()`/`values()` on retarget targets into collect
    * blocks, and strips `()` from calls chained on a Collect result. */
  private[transform] val collectPhase: Phase = new Phase:
    def name = "retarget-collect"
    override def transformApply(t: Tree.Apply)(using p: Program): Term =
      t.fun match
        case Tree.Select(recv, m, _, so) =>
          // First: try to rewrite the call itself as a Collect
          // --- 3.1ap: receiver-origin disambiguation via lookupRewriteForReceiver ---
          val recvHead = headSym(recv.tpe)
          val collectResult = recvHead.flatMap(retargetSourceOf).flatMap { srcFqn =>
            val mName = methodName(m)
            val rhs = recvHead.getOrElse(SymId.None)
            lookupRewriteForReceiver(rhs, srcFqn, mName, 0, None, resolveRecvOrigin(recv)).flatMap {
              case rw: CollectionsTransform.RetargetRewrite.Collect =>
                emitCollect(recv, srcFqn, rw, t.tpe, so)
              // A standalone `entries()` (not a for-each header — the main pass lowered those):
              // java's Entries is an ITERATOR, so the image is an Iterator[(K, V)] over a snapshot.
              case rw: CollectionsTransform.RetargetRewrite.ForEach if rw.arity == 2 && t.args.isEmpty =>
                emitEntriesIterator(recv, srcFqn, rw.targetMethod, t.tpe, so)
              case _ => scala.None
            }
          }
          if collectResult.isDefined then collectResult.get
          // Second: strip empty parens from calls chained on a Collect block, unless
          // `toArray`/`iterator`, whose scala return type is not what the caller expects.
          else recv match
            case _: Tree.Opaque if t.args.isEmpty =>
              val mName = methodName(m)
              if mName == "next" && isIteratorType(recv.tpe) then t // `values().next()` on the snapshot's iterator
              else if mName == "hasNext" && isIteratorType(recv.tpe) then Tree.Select(recv, m, t.tpe, so)
              else if mName == "toArray" then
                // an Iterator-typed snapshot's `toArray()` reads the inner DynamicArray snapshot
                val snap = Option(iteratorBlocks.get(recv)).getOrElse(recv)
                // When the ORIGINAL type head is a retarget target, the caller expects a
                // DynamicArray, not a scala.Array. Return the Collect block as-is.
                val retargetTargetFqns = retarget.values.toSet
                headSym(t.tpe) match
                  case Some(h) if p.symbolOf(h).exists(s => retargetTargetFqns(s.fullName)) => snap
                  case Some(h) if retargetTargetToSource.contains(h) => snap
                  case Some(h) if remap.contains(h) && retargetTargetToSource.contains(remap(h)) => snap
                  case _ => Tree.Select(recv, m, t.tpe, so)
              else if mName == "iterator" && iteratorFromSym != SymId.None && javaIteratorSym != SymId.None then
                // Wrap .iterator with JavaIterator.from when the caller expects JavaIterator.
                // K36: if this Collect block's receiver is tracked, emit a REMOVING iterator
                // that removes from the original MAP by key rather than wrapping the read-only
                // DynamicArray snapshot.
                headSym(t.tpe) match
                  case Some(h) if (h == javaIteratorSym || (remap.contains(h) && remap(h) == javaIteratorSym) ||
                      p.symbolOf(h).exists(s => s.fullName == "java.util.Iterator" || s.fullName == "balticporter.runtime.JavaIterator")) &&
                      collectBlockReceivers.containsKey(recv) =>
                    val (mapRecv, srcFqn, via) = collectBlockReceivers.get(recv)
                    emitRemovingIteratorForCollect(mapRecv, srcFqn, via, t.tpe, so)
                  case Some(h) if h == javaIteratorSym || (remap.contains(h) && remap(h) == javaIteratorSym) ||
                      p.symbolOf(h).exists(s => s.fullName == "java.util.Iterator" || s.fullName == "balticporter.runtime.JavaIterator") =>
                    val iterSelect = Tree.Select(recv, m, TypeRepr.NoType, so)
                    Tree.Apply(Tree.Ident(iteratorFromSym, TypeRepr.NoType, so),
                               List(iterSelect), iteratorFromSym, t.tpe, so)
                  case _ => Tree.Select(recv, m, t.tpe, so)
              else
                // Strip parens ONLY when the Opaque's own type head is a retarget target —
                // i.e. it was produced by a Collect whose `into` type is DynamicArray or similar.
                // A Template-produced Opaque has the ORIGINAL call's return type (e.g. GroupPlug),
                // and chained calls on that type must keep their parens. 3.1ai: measured at 1 gdx
                // error (afterGroup must be called with () argument) without this guard.
                val isCollectBlock = headSym(recv.tpe).exists(h =>
                  retargetTargetToSource.contains(h) ||
                  p.symbolOf(h).exists(s => retarget.values.toSet(s.fullName)))
                if isCollectBlock then Tree.Select(recv, m, t.tpe, so) else t
            case _ => t
        case _ => t
  /** Apply nodes produced by [[retargetForEach]] that need a value-carrying boundary wrapper.
    * Keyed on the Apply's identity (the object itself); value is the label name used for the
    * `boundary.break` calls inside the lambda body. [[transformDefDef]] reads this to wrap
    * the Apply + its sibling Return in a `boundary[R]`. */
  private[transform] var retFeReturnApplies: java.util.IdentityHashMap[Term, String] = new java.util.IdentityHashMap()
  /** Retarget source to target map. Not folded into `mappedTypes`/`retypedTargets`. */
  def retargetedTypes: Map[String, String] = effectiveRetarget

  /** which retarget entries this signature mentions, anywhere inside it — `Set.empty` when
    * none. Walked with [[StandardTraversal.mapType]], not a private recursion, or every method
    * would silently answer "no retarget" and be attributed to the engine instead of the manifest. */
  private[transform] def retargetKeysIn(t: TypeRepr)(using Program): Set[String] =
    if effectiveRetarget.isEmpty then Set.empty
    else
      val seen = collection.mutable.Set.empty[String]
      val scan = new Phase:
        def name = "retarget-scan"
        override def transformType(x: TypeRepr)(using p: Program): TypeRepr =
          x match
            case TypeRepr.TypeRef(_, s) =>
              p.symbolOf(s).map(_.fullName).filter(effectiveRetarget.contains).foreach(seen += _)
            case _ => ()
          x
      StandardTraversal.mapType(scan, t)
      seen.toSet

  /** true when a retarget arg mapping can be resolved without any source type args — every
    * leaf is a `FixedType`, and `Applied` entries contain only fixed leaves. */
  private[transform] def allFixed(mapping: List[CollectionsTransform.RetargetArg]): Boolean =
    def isFixed(a: CollectionsTransform.RetargetArg): Boolean = a match
      case _: CollectionsTransform.RetargetArg.FixedType => true
      case CollectionsTransform.RetargetArg.Applied(_, inner) => inner.forall(isFixed)
      case _ => false
    mapping.forall(isFixed)

  private[transform] def resolveRetargetArg(arg: CollectionsTransform.RetargetArg, sourceArgs: List[TypeRepr]): TypeRepr =
    arg match
      case CollectionsTransform.RetargetArg.SourceArg(i) =>
        if i < sourceArgs.size then
          // strip wildcard bounds on arity-changing retarget args: the target is invariant, so a
          // wildcard SourceArg is invalid — take the lower bound when present, else the upper.
          sourceArgs(i) match
            case TypeRepr.TypeBounds(lo, hi) =>
              if lo != TypeRepr.NoType then lo
              else if hi != TypeRepr.NoType then hi
              else TypeRepr.AnyBounds
            case other => other
        else TypeRepr.AnyBounds // raw source — fill with ?
      case CollectionsTransform.RetargetArg.FixedType(fqn) =>
        retargetFixedTypeSyms.get(fqn) match
          case Some(sym) => TypeRepr.TypeRef(TypeRepr.NoPrefix, sym)
          case None      => TypeRepr.AnyBounds // should not happen if validated
      case CollectionsTransform.RetargetArg.Applied(fqn, innerArgs) =>
        // 3.1aw-3: a composed type — resolve the type constructor and recursively resolve
        // each inner arg. E.g. Applied("scala.Tuple2", List(SourceArg(0), SourceArg(1)))
        // produces AppliedType(TypeRef(Tuple2), List(K, V)).
        retargetFixedTypeSyms.get(fqn) match
          case Some(sym) =>
            val resolved = innerArgs.map(resolveRetargetArg(_, sourceArgs))
            TypeRepr.AppliedType(TypeRepr.TypeRef(TypeRepr.NoPrefix, sym), resolved)
          case None => TypeRepr.AnyBounds

  // ---- Reified carrier type arguments — preserved in java's namespace ----
  // // ENGINE-LIMITS K20

  /** A `classOf[T]` literal whose inner type was retarget-mapped — syncs the `const` field to
    * match, since `mapTerm` remaps `tpe` but not the `Constant.ClassOfC` the emitter reads. Counted
    * on `collection-retarget`: a third party sees the lls class, not the upstream one (K20). */
  private[transform] def retargetClassOf(lit: Tree.Literal, tp: TypeRepr, tpe: TypeRepr)(using p: Program): Term =
    // maps only through retarget entries, never the JDK §1(a) table — a classOf on a JDK-table
    // source keeps java's class (K20: a reified carrier holds java's own class; fromJava bridges at the use).
    def mapInner(t: TypeRepr): TypeRepr = t match
      case TypeRepr.TypeRef(prefix, s) if remap.get(s).exists(retargetTargetToSource.contains) =>
        TypeRepr.TypeRef(prefix, remap(s))
      case TypeRepr.AppliedType(tc, as) =>
        val mc = mapInner(tc)
        TypeRepr.AppliedType(mc, as.map(mapInner))
      case other => other
    val mapped = mapInner(tp)
    if mapped != tp then
      headSym(mapped).foreach { h =>
        if retargetTargetToSource.contains(h) then
          seam("classOf at retarget type (K20)", "reified class literal",
               TirPrinter.tpe(mapped, TirPrinter.Style.canonical), lit.origin, SymId.None,
               issue = CollectionBoundaryCheck.Issue.ReifiedOccurrence)
      }
      lit.copy(const = Constant.ClassOfC(mapped))
    else lit

  /** A field write on a retarget target — `recv.field = value` -> `recv.method(value)` — for a
    * java field the target exposes only as a method. Keyed on symbol via
    * [[retargetTargetToSource]], never a name (§4.56). */
  private[transform] def retargetFieldWrite(a: Tree.Assign)(using p: Program): Option[Term] =
    if retargetRewrites.isEmpty && retargetRewritesByDesc.isEmpty then return scala.None
    a.lhs match
      case sel: Tree.Select =>
        headSym(sel.qual.tpe).flatMap(retargetTargetToSource.get).flatMap { srcFqn =>
          val mName = methodName(sel.sym)
          lookupRewrite(srcFqn, mName, 0, None).flatMap {
            case CollectionsTransform.RetargetRewrite.FieldWrite(_, method) =>
              retargetRewriteSyms.get((srcFqn, method)).map { tgtSym =>
                // compound assignment (size -= 1) expands to method(field op rhs)
                val effectiveRhs = a.compound match
                  case Some((op, narrow)) =>
                    compoundOps.get(op) match
                      case Some(opSym) =>
                        val binOp = Tree.Apply(
                          Tree.Select(sel, opSym, a.rhs.tpe, a.origin),
                          List(a.rhs), opSym, a.rhs.tpe, a.origin)
                        narrow.fold(binOp: Term)(nt =>
                          Tree.Typed(binOp, TypeTree(nt, a.origin), nt, a.origin))
                      case None => a.rhs // unknown operator, fall through to simple assign
                  case None => a.rhs
                Tree.Apply(
                  Tree.Select(sel.qual, tgtSym, TypeRepr.NoType, a.origin),
                  List(effectiveRhs), tgtSym, TypeRepr.NoType, a.origin)
              }
            case _ => scala.None
          }
        }
      case _ => scala.None

  /** A pre-/post-increment/decrement on a retarget FieldWrite field. Java's `--stack.size` emits
    * `{ stack.size -= 1; stack.size }`, which does not compile against a read-only `def` — the
    * faithful image is `{ setSize(size - 1); size }` (pre) or a temp-bound post form, as a
    * `Tree.Block`. */
  private[transform] def retargetIncDec(id: Tree.IncDec)(using p: Program): Option[Term] =
    if retargetRewrites.isEmpty && retargetRewritesByDesc.isEmpty then return scala.None
    id.target match
      case sel: Tree.Select =>
        headSym(sel.qual.tpe).flatMap(retargetTargetToSource.get).flatMap { srcFqn =>
          val mName = methodName(sel.sym)
          lookupRewrite(srcFqn, mName, 0, None).flatMap {
            case CollectionsTransform.RetargetRewrite.FieldWrite(_, method) =>
              retargetRewriteSyms.get((srcFqn, method)).flatMap { tgtSym =>
                compoundOps.get(id.op).map { opSym =>
                  val one = Tree.Literal(balticporter.tir.Constant.IntC(1), id.tpe, id.origin)
                  val binOp = Tree.Apply(
                    Tree.Select(sel, opSym, id.tpe, id.origin),
                    List(one), opSym, id.tpe, id.origin)
                  val call = Tree.Apply(
                    Tree.Select(sel.qual, tgtSym, TypeRepr.NoType, id.origin),
                    List(binOp), tgtSym, TypeRepr.NoType, id.origin)
                  if !id.post then
                    Tree.Block(List(call), sel, id.tpe, id.origin)
                  else
                    // post-decrement needs a temp whose SymId cannot be minted here; counted on collection-retarget.
                    return scala.None
                }
              }
            case _ => scala.None
          }
        }
      case _ => scala.None

  /** An indexed field read on a retarget target — `arr.items[i]` -> `arr.apply(i)`. Matches on
    * the SOURCE member's SymId ([[indexedFieldSyms]]), not through `retargetTargetToSource`,
    * since the bottom-up traversal has already remapped the receiver's type by the time this
    * arm sees the `ArrayAccess`. */
  private[transform] def retargetIndexedField(aa: Tree.ArrayAccess)(using p: Program): Option[Term] =
    if indexedFieldSyms.isEmpty then return scala.None
    aa.array match
      case sel: Tree.Select =>
        indexedFieldSyms.get(sel.sym).flatMap { srcFqn =>
          val applySym = retargetRewriteSyms.getOrElse((srcFqn, "apply"),
            byScalaSyms.getOrElse("apply", updateSym))
          Some(Tree.Apply(
            Tree.Select(sel.qual, applySym, aa.tpe, aa.origin),
            List(aa.index), applySym, aa.tpe, aa.origin))
        }
      case _ => scala.None

  /** An indexed field write — `arr.items[i] = v` -> `arr.update(i, v)`. Same SymId-based
    * matching as [[retargetIndexedField]]. */
  private[transform] def retargetIndexedFieldWrite(a: Tree.Assign)(using p: Program): Option[Term] =
    if indexedFieldSyms.isEmpty then return scala.None
    a.lhs match
      case aa: Tree.ArrayAccess => aa.array match
        case sel: Tree.Select =>
          indexedFieldSyms.get(sel.sym).map { srcFqn =>
            Tree.Apply(
              Tree.Select(sel.qual, updateSym, TypeRepr.NoType, a.origin),
              List(aa.index, a.rhs), updateSym, TypeRepr.NoType, a.origin)
          }
        case _ => scala.None
      // children are mapped before this method sees the Assign, so the LHS ArrayAccess has
      // already become Apply(Select(recv, applySym), List(idx)) — match that shape.
      case app: Tree.Apply => app.fun match
        case sel: Tree.Select if app.args.size == 1 && methodName(sel.sym) == "apply" =>
          val recv = sel.qual
          headSym(recv.tpe).flatMap(retargetTargetToSource.get).map { _ =>
            Tree.Apply(
              Tree.Select(recv, updateSym, TypeRepr.NoType, a.origin),
              List(app.args.head, a.rhs), updateSym, TypeRepr.NoType, a.origin)
          }
        case _ => scala.None
      case _ => scala.None

  /** A field access on a retarget target — `entry.key` -> `entry._1`, `entry.value` -> `entry._2`.
    * [[retargetRewrite]] handles call sites; a bare field select has no call node for it to see.
    * Keyed on symbol (§4.56), not a name. */
  private[transform] def retargetSelectRewrite(sel: Tree.Select)(using p: Program): Option[Term] =
    // Entry field rewrites: .key/.value -> ._1/._2
    val entryResult =
      if retargetEntryTargets.isEmpty then scala.None
      else headSym(sel.qual.tpe).flatMap { h =>
        if !retargetEntryTargets.contains(h) then scala.None
        else
          val mName = methodName(sel.sym)
          if mName == "key" || mName == "getKey" then
            Some(Tree.Select(sel.qual, key1Sym, sel.tpe, sel.origin))
          else if mName == "value" || mName == "getValue" then
            Some(Tree.Select(sel.qual, value2Sym, sel.tpe, sel.origin))
          else scala.None
      }
    if entryResult.isDefined then return entryResult
    // rename entries at a Select (nullary property access, e.g. bean-renamed isEmpty -> empty):
    // retargetRewrite fires only on Tree.Apply, so this handles the Tree.Select form.
    if retargetRewrites.nonEmpty || retargetRewritesByDesc.nonEmpty then
      val selHead = headSym(sel.qual.tpe)
      selHead.flatMap(retargetSourceOf).orElse(
        for
          mSym <- p.symbolOf(sel.sym)
          oSym <- p.symbolOf(mSym.owner)
          if effectiveRetarget.contains(oSym.fullName)
        yield oSym.fullName
      ).flatMap { srcFqn =>
        val mName = methodName(sel.sym)
        val rhs = selHead.getOrElse(SymId.None)
        lookupRewriteForReceiver(rhs, srcFqn, mName, 0, None, resolveRecvOrigin(sel.qual)).flatMap {
          case CollectionsTransform.RetargetRewrite.Rename(target) =>
            retargetRewriteSyms.get((srcFqn, target)).map { tgtSym =>
              Tree.Select(sel.qual, tgtSym, sel.tpe, sel.origin)
            }
          // a parameterless iterator on a retarget target whose declared return is JavaIterator[T]
          // (java.util.Iterator redirect): NullaryArityTransform already made this a Select, so
          // the Chain handler in retargetRewrite never sees it — wrap with JavaIterator.from.
          case CollectionsTransform.RetargetRewrite.Chain(members, _, _)
              if members.lastOption.contains("iterator") && iteratorFromSym != SymId.None =>
            // K36: for targets supporting indexed removal, emit a removing iterator over the receiver.
            val targetFqn = effectiveRetarget.get(srcFqn)
            val removingResult = targetFqn.flatMap(tgt => emitRemovingIterator(sel.qual, tgt, sel.tpe, sel.origin))
            if removingResult.isDefined then Some(removingResult.get)
            else
              Some(Tree.Apply(Tree.Ident(iteratorFromSym, TypeRepr.NoType, sel.origin),
                              List(sel), iteratorFromSym, sel.tpe, sel.origin))
          // Chain at a Select (parenless, made so by bean-property/NullaryArityTransform):
          // apply with no arguments, same logic as the Apply path. The outer Apply may still
          // wrap this in () if java called it with (); tracked in selectChainRewritten to strip it.
          case CollectionsTransform.RetargetRewrite.Chain(members, hasParens, _) if members.nonEmpty =>
            val syms = members.flatMap(m => retargetRewriteSyms.get((srcFqn, m)))
            if syms.size != members.size then scala.None
            else
              var cur: Term =
                if hasParens(members.head) then
                  Tree.Apply(Tree.Select(sel.qual, syms.head, TypeRepr.NoType, sel.origin),
                             Nil, syms.head, TypeRepr.NoType, sel.origin)
                else
                  Tree.Select(sel.qual, syms.head, TypeRepr.NoType, sel.origin)
              syms.tail.zip(members.tail).foreach { (s, mName) =>
                if hasParens(mName) then
                  cur = Tree.Apply(Tree.Select(cur, s, TypeRepr.NoType, sel.origin),
                                   Nil, s, TypeRepr.NoType, sel.origin)
                else
                  cur = Tree.Select(cur, s, TypeRepr.NoType, sel.origin)
              }
              selectChainRewritten.add(cur)
              Some(cur)
          // Template at a Select (parenless): a Template expression with no arguments — the
          // member was made parenless but the rewrite needs a template (e.g.
          // `("length", 0) -> Template("(if ($recv.isEmpty) 0 else $recv.last + 1)")`).
          // Rendered with an empty argument list; only $recv and type-level placeholders
          // ($T0, $Target) are available. Same caveat as Chain above — tracked for the Apply path.
          case CollectionsTransform.RetargetRewrite.Template(expr) =>
            val result = renderTemplate(expr, sel.qual, Nil, srcFqn, sel.tpe, sel.origin)
            selectChainRewritten.add(result)
            Some(result)
          // IndexedField is NOT handled here — it fires only on Tree.ArrayAccess (see
          // retargetIndexedField). Stripping the field select on a bare Tree.Select would turn
          // `someMethod(arr.items)` into `someMethod(arr)`, changing the type from Array[T] to
          // DynamicArray[T] and opening new E007 errors. The rewrite must be scoped to the
          // ArrayAccess node that actually indexes into the backing array.
          case _ => scala.None
        }
      }
    else scala.None

  private[transform] def staticFieldRewrite(sel: Tree.Select)(using p: Program): Option[Term] =
    for
      m   <- p.symbolOf(sel.sym)
      o   <- p.symbolOf(m.owner)
      nm  <- CollectionsTransform.StaticFieldFactories.get(MemberKey(o.fullName, m.name).render)
      f    = sym(nm)
      if f != SymId.None
    yield Tree.Apply(Tree.Ident(f, TypeRepr.NoType, sel.origin), Nil, f, sel.tpe, sel.origin)

  /** the head of a symbol's declared type where that type is a FIELD's — `None` for a method (whose
    * `info` is a `MethodType`), for an unreadable class file (`NoType`), and for anything the
    * mapping does not cover. See [[externalFieldProducer]] for why the method/field distinction has
    * to come from here and cannot come from the node. */
  private[transform] def declaredFieldHead(s: SymId)(using p: Program): Option[SymId] =
    p.symbolOf(s).map(_.info).flatMap {
      case _: TypeRepr.MethodType => scala.None
      case TypeRepr.NoType        => scala.None
      case t                      => headSym(t).filter(h => p.symbolOf(h).exists(x => typeMap.contains(x.fullName)))
    }

  /** Rewrite `entry.setValue(v)` to `map.put(entry._1, v)` when the map is reachable from
    * the enclosing for-each loop. Guards: map-kind source, loop-binding receiver, pure
    * path, no reassignment. Detached entries (no loop) stay refused. // ENGINE-LIMITS K2 */
  /** Lower a for-each over a retarget target's entries/keys/values into a lambda-based
    * iteration method. `return` in body is refused and counted (non-local return).
    * Arity-2 rewrites `.key`/`.value` selects to lambda parameters. */
  private[transform] def retargetForEach(fe: Tree.ForEach)(using p: Program): Option[Term] =
    if retargetRewrites.isEmpty && retargetRewritesByDesc.isEmpty then return scala.None
    // receiver+member from `recv.member()`, or a bare Kind.Map reference — java's implicit
    // entry iteration, since the retarget removed the Iterable[Entry] parent.
    val (recv, memberSym, srcFqn) = fe.iterable match
      case Tree.Apply(Tree.Select(r, m, _, _), Nil, _, _, _) =>
        headSym(r.tpe).flatMap(retargetTargetToSource.get) match
          case Some(src) => (r, m, src)
          case _         => return scala.None
      case bareRef =>
        headSym(bareRef.tpe).flatMap(retargetTargetToSource.get) match
          case Some(src) if lookupRewrite(src, "entries", 0, None)
                .exists(_.isInstanceOf[CollectionsTransform.RetargetRewrite.ForEach]) =>
            (bareRef, SymId.None, src)
          case _ => return scala.None
    val mName = if memberSym == SymId.None then "entries" else methodName(memberSym)
    val rewrite = lookupRewrite(srcFqn, mName, 0, None) match
      case Some(rw: CollectionsTransform.RetargetRewrite.ForEach) => rw
      case Some(rw: CollectionsTransform.RetargetRewrite.Collect) =>
        CollectionsTransform.RetargetRewrite.ForEach(rw.via, 1)
      case _ => return scala.None
    val hasReturn = returnsInForEach(fe.body)
    // returnsInForEach stops at nested lambdas/defs/anon classes, so an inner loop's returns are
    // already break(v) and this reflects only this level's.
    val bound = fe.binding.symbol
    if bound == SymId.None then return scala.None
    if rewrite.arity == 2 then
      // arity-2: the binding must be used only via .key/.value selects
      if hasNonFieldUsage(bound, fe.body) then return scala.None
    // look up the minted symbol for the target method
    val tgtSym = retargetRewriteSyms.getOrElse((srcFqn, rewrite.targetMethod), SymId.None)
    if tgtSym == SymId.None then return scala.None
    val so = fe.origin
    // a `return` in the body becomes boundary.break(v)(using retFe$N); the Apply is registered
    // for wrapping in transformDefDef.
    val label = if hasReturn then { retFeSeq += 1; Some(s"retFe$$$retFeSeq") } else scala.None
    def bodyWithBreaks(body: Term): Term =
      if !hasReturn then body
      else rewriteReturnsToBreaks(body, label.get, so)
    // unique lambda parameter symbols per rewrite, or nested entry loops shadow each other
    val n = { val i = forEachSeq; forEachSeq += 1
      require(i < forEachKeyPool.length,
        s"CollectionsTransform: forEach lambda counter reached ${forEachKeyPool.length} — " +
          "pool exhausted (was 8, now 64; if a port genuinely needs more, grow the pool)")
      i }
    val apply =
      if rewrite.arity == 2 then
        // recv.foreachEntry((k, v) => body')
        val kTpe = keyType(recv.tpe).getOrElse(TypeRepr.NoType)
        val vTpe = valueType(recv.tpe).getOrElse(TypeRepr.NoType)
        val kSym = forEachKeyPool(n)
        val vSym = forEachValPool(n)
        val kParam = Tree.ValDef(kSym, TypeTree(kTpe, so), scala.None, so)
        val vParam = Tree.ValDef(vSym, TypeTree(vTpe, so), scala.None, so)
        val rewrittenBody = rewriteEntrySelects(bound, kSym, kTpe, vSym, vTpe, fe.body, so)
        val lambda = Tree.Lambda(List(kParam, vParam), bodyWithBreaks(rewrittenBody), unitTpe, so)
        Tree.Apply(Tree.Select(recv, tgtSym, TypeRepr.NoType, so), List(lambda), tgtSym, unitTpe, so)
      else
        // recv.foreachKey(k => body) or recv.foreachValue(v => body)
        val paramTpe = fe.binding.tpt.tpe
        val eSym = forEachElemPool(n)
        val param = Tree.ValDef(eSym, TypeTree(paramTpe, so), scala.None, so)
        val rewrittenBody = rewriteBindingRefs(bound, eSym, paramTpe, fe.body, so)
        val lambda = Tree.Lambda(List(param), bodyWithBreaks(rewrittenBody), unitTpe, so)
        Tree.Apply(Tree.Select(recv, tgtSym, TypeRepr.NoType, so), List(lambda), tgtSym, unitTpe, so)
    if hasReturn then retFeReturnApplies.put(apply, label.get)
    Some(apply)

  /** Emit a standalone `Collect` block: `{ val r$coN = Into[E](); recv.via(r$coN.add); r$coN }`,
    * for keys()/values() calls `retargetRewrite` left as `None` so `retargetForEach` could
    * consume the for-each iterables first. Built from TIR nodes (not `Tree.Opaque` text) so the
    * package rename reaches the element type FQN. */
  private[transform] def emitCollect(recv: Term, srcFqn: String,
      rw: CollectionsTransform.RetargetRewrite.Collect, callTpe: TypeRepr, so: Origin)(using p: Program): Option[Term] =
    val viaSym = retargetRewriteSyms.getOrElse((srcFqn, rw.via), SymId.None)
    if viaSym == SymId.None then return scala.None
    val elemTpe = if rw.via.contains("Key") then keyType(recv.tpe).getOrElse(TypeRepr.NoType)
                  else valueType(recv.tpe).getOrElse(TypeRepr.NoType)
    if elemTpe == TypeRepr.NoType then return scala.None
    val n = { collectSeq += 1; collectSeq }
    val varName = s"r$$co$n"
    val addName = "add"
    val block = Tree.Opaque.spliced(
      List(s"{ val $varName = ${rw.into}[", s"](); ", s".${rw.via}($varName.$addName); $varName }"),
      List(Tree.Ident(headSym(elemTpe).getOrElse(SymId.None), elemTpe, so), recv),
      TypeRepr.NoType,
      so
    )
    // Track map Collect receivers so `.iterator()` chained on the block can emit a REMOVING
    // iterator that removes from the original MAP rather than from the DynamicArray snapshot.
    if rw.via == "foreachValue" || rw.via == "foreachKey" then
      collectBlockReceivers.put(block, (recv, srcFqn, rw.via))
    // The call's own type decides the static shape: a java `Keys`/`Values` (an ITERATOR, retyped
    // to scala's Iterator) is the snapshot's iterator; a collection-typed call keeps the snapshot.
    if isIteratorType(callTpe) then
      val outer = Tree.Opaque.spliced(List("", ".iterator"), List(block), callTpe, so)
      iteratorBlocks.put(outer, block)
      if collectBlockReceivers.containsKey(block) then collectBlockReceivers.put(outer, collectBlockReceivers.get(block))
      Some(outer)
    else Some(block)

  /** `recv.entries()` stored or chained (not a for-each header): java's Entries is an ITERATOR
    * over reused entry objects; the image is an `Iterator[(K, V)]` over a snapshot taken through
    * the target's 2-ary `via` (`foreachEntry`). Reads only — a write through the cursor is
    * counted (K36 IteratorRemove / entry-write). No typeclass is needed (an ArrayBuffer, not a
    * DynamicArray), so a generic K/V is fine. */
  private[transform] def emitEntriesIterator(recv: Term, srcFqn: String, via: String,
      tpe: TypeRepr, so: Origin)(using p: Program): Option[Term] =
    val viaSym = retargetRewriteSyms.getOrElse((srcFqn, via), SymId.None)
    if viaSym == SymId.None then return scala.None
    val kTpe = keyType(recv.tpe).getOrElse(TypeRepr.NoType)
    val vTpe = valueType(recv.tpe).getOrElse(TypeRepr.NoType)
    if kTpe == TypeRepr.NoType || vTpe == TypeRepr.NoType then return scala.None
    val n = { collectSeq += 1; collectSeq }
    val r = s"r$$ei$n"
    def hole(t: TypeRepr) = Tree.Ident(headSym(t).getOrElse(SymId.None), t, so)
    Some(Tree.Opaque.spliced(
      List(s"{ val $r = new scala.collection.mutable.ArrayBuffer[(", ", ", s")](); ",
           s".$via((bp$$k: ", s", bp$$v: ", s") => { $r += ((bp$$k, bp$$v)); () }); $r.iterator }"),
      List(hole(kTpe), hole(vTpe), recv, hole(kTpe), hole(vTpe)),
      tpe, so))

  /** The declared type is `scala.collection.Iterator` — the image of java's nested map
    * iterator types (`ObjectMap.Keys`/`Values`/`Entries`, …) under the retarget rows. */
  private[transform] def isIteratorType(t: TypeRepr)(using p: Program): Boolean =
    headSym(t).exists(h => p.symbolOf(h).exists(_.fullName == "scala.collection.Iterator"))

  /** K36: emit a removing iterator for a direct `recv.iterator` on a retarget target, keyed on
    * the target FQN. `None` for targets the shim does not support (caller falls back to
    * read-only `JavaIterator.from`). */
  private[transform] def emitRemovingIterator(recv: Term, targetFqn: String, tpe: TypeRepr, so: Origin)(using p: Program): Option[Term] =
    targetFqn match
      case "scala.collection.mutable.ArrayDeque" =>
        Some(Tree.Opaque.spliced(
          List("balticporter.runtime.JavaIterator.removingFromBuffer(", ")"),
          List(recv), tpe, so))
      case "lowlevel.util.DynamicArray" =>
        // $recv appears 3 times, so bind to a val to avoid multiple evaluation
        val n = { collectSeq += 1; collectSeq }
        val tmpName = s"bp$$da$n"
        val riName  = "bp$ri"
        Some(Tree.Opaque.spliced(
          List(s"{ val $tmpName = ", s"; balticporter.runtime.JavaIterator.removing(() => $tmpName.size, ($riName: scala.Int) => $tmpName.apply($riName), ($riName: scala.Int) => { $tmpName.removeIndex($riName); () }) }"),
          List(recv), tpe, so))
      case _ => scala.None

  /** K36: emit a removing iterator for a map Collect whose `.iterator()` was chained — a block
    * collecting both keys and values into parallel DynamicArrays, whose `removeAt` removes from
    * the original map (`mapRecv`) by key and prunes both snapshots. */
  private[transform] def emitRemovingIteratorForCollect(mapRecv: Term, srcFqn: String, via: String,
      tpe: TypeRepr, so: Origin)(using p: Program): Term =
    val isValues = via == "foreachValue"
    val keyTpe   = keyType(mapRecv.tpe).getOrElse(TypeRepr.NoType)
    val valTpe   = valueType(mapRecv.tpe).getOrElse(TypeRepr.NoType)
    val elemTpe  = if isValues then valTpe else keyTpe
    val n = { collectSeq += 1; collectSeq }
    val ksName   = s"bp$$ks$n"
    val vsName   = s"bp$$vs$n"
    val mapName  = s"bp$$map$n"
    val riName   = "bp$ri"
    val into     = "lowlevel.util.DynamicArray"
    // Build the block as an Opaque.spliced with the map receiver, key type and value type as holes.
    // The block collects keys and values in parallel, then creates a removing JavaIterator whose
    // removeAt callback removes from the map by key AND from both snapshot arrays.
    val iterExpr = if isValues then
      s"{ val $mapName = "; val part2 = s"""; val $ksName = $into["""; val part3 = s"""](); val $vsName = $into["""; val part4 =
        s"""](); $mapName.foreachEntry((bp$$k: """ ; val part5 = s""", bp$$v: """; val part6 =
        s""") => { $ksName.add(bp$$k); $vsName.add(bp$$v) }); balticporter.runtime.JavaIterator.removing(() => $vsName.size, ($riName: scala.Int) => $vsName.apply($riName), ($riName: scala.Int) => { $mapName.remove($ksName.apply($riName)); $ksName.removeIndex($riName); $vsName.removeIndex($riName); () }) }"""
      val keySym = headSym(keyTpe).getOrElse(SymId.None)
      val valSym = headSym(valTpe).getOrElse(SymId.None)
      Tree.Opaque.spliced(
        List(s"{ val $mapName = ", s"; val $ksName = $into[", s"](); val $vsName = $into[",
             s"](); $mapName.foreachEntry((bp$$k: ", s", bp$$v: ",
             s") => { $ksName.add(bp$$k); $vsName.add(bp$$v) }); balticporter.runtime.JavaIterator.removing(() => $vsName.size, ($riName: scala.Int) => $vsName.apply($riName), ($riName: scala.Int) => { $mapName.remove($ksName.apply($riName)); $ksName.removeIndex($riName); $vsName.removeIndex($riName); () }) }"),
        List(mapRecv,
             Tree.Ident(keySym, keyTpe, so),
             Tree.Ident(valSym, valTpe, so),
             Tree.Ident(keySym, keyTpe, so),
             Tree.Ident(valSym, valTpe, so)),
        tpe, so)
    else // foreachKey — iterator over keys, remove by key
      val keySym = headSym(keyTpe).getOrElse(SymId.None)
      Tree.Opaque.spliced(
        List(s"{ val $mapName = ", s"; val $ksName = $into[",
             s"](); $mapName.foreachKey($ksName.add); balticporter.runtime.JavaIterator.removing(() => $ksName.size, ($riName: scala.Int) => $ksName.apply($riName), ($riName: scala.Int) => { $mapName.remove($ksName.apply($riName)); $ksName.removeIndex($riName); () }) }"),
        List(mapRecv, Tree.Ident(keySym, keyTpe, so)),
        tpe, so)
    iterExpr

  /** does the for-each body contain a `return`? Stops at lambdas, nested defs, anonymous classes. */
  private[transform] def returnsInForEach(t: Any): Boolean = t match
    case _: Tree.Return                                     => true
    case _: Tree.Lambda | _: Tree.DefDef | _: Tree.AnonClass => false
    case xs: Iterable[?]                                    => xs.exists(returnsInForEach)
    case Some(x)                                            => returnsInForEach(x)
    case p: Product                                         => p.productIterator.exists(returnsInForEach)
    case _                                                  => false

  /** Replace `Return(Some(v))` with `Opaque("boundary.break(v)(using label)")` in the for-each
    * body; `[[wrapReturnBoundary]]` in `[[transformDefDef]]` produces the wrapper. Stops at
    * lambdas, nested defs and anonymous classes, matching `[[returnsInForEach]]`. */
  private[transform] def rewriteReturnsToBreaks(body: Term, label: String, so: Origin)(using Program): Term =
    val rw = new Phase:
      def name = "return-to-break"
      override def transformTerm(t: Term)(using Program): Term = t match
        case r: Tree.Return =>
          val v = r.expr.getOrElse(Tree.Opaque("()", unitTpe, so))
          Tree.Opaque.spliced(
            List(s"scala.util.boundary.break(", s")(using $label)"),
            List(v),
            unitTpe,
            so
          )
        case _ => t
      override def transformDefDef(t: Tree.DefDef)(using Program): Tree.DefDef = t
    // StandardTraversal.mapTerm descends into lambdas by default; walk manually instead
    rewriteReturnsToBreaksWalk(rw, body)

  /** walk the body replacing returns, but stop at lambdas, nested defs, anon classes. */
  private[transform] def rewriteReturnsToBreaksWalk(rw: Phase, t: Term)(using Program): Term = t match
    case _: Tree.Lambda => t // lambdas open their own return scope
    case r: Tree.Return => rw.transformTerm(r)
    case x: Tree.Block =>
      x.copy(
        stats = x.stats.map {
          case s: Term => rewriteReturnsToBreaksWalk(rw, s)
          case other   => other
        },
        expr = rewriteReturnsToBreaksWalk(rw, x.expr)
      )
    case x: Tree.If =>
      x.copy(thenp = rewriteReturnsToBreaksWalk(rw, x.thenp),
             elsep = rewriteReturnsToBreaksWalk(rw, x.elsep))
    case x: Tree.While    => x.copy(body = rewriteReturnsToBreaksWalk(rw, x.body))
    case x: Tree.DoWhile  => x.copy(body = rewriteReturnsToBreaksWalk(rw, x.body))
    case x: Tree.For      => x.copy(body = rewriteReturnsToBreaksWalk(rw, x.body))
    case x: Tree.ForEach  => x.copy(body = rewriteReturnsToBreaksWalk(rw, x.body))
    case x: Tree.Synchronized => x.copy(body = rewriteReturnsToBreaksWalk(rw, x.body))
    case x: Tree.Labeled  => x.copy(stmt = rewriteReturnsToBreaksWalk(rw, x.stmt))
    case x: Tree.Commented => x.copy(stmt = rewriteReturnsToBreaksWalk(rw, x.stmt))
    case x: Tree.Try =>
      x.copy(body = rewriteReturnsToBreaksWalk(rw, x.body),
             catches = x.catches.map(c => c.copy(body = rewriteReturnsToBreaksWalk(rw, c.body))),
             finalizer = x.finalizer.map(rewriteReturnsToBreaksWalk(rw, _)))
    case x: Tree.Match =>
      x.copy(cases = x.cases.map(c => c.copy(body = rewriteReturnsToBreaksWalk(rw, c.body))))
    case other => other

  /** Does the body reference `bound` other than via `.key`/`.value`? A bare use (e.g.
    * `list.add(entry)`) has no lls image. Walks TOP-DOWN (Product reflection), since
    * `StandardTraversal.mapTerm`'s bottom-up order would flag every `.key`/`.value` access
    * as a bare ident first. */
  private[transform] def hasNonFieldUsage(bound: SymId, body: Term)(using p: Program): Boolean =
    def walk(t: Any): Boolean = t match
      // a .key/.value select on the bound entry — this is the ALLOWED usage, skip the inner Ident
      case Tree.Select(Tree.Ident(`bound`, _, _), m, _, _) =>
        val mn = methodName(m)
        mn != "key" && mn != "value" && mn != "getKey" && mn != "getValue" && mn != "_1" && mn != "_2"
      // a bare ident reference to bound — NOT allowed, the entry has no lls image
      case Tree.Ident(`bound`, _, _) => true
      // stop at constructs that rebind (lambdas, nested defs, anonymous classes)
      case _: Tree.Lambda | _: Tree.DefDef | _: Tree.AnonClass => false
      case xs: Iterable[?]     => xs.exists(walk)
      case Some(x)             => walk(x)
      case p: Product          => p.productIterator.exists(walk)
      case _                   => false
    walk(body)

  /** rewrite `.key`/`.value` selects on `bound` to `kSym`/`vSym` idents. */
  private[transform] def rewriteEntrySelects(bound: SymId, kSym: SymId, kTpe: TypeRepr,
      vSym: SymId, vTpe: TypeRepr, body: Term, so: Origin)(using Program): Term =
    val rw = new Phase:
      def name = "entry-select-rewrite"
      override def transformTerm(x: Term)(using Program): Term = x match
        case Tree.Select(Tree.Ident(`bound`, _, _), m, _, _) =>
          val mn = methodName(m)
          if mn == "key" || mn == "getKey" || mn == "_1" then Tree.Ident(kSym, kTpe, so)
          else if mn == "value" || mn == "getValue" || mn == "_2" then Tree.Ident(vSym, vTpe, so)
          else x
        case _ => x
    StandardTraversal.mapTerm(rw, body)

  /** rewrite all references to `bound` as references to `paramSym`. */
  private[transform] def rewriteBindingRefs(bound: SymId, paramSym: SymId, paramTpe: TypeRepr,
      body: Term, so: Origin)(using Program): Term =
    val rw = new Phase:
      def name = "binding-ref-rewrite"
      override def transformTerm(x: Term)(using Program): Term = x match
        case Tree.Ident(`bound`, _, _) => Tree.Ident(paramSym, paramTpe, so)
        case _ => x
    StandardTraversal.mapTerm(rw, body)

  private[transform] def writeThroughEntries(fe: Tree.ForEach)(using p: Program): Tree.ForEach =
    entrySource(fe.iterable).filter(purePath) match
    case scala.None      => fe
    case Some(src) =>
      val bound = fe.binding.symbol
      if bound == SymId.None || reassigned(bound, fe.body) then fe
      else
        val rw = new Phase:
          def name = "entry-set-write-through"
          override def transformApply(t: Tree.Apply)(using Program): Term = t.fun match
            case Tree.Select(Tree.Ident(`bound`, bt, bo), m, _, so)
              if methodName(m) == "setValue" && t.args.sizeIs == 1 =>
              val key = Tree.Select(Tree.Ident(bound, bt, bo), key1Sym, keyType(src.tpe).getOrElse(TypeRepr.NoType), bo)
              call(call(src, putSym, List(key, t.args.head), t, so), getOrElseSym,
                   List(dflt(nullOf(so), src, so)), t, so)
            case _ => t
        fe.copy(body = StandardTraversal.mapTerm(rw, fe.body))

  /** The map a for-loop's entry source is a view OF — this phase's own `entrySet()` rewrite,
    * whichever shape it took: an application of the `entrySetView` symbol this run minted, or
    * (where that helper is absent) a source retyped to `Kind.Map`. §4.56: asked of the phase's
    * own record, never a name. */
  private[transform] def entrySource(src: Term)(using Program): Option[Term] = src match
    case Tree.Apply(_, List(m), f, _, _) if f != SymId.None && f == sym("entrySetView") => Some(m)
    case _ if kindAt(src).contains(Kind.Map)                                            => Some(src)
    case _                                                                              => scala.None

  /** an expression java may evaluate a SECOND time without changing what the program does — an
    * identifier, `this`, or a selection chain over one. Deliberately narrow: the question is asked
    * of a loop source about to be repeated inside the body, and over-approximating it duplicates an
    * effect that no compile error and no check count would report. */
  private[transform] def purePath(t: Term): Boolean = t match
    case _: Tree.Ident | _: Tree.This       => true
    case Tree.Select(q, _, _, _)            => purePath(q)
    case _                                  => false

  /** is `s` the target of an assignment anywhere under `body`? `StandardTraversal`'s walk, per
    * CLAUDE.md §3 — a hand-rolled recursion that stopped one node short would answer "no" for the
    * shape this test exists to catch. */
  private[transform] def reassigned(s: SymId, body: Term)(using Program): Boolean =
    var hit = false
    val scan = new Phase:
      def name = "binding-reassignment"
      override def transformTerm(x: Term)(using Program): Term =
        x match
          case Tree.Assign(Tree.Ident(`s`, _, _), _, _, _, _) => hit = true; x
          case _                                           => x
    StandardTraversal.mapTerm(scan, body)
    hit

  private[transform] def coerceReturns(want: TypeRepr, t: Term)(using Program): Term = t match
    case x: Tree.Return       => x.copy(expr = x.expr.map(coerce(want, _)))
    case x: Tree.Block        => x.copy(stats = x.stats.map(coerceReturnsIn(want, _)), expr = coerceReturns(want, x.expr))
    case x: Tree.If           => x.copy(thenp = coerceReturns(want, x.thenp), elsep = coerceReturns(want, x.elsep))
    case x: Tree.While        => x.copy(body = coerceReturns(want, x.body))
    case x: Tree.DoWhile      => x.copy(body = coerceReturns(want, x.body))
    case x: Tree.For          => x.copy(body = coerceReturns(want, x.body))
    case x: Tree.ForEach      => x.copy(body = coerceReturns(want, x.body))
    case x: Tree.Synchronized => x.copy(body = coerceReturns(want, x.body))
    case x: Tree.Labeled      => x.copy(stmt = coerceReturns(want, x.stmt))
    // must read through the comment wrapper (§4.58) — a return under a comment is still a return
    case x: Tree.Commented    => x.copy(stmt = coerceReturns(want, x.stmt))
    case x: Tree.Try =>
      x.copy(body = coerceReturns(want, x.body),
             catches = x.catches.map(c => c.copy(body = coerceReturns(want, c.body))),
             finalizer = x.finalizer.map(coerceReturns(want, _)))
    case x: Tree.Match => x.copy(cases = x.cases.map(c => c.copy(body = coerceReturns(want, c.body))))
    case other         => other

  /** a `Block` statement that is a TERM continues this method's return scope; a `ValDef` cannot
    * contain a `return` at all, and a nested `DefDef`/`ClassDef` opens its own. */
  private[transform] def coerceReturnsIn(want: TypeRepr, s: Statement)(using Program): Statement = s match
    case t: Term => coerceReturns(want, t)
    case other   => other

  /** Wrap Apply nodes registered in [[retFeReturnApplies]] with a `boundary[R]` whose
    * fallthrough value is whatever code follows the Apply in the enclosing Block. The `Return`
    * nodes inside the lambda body are already `boundary.break(v)(using label)` (via
    * [[rewriteReturnsToBreaks]]); a tail `Return` becomes the boundary's fallthrough expression. */
  private[transform] def wrapReturnBoundary(retType: TypeRepr, body: Term)(using p: Program): Term = body match
    case b: Tree.Block =>
      // scan stats for a registered Apply
      val idx = b.stats.indexWhere {
        case t: Term => retFeReturnApplies.containsKey(t)
        case _       => false
      }
      if idx < 0 then
        // recurse into statement-carrying nodes
        b.copy(
          stats = b.stats.map {
            case t: Term => wrapReturnBoundary(retType, t)
            case other   => other
          },
          expr = wrapReturnBoundary(retType, b.expr)
        )
      else
        val applyNode = b.stats(idx).asInstanceOf[Term]
        val label = retFeReturnApplies.get(applyNode)
        retFeReturnApplies.remove(applyNode)
        val so = applyNode.origin
        // gather tail: everything after the Apply in the Block
        val tailStats = b.stats.drop(idx + 1)
        val tailExpr  = b.expr
        // fallthrough is the tail statements with Return stripped. Every java method exits
        // through Tree.Return, so Block.expr is (); if a tail Return exists its value IS the
        // fallthrough and the block's () is excluded, else the block's expr IS the fallthrough.
        val tailHasReturn = tailStats.exists {
          case _: Tree.Return => true
          case _ => false
        }
        val fallthroughParts =
          if tailHasReturn then
            tailStats.collect { case t: Term => stripReturn(t) }
          else
            tailStats.collect { case t: Term => stripReturn(t) } :+ stripReturn(tailExpr)
        // the return type is rendered as an Opaque.spliced with the HEAD SYMBOL as an AST hole so
        // PackageRenameTransform reaches it — a text-rendered fullName would be the upstream FQN
        val retTypeRendered: Term = retType match
          case TypeRepr.TypeRef(_, s) =>
            Tree.Ident(s, retType, so)
          case TypeRepr.AppliedType(TypeRepr.TypeRef(_, s), args) =>
            val argsText = args.map(renderTypeForBoundary).mkString(", ")
            Tree.Opaque.spliced(List("", s"[$argsText]"), List(Tree.Ident(s, retType, so)), retType, so)
          case _ =>
            // fallback: render as text (primitive types, Unit, etc.)
            Tree.Opaque(renderTypeForBoundary(retType), retType, so)
        // two type holes: one for boundary[R] and one for Label[R]
        val allHoles    = retTypeRendered :: retTypeRendered :: applyNode :: fallthroughParts
        // boundary[R] { (label: Label[R]) ?=> hole0; hole1; ...; holeN }
        val parts       = new collection.mutable.ListBuffer[String]
        parts += "scala.util.boundary["
        parts += s"] { ($label: scala.util.boundary.Label["
        parts += "]) ?=> "
        for i <- 0 until (allHoles.size - 2 - 1) do parts += "; "
        parts += " }"
        val boundaryNode = Tree.Opaque.spliced(parts.toList, allHoles, retType, so)
        // replace the Apply + tail with the boundary
        val prefix = b.stats.take(idx).map {
          case t: Term => wrapReturnBoundary(retType, t)
          case other   => other
        }
        if prefix.isEmpty then boundaryNode
        else Tree.Block(prefix.toList, boundaryNode, retType, so)
    case x: Tree.If =>
      x.copy(thenp = wrapReturnBoundary(retType, x.thenp),
             elsep = wrapReturnBoundary(retType, x.elsep))
    case x: Tree.Labeled => x.copy(stmt = wrapReturnBoundary(retType, x.stmt))
    case x: Tree.Commented => x.copy(stmt = wrapReturnBoundary(retType, x.stmt))
    case x: Tree.Synchronized => x.copy(body = wrapReturnBoundary(retType, x.body))
    case x: Tree.Try =>
      x.copy(body = wrapReturnBoundary(retType, x.body))
    case _ => body

  /** Strip a `Return` wrapper, keeping only its value expression. Used to convert a method-level
    * `return false` into the boundary's fallthrough `false`. */
  private[transform] def stripReturn(t: Term): Term = t match
    case Tree.Return(Some(v), _, _) => v
    case Tree.Return(scala.None, _, so) => Tree.Opaque("()", unitTpe, so)
    case other => other

  /** Strip a `Return` wrapper from a Statement. */
  private[transform] def stripReturn(s: Statement): Term = s match
    case t: Term => stripReturn(t)
    case _ => Tree.Opaque("()", unitTpe, Origin.synthetic)

  /** Render a TypeRepr as a fully-qualified name for the boundary's type parameter.
    * Only needs to handle the return types that java methods actually produce — primitives,
    * classes, applied generics. A type that cannot be rendered falls back to `scala.Any`,
    * which is the conservative answer (the boundary accepts any value). */
  private[transform] def renderTypeForBoundary(t: TypeRepr)(using p: Program): String = t match
    case TypeRepr.TypeRef(_, s) =>
      p.symbolOf(s).map(_.fullName).getOrElse("scala.Any")
    case TypeRepr.AppliedType(tc, args) =>
      val baseName = renderTypeForBoundary(tc)
      s"$baseName[${args.map(renderTypeForBoundary).mkString(", ")}]"
    // a wildcard type argument is a TypeBounds in the TIR; render as `?` with its bounds so
    // boundary[BaseLight[?]] is legal — writable inside an argument position (CLAUDE.md §4.56).
    case TypeRepr.TypeBounds(TypeRepr.NoType, TypeRepr.NoType) => "?"
    case TypeRepr.TypeBounds(TypeRepr.NoType, hi) => s"? <: ${renderTypeForBoundary(hi)}"
    case TypeRepr.TypeBounds(lo, TypeRepr.NoType) => s"? >: ${renderTypeForBoundary(lo)}"
    case TypeRepr.TypeBounds(lo, hi) =>
      s"? >: ${renderTypeForBoundary(lo)} <: ${renderTypeForBoundary(hi)}"
    case TypeRepr.NoType => "scala.Any"
    case _ => "scala.Any"

  /** Render a retarget coercion template, wrapping `actual` in a `Tree.Opaque.spliced` expression.
    * `$0` in the template is the actual value; everything else is literal text. The result is typed
    * at the `expected` type. */
  private[transform] def renderRetargetCoercion(template: String, actual: Term, expected: TypeRepr,
      origin: Origin): Term =
    val ph      = "$0"
    val indices = scala.collection.mutable.ListBuffer.empty[Int]
    var idx     = 0
    while { idx = template.indexOf(ph, idx); idx >= 0 } do
      indices += idx
      idx += ph.length
    if indices.isEmpty then
      Tree.Opaque(template, expected, origin)
    else
      val parts = scala.collection.mutable.ListBuffer.empty[String]
      val holes = scala.collection.mutable.ListBuffer.empty[Term]
      var pos   = 0
      for p <- indices do
        parts += template.substring(pos, p)
        holes += actual
        pos = p + ph.length
      parts += template.substring(pos)
      Tree.Opaque.spliced(parts.toList, holes.toList, expected, origin)

  /** counter for template temporary variables — one run-scoped namespace so names are stable. */
  private[transform] var templateSeq: Int = 0

  /** Renders a `RetargetRewrite.Template(expr)` into a `Tree.Opaque.spliced` (or a `Tree.Block`
    * wrapping one when temp `val` bindings are needed for repeated term placeholders).
    * Type-level placeholders (`$Target`, `$T0`…) are text-substituted; term-level ones (`$recv`,
    * `$0`…) become AST holes. A term placeholder used more than once is bound to a `val`
    * (CLAUDE.md §4.4/F7). */
  private[transform] def renderTemplate(expr: String, recv: Term, args: List[Term],
      srcFqn: String, tpe: TypeRepr, so: Origin)(using p: Program): Term =
    // $Target is text-only; also check retarget (not just typeMap) or it resolves to the source FQN.
    val targetFqn = typeMap.get(srcFqn).map(_._1).orElse(retarget.get(srcFqn)).getOrElse(srcFqn)
    var text = expr.replace("$Target", targetFqn)
    // $T0, $T1... become AST holes (not text) so a later phase (package rename) can still reach
    // the symbol's fullName. An applied type arg needs a nested spliced Opaque to keep its own
    // type arguments, or a plain Ident would render only the head.
    def typeArgToTerm(ta: TypeRepr): Term = ta match
      case TypeRepr.AppliedType(tc, innerArgs) =>
        val headTerm = typeArgToTerm(tc)
        val argTerms = innerArgs.map(typeArgToTerm)
        val parts = scala.collection.mutable.ListBuffer.empty[String]
        val holes = scala.collection.mutable.ListBuffer.empty[Term]
        parts += ""            // before the head
        holes += headTerm
        parts += "["           // between head and first arg
        argTerms.zipWithIndex.foreach { (at, j) =>
          holes += at
          if j < argTerms.size - 1 then parts += ", " else parts += "]"
        }
        Tree.Opaque.spliced(parts.toList, holes.toList, ta, so)
      case TypeRepr.TypeBounds(lo, hi) =>
        // A wildcard — render as `?` (no AST hole needed, no FQN to rename).
        Tree.Opaque("?", ta, so)
      case _ =>
        val sym = headSym(ta).getOrElse(SymId.None)
        Tree.Ident(sym, ta, so)
    val typeArgTerms = scala.collection.mutable.LinkedHashMap.empty[String, Term]
    recv.tpe match
      case TypeRepr.AppliedType(_, targs) =>
        targs.zipWithIndex.foreach { (ta, i) =>
          val ph = s"$$T$i"
          if text.contains(ph) then
            typeArgTerms(ph) = typeArgToTerm(ta)
        }
      case _ => ()
    // a term placeholder is $recv or $N (argument index); must not collide with $T0/$Target
    // (text-substituted above, may survive unresolved with no type args) or $10 matching $1+0
    def findTermPh(txt: String, ph: String): List[Int] =
      val results = scala.collection.mutable.ListBuffer.empty[Int]
      val isTypeArgPh = ph.startsWith("$T") && ph.length > 2 && ph.charAt(2).isDigit
      var idx = 0
      while { idx = txt.indexOf(ph, idx); idx >= 0 } do
        // $recv/$T0..: accept as-is; $0..$N: skip if preceded by T or followed by a digit
        val precOk = ph == "$recv" || isTypeArgPh || idx == 0 || txt.charAt(idx - 1) != 'T'
        val suffOk = ph == "$recv" || isTypeArgPh || {
          val afterEnd = idx + ph.length
          afterEnd >= txt.length || !txt.charAt(afterEnd).isDigit
        }
        if precOk && suffOk then
          results += idx
          idx += ph.length
        else
          idx += 1
      results.toList
    val termPh = scala.collection.mutable.LinkedHashMap.empty[String, Term]
    // type arg placeholders are term holes; bind before $0 etc.
    for (ph, term) <- typeArgTerms do termPh(ph) = term
    if findTermPh(text, "$recv").nonEmpty then termPh("$recv") = recv
    for i <- args.indices do
      val ph = s"$$$i"
      if findTermPh(text, ph).nonEmpty then termPh(ph) = args(i)
    val counts = termPh.map { (ph, _) => ph -> findTermPh(text, ph).size }.toMap
    // placeholders appearing >1 time bind to a temp val; subsequent occurrences become the temp name
    val bindings = scala.collection.mutable.ListBuffer.empty[(String, Term, String)]
    for (ph, term) <- termPh do
      if counts.getOrElse(ph, 0) > 1 then
        templateSeq += 1
        val tmpName = s"bp$$tpl$templateSeq"
        bindings += ((ph, term, tmpName))
        // explicit substring, not append(CharSequence,start,end) — avoids Scala 3 auto-tupling
        val phPositions = findTermPh(text, ph)
        val sb = new StringBuilder
        var pos0 = 0
        for p <- phPositions do
          sb.append(text.substring(pos0, p))
          sb.append(tmpName)
          pos0 = p + ph.length
        sb.append(text.substring(pos0))
        text = sb.toString
    // split around remaining (single-occurrence) placeholders to build parts/holes
    val positions = scala.collection.mutable.ListBuffer.empty[(Int, Int, String)]
    for (ph, _) <- termPh if counts.getOrElse(ph, 0) <= 1 do
      for p <- findTermPh(text, ph) do
        positions += ((p, p + ph.length, ph))
    val sortedPositions = positions.sortBy(_._1).toList
    val parts = scala.collection.mutable.ListBuffer.empty[String]
    val holes = scala.collection.mutable.ListBuffer.empty[Term]
    var pos = 0
    for (start, end, ph) <- sortedPositions do
      parts += text.substring(pos, start)
      holes += termPh(ph)
      pos = end
    parts += text.substring(pos)
    val opaque =
      if parts.size == 1 && holes.isEmpty then Tree.Opaque(text, tpe, so)
      else Tree.Opaque.spliced(parts.toList, holes.toList, tpe, so)
    if bindings.isEmpty then opaque
    else
      val stmts = bindings.toList.map { (_, term, tmpName) =>
        Tree.Opaque.spliced(
          List(s"val $tmpName = ", ""),
          List(term),
          TypeRepr.NoType,
          so
        )
      }
      Tree.Block(stmts, opaque, tpe, so)


  /** The value's own minted ancestry, as a coercion source — K26's `DeclaredSubtype` half.
    * `coerce` reads a source's kind out of `kindOf`, keyed on the phase's own scala target
    * symbols, so it answers `None` for a type the PROGRAM declares (`OrderedSet implements
    * java.util.Set`, emitted `extends mutable.Set`, handed to its own `retainAll` — java's
    * `Set <: Collection` edge has no image). `None` where the value already conforms. Which kind,
    * where a class carries two, is [[Kind]]'s own declaration order (deterministic), never a
    * `Set`'s iteration order. ENGINE-LIMITS K26 */
  private[transform] def mintedSourceKind(head: SymId, wants: Option[SymId]): Option[Kind] =
    parentClash.get(head).filterNot { mp =>
      (wants.contains(javaIterableSym) &&
        (mp.shims(CollectionsTransform.JavaIterableFqn) || mp.shims(CollectionsTransform.JavaCollectionFqn))) ||
      (wants.contains(javaCollectionSym) && mp.shims(CollectionsTransform.JavaCollectionFqn)) ||
      (wants.contains(javaIteratorSym)   && mp.shims(CollectionsTransform.JavaIteratorFqn))
    }.flatMap(_.kinds.toList.sortBy(_.ordinal).headOption)

  /** Rewrites a call on a retarget target — `bits.get(i)` -> `bits.apply(i)` — when the
    * receiver's head symbol is a retarget target and `(memberName, arity)` has a
    * `retargetRewrites` entry. `BoolDispatch` on a non-literal flag returns `None`, counted on
    * `collection-retarget`. */
  private[transform] def retargetRewrite(recv: Term, m: SymId, so: Origin, t: Tree.Apply)(using p: Program): Option[Term] =
    if retargetRewrites.isEmpty && retargetRewritesByDesc.isEmpty then return scala.None
    // static companion reference fallback: a static call's receiver Ident carries a freshly
    // minted external SymId not in `remap`, so resolve the source FQN from the method's owner instead.
    val recvHead0 = headSym(recv.tpe)
    recvHead0.flatMap(retargetSourceOf).orElse(
      for
        mSym   <- p.symbolOf(m)
        oSym   <- p.symbolOf(mSym.owner)
        if effectiveRetarget.contains(oSym.fullName)
      yield oSym.fullName
    ).flatMap { srcFqn =>
      val mName = methodName(m)
      val arity = t.args.size
      val desc = p.symbolOf(m).flatMap(_.descriptor)
      val rhs = recvHead0.getOrElse(SymId.None)
      // receiver-origin tracking, to disambiguate when the FQN fallback above fires
      lookupRewriteForReceiver(rhs, srcFqn, mName, arity, desc, resolveRecvOrigin(recv)).flatMap {
        case CollectionsTransform.RetargetRewrite.Rename(target) =>
          retargetRewriteSyms.get((srcFqn, target)).map { tgtSym =>
            call(recv, tgtSym, t.args, t, so)
          }
        case CollectionsTransform.RetargetRewrite.BoolDispatch(flagIndex, onTrue, onFalse) =>
          if flagIndex < 0 || flagIndex >= t.args.size then scala.None
          else
            val flagArg = t.args(flagIndex)
            val remaining = t.args.take(flagIndex) ++ t.args.drop(flagIndex + 1)
            flagArg match
              case Tree.Literal(balticporter.tir.Constant.BoolC(true), _, _) =>
                retargetRewriteSyms.get((srcFqn, onTrue)).map { tgtSym =>
                  call(recv, tgtSym, remaining, t, so)
                }
              case Tree.Literal(balticporter.tir.Constant.BoolC(false), _, _) =>
                retargetRewriteSyms.get((srcFqn, onFalse)).map { tgtSym =>
                  call(recv, tgtSym, remaining, t, so)
                }
              case _ =>
                // non-literal boolean: emit `if (flag) recv.onTrue(args) else recv.onFalse(args)`,
                // evaluate-once binding for receiver and args (CLAUDE.md §4.4/F7).
                (retargetRewriteSyms.get((srcFqn, onTrue)), retargetRewriteSyms.get((srcFqn, onFalse))) match
                  case (Some(trueSym), Some(falseSym)) =>
                    val n = { templateSeq += 1; templateSeq }
                    val recvTmp = s"bp$$bd$n"
                    val argTmps = remaining.indices.map(i => s"bp$$bd${n}a$i")
                    val recvBind = s"val $recvTmp = "
                    val argBinds = argTmps.map(t => s"; val $t = ")
                    val argList = argTmps.mkString(", ")
                    val trueCall  = s"$recvTmp.${p.symbolOf(trueSym).map(_.name).getOrElse(onTrue)}($argList)"
                    val falseCall = s"$recvTmp.${p.symbolOf(falseSym).map(_.name).getOrElse(onFalse)}($argList)"
                    val tail = s"; if (" // flag hole follows
                    val afterFlag = s") $trueCall else $falseCall }"
                    val parts = List("{ " + recvBind) ++ argBinds.toList ++ List(tail, afterFlag)
                    val holes = List(recv) ++ remaining.toList ++ List(flagArg)
                    Some(Tree.Opaque.spliced(parts, holes, t.tpe, so))
                  case _ => scala.None
        // Construct entries are handled by retargetConstruct (Tree.New path); a call reaching
        // here is a name/arity collision with an "<init>" entry — leave it for RetargetBoundaryCheck.
        case _: CollectionsTransform.RetargetRewrite.Construct => scala.None
        // ForEach entries are handled on the enclosing Tree.ForEach; a call reaching here is a
        // standalone entries()/keys()/values() with no lls image.
        case _: CollectionsTransform.RetargetRewrite.ForEach => scala.None
        // Collect entries are handled on ForEach and by the collect post-pass; None here so the
        // bottom-up traversal does not steal the iterable before retargetForEach sees the ForEach.
        case _: CollectionsTransform.RetargetRewrite.Collect => scala.None
        // for a static call, recv.tpe has no type arguments so $T0 does not resolve — borrow t.tpe
        // (the call's return type) for the type-arg extraction instead.
        case CollectionsTransform.RetargetRewrite.Template(expr) =>
          val effectiveRecv = recv.tpe match
            case TypeRepr.AppliedType(_, _) => recv // instance call: recv already has type args
            case _ => t.tpe match
              case TypeRepr.AppliedType(_, _) =>
                recv match
                  case id: Tree.Ident => id.copy(tpe = t.tpe)
                  case _              => recv
              case _ => recv
          Some(renderTemplate(expr, effectiveRecv, t.args, srcFqn, t.tpe, so))
        case CollectionsTransform.RetargetRewrite.Chain(members, hasParens, dropAllArgs) if members.nonEmpty =>
          val syms = members.flatMap(m => retargetRewriteSyms.get((srcFqn, m)))
          if syms.size != members.size then scala.None
          else
            // first member: call() when source args are non-empty or parens says (); else Select.
            // the terminal chain node carries the call's type (for TestFrameworkTransform.promote);
            // intermediates keep NoType.
            val isSingle = members.size == 1
            var cur: Term =
              if !dropAllArgs && (t.args.nonEmpty || hasParens(members.head)) then
                call(recv, syms.head, t.args, t, so)
              else if hasParens(members.head) then
                val tp = if isSingle then t.tpe else TypeRepr.NoType
                Tree.Apply(Tree.Select(recv, syms.head, TypeRepr.NoType, so), Nil, syms.head, tp, so)
              else
                val tp = if isSingle then t.tpe else TypeRepr.NoType
                Tree.Select(recv, syms.head, tp, so)
            // tail members: parameterless -> Select; in parens -> Apply with Nil args.
            syms.tail.zip(members.tail).zipWithIndex.foreach { case ((s, mName), idx) =>
              val isLast = idx == syms.tail.size - 1
              val tp = if isLast then t.tpe else TypeRepr.NoType
              if hasParens(mName) then
                cur = Tree.Apply(Tree.Select(cur, s, TypeRepr.NoType, so), Nil, s, tp, so)
              else
                cur = Tree.Select(cur, s, tp, so)
            }
            // a retarget target's iterator returns scala.collection.Iterator but the declared
            // return type is JavaIterator; the Chain node's NoType hides the mismatch from the
            // return-coercion path, so wrap with JavaIterator.from(it) here instead.
            if members.last == "iterator" && iteratorFromSym != SymId.None && javaIteratorSym != SymId.None then
              val wantsJavaIterator = headSym(t.tpe) match
                case Some(h) if h == javaIteratorSym => true
                case Some(h) if remap.contains(h) && remap(h) == javaIteratorSym => true
                case Some(h) if p.symbolOf(h).exists(s =>
                    s.fullName == "java.util.Iterator" || s.fullName == "balticporter.runtime.JavaIterator") => true
                case _ => false
              if wantsJavaIterator then
                // K36: for targets supporting indexed removal, emit a removing iterator over the
                // receiver rather than a read-only JavaIterator.from wrapping.
                val targetFqn = effectiveRetarget.get(srcFqn)
                val removingResult = targetFqn.flatMap(tgt => emitRemovingIterator(recv, tgt, t.tpe, so))
                if removingResult.isDefined then
                  cur = removingResult.get
                else
                  cur = Tree.Apply(Tree.Ident(iteratorFromSym, TypeRepr.NoType, so),
                                   List(cur), iteratorFromSym, t.tpe, so)
            // toArray() returns scala.Array[T], but the call's type head may still be the
            // retarget target (the caller expects e.g. DynamicArray) — drop .toArray and return
            // the receiver, which the preceding rewrite already built as that target.
            if members.last == "toArray" && members.size == 1 then
              val retargetTargetFqns = retarget.values.toSet
              headSym(t.tpe) match
                case Some(h) if retargetTargetToSource.contains(h) =>
                  cur = recv  // the DynamicArray already built by the preceding rewrite
                case Some(h) if remap.contains(h) && retargetTargetToSource.contains(remap(h)) =>
                  cur = recv
                case Some(h) if p.symbolOf(h).exists(s => retargetTargetFqns(s.fullName)) =>
                  cur = recv
                case _ => ()
            Some(cur)
        case _: CollectionsTransform.RetargetRewrite.Chain => scala.None
        // FieldWrite is handled in transformTerm on Tree.Assign; a call reaching here is a
        // same-(name,arity) method call — return None.
        case _: CollectionsTransform.RetargetRewrite.FieldWrite => scala.None
        // IndexedField is handled in retargetSelectRewrite; a call reaching here is standalone on the field.
        case _: CollectionsTransform.RetargetRewrite.IndexedField => scala.None
      }
    }

  /** Rewrites a construction of a retarget target — `new Source[A](args)` -> `Target.factory[A](args)`
    * — via a minted companion-factory symbol, when `retargetRewrites` has a `Construct` entry for
    * `("<init>", arity)`. The factory's `inline apply[A](…)(using MkArray[A])` needs the type
    * argument explicit (else scala infers `Any`); taken from `n.tpe`, `AnyRef` for a raw source
    * (G2), emitted faithfully for a type-parameter element (`MkArray[T]` must then be threaded or
    * the construction is counted). */
  private[transform] def retargetConstruct(t: Tree.Apply)(using p: Program): Option[Term] = t.fun match
    case n: Tree.New if retargetRewrites.nonEmpty || retargetRewritesByDesc.nonEmpty =>
      val newHead = headSym(n.tpe)
      newHead.flatMap(retargetSourceOf).flatMap { srcFqn =>
        val arity = t.args.size
        val ctorSym = p.symbolOf(t.method)
        val desc = ctorSym.flatMap(_.descriptor).orElse(ctorSym.flatMap(s => Descriptor.ofInfo(p, s)))
        // receiver-origin disambiguation at the member level
        val rhs = newHead.getOrElse(SymId.None)
        lookupRewriteForReceiver(rhs, srcFqn, "<init>", arity, desc).flatMap {
          case CollectionsTransform.RetargetRewrite.Construct(companionFqn, factoryMethod, dropTrailing, fillTypeArgs) =>
            val fqn = s"$companionFqn.$factoryMethod"
            retargetRewriteSyms.get((srcFqn, fqn)).map { factorySym =>
              val rawArgs = if dropTrailing > 0 then t.args.dropRight(dropTrailing) else t.args
              val effectiveArgs =
                if rawArgs.nonEmpty then rawArgs
                else if !fillTypeArgs then Nil
                else
                  val targs = n.tpe match
                    case TypeRepr.AppliedType(_, as) => as
                    case _ => Nil
                  targs.map { a =>
                    // wildcards (TypeBounds) become Object — an unbound wildcard is not term-position syntax
                    val safe = a match
                      case _: TypeRepr.TypeBounds => TypeRepr.TypeRef(TypeRepr.NoPrefix, objectSym)
                      case other                 => other
                    Tree.Typed(
                      Tree.Literal(balticporter.tir.Constant.NullC, safe, t.origin),
                      TypeTree(safe, t.origin), safe, t.origin)
                  }
              // extract type args from the retargeted type so the factory call carries them
              // explicitly (else scala infers Any and summonInline[MkArray[Any]] fails). A
              // type-parameter element is emitted faithfully; MkArray[T] must then be provided
              // by the enclosing scope or the error is counted on collection-retarget.
              val targsFromType: List[TypeTree] = n.tpe match
                case TypeRepr.AppliedType(_, as) =>
                  as.map {
                    case _: TypeRepr.TypeBounds =>
                      TypeTree(TypeRepr.TypeRef(TypeRepr.NoPrefix, objectSym), t.origin)
                    case a => TypeTree(a, t.origin)
                  }
                case _ => Nil
              // derive element type from a dropped supplier argument: a raw-type constructor
              // interns with no/Object type arg, but a dropped Sprite[]::new MethodRef's
              // qualifier carries the real component type — use it rather than fabricate [Object]. §4.56
              val targs: List[TypeTree] =
                // `[Object]` is what the frontend's unchecked conversion fills a RAW `new` with;
                // it is not a fact about the element and is replaced exactly as `Nil` is.
                val allObject = targsFromType.nonEmpty && targsFromType.forall(tt => headSym(tt.tpe).contains(objectSym))
                if dropTrailing > 0 && (targsFromType.isEmpty || allObject) then
                  val droppedArgs = t.args.takeRight(dropTrailing)
                  val supplierDerived = droppedArgs.collectFirst {
                    case mr: Tree.MethodRef => mr.qualifier match
                      case Left(tt) => tt.tpe match
                        case TypeRepr.AppliedType(tc, List(componentType)) if headSym(tc).flatMap(p.symbolOf).exists(_.fullName == "scala.Array") =>
                          List(TypeTree(componentType, t.origin))
                        case _ => Nil
                      case Right(term) => term.tpe match
                        case TypeRepr.AppliedType(tc, List(componentType)) if headSym(tc).flatMap(p.symbolOf).exists(_.fullName == "scala.Array") =>
                          List(TypeTree(componentType, t.origin))
                        case _ => Nil
                  }
                  supplierDerived.getOrElse(targsFromType)
                else targsFromType
              val ident = Tree.Ident(factorySym, TypeRepr.NoType, t.origin)
              val fun: Term =
                if targs.nonEmpty then Tree.TypeApply(ident, targs, TypeRepr.NoType, t.origin)
                else ident
              Tree.Apply(fun, effectiveArgs, factorySym, n.tpe, t.origin)
            }
          case CollectionsTransform.RetargetRewrite.Template(expr) =>
            Some(renderTemplate(expr, Tree.Ident(SymId.None, n.tpe, t.origin), t.args, srcFqn, n.tpe, t.origin))
          case _ => scala.None // Rename/BoolDispatch at <init> is meaningless; ignore
        }
      }
    case _ => scala.None

  private[transform] def pinnedByObject(recv: Term, m: SymId, t: Tree.Apply)(using p: Program): Option[Term] =
    /** is this parent's probe position `java.lang.Object` HERE — written so, or instantiated so? */
    def probeIsObject(probe: TypeRepr, mp: MintedParents, recvTpe: TypeRepr): Boolean =
      headSym(probe).exists { h =>
        h == objectSym || (mp.tparams.indexOf(h) match
          case -1 => false
          case i  => recvTpe match
            case TypeRepr.AppliedType(_, as) if as.sizeIs > i => headSym(as(i)).contains(objectSym)
            case _                                            => false)
      }
    for
      _    <- Option.when(t.args.sizeIs == 1)(())
      s    <- p.symbolOf(m)
      mp   <- parentClash.get(s.owner)
      sigs  = mp.kinds.flatMap(k => CollectionsTransform.ShadowedByTarget.getOrElse(k.toString, Set.empty))
      if sigs.contains(CollectionsTransform.MemberSig(s.name, 1))
      d    <- p.definitionOf(m).collect { case x: Tree.DefDef => x }
      ps    = d.paramss.flatten
      if ps.sizeIs == 1 && headSym(ps.head.tpt.tpe).contains(objectSym)
      arg   = t.args.head
      if !headSym(arg.tpe).contains(objectSym)
      if !mp.probes.exists(probeIsObject(_, mp, actualOf(recv)._1))
    yield
      val tpe = TypeRepr.TypeRef(TypeRepr.NoPrefix, objectSym)
      t.copy(args = List(Tree.Typed(arg, TypeTree(tpe, arg.origin), tpe, arg.origin)))

