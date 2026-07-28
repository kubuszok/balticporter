package balticporter.transform

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
final class CollectionsTransform extends Phase:
  def name = "java-collections->scala"

  import CollectionsTransform.{JavaIterableFqn, JavaIteratorFqn, Kind}

  /** java fully-qualified name → (scala fully-qualified name, collection kind). */
  private val typeMap: Map[String, (String, Kind)] = Map(
    "java.util.List"          -> ("scala.collection.mutable.Buffer", Kind.Seq),
    "java.util.ArrayList"     -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq),
    "java.util.LinkedList"    -> ("scala.collection.mutable.ListBuffer", Kind.Seq),
    "java.util.Queue"         -> ("scala.collection.mutable.Queue", Kind.Seq),
    "java.util.Deque"         -> ("scala.collection.mutable.ArrayDeque", Kind.Seq),
    "java.util.ArrayDeque"    -> ("scala.collection.mutable.ArrayDeque", Kind.Seq),
    // NOT `scala.collection.mutable.Iterable`: java's `Collection` is add/remove/contains/size,
    // and mutable.Iterable offers none of those — `parameters.add(x)` becomes `parameters += x`,
    // which does not exist there. `Buffer` is the mutable, addable, iterable target; it adds an
    // ordering guarantee java's `Collection` does not make, which is a widening of contract and
    // cannot break a caller.
    "java.util.Collection"    -> ("scala.collection.mutable.Buffer", Kind.Seq),
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
  private var key1Sym, value2Sym, roSetSym: SymId = SymId.None
  /** `JavaIterable` + its `from` factory — see `wrapIterableArgs`. */
  private var javaIterableSym, iterableFromSym: SymId = SymId.None
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
    iteratorFromSym = mint("from", JavaIteratorFqn + ".from")
    javaIteratorSym = byScala.getOrElse(JavaIteratorFqn, SymId.None)
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
    case _ => t

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
    * overload resolution runs, and the parameter keeps the capability it declares.
    *
    * The wrapper's `remove()` inherits [[JavaIterator]]'s `UnsupportedOperationException`, which is
    * the truth: a scala collection's iterator cannot remove through it. Nothing is lost silently. */
  private def wrapIterableArgs(t: Tree.Apply)(using p: Program): Tree.Apply =
    if javaIterableSym == SymId.None then t
    else
      val formals = p.symbolOf(t.method).map(_.info).collect {
        case TypeRepr.MethodType(ps, _, _)                       => ps.map(_._2)
        case TypeRepr.PolyType(_, TypeRepr.MethodType(ps, _, _)) => ps.map(_._2)
      }.getOrElse(Nil)
      if formals.sizeIs != t.args.size then t
      else
        // the symbol table is retyped AFTER the trees (see `run`), so a formal read here is still
        // the ORIGINAL java symbol — `java.lang.Iterable`, not the shim. Compare through `remap`,
        // which makes this correct on either side of that pass.
        def scalaSym(x: SymId): SymId = remap.getOrElse(x, x)
        val as = t.args.zip(formals).map { (a, f) =>
          val wants = headSym(f).map(scalaSym).contains(javaIterableSym)
          val argS  = headSym(a.tpe).map(scalaSym)
          if wants && argS.exists(kindOf.contains) && !argS.contains(javaIterableSym)
          then Tree.Apply(Tree.Ident(iterableFromSym, TypeRepr.NoType, a.origin), List(a),
                          iterableFromSym, f, a.origin)
          else a
        }
        if as == t.args then t else t.copy(args = as)

  /** kind-aware call rewrite; `None` = leave the call as-is (same-named method binds to
    * the scala API against the retyped receiver at compile time). */
  private def rewrite(k: Kind, recv: Term, m: SymId, so: Origin, t: Tree.Apply)(using Program): Option[Term] =
    val name = methodName(m)
    (name, t.args, k) match
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
      // against a scala-shaped `def iterator` (measured 2 -> 10).
      case ("iterator", Nil, _)
          if iteratorFromSym != SymId.None &&
             !headSym(recv.tpe).exists(x => x == javaIterableSym || x == javaIteratorSym) =>
        val sel = Tree.Select(recv, m, t.tpe, t.origin) // parenless, as the generic case below
        Some(Tree.Apply(Tree.Ident(iteratorFromSym, TypeRepr.NoType, so), List(sel), iteratorFromSym, t.tpe, so))
      case ("entrySet", Nil, Kind.Map)          => Some(recv)
      case ("getKey", Nil, Kind.Entry)          => Some(Tree.Select(recv, key1Sym, t.tpe, t.origin))
      case ("getValue", Nil, Kind.Entry)        => Some(Tree.Select(recv, value2Sym, t.tpe, t.origin))
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

  val JavaIteratorFqn = "balticporter.runtime.JavaIterator"
  val JavaIterableFqn = "balticporter.runtime.JavaIterable"

  /** Support types the retyping REQUIRES, as ready Scala. The emitted program is compiled
    * standalone, so a target type that is not in the scala stdlib has to be shipped WITH it;
    * a consumer of this phase writes these out next to the emitted units (keyed by FQN).
    *
    * Only one so far, and it exists because the obvious mapping is wrong: `java.util.Iterator`
    * declares `hasNext`, `next` AND `remove`, while `scala.collection.Iterator` declares only
    * the first two. Mapping java's onto scala's therefore DELETES a method — and libGDX calls
    * it (`ModelLoader`, `ParticleControllerInfluencer`, `ArraySelection`, `Predicate`), always
    * through the interface, so no amount of call-site type narrowing brings it back. Nor can
    * the *loop* be rewritten to a scala idiom (`filterInPlace`): the receiver is not a scala
    * collection at all, it is an arbitrary user `Iterator` implementation, and in `Predicate`
    * the removal is a straight delegation from one iterator to another with no loop in sight.
    *
    * So the mapping's target is java's interface expressed in scala: scala's `Iterator` plus
    * `remove`, whose default body is java's own documented default for the method. An
    * implementation that overrides it (every libGDX iterator does) keeps its behaviour; one
    * that does not gets exactly what java gives it. Nothing is approximated.
    *
    * `JavaIterable` follows from it and is not optional: java's `Iterable.iterator()` is
    * DECLARED to return a `java.util.Iterator`, so retyping `Iterator` without retyping
    * `Iterable` splits the pair — `iterable.iterator` would yield the removal-less scala
    * iterator, and every place libGDX takes an `Iterable` only to iterate-and-remove through it
    * (`Predicate.PredicateIterator`, `CharArray.appendWithSeparators`, `ModelLoader.loadSync`)
    * would stop type-checking. Two types, one decision.
    */
  val runtimeSources: Map[String, String] = Map(
    JavaIterableFqn ->
      """package balticporter.runtime
        |
        |/** `java.lang.Iterable`, as Scala — `scala.collection.Iterable` whose `iterator` is the
        |  * removal-capable [[JavaIterator]] that java's `Iterable.iterator()` is declared to
        |  * return. Iteration is unaffected (it IS a `scala.collection.Iterable`, so `for (x <- xs)`,
        |  * `map`, `foreach` all work); what it adds back is the guarantee java gives, that the
        |  * iterator you get can remove from the collection you got it from.
        |  */
        |trait JavaIterable[A] extends scala.collection.Iterable[A]:
        |  def iterator: JavaIterator[A]
        |
        |object JavaIterable:
        |  /** Adapt a plain scala collection to the java-shaped one. Inserted by the engine at call
        |    * sites where a shim-typed parameter meets a collection the port ITSELF mapped to scala
        |    * (`CharArray.appendAll(list)`). `remove()` stays at [[JavaIterator]]'s default —
        |    * `UnsupportedOperationException` — because a scala iterator genuinely cannot remove,
        |    * which is what java reports for a non-removable iterator too. */
        |  def from[A](xs: scala.collection.Iterable[A]): JavaIterable[A] = new JavaIterable[A]:
        |    def iterator: JavaIterator[A] = new JavaIterator[A]:
        |      private val underlying = xs.iterator
        |      def hasNext: Boolean = underlying.hasNext
        |      def next(): A = underlying.next()
        |""".stripMargin,
    JavaIteratorFqn ->
      """package balticporter.runtime
        |
        |/** `java.util.Iterator`, as Scala — what `scala.collection.Iterator` is missing.
        |  *
        |  * Java's `Iterator` has a third method, `remove()`, which removes from the underlying
        |  * collection the element last returned by `next()`. `scala.collection.Iterator` has no
        |  * such operation and no way to express one, so a port that maps `java.util.Iterator` to
        |  * it drops the method — quietly, until a call site fails to compile, and dangerously if
        |  * the call site is instead "fixed" by dropping the removal.
        |  *
        |  * Ported code implementing a Java `Iterator` extends THIS instead. Removal support is
        |  * therefore preserved exactly: an implementation that defines `remove()` keeps its own
        |  * behaviour, and one that does not inherits the default the JDK itself specifies for
        |  * `Iterator.remove` — throw `UnsupportedOperationException`.
        |  *
        |  * Portable: no JVM-only API, nothing reflective.
        |  */
        |trait JavaIterator[A] extends scala.collection.Iterator[A]:
        |  /** `java.util.Iterator.remove` — the JDK's own default implementation. */
        |  def remove(): Unit = throw new UnsupportedOperationException("remove")
        |
        |object JavaIterator:
        |  /** Adapt a `scala.collection.Iterator` to the java-shaped one. `remove()` keeps the
        |    * default above, which is the truth: there is nothing to remove through. */
        |  def from[A](it: scala.collection.Iterator[A]): JavaIterator[A] = it match
        |    case ji: JavaIterator[A @unchecked] => ji
        |    case _ => new JavaIterator[A]:
        |      def hasNext: Boolean = it.hasNext
        |      def next(): A = it.next()
        |""".stripMargin,
  )
