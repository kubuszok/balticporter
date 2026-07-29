package balticporter.transform

import balticporter.core.{RequiresRuntime, RuntimeArtifact}
import balticporter.tir.*

/** `java.util` collections → `scala.collection.mutable`. A whole-program [[Phase]] — the
  * production transform behind the sge/ssg "java collections → scala" migration.
  *
  *   - `transformType` retypes every collection OCCURRENCE (field, param, return, type
  *     argument, `new`, local) — driven by the symbol table, so it hits every position the
  *     xref knows about, not just the ones a printer happened to annotate.
  *   - `transformApply` rewrites the call shapes that change name/shape between the two
  *     APIs, *kind-aware* (a `get` on a `List` is `apply`, a `get` on a `Map` is
  *     `getOrElse(k, null)`), guarded by the receiver's already-retyped collection type.
  *     Same-named methods (`contains`, `indexOf`, `clear`, …) need no rewrite: the emitter
  *     prints them by name against the retyped receiver, so scalac binds them to the scala
  *     API for free. Iteration is likewise free — a Java `for-each` over a retyped
  *     collection emits `for (x <- coll)`, which any scala `Iterable` supports.
  *
  * New scala symbols are interned into the table in `run`, so the emitter (which reads names
  * from the table) prints `scala.collection.mutable.Buffer[X]`, `xs += x`, `m.update(k, v)`
  * by construction.
  */
final class CollectionsTransform extends Phase, RequiresRuntime:
  def name = "java-collections->scala"

  /** this phase retypes onto `balticporter.runtime` — declared once, so the run derives the port's
    * dependency, its vendored sources and the emitter's external-parent table from it. */
  def runtimeTypes: Set[String] = CollectionsTransform.runtimeTypes

  import CollectionsTransform.{JavaCollectionFqn, JavaIterableFqn, JavaIteratorFqn, Kind}

  /** java fully-qualified name → (scala fully-qualified name, collection kind). */
  private val typeMap: Map[String, (String, Kind)] = Map(
    "java.util.List"          -> ("scala.collection.mutable.Buffer", Kind.Seq),
    "java.util.ArrayList"     -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq),
    // a java `LinkedList` is a List AND a Deque, and the corpus uses it as a QUEUE.
    // `mutable.Queue` extends `ArrayDeque` extends `Buffer`, so every Seq rewrite above still
    // applies and `removeHeadOption` exists — which `ListBuffer` does not have.
    "java.util.LinkedList"    -> ("scala.collection.mutable.Queue", Kind.Seq),
    "java.util.Queue"         -> ("scala.collection.mutable.Queue", Kind.Seq),
    "java.util.Deque"         -> ("scala.collection.mutable.ArrayDeque", Kind.Seq),
    "java.util.ArrayDeque"    -> ("scala.collection.mutable.ArrayDeque", Kind.Seq),
    // The INTERFACE and its ABSTRACT BASE map to the same target, and must: java's
    // `AbstractCollection implements Collection`, so a port that sent the two to unrelated types
    // would break the subtype relation the source depends on. It was split once — `Collection` to
    // `Buffer`, `AbstractCollection` to the shim — and that split is 13 of simple-graphs' 20
    // errors: every method declared to return a `Collection` while returning the library's own
    // `Array extends AbstractCollection`, and every `containsAll(Collection<?>)` override.
    //
    // The shared target is the SHIM rather than `Buffer` because only one of the two directions has
    // a repair. A library that merely USES java collections is served by either; one that DEFINES a
    // collection by extending `AbstractCollection` cannot extend `mutable.Buffer` at all — the
    // scala trait demands `apply`/`update`/`insert` java never had and collides with the `size()`/
    // `iterator()` the java class does declare (CLAUDE.md §4.5). Whereas a scala collection meeting
    // a shim-typed slot IS repairable, by `coerce`, at the slot.
    //
    // MEASURED, and the reason `coerce` covers declarations and not only arguments: mapping the
    // interface here while bridging arguments alone REGRESSED libGDX's test port by 3 — `BezierTest`
    // declares `Collection<Object[]> parameters = new ArrayList<>()`, a slot no call-site seam sees.
    //
    // No implicit bridge is available instead of the seam, and that is measured too: ENGINE-LIMITS
    // records `given Conversion` inert wherever the call is OVERLOADED, which
    // `addVertices(Collection)` / `addVertices(V...)` is.
    "java.util.Collection"         -> (JavaCollectionFqn, Kind.Seq),
    "java.util.AbstractCollection" -> (JavaCollectionFqn, Kind.Seq),
    // likewise NOT `scala.collection.Iterable`: java's `Iterable.iterator()` hands back a
    // REMOVAL-CAPABLE iterator, scala's hands back a `scala.collection.Iterator`. Mapping the
    // two independently would leave the pair inconsistent — `for (x <- xs)` would still work,
    // but `xs.iterator()` would no longer be something you can remove through, which is the
    // only reason libGDX takes an `Iterable` in `Predicate`/`CharArray`/`ModelLoader`.
    "java.lang.Iterable"      -> (JavaIterableFqn, Kind.Seq),
    // NOT `scala.collection.Iterator`: java's `Iterator` is `hasNext/next/REMOVE`, scala's is
    // `hasNext/next`. Mapping it to scala's silently drops a method the source uses (and uses
    // POLYMORPHICALLY, through the interface — so no call-site narrowing recovers it). The
    // target is the shim in [[CollectionsTransform.runtimeSources]], which is scala's `Iterator`
    // PLUS java's `remove`, defaulted to java's own default (throw UnsupportedOperationException).
    "java.util.Iterator"      -> (JavaIteratorFqn, Kind.Seq),
    "java.util.Map"           -> ("scala.collection.mutable.Map", Kind.Map),
    "java.util.HashMap"       -> ("scala.collection.mutable.HashMap", Kind.Map),
    "java.util.LinkedHashMap" -> ("scala.collection.mutable.LinkedHashMap", Kind.Map),
    "java.util.TreeMap"       -> ("scala.collection.mutable.TreeMap", Kind.Map),
    // a scala `Map` IS an `Iterable[(K, V)]`, so java's `Map.Entry` — a key/value pair with no
    // identity of its own — is a `Tuple2`. `getKey`/`getValue` become `_1`/`_2` (below).
    // Spoon's qualified name for a nested type separates with `$` — that is the key that fires;
    // the dotted spelling is an alias for frontends that name nested types with `.`.
    "java.util.Map$Entry"     -> ("scala.Tuple2", Kind.Entry),
    "java.util.Map.Entry"     -> ("scala.Tuple2", Kind.Entry),
    "java.util.Set"           -> ("scala.collection.mutable.Set", Kind.Set),
    "java.util.HashSet"       -> ("scala.collection.mutable.HashSet", Kind.Set),
    "java.util.LinkedHashSet" -> ("scala.collection.mutable.LinkedHashSet", Kind.Set),
    "java.util.TreeSet"       -> ("scala.collection.mutable.TreeSet", Kind.Set),
  )

  /** scala nullary accessors that take NO parens (`def size: Int`) — a Java `size()`
    * emitted as `size()` would be an illegal application. Strip the `Apply`. */
  private val parenless = Set("size", "isEmpty", "iterator", "keySet", "values", "nonEmpty", "hasNext", "next")

  // prepared in `run`, read by the hooks.
  private var remap: Map[SymId, SymId]    = Map.empty
  private var kindOf: Map[SymId, Kind]    = Map.empty // scala collection symbol → kind
  private var opPlusEq, opMinusEq, opPlusPlusEq: SymId = SymId.None
  private var updateSym, insertSym, getOrElseSym, containsSym: SymId = SymId.None
  /** scala `mutable.Map.put`/`remove` — they RETURN the previous value, which java's do too and
    * `update`/`-=` silently discard. */
  private var putSym, removeSym: SymId = SymId.None
  /** java Deque members. `poll`/`peek` return NULL on empty where scala's `head`/`remove(0)`
    * throw, so both go through an `Option` and `orNull` — the difference is invisible in a
    * compile and shows up as a MatchError-shaped failure at runtime (CLAUDE.md §4.4). */
  private var removeHeadOptionSym, headOptionSym, orNullSym, prependSym: SymId = SymId.None
  /** java 8 `Collection.forEach(Consumer)` — scala's is `foreach`, differing only in case, which
    * makes the failure read like a typo rather than a missing mapping. */
  private var foreachSym: SymId = SymId.None
  private var key1Sym, value2Sym, roSetSym: SymId = SymId.None
  /** `JavaIterable` + its `from` factory — see `coerce`. */
  private var javaIterableSym, iterableFromSym: SymId = SymId.None
  /** `JavaCollection` + its `from` factory — the same seam, one type up. */
  private var javaCollectionSym, collectionFromSym: SymId = SymId.None
  /** `JavaIterator.from` — the `iterator` counterpart of `wrapIterableArgs`. */
  private var iteratorFromSym, javaIteratorSym: SymId = SymId.None

  override def run(program: Program): Program =
    val added = collection.mutable.ListBuffer[Symbol]()
    var next  = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    def mint(name: String, full: String): SymId =
      val id = SymId(next); next += 1
      added += Symbol(id, name, full, Flags(), SymId.None, TypeRepr.NoType)
      id
    // one scala symbol per DISTINCT scala type (so two java types mapping to the same
    // scala type — e.g. Deque & ArrayDeque → ArrayDeque — share it and its kind).
    val byScala = collection.mutable.Map[String, SymId]()
    remap = program.symbols.all.flatMap { s =>
      typeMap.get(s.fullName).map { case (sc, _) =>
        s.id -> byScala.getOrElseUpdate(sc, mint(sc.substring(sc.lastIndexOf('.') + 1), sc))
      }
    }.toMap
    kindOf = program.symbols.all.flatMap { s =>
      typeMap.get(s.fullName).map { case (sc, k) => byScala(sc) -> k }
    }.toMap
    opPlusEq     = mint("+=", "scala.<op>#+=")   // rendered infix by the emitter
    opMinusEq    = mint("-=", "scala.<op>#-=")
    opPlusPlusEq = mint("++=", "scala.<op>#++=")
    updateSym    = mint("update", "update")
    insertSym    = mint("insert", "insert")
    getOrElseSym = mint("getOrElse", "getOrElse")
    containsSym  = mint("contains", "contains")
    key1Sym      = mint("_1", "_1") // Map.Entry#getKey   on a Tuple2
    value2Sym    = mint("_2", "_2") // Map.Entry#getValue on a Tuple2
    roSetSym     = mint("Set", "scala.collection.Set") // see `transformValDef`
    javaIterableSym = byScala.getOrElse(JavaIterableFqn, SymId.None)
    iterableFromSym = mint("from", JavaIterableFqn + ".from")
    javaCollectionSym = byScala.getOrElse(JavaCollectionFqn, SymId.None)
    collectionFromSym = mint("from", JavaCollectionFqn + ".from")
    iteratorFromSym = mint("from", JavaIteratorFqn + ".from")
    javaIteratorSym = byScala.getOrElse(JavaIteratorFqn, SymId.None)
    foreachSym          = mint("foreach", "foreach")
    removeHeadOptionSym = mint("removeHeadOption", "removeHeadOption")
    headOptionSym       = mint("headOption", "headOption")
    orNullSym           = mint("orNull", "orNull")
    prependSym          = mint("prepend", "prepend")
    putSym       = mint("put", "put")     // scala `mutable.Map.put`: returns the PREVIOUS value
    removeSym    = mint("remove", "remove") // scala `mutable.Map.remove`: returns the REMOVED value

    val symbols = SymbolTable(program.symbols.all ++ added)
    given Program = new Program(program.units, symbols, program.xref)
    val units    = program.units.map(u => StandardTraversal.mapClassDef(this, u))
    val symbols2 = StandardTraversal.mapSymbols(this, symbols) // retype signatures too
    new Program(units, symbols2, program.xref)

  override def transformType(t: TypeRepr)(using Program): TypeRepr = t match
    case TypeRepr.TypeRef(prefix, s) if remap.contains(s) => TypeRepr.TypeRef(prefix, remap(s))
    case other                                            => other

  /** `java.util.Set` has TWO faithful scala counterparts, and which one it is depends on where
    * the set came from — a distinction java's type system does not draw and scala's does.
    *
    *   - a set you OWN (`new HashSet<>()`, a field, a parameter) is `mutable.Set`;
    *   - `map.keySet()` is a live, read-only VIEW of the map's keys. Scala models it as
    *     `scala.collection.Set` — same view, same write-through on the map, but not typed as
    *     something you may add to (java lets you `remove` through it but not `add`, so scala's
    *     type is the closer of the two anyway).
    *
    * So a declaration INITIALISED from `keySet` gets the view type. The alternative —
    * `.to(mutable.Set)` to satisfy the declared type — would COPY, and silently turn a view of
    * the map into a detached snapshot. Provenance decides the type; the value is never touched.
    */
  override def transformValDef(t: Tree.ValDef)(using Program): Tree.ValDef = t.rhs match
    case Some(Tree.Select(recv, sym, _, _))
        if methodName(sym) == "keySet" && kindAt(recv).contains(Kind.Map) && headSym(t.tpt.tpe).exists(kindOf.get(_).contains(Kind.Set)) =>
      t.copy(tpt = TypeTree(withHead(t.tpt.tpe, roSetSym), t.tpt.origin))
    // a DECLARED slot is an expected type exactly as a formal parameter is — see `coerce`.
    // `Collection<Object[]> parameters = new ArrayList<>()` is the shape, and it is the one that
    // regressed libGDX's test port when only arguments were bridged.
    case Some(rhs) => t.copy(rhs = Some(coerce(t.tpt.tpe, rhs)))
    case _ => t

  /** an assignment's left-hand side declares the expected type just as a `val`'s `tpt` does. */
  override def transformTerm(t: Term)(using Program): Term = t match
    case a: Tree.Assign => a.copy(rhs = coerce(a.lhs.tpe, a.rhs))
    case other          => other

  /** replace the head (type-constructor) symbol of a `TypeRef` / `AppliedType`, keeping args. */
  private def withHead(t: TypeRepr, s: SymId): TypeRepr = t match
    case TypeRepr.TypeRef(prefix, _)    => TypeRepr.TypeRef(prefix, s)
    case TypeRepr.AppliedType(tc, args) => TypeRepr.AppliedType(withHead(tc, s), args)
    case other                          => other

  override def transformApply(t: Tree.Apply)(using Program): Term =
    val t2 = wrapIterableArgs(t)
    t2.fun match
      case Tree.Select(recv, m, _, so) => kindAt(recv) match
        case Some(k) => rewrite(k, recv, m, so, t2).getOrElse(t2)
        case None    => t2
      case _ => t2

  /** Bridge a scala collection into a shim-typed parameter, AT THE CALL SITE.
    *
    * `java.util.List` becomes a `Buffer` and `java.lang.Iterable` becomes [[JavaIterable]]; each
    * mapping is right on its own, and together they leave the port unable to pass its own
    * collections to its own methods — `CharArray.appendAll(list)`, which java accepted because
    * there `List` IS an `Iterable`.
    *
    * Both obvious repairs are measured dead ends (LIBGDX-PORT-STATUS.md): a `given Conversion`
    * never fires, because scala does not look for one when no OVERLOAD alternative matches; and
    * widening the parameter to `scala.collection.Iterable` breaks the bodies that iterate-and-
    * REMOVE through it. Wrapping the ARGUMENT has neither problem — the type is exact before
    * overload resolution runs, and the parameter keeps the capability it declares. */
  private def wrapIterableArgs(t: Tree.Apply)(using p: Program): Tree.Apply =
    if javaIterableSym == SymId.None then t
    else
      val formals = p.symbolOf(t.method).map(_.info).collect {
        case TypeRepr.MethodType(ps, _, _)                       => ps.map(_._2)
        case TypeRepr.PolyType(_, TypeRepr.MethodType(ps, _, _)) => ps.map(_._2)
      }.getOrElse(Nil)
      if formals.sizeIs != t.args.size then t
      else
        val as = t.args.zip(formals).map((a, f) => coerce(f, a))
        if as == t.args then t else t.copy(args = as)

  /** Bridge a scala collection into a SHIM-TYPED SLOT — an argument, a declared `val`, an
    * assignment target. Nothing else in the phase decides this; every position routes here.
    *
    * The boundary is self-inflicted and unavoidable. `java.util.Collection` and
    * `java.util.AbstractCollection` must map into the SAME family, because java's abstract base
    * IMPLEMENTS the interface and a port that broke that subtype relation would leave a library
    * that defines its own collection unable to return it (`Node.getConnections()` returning the
    * `Array` that `extends AbstractCollection`) — 13 of simple-graphs' 20 errors, measured. And the
    * family cannot be scala's, because §4.5: a class extending `mutable.Buffer` must supply
    * `apply`/`update`/`insert`/`patchInPlace` java never had, and its own `size`/`iterator`/
    * `remove` collide with the trait's.
    *
    * So both map to the shim, and the cost lands on the OTHER side: a scala collection reaching a
    * shim-typed slot. That is this function, and the reason it is a value-level wrap rather than a
    * retyping is that only a wrap leaves the declared capability intact.
    *
    * Positions matter as much as the rule. Bridging arguments ALONE was tried, and left
    * `Collection<Object[]> parameters = new ArrayList<>()` (libGDX's `BezierTest`) broken — a
    * declared slot is an expected type exactly as a formal is, and a seam that knows only about
    * calls reads as "the mapping is wrong" when it is the seam that is incomplete.
    *
    * Conservative by construction: it wraps only when the source is a scala collection the phase
    * itself introduced (`kindOf`), so a program class that genuinely EXTENDS the shim is left
    * alone, and an unrecognised type produces an honest compile error rather than a wrong wrap. */
  private def coerce(expected: TypeRepr, actual: Term): Term =
    // the symbol table is retyped AFTER the trees (see `run`), so a formal read here is still the
    // ORIGINAL java symbol — `java.lang.Iterable`, not the shim. Compare through `remap`, which
    // makes this correct on either side of that pass.
    def scalaSym(x: SymId): SymId = remap.getOrElse(x, x)
    val wants = headSym(expected).map(scalaSym)
    val got   = headSym(actual.tpe).map(scalaSym)
    // a shim source needs no bridge: `JavaCollection` already IS a `JavaIterable`, and neither can
    // be rebuilt from the other. `JavaIterator` is Kind.Seq and is NOT a collection — excluded.
    val fromScala = got.exists(g => kindOf.get(g).contains(Kind.Seq) && !shimSyms.contains(g))
    val factory =
      if !fromScala then SymId.None
      else if wants.contains(javaCollectionSym) then collectionFromSym
      else if wants.contains(javaIterableSym) then iterableFromSym
      else SymId.None
    if factory == SymId.None then actual
    else Tree.Apply(Tree.Ident(factory, TypeRepr.NoType, actual.origin), List(actual),
                    factory, expected, actual.origin)

  /** the runtime shims, as scala symbols — a source already typed as one is never re-wrapped. */
  private def shimSyms: Set[SymId] = Set(javaIterableSym, javaIteratorSym, javaCollectionSym)

  /** kind-aware call rewrite; `None` = leave the call as-is (same-named method binds to
    * the scala API against the retyped receiver at compile time). */
  private def rewrite(k: Kind, recv: Term, m: SymId, so: Origin, t: Tree.Apply)(using Program): Option[Term] =
    val name = methodName(m)
    /** is the receiver one of the runtime SHIMS rather than a scala collection? They carry java's
      * arity and java's OWN member names, so the scala-shaped rewrites below must leave them alone.
      *
      * This is a BLANKET refusal (the `case _ if onShim` arm) and not a guard per rewrite, because
      * the per-rewrite form has now failed twice. `parenless` and `iterator` were guarded when the
      * shims were `JavaIterable`/`JavaIterator`; adding `JavaCollection` — which has `add`,
      * `addAll`, `remove`, `size()` — meant `add` still became `+=` and `addAll` still became `++=`
      * against a type that has neither. The rule is not "these few rewrites are unsafe on a shim";
      * it is "every rewrite here reshapes a call for SCALA's collection API, and a shim is
      * deliberately not one". Exceptions are listed ABOVE the guard, so a new rewrite is safe by
      * default and an unsafe one cannot be added by omission. */
    val onShim = headSym(recv.tpe).exists(shimSyms.contains)
    (name, t.args, k) match
      // The one exception, and the reason it is one: java 8's `forEach(Consumer)` has no
      // counterpart on the shim itself — `JavaIterable` supplies `foreach` as an EXTENSION, which
      // is the whole point of the family (§4.5: an extension adds a view and cannot conflict).
      // Left alone, this is a call to a member that does not exist.
      case ("forEach", List(f), _) => Some(call(recv, foreachSym, List(f), t, so))
      case _ if onShim             => None
      // `m.entrySet()` is the VIEW of the map as its (key, value) pairs, and a scala `Map[K, V]`
      // already IS an `Iterable[(K, V)]` — so the view is the map itself. `m.toSet` would be the
      // unfaithful choice: java's `entrySet` is live, and a snapshot silently changes what a
      // concurrent `put` is observed to do. The one thing `Tuple2` does NOT carry over is
      // `Entry.setValue` (write-through to the map); that is deliberate — a `setValue` call now
      // fails to COMPILE rather than being turned into a write to a detached copy.
      // `list.iterator()` on a collection the port mapped to scala yields a
      // `scala.collection.Iterator`, but every DECLARATION the port derived from `java.util.Iterator`
      // asks for the removal-capable shim — the same self-inflicted boundary `wrapIterableArgs`
      // bridges, met here in a `val` initialiser instead of an argument. Invisible in the TIR (both
      // sides read as the shim; only the emitted scala disagrees), so it is decided on PROVENANCE:
      // a scala collection's iterator is a scala one. Widening is free — the shim IS a
      // `scala.collection.Iterator` — and `remove()` correctly throws, since this iterator has no
      // removal to offer.
      // Not on the SHIMS themselves — their `iterator` already yields a `JavaIterator`; wrapping it
      // would be a no-op that also loses the parenless form below, emitting `iterable.iterator()`
      // against a scala-shaped `def iterator` (measured 2 -> 10). Covered by the blanket guard.
      case ("iterator", Nil, _) if iteratorFromSym != SymId.None =>
        val sel = Tree.Select(recv, m, t.tpe, t.origin) // parenless, as the generic case below
        Some(Tree.Apply(Tree.Ident(iteratorFromSym, TypeRepr.NoType, so), List(sel), iteratorFromSym, t.tpe, so))
      case ("entrySet", Nil, Kind.Map)          => Some(recv)
      case ("getKey", Nil, Kind.Entry)          => Some(Tree.Select(recv, key1Sym, t.tpe, t.origin))
      case ("getValue", Nil, Kind.Entry)        => Some(Tree.Select(recv, value2Sym, t.tpe, t.origin))
      // …but never on a SHIM receiver (the blanket guard above). `parenless` exists because a scala
      // collection's `size`/`iterator` take no parens; the shims deliberately carry JAVA's arity
      // (`iterator()`, `hasNext()`, `next()`), because a class that is both java `Iterable` and java
      // `Iterator` cannot be modelled on scala's collection traits at all. Stripping `()` there
      // emits `it.hasNext` against `def hasNext()` — 24 measured errors.
      case (n, Nil, _) if parenless(n)          => Some(Tree.Select(recv, m, t.tpe, t.origin)) // drop `()`
      case ("get", List(i), Kind.Seq)           => Some(Tree.Apply(recv, List(i), m, t.tpe, t.origin)) // xs(i)
      case ("get", List(key), Kind.Map)         => Some(call(recv, getOrElseSym, List(key, dflt(nullOf(so), recv, so)), t, so))
      case ("getOrDefault", List(key, d), _)    => Some(call(recv, getOrElseSym, List(key, dflt(d, recv, so)), t, so))
      case ("set", List(i, x), Kind.Seq)        => Some(call(recv, updateSym, List(i, x), t, so)) // xs(i) = x
      // Java's `Map.put` RETURNS THE PREVIOUS VALUE; scala's `update` returns `Unit`. Mapping to
      // `update` discarded it at every site — `if (map.put(k, v) != null)` became a comparison
      // against `Unit`. Scala's own `put` keeps it, as an `Option`, so `getOrElse(null)` restores
      // java's contract exactly. The default is ascribed to `V`, as `get`'s is.
      case ("put", List(key, v), Kind.Map)      =>
        Some(call(call(recv, putSym, List(key, v), t, so), getOrElseSym, List(dflt(nullOf(so), recv, so)), t, so))
      // likewise `Map.remove`, which returns the value that was there.
      case ("remove", List(key), Kind.Map)      =>
        Some(call(call(recv, removeSym, List(key), t, so), getOrElseSym, List(dflt(nullOf(so), recv, so)), t, so))
      case ("add", List(i, x), Kind.Seq)        => Some(call(recv, insertSym, List(i, x), t, so)) // insert at index
      case ("add", List(x), _)                  => Some(infix(recv, opPlusEq, List(x), t, so))    // xs += x
      // ---- java Deque, as `LinkedList`/`ArrayDeque` are routinely used ----
      // `addLast`/`offer` append; `addFirst` prepends. Same shape as `add`, different end.
      case ("addLast" | "offer" | "offerLast", List(x), Kind.Seq) => Some(infix(recv, opPlusEq, List(x), t, so))
      case ("addFirst" | "offerFirst", List(x), Kind.Seq)         => Some(call(recv, prependSym, List(x), t, so))
      // `poll`/`peek` return NULL on an empty deque. `remove(0)`/`head` THROW, so a direct mapping
      // would turn "the queue was empty" into an exception — a behavioural change with no compile
      // error. `removeHeadOption().orNull` / `headOption.orNull` reproduce java exactly.
      // `orNull` is PARAMETERLESS and takes an implicit `Null <:< A`; emitting `orNull()` makes
      // scala look for an explicit argument list and fail. It is a `Select`, not an `Apply`.
      case ("poll" | "pollFirst", Nil, Kind.Seq) =>
        Some(Tree.Select(call(recv, removeHeadOptionSym, Nil, t, so), orNullSym, t.tpe, so))
      case ("peek" | "peekFirst" | "element", Nil, Kind.Seq) =>
        Some(Tree.Select(Tree.Select(recv, headOptionSym, TypeRepr.NoType, so), orNullSym, t.tpe, so))
      case ("addAll" | "putAll", List(c), _)    => Some(infix(recv, opPlusPlusEq, List(c), t, so))// xs ++= c
      case ("remove", List(x), Kind.Set)        => Some(infix(recv, opMinusEq, List(x), t, so)) // xs -= x
      case ("containsKey", List(key), Kind.Map) => Some(call(recv, containsSym, List(key), t, so))
      case _                                    => None

  /** `recv.op(args)` where `op` is tagged an operator → emitted infix (`recv op arg`). */
  private def infix(recv: Term, op: SymId, args: List[Term], t: Tree.Apply, so: Origin): Term =
    call(recv, op, args, t, so)

  /** `recv.member(args)`. */
  private def call(recv: Term, member: SymId, args: List[Term], t: Tree.Apply, so: Origin): Term =
    Tree.Apply(Tree.Select(recv, member, TypeRepr.NoType, so), args, member, t.tpe, t.origin)

  /** `null` — the faithful default for a Java `Map.get` miss (Java map values are always
    * reference types, so `null` always type-checks). Ascribed to `V` by [[dflt]]. */
  private def nullOf(so: Origin): Term = Tree.Literal(Constant.NullC, TypeRepr.NoType, so)

  /** Ascribe a `getOrElse` default to the map's value type `V` (`default.asInstanceOf[V]`),
    * so inference gives `getOrElse` result type `V` instead of widening to `V | Default`
    * (which breaks e.g. `m.getOrElse(k, 0) + 1` when `V = java.lang.Integer`). Falls back
    * to the bare default when the receiver's `Map[K, V]` isn't fully applied. */
  private def dflt(default: Term, recv: Term, so: Origin): Term = valueType(recv.tpe) match
    case Some(v) => Tree.Typed(default, TypeTree(v, so), v, so)
    case None    => default

  private def valueType(t: TypeRepr): Option[TypeRepr] = t match
    case TypeRepr.AppliedType(_, List(_, v)) => Some(v)
    case _                                   => None

  private def methodName(m: SymId)(using p: Program): String = p.symbolOf(m).map(_.name).getOrElse("")

  /** the receiver's (already-retyped, bottom-up) head type, if it is one of our scala
    * collections → its [[Kind]]. */
  private def kindAt(recv: Term): Option[Kind] = headSym(recv.tpe).flatMap(kindOf.get)

  private def headSym(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSym(tc)
    case _                           => None

object CollectionsTransform:
  /** the shape of a collection, which decides the call rewrite (a `Seq` `get` is `apply`,
    * a `Map` `get` is `getOrElse`). */
  enum Kind:
    case Seq, Map, Set
    /** a `java.util.Map.Entry`, mapped to `Tuple2` — `getKey`/`getValue` are `_1`/`_2`. */
    case Entry

  val JavaIteratorFqn = s"${RuntimeArtifact.Package}.JavaIterator"
  val JavaIterableFqn = s"${RuntimeArtifact.Package}.JavaIterable"
  val JavaCollectionFqn = s"${RuntimeArtifact.Package}.JavaCollection"

  /** Support types the retyping REQUIRES. They live in the PUBLISHED `balticporter-runtime`
    * module (`runtime/src/main/scala`), not here — see [[RuntimeArtifact]] for why a per-port copy
    * at a shared FQN is a correctness defect and not a packaging preference.
    *
    * Why they exist at all, since the mapping is not obvious: `java.util.Iterator` declares
    * `hasNext`, `next` AND `remove`, while `scala.collection.Iterator` declares only the first
    * two. Mapping java's onto scala's therefore DELETES a method — and libGDX calls it
    * (`ModelLoader`, `ParticleControllerInfluencer`, `ArraySelection`, `Predicate`), always
    * through the interface, so no amount of call-site type narrowing brings it back. Nor can the
    * *loop* be rewritten to a scala idiom (`filterInPlace`): the receiver is not a scala collection
    * at all, it is an arbitrary user `Iterator` implementation, and in `Predicate` the removal is
    * a straight delegation from one iterator to another with no loop in sight.
    *
    * So the mapping's target is java's interface expressed in scala: scala's `Iterator` plus
    * `remove`, whose default body is java's own documented default for the method. An
    * implementation that overrides it (every libGDX iterator does) keeps its behaviour; one that
    * does not gets exactly what java gives it. Nothing is approximated.
    *
    * `JavaIterable` follows from it and is not optional: java's `Iterable.iterator()` is DECLARED
    * to return a `java.util.Iterator`, so retyping `Iterator` without retyping `Iterable` splits
    * the pair — `iterable.iterator` would yield the removal-less scala iterator, and every place
    * libGDX takes an `Iterable` only to iterate-and-remove through it
    * (`Predicate.PredicateIterator`, `CharArray.appendWithSeparators`, `ModelLoader.loadSync`)
    * would stop type-checking. Two types, one decision.
    */
  val runtimeTypes: Set[String] = Set(JavaIteratorFqn, JavaIterableFqn, JavaCollectionFqn)

  /** What [[runtimeSources]] BRINGS, for a consumer that must reason about the injected
    * supertypes it cannot parse. `JavaIterator.remove` is concrete (java's own documented default),
    * so a class extending both it and a superclass that also defines `remove` is a scala
    * linearisation conflict — `TirEmitter` needs to be told.
    *
    * PREFER `RuntimePlan.of(phases).concreteMembers`, which derives this from the phases that ran
    * instead of asking the caller to remember it. This name is kept because existing migration
    * programs pass it directly. */
  lazy val runtimeConcreteMembers: Map[String, Set[(String, List[Int])]] =
    RuntimeArtifact.concreteMembers.filter((fqn, _) => runtimeTypes.contains(fqn))

  /** The support sources, as text, for a port that vendors them instead of depending on the
    * artifact ([[balticporter.core.RuntimeMode.Vendored]]).
    *
    * NOT the source of truth: this is the build-time copy of `runtime/src/main/scala`, read back
    * off the classpath. PREFER `RuntimePlan.of(phases, mode).writeSources(dir)`, which will not
    * write them at all when the port depends on the artifact — which is the default and the
    * correct choice for any port that is one module of several. */
  lazy val runtimeSources: Map[String, String] =
    runtimeTypes.map(fqn => fqn -> RuntimeArtifact.sourceOf(fqn)).toMap
