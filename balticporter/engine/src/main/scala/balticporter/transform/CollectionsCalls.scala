package balticporter.transform

import balticporter.tir.*

/** JDK kind-aware call rewrites, static utility rewrites and their helpers, split out of CollectionsTransform (context diet S3). */
private[transform] trait CollectionsCalls:
  self: CollectionsTransform =>
  import CollectionsTransform.{JavaCollectionFqn, JavaCollectionsFqn, JavaIterableFqn, JavaIteratorFqn, Kind}

  /** scala nullary accessors that take NO parens (`def size: Int`) — a Java `size()`
    * emitted as `size()` would be an illegal application. Strip the `Apply`. */
  private[transform] val parenless = Set("size", "isEmpty", "iterator", "keySet", "values", "nonEmpty", "hasNext", "next")

  /** Java's collection copy constructor — `new ArrayList<>(c)`, `new HashSet<>(c)`, etc. A
    * capacity hint (`new ArrayList<>(10)`) maps correctly by accident; a COPY needs
    * `<Companion>.from(c)` instead, gated on the argument being a collection. */

  /** Java's class-token constructor — `new EnumMap<K, V>(K.class)` — routed to a named factory
    * since the shim orders by `ordinal` and the token has nothing to size. Ordered before
    * [[copyConstructor]]; disjoint anyway (takes a `classOf[…]` literal). */
  private[transform] def tokenConstructor(t: Tree.Apply)(using Program): Option[Term] = t.fun match
    case n: Tree.New if enumMapOfTypeSym != SymId.None =>
      val isToken = t.args match
        case List(Tree.Literal(Constant.ClassOfC(_), _, _)) => true
        case _                                              => false
      for tgt <- headSym(n.tpe) if isToken && tgt == byScalaSym(CollectionsTransform.JavaEnumMapFqn)
      yield Tree.Apply(Tree.Ident(enumMapOfTypeSym, TypeRepr.NoType, t.origin), t.args,
                       enumMapOfTypeSym, n.tpe, t.origin)
    case _ => scala.None

  private[transform] def copyConstructor(t: Tree.Apply)(using Program): Option[Term] = t.fun match
    case n: Tree.New =>
      val target = headSym(n.tpe).filter(kindOf.contains)
      val single = t.args match
        case List(a) if headSym(a.tpe).exists(kindOf.contains) => Some(a)
        case _                                                 => scala.None
      for
        tgt <- target
        arg <- single
        f   <- fromSyms.get(tgt)
      // typed as the TARGET, not the argument: new HashMap<>(aTreeMap) is a HashMap
      yield Tree.Apply(Tree.Ident(f, TypeRepr.NoType, t.origin), List(scalaView(arg)), f, n.tpe, t.origin)
    case _ => scala.None

  /** Java's capacity-hint constructor at a HASHED collection — `new HashMap<>(16)`. Unlike the
    * sequence targets, scala's `mutable.HashMap` has no one-arg `(initialCapacity: Int)`
    * constructor, so the java one-arg form is completed with `defaultLoadFactor` (0.75, java's own
    * `DEFAULT_LOAD_FACTOR`) rather than left to fail (M6). Disjoint from [[copyConstructor]] by
    * argument type. */
  private[transform] def capacityConstructor(t: Tree.Apply)(using Program): Option[Term] = t.fun match
    case n: Tree.New =>
      val isInt = t.args match
        case List(a) => headSym(a.tpe).flatMap(summon[Program].symbolOf).exists(_.fullName == "scala.Int")
        case _       => false
      for
        tgt <- headSym(n.tpe)
        lf  <- loadFactorSyms.get(tgt) if isInt
      yield Tree.Apply(t.fun, t.args :+ Tree.Ident(lf, TypeRepr.NoType, t.origin), t.method, t.tpe, t.origin)
    case _ => scala.None


  /** `java.util.Collections`' static utilities — receiver-less, so `rewrite` never sees them and
    * the call is emitted verbatim against the real JDK class unless mapped here. Keyed on
    * `owner#name` (`PortabilityCheck.exactMember`'s identification). Deliberately small: an
    * unmapped static fails to COMPILE rather than silently approximating (a read-only `Buffer`
    * view for `unmodifiableList` would drop the immutability with a green compile). */
  private[transform] def staticRewrite(t: Tree.Apply)(using p: Program): Option[Term] =
    def qualified(s: SymId) = for
      m <- p.symbolOf(s)
      o <- p.symbolOf(m.owner)
    yield MemberKey(o.fullName, m.name).render
    val member  = qualified(t.method)
    // an explicitly-instantiated call arrives as Apply(TypeApply(Select(xs, m))), not Apply(Select(...))
    val recv    = t.fun match
      case Tree.Select(r, _, _, _)                          => Some(r)
      case Tree.TypeApply(Tree.Select(r, _, _, _), _, _, _)  => Some(r)
      case _                                                 => None
    def factory(f: SymId, args: List[Term]) =
      Tree.Apply(Tree.Ident(f, TypeRepr.NoType, t.origin), args, f, t.tpe, t.origin)
    (member, t.args) match
      case (Some("java.util.Collections#unmodifiableCollection"), List(c)) if unmodifiableSym != SymId.None =>
        Some(factory(unmodifiableSym, List(c)))

      // ---- java.util.Collections / Map.Entry statics — see JavaCollections ----
      case (Some("java.util.Collections#sort"), List(xs, cmp))    => Some(factory(sym("sort"), List(xs, cmp)))
      case (Some("java.util.Collections#sort"), List(xs))         => Some(factory(sym("sortNatural"), List(xs)))
      case (Some("java.util.Collections#reverse"), List(xs))      => Some(factory(sym("reverse"), List(xs)))
      case (Some("java.util.Collections#swap"), List(xs, i, j))   => Some(factory(sym("swap"), List(xs, i, j)))
      case (Some("java.util.Collections#shuffle"), List(xs, rnd))  => Some(factory(sym("shuffle"), List(xs, rnd)))
      // the IMMUTABLE PRODUCERS: a value the JDK hands BACK at a slot this phase already moved,
      // so nothing coerces it — the rewrite must produce the scala value directly. Targets
      // REPRODUCE java's immutability rather than dropping it (mutable.ArrayBuffer.empty would
      // turn an UnsupportedOperationException into a silent write).
      case (Some("java.util.Collections#emptyList"), Nil)           => Some(factory(sym("emptyList"), Nil))
      case (Some("java.util.Collections#emptyMap"), Nil)            => Some(factory(sym("emptyMap"), Nil))
      case (Some("java.util.Collections#emptySet"), Nil)            => Some(factory(sym("emptySet"), Nil))
      case (Some("java.util.Collections#singletonList"), List(x))   => Some(factory(sym("singletonList"), List(x)))
      case (Some("java.util.Collections#singleton"), List(x))       => Some(factory(sym("singleton"), List(x)))
      case (Some("java.util.Collections#singletonMap"), List(k, v)) => Some(factory(sym("singletonMap"), List(k, v)))
      // unmodifiable VIEWS: scala has no read-only Buffer/Set/Map view (K6), so the runtime's
      // Frozen* delegate every READ to the wrapped collection.
      // java.util.EnumSet has no public constructor; class tokens are KEPT (not dropped) since
      // allOf/range/complementOf need the enum's constants via Class.getEnumConstants
      case (Some("java.util.EnumSet#noneOf"), List(c))       => Some(factory(enumSetSym("noneOf"), List(c)))
      case (Some("java.util.EnumSet#allOf"), List(c))        => Some(factory(enumSetSym("allOf"), List(c)))
      case (Some("java.util.EnumSet#copyOf"), List(c))       => Some(factory(enumSetSym("copyOf"), List(c)))
      case (Some("java.util.EnumSet#range"), List(a, b))     => Some(factory(enumSetSym("range"), List(a, b)))
      case (Some("java.util.EnumSet#complementOf"), List(s)) => Some(factory(enumSetSym("complementOf"), List(s)))
      case (Some("java.util.EnumSet#of"), args)              => Some(factory(enumSetSym("of"), args))
      // primitive optionals: target is an alias for Option[…], so of(x) IS Some(x), empty() IS None.
      // ofNullable has no arm — OptionalInt cannot be null and reference Optional is not mapped.
      case (Some("java.util.OptionalInt#of" | "java.util.OptionalLong#of" | "java.util.OptionalDouble#of"), List(x)) =>
        Some(factory(someSym, List(x)))
      case (Some("java.util.OptionalInt#empty" | "java.util.OptionalLong#empty" | "java.util.OptionalDouble#empty"), Nil) =>
        Some(Tree.Ident(noneSym, t.tpe, t.origin))
      case (Some("java.util.Collections#unmodifiableList"), List(c)) => Some(factory(sym("unmodifiableList"), List(c)))
      case (Some("java.util.Collections#unmodifiableSet"), List(c))  => Some(factory(sym("unmodifiableSet"), List(c)))
      case (Some("java.util.Collections#unmodifiableMap"), List(c))  => Some(factory(sym("unmodifiableMap"), List(c)))
      // Arrays.asList shares the table (same kind of receiver-less JDK factory); its runtime
      // counterpart is the ONE rewritten static using a scala vararg — see [[asListArgs]]
      case (Some("java.util.Arrays#asList"), args)                 =>
        asListArgs(args) match
          // explicit type argument for mixed-type lists (scalac needs it for boxing)
          case AsList.Elements(as) =>
            Some(elementArg(t).fold(factory(sym("asList"), as))(a =>
              Tree.Apply(Tree.TypeApply(Tree.Ident(sym("asList"), TypeRepr.NoType, t.origin), List(a),
                                        TypeRepr.NoType, t.origin),
                         as, sym("asList"), t.tpe, t.origin)))
          case AsList.Aliased(arr) => Some(factory(sym("asListView"), List(asListViewArg(arr, t))))
      // `Map.Entry` became a `Tuple2`, so `Entry`'s own statics must come along or the call survives
      // to the compiler naming a type the port no longer produces.
      case (Some("java.util.Map$Entry#comparingByKey" | "java.util.Map.Entry#comparingByKey"), List(cmp)) =>
        Some(factory(sym("comparingByKey"), List(cmp)))
      case (Some("java.util.Map$Entry#comparingByValue" | "java.util.Map.Entry#comparingByValue"), List(cmp)) =>
        Some(factory(sym("comparingByValue"), List(cmp)))

      // java.util.stream: chain collapses to scala collection operations // ENGINE-LIMITS K6
      case (Some("java.util.Collection#stream" | "java.util.List#stream" | "java.util.Set#stream"), Nil) =>
        recv.map(streamSource(_, t.method))
      // `IntStream.range(a, b)` is a stream SOURCE with no collection behind it — the one shape the
      // "only collapse a collapsed receiver" rule would otherwise leave untranslated forever, since
      // nothing can ever collapse it. It becomes the range itself, and the chain proceeds normally.
      case (Some("java.util.stream.IntStream#range"), List(a, b)) =>
        Some(Tree.Apply(Tree.Ident(sym("intRange"), TypeRepr.NoType, t.origin), List(a, b),
                        sym("intRange"), asBuffer(t.tpe), t.origin))
      // The TYPE APPLICATION is carried across, and it is load-bearing: java's
      // `mapToObj(i -> i)` against `Stream<Integer>` BOXES, and `Buffer[Int].map(i => i)` does not —
      // it yields `Buffer[Int]`, which then fails to be a `Collection<Integer>` one call further out.
      // Re-applying the explicit `[Integer]` gives the lambda body the expected type java gave it,
      // and scala inserts the same boxing.
      case (Some("java.util.stream.IntStream#mapToObj" | "java.util.stream.Stream#map"), List(f)) if collapsed(recv) =>
        val targs = t.fun match { case Tree.TypeApply(_, ts, _, _) => ts; case _ => Nil }
        recv.map { r =>
          val sel: Term = Tree.Select(r, mapSym, TypeRepr.NoType, t.origin)
          val fun = if targs.isEmpty then sel else Tree.TypeApply(sel, targs, TypeRepr.NoType, t.origin)
          Tree.Apply(fun, List(f), mapSym, asBuffer(r.tpe), t.origin)
        }
      // a stream operation is rewritten only when its receiver is a collection this phase ALREADY
      // collapsed, never on the method name alone — a stream from a non-collection source (e.g.
      // "…".lines()) is simply not translated.
      case (Some("java.util.stream.Stream#mapToDouble"), List(f)) if collapsed(recv) =>
        recv.map(r => Tree.Apply(Tree.Ident(sym("mapToDouble"), TypeRepr.NoType, t.origin), List(r, f),
                                 sym("mapToDouble"), asBuffer(r.tpe), t.origin))
      case (Some("java.util.stream.DoubleStream#sum" | "java.util.stream.IntStream#sum" |
                 "java.util.stream.LongStream#sum"), Nil) if collapsed(recv) =>
        recv.map(r => Tree.Select(r, sumSym, t.tpe, t.origin))
      case (Some("java.util.stream.Stream#sorted"), List(cmp)) if collapsed(recv) =>
        recv.map(r => Tree.Apply(Tree.Ident(sym("sortedWith"), TypeRepr.NoType, t.origin), List(r, cmp),
                                 sym("sortedWith"), r.tpe, t.origin))
      case (Some("java.util.stream.Stream#collect"), List(collector))
          if collapsed(recv) && qualified(collectorOf(collector)).contains("java.util.stream.Collectors#toCollection") =>
        // toCollection(Factory::new) carries its target inside the collector, as a factory
        val f = collector match { case a: Tree.Apply => a.args; case _ => Nil }
        if f.sizeIs != 1 then None
        else recv.map(r => factory(sym("into"), List(r, f.head)))
      case (Some("java.util.stream.Stream#filter"), List(pred)) if filteredSym != SymId.None && collapsed(recv) =>
        recv.map(r => Tree.Apply(Tree.Ident(filteredSym, TypeRepr.NoType, t.origin), List(r, pred),
                                 filteredSym, r.tpe, t.origin))
      // `anyMatch`/`allMatch` = `exists`/`forall`; `noneMatch` is a helper.
      case (Some("java.util.stream.Stream#anyMatch"), List(pred)) if existsSym != SymId.None && collapsed(recv) =>
        recv.map(r => Tree.Apply(Tree.Select(r, existsSym, TypeRepr.NoType, t.origin), List(pred),
                                 existsSym, t.tpe, t.origin))
      case (Some("java.util.stream.Stream#allMatch"), List(pred)) if forallSym != SymId.None && collapsed(recv) =>
        recv.map(r => Tree.Apply(Tree.Select(r, forallSym, TypeRepr.NoType, t.origin), List(pred),
                                 forallSym, t.tpe, t.origin))
      case (Some("java.util.stream.Stream#noneMatch"), List(pred)) if sym("noneMatch") != SymId.None && collapsed(recv) =>
        recv.map(r => factory(sym("noneMatch"), List(r, pred)))
      // `collect(toList)` terminal — the receiver already IS the sequence.
      case (Some("java.util.stream.Stream#collect"), List(collector))
          if collapsed(recv) && qualified(collectorOf(collector)).contains("java.util.stream.Collectors#toList") =>
        recv
      // `collect(toSet)` / `collect(toMap)` — need helpers, cannot guess target type.
      case (Some("java.util.stream.Stream#collect"), List(collector))
          if collapsed(recv) && qualified(collectorOf(collector)).contains("java.util.stream.Collectors#toSet") =>
        recv.map(r => factory(sym("toSet"), List(r)))
      case (Some("java.util.stream.Stream#collect"), List(collector))
          if collapsed(recv) && qualified(collectorOf(collector)).contains("java.util.stream.Collectors#toMap") =>
        // mappers are inside the collector; java has a two- or three-arg form and nothing else
        val fs = collector match { case a: Tree.Apply => a.args; case _ => Nil }
        if fs.sizeIs != 2 && fs.sizeIs != 3 then None else recv.map(r => factory(sym("toMap"), r :: fs))
      case _ => None

  /** Build args for `JavaCollections.asList`. Elements (packed or `Repeated`) are opened;
    * a single caller-held array becomes a live `asListView` (aliased writes preserved).
    * Returns `AsList.Refuse` to leave the JDK call untranslated. // ENGINE-LIMITS K6.5 */
  private[transform] def asListArgs(args: List[Term])(using p: Program): AsList =
    def isArray(t: TypeRepr) = headSym(t).flatMap(p.symbolOf).exists(_.fullName == "scala.Array")
    args match
      case init :+ Tree.NewArray(_, Nil, Some(elems), _, _) => AsList.Elements(init ++ elems)
      // the external-callee shape of the same pack — opened, never read as one array argument
      case init :+ Tree.Repeated(elems, _, _)               => AsList.Elements(init ++ elems)
      // java forwards an array through T... as a Tree.Spread at an external callee (arr*); the
      // spread comes off since asListView takes the array itself
      case List(Tree.Spread(e, _, _)) if isArray(e.tpe)     => AsList.Aliased(e)
      case List(a) if isArray(a.tpe)                        => AsList.Aliased(a)
      case _                                                => AsList.Elements(args)

  /** The element type a `TypeTree` may be written for — java's own inference, made explicit.
    * Yielded only when the result really names one type: not a wildcard (K10), not an unresolved
    * marker (G2), not `NoType`. Otherwise left to scala's own inference. */
  private[transform] def elementArg(t: Tree.Apply)(using p: Program): Option[TypeTree] =
    soleTypeArg(t.tpe).collect {
      case a if a != TypeRepr.NoType && !a.isInstanceOf[TypeRepr.TypeBounds] && !namesUnresolved(a) =>
        TypeTree(a, t.origin)
    }

  /** Does this type mention an inference marker (G2) or wildcard (K10) anywhere inside it —
    * either of which cannot be written as an explicit type argument? Read through
    * `Symbol.isUnresolvedTypeVar`, never a local spelling. */
  private[transform] def namesUnresolved(t: TypeRepr)(using p: Program): Boolean = t match
    case TypeRepr.TypeRef(_, s)      => p.symbolOf(s).exists(x => Symbol.isUnresolvedTypeVar(x.fullName))
    case TypeRepr.AppliedType(c, as) => namesUnresolved(c) || as.exists(namesUnresolved)
    case _: TypeRepr.TypeBounds      => true
    case TypeRepr.AndType(l, r)      => namesUnresolved(l) || namesUnresolved(r)
    case TypeRepr.OrType(l, r)       => namesUnresolved(l) || namesUnresolved(r)
    case _                           => false

  /** The argument `asListView` should receive at `Arrays.asList(T[])`. Java's erased formal is
    * `Object[]`, so the frontend synthesises `arr.asInstanceOf[Array[Object]]` off it (G14);
    * `asListView[A]` infers `A` from the argument, so the cast must be stripped or it infers
    * `Object`. Strip when the cast wraps an array whose element type is the call's own result type
    * (§4.56, structural, names no type) — a genuine `(Object[]) value` cast survives underneath. */
  private[transform] def asListViewArg(arg: Term, call: Tree.Apply): Term = arg match
    case Tree.Typed(inner, _, _, _) =>
      val wanted = soleTypeArg(call.tpe)
      val have   = soleTypeArg(inner.tpe)
      if wanted.isDefined && wanted == have then inner else arg
    case _ => arg

  /** The single type argument of an applied type, or `None` — the one shape [[asListViewArg]]
    * compares. Not a general "element type of": `Buffer[A]`/`Array[A]` both have exactly one. */
  private[transform] def soleTypeArg(t: TypeRepr): Option[TypeRepr] = t match
    case TypeRepr.AppliedType(_, List(a)) if a != TypeRepr.NoType => Some(a)
    case _                                                        => scala.None

  /** Which of `Arrays.asList`'s two java shapes a call site is — see [[asListArgs]]. Not an
    * `Option`: the aliasing form is a DIFFERENT helper, not "no arguments to pass". */
  private[transform] enum AsList:
    case Elements(args: List[Term])
    case Aliased(array: Term)

  /** A `JavaCollections` static by name. Minted eagerly in `run`, before the traversal consults
    * it. An unlisted name yields `SymId.None`, treated as "not available", never a dangling ref. */
  private[transform] def sym(name: String): SymId = staticSyms.getOrElse(name, SymId.None)

  /** the method a collector expression calls, so `collect`'s argument can be identified. */
  private[transform] def collectorOf(t: Term): SymId = t match
    case a: Tree.Apply => a.method
    case _             => SymId.None

  /** Convert a `stream()` receiver to the scala sequence the collapse consumes.
    * Shims use `asScalaBuffer`; `Set`/`Map` sources use `.toBuffer`. */
  private[transform] def streamSource(r: Term, m: SymId)(using p: Program): Term =
    val effective = headSym(r.tpe)
      .filter(s => kindOf.contains(s) || shimSyms.contains(s))
      .orElse(p.symbolOf(m).flatMap(x => p.symbolOf(x.owner)).flatMap(o => remap.get(o.id)))
    effective match
      case Some(s) if shimSyms.contains(s) && asScalaBufferSym != SymId.None =>
        Tree.Select(r, asScalaBufferSym, asBuffer(r.tpe), r.origin)
      case Some(s) if kindOf.get(s).contains(Kind.Seq)                       => r
      case Some(s) if kindOf.get(s).contains(Kind.Set) && toBufferSym != SymId.None =>
        Tree.Select(r, toBufferSym, asBuffer(r.tpe), r.origin)
      // a Map[K, V] copies to a Buffer[(K, V)] — arity changes, so asBuffer's head-swap would
      // be wrong; the bare constructor is the honest record and only its head is ever read
      case Some(s) if kindOf.get(s).contains(Kind.Map) && toBufferSym != SymId.None =>
        Tree.Select(r, toBufferSym, TypeRepr.TypeRef(TypeRepr.NoPrefix, bufferSym), r.origin)
      case _ => r

  /** The other direction from [[coerce]]: a shim reaching a slot that wants a scala collection —
    * `new ArrayList<>(c)` routed through `ArrayBuffer.from`, which needs an `IterableOnce`. Both
    * directions exist because the two families are deliberately unrelated. */
  private[transform] def scalaView(t: Term): Term =
    if asScalaBufferSym != SymId.None && headSym(t.tpe).exists(shimSyms.contains)
    then Tree.Select(t, asScalaBufferSym, asBuffer(t.tpe), t.origin)
    else t

  /** True when the receiver is already collapsed from a `Stream` to a scala collection. Shims
    * excluded (collapse consumes them, not produces). Keyed on `kindOf`. */
  private[transform] def collapsed(recv: Option[Term]): Boolean =
    recv.flatMap(r => headSym(r.tpe)).exists(s => kindOf.get(s).contains(Kind.Seq) && !shimSyms.contains(s))

  /** The same type with `Buffer` as its head — what `asScalaBuffer` on a `JavaCollection[E]`
    * returns. Falls back to a bare `Buffer` when the input has no head (`NoType`, common on an
    * external call's node) — the head is the only part any caller reads. */
  private[transform] def asBuffer(t: TypeRepr): TypeRepr =
    if bufferSym == SymId.None then t
    else
      val h = withHead(t, bufferSym)
      if headSym(h).contains(bufferSym) then h else TypeRepr.TypeRef(TypeRepr.NoPrefix, bufferSym)

  /** Could this value be a representation this phase introduced? A type it retyped, one of its
    * own shims, or `java.lang.Object` (says nothing). Read from the phase's own tables (§4.56). */
  private[transform] def mayBeRetypedValue(a: Term)(using p: Program): Boolean =
    headSym(a.tpe).exists(s => kindOf.contains(s) || shimSyms.contains(s) ||
      p.symbolOf(s).exists(_.fullName == CollectionsTransform.ObjectFqn))

  /** `java.lang.Object` as this program spells it — the bridge's result type. Falls back to the
    * argument's own type where the program never names `Object`. */
  private[transform] def objectTpe(a: Term)(using p: Program): TypeRepr =
    p.symbols.all.find(_.fullName == CollectionsTransform.ObjectFqn)
      .map(s => TypeRepr.TypeRef(TypeRepr.NoPrefix, s.id)).getOrElse(a.tpe)


  /** the runtime shims, as scala symbols — a source already typed as one is never re-wrapped. */
  private[transform] def shimSyms: Set[SymId] =
    Set(javaIterableSym, javaIteratorSym, javaListIteratorSym, javaCollectionSym)

  /** the shims as FQNs, so a `typeMap` target can be recognised as one — [[shimSyms]] answers
    * only for a program that names the shim's java original, interned on first reference. */
  private[transform] def shimFqns: Set[String] = CollectionsTransform.ShimFqns

  /** True when the type is a shim or inherits from one (transitively, fuel-bounded).
    * Suppresses arity rewrites on receivers that carry java's member shape. */
  private[transform] def shimShaped(t: TypeRepr)(using p: Program): Boolean =
    def isShim(s: SymId): Boolean =
      shimSyms.contains(s) ||
        p.symbolOf(s).map(_.fullName).exists(fq => typeMap.get(fq).exists((tgt, _) => shimFqns(tgt)))
    // what sits above this symbol: a class's parents, or a type parameter's upper bound (a
    // receiver typed I extends Cursor<Integer> has Cursor's members exactly as a subclass would).
    def above(s: SymId): List[SymId] = p.definitionOf(s) match
      case Some(c: Tree.ClassDef) => c.parents.flatMap {
        case tt: TypeTree => headSym(tt.tpe)
        case x: Term      => headSym(x.tpe)
      }
      case Some(td: Tree.TypeDef) => td.rhs.tpe match
        case TypeRepr.TypeBounds(_, hi) => headSym(hi).toList
        case other                      => headSym(other).toList
      case _ => Nil
    def go(s: SymId, fuel: Int): Boolean =
      s != SymId.None && fuel > 0 && (isShim(s) || above(s).exists(go(_, fuel - 1)))
    headSym(t).exists(go(_, 16))


  /** kind-aware call rewrite; `None` = leave the call as-is (same-named method binds to
    * the scala API against the retyped receiver at compile time). */
  private[transform] def rewrite(k: Kind, recv: Term, m: SymId, so: Origin, t: Tree.Apply)(using Program): Option[Term] =
    val name = methodName(m)
    /** is the receiver one of the runtime SHIMS rather than a scala collection? Java's arity and
      * member names mean the scala-shaped rewrites below must leave them alone — a blanket refusal
      * (`case _ if onShim`), asked of the ANCESTRY via [[shimShaped]], not the head symbol. */
    val onShim = shimShaped(recv.tpe)
    /** is the receiver `super`? Scala allows `super` only as a Select qualifier; several rewrites
      * below would otherwise place it elsewhere (E040 syntax error). Checked structurally after the
      * rewrite is built — see [[superPlaced]] — so a new arm cannot reintroduce it by omission. */
    val onSuper = recv.isInstanceOf[Tree.Super]
    val out = (name, t.args, k) match
      // java 8's forEach has no shim counterpart; JavaIterable supplies foreach as an extension (§4.5).
      case ("forEach", List(f), _) => Some(call(recv, foreachSym, List(f), t, so))
      // `toArray`: strip erasure coercion via `arrayArg` (no call reshape needed)
      // // ENGINE-LIMITS G14
      // than a new callee.
      case ("toArray", List(a), _) if onShim =>
        val stripped = arrayArg(a, t)
        Option.when(stripped ne a)(t.copy(args = List(stripped)))
      // wildcard capture read coercion: `asInstanceOf[Object]` for unbounded `?` on a shim
      // // ENGINE-LIMITS G23, G24, G33
      case _ if onShim && wildcardElement(recv.tpe) && capturedObjectRead(t) =>
        Some(Tree.Typed(t, TypeTree(t.tpe, t.origin), t.tpe, t.origin))
      case _ if onShim             => None
      // JDK bulk defaults (`containsAll`/`addAll`/`removeAll`/`retainAll`) via VirtualJdkDefaults
      // // ENGINE-LIMITS K29
      case (n, List(c), Kind.Seq | Kind.Set)
        if onSuper && CollectionsTransform.VirtualJdkDefaults.contains(n)
           && sym(CollectionsTransform.VirtualJdkDefaults(n)) != SymId.None
           && superLostItsDefault(recv, n) =>
        val f = sym(CollectionsTransform.VirtualJdkDefaults(n))
        recv match { case Tree.Super(cls, _, _) => superDefaults += ((cls, m, n)); case _ => () }
        Some(Tree.Apply(Tree.Ident(f, TypeRepr.NoType, so), List(thisOf(recv), c), f, t.tpe, t.origin))
      // Stack push/pop/peek/search are shim members, no rewrite. empty() can't be java's — scala's
      // `empty` is already the factory — so it's renamed to the predicate asking the same question.
      case ("empty", Nil, Kind.Stack) => Some(Tree.Select(recv, isEmptySym, t.tpe, t.origin))
      case ("push" | "pop" | "peek" | "search", _, Kind.Stack) => None
      // java.util.Optional{Int,Long,Double}, target is an Option[...] alias — pure renames
      // except orElse. get/isDefined are parameterless (Select, not Apply). orElseThrow() is get
      // (both throw NoSuchElementException on empty); the supplier overload has no arm.
      case ("getAsInt" | "getAsLong" | "getAsDouble" | "orElseThrow", Nil, Kind.Opt) =>
        Some(Tree.Select(recv, getSym, t.tpe, t.origin))
      case ("isPresent", Nil, Kind.Opt)      => Some(Tree.Select(recv, isDefinedSym, t.tpe, t.origin))
      // orElse is the one non-rename: java evaluates the argument eagerly, Option.getOrElse
      // lazily — optionalOrElse restores java's by-value evaluation. CLAUDE.md §4.4
      case ("orElse", List(d), Kind.Opt) if sym("optionalOrElse") != SymId.None =>
        val f = sym("optionalOrElse")
        Some(Tree.Apply(Tree.Ident(f, TypeRepr.NoType, so), List(recv, d), f, t.tpe, t.origin))
      case ("ifPresent", List(f), Kind.Opt)  => Some(call(recv, foreachSym, List(f), t, so))
      // m.entrySet() is the VIEW of the map as (key, value) pairs; a scala Map[K, V] already IS
      // one, so the view is the map itself (Tuple2 loses setValue write-through). list.iterator()
      // yields a scala.collection.Iterator, but a java.util.Iterator-derived declaration wants the
      // removal-capable shim; decided on provenance.
      case ("iterator", Nil, _) if iteratorFromSym != SymId.None =>
        val sel = Tree.Select(recv, m, t.tpe, t.origin) // parenless, as the generic case below
        Some(Tree.Apply(Tree.Ident(iteratorFromSym, TypeRepr.NoType, so), List(sel), iteratorFromSym, t.tpe, so))
      // list.listIterator()/listIterator(i) — java's bidirectional cursor, refused as
      // scala.collection.Iterator (K23) but the receiver is mutable.Buffer, whose indexed
      // read/update/insert/remove ARE ListIterator's contract — a §4.5 standalone shim.
      // `over` writes through to the caller's buffer; Kind.Seq only, java declares it on List
      case ("listIterator", args @ (Nil | List(_)), Kind.Seq | Kind.Stack)
        if listIteratorOverSym != SymId.None =>
        Some(Tree.Apply(Tree.Ident(listIteratorOverSym, TypeRepr.NoType, so), recv :: args,
                        listIteratorOverSym, t.tpe, so))
      // c.spliterator() — K23's other refusal, kept refused unlike listIterator: nothing about
      // streams is modelled, so java's DEFAULT METHOD characteristics are stated directly
      // (Collection=0, List=ORDERED, Set=DISTINCT, all OR SIZED|SUBSIZED) rather than delegated
      // to the converter's wrapper — they follow JAVA'S declaration at the receiver's kind.
      case ("spliterator", Nil, k @ (Kind.Seq | Kind.Stack | Kind.Set))
        if orderedSpliteratorSym != SymId.None =>
        val f = if k == Kind.Set then distinctSpliteratorSym else orderedSpliteratorSym
        Some(Tree.Apply(Tree.Ident(f, TypeRepr.NoType, so), List(recv), f, t.tpe, so))
      // m.values() is the same provenance problem as iterator() above: Map.values() is declared
      // Collection<V>, so downstream slots want the shim while the emitted m.values is scala's
      // Iterable. Wrapping restores the invariant that a node's type describes what it emits.
      // unmodifiableFrom, not from: java's values() is a read-only view.
      case ("values", Nil, Kind.Map) if unmodifiableFromSym != SymId.None =>
        val sel = Tree.Select(recv, m, t.tpe, t.origin) // parenless, as the generic case below
        Some(Tree.Apply(Tree.Ident(unmodifiableFromSym, TypeRepr.NoType, so), List(sel), unmodifiableFromSym, t.tpe, so))
      // m.keySet()/m.entrySet() are java's live, write-through map views — the same provenance
      // gap values() has, widest here (keySet lost a capability, entrySet lost the Set shape
      // entirely). Fixed at the source: the rewrite emits a value that really has the type the
      // node claims, so every downstream position is answered at once (11 errors closed).
      // A VIEW, not a copy — java's is live in both directions.
      case ("keySet", Nil, Kind.Map) if sym("keySetView") != SymId.None =>
        Some(staticCall(sym("keySetView"), List(recv), t, so))
      case ("entrySet", Nil, Kind.Map) if sym("entrySetView") != SymId.None =>
        Some(staticCall(sym("entrySetView"), List(recv), t, so))
      case ("entrySet", Nil, Kind.Map)          => Some(recv)
      case ("getKey", Nil, Kind.Entry)          => Some(Tree.Select(recv, key1Sym, t.tpe, t.origin))
      case ("getValue", Nil, Kind.Entry)        => Some(Tree.Select(recv, value2Sym, t.tpe, t.origin))
      // never on a shim receiver (blanket guard above): shims deliberately carry java's arity
      // (iterator(), hasNext(), next()), stripping () there emits it.hasNext against def hasNext()
      case (n, Nil, _) if parenless(n)          => Some(Tree.Select(recv, m, t.tpe, t.origin)) // drop `()`
      case ("get", List(i), Kind.Seq)           => Some(Tree.Apply(recv, List(i), m, t.tpe, t.origin)) // xs(i)
      // a wildcard-typed map is java's three Object-keyed members and nothing else (wildcardMapCall);
      // the same helpers answer an Object PROBE at a moved key type (objectProbe). keyArg runs first.
      case (n, List(key), Kind.Map) if wildcardMapCall(n, recv, keyArg(key, recv)) || probeMapCall(n, keyArg(key, recv), recv) =>
        Some(staticCall(wildcardMapSym(n), List(recv, keyArg(key, recv)), t, so))
      case ("get", List(key), Kind.Map)         => Some(call(recv, getOrElseSym, List(keyArg(key, recv), dflt(nullOf(so), recv, so)), t, so))
      case ("getOrDefault", List(key, d), _)    => Some(call(recv, getOrElseSym, List(keyArg(key, recv), dflt(d, recv, so)), t, so))
      case ("set", List(i, x), Kind.Seq)        => Some(call(recv, updateSym, List(i, x), t, so)) // xs(i) = x
      // java's Map.put RETURNS THE PREVIOUS VALUE; scala's put keeps it as an Option, so
      // getOrElse(null) restores java's contract that update() would have discarded
      case ("put", List(key, v), Kind.Map)      =>
        Some(call(call(recv, putSym, List(keyArg(key, recv), v), t, so), getOrElseSym, List(dflt(nullOf(so), recv, so)), t, so))
      // likewise `Map.remove`, which returns the value that was there.
      case ("remove", List(key), Kind.Map)      =>
        Some(call(call(recv, removeSym, List(keyArg(key, recv)), t, so), getOrElseSym, List(dflt(nullOf(so), recv, so)), t, so))
      // `remove(Object)` by-value overload — distinguished from `remove(int)` by result type (CLAUDE.md §4.4)
      case ("remove", List(x), Kind.Seq) if removesByValue(t) && sym("removeValue") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("removeValue"), TypeRepr.NoType, so), List(recv, x),
                        sym("removeValue"), t.tpe, t.origin))
      // Collection.toArray()/toArray(T[]): scala's toArray is parenless, so xs.toArray() misparses
      // as an Array index (missing argument for apply). JavaCollections helpers restore java's
      // contract — toArray() allocates Object[]; toArray(T[]) fills the caller's array or
      // allocates on the runtime component type with a null terminator (§4.4).
      case ("toArray", Nil, Kind.Seq | Kind.Set) if sym("toArray") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("toArray"), TypeRepr.NoType, so), List(recv),
                        sym("toArray"), t.tpe, t.origin))
      case ("toArray", List(a), Kind.Seq | Kind.Set) if sym("toArray") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("toArray"), TypeRepr.NoType, so), List(recv, arrayArg(a, t)),
                        sym("toArray"), t.tpe, t.origin))
      // subList is a write-through view (java) where slice is a copy; putIfAbsent returns the
      // PREVIOUS value (null on success), the opposite of getOrElseUpdate — §4.4 shapes.
      case ("subList", List(a, b), Kind.Seq) if sym("subList") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("subList"), TypeRepr.NoType, so), List(recv, a, b),
                        sym("subList"), t.tpe, t.origin))
      case ("putIfAbsent", List(key, v), Kind.Map) if sym("putIfAbsent") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("putIfAbsent"), TypeRepr.NoType, so),
                        List(recv, keyArg(key, recv), v), sym("putIfAbsent"), t.tpe, t.origin))
      // SE8 default methods on the interfaces, each with a scala near-miss: sort mutates in place
      // (sorted copies); computeIfAbsent treats null as absent and records nothing on a null
      // factory result; removeIf is filterInPlace's complement returning java's boolean;
      // containsValue/containsAll ask the PROBE's equals; ensureCapacity is a no-op hint.
      case ("sort", List(c), Kind.Seq) if sym("sort") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("sort"), TypeRepr.NoType, so), List(recv, c),
                        sym("sort"), t.tpe, t.origin))
      case ("computeIfAbsent", List(key, f), Kind.Map) if sym("computeIfAbsent") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("computeIfAbsent"), TypeRepr.NoType, so),
                        List(recv, keyArg(key, recv), f), sym("computeIfAbsent"), t.tpe, t.origin))
      // the Set spelling is a different helper, not an overload: the two erase alike, so the
      // choice is made by receiver kind at the call rather than by run-time dispatch.
      case ("removeIf", List(p), Kind.Seq) if sym("removeIf") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("removeIf"), TypeRepr.NoType, so), List(recv, p),
                        sym("removeIf"), t.tpe, t.origin))
      case ("removeIf", List(p), Kind.Set) if sym("removeIfSet") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("removeIfSet"), TypeRepr.NoType, so), List(recv, p),
                        sym("removeIfSet"), t.tpe, t.origin))
      case ("containsValue", List(v), Kind.Map) if sym("containsValue") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("containsValue"), TypeRepr.NoType, so), List(recv, v),
                        sym("containsValue"), t.tpe, t.origin))
      case ("containsAll", List(c), Kind.Seq | Kind.Set) if sym("containsAll") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("containsAll"), TypeRepr.NoType, so), List(recv, c),
                        sym("containsAll"), t.tpe, t.origin))
      // containsAll's two mutating siblings: mutable.Buffer has neither at all. `--=` removes one
      // occurrence per argument element where java removes every occurrence; filterInPlace keeps
      // the complement and returns the collection, not java's boolean.
      case ("removeAll", List(c), Kind.Seq | Kind.Set) if sym("removeAll") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("removeAll"), TypeRepr.NoType, so), List(recv, c),
                        sym("removeAll"), t.tpe, t.origin))
      case ("retainAll", List(c), Kind.Seq | Kind.Set) if sym("retainAll") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("retainAll"), TypeRepr.NoType, so), List(recv, c),
                        sym("retainAll"), t.tpe, t.origin))
      case ("ensureCapacity", List(n), Kind.Seq) if sym("ensureCapacity") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("ensureCapacity"), TypeRepr.NoType, so), List(recv, n),
                        sym("ensureCapacity"), t.tpe, t.origin))
      case ("add", List(i, x), Kind.Seq)        => Some(call(recv, insertSym, List(i, x), t, so)) // insert at index
      case ("add", List(x), _)                  => Some(infix(recv, opPlusEq, List(x), t, so))    // xs += x
      // java Deque (LinkedList/ArrayDeque): addLast/offer append, addFirst prepends.
      case ("addLast" | "offer" | "offerLast", List(x), Kind.Seq) => Some(infix(recv, opPlusEq, List(x), t, so))
      case ("addFirst" | "offerFirst", List(x), Kind.Seq)         => Some(call(recv, prependSym, List(x), t, so))
      // poll/peek return null on an empty deque; remove(0)/head throw, so a direct mapping would
      // turn "empty" into an exception. orNull is a Select (parameterless), never an Apply.
      case ("poll" | "pollFirst", Nil, Kind.Seq) =>
        Some(Tree.Select(call(recv, removeHeadOptionSym, Nil, t, so), orNullSym, t.tpe, so))
      case ("peek" | "peekFirst" | "element", Nil, Kind.Seq) =>
        Some(Tree.Select(Tree.Select(recv, headOptionSym, TypeRepr.NoType, so), orNullSym, t.tpe, so))
      // addAll from a wildcard-elemented source is not ++= — see [[wildcardElement]].
      case ("addAll", List(c), _) if (wildcardElement(c.tpe) || standaloneSource(c.tpe)) &&
                                     sym("addAll") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("addAll"), TypeRepr.NoType, so), List(recv, c),
                        sym("addAll"), t.tpe, t.origin))
      // java's positional addAll(int, Collection), insert's bulk sibling — left to fall through,
      // scala AUTO-TUPLES the two arguments against Growable.addAll(IterableOnce), silently
      // appending a pair at an Any element type instead of inserting (§4.4).
      case ("addAll", List(i, c), Kind.Seq | Kind.Stack) if sym("insertAll") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("insertAll"), TypeRepr.NoType, so), List(recv, i, c),
                        sym("insertAll"), t.tpe, t.origin))
      case ("addAll" | "putAll", List(c), _)    => Some(infix(recv, opPlusPlusEq, List(c), t, so))// xs ++= c
      // the Set half of objectProbe's seam: scala's Set.contains is already java's own lookup at
      // an ordinary argument, so this arm exists solely for the widened probe.
      case ("contains", List(x), Kind.Set) if setContainsSym != SymId.None && probeSetCall(x, recv) =>
        Some(staticCall(setContainsSym, List(recv, x), t, so))
      case ("remove", List(x), Kind.Set) if setRemoveSym != SymId.None && probeSetCall(x, recv) =>
        Some(staticCall(setRemoveSym, List(recv, x), t, so))
      case ("remove", List(x), Kind.Set)        => Some(infix(recv, opMinusEq, List(x), t, so)) // xs -= x
      case ("containsKey", List(key), Kind.Map) => Some(call(recv, containsSym, List(keyArg(key, recv)), t, so))
      // a Stack is a List for everything the five LIFO arms above did not take (Stack extends
      // Vector extends List) — a RE-ENTRY at Kind.Seq, not a second copy of the table.
      case _ if k == Kind.Stack                 => rewrite(Kind.Seq, recv, m, so, t)
      case _                                    => None
    if !onSuper then out
    else
      // where the shape puts `super` somewhere scala has no position for, retry standing on
      // `this` instead — exact only under [[superIsThis]]'s whole-program condition.
      out.filter(superPlaced).orElse(
        if superIsThis(recv, name) then rewrite(k, thisOf(recv), m, so, t).filter(superPlaced)
        else scala.None)

  /** May a rewrite that cannot stand on `super` stand on `this` instead — do `super.m` and
    * `this.m` name the same member for every value this expression can have? True iff neither the
    * class nor any subclass IN THIS PROGRAM declares `m` (the port cannot answer beyond its own
    * scope; the alternative is a refused rewrite that does not compile). Both walks read class
    * definitions, not the symbol table; the subclass walk is transitive. */
  private[transform] def superIsThis(recv: Term, member: String)(using p: Program): Boolean = recv match
    case Tree.Super(cls, _, _) if cls != SymId.None =>
      val all      = PackageRenameTransform.allClasses(p)
      val byId     = all.map(c => c.symbol -> c).toMap
      def declares(c: Tree.ClassDef): Boolean = c.body.exists {
        case d: Tree.DefDef => methodName(d.symbol) == member
        case _              => false
      }
      def parentsOf(c: Tree.ClassDef): List[SymId] = c.parents.flatMap {
        case tt: TypeTree => headSym(tt.tpe)
        case term: Term   => headSym(term.tpe)
      }
      /** does `c` reach `cls` through its parents? Fuel-bounded; an exhausted walk counts as
        * reaching, the conservative answer since the caller refuses on `true`. */
      def below(c: Tree.ClassDef, fuel: Int): Boolean =
        fuel <= 0 || parentsOf(c).exists(s => s == cls || byId.get(s).exists(below(_, fuel - 1)))
      byId.get(cls).exists(!declares(_)) &&
        !all.exists(c => c.symbol != cls && declares(c) && below(c, 64))
    case _ => false

  /** True when re-parenting removed the JDK default this `super.<member>` targeted and no
    * program-declared ancestor declares the member. */
  private[transform] def superLostItsDefault(recv: Term, member: String)(using p: Program): Boolean = recv match
    case Tree.Super(cls, _, _) if cls != SymId.None =>
      parentClash.get(cls).exists(_.kinds.nonEmpty) && !ancestorDeclares(cls, member)
    case _ => false

  /** does any class this PROGRAM declares, strictly ABOVE `cls`, declare `member`? */
  private[transform] def ancestorDeclares(cls: SymId, member: String)(using p: Program): Boolean =
    val byId = PackageRenameTransform.allClasses(p).map(c => c.symbol -> c).toMap
    def declares(c: Tree.ClassDef): Boolean = c.body.exists {
      case d: Tree.DefDef => methodName(d.symbol) == member
      case _              => false
    }
    def parentsOf(c: Tree.ClassDef): List[SymId] = c.parents.flatMap {
      case tt: TypeTree => headSym(tt.tpe)
      case term: Term   => headSym(term.tpe)
    }
    def up(id: SymId, fuel: Int): Boolean =
      fuel <= 0 || byId.get(id).exists(c => declares(c) || parentsOf(c).exists(up(_, fuel - 1)))
    byId.get(cls).exists(c => parentsOf(c).exists(up(_, 64)))

  /** the `this` standing where `recv`'s `super` stood — same class, same origin, whose type
    * every rewrite here reads the receiver's kind from. */
  private[transform] def thisOf(recv: Term): Term = recv match
    case Tree.Super(cls, tpe, so) => Tree.This(cls, tpe, so)
    case other                    => other

  /** Does every `super` in this rewritten term stand where scala allows one — a member
    * selection's qualifier, nowhere else? Java has no such restriction, so a rewrite can put
    * `super` where scala forbids it (`E040`). Asked of the RESULT, not the arm, so a later rewrite
    * is covered by construction. `ENGINE-LIMITS.md` M6. */
  private[transform] def superPlaced(t: Term)(using Program): Boolean =
    var bad = false
    val scan = new Phase:
      def name = "super-placement"
      override def transformTerm(x: Term)(using Program): Term =
        x match
          // a super as a Select's qualifier is the one legal position; every other occurrence is bad.
          case Tree.Select(_: Tree.Super, _, _, _) => x
          case _: Tree.Super                       => bad = true; x
          case _                                   => x
    // the qualifier of a legal Select is still visited on descent, so strip the legal ones first.
    StandardTraversal.mapTerm(scan, stripLegalSuper(t))
    !bad

  /** replace every legal `super.member` with a marker the placement scan does not object to
    * (`Tree.This`, discarded after the scan), so the scan sees only misplaced occurrences. */
  private[transform] def stripLegalSuper(t: Term)(using Program): Term =
    val strip = new Phase:
      def name = "super-strip"
      override def transformTerm(x: Term)(using Program): Term = x match
        case s @ Tree.Select(sup: Tree.Super, m, tp, o) => Tree.Select(Tree.This(SymId.None, sup.tpe, sup.origin), m, tp, o)
        case _                                          => x
    StandardTraversal.mapTerm(strip, t)

  /** did java resolve `Collection.remove(Object)` (by value, returning `boolean`) rather than
    * `List.remove(int)` (by index)? A call whose result type the frontend could not record
    * answers `false` and falls back to scala's index removal. */
  private[transform] def removesByValue(t: Tree.Apply)(using p: Program): Boolean =
    headSym(t.tpe).flatMap(p.symbolOf).exists(_.fullName == "scala.Boolean")

  /** `recv.op(args)` where `op` is tagged an operator → emitted infix (`recv op arg`). */
  private[transform] def infix(recv: Term, op: SymId, args: List[Term], t: Tree.Apply, so: Origin): Term =
    call(recv, op, args, t, so)

  /** `recv.member(args)`. */
  private[transform] def call(recv: Term, member: SymId, args: List[Term], t: Tree.Apply, so: Origin): Term =
    Tree.Apply(Tree.Select(recv, member, TypeRepr.NoType, so), args, member, t.tpe, t.origin)

  /** `JavaCollections.member(args)` — a runtime helper, typed as what the java call it replaces
    * was recorded at (ENGINE-LIMITS K6's first rule: a node describes what it emits). */
  private[transform] def staticCall(member: SymId, args: List[Term], t: Tree.Apply, so: Origin): Term =
    Tree.Apply(Tree.Ident(member, TypeRepr.NoType, so), args, member, t.tpe, t.origin)

  /** `null` — the faithful default for a Java `Map.get` miss (Java map values are always
    * reference types, so `null` always type-checks). Ascribed to `V` by [[dflt]]. */
  private[transform] def nullOf(so: Origin): Term = Tree.Literal(Constant.NullC, TypeRepr.NoType, so)

  /** Ascribe a `getOrElse` default to the map's value type `V` (`default.asInstanceOf[V]`),
    * so inference gives `getOrElse` result type `V` instead of widening to `V | Default`
    * (which breaks e.g. `m.getOrElse(k, 0) + 1` when `V = java.lang.Integer`). Falls back
    * to the bare default when the receiver's `Map[K, V]` isn't fully applied. */
  private[transform] def dflt(default: Term, recv: Term, so: Origin): Term = valueType(recv.tpe) match
    case Some(v) => Tree.Typed(default, TypeTree(v, so), v, so)
    case None    => default

  private[transform] def valueType(t: TypeRepr): Option[TypeRepr] = t match
    case TypeRepr.AppliedType(_, List(_, v)) => Some(v)
    case _                                   => None

  private[transform] def keyType(t: TypeRepr): Option[TypeRepr] = t match
    case TypeRepr.AppliedType(_, List(k, _)) => Some(k)
    case _                                   => None

  /** a one-argument collection's ELEMENT type — [[keyType]]'s counterpart at a `Set`/`Buffer`. */
  private[transform] def elemType(t: TypeRepr): Option[TypeRepr] = t match
    case TypeRepr.AppliedType(_, List(e)) => Some(e)
    case _                                => None

  /** A key argument, with the coercion java's formal required stripped when the scala member's
    * formal is exactly what lies beneath it. Java's `Map.get`/`remove`/`containsKey` widen a
    * type-variable key with `asInstanceOf[Object]` (G14); after this phase retypes the receiver,
    * that widening is all that stands between the argument and `K` (`ENGINE-LIMITS.md` K5.6).
    * Structural, names no type — stripped only when what it wraps already has the wanted type. */
  private[transform] def keyArg(arg: Term, recv: Term): Term = (arg, keyType(recv.tpe)) match
    case (Tree.Typed(inner, _, _, _), Some(k)) if k != TypeRepr.NoType && inner.tpe == k => inner
    case _                                                                               => arg

  /** [[keyArg]]'s rule at `toArray(T[])`: the erasure coercion the frontend synthesised off
    * java's `Object[]` formal, stripped when `JavaCollections.toArray[A]` (which infers `A` from
    * the argument) wants what lies beneath the cast — else it infers `Object` where java inferred
    * the real element type. Structural and names no type (CLAUDE.md §4.56): strip only when the
    * cast's inner already has the call's own result type. */
  private[transform] def arrayArg(arg: Term, t: Tree.Apply): Term = arg match
    case Tree.Typed(inner, _, _, _) if inner.tpe != TypeRepr.NoType && inner.tpe == t.tpe => inner
    case _                                                                                => arg

  private[transform] def methodName(m: SymId)(using p: Program): String = p.symbolOf(m).map(_.name).getOrElse("")

  /** the receiver's (already-retyped, bottom-up) head type, if it is one of our scala
    * collections → its [[Kind]]. */
  private[transform] def kindAt(recv: Term)(using Program): Option[Kind] = headSym(actualOf(recv)._1).flatMap(kindOf.get)

  /** Kind of a call via an inherited JDK collection method (resolved method's owner in `typeMap`).
    * Covers `extends HashMap` etc. where `kindAt` returns `None`. Suppressed for scoped-out receivers. */
  private[transform] def inheritedKind(recv: Term, m: SymId)(using p: Program): Option[Kind] =
    if actualOf(recv)._2 then scala.None
    else p.symbolOf(m).flatMap(s => p.symbolOf(s.owner)).flatMap(o => typeMap.get(o.fullName)).map(_._2)

  /** the type a term really has — [[CollectionsTransform.scopedType]] against this run's
    * [[excluded]] set, with a flag for whether the answer came from a scope hold-back.
    * `excluded.isEmpty` always answers `(t.tpe, false)`, the pre-scope code path by arithmetic. */
  private[transform] def actualOf(t: Term)(using Program): (TypeRepr, Boolean) =
    if literalEmpty then (t.tpe, false)
    else CollectionsTransform.scopedType(t, literal).map(_ -> true).getOrElse(t.tpe -> false)

  // resolving the ambiguous-overload clash this phase's own parent made (§4.5): a scala
  // parent's remove(K) beside a kept java remove(Object) resolves scala's E051 where java did not.

  private[transform] def headSym(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSym(tc)
    case _                           => None

