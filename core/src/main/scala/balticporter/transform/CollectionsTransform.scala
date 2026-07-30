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

  import CollectionsTransform.{JavaCollectionFqn, JavaCollectionsFqn, JavaIterableFqn, JavaIteratorFqn, Kind}

  /** java fully-qualified name → (scala fully-qualified name, collection kind). */
  private val typeMap: Map[String, (String, Kind)] = Map(
    "java.util.List"          -> ("scala.collection.mutable.Buffer", Kind.Seq),
    "java.util.ArrayList"     -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq),
    // a java `LinkedList` is a List AND a Deque, and the corpus uses it as a QUEUE.
    // `mutable.Queue` extends `ArrayDeque` extends `Buffer`, so every Seq rewrite above still
    // applies and `removeHeadOption` exists — which `ListBuffer` does not have.
    "java.util.LinkedList"    -> ("scala.collection.mutable.Queue", Kind.Seq),
    // `java.util.Queue` maps to `ArrayDeque` and NOT to `mutable.Queue`, because the two libraries
    // order these types OPPOSITELY: java has `ArrayDeque <: Deque <: Queue`, scala has
    // `Queue <: ArrayDeque`. Sending the interface to `mutable.Queue` and the class to
    // `mutable.ArrayDeque` therefore INVERTS the relation, and ordinary java — assigning an
    // `ArrayDeque` to a `Queue`-typed field — stops type-checking. Measured in simple-graphs'
    // `MinimumWeightSpanningTree`, whose `Queue<Connection<V>>` field is filled from an
    // `ArrayDeque::new` collector: `Found: ArrayDeque[…] / Required: Queue[…]`.
    //
    // This is the third instance of one rule, and the rule is the transferable part: A MAPPING MUST
    // PRESERVE THE SOURCE LIBRARY'S OWN SUBTYPE RELATIONS. `Collection`/`AbstractCollection` (below)
    // was the same failure from the other direction. Nothing is lost by mapping to the base:
    // `mutable.ArrayDeque` has the `removeHeadOption`/`head` this phase's `poll`/`peek` rewrites
    // need, and `LinkedList -> mutable.Queue` still conforms because scala's `Queue` IS an
    // `ArrayDeque`.
    "java.util.Queue"         -> ("scala.collection.mutable.ArrayDeque", Kind.Seq),
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

  /** the java types this phase retypes — its POLICY, read back so a CHECK can ask what the phase
    * did rather than guessing from a name (CLAUDE.md §4.56). Both checks below take it as a
    * parameter and hold no mapping of their own, which is what keeps the closure property §1(a)
    * while the mapping stays §1(b). */
  def mappedTypes: Set[String] = typeMap.keySet

  /** …and what each became, so a finding can say `java.util.List -> mutable.Buffer` instead of
    * naming only the half a reader already has. `"?"` for a type the phase does not map. */
  def targetOf(fqn: String): String = typeMap.get(fqn).map(_._1).getOrElse("?")

  /** every scala/shim type this phase can PUT into a program — the other side of the boundary
    * [[CollectionBoundaryCheck]] measures. Derived from the same map, so a new mapping widens
    * both checks with no second list to update. */
  def retypedTargets: Set[String] = typeMap.values.map(_._1).toSet

  /** [[CollectionClosureCheck]] over this phase's own mapping — the phase reports on its policy,
    * so the check cannot be run against a mapping that is not the one that ran. */
  def closure(program: Program): List[CollectionClosureCheck.Finding] =
    closure(program, program.units)

  /** …held to the units the run EMITS. A dependent port's program contains its base's units, and a
    * finding attributed to one of those belongs to the base (ENGINE-LIMITS D2). */
  def closure(program: Program, units: List[Tree.ClassDef]): List[CollectionClosureCheck.Finding] =
    CollectionClosureCheck.check(program, units, mappedTypes, targetOf)

  /** [[CollectionBoundaryCheck]] over this phase's own mapping. Run on the program AFTER the
    * phase: it counts the residue the retyping CREATED and did not close. */
  def boundary(program: Program): List[CollectionBoundaryCheck.Finding] =
    boundary(program, program.units)

  /** …held to the units the run EMITS, for the reason [[closure]] gives. */
  def boundary(program: Program, units: List[Tree.ClassDef]): List[CollectionBoundaryCheck.Finding] =
    CollectionBoundaryCheck.check(program, units, mappedTypes, retypedTargets)

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
  /** `JavaCollection` + its `from` factory — the same seam, one type up. `unmodifiableFromSym` is
    * the read-only sibling (a `Map.values()` view); `unmodifiableSym` is
    * `Collections.unmodifiableCollection`. */
  private var javaCollectionSym, collectionFromSym: SymId = SymId.None
  /** the `Kind.Set` source's factory into a `JavaCollection` slot — a DISTINCT NAME rather than an
    * overload of `from`, for the reason `JavaCollection.unmodifiableFrom` gives: an overload
    * resolves on the static type, and every candidate here is a `scala.collection.Iterable`. */
  private var collectionFromSetSym: SymId = SymId.None
  private var unmodifiableFromSym, unmodifiableSym: SymId = SymId.None
  /** each scala collection symbol → its companion's `from` factory, for `copyConstructor`. */
  private var fromSyms: Map[SymId, SymId] = Map.empty
  /** `JavaCollections`' statics, by name — see `sym`. */
  private var staticSyms: Map[String, SymId] = Map.empty
  /** the `java.util.stream` collapse — see `staticRewrite`. */
  private var asScalaBufferSym, filteredSym: SymId = SymId.None
  /** `mutable.Buffer`, so a collapsed stream can be TYPED as what it now emits. */
  private var bufferSym: SymId = SymId.None
  /** scala's own `sum` — a plain MEMBER name on the collapsed buffer, not a `JavaCollections` helper. */
  private var sumSym: SymId = SymId.None
  /** scala's own `map` — a plain member on a collapsed buffer, for the stream chain. */
  private var mapSym: SymId = SymId.None
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
    javaCollectionSym   = byScala.getOrElse(JavaCollectionFqn, SymId.None)
    collectionFromSym   = mint("from", JavaCollectionFqn + ".from")
    collectionFromSetSym = mint("fromSet", JavaCollectionFqn + ".fromSet")
    unmodifiableFromSym = mint("unmodifiableFrom", JavaCollectionFqn + ".unmodifiableFrom")
    unmodifiableSym     = mint("unmodifiable", JavaCollectionFqn + ".unmodifiable")
    // `asScalaBuffer` is an EXTENSION in JavaCollection's companion, which is exactly where scala 3
    // looks for one on that receiver type — so it needs no import, like every other name the
    // structural backend emits fully qualified (CLAUDE.md §6).
    asScalaBufferSym    = mint("asScalaBuffer", JavaCollectionFqn + ".asScalaBuffer")
    filteredSym         = mint("filtered", JavaCollectionFqn + ".filtered")
    bufferSym           = byScala.getOrElse("scala.collection.mutable.Buffer", SymId.None)
    sumSym              = mint("sum", "sum")
    mapSym              = mint("map", "map")
    staticSyms = CollectionsTransform.StaticHelpers
      .map(n => n -> mint(n, s"$JavaCollectionsFqn.$n")).toMap
    // one `from` per DISTINCT scala target, so `new ArrayList<>(c)` copies through the companion the
    // target type actually has. `Tuple2` is excluded: it is a `Kind.Entry`, not a collection, and
    // `Tuple2.from` does not exist — the `kindOf` gate in `copyConstructor` never offers it one.
    fromSyms = byScala.collect {
      case (fqn, id) if fqn.startsWith("scala.collection.") => id -> mint("from", s"$fqn.from")
    }.toMap
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

  /** an assignment's left-hand side declares the expected type just as a `val`'s `tpt` does; and a
    * cast this phase has just made IMPOSSIBLE is dropped rather than emitted.
    *
    * The cast is written by the frontend and is valid java — `(Collection<V>) anArrayList`. This
    * phase sends the two sides of it to UNRELATED families: `Collection` to the shim, the concrete
    * `ArrayList`/`List` to a `scala.collection.mutable` type, so the surviving
    * `asInstanceOf[JavaCollection[V]]` is applied to a value the phase itself guaranteed is a
    * `Buffer`. It cannot ever succeed. It COMPILES, and throws `ClassCastException` at run time —
    * found by the test suite, invisible to every count, and squarely CLAUDE.md §4.4's defect class
    * even though no java statement form is involved.
    *
    * Dropping it turns a runtime failure into a compile error at the same line, which is the
    * outcome ENGINE-LIMITS M6 asks for: refuse and be counted, never approximate. And it is dropped
    * ONLY for a source the phase retyped away — see [[impossibleShimCast]] for why the test must be
    * structural. */
  override def transformTerm(t: Term)(using Program): Term = t match
    case a: Tree.Assign => a.copy(rhs = coerce(a.lhs.tpe, a.rhs))
    case ty: Tree.Typed if impossibleShimCast(ty) => ty.expr
    case other          => other

  /** a cast TO a runtime shim whose SOURCE this phase has retyped OUT of the shim family — a cast
    * no value can satisfy, because the phase itself guaranteed the runtime value is a scala
    * collection and a scala collection is not a `balticporter.runtime` trait.
    *
    * Decided from `remap`/`kindOf` — the phase's own record of what it retyped — and NEVER from the
    * source type's NAME. Testing `fullName.startsWith("java.")` was tried and is wrong for the same
    * reason CLAUDE.md §4.56 gives for package renames: a prefix is not a structural fact. It swept
    * up `java.lang.Object`, and `(Collection<V>) anObject` is an ordinary DOWNCAST that this phase
    * does not touch on the source side — at run time the value IS a shim instance, so the cast (with
    * its target retyped to the shim) succeeds, and deleting it turned a correct program into a wrong
    * one. Every JDK type the phase leaves alone is in the same position.
    *
    * A SCALA collection source counts exactly as a retyped java one does, and for the same reason:
    * once the stream chain collapses, the value really is a `Buffer`, and
    * `asInstanceOf[JavaCollection[…]]` on it throws exactly as it did on the `ArrayList`. Both are
    * `kindOf` — the map is keyed on the phase's own scala targets, which is what makes "the phase
    * moved this away from the shim family" the one question asked. Dropping the cast is also what
    * lets `coerce` see the argument for what it is and bridge it properly — the cast was standing
    * between the two. */
  private def impossibleShimCast(t: Tree.Typed): Boolean =
    def scalaSym(s: SymId) = remap.getOrElse(s, s)
    val to   = headSym(t.tpt.tpe).map(scalaSym)
    val from = headSym(t.expr.tpe).map(scalaSym)
    to.exists(shimSyms.contains) && from.exists(f => !shimSyms.contains(f) && kindOf.contains(f))

  /** replace the head (type-constructor) symbol of a `TypeRef` / `AppliedType`, keeping args. */
  private def withHead(t: TypeRepr, s: SymId): TypeRepr = t match
    case TypeRepr.TypeRef(prefix, _)    => TypeRepr.TypeRef(prefix, s)
    case TypeRepr.AppliedType(tc, args) => TypeRepr.AppliedType(withHead(tc, s), args)
    case other                          => other

  override def transformApply(t: Tree.Apply)(using Program): Term =
    val t2 = wrapIterableArgs(t)
    copyConstructor(t2).orElse(staticRewrite(t2)).getOrElse {
      t2.fun match
        case Tree.Select(recv, m, _, so) => kindAt(recv) match
          case Some(k) => rewrite(k, recv, m, so, t2).getOrElse(t2)
          case None    => t2
        case _ => t2
    }

  /** Java's collection COPY CONSTRUCTOR — `new ArrayList<>(c)`, `new HashSet<>(c)`,
    * `new HashMap<>(m)`, `new ArrayDeque<>(c)`.
    *
    * The type mapping alone is not enough and the failure is asymmetric, which is what makes this
    * worth its own rule. `new ArrayList<>(10)` is a CAPACITY hint and maps correctly by accident —
    * `new ArrayBuffer(10)` means the same thing. `new ArrayList<>(c)` is a COPY, and
    * `new ArrayBuffer(c)` is `Required: Int`. Two java constructors, one scala constructor, and only
    * one of the two lands: measured in simple-graphs' `Graph.sortEdges`, as
    * `new ArrayBuffer[Tuple2[…]](this.edgeMap)` against `Required: Int`.
    *
    * `<Companion>.from(c)` is the scala counterpart, and every `scala.collection.mutable` companion
    * this phase targets has it. Gated on the ARGUMENT being a collection, so a capacity hint is left
    * exactly as it is. */
  private def copyConstructor(t: Tree.Apply)(using Program): Option[Term] = t.fun match
    case n: Tree.New =>
      val target = headSym(n.tpe).filter(kindOf.contains)
      val single = t.args match
        case List(a) if headSym(a.tpe).exists(kindOf.contains) => Some(a)
        case _                                                 => scala.None
      for
        tgt <- target
        arg <- single
        f   <- fromSyms.get(tgt)
      // the copy is typed as the TARGET, not as the argument: `new HashMap<>(aTreeMap)` is a
      // `HashMap`, and a node must describe what it emits (see `staticRewrite`).
      yield Tree.Apply(Tree.Ident(f, TypeRepr.NoType, t.origin), List(scalaView(arg)), f, n.tpe, t.origin)
    case _ => scala.None

  /** `java.util.Collections`' STATIC utilities — a receiver-less call, so `rewrite` (which is keyed
    * on the receiver's collection kind) never sees them and the call is emitted verbatim against
    * the real JDK class. That is how `Collections.unmodifiableCollection(...)` survived to the
    * compiler with `Required: java.util.Collection[?]` while everything around it had been retyped.
    *
    * Keyed on `owner#name` — the same identification `PortabilityCheck.exactMember` uses, and the
    * only one available for an external symbol (whose own `fullName` is just the member name).
    *
    * DELIBERATELY SMALL. What is here is what the corpus calls; the rest of `java.util.Collections`
    * is not silently mapped to something approximate, because the approximations are exactly where a
    * §4.4 defect would live — `Collections.unmodifiableList` has no read-only `Buffer` view to map
    * onto, and mapping it to the identity would drop the immutability with a green compile. An
    * unmapped static still fails to COMPILE, which is the honest outcome. */
  private def staticRewrite(t: Tree.Apply)(using p: Program): Option[Term] =
    def qualified(s: SymId) = for
      m <- p.symbolOf(s)
      o <- p.symbolOf(m.owner)
    yield s"${o.fullName}#${m.name}"
    val member  = qualified(t.method)
    // through a `TypeApply`: `xs.mapToObj[Integer](f)` is `Apply(TypeApply(Select(xs, mapToObj)))`,
    // and matching only `Select` silently skipped every explicitly-instantiated call — the chain
    // then collapsed its first link and stopped, leaving `value mapToObj is not a member of
    // Buffer[Int]`, an error that reads like a missing mapping rather than an unmatched shape.
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
      case (Some("java.util.Collections#shuffle"), List(xs, rnd))  => Some(factory(sym("shuffle"), List(xs, rnd)))
      // `java.util.Arrays.asList` is not on `Collections`, but it is the same KIND of thing — a
      // receiver-less JDK factory whose result type the port has already retyped — so it shares the
      // table and the runtime object rather than earning a mechanism of its own.
      //
      // …and it is the ONE rewritten static whose runtime counterpart is a SCALA vararg (`A*`),
      // which is where the engine's own vararg convention has to be undone. See [[asListArgs]].
      case (Some("java.util.Arrays#asList"), args)                 =>
        asListArgs(args).map(as => factory(sym("asList"), as))
      // `Map.Entry` became a `Tuple2`, so `Entry`'s own statics must come along or the call survives
      // to the compiler naming a type the port no longer produces.
      case (Some("java.util.Map$Entry#comparingByKey" | "java.util.Map.Entry#comparingByKey"), List(cmp)) =>
        Some(factory(sym("comparingByKey"), List(cmp)))
      case (Some("java.util.Map$Entry#comparingByValue" | "java.util.Map.Entry#comparingByValue"), List(cmp)) =>
        Some(factory(sym("comparingByValue"), List(cmp)))

      // ---- java.util.stream: the CHAIN collapses, it does not translate call-for-call ----
      //
      // `xs.stream().filter(p).collect(Collectors.toList())` is three calls in java and one in
      // scala, because a scala collection carries the operations directly. So `stream()` becomes the
      // receiver AS a scala collection, `collect(toList())` becomes nothing at all, and only the
      // middle operation survives. Mapping any one of the three on its own produces something that
      // does not type-check — which is why ENGINE-LIMITS K6 records the whole family as untranslated
      // rather than partly done.
      //
      // Semantics: `asScalaBuffer` copies, where java's stream is lazy. The chain's TERMINAL
      // (`collect`) materialises, so the observable result is identical; what changes is that a
      // side-effecting predicate would see the elements in the same order but with no short-circuit.
      // No corpus site has one, and a lazy view would not survive `collect` becoming a no-op.
      // The collapsed node is typed `Buffer[E]`, NOT the `Stream<E>` the java call had. That is not
      // bookkeeping: `coerce` decides whether to bridge from the node's own type, so a collapse that
      // kept `Stream` produced `Found: Buffer[V] / Required: JavaCollection[V]` one call further out
      // — the chain translated and then failed to meet the method it fed. A rewritten node must
      // describe the expression it now emits, the same invariant `values()` restores above.
      //
      // The key is the DECLARING type of the resolved method, not the receiver's written type, and
      // that distinction is the whole reason one arm serves every collection: only `Collection`
      // declares `stream()`, so the frontend resolves all thirteen receiver spellings in
      // `CollectionsTransformSpec` — `ArrayDeque`, `TreeSet`, a program class extending
      // `AbstractCollection` — to `java.util.Collection#stream`. `List`/`Set` are kept as
      // defensive alternatives for a frontend that reports the receiver's type instead; neither
      // interface declares the method, so on this frontend they never fire. See `collapsed` for
      // the audit that established this and for the one shape where the collapse does not reach.
      case (Some("java.util.Collection#stream" | "java.util.List#stream" | "java.util.Set#stream"), Nil) =>
        recv.map(r => Tree.Select(r, asScalaBufferSym, asBuffer(r.tpe), t.origin))
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
      // A stream OPERATION is rewritten only when its receiver is a collection this phase ALREADY
      // collapsed — never on the method name alone. `"…".lines()` (libGDX's `JsonMatcherTests`) is a
      // `java.util.stream.Stream` with no collection behind it, so nothing collapsed it and rewriting
      // its `filter` produced `Found: java.util.stream.Stream[String] / Required: Buffer[A]`:
      // measured 0 -> 1 on the test port. A stream chain from a non-collection source is simply not
      // translated, and must fail as such.
      // `mapToDouble(f).sum()` is two more links of the same chain. `sum` is scala's own name and
      // PARENLESS, so it is a `Select`; `mapToDouble` carries java's widening (see the runtime).
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
        // `toCollection(Factory::new)` carries its target INSIDE the collector, as a factory — so the
        // collapse cannot end at the receiver the way `toList` does. `into` builds the factory's
        // collection and fills it, which is what java's collector does.
        val f = collector match { case a: Tree.Apply => a.args; case _ => Nil }
        if f.sizeIs != 1 then None
        else recv.map(r => factory(sym("into"), List(r, f.head)))
      case (Some("java.util.stream.Stream#filter"), List(pred)) if filteredSym != SymId.None && collapsed(recv) =>
        recv.map(r => Tree.Apply(Tree.Ident(filteredSym, TypeRepr.NoType, t.origin), List(r, pred),
                                 filteredSym, r.tpe, t.origin))
      // the terminal, and only for the collector the receiver ALREADY is. `Collectors.toSet` and
      // `toMap` each need a different target type, and guessing one would be a silent wrong answer;
      // unmapped, they fail to compile, which is the honest outcome. `toCollection(f)` was on that
      // list and no longer is — it is the `into` arm above, which reads the target out of the
      // collector's own factory instead of guessing it. A comment that still names a case the code
      // handles is worse than no comment: it is the reason not to look.
      case (Some("java.util.stream.Stream#collect"), List(collector))
          if collapsed(recv) && qualified(collectorOf(collector)).contains("java.util.stream.Collectors#toList") =>
        recv
      case _ => None

  /** the arguments `JavaCollections.asList` should receive — or `None`, which REFUSES the whole
    * rewrite and leaves the JDK call to fail to compile under its own name.
    *
    * Java's `Arrays.asList` has TWO shapes behind one syntax, and only one of them may reach the
    * runtime helper (its doc states the contract; read it before changing this):
    *
    *   - **elements** (`asList(a, b, c)`) — java packs them into a fresh array nobody else can
    *     reach, so a `Buffer` differs only in being growable, which is permissive;
    *   - **a caller-held array passed whole** (`asList(arr)`) — java returns a LIVE VIEW, and a
    *     copy would silently detach every aliased write. CLAUDE.md §4.4 exactly.
    *
    * The engine's own vararg convention is what makes these hard to tell apart HERE. A java `T...`
    * parameter is emitted as `Array[T]`, so the frontend MATERIALISES a call's trailing arguments
    * into a `Tree.NewArray` — which is right for every in-program vararg method and wrong for this
    * one helper, whose scala signature is `asList[A](xs: A*)`. Unfixed, `asList(xs, xs)` (two array
    * ELEMENTS — correct, translatable java) emitted the pack as ONE argument with no spread and
    * failed E007, while `asList(1, 2, 3)` came out right only by accident: the frontend declines to
    * pack primitives, so those arrived as bare elements already.
    *
    * So the pack is opened back into separate arguments — which is CLAUDE.md §6's spread with no
    * spread node needed, and makes both frontend outcomes emit the same shape. A LITERAL array in
    * the slot (`asList(new String[]{a, b})`) opens too, and that is sound rather than sloppy: the
    * array is freshly allocated at the call, so nobody holds the alias the live view would matter
    * for.
    *
    * A single argument that IS an array is the aliasing form and is REFUSED — the rewrite does not
    * happen at all, so the emitted text names `java.util.Arrays.asList` and fails to compile there.
    * That is deliberately louder than the previous behaviour, which emitted
    * `JavaCollections.asList(xs.asInstanceOf[Array[Object]])` and read as a broken runtime helper
    * rather than an untranslated call. ENGINE-LIMITS M6: the compiler is the tracker. A faithful
    * live view is possible in principle — a fixed-size `Buffer` over the array, with `add`/`remove`
    * throwing as java's does — but not reachable from here: the frontend has already coerced the
    * argument to the ERASED formal (`Array[Object]`), so the element type needed to type the view
    * is gone by this point, and recovering it is a frontend change with far wider blast radius. */
  private def asListArgs(args: List[Term])(using p: Program): Option[List[Term]] =
    def isArray(t: TypeRepr) = headSym(t).flatMap(p.symbolOf).exists(_.fullName == "scala.Array")
    args match
      case init :+ Tree.NewArray(_, Nil, Some(elems), _, _) => Some(init ++ elems)
      case List(a) if isArray(a.tpe)                        => scala.None
      case _                                                => Some(args)

  /** a `JavaCollections` static by name. Minted EAGERLY in `run` — symbols cannot be added once the
    * table is built, and the table is built before the traversal that consults these. An unlisted
    * name yields `SymId.None`, which `staticRewrite` treats as "not available" rather than emitting a
    * dangling reference. */
  private def sym(name: String): SymId = staticSyms.getOrElse(name, SymId.None)

  /** the method a collector expression calls, so `collect`'s argument can be identified. */
  private def collectorOf(t: Term): SymId = t match
    case a: Tree.Apply => a.method
    case _             => SymId.None

  /** the OTHER direction from [[coerce]]: a shim reaching a slot that wants a scala collection.
    *
    * `coerce` bridges scala → shim, because that is the direction a shim-typed PARAMETER needs. The
    * reverse appears wherever the port builds a scala collection FROM one — `new ArrayList<>(c)`
    * where `c` is a `Collection`, which `copyConstructor` routes through `ArrayBuffer.from` and which
    * therefore needs an `IterableOnce`, not a `JavaCollection`. Both directions exist because the two
    * families are deliberately unrelated; neither is the "real" one. */
  private def scalaView(t: Term): Term =
    if asScalaBufferSym != SymId.None && headSym(t.tpe).exists(shimSyms.contains)
    then Tree.Select(t, asScalaBufferSym, asBuffer(t.tpe), t.origin)
    else t

  /** has this receiver already been collapsed from a `Stream` to a scala sequence? The shims are
    * excluded: `filtered` takes a `Buffer`, and a shim is what the collapse consumes, not produces.
    *
    * ==This is the RETYPED KIND, not the written type — audited, probed and DISPROVED as a defect==
    * The question CLAUDE.md §4.56 demands ("what did the PHASE do to this type?") is exactly what
    * `kindOf` answers: the map is keyed on symbols this phase MINTED in `run`, so a hit means the
    * phase itself put that scala collection there. Three things would have to be true for the
    * answer to diverge from what the phase did, and each was checked rather than argued:
    *
    *  1. `recv.tpe` still naming a JAVA collection symbol here. It cannot:
    *     `StandardTraversal.mapTerm` routes a node's `tpe` AND its children through `transformType`
    *     — hence through `remap` — BEFORE calling `transformApply`, and every node this phase
    *     mints carries a type computed from an already-mapped one (`asBuffer`, `r.tpe`, `t.tpe`).
    *     So the `remap.getOrElse` normalisation `coerce` and `impossibleShimCast` need (they read
    *     `Symbol.info`, which is retyped AFTER the trees) would be a no-op here.
    *  2. `recv.tpe` naming a scala collection the phase did NOT introduce. `kindOf` has no key for
    *     one, by construction.
    *  3. the receiver really being a collapsed buffer while its recorded type says otherwise. This
    *     one IS reachable — through a `java.util.stream.Stream`-typed SLOT, since the stream family
    *     is deliberately not retyped (ENGINE-LIMITS K6):
    *
    *         Stream<String> st = f.stream();   //  st : Stream, value : Buffer
    *         st.filter(p).collect(toList());   //  not collapsed
    *
    *     and there `false` is the RIGHT answer. The declaration is what has no translation; making
    *     this guard say `true` would rewrite the operation and leave the `Stream`-typed slot in
    *     place, moving the error rather than closing it. Measured: that emission is 2 compile
    *     errors, so the refusal is loud (ENGINE-LIMITS M6), never silent.
    *
    * The collapse SOURCE arm is keyed the same way and not on the receiver's written type either:
    * it matches `owner#name` for the RESOLVED method, and the frontend resolves `stream()` to its
    * DECLARING interface — measured `java.util.Collection#stream` for 13 of 13 receiver spellings,
    * a program class extending `AbstractCollection` included. `CollectionsTransformSpec` pins all
    * thirteen, so the day that resolution changes a test says so instead of the chain silently
    * ceasing to translate. */
  private def collapsed(recv: Option[Term]): Boolean =
    recv.flatMap(r => headSym(r.tpe)).exists(s => kindOf.get(s).contains(Kind.Seq) && !shimSyms.contains(s))

  /** the same type with `Buffer` as its head — what `asScalaBuffer` on a `JavaCollection[E]` returns.
    *
    * Falls back to a BARE `Buffer` when the input has no head to replace. That is not a corner case:
    * an external call's node often carries `NoType`, and `withHead` then returns `NoType` unchanged —
    * so the collapsed node claimed no type at all, `collapsed` said false one link further along the
    * chain, and `IntStream.range(0, n).mapToObj(...)` stopped translating half way with
    * `value mapToObj is not a member of Buffer[Int]`. The head is the only part any caller reads. */
  private def asBuffer(t: TypeRepr): TypeRepr =
    if bufferSym == SymId.None then t
    else
      val h = withHead(t, bufferSym)
      if headSym(h).contains(bufferSym) then h else TypeRepr.TypeRef(TypeRepr.NoPrefix, bufferSym)

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

  /** a RETURN is a shim-typed slot exactly as a formal, a `val` and an assignment target are — the
    * method's declared return type is the expected type of every `return` in its body.
    *
    * The walk is DELIBERATELY BOUNDED and that is the whole subtlety: a `return` inside a lambda,
    * an anonymous class's method or a local class returns from THAT, not from here, so descending
    * into one would coerce it against the wrong type. Only the node kinds that carry a STATEMENT of
    * the same method are followed; everything else — including `Lambda`, `New` (whose `anon` body
    * holds its own methods) and any nested `DefDef`/`ClassDef` — stops.
    *
    * CLAUDE.md §3 says to walk with `StandardTraversal` and never a private recursion, and the
    * reason it says so is that a hand-rolled walk stops at whatever its author forgot. Here
    * stopping IS the semantics, so the rule is met the other way: the default arm does NOT
    * descend, which makes an unhandled node kind a MISSED coercion — an uncoerced `return`, i.e. a
    * compile error at that line — and never a wrong one. The failure direction is loud by
    * construction. (Java's `return` is a statement, so it cannot occur inside an argument or an
    * operand; the nine kinds below are the whole statement-carrying vocabulary.)
    *
    * A method body's TAIL expression is not a return value in this TIR — every Java method exits
    * through `Tree.Return`, so `Block.expr` in a ported body is a statement or `()`. It is walked
    * (a `Return` sitting there is coerced) but never coerced AS a result; a frontend that lowered
    * the tail to a bare expression would need one more case here. */
  override def transformDefDef(t: Tree.DefDef)(using Program): Tree.DefDef =
    t.copy(rhs = t.rhs.map(coerceReturns(t.returnTpt.tpe, _)))

  private def coerceReturns(want: TypeRepr, t: Term)(using Program): Term = t match
    case x: Tree.Return       => x.copy(expr = x.expr.map(coerce(want, _)))
    case x: Tree.Block        => x.copy(stats = x.stats.map(coerceReturnsIn(want, _)), expr = coerceReturns(want, x.expr))
    case x: Tree.If           => x.copy(thenp = coerceReturns(want, x.thenp), elsep = coerceReturns(want, x.elsep))
    case x: Tree.While        => x.copy(body = coerceReturns(want, x.body))
    case x: Tree.DoWhile      => x.copy(body = coerceReturns(want, x.body))
    case x: Tree.For          => x.copy(body = coerceReturns(want, x.body))
    case x: Tree.ForEach      => x.copy(body = coerceReturns(want, x.body))
    case x: Tree.Synchronized => x.copy(body = coerceReturns(want, x.body))
    case x: Tree.Labeled      => x.copy(stmt = coerceReturns(want, x.stmt))
    // §4.58: a statement-shape walk must read THROUGH the comment wrapper — a `return` under a
    // java comment is still a return, and with the trivia harvest live that is the common case.
    case x: Tree.Commented    => x.copy(stmt = coerceReturns(want, x.stmt))
    case x: Tree.Try =>
      x.copy(body = coerceReturns(want, x.body),
             catches = x.catches.map(c => c.copy(body = coerceReturns(want, c.body))),
             finalizer = x.finalizer.map(coerceReturns(want, _)))
    case x: Tree.Match => x.copy(cases = x.cases.map(c => c.copy(body = coerceReturns(want, c.body))))
    case other         => other

  /** a `Block` statement that is a TERM continues this method's return scope; a `ValDef` cannot
    * contain a `return` at all, and a nested `DefDef`/`ClassDef` opens its own. */
  private def coerceReturnsIn(want: TypeRepr, s: Statement)(using Program): Statement = s match
    case t: Term => coerceReturns(want, t)
    case other   => other

  /** Bridge a scala collection into a SHIM-TYPED SLOT — an argument, a declared `val`, an
    * assignment target, a RETURN. Nothing else in the phase decides this; every position routes
    * here.
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
    * alone, and an unrecognised type produces an honest compile error rather than a wrong wrap.
    *
    * ==COVERAGE — what this seam does and does not close==
    * Stated exactly, because "one seam covers every slot" is what this doc used to claim and it
    * was not true of two of the six cells:
    *
    * | source \ target | `JavaIterable` | `JavaCollection` |
    * |---|---|---|
    * | `Kind.Seq` (`Buffer`, `ArrayBuffer`, `Queue`, `ArrayDeque`) | `JavaIterable.from` | `JavaCollection.from` |
    * | `Kind.Set` (`mutable.Set` & co) | `JavaIterable.from` | `JavaCollection.fromSet` |
    * | `Kind.Map` (`mutable.Map` & co) | `JavaIterable.from` | REFUSED — see below |
    * | `Kind.Entry` (`Tuple2`) | n/a | n/a |
    *
    * `JavaIterable.from` takes a `scala.collection.Iterable`, so every kind reaches it with nothing
    * added — and a scala `Map[K, V]` IS an `Iterable[(K, V)]`, which is exactly what java's
    * `entrySet()` view is (the `entrySet` rewrite returns the map itself).
    *
    * `Kind.Map` into `JavaCollection` is REFUSED, and the refusal is the honest answer rather than
    * a gap: java's `Map` is neither a `Collection` nor an `Iterable`, so no valid java sends one to
    * such a slot. The only path is this phase's own `entrySet()` rewrite, and a `Collection` view
    * of a map's entries would have to reproduce `entrySet().remove(e)` — which removes a mapping
    * only when the KEY AND THE VALUE both match. Guessing that is precisely the §4.4 mistake; the
    * unwrapped value fails to COMPILE at the slot, which is what ENGINE-LIMITS M6 asks for.
    * `Kind.Entry` is a `Tuple2` and not a collection at all, so it never offers a source.
    *
    * The SHIMS themselves are excluded on both sides: `JavaCollection` already IS a `JavaIterable`
    * and neither can be rebuilt from the other. `JavaIterator` is `Kind.Seq` and is NOT a
    * collection, so it is excluded too — by the same `shimSyms` test.
    *
    * One SOURCE is refused whatever the target: `map.keySet()`. Its node claims the retyped
    * `mutable.Set`, and the scala it emits is `m.keySet`, whose type is `scala.collection.Set` — the
    * same disagreement `transformValDef` already encodes for a declaration initialised from it, and
    * ENGINE-LIMITS §0's "the recorded type is not a witness of what the emitter will print". Wrapping
    * on a type the phase knows the value does not have would emit a call that cannot compile while
    * NAMING the wrapper instead of the boundary, so the unwrapped value is left to fail at the slot
    * exactly as it did before this seam existed. `Map.values()` has no such problem: its rewrite
    * already restores the invariant by wrapping at the call. */
  private def coerce(expected: TypeRepr, actual: Term)(using Program): Term =
    // the symbol table is retyped AFTER the trees (see `run`), so a formal read here is still the
    // ORIGINAL java symbol — `java.lang.Iterable`, not the shim. Compare through `remap`, which
    // makes this correct on either side of that pass.
    def scalaSym(x: SymId): SymId = remap.getOrElse(x, x)
    val wants = headSym(expected).map(scalaSym)
    val got   = headSym(actual.tpe).map(scalaSym)
    val from  = got.filterNot(shimSyms.contains).flatMap(kindOf.get)
    val factory = from match
      case _ if wants.isEmpty || isKeySetView(actual)                                 => SymId.None
      case Some(Kind.Seq | Kind.Set | Kind.Map) if wants.contains(javaIterableSym)   => iterableFromSym
      case Some(Kind.Seq)                       if wants.contains(javaCollectionSym) => collectionFromSym
      case Some(Kind.Set)                       if wants.contains(javaCollectionSym) => collectionFromSetSym
      case _                                                                          => SymId.None
    if factory == SymId.None then actual
    else
      // The wrap is TYPED as what it now emits, which is the RETYPED expected type and not the one
      // read above. `wrapIterableArgs` takes its `expected` from a FORMAL in the symbol table, and
      // the table is retyped AFTER the trees (see `run`) — so the raw value is still
      // `java.util.Collection` while the emitted call is a `JavaCollection.from(...)`. Left as it
      // was, this node claimed a java type the port no longer produces, which is exactly the
      // invariant the stream collapse had to learn (ENGINE-LIMITS K6's first rule) and which
      // `CollectionBoundaryCheck` reports as `MappedTypeSurvived` — it found these three sites.
      // No emitted text moves: nothing prints an `Apply`'s own type. What moves is what a LATER
      // reader concludes about the value.
      val tpe = wants.map(withHead(expected, _)).getOrElse(expected)
      Tree.Apply(Tree.Ident(factory, TypeRepr.NoType, actual.origin), List(actual),
                 factory, tpe, actual.origin)

  /** `m.keySet()` — see [[coerce]]. The same structural test [[transformValDef]] uses, so the two
    * places that know this node's `tpe` overstates the emitted scala agree by construction. */
  private def isKeySetView(t: Term)(using Program): Boolean = t match
    case Tree.Select(recv, sym, _, _) => methodName(sym) == "keySet" && kindAt(recv).contains(Kind.Map)
    case _                            => false

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
      // `m.values()` is the same provenance problem as `iterator()` above, and the same fix.
      // Java's `Map.values()` is declared `Collection<V>`, so every slot the port derived from that
      // asks for the SHIM — while the emitted `m.values` is a `scala.collection.Iterable`. The TIR
      // cannot see the disagreement: the node's `tpe` is the retyped `Collection<V>`, so it CLAIMS
      // to be a shim already and `coerce` correctly declines to wrap it. Wrapping here restores the
      // invariant that a node's type describes the expression it emits.
      //
      // `unmodifiableFrom` and not `from`: java's `values()` is a VIEW that rejects `add`, so
      // read-only is java's own behaviour rather than a capability lost in translation.
      case ("values", Nil, Kind.Map) if unmodifiableFromSym != SymId.None =>
        val sel = Tree.Select(recv, m, t.tpe, t.origin) // parenless, as the generic case below
        Some(Tree.Apply(Tree.Ident(unmodifiableFromSym, TypeRepr.NoType, so), List(sel), unmodifiableFromSym, t.tpe, so))
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
      // Java's `List` declares TWO one-argument `remove`s that do OPPOSITE things, and scala's
      // `Buffer` has only one of them:
      //
      //   * `remove(int index)`  — deletes the element AT that index and returns it. Scala's
      //     `Buffer.remove(Int): A` is exactly this, so it needs no rewrite at all.
      //   * `remove(Object o)`   — deletes the FIRST element equal to `o` and returns whether it
      //     did. Scala's `Buffer` has no such method; the nearest thing, `-=`, returns the buffer.
      //
      // Emitting `buffer.remove(x)` for the second is CLAUDE.md §4.4 in its purest form: where the
      // element type is `Integer`, scala's `Integer2int` conversion applies silently and the call
      // becomes INDEX removal — `[10, 11, 12].remove(Integer.valueOf(1))` removes nothing in java
      // and removes `11` in the port, with no compile error and no count moved. (Where the element
      // is anything else the same emission at least fails to compile — `Found: String / Required:
      // Int` — which is how narrow the visible half of this defect was.)
      //
      // WHICH OVERLOAD JAVA RESOLVED is read off the call's RESULT type, and that is total rather
      // than heuristic: `remove(Object)` returns a PRIMITIVE `boolean`, while `remove(int)` returns
      // the element type — which can never be primitive, because java generics cannot be
      // instantiated at one. So `scala.Boolean` identifies the by-value overload uniquely, and it
      // stays right where reconstructing java's applicability rules from the argument's type would
      // not: a `List<Boolean>` index removal is `java.lang.Boolean` (boxed, distinct), and an
      // `ArrayDeque` has no index overload at all, so `deque.remove(5)` — which java boxes and
      // sends to `remove(Object)` — is classified by value even though its argument is an `int`.
      // Verified by pipeline probe over all six shapes.
      //
      // ALWAYS the faithful form, never the simple one where the result is discarded: this hook
      // sees an `Apply`, not the statement it sits in, so "the result is unused" is not a fact
      // available here — and a discarded `Boolean` costs nothing.
      case ("remove", List(x), Kind.Seq) if removesByValue(t) && sym("removeValue") != SymId.None =>
        Some(Tree.Apply(Tree.Ident(sym("removeValue"), TypeRepr.NoType, so), List(recv, x),
                        sym("removeValue"), t.tpe, t.origin))
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

  /** did java resolve `Collection.remove(Object)` (by VALUE, returning `boolean`) rather than
    * `List.remove(int)` (by INDEX, returning the element)? See the `remove` arm in [[rewrite]] for
    * why the result type answers this exactly. A call whose result type the frontend could not
    * record answers `false` and is left as scala's index removal — the pre-existing behaviour, and
    * the one that at least matches java for every receiver that HAS an index overload. */
  private def removesByValue(t: Tree.Apply)(using p: Program): Boolean =
    headSym(t.tpe).flatMap(p.symbolOf).exists(_.fullName == "scala.Boolean")

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
  /** `java.util.Collections`' statics — a receiver-less utility class, which is why they need their
    * own home rather than a rewrite keyed on a receiver's collection kind. */
  val JavaCollectionsFqn = s"${RuntimeArtifact.Package}.JavaCollections"

  /** every `JavaCollections` member the transform may emit. One list, so a new JDK utility is one
    * line here, one arm in `staticRewrite` and one method in the runtime object — and a typo is a
    * `SymId.None` that declines the rewrite rather than a dangling name in emitted code. */
  val StaticHelpers: List[String] =
    List("sort", "sortNatural", "reverse", "shuffle", "asList", "removeValue",
         "comparingByKey", "comparingByValue", "sortedWith", "into", "mapToDouble", "intRange")

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
  val runtimeTypes: Set[String] = Set(JavaIteratorFqn, JavaIterableFqn, JavaCollectionFqn, JavaCollectionsFqn)

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
