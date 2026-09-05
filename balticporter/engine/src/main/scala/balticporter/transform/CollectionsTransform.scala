package balticporter.transform

import balticporter.core.{MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource, RequiresRuntime, RuntimeArtifact, SurfacePolicy}
import balticporter.tir.*

/** `java.util` collections to `scala.collection.mutable`. Retypes every collection
  * occurrence via the symbol table, rewrites call shapes kind-aware, coerces at
  * JDK/external seams. [[RuleScope]] controls which declarations are reached;
  * unclosable scope seams are counted by [[CollectionBoundaryCheck]].
  */
final class CollectionsTransform(
    val scope: RuleScope = RuleScope.Everywhere(),
    /** Java FQN to scala FQN retarget: retyped at every occurrence, NO kind-aware rewrites.
      * The scala target must extend/implement the java source (no coercion needed).
      * A key colliding with `typeMap` is refused. Empty = no-op. */
    val retarget: Map[String, String] = Map.empty,
    /** Per-retarget member call-site rewrites, keyed `(memberName, arity)` to [[RetargetRewrite]].
      * Descriptor-keyed [[retargetRewritesByDesc]] wins over arity-keyed at the same member.
      * Empty = no-op. `MergeablePolicy` unions independent keys. */
    val retargetRewrites: Map[String, Map[(String, Int), CollectionsTransform.RetargetRewrite]] = Map.empty,
    /** Descriptor-keyed retarget rewrites — `(name, Descriptor)` to [[RetargetRewrite]].
      * Wins over arity-keyed [[retargetRewrites]] at the same member.
      * For members overloaded at the same arity. Empty = no-op. */
    val retargetRewritesByDesc: Map[String, Map[(String, Descriptor), CollectionsTransform.RetargetRewrite]] = Map.empty,
    /** Type argument mapping for arity-changing retargets — source FQN to target arg template.
      * Each element: `SourceArg(i)` or `FixedType(fqn)`. List length must equal target arity.
      * Orphan keys (no `retarget` entry) refused at construction. Empty = no-op. */
    val retargetTypeArgs: Map[String, List[CollectionsTransform.RetargetArg]] = Map.empty,
    /** External generic types whose type arguments a third party reifies at run time.
      * Arguments stay in java's namespace; values bridged at use via [[externalProducer]].
      * [[UniversalCarriers]] (`java.lang.Class`) always added. Empty = no-op.
      * // ENGINE-LIMITS K20 */
    val reifiedCarriers: Set[String] = Set.empty,
    /** External types that read the runtime representation of a value at an opaque slot.
      * Bridge is [[ReifiedFqn]]`.toJavaValue` (identity for non-retyped, deep-by-view for retyped).
      * Per-library policy; empty = no-op. NOT part of [[surfaceFingerprint]].
      * // ENGINE-LIMITS K21 */
    val reflectiveSinks: Set[String] = Set.empty,
    /** Additional collection families — java FQN to (scala FQN, Kind), merged into [[typeMap]]
      * at construction. Collisions with JDK entries or `retarget` refused. Empty = no-op. */
    val families: Map[String, (String, CollectionsTransform.Kind)] = Map.empty,
    /** Per-entry scopes for [[families]] — java source FQN to `RuleScope`.
      * A key with no family entry is ignored. Default `Everywhere(Set.empty)`.
      * // ENGINE-LIMITS D12 */
    val familyScopes: Map[String, RuleScope] = Map.empty,
    /** Retarget coercions — `(actualHeadFQN, expectedHeadFQN)` to template string.
      * `$0` = actual value. Rendered as `Tree.Opaque.spliced` at type boundaries.
      * Empty = no-op. `MergeablePolicy` unions; same pair with different template refuses. */
    val retargetCoercions: Map[(String, String), String] = Map.empty,
    /** Indexed field rewrites keyed by (source FQN, field name) to [[RetargetRewrite.IndexedField]].
      * Separate from [[retargetRewrites]] so a field and a method of the same name can coexist
      * (e.g. ArrayMap `keys` method -> Collect AND `keys` field -> IndexedField). Scanned
      * alongside [[retargetRewrites]] for `indexedFieldSyms`. Empty = no-op.
      * // CLAUDE.md §1(b) */
    val retargetIndexedFields: Map[String, Map[String, CollectionsTransform.RetargetRewrite.IndexedField]] = Map.empty,
) extends Phase, Rewrite, RequiresRuntime, PolicySource, SurfacePolicy, MergeablePolicy, PolicyBound,
    CollectionsRetarget, CollectionsReified, CollectionsBoundary,
    CollectionsPolicy, CollectionsCalls:
  import CollectionsTransform.{JavaCollectionFqn, JavaCollectionsFqn, JavaIterableFqn, JavaIteratorFqn, Kind}

  // prepared in `run`, read by the hooks.
  private[transform] var remap: Map[SymId, SymId]    = Map.empty
  /** Target SymIds of the full remap — for `transformType`'s wildcard-strip checks. */
  private[transform] var remapTargets: Set[SymId]   = Set.empty
  /** fullName to minted SymId fallback for `transformType` — covers dependent-interned SymIds. */
  private[transform] var remapByFullName: Map[String, SymId] = Map.empty
  /** source SymId to java FQN for family remap entries — for per-entry scope (D12). */
  private[transform] var familyRemapSources: Map[SymId, String] = Map.empty
  /** Declared classes by symbol — source of class type parameters. */
  private[transform] var classDefsBySym: Map[SymId, Tree.ClassDef] = Map.empty
  private[transform] var kindOf: Map[SymId, Kind]    = Map.empty // scala collection symbol → kind
  /** Per-class info about minted collection parents. `kinds` = set (a class may implement several).
    * `probes` = first type arg of each mapped parent. `shims` = standalone targets (no member clash).
    * `targets` = scala FQNs. `declared` = this class's own mapped clauses (non-transitive). */
  private[transform] final case class MintedParents(kinds: Set[Kind], probes: List[TypeRepr],
                                         tparams: List[SymId], shims: Set[String],
                                         targets: Set[String] = Set.empty,
                                         subsumed: Map[String, String] = Map.empty,
                                         declared: List[(Kind, List[TypeRepr])] = Nil)
  private[transform] var parentClash: Map[SymId, MintedParents] = Map.empty
  /** Collected `super.<JDK default>` rewrites: (enclosing class, callee, member name). */
  private[transform] val superDefaults = collection.mutable.ListBuffer.empty[(SymId, SymId, String)]
  private[transform] var opPlusEq, opMinusEq, opPlusPlusEq: SymId = SymId.None
  /** Plain arithmetic operators for compound-FieldWrite expansion: `size -= 1` -> `setSize(size - 1)`. */
  private[transform] var compoundOps: Map[String, SymId] = Map.empty
  private[transform] var updateSym, insertSym, getOrElseSym, containsSym: SymId = SymId.None
  /** `mutable.Map.put`/`remove` — return previous value (unlike `update`/`-=`). */
  private[transform] var putSym, removeSym: SymId = SymId.None
  /** Deque members — `poll`/`peek` go through `Option`/`orNull` (null-on-empty vs throw). */
  private[transform] var removeHeadOptionSym, headOptionSym, orNullSym, prependSym: SymId = SymId.None
  /** `Stack.empty()` renamed to `isEmpty` — `empty` on a `Buffer` is the companion's factory. */
  private[transform] var isEmptySym: SymId = SymId.None
  /** `Option` members for `Kind.Opt` arms. */
  private[transform] var getSym, isDefinedSym, someSym, noneSym: SymId = SymId.None
  /** `JavaEnumMap.ofType` and `JavaEnumSet` static factory symbols (minted). */
  private[transform] var enumMapOfTypeSym: SymId = SymId.None
  private[transform] var enumSetSyms: Map[String, SymId] = Map.empty
  /** this run's symbol for a scala/shim FQN, or `SymId.None` where the program never names it. */
  private[transform] var byScalaSyms: Map[String, SymId] = Map.empty
  private[transform] def byScalaSym(fqn: String): SymId = byScalaSyms.getOrElse(fqn, SymId.None)
  private[transform] def enumSetSym(n: String): SymId   = enumSetSyms.getOrElse(n, SymId.None)
  /** java 8 `Collection.forEach(Consumer)` — scala's is `foreach`, differing only in case, which
    * makes the failure read like a typo rather than a missing mapping. */
  private[transform] var foreachSym: SymId = SymId.None
  private[transform] var key1Sym, value2Sym, selfParamSym: SymId = SymId.None
  /** Bound method-ref receiver binding and lambda argument parameter symbols (max arity 2). */
  private[transform] var recvBindSym: SymId = SymId.None
  private[transform] var argParamSyms: Vector[SymId] = Vector.empty
  /** the scala side of a BRIDGED member (`ENGINE-LIMITS.md` K28.1) — the types its signature is
    * written in, and the two `asScala` views its body reaches java's answer through.
    * `iteratorMemberSym` is scala's parameterless `iterator`, for a `Map` with no java
    * `iterator()` reaching `entrySet().iterator()`. Resolved-or-minted like `unsupportedOpSym`. */
  private[transform] var optionSym, scalaIteratorSym, scalaIterableSym, iterableOnceSym: SymId = SymId.None
  private[transform] var tuple2Sym, boolSym, intSym, unitSym: SymId = SymId.None
  private[transform] var asScalaIteratorSym, asScalaIterableSym, iteratorMemberSym: SymId = SymId.None
  private[transform] var unitTpe: TypeRepr = TypeRepr.NoType

  /** `JavaIterable` + its `from` factory — see `coerce`. */
  private[transform] var javaIterableSym, iterableFromSym: SymId = SymId.None
  /** `JavaCollection` + its `from` factory — the same seam, one type up. `unmodifiableFromSym` is
    * the read-only sibling (a `Map.values()` view); `unmodifiableSym` is
    * `Collections.unmodifiableCollection`. */
  private[transform] var javaCollectionSym, collectionFromSym: SymId = SymId.None
  /** the `Kind.Set` source's factory into a `JavaCollection` slot — a DISTINCT NAME rather than an
    * overload of `from`, for the reason `JavaCollection.unmodifiableFrom` gives: an overload
    * resolves on the static type, and every candidate here is a `scala.collection.Iterable`. */
  private[transform] var collectionFromSetSym: SymId = SymId.None
  private[transform] var unmodifiableFromSym, unmodifiableSym: SymId = SymId.None
  /** each scala collection symbol → its companion's `from` factory, for `copyConstructor`. */
  private[transform] var fromSyms: Map[SymId, SymId] = Map.empty
  /** each HASHED scala collection symbol → its companion's `defaultLoadFactor`, for
    * [[capacityConstructor]]. Keyed on the phase's OWN targets, exactly as `fromSyms` is. */
  private[transform] var loadFactorSyms: Map[SymId, SymId] = Map.empty
  /** `JavaCollections`' statics, by name — see `sym`. */
  private[transform] var staticSyms: Map[String, SymId] = Map.empty
  /** the `java.util.stream` collapse — see `staticRewrite`. */
  private[transform] var asScalaBufferSym, filteredSym: SymId = SymId.None
  /** scala's own `toBuffer` — how a `Kind.Set` or `Kind.Map` stream SOURCE reaches the `Buffer`
    * every collapsed operation is declared over. See `streamSource`. */
  private[transform] var toBufferSym: SymId = SymId.None
  /** `mutable.Buffer`, so a collapsed stream can be TYPED as what it now emits. */
  private[transform] var bufferSym: SymId = SymId.None
  /** scala's own `sum` — a plain MEMBER name on the collapsed buffer, not a `JavaCollections` helper. */
  private[transform] var sumSym: SymId = SymId.None
  /** scala's own `map` — a plain member on a collapsed buffer, for the stream chain. */
  private[transform] var mapSym: SymId = SymId.None
  /** scala's own `exists`/`forall` — java's `anyMatch`/`allMatch`, which mean exactly these. */
  private[transform] var existsSym, forallSym: SymId = SymId.None
  /** `JavaIterator.from` — the `iterator` counterpart of `wrapIterableArgs`. */
  private[transform] var iteratorFromSym, javaIteratorSym: SymId = SymId.None
  /** `JavaListIterator` and its write-through cursor `JavaListIterator.over` — the `listIterator`
    * rewrite's target (`ENGINE-LIMITS.md` K23). `SymId.None` unless the program names
    * `java.util.ListIterator`, so the arm declines by arithmetic everywhere else. */
  private[transform] var javaListIteratorSym, listIteratorOverSym: SymId = SymId.None
  /** `JavaCollections.{spliterator, orderedSpliterator, distinctSpliterator}` — java's THREE own
    * defaults for `spliterator()`, one per owner it re-declares the member at
    * (`ENGINE-LIMITS.md` K23). Three symbols and not one, because the emitted call has to NAME which
    * java declaration it reproduces rather than carry a characteristics constant. */
  private[transform] var orderedSpliteratorSym, distinctSpliteratorSym: SymId = SymId.None
  /** `JavaCollections.fromJava` / `toJava` — the EXTERNAL seam's two directions. */
  private[transform] var fromJavaSym, toJavaSym, toStreamSym: SymId = SymId.None

  /** java's three `Object`-keyed map members, and the two `Object`-keyed collection ones, for a
    * receiver or an ARGUMENT at which the element type cannot be named — see [[objectProbe]]. */
  private[transform] var mapGetSym, mapContainsKeySym, mapRemoveSym: SymId = SymId.None
  private[transform] var setContainsSym, setRemoveSym: SymId               = SymId.None

  /** `JavaCollections.entryToPair` — the projection [[detachedEntries]] licenses. */
  private[transform] var entryToPairSym: SymId = SymId.None

  private[transform] var stringTpe: TypeRepr        = TypeRepr.NoType

  /** `java.lang.Object` as THIS run's symbol — the top of java's reference hierarchy, and the one
    * type an argument can carry that conforms to no scala element type at all ([[objectProbe]]). */
  private[transform] var objectSym: SymId = SymId.None

  /** is this symbol one the PROGRAM declares? Structural (`Program.owned`), never a name test
    * (§4.56), and computed once per run because the external-seam arms ask it per call. */
  private[transform] var ownedSym: SymId => Boolean = _ => true

  /** every symbol THIS PHASE minted in [[run]] — the rewrites' own targets. They are owned by
    * nothing and named by no class file, so the external-seam arms would otherwise read each of
    * them as a third party's method. */
  private[transform] var mintedSyms: Set[SymId] = Set.empty

  /** every symbol this run's [[scope]] held OUT of the rewrite. EMPTY for the default scope — and
    * for any scope whose entries matched nothing — which is what makes the no-op a no-op. */
  private[transform] var excluded: Set[SymId] = Set.empty

  /** …read back, so [[CollectionBoundaryCheck]] can classify a seam the scope created from the
    * phase's OWN record of what it held back rather than guessing from a type name (§4.56). */
  def scopedOut: Set[SymId] = excluded

  /** every member this run held back because it OVERRIDES A CLASS FILE — see
    * [[classFileOverridesIn]]. EMPTY for a program that extends no unconverted java type, which is
    * what makes this a no-op by arithmetic exactly as an unrestricted scope is. */
  private[transform] var retainedOverrides: Set[SymId] = Set.empty

  /** …plus the PARAMETER symbols of those members, because a tree that says `java.util.Collection`
    * over a symbol that says `JavaCollection` is the lie [[mapSignatures]] already refuses to
    * write for the scope. Held apart from [[classFileOverrides]] so the boundary check keys on
    * MEMBERS and nothing else. */
  private[transform] var retainedOwners: Set[SymId] = Set.empty

  /** …read back, for [[scopedOut]]'s reason and with a DIFFERENT classification: the scope's seam
    * names a manifest key, and this one names nothing a port can edit (§4.56). */
  def classFileOverrides: Set[SymId] = retainedOverrides

  /** the union — every declaration whose type this run reads LITERALLY, whichever refusal held it.
    * `isEmpty` is the pre-refusal code path by arithmetic, which both halves need. */
  private[transform] def literal(s: SymId): Boolean = excluded(s) || retainedOverrides(s) || retainedOwners(s)
  private[transform] def literalEmpty: Boolean      = excluded.isEmpty && retainedOverrides.isEmpty

  /** the declaration → the scope ENTRY that admitted it, for `Reason.Configured`'s key (§4.575:
    * the key is the manifest entry VERBATIM, because it is the string an agent edits). */
  private[transform] var admittedBy: Map[SymId, String] = Map.empty
  override def run(program: Program): Program =
    // the class index [[classTparams]] reads — built once, from the program as PARSED, because a
    // type parameter's symbol is one thing this phase never moves.
    classDefsBySym = program.units.flatMap(StandardTraversal.allClassDefs(_)(using program))
                            .map(cd => cd.symbol -> cd).toMap
    val added = collection.mutable.ListBuffer[Symbol]()
    var next  = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    def mint(name: String, full: String): SymId =
      val id = SymId(next); next += 1
      // inherit isFinal from the frontend's interned symbol when it read the class file (K18)
      val flags = program.symbols.all.find(_.fullName == full)
        .map(s => Flags(isFinal = s.flags.isFinal)).getOrElse(Flags())
      added += Symbol(id, name, full, flags, SymId.None, TypeRepr.NoType)
      id
    // one scala symbol per DISTINCT scala type (so two java types mapping to the same
    // scala type — e.g. Deque & ArrayDeque → ArrayDeque — share it and its kind).
    val byScala = collection.mutable.Map[String, SymId]()
    remap = program.symbols.all.flatMap { s =>
      // …RETARGET first, so a key the port also finds in `typeMap` cannot silently take the
      // collection answer: `effectiveRetarget` has already removed any such key and reported it.
      effectiveRetarget.get(s.fullName).map { sc =>
        // a source with its own rewrite table/type args gets a DISTINCT SymId, so multiple
        // sources sharing a target do not collapse onto one entry (§4.55: a loose key -> List).
        val needsOwnSym = retargetTypeArgs.contains(s.fullName) ||
          retargetRewrites.contains(s.fullName) || retargetRewritesByDesc.contains(s.fullName)
        val sym = if needsOwnSym then
          mint(sc.substring(sc.lastIndexOf('.') + 1), sc)
        else
          byScala.getOrElseUpdate(sc, mint(sc.substring(sc.lastIndexOf('.') + 1), sc))
        s.id -> sym
      }.orElse(typeMap.get(s.fullName).map(_._1).map { sc =>
        s.id -> byScala.getOrElseUpdate(sc, mint(sc.substring(sc.lastIndexOf('.') + 1), sc))
      })
    }.toMap
    // fullName -> minted SymId fallback for `transformType`: a dependent port may intern the
    // same java type under a SECOND SymId not in `remap`, so fall back to the source's fullName.
    remapByFullName = program.symbols.all.flatMap { s =>
      remap.get(s.id).map(tgt => s.fullName -> tgt)
    }.toMap
    // …and the reverse map for per-entry family scoping (D12): which remap entries came from
    // `families` (not the JDK companion typeMap, not retarget). Used by `finishRun` to narrow
    // the mapping per pass, so a dependent's family entries only retype its own declarations.
    familyRemapSources = program.symbols.all.flatMap { s =>
      if remap.contains(s.id) && families.contains(s.fullName) then Some(s.id -> s.fullName)
      else scala.None
    }.toMap
    kindOf = program.symbols.all.flatMap { s =>
      typeMap.get(s.fullName).map { case (sc, k) => byScala(sc) -> k }
    }.toMap
    opPlusEq     = mint("+=", "scala.<op>#+=")   // rendered infix by the emitter
    opMinusEq    = mint("-=", "scala.<op>#-=")
    opPlusPlusEq = mint("++=", "scala.<op>#++=")
    // Plain arithmetic operators for compound-FieldWrite expansion (`size -= 1` -> `setSize(size - 1)`).
    compoundOps  = Map("-" -> mint("-", "scala.<op>#-"), "+" -> mint("+", "scala.<op>#+"),
                       "*" -> mint("*", "scala.<op>#*"), "/" -> mint("/", "scala.<op>#/"),
                       "%" -> mint("%", "scala.<op>#%"), "|" -> mint("|", "scala.<op>#|"),
                       "&" -> mint("&", "scala.<op>#&"), "^" -> mint("^", "scala.<op>#^"),
                       "<<" -> mint("<<", "scala.<op>#<<"), ">>" -> mint(">>", "scala.<op>#>>"),
                       ">>>" -> mint(">>>", "scala.<op>#>>>"))
    updateSym    = mint("update", "update")
    insertSym    = mint("insert", "insert")
    getOrElseSym = mint("getOrElse", "getOrElse")
    containsSym  = mint("contains", "contains")
    key1Sym      = mint("_1", "_1") // Map.Entry#getKey   on a Tuple2
    value2Sym    = mint("_2", "_2") // Map.Entry#getValue on a Tuple2
    // the receiver parameter of a LOWERED unbound method reference — see [[lowerMethodRef]]. ONE
    // symbol serves every site, and that is a fact about the shape rather than a shortcut: the
    // lowered body is a single member access on the parameter, so it can contain no second lowered
    // reference and two of these lambdas can never nest. The name matches what `TirEmitter` already
    // spells for a method reference it expands itself, so the two paths read alike.
    selfParamSym = mint("self$", "self$")
    recvBindSym  = mint("recv$", "recv$")
    argParamSyms = (0 until 4).toVector.map(k => mint(s"a$k$$", s"a$k$$"))
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
    existsSym           = mint("exists", "exists")
    forallSym           = mint("forall", "forall")
    toBufferSym         = mint("toBuffer", "toBuffer")
    staticSyms = CollectionsTransform.StaticHelpers
      .map(n => n -> mint(n, s"$JavaCollectionsFqn.$n")).toMap
    // one `from` per DISTINCT scala target, so `new ArrayList<>(c)` copies through the companion the
    // target type actually has. `Tuple2` is excluded: it is a `Kind.Entry`, not a collection, and
    // `Tuple2.from` does not exist — the `kindOf` gate in `copyConstructor` never offers it one.
    fromSyms = byScala.collect {
      // …and the RUNTIME targets that publish a `from` of their own. Listed rather than matched on
      // the package, because "is this one of mine" is a membership test against the phase's own
      // record and a prefix is not a structural fact about anything (§4.56). `JavaEnumMap` is the
      // only one: `JavaStack`'s java type has no copy constructor and `JavaEnumSet`'s copy is a
      // STATIC, so neither is ever reached through a `new`.
      case (fqn, id) if fqn.startsWith("scala.collection.") || fqn == CollectionsTransform.JavaEnumMapFqn =>
        id -> mint("from", s"$fqn.from")
    }.toMap
    // The scala collections whose only paramful constructor is `(initialCapacity, loadFactor)`.
    // Listed rather than derived because there is nothing in the TIR to derive it FROM — these are
    // external types with no declaration the frontend ever saw — but the list is closed over the
    // phase's own `typeMap` targets, so it is the phase's record and not a name test (§4.56).
    loadFactorSyms = List(
      "scala.collection.mutable.HashMap", "scala.collection.mutable.LinkedHashMap",
      "scala.collection.mutable.HashSet", "scala.collection.mutable.LinkedHashSet",
    ).flatMap(fqn => byScala.get(fqn).map(_ -> mint("defaultLoadFactor", s"$fqn.defaultLoadFactor"))).toMap
    iteratorFromSym = mint("from", JavaIteratorFqn + ".from")
    javaIteratorSym = byScala.getOrElse(JavaIteratorFqn, SymId.None)
    javaListIteratorSym = byScala.getOrElse(CollectionsTransform.JavaListIteratorFqn, SymId.None)
    listIteratorOverSym = mint("over", CollectionsTransform.JavaListIteratorFqn + ".over")
    orderedSpliteratorSym  = mint("orderedSpliterator", JavaCollectionsFqn + ".orderedSpliterator")
    distinctSpliteratorSym = mint("distinctSpliterator", JavaCollectionsFqn + ".distinctSpliterator")
    // …the five targets a LIVE view exists for, as this run's own symbols. Keyed on `byScala`, so a
    // target the program never names is simply absent and the wrap declines by arithmetic.
    liveWrappableSyms = byScala.collect {
      case (fqn, id) if CollectionsTransform.liveWrappable(fqn) => id
    }.toSet
    // …and the REIFIED pair per target the program names. Two symbols each, minted rather than
    // resolved: nothing in a java program declares `JavaCollections.Reified`.
    reifiedIsSyms = byScala.collect {
      case (fqn, id) if CollectionsTransform.reifiedHelper.contains(fqn) =>
        val n = "is" + CollectionsTransform.reifiedHelper(fqn)
        id -> mint(n, s"${CollectionsTransform.ReifiedFqn}.$n")
    }.toMap
    reifiedAsSyms = byScala.collect {
      case (fqn, id) if CollectionsTransform.reifiedHelper.contains(fqn) =>
        val n = "as" + CollectionsTransform.reifiedHelper(fqn)
        id -> mint(n, s"${CollectionsTransform.ReifiedFqn}.$n")
    }.toMap
    // …and the targets a reified occurrence can name that this phase did NOT retype, as symbols of
    // THIS program — see [[unmappedReified]]. Read off `program.symbols` and not off the mapping,
    // because these are types the phase leaves alone: their symbols keep the ids they arrived with.
    unmappedSupertypeSyms = program.symbols.all.collect {
      case s if CollectionsTransform.unmappedSupertypes(s.fullName) => s.id
    }.toSet
    // …and the REIFIED CARRIERS this program actually names (K20). Resolved BEFORE the traversal
    // starts, because `preservesTypeArgsOf` is asked from inside it.
    carrierSyms = program.symbols.all.collect {
      case s if effectiveCarriers(s.fullName) => s.id
    }.toSet
    // …and the REFLECTIVE SINKS (K21), read the same way and for the same reason: a sink is a type
    // this phase leaves alone, so its symbol keeps the id it arrived with.
    sinkSyms = program.symbols.all.collect {
      case s if reflectiveSinks(s.fullName) => s.id
    }.toSet
    toJavaValueSym = mint("toJavaValue", s"${CollectionsTransform.ReifiedFqn}.toJavaValue")
    foreachSym          = mint("foreach", "foreach")
    forEachSeq          = 0
    collectSeq          = 0
    // 64 entries — never wraps; libGDX core uses ~30 forEach rewrites across the whole port.
    // An assertion in retargetForEach guards the upper bound rather than silently shadowing.
    forEachKeyPool      = (0 until 64).map(i => mint(s"k$$fe$i", s"k$$fe$i")).toArray
    forEachValPool      = (0 until 64).map(i => mint(s"v$$fe$i", s"v$$fe$i")).toArray
    forEachElemPool     = (0 until 64).map(i => mint(s"x$$fe$i", s"x$$fe$i")).toArray
    removeHeadOptionSym = mint("removeHeadOption", "removeHeadOption")
    headOptionSym       = mint("headOption", "headOption")
    orNullSym           = mint("orNull", "orNull")
    prependSym          = mint("prepend", "prepend")
    isEmptySym          = mint("isEmpty", "isEmpty")
    getSym              = mint("get", "get")
    isDefinedSym        = mint("isDefined", "isDefined")
    someSym             = mint("Some", "scala.Some")
    noneSym             = mint("None", "scala.None")
    byScalaSyms         = byScala.toMap
    // retarget rewrite wiring: build reverse map from target SymId to source FQN, and mint
    // symbols for each rewrite target member name.
    // retarget reverse map: target SymId -> source FQN. Built from `remap` so that per-source
    // minted symbols (arity-changing retargets) are included — `byScala` does not hold those.
    retargetTargetToSource = program.symbols.all.flatMap { s =>
      effectiveRetarget.get(s.fullName).flatMap(_ => remap.get(s.id).map(tgtSym => tgtSym -> s.fullName))
    }.toMap
    retargetEntryTargets = program.symbols.all.flatMap { s =>
      effectiveRetarget.get(s.fullName).filter(CollectionsTransform.UninheritableTargets.contains)
        .flatMap(_ => remap.get(s.id))
    }.toSet

    // IndexedField: collect the SOURCE member SymIds for field names in IndexedField entries.
    // The bottom-up traversal visits the inner Select BEFORE the ArrayAccess, so by the time
    // retargetIndexedField fires, the Select's member symbol may have been remapped. We match on
    // the ORIGINAL source member SymId (the field declared by the source type), keyed to its
    // source FQN so we can look up the rewrite table.
    // Scans BOTH retargetRewrites and retargetIndexedFields (the latter avoids a key collision
    // when a field and a method of the same name need different rewrite kinds).
    val rwIdxFields = retargetRewrites.flatMap { (srcFqn, tbl) =>
      tbl.collect { case ((fieldName, 0), idx: CollectionsTransform.RetargetRewrite.IndexedField) =>
        program.symbols.all.filter(s => s.fullName == srcFqn).flatMap { ownerSym =>
          program.symbols.all.filter(m => m.owner == ownerSym.id && m.name == fieldName)
            .map(m => m.id -> (srcFqn, idx))
        }
      }.flatten
    }
    val separateIdxFields = retargetIndexedFields.flatMap { (srcFqn, tbl) =>
      tbl.flatMap { (fieldName, idx) =>
        program.symbols.all.filter(s => s.fullName == srcFqn).flatMap { ownerSym =>
          program.symbols.all.filter(m => m.owner == ownerSym.id && m.name == fieldName)
            .map(m => m.id -> (srcFqn, idx))
        }
      }
    }
    indexedFieldSyms = rwIdxFields ++ separateIdxFields

    // resolve FixedType and Applied FQNs — reuse an EXISTING symbol where one is already in
    // byScala or in the program, so no FQN ends up with two SymIds. 3.1ai / O9: minting a
    // duplicate `scala.Int` gives `SymbolTable` two entries with the same `fullName`, and any
    // phase resolving a primitive by `fullName` may bind the wrong one.
    // 3.1aw-3: Applied entries contribute their OWN FQN (the type constructor) to the same pool.
    def collectFqns(arg: CollectionsTransform.RetargetArg): Set[String] = arg match
      case CollectionsTransform.RetargetArg.FixedType(fqn) => Set(fqn)
      case CollectionsTransform.RetargetArg.Applied(fqn, inner) =>
        Set(fqn) ++ inner.flatMap(collectFqns)
      case _ => Set.empty
    retargetFixedTypeSyms = retargetTypeArgs.values.flatten.flatMap(collectFqns).toSet.map { fqn =>
      val sym = byScala.getOrElseUpdate(fqn, {
        // check program symbols before minting — the frontend may already have this FQN
        program.symbols.all.find(_.fullName == fqn).map(_.id)
          .getOrElse(mint(fqn.substring(fqn.lastIndexOf('.') + 1), fqn))
      })
      fqn -> sym
    }.toMap

    // build per-source and per-target arg mappings
    retargetArgsBySource = program.symbols.all.flatMap { s =>
      retargetTypeArgs.get(s.fullName).map(args => s.id -> args)
    }.toMap
    retargetArgsByTarget = retargetArgsBySource.flatMap { (srcId, args) =>
      remap.get(srcId).map(tgtId => tgtId -> args)
    }
    retargetRewriteSyms = retargetRewrites.flatMap { (src, tbl) =>
      tbl.values.flatMap {
        case CollectionsTransform.RetargetRewrite.Rename(target) =>
          List((src, target) -> mint(target, s"$src#retargetRewrite:$target"))
        case CollectionsTransform.RetargetRewrite.BoolDispatch(_, onTrue, onFalse) =>
          List(
            (src, onTrue)  -> mint(onTrue, s"$src#retargetRewrite:$onTrue"),
            (src, onFalse) -> mint(onFalse, s"$src#retargetRewrite:$onFalse"))
        case CollectionsTransform.RetargetRewrite.Construct(companionFqn, factoryMethod, _, _, _) =>
          val fqn = s"$companionFqn.$factoryMethod"
          List((src, fqn) -> mint(factoryMethod, fqn))
        case CollectionsTransform.RetargetRewrite.ForEach(targetMethod, _) =>
          List((src, targetMethod) -> mint(targetMethod, s"$src#retargetRewrite:$targetMethod"))
        case CollectionsTransform.RetargetRewrite.Collect(via, _) =>
          List((src, via) -> mint(via, s"$src#retargetRewrite:$via"))
        case CollectionsTransform.RetargetRewrite.Chain(members, _, _) =>
          members.map(m => (src, m) -> mint(m, s"$src#retargetRewrite:$m"))
        case CollectionsTransform.RetargetRewrite.FieldWrite(_, method) =>
          List((src, method) -> mint(method, s"$src#retargetRewrite:$method"))
        case CollectionsTransform.RetargetRewrite.DropWrite(_, readTarget, _) =>
          List((src, readTarget) -> mint(readTarget, s"$src#retargetRewrite:$readTarget"))
        case CollectionsTransform.RetargetRewrite.IndexedField(_, v, vw) =>
          // always mint symbols for via/viaWrite — the handler resolves them by name from
          // retargetRewriteSyms; a default-via IndexedField on a source with no Rename("apply")
          // would otherwise fall through to updateSym.
          List((src, v) -> mint(v, s"$src#retargetRewrite:$v"),
               (src, vw) -> mint(vw, s"$src#retargetRewrite:$vw"))
        case _: CollectionsTransform.RetargetRewrite.Template =>
          Nil // no minted symbol needed — the template is rendered as Opaque text
      }
    } ++ retargetIndexedFields.flatMap { (src, tbl) =>
      tbl.values.flatMap { idx =>
        List((src, idx.via) -> mint(idx.via, s"$src#retargetRewrite:${idx.via}"),
             (src, idx.viaWrite) -> mint(idx.viaWrite, s"$src#retargetRewrite:${idx.viaWrite}"))
      }
    } ++ retargetRewritesByDesc.flatMap { (src, tbl) =>
      tbl.values.flatMap {
        case CollectionsTransform.RetargetRewrite.Construct(companionFqn, factoryMethod, _, _, _) =>
          val fqn = s"$companionFqn.$factoryMethod"
          List((src, fqn) -> mint(factoryMethod, fqn))
        case CollectionsTransform.RetargetRewrite.Rename(target) =>
          List((src, target) -> mint(target, s"$src#retargetRewrite:$target"))
        case CollectionsTransform.RetargetRewrite.BoolDispatch(_, onTrue, onFalse) =>
          List(
            (src, onTrue)  -> mint(onTrue, s"$src#retargetRewrite:$onTrue"),
            (src, onFalse) -> mint(onFalse, s"$src#retargetRewrite:$onFalse"))
        case CollectionsTransform.RetargetRewrite.ForEach(targetMethod, _) =>
          List((src, targetMethod) -> mint(targetMethod, s"$src#retargetRewrite:$targetMethod"))
        case CollectionsTransform.RetargetRewrite.Collect(via, _) =>
          List((src, via) -> mint(via, s"$src#retargetRewrite:$via"))
        case CollectionsTransform.RetargetRewrite.Chain(members, _, _) =>
          members.map(m => (src, m) -> mint(m, s"$src#retargetRewrite:$m"))
        case CollectionsTransform.RetargetRewrite.FieldWrite(_, method) =>
          List((src, method) -> mint(method, s"$src#retargetRewrite:$method"))
        case CollectionsTransform.RetargetRewrite.DropWrite(_, readTarget, _) =>
          List((src, readTarget) -> mint(readTarget, s"$src#retargetRewrite:$readTarget"))
        case _: CollectionsTransform.RetargetRewrite.IndexedField => Nil
        case _: CollectionsTransform.RetargetRewrite.Template => Nil
      }
    }
    enumMapOfTypeSym    = mint("ofType", s"${CollectionsTransform.JavaEnumMapFqn}.ofType")
    enumSetSyms = List("noneOf", "allOf", "of", "copyOf", "range", "complementOf")
      .map(n => n -> mint(n, s"${CollectionsTransform.JavaEnumSetFqn}.$n")).toMap
    putSym       = mint("put", "put")     // scala `mutable.Map.put`: returns the PREVIOUS value
    removeSym    = mint("remove", "remove") // scala `mutable.Map.remove`: returns the REMOVED value
    entryToPairSym = staticSyms.getOrElse("entryToPair", SymId.None)
    fromJavaSym  = staticSyms.getOrElse("fromJava", SymId.None)
    toJavaSym    = staticSyms.getOrElse("toJava", SymId.None)
    toStreamSym  = staticSyms.getOrElse("toStream", SymId.None)
    mapGetSym         = staticSyms.getOrElse("mapGet", SymId.None)
    mapContainsKeySym = staticSyms.getOrElse("mapContainsKey", SymId.None)
    mapRemoveSym      = staticSyms.getOrElse("mapRemove", SymId.None)
    setContainsSym    = staticSyms.getOrElse("setContains", SymId.None)
    setRemoveSym      = staticSyms.getOrElse("setRemove", SymId.None)
    // …the refusal a RETAINED PARENT's own contract prescribes (`UnsupportedOnTarget`). Resolved
    // from the program where it already holds the symbol and minted only where it does not: two
    // symbols for one FQN print the same text and compare unequal, which is how a later reader ends
    // up asking about a type this run has twice.
    def named(fqn: String, nm: String): SymId =
      // 3.1ai / O9: check `byScala` too — a FixedType resolution may have already minted a symbol
      // for this FQN (e.g. `scala.Int`), and a second mint gives `SymbolTable` two entries with the
      // same `fullName`. Minting duplicates is the root cause of textra's 58 Align opaque errors.
      byScala.get(fqn).orElse(program.symbols.all.find(_.fullName == fqn).map(_.id))
        .getOrElse(mint(nm, fqn))
    // …the BRIDGED members' own vocabulary (K28.1). `named` for every type, minted for the two
    // `asScala` views and for scala's `iterator`, which nothing in a java program declares.
    optionSym          = named("scala.Option", "Option")
    scalaIteratorSym   = named("scala.collection.Iterator", "Iterator")
    scalaIterableSym   = named("scala.collection.Iterable", "Iterable")
    iterableOnceSym    = named("scala.collection.IterableOnce", "IterableOnce")
    tuple2Sym          = named("scala.Tuple2", "Tuple2")
    boolSym            = named("scala.Boolean", "Boolean")
    intSym             = named("scala.Int", "Int")
    unitSym            = named("scala.Unit", "Unit")
    unitTpe            = TypeRepr.TypeRef(TypeRepr.NoPrefix, unitSym)
    asScalaIteratorSym = mint("asScala", JavaIteratorFqn + ".asScala")
    asScalaIterableSym = mint("asScala", JavaIterableFqn + ".asScala")
    iteratorMemberSym  = mint("iterator", "iterator")
    unsupportedOpSym = named(CollectionsTransform.UnsupportedOperationFqn, "UnsupportedOperationException")
    unsupportedOpTpe = TypeRepr.TypeRef(TypeRepr.NoPrefix, unsupportedOpSym)
    stringTpe        = TypeRepr.TypeRef(TypeRepr.NoPrefix, named("java.lang.String", "String"))
    objectSym        = named("java.lang.Object", "Object")
    externalSeams.clear()
    implicitPending.clear()
    bridgedSinkCallees.clear()

    mintedSyms = added.map(_.id).toSet
    val symbols = SymbolTable(program.symbols.all ++ added)
    given Program = program.rebuilt(symbols = symbols)
    // …resolved once. The external-seam arms ask it per CALL, and `Program.owned` walks an owner
    // chain, so asking it inside the traversal would be quadratic on a library of any size.
    val ownedNow = summon[Program].owned
    ownedSym = ownedNow
    applyScope(summon[Program]) // fills `excluded`, `admittedBy` and `report` — a no-op by default
    applyClassFileOverrides(summon[Program]) // …and the refusal no policy asked for
    uninheritableSyms = program.symbols.all.collect {
      case s if typeMap.get(s.fullName).exists((tgt, _) => CollectionsTransform.UninheritableTargets(tgt)) => s.id
    }.toSet
    // …and the half of that refusal that is not one. Read off the ORIGINAL units, before
    // `restoreUninheritableParents` retains a parent and before this phase substitutes any body:
    // the licence is the LIBRARY's own refusal to write, and a throw this phase wrote is not it.
    detachedEntries = detachedEntriesIn(summon[Program])
    parentClash = declaredParentKinds(summon[Program])
    superDefaults.clear()
    // …and the SURFACE the minted parent declares (K28.1). Planned here, where the java members
    // still carry java's names, and APPLIED as a rename before anything else reads the table: every
    // later step — the traversal, `mapSignatures`, `strippedOverrides` — must see the name the
    // emitted member will actually have, or two of them disagree about one declaration.
    bridges = planBridges(summon[Program])
    finishRun(program, renameBridgeDelegates(summon[Program]))

  /** the rest of [[run]], over the symbol table the bridge renames produced — a separate method
    * because a second `given Program` in one scope after the rename would be an ambiguity, not a
    * shadow. */
  private[transform] def finishRun(program: Program, symbols: SymbolTable): Program =
    given Program = program.rebuilt(symbols = symbols)
    ownedSym = summon[Program].owned

    // per-entry family scoping (D12): a family scoped non-Everywhere must only retype
    // declarations within that scope. One pass for JDK + everywhere-scoped families, then one
    // pass per distinct non-everywhere family scope with a narrowed remap.
    val fullRemap = remap
    remapTargets = fullRemap.values.toSet

    // built before the traversal retypes any tree: for every owned symbol whose result-type head
    // is a retarget source, record symbol -> source FQN, so a call-site rewrite can pick the right
    // table when several sources share a target (e.g. FloatArray/IntArray/Array -> DynamicArray).
    retargetDeclOrigin =
      if effectiveRetarget.isEmpty then Map.empty
      else
        val p = summon[Program]
        val buf = collection.mutable.Map[SymId, String]()
        p.symbols.all.foreach { s =>
          if p.owns(s.id) then
            infoResultHead(s.info).foreach { headId =>
              p.symbolOf(headId).foreach { hs =>
                if effectiveRetarget.contains(hs.fullName) then
                  buf(s.id) = hs.fullName
              }
            }
        }
        buf.toMap

    val scopedFamilyIds: Set[SymId] = familyRemapSources.collect {
      case (srcId, fqn) if !familyScopeOf(fqn).isUnrestricted => srcId
    }.toSet

    // pass 1: JDK entries + everywhere-scoped families (unrestricted, this is the full remap).
    remap = if scopedFamilyIds.isEmpty then fullRemap
            else fullRemap.filterNot { (k, _) => scopedFamilyIds(k) }
    var units: List[Tree.ClassDef] = program.units.map(u =>
      dropSubsumedParents(
        restoreUninheritableParents(u, restoreExcluded(u, StandardTraversal.mapClassDef(this, u)))))

    // pass 2+: one pass per distinct non-everywhere family scope, narrowing remap to that
    // scope's entries and its units only — composes because each pass rewrites its own symbols.
    if scopedFamilyIds.nonEmpty then
      val scopedGroups = scopedFamilyIds.toList.groupBy(id => familyScopeOf(familyRemapSources(id)))
      val scopedP = summon[Program] // the Program the scope is asked against
      scopedGroups.toList.sortBy(_._1.fingerprint).foreach { (sc, srcIds) =>
        remap = fullRemap.view.filterKeys(srcIds.toSet).toMap
        units = units.map { u =>
          if inFamilyScope(sc, scopedP, u.symbol) then
            dropSubsumedParents(
              restoreUninheritableParents(u, StandardTraversal.mapClassDef(this, u)))
          else u
        }
      }
    remap = fullRemap // restore for signature processing, recordings, and checks

    // collect post-pass: standalone keys()/values() calls the main pass left for retargetForEach's
    // for-each consumption; whatever remains here is a standalone call.
    if retargetRewrites.values.exists(_.values.exists(_.isInstanceOf[CollectionsTransform.RetargetRewrite.Collect])) ||
        retargetRewritesByDesc.values.exists(_.values.exists(_.isInstanceOf[CollectionsTransform.RetargetRewrite.Collect])) then
      collectPassActive = true
      units = units.map(u => StandardTraversal.mapClassDef(collectPhase, u))
      collectPassActive = false

    // Signature pass — also multi-pass when family scopes exist.
    val symbols2 =
      if scopedFamilyIds.isEmpty then mapSignatures(symbols) // unchanged code path
      else
        // Pass 1: JDK + everywhere families
        remap = fullRemap.filterNot { (k, _) => scopedFamilyIds(k) }
        var tbl = mapSignatures(symbols)
        // Pass 2+: scoped families, only on in-scope symbols
        val scopedGroups = scopedFamilyIds.toList.groupBy(id => familyScopeOf(familyRemapSources(id)))
        val scopedP = summon[Program]
        scopedGroups.toList.sortBy(_._1.fingerprint).foreach { (sc, srcIds) =>
          remap = fullRemap.view.filterKeys(srcIds.toSet).toMap
          tbl = tbl.all.foldLeft(tbl) { (t, s) =>
            if literal(s.id) || !scopedP.owns(s.id) || !inFamilyScope(sc, scopedP, s.id) then t
            else t.updated(s.copy(info = StandardTraversal.mapType(this, s.info)))
          }
        }
        remap = fullRemap
        tbl
    // the modifier the re-parenting invalidated (K28); applied here rather than inside
    // mapSignatures, whose contract is about types, not flags.
    val stripped = strippedOverrides(symbols2)
    val symbols3 =
      if stripped.isEmpty then symbols2
      else symbols2.all.foldLeft(symbols2) { (t, s) =>
        if stripped(s.id) then t.updated(s.copy(flags = s.flags.copy(isOverride = false))) else t
      }
    recordRetypings(symbols, symbols3)
    recordScopedOut(symbols)
    recordRetainedSignatures(symbols)
    recordStrippedOverrides(stripped, symbols2)
    recordSuperDefaults
    recordReifiedTypeArgs(symbols3)
    recordEgressBridges()
    // the minted parent's members are added last: already scala-shaped, so strippedOverrides
    // must not see them before they exist.
    val (units2, synthesised) = synthesiseBridges(units, symbols3)
    program.rebuilt(units2, SymbolTable(symbols3.all ++ synthesised))

  override def transformType(t: TypeRepr)(using Program): TypeRepr = t match
    case TypeRepr.TypeRef(prefix, s) if remap.contains(s) =>
      val newSym = remap(s)
      retargetArgsBySource.get(s) match
        case Some(mapping) if allFixed(mapping) =>
          // arity-changing retarget with a zero-param source (IntIntMap -> ObjectMap[Int,Int]):
          // every target arg is fixed, so this TypeRef never appears as an AppliedType's tycon.
          // The only case where transformType may return an AppliedType for a bare TypeRef.
          val args = mapping.map(resolveRetargetArg(_, Nil))
          TypeRepr.AppliedType(TypeRepr.TypeRef(prefix, newSym), args)
        case _ =>
          // same-arity retarget, or arity-changing with SourceArg entries: the frontend already
          // filled the type params, so this TypeRef is an AppliedType's tycon; the arm below rearranges args.
          TypeRepr.TypeRef(prefix, newSym)
    case TypeRepr.AppliedType(TypeRepr.TypeRef(prefix, s), existingArgs) if retargetArgsByTarget.contains(s) =>
      // Arity-changing retarget with existing type args: rearrange the args according to the
      // mapping. By the time this runs, `s` is already the TARGET sym (mapType recurses into the
      // tycon first and transformType swaps it above), and the args have already been recursed into.
      val mapping = retargetArgsByTarget(s)
      val newArgs = mapping.map(resolveRetargetArg(_, existingArgs))
      TypeRepr.AppliedType(TypeRepr.TypeRef(prefix, s), newArgs)
    case TypeRepr.AppliedType(tc @ TypeRepr.TypeRef(_, s), args) if remapTargets.contains(s) && args.exists(_.isInstanceOf[TypeRepr.TypeBounds]) =>
      // Same-arity retarget target is invariant: a wildcard arg is invalid, strip to the LOWER
      // bound when present. Upper-only is left alone HERE — a DECLARATION (a parameter, a field)
      // may keep `?`, which is valid Scala and the right image of java's own covariant wildcard;
      // stripping the upper bound is licensed only at a CAST TARGET, see [[stripCastWildcard]].
      // An UNBOUNDED wildcard (`?`) from a raw java type is bounded by Object: java's raw erasure
      // is Object (JLS 4.8), so `T[?]` must read as `T[? <: Object]` — without this, `apply`
      // returns `Any` and does not conform to `Object` slots. G2, CLAUDE.md §1(b).
      val objectRef = TypeRepr.TypeRef(TypeRepr.NoPrefix, objectSym)
      val stripped = args.map {
        case TypeRepr.TypeBounds(lo, _) if lo != TypeRepr.NoType => lo
        // only at a type THIS PHASE retargeted: a runtime shim declares `[?]` itself, and an override
        // of its member must keep that spelling or clash after erasure (simplegraphs 0 -> 4)
        case TypeRepr.TypeBounds(TypeRepr.NoType, TypeRepr.NoType) if retargetTargetToSource.contains(s) =>
          TypeRepr.TypeBounds(TypeRepr.NoType, objectRef)
        case a => a
      }
      if stripped == args then t else TypeRepr.AppliedType(tc, stripped)
    // FQN fallback for un-remapped SymIds: a dependent port may hold a second SymId for a
    // retarget source, interned from the base's resolution root and never seen by remap — every
    // SymId for one source FQN must map to the same target, or the port names both halves.
    case TypeRepr.TypeRef(prefix, s) if !remap.contains(s) && s != SymId.None =>
      summon[Program].symbolOf(s) match
        case Some(sym) =>
          remapByFullName.get(sym.fullName) match
            case Some(newSym) =>
              // same logic as the primary remap path, looked up by the fullName-matched original SymId.
              retargetArgsBySource.get(s).orElse(
                remap.collectFirst { case (srcId, `newSym`) if retargetArgsBySource.contains(srcId) =>
                  retargetArgsBySource(srcId) }
              ) match
                case Some(mapping) if allFixed(mapping) =>
                  val args = mapping.map(resolveRetargetArg(_, Nil))
                  TypeRepr.AppliedType(TypeRepr.TypeRef(prefix, newSym), args)
                case _ =>
                  TypeRepr.TypeRef(prefix, newSym)
            case None => t
        case None => t
    case other => other

  /** A raw-type occurrence's substituted bound still carries its OWN unresolved wildcards (a
    * self-bounded generic's erasure); stripping an upper bound to such a bound mis-narrows an
    * invariant slot, so upper-bound stripping only fires where this is `false` (subplan item 3). */
  private[transform] def hasNestedBound(t: TypeRepr): Boolean = t match
    case _: TypeRepr.TypeBounds       => true
    case TypeRepr.AppliedType(tc, as) => hasNestedBound(tc) || as.exists(hasNestedBound)
    case _                            => false

  /** `asInstanceOf[T]` needs a REIFIABLE `T`; a DECLARATION may keep `?` (valid Scala, the right
    * image of java's wildcard) but a cast target may not, so the upper-bound strip [[hasNestedBound]]
    * guards belongs here and not in [[transformType]] (subplan item 3). */
  private[transform] def stripCastWildcard(t: Tree.Typed): Tree.Typed =
    def strip(tp: TypeRepr): TypeRepr = tp match
      case TypeRepr.AppliedType(tc @ TypeRepr.TypeRef(_, s), args) if retargetTargetToSource.contains(s) =>
        TypeRepr.AppliedType(tc, args.map {
          case TypeRepr.TypeBounds(_, hi) if hi != TypeRepr.NoType && !hasNestedBound(hi) => hi
          case a => strip(a)
        })
      case TypeRepr.AppliedType(tc, args) => TypeRepr.AppliedType(strip(tc), args.map(strip))
      case other                          => other
    val stripped = strip(t.tpe)
    if stripped == t.tpe then t else t.copy(tpt = TypeTree(stripped, t.tpt.origin), tpe = stripped)

  /** WHICH type constructors' arguments this run must not move — the carriers, resolved to this
    * program's own symbols. `false` by arithmetic where the port declares none and the program names
    * no `java.lang.Class`, which is the §1(b) no-op with no code path. */
  override def preservesTypeArgsOf(tc: TypeRepr)(using Program): Boolean =
    carrierSyms.nonEmpty && headSym(tc).exists(carrierSyms.contains)

  /** does this type mention a java type THIS PHASE maps? The question `mentionsRetyped` asks in the
    * other direction — that one reads the types the phase PRODUCED, this one the keys it consumes —
    * and both are §4.56's "conclude only from what the phase itself did". */
  private[transform] def mentionsMapped(t: TypeRepr)(using Program): Boolean =
    var hit = false
    val scan = new Phase:
      def name = "mapped-mention-scan"
      override def transformType(x: TypeRepr)(using Program): TypeRepr =
        x match
          case TypeRepr.TypeRef(_, s) if remap.contains(s) => hit = true
          case _                                           => ()
        x
    StandardTraversal.mapType(scan, t)
    hit

  /** A `val`'s declared type is an expected type exactly as a formal parameter is, so it routes to
    * the same [[coerce]] every other position does. */
  override def transformValDef(t: Tree.ValDef)(using Program): Tree.ValDef =
    citeIfReified(t.symbol)
    transformValDefRhs(t)

  private[transform] def transformValDefRhs(t: Tree.ValDef)(using Program): Tree.ValDef = t.rhs match
    // a DECLARED slot is an expected type exactly as a formal parameter is — see `coerce`.
    case Some(rhs) => t.copy(rhs = Some(coerce(t.tpt.tpe, rhs, literal(t.symbol))))
    case _ => t

  /** An assignment's LHS declares the expected type as a `val`'s `tpt` does; a cast this phase made
    * IMPOSSIBLE (both sides sent to unrelated families) is dropped rather than emitted, turning a
    * runtime `ClassCastException` into a compile error. // CLAUDE.md §4.4, ENGINE-LIMITS M6 */
  override def transformTerm(t: Term)(using Program): Term = t match
    case a: Tree.Assign =>
      // FieldWrite: `recv.field = value` -> `recv.method(value)` on a retarget target.
      // Checked BEFORE the coercion path, because the field is NOT writable on the target
      // and the assignment would be a compile error.
      retargetFieldWrite(a).orElse(retargetIndexedFieldWrite(a)) match
        case Some(rewritten) => rewritten
        case scala.None =>
          // the TARGET may itself be a reference to a scoped-out declaration, in which case the slot
          // is a JDK one however the node reads — the same `actualOf` the argument side takes.
          val (want, wantScoped) = actualOf(a.lhs)
          a.copy(rhs = coerce(want, a.rhs, wantScoped))
    case ty: Tree.Typed if impossibleShimCast(ty) => ty.expr
    case ty: Tree.Typed   => reifiedCast(stripCastWildcard(ty))
    case io: Tree.InstanceOf => wildcardReifiedTest(io) match
      case io2: Tree.InstanceOf => reifiedTest(io2)
      case provablyFalse        => provablyFalse
    case fe: Tree.ForEach =>
      retargetForEach(fe).getOrElse {
        val wt = writeThroughEntries(fe)
        ensureUnitForEachBody(wt)
      }
    case mr: Tree.MethodRef => lowerMethodRef(mr)
    case lit @ Tree.Literal(Constant.ClassOfC(tp), tpe, _) => retargetClassOf(lit, tp, tpe)
    case sel: Tree.Select => retargetSelectRewrite(sel).getOrElse(staticFieldRewrite(sel).getOrElse(externalFieldProducer(sel)))
    case aa: Tree.ArrayAccess => retargetIndexedField(aa).getOrElse(aa)
    case id: Tree.IncDec => retargetIncDec(id).getOrElse(id)
    case other          => other

  /** Entry copy-construction fold: `val e2 = Tuple2(default, default); e2._1 = X; e2._2 = Y`
    * folds into `val e2 = Tuple2(X, Y)` — java's `Entry` has mutable public fields, `Tuple2` has
    * `val _1/_2`, so the copy-construct pattern is faithfully rewritten to construct with the
    * right values from the start. Guards: type head is `retargetEntryTargets`, assigns target
    * `_1`/`_2` contiguously right after the ValDef, variable not reassigned elsewhere. */
  override def transformBlock(b: Tree.Block)(using p: Program): Term =
    if retargetEntryTargets.isEmpty then b
    else foldEntryCopyConstruction(b)

  /** A METHOD REFERENCE at a member this phase rewrites — `Map.Entry::getKey` inside a stream,
    * or `C::new` at a retarget source with a `Construct` entry (CT6 face C, CLAUDE.md §4.56).
    *
    * Lowers the reference into a lambda with the rewritten term as body.
    * Bound references (`expr::m`) bind the receiver ONCE (JLS 15.13.3).
    * Parameters left unannotated (scalac infers from expected function type). */
  private[transform] def lowerMethodRef(mr: Tree.MethodRef)(using p: Program): Term =
    if selfParamSym == SymId.None then return mr
    // the NODE's answer and not the symbol's, which is the same one derivation the emitter reads
    // (`Tree.MethodRef.referent`, F8): an external member is interned with no `Flags`, so
    // `flags.isStatic` reads `false` for every JDK static and this phase would lower one.
    mr.qualifier match
      case Left(tt) if !mr.referent.isInstanceOf[Referent.Static] =>
        kindOf.get(headSym(tt.tpe).getOrElse(SymId.None)) match
          case None    =>
            // CT6: a `C::new` reference at a retarget source with a Construct entry —
            // emit the factory lambda through `retargetConstruct` (one derivation). §4.56
            retargetConstructRef(mr, tt) match
              case Some(lam) => lam
              case scala.None => mr
          case Some(k) =>
            val o    = mr.origin
            val self = Tree.Ident(selfParamSym, tt.tpe, o)
            // the `Apply` the reference stands for, so the rewrite runs against the same shape it
            // was written for. Its result type is the reference's own, which for a method VALUE is
            // the functional interface — unused by every arm that answers here (they read the
            // RECEIVER's kind), and honest about what is known.
            val callT = Tree.Apply(Tree.Select(self, mr.method, TypeRepr.NoType, o), Nil,
                                   mr.method, TypeRepr.NoType, o)
            rewrite(k, self, mr.method, o, callT) match
              case None       => mr
              case Some(body) =>
                val param = Tree.ValDef(selfParamSym, TypeTree(TypeRepr.NoType, o), scala.None, o)
                Tree.Lambda(List(param), body, mr.tpe, o)
      // …and the BOUND form, whose receiver is a TERM. The arity is java's, off the node
      // (`Tree.MethodRef.referent` — `G27`'s field, and the same one the emitter's own expansion
      // reads), never off the symbol: an external member is interned with no `MethodType` and would
      // read as taking no arguments.
      case Right(recv) if recvBindSym != SymId.None =>
        kindOf.get(headSym(recv.tpe).getOrElse(SymId.None)) match
          case None    => mr
          case Some(k) =>
            val arity = mr.referent match
              case Referent.Instance(n) => n
              case Referent.Static(_)   => -1 // a bound reference is never static; decline rather than guess
            if arity < 0 || arity > argParamSyms.size then mr
            else
              val o = mr.origin
              // java evaluated the qualifier ONCE, at reference creation — see the doc above.
              val (self, stats) = recv match
                case _: Tree.This => (recv, Nil)
                case _            =>
                  (Tree.Ident(recvBindSym, recv.tpe, o),
                   List(Tree.ValDef(recvBindSym, TypeTree(recv.tpe, o), Some(recv), o)))
              val ps   = argParamSyms.take(arity).toList
              val args = ps.map(s => Tree.Ident(s, TypeRepr.NoType, o))
              val callT = Tree.Apply(Tree.Select(self, mr.method, TypeRepr.NoType, o), args,
                                     mr.method, TypeRepr.NoType, o)
              rewrite(k, self, mr.method, o, callT) match
                case None       => mr
                case Some(body) =>
                  // UNANNOTATED for the arm above's reason: a reference is a poly expression and the
                  // target types its parameters, which is the job javac had.
                  val params = ps.map(s => Tree.ValDef(s, TypeTree(TypeRepr.NoType, o), scala.None, o))
                  val lam    = Tree.Lambda(params, body, mr.tpe, o)
                  if stats.isEmpty then lam else Tree.Block(stats, lam, mr.tpe, o)
      case _ => mr

  /** `C::new` at a retarget source — builds a synthetic `Tree.Apply(Tree.New, args)` and
    * delegates to `retargetConstruct` so the factory-call derivation is ONE path. The result
    * is wrapped in a lambda whose parameters match the constructor's arity. CT6 face C. */
  private[transform] def retargetConstructRef(mr: Tree.MethodRef, tt: TypeTree)(using p: Program): Option[Term] =
    val isCtor = p.symbolOf(mr.method).exists(_.name == "<init>")
    if !isCtor then return scala.None
    val arity = mr.referent match
      case Referent.Instance(n) => n
      case _                    => return scala.None
    if arity > argParamSyms.size then
      retargetSeam("constructor reference arity > argParamSyms pool",
        s"C::new with arity $arity", "Construct not applied — pool has ${argParamSyms.size}",
        mr.origin, SymId.None)
      return scala.None
    val o = mr.origin
    val newNode = Tree.New(tt, tt.tpe, o)
    val ps = argParamSyms.take(arity).toList
    val args = ps.map(s => Tree.Ident(s, TypeRepr.NoType, o))
    val syntheticApply = Tree.Apply(newNode, args, mr.method, tt.tpe, o)
    retargetConstruct(syntheticApply).map { body =>
      val params = ps.map(s => Tree.ValDef(s, TypeTree(TypeRepr.NoType, o), scala.None, o))
      Tree.Lambda(params, body, mr.tpe, o)
    }

  /** replace the head (type-constructor) symbol of a `TypeRef` / `AppliedType`, keeping args. */
  private[transform] def withHead(t: TypeRepr, s: SymId): TypeRepr = t match
    case TypeRepr.TypeRef(prefix, _)    => TypeRepr.TypeRef(prefix, s)
    case TypeRepr.AppliedType(tc, args) => TypeRepr.AppliedType(withHead(tc, s), args)
    case other                          => other

  override def transformApply(t: Tree.Apply)(using Program): Term =
    val t2 = wrapIterableArgs(t)
    val out = tokenConstructor(t2).orElse(copyConstructor(t2)).orElse(capacityConstructor(t2))
      .orElse(retargetConstruct(t2))
      .orElse(staticRewrite(t2)).getOrElse {
      // when retargetSelectRewrite's Chain/Template handler rewrote a Select that is the fun of
      // a 0-arg Apply, the outer Apply still wraps it in () for a parenless target — strip it
      // (checked by identity in selectChainRewritten) and carry the CALL's type, not the fun's.
      if t2.args.isEmpty && selectChainRewritten.remove(t2.fun) then
        t2.tpe match
          case TypeRepr.NoType | _: TypeRepr.MethodType => t2.fun
          case vt => t2.fun match
            case b: Tree.Block  => b.copy(tpe = vt)
            case a: Tree.Apply  => a.copy(tpe = vt)
            case s: Tree.Select => s.copy(tpe = vt)
            case other => other
      else t2.fun match
        case Tree.Select(recv, m, _, so) => kindAt(recv).orElse(inheritedKind(recv, m)) match
          case Some(k) => rewrite(k, recv, m, so, t2).getOrElse(t2)
          // neither answered: java resolved a class member, possibly beside a phase-given scala
          // parent — try a retarget rewrite before pinnedByObject
          case None    => retargetRewrite(recv, m, so, t2).orElse(pinnedByObject(recv, m, t2)).getOrElse(t2)
        // TypeApply(Select(recv, m), targs): the bottom-up traversal visits the inner Select
        // before the outer TypeApply, so a generic call arrives wrapped this way
        case Tree.TypeApply(Tree.Select(recv, m, _, so), _, _, _) =>
          kindAt(recv).orElse(inheritedKind(recv, m)) match
            case Some(k) => rewrite(k, recv, m, so, t2).getOrElse(t2)
            case None    => retargetRewrite(recv, m, so, t2).orElse(pinnedByObject(recv, m, t2)).getOrElse(t2)
        // retargetSelectRewrite may have replaced the fun Select with an Apply/Opaque wrap
        // (JavaIterator.from, K36 removing iterator); collapse the outer Nil-arg Apply
        case inner: Tree.Apply if t2.args.isEmpty => inner
        case inner: Tree.Opaque if t2.args.isEmpty => inner
        case _ => t2
    }
    // seam arms see only what nothing else rewrote — ordering them before the rewrites would
    // report an already-retargeted call as an unverifiable external argument
    val res =
      if out ne t2 then out
      else
        // bridge the consumer half where a live view exists, before the count runs
        val bridged = bridgeSinkArgs(bridgeJavaFormals(t2))
        externalArgs(bridged)
        externalProducer(bridged)
    res match
      case a: Tree.Apply => noteImplicitReceiver(a); a
      case other         => other

  // ---- Inherited collection call with no receiver (anonymous class double-brace idiom) ----
  // // ENGINE-LIMITS K5

  /** Call sites recorded by [[transformApply]], awaiting an enclosing class that can supply
    * `this`. Keyed by ORIGIN, not node identity — `StandardTraversal.mapTerm` rebuilds every
    * node, so no identity survives. Cleared per translation in [[run]]. */
  private[transform] val implicitPending = collection.mutable.Set[Origin]()
  /** Selects rewritten by the Chain/Template handler in `retargetSelectRewrite`, tracked by
    * identity of the RESULT term, so the Apply handler can strip an outer `()` a chain-rewritten
    * parenless target should not have. */
  private[transform] val selectChainRewritten = java.util.Collections.newSetFromMap(
    new java.util.IdentityHashMap[Term, java.lang.Boolean]())

  /** [[inheritedKind]] with no receiver to read. The scope suppression `inheritedKind` applies is
    * about the RECEIVER's declaration, and there is no receiver here; the enclosing class's own
    * [[kindAt]] — which does go through `actualOf` — is what stands in for it at the claim. */
  private[transform] def implicitInheritedKind(m: SymId)(using p: Program): Option[Kind] =
    if mintedSyms.contains(m) then scala.None
    else p.symbolOf(m).flatMap(s => p.symbolOf(s.owner)).flatMap(o => typeMap.get(o.fullName)).map(_._2)

  private[transform] def noteImplicitReceiver(t: Tree.Apply)(using Program): Unit = t.fun match
    case Tree.Ident(m, _, _) if t.origin != Origin.synthetic && implicitInheritedKind(m).isDefined =>
      implicitPending += t.origin
    case _ => ()

  override def transformNew(t: Tree.New)(using Program): Term = t.anon match
    case Some(a) if implicitPending.nonEmpty =>
      // the anonymous class's own kind decides whether it can SUPPLY the receiver; the claimer
      // drains what it finds either way, so the drop happens at the innermost anonymous class.
      val supplies = kindAt(t)
      val claimer = new Phase:
        def name: String = "collections/implicit-receiver"
        override def transformApply(x: Tree.Apply)(using Program): Term =
          if !implicitPending.remove(x.origin) then x
          else if supplies.isEmpty then x
          else x.fun match
            case Tree.Ident(m, _, so) =>
              implicitInheritedKind(m)
                .flatMap(k => rewrite(k, Tree.This(a.symbol, t.tpe, x.origin), m, so, x))
                .getOrElse(x)
            case _ => x
      t.copy(anon = Some(a.copy(body = a.body.map(StandardTraversal.mapStat(claimer, _)))))
    case _ => t

  // ---- External callee seam — boundary at compiled class files, both directions ----
  // // ENGINE-LIMITS K15, M6

  /** Does `needle` occur anywhere inside `hay`, as a whole type? Structural equality via
    * [[StandardTraversal.mapType]] (§3). */
  private[transform] def occursIn(needle: TypeRepr, hay: TypeRepr)(using Program): Boolean =
    if hay == needle then true
    else
      var hit = false
      val scan = new Phase:
        def name = "passthrough-scan"
        override def transformType(x: TypeRepr)(using Program): TypeRepr =
          if x == needle then hit = true
          x
      StandardTraversal.mapType(scan, hay)
      hit

  /** Does this callee name a member one of the phase's own static arms covers (§4.56, `MemberKey`
    * form)? A call still standing at such a name is one the phase DECLINED to rewrite — its value
    * is whatever java's was, whatever the node's retyped `tpe` now says. */
  private[transform] def handledStatic(m: SymId)(using p: Program): Boolean =
    memberKeyOf(m).exists(CollectionsTransform.handledStatics.contains)

  /** A `return` is a shim-typed slot exactly as a formal or `val` is — the declared return type
    * is the expected type of every `return` in the body. DELIBERATELY BOUNDED: a `return` inside a
    * lambda/anon/local class returns from THAT, so an unhandled kind MISSES a coercion (a loud
    * compile error) rather than wrongly coercing. Tail expression is not a return value here —
    * every java method exits through `Tree.Return`. */
  override def transformDefDef(t: Tree.DefDef)(using Program): Tree.DefDef =
    citeIfReified(t.symbol)
    val coerced = t.copy(rhs = t.rhs.map(coerceReturns(t.returnTpt.tpe, _)))
    if retFeReturnApplies.isEmpty then coerced
    else coerced.copy(rhs = coerced.rhs.map(wrapReturnBoundary(t.returnTpt.tpe, _)))


object CollectionsTransform:

  /** Zip original and mapped body by position. Throws if lengths differ (splice integrity). */
  private[transform] def spine(orig: List[Statement], mapped: List[Statement], of: SymId)
      : List[(Statement, Statement)] =
    if orig.sizeIs != mapped.size then
      throw IllegalStateException(
        s"CollectionsTransform.restoreExcluded: the declaration spine of ${of.raw} changed length " +
          s"under the traversal (${orig.size} member(s) before, ${mapped.size} after). Held-back " +
          "members are spliced back BY POSITION, which is only sound while the mapped body is the " +
          "same kinds in the same order; zipping two different lengths would silently drop the tail " +
          "of the restore and emit the mapped form for members the scope excluded.")
    orig.zip(mapped)

  /** The declared type of a scoped-out declaration's symbol, or `None` if not scoped out.
    * Reads `Symbol.info` instead of the node's `tpe` (which `transformType` already remapped). */
  def scopedType(t: Term, scopedOut: SymId => Boolean)(using program: Program): Option[TypeRepr] =
    def declared(s: SymId, isCall: Boolean) =
      program.symbolOf(s).map(_.info).map {
        case TypeRepr.MethodType(_, r, _) if isCall                       => r
        case TypeRepr.PolyType(_, TypeRepr.MethodType(_, r, _)) if isCall => r
        case other                                                        => other
      }.filter(_ != TypeRepr.NoType)
    t match
      case Tree.Ident(s, _, _) if scopedOut(s)       => declared(s, isCall = false)
      case Tree.Select(_, s, _, _) if scopedOut(s)   => declared(s, isCall = false)
      case Tree.Apply(_, _, m, _, _) if scopedOut(m) => declared(m, isCall = true)
      case _                                         => scala.None

  /** A call-site rewrite for a retarget entry's member. `retarget` swaps the type;
    * `retargetRewrites` rewrites call sites on that retarget's target, keyed on the retarget's
    * source FQN, each entry mapping `(memberName, arity)` to one of these. */
  sealed trait RetargetRewrite
  object RetargetRewrite:
    /** Simple rename: `recv.old(args)` -> `recv.new(args)`. */
    case class Rename(target: String) extends RetargetRewrite
    /** Boolean-dispatched: inspect `args(flagIndex)` — literal `true` calls `onTrue(remaining)`,
      * literal `false` calls `onFalse(remaining)`, non-literal is refused and counted. */
    case class BoolDispatch(flagIndex: Int, onTrue: String, onFalse: String) extends RetargetRewrite
    /** Construction rewrite: `new Source(args)` to `companionFqn.factoryMethod(args)`.
      * `dropTrailing` strips trailing args; `fillTypeArgs` generates null placeholders for 0-arg case. */
    case class Construct(companionFqn: String, factoryMethod: String, dropTrailing: Int = 0, fillTypeArgs: Boolean = false,
        /** a `given` clause (`Type = expr`, `$T0` = the element type), in scope when the element is a type variable */
        typeVarEvidence: Option[String] = None) extends RetargetRewrite

    /** For-each structural rewrite: `for (E e : recv.sourceMethod())` over a retarget target
      * lowers to `recv.targetMethod(e => body)` (or a 2-arg lambda for entry iteration). `break`/
      * `continue` become `boundary` around the call/lambda body; `return` has no faithful image
      * (no explicit iterator) and is refused and counted on `collection-retarget`, as is any
      * usage of `sourceMethod()` outside a for-each header. `arity` is the lambda parameter count. */
    case class ForEach(targetMethod: String, arity: Int) extends RetargetRewrite

    /** Standalone collection: `recv.sourceMethod()` outside a for-each header collects eagerly
      * into a `DynamicArray` via `into`'s `apply()`, walked by `via` (the forEach method name);
      * inside a for-each header it lowers like `ForEach(via, 1)`. Deltas (eager copy vs java's
      * live view; no `ConcurrentModificationException`) are counted on `collection-retarget`. */
    case class Collect(via: String, into: String) extends RetargetRewrite

    /** Member chain: `recv.sourceMethod(args)` -> `recv.m1.m2…`. First member takes the original
      * arguments; later members take none. Parenless by default (F9's arity-from-callee rule);
      * `parens` opts a member into `()`. `dropArgs` drops the source call's arguments entirely
      * (parenless first member) for a source parameter the target does not need. */
    case class Chain(members: List[String], parens: Set[String] = Set.empty, dropArgs: Boolean = false) extends RetargetRewrite

    /** Field write rewrite: `recv.field = value` -> `recv.method(value)`, for a java public field
      * whose target exposes only a getter method. Keyed at `(field, 0)`, the same slot a
      * `Rename`/`Chain` occupies for the read side — the two fire on different node kinds
      * (`Assign` vs `Select`/`Apply`) and coexist at one key. */
    case class FieldWrite(field: String, method: String) extends RetargetRewrite

    /** Dropped field write: `recv.field = value` is elided (the target's field is immutable or the
      * write is a no-op), and `recv.field` on the read side maps to `readTarget`. A
      * `Decision.Kind.DroppedFieldWrite` is recorded at each dropped site. K36. */
    case class DropWrite(field: String, readTarget: String, why: String) extends RetargetRewrite

    /** Indexed field bypass: `recv.field[i]` -> `recv.via(i)`, `field[i] = v` -> `recv.viaWrite(i, v)`.
      * `via`/`viaWrite` default to `apply`/`update`; non-default enters the fingerprint.
      * Fires in `retargetSelectRewrite` by stripping the field select.
      * // CLAUDE.md §1(b) */
    case class IndexedField(field: String, via: String = "apply", viaWrite: String = "update") extends RetargetRewrite

    /** Expression template with placeholders (`$recv`, `$0`/`$1`… for arguments, `$T0`… for the
      * receiver's type arguments as text, `$Target` for the retarget target's FQN as text),
      * rendered as `Tree.Opaque.spliced`. A term placeholder used more than once is bound to a
      * temporary to avoid double side effects (CLAUDE.md §4.4/F7); type placeholders may repeat freely. */
    case class Template(expr: String) extends RetargetRewrite

  /** How to construct a retarget target's type arguments from the source type's — one element
    * per target type parameter. E.g. `IntMap<V>` (1 param) -> `ObjectMap[K,V]` (2 params) needs
    * `List(FixedType("scala.Int"), SourceArg(0))`. */
  sealed trait RetargetArg
  object RetargetArg:
    /** Carry the source type's i-th argument to this position. */
    case class SourceArg(index: Int) extends RetargetArg
    /** Insert a fixed type at this position, resolved to a minted symbol at run time. */
    case class FixedType(fqn: String) extends RetargetArg
    /** An applied type at this position (e.g. `Tuple2[K,V]` for a nested Entry type in the
      * source), whose own type arguments are each a `RetargetArg`. */
    case class Applied(fqn: String, args: List[RetargetArg]) extends RetargetArg

  /** the shape of a collection, which decides the call rewrite (a `Seq` `get` is `apply`,
    * a `Map` `get` is `getOrElse`). */
  enum Kind:
    case Seq, Map, Set
    /** a `java.util.Map.Entry`, mapped to `Tuple2` — `getKey`/`getValue` are `_1`/`_2`. */
    case Entry
    /** a `java.util.Stack`: a [[Seq]] plus five LIFO members, one of which (`peek`) is the
      * opposite end from `Deque.peek` — java's `Stack.peek()` throws on empty, `Deque.peek()`
      * returns null, so one arm cannot answer both. Falls back to [[Seq]] once the five decline. */
    case Stack
    /** a `java.util.Optional{Int,Long,Double}`, mapped to an `Option[…]` alias — not a collection,
      * but its member names differ the same way (`getAsInt`/`isPresent`/`orElse`). */
    case Opt

  val JavaIteratorFqn = s"${RuntimeArtifact.Package}.JavaIterator"
  val JavaListIteratorFqn = s"${RuntimeArtifact.Package}.JavaListIterator"
  val JavaIterableFqn = s"${RuntimeArtifact.Package}.JavaIterable"
  val JavaCollectionFqn = s"${RuntimeArtifact.Package}.JavaCollection"
  /** `java.util.Stack`'s target — a `mutable.ArrayBuffer` carrying java's own LIFO five. See the
    * [[typeMap]] entry for why the stdlib type is the wrong answer and why this is not a rewrite. */
  val JavaStackFqn = s"${RuntimeArtifact.Package}.JavaStack"
  /** `java.util.EnumMap`/`EnumSet`'s targets — a `mutable.Map`/`Set` that iterates in ORDINAL
    * order, which is the GUARANTEE no stdlib type carries (catalog `JS-C42`). */
  val JavaEnumMapFqn = s"${RuntimeArtifact.Package}.JavaEnumMap"
  val JavaEnumSetFqn = s"${RuntimeArtifact.Package}.JavaEnumSet"
  /** `java.util.Optional{Int,Long,Double}`'s targets — type ALIASES for `Option[…]`, because the
    * retype is arity-changing and the head swap is not. See the alias's own doc. */
  val JavaOptionalIntFqn    = s"${RuntimeArtifact.Package}.JavaOptionalInt"
  val JavaOptionalLongFqn   = s"${RuntimeArtifact.Package}.JavaOptionalLong"
  val JavaOptionalDoubleFqn = s"${RuntimeArtifact.Package}.JavaOptionalDouble"
  /** `java.util.Collections`' statics — a receiver-less utility class, which is why they need their
    * own home rather than a rewrite keyed on a receiver's collection kind. */
  val JavaCollectionsFqn = s"${RuntimeArtifact.Package}.JavaCollections"

  /** Targets with no `scala.collection.*` parent (standalone shims per CLAUDE.md §4.5).
    * Keyed by FQN, not package — three runtime targets DO extend scala collections. */
  val standaloneTargets: Set[String] =
    Set(JavaIterableFqn, JavaCollectionFqn, JavaIteratorFqn, JavaListIteratorFqn)

  /** `java.lang.Object` — the one formal at which every value conforms and conformance proves nothing. */
  private[balticporter] val ObjectFqn = "java.lang.Object"

  /** `java.util.stream.Stream` — formal for un-terminated collapsed stream chains. */
  private[balticporter] val StreamFqn = "java.util.stream.Stream"

  /** Java FQN to (scala FQN, Kind). In companion so checks can read it without a phase instance.
    * Must stay BELOW the `*Fqn` vals (declaration-order initialisation). */
  /** Shim FQNs. Must stay BELOW the `*Fqn` vals. */
  private[balticporter] val ShimFqns: Set[String] =
    Set(JavaIterableFqn, JavaIteratorFqn, JavaListIteratorFqn, JavaCollectionFqn)

  private[balticporter] val typeMap: Map[String, (String, Kind)] = Map(
    "java.util.List"          -> ("scala.collection.mutable.Buffer", Kind.Seq),
    "java.util.ArrayList"     -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq),
    // LinkedList: List AND Deque, used as queue. Queue extends ArrayDeque extends Buffer.
    "java.util.LinkedList"    -> ("scala.collection.mutable.Queue", Kind.Seq),
    // Vector: legacy, absent from Scala.js. Does NOT preserve `synchronized`.
    "java.util.Vector"        -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq),
    // Stack: own shim (NOT mutable.Stack — different ordering semantics, CLAUDE.md §4.4).
    "java.util.Stack"         -> (JavaStackFqn, Kind.Stack),
    // Queue/Deque/ArrayDeque: all to ArrayDeque (java/scala order these types oppositely).
    "java.util.Queue"         -> ("scala.collection.mutable.ArrayDeque", Kind.Seq),
    "java.util.Deque"         -> ("scala.collection.mutable.ArrayDeque", Kind.Seq),
    "java.util.ArrayDeque"    -> ("scala.collection.mutable.ArrayDeque", Kind.Seq),
    // Collection/AbstractCollection: both to shim (must preserve subtype relation; CLAUDE.md §4.5).
    "java.util.Collection"         -> (JavaCollectionFqn, Kind.Seq),
    "java.util.AbstractCollection" -> (JavaCollectionFqn, Kind.Seq),
    // Iterable: shim (java's iterator has remove; scala's does not).
    "java.lang.Iterable"      -> (JavaIterableFqn, Kind.Seq),
    // Iterator: shim (java has `remove`, scala does not).
    "java.util.Iterator"      -> (JavaIteratorFqn, Kind.Seq),
    // ListIterator: shim, preserves `ListIterator extends Iterator` edge. // ENGINE-LIMITS K23
    "java.util.ListIterator"  -> (JavaListIteratorFqn, Kind.Seq),
    "java.util.Map"           -> ("scala.collection.mutable.Map", Kind.Map),
    "java.util.HashMap"       -> ("scala.collection.mutable.HashMap", Kind.Map),
    "java.util.LinkedHashMap" -> ("scala.collection.mutable.LinkedHashMap", Kind.Map),
    "java.util.TreeMap"       -> ("scala.collection.mutable.TreeMap", Kind.Map),
    // ConcurrentHashMap: TrieMap (preserves subtype relation and thread-safety).
    "java.util.concurrent.ConcurrentHashMap" -> ("scala.collection.concurrent.TrieMap", Kind.Map),
    "java.util.concurrent.ConcurrentMap"     -> ("scala.collection.concurrent.Map", Kind.Map),
    // Map.Entry -> Tuple2. Both `$` and `.` separators covered.
    "java.util.Map$Entry"     -> ("scala.Tuple2", Kind.Entry),
    "java.util.Map.Entry"     -> ("scala.Tuple2", Kind.Entry),
    // EnumMap/EnumSet: shims (guarantee ordinal-order iteration, absent from JS/Native).
    "java.util.EnumMap"       -> (JavaEnumMapFqn, Kind.Map),
    "java.util.EnumSet"       -> (JavaEnumSetFqn, Kind.Set),
    // Primitive optionals: type aliases for `Option[…]` (arity-changing retype).
    "java.util.OptionalInt"    -> (JavaOptionalIntFqn, Kind.Opt),
    "java.util.OptionalLong"   -> (JavaOptionalLongFqn, Kind.Opt),
    "java.util.OptionalDouble" -> (JavaOptionalDoubleFqn, Kind.Opt),
    "java.util.Set"           -> ("scala.collection.mutable.Set", Kind.Set),
    // AbstractSet: preserves `AbstractSet <: Set`; JDK defaults supplied by the phase. // ENGINE-LIMITS K29
    "java.util.AbstractSet"   -> ("scala.collection.mutable.Set", Kind.Set),
    "java.util.HashSet"       -> ("scala.collection.mutable.HashSet", Kind.Set),
    "java.util.LinkedHashSet" -> ("scala.collection.mutable.LinkedHashSet", Kind.Set),
    "java.util.TreeSet"       -> ("scala.collection.mutable.TreeSet", Kind.Set),
  )

  /** Stable digest of [[typeMap]] for [[surfaceFingerprint]]. Sorted by java FQN, includes kind. */
  private[transform] def mappingDigest: String =
    balticporter.tir.TirPrinter.sha256(
      typeMap.toList.map((k, v) => s"$k->${v._1}:${v._2}").sorted.mkString(",")).take(16)

  /** True if `fromJava`/`toJava` can express a live view for this target. `JavaCollection`
    * has no live wrapper (copy would detach both directions). // ENGINE-LIMITS M6 */
  private[transform] def liveWrappable(target: String): Boolean = Set(
    "scala.collection.mutable.Buffer", "scala.collection.mutable.Set", "scala.collection.mutable.Map",
    JavaIteratorFqn, JavaIterableFqn,
  ).contains(target)

  /** Targets where a reified occurrence (instanceof/cast) can be translated via `Reified`.
    * Concrete targets absent (no live view can be one); those are refused and counted.
    * // ENGINE-LIMITS K18 */
  private[transform] val reifiedHelper: Map[String, String] = Map(
    "scala.collection.mutable.Buffer" -> "Buffer",
    "scala.collection.mutable.Set"    -> "Set",
    "scala.collection.mutable.Map"    -> "Map",
    JavaCollectionFqn                 -> "Collection",
    JavaIterableFqn                   -> "Iterable",
    JavaIteratorFqn                   -> "Iterator",
  )

  /** `JavaCollections.Reified`, whose members [[reifiedHelper]] names. */
  val ReifiedFqn = s"$JavaCollectionsFqn.Reified"

  /** JDK supertypes a retyped value stops being (derived from typeMap keys' class hierarchy).
    * Excludes `java.lang.Object`. // ENGINE-LIMITS K18 */
  private[transform] lazy val unmappedSupertypes: Set[String] =
    def closure(c: Class[?]): Set[String] =
      if c == null then Set.empty
      else Set(c.getName) ++ closure(c.getSuperclass) ++ c.getInterfaces.flatMap(closure).toSet
    val all = typeMap.keys.flatMap { k =>
      try closure(Class.forName(k)) catch { case _: Throwable => Set.empty[String] }
    }.toSet
    all -- typeMap.keySet - "java.lang.Object"

  /** every `JavaCollections` member the transform may emit. One list, so a new JDK utility is one
    * line here, one arm in `staticRewrite` and one method in the runtime object — and a typo is a
    * `SymId.None` that declines the rewrite rather than a dangling name in emitted code. */
  /** the three members java declares over `Object`, so a `Map<?, ?>` receiver supports them and
    * scala's `Map[K, V]` does not — see `wildcardMapCall`. */
  private[balticporter] val WildcardMapMembers: Set[String] = Set("get", "containsKey", "remove")

  /** Mapping TARGETS that cannot be a PARENT, however right they are as a use.
    *
    * `scala.Tuple2` is the one: final, no `setValue`, and a constructor taking the two components.
    * A class that IMPLEMENTS `java.util.Map.Entry` therefore cannot be emitted at that target at
    * all — see `restoreUninheritableParents`, which keeps java's parent and counts the refusal. */
  private[balticporter] val UninheritableTargets: Set[String] = Set("scala.Tuple2")

  /** `java.lang.Class` — the one reified carrier java itself guarantees. // ENGINE-LIMITS K20 */
  private[balticporter] val UniversalCarriers: Set[String] = Set("java.lang.Class")

  /** Members a retained parent declares that its mapping target cannot carry.
    * `setValue` on `Map.Entry` is the only entry. */
  private[balticporter] val UnsupportedOnTarget: Map[String, Set[MemberSig]] =
    Map("scala.Tuple2" -> Set(MemberSig("setValue", 1)))

  /** One interface member by (name, arity) — arity only, since the declaring interface is external. */
  private[balticporter] final case class MemberSig(name: String, arity: Int)

  /** Members a minted parent declares at its type parameter that shadow java's `Object`-keyed
    * members. Keyed by Kind. Used by [[pinnedByObject]] to resolve ambiguous overloads. */
  private[balticporter] val ShadowedByTarget: Map[String, Set[MemberSig]] = Map(
    // `mutable.Map`: `get`/`remove`/`contains`/`apply` all take `K`.
    Kind.Map.toString   -> Set(MemberSig("get", 1), MemberSig("remove", 1),
                               MemberSig("contains", 1), MemberSig("apply", 1)),
    // `mutable.Set`: `remove`/`contains`/`apply` all take `A`.
    Kind.Set.toString   -> Set(MemberSig("remove", 1), MemberSig("contains", 1),
                               MemberSig("apply", 1)),
    // `mutable.Buffer`: `contains`/`indexOf`/`lastIndexOf` take `A`. `remove` is deliberately NOT
    // here — scala's is `remove(Int)`, which java's `remove(Object)` does not clash with at a
    // reference argument, and where the element IS an `Integer` the phase already answers through
    // `removeValue` (§4.4's own row).
    Kind.Seq.toString   -> Set(MemberSig("contains", 1), MemberSig("indexOf", 1),
                               MemberSig("lastIndexOf", 1)),
    // a `JavaStack` IS a `mutable.ArrayBuffer`, so it inherits exactly the Seq row's three.
    Kind.Stack.toString -> Set(MemberSig("contains", 1), MemberSig("indexOf", 1),
                               MemberSig("lastIndexOf", 1)),
  )

  // ---- WHAT A MINTED PARENT ACTUALLY OVERRIDES ----
  // // ENGINE-LIMITS K28
  // Table, not derivation (far side is an unparsed scala trait). Both error directions loud
  // except abstract parent strips (silent, read against error rows via members.tsv).

  /** Members a java member at this Kind's target really overrides. Keyed by Kind. */
  private[balticporter] val OverridesTarget: Map[String, Set[ExternalSurface.Member]] = Map(
    // mutable.Map
    Kind.Map.toString -> Set(
      ExternalSurface.Member("size", 0), ExternalSurface.Member("isEmpty", 0),
      ExternalSurface.Member("clear", 0), ExternalSurface.Member("keySet", 0),
      ExternalSurface.Member("keys", 0), ExternalSurface.Member("values", 0),
      ExternalSurface.Member("iterator", 0), ExternalSurface.Member("put", 2),
    ),
    // mutable.Set
    Kind.Set.toString -> Set(
      ExternalSurface.Member("size", 0), ExternalSurface.Member("isEmpty", 0),
      ExternalSurface.Member("clear", 0), ExternalSurface.Member("add", 1),
      ExternalSurface.Member("iterator", 0),
    ),
    // mutable.Buffer — `remove` needs descriptor to distinguish by-index from by-value
    Kind.Seq.toString -> Set(
      ExternalSurface.Member("size", 0), ExternalSurface.Member("isEmpty", 0),
      ExternalSurface.Member("clear", 0), ExternalSurface.Member("iterator", 0),
      ExternalSurface.Member("remove", 1, Some(Descriptor(List(Param.Prim("int"))))),
    ),
    // a `JavaStack` IS a `mutable.ArrayBuffer`, so it inherits exactly the Seq row — the same
    // sentence `ShadowedByTarget` carries for the same pair.
    Kind.Stack.toString -> Set(
      ExternalSurface.Member("size", 0), ExternalSurface.Member("isEmpty", 0),
      ExternalSurface.Member("clear", 0), ExternalSurface.Member("iterator", 0),
      ExternalSurface.Member("remove", 1, Some(Descriptor(List(Param.Prim("int"))))),
    ),
  )

  /** Override surface of standalone shim targets (engine's own runtime traits). */
  /** Shims already subsumed by a Kind's target (e.g. `JavaIterable` subsumed by any
    * `scala.collection.Iterable`-derived target). Keyed by Kind. // ENGINE-LIMITS K28.1 */
  private[balticporter] val SubsumesShim: Map[String, Set[String]] = Map(
    // every one of these targets is a `scala.collection.Iterable`, which declares `iterator` — the
    // one member `JavaIterable` has.
    Kind.Map.toString   -> Set(JavaIterableFqn),
    Kind.Set.toString   -> Set(JavaIterableFqn),
    Kind.Seq.toString   -> Set(JavaIterableFqn),
    Kind.Stack.toString -> Set(JavaIterableFqn),
  )

  private[balticporter] val OverridesShim: Map[String, Set[ExternalSurface.Member]] = Map(
    JavaIterableFqn     -> Set(ExternalSurface.Member("iterator", 0)),
    JavaIteratorFqn     -> Set(ExternalSurface.Member("hasNext", 0), ExternalSurface.Member("next", 0),
                               ExternalSurface.Member("remove", 0)),
    JavaListIteratorFqn -> Set(ExternalSurface.Member("hasNext", 0), ExternalSurface.Member("next", 0),
                               ExternalSurface.Member("remove", 0), ExternalSurface.Member("hasPrevious", 0),
                               ExternalSurface.Member("previous", 0), ExternalSurface.Member("nextIndex", 0),
                               ExternalSurface.Member("previousIndex", 0), ExternalSurface.Member("set", 1),
                               ExternalSurface.Member("add", 1)),
    JavaCollectionFqn   -> Set(ExternalSurface.Member("iterator", 0), ExternalSurface.Member("size", 0),
                               ExternalSurface.Member("isEmpty", 0), ExternalSurface.Member("contains", 1),
                               ExternalSurface.Member("add", 1), ExternalSurface.Member("remove", 1),
                               ExternalSurface.Member("clear", 0), ExternalSurface.Member("containsAll", 1),
                               ExternalSurface.Member("addAll", 1), ExternalSurface.Member("removeAll", 1),
                               ExternalSurface.Member("retainAll", 1), ExternalSurface.Member("removeIf", 1),
                               ExternalSurface.Member("toArray", 0), ExternalSurface.Member("toArray", 1)),
  )

  /** the exception java's own contract names for an optional operation a receiver cannot perform. */
  private[balticporter] val UnsupportedOperationFqn = "java.lang.UnsupportedOperationException"

  // ---- Surface the minted parent declares that java's member cannot satisfy ----
  // Rename java member to `<name>$java`, synthesise scala-shaped bridge.
  // Both error directions loud. // ENGINE-LIMITS K28.1

  /** One bridged member: scala name/arity, with `from` = java member preference list to delegate to.
    * `from = Nil` means unconditional (no java counterpart; body from `JavaCollections`). */
  private[balticporter] final case class Bridged(scalaName: String, arity: Int,
                                                 from: List[ExternalSurface.Member],
                                                 required: Boolean = true)

  private val ObjectArg = Some(Descriptor(List(Param.Named("Object"))))
  private val IntArg    = Some(Descriptor(List(Param.Prim("int"))))

  /** keyed by [[Kind]] for [[OverridesTarget]]'s reason: several map kinds share one `MapOps`
    * trait surface. `Kind.Stack`'s target has nothing abstract; `Kind.Entry`/`Kind.Opt` are never
    * a parent ([[UninheritableTargets]]) — all three are a no-op here by arithmetic. */
  private[balticporter] val BridgedTarget: Map[String, List[Bridged]] = Map(
    Kind.Map.toString -> List(
      // MapOps.put is concrete and returns Option[V]; java's returns the value or null.
      Bridged("put",         2, List(ExternalSurface.Member("put", 2)), required = false),
      // MapOps.get is abstract and takes K; java's takes Object on purpose (K24) — a legal overload pair.
      Bridged("get",         1, List(ExternalSurface.Member("get", 1, ObjectArg))),
      // Growable.addOne / Shrinkable.subtractOne ride on the two rows above; no java member is named for them.
      Bridged("addOne",      1, List(ExternalSurface.Member("put", 2))),
      Bridged("subtractOne", 1, List(ExternalSurface.Member("remove", 1, ObjectArg))),
      Bridged("iterator",    0, List(ExternalSurface.Member("iterator", 0),
                                  ExternalSurface.Member("entrySet", 0))),
      // the two MapOps declares concretely; only a same-named java member is a problem.
      Bridged("values",      0, List(ExternalSurface.Member("values", 0)), required = false),
      Bridged("keys",        0, List(ExternalSurface.Member("keys", 0)),   required = false),
    ),
    Kind.Set.toString -> List(
      Bridged("contains",    1, List(ExternalSurface.Member("contains", 1, ObjectArg))),
      Bridged("addOne",      1, List(ExternalSurface.Member("add", 1))),
      Bridged("subtractOne", 1, List(ExternalSurface.Member("remove", 1, ObjectArg))),
      Bridged("iterator",    0, List(ExternalSurface.Member("iterator", 0))),
    ),
    Kind.Seq.toString -> List(
      Bridged("apply",       1, List(ExternalSurface.Member("get", 1, IntArg))),
      Bridged("length",      0, List(ExternalSurface.Member("size", 0))),
      Bridged("update",      2, List(ExternalSurface.Member("set", 2))),
      Bridged("insert",      2, List(ExternalSurface.Member("add", 2))),
      Bridged("prepend",     1, List(ExternalSurface.Member("add", 2))),
      Bridged("addOne",      1, List(ExternalSurface.Member("add", 1))),
      // java declares both remove(int) and remove(Object); the descriptor keeps the by-value overload out.
      Bridged("remove",      1, List(ExternalSurface.Member("remove", 1, IntArg))),
      Bridged("iterator",    0, List(ExternalSurface.Member("iterator", 0))),
      // SeqOps.contains is concrete and generic ([A1 >: A]) — must be carried or it clashes at erasure (E120).
      Bridged("contains",    1, List(ExternalSurface.Member("contains", 1, ObjectArg)), required = false),
      Bridged("indexOf",     1, List(ExternalSurface.Member("indexOf", 1, ObjectArg)),  required = false),
      // the three with no java counterpart at all. See JavaCollections' own note.
      Bridged("remove",       2, Nil),
      Bridged("insertAll",    2, Nil),
      Bridged("patchInPlace", 3, Nil),
    ),
  )

  /** Which delegates the emitted parent would CAPTURE — the set that decides the rename. Where
    * the parent declares the same (name, arity), a bridge body's call binds to the PARENT's own
    * member, making the bridge an infinite recursion; a captured delegate is renamed out of the
    * way. Deliberately absent: every delegate the target does not declare (`entrySet()`, a
    * `Buffer`'s indexed members) since renaming those would move surface for no hazard. */
  private[balticporter] val CapturedByTarget: Map[String, Set[ExternalSurface.Member]] = Map(
    // MapOps declares all six (put/values/keys concretely, get abstractly); iterator from
    // IterableOnce, remove from mutable.MapOps.
    Kind.Map.toString -> Set(
      ExternalSurface.Member("put", 2), ExternalSurface.Member("get", 1, ObjectArg),
      ExternalSurface.Member("remove", 1, ObjectArg), ExternalSurface.Member("iterator", 0),
      ExternalSurface.Member("values", 0), ExternalSurface.Member("keys", 0),
    ),
    // mutable.SetOps declares add/remove concretely over addOne/subtractOne — the recursion this breaks.
    Kind.Set.toString -> Set(
      ExternalSurface.Member("contains", 1, ObjectArg), ExternalSurface.Member("add", 1),
      ExternalSurface.Member("remove", 1, ObjectArg), ExternalSurface.Member("iterator", 0),
    ),
    // SeqOps.size is final — the rename is the only repair there is here. ENGINE-LIMITS K28
    Kind.Seq.toString -> Set(
      ExternalSurface.Member("size", 0), ExternalSurface.Member("remove", 1, IntArg),
      ExternalSurface.Member("iterator", 0), ExternalSurface.Member("contains", 1, ObjectArg),
      ExternalSurface.Member("indexOf", 1, ObjectArg),
    ),
  )

  /** the SUFFIX a captured java member is renamed with. `$java` and not `$1` or a counter: an
    * emitted name keyed on anything wider than the declaration that holds it turns `members.tsv`
    * into churn (`ENGINE-LIMITS.md` M10). */
  private[balticporter] val BridgeSuffix = "$java"

  // the JDK defaults a re-parenting removes — licensed per-member by the JDK body reaching only
  // public virtual members of the receiver (ArrayList.clone/AbstractList.subList read fields
  // instead and are refused via superPlaced). ENGINE-LIMITS K29

  /** member NAME → the [[balticporter.runtime.JavaCollections]] helper that reproduces its
    * `java.util.AbstractCollection` default. */
  private[balticporter] val VirtualJdkDefaults: Map[String, String] = Map(
    "containsAll" -> "containsAll",
    "addAll"      -> "addAll",
    "removeAll"   -> "removeAll",
    "retainAll"   -> "retainAll",
  )

  /** the body each entry above stands for, from the JDK's own source, so the licence is readable
    * at the emitted call. Rendered into the decision (§4.575). Every member named is public and
    * virtual on the receiver, which IS the argument. */
  private[balticporter] val VirtualJdkDefaultBodies: Map[String, String] = Map(
    "containsAll" -> "for (Object e : c) if (!contains(e)) return false; return true;",
    "addAll"      -> "for (E e : c) if (add(e)) modified = true; return modified;",
    "removeAll"   -> "while (it.hasNext()) if (c.contains(it.next())) { it.remove(); … }",
    "retainAll"   -> "while (it.hasNext()) if (!c.contains(it.next())) { it.remove(); … }",
  )

  val StaticHelpers: List[String] =
    List("sort", "sortNatural", "reverse", "shuffle", "swap", "asList", "asListView",
         "addAll", "insertAll", "noneMatch", "removeValue",
         "computeIfAbsent", "removeIf", "removeIfSet", "containsValue", "containsAll",
         "removeAll", "retainAll", "ensureCapacity",
         "comparingByKey", "comparingByValue", "sortedWith", "into", "mapToDouble", "intRange",
         "toArray", "emptyList", "emptyMap", "emptySet", "singletonList", "singleton", "singletonMap",
         "unmodifiableList", "unmodifiableSet", "unmodifiableMap", "subList", "putIfAbsent",
         "toSet", "toMap", "fromJava", "toJava", "toStream", "entryToPair",
         "mapGet", "mapContainsKey", "mapRemove",
         "setContains", "setRemove", "keySetView", "entrySetView",
         "optionalOrElse",
         // the three mutable.Buffer members a re-parented java.util.List owes with no java
         // counterpart (K28.1). Named buffer* to avoid an ambiguity with insertAll's own overload.
         "bufferRemoveRange", "bufferInsertAll", "bufferPatchInPlace")

  // -------------------------------------------------------------------------------------------
  // what this phase handles, as data — the answer JdkSurfaceCheck needs and match arms cannot give.
  // -------------------------------------------------------------------------------------------
  // declared rather than derived at runtime; agreement with the arms is asserted by
  // CollectionsHandledDerivationSpec, which scans this file's source text in both directions.

  /** every `owner#name` a [[staticRewrite]] arm matches, including the two collector keys the
    * `collect` arms read out of a guard — a table that omitted them would report the port's own
    * translation as its wall. */
  val handledStatics: Set[String] = Set(
    "java.util.Arrays#asList",
    "java.util.Collection#stream",
    // java's three raw constants are fields, not calls — see StaticFieldFactories/staticFieldRewrite.
    "java.util.Collections#EMPTY_LIST",
    "java.util.Collections#EMPTY_MAP",
    "java.util.Collections#EMPTY_SET",
    "java.util.Collections#emptyList",
    "java.util.Collections#emptyMap",
    "java.util.Collections#emptySet",
    "java.util.Collections#reverse",
    "java.util.Collections#shuffle",
    "java.util.Collections#singleton",
    "java.util.Collections#singletonList",
    "java.util.Collections#singletonMap",
    "java.util.Collections#sort",
    "java.util.Collections#swap",
    "java.util.Collections#unmodifiableCollection",
    "java.util.Collections#unmodifiableList",
    "java.util.Collections#unmodifiableMap",
    // java.util.EnumSet has no public constructor; it reaches its shim entirely through these six.
    "java.util.EnumSet#noneOf",
    "java.util.EnumSet#allOf",
    "java.util.EnumSet#of",
    "java.util.EnumSet#copyOf",
    "java.util.EnumSet#range",
    "java.util.EnumSet#complementOf",
    // the primitive optionals' two, which are Some/None and need no runtime member.
    "java.util.OptionalInt#of",
    "java.util.OptionalInt#empty",
    "java.util.OptionalLong#of",
    "java.util.OptionalLong#empty",
    "java.util.OptionalDouble#of",
    "java.util.OptionalDouble#empty",
    "java.util.Collections#unmodifiableSet",
    "java.util.List#stream",
    "java.util.Map$Entry#comparingByKey",
    "java.util.Map$Entry#comparingByValue",
    "java.util.Map.Entry#comparingByKey",
    "java.util.Map.Entry#comparingByValue",
    "java.util.Set#stream",
    "java.util.stream.Collectors#toCollection",
    "java.util.stream.Collectors#toList",
    "java.util.stream.Collectors#toMap",
    "java.util.stream.Collectors#toSet",
    "java.util.stream.DoubleStream#sum",
    "java.util.stream.IntStream#mapToObj",
    "java.util.stream.IntStream#range",
    "java.util.stream.IntStream#sum",
    "java.util.stream.LongStream#sum",
    "java.util.stream.Stream#allMatch",
    "java.util.stream.Stream#anyMatch",
    "java.util.stream.Stream#collect",
    "java.util.stream.Stream#filter",
    "java.util.stream.Stream#map",
    "java.util.stream.Stream#mapToDouble",
    "java.util.stream.Stream#noneMatch",
    "java.util.stream.Stream#sorted",
  )

  /** java's RAW static CONSTANTS → the typed factory java itself says they are, read by
    * `staticFieldRewrite`. Three entries, and the list is closed by JAVA rather than by this table:
    * these are the only members of `java.util.Collections` that are fields at all. */
  private[balticporter] val StaticFieldFactories: Map[String, String] = Map(
    "java.util.Collections#EMPTY_LIST" -> "emptyList",
    "java.util.Collections#EMPTY_SET"  -> "emptySet",
    "java.util.Collections#EMPTY_MAP"  -> "emptyMap",
  )

  /** collection KIND → the instance member names [[rewrite]] handles for it, with
    * `JdkSurfaceCheck.AnyKind` for an arm whose kind pattern is `_`. Read by eye and deliberately
    * not asserted: assigning a name to more kinds than its arm covers can only make the check
    * kinder, never miss a real hole. */
  val handledInstance: Map[String, Set[String]] = Map(
    balticporter.tir.JdkSurfaceCheck.AnyKind -> Set(
      // arms whose kind pattern is `_` …
      "forEach", "iterator", "getOrDefault", "add", "addAll", "putAll",
      // … and `parenless`, which is an arm of its own (`case (n, Nil, _) if parenless(n)`)
      "size", "isEmpty", "keySet", "values", "nonEmpty", "hasNext", "next",
    ),
    Kind.Seq.toString   -> Set("get", "set", "remove", "addLast", "offer", "offerLast",
                               "addFirst", "offerFirst", "poll", "pollFirst", "peek", "peekFirst", "element",
                               "toArray", "subList",
                               // SE8 defaults on List/Collection plus AbstractCollection's two bulk mutators.
                               "sort", "removeIf", "containsAll", "removeAll", "retainAll",
                               "ensureCapacity",
                               // java's bidirectional cursor; only on Kind.Seq since only List declares it. K23
                               "listIterator",
                               // spliterator's own ORDERED|SIZED|SUBSIZED characteristics, not an asJava wrapper's.
                               "spliterator"),
    Kind.Map.toString   -> Set("get", "put", "remove", "containsKey", "entrySet", "values", "putIfAbsent",
                               "computeIfAbsent", "containsValue"),
    // contains rewrites to setContains at a widened Object probe; not on Kind.Seq, where Buffer.contains differs.
    Kind.Set.toString   -> Set("remove", "contains", "toArray", "removeIf", "containsAll",
                               "removeAll", "retainAll",
                               // Set.spliterator's own default passes DISTINCT where List's passes ORDERED.
                               "spliterator"),
    Kind.Entry.toString -> Set("getKey", "getValue"),
    // a Stack's own five plus everything Kind.Seq covers — the re-entry arm at the foot of rewrite
    // answers those for a stack receiver too.
    Kind.Opt.toString   -> Set("getAsInt", "getAsLong", "getAsDouble", "orElseThrow",
                               "isPresent", "orElse", "ifPresent"),
    Kind.Stack.toString -> (Set("push", "pop", "peek", "search", "empty") ++
                            Set("get", "set", "remove", "addLast", "offer", "offerLast",
                                "addFirst", "offerFirst", "poll", "pollFirst", "peekFirst", "element",
                                "toArray", "subList")),
  )

  /** This phase's record, in the shape [[balticporter.tir.JdkSurfaceCheck]] reads. `ran` is the
    * caller's to supply: absent, an unhandled member on a mapped type is an offer (`mappable`,
    * report-only); present, the same member is a hole the phase MADE, and is a finding. */
  def jdkMapping(ran: Boolean): balticporter.tir.JdkSurfaceCheck.Mapping =
    balticporter.tir.JdkSurfaceCheck.Mapping(
      phase        = "java-collections->scala",
      ran          = ran,
      types        = typeMap.view.mapValues((target, kind) => (target, kind.toString)).toMap,
      statics      = handledStatics,
      instance     = handledInstance,
      // the shims' own members, pinned to the published runtime sources by RuntimeMembersDerivationSpec.
      shimMembers  = RuntimeArtifact.concreteMembers.view.mapValues(_.map(_._1)).toMap,
      iterableShim = Some(JavaIterableFqn),
      // `new` on a retyped type is rewritten by three paths, none a member table entry — a
      // constructor is not a member call. ENGINE-LIMITS K11
      constructors = true,
    )

  /** Runtime shim types this retyping requires (e.g. `JavaIterator` = scala `Iterator` + `remove`).
    * Live in `balticporter-runtime`. */
  val runtimeTypes: Set[String] =
    Set(JavaIteratorFqn, JavaListIteratorFqn, JavaIterableFqn, JavaCollectionFqn, JavaCollectionsFqn,
        JavaStackFqn, JavaEnumMapFqn, JavaEnumSetFqn,
        JavaOptionalIntFqn, JavaOptionalLongFqn, JavaOptionalDoubleFqn)

  /** What [[runtimeSources]] brings, for a consumer that must reason about the injected
    * supertypes it cannot parse — `JavaIterator.remove` is concrete, so a class extending both it
    * and a superclass also defining `remove` is a scala linearisation conflict `TirEmitter` needs
    * to know about. Prefer `RuntimePlan.of(phases).concreteMembers`, which derives this. */
  lazy val runtimeConcreteMembers: Map[String, Set[(String, List[Int])]] =
    RuntimeArtifact.concreteMembers.filter((fqn, _) => runtimeTypes.contains(fqn))

  /** The support sources, as text, for a port that vendors them instead of depending on the
    * artifact ([[balticporter.core.RuntimeMode.Vendored]]). Not the source of truth — the build-time
    * copy of `runtime/src/main/scala`. Prefer `RuntimePlan.of(phases, mode).writeSources(dir)`. */
  lazy val runtimeSources: Map[String, String] =
    runtimeTypes.map(fqn => fqn -> RuntimeArtifact.sourceOf(fqn)).toMap
